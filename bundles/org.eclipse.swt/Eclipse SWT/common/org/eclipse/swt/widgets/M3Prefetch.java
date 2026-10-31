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
 * Prefetch ranges of <code>Table</code>, <code>List</code> and <code>Tree</code> (enhanced M3
 * API): which rows the widget is about to show, and which it no longer holds.
 * <p>
 * A listener added here receives events of type {@link #EVENT} when the widget's resident
 * window (see {@link M3Residency}) moves: first {@link #CANCEL} for the rows that left the window,
 * then {@link #PREFETCH} for the rows that entered it, each as at most two ranges in
 * <code>event.start</code> and <code>event.end</code> (inclusive; a Tree uses the visible rows of
 * {@link M3RowMath}). Scrolling by k rows announces k rows. Ranges are delivered, never items,
 * so a data source can fetch in bulk before <code>SWT.SetData</code> asks row by row.
 * {@link #setWindow(Table, int, int)} widens the announced range around the resident window.
 * </p>
 * <p>
 * The events are the same in every widget mode (the mode-neutral contract): the resident window
 * is the rows in view ({@link M3Residency#residentRange(Table)}), checked when the widget paints,
 * scrolls, resizes, expands or collapses a row, after a structural change the widget announces
 * (a Table's, or a viewport's window move), once at the end of the event. A widget that is not
 * visible holds no rows, so it announces nothing until it is shown.
 * </p>
 * <p>
 * Listeners are called at the end of the event that moved the window, never inside a native
 * paint; the first listener hears the current window when it is added. They should start
 * fetches, not change the widget's items.
 * </p>
 *
 * @since 3.138
 */
public final class M3Prefetch {

	/** The event type: outside the range of the SWT event types. */
	public static final int EVENT = 0x4D330002;
	/** <code>event.detail</code>: the rows from <code>start</code> to <code>end</code> entered the window. */
	public static final int PREFETCH = 0;
	/** <code>event.detail</code>: the rows from <code>start</code> to <code>end</code> left the window. */
	public static final int CANCEL = 1;

	private static final String KEY = "org.eclipse.swt.widgets.M3Prefetch"; //$NON-NLS-1$

	/* The latch of a widget: the last announced range [first, last], the two margins and the trigger. */
	private static final class Latch implements Listener {
		final Control widget;
		int first, last = -1;
		int before, after;
		boolean scheduled, observing;

		Latch (Control widget) {
			this.widget = widget;
		}

		/* a trigger: check once at the end of the current event */
		@Override
		public void handleEvent (Event event) {
			if (scheduled || widget.isDisposed ()) return;
			scheduled = true;
			widget.getDisplay ().asyncExec (() -> {
				scheduled = false;
				announce (widget);
			});
		}
	}

	/* the events after which the rows in view may have changed */
	private static final int [] TRIGGERS = {SWT.Resize, SWT.Paint, SWT.Expand, SWT.Collapse, SWT.MouseVerticalWheel, SWT.KeyDown};

	private M3Prefetch () {
	}

	/**
	 * Adds a listener for the prefetch ranges of the table.
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
	 * Adds a listener for the prefetch ranges of the list.
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
	 * Adds a listener for the prefetch ranges (visible rows) of the tree.
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
	 * Widens the announced range of the table: <code>rowsBefore</code> rows above and
	 * <code>rowsAfter</code> rows below its resident window. Takes effect at the next window move.
	 *
	 * @param table the table
	 * @param rowsBefore the rows above the window, not negative
	 * @param rowsAfter the rows below the window, not negative
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a margin is negative</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void setWindow (Table table, int rowsBefore, int rowsAfter) {
		window (table, rowsBefore, rowsAfter);
	}

	/**
	 * Widens the announced range of the list (see {@link #setWindow(Table, int, int)}).
	 *
	 * @param list the list
	 * @param rowsBefore the rows above the window, not negative
	 * @param rowsAfter the rows below the window, not negative
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a margin is negative</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static void setWindow (List list, int rowsBefore, int rowsAfter) {
		window (list, rowsBefore, rowsAfter);
	}

	/**
	 * Widens the announced range of the tree (see {@link #setWindow(Table, int, int)}).
	 *
	 * @param tree the tree
	 * @param rowsBefore the visible rows above the window, not negative
	 * @param rowsAfter the visible rows below the window, not negative
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a margin is negative</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static void setWindow (Tree tree, int rowsBefore, int rowsAfter) {
		window (tree, rowsBefore, rowsAfter);
	}

	/* ------------------------------------------------------------------ widget seam */

	/*
	 * The resident window may have moved (a structural change, or a viewport seam that may run inside a native
	 * paint): checks it once at the end of the current event, so listeners never run inside a paint.
	 */
	static void windowChanged (Control widget) {
		Latch latch = widget.isDisposed () ? null : latch (widget);
		if (latch != null && widget.hooks (EVENT)) latch.handleEvent (null);
	}

	/* Announces the difference between the resident window and the last announced range, now. */
	static void announce (Control widget) {
		if (!widget.hooks (EVENT) || widget.isDisposed ()) return;
		Latch latch = latch (widget);
		if (latch == null) return;
		long window = window (widget, latch);
		int first = (int) (window >> 32), last = (int) window;
		int oldFirst = latch.first, oldLast = latch.last;
		if (first == oldFirst && last == oldLast) return;
		latch.first = first;
		latch.last = last;
		/* rows that left (old minus new), then rows that entered (new minus old) */
		difference (widget, EVENT, CANCEL, oldFirst, oldLast, first, last);
		difference (widget, EVENT, PREFETCH, first, last, oldFirst, oldLast);
	}

	/* the resident window widened by the margins and clamped to the rows, packed as first << 32 | last (0, -1 when empty) */
	private static long window (Control widget, Latch latch) {
		M3ItemRange resident = resident (widget);
		if (resident.isEmpty ()) return 0xFFFFFFFFL;
		int first = Math.max (0, resident.start () - latch.before);
		int last = (int) Math.min (rows (widget) - 1L, (long) resident.end () + latch.after);
		return (long) first << 32 | last & 0xFFFFFFFFL;
	}

	/* ------------------------------------------------------------------ helpers */

	/* Sends [from, to] minus [cutFirst, cutLast] as events of the type: at most two ranges, below and above the cut. */
	static void difference (Control widget, int type, int detail, int from, int to, int cutFirst, int cutLast) {
		if (to < from) return;
		if (cutLast < cutFirst || cutLast < from || cutFirst > to) {
			send (widget, type, detail, from, to);
			return;
		}
		send (widget, type, detail, from, cutFirst - 1);
		send (widget, type, detail, cutLast + 1, to);
	}

	private static void send (Control widget, int type, int detail, int start, int end) {
		if (end < start || widget.isDisposed ()) return;
		Event event = new Event ();
		event.detail = detail;
		event.start = start;
		event.end = end;
		widget.sendEvent (type, event);
	}

	private static void add (Control widget, Listener listener) {
		if (widget == null || listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		boolean first = !widget.hooks (EVENT);
		widget.addListener (EVENT, listener);
		Latch latch = latchOf (widget);
		observe (latch, true);
		/* the first listener hears the current window as a whole */
		if (first) {
			latch.first = 0;
			latch.last = -1;
			announce (widget);
		}
	}

	private static void remove (Control widget, Listener listener) {
		if (widget == null || listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		widget.removeListener (EVENT, listener);
		Latch latch = latch (widget);
		if (latch != null && !widget.hooks (EVENT)) observe (latch, false);
	}

	private static void window (Control widget, int before, int after) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		if (before < 0 || after < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		Latch latch = latchOf (widget);
		latch.before = before;
		latch.after = after;
	}

	private static Latch latch (Widget widget) {
		return widget.getData (KEY) instanceof Latch latch ? latch : null;
	}

	private static Latch latchOf (Control widget) {
		Latch latch = latch (widget);
		if (latch == null) widget.setData (KEY, latch = new Latch (widget));
		return latch;
	}

	/* hooks or unhooks the triggers of the latch (once) */
	private static void observe (Latch latch, boolean on) {
		if (latch.observing == on) return;
		latch.observing = on;
		ScrollBar bar = ((Scrollable) latch.widget).getVerticalBar ();
		for (int type : TRIGGERS) {
			if (on) latch.widget.addListener (type, latch);
			else latch.widget.removeListener (type, latch);
		}
		if (bar != null && on) bar.addListener (SWT.Selection, latch);
		if (bar != null && !on) bar.removeListener (SWT.Selection, latch);
	}

	/* the resident window: the rows in view, the same in every mode */
	private static M3ItemRange resident (Control widget) {
		if (widget instanceof Table table) return M3Residency.residentRange (table);
		if (widget instanceof Tree tree) return M3Residency.residentRange (tree);
		return M3Residency.residentRange ((List) widget);
	}

	private static int rows (Control widget) {
		if (widget instanceof Table table) return table.getItemCount ();
		if (widget instanceof Tree tree) return (int) Math.min (Integer.MAX_VALUE, M3RowMath.visibleRowCount (tree));
		return ((List) widget).getItemCount ();
	}
}
