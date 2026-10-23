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
import java.util.regex.Matcher;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.image.*;

/**
 * SVG-skinned controls (docs/m3/behaviours/BEHAVIOURS.md, "SVG-skinned controls"; user 2026-10-20: "SVG-painted button
 * to my size, instead of painting it manually"): buttons, toolbar items, badges, chips, toggles, check and radio marks,
 * sash grips and scroll carets take their visuals from SVG skins, with no paint code per control.
 * <p>
 * A skin is a set of SVG parts ({@link #PARTS}), each optionally per state ({@link #STATES}), found as resources
 * <code>skins/&lt;skin&gt;/&lt;part&gt;[.&lt;state&gt;].svg</code> next to this class or {@link #register registered}. A part is
 * tinted by replacing <code>currentColor</code> (the colour of the L&amp;F key
 * <code>&lt;Component&gt;.skin.&lt;part&gt;.&lt;state&gt;.tint</code>, agreed with the all-widgets track) and rasterized once per
 * (skin, part, state, tint, zoom) at its own size times the zoom through SWT's {@link SVGRasterizer} seam. Any control
 * size is then composed by nine-slice scaling (insets from the key <code>&lt;Component&gt;.skin.insets</code>, else the
 * skin's <code>data-insets</code> attribute, in SVG units): corners are copied 1:1, so they keep their radius at every size,
 * edges stretch along one axis and the centre along both. A resize is a composition for the new size only, never a
 * rasterization. Both levels are cached ({@link LongCache}); a warm lookup allocates nothing. One thread (the UI thread,
 * or the render thread for the drawn planes) uses an instance.
 * </p>
 */
public final class SvgSkins {
	/** Parts of a skin (SVG resources, and the <code>&lt;part&gt;</code> of the L&amp;F tint keys). */
	public static final String [] PARTS = {"background", "border", "mark", "grip", "caret"};
	public static final int BACKGROUND = 0, BORDER = 1, MARK = 2, GRIP = 3, CARET = 4;
	/** States of a part (a missing state resource falls back to the part's normal one). */
	public static final String [] STATES = {"normal", "hover", "pressed", "focused", "disabled", "selected", "mixed"};
	public static final int NORMAL = 0, HOVER = 1, PRESSED = 2, FOCUSED = 3, DISABLED = 4, SELECTED = 5, MIXED = 6;
	private static final java.util.regex.Pattern INSETS = java.util.regex.Pattern.compile ("data-insets\\s*=\\s*\"\\s*([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s*\"");

	/* registered skins by name: an int id each (the base cache key), and their sources by part and state */
	private static final Map<String, Integer> IDS = new HashMap<> ();
	private static final List<Source []> SOURCES = new ArrayList<> ();
	private static final List<String> NAMES = new ArrayList<> ();

	/* an SVG source of a part and its own nine-slice insets, parsed once */
	private static final class Source {
		final String svg;
		final int top, left, bottom, right;

		Source (String svg) {
			this.svg = svg;
			int [] insets = insets (svg);
			top = insets [0];
			left = insets [1];
			bottom = insets [2];
			right = insets [3];
		}
	}

	/** A composed part at a size, with what it was made from (cache entries are checked against the request). */
	private static final class Composed {
		final int skin, part, state, tint, zoom, width, height, top, left, bottom, right;
		final ImageData image;

		Composed (int skin, int part, int state, int tint, int zoom, int width, int height, int top, int left, int bottom, int right, ImageData image) {
			this.skin = skin;
			this.part = part;
			this.state = state;
			this.tint = tint;
			this.zoom = zoom;
			this.width = width;
			this.height = height;
			this.top = top;
			this.left = left;
			this.bottom = bottom;
			this.right = right;
			this.image = image;
		}
	}

	private final SVGRasterizer rasterizer;
	/* rasterized parts at a zoom, keyed exactly by (skin, part, state, tint, zoom) */
	private final LongCache<ImageData> bases;
	/* composed parts at a size, keyed by a hash of the request and checked against it */
	private final LongCache<Composed> composed;
	private int rasterizations, compositions;

	/**
	 * @param capacity the composed parts kept (the rasterized ones: a quarter of it, at least 8)
	 * @return skins, or null when no SVG rasterizer (the org.eclipse.swt.svg fragment) is installed: the look then paints
	 *         as before
	 */
	public static SvgSkins create (int capacity) {
		Iterator<SVGRasterizer> found = ServiceLoader.load (SVGRasterizer.class, SvgSkins.class.getClassLoader ()).iterator ();
		return found.hasNext () ? new SvgSkins (found.next (), capacity) : null;
	}

	private SvgSkins (SVGRasterizer rasterizer, int capacity) {
		this.rasterizer = rasterizer;
		bases = new LongCache<> (Math.max (8, capacity / 4), image -> {
			/* garbage collected */
		});
		composed = new LongCache<> (Math.max (8, capacity), part -> {
			/* garbage collected */
		});
	}

	/**
	 * Registers (or replaces) a skin part's SVG source for a state (<code>null</code> state: the normal one), for skins
	 * that are not resources; answers the skin's id.
	 */
	public static synchronized int register (String skin, int part, int state, String svg) {
		int id = id (skin);
		SOURCES.get (id) [part * STATES.length + state] = svg == null ? null : new Source (svg);
		return id;
	}

	/** The id of a skin name (resources are looked up on first use); ids are stable for the life of the class. */
	public static synchronized int id (String skin) {
		Integer id = IDS.get (skin);
		if (id != null) return id;
		int next = NAMES.size ();
		IDS.put (skin, next);
		NAMES.add (skin);
		Source [] sources = new Source [PARTS.length * STATES.length];
		for (int part = 0; part < PARTS.length; part++) {
			for (int state = 0; state < STATES.length; state++) {
				String name = state == NORMAL ? PARTS [part] : PARTS [part] + "." + STATES [state];
				String svg = resource ("skins/" + skin + "/" + name + ".svg");
				sources [part * STATES.length + state] = svg == null ? null : new Source (svg);
			}
		}
		SOURCES.add (sources);
		return next;
	}

	private static String resource (String path) {
		try (InputStream in = SvgSkins.class.getResourceAsStream (path)) {
			return in == null ? null : new String (in.readAllBytes (), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	/* the source of a part in a state, falling back to the normal state */
	private static synchronized Source source (int skin, int part, int state) {
		Source [] sources = SOURCES.get (skin);
		Source source = sources [part * STATES.length + state];
		return source != null ? source : sources [part * STATES.length + NORMAL];
	}

	/** True when a skin has the part (in some state). */
	public static boolean has (int skin, int part) {
		return source (skin, part, NORMAL) != null;
	}

	/**
	 * The part of a skin in a state, tinted, composed at a size in pixels for a zoom.
	 *
	 * @param skin a skin id ({@link #id})
	 * @param tint <code>0xRRGGBB</code>, replacing <code>currentColor</code>
	 * @param width the control's width in pixels
	 * @param height the control's height in pixels
	 * @param zoom the window's zoom in percent (the corners are rasterized at it)
	 * @param insets the nine-slice insets in SVG units, top, left, bottom, right; null for the skin's own
	 * @return the image, exactly <code>width x height</code>, or null when the skin has no such part
	 */
	public ImageData part (int skin, int part, int state, int tint, int width, int height, int zoom, int [] insets) {
		if (width <= 0 || height <= 0) return null;
		tint &= 0xFFFFFF;
		Source source = source (skin, part, state);
		if (source == null) return null;
		int top = insets != null ? insets [0] : source.top, left = insets != null ? insets [1] : source.left;
		int bottom = insets != null ? insets [2] : source.bottom, right = insets != null ? insets [3] : source.right;
		int resolvedState = source == source (skin, part, NORMAL) ? NORMAL : state;
		long key = mix (mix (mix (mix (mix (mix (mix (mix (mix (mix (mix (0xcbf29ce484222325L, skin), part), resolvedState), tint), zoom), width), height), top), left),
			bottom), right);
		Composed hit = composed.get (key);
		if (hit != null && hit.skin == skin && hit.part == part && hit.state == resolvedState && hit.tint == tint && hit.zoom == zoom && hit.width == width
			&& hit.height == height && hit.top == top && hit.left == left && hit.bottom == bottom && hit.right == right) {
			return hit.image;
		}
		ImageData image;
		if (top == 0 && left == 0 && bottom == 0 && right == 0) {
			/* no insets (a mark, a grip, a caret): rasterized at the exact size, crisp, never stretched */
			image = rasterize (source.svg, tint, width, height);
			rasterizations++;
		} else {
			ImageData base = base (skin, part, resolvedState, tint, zoom, source.svg);
			if (base == null) return null;
			image = nineSlice (base, width, height, top * zoom / 100, left * zoom / 100, bottom * zoom / 100, right * zoom / 100);
			compositions++;
		}
		if (image == null) return null;
		composed.put (key, new Composed (skin, part, resolvedState, tint, zoom, width, height, top, left, bottom, right, image));
		return image;
	}

	private ImageData base (int skin, int part, int state, int tint, int zoom, String source) {
		long key = (long) (skin & 0xFFF) << 52 | (long) (part & 0xF) << 48 | (long) (state & 0xF) << 44 | (long) (zoom & 0xFFF) << 32 | (tint & 0xFFFFFFL) << 8;
		int lane = bases.lane (key);
		if (lane >= 0) return bases.value (lane);
		String tinted = source.replace ("currentColor", String.format (Locale.ROOT, "#%06x", tint));
		ImageData image;
		try (InputStream in = new ByteArrayInputStream (tinted.getBytes (StandardCharsets.UTF_8))) {
			image = rasterizer.rasterizeSVG (in, Math.max (1, zoom));
		} catch (IOException | RuntimeException e) {
			image = null;
		}
		rasterizations++;
		bases.put (key, image);
		return image;
	}

	private ImageData rasterize (String svg, int tint, int width, int height) {
		String tinted = svg.replace ("currentColor", String.format (Locale.ROOT, "#%06x", tint));
		try (InputStream in = new ByteArrayInputStream (tinted.getBytes (StandardCharsets.UTF_8))) {
			return rasterizer.rasterizeSVG (in, width, height);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/* the skin's own insets (data-insets="top left bottom right" on the svg element), in SVG units; zeros without */
	static int [] insets (String source) {
		Matcher m = INSETS.matcher (source);
		if (!m.find ()) return new int [4];
		int [] insets = new int [4];
		for (int i = 0; i < 4; i++) insets [i] = Math.round (Float.parseFloat (m.group (i + 1)));
		return insets;
	}

	/*
	 * Nine-slice scaling of a rasterized part to a size: rows and columns inside the insets are copied 1:1, the middle ones
	 * are sampled (nearest) from the middle of the source, so an edge that is uniform along its axis (a rounded rectangle's
	 * side) stays exact. Insets wider than the size shrink proportionally.
	 */
	static ImageData nineSlice (ImageData base, int width, int height, int top, int left, int bottom, int right) {
		if (left + right > width) {
			left = left * width / Math.max (1, left + right);
			right = width - left;
		}
		if (top + bottom > height) {
			top = top * height / Math.max (1, top + bottom);
			bottom = height - top;
		}
		left = Math.min (left, base.width);
		right = Math.min (right, base.width - left);
		top = Math.min (top, base.height);
		bottom = Math.min (bottom, base.height - top);
		ImageData image = new ImageData (width, height, base.depth, base.palette);
		boolean alpha = base.alphaData != null || base.alpha != -1;
		if (alpha) image.alphaData = new byte [width * height];
		int [] xs = new int [width];
		for (int x = 0; x < width; x++) xs [x] = source (x, width, base.width, left, right);
		for (int y = 0; y < height; y++) {
			int sy = source (y, height, base.height, top, bottom);
			for (int x = 0; x < width; x++) {
				image.setPixel (x, y, base.getPixel (xs [x], sy));
				if (alpha) image.alphaData [y * width + x] = (byte) base.getAlpha (xs [x], sy);
			}
		}
		return image;
	}

	/* the source coordinate of a target one: the leading inset 1:1, the trailing inset 1:1 from the end, the middle sampled */
	private static int source (int t, int size, int sourceSize, int lead, int trail) {
		if (t < lead) return t;
		if (t >= size - trail) return sourceSize - (size - t);
		int middle = sourceSize - lead - trail, target = size - lead - trail;
		if (middle <= 0) return Math.min (sourceSize - 1, lead);
		return lead + (int) ((long) (t - lead) * middle / Math.max (1, target));
	}

	private static long mix (long h, int v) {
		return (h ^ v) * 0x100000001b3L;
	}

	/** Rasterizations so far (one per skin, part, state, tint and zoom while the caches are large enough). */
	public int rasterizations () {
		return rasterizations;
	}

	/** Compositions so far (one per size of a part while the caches are large enough). */
	public int compositions () {
		return compositions;
	}

	/** The name of a skin id. */
	public static synchronized String name (int skin) {
		return NAMES.get (skin);
	}
}
