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

import java.util.*;
import java.util.function.*;

/*
 * The sorting algorithms of M3ColumnSort over int lanes, with their reusable scratch lanes
 * (ALGORITHM_CATALOGUE.tsv in docs/m3/sort-filter):
 * - multi-key order: every key becomes an unsigned radix key per row, normalized to key - min (or max - key
 *   when descending) so that it needs only the bits of its range; consecutive keys whose bits fit in 64 are
 *   packed into one key (least significant key in the low bits) and sorted together: LSD radix sort, least
 *   significant key first, one stable counting pass per byte, skipping bytes equal in every row;
 * - comparator keys (text, rows): dense ranks from one stable merge sort (coordinate compression);
 *   text keys with at most n/2 distinct values hash the texts first and sort only the distinct ones;
 * - small inputs: stable merge sort with the key chain comparator.
 */
final class M3SortLanes {

	/* below this many rows the comparator merge sort beats radix keys (MEASUREMENTS.md) */
	static final int MERGE_CUTOVER = 48;
	private static final int RUN = 32;

	int [] rowTmp = new int [0], idx = new int [0], idxTmp = new int [0];
	long [] keys = new long [0], keyTmp = new long [0], packed = new long [0];
	private long [] satelliteTmp = new long [0];
	String [] texts = new String [0];
	private static final int TABLE = 1024;
	private int [] slots = new int [0], ids = new int [0];
	private long [] values = new long [0];
	private final int [] counts = new int [8 << 8];

	void ensure (int n) {
		if (keys.length >= n) return;
		int capacity = Math.max (n, keys.length + (keys.length >> 1));
		rowTmp = new int [capacity];
		idx = new int [capacity];
		idxTmp = new int [capacity];
		keys = new long [capacity];
		keyTmp = new long [capacity];
		packed = new long [capacity];
	}

	String [] texts (int n) {
		if (texts.length < n) texts = new String [Math.max (n, texts.length + (texts.length >> 1))];
		return texts;
	}

	void releaseTexts (int n) {
		Arrays.fill (texts, 0, n, null);
	}

	/* orders rows [0, n) by the key chain; rows whose keys are all equal keep ascending source order */
	void sort (int [] rows, int n, M3ColumnKey [] chain, boolean [] descending, int keyCount, IntBinaryOperator compare) {
		if (n < 2) return;
		ensure (n);
		if (keyCount > 0 && n < MERGE_CUTOVER) {
			mergeSort (rows, n, rowTmp, compare);
			return;
		}
		sourceOrder (rows, n);
		int rowBits = 32 - Integer.numberOfLeadingZeros (rows [n - 1]), width = 0;
		for (int j = keyCount - 1; j >= 0; j--) {
			chain [j].radixKeys (rows, n, this);
			int w = normalize (n, descending [j]);
			if (w == 0) continue;
			if (width + w > 64 - rowBits && w > rowBits) {
				/* a wide key with few distinct values (doubles, longs) becomes dense ranks so that the chain packs */
				long max = compressKeys (n);
				if (max >= 0) w = 64 - Long.numberOfLeadingZeros (max);
			}
			if (width + w > 64) {
				/* the group is full: sort by it, carrying this key's lane along in the new row order */
				radixLanes (rows, n, width, true);
				width = 0;
			}
			for (int i = 0; i < n; i++) packed [i] = width == 0 ? keys [i] : keys [i] << width | packed [i];
			width += w;
		}
		if (width == 0) return;
		if (width + rowBits <= 64) radixPairs (rows, n, width, rowBits); else radixLanes (rows, n, width, false);
	}

	/*
	 * keys [0, n) -> key - min (ascending) or max - key (descending), unsigned: the order is kept (or reversed) and the
	 * keys fit in the returned number of bits, so that several keys pack into one 64-bit radix key
	 */
	private int normalize (int n, boolean down) {
		long min = keys [0], max = keys [0];
		for (int i = 1; i < n; i++) {
			long key = keys [i];
			if (Long.compareUnsigned (key, min) < 0) min = key;
			if (Long.compareUnsigned (key, max) > 0) max = key;
		}
		if (down) {
			for (int i = 0; i < n; i++) keys [i] = max - keys [i];
		} else {
			for (int i = 0; i < n; i++) keys [i] -= min;
		}
		return 64 - Long.numberOfLeadingZeros (max - min);
	}

	/*
	 * one stable LSD radix sort of (packed key << rowBits | row) pairs in a single long lane: one 8-byte scatter per
	 * row and pass instead of a row lane and a key lane; the digits are the key bits above rowBits
	 */
	private void radixPairs (int [] rows, int n, int width, int rowBits) {
		long [] k = packed, kt = keyTmp;
		for (int i = 0; i < n; i++) k [i] = k [i] << rowBits | rows [i];
		int bytes = (width + 7) >> 3;
		histogram (k, n, bytes, rowBits);
		for (int b = 0; b < bytes; b++) {
			int base = b << 8, shift = rowBits + (b << 3);
			if (counts [base | (int) (k [0] >>> shift) & 0xFF] == n) continue;
			prefix (base);
			for (int i = 0; i < n; i++) kt [counts [base | (int) (k [i] >>> shift) & 0xFF]++] = k [i];
			long [] swap = k; k = kt; kt = swap;
		}
		long mask = (1L << rowBits) - 1;
		for (int i = 0; i < n; i++) rows [i] = (int) (k [i] & mask);
	}

	/* stable LSD radix sort of rows by the packed lane (width bits); with carry, the key lane is permuted along */
	private void radixLanes (int [] rows, int n, int width, boolean carry) {
		long [] swap = keys;
		keys = packed;
		packed = swap;
		radixSort (rows, n, (width + 7) >> 3, carry ? packed : null);
		if (carry) {
			swap = keys;
			keys = packed;
			packed = swap;
		}
	}

	/* puts rows [0, n) in ascending order (they are distinct source rows) */
	void sourceOrder (int [] rows, int n) {
		int i = 1;
		while (i < n && rows [i - 1] < rows [i]) i++;
		if (i >= n) return;
		ensure (n);
		for (int k = 0; k < n; k++) keys [k] = rows [k];
		radixSort (rows, n, 4, null);
	}

	private void histogram (long [] k, int n, int bytes, int shift) {
		Arrays.fill (counts, 0, bytes << 8, 0);
		for (int i = 0; i < n; i++) {
			long key = k [i] >>> shift;
			for (int b = 0; b < bytes; b++) counts [b << 8 | (int) (key >>> (b << 3)) & 0xFF]++;
		}
	}

	private void prefix (int base) {
		for (int d = 0, sum = 0; d < 256; d++) {
			int c = counts [base | d];
			counts [base | d] = sum;
			sum += c;
		}
	}

	/* stable LSD radix sort of rows [0, n) by keys [0, n) (unsigned, the low `bytes` bytes), permuting satellite (if any) along */
	void radixSort (int [] rows, int n, int bytes, long [] satellite) {
		histogram (keys, n, bytes, 0);
		if (satellite != null && satelliteTmp.length < n) satelliteTmp = new long [keys.length];
		int [] r = rows, rt = rowTmp;
		long [] k = keys, kt = keyTmp, s = satellite, st = satelliteTmp;
		for (int b = 0; b < bytes; b++) {
			int base = b << 8, shift = b << 3;
			if (counts [base | (int) (k [0] >>> shift) & 0xFF] == n) continue;
			prefix (base);
			for (int i = 0; i < n; i++) {
				int p = counts [base | (int) (k [i] >>> shift) & 0xFF]++;
				rt [p] = r [i];
				kt [p] = k [i];
				if (s != null) st [p] = s [i];
			}
			int [] swapRows = r; r = rt; rt = swapRows;
			long [] swapKeys = k; k = kt; kt = swapKeys;
			if (s != null) {
				swapKeys = s; s = st; st = swapKeys;
			}
		}
		if (r != rows) {
			System.arraycopy (r, 0, rows, 0, n);
			System.arraycopy (k, 0, keys, 0, n);
			if (s != null) System.arraycopy (s, 0, satellite, 0, n);
		}
	}

	/* dense ranks (0, 1, ...) of the items 0 .. n-1 (n > 0) under the comparator of item indices, into keys; returns the largest rank */
	long ranks (int n, IntBinaryOperator compareItems) {
		for (int i = 0; i < n; i++) idx [i] = i;
		mergeSort (idx, n, idxTmp, compareItems);
		long rank = 0;
		keys [idx [0]] = 0;
		for (int k = 1; k < n; k++) {
			if (compareItems.applyAsInt (idx [k - 1], idx [k]) != 0) rank++;
			keys [idx [k]] = rank;
		}
		return rank;
	}

	/*
	 * dense ranks of texts [0, n) into keys, sorting only the distinct texts: an open-addressing
	 * table (String.hashCode, linear probing) maps each text to its first item; the distinct items
	 * are merge-sorted and ranked, then every item takes its distinct text's rank. Returns the
	 * largest rank, or -1 (nothing written) when more than half of the texts are distinct.
	 */
	long distinctRanks (int n, Comparator<? super String> compare) {
		if (ids.length < n) ids = new int [Math.max (n, ids.length + (ids.length >> 1))];
		int mask = table (TABLE) - 1, distinct = 0;
		for (int i = 0; i < n; i++) {
			String text = texts [i];
			int slot = mix (text == null ? 0 : text.hashCode ()) & mask;
			while (slots [slot] >= 0 && !Objects.equals (texts [slots [slot]], text)) slot = (slot + 1) & mask;
			if (slots [slot] >= 0) {
				ids [i] = ids [slots [slot]];
				continue;
			}
			if (++distinct > n >> 1) return -1;
			slots [slot] = i;
			idx [distinct - 1] = i;
			ids [i] = distinct - 1;
			if (distinct << 1 > mask) mask = growTexts (mask, distinct);
		}
		mergeSort (idx, distinct, idxTmp, (a, b) -> compare.compare (texts [a], texts [b]));
		long rank = 0;
		for (int k = 0; k < distinct; k++) {
			if (k > 0 && compare.compare (texts [idx [k - 1]], texts [idx [k]]) != 0) rank++;
			rowTmp [ids [idx [k]]] = (int) rank;
		}
		for (int i = 0; i < n; i++) keys [i] = rowTmp [ids [i]];
		return rank;
	}

	/*
	 * replaces the (normalized, unsigned) keys [0, n) by their dense ranks when at most n/2 of them are distinct
	 * (a growing open-addressing table, then a sort of the distinct values only); returns the largest rank, or -1
	 * (keys unchanged) when too many are distinct
	 */
	long compressKeys (int n) {
		if (ids.length < n) ids = new int [Math.max (n, ids.length + (ids.length >> 1))];
		int mask = table (TABLE) - 1, distinct = 0;
		for (int i = 0; i < n; i++) {
			long key = keys [i];
			int slot = mix (Long.hashCode (key)) & mask;
			while (slots [slot] >= 0 && values [slots [slot]] != key) slot = (slot + 1) & mask;
			if (slots [slot] >= 0) {
				ids [i] = slots [slot];
				continue;
			}
			if (++distinct > n >> 1) return -1;
			if (values.length < distinct) values = Arrays.copyOf (values, Math.max (TABLE, values.length << 1));
			values [distinct - 1] = key;
			slots [slot] = distinct - 1;
			ids [i] = distinct - 1;
			if (distinct << 1 > mask) mask = growValues (mask, distinct);
		}
		for (int k = 0; k < distinct; k++) keyTmp [k] = values [k] ^ Long.MIN_VALUE;
		Arrays.sort (keyTmp, 0, distinct);
		for (int k = 0; k < distinct; k++) rowTmp [k] = Arrays.binarySearch (keyTmp, 0, distinct, values [k] ^ Long.MIN_VALUE);
		for (int i = 0; i < n; i++) keys [i] = rowTmp [ids [i]];
		return distinct - 1;
	}

	/* an empty table of capacity slots (a power of two) */
	private int table (int capacity) {
		if (slots.length < capacity) slots = new int [capacity];
		Arrays.fill (slots, 0, capacity, -1);
		return capacity;
	}

	private int growTexts (int mask, int distinct) {
		int grown = table ((mask + 1) << 1) - 1;
		for (int k = 0; k < distinct; k++) {
			String text = texts [idx [k]];
			int slot = mix (text == null ? 0 : text.hashCode ()) & grown;
			while (slots [slot] >= 0) slot = (slot + 1) & grown;
			slots [slot] = idx [k];
		}
		return grown;
	}

	private int growValues (int mask, int distinct) {
		int grown = table ((mask + 1) << 1) - 1;
		for (int k = 0; k < distinct; k++) {
			int slot = mix (Long.hashCode (values [k])) & grown;
			while (slots [slot] >= 0) slot = (slot + 1) & grown;
			slots [slot] = k;
		}
		return grown;
	}

	/* spreads a hash code over the low bits (Fibonacci multiply, then fold the high half down) */
	private static int mix (int hash) {
		int x = hash * 0x9E3779B9;
		return x ^ x >>> 16;
	}

	static int bytesFor (long maxKey) {
		return Math.max (1, (64 - Long.numberOfLeadingZeros (maxKey) + 7) >> 3);
	}

	/* stable merge sort of a [0, n): insertion-sorted runs, then bottom-up merges (an ordered pair of runs is copied) */
	static void mergeSort (int [] a, int n, int [] tmp, IntBinaryOperator compare) {
		for (int low = 0; low < n; low += RUN) insertionSort (a, low, Math.min (low + RUN, n), compare);
		int [] from = a, to = tmp;
		for (int width = RUN; width < n; width <<= 1) {
			for (int low = 0; low < n; low += width << 1) {
				merge (from, to, low, Math.min (low + width, n), Math.min (low + (width << 1), n), compare);
			}
			int [] swap = from; from = to; to = swap;
		}
		if (from != a) System.arraycopy (from, 0, a, 0, n);
	}

	private static void insertionSort (int [] a, int low, int high, IntBinaryOperator compare) {
		for (int i = low + 1; i < high; i++) {
			int item = a [i], j = i - 1;
			while (j >= low && compare.applyAsInt (a [j], item) > 0) {
				a [j + 1] = a [j];
				j--;
			}
			a [j + 1] = item;
		}
	}

	private static void merge (int [] from, int [] to, int low, int middle, int high, IntBinaryOperator compare) {
		if (middle >= high || compare.applyAsInt (from [middle - 1], from [middle]) <= 0) {
			System.arraycopy (from, low, to, low, high - low);
			return;
		}
		int left = low, right = middle, out = low;
		while (left < middle && right < high) {
			to [out++] = compare.applyAsInt (from [right], from [left]) < 0 ? from [right++] : from [left++];
		}
		System.arraycopy (from, left, to, out, middle - left);
		System.arraycopy (from, right, to, out + middle - left, high - right);
	}
}
