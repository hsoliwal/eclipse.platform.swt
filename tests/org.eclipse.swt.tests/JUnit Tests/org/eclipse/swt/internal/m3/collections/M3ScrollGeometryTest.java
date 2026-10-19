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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3ScrollGeometryTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Direct tests of the scroll arithmetic: drawn scroll bar geometry, uniform-row metrics and the layout adapter. */
public class M3ScrollGeometryTest {
	@Test
	public void mulDivRoundsHalfAwayFromZero () {
		assertEquals (11, M3ScrollBarGeometry.mulDiv (7, 3, 2));
		assertEquals (-11, M3ScrollBarGeometry.mulDiv (-7, 3, 2));
		assertEquals (-11, M3ScrollBarGeometry.mulDiv (7, 3, -2));
		assertEquals (11, M3ScrollBarGeometry.mulDiv (-7, -3, 2));
		assertEquals (-11, M3ScrollBarGeometry.mulDiv (-7, -3, -2));
		assertEquals (0, M3ScrollBarGeometry.mulDiv (1, 1, 0));
		assertEquals (2, M3ScrollBarGeometry.mulDiv (5, 1, 3));
		assertEquals (1, M3ScrollBarGeometry.mulDiv (4, 1, 3));
	}

	@Test
	public void thumbGeometry () {
		assertFalse (M3ScrollBarGeometry.hasThumb (7, 8));
		assertTrue (M3ScrollBarGeometry.hasThumb (8, 8));
		assertEquals (0, M3ScrollBarGeometry.thumbLength (7, 8, 0, 99, 9));
		assertEquals (10, M3ScrollBarGeometry.thumbLength (100, 8, 0, 99, 9));
		assertEquals (8, M3ScrollBarGeometry.thumbLength (100, 8, 0, 999, 9), "minimum thumb length");
		assertEquals (45, M3ScrollBarGeometry.thumbStart (100, 8, 0, 99, 9, 45));
		assertEquals (-1, M3ScrollBarGeometry.thumbStart (100, 8, 0, 9, 9, 0), "a bar that cannot scroll");
		assertEquals (45, M3ScrollBarGeometry.selectionAt (45, 100, 8, 0, 99, 9));
		assertEquals (0, M3ScrollBarGeometry.selectionAt (-5, 100, 8, 0, 99, 9));
		assertEquals (90, M3ScrollBarGeometry.selectionAt (500, 100, 8, 0, 99, 9));
		assertEquals (5, M3ScrollBarGeometry.selectionAt (10, 100, 8, 5, 14, 9));
		assertEquals (0, M3ScrollBarGeometry.selectionAt (3, 8, 8, 0, 99, 9), "no free track");
		for (int selection = 0; selection <= 90; selection++) {
			int start = M3ScrollBarGeometry.thumbStart (100, 8, 0, 99, 9, selection);
			assertEquals (selection, M3ScrollBarGeometry.selectionAt (start, 100, 8, 0, 99, 9));
		}
	}

	@Test
	public void hitParts () {
		int arrow = 17, track = 100;
		assertEquals (M3ScrollBarGeometry.NONE, M3ScrollBarGeometry.hit (-1, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.NONE, M3ScrollBarGeometry.hit (134, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.ARROW_DECREASE, M3ScrollBarGeometry.hit (5, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.ARROW_INCREASE, M3ScrollBarGeometry.hit (117, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.PAGE_DECREASE, M3ScrollBarGeometry.hit (27, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.THUMB, M3ScrollBarGeometry.hit (67, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.PAGE_INCREASE, M3ScrollBarGeometry.hit (77, arrow, track, 8, 0, 99, 9, 45));
		assertEquals (M3ScrollBarGeometry.NONE, M3ScrollBarGeometry.hit (20, arrow, 7, 8, 0, 99, 9, 45), "no thumb in a short track");
	}

	@Test
	public void scrollMetricsSmallModel () {
		M3ScrollMetrics metrics = new M3ScrollMetrics ();
		assertEquals (1, metrics.sampleRowExtent ());
		assertEquals (0, metrics.thumb ());
		assertEquals (0, metrics.increment ());
		assertEquals (1, metrics.pageIncrement ());
		assertEquals (0, metrics.selectionForTopRow (3));
		assertEquals (0, metrics.topRowForSelection (3));
		metrics.configure (100, 10, 95);
		assertEquals (100, metrics.logicalRows ());
		assertEquals (100L, metrics.logicalRowCount ());
		assertEquals (10, metrics.sampleRowExtent ());
		assertEquals (95, metrics.viewportExtent ());
		assertEquals (1000L, metrics.estimatedContentExtent ());
		assertEquals (10, metrics.visibleRows ());
		assertTrue (metrics.scrollbarNeeded ());
		assertEquals (0, metrics.minimum ());
		assertEquals (100, metrics.maximum ());
		assertEquals (10, metrics.thumb ());
		assertEquals (1, metrics.increment ());
		assertEquals (10, metrics.pageIncrement ());
		assertEquals (90L, metrics.maxTopRow ());
		assertEquals (90, metrics.clampTopRow (500));
		assertEquals (0, metrics.clampTopRow (-4));
		assertEquals (50, metrics.selectionForTopRow (50));
		assertEquals (90, metrics.selectionForTopRow (500));
		assertEquals (0, metrics.topRowForSelection (-5));
		assertEquals (40L, metrics.topRowForSelection (40));
		metrics.configure (100, 10, 0);
		assertEquals (0, metrics.visibleRows ());
		assertEquals (1, metrics.thumb ());
		assertEquals (5, metrics.selectionForTopRow (5));
		metrics.configure (3, 10, 95);
		assertFalse (metrics.scrollbarNeeded ());
		assertEquals (0, metrics.selectionForTopRow (2), "nothing to scroll");
		assertEquals (0L, metrics.topRowForSelection (2));
		assertThrows (IllegalArgumentException.class, () -> metrics.configure (-1, 1, 0));
		assertThrows (IllegalArgumentException.class, () -> metrics.configure (1, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> metrics.configure (1, 1, -1));
		metrics.configure (Long.MAX_VALUE, 2, 0);
		assertThrows (ArithmeticException.class, metrics::estimatedContentExtent);
	}

	@Test
	public void scrollMetricsGiantModel () {
		M3ScrollMetrics metrics = new M3ScrollMetrics ();
		long rows = 1_000_000_000_000L;
		metrics.configure (rows, 10, 100);
		assertEquals (Integer.MAX_VALUE, metrics.logicalRows ());
		assertEquals (Integer.MAX_VALUE, metrics.maximum ());
		assertEquals (1, metrics.thumb (), "the thumb is scaled onto the int range");
		long maxTop = metrics.maxTopRow ();
		int maxSelection = Integer.MAX_VALUE - 1;
		assertEquals (rows - 10, maxTop);
		assertEquals (maxSelection, metrics.selectionForTopRow (maxTop));
		assertEquals (0, metrics.selectionForTopRow (0));
		int small = metrics.selectionForTopRow (1000);
		assertEquals ((1000L * maxSelection + maxTop / 2) / maxTop, small, "primitive scale");
		int half = metrics.selectionForTopRow (maxTop / 2);
		assertTrue (Math.abs (half - maxSelection / 2) <= 1, "BigInteger scale: " + half);
		assertEquals (maxTop, metrics.topRowForSelection (maxSelection));
		assertEquals ((1000L * maxTop + maxSelection / 2) / maxSelection, metrics.topRowForSelection (1000));
		long middle = metrics.topRowForSelection (maxSelection / 2);
		assertTrue (Math.abs (middle - maxTop / 2) <= maxTop / maxSelection + 1, "BigInteger scale: " + middle);
		metrics.configure (rows, 10, 10_000_000);
		assertTrue (metrics.thumb () > 1);
	}

	@Test
	public void scrollLayoutAdapter () {
		M3ScrollLayout.Result result = M3ScrollLayout.solve (100, 60, 20, 5, 10, 100, 10, 12, M3ScrollLayout.AUTO, M3ScrollLayout.AUTO);
		M3WindowRuntime.RowLayout rows = M3WindowRuntime.solveRows (100, 60, 20, 5, 10, 100, 10, 12, M3WindowRuntime.AUTO, M3WindowRuntime.AUTO);
		assertEquals (rows.horizontalVisible (), result.horizontalVisible ());
		assertEquals (rows.verticalVisible (), result.verticalVisible ());
		assertEquals (rows.cornerVisible (), result.cornerVisible ());
		assertEquals (rows.bodyWidth (), result.bodyWidth ());
		assertEquals (rows.bodyHeight (), result.bodyHeight ());
		assertEquals (rows.headerWidth (), result.headerWidth ());
		assertEquals (rows.headerHeight (), result.headerHeight ());
		assertEquals (rows.visibleRows (), result.visibleRows ());
		assertEquals (rows.logicalRows (), result.logicalRows ());
		assertEquals (rows.estimatedContentHeight (), result.estimatedContentHeight ());
		assertEquals (rows.logicalContentWidth (), result.logicalContentWidth ());
		assertEquals (M3ScrollLayout.NEVER, M3WindowRuntime.NEVER);
		assertEquals (M3ScrollLayout.ALWAYS, M3WindowRuntime.ALWAYS);
	}
}
