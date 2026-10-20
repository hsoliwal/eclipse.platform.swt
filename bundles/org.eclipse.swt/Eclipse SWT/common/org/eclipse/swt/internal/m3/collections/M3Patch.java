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
 * com.m3.collections.M3Patch (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;

/**
 * The edit between two keyed snapshots of one ordered list, as produced by {@link M3ListDiff#diff(long[], long[])} or
 * built from runs ({@link #of}): remove runs, insert runs and the moves among them, all in packed {@code int} lanes
 * (no entry objects). It is the structural delta of every core view (brief rule 24) and the run kernel under the
 * public model patches (rule 26: one section of {@code org.eclipse.swt.m3.M3Patch} / {@code javax.swing.M3Patch}).
 * <p>Application order (see {@link #applyTo(Target)}): first the remove runs, highest index first, so every index
 * refers to the old snapshot; then the insert runs, lowest index first, so every index refers to the new snapshot.
 * A moved key appears once as removed (at its old index) and once as inserted (at its new index); {@link #moveCount()}
 * and {@link #moveFrom(int)} / {@link #moveTo(int)} pair them for callers that animate moves. Keys that stay put
 * (the longest run of keys whose relative order is unchanged) are never touched.</p>
 * <p>Cost: accessors O(1); {@link #of} O(runs) with validation; {@link #applyTo(Target)} O(runs) target calls;
 * {@link #compose} O(runs of both); {@link #apply(long[], long[])} O(n x runs) array moves (a test and small-list
 * helper).</p>
 * <p>Threads: immutable. Every field is final and {@link #of} copies its arrays, so a patch built on one thread can
 * be handed to another (a worker diffs, the UI thread applies) without locking.</p>
 */
public final class M3Patch {
	/**
	 * Receiver of a patch, for example a core view or a native row host. Indices follow the application order of
	 * {@link M3Patch#applyTo(Target)}. A receiver is called on the thread that applies the patch.
	 */
	public interface Target {
		/** Removes {@code count} entries at {@code index}. */
		void remove (int index, int count);

		/** Inserts {@code count} entries at {@code index}: the new snapshot's entries {@code index .. index + count - 1}. */
		void insert (int index, int count);

		/**
		 * The contents of {@code count} entries at {@code index} changed in place (an update run of a model patch;
		 * the core run kernel itself carries none). Default: nothing to do, for receivers that hold no contents.
		 */
		default void update (int index, int count) {
		}
	}

	private final int [] removeAt, removeCount, insertAt, insertCount, moveFrom, moveTo;
	private final int removed, inserted, kept;

	M3Patch (int [] removeAt, int [] removeCount, int [] insertAt, int [] insertCount, int [] moveFrom, int [] moveTo,
			int kept) {
		this.removeAt = removeAt;
		this.removeCount = removeCount;
		this.insertAt = insertAt;
		this.insertCount = insertCount;
		this.moveFrom = moveFrom;
		this.moveTo = moveTo;
		this.removed = total (removeCount);
		this.inserted = total (insertCount);
		this.kept = kept;
	}

	/**
	 * A patch of a sequence of {@code size} entries from runs: remove runs from the highest index down (old indices),
	 * then insert runs from the lowest index up (new indices); no moves. The arrays are copied.
	 */
	public static M3Patch of (int size, int [] removeAt, int [] removeCount, int [] insertAt, int [] insertCount) {
		if (removeAt.length != removeCount.length || insertAt.length != insertCount.length) throw new IllegalArgumentException ("run lanes");
		int bound = size, removed = 0;
		for (int i = 0; i < removeAt.length; i++) {
			if (removeCount [i] <= 0 || removeAt [i] < 0 || removeAt [i] > bound - removeCount [i]) throw new IllegalArgumentException ("remove run " + i);
			bound = removeAt [i];
			removed += removeCount [i];
		}
		int floor = 0, length = size - removed;
		for (int i = 0; i < insertAt.length; i++) {
			if (insertCount [i] <= 0 || insertAt [i] < floor || insertAt [i] > length) throw new IllegalArgumentException ("insert run " + i);
			length += insertCount [i];
			floor = insertAt [i] + insertCount [i];
		}
		return new M3Patch (removeAt.clone (), removeCount.clone (), insertAt.clone (), insertCount.clone (), EMPTY, EMPTY, size - removed);
	}

	/** One insert run: {@code count} entries at {@code at} of a sequence of {@code size}. */
	public static M3Patch insertion (int size, int at, int count) {
		return of (size, EMPTY, EMPTY, new int [] {at}, new int [] {count});
	}

	/** One remove run: {@code count} entries at {@code at} of a sequence of {@code size}. */
	public static M3Patch removal (int size, int at, int count) {
		return of (size, new int [] {at}, new int [] {count}, EMPTY, EMPTY);
	}

	private static final int [] EMPTY = new int [0];

	private static int total (int [] counts) {
		int sum = 0;
		for (int c : counts) sum = Math.addExact (sum, c);
		return sum;
	}

	/** Number of remove runs; run {@code i} removes {@link #removeCount(int)} entries at {@link #removeAt(int)}. */
	public int removeRuns () {
		return removeAt.length;
	}

	/** Old-snapshot index of remove run {@code i} (runs are ordered from the highest index down). */
	public int removeAt (int i) {
		return removeAt [i];
	}

	/** Length of remove run {@code i}. */
	public int removeCount (int i) {
		return removeCount [i];
	}

	/** Number of insert runs; run {@code i} inserts {@link #insertCount(int)} entries at {@link #insertAt(int)}. */
	public int insertRuns () {
		return insertAt.length;
	}

	/** New-snapshot index of insert run {@code i} (runs are ordered from the lowest index up). */
	public int insertAt (int i) {
		return insertAt [i];
	}

	/** Length of insert run {@code i}. */
	public int insertCount (int i) {
		return insertCount [i];
	}

	/** Number of moved keys (each also counted in the removed and inserted totals). */
	public int moveCount () {
		return moveFrom.length;
	}

	/** Old-snapshot index of moved key {@code i}. */
	public int moveFrom (int i) {
		return moveFrom [i];
	}

	/** New-snapshot index of moved key {@code i}. */
	public int moveTo (int i) {
		return moveTo [i];
	}

	/** Entries removed, moves included. */
	public int removedCount () {
		return removed;
	}

	/** Entries inserted, moves included. */
	public int insertedCount () {
		return inserted;
	}

	/** Entries that stay in place (in relative order). */
	public int keptCount () {
		return kept;
	}

	/** Whether the two snapshots were equal. */
	public boolean isEmpty () {
		return removed == 0 && inserted == 0;
	}

	/** Entries of the old snapshot (kept + removed). */
	public int oldSize () {
		return kept + removed;
	}

	/** Entries of the new snapshot (kept + inserted). */
	public int newSize () {
		return kept + inserted;
	}

	/**
	 * The patch with the effect of {@code first} followed by {@code then} ({@code first.newSize () == then.oldSize ()}):
	 * an entry kept by both stays kept; every other entry is removed and inserted, moves included (the result has no
	 * moves). A two-pointer merge of the kept intervals of both, O(runs of both): the way to coalesce the patches of
	 * one frame into one.
	 */
	public static M3Patch compose (M3Patch first, M3Patch then) {
		if (first.newSize () != then.oldSize ()) {
			throw new IllegalArgumentException ("first ends with " + first.newSize () + " entries, then starts with " + then.oldSize ());
		}
		int [] a = first.keptIntervals (), b = then.keptIntervals ();
		int [] c = new int [a.length + b.length];
		int pieces = 0, kept = 0;
		for (int i = 0, j = 0; i < a.length && j < b.length;) {
			int aFrom = a [i + 1], aTo = aFrom + a [i + 2], bFrom = b [j], bTo = bFrom + b [j + 2];
			int low = Math.max (aFrom, bFrom), high = Math.min (aTo, bTo);
			if (low < high) {
				c [pieces * 3] = a [i] + (low - aFrom);
				c [pieces * 3 + 1] = b [j + 1] + (low - bFrom);
				c [pieces * 3 + 2] = high - low;
				pieces++;
				kept += high - low;
			}
			if (aTo <= bTo) i += 3;
			else j += 3;
		}
		int [][] removes = gaps (c, pieces, 0, first.oldSize (), true);
		int [][] inserts = gaps (c, pieces, 1, then.newSize (), false);
		return new M3Patch (removes [0], removes [1], inserts [0], inserts [1], EMPTY, EMPTY, kept);
	}

	/* kept entries as (old index, new index, length) triples, ascending: the k-th kept old entry is the k-th kept new one */
	private int [] keptIntervals () {
		int [] out = new int [3 * (removeAt.length + insertAt.length + 1)];
		int n = 0;
		int oldCursor = 0, oldRun = removeAt.length - 1, oldEnd = nextGap (oldRun, true);
		int newCursor = 0, newRun = 0, newEnd = nextGap (newRun, false);
		while (oldCursor < oldSize () && newCursor < newSize ()) {
			if (oldCursor == oldEnd) {
				oldCursor += removeCount [oldRun--];
				oldEnd = nextGap (oldRun, true);
				continue;
			}
			if (newCursor == newEnd) {
				newCursor += insertCount [newRun++];
				newEnd = nextGap (newRun, false);
				continue;
			}
			int length = Math.min (oldEnd - oldCursor, newEnd - newCursor);
			out [n++] = oldCursor;
			out [n++] = newCursor;
			out [n++] = length;
			oldCursor += length;
			newCursor += length;
		}
		return Arrays.copyOf (out, n);
	}

	/* where the next run starts (old side: remove runs from the lowest up; new side: insert runs), or the snapshot end */
	private int nextGap (int run, boolean old) {
		if (old) return run >= 0 ? removeAt [run] : oldSize ();
		return run < insertAt.length ? insertAt [run] : newSize ();
	}

	/* the complement of the triples' ranges on one side within [0, size): remove runs highest first, insert runs lowest first */
	private static int [][] gaps (int [] triples, int pieces, int side, int size, boolean descending) {
		int [] at = new int [pieces + 1], count = new int [pieces + 1];
		int n = 0, cursor = 0;
		for (int p = 0; p <= pieces; p++) {
			int start = p < pieces ? triples [p * 3 + side] : size;
			if (start > cursor) {
				at [n] = cursor;
				count [n++] = start - cursor;
			}
			if (p < pieces) cursor = start + triples [p * 3 + 2];
		}
		at = Arrays.copyOf (at, n);
		count = Arrays.copyOf (count, n);
		if (descending) {
			for (int i = 0, j = n - 1; i < j; i++, j--) {
				int t = at [i];
				at [i] = at [j];
				at [j] = t;
				t = count [i];
				count [i] = count [j];
				count [j] = t;
			}
		}
		return new int [][] {at, count};
	}

	/** Applies the runs to {@code target}: removes highest first, then inserts lowest first. */
	public void applyTo (Target target) {
		for (int i = 0; i < removeAt.length; i++) target.remove (removeAt [i], removeCount [i]);
		for (int i = 0; i < insertAt.length; i++) target.insert (insertAt [i], insertCount [i]);
	}

	/** Applies the patch to {@code before}, taking inserted keys from {@code after}; answers {@code after}'s keys. */
	public long [] apply (long [] before, long [] after) {
		long [] lane = Arrays.copyOf (before, before.length + inserted);
		int [] size = {before.length};
		applyTo (new Target () {
			@Override
			public void remove (int index, int count) {
				System.arraycopy (lane, index + count, lane, index, size [0] - index - count);
				size [0] -= count;
			}

			@Override
			public void insert (int index, int count) {
				System.arraycopy (lane, index, lane, index + count, size [0] - index);
				System.arraycopy (after, index, lane, index, count);
				size [0] += count;
			}
		});
		return Arrays.copyOf (lane, size [0]);
	}
}
