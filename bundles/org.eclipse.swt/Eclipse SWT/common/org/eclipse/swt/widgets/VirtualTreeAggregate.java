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
package org.eclipse.swt.widgets;

import java.util.*;

/**
 * Footer aggregates of one Tree column (BEHAVIOURS.md "Tree footer", brief rule 24): count, filled, sum (and average),
 * min and max over the rows in scope, for the whole tree (the footer row) and for every branch (subtotal rows), kept
 * by deltas, never by a recount.
 * <p>
 * Rows are the caller's dense ids (0..), each with a parent (-1 for a root), a value (NaN when the cell is empty) and
 * two bits: EXPANDED and FILTERED_OUT. The scope decides which rows count: {@link #ALL} every row, {@link #FILTERED}
 * the rows not filtered out (each row on its own), {@link #VISIBLE} the rows of expanded branches (a row counts when
 * every ancestor is expanded). Per row the lanes hold the aggregate of its subtree under the scope and the totals of
 * its children; a delta changes a row's own part and walks up its ancestors while their aggregate changes.
 * </p>
 * <p>
 * Costs: setValue, setExpanded, setFilteredOut, add and remove of a leaf are O(depth) for count, filled and sum, and
 * O(depth log k) with min/max (k children per level: an indexed binary heap per parent over the children's subtree
 * min and max; catalogue heaps/indexed-priority-queue). Removing a subtree is O(its rows + depth). A scope change is
 * the one recount, O(rows), counted in {@link #recounts()}. Lanes are parallel primitive arrays (DOOP); the heaps of
 * min and max exist only when {@link #trackMinMax(boolean)} asked for them.
 * </p>
 */
final class VirtualTreeAggregate {
	static final int ALL = 0, FILTERED = 1, VISIBLE = 2;
	static final int EXPANDED = 1, FILTERED_OUT = 2, LIVE = 4;
	private static final int ROOT = 0;

	/* node = id + 1; node 0 is the virtual root (always passes its children, never counts itself) */
	private int capacity = 16;
	private int [] parents = new int [capacity];
	/* intrusive child lists (O(1) link and unlink, O(rows) walks of a subtree) */
	private int [] firstChild = new int [capacity], nextSibling = new int [capacity], previousSibling = new int [capacity];
	private byte [] flags = new byte [capacity];
	private double [] values = new double [capacity];
	/* subtree aggregate under the scope, and the totals of the children's aggregates */
	private int [] count = new int [capacity], filled = new int [capacity], childCount = new int [capacity], childFilled = new int [capacity];
	private double [] sum = new double [capacity], childSum = new double [capacity];
	/* min/max: subtree extremes (+/-Infinity when the subtree has no value in scope) and per-parent heaps of children */
	private boolean minMax;
	private double [] min, max;
	private int [][] minHeaps, maxHeaps;
	private int [] minHeapSizes, maxHeapSizes, minPositions, maxPositions;
	private int scope = ALL;
	private long recounts, updates;

	VirtualTreeAggregate () {
		flags [ROOT] = (byte) (LIVE | EXPANDED);
		Arrays.fill (firstChild, -1);
		Arrays.fill (nextSibling, -1);
		Arrays.fill (previousSibling, -1);
		parents [ROOT] = -1;
	}

	/* ---------------------------------------------------------------- configuration */

	/** Keeps min and max too (an indexed heap per parent); switching it on is one recount. */
	void trackMinMax (boolean on) {
		if (on == minMax) return;
		minMax = on;
		if (on) {
			min = new double [capacity];
			max = new double [capacity];
			minHeaps = new int [capacity] [];
			maxHeaps = new int [capacity] [];
			minHeapSizes = new int [capacity];
			maxHeapSizes = new int [capacity];
			minPositions = new int [capacity];
			maxPositions = new int [capacity];
			for (int node = 1; node < capacity; node++) {
				if ((flags [node] & LIVE) != 0) {
					heapInsert (parents [node], node);
				}
			}
		} else {
			min = max = null;
			minHeaps = maxHeaps = null;
			minHeapSizes = maxHeapSizes = minPositions = maxPositions = null;
		}
		recount ();
	}

	void setScope (int scope) {
		if (scope < ALL || scope > VISIBLE) throw new IllegalArgumentException ("scope");
		if (this.scope == scope) return;
		this.scope = scope;
		recount ();
	}

	int scope () {
		return scope;
	}

	/** Full recomputations so far (scope changes, min/max switched on): the gate logs them; deltas never add one. */
	long recounts () {
		return recounts;
	}

	/** Rows whose aggregate a delta recomputed (the ancestors walked): the delta cost. */
	long updates () {
		return updates;
	}

	/* ---------------------------------------------------------------- deltas */

	/** A new row under parentId (-1: a root), with its value and bits; it has no children yet. */
	void add (int id, int parentId, double value, int bits) {
		int node = id + 1, parent = parentId + 1;
		if (node <= 0) throw new IllegalArgumentException ("id");
		ensureCapacity (node + 1);
		if ((flags [node] & LIVE) != 0) throw new IllegalArgumentException ("row " + id + " exists");
		if ((flags [parent] & LIVE) == 0) throw new IllegalArgumentException ("parent " + parentId + " missing");
		parents [node] = parent;
		link (parent, node);
		flags [node] = (byte) (LIVE | (bits & (EXPANDED | FILTERED_OUT)));
		values [node] = value;
		childCount [node] = childFilled [node] = 0;
		childSum [node] = 0;
		count [node] = filled [node] = 0;
		sum [node] = 0;
		if (minMax) {
			min [node] = Double.POSITIVE_INFINITY;
			max [node] = Double.NEGATIVE_INFINITY;
			minHeapSizes [node] = maxHeapSizes [node] = 0;
			heapInsert (parent, node);
		}
		refresh (node);
	}

	/** Removes the row and its subtree. */
	void remove (int id) {
		int node = live (id);
		int parent = parents [node];
		/* the parent first forgets the whole subtree's contribution, then the rows go */
		int oldCount = count [node], oldFilled = filled [node];
		double oldSum = sum [node];
		if (minMax) heapRemove (parent, node);
		childCount [parent] -= oldCount;
		childFilled [parent] -= oldFilled;
		childSum [parent] -= oldSum;
		unlink (node);
		release (node);
		refresh (parent);
	}

	void setValue (int id, double value) {
		int node = live (id);
		if (Double.compare (values [node], value) == 0) return;
		values [node] = value;
		refresh (node);
	}

	void setExpanded (int id, boolean expanded) {
		setBit (id, EXPANDED, expanded);
	}

	void setFilteredOut (int id, boolean out) {
		setBit (id, FILTERED_OUT, out);
	}

	/* ---------------------------------------------------------------- queries */

	/** Rows in scope in the whole tree (the footer row), or in the subtree of a row including it (a subtotal row). */
	int count (int id) {
		return count [node (id)];
	}

	int filled (int id) {
		return filled [node (id)];
	}

	double sum (int id) {
		return sum [node (id)];
	}

	/** NaN without a value in scope. */
	double average (int id) {
		int node = node (id);
		return filled [node] == 0 ? Double.NaN : sum [node] / filled [node];
	}

	double min (int id) {
		if (!minMax) throw new IllegalStateException ("min/max not tracked");
		double value = min [node (id)];
		return value == Double.POSITIVE_INFINITY ? Double.NaN : value;
	}

	double max (int id) {
		if (!minMax) throw new IllegalStateException ("min/max not tracked");
		double value = max [node (id)];
		return value == Double.NEGATIVE_INFINITY ? Double.NaN : value;
	}

	/** -1 is the whole tree. */
	boolean contains (int id) {
		int node = id + 1;
		return node >= 0 && node < capacity && (flags [node] & LIVE) != 0;
	}

	/* ---------------------------------------------------------------- internals */

	private int node (int id) {
		return id == -1 ? ROOT : live (id);
	}

	private int live (int id) {
		int node = id + 1;
		if (node <= 0 || node >= capacity || (flags [node] & LIVE) == 0) throw new IllegalArgumentException ("row " + id);
		return node;
	}

	private void setBit (int id, int bit, boolean on) {
		int node = live (id);
		boolean was = (flags [node] & bit) != 0;
		if (was == on) return;
		flags [node] = (byte) (on ? flags [node] | bit : flags [node] & ~bit);
		refresh (node);
	}

	/* own part: does the row itself count under the scope */
	private boolean counts (int node) {
		return node != ROOT && (scope != FILTERED || (flags [node] & FILTERED_OUT) == 0);
	}

	/* do the children's aggregates reach this row's aggregate */
	private boolean passes (int node) {
		return node == ROOT || scope != VISIBLE || (flags [node] & EXPANDED) != 0;
	}

	/*
	 * Recomputes the aggregate of node from its own part and its children's totals, then carries the difference to the
	 * parent's totals (and the parent's heaps) and goes on up while something changed: O(depth), O(depth log k) with
	 * min/max.
	 */
	private void refresh (int node) {
		while (node >= 0) {
			updates++;
			boolean own = counts (node), pass = passes (node);
			double value = values [node];
			boolean has = own && !Double.isNaN (value);
			int newCount = (own ? 1 : 0) + (pass ? childCount [node] : 0);
			int newFilled = (has ? 1 : 0) + (pass ? childFilled [node] : 0);
			double newSum = (has ? value : 0) + (pass ? childSum [node] : 0);
			double newMin = 0, newMax = 0;
			boolean extremes = false;
			if (minMax) {
				newMin = has ? value : Double.POSITIVE_INFINITY;
				newMax = has ? value : Double.NEGATIVE_INFINITY;
				if (pass && minHeapSizes [node] > 0) {
					newMin = Math.min (newMin, min [minHeaps [node] [0]]);
					newMax = Math.max (newMax, max [maxHeaps [node] [0]]);
				}
				extremes = Double.compare (newMin, min [node]) != 0 || Double.compare (newMax, max [node]) != 0;
			}
			int dCount = newCount - count [node], dFilled = newFilled - filled [node];
			double dSum = newSum - sum [node];
			boolean changed = dCount != 0 || dFilled != 0 || Double.compare (newSum, sum [node]) != 0 || extremes;
			if (!changed) return;
			count [node] = newCount;
			filled [node] = newFilled;
			sum [node] = newSum;
			int parent = parents [node];
			if (minMax) {
				min [node] = newMin;
				max [node] = newMax;
				if (parent >= 0) heapUpdate (parent, node);
			}
			if (parent < 0) return;
			childCount [parent] += dCount;
			childFilled [parent] += dFilled;
			childSum [parent] += dSum;
			node = parent;
		}
	}

	/* frees a subtree's rows (their parent already dropped their contribution and unlinked it): O(rows of the subtree) */
	private void release (int root) {
		int node = root;
		while (true) {
			/* post-order over the child lists: descend to a leaf, clear it, go on with its next sibling or its parent */
			while (firstChild [node] >= 0) node = firstChild [node];
			int next = nextSibling [node], up = parents [node];
			clearRow (node);
			if (node == root) return;
			if (next >= 0) {
				node = next;
			} else {
				node = up;
				firstChild [node] = -1;
			}
		}
	}

	private void clearRow (int node) {
		flags [node] = 0;
		parents [node] = -1;
		firstChild [node] = nextSibling [node] = previousSibling [node] = -1;
		count [node] = filled [node] = childCount [node] = childFilled [node] = 0;
		sum [node] = childSum [node] = 0;
		values [node] = Double.NaN;
		if (minMax) minHeapSizes [node] = maxHeapSizes [node] = 0;
	}

	private void link (int parent, int node) {
		int head = firstChild [parent];
		nextSibling [node] = head;
		previousSibling [node] = -1;
		if (head >= 0) previousSibling [head] = node;
		firstChild [parent] = node;
		firstChild [node] = -1;
	}

	private void unlink (int node) {
		int parent = parents [node], previous = previousSibling [node], next = nextSibling [node];
		if (previous >= 0) nextSibling [previous] = next;
		else firstChild [parent] = next;
		if (next >= 0) previousSibling [next] = previous;
		nextSibling [node] = previousSibling [node] = -1;
	}

	/* the one full recomputation, children before parents (post-order over the child lists from the virtual root): O(rows) */
	private void recount () {
		recounts++;
		int node = ROOT;
		while (firstChild [node] >= 0) node = firstChild [node];
		while (true) {
			childCount [node] = childFilled [node] = 0;
			childSum [node] = 0;
			for (int child = firstChild [node]; child >= 0; child = nextSibling [child]) {
				childCount [node] += count [child];
				childFilled [node] += filled [child];
				childSum [node] += sum [child];
			}
			compute (node);
			if (node == ROOT) return;
			int next = nextSibling [node];
			if (next >= 0) {
				node = next;
				while (firstChild [node] >= 0) node = firstChild [node];
			} else {
				node = parents [node];
			}
		}
	}

	/* the row's aggregate from its own part and its children's totals (the children are final) */
	private void compute (int node) {
		boolean own = counts (node), pass = passes (node);
		double value = values [node];
		boolean has = own && !Double.isNaN (value);
		count [node] = (own ? 1 : 0) + (pass ? childCount [node] : 0);
		filled [node] = (has ? 1 : 0) + (pass ? childFilled [node] : 0);
		sum [node] = (has ? value : 0) + (pass ? childSum [node] : 0);
		if (minMax) {
			min [node] = has ? value : Double.POSITIVE_INFINITY;
			max [node] = has ? value : Double.NEGATIVE_INFINITY;
			if (minHeapSizes [node] > 0) {
				/* the children's keys changed in this pass: rebuild this row's heaps */
				heapify (node);
				if (pass) {
					min [node] = Math.min (min [node], min [minHeaps [node] [0]]);
					max [node] = Math.max (max [node], max [maxHeaps [node] [0]]);
				}
			}
		}
	}

	private void ensureCapacity (int required) {
		if (required <= capacity) return;
		int next = Math.max (required, capacity * 2);
		parents = Arrays.copyOf (parents, next);
		firstChild = Arrays.copyOf (firstChild, next);
		nextSibling = Arrays.copyOf (nextSibling, next);
		previousSibling = Arrays.copyOf (previousSibling, next);
		Arrays.fill (parents, capacity, next, -1);
		Arrays.fill (firstChild, capacity, next, -1);
		Arrays.fill (nextSibling, capacity, next, -1);
		Arrays.fill (previousSibling, capacity, next, -1);
		flags = Arrays.copyOf (flags, next);
		values = Arrays.copyOf (values, next);
		count = Arrays.copyOf (count, next);
		filled = Arrays.copyOf (filled, next);
		childCount = Arrays.copyOf (childCount, next);
		childFilled = Arrays.copyOf (childFilled, next);
		sum = Arrays.copyOf (sum, next);
		childSum = Arrays.copyOf (childSum, next);
		if (minMax) {
			min = Arrays.copyOf (min, next);
			max = Arrays.copyOf (max, next);
			minHeaps = Arrays.copyOf (minHeaps, next);
			maxHeaps = Arrays.copyOf (maxHeaps, next);
			minHeapSizes = Arrays.copyOf (minHeapSizes, next);
			maxHeapSizes = Arrays.copyOf (maxHeapSizes, next);
			minPositions = Arrays.copyOf (minPositions, next);
			maxPositions = Arrays.copyOf (maxPositions, next);
		}
		capacity = next;
	}

	/* ---------------------------------------------------------------- indexed heaps (children of a parent by subtree min / max) */

	private void heapInsert (int parent, int child) {
		minHeaps [parent] = push (minHeaps [parent], minHeapSizes [parent]);
		maxHeaps [parent] = push (maxHeaps [parent], maxHeapSizes [parent]);
		int i = minHeapSizes [parent]++;
		minHeaps [parent] [i] = child;
		minPositions [child] = i;
		siftUp (parent, i, true);
		i = maxHeapSizes [parent]++;
		maxHeaps [parent] [i] = child;
		maxPositions [child] = i;
		siftUp (parent, i, false);
	}

	private static int [] push (int [] heap, int size) {
		if (heap == null) return new int [4];
		return size == heap.length ? Arrays.copyOf (heap, size * 2) : heap;
	}

	private void heapRemove (int parent, int child) {
		removeAt (parent, minPositions [child], true);
		removeAt (parent, maxPositions [child], false);
	}

	private void removeAt (int parent, int i, boolean isMin) {
		int [] heap = isMin ? minHeaps [parent] : maxHeaps [parent];
		int last = isMin ? --minHeapSizes [parent] : --maxHeapSizes [parent];
		if (i != last) {
			heap [i] = heap [last];
			position (heap [i], i, isMin);
			siftDown (parent, i, isMin);
			siftUp (parent, i, isMin);
		}
	}

	private void heapUpdate (int parent, int child) {
		int i = minPositions [child];
		siftUp (parent, i, true);
		siftDown (parent, minPositions [child], true);
		i = maxPositions [child];
		siftUp (parent, i, false);
		siftDown (parent, maxPositions [child], false);
	}

	private void heapify (int parent) {
		for (int i = minHeapSizes [parent] / 2 - 1; i >= 0; i--) siftDown (parent, i, true);
		for (int i = maxHeapSizes [parent] / 2 - 1; i >= 0; i--) siftDown (parent, i, false);
	}

	/* min heap: a smaller min first; max heap: a larger max first */
	private boolean before (int a, int b, boolean isMin) {
		return isMin ? min [a] < min [b] : max [a] > max [b];
	}

	private void position (int node, int i, boolean isMin) {
		if (isMin) minPositions [node] = i;
		else maxPositions [node] = i;
	}

	private void siftUp (int parent, int i, boolean isMin) {
		int [] heap = isMin ? minHeaps [parent] : maxHeaps [parent];
		int node = heap [i];
		while (i > 0) {
			int up = (i - 1) >>> 1;
			if (!before (node, heap [up], isMin)) break;
			heap [i] = heap [up];
			position (heap [i], i, isMin);
			i = up;
		}
		heap [i] = node;
		position (node, i, isMin);
	}

	private void siftDown (int parent, int i, boolean isMin) {
		int [] heap = isMin ? minHeaps [parent] : maxHeaps [parent];
		int size = isMin ? minHeapSizes [parent] : maxHeapSizes [parent];
		int node = heap [i];
		while (true) {
			int child = 2 * i + 1;
			if (child >= size) break;
			if (child + 1 < size && before (heap [child + 1], heap [child], isMin)) child++;
			if (!before (heap [child], node, isMin)) break;
			heap [i] = heap [child];
			position (heap [i], i, isMin);
			i = child;
		}
		heap [i] = node;
		position (node, i, isMin);
	}
}
