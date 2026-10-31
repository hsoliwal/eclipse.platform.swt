<!-- SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# GC extension seam (Initiative 31 adoption, leaf 1)

User directive (2026-10-30, repeated): "swt-initiative31 / JetBrains/skija ... and we are still struggling."
The drawn modes (`m3`, `gpu`) paint rows through `M3RowCanvas implements BufferCanvas`; client `PaintListener`s
never receive a `GC` in those modes because `GC` was `final`. Initiative 31 opened that seam on 2026-10-20
(swt-initiative31/skija-canvas `skia_canvas`, commits a5af63895d .. 9bcb404c42 by Denis Ungemach, EPL-2.0):

- `public sealed class GC extends Resource permits GCExtension` (win32, gtk, cocoa) and
  `public sealed class FontMetrics permits FontMetricsExtension`.
- `GCExtension(IExternalGC)` and `FontMetricsExtension(IExternalFontMetrics)` forward every call to a backend.
- `org.eclipse.swt.internal.canvasext`: `IExternalGC`, `IExternalFontMetrics`, `IExternalCanvasFactory`,
  `IExternalCanvasHandler`, `ExternalCanvasHandler` (style bit `SWT.SKIA`, `ServiceLoader`, properties
  `org.eclipse.swt.external.canvas:disabled|forceEnabled|logActivation`, permanent fallback to the native path),
  `IDpiScaler` + per-platform `DpiScaler`, `FontProperties`.
- `PatternProperties`, `RegionLog` (the SWT `Region` ops replayed by a backend), `Pattern`/`Region`/`TextLayout`
  accessors, `Canvas` hooks (win32, gtk), `GLCanvasExtension`/`GLPaintEventInvoker` (win32, gtk, cocoa).

## What this leaf does

Adopts exactly those core-bundle files (ADAPT, EPL-2.0, headers retained; `DONOR_PINS.tsv` lists every donor commit,
path and blob sha). It adds no backend: without a registered `IExternalCanvasFactory` every path is the stock path
(`ExternalCanvasHandler.isActive` is false), so observable behaviour is unchanged. The M3 backend
(`IExternalGC` over the M3 renderer, adapted from I31 `SkiaGC` with the BGRA direct path of `M3RowCanvas`) and the
headless GC-level differential against call-logging doubles (I31 `SkCanvasDouble`) are the next leaves; the drawn
planes then hand `new GCExtension(m3gc)` to `PaintListener`s.

## Method (CPU only)

`i31_patches.py` turned the six I31 commits into unified diffs (core bundle only, the `org.eclipse.swt.skia` bundle
excluded); `git apply --reject` applied 44 hunks; the 8 rejects were: a blank-line hunk (win32 `GC.init`, dropped),
the upstream `CLabel` drift (dropped, not part of the seam), and the win32 `Region` log calls, the win32
`TextLayout.drawInPixels` delegation and the two gtk `Canvas` hooks, re-applied by exact anchors with
`seam_rejects.py` (both scripts in `tools/`). Gate: `C:/m3-superset/tools/compile_platform.sh <wt> win32|gtk|cocoa`,
0 errors each (see the PR body for the counts). API-tools filters for the sealed classes (I31 commit 1b959eac89)
are a Tycho concern and are not part of the javac gate.

Catalogue rows: I31-GC-SEALED-SEAM, I31-SKIAGC, I31-HEADLESS-GC-DOUBLES, I31-EXTERNAL-CANVAS-FACTORY,
I31-SKIJA-CANVAS-20261021 (K:/m3-work/donors/catalogue/DONOR_CATALOGUE.tsv); FOSS rows in com.synexia
docs/M3-SCALE/runs/20261017-ui-donor-atomize/FOSS_REUSE_DECISION.tsv (wave 4, PR #14191).

## Leaf 2: the M3 backend behind the seam (`m3/gc-m3-backend-20261030`, unregistered)

Adopts Initiative 31's backend (`swt-initiative31/skija-canvas` `skia_canvas` head `9bcb404c421361b7f5e046dc844ec076e539d0e6`,
EPL-2.0, bundle `org.eclipse.swt.skia` and its test bundle) as the M3 backend of the GC seam. `M3ExternalGC implements
IExternalGC` (I31 `SkiaGC`) draws SWT `GC` calls onto a Skija surface through `IM3Surface`/`IM3Canvas`; the new
`M3PlaneCanvasExtension implements IM3CanvasExtension` hands it the surface a drawn plane already owns. Nothing is
registered (no `META-INF/services` file; `M3CanvasFactory.createCanvasExtension` answers `null`): every shipped path is
unchanged. Leaf 3 constructs the extension per plane (`DrawnPlane`/`RenderTarget`, the surface of `M3RowCanvas.target`)
and hands `new GCExtension (new M3ExternalGC (extension, SWT.NONE))` to the `PaintListener`s.

### Placement

| Tree | Package | Files |
|------|---------|-------|
| `bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/` | `org.eclipse.swt.internal.render.m3.gc` | 26 adopted + `M3PlaneCanvasExtension` (new) |
| `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/` | `org.eclipse.swt.tests.junit.m3gc` | 27 adopted (9 doubles, 12 draw tests, 4 converter tests, suite, platform check) |

The donor's four packages (`internal.graphics`, `internal.skia`, `internal.skia.cache`, `internal.canvasext`) collapse
into the one M3 package; the leaf-1 seam types stay in `org.eclipse.swt.internal.canvasext` (wildcard import where the
donor relied on being in that package). `FOSS_REUSE_DECISION.tsv` has one row per donor blob (ADAPT, or PATTERN_ONLY for
`SkiaGlCanvasExtension`); `adapted_hashes` is the git blob sha of the adopted file.

### Method (CPU only, scripts in `tools/`)

1. `m3gc_adopt.py <manifest> <donor> <worktree> <log>`: per donor blob, GNU `sed -E -f m3-naming.sed` (the M3 naming
   rule set: `Skija` tokens become `M3`, licence lines and `io.github.humbleui` imports stay), the class renames below,
   the `Skia`/`skia` identifier rule (inside identifiers only; prose such as "Skia draws ..." is untouched), package and
   import rewrite, one contributor line in each I31 `Contributors:` block (10 files carry one; the other 44 donor files
   have no header). Byte preserving (LF kept, written in binary).
2. `m3gc_api_deltas.py <worktree>`: the fifteen exact-text deltas that make the adopted sources compile in the fork
   (D01-D04 seam, D05-D15 Skija 0.116.3), each recorded in `RECIPE_MEMORY.tsv` with the javac line that demanded it.
3. `compile_m3gc.sh`, `test_m3gc.sh`: the gates. `m3gc_baseline_overlay.py`: repaired scratch copies of the base
   branch's pre-existing errors (see below), never written into the worktree. `m3gc_foss_ledger.py`: the ledger.

### Renames (brief rule 14: nothing of ours is named Skija or Skia)

| Donor | Adopted |
|-------|---------|
| `SkiaGC` | `M3ExternalGC` |
| `SkiaPaintManager` / `SkiaTextDrawing` / `SkiaPathConverter` | `M3PaintManager` / `M3TextDrawing` / `M3PathConverter` |
| `SkiaRegionCalculator` / `SkiaTransformConverter` / `SkiaColorConverter` | `M3RegionCalculator` / `M3TransformConverter` / `M3ColorConverter` |
| `SkiaFontMetrics` | `M3ExternalFontMetrics` |
| `SkijaToSwtImageConverter` / `SwtToSkiaImageConverter` | `M3ToSwtImageConverter` / `SwtToM3ImageConverter` |
| `ISkCanvas` / `ISkImage` / `ISkSurface` | `IM3Canvas` / `IM3Image` / `IM3Surface` |
| `ISkiaCanvasExtension` / `ISkiaResources` / `SkiaResources` | `IM3CanvasExtension` / `IM3Resources` / `M3Resources` |
| `SkijaCanvasAdapter` / `SkijaImageAdapter` / `SkijaSurfaceAdapter` | `M3CanvasAdapter` / `M3ImageAdapter` / `M3SurfaceAdapter` |
| `SkiaCanvasFactory` | `M3CanvasFactory` (answers `null`, unregistered) |
| `SkiaGlCanvasExtension` | not copied (PATTERN_ONLY); the pattern of `M3PlaneCanvasExtension` |
| `SkCanvasDouble` / `SkSurfaceDouble` / `ISkImageDouble` / `SkiaResourcesDouble` | `M3CanvasDouble` / `M3SurfaceDouble` / `IM3ImageDouble` / `M3ResourcesDouble` |
| `Test_org_eclipse_swt_skia_<name>` / `AllSkiaTests` | `Test_org_eclipse_swt_m3gc_<name>` / `AllM3GcTests` |

Identifiers followed the same rule (`getSkiaFont` -> `getM3Font`, `convertSWTColorToSkijaColor` -> `convertSWTColorToM3Color`,
`skiaExtension` -> `m3Extension`, the system property `...skia.SkiaResources.IGNORE_TEXT_IMAGE_CACHE` ->
`...render.m3.gc.M3Resources.IGNORE_TEXT_IMAGE_CACHE`). Unchanged names: `Logger`, `RGBAEncoder`, `RectangleConverter`,
`DpiScalerUtil`, `ImageTextKey`, `SplitsTextCache`, the doubles `CanvasExtensionDouble`, `ExtractedFontData`,
`MethodCall`, `PaintData`, `TextSplitUtil`, `SupportedTestPlatforms`. Residual: 23 `Skija` and 19 `Skia` mentions in
prose comments plus the `io.github.humbleui.skija` imports (the rule set keeps both).

### Seam into the M3 renderer

I31's `SkiaGlCanvasExtension` owns a GL context (`DirectContext.makeGL`), a `BackendRenderTarget` and a `Surface` per
`Canvas`, and restores the pixels outside the damage from a snapshot image of the last frame. Our planes already own a
surface (`M3RowCanvas.target (BackBuffer)`: `Surface.makeRasterDirect` over the DIB section, or
`Surface.makeRenderTarget` on `M3Gpu`) and the `BackBuffer` retains the previous frame, so `M3PlaneCanvasExtension`
owns nothing: `getSurface` wraps the plane's current surface (`Supplier<Surface>`, re-read per call because `target`
replaces it when the buffer grows), `createSupportSurface` is `surface.makeSurface` (raster or GPU, like the plane),
`frame (x, y, w, h)` is the damage and therefore the GC's clip (`getClientArea`/`getBounds`), `redraw` invalidates the
control so the plane's own paint presents from the back buffer. `M3Resources` takes a `Control` (D01-D03): the plane's
widget is a `Table`/`Tree`, not a `Canvas`; only `Control` methods are used.

### API deltas (I31 targets Skija 0.143.16, the fork binds 0.116.3; `RECIPE_MEMORY.tsv`)

| Delta | 0.143.16 | 0.116.3 | Files |
|-------|----------|---------|-------|
| D05, D06 | `io.github.humbleui.skija.RegionOp` | nested enum `Region.Op` (same constants) | `M3RegionCalculator` |
| D07-D15 | `PathBuilder` (`moveTo`.. `build()`/`detach()`, `addPolygon`) | `Path` is its own builder (`moveTo`.. return `Path`, `addPoly(float[], boolean)`); one close, not two | `M3ExternalGC`, `M3PathConverter`, `M3RegionCalculator` |

Nothing else differed: the other 24 adopted sources compiled unchanged against 0.116.3.

### Gates (2026-10-30)

- `compile_m3gc.sh C:/m3-superset/swt-gcseam H:/m3-scratch/gc-backend/main` (the win32 `build.properties` dirs plus
  `Eclipse SWT Render M3/win32` and `Eclipse SWT GPU M3/win32`, Skija 0.116.3 jars, `-Xmx1536m`, memory gate >= 1.5 GB):
  **918 sources, 0 errors** with `M3_OVERLAY` (P01, P02 below); without the overlay the same 918 sources give exactly
  the 3 pre-existing errors (`Region.java:584`, `Replay.java:174` x2) and none in the 27 adopted files.
- `C:/m3-superset/tools/compile_platform.sh <wt> win32|gtk|cocoa` (raw, no overlay): win32 886 sources / 1 error (P01),
  gtk 675 / 0, cocoa 816 / 1 error (P03). The errors are in files identical to the base branch
  (`git diff m3/gc-extension-seam-20261030 -- <file>` is empty); this leaf adds none. Pass `C:/...` as the worktree:
  with an MSYS `/c/...` path javac fails before compiling and `compile_platform.sh` reports `errors=0`.
- `test_m3gc.sh` (headless, no `Display`; JUnit 5 through `junit-platform-console-standalone-1.11.4`; suite order
  `AllM3GcTests` as I31 runs it): tests compile 27 sources / 0 errors; **59 tests found, 59 passed, 0 failed, 0 skipped**
  (19 containers). With `M3_SCAN=1` (classpath scan, another class order): 59 found, 53 passed, 6 failed; the six are
  `Test_org_eclipse_swt_m3gc_drawText`/`drawString` x `{default, Zoom150, Zoom200}` with
  `Expected 4 method calls ==> expected: <4> but was: <2>`: P04, the donor's `drawText_NoCache` sets
  `M3TextDrawing.NO_TEXT_CACHE` and never clears it (identical in I31 head), so the class order decides. PRE_EXISTING,
  not fixed here (proposal in `PRE_EXISTING.tsv`).

### PRE_EXISTING (`PRE_EXISTING.tsv`; proposals only, the owner files are untouched)

| Id | Where | Error | Proposed owner fix |
|----|-------|-------|--------------------|
| P01 | win32 `graphics/Region.java:584` (leaf 1) | `log.intersect(region)` in `set(ZoomToRegionMap, int)`: no `region` there; `intersect(Region)` already logs (line 436) | delete the duplicate line |
| P02 | `Eclipse SWT GPU M3/.../Replay.java:174` | `PathBuilder` is not in Skija 0.116.3 | `Path` as its own builder (as D07/D08) |
| P03 | cocoa: `common/graphics/RegionLog.java:45` `r.getLog()` (leaf 1) | cocoa `Region` has no `RegionLog` field and no `getLog()` | add them as gtk and win32 have |
| P04 | `Test_org_eclipse_swt_m3gc_drawText_NoCache.java:29` (donor test) | system property leak across test classes | `@AfterAll System.clearProperty(...)` |

### NOT_RUN

- The drawn planes still paint through `M3RowCanvas`; no `GCExtension` reaches a listener yet (leaf 3).
- gtk and cocoa: the M3 render tree is win32 only; the adopted sources are platform neutral but were compiled on win32 only.
- The per-draw `ImageData` <-> Skija image conversion (`M3ToSwtImageConverter`, `SwtToM3ImageConverter`) stays as
  adopted; replacing it with `M3RowCanvas`'s BGRA direct path is a later leaf (the ledger rows say so).
- `SWT.SKIA`-style `Canvas` activation through `ExternalCanvasHandler`: `M3CanvasFactory` is not registered and answers `null`.
### Leaf 1 correction (same day): the first gate was a false zero

`compile_platform.sh` was first called with an MSYS path (`/c/m3-superset/swt-gcseam`); javac failed before compiling and
the script counts only `: error:` lines, so it printed 0 errors on all three platforms. Re-run with `C:/...` (always pass
Windows paths to the gates) the seam had: win32 1 error (`Region.java:584`, a `log.intersect(region)` that
`seam_rejects.py` had leaked into `set(ZoomToRegionMap, int)` when the `intersect(Region)` anchor did not match the
braced body), cocoa 1 error (`RegionLog.java:45`: the cocoa `Region.getLog()` hunk was among the rejects that were deleted
unapplied), gtk 0 by luck (its `Region` had the field and `getLog()` but none of the 13 log calls; its
`TextLayout.drawInPixels` delegation was missing). Fixed mechanically: the stray line removed; cocoa `Region.getLog()`
(returns null, as in I31); gtk `TextLayout` hunk re-applied; the 13 gtk `Region` log calls inserted by
`tools/gtk_region_logs.py` (before the first non-check statement of each public add/intersect/subtract/translate).
A fuzzy re-apply (`git apply -C1`) duplicated the gtk `log` field, `getLog()` and the TextLayout hunk (into
`getLineCount`); the duplicates were removed. Gate now, real: win32 886/0, gtk 675/0, cocoa 816/0. Found by leaf 2's
`compile_m3gc.sh`, which counts launcher failures as errors (PRE_EXISTING.tsv P01, P03 on the backend branch).

## Leaf 3: the drawn planes hand listeners a real GC (`m3/gc-planes-wiring-20261030`, on master)

The gap, measured: the drawn planes of the `custom`, `m3` and `gpu` modes (`DrawnPlane`, `TableDrawnPlane`,
`TreeDrawnPlane`, `ListDrawnPlane`) raised no `MeasureItem`, `EraseItem` or `PaintItem` (`grep -cE` 0 in all four), while the
native win32 `Table`/`Tree` send them from `NM_CUSTOMDRAW` with a GC on the item DC. Worse than nothing: the behaviour pass
(`DrawnPlane.behaviour`, the native `WM_PAINT` into the back buffer) did run the native custom draw, so a listener got the
events with a GC on the back-buffer DC and the drawn pass then painted over whatever it drew. A JFace owner-draw label
provider or `StyledCellLabelProvider` saw its events and lost its pixels.

### What the leaf does (contract donor: the native `Table`/`Tree` of this repository, REUSE_DIRECT; pattern: I31 trick T9)

- `DrawnCustomDraw` (common `internal.render`, headless): the protocol without the widget. Per cell `MeasureItem` (bounds:
  the cell's image and text; the listener's bounds read back in pixels), `EraseItem` (bounds and clip: the cell; the
  outcome is the listener's `event.detail`, or 0 on `doit == false`), `PaintItem` (content bounds, cell clip). One reusable
  `Event` (every field a listener sees is reset per event), coordinates in points as natively (`DPIUtil`, edge by edge, the
  widget's autoscaling zoom), the GC clipped before and handed to `released` after each event.
- `DrawnOwnerDraw` (widgets, package-private, a `Theme.RowPainter`): installed on the plane's `RowRenderer` (`override`) for
  the frames in which the widget hooks one of the three events, checked on each paint. It paints the row as
  `paintDefault` does, with the events around each cell: the kept detail bits decide the cell's own background colour
  (`SWT.BACKGROUND`), the selection (`SWT.SELECTED`), the image and text (`SWT.FOREGROUND`); a cleared `SWT.FOCUSED` drops
  the focus rectangle. `event.index` is the column index (creation order; `DrawnPlane.cellColumn` per display cell),
  `event.item` the facade (`DrawnPlane.rowItem`; `TableDrawnPlane.facade` creates one while a listener is hooked, as the
  native `CDDS_SUBITEMPREPAINT` forces one). The GC's font is the cell's, its colours the native widget's for the cell's
  state (the theme's selection colours when the theme draws its own selection), through one `Color` object each.
  `MeasureItem` bounds are applied as the widget applies them (`DrawnPlane.measured`: `Table.setScrollWidth`/`setItemHeight`,
  `Tree.setScrollWidth`/`setItemHeight`).
- `event.gc`: the frame's GC through the new seam `BufferCanvas.gc (Control, BackBuffer)` (default null; `RowCanvas`
  untouched, no Skija type named outside the M3 tree). `GcRowCanvas` answers its own frame GC (made on first use, before
  any `clip`, disposed at `end`); after each event the plane gives it the frame's clip back (its other state, font and
  colours, is set before each event; a transform or alpha a listener leaves stays with the frame, where the native widget
  made a new GC per event: documented deviation). `M3RowCanvas` answers `new GCExtension (new M3ExternalGC (new
  M3PlaneCanvasExtension (control, buffer, this::surface), SWT.NONE))`: one extension per canvas, one GC per surface
  (`surface ()` is new, re-read per call because `target` replaces the surface when the buffer grows), made inside a frame
  so `M3ExternalGC.initialSaveCount` is the frame's; the plane disposes it after each event (the renderer's canvas goes back
  to the frame's state) and reuses it, which needed the leaf-2 correction L3-01 (`dispose` also resets `isClipSet`,
  `currentClipRegion`, `currentClipBounds`, `currentTransform`; the stale flag would have popped the frame's own clip).
- One source of events: while a listener is hooked, `DrawnPlane.behaviour` sets `Table/Tree.ignoreCustomDraw` around the
  native `WM_PAINT`/`WM_PRINTCLIENT` call (previous value restored), so the native custom draw sends nothing and the drawn
  pass sends each event once with a GC whose drawing reaches the screen.
- Rule 28 (the render thread makes no SWT GC): a frame whose widget hooks a custom-draw event takes the UI-thread path even
  with `-Dswt.drawn.renderThread=true` (checked on each paint; the render thread's canonical frame and slots serve again
  once the listener is gone). Owner-drawn rows are never retained (rule 24: a listener may draw from state no key holds).
- `ListDrawnPlane`: unchanged; a `List` has no items to hand out (`rowItem` stays null, so the default painting runs).

### Method and gates (2026-10-30; base master `ef6af883b04`, PRs #267/#268 merged through #269)

- Seam edits by Read + Edit on the owner files; the leaf-2 correction by `tools/m3gc_wiring_deltas.py` (byte preserving,
  idempotent; it also appends the L3 rows to `RECIPE_MEMORY.tsv`). Donors in `FOSS_REUSE_DECISION.tsv`
  (`m3-gc-planes-wiring:*`, two rows: the native contract REUSE_DIRECT, I31 `TreeItemRenderer.java:287-347` PATTERN_ONLY).
- `compile_m3gc.sh C:/m3-superset/swt-gcseam H:/m3-scratch/gc-wiring/main`: **920 sources, 0 errors** with `M3_OVERLAY`
  (P02 only; `m3gc_baseline_overlay.py` now skips P01, which master fixed: L3-08), the 2 P02 errors without it (the same
  as the base: 918/2 at `ef6af883b04`). `compile_platform.sh C:/m3-superset/swt-gcseam win32|gtk|cocoa` (Windows paths):
  **win32 888 / 0, gtk 678 / 0, cocoa 817 / 0** (base 886, 675, 816: `DrawnCustomDraw` is common, `DrawnOwnerDraw` win32).
- Natives for the headless gate (L3-07, `tools/m3gc_natives.sh`): every leaf-2 draw test initializes `Device`
  (`new Color (null, ...)` -> `Resource (Device)` -> `Device.getDevice ()` -> `Device.<clinit>` loads `Display` -> `OS` ->
  `Library`), which loads `swt-osversion`/`swt-win32` of the worktree's `Library` revision. Leaf 2 passed 59/59 at revision
  `4975r15` because `~/.swt/lib/win32/x86_64` holds that set; master is `4975r19` and no r19 dll was built on this machine,
  so on master the same 59 tests failed with `Could not load SWT library` before their first draw (the coordinator's run:
  66 found, 29 passed, 37 failed; not a leaf-3 regression, the leaf's 7 tests passed). `m3gc_natives.sh` stages the newest
  set (r15, r14 for awt/wgl) under the r19 names in `H:/m3-scratch/gc-wiring/natives`, never into `~/.swt`, and
  `test_m3gc.sh` hands it to the JVM as `-Dswt.library.path` (`M3_NATIVES`).
- `test_m3gc.sh` (headless, no `Display`; `AllM3GcTests` now includes `Test_org_eclipse_swt_m3gc_DrawnCustomDraw`, 7 tests over
  a `GCExtension` on the M3 doubles: nothing hooked / no GC sends nothing; Measure then Erase then Paint per cell with the
  native bounds and detail; cleared detail bits and `doit` are the outcome and the reused event is reset; the clip is the
  cell in points, scaled back to the surface, set before the listener and released after; `MeasureItem` bounds read back
  in pixels; the extension is reusable after `dispose`): tests compile 28 sources / 0 errors; **66 tests found, 66 passed,
  0 failed** (the 59 of leaf 2 and the 7 new ones; suite order, `M3_NATIVES` staged).
- Display probe (`tools/OwnerDrawProbe.java` through `tools/probe_owner_draw.sh <mode>`: hidden desktop via `ui_lock.sh` +
  `hidden_java.sh`, the staged natives, `-Dswt.widget.mode=<mode>`): a two-column `Table` of 5 rows with the three
  listeners; `PaintItem` fills its cell red through `event.gc`; after 1.5 s of events, `table.print (gc)` into an `Image` and
  a pixel read inside the second cell of row 1. **PASS in `m3`**: `measure=22 erase=22 paint=22 printPaint=10` (one event
  of each kind per cell per paint; the print is one paint of 10 cells), `gcExtension=true` (every `event.gc` was a
  `GCExtension`), `order=true` (Measure, Erase, Paint for every cell), `pixel=ff0000`. **PASS in `custom`**: the same
  counts and order, `gcExtension=false` (the plane's own GC, as designed), `pixel=ff0000`. `gpu`: NOT_RUN (no GL context
  on the hidden desktop was attempted; the `gpu` backend is the same `M3RowCanvas` with a GPU surface).

## Leaf 4: the GPU M3 tree compiles against the bound Skija; `SWT.M3` canvases behind `swt.widget.mode` (`m3/gc-leaf4-gpu-activation-20261030`)

Base: master `d5f6048f398` (PR #270 merged). Three parts, one owner file at a time, each gated before the next.

### (1) P02, a master defect: `Replay.java` used Skija 0.143's `PathBuilder` (commit `a740e10bfe6`)

`Eclipse SWT GPU M3/win32/.../gpu/m3/Replay.poly` built its polygon with `PathBuilder` / `detach ()`; the fork binds 0.116.3,
where `Path` is its own builder (leaf 2's D07/D08). The same delta, as D16/D17 in `tools/m3gc_api_deltas.py` (byte preserving,
idempotent, recorded in `RECIPE_MEMORY.tsv`): `try (Path builder = new Path ())`, draw `builder._ptr` directly, one close.
`compile_m3gc.sh` then reaches **920 sources, 0 errors without any `M3_OVERLAY`**; the whole GPU M3 tree (`Replay`, `M3Target`,
`M3PlaneBackend`) compiles against 0.116.3 and `rg` finds no other 0.143 name (`PathBuilder`, `RegionOp`, `detach ()`,
`addPolygon`, `build ()`) in it. `PRE_EXISTING.tsv` gained a `status` column (`m3gc_baseline_overlay.py`): P01 and P03 FIXED on
master (leaf 1 correction), P02 FIXED `a740e10bfe6`, P04 OPEN; the overlay script writes no overlay for a fixed row (no anchor
left, L3-08) and the gates no longer need one.

### (2) Activation of the M3 canvas path, default off

- `SWT.SKIA` is `SWT.M3` (same bit `1 << 23`; brief rule 14: nothing of ours is named Skija or Skia). No deprecated alias:
  the only users were the adopted `ExternalCanvasHandler.isActive` and the `M3CanvasFactory` javadoc, both ours now
  (L4-01, L4-02 through `tools/m3gc_activation_deltas.py`; FOSS row `m3-gc-canvas-activation:SWT.M3`, ADAPT).
- `M3CanvasFactory` (leaf 2 answered null) decides mode x style bit: `backend (WidgetMode, style)` is `GPU`, `M3` or null
  (no `SWT.M3` bit, or a mode that is not drawn through the M3 renderer); `create (canvas, style)` hands the canvas to the
  raster or gpu maker. The shipped no-arg constructor (ServiceLoader, Display registration) binds the existing selector
  `WidgetMode.of ("Canvas", M3, GPU)` (`swt.widget.mode.Canvas`, then `swt.widget.mode`, the lookup `RowBackends` uses) and
  `M3CanvasHandler::raster` / `::gpu`; the three-argument constructor takes a stubbed selector and makers (the headless test).
- `M3CanvasHandler implements IExternalCanvasHandler` (package `org.eclipse.swt.internal.render.m3`, next to `M3RowCanvas`,
  because `M3Gpu` is package-private there): the canvas's `WM_PAINT` into a retained `BackBuffer` of the canvas. Per frame:
  `BeginPaint`, `ensureFrame (client)`, the planes' surface recipe (`Surface.makeRasterDirect` over the DIB in `m3`, an
  `M3Gpu` render target read back into the damage in `gpu`, exactly `M3RowCanvas.target`/`end`), `save` + `clipRect (damage)`
  + `clear (canvas background)` unless `SWT.NO_BACKGROUND`, one `new GCExtension (new M3ExternalGC (new M3PlaneCanvasExtension
  (canvas, buffer, this::surface)))` per paint (as the stock path makes a GC per `WM_PAINT`; `extension.frame (damage)` is its
  clip), the Paint event with the damage as bounds in points, `dispose`, `restore`, `present` (BitBlt), `EndPaint`,
  `LRESULT.ZERO`. `scroll` moves the buffer's pixels (BitBlt) and the window's (`ScrollWindowEx`, `SW_INVALIDATE | SW_ERASE`,
  `SW_SCROLLCHILDREN` for `all`); `redrawTriggered` is a no-op (the buffer retains; `Canvas.redraw` invalidates); Dispose
  releases surface, readback, gpu and buffer. The handler is the canvas's data under `M3CanvasHandler.DATA_KEY` and answers
  `pixel (x, y)` for probes. Pattern only: I31 `SkiaGlCanvasExtension.doPaint`/`executePaintEvents` and
  `GLPaintEventInvoker.paint` (FOSS rows `m3-gc-canvas-activation:*`); contract: this repository's `Composite.WM_PAINT`.
- Registration: `ExternalCanvasHandler.registerFactory (className)` (the ServiceLoader result keeps precedence when it already
  ran; a class that cannot start leaves the stock path) and `Display.registerM3Canvas ()` (win32, from `init`), which registers
  `RowBackends.M3_CANVAS_FACTORY` by name only when `WidgetMode.of ("Canvas", M3, GPU)` is `M3` or `GPU`. Not
  `META-INF/services`: the fragment's `build.properties` `source..` has no `Eclipse SWT Render M3` folder and `bin.includes = .`
  is the compiled output, `build-swt.sh` copies classes only, and the M3 tree is compiled and loaded apart from the fragment
  (as `RowBackends` loads `M3RowCanvas`); an entry naming an absent class would raise `ServiceConfigurationError` and latch
  `FAILED_WITH_ERRORS` for every canvas (L4-03).
- Default off, measured: with no property `WidgetMode.of` answers `ORIGINAL` (a `Canvas` has no viewport), nothing is
  registered, `ExternalCanvasHandler.isActive` stays false and a canvas created with `SWT.M3` paints as it always did (stock
  probe below). With the property, a canvas without `SWT.M3` is stock too. `custom`, `viewport` and `original` are off.
- Documented deviations (`RECIPE_MEMORY.tsv`): L4-09 `Canvas.redraw (x, y, w, h, all)` with a handler repaints the whole
  canvas (adopted I31 hook, untouched); L4-10 event bounds in points by `control.nativeZoom`, the zoom the leaf-2 extension
  scales the GC by (`getAutoscalingZoom` is package-private; equal unless autoscaling is disabled at a non-100 zoom); L4-12 a
  `scroll` on an `SWT.M3` canvas has no `checkWidget` (the adopted `Canvas.scroll` delegates before its own; `Widget.checkWidget`
  is protected). Corrections demanded by javac and the first probe: L4-11 (`@Override` anchor), L4-12, L4-13 (probe loop).

### (3) Gates (2026-10-30; one JVM at a time, memory gate >= 1.5 GB, output under `H:/m3-scratch/gc-leaf4/`)

- `compile_m3gc.sh C:/m3-superset/swt-gcseam H:/m3-scratch/gc-leaf4/main`, no overlay: **921 sources, 0 errors** (P02 commit
  alone: 920 / 0).
- `compile_platform.sh` (the shared tool with its `out=` redirected to `H:/m3-scratch/gc-leaf4/platform/` by `sed`, the disk
  alert of the day: S: and K: at the floor; Windows paths): **win32 888 / 0, gtk 678 / 0, cocoa 817 / 0** (leaf 3: 888, 678,
  817; the common `SWT.java` and `ExternalCanvasHandler` changes compile on all three).
- `test_m3gc.sh C:/m3-superset/swt-gcseam H:/m3-scratch/gc-leaf4/main H:/m3-scratch/gc-leaf4/tests` with
  `M3_NATIVES=H:/m3-scratch/gc-wiring/natives` (staged by `m3gc_natives.sh`): tests compile 29 sources / 0 errors;
  **71 tests found, 71 passed, 0 failed** (the 66 of leaf 3 and the 5 of `Test_org_eclipse_swt_m3gc_M3CanvasFactory`: `backend`
  over every `WidgetMode` x {no bit, other bits, `SWT.M3`, `SWT.M3` | others}; `create` answers the raster double in `M3`, the
  gpu double in `GPU`, null otherwise; the shipped selector is off without the property; `swt.widget.mode.Canvas=m3|gpu|custom`
  selects `M3|GPU|ORIGINAL`, property restored; `SWT.M3 == 1 << 23`).
- Display probes (`tools/M3CanvasProbe.java` through `tools/probe_m3_canvas.sh <stock|m3|gpu>`: hidden desktop via `ui_lock.sh`
  + `hidden_java.sh`, the staged natives): a 320x240 shell with a `Canvas (shell, SWT.M3)` whose `PaintListener` fills
  `(10, 10, 60, 40)` red; after 1.5 s (a timer wakes the loop, L4-13) the stock reference pixels come from `canvas.print (gc)`
  into an `Image` (a stock GC in every mode) and the M3 pixels from the handler's back buffer, read inside the rectangle
  `(20, 20)` and outside it `(120, 120)`.

  | Run | handler | backend | paints | gcExtension | print inside / outside | buffer inside / outside | Result |
  |-----|---------|---------|--------|-------------|------------------------|-------------------------|--------|
  | stock (no property) | false | none | 1 | 0 | `ff0000` / `f0f0f0` | - | **PASS** (plain GC, stock path) |
  | `-Dswt.widget.mode=m3` | true | raster | 1 | 1 | `ff0000` / `f0f0f0` | `ff0000` / `f0f0f0` | **PASS** (every paint a `GCExtension`; back-buffer pixels identical to the stock run) |
  | `-Dswt.widget.mode=gpu` | true | gpu | 1 | 1 | `ff0000` / `f0f0f0` | `ff0000` / `f0f0f0` | **PASS** (an `M3Gpu` GL context was made on the hidden desktop; the readback carries the pixels) |

  The first stock run timed out after 120 s with an empty log: the leaf-3 loop `while (now < end) ... display.sleep ()` never
  woke because a bare canvas raises no event after its first paint (the leaf-3 `Table` kept producing them); `display.timerExec`
  fixed it (L4-13), then stock, m3 and gpu passed in one run each.

### NOT_RUN

- gtk and cocoa: the handler is win32 (the M3 render tree is win32 only); `SWT.M3` there is inert (the common
  `ExternalCanvasHandler` with nothing registered). Compiled on all three, probed on win32.
- Caret and IME on an `SWT.M3` canvas (I31 noted carets unsupported in its handler too), `scroll` with children
  (`SW_SCROLLCHILDREN`), `SWT.NO_REDRAW_RESIZE` growth, a second GL context made current by application code (`makeCurrent`),
  and a Tycho fragment build (the registration is programmatic; a bundle shipping a `META-INF/services` entry still goes
  through the ServiceLoader path): not probed.
- `Canvas.redraw (x, y, w, h, all)` forwarding the rectangle instead of a full repaint (L4-09): a later leaf.

## Leaf 5: every Canvas subclass through the M3 renderer in the `m3`/`gpu` modes (`m3/gc-leaf5-canvas-coverage-20261030`)

Base: leaf 4 `ccf0eaaaef4` (draft PR #271). The gap, measured (`COVERAGE.tsv`, `tools/m3gc_canvas_inventory.sh`, grep + awk over the
`class X extends Y` edges): in the `m3`/`gpu` modes only a canvas created with `SWT.M3` reached `M3ExternalGC`. No widget passes the bit:
StyledText was excluded by the adopted I31 rule, CLabel's `checkStyle` drops it (I31 kept it in the mask, `a5af63895d`), the cursors,
`AnimatedProgress`, `Java2DCanvas` and Nebula's canvases never set it. Before: 0 of 66 inventoried classes reach M3 without the bit.

### Atoms and patterns (versioned)

| Atom | Typed behaviour | Owner | Pattern |
|------|-----------------|-------|---------|
| A1 activation | `(WidgetMode of the class chain, Class<? extends Canvas>, int style) -> GPU \| M3 \| null` | `M3CanvasFactory.backend/accepts/mode`, `WidgetMode.of (Class, Class, ...)`, `WidgetMode.anyRequested`, `IExternalCanvasFactory.accepts`, `ExternalCanvasHandler.isActive`, `Display.registerM3Canvas` | M3-CANVAS-ACTIVATION v2 (v1 = leaf 4, mode x bit) |
| A2 damage | `(RGNDATA ints, rcPaint, style) -> rectangles, bounds, passes, Event.count, clip` | `M3CanvasDamage` (pure), `M3CanvasHandler.paint/frame/pass/present`, `Canvas.redraw (x, y, w, h, all)` | M3-CANVAS-DAMAGE v1 |
| A3 retention | `style -> ensureFrame \| growFrame`; a new GPU target seeded from the buffer | `M3CanvasHandler.prepare/target/gpuTarget`, deferred `release` | M3-CANVAS-RETAIN v1 |
| A4 stroke state | `(lineStyle, dashOffset, miterLimit, given?) -> PathEffect phase, strokeMiter` | `M3ExternalGC.getDashPhase/isMiterLimitSet/getMiterLimit`, `M3PaintManager.performDraw/createPathEffect` | M3-GC-STROKE v1 |
| A5 probe | `(mode, widget) -> handler?, paints, GCExtension paints, exact mismatches vs the stock run, coverage ratio` | `tools/M3CanvasProbe.java`, `tools/probe_m3_canvas.sh` | M3-CANVAS-PROBE v2 (v1 = leaf 4) |

- A1: in `m3`/`gpu` every Canvas subclass gets the handler with or without `SWT.M3`; a class may choose its own mode
  (`swt.widget.mode.StyledText`, then a superclass's key up to `swt.widget.mode.Canvas`, then `swt.widget.mode`: the drawn planes'
  selector and property names, no parallel one); `GLCanvas` and `SWT.EMBEDDED` hosts (pixels not drawn by paint listeners) only with the
  bit; `Decorations` (Shell) never; no property: nothing registered, stock (measured). A foreign factory keeps I31's rule through the
  default `accepts` (bit, never StyledText). Two leaf-4 assertions (no bit -> null in `m3`/`gpu`) encoded the contract this leaf changes on
  the user's brief: rewritten in place (L5-24, L5-25), the test count unchanged.
- A2 (closes L4-09): `Canvas.redraw (x, y, w, h, all)` invalidates the rectangle. The handler reads the update region before `BeginPaint`
  (the `ViewportDamageHook.record` pattern); a merged paint draws one event over the bounds clipped to the region (Skija `Region` clip),
  `SWT.NO_MERGE_PAINTS` one event per rectangle with `Event.count`; only the region's rectangles are read back (gpu) and presented;
  `WS_CLIPSIBLINGS | WS_CLIPCHILDREN` while painting; no background for `NO_BACKGROUND` and `TRANSPARENT` (the stock contract,
  `Composite.WM_PAINT`). `DOUBLE_BUFFERED` is inherent (the back buffer); `NO_REDRAW_RESIZE` changes only what Windows invalidates.
- A3: a canvas that draws over its previous frame (`NO_BACKGROUND`, `TRANSPARENT`) keeps its pixels when the buffer grows; a new GPU target
  starts from the buffer (`Surface.writePixels`, 0.116.3); a listener that disposes the canvas in its Paint event no longer frees the
  surface under the running pass.
- A4: the dash offset is the phase of `PathEffect.makeDash (float[], float)` for `LINE_CUSTOM` only (stock `GC.getPen`: GDI+
  `Pen_SetDashOffset` for a custom pattern); a miter limit given by `setLineAttributes` reaches every stroke (`Paint.setStrokeMiter`) and
  wins over drawPath's 1000; `getLineAttributes` reports the stock 10 (the donor reported 0). Until a limit is given the paint keeps the
  donor's Skija miter (4, drawPath 1000), which the adopted draw tests pin: documented deviation from the stock 10. API by `javap` of
  `skija-shared-0.116.3.jar` (none of `m3gc_api_deltas.py` D1-D17 is involved).

### Method (CPU only, scripts in `tools/`)

- `m3gc_coverage_deltas.py <worktree> [--ledger]`: every owner edit as an exact byte-preserving replacement (an insertion is DONE when the
  new text is present, a deletion when the old text is gone: L5-36), idempotent (a replay prints DONE for every delta); the RECIPE_MEMORY
  rows L5-01..L5-50 (deltas, decisions, and every correction of my own script, tests and probe); with `--ledger` the
  `FOSS_REUSE_DECISION.tsv` rows `m3-gc-canvas-coverage:*` (adapted hashes = git blob shas of the committed owner files).
- `m3gc_canvas_inventory.sh [worktree] [nebula-root]` writes `COVERAGE.tsv` (GNU grep, not the profile's `rg` function: L5-42).

### Coverage (`COVERAGE.tsv`, 66 classes; m3/gpu mode, no `SWT.M3`)

| Set | Classes | Before (leaf 4) | After (leaf 5) |
|-----|---------|-----------------|----------------|
| Fork Canvas family (Canvas + 9 subclasses) | 10 | 0 | 7 (Canvas, StyledText, CLabel, TableCursor, TreeCursor, AnimatedProgress, Java2DCanvas); GLCanvas only with the bit; Decorations, Shell never |
| Fork custom widgets that extend Composite (CTabFolder, CCombo, CBanner, SashForm, ScrolledComposite, ViewForm) | 6 | 0 | 0 (outside the Canvas hook) |
| Nebula Canvas subclasses (`C:/m3-superset/nebula`, direct, non-test) | 50 | 0 | 50 by class (not built or probed here) |
| Total | 66 | 0 | 57 |

### Gates (2026-10-30; one JVM at a time; output under `H:/m3-scratch/gc-leaf5/`)

- `compile_m3gc.sh C:/m3-superset/swt-gcseam H:/m3-scratch/gc-leaf5/main`: **922 sources, 0 errors** (leaf 4: 921; `M3CanvasDamage` is new).
- `compile_platform.sh` (a copy with `out=` under `H:/m3-scratch/gc-leaf5/platform`; Windows paths; logs empty, classes written):
  **win32 888 / 0, gtk 678 / 0, cocoa 817 / 0** (leaf 4: the same counts).
- `test_m3gc.sh ... H:/m3-scratch/gc-leaf5/tests` with `M3_NATIVES=H:/m3-scratch/gc-wiring/natives`: tests compile 32 sources / 0 errors;
  **91 found, 91 passed, 0 failed** (the 71 of leaf 4 and 20 new: `CanvasCoverage` 8, `CanvasDamage` 6, `StrokeState` 6). Classpath-scan
  order (`M3_SCAN=1`): 85 / 91, exactly the 6 P04 failures (pre-existing); the new tests are order independent.
- Display probe (`probe_m3_canvas.sh stock|m3|gpu`, hidden desktop; PNGs of the print and the back buffer and `probe_<mode>.tsv` in
  `H:/m3-scratch/gc-leaf5/probes/`). The reference of an m3/gpu run is the **stock run's print**: in m3/gpu `Control.print` of a handled
  canvas paints through the handler too (`printThroughM3=1`, L5-46). Committed state:

  | Widget | stock | m3 | gpu |
  |--------|-------|----|-----|
  | Canvas, no bit (fill, dashes, miters, partial redraw) | PASS: handler=false, damage 20,20,30,10, on 90/90, phase 1.000, tips 0/35 | **PASS**: raster, 2/2 GCExtension paints, damage **20,20,30,10**, fill mismatch **0**, on 90/89, phase **1.000**, tips **0/39** | **PASS**: gpu, 2/2, damage 20,20,30,10, fill mismatch 0, on 89/90, phase 1.000, tips 0/38 |
  | StyledText (3 lines, selection 3..13) | PASS: ink 1409, selection 494 | **FAIL** (GF01): raster, 1/1 GCExtension, the paint throws in `Canvas.drawBackground`: ink 9000, selection 0, blank mismatch 41040 | **FAIL** (GF01): the same on gpu |
  | CTabFolder (3 tabs) | PASS: handler=false, ink 884 | NOT_COVERED: handler=false, 0 GCExtension (extends Composite) | NOT_COVERED (same) |
  | CLabel (24x24 image + text) | PASS: image box 3,86,24,24, ink 514 | **PASS**: 1/1, image mismatch **0**, ink 543 vs 514 | **PASS**: 1/1, image mismatch 0, ink 434 vs 514 |
  | Canvas(SWT.M3) (leaf-4 probe) | PASS: handler=false, ff0000 | **PASS**: 1/1, rect mismatch **0** | **PASS**: 1/1, rect mismatch 0 |

  Thresholds (set before the m3 runs): non-text regions exact (0 mismatches against the stock print); text coverage within 50 % of the
  stock count; dash phase 1.000 inside the line (an M3 line starts at the half pixel: L5-48); miter tip of limit 1 below that of limit 10.

### Finding GF01 (StyledText under M3; not fixed in this leaf)

`M3ExternalGC.getGCData ()` is null and the public `Canvas/Composite.drawBackground (GC, ...)` reads the GC's `GCData` and HDC, so every
StyledText paint under M3 throws: `NullPointerException` at `Composite.drawBackgroundInPixels(Composite.java:398)` <-
`Canvas.drawBackground(Canvas.java:154)` <- `StyledTextRenderer.drawLineBackground(StyledTextRenderer.java:592)` (full stack:
`H:/m3-scratch/gc-leaf5/probes/GF01_StyledText_stack.txt`); this is why I31 excluded StyledText. On the coordinator's decision GF01 belongs
to the leaf-6 GC-fidelity agent (`C:/m3-superset/swt-gcfix`, census `GC_FIDELITY.tsv` row GF01). A local patch (fill through the GC:
L5-44/L5-45, reverted, L5-49) was measured before the revert: StyledText **PASS** in m3 and gpu (blank mismatch 0, ink 1394 vs 1409,
selection 494 = 494; `H:/m3-scratch/gc-leaf5/probes-gf01-local/`), evidence for leaf 6.

### Deviations (documented)

- One GC per pass: with `NO_MERGE_PAINTS` the stock path hands one GC to all events of a paint, here one per event.
- Background: the canvas's colour (`clear`), not `drawBackground`'s background image or theme part (leaf 4, unchanged).
- The miter limit of a GC that never called `setLineAttributes` is the donor's 4 (stock 10), see A4.
- Not reached by the Canvas hook: CTabFolder, CCombo, CBanner, SashForm, ScrolledComposite, ViewForm (Composite subclasses).

### PRE_EXISTING (proposals; owner files untouched)

- P05 (`M3PaintManager.java:109`, donor): an even-length `LINE_CUSTOM` dash array is multiplied by the line width (stock: lengths).
- P06 (`M3ExternalGC.java:413`, donor): `drawPolyline` draws Skija `drawPolygon` (unjoined segments), so joins and the miter limit never act
  on a polyline; the probe measures the miter on a path (L5-47); the adopted polyline tests pin the call.

### NOT_RUN

- StyledText PASS on the committed branch (GF01, leaf 6); caret and IME on M3 canvases; RTL (`SWT.RIGHT_TO_LEFT`) region mirroring.
- `NO_MERGE_PAINTS` with a complex region, and the deferred release of a canvas disposed inside its Paint event: unit-tested
  (`CanvasDamage`) or reviewed, not probed on a Display; a background image through the GC (only in the reverted GF01 patch).
- A Composite hook (CTabFolder and the other Composite custom widgets): next leaf.
- Nebula widgets: covered by class (the Canvas constructor hook), not built or probed; gtk and cocoa: no handler (compiled only); a Tycho
  build.
