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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The built-in SVG glyph set (leaf SV2) and its rasterization at 100, 150 and 200 % (leaf SV3, headless part) through
 * SWT's SVG rasterizer: exact pixel sizes, a tinted colour, and crispness (the share of anti-aliased edge pixels stays
 * under a half at every zoom: rasterized at that size, not blurred up from a 100 % bitmap). Skips the rasterizer tests
 * when the <code>org.eclipse.swt.svg</code> fragment is not on the class path.
 */
public class Test_org_eclipse_swt_internal_render_Glyphs {

	@Test
	public void everyGlyphHasASourceWithCurrentColor () {
		for (int glyph = 0; glyph < Glyphs.COUNT; glyph++) {
			String source = Glyphs.source (glyph);
			assertNotNull (source, Glyphs.name (glyph));
			assertTrue (source.contains ("currentColor"), Glyphs.name (glyph) + " is tinted through currentColor");
			assertTrue (source.contains ("viewBox=\"0 0 16 16\""), Glyphs.name (glyph) + " has the 16 x 16 view box");
			assertFalse (Glyphs.tinted (glyph, 0x2F6FD0).contains ("currentColor"));
			assertTrue (Glyphs.tinted (glyph, 0x2F6FD0).contains ("#2f6fd0"));
		}
		assertNull (Glyphs.name (-1));
		assertNull (Glyphs.source (Glyphs.COUNT));
		assertNull (Glyphs.tinted (99, 0));
		assertFalse (Glyphs.shaped (null));
		assertFalse (Glyphs.shaped (new ImageData (4, 4, 24, new PaletteData (0xFF0000, 0xFF00, 0xFF))), "no alpha: not a glyph");
	}

	@Test
	public void rasterizedAtTheExactSizeOfEachZoomTintedAndCrisp () {
		GlyphCache.Rasterizer rasterizer = Glyphs.rasterizer (glyph -> 0x2F6FD0);
		Assumptions.assumeTrue (rasterizer != null, "no SVG rasterizer (org.eclipse.swt.svg fragment) on the class path");
		GlyphCache cache = new GlyphCache (64, rasterizer);
		for (int glyph = 0; glyph < Glyphs.COUNT; glyph++) {

			for (int zoom : new int [] {100, 150, 200}) {
				ImageData image = cache.get (glyph, 16, zoom, 0);
				int size = 16 * zoom / 100;
				assertNotNull (image, Glyphs.name (glyph) + " at " + zoom);
				assertEquals (size, image.width, Glyphs.name (glyph) + " at " + zoom);
				assertEquals (size, image.height);
				assertTrue (Glyphs.shaped (image), Glyphs.name (glyph) + " at " + zoom + " has opaque and transparent pixels");
				assertTrue (tinted (image, 0x2F6FD0), Glyphs.name (glyph) + " at " + zoom + " carries the tint");
				double partial = partialShare (image);
				assertTrue (partial < 0.5, Glyphs.name (glyph) + " at " + zoom + ": the edge share " + partial + " stays under a half (rasterized at this size, not blurred up from 100 %)");
			}
		}
		assertEquals (Glyphs.COUNT * 3, cache.rasterizations (), "one rasterization per glyph and zoom");
		for (int frame = 0; frame < 100; frame++) cache.get (Glyphs.CHECK_ON, 16, 150, 0);
		assertEquals (Glyphs.COUNT * 3, cache.rasterizations (), "frames look up");
	}

	/* true when an opaque pixel has the tint colour */
	static boolean tinted (ImageData image, int rgb) {
		for (int y = 0; y < image.height; y++) {
			for (int x = 0; x < image.width; x++) {
				RGB c = image.palette.getRGB (image.getPixel (x, y));
				if (image.getAlpha (x, y) == 255 && (c.red << 16 | c.green << 8 | c.blue) == rgb) return true;
			}
		}
		return false;
	}

	/* the share of pixels that are neither opaque nor transparent: the anti-aliased edges */
	static double partialShare (ImageData image) {
		int partial = 0;
		for (byte a : image.alphaData) {
			if (a != 0 && a != -1) partial++;
		}
		return partial / (double) image.alphaData.length;
	}
}
