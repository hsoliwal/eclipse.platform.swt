/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
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
 *     Hitesh Soliwal (Synexia) - accumulated damage containment regressions
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Random;

import org.eclipse.swt.internal.ViewportDamageRegion;
import org.junit.jupiter.api.Test;

/** Headless subset contracts using independent pixel-mask and Java2D oracles. */
public class Test_ViewportDamageSubset {

	private static final int LIMIT = 1 << 29;

	@Test
	public void coalescedHorizontalRegionsRemainSubsetsOfThemselves () {
		ViewportDamageRegion region = region (-LIMIT, 0, LIMIT, 1, 0, 0, LIMIT, 1);
		assertEquals (1, region.rectCount ());
		assertEquals (2 * LIMIT, region.width (0));
		assertSubset ("horizontal reflexivity", region, region, true);
	}

	@Test
	public void coalescedVerticalRegionsRemainSubsetsOfThemselves () {
		ViewportDamageRegion region = region (0, -LIMIT, 1, LIMIT, 0, 0, 1, LIMIT);
		assertEquals (1, region.rectCount ());
		assertEquals (2 * LIMIT, region.height (0));
		assertSubset ("vertical reflexivity", region, region, true);
	}

	@Test
	public void coalescedRegionsCompareAgainstDistinctRegions () {
		ViewportDamageRegion wide = region (-LIMIT, 0, LIMIT, 1, 0, 0, LIMIT, 1);
		ViewportDamageRegion same = region (0, 0, LIMIT, 1, -LIMIT, 0, LIMIT, 1);
		ViewportDamageRegion half = region (0, 0, LIMIT, 1);
		assertAll (
				() -> assertSubset ("equivalent regions", wide, same, true),
				() -> assertSubset ("partial coverage", wide, half, false),
				() -> assertSubset ("contained half", half, wide, true),
				() -> assertSubset ("nonempty versus empty", wide, region (), false));
	}

	@Test
	public void collapsedLargeRegionsMatchIndependentAreaOracle () {
		ViewportDamageRegion collapsed = new ViewportDamageRegion (1);
		collapsed.add (-LIMIT, -LIMIT, LIMIT, LIMIT);
		collapsed.add (LIMIT, LIMIT, LIMIT, LIMIT);
		assertTrue (collapsed.overflowed ());
		assertEquals (1, collapsed.rectCount ());
		assertSubsetByArea ("collapsed reflexivity", collapsed, collapsed);
		ViewportDamageRegion corner = region (-LIMIT, -LIMIT, LIMIT, LIMIT);
		assertSubsetByArea ("collapsed contains corner", corner, collapsed);
		assertSubsetByArea ("corner misses collapsed bounds", collapsed, corner);
	}

	@Test
	public void maximumSupportedUnionKeepsExactLongAreaAndContainment () {
		ViewportDamageRegion region = new ViewportDamageRegion ();
		for (int y : new int [] {-LIMIT, 0, LIMIT}) {
			for (int x : new int [] {-LIMIT, 0, LIMIT}) region.add (x, y, LIMIT, LIMIT);
		}
		assertEquals (1, region.rectCount ());
		assertEquals (3 * LIMIT, region.width (0));
		assertEquals (3 * LIMIT, region.height (0));
		assertEquals (9L * LIMIT * LIMIT, region.area ());
		assertEquals (region.area (), region.intersectionArea (region));
		assertSubsetByArea ("maximum-domain reflexivity", region, region);
	}

	@Test
	public void subsetMatchesEveryPairOfSmallPixelMasks () {
		ViewportDamageRegion [] regions = new ViewportDamageRegion [64];
		for (int mask = 0; mask < regions.length; mask++) regions [mask] = pixelMask (mask);
		for (int left = 0; left < regions.length; left++) {
			for (int right = 0; right < regions.length; right++) {
				assertSubset ("pixel masks " + left + " <= " + right,
						regions [left], regions [right], (left & ~right) == 0);
			}
		}
	}

	@Test
	public void largeDomainSubsetsMatchIndependentJava2D () {
		Random random = new Random (0x5357545355425345L);
		for (int round = 0; round < 240; round++) {
			ViewportDamageRegion left = randomRegion (random, round % 3 == 0 ? 1 : 64);
			ViewportDamageRegion right = randomRegion (random, round % 4 == 0 ? 1 : 64);
			assertSubsetByArea ("random reflexivity " + round, left, left);
			assertSubsetByArea ("random left/right " + round, left, right);
			assertSubsetByArea ("random right/left " + round, right, left);
		}
	}

	@Test
	public void emptyAndNullBehaviorRemainsCompatible () {
		ViewportDamageRegion empty = region ();
		ViewportDamageRegion nonempty = region (1, 2, 3, 4);
		assertSubset ("empty reflexivity", empty, empty, true);
		assertSubset ("empty subset", empty, nonempty, true);
		assertSubset ("nonempty versus empty", nonempty, empty, false);
		assertTrue (empty.isSubsetOf (null));
		long [] before = snapshot (nonempty);
		assertThrows (NullPointerException.class, () -> nonempty.isSubsetOf (null));
		assertArrayEquals (before, snapshot (nonempty));
	}

	@Test
	public void publicCoverageQueryValidationRemainsUnchanged () {
		ViewportDamageRegion region = region (-LIMIT, 0, LIMIT, 1, 0, 0, LIMIT, 1);
		int outside = LIMIT + 1;
		int [][] invalid = {
				{-outside, 0, 1, 1}, {outside, 0, 1, 1},
				{0, -outside, 1, 1}, {0, outside, 1, 1},
				{0, 0, outside, 1}, {0, 0, 1, outside}
		};
		long [] before = snapshot (region);
		for (int [] box : invalid) {
			assertThrows (IllegalArgumentException.class,
					() -> region.covers (box [0], box [1], box [2], box [3]));
			assertArrayEquals (before, snapshot (region));
		}
		for (int extent : new int [] {0, -1, Integer.MIN_VALUE}) {
			assertTrue (region.covers (Integer.MAX_VALUE, Integer.MIN_VALUE, extent, 1));
			assertTrue (region.covers (Integer.MAX_VALUE, Integer.MIN_VALUE, 1, extent));
		}
		assertArrayEquals (before, snapshot (region));
	}

	@Test
	public void subsetQueriesPreserveGeometryAndCopyMetrics () {
		ViewportDamageRegion left = new ViewportDamageRegion ();
		left.addCopy (0, 0, -LIMIT, 0, LIMIT, 1);
		left.addCopy (0, 0, 0, 0, LIMIT, 1);
		ViewportDamageRegion right = region (-LIMIT, 0, LIMIT, 1, 0, 0, LIMIT, 1);
		assertEquals (2, left.copyCount ());
		assertEquals (2L * LIMIT, left.copiedPixels ());
		assertSubsetByArea ("query preserves copy metrics", left, right);
		assertSubsetByArea ("alias preserves copy metrics", left, left);
	}

	@Test
	public void subsetRejectsHolesWhenBoundsAreEqual () {
		ViewportDamageRegion ring = region (0, 0, 4, 1, 0, 3, 4, 1,
				0, 1, 1, 2, 3, 1, 1, 2);
		ViewportDamageRegion filled = region (0, 0, 4, 4);
		assertSubset ("filled region has uncovered center", filled, ring, false);
		assertSubset ("ring lies inside filled region", ring, filled, true);
		assertSubsetByArea ("same bounds with hole", filled, ring);
	}

	private static ViewportDamageRegion region (int... boxes) {
		ViewportDamageRegion result = new ViewportDamageRegion ();
		for (int i = 0; i < boxes.length; i += 4) {
			result.add (boxes [i], boxes [i + 1], boxes [i + 2], boxes [i + 3]);
		}
		return result;
	}

	private static ViewportDamageRegion pixelMask (int mask) {
		ViewportDamageRegion region = new ViewportDamageRegion ();
		for (int bit = 0; bit < 6; bit++) {
			if ((mask & 1 << bit) != 0) region.add (bit % 3 - 1, bit / 3 - 1, 1, 1);
		}
		return region;
	}

	private static ViewportDamageRegion randomRegion (Random random, int capacity) {
		ViewportDamageRegion result = new ViewportDamageRegion (capacity);
		int [] coordinates = {-LIMIT, -LIMIT / 2, -1, 0, 1, LIMIT / 2, LIMIT};
		int [] extents = {1, 2, 17, LIMIT / 2, LIMIT};
		for (int i = 0, count = random.nextInt (9); i < count; i++) {
			result.add (coordinates [random.nextInt (coordinates.length)],
					coordinates [random.nextInt (coordinates.length)],
					extents [random.nextInt (extents.length)], extents [random.nextInt (extents.length)]);
		}
		return result;
	}

	private static void assertSubsetByArea (String label, ViewportDamageRegion left,
			ViewportDamageRegion right) {
		Area uncovered = area (left);
		uncovered.subtract (area (right));
		assertSubset (label, left, right, uncovered.isEmpty ());
	}

	/** Reads stored geometry so collapsed regions retain their documented bounding approximation. */
	private static Area area (ViewportDamageRegion region) {
		Area result = new Area ();
		for (int i = 0; i < region.rectCount (); i++) {
			result.add (new Area (new Rectangle2D.Double (region.x (i), region.y (i),
					region.width (i), region.height (i))));
		}
		return result;
	}

	private static void assertSubset (String label, ViewportDamageRegion left,
			ViewportDamageRegion right, boolean expected) {
		long [] leftBefore = snapshot (left), rightBefore = snapshot (right);
		assertAll (label,
				() -> assertEquals (expected, left.isSubsetOf (right)),
				() -> assertArrayEquals (leftBefore, snapshot (left), "receiver state"),
				() -> assertArrayEquals (rightBefore, snapshot (right), "argument state"));
	}

	private static long [] snapshot (ViewportDamageRegion region) {
		long [] result = new long [4 + 4 * region.rectCount ()];
		result [0] = region.area ();
		result [1] = region.copyCount ();
		result [2] = region.copiedPixels ();
		result [3] = region.overflowed () ? 1 : 0;
		for (int i = 0; i < region.rectCount (); i++) {
			result [4 + 4 * i] = region.x (i);
			result [5 + 4 * i] = region.y (i);
			result [6 + 4 * i] = region.width (i);
			result [7 + 4 * i] = region.height (i);
		}
		return result;
	}
}
