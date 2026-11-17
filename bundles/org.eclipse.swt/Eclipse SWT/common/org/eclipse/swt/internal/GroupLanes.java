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

/*
 * The backing store of ColumnGroups: int lanes and no widget. Rows are kept group after group;
 * group g holds the view rows starts [g] .. starts [g + 1] - 1, and inside a group the rows are in
 * ascending rank (the row's place in the input view). Each edit moves the rows after the edited
 * one by one place (an array copy) and returns the view row it touched, so the caller reports the
 * damage of that edit only.
 */
final class GroupLanes {

	private final Comparator<String> order;
	/* view row -> source row (size used), source row -> view row or -1 */
	int [] rows = new int [16], position = {};
	int size;
	/* the group values in order (groups used), and the first view row of each group, starts [groups] == size */
	String [] values = new String [4];
	int [] starts = new int [5];
	int groups;

	GroupLanes (Comparator<String> order) {
		this.order = order;
	}

	/* every row again: the rows of the input in input order, and the key of each */
	void rebuild (int [] source, String [] keys, int sourceSize) {
		int n = source.length;
		Integer [] byKey = new Integer [n];
		for (int i = 0; i < n; i++) byKey [i] = i;
		Arrays.sort (byKey, (a, b) -> order.compare (keys [a], keys [b]));
		rows = new int [Math.max (16, n)];
		position = new int [sourceSize];
		Arrays.fill (position, -1);
		values = new String [Math.max (4, n)];
		starts = new int [values.length + 1];
		groups = 0;
		for (int i = 0; i < n; i++) {
			int at = byKey [i];
			if (i == 0 || order.compare (keys [byKey [i - 1]], keys [at]) != 0) {
				values [groups] = keys [at];
				starts [groups++] = i;
			}
			rows [i] = source [at];
			position [source [at]] = i;
		}
		size = n;
		starts [groups] = n;
	}

	/* the group of a value: its index, or -(insertion point) - 1 */
	int find (String key) {
		int low = 0, high = groups - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1, compared = order.compare (values [mid], key);
			if (compared == 0) return mid;
			if (compared < 0) low = mid + 1;
			else high = mid - 1;
		}
		return -low - 1;
	}

	/* the group of a view row */
	int groupOf (int row) {
		int found = Arrays.binarySearch (starts, 0, groups, row);
		if (found < 0) return -found - 2;
		while (found + 1 < groups && starts [found + 1] == row) found++;
		return found;
	}

	/* places a source row into the group of its key, after the rows of lower rank; returns its view row */
	int insert (int sourceRow, String key, IntUnaryOperator rank) {
		int g = find (key);
		if (g < 0) g = openGroup (-g - 1, key);
		int low = starts [g], high = starts [g + 1], own = rank.applyAsInt (sourceRow);
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (rank.applyAsInt (rows [mid]) < own) low = mid + 1;
			else high = mid;
		}
		if (size == rows.length) rows = Arrays.copyOf (rows, size + (size >> 1) + 1);
		System.arraycopy (rows, low, rows, low + 1, size - low);
		rows [low] = sourceRow;
		size++;
		for (int row = low; row < size; row++) position [rows [row]] = row;
		for (int k = g + 1; k <= groups; k++) starts [k]++;
		return low;
	}

	/* takes a source row out of its group; returns the view row it had, or -1 */
	int remove (int sourceRow) {
		int row = position [sourceRow];
		if (row < 0) return -1;
		int g = groupOf (row);
		System.arraycopy (rows, row + 1, rows, row, size - row - 1);
		size--;
		position [sourceRow] = -1;
		for (int r = row; r < size; r++) position [rows [r]] = r;
		for (int k = g + 1; k <= groups; k++) starts [k]--;
		if (starts [g] == starts [g + 1]) closeGroup (g);
		return row;
	}

	private int openGroup (int g, String key) {
		if (groups == values.length) {
			values = Arrays.copyOf (values, groups * 2);
			starts = Arrays.copyOf (starts, values.length + 1);
		}
		System.arraycopy (values, g, values, g + 1, groups - g);
		System.arraycopy (starts, g, starts, g + 1, groups - g + 1);
		values [g] = key;
		groups++;
		return g;
	}

	private void closeGroup (int g) {
		System.arraycopy (values, g + 1, values, g, groups - g - 1);
		System.arraycopy (starts, g + 1, starts, g, groups - g);
		values [--groups] = null;
	}

	/* source rows start .. start + count - 1 were inserted: later source rows move up by count */
	void insertSourceRange (int start, int count) {
		for (int row = 0; row < size; row++) {
			if (rows [row] >= start) rows [row] += count;
		}
		int [] grown = new int [position.length + count];
		System.arraycopy (position, 0, grown, 0, start);
		Arrays.fill (grown, start, start + count, -1);
		System.arraycopy (position, start, grown, start + count, position.length - start);
		position = grown;
	}

	/* source rows start .. start + count - 1 were removed; returns the first view row that held one, or -1 */
	int removeSourceRange (int start, int count) {
		int first = -1, kept = 0, g = 0, end = start + count;
		for (int group = 0; group < groups; group++) {
			int from = starts [group];
			starts [group] = kept;
			for (int row = from; row < starts [group + 1]; row++) {
				int source = rows [row];
				if (source >= start && source < end) {
					if (first < 0) first = row;
					continue;
				}
				rows [kept++] = source >= end ? source - count : source;
			}
			if (kept > starts [group]) {
				values [g] = values [group];
				starts [g++] = starts [group];
			}
		}
		Arrays.fill (values, g, groups, null);
		groups = g;
		size = kept;
		starts [groups] = size;
		position = new int [position.length - count];
		Arrays.fill (position, -1);
		for (int row = 0; row < size; row++) position [rows [row]] = row;
		return first;
	}
}
