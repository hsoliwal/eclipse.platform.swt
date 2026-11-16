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
package org.eclipse.swt.internal.android;

import android.graphics.Bitmap;

/**
 * Moves a rectangle of pixels inside one bitmap; source and destination may overlap.
 *
 * With the native library (<code>swtpixels</code>, built from <code>swt_pixels.c</code> against the NDK's
 * <code>android/bitmap.h</code>) this is one call that locks the bitmap and moves its rows in place. Without
 * the library the rows cross the framework through one reusable int lane. Both rectangles must lie inside the
 * bitmap. Display thread only.
 */
final class AndroidPixels {
	/** At most this many pixels cross the framework per step of the Java lane. */
	private static final int LANE_PIXELS = 1 << 16;
	private static final boolean NATIVE = load();
	private static int[] lane = new int[0];

	private AndroidPixels() {
	}

	private static boolean load() {
		try {
			System.loadLibrary("swtpixels");
			return true;
		} catch (UnsatisfiedLinkError | SecurityException missing) {
			return false;
		}
	}

	/** Whether the native move is in use. */
	static boolean isNative() {
		return NATIVE;
	}

	static void move(Bitmap bitmap, int x, int y, int width, int height, int dx, int dy) {
		if (width <= 0 || height <= 0 || dx == 0 && dy == 0) return;
		if (NATIVE && moveNative(bitmap, x, y, width, height, dx, dy)) return;
		moveByLane(bitmap, x, y, width, height, dx, dy);
	}

	/** One native call; false when the bitmap is not 32 bits per pixel or cannot be locked. */
	private static native boolean moveNative(Bitmap bitmap, int x, int y, int width, int height, int dx, int dy);

	/*
	 * Tile wide rectangles as well as tall ones so no step ever crosses more than LANE_PIXELS through
	 * the Java/Bitmap boundary. Vertical bands follow memmove order for dy; horizontal tiles go
	 * right-to-left when dx > 0, otherwise left-to-right. This preserves overlapping copies even
	 * for a rectangle wider than the reusable pixel lane, without allocating a width-sized array.
	 */
	static void moveByLane(Bitmap bitmap, int x, int y, int width, int height, int dx, int dy) {
		if (width <= 0 || height <= 0 || dx == 0 && dy == 0) return;
		int columns = Math.min(width, LANE_PIXELS);
		int rows = Math.max(1, LANE_PIXELS / columns);
		int required = rows * columns; // <= LANE_PIXELS even when width is enormous
		if (lane.length < required) lane = new int[required];
		if (dy > 0) {
			for (int bottom = y + height; bottom > y;) {
				int count = Math.min(rows, bottom - y);
				int top = bottom - count;
				copyBand(bitmap, x, top, width, count, dx, dy, columns);
				bottom = top;
			}
		} else {
			for (int top = y, end = y + height; top < end;) {
				int count = Math.min(rows, end - top);
				copyBand(bitmap, x, top, width, count, dx, dy, columns);
				top += count;
			}
		}
	}

	private static void copyBand(Bitmap bitmap, int x, int top, int width, int count,
			int dx, int dy, int columns) {
		if (dx > 0) {
			for (int right = width; right > 0;) {
				int segment = Math.min(columns, right);
				int left = right - segment;
				step(bitmap, x + left, top, segment, count, dx, dy);
				right = left;
			}
		} else {
			for (int left = 0; left < width;) {
				int segment = Math.min(columns, width - left);
				step(bitmap, x + left, top, segment, count, dx, dy);
				left += segment;
			}
		}
	}

	private static void step(Bitmap bitmap, int x, int top, int width, int count, int dx, int dy) {
		bitmap.getPixels(lane, 0, width, x, top, width, count);
		bitmap.setPixels(lane, 0, width, x + dx, top + dy, width, count);
	}
}
