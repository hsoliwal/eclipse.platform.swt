/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.*;

/**
 * Sparse columnar topology for materialized coordinates of a virtual Tree.
 *
 * <p>Slots are keyed by the platform item's stable SWT id. Cold logical children
 * do not require slots; only their parent's logical child count is retained.
 * This is intentionally independent of any native Tree model so native rows can
 * later become a reconstructable viewport projection.</p>
 */
final class VirtualTreeTopology {
	static final int ROOT = -1;
	private static final int ABSENT = Integer.MIN_VALUE;
	private static final int UNKNOWN_CHILD_COUNT = -1;

	private int [] parentIds = new int [4];
	private int [] childIndices = new int [4];
	private int [] childCounts = new int [4];
	private long [] stateMasks = new long [4];
	private int rootChildCount = UNKNOWN_CHILD_COUNT;
	private int materializedCount;

	VirtualTreeTopology () {
		Arrays.fill (parentIds, ABSENT);
		Arrays.fill (childIndices, -1);
		Arrays.fill (childCounts, UNKNOWN_CHILD_COUNT);
	}

	void bind (int id, int parentId, int childIndex) {
		if (id < 0) throw new IllegalArgumentException ("negative tree id");
		if (parentId < ROOT) throw new IllegalArgumentException ("invalid parent id");
		if (childIndex < 0) throw new IllegalArgumentException ("negative child index");
		ensureCapacity (id + 1);
		if (parentIds [id] == ABSENT) {
			materializedCount++;
		} else {
			int oldParent = parentIds [id];
			int oldIndex = childIndices [id];
			if (oldParent == parentId && oldIndex == childIndex) return;
		}
		parentIds [id] = parentId;
		childIndices [id] = childIndex;
	}

	void insertCoordinate (int parentId, int childIndex, int id) {
		if (childIndex < 0) throw new IllegalArgumentException ("negative child index");
		shiftSiblingIndices (parentId, childIndex, 1);
		bind (id, parentId, childIndex);
		adjustKnownChildCount (parentId, 1);
	}

	void releaseSubtree (int id) {
		if (!contains (id)) return;
		int parentId = parentIds [id];
		int removedIndex = childIndices [id];
		discardSubtree (id);
		shiftSiblingIndices (parentId, removedIndex + 1, -1);
		adjustKnownChildCount (parentId, -1);
	}

	void clear () {
		Arrays.fill (parentIds, ABSENT);
		Arrays.fill (childIndices, -1);
		Arrays.fill (childCounts, UNKNOWN_CHILD_COUNT);
		Arrays.fill (stateMasks, 0);
		rootChildCount = UNKNOWN_CHILD_COUNT;
		materializedCount = 0;
	}

	boolean contains (int id) {
		return id >= 0 && id < parentIds.length && parentIds [id] != ABSENT;
	}

	int materializedCount () {
		return materializedCount;
	}

	int parentId (int id) {
		requirePresent (id);
		return parentIds [id];
	}

	int childIndex (int id) {
		requirePresent (id);
		return childIndices [id];
	}

	void setChildCount (int parentId, int count) {
		if (count < 0) throw new IllegalArgumentException ("negative child count");
		if (parentId == ROOT) {
			rootChildCount = count;
			return;
		}
		requirePresent (parentId);
		childCounts [parentId] = count;
		long state = stateMasks [parentId] | VirtualItemState.CHILDREN_KNOWN
				| VirtualItemState.CHILDREN_COMPLETE;
		state &= ~(VirtualItemState.CHILDREN_LOADING | VirtualItemState.CHILDREN_PARTIAL);
		if (count == 0) state &= ~VirtualItemState.HAS_CHILDREN;
		else state |= VirtualItemState.HAS_CHILDREN;
		stateMasks [parentId] = state;
		pruneCoordinatesPast (parentId, count);
	}

	boolean childCountKnown (int parentId) {
		if (parentId == ROOT) return rootChildCount != UNKNOWN_CHILD_COUNT;
		return contains (parentId) && childCounts [parentId] != UNKNOWN_CHILD_COUNT;
	}

	int childCount (int parentId) {
		if (parentId == ROOT) {
			if (rootChildCount == UNKNOWN_CHILD_COUNT) throw new IllegalStateException ("root child count unknown");
			return rootChildCount;
		}
		requirePresent (parentId);
		int count = childCounts [parentId];
		if (count == UNKNOWN_CHILD_COUNT) throw new IllegalStateException ("child count unknown");
		return count;
	}

	void state (int id, long state) {
		requirePresent (id);
		stateMasks [id] = state;
	}

	long state (int id) {
		requirePresent (id);
		return stateMasks [id];
	}

	void flag (int id, long flag, boolean value) {
		requirePresent (id);
		if (value) stateMasks [id] |= flag;
		else stateMasks [id] &= ~flag;
	}

	boolean flag (int id, long flag) {
		requirePresent (id);
		return (stateMasks [id] & flag) != 0;
	}

	long visibleRowCount () {
		return visibleChildrenRowCount (ROOT);
	}

	long visibleChildrenRowCount (int parentId) {
		long rows = childCount (parentId);
		for (int id = 0; id < parentIds.length; id++) {
			if (parentIds [id] != parentId) continue;
			if ((stateMasks [id] & VirtualItemState.EXPANDED) == 0) continue;
			if (!childCountKnown (id)) continue;
			rows = Math.addExact (rows, visibleChildrenRowCount (id));
		}
		return rows;
	}

	int highestChildIndexWithSubtreeFlag (int parentId, long flag) {
		int highest = -1;
		for (int id = 0; id < parentIds.length; id++) {
			if (parentIds [id] == parentId && subtreeHasFlag (id, flag)) {
				highest = Math.max (highest, childIndices [id]);
			}
		}
		return highest;
	}

	void forgetSubtree (int id) {
		discardSubtree (id);
	}

	private boolean subtreeHasFlag (int id, long flag) {
		if (!contains (id)) return false;
		if ((stateMasks [id] & flag) != 0) return true;
		for (int child = 0; child < parentIds.length; child++) {
			if (parentIds [child] == id && subtreeHasFlag (child, flag)) return true;
		}
		return false;
	}

	private void pruneCoordinatesPast (int parentId, int count) {
		for (int id = 0; id < parentIds.length; id++) {
			if (parentIds [id] == parentId && childIndices [id] >= count) {
				discardSubtree (id);
			}
		}
	}

	private void discardSubtree (int id) {
		if (!contains (id)) return;
		for (int child = 0; child < parentIds.length; child++) {
			if (parentIds [child] == id) discardSubtree (child);
		}
		parentIds [id] = ABSENT;
		childIndices [id] = -1;
		childCounts [id] = UNKNOWN_CHILD_COUNT;
		stateMasks [id] = 0;
		materializedCount--;
	}

	private void adjustKnownChildCount (int parentId, int delta) {
		if (parentId == ROOT) {
			if (rootChildCount != UNKNOWN_CHILD_COUNT) rootChildCount = Math.max (0, rootChildCount + delta);
			return;
		}
		if (contains (parentId) && childCounts [parentId] != UNKNOWN_CHILD_COUNT) {
			childCounts [parentId] = Math.max (0, childCounts [parentId] + delta);
		}
	}

	private void shiftSiblingIndices (int parentId, int fromInclusive, int delta) {
		if (delta == 0) return;
		for (int id = 0; id < parentIds.length; id++) {
			if (parentIds [id] == parentId && childIndices [id] >= fromInclusive) {
				childIndices [id] = Math.addExact (childIndices [id], delta);
			}
		}
	}

	private void ensureCapacity (int required) {
		if (required <= parentIds.length) return;
		int next = Math.max (required, Math.max (4, parentIds.length * 3 / 2));
		int old = parentIds.length;
		parentIds = Arrays.copyOf (parentIds, next);
		childIndices = Arrays.copyOf (childIndices, next);
		childCounts = Arrays.copyOf (childCounts, next);
		stateMasks = Arrays.copyOf (stateMasks, next);
		Arrays.fill (parentIds, old, next, ABSENT);
		Arrays.fill (childIndices, old, next, -1);
		Arrays.fill (childCounts, old, next, UNKNOWN_CHILD_COUNT);
	}

	private void requirePresent (int id) {
		if (!contains (id)) throw new IllegalArgumentException ("unknown tree id " + id);
	}
}
