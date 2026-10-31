# Leaf 12 - one Skija: 0.143.18 + types 0.2.0 everywhere (prepared, measured, not yet applied)

State (2026-10-31): the switch is a ready, re-runnable package. The forward delta tool, the version pin of every gate,
the structured javac error list, the native signature gate and the measured receipts are on this branch; no renderer
source changes here. The text leaf (`m3/text-through-m3-20261031`) edits the Render M3 text files right now, so the
renderer switch is made after it lands, by rerunning the tool (procedure below).

Base: `origin/m3/superset-20261030b` @ `cf669d53ef8`. Branch `m3/one-skija-prep-20261031`.
Census: LEAF_QUEUE.md L12, root cause 1 (SH01 SH02 SKC-V01 M-VERSPLIT PR-18 PR-19 SH24): two Skija majors in one tree,
donor code from 0.143.16 edited backwards to the 0.116.3 jars, each leaf rediscovering the drift from javac.
Recipe rows: `RECIPE_MEMORY.tsv` L12-01..L12-25. FOSS rows: `FOSS_REUSE_DECISION.tsv` `m3-one-skija:*`.

## Atoms

| atom | files |
|---|---|
| SKIJA_PIN v1 | `tools/m3_skija_pin.sh` (sourced); `tools/{compile_m3gc.sh,test_m3gc.sh,test_m3fidelity.sh,probe_m3_canvas.sh,probe_owner_draw.sh}` read `skija.version` of `bundles/org.eclipse.swt.m3/pom.xml`, types from skija-shared's own POM (the `build_m3_ship.sh` rule); `M3_SKIJA_VERSION` / `M3_SKIJA_LIB` select a candidate set |
| DELTA_FORWARD v1 | `tools/m3gc_api_deltas.py --forward`: 17 typed atoms L12-01..L12-17 (REVERT 8, PORT 2, RULE 2, CHECK 4, PIN 1), fixed point, RECIPE_MEMORY rows |
| JAVAC_BLLSE v1 | `tools/m3gc_javac_bllse.py`: javac.log -> {file, line, col, severity, message, symbol, location, source}; `LEAF12_JAVAC_ERRORS.tsv` |
| NATIVE_SIG_GATE v1 | `tools/m3gc_native_sig_gate.sh`: javap descriptors of every `<SkijaClass>._n*` the M3 trees call, pinned vs candidate jar; `LEAF12_NATIVE_SIG.tsv` |
| PIXEL_REBASE v1 | `tools/m3_l12_realapp.sh` (real-app smoke per pin) + `tools/m3gc_skija_compare.py` (tab-by-tab, pin vs pin); `LEAF12_REALAPP_COMPARE.tsv` |
| BINARIES_SET v1 (win32 x64 only) | `LEAF12_JARS.tsv`: URL, size, SHA-1 (= Maven Central `.sha1`), SHA-256 of the three jars and of `skija.dll` |

Not in this package (census L12 atoms that follow the switch): NATIVE_CANVAS_ADAPTER (the 50 `Canvas._n*` sites behind
one `M3NativeCanvas`; renderer code), CLEARTYPE_PARAMS (`FontMgr.useSystemRenderingParams(boolean)`, new in 0.143.11)
and CACHE_LIMITS (`io.github.humbleui.skija.Graphics`, new in 0.143.17): both are additions with no 0.116.3 use to
port (atoms L12-15/L12-16 assert that), so they change rendering and need their own measured leaf; BINARIES_SET for
arm64/linux/macos fragments.

## Jars (BINARIES_SET, win32 x64)

| artifact | version | source | SHA-256 |
|---|---|---|---|
| skija-shared | 0.143.18 | local census fetch `H:/m3-scratch/i31-census/platform-runtime-verify/dl143` (SHA-1 = Central) | `972bca0f05f44d008c2eaa7b49e7275e23480e650da1394b7b1c319ebe8db4b4` |
| skija-windows-x64 | 0.143.18 | `https://repo1.maven.org/maven2/io/github/humbleui/skija-windows-x64/0.143.18/skija-windows-x64-0.143.18.jar` | `837f53c07fb50ff09198326e86ff3ccc0a4b070f0c28d99694c3b4e8a39fc01e` |
| types | 0.2.0 | `https://repo1.maven.org/maven2/io/github/humbleui/types/0.2.0/types-0.2.0.jar` | `38d94d00770c4f261ffb50ee68d5da853c416c8fe7c57842f0e28049fc26cca8` |
| skija.dll (in the windows jar) | 0.143.18 | entry `io/github/humbleui/skija/windows/x64/skija.dll` | `198e0aa4b24fa6776bb9af0c04ba9b07833ba82d9998acc3808e5edc9a6fd421` |

Staged at `H:/m3-scratch/l12/jars` (outside the repository). The three jar hashes equal the ones in
`docs/licenses/swt-gpu-retained-planes-NOTICE.txt`; the dll equals the census extract `skija-capabilities/dll143`;
skija-shared 0.143.18's embedded POM declares types 0.2.0. Downloaded native code ran only inside the smoke JVMs below.

## Compile: 0.143.18 before and after the forward deltas

`compile_m3gc.sh` on the overlay (960 sources: win32 source set + Render M3 + GPU M3):

| build | Skija | errors | classes |
|---|---|---|---|
| base (pristine overlay) | 0.116.3 | 0 | 1454 |
| base (pristine overlay) | 0.143.18 | **17** (4 files) | 794 |
| base + L12-01..L12-17 | 0.143.18 | **0** | 1454 |

The 17 records (`LEAF12_JAVAC_ERRORS.tsv`) are all `cannot find symbol` on a `Path` builder method (moveTo 5, lineTo 5,
closePath 5, cubicTo 1, quadTo 1): Replay.java 3, M3ExternalGC.java 3, M3PathConverter.java 8, M3RowCanvas.java 3. A
second compile with `-XDshould-stop.ifError=FLOW` (attribute every class even after an error) gives the same 17, so the
list is complete, not the first wave. RegionOp produced no error: leaf 6 (CLIP_STATE) rewrote M3RegionCalculator, the
only Region.Op/addPoly user, and the GPU M3 tree was already written for 0.143.18 except `Replay.poly` (P02).

## Forward deltas (one typed atom per API break)

| id | kind | API | file | state on the overlay (run 1 / run 2) | what |
|---|---|---|---|---|---|
| L12-01 | REVERT D07 | PathBuilder | M3ExternalGC.java | APPLIED / DONE | drawPolygon builds with `PathBuilder` (I31 SkiaGC original) |
| L12-02 | REVERT D08 | PathBuilder | M3ExternalGC.java | APPLIED / DONE | `try (var path = pathBuilder.build())` |
| L12-03 | REVERT D09 | PathBuilder | M3PathConverter.java | APPLIED / DONE | convertSWTPathToM3Path: `try (PathBuilder ...)` |
| L12-04 | REVERT D10 | PathBuilder | M3PathConverter.java | APPLIED / DONE | `m3PathBuilder.build()` |
| L12-05 | REVERT D11 | PathBuilder | M3PathConverter.java | APPLIED / DONE | createPath: `try (PathBuilder ...)` (setFillMode is a PathBuilder method) |
| L12-06 | REVERT D12 | PathBuilder | M3PathConverter.java | APPLIED / DONE | `return pathBuilder.build()` |
| L12-07 | REVERT D16 | PathBuilder | Replay.java | APPLIED / DONE | Replay.poly: `try (PathBuilder builder = new PathBuilder ())` |
| L12-08 | REVERT D17 | PathBuilder | Replay.java | APPLIED / DONE | `try (Path path = builder.detach ())`, draw `path._ptr` |
| L12-09 | PORT | PathBuilder | M3RowCanvas.java | APPLIED / DONE | the reused polygon field becomes a `PathBuilder` (reset/close exist) |
| L12-10 | PORT | PathBuilder | M3RowCanvas.java | APPLIED / DONE | draw `path.detach ()` in a try (one native Path per polygon) |
| L12-11 | RULE | RegionOp | both M3 trees | NO_SITE | `Region.Op` -> `RegionOp` (0.143.5); inverse of D05/D06, sites retired by leaf 6 |
| L12-12 | RULE | PathBuilder | both M3 trees | NO_SITE | `.addPoly(` would need a PathBuilder (MANUAL_SITE); inverse of D13-D15 |
| L12-13 | CHECK | FontMgr | M3Resources.java | CHECK_OK(2) | `matchFamilyStyle(null, style)` = default font (0.143.4): what both fallbacks ask for |
| L12-14 | CHECK | Canvas | both M3 trees | CHECK_OK(50) | 50 direct `Canvas._n*` calls; descriptors by NATIVE_SIG_GATE |
| L12-15 | CHECK | Graphics | both M3 trees | CHECK_OK(0) | no use; new class, nothing to port |
| L12-16 | CHECK | FontMgr | both M3 trees | CHECK_OK(0) | `useSystemRenderingParams` not used; nothing to port |
| L12-17 | PIN | SKIJA_PIN | bundles/org.eclipse.swt.m3/pom.xml | APPLIED / DONE | `<skija.version>0.143.18</skija.version>` |

Run 1: `FORWARD rounds=2 applied=11 bad=- FIXED_POINT`; run 2: `rounds=1 applied=0 FIXED_POINT`. The REVERT atoms are the
byte inverse of the D rows (D.new -> D.old), so the restored text is the donor's
(`swt-initiative31/skija-canvas@9bcb404c42` SkiaGC.java / SkiaPathConverter.java). Replayed on a fresh `git archive` of
the base: same result, the four renderer files byte-identical to the overlay. The renderer diff is 11 hunks, 29 lines.
0.143 facts the atoms rely on (skija-shared 0.143.18 sources + CHANGELOG): `PathBuilder.build()` returns the Path and
closes the builder, `detach()` returns it and resets the builder, `Managed.close()` is a no-op on a closed object
since 0.143.3, so `try (PathBuilder b) { ... b.build() ... }` closes once.

NATIVE_SIG_GATE (`LEAF12_NATIVE_SIG.tsv`): 12 distinct `Canvas._n*` natives used, 12 SAME descriptors between
skija-shared 0.116.3 and 0.143.18, 0 changed or missing.

## Headless tests and real-app smoke: 0.143.18 vs 0.116.3

Same overlay, same natives (`H:/m3-scratch/fidelity/natives`, 4975r19, verified by LFS oid in every test run: ok=6
bad=0), one JVM at a time; the 0.116.3 build is the pristine overlay, the 0.143.18 build is the overlay after the
forward deltas.

| gate | 0.116.3 | 0.143.18 |
|---|---|---|
| `test_m3gc.sh`, suite order (AllM3GcTests) | 156 found, 156 passed | 156 found, 156 passed |
| `test_m3gc.sh`, `M3_SCAN=1` (classpath scan) | 156 / 156 | 156 / 156 |
| real app stock (31 tabs: ControlExample 26 + CustomControlExample 5) | 31 PASS, 0 EXCEPTION / BLANK / HANG | 31 PASS, 0 / 0 / 0 |
| real app m3 | 31 PASS; M3 canvas handler on 4 tabs, drawn plane on 26 | 31 PASS; 4 / 26 |
| real app gpu | 31 PASS; 4 / 26 | 31 PASS; 4 / 26 |
| Skija classes loaded m3 / gpu | 105 / 107 | 117 / 119 (+ PathBuilder, PathBuilder$_FinalizerHolder, ...) |
| skija.dll extracted (m3, gpu) | `skija_0.116.3_x64`, sha256 3ff80f3935fc0ad4.. | `skija_0.143.18_x64`, sha256 198e0aa4b24fa677.. |

The 0.143.18 runs construct `PathBuilder` (its finalizer holder loads), so the ported path code runs in the real app.

PIXEL_REBASE (`LEAF12_REALAPP_COMPARE.tsv`, every tab's screen capture of one pin against the other's):

| mode | tabs with any differing pixel | pixels | where |
|---|---|---|---|
| stock (Skija never loaded) | 4 / 31 | 51,772 | y 815-840 of 861, CustomControlExample CCombo, CTabFolder, SashForm, StyledText |
| m3 | **0 / 31** | **0** | - |
| gpu | 4 / 31 | 30,148 | y 841-860 of 861, the same 4 tabs |

The only differences are in the bottom band of the CustomControlExample shell, which is captured unpainted (white) in
some runs and painted in others; stock, which loads no Skija, varies there between the two runs as much as gpu does.
Above y 815 no pixel differs in any mode on any tab, and m3 is pixel-identical everywhere: the m116 -> m143 natives
(Clang build) change no captured pixel of these 31 tabs, so no smoke baseline needs re-recording for the bump.

## Apply procedure (after the text leaf lands)

On the leaf's worktree (any branch with `bundles/org.eclipse.swt.m3/pom.xml`), Windows paths only:

1. Jars: put `skija-shared-0.143.18.jar`, `skija-windows-x64-0.143.18.jar`, `types-0.2.0.jar` (hashes above, or fetch
   from the URLs and compare) in the Skija lib dir, default `C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib`
   (the 0.116.3 jars stay beside them), or set `M3_SKIJA_LIB=H:/m3-scratch/l12/jars`.
2. Dry run: `python -I docs/m3/gc-extension-seam/tools/m3gc_api_deltas.py --forward <wt> --dry-run`. Expect L12-01..L12-10
   and L12-17 APPLIED, the rest NO_SITE / CHECK_OK. A MISSING_ANCHOR or AMBIGUOUS row means a later leaf changed that
   text (the text leaf touches Render M3): refresh that atom's anchor from the new file, record an L12-NN correction
   row, rerun.
3. Apply: the same without `--dry-run` (it bumps the one `skija.version` line itself, L12-17), then once more: every
   row DONE / NO_SITE / CHECK_OK and `FIXED_POINT`. `git diff --stat` shows the four renderer files and the pom.
4. Gates (one JVM at a time, absolute paths):
   - `bash docs/m3/gc-extension-seam/tools/m3_skija_pin.sh <wt>` -> `skija: 0.143.18 types: 0.2.0`
   - `compile_m3gc.sh <wt> <out>/main` -> 0 errors, classes = sources' classes (1454 here); on errors
     `python -I m3gc_javac_bllse.py <out>/main/javac.log` gives the list a new atom cites
   - `m3gc_native_sig_gate.sh <wt> <lib>/skija-shared-0.116.3.jar <lib>/skija-shared-0.143.18.jar` -> all SAME
   - `M3_NATIVES=H:/m3-scratch/fidelity/natives test_m3gc.sh <wt> <out>/main <out>/tests`, then with `M3_SCAN=1`
   - `M3_NATIVES=... m3_l12_realapp.sh <wt> <out> stock m3 gpu`, then `m3gc_skija_compare.py <0.116.3 out> <out>/out <tsv>`
     against the receipts below
   - L18 artifact gates with the new pin: `build_m3_ship.sh` (nests the 0.143.18 jars by the pom), artifact smoke
5. Docs in the same commit: `docs/licenses/swt-gpu-skija-packaging-NOTICE.txt` (jar lines and SHA-256s from
   `LEAF12_JARS.tsv`, skija.dll `198e0aa4...`, and the native component list re-derived for Skia m143), the README API
   delta table, LEAF18_SHIP.md's pin line.

Overlay recipe used for the receipts (no worktree checkout, nothing pushed): `git archive <base>` of the win32 source
dirs of `binaries/org.eclipse.swt.win32.win32.x86_64/build.properties`, both M3 trees, `tests/.../junit/m3gc`,
`examples/.../controlexample` plus `examples/org.eclipse.swt.examples/src/examples_control.properties` (L12-23),
`bundles/org.eclipse.swt.m3`, `docs/m3/gc-extension-seam/tools`; `GIT_DIR=<worktree gitdir at the base>` lets
`m3gc_natives.sh --verify` read the LFS pointers (L12-21).

## Gates

All rows: `LEAF12_GATES.tsv` (evidence logs under `H:/m3-scratch/l12/logs`).

| gate | result |
|---|---|
| jars SHA-1 = Maven Central `.sha1` | 4 / 4 (`LEAF12_JARS.tsv`) |
| `m3_skija_pin.sh`: base pom / bumped pom | 0.116.3 + types 0.1.1 / 0.143.18 + types 0.2.0 |
| `compile_m3gc.sh` pristine, 0.116.3 | sources 960, classes 1454, errors 0 |
| `compile_m3gc.sh` pristine, 0.143.18 | sources 960, classes 794, **errors 17** (complete: same 17 with `-XDshould-stop.ifError=FLOW`) |
| `m3gc_api_deltas.py --forward` run 1 / run 2 | rounds 2, applied 11, FIXED_POINT / rounds 1, applied 0, FIXED_POINT |
| forward replay on a fresh `git archive` | FIXED_POINT, 4 renderer files byte-identical |
| `compile_m3gc.sh` overlay + L12-01..L12-17, 0.143.18 | sources 960, classes 1454, **errors 0** |
| `m3gc_native_sig_gate.sh` 0.116.3 -> 0.143.18 | 12 used, 12 SAME |
| `test_m3gc.sh` suite / scan, 0.116.3 and 0.143.18 | 156 / 156 in all four |
| `m3_l12_realapp.sh` stock, m3, gpu, both pins | 31 / 31 PASS in all six |
| `m3gc_skija_compare.py` | m3 0 px; stock and gpu only the bottom capture band of 4 tabs |
| backward mode replay on the base (unchanged tool path) | D01-D04, D07-D12, D16, D17 DONE; D05, D06, D13-D15 MISSING_ANCHOR (retired by leaf 6, before this leaf) |

## NOT_RUN

- The renderer switch itself on a pushed branch (by instruction: the text leaf owns Render M3 now).
- `build_m3_ship.sh` / Maven offline verify / Tycho with 0.143.18 nested in the fragment; artifact and IDE smoke on it.
- M3CanvasProbe and OwnerDrawProbe stock/m3/gpu, `test_m3fidelity.sh`, theme/language tests on 0.143.18 (the scripts
  take the pin; not run here).
- `compile_platform` gtk/cocoa (no M3 tree, no Skija on their class path).
- arm64/linux/macos Skija natives (BINARIES_SET beyond win32 x64), NATIVE_CANVAS_ADAPTER, CLEARTYPE_PARAMS, CACHE_LIMITS.
- Packaging NOTICE native component list for m143 (needs the 0.143.18 dll string census against the m143 DEPS).

## Applied (2026-10-31, branch `m3/one-skija-20261031`, stacked on the text leaf #309)

The package was rerun on the text leaf. The dry run gave the expected rows (L12-01..L12-10 and L12-17 APPLIED, the rest
NO_SITE / CHECK_OK) with no MISSING_ANCHOR, and the apply reached FIXED_POINT in 2 rounds. The second run applied 0.

| gate | result |
|---|---|
| `m3_skija_pin.sh` | skija 0.143.18, types 0.2.0 |
| `compile_m3gc.sh` | 967 sources, 1463 classes, 0 errors |
| `m3gc_native_sig_gate.sh` 0.116.3 vs 0.143.18 | 11 natives, all SAME |
| `test_m3gc.sh` (and `M3_SCAN=1`) | 171 found, 171 successful |
| `m3_l12_realapp.sh` stock / m3 / gpu | 31/31 PASS in each mode, 0 exceptions, blanks or hangs; the 0.143.18 skija.dll is loaded (sha256 198e0aa4) |

Pin-only pixel change: the text leaf at 0.116.3 against the text leaf at 0.143.18, measured by `m3gc_skija_compare.py`
(`LEAF12_REALAPP_COMPARE_APPLIED.tsv`, differing pixels against stock summed over 31 tabs):

| mode | 0.116.3 | 0.143.18 | tabs changed |
|---|---:|---:|---:|
| stock | 0 | 0 | 0 |
| m3 | 56,520 | 56,979 | 5 (+459 px) |
| gpu | 59,435 | 60,954 | 24 (+1,519 px) |

These are Skia m143 anti-aliasing differences, with no structural change. The NOTICE carries the 0.143.18 jar hashes.
The m143 revisions of HarfBuzz, FreeType, libpng and Expat are still open: the strings of this dll show zlib 1.3.0.1,
libjpeg-turbo 3.1.0 and ICU data icudt74.
