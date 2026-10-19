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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3Patch (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;

/**
 * The edit between two keyed snapshots, as produced by {@link M3ListDiff#diff(long[], long[])}: remove runs, insert
 * runs and the moves among them, all in packed {@code int} lanes (no entry objects).
 * <p>Application order (see {@link #applyTo(Target)}): first the remove runs, highest index first, so every index
 * refers to the old snapshot; then the insert runs, lowest index first, so every index refers to the new snapshot.
 * A moved key appears once as removed (at its old index) and once as inserted (at its new index); {@link #moveCount()}
 * and {@link #moveFrom(int)} / {@link #moveTo(int)} pair them for callers that animate moves. Keys that stay put
 * (the longest run of keys whose relative order is unchanged) are never touched.</p>
 */
public final class M3Patch {
	/** Receiver of a patch, for example a list or a native row host. */
	public interface Target {
		/** Removes {@code count} entries at {@code index}. */
		void remove (int index, int count);

		/** Inserts {@code count} entries at {@code index}: the new snapshot's entries {@code index .. index + count - 1}. */
		void insert (int index, int count);
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
		this.removed = Arrays.stream (removeCount).sum ();
		this.inserted = Arrays.stream (insertCount).sum ();
		this.kept = kept;
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
