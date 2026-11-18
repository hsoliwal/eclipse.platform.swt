/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

import java.util.Arrays;

/**
 * Array-only two-axis spatial index for half-open, signed-long rectangles.
 * Build on scene mutation and query on the SWT UI thread. No per-layer nodes,
 * HashMaps, lambda allocations, or boxing are needed during query.
 *
 * <p>Each axis is a stable signed-long radix sort of rectangle starts with a
 * prefix maximum of rectangle ends. A rectangle can intersect a query only
 * between the first prefix end above its lower bound and the first start at
 * or above its upper bound. The query selects the narrower axis and checks
 * both coordinates. Result IDs are returned in caller-provided Z order.</p>
 *
 * <p>Building takes O(16N) radix passes; query takes O(log N + scanned +
 * K log K) with an expected small scanned range for sparse, localized UI
 * geometry. Pathological overlapping rectangles remain O(N).</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class LayerRectIndex {
	/** Supplies exact world bounds and layer order at rebuild time. */
	public interface Bounds {
		int size();
		boolean active(int index);
		long left(int index);
		long top(int index);
		long right(int index);
		long bottom(int index);
	}

	private long[] left = new long[0];
	private long[] top = new long[0];
	private long[] right = new long[0];
	private long[] bottom = new long[0];
	private long[] maxRight = new long[0];
	private long[] maxBottom = new long[0];
	private int[] sourceIndex = new int[0];
	private int[] xOrder = new int[0];
	private int[] yOrder = new int[0];
	private int[] sortScratch = new int[0];
	private int[] results = new int[0];
	private final int[] frequencies = new int[256];
	private int count;
	private int resultCount;
	private long generation;

	public void rebuild(Bounds rectangles) {
		if (rectangles == null) throw new IllegalArgumentException("bounds required");
		int size = rectangles.size();
		if (size < 0) throw new IllegalArgumentException("negative size");
		count = 0;
		resultCount = 0;
		for (int i = 0; i < size; i++) {
			if (!rectangles.active(i)) continue;
			long l = rectangles.left(i);
			long t = rectangles.top(i);
			long r = rectangles.right(i);
			long b = rectangles.bottom(i);
			if (l > r || t > b) throw new IllegalArgumentException("inverted bounds at " + i);
			if (l == r || t == b) continue;
			if (count == left.length) ensureCapacity(count + 1);
			int slot = count++;
			left[slot] = l;
			top[slot] = t;
			right[slot] = r;
			bottom[slot] = b;
			sourceIndex[slot] = i;
			xOrder[slot] = slot;
			yOrder[slot] = slot;
		}
		radixSort(xOrder, left, count);
		radixSort(yOrder, top, count);
		long xr = Long.MIN_VALUE, yb = Long.MIN_VALUE;
		for (int i = 0; i < count; i++) {
			xr = Math.max(xr, right[xOrder[i]]);
			yb = Math.max(yb, bottom[yOrder[i]]);
			maxRight[i] = xr;
			maxBottom[i] = yb;
		}
		generation++;
	}

	/** Query a half-open rectangle. Results remain valid until the next query/rebuild. */
	public int query(long x0, long y0, long x1, long y1) {
		resultCount = 0;
		if (x0 >= x1 || y0 >= y1 || count == 0) return 0;
		int xe = firstStartAtLeast(left, xOrder, x1);
		int xs = firstPrefixAbove(maxRight, x0);
		int ye = firstStartAtLeast(top, yOrder, y1);
		int ys = firstPrefixAbove(maxBottom, y0);
		if (xe <= xs || ye <= ys) return 0;
		boolean xAxis = xe - xs <= ye - ys;
		int[] selected = xAxis ? xOrder : yOrder;
		int start = xAxis ? xs : ys;
		int end = xAxis ? xe : ye;
		for (int i = start; i < end; i++) {
			int slot = selected[i];
			if (left[slot] < x1 && right[slot] > x0 &&
					top[slot] < y1 && bottom[slot] > y0) {
				results[resultCount++] = sourceIndex[slot];
			}
		}
		Arrays.sort(results, 0, resultCount);
		return resultCount;
	}

	/** Returns an original source index in bottom-to-top Z order. */
	public int resultAt(int at) {
		if (at < 0 || at >= resultCount) throw new IndexOutOfBoundsException(at);
		return results[at];
	}

	public int indexedCount() { return count; }
	public long generation() { return generation; }

	/** Release retained array capacity explicitly on viewport disposal. */
	public void clear() {
		left = top = right = bottom = maxRight = maxBottom = new long[0];
		sourceIndex = xOrder = yOrder = sortScratch = results = new int[0];
		count = resultCount = 0;
		generation++;
	}

	private void ensureCapacity(int needed) {
		if (left.length >= needed) return;
		int capacity = Math.max(16, left.length);
		while (capacity < needed) {
			int grown = capacity + Math.max(16, capacity >>> 1);
			capacity = grown > capacity ? grown : needed;
		}
		left = Arrays.copyOf(left, capacity);
		top = Arrays.copyOf(top, capacity);
		right = Arrays.copyOf(right, capacity);
		bottom = Arrays.copyOf(bottom, capacity);
		maxRight = Arrays.copyOf(maxRight, capacity);
		maxBottom = Arrays.copyOf(maxBottom, capacity);
		sourceIndex = Arrays.copyOf(sourceIndex, capacity);
		xOrder = Arrays.copyOf(xOrder, capacity);
		yOrder = Arrays.copyOf(yOrder, capacity);
		sortScratch = Arrays.copyOf(sortScratch, capacity);
		results = Arrays.copyOf(results, capacity);
	}

	private int firstStartAtLeast(long[] starts, int[] order, long target) {
		int lo = 0, hi = count;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (starts[order[mid]] < target) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	private int firstPrefixAbove(long[] maxima, long target) {
		int lo = 0, hi = count;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (maxima[mid] <= target) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	private void radixSort(int[] order, long[] keys, int length) {
		if (length < 2) return;
		if (length <= 16) {
			// A typical viewport has few layers. Avoid eight bucket resets and
			// scratch-lane passes for a stack smaller than a radix histogram.
			// Strict comparison keeps equal starts in their original order.
			for (int i = 1; i < length; i++) {
				int slot = order[i];
				long key = keys[slot];
				int j = i;
				while (j > 0 && keys[order[j - 1]] > key) {
					order[j] = order[j - 1];
					j--;
				}
				order[j] = slot;
			}
			return;
		}
		int[] from = order, to = sortScratch;
		for (int shift = 0; shift < Long.SIZE; shift += 8) {
			Arrays.fill(frequencies, 0);
			for (int i = 0; i < length; i++) {
				int bucket = (int) (((keys[from[i]] ^ Long.MIN_VALUE) >>> shift) & 255);
				frequencies[bucket]++;
			}
			int prefix = 0;
			for (int i = 0; i < frequencies.length; i++) {
				int freq = frequencies[i];
				frequencies[i] = prefix;
				prefix += freq;
			}
			for (int i = 0; i < length; i++) {
				int item = from[i];
				int bucket = (int) (((keys[item] ^ Long.MIN_VALUE) >>> shift) & 255);
				to[frequencies[bucket]++] = item;
			}
			int[] temporary = from;
			from = to;
			to = temporary;
		}
		// Eight byte-passes is even; output is in the original order array.
		if (from != order) System.arraycopy(from, 0, order, 0, length);
	}
}
