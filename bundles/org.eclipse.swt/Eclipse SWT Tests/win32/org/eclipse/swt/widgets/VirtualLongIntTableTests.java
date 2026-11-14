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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * VirtualLongIntTable against a <code>HashMap</code> oracle: every put, get and remove answers what the map answers
 * (previous value or -1), over random operation mixes on dense, sparse and clustered keys, with and without a size
 * hint. Removal is the backward-shift deletion of the row models, so the probe clusters it compacts are checked by
 * looking up every live and every removed key after each round.
 */
class VirtualLongIntTableTests {

	private static final int ROUNDS = 40;
	private static final int OPERATIONS = 2000;

	@Test
	void putGetRemoveMatchAMap() {
		Random random = new Random(42);
		for (int round = 0; round < ROUNDS; round++) {
			int range = 1 << (2 + round % 12);
			VirtualLongIntTable table = round % 2 == 0 ? new VirtualLongIntTable() : new VirtualLongIntTable(random.nextInt(64));
			Map<Long, Integer> map = new HashMap<>();
			for (int i = 0; i < OPERATIONS; i++) {
				long key = key(random, round, range);
				int op = random.nextInt(3);
				if (op == 0) {
					int value = random.nextInt(1 << 20);
					assertEquals(orMinusOne(map.put(key, value)), table.put(key, value), "put");
				} else if (op == 1) {
					assertEquals(orMinusOne(map.remove(key)), table.remove(key), "remove");
				} else {
					assertEquals(orMinusOne(map.get(key)), table.get(key), "get");
				}
				assertEquals(map.size(), table.size(), "size");
			}
			for (long key = 0; key < range; key++) {
				long probe = key(round, key);
				assertEquals(orMinusOne(map.get(probe)), table.get(probe), "lookup after the round");
			}
		}
	}

	@Test
	void removeEveryKeyInAnyOrder() {
		Random random = new Random(7);
		int count = 5000;
		long[] keys = new long[count];
		VirtualLongIntTable table = new VirtualLongIntTable(count);
		for (int i = 0; i < count; i++) {
			keys[i] = (long) i << 32 | i;
			assertEquals(-1, table.put(keys[i], i));
		}
		for (int i = count - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			long swap = keys[i];
			keys[i] = keys[j];
			keys[j] = swap;
		}
		for (int i = 0; i < count; i++) {
			assertEquals((int) keys[i], table.remove(keys[i]));
			assertEquals(-1, table.remove(keys[i]));
			for (int k = i + 1; k < count; k += 97) {
				assertEquals((int) keys[k], table.get(keys[k]), "a later key is still found");
			}
		}
		assertEquals(0, table.size());
	}

	@Test
	void clearAndNegativeValues() {
		VirtualLongIntTable table = new VirtualLongIntTable();
		for (int i = 0; i < 100; i++) table.put(i, i);
		table.clear();
		assertEquals(0, table.size());
		assertEquals(-1, table.get(5));
		assertEquals(-1, table.put(5, 0));
		assertEquals(0, table.put(5, 1));
		assertThrows(IllegalArgumentException.class, () -> table.put(6, -1));
	}

	/* dense small ranges collide in few slots; the high bits imitate the parent ids of tree rows */
	private static long key(Random random, int round, int range) {
		return key(round, random.nextInt(range));
	}

	private static long key(int round, long index) {
		switch (round % 3) {
		case 0: return index;
		case 1: return index << 32;
		default: return index * 1024 + (Long.MIN_VALUE >>> 1);
		}
	}

	private static int orMinusOne(Integer value) {
		return value == null ? -1 : value;
	}
}
