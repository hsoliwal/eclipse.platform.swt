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
package org.eclipse.swt.internal.render.m3;

import org.eclipse.swt.internal.win32.*;

/**
 * TEXT_TRUNCATION v1 (real-app fidelity leaf): where <code>DrawText</code>'s <code>DT_END_ELLIPSIS</code> cuts a cell text,
 * measured by GDI on the cell's own font handle, as the native list view cuts it: the text is kept whole when its extent fits
 * the box, else the longest prefix whose extent fits the box minus the extent of "..." (GetTextExtentPoint32, integer
 * advances), at least one character. The library's fractional advances cut one character later ("#FFF..." where the stock System tab shows
 * "#FF..."). One memory DC, selected font remembered; the caller caches the cut per line and box width.
 */
public final class M3GdiEllipsis {
	private static final char [] DOTS = {'.', '.', '.'};
	private final SIZE size = new SIZE ();
	private long dc, selected;
	private char [] chars = new char [64];

	/**
	 * @param font the font handle (HFONT) the text is drawn with
	 * @param text the cell text
	 * @param width the text box width in pixels
	 * @return -1 when the text fits, else the number of leading characters kept before "..."
	 */
	public int cut (long font, String text, int width) {
		select (font);
		int length = load (text);
		if (extent (chars, length) <= width) return -1;
		/* DrawText keeps the first character even when it and "..." exceed the box ("#..." from 8 pixels on, EllipsisProbe) */
		return Math.max (1, fit (0, length, width - extent (DOTS, DOTS.length)));
	}

	/* the longest prefix in [low, high] whose extent fits the room (extents grow with the prefix) */
	private int fit (int low, int high, int room) {
		if (low >= high) return low;
		int middle = (low + high + 1) >>> 1;
		return extent (chars, middle) <= room ? fit (middle, high, room) : fit (low, middle - 1, room);
	}

	private int load (String text) {
		if (chars.length < text.length ()) chars = new char [text.length () * 2];
		text.getChars (0, text.length (), chars, 0);
		return text.length ();
	}

	private void select (long font) {
		if (dc == 0) dc = OS.CreateCompatibleDC (0);
		if (font != selected) OS.SelectObject (dc, font);
		selected = font;
	}

	private int extent (char [] text, int count) {
		if (count == 0) return 0;
		OS.GetTextExtentPoint32 (dc, text, count, size);
		return size.cx;
	}

	/** Releases the memory DC. */
	public void dispose () {
		if (dc != 0) OS.DeleteDC (dc);
		dc = 0;
		selected = 0;
	}
}
