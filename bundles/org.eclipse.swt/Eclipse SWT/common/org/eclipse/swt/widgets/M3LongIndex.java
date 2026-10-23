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

/*
 * A growable long -> int index (open addressing, linear probing, load at most 1/2, backward-shift
 * removal): two primitive lanes, no boxing, no entry objects. Values are not negative.
 */
final class M3LongIndex {
	private long [] keys;
	/* value + 1; 0 marks an empty slot */
	private int [] values;
	private int mask, size;

	M3LongIndex (int expected) {
		int capacity = Integer.highestOneBit (Math.max (4, expected) * 2 - 1) << 1;
		keys = new long [capacity];
		values = new int [capacity];
		mask = capacity - 1;
	}

	int size () {
		return size;
	}

	/* the value of a key, or -1 */
	int get (long key) {
		for (int slot = slot (key); values [slot] != 0; slot = (slot + 1) & mask) {
			if (keys [slot] == key) return values [slot] - 1;
		}
		return -1;
	}

	/* sets the value of a key; answers the previous value, or -1 */
	int put (long key, int value) {
		int slot = slot (key);
		for (; values [slot] != 0; slot = (slot + 1) & mask) {
			if (keys [slot] == key) {
				int previous = values [slot] - 1;
				values [slot] = value + 1;
				return previous;
			}
		}
		keys [slot] = key;
		values [slot] = value + 1;
		if (++size * 2 > keys.length) rehash (keys.length * 2);
		return -1;
	}

	/* removes a key; answers its value, or -1 */
	int remove (long key) {
		int slot = slot (key);
		for (; values [slot] != 0; slot = (slot + 1) & mask) {
			if (keys [slot] == key) break;
		}
		if (values [slot] == 0) return -1;
		int previous = values [slot] - 1;
		/* backward shift: move later entries of the cluster into the hole when their home allows it */
		int hole = slot;
		for (int next = (hole + 1) & mask; values [next] != 0; next = (next + 1) & mask) {
			int home = slot (keys [next]);
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

	private void rehash (int capacity) {
		long [] oldKeys = keys;
		int [] oldValues = values;
		keys = new long [capacity];
		values = new int [capacity];
		mask = capacity - 1;
		size = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldValues [i] != 0) put (oldKeys [i], oldValues [i] - 1);
		}
	}

	private int slot (long key) {
		long h = key * 0x9E3779B97F4A7C15L;
		return (int) (h ^ (h >>> 32)) & mask;
	}
}
