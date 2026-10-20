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
 *     Hitesh Soliwal (Synexia) - per-parent sorted children adapted from TV_5.0 treeviewer
 *         SortedElementProvider and Comparator (docs/m3/sort-filter/PROVENANCE.tsv)
 *******************************************************************************/
package org.eclipse.swt.m3;

import java.util.*;
import java.util.function.*;

/*
 * The lanes of M3ColumnSort: the order lane (view row -> source row), its inverse (source row ->
 * view row or -1), and the sibling index of tree mode (CSR: child start per parent, children in
 * sorted order, sibling position per source row). Changes are applied incrementally: a moved row
 * is found by binary search and shifted with one arraycopy; an entering batch is sorted and
 * merged from the back; leaving rows are compacted in one pass. Each operation returns the
 * first view row whose content changed.
 */
final class M3SortOrder {

	private static final int REMOVED = -2;

	final M3SortLanes lanes = new M3SortLanes ();
	private final M3ColumnSort sort;
	int [] order = new int [0];
	int size;
	int [] inverse = new int [0];
	int sourceSize;
	private int [] batch = new int [0], places = new int [0], olds = new int [0];
	private int [] childStart = new int [0], children = new int [0], siblingIndex = new int [0], cursor = new int [0];
	private boolean treeValid;
	/* full rebuilds of the sibling index (read by the tests: value updates keep it) */
	int treeBuilds;

	M3SortOrder (M3ColumnSort sort) {
		this.sort = sort;
	}

	/* how rows are sorted: the sort's own chain, or a chain captured by a worker */
	@FunctionalInterface
	interface Sorter {
		void sort (M3SortLanes lanes, int [] rows, int n);
	}

	/* replaces the rows by rows [0, n) of a source of sourceSize rows and sorts them */
	void reset (int [] rows, int n, int sourceSize) {
		reset (rows, n, sourceSize, sort::sortRows);
	}

	void reset (int [] rows, int n, int sourceSize, Sorter sorter) {
		this.sourceSize = sourceSize;
		order = ensure (order, n, 0);
		System.arraycopy (rows, 0, order, 0, n);
		size = n;
		sorter.sort (lanes, order, size);
		index ();
	}

	/*
	 * sorts the current rows again (the sort keys changed); in a dense view the rows are first laid out in source
	 * order from the inverse lane (one sequential pass instead of the radix source-order pass), so that the keys are
	 * also read from the model lanes in order
	 */
	void resort () {
		if (size << 3 >= sourceSize) {
			for (int s = 0, k = 0; k < size; s++) {
				if (inverse [s] >= 0) order [k++] = s;
			}
		}
		sort.sortRows (lanes, order, size);
		index ();
	}

	private void index () {
		if (inverse.length < sourceSize) inverse = new int [sourceSize];
		Arrays.fill (inverse, 0, sourceSize, -1);
		reindex (0);
	}

	/* source rows from start move up by count; the new rows are not in the view yet */
	void insertSourceRange (int start, int count) {
		inverse = ensure (inverse, sourceSize + count, sourceSize);
		System.arraycopy (inverse, start, inverse, start + count, sourceSize - start);
		Arrays.fill (inverse, start, start + count, -1);
		sourceSize += count;
		for (int i = 0; i < size; i++) {
			if (order [i] >= start) order [i] += count;
		}
		treeValid = false;
	}

	/* removes source rows start .. start + count - 1; returns the first damaged view row */
	int removeSourceRange (int start, int count) {
		int first = size;
		for (int s = start; s < start + count; s++) {
			if (inverse [s] >= 0) {
				first = Math.min (first, inverse [s]);
				inverse [s] = REMOVED;
			}
		}
		compact (first);
		for (int i = 0; i < size; i++) {
			if (order [i] >= start + count) order [i] -= count;
		}
		System.arraycopy (inverse, start + count, inverse, start, sourceSize - start - count);
		sourceSize -= count;
		return first;
	}

	/* adds rows [0, m) (not in the view); returns the first damaged view row */
	int insert (int [] rows, int m) {
		if (m == 0) return size;
		boolean tree = treeValid && sort.parents () != null;
		batch = ensure (batch, m, 0);
		System.arraycopy (rows, 0, batch, 0, m);
		sort.sortRows (lanes, batch, m);
		order = ensure (order, size + m, size);
		int first = m > (size >> 3) ? mergeLinear (m) : mergeSearch (m);
		size += m;
		reindex (first);
		if (tree) treeValid = enterSiblings (m, sort.parents ());
		return first;
	}

	/* merges the sorted batch from the back, one comparison per step (large batches) */
	private int mergeLinear (int m) {
		int i = size - 1, out = size + m - 1;
		for (int j = m - 1; j >= 0;) {
			order [out--] = i >= 0 && sort.compare (order [i], batch [j]) > 0 ? order [i--] : batch [j--];
		}
		return i + 1;
	}

	/* merges the sorted batch from the back, finding each place by binary search and moving runs (small batches) */
	private int mergeSearch (int m) {
		int i = size - 1, out = size + m - 1, first = size;
		for (int j = m - 1; j >= 0; j--) {
			int row = batch [j], p = upper (row, 0, i + 1);
			int moved = i + 1 - p;
			System.arraycopy (order, p, order, out - moved + 1, moved);
			out -= moved;
			i = p - 1;
			order [out--] = row;
			first = p;
		}
		return first;
	}

	/* removes rows [0, m) that are in the view; returns the first damaged view row */
	int remove (int [] rows, int m) {
		boolean tree = treeValid && sort.parents () != null && leaveSiblings (rows, m, sort.parents ());
		int first = size;
		for (int k = 0; k < m; k++) {
			int position = inverse [rows [k]];
			if (position >= 0) {
				first = Math.min (first, position);
				inverse [rows [k]] = REMOVED;
			}
		}
		compact (first);
		if (tree) treeValid = true;
		return first;
	}

	/* rows [0, m) of the view changed their values; returns {first, last} damaged view rows */
	void update (int [] rows, int m, M3RowChange change) {
		boolean tree = treeValid && sort.parents () != null;
		if (m == 1) move (rows [0], change); else reposition (rows, m, change);
		if (tree) treeValid = siblings (rows, m, sort.parents ());
	}

	/*
	 * the sibling index follows rows whose values changed inside their parents' segments instead of being rebuilt
	 * (brief rule 24): a single row by binary search and one shift, several rows of one segment by one merge. Returns
	 * false (rebuild on demand) when a row changed its parent, which is a structural edit.
	 */
	private boolean siblings (int [] rows, int m, IntUnaryOperator parentOf) {
		long [] keyed = new long [m];
		int count = 0;
		for (int k = 0; k < m; k++) {
			int row = rows [k];
			if (inverse [row] < 0) continue;
			int parent = parentOf.applyAsInt (row);
			if (parent < -1 || parent >= sourceSize) return false;
			int start = childStart [parent + 1], index = siblingIndex [row];
			if (index >= childStart [parent + 2] - start || children [start + index] != row) return false;
			keyed [count++] = (long) (parent + 1) << 32 | row;
		}
		Arrays.sort (keyed, 0, count);
		for (int k = 0; k < count;) {
			int bucket = (int) (keyed [k] >>> 32), group = k;
			while (k < count && (int) (keyed [k] >>> 32) == bucket) k++;
			if (k - group == 1) moveSibling ((int) keyed [group], bucket); else mergeSiblings (keyed, group, k, bucket);
		}
		return true;
	}

	/* rows keyed [from, to) of one segment changed: they are taken out, sorted alone and merged back from the end */
	private void mergeSiblings (long [] keyed, int from, int to, int bucket) {
		int start = childStart [bucket], end = childStart [bucket + 1], k = to - from;
		for (int j = from; j < to; j++) children [start + siblingIndex [(int) keyed [j]]] = -1;
		int kept = start;
		for (int i = start; i < end; i++) {
			if (children [i] >= 0) children [kept++] = children [i];
		}
		batch = ensure (batch, k, 0);
		for (int j = 0; j < k; j++) batch [j] = (int) keyed [from + j];
		sort.sortRows (lanes, batch, k);
		for (int out = end - 1, i = kept - 1, j = k - 1; j >= 0; out--) {
			children [out] = i >= start && sort.compare (children [i], batch [j]) > 0 ? children [i--] : batch [j--];
		}
		for (int i = start; i < end; i++) siblingIndex [children [i]] = i - start;
	}

	/*
	 * rows batch [0, m) (sorted by the key chain) entered the view: each is placed in its parent's segment by binary
	 * search (rows of one segment in order), the children lane is merged once from the end, the segment starts after
	 * the first changed segment move up, and only the changed segments are renumbered (brief rule 24). Returns false
	 * (rebuild on demand) for a parent outside the source.
	 */
	private boolean enterSiblings (int m, IntUnaryOperator parentOf) {
		int old = size - m;
		long [] keyed = new long [m];
		for (int k = 0; k < m; k++) {
			int parent = parentOf.applyAsInt (batch [k]);
			if (parent < -1 || parent >= sourceSize) return false;
			keyed [k] = (long) (parent + 1) << 32 | k;
		}
		Arrays.sort (keyed);
		places = ensure (places, m, 0);
		olds = ensure (olds, m, 0);
		for (int j = 0, low = 0, bucket = -1; j < m; j++) {
			int b = (int) (keyed [j] >>> 32), row = batch [(int) keyed [j]], high = childStart [b + 1];
			if (b != bucket) {
				bucket = b;
				low = childStart [b];
			}
			while (low < high) {
				int middle = (low + high) >>> 1;
				if (sort.compare (children [middle], row) > 0) high = middle; else low = middle + 1;
			}
			places [j] = low;
			olds [j] = row;
		}
		children = ensure (children, size, old);
		for (int j = m - 1, out = size - 1, i = old - 1; j >= 0; j--) {
			while (i >= places [j]) children [out--] = children [i--];
			children [out--] = olds [j];
		}
		shiftStarts (keyed, m, 1);
		renumber (keyed, m);
		return true;
	}

	/*
	 * rows [0, m) leave the view (called before the order lane drops them): they are taken out of their parents'
	 * segments in one compaction from the first, the segment starts after it move down, and only the changed segments
	 * are renumbered. Returns false (rebuild on demand) when a row is not in the segment of its parent.
	 */
	private boolean leaveSiblings (int [] rows, int m, IntUnaryOperator parentOf) {
		long [] keyed = new long [m];
		int count = 0;
		for (int k = 0; k < m; k++) {
			int row = rows [k];
			if (inverse [row] < 0) continue;
			int parent = parentOf.applyAsInt (row);
			if (parent < -1 || parent >= sourceSize) return false;
			int start = childStart [parent + 1], index = siblingIndex [row];
			if (index >= childStart [parent + 2] - start || children [start + index] != row) return false;
			keyed [count++] = (long) (parent + 1) << 32 | start + index;
		}
		if (count == 0) return true;
		Arrays.sort (keyed, 0, count);
		int unique = 0;
		for (int j = 0; j < count; j++) {
			if (j == 0 || keyed [j] != keyed [j - 1]) keyed [unique++] = keyed [j];
		}
		for (int j = 0; j < unique; j++) children [(int) keyed [j]] = -1;
		for (int read = (int) keyed [0], write = read; read < size; read++) {
			if (children [read] >= 0) children [write++] = children [read];
		}
		shiftStarts (keyed, unique, -1);
		renumber (keyed, unique);
		return true;
	}

	/* the segment start of every bucket after the first changed one moves by sign per changed row in an earlier bucket */
	private void shiftStarts (long [] keyed, int count, int sign) {
		for (int b = (int) (keyed [0] >>> 32) + 1, j = 0; b <= sourceSize + 1; b++) {
			while (j < count && (int) (keyed [j] >>> 32) < b) j++;
			childStart [b] += sign * j;
		}
	}

	/* the sibling indexes of the changed buckets (keyed by bucket, ascending) */
	private void renumber (long [] keyed, int count) {
		for (int j = 0; j < count; j++) {
			int b = (int) (keyed [j] >>> 32);
			if (j > 0 && b == (int) (keyed [j - 1] >>> 32)) continue;
			for (int i = childStart [b], end = childStart [b + 1]; i < end; i++) siblingIndex [children [i]] = i - childStart [b];
		}
	}

	/* a row whose values changed moves inside its parent's sibling segment: binary search and one shift, O(log c + distance) */
	private void moveSibling (int row, int bucket) {
		int start = childStart [bucket], end = childStart [bucket + 1], old = start + siblingIndex [row], at = old;
		if (old > start && sort.compare (children [old - 1], row) > 0) {
			int low = start, high = old;
			while (low < high) {
				int middle = (low + high) >>> 1;
				if (sort.compare (children [middle], row) > 0) high = middle; else low = middle + 1;
			}
			at = low;
			System.arraycopy (children, at, children, at + 1, old - at);
		} else if (old < end - 1 && sort.compare (children [old + 1], row) < 0) {
			int low = old + 1, high = end;
			while (low < high) {
				int middle = (low + high) >>> 1;
				if (sort.compare (children [middle], row) > 0) high = middle; else low = middle + 1;
			}
			at = low - 1;
			System.arraycopy (children, old + 1, children, old, at - old);
		} else {
			return;
		}
		children [at] = row;
		for (int i = Math.min (old, at), last = Math.max (old, at); i <= last; i++) siblingIndex [children [i]] = i - start;
	}

	/*
	 * moves rows [0, m) of the view (their values changed) in one pass: they are marked, sorted, their new places
	 * found by binary search over the unmarked rows, and the span between the first and the last place that changes
	 * is rewritten once (removals and insertions are equal in number, so the rows after the span keep their place)
	 */
	private void reposition (int [] rows, int m, M3RowChange change) {
		batch = ensure (batch, m, 0);
		places = ensure (places, m, 0);
		olds = ensure (olds, m, 0);
		int count = 0, first = size, last = -1;
		for (int k = 0; k < m; k++) {
			int position = inverse [rows [k]];
			if (position < 0) continue;
			first = Math.min (first, position);
			last = Math.max (last, position);
			inverse [rows [k]] = REMOVED;
			olds [count] = position;
			batch [count++] = rows [k];
		}
		if (count == 0) return;
		sort.sortRows (lanes, batch, count);
		for (int j = 0, low = 0; j < count; j++) {
			low = places [j] = unmarkedUpper (batch [j], low);
			first = Math.min (first, low);
			last = Math.max (last, Math.min (low, size - 1));
		}
		int span = last - first + 1;
		lanes.ensure (span);
		int [] old = lanes.rowTmp;
		System.arraycopy (order, first, old, 0, span);
		/* the old places of the moved rows are marked in the copy, so that the pass reads no inverse entry */
		for (int k = 0; k < count; k++) old [olds [k] - first] = -1;
		int out = first, j = 0;
		for (int i = first; i <= last; i++) {
			while (j < count && places [j] <= i) out = enter (j++, out);
			int row = old [i - first];
			if (row < 0) continue;
			if (out != i) {
				order [out] = row;
				inverse [row] = out;
			}
			out++;
		}
		while (j < count) out = enter (j++, out);
		for (j = 0; j < count; j++) inverse [batch [j]] = places [j];
		treeValid = false;
		change.damage (first, last);
	}

	/* writes batch row j at position (its inverse entry is set after the pass); returns the next position */
	private int enter (int j, int position) {
		order [position] = batch [j];
		places [j] = position;
		return position + 1;
	}

	/* the first position in [low, size) whose unmarked row sorts after row; marked rows are stepped over */
	private int unmarkedUpper (int row, int low) {
		int high = size;
		while (low < high) {
			int middle = (low + high) >>> 1, probe = middle;
			while (probe < high && inverse [order [probe]] == REMOVED) probe++;
			if (probe == high) {
				high = middle;
			} else if (sort.compare (order [probe], row) > 0) {
				high = middle;
			} else {
				low = probe + 1;
			}
		}
		return low;
	}

	private void move (int row, M3RowChange change) {
		int old = inverse [row];
		if (old < 0) return;
		if (old > 0 && sort.compare (order [old - 1], row) > 0) {
			int p = upper (row, 0, old);
			System.arraycopy (order, p, order, p + 1, old - p);
			order [p] = row;
			reindex (p, old + 1);
			change.damage (p, old);
		} else if (old < size - 1 && sort.compare (order [old + 1], row) < 0) {
			int q = upper (row, old + 1, size) - 1;
			System.arraycopy (order, old + 1, order, old, q - old);
			order [q] = row;
			reindex (old, q + 1);
			change.damage (old, q);
		} else {
			change.damage (old, old);
		}
		treeValid = false;
	}

	/* the first position in [low, high) whose row sorts after row (rows are distinct, the order is total) */
	private int upper (int row, int low, int high) {
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (sort.compare (order [middle], row) > 0) high = middle; else low = middle + 1;
		}
		return low;
	}


	private void compact (int first) {
		int write = first;
		for (int read = first; read < size; read++) {
			int row = order [read];
			if (inverse [row] == REMOVED) {
				inverse [row] = -1;
			} else {
				order [write] = row;
				inverse [row] = write++;
			}
		}
		size = write;
		treeValid = false;
	}

	private void reindex (int first) {
		reindex (first, size);
	}

	private void reindex (int first, int end) {
		for (int i = first; i < end; i++) inverse [order [i]] = i;
		treeValid = false;
	}

	/* the sibling index for tree mode, rebuilt on demand by one stable counting pass over the order */
	void buildTree (IntUnaryOperator parentOf) {
		if (treeValid) return;
		treeBuilds++;
		childStart = ensureExact (childStart, sourceSize + 2);
		Arrays.fill (childStart, 0, sourceSize + 2, 0);
		children = ensure (children, size, 0);
		siblingIndex = ensure (siblingIndex, sourceSize, 0);
		for (int i = 0; i < size; i++) {
			siblingIndex [order [i]] = bucket (parentOf, order [i]);
			childStart [siblingIndex [order [i]] + 1]++;
		}
		for (int b = 0; b <= sourceSize; b++) childStart [b + 1] += childStart [b];
		cursor = ensureExact (cursor, sourceSize + 1);
		System.arraycopy (childStart, 0, cursor, 0, sourceSize + 1);
		for (int i = 0; i < size; i++) {
			int row = order [i], b = siblingIndex [row];
			int position = cursor [b]++;
			children [position] = row;
			siblingIndex [row] = position - childStart [b];
		}
		treeValid = true;
	}

	void invalidateTree () {
		treeValid = false;
	}

	private int bucket (IntUnaryOperator parentOf, int row) {
		int parent = parentOf.applyAsInt (row);
		M3Checks.argument (parent >= -1 && parent < sourceSize);
		return parent + 1;
	}

	int childCount (int parent) {
		return childStart [parent + 2] - childStart [parent + 1];
	}

	int child (int parent, int index) {
		M3Checks.range (index, childCount (parent));
		return children [childStart [parent + 1] + index];
	}

	int indexOfChild (int row) {
		return inverse [row] < 0 ? -1 : siblingIndex [row];
	}

	private static int [] ensure (int [] lane, int capacity, int keep) {
		if (lane.length >= capacity) return lane;
		int [] grown = new int [Math.max (capacity, lane.length + (lane.length >> 1))];
		System.arraycopy (lane, 0, grown, 0, keep);
		return grown;
	}

	private static int [] ensureExact (int [] lane, int capacity) {
		return lane.length >= capacity ? lane : new int [capacity];
	}
}
