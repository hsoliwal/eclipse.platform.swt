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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (text leaf L13)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;

import jdk.m3.graphics.M3Bitmap;
import jdk.m3.graphics.M3ColorAlphaType;
import jdk.m3.graphics.M3ColorType;
import jdk.m3.graphics.M3ImageInfo;

//FLLDCIM: L13 TEXT_FALLBACK_SAFE v1 — pattern M3-TEXT-FALLBACK v1; census GF27 SC-R01 SWS-R01 SWS-V02 (skia.md:483 M12)
/**
 * A <code>TextLayout</code> the glyph-run route does not take, drawn on an M3 GC through the stock GC without a white box
 * (text leaf L13, atom TEXT_FALLBACK_SAFE v1). The donor (swt-initiative31/skija-canvas 9bcb404c42, skia.md:483 M12) drew
 * the layout into an opaque white <code>Image</code> with a fresh GC (black text, system font) and blitted it, which painted
 * white boxes over StyledText line backgrounds and the dark theme and dropped the GC foreground.
 * <p>
 * Here the stock GC rasterises the layout twice, on opaque black and on opaque white, with the M3 GC's font, foreground,
 * background and text anti-aliasing carried onto it. Per pixel and channel the coverage is <code>255 - (white - black)</code>
 * and the black pass is already the premultiplied colour, so the composite is a 32-bit premultiplied transparent raster: what
 * lies under the layout shows through, opaque run and selection backgrounds stay opaque, and the GC alpha is applied once,
 * by the composite (carrying it onto the native GC as well would square it). Every use is counted ({@link #count()}).
 * </p>
 */
final class M3TextLayoutFallback {

	private static final AtomicLong COUNT = new AtomicLong();
	private static final AtomicLong UNDERLAID = new AtomicLong();

	private M3TextLayoutFallback() {
	}

	/** layouts drawn through this fallback (two passes or underlay) since load (RUN_COVERAGE counts what glyph runs leave) */
	static long count() {
		return COUNT.get() + UNDERLAID.get();
	}

	/** draws <code>call</code> on <code>gc</code>: two stock passes, one premultiplied composite */
	static void draw(M3ExternalGC gc, M3TextLayoutCall call) {
		final Rectangle box = box(gc, call);
		if (box.width <= 0 || box.height <= 0) {
			return;
		}
		final int zoom = gc.getScaler().getNativeZoom();
		if (gc.getAlpha() == 255 && underlay(gc, call, box, zoom)) {
			UNDERLAID.incrementAndGet();
			return;
		}
		final ImageData black = pass(gc, call, box, SWT.COLOR_BLACK, zoom);
		final ImageData white = pass(gc, call, box, SWT.COLOR_WHITE, zoom);
		COUNT.incrementAndGet();
		gc.drawLayoutRaster(premultiplied(black, white), black.width, black.height,
				new Rectangle(call.x() + box.x, call.y() + box.y, box.width, box.height));
	}

	/** layouts drawn by GDI over the surface pixels (v2, raster surfaces) since load */
	static long underlaid() {
		return UNDERLAID.get();
	}

	//FLLDCIM: L13 TEXT_FALLBACK_SAFE v2 (GDI_UNDERLAY) — exact stock ClearType on raster surfaces: GDI draws over the real pixels
	/* v2: the stock GC draws the layout over the surface pixels under it (raster, plain translation); composited opaque */
	static boolean underlay(M3ExternalGC gc, M3TextLayoutCall call, Rectangle box, int zoom) {
		final Rectangle dest = new Rectangle(call.x() + box.x, call.y() + box.y, box.width, box.height);
		final ImageData under = gc.readSurface(dest);
		if (under == null) {
			return false;
		}
		final Image image = new Image(gc.getDevice(), under);
		final GC stock = new GC(image);
		try {
			carry(gc, stock);
			call.drawOn(stock, -box.x, -box.y);
			final ImageData drawn = image.getImageData(zoom);
			gc.drawLayoutRaster(opaque(drawn), drawn.width, drawn.height, dest);
			return true;
		} finally {
			stock.dispose();
			image.dispose();
		}
	}

	/* raster surface pixels at (x, y) as 32-bit BGRA ImageData; null when not CPU-readable */
	static ImageData read(IM3Surface surface, int x, int y, int width, int height) {
		try (M3Bitmap bitmap = new M3Bitmap()) {
			bitmap.allocPixels(new M3ImageInfo(width, height, M3ColorType.BGRA_8888, M3ColorAlphaType.PREMUL));
			if (!surface.readRasterPixels(bitmap, x, y)) {
				return null;
			}
			return new ImageData(width, height, 32, new PaletteData(0xFF00, 0xFF0000, 0xFF000000), 4, bitmap.readPixels());
		}
	}

	/* BGRA opaque pixels of a stock drawing */
	static byte[] opaque(ImageData drawn) {
		final int[] rgb = rgb(drawn);
		final byte[] out = new byte[rgb.length * 4];
		IntStream.range(0, rgb.length).forEach(i -> put(out, i, rgb[i], rgb[i]));
		return out;
	}

	/* the layout bounds plus room for overhang and delimiter selection; FULL_SELECTION runs to the clip's right edge */
	static Rectangle box(M3ExternalGC gc, M3TextLayoutCall call) {
		final TextLayout layout = call.layout();
		final Rectangle bounds = layout.getBounds();
		int right = bounds.x + bounds.width + layout.getLineBounds(0).height / 3 + 2;
		if ((call.flags() & SWT.FULL_SELECTION) != 0) {
			final Rectangle clip = gc.getClipping();
			right = Math.max(right, clip.x + clip.width - call.x());
		}
		final int left = Math.min(0, bounds.x);
		return new Rectangle(left, Math.min(0, bounds.y), right - left, bounds.y + bounds.height - Math.min(0, bounds.y));
	}

	private static ImageData pass(M3ExternalGC gc, M3TextLayoutCall call, Rectangle box, int base, int zoom) {
		final Device device = gc.getDevice();
		final Image image = new Image(device, box.width, box.height);
		final GC stock = new GC(image);
		try {
			stock.setBackground(device.getSystemColor(base));
			stock.fillRectangle(0, 0, box.width, box.height);
			carry(gc, stock);
			call.drawOn(stock, -box.x, -box.y);
			return image.getImageData(zoom);
		} finally {
			stock.dispose();
			image.dispose();
		}
	}

	/* the M3 GC state the stock TextLayout reads from its GC (default run colour and font, text anti-aliasing) */
	static void carry(M3ExternalGC gc, GC stock) {
		if (gc.getFont() != null) {
			stock.setFont(gc.getFont());
		}
		stock.setForeground(gc.getForeground());
		stock.setBackground(gc.getBackground());
		if (gc.getTextAntialias() != SWT.DEFAULT) {
			stock.setTextAntialias(gc.getTextAntialias());
		}
	}

	/** BGRA premultiplied pixels from the black and white passes (same size) */
	static byte[] premultiplied(ImageData black, ImageData white) {
		final int[] onBlack = rgb(black);
		final int[] onWhite = rgb(white);
		final byte[] out = new byte[onBlack.length * 4];
		IntStream.range(0, onBlack.length).forEach(i -> put(out, i, onBlack[i], onWhite[i]));
		return out;
	}

	/* one pixel: coverage = the largest channel coverage; colour channels never above it (premultiplied) */
	static void put(byte[] out, int i, int onBlack, int onWhite) {
		final int a = Math.max(coverage(onBlack, onWhite, 16), Math.max(coverage(onBlack, onWhite, 8), coverage(onBlack, onWhite, 0)));
		out[i * 4] = (byte) Math.min(a, onBlack & 0xFF);
		out[i * 4 + 1] = (byte) Math.min(a, onBlack >> 8 & 0xFF);
		out[i * 4 + 2] = (byte) Math.min(a, onBlack >> 16 & 0xFF);
		out[i * 4 + 3] = (byte) a;
	}

	static int coverage(int onBlack, int onWhite, int shift) {
		return 255 - Math.max(0, (onWhite >> shift & 0xFF) - (onBlack >> shift & 0xFF));
	}

	/* 0xRRGGBB per pixel, row-major, for direct and indexed palettes */
	static int[] rgb(ImageData data) {
		final int[] pixels = new int[data.width * data.height];
		IntStream.range(0, data.height).forEach(y -> data.getPixels(0, y, data.width, pixels, y * data.width));
		final PaletteData palette = data.palette;
		return IntStream.of(pixels).map(p -> palette.isDirect ? direct(palette, p) : indexed(palette, p)).toArray();
	}

	private static int direct(PaletteData palette, int pixel) {
		return channel(pixel, palette.redMask, palette.redShift) << 16 | channel(pixel, palette.greenMask, palette.greenShift) << 8
				| channel(pixel, palette.blueMask, palette.blueShift);
	}

	private static int channel(int pixel, int mask, int shift) {
		final int value = pixel & mask;
		return (shift < 0 ? value >>> -shift : value << shift) & 0xFF;
	}

	private static int indexed(PaletteData palette, int pixel) {
		final var rgb = palette.colors[pixel];
		return rgb.red << 16 | rgb.green << 8 | rgb.blue;
	}
}
