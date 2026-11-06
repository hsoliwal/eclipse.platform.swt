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
 * The drawn plane of a win32 Table in the <code>custom</code>, <code>m3</code> and <code>gpu</code> modes
 * (docs/m3/RENDER_MODES.md; see {@link DrawnPlane}). The ListView is the behaviour plane. Rows are read with its
 * geometry (row, label, selection and icon rectangles, header items) and its state (selection, focus); cell
 * content comes from the row's facade as the Table's own custom draw sees it: a live facade, the viewport's paint
 * facade loaded from its lanes, or none.
 */
final class TableDrawnPlane extends DrawnPlane {
	/** The Table's startup mode, read once: a drawn plane exists only for <code>custom</code>, <code>m3</code> and <code>gpu</code>. */
	static final WidgetMode MODE = WidgetMode.of ("Table", WidgetMode.VIEWPORT, WidgetMode.CUSTOM, WidgetMode.M3, WidgetMode.GPU);
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.widgets.Table");

	final Table table;
	private final RECT bounds = new RECT (), label = new RECT (), select = new RECT (), icon = new RECT (), header = new RECT (), item = new RECT ();
	private final int [] stateWidth = new int [1], stateHeight = new int [1];
	/*
	 * Where the ListView's text box ends left of a cell's right edge, in the first column and in the others: the
	 * column widths at which stock 3.135.0 stops cutting a text with "..." (ListViewPaddingProbe, Windows 11, 100 %).
	 */
	static final int FIRST_TEXT_END = 4, TEXT_END = 8;
	/* GRID_LINES v1: COLUMN_DETAIL for every row of a frame of a Table with columns (the themed report view draws its column detail at every column edge) */
	private int detail;
	/* the theme's column detail colour, read once (-2: not read yet) */
	private int columnDetail = -2;

	private TableDrawnPlane (Table table) {
		super (table, MODE, "Table", Table.TableProc, Display.TREEVIEW);
		this.table = table;
		LOG.debug (() -> "Table drawn plane: mode " + MODE + ", theme " + renderer.theme ().name + ", backend " + canvas.getClass ().getSimpleName ());
	}

	/** The drawn plane of a new Table, or null when the Table's mode does not draw (viewport, original). */
	static TableDrawnPlane install (Table table) {
		return drawn (MODE) ? new TableDrawnPlane (table) : null;
	}

	@Override
	void afterBehaviour (int msg) {
		/* as Table.callWindowProc does after the native WM_PAINT */
		if (msg == OS.WM_PAINT) table.painted = true;
	}

	/* the header is a child window over the top of the client area: rows start below it */
	@Override
	boolean clipDamage (long hwnd) {
		long hwndHeader = OS.SendMessage (hwnd, OS.LVM_GETHEADER, 0, 0);
		rowsTop = 0;
		if (hwndHeader != 0 && OS.IsWindowVisible (hwndHeader)) {
			OS.GetWindowRect (hwndHeader, header);
			OS.MapWindowPoints (0, hwnd, header, 2);
			rowsTop = header.bottom;
			damage.top = Math.max (damage.top, header.bottom);
		}
		return damage.top < damage.bottom;
	}

	@Override
	void drawRows (long hwnd) {
		frameDefaults (hwnd);
		int count = (int) OS.SendMessage (hwnd, OS.LVM_GETITEMCOUNT, 0, 0);
		int top = (int) OS.SendMessage (hwnd, OS.LVM_GETTOPINDEX, 0, 0);
		int bottom = damage.top;
		if (count > 0 && rect (hwnd, top, OS.LVIR_BOUNDS, bounds)) {
			int height = Math.max (1, bounds.bottom - bounds.top);
			int focus = (int) OS.SendMessage (hwnd, OS.LVM_GETNEXTITEM, -1, OS.LVNI_FOCUSED);
			for (int index = top + Math.max (0, (damage.top - bounds.top) / height); index < count; index++) {
				if (!rect (hwnd, index, OS.LVIR_BOUNDS, bounds) || bounds.top >= damage.bottom) break;
				fillRow (hwnd, index, focus);
				paintRow ();
				bottom = bounds.bottom;
			}
		}
		if (bottom < damage.bottom) emptyArea (hwnd, bottom);
	}

	/* the empty area below the rows; GRID_LINES v1: with no row painted it takes the columns from the header first */
	private void emptyArea (long hwnd, int bottom) {
		if (bottom == damage.top) emptyColumns (hwnd);
		paintEmpty (damage.left, bottom, damage.right - damage.left, damage.bottom - bottom);
	}

	/* GRID_LINES v1: with no row painted in this frame, the empty area takes the column geometry and state from the header */
	private void emptyColumns (long hwnd) {
		row.state = detail;
		row.x = client.left;
		if (table.columnCount > 0) columns (hwnd, -1, null);
	}

	/*
	 * The facade the Table's own custom draw sees for a row: a live facade, or (viewport) the paint facade loaded
	 * from the lanes while painting, or none. No facade is created, unless a MeasureItem, EraseItem or PaintItem
	 * listener is about to be handed the item (as Table.CDDS_SUBITEMPREPAINT forces one then).
	 */
	private TableItem facade (int index) {
		boolean create = ownerDraw.hooked ();
		TableViewport viewport = table.viewport;
		if (viewport == null) return table._getItem (index, create);
		viewport.painting++;
		try {
			return viewport.facade (index, create);
		} finally {
			viewport.painting--;
		}
	}

	@Override
	boolean ignoreNativeCustomDraw (boolean ignore) {
		boolean previous = table.ignoreCustomDraw;
		table.ignoreCustomDraw = ignore;
		return previous;
	}

	/* as Table.sendMeasureItemEvent applies the listener's bounds: the scroll width of a column-less table, the item height */
	@Override
	void measured (int x, int width, int height) {
		if (table.columnCount == 0) {
			int columnWidth = (int) OS.SendMessage (table.handle, OS.LVM_GETCOLUMNWIDTH, 0, 0);
			if (x + width > columnWidth) table.setScrollWidth (x + width);
		}
		if (!table.settingItemHeight && height > table.getItemHeightInPixels ()) {
			table.settingItemHeight = true;
			table.setItemHeight (height);
			table.settingItemHeight = false;
		}
	}

	/**
	 * Paints like {@link DrawnPlane#paint(long, int, long, long)}, then lets the frame repaint when the Table took
	 * the scroll bars that Windows added off the style before the paint (Table.callWindowProc).
	 */
	long paint (long hwnd, int msg, long wParam, long lParam, boolean fixScroll) {
		long code = paint (hwnd, msg, wParam, lParam);
		if (fixScroll) OS.RedrawWindow (table.handle, null, 0, OS.RDW_FRAME | OS.RDW_INVALIDATE);
		return code;
	}

	private static boolean rect (long hwnd, int index, int code, RECT rect) {
		rect.left = code;
		return OS.SendMessage (hwnd, OS.LVM_GETITEMRECT, index, rect) != 0;
	}

	private void frameDefaults (long hwnd) {
		row.foreground = RowPaint.rgbOf (table.getForegroundPixel ());
		row.background = RowPaint.rgbOf (table.getBackgroundPixel ());
		row.font = OS.SendMessage (hwnd, OS.WM_GETFONT, 0, 0);
		row.sortBackground = table.sortColumn != null && table.sortDirection != SWT.NONE ? RowPaint.rgbOf (table.getSortColumnPixel ()) : -1;
		/* the ListView's grid: lines in COLOR_3DFACE, the theme's column detail line */
		if (columnDetail == -2) columnDetail = ThemeParts.listColumnDetail (hwnd);
		row.listGrid = RowPaint.rgbOf (OS.GetSysColor (OS.COLOR_3DFACE));
		row.listColumnDetail = columnDetail;
		/* ROW_IMAGE_BOUNDS v1: the ListView draws every image at its image list's size, centred in the row */
		row.imageHeight = imageListHeight (OS.SendMessage (hwnd, OS.LVM_GETIMAGELIST, OS.LVSIL_SMALL, 0));
		detail = table.columnCount > 0 ? RowPaint.COLUMN_DETAIL : 0;
		long states = OS.SendMessage (hwnd, OS.LVM_GETIMAGELIST, OS.LVSIL_STATE, 0);
		stateWidth [0] = 0;
		if (states != 0) OS.ImageList_GetIconSize (states, stateWidth, stateHeight);
	}

	private void fillRow (long hwnd, int index, int focus) {
		/* the facade the Table's custom draw would see; none is created here */
		TableItem facade = facade (index);
		rect (hwnd, index, OS.LVIR_LABEL, label);
		rect (hwnd, index, OS.LVIR_SELECTBOUNDS, select);
		rect (hwnd, index, OS.LVIR_ICON, icon);
		row.x = bounds.left;
		row.y = bounds.top;
		row.width = Math.max (bounds.right, client.right) - bounds.left;
		row.height = bounds.bottom - bounds.top;
		row.index = index;
		rowItem = facade;
		row.state = state (hwnd, index, focus, facade);
		row.expanderWidth = 0;
		row.checkWidth = row.is (RowPaint.CHECKBOX) ? stateWidth [0] : 0;
		row.checkX = icon.left - row.checkWidth;
		if (table.columnCount > 0) {
			columns (hwnd, index, facade);
		} else {
			firstColumnOnly (hwnd, index, facade);
		}
	}

	private int state (long hwnd, int index, int focus, TableItem facade) {
		int bits = (int) OS.SendMessage (hwnd, OS.LVM_GETITEMSTATE, index, OS.LVIS_SELECTED | OS.LVIS_DROPHILITED);
		/* the ListView ends cell text that does not fit with "..." */
		int state = RowPaint.ELLIPSIS | detail;
		if ((bits & OS.LVIS_SELECTED) != 0) state |= RowPaint.SELECTED;
		if ((bits & OS.LVIS_DROPHILITED) != 0) state |= RowPaint.DROP;
		if (index == focus) state |= RowPaint.CARET;
		if (OS.GetFocus () == hwnd) state |= RowPaint.FOCUSED;
		if ((OS.SendMessage (hwnd, OS.WM_QUERYUISTATE, 0, 0) & OS.UISF_HIDEFOCUS) == 0) state |= RowPaint.FOCUS_CUES;
		if ((table.style & SWT.FULL_SELECTION) != 0) state |= RowPaint.FULL_SELECTION;
		if ((OS.SendMessage (hwnd, OS.LVM_GETEXTENDEDLISTVIEWSTYLE, 0, 0) & OS.LVS_EX_GRIDLINES) != 0) state |= RowPaint.GRID_LINES;
		if ((table.style & SWT.CHECK) != 0) {
			state |= RowPaint.CHECKBOX;
			if (facade != null && facade.checked) state |= RowPaint.CHECKED;
			if (facade != null && facade.grayed) state |= RowPaint.GRAYED;
		}
		return state;
	}

	/* a Table without columns: the ListView's single column */
	private void firstColumnOnly (long hwnd, int index, TableItem facade) {
		row.cells (1);
		row.sortCell = -1;
		cellColumn [0] = 0;
		int width = (int) OS.SendMessage (hwnd, OS.LVM_GETCOLUMNWIDTH, 0, 0);
		row.cellX [0] = bounds.left;
		row.cellWidth [0] = width;
		text (0, 0, facade, label.left, label.right - FIRST_TEXT_END);
		selection (bounds.left + width);
	}

	/* a Table with columns: one cell per column in display order, from the header items */
	private void columns (long hwnd, int index, TableItem facade) {
		long hwndHeader = OS.SendMessage (hwnd, OS.LVM_GETHEADER, 0, 0);
		OS.GetWindowRect (hwndHeader, header);
		OS.MapWindowPoints (0, hwnd, header, 2);
		int count = table.columnCount, right = row.x;
		row.cells (count);
		row.sortCell = -1;
		cellColumns (count);
		for (int order = 0; order < count; order++) {
			int column = (int) OS.SendMessage (hwndHeader, OS.HDM_ORDERTOINDEX, order, 0);
			cellColumn [order] = column;
			if (OS.SendMessage (hwndHeader, OS.HDM_GETITEMRECT, column, item) == 0) continue;
			int left = header.left + item.left, end = header.left + item.right;
			row.cellX [order] = left;
			row.cellWidth [order] = end - left;
			right = Math.max (right, end);
			row.align [order] = table.columns [column].style & (SWT.LEFT | SWT.CENTER | SWT.RIGHT);
			if (table.columns [column] == table.sortColumn) row.sortCell = order;
			if (column == 0) {
				text (column, order, facade, label.left, Math.min (label.right, end) - FIRST_TEXT_END);
			} else {
				text (column, order, facade, left + Table.INSET, end - TEXT_END);
			}
		}
		selection (right);
	}

	private void text (int column, int cell, TableItem facade, int left, int right) {
		row.textX [cell] = left;
		row.textWidth [cell] = Math.max (0, right - left);
		if (facade == null) return;
		row.text [cell] = column == 0 ? facade.text : facade.strings != null && column < facade.strings.length ? facade.strings [column] : null;
		int foreground = facade.cellForeground != null && column < facade.cellForeground.length ? facade.cellForeground [column] : -1;
		int background = facade.cellBackground != null && column < facade.cellBackground.length ? facade.cellBackground [column] : -1;
		row.cellForeground [cell] = RowPaint.rgbOf (foreground != -1 ? foreground : facade.foreground);
		row.cellBackground [cell] = RowPaint.rgbOf (background != -1 ? background : facade.background);
		row.cellFont [cell] = facade.fontHandle (column);
		Image image = column == 0 ? facade.image : facade.images != null && column < facade.images.length ? facade.images [column] : null;
		if (image == null) return;
		RECT box = facade.getBounds (row.index, column, false, true, false, false, 0);
		row.image [cell] = image;
		row.imageX [cell] = box.left;
		row.imageWidth [cell] = box.right - box.left;
		if (column != 0) {
			int end = row.textX [cell] + row.textWidth [cell];
			row.textX [cell] = box.right + Table.INSET;
			row.textWidth [cell] = Math.max (0, end - row.textX [cell]);
		}
	}

	private void selection (int right) {
		boolean full = row.is (RowPaint.FULL_SELECTION);
		row.selectionX = full ? row.x : select.left;
		row.selectionWidth = full ? right - row.x : select.right - select.left;
	}
}
