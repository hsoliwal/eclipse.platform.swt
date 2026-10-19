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
 * com.m3.collections.M3LongLongHashMap (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Primitive long-to-long open-addressed map with no per-entry objects.
 *
 * <p>Keys and values occupy parallel primitive lanes and occupancy is independent of key bits,
 * so every long is representable. The API intentionally stays primitive and target-internal;
 * {@link java.util.HashMap} remains the public JDK contract owner.</p>
 */
public final class M3LongLongHashMap {
    private static final byte EMPTY = 0;
    private static final byte USED = 1;
    private static final byte DELETED = 2;
    private static final int MIN_CAPACITY = 8;
    private static final int LOAD_NUMERATOR = 2;
    private static final int LOAD_DENOMINATOR = 3;

    private long[] keys;
    private long[] values;
    private byte[] states;
    private int size;
    private int occupied;
    private int modCount;

    public M3LongLongHashMap() {
        this(0);
    }

    public M3LongLongHashMap(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("negative expectedSize");
        }
        int capacity = capacityFor(expectedSize);
        keys = new long[capacity];
        values = new long[capacity];
        states = new byte[capacity];
    }

    /** Puts a value and returns true only when a new key was inserted. */
    public boolean put(long key, long value) {
        int existing = findIndex(key);
        if (existing >= 0) {
            values[existing] = value;
            return false;
        }
        if (needsGrowth(occupied + 1, keys.length)) {
            // The lookup above proved this key absent. A tombstone reached
            // before the first EMPTY slot is a legal insertion coordinate.
            // Only probe again on the growth boundary; keep primitive lanes.
            int mask = keys.length - 1;
            int probe = mix(key) & mask;
            while (states[probe] != EMPTY) {
                if (states[probe] == DELETED) {
                    keys[probe] = key;
                    values[probe] = value;
                    states[probe] = USED;
                    size++;
                    modCount++;
                    return true;
                }
                probe = (probe + 1) & mask;
            }
            if (keys.length >= (1 << 30)) {
                throw new IllegalStateException("maximum hash-table capacity reached");
            }
            rehash(keys.length << 1);
        }
        int mask = keys.length - 1;
        int index = mix(key) & mask;
        int firstDeleted = -1;
        for (;;) {
            byte state = states[index];
            if (state == EMPTY) {
                int target = firstDeleted >= 0 ? firstDeleted : index;
                keys[target] = key;
                values[target] = value;
                if (states[target] == EMPTY) {
                    occupied++;
                }
                states[target] = USED;
                size++;
                modCount++;
                return true;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = index;
            }
            index = (index + 1) & mask;
        }
    }

    public long getOrDefault(long key, long defaultValue) {
        int index = findIndex(key);
        return index < 0 ? defaultValue : values[index];
    }

    public boolean containsKey(long key) {
        return findIndex(key) >= 0;
    }

    public boolean containsValue(long value) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] == USED && values[i] == value) {
                return true;
            }
        }
        return false;
    }

    public boolean replace(long key, long expectedValue, long newValue) {
        int index = findIndex(key);
        if (index < 0 || values[index] != expectedValue) {
            return false;
        }
        values[index] = newValue;
        return true;
    }

    public boolean remove(long key) {
        int index = findIndex(key);
        if (index < 0) {
            return false;
        }
        deleteAt(index);
        return true;
    }

    public boolean remove(long key, long expectedValue) {
        int index = findIndex(key);
        if (index < 0 || values[index] != expectedValue) {
            return false;
        }
        deleteAt(index);
        return true;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return keys.length;
    }

    public void clear() {
        if (size == 0 && occupied == 0) {
            return;
        }
        Arrays.fill(keys, 0L);
        Arrays.fill(values, 0L);
        Arrays.fill(states, EMPTY);
        size = 0;
        occupied = 0;
        modCount++;
    }

    public void forEach(M3LongLongConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        int expectedModCount = modCount;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == USED) {
                consumer.accept(keys[i], values[i]);
                checkForComodification(expectedModCount);
            }
        }
    }

    /** Returns a primitive iterator over keys. */
    public M3LongIterator keyIterator() {
        int expectedModCount = modCount;
        return new M3LongIterator() {
            private int cursor;
            private int emitted;

            @Override
            public boolean hasNext() {
                checkForComodification(expectedModCount);
                return emitted < size;
            }

            @Override
            public long nextLong() {
                checkForComodification(expectedModCount);
                while (cursor < states.length && states[cursor] != USED) {
                    cursor++;
                }
                if (cursor >= states.length) {
                    throw new NoSuchElementException();
                }
                emitted++;
                return keys[cursor++];
            }
        };
    }

    public long[] keysToArray() {
        long[] result = new long[size];
        int cursor = 0;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == USED) {
                result[cursor++] = keys[i];
            }
        }
        return result;
    }

    private void deleteAt(int index) {
        states[index] = DELETED;
        keys[index] = 0L;
        values[index] = 0L;
        size--;
        modCount++;
        if (size == 0) {
            Arrays.fill(states, EMPTY);
            occupied = 0;
        } else if ((long) occupied > (long) size * 2 && keys.length > MIN_CAPACITY) {
            rehash(keys.length);
        }
    }

    private int findIndex(long key) {
        int mask = keys.length - 1;
        int index = mix(key) & mask;
        for (;;) {
            byte state = states[index];
            if (state == EMPTY) {
                return -1;
            }
            if (state == USED && keys[index] == key) {
                return index;
            }
            index = (index + 1) & mask;
        }
    }

    private void rehash(int requestedCapacity) {
        int capacity = tableSizeFor(Math.max(MIN_CAPACITY, requestedCapacity));
        long[] oldKeys = keys;
        long[] oldValues = values;
        byte[] oldStates = states;
        keys = new long[capacity];
        values = new long[capacity];
        states = new byte[capacity];
        int previousSize = size;
        size = 0;
        occupied = 0;
        for (int i = 0; i < oldStates.length; i++) {
            if (oldStates[i] == USED) {
                insertRehashed(oldKeys[i], oldValues[i]);
            }
        }
        if (size != previousSize) {
            throw new IllegalStateException("rehash lost map entries");
        }
    }

    private void insertRehashed(long key, long value) {
        int mask = keys.length - 1;
        int index = mix(key) & mask;
        while (states[index] == USED) {
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        states[index] = USED;
        size++;
        occupied++;
    }

    private static int capacityFor(int expectedSize) {
        if (expectedSize == 0) {
            return MIN_CAPACITY;
        }
        long needed = ((long) expectedSize * LOAD_DENOMINATOR + LOAD_NUMERATOR - 1)
                / LOAD_NUMERATOR;
        if (needed > (1L << 30)) {
            throw new IllegalArgumentException("expectedSize too large");
        }
        return tableSizeFor((int) Math.max(MIN_CAPACITY, needed));
    }

    private static boolean needsGrowth(int occupied, int capacity) {
        return (long) occupied * LOAD_DENOMINATOR > (long) capacity * LOAD_NUMERATOR;
    }

    private static int tableSizeFor(int requested) {
        if (requested >= (1 << 30)) {
            return 1 << 30;
        }
        int normalized = Math.max(MIN_CAPACITY, requested);
        if ((normalized & (normalized - 1)) == 0) {
            return normalized;
        }
        return Integer.highestOneBit(normalized) << 1;
    }

    private static int mix(long value) {
        long x = value;
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdl;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53l;
        x ^= x >>> 33;
        return (int) x;
    }

    private void checkForComodification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }
}
