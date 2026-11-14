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
package org.eclipse.swt.widgets;

import java.util.*;

/**
 * A <code>long -> int</code> table: open addressing with linear probing over two parallel arrays, so a lookup, an
 * insert or a removal allocates nothing (no boxed keys, no entry nodes). Values are non-negative; a missing key
 * answers -1. The load stays at most 1/2. A removal shifts the later entries of its probe cluster back into the hole,
 * so no tombstones are left. Used for the resident windows of the viewports (row key or native handle to window
 * entry, re-indexed by {@link #clear()}) and for the row-id positions of the row models.
 * Catalogue: hashing/open-addressing (linear probing, backward-shift deletion, 64-bit finalizer mix).
 */
final class VirtualLongIntTable {
	private long [] keys;
	/* value + 1; 0 marks an empty slot */
	private int [] values;
	private int size;

	VirtualLongIntTable () {
		keys = new long [16];
		values = new int [16];
	}

	/** A table sized for <code>expected</code> keys without growing. */
	VirtualLongIntTable (int expected) {
		int capacity = Integer.highestOneBit (Math.max (4, expected) * 2 - 1) << 1;
		keys = new long [capacity];
		values = new int [capacity];
	}

	/** The value stored for <code>key</code>, or -1. */
	int get (long key) {
		int mask = keys.length - 1;
		for (int i = mix (key) & mask;; i = (i + 1) & mask) {
			int value = values [i];
			if (value == 0) return -1;
			if (keys [i] == key) return value - 1;
		}
	}

	/**
	 * Stores <code>value</code> (non-negative) for <code>key</code>, replacing an earlier one.
	 *
	 * @return the previous value of <code>key</code>, or -1
	 */
	int put (long key, int value) {
		if (value < 0) throw new IllegalArgumentException ("negative value");
		if ((size + 1) * 2 > keys.length) grow ();
		int mask = keys.length - 1;
		for (int i = mix (key) & mask;; i = (i + 1) & mask) {
			if (values [i] == 0) {
				keys [i] = key;
				values [i] = value + 1;
				size++;
				return -1;
			}
			if (keys [i] == key) {
				int previous = values [i] - 1;
				values [i] = value + 1;
				return previous;
			}
		}
	}

	/**
	 * Removes <code>key</code>.
	 *
	 * @return the value <code>key</code> had, or -1
	 */
	int remove (long key) {
		int mask = keys.length - 1;
		int hole = mix (key) & mask;
		for (;; hole = (hole + 1) & mask) {
			if (values [hole] == 0) return -1;
			if (keys [hole] == key) break;
		}
		int previous = values [hole] - 1;
		/* backward shift: an entry later in the cluster moves into the hole unless its home lies after the hole */
		for (int next = (hole + 1) & mask; values [next] != 0; next = (next + 1) & mask) {
			int home = mix (keys [next]) & mask;
			boolean movable = hole <= next ? (home <= hole || home > next) : (home <= hole && home > next);
			if (movable) {
				keys [hole] = keys [next];
				values [hole] = values [next];
				hole = next;
			}
		}
		values [hole] = 0;
		size--;
		return previous;
	}

	int size () {
		return size;
	}

	void clear () {
		if (size == 0) return;
		Arrays.fill (values, 0);
		size = 0;
	}

	private void grow () {
		long [] oldKeys = keys;
		int [] oldValues = values;
		keys = new long [oldKeys.length * 2];
		values = new int [oldValues.length * 2];
		size = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldValues [i] != 0) put (oldKeys [i], oldValues [i] - 1);
		}
	}

	/* the 64-bit finalizer of MurmurHash3: keys differ in the low child-index bits and in the high parent bits */
	private static int mix (long key) {
		key ^= key >>> 33;
		key *= 0xff51afd7ed558ccdL;
		key ^= key >>> 33;
		key *= 0xc4ceb9fe1a85ec53L;
		key ^= key >>> 33;
		return (int) key;
	}
}
