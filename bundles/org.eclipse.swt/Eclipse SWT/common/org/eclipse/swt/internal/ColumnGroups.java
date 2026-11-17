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
import java.util.function.*;

/**
 * The rows of an input view grouped by the text of one column: the model of "group by" in a
 * table or tree, as a {@link RowView}.
 * <p>
 * The view shows the input's rows group after group. The groups are the distinct texts of the
 * column (an empty cell is the empty text), in the order of a {@link Collator} for the default
 * locale; texts the collator finds equal stay distinct and are ordered by
 * {@link String#compareTo(String)}. Inside a group the rows keep the input's order, so a
 * {@link ColumnSort} below the groups sorts the rows of every group. Each group has a value, a
 * first view row and a size; {@link #groupOf(int)} answers the group of a view row.
 * </p>
 * <p>
 * The groups follow the input's deltas: a row that enters, leaves, is inserted, removed or
 * updated is placed into or taken out of its group alone, and the change is reported with the
 * same kind and the view rows it damaged, so a bound viewport repaints those rows only. A new
 * order of the input, a reset, a new column, or a batch larger than an eighth of the view
 * groups every row again in one pass and is reported as an order change or a reset. The rows
 * are kept in int lanes apart from any widget. With no column (-1) the view shows the input's
 * rows as they are and has no groups.
 * </p>
 *
 * @see ColumnValues
 * @since 3.138
 */
public final class ColumnGroups extends RowViewBase {

	private final CellText text;
	private final Collator collator = Collator.getInstance ();
	private final GroupLanes lanes = new GroupLanes (this::compare);
	private final IntUnaryOperator rank = sourceRow -> input.unmap (sourceRow);
	private int column;

	/**
	 * Constructs the groups of the rows of a view.
	 *
	 * @param input the view whose rows are grouped
	 * @param text the cell text of the source rows
	 * @param column the column whose texts name the groups, or -1 for no groups
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the input or the text is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the column is less than -1</li>
	 * </ul>
	 */
	public ColumnGroups (RowView input, CellText text, int column) {
		super (checked (input, text, column), 0);
		this.text = text;
		this.column = column;
		rebuild ();
	}

	private static RowView checked (RowView input, CellText text, int column) {
		RowChecks.nonNull (input);
		RowChecks.nonNull (text);
		RowChecks.argument (column >= -1);
		return input;
	}

	private int compare (String a, String b) {
		int byCollator = collator.compare (a, b);
		return byCollator != 0 ? byCollator : a.compareTo (b);
	}

	/* the group key of a source row: its text in the column, or one key for every row without a column */
	private String key (int sourceRow) {
		if (column < 0) return ""; //$NON-NLS-1$
		String value = text.text (sourceRow, column);
		return value == null ? "" : value; //$NON-NLS-1$
	}

	/**
	 * Returns the column whose texts name the groups.
	 *
	 * @return the column, or -1 when there are no groups
	 */
	public int getColumn () {
		return column;
	}

	/**
	 * Groups the rows by another column, or ungroups them.
	 *
	 * @param column the column, or -1 for no groups
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the column is less than -1</li>
	 * </ul>
	 */
	public void setColumn (int column) {
		RowChecks.argument (column >= -1);
		if (this.column == column) return;
		this.column = column;
		regroup (RowChange.RESET);
	}

	/**
	 * Returns the number of groups.
	 *
	 * @return the number of groups; 0 when the view is empty or has no column
	 */
	public int getGroupCount () {
		return column < 0 ? 0 : lanes.groups;
	}

	/**
	 * Returns the value of a group: the text of the column in each of its rows.
	 *
	 * @param group the group
	 * @return the value; the empty text for empty cells
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the group is not a group</li>
	 * </ul>
	 */
	public String getGroupValue (int group) {
		return lanes.values [RowChecks.range (group, getGroupCount ())];
	}

	/**
	 * Returns the first view row of a group.
	 *
	 * @param group the group
	 * @return the view row
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the group is not a group</li>
	 * </ul>
	 */
	public int getGroupStart (int group) {
		return lanes.starts [RowChecks.range (group, getGroupCount ())];
	}

	/**
	 * Returns the number of rows of a group.
	 *
	 * @param group the group
	 * @return the number of rows, at least one
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the group is not a group</li>
	 * </ul>
	 */
	public int getGroupSize (int group) {
		RowChecks.range (group, getGroupCount ());
		return lanes.starts [group + 1] - lanes.starts [group];
	}

	/**
	 * Returns the group of a view row.
	 *
	 * @param row the view row
	 * @return the group, or -1 when the view has no column
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the row is not a row of the view</li>
	 * </ul>
	 */
	public int groupOf (int row) {
		RowChecks.range (row, lanes.size);
		return column < 0 ? -1 : lanes.groupOf (row);
	}

	@Override
	public int size () {
		return lanes.size;
	}

	@Override
	public int map (int row) {
		return lanes.rows [RowChecks.range (row, lanes.size)];
	}

	@Override
	public int unmap (int sourceRow) {
		return lanes.position [RowChecks.range (sourceRow, lanes.position.length)];
	}

	@Override
	void inputChanged (RowChange in) {
		int kind = in.kind;
		if (kind == RowChange.RESET || kind == RowChange.ORDER || large (in)) {
			regroup (kind == RowChange.ORDER ? RowChange.ORDER : RowChange.RESET);
			return;
		}
		change.set (kind, in.start, in.count);
		for (int i = 0; i < in.rowCount; i++) change.add (in.rows [i]);
		switch (kind) {
			case RowChange.INSERTED -> {
				lanes.insertSourceRange (in.start, in.count);
				damageFrom (place (in));
			}
			case RowChange.ENTERED -> damageFrom (place (in));
			case RowChange.REMOVED -> damageFrom (lanes.removeSourceRange (in.start, in.count));
			case RowChange.LEFT -> damageFrom (take (in));
			default -> replace (in);
		}
		fire ();
	}

	/* a batch that one pass places faster than one row at a time */
	private boolean large (RowChange in) {
		return in.rowCount > 32 && in.rowCount > lanes.size >> 3;
	}

	private int place (RowChange in) {
		int first = -1;
		for (int i = 0; i < in.rowCount; i++) {
			int row = lanes.insert (in.rows [i], key (in.rows [i]), rank);
			first = first < 0 ? row : Math.min (first, row);
		}
		return first;
	}

	private int take (RowChange in) {
		int first = -1;
		for (int i = 0; i < in.rowCount; i++) {
			int row = lanes.remove (in.rows [i]);
			if (row >= 0) first = first < 0 ? row : Math.min (first, row);
		}
		return first;
	}

	/*
	 * Updated rows may change group, or place in the input's order. All of them are taken out before any is placed
	 * again, so each is placed among rows that are in the input's order. A row outside the span from the lowest to
	 * the highest place an updated row had or has keeps its place, so that span is the damage.
	 */
	private void replace (RowChange in) {
		int [] moved = new int [in.rowCount];
		int n = 0, low = Integer.MAX_VALUE, high = -1;
		for (int i = 0; i < in.rowCount; i++) {
			int from = lanes.position [in.rows [i]];
			if (from < 0) continue;
			low = Math.min (low, from);
			high = Math.max (high, from);
			moved [n++] = in.rows [i];
		}
		for (int i = 0; i < n; i++) lanes.remove (moved [i]);
		for (int i = 0; i < n; i++) lanes.insert (moved [i], key (moved [i]), rank);
		for (int i = 0; i < n; i++) {
			low = Math.min (low, lanes.position [moved [i]]);
			high = Math.max (high, lanes.position [moved [i]]);
		}
		if (n > 0) change.damage (low, high);
	}

	private void damageFrom (int first) {
		if (first >= 0) change.damage (first, lanes.size - 1);
	}

	private void regroup (int kind) {
		rebuild ();
		change.set (kind, 0, 0);
		change.damage (0, lanes.size - 1);
		fire ();
	}

	/* one pass over the input's rows */
	private void rebuild () {
		int n = input.size ();
		int [] source = new int [n];
		String [] keys = new String [n];
		for (int i = 0; i < n; i++) {
			source [i] = input.map (i);
			keys [i] = key (source [i]);
		}
		lanes.rebuild (source, keys, input.sourceSize ());
		fullPasses++;
	}

	@Override
	public String toString () {
		return "ColumnGroups [column " + column + ", " + getGroupCount () + " groups, " + lanes.size + " rows]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
