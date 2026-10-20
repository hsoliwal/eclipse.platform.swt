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

	/** Column-mask bit of an update run: the flag lanes of the rows changed. */
	public static final long FLAGS = 1L << 63;
	/** Column-mask bit of an update run: the styles of the rows changed. */
	public static final long STYLE = 1L << 62;
	/** Column-mask bit of an update run: the heights of the rows changed. */
	public static final long EXTENT = 1L << 61;
	/** Column-mask bit of an update run: the rows came from another parent in this patch (a reparent). */
	public static final long PARENT = 1L << 60;
	/** The column mask of an update run that names no columns: everything may have changed. */
	public static final long ALL = -1L;
	/** Section kind: remove, insert, update and move runs. */
	public static final int RUNS = 0;
	/** Section kind: a permutation of the rows ({@link #reorderedRow(int)}); every row keeps its id and slot. */
	public static final int REORDER = 1;
	/** Section kind: the children are replaced by {@link #resetId(int)}; surviving ids keep their slots and flags. */
	public static final int RESET = 2;

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

		/**
		 * Permutes the rows ({@link #REORDER} sections). The default marks every row as updated.
		 *
		 * @param oldRowOfNewRow the old row of each new row
		 */
		default void reorder (int [] oldRowOfNewRow) {
			if (oldRowOfNewRow.length > 0) update (0, oldRowOfNewRow.length);
		}

		/**
		 * Replaces every row ({@link #RESET} sections). A target that cannot replace its rows keeps the default, which
		 * reports it.
		 *
		 * @param count the number of new rows
		 * @exception org.eclipse.swt.SWTError <ul>
		 *    <li>ERROR_NOT_IMPLEMENTED - by default</li>
		 * </ul>
		 */
		default void reset (int count) {
			SWT.error (SWT.ERROR_NOT_IMPLEMENTED);
		}
	}

	private static final int [] NONE = new int [0];
	private static final long [] NO_IDS = new long [0];

	private final long parent;
	private final int kind;
	private final int [] removeAt, removeCount, insertAt, insertCount, insertStart, updateAt, updateCount, moveFrom, moveTo;
	private final long [] insertedIds, updateColumns;
	/* REORDER: the old row of each new row; RESET: the new children */
	private final int [] reorder;
	private final long [] resetIds;
	private final int removed, inserted, updated;
	private final M3PatchValues values;
	private final long baseGeneration;
	/* null for a one-section patch; else every section, this patch reading as section 0 */
	private final M3Patch [] sections;

	M3Patch (long parent, int [] removeAt, int [] removeCount, int [] insertAt, int [] insertCount, long [] insertedIds,
			int [] updateAt, int [] updateCount, int [] moveFrom, int [] moveTo) {
		this (parent, RUNS, removeAt, removeCount, insertAt, insertCount, insertedIds, updateAt, updateCount, null, moveFrom,
				moveTo, null, null, null, 0, null);
	}

	private M3Patch (long parent, int kind, int [] removeAt, int [] removeCount, int [] insertAt, int [] insertCount,
			long [] insertedIds, int [] updateAt, int [] updateCount, long [] updateColumns, int [] moveFrom, int [] moveTo,
			int [] reorder, long [] resetIds, M3PatchValues values, long baseGeneration, M3Patch [] sections) {
		this.parent = parent;
		this.kind = kind;
		this.removeAt = removeAt;
		this.removeCount = removeCount;
		this.insertAt = insertAt;
		this.insertCount = insertCount;
		this.insertedIds = insertedIds;
		this.updateAt = updateAt;
		this.updateCount = updateCount;
		this.updateColumns = updateColumns;
		this.moveFrom = moveFrom;
		this.moveTo = moveTo;
		this.reorder = reorder;
		this.resetIds = resetIds;
		this.values = values;
		this.baseGeneration = baseGeneration;
		this.sections = sections;
		this.removed = sum (removeCount);
		this.inserted = sum (insertCount);
		this.updated = sum (updateCount);
		insertStart = new int [insertCount.length];
		for (int i = 1; i < insertCount.length; i++) insertStart [i] = insertStart [i - 1] + insertCount [i - 1];
	}

	/* the same section with the patch-wide parts: values, base generation, sections */
	private M3Patch with (M3PatchValues patchValues, long base, M3Patch [] all) {
		return new M3Patch (parent, kind, removeAt, removeCount, insertAt, insertCount, insertedIds, updateAt, updateCount,
				updateColumns, moveFrom, moveTo, reorder, resetIds, patchValues, base, all);
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
	 * Returns a builder bound to a model: besides the index runs it takes id-addressed operations
	 * ({@link Builder#removeId(long)}, {@link Builder#insertId(long, int, long)}, {@link Builder#updateId(long, long)},
	 * {@link Builder#moveId(long, long, int)}), resolved against the model when the patch is built; the patch then
	 * records the model's generation as its {@link #baseGeneration()}.
	 *
	 * @param model the model the patch is for
	 * @return the builder, its first section for {@link #ROOT}
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the model is null</li>
	 * </ul>
	 */
	public static Builder builder (M3ListModel model) {
		if (model == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		Builder builder = new Builder (ROOT);
		builder.byId = new M3PatchIds (model);
		return builder;
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

	/**
	 * Returns the column mask of an update run: bits 0 to 55 name columns, {@link #FLAGS}, {@link #STYLE},
	 * {@link #EXTENT} and {@link #PARENT} name row planes.
	 *
	 * @param i the run
	 * @return the mask, {@link #ALL} when the run named no columns
	 */
	public long updateColumns (int i) {
		return updateColumns == null ? ALL : updateColumns [i];
	}

	/** @return the kind of this section: {@link #RUNS}, {@link #REORDER} or {@link #RESET} */
	public int kind () {
		return kind;
	}

	/** @return the number of rows of a {@link #REORDER} section, 0 for the other kinds */
	public int reorderSize () {
		return reorder == null ? 0 : reorder.length;
	}

	/**
	 * @param newRow a row after the reorder
	 * @return the row it had before
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the section is not a {@link #REORDER} or the row is out of range</li>
	 * </ul>
	 */
	public int reorderedRow (int newRow) {
		if (reorder == null || newRow < 0 || newRow >= reorder.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		return reorder [newRow];
	}

	/** @return the number of children after a {@link #RESET} section, 0 for the other kinds */
	public int resetSize () {
		return resetIds == null ? 0 : resetIds.length;
	}

	/**
	 * @param row a child after the reset
	 * @return its id
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the section is not a {@link #RESET} or the row is out of range</li>
	 * </ul>
	 */
	public long resetId (int row) {
		if (resetIds == null || row < 0 || row >= resetIds.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		return resetIds [row];
	}

	/** @return the number of sections, one per parent, in application order */
	public int sections () {
		return sections == null ? 1 : sections.length;
	}

	/**
	 * @param index the section
	 * @return the section: a one-section patch of one parent, sharing this patch's values and base generation
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the index is out of range</li>
	 * </ul>
	 */
	public M3Patch section (int index) {
		if (index < 0 || index >= sections ()) SWT.error (SWT.ERROR_INVALID_RANGE);
		return sections == null ? this : sections [index];
	}

	/**
	 * @param id a row id
	 * @return whether the patch carries values for the row (<code>Builder.values</code>)
	 */
	public boolean hasValues (long id) {
		return values != null && values.first (id) >= 0;
	}

	/**
	 * Writes the values the patch carries for a row, in the order they were given.
	 *
	 * @param id the row id
	 * @param out the receiver
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the receiver is null</li>
	 * </ul>
	 */
	public void values (long id, M3CellWriter out) {
		if (out == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (values != null) values.replay (id, out);
	}

	/** @return the model generation a model-bound builder resolved the patch against, 0 for a free builder */
	public long baseGeneration () {
		return baseGeneration;
	}

	/** @return whether the patch changes nothing: no run in any section, no reorder and no reset */
	public boolean isEmpty () {
		if (sections != null) {
			for (M3Patch section : sections) {
				if (!section.isEmpty ()) return false;
			}
			return true;
		}
		return kind == RUNS && removed == 0 && inserted == 0 && updated == 0;
	}

	/**
	 * Applies this section to a target: removes (highest first), then inserts (lowest first), then updates; a
	 * {@link #REORDER} section calls {@link Target#reorder(int[])}, a {@link #RESET} section {@link Target#reset(int)}.
	 * A patch of several sections applies section 0: a receiver of a tree walks {@link #section(int)}.
	 *
	 * @param target the receiver
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the target is null</li>
	 * </ul>
	 */
	public void applyTo (Target target) {
		if (target == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (kind == REORDER) {
			target.reorder (reorder.clone ());
			return;
		}
		if (kind == RESET) {
			target.reset (resetIds.length);
			return;
		}
		for (int i = 0; i < removeAt.length; i++) target.remove (removeAt [i], removeCount [i]);
		for (int i = 0; i < insertAt.length; i++) target.insert (insertAt [i], insertCount [i]);
		for (int i = 0; i < updateAt.length; i++) target.update (updateAt [i], updateCount [i]);
	}

	/**
	 * Applies this section to a list of ids.
	 *
	 * @param before the ids before
	 * @return a new array of the ids after
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the array is null</li>
	 *    <li>ERROR_INVALID_RANGE - if a run does not fit the list, or a reorder is not of its length</li>
	 * </ul>
	 */
	public long [] apply (long [] before) {
		if (before == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (kind == RESET) return resetIds.clone ();
		if (kind == REORDER) {
			if (reorder.length != before.length) SWT.error (SWT.ERROR_INVALID_RANGE);
			long [] after = new long [before.length];
			for (int i = 0; i < after.length; i++) after [i] = before [reorder [i]];
			return after;
		}
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
		String body = kind == REORDER ? "reorder " + reorder.length : kind == RESET ? "reset " + resetIds.length //$NON-NLS-1$ //$NON-NLS-2$
				: "-" + removed + " +" + inserted + " ~" + updated + ", " + moveFrom.length + " moves"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		return "M3Patch [parent " + parent + ", " + body + (sections == null ? "" : ", " + sections.length + " sections") + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}

	/**
	 * Builds a patch; runs must be given in application order. A builder starts with one section, of the parent it
	 * was made for; {@link #parent(long)} starts the section of another parent. {@link #build()} empties it for the next
	 * patch.
	 *
	 * @since 3.138
	 */
	public static final class Builder {
		private final long origin;
		private long parent;
		private int kind = RUNS;
		private int removes, inserts, updates, moves;
		private int [] removeAt = NONE, removeCount = NONE, insertAt = NONE, insertCount = NONE, updateAt = NONE,
				updateCount = NONE, moveFrom = NONE, moveTo = NONE;
		private long [] updateMask = NO_IDS;
		private long [] ids = new long [0];
		private int idCount;
		private int [] reorder;
		private long [] resetIds;
		private final ArrayList<M3Patch> done = new ArrayList<> ();
		private M3PatchValues.Stage stage;
		M3PatchIds byId;

		Builder (long parent) {
			this.origin = parent;
			this.parent = parent;
		}

		/**
		 * Ends the current section and starts the section of another parent. An empty current section is
		 * replaced instead.
		 *
		 * @param parent the parent id, or {@link #ROOT}
		 * @return this builder
		 */
		public Builder parent (long parent) {
			if (!sectionEmpty ()) finish ();
			this.parent = parent;
			return this;
		}

		/**
		 * Adds a remove run, below the previous one (old indices, highest first).
		 *
		 * @param at the first old index
		 * @param count the number of rows, positive
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not below the previous remove run, or the
		 *    section is a reorder or a reset</li>
		 * </ul>
		 */
		public Builder remove (int at, int count) {
			if (kind != RUNS || at < 0 || count <= 0 || (removes > 0 && at + count > removeAt [removes - 1])) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
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
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not after the previous insert run, or the
		 *    section is a reorder or a reset</li>
		 * </ul>
		 */
		public Builder insert (int at, long... newIds) {
			if (newIds == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
			if (kind != RUNS || at < 0 || newIds.length == 0 || (inserts > 0 && at < insertAt [inserts - 1] + insertCount [inserts - 1])) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
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
		 * Adds an update run of every column, after the previous one (final indices, ascending).
		 *
		 * @param at the first final index
		 * @param count the number of rows, positive
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not after the previous update run, or the
		 *    section is a reorder or a reset</li>
		 * </ul>
		 */
		public Builder update (int at, int count) {
			return update (at, count, ALL);
		}

		/**
		 * Adds an update run of some columns, after the previous one (final indices, ascending).
		 *
		 * @param at the first final index
		 * @param count the number of rows, positive
		 * @param columns the column mask: bits 0 to 55 for columns, {@link #FLAGS}, {@link #STYLE}, {@link #EXTENT},
		 *    {@link #PARENT}; {@link #ALL} for everything
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the run is empty, negative or not after the previous update run, the mask
		 *    is 0, or the section is a reorder or a reset</li>
		 * </ul>
		 */
		public Builder update (int at, int count, long columns) {
			if (kind != RUNS || columns == 0 || at < 0 || count <= 0 || (updates > 0 && at < updateAt [updates - 1] + updateCount [updates - 1])) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			updateAt = grow (updateAt, updates);
			updateCount = grow (updateCount, updates);
			if (updates == updateMask.length) updateMask = Arrays.copyOf (updateMask, Math.max (8, updates * 2));
			updateAt [updates] = at;
			updateMask [updates] = columns;
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

		/**
		 * Makes the current section a permutation of its rows: every row keeps its id, slot and state.
		 *
		 * @param oldRowOfNewRow the old row of each new row, a permutation of <code>0 .. n-1</code>
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_NULL_ARGUMENT - if the array is null</li>
		 *    <li>ERROR_INVALID_ARGUMENT - if it is not a permutation, or the section already has content</li>
		 * </ul>
		 */
		public Builder reorder (int [] oldRowOfNewRow) {
			if (oldRowOfNewRow == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
			if (!sectionEmpty ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			BitSet seen = new BitSet (oldRowOfNewRow.length);
			for (int old : oldRowOfNewRow) {
				if (old < 0 || old >= oldRowOfNewRow.length || seen.get (old)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
				seen.set (old);
			}
			kind = REORDER;
			reorder = oldRowOfNewRow.clone ();
			return this;
		}

		/**
		 * Makes the current section a replacement of the parent's children: rows whose ids survive keep their
		 * slots and flags, the others are removed or inserted.
		 *
		 * @param newIds the ids of the children after the patch, unique
		 * @return this builder
		 *
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_NULL_ARGUMENT - if the ids are null</li>
		 *    <li>ERROR_INVALID_ARGUMENT - if an id repeats, or the section already has content</li>
		 * </ul>
		 */
		public Builder reset (long... newIds) {
			if (newIds == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
			if (!sectionEmpty ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			LongIndex seen = new LongIndex (newIds.length);
			for (int i = 0; i < newIds.length; i++) {
				if (seen.put (newIds [i], i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			}
			kind = RESET;
			resetIds = newIds.clone ();
			return this;
		}

		/**
		 * Returns the writer of the values the patch carries for a row (an inserted or updated id). The writer is
		 * reused: it writes for the id of the last call.
		 *
		 * @param id the row id
		 * @return the writer
		 */
		public M3CellWriter values (long id) {
			if (stage == null) stage = new M3PatchValues.Stage ();
			stage.id = id;
			return stage;
		}

		/**
		 * Removes a row by id (a builder bound to a model).
		 *
		 * @param id the id of a row of the model
		 * @return this builder
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the builder is not bound to a model, or no row has the id (checked when built)</li>
		 * </ul>
		 */
		public Builder removeId (long id) {
			bound ().remove (id);
			return this;
		}

		/**
		 * Inserts a row by id (a builder bound to a model).
		 *
		 * @param parent the parent id, {@link #ROOT} for a flat model
		 * @param at the row's index among the parent's children after the patch
		 * @param id the new id
		 * @return this builder
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the builder is not bound to a model, the index is negative, or (when built)
		 *    the id is present and not removed, or two inserts of a parent share an index</li>
		 * </ul>
		 */
		public Builder insertId (long parent, int at, long id) {
			if (at < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			bound ().insert (parent, at, id);
			return this;
		}

		/**
		 * Marks columns of a row as changed, by id (a builder bound to a model).
		 *
		 * @param id the id
		 * @param columns the column mask
		 * @return this builder
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the builder is not bound to a model, the mask is 0, or (when built) the
		 *    row is neither present nor inserted, or is removed</li>
		 * </ul>
		 */
		public Builder updateId (long id, long columns) {
			if (columns == 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			bound ().update (id, columns);
			return this;
		}

		/**
		 * Moves a row by id, within its parent or to another parent (a reparent, whose update carries
		 * {@link #PARENT}); the row keeps its id, slot and state.
		 *
		 * @param id the id of a row of the model
		 * @param newParent the parent after the move
		 * @param at the row's index among the new parent's children after the patch
		 * @return this builder
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if the builder is not bound to a model, the index is negative, or no row has
		 *    the id</li>
		 * </ul>
		 */
		public Builder moveId (long id, long newParent, int at) {
			if (at < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			bound ().move (id, newParent, at);
			return this;
		}

		/**
		 * Empties the builder for another patch, keeping its buffers, its model and its first parent; {@link #build()}
		 * ends with it.
		 *
		 * @return this builder
		 */
		public Builder clear () {
			resetSection ();
			done.clear ();
			parent = origin;
			if (stage != null) stage.values.clear ();
			if (byId != null) byId.clear ();
			return this;
		}

		/**
		 * Builds the patch: the explicit sections in order, then one section per parent of the id-addressed
		 * operations.
		 *
		 * @return the patch
		 * @exception IllegalArgumentException <ul>
		 *    <li>ERROR_INVALID_ARGUMENT - if two sections edit one parent, or an id-addressed operation does not fit
		 *    the model</li>
		 * </ul>
		 */
		public M3Patch build () {
			if (!sectionEmpty () || done.isEmpty ()) finish ();
			int explicit = done.size ();
			if (byId != null) {
				byId.resolve (this);
				if (!sectionEmpty ()) finish ();
			}
			if (done.size () > explicit && done.get (0).isEmpty () && explicit == 1) done.remove (0);
			LongIndex parents = new LongIndex (done.size ());
			for (int i = 0; i < done.size (); i++) {
				if (parents.put (done.get (i).parent, i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			}
			M3PatchValues frozen = stage == null || stage.values.count == 0 ? null : stage.values.freeze ();
			long base = byId == null ? 0 : byId.model.generation ();
			M3Patch [] all = new M3Patch [done.size ()];
			for (int i = 0; i < all.length; i++) all [i] = done.get (i).with (frozen, base, null);
			clear ();
			return all.length == 1 ? all [0] : all [0].with (frozen, base, all);
		}

		/* ------------------------------------------------------------------ helpers */

		private M3PatchIds bound () {
			if (byId == null) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			return byId;
		}

		private boolean sectionEmpty () {
			return kind == RUNS && removes == 0 && inserts == 0 && updates == 0;
		}

		/* ends the current section */
		void finish () {
			long [] masks = null;
			for (int i = 0; i < updates; i++) {
				if (updateMask [i] != ALL) {
					masks = Arrays.copyOf (updateMask, updates);
					break;
				}
			}
			done.add (new M3Patch (parent, kind, Arrays.copyOf (removeAt, removes), Arrays.copyOf (removeCount, removes),
					Arrays.copyOf (insertAt, inserts), Arrays.copyOf (insertCount, inserts), Arrays.copyOf (ids, idCount),
					Arrays.copyOf (updateAt, updates), Arrays.copyOf (updateCount, updates), masks, Arrays.copyOf (moveFrom, moves),
					Arrays.copyOf (moveTo, moves), reorder, resetIds, null, 0, null));
			resetSection ();
		}

		private void resetSection () {
			kind = RUNS;
			removes = inserts = updates = moves = idCount = 0;
			reorder = null;
			resetIds = null;
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
