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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 826e57b779174368fec5c4295eca9e3ad794c2e1,
 * com.m3.collections.M3ListDiff (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;

/**
 * Diff between two keyed snapshots (diffable-snapshot pattern): the keys present in both, in an order that keeps the
 * longest increasing run of their new positions, stay put; every other old key is removed, every other new key is
 * inserted, and a key that is removed and inserted is reported as a move. The result is an {@link M3Patch} of int
 * runs.
 * <p>Keys must be unique within each snapshot. Cost: O((n + m) log(n + m)) time, O(n + m) int lanes plus two
 * open-addressed key maps ({@link M3LongLongHashMap}); no entry objects.</p>
 */
public final class M3ListDiff {
	private M3ListDiff () {
	}

	/** Diffs {@code before} into {@code after}; throws {@link IllegalArgumentException} on a duplicate key. */
	public static M3Patch diff (long [] before, long [] after) {
		M3LongLongHashMap newIndex = index (after, "after");
		index (before, "before");
		int n = before.length;
		/* positions in the new snapshot of the old keys that survive, in old order */
		int [] survivorOld = new int [n], survivorNew = new int [n];
		int survivors = 0;
		for (int i = 0; i < n; i++) {
			int j = (int) newIndex.getOrDefault (before [i], -1L);
			if (j >= 0) {
				survivorOld [survivors] = i;
				survivorNew [survivors++] = j;
			}
		}
		boolean [] keptOld = new boolean [n], keptNew = new boolean [after.length];
		int kept = 0;
		for (int s : longestIncreasing (survivorNew, survivors)) {
			keptOld [survivorOld [s]] = true;
			keptNew [survivorNew [s]] = true;
			kept++;
		}
		int moves = survivors - kept;
		int [] moveFrom = new int [moves], moveTo = new int [moves];
		for (int s = 0, m = 0; s < survivors; s++) {
			if (!keptOld [survivorOld [s]]) {
				moveFrom [m] = survivorOld [s];
				moveTo [m++] = survivorNew [s];
			}
		}
		int [][] removes = runs (keptOld, true);
		int [][] inserts = runs (keptNew, false);
		return new M3Patch (removes [0], removes [1], inserts [0], inserts [1], moveFrom, moveTo, kept);
	}

	private static M3LongLongHashMap index (long [] keys, String side) {
		M3LongLongHashMap map = new M3LongLongHashMap (keys.length);
		for (int i = 0; i < keys.length; i++) {
			if (map.containsKey (keys [i])) throw new IllegalArgumentException ("duplicate key in " + side + ": " + keys [i]);
			map.put (keys [i], i);
		}
		return map;
	}

	/** Runs of {@code false} entries as (start, length) lanes; descending start order when {@code descending}. */
	static int [][] runs (boolean [] kept, boolean descending) {
		int [] starts = new int [kept.length], lengths = new int [kept.length];
		int count = 0;
		for (int i = 0; i < kept.length; i++) {
			if (kept [i]) continue;
			int start = i;
			while (i + 1 < kept.length && !kept [i + 1]) i++;
			starts [count] = start;
			lengths [count++] = i - start + 1;
		}
		starts = Arrays.copyOf (starts, count);
		lengths = Arrays.copyOf (lengths, count);
		if (descending) {
			reverse (starts);
			reverse (lengths);
		}
		return new int [][] {starts, lengths};
	}

	private static void reverse (int [] a) {
		for (int i = 0, j = a.length - 1; i < j; i++, j--) {
			int t = a [i];
			a [i] = a [j];
			a [j] = t;
		}
	}

	/**
	 * Indices (into {@code values [0 .. count)}) of one longest strictly increasing subsequence, in ascending order:
	 * patience sorting with predecessor links, O(count log count).
	 */
	static int [] longestIncreasing (int [] values, int count) {
		int [] tails = new int [count], previous = new int [count];
		int length = 0;
		for (int i = 0; i < count; i++) {
			int low = 0, high = length;
			while (low < high) {
				int middle = (low + high) >>> 1;
				if (values [tails [middle]] < values [i]) low = middle + 1;
				else high = middle;
			}
			previous [i] = low > 0 ? tails [low - 1] : -1;
			tails [low] = i;
			if (low == length) length++;
		}
		int [] result = new int [length];
		for (int k = length - 1, i = length > 0 ? tails [length - 1] : -1; k >= 0; k--, i = previous [i]) result [k] = i;
		return result;
	}
}
