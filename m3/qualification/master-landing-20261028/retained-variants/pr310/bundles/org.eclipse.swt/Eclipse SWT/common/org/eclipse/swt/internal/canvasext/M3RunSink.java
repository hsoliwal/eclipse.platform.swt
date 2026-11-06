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
package org.eclipse.swt.internal.canvasext;

import org.eclipse.swt.graphics.*;

//FLLDCIM: IDL-LAW — L13 GLYPH_RUN_SINK v1: the one target of TextLayout's shaped-run recorder (retained planes, M3 GC)
/**
 * Target of the platform TextLayout's shaped-run recorder (text leaf L13, atom GLYPH_RUN_SINK v1). The recorder walks the
 * layout once and emits, in the stock paint order, run and selection backgrounds, one shaped glyph run per text run
 * (glyph ids, advances and offsets as the platform shaper placed them) and the run decorations. Two sinks: the retained
 * planes' op stream (GPU replay) and an M3 GC (<code>IExternalGC.runSink</code>), which draws each run as a positioned
 * Skia text blob. Coordinates are device pixels at {@link #zoom()}; colours are 0xAARRGGBB.
 * <p>
 * The recorder asks {@link #accepts()} before emitting anything; a sink that cannot take the layout (XOR, a mirrored
 * frame) leaves it to the platform raster path. A <code>false</code> from {@link #glyphRun} after the first op means the
 * sink failed mid-layout.
 * </p>
 *
 * @noreference This interface is not intended to be referenced by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface M3RunSink {

	/** RUN_STYLE v1: underline (single, double, link), strikeout and rise as rectangles and shifted baselines */
	int CAP_LINES = 1;
	/** RUN_COVERAGE v1: runs shaped with a fallback font (the run's own platform font handle) */
	int CAP_FALLBACK_FONT = 1 << 1;
	/** RUN_COVERAGE v1: right-to-left runs inside a left-to-right layout (glyphs in visual order) */
	int CAP_RTL = 1 << 2;
	/** RUN_COVERAGE v1: glyph offsets (combining marks) */
	int CAP_OFFSETS = 1 << 3;
	/** RUN_COVERAGE v1: runs the shaper left as characters (win32 fNoGlyphIndex: font-linked fallback runs); glyphs[] holds UTF-16 units */
	int CAP_CHAR_GLYPHS = 1 << 4;

	/** @return the CAP_* bits this sink draws; anything else leaves the layout to the raster path */
	int capabilities();

	/** @return the zoom of the sink's pixel space */
	int zoom();

	/** @return the GC alpha (0..255), applied to every colour the recorder computes */
	int alpha();

	/** @return the GC foreground as a platform colour value (win32 COLORREF), the colour of runs without a style foreground */
	int foregroundPixel();

	/** @return whether the sink takes this layout at all (checked once, before the first op) */
	boolean accepts();

	/** fills a rectangle (run background, selection, underline, strikeout) */
	void fillRect(int x, int y, int width, int height, int argb);

	/** intersects the clip with a rectangle until the matching {@link #clipPop()} */
	void clipPush(int x, int y, int width, int height);

	/** ends the innermost {@link #clipPush} */
	void clipPop();

	/**
	 * One shaped run.
	 *
	 * @param font the SWT font of the run (style font, layout font or system font)
	 * @param fontHandle the platform font the shaper used (win32: the run's fallback HFONT or the font's HFONT)
	 * @param glyphs glyph ids in drawing order, <code>count</code> used
	 * @param advances advance of each glyph in pixels
	 * @param offsets glyph offsets as (du, dv) pairs, or <code>null</code>
	 * @param x left edge of the run
	 * @param baselineY baseline of the run (rise applied)
	 * @param argb colour of the glyphs
	 * @param characters whether <code>glyphs</code> holds UTF-16 characters instead of glyph ids (CAP_CHAR_GLYPHS)
	 * @return <code>false</code> when the run cannot be drawn by this sink
	 */
	boolean glyphRun(Font font, long fontHandle, char[] glyphs, int[] advances, int[] offsets, int count, int x, int baselineY,
			int argb, boolean characters);
}
