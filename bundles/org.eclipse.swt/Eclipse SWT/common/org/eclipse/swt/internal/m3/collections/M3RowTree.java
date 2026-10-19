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
 * com.m3.collections.M3RowTree (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/nebula 79fdab533b25bd228e96a70feed55c905ab9aefb,
 * GridRowTree.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 * The AVL lanes (rebalance, rotations, successor detach) are adapted from
 * hsoliwal/eclipse.platform.swt VirtualTreeSiblingIndex.java (PR #197,
 * m3/viewport-integration-20261016 cbc458de66, EPL-2.0), itself adapted from
 * hsoliwal/com.synexia synexia-primitives LongOrderStatisticMultiset.java
 * (b1b665299048, Apache-2.0). See docs/m3/grid-viewport/FOSS_REUSE_DECISION.tsv.
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The item list of a Grid in the viewport mode: a height-balanced tree ordered by row, whose nodes
 * are lane slots (one per item) and whose subtrees count their items, their visible rows and the
 * pixel extent of those rows. Inserting or removing a row anywhere, finding the row of an item,
 * the item of a row, and every rank, select and pixel prefix question of {@link M3RowIndex} are
 * O(log n); nothing is rebuilt and no other item is touched, so no structural change costs O(n)
 * on the UI thread. An item's extent is its height plus the one-pixel line when it is visible, 0
 * when it is hidden; {@link #update(M3RowTreeItem)} records a change of either.
 */
public final class M3RowTree extends AbstractList<M3RowTreeItem> implements M3RowIndex {
	private static final int NONE = -1;
	/* bytes per slot over the lanes: left, right, up, size, visible, weight (int), sum (long), height (byte), item */
	private static final int SLOT_BYTES = 45;

	private int root = NONE;
	private int[] left = new int[0];
	private int[] right = new int[0];
	private int[] up = new int[0];
	private byte[] heights = new byte[0];
	private int[] sizes = new int[0];
	private int[] visibles = new int[0];
	private int[] weights = new int[0];
	private long[] sums = new long[0];
	private M3RowTreeItem[] items = new M3RowTreeItem[0];
	private int[] free = new int[0];
	private int freeCount;
	private int nextSlot;

	public M3RowTree () {
	}

	@Override
	public int size() {
		return size(root);
	}

	@Override
	public M3RowTreeItem get(final int index) {
		checkIndex(index, size());
		return items[slotAt(index)];
	}

	@Override
	public void add(final int index, final M3RowTreeItem item) {
		if (index < 0 || index > size()) {
			throw new IndexOutOfBoundsException("index " + index + ", size " + size());
		}
		final int slot = allocate();
		items[slot] = item;
		weights[slot] = extentOf(item);
		root = insertAt(root, index, slot);
		up[root] = NONE;
		item.enteredRowTree(slot);
		modCount++;
	}

	@Override
	public M3RowTreeItem remove(final int index) {
		checkIndex(index, size());
		final int slot = slotAt(index);
		final M3RowTreeItem item = items[slot];
		root = removeAt(root, index);
		if (root != NONE) {
			up[root] = NONE;
		}
		release(slot);
		item.leftRowTree(index);
		modCount++;
		return item;
	}

	@Override
	public boolean remove(final Object o) {
		final int index = indexOf(o);
		if (index < 0) {
			return false;
		}
		remove(index);
		return true;
	}

	@Override
	public M3RowTreeItem set(final int index, final M3RowTreeItem item) {
		checkIndex(index, size());
		final int slot = slotAt(index);
		final M3RowTreeItem old = items[slot];
		old.leftRowTree(index);
		items[slot] = item;
		item.enteredRowTree(slot);
		reweigh(slot, extentOf(item));
		return old;
	}

	@Override
	public int indexOf(final Object o) {
		final int slot = slotOf(o);
		return slot < 0 ? -1 : rowOfSlot(slot);
	}

	@Override
	public int lastIndexOf(final Object o) {
		// an item is in the list once at most
		return indexOf(o);
	}

	@Override
	public boolean contains(final Object o) {
		return slotOf(o) >= 0;
	}

	@Override
	public void clear() {
		int row = 0;
		for (int slot = first(root); slot != NONE; slot = successor(slot)) {
			items[slot].leftRowTree(row++);
		}
		root = NONE;
		Arrays.fill(items, 0, nextSlot, null);
		nextSlot = 0;
		freeCount = 0;
		modCount++;
	}

	@Override
	public Iterator<M3RowTreeItem> iterator() {
		return new Iterator<M3RowTreeItem>() {
			private int next = first(root);
			private final int expectedModCount = modCount;

			@Override
			public boolean hasNext() {
				return next != NONE;
			}

			@Override
			public M3RowTreeItem next() {
				if (modCount != expectedModCount) {
					throw new ConcurrentModificationException();
				}
				if (next == NONE) {
					throw new NoSuchElementException();
				}
				final M3RowTreeItem item = items[next];
				next = successor(next);
				return item;
			}
		};
	}

	/**
	 * @param selected items of this list
	 * @return their rows, in their order: O(k log n) for few items, one O(n) walk for many
	 */
	public int[] rowsOf(final java.util.List<M3RowTreeItem> selected) {
		final int[] rows = new int[selected.size()];
		int i = 0;
		if ((long) selected.size() * 16 < size()) {
			for (final M3RowTreeItem item : selected) {
				final int slot = slotOf(item);
				rows[i++] = slot < 0 ? item.getRowIndex() : rowOfSlot(slot);
			}
			return rows;
		}
		final int[] rowOfSlot = new int[nextSlot];
		int row = 0;
		for (int slot = first(root); slot != NONE; slot = successor(slot)) {
			rowOfSlot[slot] = row++;
		}
		for (final M3RowTreeItem item : selected) {
			final int slot = slotOf(item);
			rows[i++] = slot < 0 ? item.getRowIndex() : rowOfSlot[slot];
		}
		return rows;
	}

	/**
	 * Records that the visibility or the height of {@code item} changed; O(log n).
	 *
	 * @param item an item of this list (others are ignored)
	 */
	public void update(final M3RowTreeItem item) {
		final int slot = slotOf(item);
		if (slot >= 0) {
			reweigh(slot, extentOf(item));
		}
	}

	@Override
	public int visibleCount() {
		return visible(root);
	}

	@Override
	public long totalExtent() {
		return sum(root);
	}

	@Override
	public int extentAt(final int position) {
		return weights[slotAt(position)];
	}

	@Override
	public int rankOf(final int position) {
		int k = position;
		int rank = 0;
		int node = root;
		while (node != NONE && k > 0) {
			final int before = size(left[node]);
			if (k <= before) {
				node = left[node];
			} else {
				rank += visible(left[node]) + (weights[node] > 0 ? 1 : 0);
				k -= before + 1;
				node = right[node];
			}
		}
		return rank;
	}

	@Override
	public long extentBefore(final int position) {
		int k = position;
		long extent = 0;
		int node = root;
		while (node != NONE && k > 0) {
			final int before = size(left[node]);
			if (k <= before) {
				node = left[node];
			} else {
				extent += sum(left[node]) + weights[node];
				k -= before + 1;
				node = right[node];
			}
		}
		return extent;
	}

	@Override
	public int positionOfRank(final int rank) {
		int remaining = rank;
		int position = 0;
		int node = root;
		while (node != NONE) {
			final int before = visible(left[node]);
			if (remaining < before) {
				node = left[node];
				continue;
			}
			final int own = weights[node] > 0 ? 1 : 0;
			if (remaining < before + own) {
				return position + size(left[node]);
			}
			remaining -= before + own;
			position += size(left[node]) + 1;
			node = right[node];
		}
		throw new IndexOutOfBoundsException("visible row " + rank + ", visible rows " + visibleCount());
	}

	@Override
	public long extentOfRank(final int rank) {
		if (rank == 0) {
			return 0;
		}
		if (rank == visibleCount()) {
			return totalExtent();
		}
		return extentBefore(positionOfRank(rank));
	}

	@Override
	public int rankAtExtent(final long offset) {
		if (offset < 0) {
			return -1;
		}
		int rank = 0;
		long extent = 0;
		int node = root;
		while (node != NONE) {
			if (extent + sum(left[node]) > offset) {
				node = left[node];
				continue;
			}
			extent += sum(left[node]);
			rank += visible(left[node]);
			if (extent + weights[node] > offset) {
				return rank;
			}
			extent += weights[node];
			rank += weights[node] > 0 ? 1 : 0;
			node = right[node];
		}
		return rank;
	}

	@Override
	public int nextVisible(final int position) {
		final int rank = rankOf(position);
		return rank < visibleCount() ? positionOfRank(rank) : -1;
	}

	@Override
	public int previousVisible(final int position) {
		final int rank = rankOf(position + 1) - 1;
		return rank >= 0 ? positionOfRank(rank) : -1;
	}

	/* the extent a visible item adds: its height and the one-pixel line below it */
	private static int extentOf(final M3RowTreeItem item) {
		return item.isVisible() ? item.getHeight() + 1 : 0;
	}

	private int slotOf(final Object o) {
		if (!(o instanceof M3RowTreeItem)) {
			return NONE;
		}
		final int slot = ((M3RowTreeItem) o).rowTreeSlot();
		return slot >= 0 && slot < nextSlot && items[slot] == o ? slot : NONE;
	}

	private int slotAt(final int index) {
		int k = index;
		int node = root;
		while (true) {
			final int before = size(left[node]);
			if (k < before) {
				node = left[node];
			} else if (k == before) {
				return node;
			} else {
				k -= before + 1;
				node = right[node];
			}
		}
	}

	private int rowOfSlot(final int slot) {
		int row = size(left[slot]);
		for (int node = slot; up[node] != NONE; node = up[node]) {
			final int parent = up[node];
			if (right[parent] == node) {
				row += size(left[parent]) + 1;
			}
		}
		return row;
	}

	private void reweigh(final int slot, final int weight) {
		final long delta = (long) weight - weights[slot];
		final int visibleDelta = (weight > 0 ? 1 : 0) - (weights[slot] > 0 ? 1 : 0);
		if (delta == 0 && visibleDelta == 0) {
			return;
		}
		weights[slot] = weight;
		for (int node = slot; node != NONE; node = up[node]) {
			sums[node] += delta;
			visibles[node] += visibleDelta;
		}
	}

	private int first(final int node) {
		int first = node;
		if (first == NONE) {
			return NONE;
		}
		while (left[first] != NONE) {
			first = left[first];
		}
		return first;
	}

	private int successor(final int slot) {
		if (right[slot] != NONE) {
			return first(right[slot]);
		}
		int node = slot;
		while (up[node] != NONE && right[up[node]] == node) {
			node = up[node];
		}
		return up[node];
	}

	private int insertAt(final int node, final int index, final int slot) {
		if (node == NONE) {
			left[slot] = right[slot] = up[slot] = NONE;
			refresh(slot);
			return slot;
		}
		final int before = size(left[node]);
		if (index <= before) {
			final int child = insertAt(left[node], index, slot);
			left[node] = child;
			up[child] = node;
		} else {
			final int child = insertAt(right[node], index - before - 1, slot);
			right[node] = child;
			up[child] = node;
		}
		return rebalance(node);
	}

	private int removeAt(final int node, final int index) {
		final int before = size(left[node]);
		if (index < before) {
			final int child = removeAt(left[node], index);
			left[node] = child;
			if (child != NONE) {
				up[child] = node;
			}
		} else if (index > before) {
			final int child = removeAt(right[node], index - before - 1);
			right[node] = child;
			if (child != NONE) {
				up[child] = node;
			}
		} else {
			return detach(node);
		}
		return rebalance(node);
	}

	/* the successor takes the removed slot's place (as in the donor) */
	private int detach(final int slot) {
		final int l = left[slot], r = right[slot];
		left[slot] = right[slot] = up[slot] = NONE;
		if (l == NONE) {
			return r;
		}
		if (r == NONE) {
			return l;
		}
		final int successor = first(r);
		final int rest = removeFirst(r);
		right[successor] = rest;
		if (rest != NONE) {
			up[rest] = successor;
		}
		left[successor] = l;
		up[l] = successor;
		return rebalance(successor);
	}

	private int removeFirst(final int node) {
		if (left[node] == NONE) {
			return right[node];
		}
		final int child = removeFirst(left[node]);
		left[node] = child;
		if (child != NONE) {
			up[child] = node;
		}
		return rebalance(node);
	}

	private int rebalance(final int node) {
		refresh(node);
		final int balance = height(left[node]) - height(right[node]);
		if (balance > 1) {
			if (height(left[left[node]]) < height(right[left[node]])) {
				left[node] = rotateLeft(left[node]);
				up[left[node]] = node;
			}
			return rotateRight(node);
		}
		if (balance < -1) {
			if (height(right[right[node]]) < height(left[right[node]])) {
				right[node] = rotateRight(right[node]);
				up[right[node]] = node;
			}
			return rotateLeft(node);
		}
		return node;
	}

	private int rotateLeft(final int node) {
		final int pivot = right[node];
		right[node] = left[pivot];
		if (right[node] != NONE) {
			up[right[node]] = node;
		}
		left[pivot] = node;
		up[node] = pivot;
		refresh(node);
		refresh(pivot);
		return pivot;
	}

	private int rotateRight(final int node) {
		final int pivot = left[node];
		left[node] = right[pivot];
		if (left[node] != NONE) {
			up[left[node]] = node;
		}
		right[pivot] = node;
		up[node] = pivot;
		refresh(node);
		refresh(pivot);
		return pivot;
	}

	private void refresh(final int node) {
		final int l = left[node], r = right[node];
		sizes[node] = 1 + size(l) + size(r);
		visibles[node] = (weights[node] > 0 ? 1 : 0) + visible(l) + visible(r);
		sums[node] = weights[node] + sum(l) + sum(r);
		heights[node] = (byte) (1 + Math.max(height(l), height(r)));
	}

	private int height(final int node) {
		return node == NONE ? 0 : heights[node];
	}

	private int size(final int node) {
		return node == NONE ? 0 : sizes[node];
	}

	private int visible(final int node) {
		return node == NONE ? 0 : visibles[node];
	}

	private long sum(final int node) {
		return node == NONE ? 0 : sums[node];
	}

	private int allocate() {
		if (freeCount > 0) {
			return free[--freeCount];
		}
		ensureCapacity(nextSlot + 1);
		return nextSlot++;
	}

	private void release(final int slot) {
		items[slot] = null;
		weights[slot] = 0;
		if (freeCount == free.length) {
			free = Arrays.copyOf(free, Math.max(8, free.length * 2));
		}
		free[freeCount++] = slot;
	}

	private void ensureCapacity(final int slots) {
		if (slots <= items.length) {
			return;
		}
		final int capacity = M3WarmHeapGrowth.capacity(items.length, slots, SLOT_BYTES);
		// stage every lane before publishing, so a failed allocation leaves the tree unchanged
		final int[] nextLeft = Arrays.copyOf(left, capacity);
		final int[] nextRight = Arrays.copyOf(right, capacity);
		final int[] nextUp = Arrays.copyOf(up, capacity);
		final byte[] nextHeights = Arrays.copyOf(heights, capacity);
		final int[] nextSizes = Arrays.copyOf(sizes, capacity);
		final int[] nextVisibles = Arrays.copyOf(visibles, capacity);
		final int[] nextWeights = Arrays.copyOf(weights, capacity);
		final long[] nextSums = Arrays.copyOf(sums, capacity);
		final M3RowTreeItem[] nextItems = Arrays.copyOf(items, capacity);
		left = nextLeft;
		right = nextRight;
		up = nextUp;
		heights = nextHeights;
		sizes = nextSizes;
		visibles = nextVisibles;
		weights = nextWeights;
		sums = nextSums;
		items = nextItems;
	}

	private static void checkIndex(final int index, final int size) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException("index " + index + ", size " + size);
		}
	}
}
