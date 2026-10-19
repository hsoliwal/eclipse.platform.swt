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

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Contract and seeded differential tests of {@link M3TextExtentCache}. */
public class Test_org_eclipse_swt_internal_m3_collections_M3TextExtentCache {
	private int calls;

	private long measure (int label, int font) {
		calls++;
		return M3TextExtentCache.pack (label * 7 + font, 10 + font);
	}

	@Test
	void packsWidthAndHeight () {
		long extent = M3TextExtentCache.pack (1234, -5);
		assertEquals (1234, M3TextExtentCache.width (extent));
		assertEquals (-5, M3TextExtentCache.height (extent));
		assertEquals (-7, M3TextExtentCache.width (M3TextExtentCache.pack (-7, 3)));
	}

	@Test
	void measuresEachLabelAndFontOnce () {
		M3TextExtentCache cache = new M3TextExtentCache (16);
		assertEquals (M3TextExtentCache.pack (21, 10), cache.extent (3, 0, this::measure));
		assertEquals (21, cache.width (3, 0, this::measure));
		assertEquals (M3TextExtentCache.pack (22, 11), cache.extent (3, 1, this::measure));
		assertEquals (2, calls);
		assertEquals (2, cache.misses ());
		assertEquals (1, cache.hits ());
		assertEquals (2, cache.size ());
		assertEquals (-1L, cache.peek (4, 0));
		assertEquals (M3TextExtentCache.pack (21, 10), cache.peek (3, 0));
		assertEquals (16, cache.maxEntries ());
		assertTrue (cache.retainedBytes () > 32 * 17);
	}

	@Test
	void widestMeasuresDistinctLabelsOnly () {
		M3TextExtentCache cache = new M3TextExtentCache (64);
		int [] rows = new int [10_000];
		for (int i = 0; i < rows.length; i++) rows [i] = i % 5;
		assertEquals (4 * 7 + 2, cache.widest (rows, 0, rows.length, 2, this::measure));
		assertEquals (5, calls);
		assertEquals (0, cache.widest (rows, 3, 3, 2, this::measure));
		assertEquals (2 * 7 + 2, cache.widest (rows, 1, 3, 2, this::measure));
		assertEquals (5, calls);
		assertThrows (IndexOutOfBoundsException.class, () -> cache.widest (rows, 2, 1, 0, this::measure));
	}

	@Test
	void invalidationByLabelFontAndAll () {
		M3TextExtentCache cache = new M3TextExtentCache (64);
		for (int font = 0; font < 6; font++) {
			for (int label = 0; label < 3; label++) cache.extent (label, font, this::measure);
		}
		cache.invalidateLabel (1);
		for (int font = 0; font < 6; font++) {
			assertEquals (-1L, cache.peek (1, font));
			assertNotEquals (-1L, cache.peek (0, font));
		}
		cache.invalidateLabel (9);
		assertEquals (12, cache.size ());
		cache.invalidateFont (3);
		assertEquals (10, cache.size ());
		assertEquals (-1L, cache.peek (0, 3));
		assertNotEquals (-1L, cache.peek (2, 4));
		cache.invalidateFont (42);
		assertEquals (10, cache.size ());
		cache.extent (5, 3, this::measure);
		cache.invalidateLabel (5);
		assertEquals (-1L, cache.peek (5, 3));
		cache.invalidateAll ();
		assertEquals (0, cache.size ());
		assertEquals (-1L, cache.peek (2, 4));
		cache.extent (2, 4, this::measure);
		assertEquals (1, cache.size ());
	}

	@Test
	void clockGivesRecentlyInsertedEntriesASecondChance () {
		M3TextExtentCache cache = new M3TextExtentCache (2);
		cache.extent (1, 0, this::measure);
		cache.extent (2, 0, this::measure);
		cache.extent (3, 0, this::measure);
		assertEquals (1, cache.evictions ());
		int survivor = cache.peek (1, 0) != -1L ? 1 : 2;
		assertEquals (-1L, cache.peek (3 - survivor, 0));
		cache.extent (4, 0, this::measure);
		assertEquals (-1L, cache.peek (survivor, 0));
		assertNotEquals (-1L, cache.peek (3, 0));
		assertNotEquals (-1L, cache.peek (4, 0));
		assertEquals (2, cache.size ());
	}

	@Test
	void refusesInvalidArguments () {
		assertThrows (IllegalArgumentException.class, () -> new M3TextExtentCache (0));
		assertThrows (IllegalArgumentException.class, () -> new M3TextExtentCache ((1 << 29) + 1));
		M3TextExtentCache cache = new M3TextExtentCache (1);
		assertThrows (IllegalArgumentException.class, () -> cache.extent (-1, 0, this::measure));
		cache.extent (0, Integer.MIN_VALUE, this::measure);
		assertNotEquals (-1L, cache.peek (0, Integer.MIN_VALUE));
	}

	@Test
	void seededDifferentialKeepsOnlyCorrectExtents () {
		for (int seed = 1; seed <= 10; seed++) {
			Random random = new Random (seed);
			M3TextExtentCache cache = new M3TextExtentCache (1 + seed % 9);
			for (int step = 0; step < 5000; step++) {
				int label = random.nextInt (24), font = random.nextInt (7), op = random.nextInt (20);
				if (op == 0) {
					cache.invalidateLabel (label);
					for (int f = 0; f < 7; f++) assertEquals (-1L, cache.peek (label, f));
				} else if (op == 1) {
					cache.invalidateFont (font);
					for (int l = 0; l < 24; l++) assertEquals (-1L, cache.peek (l, font));
				} else if (op == 2 && step % 50 == 0) {
					cache.invalidateAll ();
				} else {
					assertEquals (M3TextExtentCache.pack (label * 7 + font, 10 + font), cache.extent (label, font, this::measure));
				}
				assertTrue (cache.size () <= cache.maxEntries ());
				if (step % 61 == 0) verify (cache);
			}
			verify (cache);
		}
	}

	private static void verify (M3TextExtentCache cache) {
		int present = 0;
		for (int label = 0; label < 24; label++) {
			for (int font = 0; font < 7; font++) {
				long extent = cache.peek (label, font);
				if (extent != -1L) {
					present++;
					assertEquals (M3TextExtentCache.pack (label * 7 + font, 10 + font), extent);
				}
			}
		}
		assertEquals (cache.size (), present);
	}
}
