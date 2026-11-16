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
import java.util.function.*;

/**
 * One summary value per column over the rows an {@link RowView} shows: the model of a footer
 * row whose cells can be configured per column (count, sum, minimum, maximum, average).
 * <p>
 * A column is summarised when it has values ({@link #setValues(int, IntToDoubleFunction)}: the
 * number of a source row, read from the application's own data; widget items are never read) and
 * a function ({@link #setFunction(int, int)}). A row whose value is not a number
 * (<code>NaN</code>) has no value in that column and is left out.
 * </p>
 * <p>
 * The summary follows the view: when a filter above it shows other rows, or the source is
 * edited, the listeners are told and the values are computed again when they are next asked
 * for, in one pass over the rows shown, however many changes came before. Nothing is computed
 * for a footer that is not painted.
 * </p>
 *
 * @see ColumnFilter
 * @since 3.138
 */
public final class ColumnSummary {

	/** Function: the column has no summary. */
	public static final int NONE = 0;

	/** Function: the number of rows shown that have a value in the column (all rows shown, if the column has no values). */
	public static final int COUNT = 1;

	/** Function: the sum of the values (0 when no row has a value). */
	public static final int SUM = 2;

	/** Function: the smallest value (<code>NaN</code> when no row has a value). */
	public static final int MINIMUM = 3;

	/** Function: the largest value (<code>NaN</code> when no row has a value). */
	public static final int MAXIMUM = 4;

	/** Function: the sum divided by the number of rows that have a value (<code>NaN</code> when none has). */
	public static final int AVERAGE = 5;

	/** Receives the changes of a summary. */
	@FunctionalInterface
	public interface Listener {
		/**
		 * Called when summary values may have changed.
		 *
		 * @param column the column whose function or values changed, or -1 when the rows changed
		 */
		void summaryChanged (int column);
	}

	private final RowView view;
	private final RowView.Listener follower = change -> rowsChanged (change);
	private final IntToDoubleFunction [] values;
	private final int [] function, counted;
	private final double [] sum, minimum, maximum;
	/* bit = the lanes of the column hold the rows shown now */
	private final long [] current;
	private Listener [] listeners = {};
	int passes;

	/**
	 * Constructs a summary of the rows a view shows, without values or functions.
	 *
	 * @param columnCount the number of columns, not negative
	 * @param view the view whose rows are summarised
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the view is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the count is negative</li>
	 * </ul>
	 */
	public ColumnSummary (int columnCount, RowView view) {
		RowChecks.argument (columnCount >= 0);
		this.view = RowChecks.nonNull (view);
		values = new IntToDoubleFunction [columnCount];
		function = new int [columnCount];
		counted = new int [columnCount];
		sum = new double [columnCount];
		minimum = new double [columnCount];
		maximum = new double [columnCount];
		current = new long [RowMask.words (columnCount)];
		view.addListener (follower);
	}

	/**
	 * Returns the number of columns.
	 *
	 * @return the column count
	 */
	public int getColumnCount () {
		return function.length;
	}

	/**
	 * Sets how the value of a row in a column is read.
	 *
	 * @param column the column
	 * @param value the number of a source row (<code>NaN</code>: the row has no value), or <code>null</code> to remove the values
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public void setValues (int column, IntToDoubleFunction value) {
		values [RowChecks.range (column, function.length)] = value;
		RowMask.set (current, column, false);
		notify (column);
	}

	/**
	 * Sets the function of a column.
	 *
	 * @param column the column
	 * @param function {@link #NONE}, {@link #COUNT}, {@link #SUM}, {@link #MINIMUM}, {@link #MAXIMUM} or {@link #AVERAGE}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the function is not one of the functions</li>
	 * </ul>
	 */
	public void setFunction (int column, int function) {
		RowChecks.range (column, this.function.length);
		RowChecks.argument (function >= NONE && function <= AVERAGE);
		if (this.function [column] == function) return;
		this.function [column] = function;
		notify (column);
	}

	/**
	 * Returns the function of a column.
	 *
	 * @param column the column
	 * @return the function
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public int getFunction (int column) {
		return function [RowChecks.range (column, function.length)];
	}

	/**
	 * Returns the summary value of a column for the rows the view shows now.
	 *
	 * @param column the column
	 * @return the value of the column's function; <code>NaN</code> when the column has no function,
	 *         or the function needs values and no row shown has one
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public double getValue (int column) {
		int kind = getFunction (column);
		if (kind == NONE) return Double.NaN;
		if (values [column] == null) return kind == COUNT ? view.size () : Double.NaN;
		if (!RowMask.get (current, column)) compute (column);
		return switch (kind) {
			case COUNT -> counted [column];
			case SUM -> sum [column];
			case MINIMUM -> minimum [column];
			case MAXIMUM -> maximum [column];
			default -> counted [column] == 0 ? Double.NaN : sum [column] / counted [column];
		};
	}

	/**
	 * Returns the number of rows shown that have a value in a column.
	 *
	 * @param column the column
	 * @return the number of rows with a value; the number of rows shown if the column has no values
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public int getCount (int column) {
		if (values [RowChecks.range (column, function.length)] == null) return view.size ();
		if (!RowMask.get (current, column)) compute (column);
		return counted [column];
	}

	/* one pass over the rows shown, in view order */
	private void compute (int column) {
		IntToDoubleFunction value = values [column];
		int n = 0;
		double total = 0, low = Double.NaN, high = Double.NaN;
		for (int row = 0, size = view.size (); row < size; row++) {
			double v = value.applyAsDouble (view.map (row));
			if (Double.isNaN (v)) continue;
			total += v;
			if (n++ == 0) {
				low = high = v;
			} else {
				if (v < low) low = v;
				if (v > high) high = v;
			}
		}
		counted [column] = n;
		sum [column] = total;
		minimum [column] = low;
		maximum [column] = high;
		RowMask.set (current, column, true);
		passes++;
	}

	/* the rows shown changed, or their values: every column is computed again when it is next asked for */
	private void rowsChanged (RowChange change) {
		if (change.kind () == RowChange.ORDER) return;
		Arrays.fill (current, 0L);
		notify (-1);
	}

	/**
	 * Adds a listener to the changes of the summary.
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

	private void notify (int column) {
		for (Listener listener : listeners) listener.summaryChanged (column);
	}

	/**
	 * Stops following the view.
	 */
	public void dispose () {
		view.removeListener (follower);
		listeners = new Listener [0];
	}
}
