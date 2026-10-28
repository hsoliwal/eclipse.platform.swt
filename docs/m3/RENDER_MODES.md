# Render modes: drawn planes for Tree, Table and List

Status: design (deliverable 1). Implementation status per leaf is at the end.

## The framing: drawn modes are viewport modes with a different plane

The viewport approach is king. `custom`, `m3` and `gpu` are **not new widgets**; they are the viewport
mode with another plane.

- The mediated model is the single source of truth in every mode: topology, visible projection, lanes, scroll
  metrics, resident window and facades (`VirtualTreeTopology`, `VirtualTreeVisibleProjection`,
  `VirtualTreeViewport`, `VirtualScrollMetrics`, `VirtualTreeResidentPlan`, `TreeViewport` and its Table and
  List siblings). No mode keeps a copy of the model.
- The only difference from the native viewport is the plane. In `viewport` mode a native TreeView, ListView or
  ListBox holds the resident window and paints it. In a drawn mode a drawn surface (GC, the M3 renderer on the CPU, or the M3
  renderer on the graphics hardware) paints exactly the resident window.
- One planner drives every plane, with the phases and names of `TreeViewport`: capture, plan, retire/insert,
  commit, bind, restore focus. A plane only adds a paint of the committed window.
- No per-mode model copies and no O(n) work per frame: a frame paints the rows that intersect the damage, found
  by arithmetic over the resident window.

## Modes and how one is selected

| `-Dswt.widget.mode` | Plane | Pixels |
|---|---|---|
| `viewport` (default) | native control holding the resident window | the platform's |
| `original` | stock SWT, no mediation | the platform's |
| `custom` | drawn plane, backend `GC` | SWT `GC` (GDI) |
| `m3` | drawn plane, the M3 renderer on the CPU | M3 raster into the plane's DIB section (through the Skija library) |
| `gpu` | drawn plane, the M3 renderer on the graphics hardware | M3 over OpenGL (Skija's `DirectContext.makeGL` on SWT's WGL natives), read back into the DIB |

`-Dswt.widget.mode.Tree=custom` selects a mode for one widget class, as for every widget. Naming (brief rule 14):
nothing of ours is called Skija or Skia; the mode is `m3` (`WidgetMode.M3`) and our classes are `M3*`
(`M3RowCanvas`, `M3Gpu` in `org.eclipse.swt.internal.render.m3`, source folder `Eclipse SWT Render M3`). The renames
were made by recipe ([render-modes/recipe/render_names_recipe.sh](render-modes/recipe/render_names_recipe.sh):
OpenRewrite `ChangeType`, `ChangePackage`, `ChangeMethodName`; pass 1 CHANGED, pass 2 UNCHANGED). The library's own
binding classes (`io.github.humbleui.skija`, `io.github.humbleui.types`) keep their package because its prebuilt
natives bind to it; the package `org.eclipse.swt.internal.render.m3` is the one adapter that touches them, loaded by
name, so SWT runs without the library when no drawn mode needs it.

Selection, in code:

- The mediator keeps its own query, `WidgetMode.of ("Tree", VIEWPORT) == VIEWPORT`. A drawn mode requested for
  Tree is not in that list, so `WidgetMode` falls back to `VIEWPORT` there and the mediator stays on. Nothing in
  the mediator changes.
- The plane asks `WidgetMode.of ("Tree", VIEWPORT, CUSTOM, M3, GPU)` once, when its class initializes, and installs
  a drawn plane for `CUSTOM`, `M3` or `GPU`. A backend that cannot start (no library jars on the class path, no
  OpenGL context) logs at `info` and falls back: `gpu` to `m3` to `custom`.
- `original` keeps stock SWT everywhere (it is the compatibility reference).

## The plane and its two stages

A plane owns the resident window's realization. The phases are the mediator's:

| Phase | Native plane (`viewport`) | Drawn plane |
|---|---|---|
| capture | read native caret, selection, check state of resident rows | same (stage 1), lanes only (stage 2) |
| plan | `VirtualTreeResidentPlan` diff of old and new window | same planner |
| retire/insert | `TVM_DELETEITEM`/`TVM_INSERTITEM` of changed rows only | same on the behaviour plane (stage 1); no native rows (stage 2) |
| commit | plan committed, keys and handles | same; the drawn plane's row cache is keyed by plan keys |
| bind | facades bound to resident rows | same |
| restore focus | caret restored selection-neutrally | same |
| paint | comctl32 paints the window | the drawing core paints the window |

**Stage 1 (this programme's first leaves).** The native control stays as the **behaviour plane**: it still holds
the resident window, takes keyboard and mouse input, owns the caret and answers hit tests and item rectangles,
exactly as in `viewport` mode, so every public answer, event and SetData is the viewport's by construction. It
never paints to the screen: its paint runs into an off-screen scratch bitmap, only for its side effects (SetData
of rows coming into view, label measurement, custom-draw notifications), and the drawing core paints the window.
A paint of which nothing is visible (a window off the screen) draws nothing, but the native control still runs its
paint as natively, through its own `BeginPaint` on the update region the plane restores: an empty ListView paint
still asks for the row at the top (Table differential variant 2, SD:68 under `setScrollWidth`). The drawn rows use the behaviour plane's geometry (row rectangles, label rectangles, column rectangles), so what
the user sees is where the user clicks.

**Stage 2 (later leaves).** The behaviour plane's duties move to the model leaf by leaf, each proved by the same
recorded differential: label measurement and SetData timing from the projection, keyboard (Initiative 31
`ListLikeModel` anchor/current pattern, the measured page rules of the Tree viewport), mouse and hit testing from
row arithmetic, focus. When the last duty has moved, the native control is no longer created in drawn modes.

## Drawing core (one for the three drawn modes)

```
RowSource  --rows on the plane-->  RowRenderer (theme)  --ops-->  RowCanvas (backend)
                                        |                              GcRowCanvas     custom
                                   Theme defaults (packed ints)        M3RowCanvas     m3   (CPU raster)
                                                                       M3RowCanvas     gpu  (OpenGL, M3Gpu)
```

- **`RowSource`**: the rows of the resident window that intersect the damage, top to bottom. Per row: plan key,
  facade, row rectangle, label rectangle, depth, state bits (selected, focused, hot, expanded, has children,
  checked, grayed). Stage 1 reads them from the behaviour plane (O(rows in the damage) native queries); stage 2
  from the projection and lanes. The first row is found by arithmetic (`(damageTop - planeTop) / itemHeight`), the
  loop stops at the first row below the damage (Initiative 31 T1/T2).
- **`RowRenderer`**: one per theme, shared by Tree, Table and List (a List is a Table with one column and no
  header; a Table is a Tree of depth 1). It paints a row's background (selection, hot, stripes), expander,
  check box, image, text per column, grid lines and focus rectangle, in this order, through a `RowCanvas`. It
  emits SWT's owner-draw events (`MeasureItem`, `EraseItem`, `PaintItem`, Initiative 31 T9 order) when the
  application listens (stage 2; in stage 1 the behaviour plane emits them).
- **`RowCanvas`**: the backend. `fillRect`, `line`, `text`, `image`, `part` (a theme part such as an expander
  glyph or a check box), clip, begin and end of a frame. Colours are ARGB ints, fonts are SWT `Font`s.
- **Theme defaults**: a table of packed ints (colours, sizes, glyph style) resolved once per theme, keyed with
  Swing/FlatLaf names (`Tree.selectionBackground`, `Tree.hash`, `Table.gridColor`, ...).

Backends:

| Backend | Frame | Text | Images and parts |
|---|---|---|---|
| `GcRowCanvas` (custom) | paints into the plane's back buffer DC (a DIB section kept per widget) | GDI `DrawText` with the native font | `GC.drawImage`; theme parts through uxtheme `DrawThemeBackground` |
| `M3RowCanvas` (m3) | `Surface.makeRasterDirect` over the same DIB section, resized only on size change; damage `BitBlt`-ed | a library font per native font (LOGFONT face, pixel height, weight, italic); shaped `TextLine` LRU keyed by (font, string) | SWT image pixels converted once, LRU; theme parts rendered once by uxtheme on black and white (alpha recovered) into an image cache |
| `M3RowCanvas` (gpu) | an offscreen GPU render target per widget (`Surface.makeRenderTarget` on the thread's OpenGL context, `M3Gpu`); after the frame, `flush` and `readPixels` of the damage straight into the DIB | as m3 (glyph atlas on the GPU) | as m3 (uploaded by the library) |

Work split (brief rule 5): the UI thread does O(rows in the damage) per frame. Font, text and image caches are
bounded LRUs. The OpenGL context is one per UI thread (a hidden, disabled, never-shown popup window, not a child of
any widget), created by the first gpu plane and freed with the last one.

## Look and feel: the theme API (rule 16, leaf T1)

Every visual of a drawn row comes from a theme object (`org.eclipse.swt.internal.render.Theme`): colours, the look
of selection, expanders and check boxes, the selected text colour, metrics (text inset; row height and indent are
-1 = the platform's in every built-in theme, so geometry and answers never change), optional glyph images
(expander open/closed, check box on/off/mixed) and optional painters (`Theme.RowPainter` replaces the whole row,
`Theme.CellPainter` the cell content; both can call back into the default painting). Themes are immutable; new ones
come from `Theme.builder (name)` or `theme.derive (name)`.

`Themes` is the registry and the selection (like Swing's `UIManager`):

- startup: `-Dswt.widget.theme=<name>` and `-Dswt.widget.theme.<Widget>=<name>` (`swt.lookAndFeel[.<Widget>]` is
  read as an alias); an unknown name logs once and falls back to `native`;
- run time: `Themes.register (theme)` for user-defined themes (the built-in names are reserved) and
  `Themes.select (widget | null, theme | null)`; a selection wins over the properties, `null` goes back to them;
- change propagation: `Themes.generation ()` and listeners; every `DrawnPlane` re-reads its theme per frame by
  generation and repaints when the selection changes, so a theme switch is live.

| Theme | Donor | Selection | Expander | Check box | Other values |
|---|---|---|---|---|---|
| `native` (default) | the platform's uxtheme parts through SWT's own natives (`ThemeParts`) | `Explorer::TreeView` `TVP_TREEITEM` (`ListView` `LVP_LISTITEM` for Table; a List fills with the system highlight colours, as the unthemed list box does) | `TVP_GLYPH`/`TVP_HOTGLYPH` chevrons at their part size | `BUTTON` `BP_CHECKBOX` at its part size | widget colours and font; grid E3E3E3; focus rectangle with keyboard cues |
| `swing` | Swing Metal (Ocean); values measured from the JDK by [SwingThemeProbe](render-modes/evidence/swing-theme.output.txt); SWTSwing/prototype-swing key names | `Tree.selectionBackground` B8CFE5 (DDE6F0 unfocused) | 9 x 9 handle, border 6382BF, minus/plus 333333, `Tree.hash` stub | 13 x 13 box, border 6382BF, check mark | foreground 333333, `Table.gridColor` 7A8A99, focus lines A3B8CC |
| `initiative31` | swt-initiative31/prototype-skija `TreeItemRenderer`, `DefaultCheckboxRenderer`, `DefaultColorProvider` (EPL-2.0, adapted in `Initiative31Look` with its notice) | `Table.SELECTION_COLOR` E0EEFE; hover `COLOR_YELLOW` | grey triangle, right or down | 12 px rounded box, border gray128, fill 0062B8 checked / C0C0C0 grayed | focus on every selected row of the focus control |

Proof: the headless renderer tests (`Test_org_eclipse_swt_internal_render_RowRenderer`, a recording canvas, every
look and the user-theme hooks) and `Test_org_eclipse_swt_internal_render_Themes` (registry and selection), plus the
recorded differential run per theme (`-Dswt.widget.theme=swing|initiative31` in `custom` mode): every non-pixel
probe must stay equal to stock, pixels are similarity rows, like a mode.
## GPU route (Java only, user decisions 2026-10-17)

The user first chose Direct2D on win32 and OpenGL elsewhere, then ruled that every new line is Java (brief rule 13):
no C, C++ or JNI of our own and no native builds. A minimal JNI shim for Direct2D had been committed in R5
(`403c384e5f`, `Eclipse SWT Render Direct2D/win32/library/swt_m3_d2d.cpp`, built with mingw-w64); it is removed in the
following commit, with its Java binding (`Direct2D`, `Direct2DRowCanvas`) and its build script. The Java-only routes,
in the user's order, were probed on this host (Windows 11, JDK 21, invisible test desktop; 300 x 380 pixels, a frame =
clear + one filled row + 20 text rows; sources and output in [render-modes/evidence](render-modes/evidence),
`gpu-route-java-only.output.txt`):

| Route | Pixels | First setup | Per frame |
|---|---|---|---|
| 1. the M3 renderer on the GPU: Skija's `DirectContext.makeGL` on an OpenGL context made through SWT's own WGL natives (hidden popup), offscreen render target, `readPixels` of every frame | correct (fill, text) | 833 ms (includes loading the library's natives) | **0.72 ms** including the read back |
| 2. Direct2D through the JDK FFM API (`java.lang.foreign`, preview in JDK 21: `--enable-preview --enable-native-access=ALL-UNNAMED`), DC render target, COM methods through their vtable slots, hardware | correct (fill, DirectWrite text) | 342 ms | 1.22 ms |
| 2, software target | correct | 6 ms | 0.57 ms |

Decision:

- **gpu = route 1 on every platform**: it needs no preview flag on JDK 21, its natives come with the library, and the
  same code serves GTK and Cocoa (their runs: NOT_RUN on this host). A swapBuffers-based variant measured 21.8 ms per
  frame earlier (vsync); the offscreen target with read back avoids it.
- **Route 2 works** and is kept as evidence: Direct2D can be driven from Java once SWT may use the FFM API (final in
  JDK 22; preview with M3jdk21). SWT's `COM.VtblCall` alone cannot do it: it passes the object as the first argument
  (no `D2D1CreateFactory`) and passes no float arguments in registers.
- Dawn (WebGPU) and Skia's C++ are pattern donors only (rule 13).

## Proof

- **Contract (gate):** the recorded differentials of Tree (plain and check+columns variants), Table and List run
  with `-Dswt.widget.mode=custom|m3|gpu` against the stock recording of the official SWT 3.135.0 jar. Every
  non-pixel probe (answer, scroll bar, selection, caret, events, geometry, hit tests) must be equal.
- **Pixels (metric, not a gate):** drawn modes differ by design. The harness reports, per seed, the share of
  pixels equal to the stock capture and the mean absolute channel difference, instead of failing on the hash; so do
  the steps whose answer is the pixels (Table GEOMETRY and DPI scripts), with a PNG of each step in every run.
- **Benchmark:** [render-modes/bench](render-modes/bench) (deliverable 2): per widget x mode x operation, wall
  time and allocated bytes, on the invisible desktop, one JVM per mode.

## Benchmark (leaf R2)

Program `RenderModeBenchmark` (test tree, public API only, so it also runs on the official jar), driver
[render-modes/bench/run_bench.sh](render-modes/bench/run_bench.sh), table generator
[render-modes/bench/bench_table.py](render-modes/bench/bench_table.py). One JVM per widget x mode x operation on the
invisible desktop (90 s budget per JVM; a JVM over budget is a `timeout` row). Every widget has 1,000,000 rows: Tree
and Table `SWT.VIRTUAL` with `setItemCount`, List `setItems` (List has no virtual style); the Tree's first root has
10,000 children. Each measured repetition ends with `update ()`, so the paint it causes is included. Cells: median
ms over 10 repetitions after 2 warm-up runs (create: 3 after 1; selectAll: 5), and the median bytes the UI thread
allocated per repetition. `official` is the SWT 3.135.0 jar.

Operations: `create` (new widget + 1,000,000 rows + first paint), `setItemCount` (alternately 999,000 and 1,000,000),
`scrollPage` and `scroll10k` (`setTopItem`/`setTopIndex` by one page and by 10,000 rows), `paint` (`redraw`),
`selectAll`, `expand10k`/`collapse10k` (the 10,000-child root), `selectFar` (`setSelection` of row 900,000 and
`showSelection`).

R2 run, 2026-10-17, build `rm20261017a` (this branch at the integration head), raw rows in
[render-modes/bench/r2-20261017-viewport-original-official.tsv](render-modes/bench/r2-20261017-viewport-original-official.tsv):

| widget | operation | official | original | viewport |
|---|---|---|---|---|
| Tree | create | timeout | 165.261 (528 KB) | 21.358 (101 KB) |
| Tree | setItemCount | timeout | 0.236 (13 KB) | 4.918 (41 KB) |
| Tree | scrollPage | timeout | 26.404 (515 KB) | 10.683 (91 KB) |
| Tree | scroll10k | timeout | timeout | 10.545 (101 KB) |
| Tree | paint | timeout | 8.483 (263 KB) | 2.957 (26 KB) |
| Tree | selectAll | timeout | 18.673 (303 KB) | 2.524 (34 KB) |
| Tree | expand10k | timeout | 10.606 (343 KB) | 6.631 (44 KB) |
| Tree | collapse10k | timeout | 17.444 (272 KB) | 11.837 (49 KB) |
| Tree | selectFar | timeout | timeout | 12.532 (86 KB) |
| Table | create | 34.672 (78 KB) | 46.369 (79 KB) | 32.862 (79 KB) |
| Table | setItemCount | 0.726 (1 KB) | 0.848 (1 KB) | 0.421 (1 KB) |
| Table | scrollPage | 11.574 (51 KB) | 8.958 (49 KB) | 13.797 (43 KB) |
| Table | scroll10k | 7.004 (43 KB) | 4.034 (43 KB) | 5.517 (36 KB) |
| Table | paint | 3.236 (25 KB) | 1.680 (25 KB) | 2.240 (25 KB) |
| Table | selectAll | 2.855 (25 KB) | 2.205 (25 KB) | 6.056 (25 KB) |
| Table | selectFar | 7.278 (27 KB) | 3.673 (27 KB) | 2.554 (27 KB) |
| List | create | timeout | timeout | 52.172 (7820 KB) |
| List | setItemCount | 2554.691 (125 KB) | 2407.163 (125 KB) | 443.855 (136 KB) |
| List | scrollPage | 2.621 (1 KB) | 2.329 (1 KB) | 4.226 (7 KB) |
| List | scroll10k | 2.764 (1 KB) | 2.264 (1 KB) | 9.964 (5 KB) |
| List | paint | 1.222 (1 KB) | 1.103 (1 KB) | 1.136 (1 KB) |
| List | selectAll | 51.523 (1 KB) | 56.404 (1 KB) | 1.766 (2 KB) |
| List | selectFar | 10.433 (1 KB) | 9.148 (1 KB) | 4.801 (6 KB) |

Reading it:

- Stock SWT cannot hold 1,000,000 virtual Tree rows: `setItemCount (1,000,000)` on the official jar does not return
  within 90 s, so every official Tree operation times out in its setup. The fork's `original` Tree keeps a growing
  native prefix: `scroll10k` and `selectFar` time out there (`getItem` far away grows the prefix row by row).
- Stock List cannot create 1,000,000 items four times in 90 s (`create` times out); one creation fits in the setup
  of the other operations.
- Candidates for the viewport owners (reported, not changed here): Tree `setItemCount` costs 4.9 ms in `viewport`
  against 0.24 ms in `original`; Table `scrollPage` 13.8 ms against 9.0 ms and `selectAll` 6.1 ms against 2.2 ms;
  List `scroll10k` 10.0 ms against 2.3 ms and `scrollPage` 4.2 ms against 2.3 ms.

## Tree drawn plane: results (leaves R3, R4)

The `m3` mode was named `skija` when these runs were made; the rows say `m3`.

Build `rm20261017c`: `TreeDrawnPlane` (stage 1) with `GcRowCanvas` (custom) and `M3RowCanvas` (m3, then `SkijaRowCanvas`; library
0.116.3 jars and the backend jar on the class path). Hooks in `Tree.java` come from the recipe
[render-modes/recipe](render-modes/recipe) (`render_modes_recipe.sh`: pass 1 CHANGED, pass 2 UNCHANGED): the
`drawnPlane` field, `TreeDrawnPlane.install` at the end of `createWidget`, one delegation in `callWindowProc`
for `WM_PAINT`/`WM_PRINTCLIENT` of the TreeView, and the release in `releaseWidget`.

Recorded differential `Test_org_eclipse_swt_widgets_Tree_ViewportDifferential` against the official SWT 3.135.0
recording made with this build's tests (both variants, 12 seeds x 150 steps each; probes answer, bar, selection,
caret, events, geometry and hit tests, plus the column probes in the check+columns variant), runner
[render-modes/tools/tree_differential_modes.sh](render-modes/tools/tree_differential_modes.sh), hidden desktop:

| Mode | Plain variant | Check + columns variant | Pixels equal to stock (mean of 12 seeds) |
|---|---|---|---|
| `custom` | pass | pass | plain 99.52 %, columns 85.57 % |
| `m3` | pass | pass | plain 94.08 %, columns 83.33 % |
| `viewport` (same recording, for reference) | pixels of seed 0 differ from stock (owner's leaf; reported) | pass | strict gate |

Per-seed similarity: [render-modes/evidence/tree-similarity-rm20261017c.tsv](render-modes/evidence/tree-similarity-rm20261017c.tsv).
The columns variant differs mostly in the full-row selection over the sort column (the sort colour shows through
the selection part) and in the text inset of columns after the first; both are theme work, after the contract and
performance leaves.

Benchmark of the Tree in the drawn modes (same program as R2, build `rm20261017c`, raw rows in
[render-modes/bench/r3-20261017-tree-viewport-custom-m3.tsv](render-modes/bench/r3-20261017-tree-viewport-custom-m3.tsv)):

| widget | operation | viewport | custom | m3 |
|---|---|---|---|---|
| Tree | create | 23.055 (100 KB) | 28.297 (107 KB) | 28.792 (124 KB) |
| Tree | setItemCount | 4.452 (41 KB) | 5.931 (45 KB) | 5.168 (50 KB) |
| Tree | scrollPage | 5.750 (90 KB) | 13.386 (95 KB) | 10.793 (104 KB) |
| Tree | scroll10k | 6.990 (100 KB) | 14.565 (105 KB) | 11.681 (116 KB) |
| Tree | paint | 3.940 (26 KB) | 5.957 (30 KB) | 3.882 (35 KB) |
| Tree | selectAll | 4.010 (34 KB) | 5.168 (38 KB) | 5.175 (45 KB) |
| Tree | expand10k | 6.473 (45 KB) | 6.995 (49 KB) | 7.648 (54 KB) |
| Tree | collapse10k | 17.353 (48 KB) | 20.105 (53 KB) | 15.243 (58 KB) |
| Tree | selectFar | 8.984 (86 KB) | 10.615 (90 KB) | 10.215 (97 KB) |

Stage 1 pays for two passes per frame (the behaviour plane's paint into the back buffer, then the drawn rows):
`paint` costs 6.0 ms in `custom` against 3.9 ms in `viewport`; `m3` is at 3.9 ms because its text lines are
shaped once and cached. The optimization leaves (R7) start with the behaviour pass.

## Tree drawn plane in the gpu mode (leaf R5) and the shared plane

`DrawnPlane` now holds the stage-1 frame for every row widget (behaviour pass, drawn pass, present);
`TreeDrawnPlane` only reads the Tree's rows. Build `rm20261017f` (before that refactor) with the Direct2D shim, since removed (rule 13):

| Mode | Plain variant | Check + columns variant | Pixels equal to stock (mean of 12 seeds) |
|---|---|---|---|
| `custom` | pass | pass | plain 99.52 %, columns 85.57 % |
| `m3` | pass | pass | plain 94.09 %, columns 83.37 % |
| `gpu` (the removed Direct2D shim, hardware) | pass | pass | plain 95.22 %, columns 84.49 % |

Per-seed similarity: [render-modes/evidence/tree-similarity-rm20261017f.tsv](render-modes/evidence/tree-similarity-rm20261017f.tsv).

Benchmark, Tree, four modes, 20 repetitions (min / median ms), build `rm20261017f`, raw rows in
[render-modes/bench/r5-20261017-tree-four-modes.tsv](render-modes/bench/r5-20261017-tree-four-modes.tsv). The host ran
out of commit memory during this run (memory gate at 0 GB, `fork` failures): the `exit` cells are JVMs that could not
start, not widget failures, and the medians are noisy; the minimums are the least disturbed numbers.

| widget | operation | viewport | custom | m3 | gpu |
|---|---|---|---|---|---|
| Tree | create | 24.672 / 32.449 (100 KB) | exit 1 | 19.460 / 19.995 (123 KB) | 201.491 / 212.976 (117 KB) |
| Tree | setItemCount | 4.315 / 5.292 (41 KB) | 3.757 / 9.663 (45 KB) | 2.957 / 4.336 (50 KB) | 4.958 / 6.925 (45 KB) |
| Tree | scrollPage | 7.060 / 10.354 (92 KB) | 8.142 / 10.480 (95 KB) | 7.654 / 9.244 (107 KB) | 9.347 / 11.066 (96 KB) |
| Tree | scroll10k | 8.677 / 12.099 (101 KB) | 12.759 / 22.004 (106 KB) | 8.256 / 10.565 (117 KB) | 9.271 / 11.553 (105 KB) |
| Tree | paint | 2.538 / 4.198 (26 KB) | 1.855 / 3.908 (30 KB) | 2.772 / 3.449 (35 KB) | 3.731 / 5.771 (30 KB) |
| Tree | selectAll | 5.404 / 17.770 (34 KB) | 3.543 / 5.800 (38 KB) | 4.441 / 5.192 (45 KB) | 5.291 / 6.309 (39 KB) |
| Tree | expand10k | 3.310 / 5.431 (44 KB) | 5.831 / 10.562 (49 KB) | 4.732 / 5.529 (54 KB) | 6.294 / 7.050 (49 KB) |
| Tree | collapse10k | exit 1 | 14.211 / 17.713 (53 KB) | 10.868 / 15.593 (57 KB) | 13.872 / 15.725 (53 KB) |
| Tree | selectFar | exit 127 | 7.616 / 9.397 (91 KB) | 9.057 / 10.324 (97 KB) | 11.225 / 12.147 (92 KB) |

- `gpu` `create` cost about 200 ms per Tree with the shim: each Tree made its own Direct2D context. The OpenGL route
  shares one context per UI thread (`M3Gpu`).
- Stage-1 `paint` minimums: viewport 2.5 ms, custom 1.9 ms, m3 2.8 ms, gpu (shim) 3.7 ms.

## Table drawn plane: pixels inside a step and the ListView's look (leaf R6, 2026-10-19)

The GEOMETRY and DPI scripts of the Table differential answer some steps with the table's pixels (variant 5 seed 0
step 71: `pixels 17bf523e` in the official recording). Two changes:

- **Harness.** `ViewportDifferential.stepPixels` hands the capture of such a step to the harness. Every run saves it as
  `<name>-seed<k>-step<n>-<reference>.png`; a drawn mode reports its similarity to the stock capture of the same step
  (TSV column `<seed> step <n>`) and compares the step's probes under the recorded answer, as it already did for the
  pixels after each seed. A stock re-recording of variants 5, 6 and 9 equals the accepted recording line for line (20
  files) and gives 27 stock step PNGs.
- **Native look of the ListView** (the step 71 capture, [render-modes/evidence/table-step71](render-modes/evidence/table-step71)):
  - The ListView ends cell text that does not fit with "..." (`cell 2...`); the drawn plane clipped it. Rows of a Table
    carry `RowPaint.ELLIPSIS`; `RowCanvas.ELLIPSIS` in the text alignment cuts the text: `DT_END_ELLIPSIS` in the GC
    backend, and in the M3 backends the longest prefix that fits with "...", found by binary search over the shaped
    line's offsets and kept on the line for the last width.
  - The text box ends 4 pixels left of the first column's edge and 8 pixels left of the others: the column widths at
    which stock stops cutting (`ListViewPaddingProbe`, [output](render-modes/evidence/listview-padding.output.txt)).
  - Grid: the ListView draws its lines in `COLOR_3DFACE` (F0F0F0), the column line one pixel right of the column, and
    the theme part `LVP_COLUMNDETAIL` (EBF4FE on this host) on the column's last pixel (`ListViewPartProbe`,
    [output](render-modes/evidence/listview-parts.output.txt)). The Tree's own grid (DrawEdge, `COLOR_3DLIGHT` E3E3E3 on
    the cell's last pixel) is unchanged. `RowPaint.listGrid` and `listColumnDetail` carry the colours; only the native
    look draws them.

Variant 5 step captures, share of pixels equal to stock, before and after
([table-step-similarity-rm20261019e.tsv](render-modes/evidence/table-step-similarity-rm20261019e.tsv)):

| Mode | Step 71 before | Step 71 after | All 13 steps after |
|---|---|---|---|
| custom | 94.21 % | 99.97 % | 99.29 to 99.99 % |
| m3 | 85.60 % | 91.21 % | 87.52 to 99.95 % |
| gpu | 85.60 % | 91.21 % | 87.52 to 99.95 % |

The 10 pixels custom still differs by at step 71 are rows under the header: the ListView paints them, the drawn
plane starts below the header, and only WM_PRINT shows them (it prints the header 2 pixels in, by the border). m3 and
gpu shape and rasterize text with the library (subpixel anti-aliasing of their own), which is the rest.

## Look and feel consumption (LF6, user 2026-10-19: "pull in Swing-style themeability")

The drawn rows read the colours of a built-in look and feel from its defaults table (docs/m3/LOOK_AND_FEEL.md, the
all-widgets track's LF2 tables, to become `M3UIDefaults` lanes in LF6), not from constants of the drawn modes:

- `Themes.current (widget)` answers a built-in theme (`native`, `swing`/`metal`, `initiative31`/`i31`) as
  `ThemeDefaults.of (widget, look, LookAndFeel.named (name).defaults ())`: the built-in theme gives the look (how
  selection, expanders and check boxes are drawn) and answers only the keys a table leaves out; the colours come from
  `<Widget>.selectionBackground`, `<Widget>.selectionInactiveBackground`, `<Widget>.foreground`/`background`/
  `selectionForeground` (non-native looks), `Component.hoverBackground`, `Tree.lineColor`, `Table.gridColor`,
  `Tree.expandIconColor`, `Component.focusColor`. Resolved once per widget class and theme on a display thread, and
  dropped on every theme change. A user-defined theme (`Themes.register`) answers as built.
- [render-modes/LAF_KEYS.tsv](render-modes/LAF_KEYS.tsv) lists every visual input of a drawn row with its key and
  state: READ (from the table now), PROPOSED (a key the tables do not carry yet: check box colours and glyph sizes,
  cell margins, fonts; the look's value answers until then), NOT_APPLIED (row height and indent: in stage 1 the native
  control owns the geometry, so answers stay stock), PLATFORM (the native look's ListView grid and column detail).
- Run-time switch: `Themes.select` repaints the planes (listener and generation). The differential harness has a
  switch leg, `-Dswt.viewport.themeSwitch=<step>:<theme>`: the theme changes at that step of every seed, every
  non-pixel probe must still equal stock, and the captures after the switch are compared with a leg that started in
  the target theme.
- **Row delegate (since integration round 9 brought `M3LookAndFeel`, `M3UIDefaults` and `M3ComponentUI`):** every
  drawn plane installs `M3DrawnRowUI` with `M3LookAndFeel.installUI`. The delegate reads the colours above by key id from
  the look and feel's `M3UIDefaults` (`ThemeDefaults.of (widget, look, defaults)`; the developer layer of
  `M3UIDefaults.put` answers first) and declares them as its paint keys, so `M3LookAndFeel.setLookAndFeel` installs it
  again and repaints the widget only when a key it reads changes; the plane picks the new theme on its next frame.
  The look follows the look and feel's name: `native` the platform parts, `swing`/`metal` Metal, every other
  (`initiative31`, `light`, `dark`, registered and imported ones) the flat glyphs. A run-time `Themes.select` or a
  user-defined theme named by the properties still wins (`Themes.explicit`).

## Interaction model: CPU model, GPU renderer (design, user 2026-10-19)

"The CPU model and the GPU model must have an interactive perfect model." The drawn modes split into a model side
that changes and a renderer that only ever shows complete states. Status: design (documentation first, D2C); the
leaves and their gates are at the end of this section.

### Threads and roles

| Role | Thread | Owns | Never does |
|---|---|---|---|
| Widget API and input | UI thread | the widget's public API, the behaviour plane (stage 1: the native control), input dispatch | wait for a model worker |
| Model | UI thread for small changes; CPU workers for bulk work (10^6-row inserts and removes, sorting, prefix sums, lane fills) | the row model (viewport lanes, row index, heights), the theme ids | touch a device context, a GL context or a widget handle |
| Renderer | UI thread (custom, m3: the back buffer; gpu: the thread's OpenGL context, `M3Gpu`, which is per UI thread) | the frame: the snapshot it paints, the back buffer or render target, the caches | read the live model, block, allocate per frame |

### Snapshot protocol

A **snapshot** is what one frame needs and nothing else, immutable once published:

- `generation` (long, strictly increasing per widget) and the model generation it was built from;
- the row window: first row, row count, the pixel offset of the first row, the row heights (a prefix-sum lane);
- the window's lanes: text ids (interned), foreground, background and font ids, image ids, state bits (selected,
  focused, checked, grayed, expanded, children, depth);
- the theme id and theme generation (the look and feel of the widget: `M3DrawnRowUI`);
- the damage since the previous snapshot (rectangles in plane pixels), and the scroll position.

Publication is single-writer, many-reader, wait-free for the reader:

1. The writer fills a **slab** (preallocated lanes; three slabs per widget: one being written, the latest published,
   one being read). Strings are ids into the widget's interned text lane; no slab holds an object reference that
   the model can change.
2. The three slabs are a lock-free triple buffer (`RowSnapshots`, 2026-10-20): the writer owns the *back* slab, the
   renderer the *front* slab, the third is the *middle*, the latest complete snapshot. One `AtomicInteger` holds the
   middle slab and a *fresh* bit. Publishing exchanges back and middle (`getAndSet`, release) and sets the bit.
3. At the start of a frame the renderer exchanges front and middle only when the bit is set (acquire), then paints its
   front slab. Each side only touches the slab it owns, so a frame never sees a slab change under it. A first design
   (one published word plus a separate "being read" index) tore: the writer could take a slab the renderer had loaded
   but not yet marked (1012 torn frames in 200000 publications); the triple buffer has none.
4. If no new generation was published since the last frame and nothing was damaged, no frame is drawn.

### Input: model first, predicted pixels at once

Input goes to the model immediately on the UI thread (scroll position, selection, focus, type-ahead): the API answers
from the model at once, as stock does. The renderer does not wait for the next snapshot: it paints a **predicted**
frame from the latest snapshot and the input (a scroll shifts the resident window by the delta; the resident window
is larger than the visible rows, so a fast scroll stays inside it; a selection or focus change sets the state bits of
resident rows). When the snapshot built after the input is published, the next frame paints it: the prediction is
replaced within one frame. A prediction never shows a row the snapshot does not contain; rows outside the resident
window are painted empty until their snapshot lands.

### Invariants (checked by the interactive differential)

| | Invariant |
|---|---|
| I1 | Complete or nothing: every frame paints exactly one published snapshot (plus, between snapshots, a prediction from it). |
| I2 | Monotonic: the snapshot generation of frame n+1 is at least that of frame n (never an older state). |
| I3 | Never blocking: the renderer takes no lock the model holds; a frame costs O(visible rows). |
| I4 | No allocation per frame: slabs, caches and the frame log are preallocated (rule 18, DOOP). |
| I5 | Input first: an input event changes the model before the next frame; its pixels appear in the next frame (predicted) and the snapshot's pixels within one frame of its publication. |
| I6 | Budgets: input-to-pixel latency p99 within two frames; frame time p99 under 16.6 ms in the gpu mode. |
| I7 | Same pixels: once the model is quiet, the gpu frame equals the CPU frame of the same snapshot. gpu and m3 (CPU raster) share the M3 renderer and are compared pixel for pixel; custom draws text with GDI, so against custom the comparison is the similarity of this document's other measurements. |

### Frame pacing

Pattern donor: skiko (JetBrains, Apache-2.0) frame dispatching, as the donor PATTERN_MAP's frame-pacing theme will
record it: redraw requests coalesce into one pending frame; at most one frame in flight; frames align to the display
refresh (swap interval on the GL path, a refresh-rate timer otherwise); a frame that has nothing new is skipped. No
code is copied; the decision row goes into [render-modes/FOSS_REUSE_DECISION.tsv](render-modes/FOSS_REUSE_DECISION.tsv)
when the pattern is applied.

### Proof: the interactive differential

`Test_org_eclipse_swt_widgets_DrawnInteraction` (to be written): a Table with 10^6 rows in custom, m3 and gpu on the
invisible desktop; a scripted fast scroll (wheel bursts, page keys) and typing (type-ahead) while a model worker
inserts and removes rows in bursts. Each frame appends to a preallocated frame log: snapshot generation, prediction or
snapshot, input sequence number it reflects, frame start and end. Assertions: I2 over the whole log; input-to-pixel
p99 and frame time p99 under the budgets of I6 (gpu); after the script, with the model quiet, the gpu pixels equal the
m3 pixels and the custom pixels are reported by similarity; every non-pixel answer equals the viewport mode's.

### Leaves

| Leaf | Scope | Gate |
|---|---|---|
| IM1 | frame log and overlay: per frame generation, frame time and input-to-pixel latency, preallocated; `-Dswt.drawn.overlay=true` paints them in the corner | headless test of the log; the overlay in the examples |
| IM2 | snapshot slabs and publication (three slabs, a lock-free triple buffer), built on the UI thread from the viewport lanes: the protocol DONE (`RowSnapshots`, headless: 0 torn and 0 backward frames under concurrent reads, about 1.1-1.9 us per publish and acquire of 64 rows, no allocation); building the slabs in the planes is open | I1, I2, I4 under the existing differentials |
| IM3 | model worker for bulk changes; the UI thread applies the finished change and publishes | I3 under 10^6-row churn |
| IM4 | predicted frames for scroll, selection and focus | I5 |
| IM5 | frame pacing (skiko pattern) | I6 in gpu |
| IM6 | the interactive differential | I1-I7 |

## Thread architecture: CPU and GPU separation (brief rule 28, user 2026-10-20)

User 2026-10-20: "CPU vs GPU thread separation must exist", "the SWT synchronizer too", "just like Swing". Three roles,
never mixed (`ThreadRole`):

| Role | Owns | Never |
|---|---|---|
| UI | OS events, the native control (behaviour plane) and its resident window, ONE patch per widget per frame, building and publishing the frame's snapshot | waits on the render thread |
| MODEL | lanes, deltas, sort, filter, scope (viewport tracks) | touches a widget handle, a device context or a frame |
| RENDER | in custom, m3 and gpu: acquires the newest published snapshot, draws it into its own frame buffer (GDI DIB, Skia raster or GL target), hands the frame to the UI thread | touches the model, a widget or any SWT object; blocks on the model |

**The frame tick** (agreed with the all-widgets track, which rebuilds the Synchronizer as a Swing-EventQueue-like
dispatcher: a ring, keyed coalescing, priority classes `Synchronizer.INPUT`, `LAYOUT`, `DEFAULT` (stock FIFO), `MODEL`,
`IDLE`). Model threads post their completions as `MODEL` runnables keyed by widget (`asyncExec (priority, key,
runnable)`: the latest per key replaces the queued one). At most one tick is pending per Display, paced at 16 ms and
skipped when nothing is dirty; it drains the queued `MODEL` runnables (one window patch per widget), increments the
Display's frame generation (1, 2, 3, ...) and calls every `org.eclipse.swt.widgets.FrameListener`
(`frameApplied (Display, long frameGeneration)`, registered with `display.getSynchronizer ().addFrameListener`) once,
in one dispatcher step, so no `WM_PAINT` runs between the patches and the listeners. A plane that is dirty without model
work asks for a tick with `requestFrame ()`.

**Flow of a frame.** In its frame listener a dirty drawn plane fills its back slab (`RowSnapshots`) from the viewport
lanes: per visible row the `RowPaint.key` ints, the texts (immutable strings), image pixels as immutable copies and
fonts as descriptors, never an SWT object; it publishes (one `getAndSet`) and unparks the render thread. The render
thread exchanges in the newest slab (older ones are dropped: the newest generation wins), draws it and publishes the
finished frame through a second triple buffer. On `WM_PAINT` the UI thread runs the behaviour pass and blits the newest
finished frame over the damage; when that frame is older than the snapshot of the damage it is shown once more and the
render thread's completion invalidates the damage again (one frame later, invariant I5). The UI thread never waits on
the render thread; the render thread never blocks on the model (it reads published slabs only). A hidden widget
(rule 24) skips the patch and the publication; on becoming part-visible it posts its one patch as `MODEL`, keyed by the
widget, and the next tick publishes once.

**Captures.** `WM_PRINTCLIENT` (tests, printing) asks for the pixels of the current model now: it is served from the
newest finished frame when its generation is current, otherwise drawn once on the UI thread outside the frame path and
counted apart in the frame log.

**Gates.** `-Dswt.m3.threadCheck=true` turns on the role assertions (a constant false otherwise, so the checks compile
away): a plane is made and painted on the UI thread, the render thread never publishes, a model thread never acquires
or renders, an undeclared thread fails role-owned code. The frame log counts the UI thread's waits on the render thread
and the render thread's blocks on the model (both must stay 0) and the frame time p99 under model churn of 10^4
deltas/s at 10^6 rows (the interactive differential with a model thread).

| Leaf | Scope | Status |
|---|---|---|
| R28-1 | `ThreadRole` and the assertion mode at the plane, snapshot and renderer boundaries; this design | DONE (headless: `Test_..._ThreadRole` with and without the mode) |
| R28-2 | the row slab: `RowPaint` rows into snapshot lanes (key ints, texts, image copies, font descriptors) and back | DONE headless (`FrameSlab`, `TripleBuffer`, `FontDescriptor`, `RowPaint.write/read`: identical renderer calls after the round trip, 0 torn frames in 50000 publications under concurrent reads, 0 bytes per frame); the planes fill it with R28-3 |
| R28-3 | the render thread per Display, the frame triple buffer, the completion that invalidates the damage | |
| R28-4 | the frame listener of the planes on the Synchronizer (API above, all-widgets track implements it) | DONE (render-thread mode: at a tick a damaged visible plane paints at once, so a patch is published in the same tick; the Synchronizer delta of all-widgets 9c52f39e315 is on this branch) |
| R28-5 | the gates under churn at 10^6 rows in custom, m3 and gpu | |

## SVG-skinned controls (user 2026-10-20: "SVG-painted button to my size, instead of painting it manually")

docs/m3/behaviours/BEHAVIOURS.md, "SVG-skinned controls": buttons, toolbar items, badges, chips, toggles, check and radio
marks, sash grips and scroll carets take their visuals from SVG skins, with no paint code per control. This track owns
the skin engine (`SvgSkins`); the all-widgets track owns the controls and the L&amp;F keys, agreed 2026-10-20:

| Key | Kind | Meaning |
|---|---|---|
| `<Component>.skin` | STRING (all-widgets adds the kind, interned to an int id) | the skin: a resource name such as `m3/button`; unset: the control paints as today, so native mode stays pixel-identical |
| `<Component>.skin.insets` | INSETS | nine-slice insets in SVG units (top, left, bottom, right); unset: the skin's `data-insets` |
| `<Component>.skin.<part>.<state>.tint`, `<Component>.skin.<part>.tint` | COLOR | the colour that replaces `currentColor`; falls back to `<Component>.foreground` |

Parts: background, border, mark, grip, caret (SVG resources `skins/<skin>/<part>[.<state>].svg`). States: normal, hover,
pressed, focused, disabled, selected, mixed (a missing state uses the normal part). A skinned delegate's paint keys
include its skin keys, so a look and feel switch repaints only the skinned widgets whose keys changed.

How a part is drawn: rasterized once per (skin, part, state, tint, zoom) at its own size times the zoom through SWT's
`SVGRasterizer` seam (org.eclipse.swt.svg, JSVG), then composed to the control's size by nine-slice scaling: corners
copied 1:1 (they keep their radius at every size), edges stretched along one axis, the centre along both. A part
without insets (a mark, a grip, a caret) is rasterized at the exact size instead, crisp. Both levels are cached
(`LongCache`); a warm lookup allocates nothing. Built-in skins: `m3/button` (background, border, focused border),
`m3/check` (border, mark, mixed mark), `m3/radio` (border, mark), `m3/sash` (grip), `m3/scroll` (caret).

Donors (ADAPT, no code copied): SWT SVGRasterizer and JSVG (MIT) for rasterizing, FlatLaf FlatSVGIcon colour filters
(Apache-2.0) for the tint by colour replacement, IntelliJ's SVG icon loader (Apache-2.0) for caching by size and scale,
the CSS nine-slice (`border-image`) pattern. Gate (`Test_org_eclipse_swt_internal_render_SvgSkins`, headless): the button
at 100/125/150/200 % and 10 sizes keeps its corners equal to the part at its own size, its edges uniform and its fill the
tint; one rasterization per zoom whatever the sizes; marks at their exact size; a missing state falls back; 100000 warm
lookups allocate 688 bytes in total (not per lookup). The Swing side (M3JDK) reads the same skins.

## Optimization pass and brief rule 24 (2026-10-20)

User 2026-10-20: "proceed with optimizing the whole" (C:/m3-superset/tools/OPTIMIZATION_PASS.md) and "all delta based
view" (brief rule 24, with the sub-rules "viewport deltas (resize, layout)" and "hidden viewports"). Every leaf has a
row with its before and after numbers in [render-modes/OPTIMIZATION_LEDGER.tsv](render-modes/OPTIMIZATION_LEDGER.tsv).

### Frame allocations

A paint frame's bytes are attributed with JFR (`render-modes/bench/alloc_profile.sh`: TLAB events whose stack passes
through `DrawnPlane.draw`). The drawn path's own allocations are gone:

- `LongCache`: a boxing-free cache keyed by a native handle or a packed key (open addressing with backward
  re-insertion, clock eviction, a callback that closes what it drops). `GlyphCache` keeps its glyphs in one;
  `M3RowCanvas` keys fonts and part images in them, and a font entry carries its shaped lines.
- `M3RowCanvas` clips and draws images, glyphs, parts and the focus rectangle through Skija's primitive entry points
  (`_nDrawImageRect` with the default sampling and strict bounds, which is what `drawImage`/`drawImageRect` pass), and
  keeps one `Path` and the gpu readback's `ImageInfo`.
- `ListDrawnPlane` reuses the string it kept for an index while the list box's characters are the same.

Every backend now allocates per frame what custom does (Tree 35008 bytes, List 3504 at 10^6 rows); the rest is
shared SWT and viewport code (`Display.getControl` boxing, `Tree.wmNotifyChild` structs per notification, the Tree
viewport's boxed lookups), outside this track.

### Theme and look and feel switches

- One glyph cache per UI thread serves every renderer, keyed by (glyph, size, zoom, tint): a switch back draws glyphs
  already rasterized. The first row after a switch went from 1.1-1.6 ms to 6-9 us at 150 %.
- A `Themes` selection repaints only the planes whose theme it changes (before: every drawn plane repainted).
- Look and feel switches reinstall and repaint only the delegates whose paint keys changed (`M3LookAndFeel.update`,
  all-widgets track).

### Delta based view (rule 24)

The native controls invalidate more than a delta changes: a list box insert or remove repaints every row below it
(also for rows outside the window), a tree delete below the window repaints the whole client, a resize repaints the
whole client. The drawn plane therefore keeps a **retained frame** (the pixels it last presented) and, per row slot,
what it drew there: the row's key (`RowPaint.key`: every field the renderer reads, compared exactly, texts with
`equals`) and the theme. A row whose key and theme equal its slot's is not drawn: its band is copied back from the
retained frame over the behaviour pass. A row with a cell image is always drawn (its pixels may change in place), and
so is every row of a theme with its own row or cell painter. A slot is remembered only for a row drawn whole under a
damage as wide as the client; a platform theme, system colour, setting or font change forgets all slots.

`FrameLog` counts per frame the damage, the rows drawn and kept, and a **full repaint** (a frame that drew every row
of at least 90 % of the rows area); the gates read it before and after each delta:

| Gate | Test, tool | Pass |
|---|---|---|
| model deltas | `Test_org_eclipse_swt_widgets_DrawnDeltaRepaint`, `tools/delta_repaint_legs.sh` | no full repaint per delta (text, colour, check of a visible row; inserts, removes and count changes outside the window), and the kept rows equal the rows drawn from scratch |
| viewport deltas (resize) | `Test_org_eclipse_swt_widgets_DrawnResizeSweep`, `tools/resize_sweep_legs.sh` | 50 sizes: no full repaint after the first step; back buffer re-creations and frame p50/p99 reported |
| hidden viewports | `Test_org_eclipse_swt_widgets_DrawnHiddenViewport`, `tools/hidden_viewport_legs.sh` | hide, 10^4 deltas, show: no paint and no native message while hidden, one paint on show, kept rows equal drawn rows |

The back buffer grows in **size classes** of 128 pixels per axis and never shrinks (`BackBuffer.ensureFrame`): a resize
drag re-creates it, and the m3 or gpu surface over it, once per class instead of once per step (50-step sweep: 24
re-creations before, 3-4 after). Glyph and shaped-line caches belong to the backend, not to the size, so a resize keeps
them.

While a drawn widget is hidden, Windows sends no paint, so the plane draws nothing and publishes nothing. Native work
while hidden is the widget viewport's (Tree, Table, List tracks): the gate counts every message that reaches the native
window procedure (`FrameLog.messages`) so their hidden-period deferral is measured here too.

### Measurement notes

- Input-to-pixel latency is measured per input event in `Test_org_eclipse_swt_widgets_Table_DrawnInteraction` (send
  to the end of the paint `update ()` makes; inputs that damaged nothing counted apart). The frame log's own figure
  charges an input that damaged nothing to the next frame, whatever made it (186-197 ms p99 at stamp m was mostly
  that).
- gpu and m3 share the M3 renderer: the glyph column matches within one level per channel (blending rounding); text
  differs by up to five levels (the GPU's text anti-aliasing), so I7 holds for glyphs, not for text.
- Glyphs follow the window's DPI (`Widget.nativeZoom`), the density of the native rows: a forced `swt.autoScale`
  above the monitor's does not enlarge them, as it does not the native expanders and check boxes.
- gpu timings on this machine vary with the other UI legs that run in the other two desktop slots; allocation counts
  and frame or row counts are the stable measures.

## Vector glyphs: SVG at the exact DPI (design, user 2026-10-19)

"SWT SVG is there, Batik is there": theme glyphs (expanders, check boxes, sort indicators, chevrons) and vector icons
of the drawn modes come from SVG, rasterized at the widget's exact zoom.

- **Rasterizer (donor rows in [render-modes/FOSS_REUSE_DECISION.tsv](render-modes/FOSS_REUSE_DECISION.tsv)).** SWT's
  own: `org.eclipse.swt.internal.image.SVGRasterizer` (`rasterizeSVG (stream, zoom)` and `(stream, width, height)`),
  found through the ServiceLoader, implemented by the `org.eclipse.swt.svg` fragment with JSVG (MIT). Apache Batik
  (Apache-2.0) is the fallback for a feature JSVG lacks (filters, SVG text); the built-in glyph set needs none.
- **Cache in lanes, never per frame.** A glyph key packs (icon id, size in pixels, zoom, theme generation) into one
  long; an open-addressing table maps it to a slot of preallocated lanes holding the `ImageData`. A miss rasterizes
  once (on the UI thread, or on a model worker as part of a snapshot in the interaction model); a frame only looks the
  slot up. The backends already keep one device copy per `ImageData` (`GcRowCanvas`, `M3RowCanvas` glyph caches), so a
  hit costs a table probe. A theme switch or a zoom change moves to new keys; old slots are reused LRU.
- **Themes.** A theme names its glyphs by SVG resource (`Tree.icon.expanded`, `Tree.icon.collapsed`,
  `CheckBox.icon.checked`, `CheckBox.icon.unchecked`, `CheckBox.icon.mixed`, `Table.sortIcon.ascending`,
  `Table.sortIcon.descending`), keys of the look and feel tables (the all-widgets track owns the tables; FlatLaf's
  icon key names). `Theme.expanderOpen` and the other glyph images become per-zoom lookups in the cache.
- **Proof.** A differential leg runs at 100, 150 and 200 % (`-Dswt.autoScale`): the glyph pixels are crisp (no
  scaled bitmap: the rasterized size equals the slot size at that zoom) and equal across custom, m3 and gpu (all three
  blit the same `ImageData` 1:1); every non-pixel probe stays equal to stock.
- **Theme tint.** The glyph sources use `currentColor`; before rasterizing, the cache resolves it to the theme's colour
  (expander: `Tree.expandIconColor`, check box: the border and `CheckBox.icon.checkmarkColor`, sort indicator: the
  header foreground), so one SVG serves every look and feel (the FlatSVGIcon colour-filter pattern, FlatLaf #303 and
  #817; SWT's rasterizer takes the resolved SVG, as platform.ui #4497/#4505 set `currentColor`). The built-in set is
  in `org/eclipse/swt/internal/render/glyphs` (chevron right and down, check box off/on/mixed, sort ascending and
  descending; 16 x 16 view boxes, 1.5 px strokes).
- **Differential cases absorbed from upstream (leaf SV3):** swt #3659 (the unfocused tree selection fill of the dark
  theme on win32: the inactive selection colour comes from `Tree.selectionInactiveBackground` of the dark table, not
  from the platform), swt #3631 (crisp HiDPI images: the glyph is rasterized at the slot's pixel size, never scaled
  from 100 %), FlatLaf #1022 (fractional scale artifacts: at 125 and 150 % the slot and the glyph round the same way,
  `(size * zoom + 50) / 100`, so no seam between a selection fill and the glyph).

| Leaf | Scope | Gate |
|---|---|---|
| SV1 | glyph cache (packed keys, lanes, clock eviction), SWT rasterizer through the ServiceLoader: DONE (`GlyphCache`, 4/4 headless) | headless test: one rasterization per key, none per frame |
| SV2 | built-in SVG glyph set for the flat looks (DONE: `Glyphs`, 7 glyphs with `currentColor`, tinted per theme; `Test_org_eclipse_swt_internal_render_Glyphs` 2/2 with the SVG fragment and JSVG 2.1.0 on the class path); theme keys with the all-widgets track | the theme tests |
| SV3 | the 100/125/150/200 % leg: headless part DONE; the drawn path DONE (non-native looks draw the SVG set at the zoom, `GcRowCanvas` blits glyphs 1:1 with AlphaBlend); `Test_org_eclipse_swt_widgets_Tree_DrawnGlyphZoom` + `tools/glyph_zoom_legs.sh` with the upstream cases (swt #3659, #3631, FlatLaf #1022): results pending (queued on the UI lock) | crisp and equal across the three drawn modes |
| SV4 | the example (below, with the interaction model's overlay) | runs on the invisible desktop |

## Examples (user 2026-10-19: "enrich everything with examples")

**A drawn Table in the gpu mode with the overlay** ([M3DrawnModesDemo](../../examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/M3DrawnModesDemo.java):
a million virtual rows, three columns, look and feel buttons):

```
java -Dswt.widget.mode=gpu -Dswt.drawn.overlay=true ^
     -cp swt.jar;swt-render-m3.jar;skija-shared-0.116.3.jar;skija-windows-x64-0.116.3.jar;types-0.1.1.jar ^
     org.eclipse.swt.snippets.M3DrawnModesDemo
```

The overlay (bottom right) shows the frames drawn, the frame time p99 and the input-to-pixel latency p99 of the plane's
frame log (interaction model, IM1). `-Dswt.widget.mode=custom` draws with GDI, `m3` with the M3 renderer on the CPU.
Nothing in the application changes between the modes:

```java
Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
table.setHeaderVisible (true);
table.setLinesVisible (true);
table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("row " + event.index));
table.setItemCount (1_000_000);
```

**Switching the look and feel at run time** (LF6; the rows' `M3DrawnRowUI` delegate is installed again and the widget
repaints only when a key it reads changes):

```java
M3LookAndFeel.setLookAndFeel ("flatdark");   // native, swing, initiative31, flat, flatdark, or a registered one
M3LookAndFeel.getDefaults ().put (M3UIKeys.of ("Table", "selectionBackground", M3UIKeys.COLOR), 0xFF2F6FD0); // developer layer
```

**One widget class pinned** at startup: `-Dswt.widget.theme.Tree=swing` keeps Trees in Swing's Metal look while the
rest follows `setLookAndFeel`.

**A theme of one's own** (painters and glyph images; a run-time selection wins over the look and feel):

```java
Theme ocean = Theme.builder ("ocean").look (Theme.LOOK_SWING).selection (0x2F6FD0, 0xC5D5EA).lines (0x7A8A99, 0xD0D8E0).build ();
Themes.register (ocean);
Themes.select ("Table", ocean);
```

**Vector glyphs at a zoom** (the non-native looks; the SVG fragment and JSVG on the class path):

```
java -Dswt.autoScale=200 -Dswt.widget.theme=flatdark -Dswt.widget.mode=m3 -Dswt.drawn.overlay=true ^
     -cp swt.jar;swt-render-m3.jar;skija-shared-0.116.3.jar;skija-windows-x64-0.116.3.jar;types-0.1.1.jar;org.eclipse.swt.svg.jar;jsvg-2.1.0.jar ^
     org.eclipse.swt.snippets.M3DrawnModesDemo
```

The expanders and check boxes are rasterized once at 32 pixels (16 points at 200 %) and tinted with `Tree.expandIconColor`
and `CheckBox.icon.checkmarkColor` of the look and feel; a zoom change rasterizes them again at the new size, never scales.

**Measuring a drawn plane from a test** (the frame log is per plane; `FrameLog.monotonic` is invariant I2):

```java
FrameLog frames = ...;                  // the plane's log (package-private field DrawnPlane.frames)
long frameP99 = frames.frameTime (99);    // nanoseconds
long inputP99 = frames.inputLatency (99); // nanoseconds from input to the frame that shows it
assertTrue (frames.monotonic ());
```

## Leaves

| Leaf | Scope | Gate |
|---|---|---|
| R1 | this design and the donor inventory | review |
| R2 | mode benchmark, viewport and original first | TSV under `render-modes/bench` |
| R3 | drawing core + `GcRowCanvas` + Tree drawn plane (stage 1) | Tree differentials (both variants) in `custom`: all non-pixel probes equal to stock |
| R4 | `M3RowCanvas` raster | the same in `m3` |
| R5 | gpu backend: Java only, `M3RowCanvas` over OpenGL (`M3Gpu`) | the same in `gpu` |
| R6 | Table, then List, on the same core (`DrawnPlane`, `TableDrawnPlane`, `ListDrawnPlane`) | Table and List differentials in the three drawn modes |
| R7.. | optimization passes driven by the benchmark (before/after numbers each); data-oriented paint (rule 18) | benchmark + differentials |
| T1 | theme API: Theme/Themes, painters, glyph images, live switching; headless tests | theme differential (custom mode per theme) + the headless renderer tests |
| C1 | JUnit coverage of the render code, at least 99 % lines and branches (rule 15) | JaCoCo report under `render-modes/coverage` |
| S2.x | stage 2: behaviour duties move from the native control to the model | the same differentials |

## Donors

[render-modes/FOSS_REUSE_DECISION.tsv](render-modes/FOSS_REUSE_DECISION.tsv). In short: the viewport model and
planner, `WidgetMode` and the retained-planes `OpStream` are reused from this repository; Windows theme parts
through SWT's existing natives; the Skija library (Apache-2.0, with its own natives) under the M3 renderer for the
m3 and gpu planes; skija-canvas's text LRU and GL surface are adapted; Initiative 31's custom widget classes are not used (a second
model per widget), its owner-draw order and paint cull are patterns; the SWT-on-Swing projects (SWTSwing,
Initiative 31 prototype-swing) are patterns for the row renderer, the Swing theme keys and later the input mapping;
SwingWT, DJ Native Swing, SWT_AWT and Albireo embed rather than draw (no fit).

## Status

| Leaf | State |
|---|---|
| R1 | this document |
| R2 | benchmark program and driver; viewport, original and official measured (table above) |
| R3 | drawing core, GC backend, Tree drawn plane (stage 1): Tree differentials pass in `custom` |
| R4 | M3 raster backend: Tree differentials pass in `m3` (then named `skija`) |
| R5 | gpu: first a Direct2D JNI shim (passed the Tree differentials), removed under rule 13; now M3 over OpenGL, Java only; `DrawnPlane` shared by the row widgets |
| R6 | Table and List drawn planes; List matches its viewport control in custom, m3 and gpu; Table variants 5-9 pass in custom, m3 and gpu (pixels inside a step are similarity rows); ListView ellipsis, text box and grid in the native look |
| T1 | theme API (`Theme`, `Themes`), Tree differential passes under `swing` and `initiative31` in `custom`; built-in themes read their look and feel tables (`ThemeDefaults`, LAF_KEYS.tsv); run-time switch leg |
| C1 | headless tests of the render core and backends; coverage report under `render-modes/coverage` (UI exec merge in progress) |
| Seam | [render-modes/ROW_PAINTER_SEAM.md](render-modes/ROW_PAINTER_SEAM.md): the row painter shared with the viewport-everywhere track |
| R7 | not started |

Non-claims: the owner-draw events (MeasureItem, EraseItem, PaintItem) are still emitted by the behaviour plane's pass, so an application's owner drawing is painted over in the drawn modes (stage 2 moves them to the renderer); rows answered by a page source without a facade draw no text yet; GTK and Cocoa compile (`compile_platform.sh`: 0 errors) but are not run on this host.
