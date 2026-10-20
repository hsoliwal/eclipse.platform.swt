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
 * A <code>long -> int</code> table for the resident windows of the viewports (row key or native handle to window
 * entry): open addressing with linear probing over two parallel arrays, so a lookup or an insert allocates nothing
 * (no boxed keys, no entry nodes). Values are non-negative; a missing key answers -1. There is no single remove: a
 * window is re-indexed by {@link #clear()} (O(capacity), and the capacity follows the largest window) and re-filled.
 * Catalogue: hashing/open-addressing (linear probing, 64-bit finalizer mix).
 */
final class VirtualLongIntTable {
	private long [] keys = new long [16];
	/* value + 1; 0 marks an empty slot */
	private int [] values = new int [16];
	private int size;

	/** The value stored for <code>key</code>, or -1. */
	int get (long key) {
		int mask = keys.length - 1;
		for (int i = mix (key) & mask;; i = (i + 1) & mask) {
			int value = values [i];
			if (value == 0) return -1;
			if (keys [i] == key) return value - 1;
		}
	}

	/** Stores <code>value</code> (non-negative) for <code>key</code>, replacing an earlier one. */
	void put (long key, int value) {
		if (value < 0) throw new IllegalArgumentException ("negative value");
		if ((size + 1) * 2 > keys.length) grow ();
		int mask = keys.length - 1;
		for (int i = mix (key) & mask;; i = (i + 1) & mask) {
			if (values [i] == 0) {
				keys [i] = key;
				values [i] = value + 1;
				size++;
				return;
			}
			if (keys [i] == key) {
				values [i] = value + 1;
				return;
			}
		}
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
