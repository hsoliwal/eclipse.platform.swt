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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.internal.render.m3.gc.IM3Resources;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;

/**
 * Headless: the stroke state of the M3 GC (GC seam, leaf 5; pattern M3-GC-STROKE v1). The donor stored the dash offset and
 * the miter limit and never applied them. Now the miter limit of <code>setLineAttributes</code> reaches every stroke
 * (until then the paint keeps the donor's Skija miter, 4, and drawPath's 1000, which the adopted draw tests pin) while
 * <code>getLineAttributes</code> reports the stock default 10; the dash offset is the phase of a <code>LINE_CUSTOM</code>
 * pattern and, as in the stock GC, of no predefined style. The phase is proven on real raster pixels.
 */
public class Test_org_eclipse_swt_m3gc_StrokeState {
	static final int WIDTH = 64;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	static M3ExternalGC gc(M3SurfaceDouble surface) {
		IM3Resources resources = new M3ResourcesDouble();
		resources.setForeground(new Color(null, 255, 255, 255));
		resources.setBackground(new Color(null, 0, 0, 0));
		CanvasExtensionDouble ext = new CanvasExtensionDouble();
		ext.surface = surface;
		ext.resources = resources;
		return new M3ExternalGC(ext, SWT.NONE);
	}

	static float miter(M3SurfaceDouble surface, int call) {
		return Arrays.stream(surface.calls.get(call).params).filter(PaintData.class::isInstance).map(PaintData.class::cast)
				.findFirst().orElseThrow().strokeMiter;
	}

	static LineAttributes attributes(int style, float[] dash, float offset, float miter) {
		return new LineAttributes(1, SWT.CAP_FLAT, SWT.JOIN_MITER, style, dash, offset, miter);
	}

	@Test
	public void testDefaultsKeepTheAdoptedContract() {
		M3SurfaceDouble surface = new M3SurfaceDouble(null);
		M3ExternalGC gc = gc(surface);
		gc.drawLine(10, 20, 30, 40);
		assertEquals(4.0f, miter(surface, 0), "no limit given: the donor's Skija default");
		assertEquals(10f, gc.getLineAttributes().miterLimit, "the stock GCData default (the donor reported 0)");
		assertEquals(0f, gc.getLineAttributes().dashOffset);
	}

	@Test
	public void testGivenMiterLimitReachesEveryStroke() {
		M3SurfaceDouble surface = new M3SurfaceDouble(null);
		M3ExternalGC gc = gc(surface);
		gc.setLineAttributes(attributes(SWT.LINE_SOLID, null, 0, 2.5f));
		gc.drawLine(10, 20, 30, 40);
		gc.drawRectangle(1, 1, 10, 10);
		assertEquals(2.5f, miter(surface, 0), "drawLine");
		assertEquals(2.5f, miter(surface, 1), "drawRectangle");
		assertEquals(2.5f, gc.getLineAttributes().miterLimit);
	}

	@Test
	public void testDrawPathKeepsTheDonorMiterUntilALimitIsGiven() {
		M3SurfaceDouble surface = new M3SurfaceDouble(null);
		M3ExternalGC gc = gc(surface);
		Path path = new Path(null);
		path.moveTo(10, 20);
		path.lineTo(30, 40);
		path.lineTo(10, 40);
		gc.drawPath(path);
		gc.setLineAttributes(attributes(SWT.LINE_SOLID, null, 0, 3f));
		gc.drawPath(path);
		path.dispose();
		assertEquals(1000f, miter(surface, 0), "the donor's drawPath miter");
		assertEquals(3f, miter(surface, 1), "the given limit wins");
	}

	@Test
	public void testRoundTripStrokesWithTheStockMiter() {
		M3SurfaceDouble surface = new M3SurfaceDouble(null);
		M3ExternalGC gc = gc(surface);
		gc.setLineAttributes(gc.getLineAttributes());
		gc.drawLine(10, 20, 30, 40);
		assertEquals(10f, miter(surface, 0), "get/set keeps the stock 10 (a 0 would bevel every join)");
	}

	/* the on/off pixels of a horizontal 1-pixel line through the GC, rasterized by Skija on a real bitmap row */
	static boolean[] row(int style, float[] dash, float offset) {
		try (Bitmap bitmap = new Bitmap()) {
			bitmap.allocN32Pixels(WIDTH, 8);
			try (Canvas real = new Canvas(bitmap)) {
				real.clear(0xFF000000);
				M3SurfaceDouble surface = new M3SurfaceDouble(null);
				surface.canvas = new M3CanvasDouble(WIDTH, 8, "real", surface.calls) {
					@Override
					public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
						super.drawLine(x0, y0, x1, y1, paint);
						real.drawLine(x0, y0, x1, y1, paint);
					}
				};
				M3ExternalGC gc = gc(surface);
				gc.setLineAttributes(attributes(style, dash, offset, 10));
				gc.drawLine(0, 4, WIDTH - 1, 4);
				boolean[] on = new boolean[WIDTH];
				for (int x = 0; x < WIDTH; x++) {
					on[x] = (bitmap.getColor(x, 4) & 0xFFFFFF) != 0;
				}
				return on;
			}
		}
	}

	@Test
	public void testDashOffsetShiftsACustomPattern() {
		float[] dash = { 4, 4 };
		// offset 2, a quarter of the period: the direction is observable (half a period would look the same both ways, L5-40)
		boolean[] plain = row(SWT.LINE_CUSTOM, dash, 0), shifted = row(SWT.LINE_CUSTOM, dash, 2);
		// the line starts at x 0.5 (the GC's half-pixel offset) and Skia's non-antialiased edge rounds it to pixel 1 (L5-38)
		int first = first(plain);
		assertTrue(first >= 0 && first <= 1, "offset 0 starts with the dash: " + Arrays.toString(plain));
		assertTrue(plain[first + 3] && !plain[first + 4] && !plain[first + 7] && plain[first + 8], "4 on, 4 off: " + Arrays.toString(plain));
		assertTrue(shifted[first] && !shifted[first + 2], "offset 2 starts 2 pixels into the dash: " + Arrays.toString(shifted));
		boolean[] expected = Arrays.copyOfRange(plain, first + 2, WIDTH - 2), actual = Arrays.copyOfRange(shifted, first, WIDTH - 4);
		assertArrayEquals(expected, actual, "the pattern starts the offset into itself (GDI+ DashOffset, Skia phase)");
	}

	@Test
	public void testDashOffsetIgnoredForPredefinedStyles() {
		boolean[] plain = row(SWT.LINE_DASH, null, 0), offset = row(SWT.LINE_DASH, null, 3);
		assertTrue(contains(plain, true) && contains(plain, false), "LINE_DASH draws dashes: " + Arrays.toString(plain));
		assertArrayEquals(plain, offset, "the stock GC offsets only a custom pattern");
	}

	static int first(boolean[] values) {
		for (int i = 0; i < values.length; i++) {
			if (values[i]) {
				return i;
			}
		}
		return -1;
	}

	static boolean contains(boolean[] values, boolean value) {
		for (boolean v : values) {
			if (v == value) {
				return true;
			}
		}
		return false;
	}
}
