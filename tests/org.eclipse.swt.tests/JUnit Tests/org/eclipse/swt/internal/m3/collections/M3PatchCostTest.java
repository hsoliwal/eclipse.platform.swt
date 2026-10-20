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
 * com.m3.collections.M3PatchCostTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Brief rule 24: an {@link M3Patch} reaches the order-statistic forest and the tree topology in O(changed + log n).
 * The forest is checked against a sorted-map oracle under random scripts (insert, remove, weight change, lazy key
 * shift, range removal), then its work counter is read for single edits at n = 2^10 .. 2^20: the visited-node count
 * stays within a fixed multiple of log2 n plus the removed entries and does not grow with n beyond the logarithm.
 */
public class M3PatchCostTest {
	private static final int P = -1;

	static final class Forest {
		final M3OrderStatisticForest index = new M3OrderStatisticForest ();
		final int [] keys;
		final long [] weights;

		Forest (int capacity) {
			keys = new int [capacity];
			weights = new long [capacity];
			index.ensureCapacity (capacity);
			index.lanes (keys, weights);
		}

		void insert (int id, int key, long weight) {
			keys [id] = key;
			weights [id] = weight;
			index.insert (P, id);
		}
	}

	@Test
	public void forestMatchesSortedOracleUnderRandomScripts () {
		for (int seed = 1; seed <= 12; seed++) {
			Random random = new Random (seed);
			int capacity = 600;
			Forest f = new Forest (capacity);
			TreeMap<Integer, Integer> oracle = new TreeMap<> ();
			Map<Integer, Long> weight = new HashMap<> ();
			List<Integer> free = new ArrayList<> ();
			for (int id = 0; id < capacity; id++) free.add (id);
			int [] out = new int [capacity];
			for (int step = 0; step < 1_500; step++) {
				int op = random.nextInt (10);
				if (op < 4 && !free.isEmpty ()) {
					int key = random.nextInt (2_000);
					if (oracle.containsKey (key)) continue;
					int id = free.remove (free.size () - 1);
					long w = random.nextInt (5);
					f.insert (id, key, w);
					oracle.put (key, id);
					weight.put (id, w);
				} else if (op == 4 && !oracle.isEmpty ()) {
					int key = pick (oracle, random);
					int id = oracle.remove (key);
					f.index.remove (P, id);
					weight.remove (id);
					free.add (id);
				} else if (op == 5 && !oracle.isEmpty ()) {
					int id = oracle.get (pick (oracle, random));
					long delta = random.nextInt (7) - 3;
					if (weight.get (id) + delta < 0) continue;
					f.weights [id] += delta;
					f.index.adjust (P, id, delta);
					weight.put (id, weight.get (id) + delta);
				} else if (op <= 7) {
					int from = random.nextInt (2_100), delta = random.nextInt (9) - 4;
					if (delta == 0 || delta < 0 && !oracle.subMap (from + delta, from).isEmpty ()) continue;
					f.index.shift (P, from, delta);
					TreeMap<Integer, Integer> next = new TreeMap<> ();
					oracle.forEach ((k, id) -> next.put (k >= from ? k + delta : k, id));
					oracle = next;
				} else if (op == 8) {
					int from = random.nextInt (2_100), to = from + random.nextInt (60);
					int expected = oracle.subMap (from, to).size ();
					assertEquals (expected, f.index.countRange (P, from, to));
					int removed = f.index.removeRange (P, from, to, out);
					assertEquals (expected, removed);
					// copy keys first: a TreeMap entry may take its successor's key when a node is deleted
					List<Integer> doomed = new ArrayList<> (oracle.subMap (from, to).keySet ());
					for (int i = 0; i < doomed.size (); i++) {
						int key = doomed.get (i), id = oracle.remove (key);
						assertEquals (id, out [i], "removed in key order");
						assertEquals (key, f.keys [id], "a removed id keeps its absolute key");
						weight.remove (id);
						free.add (id);
					}
				} else {
					f.index.flush (P);
					for (Map.Entry<Integer, Integer> e : oracle.entrySet ()) assertEquals (e.getKey (), f.keys [e.getValue ()]);
				}
				verify (f, oracle, weight, random, seed + "/" + step);
			}
		}
	}

	private static int pick (TreeMap<Integer, Integer> oracle, Random random) {
		Integer key = oracle.ceilingKey (random.nextInt (2_000));
		return key != null ? key : oracle.firstKey ();
	}

	private static void verify (Forest f, TreeMap<Integer, Integer> oracle, Map<Integer, Long> weight, Random random, String where) {
		M3OrderStatisticForest x = f.index;
		assertEquals (oracle.size (), x.size (P), where);
		long total = 0;
		for (Map.Entry<Integer, Integer> e : oracle.entrySet ()) {
			assertEquals (e.getKey (), x.key (e.getValue ()), where);
			total += weight.get (e.getValue ());
		}
		assertEquals (total, x.total (P), where);
		assertTrue (x.treeHeight (P) <= 1.45 * (Math.log (oracle.size () + 2) / Math.log (2)) + 1, where + " balanced");
		for (int probe = 0; probe < 6; probe++) {
			int key = random.nextInt (2_200) - 100;
			assertEquals (id (oracle.get (key)), x.find (P, key), where);
			assertEquals (id (oracle.lowerKey (key) == null ? null : oracle.get (oracle.lowerKey (key))), x.lower (P, key), where);
			assertEquals (id (oracle.ceilingKey (key) == null ? null : oracle.get (oracle.ceilingKey (key))), x.ceiling (P, key), where);
			assertEquals (oracle.headMap (key).size (), x.rank (P, key), where);
			long prefix = 0;
			for (int id : oracle.headMap (key).values ()) prefix += weight.get (id);
			assertEquals (prefix, x.prefix (P, key), where);
			long row = random.nextInt (3_000);
			int limit = random.nextInt (2_200);
			int expectSeek = -1, expectOffset = -1, rank = 0;
			long before = 0, seekBefore = 0, offsetBefore = 0, slope = 1 + random.nextInt (4), offset = random.nextInt (8_000);
			int offsetRank = 0;
			for (Map.Entry<Integer, Integer> e : oracle.entrySet ()) {
				int k = e.getKey ();
				if (k < limit && k + before <= row) {
					expectSeek = e.getValue ();
					seekBefore = before;
				}
				if ((k - (long) rank) * slope + before <= offset) {
					expectOffset = e.getValue ();
					offsetBefore = before;
					offsetRank = rank;
				}
				before += weight.get (e.getValue ());
				rank++;
			}
			assertEquals (expectSeek, x.seek (P, row, limit), where + " seek");
			if (expectSeek >= 0) assertEquals (seekBefore, x.seekBefore (), where);
			assertEquals (expectOffset, x.seekOffset (P, offset, slope), where + " seekOffset");
			if (expectOffset >= 0) {
				assertEquals (offsetBefore, x.seekBefore (), where);
				assertEquals (offsetRank, x.seekRank (), where);
			}
		}
		int last = x.last (P);
		assertEquals (id (oracle.isEmpty () ? null : oracle.lastEntry ().getValue ()), last, where);
	}

	private static int id (Integer id) {
		return id == null ? -1 : id;
	}

	@Test
	public void forestEditWorkIsLogarithmic () {
		long [] shiftWork = new long [4], rangeWork = new long [4], insertWork = new long [4];
		int [] logs = {10, 14, 17, 20};
		for (int s = 0; s < logs.length; s++) {
			int n = 1 << logs [s];
			Forest f = new Forest (n + 1);
			for (int id = 0; id < n; id++) f.insert (id, id, 1);
			M3OrderStatisticForest x = f.index;
			long w = x.work ();
			x.shift (P, 0, 1);
			x.shift (P, 1, -1);
			shiftWork [s] = x.work () - w;
			int [] out = new int [64];
			w = x.work ();
			assertEquals (64, x.removeRange (P, n / 2, n / 2 + 64, out));
			rangeWork [s] = x.work () - w;
			w = x.work ();
			f.insert (n, n / 2, 1);
			insertWork [s] = x.work () - w;
			for (int id = 0; id < n; id += Math.max (1, n / 64)) {
				if (id >= n / 2 && id < n / 2 + 64) continue;
				assertEquals (id, x.key (id));
			}
			int log = logs [s];
			assertTrue (shiftWork [s] <= 2 * (1.45 * log + 2), "shift visits one path per call: " + shiftWork [s]);
			assertTrue (rangeWork [s] <= 12 * log + 64 + 32, "split, split, collect, join: " + rangeWork [s]);
			assertTrue (insertWork [s] <= 8 * log + 16, "split and join: " + insertWork [s]);
		}
		assertTrue (shiftWork [3] <= 2.5 * shiftWork [0], "2^20 vs 2^10: shift work grows with log n only");
		assertTrue (rangeWork [3] <= 2.5 * rangeWork [0], "range removal grows with log n only");
		System.getLogger ("com.m3.collections.M3PatchCostTest").log (System.Logger.Level.INFO,
				"PATCHWORK forest shift " + java.util.Arrays.toString (shiftWork) + " removeRange(64) " + java.util.Arrays.toString (rangeWork)
						+ " insert " + java.util.Arrays.toString (insertWork) + " at n=2^10,2^14,2^17,2^20");
	}

	private static final long EXPANDED = M3ItemState.EXPANDED;

	private static M3TreeTopology expandedChildren (int n) {
		M3TreeTopology t = new M3TreeTopology ();
		t.setChildCount (M3TreeTopology.ROOT, n);
		for (int id = 0; id < n; id++) {
			t.bind (id, M3TreeTopology.ROOT, id);
			t.setChildCount (id, 2);
			t.flag (id, EXPANDED, true);
		}
		return t;
	}

	@Test
	public void topologyPatchWorkIsLogarithmic () {
		int [] logs = {10, 14, 17, 20};
		long [] work = new long [logs.length];
		for (int s = 0; s < logs.length; s++) {
			int n = 1 << logs [s], c = 16;
			M3TreeTopology t = expandedChildren (n);
			assertEquals (3L * n, t.visibleRowCount ());
			M3Patch patch = M3Patch.of (n, new int [] {3 * n / 4, n / 4}, new int [] {c, c}, new int [] {10, n / 2}, new int [] {c, c});
			long generation = t.generation (), w = t.indexWork ();
			t.apply (M3TreeTopology.ROOT, patch);
			work [s] = t.indexWork () - w;
			assertTrue (t.generation () > generation);
			assertEquals (3L * n - 3 * 2 * c + 2 * c, t.visibleRowCount (), "removed expanded children took their rows; inserted ones are cold");
			assertEquals (n - 2 * c, t.materializedCount ());
			assertEquals (n, t.childCount (M3TreeTopology.ROOT));
			assertFalse (t.contains (n / 4));
			assertEquals (0, t.childIndex (0));
			assertEquals (9, t.childIndex (9));
			assertEquals (10 + c, t.childIndex (10), "after the insert at 10");
			assertEquals (n / 4 + c, t.childIndex (n / 4 + c), "the run removed at n/4 and the run inserted at 10 cancel");
			assertEquals (n - 1, t.childIndex (n - 1));
			assertTrue (work [s] <= 4 * (16 * logs [s] + 40) + 4 * c, "four runs: " + work [s]);
		}
		assertTrue (work [3] <= 2.5 * work [0], "2^20 vs 2^10: patch work grows with log n only: " + java.util.Arrays.toString (work));
		System.getLogger ("com.m3.collections.M3PatchCostTest").log (System.Logger.Level.INFO,
				"PATCHWORK topology 4-run patch (2x16 removed, 2x16 inserted) " + java.util.Arrays.toString (work) + " at n=2^10,2^14,2^17,2^20");
	}

	@Test
	public void topologyPatchMatchesTheEditedChildList () {
		for (int seed = 1; seed <= 40; seed++) {
			Random random = new Random (seed);
			int n = 30 + random.nextInt (200);
			M3TreeTopology t = new M3TreeTopology ();
			t.setChildCount (M3TreeTopology.ROOT, n);
			boolean [] expanded = new boolean [n], bound = new boolean [n];
			for (int id = 0; id < n; id++) {
				if (random.nextInt (3) != 0) continue;
				t.bind (id, M3TreeTopology.ROOT, id);
				bound [id] = true;
				if (random.nextBoolean ()) {
					t.setChildCount (id, 3);
					t.flag (id, EXPANDED, true);
					expanded [id] = true;
				}
			}
			List<Long> after = new ArrayList<> ();
			for (long k = 0; k < n; k++) after.add (k);
			for (int edit = 0; edit < 12; edit++) {
				int kind = random.nextInt (3);
				if (kind == 0 && after.size () > 1) after.remove (random.nextInt (after.size ()));
				else if (kind == 1) after.add (random.nextInt (after.size () + 1), (long) (n + edit));
				else if (after.size () > 2) after.add (random.nextInt (after.size ()), after.remove (random.nextInt (after.size ())));
			}
			long [] b = new long [n], a = new long [after.size ()];
			for (int i = 0; i < n; i++) b [i] = i;
			for (int i = 0; i < a.length; i++) a [i] = after.get (i);
			M3Patch patch = M3ListDiff.diff (b, a);
			t.apply (M3TreeTopology.ROOT, patch);
			assertEquals (a.length, t.childCount (M3TreeTopology.ROOT), "seed " + seed);
			long rows = a.length;
			for (int id = 0; id < n; id++) {
				int at = after.indexOf ((long) id);
				boolean was = bound [id];
				if (at < 0) {
					assertFalse (t.contains (id), "seed " + seed + " id " + id + " removed");
					continue;
				}
				if (!t.contains (id)) {
					assertFalse (was, "seed " + seed + " id " + id + " stays materialized");
					continue;
				}
				assertEquals (at, t.childIndex (id), "seed " + seed + " id " + id);
				if (expanded [id]) rows += 3;
			}
			assertEquals (rows, t.visibleRowCount (), "seed " + seed + ": moved expanded children keep their rows");
		}
	}

}
