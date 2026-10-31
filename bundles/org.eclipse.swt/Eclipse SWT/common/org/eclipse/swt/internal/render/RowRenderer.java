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
 * Paints one row of a drawn plane (Tree, Table or List) through a {@link RowCanvas}, in a fixed order: row
 * background, cell backgrounds, selection, expander, check box, per cell image and text, grid lines, focus.
 * One renderer serves every row widget; the theme decides the colours and how selection, expanders and check
 * boxes look (platform parts, Swing Metal, or Initiative 31). The renderer holds no widget state; it reads the
 * {@link RowPaint} its plane filled.
 */
public final class RowRenderer {
	/** Glyphs kept per thread for every renderer: seven glyphs at a few zooms and tints. */
	static final int GLYPHS = 64;
	/*
	 * The SVG glyphs of every renderer of a thread (the UI thread), keyed by (glyph, size, zoom, tint): the renderers of
	 * all widgets share them, and a theme or look and feel switch (a new renderer) rasterizes only tints it never drew.
	 */
	private static final ThreadLocal<SharedGlyphs> SHARED = ThreadLocal.withInitial (SharedGlyphs::new);
	private final Theme theme;
	/* the thread's shared glyphs, taken on first use */
	private SharedGlyphs glyphs;

	/* a glyph cache whose rasterizer tints with the tint of the request in progress; null cache without an SVG rasterizer */
	static final class SharedGlyphs {
		final GlyphCache cache;
		int tint;

		SharedGlyphs () {
			GlyphCache.Rasterizer rasterizer = Glyphs.rasterizer (glyph -> tint);
			cache = rasterizer == null ? null : new GlyphCache (GLYPHS, rasterizer);
		}

		ImageData get (int id, int zoom, int rgb) {
			tint = rgb & 0xFFFFFF;
			return cache.get (id, 16, zoom, tint);
		}
	}

	public RowRenderer (Theme theme) {
		this.theme = theme;
	}

	/** The theme this renderer paints with. */
	public Theme theme () {
		return theme;
	}

	/** The SVG rasterizations of this thread's shared glyphs so far (-1 without an SVG rasterizer). */
	public static int rasterizations () {
		GlyphCache cache = SHARED.get ().cache;
		return cache == null ? -1 : cache.rasterizations ();
	}

	/* a row painter the plane installs for its frames over the theme's: the owner-draw protocol (DrawnCustomDraw); null: none */
	private Theme.RowPainter override;

	/**
	 * Installs a row painter for the frames to come, over the theme's (null: the theme's again): a plane whose widget hooks
	 * <code>MeasureItem</code>, <code>EraseItem</code> or <code>PaintItem</code> paints its rows through the owner-draw
	 * protocol, which interleaves the events with the default pieces ({@link #background}, {@link #selection},
	 * {@link #paintCell} and the others).
	 */
	public void override (Theme.RowPainter painter) {
		override = painter;
	}

	/** Paints a row: the installed or the theme's row painter when there is one, else {@link #paintDefault}. */
	public void paint (RowCanvas canvas, RowPaint row) {
		ThreadRole.MODEL.forbid ("RowRenderer.paint");
		Theme.RowPainter painter = override != null ? override : theme.rowPainter;
		if (painter != null) {
			painter.paint (canvas, row, theme, this);
		} else {
			paintDefault (canvas, row);
		}
	}

	/** Paints a row in the default order: background, selection, expander, check box, cells, grid lines, focus. */
	public void paintDefault (RowCanvas canvas, RowPaint row) {
		background (canvas, row);
		selection (canvas, row);
		expander (canvas, row);
		checkBox (canvas, row);
		for (int cell = 0; cell < row.cells; cell++) {
			if (theme.cellPainter != null) {
				theme.cellPainter.paint (canvas, row, cell, theme);
			} else {
				paintCell (canvas, row, cell);
			}
		}
		gridLines (canvas, row);
		focus (canvas, row);
	}

	/** Fills the area of a plane that has no row (below the last row). */
	public void paintEmpty (RowCanvas canvas, RowPaint frame, int x, int y, int width, int height) {
		canvas.fill (x, y, width, height, background (frame));
		/* GRID_LINES v1: the themed report view continues its column detail through the empty area below the rows */
		columnDetail (canvas, frame, y, height);
	}

	/* GRID_LINES v1: a themed report view without grid lines: column detail on each cell's last pixel */
	private boolean detailed (RowPaint row) {
		return theme.parts && row.is (RowPaint.COLUMN_DETAIL) && !row.is (RowPaint.GRID_LINES) && row.listColumnDetail != -1;
	}

	/* GRID_LINES v1: there a cell's own colour ends two pixels before its edge (the column detail and one pixel of the row's background) */
	private int cellFillWidth (RowPaint row, int cell) {
		return detailed (row) ? Math.max (0, row.cellWidth [cell] - 2) : row.cellWidth [cell];
	}

	private int background (RowPaint row) {
		return theme.background != -1 ? theme.background : row.background;
	}

	private int foreground (RowPaint row) {
		return theme.foreground != -1 ? theme.foreground : row.foreground;
	}

	private void background (RowCanvas canvas, RowPaint row) {
		rowBackground (canvas, row);
		for (int cell = 0; cell < row.cells; cell++) cellBackground (canvas, row, cell);
	}

	/** Fills the row rectangle with the row's background (the theme's or the widget's); the cells' own colours are {@link #cellBackground}. */
	public void rowBackground (RowCanvas canvas, RowPaint row) {
		canvas.fill (row.x, row.y, row.width, row.height, background (row));
	}

	/** Fills a cell with its own background colour, or the sort column's, when it has one (what <code>SWT.BACKGROUND</code> offers an <code>EraseItem</code> listener). */
	public void cellBackground (RowCanvas canvas, RowPaint row, int cell) {
		int color = row.cellBackground [cell];
		if (color == -1 && cell == row.sortCell) color = row.sortBackground;
		if (color != -1) canvas.fill (row.cellX [cell], row.y, cellFillWidth (row, cell), row.height, color);
	}

	/** Paints the selection (or hover) of a selected, drop-target or hot row over its selection rectangle. */
	public void selection (RowCanvas canvas, RowPaint row) {
		boolean selected = row.is (RowPaint.SELECTED) || row.is (RowPaint.DROP), hot = row.is (RowPaint.HOT);
		if (!selected && !hot || row.selectionWidth <= 0) return;
		if (theme.parts && row.is (RowPaint.SYSTEM_SELECTION)) {
			if (selected) canvas.fill (row.selectionX, row.y, row.selectionWidth, row.height, row.systemSelectionBackground);
		} else if (theme.parts) {
			int state = selected ? hot ? RowCanvas.SELECTION_HOT_SELECTED : row.is (RowPaint.FOCUSED) ? RowCanvas.SELECTION_SELECTED : RowCanvas.SELECTION_NO_FOCUS : RowCanvas.SELECTION_HOT;
			canvas.part (RowCanvas.PART_SELECTION, state, row.selectionX, row.y, row.selectionWidth, row.height);
		} else {
			/* Initiative 31 paints the selection over the hover; Swing has no hover */
			int color = selected ? row.is (RowPaint.FOCUSED) ? theme.selectionBackground : theme.selectionInactiveBackground : theme.hotBackground;
			canvas.fill (row.selectionX, row.y, row.selectionWidth, row.height, color);
		}
	}

	/** Paints the expander of a row with children in its slot. */
	public void expander (RowCanvas canvas, RowPaint row) {
		if (!row.is (RowPaint.CHILDREN) || row.expanderWidth <= 0) return;
		boolean open = row.is (RowPaint.EXPANDED);
		if (theme.expanderOpen != null) {
			glyph (canvas, open ? theme.expanderOpen : theme.expanderClosed, row.expanderX, row.y, row.expanderWidth, row.height);
			return;
		}
		if (theme.vectorGlyphs && vector (canvas, open ? Glyphs.CHEVRON_DOWN : Glyphs.CHEVRON_RIGHT, row, row.expanderX, row.expanderWidth)) return;
		switch (theme.look) {
			case Theme.LOOK_NATIVE -> {
				int state = (open ? RowCanvas.EXPANDER_OPEN : RowCanvas.EXPANDER_CLOSED) | (row.is (RowPaint.HOT) ? RowCanvas.HOT : 0);
				canvas.part (RowCanvas.PART_EXPANDER, state, row.expanderX, row.y, row.expanderWidth, row.height);
			}
			case Theme.LOOK_INITIATIVE31 -> Initiative31Look.expander (canvas, theme, row, open);
			default -> metalHandle (canvas, row, open);
		}
	}

	/* Metal handle: a 9 x 9 box with a minus (open) or a plus (closed), and the line to the label */
	private void metalHandle (RowCanvas canvas, RowPaint row, boolean open) {
		int size = 9, cx = row.expanderX + row.expanderWidth / 2, cy = row.y + row.height / 2;
		int left = cx - size / 2, top = cy - size / 2;
		canvas.fill (cx + size / 2 + 1, cy, Math.max (0, row.expanderX + row.expanderWidth - (cx + size / 2 + 1)), 1, theme.hash);
		canvas.fill (left, top, size, size, theme.border);
		canvas.fill (left + 1, top + 1, size - 2, size - 2, 0xFFFFFF);
		canvas.fill (left + 2, cy, size - 4, 1, theme.foreground);
		if (!open) canvas.fill (cx, top + 2, 1, size - 4, theme.foreground);
	}

	/** Paints the check box of a row in its slot. */
	public void checkBox (RowCanvas canvas, RowPaint row) {
		if (!row.is (RowPaint.CHECKBOX) || row.checkWidth <= 0) return;
		boolean checked = row.is (RowPaint.CHECKED), grayed = row.is (RowPaint.GRAYED);
		if (theme.checkOn != null) {
			glyph (canvas, checked ? grayed ? theme.checkMixed : theme.checkOn : theme.checkOff, row.checkX, row.y, row.checkWidth, row.height);
			return;
		}
		if (theme.vectorGlyphs && vector (canvas, checked ? grayed ? Glyphs.CHECK_MIXED : Glyphs.CHECK_ON : Glyphs.CHECK_OFF, row, row.checkX, row.checkWidth)) return;
		switch (theme.look) {
			case Theme.LOOK_NATIVE -> {
				int state = checked ? grayed ? RowCanvas.CHECKBOX_MIXED : RowCanvas.CHECKBOX_CHECKED : RowCanvas.CHECKBOX_UNCHECKED;
				canvas.part (RowCanvas.PART_CHECKBOX, state, row.checkX, row.y, row.checkWidth, row.height);
			}
			case Theme.LOOK_INITIATIVE31 -> Initiative31Look.checkBox (canvas, theme, row, checked, grayed);
			default -> metalCheckBox (canvas, row, checked, grayed);
		}
	}

	/* Metal check box: 13 x 13, border, white, a check mark of two strokes */
	private void metalCheckBox (RowCanvas canvas, RowPaint row, boolean checked, boolean grayed) {
		int size = Math.min (13, Math.min (row.checkWidth, row.height) - 2);
		int left = row.checkX + (row.checkWidth - size) / 2, top = row.y + (row.height - size) / 2;
		canvas.fill (left, top, size, size, theme.border);
		canvas.fill (left + 1, top + 1, size - 2, size - 2, grayed ? 0xEEEEEE : 0xFFFFFF);
		if (!checked) return;
		int mark = grayed ? 0x808080 : theme.checkFill;
		for (int i = 0; i < 3; i++) canvas.fill (left + 3 + i, top + size / 2 + i - 1, 1, 2, mark);
		for (int i = 0; i < size - 7; i++) canvas.fill (left + 6 + i, top + size / 2 + 1 - i, 1, 2, mark);
	}

	/* a theme glyph image, at its own size, centred in its slot */
	private static void glyph (RowCanvas canvas, ImageData image, int x, int y, int width, int height) {
		canvas.imageData (image, x + (width - image.width) / 2, y + (height - image.height) / 2, image.width, image.height);
	}

	/*
	 * An SVG glyph of the built-in set, 16 points rasterized at the row's zoom and tinted with the theme (chevrons in the
	 * expander colour, check boxes in the check fill or the border), centred in its slot; false when no SVG rasterizer is
	 * installed (the look then draws its own).
	 */
	private boolean vector (RowCanvas canvas, int id, RowPaint row, int x, int width) {
		if (glyphs == null) glyphs = SHARED.get ();
		if (glyphs.cache == null) return false;
		int tint = id >= Glyphs.CHECK_OFF && id <= Glyphs.CHECK_MIXED && theme.checkFill != -1 ? theme.checkFill : theme.border;
		ImageData image = glyphs.get (id, row.zoom, tint);
		if (image == null) return false;
		glyph (canvas, image, x, row.y, width, row.height);
		return true;
	}

	/* ROW_IMAGE_BOUNDS v1 (real-app fidelity): the widget's image size (its image list), not the row's height; unknown: the row's */
	private static int imageHeight (RowPaint row) {
		return row.imageHeight > 0 && row.imageHeight < row.height ? row.imageHeight : row.height;
	}

	/* ROW_IMAGE_BOUNDS v1: the image box centred in the row as the native list and tree views draw it (rounded down) */
	private static int imageY (RowPaint row) {
		return row.y + (row.height - imageHeight (row)) / 2;
	}

	/** Paints the default content of a cell: its image, then its text (for cell painters that add to it). */
	public void paintCell (RowCanvas canvas, RowPaint row, int cell) {
		if (row.imageData [cell] != null && row.imageWidth [cell] > 0) {
			/* a row read back from a snapshot: the image's pixels at the frame's zoom */
			canvas.imageData (row.imageData [cell], row.imageX [cell], imageY (row), row.imageWidth [cell], imageHeight (row));
		} else if (row.image [cell] != null && row.imageWidth [cell] > 0) {
			canvas.image (row.image [cell], row.imageX [cell], imageY (row), row.imageWidth [cell], imageHeight (row));
		}
		String text = row.text [cell];
		if (text == null || text.isEmpty () || row.textWidth [cell] <= 0) return;
		int color = row.cellForeground [cell] != -1 ? row.cellForeground [cell] : foreground (row);
		if (theme.parts && row.is (RowPaint.SYSTEM_SELECTION) && row.is (RowPaint.SELECTED)) color = row.systemSelectionForeground;
		if (theme.selectionForeground != -1 && row.is (RowPaint.SELECTED)) color = theme.selectionForeground;
		long font = row.cellFont [cell] != -1 ? row.cellFont [cell] : row.font;
		int align = row.align [cell] == 0 ? SWT.LEFT : row.align [cell];
		int inset = align == SWT.LEFT ? theme.textInset : 0;
		int width = row.textWidth [cell] - inset;
		if (width <= 0) return;
		canvas.text (text, font, color, row.textX [cell] + inset, row.y, width, row.height - row.textInset,
			align | (row.is (RowPaint.ELLIPSIS) ? RowCanvas.ELLIPSIS : 0));
	}

	/** Paints the grid lines of a row that shows them. */
	public void gridLines (RowCanvas canvas, RowPaint row) {
		if (!row.is (RowPaint.GRID_LINES)) {
			columnDetail (canvas, row, row.y, row.height);
			return;
		}
		if (theme.parts && row.listGrid != -1) {
			listGridLines (canvas, row);
			return;
		}
		for (int cell = 0; cell < row.cells; cell++) {
			canvas.fill (row.cellX [cell] + row.cellWidth [cell] - 1, row.y, 1, row.height, theme.grid);
		}
		canvas.fill (row.x, row.y + row.height - 1, row.width, 1, theme.grid);
	}

	/*
	 * GRID_LINES v1 (real-app fidelity): a themed list view with columns draws its column detail line on each cell's last
	 * pixel also without grid lines (stock: ebf4fe at every column edge of the System and Table tabs, Windows 11 light)
	 */
	private void columnDetail (RowCanvas canvas, RowPaint row, int y, int height) {
		if (!detailed (row)) return;
		java.util.stream.IntStream.range (0, row.cells).forEach (cell ->
			canvas.fill (row.cellX [cell] + row.cellWidth [cell] - 1, y, 1, height, row.listColumnDetail));
	}

	/* the native look of a list view's grid: the column detail on each cell's last pixel, the column line one pixel right of the cell, the row line over both */
	private static void listGridLines (RowCanvas canvas, RowPaint row) {
		for (int cell = 0; cell < row.cells; cell++) {
			int right = row.cellX [cell] + row.cellWidth [cell];
			if (row.listColumnDetail != -1) canvas.fill (right - 1, row.y, 1, row.height, row.listColumnDetail);
			canvas.fill (right, row.y, 1, row.height, row.listGrid);
		}
		canvas.fill (row.x, row.y + row.height - 1, row.width, 1, row.listGrid);
	}

	/** Paints the focus rectangle of the caret row of the focus control (what <code>SWT.FOCUSED</code> offers an <code>EraseItem</code> listener). */
	public void focus (RowCanvas canvas, RowPaint row) {
		if (theme.look == Theme.LOOK_INITIATIVE31) {
			/* Initiative 31 draws the focus on every selected row of the focus control */
			if (row.is (RowPaint.SELECTED) && row.is (RowPaint.FOCUSED) && row.selectionWidth > 0) canvas.focus (row.selectionX, row.y, row.selectionWidth, row.height);
			return;
		}
		if (!row.is (RowPaint.CARET) || !row.is (RowPaint.FOCUSED) || !row.is (RowPaint.FOCUS_CUES) || row.selectionWidth <= 0) return;
		if (theme.parts) {
			canvas.focus (row.selectionX, row.y, row.selectionWidth, row.height);
		} else {
			int x = row.selectionX, y = row.y, w = row.selectionWidth, h = row.height, c = theme.focusColor;
			canvas.fill (x, y, w, 1, c);
			canvas.fill (x, y + h - 1, w, 1, c);
			canvas.fill (x, y, 1, h, c);
			canvas.fill (x + w - 1, y, 1, h, c);
		}
	}
}
