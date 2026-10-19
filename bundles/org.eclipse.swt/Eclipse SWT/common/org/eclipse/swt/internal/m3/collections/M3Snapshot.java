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
 * com.m3.collections.M3Snapshot (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.lang.invoke.VarHandle;

/**
 * One generation of a viewport model as a renderer sees it: the window {@code [first, first + size)}, one {@code int}
 * state lane and one {@code long} key lane aligned with the window rows, and the damaged rows as {@code [start, end)}
 * ranges. Snapshots are filled by an {@link M3SnapshotPublisher} and are read-only for everybody else.
 * <p>Read protocol (no locks): take {@link #generation()} first, read what you need, then check
 * {@link #stillValid(long)}. A {@code false} answer means the publisher reused this buffer while you read (a torn
 * read); discard what you read and take {@link M3SnapshotPublisher#latest()} again.
 * {@link M3SnapshotPublisher#consume(M3SnapshotPublisher.Reader)} does this loop for you.</p>
 */
public final class M3Snapshot {
	/* -1 while the publisher writes this buffer (seqlock-style), otherwise the generation it holds */
	private volatile long generation = -1;
	int first, size, damageCount;
	final int [] states;
	final long [] keys;
	final int [] damage;

	M3Snapshot (int capacity, int damageCapacity) {
		states = new int [capacity];
		keys = new long [capacity];
		damage = new int [damageCapacity * 2];
	}

	/** The generation of this snapshot, or -1 while it is being written. */
	public long generation () {
		return generation;
	}

	void generation (long value) {
		generation = value;
	}

	/** Whether this buffer still holds {@code generation} (it was not reused while it was read). */
	public boolean stillValid (long generation) {
		/* the lane reads before this check must not move after it (seqlock read side) */
		VarHandle.loadLoadFence ();
		return generation >= 0 && this.generation == generation;
	}

	/** First model row of the window. */
	public int first () {
		return first;
	}

	/** Rows in the window. */
	public int size () {
		return size;
	}

	/** State mask of window entry {@code index}. */
	public int state (int index) {
		return states [checkIndex (index)];
	}

	/** Key (for example a row id or native handle) of window entry {@code index}. */
	public long key (int index) {
		return keys [checkIndex (index)];
	}

	/** Number of damaged row ranges. */
	public int damageRanges () {
		return damageCount;
	}

	/** First damaged model row of range {@code range}. */
	public int damageStart (int range) {
		return damage [checkRange (range) * 2];
	}

	/** End (exclusive) of damaged range {@code range}. */
	public int damageEnd (int range) {
		return damage [checkRange (range) * 2 + 1];
	}

	private int checkIndex (int index) {
		if (index < 0 || index >= size) throw new IndexOutOfBoundsException ("entry " + index + ", size " + size);
		return index;
	}

	private int checkRange (int range) {
		if (range < 0 || range >= damageCount) throw new IndexOutOfBoundsException ("range " + range + ", ranges " + damageCount);
		return range;
	}
}
