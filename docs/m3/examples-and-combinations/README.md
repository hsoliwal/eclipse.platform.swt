<!-- SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# M3 example gallery and the combination test matrix

Branch `m3/examples-and-combinations-20261030` (base `m3/superset-20261030b` `d1cb5ff10d2`: master + #271 GC seam leaf 4 +
#272 feature switches). User directives 2026-10-30: "SWT: add examples of all kinds", "add tests for all kinds of
combinations", "enhance them and perfect them all".

## Shape (atoms, patterns, recipes)

Both halves are a recipe: a template or a table plus a CPU generator, never N hand-written near-identical files.

| Part | Owner | Generator (Java, CPU, fixed point) | Output |
|---|---|---|---|
| Gallery index | `examples/org.eclipse.swt.snippets/tools/M3GalleryIndexGen.java` | scans `src/org/eclipse/swt/snippets/**` for example classes (a `main`), reads the class Javadoc, the `//GALLERY:` entry anchors, the widgets each example creates and the M3 classes it calls; joins `docs/m3/ENHANCED_API_MAP.tsv` (shipped = the surface class exists in the bundle) | `src/org/eclipse/swt/snippets/m3/gallery/M3GalleryIndex.java`, `tools/GALLERY_INDEX.tsv`, `tools/API_EXAMPLES.tsv` |
| Covering array | `tests/org.eclipse.swt.tests/tools/M3CoveringArray.java` | IPOG (Lei et al. 2007), deterministic and seedless; mixed strength: the full product of the critical factors (widget x mode x style) seeds the array, every other pair is covered pairwise; pair constraints are excluded; factor values for features (`docs/m3/feature-switches/SHIPPED.tsv`) and enhanced API rows (shipped rows of the map) are derived, so new leaves grow the matrix on the next run | `tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv` |

Rerun = fixed point: each generator prints `UNCHANGED` per output on a second run and `--check` exits 1 when an output would
change.

### Gallery (`org.eclipse.swt.snippets.m3.gallery`)

- `M3ExampleGallery`: one launcher window. A tree of categories and entries (from the generated index), the entry's
  description, mode and widget-mode overrides, feature toggles bound to `M3Features` (shipped rows switch; `NATIVE_HAS`
  rows show read-only), and Run / Run in all five modes. An entry runs in a child JVM with the same class path and
  `-Dswt.widget.mode[.<Widget>]=<mode>` and `-Dswt.widget.feature.<id>=true`: widget modes are `static final` per widget
  class (read once per JVM), so a live mode switch is a relaunch.
- `M3ExampleRunner`: runs any example's `main` in this JVM, finds the example's Display from a watcher thread, snapshots
  every shell after a delay and closes it, and reports `PASS | FAIL | TIMEOUT` with the stack. `--batch` runs every
  index entry in one JVM per mode (the all-modes proof); the same code path is what the gallery starts.
- Categories: `widgets` (every SWT and custom widget, Table/Tree/List virtual and non-virtual, StyledText, Combo,
  CTabFolder, Canvas drawing with GC paths, patterns, transforms, text, images), `modes` (live mode switcher),
  `features` (M3Features toggles), `enhanced-api` (one entry per shipped API row), `scale` (10^3..10^7 items),
  `renderer` (SWT.M3 canvas, raster and gpu), `demos` (the earlier M3 demos and snippets).
- Entry anchors: `//GALLERY: <args> | <title>` lines in an example add one entry each (one small file, many entries);
  without anchors an example is one entry.

### Combination matrix (`org.eclipse.swt.tests.junit.m3combinations`)

Factors: widget (Table, Tree, List, Combo, StyledText, Canvas, CTabFolder, CCombo, Model = the widget-free M3 model layer,
headless), render mode (viewport, original, custom, m3, gpu), style (as applicable), feature switch (shipped rows), enhanced
API call (shipped rows), operation (setItemCount, scroll top/middle/bottom, select range, sort/filter, update item,
dispose), item count (0, 1, 10^3, 10^5), theme (none, light, dark, high-contrast) and language (none, en, de, ar = right to
left, ja). Theme and language come from `M3Theme` / `M3Language` of the theme-language branch
(`m3/theme-language-20261030`): until those classes are on the class path the row's theme/language step answers
`NOT_RUN` (column `theme_language` of the results) and the rest of the row runs; when they are, the first public static
`apply|set|select(Control, String)` is called. The gallery's toolbar slot mounts `M3ThemeSwitcher` and
`M3LanguageSwitcher` (`new X (Composite, int)`, packages `org.eclipse.swt.widgets`, `custom`, `m3`) when present and is
hidden otherwise, so this branch builds without theirs.

Each row is one parameterized JUnit case (`Test_M3CombinationMatrix`) asserting:

1. no exception except the documented `SWTException` (`ERROR_WIDGET_DISPOSED`) after the dispose operation;
2. no leaks: tracked device objects (`DeviceData.tracking`) and shells return to their count before the row (a positive delta
   is re-measured once on a repeat, so a first-use cache is not a leak), and every item the row saw is disposed;
3. mode-neutral answers: the recorded differential harness (`ViewportDifferential.compareWithRecording`) records every
   step's answer and probes in the reference JVM (`-Dswt.widget.mode=original -Dswt.viewport.record=true`, all rows) and
   compares each mode's rows against it; `M3-MODE-QUERY` answers its consistency with the widget's implementation instead
   of the mode name (the one API whose answer is the mode).

Rows with `env=headless` (widget Model) run without a Display; `env=display` rows run on the hidden desktop through
`C:/m3-superset/tools/ui_lock.sh`, one JVM per mode, the reference first.

## Runs

| Step | Command (Windows paths; output under `H:/m3-scratch/swt-examples`) |
|---|---|
| SWT classes (win32 + M3 render/GPU trees) | `bash docs/m3/examples-and-combinations/tools/compile_platform.sh C:/m3-superset/swt-examples win32` |
| fixed points + javac of snippets and tests | `bash docs/m3/examples-and-combinations/tools/build.sh` |
| headless legs (generators, model, Model rows) | `bash docs/m3/examples-and-combinations/tools/run_headless.sh` |
| display legs (hidden desktop, `ui_lock.sh`, one JVM at a time) | `bash docs/m3/examples-and-combinations/tools/run_display.sh matrix\|gallery\|examples` |
| results, findings | `bash docs/m3/examples-and-combinations/tools/results.sh` |
| coverage (JaCoCo 0.8.15) | `bash docs/m3/examples-and-combinations/tools/coverage.sh` |
| theme/language rows against a local merge | `bash docs/m3/examples-and-combinations/tools/theme_language_rerun.sh <their-ref>` |

## Results

Base `d1cb5ff10d2` + this branch; win32 classes of `compile_platform.sh` (924 sources, 0 errors, M3 render/GPU trees
included), natives `swt-win32-4975r19`, JDK 21, hidden desktop. Files: `results/COMBINATION_RESULTS.tsv` (one line per
row: reference leg, own-mode leg, theme/language step, leak, failure, location, message), `results/stacks/` (the stack
of every failing row), `results/EXAMPLE_MODES.tsv` (every gallery entry in the five modes), `results/COVERAGE.tsv`,
`FINDINGS.tsv` (one line per recurring failure class, with an owner).

### Combination matrix (2026-10-30/31)

| Measure | Value |
|---|---|
| Covering array | 278 rows, 9 factors (widget 9, mode 5, style 9, feature 2, api 23, op 8, count 4, theme 4, language 5); 167 forbidden pairs; every allowed pair covered, every allowed widget x mode x style triple covered (195 seeds), 0 rows with a forbidden pair, 0 implied-forbidden pairs |
| Headless rows (Model) | 5 recorded + 4 compared: 9 PASS |
| Display rows | 273 recorded in `original` (271 PASS, 2 TIMEOUT at the 900 s row limit); 233 compared in their own mode (viewport 113, custom 41, m3 39, gpu 40): 224 PASS, 8 FAIL, 1 NOT_RUN (its reference timed out) |
| Row status | **268 PASS, 8 FAIL, 2 TIMEOUT** |
| Leaks | 0 (tracked device objects, shells and children back to their count after every row; 3 rows grew on first use only and were clean on the repeat) |
| Exceptions | 0 unexpected (the documented `SWTException` after dispose is an answer) |
| Theme/language step | NOT_RUN in 130 rows on this base (the theme and language classes are not on its class path; the rest of those rows ran); run against a local merge with PR #286 below |

Failures, by class (details, stacks and every row in `FINDINGS.tsv` and `results/`):

| Finding | Rows | Cause (file:line) | Owner |
|---|---|---|---|
| `M3-MODE-QUERY` answers a mode the widget does not run: Table gpu (`r0045`) and List custom (`r0099`) report `viewport` while their drawn plane runs; StyledText viewport/custom (`r0124`, `r0132`) report `original` while `StyledText.VIEWPORT` is on | 4 | `M3Mode.supported` lists the drawn modes for Tree only (`M3Mode.java:100`), though `TableDrawnPlane.java:32` and `ListDrawnPlane.java:31` implement custom/m3/gpu; `M3Mode.gate` maps custom widgets to their nearest `org.eclipse.swt.widgets` superclass (`M3Mode.java:92`: StyledText -> Canvas, CTabFolder -> Composite) instead of their own gate (`StyledText.java:745`, `CTabFolder.java:143`); Canvas m3/gpu (`Display.java:3085`) is not in the table either | enhanced API (M3-MODE-QUERY) |
| `M3-PREFETCH` hears prefetch requests in custom/m3 but none in original for the same scroll | 2 (`r0024` Table custom, `r0081` Tree m3) | prefetch events come from the viewport/drawn planes only; the API's mode-neutral contract (ENHANCED_API.md) does not hold in `original` | enhanced API (M3-PREFETCH) |
| `M3-RESIDENCY` resident range `[0..999]` in original, `[987..999]` in custom (`r0023`, Table 1000 rows scrolled to the end) | 1 | the resident window is a viewport-plane notion; original answers every row; contract to be decided (mode-neutral or documented as mode-dependent) | enhanced API (M3-RESIDENCY) |
| Virtual Tree, 10^5 roots, `setTopItem (getItem (50000))`: top 49,987 in original, 50,000 in m3 (`r0074`) | 1 | the reference is the outlier: in original the fork's lazily materialized native children (`Tree.ensureVirtualNativeChildren`, `Tree.java:522`) leave the item at the bottom of the page instead of the top; also 800 s for this row in original | viewport (Tree virtual topology, original mode) |
| TIMEOUT: Tree CHECK, 10^5 plain items, `setItemCount` (`r0076`, original) | 1 | native item creation one by one (`Tree.setItemCount` -> `TreeItem.<init>` -> `Tree.createTreeItem`, `Tree.java:5956` / `3398`) over 900 s | viewport / stock behaviour |
| TIMEOUT: `M3RowMath.visibleRowCount` on a plain Tree of 10^5 roots in original (`r0200`) | 1 | `M3TreeWalk.childCount` (`M3TreeWalk.java:111`) calls `Tree.getItemCount`, which walks the native siblings (`Tree.nativeChildCount`, `Tree.java:324`): quadratic; the very cost M3-ROW-MATH claims to avoid (swt#882/#904) | enhanced API (M3-ROW-MATH) |

Observations (not failures): plain Tree with 10^5 items in original takes 186-900 s for dispose, setItemCount and
scrolls (`FINDINGS.tsv`, class `SLOW`); the same rows run in seconds in viewport mode. A virtual Tree in original
materializes native children one by one with a native sibling walk per item (`Tree.createTreeItem` ->
`bindVirtualTopology` -> `nativeChildIndex`, `Tree.java:3417`/`357`/`334`): quadratic.

### Gallery and examples (2026-10-31)

- Index: **112 entries from 52 example classes** (widgets 46, modes 7, features 1, enhanced-api 23, scale 17, renderer 2,
  demos 16); every one of the 22 shipped enhanced API rows has an example (`tools/API_EXAMPLES.tsv`, 0 MISSING).
- New: 8 example classes with 78 entries (widgets: `M3ControlsExample` 13, `M3ItemWidgetsExample` 14,
  `M3CustomWidgetsExample` 9, `M3CanvasDrawingExample` 10; modes: `M3ModeSwitcherExample` 7; features:
  `M3FeatureSwitchExample` 1; scale: `M3ScaleExample` 12; renderer: `M3RendererCanvasExample` 2) and the gallery
  (`M3ExampleGallery`, `M3GalleryModel`, `M3ExampleRunner`, generated `M3GalleryIndex`).
- Gallery tests (`org.eclipse.swt.tests.junit.m3gallery`, hidden desktop): 22 found, 22 passed (window and tree, description,
  mode/widget-mode/feature command lines, child JVM exit, toolbar buttons and children ending with the gallery, switcher
  slot, in-process runner pass/fail/snapshot, generator and model tests).
- Every entry in every mode (`M3ExampleRunner --batch`, one JVM per mode, a blocked entry is recorded with the example
  thread's stack and the batch resumes): 560 runs, **513 PASS, 30 TIMEOUT, 17 FAIL**; per mode viewport 110/1/1,
  original 98/13/1, custom 101/6/5, m3 102/5/5, gpu 102/5/5 (PASS/TIMEOUT/FAIL). **93 of 112 entries pass in all five
  modes**; `results/EXAMPLE_MODES.tsv` records, per entry, the modes it passes, its implemented modes and the reason of the
  first non-pass. Why a mode cannot run an entry:
  - original: native List/Combo of 10^6 items (`List.setAllItems`, `List.java:1875`; `Combo.callWindowProc`) and native
    Tree of 10^5..10^7 items (`Tree.createTreeItem`) take more than 90 s: stock behaviour, the reason the viewport exists;
  - custom/m3/gpu: `NullPointerException` in `RetainedRows.paint` (`RetainedRows.java:116`, renderer null outside a
    frame) for 4 entries whose table is relaid out (`M3ModeSwitcherExample Table`, `M3FeatureSwitchExample`,
    `M3PrefetchReleaseExample`, `M3TextSegmentsExample`); `List.computeSize` measures every one of 10^6 items
    (`VirtualListWidth.measurePending` -> `List.measureStoreText`, `List.java:2474..2495`) for 4 million-item entries;
  - all modes: `M3DeltaStreamExample` streams deltas in `main` and crashes when its window closes early
    (`M3Model.size`, `M3Model.java:214`, from `M3DeltaStreamExample.java:144`); `M3HiddenTabDeltasExample` does not
    return after its shells close (`Display.sleep` in its idle pump).
- Snapshot observation: `Shell.print` draws two or more `DOUBLE_BUFFERED` canvases all at (0,0) in every mode, original
  included (finding `SNAPSHOT_ORIGIN`), so multi-canvas snapshots cannot show the M3 canvas.

### Leaf 6 (PR #284) before/after: StyledText and Canvas under m3/gpu

Local merge of this branch with `m3/gc-leaf6-fidelity-20261030` (`git merge-tree`, tree `b8d9fb9fe16`, clean; win32 927
sources, 0 errors); the 18 StyledText/Canvas rows of the matrix in m3/gpu plus the same rows with 1,000 items scrolled to
the middle (36 rows), recorded in original and compared in m3 and gpu, with the pixels step (`-Dm3.combinations.pixels`).
Table: `results/LEAF6_BEFORE_AFTER.tsv`.

| Capture | Group | Before (base): equal / mean abs channel difference | After (merge) |
|---|---|---|---|
| answers | 36 rows x (original, m3, gpu) | 72 PASS | 72 PASS |
| WM_PRINT (harness `render`) | StyledText, Canvas, m3 and gpu | 100.00 % / 0.00 | 100.00 % / 0.00 |
| Control.print (composited) | Canvas text rows, m3 | 93.54 % / 13.90 | 94.12 % / 6.23 |
| Control.print (composited) | Canvas text rows, gpu | 93.67 % / 8.09 | 93.50 % / 6.48 |
| Control.print (composited) | StyledText, m3 / gpu | 77.99 % / 4.06, 70.39 % / 6.24 | 77.54 % / 3.55, 71.98 % / 4.80 |

Reading: StyledText is not drawn by the M3 renderer on either tree (`ExternalCanvasHandler.isActive` returns false for
StyledText, `ExternalCanvasHandler.java:119`), so WM_PRINT shows it identical to original before and after; WM_PRINT
repaints through the native Paint path and is blind to the M3 surface. The composited captures see the M3 canvas: leaf 6
halves the mean channel difference of the Canvas text rows in m3 (13.90 -> 6.23) and lowers it in gpu (8.09 -> 6.48).
Composited captures are not stable between runs on the hidden desktop (StyledText rows flip between 0 % and 100 %), so
their per-row values are a similarity metric, never a gate.

### Theme and language (PR #286) against a local merge

Local merge with `m3/theme-language-20261030` (tree `1bea339c3af`, clean; win32 943 sources, 0 errors; the platform copy
now carries `M3Languages.tsv`). The 105 matrix rows whose theme or language is not `none` (rows of 10^5 items left out:
their theme/language step is independent of the count and they take minutes in original), through
`M3Themes.apply/reset` and `M3Language.setLocale`, restored after every row. `results/THEME_LANGUAGE_MERGE.tsv`:
102 display rows recorded in original, all PASS with the theme and language applied (themes: light 24, dark 25,
high-contrast 15 rows; languages: en 28, de 22, ar (right to left) 17, ja 27 rows); 80 compared in their modes: 76 PASS, 4 FAIL, the same four findings as
without themes (M3-RESIDENCY `r0023`, M3-MODE-QUERY `r0045`/`r0099`, M3-PREFETCH `r0081`); 3 headless rows recorded + 2
compared, PASS; 0 leaks. No failure is introduced by a theme or a language.

On the merged trees the generators grow on their own: theme-language adds `M3ThemeLanguageExample` (113 entries) and makes
M3-LANG-ID and M3-THEME-HOOKS shipped rows (24) with two more shipped feature switches, so the covering array becomes 290
rows; leaf 6 changes the Canvas activation, so the index derives Canvas's implemented modes as `original`.
