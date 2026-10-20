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

import org.eclipse.swt.*;

/**
 * A delta of one ordered child list: remove, insert, update and move runs over row ids (enhanced M3
 * API, the model seam).
 * <p>
 * A patch edits the children of one parent ({@link #parent()}, {@link #ROOT} for the roots or a
 * flat list). Remove runs are in old indices, ordered from the highest index down; insert runs are in
 * new indices, ordered from the lowest index up, and carry the ids of the inserted rows; update runs
 * name rows whose labels changed, in new indices. A move pairs a removed index with an inserted one
 * that carry the same id, so a receiver can move the row's state instead of rebuilding it. The names
 * and the run order are those of the M3JDK collections patch, so one patch travels from a model view
 * to a widget unchanged. All runs are packed in <code>int</code> lanes and one <code>long</code> lane of
 * inserted ids; there are no entry objects. Instances are immutable.
 * </p>
 *
 * @since 3.138
 */
public final class M3Patch {

	/** The parent id of the roots of a tree and of every row of a flat list. */
	public static final long ROOT = -1;

	/** Receiver of a patch, in application order: removes, then inserts, then updates. */
	public interface Target {
		/**
		 * Removes rows.
		 *
		 * @param index the first row, in the indices of the list before this run
		 * @param count the number of rows
		 */
		void remove (int index, int count);

		/**
		 * Inserts rows.
		 *
		 * @param index the first new row
		 * @param count the number of rows
		 */
		void insert (int index, int count);

		/**
		 * Marks rows whose labels changed.
		 *
		 * @param index the first row, in the final indices
		 * @param count the number of rows
		 */
		void update (int index, int count);
	}

	private static final int [] NONE = new int [0];

	private final long parent;
	private final int [] removeAt, removeCount, insertAt, insertCount, insertStart, updateAt, updateCount, moveFrom, moveTo;
	private final long [] insertedIds;
	private final int removed, inserted, updated;

	M3Patch (long parent, int [] removeAt, int [] removeCount, int [] insertAt, int [] insertCount, long [] insertedIds,
			int [] updateAt, int [] updateCount, int [] moveFrom, int [] moveTo) {
		this.parent = parent;
		this.removeAt = removeAt;
		this.removeCount = removeCount;
		this.insertAt = insertAt;
		this.insertCount = insertCount;
		this.insertedIds = insertedIds;
		this.updateAt = updateAt;
		this.updateCount = updateCount;
		this.moveFrom = moveFrom;
		this.moveTo = moveTo;
		this.removed = sum (removeCount);
		this.inserted = sum (insertCount);
		this.updated = sum (updateCount);
		insertStart = new int [insertCount.length];
		for (int i = 1; i < insertCount.length; i++) insertStart [i] = insertStart [i - 1] + insertCount [i - 1];
	}

	/**
	 * Returns a builder of a patch of the children of a parent.
	 *
	 * @param parent the parent id, or {@link #ROOT}
	 * @return the builder
	 */
	public static Builder builder (long parent) {
		return new Builder (parent);
	}

	/**
	 * Returns the minimal patch from one ordered set of ids to another: the rows kept in place are a
	 * longest increasing subsequence of the old positions in the new order, every other old row is
	 * removed, every other new row is inserted, and an id present in both is paired as a move.
	 *
	 * @param parent the parent id, or {@link #ROOT}
	 * @param oldIds the ids before, unique
	 * @param newIds the ids after, unique
	 * @return the patch
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if an array is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if an array repeats an id</li>
	 * </ul>
	 */
	public static M3Patch diff (long parent, long [] oldIds, long [] newIds) {
		if (oldIds == null || newIds == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		LongIndex old = new LongIndex (oldIds.length);
		for (int i = 0; i < oldIds.length; i++) {
			if (old.put (oldIds [i], i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		LongIndex seen = new LongIndex (newIds.length);
		int [] seqOld = new int [Math.min (oldIds.length, newIds.length)], seqNew = new int [seqOld.length];
		int m = 0;
		for (int j = 0; j < newIds.length; j++) {
			if (seen.put (newIds [j], j) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			int position = old.get (newIds [j]);
			if (position >= 0) {
				seqOld [m] = position;
				seqNew [m++] = j;
			}
		}
		/* longest increasing subsequence of the old positions in new order (patience sorting) */
		int [] tails = new int [Math.max (1, m)], previous = new int [Math.max (1, m)];
		int length = 0;
		for (int s = 0; s < m; s++) {
			int low = 0, high = length;
			while (low < high) {
				int middle = (low + high) >>> 1;
				if (seqOld [tails [middle]] < seqOld [s]) low = middle + 1; else high = middle;
			}
			previous [s] = low > 0 ? tails [low - 1] : -1;
			tails [low] = s;
			if (low == length) length++;
		}
		BitSet keptOld = new BitSet (oldIds.length), keptNew = new BitSet (newIds.length);
		for (int s = length > 0 ? tails [length - 1] : -1; s >= 0; s = previous [s]) {
			keptOld.set (seqOld [s]);
			keptNew.set (seqNew [s]);
		}
		Builder builder = new Builder (parent);
		/* removes: runs of old rows not kept, highest first */
		for (int end = oldIds.length; (end = keptOld.previousClearBit (end - 1)) >= 0; ) {
			int start = keptOld.previousSetBit (end) + 1;
			builder.remove (start, end - start + 1);
			end = start;
		}
		/* inserts: runs of new rows not kept, lowest first */
		for (int start = keptNew.nextClearBit (0); start < newIds.length; ) {
			int next = keptNew.nextSetBit (start), end = next < 0 ? newIds.length : next;
			builder.insert (start, Arrays.copyOfRange (newIds, start, end));
			start = keptNew.nextClearBit (end);
		}
		/* moves: ids in both lists whose rows were not kept, in new order */
		for (int s = 0; s < m; s++) {
			if (!keptNew.get (seqNew [s])) builder.move (seqOld [s], seqNew [s]);
		}
		return builder.build ();
	}

	/** @return the parent id whose children this patch edits, or {@link #ROOT} */
	public long parent () {
		return parent;
	}

	/** @return the number of remove runs */
	public int removeRuns () {
		return removeAt.length;
	}

	/**
	 * @param i the run
	 * @return the first old index of remove run <code>i</code> (runs are ordered from the highest index down)
	 */
	public int removeAt (int i) {
		return removeAt [i];
	}

	/**
	 * @param i the run
	 * @return the length of remove run <code>i</code>
	 */
	public int removeCount (int i) {
		return removeCount [i];
	}

	/** @return the number of insert runs */
	public int insertRuns () {
		return insertAt.length;
	}

	/**
	 * @param i the run
	 * @return the first new index of insert run <code>i</code> (runs are ordered from the lowest index up)
	 */
	public int insertAt (int i) {
		return insertAt [i];
	}

	/**
	 * @param i the run
	 * @return the length of insert run <code>i</code>
	 */
	public int insertCount (int i) {
		return insertCount [i];
	}

	/**
	 * @param i the insert run
	 * @param k the row of the run, <code>0 &lt;= k &lt; insertCount(i)</code>
	 * @return the id of the inserted row
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if <code>k</code> is not a row of the run</li>
	 * </ul>
	 */
	public long insertedId (int i, int k) {
		if (k < 0 || k >= insertCount [i]) SWT.error (SWT.ERROR_INVALID_RANGE);
		return insertedIds [insertStart [i] + k];
	}

	/** @return the number of update runs */
	public int updateRuns () {
		return updateAt.length;
	}

	/**
	 * @param i the run
	 * @return the first final index of update run <code>i</code> (ascending)
	 */
	public int updateAt (int i) {
		return updateAt [i];
	}

	/**
	 * @param i the run
	 * @return the length of update run <code>i</code>
	 */
	public int updateCount (int i) {
		return updateCount [i];
	}

	/** @return the number of moves (each also counted as removed and as inserted) */
	public int moveCount () {
		return moveFrom.length;
	}

	/**
	 * @param i the move
	 * @return the old index of the moved row
	 */
	public int moveFrom (int i) {
		return moveFrom [i];
	}

	/**
	 * @param i the move
	 * @return the new index of the moved row
	 */
	public int moveTo (int i) {
		return moveTo [i];
	}

	/** @return the rows removed, moves included */
	public int removedCount () {
		return removed;
	}

	/** @return the rows inserted, moves included */
	public int insertedCount () {
		return inserted;
	}

	/** @return the rows marked as updated */
	public int updatedCount () {
		return updated;
	}

	/** @return whether the patch changes nothing */
	public boolean isEmpty () {
		return removed == 0 && inserted == 0 && updated == 0;
	}

	/**
	 * Applies the runs to a target: removes (highest first), then inserts (lowest first), then updates.
	 *
	 * @param target the receiver
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the target is null</li>
	 * </ul>
	 */
	public void applyTo (Target target) {
		if (target == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		for (int i = 0; i < removeAt.length; i++) target.remove (removeAt [i], removeCount [i]);
		for (int i = 0; i < insertAt.length; i++) target.insert (insertAt [i], insertCount [i]);
		for (int i = 0; i < updateAt.length; i++) target.update (updateAt [i], updateCount [i]);
	}

	/**
	 * Applies the patch to a list of ids.
	 *
	 * @param before the ids before
	 * @return a new array of the ids after
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the array is null</li>
	 *    <li>ERROR_INVALID_RANGE - if a run does not fit the list</li>
	 * </ul>
	 */
	public long [] apply (long [] before) {
		if (before == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		int size = before.length;
		for (int i = 0; i < removeAt.length; i++) {
			if (removeAt [i] + removeCount [i] > size) SWT.error (SWT.ERROR_INVALID_RANGE);
			size -= removeCount [i];
		}
		for (int i = 0; i < insertAt.length; i++) {
			if (insertAt [i] > size) SWT.error (SWT.ERROR_INVALID_RANGE);
			size += insertCount [i];
		}
		for (int i = 0; i < updateAt.length; i++) {
			if (updateAt [i] + updateCount [i] > size) SWT.error (SWT.ERROR_INVALID_RANGE);
		}
		/* one pass, one array (catalogue: two-pointer merge of the kept rows and the insert runs) */
		long [] after = new long [size];
		int from = 0, to = 0, cut = removeAt.length - 1;
		for (int i = 0; i <= insertAt.length; i++) {
			int until = i < insertAt.length ? insertAt [i] : size;
			while (to < until) {
				while (cut >= 0 && removeAt [cut] == from) from += removeCount [cut--];
				int kept = Math.min (until - to, (cut >= 0 ? removeAt [cut] : before.length) - from);
				System.arraycopy (before, from, after, to, kept);
				from += kept;
				to += kept;
			}
			if (i < insertAt.length) {
				System.arraycopy (insertedIds, insertStart [i], after, to, insertCount [i]);
				to += insertCount [i];
			}
		}
		return after;
	}

	private static int sum (int [] values) {
		int sum = 0;
		for (int value : values) sum = Math.addExact (sum, value);
		return sum;
	}

	@Override
	public String toString () {
		return "M3Patch [parent " + parent + ", -" + removed + " +" + inserted + " ~" + updated + ", " + moveFrom.length + " moves]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}

	/**
	 * Builds a patch; runs must be given in application order.
	 *
	 * @since 3.138
	 */
	public static final class Builder {
		private final long parent;
		private int removes, inserts, updates, moves;
		private int [] removeAt = NONE, removeCount = NONE, insertAt = NONE, insertCount = NONE, updateAt = NONE,
				updateCount = NONE, moveFrom = NONE, moveTo = NONE;
		private long [] ids = new long [0];
		private int idCount;

		Builder (long parent) {
			this.parent = parent;
		}

		/**
		 * Adds a remove run, below the previous one (old indices, highest first).
		 *
		 * @param at the first old index
		 * @param count the number of rows, positive
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not below the previous remove run</li>
		 * </ul>
		 */
		public Builder remove (int at, int count) {
			if (at < 0 || count <= 0 || (removes > 0 && at + count > removeAt [removes - 1])) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			removeAt = grow (removeAt, removes);
			removeCount = grow (removeCount, removes);
			removeAt [removes] = at;
			removeCount [removes++] = count;
			return this;
		}

		/**
		 * Adds an insert run, after the previous one (new indices, lowest first).
		 *
		 * @param at the first new index
		 * @param newIds the ids of the inserted rows, at least one
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_NULL_ARGUMENT - if the ids are null</li>
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not after the previous insert run</li>
		 * </ul>
		 */
		public Builder insert (int at, long... newIds) {
			if (newIds == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
			if (at < 0 || newIds.length == 0 || (inserts > 0 && at < insertAt [inserts - 1] + insertCount [inserts - 1])) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			insertAt = grow (insertAt, inserts);
			insertCount = grow (insertCount, inserts);
			insertAt [inserts] = at;
			insertCount [inserts++] = newIds.length;
			if (idCount + newIds.length > ids.length) ids = Arrays.copyOf (ids, Math.max (ids.length * 2, idCount + newIds.length));
			System.arraycopy (newIds, 0, ids, idCount, newIds.length);
			idCount += newIds.length;
			return this;
		}

		/**
		 * Adds an update run, after the previous one (final indices, ascending).
		 *
		 * @param at the first final index
		 * @param count the number of rows, positive
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not after the previous update run</li>
		 * </ul>
		 */
		public Builder update (int at, int count) {
			if (at < 0 || count <= 0 || (updates > 0 && at < updateAt [updates - 1] + updateCount [updates - 1])) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			updateAt = grow (updateAt, updates);
			updateCount = grow (updateCount, updates);
			updateAt [updates] = at;
			updateCount [updates++] = count;
			return this;
		}

		/**
		 * Pairs a removed old index with an inserted new index that carry the same id.
		 *
		 * @param from an old index inside a remove run
		 * @param to a new index inside an insert run
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if <code>from</code> is not removed or <code>to</code> is not inserted (add the runs first)</li>
		 * </ul>
		 */
		public Builder move (int from, int to) {
			if (!inside (removeAt, removeCount, removes, from) || !inside (insertAt, insertCount, inserts, to)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			moveFrom = grow (moveFrom, moves);
			moveTo = grow (moveTo, moves);
			moveFrom [moves] = from;
			moveTo [moves++] = to;
			return this;
		}

		/** @return the patch */
		public M3Patch build () {
			return new M3Patch (parent, Arrays.copyOf (removeAt, removes), Arrays.copyOf (removeCount, removes),
					Arrays.copyOf (insertAt, inserts), Arrays.copyOf (insertCount, inserts), Arrays.copyOf (ids, idCount),
					Arrays.copyOf (updateAt, updates), Arrays.copyOf (updateCount, updates), Arrays.copyOf (moveFrom, moves),
					Arrays.copyOf (moveTo, moves));
		}

		private static boolean inside (int [] at, int [] count, int runs, int index) {
			for (int i = 0; i < runs; i++) {
				if (at [i] <= index && index < at [i] + count [i]) return true;
			}
			return false;
		}

		private static int [] grow (int [] lane, int used) {
			if (used < lane.length) return lane;
			return Arrays.copyOf (lane, Math.max (8, lane.length * 2));
		}
	}

	/* Open addressing long id -> int position, linear probing, load at most 1/2 (no boxing). */
	static final class LongIndex {
		private final long [] keys;
		private final int [] values;
		private final int mask;

		LongIndex (int capacity) {
			int size = Integer.highestOneBit (Math.max (2, capacity) * 2 - 1) << 1;
			keys = new long [size];
			values = new int [size];
			mask = size - 1;
		}

		int put (long key, int position) {
			int slot = slot (key);
			while (values [slot] != 0) {
				if (keys [slot] == key) return values [slot] - 1;
				slot = (slot + 1) & mask;
			}
			keys [slot] = key;
			values [slot] = position + 1;
			return -1;
		}

		int get (long key) {
			int slot = slot (key);
			while (values [slot] != 0) {
				if (keys [slot] == key) return values [slot] - 1;
				slot = (slot + 1) & mask;
			}
			return -1;
		}

		private int slot (long key) {
			long h = key * 0x9E3779B97F4A7C15L;
			return (int) (h ^ (h >>> 32)) & mask;
		}
	}
}
