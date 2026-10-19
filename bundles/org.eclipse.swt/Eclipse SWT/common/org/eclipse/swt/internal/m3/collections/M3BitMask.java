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
 * com.m3.collections.M3BitMask (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 5593cc43baad179097f1fc3f72c9e5d8b6f8fb8a,
 * M3RowMask.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/*
 * Row masks: one bit per source row in a long[] lane (bit i of word i >>> 6). Bit runs are moved
 * 64 bits at a time for insertions and removals of source rows; the kept lane of a mask is its
 * compaction (trailing-zero walk) and a view row is found back by a rank query (per-word prefix
 * popcounts + bitCount of the masked word).
 */
public final class M3BitMask {

	private M3BitMask () {
	}

	public static int words (int bits) {
		return (bits + 63) >>> 6;
	}

	public static long [] ensure (long [] mask, int bits) {
		int words = words (bits);
		if (mask.length >= words) return mask;
		long [] grown = new long [Math.max (words, mask.length + (mask.length >> 1))];
		System.arraycopy (mask, 0, grown, 0, mask.length);
		return grown;
	}

	public static boolean get (long [] mask, int bit) {
		return (mask [bit >>> 6] & 1L << bit) != 0;
	}

	public static void set (long [] mask, int bit, boolean value) {
		if (value) mask [bit >>> 6] |= 1L << bit; else mask [bit >>> 6] &= ~(1L << bit);
	}

	/* sets bits [0, size) and clears the rest of the lane */
	public static void fill (long [] mask, int size) {
		int full = size >>> 6;
		java.util.Arrays.fill (mask, 0, full, -1L);
		java.util.Arrays.fill (mask, full, mask.length, 0L);
		if ((size & 63) != 0) mask [full] = (1L << size) - 1;
	}

	/* bits [start, size) move up by count; the count bits from start are cleared (capacity size + count) */
	public static void insert (long [] mask, int size, int start, int count) {
		for (int remaining = size - start; remaining > 0;) {
			int length = Math.min (64, remaining);
			remaining -= length;
			write (mask, start + remaining + count, read (mask, start + remaining), length);
		}
		clear (mask, start, count);
	}

	/* bits [start + count, size) move down by count; the count bits at the end are cleared */
	public static void remove (long [] mask, int size, int start, int count) {
		for (int done = 0, total = size - start - count; done < total;) {
			int length = Math.min (64, total - done);
			write (mask, start + done, read (mask, start + count + done), length);
			done += length;
		}
		clear (mask, size - count, count);
	}

	public static void clear (long [] mask, int start, int count) {
		for (int done = 0; done < count;) {
			int length = Math.min (64, count - done);
			write (mask, start + done, 0, length);
			done += length;
		}
	}

	/* the 64 bits from position (bits past the lane read as 0) */
	public static long read (long [] mask, int position) {
		int word = position >>> 6, bit = position & 63;
		long low = word < mask.length ? mask [word] >>> bit : 0;
		if (bit == 0) return low;
		long high = word + 1 < mask.length ? mask [word + 1] << (64 - bit) : 0;
		return low | high;
	}

	/* writes the low length (1 .. 64) bits of value at position */
	public static void write (long [] mask, int position, long value, int length) {
		int word = position >>> 6, bit = position & 63;
		long ones = length == 64 ? -1L : (1L << length) - 1;
		value &= ones;
		mask [word] = mask [word] & ~(ones << bit) | value << bit;
		if (bit + length > 64) {
			int shift = 64 - bit;
			mask [word + 1] = mask [word + 1] & ~(ones >>> shift) | value >>> shift;
		}
	}

	/* writes the positions of the set bits of mask [0, words) into kept (capacity: their count); returns the count */
	public static int compact (long [] mask, int words, int [] kept, int [] rank) {
		int count = 0;
		for (int w = 0; w < words; w++) {
			rank [w] = count;
			for (long x = mask [w]; x != 0; x &= x - 1) kept [count++] = w << 6 | Long.numberOfTrailingZeros (x);
		}
		return count;
	}

	/* the number of set bits of mask [0, words) */
	public static int count (long [] mask, int words) {
		int count = 0;
		for (int w = 0; w < words; w++) count += Long.bitCount (mask [w]);
		return count;
	}

	/* the view row of a set bit, -1 for a clear bit */
	public static int rank (long [] mask, int [] rank, int bit) {
		long word = mask [bit >>> 6], b = 1L << bit;
		return (word & b) == 0 ? -1 : rank [bit >>> 6] + Long.bitCount (word & b - 1);
	}
}
