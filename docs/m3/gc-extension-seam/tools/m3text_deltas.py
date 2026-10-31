"""Text leaf L13/L14 mechanical deltas (CPU, exact-match, fail-closed). Each atom is a list of (file, old, new) replacements
applied only when `old` occurs exactly once; a second run is a no-op (fixed point: `new` present, `old` absent).
Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others. SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
Usage: python -I m3text_deltas.py <worktree> <ATOM> [replacement-text-dir, default tools/m3text_bodies]
"""
import re
import sys
import os
if __name__ == "__main__":  # M3_GRAPHICS_GUARD v1: NOT_APPLICABLE on jdk.m3.graphics trees
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from m3_graphics_guard import exit_if_m3_graphics
    exit_if_m3_graphics(sys.argv)
from pathlib import Path

RM = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
W32G = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/"


def text_layout_draw_span(src):
    """the stock-delegating textLayoutDraw of M3ExternalGC: @Override line through the method's closing brace"""
    m = re.search(r"\t@Override\n\tpublic void textLayoutDraw\(.*?\n\t}\n", src, re.S)
    return m.group(0) if m else None


def atom_fallback(wt, extra):
    f = Path(wt, RM, "M3ExternalGC.java")
    src = f.read_text(encoding="utf-8")
    body = Path(extra, "l13_fallback_body.txt").read_text(encoding="utf-8")
    if body in src:
        return [(f, "fixed point")]
    old = text_layout_draw_span(src)
    assert old and src.count(old) == 1 and "new Image(device, rectangle.width" in old, "textLayoutDraw anchor"
    src = src.replace(old, body)
    imp = "import io.github.humbleui.skija.ClipMode;\n"
    assert src.count(imp) == 1
    src = src.replace(imp, "import io.github.humbleui.skija.ClipMode;\nimport io.github.humbleui.skija.ColorAlphaType;\n"
                      "import io.github.humbleui.skija.ColorType;\n", 1)
    imp2 = "import io.github.humbleui.skija.GradientStyle;\n"
    assert src.count(imp2) == 1
    src = src.replace(imp2, imp2 + "import io.github.humbleui.skija.ImageInfo;\n", 1)
    f.write_bytes(src.encode("utf-8"))
    return [(f, "applied")]


COMMON = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/"


def replace_once(f, pairs):
    """apply (old, new) pairs to one file; each old exactly once; all-new already present = fixed point"""
    src = f.read_text(encoding="utf-8")
    if all(new in src for _, new in pairs):
        return (f, "fixed point")
    for old, new in pairs:
        assert src.count(old) == 1, f"{f.name}: anchor count {src.count(old)}: {old[:70]!r}"
        src = src.replace(old, new, 1)
    f.write_bytes(src.encode("utf-8"))
    return (f, "applied")


def atom_sink(wt, extra):
    """GLYPH_RUN_SINK v1: TextLayout's recorder emits to an M3RunSink; op stream adapter; GCExtension/IExternalGC hand one out"""
    tl = Path(wt, W32G, "TextLayout.java")
    src = tl.read_text(encoding="utf-8")
    body = Path(extra, "l13_record_runs.txt").read_text(encoding="utf-8")
    out = []
    if body not in src:
        start = src.index("/**\n * Retained planes: records the layout as GPU ops. Run backgrounds and the\n")
        end_marker = "static final RECT RECORD_RECT = new RECT();\n"
        end = src.index(end_marker, start) + len(end_marker)
        assert src.count(end_marker) == 1
        src = src[:start] + body + src[end:]
        tl.write_bytes(src.encode("utf-8"))
        out.append((tl, "applied recorder"))
    dispatch_old = ("\tif(gc instanceof GCExtension gcext) {\n\t\tgcext.textLayoutDraw(this, gc,  xInPoints,  yInPoints,  selectionStart,"
                    "  selectionEnd,  selectionForeground,  selectionBackground,  flags);\n\t\treturn;\n\t}\n")
    dispatch_new = ("\tif(gc instanceof GCExtension gcext) {\n"
                    "\t\t// L13 GLYPH_RUN_SINK v1: shaped runs to the M3 GC's sink; what it cannot take goes to its fallback (counted)\n"
                    "\t\tM3RunSink sink = gcext.runSink();\n\t\tcomputeRuns(null);\n"
                    "\t\tif (sink == null || !recordRuns(sink, xInPoints, yInPoints, selectionStart, selectionEnd, selectionForeground, selectionBackground, flags)) {\n"
                    "\t\t\tgcext.textLayoutDraw(this, gc,  xInPoints,  yInPoints,  selectionStart,  selectionEnd,  selectionForeground,  selectionBackground,  flags);\n"
                    "\t\t}\n\t\treturn;\n\t}\n")
    out.append(replace_once(tl, [(dispatch_old, dispatch_new),
                                 ("import org.eclipse.swt.internal.*;\n", "import org.eclipse.swt.internal.*;\nimport org.eclipse.swt.internal.canvasext.*;\n")]))
    gp = Path(wt, W32G, "GCPlanes.java")
    gsrc = gp.read_text(encoding="utf-8")
    sink_body = Path(extra, "l13_opstream_sink.txt").read_text(encoding="utf-8")
    if sink_body not in gsrc:
        start = gsrc.index("\t/* Scratch for native glyph arrays; UI-thread confined like every GC. */\n")
        gsrc = gsrc[:start] + sink_body
        gsrc = gsrc.replace("import org.eclipse.swt.internal.*;\n", "import org.eclipse.swt.internal.*;\nimport org.eclipse.swt.internal.canvasext.*;\n", 1)
        gp.write_bytes(gsrc.encode("utf-8"))
        out.append((gp, "applied op stream sink"))
    out.append(replace_once(Path(wt, COMMON, "internal/canvasext/IExternalGC.java"), [(
        "\tvoid textLayoutDraw(TextLayout textLayout, GC gc, int xInPoints, int yInPoints, int selectionStart,\n"
        "\t\t\tint selectionEnd, Color selectionForeground, Color selectionBackground, int flags);\n",
        "\tvoid textLayoutDraw(TextLayout textLayout, GC gc, int xInPoints, int yInPoints, int selectionStart,\n"
        "\t\t\tint selectionEnd, Color selectionForeground, Color selectionBackground, int flags);\n\n"
        "\t/** L13 GLYPH_RUN_SINK v1: the sink TextLayout records shaped runs to, or null for textLayoutDraw only */\n"
        "\tdefault M3RunSink runSink() {\n\t\treturn null;\n\t}\n")]))
    out.append(replace_once(Path(wt, COMMON, "graphics/GCExtension.java"), [(
        "\t\te.textLayoutDraw(textLayout,gc,xInPoints,yInPoints,selectionStart, selectionEnd, selectionForeground, selectionBackground, flags);\n\t}\n",
        "\t\te.textLayoutDraw(textLayout,gc,xInPoints,yInPoints,selectionStart, selectionEnd, selectionForeground, selectionBackground, flags);\n\t}\n\n"
        "\t/**\n\t * L13 GLYPH_RUN_SINK v1: the run sink of the external GC (null: TextLayout uses {@link #textLayoutDraw}).\n"
        "\t *\n\t * @noreference This method is not intended to be referenced by clients.\n\t */\n"
        "\tpublic M3RunSink runSink() {\n\t\treturn e.runSink();\n\t}\n")]))
    return out


def atom_draw(wt, extra):
    """GLYPH_RUN_DRAW v1: IM3Canvas.drawTextBlob (adapter delegates), M3ExternalGC.runSink() = M3GlyphRuns"""
    out = [replace_once(Path(wt, RM, "IM3Canvas.java"), [
        ("\tvoid clipRegion(Region m3Region);\n",
         "\tvoid clipRegion(Region m3Region);\n\n\t/** L13 GLYPH_RUN_DRAW v1: one positioned text blob (doubles record or ignore it) */\n"
         "\tdefault void drawTextBlob(TextBlob blob, float x, float y, Paint paint) {\n\t}\n"),
        ("import io.github.humbleui.skija.Region;\n", "import io.github.humbleui.skija.Region;\nimport io.github.humbleui.skija.TextBlob;\n")])]
    out.append(replace_once(Path(wt, RM, "M3CanvasAdapter.java"), [
        ("\tpublic void clipRegion(Region m3Region) {\n\t\tdelegate.clipRegion(m3Region);\n\t}\n",
         "\tpublic void clipRegion(Region m3Region) {\n\t\tdelegate.clipRegion(m3Region);\n\t}\n\n\t@Override\n"
         "\tpublic void drawTextBlob(TextBlob blob, float x, float y, Paint paint) {\n\t\tdelegate.drawTextBlob(blob, x, y, paint);\n\t}\n"),
        ("import io.github.humbleui.skija.SamplingMode;\n", "import io.github.humbleui.skija.SamplingMode;\nimport io.github.humbleui.skija.TextBlob;\n")]))
    out.append(replace_once(Path(wt, RM, "M3ExternalGC.java"), [
        ("\t/* L13 TEXT_FALLBACK_SAFE v1: a premultiplied BGRA raster composited once with the GC alpha (not squared) */\n",
         "\t/* L13 GLYPH_RUN_DRAW v1: TextLayout records its shaped runs here (M3GlyphRuns); the rest reaches textLayoutDraw */\n"
         "\t@Override\n\tpublic org.eclipse.swt.internal.canvasext.M3RunSink runSink() {\n\t\treturn runSink;\n\t}\n\n"
         "\t/* L13 TEXT_FALLBACK_SAFE v1: a premultiplied BGRA raster composited once with the GC alpha (not squared) */\n"),
        ("\tprivate final DpiScalerUtil dpiScalerUtil;\n",
         "\tprivate final DpiScalerUtil dpiScalerUtil;\n\t// L13 GLYPH_RUN_DRAW v1: this GC as TextLayout's run sink\n"
         "\tprivate final M3GlyphRuns runSink = new M3GlyphRuns(this);\n")]))
    return out


def atom_surrogate_tabs(wt, extra):
    """L14 SURROGATE_SAFE v1 + TAB_STOPS v1: GDI advance/black-box widths, code-point-safe splits, GDI tab expansion"""
    metrics = Path(extra, "l14_gdi_metrics.txt").read_text(encoding="utf-8")
    out = [replace_once(Path(wt, RM, "M3GdiText.java"), [
        ("\tprivate static Point textExtentPoint(long hdc, String text) {\n", metrics),
        ("import org.eclipse.swt.internal.win32.SIZE;\n", "import org.eclipse.swt.internal.win32.SIZE;\nimport org.eclipse.swt.internal.win32.TEXTMETRIC;\n")])]
    gc = Path(wt, RM, "M3ExternalGC.java")
    src = gc.read_text(encoding="utf-8")
    m = re.search(r"\t@Override\n\tpublic int getAdvanceWidth\(char ch\) \{\n.*?\n\t}\n", src, re.S)
    if m and "f.measureTextWidth(String.valueOf(ch), paint)" in m.group(0):
        src = src.replace(m.group(0), "\t@Override\n\tpublic int getAdvanceWidth(char ch) {\n"
                          "\t\t// L14 SURROGATE_SAFE v1: stock GetCharWidth; a lone surrogate never reaches Skia's measureText (#109)\n"
                          "\t\treturn M3GdiText.advanceWidth(getFont(), getScaler().getNativeZoom(), ch);\n\t}\n", 1)
        gc.write_bytes(src.encode("utf-8"))
    out.append(replace_once(gc, [
        ("\tpublic int getCharWidth(char ch) {\n\t\treturn getAdvanceWidth(ch);\n\t}\n",
         "\tpublic int getCharWidth(char ch) {\n\t\t// L14 TAB_STOPS v1: stock black-box width (GetCharABCWidths abcB), not the advance (GF37)\n"
         "\t\treturn M3GdiText.charWidth(getFont(), getScaler().getNativeZoom(), ch);\n\t}\n")]))
    out.append(replace_once(Path(wt, RM, "M3Resources.java"), [
        ("\t\tString workInputText = inputText;\n",
         "\t\t// L14 SURROGATE_SAFE v1: no lone surrogate reaches Skia (U+FFFD, as GDI shows it)\n"
         "\t\tString workInputText = M3CodePoints.safe(inputText);\n"),
        ("\t\t\t\tworkInputText = expandTabs(workInputText, 0);\n",
         "\t\t\t\t// L14 TAB_STOPS v1: DT_EXPANDTABS stops, segments measured with GDI (no per-character truncation, GF31)\n"
         "\t\t\t\tworkInputText = M3CodePoints.expandTabs(workInputText, getFont(), getScaler().getNativeZoom());\n")]))
    res = Path(wt, RM, "M3Resources.java")
    rsrc = res.read_text(encoding="utf-8")
    start = rsrc.find("\t/**\n\t * Expands tab characters (\\t) in the text to position-dependent spaces")
    if start >= 0:
        end = rsrc.index("\t\treturn result.toString();\n\t}\n", start) + len("\t\treturn result.toString();\n\t}\n")
        rsrc = rsrc[:start] + rsrc[end:].lstrip("\n")
        res.write_bytes(rsrc.encode("utf-8"))
        out.append((res, "removed per-char expandTabs (dead)"))
    return out


def atom_shaper_raster(wt, extra):
    """L14 ONE_TEXT_SHAPER v1 (raster): drawText/drawString try M3GdiTextDraw (stock GDI over the surface pixels) first"""
    return [replace_once(Path(wt, RM, "M3ExternalGC.java"), [
        ("\t\tif (!isCulled(x, y)) {\n\t\t\tM3TextDrawing.drawText(this, new String[] { string }, isTransparent ? SWT.DRAW_TRANSPARENT : SWT.NONE, x, y);\n",
         "\t\t// L14 ONE_TEXT_SHAPER v1 (raster): the stock ExtTextOut over the surface pixels when the route applies (M3GdiTextDraw)\n"
         "\t\tif (!isCulled(x, y) && !M3GdiTextDraw.draw(this, string, isTransparent ? SWT.DRAW_TRANSPARENT : SWT.NONE, x, y, true)) {\n"
         "\t\t\tM3TextDrawing.drawText(this, new String[] { string }, isTransparent ? SWT.DRAW_TRANSPARENT : SWT.NONE, x, y);\n"),
        ("\t\tif (isCulled(x, y)) {\n\t\t\treturn;\n\t\t}\n\t\tfinal String splits[] = this.resources.getTextSplits(inputText, flags);\n",
         "\t\tif (isCulled(x, y) || M3GdiTextDraw.draw(this, inputText, flags, x, y, false)) {\n\t\t\treturn;\n\t\t}\n"
         "\t\tfinal String splits[] = this.resources.getTextSplits(inputText, flags);\n")])]


ATOMS = {"TEXT_FALLBACK_SAFE": atom_fallback, "ONE_TEXT_SHAPER_RASTER": atom_shaper_raster, "GLYPH_RUN_SINK": atom_sink, "GLYPH_RUN_DRAW": atom_draw,
         "SURROGATE_SAFE+TAB_STOPS": atom_surrogate_tabs}

if __name__ == "__main__":
    wt, atom = sys.argv[1], sys.argv[2]
    extra = sys.argv[3] if len(sys.argv) > 3 else str(Path(__file__).with_name("m3text_bodies"))
    for path, state in ATOMS[atom](wt, extra):
        print(f"{atom}\t{Path(path).name}\t{state}")
