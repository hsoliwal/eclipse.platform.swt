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
