# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 4 (SWT.M3 canvas activation behind swt.widget.mode, default off): the mechanical token deltas of the seam files as
exact byte-preserving text replacements (count 1), plus the record of the seam deltas made by hand in the owner files
(Read + Edit) and the decisions of the leaf, all appended to RECIPE_MEMORY.tsv with ids L4-xx (same six columns as leaf 2).

Idempotent: a delta whose `new` text is present and `old` text absent is DONE; a record-only row (old and new are
descriptions, no file edit) is appended once. Usage: python -I m3gc_activation_deltas.py <worktree>
"""
import sys
from pathlib import Path

SWT = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/SWT.java"
CANVASEXT = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/canvasext/"
GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
M3 = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/"
RENDER = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/internal/render/"
WIDGETS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
TESTS = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/"
MEMORY = "docs/m3/gc-extension-seam/RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

SKIA_OLD = (
    "\t /**\n"
    "\t * Experimental This API is experimental and may change or be removed in future releases.\n"
    "\t * Style constant specifying that a Canvas should use Skia. Only works for windows and linux.\n"
    "\t * for rendering its content (value is 1&lt;&lt;23).\n"
    "\t * <p><b>Used By:</b></p>\n"
    "\t * <ul>\n"
    "\t * <li><code>Canvas</code></li>\n"
    "\t * </ul>\n"
    "\t *\n"
    "\t * @noreference This API is experimental and may change or be removed in future releases.\n"
    "\t */\n"
    "\tpublic static final int SKIA = 1 << 23;\n")
M3_NEW = (
    "\t/**\n"
    "\t * Style constant specifying that a <code>Canvas</code> is drawn through the M3 renderer\n"
    "\t * (value is 1&lt;&lt;23): its <code>PaintListener</code>s receive a <code>GCExtension</code>\n"
    "\t * over the M3 back buffer of that canvas. Honoured only when the Canvas startup mode\n"
    "\t * (<code>swt.widget.mode.Canvas</code>, else <code>swt.widget.mode</code>) is <code>m3</code>\n"
    "\t * or <code>gpu</code>; otherwise the canvas paints as it always did.\n"
    "\t * <p><b>Used By:</b></p>\n"
    "\t * <ul>\n"
    "\t * <li><code>Canvas</code></li>\n"
    "\t * </ul>\n"
    "\t *\n"
    "\t * @noreference This API is experimental and may change or be removed in future releases.\n"
    "\t */\n"
    "\tpublic static final int M3 = 1 << 23;\n")

# (id, file, old, new, javac_line, reason, apply)  -- apply False: record only (the delta was made by Read + Edit, or is a decision)
DELTAS = [
    ("L4-01", SWT, SKIA_OLD, M3_NEW, "seam",
     "brief rule 14: nothing of ours is named Skija or Skia; the style bit (I31's 1 << 23) is our constant now, SWT.M3; no deprecated SKIA alias: "
     "the only adopted users are ExternalCanvasHandler.isActive (L4-02) and the M3CanvasFactory javadoc, both ours", True),
    ("L4-02", CANVASEXT + "ExternalCanvasHandler.java", "\t\tif ((style & SWT.SKIA) != 0 && externalFactory != null)",
     "\t\tif ((style & SWT.M3) != 0 && externalFactory != null)", "seam", "see L4-01", True),
    ("L4-03", CANVASEXT + "ExternalCanvasHandler.java", "the factory came from ServiceLoader.load(IExternalCanvasFactory.class) only",
     "public static void registerFactory(String className): a factory registered by class name before the first Canvas; the ServiceLoader "
     "result keeps precedence when it already ran; a class that cannot start leaves the stock path (logged behind logActivation)",
     "seam", "the M3 render tree is compiled and loaded apart from the fragment (RowBackends.M3_CANVAS loads the planes' backend by name): "
     "build.properties source.. has no Render M3 folder and build-swt.sh copies classes only, so a META-INF/services entry cannot ship in the fragment; "
     "one naming an absent class would raise ServiceConfigurationError for every Canvas (FAILED_WITH_ERRORS)", False),
    ("L4-04", RENDER + "RowBackends.java", "M3_CANVAS only",
     "public static final String M3_CANVAS_FACTORY = \"org.eclipse.swt.internal.render.m3.gc.M3CanvasFactory\"", "seam",
     "the name the Display registers; the widgets tree never names an M3 class (loaded by name, no Skija dependency of SWT)", False),
    ("L4-05", WIDGETS + "Display.java", "init(): no canvas factory",
     "init(): registerM3Canvas() after DPIUtil.setDeviceZoom: WidgetMode.of(\"Canvas\", M3, GPU) in {M3, GPU} -> ExternalCanvasHandler.registerFactory(RowBackends.M3_CANVAS_FACTORY)",
     "seam", "default off: with no property WidgetMode.of answers ORIGINAL (Canvas has no viewport), nothing is registered, ExternalCanvasHandler.isActive stays false "
     "and every stock test is unchanged; the same selector and lookup as the drawn planes (swt.widget.mode.Canvas, then swt.widget.mode)", False),
    ("L4-06", GC + "M3CanvasFactory.java", "createCanvasExtension(Canvas) answered null (leaf 2, unregistered)",
     "backend(WidgetMode, int style): SWT.M3 bit and mode M3 or GPU, else null; create(Canvas, style) = backend -> raster/gpu maker; constructor "
     "(Supplier<WidgetMode> selector, Function<Canvas, IExternalCanvasHandler> raster, gpu) for the headless test; the no-arg constructor (ServiceLoader, "
     "Display registration) binds WidgetMode.of(\"Canvas\", M3, GPU) and M3CanvasHandler::raster / ::gpu; failure latch and Logger kept (I31 shape)",
     "seam", "activation = mode x style bit, in one testable atom (Test_org_eclipse_swt_m3gc_M3CanvasFactory, no Display)", False),
    ("L4-07", M3 + "M3CanvasHandler.java", "-",
     "new IExternalCanvasHandler over a BackBuffer of the canvas: paint = BeginPaint, ensureFrame(client), one Surface over the DIB (makeRasterDirect) "
     "or an M3Gpu render target (read back into the damage, as M3RowCanvas.end), save + clipRect(damage) + clear(canvas background unless NO_BACKGROUND), "
     "one new GCExtension(new M3ExternalGC(M3PlaneCanvasExtension(canvas, buffer, this::surface))) per frame (as the stock path makes a GC per WM_PAINT), "
     "Paint event with the damage as bounds in points, dispose, restore, present(BitBlt), EndPaint, LRESULT.ZERO; scroll = BitBlt within the buffer + "
     "ScrollWindowEx(SW_INVALIDATE | SW_ERASE [| SW_SCROLLCHILDREN]); redrawTriggered no-ops (the buffer retains); Dispose releases surface, readback, gpu, buffer; "
     "the handler is the canvas's data under M3CanvasHandler.DATA_KEY and answers pixel(x, y) for probes",
     "seam", "pattern: I31 SkiaGlCanvasExtension.doPaint/executePaintEvents and GLPaintEventInvoker.paint (PATTERN_ONLY, FOSS rows m3-gc-canvas-activation:*); "
     "contract: Composite.WM_PAINT of this repository (background, then the Paint event with the damage in points); no GL context of its own: the surface is "
     "exactly the planes' (M3RowCanvas.target)", False),
    ("L4-08", TESTS + "Test_org_eclipse_swt_m3gc_M3CanvasFactory.java", "-",
     "headless: backend() over every WidgetMode x {no bit, other bits, SWT.M3, SWT.M3 | others}; create() answers the raster double in M3, the gpu double in GPU, "
     "null otherwise; the shipped selector is off without the property (assumption); swt.widget.mode.Canvas=m3|gpu|custom selects M3|GPU|ORIGINAL (property restored: "
     "P04 lesson); SWT.M3 == 1 << 23; added to AllM3GcTests", "seam", "the factory decision is the activation gate; it must be provable without a Display", False),
    ("L4-09", WIDGETS + "Canvas.java", "redraw(x, y, width, height, all) with a handler calls super.redraw() (the whole canvas)",
     "unchanged in leaf 4 (adopted I31 hook): a partial redraw of an SWT.M3 canvas repaints it whole; the handler's redrawTriggered is a no-op because the back buffer retains",
     "seam", "documented deviation, no leaf escape: the hook belongs to leaf 1's adopted Canvas; a later leaf may forward the rectangle", False),
    ("L4-10", M3 + "M3CanvasHandler.java", "event bounds and GC points scaled by the autoscaling zoom (stock Composite.WM_PAINT: getAutoscalingZoom, package-private)",
     "event bounds in points by control.nativeZoom (Win32DPIUtils.pixelToPoint), the zoom M3PlaneCanvasExtension's DpiScalerUtil scales the GC by (leaf 2)",
     "seam", "GC and event must agree; getAutoscalingZoom is package-private to widgets; with autoscaling disabled (swt.autoScale=false at a non-100 zoom) both differ "
     "from the stock event by the same factor: documented, a later leaf may expose the autoscaling zoom", False),
    # corrections demanded by javac (compile_m3gc.sh, first run of the activation files: 921 sources, 2 errors)
    ("L4-11", WIDGETS + "Display.java", "registerM3Canvas() inserted between the @Override annotation and init()",
     "registerM3Canvas() placed before init()'s javadoc; the @Override stays on init()",
     "Display.java:3088: error: method does not override or implement a method from a supertype",
     "Read + Edit anchored on `protected void init ()` took the annotation line with it; anchor method insertions on the javadoc opener", False),
    ("L4-12", M3 + "M3CanvasHandler.java", "scroll(): control.checkWidget() (the stock Canvas.scroll checks before scrolling)",
     "scroll(): if (control.isDisposed ()) return; (Widget.checkWidget is protected; Canvas.scroll delegates to the handler before its own check: adopted I31 hook)",
     "M3CanvasHandler.java:214: error: checkWidget() has protected access in Widget",
     "documented deviation: the thread check of a scroll on an SWT.M3 canvas is the window's (ScrollWindowEx), not SWT's ERROR_THREAD_INVALID_ACCESS", False),
    ("L4-13", "docs/m3/gc-extension-seam/tools/M3CanvasProbe.java", "event loop `while (now < end) if (!readAndDispatch ()) sleep ()` (the leaf-3 OwnerDrawProbe loop)",
     "display.timerExec (1500, () -> done [0] = true) and `while (!done [0]) ...`: the timer wakes display.sleep ()",
     "probe_canvas_stock.log: empty, hidden desktop: timed out after 120 s (exit=124)",
     "a bare Canvas raises no event after its first paint, so display.sleep () never returned and the clock was never re-read; the leaf-3 Table kept "
     "producing events, which hid the loop's defect", False),
]


def tsv(value):
    return value.replace("\t", "\\t").replace("\n", "\\n")


def main(worktree):
    wt = Path(worktree)
    memory = wt / MEMORY
    existing = memory.read_text(encoding="utf-8") if memory.exists() else HEADER
    if not existing.startswith(HEADER):
        print("RECIPE_MEMORY.tsv: unexpected header")
        return 1
    known = {line.split("\t", 1)[0] for line in existing.splitlines()[1:] if line}
    rows = []
    for delta_id, rel, old, new, javac, reason, apply in DELTAS:
        if apply:
            path = wt / rel
            text = path.read_bytes().decode("utf-8")
            if new in text and old not in text:
                print(f"{delta_id} DONE {rel}")
            else:
                count = text.count(old)
                if count != 1:
                    print(f"{delta_id} ANCHOR x{count} {rel}")
                    return 1
                path.write_bytes(text.replace(old, new).encode("utf-8"))
                print(f"{delta_id} APPLIED {rel}")
        if delta_id not in known:
            rows.append("\t".join([delta_id, rel, tsv(old), tsv(new), javac, reason]) + "\n")
    if rows:
        if not existing.endswith("\n"):
            existing += "\n"
        memory.write_bytes((existing + "".join(rows)).encode("utf-8"))
        print(f"RECIPE_MEMORY.tsv: +{len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "C:/m3-superset/swt-gcseam"))
