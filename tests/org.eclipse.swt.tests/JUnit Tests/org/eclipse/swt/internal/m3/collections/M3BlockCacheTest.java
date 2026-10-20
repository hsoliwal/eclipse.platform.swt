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
 * com.m3.collections.M3BlockCacheTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct tests of the packed block cache: slots, LRU eviction by frame, lanes, invalidation and clock rebase. */
public class M3BlockCacheTest {
	@Test
	public void constructionAndShape () {
		M3BlockCache cache = new M3BlockCache (4, 3, 2);
		assertEquals (4, cache.blockSize ());
		assertEquals (3, cache.capacity ());
		assertEquals (2, cache.columns ());
		assertEquals (0, cache.frame ());
		assertEquals (-1, cache.find (0, 0));
		assertThrows (IllegalArgumentException.class, () -> new M3BlockCache (0, 1, 1));
		assertThrows (IllegalArgumentException.class, () -> new M3BlockCache (1, 0, 1));
		assertThrows (IllegalArgumentException.class, () -> new M3BlockCache (1, 1, 0));
		assertThrows (IllegalArgumentException.class, () -> new M3BlockCache (1 << 16, 1 << 16, 1));
	}

	@Test
	public void lanesAndBounds () {
		M3BlockCache cache = new M3BlockCache (4, 2, 2);
		int slot = cache.reserve (7, 3);
		assertEquals (M3BlockStore.PENDING, cache.state (slot));
		assertEquals (7, cache.parentKey (slot));
		assertEquals (3, cache.block (slot));
		assertEquals (12, cache.firstRow (slot));
		assertEquals (M3BlockStore.UNKNOWN, cache.total (slot));
		cache.total (slot, 40);
		assertEquals (40, cache.total (slot));
		cache.text (slot, 1, 1, "b");
		assertEquals ("b", cache.text (slot, 1, 1));
		assertNull (cache.text (slot, 1, 0));
		cache.childHint (slot, 2, 5);
		assertEquals (5, cache.childHint (slot, 2));
		cache.key (slot, 3, 9);
		assertEquals (9, cache.key (slot, 3));
		Object payload = new Object ();
		cache.data (slot, 0, payload);
		assertSame (payload, cache.data (slot, 0));
		cache.state (slot, M3BlockStore.LOADED);
		assertEquals (1, cache.count (M3BlockStore.LOADED));
		assertEquals (0, cache.count (M3BlockStore.BUSY));
		assertThrows (IllegalArgumentException.class, () -> cache.key (-1, 0));
		assertThrows (IllegalArgumentException.class, () -> cache.key (2, 0));
		assertThrows (IllegalArgumentException.class, () -> cache.key (0, -1));
		assertThrows (IllegalArgumentException.class, () -> cache.key (0, 4));
		assertThrows (IllegalArgumentException.class, () -> cache.text (0, 0, -1));
		assertThrows (IllegalArgumentException.class, () -> cache.text (0, 0, 2));
		cache.free (slot);
		assertEquals (M3BlockStore.EMPTY, cache.state (slot));
		assertNull (cache.text (slot, 1, 1), "a freed slot is cleared");
		assertNull (cache.data (slot, 0));
	}

	@Test
	public void findUsesLastHitAndSkipsEmpty () {
		M3BlockCache cache = new M3BlockCache (2, 3, 1);
		int a = cache.reserve (1, 0);
		int b = cache.reserve (1, 1);
		assertEquals (a, cache.find (1, 0));
		assertEquals (a, cache.find (1, 0), "last hit");
		assertEquals (b, cache.find (1, 1));
		assertEquals (-1, cache.find (2, 1));
		assertEquals (-1, cache.find (1, 2));
		cache.free (b);
		assertEquals (-1, cache.find (1, 1), "freed slot clears the last hit");
		assertEquals (a, cache.find (1, 0));
		cache.state (a, M3BlockStore.EMPTY);
		assertEquals (-1, cache.find (1, 0), "an empty slot is never found, not even as the last hit");
	}

	@Test
	public void evictionByFrameAndEvictor () {
		M3BlockCache cache = new M3BlockCache (1, 2, 1);
		List<Integer> evicted = new ArrayList<> ();
		cache.evictor (evicted::add);
		cache.nextFrame ();
		int a = cache.reserve (1, 0);
		int b = cache.reserve (1, 1);
		assertEquals (-1, cache.reserve (1, 2), "both slots are busy");
		cache.state (a, M3BlockStore.LOADED);
		cache.state (b, M3BlockStore.FAILED);
		assertEquals (-1, cache.reserve (1, 2), "both slots were touched in this frame");
		cache.nextFrame ();
		cache.touch (a);
		assertEquals (cache.frame (), cache.stamp (a));
		int c = cache.reserve (1, 2);
		assertEquals (b, c, "the least recently touched settled slot is evicted");
		assertEquals (1, cache.evictions ());
		assertTrue (evicted.isEmpty (), "only loaded slots go to the evictor");
		cache.state (c, M3BlockStore.LOADED);
		cache.nextFrame ();
		cache.nextFrame ();
		cache.touch (c);
		int d = cache.reserve (1, 3);
		assertEquals (a, d);
		assertEquals (List.of (a), evicted);
		assertEquals (2, cache.evictions ());
		cache.evictor (null);
		cache.state (d, M3BlockStore.LOADED);
		cache.nextFrame ();
		assertTrue (cache.reserve (1, 4) >= 0, "eviction without an evictor");
	}

	@Test
	public void evictionPrefersOldestStamp () {
		M3BlockCache cache = new M3BlockCache (1, 3, 1);
		int[] slots = {cache.reserve (0, 0), cache.reserve (0, 1), cache.reserve (0, 2)};
		for (int slot : slots) cache.state (slot, M3BlockStore.LOADED);
		cache.nextFrame ();
		cache.touch (slots [0]);
		cache.nextFrame ();
		cache.touch (slots [2]);
		cache.nextFrame ();
		cache.touch (slots [1]);
		cache.nextFrame ();
		assertEquals (slots [0], cache.reserve (0, 9));
	}

	@Test
	public void invalidateByParentAndAll () {
		M3BlockCache cache = new M3BlockCache (1, 4, 1);
		int a = cache.reserve (1, 0), b = cache.reserve (2, 0), c = cache.reserve (1, 1);
		cache.state (a, M3BlockStore.LOADED);
		cache.state (b, M3BlockStore.LOADED);
		cache.state (c, M3BlockStore.LOADING);
		assertEquals (1, cache.invalidate (1), "busy slots stay with their owner");
		assertEquals (M3BlockStore.LOADING, cache.state (c));
		assertEquals (M3BlockStore.LOADED, cache.state (b));
		cache.state (c, M3BlockStore.CANCELLED);
		assertEquals (2, cache.invalidate (Integer.MIN_VALUE));
		assertEquals (0, cache.count (~0));
	}

	@Test
	public void clockRebaseKeepsOrder () throws ReflectiveOperationException {
		M3BlockCache cache = new M3BlockCache (1, 4, 1);
		setClock (cache, Integer.MAX_VALUE);
		assertEquals (1, cache.nextFrame (), "an empty cache restarts the clock");
		int a = cache.reserve (0, 0), b = cache.reserve (0, 1), c = cache.reserve (0, 2);
		cache.state (a, M3BlockStore.LOADED);
		cache.state (b, M3BlockStore.LOADED);
		cache.state (c, M3BlockStore.LOADED);
		setClock (cache, Integer.MAX_VALUE - 5);
		cache.touch (a);
		setClock (cache, Integer.MAX_VALUE - 2);
		cache.touch (b);
		cache.touch (c);
		setClock (cache, Integer.MAX_VALUE);
		int frame = cache.nextFrame ();
		assertEquals (3, frame, "two distinct active stamps, then the new frame");
		assertEquals (1, cache.stamp (a));
		assertEquals (2, cache.stamp (b));
		assertEquals (2, cache.stamp (c));
		assertEquals (0, cache.stamp (3), "empty slots rebase to zero");
		assertEquals (3, cache.reserve (0, 3), "an empty slot first");
		assertEquals (a, cache.reserve (0, 4), "then the oldest rebased stamp");
		assertEquals (-1, cache.find (0, 9));
	}

	private static void setClock (M3BlockCache cache, int value) throws ReflectiveOperationException {
		Field clock = M3BlockCache.class.getDeclaredField ("clock");
		clock.setAccessible (true);
		clock.setInt (cache, value);
	}
}
