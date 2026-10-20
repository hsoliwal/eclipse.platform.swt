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

import java.util.*;

import org.eclipse.swt.graphics.*;

/**
 * One frame of a drawn plane as the UI thread hands it to the render thread (brief rule 28, leaf R28-2): the frame's
 * damage, client size, rows top, zoom and theme; per row everything the renderer reads ({@link RowPaint#write}: the key
 * ints, the cell texts and the cell images as immutable pixel copies) and whether the render thread draws the row or
 * keeps it from its previous frame (rule 24); and the descriptors of the fonts the rows name by handle. A slab holds
 * immutable strings, immutable image copies and an immutable theme, never an SWT widget or resource, so the render thread
 * reads it without touching the UI thread's objects. Slabs live in a {@link TripleBuffer}: filled by the UI thread,
 * published, read by the render thread; the lanes grow when a frame needs more and are reused, so a frame allocates
 * nothing once they fit.
 */
public final class FrameSlab implements FontTable {
	/** Drawn: the render thread paints the row. */
	public static final int DRAW = -1;
	/** Kept whole from the previous frame. */
	public static final int KEEP = Integer.MAX_VALUE;
	/** Not a row: the empty area below the rows (its y and height), painted with the row's defaults. */
	public static final int EMPTY = Integer.MIN_VALUE;

	/** The frame's damage in client pixels, the client size and the top of the rows area. */
	public int damageX, damageY, damageWidth, damageHeight, clientWidth, clientHeight, rowsTop;
	/** The window's zoom (glyphs) and the theme the frame paints with. */
	public int zoom = 100;
	public Theme theme;
	/** False when every row must be drawn (a theme with its own row or cell painter). */
	public boolean retain = true;
	/** Rows in the frame, top to bottom. */
	public int rows;
	private int [] ints = new int [RowPaint.keyLength (4) * 64];
	private int [] intStart = new int [65], cellStart = new int [65], keep = new int [64];
	private String [] texts = new String [256];
	private ImageData [] images = new ImageData [256];
	private long [] fontHandles = new long [8];
	private FontDescriptor [] fonts = new FontDescriptor [8];
	private int fontCount, cellsUsed;

	/** Empties the slab for the next frame (the texts and images of the last one are let go). */
	public void clear () {
		Arrays.fill (texts, 0, cellsUsed, null);
		Arrays.fill (images, 0, cellsUsed, null);
		Arrays.fill (fonts, 0, fontCount, null);
		rows = fontCount = cellsUsed = 0;
		theme = null;
		retain = true;
	}

	/**
	 * Adds a row.
	 *
	 * @param row the row as the plane filled it; an SWT image of a cell must already be in its {@link RowPaint#imageData}
	 * @param keepRight {@link #DRAW}, {@link #KEEP}, the right end up to which the row is kept (a widened row: the strip
	 *        right of it is drawn), or {@link #EMPTY} for the empty area below the rows
	 */
	public void add (RowPaint row, int keepRight) {
		int cells = Math.max (1, row.cells), at = intStart [rows];
		if (rows + 2 > intStart.length) {
			intStart = Arrays.copyOf (intStart, intStart.length * 2);
			cellStart = Arrays.copyOf (cellStart, cellStart.length * 2);
			keep = Arrays.copyOf (keep, keep.length * 2);
		}
		if (at + RowPaint.keyLength (cells) > ints.length) ints = Arrays.copyOf (ints, Math.max (ints.length * 2, at + RowPaint.keyLength (cells)));
		if (cellsUsed + cells > texts.length) {
			texts = Arrays.copyOf (texts, Math.max (texts.length * 2, cellsUsed + cells));
			images = Arrays.copyOf (images, texts.length);
		}
		cellStart [rows] = cellsUsed;
		intStart [rows + 1] = at + row.write (ints, at, texts, images, cellsUsed);
		cellsUsed += row.cells;
		keep [rows++] = keepRight;
	}

	/** Fills a row with row <code>r</code> of the frame (its fields, texts and image pixels). */
	public void read (int r, RowPaint into) {
		into.read (ints, intStart [r], texts, images, cellStart [r]);
	}

	/** {@link #DRAW}, {@link #KEEP}, {@link #EMPTY} or the right end up to which row <code>r</code> is kept. */
	public int keep (int r) {
		return keep [r];
	}

	/** Records the descriptor of a font handle the rows name (once per handle). */
	public void font (long handle, FontDescriptor descriptor) {
		if (font (handle) != null) return;
		if (fontCount == fontHandles.length) {
			fontHandles = Arrays.copyOf (fontHandles, fontCount * 2);
			fonts = Arrays.copyOf (fonts, fontCount * 2);
		}
		fontHandles [fontCount] = handle;
		fonts [fontCount++] = descriptor;
	}

	/** The descriptor of a font handle the rows name, or null. */
	@Override
	public FontDescriptor font (long handle) {
		for (int i = 0; i < fontCount; i++) {
			if (fontHandles [i] == handle) return fonts [i];
		}
		return null;
	}

	/** The number of fonts recorded. */
	public int fonts () {
		return fontCount;
	}
}
