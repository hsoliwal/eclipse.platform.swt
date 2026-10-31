<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# Array loops to JDK intrinsics (ARRAY_LOOP_INTRINSICS v1)

Some SWT loops copy or fill arrays one element at a time. HotSpot already compiles `System.arraycopy` and
`Arrays.fill` to vector code. A recipe changes such a loop into one of those calls, but only when it can prove the
two behave the same. Every other loop stays as it is, and the ledger gives the reason.

The census (ARRAY-LOOP-PATTERN v1, `tools/array_loop_census.py`) found 29 SWT loops shaped like a copy or a fill: 10
COPY and 19 FILL. **8 are rewritten and 21 are skipped.** The rows are in
[ARRAY_LOOP_LEDGER.tsv](ARRAY_LOOP_LEDGER.tsv), with file hashes before and after in [RECEIPT.tsv](RECEIPT.tsv).

## Inventory first

Nothing existing already does this, so no owner is duplicated. The full rows are in
[FOSS_REUSE_DECISION.tsv](FOSS_REUSE_DECISION.tsv).

| Owner | What it is | Decision |
|---|---|---|
| Synexia array census (`array_loop_census.py`) | loop shape, comment and string mask, body text | REUSE_DIRECT (copied byte-identical) |
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
| C1 COPY_FRESH v1 | `D = new T[N]; for (int i = 0; i < N; i++) D[i] = S[i];` | `System.arraycopy(S, 0, D, 0, N);` | `T` is exactly the element type of `S`; `D` is fresh (non-null, a different array, length `N`); `S` is non-null with length >= `N`, because `N` is `S.length` or `S` is a local `new T[N]` with a final `N` that is never reassigned |
| F1 FILL_FULL v1 | `for (int i = 0; i < D.length; i++) D[i] = V;` | `Arrays.fill(D, V);` | `V` has no side effect and does not read `i`; the element type is primitive (byte, short and char get the cast the assignment applied), or `V` is `null` |
| F2 FILL_FRESH v1 | `D = new T[N]; for (int i = 0; i < N; i++) D[i] = V;` | `Arrays.fill(D, V);` | as F1; `D` is fresh with length `N` |

These conditions follow from how the calls differ from a loop:

- `System.arraycopy` copies as if through a temporary array, but a forward loop that overlaps itself does not.
- `arraycopy` throws `ArrayStoreException` where a loop would widen `int` to `float`.
- Both calls throw where a loop runs zero times: `IllegalArgumentException` when the start is past the end, and
  `NullPointerException` on a null array of length 0.
- Both calls check the range before storing anything, while a loop stores elements before it fails.
- `Arrays.fill` evaluates its value once, so per-element objects, reads and boxing are not fills.

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

| Skip reason | Rows | Examples |
|---|---|---|
| SKIP_VALUE_SIDE_EFFECT | 6 | `new Rectangle(...)` per element (CTabFolder:561), `createSash()`, `readU2(raf)` (IconExe x3), `new Image` (test) |
| SKIP_START_NONZERO | 4 | `ImageData:656`, `PngTrnsChunk:129`, `FontData:691`, `ColorDialog:210`: a range fill would throw where the loop does nothing if start > end |
| SKIP_TYPE_UNRESOLVED | 2 | `GC.setLineDash` (cocoa, gtk): `float[] <- int[]` widening; the file declares `dashes` as both `int[]` and `float[]` |
| SKIP_BOUND_UNPROVEN | 2 | `PngEncoder:220` (allocation `pixel + 1`, bound `pixel`), `ColorDialog:194` (constant bound over a non-final field) |
| SKIP_OFFSET | 2 | `PNGFileFormat:433`, `JPEGFileFormat:281` |
| SKIP_SAME_ARRAY | 1 | `DefaultContent:855` (shifts `lines` down; offset sign and length need data flow) |
| SKIP_SOURCE_UNPROVEN | 1 | `CTabFolder:3935` (bound `colorsLength`, not `colors.length`) |
| SKIP_LE_BOUND | 1 | `Image:1943` |
| SKIP_VALUE_FIELD | 1 | `WebSite:774` (`COM.DISPID_UNKNOWN`; class-initialization proof not attempted) |
| SKIP_BODY_SHAPE | 1 | `PngIhdrChunk:289` (two statements) |

## Proof

The gates are in [GATES.tsv](GATES.tsv) and the corrections in [RECIPE_MEMORY.tsv](RECIPE_MEMORY.tsv).

- **Fixture** (`tools/prove_array_intrinsics.sh`, [fixtures/ArrayLoopFixtures.java](fixtures/ArrayLoopFixtures.java)):
  - 25 of 25 expected decisions match: 7 rewrites, and one skip for each reason.
  - A second run rewrites nothing.
  - The fixture prints the same 45-line trace before and after the recipe, including length 0, null and negative
    sizes.
  - 11 of 11 discriminators differ. Each runs the naive replacement of a skipped form and shows the behaviour change
    that makes the skip necessary.
- **SWT tree:**
  - `compile_m3gc.sh`: 967 sources, 1,463 classes, 0 errors. All 5 changed files are in the source set.
  - Census fixed point: a second `--apply` reports DONE=8 and writes no files.
- **Runtime, headless** (`tools/prove_jpeg_loops.sh`, `tools/JpegLoopProbe.java`):
  - 18 generated JPEGs (rgb, gray, progressive; 6 sizes) decode to the same SHA-256 with the old and the new classes.
    This holds on the default `JPEGDecoder` path and on the `JPEGFileFormat` 3.2 path.
  - A mutant that fills 1 instead of 0 at the rewritten sites changes the output on both paths. That shows the probe
    reaches those sites.
- **NOT_RUN:** widget runtime for CLabel and CTabFolder (needs a Display) and `Library.loadLibrary` (natives); no
  per-site performance measurement.

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
python -I tools/array_loop_intrinsics.py census <worktree> CENSUS_COPY_FILL.tsv <ledger> [--apply] [--receipt <tsv>]
bash tools/prove_array_intrinsics.sh <out-dir>
bash tools/prove_jpeg_loops.sh <worktree> <compile_m3gc classes> <base-rev> <out-dir>
```
