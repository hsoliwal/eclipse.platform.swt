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

/**
 * Visible-range observer of <code>Table</code>, <code>List</code> and <code>Tree</code>
 * (enhanced M3 API): which rows came into view and which left it.
 * <p>
 * A listener added here receives events of type {@link #EVENT}: first {@link #LEFT} for the rows
 * that left the visible range, then {@link #ENTERED} for the rows that entered it, each as at most
 * two ranges in <code>event.start</code> and <code>event.end</code> (inclusive; a Tree uses the
 * visible rows of {@link M3RowMath}). The visible range is
 * <code>M3ItemSelection.getVisibleRange</code>. The first listener hears the current range as
 * entered. Ranges are delivered, never items.
 * </p>
 * <p>
 * The range is checked when the widget scrolls, resizes, expands or collapses a row (once, at
 * the end of the event), and immediately when a viewport moves its window. In the original
 * mode a <code>setTopIndex</code> that is not followed by any of these is announced at the next
 * one. The win32 widgets drive the viewport checks.
 * </p>
 * <p>
 * Viewport deltas (brief rule 24): after the row ranges, a listener receives {@link #EXTENTS} when
 * the extents of the viewport changed: the size of the client area, the rows (the visible rows of a
 * Tree), the row height, or the width of the columns. The extents are checked after a resize, an
 * expand or collapse, a zoom change, a resize of a column that existed when the first
 * listener was added, and immediately after a model patch ({@link M3Model#apply}) or a bulk update;
 * scrolling alone changes no extent. The first listener hears the current extents. Together the
 * three details are the window delta: an application prefetches what entered, releases what left
 * and re-plans only on new extents, instead of polling <code>getTopIndex</code>.
 * </p>
 *
 * @since 3.138
 */
public final class M3VisibilityObserver {

	/** The event type: outside the range of the SWT event types. */
	public static final int EVENT = 0x4D330003;
	/** <code>event.detail</code>: the rows from <code>start</code> to <code>end</code> came into view. */
	public static final int ENTERED = 0;
	/** <code>event.detail</code>: the rows from <code>start</code> to <code>end</code> left the view. */
	public static final int LEFT = 1;
	/**
	 * <code>event.detail</code>: the extents of the viewport changed. <code>event.width</code> and
	 * <code>event.height</code> are the size of the client area and <code>event.count</code> the rows
	 * (the visible rows of a Tree); <code>M3Scrolling.getScrollInfo</code> answers the rest.
	 *
	 * @since 3.138
	 */
	public static final int EXTENTS = 2;

	private static final String KEY = "org.eclipse.swt.widgets.M3VisibilityObserver"; //$NON-NLS-1$

	/* The latch of a listened widget: the last announced range and extents, and the trigger listener itself. */
	private static final class Latch implements Listener {
		final Control widget;
		int first, last = -1;
		int width = -1, height = -1, rows = -1, rowExtent = -1, contentWidth = -1;
		boolean scheduled, extentsDirty = true;

		Latch (Control widget) {
			this.widget = widget;
		}

		/* a trigger: check once at the end of the current event; scrolling alone changes no extent */
		@Override
		public void handleEvent (Event event) {
			if (event.type != SWT.Selection && event.type != SWT.MouseVerticalWheel && event.type != SWT.KeyDown) extentsDirty = true;
			if (scheduled || widget.isDisposed ()) return;
			scheduled = true;
			widget.getDisplay ().asyncExec (() -> {
				scheduled = false;
				if (!widget.isDisposed ()) check (widget, this);
			});
		}
	}

	private M3VisibilityObserver () {
	}

	/**
	 * Adds a listener for the visible range of the table.
	 *
	 * @param table the table
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table or the listener is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void addListener (Table table, Listener listener) {
		add (table, listener);
	}

	/**
	 * Adds a listener for the visible range of the list.
	 *
	 * @param list the list
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list or the listener is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static void addListener (List list, Listener listener) {
		add (list, listener);
	}

	/**
	 * Adds a listener for the visible rows of the tree.
	 *
	 * @param tree the tree
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree or the listener is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static void addListener (Tree tree, Listener listener) {
		add (tree, listener);
	}

	/**
	 * Removes a listener added with {@link #addListener(Table, Listener)}.
	 *
	 * @param table the table
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table or the listener is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void removeListener (Table table, Listener listener) {
		remove (table, listener);
	}

	/**
	 * Removes a listener added with {@link #addListener(List, Listener)}.
	 *
	 * @param list the list
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list or the listener is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static void removeListener (List list, Listener listener) {
		remove (list, listener);
	}

	/**
	 * Removes a listener added with {@link #addListener(Tree, Listener)}.
	 *
	 * @param tree the tree
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree or the listener is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static void removeListener (Tree tree, Listener listener) {
		remove (tree, listener);
	}

	/* ------------------------------------------------------------------ widget seam */

	/* A viewport moved its window or the rows changed: check the visible range now. */
	static void changed (Control widget) {
		if (!widget.hooks (EVENT) || widget.isDisposed ()) return;
		Latch latch = latch (widget);
		if (latch == null) return;
		latch.extentsDirty = true;
		check (widget, latch);
	}

	/* ------------------------------------------------------------------ helpers */

	private static void check (Control widget, Latch latch) {
		M3ItemRange visible = visible (widget);
		int first = visible.isEmpty () ? 0 : visible.start (), last = visible.isEmpty () ? -1 : visible.end ();
		int oldFirst = latch.first, oldLast = latch.last;
		if (first != oldFirst || last != oldLast) {
			latch.first = first;
			latch.last = last;
			M3Prefetch.difference (widget, EVENT, LEFT, oldFirst, oldLast, first, last);
			M3Prefetch.difference (widget, EVENT, ENTERED, first, last, oldFirst, oldLast);
		}
		if (latch.extentsDirty && !widget.isDisposed ()) extents (widget, latch);
	}

	/* the extents of the viewport: EXTENTS when the client area, the rows, the row height or the column widths changed */
	private static void extents (Control widget, Latch latch) {
		latch.extentsDirty = false;
		Rectangle area = ((Scrollable) widget).getClientArea ();
		int rows, rowExtent, contentWidth = area.width;
		if (widget instanceof Table table) {
			rows = table.getItemCount ();
			rowExtent = table.getItemHeight ();
			if (table.getColumnCount () > 0) contentWidth = width (table.getColumns ());
		} else if (widget instanceof Tree tree) {
			rows = (int) Math.min (Integer.MAX_VALUE, M3RowMath.visibleRowCount (tree));
			rowExtent = tree.getItemHeight ();
			if (tree.getColumnCount () > 0) contentWidth = width (tree.getColumns ());
		} else {
			rows = ((List) widget).getItemCount ();
			rowExtent = ((List) widget).getItemHeight ();
		}
		if (area.width == latch.width && area.height == latch.height && rows == latch.rows && rowExtent == latch.rowExtent
				&& contentWidth == latch.contentWidth) return;
		latch.width = area.width;
		latch.height = area.height;
		latch.rows = rows;
		latch.rowExtent = rowExtent;
		latch.contentWidth = contentWidth;
		Event event = new Event ();
		event.detail = EXTENTS;
		event.width = area.width;
		event.height = area.height;
		event.count = rows;
		widget.sendEvent (EVENT, event);
	}

	/* the width of the columns: TableColumn and TreeColumn widths, in any order */
	private static int width (Item [] columns) {
		int sum = 0;
		for (Item column : columns) sum += column instanceof TableColumn c ? c.getWidth () : ((TreeColumn) column).getWidth ();
		return sum;
	}

	private static M3ItemRange visible (Control widget) {
		if (widget instanceof Table table) return M3ItemSelection.getVisibleRange (table);
		if (widget instanceof Tree tree) return M3ItemSelection.getVisibleRange (tree);
		return M3ItemSelection.getVisibleRange ((List) widget);
	}

	private static void add (Control widget, Listener listener) {
		if (widget == null || listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		Latch latch = latch (widget);
		if (latch == null) {
			latch = new Latch (widget);
			widget.setData (KEY, latch);
			widget.addListener (SWT.Resize, latch);
			if (widget instanceof Tree) {
				widget.addListener (SWT.Expand, latch);
				widget.addListener (SWT.Collapse, latch);
			}
			ScrollBar bar = ((Scrollable) widget).getVerticalBar ();
			if (bar != null) bar.addListener (SWT.Selection, latch);
			widget.addListener (SWT.MouseVerticalWheel, latch);
			widget.addListener (SWT.KeyDown, latch);
			widget.addListener (SWT.ZoomChanged, latch);
			for (Item column : columns (widget)) {
				column.addListener (SWT.Resize, latch);
			}
		}
		widget.addListener (EVENT, listener);
		check (widget, latch);
	}

	private static Item [] columns (Control widget) {
		if (widget instanceof Table table) return table.getColumns ();
		if (widget instanceof Tree tree) return tree.getColumns ();
		return new Item [0];
	}

	private static void remove (Control widget, Listener listener) {
		if (widget == null || listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		widget.removeListener (EVENT, listener);
		Latch latch = latch (widget);
		if (latch == null || widget.hooks (EVENT)) return;
		widget.removeListener (SWT.Resize, latch);
		widget.removeListener (SWT.Expand, latch);
		widget.removeListener (SWT.Collapse, latch);
		ScrollBar bar = ((Scrollable) widget).getVerticalBar ();
		if (bar != null) bar.removeListener (SWT.Selection, latch);
		widget.removeListener (SWT.MouseVerticalWheel, latch);
		widget.removeListener (SWT.KeyDown, latch);
		widget.removeListener (SWT.ZoomChanged, latch);
		for (Item column : columns (widget)) {
			column.removeListener (SWT.Resize, latch);
		}
		widget.setData (KEY, null);
	}

	private static Latch latch (Widget widget) {
		return widget.getData (KEY) instanceof Latch latch ? latch : null;
	}
}
