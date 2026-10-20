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

import java.text.SimpleDateFormat;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.m3.M3Footer;

/**
 * The footer of a Table (M3Footer), platform-neutral: the settings, one {@link FooterAggregate} per aggregated column,
 * the change counters and the texts of the footer bar. A Table that keeps its rows in lanes (the win32 viewport) feeds
 * every change as a delta ({@link #cellChanged}, {@link #rowsInserted}, {@link #rowsRemoved}), so every read is O(1)
 * or O(log n); any other Table rebuilds the aggregates from its items at every read (O(rows), documented on the API). A Table without a footer has no model.
 */
final class TableFooterModel {
	/** The cells a footer reads: the row count, and a row's text in a column. */
	interface Cells {
		int rows ();

		String text (int column, int row);
	}

	final Table table;
	final Cells cells;
	/* fed by deltas (the lanes of the win32 viewport); otherwise rebuilt from the items when read */
	final boolean deltas;
	boolean visible;
	int bar, scope;
	String hint = ""; //$NON-NLS-1$
	/* per column index: the kind, the custom function and the aggregate (null for NONE) */
	int [] kinds = new int [0];
	M3Footer.Function [] functions = new M3Footer.Function [0];
	FooterAggregate [] aggregates = new FooterAggregate [0];
	/* CHANGES: rows added, removed and changed since markChanges; FRESHNESS: the last change and the changes per second */
	long added, removed, changed;
	long lastChange;
	private long rateSecond, rateThis, rateLast;
	/* SELECTION: count, numbers and sum of the selected cells of the first aggregated column, rebuilt when invalid */
	private boolean selectionValid;
	private long selectedCount, selectedNumbers;
	private double selectedSum;

	TableFooterModel (Table table, Cells cells, boolean deltas) {
		this.table = table;
		this.cells = cells;
		this.deltas = deltas;
	}

	boolean shown () {
		return visible || bar != 0;
	}

	int kind (int column) {
		return column < kinds.length ? kinds [column] : M3Footer.NONE;
	}

	M3Footer.Function function (int column) {
		return column < functions.length ? functions [column] : null;
	}

	/** Sets the aggregate of a column; a Table fed by deltas builds it now from its rows (O(rows), once). */
	void setAggregate (int column, int kind, M3Footer.Function function) {
		if (column >= kinds.length) {
			int length = column + 1;
			kinds = Arrays.copyOf (kinds, length);
			functions = Arrays.copyOf (functions, length);
			aggregates = Arrays.copyOf (aggregates, length);
		}
		kinds [column] = kind;
		functions [column] = function;
		aggregates [column] = null;
		selectionValid = false;
		if (deltas) aggregates [column] = build (column);
	}

	private FooterAggregate build (int column) {
		int kind = kinds [column];
		if (kind == M3Footer.NONE || kind == M3Footer.CUSTOM && functions [column] == null) return null;
		FooterAggregate aggregate = new FooterAggregate (kind, functions [column]);
		int rows = cells.rows ();
		if (aggregate.positional ()) aggregate.inserted (0, rows);
		for (int row = 0; row < rows; row++) {
			String text = cells.text (column, row);
			aggregate.add (text);
			if (aggregate.positional () && text != null) aggregate.set (row, text);
		}
		return aggregate;
	}

	/* a Table that is not fed by deltas rebuilds its aggregates from its items at every read */
	private void refresh () {
		if (deltas) return;
		for (int column = 0; column < kinds.length; column++) aggregates [column] = build (column);
		selectionValid = false;
	}

	private void touch (long rows) {
		long now = System.currentTimeMillis ();
		lastChange = now;
		long second = now / 1000;
		if (second != rateSecond) {
			rateLast = second == rateSecond + 1 ? rateThis : 0;
			rateThis = 0;
			rateSecond = second;
		}
		rateThis += rows;
	}

	/* ---------------------------------------------------------------- deltas */

	/** The text of a cell changed from <code>oldText</code> to <code>newText</code>. */
	void cellChanged (int column, int row, String oldText, String newText) {
		if (Objects.equals (oldText, newText)) return;
		FooterAggregate aggregate = column < aggregates.length ? aggregates [column] : null;
		if (aggregate != null) {
			aggregate.remove (oldText);
			aggregate.add (newText);
			if (aggregate.positional ()) aggregate.set (row, newText);
		}
		changed++;
		selectionValid = false;
		touch (1);
	}

	/** Rows [index, index + count) came in empty. */
	void rowsInserted (int index, int count) {
		if (count <= 0) return;
		for (FooterAggregate aggregate : aggregates) {
			if (aggregate != null) aggregate.inserted (index, count);
		}
		added += count;
		touch (count);
	}

	/** Rows [index, index + count) are about to leave: their cells leave the aggregates (read before the lanes move). */
	void rowsRemoved (int index, int count) {
		if (count <= 0) return;
		for (int column = 0; column < aggregates.length; column++) {
			FooterAggregate aggregate = aggregates [column];
			if (aggregate == null) continue;
			for (int row = index; row < index + count; row++) aggregate.remove (cells.text (column, row));
			aggregate.removed (index, count);
		}
		removed += count;
		selectionValid = false;
		touch (count);
	}

	/** Every row left (setItemCount (0), removeAll): the aggregates start again empty. */
	void rowsCleared (int count) {
		for (int column = 0; column < aggregates.length; column++) {
			if (aggregates [column] != null) aggregates [column] = new FooterAggregate (kinds [column], functions [column]);
		}
		removed += count;
		selectionValid = false;
		touch (count);
	}

	/** A column was inserted at <code>index</code>: the settings of the columns after it move. */
	void columnInserted (int index) {
		if (index >= kinds.length) return;
		kinds = insert (kinds, index);
		functions = insert (functions, index);
		aggregates = insert (aggregates, index);
	}

	/** The column at <code>index</code> was removed. */
	void columnRemoved (int index) {
		if (index >= kinds.length) return;
		int length = kinds.length - 1;
		System.arraycopy (kinds, index + 1, kinds, index, length - index);
		System.arraycopy (functions, index + 1, functions, index, length - index);
		System.arraycopy (aggregates, index + 1, aggregates, index, length - index);
		kinds = Arrays.copyOf (kinds, length);
		functions = Arrays.copyOf (functions, length);
		aggregates = Arrays.copyOf (aggregates, length);
		selectionValid = false;
	}

	private static int [] insert (int [] array, int index) {
		int [] result = new int [array.length + 1];
		System.arraycopy (array, 0, result, 0, index);
		System.arraycopy (array, index, result, index + 1, array.length - index);
		return result;
	}

	private static <T> T [] insert (T [] array, int index) {
		T [] result = Arrays.copyOf (array, array.length + 1);
		System.arraycopy (array, index, result, index + 1, array.length - index);
		result [index] = null;
		return result;
	}

	/** The selection changed. */
	void selectionChanged () {
		selectionValid = false;
	}

	void markChanges () {
		added = removed = changed = 0;
	}

	/* ---------------------------------------------------------------- reads */

	/** The value of a column's aggregate, NaN without one. */
	double value (int column) {
		refresh ();
		FooterAggregate aggregate = column < aggregates.length ? aggregates [column] : null;
		return aggregate == null ? Double.NaN : aggregate.value (cells.rows ());
	}

	/** The text of a column's footer cell, empty without an aggregate. */
	String text (int column) {
		refresh ();
		FooterAggregate aggregate = column < aggregates.length ? aggregates [column] : null;
		return aggregate == null ? "" : aggregate.text (cells.rows ()); //$NON-NLS-1$
	}

	/** The first column with an aggregate, or -1. */
	int firstAggregated () {
		for (int column = 0; column < kinds.length; column++) {
			if (kinds [column] != M3Footer.NONE) return column;
		}
		return -1;
	}

	/**
	 * The breakdown of a column for the footer popup: rows, filled cells, numbers, sum, average, and the least and
	 * greatest numbers and the distinct texts (one pass over the rows: the popup is an explicit request).
	 */
	String breakdown (int column) {
		refresh ();
		int rows = cells.rows ();
		long filled = 0, numbers = 0;
		double sum = 0, min = Double.NaN, max = Double.NaN;
		Set<String> distinct = new HashSet<> ();
		for (int row = 0; row < rows; row++) {
			String text = cells.text (column, row);
			if (text == null || text.isEmpty ()) continue;
			filled++;
			distinct.add (text);
			double number = FooterAggregate.number (text);
			if (Double.isNaN (number)) continue;
			numbers++;
			sum += number;
			min = Double.isNaN (min) ? number : Math.min (min, number);
			max = Double.isNaN (max) ? number : Math.max (max, number);
		}
		StringBuilder text = new StringBuilder ();
		text.append ("rows ").append (rows).append ('\n'); //$NON-NLS-1$
		text.append ("filled ").append (filled).append ('\n'); //$NON-NLS-1$
		text.append ("distinct ").append (distinct.size ()).append ('\n'); //$NON-NLS-1$
		text.append ("numbers ").append (numbers); //$NON-NLS-1$
		if (numbers > 0) {
			text.append ('\n').append ("sum ").append (FooterAggregate.format (sum)); //$NON-NLS-1$
			text.append ('\n').append ("avg ").append (FooterAggregate.format (sum / numbers)); //$NON-NLS-1$
			text.append ('\n').append ("min ").append (FooterAggregate.format (min)); //$NON-NLS-1$
			text.append ('\n').append ("max ").append (FooterAggregate.format (max)); //$NON-NLS-1$
		}
		M3Footer.Function function = function (column);
		if (kind (column) == M3Footer.CUSTOM && function != null) text.append ('\n').append ("custom ").append (FooterAggregate.format (value (column))); //$NON-NLS-1$
		return text.toString ();
	}

	/** The text of a part of the footer bar; empty when the part has nothing to say. */
	String partText (int part) {
		return switch (part) {
			case M3Footer.COUNTS -> countsText ();
			case M3Footer.SELECTION -> selectionText ();
			case M3Footer.SORT -> sortText ();
			case M3Footer.FRESHNESS -> freshnessText ();
			case M3Footer.CHANGES -> added + removed + changed == 0 ? "" : "+" + added + " -" + removed + " ~" + changed; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			case M3Footer.POSITION -> positionText ();
			case M3Footer.HINTS -> hint;
			default -> ""; //$NON-NLS-1$
		};
	}

	/** The footer bar: the texts of its parts in order, separated by three spaces. */
	String barText () {
		StringBuilder text = new StringBuilder ();
		for (int part = M3Footer.COUNTS; part <= M3Footer.PAGING; part <<= 1) {
			if ((bar & part) == 0) continue;
			String piece = partText (part);
			if (piece.isEmpty ()) continue;
			if (text.length () > 0) text.append ("   "); //$NON-NLS-1$
			text.append (piece);
		}
		return text.toString ();
	}

	private String countsText () {
		int rows = cells.rows ();
		int selected = table.getSelectionCount ();
		return rows + (rows == 1 ? " row" : " rows") + (selected > 0 ? ", " + selected + " selected" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	}

	private String selectionText () {
		int column = firstAggregated ();
		if (column < 0) return ""; //$NON-NLS-1$
		if (!selectionValid) {
			selectedCount = selectedNumbers = 0;
			selectedSum = 0;
			for (int row : table.getSelectionIndices ()) {
				selectedCount++;
				double number = FooterAggregate.number (cells.text (column, row));
				if (Double.isNaN (number)) continue;
				selectedNumbers++;
				selectedSum += number;
			}
			selectionValid = true;
		}
		if (selectedNumbers == 0) return ""; //$NON-NLS-1$
		return "selected: sum " + FooterAggregate.format (selectedSum) + ", avg " + FooterAggregate.format (selectedSum / selectedNumbers); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private String sortText () {
		TableColumn column = table.getSortColumn ();
		int direction = table.getSortDirection ();
		if (column == null || direction == SWT.NONE) return ""; //$NON-NLS-1$
		return "sorted by " + column.getText () + (direction == SWT.UP ? " ascending" : " descending"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private String freshnessText () {
		if (lastChange == 0) return ""; //$NON-NLS-1$
		long second = System.currentTimeMillis () / 1000;
		long rate = second == rateSecond ? rateLast : second == rateSecond + 1 ? rateThis : 0;
		String time = new SimpleDateFormat ("HH:mm:ss").format (new Date (lastChange)); //$NON-NLS-1$
		return "updated " + time + (rate > 0 ? ", " + rate + "/s" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private String positionText () {
		int rows = cells.rows ();
		if (rows == 0) return ""; //$NON-NLS-1$
		int top = table.getTopIndex ();
		int height = Math.max (1, table.getItemHeight ());
		int last = Math.min (rows, top + Math.max (1, table.getClientArea ().height / height));
		return "rows " + (top + 1) + "-" + last + " of " + rows; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
