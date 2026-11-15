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

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Contract and seeded differential tests of {@link TextExtentCache}. */
public class Test_org_eclipse_swt_internal_residency_TextExtentCache {
	private int calls;

	private long measure (long label, int font) {
		calls++;
		return TextExtentCache.pack ((int) label * 7 + font, 10 + font);
	}

	@Test
	void packsWidthAndHeight () {
		long extent = TextExtentCache.pack (1234, -5);
		assertEquals (1234, TextExtentCache.width (extent));
		assertEquals (-5, TextExtentCache.height (extent));
		assertEquals (-7, TextExtentCache.width (TextExtentCache.pack (-7, 3)));
	}

	@Test
	void measuresEachLabelAndFontOnce () {
		TextExtentCache cache = new TextExtentCache (16);
		assertEquals (TextExtentCache.pack (21, 10), cache.extent (3, 0, this::measure));
		assertEquals (21, cache.width (3, 0, this::measure));
		assertEquals (TextExtentCache.pack (22, 11), cache.extent (3, 1, this::measure));
		assertEquals (2, calls);
		assertEquals (2, cache.misses ());
		assertEquals (1, cache.hits ());
		assertEquals (2, cache.size ());
		assertEquals (-1L, cache.peek (4, 0));
		assertEquals (TextExtentCache.pack (21, 10), cache.peek (3, 0));
		assertEquals (16, cache.maxEntries ());
		assertTrue (cache.retainedBytes () > 32 * 17);
	}

	@Test
	void widestMeasuresDistinctLabelsOnly () {
		TextExtentCache cache = new TextExtentCache (64);
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
		TextExtentCache cache = new TextExtentCache (64);
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
		TextExtentCache cache = new TextExtentCache (2);
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
	void immediateLabelsAreKeysToo () {
		TextExtentCache cache = new TextExtentCache (16);
		long [] labels = {ShortLabel.pack ("Error"), ShortLabel.pack ("Warning"), ShortLabel.pack ("Error"), 3L};
		TextExtentCache.Measurer measurer = (label, font) -> TextExtentCache.pack ((int) (label % 1000), font);
		int expected = 0;
		for (long label : labels) expected = Math.max (expected, (int) (label % 1000));
		assertEquals (expected, cache.widest (labels, 0, 4, 1, measurer));
		assertEquals (3, cache.misses ());
		assertNotEquals (-1L, cache.peek (labels [0], 1));
		cache.invalidateLabel (labels [0]);
		assertEquals (-1L, cache.peek (labels [0], 1));
		assertEquals (2, cache.size ());
		assertThrows (IndexOutOfBoundsException.class, () -> cache.widest (labels, 0, 5, 1, measurer));
		assertThrows (IllegalArgumentException.class, () -> cache.peek (-2L, 0));
		assertThrows (IllegalArgumentException.class, () -> cache.invalidateLabel (-2L));
	}

	@Test
	void refusesInvalidArguments () {
		assertThrows (IllegalArgumentException.class, () -> new TextExtentCache (0));
		assertThrows (IllegalArgumentException.class, () -> new TextExtentCache ((1 << 29) + 1));
		TextExtentCache cache = new TextExtentCache (1);
		assertThrows (IllegalArgumentException.class, () -> cache.extent (-1, 0, this::measure));
		cache.extent (0, Integer.MIN_VALUE, this::measure);
		assertNotEquals (-1L, cache.peek (0, Integer.MIN_VALUE));
	}

	@Test
	void seededDifferentialKeepsOnlyCorrectExtents () {
		for (int seed = 1; seed <= 10; seed++) {
			Random random = new Random (seed);
			TextExtentCache cache = new TextExtentCache (1 + seed % 9);
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
					assertEquals (TextExtentCache.pack (label * 7 + font, 10 + font), cache.extent (label, font, this::measure));
				}
				assertTrue (cache.size () <= cache.maxEntries ());
				if (step % 61 == 0) verify (cache);
			}
			verify (cache);
		}
	}

	private static void verify (TextExtentCache cache) {
		int present = 0;
		for (int label = 0; label < 24; label++) {
			for (int font = 0; font < 7; font++) {
				long extent = cache.peek (label, font);
				if (extent != -1L) {
					present++;
					assertEquals (TextExtentCache.pack (label * 7 + font, 10 + font), extent);
				}
			}
		}
		assertEquals (cache.size (), present);
	}

	@Test
	void retainedMemoryIsBoundedAcrossDistinctFonts () {
		assertAll ("font history stays bounded by cache capacity",
				() -> assertBoundedFontHistory (1),
				() -> assertBoundedFontHistory (2),
				() -> assertBoundedFontHistory (3),
				() -> assertBoundedFontHistory (7));
	}

	private void assertBoundedFontHistory (int capacity) {
		TextExtentCache cache = new TextExtentCache (capacity);
		long bound = cache.retainedBytes () + 4L * capacity;
		for (int font = 0; font < 2048; font++) cache.extent (0L, font, this::measure);
		assertEquals (capacity, cache.size ());
		assertEquals (2048, cache.misses ());
		assertEquals (2048 - capacity, cache.evictions ());
		assertTrue (cache.retainedBytes () <= bound,
				"capacity " + capacity + " retained " + cache.retainedBytes () + " bytes; bound " + bound);
	}

	@Test
	void oneFontAcrossManyLabelsKeepsMeasurementsAndStorageStable () {
		TextExtentCache cache = new TextExtentCache (257);
		long retained = cache.retainedBytes ();
		for (int round = 0; round < 32; round++) {
			for (int label = 0; label < 128; label++) cache.extent (label, -7, this::measure);
		}
		assertEquals (128, calls);
		assertEquals (128, cache.misses ());
		assertEquals (3968, cache.hits ());
		assertEquals (0, cache.evictions ());
		assertEquals (retained, cache.retainedBytes ());
		cache.invalidateLabel (57);
		assertEquals (-1L, cache.peek (57, -7));
		assertEquals (TextExtentCache.pack (385, 3), cache.peek (56, -7));
		assertEquals (127, cache.size ());
		assertEquals (128, calls);
	}

	private void churnFontsAndRemoveEntries (TextExtentCache cache) {
		for (int font = 0; font < 32; font++) cache.extent (99L, font, this::measure);
		cache.invalidateLabel (99L);
		assertEquals (0, cache.size ());
	}

	@Test
	void labelInvalidationAfterFontChurnKeepsOtherLabels () {
		TextExtentCache cache = new TextExtentCache (3);
		churnFontsAndRemoveEntries (cache);
		cache.extent (11L, -7, this::measure);
		cache.extent (11L, Integer.MIN_VALUE, this::measure);
		cache.extent (12L, 123456, this::measure);
		long misses = cache.misses (), hits = cache.hits (), evictions = cache.evictions ();
		int measured = calls;
		cache.invalidateLabel (11L);
		cache.invalidateLabel (11L);
		assertEquals (-1L, cache.peek (11L, -7));
		assertEquals (-1L, cache.peek (11L, Integer.MIN_VALUE));
		assertEquals (TextExtentCache.pack (123540, 123466), cache.peek (12L, 123456));
		assertEquals (1, cache.size ());
		assertEquals (misses, cache.misses ());
		assertEquals (hits, cache.hits ());
		assertEquals (evictions, cache.evictions ());
		assertEquals (measured, calls);
	}

	@Test
	void fontInvalidationAfterChurnAllowsFontReuse () {
		TextExtentCache cache = new TextExtentCache (5);
		churnFontsAndRemoveEntries (cache);
		for (int label = 1; label <= 3; label++) cache.extent (label, -13, this::measure);
		cache.extent (2L, 2, this::measure);
		cache.extent (1L, 37, this::measure);
		cache.invalidateFont (-13);
		cache.invalidateFont (-13);
		for (int label = 1; label <= 3; label++) assertEquals (-1L, cache.peek (label, -13));
		assertEquals (TextExtentCache.pack (16, 12), cache.peek (2L, 2));
		assertEquals (TextExtentCache.pack (44, 47), cache.peek (1L, 37));
		assertEquals (2, cache.size ());
		cache.extent (1L, -13, this::measure);
		cache.invalidateLabel (1L);
		assertEquals (-1L, cache.peek (1L, -13));
		assertEquals (-1L, cache.peek (1L, 37));
		assertEquals (TextExtentCache.pack (16, 12), cache.peek (2L, 2));
		assertEquals (1, cache.size ());
	}

	@Test
	void clearAfterFontChurnPreservesCountersAndAllowsRepopulation () {
		TextExtentCache cache = new TextExtentCache (3);
		churnFontsAndRemoveEntries (cache);
		cache.extent (1L, -7, this::measure);
		cache.extent (1L, -7, this::measure);
		long misses = cache.misses (), hits = cache.hits (), evictions = cache.evictions ();
		cache.invalidateAll ();
		assertEquals (0, cache.size ());
		assertEquals (-1L, cache.peek (1L, -7));
		assertEquals (misses, cache.misses ());
		assertEquals (hits, cache.hits ());
		assertEquals (evictions, cache.evictions ());
		cache.extent (1L, Integer.MIN_VALUE, this::measure);
		cache.extent (2L, Integer.MIN_VALUE, this::measure);
		cache.invalidateLabel (1L);
		assertEquals (-1L, cache.peek (1L, Integer.MIN_VALUE));
		assertEquals (TextExtentCache.pack (-2147483634, -2147483638), cache.peek (2L, Integer.MIN_VALUE));
		cache.invalidateFont (Integer.MIN_VALUE);
		assertEquals (0, cache.size ());
	}

	@Test
	void labelInvalidationAfterChurnDeletesEveryMatchInWrappingCluster () {
		TextExtentCache cache = new TextExtentCache (3);
		churnFontsAndRemoveEntries (cache);
		// These keys share home slot 7 in the eight-slot table. The two labels
		// to remove wrap into slots 0 and 1; deletion must revisit the shifted slot.
		cache.extent (9L, -94, this::measure);
		cache.extent (11L, -100, this::measure);
		cache.extent (11L, -93, this::measure);
		cache.invalidateLabel (11L);
		assertEquals (-1L, cache.peek (11L, -100));
		assertEquals (-1L, cache.peek (11L, -93));
		assertEquals (TextExtentCache.pack (-31, -84), cache.peek (9L, -94));
		assertEquals (1, cache.size ());
		cache.extent (11L, -93, this::measure);
		cache.extent (11L, -100, this::measure);
		cache.invalidateFont (-93);
		assertEquals (-1L, cache.peek (11L, -93));
		assertEquals (TextExtentCache.pack (-23, -90), cache.peek (11L, -100));
		assertEquals (TextExtentCache.pack (-31, -84), cache.peek (9L, -94));
	}

	@Test
	void failedMeasurementAfterFontChurnKeepsCachedEntry () {
		TextExtentCache cache = new TextExtentCache (1);
		churnFontsAndRemoveEntries (cache);
		cache.extent (1L, -7, this::measure);
		long misses = cache.misses (), hits = cache.hits (), evictions = cache.evictions ();
		IllegalStateException failure = new IllegalStateException ("measurement failed");
		assertSame (failure, assertThrows (IllegalStateException.class,
				() -> cache.extent (2L, -8, (label, font) -> { throw failure; })));
		assertEquals (TextExtentCache.pack (0, 3), cache.peek (1L, -7));
		assertEquals (-1L, cache.peek (2L, -8));
		assertEquals (1, cache.size ());
		assertEquals (misses + 1, cache.misses ());
		assertEquals (hits, cache.hits ());
		assertEquals (evictions, cache.evictions ());
	}
}
