/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
	private int size;

	int size () {
		return size;
	}

	boolean isEmpty () {
		return size == 0;
	}

	@SuppressWarnings ("unchecked")
	T get (int index) {
		if (index < 0) return null;
		int position = Arrays.binarySearch (indices, 0, size, index);
		return position < 0 ? null : (T) values [position];
	}

	void put (int index, T value) {
		if (index < 0) throw new IllegalArgumentException ("negative index");
		Objects.requireNonNull (value, "value");
		int position = Arrays.binarySearch (indices, 0, size, index);
		if (position >= 0) {
			values [position] = value;
			return;
		}
		position = -position - 1;
		ensureCapacity (size + 1);
		System.arraycopy (indices, position, indices, position + 1, size - position);
		System.arraycopy (values, position, values, position + 1, size - position);
		indices [position] = index;
		values [position] = value;
		size++;
	}

	void insert (int index, T value) {
		if (index < 0) throw new IllegalArgumentException ("negative index");
		Objects.requireNonNull (value, "value");
		int position = lowerBound (index);
		ensureCapacity (size + 1);
		System.arraycopy (indices, position, indices, position + 1, size - position);
		System.arraycopy (values, position, values, position + 1, size - position);
		for (int i = position + 1; i <= size; i++) indices [i]++;
		indices [position] = index;
		values [position] = value;
		size++;
	}

	@SuppressWarnings ("unchecked")
	T remove (int index) {
		if (index < 0) throw new IllegalArgumentException ("negative index");
		int position = Arrays.binarySearch (indices, 0, size, index);
		T removed = null;
		if (position >= 0) {
			removed = (T) values [position];
			int tail = size - position - 1;
			if (tail > 0) {
				System.arraycopy (indices, position + 1, indices, position, tail);
				System.arraycopy (values, position + 1, values, position, tail);
			}
			size--;
			values [size] = null;
		} else {
			position = -position - 1;
		}
		for (int i = position; i < size; i++) indices [i]--;
		return removed;
	}

	void removeRange (int start, int endExclusive, Consumer<? super T> removed) {
		if (start < 0 || endExclusive < start) throw new IllegalArgumentException ("invalid range");
		if (start == endExclusive) return;
		Objects.requireNonNull (removed, "removed");
		int left = lowerBound (start);
		int right = lowerBound (endExclusive);
		for (int i = left; i < right; i++) removed.accept (valueAt (i));
		int removedCount = right - left;
		int tail = size - right;
		if (tail > 0) {
			System.arraycopy (indices, right, indices, left, tail);
			System.arraycopy (values, right, values, left, tail);
		}
		int logicalWidth = endExclusive - start;
		size -= removedCount;
		for (int i = left; i < size; i++) indices [i] -= logicalWidth;
		Arrays.fill (values, size, size + removedCount, null);
	}

	void truncate (int logicalCount, Consumer<? super T> removed) {
		if (logicalCount < 0) throw new IllegalArgumentException ("negative logical count");
		Objects.requireNonNull (removed, "removed");
		int firstRemoved = lowerBound (logicalCount);
		for (int i = firstRemoved; i < size; i++) removed.accept (valueAt (i));
		Arrays.fill (values, firstRemoved, size, null);
		size = firstRemoved;
	}

	void clear (Consumer<? super T> removed) {
		Objects.requireNonNull (removed, "removed");
		for (int i = 0; i < size; i++) removed.accept (valueAt (i));
		Arrays.fill (values, 0, size, null);
		size = 0;
	}

	void forEach (Consumer<? super T> consumer) {
		Objects.requireNonNull (consumer, "consumer");
		for (int i = 0; i < size; i++) consumer.accept (valueAt (i));
	}

	void forEachIndexed (IndexedConsumer<? super T> consumer) {
		Objects.requireNonNull (consumer, "consumer");
		for (int i = 0; i < size; i++) consumer.accept (indices [i], valueAt (i));
	}

	int indexOfIdentity (T value) {
		for (int i = 0; i < size; i++) {
			if (values [i] == value) return indices [i];
		}
		return -1;
	}

	int indexAt (int materializedIndex) {
		return indices [Objects.checkIndex (materializedIndex, size)];
	}

	@SuppressWarnings ("unchecked")
	T valueAt (int materializedIndex) {
		return (T) values [Objects.checkIndex (materializedIndex, size)];
	}

	private int lowerBound (int index) {
		int position = Arrays.binarySearch (indices, 0, size, index);
		return position >= 0 ? position : -position - 1;
	}

	private void ensureCapacity (int required) {
		if (required <= indices.length) return;
		int next = Math.max (required, Math.max (4, indices.length * 3 / 2));
		indices = Arrays.copyOf (indices, next);
		values = Arrays.copyOf (values, next);
	}
}
