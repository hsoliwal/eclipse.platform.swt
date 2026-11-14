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
package org.eclipse.swt.tests.junit;

import org.eclipse.swt.internal.residency.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Contract and seeded differential tests of {@link TreePathTrie}. */
public class Test_org_eclipse_swt_internal_residency_TreePathTrie {

	@Test
	void pathsSharePrefixesAndAnswerTheirShape () {
		TreePathTrie trie = new TreePathTrie ();
		int a = trie.child (TreePathTrie.ROOT, 3);
		int ab = trie.child (a, 7);
		int abc = trie.intern (new int [] {9, 3, 7, 1}, 1, 4);
		assertEquals (ab, trie.parent (abc));
		assertEquals (abc, trie.find (new int [] {3, 7, 1}, 0, 3));
		assertEquals (TreePathTrie.NOT_FOUND, trie.find (new int [] {3, 8, 1}, 0, 3));
		assertEquals (TreePathTrie.ROOT, trie.find (new int [0], 0, 0));
		assertEquals (a, trie.child (TreePathTrie.ROOT, 3));
		assertEquals (3, trie.size ());
		assertEquals (3, trie.depth (abc));
		assertEquals (1, trie.segment (abc));
		assertEquals (TreePathTrie.NOT_FOUND, trie.parent (TreePathTrie.ROOT));
		assertEquals (0, trie.depth (TreePathTrie.ROOT));
		assertTrue (trie.hasChildren (TreePathTrie.ROOT));
		assertTrue (trie.hasChildren (ab));
		assertFalse (trie.hasChildren (abc));
		int [] segments = new int [5];
		assertEquals (3, trie.segments (abc, segments));
		assertArrayEquals (new int [] {3, 7, 1, 0, 0}, segments);
		assertEquals (a, trie.ancestorAt (abc, 1));
		assertEquals (TreePathTrie.ROOT, trie.ancestorAt (abc, 0));
		assertEquals (abc, trie.ancestorAt (abc, 3));
		assertThrows (IndexOutOfBoundsException.class, () -> trie.ancestorAt (abc, 4));
		assertThrows (IndexOutOfBoundsException.class, () -> trie.segments (abc, new int [2]));
		assertTrue (trie.startsWith (abc, a));
		assertTrue (trie.startsWith (abc, abc));
		assertFalse (trie.startsWith (a, abc));
		int other = trie.intern (new int [] {3, 2}, 0, 2);
		assertFalse (trie.startsWith (abc, other));
		assertEquals (a, trie.commonAncestor (abc, other));
		assertEquals (TreePathTrie.ROOT, trie.commonAncestor (abc, trie.child (TreePathTrie.ROOT, -5)));
		assertEquals (ab, trie.commonAncestor (ab, abc));
		assertEquals ("/3/7/1", trie.appendPath (abc, new StringBuilder (), "/").toString ());
		assertNull (trie.names ());
		assertTrue (trie.retainedBytes () > 0);
	}

	@Test
	void flagsMarkAncestorsInOnePass () {
		TreePathTrie trie = new TreePathTrie ();
		int deep = trie.intern (new int [] {0, 1, 2, 3}, 0, 4);
		int sibling = trie.intern (new int [] {0, 1, 5}, 0, 3);
		trie.flag (deep, TreePathTrie.MATCH, true);
		assertEquals (3, trie.markAncestors (deep, TreePathTrie.MATCH_ANCESTOR));
		assertEquals (0, trie.markAncestors (sibling, TreePathTrie.MATCH_ANCESTOR));
		assertEquals (0, trie.markAncestors (TreePathTrie.ROOT, TreePathTrie.MATCH_ANCESTOR));
		assertFalse (trie.flag (TreePathTrie.ROOT, TreePathTrie.MATCH_ANCESTOR));
		List<Integer> marked = new ArrayList<> ();
		assertEquals (3, trie.forEachFlagged (TreePathTrie.MATCH_ANCESTOR, marked::add));
		assertTrue (marked.contains (trie.parent (deep)));
		assertFalse (marked.contains (deep));
		trie.flag (sibling, TreePathTrie.EXPANDED | TreePathTrie.SELECTED, true);
		assertTrue (trie.flag (sibling, TreePathTrie.EXPANDED | TreePathTrie.SELECTED));
		trie.flag (sibling, TreePathTrie.SELECTED, false);
		assertFalse (trie.flag (sibling, TreePathTrie.EXPANDED | TreePathTrie.SELECTED));
		assertEquals (TreePathTrie.EXPANDED, trie.flags (sibling));
		trie.clearFlags (TreePathTrie.MATCH_ANCESTOR | TreePathTrie.EXPANDED);
		assertEquals (0, trie.forEachFlagged (TreePathTrie.MATCH_ANCESTOR, marked::add));
		assertEquals (0, trie.flags (sibling));
		assertEquals (5, trie.forEachFlagged (0, id -> { }));
		trie.remove (sibling);
		assertEquals (4, trie.forEachFlagged (0, id -> { }));
	}

	@Test
	void removeDropsTheSubtreeAndReusesIds () {
		TreePathTrie trie = new TreePathTrie ();
		int parent = trie.child (TreePathTrie.ROOT, 1);
		int first = trie.child (parent, 1), middle = trie.child (parent, 2), last = trie.child (parent, 3);
		int grandchild = trie.child (middle, 9);
		trie.child (grandchild, 4);
		trie.remove (middle);
		assertFalse (trie.contains (middle));
		assertFalse (trie.contains (grandchild));
		assertEquals (TreePathTrie.NOT_FOUND, trie.find (parent, 2));
		assertEquals (first, trie.find (parent, 1));
		assertEquals (last, trie.find (parent, 3));
		trie.remove (last);
		trie.remove (first);
		assertEquals (1, trie.size ());
		int again = trie.child (parent, 8);
		assertTrue (again == first || again == last || again == middle || again == grandchild);
		trie.child (parent, 9);
		trie.remove (parent);
		assertEquals (0, trie.size ());
		assertThrows (IllegalArgumentException.class, () -> trie.remove (TreePathTrie.ROOT));
		assertThrows (IllegalArgumentException.class, () -> trie.depth (parent));
		assertThrows (IllegalArgumentException.class, () -> trie.child (99, 1));
		assertFalse (trie.contains (-1));
		trie.child (TreePathTrie.ROOT, 1);
		trie.clear ();
		assertEquals (0, trie.size ());
		assertFalse (trie.contains (1));
	}

	@Test
	void namedSegmentsHoldOneReferencePerPath () {
		LabelPool pool = new LabelPool (0);
		TreePathTrie trie = new TreePathTrie (pool);
		int file = trie.internPath ("/S:/m3-scratch//ui-interning/data/REPEAT_RATIOS.tsv", '/');
		assertEquals (5, trie.depth (file));
		assertEquals ("/S:/m3-scratch/ui-interning/data/REPEAT_RATIOS.tsv", trie.appendPath (file, new StringBuilder (), "/").toString ());
		int data = trie.parent (file);
		assertEquals (data, trie.internPath ("S:/m3-scratch/ui-interning/data", '/'));
		int sibling = trie.childNamed (data, "fs-sample-1m.tsv");
		assertEquals (sibling, trie.childNamed (data, "fs-sample-1m.tsv"));
		assertEquals (sibling, trie.findNamed (data, "fs-sample-1m.tsv"));
		assertEquals (TreePathTrie.NOT_FOUND, trie.findNamed (data, "missing"));
		assertEquals (TreePathTrie.NOT_FOUND, trie.findNamed (file, "fs-sample-1m.tsv"));
		int dataLabel = pool.find ("data");
		assertEquals (1, pool.refCount (dataLabel));
		int byLabel = trie.child (sibling, dataLabel);
		assertEquals (2, pool.refCount (dataLabel));
		assertEquals (byLabel, trie.child (sibling, dataLabel));
		assertEquals (2, pool.refCount (dataLabel));
		trie.remove (sibling);
		assertEquals (1, pool.refCount (dataLabel));
		assertEquals (LabelPool.NOT_FOUND, pool.find ("fs-sample-1m.tsv"));
		String longName = "n".repeat (200);
		int deep = trie.internPath (longName + "/" + longName, '/');
		assertEquals (2, trie.depth (deep));
		assertEquals (trie.child (TreePathTrie.ROOT, pool.find (longName)), trie.parent (deep));
		assertSame (pool, trie.names ());
		trie.remove (deep);
		trie.clear ();
		assertEquals (0, pool.size ());
		assertThrows (IllegalStateException.class, () -> new TreePathTrie ().childNamed (0, "x"));
		assertThrows (IllegalStateException.class, () -> new TreePathTrie ().findNamed (0, "x"));
		assertThrows (IllegalStateException.class, () -> new TreePathTrie ().internPath ("x", '/'));
		assertThrows (IllegalArgumentException.class, () -> trie.child (0, 12345));
	}

	@Test
	void wideNodesGrowTheEdgeTable () {
		TreePathTrie trie = new TreePathTrie ();
		int parent = trie.child (TreePathTrie.ROOT, 0);
		for (int i = 0; i < 50_000; i++) assertEquals (i + 2, trie.child (parent, i));
		for (int i = 0; i < 50_000; i++) assertEquals (i + 2, trie.find (parent, i));
		assertEquals (50_001, trie.size ());
	}

	@Test
	void seededDifferentialAgainstAPathMap () {
		for (int seed = 1; seed <= 12; seed++) {
			Random random = new Random (seed);
			TreePathTrie trie = new TreePathTrie ();
			Map<List<Integer>, Integer> model = new HashMap<> ();
			for (int step = 0; step < 3000; step++) {
				List<Integer> path = randomPath (random);
				int [] segments = path.stream ().mapToInt (Integer::intValue).toArray ();
				if (random.nextInt (5) == 0) {
					int id = trie.find (segments, 0, segments.length);
					if (id > TreePathTrie.ROOT) {
						trie.remove (id);
						model.keySet ().removeIf (p -> p.size () >= path.size () && p.subList (0, path.size ()).equals (path));
					}
				} else {
					int id = trie.intern (segments, 0, segments.length);
					for (int d = 1; d <= path.size (); d++) model.put (new ArrayList<> (path.subList (0, d)), trie.ancestorAt (id, d));
				}
				if (step % 101 == 0) verify (trie, model, random);
			}
			verify (trie, model, random);
		}
	}

	private static List<Integer> randomPath (Random random) {
		List<Integer> path = new ArrayList<> ();
		for (int d = 1 + random.nextInt (5); d > 0; d--) path.add (random.nextInt (4));
		return path;
	}

	private static void verify (TreePathTrie trie, Map<List<Integer>, Integer> model, Random random) {
		assertEquals (model.size (), trie.size ());
		for (Map.Entry<List<Integer>, Integer> entry : model.entrySet ()) {
			int [] segments = entry.getKey ().stream ().mapToInt (Integer::intValue).toArray ();
			assertEquals ((int) entry.getValue (), trie.find (segments, 0, segments.length));
			int [] back = new int [segments.length];
			trie.segments (entry.getValue (), back);
			assertArrayEquals (segments, back);
		}
		List<Integer> probe = randomPath (random);
		int [] segments = probe.stream ().mapToInt (Integer::intValue).toArray ();
		assertEquals (model.containsKey (probe), trie.find (segments, 0, segments.length) != TreePathTrie.NOT_FOUND);
	}
}
