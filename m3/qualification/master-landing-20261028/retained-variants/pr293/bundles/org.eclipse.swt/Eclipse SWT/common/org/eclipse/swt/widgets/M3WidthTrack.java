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
import org.eclipse.swt.m3.*;

/**
 * Content widths of <code>List</code> and <code>Table</code> columns (enhanced M3 API).
 * <p>
 * The content width of a list is the widest item text, and of a table column the widest cell text
 * drawn with the cell's font (<code>TableItem.getFont (int)</code>), each measured as
 * <code>GC.textExtent (text).x</code>. The widest row is the first row that has that width. Each
 * widget keeps an {@link M3TextMeasure}: a text and font pair is measured once, so asking again
 * after edits measures only the texts that are new, and asking again without edits measures
 * nothing. The answers come from the public item API and are the same in every startup mode. A
 * virtual table is asked for the text of every row, as by <code>getItems</code>.
 * </p>
 *
 * @since 3.138
 */
public final class M3WidthTrack {
	private static final String KEY = "org.eclipse.swt.widgets.M3WidthTrack"; //$NON-NLS-1$

	private M3WidthTrack () {
	}

	/**
	 * Returns the width of the widest item text of the list.
	 *
	 * @param list the list
	 * @return the width in points, 0 without items
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static int contentWidth (List list) {
		return width (scan (list));
	}

	/**
	 * Returns the first row of the list whose text has the content width.
	 *
	 * @param list the list
	 * @return the row, or -1 without items
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static int widestRow (List list) {
		return row (scan (list));
	}

	/**
	 * Returns the width of the widest cell text of a table column, each drawn with its cell's font.
	 *
	 * @param table the table
	 * @param column the column index (0 for a table without columns)
	 * @return the width in points, 0 without rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the table</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static int contentWidth (Table table, int column) {
		return width (scan (table, column));
	}

	/**
	 * Returns the first row of a table column whose cell text has the content width.
	 *
	 * @param table the table
	 * @param column the column index (0 for a table without columns)
	 * @return the row, or -1 without rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the table</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static int widestRow (Table table, int column) {
		return row (scan (table, column));
	}

	/**
	 * Forgets the measured widths of the current texts of rows of the list, so that they are measured
	 * again (for example after the font of the list changed the way a text draws).
	 *
	 * @param list the list
	 * @param rows the rows; rows outside the list are ignored
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list or the rows are null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static void invalidate (List list, M3ItemRange rows) {
		check (list, rows);
		M3TextMeasure measure = measure (list);
		int last = Math.min (rows.end (), list.getItemCount () - 1);
		for (int row = rows.start (); row <= last; row++) measure.forget (list.getItem (row), list.getFont ());
	}

	/**
	 * Forgets the measured widths of the current cell texts of rows of the table, in every column.
	 *
	 * @param table the table
	 * @param rows the rows; rows outside the table are ignored
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table or the rows are null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void invalidate (Table table, M3ItemRange rows) {
		check (table, rows);
		M3TextMeasure measure = measure (table);
		int last = Math.min (rows.end (), table.getItemCount () - 1), columns = Math.max (1, table.getColumnCount ());
		for (int row = rows.start (); row <= last; row++) {
			TableItem item = table.getItem (row);
			for (int column = 0; column < columns; column++) measure.forget (item.getText (column), item.getFont (column));
		}
	}

	/**
	 * Returns the measuring counts of the widget, indexed by <code>M3TextMeasure.MEASURED</code>,
	 * <code>HITS</code> and <code>ENTRIES</code>; zeros before the first question.
	 *
	 * @param widget the list or table
	 * @return the counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the widget is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public static long [] stats (Control widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		return widget.getData (KEY) instanceof M3TextMeasure measure ? measure.stats () : new long [3];
	}

	/* ------------------------------------------------------------------ scans */

	/* the widest row and its width, packed: row in the high int (-1 without rows), width in the low int */
	private static long scan (List list) {
		check (list, M3ItemRange.EMPTY);
		M3TextMeasure measure = measure (list);
		String [] items = list.getItems ();
		Font font = list.getFont ();
		long best = pack (-1, 0);
		try {
			for (int row = 0; row < items.length; row++) best = widest (best, row, measure.width (items [row], font));
		} finally {
			measure.release ();
		}
		return best;
	}

	private static long scan (Table table, int column) {
		check (table, M3ItemRange.EMPTY);
		if (column < 0 || column >= Math.max (1, table.getColumnCount ())) SWT.error (SWT.ERROR_INVALID_RANGE);
		M3TextMeasure measure = measure (table);
		int count = table.getItemCount ();
		long best = pack (-1, 0);
		try {
			for (int row = 0; row < count; row++) {
				TableItem item = table.getItem (row);
				best = widest (best, row, measure.width (item.getText (column), item.getFont (column)));
			}
		} finally {
			measure.release ();
		}
		return best;
	}

	private static long widest (long best, int row, int width) {
		return row (best) < 0 || width > width (best) ? pack (row, width) : best;
	}

	private static long pack (int row, int width) {
		return (long) row << 32 | (width & 0xFFFFFFFFL);
	}

	private static int row (long packed) {
		return (int) (packed >> 32);
	}

	private static int width (long packed) {
		return (int) packed;
	}

	private static M3TextMeasure measure (Control widget) {
		if (widget.getData (KEY) instanceof M3TextMeasure measure) return measure;
		M3TextMeasure measure = new M3TextMeasure (widget);
		widget.setData (KEY, measure);
		return measure;
	}

	private static void check (Control widget, M3ItemRange rows) {
		if (widget == null || rows == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
	}
}
