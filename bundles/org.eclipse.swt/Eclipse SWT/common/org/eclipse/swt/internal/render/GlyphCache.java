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
import java.util.*;
import java.util.function.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.image.*;

/**
 * The vector glyphs of the drawn modes (docs/m3/RENDER_MODES.md, vector glyphs, leaf SV1): an icon rasterized at the
 * exact pixel size of a zoom once per (icon, size, zoom, theme generation), kept in preallocated lanes and looked up
 * per frame without rasterizing. The key packs the four values into one long; an open-addressing table with linear
 * probing maps it to a slot; a full cache evicts by the clock (second chance) policy. The backends keep one device copy
 * per {@link ImageData}, so a hit draws an already uploaded glyph. One thread (the UI thread) uses a cache.
 */
public final class GlyphCache {
	/** Rasterizes an icon to an image of exactly <code>size x size</code> pixels; null when it cannot. */
	public interface Rasterizer {
		ImageData rasterize (int icon, int size);
	}

	private final Rasterizer rasterizer;
	/* the lanes, the open-addressing table and the clock (no native resources: nothing to close on eviction) */
	private final LongCache<ImageData> cache;
	private int rasterizations;

	/**
	 * @param capacity the glyphs kept (at least 1)
	 * @param rasterizer makes a glyph on a miss
	 */
	public GlyphCache (int capacity, Rasterizer rasterizer) {
		this.rasterizer = Objects.requireNonNull (rasterizer, "rasterizer");
		cache = new LongCache<> (capacity, image -> {
			/* garbage collected */
		});
	}

	/**
	 * The key of a glyph: icon (16 bits), size in pixels (12 bits), zoom in percent (12 bits), and a 24-bit variant: the
	 * theme generation, or the tint (<code>0xRRGGBB</code>) of a cache shared by every theme.
	 */
	public static long key (int icon, int size, int zoom, int themeGeneration) {
		return (icon & 0xFFFFL) << 48 | (size & 0xFFFL) << 36 | (zoom & 0xFFFL) << 24 | themeGeneration & 0xFFFFFFL;
	}

	/**
	 * The glyph of an icon at a size in points and a zoom (100 for 100 %): rasterized at <code>size * zoom / 100</code>
	 * pixels on the first request, then answered from the cache.
	 *
	 * @return the image, or null when the rasterizer cannot make it
	 */
	public ImageData get (int icon, int size, int zoom, int themeGeneration) {
		long key = key (icon, size, zoom, themeGeneration);
		int lane = cache.lane (key);
		/* a kept null (the rasterizer could not make it) is answered too: no rasterization per frame */
		if (lane >= 0) return cache.value (lane);
		ImageData image = rasterizer.rasterize (icon, Math.max (1, (size * zoom + 50) / 100));
		rasterizations++;
		cache.put (key, image);
		return image;
	}

	/** The rasterizations so far (one per distinct key while the cache is large enough). */
	public int rasterizations () {
		return rasterizations;
	}

	/** The glyphs kept. */
	public int size () {
		return cache.size ();
	}

	/**
	 * A rasterizer of SVG sources through SWT's own SVG support (the <code>SVGRasterizer</code> service of the
	 * <code>org.eclipse.swt.svg</code> fragment), or null when no rasterizer is installed.
	 *
	 * @param sources the SVG stream of an icon id (a new stream per call), or null for an unknown icon
	 */
	public static Rasterizer svg (IntFunction<InputStream> sources) {
		Iterator<SVGRasterizer> found = ServiceLoader.load (SVGRasterizer.class, GlyphCache.class.getClassLoader ()).iterator ();
		if (!found.hasNext ()) return null;
		SVGRasterizer svg = found.next ();
		return (icon, size) -> {
			try (InputStream in = sources.apply (icon)) {
				return in == null ? null : svg.rasterizeSVG (in, size, size);
			} catch (IOException e) {
				return null;
			}
		};
	}
}
