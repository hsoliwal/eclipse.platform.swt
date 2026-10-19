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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3ViewportCollectionsTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Edge tests of the viewport-extracted collection owners: bit lanes, growth rules, sibling forest, sparse extents. */
public class M3ViewportCollectionsTest {
	@AfterEach
	public void restoreSampler () {
		M3HeapGrowth.sampler (null);
	}

	@Test
	public void bitLanesArgumentsAndEmptyLanes () {
		assertThrows (IllegalArgumentException.class, () -> new M3BitLanes (0));
		M3BitLanes lanes = new M3BitLanes (2);
		assertThrows (IllegalArgumentException.class, () -> lanes.setLength (-1));
		lanes.setLength (40);
		assertFalse (lanes.get (0, 3), "untouched lane");
		lanes.set (0, 3, false);
		assertEquals (0, lanes.count (1));
		assertEquals (0, lanes.count (1, 0, 40));
		assertEquals (0, lanes.nextClear (1, 0), "an untouched lane is clear everywhere");
		assertEquals (-1, lanes.nextSet (1, 0));
		lanes.set (0, 3, true);
		assertFalse (lanes.get (0, -1));
		assertFalse (lanes.get (0, 40));
		assertEquals (0, lanes.count (0, 5, 5));
		assertThrows (IllegalArgumentException.class, () -> lanes.setRange (0, -1, 2, true));
		assertThrows (IllegalArgumentException.class, () -> lanes.setRange (0, 0, 41, true));
		assertThrows (IllegalArgumentException.class, () -> lanes.setRange (0, 5, 4, true));
		assertThrows (IllegalArgumentException.class, () -> lanes.count (0, -1, 2));
		assertThrows (IllegalArgumentException.class, () -> lanes.count (0, 0, 41));
		assertThrows (IllegalArgumentException.class, () -> lanes.count (0, 5, 4));
		assertThrows (IllegalArgumentException.class, () -> lanes.flipRange (0, -1, 2));
		assertThrows (IllegalArgumentException.class, () -> lanes.flipRange (0, 0, 41));
		assertThrows (IllegalArgumentException.class, () -> lanes.flipRange (0, 5, 4));
		assertThrows (IllegalArgumentException.class, () -> lanes.insert (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> lanes.insert (41, 1));
		assertThrows (IllegalArgumentException.class, () -> lanes.insert (0, -1));
		assertThrows (IllegalArgumentException.class, () -> lanes.insert (0, Integer.MAX_VALUE));
		assertThrows (IllegalArgumentException.class, () -> lanes.remove (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> lanes.remove (0, -1));
		assertThrows (IllegalArgumentException.class, () -> lanes.remove (0, 41));
		assertThrows (IllegalArgumentException.class, () -> lanes.set (0, 40, true));
		assertThrows (IllegalArgumentException.class, () -> lanes.set (0, -1, true));
		lanes.clear ();
		assertEquals (0, lanes.length ());
		assertEquals (0, lanes.count (0));
	}

	@Test
	public void bitLanesRangesAndScans () {
		M3BitLanes lanes = new M3BitLanes (1);
		lanes.setLength (100);
		lanes.flipRange (0, 3, 3);
		lanes.flipRange (0, 3, 9);
		assertEquals (6, lanes.count (0));
		lanes.flipRange (0, 4, 8);
		assertEquals (2, lanes.count (0), "flip inside one word");
		lanes.flipRange (0, 10, 90);
		assertEquals (82, lanes.count (0), "flip across words");
		assertEquals (80, lanes.count (0, 10, 90));
		lanes.setRange (0, 0, 0, true);
		lanes.setRange (0, 20, 30, false);
		assertEquals (20, lanes.nextClear (0, 15));
		assertEquals (30, lanes.nextSet (0, 20));
		assertEquals (0, lanes.nextClear (0, -5));
		assertEquals (3, lanes.nextSet (0, -5));
		assertEquals (100, lanes.nextClear (0, 100));
		assertEquals (-1, lanes.nextSet (0, 100));
		lanes.setRange (0, 90, 100, true);
		assertEquals (100, lanes.nextClear (0, 90), "all set to the end");
		M3BitLanes shortLane = new M3BitLanes (1);
		shortLane.setLength (10);
		shortLane.set (0, 1, true);
		shortLane.setLength (200);
		assertEquals (100, shortLane.nextClear (0, 100), "beyond the allocated words");
		assertEquals (-1, shortLane.nextSet (0, 100));
		assertEquals (1, shortLane.count (0, 0, 200), "the count stops at the allocated words");
		shortLane.setLength (32);
		shortLane.setRange (0, 0, 32, true);
		assertEquals (32, shortLane.nextClear (0, 0), "every allocated word is full");
		shortLane.setLength (10);
		assertEquals (10, shortLane.count (0), "shrinking clears the tail");
		assertEquals (-1, shortLane.nextSet (0, 10));
	}

	/** The largest lane (2^26 words, 256 MiB) has more bits than an int counts; clearing uses the int ceiling. */
	@Test
	public void bitLanesFullIntRange () {
		M3BitLanes lanes = new M3BitLanes (1);
		lanes.setLength (Integer.MAX_VALUE);
		lanes.set (0, Integer.MAX_VALUE - 1, true);
		lanes.set (0, 5, true);
		lanes.setLength (10);
		assertEquals (1, lanes.count (0));
		assertTrue (lanes.get (0, 5));
		lanes.clear ();
	}

	@Test
	public void bitLanesInsertRemoveShift () {
		M3BitLanes lanes = new M3BitLanes (2);
		lanes.setLength (8);
		lanes.set (0, 0, true);
		lanes.set (0, 7, true);
		lanes.insert (0, 0);
		lanes.remove (0, 0);
		lanes.insert (1, 40);
		assertEquals (48, lanes.length ());
		assertTrue (lanes.get (0, 0));
		assertTrue (lanes.get (0, 47));
		assertEquals (2, lanes.count (0));
		lanes.insert (0, 1);
		assertTrue (lanes.get (0, 1));
		assertTrue (lanes.get (0, 48));
		lanes.remove (0, 45);
		assertEquals (4, lanes.length ());
		assertTrue (lanes.get (0, 3));
		assertEquals (1, lanes.count (0));
		lanes.remove (2, 2);
		assertEquals (0, lanes.count (0), "removing the tail clears it");
	}

	@Test
	public void growthRules () {
		assertEquals (10, M3LaneGrowth.capacity (10, 4, 4), "no growth needed");
		assertEquals (10, M3LaneGrowth.capacity (10, 10, 4));
		assertTrue (M3LaneGrowth.capacity (10, 11, 0) >= 11);
		assertEquals (8, M3HeapGrowth.grow (0, 1));
		assertEquals (15, M3HeapGrowth.grow (10, 11));
		assertEquals (100, M3HeapGrowth.grow (10, 100));
		assertEquals (M3HeapGrowth.MAX_ARRAY_SIZE, M3HeapGrowth.grow (Integer.MAX_VALUE - 100, Integer.MAX_VALUE - 99));
		assertThrows (OutOfMemoryError.class, () -> M3HeapGrowth.grow (0, -1));
		assertThrows (OutOfMemoryError.class, () -> M3HeapGrowth.grow (0, Integer.MAX_VALUE));
		assertThrows (IllegalArgumentException.class, () -> M3HeapGrowth.choose (0, 1, 0, 1, 0));
		long mib = 1024L * 1024L;
		assertEquals (100, M3HeapGrowth.choose (10, 100, 4, 1000 * mib, 0), "geometric equals minimum");
		assertEquals (15, M3HeapGrowth.choose (10, 11, 4, 0, 0), "no heap limit");
		assertEquals (15, M3HeapGrowth.choose (10, 11, 4, Long.MAX_VALUE, 0));
		assertEquals (15, M3HeapGrowth.choose (10, 11, 4, 1000 * mib, -1));
		assertEquals (15, M3HeapGrowth.choose (10, 11, 4, 1000 * mib, 1001 * mib));
		assertEquals (15, M3HeapGrowth.choose (10, 11, 4, 1000 * mib, 0), "headroom keeps geometric growth");
		assertEquals (11, M3HeapGrowth.choose (10, 11, 4, 1000 * mib, 990 * mib), "available below the reserve");
		assertEquals (1_000_001, M3HeapGrowth.choose (1_000_000, 1_000_001, 8, 1000 * mib, 990 * mib - 64 * mib), "geometric does not fit");
		int samples = M3HeapGrowth.samples (), exact = M3HeapGrowth.exactChoices ();
		M3HeapGrowth.sampler (new M3HeapGrowth.Sampler () {
			@Override
			public long maxBytes () {
				return 1000 * mib;
			}

			@Override
			public long usedBytes () {
				return 999 * mib;
			}
		});
		assertEquals (11, M3HeapGrowth.capacity (10, 11, 4));
		assertEquals (100, M3HeapGrowth.capacity (10, 100, 4), "exact because geometric equals minimum: not a pressure choice");
		assertEquals (samples + 2, M3HeapGrowth.samples ());
		assertEquals (exact + 1, M3HeapGrowth.exactChoices ());
		M3HeapGrowth.sampler (null);
		assertTrue (M3HeapGrowth.capacity (10, 11, 4) >= 11, "runtime sampler restored");
	}

	@Test
	public void siblingForest () {
		M3OrderStatisticForest forest = new M3OrderStatisticForest ();
		int [] keys = {10, 20, 30, 40, 50, 20, 60};
		long [] weights = {1, 2, 3, 4, 5, 0, 0};
		forest.lanes (keys, weights);
		forest.ensureCapacity (7);
		forest.ensureCapacity (3);
		for (int id = 0; id < 4; id++) forest.insert (0, id);
		forest.insert (-1, 4);
		assertEquals (50, keys [forest.find (-1, 50)]);
		assertEquals (1, forest.find (0, 20));
		assertEquals (-1, forest.find (0, 25));
		assertEquals (1, forest.lower (0, 25));
		assertEquals (-1, forest.lower (0, 10));
		assertEquals (10L, forest.total (0));
		assertEquals (5L, forest.total (-1));
		assertEquals (3L, forest.prefix (0, 30));
		assertEquals (3, forest.treeHeight (0));
		forest.adjust (0, 2, 0);
		weights [2] += 5;
		forest.adjust (0, 2, 5);
		assertEquals (15L, forest.total (0));
		assertEquals (0, forest.seek (0, 100, 15), "the limit stops at key 10");
		assertEquals (0L, forest.seekBefore ());
		assertThrows (IllegalStateException.class, () -> forest.insert (0, 5), "duplicate key");
		assertThrows (IllegalStateException.class, () -> forest.remove (0, 6), "absent id");
		assertThrows (IllegalStateException.class, () -> forest.adjust (-1, 6, 1), "absent id");
		forest.clear ();
		assertEquals (-1, forest.find (0, 20));
		assertEquals (0L, forest.total (0));
	}

	@Test
	public void sparseExtentsArguments () {
		M3SparseExtents extents = new M3SparseExtents (10);
		assertEquals (10, extents.estimate ());
		extents.reset (5);
		assertThrows (IllegalArgumentException.class, () -> extents.clearFrom (-1));
		assertThrows (IllegalArgumentException.class, () -> extents.clearFrom (6));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (6, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (0, 6));
		assertThrows (IllegalArgumentException.class, () -> extents.offsetOf (-1));
		assertThrows (IllegalArgumentException.class, () -> extents.offsetOf (6));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (0, -1, 0, false));
		assertEquals (50L, extents.offsetOf (5));
	}
}
