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
package org.eclipse.swt.m3;

/**
 * The footer of a Table or a Tree (M3 behaviours, "What a footer can encode"; the same API on both widgets, agreed
 * between the Table and Tree tracks): a row of per-column aggregate cells pinned at the bottom of the viewport, and an
 * optional footer bar of parts (counts, selection summary, sort, filters, freshness, problems, changes, position,
 * hints, paging). Every value is kept by deltas from the widget's rows; a footer that is off keeps no state.
 * <p>
 * Widget API: <code>setFooterVisible</code>, <code>setFooterBar</code>, <code>getFooterText (int part)</code>,
 * <code>setFooterHint</code>, <code>markFooterChanges</code>, <code>setFooterScope</code>; column API:
 * <code>setFooterAggregate</code>, <code>setFooterFunction</code>, <code>getFooterText</code>,
 * <code>getFooterValue</code>. A view declares its footer in its <code>.m3view</code> spec:
 * <code>footer=counts, selection, aggregates:sum(size), sort, filters, freshness, problems, changes, position,
 * hints, paging</code>.
 * </p>
 *
 * @since 3.138
 */
public final class M3Footer {

	/** Aggregate kind: no aggregate in the column's footer cell. */
	public static final int NONE = 0;
	/** Aggregate kind: the rows in scope. */
	public static final int COUNT = 1;
	/** Aggregate kind: the rows in scope whose cell is not empty. */
	public static final int FILLED = 2;
	/** Aggregate kind: the distinct non-empty cell texts. */
	public static final int DISTINCT = 3;
	/** Aggregate kind: the sum of the cells that are numbers. */
	public static final int SUM = 4;
	/** Aggregate kind: the average of the cells that are numbers. */
	public static final int AVERAGE = 5;
	/** Aggregate kind: the least cell that is a number. */
	public static final int MIN = 6;
	/** Aggregate kind: the greatest cell that is a number. */
	public static final int MAX = 7;
	/** Aggregate kind: the column's {@link Function} over the cells that are numbers, in row order; NONE without one. */
	public static final int CUSTOM = 8;

	/** Footer bar part: shown, total and selected rows. */
	public static final int COUNTS = 1;
	/** Footer bar part: count, sum and average of the selected cells of the first aggregated column. */
	public static final int SELECTION = 1 << 1;
	/** Footer bar part: the sort column and direction. */
	public static final int SORT = 1 << 2;
	/** Footer bar part: the active filters. */
	public static final int FILTERS = 1 << 3;
	/** Footer bar part: the time of the last change and the changes per second. */
	public static final int FRESHNESS = 1 << 4;
	/** Footer bar part: error and warning counts of the rows. */
	public static final int PROBLEMS = 1 << 5;
	/** Footer bar part: rows added, removed and changed since the last <code>markFooterChanges</code>. */
	public static final int CHANGES = 1 << 6;
	/** Footer bar part: the rows of the window. */
	public static final int POSITION = 1 << 7;
	/** Footer bar part: the hint of the view. */
	public static final int HINTS = 1 << 8;
	/** Footer bar part: paging controls. */
	public static final int PAGING = 1 << 9;

	/** Footer scope: every row. */
	public static final int ALL = 0;
	/** Footer scope: the rows the filter keeps (a Table without a filter: every row). */
	public static final int FILTERED = 1;
	/** Footer scope: the rows shown (a Tree: the rows of expanded branches; a Table: as FILTERED). */
	public static final int VISIBLE = 2;

	/**
	 * A custom aggregate: an associative combination of the column's numbers in row order, so a widget keeps it per
	 * subtree or per range in O(changed log n).
	 */
	public interface Function {
		/**
		 * The value of no rows: <code>combine (identity (), x) == x</code>.
		 *
		 * @return the identity
		 */
		double identity ();

		/**
		 * Combines two adjacent values; associative.
		 *
		 * @param a the value of the rows before
		 * @param b the value of the rows after
		 * @return the combined value
		 */
		double combine (double a, double b);
	}

	private M3Footer () {
	}
}
