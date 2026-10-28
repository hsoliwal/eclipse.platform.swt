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
package org.eclipse.swt.internal.render;

import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * A lock-free triple buffer between one writer thread and one reader thread (brief rule 28: the UI thread hands
 * snapshots to the render thread, the render thread hands finished frames back). The writer owns the <em>back</em>
 * value and fills it; the reader owns the <em>front</em> value; the third is the <em>middle</em>, the latest complete
 * one. One atomic word holds the middle's index and a <em>fresh</em> bit: {@link #publish} exchanges back and middle and
 * sets the bit, {@link #acquire} exchanges front and middle only when the bit is set. Each side touches only the value
 * it owns, so the reader never sees a value being written; the reader only ever takes a newer value than its own
 * (older ones published in between are dropped: the newest generation wins); neither side waits for the other; nothing
 * is allocated after construction. Catalogue: triple buffering.
 *
 * @param <T> the values (preallocated, reused)
 */
public final class TripleBuffer<T> {
	private static final int FRESH = 4;
	private final Object [] values = new Object [3];
	private final long [] generations = new long [3];
	/* bits 0-1: the middle value; FRESH: it is newer than the front */
	private final AtomicInteger middle = new AtomicInteger (1);
	/* owned by the writer */
	private int back = 0;
	private long generation;
	/* owned by the reader */
	private int front = 2;
	private volatile long latest;

	/** @param factory makes the three values */
	public TripleBuffer (Supplier<T> factory) {
		for (int i = 0; i < 3; i++) values [i] = factory.get ();
	}

	/** The value the writer fills now: neither the latest one nor the reader's. Writer only. */
	@SuppressWarnings ("unchecked")
	public T writable () {
		return (T) values [back];
	}

	/** Publishes the {@link #writable} value as the next generation; the middle becomes writable. Writer only. */
	public long publish () {
		generations [back] = ++generation;
		back = middle.getAndSet (back | FRESH) & 3;
		latest = generation;
		return generation;
	}

	/**
	 * The latest published value: the middle when it is newer than the reader's, else the reader's again; null before the
	 * first publication. It stays the reader's until the next call. Reader only.
	 */
	@SuppressWarnings ("unchecked")
	public T acquire () {
		if ((middle.get () & FRESH) != 0) front = middle.getAndSet (front) & 3;
		return generations [front] == 0 ? null : (T) values [front];
	}

	/** The generation of the value the reader acquired last (0: none). Reader only. */
	public long acquired () {
		return generations [front];
	}

	/** True when a value newer than the reader's was published (the reader's next {@link #acquire} takes it). */
	public boolean fresh () {
		return (middle.get () & FRESH) != 0;
	}

	/** The generation of the latest publication (0: none). */
	public long generation () {
		return latest;
	}
}
