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

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * The mirrored drawing of a right-to-left drawn plane (docs/m3/theme-language/README.md): every backend (GC, M3 raster,
 * graphics hardware) draws in the plane's physical pixels, so the row renderer's boxes, given in the logical coordinates of
 * a mirrored window (x from the right edge), are mirrored here, <code>x -&gt; width - x - w</code>; text keeps its glyphs and
 * swaps left and right alignment, images keep their orientation, polygons (expanders) mirror point by point. While
 * {@link #mirror(int)} is 0 every call passes through unchanged (the stock left-to-right plane).
 */
//FLLDCIM: HPCPOP — one int test per primitive when not mirrored; no allocation (the polygon buffer grows only)
public final class MirroredCanvas implements BufferCanvas {
	private final BufferCanvas canvas;
	private int width;
	private int [] points = new int [16];

	/** @param canvas the backend that draws in physical pixels */
	public MirroredCanvas (BufferCanvas canvas) {
		this.canvas = canvas;
	}

	/** @param clientWidth the width to mirror around for the next frames, or 0 for no mirroring */
	public void mirror (int clientWidth) {
		width = clientWidth;
	}

	/** @return the width mirrored around, 0 when the canvas passes through */
	public int mirrored () {
		return width;
	}

	/** @return the backend */
	public BufferCanvas canvas () {
		return canvas;
	}

	private int x (int x, int w) {
		return width == 0 ? x : width - x - w;
	}

	/* MODE_TRUTH, BACKEND_REPORT: the mirroring is transparent to reports; the backend behind it answers */
	@Override
	public org.eclipse.swt.internal.WidgetMode route () {
		return canvas.route ();
	}

	@Override
	public String backend () {
		return canvas.backend ();
	}

	@Override
	public void target (BackBuffer buffer) {
		canvas.target (buffer);
	}

	@Override
	public GC gc (org.eclipse.swt.widgets.Control control, BackBuffer buffer) {
		return canvas.gc (control, buffer);
	}

	@Override
	public void clip (int x, int y, int w, int h) {
		canvas.clip (x (x, w), y, w, h);
	}

	@Override
	public void unclip () {
		canvas.unclip ();
	}

	@Override
	public void fonts (FontTable table) {
		canvas.fonts (table);
	}

	@Override
	public void begin (int x, int y, int w, int h) {
		canvas.begin (x (x, w), y, w, h);
	}

	@Override
	public void end () {
		canvas.end ();
	}

	@Override
	public void fill (int x, int y, int w, int h, int rgb) {
		canvas.fill (x (x, w), y, w, h, rgb);
	}

	@Override
	public void text (String text, long font, int rgb, int x, int y, int w, int h, int align) {
		canvas.text (text, font, rgb, x (x, w), y, w, h, width == 0 ? align : swap (align));
	}

	/* leading is right in a mirrored plane: LEFT and RIGHT trade places, CENTER and the ELLIPSIS bit stay */
	static int swap (int align) {
		int horizontal = align & ~ELLIPSIS, ellipsis = align & ELLIPSIS;
		return (horizontal == SWT.LEFT ? SWT.RIGHT : horizontal == SWT.RIGHT ? SWT.LEFT : horizontal) | ellipsis;
	}

	@Override
	public void image (Image image, int x, int y, int w, int h) {
		canvas.image (image, x (x, w), y, w, h);
	}

	@Override
	public void imageData (ImageData data, int x, int y, int w, int h) {
		canvas.imageData (data, x (x, w), y, w, h);
	}

	@Override
	public void polygon (int [] xy, int count, int rgb) {
		if (width == 0) {
			canvas.polygon (xy, count, rgb);
			return;
		}
		if (points.length < count * 2) points = new int [count * 2];
		for (int i = 0; i < count * 2; i += 2) {
			points [i] = width - xy [i];
			points [i + 1] = xy [i + 1];
		}
		canvas.polygon (points, count, rgb);
	}

	@Override
	public void part (int part, int state, int x, int y, int w, int h) {
		canvas.part (part, state, x (x, w), y, w, h);
	}

	@Override
	public void focus (int x, int y, int w, int h) {
		canvas.focus (x (x, w), y, w, h);
	}

	@Override
	public void dispose () {
		canvas.dispose ();
	}
}
