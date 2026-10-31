/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *     SAP SE and others - initial API and implementation
 *     Synexia (Hitesh Soliwal) - region clip from the region's own handle (GC seam, leaf 6), 2026
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.internal.win32.OS;

import jdk.m3.graphics.M3IRect;
import jdk.m3.graphics.M3Rect;

/**
 * An SWT {@link Region} as a clip of the M3 GC (GC seam, leaf 6; atom CLIP_STATE, pattern M3-GC-CLIP v1; census GF03, GF18).
 * <p>
 * The region's own handle at the GC's zoom ({@link Region#win32_getHandle}, the path of the stock
 * <code>GC.setClipping(Region)</code>) is read once with <code>GetRegionData</code> into device rectangles. The copy is taken
 * when the clip is set, so a later mutation or dispose of the caller's region does not reach the clip; translations,
 * negative coordinates and regions not built through logged operations (a region filled by <code>GC.getClipping</code>)
 * come out exactly as the stock region computes them at that zoom. The donor (I31 <code>SkiaRegionCalculator</code>)
 * replayed the <code>RegionLog</code>: live argument objects, a translate in points applied to pixels, polygons cut at 0,
 * an empty clip for an unlogged region.
 * </p>
 */
public final class M3RegionCalculator {
	/** <code>RGNDATAHEADER</code> in ints (dwSize, iType, nCount, nRgnSize, rcBound); the rectangles follow. */
	static final int HEADER = 8;

	private M3RegionCalculator() {
	}

	/** The region's rectangles at the zoom: left, top, right, bottom per rectangle, in pixels; empty for an empty region. */
	public static int[] rectangles(Region region, int zoom) {
		long handle = Region.win32_getHandle(region, zoom);
		int bytes = OS.GetRegionData(handle, 0, null);
		if (bytes <= 0) return new int[0];
		int[] data = new int[(bytes + 3) / 4];
		if (OS.GetRegionData(handle, bytes, data) == 0) return new int[0];
		int count = Math.max(0, Math.min(data[2], (data.length - HEADER) / 4));
		return Arrays.copyOfRange(data, HEADER, HEADER + 4 * count);
	}

	/** The bounds of the rectangles (pixels); an empty rectangle for none. */
	public static M3Rect bounds(int[] rects) {
		if (rects.length == 0) return new M3Rect(0, 0, 0, 0);
		return new M3Rect(edge(rects, 0, false), edge(rects, 1, false), edge(rects, 2, true), edge(rects, 3, true));
	}

	/** Clips the canvas to the rectangles (device space, as Skia's clipRegion; an empty list clips everything away). */
	static void clip(IM3Canvas canvas, int[] rects) {
		try (jdk.m3.graphics.M3Region region = new jdk.m3.graphics.M3Region()) {
			region.setRects(IntStream.range(0, rects.length / 4)
					.mapToObj(i -> new M3IRect(rects[4 * i], rects[4 * i + 1], rects[4 * i + 2], rects[4 * i + 3]))
					.toArray(M3IRect[]::new));
			canvas.clipRegion(region);
		}
	}

	private static int edge(int[] rects, int offset, boolean max) {
		IntStream values = IntStream.range(0, rects.length / 4).map(i -> rects[4 * i + offset]);
		return max ? values.max().orElse(0) : values.min().orElse(0);
	}
}
