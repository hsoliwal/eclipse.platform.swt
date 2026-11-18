/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

/**
 * Rows of an {@link ImageData} to and from ARGB ints without an object per pixel: a direct palette through its masks
 * and shifts (the bit operations of {@link PaletteData#getRGB(int)} and {@link PaletteData#getPixel(RGB)}, bit for
 * bit), an indexed palette through a table of its colours made once. Whole rows move through
 * {@link ImageData#getPixels(int, int, int, int[], int)} and {@link ImageData#setPixels(int, int, int, int[], int)}
 * into scratch rows this converter owns, instead of a call and an {@link RGB} per pixel. Owns no native resource.
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ImageDataArgb {

	private final ImageData data;
	private final PaletteData palette;
	/* an indexed palette's colours as 0xRRGGBB; null for a direct palette */
	private final int[] table;
	private final int[] pixels;
	private final byte[] alphas;
	/* a direct palette's masks, and per channel how far right then left a masked pixel moves to 8 bits (one is 0) */
	private final int redMask, greenMask, blueMask, redRight, redLeft, greenRight, greenLeft, blueRight, blueLeft;

	/** A converter for the rows of {@code data}. */
	public ImageDataArgb(ImageData data) {
		this.data = data;
		this.palette = data.palette;
		if (palette.isDirect) {
			table = null;
		} else {
			RGB[] colors = palette.colors;
			table = new int[colors.length];
			for (int i = 0; i < colors.length; i++) table[i] = colors[i].red << 16 | colors[i].green << 8 | colors[i].blue;
		}
		pixels = new int[data.width];
		alphas = new byte[data.alphaData != null ? data.width : 0];
		redMask = palette.redMask;
		greenMask = palette.greenMask;
		blueMask = palette.blueMask;
		redRight = Math.max(0, -palette.redShift);
		redLeft = Math.max(0, palette.redShift);
		greenRight = Math.max(0, -palette.greenShift);
		greenLeft = Math.max(0, palette.greenShift);
		blueRight = Math.max(0, -palette.blueShift);
		blueLeft = Math.max(0, palette.blueShift);
	}

	/** A pixel's colour as 0xRRGGBB, as {@link PaletteData#getRGB(int)} gives it. */
	public int rgb(int pixel) {
		if (table != null) {
			if (pixel < 0 || pixel >= table.length) throw new IllegalArgumentException("pixel outside the palette");
			return table[pixel];
		}
		// shift < 0: right by -shift; else left by shift (one of the two amounts is 0): no branch per pixel
		return (pixel & redMask) >>> redRight << redLeft << 16 | (pixel & greenMask) >>> greenRight << greenLeft << 8
				| (pixel & blueMask) >>> blueRight << blueLeft;
	}

	/** The pixel of a colour (0xRRGGBB, any alpha bits ignored), as {@link PaletteData#getPixel(RGB)} gives it. */
	public int pixel(int rgb) {
		int r = rgb >>> 16 & 0xFF, g = rgb >>> 8 & 0xFF, b = rgb & 0xFF;
		if (table != null) return palette.getPixel(new RGB(r, g, b));
		PaletteData p = palette;
		return (p.redShift < 0 ? r << -p.redShift : r >>> p.redShift) & p.redMask
				| (p.greenShift < 0 ? g << -p.greenShift : g >>> p.greenShift) & p.greenMask
				| (p.blueShift < 0 ? b << -p.blueShift : b >>> p.blueShift) & p.blueMask;
	}

	/**
	 * Row {@code y} as premultiplied ARGB into {@code out} from {@code offset}: the alpha from the alpha data, else the
	 * global alpha, else 255; 0 for the transparent pixel; each colour times the alpha, rounded.
	 */
	public void premultipliedRow(int y, int[] out, int offset) {
		int w = data.width, transparent = data.transparentPixel;
		data.getPixels(0, y, w, pixels, 0);
		boolean perPixel = data.alphaData != null;
		if (perPixel) data.getAlphas(0, y, w, alphas, 0);
		int global = data.alpha >= 0 ? data.alpha : 255;
		boolean keyed = transparent >= 0; // a 32-bit pixel can be -1, the value of "no transparent pixel"
		for (int x = 0; x < w; x++) {
			int pixel = pixels[x], a = perPixel ? alphas[x] & 0xFF : global;
			if (keyed && pixel == transparent) a = 0;
			int c = rgb(pixel);
			out[offset + x] = a << 24 | mul255(c >>> 16 & 0xFF, a) << 16 | mul255(c >>> 8 & 0xFF, a) << 8 | mul255(c & 0xFF, a);
		}
	}

	/** Row {@code y}'s colours as opaque ARGB (0xFFRRGGBB) into {@code out} from {@code offset}. */
	public void opaqueRow(int y, int[] out, int offset) {
		int w = data.width;
		data.getPixels(0, y, w, pixels, 0);
		for (int x = 0; x < w; x++) out[offset + x] = 0xFF000000 | rgb(pixels[x]);
	}

	/** Row {@code y}'s pixels set to the colours of {@code argb} from {@code offset} (their alpha ignored). */
	public void setOpaqueRow(int y, int[] argb, int offset) {
		int w = data.width;
		for (int x = 0; x < w; x++) pixels[x] = pixel(argb[offset + x]);
		data.setPixels(0, y, w, pixels, 0);
	}

	/* c * a / 255 rounded to nearest: (c a + 127) / 255, exactly, for c and a in 0..255 */
	static int mul255(int c, int a) {
		return (c * a + 127) / 255;
	}
}
