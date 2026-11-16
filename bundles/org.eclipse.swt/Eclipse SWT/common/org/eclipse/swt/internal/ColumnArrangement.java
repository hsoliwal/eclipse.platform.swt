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

import java.util.*;

/**
 * Which columns of a column based widget are shown, in which order, and how wide: the model of a
 * column selector and of a column order dialog.
 * <p>
 * A column is named by its index. Every column has a position in the display order, whether it
 * is shown or hidden, so a column that is shown again returns to its place. The arrangement
 * touches no widget: it is a set of int lanes that a widget, a dialog or a test reads. Each
 * change is reported to the listeners as one delta (the column and what happened to it), so a
 * bound widget moves, shows, hides or resizes one column and does not rebuild the others.
 * </p>
 * <p>
 * {@link #saveState()} and {@link #restoreState(String)} keep an arrangement as text.
 * </p>
 *
 * @since 3.138
 */
public final class ColumnArrangement {

	/** Change: the column is shown again; <code>to</code> is its position. */
	public static final int SHOWN = 1;

	/** Change: the column was hidden; <code>from</code> is its position. */
	public static final int HIDDEN = 2;

	/** Change: the column moved from position <code>from</code> to position <code>to</code>; the columns between shifted by one. */
	public static final int MOVED = 3;

	/** Change: the width of the column changed from <code>from</code> to <code>to</code>. */
	public static final int RESIZED = 4;

	/** Change: a column was inserted with index <code>column</code> at position <code>to</code>; later indices grew by one. */
	public static final int INSERTED = 5;

	/** Change: the column with index <code>column</code> was removed from position <code>from</code>; later indices shrank by one. */
	public static final int REMOVED = 6;

	/** Change: everything may differ (a new order, a reset, a restored state); <code>column</code> is -1. */
	public static final int RESET = 7;

	/** The width of a column whose width was never set. */
	public static final int DEFAULT_WIDTH = -1;

	/** Receives the changes of an arrangement. */
	@FunctionalInterface
	public interface Listener {
		/**
		 * Called after the arrangement changed.
		 *
		 * @param kind {@link #SHOWN}, {@link #HIDDEN}, {@link #MOVED}, {@link #RESIZED}, {@link #INSERTED}, {@link #REMOVED} or {@link #RESET}
		 * @param column the column, or -1
		 * @param from the position or width before, as the kind says
		 * @param to the position or width after, as the kind says
		 */
		void arrangementChanged (int kind, int column, int from, int to);
	}

	static final String HEADER = "ColumnArrangement/1 "; //$NON-NLS-1$

	private int count;
	/* position -> column, column -> position, column -> width, bit = the column is hidden */
	private int [] order, position, width;
	private long [] hidden;
	private int hiddenCount;
	private Listener [] listeners = {};

	/**
	 * Constructs an arrangement of columns in index order, all shown, with default widths.
	 *
	 * @param columnCount the number of columns, not negative
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the count is negative</li>
	 * </ul>
	 */
	public ColumnArrangement (int columnCount) {
		RowChecks.argument (columnCount >= 0);
		count = columnCount;
		order = new int [Math.max (4, columnCount)];
		position = new int [order.length];
		width = new int [order.length];
		hidden = new long [RowMask.words (order.length)];
		identity ();
	}

	private void identity () {
		for (int i = 0; i < count; i++) order [i] = position [i] = i;
		Arrays.fill (width, 0, count, DEFAULT_WIDTH);
		Arrays.fill (hidden, 0L);
		hiddenCount = 0;
	}

	/**
	 * Returns the number of columns, shown and hidden.
	 *
	 * @return the column count
	 */
	public int getColumnCount () {
		return count;
	}

	/**
	 * Returns the number of columns that are shown.
	 *
	 * @return the number of shown columns
	 */
	public int getVisibleCount () {
		return count - hiddenCount;
	}

	/**
	 * Returns whether a column is shown.
	 *
	 * @param column the column
	 * @return <code>true</code> if the column is shown
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public boolean isVisible (int column) {
		return !RowMask.get (hidden, RowChecks.range (column, count));
	}

	/**
	 * Shows or hides a column. The column keeps its position and its width.
	 *
	 * @param column the column
	 * @param visible <code>true</code> to show the column, <code>false</code> to hide it
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public void setVisible (int column, boolean visible) {
		if (isVisible (column) == visible) return;
		RowMask.set (hidden, column, !visible);
		hiddenCount += visible ? -1 : 1;
		notify (visible ? SHOWN : HIDDEN, column, position [column], position [column]);
	}

	/**
	 * Returns the position of a column in the display order, counting shown and hidden columns.
	 *
	 * @param column the column
	 * @return the position, from 0
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public int getPosition (int column) {
		return position [RowChecks.range (column, count)];
	}

	/**
	 * Returns the display order: the column at every position, shown and hidden.
	 *
	 * @return a new array of the column indices in display order
	 */
	public int [] getOrder () {
		return Arrays.copyOf (order, count);
	}

	/**
	 * Returns the shown columns in display order.
	 *
	 * @return a new array of the indices of the shown columns in display order
	 */
	public int [] getVisibleOrder () {
		int [] result = new int [count - hiddenCount];
		for (int p = 0, n = 0; p < count; p++) {
			if (!RowMask.get (hidden, order [p])) result [n++] = order [p];
		}
		return result;
	}

	/**
	 * Moves a column to a position in the display order. The columns between its old and its new
	 * position shift by one; every other column stays.
	 *
	 * @param column the column
	 * @param to the new position, counting shown and hidden columns
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column or the position is not a position</li>
	 * </ul>
	 */
	public void move (int column, int to) {
		int from = getPosition (column);
		if (RowChecks.range (to, count) == from) return;
		if (from < to) {
			System.arraycopy (order, from + 1, order, from, to - from);
		} else {
			System.arraycopy (order, to, order, to + 1, from - to);
		}
		order [to] = column;
		for (int p = Math.min (from, to), end = Math.max (from, to); p <= end; p++) position [order [p]] = p;
		notify (MOVED, column, from, to);
	}

	/**
	 * Sets the display order of all columns.
	 *
	 * @param order the column at every position: each column index exactly once
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the order is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the order does not hold each column exactly once</li>
	 * </ul>
	 */
	public void setOrder (int [] order) {
		checkOrder (RowChecks.nonNull (order), count);
		if (Arrays.equals (order, 0, count, this.order, 0, count)) return;
		System.arraycopy (order, 0, this.order, 0, count);
		for (int p = 0; p < count; p++) position [order [p]] = p;
		notify (RESET, -1, 0, 0);
	}

	private static void checkOrder (int [] order, int count) {
		RowChecks.argument (order.length == count);
		long [] seen = new long [RowMask.words (count)];
		for (int column : order) {
			RowChecks.argument (column >= 0 && column < count && !RowMask.get (seen, column));
			RowMask.set (seen, column, true);
		}
	}

	/**
	 * Returns the width of a column.
	 *
	 * @param column the column
	 * @return the width, or {@link #DEFAULT_WIDTH} if it was never set
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public int getWidth (int column) {
		return width [RowChecks.range (column, count)];
	}

	/**
	 * Sets the width of a column. A hidden column keeps its width for when it is shown again.
	 *
	 * @param column the column
	 * @param width the width, not negative, or {@link #DEFAULT_WIDTH}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the width is negative and not {@link #DEFAULT_WIDTH}</li>
	 * </ul>
	 */
	public void setWidth (int column, int width) {
		int old = getWidth (column);
		RowChecks.argument (width >= DEFAULT_WIDTH);
		if (old == width) return;
		this.width [column] = width;
		notify (RESIZED, column, old, width);
	}

	/**
	 * Inserts a column: it gets the given index, the columns from that index on get the next
	 * index. The new column is shown, has the default width, and stands at the given position.
	 *
	 * @param column the index of the new column, at most the column count
	 * @param at its position in the display order, at most the column count
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the index or the position is out of range</li>
	 * </ul>
	 */
	public void insertColumn (int column, int at) {
		RowChecks.range (column, count + 1);
		RowChecks.range (at, count + 1);
		if (count == order.length) {
			order = Arrays.copyOf (order, count + (count >> 1) + 1);
			position = Arrays.copyOf (position, order.length);
			width = Arrays.copyOf (width, order.length);
		}
		hidden = RowMask.ensure (hidden, count + 1);
		RowMask.insert (hidden, count, column, 1);
		System.arraycopy (width, column, width, column + 1, count - column);
		width [column] = DEFAULT_WIDTH;
		for (int p = 0; p < count; p++) {
			if (order [p] >= column) order [p]++;
		}
		System.arraycopy (order, at, order, at + 1, count - at);
		order [at] = column;
		count++;
		for (int p = 0; p < count; p++) position [order [p]] = p;
		notify (INSERTED, column, at, at);
	}

	/**
	 * Removes a column: the columns after it get the previous index and keep their order.
	 *
	 * @param column the column
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public void removeColumn (int column) {
		int from = getPosition (column);
		if (RowMask.get (hidden, column)) hiddenCount--;
		RowMask.remove (hidden, count, column, 1);
		System.arraycopy (width, column + 1, width, column, count - column - 1);
		System.arraycopy (order, from + 1, order, from, count - from - 1);
		count--;
		for (int p = 0; p < count; p++) {
			if (order [p] > column) order [p]--;
			position [order [p]] = p;
		}
		notify (REMOVED, column, from, from);
	}

	/**
	 * Puts every column back: index order, all shown, default widths.
	 */
	public void reset () {
		boolean same = hiddenCount == 0;
		for (int i = 0; i < count && same; i++) same = order [i] == i && width [i] == DEFAULT_WIDTH;
		if (same) return;
		identity ();
		notify (RESET, -1, 0, 0);
	}

	/**
	 * Returns the arrangement as a text that {@link #restoreState(String)} reads back.
	 *
	 * @return the state
	 */
	public String saveState () {
		StringBuilder out = new StringBuilder (HEADER).append (count).append ('\n');
		for (int p = 0; p < count; p++) {
			int column = order [p];
			out.append (column).append (' ').append (RowMask.get (hidden, column) ? 0 : 1).append (' ').append (width [column]).append ('\n');
		}
		return out.toString ();
	}

	/**
	 * Sets the arrangement from a text that {@link #saveState()} returned for the same number of
	 * columns. A text that is not such a state is rejected and the arrangement stays as it is.
	 *
	 * @param state the state
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the state is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the text is not a state of an arrangement of this number of columns</li>
	 * </ul>
	 */
	public void restoreState (String state) {
		RowChecks.nonNull (state);
		int [] newOrder = new int [count], newWidth = new int [count];
		long [] newHidden = new long [hidden.length];
		int hiddenNow = 0;
		try {
			RowChecks.argument (state.startsWith (HEADER));
			String [] lines = state.substring (HEADER.length ()).split ("\n", -1); //$NON-NLS-1$
			RowChecks.argument (lines.length == count + 2 && lines [count + 1].isEmpty () && Integer.parseInt (lines [0]) == count);
			for (int p = 0; p < count; p++) {
				String [] fields = lines [p + 1].split (" ", -1); //$NON-NLS-1$
				RowChecks.argument (fields.length == 3);
				int column = newOrder [p] = Integer.parseInt (fields [0]), shown = Integer.parseInt (fields [1]);
				RowChecks.argument (column >= 0 && column < count && (shown == 0 || shown == 1));
				newWidth [column] = Integer.parseInt (fields [2]);
				RowChecks.argument (newWidth [column] >= DEFAULT_WIDTH);
				if (shown == 0) {
					RowMask.set (newHidden, column, true);
					hiddenNow++;
				}
			}
		} catch (NumberFormatException e) {
			RowChecks.argument (false);
		}
		checkOrder (newOrder, count);
		if (state.equals (saveState ())) return;
		System.arraycopy (newOrder, 0, order, 0, count);
		System.arraycopy (newWidth, 0, width, 0, count);
		hidden = newHidden;
		hiddenCount = hiddenNow;
		for (int p = 0; p < count; p++) position [order [p]] = p;
		notify (RESET, -1, 0, 0);
	}

	/**
	 * Adds a listener to the changes of the arrangement.
	 *
	 * @param listener the listener
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
	 * </ul>
	 */
	public void addListener (Listener listener) {
		RowChecks.nonNull (listener);
		listeners = Arrays.copyOf (listeners, listeners.length + 1);
		listeners [listeners.length - 1] = listener;
	}

	/**
	 * Removes a listener.
	 *
	 * @param listener the listener
	 */
	public void removeListener (Listener listener) {
		for (int i = 0; i < listeners.length; i++) {
			if (listeners [i] == listener) {
				Listener [] rest = new Listener [listeners.length - 1];
				System.arraycopy (listeners, 0, rest, 0, i);
				System.arraycopy (listeners, i + 1, rest, i, rest.length - i);
				listeners = rest;
				return;
			}
		}
	}

	private void notify (int kind, int column, int from, int to) {
		for (Listener listener : listeners) listener.arrangementChanged (kind, column, from, to);
	}
}
