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
 * com.m3.collections.M3PatchComposeTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Rule 26 additions to the core: {@link M3Patch#compose} (one patch per frame from several), the in-order export
 * {@link M3OrderStatisticForest#order} (row runs to slots) and content updates through {@link M3Patch.Target#update}.
 * A composed patch must leave every surviving entry exactly where the two patches applied in turn leave it.
 */
public class M3PatchComposeTest {
	private static final long NEW = -1L;

	/* applies p to list, entries kept by p keep their value, inserted ones become NEW */
	private static List<Long> run (M3Patch p, List<Long> list) {
		p.applyTo (new M3Patch.Target () {
			@Override
			public void remove (int index, int count) {
				list.subList (index, index + count).clear ();
			}

			@Override
			public void insert (int index, int count) {
				for (int i = 0; i < count; i++) list.add (index + i, NEW);
			}
		});
		return list;
	}

	private static List<Long> list (long [] keys) {
		List<Long> out = new ArrayList<> ();
		for (long k : keys) out.add (k);
		return out;
	}

	private static long [] edit (long [] before, Random random, long [] next) {
		List<Long> l = list (before);
		for (int e = 0; e < 1 + random.nextInt (8); e++) {
			int kind = random.nextInt (3);
			if (kind == 0 && !l.isEmpty ()) l.remove (random.nextInt (l.size ()));
			else if (kind == 1) l.add (random.nextInt (l.size () + 1), next [0]++);
			else if (l.size () > 1) l.add (random.nextInt (l.size ()), l.remove (random.nextInt (l.size ())));
		}
		return l.stream ().mapToLong (Long::longValue).toArray ();
	}

	@Test
	public void composeLeavesSurvivorsWhereTwoPatchesInTurnLeaveThem () {
		for (int seed = 1; seed <= 300; seed++) {
			Random random = new Random (seed);
			long [] next = {1_000};
			long [] s0 = new long [random.nextInt (40)];
			for (int i = 0; i < s0.length; i++) s0 [i] = i;
			long [] s1 = edit (s0, random, next), s2 = edit (s1, random, next);
			M3Patch a = M3ListDiff.diff (s0, s1), b = M3ListDiff.diff (s1, s2);
			M3Patch c = M3Patch.compose (a, b);
			assertEquals (s0.length, c.oldSize (), "seed " + seed);
			assertEquals (s2.length, c.newSize (), "seed " + seed);
			assertEquals (0, c.moveCount ());
			List<Long> sequential = run (b, run (a, list (s0)));
			List<Long> composed = run (c, list (s0));
			assertEquals (sequential, composed, "seed " + seed);
			assertEquals (sequential.stream ().filter (v -> v != NEW).count (), c.keptCount (), "seed " + seed);
			for (int i = 0; i < s2.length; i++) if (composed.get (i) != NEW) assertEquals (s2 [i], composed.get (i).longValue ());
			M3Patch d = M3Patch.compose (c, M3ListDiff.diff (s2, s2));
			assertEquals (composed, run (d, list (s0)), "composing with an empty patch changes nothing");
		}
	}

	@Test
	public void composeOfRunPatchesAndSizeCheck () {
		M3Patch a = M3Patch.of (10, new int [] {6, 1}, new int [] {2, 3}, new int [] {0, 4}, new int [] {1, 2});
		M3Patch b = M3Patch.of (a.newSize (), new int [] {0}, new int [] {2}, new int [] {5}, new int [] {4});
		List<Long> base = new ArrayList<> ();
		for (long i = 0; i < 10; i++) base.add (i);
		assertEquals (run (b, run (a, new ArrayList<> (base))), run (M3Patch.compose (a, b), new ArrayList<> (base)));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.compose (a, a), "first must end where then starts");
		M3Patch none = M3Patch.of (0, new int [0], new int [0], new int [0], new int [0]);
		M3Patch fill = M3Patch.insertion (0, 0, 3);
		M3Patch both = M3Patch.compose (none, fill);
		assertEquals (3, both.insertedCount ());
		assertEquals (0, both.keptCount ());
		M3Patch clear = M3Patch.compose (fill, M3Patch.removal (3, 0, 3));
		assertTrue (clear.isEmpty (), "insert then remove the same rows is nothing");
	}

	@Test
	public void orderExportsRowRunsAsSlots () {
		for (int seed = 1; seed <= 20; seed++) {
			Random random = new Random (seed);
			M3PatchCostTest.Forest f = new M3PatchCostTest.Forest (400);
			TreeMap<Integer, Integer> oracle = new TreeMap<> ();
			for (int id = 0; id < 300; id++) {
				int key = random.nextInt (5_000);
				if (oracle.containsKey (key)) continue;
				f.insert (id, key, 0);
				oracle.put (key, id);
			}
			f.index.shift (-1, 2_500, 7);
			TreeMap<Integer, Integer> shifted = new TreeMap<> ();
			oracle.forEach ((k, id) -> shifted.put (k >= 2_500 ? k + 7 : k, id));
			int [] out = new int [50];
			for (int probe = 0; probe < 30; probe++) {
				int from = random.nextInt (5_200), count = random.nextInt (50);
				long w = f.index.work ();
				int n = f.index.order (-1, from, count, out);
				List<Integer> expected = new ArrayList<> (shifted.tailMap (from).values ());
				expected = expected.subList (0, Math.min (count, expected.size ()));
				assertEquals (expected.size (), n);
				for (int i = 0; i < n; i++) assertEquals (expected.get (i).intValue (), out [i]);
				assertTrue (f.index.work () - w <= 4 * 14 + 2 * count + 8, "O(log k + count)");
			}
		}
	}

	@Test
	public void windowUpdatesReachTheHostOrTheDirtySpan () {
		List<Integer> source = new ArrayList<> ();
		for (int i = 0; i < 50; i++) source.add (i);
		M3WindowView<Integer> window = new M3WindowView<> (source);
		M3ViewPatchTest.Plane plane = new M3ViewPatchTest.Plane (window);
		window.host (plane);
		window.move (10, 10);
		plane.rows.addAll (window);
		window.flag (3, 1, true);
		source.set (13, -13);
		window.update (13, 1);
		source.set (40, -40);
		window.update (40, 1);
		assertEquals (new ArrayList<> (window), plane.rows, "the host re-read the updated entry");
		assertEquals (1, plane.calls, "an update outside the window costs the host nothing");
		assertTrue (window.flag (3, 1), "an update keeps the entry's state");
		window.hide ();
		source.set (12, -12);
		window.update (12, 1);
		source.set (17, -17);
		window.update (17, 2);
		M3Patch shown = window.show ();
		assertEquals (7, shown.insertedCount (), "the span from the first to the last updated entry");
		shown.applyTo (plane);
		assertEquals (new ArrayList<> (window), plane.rows);
		M3SparseExtents extents = new M3SparseExtents (10);
		extents.reset (5);
		extents.setExtent (2, 30);
		extents.update (2, 1);
		assertEquals (30, extents.extentOf (2), "an update keeps a measurement until the row is measured again");
	}
}
