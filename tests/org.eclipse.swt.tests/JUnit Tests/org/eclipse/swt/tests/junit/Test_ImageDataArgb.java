/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.render.ImageDataArgb;
import org.junit.jupiter.api.Test;

/**
 * Differential: ImageDataArgb's rows equal, bit for bit, the per-pixel ImageData and PaletteData calls they replace
 * (getPixel, getAlpha, PaletteData.getRGB and getPixel(RGB)), over direct and indexed palettes of several depths, with
 * alpha data, a global alpha or a transparent pixel.
 */
public class Test_ImageDataArgb {

	private static final int W = 37, H = 11;

	private static ImageData[] images() {
		Random random = new Random(20261118);
		RGB[] colors256 = new RGB[256], colors16 = new RGB[16];
		for (int i = 0; i < 256; i++) colors256[i] = new RGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
		System.arraycopy(colors256, 0, colors16, 0, 16);
		ImageData[] images = {
				new ImageData(W, H, 24, new PaletteData(0xFF, 0xFF00, 0xFF0000)),
				new ImageData(W, H, 32, new PaletteData(0xFF00, 0xFF0000, 0xFF000000)),
				new ImageData(W, H, 16, new PaletteData(0xF800, 0x7E0, 0x1F)),
				new ImageData(W, H, 8, new PaletteData(colors256)),
				new ImageData(W, H, 4, new PaletteData(colors16)),
				new ImageData(W, H, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF)), };
		for (ImageData data : images) {
			int limit = data.palette.isDirect ? -1 : data.palette.colors.length;
			for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) data.setPixel(x, y, limit < 0 ? random.nextInt(1 << Math.min(30, data.depth)) : random.nextInt(limit));
		}
		images[0].alphaData = new byte[W * H];
		random.nextBytes(images[0].alphaData);
		images[2].alpha = 77;
		images[3].transparentPixel = images[3].getPixel(3, 4);
		images[4].alphaData = new byte[W * H];
		random.nextBytes(images[4].alphaData);
		images[4].transparentPixel = 5;
		return images;
	}

	/* the per-pixel code ImageDataArgb replaces in LayeredViewport */
	private static int reference(ImageData data, int x, int y) {
		int pixel = data.getPixel(x, y);
		int alpha = data.alphaData != null ? data.getAlpha(x, y) : data.alpha >= 0 ? data.alpha : 255;
		if (data.transparentPixel >= 0 && pixel == data.transparentPixel) alpha = 0;
		RGB s = data.palette.getRGB(pixel);
		return alpha << 24 | (s.red * alpha + 127) / 255 << 16 | (s.green * alpha + 127) / 255 << 8 | (s.blue * alpha + 127) / 255;
	}

	@Test
	public void premultipliedRowsEqualThePerPixelCalls() {
		for (ImageData data : images()) {
			ImageDataArgb rows = new ImageDataArgb(data);
			int[] out = new int[W + 3];
			for (int y = 0; y < H; y++) {
				rows.premultipliedRow(y, out, 3);
				for (int x = 0; x < W; x++) assertEquals(reference(data, x, y), out[3 + x], "depth " + data.depth + " at " + x + "," + y);
			}
		}
	}

	@Test
	public void opaqueRowsAndPixelsEqualThePaletteCalls() {
		for (ImageData data : images()) {
			ImageDataArgb rows = new ImageDataArgb(data);
			int[] out = new int[W];
			for (int y = 0; y < H; y++) {
				rows.opaqueRow(y, out, 0);
				for (int x = 0; x < W; x++) {
					RGB rgb = data.palette.getRGB(data.getPixel(x, y));
					assertEquals(0xFF000000 | rgb.red << 16 | rgb.green << 8 | rgb.blue, out[x]);
					assertEquals(data.palette.getPixel(rgb), rows.pixel(out[x]), "back to the pixel");
				}
			}
		}
	}

	@Test
	public void writtenRowsEqualSetPixelWithGetPixel() {
		Random random = new Random(7);
		for (ImageData data : images()) {
			ImageData copy = (ImageData) data.clone();
			ImageDataArgb rows = new ImageDataArgb(data);
			int[] argb = new int[W];
			for (int y = 0; y < H; y++) {
				for (int x = 0; x < W; x++) {
					// a direct palette takes any colour; an indexed one only its own
					RGB rgb = data.palette.isDirect ? new RGB(random.nextInt(256), random.nextInt(256), random.nextInt(256))
							: data.palette.colors[random.nextInt(data.palette.colors.length)];
					argb[x] = random.nextInt() << 24 | rgb.red << 16 | rgb.green << 8 | rgb.blue;
					copy.setPixel(x, y, data.palette.getPixel(rgb));
				}
				rows.setOpaqueRow(y, argb, 0);
			}
			assertArrayEquals(copy.data, data.data, "depth " + data.depth);
		}
	}

	@Test
	public void anIndexedPixelOutsideThePaletteIsRefused() {
		ImageDataArgb rows = new ImageDataArgb(images()[4]);
		assertThrows(IllegalArgumentException.class, () -> rows.rgb(16));
		assertThrows(IllegalArgumentException.class, () -> rows.rgb(-1));
	}

	@Test
	public void signed32BitRowsEqualThePaletteAndPerPixelCalls() {
		int[] source = {Integer.MIN_VALUE, 0xC0123400, 0xFF345600, -1, Integer.MAX_VALUE, 0x407F8000, 0};
		for (PaletteData palette : new PaletteData[] {
				new PaletteData(0xFF000000, 0xFF0000, 0xFF00),
				new PaletteData(0xFF00, 0xFF000000, 0xFF0000),
				new PaletteData(0xFF00, 0xFF0000, 0xFF000000)}) {
			ImageData data = new ImageData(source.length, 2, 32, palette);
			data.setPixels(0, 0, source.length, source, 0);
			data.alphaData = new byte[source.length * 2];
			System.arraycopy(new byte[] {(byte) 255, 127, 1, 0, (byte) 200, 50, (byte) 254},
					0, data.alphaData, 0, source.length);
			ImageDataArgb rows = new ImageDataArgb(data);
			int[] actual = new int[source.length + 2];
			java.util.Arrays.fill(actual, 0x13579BDF);
			rows.premultipliedRow(0, actual, 1);
			assertEquals(0x13579BDF, actual[0]);
			assertEquals(0x13579BDF, actual[actual.length - 1]);
			for (int x = 0; x < source.length; x++) {
				assertEquals(source[x], data.getPixel(x, 0), "the fixture preserves the signed high bits");
				assertEquals(reference(data, x, 0), actual[x + 1], "premultiplied signed pixel " + x);
			}

			rows.opaqueRow(0, actual, 1);
			ImageData expected = (ImageData) data.clone();
			for (int x = 0; x < source.length; x++) {
				RGB color = palette.getRGB(source[x]);
				assertEquals(0xFF000000 | color.red << 16 | color.green << 8 | color.blue, actual[x + 1]);
				assertEquals(palette.getPixel(color), rows.pixel(actual[x + 1]));
				expected.setPixel(x, 1, palette.getPixel(color));
			}
			rows.setOpaqueRow(1, actual, 1);
			assertArrayEquals(expected.data, data.data, "signed high-bit row writes preserve the original palette mapping");
		}
	}
}
