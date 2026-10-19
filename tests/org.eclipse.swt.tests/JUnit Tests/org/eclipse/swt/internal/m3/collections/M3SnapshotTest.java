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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 826e57b779174368fec5c4295eca9e3ad794c2e1,
 * com.m3.collections.M3SnapshotTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The model-to-render protocol: single-thread contract, and a concurrent torn-read detector. */
public class M3SnapshotTest {
	@Test
	public void publishAndRead () {
		M3SnapshotPublisher publisher = new M3SnapshotPublisher (8, 2);
		M3Snapshot empty = publisher.latest ();
		assertEquals (0, empty.generation ());
		assertEquals (0, empty.size ());
		assertThrows (IllegalStateException.class, () -> publisher.set (0, 1, 1));
		assertThrows (IllegalStateException.class, publisher::publish);
		assertThrows (IllegalArgumentException.class, () -> publisher.begin (0, 9));
		assertThrows (IllegalArgumentException.class, () -> publisher.begin (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> publisher.begin (0, -1));
		publisher.begin (100, 3);
		publisher.set (0, 7, 70L);
		publisher.set (2, 9, 90L);
		assertThrows (IndexOutOfBoundsException.class, () -> publisher.set (3, 0, 0));
		assertThrows (IndexOutOfBoundsException.class, () -> publisher.set (-1, 0, 0));
		publisher.damage (100, 102);
		publisher.damage (102, 103);
		assertThrows (IllegalStateException.class, () -> publisher.damage (0, 1), "damage capacity");
		assertThrows (IllegalArgumentException.class, () -> publisher.damage (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> publisher.damage (5, 4));
		assertEquals (0, publisher.latest ().generation (), "nothing visible before publish");
		assertEquals (1, publisher.publish ());
		M3Snapshot s = publisher.latest ();
		long g = s.generation ();
		assertEquals (1, g);
		assertEquals (100, s.first ());
		assertEquals (3, s.size ());
		assertEquals (7, s.state (0));
		assertEquals (0, s.state (1));
		assertEquals (90L, s.key (2));
		assertEquals (2, s.damageRanges ());
		assertEquals (102, s.damageStart (1));
		assertEquals (103, s.damageEnd (1));
		assertTrue (s.stillValid (g));
		assertFalse (s.stillValid (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> s.state (3));
		assertThrows (IndexOutOfBoundsException.class, () -> s.key (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> s.damageStart (2));
		assertThrows (IndexOutOfBoundsException.class, () -> s.damageEnd (-1));
		List<String> seen = new ArrayList<> ();
		long consumed = publisher.consume (new M3SnapshotPublisher.Reader () {
			@Override
			public void window (long generation, int first, int size) {
				seen.add ("w" + generation + ":" + first + "+" + size);
			}

			@Override
			public void row (int index, int state, long key) {
				seen.add ("r" + index + "=" + state + "/" + key);
			}

			@Override
			public void damage (int start, int end) {
				seen.add ("d" + start + ".." + end);
			}
		});
		assertEquals (1, consumed);
		assertEquals (List.of ("w1:100+3", "r0=7/70", "r1=0/0", "r2=9/90", "d100..102", "d102..103"), seen);
		for (int k = 0; k < 3; k++) {
			publisher.begin (0, 1);
			publisher.publish ();
		}
		assertFalse (s.stillValid (g), "the buffer of generation 1 was reused");
		assertEquals (4, publisher.generation ());
		assertEquals (0, publisher.retries ());
		assertThrows (IllegalArgumentException.class, () -> new M3SnapshotPublisher (-1, 0));
		assertThrows (IllegalArgumentException.class, () -> new M3SnapshotPublisher (1, -1));
	}

	@Test
	public void concurrentReadersNeverSeeTornSnapshots () throws InterruptedException {
		int capacity = 64, publishes = 200_000;
		M3SnapshotPublisher publisher = new M3SnapshotPublisher (capacity, 1);
		AtomicReference<String> failure = new AtomicReference<> ();
		Thread writer = new Thread (() -> {
			for (int k = 1; k <= publishes; k++) {
				int size = k % (capacity + 1);
				publisher.begin (k, size);
				for (int i = 0; i < size; i++) publisher.set (i, k, k * 31L + i);
				publisher.damage (k, k + 1);
				publisher.publish ();
			}
		});
		List<Thread> readers = new ArrayList<> ();
		long [] delivered = new long [3];
		for (int r = 0; r < 3; r++) {
			int id = r;
			readers.add (new Thread (() -> {
				long last = -1;
				int [] states = new int [capacity];
				long [] keys = new long [capacity];
				int [] window = new int [3];
				while (writer.isAlive () || last < publishes) {
					long g = publisher.consume (new M3SnapshotPublisher.Reader () {
						@Override
						public void window (long generation, int first, int size) {
							window [0] = first;
							window [1] = size;
						}

						@Override
						public void row (int index, int state, long key) {
							states [index] = state;
							keys [index] = key;
						}

						@Override
						public void damage (int start, int end) {
							window [2] = start;
						}
					});
					if (g < last) failure.compareAndSet (null, "generation went back " + last + " -> " + g);
					if (g > 0) {
						if (window [0] != g || window [1] != g % (capacity + 1) || window [2] != g)
							failure.compareAndSet (null, "torn window at " + g);
						for (int i = 0; i < window [1]; i++) {
							if (states [i] != g || keys [i] != g * 31L + i) failure.compareAndSet (null, "torn row " + i + " at " + g);
						}
					}
					last = g;
					delivered [id]++;
					if (!writer.isAlive () && last == publishes) break;
				}
			}));
		}
		readers.forEach (Thread::start);
		writer.start ();
		writer.join ();
		for (Thread t : readers) t.join (60_000);
		assertNull (failure.get ());
		assertEquals (publishes, publisher.generation ());
		assertEquals (publishes, publisher.latest ().generation ());
		for (long d : delivered) assertTrue (d > 0);
	}
}
