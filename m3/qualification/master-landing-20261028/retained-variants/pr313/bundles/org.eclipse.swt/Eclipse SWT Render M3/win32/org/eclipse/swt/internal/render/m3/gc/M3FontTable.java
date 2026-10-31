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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (text leaf L13/L14)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.LOGFONT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.TEXTMETRIC;

import jdk.m3.graphics.M3Font;
import jdk.m3.graphics.M3FontEdging;
import jdk.m3.graphics.M3FontHinting;
import jdk.m3.graphics.M3FontMgr;
import jdk.m3.graphics.M3FontSlant;
import jdk.m3.graphics.M3FontStyle;
import jdk.m3.graphics.M3Typeface;

//FLLDCIM: L14 FONT_TABLE v1 (GC half) — one LOGFONT-keyed Skija font table; donor pattern GM/M3PlaneBackend.defineFont
//FLLDCIM: MRL — M3_GRAPHICS_PROSE v1: the table holds jdk.m3.graphics fonts (M3Font, absorbed Skija 0.143.18); the L14 anchor above keeps its words
/**
 * One LOGFONT-keyed table of jdk.m3.graphics fonts for M3 text (text leaf L13 GLYPH_RUN_DRAW, L14 FONT_TABLE v1, GC half). The key is
 * what GDI resolved for the platform font: face name, pixel height on the device (lfHeight, or the cell height minus the
 * internal leading when lfHeight is positive, as <code>GCPlanes.defineFont</code>), weight, italic, plus the edging the GC's
 * text anti-aliasing asks for. A glyph id from Uniscribe indexes the same font file, so a run shaped by GDI is drawn by
 * Skia with this font's typeface. Bounded (least recently used font closed beyond {@link #CAPACITY}); display thread only.
 * The drawn-row half (M3RowCanvas's M3TextLine LRU) is a follow-up of the row owner.
 */
public final class M3FontTable {

	static final int CAPACITY = 128;

	private static final Map<String, M3Font> FONTS = new LinkedHashMap<>(32, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, M3Font> eldest) {
			final boolean drop = size() > CAPACITY;
			if (drop && eldest.getValue() != null) {
				eldest.getValue().close();
			}
			return drop;
		}
	};

	private M3FontTable() {
	}

	/** the jdk.m3.graphics font of a platform font handle (HFONT) for the GC text anti-aliasing (SWT.DEFAULT, ON, OFF); null if unmatched */
	public static M3Font font(long hFont, int textAntialias) {
		final LOGFONT logFont = new LOGFONT();
		OS.GetObject(hFont, LOGFONT.sizeof, logFont);
		final int pixels = pixelHeight(hFont, logFont);
		final String face = face(logFont);
		final String key = face + '|' + pixels + '|' + logFont.lfWeight + '|' + logFont.lfItalic + '|' + textAntialias;
		return FONTS.computeIfAbsent(key, k -> create(face, pixels, logFont, textAntialias));
	}

	/**
	 * L14 FONT_TABLE v1 / SKC-G02: the font of the same size and edging whose typeface has <code>codePoint</code> (Skia's
	 * system fallback, as GDI font linking draws a character run whose linked face lacks it); the font itself when no face has it
	 */
	public static M3Font fallback(M3Font font, int codePoint) {
		try (M3Typeface primary = font.getTypeface()) {
			final M3Typeface typeface = M3FontMgr.getDefault().matchFamilyStyleCharacter(primary.getFamilyName(),
					primary.getFontStyle(), null, codePoint);
			return typeface == null ? font : derived(typeface, font);
		}
	}

	private static M3Font derived(M3Typeface typeface, M3Font font) {
		final String key = typeface.getFamilyName() + '|' + font.getSize() + '|' + font.getEdging() + "|fallback";
		final M3Font cached = FONTS.get(key);
		if (cached != null) {
			typeface.close();
			return cached;
		}
		final M3Font derived = settings(new M3Font(typeface, font.getSize()), font.getEdging());
		FONTS.put(key, derived);
		return derived;
	}

	/** fonts in the table (bounded by {@link #CAPACITY}) */
	public static int size() {
		return FONTS.size();
	}

	static String face(LOGFONT logFont) {
		final char[] name = logFont.lfFaceName;
		return new String(name, 0, IntStream.range(0, name.length).filter(i -> name[i] == 0).findFirst().orElse(name.length));
	}

	/* the em height in pixels: -lfHeight, or the cell height minus internal leading (GDI's mapping of a positive height) */
	static int pixelHeight(long hFont, LOGFONT logFont) {
		if (logFont.lfHeight < 0) {
			return -logFont.lfHeight;
		}
		final long hdc = OS.CreateCompatibleDC(0);
		final long previous = OS.SelectObject(hdc, hFont);
		final TEXTMETRIC metric = new TEXTMETRIC();
		OS.GetTextMetrics(hdc, metric);
		OS.SelectObject(hdc, previous);
		OS.DeleteDC(hdc);
		return metric.tmHeight - metric.tmInternalLeading;
	}

	private static M3Font create(String face, int pixels, LOGFONT logFont, int textAntialias) {
		final M3FontStyle style = new M3FontStyle(logFont.lfWeight == 0 ? 400 : logFont.lfWeight, 5,
				logFont.lfItalic != 0 ? M3FontSlant.ITALIC : M3FontSlant.UPRIGHT);
		final M3Typeface typeface = M3FontMgr.getDefault().matchFamilyStyle(face, style);
		if (typeface == null) {
			return null;
		}
		return settings(new M3Font(typeface, pixels), edging(textAntialias));
	}

	/* GDI-like glyph settings (pattern of M3PlaneBackend.defineFont): hinted, whole-pixel glyph origins, snapped baseline */
	private static M3Font settings(M3Font font, M3FontEdging edging) {
		font.setEdging(edging);
		font.setHinting(M3FontHinting.NORMAL);
		font.setSubpixel(false);
		font.setBaselineSnapped(true);
		return font;
	}

	/* stock rule: DEFAULT follows the system (ClearType: subpixel), ON grey anti-aliasing, OFF aliased */
	static M3FontEdging edging(int textAntialias) {
		return textAntialias == SWT.OFF ? M3FontEdging.ALIAS
				: textAntialias == SWT.ON ? M3FontEdging.ANTI_ALIAS : M3FontEdging.SUBPIXEL_ANTI_ALIAS;
	}
}
