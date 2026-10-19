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
package org.eclipse.swt.internal.laf;

/**
 * OKLCH, the polar form of Oklab (docs/m3/look-and-feel/GENERATOR.md): lightness <code>L</code> 0 to 1, chroma
 * <code>C</code> (0 to about 0.4 in sRGB) and hue <code>h</code> in degrees. Lightness steps in OKLCH look even to the
 * eye, so the theme completer moves colours there (hue and chroma kept) to solve contrast and derive states. The
 * matrices are Bjorn Ottosson's ("A perceptual color space for image processing", 2020, public domain formulas);
 * colours outside sRGB lose chroma by bisection until they fit.
 */
public final class M3Oklch {
	private M3Oklch () {
	}

	/** The OKLCH coordinates <code>{L, C, h}</code> of an ARGB colour (alpha ignored). */
	public static double [] of (int argb) {
		double r = linear ((argb >> 16) & 0xFF), g = linear ((argb >> 8) & 0xFF), b = linear (argb & 0xFF);
		double l = Math.cbrt (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
		double m = Math.cbrt (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
		double s = Math.cbrt (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
		double lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
		double a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
		double bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
		double hue = Math.toDegrees (Math.atan2 (bb, a));
		return new double [] {lightness, Math.hypot (a, bb), hue < 0 ? hue + 360 : hue};
	}

	/** The opaque ARGB colour of OKLCH coordinates, its chroma reduced until it fits sRGB. */
	public static int argb (double lightness, double chroma, double hue) {
		double light = Math.max (0, Math.min (1, lightness));
		if (fits (light, chroma, hue)) return pack (light, chroma, hue);
		double low = 0, high = chroma;
		for (int i = 0; i < 24; i++) {
			double middle = (low + high) / 2;
			if (fits (light, middle, hue)) low = middle;
			else high = middle;
		}
		return pack (light, low, hue);
	}

	/** An ARGB colour with another OKLCH lightness (hue and chroma kept, alpha opaque). */
	public static int withLightness (int argb, double lightness) {
		double [] lch = of (argb);
		return argb (lightness, lch [1], lch [2]);
	}

	/** The OKLCH lightness of an ARGB colour. */
	public static double lightness (int argb) {
		return of (argb) [0];
	}

	private static boolean fits (double lightness, double chroma, double hue) {
		double [] rgb = rgb (lightness, chroma, hue);
		return rgb [0] >= -1e-9 && rgb [0] <= 1 + 1e-9 && rgb [1] >= -1e-9 && rgb [1] <= 1 + 1e-9 && rgb [2] >= -1e-9 && rgb [2] <= 1 + 1e-9;
	}

	/* linear sRGB of OKLCH coordinates (may lie outside 0..1) */
	private static double [] rgb (double lightness, double chroma, double hue) {
		double a = chroma * Math.cos (Math.toRadians (hue)), b = chroma * Math.sin (Math.toRadians (hue));
		double l = lightness + 0.3963377774 * a + 0.2158037573 * b;
		double m = lightness - 0.1055613458 * a - 0.0638541728 * b;
		double s = lightness - 0.0894841775 * a - 1.2914855480 * b;
		l = l * l * l;
		m = m * m * m;
		s = s * s * s;
		return new double [] {4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
			-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s};
	}

	private static int pack (double lightness, double chroma, double hue) {
		double [] rgb = rgb (lightness, chroma, hue);
		return 0xFF000000 | gamma (rgb [0]) << 16 | gamma (rgb [1]) << 8 | gamma (rgb [2]);
	}

	private static double linear (int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow ((c + 0.055) / 1.055, 2.4);
	}

	private static int gamma (double linear) {
		double c = Math.max (0, Math.min (1, linear));
		double encoded = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow (c, 1 / 2.4) - 0.055;
		return (int) Math.round (encoded * 255);
	}
}
