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
