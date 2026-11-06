<!-- SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# Real-app fidelity: the defects ControlExample found in the M3 renderer (`m3/realapp-fidelity-20261031`)

User directive (2026-10-31): "https://github.com/swt-initiative31 https://github.com/JetBrains/skija And we are still
struggling - should give our UI framework just about everything"; "enhance them and perfect them all". The real-app run
(`H:/m3-scratch/realapp`: ControlExample and CustomControlExample end to end in stock, `-Dswt.widget.mode=m3` and `gpu`,
31 tabs) found what the headless and probe gates of leaves 4..7 did not. Base: `m3/superset-20261030b` at `41a7e0bb06f`
(leaves 4..7, feature switches, theme and language #286). Census plan: `H:/m3-scratch/i31-census/LEAF_QUEUE.md` L8, L10,
L16 (and GF48 of L19). Oracle everywhere: the stock win32 path of this repository on the same screen.

## Atoms and patterns (versioned), one commit each

| # | Atom | Typed behaviour | Owner | Proof (failing first -> after) | Recipe row |
|---|------|-----------------|-------|--------------------------------|------------|
| 1 | NATIVE_STAGE_BY_OID v1 | `(LFS pointer at HEAD, git-common-dir lfs/objects) -> staged dll + {name, oid, size, sha256, verdict}`; no rename fallback | `tools/m3gc_natives.sh`, `tools/test_m3gc.sh` | verify of the shared staging dir: 5 x OID_MISMATCH (r10 renamed r19) -> ok=6 bad=0 | LF-01 |
| 2 | GATE_POSITIVE_COUNT v1 | `logs -> green iff sources > 0, classes > 0, errors = 0, tests found > 0, failed = aborted = 0` | `tools/compile_m3gc.sh`, `tools/test_m3gc.sh`, `tools/m3gc_compile_platform.sh` (new) | witness suite: `1 tests failed`, exit 0 -> exit 1 | LF-02 |
| 3 | MODE_TRUTH v1, BACKEND_REPORT v1, M3_LOAD_LATCH v1 | `widget -> route actually drawn (plane backend / canvas handler / gate)`; `widget -> backend name`; `Library.staticLoad once -> stock/GC path on LinkageError` | `Widget.m3Route/m3Backend` (3 platforms), plane widgets, `Canvas`, `Composite`, `DrawnPlane`, `BufferCanvas`, `M3Mode.backend`, `M3BackendReport`, `M3Library` | `M3ModeTruth`: "Table in m3 answers viewport" -> m3 2/2, gpu 2/2, viewport 3/3; real app with an empty `skija.library.path`: 4 EXCEPTION rows -> 31/31 PASS, 0 exceptions | LF-03, LF-04 |
| 4 | BACKGROUND_PARITY v1 | `damage rect -> stock chain (image at offset, parent theme part, colour) on the frame's DC, opaque` | `M3BackgroundChain` (new), `IExternalCanvasHandler.paint` overload, `Canvas`, `Composite.fillM3Background`, `M3CanvasHandler.background` | real app CLabel 11589 -> 3312, CTabFolder 11651 -> 5847 (m3) | LF-05 |
| 5 | ROW_IMAGE_BOUNDS v1 | `(image list size, row) -> image box centred, rounded down` | `RowPaint.imageHeight`, `RowRenderer.paintCell`, `DrawnPlane.imageListHeight` | swatch region 1408 px -> 0 | LF-06 |
| 6 | GRID_LINES v1 | `themed report view -> column detail on each cell's last pixel, rows and empty area; cell colour to edge - 2` | `RowPaint.COLUMN_DETAIL`, `RowRenderer.columnDetail/cellFillWidth/paintEmpty`, `TableDrawnPlane.emptyArea` | System tab column lines absent -> equal at x 70..465 | LF-07, LF-12 |
| 7 | TEXT_TRUNCATION v1 | `(HFONT, text, width) -> DrawText DT_END_ELLIPSIS cut (GDI extents, >= 1 character)` | `M3GdiEllipsis` (new), `M3RowCanvas.ellipsis/cut` | oracle "#FFFFFF  in 10 px" failed -> 567/567 cuts equal | LF-08 |
| 8 | RTL_FRAME v1 | `WS_EX_LAYOUTRTL -> frame x -> clientWidth - x; text runs and images flipped back; buffer and present unmirrored` | `M3Mirror` (new), `M3TextDrawing`, `M3ExternalGC.drawImage`, `M3CanvasHandler` | `M3CanvasMirroring`: 194 px differ from the stock canvas -> 0 | LF-09 |
| 9 | SWITCHER_SIZE v1 | `entries change -> drop-down layout flushed -> width >= widest entry` | `M3ThemeSwitcher.sync` | `ComboContentSize`: "drop-down 103 for entries up to 176" -> 2/2 | LF-10 |
| 10 | ROW_OFFSET v1 | `tree row -> text box = item height - 1 (TreeView text cell tmHeight + 1)` | `RowPaint.textInset`, `TreeDrawnPlane` | `TreeTextProbe` ink tops 5,6,6,7,9 -> 4,5,6,7,8 (= stock) | LF-11 |
| 11 | MODE_TRUTH v1 (second pass) | `custom widget with its own viewport gate -> its own name decides` | `M3Mode.gate` | `M3ModeTruth`: "StyledText in viewport answers original" -> 7/7 in viewport, original, m3, gpu | LF-13 |

Mechanical multi-site edits: `tools/m3gc_realapp_deltas.py MODE_TRUTH` (LF-03a..f: the `Widget` hooks on win32, gtk and cocoa,
the plane hooks of `Table`, `List`, `Tree`; a second run reports UNCHANGED). Every other edit is semantic and recorded in
`RECIPE_MEMORY.tsv` (LF-01..LF-13, the six columns), including my own corrections: the staging script's receipt in verify
mode, `OS.GdiFlush` that does not exist, the ellipsis minimum of one character, the double mirror of a memory DC made
compatible with a mirrored paint DC, a Combo change reverted because its test passed on the old code.

## Real-app smoke (genuine r19 natives; `H:/m3-scratch/fidelity/{base,final}/out`)

Differing pixels of each m3/gpu screen against the same build's stock screen (`diff_report.py`, CPU). The first four
columns count above y = 832: the shell's bottom strip (outside every example widget) is captured before it paints in some
stock runs, in either build (the evidence run had it on 20 tabs; the first stock run of the final build on 26). "Before"
= the base branch, "after" = this branch; stock screens of the final build from the run with `-Drealapp.settle=4000`.

| tab | m3 before | m3 after | gpu before | gpu after | whole shell m3 before / after | whole shell gpu before / after |
|-----|----------:|---------:|-----------:|----------:|------------------------------:|-------------------------------:|
| ControlExample_00_Button | 0 | 0 | 0 | 0 | 0 / 22510 | 0 / 22510 |
| ControlExample_01_Canvas | 356 | 356 | 343 | 343 | 356 / 374 | 343 / 361 |
| ControlExample_02_Combo | 0 | 0 | 0 | 0 | 0 / 15 | 0 / 15 |
| ControlExample_03_CoolBar | 0 | 0 | 0 | 0 | 0 / 15 | 0 / 15 |
| ControlExample_04_DateTime | 332 | 85 | 331 | 85 | 332 / 103 | 331 / 103 |
| ControlExample_05_Dialog | 0 | 0 | 0 | 0 | 0 / 2745 | 0 / 2745 |
| ControlExample_06_ExpandBar | 0 | 0 | 0 | 0 | 0 / 2751 | 0 / 2751 |
| ControlExample_07_Group | 0 | 0 | 0 | 0 | 0 / 15 | 0 / 15 |
| ControlExample_08_Label | 0 | 0 | 0 | 0 | 0 / 18 | 0 / 18 |
| ControlExample_09_Link | 0 | 0 | 0 | 0 | 0 / 15 | 0 / 15 |
| ControlExample_10_List | 2300 | 2300 | 2300 | 2300 | 2300 / 2300 | 2300 / 2300 |
| ControlExample_11_Menu | 0 | 0 | 0 | 0 | 0 / 3453 | 0 / 3453 |
| ControlExample_12_ProgressBar | 0 | 0 | 0 | 0 | 0 / 3450 | 0 / 3450 |
| ControlExample_13_Sash | 5305 | 5028 | 5304 | 5028 | 5305 / 8478 | 5304 / 8478 |
| ControlExample_14_Scale | 0 | 0 | 0 | 0 | 0 / 3450 | 0 / 3450 |
| ControlExample_15_Shell | 0 | 0 | 0 | 0 | 0 / 3450 | 0 / 3450 |
| ControlExample_16_Slider | 0 | 0 | 0 | 0 | 0 / 3450 | 0 / 3450 |
| ControlExample_17_Spinner | 0 | 0 | 0 | 0 | 0 / 0 | 0 / 0 |
| ControlExample_18_System | 33011 | 24774 | 32941 | 24774 | 33109 / 26052 | 33039 / 26052 |
| ControlExample_19_TabFolder | 0 | 0 | 0 | 0 | 0 / 1275 | 0 / 1275 |
| ControlExample_20_Table | 10556 | 7119 | 10528 | 7119 | 10556 / 7171 | 10528 / 7171 |
| ControlExample_21_Text | 0 | 0 | 0 | 0 | 0 / 496 | 0 / 496 |
| ControlExample_22_ToolBar | 0 | 0 | 0 | 0 | 0 / 456 | 0 / 456 |
| ControlExample_23_ToolTip | 0 | 0 | 0 | 0 | 0 / 3450 | 0 / 3450 |
| ControlExample_24_Tree | 3792 | 2295 | 3807 | 2295 | 3792 / 5745 | 3807 / 5745 |
| ControlExample_25_Browser | 0 | 0 | 0 | 0 | 0 / 3450 | 0 / 3450 |
| CustomControlExample_00_CCombo | 1408 | 1131 | 1407 | 1131 | 23918 / 1131 | 23917 / 1131 |
| CustomControlExample_01_CLabel | 11589 | 3312 | 11588 | 3312 | 11589 / 3312 | 11588 / 3312 |
| CustomControlExample_02_CTabFolder | 10856 | 5847 | 11171 | 6410 | 11651 / 5847 | 11966 / 6410 |
| CustomControlExample_03_SashForm | 4346 | 4069 | 4345 | 4069 | 7769 / 4069 | 7768 / 4069 |
| CustomControlExample_04_StyledText | 1378 | 1131 | 1377 | 1131 | 4798 / 1131 | 4797 / 1131 |

Handled widgets, m3 (gpu): CLabel 11589 -> 3312 (11588 -> 3312), CTabFolder 10856 -> 5847 (11171 -> 6410), the drawn
Table tab 10556 -> 7119 (10528 -> 7119), System 33011 -> 24774 (32941 -> 24774), Tree 3792 -> 2295 (3807 -> 2295), the
"Colors and Font" table 1408 -> 1131 on every CustomControlExample tab, DateTime 332 -> 85, SashForm 4346 -> 4069.
The handled canvases with their own drawing (Canvas 356 / 343) and the drawn List (2300) are unchanged: glyph and curve
rasterization, below.

All runs 31/31 PASS, 0 EXCEPTION, 0 BLANK, 0 HANG in stock, m3 and gpu (before and after). "Before" is the base branch
on the genuine natives, not the earlier evidence run (`H:/m3-scratch/realapp/out-l6`), which loaded r10 dlls renamed r19.

Remaining differences, by cause (none of them a background, image box, column line, truncation or tree text offset):
- Glyph rasterization: the drawn planes and the GC draw text with Skia's LCD coverage (levels 3f/67/97/bf/df) where GDI
  ClearType draws 39/66/8f/b6/db at the same glyph positions (List: best shift 0, the whole 2300 px are coverage). Not an
  atom of this leaf: matching it means GDI text in the raster backend or a gamma/contrast table for Skia (decision for the
  owner of the text stack, census L13/L14).
- GC text placement (CLabel, CTabFolder tab text): `M3TextDrawing` advances lines by Skia metrics (leaf 6 deferred list).
- The tree expander glyph is one pixel high at item heights 18, 23 and 26 (stock ink top 6, 6, 6, 8, 10 for 18, 20, 21,
  23, 26 px; drawn 5, 6, 6, 7, 9); no native rule was established, so nothing was changed.
- Canvas tab (356 / 343 px): the example's own drawing through the GC (arc and oval anti-aliasing), leaf 6 GF43 family.

## Gates (2026-10-31; one JVM at a time; output under `H:/m3-scratch/fidelity/`)

- Natives: `m3gc_natives.sh C:/m3-superset/swt-fidelity H:/m3-scratch/fidelity/natives`: **ok=6 bad=0** (4975r19 osversion
  648dda89.., win32 9f758f09.., gdip ad0b938c.., awt e0d87eeb.., wgl 5a6069aa.., WebView2Loader 184574b9..; receipt
  `natives.receipt.tsv`); every run below loaded this set (the shared `H:/m3-scratch/gc-wiring/natives` holds r10 renamed r19:
  `--verify` 5 x OID_MISMATCH; untouched).
- `compile_m3gc.sh` (positive counts): **950 sources, 1441 classes, 0 errors** (base 945 / 1436).
- `m3gc_compile_platform.sh <wt> <os> H:/m3-scratch/fidelity/platform`: **win32 911 / gtk 700 / cocoa 839 sources, 0 errors**
  (1396 / 1043 / 1152 classes; base 909 / 698 / 837: the two new common files).
- `test_m3gc.sh` (natives verified by oid, positive counts): **156 found, 156 passed** in suite order and with `M3_SCAN=1`
  (classpath-scan order).
- `test_m3fidelity.sh headless`: **28 / 28** (RowFidelity 9, M3GdiEllipsis 1 = 567 cuts, RowRenderer and RowPaintKey 18
  unchanged); `display viewport`, `original`, `m3`, `gpu`: **7 / 7** each (M3ModeTruth 4, ComboContentSize 2, M3CanvasMirroring 1).
- Theme and language (`docs/m3/theme-language/tools/test_theme_language.sh`, this worktree's classes): **headless 30 / 30;
  display 27 / 27 in each of viewport, original, custom, m3, gpu**.
- Real app (`run_mode.sh`, ui_lock, hidden desktop, heartbeat every 5 minutes): **31 / 31 PASS, 0 EXCEPTION** in stock, m3
  and gpu, base and final; m3 with an empty `skija.library.path` 31 / 31 PASS (before: 4 EXCEPTION rows).
- Method length: the new classes 13 methods, 0 over 15 lines; the edited planes keep the base's count (LF-12).
- Final head (all atoms): m3 and gpu reruns give the same per-tab numbers above the strip as the table (`H:/m3-scratch/fidelity/head/out`).

## Findings handed to this leaf after the run, and where they stand

- Eclipse IDE on M3 (packaging leaf, PR #302, `H:/m3-scratch/l18/ide/out/m3/package_explorer_active.png`): the Package
  Explorer Tree (a drawn plane) shows its label as black text on a white box and no item image in the dark theme. That tree
  is drawn by JFace owner draw (StyledCellLabelProvider: EraseItem clears FOREGROUND, PaintItem draws the image with
  `gc.drawImage` at `getImageBounds` and the label with a `TextLayout`), not by the plane's own cell content, which this leaf
  fixed (ROW_IMAGE_BOUNDS, BACKGROUND_PARITY cover the plane's images and the canvases' backgrounds). The white box is the
  TextLayout-through-GC defect the coordinator scoped to the text leaf (census L13/L14); the missing image is in the
  owner-draw PaintItem path (`DrawnOwnerDraw`) and was not reproduced here: NOT_RUN (the IDE launcher was not run by this leaf).
- `Shell.print` draws two or more double-buffered canvases at (0, 0) in every mode, original included (combination matrix,
  `Control.java:2584`): a stock print path outside the M3 frame; noted, not changed.

## NOT_RUN

- gtk and cocoa: compiled only (the M3 tree, the planes and their hooks are win32).
- Zoom other than 100 % (this machine runs at 100 %); RTL scroll (`Canvas.scroll` of a mirrored M3 canvas) is written but
  not exercised; a background image under a mirrored M3 canvas is tiled from the physical origin (theme and colour are
  uniform, so the probes cannot see it).
- The render-thread path of the planes (`-Dswt.drawn.renderThread=true`) keeps the library's ellipsis cut (no platform
  font there).
- `M3LanguageSwitcher.sync` has the same layout pattern as the theme switcher had; no failing case was measured, so it is
  unchanged.
