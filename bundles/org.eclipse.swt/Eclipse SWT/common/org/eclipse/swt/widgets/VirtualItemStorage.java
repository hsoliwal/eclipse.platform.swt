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
import java.util.function.*;

/**
 * Sparse identity-preserving storage for materialized entries of a virtual widget.
 *
 * <p>The logical item count is owned by the widget. This store contains only entries that have
 * actually been materialized, so a large logical model does not require a same-sized Java
 * reference array. Logical insert/remove operations shift the sparse indices while retaining the
 * identity of already materialized items.</p>
 *
 * @param <T> materialized item type
 */
final class VirtualItemStorage<T> {
	@FunctionalInterface
	interface IndexedConsumer<T> {
		void accept(int index, T value);
	}

	private int [] indices = new int [4];
	private Object [] values = new Object [4];
	/*
	 * Parallel packed state lane.  A virtual widget can keep semantic/native-cache
	 * flags beside the logical coordinate without growing the public Item object
	 * graph.  The lane shifts together with indices/values, so state follows the
	 * same logical item across inserts/removes.
	 */
	private long [] stateMasks = new long [4];
	private int size;
	/*
	 * Identity index: two parent-owned lanes (references and logical indices), no object per entry.
	 * An open-addressing table maps a materialized value to its logical index, so the identity
	 * queries (indexOfIdentity, stateOfIdentity, flagOfIdentity), which the widgets ask for the
	 * items they paint, cost a few table slots and one binary search instead of a scan over every
	 * materialized entry. The table is built by the first identity query once the store holds more
	 * than IDENTITY_SCAN entries; put and truncate keep it in step; an edit that moves entries
	 * (insert, remove, removeRange, and releaseOutside when it drops an entry, which all visit every
	 * moved entry already) or outgrows the table leaves it to be rebuilt by the next query. A value
	 * stored at two logical indices blocks the table until the next edit, and the queries keep the
	 * original first-match scan; so do identity queries nested in releaseOutside's callbacks, which
	 * run while kept entries move down (compacting counts the loops in progress, nested ones
	 * included: no edit made from a callback can let a query build the table over a half-moved
	 * store). A table that stops following the entries drops its lanes at
	 * once (identityKeys is non-null only while identityValid, outside releaseOutside's loop), so it
	 * never keeps an entry that left the store reachable, nor arrays sized for an earlier peak.
	 */
	private static final int IDENTITY_SCAN = 16;
	private Object [] identityKeys;
	private int [] identityIndices;
	private int identityCount;
	private boolean identityValid, identityBlocked;
	/* releaseOutside compaction loops in progress (a callback may nest one): no table is built meanwhile */
	private int compacting;
	/** Proof counters: entries compared or table slots probed by identity queries, and table builds. */
	long identityProbes, identityBuilds;

	int size () {
		return size;
	}

	boolean isEmpty () {
		return size == 0;
	}

	@SuppressWarnings ("unchecked")
	T get (int index) {
        if (index < 0) {
            return null;
        }
		int position = Arrays.binarySearch (indices, 0, size, index);
		return position < 0 ? null : (T) values [position];
	}

	void put (int index, T value) {
        if (index < 0) {
            throw new IllegalArgumentException("negative index");
        }
		Objects.requireNonNull (value, "value");
		int position = Arrays.binarySearch (indices, 0, size, index);
		if (position >= 0) {
			Object old = values [position];
			values [position] = value;
			if (old != value) {
				identityRemoved (old);
				identityBlocked = false;
				identityAdded (value, index);
			}
			return;
		}
		position = -position - 1;
		ensureCapacity (size + 1);
		System.arraycopy (indices, position, indices, position + 1, size - position);
		System.arraycopy (values, position, values, position + 1, size - position);
		System.arraycopy (stateMasks, position, stateMasks, position + 1, size - position);
		indices [position] = index;
		values [position] = value;
		stateMasks [position] = 0;
		size++;
		identityAdded (value, index);
	}

	void insert (int index, T value) {
        if (index < 0) {
            throw new IllegalArgumentException("negative index");
        }
		Objects.requireNonNull (value, "value");
		int position = lowerBound (index);
		// Every existing coordinate at/after the insertion must remain representable.
		if (size > 0 && indices [size - 1] == Integer.MAX_VALUE) {
			throw new IllegalArgumentException("logical item index overflow");
		}
		ensureCapacity (size + 1);
		System.arraycopy (indices, position, indices, position + 1, size - position);
		System.arraycopy (values, position, values, position + 1, size - position);
		System.arraycopy (stateMasks, position, stateMasks, position + 1, size - position);
        for (int i = position + 1; i <= size; i++) {
            indices [i]++;
        }
		indices [position] = index;
		values [position] = value;
		stateMasks [position] = 0;
		boolean shifted = position < size;
		size++;
		if (shifted) {
			invalidateIdentity ();
		} else {
			identityAdded (value, index);
		}
	}

	@SuppressWarnings ("unchecked")
	T remove (int index) {
        if (index < 0) {
            throw new IllegalArgumentException("negative index");
        }
		int position = Arrays.binarySearch (indices, 0, size, index);
		T removed = null;
		if (position >= 0) {
			removed = (T) values [position];
			int tail = size - position - 1;
			if (tail > 0) {
				System.arraycopy (indices, position + 1, indices, position, tail);
				System.arraycopy (values, position + 1, values, position, tail);
				System.arraycopy (stateMasks, position + 1, stateMasks, position, tail);
			}
			size--;
			values [size] = null;
			stateMasks [size] = 0;
		} else {
			position = -position - 1;
		}
        for (int i = position; i < size; i++) {
            indices [i]--;
        }
		if (position < size) {
			invalidateIdentity ();
		} else if (removed != null) {
			identityRemoved (removed);
			identityBlocked = false;
		}
		return removed;
	}

	void removeRange (int start, int endExclusive, Consumer<? super T> removed) {
        if (start < 0 || endExclusive < start) {
            throw new IllegalArgumentException("invalid range");
        }
        if (start == endExclusive) {
            return;
        }
		Objects.requireNonNull (removed, "removed");
		int left = lowerBound (start);
		int right = lowerBound (endExclusive);
        for (int i = left; i < right; i++) {
            removed.accept(valueAt(i));
        }
		int removedCount = right - left;
		int tail = size - right;
		if (tail > 0) {
			invalidateIdentity ();
		} else {
			for (int i = left; i < right; i++) {
				identityRemoved (values [i]);
			}
			identityBlocked = false;
		}
		if (tail > 0) {
			System.arraycopy (indices, right, indices, left, tail);
			System.arraycopy (values, right, values, left, tail);
			System.arraycopy (stateMasks, right, stateMasks, left, tail);
		}
		int logicalWidth = endExclusive - start;
		size -= removedCount;
        for (int i = left; i < size; i++) {
            indices [i] -= logicalWidth;
        }
		Arrays.fill (values, size, size + removedCount, null);
		Arrays.fill (stateMasks, size, size + removedCount, 0L);
	}

	void truncate (int logicalCount, Consumer<? super T> removed) {
        if (logicalCount < 0) {
            throw new IllegalArgumentException("negative logical count");
        }
		Objects.requireNonNull (removed, "removed");
		int firstRemoved = lowerBound (logicalCount);
        for (int i = firstRemoved; i < size; i++) {
            removed.accept(valueAt(i));
        }
		for (int i = firstRemoved; i < size; i++) {
			identityRemoved (values [i]);
		}
		identityBlocked = false;
		Arrays.fill (values, firstRemoved, size, null);
		Arrays.fill (stateMasks, firstRemoved, size, 0L);
		size = firstRemoved;
	}

	void clear (Consumer<? super T> removed) {
		Objects.requireNonNull (removed, "removed");
        for (int i = 0; i < size; i++) {
            removed.accept(valueAt(i));
        }
		Arrays.fill (values, 0, size, null);
		Arrays.fill (stateMasks, 0, size, 0L);
		size = 0;
		/* an empty store keeps no identity lanes; a later query of a grown store builds them again */
		dropIdentity ();
		identityBlocked = false;
	}

	void forEach (Consumer<? super T> consumer) {
		Objects.requireNonNull (consumer, "consumer");
        for (int i = 0; i < size; i++) {
            consumer.accept(valueAt(i));
        }
	}

	void forEachIndexed (IndexedConsumer<? super T> consumer) {
		Objects.requireNonNull (consumer, "consumer");
        for (int i = 0; i < size; i++) {
            consumer.accept(indices [i], valueAt(i));
        }
	}

	/**
	 * Drops materialized entries whose logical index is outside <code>[keepStart, keepEnd)</code>
	 * and whose state has none of the <code>keepMask</code> bits, without shifting any logical
	 * index. Returns the number of entries dropped.
	 */
	int releaseOutside (int keepStart, int keepEnd, long keepMask, Consumer<? super T> released) {
		Objects.requireNonNull (released, "released");
		/*
		 * A callback may ask about a kept entry after it was copied down and before its old slot is
		 * overwritten: only the first match (the scan's answer) is the slot that survives.
		 */
		boolean valid = identityValid, blocked = identityBlocked, called = false;
		identityValid = false;
		identityBlocked = true;
		int kept = 0;
		int dropped = 0;
		/*
		 * The block alone is not enough: an edit made from a callback (put, remove, removeRange,
		 * truncate, insert, a nested releaseOutside) clears it, and the next nested query would build
		 * the table over the half-moved entries.
		 */
		compacting++;
		try {
			for (int i = 0; i < size; i++) {
				int index = indices [i];
				if ((index < keepStart || index >= keepEnd) && (stateMasks [i] & keepMask) == 0) {
					/* set before the call: a callback that throws may already have changed the store */
					called = true;
					released.accept (valueAt (i));
					dropped++;
					continue;
				}
				if (kept != i) {
					indices [kept] = indices [i];
					values [kept] = values [i];
					stateMasks [kept] = stateMasks [i];
				}
				kept++;
			}
			Arrays.fill (values, kept, size, null);
			Arrays.fill (stateMasks, kept, size, 0L);
			size = kept;
		} finally {
			compacting--;
			if (called) {
				/* entries left or moved, and a callback may have edited the store: the next query rebuilds */
				dropIdentity ();
				identityBlocked = false;
			} else {
				/* no callback ran and no entry moved: the table (or its block) still holds */
				identityValid = valid;
				identityBlocked = blocked;
			}
		}
		return dropped;
	}

	int indexOfIdentity (T value) {
		int position = positionOfIdentity (value);
		return position < 0 ? -1 : indices [position];
	}

	long state (int index) {
		int position = Arrays.binarySearch (indices, 0, size, index);
		return position < 0 ? 0L : stateMasks [position];
	}

	void state (int index, long state) {
		int position = Arrays.binarySearch (indices, 0, size, index);
        if (position < 0) {
            throw new IllegalStateException("logical item is not materialized");
        }
		stateMasks [position] = state;
	}

	boolean flag (int index, long mask) {
		return (state (index) & mask) != 0;
	}

	void flag (int index, long mask, boolean enabled) {
		int position = Arrays.binarySearch (indices, 0, size, index);
        if (position < 0) {
            throw new IllegalStateException("logical item is not materialized");
        }
        if (enabled) {
            stateMasks [position] |= mask;
        } else {
            stateMasks [position] &= ~mask;
        }
	}

	long stateOfIdentity (T value) {
		int position = positionOfIdentity (value);
		return position < 0 ? 0L : stateMasks [position];
	}

	boolean flagOfIdentity (T value, long mask) {
		return (stateOfIdentity (value) & mask) != 0;
	}

	void flagOfIdentity (T value, long mask, boolean enabled) {
		int position = positionOfIdentity (value);
        if (position < 0) {
            throw new IllegalStateException("materialized item is not in storage");
        }
        if (enabled) {
            stateMasks [position] |= mask;
        } else {
            stateMasks [position] &= ~mask;
        }
	}

	int indexAt (int materializedIndex) {
		return indices [Objects.checkIndex (materializedIndex, size)];
	}

	@SuppressWarnings ("unchecked")
	T valueAt (int materializedIndex) {
		return (T) values [Objects.checkIndex (materializedIndex, size)];
	}

	private int positionOfIdentity (T value) {
		if (value != null && size > IDENTITY_SCAN && (identityValid || buildIdentity ())) {
			int index = identityLookup (value);
			if (index < 0) {
				return -1;
			}
			int position = Arrays.binarySearch (indices, 0, size, index);
			if (position >= 0 && values [position] == value) {
				return position;
			}
			/* not reached while the table follows every edit; answer from the scan and rebuild later */
			invalidateIdentity ();
		}
		for (int i = 0; i < size; i++) {
			identityProbes++;
            if (values [i] == value) {
                return i;
            }
		}
		return -1;
	}

	/* ---------------------------------------------------------------- identity index */

	/** Builds the identity table from the entries; false (table blocked) when a value is stored twice. */
	private boolean buildIdentity () {
		if (identityBlocked || compacting > 0) {
			return false;
		}
		int length = identityLength (size);
		identityKeys = new Object [length];
		identityIndices = new int [length];
		identityCount = 0;
		identityBuilds++;
		for (int i = 0; i < size; i++) {
			if (!identityInsert (values [i], indices [i])) {
				dropIdentity ();
				identityBlocked = true;
				return false;
			}
		}
		identityValid = true;
		return true;
	}

	/* A power of two at least twice the entries (load at most one half). */
	private static int identityLength (int entries) {
		long length = Long.highestOneBit (Math.max (IDENTITY_SCAN, 2L * entries - 1)) << 1;
		return (int) Math.min (length, 1 << 30);
	}

	private static int identitySlot (Object key, int mask) {
		int h = System.identityHashCode (key);
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		return h & mask;
	}

	/* Inserts a key that is absent; false when the key is already in the table. */
	private boolean identityInsert (Object key, int index) {
		int mask = identityKeys.length - 1;
		for (int slot = identitySlot (key, mask);; slot = (slot + 1) & mask) {
			Object k = identityKeys [slot];
			if (k == null) {
				identityKeys [slot] = key;
				identityIndices [slot] = index;
				identityCount++;
				return true;
			}
			if (k == key) {
				return false;
			}
		}
	}

	/* The logical index of a key, or -1; counts the slots probed. */
	private int identityLookup (Object key) {
		int mask = identityKeys.length - 1;
		for (int slot = identitySlot (key, mask);; slot = (slot + 1) & mask) {
			identityProbes++;
			Object k = identityKeys [slot];
			if (k == key) {
				return identityIndices [slot];
			}
			if (k == null) {
				return -1;
			}
		}
	}

	/* An entry was added without shifting another one. */
	private void identityAdded (Object key, int index) {
		if (!identityValid) {
			return;
		}
		if (2L * (identityCount + 1) > identityKeys.length) {
			dropIdentity (); // outgrown: the next query builds a larger table
			return;
		}
		if (!identityInsert (key, index)) {
			dropIdentity ();
			identityBlocked = true;
		}
	}

	/* An entry left without shifting another one: backward-shift deletion keeps every probe chain intact. */
	private void identityRemoved (Object key) {
		if (!identityValid) {
			return;
		}
		int mask = identityKeys.length - 1;
		int hole = identitySlot (key, mask);
		while (identityKeys [hole] != key) {
			if (identityKeys [hole] == null) {
				dropIdentity ();
				return;
			}
			hole = (hole + 1) & mask;
		}
		for (int next = (hole + 1) & mask; identityKeys [next] != null; next = (next + 1) & mask) {
			int home = identitySlot (identityKeys [next], mask);
			if (((next - home) & mask) >= ((next - hole) & mask)) {
				identityKeys [hole] = identityKeys [next];
				identityIndices [hole] = identityIndices [next];
				hole = next;
			}
		}
		identityKeys [hole] = null;
		identityCount--;
	}

	/* Logical indices moved: the next identity query rebuilds the table. */
	private void invalidateIdentity () {
		dropIdentity ();
		identityBlocked = false;
	}

	/* The table no longer follows the entries: release its lanes and the references they hold. */
	private void dropIdentity () {
		identityKeys = null;
		identityIndices = null;
		identityCount = 0;
		identityValid = false;
	}

	private int lowerBound (int index) {
		int position = Arrays.binarySearch (indices, 0, size, index);
		return position >= 0 ? position : -position - 1;
	}

	private void ensureCapacity (int required) {
        if (required <= indices.length) {
            return;
        }
		int next = (int) Math.min (Integer.MAX_VALUE,
				Math.max ((long) required, Math.max (4L, (long) indices.length * 3 / 2)));
		// Stage every lane before publication. If any allocation fails, the old
		// equal-capacity directory, object identities and state masks stay intact.
		int [] nextIndices = Arrays.copyOf (indices, next);
		Object [] nextValues = Arrays.copyOf (values, next);
		long [] nextMasks = Arrays.copyOf (stateMasks, next);
		indices = nextIndices;
		values = nextValues;
		stateMasks = nextMasks;
	}
}
