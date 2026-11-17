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
import java.util.*;
import java.util.function.*;

/**
 * The distinct texts of one column over a set of source rows, each with the number of rows that
 * have it: the model of a value list filter (the user checks the values to keep, see
 * {@link ColumnFilter#setValues(int, String...)}) and of grouping rows by a column.
 * <p>
 * The values are computed once, when the object is made, in one pass over the source rows; the
 * object does not change afterwards. An empty cell (a <code>null</code> text) is the empty text.
 * The values are in the order of a {@link Collator} for the default locale; texts the collator
 * finds equal stay distinct and are ordered by {@link String#compareTo(String)}.
 * </p>
 *
 * @see ColumnFilter#matchesExcept(int, int)
 * @since 3.138
 */
public final class ColumnValues {

	private final int column, total;
	private final String [] values;
	private final int [] counts;
	private final Collator collator = Collator.getInstance ();

	/**
	 * Returns the values of a column over the source rows that pass every other criterion of a
	 * filter: the choices of a value list filter on that column, including the values the
	 * column's own criterion hides.
	 *
	 * @param filter the filter, which must read cell texts
	 * @param column the column
	 * @return the values
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the filter is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the filter</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the filter has no cell text</li>
	 * </ul>
	 */
	public static ColumnValues of (ColumnFilter filter, int column) {
		RowChecks.nonNull (filter);
		RowChecks.range (column, filter.getColumnCount ());
		RowChecks.argument (filter.cellText () != null);
		return new ColumnValues (filter.cellText (), column, filter.sourceSize (), row -> filter.matchesExcept (row, column));
	}

	/**
	 * Computes the values of a column over the source rows a predicate accepts.
	 *
	 * @param text the cell text
	 * @param column the column, not negative
	 * @param sourceSize the number of source rows, not negative
	 * @param rows whether a source row is counted, or <code>null</code> to count every row
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the text is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the column or the size is negative</li>
	 * </ul>
	 */
	public ColumnValues (CellText text, int column, int sourceSize, IntPredicate rows) {
		RowChecks.nonNull (text);
		RowChecks.argument (column >= 0 && sourceSize >= 0);
		this.column = column;
		Map<String, int []> seen = new HashMap<> ();
		int counted = 0;
		for (int row = 0; row < sourceSize; row++) {
			if (rows != null && !rows.test (row)) continue;
			String value = text.text (row, column);
			seen.computeIfAbsent (value == null ? "" : value, key -> new int [1]) [0]++; //$NON-NLS-1$
			counted++;
		}
		total = counted;
		values = seen.keySet ().toArray (new String [0]);
		Arrays.sort (values, (a, b) -> {
			int order = collator.compare (a, b);
			return order != 0 ? order : a.compareTo (b);
		});
		counts = new int [values.length];
		for (int i = 0; i < values.length; i++) counts [i] = seen.get (values [i]) [0];
	}

	/**
	 * Returns the column.
	 *
	 * @return the column
	 */
	public int getColumn () {
		return column;
	}

	/**
	 * Returns the number of distinct values.
	 *
	 * @return the number of values
	 */
	public int size () {
		return values.length;
	}

	/**
	 * Returns a value.
	 *
	 * @param index the index of the value, in collator order
	 * @return the value; the empty text for empty cells
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the index is not an index of a value</li>
	 * </ul>
	 */
	public String getValue (int index) {
		return values [RowChecks.range (index, values.length)];
	}

	/**
	 * Returns the number of rows that have a value.
	 *
	 * @param index the index of the value
	 * @return the number of rows, at least one
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the index is not an index of a value</li>
	 * </ul>
	 */
	public int getCount (int index) {
		return counts [RowChecks.range (index, counts.length)];
	}

	/**
	 * Returns the index of a value.
	 *
	 * @param value the value; <code>null</code> is the empty text
	 * @return the index, or -1 when no row counted has the value
	 */
	public int indexOf (String value) {
		String key = value == null ? "" : value; //$NON-NLS-1$
		int low = 0, high = values.length - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1, order = collator.compare (values [mid], key);
			if (order == 0) order = values [mid].compareTo (key);
			if (order == 0) return mid;
			if (order < 0) low = mid + 1;
			else high = mid - 1;
		}
		return -1;
	}

	/**
	 * Returns the number of rows counted.
	 *
	 * @return the sum of the counts
	 */
	public int getTotal () {
		return total;
	}
}
