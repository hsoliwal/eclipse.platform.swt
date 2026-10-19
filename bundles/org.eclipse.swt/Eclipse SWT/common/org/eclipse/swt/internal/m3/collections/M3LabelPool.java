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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Bounded interner of user-interface label text, local to one model.
 *
 * <p>Labels in tables and trees repeat: a file type, a size, a date, a JSON key
 * or a path segment appears on thousands of rows. A model that keeps an
 * {@code int} label id per cell instead of a {@code String} stores every
 * distinct text once. This pool hands out those ids.</p>
 *
 * <p>Text lives in one byte arena, one byte per character when every character
 * of the label is below 256 and two bytes otherwise (the layout of compact
 * strings). Per id the pool keeps a span (offset, length, coder), a key (the
 * text's {@link String#hashCode()} and length, or a canonical id), a reference
 * count and two links. Lookup is open addressing with linear probing over an
 * {@code int} slot table; deletion shifts entries back instead of leaving
 * tombstones. There is no entry object and no call of {@link String#intern()}.</p>
 *
 * <p>{@link #intern(String)} adds a reference and {@link #release(int)} drops
 * one. A label without references stays findable on a least-recently-released
 * list; when more than {@code maxCached} labels are unreferenced the oldest one
 * is evicted, its id is reused later and the eviction listener is told, so
 * caches keyed by label id can drop it. Referenced labels are never evicted.
 * Id {@link #EMPTY} is the empty string and is permanent.</p>
 *
 * <p>Strings are facades: {@link #text(int)} creates one per id on demand and
 * keeps it until the label is evicted or {@link #trimFacades()} runs. The other
 * queries answer from the arena without creating a String.</p>
 *
 * <p>The pool is not thread-safe; the model that owns it confines it to one
 * thread, normally the user-interface thread.</p>
 */
public final class M3LabelPool {
	/** Id of the empty string. It is always present and never evicted. */
	public static final int EMPTY = 0;

	/** Answer of {@link #find(String)} for text that is not in the pool. */
	public static final int NOT_FOUND = -1;

	/**
	 * Maps a label to a 64-bit id that identifies its text, for runtimes that
	 * already keep canonical text (the M3 JDK's canonical string atoms). The
	 * function must be injective on text and must never answer 0; the pool then
	 * compares ids instead of characters.
	 */
	@FunctionalInterface
	public interface CanonicalIds {
		/** Returns the canonical id of {@code text}; never 0. */
		long canonicalId (String text);
	}

	private static final int NONE = -1;
	private static final int FREE = -1;
	private static final long UTF16 = 1L << 31;
	private static final int LENGTH_MASK = 0x7FFF_FFFF;
	private static final int MIN_GARBAGE = 4096;
	private static final int FIBONACCI = 0x9E37_79B9;

	private final int maxCached;
	private final CanonicalIds canonical;
	private byte [] arena;
	private int arenaUsed, garbage;
	private long [] spans;
	private long [] keys;
	private int [] refs;
	private int [] prev;
	private int [] next;
	private String [] facades;
	private int [] slots;
	private int shift;
	private int idCount, entries, cached, facadeCount;
	private int freeHead, lruHead, lruTail;
	private long hits, misses, evictions, compactions;
	private IntConsumer evictionListener;
	private String probeText;
	private char [] probeChars;
	private int probeOffset;

	/** Creates a pool that keeps up to 4096 unreferenced labels. */
	public M3LabelPool () {
		this (4096);
	}

	/** Creates a pool that keeps up to {@code maxCached} unreferenced labels. */
	public M3LabelPool (int maxCached) {
		this (maxCached, null);
	}

	/**
	 * Creates a pool that keeps up to {@code maxCached} unreferenced labels and,
	 * when {@code canonical} is not null, keys labels on their canonical ids.
	 */
	public M3LabelPool (int maxCached, CanonicalIds canonical) {
		if (maxCached < 0) throw new IllegalArgumentException ("maxCached < 0: " + maxCached);
		this.maxCached = maxCached;
		this.canonical = canonical;
		reset ();
	}

	private void reset () {
		arena = new byte [64];
		spans = new long [8];
		keys = new long [8];
		refs = new int [8];
		prev = new int [8];
		next = new int [8];
		facades = new String [8];
		slots = new int [16];
		shift = 32 - 4;
		refs [EMPTY] = 1;
		facades [EMPTY] = "";
		arenaUsed = garbage = entries = cached = facadeCount = 0;
		idCount = 1;
		freeHead = lruHead = lruTail = NONE;
	}

	/**
	 * Returns the id of {@code text} and adds one reference to it, adding the
	 * text to the pool when it is new.
	 */
	public int intern (String text) {
		int length = text.length ();
		if (length == 0) return EMPTY;
		long key = canonical == null ? contentKey (text.hashCode (), length) : canonicalKey (text);
		probeText = text;
		try {
			return internProbe (key, length);
		} finally {
			probeText = null;
		}
	}

	/**
	 * Returns the id of the text in {@code chars [offset, offset + length)} and
	 * adds one reference to it. No String is created unless the pool keys on
	 * canonical ids.
	 */
	public int intern (char [] chars, int offset, int length) {
		Objects.checkFromIndexSize (offset, length, chars.length);
		if (length == 0) return EMPTY;
		if (canonical != null) return intern (new String (chars, offset, length));
		probeChars = chars;
		probeOffset = offset;
		try {
			return internProbe (contentKey (hashOf (chars, offset, length), length), length);
		} finally {
			probeChars = null;
		}
	}

	/** Returns the id of {@code text} without adding a reference, or {@link #NOT_FOUND}. */
	public int find (String text) {
		int length = text.length ();
		if (length == 0) return EMPTY;
		long key = canonical == null ? contentKey (text.hashCode (), length) : canonicalKey (text);
		probeText = text;
		try {
			return lookup (key, length);
		} finally {
			probeText = null;
		}
	}

	/** Adds one reference to the live label {@code id}. */
	public void acquire (int id) {
		checkId (id);
		if (id != EMPTY) reference (id);
	}

	/**
	 * Drops one reference from {@code id}. A label without references becomes the
	 * most recently released one and may be evicted later.
	 */
	public void release (int id) {
		checkId (id);
		if (id == EMPTY) return;
		if (refs [id] == 0) throw new IllegalStateException ("label " + id + " has no reference");
		if (--refs [id] == 0) cacheReleased (id);
	}

	/** Returns the text of {@code id}, creating its String facade on first use. */
	public String text (int id) {
		checkId (id);
		String facade = facades [id];
		if (facade == null) {
			facade = decode (spans [id]);
			facades [id] = facade;
			facadeCount++;
		}
		return facade;
	}

	/** Returns the number of characters of {@code id}. */
	public int length (int id) {
		checkId (id);
		return length (spans [id]);
	}

	/** Returns the character at {@code index} of {@code id}. */
	public char charAt (int id, int index) {
		checkId (id);
		long span = spans [id];
		Objects.checkIndex (index, length (span));
		return arenaChar (span, index);
	}

	/** Copies the characters of {@code id} to {@code dst} at {@code dstOffset}; returns their count. */
	public int getChars (int id, char [] dst, int dstOffset) {
		checkId (id);
		long span = spans [id];
		int length = length (span);
		Objects.checkFromIndexSize (dstOffset, length, dst.length);
		for (int i = 0; i < length; i++) dst [dstOffset + i] = arenaChar (span, i);
		return length;
	}

	/** Answers whether the text of {@code id} equals {@code text}, without creating a String. */
	public boolean contentEquals (int id, CharSequence text) {
		checkId (id);
		long span = spans [id];
		int length = length (span);
		if (text.length () != length) return false;
		for (int i = 0; i < length; i++) {
			if (arenaChar (span, i) != text.charAt (i)) return false;
		}
		return true;
	}

	/** Compares the texts of two ids like {@link String#compareTo(String)}. */
	public int compare (int a, int b) {
		checkId (a);
		checkId (b);
		long spanA = spans [a], spanB = spans [b];
		int lengthA = length (spanA), lengthB = length (spanB), common = Math.min (lengthA, lengthB);
		for (int i = 0; i < common; i++) {
			int delta = arenaChar (spanA, i) - arenaChar (spanB, i);
			if (delta != 0) return delta;
		}
		return lengthA - lengthB;
	}

	/** Returns {@link String#hashCode()} of the text of {@code id}. */
	public int hash (int id) {
		checkId (id);
		if (id == EMPTY) return 0;
		if (canonical == null) return (int) (keys [id] >>> 32);
		long span = spans [id];
		int hash = 0;
		for (int i = 0, length = length (span); i < length; i++) hash = 31 * hash + arenaChar (span, i);
		return hash;
	}

	/** Returns the reference count of {@code id}; 0 for a released label still cached. */
	public int refCount (int id) {
		checkId (id);
		return refs [id];
	}

	/** Answers whether {@code id} names a label that is referenced or cached. */
	public boolean isLive (int id) {
		return id >= 0 && id < idCount && refs [id] >= 0;
	}

	/** Number of labels in the pool, referenced or cached, not counting {@link #EMPTY}. */
	public int size () {
		return entries;
	}

	/** Number of labels without references that are still cached. */
	public int cachedCount () {
		return cached;
	}

	/** Upper bound of {@link #cachedCount()}. */
	public int maxCached () {
		return maxCached;
	}

	/** Number of String facades currently held. */
	public int facadeCount () {
		return facadeCount;
	}

	/** Number of {@code intern} calls that found their text in the pool. */
	public long hits () {
		return hits;
	}

	/** Number of {@code intern} calls that added new text. */
	public long misses () {
		return misses;
	}

	/** Number of labels evicted from the released list. */
	public long evictions () {
		return evictions;
	}

	/** Number of arena compactions. */
	public long compactions () {
		return compactions;
	}

	/** Bytes of label text held for live labels. */
	public int textBytes () {
		return arenaUsed - garbage;
	}

	/** Bytes of the arena that belong to evicted labels and await compaction. */
	public int garbageBytes () {
		return garbage;
	}

	/**
	 * Estimated heap bytes retained by the pool: its arrays plus the String
	 * facades it holds (object headers of 16 bytes, references of 4 bytes).
	 */
	public long retainedBytes () {
		long lanes = (long) spans.length * (8 + 8 + 4 + 4 + 4 + 4);
		long arrays = 7L * 16 + arena.length + (long) slots.length * 4 + lanes;
		return arrays + facadeBytes ();
	}

	private long facadeBytes () {
		long bytes = 0;
		for (int id = 1; id < idCount; id++) {
			if (facades [id] != null) bytes += 24 + 16 + bytes (spans [id]);
		}
		return bytes;
	}

	/** Drops every String facade except the empty string's. */
	public void trimFacades () {
		Arrays.fill (facades, 1, idCount, null);
		facadeCount = 0;
	}

	/** Sets the listener told about every evicted id, or removes it with null. */
	public void setEvictionListener (IntConsumer listener) {
		evictionListener = listener;
	}

	/** Moves every live label's text to the front of a new arena of the same size. */
	public void compact () {
		byte [] packed = new byte [arena.length];
		int used = 0;
		for (int id = 1; id < idCount; id++) {
			if (refs [id] < 0) continue;
			long span = spans [id];
			int bytes = bytes (span);
			System.arraycopy (arena, (int) (span >>> 32), packed, used, bytes);
			spans [id] = (long) used << 32 | (span & 0xFFFF_FFFFL);
			used += bytes;
		}
		arena = packed;
		arenaUsed = used;
		garbage = 0;
		compactions++;
	}

	/**
	 * Forgets every label. Ids handed out before become invalid; caches keyed by
	 * them must be cleared by the caller. Counters keep their values.
	 */
	public void clear () {
		reset ();
	}

	private int internProbe (long key, int length) {
		int id = lookup (key, length);
		if (id != NONE) {
			hits++;
			reference (id);
			return id;
		}
		misses++;
		return insert (key, length);
	}

	private int lookup (long key, int length) {
		int mask = slots.length - 1;
		for (int i = slot (key); ; i = (i + 1) & mask) {
			int entry = slots [i];
			if (entry == 0) return NONE;
			int id = entry - 1;
			if (keys [id] == key && (canonical != null || probeMatches (spans [id], length))) return id;
		}
	}

	private boolean probeMatches (long span, int length) {
		for (int i = 0; i < length; i++) {
			if (arenaChar (span, i) != probeChar (i)) return false;
		}
		return true;
	}

	private char probeChar (int index) {
		return probeText != null ? probeText.charAt (index) : probeChars [probeOffset + index];
	}

	private int insert (long key, int length) {
		if ((entries + 1) * 2 > slots.length) rehash (slots.length * 2);
		boolean latin1 = probeLatin1 (length);
		int offset = reserve (latin1 ? length : length << 1);
		int id = allocateId ();
		for (int i = 0; i < length; i++) writeChar (offset, latin1, i, probeChar (i));
		spans [id] = (long) offset << 32 | length | (latin1 ? 0 : UTF16);
		keys [id] = key;
		refs [id] = 1;
		place (id);
		entries++;
		return id;
	}

	private boolean probeLatin1 (int length) {
		for (int i = 0; i < length; i++) {
			if (probeChar (i) > 0xFF) return false;
		}
		return true;
	}

	private void writeChar (int offset, boolean latin1, int index, char c) {
		if (latin1) {
			arena [offset + index] = (byte) c;
		} else {
			arena [offset + 2 * index] = (byte) c;
			arena [offset + 2 * index + 1] = (byte) (c >>> 8);
		}
	}

	private int reserve (int bytes) {
		if (arenaUsed + bytes > arena.length && garbage >= MIN_GARBAGE && garbage >= arenaUsed - garbage) compact ();
		if (arenaUsed + bytes > arena.length) arena = Arrays.copyOf (arena, Math.max (arena.length * 2, arenaUsed + bytes));
		int offset = arenaUsed;
		arenaUsed += bytes;
		return offset;
	}

	private int allocateId () {
		if (freeHead != NONE) {
			int id = freeHead;
			freeHead = next [id];
			return id;
		}
		if (idCount == refs.length) growLanes (refs.length * 2);
		return idCount++;
	}

	private void growLanes (int capacity) {
		spans = Arrays.copyOf (spans, capacity);
		keys = Arrays.copyOf (keys, capacity);
		refs = Arrays.copyOf (refs, capacity);
		prev = Arrays.copyOf (prev, capacity);
		next = Arrays.copyOf (next, capacity);
		facades = Arrays.copyOf (facades, capacity);
	}

	/*
	 * Free ids are reused before new ones, so idCount - 1 is the highest entry
	 * count ever reached; the table grows only when the entry count reaches that
	 * height again, so at that moment every id below idCount is in use.
	 */
	private void rehash (int capacity) {
		slots = new int [capacity];
		shift = 32 - Integer.numberOfTrailingZeros (capacity);
		for (int id = 1; id < idCount; id++) place (id);
	}

	private void place (int id) {
		int mask = slots.length - 1, i = slot (keys [id]);
		while (slots [i] != 0) i = (i + 1) & mask;
		slots [i] = id + 1;
	}

	private int slot (long key) {
		return ((int) (key ^ (key >>> 32)) * FIBONACCI) >>> shift;
	}

	private void reference (int id) {
		if (refs [id] == 0) unlinkReleased (id);
		refs [id] = Math.incrementExact (refs [id]);
	}

	private void cacheReleased (int id) {
		prev [id] = lruTail;
		next [id] = NONE;
		if (lruTail == NONE) lruHead = id; else next [lruTail] = id;
		lruTail = id;
		if (++cached > maxCached) evict (lruHead);
	}

	private void unlinkReleased (int id) {
		int before = prev [id], after = next [id];
		if (before == NONE) lruHead = after; else next [before] = after;
		if (after == NONE) lruTail = before; else prev [after] = before;
		cached--;
	}

	private void evict (int id) {
		unlinkReleased (id);
		deleteSlot (id);
		garbage += bytes (spans [id]);
		if (facades [id] != null) facadeCount--;
		facades [id] = null;
		refs [id] = FREE;
		next [id] = freeHead;
		freeHead = id;
		entries--;
		evictions++;
		if (evictionListener != null) evictionListener.accept (id);
	}

	private void deleteSlot (int id) {
		int mask = slots.length - 1, hole = slot (keys [id]);
		while (slots [hole] != id + 1) hole = (hole + 1) & mask;
		for (int i = (hole + 1) & mask; slots [i] != 0; i = (i + 1) & mask) {
			int home = slot (keys [slots [i] - 1]);
			if (((i - home) & mask) >= ((i - hole) & mask)) {
				slots [hole] = slots [i];
				hole = i;
			}
		}
		slots [hole] = 0;
	}

	private String decode (long span) {
		int offset = (int) (span >>> 32), length = length (span);
		if ((span & UTF16) == 0) return new String (arena, offset, length, StandardCharsets.ISO_8859_1);
		char [] chars = new char [length];
		for (int i = 0; i < length; i++) chars [i] = arenaChar (span, i);
		return new String (chars);
	}

	private char arenaChar (long span, int index) {
		int offset = (int) (span >>> 32);
		if ((span & UTF16) == 0) return (char) (arena [offset + index] & 0xFF);
		int at = offset + 2 * index;
		return (char) ((arena [at] & 0xFF) | (arena [at + 1] & 0xFF) << 8);
	}

	private void checkId (int id) {
		if (!isLive (id)) throw new IllegalArgumentException ("not a live label id: " + id);
	}

	private long canonicalKey (String text) {
		long key = canonical.canonicalId (text);
		if (key == 0) throw new IllegalStateException ("canonical id 0 for a non-empty label");
		return key;
	}

	private static long contentKey (int hash, int length) {
		return (long) hash << 32 | length;
	}

	private static int hashOf (char [] chars, int offset, int length) {
		int hash = 0;
		for (int i = 0; i < length; i++) hash = 31 * hash + chars [offset + i];
		return hash;
	}

	private static int length (long span) {
		return (int) span & LENGTH_MASK;
	}

	private static int bytes (long span) {
		return (span & UTF16) == 0 ? length (span) : length (span) << 1;
	}
}
