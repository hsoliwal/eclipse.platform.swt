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

import java.lang.management.*;
import java.util.*;

import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The boxing-free long-keyed cache of the drawn backends (optimization pass, frame allocations): answers like a map
 * while it has room, evicts by the clock when full and hands every value it lets go of to the callback exactly once,
 * and a hit allocates nothing.
 */
public class Test_org_eclipse_swt_internal_render_LongCache {

	@Test
	public void answersLikeAMapAndReleasesWhatItDrops () {
		List<String> released = new ArrayList<> ();
		LongCache<String> cache = new LongCache<> (4, released::add);
		long [] keys = {0x7FFF_0000_1234L, -1L, 0L, Long.MIN_VALUE};
		for (int i = 0; i < keys.length; i++) cache.put (keys [i], "v" + i);
		for (int i = 0; i < keys.length; i++) assertEquals ("v" + i, cache.get (keys [i]));
		assertNull (cache.get (42), "absent");
		assertEquals (-1, cache.lane (42));
		assertEquals (4, cache.size ());
		cache.put (keys [1], "w1");
		assertEquals (List.of ("v1"), released, "a replaced value is released");
		/* full: every lane was used since the clock last passed, so the clock clears them all and takes the first */
		cache.put (99, "v99");
		assertEquals (List.of ("v1", "v0"), released, "the clock's victim is released");
		assertNull (cache.get (keys [0]));
		assertEquals ("v99", cache.get (99));
		assertEquals ("w1", cache.get (keys [1]), "the rest of a probe cluster is still found after a removal");
		assertEquals ("v2", cache.get (keys [2]));
		assertEquals ("v3", cache.get (keys [3]));
		cache.clear ();
		assertEquals (0, cache.size ());
		assertEquals (6, released.size (), "clear releases the four kept values");
		assertNull (cache.get (99));
	}

	@Test
	public void manyKeysThroughASmallCache () {
		int [] releases = {0};
		LongCache<Long> cache = new LongCache<> (64, value -> releases [0]++);
		Random random = new Random (5);
		int puts = 0, hits = 0;
		for (int i = 0; i < 100_000; i++) {
			long key = random.nextInt (200) * 0x1_0000_0001L;
			Long kept = cache.get (key);
			if (kept != null) {
				assertEquals (key, kept.longValue (), "a hit answers its own value");
				hits++;
			} else {
				cache.put (key, key);
				puts++;
			}
			assertTrue (cache.size () <= 64);
		}
		assertEquals (puts, releases [0] + cache.size (), "every value put is kept or released once");
		assertTrue (hits > 0 && puts > 200, "both hits and evictions happened");
	}

	@Test
	public void aHitAllocatesNothing () {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();
		LongCache<Object> cache = new LongCache<> (32, value -> {});
		Object value = new Object ();
		long handle = 0x0000_7FF6_1234_5678L;
		cache.put (handle, value);
		long id = Thread.currentThread ().threadId ();
		for (int i = 0; i < 100_000; i++) cache.get (handle + (i & 1));
		long before = threads.getThreadAllocatedBytes (id);
		int hits = 0;
		for (int i = 0; i < 1_000_000; i++) {
			if (cache.get (handle) == value) hits++;
		}
		long bytes = threads.getThreadAllocatedBytes (id) - before;
		assertEquals (1_000_000, hits);
		assertTrue (bytes < 1024, "10^6 hits allocated " + bytes + " bytes");
	}
}
