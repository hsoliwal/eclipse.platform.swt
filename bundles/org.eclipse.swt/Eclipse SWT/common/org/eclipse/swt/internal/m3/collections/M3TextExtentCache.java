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
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded cache of measured text extents keyed by (label, font id).
 *
 * <p>Rows that share a label share its measurement: computing the widest row
 * of a column measures every distinct label once, not every row. A label is an
 * {@link M3LabelPool} id or an {@link M3ShortLabel} value (both non-negative
 * {@code long}s); values pack width and height into one {@code long}
 * ({@link #pack(int, int)}). Lookup is open addressing with linear probing and
 * backward-shift deletion over parallel label, font and extent lanes; when the
 * cache is full, a CLOCK hand (one reference byte per slot) picks the entry to
 * drop.</p>
 *
 * <p>Invalidate with {@link #invalidateFont(int)} when a font changes,
 * {@link #invalidateAll()} when the zoom or DPI changes, and
 * {@link #invalidateLabel(int)} when a label id is evicted from its pool (ids
 * are reused; immediate labels never are). Not thread-safe; owned by one
 * model.</p>
 */
public final class M3TextExtentCache {
	/** Measures the text of a label in a font; called only on a cache miss. */
	@FunctionalInterface
	public interface Measurer {
		/** Returns the extent of {@code label} in {@code fontId}, packed by {@link M3TextExtentCache#pack(int, int)}. */
		long measure (long label, int fontId);
	}

	private static final long EMPTY = -1L;
	private static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

	private final int maxEntries;
	private final long [] labels;
	private final int [] fonts;
	private final long [] values;
	private final byte [] referenced;
	private final int shift;
	private int size, hand, fontCount;
	private int [] fontIds = new int [4];
	private long hits, misses, evictions;

	/** Creates a cache that holds at most {@code maxEntries} extents. */
	public M3TextExtentCache (int maxEntries) {
		if (maxEntries < 1 || maxEntries > 1 << 29) throw new IllegalArgumentException ("maxEntries out of range: " + maxEntries);
		this.maxEntries = maxEntries;
		int capacity = Integer.highestOneBit (maxEntries * 2 - 1) << 1;
		labels = new long [capacity];
		fonts = new int [capacity];
		values = new long [capacity];
		referenced = new byte [capacity];
		shift = 64 - Integer.numberOfTrailingZeros (capacity);
		Arrays.fill (labels, EMPTY);
	}

	/** Packs a width and a height into one extent value. */
	public static long pack (int width, int height) {
		return (long) width << 32 | (height & 0xFFFF_FFFFL);
	}

	/** Width of a packed extent. */
	public static int width (long extent) {
		return (int) (extent >> 32);
	}

	/** Height of a packed extent. */
	public static int height (long extent) {
		return (int) extent;
	}

	/** Returns the extent of {@code label} in {@code fontId}, measuring it on a miss. */
	public long extent (long label, int fontId, Measurer measurer) {
		checkLabel (label);
		int index = indexOf (label, fontId);
		if (index >= 0) {
			hits++;
			referenced [index] = 1;
			return values [index];
		}
		misses++;
		long value = measurer.measure (label, fontId);
		if (size == maxEntries) evictOne ();
		insert (label, fontId, value, (byte) 1);
		noteFont (fontId);
		return value;
	}

	/** Returns the extent of pool id {@code labelId} in {@code fontId}, measuring it on a miss. */
	public long extent (int labelId, int fontId, Measurer measurer) {
		return extent ((long) labelId, fontId, measurer);
	}

	/** Returns the cached extent of {@code label} in {@code fontId} without measuring, or -1. */
	public long peek (long label, int fontId) {
		checkLabel (label);
		int index = indexOf (label, fontId);
		return index < 0 ? -1L : values [index];
	}

	/** Width of {@code label} in {@code fontId}. */
	public int width (long label, int fontId, Measurer measurer) {
		return width (extent (label, fontId, measurer));
	}

	/**
	 * Width of the widest label among {@code labels [from, to)} in {@code fontId},
	 * 0 when the range is empty. Each distinct label is measured at most once
	 * while it stays cached.
	 */
	public int widest (int [] labelIds, int from, int to, int fontId, Measurer measurer) {
		Objects.checkFromToIndex (from, to, labelIds.length);
		int widest = 0;
		for (int i = from; i < to; i++) widest = Math.max (widest, width (extent ((long) labelIds [i], fontId, measurer)));
		return widest;
	}

	/** {@link #widest(int[], int, int, int, Measurer)} over a lane of {@code long} labels. */
	public int widest (long [] labelValues, int from, int to, int fontId, Measurer measurer) {
		Objects.checkFromToIndex (from, to, labelValues.length);
		int widest = 0;
		for (int i = from; i < to; i++) widest = Math.max (widest, width (extent (labelValues [i], fontId, measurer)));
		return widest;
	}

	/** Drops every extent of pool id {@code labelId}, in every font measured so far. */
	public void invalidateLabel (int labelId) {
		invalidateLabel ((long) labelId);
	}

	/** Drops every extent of {@code label}, in every font measured so far. */
	public void invalidateLabel (long label) {
		checkLabel (label);
		for (int i = 0; i < fontCount; i++) {
			int index = indexOf (label, fontIds [i]);
			if (index >= 0) deleteAt (index);
		}
	}

	/** Drops every extent measured in {@code fontId}. */
	public void invalidateFont (int fontId) {
		for (int i = 0; i < labels.length; ) {
			if (labels [i] != EMPTY && fonts [i] == fontId) {
				deleteAt (i);
			} else {
				i++;
			}
		}
		forgetFont (fontId);
	}

	/** Drops every extent, for example after a zoom or DPI change. */
	public void invalidateAll () {
		Arrays.fill (labels, EMPTY);
		Arrays.fill (referenced, (byte) 0);
		size = 0;
		hand = 0;
		fontCount = 0;
	}

	/** Number of cached extents. */
	public int size () {
		return size;
	}

	/** Upper bound of {@link #size()}. */
	public int maxEntries () {
		return maxEntries;
	}

	/** Lookups answered from the cache. */
	public long hits () {
		return hits;
	}

	/** Lookups that called the measurer: the measurements the toolkit had to make. */
	public long misses () {
		return misses;
	}

	/** Extents dropped by the CLOCK hand to stay within {@link #maxEntries()}. */
	public long evictions () {
		return evictions;
	}

	/** Estimated heap bytes retained by the cache arrays. */
	public long retainedBytes () {
		return 5L * 16 + labels.length * 21L + fontIds.length * 4L;
	}

	private int indexOf (long label, int fontId) {
		int mask = labels.length - 1;
		for (int i = slot (label, fontId); ; i = (i + 1) & mask) {
			long found = labels [i];
			if (found == label && fonts [i] == fontId) return i;
			if (found == EMPTY) return -1;
		}
	}

	private void insert (long label, int fontId, long value, byte reference) {
		int mask = labels.length - 1, i = slot (label, fontId);
		while (labels [i] != EMPTY) i = (i + 1) & mask;
		labels [i] = label;
		fonts [i] = fontId;
		values [i] = value;
		referenced [i] = reference;
		size++;
	}

	private void evictOne () {
		int mask = labels.length - 1;
		while (true) {
			int i = hand;
			hand = (hand + 1) & mask;
			if (labels [i] == EMPTY) continue;
			if (referenced [i] != 0) {
				referenced [i] = 0;
				continue;
			}
			deleteAt (i);
			evictions++;
			return;
		}
	}

	private void deleteAt (int hole) {
		int mask = labels.length - 1;
		for (int i = (hole + 1) & mask; labels [i] != EMPTY; i = (i + 1) & mask) {
			int home = slot (labels [i], fonts [i]);
			if (((i - home) & mask) >= ((i - hole) & mask)) {
				labels [hole] = labels [i];
				fonts [hole] = fonts [i];
				values [hole] = values [i];
				referenced [hole] = referenced [i];
				hole = i;
			}
		}
		labels [hole] = EMPTY;
		referenced [hole] = 0;
		size--;
	}

	private void noteFont (int fontId) {
		for (int i = 0; i < fontCount; i++) {
			if (fontIds [i] == fontId) return;
		}
		if (fontCount == fontIds.length) fontIds = Arrays.copyOf (fontIds, fontCount * 2);
		fontIds [fontCount++] = fontId;
	}

	private void forgetFont (int fontId) {
		for (int i = 0; i < fontCount; i++) {
			if (fontIds [i] == fontId) {
				fontIds [i] = fontIds [--fontCount];
				return;
			}
		}
	}

	private int slot (long label, int fontId) {
		return (int) (((label * GOLDEN) ^ fontId) * GOLDEN >>> shift);
	}

	private static void checkLabel (long label) {
		if (label < 0) throw new IllegalArgumentException ("negative label: " + label);
	}
}
