# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 5 (every Canvas subclass through the M3 renderer in the m3/gpu modes): the source deltas of the four atoms as exact,
byte-preserving text replacements (count 1; a REGION delta replaces the text from a start anchor up to an end anchor or to the
end of the file), plus record-only rows for new files and decisions; every row is appended once to RECIPE_MEMORY.tsv with ids
L5-xx (same six columns as leaves 2-4).

Atoms (README "Leaf 5"): A1 activation (pattern M3-CANVAS-ACTIVATION v2), A2 damage (M3-CANVAS-DAMAGE v1), A3 retention
(M3-CANVAS-RETAIN v1), A4 stroke state (M3-GC-STROKE v1), A5 probe (M3-CANVAS-PROBE v2).

Idempotent: a delta whose `new` text is present (and whose `old` text is absent) is DONE. Line endings: a CRLF file gets its
anchors with CRLF. Usage: python -I m3gc_coverage_deltas.py <worktree>
"""
import hashlib
import sys
from pathlib import Path

COMMON = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/"
GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
M3 = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/"
WIDGETS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
TESTS = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/"
TOOLS = "docs/m3/gc-extension-seam/tools/"
MEMORY = "docs/m3/gc-extension-seam/RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

# ---------------------------------------------------------------- A1 activation
WM_IMPORT_OLD = "import java.util.*;\n\n/**\n * Startup strategy"
WM_IMPORT_NEW = "import java.util.*;\nimport java.util.stream.*;\n\n/**\n * Startup strategy"
WM_OF_OLD = "\t\treturn mode;\n\t}\n\n\t/** The mode the startup properties ask for, before any fallback. */\n"
WM_OF_NEW = '''\
\t\treturn mode;
\t}

\t/**
\t * Answers the mode of a widget class that inherits its realization from <code>root</code> (GC seam, leaf 5: every
\t * <code>Canvas</code> subclass). The most derived class with its own property decides
\t * (<code>swt.widget.mode.StyledText</code> before <code>swt.widget.mode.Canvas</code>), otherwise the global property;
\t * anonymous classes have no property of their own.
\t *
\t * @param type the runtime class of the widget
\t * @param root the class whose realization the widget inherits, for example <code>Canvas.class</code>
\t * @param supported the modes the root implements; {@link #ORIGINAL} is always implied
\t * @return the requested mode when supported, otherwise the fallback
\t */
\tpublic static WidgetMode of (Class<?> type, Class<?> root, WidgetMode... supported) {
\t\tString owner = Stream.<Class<?>> iterate (type, c -> c != null && root.isAssignableFrom (c), c -> c.getSuperclass ())
\t\t\t.map (Class::getSimpleName)
\t\t\t.filter (name -> !name.isEmpty () && System.getProperty (PROPERTY + "." + name) != null)
\t\t\t.findFirst ().orElse (root.getSimpleName ());
\t\treturn of (owner, supported);
\t}

\t/**
\t * Whether any startup property, global or per widget, asks for one of the modes (GC seam, leaf 5: the Display registers
\t * the M3 canvas factory when one does, so <code>swt.widget.mode.StyledText=m3</code> alone reaches it).
\t */
\tpublic static boolean anyRequested (WidgetMode... modes) {
\t\tList<WidgetMode> wanted = Arrays.asList (modes);
\t\treturn System.getProperties ().stringPropertyNames ().stream ()
\t\t\t.filter (key -> key.equals (PROPERTY) || key.startsWith (PROPERTY + "."))
\t\t\t.map (key -> System.getProperty (key, "").trim ().toUpperCase (Locale.ROOT))
\t\t\t.anyMatch (value -> wanted.stream ().anyMatch (mode -> mode.name ().equals (value)));
\t}

\t/** The mode the startup properties ask for, before any fallback. */
'''

FACTORY_IF_OLD = '''\
import org.eclipse.swt.widgets.*;

/**
 * Defines a factory interface for creating external canvas handlers.
 * Implementations of this interface can be provided to enable support for
 * different types of canvas extensions based on the style of the canvas and the
 * availability of an extension factory.
 */
public interface IExternalCanvasFactory {

\tIExternalCanvasHandler createCanvasExtension(Canvas c);

}
'''
FACTORY_IF_NEW = '''\
import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/**
 * Defines a factory interface for creating external canvas handlers.
 * Implementations of this interface can be provided to enable support for
 * different types of canvas extensions based on the style of the canvas and the
 * availability of an extension factory.
 */
public interface IExternalCanvasFactory {

\tIExternalCanvasHandler createCanvasExtension(Canvas c);

\t/**
\t * M3 (GC seam, leaf 5): whether this factory handles a canvas being constructed (its runtime class is known, the
\t * subclass constructor has not run yet). The default is the Initiative 31 rule the seam was adopted with: the canvas
\t * was created with {@link SWT#M3} and is not a <code>StyledText</code>. The M3 factory decides by the widget mode of
\t * the canvas's class instead, so every <code>Canvas</code> subclass is drawn through it in the <code>m3</code> and
\t * <code>gpu</code> modes.
\t */
\tdefault boolean accepts(Canvas canvas, int style) {
\t\treturn (style & SWT.M3) != 0 && !(canvas instanceof StyledText);
\t}

}
'''

HANDLER_ACTIVE_OLD = '''\
\t\tif (canvas instanceof StyledText || canvas instanceof Decorations)
\t\t\treturn false;

\t\tloadFactory();

\t\tif ((style & SWT.M3) != 0 && externalFactory != null)
\t\t\treturn true;
'''
HANDLER_ACTIVE_NEW = '''\
\t\tif (canvas instanceof Decorations)
\t\t\treturn false;

\t\tloadFactory();

\t\t// M3 (GC seam, leaf 5): the factory decides (IExternalCanvasFactory.accepts); its default is the I31 rule this method
\t\t// had (SWT.M3, never a StyledText); the M3 factory accepts every Canvas subclass of an m3 or gpu mode
\t\tif (externalFactory != null && externalFactory.accepts(canvas, style))
\t\t\treturn true;

\t\tif (canvas instanceof StyledText)
\t\t\treturn false;
'''

DISPLAY_OLD = '''\
/*
 * M3 (GC seam, leaf 4): registers the M3 canvas factory (loaded by name, as RowBackends loads the planes' backend) with
 * ExternalCanvasHandler when the Canvas startup mode (swt.widget.mode.Canvas, else swt.widget.mode) is m3 or gpu. With no
 * mode set nothing is registered and a Canvas created with SWT.M3 paints as it always did.
 */
void registerM3Canvas () {
\tWidgetMode mode = WidgetMode.of ("Canvas", WidgetMode.M3, WidgetMode.GPU); //$NON-NLS-1$
\tif (mode == WidgetMode.M3 || mode == WidgetMode.GPU) {
'''
DISPLAY_NEW = '''\
/*
 * M3 (GC seam, leaf 4): registers the M3 canvas factory (loaded by name, as RowBackends loads the planes' backend) with
 * ExternalCanvasHandler when a startup property asks for m3 or gpu. Leaf 5: any such property, global or per widget
 * (swt.widget.mode.StyledText=m3 alone reaches StyledText); the factory then decides per canvas class
 * (M3CanvasFactory.accepts). With no mode set nothing is registered and every canvas paints as it always did.
 */
void registerM3Canvas () {
\tif (WidgetMode.anyRequested (WidgetMode.M3, WidgetMode.GPU)) {
'''

FACTORY_IMPORT_OLD = "import org.eclipse.swt.widgets.Canvas;\n\n/**\n * Provides a factory"
FACTORY_IMPORT_NEW = ("import org.eclipse.swt.opengl.GLCanvas;\nimport org.eclipse.swt.widgets.Canvas;\nimport org.eclipse.swt.widgets.Decorations;\n"
                      "\n/**\n * Provides a factory")
FACTORY_START = " * M3 (GC seam, leaf 4): the decision is the widget-mode selector times the style bit"
FACTORY_NEW = '''\
 * M3 (GC seam, leaf 5; pattern M3-CANVAS-ACTIVATION v2, v1 was leaf 4's mode x style bit): the decision is the widget
 * mode of the canvas's class, {@link #backend(WidgetMode, Class, int)}. In the <code>m3</code> (raster,
 * {@link M3CanvasHandler#raster}) and <code>gpu</code> ({@link M3CanvasHandler#gpu}) modes every <code>Canvas</code>
 * subclass gets a handler, with or without <code>SWT.M3</code> (StyledText, CLabel, the table and tree cursors, Nebula's
 * canvases); a class may choose its own mode (<code>swt.widget.mode.StyledText</code>, then a superclass's property up to
 * <code>swt.widget.mode.Canvas</code>, then <code>swt.widget.mode</code>). A canvas whose pixels are not its paint
 * listeners' (a <code>GLCanvas</code>, an <code>SWT.EMBEDDED</code> host) needs the bit, a <code>Decorations</code>
 * (Shell) never gets one, and with no mode set every canvas paints as it always did. The selector and the handler makers
 * are constructor arguments, so the decision is testable without a Display.
 * </p>
 */
public class M3CanvasFactory implements IExternalCanvasFactory {
\t/** The widget class whose startup mode decides when no subclass has a property of its own. */
\tpublic static final String WIDGET = "Canvas"; //$NON-NLS-1$

\tprivate static boolean m3FailedWithErrors = false;

\tprivate final Function<Class<?>, WidgetMode> selector;
\tprivate final Function<Canvas, IExternalCanvasHandler> raster, gpu;

\t/** The shipped factory (ServiceLoader, Display registration): the startup mode of the canvas's class, the M3 handlers. */
\tpublic M3CanvasFactory() {
\t\tthis(M3CanvasFactory::mode, M3CanvasHandler::raster, M3CanvasHandler::gpu);
\t}

\t/** The decision with one stubbed mode for every class and stubbed handler makers (headless tests, leaf 4). */
\tpublic M3CanvasFactory(Supplier<WidgetMode> selector, Function<Canvas, IExternalCanvasHandler> raster,
\t\t\tFunction<Canvas, IExternalCanvasHandler> gpu) {
\t\tthis(type -> selector.get(), raster, gpu);
\t}

\t/** The decision with a stubbed per-class selector and stubbed handler makers (headless tests, leaf 5). */
\tpublic M3CanvasFactory(Function<Class<?>, WidgetMode> selector, Function<Canvas, IExternalCanvasHandler> raster,
\t\t\tFunction<Canvas, IExternalCanvasHandler> gpu) {
\t\tthis.selector = selector;
\t\tthis.raster = raster;
\t\tthis.gpu = gpu;
\t}

\t/** The startup mode of a canvas class: its own property, a superclass's up to <code>Canvas</code>, else the global one. */
\tpublic static WidgetMode mode(Class<?> type) {
\t\treturn WidgetMode.of(type, Canvas.class, WidgetMode.M3, WidgetMode.GPU);
\t}

\t/**
\t * The backend a mode, a canvas class and a style select: {@link WidgetMode#GPU}, {@link WidgetMode#M3} (raster) or null
\t * for the stock path. A mode that is not drawn through the M3 renderer is null (default off); a <code>Decorations</code>
\t * never; a canvas whose pixels come from elsewhere ({@link #foreign}) only with <code>SWT.M3</code>; every other class
\t * with or without the bit.
\t */
\tpublic static WidgetMode backend(WidgetMode mode, Class<?> type, int style) {
\t\tif (mode != WidgetMode.M3 && mode != WidgetMode.GPU || Decorations.class.isAssignableFrom(type)) {
\t\t\treturn null;
\t\t}
\t\treturn foreign(type, style) && (style & SWT.M3) == 0 ? null : mode;
\t}

\t/** {@link #backend(WidgetMode, Class, int)} of a plain <code>Canvas</code>. */
\tpublic static WidgetMode backend(WidgetMode mode, int style) {
\t\treturn backend(mode, Canvas.class, style);
\t}

\t/** A canvas whose pixels come from elsewhere: GL content (<code>GLCanvas</code>) or an embedded foreign window (<code>SWT.EMBEDDED</code>). */
\tpublic static boolean foreign(Class<?> type, int style) {
\t\treturn (style & SWT.EMBEDDED) != 0 || GLCanvas.class.isAssignableFrom(type);
\t}

\t@Override
\tpublic boolean accepts(Canvas canvas, int style) {
\t\tif (m3FailedWithErrors) {
\t\t\treturn false;
\t\t}
\t\tClass<?> type = type(canvas);
\t\treturn backend(selector.apply(type), type, style) != null;
\t}

\t@Override
\tpublic IExternalCanvasHandler createCanvasExtension(Canvas c) {
\t\treturn create(c, c.getStyle());
\t}

\t/** {@link #createCanvasExtension} with the style given; the canvas is only handed to the handler maker. */
\tpublic IExternalCanvasHandler create(Canvas c, int style) {
\t\tif (m3FailedWithErrors) {
\t\t\treturn null;
\t\t}
\t\ttry {
\t\t\tClass<?> type = type(c);
\t\t\tWidgetMode backend = backend(selector.apply(type), type, style);
\t\t\treturn backend == null ? null : (backend == WidgetMode.GPU ? gpu : raster).apply(c);
\t\t} catch (final Throwable t) {
\t\t\t// M3 initialization failed; log and disable M3 for all future calls
\t\t\tLogger.logException(t);
\t\t\tm3FailedWithErrors = true;
\t\t\treturn null;
\t\t}
\t}

\t/* the class that decides: the canvas's runtime class (a null canvas, in the headless tests, is a plain Canvas) */
\tstatic Class<?> type(Canvas canvas) {
\t\treturn canvas == null ? Canvas.class : canvas.getClass();
\t}
}
'''

# ---------------------------------------------------------------- A2 damage, A3 retention
CANVAS_REDRAW_OLD = '''\
\tif(externalCanvasHandler != null) {
\t\texternalCanvasHandler.redrawTriggered( x,  y,  width,  height,  all);
\t\tsuper.redraw();
\t}
\telse
\t\tsuper.redraw( x,  y,  width,  height,  all);
'''
CANVAS_REDRAW_NEW = '''\
\tif(externalCanvasHandler != null) {
\t\texternalCanvasHandler.redrawTriggered( x,  y,  width,  height,  all);
\t}
\t// M3 (GC seam, leaf 5, was L4-09): the damage rectangle only; the M3 handler paints and presents exactly the invalidated region
\tsuper.redraw( x,  y,  width,  height,  all);
'''

H_IMPORT1_OLD = "import java.util.function.*;\n\nimport org.eclipse.swt.*;\nimport org.eclipse.swt.graphics.GCExtension;\n"
H_IMPORT1_NEW = ("import java.util.function.*;\nimport java.util.stream.*;\n\nimport org.eclipse.swt.*;\nimport org.eclipse.swt.graphics.GC;\n"
                 "import org.eclipse.swt.graphics.GCExtension;\n")
H_IMPORT2_OLD = "import io.github.humbleui.skija.SurfaceProps;\nimport io.github.humbleui.types.Rect;\n"
H_IMPORT2_NEW = "import io.github.humbleui.skija.SurfaceProps;\n"
H_DOC_OLD = " * {@link #pixel} for probes and tests.\n * </p>\n */\npublic final class M3CanvasHandler"
H_DOC_NEW = ''' * {@link #pixel} for probes and tests.
 * </p>
 * <p>
 * Leaf 5: every <code>Canvas</code> subclass of the <code>m3</code> and <code>gpu</code> modes gets this handler
 * (<code>M3CanvasFactory</code>). Damage (pattern M3-CANVAS-DAMAGE v1, {@link M3CanvasDamage}): the update region is read
 * before <code>BeginPaint</code>, each Paint event is drawn clipped to its part of it (one per rectangle with
 * <code>SWT.NO_MERGE_PAINTS</code>), and only its rectangles are presented, so a partial <code>Canvas.redraw</code> repaints
 * and copies that damage alone. Retention (M3-CANVAS-RETAIN v1): a canvas that draws over its previous frame
 * (<code>SWT.NO_BACKGROUND</code>, <code>SWT.TRANSPARENT</code>) keeps its pixels when the buffer grows, and a new GPU
 * target starts from the buffer's pixels.
 * </p>
 */
public final class M3CanvasHandler'''
H_FIELDS_OLD = "\tprivate boolean disposed;\n"
H_FIELDS_NEW = '''\
\tprivate boolean disposed;
\t/* leaf 5: the damage of the paint in progress, the update region read before BeginPaint, the release deferred while a paint runs */
\tprivate final M3CanvasDamage damage = new M3CanvasDamage ();
\tprivate long regionHandle;
\tprivate int [] regionData = new int [M3CanvasDamage.HEADER + 4 * 8];
\tprivate boolean painting, released;
'''
H_PAINT_START = "\t@Override\n\tpublic Object paint (Consumer<Event> send, long wParam, long lParam) {\n"
H_PAINT_END = "\t/* M3RowCanvas.end: the GPU frame is read back"
H_PAINT_NEW = '''\
\t@Override
\tpublic Object paint (Consumer<Event> send, long wParam, long lParam) {
\t\tlong hwnd = control.handle;
\t\tint oldBits = clipBits (hwnd);
\t\treadRegion (hwnd);
\t\tPAINTSTRUCT ps = new PAINTSTRUCT ();
\t\tlong hdc = OS.BeginPaint (hwnd, ps);
\t\tpainting = true;
\t\ttry {
\t\t\tif (!disposed && ps.right > ps.left && ps.bottom > ps.top) frame (send, hdc, ps);
\t\t} finally {
\t\t\tpainting = false;
\t\t\tOS.EndPaint (hwnd, ps);
\t\t\tif (disposed) release (); else if (oldBits != 0) OS.SetWindowLong (hwnd, OS.GWL_STYLE, oldBits);
\t\t}
\t\treturn LRESULT.ZERO;
\t}

\t/* the stock Composite.WM_PAINT clips siblings and children while it paints; answers the bits to restore, or 0 */
\tprivate int clipBits (long hwnd) {
\t\tint oldBits = OS.GetWindowLong (hwnd, OS.GWL_STYLE), newBits = oldBits | OS.WS_CLIPSIBLINGS | OS.WS_CLIPCHILDREN;
\t\tif (newBits == oldBits) return 0;
\t\tOS.SetWindowLong (hwnd, OS.GWL_STYLE, newBits);
\t\treturn oldBits;
\t}

\t/* the update region as RGNDATA, read before BeginPaint validates it (ViewportDamageHook.record); no rectangle when there is none */
\tprivate void readRegion (long hwnd) {
\t\tif (regionHandle == 0) regionHandle = OS.CreateRectRgn (0, 0, 0, 0);
\t\tregionData [2] = 0;
\t\tif (OS.GetUpdateRgn (hwnd, regionHandle, false) <= OS.NULLREGION) return;
\t\tint bytes = OS.GetRegionData (regionHandle, 0, null);
\t\tif (bytes <= 0) return;
\t\tif (regionData.length < (bytes + 3) / 4) regionData = new int [(bytes + 3) / 4];
\t\tif (OS.GetRegionData (regionHandle, bytes, regionData) == 0) regionData [2] = 0;
\t}

\t/* the stock Composite.WM_PAINT in M3: per pass the background and the Paint event, clipped to the damage; then the damage presented */
\tprivate void frame (Consumer<Event> send, long hdc, PAINTSTRUCT ps) {
\t\tdamage.read (regionData, ps.left, ps.top, ps.right, ps.bottom);
\t\tprepare (hdc);
\t\tint passes = damage.passes (control.getStyle ());
\t\tIntStream.range (0, passes).takeWhile (p -> !disposed).forEach (p -> pass (send, p, passes));
\t\tif (disposed) return;
\t\tIntStream.range (0, damage.count ()).forEach (i -> present (hdc, damage.x (i), damage.y (i), damage.width (i), damage.height (i)));
\t}

\t/* one Paint event, clipped to its pass (M3CanvasDamage.clip): the background first unless the canvas retains its pixels */
\tprivate void pass (Consumer<Event> send, int pass, int passes) {
\t\tio.github.humbleui.skija.Canvas canvas = surface.getCanvas ();
\t\tcanvas.save ();
\t\tdamage.clip (canvas, pass, passes);
\t\tif (!retains ()) canvas.clear (M3ColorConverter.convertSWTColorToM3Color (control.getBackground ()));
\t\ttry {
\t\t\tevent (send, damage.bounds (pass, passes), damage.eventCount (pass, passes));
\t\t} finally {
\t\t\tcanvas.restore ();
\t\t}
\t}

\t/* SWT.NO_BACKGROUND and SWT.TRANSPARENT draw over the previous frame (the stock path draws no background for them) */
\tprivate boolean retains () {
\t\treturn (control.getStyle () & (SWT.NO_BACKGROUND | SWT.TRANSPARENT)) != 0;
\t}

\t/* one damage rectangle: the GPU frame read back into it, then copied to the window */
\tprivate void present (long hdc, int x, int y, int presentWidth, int presentHeight) {
\t\tif (gpu != null) readback (x, y, presentWidth, presentHeight);
\t\tbuffer.present (hdc, x, y, presentWidth, presentHeight);
\t}

\t/* the back buffer at the client size (size classes, never shrinking; growFrame keeps the pixels of a retaining canvas), its surface */
\tprivate void prepare (long hdc) {
\t\tRECT client = new RECT ();
\t\tOS.GetClientRect (control.handle, client);
\t\tif (retains ()) buffer.growFrame (hdc, client.right, client.bottom); else buffer.ensureFrame (hdc, client.right, client.bottom);
\t\ttarget ();
\t\tif (gpu != null) gpu.makeCurrent ();
\t}

\t/* M3RowCanvas.target: one surface over the buffer's pixels, replaced when the buffer is (re)made */
\tprivate void target () {
\t\tif (surface != null && buffer.bits == bits && buffer.width == width && buffer.height == height) return;
\t\tif (surface != null) surface.close ();
\t\tbits = buffer.bits;
\t\twidth = buffer.width;
\t\theight = buffer.height;
\t\tImageInfo info = new ImageInfo (width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL);
\t\tsurface = gpu == null ? Surface.makeRasterDirect (info, bits, width * 4L, PROPS) : gpuTarget (info);
\t}

\t/* a render target on the thread's GL context that starts from the buffer's pixels, so retained pixels survive a growth */
\tprivate Surface gpuTarget (ImageInfo info) {
\t\tgpu.makeCurrent ();
\t\tSurface target = Surface.makeRenderTarget (gpu.context, false, info, 0, SurfaceOrigin.TOP_LEFT, PROPS);
\t\ttry (Pixmap pixels = Pixmap.make (info, bits, width * 4)) {
\t\t\ttarget.writePixels (pixels, 0, 0);
\t\t}
\t\treturn target;
\t}

\t/*
\t * One GC per pass, as the stock path makes one per WM_PAINT: made inside the pass's save so M3ExternalGC restores to it
\t * on dispose. The bounds are the pass in points by the zoom the extension's scaler uses (control.nativeZoom); count is
\t * the number of NO_MERGE_PAINTS events still to come.
\t */
\tprivate void event (Consumer<Event> send, int [] bounds, int count) {
\t\textension.frame (bounds [0], bounds [1], bounds [2], bounds [3]);
\t\tGC gc = new GCExtension (new M3ExternalGC (extension, SWT.NONE));
\t\tEvent event = new Event ();
\t\tevent.gc = gc;
\t\tevent.count = count;
\t\tevent.setBounds (Win32DPIUtils.pixelToPoint (new Rectangle (bounds [0], bounds [1], bounds [2], bounds [3]), control.nativeZoom));
\t\ttry {
\t\t\tsend.accept (event);
\t\t} finally {
\t\t\tgc.dispose ();
\t\t\tevent.gc = null;
\t\t}
\t}

'''
H_DISPOSE_OLD = '''\
\tprivate void dispose () {
\t\tdisposed = true;
\t\tif (surface != null) surface.close ();
\t\tsurface = null;
\t\tif (readback != null) readback.close ();
\t\treadback = null;
\t\tif (gpu != null) gpu.release ();
\t\tbuffer.release ();
\t}
'''
H_DISPOSE_NEW = '''\
\t/* a listener may dispose the canvas during its Paint event: the native resources then go when the paint ends */
\tprivate void dispose () {
\t\tdisposed = true;
\t\tif (!painting) release ();
\t}

\t/* once: the surface, the readback, the GL context reference, the buffer, the region */
\tprivate void release () {
\t\tif (released) return;
\t\treleased = true;
\t\tif (surface != null) surface.close ();
\t\tsurface = null;
\t\tif (readback != null) readback.close ();
\t\treadback = null;
\t\tif (gpu != null) gpu.release ();
\t\tbuffer.release ();
\t\tif (regionHandle != 0) OS.DeleteObject (regionHandle);
\t\tregionHandle = 0;
\t}
'''

# ---------------------------------------------------------------- A4 stroke state
GC_FIELDS_OLD = "\tprivate float[] lineDashes;\n\t// TODO: implement -----------------------------------\n\tprivate float dashOffset;\n\tprivate float miterLimit;\n\t// -----------------------------------\n"
GC_FIELDS_NEW = '''\
\tprivate float[] lineDashes;
\t// M3 (GC seam, leaf 5; pattern M3-GC-STROKE v1): the stroke state the donor stored but never applied, with the stock
\t// defaults (GCData: dash offset 0, miter limit 10); the dash phase reaches LINE_CUSTOM patterns (M3PaintManager), the
\t// miter limit every stroke once setLineAttributes ran (until then the paint keeps the donor's Skija miter, 4, and
\t// drawPath's 1000, which the adopted tests pin)
\tprivate float dashOffset;
\tprivate float miterLimit = 10;
\tprivate boolean miterLimitSet;
'''
GC_MITER_OLD = "\t\tif (this.miterLimit != miterLimit) {\n\t\t\tthis.miterLimit = miterLimit;\n\t\t\tchanged = true;\n\t\t}\n"
GC_MITER_NEW = "\t\tif (this.miterLimit != miterLimit) {\n\t\t\tthis.miterLimit = miterLimit;\n\t\t\tchanged = true;\n\t\t}\n\t\tthis.miterLimitSet = true;\n"
GC_PATH_OLD = "\t\t\t\t// the stroke miter on GC is quite high.\n\t\t\t\tpaint.setStrokeMiter(1000);\n"
GC_PATH_NEW = ("\t\t\t\t// the stroke miter on GC is quite high (the donor's default; leaf 5: a miter limit setLineAttributes gave wins,\n"
               "\t\t\t\t// M3PaintManager.performDraw applied it)\n\t\t\t\tif (!miterLimitSet) {\n\t\t\t\t\tpaint.setStrokeMiter(1000);\n\t\t\t\t}\n")
GC_ACCESS_OLD = "\tfloat[] getLineDashes() {\n\t\treturn this.lineDashes;\n\t}\n"
GC_ACCESS_NEW = '''\
\tfloat[] getLineDashes() {
\t\treturn this.lineDashes;
\t}

\t/* leaf 5: the dash phase in pixels; the stock GC offsets only a custom pattern (win32 GC.getPen: LINE_CUSTOM, GDI+ dash offset) */
\tfloat getDashPhase() {
\t\treturn lineStyle == SWT.LINE_CUSTOM ? getScaler().autoScaleUp(dashOffset) : 0f;
\t}

\t/* leaf 5: whether setLineAttributes gave a miter limit (the stroke then uses it) */
\tboolean isMiterLimitSet() {
\t\treturn miterLimitSet;
\t}

\t/* leaf 5: the miter limit of the line attributes (stock default 10) */
\tfloat getMiterLimit() {
\t\treturn miterLimit;
\t}
'''
PM_MITER_OLD = "\t\t\tpaint.setStrokeJoin(getM3LineJoin(gc.getLineJoin()));\n\n\t\t\ttry (var pathEffect"
PM_MITER_NEW = ("\t\t\tpaint.setStrokeJoin(getM3LineJoin(gc.getLineJoin()));\n"
                "\t\t\t// M3 (GC seam, leaf 5): the miter limit of the line attributes, once set (M3ExternalGC.isMiterLimitSet)\n"
                "\t\t\tif (gc.isMiterLimitSet()) {\n\t\t\t\tpaint.setStrokeMiter(gc.getMiterLimit());\n\t\t\t}\n\n\t\t\ttry (var pathEffect")
PM_ODD_OLD = "\t\t\t\treturn PathEffect.makeDash(getScaledPathFloats(scaler.autoScaleUp(1), newFloats, scaler), 0f);\n"
PM_ODD_NEW = ("\t\t\t\t// M3 (GC seam, leaf 5): the dash offset of the line attributes is the phase (M3ExternalGC.getDashPhase)\n"
              "\t\t\t\treturn PathEffect.makeDash(getScaledPathFloats(scaler.autoScaleUp(1), newFloats, scaler), gc.getDashPhase());\n")
PM_EVEN_OLD = "\t\treturn PathEffect.makeDash(scaledFloats, 0f);\n"
PM_EVEN_NEW = "\t\treturn PathEffect.makeDash(scaledFloats, gc.getDashPhase());\n"

# ---------------------------------------------------------------- tests, probe script
T_FACTORY_DOC_OLD = (" * Headless (no Display): the activation decision of the M3 canvas factory (GC seam, leaf 4). A canvas gets a handler only\n"
                     " * when the widget mode of <code>Canvas</code> is <code>m3</code> or <code>gpu</code> and it was created with\n"
                     " * <code>SWT.M3</code>; the selector and the handler makers are stubbed, so mode x style bit maps to a handler or null.\n")
T_FACTORY_DOC_NEW = (" * Headless (no Display): the activation decision of the M3 canvas factory (GC seam, leaf 4; leaf 5 contract). A plain\n"
                     " * canvas gets a handler when the widget mode of <code>Canvas</code> is <code>m3</code> or <code>gpu</code>, with or\n"
                     " * without <code>SWT.M3</code> (leaf 4 required the bit: L5-02); the selector and the handler makers are stubbed, so the\n"
                     " * mode maps to a handler or null. The per-class matrix is Test_org_eclipse_swt_m3gc_CanvasCoverage.\n")
T_BACKEND_OLD = '''\
\tpublic void testBackendNeedsTheStyleBitAndADrawnMode() {
\t\tfor (WidgetMode mode : WidgetMode.values()) {
\t\t\tassertNull(M3CanvasFactory.backend(mode, SWT.NONE), mode + " without SWT.M3");
\t\t\tassertNull(M3CanvasFactory.backend(mode, SWT.BORDER | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED), mode + " without SWT.M3");
\t\t\tWidgetMode expected = mode == WidgetMode.M3 || mode == WidgetMode.GPU ? mode : null;
'''
T_BACKEND_NEW = '''\
\tpublic void testBackendNeedsADrawnModeNotTheStyleBit() {
\t\tfor (WidgetMode mode : WidgetMode.values()) {
\t\t\tWidgetMode expected = mode == WidgetMode.M3 || mode == WidgetMode.GPU ? mode : null;
\t\t\t// leaf 5 (L5-02): a plain canvas no longer needs SWT.M3 in a drawn mode; leaf 4 asserted null here
\t\t\tassertEquals(expected, M3CanvasFactory.backend(mode, SWT.NONE), mode + " without SWT.M3");
\t\t\tassertEquals(expected, M3CanvasFactory.backend(mode, SWT.BORDER | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED), mode + " without SWT.M3");
'''
T_CREATE_OLD = ('\t\tassertNull(factory(WidgetMode.M3).create(null, SWT.NONE), "m3 mode without the style bit");\n'
                '\t\tassertNull(factory(WidgetMode.GPU).create(null, SWT.BORDER), "gpu mode without the style bit");\n')
T_CREATE_NEW = ('\t\t// leaf 5 (L5-02): without the style bit too; leaf 4 asserted null for these two\n'
                '\t\tassertSame(RASTER, factory(WidgetMode.M3).create(null, SWT.NONE), "m3 mode without the style bit");\n'
                '\t\tassertSame(GPU, factory(WidgetMode.GPU).create(null, SWT.BORDER), "gpu mode without the style bit");\n')
T_SHIPPED_OLD = '\t\tassertNull(M3CanvasFactory.backend(shipped(), SWT.M3), "default off even with the style bit");\n'
T_SHIPPED_NEW = ('\t\tassertNull(M3CanvasFactory.backend(shipped(), SWT.M3), "default off even with the style bit");\n'
                 '\t\tassertNull(M3CanvasFactory.backend(shipped(), SWT.NONE), "default off without it");\n')
T_SUITE_OLD = "\t\tTest_org_eclipse_swt_m3gc_M3CanvasFactory.class, //\n"
T_SUITE_NEW = ("\t\tTest_org_eclipse_swt_m3gc_M3CanvasFactory.class, //\n\t\tTest_org_eclipse_swt_m3gc_CanvasCoverage.class, //\n"
               "\t\tTest_org_eclipse_swt_m3gc_CanvasDamage.class, //\n\t\tTest_org_eclipse_swt_m3gc_StrokeState.class, //\n")
PROBE_SH_OLD = ("# Usage: probe_m3_canvas.sh <m3|gpu|stock> [worktree] [main-out]  (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-leaf4/main)\n"
                "MODE=${1:-m3}; WT=${2:-C:/m3-superset/swt-gcseam}; MAIN=${3:-H:/m3-scratch/gc-leaf4/main}\n"
                "W=${M3_PROBE_OUT:-H:/m3-scratch/gc-leaf4}; P=$W/probe\n")
PROBE_SH_NEW = ("# Leaf 5: five widgets per run (a Canvas without SWT.M3 with dashes, miters and a partial redraw, a StyledText with a\n"
                "# selection, a CTabFolder with 3 tabs, a CLabel with an image, the leaf-4 SWT.M3 canvas); PNGs of the print (stock GC)\n"
                "# and of the M3 back buffer, and probe_<mode>.tsv, go to $W/probes.\n"
                "# Usage: probe_m3_canvas.sh <m3|gpu|stock> [worktree] [main-out]  (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-leaf5/main)\n"
                "MODE=${1:-m3}; WT=${2:-C:/m3-superset/swt-gcseam}; MAIN=${3:-H:/m3-scratch/gc-leaf5/main}\n"
                "W=${M3_PROBE_OUT:-H:/m3-scratch/gc-leaf5}; P=$W/probe; mkdir -p \"$W/probes\"\n")
PROBE_SH_RUN_OLD = ("bash C:/m3-superset/tools/ui_lock.sh claude-gcleaf4 bash C:/m3-superset/tools/hidden_java.sh 120 \"$LOG\" -Xmx1536m \\\n"
                    "  $PROP -Djava.library.path")
PROBE_SH_RUN_NEW = ("bash C:/m3-superset/tools/ui_lock.sh claude-gcleaf5 bash C:/m3-superset/tools/hidden_java.sh 180 \"$LOG\" -Xmx1536m \\\n"
                    "  $PROP -Dm3.probe.out=\"$(cygpath -m \"$W/probes\")\" -Djava.library.path")
PROBE_SH_GREP_OLD = "grep -E 'PROBE|Exception|Error|exit=' \"$LOG\" | head -12\n"
PROBE_SH_GREP_NEW = "grep -E 'PROBE|Exception|Error|exit=' \"$LOG\" | head -40\n"

# ---------------------------------------------------------------- A6 background through the GC (found by the m3 probe)
COMPOSITE_BG_OLD = ("\t/* Retained planes: skipped plane, or record the fill for GPU replay instead of drawing on the HDC */\n"
                    "\tGCData data = gc.getGCData ();\n")
COMPOSITE_BG_NEW = ("\tif (gc instanceof GCExtension) {\n\t\tdrawBackgroundThroughGC (gc, x, y, width, height, offsetX, offsetY);\n\t\treturn;\n\t}\n"
                    "\t/* Retained planes: skipped plane, or record the fill for GPU replay instead of drawing on the HDC */\n"
                    "\tGCData data = gc.getGCData ();\n")
COMPOSITE_FILL_OLD = "\tdrawBackground (hDC, rect, pixel, offsetX, offsetY);\n}\n\nComposite findDeferredControl () {\n"
COMPOSITE_FILL_NEW = '''\
\tdrawBackground (hDC, rect, pixel, offsetX, offsetY);
}

/*
 * M3 (GC seam, leaf 5; pattern M3-CANVAS-BACKGROUND v1): the Paint GC of an M3 canvas (a GCExtension) has no device context
 * and no GCData, so drawBackground (gc, ...) fills through the GC, in points, with the choice of drawBackground (hDC, ...):
 * the background control's image tiled from its client origin moved by the offset, else its colour, else the GC's
 * background (a theme background is filled with that colour). StyledText fills its margins and line backgrounds this way.
 */
void drawBackgroundThroughGC (GC gc, int x, int y, int width, int height, int offsetX, int offsetY) {
\tint zoom = getAutoscalingZoom ();
\tRectangle area = Win32DPIUtils.pixelToPoint (new Rectangle (x, y, width, height), zoom);
\tControl control = findBackgroundControl ();
\tif (control != null && control.backgroundImage != null) {
\t\ttileBackgroundThroughGC (gc, control.backgroundImage, display.map (control, this, 0, 0), area,
\t\t\tDPIUtil.pixelToPoint (offsetX, zoom), DPIUtil.pixelToPoint (offsetY, zoom));
\t\treturn;
\t}
\tColor previous = gc.getBackground ();
\tif (control != null) gc.setBackground (Color.win32_new (display, control.getBackgroundPixel ()));
\tgc.fillRectangle (area);
\tgc.setBackground (previous);
}

/* the image tiled over the area (points), the tiles aligned on the origin moved by the offset; the GC's clipping restored */
void tileBackgroundThroughGC (GC gc, Image image, Point origin, Rectangle area, int offsetX, int offsetY) {
\tRectangle tile = image.getBounds ();
\tint startX = area.x - Math.floorMod (area.x - origin.x + offsetX, tile.width);
\tint startY = area.y - Math.floorMod (area.y - origin.y + offsetY, tile.height);
\tRegion saved = gc.isClipped () ? new Region (display) : null;
\tif (saved != null) gc.getClipping (saved);
\tgc.setClipping (area.intersection (gc.getClipping ()));
\tfor (int ty = startY; ty < area.y + area.height; ty += tile.height) {
\t\tfor (int tx = startX; tx < area.x + area.width; tx += tile.width) gc.drawImage (image, tx, ty);
\t}
\tgc.setClipping (saved);
\tif (saved != null) saved.dispose ();
}

Composite findDeferredControl () {
'''

REGION = object()

# (id, file, old, new, javac_line, reason, apply); old = (REGION, start, end|None) for a region delta; apply False = record only
DELTAS = [
    ("L5-01", COMMON + "internal/WidgetMode.java", WM_IMPORT_OLD, WM_IMPORT_NEW, "seam", "A1: streams for the class-chain selector", True),
    ("L5-02", COMMON + "internal/WidgetMode.java", WM_OF_OLD, WM_OF_NEW, "seam",
     "A1 (M3-CANVAS-ACTIVATION v2): the existing selector (WidgetMode.of/requested, same property names) over a class chain: the most derived "
     "class with its own swt.widget.mode.<SimpleName> decides, up to the root (Canvas), else the global property; anyRequested lets the Display "
     "register the factory for a per-widget key alone. Leaf 4's activation needed SWT.M3; the leaf-5 contract (user brief 2026-10-30: every Canvas "
     "subclass in m3/gpu without the bit) supersedes two leaf-4 assertions of Test_org_eclipse_swt_m3gc_M3CanvasFactory (rewritten, same tests)", True),
    ("L5-03", COMMON + "internal/canvasext/IExternalCanvasFactory.java", FACTORY_IF_OLD, FACTORY_IF_NEW, "seam",
     "A1: the factory decides activation; the default accepts() is exactly the I31 rule isActive had (SWT.M3 bit, never StyledText), so a foreign "
     "factory behaves as before", True),
    ("L5-04", COMMON + "internal/canvasext/ExternalCanvasHandler.java", HANDLER_ACTIVE_OLD, HANDLER_ACTIVE_NEW, "seam",
     "A1: isActive asks the factory first; Decorations (Shell) stays excluded for every factory; truth table of a foreign factory unchanged "
     "(Decorations false, bit and not StyledText true, StyledText false, else forceEnabled)", True),
    ("L5-05", WIDGETS + "Display.java", DISPLAY_OLD, DISPLAY_NEW, "seam",
     "A1: register when any startup property asks for m3/gpu (global, Canvas or a subclass key); no property: nothing registered (default off, measured)", True),
    ("L5-06", GC + "M3CanvasFactory.java", FACTORY_IMPORT_OLD, FACTORY_IMPORT_NEW, "seam", "A1: GLCanvas and Decorations for the exclusions", True),
    ("L5-07", GC + "M3CanvasFactory.java", (REGION, FACTORY_START, None), FACTORY_NEW, "seam",
     "A1: backend(mode, class, style): drawn mode -> handler for every Canvas subclass with or without SWT.M3; Decorations never; GLCanvas and "
     "SWT.EMBEDDED (pixels not drawn by paint listeners) only with the bit; per-class selector (Function<Class<?>, WidgetMode>), leaf-4 Supplier "
     "constructor kept; accepts() = the same decision", True),
    ("L5-08", WIDGETS + "Canvas.java", CANVAS_REDRAW_OLD, CANVAS_REDRAW_NEW, "seam",
     "A2 (M3-CANVAS-DAMAGE v1), closes L4-09: Canvas.redraw(x, y, w, h, all) with a handler invalidates the rectangle only (I31 repainted the whole "
     "canvas because its GL frame is not retained; our back buffer is)", True),
    ("L5-09", M3 + "M3CanvasDamage.java", "-",
     "new pure atom: RGNDATA (GetRegionData ints) intersected with rcPaint -> rectangles, bounds, passes (one merged; one per rectangle with "
     "NO_MERGE_PAINTS and several rectangles, Event.count = rectangles still to come), clip (rect or Skija Region clip in device coordinates)",
     "seam", "A2: the damage arithmetic and the clip are testable without a Display (Test_org_eclipse_swt_m3gc_CanvasDamage, real raster pixels)", False),
    ("L5-10", M3 + "M3CanvasHandler.java", H_IMPORT1_OLD, H_IMPORT1_NEW, "seam", "A2: streams over passes and rectangles; a local GC per pass", True),
    ("L5-11", M3 + "M3CanvasHandler.java", H_IMPORT2_OLD, H_IMPORT2_NEW, "seam", "A2: the rect clip moved to M3CanvasDamage", True),
    ("L5-12", M3 + "M3CanvasHandler.java", H_DOC_OLD, H_DOC_NEW, "seam", "A2/A3 documented on the handler", True),
    ("L5-13", M3 + "M3CanvasHandler.java", H_FIELDS_OLD, H_FIELDS_NEW, "seam", "A2: damage, reusable HRGN and RGNDATA; deferred release flags", True),
    ("L5-14", M3 + "M3CanvasHandler.java", (REGION, H_PAINT_START, H_PAINT_END), H_PAINT_NEW, "seam",
     "A2: region read before BeginPaint (GetUpdateRgn + GetRegionData, the ViewportDamageHook pattern), one pass per Paint event clipped to its part "
     "of the region, each damage rectangle read back (gpu) and presented; WS_CLIPSIBLINGS | WS_CLIPCHILDREN while painting (stock Composite.WM_PAINT); "
     "no background for NO_BACKGROUND and TRANSPARENT (stock). A3 (M3-CANVAS-RETAIN v1): growFrame keeps the pixels of a retaining canvas, a new GPU "
     "target is seeded from the buffer (Surface.writePixels, 0.116.3); GC disposed through a local (a listener may null event.gc)", True),
    ("L5-15", M3 + "M3CanvasHandler.java", H_DISPOSE_OLD, H_DISPOSE_NEW, "seam",
     "A2: a listener that disposes the canvas inside its Paint event no longer frees the surface under the running pass; release once, region freed", True),
    ("L5-16", GC + "M3ExternalGC.java", GC_FIELDS_OLD, GC_FIELDS_NEW, "seam",
     "A4 (M3-GC-STROKE v1): dashOffset/miterLimit leave the TODO block; miter default 10 as the stock GCData (getLineAttributes reported 0, and a "
     "get/set round trip would have stroked every join beveled once the limit is applied)", True),
    ("L5-17", GC + "M3ExternalGC.java", GC_MITER_OLD, GC_MITER_NEW, "seam", "A4: setLineAttributes marks the miter limit as given", True),
    ("L5-18", GC + "M3ExternalGC.java", GC_PATH_OLD, GC_PATH_NEW, "seam", "A4: drawPath keeps the donor's 1000 only while no limit was given", True),
    ("L5-19", GC + "M3ExternalGC.java", GC_ACCESS_OLD, GC_ACCESS_NEW, "seam",
     "A4: phase only for LINE_CUSTOM (stock GC.getPen: GDI+ Pen_SetDashOffset for a custom pattern; predefined styles have no offset), in pixels "
     "(the custom dashes are scaled up at set time, the offset is kept in points)", True),
    ("L5-20", GC + "M3PaintManager.java", PM_MITER_OLD, PM_MITER_NEW, "seam",
     "A4: Paint.setStrokeMiter(float) (javap skija-shared-0.116.3: present, same name as 0.143)", True),
    ("L5-21", GC + "M3PaintManager.java", PM_ODD_OLD, PM_ODD_NEW, "seam", "A4: PathEffect.makeDash(float[], float phase) (javap 0.116.3)", True),
    ("L5-22", GC + "M3PaintManager.java", PM_EVEN_OLD, PM_EVEN_NEW, "seam", "A4: see L5-21", True),
    ("L5-23", TESTS + "Test_org_eclipse_swt_m3gc_M3CanvasFactory.java", T_FACTORY_DOC_OLD, T_FACTORY_DOC_NEW, "seam", "A1: test doc follows the contract", True),
    ("L5-24", TESTS + "Test_org_eclipse_swt_m3gc_M3CanvasFactory.java", T_BACKEND_OLD, T_BACKEND_NEW, "seam",
     "A1: the leaf-4 assertion 'no bit -> null in m3/gpu' is the contract leaf 5 changes on the user's brief; the test method stays (renamed), "
     "the other modes still answer null", True),
    ("L5-25", TESTS + "Test_org_eclipse_swt_m3gc_M3CanvasFactory.java", T_CREATE_OLD, T_CREATE_NEW, "seam", "A1: see L5-24", True),
    ("L5-26", TESTS + "Test_org_eclipse_swt_m3gc_M3CanvasFactory.java", T_SHIPPED_OLD, T_SHIPPED_NEW, "seam",
     "A1: default off is now asserted without the bit too (the discriminator of the leaf-5 default)", True),
    ("L5-27", TESTS + "AllM3GcTests.java", T_SUITE_OLD, T_SUITE_NEW, "seam", "the three leaf-5 test classes in the suite order", True),
    ("L5-28", TESTS + "Test_org_eclipse_swt_m3gc_CanvasCoverage.java", "-",
     "headless matrix mode x subclass x style (WidgetMode x {Canvas, StyledText, CLabel, TableCursor, TreeCursor, AnimatedProgress, Java2DCanvas, "
     "test subclasses} x {none, SWT.M3, NO_BACKGROUND|DOUBLE_BUFFERED, SWT.M3|BORDER}); GLCanvas/EMBEDDED need the bit; Decorations/Shell never; "
     "per-class properties (WidgetMode.of(Class, Class, ...)) and anyRequested with properties restored; the default accepts() is I31's rule",
     "seam", "A1 proof without a Display", False),
    ("L5-29", TESTS + "Test_org_eclipse_swt_m3gc_CanvasDamage.java", "-",
     "headless: no region -> the paint rectangle; rectangles, bounds and intersection with rcPaint; merged vs NO_MERGE_PAINTS passes and event counts; "
     "growth past the initial capacity; the clip on a real Skija raster bitmap (a full clear touches the region's pixels only)",
     "seam", "A2 proof without a Display", False),
    ("L5-30", TESTS + "Test_org_eclipse_swt_m3gc_StrokeState.java", "-",
     "headless: defaults keep the adopted contract (miter 4, drawPath 1000) while getLineAttributes reports the stock 10; a given limit reaches "
     "drawLine and drawPath; a get/set round trip strokes with 10; the dash offset shifts a LINE_CUSTOM pattern on real raster pixels and is ignored "
     "for LINE_DASH (stock)", "seam", "A4 proof", False),
    ("L5-31", TOOLS + "probe_m3_canvas.sh", PROBE_SH_OLD, PROBE_SH_NEW, "seam", "A5 (M3-CANVAS-PROBE v2): leaf-5 output lane and defaults", True),
    ("L5-32", TOOLS + "probe_m3_canvas.sh", PROBE_SH_RUN_OLD, PROBE_SH_RUN_NEW, "seam",
     "A5: own lock name, 180 s (five widgets and pixel reads), PNG/TSV output dir passed to the probe", True),
    ("L5-33", TOOLS + "probe_m3_canvas.sh", PROBE_SH_GREP_OLD, PROBE_SH_GREP_NEW, "seam", "A5: one PROBE line per widget", True),
    ("L5-34", TOOLS + "M3CanvasProbe.java", "leaf 4: one SWT.M3 Canvas with a red rectangle",
     "five widgets in one shell (Canvas without SWT.M3: fill, two LINE_CUSTOM dashes offset 0/6, two miter polylines limit 1/10, a partial redraw; "
     "StyledText with a selection; CTabFolder with 3 tabs; CLabel with an image; the leaf-4 SWT.M3 canvas); per widget handler?, paints, gcExtension "
     "paints (counted before any print), print (stock GC) vs back buffer: exact mismatches of non-text regions, text-coverage ratio; PNGs and TSV",
     "seam", "A5: stock vs m3 vs gpu measured per widget in one JVM per mode", False),
    ("L5-35", "(inventory)", "grep -c $'\\r$' counted every line of the owner files as CRLF",
     "git ls-files --eol: i/lf w/lf for every owner file (od -c confirms \\n)", "-",
     "the Bash tool collapsed the $'\\r$' escape (feedback-bash-tool-backslash-collapse); line endings are read with git ls-files --eol or od, the "
     "delta script still converts anchors for a CRLF file", False),
    ("L5-36", TOOLS + "m3gc_coverage_deltas.py", "DONE = new in text and old not in text",
     "insertion (old inside new): DONE = new in text; deletion (new inside old): DONE = old not in text; else both",
     "replay: 5 APPLIED instead of DONE (L5-13, L5-17, L5-19, L5-26, L5-27 inserted twice)",
     "an insertion delta keeps its anchor, so the leaf-4 rule re-applied it on replay; the worktree was reset (git checkout) and the fixed "
     "script applied once; the fixed point is proven by a replay that prints DONE for every delta", False),
    ("L5-37", TESTS + "Test_org_eclipse_swt_m3gc_CanvasCoverage.java", "saved.putIfAbsent(key, System.getProperty(key))",
     "if (!saved.containsKey(key)) saved.put(key, System.getProperty(key))",
     "junit.log: testAnyRequestedSeesGlobalAndPerWidgetKeys, testAnonymousClassesHaveNoPropertyOfTheirOwn: Assumption failed: the test JVM sets no widget mode",
     "an absent property is saved as null and HashMap.putIfAbsent replaces a null mapping, so the second set of the same key saved the test's own value "
     "and @AfterEach restored it: a property leak across tests of the class (the P04 failure shape); the assumption caught it", False),
    ("L5-38", TESTS + "Test_org_eclipse_swt_m3gc_StrokeState.java", "assert plain[0] && plain[3] && !plain[4] (dash covers pixels 0..3)",
     "first on pixel in {0, 1}, then 4 on, 4 off; the shift assertion unchanged",
     "junit.log: testDashOffsetShiftsACustomPattern: offset 0 starts with the dash: [false, true, true, true, true, false, ...]",
     "test expectation, not the GC: the line starts at x 0.5 (the GC's half-pixel offset) and Skia's non-antialiased edge rounds 0.5 up, so the "
     "first dash covers pixels 1..4; the phase proof (offset 4 = the pattern moved by 4) is position independent", False),
    ("L5-40", TESTS + "Test_org_eclipse_swt_m3gc_StrokeState.java + " + TOOLS + "M3CanvasProbe.java",
     "offset 4 of a {4, 4} pattern (test), offset 6 of a {6, 6} pattern (probe); expected shifted[x] == plain[x + 4]",
     "offset 2 of {4, 4}: shifted[x] == plain[x + 2] from the first dash pixel; probe offset 3 of {6, 6}: row1[x] == row0[x + 3]",
     "junit.log: testDashOffsetShiftsACustomPattern: the pattern moves by the offset ==> array contents differ at index [0]",
     "half a period is direction blind (x + 4 and x - 4 agree modulo 8) and index 0 is outside the line (it starts at pixel 1); a quarter period "
     "observes the direction: the pattern starts the offset into itself, as GDI+ Pen_SetDashOffset does", False),
    ("L5-41", TOOLS + "M3CanvasProbe.java", "Probe.control typed Control", "Probe.control typed Composite (every probed widget is one)",
     "M3CanvasProbe.java:216,339,349: error: cannot find symbol  symbol: method getClientArea()  location: variable control of type Control",
     "getClientArea is Scrollable's; the probe compile (probe_m3_canvas.sh) failed before any run, no measurement was taken", False),
    ("L5-43", TOOLS + "probe_m3_canvas.sh (how it is run)", "timeout 580 bash probe_m3_canvas.sh stock ... | tail",
     "bash probe_m3_canvas.sh <mode> ... > out (background, no outer timeout)",
     "probe_canvas_stock.log complete (PROBE PASS) but no exit= line; wrapper output: memory gate: headroom 2 GB, waiting ... Terminated",
     "ui_lock.sh waits up to 15 min for 6 GB commit headroom (the machine was at its commit limit, fork failures in bash); the outer timeout "
     "killed the wrapper after the probe JVM had finished and written its log, so the stock result stands; later runs have no outer timeout", False),
    ("L5-44", WIDGETS + "Composite.java", COMPOSITE_BG_OLD, COMPOSITE_BG_NEW,
     "probe_canvas_m3.log: NullPointerException: Cannot read field \"planeDraw\" because \"<local8>\" is null at "
     "Composite.drawBackgroundInPixels(Composite.java:398) <- Canvas.drawBackground(Canvas.java:154) <- StyledTextRenderer.drawLineBackground(:592)",
     "A6 (M3-CANVAS-BACKGROUND v1), found by the first m3 probe: the public Canvas/Composite.drawBackground(GC, ...) read the GCData and the HDC "
     "of the GC; a GCExtension has neither, so every StyledText paint threw (the reason I31 excluded StyledText); the GC now fills it", False),
    ("L5-45", WIDGETS + "Composite.java", COMPOSITE_FILL_OLD, COMPOSITE_FILL_NEW, "seam",
     "A6: the stock choice of drawBackground(hDC, ...) through the GC: background control's image (tiled, clipping saved and restored as a Region) "
     "or colour (Color.win32_new of its pixel), else the GC's background; theme backgrounds fall back to the colour (documented)", False),
    ("L5-46", TOOLS + "M3CanvasProbe.java", "the stock reference of an m3/gpu run = this run's Control.print (leaf-4 assumption: a stock GC in every mode)",
     "reference = the stock run's print_<widget>_stock.png; this run's print kept as a PNG; printThroughM3 = GCExtension paints during print",
     "probe m3: CLabel printInk=543 = bufferInk=543 while the stock run's printInk=514; Canvas printPhase=0.994 = bufferPhase (stock 1.000)",
     "in the m3/gpu modes Control.print of a handled canvas paints through the M3 handler (its WM_PAINT into the print DC), so the same-run print "
     "compared M3 with M3; the stock run (no handler) is the true stock reference; the stock run comes first", False),
    ("L5-47", TOOLS + "M3CanvasProbe.java", "miter shapes drawn with gc.drawPolyline", "miter shapes drawn with gc.drawPath (open two-segment path)",
     "probe m3: bufferTip1=0 bufferTip10=0 (stock run: printTip1=0 printTip10=35)",
     "M3ExternalGC.drawPolyline draws Skija drawPolygon (points in POLYGON mode: separate segments, no joins), so no join, miter or bevel exists "
     "for the limit to act on; the adopted drawPolyline tests pin that call (PRE_EXISTING P06, proposal: a Path without close, as drawPolygon); "
     "the probe measures the miter limit on a path", False),
    ("L5-48", TOOLS + "M3CanvasProbe.java", "dash phase compared from x = DASH_X0", "compared from x = DASH_X0 + 1",
     "probe m3: bufferPhase=0.994 (167/168), the mismatch at x=10",
     "an M3 line starts at the half pixel (10.5, Skia rounds it to pixel 11) and a GDI+ line at pixel 10: the first pixel is a start-edge "
     "difference, not a phase difference; the phase is compared where both rows are inside the line", False),
    ("L5-49", WIDGETS + "Composite.java", "L5-44/L5-45 applied (drawBackground through the GC)",
     "reverted (git checkout); L5-44/L5-45 are record-only in this script (apply False): GF01 belongs to the leaf-6 GC-fidelity agent "
     "(C:/m3-superset/swt-gcfix, stacked on 184b5a50980; census H:/m3-scratch/i31-census/gc-api-fidelity/GC_FIDELITY.tsv GF01)",
     "coordinator 2026-10-30: record the StyledText NPE as a finding with its stack, do not fix it in leaf 5, no further M3ExternalGC/M3PaintManager edits",
     "the patch was measured before the revert (probes-gf01-local: StyledText PASS in m3 and gpu, blankMismatch=0, ink 1394 vs stock 1409, selection "
     "494 = 494) and is handed to leaf 6 as evidence; the committed leaf-5 state is probed as it is (StyledText FAIL, GF01 stack)", False),
    ("L5-50", TOOLS + "M3CanvasProbe.java", "a listener exception ended the probe JVM in Shell.open (no widget measured)",
     "guarded steps: shell.open, each readAndDispatch, the partial redraw and each print record the exception (class, message, first frame) and go on; "
     "any exception fails the run", "probe_canvas_m3.log: NullPointerException ... at M3CanvasProbe.main(M3CanvasProbe.java:107) (Shell.open)",
     "one widget's failure must not hide the measurements of the other four", False),
    ("L5-39", TOOLS + "M3CanvasProbe.java", "checkText: int background = ...getRGB ().hashCode (); p.fact (\"ignoredBackgroundHash\", background)",
     "(removed)", "-", "a leftover of the first draft, removed before the probe was first compiled; no measured value depended on it", False),
    ("L5-42", TOOLS + "m3gc_canvas_inventory.sh", "rg -n -o --type java ... ; Nebula filter /test|snippet|example",
     "grep -rnoE --include='*.java' ...; Nebula filter also drops donor-inventory copies",
     "inventory.err: line 13: rg: command not found (COVERAGE.tsv had the header and one row)",
     "rg is a shell function of the interactive profile, not a binary on a script's PATH; and the Nebula clone holds a donor-inventory copy of "
     "RoundedToolbar under m3/stream-segment-proof, which is not a widget of the build (50 Nebula Canvas subclasses, not 51)", False),
]

LEDGER = "docs/m3/gc-extension-seam/FOSS_REUSE_DECISION.tsv"
I31 = "swt-initiative31/skija-canvas"
EPL = "EPL-2.0:CODE_REUSE_ALLOWED_WITH_ATTRIBUTION"
SAME = "EPL-2.0 OR Apache-2.0 (same repository, same licence)"
BASE = "ccf0eaaaef4"
NOTICE = "docs/m3/gc-extension-seam/README.md#leaf-5"
GATES = "javac 0 errors (compile_m3gc.sh 922 sources; compile_platform win32 888, gtk 678, cocoa 817); junit 91/91 (test_m3gc.sh)"
# (capability, repo, commit, source_paths, license, contract_fit, decision, rejected_reason, files whose blob sha is adapted_hashes)
FOSS_ROWS = [
    ("m3-gc-canvas-coverage:accepts-default", I31, "a5af63895d0a7fa797a45d236ff7d0163f38e65b",
     "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/canvasext/ExternalCanvasHandler.java#isActive@blob:abf5992c5fc75ae50c2c0bfcdbefb7c673d276dc;"
     "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/canvasext/IExternalCanvasFactory.java@blob:b9e71b0f5efb4a4db8bc442d9980ae8a4d1d002c",
     EPL, "the I31 rule (style bit, never StyledText, never Decorations) is IExternalCanvasFactory.accepts's default: a foreign factory's truth table "
     "is unchanged (CanvasCoverage.testDefaultAcceptIsTheInitiative31Rule); " + GATES, "ADAPT",
     "the StyledText exclusion stays for foreign factories only; the M3 factory covers StyledText (user brief: every Canvas subclass)",
     [COMMON + "internal/canvasext/IExternalCanvasFactory.java", COMMON + "internal/canvasext/ExternalCanvasHandler.java"]),
    ("m3-gc-canvas-coverage:CLabel-style-mask", I31, "a5af63895d0a7fa797a45d236ff7d0163f38e65b",
     "bundles/org.eclipse.swt/Eclipse SWT Custom Widgets/common/org/eclipse/swt/custom/CLabel.java#checkStyle@blob:b0c3e753604c53272da0df4d0702b2c7d0904f79",
     EPL, "I31 activates a CLabel by keeping SWT.SKIA in checkStyle's mask (blob verified with git hash-object of the raw file)", "NO_FIT",
     "superseded: leaf 5 activates every Canvas subclass by its widget mode, no style bit; CLabel.java untouched", []),
    ("m3-gc-canvas-coverage:gl-full-repaint", I31, "9bcb404c421361b7f5e046dc844ec076e539d0e6",
     "bundles/org.eclipse.swt.skia/src/org/eclipse/swt/internal/skia/SkiaGlCanvasExtension.java#redrawTriggered,doPaint@blob:3609abe580cc0ac618b5e6c98f24352b287c63c5;"
     "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/Canvas.java#redraw(int,int,int,int,boolean)",
     EPL, "I31 repaints the whole GL frame on a partial redraw (super.redraw()) and restores the rest from a snapshot image", "NO_FIT",
     "our back buffer retains its pixels: Canvas.redraw forwards the rectangle and the handler paints and presents the region only (L5-08, L5-14)",
     [WIDGETS + "Canvas.java"]),
    ("m3-gc-canvas-coverage:custom-drawn-controls", "swt-initiative31/custom-drawn-controls", "01471120c8429952d4c8b586de25084cc81dc774",
     "(master head: 'Move Table, TableColumn, TableItem to new source directories', 31 files: build.properties, .classpath, file moves)",
     "EPL-2.0", "no Canvas activation, damage or stroke code on master (git show --stat 01471120c8 in C:/m3-superset/i31)", "NO_FIT",
     "the custom Table (feature/table e445d28c) is the drawn planes' donor (I31_SKIJA_TRICKS.md); nothing for leaf 5", []),
    ("m3-gc-canvas-coverage:damage-contract", "hsoliwal/eclipse.platform.swt (this repository)", BASE,
     "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/Composite.java#WM_PAINT@blob:fb219b49a3f28b2d256d750a484ec282cca99076",
     SAME, "contract: paint clipped to the system region; NO_MERGE_PAINTS with a complex region = one Paint event per rectangle, Event.count the ones "
     "still to come; no background for NO_BACKGROUND or TRANSPARENT; WS_CLIPSIBLINGS | WS_CLIPCHILDREN while painting; " + GATES, "REUSE_DIRECT", "-",
     [M3 + "M3CanvasDamage.java", M3 + "M3CanvasHandler.java"]),
    ("m3-gc-canvas-coverage:region-read", "hsoliwal/eclipse.platform.swt (this repository)", BASE,
     "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/ViewportDamageHook.java#record@blob:c879904d72ce1d65839ceb3705ef8086e22fd027",
     SAME, "pattern: GetUpdateRgn before BeginPaint validates the region, GetRegionData into ints, rectangles from int 8 (left, top, right, bottom)",
     "REUSE_DIRECT", "-", [M3 + "M3CanvasHandler.java"]),
    ("m3-gc-canvas-coverage:stroke-contract", "hsoliwal/eclipse.platform.swt (this repository)", BASE,
     "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/GC.java#getPen,setLineAttributes@blob:48b2d4a464aa0ed793c7c55527b0c25a760e8f76;"
     "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/GCData.java#lineMiterLimit@blob:6a8dbf6225e80b510f1f1ac5b5f1e97141ddd321",
     SAME, "contract: the dash offset applies to a LINE_CUSTOM pattern only (GDI+ Pen_SetDashOffset), the miter limit defaults to 10 and reaches "
     "every stroke (Pen_SetMiterLimit); StrokeState 6/6", "REUSE_DIRECT", "-",
     [GC + "M3ExternalGC.java", GC + "M3PaintManager.java"]),
    ("m3-gc-canvas-coverage:skija-0.116.3-api", "JetBrains/skija (io.github.humbleui:skija-shared, the fork's bound jar)", "0.116.3",
     "io.github.humbleui.skija.PathEffect#makeDash(float[],float);Paint#setStrokeMiter(float);Canvas#clipRegion(Region);Region#setRects(IRect[]);"
     "Surface#writePixels(Pixmap,int,int) (C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib/skija-shared-0.116.3.jar)",
     "Apache-2.0", "javap of the bound jar (never the 0.143 names; m3gc_api_deltas.py D1-D17 list the known deltas, none of these differ)",
     "DIRECT_IMPORT", "-", []),
    ("m3-gc-canvas-coverage:mode-selector", "hsoliwal/eclipse.platform.swt (this repository)", BASE,
     "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/WidgetMode.java#of,requested@blob:cdeed6e9ff84f4b319504b8c4f5e2eef78aaaeb1",
     SAME, "the drawn planes' selector and property names, extended with the class chain (of(Class, Class, ...)) and anyRequested; no parallel "
     "selector; CanvasCoverage 8/8", "REUSE_DIRECT", "-", [COMMON + "internal/WidgetMode.java", GC + "M3CanvasFactory.java"]),
]


def blob(path):
    data = path.read_bytes()
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


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


def tsv(value):
    return value.replace("\t", "\\t").replace("\n", "\\n")


def describe(old):
    if isinstance(old, tuple):
        return "region from %r to %s" % (old[1][:60], "EOF" if old[2] is None else repr(old[2][:60]))
    return old


def apply_one(path, delta_id, old, new):
    raw = path.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n") if crlf else raw
    if isinstance(old, tuple):
        start = text.find(old[1])
        end = len(text) if old[2] is None else text.find(old[2], start + 1)
        if new in text:
            return "DONE"
        if start < 0 or end < 0 or text.count(old[1]) != 1:
            return "ANCHOR"
        text = text[:start] + new + text[end:]
    else:
        # an insertion keeps `old` inside `new` and a deletion keeps `new` inside `old`: decide by the containing text (L5-36)
        if (new in text) if old in new else (old not in text) if new in old else (new in text and old not in text):
            return "DONE"
        if text.count(old) != 1:
            return "ANCHOR x%d" % text.count(old)
        text = text.replace(old, new)
    path.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    return "APPLIED"


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
            status = apply_one(wt / rel, delta_id, old, new)
            print(f"{delta_id} {status} {rel}")
            if status.startswith("ANCHOR"):
                return 1
        if delta_id not in known:
            rows.append("\t".join([delta_id, rel, tsv(describe(old)), tsv(new), javac, reason]) + "\n")
    if rows:
        if not existing.endswith("\n"):
            existing += "\n"
        memory.write_bytes((existing + "".join(rows)).encode("utf-8"))
        print(f"RECIPE_MEMORY.tsv: +{len(rows)} rows")
    # the ledger last, once the owner files are final: adapted_hashes are the blob shas of the files as committed
    if "--ledger" in sys.argv:
        ledger(wt)
    return 0


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    sys.exit(main(args[0] if args else "C:/m3-superset/swt-gcseam"))
