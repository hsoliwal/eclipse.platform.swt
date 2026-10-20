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
 * com.m3.collections.M3TreeTopology (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 553eefd41d9961d303c41e0e4e181712e64a1e45,
 * VirtualTreeTopology.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.*;

/**
 * Sparse columnar topology for materialized coordinates of a virtual Tree.
 *
 * <p>Slots are keyed by the platform item's stable SWT id. Cold logical children
 * do not require slots; only their parent's logical child count is retained.
 * This is intentionally independent of any native Tree model so native rows can
 * later become a reconstructable viewport projection.</p>
 *
 * <p>Structural edits arrive as patch runs ({@link #apply}, {@link #insertCoordinates},
 * {@link #removeCoordinates}): O(log k + discarded) per run plus one refresh of the
 * ancestors, through the lazy shifts of the {@link M3OrderStatisticForest} sibling
 * index (brief rule 24). Threads: not thread-safe and holds no lock; confined to the
 * thread that applies the model's patches.</p>
 */
public final class M3TreeTopology {
	static final int ROOT = -1;
	private static final int ABSENT = Integer.MIN_VALUE;
	private static final int UNKNOWN_CHILD_COUNT = -1;
	/* Up to this sibling-index height (at most 2^h - 1 materialized siblings) the
	 * queries walk the sibling chain, which measured faster than a descent. */
	private static final int LINEAR_HEIGHT = 4;

	private int [] parentIds = new int [4];
	private int [] childIndices = new int [4];
	private int [] childCounts = new int [4];
	private int [] firstChildIds = new int [4];
	private int [] nextSiblingIds = new int [4];
	private long [] stateMasks = new long [4];
	private long [] visibleExtraRows = new long [4];
	private long [] childVisibleExtraSums = new long [4];
	/* Per-parent order-statistic index over the materialized children, keyed by
	 * the childIndices lane and weighted by the visibleExtraRows lane. */
	private final M3OrderStatisticForest siblings = new M3OrderStatisticForest ();
	private long seekBefore;
	private int rootChildCount = UNKNOWN_CHILD_COUNT;
	private int rootFirstChildId = -1;
	private long rootVisibleExtraRows;
	private int materializedCount;
	private long generation;
	/* Parent-owned presentation flags have a separate epoch: checkbox/cache
	 * changes must not force a native tree-model rebuild. */
	private long presentationGeneration;
	/* reusable buffers: ids of a range removal, (id, new index) pairs of moved children */
	private int [] scratch = new int [16];
	private int [] parkedIds = new int [0];
	private java.util.function.IntConsumer discardListener;

	public M3TreeTopology () {
		Arrays.fill (parentIds, ABSENT);
		Arrays.fill (childIndices, -1);
		Arrays.fill (childCounts, UNKNOWN_CHILD_COUNT);
		Arrays.fill (firstChildIds, -1);
		Arrays.fill (nextSiblingIds, -1);
		siblings.ensureCapacity (parentIds.length);
		siblings.lanes (childIndices, visibleExtraRows);
	}

	public void bind (int id, int parentId, int childIndex) {
        if (id < 0) {
            throw new IllegalArgumentException("negative tree id");
        }
        if (parentId < ROOT) {
            throw new IllegalArgumentException("invalid parent id");
        }
        if (childIndex < 0) {
            throw new IllegalArgumentException("negative child index");
        }
        if (parentId != ROOT) {
            requirePresent(parentId);
        }
		int existing = materializedChildId (parentId, childIndex);
		if (existing >= 0 && existing != id) {
			throw new IllegalStateException ("duplicate materialized tree coordinate");
		}
		if (contains (id)) {
			for (int ancestor = parentId; ancestor != ROOT; ancestor = parentIds [ancestor]) {
                if (ancestor == id) {
                    throw new IllegalArgumentException("cyclic tree parent");
                }
			}
		}
		ensureCapacity (id + 1);
		boolean absent = parentIds [id] == ABSENT;
		long visibleContribution = 0;
		if (absent) {
			materializedCount++;
		} else {
			int oldParent = parentIds [id];
			int oldIndex = siblings.key (id);
            if (oldParent == parentId && oldIndex == childIndex) {
                return;
            }
			visibleContribution = visibleExtraRows [id];
            if (visibleContribution != 0) {
                propagateVisibleContribution(oldParent, -visibleContribution);
            }
			unlink (id);
		}
		parentIds [id] = parentId;
		childIndices [id] = childIndex;
		nextSiblingIds [id] = -1;
		linkSorted (id);
		generation++;
        if (visibleContribution != 0) {
            propagateVisibleContribution(parentId, visibleContribution);
        }
	}

	public void insertCoordinate (int parentId, int childIndex, int id) {
        if (childIndex < 0) {
            throw new IllegalArgumentException("negative child index");
        }
		shiftSiblingIndices (parentId, childIndex, 1);
		bind (id, parentId, childIndex);
		adjustKnownChildCount (parentId, 1);
	}

	public void removeCoordinate (int parentId, int childIndex) {
		if (childIndex < 0) throw new IllegalArgumentException ("negative child index");
		if (parentId != ROOT) requirePresent (parentId);
		int count = childCount (parentId);
		if (childIndex >= count) throw new IndexOutOfBoundsException (childIndex);
		int id = materializedChildId (parentId, childIndex);
		if (id >= 0) discardSubtree (id);
		shiftSiblingIndices (parentId, childIndex + 1, -1);
		adjustKnownChildCount (parentId, -1);
	}

	/**
	 * Removes the {@code count} child coordinates of {@code parentId} at {@code childIndex}: the materialized children
	 * among them are discarded with their subtrees, later siblings move down and the known child count drops.
	 * O(log k + discarded) plus one refresh of the ancestors, whatever the number of later siblings (brief rule 24).
	 */
	public void removeCoordinates (int parentId, int childIndex, int count) {
		if (childIndex < 0 || count < 0) throw new IllegalArgumentException ("negative child range");
		if (count == 0) return;
		if (parentId != ROOT) requirePresent (parentId);
		if (childIndex > childCount (parentId) - count) throw new IndexOutOfBoundsException (childIndex);
		int end = childIndex + count;
		discardRange (parentId, childIndex, end);
		shiftSiblingIndices (parentId, end, -count);
		adjustKnownChildCount (parentId, -count);
	}

	/**
	 * Opens {@code count} cold child coordinates of {@code parentId} at {@code childIndex}: later siblings move up and
	 * the known child count grows. O(log k) plus one refresh of the ancestors.
	 */
	public void insertCoordinates (int parentId, int childIndex, int count) {
		if (childIndex < 0 || count < 0) throw new IllegalArgumentException ("negative child range");
		if (count == 0) return;
		if (parentId != ROOT) requirePresent (parentId);
		if (childIndex > childCount (parentId)) throw new IndexOutOfBoundsException (childIndex);
		shiftSiblingIndices (parentId, childIndex, count);
		adjustKnownChildCount (parentId, count);
	}

	/**
	 * Applies a patch of the child list of {@code parentId} (old snapshot to new): remove runs, then insert runs, then
	 * the moved children that were materialized go back in at their new index with their subtrees and expansion.
	 * O(runs log k + moves log k + discarded); the only structural update path of the children (brief rule 24).
	 */
	public void apply (int parentId, M3Patch patch) {
		if (parentId != ROOT) requirePresent (parentId);
		int moves = patch.moveCount (), parked = 0;
		if (moves > 0 && parkedIds.length < moves * 2) parkedIds = new int [moves * 2];
		for (int i = 0; i < moves; i++) {
			int id = materializedChildId (parentId, patch.moveFrom (i));
			if (id < 0) continue;
			long contribution = visibleExtraRows [id];
			if (contribution != 0) propagateVisibleContribution (parentId, -contribution);
			unlink (id);
			parkedIds [parked * 2] = id;
			parkedIds [parked * 2 + 1] = patch.moveTo (i);
			parked++;
		}
		for (int i = 0; i < patch.removeRuns (); i++) removeCoordinates (parentId, patch.removeAt (i), patch.removeCount (i));
		for (int i = 0; i < patch.insertRuns (); i++) insertCoordinates (parentId, patch.insertAt (i), patch.insertCount (i));
		for (int i = 0; i < parked; i++) {
			int id = parkedIds [i * 2];
			childIndices [id] = parkedIds [i * 2 + 1];
			nextSiblingIds [id] = -1;
			linkSorted (id);
			generation++;
			long contribution = visibleExtraRows [id];
			if (contribution != 0) propagateVisibleContribution (parentId, contribution);
		}
	}

	/** Receives every id the topology discards (once each), so an owner can drop what it keeps per id. */
	public void onDiscard (java.util.function.IntConsumer listener) {
		discardListener = listener;
	}

	/** Nodes visited by the sibling index (the cost counter of brief rule 24). */
	public long indexWork () {
		return siblings.work ();
	}

	public void releaseSubtree (int id) {
        if (!contains(id)) {
            return;
        }
		int parentId = parentIds [id];
		int removedIndex = siblings.key (id);
		discardSubtree (id);
		shiftSiblingIndices (parentId, removedIndex + 1, -1);
		adjustKnownChildCount (parentId, -1);
	}

	public void clear () {
		Arrays.fill (parentIds, ABSENT);
		Arrays.fill (childIndices, -1);
		Arrays.fill (childCounts, UNKNOWN_CHILD_COUNT);
		Arrays.fill (firstChildIds, -1);
		Arrays.fill (nextSiblingIds, -1);
		Arrays.fill (stateMasks, 0);
		Arrays.fill (visibleExtraRows, 0);
		Arrays.fill (childVisibleExtraSums, 0);
		rootChildCount = UNKNOWN_CHILD_COUNT;
		rootFirstChildId = -1;
		rootVisibleExtraRows = 0;
		siblings.clear ();
		materializedCount = 0;
		presentationGeneration = Math.incrementExact (presentationGeneration);
		generation++;
	}

	/** Revision of coordinates, child counts and expansion; presentation flags are independent. */
	public long generation () {
		return generation;
	}

	/** Epoch of parent-owned flags, independent of native hierarchy geometry. */
	public long presentationGeneration () {
		return presentationGeneration;
	}

	public boolean contains (int id) {
		return id >= 0 && id < parentIds.length && parentIds [id] != ABSENT;
	}

	public int idCapacity () {
		return parentIds.length;
	}

	public int materializedCount () {
		return materializedCount;
	}

	public int parentId (int id) {
		requirePresent (id);
		return parentIds [id];
	}

	public int childIndex (int id) {
		requirePresent (id);
		return siblings.key (id);
	}

	public int firstMaterializedChildId (int parentId) {
        if (parentId == ROOT) {
            return rootFirstChildId;
        }
		requirePresent (parentId);
		return firstChildIds [parentId];
	}

	public int nextMaterializedSiblingId (int id) {
		requirePresent (id);
		return nextSiblingIds [id];
	}

	public int materializedChildId (int parentId, int childIndex) {
		if (childIndex < 0 || parentId != ROOT && !contains (parentId)) {
			return -1;
		}
		if (siblings.treeHeight (parentId) > LINEAR_HEIGHT) {
			return siblings.find (parentId, childIndex);
		}
		for (int id = firstChild (parentId), key; id >= 0 && (key = siblings.key (id)) <= childIndex; id = nextSiblingIds [id]) {
			if (key == childIndex) return id;
		}
		return -1;
	}

	/**
	 * Visible extra rows of the materialized children of {@code parentId} whose
	 * child index is below {@code childIndex}: the rows their expanded subtrees
	 * add in front of that coordinate. O(log k) for k materialized siblings.
	 */
	public long visibleExtraRowsBefore (int parentId, int childIndex) {
		if (parentId != ROOT) requirePresent (parentId);
		if (siblings.treeHeight (parentId) > LINEAR_HEIGHT) {
			return siblings.prefix (parentId, childIndex);
		}
		long result = 0;
		for (int id = firstChild (parentId); id >= 0 && siblings.key (id) < childIndex; id = nextSiblingIds [id]) {
			result += visibleExtraRows [id];
		}
		return result;
	}

	/** Rows that the expanded subtree of {@code id} adds below it; 0 when collapsed. */
	public long visibleExtraRows (int id) {
		requirePresent (id);
		return visibleExtraRows [id];
	}

	/**
	 * Returns the last materialized child c of {@code parentId} with child index
	 * below {@code limit} whose row offset among its siblings, its index plus
	 * {@link #visibleExtraRowsBefore(int, int)}, is at most {@code row}; or -1.
	 * {@link #seekExtraRowsBefore()} then answers the extra rows in front of c.
	 */
	public int seekMaterializedChild (int parentId, long row, int limit) {
		if (parentId != ROOT) requirePresent (parentId);
		if (siblings.treeHeight (parentId) > LINEAR_HEIGHT) {
			int found = siblings.seek (parentId, row, limit);
			seekBefore = siblings.seekBefore ();
			return found;
		}
		int found = -1;
		long before = 0;
		seekBefore = 0;
		for (int id = firstChild (parentId); id >= 0; id = nextSiblingIds [id]) {
			int index = siblings.key (id);
			if (index >= limit || index + before > row) break;
			found = id;
			seekBefore = before;
			before += visibleExtraRows [id];
		}
		return found;
	}

	/** Extra rows in front of the child returned by the last seek. */
	public long seekExtraRowsBefore () {
		return seekBefore;
	}

	private int firstChild (int parentId) {
		return parentId == ROOT ? rootFirstChildId : firstChildIds [parentId];
	}

	public void setChildCount (int parentId, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("negative child count");
        }
		if (parentId == ROOT) {
			pruneCoordinatesPast (ROOT, count);
			if (rootChildCount != count) generation++;
			rootChildCount = count;
			return;
		}
		requirePresent (parentId);
		pruneCoordinatesPast (parentId, count);
		if (childCounts [parentId] != count) generation++;
		childCounts [parentId] = count;
		long state = stateMasks [parentId] | M3ItemState.CHILDREN_KNOWN
				| M3ItemState.CHILDREN_COMPLETE;
		state &= ~(M3ItemState.CHILDREN_LOADING | M3ItemState.CHILDREN_PARTIAL);
        if (count == 0) {
            state &= ~M3ItemState.HAS_CHILDREN;
        } else {
            state |= M3ItemState.HAS_CHILDREN;
        }
		stateMasks [parentId] = state;
		refreshVisibleExtra (parentId);
	}

	public boolean childCountKnown (int parentId) {
        if (parentId == ROOT) {
            return rootChildCount != UNKNOWN_CHILD_COUNT;
        }
		return contains (parentId) && childCounts [parentId] != UNKNOWN_CHILD_COUNT;
	}

	public int childCount (int parentId) {
		if (parentId == ROOT) {
            if (rootChildCount == UNKNOWN_CHILD_COUNT) {
                throw new IllegalStateException("root child count unknown");
            }
			return rootChildCount;
		}
		requirePresent (parentId);
		int count = childCounts [parentId];
        if (count == UNKNOWN_CHILD_COUNT) {
            throw new IllegalStateException("child count unknown");
        }
		return count;
	}

	/** Primitive, rebuildable snapshot for a native logical GtkTreeModel. */
	public int [] nativeModelSnapshot () {
		int capacity = parentIds.length;
		int [] snapshot = new int [Math.addExact (2, Math.multiplyExact (capacity, 3))];
		snapshot [0] = capacity;
		snapshot [1] = rootChildCount == UNKNOWN_CHILD_COUNT ? 0 : rootChildCount;
		System.arraycopy (parentIds, 0, snapshot, 2, capacity);
		for (int id = 0; id < capacity; id++) {
			snapshot [2 + capacity + id] = parentIds [id] == ABSENT ? childIndices [id] : siblings.key (id);
		}
		System.arraycopy (childCounts, 0, snapshot, 2 + capacity * 2, capacity);
		return snapshot;
	}

	public void state (int id, long state) {
		requirePresent (id);
		long before = stateMasks [id];
		if (before == state) return;
		boolean expansionChanged = ((before ^ state) & M3ItemState.EXPANDED) != 0;
		long nextPresentation = Math.incrementExact (presentationGeneration);
		stateMasks [id] = state;
		presentationGeneration = nextPresentation;
        if (expansionChanged) {
            generation++;
            refreshVisibleExtra(id);
        }
	}

	public long state (int id) {
		requirePresent (id);
		return stateMasks [id];
	}

	public void flag (int id, long flag, boolean value) {
		requirePresent (id);
		boolean old = (stateMasks [id] & flag) != 0;
        if (old == value) {
            return;
        }
        long nextPresentation = Math.incrementExact (presentationGeneration);
        if (value) {
            stateMasks [id] |= flag;
        } else {
            stateMasks [id] &= ~flag;
        }
        presentationGeneration = nextPresentation;
        if ((flag & M3ItemState.EXPANDED) != 0) {
            generation++;
            refreshVisibleExtra(id);
        }
	}

	public boolean flag (int id, long flag) {
		requirePresent (id);
		return (stateMasks [id] & flag) != 0;
	}

	public long visibleRowCount () {
		return Math.addExact ((long)childCount (ROOT), rootVisibleExtraRows);
	}

	public long visibleChildrenRowCount (int parentId) {
		long extra = parentId == ROOT ? rootVisibleExtraRows : childVisibleExtraSums [parentId];
		return Math.addExact ((long)childCount (parentId), extra);
	}

	public int highestChildIndexWithSubtreeFlag (int parentId, long flag) {
		int highest = -1;
		for (int id = firstMaterializedChildId (parentId); id >= 0; id = nextSiblingIds [id]) {
            if (subtreeHasFlag(id, flag)) {
                highest = siblings.key (id);
            }
		}
		return highest;
	}

	public void forgetSubtree (int id) {
		discardSubtree (id);
	}

	private boolean subtreeHasFlag (int id, long flag) {
		if (!contains (id)) {
			return false;
		}
		int current = id;
		while (true) {
			if ((stateMasks [current] & flag) != 0) {
				return true;
			}
			int child = firstChildIds [current];
			if (child >= 0) {
				current = child;
				continue;
			}
			/* Reuse parent links, but never escape the queried subtree. */
			while (current != id && nextSiblingIds [current] < 0) {
				current = parentIds [current];
			}
			if (current == id) {
				return false;
			}
			current = nextSiblingIds [current];
		}
	}

	private void pruneCoordinatesPast (int parentId, int count) {
		discardRange (parentId, count, Integer.MAX_VALUE);
		int last = siblings.last (parentId);
		if (last >= 0 && siblings.key (last) == Integer.MAX_VALUE) {
			discardSubtree (last);
		}
	}

	/*
	 * Discards the materialized children of parentId with a coordinate in [from, to) and their subtrees: one range
	 * removal from the sibling index (O(log k + removed)), one splice of the sibling chain, one ancestor refresh.
	 */
	private void discardRange (int parentId, int from, int to) {
		int removed = siblings.countRange (parentId, from, to);
		if (removed == 0) return;
		int previous = siblings.lower (parentId, from);
		if (scratch.length < removed) scratch = new int [M3LaneGrowth.capacity (scratch.length, removed, 4)];
		int [] ids = scratch;
		siblings.removeRange (parentId, from, to, ids);
		int next = nextSiblingIds [ids [removed - 1]];
		if (previous >= 0) nextSiblingIds [previous] = next;
		else if (parentId == ROOT) rootFirstChildId = next;
		else firstChildIds [parentId] = next;
		long contribution = 0;
		for (int i = 0; i < removed; i++) contribution = Math.addExact (contribution, visibleExtraRows [ids [i]]);
		if (contribution != 0) propagateVisibleContribution (parentId, -contribution);
		for (int i = 0; i < removed; i++) {
			int id = ids [i];
			nextSiblingIds [id] = -1;
			discardSubtreeDetached (id, false);
		}
	}

	private void discardSubtree (int id) {
        if (!contains(id)) {
            return;
        }
		int parentId = parentIds [id];
		long visibleContribution = visibleExtraRows [id];
        if (visibleContribution != 0) {
            propagateVisibleContribution(parentId, -visibleContribution);
        }
		discardSubtreeDetached (id);
	}

	private void discardSubtreeDetached (int id) {
		discardSubtreeDetached (id, true);
	}

	/* topLinked: whether id is still in its parent's sibling index and chain (a range removal has taken it out) */
	private void discardSubtreeDetached (int id, boolean topLinked) {
		if (!contains (id)) {
			return;
		}
		int current = id;
		while (true) {
			int child = firstChildIds [current];
			if (child >= 0) {
				current = child;
				continue;
			}
			/* Delete leaves before their parents. Advancing the parent's first-child
			 * lane supplies the next postorder step without recursion, an auxiliary
			 * stack, or per-node temporary objects. Below id every sibling index is
			 * dropped whole, so a descendant costs O(1), not an index removal. */
			int parentId = parentIds [current];
			if (current != id) {
				int next = nextSiblingIds [current];
				firstChildIds [parentId] = next;
				siblings.forget (current);
				if (next < 0) siblings.dropTree (parentId);
			} else if (topLinked) {
				unlink (current);
			}
			if (discardListener != null) discardListener.accept (current);
			parentIds [current] = ABSENT;
			childIndices [current] = -1;
			childCounts [current] = UNKNOWN_CHILD_COUNT;
			firstChildIds [current] = -1;
			nextSiblingIds [current] = -1;
			stateMasks [current] = 0;
			visibleExtraRows [current] = 0;
			childVisibleExtraSums [current] = 0;
			materializedCount--;
			generation++;
			if (current == id) {
				return;
			}
			current = parentId;
		}
	}

	private void adjustKnownChildCount (int parentId, int delta) {
		if (parentId == ROOT) {
            if (rootChildCount != UNKNOWN_CHILD_COUNT) {
                rootChildCount = Math.max(0, rootChildCount + delta);
                generation++;
            }
			return;
		}
		if (contains (parentId) && childCounts [parentId] != UNKNOWN_CHILD_COUNT) {
			childCounts [parentId] = Math.max (0, childCounts [parentId] + delta);
			generation++;
            if (childCounts [parentId] == 0) {
                stateMasks [parentId] &= ~M3ItemState.HAS_CHILDREN;
            } else {
                stateMasks [parentId] |= M3ItemState.HAS_CHILDREN;
            }
			refreshVisibleExtra (parentId);
		}
	}

	private void refreshVisibleExtra (int id) {
		requirePresent (id);
		long next = 0;
		if ((stateMasks [id] & M3ItemState.EXPANDED) != 0
				&& childCounts [id] != UNKNOWN_CHILD_COUNT) {
			next = Math.addExact ((long)childCounts [id], childVisibleExtraSums [id]);
		}
		long delta = Math.subtractExact (next, visibleExtraRows [id]);
        if (delta == 0) {
            return;
        }
		visibleExtraRows [id] = next;
		siblings.adjust (parentIds [id], id, delta);
		propagateVisibleContribution (parentIds [id], delta);
	}

	private void propagateVisibleContribution (int parentId, long delta) {
        if (delta == 0) {
            return;
        }
		while (parentId != ROOT) {
			requirePresent (parentId);
			childVisibleExtraSums [parentId] = Math.addExact (childVisibleExtraSums [parentId], delta);
            if ((stateMasks [parentId] & M3ItemState.EXPANDED) == 0
                    || childCounts [parentId] == UNKNOWN_CHILD_COUNT) {
                return;
            }
			visibleExtraRows [parentId] = Math.addExact (visibleExtraRows [parentId], delta);
			siblings.adjust (parentIds [parentId], parentId, delta);
			parentId = parentIds [parentId];
		}
		rootVisibleExtraRows = Math.addExact (rootVisibleExtraRows, delta);
	}

	private void shiftSiblingIndices (int parentId, int fromInclusive, int delta) {
        if (delta == 0) {
            return;
        }
		if (parentId != ROOT && !contains (parentId)) {
			legacyShift (parentId, fromInclusive, delta);
			return;
		}
		int first = siblings.ceiling (parentId, fromInclusive);
		if (first < 0) return;
		long edge = delta > 0 ? (long) siblings.key (siblings.last (parentId)) + delta : (long) siblings.key (first) + delta;
		if (edge < Integer.MIN_VALUE || edge > Integer.MAX_VALUE) {
			legacyShift (parentId, fromInclusive, delta);
			return;
		}
		// One lazy shift of the sibling index: O(log k) whatever the number of later siblings (brief rule 24).
		siblings.shift (parentId, fromInclusive, delta);
		generation++;
	}

	/* Overflow must retain the original id-ordered partial updates, so it keeps the original id-order loop. */
	private void legacyShift (int parentId, int fromInclusive, int delta) {
		if (parentId == ROOT || contains (parentId)) siblings.flush (parentId);
		boolean changed = false;
		for (int id = 0; id < parentIds.length; id++) {
			if (parentIds [id] == parentId && childIndices [id] >= fromInclusive) {
				changed = shiftSiblingIndex (id, delta, changed);
			}
		}
	}

	private boolean shiftSiblingIndex (int id, int delta, boolean changed) {
		childIndices [id] = Math.addExact (childIndices [id], delta);
		if (!changed) generation++;
		return true;
	}

	/* The sibling index supplies the predecessor in O(log k); the sibling chain
	 * stays for in-order iteration. */
	private void linkSorted (int id) {
		int parentId = parentIds [id];
		int previous = siblings.lower (parentId, childIndices [id]);
		siblings.insert (parentId, id);
		if (previous < 0) {
			nextSiblingIds [id] = firstChild (parentId);
			if (parentId == ROOT) {
				rootFirstChildId = id;
			} else {
				firstChildIds [parentId] = id;
			}
			return;
		}
		nextSiblingIds [id] = nextSiblingIds [previous];
		nextSiblingIds [previous] = id;
	}

	private void unlink (int id) {
        if (!contains(id)) {
            return;
        }
		int parentId = parentIds [id];
		int previous = siblings.lower (parentId, siblings.key (id));
		if (previous < 0 ? firstChild (parentId) != id : nextSiblingIds [previous] != id) {
			throw new IllegalStateException ("materialized tree sibling chain is inconsistent");
		}
		siblings.remove (parentId, id);
		if (previous >= 0) {
			nextSiblingIds [previous] = nextSiblingIds [id];
		} else if (parentId == ROOT) {
			rootFirstChildId = nextSiblingIds [id];
		} else {
			firstChildIds [parentId] = nextSiblingIds [id];
		}
		nextSiblingIds [id] = -1;
	}

	private void ensureCapacity (int required) {
        if (required <= parentIds.length) {
            return;
        }
		int next = Math.max (required, Math.max (4, parentIds.length * 3 / 2));
		int old = parentIds.length;
		parentIds = Arrays.copyOf (parentIds, next);
		childIndices = Arrays.copyOf (childIndices, next);
		childCounts = Arrays.copyOf (childCounts, next);
		firstChildIds = Arrays.copyOf (firstChildIds, next);
		nextSiblingIds = Arrays.copyOf (nextSiblingIds, next);
		stateMasks = Arrays.copyOf (stateMasks, next);
		visibleExtraRows = Arrays.copyOf (visibleExtraRows, next);
		childVisibleExtraSums = Arrays.copyOf (childVisibleExtraSums, next);
		Arrays.fill (parentIds, old, next, ABSENT);
		Arrays.fill (childIndices, old, next, -1);
		Arrays.fill (childCounts, old, next, UNKNOWN_CHILD_COUNT);
		Arrays.fill (firstChildIds, old, next, -1);
		Arrays.fill (nextSiblingIds, old, next, -1);
		siblings.ensureCapacity (next);
		siblings.lanes (childIndices, visibleExtraRows);
	}

	private void requirePresent (int id) {
        if (!contains(id)) {
            throw new IllegalArgumentException("unknown tree id " + id);
        }
	}
}
