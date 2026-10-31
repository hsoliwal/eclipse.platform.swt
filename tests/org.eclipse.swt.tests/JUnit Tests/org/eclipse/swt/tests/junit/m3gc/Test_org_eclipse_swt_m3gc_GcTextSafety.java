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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (text leaf L14)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.M3Resources;
import org.eclipse.swt.internal.render.m3.gc.M3SurfaceAdapter;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceProps;

/**
 * Headless, real Skija raster surface, oracle the stock win32 GC (text leaf L14). SURROGATE_SAFE: a lone surrogate and
 * an emoji ZWJ sequence draw and measure without reaching Skia's measurement; getAdvanceWidth is GetCharWidth. TAB_STOPS:
 * getCharWidth is the black-box width; DRAW_TAB puts the text after a tab at the stock tab stop.
 */
public class Test_org_eclipse_swt_m3gc_GcTextSafety {
	static final int W = 300, H = 40;
	Surface real;
	M3ExternalGC gc;
	Font font;
	Image image;
	GC stock;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@BeforeEach
	void setUp() {
		real = Surface.makeRaster(ImageInfo.makeN32Premul(W, H), 0, new SurfaceProps(false, PixelGeometry.RGB_H));
		real.getCanvas().clear(0xFFFFFFFF);
		font = new Font(Display.getDefault(), "Segoe UI", 9, SWT.NORMAL);
		M3GcFixture f = new M3GcFixture(DPIUtil.getDeviceZoom());
		f.ext.surface = new M3SurfaceAdapter(real);
		M3Resources resources = new M3Resources(null, f.ext);
		resources.setFont(font);
		resources.setForeground(new Color(0, 0, 0));
		resources.setBackground(new Color(255, 255, 255));
		f.ext.resources = resources;
		gc = new M3ExternalGC(f.ext, SWT.NONE);
		image = new Image(Display.getDefault(), W, H);
		stock = new GC(image);
		stock.setFont(font);
		stock.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
		stock.fillRectangle(0, 0, W, H);
	}

	@AfterEach
	void tearDown() {
		stock.dispose();
		image.dispose();
		real.close();
		font.dispose();
	}

	static String loneHigh() {
		return "a" + (char) 0xD83D + " b";
	}

	static String emojiZwj() {
		return new String(Character.toChars(0x1F469)) + (char) 0x200D + new String(Character.toChars(0x1F4BB));
	}

	@Test
	public void testLoneSurrogateDrawsAndMeasuresAsStock() {
		gc.drawText(loneHigh(), 0, 0, SWT.DRAW_TRANSPARENT);
		gc.drawString(String.valueOf((char) 0xDC00), 40, 0, true);
		assertEquals(stock.getAdvanceWidth((char) 0xD83D), gc.getAdvanceWidth((char) 0xD83D), "advance of a lone high surrogate");
		assertEquals(stock.textExtent(loneHigh()), gc.textExtent(loneHigh()), "textExtent with a lone surrogate");
	}

	@Test
	public void testEmojiZwjDraws() {
		gc.drawText(emojiZwj(), 0, 0, SWT.DRAW_TRANSPARENT);
		assertEquals(stock.textExtent(emojiZwj()), gc.textExtent(emojiZwj()), "textExtent of an emoji ZWJ sequence");
	}

	@Test
	public void testCharAndAdvanceWidthsAreStock() {
		"Wif&.".chars().forEach(c -> {
			assertEquals(stock.getCharWidth((char) c), gc.getCharWidth((char) c), "black-box width of " + (char) c);
			assertEquals(stock.getAdvanceWidth((char) c), gc.getAdvanceWidth((char) c), "advance of " + (char) c);
		});
	}

	/* first column at or right of x0 with dark ink, M3 surface */
	int m3InkFrom(int x0) {
		return IntStream.range(x0, W).filter(x -> IntStream.range(0, H).anyMatch(y -> dark(m3Pixel(x, y)))).findFirst().orElse(-1);
	}

	int stockInkFrom(ImageData d, int x0) {
		return IntStream.range(x0, W).filter(x -> IntStream.range(0, H).anyMatch(y -> {
			var rgb = d.palette.getRGB(d.getPixel(x, y));
			return dark(rgb.red << 16 | rgb.green << 8 | rgb.blue);
		})).findFirst().orElse(-1);
	}

	static boolean dark(int c) {
		return Math.max(c >> 16 & 0xFF, Math.max(c >> 8 & 0xFF, c & 0xFF)) < 0x80;
	}

	int m3Pixel(int x, int y) {
		try (var bitmap = new io.github.humbleui.skija.Bitmap()) {
			bitmap.allocN32Pixels(1, 1);
			real.readPixels(bitmap, x, y);
			return bitmap.getColor(0, 0) & 0xFFFFFF;
		}
	}

	@Test
	public void testTabStopIsStock() {
		int flags = SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT;
		gc.drawText("i\tX", 0, 0, flags);
		stock.drawText("i\tX", 0, 0, flags);
		ImageData d = image.getImageData();
		int afterI = stock.textExtent("i ").x;
		int m3 = m3InkFrom(afterI), ref = stockInkFrom(d, afterI);
		assertTrue(ref > 0 && Math.abs(m3 - ref) <= 3, "ink after the tab: m3 " + m3 + " stock " + ref);
	}

	/* L14 ONE_TEXT_SHAPER v1 (raster): CLabel-like text (emoji, CJK, Arabic, mnemonic, tab) is the stock DrawText exactly */
	@Test
	public void testRasterDrawTextIsStockExactly() {
		String text = "emoji " + new String(Character.toChars(0x1F600)) + " CJK 漢字 Arabic مرحبا a&&b	tab";
		int flags = SWT.DRAW_MNEMONIC | SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT | SWT.DRAW_DELIMITER;
		gc.drawText(text, 2, 2, flags);
		stock.drawText(text, 2, 2, flags);
		ImageData d = image.getImageData();
		long differ = IntStream.range(0, H).flatMap(y -> IntStream.range(0, W).filter(x -> {
			var rgb = d.palette.getRGB(d.getPixel(x, y));
			return (rgb.red << 16 | rgb.green << 8 | rgb.blue) != m3Pixel(x, y);
		})).count();
		assertEquals(0, differ, "pixels differing from the stock drawText");
	}
}
