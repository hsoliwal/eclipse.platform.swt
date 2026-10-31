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

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontSlant;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

//FLLDCIM: L14 FONT_TABLE v1 (GC half) — one LOGFONT-keyed Skija font table; donor pattern GM/M3PlaneBackend.defineFont
/**
 * One LOGFONT-keyed table of Skija fonts for M3 text (text leaf L13 GLYPH_RUN_DRAW, L14 FONT_TABLE v1, GC half). The key is
 * what GDI resolved for the platform font: face name, pixel height on the device (lfHeight, or the cell height minus the
 * internal leading when lfHeight is positive, as <code>GCPlanes.defineFont</code>), weight, italic, plus the edging the GC's
 * text anti-aliasing asks for. A glyph id from Uniscribe indexes the same font file, so a run shaped by GDI is drawn by
 * Skia with this font's typeface. Bounded (least recently used font closed beyond {@link #CAPACITY}); display thread only.
 * The drawn-row half (M3RowCanvas's TextLine LRU) is a follow-up of the row owner.
 */
public final class M3FontTable {

	static final int CAPACITY = 128;

	private static final Map<String, Font> FONTS = new LinkedHashMap<>(32, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Font> eldest) {
			final boolean drop = size() > CAPACITY;
			if (drop && eldest.getValue() != null) {
				eldest.getValue().close();
			}
			return drop;
		}
	};

	private M3FontTable() {
	}

	/** the Skija font of a platform font handle (HFONT) for the GC text anti-aliasing (SWT.DEFAULT, ON, OFF); null if unmatched */
	public static Font font(long hFont, int textAntialias) {
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
	public static Font fallback(Font font, int codePoint) {
		try (Typeface primary = font.getTypeface()) {
			final Typeface typeface = FontMgr.getDefault().matchFamilyStyleCharacter(primary.getFamilyName(),
					primary.getFontStyle(), null, codePoint);
			return typeface == null ? font : derived(typeface, font);
		}
	}

	private static Font derived(Typeface typeface, Font font) {
		final String key = typeface.getFamilyName() + '|' + font.getSize() + '|' + font.getEdging() + "|fallback";
		final Font cached = FONTS.get(key);
		if (cached != null) {
			typeface.close();
			return cached;
		}
		final Font derived = settings(new Font(typeface, font.getSize()), font.getEdging());
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

	private static Font create(String face, int pixels, LOGFONT logFont, int textAntialias) {
		final FontStyle style = new FontStyle(logFont.lfWeight == 0 ? 400 : logFont.lfWeight, 5,
				logFont.lfItalic != 0 ? FontSlant.ITALIC : FontSlant.UPRIGHT);
		final Typeface typeface = FontMgr.getDefault().matchFamilyStyle(face, style);
		if (typeface == null) {
			return null;
		}
		return settings(new Font(typeface, pixels), edging(textAntialias));
	}

	/* GDI-like glyph settings (pattern of M3PlaneBackend.defineFont): hinted, whole-pixel glyph origins, snapped baseline */
	private static Font settings(Font font, FontEdging edging) {
		font.setEdging(edging);
		font.setHinting(FontHinting.NORMAL);
		font.setSubpixel(false);
		font.setBaselineSnapped(true);
		return font;
	}

	/* stock rule: DEFAULT follows the system (ClearType: subpixel), ON grey anti-aliasing, OFF aliased */
	static FontEdging edging(int textAntialias) {
		return textAntialias == SWT.OFF ? FontEdging.ALIAS
				: textAntialias == SWT.ON ? FontEdging.ANTI_ALIAS : FontEdging.SUBPIXEL_ANTI_ALIAS;
	}
}
