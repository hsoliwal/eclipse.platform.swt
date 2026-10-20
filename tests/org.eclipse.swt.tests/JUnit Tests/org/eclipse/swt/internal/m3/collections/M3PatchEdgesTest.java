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
 * com.m3.collections.M3PatchEdgesTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Edges of the brief rule 24 paths: argument checks of the patch entry points, the overflow fallback of the lazy
 * sibling shift (which keeps the original id-ordered partial updates), the guards of the forest against corrupted
 * links, and the hidden-window corner cases.
 */
public class M3PatchEdgesTest {
	private static final int ROOT = M3TreeTopology.ROOT, P = -1;

	@Test
	public void patchRunsAreValidated () {
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [] {1}, new int [0], new int [0], new int [0]));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [0], new int [0], new int [] {1}, new int [0]));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [] {1}, new int [] {0}, new int [0], new int [0]));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [] {-1}, new int [] {1}, new int [0], new int [0]));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [] {4}, new int [] {2}, new int [0], new int [0]));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [] {1, 2}, new int [] {1, 1}, new int [0], new int [0]),
				"remove runs go from the highest index down");
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [0], new int [0], new int [] {1}, new int [] {0}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [0], new int [0], new int [] {-1}, new int [] {1}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [0], new int [0], new int [] {6}, new int [] {1}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.of (5, new int [0], new int [0], new int [] {3, 1}, new int [] {1, 1}),
				"insert runs go from the lowest index up");
		M3Patch p = M3Patch.of (5, new int [] {3, 0}, new int [] {2, 1}, new int [] {0, 2}, new int [] {1, 2});
		assertEquals (3, p.removedCount ());
		assertEquals (3, p.insertedCount ());
		assertEquals (2, p.keptCount ());
		assertArrayEquals (new long [] {9, 2, 8, 8, 3}, p.apply (new long [] {1, 2, 3, 4, 5}, new long [] {9, 2, 8, 8, 3}));
		assertTrue (M3Patch.insertion (0, 0, 1).insertedCount () == 1 && M3Patch.removal (1, 0, 1).removedCount () == 1);
	}

	@Test
	public void topologyRangeOperationsCheckTheirArguments () {
		M3TreeTopology t = new M3TreeTopology ();
		t.setChildCount (ROOT, 10);
		assertThrows (IllegalArgumentException.class, () -> t.removeCoordinates (ROOT, -1, 1));
		assertThrows (IllegalArgumentException.class, () -> t.removeCoordinates (ROOT, 0, -1));
		assertThrows (IllegalArgumentException.class, () -> t.insertCoordinates (ROOT, -1, 1));
		assertThrows (IllegalArgumentException.class, () -> t.insertCoordinates (ROOT, 0, -1));
		assertThrows (IndexOutOfBoundsException.class, () -> t.removeCoordinates (ROOT, 8, 3));
		assertThrows (IndexOutOfBoundsException.class, () -> t.insertCoordinates (ROOT, 11, 1));
		assertThrows (IllegalArgumentException.class, () -> t.removeCoordinates (7, 0, 1), "unknown parent");
		assertThrows (IllegalArgumentException.class, () -> t.insertCoordinates (7, 0, 1), "unknown parent");
		assertThrows (IllegalArgumentException.class, () -> t.apply (7, M3Patch.insertion (0, 0, 1)), "unknown parent");
		long generation = t.generation ();
		t.removeCoordinates (ROOT, 3, 0);
		t.insertCoordinates (ROOT, 3, 0);
		assertEquals (generation, t.generation (), "empty runs change nothing");
		t.bind (0, ROOT, 4);
		t.setChildCount (0, 2);
		t.bind (1, 0, 0);
		t.bind (2, 0, 1);
		t.removeCoordinates (0, 0, 1);
		assertEquals (0, t.childIndex (2));
		t.insertCoordinates (0, 0, 3);
		assertEquals (3, t.childIndex (2));
		assertEquals (4, t.childCount (0));
	}

	@Test
	public void rangeRemovalGrowsItsBufferAndPruneDropsTheMaximumCoordinate () {
		M3TreeTopology t = new M3TreeTopology ();
		t.setChildCount (ROOT, 100);
		for (int id = 0; id < 60; id++) t.bind (id, ROOT, id);
		List<Integer> discarded = new ArrayList<> ();
		t.onDiscard (discarded::add);
		t.removeCoordinates (ROOT, 5, 40);
		assertEquals (40, discarded.size (), "every materialized child of the range is discarded once");
		assertEquals (20, t.materializedCount ());
		assertEquals (5, t.childIndex (45));
		assertEquals (60, t.childCount (ROOT));
		M3TreeTopology big = new M3TreeTopology ();
		big.setChildCount (ROOT, Integer.MAX_VALUE);
		big.bind (0, ROOT, Integer.MAX_VALUE);
		big.bind (1, ROOT, 3);
		big.bind (2, ROOT, 9);
		big.setChildCount (ROOT, 5);
		assertFalse (big.contains (0), "the coordinate at Integer.MAX_VALUE is pruned too");
		assertFalse (big.contains (2));
		assertTrue (big.contains (1));
		big.clear ();
		assertEquals (0, big.materializedCount ());
		assertFalse (big.childCountKnown (ROOT));
	}

	@Test
	public void overflowUnderANonRootParentKeepsIdOrderedPartialUpdates () {
		M3TreeTopology t = new M3TreeTopology ();
		t.setChildCount (ROOT, 1);
		t.bind (0, ROOT, 0);
		t.setChildCount (0, Integer.MAX_VALUE);
		t.bind (10, 0, 3);
		t.bind (11, 0, 5);
		t.bind (12, 0, Integer.MAX_VALUE);
		long generation = t.generation ();
		assertThrows (ArithmeticException.class, () -> t.insertCoordinate (0, 0, 13));
		assertEquals (4, t.childIndex (10));
		assertEquals (6, t.childIndex (11));
		assertEquals (Integer.MAX_VALUE, t.childIndex (12));
		assertEquals (generation + 1, t.generation (), "one generation step for the partial shift");
	}

	@Test
	public void negativeOverflowFallsBackToTheIdOrderLoop () throws Exception {
		M3TreeTopology t = new M3TreeTopology ();
		t.setChildCount (ROOT, Integer.MAX_VALUE);
		t.bind (0, ROOT, Integer.MAX_VALUE);
		t.bind (1, ROOT, 0);
		t.releaseSubtree (0);
		assertEquals (-1, t.childIndex (1), "the pinned boundary behaviour: the release of the maximum coordinate shifts every sibling");
		Method shift = M3TreeTopology.class.getDeclaredMethod ("shiftSiblingIndices", int.class, int.class, int.class);
		shift.setAccessible (true);
		InvocationTargetException e = assertThrows (InvocationTargetException.class, () -> shift.invoke (t, ROOT, Integer.MIN_VALUE, Integer.MIN_VALUE));
		assertInstanceOf (ArithmeticException.class, e.getCause ());
		assertEquals (-1, t.childIndex (1));
	}

	@Test
	public void forestGuardsAgainstCorruptedLinksAndBadArguments () throws Exception {
		M3PatchCostTest.Forest f = new M3PatchCostTest.Forest (8);
		f.insert (0, 10, 1);
		f.insert (1, 20, 1);
		f.insert (2, 30, 1);
		M3OrderStatisticForest x = f.index;
		long w = x.work ();
		x.shift (P, 0, 0);
		assertEquals (w, x.work (), "a zero shift visits nothing");
		assertThrows (IllegalArgumentException.class, () -> x.removeRange (P, 0, 100, new int [1]));
		assertEquals (0, x.removeRange (P, 50, 40, new int [0]));
		assertThrows (IllegalStateException.class, () -> x.remove (P, 5), "an unlinked id");
		assertThrows (IllegalStateException.class, () -> x.adjust (P, 5, 1), "an unlinked id");
		assertThrows (IllegalStateException.class, () -> x.insert (P, 0), "a duplicate key");
		f.keys [2] = 15;
		assertThrows (IllegalStateException.class, () -> x.remove (P, 2), "a key that no longer matches its place");
		M3PatchCostTest.Forest g = new M3PatchCostTest.Forest (8);
		g.insert (0, 10, 1);
		g.insert (1, 20, 1);
		g.insert (2, 30, 1);
		M3OrderStatisticForest y = g.index;
		Field upField = M3OrderStatisticForest.class.getDeclaredField ("up");
		upField.setAccessible (true);
		int [] up = (int []) upField.get (y);
		int root = up [0] == -1 ? 0 : up [1] == -1 ? 1 : 2;
		int leaf = root == 0 ? 1 : 0;
		up [root] = leaf;
		assertThrows (IllegalStateException.class, () -> y.key (leaf), "a cycle of parent links is reported, never walked forever");
		assertThrows (IllegalStateException.class, () -> y.remove (P, leaf));
		M3PatchCostTest.Forest h = new M3PatchCostTest.Forest (8);
		h.insert (0, 10, 1);
		h.insert (1, 20, 1);
		h.insert (2, 30, 1);
		h.keys [0] = 1_000;
		assertThrows (IllegalStateException.class, () -> h.index.remove (P, 0), "a key past every other: the split leaves nothing at or after it");
	}

	@Test
	public void flatteningPatchNeedsAnExpandedRow () {
		M3ViewPatchTest.Tree tree = new M3ViewPatchTest.Tree ();
		tree.roots.add (1);
		tree.roots.add (2);
		tree.list (1).add (3);
		M3FlatteningView<Integer> view = new M3FlatteningView<> (tree);
		assertThrows (IllegalArgumentException.class, () -> view.apply (0, M3Patch.insertion (1, 0, 1)));
		view.expand (0);
		view.collapse (0);
		tree.list (1).add (4);
		view.apply (0, M3Patch.insertion (1, 1, 1));
		view.expand (0);
		assertEquals (List.of (1, 3, 4, 2), new ArrayList<> (view), "a collapsed node with known children takes the patch");
	}

	@Test
	public void windowWithoutHostAndHiddenCorners () {
		List<Integer> source = new ArrayList<> ();
		for (int i = 0; i < 30; i++) source.add (i);
		M3WindowView<Integer> window = new M3WindowView<> (source);
		window.move (5, 10);
		source.add (7, -7);
		window.apply (M3Patch.insertion (30, 7, 1));
		source.remove (8);
		window.apply (M3Patch.removal (31, 8, 1));
		assertEquals (List.of (5, 6, -7, 8, 9, 10, 11, 12, 13, 14), new ArrayList<> (window), "no host: only the lanes follow");
		M3ViewPatchTest.Plane plane = new M3ViewPatchTest.Plane (window);
		window.host (plane);
		plane.rows.addAll (window);
		window.hide ();
		source.add (9, -9);
		window.apply (M3Patch.insertion (30, 9, 1));
		M3Patch shown = window.show ();
		assertEquals (0, shown.removeRuns (), "only an insert: no remove run");
		shown.applyTo (plane);
		assertEquals (new ArrayList<> (window), plane.rows);
		window.hide ();
		source.remove (9);
		window.apply (M3Patch.removal (31, 9, 1));
		shown = window.show ();
		assertEquals (0, shown.insertRuns (), "only a removal: no insert run");
		shown.applyTo (plane);
		assertEquals (new ArrayList<> (window), plane.rows);
		M3WindowView<Integer> tail = new M3WindowView<> (source);
		M3ViewPatchTest.Plane tailPlane = new M3ViewPatchTest.Plane (tail);
		tail.host (tailPlane);
		tail.move (25, 20);
		tailPlane.rows.addAll (tail);
		tail.hide ();
		source.add (31);
		source.add (32);
		M3Patch grown = tail.show ();
		assertEquals (2, grown.insertedCount (), "a source that grew past the clamp shows its new tail");
		grown.applyTo (tailPlane);
		assertEquals (new ArrayList<> (tail), tailPlane.rows);
	}
}
