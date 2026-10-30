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
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.internal.win32.*;

/**
 * The owner-draw protocol of a drawn plane's rows (GC seam, leaf 3): the row painter the plane installs on its renderer
 * for the frames in which the widget hooks <code>SWT.MeasureItem</code>, <code>SWT.EraseItem</code> or
 * <code>SWT.PaintItem</code>. A row is painted as {@link RowRenderer#paintDefault} paints it, with the native widget's
 * events around each cell ({@link DrawnCustomDraw}: the sequence, the event fields, the clip): <code>MeasureItem</code>
 * (its bounds applied as the widget applies them, {@link DrawnPlane#measured}), <code>EraseItem</code>, then the default
 * pieces of the cell the listener left in <code>event.detail</code> (its own background colour, the selection, the image
 * and text), then <code>PaintItem</code>. The row's background, expander, check box and grid lines are the plane's; the
 * focus rectangle is dropped when a listener cleared <code>SWT.FOCUSED</code>, as natively.
 * <p>
 * <code>event.gc</code> is the frame's GC ({@link BufferCanvas#gc}): in the <code>custom</code> mode the plane's own GC on
 * the back buffer's device context, which gets the frame's clip back after each event (its other state, font and colours,
 * is set before each event; a transform or alpha a listener leaves stays with the frame, where the native widget gave each
 * event a new GC); in the <code>m3</code> and <code>gpu</code> modes a <code>GCExtension</code> over the M3 renderer on the
 * plane's surface, disposed after each event (which restores the renderer's canvas to the frame's state) and reused. The
 * GC's font is the cell's, its colours are the native widget's for the cell's state (the theme's selection colours when
 * the theme draws its own selection): one {@link Color} object each, whose handle is the event's pixel.
 * </p>
 */
final class DrawnOwnerDraw implements Theme.RowPainter {
	private final DrawnPlane plane;
	/** The event protocol (hooks, sender, the frame's GC, what releases it after an event). */
	final DrawnCustomDraw protocol;
	/* the frame's GC, asked before any clip of the frame (GcRowCanvas then keeps it past unclip); null between frames */
	private GC gc;
	/* the frame's damage, in pixels: the clip the custom mode's GC goes back to after an event */
	private int frameX, frameY, frameWidth, frameHeight, zoom = 100;
	/* the colours a listener gets through the GC; their handle is the pixel of the event (Color.win32_new) */
	private final Color foreground, background;

	DrawnOwnerDraw (DrawnPlane plane) {
		this.plane = plane;
		Control control = plane.control;
		protocol = new DrawnCustomDraw (control::hooks, control::sendEvent, this::frameGC, this::release);
		foreground = Color.win32_new (control.display, 0);
		background = Color.win32_new (control.display, 0);
	}

	/** True when the widget hooks one of the three events: the frame then takes the UI-thread path with this painter installed. */
	boolean hooked () {
		return protocol.hooked ();
	}

	/** Starts a frame (after the canvas's): the damage in pixels; installs the painter and asks for the GC before any clip. */
	void begin (int x, int y, int width, int height) {
		frameX = x;
		frameY = y;
		frameWidth = width;
		frameHeight = height;
		zoom = plane.control.getAutoscalingZoom ();
		protocol.frame (zoom);
		plane.renderer.override (this);
		frameGC ();
	}

	/** Ends the frame (before the canvas's, which owns the GC): the painter is removed. */
	void end () {
		plane.renderer.override (null);
		gc = null;
	}

	private GC frameGC () {
		if (gc == null) gc = plane.canvas.gc (plane.control, plane.buffer);
		return gc;
	}

	/*
	 * After each event: a GCExtension is disposed (the M3 renderer's canvas goes back to the frame's state; the extension
	 * is reusable), the plane's own GC gets the frame's clip back (in points, edge by edge, as the events are)
	 */
	private void release (GC gc) {
		if (gc instanceof GCExtension) {
			gc.dispose ();
			return;
		}
		int left = DPIUtil.pixelToPoint (frameX, zoom), top = DPIUtil.pixelToPoint (frameY, zoom);
		gc.setClipping (left, top, DPIUtil.pixelToPoint (frameX + frameWidth, zoom) - left, DPIUtil.pixelToPoint (frameY + frameHeight, zoom) - top);
	}

	@Override
	public void paint (RowCanvas canvas, RowPaint row, Theme theme, RowRenderer defaults) {
		Widget item = plane.rowItem;
		/* no item to hand to a listener (a List, a row without a facade), or no GC: the default drawing, as the native widget returns early */
		if (item == null || gc == null) {
			defaults.paintDefault (canvas, row);
			return;
		}
		defaults.rowBackground (canvas, row);
		defaults.expander (canvas, row);
		defaults.checkBox (canvas, row);
		boolean focus = true;
		for (int cell = 0; cell < row.cells; cell++) {
			int keep = cell (canvas, row, theme, defaults, item, cell);
			if (keep < 0) return;
			if ((keep & SWT.FOCUSED) == 0) focus = false;
		}
		defaults.gridLines (canvas, row);
		if (focus) defaults.focus (canvas, row);
	}

	/*
	 * One cell: MeasureItem, EraseItem, the default pieces the listener kept, PaintItem. Answers the kept bits (SWT.FOCUSED
	 * set when the focus rectangle may still be drawn), or -1 when the widget or the item went away in a listener.
	 */
	private int cell (RowCanvas canvas, RowPaint row, Theme theme, RowRenderer defaults, Widget item, int cell) {
		int column = plane.cellColumn [cell];
		int detail = detail (row, cell, column);
		int itemX = row.imageWidth [cell] > 0 ? Math.min (row.imageX [cell], row.textX [cell]) : row.textX [cell];
		int itemWidth = Math.max (0, row.textX [cell] + row.textWidth [cell] - itemX);
		int cellX = row.cellX [cell], cellWidth = row.cellWidth [cell];
		prepare (item, column, cell, row, theme, detail);
		if (protocol.measure (item, column, detail & SWT.SELECTED, itemX, row.y, itemWidth, row.height)) {
			plane.measured (protocol.measuredX, protocol.measuredWidth, protocol.measuredHeight);
			if (gone (item)) return -1;
			prepare (item, column, cell, row, theme, detail);
		}
		int keep = protocol.erase (item, column, detail, cellX, row.y, cellWidth, row.height);
		if (gone (item)) return -1;
		plane.canvas.clip (cellX, row.y, cellWidth, row.height);
		try {
			if ((keep & SWT.BACKGROUND) != 0) defaults.cellBackground (canvas, row, cell);
			if ((keep & SWT.SELECTED) != 0) defaults.selection (canvas, row);
			if ((keep & SWT.FOREGROUND) != 0) defaults.paintCell (canvas, row, cell);
		} finally {
			plane.canvas.unclip ();
		}
		prepare (item, column, cell, row, theme, detail);
		protocol.paint (item, column, detail, itemX, row.y, itemWidth, row.height, cellX, row.y, cellWidth, row.height);
		if (gone (item)) return -1;
		/* a cell that was not offered the focus does not take it away */
		return (detail & SWT.FOCUSED) == 0 ? keep | SWT.FOCUSED : keep;
	}

	private boolean gone (Widget item) {
		return plane.control.isDisposed () || item.isDisposed ();
	}

	/*
	 * The detail bits of a cell, as Table.sendEraseItemEvent sets them: FOREGROUND always; SELECTED for a selected row in
	 * the first column or with FULL_SELECTION, shown (focus, high contrast, or no HIDE_SELECTION); BACKGROUND for a cell
	 * with its own colour (or the sort column's) that is not selected; FOCUSED for the caret row of the focus control with
	 * focus cues, in the same columns as the selection; HOT likewise for a hot row.
	 */
	private int detail (RowPaint row, int cell, int column) {
		Control control = plane.control;
		int detail = SWT.FOREGROUND;
		boolean inSelection = column == 0 || row.is (RowPaint.FULL_SELECTION);
		boolean shown = row.is (RowPaint.FOCUSED) || control.display.getHighContrast () || (control.style & SWT.HIDE_SELECTION) == 0;
		if (row.is (RowPaint.SELECTED) && inSelection && shown) {
			detail |= SWT.SELECTED;
		} else if (row.cellBackground [cell] != -1 || cell == row.sortCell && row.sortBackground != -1) {
			detail |= SWT.BACKGROUND;
		}
		if (inSelection && row.is (RowPaint.CARET) && row.is (RowPaint.FOCUSED) && row.is (RowPaint.FOCUS_CUES)) detail |= SWT.FOCUSED;
		if (inSelection && row.is (RowPaint.HOT)) detail |= SWT.HOT;
		return detail;
	}

	/*
	 * The GC a listener gets, as the native widget fills its GCData before each event: the cell's font; the highlight text
	 * and highlight colours of a selected cell (the 3D face when the control has no focus; the theme's own selection
	 * colours when the theme paints its selection, so a listener's text matches the drawn selection), else the cell's or the
	 * widget's colours. Set again after each event: a listener may have changed them (the M3 GC drops them on dispose).
	 */
	private void prepare (Widget item, int column, int cell, RowPaint row, Theme theme, int detail) {
		Control control = plane.control;
		Font font = item instanceof TableItem tableItem ? tableItem.getFont (column) : item instanceof TreeItem treeItem ? treeItem.getFont (column) : null;
		if (font != null) gc.setFont (font);
		int fg, bg;
		if ((detail & SWT.SELECTED) != 0) {
			boolean focused = row.is (RowPaint.FOCUSED) || control.display.getHighContrast ();
			boolean themed = !theme.parts && theme.selectionBackground != -1;
			fg = themed && theme.selectionForeground != -1 ? colorRef (theme.selectionForeground) : focused ? OS.GetSysColor (OS.COLOR_HIGHLIGHTTEXT) : control.getForegroundPixel ();
			bg = themed ? colorRef (focused ? theme.selectionBackground : theme.selectionInactiveBackground) : OS.GetSysColor (focused ? OS.COLOR_HIGHLIGHT : OS.COLOR_3DFACE);
		} else {
			fg = row.cellForeground [cell] != -1 ? colorRef (row.cellForeground [cell]) : control.getForegroundPixel ();
			bg = row.cellBackground [cell] != -1 ? colorRef (row.cellBackground [cell]) : control.getBackgroundPixel ();
		}
		foreground.handle = fg;
		background.handle = bg;
		gc.setForeground (foreground);
		gc.setBackground (background);
	}

	/* 0xRRGGBB to a GDI COLORREF, the handle of a win32 Color */
	private static int colorRef (int rgb) {
		return (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF;
	}
}
