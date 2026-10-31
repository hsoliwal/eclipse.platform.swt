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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.M3SurfaceAdapter;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3Paint;
import jdk.m3.graphics.M3Surface;
import jdk.m3.graphics.M3IRect;
import jdk.m3.graphics.M3Rect;

/**
 * Headless, on a real Skija raster surface: copyArea of the M3 GC (GC seam, leaf 6; atom PIXEL_COPY, pattern M3-GC-PIXEL-COPY
 * v1; census GF20, GF21). Oracle: the stock win32 GC (BitBlt SRCCOPY): a source outside the drawable copies nothing and a
 * source cut by its edge keeps every copied pixel at its offset; copyArea(Image) replaces the image's pixels with the GC's,
 * colours unchanged.
 */
public class Test_org_eclipse_swt_m3gc_PixelCopy {
	static final int WHITE = 0xFFFFFFFF, RED = 0xFFFF0000;
	M3Surface real;
	M3ExternalGC gc;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@BeforeEach
	void setUp() {
		real = M3Surface.makeRasterN32Premul(100, 100);
		real.getCanvas().clear(WHITE);
		M3GcFixture f = new M3GcFixture(100);
		f.ext.surface = new M3SurfaceAdapter(real);
		gc = new M3ExternalGC(f.ext, SWT.NONE);
	}

	@AfterEach
	void tearDown() {
		real.close();
	}

	int pixel(int x, int y) {
		try (var bitmap = new jdk.m3.graphics.M3Bitmap()) {
			bitmap.allocN32Pixels(1, 1);
			real.readPixels(bitmap, x, y);
			return bitmap.getColor(0, 0);
		}
	}

	@Test
	public void testSnapshotOutsideTheSurfaceIsNull() {
		assertNull(new M3SurfaceAdapter(real).makeImageSnapshot(new M3IRect(200, 200, 210, 210)), "no wrapper around a null image");
	}

	@Test
	public void testSourceOutsideTheSurfaceCopiesNothing() {
		gc.copyArea(200, 200, 10, 10, 0, 0);
		assertEquals(WHITE, pixel(0, 0), "BitBlt from outside the drawable: nothing, no exception");
	}

	@Test
	public void testSourceCutByTheEdgeKeepsItsOffset() {
		try (M3Paint red = new M3Paint().setColor(RED)) {
			real.getCanvas().drawRect(M3Rect.makeXYWH(0, 0, 2, 10), red);
		}
		gc.copyArea(-5, 0, 20, 10, 50, 50);
		assertEquals(RED, pixel(55, 52), "source x 0 lands at destination x 50 + 5");
		assertEquals(WHITE, pixel(50, 52), "the part of the source left of the surface copies nothing");
	}

	@Test
	public void testCopyIntoAnImageKeepsTheColours() {
		real.getCanvas().clear(0xFFC86432);
		Image image = new Image(Display.getDefault(), 4, 4);
		GC black = new GC(image);
		black.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
		black.fillRectangle(0, 0, 4, 4);
		black.dispose();
		gc.copyArea(image, 10, 10);
		ImageData data = image.getImageData();
		assertEquals(new RGB(200, 100, 50), data.palette.getRGB(data.getPixel(1, 1)), "the surface's colour replaces the image's");
		image.dispose();
	}
}
