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
 * com.m3.collections.M3SnapshotPublisher (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.lang.invoke.VarHandle;

/**
 * The model-to-render protocol of the viewport: one CPU writer publishes immutable, generation-numbered
 * {@link M3Snapshot}s of the window, its lanes and its damage; any number of renderers (SWT native, drawn/GPU, Swing,
 * Draw2d) read the latest one without locks.
 * <p>Invariants:</p>
 * <ul>
 * <li>generations are monotonic: every {@link #publish()} answers the previous generation + 1;</li>
 * <li>no torn reads: buffers are preallocated and recycled round-robin (three of them); a buffer is marked -1 before
 * it is rewritten, so a reader that validates with {@link M3Snapshot#stillValid(long)} never accepts a mixed
 * buffer;</li>
 * <li>O(window) consume: a reader touches only the window rows and the damage ranges.</li>
 * </ul>
 * <p>Single writer: {@link #begin(int, int)}, the setters and {@link #publish()} must come from one thread at a time
 * (the CPU worker). Readers may run on any thread.</p>
 */
public final class M3SnapshotPublisher {
	/** Receives one consistent snapshot. */
	public interface Reader {
		/** Called first, with the window. */
		void window (long generation, int first, int size);

		/** Called once per window entry, in order. */
		void row (int index, int state, long key);

		/** Called once per damaged range, after the rows. */
		void damage (int start, int end);
	}

	private final M3Snapshot [] buffers;
	private final int capacity, damageCapacity;
	private volatile M3Snapshot published;
	private M3Snapshot writing;
	private long generation;
	private int next = 1;
	private long retries;

	/** A publisher of windows of at most {@code capacity} rows and {@code damageCapacity} damage ranges. */
	public M3SnapshotPublisher (int capacity, int damageCapacity) {
		if (capacity < 0 || damageCapacity < 0) throw new IllegalArgumentException ("negative capacity");
		this.capacity = capacity;
		this.damageCapacity = damageCapacity;
		buffers = new M3Snapshot [] {new M3Snapshot (capacity, damageCapacity), new M3Snapshot (capacity, damageCapacity),
				new M3Snapshot (capacity, damageCapacity)};
		buffers [0].generation (0);
		published = buffers [0];
	}

	/** The latest published snapshot (generation 0 is the empty window). */
	public M3Snapshot latest () {
		return published;
	}

	/** Starts writing the next snapshot of window {@code [first, first + size)}; entries start at state 0, key 0. */
	public void begin (int first, int size) {
		if (size < 0 || size > capacity || first < 0) throw new IllegalArgumentException ("window " + first + "+" + size);
		M3Snapshot b = buffers [next];
		next = next == 2 ? 0 : next + 1;
		b.generation (-1);
		/* the lane writes below must not move before the -1 mark (seqlock write side) */
		VarHandle.storeStoreFence ();
		b.first = first;
		b.size = size;
		b.damageCount = 0;
		java.util.Arrays.fill (b.states, 0, size, 0);
		java.util.Arrays.fill (b.keys, 0, size, 0L);
		writing = b;
	}

	/** Sets entry {@code index} of the snapshot being written. */
	public void set (int index, int state, long key) {
		M3Snapshot b = writing ();
		if (index < 0 || index >= b.size) throw new IndexOutOfBoundsException ("entry " + index + ", size " + b.size);
		b.states [index] = state;
		b.keys [index] = key;
	}

	/** Adds a damaged row range {@code [start, end)} to the snapshot being written. */
	public void damage (int start, int end) {
		M3Snapshot b = writing ();
		if (start < 0 || end < start) throw new IllegalArgumentException ("damage " + start + ".." + end);
		if (b.damageCount == damageCapacity) throw new IllegalStateException ("damage capacity " + damageCapacity);
		b.damage [b.damageCount * 2] = start;
		b.damage [b.damageCount * 2 + 1] = end;
		b.damageCount++;
	}

	/** Publishes the snapshot being written; answers its generation (previous + 1). */
	public long publish () {
		M3Snapshot b = writing ();
		b.generation (++generation);
		published = b;
		writing = null;
		return generation;
	}

	/** Generation of the latest publish. */
	public long generation () {
		return generation;
	}

	/** Torn reads detected (and retried) by {@link #consume(Reader)} so far; a diagnostic. */
	public long retries () {
		return retries;
	}

	/**
	 * Hands the latest consistent snapshot to {@code reader}, retrying when the buffer was reused during the read
	 * (the reader then sees the rows again from the start, from a newer snapshot). Answers the generation delivered.
	 */
	public long consume (Reader reader) {
		for (;;) {
			M3Snapshot s = published;
			long g = s.generation ();
			if (g < 0) {
				retries++;
				continue;
			}
			int first = s.first, size = s.size, ranges = s.damageCount;
			reader.window (g, first, size);
			for (int i = 0; i < size; i++) reader.row (i, s.states [i], s.keys [i]);
			for (int r = 0; r < ranges; r++) reader.damage (s.damage [r * 2], s.damage [r * 2 + 1]);
			if (s.stillValid (g)) return g;
			retries++;
		}
	}

	private M3Snapshot writing () {
		if (writing == null) throw new IllegalStateException ("no snapshot being written: call begin first");
		return writing;
	}
}
