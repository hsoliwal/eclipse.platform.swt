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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 118b3cb7e59ea4d4cc32468dccfc6f32e5715dcb,
 * com.m3.collections.M3SparseExtents (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 553eefd41d9961d303c41e0e4e181712e64a1e45,
 * VirtualRowExtents.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;

/**
 * Variable row extents with a measurement cache: where a row starts, which row
 * holds an offset, which rows a viewport shows, how far to scroll to reveal a row,
 * and how much to move the scroll offset when a row above the viewport changes size.
 *
 * <p>{@link M3ScrollMetrics} covers uniform rows; this class is its variable
 * counterpart for widgets whose rows differ in height (wrapped text, owner-drawn
 * items, nested content) or columns that differ in width. Rows that have not been
 * measured count as the estimate, so the scrollable extent is available before every
 * row is laid out.</p>
 *
 * <p>Unmeasured rows are implicit: logical count and estimate allocate no row-sized
 * backing. Measured rows are the nodes of an {@link M3OrderStatisticForest} (one
 * tree): the row is the key, the measured extent the weight, and every node keeps
 * its subtree count and extent sum, so the offset of row r is
 * {@code (r - measured rows before r) * estimate + measured extent before r}.
 * With m measured rows: offset, row lookup and a measurement (first or repeated) are
 * O(log m); an estimate change is O(1); opening or deleting rows ({@link #insert},
 * {@link #remove}, the {@link M3Patch.Target} operations) is one lazy key shift plus
 * a range removal, O(log m + removed measurements), never a rebuild (brief rule 24,
 * viewport deltas: one row height or column width in O(log m)). {@link #work()}
 * counts the visited nodes. No Item/proxy or per-row object is created; reset drops
 * all measurement backing.</p>
 *
 * <p>Threads: not thread-safe and holds no lock; confined to the thread that applies the
 * model's patches and measures rows (the UI thread for a widget).</p>
 *
 * <p>The behaviour follows two pattern donors, read at pinned commits and not copied:
 * react-window (MIT, 439cfee: lib/core getOffsetForIndex, getEstimatedSize,
 * createCachedBounds, getStartStopIndices) for lazy measurement, the
 * start/end/center/auto/smart alignments and the oversized-row rule; and TanStack
 * Virtual (MIT, f211d3a: packages/virtual-core getOffsetForIndex and resizeItem) for
 * the scroll-anchoring rule when a row above the fold is measured. Two differences:
 * react-window keeps an append-only prefix cache (rows are measured in order and a
 * reset is needed to re-measure), whereas rows here can be measured, re-measured or
 * cleared in any order; and the estimate for unmeasured rows is a constant
 * the caller sets (setEstimate), where react-window uses the running average.</p>
 */
public final class M3SparseExtents implements M3Patch.Target {
	static final int ALIGN_AUTO = 0;
	static final int ALIGN_START = 1;
	static final int ALIGN_CENTER = 2;
	static final int ALIGN_END = 3;
	static final int ALIGN_SMART = 4;
	private static final int TREE = -1;
	private static final int [] EMPTY = new int [0];
	private static final long [] EMPTY_EXTENTS = new long [0];

	private int estimate;
	private int count;
	private M3OrderStatisticForest index = new M3OrderStatisticForest ();
	/* node lanes by slot: the row (relative to pending shifts, see M3OrderStatisticForest) and the measured extent */
	private int [] rows = EMPTY;
	private long [] extents = EMPTY_EXTENTS;
	/* free slots below nextSlot, as a stack */
	private int [] free = EMPTY;
	private int freeCount, nextSlot;
	private int [] removed = EMPTY;

	public M3SparseExtents (int estimate) {
		checkEstimate (estimate);
		this.estimate = estimate;
	}

	public int rowCount () {
		return count;
	}

	public int estimate () {
		return estimate;
	}

	public int measuredCount () {
		return index.size (TREE);
	}

	/** Nodes visited by the measurement index since the last reset (the cost counter of brief rule 24). */
	public long work () {
		return index.work ();
	}

	public boolean isMeasured (int row) {
		checkRow (row);
		return index.find (TREE, row) >= 0;
	}

	/** Measured extent, or the estimate for a row that was never measured. */
	public int extentOf (int row) {
		checkRow (row);
		return effective (row);
	}

	/** O(1): unmeasured rows are counted, not stored. */
	public void setEstimate (int estimate) {
		checkEstimate (estimate);
		this.estimate = estimate;
	}

	/** Forgets every measurement and sets the row count; all rows become the estimate. */
	public void reset (int rows) {
		if (rows < 0) throw new IllegalArgumentException ("negative row count");
		count = rows;
		index = new M3OrderStatisticForest ();
		this.rows = EMPTY;
		extents = EMPTY_EXTENTS;
		free = removed = EMPTY;
		freeCount = nextSlot = 0;
	}

	/** Records the measured extent of one row (a row height or a column width): O(log m). */
	public void setExtent (int row, int extent) {
		checkRow (row);
		if (extent < 0) throw new IllegalArgumentException ("negative extent");
		int slot = index.find (TREE, row);
		if (slot >= 0) {
			long delta = extent - extents [slot];
			extents [slot] = extent;
			index.adjust (TREE, slot, delta);
			return;
		}
		slot = allocate ();
		rows [slot] = row;
		extents [slot] = extent;
		index.insert (TREE, slot);
	}

	/** Returns one row to the estimate. */
	public void clearExtent (int row) {
		checkRow (row);
		int slot = index.find (TREE, row);
		if (slot < 0) return;
		index.remove (TREE, slot);
		release (slot);
	}

	/** Returns every row from {@code row} on to the estimate (react-window resetAfterIndex). */
	public void clearFrom (int row) {
		if (row < 0 || row > count) throw new IllegalArgumentException ("invalid row");
		dropRange (row, count);
	}

	/** Opens {@code rows} unmeasured rows at {@code index}: one lazy shift of the measured rows after it, O(log m). */
	@Override
	public void insert (int index, int rows) {
		if (rows < 0 || index < 0 || index > count) throw new IllegalArgumentException ("invalid insert");
		if (rows == 0) return;
		int next = Math.addExact (count, rows);
		this.index.shift (TREE, index, rows);
		count = next;
	}

	/** Deletes {@code rows} rows at {@code index}: O(log m + measurements removed). */
	@Override
	public void remove (int index, int rows) {
		if (rows < 0 || index < 0 || index > count - rows) throw new IllegalArgumentException ("invalid remove");
		if (rows == 0) return;
		dropRange (index, index + rows);
		this.index.shift (TREE, index + rows, -rows);
		count -= rows;
	}

	/** Applies a structural patch of the rows (remove runs, then insert runs); moved rows come back unmeasured. */
	public void apply (M3Patch patch) {
		patch.applyTo (this);
	}

	/** Start offset of {@code row}; {@code offsetOf (rowCount ())} is the total extent. O(log m). */
	public long offsetOf (int row) {
		if (row < 0 || row > count) throw new IllegalArgumentException ("invalid row");
		long before = index.prefixRank (TREE, row);
		return (long) (row - index.seekRank ()) * estimate + before;
	}

	public long totalExtent () {
		return (long) (count - index.size (TREE)) * estimate + index.total (TREE);
	}

	/** Row that contains {@code offset}; clamped to the first or last row, -1 when empty. O(log m). */
	public int rowAt (long offset) {
		if (count == 0) return -1;
		if (offset <= 0) return 0;
		if (offset >= totalExtent ()) return count - 1;
		// The last measured row that starts at or before the offset; zero-height rows stay half-open.
		int slot = index.seekOffset (TREE, offset, estimate);
		if (slot < 0) return (int) Math.min (offset / estimate, count - 1);
		int key = index.seekKey ();
		long start = (long) (key - index.seekRank ()) * estimate + index.seekBefore ();
		long end = start + extents [slot];
		if (offset < end) return key;
		return (int) Math.min (key + 1 + (offset - end) / estimate, count - 1);
	}

	/**
	 * Rows a viewport shows. Writes the first row to {@code out [0]} and the row after
	 * the last one to {@code out [1]}; both are 0 for an empty list.
	 */
	public void visibleRange (long offset, int viewport, int [] out) {
		if (viewport < 0) throw new IllegalArgumentException ("negative viewport");
		if (count == 0) {
			out [0] = 0;
			out [1] = 0;
			return;
		}
		int first = rowAt (offset);
		out [0] = first;
		out [1] = viewport == 0 ? first : rowAt (offset + viewport - 1) + 1;
	}

	public long maxScrollOffset (int viewport) {
		return Math.max (0L, totalExtent () - viewport);
	}

	public long clampOffset (long offset, int viewport) {
		return Math.max (0L, Math.min (offset, maxScrollOffset (viewport)));
	}

	/**
	 * Scroll offset that reveals {@code row}. START puts the row at the top, END at the
	 * bottom, CENTER in the middle; AUTO is the smallest move (none when the row is already
	 * revealed); SMART keeps the offset when the row is revealed and centers it otherwise.
	 * A row taller than the viewport is revealed while it fills the viewport. The result
	 * is clamped to the scrollable range.
	 */
	public long scrollOffsetFor (int row, int align, long current, int viewport) {
		checkRow (row);
		long start = offsetOf (row);
		long end = start + effective (row);
		long target = switch (align) {
			case ALIGN_START -> start;
			case ALIGN_END -> end - viewport;
			case ALIGN_CENTER -> start + Math.floorDiv (effective (row) - viewport, 2);
			case ALIGN_AUTO -> autoTarget (start, end, current, viewport);
			case ALIGN_SMART -> isRevealed (start, end, current, viewport) ? current
					: start + Math.floorDiv (effective (row) - viewport, 2);
			default -> throw new IllegalArgumentException ("invalid alignment");
		};
		return clampOffset (target, viewport);
	}

	/**
	 * Measures {@code row} and returns how far the caller should move its scroll offset
	 * so content already in view does not jump: the whole change when a first measurement
	 * lands on a row that starts above the offset, or when a re-measured row lies entirely
	 * above it and the user is not scrolling backward; otherwise 0.
	 */
	public long setExtentAnchored (int row, int extent, long scrollOffset, boolean scrollingBackward) {
		checkRow (row);
		if (extent < 0) throw new IllegalArgumentException ("negative extent");
		boolean first = index.find (TREE, row) < 0;
		long start = offsetOf (row);
		long end = start + effective (row);
		long delta = (long) extent - effective (row);
		setExtent (row, extent);
		if (delta == 0) return 0;
		boolean above = first ? start < scrollOffset : end <= scrollOffset && !scrollingBackward;
		return above ? delta : 0;
	}

	/** Offsets at which the row is revealed lie in [min (start, end - viewport), max (start, end - viewport)]. */
	private static boolean isRevealed (long start, long end, long current, int viewport) {
		return current >= Math.min (start, end - viewport) && current <= Math.max (start, end - viewport);
	}

	private static long autoTarget (long start, long end, long current, int viewport) {
		long low = Math.min (start, end - viewport);
		long high = Math.max (start, end - viewport);
		return current < low ? low : Math.min (current, high);
	}

	private int effective (int row) {
		int slot = index.find (TREE, row);
		return slot < 0 ? estimate : (int) extents [slot];
	}

	/* the measured rows in [from, to) return to the estimate: one range removal, O(log m + removed) */
	private void dropRange (int from, int to) {
		int n = index.countRange (TREE, from, to);
		if (n == 0) return;
		if (removed.length < n) removed = new int [M3LaneGrowth.capacity (removed.length, n, 4)];
		index.removeRange (TREE, from, to, removed);
		for (int i = 0; i < n; i++) release (removed [i]);
	}

	private int allocate () {
		if (freeCount > 0) return free [--freeCount];
		int slot = nextSlot++;
		if (slot == rows.length) {
			// One parent-owned lane directory: stage every allocation before publication.
			int grown = M3HeapGrowth.capacity (rows.length, slot + 1, 45);
			int [] nextRows = Arrays.copyOf (rows, grown);
			long [] nextExtents = Arrays.copyOf (extents, grown);
			index.ensureCapacity (grown);
			rows = nextRows;
			extents = nextExtents;
			index.lanes (rows, extents);
		}
		return slot;
	}

	private void release (int slot) {
		if (freeCount == free.length) free = Arrays.copyOf (free, M3LaneGrowth.capacity (free.length, freeCount + 1, 4));
		free [freeCount++] = slot;
	}

	private void checkRow (int row) {
		if (row < 0 || row >= count) throw new IllegalArgumentException ("invalid row");
	}

	private static void checkEstimate (int estimate) {
		if (estimate < 1) throw new IllegalArgumentException ("non-positive estimate");
	}
}
