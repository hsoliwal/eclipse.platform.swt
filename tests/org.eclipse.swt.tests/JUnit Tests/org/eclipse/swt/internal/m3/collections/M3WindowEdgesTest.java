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
 * com.m3.collections.M3WindowEdgesTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Edge tests of the viewport core: selection representations, flat window plan, plane bands, topology, projection. */
public class M3WindowEdgesTest {
	private static void selectEveryOther (M3RangeSet model, int count, boolean selected) {
		for (int i = 0; i < count; i += 2) model.setSelected (i, selected);
	}

	@Test
	public void rangesOnlyPolicyNeverSwitches () {
		M3RangeSet model = new M3RangeSet (false);
		model.setLogicalCount (4000);
		selectEveryOther (model, 4000, true);
		assertFalse (model.bitsMode ());
		assertEquals (2000, model.rangeCount ());
		assertEquals (0, model.representationSwitches ());
	}

	@Test
	public void eagerPolicyBitsForm () {
		M3RangeSet model = new M3RangeSet (true);
		model.setLogicalCount (100);
		selectEveryOther (model, 100, true);
		assertTrue (model.bitsMode ());
		assertTrue (model.bitmapMode ());
		assertEquals (1, model.representationSwitches ());
		assertEquals (50, model.selectedCount ());
		assertThrows (IllegalArgumentException.class, () -> model.setLogicalCount (-1));
		assertThrows (IllegalArgumentException.class, () -> model.isSelected (-1));
		assertThrows (IllegalArgumentException.class, () -> model.isSelected (100));
		assertThrows (IllegalArgumentException.class, () -> model.selectRange (-1, 2));
		assertThrows (IllegalArgumentException.class, () -> model.selectRange (5, 4));
		assertThrows (IllegalArgumentException.class, () -> model.deselectRange (0, 101));
		assertThrows (IllegalArgumentException.class, () -> model.insert (0, -1));
		assertThrows (IllegalArgumentException.class, () -> model.insert (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> model.insert (101, 1));
		assertThrows (IllegalArgumentException.class, () -> model.remove (0, -1));
		assertThrows (IllegalArgumentException.class, () -> model.remove (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> model.remove (0, 101));
		assertEquals (50, model.rangeCount ());
		assertThrows (IndexOutOfBoundsException.class, () -> model.rangeStart (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> model.rangeStart (50));
		assertThrows (IndexOutOfBoundsException.class, () -> model.rangeEndExclusive (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> model.rangeEndExclusive (50));
		model.setLogicalCount (50);
		assertEquals (50, model.logicalCount ());
		assertEquals (25, model.selectedCount (), "shrinking drops the selected tail");
		model.setLogicalCount (60);
		assertEquals (25, model.selectedCount ());
		assertFalse (model.isSelected (59));
		model.remove (0, 60);
		assertFalse (model.bitsMode (), "an empty bitset returns to the range form");
		assertEquals (0, model.logicalCount ());
	}

	@Test
	public void complementConvertsToBits () {
		M3RangeSet model = new M3RangeSet (true);
		model.setLogicalCount (100);
		model.selectAll ();
		assertTrue (model.complementMode ());
		selectEveryOther (model, 100, false);
		assertTrue (model.bitsMode ());
		assertFalse (model.complementMode ());
		assertEquals (50, model.selectedCount ());
		assertTrue (model.isSelected (1));
		assertFalse (model.isSelected (2));
	}

	@Test
	public void rowWindowPlanEdges () {
		M3WindowPlan plan = new M3WindowPlan ();
		assertThrows (IllegalArgumentException.class, () -> plan.plan (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> plan.plan (0, -1));
		assertThrows (IllegalArgumentException.class, () -> plan.plan (Integer.MAX_VALUE, 1));
		plan.plan (10, 5);
		assertEquals (10, plan.plannedFirst ());
		assertEquals (5, plan.plannedSize ());
		plan.commit ();
		assertEquals (15, plan.end ());
		assertEquals (12, plan.rowOf (2));
		assertThrows (IllegalArgumentException.class, () -> plan.rowOf (-1));
		assertThrows (IllegalArgumentException.class, () -> plan.rowOf (5));
		assertTrue (plan.covers (10, 15));
		assertFalse (plan.covers (9, 12));
		assertFalse (plan.covers (11, 16));
		assertThrows (IllegalArgumentException.class, () -> plan.inserted (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> plan.inserted (0, -1));
		assertEquals (-1, plan.inserted (3, 0));
		assertEquals (-1, plan.inserted (3, 2));
		assertEquals (12, plan.first (), "insert above shifts the window");
		assertEquals (2, plan.inserted (14, 1));
		assertEquals (6, plan.size (), "insert inside grows the window");
		assertEquals (-1, plan.inserted (40, 1));
		assertThrows (IllegalArgumentException.class, () -> plan.removed (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> plan.removed (0, -1));
		assertEquals (2, plan.removed (10, 4), "two rows above, two resident");
		assertEquals (10, plan.first ());
		assertEquals (4, plan.size ());
		assertEquals (3, plan.removedEntry (13));
		assertEquals (0, plan.removedEntry (2));
		plan.clear ();
		assertEquals (0, plan.first ());
		assertEquals (0, plan.size ());
		assertEquals (0, plan.plannedSize ());
		assertFalse (plan.replaced ());
		assertEquals (-1, plan.inserted (0, 3), "an empty window at the insert row shifts");
		assertEquals (3, plan.first ());
	}

	@Test
	public void planeGeometryBands () {
		assertTrue (M3PlaneGeometry.fitsPosition (-32768));
		assertFalse (M3PlaneGeometry.fitsPosition (-32769));
		assertTrue (M3PlaneGeometry.fitsPosition (32767));
		assertFalse (M3PlaneGeometry.fitsPosition (32768));
		assertEquals (M3PlaneGeometry.BAND, M3PlaneGeometry.nativeExtent (30000));
		assertEquals (100, M3PlaneGeometry.nativeExtent (100));
		int size = 100_000, position = -50_000, visible = 1000;
		int centred = 43_856;
		assertEquals (centred, M3PlaneGeometry.base (position, size, 0, visible, -1), "negative base");
		assertEquals (centred, M3PlaneGeometry.base (position, size, 0, visible, 80_000), "base past the maximum");
		assertEquals (centred, M3PlaneGeometry.base (position, size, 0, visible, 50_001), "band starts below the visible part");
		assertEquals (centred, M3PlaneGeometry.base (position, size, 0, visible, 30_000), "band ends before the visible part");
		assertEquals (45_000, M3PlaneGeometry.base (position, size, 0, visible, 45_000), "band still covers the visible part");
	}

	@Test
	public void topologyClearAndOverflow () {
		M3TreeTopology topology = new M3TreeTopology ();
		topology.bind (15, M3TreeTopology.ROOT, 5);
		topology.bind (0, M3TreeTopology.ROOT, Integer.MAX_VALUE);
		assertTrue ((long) topology.materializedCount () * 4 < topology.idCapacity (), "sparse shift path");
		long generation = topology.generation (), presentation = topology.presentationGeneration ();
		assertThrows (ArithmeticException.class, () -> topology.insertCoordinate (M3TreeTopology.ROOT, 0, 1),
				"the sparse shift falls back and the checked shift overflows");
		topology.clear ();
		assertFalse (topology.contains (0));
		assertFalse (topology.contains (15));
		assertEquals (0, topology.materializedCount ());
		assertTrue (topology.generation () > generation);
		assertEquals (presentation + 1, topology.presentationGeneration ());
		assertEquals (-1, topology.firstMaterializedChildId (M3TreeTopology.ROOT));
	}

	@Test
	public void remainingDecisionEdges () {
		M3WindowPlanner.HorizontalWindow window = new M3WindowPlanner.HorizontalWindow ();
		window.setColumns (new long [] {10, 20}, new int [] {0, 1});
		assertTrue (window.setColumns (new long [] {10, 20}, new int [] {1, 0}), "same widths, new order");
		assertTrue (window.setViewport (0, 50, 0, false));
		assertTrue (window.setViewport (0, 50, 4, false), "overscan change");
		assertTrue (window.setViewport (0, 50, 4, true), "direction change");
		assertFalse (window.ensureColumnVisible (0), "a column inside the viewport needs no move");

		M3WindowRuntime.RowWindow rows = new M3WindowRuntime.RowWindow ();
		rows.setLogicalCount (6);
		rows.setViewport (4, 2);
		long g = rows.generation ();
		rows.setViewport (4, 5);
		assertEquals (2, rows.visibleCount ());
		assertNotEquals (g, rows.generation (), "a new capacity with the same visible rows still counts");
		rows.setViewport (4, 1);
		assertEquals (1, rows.visibleCount ());

		M3WindowRuntime.PixelLayout pixels = M3WindowRuntime.solvePixels (100, 50, 200, 10, 10, 12,
				M3WindowRuntime.AUTO, M3WindowRuntime.NEVER);
		assertTrue (pixels.horizontalVisible ());
		assertFalse (pixels.cornerVisible ());
		M3WindowRuntime.RowLayout layout = M3WindowRuntime.solveRows (100, 50, 0, 1, 10, 10, 10, 12,
				M3WindowRuntime.ALWAYS, M3WindowRuntime.NEVER);
		assertTrue (layout.horizontalVisible ());
		assertFalse (layout.verticalVisible ());
		assertFalse (layout.cornerVisible ());

		M3WindowPlan plan = new M3WindowPlan ();
		plan.plan (10, 5);
		plan.commit ();
		assertEquals (0, plan.inserted (10, 1), "insert at the first resident row grows the window");
		assertEquals (6, plan.size ());

		M3ScrollMetrics single = new M3ScrollMetrics ();
		single.configure (1, 10, 0);
		assertEquals (1L, single.maxTopRow ());
		assertEquals (1, single.thumb ());
		assertEquals (0, single.selectionForTopRow (1), "a one-row bar with a full thumb cannot move");

		assertFalse (new M3RangeSet ().bitmapMode ());
		assertFalse (new M3RangeSet ().bitsMode ());
	}

	@Test
	public void projectionRowAndResidentKeys () {
		assertTrue (new M3TreeProjection.Row (M3TreeTopology.ROOT, 0, 5, 0).materialized ());
		assertFalse (new M3TreeProjection.Row (M3TreeTopology.ROOT, 0, -1, 0).materialized ());
		M3TreeWindowPlan plan = new M3TreeWindowPlan ();
		int [] parents = {M3TreeTopology.ROOT}, indices = {0}, physical = {-1};
		plan.plan (1, parents, indices, physical);
		plan.commit (1, parents, indices, physical, new long [] {77});
		assertEquals (M3TreeWindowPlan.key (M3TreeTopology.ROOT, 0), plan.keyOf (0));
		assertThrows (RuntimeException.class, () -> plan.keyOf (1));
	}
}
