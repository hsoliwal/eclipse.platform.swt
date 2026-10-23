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

import org.eclipse.swt.*;
import org.eclipse.swt.internal.win32.*;

/**
 * Brief rule 24, delta based view: the rows a drawn frame keeps instead of drawing. Per row slot (a row's place in the
 * rows area) it remembers what was drawn there: the row's key ({@link RowPaint#key}: everything the renderer reads,
 * compared exactly) and the theme. A row whose key and theme are its slot's is kept; a row that differs only in width
 * (a resize) keeps its left part and draws the strip on its right ({@link BufferCanvas#clip}); any other row is drawn.
 * A slot is remembered only for a row drawn whole under a damage as wide as the client; a slot in the damage that no row
 * claimed is forgotten.
 * <p>
 * Two placements of the kept pixels. On the UI thread (stage 1) the frame is drawn over the behaviour pass, so the kept
 * bands are copied back from a separate retained frame, which then takes the damage as presented. On the render thread
 * (rule 28) the frame is drawn in place into the render thread's canonical frame, which already holds the kept rows:
 * nothing is copied. Whichever thread draws owns its instance, so a dropped snapshot never desynchronizes the slots from
 * the pixels.
 * </p>
 */
public final class RetainedRows {
	/* the rows area holds at most this many slots: taller planes draw the rows beyond it every time */
	private static final int SLOTS = 512;
	/* the retained frame of the UI-thread placement; null in place */
	private final BackBuffer retained;
	private int [][] slotKey = new int [0][];
	private String [][] slotText = new String [0][];
	private Theme [] slotTheme = new Theme [0];
	private int [] slotLength = new int [0], slotY = new int [0], slotHeight = new int [0];
	/* the right end of the pixels kept for a slot's row */
	private int [] slotRight = new int [0];
	private long [] slotFrame = new long [0];
	private int [] key = new int [RowPaint.keyLength (4)];
	private String [] keyText = new String [4];
	private int [] keptY = new int [32], keptHeight = new int [32], keptRight = new int [32];
	private BufferCanvas canvas;
	private RowRenderer renderer;
	private BackBuffer target;
	private int left, top, right, bottom, clientRight, rowsTop;
	private boolean retain;
	private long frameNumber;
	/** The current frame's counts: rows painted ({@link #paint} calls), drawn whole, kept (whole or up to a right end). */
	public int rowsIterated, rowsDrawn, kept;

	/** @param inPlace true when frames are drawn into the kept pixels themselves (the render thread's canonical frame) */
	public RetainedRows (boolean inPlace) {
		retained = inPlace ? null : new BackBuffer ();
	}

	/** Sizes the retained frame like the frame buffer (UI-thread placement); a new one keeps no row. */
	public void ensure (long referenceDC, int width, int height) {
		/* a frame that grows keeps its pixels (BackBuffer.growFrame): a resize keeps its rows */
		if (retained != null && retained.growFrame (referenceDC, width, height)) forget ();
	}

	/**
	 * Starts a frame.
	 *
	 * @param retain false when the rows must all be drawn (a theme with its own row or cell painter, the overlay)
	 */
	public void begin (BufferCanvas canvas, RowRenderer renderer, BackBuffer target, int left, int top, int right, int bottom, int clientRight, int rowsTop, boolean retain) {
		this.canvas = canvas;
		this.renderer = renderer;
		this.target = target;
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.clientRight = clientRight;
		this.rowsTop = rowsTop;
		this.retain = retain;
		frameNumber++;
		kept = rowsIterated = rowsDrawn = 0;
	}

	/** Paints a row of the frame, or keeps it when its slot shows it already. */
	public void paint (RowPaint row) {
		rowsIterated++;
		int cells = Math.max (1, row.cells);
		if (key.length < RowPaint.keyLength (cells)) key = new int [RowPaint.keyLength (cells) * 2];
		if (keyText.length < cells) keyText = new String [cells * 2];
		int length = retain ? row.key (key, keyText) : -1;
		int slot = slot (row);
		if (slot >= 0 && length >= 0 && same (slot, length, row)) {
			keep (row, Integer.MAX_VALUE);
			slotFrame [slot] = frameNumber;
			return;
		}
		if (slot >= 0 && length >= 0 && widened (slot, length, row)) {
			/* rule 24, viewport deltas: only the width changed (a resize); keep the left part, draw the strip on its right */
			int keepRight = Math.min (slotRight [slot], Math.min (row.x + row.width, clientRight));
			keep (row, keepRight);
			canvas.clip (keepRight, row.y, Math.max (0, right - keepRight), row.height);
			try {
				renderer.paint (canvas, row);
			} finally {
				canvas.unclip ();
			}
		} else {
			renderer.paint (canvas, row);
			rowsDrawn++;
		}
		if (slot < 0) return;
		slotFrame [slot] = frameNumber;
		boolean whole = length >= 0 && left <= 0 && right >= clientRight && row.y >= top && row.y + row.height <= bottom;
		if (whole) remember (slot, length, row); else slotLength [slot] = -1;
	}

	/*
	 * Ends the frame (after the canvas's): in the UI-thread placement the kept bands come back from the retained frame
	 * over the behaviour pass and the retained frame takes the damage as it will be presented; slots in the damage that
	 * no row claimed are forgotten.
	 */
	public void end () {
		int width = right - left;
		if (retained != null) {
			for (int i = 0; i < kept; i++) {
				int bandTop = Math.max (keptY [i], top), bandBottom = Math.min (keptY [i] + keptHeight [i], bottom);
				int bandRight = Math.min (right, keptRight [i]);
				if (bandBottom > bandTop && bandRight > left) OS.BitBlt (target.dc, left, bandTop, bandRight - left, bandBottom - bandTop, retained.dc, left, bandTop, OS.SRCCOPY);
			}
		}
		for (int slot = 0; slot < slotLength.length; slot++) {
			if (slotLength [slot] >= 0 && slotFrame [slot] != frameNumber && slotY [slot] < bottom && slotY [slot] + slotHeight [slot] > top) slotLength [slot] = -1;
		}
		if (retained != null) OS.BitBlt (retained.dc, left, top, width, bottom - top, target.dc, left, top, OS.SRCCOPY);
		canvas = null;
		renderer = null;
		target = null;
	}

	/** Forgets every slot: the next frame draws every row (the kept pixels are gone or no longer what a key says). */
	public void forget () {
		Arrays.fill (slotLength, -1);
	}

	/** Frees the retained frame. */
	public void release () {
		if (retained != null) retained.release ();
	}

	/* a row kept, up to a right end (the strip right of it is drawn) */
	private void keep (RowPaint row, int keepRight) {
		if (kept == keptY.length) {
			keptY = Arrays.copyOf (keptY, kept * 2);
			keptHeight = Arrays.copyOf (keptHeight, kept * 2);
			keptRight = Arrays.copyOf (keptRight, kept * 2);
		}
		keptY [kept] = row.y;
		keptHeight [kept] = row.height;
		keptRight [kept++] = keepRight;
	}

	/*
	 * True when the row differs from its slot only in width: the row's width and selection width, and the last cell's
	 * box and text widths (a list box row spans the client). Its left part is then the pixels the slot holds, unless
	 * something is anchored at the right end: a focus rectangle (the caret row), an ellipsis, or a last cell that is not
	 * left-aligned.
	 */
	private boolean widened (int slot, int length, RowPaint row) {
		if (slotLength [slot] != length || slotTheme [slot] != renderer.theme ()) return false;
		if ((row.state & RowPaint.CARET) != 0) return false;
		int last = 26 + 11 * (Math.max (1, row.cells) - 1);
		int align = key [last + 6];
		int [] remembered = slotKey [slot];
		/* an ellipsis or a right or centre alignment moves only when the last cell's box changes (a list box row), not with the row (a list view row) */
		boolean lastCellWidened = remembered [last + 1] != key [last + 1] || remembered [last + 3] != key [last + 3];
		if (lastCellWidened && ((row.state & RowPaint.ELLIPSIS) != 0 || align != 0 && align != SWT.LEFT)) return false;
		for (int i = 0; i < length; i++) {
			if (remembered [i] == key [i] || i == 2 || i == 5 || i == last + 1 || i == last + 3) continue;
			return false;
		}
		return sameTexts (slot, row);
	}

	/* the slot of the row's place, or -1; a slot that held another place forgets it */
	private int slot (RowPaint row) {
		if (row.height <= 0 || row.y < rowsTop) return -1;
		int slot = (row.y - rowsTop) / row.height;
		if (slot >= SLOTS) return -1;
		if (slot >= slotLength.length) growSlots (slot + 1);
		if (slotY [slot] != row.y || slotHeight [slot] != row.height) slotLength [slot] = -1;
		return slot;
	}

	private boolean same (int slot, int length, RowPaint row) {
		if (slotLength [slot] != length || slotTheme [slot] != renderer.theme ()) return false;
		int [] remembered = slotKey [slot];
		for (int i = 0; i < length; i++) {
			if (remembered [i] != key [i]) return false;
		}
		return sameTexts (slot, row);
	}

	private boolean sameTexts (int slot, RowPaint row) {
		String [] texts = slotText [slot];
		for (int i = 0; i < row.cells; i++) {
			if (!Objects.equals (texts [i], keyText [i])) return false;
		}
		return true;
	}

	private void remember (int slot, int length, RowPaint row) {
		if (slotKey [slot] == null || slotKey [slot].length < length) slotKey [slot] = new int [key.length];
		System.arraycopy (key, 0, slotKey [slot], 0, length);
		if (slotText [slot] == null || slotText [slot].length < row.cells) slotText [slot] = new String [keyText.length];
		System.arraycopy (keyText, 0, slotText [slot], 0, row.cells);
		slotTheme [slot] = renderer.theme ();
		slotY [slot] = row.y;
		slotHeight [slot] = row.height;
		slotRight [slot] = Math.min (row.x + row.width, clientRight);
		slotLength [slot] = length;
	}

	private void growSlots (int count) {
		int n = Math.min (SLOTS, Math.max (count, slotLength.length * 2));
		int old = slotLength.length;
		slotKey = Arrays.copyOf (slotKey, n);
		slotText = Arrays.copyOf (slotText, n);
		slotTheme = Arrays.copyOf (slotTheme, n);
		slotLength = Arrays.copyOf (slotLength, n);
		slotY = Arrays.copyOf (slotY, n);
		slotHeight = Arrays.copyOf (slotHeight, n);
		slotRight = Arrays.copyOf (slotRight, n);
		slotFrame = Arrays.copyOf (slotFrame, n);
		Arrays.fill (slotLength, old, n, -1);
		Arrays.fill (slotY, old, n, Integer.MIN_VALUE);
	}
}
