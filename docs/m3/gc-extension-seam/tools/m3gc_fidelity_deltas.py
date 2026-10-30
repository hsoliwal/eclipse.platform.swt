# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 6 (GC fidelity of M3ExternalGC against the stock win32 GC): the mechanical multi-site deltas as exact byte-preserving
text replacements with an expected occurrence count, plus the record of the semantic edits made by Read + Edit in the owner
files, the decisions and every correction of my own script, tests and code, all appended to LEAF6_RECIPE_MEMORY.tsv with ids
L6-xx (the six columns of RECIPE_MEMORY.tsv; merged into it after leaf 5 lands).

Idempotent: a delta whose `new` text is present and `old` text absent is DONE; a record-only row (apply False: old and new are
descriptions) is appended once. Usage: python -I m3gc_fidelity_deltas.py <worktree>
"""
import sys
from pathlib import Path

GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
WIDGETS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
GRAPHICS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/"
TESTS = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/"
MEMORY = "docs/m3/gc-extension-seam/LEAF6_RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

# (id, file, old, new, evidence, reason, apply, count)  -- apply False: record only; count: occurrences of `old` replaced
DELTAS = [
    ("L6-01", GC + "M3PaintManager.java", "paint.setAntiAlias(gc.getAntialias() != SWT.OFF);",
     "paint.setAntiAlias(gc.getAntialias() == SWT.ON);",
     "Test_org_eclipse_swt_m3gc_HintState.testDefaultAntialiasDrawsAliased: expected <false> but was <true>",
     "HINT_STATE (M3-GC-HINT v1), GF06: the fresh GC answers SWT.DEFAULT (stock: no GDI+ graphics), and DEFAULT strokes and fills "
     "aliased as GDI does (and GDI+ SmoothingModeDefault); only ON antialiases, as fillPath already tested (performDraw, performDrawFilled)",
     True, 2),
    ("L6-02", GC + "M3TextDrawing.java",
     "final boolean antiAlias = gc.getAntialias() != SWT.OFF || gc.getTextAntialias() != SWT.OFF;",
     "final boolean antiAlias = gc.getTextAntialias() != SWT.OFF;",
     "Test_org_eclipse_swt_m3gc_HintState.testTextAntialiasAloneDecidesText: expected <false> but was <true>",
     "HINT_STATE, GF06: text smoothing is the text antialias alone (stock getTextAntialias javadoc: 'only for text drawing'; GDI+ "
     "TextRenderingHint); DEFAULT keeps the system smoothing (antialiased, the adopted text tests pin it); the cached and uncached paths",
     True, 2),
    ("L6-03", GC + "M3ExternalGC.java", "antialias 0 (= SWT.OFF), textAntiAlias SWT.ON, getInterpolation decoded from the sampling, "
     "setInterpolation(DEFAULT) = Mitchell, setTextAntialias unvalidated",
     "antialias and textAntiAlias start at SWT.DEFAULT; int interpolation (SWT.DEFAULT) stored by setInterpolation and answered by "
     "getInterpolation; DEFAULT and NONE sample nearest; setTextAntialias rejects values other than DEFAULT/OFF/ON (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_HintState: 6 failures before (antialias expected <-1> but was <0>; interpolation 1 read back 2; "
     "no IllegalArgumentException; DEFAULT sampled Mitchell), 6/6 after",
     "HINT_STATE, GF06; stock W32G/GC.java getAntialias/getTextAntialias/getInterpolation (DEFAULT without GDI+), setTextAntialias "
     "(ERROR_INVALID_ARGUMENT); departs from I31 SkiaGC (fields at :70/:100, getInterpolation at :851)", False, 0),
    ("L6-04", GC + "M3ExternalGC.java", "setLineAttributes wrote width 1 into the caller's object for width <= 0 and stored "
     "autoScaleUp(width) (pixels) in lineWidth (points); getLineAttributes divided lineWidth by the zoom",
     "setLineAttributes keeps the width in points in a local (<= 0 -> 1), the caller's object untouched; getLineAttributes = new "
     "LineAttributes(lineWidth, cap, join, style, autoScaleDown(lineDashes), dashOffset, miterLimit), getLineAttributesInPixels "
     "removed (no other caller) (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_StrokeScale: getLineWidth expected <2> but was <4>; width read back 1.5 for 3; caller width 0 "
     "became 1.0",
     "STROKE_SCALE (M3-GC-STROKE v2), GF14; stock W32G/GC.java setLineAttributes (SetLineAttributesOperation copies; point and "
     "pixel attributes apart), getLineAttributes; same defect in I31 SkiaGC.setLineAttributes (:1028)", False, 0),
    ("L6-05", GC + "M3PaintManager.java", "LINE_CUSTOM: even arrays x scaled line width, odd arrays doubled x autoScaleUp(1)",
     "LINE_CUSTOM: PathEffect.makeDash(doubled(lineDashes), phase): absolute pixel lengths, every array doubled (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_StrokeScale: {4,2} width 3 at 200 % ran [48, 24] instead of [8, 4]; {3} ran 12 instead of 6",
     "STROKE_SCALE, GF11 = PRE_EXISTING P05 (leaf 5 proposal); stock W32G/GC.java getPen: GDI+ dashes / width with the array "
     "doubled, GDI PS_USERSTYLE absolute; same in I31 SkiaPaintManager.createPathEffect; the predefined styles keep the "
     "width-relative tables (GF12, approximated, not in this leaf)", False, 0),
    ("L6-06", TESTS + "Test_org_eclipse_swt_m3gc_StrokeScale.java", "asserted runs[1] as the dash and runs[2] as the gap",
     "runs[] = on, off, on, ...: runs[2] is the first whole dash, runs[1] the first gap (and the stray List8x4 holder removed)",
     "own review before the first run (no javac or JUnit line)",
     "correction of my own test: the run-length helper starts at the first lit pixel, so index 0 is a dash that may be cut by "
     "the line start", False, 0),
    ("L6-07", GC + "M3GcLayer.java", "(new)",
     "one save layer above the frame for the GC transform and the GC clip: setTransform = setMatrix(frame x T) on the layer, "
     "setClip = restoreToCount(base) + save + setMatrix + clip once, close = restoreToCount(base); localClip maps the clip's "
     "device bounds within the frame back through the inverse matrix (Read + Write)",
     "Test_org_eclipse_swt_m3gc_ClipState 8/9 failing before (overlay run tests-before-clip), 9/9 after; "
     "Test_org_eclipse_swt_m3gc_TransformState 3/3 failing before, 3/3 after",
     "CLIP_STATE (M3-GC-CLIP v1) + TRANSFORM_STATE (M3-GC-TRANSFORM v1), GF02, GF16; stock W32G/GC.java setClipping replaces "
     "the clip regardless of setTransform, getClippingInPixels = GDI+ visible clip bounds (world coordinates) or GetClipBox; "
     "departs from I31 SkiaGC (save per setTransform, restore per setClipping on one stack)", False, 0),
    ("L6-08", GC + "M3GcLayer.java", "setClip computed the clip's device bounds with matrix() before openLayer captured the "
     "frame matrix; localClip used base x T while the layer was closed",
     "device bounds computed after openLayer; a closed layer reads canvas.getLocalToDevice()",
     "own review before the first compile",
     "correction of my own code: base is captured when the layer opens, so a bound computed earlier used a stale or identity "
     "frame matrix", False, 0),
    ("L6-09", TESTS + "StateCanvasDouble.java", "save() pushed a copy of the parent level's clips and cleared it; "
     "restoreToCount popped in a while loop",
     "save() pushes an empty level (clips() concatenates the levels); restoreToCount pops through IntStream.range",
     "own review before the first compile",
     "correction of my own test double: a level holds only the clips applied in it, otherwise clips() counted a parent clip "
     "twice; no loops in new code (M3-SCALE hard limit)", False, 0),
    ("L6-10", GC + "M3RegionCalculator.java", "RegionLog replay into a Skija Region (translate in points, polygons inside "
     "IRect(0, 0, maxX, maxY), live argument objects, empty for an unlogged region; AutoCloseable instance)",
     "static rectangles(Region, zoom) = GetRegionData of Region.win32_getHandle(region, zoom) copied once; bounds(int[]); "
     "clip(IM3Canvas, int[]) through Region.setRects + clipRegion (Write, whole file)",
     "Test_org_eclipse_swt_m3gc_ClipState.testRegionReplayKeepsTranslateAndNegativeAreaAtZoom: expected [(-10,-10,50,50)] but "
     "was [(5,5,...)]; testRegionFilledByTheStockGcReplays: expected (3,4,5,6) but was null; testRegionClipIsACopy: Argument "
     "not valid",
     "CLIP_STATE, GF03, GF18; stock W32G/GC.java setClipping(Region) copies win32_getHandle(region, zoom) into its own HRGN; "
     "M3 no longer reads RegionLog (the common RegionLog keeps growing for I31's reader: next leaf); departs from I31 "
     "SkiaRegionCalculator", False, 0),
    ("L6-11", GC + "M3ExternalGC.java", "isClipSet/currentClipBounds/currentClipRegion/currentTransform/initialSaveCount; "
     "getClipping = frame in pixels unless a region was set (then null); getClipping(Region) intersected with the caller's "
     "region; fromM3Matrix scaled m21 and kept dx in pixels",
     "M3GcLayer layer; setClipping(Rectangle|Path|Region) and setTransform through the layer; getClipping = toPoints("
     "layer.localClip(frame())); getClipping(Region) = the region clip's rectangles within the frame in points, else the clip "
     "box; getTransform checks a disposed transform; M3TransformConverter.fromM3Matrix(m[0], m[3], m[1], m[4], down(m[2]), "
     "down(m[5])) (Read + Edit, python replace with count 1)",
     "ClipState and TransformState as L6-07",
     "CLIP_STATE + TRANSFORM_STATE, GF02, GF03, GF04 (path clip answered by its bounds), GF15, GF16; IM3Canvas gains "
     "default getLocalToDevice (M3CanvasAdapter: Canvas.getLocalToDeviceAsMatrix33)", False, 0),
    ("L6-12", GC + "M3ExternalGC.java", "drawString -> drawText(string, x, y, isTransparent) (DRAW_DELIMITER | DRAW_TAB)",
     "drawString -> M3TextDrawing.drawText(this, new String[] { string }, isTransparent ? DRAW_TRANSPARENT : NONE, x, y), "
     "the culling shared with executeTextDraw (isCulled); M3Resources.replaceMnemonics = replaceAll(\"&(&?)\", \"$1\") (Read + "
     "Edit through the scratch script H:/m3-scratch/gc-leaf6/edits/text_atoms.py)",
     "Test_org_eclipse_swt_m3gc_TextDrawFlags: drawString(\"a\\nb\") drew [a, b], drawString(\"a\\tb\") drew [a    b], "
     "getTextSplits(\"a&&b\", DRAW_MNEMONIC) gave [ab]: 3/4 failing before, 4/4 after",
     "TEXT_DRAW_FLAGS (M3-GC-TEXT-FLAGS v1), GF29 (and GF28's flag part, GF30's '&&'); stock W32G/GC.java drawString "
     "(ExtTextOut, verbatim) and DrawText DT_NOPREFIX; same in I31 SkiaGC.drawString", False, 0),
    ("L6-13", "H:/m3-scratch/gc-leaf6/edits/split_hunks.py", "sys.stdout.write(text) of the selected hunks",
     "sys.stdout.buffer.write(bytes): stdin and stdout as UTF-8 bytes",
     "git apply --cached: patch failed: M3ExternalGC.java:272, M3Resources.java:481 (patch does not apply)",
     "correction of my own scratch tool (staging one atom's hunks of a shared owner file): Python text mode wrote CRLF into the "
     "patch of LF sources (the known Windows newline trap, MEMORY feedback-python-newlines-windows)", False, 0),
    ("L6-14", "docs/m3/gc-extension-seam/tools/m3gc_fidelity_deltas.py (Bash tool)", "one Bash call carrying a ~9 KB "
     "python heredoc of the text-atom replacements",
     "the replacements in a file (H:/m3-scratch/gc-leaf6/edits/text_atoms.py, Write tool), run with python -I",
     "bash: -c: line 152: unexpected EOF while looking for matching `''",
     "correction of my own procedure: the Bash tool breaks long heredoc commands (MEMORY feedback-bash-tool-command-length: "
     "keep commands under ~5 KB, long text via Write)", False, 0),
    ("L6-15", GC + "M3ExternalFontMetrics.java", "ascent = ceil(|ascent| - line gap) scaled; descent, height = ceil of the "
     "scaled Skia values; leading = autoScaleDownToInt(height - ascent - descent) (scaled twice)",
     "ascentPixels = ceil |ascent|, descentPixels = ceil descent, internal leading = ascent + descent - round(em size); "
     "getAscent = down(ascent - internal), getDescent = down(descent), getHeight = down(ascent + descent), getLeading = "
     "height - ascent - descent; the em size is a constructor argument (M3ExternalGC passes font.getSize()) (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_TextMetrics.testAscentExcludesTheInternalLeading: expected <12> but was <14>",
     "TEXT_METRICS (M3-GC-TEXT-METRICS v1), GF36; stock win32 FontMetrics (tmAscent - tmInternalLeading, tmDescent, tmHeight, "
     "derived leading); GDI's VDMX-hinted TEXTMETRIC differs by 1-2 px at small sizes (H:/m3-scratch/gc-leaf6/exp/"
     "fontprobe.tsv), so the M3 values follow M3's own text placement; same defect in I31 SkiaFontMetrics", False, 0),
    ("L6-16", GC + "M3Resources.java", "textExtent(text, flags) = ((int) measureTextWidth(replaceMnemonics(text)), (int) "
     "Skia height); stringExtent = textExtent(string)",
     "textExtent = widest of getTextSplits(text, flags) rounded up x (lines x lineHeight), lineHeight = ceil(ceil |ascent| + "
     "ceil descent + Skia line gap) as M3TextDrawing advances; stringExtent(string) as given (IM3Resources default for the "
     "doubles) (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_TextMetrics: two lines 17 high (expected 34), '&' never measured, tab not expanded, width 22 "
     "for 22.21: 5/6 failing before, 6/6 after",
     "TEXT_METRICS, GF28; stock W32G/GC.java textExtent (DT_CALCRECT with DT_SINGLELINE/DT_EXPANDTABS/DT_NOPREFIX per flag) and "
     "stringExtent (GetTextExtentPoint32); the extent follows M3's draw (its line advance includes Skia's line gap), so an "
     "extent and the drawn text agree; same defect in I31 SkiaResources.textExtent", False, 0),
    ("L6-17", GC + "DpiScalerUtil.java", "scaleUp = (v * zoom + 50) / 100; scaleDown = (v * 100 + zoom / 2) / zoom",
     "Math.floorDiv for both (round half up for negative values, = Math.round of the scaled value) (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_GcContract.testDrawBackgroundTilesTheBackgroundImage: tile at -2.0, expected -3.0; "
     "Test_org_eclipse_swt_m3gc_ScaleRounding: scale up -40 at 100 expected <-40> but was <-39>",
     "census GF41 (APPROXIMATED, outside the leaf's BUG/STUBBED/MISSING list) taken in because the GF01 tile test exposed it: "
     "every negative coordinate drew one pixel off at 100 %; stock DPIUtil.pointToPixel = Math.round; same in I31 "
     "DpiScalerUtil", False, 0),
    ("L6-18", WIDGETS + "Composite.java", "drawBackgroundInPixels read gc.getGCData().planeDraw and gc.handle for any GC",
     "a GCExtension fills through the GC: Composite/Canvas.drawBackground (points, no pixel round trip) and "
     "drawBackgroundInPixels (pixels -> points) -> drawBackgroundThroughGC (alpha 255 and no background pattern while "
     "filling, both put back) -> paintBackgroundThroughGC (background image tiled with the offset, else the background control's "
     "pixel, else the GC background) (Read + Edit; Canvas.java, Control.java printThroughGC/printInto, Shell.print branch)",
     "Test_org_eclipse_swt_m3gc_GcContract: 5/5 failing before (NullPointerException: Cannot read field \"planeDraw\" because "
     "\"<local8>\" is null; \"gdipGraphics\" ... getGCData() is null), 5/5 after",
     "ARG_CONTRACT (M3-GC-CONTRACT v1), GF01; pattern of leaf 5's reverted L5-44/L5-45 (measured StyledText PASS), plus: no "
     "pixel round trip for the point entries (a 3-point offset came back as 2 at 150 %), FillRect semantics (GC alpha and "
     "background pattern ignored), Control/Shell.print through an image; M3ExternalGC.getGCData answers its own GCData "
     "(planeDraw -1, gdipGraphics 0, nativeZoom, style, alpha); I31 SkiaGC.getGCData answers null (same defect)", False, 0),
    ("L6-19", WIDGETS + "Composite.java", "drawBackgroundThroughGC (gc, x, y, width, height, offsetX, offsetY) in pixels, "
     "converted to points inside",
     "drawBackgroundThroughGC (gc, Rectangle area, Point offset) in points; the public point entries call it directly",
     "testDrawBackgroundTilesTheBackgroundImage: expected <-3.0> but was <-2.0> (the pixel round trip ruled out: the same -2 "
     "remained, the cause was GF41, L6-17)",
     "correction of my own code: a points -> pixels -> points round trip of the offset is lossy at fractional zoom; the "
     "entries in points no longer make it", False, 0),
    ("L6-20", GC + "M3ExternalGC.java", "drawImage: a null image logged once and ignored, no size or sign checks (a zero source "
     "size gave Integer.MAX_VALUE as the image zoom); drawPoint through performDrawFilled with the foreground colour at "
     "Color alpha",
     "drawImage x3 in the stock order: zero size returns, negative sizes or source coordinates ERROR_INVALID_ARGUMENT, then "
     "checkImage (null ERROR_NULL_ARGUMENT, disposed ERROR_INVALID_ARGUMENT); drawPoint through the new "
     "M3PaintManager.performFillForeground (foreground at the GC alpha, or the foreground pattern) (scratch script "
     "H:/m3-scratch/gc-leaf6/edits/contract_atoms.py, job arg)",
     "Test_org_eclipse_swt_m3gc_ArgContract: 6/6 failing before (nothing thrown for null and negatives; SWTException for a "
     "disposed image; Argument not valid for a zero size; alpha 255 instead of 128; the background pattern's shader on the "
     "point), 6/6 after",
     "ARG_CONTRACT (M3-GC-CONTRACT v1), GF24, GF46; stock W32G/GC.java drawImage (1231/1321/1372) and drawPointInPixels "
     "(GDI+ FillRectangle with getFgBrush); same in I31 SkiaGC.drawImage/drawPoint", False, 0),
    ("L6-21", "H:/m3-scratch/gc-leaf6 (gate of a staged atom)", "git stash push --keep-index --include-untracked, gates, git "
     "stash pop",
     "stage_gate.sh: the index versions of the changed bundle files as M3_OVERLAY of compile_m3gc.sh and the index's m3gc test "
     "directory exported to a scratch root for test_m3gc.sh; the working tree is never touched",
     "git stash pop: CONFLICT in AllM3GcTests.java (index and working copy both changed), every other change left staged",
     "correction of my own procedure: the suite file is staged partially per atom, so a keep-index stash conflicts on pop and "
     "loses the staged selection; recovered by taking the stash's file, git reset (mixed) and restaging (the working tree "
     "compared byte-equal to the stash first)", False, 0),
    ("L6-22", GRAPHICS + "Path.java", "PathHandle.containsInPixels: gc.initGdip(); gc.checkGC(...); GetPolyFillMode(gc.handle)",
     "a GCExtension takes containsThroughGC: GraphicsPath_SetFillMode by gc.getFillRule(), IsVisible(handle, x, y, 0), and for an "
     "outline a temporary pen (SolidBrush black, width = the GC line width in pixels, cap and join of the GC) with "
     "IsOutlineVisible(..., 0) (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_PathHitTest: 2/2 failing before (NullPointerException: Cannot read field \"device\" because "
     "\"this.data\" is null), 2/2 after",
     "PATH_HIT_TEST (M3-GC-PATH-HIT v1), GF51; stock W32G/Path.java contains (GDI+ IsVisible/IsOutlineVisible with the GC's pen "
     "and fill mode; the GC transform is ignored there too: stock TODO); I31 has the same NPE", False, 0),
]


def tsv(value):
    return value.replace("\t", "\\t").replace("\n", "\\n")


def apply_delta(wt, delta_id, rel, old, new, count):
    path = wt / rel
    text = path.read_bytes().decode("utf-8")
    if new in text and old not in text:
        print(f"{delta_id} DONE {rel}")
        return True
    found = text.count(old)
    if found != count:
        print(f"{delta_id} ANCHOR x{found} (expected {count}) {rel}")
        return False
    path.write_bytes(text.replace(old, new).encode("utf-8"))
    print(f"{delta_id} APPLIED x{count} {rel}")
    return True


def main(worktree):
    wt = Path(worktree)
    memory = wt / MEMORY
    existing = memory.read_text(encoding="utf-8") if memory.exists() else HEADER
    if not existing.startswith(HEADER):
        print("LEAF6_RECIPE_MEMORY.tsv: unexpected header")
        return 1
    known = {line.split("\t", 1)[0] for line in existing.splitlines()[1:] if line}
    rows = []
    for delta_id, rel, old, new, evidence, reason, apply, count in DELTAS:
        if apply and not apply_delta(wt, delta_id, rel, old, new, count):
            return 1
        if delta_id not in known:
            rows.append("\t".join([delta_id, rel, tsv(old), tsv(new), evidence, reason]) + "\n")
    if rows:
        if not existing.endswith("\n"):
            existing += "\n"
        memory.write_bytes((existing + "".join(rows)).encode("utf-8"))
        print(f"LEAF6_RECIPE_MEMORY.tsv: +{len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "C:/m3-superset/swt-gcfix"))
