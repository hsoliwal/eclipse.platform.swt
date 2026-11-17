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
package org.eclipse.swt.tests.junit.skia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.internal.canvasext.raster.IRasterImage;
import org.eclipse.swt.internal.canvasext.raster.IRasterResources;
import org.eclipse.swt.internal.canvasext.raster.RasterExternalGC;
import org.eclipse.swt.internal.canvasext.raster.SwtToRasterImageConverter;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.eclipse.swt.internal.maya.Bitmap;
import org.eclipse.swt.internal.maya.ColorType;

/** The raster GC's copies of SWT images: one per image while it does not change, in the surfaces' pixel format. */
public class Test_org_eclipse_swt_skia_drawImageCache {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	static List<IRasterImage> drawn(SkiaSurfaceDouble surface) {
		return surface.calls.stream().filter(c -> c.methodName.equals("drawImageRect")).map(c -> (IRasterImage) c.params[0]).toList();
	}

	/* the B, G, R, A bytes of a pixel of a copy, which must be BGRA_8888 */
	static int[] bgra(IRasterImage copy, int x, int y) {
		try (Bitmap bitmap = Bitmap.makeFromImage(copy.getSkiaImage())) {
			assertEquals(ColorType.BGRA_8888, bitmap.getColorType(), "the surfaces' pixel format");
			byte[] pixels = bitmap.readPixels();
			int i = (x + y * bitmap.getWidth()) * 4;
			return new int[] { pixels[i] & 0xFF, pixels[i + 1] & 0xFF, pixels[i + 2] & 0xFF, pixels[i + 3] & 0xFF };
		}
	}

	static void fill(Image image, int color) {
		GC gc = new GC(image);
		gc.setBackground(image.getDevice().getSystemColor(color));
		gc.fillRectangle(0, 0, 4, 4);
		gc.dispose();
	}

	@Test
	public void anImageDrawnAgainUsesOneCopyUntilItIsDrawnIntoAndDisposeClosesTheCopies() {
		Display display = Display.getDefault();
		Image image = new Image(display, 4, 4);
		fill(image, SWT.COLOR_RED);
		IRasterResources resources = new SkiaResourcesDouble();
		resources.setForeground(new Color(null, 0, 0, 0));
		resources.setBackground(new Color(null, 255, 255, 255));
		SkiaSurfaceDouble surface = new SkiaSurfaceDouble(null);
		CanvasExtensionDouble ext = new CanvasExtensionDouble();
		ext.surface = surface;
		ext.resources = resources;
		RasterExternalGC gc = new RasterExternalGC(ext, SWT.NONE);
		try {
			gc.drawImage(image, 0, 0);
			gc.drawImage(image, 10, 0);
			assertSame(drawn(surface).get(0), drawn(surface).get(1), "one copy for both draws");
			assertEquals(255, bgra(drawn(surface).get(0), 1, 1)[2], "the copy holds red (read while it is kept)");
			GC drawing = new GC(image);
			gc.drawImage(image, 20, 0);
			drawing.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			drawing.fillRectangle(0, 0, 4, 4);
			gc.drawImage(image, 25, 0);
			assertNotSame(drawn(surface).get(2), drawn(surface).get(3), "while a GC draws into the image no copy is kept: its pixels change");
			drawing.dispose();
			gc.drawImage(image, 30, 0);
			List<IRasterImage> copies = drawn(surface);
			copies = java.util.List.of(copies.get(0), copies.get(1), copies.get(2), copies.get(4));
			assertEquals(5, drawn(surface).size());
			assertNotSame(copies.get(1), copies.get(2), "while a GC draws into the image: a copy of its own");
			assertTrue(copies.get(2).isClosed(), "that copy is closed after its draw");
			assertNotSame(copies.get(1), copies.get(3), "after the image changed: a new copy");
			assertTrue(copies.get(1).isClosed(), "the stale copy is closed");
			assertFalse(copies.get(3).isClosed());
			int[] blue = bgra(copies.get(3), 1, 1);
			assertEquals(255, blue[0], "the new copy holds the new pixels (blue)");
			assertEquals(0, blue[2]);
			assertEquals(255, blue[3]);
		} finally {
			gc.dispose();
			image.dispose();
		}
		assertTrue(drawn(surface).get(3).isClosed(), "dispose closes the kept copies");
	}

	@Test
	public void copiesKeepColourAndAlpha() {
		ImageData data = new ImageData(2, 1, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
		data.setPixel(0, 0, 0x336699);
		data.setPixel(1, 0, 0xCC8844);
		data.setAlpha(0, 0, 255);
		data.setAlpha(1, 0, 128);
		try (IRasterImage copy = SwtToRasterImageConverter.convertSWTImageToSkiaImage(data)) {
			org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] { 0x99, 0x66, 0x33, 255 }, bgra(copy, 0, 0), "opaque: the colour, in BGRA order");
			/* 0xCC8844 at alpha 128, premultiplied as Skia does: (c * a + 128 + ((c * a + 128) >> 8)) >> 8 */
			org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] { 34, 68, 102, 128 }, bgra(copy, 1, 0), "premultiplied with Skia's rounding");
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
