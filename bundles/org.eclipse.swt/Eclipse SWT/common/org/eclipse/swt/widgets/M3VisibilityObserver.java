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
 * <p>
 * Hidden viewports (brief rule 24): a viewport is hidden while the widget or one of its ancestors
 * is not visible, its shell is minimized, or its client area is empty
 * ({@link #isViewportHidden(Table)}). A hidden viewport announces {@link #HIDDEN} once and then
 * nothing: no range, no extents. The model may change it meanwhile; a change through
 * {@link M3Model#apply}, <code>M3BulkUpdates</code> or <code>M3DiffApply</code> (with a listener
 * added here, also through the item API) makes it dirty ({@link #isViewportDirty(Table)}) and costs
 * no window work: the top row is re-anchored, and the window planned, when the viewport shows.
 * When it shows, a listener receives {@link #SHOWN} (while the viewport still answers dirty), then
 * the window delta since the last announced range ({@link #LEFT}, {@link #ENTERED}) and the new
 * {@link #EXTENTS}; then the viewport is clean. A show through <code>setVisible (true)</code> of
 * the widget or an ancestor, or the restore of its shell, is announced before the widget paints,
 * so the viewport paints once, with its final rows. A viewport that becomes non-empty by a resize
 * is announced at the end of the resize.
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
	/**
	 * <code>event.detail</code>: the viewport became hidden; no range or extents follow until
	 * {@link #SHOWN}.
	 *
	 * @since 3.138
	 */
	public static final int HIDDEN = 3;
	/**
	 * <code>event.detail</code>: the viewport became visible. During this event
	 * {@link #isViewportDirty(Table)} answers whether the rows changed while it was hidden; the window
	 * delta and the extents follow.
	 *
	 * @since 3.138
	 */
	public static final int SHOWN = 4;

	private static final String KEY = "org.eclipse.swt.widgets.M3VisibilityObserver"; //$NON-NLS-1$

	/*
	 * The latch of a widget: the last announced range and extents, whether the viewport was hidden and dirty
	 * when last seen, and the listeners it installed. It exists while the widget has observer listeners or holds
	 * changes back while hidden.
	 */
	private static final class Latch implements Listener {
		final Control widget;
		int first, last = -1;
		int width = -1, height = -1, rows = -1, rowExtent = -1, contentWidth = -1;
		boolean scheduled, extentsDirty = true, hidden, dirty, triggers;
		/* the widget and its ancestors up to its shell, while their Show and Hide events are heard */
		Control [] chain;
		/* Show and Hide of the chain, Iconify and Deiconify of the shell, Dispose of the widget: at once, not deferred */
		final Listener chainListener = this::chainEvent;

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

		private void chainEvent (Event event) {
			if (event.type == SWT.Dispose) {
				unwatch (this);
				return;
			}
			if (!widget.isDisposed ()) visibility (widget, this, event);
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

	/**
	 * Returns whether the viewport of the table is hidden: the table or one of its ancestors is not
	 * visible, its shell is minimized, or its client area is empty.
	 *
	 * @param table the table
	 * @return <code>true</code> when the table shows no rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static boolean isViewportHidden (Table table) {
		return isHidden (table);
	}

	/**
	 * Returns whether the viewport of the list is hidden: the list or one of its ancestors is not
	 * visible, its shell is minimized, or its client area is empty.
	 *
	 * @param list the list
	 * @return <code>true</code> when the list shows no rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static boolean isViewportHidden (List list) {
		return isHidden (list);
	}

	/**
	 * Returns whether the viewport of the tree is hidden: the tree or one of its ancestors is not
	 * visible, its shell is minimized, or its client area is empty.
	 *
	 * @param tree the tree
	 * @return <code>true</code> when the tree shows no rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static boolean isViewportHidden (Tree tree) {
		return isHidden (tree);
	}

	/**
	 * Returns whether the rows of the table changed while its viewport was hidden and the viewport has
	 * not shown since. During {@link #SHOWN} it still answers for the hidden period.
	 *
	 * @param table the table
	 * @return <code>true</code> when the viewport holds changes back for its next show
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static boolean isViewportDirty (Table table) {
		return isDirty (table);
	}

	/**
	 * Returns whether the rows of the list changed while its viewport was hidden and the viewport has
	 * not shown since. During {@link #SHOWN} it still answers for the hidden period.
	 *
	 * @param list the list
	 * @return <code>true</code> when the viewport holds changes back for its next show
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static boolean isViewportDirty (List list) {
		return isDirty (list);
	}

	/**
	 * Returns whether the rows of the tree changed while its viewport was hidden and the viewport has
	 * not shown since. During {@link #SHOWN} it still answers for the hidden period.
	 *
	 * @param tree the tree
	 * @return <code>true</code> when the viewport holds changes back for its next show
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static boolean isViewportDirty (Tree tree) {
		return isDirty (tree);
	}

	/* ------------------------------------------------------------------ widget seam */

	/* A viewport moved its window or the rows changed through the item API: check the visible range now. */
	static void changed (Control widget) {
		if (!widget.hooks (EVENT) || widget.isDisposed ()) return;
		Latch latch = latch (widget);
		if (latch == null) return;
		latch.extentsDirty = true;
		if (visibility (widget, latch, null)) {
			latch.dirty = true;
			return;
		}
		if (!widget.isDisposed () && widget.hooks (EVENT)) window (widget, latch);
	}

	/*
	 * The rows changed through the model (M3Model, M3BulkUpdates, M3DiffApply). A hidden viewport becomes dirty
	 * and is watched until it shows, so the model can re-anchor its top row then; a visible one is checked now.
	 */
	static void modelChanged (Control widget) {
		if (widget.isDisposed ()) return;
		Latch latch = latch (widget);
		if (hidden (widget, null)) {
			if (latch == null) latch = create (widget);
			watch (latch);
			visibility (widget, latch, null);
			latch.dirty = true;
			latch.extentsDirty = true;
			return;
		}
		if (latch == null) return;
		latch.extentsDirty = true;
		check (widget, latch);
	}

	/* Whether the viewport is hidden now (M3Model asks before a patch). */
	static boolean hidden (Control widget) {
		return hidden (widget, null);
	}

	/* ------------------------------------------------------------------ helpers */

	private static boolean isHidden (Control widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		return hidden (widget, null);
	}

	private static boolean isDirty (Control widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		Latch latch = latch (widget);
		return latch != null && latch.dirty;
	}

	/*
	 * Whether the viewport is hidden, with the event that is changing it: Show is sent before the control shows,
	 * Deiconify before the shell is restored, so they count as done.
	 */
	private static boolean hidden (Control widget, Event cause) {
		int type = cause == null ? SWT.None : cause.type;
		Widget changing = cause == null ? null : cause.widget;
		if (type == SWT.Iconify) return true;
		for (Control c = widget; c != null; c = c.getParent ()) {
			boolean visible = c == changing && type == SWT.Show || (c != changing || type != SWT.Hide) && c.getVisible ();
			if (!visible) return true;
			if (c instanceof Shell shell) {
				if (type != SWT.Deiconify && shell.getMinimized ()) return true;
				break;
			}
		}
		Rectangle area = ((Scrollable) widget).getClientArea ();
		return area.width <= 0 || area.height <= 0;
	}

	/*
	 * Answers whether the viewport is hidden now, and announces a change: HIDDEN; or, when it shows, the model's
	 * re-anchoring, SHOWN while still dirty, the window delta and the extents, then clean.
	 */
	private static boolean visibility (Control widget, Latch latch, Event cause) {
		boolean hidden = hidden (widget, cause);
		if (hidden == latch.hidden) return hidden;
		latch.hidden = hidden;
		if (hidden) {
			if (widget.hooks (EVENT)) send (widget, HIDDEN, 0, -1);
			return true;
		}
		M3Model.shown (widget);
		if (!widget.isDisposed () && widget.hooks (EVENT)) {
			send (widget, SHOWN, 0, -1);
			latch.extentsDirty = true;
			if (!widget.isDisposed ()) window (widget, latch);
		}
		latch.dirty = false;
		if (!widget.isDisposed ()) release (latch);
		return false;
	}

	private static void check (Control widget, Latch latch) {
		if (!visibility (widget, latch, null) && !widget.isDisposed () && widget.hooks (EVENT)) window (widget, latch);
	}

	/* the window delta of a visible viewport: the rows that left, the rows that entered, then new extents */
	private static void window (Control widget, Latch latch) {
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

	private static void send (Control widget, int detail, int start, int end) {
		Event event = new Event ();
		event.detail = detail;
		event.start = start;
		event.end = end;
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

	private static Latch create (Control widget) {
		Latch latch = new Latch (widget);
		widget.setData (KEY, latch);
		return latch;
	}

	private static void add (Control widget, Listener listener) {
		if (widget == null || listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		Latch latch = latch (widget);
		if (latch == null) latch = create (widget);
		if (!latch.triggers) {
			latch.triggers = true;
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
			for (Item column : columns (widget)) column.addListener (SWT.Resize, latch);
		}
		watch (latch);
		boolean first = !widget.hooks (EVENT);
		widget.addListener (EVENT, listener);
		/* the first listener hears the current state: HIDDEN, or the range and the extents */
		if (first && latch.hidden && hidden (widget, null)) send (widget, HIDDEN, 0, -1);
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
		if (latch != null) release (latch);
	}

	/* a latch without listeners and without held-back changes is forgotten with everything it installed */
	private static void release (Latch latch) {
		Control widget = latch.widget;
		if (widget.hooks (EVENT) || latch.dirty) return;
		if (latch.triggers) {
			latch.triggers = false;
			widget.removeListener (SWT.Resize, latch);
			widget.removeListener (SWT.Expand, latch);
			widget.removeListener (SWT.Collapse, latch);
			ScrollBar bar = ((Scrollable) widget).getVerticalBar ();
			if (bar != null) bar.removeListener (SWT.Selection, latch);
			widget.removeListener (SWT.MouseVerticalWheel, latch);
			widget.removeListener (SWT.KeyDown, latch);
			widget.removeListener (SWT.ZoomChanged, latch);
			for (Item column : columns (widget)) column.removeListener (SWT.Resize, latch);
		}
		unwatch (latch);
		widget.setData (KEY, null);
	}

	/* hears the Show and Hide of the widget and its ancestors, the Iconify and Deiconify of its shell */
	private static void watch (Latch latch) {
		if (latch.chain != null) return;
		int depth = 0;
		for (Control c = latch.widget; c != null; c = c instanceof Shell ? null : c.getParent ()) depth++;
		Control [] chain = new Control [depth];
		Control c = latch.widget;
		for (int i = 0; i < depth; i++, c = c.getParent ()) {
			chain [i] = c;
			c.addListener (SWT.Show, latch.chainListener);
			c.addListener (SWT.Hide, latch.chainListener);
		}
		chain [depth - 1].addListener (SWT.Iconify, latch.chainListener);
		chain [depth - 1].addListener (SWT.Deiconify, latch.chainListener);
		latch.widget.addListener (SWT.Dispose, latch.chainListener);
		latch.chain = chain;
	}

	/* the ancestors outlive the widget: they forget its listener when it is disposed or released */
	private static void unwatch (Latch latch) {
		Control [] chain = latch.chain;
		if (chain == null) return;
		latch.chain = null;
		for (Control c : chain) {
			if (c.isDisposed ()) continue;
			c.removeListener (SWT.Show, latch.chainListener);
			c.removeListener (SWT.Hide, latch.chainListener);
			c.removeListener (SWT.Iconify, latch.chainListener);
			c.removeListener (SWT.Deiconify, latch.chainListener);
			c.removeListener (SWT.Dispose, latch.chainListener);
		}
	}

	private static Latch latch (Widget widget) {
		return widget.getData (KEY) instanceof Latch latch ? latch : null;
	}
}
