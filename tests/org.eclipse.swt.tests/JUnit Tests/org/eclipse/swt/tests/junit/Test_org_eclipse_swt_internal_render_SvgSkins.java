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

import java.lang.management.*;
import java.util.logging.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * SVG-skinned controls (BEHAVIOURS.md, "SVG-skinned controls"), headless: the built-in button skin composed at
 * 100/125/150/200 % and 10 sizes keeps its corners exact (nine-slice: the corners of any size equal the part rasterized
 * at its own size), its edges uniform and its fill the tint; a part is rasterized once per zoom whatever the sizes;
 * marks without insets are rasterized at their exact size; a missing state falls back to the normal part; a warm lookup
 * allocates nothing. Needs the SVG rasterizer (org.eclipse.swt.svg and JSVG) on the class path.
 */
public class Test_org_eclipse_swt_internal_render_SvgSkins {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_internal_render_SvgSkins.class.getName ());
	static final int [] ZOOMS = {100, 125, 150, 200};
	static final int [] [] SIZES = {{30, 20}, {40, 24}, {60, 28}, {80, 30}, {100, 32}, {120, 36}, {160, 40}, {200, 48}, {240, 56}, {320, 64}};
	static final int TINT = 0x3366CC;

	static int rgb (ImageData image, int x, int y) {
		RGB c = image.palette.getRGB (image.getPixel (x, y));
		return c.red << 16 | c.green << 8 | c.blue;
	}

	@Test
	public void buttonsKeepTheirCornersAtEveryZoomAndSize () {
		SvgSkins skins = SvgSkins.create (256);
		Assumptions.assumeTrue (skins != null, "no SVG rasterizer (org.eclipse.swt.svg fragment) on the class path");
		int button = SvgSkins.id ("m3/button");
		assertTrue (SvgSkins.has (button, SvgSkins.BACKGROUND));
		for (int zoom : ZOOMS) {
			int natural = 24 * zoom / 100, corner = 6 * zoom / 100;
			ImageData own = skins.part (button, SvgSkins.BACKGROUND, SvgSkins.NORMAL, TINT, natural, natural, zoom, null);
			assertNotNull (own);
			for (int [] size : SIZES) {
				int width = size [0] * zoom / 100, height = size [1] * zoom / 100;
				ImageData image = skins.part (button, SvgSkins.BACKGROUND, SvgSkins.NORMAL, TINT, width, height, zoom, null);
				assertEquals (width, image.width, "the exact width");
				assertEquals (height, image.height, "the exact height");
				for (int y = 0; y < corner; y++) {
					for (int x = 0; x < corner; x++) {
						assertEquals (own.getAlpha (x, y), image.getAlpha (x, y), zoom + " % " + width + "x" + height + ": the top left corner at " + x + "," + y);
						assertEquals (own.getAlpha (natural - 1 - x, natural - 1 - y), image.getAlpha (width - 1 - x, height - 1 - y), "the bottom right corner");
					}
				}
				/* the top edge is uniform between the corners; the centre is the tint, opaque */
				int edgeAlpha = image.getAlpha (corner, 0);
				for (int x = corner; x < width - corner; x++) assertEquals (edgeAlpha, image.getAlpha (x, 0), "the top edge is uniform");
				assertEquals (255, image.getAlpha (width / 2, height / 2));
				assertEquals (TINT, rgb (image, width / 2, height / 2), "the fill is the tint");
			}
		}
		assertEquals (ZOOMS.length, skins.rasterizations (), "one rasterization per zoom, whatever the sizes");
		assertEquals (ZOOMS.length * (SIZES.length + 1), skins.compositions (), "one composition per size");
	}

	@Test
	public void marksAreRasterizedAtTheirSizeAndStatesFallBack () {
		SvgSkins skins = SvgSkins.create (64);
		Assumptions.assumeTrue (skins != null, "no SVG rasterizer (org.eclipse.swt.svg fragment) on the class path");
		int check = SvgSkins.id ("m3/check"), button = SvgSkins.id ("m3/button");
		for (int size : new int [] {16, 20, 24, 32}) {
			ImageData mark = skins.part (check, SvgSkins.MARK, SvgSkins.NORMAL, 0x202020, size, size, 100, null);
			assertEquals (size, mark.width);
			assertTrue (Glyphs.shaped (mark), "a mark has opaque and clear pixels at " + size);
		}
		ImageData mixed = skins.part (check, SvgSkins.MARK, SvgSkins.MIXED, 0x202020, 16, 16, 100, null);
		assertNotSame (skins.part (check, SvgSkins.MARK, SvgSkins.NORMAL, 0x202020, 16, 16, 100, null), mixed, "the mixed mark has its own source");
		ImageData normal = skins.part (button, SvgSkins.BORDER, SvgSkins.NORMAL, TINT, 80, 30, 100, null);
		assertSame (normal, skins.part (button, SvgSkins.BORDER, SvgSkins.HOVER, TINT, 80, 30, 100, null), "no hover source: the normal part, cached once");
		assertNotSame (normal, skins.part (button, SvgSkins.BORDER, SvgSkins.FOCUSED, TINT, 80, 30, 100, null), "the focused border has its own source");
		assertNull (skins.part (SvgSkins.id ("m3/no-such-skin"), SvgSkins.BACKGROUND, SvgSkins.NORMAL, TINT, 80, 30, 100, null), "a missing part paints as before");
	}

	@Test
	public void aWarmLookupAllocatesNothing () {
		SvgSkins skins = SvgSkins.create (256);
		Assumptions.assumeTrue (skins != null, "no SVG rasterizer (org.eclipse.swt.svg fragment) on the class path");
		int button = SvgSkins.id ("m3/button");
		int [] insets = {6, 6, 6, 6};
		for (int [] size : SIZES) skins.part (button, SvgSkins.BACKGROUND, SvgSkins.NORMAL, TINT, size [0], size [1], 150, insets);
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();
		long id = Thread.currentThread ().threadId ();
		for (int i = 0; i < 20_000; i++) skins.part (button, SvgSkins.BACKGROUND, SvgSkins.NORMAL, TINT, SIZES [i % SIZES.length] [0], SIZES [i % SIZES.length] [1], 150, insets);
		long before = threads.getThreadAllocatedBytes (id);
		int found = 0;
		for (int i = 0; i < 100_000; i++) {
			if (skins.part (button, SvgSkins.BACKGROUND, SvgSkins.NORMAL, TINT, SIZES [i % SIZES.length] [0], SIZES [i % SIZES.length] [1], 150, insets) != null) found++;
		}
		long bytes = threads.getThreadAllocatedBytes (id) - before;
		LOG.info ("svg skins: 100000 warm lookups, " + bytes + " bytes allocated, " + skins.rasterizations () + " rasterizations");
		assertEquals (100_000, found);
		assertTrue (bytes < 4096, "warm lookups allocated " + bytes + " bytes");
	}
}
