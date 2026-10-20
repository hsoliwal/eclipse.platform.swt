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
 *     Hitesh Soliwal (Synexia) - adapted from TV_5.0 treeviewer IMultiPropertyTextFilter,
 *         MultiPropertyTextFilter, ElementFilter, FilteredTreeElementProvider and
 *         TreeFilterStateProvider (docs/m3/sort-filter/PROVENANCE.tsv)
 *     Hitesh Soliwal (Synexia) - filtering the model off the UI thread adapted from x86core
 *         jsonviewer LazyTreeFilter and TreeFilterControl (docs/m3/sort-filter/PROVENANCE.tsv)
 *******************************************************************************/
package org.eclipse.swt.m3;

import java.util.*;
import java.util.function.*;

import org.eclipse.swt.widgets.*;

/**
 * Per-column filters over the rows of a source (enhanced M3 API).
 * <p>
 * Each column may have one criterion: a text match ({@link M3TextMatch}: contains, starts with,
 * equals, wildcard or regular expression, optionally negated or without case) or a value list
 * (texts, or value ids). One more criterion, addressed as {@link #ALL_COLUMNS}, matches one text
 * against several columns (a row matches when any of them matches). Criteria combine from left
 * to right, the multi-column criterion first and then the columns in order, each with
 * {@link #AND} (the default) or {@link #OR}. The view shows the source rows that pass, in source
 * order.
 * </p>
 * <p>
 * Every criterion keeps a bit mask over the source rows; combining is a word-wide fold, so
 * changing one criterion re-tests one column. Cell texts come from an {@link M3CellText}; widget
 * items are never read. In tree mode ({@link #setParents(IntUnaryOperator)}) every ancestor of a
 * passing row is shown too, so that the row stays reachable.
 * </p>
 * <p>
 * Criterion changes report the rows that left and entered the view to the listeners, so an
 * {@link M3ColumnSort} over this filter updates incrementally.
 * </p>
 *
 * @see M3ColumnSort
 * @since 3.138
 */
public final class M3ColumnFilter extends M3RowViewBase {

	/** Logic: a row passes when it passes the criteria before and this one. */
	public static final int AND = 0;

	/** Logic: a row passes when it passes the criteria before or this one. */
	public static final int OR = 1;

	/** The index of the multi-column criterion. */
	public static final int ALL_COLUMNS = -1;

	private final M3CellText text;
	private final M3FilterCriterion [] criteria;
	private M3FilterCriterion [] pending;
	private boolean [] dirty;
	volatile int generation;
	private int appliedGeneration;
	/* worker results applied on the UI thread (read by the hidden-viewport gate) */
	int applies;
	M3ColumnSort follower;
	private M3FilterWorker worker;
	private IntUnaryOperator parentOf;
	private long [] matched = {}, kept = {}, previous = {};
	private int [] rows = {}, rank = {}, spare = {}, flipRows = {}, flipAt = {};
	/* tree mode: the number of matching strict descendants of every row */
	private int [] below = {};
	private int size;
	private final M3FilterDelta delta = new M3FilterDelta (this);

	/**
	 * Constructs a filter without criteria (every row passes).
	 *
	 * @param columnCount the number of columns, not negative
	 * @param sourceSize the number of source rows, not negative
	 * @param text the cell text read by text criteria, or <code>null</code> when only value-id lists are used
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if a count is negative</li>
	 * </ul>
	 */
	public M3ColumnFilter (int columnCount, int sourceSize, M3CellText text) {
		super (null, sourceSize);
		M3Checks.argument (columnCount >= 0);
		this.text = text;
		criteria = new M3FilterCriterion [columnCount + 1];
		for (int i = 0; i < criteria.length; i++) criteria [i] = new M3FilterCriterion ();
		pending = criteria;
		dirty = new boolean [criteria.length];
		recompute (null);
	}

	/**
	 * Returns the number of columns.
	 *
	 * @return the column count
	 */
	public int getColumnCount () {
		return criteria.length - 1;
	}

	/**
	 * Sets the text criterion of a column; a <code>null</code> pattern, or one without tokens,
	 * removes the criterion.
	 *
	 * @param column the column
	 * @param pattern the pattern text (see {@link M3TextMatch}), or <code>null</code>
	 * @param mode the mode and flags of {@link M3TextMatch}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the mode or a regular expression is not valid, or the filter has no cell text</li>
	 * </ul>
	 */
	public void setText (int column, String pattern, int mode) {
		M3Checks.range (column, criteria.length - 1);
		setText (column, new int [] {column}, pattern, mode);
	}

	/**
	 * Sets the multi-column criterion: a row matches when the pattern matches any of the columns
	 * (with {@link M3TextMatch#NEGATE}: none of them). A <code>null</code> pattern, or one without
	 * tokens, removes the criterion.
	 *
	 * @param columns the columns
	 * @param pattern the pattern text, or <code>null</code>
	 * @param mode the mode and flags of {@link M3TextMatch}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the columns are null</li>
	 *    <li>ERROR_INVALID_RANGE - if a column is not a column</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the mode or a regular expression is not valid, or the filter has no cell text</li>
	 * </ul>
	 */
	public void setText (int [] columns, String pattern, int mode) {
		M3Checks.nonNull (columns);
		for (int column : columns) M3Checks.range (column, criteria.length - 1);
		setText (ALL_COLUMNS, columns.clone (), pattern, mode);
	}

	private void setText (int index, int [] columns, String pattern, int mode) {
		M3TextMatch match = pattern == null ? null : M3TextMatch.compile (pattern, mode);
		M3Checks.argument (text != null || match == null);
		M3FilterCriterion before = pending [index + 1];
		boolean narrowing = worker == null && match != null && before.match != null && Arrays.equals (columns, before.columns) && match.narrows (before.match);
		M3FilterCriterion criterion = edit (index + 1);
		if (match != null && match.isActive ()) {
			criterion.match = match;
			criterion.columns = columns;
		}
		if (narrowing) narrowed (criterion); else changed (criterion);
	}

	/* typing more of a contains or starts-with pattern: only the rows the previous pattern kept are tested again */
	private void narrowed (M3FilterCriterion criterion) {
		generation++;
		criterion.narrow (0, M3RowMask.words (sourceSize ()), text);
		recompute (null);
	}

	/**
	 * Sets a value list on a column: a row passes when its cell text (an empty cell is the empty
	 * text) is one of the values. <code>null</code> removes the criterion.
	 *
	 * @param column the column
	 * @param values the values, or <code>null</code>
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the filter has no cell text</li>
	 * </ul>
	 */
	public void setValues (int column, String... values) {
		M3Checks.range (column, criteria.length - 1);
		M3Checks.argument (text != null || values == null);
		M3FilterCriterion criterion = edit (column + 1);
		if (values != null) {
			criterion.values = new HashSet<> (Arrays.asList (values));
			criterion.columns = new int [] {column};
		}
		changed (criterion);
	}

	/**
	 * Sets a value list of ids on a column: a row passes when the id of its value is one of the
	 * allowed ids (ids are not negative; a negative id never passes).
	 *
	 * @param column the column
	 * @param valueId the value id of a source row
	 * @param allowedIds the allowed ids
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 *    <li>ERROR_NULL_ARGUMENT - if the function or the ids are null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if an id is negative</li>
	 * </ul>
	 */
	public void setValues (int column, IntUnaryOperator valueId, int... allowedIds) {
		M3Checks.range (column, criteria.length - 1);
		M3Checks.nonNull (valueId);
		M3Checks.nonNull (allowedIds);
		long [] allowed = {};
		for (int id : allowedIds) {
			M3Checks.argument (id >= 0);
			allowed = M3RowMask.ensure (allowed, id + 1);
			M3RowMask.set (allowed, id, true);
		}
		M3FilterCriterion criterion = edit (column + 1);
		criterion.valueId = valueId;
		criterion.allowed = allowed;
		criterion.columns = new int [] {column};
		changed (criterion);
	}

	/**
	 * Removes the criterion of a column, or of {@link #ALL_COLUMNS}.
	 *
	 * @param column the column, or {@link #ALL_COLUMNS}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is neither a column nor ALL_COLUMNS</li>
	 * </ul>
	 */
	public void clear (int column) {
		criterion (column);
		changed (edit (column + 1));
	}

	/** Removes every criterion: every row passes. */
	public void clearAll () {
		for (int i = 0; i < criteria.length; i++) edit (i);
		changed (null);
	}

	/**
	 * Sets how the criterion of a column combines with the criteria before it.
	 *
	 * @param column the column, or {@link #ALL_COLUMNS}
	 * @param logic {@link #AND} or {@link #OR}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is neither a column nor ALL_COLUMNS</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the logic is neither AND nor OR</li>
	 * </ul>
	 */
	public void setLogic (int column, int logic) {
		criterion (column);
		M3Checks.argument (logic == AND || logic == OR);
		if (worker != null) pending [column + 1] = pending [column + 1].copy ();
		pending [column + 1].logic = logic;
		changed (null);
	}

	/**
	 * Returns how the criterion of a column combines with the criteria before it.
	 *
	 * @param column the column, or {@link #ALL_COLUMNS}
	 * @return {@link #AND} or {@link #OR}
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is neither a column nor ALL_COLUMNS</li>
	 * </ul>
	 */
	public int getLogic (int column) {
		return criterion (column).logic;
	}

	/**
	 * Returns whether a column, or {@link #ALL_COLUMNS}, has a criterion.
	 *
	 * @param column the column, or {@link #ALL_COLUMNS}
	 * @return whether it has a criterion
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is neither a column nor ALL_COLUMNS</li>
	 * </ul>
	 */
	public boolean isActive (int column) {
		return criterion (column).isActive ();
	}

	/**
	 * Returns the text criterion of a column, or <code>null</code> when it has none.
	 *
	 * @param column the column, or {@link #ALL_COLUMNS}
	 * @return the match, or <code>null</code>
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is neither a column nor ALL_COLUMNS</li>
	 * </ul>
	 */
	public M3TextMatch getMatch (int column) {
		return criterion (column).match;
	}

	/**
	 * Returns the columns of the multi-column criterion (empty when it is not set).
	 *
	 * @return a copy of the columns
	 */
	public int [] getColumns () {
		return pending [0].columns.clone ();
	}

	/**
	 * Returns whether a source row passes the criteria (before the tree ancestor rule).
	 *
	 * @param sourceRow the source row
	 * @return whether it passes
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the row is not a source row</li>
	 * </ul>
	 */
	public boolean matches (int sourceRow) {
		return M3RowMask.get (matched, M3Checks.range (sourceRow, sourceSize ()));
	}

	/**
	 * Turns tree mode on: every ancestor of a passing row is shown. The parent of a root is -1.
	 * <code>null</code> turns tree mode off.
	 *
	 * @param parentOf the parent source row of a source row, or -1
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if a parent is neither -1 nor a source row</li>
	 * </ul>
	 */
	public void setParents (IntUnaryOperator parentOf) {
		this.parentOf = parentOf;
		changed (null);
	}

	@Override
	public int size () {
		return size;
	}

	@Override
	public int map (int row) {
		return rows [M3Checks.range (row, size)];
	}

	@Override
	public int unmap (int sourceRow) {
		return M3RowMask.rank (kept, rank, M3Checks.range (sourceRow, sourceSize ()));
	}

	private M3FilterCriterion criterion (int column) {
		return pending [M3Checks.range (column + 1, criteria.length)];
	}

	/* the criterion to fill: cleared in place when synchronous, a fresh pending one when asynchronous */
	private M3FilterCriterion edit (int index) {
		if (worker == null) {
			criteria [index].clear ();
			return criteria [index];
		}
		M3FilterCriterion fresh = new M3FilterCriterion ();
		fresh.logic = pending [index].logic;
		pending [index] = fresh;
		dirty [index] = true;
		return fresh;
	}

	private void changed (M3FilterCriterion criterion) {
		generation++;
		if (worker != null) {
			worker.schedule ();
			return;
		}
		if (criterion != null) {
			int n = sourceSize ();
			criterion.mask = M3RowMask.ensure (criterion.mask, n);
			if (criterion.isActive ()) criterion.evaluate (0, n, text);
		}
		recompute (null);
	}

	@Override
	void inputChanged (M3RowChange in) {
		generation++;
		if (worker != null && anyDirty ()) worker.schedule ();
		if (in.kind == M3RowChange.UPDATED || parentOf == null && in.kind != M3RowChange.RESET) {
			applyDelta (in);
			return;
		}
		/* a new source (RESET), or a structural edit in tree mode (the parents of removed rows are gone) */
		fullPasses++;
		int n = sourceSize ();
		switch (in.kind) {
			case M3RowChange.INSERTED: shift (in.start, in.count, true); evaluate (in.start, in.start + in.count); break;
			case M3RowChange.REMOVED: shift (in.start, in.count, false); break;
			case M3RowChange.UPDATED: for (int i = 0; i < in.rowCount; i++) evaluate (in.rows [i], in.rows [i] + 1); break;
			default: for (M3FilterCriterion criterion : criteria) criterion.mask = new long [M3RowMask.words (n)]; evaluate (0, n); break;
		}
		recompute (in);
	}

	/*
	 * a model delta of a flat filter, applied to the edited rows only: their criterion bits are evaluated (bit flips),
	 * the rows whose status flips are moved in the rows lane in one pass from the first of them, the word ranks are
	 * recounted from there, and the change is reported from the edited rows (no fold, compaction or scan over n)
	 */
	private void applyDelta (M3RowChange in) {
		int n = sourceSize (), start = in.start, count = in.count;
		switch (in.kind) {
			case M3RowChange.INSERTED: {
				int old = n - count, at = positionBefore (start, old);
				shift (start, count, true);
				matched = shift (matched, old, n, start, count, true);
				kept = shift (kept, old, n, start, count, true);
				evaluate (start, start + count);
				int entering = 0;
				for (int row = start; row < start + count; row++) {
					if (M3FilterCriterion.passes (criteria, row)) {
						M3RowMask.set (matched, row, true);
						M3RowMask.set (kept, row, true);
						entering++;
					}
				}
				rows = capacity (rows, size + entering);
				for (int i = size - 1; i >= at; i--) rows [i + entering] = rows [i] + count;
				for (int row = start, p = at; p < at + entering; row++) {
					if (M3RowMask.get (kept, row)) rows [p++] = row;
				}
				size += entering;
				rerank (start >>> 6, n);
				delta.structural (in, kept);
				for (int row = start; row < start + count; row++) {
					if (M3RowMask.get (kept, row)) M3RowMask.set (previous, row, true);
				}
				break;
			}
			case M3RowChange.REMOVED: {
				int old = n + count, from = positionBefore (start, old), to = positionBefore (start + count, old);
				shift (start, count, false);
				matched = shift (matched, old, n, start, count, false);
				kept = shift (kept, old, n, start, count, false);
				for (int i = to; i < size; i++) rows [i - to + from] = rows [i] - count;
				size -= to - from;
				rerank (start >>> 6, n);
				delta.structural (in, kept);
				break;
			}
			default:
				updated (in);
				break;
		}
	}

	/* the view position of a source row (the number of kept rows before it) in lanes of oldSize source rows */
	private int positionBefore (int sourceRow, int oldSize) {
		if (sourceRow >= oldSize) return size;
		int w = sourceRow >>> 6;
		return rank [w] + Long.bitCount (kept [w] & (1L << sourceRow) - 1);
	}

	/*
	 * an UPDATED edit (values changed, structure kept): the rows whose kept status flips leave or enter the rows
	 * lane, merged in one pass from the first flip; the events are built from the flips and the edited rows
	 */
	private void updated (M3RowChange in) {
		for (int i = 0; i < in.rowCount; i++) evaluate (in.rows [i], in.rows [i] + 1);
		int flips = parentOf == null ? flatFlips (in) : treeFlips (in);
		if (flips > 0) merge (flips);
		delta.changedRows (in, flipRows, flips, previous, kept);
		for (int f = 0; f < flips; f++) {
			int row = flipRows [f] < 0 ? ~flipRows [f] : flipRows [f];
			M3RowMask.set (previous, row, flipRows [f] >= 0);
		}
	}

	/* flat: the edited rows whose fold changed, ascending, ~row for a row that leaves */
	private int flatFlips (M3RowChange in) {
		flipRows = capacity (flipRows, in.rowCount);
		int flips = 0;
		for (int i = 0; i < in.rowCount; i++) {
			int row = in.rows [i];
			boolean was = M3RowMask.get (kept, row);
			if (M3FilterCriterion.passes (criteria, row) == was) continue;
			M3RowMask.set (matched, row, !was);
			flipRows [flips++] = was ? ~row : row;
		}
		return flips;
	}

	/*
	 * tree: a row is kept when it matches or has a matching descendant (below > 0). A match flip of an edited row
	 * moves the descendant counts of its ancestors by one (O(depth)); the candidates for a kept flip are the edited
	 * rows and the ancestors whose count crossed zero. Returns the flips, ascending, ~row for a row that leaves.
	 */
	private int treeFlips (M3RowChange in) {
		int candidates = 0;
		for (int i = 0; i < in.rowCount; i++) {
			int row = in.rows [i];
			boolean match = M3FilterCriterion.passes (criteria, row);
			if (match == M3RowMask.get (matched, row)) continue;
			M3RowMask.set (matched, row, match);
			candidates = candidate (row, candidates);
			int step = match ? 1 : -1;
			for (int p = parentOf.applyAsInt (row); p != -1; p = parentOf.applyAsInt (p)) {
				int before = below [p];
				below [p] = before + step;
				if (before == 0 || before + step == 0) candidates = candidate (p, candidates);
			}
		}
		Arrays.sort (flipRows, 0, candidates);
		int flips = 0, last = -1;
		for (int k = 0; k < candidates; k++) {
			int row = flipRows [k];
			if (row == last) continue;
			last = row;
			boolean now = M3RowMask.get (matched, row) || below [row] > 0;
			if (now != M3RowMask.get (kept, row)) flipRows [flips++] = now ? row : ~row;
		}
		return flips;
	}

	private int candidate (int row, int count) {
		if (flipRows.length == count) flipRows = Arrays.copyOf (flipRows, Math.max (16, count << 1));
		flipRows [count] = row;
		return count + 1;
	}

	/* moves the flipped rows (ascending) in the rows lane in one pass from the first, sets their kept bits, recounts ranks */
	private void merge (int flips) {
		flipAt = capacity (flipAt, flips);
		int entering = 0;
		for (int f = 0; f < flips; f++) {
			flipAt [f] = positionBefore (flipRows [f] < 0 ? ~flipRows [f] : flipRows [f], sourceSize ());
			if (flipRows [f] >= 0) entering++;
		}
		int first = flipAt [0], tail = size - first, out = first, read = first;
		spare = capacity (spare, tail);
		System.arraycopy (rows, first, spare, 0, tail);
		rows = capacity (rows, size + entering);
		for (int f = 0; f < flips; f++) {
			int at = flipAt [f], row = flipRows [f];
			System.arraycopy (spare, read - first, rows, out, at - read);
			out += at - read;
			read = at;
			if (row < 0) {
				read++;
				M3RowMask.set (kept, ~row, false);
			} else {
				rows [out++] = row;
				M3RowMask.set (kept, row, true);
			}
		}
		System.arraycopy (spare, read - first, rows, out, size - read);
		size = out + size - read;
		rerank ((flipRows [0] < 0 ? ~flipRows [0] : flipRows [0]) >>> 6, sourceSize ());
	}

	/* recounts the word ranks after word fromWord (the ranks up to it are unchanged) */
	private void rerank (int fromWord, int n) {
		int words = M3RowMask.words (n);
		if (rank.length < words) rank = Arrays.copyOf (rank, Math.max (words, rank.length + (rank.length >> 1)));
		for (int w = fromWord + 1; w < words; w++) rank [w] = rank [w - 1] + Long.bitCount (kept [w - 1]);
	}

	private static int [] capacity (int [] lane, int length) {
		return lane.length >= length ? lane : Arrays.copyOf (lane, Math.max (length, lane.length + (lane.length >> 1)));
	}

	private void evaluate (int start, int end) {
		for (M3FilterCriterion criterion : criteria) {
			if (criterion.isActive ()) criterion.evaluate (start, end, text);
		}
	}

	private void shift (int start, int count, boolean insert) {
		int n = sourceSize (), old = insert ? n - count : n + count;
		for (M3FilterCriterion criterion : criteria) criterion.mask = shift (criterion.mask, old, n, start, count, insert);
		previous = shift (previous, old, n, start, count, insert);
	}

	private static long [] shift (long [] mask, int old, int n, int start, int count, boolean insert) {
		mask = M3RowMask.ensure (mask, Math.max (old, n));
		if (insert) M3RowMask.insert (mask, old, start, count); else M3RowMask.remove (mask, old, start, count);
		return mask;
	}

	/* folds the criteria, applies the ancestor rule, compacts the view, and reports the change */
	private void recompute (M3RowChange in) {
		int n = sourceSize (), words = M3RowMask.words (n);
		matched = M3RowMask.ensure (matched, n);
		M3FilterCriterion.fold (criteria, matched, n);
		kept = M3RowMask.ensure (kept, n);
		System.arraycopy (matched, 0, kept, 0, words);
		if (parentOf != null) {
			M3FilterCriterion.ancestors (matched, kept, n, parentOf);
			below = M3FilterCriterion.below (matched, n, parentOf, below);
		}
		rows = rows.length >= n ? rows : new int [n];
		rank = rank.length >= words ? rank : new int [words];
		size = M3RowMask.compact (kept, words, rows, rank);
		previous = M3RowMask.ensure (previous, n);
		delta.report (in, previous, kept, n);
		System.arraycopy (kept, 0, previous, 0, words);
	}
	/**
	 * Makes criterion changes asynchronous, or synchronous again with a <code>null</code> display.
	 * <p>
	 * When asynchronous, a criterion change (<code>setText</code>, <code>setValues</code>,
	 * <code>clear</code>, <code>clearAll</code>, <code>setLogic</code>, <code>setParents</code>) is
	 * recorded at once and increments the generation; after <code>debounceMs</code> without a newer
	 * change, a worker thread evaluates the changed criteria over the source rows and computes the
	 * view (and the order of an {@link M3ColumnSort} over this filter), dropping the work as soon as
	 * a newer generation exists; the result of the newest generation is applied on the display's
	 * thread in one step and reported as one {@link M3RowChange#RESET}. Until then the view shows
	 * the previous result, and {@link #isActive(int)} and {@link #getMatch(int)} already answer the
	 * recorded criteria. The cell text, the parents and the sort keys are then read on the worker
	 * thread: they must be safe to read there while the source is not being edited. Source edits
	 * stay synchronous and supersede a running computation.
	 * </p>
	 *
	 * @param display the display whose thread applies the results, or <code>null</code>
	 * @param debounceMs the quiet time before a change is computed, in milliseconds, not negative
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the delay is negative</li>
	 * </ul>
	 */
	public void setAsync (Display display, int debounceMs) {
		M3Checks.argument (debounceMs >= 0);
		if (display == null) {
			if (worker != null) flush ();
			return;
		}
		if (worker == null) {
			worker = new M3FilterWorker (this, display);
			worker.hold (held ());
			pending = criteria.clone ();
		}
		worker.debounce = debounceMs;
	}

	/**
	 * Returns whether criterion changes are asynchronous.
	 *
	 * @return whether the filter is asynchronous
	 */
	public boolean isAsync () {
		return worker != null;
	}

	/* while every bound viewport is hidden, worker results are kept instead of posted; on show the newest applies once */
	@Override
	void viewportsChanged () {
		if (worker != null) worker.hold (held ());
	}

	/**
	 * Returns the generation: the number of criterion changes and source edits so far.
	 *
	 * @return the generation
	 */
	public int getGeneration () {
		return generation;
	}

	/**
	 * Returns the generation whose result the view shows when asynchronous (the last applied one).
	 *
	 * @return the applied generation
	 */
	public int getAppliedGeneration () {
		return worker == null ? generation : appliedGeneration;
	}

	/* back to synchronous: the recorded criteria are evaluated now */
	private void flush () {
		worker.stop ();
		worker = null;
		int n = sourceSize ();
		for (int i = 0; i < criteria.length; i++) {
			if (dirty [i]) {
				pending [i].mask = new long [M3RowMask.words (n)];
				if (pending [i].isActive ()) pending [i].evaluate (0, n, text);
			} else {
				pending [i].mask = criteria [i].mask;
			}
			criteria [i] = pending [i];
		}
		pending = criteria;
		Arrays.fill (dirty, false);
		changed (null);
	}

	private boolean anyDirty () {
		for (boolean changed : dirty) {
			if (changed) return true;
		}
		return false;
	}

	M3FilterCriterion [] pendingCriteria () {
		return pending;
	}

	M3FilterCriterion [] appliedCriteria () {
		return criteria;
	}

	boolean [] dirtyCriteria () {
		return dirty;
	}

	IntUnaryOperator parents () {
		return parentOf;
	}

	M3CellText cellText () {
		return text;
	}

	/* UI thread: takes a worker result (its generation is current) and reports one RESET */
	void apply (M3FilterCriterion [] next, long [] matched, long [] kept, int [] below, int [] rows, int [] rank, int size, int generation) {
		applies++;
		System.arraycopy (next, 0, criteria, 0, criteria.length);
		Arrays.fill (dirty, false);
		this.matched = matched;
		if (below != null) this.below = below;
		this.kept = kept;
		this.rows = rows;
		this.rank = rank;
		this.size = size;
		int n = sourceSize (), words = M3RowMask.words (n), changed = words;
		previous = M3RowMask.ensure (previous, n);
		for (int w = 0; w < words && changed == words; w++) {
			if (previous [w] != kept [w]) changed = w;
		}
		int first = changed == words ? size : rankBefore (changed << 6 | Long.numberOfTrailingZeros (previous [changed] ^ kept [changed]));
		System.arraycopy (kept, 0, previous, 0, words);
		appliedGeneration = generation;
		if (follower != null) follower.commitPrepared (generation);
		change.set (M3RowChange.RESET, 0, 0);
		change.damage (first, size - 1);
		fire ();
	}

	/* the number of rows of the view before a source row */
	int rankBefore (int sourceRow) {
		if (sourceRow >= sourceSize ()) return size;
		int w = sourceRow >>> 6;
		return rank [w] + Long.bitCount (kept [w] & (1L << sourceRow) - 1);
	}

	@Override
	public String toString () {
		return "M3ColumnFilter [" + size + " of " + sourceSize () + " rows]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
