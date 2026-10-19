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

import org.eclipse.swt.internal.m3.collections.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Contract and seeded differential tests of {@link M3LabelPool}. */
public class Test_org_eclipse_swt_internal_m3_collections_M3LabelPool {

	@Test
	void emptyStringIsPermanentIdZero () {
		M3LabelPool pool = new M3LabelPool (0);
		assertEquals (M3LabelPool.EMPTY, pool.intern (""));
		assertEquals (M3LabelPool.EMPTY, pool.intern (new char [3], 1, 0));
		assertEquals (M3LabelPool.EMPTY, pool.find (""));
		pool.acquire (M3LabelPool.EMPTY);
		pool.release (M3LabelPool.EMPTY);
		pool.release (M3LabelPool.EMPTY);
		assertEquals ("", pool.text (M3LabelPool.EMPTY));
		assertEquals (0, pool.length (M3LabelPool.EMPTY));
		assertEquals (0, pool.hash (M3LabelPool.EMPTY));
		assertEquals (1, pool.refCount (M3LabelPool.EMPTY));
		assertEquals (0, pool.size ());
		assertEquals (0, pool.misses ());
	}

	@Test
	void repeatedTextSharesOneIdAndCountsReferences () {
		M3LabelPool pool = new M3LabelPool ();
		int a = pool.intern ("JAVA File");
		int b = pool.intern (new String ("JAVA File"));
		int c = pool.intern ("xJAVA Filex".toCharArray (), 1, 9);
		assertEquals (a, b);
		assertEquals (a, c);
		assertEquals (3, pool.refCount (a));
		assertEquals (1, pool.misses ());
		assertEquals (2, pool.hits ());
		assertEquals (1, pool.size ());
		assertEquals (a, pool.find ("JAVA File"));
		assertEquals (M3LabelPool.NOT_FOUND, pool.find ("JAVA file"));
		assertEquals (3, pool.refCount (a));
		assertEquals (4096, pool.maxCached ());
	}

	@Test
	void releasedLabelsStayCachedUntilTheBoundEvictsTheOldest () {
		M3LabelPool pool = new M3LabelPool (2);
		List<Integer> evicted = new ArrayList<> ();
		pool.setEvictionListener (evicted::add);
		int a = pool.intern ("a"), b = pool.intern ("b"), c = pool.intern ("c"), d = pool.intern ("d");
		pool.release (a);
		pool.release (b);
		assertEquals (2, pool.cachedCount ());
		assertEquals (a, pool.find ("a"));
		assertEquals (0, pool.refCount (a));
		pool.release (c);
		assertEquals (List.of (a), evicted);
		assertEquals (M3LabelPool.NOT_FOUND, pool.find ("a"));
		assertFalse (pool.isLive (a));
		assertEquals (b, pool.intern ("b"));
		assertEquals (1, pool.cachedCount ());
		pool.release (d);
		pool.release (b);
		assertEquals (List.of (a, c), evicted);
		assertEquals (2, pool.evictions ());
		pool.setEvictionListener (null);
		int e = pool.intern ("e"), f = pool.intern ("f");
		assertTrue (e == a || e == c, "evicted ids are reused");
		assertTrue (f == a || f == c);
		assertEquals ("e", pool.text (e));
		assertEquals (4, pool.size ());
	}

	@Test
	void zeroBoundEvictsOnRelease () {
		M3LabelPool pool = new M3LabelPool (0);
		int id = pool.intern ("gone");
		pool.text (id);
		assertEquals (1, pool.facadeCount ());
		pool.release (id);
		assertEquals (0, pool.facadeCount ());
		assertEquals (M3LabelPool.NOT_FOUND, pool.find ("gone"));
		assertEquals (4, pool.garbageBytes ());
		assertEquals (0, pool.textBytes ());
	}

	@Test
	void unlinkingFromTheMiddleHeadAndTailOfTheReleasedList () {
		M3LabelPool pool = new M3LabelPool (10);
		int [] ids = new int [5];
		for (int i = 0; i < ids.length; i++) ids [i] = pool.intern ("l" + i);
		for (int id : ids) pool.release (id);
		pool.acquire (ids [2]);
		pool.acquire (ids [0]);
		pool.acquire (ids [4]);
		assertEquals (2, pool.cachedCount ());
		pool.acquire (ids [1]);
		pool.acquire (ids [3]);
		assertEquals (0, pool.cachedCount ());
		for (int id : ids) assertEquals (1, pool.refCount (id));
	}

	@Test
	void utf16AndLatin1TextAnswerWithoutFacades () {
		M3LabelPool pool = new M3LabelPool ();
		String wide = "नमस्ते world";
		int w = pool.intern (wide), l = pool.intern ("café");
		assertEquals (wide.length (), pool.length (w));
		assertEquals ('न', pool.charAt (w, 0));
		assertEquals ('é', pool.charAt (l, 3));
		char [] buffer = new char [wide.length () + 2];
		assertEquals (wide.length (), pool.getChars (w, buffer, 2));
		assertEquals (wide, new String (buffer, 2, wide.length ()));
		assertTrue (pool.contentEquals (w, new StringBuilder (wide)));
		assertFalse (pool.contentEquals (w, wide + "!"));
		assertFalse (pool.contentEquals (l, "cafe"));
		assertEquals (wide.hashCode (), pool.hash (w));
		assertEquals (0, pool.facadeCount ());
		assertEquals (wide, pool.text (w));
		assertSame (pool.text (w), pool.text (w));
		assertEquals ("café", pool.text (l));
		assertEquals (2, pool.facadeCount ());
		assertEquals (wide.length () * 2 + 4, pool.textBytes ());
		assertEquals (w, pool.intern (wide.toCharArray (), 0, wide.length ()));
		assertThrows (IndexOutOfBoundsException.class, () -> pool.charAt (l, 4));
		assertThrows (IndexOutOfBoundsException.class, () -> pool.getChars (w, new char [3], 0));
		assertThrows (IndexOutOfBoundsException.class, () -> pool.intern (new char [2], 1, 2));
	}

	@Test
	void compareOrdersLikeString () {
		M3LabelPool pool = new M3LabelPool ();
		String [] texts = {"apple", "app", "apples", "bā", "ba", "B", ""};
		for (String a : texts) {
			for (String b : texts) {
				assertEquals (Integer.signum (a.compareTo (b)), Integer.signum (pool.compare (pool.intern (a), pool.intern (b))), a + " vs " + b);
			}
		}
	}

	@Test
	void equalHashCodesWithDifferentTextGetDifferentIds () {
		M3LabelPool pool = new M3LabelPool ();
		assertEquals ("Aa".hashCode (), "BB".hashCode ());
		int aa = pool.intern ("Aa"), bb = pool.intern ("BB");
		assertNotEquals (aa, bb);
		assertEquals (bb, pool.find ("BB"));
		assertEquals (aa, pool.intern ("Aa".toCharArray (), 0, 2));
		assertEquals ("BB", pool.text (bb));
	}

	@Test
	void facadesAreDroppedOnTrimAndCountedInRetainedBytes () {
		M3LabelPool pool = new M3LabelPool ();
		int id = pool.intern ("facade");
		long before = pool.retainedBytes ();
		String text = pool.text (id);
		assertEquals (before + 24 + 16 + 6, pool.retainedBytes ());
		pool.trimFacades ();
		assertEquals (0, pool.facadeCount ());
		assertEquals (before, pool.retainedBytes ());
		assertNotSame (text, pool.text (id));
		assertEquals ("", pool.text (M3LabelPool.EMPTY));
	}

	@Test
	void arenaCompactsWhenGarbageOutweighsLiveText () {
		M3LabelPool pool = new M3LabelPool (0);
		String big = "x".repeat (3000);
		int keep = pool.intern ("keep क");
		int first = pool.intern (big + 1), second = pool.intern (big + 2);
		pool.release (first);
		pool.release (second);
		assertEquals (6002, pool.garbageBytes ());
		int third = pool.intern (big + 3);
		assertEquals (1, pool.compactions ());
		assertEquals (0, pool.garbageBytes ());
		assertEquals ("keep क", pool.text (keep));
		assertEquals (big + 3, pool.text (third));
		assertEquals (12 + 3001, pool.textBytes ());
		pool.compact ();
		assertEquals (2, pool.compactions ());
		assertEquals (big + 3, pool.text (third));
	}

	@Test
	void arenaGrowsInsteadOfCompactingWhileLiveTextDominates () {
		M3LabelPool pool = new M3LabelPool (0);
		String big = "y".repeat (5000);
		int live = pool.intern (big + big), dead = pool.intern (big + "dead");
		pool.release (dead);
		int next = pool.intern (big + "next");
		assertEquals (0, pool.compactions ());
		assertEquals (5004, pool.garbageBytes ());
		assertEquals (big + big, pool.text (live));
		assertEquals (big + "next", pool.text (next));
		M3LabelPool small = new M3LabelPool (0);
		int a = small.intern ("z".repeat (5000)), b = small.intern ("w".repeat (100));
		small.release (a);
		small.intern ("v".repeat (100_000));
		assertEquals (1, small.compactions ());
		assertEquals ("w".repeat (100), small.text (b));
	}

	@Test
	void manyLabelsRehashAndStayFindable () {
		M3LabelPool pool = new M3LabelPool ();
		int [] ids = new int [20_000];
		for (int i = 0; i < ids.length; i++) ids [i] = pool.intern ("row " + i);
		for (int i = 0; i < ids.length; i++) {
			assertEquals (ids [i], pool.find ("row " + i));
			assertEquals ("row " + i, pool.text (ids [i]));
		}
		assertEquals (ids.length, pool.size ());
		assertTrue (pool.retainedBytes () > 20_000L * 4);
		M3LabelPool holes = new M3LabelPool (0);
		for (int i = 0; i < 16; i++) holes.intern ("h" + i);
		holes.release (holes.find ("h3"));
		holes.release (holes.find ("h9"));
		for (int i = 0; i < 3; i++) holes.intern ("after rehash " + i);
		assertEquals (M3LabelPool.NOT_FOUND, holes.find ("h3"));
		for (int i = 0; i < 3; i++) assertEquals ("after rehash " + i, holes.text (holes.find ("after rehash " + i)));
		for (int i = 0; i < 16; i++) assertEquals (i != 3 && i != 9, holes.find ("h" + i) != M3LabelPool.NOT_FOUND);
		assertEquals (17, holes.size ());
	}

	@Test
	void invalidUseIsRefused () {
		M3LabelPool pool = new M3LabelPool (1);
		assertThrows (IllegalArgumentException.class, () -> new M3LabelPool (-1));
		assertThrows (NullPointerException.class, () -> pool.intern ((String) null));
		assertThrows (IllegalArgumentException.class, () -> pool.text (-1));
		assertThrows (IllegalArgumentException.class, () -> pool.release (7));
		int id = pool.intern ("once");
		pool.release (id);
		assertThrows (IllegalStateException.class, () -> pool.release (id));
		pool.release (pool.intern ("evicts once"));
		assertThrows (IllegalArgumentException.class, () -> pool.acquire (id));
		assertFalse (pool.isLive (-3));
		assertFalse (pool.isLive (99));
	}

	@Test
	void clearForgetsLabelsButKeepsCounters () {
		M3LabelPool pool = new M3LabelPool ();
		int id = pool.intern ("x");
		pool.intern ("x");
		pool.clear ();
		assertFalse (pool.isLive (id));
		assertEquals (0, pool.size ());
		assertEquals (1, pool.hits ());
		assertEquals (M3LabelPool.NOT_FOUND, pool.find ("x"));
		assertEquals (1, pool.intern ("y"));
	}

	@Test
	void canonicalIdsReplaceTextComparison () {
		Map<String, Long> canonical = new HashMap<> ();
		M3LabelPool pool = new M3LabelPool (8, text -> canonical.computeIfAbsent (text, t -> canonical.size () + 1L));
		int a = pool.intern ("alpha");
		assertEquals (a, pool.intern ("alpha".toCharArray (), 0, 5));
		assertEquals (a, pool.find ("alpha"));
		assertEquals ("alpha".hashCode (), pool.hash (a));
		assertEquals ("alpha", pool.text (a));
		assertNotEquals (a, pool.intern ("beta"));
		M3LabelPool broken = new M3LabelPool (8, text -> 0L);
		assertThrows (IllegalStateException.class, () -> broken.intern ("x"));
	}

	@Test
	void seededDifferentialAgainstAnLruModel () {
		String [] universe = universe ();
		for (int seed = 1; seed <= 12; seed++) {
			Random random = new Random (seed);
			int bound = seed % 4;
			M3LabelPool pool = new M3LabelPool (bound);
			Map<String, Integer> refs = new HashMap<> ();
			LinkedHashMap<String, Boolean> released = new LinkedHashMap<> ();
			for (int step = 0; step < 4000; step++) {
				String text = universe [random.nextInt (universe.length)];
				int count = refs.getOrDefault (text, 0);
				if (count > 0 && random.nextInt (3) == 0) {
					pool.release (pool.find (text));
					refs.put (text, count - 1);
					if (count == 1) released.put (text, Boolean.TRUE);
					if (released.size () > bound) released.remove (released.keySet ().iterator ().next ());
				} else {
					int id = random.nextBoolean () ? pool.intern (text) : pool.intern (text.toCharArray (), 0, text.length ());
					assertEquals (text, pool.text (id));
					refs.put (text, count + 1);
					released.remove (text);
				}
				if (step % 97 == 0) verify (pool, universe, refs, released, seed, step);
			}
			verify (pool, universe, refs, released, seed, -1);
		}
	}

	private static void verify (M3LabelPool pool, String [] universe, Map<String, Integer> refs, Map<String, Boolean> released, int seed, int step) {
		for (String text : universe) {
			int id = pool.find (text);
			int count = refs.getOrDefault (text, 0);
			boolean present = count > 0 || released.containsKey (text);
			assertEquals (present, id != M3LabelPool.NOT_FOUND, "seed " + seed + " step " + step + " " + text);
			if (present) {
				assertEquals (count, pool.refCount (id));
				assertTrue (pool.contentEquals (id, text));
			}
		}
		assertEquals (released.size (), pool.cachedCount ());
	}

	private static String [] universe () {
		List<String> texts = new ArrayList<> (List.of ("Aa", "BB", "AaAa", "BBBB", "AaBB", "BBAa", "JAVA File", "File folder", "1 KB"));
		for (int i = 0; i < 40; i++) texts.add ((i % 3 == 0 ? "अ" : "n") + i);
		return texts.toArray (new String [0]);
	}
}
