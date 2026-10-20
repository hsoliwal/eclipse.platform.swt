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
 * com.m3.collections.M3ViewsTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Oracle tests of the general views: window, flattening (and their composition) and the CLOCK cache. */
public class M3ViewsTest {
	static final class Node {
		final String name;
		final List<Node> children = new ArrayList<> ();

		Node (String name) {
			this.name = name;
		}

		@Override
		public String toString () {
			return name;
		}
	}

	private static Node tree (Random random, String name, int depth) {
		Node node = new Node (name);
		int count = depth == 0 ? 0 : random.nextInt (5);
		for (int i = 0; i < count; i++) node.children.add (tree (random, name + "." + i, depth - 1));
		return node;
	}

	private static final M3FlatteningView.Source<Node> SOURCE = new M3FlatteningView.Source<> () {
		@Override
		public int childCount (Node parent) {
			return parent.children.size ();
		}

		@Override
		public Node child (Node parent, int index) {
			return parent.children.get (index);
		}
	};

	private static void flatten (Node parent, int depth, Map<Node, Boolean> expanded, List<Node> rows, List<Integer> depths) {
		for (Node child : parent.children) {
			rows.add (child);
			depths.add (depth);
			if (expanded.getOrDefault (child, false)) flatten (child, depth + 1, expanded, rows, depths);
		}
	}

	@Test
	public void flatteningMatchesDepthFirstOracle () {
		Random random = new Random (5L);
		Node root = tree (random, "r", 5);
		while (root.children.size () < 2) root = tree (random, "r", 5);
		Node top = root;
		M3FlatteningView<Node> view = new M3FlatteningView<> (new M3FlatteningView.Source<> () {
			@Override
			public int childCount (Node parent) {
				return SOURCE.childCount (parent == null ? top : parent);
			}

			@Override
			public Node child (Node parent, int index) {
				return SOURCE.child (parent == null ? top : parent, index);
			}
		});
		Map<Node, Boolean> expanded = new IdentityHashMap<> ();
		for (int step = 0; step < 300; step++) {
			List<Node> rows = new ArrayList<> ();
			List<Integer> depths = new ArrayList<> ();
			flatten (root, 0, expanded, rows, depths);
			assertEquals (rows.size (), view.size ());
			for (int i = 0; i < rows.size (); i++) {
				assertSame (rows.get (i), view.get (i), "step " + step + " row " + i);
				assertEquals (depths.get (i), view.depth (i));
				assertEquals (expanded.getOrDefault (rows.get (i), false), view.isExpanded (i));
			}
			int row = random.nextInt (rows.size ());
			if (expanded.getOrDefault (rows.get (row), false)) {
				view.collapse (row);
				expanded.put (rows.get (row), false);
			} else {
				view.expand (row);
				expanded.put (rows.get (row), true);
			}
		}
		assertTrue (view.materializedCount () > 0);
		view.collapse (0);
		view.collapse (0);
		assertThrows (IndexOutOfBoundsException.class, () -> view.get (view.size ()));
		assertThrows (IndexOutOfBoundsException.class, () -> view.depth (-1));
		assertThrows (NullPointerException.class, () -> new M3FlatteningView<Node> (null));
	}

	@Test
	public void windowOverListAndOverViews () {
		List<Integer> numbers = new ArrayList<> ();
		for (int i = 0; i < 100; i++) numbers.add (i);
		M3WindowView<Integer> window = new M3WindowView<> (numbers);
		assertSame (numbers, window.source ());
		assertEquals (0, window.size ());
		M3WindowPlan plan = window.move (10, 5);
		assertTrue (plan.replaced ());
		assertEquals (List.of (10, 11, 12, 13, 14), window);
		assertEquals (10, window.first ());
		assertEquals (5, window.requestedSize ());
		assertEquals (12, window.rowOf (2));
		assertEquals (-1, window.rowOf (5));
		assertEquals (-1, window.rowOf (-1));
		assertEquals (3, window.entryOf (13));
		assertEquals (-1, window.entryOf (15));
		window.flag (3, 1, true);
		window.state (4, 6);
		assertEquals (1, window.count (1));
		window.move (12, 5);
		assertEquals (2, window.lastMove ().retiredHead ());
		assertEquals (2, window.lastMove ().insertedTail ());
		assertTrue (window.flag (1, 1), "the state follows row 13");
		assertEquals (6, window.state (2), "the state follows row 14");
		assertEquals (0, window.state (4), "an entering row starts clear");
		window.flag (1, 1, false);
		assertFalse (window.flag (1, 1));
		window.move (8, 6);
		assertEquals (List.of (8, 9, 10, 11, 12, 13), window);
		assertEquals (4, window.lastMove ().insertedHead ());
		assertEquals (0, window.state (5), "row 13 was cleared");
		window.move (12, 3);
		assertEquals (0, window.state (2), "row 14 left the window and lost its state");
	}

	@Test
	public void windowClampsAndComposes () {
		List<Integer> numbers = new ArrayList<> ();
		for (int i = 0; i < 10; i++) numbers.add (i);
		M3WindowView<Integer> window = new M3WindowView<> (numbers);
		window.move (7, 6);
		assertEquals (List.of (7, 8, 9), window, "clamped to the source size");
		assertEquals (-1, window.entryOf (11));
		numbers.add (10);
		assertEquals (4, window.size (), "the window sees the grown source");
		M3WindowView<Integer> inner = new M3WindowView<> (window);
		inner.move (1, 2);
		assertEquals (List.of (8, 9), inner, "a window of a window");
		window.move (20, 3);
		assertEquals (0, window.size ());
		assertEquals (0, inner.size (), "the inner window follows its source");
		assertThrows (IndexOutOfBoundsException.class, () -> window.get (0));
		assertThrows (IndexOutOfBoundsException.class, () -> window.state (3));
		assertThrows (IndexOutOfBoundsException.class, () -> window.state (-1, 1));
		assertThrows (IndexOutOfBoundsException.class, () -> window.flag (3, 1, true));
		assertThrows (IllegalArgumentException.class, () -> window.move (-1, 1));
		assertThrows (NullPointerException.class, () -> new M3WindowView<Integer> (null));
		Node root = new Node ("r");
		for (int i = 0; i < 20; i++) root.children.add (new Node ("n" + i));
		M3FlatteningView<Node> flat = new M3FlatteningView<> (new M3FlatteningView.Source<> () {
			@Override
			public int childCount (Node parent) {
				return (parent == null ? root : parent).children.size ();
			}

			@Override
			public Node child (Node parent, int index) {
				return (parent == null ? root : parent).children.get (index);
			}
		});
		M3WindowView<Node> rows = new M3WindowView<> (flat);
		rows.move (18, 5);
		assertEquals ("n18", rows.get (0).name);
		assertEquals (2, rows.size (), "a window over a flattening view");
		flat.collapse (3);
		assertEquals (20, flat.size (), "collapsing a never-expanded row changes nothing");
		assertEquals (0, flat.materializedCount ());
	}

	@Test
	public void clockCacheMatchesReferenceModel () {
		Random random = new Random (3L);
		int capacity = 7;
		M3BoundedCache<Integer, String> cache = new M3BoundedCache<> (capacity);
		List<int []> evicted = new ArrayList<> ();
		cache.evictor ((k, v) -> evicted.add (new int [] {k}));
		/* reference CLOCK: slot arrays, hand, reference bits */
		Integer [] slotKey = new Integer [capacity];
		boolean [] ref = new boolean [capacity];
		Map<Integer, String> values = new java.util.HashMap<> ();
		int hand = 0;
		long evictions = 0;
		for (int step = 0; step < 5000; step++) {
			int key = random.nextInt (15);
			int op = random.nextInt (5);
			if (op < 2) {
				String expected = values.get (key);
				assertEquals (expected, cache.get (key));
				if (expected != null) ref [slot (slotKey, key)] = true;
			} else if (op < 4) {
				String value = "v" + step;
				String previous = cache.put (key, value);
				assertEquals (values.get (key), previous);
				if (previous != null) {
					ref [slot (slotKey, key)] = true;
				} else {
					int free = slot (slotKey, null);
					if (free < 0) {
						while (ref [hand]) {
							ref [hand] = false;
							hand = (hand + 1) % capacity;
						}
						free = hand;
						hand = (hand + 1) % capacity;
						assertEquals ((int) slotKey [free], evicted.get (evicted.size () - 1) [0], "step " + step);
						values.remove (slotKey [free]);
						evictions++;
					}
					slotKey [free] = key;
					ref [free] = false;
				}
				values.put (key, value);
			} else {
				assertEquals (values.remove (key), cache.remove (key));
				int s = slot (slotKey, key);
				if (s >= 0) {
					slotKey [s] = null;
					ref [s] = false;
				}
			}
			assertEquals (values.size (), cache.size ());
			assertEquals (values.containsKey (key), cache.containsKey (key));
		}
		assertEquals (evictions, cache.evictions ());
		assertTrue (cache.hits () > 0 && cache.misses () > 0);
		assertEquals (capacity, cache.capacity ());
		cache.clear ();
		assertEquals (0, cache.size ());
		assertNull (cache.get (1));
		assertThrows (NullPointerException.class, () -> cache.put (1, null));
		assertThrows (IllegalArgumentException.class, () -> new M3BoundedCache<Integer, String> (0));
		assertThrows (IllegalArgumentException.class, () -> new M3BoundedCache<Integer, String> ((1 << 29) + 1));
		M3BoundedCache<Integer, String> wide = new M3BoundedCache<> (100);
		for (int i = 0; i < 70; i++) wide.put (i, "w" + i);
		assertEquals (70, wide.size (), "free slots beyond the first bit word");
		assertEquals ("w69", wide.get (69));
		M3BoundedCache<Integer, String> plain = new M3BoundedCache<> (1);
		plain.put (1, "a");
		plain.put (2, "b");
		assertEquals ("b", plain.get (2), "eviction without an evictor");
	}

	/** Lowest slot holding {@code key} (or the lowest free slot for {@code null}), -1 when none. */
	private static int slot (Integer [] slots, Integer key) {
		for (int i = 0; i < slots.length; i++) if (key == null ? slots [i] == null : key.equals (slots [i])) return i;
		return -1;
	}

	@Test
	public void clockCacheCollisions () {
		M3BoundedCache<Long, Long> cache = new M3BoundedCache<> (64);
		for (long i = 0; i < 64; i++) cache.put ((i << 32) | i, i);
		for (long i = 0; i < 64; i += 2) assertEquals (i, cache.remove ((i << 32) | i));
		for (long i = 1; i < 64; i += 2) assertEquals (i, cache.get ((i << 32) | i), "backward-shift deletion keeps colliding chains");
		assertNull (cache.remove (5L));
		assertEquals (32, cache.size ());
	}
}
