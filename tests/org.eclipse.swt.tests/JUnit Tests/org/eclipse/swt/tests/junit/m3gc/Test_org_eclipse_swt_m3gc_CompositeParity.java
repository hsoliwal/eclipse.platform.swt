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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.M3Resources;
import org.eclipse.swt.internal.render.m3.gc.M3SurfaceAdapter;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3ImageInfo;
import jdk.m3.graphics.M3PixelGeometry;
import jdk.m3.graphics.M3Surface;
import jdk.m3.graphics.M3SurfaceProps;

/**
 * Headless, on a real Skija raster surface with the back buffer's LCD pixel geometry: the GC fidelity defects leaf 7's
 * composite probe found (GC seam, leaf 6; L7-31, L7-32, L7-34, L7-35). Oracle: the stock win32 GC. Text is measured as the
 * stock GC measures it, so a widget that lays out with a stock GC outside paint (CTabFolder) does not shorten "Tab 2" in
 * paint; transparent text is not drawn as solid glyph blocks; a non-advanced gradient has GradientFill's rows exactly; a
 * horizontal one-pixel line covers x1 .. x2 - 1 like the stock line.
 */
public class Test_org_eclipse_swt_m3gc_CompositeParity {
	static final int WHITE = 0xFFFFFFFF;
	M3Surface real;
	M3GcFixture f;
	M3ExternalGC gc;
	Font font;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@BeforeEach
	void setUp() {
		real = M3Surface.makeRaster(M3ImageInfo.makeN32Premul(200, 60), 0, new M3SurfaceProps(false, M3PixelGeometry.RGB_H));
		real.getCanvas().clear(WHITE);
		font = new Font(Display.getDefault(), "Segoe UI", 9, SWT.NORMAL);
		f = new M3GcFixture(DPIUtil.getDeviceZoom());
		f.ext.surface = new M3SurfaceAdapter(real);
		M3Resources resources = new M3Resources(null, f.ext);
		resources.setFont(font);
		resources.setForeground(new Color(0, 0, 0));
		resources.setBackground(new Color(255, 255, 255));
		f.ext.resources = resources;
		gc = new M3ExternalGC(f.ext, SWT.NONE);
	}

	@AfterEach
	void tearDown() {
		real.close();
		font.dispose();
	}

	int pixel(int x, int y) {
		try (var bitmap = new jdk.m3.graphics.M3Bitmap()) {
			bitmap.allocN32Pixels(1, 1);
			real.readPixels(bitmap, x, y);
			return bitmap.getColor(0, 0) & 0xFFFFFF;
		}
	}

	/* pixels darker than mid grey in the band */
	int ink(int top, int bottom) {
		return (int) IntStream.range(top, bottom).flatMap(y -> IntStream.range(0, 200).map(x -> pixel(x, y)))
				.filter(c -> Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)) < 128).count();
	}

	@Test
	public void testTextExtentsAreTheStockGcs() {
		Image image = new Image(Display.getDefault(), 10, 10);
		GC stock = new GC(image);
		stock.setFont(font);
		int flags = SWT.DRAW_TRANSPARENT | SWT.DRAW_MNEMONIC | SWT.DRAW_DELIMITER;
		Stream.of("Tab 1", "Tab 2", "...", "Hello World", "a&&b", "two\nlines", "").forEach(text -> {
			assertEquals(stock.textExtent(text, flags), gc.textExtent(text, flags), "textExtent of '" + text + "'");
			assertEquals(stock.stringExtent(text), gc.stringExtent(text), "stringExtent of '" + text + "'");
		});
		stock.dispose();
		image.dispose();
	}

	@Test
	public void testTransparentTextIsNotDrawnAsBlocks() {
		real.getCanvas().clear(0xFFF0F0F0);
		gc.setBackground(new Color(240, 240, 240));
		gc.drawText("Tab 1", 4, 4, SWT.DRAW_TRANSPARENT | SWT.DRAW_MNEMONIC | SWT.DRAW_DELIMITER);
		gc.drawText("Tab 1", 4, 40, SWT.NONE);
		int transparent = black(0, 30), opaque = black(36, 60);
		assertTrue(transparent <= Math.max(opaque, 4) * 2, "pure black pixels: transparent " + transparent + " vs opaque " + opaque);
	}

	/* pixels of pure black in the band of the first 60 columns (solid glyph blocks are pure black, smoothed text hardly) */
	int black(int top, int bottom) {
		return (int) IntStream.range(top, bottom).flatMap(y -> IntStream.range(0, 60).map(x -> pixel(x, y))).filter(c -> c == 0).count();
	}

	@Test
	public void testGradientRowsAreGradientFills() {
		gc.setForeground(new Color(0, 84, 227));
		gc.setBackground(new Color(240, 240, 240));
		gc.fillGradientRectangle(0, 0, 10, 20 * 100 / DPIUtil.getDeviceZoom(), true);
		int rows = 20;
		IntStream.range(0, rows).forEach(i -> {
			int r = (0 * 257 + 240 * 257 * i / rows) >> 8, g = (84 * 257 + (240 - 84) * 257 * i / rows) >> 8;
			int b = (227 * 257 + (240 - 227) * 257 * i / rows) >> 8;
			assertEquals(String.format("%06x", (r << 16) | (g << 8) | b), String.format("%06x", pixel(5, i)), "row " + i);
		});
	}

	@Test
	public void testOnePixelAxisLinesCoverBothEnds() {
		assumeTrue(DPIUtil.getDeviceZoom() == 100, "pixel positions are those of 100 %");
		gc.drawLine(3, 19, 30, 19);
		gc.drawLine(40, 10, 40, 4);
		assertEquals(0x000000, pixel(3, 19), "the start pixel (stock: x1)");
		assertEquals(0x000000, pixel(30, 19), "the end pixel (stock: LineTo then SetPixel at x2 for a one-pixel pen)");
		assertEquals(0xFFFFFF, pixel(31, 19));
		assertEquals(0xFFFFFF, pixel(2, 19));
		assertEquals(0x000000, pixel(40, 4), "a vertical line drawn upwards covers both ends too");
		assertEquals(0x000000, pixel(40, 10));
		assertEquals(0xFFFFFF, pixel(40, 11));
		assertEquals(0xFFFFFF, pixel(40, 3));
	}
}
