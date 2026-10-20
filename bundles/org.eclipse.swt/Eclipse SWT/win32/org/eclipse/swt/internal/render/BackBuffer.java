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

import org.eclipse.swt.internal.win32.*;

/**
 * The retained frame of a drawn plane on win32: a top-down 32-bit DIB section selected into a memory DC, the
 * size of the plane's client area. It is recreated only when the size grows past it, so a frame allocates
 * nothing. Its pixels (<code>bits</code>, BGRA, <code>stride = width * 4</code>) are what the M3 backend wraps.
 */
public final class BackBuffer {
	/** The memory DC with the DIB selected, or 0 before the first {@link #ensure}. */
	public long dc;
	/** The DIB section and its pixels. */
	public long bitmap, bits;
	/** Allocated size in pixels. */
	public int width, height;
	/** Times the DC and DIB were (re)created (rule 24, viewport deltas: a resize step should reuse them). */
	public int creations;
	private long oldBitmap;

	/** The size class of a frame buffer: it grows in steps of this many pixels per axis. */
	public static final int SIZE_CLASS = 128;

	/**
	 * {@link #ensure} for a plane's frame, which a resize changes step by step (rule 24, viewport deltas): a buffer that
	 * must grow takes the next size class on both axes and never shrinks, so a resize drag re-creates it (and the
	 * surface over its pixels) once per class instead of once per step. A buffer that fits is reused as it is.
	 */
	public boolean ensureFrame (long referenceDC, int width, int height) {
		if (dc != 0 && width <= this.width && height <= this.height) return false;
		return ensure (referenceDC, sizeClass (Math.max (width, this.width)), sizeClass (Math.max (height, this.height)));
	}

	/**
	 * {@link #ensureFrame} that keeps the pixels (rule 24, viewport deltas): a buffer that must grow is made at the next size
	 * class and its pixels are copied in, so the rows it holds stay valid across a resize.
	 *
	 * @return true only when the buffer was made for the first time (it holds no pixels yet)
	 */
	public boolean growFrame (long referenceDC, int width, int height) {
		if (dc != 0 && width <= this.width && height <= this.height) return false;
		if (dc == 0) return ensureFrame (referenceDC, width, height);
		long oldDc = dc, oldDib = bitmap, oldSelected = oldBitmap;
		int oldWidth = this.width, oldHeight = this.height, made = creations;
		/* detached: ensure makes the new buffer without freeing the old one, which is copied, then freed */
		dc = 0;
		ensure (referenceDC, sizeClass (Math.max (width, oldWidth)), sizeClass (Math.max (height, oldHeight)));
		OS.BitBlt (dc, 0, 0, oldWidth, oldHeight, oldDc, 0, 0, OS.SRCCOPY);
		OS.SelectObject (oldDc, oldSelected);
		OS.DeleteObject (oldDib);
		OS.DeleteDC (oldDc);
		creations = made + 1;
		return false;
	}

	static int sizeClass (int pixels) {
		return (Math.max (1, pixels) + SIZE_CLASS - 1) / SIZE_CLASS * SIZE_CLASS;
	}

	/** Makes the buffer at least <code>width x height</code> (exactly that size when it is made); answers true when it was (re)created. */
	public boolean ensure (long referenceDC, int width, int height) {
		width = Math.max (1, width);
		height = Math.max (1, height);
		if (dc != 0 && width <= this.width && height <= this.height) return false;
		release ();
		BITMAPINFOHEADER header = new BITMAPINFOHEADER ();
		header.biSize = BITMAPINFOHEADER.sizeof;
		header.biWidth = width;
		header.biHeight = -height;
		header.biPlanes = 1;
		header.biBitCount = 32;
		header.biCompression = OS.BI_RGB;
		byte [] info = new byte [BITMAPINFOHEADER.sizeof];
		OS.MoveMemory (info, header, BITMAPINFOHEADER.sizeof);
		long [] pixels = new long [1];
		dc = OS.CreateCompatibleDC (referenceDC);
		bitmap = OS.CreateDIBSection (dc, info, OS.DIB_RGB_COLORS, pixels, 0, 0);
		bits = pixels [0];
		oldBitmap = OS.SelectObject (dc, bitmap);
		this.width = width;
		this.height = height;
		creations++;
		return true;
	}

	/** Copies a rectangle of the frame to a device context (same coordinates on both sides). */
	public void present (long targetDC, int x, int y, int width, int height) {
		OS.BitBlt (targetDC, x, y, width, height, dc, x, y, OS.SRCCOPY);
	}

	/** Frees the DC and the DIB. */
	public void release () {
		if (dc != 0) {
			OS.SelectObject (dc, oldBitmap);
			OS.DeleteObject (bitmap);
			OS.DeleteDC (dc);
		}
		dc = bitmap = bits = oldBitmap = 0;
		width = height = 0;
	}
}
