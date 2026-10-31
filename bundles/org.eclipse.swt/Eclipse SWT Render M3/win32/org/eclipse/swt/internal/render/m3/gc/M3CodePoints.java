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
package org.eclipse.swt.internal.render.m3.gc;

import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import org.eclipse.swt.graphics.Font;

//FLLDCIM: L14 SURROGATE_SAFE v1 + TAB_STOPS v1 — code points, never UTF-16 units, reach Skia; tabs expanded by GDI advance
/**
 * Code-point safety and tab expansion of GC text before it reaches Skia (text leaf L14, SURROGATE_SAFE v1, TAB_STOPS v1).
 * A lone surrogate (half of a supplementary character cut by a caller) becomes U+FFFD, the replacement glyph GDI shows,
 * instead of reaching Skia's text measurement (Skija #109 segfaulted there). Tabs are expanded segment by segment: each
 * run between tabs is measured as the stock GC measures it ({@link M3GdiText}), the pen moves to the next DT_EXPANDTABS
 * stop (8 average character widths), and the gap is filled with the nearest whole number of spaces (no per-character
 * width truncation).
 */
final class M3CodePoints {

	private static final IntUnaryOperator NO_LONE_SURROGATE = cp -> Character.isSurrogate((char) cp) && cp <= 0xFFFF ? 0xFFFD : cp;

	private M3CodePoints() {
	}

	/** the text with every lone surrogate replaced by U+FFFD (pairs kept) */
	static String safe(String text) {
		if (text.chars().noneMatch(c -> Character.isSurrogate((char) c))) {
			return text;
		}
		return new String(text.codePoints().map(NO_LONE_SURROGATE).toArray(), 0, text.codePointCount(0, text.length()));
	}

	/** tabs expanded to spaces at the stock stops, measured with GDI at the zoom */
	static String expandTabs(String text, Font font, int zoom) {
		final int stop = M3GdiText.tabStop(font, zoom);
		final int space = Math.max(1, M3GdiText.stringExtent(font, zoom, " ").x);
		final String[] segments = text.split("\t", -1);
		final StringBuilder out = new StringBuilder(segments[0]);
		final int[] x = { M3GdiText.stringExtent(font, zoom, segments[0]).x };
		IntStream.range(1, segments.length).forEach(i -> {
			final int next = (x[0] / stop + 1) * stop;
			out.append(" ".repeat(Math.max(1, Math.round((next - x[0]) / (float) space)))).append(segments[i]);
			x[0] = next + M3GdiText.stringExtent(font, zoom, segments[i]).x;
		});
		return out.toString();
	}
}
