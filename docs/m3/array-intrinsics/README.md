<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# Array loops to System.arraycopy and Arrays.fill (ARRAY_LOOP_INTRINSICS v1)

Some SWT loops copy or fill arrays one element at a time. A recipe changes such a loop into `System.arraycopy` or
`Arrays.fill`, but only when it can prove the two behave the same. Every other loop stays as it is, and the ledger
gives the reason.

The two calls differ for speed. `System.arraycopy` is a HotSpot intrinsic. `Arrays.fill` is not: on JDK 21 it is a
plain Java loop, which C2 vectorizes the same way as the loop it replaces. The copy rewrites (C1) can be faster. The
fill rewrites (F1, F2) are for readability only and make no speed claim (see [Speed](#speed)). The recipe keeps its
name, but only C1 targets an intrinsic.

The census (ARRAY-LOOP-PATTERN v1, `tools/array_loop_census.py`) found 29 SWT loops shaped like a copy or a fill: 10
COPY and 19 FILL. **8 are rewritten and 21 are skipped.** The rows are in
[ARRAY_LOOP_LEDGER.tsv](ARRAY_LOOP_LEDGER.tsv), with file hashes before and after in [RECEIPT.tsv](RECEIPT.tsv).

## Inventory first

Nothing existing already does this, so no owner is duplicated. The full rows are in
[FOSS_REUSE_DECISION.tsv](FOSS_REUSE_DECISION.tsv).

| Owner | What it is | Decision |
|---|---|---|
| Synexia array census (`array_loop_census.py`) | loop shape, comment and string mask, body text | ADAPT (copied; the FILL route label corrected, `--relabel` mode added) |
| IntelliJ `ManualArrayCopyInspection` (`188b7ef96f7`) | the IDE's manual-copy conditions | PATTERN_ONLY (needs the IDE's PSI) |
| PMD `AvoidArrayLoops` | detects copy loops; has no fix | NO_FIT |
| OpenRewrite rewrite-static-analysis | no copy or fill recipe (code search: 0 hits) | NO_FIT |
| M3JDK `m3/arrays`, `m3/tooling/recipe-catalogue.tsv` | array views and packed integers; no copy or fill recipe | NO_FIT |
| M3JDK `m3/file`, `m3/lexicon` catalogues | file access and natural-language lexicon | NO_FIT |
| Peer lane `m3/array-map-fill-20261031` (`arrfill`) | array-backed maps for the collection forks | NO_FIT; not touched |

102 M3JDK branch names mention array, fill or intrinsic. Judged by name only, they are mask, collection, char-copy
and collection-fill lanes (`m3/array-map-fill-20261031`, `m3/decorator-fill-20261030`). None is named for rewriting
loops.

## Atoms

| Atom | Loop | Replacement | Proven when |
|---|---|---|---|
| C1 COPY_FRESH v1 | `D = new T[N]; for (int i = 0; i < N; i++) D[i] = S[i];` | `System.arraycopy(S, 0, D, 0, N);` | `T` is exactly the element type of `S`, taken from the declaration of `S` in scope; `D` is fresh (non-null, a different array, length `N`); `S` is non-null with length >= `N`, because `N` is `S.length` or `S` is a local `new T[N]` with a final `N` that is never reassigned |
| F1 FILL_FULL v1 | `for (int i = 0; i < D.length; i++) D[i] = V;` | `Arrays.fill(D, V);` | `V` has no side effect and does not read `i`; the element type, taken from the declaration of `D` in scope, is primitive (byte, short and char get the cast the assignment applied), or `V` is `null` |
| F2 FILL_FRESH v1 | `D = new T[N]; for (int i = 0; i < N; i++) D[i] = V;` | `Arrays.fill(D, V);` | as F1, with the type of `D` from its allocation; `D` is fresh with length `N` |

These conditions follow from how the calls differ from a loop:

- `System.arraycopy` copies as if through a temporary array, but a forward loop that overlaps itself does not.
- `arraycopy` throws `ArrayStoreException` where a loop would widen `int` to `float`.
- Both calls throw where a loop runs zero times: `IllegalArgumentException` when the start is past the end, and
  `NullPointerException` on a null array of length 0.
- Both calls check the range before storing anything, while a loop stores elements before it fails.
- `Arrays.fill` evaluates its value once, so per-element objects, reads and boxing are not fills.

## Type proof (TYPE-SCOPE v1)

An element type comes from the declaration that is in scope at the loop, never from some other declaration that
shares the name. The recipe reads one file and uses its block structure:

- A simple name is a local declared before the loop in an enclosing block, a parameter of the enclosing method, or a
  field of an enclosing class. The innermost one wins, as in Java.
- In a qualified chain `q.f`, `q` is resolved the same way. `f` must be a field declared in the body of `q`'s class,
  and that class must be declared in this file.
- The `D` of C1 and F2 needs no lookup: the `new T[N]` right before the loop gives its type.

| Reason | When |
|---|---|
| SKIP_TYPE_UNPROVEN | the name, or the class of a qualifier, is not declared in this file (it is in another file or a supertype), or a class with a supertype lies between the loop and the declaration, so an inherited member could shadow it |
| SKIP_TYPE_UNRESOLVED | a declaration in scope does not give its type in the text (a lambda, for, catch, resource or pattern variable, or `var`), or two declarations are at the same depth |

The review case behind this rule: `o.items` and `o.buf` are declared `int[]` in another file, and the scanned file
has an unrelated `long[] items; short[] buf;`. The earlier file-wide lookup took those. It emitted `System.arraycopy`
for a widening copy, which throws `ArrayStoreException`, and `Arrays.fill(o.buf, (short)65535)`, which stores -1
instead of 65535. Both loops now skip as SKIP_TYPE_UNPROVEN, and the fixture keeps both cases
([fixtures/ArrayLoopHolder.java](fixtures/ArrayLoopHolder.java) is the other file).

## Result (SWT, base `m3/superset-20261030b` 438161fb05a)

| File:line | Atom | Replacement |
|---|---|---|
| `JPEGDecoder.java:3867` | F1 | `Arrays.fill(blk, (short) 0);` |
| `JPEGDecoder.java:6325` | F1 | `Arrays.fill(dtbl.look_nbits, 0);` |
| `JPEGFileFormat.java:762` | F1 | `Arrays.fill(dataUnit, 0);` |
| `CLabel.java:828` | C1 | `System.arraycopy(percents, 0, gradientPercents, 0, percents.length);` |
| `CTabFolder.java:2727` | C1 | `System.arraycopy(colors, 0, gradientColors, 0, colors.length);` |
| `CTabFolder.java:2731` | C1 | `System.arraycopy(percents, 0, gradientPercents, 0, percents.length);` |
| `CTabFolder.java:3939` | C1 | `System.arraycopy(percents, 0, selectionGradientPercents, 0, percents.length);` |
| `Library.java:354` | C1 | `System.arraycopy (mappedNames, 0, fileNames, 0, candidates);` (local `new String[candidates]`, `final int candidates = 3`) |

Each file keeps its own call and cast spacing. `JPEGDecoder` and `JPEGFileFormat` gain `import java.util.Arrays;`.
Every type behind these rewrites is a declaration in scope in the same file: the local `short[] blk`, the parameter
`d_derived_tbl dtbl` with the field `int[] look_nbits` of the nested class `d_derived_tbl`, the field
`int[] dataUnit` of `JPEGFileFormat`, the parameters `int[] percents` and `Color[] colors`, and the local
`String[] mappedNames`.

| Skip reason | Rows | Examples |
|---|---|---|
| SKIP_VALUE_SIDE_EFFECT | 6 | `new Rectangle(...)` per element (CTabFolder:561), `createSash()`, `readU2(raf)` (IconExe x3), `new Image` (test) |
| SKIP_START_NONZERO | 4 | `ImageData:656`, `PngTrnsChunk:129`, `FontData:691`, `ColorDialog:210`: a range fill would throw where the loop does nothing if start > end |
| SKIP_TYPE_MISMATCH | 2 | `GC.setLineDash` (cocoa, gtk): `float[] <- int[]` widening; the parameter `int[] dashes` is the declaration in scope |
| SKIP_BOUND_UNPROVEN | 2 | `PngEncoder:220` (allocation `pixel + 1`, bound `pixel`), `ColorDialog:194` (constant bound over a non-final field) |
| SKIP_OFFSET | 2 | `PNGFileFormat:433`, `JPEGFileFormat:281` |
| SKIP_SAME_ARRAY | 1 | `DefaultContent:855` (shifts `lines` down; offset sign and length need data flow) |
| SKIP_SOURCE_UNPROVEN | 1 | `CTabFolder:3935` (bound `colorsLength`, not `colors.length`) |
| SKIP_LE_BOUND | 1 | `Image:1943` |
| SKIP_VALUE_FIELD | 1 | `WebSite:774` (`COM.DISPID_UNKNOWN`; class-initialization proof not attempted) |
| SKIP_BODY_SHAPE | 1 | `PngIhdrChunk:289` (two statements) |

## Speed

| Route | JDK 21.0.9 | Claim |
|---|---|---|
| `System.arraycopy` (C1) | `@IntrinsicCandidate` in `java/lang/System.java` | intrinsic; not measured per site |
| `Arrays.fill` (F1, F2) | no `fill` overload carries `@IntrinsicCandidate` in `java/util/Arrays.java` (only `equals(char[], char[])`, `equals(byte[], byte[])`, `copyOf(U[], int, Class)` and `copyOfRange(U[], int, int, Class)` do); `fill(int[], int)` is a plain loop; `-XX:+PrintFlagsFinal` shows `OptimizeFill = false`, so C2 does not swap in its fill stub | none: C2's SuperWord vectorizes it like the original loop, so F1 and F2 are readability only |

## Proof

The gates are in [GATES.tsv](GATES.tsv) and the corrections in [RECIPE_MEMORY.tsv](RECIPE_MEMORY.tsv).

- **Fixture** (`tools/prove_array_intrinsics.sh`, [fixtures/ArrayLoopFixtures.java](fixtures/ArrayLoopFixtures.java)
  compiled with [fixtures/ArrayLoopHolder.java](fixtures/ArrayLoopHolder.java)):
  - 32 of 32 expected decisions match: 11 rewrites, and a skip for each reason. Three skips are SKIP_TYPE_UNPROVEN:
    the two two-file cases and a field inherited from a supertype.
  - Three scope cases rewrite with the type of the declaration in scope: a local that hides a static field of a
    narrower type, a static field after a block whose local of the same name is out of scope, and a varargs
    parameter. Had the recipe taken the other declaration, the fill would carry a `(short)` cast and the trace would
    change.
  - A second run rewrites nothing.
  - The fixture prints the same 56-line trace before and after the recipe, including length 0, null and negative
    sizes.
  - 14 of 14 discriminators differ. Each runs the naive replacement of a skipped form and shows the behaviour change
    that makes the skip necessary.
  - The previous tool (`fe9a3c5`) fails 6 of the 32 markers. It rewrites both two-file cases, and it skips four loops
    whose type the scope decides (SKIP_TYPE_UNRESOLVED).
- **SWT tree:**
  - The recipe regenerates the ledger from the 20 census files at the base 438161fb05a: the same 8 rewrites, and the
    5 rewritten files are byte-identical to the committed ones (RECEIPT.tsv is unchanged).
  - `compile_m3gc.sh`: 967 sources, 1,463 classes, 0 errors. All 5 changed files are in the source set.
  - Census fixed point: a second `--apply` reports DONE=8 and writes no files.
- **Runtime, headless** (`tools/prove_jpeg_loops.sh`, `tools/JpegLoopProbe.java`, base 438161fb05a):
  - 18 generated JPEGs (rgb, gray, progressive; 6 sizes) decode to the same SHA-256 with the old and the new classes.
    This holds on the default `JPEGDecoder` path and on the `JPEGFileFormat` 3.2 path.
  - A mutant that fills 1 instead of 0 at the rewritten sites changes the output on both paths. That shows the probe
    reaches those sites.
  - The script refuses a base whose two decoder sources equal the worktree's (exit 2). A base of `HEAD` after the
    commit can no longer pass by comparing the new decoder with itself.
- **NOT_RUN:** widget runtime for CLabel and CTabFolder (needs a Display) and `Library.loadLibrary` (natives); no
  per-site speed measurement (C1 targets an intrinsic, and F1 makes no speed claim).

## Known gaps of v1

These are not proven yet. Neither affects a current SWT rewrite: all 3 fills store the literal 0, and the only C1
that relies on a final bound uses Library's local `final int candidates = 3`.

- A value that names a boxed variable, such as an `Integer v` filled into an `int[]`. `Arrays.fill` unboxes `v` once,
  even for an empty array, so a null `v` throws where the loop does nothing.
- The final-int check of a bound `N` (C1 with a fresh local source) reads every `int N =` declaration in the file,
  not the one in scope.

## Nebula candidates (read-only; Nebula unchanged)

[NEBULA_CANDIDATES.tsv](NEBULA_CANDIDATES.tsv) covers 11 census rows in `C:/m3-superset/nebula-webwidgets`
(`c25023b8b0d`). None is rewritten.

- `RowBitLanes.java:141` and `:432` fill the words between a first and a last word, from `fw + 1`. They are
  equivalent to `Arrays.fill(w, fw + 1, lw, ...)` only because a guard before the loop ensures `fw < lw`, and v1 does
  not prove guards (SKIP_START_NONZERO). They are candidates for a guard-aware range fill (v2).
- The other 9 rows are tests that fill with `random.next*()` or `new GridItem(...)` (SKIP_VALUE_SIDE_EFFECT).

## Re-run

```
python -I tools/array_loop_census.py <out> swt=<worktree>/bundles nebula=<nebula>          # census (optional)
python -I tools/array_loop_census.py --relabel CENSUS_COPY_FILL.tsv                         # route labels only
python -I tools/array_loop_intrinsics.py census <tree> CENSUS_COPY_FILL.tsv <ledger> [--apply] [--receipt <tsv>]
bash tools/prove_array_intrinsics.sh <out-dir>
bash tools/prove_jpeg_loops.sh <worktree> <compile_m3gc classes> 438161fb05a <out-dir>
```

`<tree>` is the worktree, or a copy of the census files at the base revision when the ledger is regenerated.
