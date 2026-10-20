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
 * com.m3.collections.M3OrderStatisticForest (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 553eefd41d9961d303c41e0e4e181712e64a1e45,
 * VirtualTreeSiblingIndex.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 * The AVL order-statistic lanes are adapted from hsoliwal/com.synexia
 * synexia-primitives/src/main/java/com/synexia/primitives/LongOrderStatisticMultiset.java
 * (af4494b143270d03cf4d3f33d1e7b4f84325f5dd), Copyright 2026 Hitesh Soliwal and
 * contributors, Apache-2.0. See docs/m3/tree-viewport/THIRD_PARTY_NOTICES.md.
 * Split and join follow the join-based balanced-tree algorithms of Blelloch, Ferizovic and Sun, "Just Join for
 * Parallel Ordered Sets" (SPAA 2016), pattern only.
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;

/**
 * Order-statistic index over the materialized children of every parent of a
 * {@link M3TreeTopology} (and over the measured rows of {@link M3SparseExtents}).
 *
 * <p>Each parent owns a height-balanced binary search tree whose nodes are the
 * owner's ids, so the index allocates no node of its own. The search key of a node
 * is kept in the owner's key lane and the weight in its weight lane; both lanes are
 * borrowed, never copied. Every node also carries its subtree size and weight sum.</p>
 *
 * <p>Keys shift lazily: {@link #shift(int, int, int)} adds a delta to every key at
 * or after a position in O(log k), leaving a pending shift on O(log k) nodes. The
 * key lane therefore holds a node's key relative to the pending shifts of its
 * ancestors; {@link #key(int)} answers the absolute key in O(log k) through parent
 * links. Range removal ({@link #removeRange(int, int, int, int[])}) splits the tree
 * twice and joins the outer parts, O(log k + removed). These two make a patch
 * (remove runs, insert runs) cost O(runs log k + removed), independent of how many
 * siblings follow the edit (brief rule 24).</p>
 *
 * <p>Every lookup, prefix sum and row descent is one root-to-leaf walk; insertion and
 * removal are a split and a join along one path. {@link #work()} counts the nodes
 * those walks visit, so tests can bound the cost of an edit. The lanes are parallel
 * primitive arrays indexed by id: five ints, one byte and one long per id, plus one
 * root int per parent slot (slot 0 is the root of the tree itself).</p>
 *
 * <p>Ids are the owner's storage slots (rule 26: the public lane model's slots); a parent's
 * key lane holds each child's row among its siblings, dense (every row has a slot) or
 * sparse (only materialized rows have one). Cost, k children of the parent:
 * {@link #find} (row to slot), {@link #key} (slot to row), {@link #lower},
 * {@link #ceiling}, {@link #rank}, {@link #prefix}, {@link #seek}, {@link #seekOffset},
 * {@link #insert}, {@link #remove}, {@link #adjust}, {@link #shift}: O(log k);
 * {@link #removeRange}: O(log k + removed); {@link #order}: O(log k + count);
 * {@link #flush}: O(k); {@link #size(int)}, {@link #total}, {@link #treeHeight}: O(1).</p>
 *
 * <p>Threads: not thread-safe and holds no lock; confined to its owner's thread (the
 * thread that applies the model's patches). Even reads may not run beside a write:
 * a write moves nodes and pending shifts.</p>
 */
public final class M3OrderStatisticForest {
	private static final int NONE = -1;
	/* longer than any parent chain of a balanced tree over 2^31 ids; bounds walks over stale links */
	private static final int MAX_DEPTH = 64;

	private int [] left = new int [0];
	private int [] right = new int [0];
	private int [] up = new int [0];
	private byte [] heights = new byte [0];
	private int [] sizes = new int [0];
	private long [] sums = new long [0];
	/* key shift still owed to every strict descendant of a node */
	private int [] shifts = new int [0];
	private int [] roots = {NONE};
	private int [] keys = new int [0];
	private long [] weights = new long [0];
	private long seekBefore;
	private int seekRank, seekKey;
	private int splitLeft, splitRight, firstNode;
	private long work;

	public M3OrderStatisticForest () {
	}

	/** Rebinds the borrowed key and weight lanes after the owner grows them. */
	public void lanes (int [] keys, long [] weights) {
		this.keys = keys;
		this.weights = weights;
	}

	/** Grows the lanes to hold ids below {@code capacity}. */
	public void ensureCapacity (int capacity) {
		if (capacity <= left.length) return;
		int old = left.length, oldRoots = roots.length;
		left = Arrays.copyOf (left, capacity);
		right = Arrays.copyOf (right, capacity);
		up = Arrays.copyOf (up, capacity);
		heights = Arrays.copyOf (heights, capacity);
		sizes = Arrays.copyOf (sizes, capacity);
		sums = Arrays.copyOf (sums, capacity);
		shifts = Arrays.copyOf (shifts, capacity);
		Arrays.fill (left, old, capacity, NONE);
		Arrays.fill (right, old, capacity, NONE);
		Arrays.fill (up, old, capacity, NONE);
		roots = Arrays.copyOf (roots, Math.addExact (capacity, 1));
		Arrays.fill (roots, oldRoots, roots.length, NONE);
	}

	/** Forgets every parent's tree; node lanes are reinitialized on insertion. */
	public void clear () {
		Arrays.fill (roots, NONE);
		Arrays.fill (up, NONE);
	}

	/** Nodes visited by the walks of this index since it was created (the cost counter of rule 24). */
	public long work () {
		return work;
	}

	/** Absolute key of the linked {@code id}: its lane value plus the shifts its ancestors still owe it. */
	public int key (int id) {
		int key = keys [id];
		for (int a = up [id], depth = 0; a != NONE; a = up [a]) {
			work++;
			if (++depth > MAX_DEPTH) throw new IllegalStateException ("sibling index is inconsistent");
			key += shifts [a];
		}
		return key;
	}

	/** Returns the child of {@code parentId} at {@code key}, or -1. */
	public int find (int parentId, int key) {
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			int nodeKey = keys [node] + acc;
			if (key == nodeKey) return node;
			acc += shifts [node];
			node = key < nodeKey ? left [node] : right [node];
		}
		return NONE;
	}

	/** Returns the child of {@code parentId} with the greatest key below {@code key}, or -1. */
	public int lower (int parentId, int key) {
		int found = NONE;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			boolean below = keys [node] + acc < key;
			if (below) found = node;
			acc += shifts [node];
			node = below ? right [node] : left [node];
		}
		return found;
	}

	/** Returns the child of {@code parentId} with the least key at or above {@code key}, or -1. */
	public int ceiling (int parentId, int key) {
		int found = NONE;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			boolean atOrAbove = keys [node] + acc >= key;
			if (atOrAbove) found = node;
			acc += shifts [node];
			node = atOrAbove ? left [node] : right [node];
		}
		return found;
	}

	/** Returns the child of {@code parentId} with the greatest key, or -1. */
	public int last (int parentId) {
		int node = roots [parentId + 1];
		if (node == NONE) return NONE;
		while (right [node] != NONE) {
			work++;
			node = right [node];
		}
		return node;
	}

	/** Links {@code id}, whose absolute key and weight are already in the owner's lanes. */
	public void insert (int parentId, int id) {
		int key = keys [id];
		if (find (parentId, key) != NONE) throw new IllegalStateException ("duplicate sibling index");
		split (roots [parentId + 1], key);
		shifts [id] = 0;
		setRoot (parentId, join (splitLeft, id, splitRight));
	}

	/** Unlinks {@code id}. */
	public void remove (int parentId, int id) {
		requireLinked (parentId, id);
		int key = key (id);
		split (roots [parentId + 1], key);
		int l = splitLeft;
		int r = removeFirst (splitRight);
		if (firstNode != id) throw new IllegalStateException ("sibling index is inconsistent");
		reset (id);
		setRoot (parentId, join2 (l, r));
	}

	/** Records that the weight of the linked {@code id} changed by {@code delta}. */
	public void adjust (int parentId, int id, long delta) {
		if (delta == 0) return;
		requireLinked (parentId, id);
		for (int node = id; node != NONE; node = up [node]) {
			work++;
			sums [node] += delta;
		}
	}

	/** Sum of the weights of the children of {@code parentId} whose key is below {@code key}. */
	public long prefix (int parentId, int key) {
		long result = 0;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			boolean below = keys [node] + acc < key;
			acc += shifts [node];
			if (below) {
				result += sums [node] - sum (right [node]);
				node = right [node];
			} else {
				node = left [node];
			}
		}
		return result;
	}

	/** {@link #prefix(int, int)} and {@link #rank(int, int)} in one walk: answers the prefix, {@link #seekRank()} the rank. */
	public long prefixRank (int parentId, int key) {
		long result = 0;
		int count = 0;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			boolean below = keys [node] + acc < key;
			acc += shifts [node];
			if (below) {
				result += sums [node] - sum (right [node]);
				count += sizeOf (left [node]) + 1;
				node = right [node];
			} else {
				node = left [node];
			}
		}
		seekRank = count;
		return result;
	}

	/** Number of children of {@code parentId} whose key is below {@code key}. */
	public int rank (int parentId, int key) {
		int result = 0;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			boolean below = keys [node] + acc < key;
			acc += shifts [node];
			if (below) {
				result += sizeOf (left [node]) + 1;
				node = right [node];
			} else {
				node = left [node];
			}
		}
		return result;
	}

	/**
	 * Height of the tree of {@code parentId}, 0 when it has no child. A tree of
	 * height h holds at most 2^h - 1 children.
	 */
	public int treeHeight (int parentId) {
		return height (roots [parentId + 1]);
	}

	/** Sum of the weights of every child of {@code parentId}. */
	public long total (int parentId) {
		return sum (roots [parentId + 1]);
	}

	/** Number of children of {@code parentId} in the index. */
	public int size (int parentId) {
		return sizeOf (roots [parentId + 1]);
	}

	/**
	 * Returns the last child c of {@code parentId} with {@code key (c) < limit} and
	 * {@code key (c) + prefix (key (c)) <= row}, or -1. That sum is the row offset
	 * of c among its siblings when every logical child counts one row plus its
	 * weight. {@link #seekBefore()} then answers {@code prefix (key (c))}.
	 */
	public int seek (int parentId, long row, int limit) {
		int found = NONE;
		long before = 0, foundBefore = 0;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			int key = keys [node] + acc;
			acc += shifts [node];
			long leftSum = sum (left [node]);
			if (key < limit && key + before + leftSum <= row) {
				found = node;
				foundBefore = before + leftSum;
				before = foundBefore + weights [node];
				node = right [node];
			} else {
				node = left [node];
			}
		}
		seekBefore = foundBefore;
		return found;
	}

	/**
	 * Returns the last child c of {@code parentId} whose start, {@code (key (c) - rank (key (c))) * slope +
	 * prefix (key (c))}, is at most {@code offset}; or -1. With weights as measured extents and {@code slope} as the
	 * extent of an unmeasured key, that start is the offset of key c. {@link #seekBefore()} then answers the prefix
	 * and {@link #seekRank()} the rank of c.
	 */
	public int seekOffset (int parentId, long offset, long slope) {
		int found = NONE, rank = 0, foundRank = 0;
		long before = 0, foundBefore = 0;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			int key = keys [node] + acc;
			acc += shifts [node];
			int leftRank = rank + sizeOf (left [node]);
			long leftSum = before + sum (left [node]);
			if ((key - (long) leftRank) * slope + leftSum <= offset) {
				found = node;
				seekKey = key;
				foundRank = leftRank;
				foundBefore = leftSum;
				rank = leftRank + 1;
				before = leftSum + weights [node];
				node = right [node];
			} else {
				node = left [node];
			}
		}
		seekBefore = foundBefore;
		seekRank = foundRank;
		return found;
	}

	/** Weight before the child returned by the last {@link #seek(int, long, int)} or {@link #seekOffset(int, long, long)}. */
	public long seekBefore () {
		return seekBefore;
	}

	/** Rank of the child returned by the last {@link #seekOffset(int, long, long)} (or of the key of {@link #prefixRank(int, int)}). */
	public int seekRank () {
		return seekRank;
	}

	/** Absolute key of the child returned by the last {@link #seekOffset(int, long, long)}. */
	public int seekKey () {
		return seekKey;
	}

	/**
	 * Adds {@code delta} to the key of every child of {@code parentId} whose key is at or above {@code from}, in
	 * O(log k). The caller keeps the keys distinct and in range: for a negative delta no key lies in
	 * {@code [from + delta, from)}, and no shifted key leaves the int range (see {@link #last(int)}).
	 */
	public void shift (int parentId, int from, int delta) {
		if (delta == 0) return;
		int node = roots [parentId + 1], acc = 0;
		while (node != NONE) {
			work++;
			int key = keys [node] + acc;
			acc += shifts [node];
			if (key >= from) {
				keys [node] += delta;
				int r = right [node];
				if (r != NONE) {
					keys [r] += delta;
					shifts [r] += delta;
				}
				node = left [node];
			} else {
				node = right [node];
			}
		}
	}

	/**
	 * Writes the children of {@code parentId} in key order, starting at the first key at or above {@code fromKey}, to
	 * {@code out} (at most {@code count} of them, from index 0); answers how many. One descent, then in-order
	 * successor steps along parent links: O(log k + count). The bulk export of a row run to slots.
	 */
	public int order (int parentId, int fromKey, int count, int [] out) {
		int node = ceiling (parentId, fromKey), n = 0;
		while (node != NONE && n < count) {
			out [n++] = node;
			node = successor (node);
		}
		return n;
	}

	/* the next node in key order: the leftmost of the right subtree, else the first ancestor reached from its left */
	private int successor (int node) {
		int r = right [node];
		if (r != NONE) {
			while (left [r] != NONE) {
				work++;
				r = left [r];
			}
			return r;
		}
		int child = node, parent = up [node];
		while (parent != NONE && right [parent] == child) {
			work++;
			child = parent;
			parent = up [parent];
		}
		return parent;
	}

	/** Number of children of {@code parentId} with a key in {@code [from, to)}. */
	public int countRange (int parentId, int from, int to) {
		return from >= to ? 0 : rank (parentId, to) - rank (parentId, from);
	}

	/**
	 * Unlinks every child of {@code parentId} with a key in {@code [from, to)} and writes their ids, in key order,
	 * to {@code out} (at least {@link #countRange(int, int, int)} long); answers how many. Two splits and one join:
	 * O(log k + removed). The removed ids keep their absolute keys in the owner's lane.
	 */
	public int removeRange (int parentId, int from, int to, int [] out) {
		if (from >= to) return 0;
		if (out.length < countRange (parentId, from, to)) throw new IllegalArgumentException ("removal buffer too small");
		split (roots [parentId + 1], from);
		int l = splitLeft;
		split (splitRight, to);
		int middle = splitLeft, r = splitRight;
		int removed = collect (middle, out, 0);
		setRoot (parentId, join2 (l, r));
		return removed;
	}

	/**
	 * Resets the lanes of {@code id} without unlinking it: for an owner that drops a whole tree (every node of it is
	 * forgotten, then {@link #dropTree(int)}), O(1) per node instead of one removal each.
	 */
	public void forget (int id) {
		reset (id);
	}

	/** Forgets the tree of {@code parentId}; its nodes must have been {@link #forget(int) forgotten}. */
	public void dropTree (int parentId) {
		roots [parentId + 1] = NONE;
	}

	/** Applies every pending shift of the tree of {@code parentId}, so the key lane holds absolute keys. O(k). */
	public void flush (int parentId) {
		flushNode (roots [parentId + 1]);
	}

	private void flushNode (int node) {
		if (node == NONE) return;
		work++;
		push (node);
		flushNode (left [node]);
		flushNode (right [node]);
	}

	private void requireLinked (int parentId, int id) {
		int top = id;
		for (int depth = 0; up [top] != NONE; top = up [top]) {
			if (++depth > MAX_DEPTH) throw new IllegalStateException ("sibling index is inconsistent");
		}
		if (top != roots [parentId + 1]) throw new IllegalStateException ("sibling index is inconsistent");
	}

	/* in key order, pushing shifts so every removed id ends with its absolute key; detaches each one */
	private int collect (int node, int [] out, int at) {
		if (node == NONE) return at;
		work++;
		push (node);
		int r = right [node];
		at = collect (left [node], out, at);
		out [at++] = node;
		reset (node);
		return collect (r, out, at);
	}

	private void setRoot (int parentId, int root) {
		roots [parentId + 1] = root;
		if (root != NONE) up [root] = NONE;
	}

	private void reset (int node) {
		left [node] = right [node] = up [node] = NONE;
		heights [node] = 0;
		sizes [node] = 0;
		sums [node] = 0;
		shifts [node] = 0;
	}

	/* hands the shift a node owes its descendants to its children; their keys become absolute when its own is */
	private void push (int node) {
		int s = shifts [node];
		if (s == 0) return;
		int l = left [node], r = right [node];
		if (l != NONE) {
			keys [l] += s;
			shifts [l] += s;
		}
		if (r != NONE) {
			keys [r] += s;
			shifts [r] += s;
		}
		shifts [node] = 0;
	}

	/* links l and r below node (which owes no shift) and refreshes its aggregates */
	private int make (int node, int l, int r) {
		left [node] = l;
		right [node] = r;
		if (l != NONE) up [l] = node;
		if (r != NONE) up [r] = node;
		heights [node] = (byte) (1 + Math.max (height (l), height (r)));
		sizes [node] = 1 + sizeOf (l) + sizeOf (r);
		sums [node] = weights [node] + sum (l) + sum (r);
		return node;
	}

	/* splits the tree at t (absolute key at its root) into the keys below key and the keys at or above it */
	private void split (int t, int key) {
		if (t == NONE) {
			splitLeft = splitRight = NONE;
			return;
		}
		work++;
		push (t);
		int l = left [t], r = right [t];
		if (key <= keys [t]) {
			split (l, key);
			splitRight = join (splitRight, t, r);
		} else {
			split (r, key);
			int sl = splitLeft;
			splitLeft = join (l, t, sl);
		}
	}

	/* every key of l below the key of k, every key of r above it; k owes no shift */
	private int join (int l, int k, int r) {
		int hl = height (l), hr = height (r);
		if (hl > hr + 1) return joinRight (l, k, r);
		if (hr > hl + 1) return joinLeft (l, k, r);
		return make (k, l, r);
	}

	private int joinRight (int l, int k, int r) {
		work++;
		push (l);
		int ll = left [l], c = right [l];
		if (height (c) <= height (r) + 1) {
			int t = make (k, c, r);
			if (height (t) <= height (ll) + 1) return make (l, ll, t);
			return rotateLeft (make (l, ll, rotateRight (t)));
		}
		int t = joinRight (c, k, r);
		make (l, ll, t);
		return height (t) <= height (ll) + 1 ? l : rotateLeft (l);
	}

	private int joinLeft (int l, int k, int r) {
		work++;
		push (r);
		int c = left [r], rr = right [r];
		if (height (c) <= height (l) + 1) {
			int t = make (k, l, c);
			if (height (t) <= height (rr) + 1) return make (r, t, rr);
			return rotateRight (make (r, rotateLeft (t), rr));
		}
		int t = joinLeft (l, k, c);
		make (r, t, rr);
		return height (t) <= height (rr) + 1 ? r : rotateRight (r);
	}

	/* concatenates l and r (every key of l below every key of r) */
	private int join2 (int l, int r) {
		if (l == NONE) return r;
		if (r == NONE) return l;
		int rest = removeFirst (r);
		return join (l, firstNode, rest);
	}

	/* the tree at t without its first node, which is left in firstNode (detached, owing no shift) */
	private int removeFirst (int t) {
		if (t == NONE) {
			firstNode = NONE;
			return NONE;
		}
		work++;
		push (t);
		int l = left [t], r = right [t];
		if (l == NONE) {
			firstNode = t;
			return r;
		}
		int rest = removeFirst (l);
		return join (rest, t, r);
	}

	private int rotateLeft (int node) {
		push (node);
		int pivot = right [node];
		push (pivot);
		make (node, left [node], left [pivot]);
		return make (pivot, node, right [pivot]);
	}

	private int rotateRight (int node) {
		push (node);
		int pivot = left [node];
		push (pivot);
		make (node, right [pivot], right [node]);
		return make (pivot, left [pivot], node);
	}

	private int height (int node) {
		return node == NONE ? 0 : heights [node];
	}

	private int sizeOf (int node) {
		return node == NONE ? 0 : sizes [node];
	}

	private long sum (int node) {
		return node == NONE ? 0 : sums [node];
	}
}
