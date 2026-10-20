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
 * The id-addressed operations of a model-bound {@link M3Patch.Builder} (public model API, brief rule 26), kept in
 * lanes and resolved against the model when the patch is built: per parent, the old rows of the removed ids become
 * remove runs (highest first), the final indices of the inserted ids insert runs (ascending), and the updated ids
 * update runs at their final indices with their column masks. A move within a parent is a remove and an insert of
 * one id paired as a move; a move to another parent is a reparent whose update carries {@link M3Patch#PARENT}.
 */
final class M3PatchIds {

	final M3ListModel model;
	private long [] removeIds = new long [8];
	private int removes;
	private long [] insertParents = new long [8], insertIds = new long [8];
	private int [] insertAts = new int [8];
	private int inserts;
	private long [] updateIds = new long [8], updateMasks = new long [8];
	private int updates;
	/* moves within a parent: (id, final index), paired at resolution */
	private long [] moveIds = new long [8];
	private int [] moveAts = new int [8];
	private int moves;

	M3PatchIds (M3ListModel model) {
		this.model = model;
	}

	void clear () {
		removes = inserts = updates = moves = 0;
	}

	void remove (long id) {
		if (removes == removeIds.length) removeIds = Arrays.copyOf (removeIds, removes * 2);
		removeIds [removes++] = id;
	}

	void insert (long parent, int at, long id) {
		if (inserts == insertIds.length) {
			insertParents = Arrays.copyOf (insertParents, inserts * 2);
			insertIds = Arrays.copyOf (insertIds, inserts * 2);
			insertAts = Arrays.copyOf (insertAts, inserts * 2);
		}
		insertParents [inserts] = parent;
		insertAts [inserts] = at;
		insertIds [inserts++] = id;
	}

	void update (long id, long mask) {
		if (updates == updateIds.length) {
			updateIds = Arrays.copyOf (updateIds, updates * 2);
			updateMasks = Arrays.copyOf (updateMasks, updates * 2);
		}
		updateIds [updates] = id;
		updateMasks [updates++] = mask;
	}

	void move (long id, long newParent, int at) {
		if (model.slot (id) < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		long oldParent = parentOf (id);
		remove (id);
		insert (newParent, at, id);
		if (oldParent != newParent) {
			update (id, M3Patch.PARENT);
			return;
		}
		if (moves == moveIds.length) {
			moveIds = Arrays.copyOf (moveIds, moves * 2);
			moveAts = Arrays.copyOf (moveAts, moves * 2);
		}
		moveIds [moves] = id;
		moveAts [moves++] = at;
	}

	private long parentOf (long id) {
		return model instanceof M3TreeModel tree ? tree.parent (id) : M3Patch.ROOT;
	}

	/* appends one section per parent, in the order the parents were first named */
	void resolve (M3Patch.Builder builder) {
		if (removes + inserts + updates == 0) return;
		M3Patch.LongIndex removed = new M3Patch.LongIndex (removes), inserted = new M3Patch.LongIndex (inserts);
		long [] parents = new long [removes + inserts + updates];
		int parentCount = 0;
		M3Patch.LongIndex named = new M3Patch.LongIndex (parents.length);
		long [] removeParents = new long [removes];
		int [] removeRows = new int [removes];
		for (int i = 0; i < removes; i++) {
			long id = removeIds [i];
			int row = model.indexOf (id);
			if (row < 0 || removed.put (id, i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			removeParents [i] = parentOf (id);
			removeRows [i] = row;
			if (named.put (removeParents [i], parentCount) < 0) parents [parentCount++] = removeParents [i];
		}
		for (int i = 0; i < inserts; i++) {
			long id = insertIds [i];
			if (id == M3Patch.ROOT || inserted.put (id, i) >= 0 || model.slot (id) >= 0 && removed.get (id) < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			if (insertParents [i] != M3Patch.ROOT && !(model instanceof M3TreeModel)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			if (named.put (insertParents [i], parentCount) < 0) parents [parentCount++] = insertParents [i];
		}
		long [] updateParents = new long [updates];
		for (int i = 0; i < updates; i++) {
			long id = updateIds [i];
			int insert = inserted.get (id);
			if (insert >= 0) {
				updateParents [i] = insertParents [insert];
			} else {
				if (model.slot (id) < 0 || removed.get (id) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
				updateParents [i] = parentOf (id);
			}
			if (named.put (updateParents [i], parentCount) < 0) parents [parentCount++] = updateParents [i];
		}
		for (int p = 0; p < parentCount; p++) section (builder, parents [p], removeParents, removeRows, updateParents, removed, inserted);
	}

	private void section (M3Patch.Builder builder, long parent, long [] removeParents, int [] removeRows, long [] updateParents,
			M3Patch.LongIndex removed, M3Patch.LongIndex inserted) {
		builder.parent (parent);
		/* removes: old rows, highest first, merged into runs */
		int [] rows = new int [removes];
		int n = 0;
		for (int i = 0; i < removes; i++) {
			if (removeParents [i] == parent) rows [n++] = removeRows [i];
		}
		Arrays.sort (rows, 0, n);
		for (int end = n - 1; end >= 0; ) {
			int start = end;
			while (start > 0 && rows [start - 1] == rows [start] - 1) start--;
			builder.remove (rows [start], rows [end] - rows [start] + 1);
			end = start - 1;
		}
		/* inserts: final indices ascending, consecutive indices in one run */
		long [] keys = new long [inserts];
		int m = 0;
		for (int i = 0; i < inserts; i++) {
			if (insertParents [i] == parent) keys [m++] = (long) insertAts [i] << 32 | i;
		}
		Arrays.sort (keys, 0, m);
		int [] ats = new int [m];
		for (int k = 0; k < m; k++) ats [k] = (int) (keys [k] >>> 32);
		for (int k = 0; k < m; ) {
			int start = k;
			while (k + 1 < m && ats [k + 1] == ats [k] + 1) k++;
			if (k + 1 < m && ats [k + 1] == ats [k]) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			long [] run = new long [k - start + 1];
			for (int j = start; j <= k; j++) run [j - start] = insertIds [(int) keys [j]];
			builder.insert (ats [start], run);
			k++;
		}
		/* moves within this parent */
		for (int i = 0; i < moves; i++) {
			int removal = removed.get (moveIds [i]);
			if (removeParents [removal] == parent) builder.move (removeRows [removal], moveAts [i]);
		}
		/* updates: final rows ascending, masks of one row merged, equal masks of consecutive rows in one run */
		long [] finals = new long [updates];
		int u = 0;
		for (int i = 0; i < updates; i++) {
			if (updateParents [i] != parent) continue;
			int insert = inserted.get (updateIds [i]);
			int row = insert >= 0 ? insertAts [insert] : follow (model.indexOf (updateIds [i]), rows, n, ats, m);
			finals [u++] = (long) row << 32 | i;
		}
		Arrays.sort (finals, 0, u);
		for (int k = 0; k < u; ) {
			int row = (int) (finals [k] >>> 32);
			long mask = 0;
			while (k < u && (int) (finals [k] >>> 32) == row) mask |= updateMasks [(int) finals [k++]];
			int count = 1;
			while (k < u) {
				int next = (int) (finals [k] >>> 32);
				if (next != row + count) break;
				long nextMask = 0;
				int j = k;
				while (j < u && (int) (finals [j] >>> 32) == next) nextMask |= updateMasks [(int) finals [j++]];
				if (nextMask != mask) break;
				count++;
				k = j;
			}
			builder.update (row, count, mask);
		}
	}

	/*
	 * The final index of a kept old row, in O(log n): minus the removed rows below it, plus the inserted rows that
	 * land at or before it. With the inserted final indices a[0] < a[1] < ..., insert k lands before the row exactly
	 * when a[k] - k <= the row's index after the removes, and a[k] - k never decreases, so a binary search counts them.
	 */
	private static int follow (int old, int [] removedAscending, int removedCount, int [] insertedAscending, int insertedCount) {
		int below = -Arrays.binarySearch (removedAscending, 0, removedCount, old) - 1;
		int row = old - below;
		int low = 0, high = insertedCount;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (insertedAscending [middle] - middle <= row) low = middle + 1; else high = middle;
		}
		return row + low;
	}
}
