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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 118b3cb7e59ea4d4cc32468dccfc6f32e5715dcb,
 * com.m3.collections.M3WindowPlannerTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.eclipse.swt.internal.m3.collections.M3WindowPlanner.HorizontalWindow;

/** Direct tests of the flat-widget planner and its horizontal column window. */
public class M3WindowPlannerTest {
	@Test
	public void plannerWindowAndGenerations () {
		M3WindowPlanner planner = new M3WindowPlanner ();
		assertNotNull (new M3WindowPlanner (false).selection ());
		assertThrows (IllegalArgumentException.class, () -> planner.setLogicalCount (-1));
		int g = planner.generation ();
		planner.setLogicalCount (0);
		assertEquals (g, planner.generation (), "same count is a no-op");
		planner.setLogicalCount (20);
		assertEquals (20, planner.logicalCount ());
		assertEquals (20, planner.selection ().logicalCount ());
		assertTrue (planner.generation () > g);
		planner.setViewport (4, 5);
		assertEquals (4, planner.firstVisible ());
		assertEquals (5, planner.visibleCount ());
		assertEquals (M3WindowPlanner.DEFAULT_OVERSCAN_ROWS, planner.overscanRows ());
		planner.setOverscanRows (2);
		assertEquals (2, planner.overscanRows ());
		assertEquals (2, planner.paintStart ());
		assertEquals (11, planner.paintEndExclusive ());
		assertTrue (planner.isVisible (8));
		assertFalse (planner.isVisible (9));
		assertTrue (planner.isPaintCandidate (10));
		assertFalse (planner.isPaintCandidate (11));
		int before = planner.generation ();
		planner.setViewport (4, 5);
		assertEquals (before, planner.generation (), "unchanged window keeps the generation");
		planner.insert (0, 0);
		planner.remove (0, 0);
		assertEquals (before, planner.generation ());
		planner.insert (0, 3);
		assertEquals (23, planner.logicalCount ());
		assertEquals (7, planner.firstVisible ());
		planner.remove (0, 3);
		assertEquals (4, planner.firstVisible ());
		assertThrows (IllegalArgumentException.class, () -> planner.insert (0, -1));
		assertThrows (IllegalArgumentException.class, () -> planner.insert (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> planner.insert (21, 1));
		assertThrows (IllegalArgumentException.class, () -> planner.remove (0, -1));
		assertThrows (IllegalArgumentException.class, () -> planner.remove (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> planner.remove (0, 21));
	}

	@Test
	public void uniformGeometryAndRepaintLock () {
		M3WindowPlanner planner = new M3WindowPlanner (true);
		planner.setUniformGeometry (100, 10, 95, 500);
		assertEquals (100, planner.logicalCount ());
		assertEquals (90, planner.firstVisible (), "the top row is clamped to the last full page");
		assertEquals (10, planner.visibleCount ());
		assertSame (planner.scrollMetrics (), planner.scrollMetrics ());
		assertEquals (10, planner.scrollMetrics ().thumb ());
		assertFalse (planner.repaintLocked ());
		assertThrows (IllegalStateException.class, planner::endRepaintLock);
		planner.beginRepaintLock ();
		planner.beginRepaintLock ();
		assertTrue (planner.repaintLocked ());
		int g = planner.generation ();
		planner.endRepaintLock ();
		planner.endRepaintLock ();
		assertFalse (planner.repaintLocked ());
		assertEquals (g + 2, planner.generation ());
		assertSame (planner.horizontal (), planner.horizontal ());
	}

	/** Columns 0..3 with widths 10, 0, 30, 20 shown in display order 2, 0, 1, 3: starts 30, 40, 0, 40; content 60. */
	private static HorizontalWindow columns () {
		HorizontalWindow window = new HorizontalWindow ();
		assertTrue (window.setColumns (new long [] {10, 0, 30, 20}, new int [] {2, 0, 1, 3}));
		return window;
	}

	@Test
	public void columnDirectory () {
		HorizontalWindow window = columns ();
		assertFalse (window.setColumns (new long [] {10, 0, 30, 20}, new int [] {2, 0, 1, 3}), "same layout");
		assertEquals (4, window.columnCount ());
		assertEquals (60, window.contentWidth ());
		assertEquals (30, window.columnStart (0));
		assertEquals (40, window.columnStart (1));
		assertEquals (0, window.columnStart (2));
		assertEquals (40, window.columnStart (3));
		assertEquals (30, window.columnWidth (2));
		assertEquals (0, window.columnAtDisplayIndex (1));
		assertEquals (1, window.geometryGeneration ());
		assertThrows (IndexOutOfBoundsException.class, () -> window.columnAtSlot (3));
		assertThrows (IndexOutOfBoundsException.class, () -> window.columnAtDisplayIndex (4));
		assertThrows (IndexOutOfBoundsException.class, () -> window.columnStart (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> window.columnWidth (4));
		assertThrows (NullPointerException.class, () -> window.setColumns (null, new int [0]));
		assertThrows (NullPointerException.class, () -> window.setColumns (new long [0], null));
		assertThrows (IllegalArgumentException.class, () -> window.setColumns (new long [] {1}, new int [0]));
		assertThrows (IllegalArgumentException.class, () -> window.setColumns (new long [] {-1}, new int [] {0}));
		assertThrows (IllegalArgumentException.class, () -> window.setColumns (new long [] {1, 1}, new int [] {0, 0}));
		assertThrows (IllegalArgumentException.class, () -> window.setColumns (new long [] {1, 1}, new int [] {0, 2}));
		assertThrows (IllegalArgumentException.class, () -> window.setColumns (new long [] {1, 1}, new int [] {-1, 0}));
		assertThrows (ArithmeticException.class, () -> window.setColumns (new long [] {Long.MAX_VALUE, 1}, new int [] {0, 1}));
		assertEquals (60, window.contentWidth (), "invalid input leaves the directory unchanged");
		assertEquals (1, window.geometryGeneration ());
	}

	@Test
	public void leftToRightWindow () {
		HorizontalWindow window = columns ();
		assertTrue (window.setViewport (0, 25, 5, false));
		assertFalse (window.setViewport (0, 25, 5, false));
		assertFalse (window.rightToLeft ());
		assertEquals (0, window.origin ());
		assertEquals (25, window.viewportWidth ());
		assertEquals (0, window.paintStart ());
		assertEquals (30, window.paintEndExclusive ());
		assertEquals (30, window.residentWidth ());
		assertEquals (0, window.firstSlot ());
		assertEquals (1, window.endSlot ());
		assertEquals (2, window.columnAtSlot (0));
		assertEquals (30, window.viewportX (0));
		assertEquals (0, window.residentX (2));
		assertEquals (-1, window.residentX (0));
		assertEquals (30, window.residentColumnWidth (2));
		assertEquals (0, window.residentColumnWidth (0));
		assertEquals (0, window.sourceOffset (2));
		assertEquals (-1, window.sourceOffset (0));
		assertEquals (0, window.residentViewportX ());
		assertEquals (-1, window.logicalAtViewportPixel (-1));
		assertEquals (-1, window.logicalAtViewportPixel (25));
		assertEquals (5, window.logicalAtViewportPixel (5));
		assertEquals (2, window.columnAtViewportPixel (5));
		assertEquals (-1, window.columnAtViewportPixel (-1));
		assertTrue (window.setViewport (35, 20, 0, false));
		assertEquals (5, window.sourceOffset (0), "clipped on its leading edge");
		assertEquals (0, window.sourceOffset (3));
		assertEquals (0, window.columnAtViewportPixel (0));
		assertEquals (3, window.columnAtViewportPixel (19));
		assertTrue (window.generation () >= 3);
	}

	@Test
	public void rightToLeftWindow () {
		HorizontalWindow window = columns ();
		assertTrue (window.setViewport (100, 25, 5, true));
		assertTrue (window.rightToLeft ());
		assertEquals (35, window.origin (), "origin clamped to content - width");
		assertEquals (30, window.paintStart ());
		assertEquals (60, window.paintEndExclusive ());
		assertEquals (1, window.firstSlot ());
		assertEquals (3, window.endSlot ());
		assertEquals (0, window.viewportX (3));
		assertEquals (30, window.viewportX (2));
		assertEquals (0, window.residentX (3));
		assertEquals (20, window.residentX (0));
		assertEquals (-1, window.residentX (2));
		assertEquals (0, window.residentViewportX ());
		assertEquals (59, window.logicalAtViewportPixel (0));
		assertTrue (window.setViewport (35, 20, 0, true));
		assertEquals (5, window.sourceOffset (3), "clipped on its trailing edge");
		assertEquals (0, window.sourceOffset (0));
		assertTrue (window.setViewport (0, 25, 5, true));
		assertEquals (-5, window.residentViewportX ());
	}

	@Test
	public void emptyAndInvalidWindows () {
		HorizontalWindow empty = new HorizontalWindow ();
		assertTrue (empty.setViewport (7, 10, 3, false));
		assertEquals (0, empty.origin ());
		assertEquals (0, empty.paintStart ());
		assertEquals (0, empty.paintEndExclusive ());
		assertEquals (-1, empty.columnAtViewportPixel (3), "blank pixel");
		HorizontalWindow window = columns ();
		assertTrue (window.setViewport (20, 0, 5, false));
		assertEquals (20, window.paintStart (), "zero width has no demand");
		assertEquals (20, window.paintEndExclusive ());
		assertEquals (0, window.firstSlot ());
		assertEquals (0, window.endSlot ());
		assertFalse (window.ensureColumnVisible (2), "zero-width viewport");
		assertThrows (IllegalArgumentException.class, () -> window.setViewport (0, -1, 0, false));
		assertThrows (IllegalArgumentException.class, () -> window.setViewport (0, 1, -1, false));
		assertThrows (IllegalArgumentException.class, () -> window.setViewport (0, Integer.MAX_VALUE, 1, false));
		HorizontalWindow wide = columns ();
		wide.setViewport (0, 100, 0, false);
		assertEquals (-1, wide.columnAtViewportPixel (90), "past the content");
	}

	@Test
	public void ensureColumnVisibleAndRelayout () {
		HorizontalWindow window = columns ();
		window.setViewport (0, 25, 0, false);
		assertFalse (window.ensureColumnVisible (1), "zero-width column");
		assertFalse (window.ensureColumnVisible (2), "already visible");
		assertTrue (window.ensureColumnVisible (3));
		assertEquals (35, window.origin ());
		assertTrue (window.ensureColumnVisible (2));
		assertEquals (0, window.origin ());
		window.setViewport (40, 10, 0, false);
		assertTrue (window.ensureColumnVisible (2), "oversized column shows its leading edge");
		assertEquals (0, window.origin ());
		window.setViewport (50, 10, 0, false);
		assertTrue (window.setColumns (new long [] {10, 0, 10, 10}, new int [] {2, 0, 1, 3}));
		assertEquals (20, window.origin (), "relayout clamps the origin to the new content");
		assertEquals (2, window.geometryGeneration ());
	}
}
