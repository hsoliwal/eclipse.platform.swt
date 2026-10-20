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

import static org.junit.jupiter.api.Assertions.*;

import java.text.*;
import java.util.*;

import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3LabelTable (public model API, brief rule 26), headless. A randomized run checks the table against a
 * map oracle (text to id, id to references) through interning, releases, revivals and recycles, including the
 * backward-shift deletions of the open-addressing table.
 */
public class Test_M3LabelTable {

	@Test
	public void test_equalTextsShareOneId () {
		M3LabelTable labels = new M3LabelTable ();
		int a = labels.intern ("alpha");
		assertEquals (a, labels.intern (new StringBuilder ("alpha")));
		assertEquals (a, labels.intern ("xalphax".toCharArray (), 1, 5));
		assertNotEquals (a, labels.intern ("beta"));
		assertEquals (3, labels.references (a));
		assertEquals (2, labels.size ());
		assertEquals (2, labels.maxId ());
		assertSame (labels.text (a), labels.text (labels.intern ("alpha")), "one String per label");
		assertEquals (0, labels.intern ((CharSequence) null));
		assertEquals (0, labels.intern (""));
		assertEquals (0, labels.intern (new char [3], 1, 0));
		assertEquals ("", labels.text (0));
		assertEquals (0, labels.references (0));
		labels.acquire (0);
		labels.release (0);
		assertEquals (2, labels.size (), "the empty label counts nothing");
	}

	@Test
	public void test_releasedIdIsReusedOnlyAfterRecycle () {
		M3LabelTable labels = new M3LabelTable ();
		int a = labels.intern ("alpha");
		labels.acquire (a);
		labels.release (a);
		labels.release (a);
		assertEquals (0, labels.size ());
		assertEquals ("alpha", labels.text (a).toString (), "readable until recycle");
		assertEquals (a, labels.intern ("alpha"), "a re-intern revives the id");
		labels.release (a);
		int b = labels.intern ("beta");
		assertNotEquals (a, b, "no reuse before recycle");
		long epoch = labels.reuseEpoch ();
		labels.recycle ();
		assertEquals (epoch + 1, labels.reuseEpoch ());
		assertThrows (IllegalArgumentException.class, () -> labels.text (a));
		assertEquals (a, labels.intern ("gamma"), "the freed id is reused after recycle");
		labels.recycle ();
		assertEquals (epoch + 1, labels.reuseEpoch (), "nothing freed: the epoch stays");
		labels.release (b);
		labels.acquire (b);
		labels.recycle ();
		assertEquals ("beta", labels.text (b).toString (), "a label acquired again before recycle lives");
		assertEquals (epoch + 1, labels.reuseEpoch ());
	}

	@Test
	public void test_charsLengthAndErrors () {
		M3LabelTable labels = new M3LabelTable ();
		int a = labels.intern ("hello");
		char [] buffer = new char [8];
		assertEquals (5, labels.getChars (a, buffer, 2));
		assertEquals ("hello", new String (buffer, 2, 5));
		assertEquals (-5, labels.getChars (a, buffer, 4), "too short: nothing copied");
		assertEquals (0, labels.getChars (0, buffer, 8));
		assertEquals (5, labels.length (a));
		assertThrows (IllegalArgumentException.class, () -> labels.getChars (a, null, 0));
		assertThrows (IllegalArgumentException.class, () -> labels.getChars (a, buffer, -1));
		assertThrows (IllegalArgumentException.class, () -> labels.getChars (a, buffer, 9));
		assertThrows (IllegalArgumentException.class, () -> labels.intern (null, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> labels.intern (buffer, -1, 1));
		assertThrows (IllegalArgumentException.class, () -> labels.intern (buffer, 0, -1));
		assertThrows (IllegalArgumentException.class, () -> labels.intern (buffer, 4, 5));
		assertThrows (IllegalArgumentException.class, () -> labels.text (-1));
		assertThrows (IllegalArgumentException.class, () -> labels.text (7));
		assertThrows (IllegalArgumentException.class, () -> labels.acquire (7));
		assertThrows (IllegalArgumentException.class, () -> labels.release (7));
		assertThrows (IllegalArgumentException.class, () -> labels.references (7));
		labels.release (a);
		assertThrows (IllegalArgumentException.class, () -> labels.release (a), "no reference left");
	}

	@Test
	public void test_collateAndCompare () {
		M3LabelTable labels = new M3LabelTable ();
		int lower = labels.intern ("apple"), upper = labels.intern ("Banana"), accent = labels.intern ("éclair");
		Collator english = Collator.getInstance (Locale.ENGLISH);
		assertTrue (labels.collate (lower, upper, english) < 0, "apple before Banana by collation");
		assertTrue (labels.compare (lower, upper) > 0, "Banana before apple by chars");
		assertTrue (labels.collate (accent, upper, english) > 0);
		assertEquals (0, labels.collate (upper, upper, english));
		assertEquals (0, labels.collate (0, 0, english));
		assertTrue (labels.collate (0, lower, english) < 0, "the empty label first");
		CollationKey key = labels.collationKey (lower, english);
		assertSame (key, labels.collationKey (lower, english), "cached per label");
		Collator french = Collator.getInstance (Locale.FRENCH);
		assertNotSame (key, labels.collationKey (lower, french), "a new collator drops the cache");
		assertThrows (IllegalArgumentException.class, () -> labels.collationKey (lower, null));
		assertThrows (IllegalArgumentException.class, () -> labels.collate (lower, lower, null));
		assertThrows (IllegalArgumentException.class, () -> labels.collate (9, 9, english));
		for (int i = 0; i < 200; i++) labels.intern ("grow " + i);
		assertNotNull (labels.collationKey (labels.intern ("grow 150"), french), "the key cache grows with the ids");
		labels.release (lower);
		labels.recycle ();
		int reused = labels.intern ("zebra");
		assertEquals (lower, reused);
		assertEquals ("zebra", labels.collationKey (reused, french).getSourceString (), "a freed key is not served again");
	}

	@Test
	public void test_randomAgainstMapOracle () {
		M3LabelTable labels = new M3LabelTable ();
		Map<String, Integer> idOf = new HashMap<> ();
		Map<Integer, Integer> refs = new HashMap<> ();
		Set<Integer> waiting = new HashSet<> ();
		Random random = new Random (26);
		for (int step = 0; step < 200_000; step++) {
			int op = random.nextInt (10);
			String text = "t" + random.nextInt (3000);
			if (op < 5) {
				int id = random.nextBoolean () ? labels.intern (text) : labels.intern (("<" + text + ">").toCharArray (), 1, text.length ());
				Integer known = idOf.get (text);
				if (known != null) assertEquals (known.intValue (), id, text);
				idOf.put (text, id);
				refs.merge (id, 1, Integer::sum);
				waiting.remove (id);
			} else if (op < 9) {
				Integer id = idOf.get (text);
				if (id == null || refs.get (id) == 0) continue;
				labels.release (id);
				if (refs.merge (id, -1, Integer::sum) == 0) waiting.add (id);
			} else {
				labels.recycle ();
				for (int id : waiting) {
					idOf.values ().remove (id);
					refs.remove (id);
				}
				waiting.clear ();
			}
		}
		int live = 0;
		for (Map.Entry<String, Integer> entry : idOf.entrySet ()) {
			assertEquals (entry.getKey (), labels.text (entry.getValue ()).toString ());
			assertEquals (refs.get (entry.getValue ()).intValue (), labels.references (entry.getValue ()));
			if (refs.get (entry.getValue ()) > 0) live++;
		}
		assertEquals (live, labels.size ());
		assertTrue (labels.maxId () <= 3000 + 1, "ids are reused: " + labels.maxId ());
	}
}
