/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.internal.maya.Bitmap;
import org.eclipse.swt.internal.maya.ColorAlphaType;
import org.eclipse.swt.internal.maya.ImageInfo;
import org.eclipse.swt.internal.maya.Surface;
import org.eclipse.swt.tests.junit.maya.CanvasExtensionDouble;
import org.eclipse.swt.tests.junit.maya.MayaResourcesDouble;
import org.eclipse.swt.tests.junit.maya.SupportedTestPlatforms;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * maya's GC on a real raster surface, where a scrolled viewport draws: a TextLayout goes over the pixels already there
 * (a block's background), not over a white box; a layout or an image that starts above or left of the surface keeps
 * its rows and columns (negative coordinates were rounded toward zero, one pixel off).
 */
public class Test_org_eclipse_swt_maya_TextLayoutDraw {

	private static final int W = 160, H = 40, BACKGROUND = 0xFF2F1B37, WHITE = 0xFFFFFFFF;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	/* the surface, as ARGB per pixel, after the drawing over the background */
	private static int[] draw(Consumer<RasterExternalGC> drawing) {
		try (Surface raster = Surface.makeRaster(ImageInfo.makeN32(W, H, ColorAlphaType.PREMUL))) {
			raster.getCanvas().clear(BACKGROUND);
			MayaResourcesDouble resources = new MayaResourcesDouble();
			resources.scaler = new DpiScalerUtil(100);
			resources.foreground = new Color(null, 0, 0, 0);
			resources.background = new Color(null, 255, 255, 255);
			CanvasExtensionDouble extension = new CanvasExtensionDouble();
			extension.resources = resources;
			extension.surface = new RasterSurfaceAdapter(raster);
			extension.device = Display.getDefault();
			extension.clientArea = new Rectangle(0, 0, W, H);
			drawing.accept(new RasterExternalGC(extension, SWT.NONE));
			Bitmap bitmap = new Bitmap();
			bitmap.allocN32Pixels(W, H);
			raster.readPixels(bitmap, 0, 0);
			byte[] bytes = bitmap.readPixels();
			bitmap.close();
			int[] pixels = new int[W * H];
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = (bytes[4 * i] & 0xFF) | (bytes[4 * i + 1] & 0xFF) << 8 | (bytes[4 * i + 2] & 0xFF) << 16
						| (bytes[4 * i + 3] & 0xFF) << 24;
			}
			return pixels;
		}
	}

	private static int[] text(int x, int y) {
		return draw(gc -> {
			TextLayout layout = new TextLayout(Display.getDefault());
			try {
				layout.setText("Hello maya, drawn");
				gc.textLayoutDraw(layout, null, x, y, -1, -1, null, null, 0);
			} finally {
				layout.dispose();
			}
		});
	}

	/* an image of 20 rows, each a different colour */
	private static int[] stripes(int x, int y) {
		return draw(gc -> {
			Image image = new Image(Display.getDefault(), 30, 20);
			GC painter = new GC(image);
			try {
				for (int r = 0; r < 20; r++) {
					Color colour = new Color(null, r * 12, 0, 255 - r * 12);
					painter.setForeground(colour);
					painter.drawLine(0, r, 29, r);
				}
				gc.drawImage(image, 0, 0, 30, 20, x, y, 30, 20);
			} finally {
				painter.dispose();
				image.dispose();
			}
		});
	}

	/* each pixel of the drawing at the moved position equals the reference's pixel moved by (dx, dy) */
	private static void assertMoved(int[] reference, int[] moved, int dx, int dy, String what) {
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int sx = x - dx, sy = y - dy;
				if (sx < 0 || sy < 0 || sx >= W || sy >= H) continue;
				assertEquals(Integer.toHexString(reference[sy * W + sx]), Integer.toHexString(moved[y * W + x]),
						what + ": pixel " + x + "," + y);
			}
		}
	}

	@Test
	public void theTextIsDrawnOverTheBackgroundNotAWhiteBox() {
		int white = 0, text = 0;
		for (int pixel : text(4, 10)) {
			if (pixel == WHITE) white++;
			if (pixel != BACKGROUND && pixel != WHITE) text++;
		}
		assertEquals(0, white, "white pixels");
		assertTrue(text > 40, "text pixels " + text);
	}

	@Test
	public void aLayoutAboveOrLeftOfTheSurfaceDrawsTheSameRows() {
		int[] reference = text(4, 8);
		for (int[] at : new int[][] { { 4, -5 }, { 4, -11 }, { -6, 8 }, { -3, -2 } }) {
			assertMoved(reference, text(at[0], at[1]), at[0] - 4, at[1] - 8, "text at " + at[0] + "," + at[1]);
		}
	}

	@Test
	public void anImageAboveOrLeftOfTheSurfaceKeepsItsRows() {
		int[] reference = stripes(4, 8);
		for (int[] at : new int[][] { { 4, -5 }, { 4, -1 }, { -7, 8 }, { -2, -9 } }) {
			assertMoved(reference, stripes(at[0], at[1]), at[0] - 4, at[1] - 8, "image at " + at[0] + "," + at[1]);
		}
	}

	@Test
	public void negativeCoordinatesScaleLikePositiveOnes() {
		DpiScalerUtil at100 = new DpiScalerUtil(100), at125 = new DpiScalerUtil(125);
		for (int v = -40; v <= 40; v++) {
			assertEquals(v, at100.autoScaleUp(v), "100% up " + v);
			assertEquals(v, at100.scaleDown(new org.eclipse.swt.graphics.Point(v, v)).x, "100% down " + v);
			assertEquals((int) Math.floor(v * 1.25 + 0.5), at125.autoScaleUp(v), "125% up (round half up) " + v);
		}
	}
}
