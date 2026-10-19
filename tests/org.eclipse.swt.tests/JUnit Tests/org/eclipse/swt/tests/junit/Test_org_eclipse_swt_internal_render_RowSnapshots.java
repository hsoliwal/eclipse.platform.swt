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

import java.util.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The snapshot protocol (leaf IM2) without a display: a model thread publishes slabs as fast as it can while a renderer
 * thread acquires frames; every acquired slab is consistent (its checksum matches: never torn, I1), the generations
 * never go back (I2), the writer always has a slab (three suffice, I3) and nothing allocates after construction (I4).
 */
public class Test_org_eclipse_swt_internal_render_RowSnapshots {

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
				/* a frame: read the whole slab while the writer keeps publishing into the other two */
				if (!slab.consistent ()) torn.incrementAndGet ();
				if (slab.generation < previous) backwards.incrementAndGet ();
				previous = slab.generation;
				frames.incrementAndGet ();
				snapshots.release ();
			}
		}, "renderer");
		renderer.start ();
		Random random = new Random (7);
		long published = 0;
		for (int i = 0; i < 200_000; i++) {
			RowSnapshots.Slab slab = snapshots.writable ();
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
			published = snapshots.publish (slab);
		}
		stop.set (true);
		renderer.join (10_000);
		assertEquals (200_000, published);
		assertEquals (published, snapshots.generation ());
		assertEquals (0, torn.get (), "I1: a frame read a slab while it was being written");
		assertEquals (0, backwards.get (), "I2: a frame painted an older generation");
		assertTrue (frames.get () > 0, "the renderer drew frames");
		RowSnapshots.Slab last = snapshots.acquire ();
		assertEquals (published, last.generation);
		assertEquals (199_999, last.scrollY);
		assertTrue (last.consistent ());
	}

	@Test
	public void theWriterNeverGetsThePublishedOrTheReadSlab () {
		RowSnapshots snapshots = new RowSnapshots (4);
		RowSnapshots.Slab a = snapshots.writable ();
		snapshots.publish (a);
		RowSnapshots.Slab read = snapshots.acquire ();
		assertSame (a, read);
		RowSnapshots.Slab b = snapshots.writable ();
		assertNotSame (a, b, "the published and read slab is not writable");
		snapshots.publish (b);
		RowSnapshots.Slab c = snapshots.writable ();
		assertNotSame (a, c, "still being read");
		assertNotSame (b, c, "just published");
		snapshots.release ();
		snapshots.publish (c);
		assertSame (a, snapshots.writable (), "released and superseded: writable again");
		assertEquals (3, snapshots.generation ());
		assertThrows (IllegalArgumentException.class, () -> snapshots.publish (new RowSnapshots (1).writable ()));
	}
}
