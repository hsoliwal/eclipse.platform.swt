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
package org.eclipse.swt.internal;

import java.text.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * A footer row for a Table or Tree: the value of each column's {@link ColumnSummary} function,
 * painted under the column.
 * <p>
 * Place the row below the control with any layout. Each value is painted at its column's position
 * and width, with the column's alignment, and follows the column order, the control's horizontal
 * scrolling and where the row and the control are (a move of either). A value that does not fit is
 * shortened with an ellipsis. A value is computed only when the row paints its column or
 * {@link #getText(int)} asks for it, so a change repaints the columns it touched and a row that is
 * not painted computes nothing.
 * </p>
 * <p>
 * The row's menu, asked for over a column, chooses that column's function; {@link #fillMenu(Menu, int)}
 * puts the same choices into another menu. Call {@link #refresh()} after columns are created or
 * disposed.
 * </p>
 *
 * @see ColumnSummary
 * @see FilterRow
 * @since 3.138
 */
public final class FooterRow extends Composite {

	static final int MARGIN = 4;
	static final String ELLIPSIS = "..."; //$NON-NLS-1$
	static final String VALUE = "SWT_Summary_Value"; //$NON-NLS-1$
	/* the label of each function, indexed by the function */
	static final String [] LABELS = {"SWT_Summary_None", "SWT_Summary_Count", "SWT_Summary_Sum", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		"SWT_Summary_Minimum", "SWT_Summary_Maximum", "SWT_Summary_Average"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private final Composite control;
	private final ColumnSummary summary;
	private final Listener listener = this::handleEvent;
	private final ColumnSummary.Listener follower = this::summaryChanged;
	private final Format [] formats;
	private final Format wholeFormat = NumberFormat.getIntegerInstance ();
	private final Format numberFormat = NumberFormat.getNumberInstance ();
	private int hooked;

	/**
	 * Constructs a footer row for a table.
	 *
	 * @param parent the parent of the row
	 * @param table the table whose columns the row follows
	 * @param summary the summary of the table's columns
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if an argument is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
	 * </ul>
	 */
	public FooterRow (Composite parent, Table table, ColumnSummary summary) {
		this (parent, (Composite) RowChecks.nonNull (table), summary);
	}

	/**
	 * Constructs a footer row for a tree.
	 *
	 * @param parent the parent of the row
	 * @param tree the tree whose columns the row follows
	 * @param summary the summary of the tree's columns
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if an argument is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
	 * </ul>
	 */
	public FooterRow (Composite parent, Tree tree, ColumnSummary summary) {
		this (parent, (Composite) RowChecks.nonNull (tree), summary);
	}

	private FooterRow (Composite parent, Composite control, ColumnSummary summary) {
		super (parent, SWT.DOUBLE_BUFFERED);
		this.control = control;
		this.summary = RowChecks.nonNull (summary);
		formats = new Format [summary.getColumnCount ()];
		for (int type : new int [] {SWT.Resize, SWT.Move, SWT.Dispose}) {
			control.addListener (type, listener);
			addListener (type, listener);
		}
		ScrollBar bar = control.getHorizontalBar ();
		if (bar != null) bar.addListener (SWT.Selection, listener);
		addListener (SWT.Paint, listener);
		addListener (SWT.MenuDetect, listener);
		setMenu (new Menu (this));
		summary.addListener (follower);
		refresh ();
	}

	/**
	 * Follows the columns created since the last refresh and paints the row again.
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the row has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the row</li>
	 * </ul>
	 */
	public void refresh () {
		checkWidget ();
		hooked = count ();
		for (int i = 0; i < hooked; i++) {
			Item column = Columns.column (control, i);
			column.removeListener (SWT.Resize, listener);
			column.removeListener (SWT.Move, listener);
			column.addListener (SWT.Resize, listener);
			column.addListener (SWT.Move, listener);
		}
		redraw ();
	}

	/**
	 * Sets the format of a column's sum, minimum, maximum and average. A count is always shown as a
	 * whole number.
	 *
	 * @param column the column
	 * @param format the format, or <code>null</code> for the number format of the default locale
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the summary</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the row has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the row</li>
	 * </ul>
	 */
	public void setFormat (int column, Format format) {
		checkWidget ();
		formats [RowChecks.range (column, formats.length)] = format;
		redrawColumn (column);
	}

	/**
	 * Returns the text the row shows for a column: the function's label and the value, or an empty
	 * text when the column has no function or no value.
	 *
	 * @param column the column
	 * @return the text
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the summary</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the row has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the row</li>
	 * </ul>
	 */
	public String getText (int column) {
		checkWidget ();
		int function = summary.getFunction (column);
		if (function == ColumnSummary.NONE) return ""; //$NON-NLS-1$
		double value = summary.getValue (column);
		if (Double.isNaN (value)) return ""; //$NON-NLS-1$
		Format format = function == ColumnSummary.COUNT ? wholeFormat : formats [column] != null ? formats [column] : numberFormat;
		return SWT.getMessage (VALUE, new Object [] {SWT.getMessage (LABELS [function]), format.format (value)});
	}

	/**
	 * Replaces the items of a menu by one radio item per function, the column's function selected.
	 * Selecting an item sets the column's function in the summary.
	 *
	 * @param menu the menu to fill
	 * @param column the column
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the menu is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the summary</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the row or the menu has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the row</li>
	 * </ul>
	 */
	public void fillMenu (Menu menu, int column) {
		checkWidget ();
		if (menu == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		int current = summary.getFunction (column);
		for (MenuItem item : menu.getItems ()) item.dispose ();
		for (int function = ColumnSummary.NONE; function <= ColumnSummary.AVERAGE; function++) {
			MenuItem item = new MenuItem (menu, SWT.RADIO);
			int chosen = function;
			item.setText (SWT.getMessage (LABELS [function]));
			item.setSelection (function == current);
			item.addListener (SWT.Selection, event -> {
				if (item.getSelection ()) summary.setFunction (column, chosen);
			});
		}
	}

	@Override
	public Point computeSize (int wHint, int hHint, boolean changed) {
		checkWidget ();
		GC gc = new GC (this);
		int height = gc.getFontMetrics ().getHeight () + 2 * MARGIN + 1;
		gc.dispose ();
		return new Point (wHint == SWT.DEFAULT ? control.getSize ().x : wHint, hHint == SWT.DEFAULT ? height : hHint);
	}

	private void handleEvent (Event event) {
		switch (event.type) {
			case SWT.Dispose -> disposed (event);
			case SWT.Paint -> paint (event);
			case SWT.MenuDetect -> menuDetect (event);
			default -> {
				if (!isDisposed ()) redraw ();
			}
		}
	}

	/* the columns of the control that the summary has */
	private int count () {
		return Math.min (Columns.count (control), summary.getColumnCount ());
	}

	/* the x of the control's first column in the row's coordinates */
	private int origin () {
		return toControl (control.toDisplay (0, 0)).x + Columns.origin (control);
	}

	/* the line on top, then each column the damage touches, in display order */
	private void paint (Event event) {
		GC gc = event.gc;
		Rectangle area = getClientArea ();
		gc.setForeground (getDisplay ().getSystemColor (SWT.COLOR_WIDGET_NORMAL_SHADOW));
		gc.drawLine (area.x, area.y, area.x + area.width - 1, area.y);
		gc.setForeground (getForeground ());
		int left = origin (), count = count ();
		for (int column : Columns.order (control)) {
			int width = Columns.width (Columns.column (control, column));
			if (column < count && left < event.x + event.width && left + width > event.x) paintCell (gc, column, left, width, area);
			left += width;
		}
		gc.setClipping ((Rectangle) null);
	}

	private void paintCell (GC gc, int column, int left, int width, Rectangle area) {
		int room = width - 2 * MARGIN;
		if (room <= 0) return;
		String text = getText (column);
		if (text.isEmpty ()) return;
		text = shorten (gc, text, room);
		int extent = gc.textExtent (text).x, x = left + MARGIN, y = area.y + 1 + (area.height - 1 - gc.getFontMetrics ().getHeight ()) / 2;
		int alignment = alignment (Columns.column (control, column));
		if (alignment == SWT.RIGHT) x += room - extent;
		else if (alignment == SWT.CENTER) x += (room - extent) / 2;
		gc.setClipping (left + MARGIN, area.y + 1, room, area.height - 1);
		gc.drawString (text, x, y, true);
	}

	/* the longest start of the text that fits with an ellipsis (Nebula Grid's footer shortens the same way) */
	static String shorten (GC gc, String text, int width) {
		if (gc.textExtent (text).x <= width) return text;
		int low = 0, high = text.length ();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (gc.textExtent (text.substring (0, mid) + ELLIPSIS).x <= width) low = mid;
			else high = mid - 1;
		}
		if (low > 0 && Character.isHighSurrogate (text.charAt (low - 1))) low--;
		return text.substring (0, low) + ELLIPSIS;
	}

	private static int alignment (Item column) {
		return column instanceof TableColumn tableColumn ? tableColumn.getAlignment () : ((TreeColumn) column).getAlignment ();
	}

	/* the menu is for the column under the pointer, or the first shown column for the keyboard */
	private void menuDetect (Event event) {
		int column = -1;
		if (event.detail == SWT.MENU_KEYBOARD) {
			for (int index : Columns.order (control)) {
				if (index < count () && Columns.width (Columns.column (control, index)) > 0) {
					column = index;
					break;
				}
			}
		} else {
			int x = toControl (event.x, event.y).x - origin () + Columns.origin (control);
			column = Columns.columnAt (control, x);
		}
		event.doit = column >= 0 && column < count ();
		if (event.doit) fillMenu (getMenu (), column);
	}

	private void summaryChanged (int column) {
		if (isDisposed ()) return;
		if (column < 0) redraw ();
		else redrawColumn (column);
	}

	private void redrawColumn (int column) {
		if (column >= count ()) return;
		Rectangle area = getClientArea ();
		int left = origin () + Columns.left (control, column) - Columns.origin (control);
		redraw (left, area.y, Columns.width (Columns.column (control, column)), area.height, false);
	}

	private void disposed (Event event) {
		if (event.widget == control) {
			if (!isDisposed ()) dispose ();
			return;
		}
		summary.removeListener (follower);
		if (control.isDisposed ()) return;
		for (int type : new int [] {SWT.Resize, SWT.Move, SWT.Dispose}) control.removeListener (type, listener);
		ScrollBar bar = control.getHorizontalBar ();
		if (bar != null) bar.removeListener (SWT.Selection, listener);
		for (int i = 0, n = Math.min (hooked, Columns.count (control)); i < n; i++) {
			Columns.column (control, i).removeListener (SWT.Resize, listener);
			Columns.column (control, i).removeListener (SWT.Move, listener);
		}
	}
}
