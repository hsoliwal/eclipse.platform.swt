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

import java.util.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/** The vector glyph cache (leaf SV1) without a display: one rasterization per key at the exact pixel size, lookups per frame, eviction. */
public class Test_org_eclipse_swt_internal_render_GlyphCache {
	final List<String> made = new ArrayList<> ();
	final GlyphCache.Rasterizer counting = (icon, size) -> {
		made.add (icon + "@" + size);
		return icon == 99 ? null : new ImageData (size, size, 24, new PaletteData (0xFF0000, 0xFF00, 0xFF));
	};

	@Test
	public void oneRasterizationPerKeyAtTheExactPixelSize () {
		GlyphCache cache = new GlyphCache (16, counting);
		ImageData first = cache.get (1, 16, 100, 0);
		for (int frame = 0; frame < 1000; frame++) assertSame (first, cache.get (1, 16, 100, 0), "a frame looks up, never rasterizes");
		assertEquals (24, cache.get (1, 16, 150, 0).width, "150 % of 16 points is 24 pixels");
		assertEquals (32, cache.get (1, 16, 200, 0).width);
		assertEquals (List.of ("1@16", "1@24", "1@32"), made);
		cache.get (1, 16, 100, 1);
		assertEquals (4, cache.rasterizations (), "a new theme generation is a new key");
		assertNull (cache.get (99, 16, 100, 0), "an icon the rasterizer cannot make answers null");
		assertNull (cache.get (99, 16, 100, 0));
		assertEquals (5, cache.rasterizations (), "and is not tried again");
		assertEquals (5, cache.size ());
	}

	@Test
	public void aFullCacheEvictsByTheClock () {
		GlyphCache cache = new GlyphCache (2, counting);
		cache.get (1, 16, 100, 0);
		cache.get (2, 16, 100, 0);
		cache.get (3, 16, 100, 0);
		assertEquals (2, cache.size ());
		int before = cache.rasterizations ();
		cache.get (3, 16, 100, 0);
		assertEquals (before, cache.rasterizations (), "the newest glyph stays");
		for (int i = 0; i < 200; i++) cache.get (10 + i % 7, 16, 100, i % 3);
		assertEquals (2, cache.size (), "the lanes never grow");
	}

	@Test
	public void keysPackEveryPart () {
		assertNotEquals (GlyphCache.key (1, 16, 100, 0), GlyphCache.key (2, 16, 100, 0));
		assertNotEquals (GlyphCache.key (1, 16, 100, 0), GlyphCache.key (1, 17, 100, 0));
		assertNotEquals (GlyphCache.key (1, 16, 100, 0), GlyphCache.key (1, 16, 125, 0));
		assertNotEquals (GlyphCache.key (1, 16, 100, 0), GlyphCache.key (1, 16, 100, 1));
		assertThrows (NullPointerException.class, () -> new GlyphCache (1, null));
		assertEquals (8, new GlyphCache (0, counting).get (5, 8, 100, 0).width, "capacity 0 keeps one glyph");
	}

	@Test
	public void svgRasterizerIsSwtsServiceWhenInstalled () {
		GlyphCache.Rasterizer svg = GlyphCache.svg (icon -> null);
		if (svg == null) return; /* no org.eclipse.swt.svg fragment on this class path */
		assertNull (svg.rasterize (1, 16), "an unknown icon has no source");
	}
}
