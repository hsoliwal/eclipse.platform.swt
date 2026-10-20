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
 * com.m3.collections.M3MaskAndPermutationTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Oracle tests: M3BitMask against java.util.BitSet, M3PermutationIndex against a stable library sort. */
public class M3MaskAndPermutationTest {
	@Test
	public void bitMaskMatchesBitSet () {
		Random random = new Random (11L);
		for (int round = 0; round < 200; round++) {
			int size = random.nextInt (300);
			long [] mask = M3BitMask.ensure (new long [0], size);
			assertTrue (mask.length >= M3BitMask.words (size));
			BitSet oracle = new BitSet ();
			for (int i = 0; i < size; i++) if (random.nextBoolean ()) {
				M3BitMask.set (mask, i, true);
				oracle.set (i);
			}
			if (size > 0 && random.nextBoolean ()) {
				int i = random.nextInt (size);
				M3BitMask.set (mask, i, false);
				oracle.clear (i);
			}
			for (int i = 0; i < size; i++) assertEquals (oracle.get (i), M3BitMask.get (mask, i));
			int words = M3BitMask.words (size);
			assertEquals (oracle.cardinality (), M3BitMask.count (mask, words));
			int [] kept = new int [size], rank = new int [Math.max (1, words)];
			int count = M3BitMask.compact (mask, words, kept, rank);
			assertArrayEquals (oracle.stream ().toArray (), java.util.Arrays.copyOf (kept, count));
			for (int i = 0; i < size; i++) {
				int expected = oracle.get (i) ? oracle.get (0, i).cardinality () : -1;
				assertEquals (expected, M3BitMask.rank (mask, rank, i));
			}
			if (size > 0) {
				int start = random.nextInt (size), n = random.nextInt (40);
				long [] grown = M3BitMask.ensure (mask, size + n);
				M3BitMask.insert (grown, size, start, n);
				BitSet shifted = new BitSet ();
				for (int i = 0; i < size; i++) if (oracle.get (i)) shifted.set (i < start ? i : i + n);
				for (int i = 0; i < size + n; i++) assertEquals (shifted.get (i), M3BitMask.get (grown, i), "insert round " + round);
				M3BitMask.remove (grown, size + n, start, n);
				for (int i = 0; i < size; i++) assertEquals (oracle.get (i), M3BitMask.get (grown, i), "remove round " + round);
			}
		}
	}

	@Test
	public void bitMaskWordEdges () {
		long [] mask = new long [3];
		M3BitMask.fill (mask, 130);
		assertEquals (130, M3BitMask.count (mask, 3));
		M3BitMask.fill (mask, 128);
		assertEquals (128, M3BitMask.count (mask, 3), "a full last word");
		M3BitMask.write (mask, 60, 0L, 10);
		assertEquals (118, M3BitMask.count (mask, 3), "a write across a word boundary");
		M3BitMask.write (mask, 64, -1L, 64);
		assertEquals (-1L, M3BitMask.read (mask, 64));
		assertEquals (0L, M3BitMask.read (mask, 192), "past the lane reads 0");
		assertEquals (mask [2], M3BitMask.read (mask, 128));
		M3BitMask.clear (mask, 0, 192);
		assertEquals (0, M3BitMask.count (mask, 3));
		long [] same = M3BitMask.ensure (mask, 100);
		assertSame (mask, same);
	}

	private static final class IntKey extends M3RadixKey {
		private final int [] values;

		IntKey (int [] values) {
			this.values = values;
		}

		@Override
		public int radixKeys (int [] rows, int n, M3PermutationIndex lanes) {
			long max = 0;
			for (int i = 0; i < n; i++) {
				lanes.keys [i] = values [rows [i]];
				max = Math.max (max, values [rows [i]]);
			}
			return M3PermutationIndex.bytesFor (max);
		}
	}

	@Test
	public void permutationMatchesStableSort () {
		Random random = new Random (13L);
		M3PermutationIndex index = new M3PermutationIndex ();
		for (int round = 0; round < 120; round++) {
			int n = random.nextInt (round < 40 ? 47 : 400);
			int [] a = random.ints (n, 0, 1 + random.nextInt (5000)).toArray ();
			int [] b = random.ints (n, 0, 4).toArray ();
			boolean descA = random.nextBoolean (), descB = random.nextBoolean ();
			Comparator<Integer> byA = Comparator.comparingInt (r -> a [r]);
			Comparator<Integer> byB = Comparator.comparingInt (r -> b [r]);
			Comparator<Integer> order = (descB ? byB.reversed () : byB).thenComparing (descA ? byA.reversed () : byA);
			List<Integer> expected = new ArrayList<> (IntStream.range (0, n).boxed ().toList ());
			java.util.Collections.shuffle (expected, random);
			int [] rows = expected.stream ().mapToInt (Integer::intValue).toArray ();
			expected.sort (order.thenComparing (Comparator.naturalOrder ()));
			index.sort (rows, n, new M3RadixKey [] {new IntKey (b), new IntKey (a)}, new boolean [] {descB, descA}, 2,
					(x, y) -> order.thenComparing (Comparator.naturalOrder ()).compare (x, y));
			assertArrayEquals (expected.stream ().mapToInt (Integer::intValue).toArray (), rows, "round " + round + " n " + n);
		}
	}

	@Test
	public void ranksSourceOrderAndBytes () {
		M3PermutationIndex index = new M3PermutationIndex ();
		index.ensure (5);
		int [] values = {30, 10, 30, 20, 10};
		assertEquals (2, index.ranks (5, (x, y) -> Integer.compare (values [x], values [y])));
		assertArrayEquals (new long [] {2, 0, 2, 1, 0}, java.util.Arrays.copyOf (index.keys, 5));
		assertEquals (0, index.ranks (0, (x, y) -> 0));
		int [] rows = {4, 2, 0, 3, 1};
		index.sourceOrder (rows, 5);
		assertArrayEquals (new int [] {0, 1, 2, 3, 4}, rows);
		index.sourceOrder (rows, 5);
		assertArrayEquals (new int [] {0, 1, 2, 3, 4}, rows, "already in order");
		int [] one = {7};
		index.sort (one, 1, new M3RadixKey [0], new boolean [0], 0, (x, y) -> 0);
		assertArrayEquals (new int [] {7}, one);
		int [] noKeys = {3, 1, 2};
		index.sort (noKeys, 3, new M3RadixKey [0], new boolean [0], 0, (x, y) -> 0);
		assertArrayEquals (new int [] {1, 2, 3}, noKeys, "no keys: source order");
		assertEquals (1, M3PermutationIndex.bytesFor (0));
		assertEquals (1, M3PermutationIndex.bytesFor (255));
		assertEquals (2, M3PermutationIndex.bytesFor (256));
		assertEquals (8, M3PermutationIndex.bytesFor (-1L));
		String [] texts = index.texts (3);
		texts [0] = "x";
		index.releaseTexts (3);
		assertNull (index.texts (3) [0]);
		int [] constant = {5, 4, 3, 2, 1, 0, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
				27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49};
		int [] same = new int [50];
		index.sort (constant, 50, new M3RadixKey [] {new IntKey (same)}, new boolean [] {false}, 1, (x, y) -> Integer.compare (x, y));
		assertArrayEquals (IntStream.range (0, 50).toArray (), constant, "equal keys keep source order");
		int [] presorted = IntStream.range (0, 40).toArray ();
		index.sort (presorted, 40, new M3RadixKey [] {new IntKey (same)}, new boolean [] {false}, 1, Integer::compare);
		assertArrayEquals (IntStream.range (0, 40).toArray (), presorted, "ordered runs are copied, not merged");
		int [] wide = IntStream.range (0, 60).toArray ();
		M3RadixKey eightBytes = new M3RadixKey () {
			@Override
			public int radixKeys (int [] r, int n, M3PermutationIndex lanes) {
				for (int i = 0; i < n; i++) lanes.keys [i] = r [i] * 1_000_000_007L;
				return 8;
			}
		};
		index.sort (wide, 60, new M3RadixKey [] {eightBytes}, new boolean [] {true}, 1, (x, y) -> Integer.compare (y, x));
		assertArrayEquals (IntStream.range (0, 60).map (i -> 59 - i).toArray (), wide, "descending on all eight key bytes");
	}
}
