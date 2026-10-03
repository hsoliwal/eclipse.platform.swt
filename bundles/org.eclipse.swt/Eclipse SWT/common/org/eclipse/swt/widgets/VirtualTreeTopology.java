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
	private int [] firstChildIds = new int [4];
	private int [] nextSiblingIds = new int [4];
	private long [] stateMasks = new long [4];
	private int rootChildCount = UNKNOWN_CHILD_COUNT;
	private int rootFirstChildId = -1;
	private int materializedCount;

	VirtualTreeTopology () {
		Arrays.fill (parentIds, ABSENT);
		Arrays.fill (childIndices, -1);
		Arrays.fill (childCounts, UNKNOWN_CHILD_COUNT);
		Arrays.fill (firstChildIds, -1);
		Arrays.fill (nextSiblingIds, -1);
	}

	void bind (int id, int parentId, int childIndex) {
		if (id < 0) throw new IllegalArgumentException ("negative tree id");
		if (parentId < ROOT) throw new IllegalArgumentException ("invalid parent id");
		if (childIndex < 0) throw new IllegalArgumentException ("negative child index");
		ensureCapacity (id + 1);
		boolean absent = parentIds [id] == ABSENT;
		if (absent) {
			materializedCount++;
		} else {
			int oldParent = parentIds [id];
			int oldIndex = childIndices [id];
			if (oldParent == parentId && oldIndex == childIndex) return;
			unlink (id);
		}
		int existing = materializedChildId (parentId, childIndex);
		if (existing >= 0 && existing != id) {
			throw new IllegalStateException ("duplicate materialized tree coordinate");
		}
		parentIds [id] = parentId;
		childIndices [id] = childIndex;
		nextSiblingIds [id] = -1;
		linkSorted (id);
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
		Arrays.fill (firstChildIds, -1);
		Arrays.fill (nextSiblingIds, -1);
		Arrays.fill (stateMasks, 0);
		rootChildCount = UNKNOWN_CHILD_COUNT;
		rootFirstChildId = -1;
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

	int firstMaterializedChildId (int parentId) {
		if (parentId == ROOT) return rootFirstChildId;
		requirePresent (parentId);
		return firstChildIds [parentId];
	}

	int nextMaterializedSiblingId (int id) {
		requirePresent (id);
		return nextSiblingIds [id];
	}

	int materializedChildId (int parentId, int childIndex) {
		if (childIndex < 0) return -1;
		for (int id = parentId == ROOT ? rootFirstChildId : contains (parentId) ? firstChildIds [parentId] : -1;
				id >= 0; id = nextSiblingIds [id]) {
			int index = childIndices [id];
			if (index == childIndex) return id;
			if (index > childIndex) break;
		}
		return -1;
	}

	void setChildCount (int parentId, int count) {
		if (count < 0) throw new IllegalArgumentException ("negative child count");
		if (parentId == ROOT) {
			rootChildCount = count;
			pruneCoordinatesPast (ROOT, count);
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

	int highestChildIndexWithSubtreeFlag (int parentId, long flag) {
		int highest = -1;
		for (int id = firstMaterializedChildId (parentId); id >= 0; id = nextSiblingIds [id]) {
			if (subtreeHasFlag (id, flag)) highest = childIndices [id];
		}
		return highest;
	}

	void forgetSubtree (int id) {
		discardSubtree (id);
	}

	private boolean subtreeHasFlag (int id, long flag) {
		if (!contains (id)) return false;
		if ((stateMasks [id] & flag) != 0) return true;
		for (int child = firstChildIds [id]; child >= 0; child = nextSiblingIds [child]) {
			if (subtreeHasFlag (child, flag)) return true;
		}
		return false;
	}

	private void pruneCoordinatesPast (int parentId, int count) {
		int id = firstMaterializedChildId (parentId);
		while (id >= 0) {
			int next = nextSiblingIds [id];
			if (childIndices [id] >= count) discardSubtree (id);
			id = next;
		}
	}

	private void discardSubtree (int id) {
		if (!contains (id)) return;
		int child = firstChildIds [id];
		while (child >= 0) {
			int next = nextSiblingIds [child];
			discardSubtree (child);
			child = next;
		}
		unlink (id);
		parentIds [id] = ABSENT;
		childIndices [id] = -1;
		childCounts [id] = UNKNOWN_CHILD_COUNT;
		firstChildIds [id] = -1;
		nextSiblingIds [id] = -1;
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

	private void linkSorted (int id) {
		int parentId = parentIds [id];
		int head = parentId == ROOT ? rootFirstChildId : firstChildIds [parentId];
		if (head < 0 || childIndices [id] < childIndices [head]) {
			nextSiblingIds [id] = head;
			if (parentId == ROOT) rootFirstChildId = id;
			else firstChildIds [parentId] = id;
			return;
		}
		int previous = head;
		int current = nextSiblingIds [previous];
		while (current >= 0 && childIndices [current] < childIndices [id]) {
			previous = current;
			current = nextSiblingIds [current];
		}
		nextSiblingIds [id] = current;
		nextSiblingIds [previous] = id;
	}

	private void unlink (int id) {
		if (!contains (id)) return;
		int parentId = parentIds [id];
		int head = parentId == ROOT ? rootFirstChildId : firstChildIds [parentId];
		if (head == id) {
			if (parentId == ROOT) rootFirstChildId = nextSiblingIds [id];
			else firstChildIds [parentId] = nextSiblingIds [id];
			nextSiblingIds [id] = -1;
			return;
		}
		for (int previous = head; previous >= 0; previous = nextSiblingIds [previous]) {
			if (nextSiblingIds [previous] == id) {
				nextSiblingIds [previous] = nextSiblingIds [id];
				nextSiblingIds [id] = -1;
				return;
			}
		}
		throw new IllegalStateException ("materialized tree sibling chain is inconsistent");
	}

	private void ensureCapacity (int required) {
		if (required <= parentIds.length) return;
		int next = Math.max (required, Math.max (4, parentIds.length * 3 / 2));
		int old = parentIds.length;
		parentIds = Arrays.copyOf (parentIds, next);
		childIndices = Arrays.copyOf (childIndices, next);
		childCounts = Arrays.copyOf (childCounts, next);
		firstChildIds = Arrays.copyOf (firstChildIds, next);
		nextSiblingIds = Arrays.copyOf (nextSiblingIds, next);
		stateMasks = Arrays.copyOf (stateMasks, next);
		Arrays.fill (parentIds, old, next, ABSENT);
		Arrays.fill (childIndices, old, next, -1);
		Arrays.fill (childCounts, old, next, UNKNOWN_CHILD_COUNT);
		Arrays.fill (firstChildIds, old, next, -1);
		Arrays.fill (nextSiblingIds, old, next, -1);
	}

	private void requirePresent (int id) {
		if (!contains (id)) throw new IllegalArgumentException ("unknown tree id " + id);
	}
}
