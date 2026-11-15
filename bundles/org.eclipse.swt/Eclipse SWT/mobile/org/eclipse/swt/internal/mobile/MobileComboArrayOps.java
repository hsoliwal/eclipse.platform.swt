/* SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.swt.internal.mobile;

import java.util.Arrays;
import java.util.Objects;

/**
 * Array-only insert/delete and selection-index transforms for mobile SWT Combo.
 * No per-item node objects, boxed indices or native/UI-thread dependencies.
 * The caller owns the logical item count and supplies a reusable storage array.
 */
public final class MobileComboArrayOps {
    private MobileComboArrayOps() {}

    /** Insert one item, reusing available capacity and returning its storage. */
    public static String[] insert(String[] array, int count, int index, String value) {
        Objects.requireNonNull(array, "array");
        Objects.requireNonNull(value, "value");
        if (count < 0 || count > array.length || index < 0 || index > count)
            throw new IndexOutOfBoundsException("insert " + index + " / " + count);
        if (count == Integer.MAX_VALUE) throw new IllegalStateException("Combo item capacity exhausted");
        if (count == array.length) {
            long proposed = (long)count + Math.max(8L, (long)count >>> 1);
            int capacity = (int)Math.min(Integer.MAX_VALUE,
                    Math.max((long)count + 1, proposed));
            array = Arrays.copyOf(array, capacity);
        }
        System.arraycopy(array, index, array, index + 1, count - index);
        array[index] = value;
        return array;
    }

    /** Remove an inclusive range, compacting in place and clearing all stale refs. */
    public static void removeRange(String[] array, int count, int first, int last) {
        Objects.requireNonNull(array, "array");
        if (count < 0 || count > array.length || first < 0 || first > last || last >= count)
            throw new IndexOutOfBoundsException("remove " + first + ".." + last + " / " + count);
        int removed = last - first + 1;
        System.arraycopy(array, last + 1, array, first, count - last - 1);
        Arrays.fill(array, count - removed, count, null);
    }

    /** Adjust a selected index after insertion at the given position. */
    public static int selectionAfterInsert(int selected, int index) {
        return selected >= index ? selected + 1 : selected;
    }

    /** Adjust a selected index after removal of an inclusive range. */
    public static int selectionAfterRemove(int selected, int first, int last) {
        if (selected < first) return selected;
        if (selected <= last) return -1;
        return selected - (last - first + 1);
    }

    /** Return the first exact match at or after start without allocating. */
    public static int indexOf(String[] array, int count, String value, int start) {
        Objects.requireNonNull(array, "array");
        Objects.requireNonNull(value, "value");
        if (count < 0 || count > array.length) throw new IndexOutOfBoundsException("count " + count);
        for (int i = Math.max(0, start); i < count; i++)
            if (value.equals(array[i])) return i;
        return -1;
    }
}
