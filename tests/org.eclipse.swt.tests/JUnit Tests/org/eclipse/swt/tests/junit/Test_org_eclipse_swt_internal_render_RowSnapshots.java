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
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The snapshot protocol (leaf IM2) without a display: a model thread publishes slabs as fast as it can while a renderer
 * thread acquires frames; every acquired slab is consistent (its checksum matches: never torn, I1), the generations
 * never go back (I2), each side finishes without waiting for the other (I3) and nothing allocates after construction
 * (I4).
 */
public class Test_org_eclipse_swt_internal_render_RowSnapshots {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_internal_render_RowSnapshots.class.getName ());

	static void fill (RowSnapshots.Slab slab, Random random, int i) {
		slab.firstRow = random.nextInt (1000);
		slab.rows = 1 + random.nextInt (64);
		slab.scrollY = i;
		slab.themeGeneration = i & 7;
		for (int r = 0; r < slab.rows; r++) {
			slab.y [r] = r * 18;
			slab.height [r] = 18;
			slab.state [r] = random.nextInt ();
			slab.text [r] = slab.firstRow + r;
			slab.foreground [r] = random.nextInt ();
			slab.background [r] = random.nextInt ();
			slab.font [r] = r & 3;
			slab.image [r] = -1;
		}
	}

	@Test
	public void threeSlabsPublishAndAcquireWithoutTearing () throws InterruptedException {
		RowSnapshots snapshots = new RowSnapshots (64);
		assertNull (snapshots.acquire (), "nothing published yet");
		assertEquals (0, snapshots.generation ());
		AtomicBoolean stop = new AtomicBoolean ();
		AtomicLong torn = new AtomicLong (), backwards = new AtomicLong (), frames = new AtomicLong ();
		Thread renderer = new Thread (() -> {
			long previous = 0;
			while (!stop.get ()) {
				RowSnapshots.Slab slab = snapshots.acquire ();
				if (slab == null) continue;
				/* a frame: read the whole slab while the writer keeps publishing */
				if (!slab.consistent ()) torn.incrementAndGet ();
				if (slab.generation < previous) backwards.incrementAndGet ();
				previous = slab.generation;
				frames.incrementAndGet ();
			}
		}, "renderer");
		renderer.start ();
		Random random = new Random (7);
		long published = 0;
		for (int i = 0; i < 200_000; i++) {
			RowSnapshots.Slab slab = snapshots.writable ();
			fill (slab, random, i);
			published = snapshots.publish (slab);
		}
		stop.set (true);
		renderer.join (10_000);
		long seen = frames.get ();
		LOG.info (() -> "snapshots: 200000 publications, " + seen + " frames read concurrently");
		assertEquals (200_000, published);
		assertEquals (published, snapshots.generation ());
		assertEquals (0, torn.get (), "I1: a frame read a slab while it was being written");
		assertEquals (0, backwards.get (), "I2: a frame painted an older generation");
		assertTrue (seen > 0, "the renderer drew frames");
		RowSnapshots.Slab last = snapshots.acquire ();
		assertEquals (published, last.generation);
		assertEquals (199_999, last.scrollY);
		assertTrue (last.consistent ());
	}

	@Test
	public void theWriterNeverGetsTheLatestOrTheReadSlab () {
		RowSnapshots snapshots = new RowSnapshots (4);
		RowSnapshots.Slab a = snapshots.writable ();
		snapshots.publish (a);
		assertSame (a, snapshots.acquire ());
		assertSame (a, snapshots.acquire (), "nothing newer: the same frame again");
		RowSnapshots.Slab b = snapshots.writable ();
		assertNotSame (a, b, "the read slab is not writable");
		snapshots.publish (b);
		RowSnapshots.Slab c = snapshots.writable ();
		assertNotSame (a, c, "still being read");
		assertNotSame (b, c, "the latest snapshot");
		snapshots.publish (c);
		assertSame (b, snapshots.writable (), "superseded before it was read: writable again");
		assertSame (c, snapshots.acquire (), "the renderer skips to the latest");
		snapshots.publish (b);
		assertSame (a, snapshots.writable (), "the renderer's previous slab, once it took a newer one");
		assertEquals (4, snapshots.generation ());
		assertThrows (IllegalArgumentException.class, () -> snapshots.publish (c), "only the writable slab is published");
	}

	/* I4 and the cost of the protocol: publish + acquire of a 64-row window, no allocation */
	@Test
	public void publicationAllocatesNothing () {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();
		RowSnapshots snapshots = new RowSnapshots (64);
		Random random = new Random (3);
		for (int i = 0; i < 20_000; i++) {
			fill (snapshots.writable (), random, i);
			snapshots.publish (snapshots.writable ());
			snapshots.acquire ();
		}
		long id = Thread.currentThread ().threadId ();
		int n = 1_000_000;
		long before = threads.getThreadAllocatedBytes (id), start = System.nanoTime ();
		for (int i = 0; i < n; i++) {
			RowSnapshots.Slab slab = snapshots.writable ();
			slab.scrollY = i;
			slab.rows = 64;
			snapshots.publish (slab);
			if (snapshots.acquire ().scrollY != i) fail ("the latest snapshot is acquired");
		}
		long nanos = System.nanoTime () - start, bytes = threads.getThreadAllocatedBytes (id) - before;
		LOG.info (() -> "snapshots: " + n + " publish+acquire of 64 rows, " + (nanos / n) + " ns each, " + bytes + " bytes allocated");
		assertTrue (bytes < 4096, "I4: publication allocated " + bytes + " bytes");
	}
}
