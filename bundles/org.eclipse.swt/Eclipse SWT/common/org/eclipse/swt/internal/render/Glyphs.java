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
package org.eclipse.swt.internal.render;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.swt.graphics.*;

/**
 * The built-in vector glyph set of the drawn modes (docs/m3/RENDER_MODES.md, vector glyphs, leaf SV2): SVG sources in
 * <code>org/eclipse/swt/internal/render/glyphs</code>, each drawn with <code>currentColor</code>, which is resolved to
 * a theme colour before the source reaches SWT's SVG rasterizer (the FlatSVGIcon colour-filter pattern). A
 * {@link GlyphCache} over {@link #rasterizer} keeps one raster per (glyph, size, zoom, theme generation);
 * {@link #tinted} makes the per-theme source once. Without an SVG rasterizer on the class path (the
 * <code>org.eclipse.swt.svg</code> fragment) {@link #rasterizer} answers null and the looks draw their own glyphs.
 */
public final class Glyphs {
	/** Glyph ids: the expander closed and open, the check box off, on and mixed, the sort indicators. */
	public static final int CHEVRON_RIGHT = 0, CHEVRON_DOWN = 1, CHECK_OFF = 2, CHECK_ON = 3, CHECK_MIXED = 4, SORT_ASCENDING = 5, SORT_DESCENDING = 6;
	/** The glyph ids, in order. */
	public static final int COUNT = 7;
	private static final String [] NAMES = {"chevron-right", "chevron-down", "check-off", "check-on", "check-mixed", "sort-ascending", "sort-descending"};
	private static final Logger LOG = Logger.getLogger (Glyphs.class.getName ());
	private static final String [] SOURCES = new String [COUNT];

	private Glyphs () {
	}

	/** The name of a glyph id (its resource without the extension), or null for an unknown id. */
	public static String name (int glyph) {
		return glyph >= 0 && glyph < COUNT ? NAMES [glyph] : null;
	}

	/** The SVG source of a glyph with <code>currentColor</code> unresolved, read once from the resources; null for an unknown id or a missing resource. */
	public static synchronized String source (int glyph) {
		if (glyph < 0 || glyph >= COUNT) return null;
		if (SOURCES [glyph] == null) {
			try (InputStream in = Glyphs.class.getResourceAsStream ("glyphs/" + NAMES [glyph] + ".svg")) {
				SOURCES [glyph] = in == null ? "" : new String (in.readAllBytes (), StandardCharsets.UTF_8);
			} catch (IOException e) {
				LOG.log (Level.FINE, e, () -> "glyph " + NAMES [glyph] + " cannot be read");
				SOURCES [glyph] = "";
			}
		}
		return SOURCES [glyph].isEmpty () ? null : SOURCES [glyph];
	}

	/** The SVG source of a glyph tinted with a colour (<code>0xRRGGBB</code> for <code>currentColor</code>), or null. */
	public static String tinted (int glyph, int rgb) {
		String source = source (glyph);
		return source == null ? null : source.replace ("currentColor", String.format (Locale.ROOT, "#%06x", rgb & 0xFFFFFF));
	}

	/**
	 * A rasterizer of the built-in glyphs through SWT's SVG support, tinting each glyph with the colour the function
	 * answers for its id (<code>0xRRGGBB</code>); null when no SVG rasterizer is installed.
	 */
	public static GlyphCache.Rasterizer rasterizer (java.util.function.IntUnaryOperator tint) {
		GlyphCache.Rasterizer svg = GlyphCache.svg (glyph -> {
			String source = tinted (glyph, tint.applyAsInt (glyph));
			return source == null ? null : new ByteArrayInputStream (source.getBytes (StandardCharsets.UTF_8));
		});
		if (svg == null && LOG.isLoggable (Level.FINE)) LOG.fine ("no SVG rasterizer (org.eclipse.swt.svg) on the class path: the looks draw their own glyphs");
		return svg;
	}

	/** True when an image has both opaque and transparent pixels: a rasterized glyph, not a filled box. */
	public static boolean shaped (ImageData image) {
		if (image == null || image.alphaData == null) return false;
		boolean opaque = false, clear = false;
		for (byte a : image.alphaData) {
			if (a == -1) opaque = true; else if (a == 0) clear = true;
			if (opaque && clear) return true;
		}
		return false;
	}
}
