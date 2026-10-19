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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 826e57b779174368fec5c4295eca9e3ad794c2e1,
 * com.m3.collections.M3BoundedCache (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;
import java.util.Objects;

/**
 * A bounded key/value cache with CLOCK (second-chance) eviction, held in lanes: key and value arrays, a cached hash
 * lane, reference and occupancy bit words, and an open-addressed slot index ({@code int} table, linear probing,
 * backward-shift deletion). No entry objects; no allocation after construction.
 * <p>{@link #get(Object)} marks the slot referenced; when the cache is full, {@link #put(Object, Object)} advances the
 * clock hand, clearing reference bits until it finds an unreferenced slot, and evicts it. Keys and values are
 * non-null; keys use {@code equals}/{@code hashCode}.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class M3BoundedCache<K, V> {
	/** Told about every eviction (not about {@link #remove(Object)} or {@link #clear()}). */
	@FunctionalInterface
	public interface Evictor<K, V> {
		void evicted (K key, V value);
	}

	private final int capacity;
	private final Object [] keys, values;
	private final int [] hashes;
	private final long [] referenced, occupied;
	/* slot + 1 per bucket, 0 = empty; length is a power of two of at least twice the capacity */
	private final int [] table;
	private final int mask;
	private int size, hand;
	private long hits, misses, evictions;
	private Evictor<K, V> evictor;

	/** A cache of at most {@code capacity} entries ({@code capacity >= 1}). */
	public M3BoundedCache (int capacity) {
		if (capacity < 1 || capacity > (1 << 29)) throw new IllegalArgumentException ("capacity 1.." + (1 << 29) + ": " + capacity);
		this.capacity = capacity;
		keys = new Object [capacity];
		values = new Object [capacity];
		hashes = new int [capacity];
		referenced = new long [(capacity + 63) >>> 6];
		occupied = new long [(capacity + 63) >>> 6];
		table = new int [Integer.highestOneBit (capacity * 2 - 1) << 1];
		mask = table.length - 1;
	}

	public int capacity () {
		return capacity;
	}

	public int size () {
		return size;
	}

	public long hits () {
		return hits;
	}

	public long misses () {
		return misses;
	}

	public long evictions () {
		return evictions;
	}

	public void evictor (Evictor<K, V> evictor) {
		this.evictor = evictor;
	}

	/** The value of {@code key}, marking it recently used, or {@code null}. */
	@SuppressWarnings ("unchecked")
	public V get (K key) {
		int slot = find (key);
		if (slot < 0) {
			misses++;
			return null;
		}
		hits++;
		set (referenced, slot);
		return (V) values [slot];
	}

	/** Whether {@code key} is cached; does not mark it used. */
	public boolean containsKey (K key) {
		return find (key) >= 0;
	}

	/** Caches {@code value} under {@code key}; answers the previous value of that key, or {@code null}. */
	@SuppressWarnings ("unchecked")
	public V put (K key, V value) {
		Objects.requireNonNull (value, "value");
		int slot = find (key);
		if (slot >= 0) {
			V old = (V) values [slot];
			values [slot] = value;
			set (referenced, slot);
			return old;
		}
		slot = size < capacity ? freeSlot () : evict ();
		int hash = spread (key.hashCode ());
		keys [slot] = key;
		values [slot] = value;
		hashes [slot] = hash;
		set (occupied, slot);
		clear (referenced, slot);
		int bucket = hash & mask;
		while (table [bucket] != 0) bucket = (bucket + 1) & mask;
		table [bucket] = slot + 1;
		size++;
		return null;
	}

	/** Removes {@code key}; answers its value, or {@code null}. */
	@SuppressWarnings ("unchecked")
	public V remove (K key) {
		int slot = find (key);
		if (slot < 0) return null;
		V old = (V) values [slot];
		release (slot);
		return old;
	}

	/** Drops every entry. */
	public void clear () {
		Arrays.fill (keys, null);
		Arrays.fill (values, null);
		Arrays.fill (referenced, 0L);
		Arrays.fill (occupied, 0L);
		Arrays.fill (table, 0);
		size = 0;
		hand = 0;
	}

	private int find (K key) {
		int hash = spread (key.hashCode ());
		for (int bucket = hash & mask;; bucket = (bucket + 1) & mask) {
			int slot = table [bucket] - 1;
			if (slot < 0) return -1;
			if (hashes [slot] == hash && key.equals (keys [slot])) return slot;
		}
	}

	private int freeSlot () {
		for (int word = 0;; word++) {
			long free = ~occupied [word];
			if (free != 0) return (word << 6) + Long.numberOfTrailingZeros (free);
		}
	}

	@SuppressWarnings ("unchecked")
	private int evict () {
		while (get (referenced, hand)) {
			clear (referenced, hand);
			hand = hand + 1 == capacity ? 0 : hand + 1;
		}
		int victim = hand;
		hand = hand + 1 == capacity ? 0 : hand + 1;
		K key = (K) keys [victim];
		V value = (V) values [victim];
		release (victim);
		evictions++;
		if (evictor != null) evictor.evicted (key, value);
		return victim;
	}

	/* removes the slot from the index (backward-shift deletion) and frees its lanes */
	private void release (int slot) {
		int bucket = hashes [slot] & mask;
		while (table [bucket] != slot + 1) bucket = (bucket + 1) & mask;
		for (int next = (bucket + 1) & mask;; next = (next + 1) & mask) {
			int moving = table [next] - 1;
			if (moving < 0) break;
			int home = hashes [moving] & mask;
			if (((next - home) & mask) >= ((next - bucket) & mask)) {
				table [bucket] = moving + 1;
				bucket = next;
			}
		}
		table [bucket] = 0;
		keys [slot] = null;
		values [slot] = null;
		clear (occupied, slot);
		clear (referenced, slot);
		size--;
	}

	private static int spread (int h) {
		return h ^ (h >>> 16);
	}

	private static boolean get (long [] bits, int i) {
		return (bits [i >>> 6] & (1L << i)) != 0;
	}

	private static void set (long [] bits, int i) {
		bits [i >>> 6] |= 1L << i;
	}

	private static void clear (long [] bits, int i) {
		bits [i >>> 6] &= ~(1L << i);
	}
}
