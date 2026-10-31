/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.internal.win32.*;

/**
 * The drawn plane of a win32 row widget (Tree, Table, List) in the <code>custom</code>, <code>m3</code> and
 * <code>gpu</code> modes (docs/m3/RENDER_MODES.md, stage 1).
 * <p>
 * The widget keeps its viewport (or its native rows) exactly as in the <code>viewport</code> mode: the native
 * control is the behaviour plane that holds the resident window, takes input, owns the caret and answers
 * geometry. It never paints to the screen. Each <code>WM_PAINT</code> and <code>WM_PRINTCLIENT</code> first lets
 * the native control paint the damage into the back buffer, for its side effects only (data requests, label
 * measurement, custom draw), and then the drawing core paints the same damage over it: the rows of the resident
 * window that intersect the damage, laid out with the native control's own geometry. Subclasses read the rows.
 * </p>
 */
abstract class DrawnPlane {
	final Control control;
	private final long nativeProc;
	final BufferCanvas canvas;
	/* the backend behind the mirroring of a right-to-left plane (docs/m3/theme-language/README.md); canvas is this */
	private final MirroredCanvas mirroring;
	private final String widget;
	/* the renderer of the theme in effect; replaced when the theme selection changes (Themes) */
	RowRenderer renderer;
	/* the look and feel delegate of the rows (M3LookAndFeel, LF6); a switch installs it again with a new theme */
	private final M3DrawnRowUI ui;
	/* the run-time or user-defined theme that wins over the delegate's (Themes.explicit), read again after a Themes change */
	private Theme explicit;
	private int themeGeneration;
	/* the frame log of the interaction model (IM1): frame times, input-to-pixel latency, snapshot generations */
	final FrameLog frames = new FrameLog ();
	private final StringBuilder overlayText = new StringBuilder (96);
	private final Runnable themeChanged;
	final RowPaint row = new RowPaint ();
	/**
	 * The item of the row being filled, the facade the widget's own custom draw would hand to a listener, or null (none,
	 * or a widget without items): set by {@link #drawRows} before {@link #paintRow}; read by the owner-draw protocol.
	 */
	Widget rowItem;
	/** Per cell of {@link #row}, in display order: the column index (creation order) the owner-draw events report. */
	int [] cellColumn = new int [1];
	/*
	 * GC seam, leaf 3: the owner-draw protocol of the widget's MeasureItem, EraseItem and PaintItem listeners, installed
	 * on the renderer for the frames the widget hooks one of them (checked on each paint); the native custom draw of the
	 * behaviour pass sends none then (ignoreNativeCustomDraw), so a listener sees each event once, from the plane.
	 */
	final DrawnOwnerDraw ownerDraw;
	final RECT damage = new RECT (), client = new RECT ();
	final BackBuffer buffer = new BackBuffer ();
	private final PAINTSTRUCT ps = new PAINTSTRUCT ();
	/* the update region of the paint in progress, restored for the native paint when none of it is visible */
	private final long update = OS.CreateRectRgn (0, 0, 0, 0);
	/* winuser.h: invalidate the window only, not its children (the header); OS has no constant for it */
	private static final int RDW_NOCHILDREN = 0x40;
	/* rule 28: -Dswt.drawn.renderThread=true draws the frames on the Display's render thread (RenderThread) */
	static final boolean RENDER_THREAD = Boolean.getBoolean ("swt.drawn.renderThread");
	/* the plane on the render thread, and the thread; null without it */
	final RenderTarget renderTarget;
	private final RenderThread renderThread;
	/*
	 * Rule 28, leaf R28-4: at each frame tick of the Display's Synchronizer, after the model patches, a plane whose window
	 * the patches damaged paints now (its snapshot is published in the same tick); a hidden window has no update region
	 */
	private final FrameListener frameListener;
	/* the slab being filled while a paint goes to the render thread; null otherwise */
	private FrameSlab slab;
	/* the damage of the last slab published, added to the next while the render thread has not taken it */
	private final RECT pending = new RECT ();
	/* the client size of the last slab published (-1: none yet) */
	private int publishedWidth = -1, publishedHeight = -1;
	/* the zoom the render thread's canvas reads (theme parts), written by the UI thread */
	private volatile int renderZoom = 100;
	/* font descriptors by handle (the platform is asked once per handle) */
	private final LongCache<FontDescriptor> fontDescriptors = new LongCache<> (32, descriptor -> {
		/* nothing to free */
	});
	private final LOGFONT logFont = new LOGFONT ();

	/**
	 * @param control the widget whose <code>handle</code> is the behaviour plane
	 * @param mode the widget's drawn mode
	 * @param widget the widget's simple class name (theme and log)
	 * @param nativeProc the native window procedure of the behaviour plane
	 * @param itemThemeClass the uxtheme class of its rows (NUL-terminated)
	 */
	DrawnPlane (Control control, WidgetMode mode, String widget, long nativeProc, char [] itemThemeClass) {
		this.control = control;
		this.nativeProc = nativeProc;
		this.widget = widget;
		/* rule 28: a plane is made on its display's thread, the UI thread */
		if (ThreadRole.current () == ThreadRole.NONE) ThreadRole.assume (ThreadRole.UI);
		ThreadRole.UI.check ("a drawn plane");
		this.ui = M3LookAndFeel.installUI (control, M3DrawnRowUI.class, new M3DrawnRowUI (widget));
		this.themeGeneration = Themes.generation ();
		this.explicit = Themes.explicit (widget);
		this.renderer = new RowRenderer (explicit != null ? explicit : ui.theme ());
		this.mirroring = new MirroredCanvas (RowBackends.create (mode, widget, control.handle, itemThemeClass, control::getAutoscalingZoom, hdc -> {
			GCData data = new GCData ();
			data.device = control.display;
			return control.createNewGC (hdc, data);
		}));
		this.canvas = mirroring;
		M3BackendReport.first (widget, route (), backend ());
		this.ownerDraw = new DrawnOwnerDraw (this);
		if (RENDER_THREAD) {
			/* the render thread's canvas: made there, reading only the zoom the UI thread writes; it draws image pixels, never an SWT GC */
			java.util.function.IntSupplier zoom = () -> renderZoom;
			long hwnd = control.handle;
			this.renderTarget = new RenderTarget (hwnd, () -> RowBackends.create (mode, widget, hwnd, itemThemeClass, zoom, dc -> {
				throw new IllegalStateException ("rule 28: the render thread makes no SWT GC");
			}));
			this.renderThread = RenderThread.of (control.display);
			renderThread.add (renderTarget);
			this.frameListener = (tickDisplay, frameGeneration) -> {
				if (control.isDisposed ()) return;
				long handle = control.handle;
				if (OS.IsWindowVisible (handle) && OS.GetUpdateRect (handle, null, false)) OS.UpdateWindow (handle);
			};
			control.display.getSynchronizer ().addFrameListener (frameListener);
		} else {
			this.renderTarget = null;
			this.renderThread = null;
			this.frameListener = null;
		}
		/* a theme change (any thread) repaints the plane on its display's thread, when it changes the theme the plane paints with */
		Display display = control.display;
		this.themeChanged = () -> {
			if (display.isDisposed ()) return;
			display.asyncExec (() -> {
				if (!control.isDisposed () && themeChanges ()) control.redraw ();
			});
		};
		Themes.addListener (themeChanged);
		if (M3Features.isOn (control, M3Features.FRAME_OVERLAY)) display.timerExec (500, new Runnable () {
			@Override
			public void run () {
				if (control.isDisposed ()) return;
				control.redraw ();
				display.timerExec (500, this);
			}
		});
	}

	/*
	 * The renderer of the theme in effect: a run-time or user-defined theme (looked up again only after a Themes
	 * change), else the theme of the look and feel delegate, which a look and feel switch replaces.
	 */
	private void currentTheme () {
		int generation = Themes.generation ();
		if (generation != themeGeneration) {
			themeGeneration = generation;
			explicit = Themes.explicit (widget);
		}
		Theme theme = explicit != null ? explicit : ui.theme ();
		if (theme != renderer.theme ()) renderer = new RowRenderer (theme);
	}

	/*
	 * True when the theme in effect differs from the one the plane last painted with: a selection for another widget
	 * class (Themes.select ("Table", ...)) does not repaint a Tree. Comparing instances may answer true for an equal theme
	 * (a re-resolved one); that only repaints once more, as before.
	 */
	private boolean themeChanges () {
		Theme selected = Themes.explicit (widget);
		return (selected != null ? selected : ui.theme ()) != renderer.theme ();
	}

	/** True for the drawn modes. */
	static boolean drawn (WidgetMode mode) {
		return mode == WidgetMode.CUSTOM || mode == WidgetMode.M3 || mode == WidgetMode.GPU;
	}

	/** True for the messages this plane paints: the behaviour plane's paint and print-client. */
	final boolean paints (long hwnd, int msg) {
		if (hwnd != control.handle) return false;
		/* rule 24, hidden viewports: every message that reaches the native window procedure is native work */
		frames.message ();
		if (msg == OS.WM_PAINT || msg == OS.WM_PRINTCLIENT) return true;
		/* rule 28: the render thread's completion, and no erase (the frames cover the client; the screen keeps its pixels until the present) */
		if (renderTarget != null && (msg == RenderTarget.PRESENT || msg == OS.WM_ERASEBKGND)) return true;
		/* input reaches the model at once; the frame log measures until a frame shows it (interaction model, IM1) */
		if (msg == OS.WM_KEYDOWN || msg == OS.WM_CHAR || msg == OS.WM_MOUSEWHEEL || msg == OS.WM_LBUTTONDOWN || msg == OS.WM_VSCROLL
			|| msg == OS.WM_HSCROLL) frames.input (System.nanoTime ());
		/* what no row key holds: the platform's theme parts and colours, and a font handle that may be reused */
		if (msg == OS.WM_THEMECHANGED || msg == OS.WM_SYSCOLORCHANGE || msg == OS.WM_SETTINGCHANGE || msg == OS.WM_SETFONT) {
			forgetRows ();
			fontDescriptors.clear ();
		}
		return false;
	}

	/**
	 * Paints the damage of a <code>WM_PAINT</code> (with or without a device context) or a
	 * <code>WM_PRINTCLIENT</code>: behaviour pass into the back buffer, drawn pass over it, present. When none of the
	 * damage is visible (a window off the screen) nothing is drawn, but the native control still paints as it does
	 * natively: an empty paint still asks for the row at the top (SetData) and sends its custom draw.
	 */
	final long paint (long hwnd, int msg, long wParam, long lParam) {
		ThreadRole.UI.check ("DrawnPlane.paint");
		if (msg == RenderTarget.PRESENT) {
			/* the render thread finished a frame: blit it when it shows the newest snapshot */
			OS.GetClientRect (hwnd, client);
			renderTarget.present (rowsTop, client.right, client.bottom);
			return 0;
		}
		if (msg == OS.WM_ERASEBKGND) return 1;
		boolean own = msg == OS.WM_PAINT && wParam == 0, visible;
		if (own) OS.GetUpdateRgn (hwnd, update, false);
		long target = own ? OS.BeginPaint (hwnd, ps) : wParam;
		try {
			OS.GetClientRect (hwnd, client);
			if (own) {
				OS.SetRect (damage, ps.left, ps.top, ps.right, ps.bottom);
			} else {
				OS.GetClipBox (target, damage);
			}
			visible = OS.IntersectRect (damage, damage, client);
			if (visible) draw (hwnd, msg, target, lParam);
		} finally {
			if (own) OS.EndPaint (hwnd, ps);
		}
		if (!visible) emptyBehaviour (hwnd, msg, wParam, lParam, own);
		return 0;
	}

	/* behaviour pass into the back buffer, drawn pass over it, present: the damage is visible */
	private void draw (long hwnd, int msg, long target, long lParam) {
		/*
		 * The owner-draw events hand the listener a GC, which the render thread never makes (rule 28): while the widget hooks
		 * one of them, every frame takes the UI-thread path below (the render thread's canonical frame and slots stay as
		 * they are and serve again once the listener is gone). Checked on each paint.
		 */
		boolean owner = ownerDraw.hooked ();
		if (renderTarget != null && msg == OS.WM_PAINT && !owner) {
			drawOnRenderThread (hwnd, msg, target, lParam);
			return;
		}
		long begin = System.nanoTime ();
		buffer.ensureFrame (target, client.right, client.bottom);
		/* the retained frame has the back buffer's size: a new one keeps no row */
		retainedRows.ensure (target, buffer.width, buffer.height);
		behaviour (hwnd, msg, lParam);
		if (control.isDisposed ()) return;
		afterBehaviour (msg);
		if (!clipDamage (hwnd)) return;
		currentTheme ();
		/* glyphs at the window's DPI, the density of the native rows (a forced swt.autoScale does not resize them) */
		row.zoom = control.nativeZoom;
		/* a theme's own row or cell painter may read state outside the row: its rows are always drawn */
		Theme theme = renderer.theme ();
		/*
		 * With a render thread this path serves captures (WM_PRINTCLIENT) and owner-drawn frames only: its slots are not kept
		 * up, so it draws every row; a listener's drawing is never kept either (it may draw anything, from state no key holds)
		 */
		boolean mirrored = (OS.GetLayout (target) & OS.LAYOUT_RTL) != 0;
		boolean retain = renderTarget == null && !M3Features.isOn (control, M3Features.FRAME_OVERLAY) && !owner && !mirrored && theme.rowPainter == null && theme.cellPainter == null;
		mirroring.mirror (mirrored ? client.right : 0);
		canvas.target (buffer);
		canvas.begin (damage.left, damage.top, damage.right - damage.left, damage.bottom - damage.top);
		if (owner) ownerDraw.begin (damage.left, damage.top, damage.right - damage.left, damage.bottom - damage.top);
		retainedRows.begin (canvas, renderer, buffer, damage.left, damage.top, damage.right, damage.bottom, client.right, rowsTop, retain);
		try {
			drawRows (hwnd);
			if (M3Features.isOn (control, M3Features.FRAME_OVERLAY)) overlay ();
		} finally {
			if (owner) ownerDraw.end ();
			canvas.end ();
		}
		retainedRows.end ();
		/* rule 24: a frame that drew every row of the rows area is a full repaint; a model delta must not cause one */
		int width = damage.right - damage.left, height = damage.bottom - damage.top;
		boolean allRows = damage.left <= 0 && damage.right >= client.right && height * 10L >= (client.bottom - rowsTop) * 9L;
		int iterated = retainedRows.rowsIterated, drawn = retainedRows.rowsDrawn;
		frames.damage (allRows && iterated > 0 && drawn == iterated, (long) width * height);
		frames.rows (drawn, retainedRows.kept);
		present (target, mirrored, width, height);
		/* stage 1 paints the live model on the UI thread: one generation per frame until the snapshots of IM2 */
		frames.frame (begin, System.nanoTime (), frames.frames ());
	}

	/*
	 * The damage of a mirrored window (WS_EX_LAYOUTRTL: its paint DC has LAYOUT_RTL) is in logical coordinates, x from the right
	 * edge, and the frame holds physical pixels (the behaviour pass drew through a mirrored DC, the rows through MirroredCanvas):
	 * the copy goes through the target DC with its layout set to left-to-right for that call, so it does not mirror the pixels.
	 */
	private void present (long target, boolean mirrored, int width, int height) {
		if (!mirrored) {
			buffer.present (target, damage.left, damage.top, width, height);
			return;
		}
		OS.SetLayout (target, 0);
		buffer.present (target, client.right - damage.right, damage.top, width, height);
		OS.SetLayout (target, OS.LAYOUT_RTL);
	}

	/*
	 * -Dswt.widget.feature.frame-overlay=true (M3Features.FRAME_OVERLAY; -Dswt.drawn.overlay=true is its alias): the frame time (last, p99) and the input-to-pixel latency p99 in the bottom right corner of
	 * the client area (a header covers the top), repainted twice a second. A debugging aid: building its text allocates.
	 */
	private void overlay () {
		overlayText.setLength (0);
		overlayText.append (widget).append (' ').append (frames.frames ()).append (" f | frame p99 ").append (frames.frameTime (99) / 1000 / 1000.0)
			.append (" ms | input p99 ").append (frames.inputLatency (99) / 1000 / 1000.0).append (" ms");
		int width = 300, height = 18, x = Math.max (0, client.right - width - 2), y = Math.max (0, client.bottom - height - 2);
		canvas.fill (x, y, width, height, 0xFFFFE1);
		canvas.text (overlayText.toString (), row.font, 0x000000, x + 4, y, width - 8, height, SWT.LEFT);
	}

	/*
	 * The native paint of a damage nobody sees, as natively: on the window's own paint the native control finds the
	 * update region it would have found (the plane's BeginPaint validated it) and paints through its own BeginPaint,
	 * which answers the same empty rectangle; on a given device context it paints into that context.
	 */
	private void emptyBehaviour (long hwnd, int msg, long wParam, long lParam, boolean own) {
		if (own) OS.RedrawWindow (hwnd, null, update, OS.RDW_INVALIDATE | RDW_NOCHILDREN);
		OS.CallWindowProc (nativeProc, hwnd, msg, wParam, lParam);
		if (!control.isDisposed ()) afterBehaviour (msg);
	}

	/*
	 * The native control paints the damage into the back buffer: its side effects happen as natively, except the owner-draw
	 * events while the widget hooks one: the drawn pass raises those itself, with a GC a listener's drawing survives on
	 * (DrawnOwnerDraw), so the native custom draw of this pass is told to send none (the GC it would make is on the pixels
	 * the drawn pass paints over).
	 */
	private void behaviour (long hwnd, int msg, long lParam) {
		long dc = buffer.dc;
		int saved = OS.SaveDC (dc);
		/* a mirrored window paints through a mirrored DC; mirrored around the client width, not the (larger) buffer's */
		if ((control.style & SWT.RIGHT_TO_LEFT) != 0) {
			OS.SetLayout (dc, OS.LAYOUT_RTL);
			OS.SetWindowOrgEx (dc, client.right - buffer.width, 0, null);
		}
		OS.IntersectClipRect (dc, damage.left, damage.top, damage.right, damage.bottom);
		boolean owner = ownerDraw.hooked (), previous = owner && ignoreNativeCustomDraw (true);
		try {
			if (msg == OS.WM_PRINTCLIENT) {
				OS.CallWindowProc (nativeProc, hwnd, OS.WM_PRINTCLIENT, dc, lParam);
			} else {
				OS.CallWindowProc (nativeProc, hwnd, OS.WM_PAINT, dc, 0);
			}
		} finally {
			if (owner && !control.isDisposed ()) ignoreNativeCustomDraw (previous);
			OS.RestoreDC (dc, saved);
			OS.SetLayout (dc, 0);
			OS.SetWindowOrgEx (dc, 0, 0, null);
		}
	}

	/** Runs after the behaviour pass, where the widget's own paint path runs after its native paint. */
	void afterBehaviour (int msg) {
	}

	/**
	 * Tells the widget's native custom draw to send no owner-draw events (<code>Table.ignoreCustomDraw</code>) for the
	 * behaviour pass of a frame the plane raises them in; answers the previous setting, which the plane restores. The
	 * default (a widget without owner draw) does nothing.
	 */
	boolean ignoreNativeCustomDraw (boolean ignore) {
		return false;
	}

	/**
	 * Applies the bounds a <code>MeasureItem</code> listener answered (pixels: the left and width of the cell's content,
	 * the height), as the widget's own <code>sendMeasureItemEvent</code> does: a wider content widens the scroll range of a
	 * column-less widget, a taller one sets the item height. The default does nothing.
	 */
	void measured (int x, int width, int height) {
	}

	/** Makes room for <code>count</code> entries of {@link #cellColumn}. */
	final void cellColumns (int count) {
		if (cellColumn.length < count) cellColumn = new int [Math.max (count, cellColumn.length * 2)];
	}

	/** Narrows the damage to the rows area (for example below a header, setting {@link #rowsTop}); answers false when nothing is left. */
	boolean clipDamage (long hwnd) {
		return true;
	}

	/** The top of the rows area in the client area (below a header), as the last {@link #clipDamage} found it. */
	int rowsTop;

	/** Paints the rows that intersect the damage, top to bottom (each with {@link #paintRow}), and the empty area below them. */
	abstract void drawRows (long hwnd);

	/* rule 24, delta based view: the rows the frame keeps instead of drawing (RetainedRows, UI-thread placement) */
	private final RetainedRows retainedRows = new RetainedRows (false);

	/**
	 * Paints the row the plane filled ({@link #row}), or keeps it when its slot shows it already (rule 24); while a
	 * paint goes to the render thread, adds it to the snapshot instead (rule 28).
	 */
	final void paintRow () {
		if (slab != null) {
			addRow ();
			return;
		}
		retainedRows.paint (row);
	}

	/* ROW_IMAGE_BOUNDS v1 (real-app fidelity): the image size of a native image list (0 for none) */
	private final int [] iconWidth = new int [1], iconHeight = new int [1];

	final int imageListHeight (long imageList) {
		iconHeight [0] = 0;
		if (imageList != 0) OS.ImageList_GetIconSize (imageList, iconWidth, iconHeight);
		return iconHeight [0];
	}

	/** MODE_TRUTH v1 (census L10): the mode the plane's backend realizes (gpu without a GL context draws m3, m3 without the library custom). */
	final WidgetMode route () {
		return canvas.route ();
	}

	/** BACKEND_REPORT v1: the plane and its backend, for example <code>Table drawn plane, M3RowCanvas(raster)</code>. */
	final String backend () {
		return widget + " drawn plane, " + canvas.backend ();
	}

	/** Paints the empty area below the rows with the frame's defaults (or adds it to the snapshot). */
	final void paintEmpty (int x, int y, int width, int height) {
		if (slab != null) {
			int rowY = row.y, rowHeight = row.height;
			row.y = y;
			row.height = height;
			slab.add (row, FrameSlab.EMPTY);
			row.y = rowY;
			row.height = rowHeight;
			return;
		}
		renderer.paintEmpty (canvas, row, x, y, width, height);
	}

	/*
	 * Rule 28: the behaviour pass runs here as always (into the back buffer, for its side effects); the rows go into the
	 * next snapshot, which the render thread draws; the screen keeps its pixels until the render thread's frame is
	 * presented (RenderTarget.PRESENT). The UI thread never waits: one exchange publishes, an unpark wakes the thread.
	 */
	private void drawOnRenderThread (long hwnd, int msg, long target, long lParam) {
		long begin = System.nanoTime ();
		buffer.ensureFrame (target, client.right, client.bottom);
		behaviour (hwnd, msg, lParam);
		if (control.isDisposed ()) return;
		afterBehaviour (msg);
		if (!clipDamage (hwnd)) return;
		currentTheme ();
		row.zoom = control.nativeZoom;
		renderZoom = control.getAutoscalingZoom ();
		/*
		 * The first frame, and a frame after the client size changed, covers the whole rows area: the render thread's
		 * canonical frame may be new (no pixels yet), and erase is suppressed, so nothing else would fill what the damage
		 * leaves out (the strip under the last whole row). Its unchanged rows are still kept there.
		 */
		if (client.right != publishedWidth || client.bottom != publishedHeight) {
			OS.SetRect (damage, 0, rowsTop, client.right, client.bottom);
			publishedWidth = client.right;
			publishedHeight = client.bottom;
		}
		/* a slab the render thread has not taken yet is replaced: this one covers both damages, from the current model */
		if (renderTarget.slabs.fresh ()) {
			damage.left = Math.max (client.left, Math.min (damage.left, pending.left));
			damage.top = Math.max (rowsTop, Math.min (damage.top, pending.top));
			damage.right = Math.min (client.right, Math.max (damage.right, pending.right));
			damage.bottom = Math.min (client.bottom, Math.max (damage.bottom, pending.bottom));
		}
		OS.SetRect (pending, damage.left, damage.top, damage.right, damage.bottom);
		Theme theme = renderer.theme ();
		slab = renderTarget.slabs.writable ();
		slab.clear ();
		slab.damageX = damage.left;
		slab.damageY = damage.top;
		slab.damageWidth = damage.right - damage.left;
		slab.damageHeight = damage.bottom - damage.top;
		slab.clientWidth = client.right;
		slab.clientHeight = client.bottom;
		slab.rowsTop = rowsTop;
		slab.zoom = row.zoom;
		slab.theme = theme;
		slab.retain = theme.rowPainter == null && theme.cellPainter == null;
		try {
			drawRows (hwnd);
		} finally {
			slab = null;
		}
		long generation = renderTarget.slabs.publish ();
		renderThread.wake ();
		frames.damage (false, (long) (damage.right - damage.left) * (damage.bottom - damage.top));
		frames.frame (begin, System.nanoTime (), generation);
	}

	/* a row into the snapshot: SWT images as pixel copies at the frame's zoom, its fonts described */
	private void addRow () {
		for (int c = 0; c < row.cells; c++) {
			Image image = row.image [c];
			if (image != null) {
				/* copied per frame: an image may change in place, and the render thread never reads an SWT image */
				row.imageData [c] = image.isDisposed () ? null : image.getImageData (renderZoom);
				row.image [c] = null;
			}
			describe (row.cellFont [c]);
		}
		describe (row.font);
		slab.add (row, FrameSlab.DRAW);
	}

	private void describe (long handle) {
		if (handle == 0 || handle == -1 || slab.font (handle) != null) return;
		FontDescriptor descriptor = fontDescriptors.get (handle);
		if (descriptor == null) {
			if (OS.GetObject (handle, LOGFONT.sizeof, logFont) == 0) return;
			int length = 0;
			while (length < logFont.lfFaceName.length && logFont.lfFaceName [length] != 0) length++;
			descriptor = new FontDescriptor (new String (logFont.lfFaceName, 0, length), logFont.lfHeight, logFont.lfWeight, logFont.lfItalic != 0,
				logFont.lfCharSet & 0xFF, logFont.lfQuality & 0xFF, logFont.lfPitchAndFamily & 0xFF);
			fontDescriptors.put (handle, descriptor);
		}
		slab.font (handle, descriptor);
	}

	private void forgetRows () {
		retainedRows.forget ();
	}

	/** Frees the back buffer and the backend. */
	void release () {
		Themes.removeListener (themeChanged);
		M3LookAndFeel.uninstallUI (control);
		buffer.release ();
		retainedRows.release ();
		if (renderTarget != null) {
			renderThread.remove (renderTarget);
			if (!control.display.isDisposed ()) control.display.getSynchronizer ().removeFrameListener (frameListener);
		}
		canvas.dispose ();
		OS.DeleteObject (update);
	}
}
