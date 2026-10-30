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
package org.eclipse.swt.internal.render.m3.gc;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ImageInfo;

public class SwtToM3ImageConverter {
	public static IM3Image convertSWTImageToM3Image(Image swtImage, int zoom, IM3Resources resources) {
		IM3Image img = null;
		final ImageData imageData = swtImage.getImageData(zoom);
		img = convertSWTImageToM3Image(imageData);
		return img;
	}

	public static IM3Image convertSWTImageToM3Image(ImageData imageData) {
		final int width = imageData.width;
		final int height = imageData.height;
		ColorType colType = getColorType(imageData);
		if (colType.equals(ColorType.UNKNOWN) || imageData.alphaData != null || colType.equals(ColorType.ALPHA_8)) {
			final byte[] bytes = RGBAEncoder.encode(imageData);
			colType = ColorType.RGBA_8888;
			final ImageInfo imageInfo = new ImageInfo(width, height, colType, ColorAlphaType.UNPREMUL);
			return new M3ImageAdapter(
					io.github.humbleui.skija.Image.makeRasterFromBytes(imageInfo, bytes, imageData.width * 4));
		}
		final ImageInfo imageInfo = new ImageInfo(width, height, colType, ColorAlphaType.UNPREMUL);
		return new M3ImageAdapter(
				io.github.humbleui.skija.Image.makeRasterFromBytes(imageInfo, imageData.data, imageData.width * 4));
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