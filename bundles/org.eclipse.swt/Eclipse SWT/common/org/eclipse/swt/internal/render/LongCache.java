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

import java.util.*;
import java.util.function.*;

/**
 * A bounded map from <code>long</code> keys (native handles, packed part keys) to values that boxes nothing, for the
 * per-frame lookups of the drawn backends (optimization pass, frame allocations): preallocated lanes, an
 * open-addressing table with linear probing (no tombstones: a removal re-inserts the rest of its probe cluster), and
 * the clock (second chance) policy when full. An evicted or replaced value is handed to a callback, which closes its
 * native resources. One thread uses a cache. Catalogue: open addressing with backward re-insertion; clock eviction.
 *
 * @param <V> the values
 */
public final class LongCache<V> {
	private final long [] keys;
	private final Object [] values;
	private final boolean [] used;
	/* table slot -> lane + 1 (0 empty); at least twice the capacity, a power of two */
	private final int [] table;
	private final Consumer<? super V> evicted;
	private int count, hand;

	/**
	 * @param capacity the values kept (at least 1)
	 * @param evicted told about each value the cache lets go of (eviction, replacement, {@link #clear})
	 */
	public LongCache (int capacity, Consumer<? super V> evicted) {
		this.evicted = Objects.requireNonNull (evicted, "evicted");
		int lanes = Math.max (1, capacity);
		keys = new long [lanes];
		values = new Object [lanes];
		used = new boolean [lanes];
		table = new int [Integer.highestOneBit (lanes * 2 - 1) << 1];
	}

	/** The lane of a key, or -1 when absent. */
	public int lane (long key) {
		int mask = table.length - 1;
		for (int i = hash (key) & mask;; i = (i + 1) & mask) {
			int lane = table [i] - 1;
			if (lane < 0) return -1;
			if (keys [lane] == key) return lane;
		}
	}

	/** The value of a lane (see {@link #lane}), marked as used. */
	@SuppressWarnings ("unchecked")
	public V value (int lane) {
		used [lane] = true;
		return (V) values [lane];
	}

	/** The value of a key, marked as used; null when absent (or stored as null). */
	public V get (long key) {
		int lane = lane (key);
		return lane < 0 ? null : value (lane);
	}

	/** Stores a value: replaces the value of a present key, else takes a free lane or the clock's victim. */
	@SuppressWarnings ("unchecked")
	public void put (long key, V value) {
		int lane = lane (key);
		if (lane < 0) {
			if (count < keys.length) {
				lane = count++;
			} else {
				lane = victim ();
				unlink (lane);
				release ((V) values [lane]);
			}
			keys [lane] = key;
			values [lane] = value;
			link (lane);
		} else if (values [lane] != value) {
			release ((V) values [lane]);
			values [lane] = value;
		}
		used [lane] = true;
	}

	/** The values kept. */
	public int size () {
		return count;
	}

	/** Lets go of every value (each is handed to the callback). */
	@SuppressWarnings ("unchecked")
	public void clear () {
		for (int i = 0; i < count; i++) {
			release ((V) values [i]);
			values [i] = null;
			used [i] = false;
		}
		Arrays.fill (table, 0);
		count = hand = 0;
	}

	private void release (V value) {
		if (value != null) evicted.accept (value);
	}

	/* the clock: the first lane from the hand not used since the last pass */
	private int victim () {
		while (used [hand]) {
			used [hand] = false;
			hand = (hand + 1) % keys.length;
		}
		int lane = hand;
		hand = (hand + 1) % keys.length;
		return lane;
	}

	private void link (int lane) {
		int mask = table.length - 1;
		int i = hash (keys [lane]) & mask;
		while (table [i] != 0) i = (i + 1) & mask;
		table [i] = lane + 1;
	}

	/* removes a lane from the table and re-inserts the rest of its probe cluster (no tombstones) */
	private void unlink (int lane) {
		int mask = table.length - 1;
		int i = hash (keys [lane]) & mask;
		while (table [i] != lane + 1) i = (i + 1) & mask;
		table [i] = 0;
		for (int j = (i + 1) & mask; table [j] != 0; j = (j + 1) & mask) {
			int moved = table [j];
			table [j] = 0;
			link (moved - 1);
		}
	}

	private static int hash (long key) {
		long h = key * 0x9E3779B97F4A7C15L;
		return (int) (h ^ h >>> 32);
	}
}
