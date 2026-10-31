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
 * Residency views of <code>Table</code>, <code>List</code> and <code>Tree</code> (enhanced M3
 * API): which rows the widget must hold ready to paint and how many item objects exist.
 * <p>
 * The resident rows answer identically in every widget mode (the mode-neutral contract): they
 * are the rows in view, computed from public state (the top row, the item height and the client
 * area, as <code>M3ItemSelection.getVisibleRange</code>), and none while the widget is not
 * visible. Every mode holds at least these rows; how many more it keeps (a viewport's window and
 * overscan, or every row without a viewport) is the mode's own business and is not answered here.
 * The item-object counts of {@link #stats(Table)} are a census of what the mode made: with a
 * viewport the widgets make item objects (facades) on demand and hold a Table's facades strongly
 * in a ring of recent rows only, which these counts make observable for diagnostics and tests.
 * The views never make an item and never move a window.
 * </p>
 * <p>
 * The win32 widgets answer the facade counts. On other platforms they are -1 (unknown) and
 * {@link #isMaterialized(Table, int)} answers only for tables without <code>SWT.VIRTUAL</code>.
 * </p>
 *
 * @since 3.138
 */
public final class M3Residency {

	/** Index in {@link #stats(Table)}: the number of rows of the resident range. */
	public static final int RESIDENT_ROWS = 0;
	/** Index in {@link #stats(Table)}: item objects alive, strongly or weakly reachable (-1 when unknown). */
	public static final int FACADES = 1;
	/** Index in {@link #stats(Table)}: item objects the widget holds strongly (-1 when unknown). */
	public static final int HELD_FACADES = 2;

	private M3Residency () {
	}

	/**
	 * Returns the rows the table must hold ready to paint: the rows in view, none while the table is
	 * not visible. The answer is the same in every widget mode.
	 *
	 * @param table the table
	 * @return the resident rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static M3ItemRange residentRange (Table table) {
		check (table);
		return table.isVisible () ? M3ItemSelection.getVisibleRange (table) : M3ItemRange.EMPTY;
	}

	/**
	 * Returns the rows the list must hold ready to paint: the rows in view, none while the list is
	 * not visible. The answer is the same in every widget mode.
	 *
	 * @param list the list
	 * @return the resident rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static M3ItemRange residentRange (List list) {
		check (list);
		return list.isVisible () ? M3ItemSelection.getVisibleRange (list) : M3ItemRange.EMPTY;
	}

	/**
	 * Returns the visible rows (as numbered by {@link M3RowMath}) the tree must hold ready to paint:
	 * the rows in view, none while the tree is not visible. The answer is the same in every widget
	 * mode; the viewport's lanes answer it when they exist, otherwise one walk to the last row in
	 * view.
	 *
	 * @param tree the tree
	 * @return the resident visible rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 * @exception ArithmeticException if a resident row is beyond <code>Integer.MAX_VALUE</code>
	 */
	public static M3ItemRange residentRange (Tree tree) {
		check (tree);
		return window (tree, 0, 0);
	}

	/*
	 * The rows in view widened by before and after and clamped to the visible rows: from the viewport's lanes when
	 * they exist, otherwise one walk to the last row of the window (never a walk over every row).
	 */
	static M3ItemRange window (Tree tree, int before, int after) {
		TreeItem top = tree.isVisible () ? tree.getTopItem () : null;
		int header = tree.getHeaderVisible () ? tree.getHeaderHeight () : 0;
		int rows = M3ItemSelection.rows (tree.getClientArea ().height - header, tree.getItemHeight ());
		if (top == null || rows == 0) return M3ItemRange.EMPTY;
		long first = tree.m3RowOf (top), count = tree.m3VisibleRowCount (), last;
		if (first == M3RowMath.UNKNOWN || count == M3RowMath.UNKNOWN) {
			M3TreeWalk walk = new M3TreeWalk (tree);
			first = walk.indexOf (top);
			last = first + walk.advance (rows - 1 + after);
		} else {
			last = Math.min (count - 1, first + rows - 1 + after);
		}
		return M3ItemRange.of (Math.toIntExact (Math.max (0, first - before)), Math.toIntExact (last));
	}

	/**
	 * Returns whether a <code>TableItem</code> exists for the row now. Asking never makes one.
	 *
	 * @param table the table
	 * @param index the row
	 * @return <code>true</code> when the row has an item object
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the index is not a row of the table</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 *    <li>ERROR_NOT_IMPLEMENTED - for a virtual table on a platform without the residency seam</li>
	 * </ul>
	 */
	public static boolean isMaterialized (Table table, int index) {
		check (table);
		if (index < 0 || index >= table.getItemCount ()) SWT.error (SWT.ERROR_INVALID_RANGE);
		int answer = table.m3Materialized (index);
		/* only a virtual table on a platform without the residency seam answers -1 */
		if (answer < 0) SWT.error (SWT.ERROR_NOT_IMPLEMENTED);
		return answer == 1;
	}

	/**
	 * Returns the residency counts of the table, indexed by {@link #RESIDENT_ROWS},
	 * {@link #FACADES} and {@link #HELD_FACADES}. One pass over the item lanes, O(rows): for
	 * diagnostics and tests.
	 *
	 * @param table the table
	 * @return a new array of the counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static long [] stats (Table table) {
		return new long [] {residentRange (table).size (), table.m3Facades (false), table.m3Facades (true)};
	}

	/**
	 * Returns the residency counts of the list (see {@link #stats(Table)}); a list has no item
	 * objects, so both facade counts are 0.
	 *
	 * @param list the list
	 * @return a new array of the counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static long [] stats (List list) {
		return new long [] {residentRange (list).size (), 0, 0};
	}

	/**
	 * Returns the residency counts of the tree (see {@link #stats(Table)}).
	 *
	 * @param tree the tree
	 * @return a new array of the counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static long [] stats (Tree tree) {
		return new long [] {residentRange (tree).size (), tree.m3Facades (false), tree.m3Facades (true)};
	}

	private static void check (Widget widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
	}
}
