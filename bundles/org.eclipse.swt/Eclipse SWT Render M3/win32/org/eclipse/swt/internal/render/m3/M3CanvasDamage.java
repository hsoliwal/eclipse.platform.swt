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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 5)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3;

import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.*;

import jdk.m3.graphics.M3Canvas;
import jdk.m3.graphics.M3Region;
import jdk.m3.graphics.M3IRect;
import jdk.m3.graphics.M3Rect;

/**
 * The damage of one <code>WM_PAINT</code> of an M3 canvas (GC seam, leaf 5; pattern M3-CANVAS-DAMAGE v1): the update
 * region as rectangles in client pixels, their bounds, and the Paint events the stock <code>Composite.WM_PAINT</code> of
 * this repository makes of them. By default one event whose bounds are the region's bounds, drawn clipped to the region
 * itself (the stock paint DC carries the region as its system clip); with {@link SWT#NO_MERGE_PAINTS} and more than one
 * rectangle one event per rectangle, clipped to it, whose <code>count</code> is the number of rectangles still to come.
 * <p>
 * No window handle: the handler reads the region (<code>GetUpdateRgn</code> before <code>BeginPaint</code> validates it,
 * then <code>GetRegionData</code>, as <code>ViewportDamageHook.record</code> does) and hands the data in, so the arithmetic
 * and the clip are testable without a Display.
 * </p>
 */
public final class M3CanvasDamage {
	/** <code>RGNDATAHEADER</code> in ints (dwSize, iType, nCount, nRgnSize, rcBound); the rectangles follow as left, top, right, bottom. */
	public static final int HEADER = 8;
	/* x, y, width, height per rectangle; reused across paints */
	private int [] rects = new int [4 * 8];
	private int count;
	private int left, top, right, bottom;

	/**
	 * Reads the rectangles of the region data intersected with the paint rectangle (<code>PAINTSTRUCT.rcPaint</code>);
	 * without data, or with no rectangle inside it, the damage is the paint rectangle itself.
	 *
	 * @param data <code>GetRegionData</code> output in ints, or null; <code>data [2]</code> is the rectangle count
	 */
	public void read (int [] data, int paintLeft, int paintTop, int paintRight, int paintBottom) {
		count = 0;
		left = top = Integer.MAX_VALUE;
		right = bottom = Integer.MIN_VALUE;
		int n = data == null || data.length < HEADER ? 0 : Math.max (0, Math.min (data [2], (data.length - HEADER) / 4));
		IntStream.range (0, n).map (i -> HEADER + 4 * i).forEach (o -> add (Math.max (data [o], paintLeft),
			Math.max (data [o + 1], paintTop), Math.min (data [o + 2], paintRight), Math.min (data [o + 3], paintBottom)));
		if (count == 0) add (paintLeft, paintTop, paintRight, paintBottom);
	}

	/* one rectangle given by its edges; an empty one is dropped */
	private void add (int l, int t, int r, int b) {
		if (r <= l || b <= t) return;
		if (rects.length < 4 * (count + 1)) rects = Arrays.copyOf (rects, rects.length * 2);
		int o = 4 * count++;
		rects [o] = l;
		rects [o + 1] = t;
		rects [o + 2] = r - l;
		rects [o + 3] = b - t;
		left = Math.min (left, l);
		top = Math.min (top, t);
		right = Math.max (right, r);
		bottom = Math.max (bottom, b);
	}

	/** The number of rectangles: at least one after a non-empty paint rectangle. */
	public int count () {
		return count;
	}

	/** The left edge of a rectangle. */
	public int x (int i) {
		return rects [4 * i];
	}

	/** The top edge of a rectangle. */
	public int y (int i) {
		return rects [4 * i + 1];
	}

	/** The width of a rectangle. */
	public int width (int i) {
		return rects [4 * i + 2];
	}

	/** The height of a rectangle. */
	public int height (int i) {
		return rects [4 * i + 3];
	}

	/** The number of Paint events: one per rectangle with <code>SWT.NO_MERGE_PAINTS</code> and several rectangles, else one. */
	public int passes (int style) {
		return (style & SWT.NO_MERGE_PAINTS) != 0 && count > 1 ? count : 1;
	}

	/** The bounds of a pass in client pixels, <code>{x, y, width, height}</code>: the region's bounds when merged, else the pass's rectangle. */
	public int [] bounds (int pass, int passes) {
		if (passes > 1) return new int [] {x (pass), y (pass), width (pass), height (pass)};
		if (count == 0) return new int [4];
		return new int [] {left, top, right - left, bottom - top};
	}

	/** The <code>Event.count</code> of a pass: the rectangles still to come with <code>SWT.NO_MERGE_PAINTS</code>, else 0. */
	public int eventCount (int pass, int passes) {
		return passes - 1 - pass;
	}

	/**
	 * Clips a canvas (device coordinates: the frame's identity matrix) to a pass: the whole region when merged (one
	 * rectangle as a rect clip, several as a region clip), else the pass's rectangle.
	 */
	public void clip (M3Canvas canvas, int pass, int passes) {
		if (passes > 1 || count == 1) {
			int i = passes > 1 ? pass : 0;
			canvas.clipRect (M3Rect.makeXYWH (x (i), y (i), width (i), height (i)));
			return;
		}
		try (M3Region region = new M3Region ()) {
			region.setRects (IntStream.range (0, count).mapToObj (i -> M3IRect.makeXYWH (x (i), y (i), width (i), height (i))).toArray (M3IRect []::new));
			canvas.clipRegion (region);
		}
	}
}
