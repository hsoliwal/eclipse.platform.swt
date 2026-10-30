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

See `results/` and `FINDINGS.tsv` (filled by the runs below).
