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
package org.eclipse.swt.m3;

import java.util.*;
import java.util.function.*;

/*
 * One criterion of M3ColumnFilter: a text match over one or more columns (a row matches when any
 * of the columns matches; NEGATE inverts the whole), a text value list, or an id value list, plus
 * its logic with the criteria before it and its mask lane (bit = source row passes).
 */
final class M3FilterCriterion {

	int logic = M3ColumnFilter.AND;
	M3TextMatch match;
	int [] columns = {};
	Set<String> values;
	IntUnaryOperator valueId;
	long [] allowed;
	long [] mask = {};

	boolean isActive () {
		return match != null || values != null || valueId != null;
	}

	void clear () {
		match = null;
		values = null;
		valueId = null;
		allowed = null;
		columns = new int [0];
	}

	boolean test (int row, M3CellText text) {
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
		return id >= 0 && id < allowed.length << 6 && M3RowMask.get (allowed, id);
	}

	/* evaluates the rows [start, end) into the mask */
	void evaluate (int start, int end, M3CellText text) {
		for (int row = start; row < end; row++) M3RowMask.set (mask, row, test (row, text));
	}

	/* whether this criterion keeps a subset of the rows previous keeps (same columns, a narrowing text match) */
	boolean narrows (M3FilterCriterion previous) {
		return match != null && previous.match != null && Arrays.equals (columns, previous.columns) && match.narrows (previous.match);
	}

	/* clears, in the mask, the bits of rows [start, end) (by 64-row word) that this criterion rejects; set bits are the candidates */
	void narrow (int startWord, int endWord, M3CellText text) {
		for (int w = startWord; w < endWord; w++) {
			for (long x = mask [w]; x != 0; x &= x - 1) {
				int row = w << 6 | Long.numberOfTrailingZeros (x);
				if (!test (row, text)) mask [w] &= ~(1L << row);
			}
		}
	}

	/* the same criterion with a new identity (spec and mask shared): a pending edit of an asynchronous filter */
	M3FilterCriterion copy () {
		M3FilterCriterion copy = new M3FilterCriterion ();
		copy.logic = logic;
		copy.match = match;
		copy.columns = columns;
		copy.values = values;
		copy.valueId = valueId;
		copy.allowed = allowed;
		copy.mask = mask;
		return copy;
	}

	/* folds the active criteria from left to right (AND/OR) into result; every row passes when none is active */
	static void fold (M3FilterCriterion [] criteria, long [] result, int n) {
		int words = M3RowMask.words (n);
		boolean first = true;
		for (M3FilterCriterion criterion : criteria) {
			if (!criterion.isActive ()) continue;
			long [] mask = criterion.mask;
			for (int w = 0; w < words; w++) {
				result [w] = first ? mask [w] : criterion.logic == M3ColumnFilter.AND ? result [w] & mask [w] : result [w] | mask [w];
			}
			first = false;
		}
		if (first) M3RowMask.fill (result, n);
		else if ((n & 63) != 0) result [words - 1] &= (1L << n) - 1;
	}

	/* the fold for one row */
	static boolean passes (M3FilterCriterion [] criteria, int row) {
		boolean result = true, first = true;
		for (M3FilterCriterion criterion : criteria) {
			if (!criterion.isActive ()) continue;
			boolean bit = M3RowMask.get (criterion.mask, row);
			result = first ? bit : criterion.logic == M3ColumnFilter.AND ? result && bit : result || bit;
			first = false;
		}
		return result;
	}

	/* keeps every ancestor of a matching row: walk up until an ancestor is already kept (each row once) */
	static void ancestors (long [] matched, long [] kept, int n, IntUnaryOperator parentOf) {
		for (int w = 0, words = M3RowMask.words (n); w < words; w++) {
			for (long x = matched [w]; x != 0; x &= x - 1) {
				int parent = parentOf.applyAsInt (w << 6 | Long.numberOfTrailingZeros (x));
				while (parent != -1) {
					M3Checks.argument (parent >= 0 && parent < n);
					if (M3RowMask.get (kept, parent)) break;
					M3RowMask.set (kept, parent, true);
					parent = parentOf.applyAsInt (parent);
				}
			}
		}
	}
}
