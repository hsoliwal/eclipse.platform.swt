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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 6)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.LineAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3Bitmap;
import jdk.m3.graphics.M3Canvas;
import jdk.m3.graphics.M3Paint;

/**
 * Headless: the stroke scale of the M3 GC (GC seam, leaf 6; atom STROKE_SCALE, pattern M3-GC-STROKE v2; census GF14, GF11).
 * Oracle: the stock win32 GC keeps the line width in points apart from its pixels (setLineAttributes copies the caller's
 * object), and a custom dash pattern is absolute: GDI+ divides each length by the width and the pen multiplies it back, GDI
 * PS_USERSTYLE takes it as is; an odd array is doubled (GC.getPen). The dash lengths are measured on real raster pixels.
 */
public class Test_org_eclipse_swt_m3gc_StrokeScale {
	static final int POINTS = 64;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testLineAttributesWidthIsPoints() {
		M3GcFixture f = new M3GcFixture(200);
		f.gc.setLineAttributes(new LineAttributes(2));
		f.gc.drawLine(1, 1, 30, 1);
		assertEquals(2, f.gc.getLineWidth(), "getLineWidth answers points");
		assertEquals(2f, f.gc.getLineAttributes().width, "getLineAttributes answers points");
		assertEquals(4f, f.paint("drawLine").strokeWidth, "2 points stroke 4 pixels at 200 %");
	}

	@Test
	public void testLineWidthAndAttributesAgree() {
		M3GcFixture f = new M3GcFixture(200);
		f.gc.setLineWidth(3);
		f.gc.setLineDash(new int[] { 4, 2 });
		LineAttributes read = f.gc.getLineAttributes();
		assertEquals(3f, read.width, "setLineWidth and getLineAttributes share one unit");
		assertArrayEquals(new float[] { 4, 2 }, read.dash, "the dash reads back in points");
	}

	@Test
	public void testCallerAttributesAreNotMutated() {
		M3GcFixture f = new M3GcFixture(100);
		LineAttributes given = new LineAttributes(0);
		f.gc.setLineAttributes(given);
		assertEquals(0f, given.width, "the stock GC copies the attributes; the caller's object keeps its width");
	}

	@Test
	public void testCustomDashIsAbsolute() {
		int[] runs = runs(row(200, 3, new int[] { 4, 2 }));
		assertEquals(8, runs[2], "4 points on at 200 % = 8 pixels whatever the width: " + Arrays.toString(runs));
		assertEquals(4, runs[1], "2 points off = 4 pixels: " + Arrays.toString(runs));
	}

	@Test
	public void testOddDashIsDoubled() {
		int[] runs = runs(row(200, 1, new int[] { 3 }));
		assertEquals(6, runs[2], "{3} = {3, 3}: 6 pixels on at 200 %: " + Arrays.toString(runs));
		assertEquals(6, runs[1], "6 pixels off: " + Arrays.toString(runs));
	}

	@Test
	public void testOddAndEvenScaleAlike() {
		assertArrayEquals(row(200, 2, new int[] { 3, 3 }), row(200, 2, new int[] { 3 }), "{3} and {3, 3} draw the same pattern");
	}

	/* the on/off pixels of the middle row of a horizontal dashed line through the GC, rasterized by Skija on a real bitmap */
	static boolean[] row(int zoom, int width, int[] dash) {
		int pixels = POINTS * zoom / 100, y = 20 * zoom / 100;
		try (M3Bitmap bitmap = new M3Bitmap()) {
			bitmap.allocN32Pixels(pixels, 2 * y);
			try (M3Canvas real = new M3Canvas(bitmap)) {
				real.clear(0xFF000000);
				M3GcFixture f = new M3GcFixture(zoom);
				f.surface.canvas = new M3CanvasDouble(pixels, 2 * y, "real", f.surface.calls) {
					@Override
					public void drawLine(float x0, float y0, float x1, float y1, M3Paint paint) {
						real.drawLine(x0, y0, x1, y1, paint);
					}
				};
				f.gc.setLineWidth(width);
				f.gc.setLineDash(dash);
				f.gc.drawLine(0, 20, POINTS - 1, 20);
				boolean[] on = new boolean[pixels];
				IntStream.range(0, pixels).forEach(x -> on[x] = (bitmap.getColor(x, y) & 0xFFFFFF) != 0);
				return on;
			}
		}
	}

	/* run lengths from the first lit pixel: on, off, on, ... */
	static int[] runs(boolean[] row) {
		int start = IntStream.range(0, row.length).filter(x -> row[x]).findFirst().orElse(row.length);
		int[] boundaries = IntStream.range(start + 1, row.length).filter(x -> row[x] != row[x - 1]).toArray();
		int[] edges = IntStream.concat(IntStream.of(start), IntStream.of(boundaries)).toArray();
		return IntStream.range(0, edges.length - 1).map(i -> edges[i + 1] - edges[i]).toArray();
	}
}
