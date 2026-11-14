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

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.eclipse.swt.internal.ItemRange;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of VirtualRowExtents (leaf 16). Headless. The oracle is a plain list of
 * extents (null for unmeasured rows) with naive prefix sums.
 */
public class VirtualRowExtentsTests {
	/* the extents of 'rows' unmeasured rows */
	private static VirtualRowExtents extents (int rows, int estimate) {
		VirtualRowExtents extents = new VirtualRowExtents (estimate);
		extents.reset (rows);
		return extents;
	}

	/* the oracle */
	static final class Naive {
		final ArrayList<Integer> rows = new ArrayList<> ();
		int estimate;

		Naive (int count, int estimate) {
			this.estimate = estimate;
			for (int i = 0; i < count; i++) rows.add (null);
		}

		int extent (int row) {
			Integer value = rows.get (row);
			return value == null ? estimate : value;
		}

		long offset (int row) {
			long sum = 0;
			for (int i = 0; i < row; i++) sum += extent (i);
			return sum;
		}
	}

	private static void assertAgrees (Naive naive, VirtualRowExtents extents, String what) {
		assertEquals (naive.rows.size (), extents.rowCount (), what);
		for (int row = 0; row <= naive.rows.size (); row++) assertEquals (naive.offset (row), extents.offsetOf (row), what + " offset " + row);
		for (int row = 0; row < naive.rows.size (); row++) {
			assertEquals (naive.extent (row), extents.extentOf (row), what + " extent " + row);
			assertEquals (naive.rows.get (row) != null, extents.isMeasured (row), what + " measured " + row);
			/* rowAt answers row 0 for offset 0 even when leading rows have no extent */
			if (naive.extent (row) > 0 && (row == 0 || naive.offset (row) > 0)) assertEquals (row, extents.rowAt (naive.offset (row)), what + " rowAt " + row);
		}
		assertEquals (naive.offset (naive.rows.size ()), extents.totalExtent ());
	}

	@Test
	public void test_randomEditsAgreeWithNaivePrefixSums () {
		Random random = new Random (13);
		VirtualRowExtents extents = extents (40, 20);
		Naive naive = new Naive (40, 20);
		for (int step = 0; step < 600; step++) {
			int count = naive.rows.size ();
			switch (random.nextInt (7)) {
				case 0, 1, 2 -> {
					if (count == 0) break;
					int row = random.nextInt (count), extent = random.nextInt (60);
					extents.setExtent (row, extent);
					naive.rows.set (row, extent);
				}
				case 3 -> {
					if (count == 0) break;
					int row = random.nextInt (count);
					extents.clearExtent (row);
					naive.rows.set (row, null);
				}
				case 4 -> {
					int index = random.nextInt (count + 1), rows = random.nextInt (4);
					extents.insert (index, rows);
					for (int i = 0; i < rows; i++) naive.rows.add (index, null);
				}
				case 5 -> {
					if (count == 0) break;
					int index = random.nextInt (count), rows = random.nextInt (Math.min (3, count - index) + 1);
					extents.remove (index, rows);
					for (int i = 0; i < rows; i++) naive.rows.remove (index);
				}
				default -> {
					int estimate = 1 + random.nextInt (30);
					extents.setEstimate (estimate);
					naive.estimate = estimate;
				}
			}
			if (step % 25 == 0) assertAgrees (naive, extents, "step " + step);
		}
		assertAgrees (naive, extents, "end");
		int from = Math.min (5, naive.rows.size ());
		extents.clearFrom (from);
		for (int i = from; i < naive.rows.size (); i++) naive.rows.set (i, null);
		assertAgrees (naive, extents, "clearFrom");
		extents.reset (7);
		assertAgrees (new Naive (7, naive.estimate), extents, "reset");
		assertEquals (0, extents.measuredCount ());
	}

	@Test
	public void test_visibleRangeIsExact () {
		VirtualRowExtents extents = extents (100, 10);
		for (int row = 0; row < 100; row += 3) extents.setExtent (row, 25);
		for (long offset = 0; offset < extents.totalExtent (); offset += 17) {
			for (int viewport : new int [] {1, 50, 333}) {
				ItemRange range = extents.visibleRange (offset, viewport);
				for (int row = 0; row < 100; row++) {
					long start = extents.offsetOf (row), end = start + extents.extentOf (row);
					boolean intersects = end > offset && start < offset + viewport;
					assertEquals (intersects, range.contains (row), "row " + row + " at " + offset + "+" + viewport);
				}
			}
		}
		assertTrue (extents (0, 10).visibleRange (0, 100).isEmpty ());
		assertEquals (-1, extents (0, 10).rowAt (5));
	}

	@Test
	public void test_anchoringKeepsTheContentInView () {
		VirtualRowExtents extents = extents (100, 10);
		long offset = extents.offsetOf (50);
		/* first measurement above the offset: move by the whole change */
		assertEquals (15, extents.setExtentAnchored (10, 25, offset, false));
		offset += 15;
		assertEquals (extents.offsetOf (50), offset, "row 50 stays at the top");
		/* below the offset: nothing moves */
		assertEquals (0, extents.setExtentAnchored (80, 40, offset, false));
		/* re-measure above while scrolling backward: the user's motion wins */
		assertEquals (0, extents.setExtentAnchored (10, 5, offset, true));
		/* re-measure above, not scrolling backward: move by the change */
		assertEquals (25, extents.setExtentAnchored (10, 30, offset, false));
	}

	@Test
	public void test_scrollOffsets () {
		VirtualRowExtents extents = extents (100, 10);
		extents.setExtent (40, 100);
		assertEquals (400, extents.scrollOffsetFor (40, VirtualRowExtents.ALIGN_START, 0, 50));
		assertEquals (500 - 50, extents.scrollOffsetFor (40, VirtualRowExtents.ALIGN_END, 0, 50));
		assertEquals (400 + (100 - 50) / 2, extents.scrollOffsetFor (40, VirtualRowExtents.ALIGN_CENTER, 0, 50));
		assertEquals (420, extents.scrollOffsetFor (40, VirtualRowExtents.ALIGN_AUTO, 420, 50), "revealed: no move");
		assertEquals (extents.maxScrollOffset (50), extents.scrollOffsetFor (99, VirtualRowExtents.ALIGN_START, 0, 50), "clamped");
		assertEquals (extents.totalExtent () - 50, extents.maxScrollOffset (50));
		assertTrue (extents.toString ().startsWith ("VirtualRowExtents [100 rows, 1 measured"));
	}


	@Test
	public void test_negativeEndpointBranchesAndZeroLengthEdits () {
		/* Separate left-hand and right-hand range failures (short-circuit boundary coverage). */
		VirtualRowExtents extents = extents (3, 10);
		assertThrows (IllegalArgumentException.class, () -> extents.clearFrom (-1));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (-1, 0));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (-1, 0));
		assertThrows (IllegalArgumentException.class, () -> extents.offsetOf (-1));
		assertThrows (IllegalArgumentException.class, () ->
			extents.scrollOffsetFor (0, -1, 0, 10));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (-1, 1, 0, false));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (0, -1, 0, false));
		/* The inclusive right endpoint is legal for zero-length edits. */
		extents.clearFrom (3);
		extents.insert (3, 0);
		extents.remove (3, 0);
		assertEquals (3, extents.rowCount ());
		assertEquals (30, extents.offsetOf (3));
		assertEquals (30, extents.totalExtent ());
		extents.reset (0);
		assertEquals (-1, extents.rowAt (0));
		assertTrue (extents.visibleRange (0, 10).isEmpty ());
		extents.insert (0, 1);
		assertEquals (10, extents.totalExtent ());
		assertEquals (0, extents.rowAt (0));
	}


	@Test
	public void test_argumentsAreChecked () {
		assertThrows (IllegalArgumentException.class, () -> extents (-1, 10));
		assertThrows (IllegalArgumentException.class, () -> extents (5, 0));
		VirtualRowExtents extents = extents (5, 10);
		assertThrows (IllegalArgumentException.class, () -> extents.setEstimate (0));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtent (5, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtent (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.extentOf (-1));
		assertThrows (IllegalArgumentException.class, () -> extents.isMeasured (5));
		assertThrows (IllegalArgumentException.class, () -> extents.clearExtent (5));
		assertThrows (IllegalArgumentException.class, () -> extents.clearFrom (6));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (6, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (4, 2));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.reset (-1));
		assertThrows (IllegalArgumentException.class, () -> extents.offsetOf (6));
		assertThrows (IllegalArgumentException.class, () -> extents.visibleRange (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.scrollOffsetFor (0, 9, 0, 10));
		assertThrows (IllegalArgumentException.class, () -> extents.scrollOffsetFor (5, VirtualRowExtents.ALIGN_AUTO, 0, 10));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (5, 1, 0, false));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (0, -1, 0, false));
	}

	@Test
	public void test_smartAlignmentKeepsRevealedOffsetsAndCentersOthers () {
		VirtualRowExtents extents = extents (10, 10);
		extents.setExtent (4, 30); // row 4 occupies [40, 70), viewport 20 reveals it at [40, 50].
		for (long current : new long [] {40, 45, 50}) {
			assertEquals (current, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_SMART, current, 20));
		}
		assertEquals (45, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_SMART, 0, 20));
		assertEquals (45, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_SMART, 100, 20));
		assertEquals (0, extents.scrollOffsetFor (0, VirtualRowExtents.ALIGN_SMART, 100, 50), "center clamps at the start");
		assertEquals (70, extents.scrollOffsetFor (9, VirtualRowExtents.ALIGN_SMART, 0, 50), "center clamps at the end");
		assertEquals (50, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_SMART, 50, 0), "empty viewport retains a revealed offset");
		extents.setExtent (4, 0);
		assertEquals (40, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_SMART, 40, 0), "zero-height row at the exact offset");
		assertEquals (40, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_SMART, 0, 0));
	}

	@Test
	public void test_anchoringDistinguishesFirstMeasurementAndExactBoundaries () {
		VirtualRowExtents atStart = extents (3, 10);
		assertEquals (0, atStart.setExtentAnchored (1, 25, 10, true), "first measurement at the offset is not above it");
		VirtualRowExtents above = extents (3, 10);
		assertEquals (15, above.setExtentAnchored (1, 25, 11, true), "first measurement anchors even while scrolling backward");
		assertEquals (5, above.setExtentAnchored (1, 30, 35, false), "remeasurement anchors at the previous exact end");
		assertEquals (0, above.setExtentAnchored (1, 20, 39, false), "previous row still crosses the offset");
		assertEquals (0, above.setExtentAnchored (1, 10, 30, true), "backward scrolling suppresses remeasurement anchoring");
		assertEquals (-10, above.setExtentAnchored (1, 0, 20, false), "negative changes preserve the same boundary rule");
		assertEquals (0, above.setExtentAnchored (1, 0, 10, false), "unchanged measurement has no delta");
		assertEquals (1, above.measuredCount ());
		assertEquals (0, above.extentOf (1));
	}

	@Test
	public void test_sparseMaximumRowCountKeepsLongOffsetsThroughEdits () {
		int rows = Integer.MAX_VALUE, middle = rows / 2;
		VirtualRowExtents extents = extents (rows, 2);
		assertEquals (0, extents.measuredCount ());
		assertEquals (2L * rows, extents.totalExtent ());
		assertEquals (rows - 1, extents.rowAt (Long.MAX_VALUE));
		extents.setExtent (0, 0);
		extents.setExtent (middle, 3);
		extents.setExtent (rows - 1, 5);
		assertEquals (3, extents.measuredCount ());
		assertEquals (2L * rows + 2, extents.totalExtent ());
		assertEquals (2L * middle - 2, extents.offsetOf (middle));
		assertEquals (2L * (rows - 1) - 1, extents.offsetOf (rows - 1));
		extents.remove (0, 1); // Removing the zero-height row leaves the total unchanged.
		assertEquals (rows - 1, extents.rowCount ());
		assertEquals (2, extents.measuredCount ());
		assertEquals (3, extents.extentOf (middle - 1));
		assertEquals (5, extents.extentOf (rows - 2));
		assertEquals (2L * rows + 2, extents.totalExtent ());
		extents.insert (0, 1);
		assertEquals (rows, extents.rowCount ());
		assertEquals (2, extents.measuredCount ());
		assertEquals (2L * rows + 4, extents.totalExtent ());
		assertEquals (3, extents.extentOf (middle));
		assertEquals (5, extents.extentOf (rows - 1));
		extents.clearFrom (middle);
		assertEquals (0, extents.measuredCount ());
		extents.setEstimate (Integer.MAX_VALUE);
		assertEquals ((long) rows * rows, extents.totalExtent ());
		extents.setExtent (rows - 1, 0);
		assertEquals ((long) (rows - 1) * rows, extents.totalExtent ());
		extents.reset (rows);
		assertEquals (0, extents.measuredCount ());
		assertEquals ((long) rows * rows, extents.totalExtent ());
		assertTrue (extents.visibleRange (extents.offsetOf (rows - 1), 0).isEmpty ());
		assertTrue (extents.visibleRange (extents.offsetOf (rows - 1), 1).contains (rows - 1));
	}

	@Test
	public void test_autoAlignmentMovesOnlyToNearestRevealBoundary () {
		VirtualRowExtents extents = extents (10, 10);
		extents.setExtent (4, 30); // With viewport 20, the oversized row is revealed at [40, 50].
		assertEquals (40, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_AUTO, 0, 20), "move only to the nearer lower boundary");
		assertEquals (50, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_AUTO, Long.MAX_VALUE, 20), "move only to the nearer upper boundary");
		for (long current : new long [] {40, 45, 50}) {
			assertEquals (current, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_AUTO, current, 20), "inclusive reveal interval remains unchanged");
		}
		/* Row 3 occupies [30, 40); viewport 50 reveals it at [-10, 30], then offsets clamp. */
		assertEquals (30, extents.scrollOffsetFor (3, VirtualRowExtents.ALIGN_AUTO, 60, 50));
		assertEquals (0, extents.scrollOffsetFor (3, VirtualRowExtents.ALIGN_AUTO, -20, 50));
		assertEquals (0, extents.scrollOffsetFor (3, VirtualRowExtents.ALIGN_AUTO, 0, 50));
		assertEquals (30, extents.scrollOffsetFor (3, VirtualRowExtents.ALIGN_AUTO, 30, 50));
		assertEquals (40, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_AUTO, 0, 0));
		assertEquals (70, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_AUTO, 100, 0));
		assertEquals (0, extents.scrollOffsetFor (4, VirtualRowExtents.ALIGN_AUTO, 100, 500), "viewport larger than the content clamps to zero");
	}
}
