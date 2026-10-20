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
 * com.m3.collections.M3ViewPatchTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Brief rule 24 for the views: {@link M3SparseExtents}, {@link M3FlatteningView} and {@link M3WindowView} take
 * {@link M3Patch} runs as their only structural update, in O(changed + log n), with no rebuild; the window's move is a
 * delta of the entries entering and leaving; a hidden window makes no host call and answers one patch on show.
 * Every bound is read from the view's own work counter.
 */
public class M3ViewPatchTest {
	private static final System.Logger LOG = System.getLogger ("com.m3.collections.M3ViewPatchTest");
	private static final int [] LOGS = {10, 14, 17, 20};

	@Test
	public void extentsEditsAndOneHeightCostLogM () {
		long [] head = new long [LOGS.length], height = new long [LOGS.length], patch = new long [LOGS.length];
		for (int s = 0; s < LOGS.length; s++) {
			int n = 1 << LOGS [s];
			M3SparseExtents x = new M3SparseExtents (20);
			x.reset (n);
			long expected = 0;
			for (int row = 0; row < n; row++) {
				x.setExtent (row, 10 + (row & 7));
				expected += 10 + (row & 7);
			}
			assertEquals (expected, x.totalExtent ());
			long w = x.work ();
			x.insert (0, 1);
			x.remove (0, 1);
			head [s] = x.work () - w;
			w = x.work ();
			x.setExtent (n / 2, 33);
			height [s] = x.work () - w;
			expected += 33 - (10 + ((n / 2) & 7));
			assertEquals (expected, x.totalExtent (), "one row height changed the total by its delta");
			w = x.work ();
			x.setEstimate (25);
			assertEquals (w, x.work (), "an estimate change visits nothing");
			M3Patch p = M3Patch.of (n, new int [] {3 * n / 4, n / 4}, new int [] {8, 8}, new int [] {0, n / 2}, new int [] {8, 8});
			w = x.work ();
			x.apply (p);
			patch [s] = x.work () - w;
			assertEquals (n, x.rowCount ());
			assertEquals (n - 16, x.measuredCount ());
			assertEquals (expected - sum (n / 4, 8) - sum (3 * n / 4, 8) + 16 * 25, x.totalExtent (), "removed rows took their extents; new rows are the estimate");
			int log = LOGS [s];
			assertTrue (head [s] <= 2 * (8 * log + 16), "head insert + remove: " + head [s]);
			assertTrue (height [s] <= 4 * log + 8, "one height: " + height [s]);
			assertTrue (patch [s] <= 4 * (8 * log + 16) + 16, "four runs: " + patch [s]);
		}
		assertTrue (head [3] <= 2.5 * head [0] && height [3] <= 2.5 * height [0] && patch [3] <= 2.5 * patch [0],
				"2^20 vs 2^10 grows with log n only");
		LOG.log (System.Logger.Level.INFO, "PATCHWORK extents head insert+remove " + Arrays.toString (head) + " one height " + Arrays.toString (height)
				+ " 4-run patch " + Arrays.toString (patch) + " at n=2^10,2^14,2^17,2^20 measured rows");
	}

	private static long sum (int from, int count) {
		long s = 0;
		for (int row = from; row < from + count; row++) s += 10 + (row & 7);
		return s;
	}

	/* a mutable tree of Integer nodes for the flattening view */
	static final class Tree implements M3FlatteningView.Source<Integer> {
		final Map<Integer, List<Integer>> children = new HashMap<> ();
		final List<Integer> roots = new ArrayList<> ();

		List<Integer> list (Integer parent) {
			return parent == null ? roots : children.computeIfAbsent (parent, k -> new ArrayList<> ());
		}

		@Override
		public int childCount (Integer parent) {
			return list (parent).size ();
		}

		@Override
		public Integer child (Integer parent, int index) {
			return list (parent).get (index);
		}

		void flatten (Integer parent, Set<Integer> expanded, List<Integer> out) {
			for (Integer node : list (parent)) {
				out.add (node);
				if (expanded.contains (node)) flatten (node, expanded, out);
			}
		}
	}

	@Test
	public void flatteningViewTakesPatchesLikeARebuild () {
		for (int seed = 1; seed <= 30; seed++) {
			Random random = new Random (seed);
			Tree tree = new Tree ();
			int next = 0;
			for (int i = 0; i < 20 + random.nextInt (40); i++) tree.roots.add (next++);
			for (int r : new ArrayList<> (tree.roots)) for (int c = 0; c < random.nextInt (5); c++) tree.list (r).add (next++);
			M3FlatteningView<Integer> view = new M3FlatteningView<> (tree);
			Set<Integer> expanded = new HashSet<> ();
			for (int row = view.size () - 1; row >= 0; row--) {
				if (random.nextInt (3) == 0 && tree.childCount (view.get (row)) > 0) {
					expanded.add (view.get (row));
					view.expand (row);
				}
			}
			for (int step = 0; step < 6; step++) {
				Integer parent = null;
				int parentRow = -1;
				if (random.nextBoolean () && !expanded.isEmpty ()) {
					List<Integer> rows = new ArrayList<> (view);
					parent = new ArrayList<> (expanded).get (random.nextInt (expanded.size ()));
					parentRow = rows.indexOf (parent);
					if (parentRow < 0) continue;
				}
				List<Integer> list = tree.list (parent);
				long [] before = list.stream ().mapToLong (Integer::longValue).toArray ();
				for (int edit = 0; edit < 4; edit++) {
					int kind = random.nextInt (3);
					if (kind == 0 && !list.isEmpty ()) list.remove (random.nextInt (list.size ()));
					else if (kind == 1) list.add (random.nextInt (list.size () + 1), next++);
					else if (list.size () > 1) list.add (random.nextInt (list.size ()), list.remove (random.nextInt (list.size ())));
				}
				long [] after = list.stream ().mapToLong (Integer::longValue).toArray ();
				view.apply (parentRow, M3ListDiff.diff (before, after));
				Set<Integer> alive = new HashSet<> ();
				List<Integer> oracle = new ArrayList<> ();
				tree.flatten (null, expanded, oracle);
				alive.addAll (oracle);
				expanded.retainAll (alive);
				oracle.clear ();
				tree.flatten (null, expanded, oracle);
				assertEquals (oracle, new ArrayList<> (view), "seed " + seed + " step " + step);
			}
		}
	}

	@Test
	public void flatteningPatchWorkIsLogarithmic () {
		int [] logs = {10, 14, 17};
		long [] work = new long [logs.length];
		for (int s = 0; s < logs.length; s++) {
			int n = 1 << logs [s];
			Tree tree = new Tree ();
			for (int i = 0; i < n; i++) {
				tree.roots.add (i);
				tree.list (i).add (n + i);
			}
			M3FlatteningView<Integer> view = new M3FlatteningView<> (tree);
			for (int row = n - 1; row >= 0; row--) view.expand (row);
			assertEquals (2 * n, view.size ());
			long [] before = new long [n];
			for (int i = 0; i < n; i++) before [i] = i;
			tree.roots.subList (n / 4, n / 4 + 8).clear ();
			for (int i = 0; i < 8; i++) tree.roots.add (10 + i, 3 * n + i);
			M3Patch p = M3Patch.of (n, new int [] {n / 4}, new int [] {8}, new int [] {10}, new int [] {8});
			long w = view.work ();
			view.apply (-1, p);
			work [s] = view.work () - w;
			assertEquals (2 * n - 16 + 8, view.size ());
			assertEquals (Integer.valueOf (3 * n), view.get (20), "row 20: the first inserted root (roots 0..9 take two rows each)");
			assertEquals (Integer.valueOf (n - 1), view.get (view.size () - 2));
			assertTrue (work [s] <= 2 * (16 * logs [s] + 40), "two runs: " + work [s]);
		}
		assertTrue (work [2] <= 2.5 * work [0], Arrays.toString (work));
		LOG.log (System.Logger.Level.INFO, "PATCHWORK flattening 2-run root patch (8 expanded removed, 8 inserted) " + Arrays.toString (work) + " at n=2^10,2^14,2^17 expanded roots");
	}

	/* a host plane that mirrors the window entries and counts its calls */
	static final class Plane implements M3Patch.Target {
		final List<Integer> rows = new ArrayList<> ();
		final M3WindowView<Integer> window;
		int calls;

		Plane (M3WindowView<Integer> window) {
			this.window = window;
		}

		@Override
		public void remove (int index, int count) {
			calls++;
			rows.subList (index, index + count).clear ();
		}

		@Override
		public void insert (int index, int count) {
			calls++;
			for (int i = 0; i < count; i++) rows.add (index + i, window.get (index + i));
		}

		@Override
		public void update (int index, int count) {
			calls++;
			for (int i = 0; i < count; i++) rows.set (index + i, window.get (index + i));
		}

		void follow (M3WindowPlan plan) {
			if (plan.replaced ()) {
				rows.clear ();
				for (int i = 0; i < window.size (); i++) rows.add (window.get (i));
				return;
			}
			for (int i = 0; i < plan.retiredHead (); i++) rows.remove (0);
			for (int i = 0; i < plan.retiredTail (); i++) rows.remove (rows.size () - 1);
			for (int i = plan.insertedHead () - 1; i >= 0; i--) rows.add (0, window.get (i));
			while (rows.size () < window.size ()) rows.add (window.get (rows.size ()));
		}
	}

	@Test
	public void windowFollowsPatchesAndMovesWithItsStates () {
		for (int seed = 1; seed <= 40; seed++) {
			Random random = new Random (seed);
			List<Integer> source = new ArrayList<> ();
			int next = 0;
			for (int i = 0; i < 50 + random.nextInt (100); i++) source.add (next++);
			M3WindowView<Integer> window = new M3WindowView<> (source);
			Plane plane = new Plane (window);
			window.host (plane);
			plane.follow (window.move (random.nextInt (source.size ()), 1 + random.nextInt (20)));
			Map<Integer, Integer> flags = new HashMap<> ();
			for (int step = 0; step < 60; step++) {
				if (random.nextInt (4) == 0) {
					int first = random.nextInt (source.size () + 1);
					plane.follow (window.move (first, Math.max (0, Math.min (source.size () - first, 1 + random.nextInt (24)))));
					flags.keySet ().retainAll (window);
				} else {
					long [] before = source.stream ().mapToLong (Integer::longValue).toArray ();
					for (int edit = 0; edit < 1 + random.nextInt (4); edit++) {
						int kind = random.nextInt (3);
						if (kind == 0 && source.size () > 1) source.remove (random.nextInt (source.size ()));
						else if (kind == 1) source.add (random.nextInt (source.size () + 1), next++);
						else if (source.size () > 2) source.add (random.nextInt (source.size ()), source.remove (random.nextInt (source.size ())));
					}
					long [] after = source.stream ().mapToLong (Integer::longValue).toArray ();
					M3Patch patch = M3ListDiff.diff (before, after);
					window.apply (patch);
					for (int m = 0; m < patch.moveCount (); m++) flags.remove ((int) before [patch.moveFrom (m)]);
					flags.keySet ().retainAll (window);
				}
				assertEquals (new ArrayList<> (window), plane.rows, "seed " + seed + " step " + step);
				for (int i = 0; i < window.size (); i++) {
					Integer row = window.get (i);
					assertEquals (flags.getOrDefault (row, 0).intValue (), window.state (i), "seed " + seed + " step " + step + " state follows its row");
				}
				if (window.size () > 0) {
					int i = random.nextInt (window.size ()), flag = 1 << random.nextInt (4);
					window.flag (i, flag, true);
					flags.merge (window.get (i), flag, (a, b) -> a | b);
				}
			}
		}
	}

	@Test
	public void windowMoveAndPatchWorkIgnoreTheSourceSize () {
		long [] moveWork = new long [LOGS.length], patchWork = new long [LOGS.length];
		for (int s = 0; s < LOGS.length; s++) {
			int n = 1 << LOGS [s];
			List<Integer> source = new ArrayList<> (n);
			for (int i = 0; i < n; i++) source.add (i);
			M3WindowView<Integer> window = new M3WindowView<> (source);
			window.move (n / 2, 64);
			long w = window.work ();
			window.move (n / 2 + 3, 64);
			window.move (n / 2 + 3, 70);
			moveWork [s] = window.work () - w;
			Plane plane = new Plane (window);
			window.host (plane);
			plane.rows.addAll (window);
			source.subList (10, 20).clear ();
			source.add (n / 2 + 10, -1);
			w = window.work ();
			window.apply (M3Patch.of (n, new int [] {10}, new int [] {10}, new int [] {n / 2 + 10}, new int [] {1}));
			patchWork [s] = window.work () - w;
			assertEquals (1, plane.calls, "the run before the window only shifts it; the run inside is one host call");
			assertEquals (n / 2 + 3 - 10, window.first ());
			assertTrue (moveWork [s] <= 3 + 6 + 128, "a move costs the entries entering and leaving: " + moveWork [s]);
			assertTrue (patchWork [s] <= 2 + 70 / 2 + 1, "a run costs O(1) plus the shorter side of the window: " + patchWork [s]);
		}
		assertEquals (moveWork [0], moveWork [3], "independent of n");
		assertEquals (patchWork [0], patchWork [3], "independent of n");
		LOG.log (System.Logger.Level.INFO, "PATCHWORK window move(+3 rows, +6 rows) " + Arrays.toString (moveWork) + " 2-run patch " + Arrays.toString (patchWork)
				+ " at n=2^10,2^14,2^17,2^20");
	}

	@Test
	public void hiddenWindowFoldsTenThousandPatchesAndShowsOnce () {
		for (int seed = 1; seed <= 6; seed++) {
			Random random = new Random (seed);
			List<Integer> source = new ArrayList<> ();
			int next = 0;
			for (int i = 0; i < 2_000; i++) source.add (next++);
			M3WindowView<Integer> window = new M3WindowView<> (source);
			Plane plane = new Plane (window);
			window.host (plane);
			plane.follow (window.move (900, 40));
			window.hide ();
			long w = window.work ();
			for (int p = 0; p < 10_000; p++) {
				int size = source.size ();
				M3Patch patch;
				if (random.nextBoolean () && size > 100) {
					int at = 850 + random.nextInt (120);
					source.remove (at);
					patch = M3Patch.removal (size, at, 1);
				} else {
					int at = 850 + random.nextInt (120);
					source.add (at, next++);
					patch = M3Patch.insertion (size, at, 1);
				}
				window.apply (patch);
				if (p % 2_500 == 0) {
					int first = Math.max (0, Math.min (source.size () - 40, window.first () + random.nextInt (9) - 4));
					window.move (first, 40);
				}
			}
			window.move (Math.min (window.first (), source.size () - 40), 40);
			assertEquals (0, plane.calls, "no host call while hidden");
			long hiddenWork = window.work () - w;
			assertTrue (hiddenWork <= 10_000L * (2 + 40), "O(1) per run plus lane moves bounded by the window: " + hiddenWork);
			M3Patch shown = window.show ();
			assertTrue (shown.removeRuns () <= 1 && shown.insertRuns () <= 1, "one remove run and one insert run");
			shown.applyTo (plane);
			assertTrue (plane.calls <= 2, "exactly one patch application after show: " + plane.calls);
			assertEquals (new ArrayList<> (window), plane.rows, "seed " + seed + ": the host shows the current window");
			M3Patch again = window.show ();
			assertTrue (again.isEmpty (), "a visible window has nothing pending");
			if (seed == 1) LOG.log (System.Logger.Level.INFO, "PATCHWORK hidden window: 10^4 single-run patches, work " + hiddenWork
					+ ", host calls while hidden 0, show patch " + shown.removedCount () + " removed + " + shown.insertedCount () + " inserted");
		}
	}

	@Test
	public void showWithoutChangesIsEmptyAndHiddenMovesFold () {
		List<Integer> source = new ArrayList<> ();
		for (int i = 0; i < 100; i++) source.add (i);
		M3WindowView<Integer> window = new M3WindowView<> (source);
		Plane plane = new Plane (window);
		window.host (plane);
		plane.follow (window.move (10, 20));
		window.hide ();
		assertTrue (window.isHidden ());
		window.hide ();
		assertTrue (window.show ().isEmpty ());
		assertFalse (window.isHidden ());
		window.hide ();
		window.move (15, 20);
		window.move (80, 20);
		window.move (82, 30);
		M3Patch shown = window.show ();
		shown.applyTo (plane);
		assertEquals (new ArrayList<> (window), plane.rows);
		window.hide ();
		window.move (84, 20);
		source.subList (90, 100).clear ();
		window.apply (M3Patch.removal (100, 90, 10));
		window.show ().applyTo (plane);
		assertEquals (new ArrayList<> (window), plane.rows);
		window.hide ();
		source.add (0, -1);
		window.apply (M3Patch.insertion (90, 0, 1));
		assertEquals (85, window.first ());
		window.show ().applyTo (plane);
		assertEquals (new ArrayList<> (window), plane.rows);
	}
}
