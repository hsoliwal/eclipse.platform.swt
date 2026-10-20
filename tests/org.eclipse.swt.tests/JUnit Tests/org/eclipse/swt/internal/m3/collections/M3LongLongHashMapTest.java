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
 * com.m3.collections.M3LongLongHashMapTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Oracle test of the primitive open-addressed map (the viewport's resident-plan map) against java.util.HashMap. */
public class M3LongLongHashMapTest {
	@Test
	public void randomOperationsMatchHashMap () {
		Random random = new Random (31L);
		M3LongLongHashMap map = new M3LongLongHashMap ();
		Map<Long, Long> oracle = new HashMap<> ();
		for (int step = 0; step < 60_000; step++) {
			long key = random.nextInt (step < 30_000 ? 2_000 : 64);
			switch (random.nextInt (7)) {
				case 0, 1, 2 -> {
					long value = random.nextLong ();
					assertEquals (!oracle.containsKey (key), map.put (key, value));
					oracle.put (key, value);
				}
				case 3 -> assertEquals (oracle.remove (key) != null, map.remove (key));
				case 4 -> {
					long expected = oracle.getOrDefault (key, 7L);
					boolean present = oracle.containsKey (key) && random.nextBoolean ();
					assertEquals (present, map.remove (key, present ? expected : expected + 1));
					if (present) oracle.remove (key);
				}
				case 5 -> {
					long expected = oracle.getOrDefault (key, 7L);
					boolean present = oracle.containsKey (key) && random.nextBoolean ();
					assertEquals (present, map.replace (key, present ? expected : expected + 1, 99L));
					if (present) oracle.put (key, 99L);
				}
				default -> {
					if (random.nextInt (500) == 0) {
						map.clear ();
						oracle.clear ();
					}
				}
			}
			assertEquals (oracle.getOrDefault (key, -1L), map.getOrDefault (key, -1L));
			assertEquals (oracle.containsKey (key), map.containsKey (key));
			assertEquals (oracle.size (), map.size ());
			assertEquals (oracle.isEmpty (), map.isEmpty ());
			if (step % 1_000 == 0) {
				long [] keys = map.keysToArray ();
				Arrays.sort (keys);
				assertArrayEquals (oracle.keySet ().stream ().mapToLong (Long::longValue).sorted ().toArray (), keys);
				assertEquals (oracle.containsValue (99L), map.containsValue (99L));
				int [] count = {0};
				map.forEach ((k, v) -> {
					assertEquals (oracle.get (k), v);
					count [0]++;
				});
				assertEquals (oracle.size (), count [0]);
			}
		}
		assertTrue (map.capacity () >= map.size ());
	}

	@Test
	public void tombstonesGrowthAndIterators () {
		M3LongLongHashMap map = new M3LongLongHashMap (4);
		assertTrue (map.capacity () >= 8);
		for (long k = 0; k < 6; k++) map.put (k, k * 10);
		for (long k = 0; k < 5; k++) assertTrue (map.remove (k));
		assertEquals (1, map.size ());
		for (long k = 100; k < 112; k++) assertTrue (map.put (k, k), "inserts over tombstones and through growth");
		assertEquals (13, map.size ());
		for (long k = 100; k < 112; k++) assertEquals (k, map.getOrDefault (k, -1));
		assertEquals (50, map.getOrDefault (5, -1));
		M3LongIterator it = map.keyIterator ();
		int seen = 0;
		while (it.hasNext ()) {
			it.nextLong ();
			seen++;
		}
		assertEquals (13, seen);
		assertThrows (NoSuchElementException.class, it::nextLong);
		M3LongIterator stale = map.keyIterator ();
		map.put (500, 1);
		assertThrows (ConcurrentModificationException.class, stale::hasNext);
		assertThrows (ConcurrentModificationException.class, stale::nextLong);
		assertThrows (ConcurrentModificationException.class, () -> map.forEach ((k, v) -> map.remove (k)));
		assertThrows (NullPointerException.class, () -> map.forEach (null));
		assertThrows (IllegalArgumentException.class, () -> new M3LongLongHashMap (-1));
		map.clear ();
		map.clear ();
		assertEquals (0, map.size ());
		assertFalse (map.containsValue (1));
		M3LongLongHashMap big = new M3LongLongHashMap ();
		for (long k = 0; k < 5_000; k++) big.put (k * 1_000_003L, k);
		for (long k = 0; k < 5_000; k += 2) big.remove (k * 1_000_003L);
		for (long k = 1; k < 5_000; k += 2) assertEquals (k, big.getOrDefault (k * 1_000_003L, -1), "survives rehash after mass deletion");
		assertEquals (2_500, big.keysToArray ().length);
	}

	@Test
	public void growthBoundaryReusesTombstoneAndIteratorDrains () {
		M3LongLongHashMap map = new M3LongLongHashMap ();
		assertEquals (8, map.capacity ());
		for (long k = 0; k < 5; k++) map.put (k, k);
		for (long k = 0; k < 4; k++) map.remove (k);
		assertTrue (map.put (0, 42), "the growth-boundary probe reuses the tombstone of key 0");
		assertEquals (8, map.capacity (), "no growth when a tombstone takes the key");
		assertEquals (42, map.getOrDefault (0, -1));
		assertEquals (2, map.size ());
		long [] sum = {0};
		map.keyIterator ().forEachRemaining (k -> sum [0] += k);
		assertEquals (4, sum [0]);
		assertThrows (NullPointerException.class, () -> map.keyIterator ().forEachRemaining (null));
		assertThrows (IllegalArgumentException.class, () -> new M3LongLongHashMap (Integer.MAX_VALUE));
	}
}
