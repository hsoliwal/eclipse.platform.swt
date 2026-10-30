# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 7 (Composite-based custom widgets through the M3 renderer in the m3/gpu modes): the source deltas of the atoms as
exact, byte-preserving text replacements, plus record-only rows for decisions and corrections; every row is appended once
to RECIPE_MEMORY.tsv with ids L7-xx (same six columns as leaves 2-5).

Atoms (README "Leaf 7"): COMPOSITE_PAINT_HOOK v1 (the decision and the hook), CHILD_CLIP v1 (child windows stay native, the
present never covers them), CONTAINER_OPT_OUT v1 (nothing hooks Paint: the stock path), M3-COMPOSITE-PROBE v1 (the probe).

The engine (apply_one, describe, tsv, blob) is leaf 5's m3gc_coverage_deltas.py, imported, not copied. Idempotent: a replay
prints DONE for every delta. Usage: python -I m3gc_composite_deltas.py <worktree> [--ledger]
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from m3gc_coverage_deltas import apply_one, blob, describe, tsv  # noqa: E402  (one owner of the delta engine)

COMMON = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/"
GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
M3 = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/"
WIDGETS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
TOOLS = "docs/m3/gc-extension-seam/tools/"
MEMORY = "docs/m3/gc-extension-seam/RECIPE_MEMORY.tsv"
LEDGER = "docs/m3/gc-extension-seam/FOSS_REUSE_DECISION.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"
NOTICE = "docs/m3/gc-extension-seam/README.md#leaf-7"

# ---------------------------------------------------------------- COMPOSITE_PAINT_HOOK: the common seam
FACTORY_IF_OLD = '''\
\tdefault boolean accepts(Canvas canvas, int style) {
\t\treturn (style & SWT.M3) != 0 && !(canvas instanceof StyledText);
\t}

}
'''
FACTORY_IF_NEW = '''\
\tdefault boolean accepts(Canvas canvas, int style) {
\t\treturn (style & SWT.M3) != 0 && !(canvas instanceof StyledText);
\t}

\t/**
\t * M3 (GC seam, leaf 7; pattern COMPOSITE_PAINT_HOOK v1): whether this factory draws a composite that paints itself (a
\t * <code>Composite</code> that is not a <code>Canvas</code>, asked once at its first <code>WM_PAINT</code> with a Paint
\t * listener or filter, when the subclass constructor has run). The default is false: the Initiative 31 seam handles
\t * canvases only.
\t */
\tdefault boolean acceptsComposite(Composite composite, int style) {
\t\treturn false;
\t}

\t/** The handler of a composite this factory accepts ({@link #acceptsComposite}), or null for the stock path. */
\tdefault IExternalCanvasHandler createCompositeExtension(Composite composite) {
\t\treturn null;
\t}

}
'''

HANDLER_OLD = '''\
\t\t\treturn externalCanvas;
\t\t} catch (Throwable t) {
\t\t\tFAILED_WITH_ERRORS = true;
\t\t\tt.printStackTrace(System.err);
\t\t\treturn null;
\t\t}
\t}
}
'''
HANDLER_NEW = '''\
\t\t\treturn externalCanvas;
\t\t} catch (Throwable t) {
\t\t\tFAILED_WITH_ERRORS = true;
\t\t\tt.printStackTrace(System.err);
\t\t\treturn null;
\t\t}
\t}

\t/**
\t * M3 (GC seam, leaf 7; pattern COMPOSITE_PAINT_HOOK v1): the handler of a composite that paints itself, asked once by the
\t * win32 <code>Composite</code> at its first <code>WM_PAINT</code> with a Paint listener or filter (the subclass constructor
\t * has run, so the listener that makes it draw is known), or null for the stock path: the seam disabled or failed, a
\t * <code>Canvas</code> (its constructor hook decided), no factory, or a factory that does not accept the composite
\t * ({@link IExternalCanvasFactory#acceptsComposite}). A factory that throws disables the seam, as for canvases.
\t */
\tpublic static IExternalCanvasHandler createCompositeHandler(Composite composite) {
\t\tif (FAILED_WITH_ERRORS || isDisabled() || composite instanceof Canvas)
\t\t\treturn null;
\t\tloadFactory();
\t\tif (externalFactory == null || !externalFactory.acceptsComposite(composite, composite.getStyle()))
\t\t\treturn null;
\t\ttry {
\t\t\treturn externalFactory.createCompositeExtension(composite);
\t\t} catch (RuntimeException | LinkageError t) {
\t\t\tFAILED_WITH_ERRORS = true;
\t\t\torg.eclipse.swt.internal.SwtLog.getLog("org.eclipse.swt.internal.canvasext").info(() -> "composite handler failed, external canvas off: " + t);
\t\t\treturn null;
\t\t}
\t}
}
'''

# ---------------------------------------------------------------- COMPOSITE_PAINT_HOOK: the M3 factory decides
F_IMPORT_OLD = '''\
import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.render.m3.*;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Decorations;
'''
F_IMPORT_NEW = '''\
import org.eclipse.swt.*;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.render.m3.*;
import org.eclipse.swt.ole.win32.OleClientSite;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Decorations;
'''

F_MAKERS_START = "\tprivate final Function<Canvas, IExternalCanvasHandler> raster, gpu;\n"
F_MAKERS_END = "\t/** The startup mode of a canvas class"
F_MAKERS_NEW = '''\
\tprivate final Function<Composite, IExternalCanvasHandler> raster, gpu;

\t/** The shipped factory (ServiceLoader, Display registration): the startup mode of the canvas's class, the M3 handlers. */
\tpublic M3CanvasFactory() {
\t\tthis(M3CanvasFactory::mode, M3CanvasHandler::raster, M3CanvasHandler::gpu);
\t}

\t/** The decision with one stubbed mode for every class and stubbed handler makers (headless tests, leaf 4). */
\tpublic M3CanvasFactory(Supplier<WidgetMode> selector, Function<Composite, IExternalCanvasHandler> raster,
\t\t\tFunction<Composite, IExternalCanvasHandler> gpu) {
\t\tthis(type -> selector.get(), raster, gpu);
\t}

\t/** The decision with a stubbed per-class selector and stubbed handler makers (headless tests, leaf 5; leaf 7: a composite too). */
\tpublic M3CanvasFactory(Function<Class<?>, WidgetMode> selector, Function<Composite, IExternalCanvasHandler> raster,
\t\t\tFunction<Composite, IExternalCanvasHandler> gpu) {
\t\tthis.selector = selector;
\t\tthis.raster = raster;
\t\tthis.gpu = gpu;
\t}

'''

F_MODE_OLD = '''\
\t/** The startup mode of a canvas class: its own property, a superclass's up to <code>Canvas</code>, else the global one. */
\tpublic static WidgetMode mode(Class<?> type) {
\t\treturn WidgetMode.of(type, Canvas.class, WidgetMode.M3, WidgetMode.GPU);
\t}
'''
F_MODE_NEW = '''\
\t/**
\t * The startup mode of a canvas class: its own property, a superclass's up to <code>Canvas</code>, else the global one.
\t * Leaf 7: a composite that is not a canvas up to <code>Composite</code> (<code>swt.widget.mode.CTabFolder</code>, then
\t * <code>swt.widget.mode.Composite</code>, then <code>swt.widget.mode</code>: the properties the viewport selectors read).
\t */
\tpublic static WidgetMode mode(Class<?> type) {
\t\tClass<?> root = Canvas.class.isAssignableFrom(type) ? Canvas.class : Composite.class;
\t\treturn WidgetMode.of(type, root, WidgetMode.M3, WidgetMode.GPU);
\t}
'''

F_FOREIGN_OLD = '''\
\tpublic static boolean foreign(Class<?> type, int style) {
\t\treturn (style & SWT.EMBEDDED) != 0 || GLCanvas.class.isAssignableFrom(type);
\t}
'''
F_FOREIGN_NEW = '''\
\tpublic static boolean foreign(Class<?> type, int style) {
\t\treturn (style & SWT.EMBEDDED) != 0 || GLCanvas.class.isAssignableFrom(type);
\t}

\t/**
\t * M3 (GC seam, leaf 7; pattern COMPOSITE_PAINT_HOOK v1): the backend of a composite that paints itself, or null for the
\t * stock path. A <code>Canvas</code> is never decided here (its constructor hook decides; <code>Decorations</code> included);
\t * a composite whose pixels come from elsewhere ({@link #foreignComposite}) stays stock, and so does one whose stock paint
\t * reads the pixels behind it or mirrors them (<code>SWT.TRANSPARENT</code>, <code>SWT.RIGHT_TO_LEFT</code>: the retained
\t * planes' rule of <code>Composite.createHandle</code>).
\t */
\tpublic static WidgetMode compositeBackend(WidgetMode mode, Class<?> type, int style) {
\t\tif (mode != WidgetMode.M3 && mode != WidgetMode.GPU || Canvas.class.isAssignableFrom(type)) {
\t\t\treturn null;
\t\t}
\t\treturn foreignComposite(type, style) || (style & (SWT.TRANSPARENT | SWT.RIGHT_TO_LEFT)) != 0 ? null : mode;
\t}

\t/** A composite whose pixels come from elsewhere: an embedded window, an OLE site (its object draws on the device context), a Browser. */
\tpublic static boolean foreignComposite(Class<?> type, int style) {
\t\treturn (style & SWT.EMBEDDED) != 0 || OleClientSite.class.isAssignableFrom(type) || Browser.class.isAssignableFrom(type);
\t}
'''

F_CREATE_OLD = '''\
\tpublic IExternalCanvasHandler create(Canvas c, int style) {
\t\tif (m3FailedWithErrors) {
\t\t\treturn null;
\t\t}
\t\ttry {
\t\t\tClass<?> type = type(c);
\t\t\tWidgetMode backend = backend(selector.apply(type), type, style);
\t\t\treturn backend == null ? null : (backend == WidgetMode.GPU ? gpu : raster).apply(c);
\t\t} catch (final Throwable t) {
'''
F_CREATE_NEW = '''\
\tpublic IExternalCanvasHandler create(Canvas c, int style) {
\t\treturn make(c, type -> backend(selector.apply(type), type, style), type(c));
\t}

\t@Override
\tpublic boolean acceptsComposite(Composite composite, int style) {
\t\tif (m3FailedWithErrors) {
\t\t\treturn false;
\t\t}
\t\tClass<?> type = compositeType(composite);
\t\treturn compositeBackend(selector.apply(type), type, style) != null;
\t}

\t@Override
\tpublic IExternalCanvasHandler createCompositeExtension(Composite composite) {
\t\treturn createComposite(composite, composite.getStyle());
\t}

\t/** {@link #createCompositeExtension} with the style given (leaf 7); the composite is only handed to the handler maker. */
\tpublic IExternalCanvasHandler createComposite(Composite composite, int style) {
\t\treturn make(composite, type -> compositeBackend(selector.apply(type), type, style), compositeType(composite));
\t}

\t/* the backend the decision selects for the widget's class, made by the raster or gpu maker; a failure disables M3 */
\tprivate IExternalCanvasHandler make(Composite widget, Function<Class<?>, WidgetMode> decision, Class<?> type) {
\t\tif (m3FailedWithErrors) {
\t\t\treturn null;
\t\t}
\t\ttry {
\t\t\tWidgetMode backend = decision.apply(type);
\t\t\treturn backend == null ? null : (backend == WidgetMode.GPU ? gpu : raster).apply(widget);
\t\t} catch (final Throwable t) {
'''

F_TYPE_OLD = '''\
\tstatic Class<?> type(Canvas canvas) {
\t\treturn canvas == null ? Canvas.class : canvas.getClass();
\t}
}
'''
F_TYPE_NEW = '''\
\tstatic Class<?> type(Canvas canvas) {
\t\treturn canvas == null ? Canvas.class : canvas.getClass();
\t}

\t/* the class that decides for a composite (a null composite, in the headless tests, is a plain Composite) */
\tstatic Class<?> compositeType(Composite composite) {
\t\treturn composite == null ? Composite.class : composite.getClass();
\t}
}
'''

# ---------------------------------------------------------------- the handler serves a Composite (Canvas is one)
H_IMPORT_OLD = "import org.eclipse.swt.widgets.Canvas;\nimport org.eclipse.swt.widgets.Event;\n"
H_IMPORT_NEW = "import org.eclipse.swt.widgets.Composite;\nimport org.eclipse.swt.widgets.Event;\n"
H_FIELD_OLD = "\tprivate final Canvas control;\n"
H_FIELD_NEW = "\tprivate final Composite control;\n"
H_CTOR_OLD = "\tprivate M3CanvasHandler (Canvas canvas, M3Gpu gpu) {\n"
H_CTOR_NEW = "\tprivate M3CanvasHandler (Composite canvas, M3Gpu gpu) {\n"
H_RASTER_OLD = "\tpublic static M3CanvasHandler raster (Canvas canvas) {\n"
H_RASTER_NEW = "\tpublic static M3CanvasHandler raster (Composite canvas) {\n"
H_GPU_OLD = "\tpublic static M3CanvasHandler gpu (Canvas canvas) {\n"
H_GPU_NEW = "\tpublic static M3CanvasHandler gpu (Composite canvas) {\n"
H_DOC_OLD = " * </p>\n */\npublic final class M3CanvasHandler implements IExternalCanvasHandler {\n"
H_DOC_NEW = ''' * </p>
 * <p>
 * Leaf 7 (pattern COMPOSITE_PAINT_HOOK v1): a <code>Composite</code> that paints itself (CTabFolder, CBanner, ViewForm, an
 * application's or Nebula's composite with a Paint listener) gets this handler too, made at its first painted
 * <code>WM_PAINT</code> (<code>Composite.paintThroughM3</code>). Its child controls stay native windows: the paint runs between
 * <code>BeginPaint</code> and <code>EndPaint</code> with <code>WS_CLIPCHILDREN</code>, so the present, clipped by the paint
 * device context, never covers a child (CHILD_CLIP v1); the back buffer under a child holds the composite's own pixels.
 * </p>
 */
public final class M3CanvasHandler implements IExternalCanvasHandler {
'''

# ---------------------------------------------------------------- COMPOSITE_PAINT_HOOK + CONTAINER_OPT_OUT + CHILD_CLIP in Composite
C_PAINT_OLD = '''\
\tif ((state & CANVAS) == 0 || (state & FOREIGN_HANDLE) != 0) {
\t\treturn super.WM_PAINT (wParam, lParam);
\t}

\t/* Set the clipping bits */
'''
C_PAINT_NEW = '''\
\tif ((state & CANVAS) == 0 || (state & FOREIGN_HANDLE) != 0) {
\t\treturn super.WM_PAINT (wParam, lParam);
\t}

\t/* M3 (GC seam, leaf 7): a composite that paints itself, through the M3 renderer in the m3/gpu modes (paintThroughM3) */
\tLRESULT m3 = paintThroughM3 (wParam, lParam);
\tif (m3 != null) return m3;

\t/* Set the clipping bits */
'''

C_HOOK_OLD = '''\
\treturn LRESULT.ZERO;
}

@Override
LRESULT WM_PRINTCLIENT (long wParam, long lParam) {
'''
C_HOOK_NEW = '''\
\treturn LRESULT.ZERO;
}

/* M3 (GC seam, leaf 7; COMPOSITE_PAINT_HOOK v1): the M3 handler of this composite, made at its first painted WM_PAINT; m3Stock once declined */
org.eclipse.swt.internal.canvasext.IExternalCanvasHandler m3Handler;
boolean m3Stock;

/*
 * M3 (GC seam, leaf 7; patterns COMPOSITE_PAINT_HOOK v1, CONTAINER_OPT_OUT v1, CHILD_CLIP v1): in the m3/gpu modes a composite
 * that paints itself (a Paint listener or filter: the test of the stock path) paints its client area through the M3 handler the
 * factory makes once (ExternalCanvasHandler.createCompositeHandler: not a Canvas, whose constructor hook decides; not an
 * embedded, OLE, Browser, transparent or mirrored composite). A pure container (nothing hooks Paint), a retained-plane or
 * viewport-plane composite and a declined one take the stock path. Child windows stay native: the handler paints between
 * BeginPaint and EndPaint with WS_CLIPCHILDREN, as the stock path does, so its present never covers them. Null: the stock path.
 */
LRESULT paintThroughM3 (long wParam, long lParam) {
\tif (m3Stock || !(hooks (SWT.Paint) || filters (SWT.Paint)) || (retainedBits & GPU_RETAINED) != 0 || plane != null) return null;
\tif (m3Handler == null) {
\t\tm3Handler = org.eclipse.swt.internal.canvasext.ExternalCanvasHandler.createCompositeHandler (this);
\t\tm3Stock = m3Handler == null;
\t\tif (m3Stock) return null;
\t}
\tObject result = m3Handler.paint (event -> sendEvent (SWT.Paint, event), wParam, lParam);
\treturn result instanceof LRESULT r ? r : LRESULT.ZERO;
}

@Override
LRESULT WM_PRINTCLIENT (long wParam, long lParam) {
'''

S = "seam"
DELTAS = [
    # (id, file, old, new, javac_line, reason, apply)
    ("L7-01", COMMON + "internal/canvasext/IExternalCanvasFactory.java", FACTORY_IF_OLD, FACTORY_IF_NEW, S,
     "COMPOSITE_PAINT_HOOK v1: the seam asks a factory about a composite (default false: I31's seam handles canvases only)", True),
    ("L7-02", COMMON + "internal/canvasext/ExternalCanvasHandler.java", HANDLER_OLD, HANDLER_NEW, S,
     "COMPOSITE_PAINT_HOOK v1: createCompositeHandler, null for Canvas/disabled/failed/no factory/declined; a throwing factory disables the seam (SwtLog, no System.out)", True),
    ("L7-03", GC + "M3CanvasFactory.java", F_IMPORT_OLD, F_IMPORT_NEW, S,
     "imports of the composite decision (Browser, OleClientSite: foreign composites; Composite)", True),
    ("L7-04", GC + "M3CanvasFactory.java", ("REGION", F_MAKERS_START, F_MAKERS_END), F_MAKERS_NEW, S,
     "the handler makers take a Composite (a Canvas is one): Function<Composite, IExternalCanvasHandler>; test lambdas c -> double compile unchanged", True),
    ("L7-05", GC + "M3CanvasFactory.java", F_MODE_OLD, F_MODE_NEW, S,
     "one selector: a canvas class up to Canvas (unchanged), a composite class up to Composite (swt.widget.mode.CTabFolder, .Composite, global)", True),
    ("L7-06", GC + "M3CanvasFactory.java", F_FOREIGN_OLD, F_FOREIGN_NEW, S,
     "compositeBackend: m3/gpu only; never a Canvas; foreign (EMBEDDED, OleClientSite, Browser), TRANSPARENT and RIGHT_TO_LEFT stay stock (Composite.createHandle GPU_RETAINED rule)", True),
    ("L7-07", GC + "M3CanvasFactory.java", F_CREATE_OLD, F_CREATE_NEW, S,
     "acceptsComposite/createCompositeExtension/createComposite share make() with create(): one failure path (Logger, m3FailedWithErrors)", True),
    ("L7-08", GC + "M3CanvasFactory.java", F_TYPE_OLD, F_TYPE_NEW, S, "compositeType: a null composite (headless tests) is a plain Composite", True),
    ("L7-09", M3 + "M3CanvasHandler.java", H_IMPORT_OLD, H_IMPORT_NEW, S, "the handler serves a Composite (Canvas is one); only Control/Composite members are used", True),
    ("L7-10", M3 + "M3CanvasHandler.java", H_FIELD_OLD, H_FIELD_NEW, S, "as L7-09", True),
    ("L7-11", M3 + "M3CanvasHandler.java", H_CTOR_OLD, H_CTOR_NEW, S, "as L7-09", True),
    ("L7-12", M3 + "M3CanvasHandler.java", H_RASTER_OLD, H_RASTER_NEW, S, "as L7-09", True),
    ("L7-13", M3 + "M3CanvasHandler.java", H_GPU_OLD, H_GPU_NEW, S, "as L7-09", True),
    ("L7-14", M3 + "M3CanvasHandler.java", H_DOC_OLD, H_DOC_NEW, S, "CHILD_CLIP v1 documented where it holds: BeginPaint DC + WS_CLIPCHILDREN (clipBits)", True),
    ("L7-15", WIDGETS + "Composite.java", C_PAINT_OLD, C_PAINT_NEW, S,
     "COMPOSITE_PAINT_HOOK v1: one call after the CANVAS/FOREIGN_HANDLE test of the stock WM_PAINT; drawBackground*/getGCData untouched (leaf 6 owns them)", True),
    ("L7-16", WIDGETS + "Composite.java", C_HOOK_OLD, C_HOOK_NEW, S,
     "paintThroughM3 + its two fields in one block after WM_PAINT (merge-friendly with leaf 6); CONTAINER_OPT_OUT = the stock test hooks||filters(Paint); retained/viewport planes keep their paths", True),
]


def read_rows():
    """Record-only rows (decisions, corrections) live in m3gc_composite_rows.tsv next to this script, appended as they happen."""
    path = Path(__file__).resolve().parent / "m3gc_composite_rows.tsv"
    if not path.exists():
        return []
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("delta_id\t"):
            delta_id, rel, old, new, javac, reason = line.split("\t")
            rows.append((delta_id, rel, old, new, javac, reason, False))
    return rows


FOSS_ROWS = []  # filled by m3gc_composite_ledger rows at the end of this file


def ledger(wt):
    path = wt / LEDGER
    text = path.read_bytes().decode("utf-8")
    known = {line.split("\t", 1)[0] for line in text.splitlines()}
    rows = []
    for cap, repo, commit, paths, lic, fit, decision, reason, files in FOSS_ROWS:
        if cap not in known:
            hashes = ";".join(blob(wt / f) for f in files) or "-"
            rows.append("\t".join([cap, repo, commit, paths, lic, fit, decision, reason, hashes, NOTICE]) + "\n")
    if rows:
        path.write_bytes((text if text.endswith("\n") else text + "\n").encode("utf-8") + "".join(rows).encode("utf-8"))
        print(f"FOSS_REUSE_DECISION.tsv: +{len(rows)} rows")


def main(worktree):
    wt = Path(worktree)
    memory = wt / MEMORY
    existing = memory.read_text(encoding="utf-8")
    if not existing.startswith(HEADER):
        print("RECIPE_MEMORY.tsv: unexpected header")
        return 1
    known = {line.split("\t", 1)[0] for line in existing.splitlines()[1:] if line}
    rows = []
    for delta_id, rel, old, new, javac, reason, apply in DELTAS + read_rows():
        if apply:
            status = apply_one(wt / rel, delta_id, old, new)
            print(f"{delta_id} {status} {rel}")
            if status.startswith("ANCHOR"):
                return 1
        if delta_id not in known:
            rows.append("\t".join([delta_id, rel, tsv(describe(old)) if apply else old, tsv(new) if apply else new, javac, reason]) + "\n")
    if rows:
        if not existing.endswith("\n"):
            existing += "\n"
        memory.write_bytes((existing + "".join(rows)).encode("utf-8"))
        print(f"RECIPE_MEMORY.tsv: +{len(rows)} rows")
    if "--ledger" in sys.argv:
        ledger(wt)
    return 0


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    sys.exit(main(args[0] if args else "C:/m3-superset/swt-gcleaf7"))
