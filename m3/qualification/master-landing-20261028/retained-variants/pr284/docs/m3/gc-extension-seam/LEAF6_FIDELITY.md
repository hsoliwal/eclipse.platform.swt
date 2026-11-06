<!-- SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# Leaf 6: GC fidelity of `M3ExternalGC` against the stock win32 GC (`m3/gc-leaf6-fidelity-20261030`)

User directive (2026-10-30, repeated): "https://github.com/swt-initiative31 https://github.com/JetBrains/skija And we are still
struggling - should give our UI framework just about everything". The census
`H:/m3-scratch/i31-census/gc-api-fidelity/GC_FIDELITY.tsv` found 17 bugs, 4 stubs and 2 missing features in the GC our renderer hands
to paint listeners, almost all inherited unchanged from Initiative 31's `SkiaGC` (`SAME_AS_I31`). Real SWT widgets call exactly these
methods: StyledText threw in every paint, CTabFolder threw on its clip. Base: leaf 5 `578bdc98997` (PR #274). Oracle everywhere: the stock
`W32G/GC.java` of this repository; where I31 differs, stock wins. Every row was confirmed by a failing headless test before its fix.
(Kept apart from `README.md`, where leaves 5 and 7 add their sections; merged after they land.)

## Atoms and patterns (versioned)

| Atom | Typed behaviour | Owner | Pattern | Rows |
|------|-----------------|-------|---------|------|
| HINT_STATE | `(antialias, textAntialias, interpolation) -> read back as set; DEFAULT at start; paint AA = ON only; text AA = textAntialias != OFF` | `M3ExternalGC` hint fields, `M3PaintManager.performDraw*`, `M3TextDrawing` | M3-GC-HINT v1 | GF06 |
| STROKE_SCALE | `(width pt, dashes pt) -> stroke px = width x zoom once; custom dashes absolute px, array doubled` | `M3ExternalGC.set/getLineAttributes`, `M3PaintManager.createPathEffect` | M3-GC-STROKE v2 (v1 = leaf 5 A4) | GF14, GF11 (= P05) |
| CLIP_STATE | `(frame px, clip as set, transform) -> one save layer; setClipping replaces; getClipping = clip box in points` | `M3GcLayer` (new), `M3RegionCalculator` (rewritten), `M3ExternalGC.setClipping/getClipping/isClipped` | M3-GC-CLIP v1 | GF02, GF03, GF16, GF18 (GF04 improved) |
| TRANSFORM_STATE | `(frame matrix, SWT transform) -> layer matrix = frame x T; getTransform in points` | `M3GcLayer`, `M3TransformConverter.fromM3Matrix`, `IM3Canvas.getLocalToDevice` | M3-GC-TRANSFORM v1 | GF15, GF16 |
| TEXT_DRAW_FLAGS | `(string, flags) -> lines; drawString verbatim; '&&' -> '&'; transparent text on the destination` | `M3ExternalGC.drawString`, `M3Resources.replaceMnemonics`, `M3TextDrawing.drawTextBlobWithCache` | M3-GC-TEXT-FLAGS v1 | GF29, GF28 (flags), L7-35 (raster) |
| TEXT_METRICS | `font -> stock FontMetrics meaning; (text, flags) -> the stock GC's GDI extent` | `M3ExternalFontMetrics`, `M3GdiText` (new), `M3Resources.textExtent/stringExtent` | M3-GC-TEXT-METRICS v2 (v1 measured with Skia, superseded: L6-26) | GF36, GF28, L7-35 (layout) |
| ARG_CONTRACT | `stock callers' contract: getGCData non-null; drawBackground/print through any GC; drawImage checks; drawPoint = foreground brush` | `M3ExternalGC.getGCData/drawImage/drawPoint`, `M3PaintManager.performFillForeground`, win32 `Composite/Canvas.drawBackground`, `Control/Shell.print` | M3-GC-CONTRACT v1 | GF01, GF24, GF46 |
| PIXEL_COPY | `(src rect, dest) -> BitBlt semantics; copyArea(Image) replaces pixels at the GC zoom` | `M3ExternalGC.copyArea x2/replacePixels`, `M3SurfaceAdapter.makeImageSnapshot`, `M3ToSwtImageConverter` | M3-GC-PIXEL-COPY v1 | GF20, GF21 |
| GC_STATE_RESET | `advanced flag as stock; setAdvanced(false) resets alpha/patterns/transform/clip/hints; dispose resets every attribute` | `M3ExternalGC.setAdvanced/getAdvanced/dispose/resetState`, `IM3Resources.resetFont` | M3-GC-RESET v1 | GF05, GF49 |
| PATH_HIT_TEST | `(path, x, y, GCExtension, outline) -> GDI+ IsVisible/IsOutlineVisible with the GC's fill rule and pen` | win32 `Path.PathHandle.containsThroughGC` | M3-GC-PATH-HIT v1 | GF51 |
| SCALE_ROUNDING | `int point -> pixel: round half up for both signs` | `DpiScalerUtil.scaleUp/scaleDown` | (fix of the adopted scaler) | GF41 |
| GRADIENT_ROWS | `(from, to, n) -> GDI GradientFill row i = (a*257 + (b-a)*257*i/n) >> 8 without advanced graphics` | `M3ExternalGC.fillGradientRectangle/gradientRows/gradientColor` | M3-GC-GRADIENT v1 | L7-31, L7-34, GF47 |
| LINE_ENDS | `axis-aligned 1-px line -> x1 .. x2 - 1` | `M3ExternalGC.drawLine` | M3-GC-LINE-ENDS v1 | L7-32 |

## Rows (census `GC_FIDELITY.tsv` BUG / STUBBED / MISSING, GF09 and GF10 done by leaf 5)

| Row | Status | Commit | Proof (failing before -> passing after) |
|-----|--------|--------|------------------------------------------|
| GF01 getGCData null (StyledText NPE) | FIXED | `c1a45ecf8ff` | `GcContract` 5/5; Display probe StyledText PASS m3 and gpu (below) |
| GF02 getClipping inverted | FIXED | `a91bfb855aa` | `ClipState` (frame in points, rectangle, region, transformed) |
| GF03 region clip by reference | FIXED | `a91bfb855aa` | `ClipState.testRegionClipIsACopy` (caller disposes after setClipping) |
| GF05 setAdvanced no-op | FIXED | `69741fee16b` | `GcStateReset` |
| GF06 hint round trip | FIXED | `d992ec9513c` | `HintState` 6/6 |
| GF11 custom dash x width | FIXED | `7a6997f7ccd` | `StrokeScale` (raster runs [48, 24] -> [8, 4]) |
| GF14 line width pixels in points | FIXED | `7a6997f7ccd` | `StrokeScale` |
| GF15 getTransform m21/dx | FIXED | `a91bfb855aa` | `TransformState.testGetTransformAnswersPoints` |
| GF16 transform x clip stack | FIXED | `a91bfb855aa` | `ClipState`/`TransformState` on `StateCanvasDouble` (Skia's save stack) |
| GF18 region replay | FIXED | `a91bfb855aa` | `ClipState` (translate + negative at 200 %, region filled by the stock GC) |
| GF20 copyArea null snapshot | FIXED | `04897d878e0` | `PixelCopy` (real raster) |
| GF21 copyArea(Image) colours/scale/blend | FIXED | `04897d878e0` | `PixelCopy.testCopyIntoAnImageKeepsTheColours` |
| GF24 drawImage argument contract | FIXED | `70f26507f3f` | `ArgContract` |
| GF28 textExtent/stringExtent | FIXED (v2) | `f1cbab666bd`, `adf5bf38526` | `TextMetrics`, `CompositeParity.testTextExtentsAreTheStockGcs` (equal to a stock GC) |
| GF29 drawString processed delimiters/tabs | FIXED | `b2529faa7b2` | `TextDrawFlags` |
| GF36 font metrics | FIXED | `f1cbab666bd` | `TextMetrics` (ascent without internal leading, invariant, single scaling) |
| GF46 drawPoint alpha/pattern | FIXED | `70f26507f3f` | `ArgContract` |
| GF49 GC state leak on reuse | FIXED | `69741fee16b` | `GcStateReset.testDisposeResetsEveryAttribute` |
| GF51 Path.contains NPE | FIXED | `e4d5c2610eb` | `PathHitTest` |
| GF48 RTL mirroring (MISSING) | DEFERRED | - | next leaf, plan below |
| GF50 new GC(canvas) outside paint (MISSING) | DEFERRED | - | next leaf, plan below |
| Taken in on evidence: GF41 (scale rounding), GF47 (gradient negative extents), P04 (test property leak), P05 (= GF11) | FIXED | `d311f072154`, `adf5bf38526`, `23e406a1527`, `7a6997f7ccd` | `ScaleRounding`, `CompositeParity`, suite with the text cache on |
| Leaf 7 findings L7-30 (CTabFolder NPE), L7-31/L7-34 (gradients), L7-32 (line ends), L7-35 (CTabFolder text) | FIXED in GC (L7-30 by `a91bfb855aa`; L7-31 gradient, L7-34, L7-35 by `adf5bf38526`; L7-32 by `adf5bf38526` + `aeeb35ec099`); L7-31's anti-aliased curve DEFERRED (GF43) | | `CompositeParity` 4/4; composite probe (below) |

No row was found not to be a bug: every claim reproduced in a failing test first (counts in `LEAF6_RECIPE_MEMORY.tsv`).

## Initiative 31: where this follows or departs from `SkiaGC` (skija-canvas `skia_canvas` 9bcb404c42)

Departures (stock wins): DEFAULT interpolation samples nearest, not Mitchell (HINT_STATE); one layer instead of save-per-setTransform
(CLIP_STATE); region clips from the region handle, not the RegionLog replay; text measured by GDI (TEXT_METRICS v2); transparent text not
cached; GradientFill rows without advanced graphics. Followed: every other adopted behaviour (paint construction, caps/joins, patterns, the
text image cache for opaque text, the donor's predefined dash tables (GF12) and default miter 4 until set (leaf 5)).

Offer back to I31 (`SAME_AS_I31` rows whose fix applies unchanged after the rename map of README leaf 2): GF05, GF06, GF11, GF14, GF15, GF20,
GF21, GF24, GF29, GF36, GF41, GF46, P04, the transparent-text and line-end fixes; with the new `M3GcLayer` class: GF02, GF03 (copy), GF16;
win32 only (same code in I31's win32 widgets/graphics): GF01 (`Composite/Canvas.drawBackground`, `Control/Shell.print`), GF51 (`Path`),
GF18 (`Region.win32_getHandle`), GF28 v2 (GDI measurement), GRADIENT_ROWS (GDI's formula); gtk needs its own equivalents (cairo region
rectangles, Pango extents, cairo gradients).

## Method (CPU only)

- TDD per atom: the test class first (headless: doubles, `StateCanvasDouble` for Skia's save stack, real Skija raster surfaces through the
  now public `M3SurfaceAdapter`, an unopened Shell for the widget paths), the failing run kept (`H:/m3-scratch/gc-leaf6/tests*`, overlay
  runs of the committed sources where the gate's memory check had blocked the first run), then the fix, then the suite.
- `tools/m3gc_fidelity_deltas.py`: the multi-site mechanical deltas (L6-01, L6-02) and the record of every semantic edit, decision and
  correction of my own scripts, tests and code (`LEAF6_RECIPE_MEMORY.tsv`, the six RECIPE_MEMORY columns, rows L6-01..L6-29).
- Shared owner files were committed per atom by hunk (`split_hunks.py`, `stage_atom.sh`) and every staged tree gated before its commit
  without touching the working tree (`stage_gate.sh`: index versions as `M3_OVERLAY`, the index's tests exported); scratch scripts in
  `H:/m3-scratch/gc-leaf6/edits/`.
- CPU experiments behind decisions: `exp/FontProbe.java` (GDI TEXTMETRIC vs Skija metrics: VDMX-hinted GDI differs 1-2 px at small sizes),
  `exp/ParityProbe.java` (stock text extents vs Skija advances under every hinting; GDI GradientFill rows vs six formulas: only the
  16-bit one is exact), `exp/TextSmear.java` (the raster glyph blocks reproduced off-screen).

## Gates (2026-10-30; one JVM at a time; output under `H:/m3-scratch/gc-leaf6/`)

- `compile_m3gc.sh C:/m3-superset/swt-gcfix H:/m3-scratch/gc-leaf6/main` (no overlay): **924 sources, 0 errors** (leaf 5: 922; new
  `M3GcLayer`, `M3GdiText`).
- `compile_platform.sh` (a copy with `out=` under `H:/m3-scratch/gc-leaf6/platform`; Windows paths; logs empty, 1367 / 1015 / 1124
  classes written): **win32 888 / 0, gtk 678 / 0, cocoa 817 / 0** (leaf 5: the same counts; the win32 set carries the widget and `Path`
  edits).
- `test_m3gc.sh ... H:/m3-scratch/gc-leaf6/tests` (`M3_NATIVES=H:/m3-scratch/gc-wiring/natives`): tests compile 47 sources / 0 errors;
  **150 found, 150 passed, 0 failed** (leaf 5's 91 and 59 new: HintState 6, StrokeScale 6, ClipState 9, TransformState 3, TextMetrics 6,
  TextDrawFlags 4, ScaleRounding 1, GcContract 5, ArgContract 6, PathHitTest 2, PixelCopy 4, GcStateReset 3, CompositeParity 4).
  Classpath-scan order (`M3_SCAN=1`): **150 / 150** (leaf 5: 85 / 91, the 6 P04 failures; P04 is fixed).
- Every intermediate commit of a shared owner file was gated on its staged tree (`stage_gate.sh`: 923 sources / 0 errors and the suite of
  that commit green: 137, 139, 143, 146).

## Display probes (hidden desktop, `ui_lock.sh`; reference = the stock run's print)

Leaf 5's canvas probe (`probe_m3_canvas.sh stock|m3|gpu` on this branch, final head `aeeb35ec099`; `H:/m3-scratch/gc-leaf6/canvasprobe2`):

| Widget | m3 | gpu |
|--------|----|-----|
| Canvas (fill, dashes, miters, partial redraw) | PASS: damage 20,20,30,10, fill mismatch 0, on 90/90, phase 1.000, tips 0/39 | PASS: the same, tips 0/38 |
| StyledText (GF01) | **PASS**: ink 1394 vs stock 1409, selection 494 = 494, blank mismatch 0 (also measured at `c1a45ecf8ff`, the GF01 commit) | **PASS**: the same |
| CLabel | PASS: image mismatch 0, ink 434 vs 514 | PASS: the same |
| Canvas(SWT.M3) | PASS: rect mismatch 0 | PASS |
| CTabFolder | NOT_COVERED here (no Composite hook on this branch; leaf 7's probe below) | NOT_COVERED |

Leaf 7's composite probe (`M3CompositeProbe`, branch `m3/gc-leaf7-composite-coverage-20261030` head `32645494193` merged locally with
this branch in the scratch worktree `C:/m3-superset/swt-gcfix-l7`, never pushed; `H:/m3-scratch/gc-leaf6/l7probe*`). "Before" = leaf 7's
merge with leaf 6 up to `69741fee16b` (every census row fixed, none of the leaf-7 findings):

| Composite | before m3 / gpu | final m3 | final gpu |
|-----------|-----------------|----------|-----------|
| CTabFolder | FAIL: strip ink 425 vs 165 (glyph blocks) / FAIL: 264 vs 165 ("Tab..."); no exceptions (GF02 fixed) | **PASS**: body 0, close 17/17 mismatch 0, fills equal, strip ink 108 vs 210 | **PASS**: body 0, fills equal, strip ink 106 vs 210, close 15/17 (mismatch 62) |
| CBanner | FAIL: curve 16, flat 85 / FAIL: 719, 117 | FAIL: flat **0** (gradient rows exact), curve 16 | FAIL: flat 25, curve 719 |
| ViewForm | FAIL: flat 1 at (3,19) | **PASS**: border 0, flat 0 | **PASS**: border 0, flat 0 |
| ChildClip | PASS | PASS (flat 0, window child 00ff00) | PASS |
| Container | PASS | PASS (stock mismatch 0) | PASS |
| Nebula CollapsibleButtons | FAIL: CustomButton mismatch 11008, ink 0/129 / 0/53 | **PASS**: CustomButtons ink 0/0, mismatch 210/342/323, mean delta 0.92/2.06/1.72, images 0, ToolbarComposite 0 | **PASS**: mismatch 213/343/324, mean delta 0.92/2.05/1.72 |

The stock reference itself moved between two stock runs of the same code (CTabFolder strip ink 165 at 19:11, 210 at 19:26; this branch
changes no stock path: every widget edit is behind `gc instanceof GCExtension`), so strip ink is compared within the 50 % threshold only.
CBanner's remaining difference is its anti-aliased curve (`drawPolyline`), drawn one pixel left of the stock GDI+ curve (rows 170..179,
columns 342..347): the stock half-pixel offset that `drawPolyline` lacks (GF43), deferred with the adopted polyline tests that pin it.

## Deferred, with the next leaf's plan

- **GF48 RTL mirroring.** `M3CanvasHandler.event` makes the GC with `SWT.NONE`; a `SWT.RIGHT_TO_LEFT` canvas paints unmirrored. Plan: pass
  `SWT.RIGHT_TO_LEFT | SWT.MIRRORED` and open the frame with a horizontal flip (`scale (-1, 1)`, `translate (clientWidth, 0)`) as the
  frame matrix `M3GcLayer` already composes with; counter-flip around glyph runs (`M3TextDrawing`) and images (`drawImage`, stock flips
  them back unless `LAYOUT_BITMAPORIENTATIONPRESERVED`); `getStyle` then answers `MIRRORED` and drawPolygon's x-adjust becomes live.
  Tests: `StateCanvasDouble` frame matrix plus raster pixels of text and an image in a mirrored frame.
- **GF50 `new GC (canvas)` outside paint.** A stock GC on the HWND draws pixels the next M3 frame overwrites. Plan: in
  `Control.internal_new_GC` of a canvas with an M3 handler, hand out the back buffer's memory DC (the DIB the raster surface shares) and
  present the drawn bounds in `internal_dispose_GC`; in gpu mode re-seed the render target from the buffer (`writePixels`, as A3 does on
  growth). Until then widget layout measured with such a GC agrees with paint (TEXT_METRICS v2 measures as GDI).
- Text drawing beyond metrics: M3TextDrawing advances lines by Skia's ascent + descent + line gap (GDI: tmHeight), draws Skia glyph
  widths (a line can exceed its GDI extent by 1-2 px), no mnemonic underline (GF30), tabs at 8 x Skia's average width (GF31).
- Anti-aliased curves (CBanner's polyline band, L7-31): two rasterizers never agree pixel for pixel; GF43 (polyline joins, half pixel)
  remains approximated. `RegionLog` keeps growing for I31's reader; M3 no longer reads it (leaf 1 seam: bound or drop it).

## NOT_RUN

- gtk and cocoa: compiled only (the M3 tree and its widget hooks are win32).
- GF48 and GF50: deferred (plans above); CBanner's anti-aliased curve (GF43): deferred.
- Display probes at a zoom other than 100 % (this machine runs at 100 %); zoom is covered by the headless tests (125 .. 200 %).
- `Shell.print` through a GCExtension (only `Control.print` is tested headless); a background image through the GC (headless only, no
  probe widget has one); `copyArea (Image)` above 100 %; GradientFill rows for a decreasing channel (the formula was measured on
  increasing channels only); `Path.contains` under a GC transform (stock ignores it too).
- The leaf 6 x leaf 7 merge exists only in the scratch worktree; the PR is stacked on leaf 5 and merges with leaf 7 at the suite list
  (`AllM3GcTests`: keep both sides).
