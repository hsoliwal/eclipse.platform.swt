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
 * Bounded cache of measured text extents keyed by (label id, font id).
 *
 * <p>Rows that share a label id share its measurement: computing the widest
 * row of a column measures every distinct label once, not every row. Keys pack
 * the label id and the font id into one {@code long}; values pack width and
 * height into one {@code long} ({@link #pack(int, int)}). Lookup is open
 * addressing with linear probing and backward-shift deletion; when the cache is
 * full, a CLOCK hand (one reference byte per slot) picks the entry to drop.</p>
 *
 * <p>Invalidate with {@link #invalidateFont(int)} when a font changes,
 * {@link #invalidateAll()} when the zoom or DPI changes, and
 * {@link #invalidateLabel(int)} when a label id is evicted from its pool (ids
 * are reused). Not thread-safe; owned by one model.</p>
 */
public final class M3TextExtentCache {
	/** Measures the text of a label in a font; called only on a cache miss. */
	@FunctionalInterface
	public interface Measurer {
		/** Returns the extent of {@code labelId} in {@code fontId}, packed by {@link M3TextExtentCache#pack(int, int)}. */
		long measure (int labelId, int fontId);
	}

	private static final long EMPTY = -1L;
	private static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

	private final int maxEntries;
	private long [] keys;
	private long [] values;
	private byte [] referenced;
	private final int shift;
	private int size, hand, fontCount;
	private int [] fonts = new int [4];
	private long hits, misses, evictions;

	/** Creates a cache that holds at most {@code maxEntries} extents. */
	public M3TextExtentCache (int maxEntries) {
		if (maxEntries < 1 || maxEntries > 1 << 29) throw new IllegalArgumentException ("maxEntries out of range: " + maxEntries);
		this.maxEntries = maxEntries;
		int capacity = Integer.highestOneBit (maxEntries * 2 - 1) << 1;
		keys = new long [capacity];
		values = new long [capacity];
		referenced = new byte [capacity];
		shift = 64 - Integer.numberOfTrailingZeros (capacity);
		Arrays.fill (keys, EMPTY);
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

	/** Returns the extent of {@code labelId} in {@code fontId}, measuring it on a miss. */
	public long extent (int labelId, int fontId, Measurer measurer) {
		long key = key (labelId, fontId);
		int index = indexOf (key);
		if (index >= 0) {
			hits++;
			referenced [index] = 1;
			return values [index];
		}
		misses++;
		long value = measurer.measure (labelId, fontId);
		if (size == maxEntries) evictOne ();
		insert (key, value, (byte) 1);
		noteFont (fontId);
		return value;
	}

	/** Returns the cached extent of {@code labelId} in {@code fontId} without measuring, or -1. */
	public long peek (int labelId, int fontId) {
		int index = indexOf (key (labelId, fontId));
		return index < 0 ? -1L : values [index];
	}

	/** Width of {@code labelId} in {@code fontId}. */
	public int width (int labelId, int fontId, Measurer measurer) {
		return width (extent (labelId, fontId, measurer));
	}

	/**
	 * Width of the widest label among {@code labelIds [from, to)} in
	 * {@code fontId}, 0 when the range is empty. Each distinct label is measured
	 * at most once while it stays cached.
	 */
	public int widest (int [] labelIds, int from, int to, int fontId, Measurer measurer) {
		Objects.checkFromToIndex (from, to, labelIds.length);
		int widest = 0;
		for (int i = from; i < to; i++) widest = Math.max (widest, width (extent (labelIds [i], fontId, measurer)));
		return widest;
	}

	/** Drops every extent of {@code labelId}, in every font measured so far. */
	public void invalidateLabel (int labelId) {
		for (int i = 0; i < fontCount; i++) {
			int index = indexOf (key (labelId, fonts [i]));
			if (index >= 0) deleteAt (index);
		}
	}

	/** Drops every extent measured in {@code fontId}. */
	public void invalidateFont (int fontId) {
		long [] oldKeys = keys, oldValues = values;
		byte [] oldReferenced = referenced;
		keys = new long [oldKeys.length];
		values = new long [oldKeys.length];
		referenced = new byte [oldKeys.length];
		Arrays.fill (keys, EMPTY);
		size = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			long key = oldKeys [i];
			if (key != EMPTY && (int) key != fontId) insert (key, oldValues [i], oldReferenced [i]);
		}
		forgetFont (fontId);
	}

	/** Drops every extent, for example after a zoom or DPI change. */
	public void invalidateAll () {
		Arrays.fill (keys, EMPTY);
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
		return 4L * 16 + keys.length * 17L + fonts.length * 4L;
	}

	private int indexOf (long key) {
		int mask = keys.length - 1;
		for (int i = slot (key); ; i = (i + 1) & mask) {
			long found = keys [i];
			if (found == key) return i;
			if (found == EMPTY) return -1;
		}
	}

	private void insert (long key, long value, byte reference) {
		int mask = keys.length - 1, i = slot (key);
		while (keys [i] != EMPTY) i = (i + 1) & mask;
		keys [i] = key;
		values [i] = value;
		referenced [i] = reference;
		size++;
	}

	private void evictOne () {
		int mask = keys.length - 1;
		while (true) {
			int i = hand;
			hand = (hand + 1) & mask;
			if (keys [i] == EMPTY) continue;
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
		int mask = keys.length - 1;
		for (int i = (hole + 1) & mask; keys [i] != EMPTY; i = (i + 1) & mask) {
			int home = slot (keys [i]);
			if (((i - home) & mask) >= ((i - hole) & mask)) {
				keys [hole] = keys [i];
				values [hole] = values [i];
				referenced [hole] = referenced [i];
				hole = i;
			}
		}
		keys [hole] = EMPTY;
		referenced [hole] = 0;
		size--;
	}

	private void noteFont (int fontId) {
		for (int i = 0; i < fontCount; i++) {
			if (fonts [i] == fontId) return;
		}
		if (fontCount == fonts.length) fonts = Arrays.copyOf (fonts, fontCount * 2);
		fonts [fontCount++] = fontId;
	}

	private void forgetFont (int fontId) {
		for (int i = 0; i < fontCount; i++) {
			if (fonts [i] == fontId) {
				fonts [i] = fonts [--fontCount];
				return;
			}
		}
	}

	private int slot (long key) {
		return (int) ((key * GOLDEN) >>> shift);
	}

	private static long key (int labelId, int fontId) {
		if (labelId < 0) throw new IllegalArgumentException ("negative label id: " + labelId);
		return (long) labelId << 32 | (fontId & 0xFFFF_FFFFL);
	}
}
