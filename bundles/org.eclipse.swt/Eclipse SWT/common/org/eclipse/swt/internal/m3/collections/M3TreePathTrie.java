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
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Interned tree paths: one {@code int} path id per node path, prefixes shared.
 *
 * <p>A path is the sequence of segments from the root to a node: child
 * indices, element ids, or label ids of names. Every path id records its
 * parent's path id and its last segment, so a path shares all of its prefix
 * with its parent. The child of a path with a given segment is found by one
 * probe of an edge table keyed by (parent path id, segment). Path id
 * {@link #ROOT} is the empty path.</p>
 *
 * <p>Per path the trie keeps parent, segment, depth, first child, next and
 * previous sibling ({@code int} lanes) and 64 flag bits ({@code long} lane).
 * Flags hold state that must survive a rebuild of the widget: expansion,
 * selection, filter matches, load state. Because a refreshed model derives the
 * same path ids from the same segments, the flags apply to the new items
 * without any per-item object or String path.</p>
 *
 * <p>With an {@link M3LabelPool} for names, every path holds one reference on
 * the label id of its segment and releases it when the path is removed.</p>
 *
 * <p>Not thread-safe; owned by one model.</p>
 */
public final class M3TreePathTrie {
	/** Path id of the empty path, the root of every path. */
	public static final int ROOT = 0;
	/** Answer of the find methods for a path that is not interned. */
	public static final int NOT_FOUND = -1;
	/** Flag: the node is expanded. */
	public static final long EXPANDED = 1L;
	/** Flag: the node is selected. */
	public static final long SELECTED = 1L << 1;
	/** Flag: the node matches the current filter. */
	public static final long MATCH = 1L << 2;
	/** Flag: a descendant of the node matches the current filter. */
	public static final long MATCH_ANCESTOR = 1L << 3;
	/** Flag: the children of the node have been loaded. */
	public static final long LOADED = 1L << 4;

	private static final int NONE = -1;
	private static final int FREE = -1;
	private static final long EMPTY = -1L;
	private static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

	private final M3LabelPool names;
	private int [] parents, segments, depths, firstChildren, nextSiblings, prevSiblings;
	private long [] flags;
	private long [] edgeKeys;
	private int [] edgeIds;
	private int edgeShift, idCount, size, freeHead;
	private char [] scratch = new char [64];

	/** Creates a trie whose segments are plain ints. */
	public M3TreePathTrie () {
		this (null);
	}

	/**
	 * Creates a trie whose segments may be label ids of {@code names}; every path
	 * then holds a reference on its segment's label.
	 */
	public M3TreePathTrie (M3LabelPool names) {
		this.names = names;
		reset ();
	}

	private void reset () {
		parents = new int [8];
		segments = new int [8];
		depths = new int [8];
		firstChildren = new int [8];
		nextSiblings = new int [8];
		prevSiblings = new int [8];
		flags = new long [8];
		edgeKeys = new long [16];
		edgeIds = new int [16];
		Arrays.fill (edgeKeys, EMPTY);
		edgeShift = 64 - 4;
		parents [ROOT] = NONE;
		firstChildren [ROOT] = NONE;
		idCount = 1;
		size = 0;
		freeHead = NONE;
	}

	/** Returns the path id of {@code parent} extended by {@code segment}, interning it when new. */
	public int child (int parent, int segment) {
		checkPath (parent);
		int index = edgeIndex (edgeKey (parent, segment));
		if (index >= 0) return edgeIds [index];
		if (names != null) names.acquire (segment);
		return create (parent, segment);
	}

	/** Returns the path id of {@code parent} extended by {@code segment}, or {@link #NOT_FOUND}. */
	public int find (int parent, int segment) {
		checkPath (parent);
		int index = edgeIndex (edgeKey (parent, segment));
		return index < 0 ? NOT_FOUND : edgeIds [index];
	}

	/** Interns the path of {@code segments [from, to)} below the root and returns its id. */
	public int intern (int [] segments, int from, int to) {
		Objects.checkFromToIndex (from, to, segments.length);
		int path = ROOT;
		for (int i = from; i < to; i++) path = child (path, segments [i]);
		return path;
	}

	/** Returns the id of the path of {@code segments [from, to)}, or {@link #NOT_FOUND}. */
	public int find (int [] segments, int from, int to) {
		Objects.checkFromToIndex (from, to, segments.length);
		int path = ROOT;
		for (int i = from; i < to && path != NOT_FOUND; i++) path = find (path, segments [i]);
		return path;
	}

	/** Returns the child of {@code parent} named {@code name}, interning the name and the path. */
	public int childNamed (int parent, String name) {
		checkPath (parent);
		int label = requireNames ().intern (name);
		int index = edgeIndex (edgeKey (parent, label));
		if (index < 0) return create (parent, label);
		names.release (label);
		return edgeIds [index];
	}

	/** Returns the child of {@code parent} named {@code name}, or {@link #NOT_FOUND}. */
	public int findNamed (int parent, String name) {
		checkPath (parent);
		int label = requireNames ().find (name);
		return label == M3LabelPool.NOT_FOUND ? NOT_FOUND : find (parent, label);
	}

	/**
	 * Interns {@code path}, split at {@code separator} into named segments (empty
	 * segments are skipped), and returns its id. A file path or a dotted key path
	 * becomes a path id without a String per segment.
	 */
	public int internPath (String path, char separator) {
		M3LabelPool pool = requireNames ();
		int length = path.length ();
		if (scratch.length < length) scratch = new char [Math.max (length, scratch.length * 2)];
		path.getChars (0, length, scratch, 0);
		int id = ROOT, start = 0;
		for (int i = 0; i <= length; i++) {
			if (i < length && scratch [i] != separator) continue;
			if (i > start) id = adoptNamed (id, pool.intern (scratch, start, i - start));
			start = i + 1;
		}
		return id;
	}

	private int adoptNamed (int parent, int label) {
		int index = edgeIndex (edgeKey (parent, label));
		if (index < 0) return create (parent, label);
		names.release (label);
		return edgeIds [index];
	}

	/** Parent path id of {@code path}; {@link #NOT_FOUND} for the root. */
	public int parent (int path) {
		checkPath (path);
		return parents [path];
	}

	/** Last segment of {@code path}; 0 for the root. */
	public int segment (int path) {
		checkPath (path);
		return segments [path];
	}

	/**
	 * Answers whether some interned path extends {@code path}. When none does, no
	 * descendant of {@code path} is interned, so a walk that restores flags can
	 * skip the lookups of the whole subtree (the trie is prefix-closed).
	 */
	public boolean hasChildren (int path) {
		checkPath (path);
		return firstChildren [path] != NONE;
	}

	/** Number of segments of {@code path}. */
	public int depth (int path) {
		checkPath (path);
		return depths [path];
	}

	/** Copies the segments of {@code path}, root first, into {@code dst}; returns their count. */
	public int segments (int path, int [] dst) {
		checkPath (path);
		int depth = depths [path];
		Objects.checkFromIndexSize (0, depth, dst.length);
		for (int node = path, i = depth - 1; i >= 0; node = parents [node], i--) dst [i] = segments [node];
		return depth;
	}

	/** The prefix of {@code path} with {@code depth} segments. */
	public int ancestorAt (int path, int depth) {
		checkPath (path);
		Objects.checkIndex (depth, depths [path] + 1);
		int node = path;
		while (depths [node] > depth) node = parents [node];
		return node;
	}

	/** Answers whether {@code prefix} is {@code path} or one of its ancestors. */
	public boolean startsWith (int path, int prefix) {
		checkPath (prefix);
		return depths [prefix] <= depth (path) && ancestorAt (path, depths [prefix]) == prefix;
	}

	/** The longest common prefix of two paths. */
	public int commonAncestor (int a, int b) {
		int depth = Math.min (depth (a), depth (b));
		int x = ancestorAt (a, depth), y = ancestorAt (b, depth);
		while (x != y) {
			x = parents [x];
			y = parents [y];
		}
		return x;
	}

	/** Sets or clears the flags in {@code mask} on {@code path}. */
	public void flag (int path, long mask, boolean on) {
		checkPath (path);
		flags [path] = on ? flags [path] | mask : flags [path] & ~mask;
	}

	/** Answers whether every flag of {@code mask} is set on {@code path}. */
	public boolean flag (int path, long mask) {
		checkPath (path);
		return (flags [path] & mask) == mask;
	}

	/** All flags of {@code path}. */
	public long flags (int path) {
		checkPath (path);
		return flags [path];
	}

	/**
	 * Sets {@code mask} on every proper ancestor of {@code path} except the root
	 * and returns how many nodes it newly marked. The walk stops at the first
	 * ancestor that already has the mask, so marking all matches of a filter
	 * costs the number of marked nodes in total; this holds while the mask is set
	 * only through this method (prefix-closed).
	 */
	public int markAncestors (int path, long mask) {
		checkPath (path);
		int marked = 0;
		for (int node = parents [path]; node > ROOT && (flags [node] & mask) != mask; node = parents [node]) {
			flags [node] |= mask;
			marked++;
		}
		return marked;
	}

	/** Clears {@code mask} on every path. */
	public void clearFlags (long mask) {
		for (int id = 0; id < idCount; id++) flags [id] &= ~mask;
	}

	/** Calls {@code action} with every live path that has all flags of {@code mask}; returns their count. */
	public int forEachFlagged (long mask, IntConsumer action) {
		int count = 0;
		for (int id = 1; id < idCount; id++) {
			if (depths [id] != FREE && (flags [id] & mask) == mask) {
				action.accept (id);
				count++;
			}
		}
		return count;
	}

	/** Removes {@code path} and every path below it; their ids may be reused. */
	public void remove (int path) {
		checkPath (path);
		if (path == ROOT) throw new IllegalArgumentException ("the root path cannot be removed");
		int node = path;
		while (true) {
			if (firstChildren [node] != NONE) {
				node = firstChildren [node];
				continue;
			}
			int parent = parents [node];
			free (node);
			if (node == path) return;
			node = parent;
		}
	}

	/** Appends the segments of {@code path} to {@code out}, each preceded by {@code separator}; names are pooled text. */
	public StringBuilder appendPath (int path, StringBuilder out, String separator) {
		int [] chain = new int [depth (path)];
		segments (path, chain);
		for (int segment : chain) {
			out.append (separator);
			if (names != null) out.append (names.text (segment)); else out.append (segment);
		}
		return out;
	}

	/** Answers whether {@code path} is a live path id. */
	public boolean contains (int path) {
		return path >= 0 && path < idCount && depths [path] != FREE;
	}

	/** Number of live paths, not counting the root. */
	public int size () {
		return size;
	}

	/** The pool of segment names, or null. */
	public M3LabelPool names () {
		return names;
	}

	/** Estimated heap bytes retained by the trie arrays (not the name pool). */
	public long retainedBytes () {
		return 10L * 16 + parents.length * 32L + edgeKeys.length * 12L + scratch.length * 2L;
	}

	/** Removes every path; releases the names they held. */
	public void clear () {
		if (names != null) {
			for (int id = 1; id < idCount; id++) {
				if (depths [id] != FREE) names.release (segments [id]);
			}
		}
		reset ();
	}

	private int create (int parent, int segment) {
		if ((size + 1) * 2 > edgeKeys.length) rehash (edgeKeys.length * 2);
		int id = allocate ();
		parents [id] = parent;
		segments [id] = segment;
		depths [id] = depths [parent] + 1;
		flags [id] = 0;
		firstChildren [id] = NONE;
		int first = firstChildren [parent];
		nextSiblings [id] = first;
		prevSiblings [id] = NONE;
		if (first != NONE) prevSiblings [first] = id;
		firstChildren [parent] = id;
		placeEdge (edgeKey (parent, segment), id);
		size++;
		return id;
	}

	private void free (int id) {
		int before = prevSiblings [id], after = nextSiblings [id];
		if (before == NONE) firstChildren [parents [id]] = after; else nextSiblings [before] = after;
		if (after != NONE) prevSiblings [after] = before;
		deleteEdge (edgeIndex (edgeKey (parents [id], segments [id])));
		if (names != null) names.release (segments [id]);
		depths [id] = FREE;
		nextSiblings [id] = freeHead;
		freeHead = id;
		size--;
	}

	private int allocate () {
		if (freeHead != NONE) {
			int id = freeHead;
			freeHead = nextSiblings [id];
			return id;
		}
		if (idCount == parents.length) grow (parents.length * 2);
		return idCount++;
	}

	private void grow (int capacity) {
		parents = Arrays.copyOf (parents, capacity);
		segments = Arrays.copyOf (segments, capacity);
		depths = Arrays.copyOf (depths, capacity);
		firstChildren = Arrays.copyOf (firstChildren, capacity);
		nextSiblings = Arrays.copyOf (nextSiblings, capacity);
		prevSiblings = Arrays.copyOf (prevSiblings, capacity);
		flags = Arrays.copyOf (flags, capacity);
	}

	/* As in M3LabelPool, free ids are reused first, so every id is live when the table grows. */
	private void rehash (int capacity) {
		edgeKeys = new long [capacity];
		edgeIds = new int [capacity];
		Arrays.fill (edgeKeys, EMPTY);
		edgeShift = 64 - Integer.numberOfTrailingZeros (capacity);
		for (int id = 1; id < idCount; id++) placeEdge (edgeKey (parents [id], segments [id]), id);
	}

	private void placeEdge (long key, int id) {
		int mask = edgeKeys.length - 1, i = edgeSlot (key);
		while (edgeKeys [i] != EMPTY) i = (i + 1) & mask;
		edgeKeys [i] = key;
		edgeIds [i] = id;
	}

	private int edgeIndex (long key) {
		int mask = edgeKeys.length - 1;
		for (int i = edgeSlot (key); ; i = (i + 1) & mask) {
			long found = edgeKeys [i];
			if (found == key) return i;
			if (found == EMPTY) return -1;
		}
	}

	private void deleteEdge (int hole) {
		int mask = edgeKeys.length - 1;
		for (int i = (hole + 1) & mask; edgeKeys [i] != EMPTY; i = (i + 1) & mask) {
			int home = edgeSlot (edgeKeys [i]);
			if (((i - home) & mask) >= ((i - hole) & mask)) {
				edgeKeys [hole] = edgeKeys [i];
				edgeIds [hole] = edgeIds [i];
				hole = i;
			}
		}
		edgeKeys [hole] = EMPTY;
	}

	private int edgeSlot (long key) {
		return (int) ((key * GOLDEN) >>> edgeShift);
	}

	private M3LabelPool requireNames () {
		if (names == null) throw new IllegalStateException ("this trie has no name pool");
		return names;
	}

	private void checkPath (int path) {
		if (!contains (path)) throw new IllegalArgumentException ("not a live path id: " + path);
	}

	private static long edgeKey (int parent, int segment) {
		return (long) parent << 32 | (segment & 0xFFFF_FFFFL);
	}
}
