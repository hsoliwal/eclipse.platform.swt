# Text leaf (census L13 + L14 GC half) - TextLayout and GC text through M3

Base `origin/m3/superset-20261030b` @ `0e703903e8e` + real-app fidelity PR #304 head `bacabc9caa3` (merged first, at the
coordinator's request, so text is measured on top of the background/row/grid fixes; the requested `d47c0ff0b47` is not
in the remote). Branch `m3/text-through-m3-20261031`. Skija binding `io.github.humbleui` 0.116.3 (L12 not landed).
Census: `H:/m3-scratch/i31-census/LEAF_QUEUE.md` L13, L14. Recipe rows `RECIPE_MEMORY.tsv` L13-01..L13-12, L14-01..L14-03;
FOSS rows `FOSS_REUSE_DECISION.tsv` `m3-text:*`. Mechanical edits: `tools/m3text_deltas.py <wt> <ATOM>` (exact anchors,
fail-closed, "fixed point" on rerun), bodies in `tools/m3text_bodies/`.

## Atoms and versioned patterns

| atom | pattern | typed shape | files |
|---|---|---|---|
| TEXT_FALLBACK_SAFE v1 | M3-TEXT-FALLBACK v1 | `(TextLayout, origin, selection, flags; GC font/fg/bg/AA) -> stock GC on opaque black + opaque white -> a = max_c(255 - (W_c - B_c)), premultiplied = B -> BGRA_8888 PREMUL raster -> one composite with the GC alpha` | `RM/gc/M3TextLayoutFallback.java`, `M3TextLayoutCall.java` (new), `M3ExternalGC` (textLayoutDraw, drawLayoutRaster) |
| TEXT_FALLBACK_SAFE v2 (GDI_UNDERLAY) | M3-TEXT-FALLBACK v2 | `raster surface, plain translation, 100 %, alpha 255 -> Surface.readPixels under the box -> stock GC draws the layout over those pixels -> opaque composite` (exact stock ClearType) | `M3TextLayoutFallback.underlay/read`, `M3ExternalGC.readSurface/isRasterSurface`, `IM3Surface.readRasterPixels`, `M3SurfaceAdapter` |
| GLYPH_RUN_SINK v1 | M3-RUN-SINK v1 | `TextLayout runs -> capabilities check (before any op) -> fillRect / clipPush / glyphRun(font, HFONT, glyphs, advances, offsets, x, baseline, argb, characters) / clipPop` | `common/internal/canvasext/M3RunSink.java` (new), `W32G/TextLayout.recordRuns`, `W32G/GCPlanes.OpStreamRunSink`, `IExternalGC.runSink`, `GCExtension.runSink` |
| GLYPH_RUN_DRAW v1 | M3-GLYPH-RUN v1 (from GM `M3PlaneBackend.glyphRun` / `Replay` GLYPH_RUN) | `run -> TextBlob.makeFromPosH(ids, pen from advances) (makeFromPos with (du,-dv)) with the typeface of the run HFONT -> canvas.drawTextBlob(x, baseline, paint(argb))` | `RM/gc/M3GlyphRuns.java`, `M3FontTable.java` (new), `IM3Canvas.drawTextBlob`, `M3CanvasAdapter` |
| RUN_STYLE v1 | M3-RUN-STYLE v1 | `TextStyle fg/bg, selection fg/bg (full + partial, clipped second pass), underline single/double/link + strikeout at the stock rects, rise on the baseline, GlyphMetrics runs without glyphs; tab stops = the layout's run widths` | `TextLayout.recordRuns/recordRunLines/riseInPixels` |
| RUN_COVERAGE v1 | M3-RUN-COVERAGE v1 | `CAP_LINES / FALLBACK_FONT / RTL / OFFSETS / CHAR_GLYPHS; rejected: border, error/squiggle underline, justify, right-to-left layout orientation -> fallback, counted (M3GlyphRuns.fallbacks)`; character runs (fNoGlyphIndex, GDI font linking) map code points, glyph 0 through `FontMgr.matchFamilyStyleCharacter` | `TextLayout.runsRecordable/runRecordable`, `M3GlyphRuns.charBlob/append`, `M3FontTable.fallback` |
| SURROGATE_SAFE v1 (L14) | M3-GC-CODEPOINT v1 | `getAdvanceWidth = GetCharWidth; splits: lone surrogate -> U+FFFD before Skia` | `M3GdiText.advanceWidth`, `M3CodePoints.safe` (new), `M3Resources.getTextSplits` |
| TAB_STOPS v1 (L14) | M3-GC-TABS v1 | `getCharWidth = GetCharABCWidths abcB on a colour memory DC; DRAW_TAB: segments measured with GDI, stops every 8 x tmAveCharWidth, gap = nearest whole spaces` | `M3GdiText.charWidth/tabStop`, `M3CodePoints.expandTabs` |
| FONT_TABLE v1 (L14, GC half, partial) | M3-FONT-TABLE v1 | `HFONT -> LOGFONT (face, -lfHeight or tmHeight-tmInternalLeading, weight, italic) + edging -> Skija Font, LRU 128` | `M3FontTable` (used by glyph runs; M3Resources not yet) |

## Decisions

- Text route (coordinator item 1, "GDI text in the raster backend or a gamma/contrast table for Skia"): measured on the
  IDE Java editor against the baseline, Skia glyph runs and the two-pass GDI raster differ by the same ~9.8k px, all of
  it glyph anti-aliasing (Skia LCD coverage vs ClearType). GDI over the real pixels is exact (0 px in
  `testRasterRouteIsStockExactly`). Default `swt.m3.text=auto`: raster surfaces draw TextLayout with GDI over the surface
  pixels; GPU surfaces draw Skia glyph runs (no readback per layout). `runs` and `gdi` stay selectable. A Skia
  gamma/contrast table was not built: it can only approach ClearType, the underlay equals it.
- TEXT_FALLBACK_SAFE: the GC alpha is applied by the composite only (carrying it onto the native GC too would square it
  and switch the stock TextLayout to GDI+); the underlay runs only at alpha 255.
- ONE_TEXT_SHAPER on win32 is Uniscribe (the stock TextLayout's shaper), not Skija's HarfBuzz Shaper: draw and measure
  agree only with GDI positions (leaf 6 TEXT_METRICS, leaf 7 L7-35). HarfBuzz stays the gtk/cocoa route (census L34).

## Results

### IDE smoke (Eclipse JEE 2026-09 install read-only; IDE_SMOKE v2; editor area = the Java editor's StyledText client rectangle 304,105 681x386 = 262,866 px)

Differing pixels against the baseline capture of the same step (install SWT 3.135, `ide_stock.tsv`), `m3text_editor_diff.py`.
"before" = base + #304 without this leaf; "v1" = TEXT_FALLBACK_SAFE v1 (two passes); "runs" = Skia glyph runs everywhere
(`swt.m3.text=runs` equivalent build); "gdi" = two passes everywhere; "final" = this branch's default (`auto`).

| step | m3 before | m3 v1 | m3 runs | m3 gdi | **m3 final** | gpu before | gpu v1 | gpu gdi | **gpu final (runs)** |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| workbench_java_editor | 32,840 | 9,764 | 9,822 | 9,764 | **4,493** | 33,831 | 10,755 | 10,755 | **10,824** |
| java_editor_selection | 29,792 | 9,010 | 9,816 | 9,010 | **4,493** | 30,783 | 10,001 | 10,001 | **10,836** |
| package_explorer_active | 26,004 | 5,222 | 6,028 | 5,366 | **849** | 27,046 | 6,408 | 6,264 | **7,099** |
| editor_tab_switched_back | 29,792 | 9,010 | 9,816 | 9,010 | **4,493** | 30,783 | 10,001 | 10,001 | **10,836** |

m3 final: every remaining differing pixel of the editor rectangle lies in rows 435-459 (the band under the text: the
editor's horizontal scroll bar / bottom edge), 0 in the text rows; the white boxes and the lost punctuation are gone in
both modes (`H:/m3-scratch/text/ide/out/{m3,gpu,stock}/*.png`). gpu final: glyph anti-aliasing only (Skia LCD vs
ClearType), same order as the two-pass GDI raster (10,755 vs 10,824).

After 7/n (L14 ONE_TEXT_SHAPER raster half: GC drawText/drawString draw with GDI over the surface pixels), rerun on the
rebuilt jars (`build_m3_ship.sh`, IDE_SMOKE v2, 2026-10-31):

- m3: workbench_java_editor, java_editor_selection and editor_tab_switched_back are now 4,352 px (was 4,493).
  package_explorer_active is 685 px (was 849).
- gpu: unchanged (10,824 / 10,836 / 7,099 / 10,836). The GC route applies to raster surfaces only.
- Where the remaining m3 differences are: rows 436-459 of the editor rectangle and rows 443-477 for the package
  explorer step. None are in the text rows.
- Workspace .log: the same 11 errors in m3, gpu and the stock baseline. They come from the install (mpc.ui early
  startup, OSGI-INF descriptors), not from SWT.
