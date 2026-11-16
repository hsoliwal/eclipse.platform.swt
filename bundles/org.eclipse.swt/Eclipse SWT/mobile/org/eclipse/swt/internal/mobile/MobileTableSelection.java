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
package org.eclipse.swt.internal.mobile;

import java.util.Arrays;

/** Compact sorted, disjoint inclusive intervals for SWT Table selection. */
public final class MobileTableSelection {
    private int[] starts = new int[8], ends = new int[8];
    private int size;

    public void clear() { size = 0; }
    public int intervalCount() { return size; }
    private void ensure(int count) {
        if (count <= starts.length) return;
        int next = Math.max(count, starts.length + Math.max(8, starts.length >>> 1));
        starts = Arrays.copyOf(starts, next);
        ends = Arrays.copyOf(ends, next);
    }
    public boolean contains(int index) {
        int left = 0, right = size - 1;
        while (left <= right) {
            int middle = (left + right) >>> 1;
            if (index < starts[middle]) right = middle - 1;
            else if (index > ends[middle]) left = middle + 1;
            else return true;
        }
        return false;
    }
    public int count() {
        long total = 0;
        for (int i = 0; i < size; i++) total += (long)ends[i] - starts[i] + 1;
        return Math.toIntExact(total);
    }
    public int first() { return size == 0 ? -1 : starts[0]; }
    public int[] indices() {
        int[] result = new int[count()]; int offset = 0;
        for (int i = 0; i < size; i++) {
            for (int index = starts[i]; index <= ends[i]; index++) {
                result[offset++] = index;
                if (index == Integer.MAX_VALUE) break;
            }
        }
        return result;
    }
    public void select(int first, int last) {
        if (first < 0 || last < first) throw new IndexOutOfBoundsException();
        int from = 0;
        while (from < size && (long)ends[from] + 1 < first) from++;
        int to = from;
        while (to < size && (long)starts[to] <= (long)last + 1) {
            first = Math.min(first, starts[to]);
            last = Math.max(last, ends[to]);
            to++;
        }
        if (from == to) {
            ensure(size + 1);
            System.arraycopy(starts, from, starts, from + 1, size - from);
            System.arraycopy(ends, from, ends, from + 1, size - from);
            size++;
        } else {
            int removed = to - from - 1;
            System.arraycopy(starts, to, starts, from + 1, size - to);
            System.arraycopy(ends, to, ends, from + 1, size - to);
            size -= removed;
        }
        starts[from] = first;
        ends[from] = last;
    }
    public void deselect(int first, int last) {
        if (first < 0 || last < first) throw new IndexOutOfBoundsException();
        for (int i = 0; i < size;) {
            int a = starts[i], b = ends[i];
            if (b < first || a > last) { i++; continue; }
            if (a < first && b > last) {
                ensure(size + 1);
                System.arraycopy(starts, i + 1, starts, i + 2, size - i - 1);
                System.arraycopy(ends, i + 1, ends, i + 2, size - i - 1);
                ends[i] = first - 1;
                starts[i + 1] = last + 1;
                ends[i + 1] = b;
                size++; i += 2;
            } else if (a < first) {
                ends[i] = first - 1; i++;
            } else if (b > last) {
                starts[i] = last + 1; i++;
            } else {
                System.arraycopy(starts, i + 1, starts, i, size - i - 1);
                System.arraycopy(ends, i + 1, ends, i, size - i - 1);
                size--;
            }
        }
    }
    public void toggle(int index) {
        if (index < 0) return;
        if (contains(index)) deselect(index, index); else select(index, index);
    }
    public void selectAll(int count) {
        clear();
        if (count > 0) { starts[0] = 0; ends[0] = count - 1; size = 1; }
    }
    /** Shift existing identities on insertion, leaving the new row unselected. */
    public void insertRow(int index) {
        int i = 0;
        while (i < size && ends[i] < index) i++;
        if (i == size) return;
        if (starts[i] < index && ends[i] >= index) {
            ensure(size + 1);
            System.arraycopy(starts, i + 1, starts, i + 2, size - i - 1);
            System.arraycopy(ends, i + 1, ends, i + 2, size - i - 1);
            starts[i + 1] = index + 1;
            ends[i + 1] = ends[i] + 1;
            ends[i] = index - 1;
            size++;
            i += 2;
        }
        for (; i < size; i++) { starts[i]++; ends[i]++; }
    }
    /** Discard removed rows and compact remaining logical row indices. */
    public void removeRows(int first, int last) {
        if (first < 0 || last < first) throw new IndexOutOfBoundsException();
        deselect(first, last);
        int amount = last - first + 1;
        for (int i = 0; i < size; i++) {
            if (starts[i] > last) { starts[i] -= amount; ends[i] -= amount; }
        }
        int write = 0;
        for (int i = 0; i < size; i++) {
            if (write > 0 && (long)starts[i] <= (long)ends[write - 1] + 1) {
                ends[write - 1] = Math.max(ends[write - 1], ends[i]);
            } else { starts[write] = starts[i]; ends[write] = ends[i]; write++; }
        }
        size = write;
    }
}
