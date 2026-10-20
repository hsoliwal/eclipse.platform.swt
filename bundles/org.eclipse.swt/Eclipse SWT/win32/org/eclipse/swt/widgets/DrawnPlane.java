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
	/* -Dswt.drawn.overlay=true paints the frame log's numbers over the rows */
	static final boolean OVERLAY = Boolean.getBoolean ("swt.drawn.overlay");
	private final StringBuilder overlayText = new StringBuilder (96);
	private final Runnable themeChanged;
	final RowPaint row = new RowPaint ();
	final RECT damage = new RECT (), client = new RECT ();
	private final BackBuffer buffer = new BackBuffer ();
	private final PAINTSTRUCT ps = new PAINTSTRUCT ();
	/* the update region of the paint in progress, restored for the native paint when none of it is visible */
	private final long update = OS.CreateRectRgn (0, 0, 0, 0);
	/* winuser.h: invalidate the window only, not its children (the header); OS has no constant for it */
	private static final int RDW_NOCHILDREN = 0x40;

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
		this.ui = M3LookAndFeel.installUI (control, M3DrawnRowUI.class, new M3DrawnRowUI (widget));
		this.themeGeneration = Themes.generation ();
		this.explicit = Themes.explicit (widget);
		this.renderer = new RowRenderer (explicit != null ? explicit : ui.theme ());
		this.canvas = RowBackends.create (mode, widget, control.handle, itemThemeClass, control::getAutoscalingZoom, hdc -> {
			GCData data = new GCData ();
			data.device = control.display;
			return control.createNewGC (hdc, data);
		});
		/* a theme change (any thread) repaints the plane on its display's thread, when it changes the theme the plane paints with */
		Display display = control.display;
		this.themeChanged = () -> {
			if (display.isDisposed ()) return;
			display.asyncExec (() -> {
				if (!control.isDisposed () && themeChanges ()) control.redraw ();
			});
		};
		Themes.addListener (themeChanged);
		if (OVERLAY) display.timerExec (500, new Runnable () {
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
		if (msg == OS.WM_PAINT || msg == OS.WM_PRINTCLIENT) return true;
		/* input reaches the model at once; the frame log measures until a frame shows it (interaction model, IM1) */
		if (msg == OS.WM_KEYDOWN || msg == OS.WM_CHAR || msg == OS.WM_MOUSEWHEEL || msg == OS.WM_LBUTTONDOWN || msg == OS.WM_VSCROLL
			|| msg == OS.WM_HSCROLL) frames.input (System.nanoTime ());
		return false;
	}

	/**
	 * Paints the damage of a <code>WM_PAINT</code> (with or without a device context) or a
	 * <code>WM_PRINTCLIENT</code>: behaviour pass into the back buffer, drawn pass over it, present. When none of the
	 * damage is visible (a window off the screen) nothing is drawn, but the native control still paints as it does
	 * natively: an empty paint still asks for the row at the top (SetData) and sends its custom draw.
	 */
	final long paint (long hwnd, int msg, long wParam, long lParam) {
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
		long begin = System.nanoTime ();
		buffer.ensure (target, client.right, client.bottom);
		behaviour (hwnd, msg, lParam);
		if (control.isDisposed ()) return;
		afterBehaviour (msg);
		if (!clipDamage (hwnd)) return;
		/* rule 24: a frame that repaints the whole rows area is a full repaint; a model delta must not cause one */
		frames.damage (damage.left <= 0 && damage.right >= client.right && damage.top <= rowsTop && damage.bottom >= client.bottom,
			(long) (damage.right - damage.left) * (damage.bottom - damage.top));
		currentTheme ();
		/* glyphs at the window's DPI, the density of the native rows (a forced swt.autoScale does not resize them) */
		row.zoom = control.nativeZoom;
		canvas.target (buffer);
		canvas.begin (damage.left, damage.top, damage.right - damage.left, damage.bottom - damage.top);
		try {
			drawRows (hwnd);
			if (OVERLAY) overlay ();
		} finally {
			canvas.end ();
		}
		buffer.present (target, damage.left, damage.top, damage.right - damage.left, damage.bottom - damage.top);
		/* stage 1 paints the live model on the UI thread: one generation per frame until the snapshots of IM2 */
		frames.frame (begin, System.nanoTime (), frames.frames ());
	}

	/*
	 * -Dswt.drawn.overlay=true: the frame time (last, p99) and the input-to-pixel latency p99 in the bottom right corner of
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

	/* the native control paints the damage into the back buffer: its side effects happen as natively */
	private void behaviour (long hwnd, int msg, long lParam) {
		long dc = buffer.dc;
		int saved = OS.SaveDC (dc);
		OS.IntersectClipRect (dc, damage.left, damage.top, damage.right, damage.bottom);
		if (msg == OS.WM_PRINTCLIENT) {
			OS.CallWindowProc (nativeProc, hwnd, OS.WM_PRINTCLIENT, dc, lParam);
		} else {
			OS.CallWindowProc (nativeProc, hwnd, OS.WM_PAINT, dc, 0);
		}
		OS.RestoreDC (dc, saved);
	}

	/** Runs after the behaviour pass, where the widget's own paint path runs after its native paint. */
	void afterBehaviour (int msg) {
	}

	/** Narrows the damage to the rows area (for example below a header, setting {@link #rowsTop}); answers false when nothing is left. */
	boolean clipDamage (long hwnd) {
		return true;
	}

	/** The top of the rows area in the client area (below a header), as the last {@link #clipDamage} found it. */
	int rowsTop;

	/** Paints the rows that intersect the damage, top to bottom, and the empty area below them. */
	abstract void drawRows (long hwnd);

	/** Frees the back buffer and the backend. */
	void release () {
		Themes.removeListener (themeChanged);
		M3LookAndFeel.uninstallUI (control);
		buffer.release ();
		canvas.dispose ();
		OS.DeleteObject (update);
	}
}
