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

import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.internal.canvasext.M3RunSink;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.TextBlob;
import io.github.humbleui.skija.TextBlobBuilder;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;

//FLLDCIM: L13 GLYPH_RUN_DRAW v1 — positioned TextBlob per Uniscribe run; pattern GM/M3PlaneBackend.glyphRun + Replay GLYPH_RUN
/**
 * The M3 GC as TextLayout's run sink (text leaf L13, GLYPH_RUN_DRAW v1, RUN_STYLE v1, RUN_COVERAGE v1). Each shaped run
 * becomes one positioned Skia text blob: the glyph ids and advances Uniscribe produced (so glyphs land where ScriptTextOut
 * puts them), the typeface of the run's own HFONT ({@link M3FontTable}; fallback-font runs keep their fallback face), glyph
 * offsets as (du, -dv); character runs (fNoGlyphIndex, font-linked fallback) map code points to glyphs of the run font; drawn in the run colour with the GC alpha on the GC's canvas, under its clip and transform. Run
 * backgrounds, selection, underline and strikeout are rectangles. In-house pattern: <code>M3PlaneBackend.glyphRun</code>
 * and <code>Replay</code> GLYPH_RUN of the GPU planes.
 * <p>
 * Not taken (the layout goes to {@link M3TextLayoutFallback}, counted): XOR, a mirrored frame, the system property
 * <code>swt.m3.text=gdi</code> (the transparent GDI raster for every layout, used to measure Skia against GDI), and what
 * the recorder rejects (border, error/squiggle underline, justified runs, runs without glyph indices, right-to-left
 * layouts).
 * </p>
 */
public final class M3GlyphRuns implements M3RunSink {

	static final boolean GDI_ONLY = "gdi".equals(System.getProperty("swt.m3.text"));
	private static final AtomicLong RUNS = new AtomicLong();
	private static final AtomicLong BLOBS = new AtomicLong();
	private final M3ExternalGC gc;

	M3GlyphRuns(M3ExternalGC gc) {
		this.gc = gc;
	}

	/** glyph runs handed to M3 sinks since load */
	public static long runs() {
		return RUNS.get();
	}

	/** text blobs drawn by M3 sinks since load */
	public static long blobs() {
		return BLOBS.get();
	}

	/** layouts drawn through the transparent fallback since load */
	public static long fallbacks() {
		return M3TextLayoutFallback.count();
	}

	@Override
	public int capabilities() {
		return CAP_LINES | CAP_FALLBACK_FONT | CAP_RTL | CAP_OFFSETS | CAP_CHAR_GLYPHS;
	}

	@Override
	public int zoom() {
		return gc.getScaler().getNativeZoom();
	}

	@Override
	public int alpha() {
		return gc.getAlpha();
	}

	@Override
	public int foregroundPixel() {
		return (int) gc.getForeground().handle;
	}

	@Override
	public boolean accepts() {
		return !GDI_ONLY && !gc.getXORMode() && !gc.isMirrored();
	}

	@Override
	public void fillRect(int x, int y, int width, int height, int argb) {
		try (Paint paint = new Paint().setColor(argb).setMode(PaintMode.FILL)) {
			gc.getM3Surface().getCanvas().drawRect(Rect.makeXYWH(x, y, width, height), paint);
		}
	}

	@Override
	public void clipPush(int x, int y, int width, int height) {
		gc.getM3Surface().getCanvas().save();
		gc.getM3Surface().getCanvas().clipRect(Rect.makeXYWH(x, y, width, height));
	}

	@Override
	public void clipPop() {
		gc.getM3Surface().getCanvas().restore();
	}

	@Override
	public boolean glyphRun(Font font, long fontHandle, char[] glyphs, int[] advances, int[] offsets, int count, int x,
			int baselineY, int argb, boolean characters) {
		RUNS.incrementAndGet();
		final io.github.humbleui.skija.Font m3Font = M3FontTable.font(fontHandle, gc.getTextAntialias());
		if (m3Font == null) {
			return false;
		}
		try (TextBlob blob = characters ? charBlob(m3Font, new String(glyphs, 0, count), pen(advances, count))
				: blob(m3Font, glyphs, advances, offsets, count);
				Paint paint = new Paint().setColor(argb).setAntiAlias(true)) {
			if (blob != null) {
				gc.getM3Surface().getCanvas().drawTextBlob(blob, x, baselineY, paint);
				BLOBS.incrementAndGet();
			}
		}
		return true;
	}

	/* positioned blob: pen positions from the shaper's advances; offsets as (du, -dv) when present */
	static TextBlob blob(io.github.humbleui.skija.Font font, char[] glyphs, int[] advances, int[] offsets, int count) {
		final short[] ids = new short[count];
		final float[] pen = pen(advances, count);
		IntStream.range(0, count).forEach(i -> ids[i] = (short) glyphs[i]);
		if (offsets == null) {
			return TextBlob.makeFromPosH(ids, pen, 0, font);
		}
		final Point[] at = IntStream.range(0, count).mapToObj(i -> new Point(pen[i] + offsets[2 * i], -offsets[2 * i + 1]))
				.toArray(Point[]::new);
		return TextBlob.makeFromPos(ids, at, font);
	}

	/* pen x of each glyph from the shaper's advances */
	static float[] pen(int[] advances, int count) {
		final float[] pen = new float[count];
		IntStream.range(1, count).forEach(i -> pen[i] = pen[i - 1] + advances[i - 1]);
		return pen;
	}

	/* RUN_COVERAGE v1: a character run (fNoGlyphIndex): one glyph per code point of the run font, at its first unit's pen */
	static TextBlob charBlob(io.github.humbleui.skija.Font font, String text, float[] pen) {
		final int[] starts = IntStream.range(0, text.length())
				.filter(i -> i == 0 || !Character.isLowSurrogate(text.charAt(i)) || !Character.isHighSurrogate(text.charAt(i - 1)))
				.toArray();
		final int[] codePoints = IntStream.of(starts).map(text::codePointAt).toArray();
		final short[] ids = font.getUTF32Glyphs(codePoints);
		try (TextBlobBuilder builder = new TextBlobBuilder()) {
			IntStream.range(0, starts.length).forEach(i -> append(builder, font, ids[i], codePoints[i], pen[starts[i]]));
			return builder.build();
		}
	}

	/* one glyph; a code point the run font lacks (glyph 0) comes from Skia's fallback face (M3FontTable.fallback) */
	static void append(TextBlobBuilder builder, io.github.humbleui.skija.Font font, short id, int codePoint, float x) {
		final io.github.humbleui.skija.Font face = id != 0 ? font : M3FontTable.fallback(font, codePoint);
		final short glyph = id != 0 ? id : face.getUTF32Glyphs(new int[] { codePoint })[0];
		builder.appendRunPosH(face, new short[] { glyph }, new float[] { x }, 0);
	}
}
