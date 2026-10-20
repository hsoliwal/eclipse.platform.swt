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
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.m3.M3Footer;

/**
 * The footer of a win32 Table (M3Footer; Table leaf T-Table-O5, docs/m3/TABLE_MEDIATED_VIEWPORT.md): the model
 * ({@link TableFooterModel}) and, with the viewport, the strip that shows it inside the widget (brief rule 25).
 * <p>
 * The strip is non-client area of the ListView: <code>WM_NCCALCSIZE</code> takes its height from the bottom of the client
 * area, above the horizontal scroll bar, so the rows, the scroll bars and the header keep their native geometry.
 * <code>WM_NCPAINT</code> draws it after the native frame: the footer row (one aggregate cell under each column, aligned
 * to the header items, so it scrolls and resizes with them) and the footer bar. A click on a footer cell opens the
 * footer popup (the breakdown of the column, with copy). A change of the model redraws the strip once per burst (one
 * asynchronous paint); a hidden Table draws nothing and paints the strip when it shows. Without the viewport (a VIRTUAL
 * Table, the original mode) the model answers its reads from the items and there is no strip.
 * </p>
 */
final class TableFooter {
	static final int DT_END_ELLIPSIS = 0x8000;
	final Table table;
	final TableViewport viewport;
	final TableFooterModel model;
	private boolean redrawQueued;
	private final Runnable redrawLater = this::redrawQueued;
	/* the column offsets and horizontal scroll of the last paint: a WM_PAINT after they changed repaints the strip */
	private long drawnLayout;
	Shell popup;

	static TableFooter of (Table table) {
		if (table.footer == null) table.footer = new TableFooter (table);
		return table.footer;
	}

	private TableFooter (Table table) {
		this.table = table;
		this.viewport = table.viewport;
		TableViewport lanes = viewport;
		TableFooterModel.Cells cells = lanes != null ? new TableFooterModel.Cells () {
			@Override
			public int rows () {
				return lanes.cells.length ();
			}

			@Override
			public String text (int column, int row) {
				return (String) lanes.cells.get (TableViewport.TEXT, column, row);
			}
		} : new TableFooterModel.Cells () {
			@Override
			public int rows () {
				return table.getItemCount ();
			}

			@Override
			public String text (int column, int row) {
				TableItem item = table._getItem (row, false);
				return item == null ? null : item.getText (column);
			}
		};
		model = new TableFooterModel (table, cells, lanes != null);
	}

	/* ---------------------------------------------------------------- settings */

	/** The strip changes height: the native frame is computed again. */
	void layoutChanged () {
		if (viewport == null) return;
		OS.SetWindowPos (table.handle, 0, 0, 0, 0, 0, OS.SWP_NOMOVE | OS.SWP_NOSIZE | OS.SWP_NOZORDER | OS.SWP_NOACTIVATE | OS.SWP_FRAMECHANGED);
	}

	/** The model changed: the strip is drawn again once, after the burst. */
	void changed () {
		if (viewport == null || !model.shown () || redrawQueued) return;
		redrawQueued = true;
		table.display.asyncExec (redrawLater);
	}

	private void redrawQueued () {
		redrawQueued = false;
		if (!table.isDisposed ()) paint ();
	}

	/* ---------------------------------------------------------------- geometry */

	int rowHeight () {
		return Math.max (1, table.getItemHeightInPixels ());
	}

	/** The height of the strip: the footer row and the footer bar, and the separator line above them. */
	int height () {
		if (!model.shown ()) return 0;
		return (model.visible ? rowHeight () : 0) + (model.bar != 0 ? rowHeight () : 0) + 1;
	}

	/* the strip in window coordinates (GetWindowDC), or null when it has no room */
	private RECT strip () {
		long hwnd = table.handle;
		RECT window = new RECT (), client = new RECT ();
		OS.GetWindowRect (hwnd, window);
		OS.GetClientRect (hwnd, client);
		OS.MapWindowPoints (hwnd, 0, client, 2);
		RECT strip = new RECT ();
		OS.SetRect (strip, client.left - window.left, client.bottom - window.top, client.right - window.left, client.bottom - window.top + height ());
		return strip.right > strip.left && strip.bottom > strip.top ? strip : null;
	}

	/* the cell of a column in the footer row, in window coordinates (null when the column is not shown) */
	private RECT cell (RECT strip, int column) {
		long hwnd = table.handle;
		if (table.hwndHeader == 0 || column >= table.columnCount) return null;
		RECT item = new RECT ();
		if (OS.SendMessage (table.hwndHeader, OS.HDM_GETITEMRECT, column, item) == 0 || item.right <= item.left) return null;
		OS.MapWindowPoints (table.hwndHeader, hwnd, item, 2);
		RECT client = new RECT (), window = new RECT ();
		OS.GetClientRect (hwnd, client);
		OS.MapWindowPoints (hwnd, 0, client, 2);
		OS.GetWindowRect (hwnd, window);
		int dx = client.left - window.left;
		RECT cell = new RECT ();
		OS.SetRect (cell, Math.max (strip.left, item.left + dx), strip.top + 1, Math.min (strip.right, item.right + dx), strip.top + 1 + rowHeight ());
		return cell.right > cell.left ? cell : null;
	}

	/**
	 * The bounds of a footer cell (column &gt;= 0) or of the footer bar (-1) relative to the Table's client area: below
	 * it, since the strip is outside the client area. Null when not shown.
	 */
	Rectangle bounds (int column) {
		if (viewport == null || !model.shown ()) return null;
		RECT strip = strip ();
		if (strip == null) return null;
		RECT rect;
		if (column < 0) {
			if (model.bar == 0) return null;
			rect = new RECT ();
			int top = strip.top + 1 + (model.visible ? rowHeight () : 0);
			OS.SetRect (rect, strip.left, top, strip.right, top + rowHeight ());
		} else {
			rect = model.visible ? cell (strip, column) : null;
			if (rect == null) return null;
		}
		/* window coordinates to client coordinates */
		long hwnd = table.handle;
		RECT client = new RECT (), window = new RECT ();
		OS.GetClientRect (hwnd, client);
		OS.MapWindowPoints (hwnd, 0, client, 2);
		OS.GetWindowRect (hwnd, window);
		int dx = window.left - client.left, dy = window.top - client.top;
		Rectangle pixels = new Rectangle (rect.left + dx, rect.top + dy, rect.right - rect.left, rect.bottom - rect.top);
		return Win32DPIUtils.pixelToPoint (pixels, table.getAutoscalingZoom ());
	}

	/* the column whose footer cell holds a point (screen coordinates), -2 on the footer bar, -1 elsewhere */
	private int columnAt (int x, int y) {
		RECT strip = strip ();
		if (strip == null) return -1;
		RECT window = new RECT ();
		OS.GetWindowRect (table.handle, window);
		int wx = x - window.left, wy = y - window.top;
		if (wx < strip.left || wx >= strip.right || wy < strip.top || wy >= strip.bottom) return -1;
		if (model.visible && wy < strip.top + 1 + rowHeight ()) {
			for (int column = 0; column < table.columnCount; column++) {
				RECT cell = cell (strip, column);
				if (cell != null && wx >= cell.left && wx < cell.right) return column;
			}
			return -1;
		}
		return -2;
	}

	/* ---------------------------------------------------------------- messages */

	/** The strip's messages (TableViewport.beforeMessage); PASS for the others. */
	long message (int msg, long wParam, long lParam) {
		long hwnd = table.handle;
		switch (msg) {
			case OS.WM_NCCALCSIZE: {
				long code = table.callWindowProc (hwnd, msg, wParam, lParam);
				int height = height ();
				if (height > 0) {
					RECT rect = new RECT ();
					OS.MoveMemory (rect, lParam, RECT.sizeof);
					rect.bottom = Math.max (rect.top, rect.bottom - height);
					OS.MoveMemory (lParam, rect, RECT.sizeof);
				}
				return code;
			}
			case OS.WM_NCPAINT: {
				long code = table.callWindowProc (hwnd, msg, wParam, lParam);
				paint ();
				return code;
			}
			case OS.WM_NCHITTEST: {
				long code = table.callWindowProc (hwnd, msg, wParam, lParam);
				int x = (short) (lParam & 0xFFFF), y = (short) (lParam >> 16);
				return columnAt (x, y) != -1 ? OS.HTBORDER : code;
			}
			case OS.WM_NCLBUTTONDOWN: {
				int x = (short) (lParam & 0xFFFF), y = (short) (lParam >> 16);
				int column = columnAt (x, y);
				if (column < 0) return TableViewport.PASS;
				open (column, x, y);
				return 0;
			}
			case OS.WM_PAINT: {
				/* the columns moved or were scrolled since the strip was drawn: draw it again after this paint */
				if (layout () != drawnLayout) changed ();
				return TableViewport.PASS;
			}
		}
		return TableViewport.PASS;
	}

	/* the horizontal scroll and the column widths, folded into one number */
	private long layout () {
		long hwnd = table.handle;
		SCROLLINFO info = new SCROLLINFO ();
		info.cbSize = SCROLLINFO.sizeof;
		info.fMask = OS.SIF_POS;
		OS.GetScrollInfo (hwnd, OS.SB_HORZ, info);
		long hash = info.nPos;
		for (int column = 0; column < table.columnCount; column++) {
			hash = hash * 31 + OS.SendMessage (hwnd, OS.LVM_GETCOLUMNWIDTH, column, 0);
		}
		if (table.hwndHeader != 0 && table.columnCount > 0) {
			int [] order = new int [table.columnCount];
			OS.SendMessage (hwnd, OS.LVM_GETCOLUMNORDERARRAY, order.length, order);
			for (int index : order) hash = hash * 31 + index;
		}
		return hash;
	}

	/* ---------------------------------------------------------------- paint */

	/** Draws the strip into the window's frame; nothing while the Table is hidden. */
	void paint () {
		if (viewport == null || !model.shown () || viewport.hidden ()) return;
		RECT strip = strip ();
		if (strip == null) return;
		long hwnd = table.handle;
		drawnLayout = layout ();
		long hDC = OS.GetWindowDC (hwnd);
		try {
			OS.FillRect (hDC, strip, OS.GetSysColorBrush (OS.COLOR_BTNFACE));
			RECT line = new RECT ();
			OS.SetRect (line, strip.left, strip.top, strip.right, strip.top + 1);
			OS.FillRect (hDC, line, OS.GetSysColorBrush (OS.COLOR_3DSHADOW));
			long font = OS.SendMessage (hwnd, OS.WM_GETFONT, 0, 0);
			long oldFont = font != 0 ? OS.SelectObject (hDC, font) : 0;
			OS.SetBkMode (hDC, OS.TRANSPARENT);
			OS.SetTextColor (hDC, OS.GetSysColor (OS.COLOR_BTNTEXT));
			int inset = Table.INSET;
			if (model.visible) {
				for (int column = 0; column < table.columnCount; column++) {
					RECT cell = cell (strip, column);
					String text = cell == null ? "" : model.text (column); //$NON-NLS-1$
					if (text.isEmpty ()) continue;
					cell.left += inset;
					cell.right -= inset;
					draw (hDC, text, cell, OS.DT_RIGHT);
				}
			}
			if (model.bar != 0) {
				RECT bar = new RECT ();
				int top = strip.top + 1 + (model.visible ? rowHeight () : 0);
				OS.SetRect (bar, strip.left + inset, top, strip.right - inset, top + rowHeight ());
				draw (hDC, model.barText (), bar, OS.DT_LEFT);
			}
			if (oldFont != 0) OS.SelectObject (hDC, oldFont);
		} finally {
			OS.ReleaseDC (hwnd, hDC);
		}
	}

	private static void draw (long hDC, String text, RECT rect, int align) {
		char [] chars = text.toCharArray ();
		OS.DrawText (hDC, chars, chars.length, rect, align | OS.DT_SINGLELINE | OS.DT_VCENTER | OS.DT_NOPREFIX | DT_END_ELLIPSIS);
	}

	/* ---------------------------------------------------------------- popup */

	/** The footer popup of a column at a screen point: its breakdown, and copy. */
	void open (int column, int x, int y) {
		if (popup != null && !popup.isDisposed ()) popup.dispose ();
		String text = model.breakdown (column);
		Shell shell = popup = new Shell (table.getShell (), SWT.ON_TOP | SWT.TOOL | SWT.BORDER);
		shell.setLayout (new org.eclipse.swt.layout.GridLayout (1, false));
		Label label = new Label (shell, SWT.NONE);
		String name = column < table.columnCount ? table.columns [column].getText () : ""; //$NON-NLS-1$
		label.setText (name.isEmpty () ? "Column " + (column + 1) : name); //$NON-NLS-1$
		Text values = new Text (shell, SWT.MULTI | SWT.READ_ONLY);
		values.setText (text);
		Button copy = new Button (shell, SWT.PUSH);
		copy.setText ("Copy"); //$NON-NLS-1$
		copy.addListener (SWT.Selection, event -> {
			Clipboard clipboard = new Clipboard (table.display);
			try {
				clipboard.setContents (new Object [] {text}, new Transfer [] {TextTransfer.getInstance ()});
			} finally {
				clipboard.dispose ();
			}
		});
		shell.addListener (SWT.Deactivate, event -> shell.dispose ());
		shell.addListener (SWT.Traverse, event -> {
			if (event.detail == SWT.TRAVERSE_ESCAPE) shell.dispose ();
		});
		shell.pack ();
		Point size = shell.getSize ();
		Point at = Win32DPIUtils.pixelToPointAsLocation (new Point (x, y), table.getAutoscalingZoom ());
		shell.setLocation (at.x, at.y - size.y);
		shell.open ();
	}

	void release () {
		if (popup != null && !popup.isDisposed ()) popup.dispose ();
		popup = null;
	}
}
