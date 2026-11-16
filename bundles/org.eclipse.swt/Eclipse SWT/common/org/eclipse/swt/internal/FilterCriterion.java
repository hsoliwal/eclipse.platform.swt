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
 *     Hitesh Soliwal (Synexia) - adapted from TV_5.0 treeviewer RegexFilter, WildCardFilter,
 *         MultiPropertyTextFilter and ElementFilter (docs/m3/sort-filter/PROVENANCE.tsv)
 *******************************************************************************/
package org.eclipse.swt.internal;

import java.util.*;
import java.util.function.*;

/*
 * One criterion of ColumnFilter: a text match over one or more columns (a row matches when any
 * of the columns matches; NEGATE inverts the whole), a text value list, or an id value list, plus
 * its logic with the criteria before it and its mask lane (bit = source row passes).
 */
final class FilterCriterion {

	int logic = ColumnFilter.AND;
	TextMatch match;
	int [] columns = {};
	Set<String> values;
	IntUnaryOperator valueId;
	long [] allowed;
	long [] mask = {};

	/*
	 * short circuit: null when the mask holds the result for every row; otherwise bit = the row was tested and its
	 * mask bit is its result. A row that was not tested has a clear mask bit and is tested when a fold first needs it.
	 */
	long [] known;

	/* a suspended criterion keeps its specification and its mask and is left out of the fold until it is resumed */
	boolean suspended;

	boolean isActive () {
		return match != null || values != null || valueId != null;
	}

	/* whether the criterion takes part in the filter: it has a specification and is not suspended */
	boolean applies () {
		return !suspended && isActive ();
	}

	void clear () {
		match = null;
		values = null;
		valueId = null;
		allowed = null;
		columns = new int [0];
		suspended = false;
	}

	boolean test (int row, CellText text) {
		if (match != null) {
			boolean any = false;
			for (int i = 0; i < columns.length && !any; i++) any = match.any (text.text (row, columns [i]));
			return any != match.negated ();
		}
		if (values != null) {
			String value = text.text (row, columns [0]);
			return values.contains (value == null ? "" : value); //$NON-NLS-1$
		}
		int id = valueId.applyAsInt (row);
		return id >= 0 && id < allowed.length << 6 && RowMask.get (allowed, id);
	}

	/* evaluates the rows [start, end) into the mask */
	void evaluate (int start, int end, CellText text) {
		for (int row = start; row < end; row++) RowMask.set (mask, row, test (row, text));
		if (known != null) {
			for (int row = start; row < end; row++) RowMask.set (known, row, true);
		}
	}

	/* forgets every result: a row is tested when a fold first needs it */
	void defer (int n) {
		int words = RowMask.words (n);
		if (mask.length >= words) Arrays.fill (mask, 0L); else mask = new long [words];
		known = new long [mask.length];
	}

	/* tests the rows of word w that are in need and were not tested yet */
	void ensure (int w, long need, CellText text) {
		long todo = need & ~known [w];
		if (todo == 0) return;
		long passing = 0;
		for (long x = todo; x != 0; x &= x - 1) {
			int bit = Long.numberOfTrailingZeros (x);
			if (test (w << 6 | bit, text)) passing |= 1L << bit;
		}
		mask [w] |= passing;
		known [w] |= todo;
	}

	/* tests every row that was not tested yet: the mask then holds the result for every row */
	void complete (int n, CellText text) {
		if (known == null) return;
		if (isActive ()) {
			for (int w = 0, words = RowMask.words (n); w < words; w++) ensure (w, valid (w, n), text);
		}
		known = null;
	}

	/* the bits of word w that are rows of a source of n rows */
	static long valid (int w, int n) {
		return w == RowMask.words (n) - 1 && (n & 63) != 0 ? (1L << n) - 1 : -1L;
	}

	/* whether this criterion keeps a subset of the rows previous keeps (same columns, a narrowing text match) */
	boolean narrows (FilterCriterion previous) {
		return match != null && previous.match != null && Arrays.equals (columns, previous.columns) && match.narrows (previous.match);
	}

	/* clears, in the mask, the bits of rows [start, end) (by 64-row word) that this criterion rejects; set bits are the candidates */
	void narrow (int startWord, int endWord, CellText text) {
		for (int w = startWord; w < endWord; w++) {
			for (long x = mask [w]; x != 0; x &= x - 1) {
				int row = w << 6 | Long.numberOfTrailingZeros (x);
				if (!test (row, text)) mask [w] &= ~(1L << row);
			}
		}
	}

	/* the same criterion with a new identity (spec and mask shared): a pending edit of an asynchronous filter */
	FilterCriterion copy () {
		FilterCriterion copy = new FilterCriterion ();
		copy.logic = logic;
		copy.match = match;
		copy.columns = columns;
		copy.values = values;
		copy.valueId = valueId;
		copy.allowed = allowed;
		copy.mask = mask;
		copy.known = known;
		copy.suspended = suspended;
		return copy;
	}

	/* folds the active criteria from left to right (AND/OR) into result; every row passes when none is active */
	static void fold (FilterCriterion [] criteria, long [] result, int n) {
		int words = RowMask.words (n);
		boolean first = true;
		for (FilterCriterion criterion : criteria) {
			if (!criterion.applies ()) continue;
			long [] mask = criterion.mask;
			for (int w = 0; w < words; w++) {
				result [w] = first ? mask [w] : criterion.logic == ColumnFilter.AND ? result [w] & mask [w] : result [w] | mask [w];
			}
			first = false;
		}
		if (first) RowMask.fill (result, n);
		else if ((n & 63) != 0) result [words - 1] &= (1L << n) - 1;
	}

	/*
	 * The same fold with a short circuit: a criterion is tested only on the rows whose result still depends on it.
	 * After a false operand the AND criteria that follow are not tested for that row, after a true operand the OR
	 * criteria are not. Criteria joined by the same logic commute, so within such a run the criteria that hold a
	 * result for every row go first and decide rows for the others without a test. The result is the fold above;
	 * only the cells that are read differ.
	 */
	static void fold (FilterCriterion [] criteria, long [] result, int n, CellText text) {
		FilterCriterion [] order = new FilterCriterion [criteria.length];
		int count = 0;
		for (FilterCriterion criterion : criteria) {
			if (criterion.applies ()) order [count++] = criterion;
		}
		if (count == 0) {
			RowMask.fill (result, n);
			return;
		}
		/* the logic that joins each criterion to the fold before it; the first one joins the run that follows it */
		boolean [] and = new boolean [count], full = new boolean [count];
		for (int i = 1; i < count; i++) and [i] = order [i].logic == ColumnFilter.AND;
		and [0] = count == 1 || and [1];
		for (int from = 0, to; from < count; from = to) {
			for (to = from + 1; to < count && and [to] == and [from]; to++) { /* a run of one logic */ }
			for (int at = from, i = from; i < to; i++) {
				if (order [i].known != null) continue;
				FilterCriterion settled = order [i];
				System.arraycopy (order, at, order, at + 1, i - at);
				order [at++] = settled;
			}
		}
		Arrays.fill (full, true);
		for (int w = 0, words = RowMask.words (n); w < words; w++) {
			long valid = valid (w, n), fold = and [0] ? valid : 0;
			for (int i = 0; i < count; i++) {
				FilterCriterion criterion = order [i];
				if (criterion.known != null) {
					criterion.ensure (w, (and [i] ? fold : ~fold) & valid, text);
					full [i] &= (criterion.known [w] & valid) == valid;
				}
				fold = and [i] ? fold & criterion.mask [w] : fold | criterion.mask [w];
			}
			result [w] = fold & valid;
		}
		for (int i = 0; i < count; i++) {
			if (full [i]) order [i].known = null;
		}
	}

	/* the fold for one row (its criteria were tested for that row) */
	static boolean passes (FilterCriterion [] criteria, int row) {
		boolean result = true, first = true;
		for (FilterCriterion criterion : criteria) {
			if (!criterion.applies ()) continue;
			boolean bit = RowMask.get (criterion.mask, row);
			result = first ? bit : criterion.logic == ColumnFilter.AND ? result && bit : result || bit;
			first = false;
		}
		return result;
	}

	/* keeps every ancestor of a matching row: walk up until an ancestor is already kept (each row once) */
	static void ancestors (long [] matched, long [] kept, int n, IntUnaryOperator parentOf) {
		for (int w = 0, words = RowMask.words (n); w < words; w++) {
			for (long x = matched [w]; x != 0; x &= x - 1) {
				int parent = parentOf.applyAsInt (w << 6 | Long.numberOfTrailingZeros (x));
				while (parent != -1) {
					RowChecks.argument (parent >= 0 && parent < n);
					if (RowMask.get (kept, parent)) break;
					RowMask.set (kept, parent, true);
					parent = parentOf.applyAsInt (parent);
				}
			}
		}
	}

	/*
	 * tree mode: the number of matching strict descendants of every row, so that a match flip updates its ancestors in
	 * O(depth); parents receives the parent lane (a value update may change a parent: the old one is needed then)
	 */
	static int [] below (long [] matched, int n, IntUnaryOperator parentOf, int [] lane, int [] parents) {
		if (lane.length < n) lane = new int [n]; else Arrays.fill (lane, 0, n, 0);
		for (int row = 0; row < n; row++) parents [row] = parentOf.applyAsInt (row);
		for (int w = 0, words = RowMask.words (n); w < words; w++) {
			for (long x = matched [w]; x != 0; x &= x - 1) {
				int steps = 0;
				for (int p = parents [w << 6 | Long.numberOfTrailingZeros (x)]; p != -1 && steps++ < n; p = parents [p]) lane [p]++;
			}
		}
		return lane;
	}
}
