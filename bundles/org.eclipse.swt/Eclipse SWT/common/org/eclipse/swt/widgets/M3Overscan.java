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

/**
 * Overscan of <code>Table</code>, <code>List</code> and <code>Tree</code> (enhanced M3 API): the rows before and
 * after the rows in view that the widget must also hold ready to paint.
 * <p>
 * {@link M3Residency#residentRange(Table)} is the rows in view widened by the overscan and clamped to the rows, and
 * {@link M3Prefetch} announces that window (widened again by its own margins). The answer is the same in every widget
 * mode (the mode-neutral contract): it is computed from public state; a viewport may keep more rows than it.
 * Setting the overscan checks the prefetch window at once. The default is no overscan.
 * </p>
 * <p>
 * The window is TanStack Virtual's range extractor (<code>max (start - overscan, 0)</code> to
 * <code>min (end + overscan, count - 1)</code>), with a margin on each side as the Tree viewport's resident plan has.
 * </p>
 *
 * @since 3.138
 */
public final class M3Overscan {

	private static final String KEY = "org.eclipse.swt.widgets.M3Overscan"; //$NON-NLS-1$

	private M3Overscan () {
	}

	/**
	 * Sets the overscan of the table.
	 *
	 * @param table the table
	 * @param rowsBefore rows above the rows in view
	 * @param rowsAfter rows below the rows in view
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a count is negative</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void set (Table table, int rowsBefore, int rowsAfter) {
		put (table, rowsBefore, rowsAfter);
	}

	/**
	 * Sets the overscan of the list.
	 *
	 * @param list the list
	 * @param rowsBefore rows above the rows in view
	 * @param rowsAfter rows below the rows in view
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a count is negative</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static void set (List list, int rowsBefore, int rowsAfter) {
		put (list, rowsBefore, rowsAfter);
	}

	/**
	 * Sets the overscan of the tree, in visible rows (as numbered by {@link M3RowMath}).
	 *
	 * @param tree the tree
	 * @param rowsBefore visible rows above the rows in view
	 * @param rowsAfter visible rows below the rows in view
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a count is negative</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static void set (Tree tree, int rowsBefore, int rowsAfter) {
		put (tree, rowsBefore, rowsAfter);
	}

	/**
	 * Returns the overscan of the table as <code>{rowsBefore, rowsAfter}</code>.
	 *
	 * @param table the table
	 * @return a new array of the two counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static int [] get (Table table) {
		return pair (table);
	}

	/**
	 * Returns the overscan of the list as <code>{rowsBefore, rowsAfter}</code>.
	 *
	 * @param list the list
	 * @return a new array of the two counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static int [] get (List list) {
		return pair (list);
	}

	/**
	 * Returns the overscan of the tree as <code>{rowsBefore, rowsAfter}</code>.
	 *
	 * @param tree the tree
	 * @return a new array of the two counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static int [] get (Tree tree) {
		return pair (tree);
	}

	private static int [] pair (Control widget) {
		check (widget);
		long packed = packed (widget);
		return new int [] {(int) (packed >>> 32), (int) packed};
	}

	/* the rows in view [first, last] widened by the overscan and clamped to [0, count) */
	static M3ItemRange widen (Control widget, M3ItemRange visible, long count) {
		long packed = packed (widget);
		if (visible.isEmpty () || packed == 0) return visible;
		int first = (int) Math.max (0, visible.start () - (packed >>> 32));
		long last = Math.min (count - 1, (long) visible.end () + (int) packed);
		return M3ItemRange.of (first, Math.toIntExact (last));
	}

	private static long packed (Widget widget) {
		return widget.getData (KEY) instanceof Long packed ? packed.longValue () : 0;
	}

	private static void put (Control widget, int before, int after) {
		check (widget);
		if (before < 0 || after < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		long packed = (long) before << 32 | after & 0xFFFFFFFFL;
		widget.setData (KEY, packed == 0 ? null : Long.valueOf (packed));
		M3Prefetch.announce (widget);
	}

	private static void check (Widget widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
	}
}
