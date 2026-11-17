/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *     SAP SE and others - initial API and implementation
 *     Synexia (Hitesh Soliwal) - adoption into the M3 fork, 2026
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext.raster;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

import org.eclipse.swt.internal.maya.ColorAlphaType;
import org.eclipse.swt.internal.maya.ColorType;
import org.eclipse.swt.internal.maya.ImageInfo;

public class SwtToRasterImageConverter {
	public static IRasterImage convertSWTImageToMayaImage(Image swtImage, int zoom, IRasterResources resources) {
		IRasterImage img = null;
		final ImageData imageData = swtImage.getImageData(zoom);
		img = convertSWTImageToMayaImage(imageData);
		return img;
	}

	public static IRasterImage convertSWTImageToMayaImage(ImageData imageData) {
		final int width = imageData.width;
		final int height = imageData.height;
		ColorType colType = getColorType(imageData);
		if (colType.equals(ColorType.UNKNOWN) || imageData.alphaData != null || colType.equals(ColorType.ALPHA_8)) {
			return nativeImage(RGBAEncoder.encode(imageData), width, height, false);
		}
		// M3 (efficiency): the 32-bit layouts in the surface's own format, see nativeImage
		if (colType.equals(ColorType.RGBA_8888) || colType.equals(ColorType.BGRA_8888)) {
			return nativeImage(imageData.data, width, height, colType.equals(ColorType.BGRA_8888));
		}
		final ImageInfo imageInfo = new ImageInfo(width, height, colType, ColorAlphaType.UNPREMUL);
		return new RasterImageAdapter(
				org.eclipse.swt.internal.maya.Image.makeRasterFromBytes(imageInfo, imageData.data, imageData.width * 4));
	}

	/*
	 * M3 (efficiency): an image in the format of the surfaces it is drawn on (BGRA, premultiplied; opaque when every alpha
	 * is 255). Skia blits such an image (about 0.6 microseconds for 16x16 on win32) where any other format makes it build
	 * a converting pipeline on every draw (23 to 32 microseconds). The pixels and their alpha are those of the unpremultiplied
	 * source, premultiplied once with Skia's rounding (SkMulDiv255Round).
	 */
	static IRasterImage nativeImage(byte[] source, int width, int height, boolean bgra) {
		final int n = width * height * 4;
		final byte[] pixels = new byte[n];
		boolean opaque = true;
		for (int i = 0; i < n; i += 4) {
			final int a = source[i + 3] & 0xFF;
			final int r = source[bgra ? i + 2 : i] & 0xFF, g = source[i + 1] & 0xFF, b = source[bgra ? i : i + 2] & 0xFF;
			if (a == 255) {
				pixels[i] = (byte) b;
				pixels[i + 1] = (byte) g;
				pixels[i + 2] = (byte) r;
			} else {
				opaque = false;
				pixels[i] = (byte) premultiply(b, a);
				pixels[i + 1] = (byte) premultiply(g, a);
				pixels[i + 2] = (byte) premultiply(r, a);
			}
			pixels[i + 3] = (byte) a;
		}
		final ImageInfo info = new ImageInfo(width, height, ColorType.BGRA_8888, opaque ? ColorAlphaType.OPAQUE : ColorAlphaType.PREMUL);
		return new RasterImageAdapter(org.eclipse.swt.internal.maya.Image.makeRasterFromBytes(info, pixels, width * 4));
	}

	/* c * a / 255, rounded as Skia rounds (SkMulDiv255Round) */
	static int premultiply(int c, int a) {
		final int product = c * a + 128;
		return (product + (product >> 8)) >> 8;
	}

	public static ColorType getColorType(ImageData imageData) {
		final PaletteData palette = imageData.palette;
		if (imageData.getTransparencyType() == SWT.TRANSPARENCY_MASK) {
			return ColorType.UNKNOWN;
		}
		if (palette.isDirect) {
			final int redMask = palette.redMask;
			final int greenMask = palette.greenMask;
			final int blueMask = palette.blueMask;
			if (redMask == 0xFF0000 && greenMask == 0x00FF00 && blueMask == 0x0000FF) {
				return ColorType.UNKNOWN;
			}
			if (redMask == 0xFF000000 && greenMask == 0x00FF0000 && blueMask == 0x0000FF00) {
				return ColorType.RGBA_8888;
			}
			if (redMask == 0xF800 && greenMask == 0x07E0 && blueMask == 0x001F) {
				return ColorType.RGB_565;
			}
			if (redMask == 0xF000 && greenMask == 0x0F00 && blueMask == 0x00F0) {
				return ColorType.ARGB_4444;
			}
			if (redMask == 0x0000FF00 && greenMask == 0x00FF0000 && blueMask == 0xFF000000) {
				return ColorType.BGRA_8888;
			}
			if (redMask == 0x3FF00000 && greenMask == 0x000FFC00 && blueMask == 0x000003FF) {
				return ColorType.RGBA_1010102;
			}
			if (redMask == 0x000003FF && greenMask == 0x000FFC00 && blueMask == 0x3FF00000) {
				return ColorType.BGRA_1010102;
			}
			if (redMask == 0xFF && greenMask == 0xFF00 && blueMask == 0xFF0000) {
				return ColorType.UNKNOWN;
			}
		} else {
			if (imageData.depth == 8 && palette.colors != null && palette.colors.length <= 256) {
				return ColorType.ALPHA_8;
			}
			switch (imageData.depth) {
			case 16:
				return ColorType.ARGB_4444;
			case 24:
				return ColorType.RGB_888X;
			case 32:
				return ColorType.RGBA_8888;
			default:
				break;
			}
		}
		return ColorType.UNKNOWN;
	}
}