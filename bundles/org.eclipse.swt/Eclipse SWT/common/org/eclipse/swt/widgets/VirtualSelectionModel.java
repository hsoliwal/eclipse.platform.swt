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

/**
 * Range-compressed selection state for a very large logical widget.
 *
 * <p>The model stores sorted, non-overlapping integer ranges.  In normal mode the
 * ranges are selected coordinates.  After {@link #selectAll()} the representation
 * flips and ranges become deselected exceptions.  This keeps both sparse selection
 * and "select all" bounded without one boolean or object per logical row.</p>
 */
final class VirtualSelectionModel {
	private int logicalCount;
	private boolean allSelected;
	private int [] starts = new int [4];
	private int [] ends = new int [4];
	private int rangeCount;

	int logicalCount () {
		return logicalCount;
	}

	void setLogicalCount (int count) {
		if (count < 0) throw new IllegalArgumentException ("negative logical count");
		if (count == logicalCount) return;
		if (count < logicalCount) {
			remove (count, logicalCount - count);
			return;
		}
		int oldCount = logicalCount;
		logicalCount = count;
		if (allSelected && oldCount < count) {
			/*
			 * Newly appended logical items were not part of the previous select-all.
			 * Keep them unselected by recording one exception range.
			 */
			addRange (oldCount, count);
		}
	}

	boolean isSelected (int index) {
		checkIndex (index);
		boolean represented = contains (index);
		return allSelected ? !represented : represented;
	}

	void setSelected (int index, boolean selected) {
		checkIndex (index);
		if (selected == isSelected (index)) return;
		if (allSelected) {
			if (selected) removeRange (index, index + 1);
			else addRange (index, index + 1);
		} else {
			if (selected) addRange (index, index + 1);
			else removeRange (index, index + 1);
		}
	}

	void selectRange (int start, int endExclusive) {
		checkRange (start, endExclusive);
		if (allSelected) removeRange (start, endExclusive);
		else addRange (start, endExclusive);
	}

	void deselectRange (int start, int endExclusive) {
		checkRange (start, endExclusive);
		if (allSelected) addRange (start, endExclusive);
		else removeRange (start, endExclusive);
	}

	void clear () {
		allSelected = false;
		rangeCount = 0;
	}

	void selectAll () {
		allSelected = true;
		rangeCount = 0;
	}

	int selectedCount () {
		long represented = 0;
		for (int i = 0; i < rangeCount; i++) represented += ends [i] - starts [i];
		long selected = allSelected ? (long) logicalCount - represented : represented;
		return (int) selected;
	}

	int [] toArray () {
		int count = selectedCount ();
		int [] result = new int [count];
		int offset = 0;
		if (!allSelected) {
			for (int i = 0; i < rangeCount; i++) {
				for (int value = starts [i]; value < ends [i]; value++) result [offset++] = value;
			}
			return result;
		}
		int range = 0;
		for (int value = 0; value < logicalCount; value++) {
			while (range < rangeCount && ends [range] <= value) range++;
			if (range < rangeCount && starts [range] <= value && value < ends [range]) continue;
			result [offset++] = value;
		}
		return result;
	}

	void insert (int index, int count) {
		if (count < 0 || index < 0 || index > logicalCount) throw new IllegalArgumentException ("invalid insert");
		if (count == 0) return;
		VirtualSelectionModel shifted = new VirtualSelectionModel ();
		shifted.logicalCount = Math.addExact (logicalCount, count);
		shifted.allSelected = allSelected;
		for (int i = 0; i < rangeCount; i++) {
			int start = starts [i], end = ends [i];
			if (end <= index) {
				shifted.addRange (start, end);
			} else if (start >= index) {
				shifted.addRange (start + count, end + count);
			} else {
				shifted.addRange (start, index);
				shifted.addRange (index + count, end + count);
			}
		}
		if (allSelected) shifted.addRange (index, index + count);
		copyFrom (shifted);
	}

	void remove (int index, int count) {
		if (count < 0 || index < 0 || index > logicalCount - count) throw new IllegalArgumentException ("invalid remove");
		if (count == 0) return;
		int endRemoved = index + count;
		VirtualSelectionModel shifted = new VirtualSelectionModel ();
		shifted.logicalCount = logicalCount - count;
		shifted.allSelected = allSelected;
		for (int i = 0; i < rangeCount; i++) {
			int start = starts [i], end = ends [i];
			if (start < index) shifted.addRange (start, Math.min (end, index));
			if (end > endRemoved) {
				shifted.addRange (Math.max (start, endRemoved) - count, end - count);
			}
		}
		copyFrom (shifted);
	}

	int rangeCount () {
		return rangeCount;
	}

	int rangeStart (int range) {
		return starts [Objects.checkIndex (range, rangeCount)];
	}

	int rangeEndExclusive (int range) {
		return ends [Objects.checkIndex (range, rangeCount)];
	}

	boolean complementMode () {
		return allSelected;
	}

	private void addRange (int start, int endExclusive) {
		if (start >= endExclusive) return;
		int first = 0;
		while (first < rangeCount && ends [first] < start) first++;
		int mergedStart = start, mergedEnd = endExclusive;
		int last = first;
		while (last < rangeCount && starts [last] <= mergedEnd) {
			mergedStart = Math.min (mergedStart, starts [last]);
			mergedEnd = Math.max (mergedEnd, ends [last]);
			last++;
		}
		int removed = last - first;
		ensureCapacity (rangeCount - removed + 1);
		System.arraycopy (starts, last, starts, first + 1, rangeCount - last);
		System.arraycopy (ends, last, ends, first + 1, rangeCount - last);
		starts [first] = mergedStart;
		ends [first] = mergedEnd;
		rangeCount = rangeCount - removed + 1;
	}

	private void removeRange (int start, int endExclusive) {
		if (start >= endExclusive || rangeCount == 0) return;
		int [] nextStarts = new int [Math.max (4, rangeCount * 2)];
		int [] nextEnds = new int [nextStarts.length];
		int nextCount = 0;
		for (int i = 0; i < rangeCount; i++) {
			int rangeStart = starts [i], rangeEnd = ends [i];
			if (rangeEnd <= start || rangeStart >= endExclusive) {
				nextStarts [nextCount] = rangeStart;
				nextEnds [nextCount++] = rangeEnd;
				continue;
			}
			if (rangeStart < start) {
				nextStarts [nextCount] = rangeStart;
				nextEnds [nextCount++] = start;
			}
			if (rangeEnd > endExclusive) {
				nextStarts [nextCount] = endExclusive;
				nextEnds [nextCount++] = rangeEnd;
			}
		}
		starts = nextStarts;
		ends = nextEnds;
		rangeCount = nextCount;
	}

	private boolean contains (int index) {
		int low = 0, high = rangeCount - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (index < starts [mid]) {
				high = mid - 1;
			} else if (index >= ends [mid]) {
				low = mid + 1;
			} else {
				return true;
			}
		}
		return false;
	}

	private void ensureCapacity (int required) {
		if (required <= starts.length) return;
		int next = Math.max (required, Math.max (4, starts.length * 3 / 2));
		starts = Arrays.copyOf (starts, next);
		ends = Arrays.copyOf (ends, next);
	}

	private void copyFrom (VirtualSelectionModel other) {
		logicalCount = other.logicalCount;
		allSelected = other.allSelected;
		starts = Arrays.copyOf (other.starts, other.starts.length);
		ends = Arrays.copyOf (other.ends, other.ends.length);
		rangeCount = other.rangeCount;
	}

	private void checkIndex (int index) {
		if (index < 0 || index >= logicalCount) throw new IllegalArgumentException ("index outside logical model");
	}

	private void checkRange (int start, int endExclusive) {
		if (start < 0 || endExclusive < start || endExclusive > logicalCount) {
			throw new IllegalArgumentException ("range outside logical model");
		}
	}
}
