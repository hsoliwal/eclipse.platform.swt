/*******************************************************************************
 * Copyright (c) 2026 Contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.BitSet;
import java.util.Random;

import org.eclipse.swt.internal.residency.ItemBitSet;
import org.junit.jupiter.api.Test;

/**
 * Deterministic, bounded differential contract for sparse M3 item state.
 * An independent java.util.BitSet oracle checks cross-container boundaries,
 * sparse points, dense runs, toggles, counts and bounded next-set lookup.
 * The oracle's domain stays small; no int-domain-wide allocation is used.
 */
public class Test_ItemBitSetDifferential {
	private static final int LIMIT = 3 * 65536 + 97;

	private static void probe(ItemBitSet actual, BitSet expected, int from, int to) {
		assertEquals(expected.get(from), actual.get(from), "get at " + from);
		assertEquals(expected.get(to - 1), actual.get(to - 1), "get at " + (to - 1));
		assertEquals(expected.get(from, to).cardinality(), actual.count(from, to),
				"cardinality [" + from + ", " + to + ")");
		int expectedNext = expected.nextSetBit(from);
		if (expectedNext >= to) expectedNext = -1;
		assertEquals(expectedNext, actual.next(from, to),
				"next [" + from + ", " + to + ")");
	}

	@Test
	public void testCrossContainerBoundariesAndClears() {
		ItemBitSet actual = new ItemBitSet();
		BitSet expected = new BitSet();
		assertEquals(-1, actual.next(0, LIMIT));
		for (int position : new int[] {0, 31, 32, 4095, 4096,
				65534, 65535, 65536, 65537, 131071, 131072, LIMIT - 1}) {
			actual.set(position, true);
			expected.set(position);
		}
		actual.setRange(65532, 65540, true);
		expected.set(65532, 65540);
		actual.setRange(65534, 65538, false);
		expected.clear(65534, 65538);
		actual.set(131072, false);
		expected.clear(131072);
		for (int position : new int[] {0, 30, 31, 32, 4094, 4095, 4096,
				65532, 65534, 65536, 65538, 131070, 131071, 131072, LIMIT - 1}) {
			probe(actual, expected, position, Math.min(LIMIT, position + 97));
		}
		probe(actual, expected, 0, LIMIT);
	}

	@Test
	public void testFullRunToBitmapAndBack() {
		ItemBitSet actual = new ItemBitSet();
		BitSet expected = new BitSet();
		actual.setRange(0, 65536, true);
		expected.set(0, 65536);
		actual.setRange(1000, 3000, false);
		expected.clear(1000, 3000);
		for (int position = 1; position < 65536; position += 2) {
			actual.set(position, false);
			expected.clear(position);
		}
		probe(actual, expected, 0, 65536);
		probe(actual, expected, 1001, 2000);
		actual.setRange(0, 65536, false);
		expected.clear(0, 65536);
		probe(actual, expected, 0, 65536);
		assertEquals(-1, actual.next(0, LIMIT));
	}

	@Test
	public void testSeededMutationDifferential() {
		Random random = new Random(20261107L);
		ItemBitSet actual = new ItemBitSet();
		BitSet expected = new BitSet();
		for (int step = 0; step < 1500; step++) {
			int from = random.nextInt(LIMIT);
			int to = Math.min(LIMIT, from + 1 + random.nextInt(2048));
			switch (random.nextInt(4)) {
				case 0:
					actual.set(from, true);
					expected.set(from);
					break;
				case 1:
					actual.set(from, false);
					expected.clear(from);
					break;
				case 2:
					actual.setRange(from, to, true);
					expected.set(from, to);
					break;
				default:
					actual.setRange(from, to, false);
					expected.clear(from, to);
			}
			if (step % 17 == 0) {
				probe(actual, expected, from, to);
				probe(actual, expected, 0, LIMIT);
				int crossing = step % 2 == 0 ? 65530 : 131066;
				probe(actual, expected, crossing, crossing + 32);
			}
		}
		for (int from = 0; from < LIMIT; from += 3137) {
			probe(actual, expected, from, Math.min(LIMIT, from + 3137));
		}
	}
}
