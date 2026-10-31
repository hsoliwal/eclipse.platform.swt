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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 5)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.render.m3.M3CanvasDamage;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3Bitmap;
import jdk.m3.graphics.M3Canvas;

/**
 * Headless (no Display): the damage atom of the M3 canvas handler (GC seam, leaf 5; pattern M3-CANVAS-DAMAGE v1). The
 * region data is what <code>GetRegionData</code> answers; the passes are the stock <code>Composite.WM_PAINT</code>'s (one
 * merged event over the bounds, or one per rectangle with <code>SWT.NO_MERGE_PAINTS</code>); the clip is proven on real
 * pixels of a Skija raster bitmap: a full clear inside it touches the region and nothing else.
 */
public class Test_org_eclipse_swt_m3gc_CanvasDamage {
	static final int BLACK = 0xFF000000, RED = 0xFFFF0000, GREEN = 0xFF00FF00;

	/** RGNDATA in ints: the header (dwSize 32, iType RDH_RECTANGLES, nCount, nRgnSize, rcBound) and left, top, right, bottom per rectangle. */
	static int[] region(int... ltrb) {
		int n = ltrb.length / 4;
		int[] data = new int[M3CanvasDamage.HEADER + ltrb.length];
		data[0] = 32;
		data[1] = 1;
		data[2] = n;
		data[3] = 16 * n;
		System.arraycopy(ltrb, 0, data, M3CanvasDamage.HEADER, ltrb.length);
		return data;
	}

	static int[] rect(M3CanvasDamage damage, int i) {
		return new int[] { damage.x(i), damage.y(i), damage.width(i), damage.height(i) };
	}

	@Test
	public void testNoRegionIsThePaintRectangle() {
		M3CanvasDamage damage = new M3CanvasDamage();
		damage.read(null, 5, 6, 25, 16);
		assertEquals(1, damage.count());
		assertArrayEquals(new int[] { 5, 6, 20, 10 }, rect(damage, 0));
		assertArrayEquals(new int[] { 5, 6, 20, 10 }, damage.bounds(0, damage.passes(SWT.NONE)));
		damage.read(region(), 5, 6, 25, 16);
		assertEquals(1, damage.count(), "a region without rectangles");
		assertArrayEquals(new int[] { 5, 6, 20, 10 }, rect(damage, 0));
	}

	@Test
	public void testRectanglesAndBounds() {
		M3CanvasDamage damage = new M3CanvasDamage();
		damage.read(region(10, 10, 30, 20, 50, 40, 70, 60), 10, 10, 70, 60);
		assertEquals(2, damage.count());
		assertArrayEquals(new int[] { 10, 10, 20, 10 }, rect(damage, 0));
		assertArrayEquals(new int[] { 50, 40, 20, 20 }, rect(damage, 1));
		assertArrayEquals(new int[] { 10, 10, 60, 50 }, damage.bounds(0, 1), "merged: the region's bounds");
	}

	@Test
	public void testMergedUnlessNoMergePaints() {
		M3CanvasDamage damage = new M3CanvasDamage();
		damage.read(region(10, 10, 30, 20, 50, 40, 70, 60), 10, 10, 70, 60);
		assertEquals(1, damage.passes(SWT.NONE));
		assertEquals(1, damage.passes(SWT.DOUBLE_BUFFERED | SWT.NO_BACKGROUND));
		assertEquals(2, damage.passes(SWT.NO_MERGE_PAINTS));
		assertArrayEquals(rect(damage, 0), damage.bounds(0, 2));
		assertArrayEquals(rect(damage, 1), damage.bounds(1, 2));
		assertEquals(1, damage.eventCount(0, 2), "Event.count: rectangles still to come");
		assertEquals(0, damage.eventCount(1, 2));
		assertEquals(0, damage.eventCount(0, 1));
		damage.read(region(10, 10, 30, 20), 10, 10, 30, 20);
		assertEquals(1, damage.passes(SWT.NO_MERGE_PAINTS), "one rectangle is one event");
	}

	@Test
	public void testIntersectedWithThePaintRectangle() {
		M3CanvasDamage damage = new M3CanvasDamage();
		damage.read(region(0, 0, 40, 40, 100, 100, 120, 120), 10, 10, 30, 30);
		assertEquals(1, damage.count(), "the rectangle outside rcPaint is dropped");
		assertArrayEquals(new int[] { 10, 10, 20, 20 }, rect(damage, 0));
		damage.read(region(100, 100, 120, 120), 10, 10, 30, 30);
		assertEquals(1, damage.count());
		assertArrayEquals(new int[] { 10, 10, 20, 20 }, rect(damage, 0), "nothing inside: the paint rectangle");
	}

	@Test
	public void testGrowsPastItsInitialCapacityAndResets() {
		int[] ltrb = new int[4 * 20];
		for (int i = 0; i < 20; i++) {
			ltrb[4 * i] = i * 5;
			ltrb[4 * i + 1] = 0;
			ltrb[4 * i + 2] = i * 5 + 3;
			ltrb[4 * i + 3] = 7;
		}
		M3CanvasDamage damage = new M3CanvasDamage();
		damage.read(region(ltrb), 0, 0, 100, 10);
		assertEquals(20, damage.count());
		assertArrayEquals(new int[] { 95, 0, 3, 7 }, rect(damage, 19));
		assertEquals(20, damage.passes(SWT.NO_MERGE_PAINTS));
		damage.read(region(1, 1, 2, 2), 0, 0, 100, 10);
		assertEquals(1, damage.count(), "a new paint starts empty");
	}

	@Test
	public void testClipTouchesTheRegionOnly() {
		M3CanvasDamage damage = new M3CanvasDamage();
		damage.read(region(10, 10, 30, 20, 50, 40, 70, 60), 10, 10, 70, 60);
		try (M3Bitmap bitmap = new M3Bitmap()) {
			bitmap.allocN32Pixels(80, 80);
			try (M3Canvas canvas = new M3Canvas(bitmap)) {
				canvas.clear(BLACK);
				canvas.save();
				damage.clip(canvas, 0, 1);
				canvas.clear(RED);
				canvas.restore();
				assertEquals(RED, bitmap.getColor(15, 15), "first rectangle");
				assertEquals(RED, bitmap.getColor(55, 45), "second rectangle");
				assertEquals(BLACK, bitmap.getColor(40, 15), "inside the bounds, outside the region");
				assertEquals(BLACK, bitmap.getColor(5, 5), "outside the bounds");
				canvas.clear(BLACK);
				canvas.save();
				damage.clip(canvas, 1, 2);
				canvas.clear(GREEN);
				canvas.restore();
				assertEquals(GREEN, bitmap.getColor(55, 45), "the NO_MERGE_PAINTS pass of the second rectangle");
				assertEquals(BLACK, bitmap.getColor(15, 15), "the first rectangle is another pass");
			}
		}
	}
}
