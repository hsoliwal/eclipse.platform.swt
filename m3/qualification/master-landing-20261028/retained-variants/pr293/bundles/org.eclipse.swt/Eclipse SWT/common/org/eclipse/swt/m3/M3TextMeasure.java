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
package org.eclipse.swt.m3;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * Text widths in pixels, measured once per text and font (enhanced M3 API).
 * <p>
 * A width is <code>GC.textExtent (text).x</code> with the font, measured on the drawable the
 * measure was made for. Each text and font pair is measured once; a repeat is answered from an
 * open-addressing table of int lanes (no object per entry). The table holds at most
 * {@link #capacity()} entries and starts again empty when it is full. Measuring opens one
 * <code>GC</code> on the first miss and keeps it until {@link #release()}, so a scan over many
 * texts opens one GC.
 * </p>
 *
 * @since 3.138
 */
public final class M3TextMeasure {
	/** Index in {@link #stats()}: the texts measured with a GC. */
	public static final int MEASURED = 0;
	/** Index in {@link #stats()}: the answers given from the table. */
	public static final int HITS = 1;
	/** Index in {@link #stats()}: the entries in the table. */
	public static final int ENTRIES = 2;
	private final Drawable drawable;
	private final int capacity;
	private String [] texts;
	private Font [] fonts;
	private int [] widths;
	private int size;
	private long measured, hits;
	private GC gc;
	private Font gcFont;

	/**
	 * Makes a measure for a drawable, holding at most 65,536 entries.
	 *
	 * @param drawable the control or image whose GC measures
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the drawable is null</li>
	 * </ul>
	 */
	public M3TextMeasure (Drawable drawable) {
		this (drawable, 1 << 16);
	}

	/**
	 * Makes a measure for a drawable.
	 *
	 * @param drawable the control or image whose GC measures
	 * @param capacity the most entries the table holds (at least 1)
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the drawable is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the capacity is less than 1</li>
	 * </ul>
	 */
	public M3TextMeasure (Drawable drawable, int capacity) {
		if (drawable == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (capacity < 1) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		this.drawable = drawable;
		this.capacity = capacity;
		clear ();
	}

	/**
	 * Returns the most entries the table holds.
	 *
	 * @return the capacity
	 */
	public int capacity () {
		return capacity;
	}

	/**
	 * Returns the width of a text drawn with a font.
	 *
	 * @param text the text
	 * @param font the font, or <code>null</code> for the drawable's GC font
	 * @return <code>GC.textExtent (text).x</code> with the font
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the text is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the font has been disposed</li>
	 * </ul>
	 */
	public int width (String text, Font font) {
		if (text == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		int slot = slot (text, font);
		if (texts [slot] != null) {
			hits++;
			return widths [slot];
		}
		int width = measure (text, font);
		store (text, font, width);
		return width;
	}

	/**
	 * Returns the widths of texts drawn with one font, as an int lane.
	 *
	 * @param texts the texts
	 * @param font the font, or <code>null</code> for the drawable's GC font
	 * @return the widths, one per text
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the array or one of the texts is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the font has been disposed</li>
	 * </ul>
	 */
	public int [] widths (String [] texts, Font font) {
		if (texts == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		int [] out = new int [texts.length];
		try {
			for (int i = 0; i < texts.length; i++) out [i] = width (texts [i], font);
		} finally {
			release ();
		}
		return out;
	}

	/**
	 * Forgets the width of one text and font, so that the next request measures it again.
	 *
	 * @param text the text
	 * @param font the font, or <code>null</code>
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the text is null</li>
	 * </ul>
	 */
	public void forget (String text, Font font) {
		if (text == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		int slot = slot (text, font);
		if (texts [slot] != null) delete (slot);
	}

	/**
	 * Forgets the widths measured with a font (all widths for <code>null</code>), for example when the
	 * font changed or was disposed.
	 *
	 * @param font the font, or <code>null</code> for every entry
	 */
	public void invalidate (Font font) {
		if (font == null) {
			clear ();
			return;
		}
		for (int i = 0; i < texts.length; i++) {
			if (texts [i] != null && fonts [i] == font) texts [i] = null;
		}
		rehash (texts, fonts, widths, texts.length);
	}

	/**
	 * Releases the GC the measure opened, if any. The next miss opens a new one.
	 */
	public void release () {
		if (gc == null) return;
		gc.dispose ();
		gc = null;
	}

	/**
	 * Returns the counts of the measure, indexed by {@link #MEASURED}, {@link #HITS} and {@link #ENTRIES}.
	 *
	 * @return the counts
	 */
	public long [] stats () {
		return new long [] {measured, hits, size};
	}

	/* ------------------------------------------------------------------ table */

	/* the slot of the pair: its entry, or the free slot where it goes (linear probing) */
	private int slot (String text, Font font) {
		int mask = texts.length - 1, slot = hash (text, font) & mask;
		while (texts [slot] != null && (fonts [slot] != font || !texts [slot].equals (text))) slot = (slot + 1) & mask;
		return slot;
	}

	private static int hash (String text, Font font) {
		int h = text.hashCode () * 31 + System.identityHashCode (font);
		return h ^ (h >>> 16);
	}

	private void store (String text, Font font, int width) {
		if (size >= capacity) clear ();
		if (2 * (size + 1) > texts.length) rehash (texts, fonts, widths, texts.length * 2);
		int slot = slot (text, font);
		texts [slot] = text;
		fonts [slot] = font;
		widths [slot] = width;
		size++;
	}

	/* removes an entry and moves the rest of its probe cluster back into place (no tombstones) */
	private void delete (int slot) {
		int mask = texts.length - 1, next = (slot + 1) & mask;
		texts [slot] = null;
		size--;
		while (texts [next] != null) {
			String text = texts [next];
			Font font = fonts [next];
			int width = widths [next];
			texts [next] = null;
			int to = slot (text, font);
			texts [to] = text;
			fonts [to] = font;
			widths [to] = width;
			next = (next + 1) & mask;
		}
	}

	private void rehash (String [] oldTexts, Font [] oldFonts, int [] oldWidths, int length) {
		texts = new String [length];
		fonts = new Font [length];
		widths = new int [length];
		size = 0;
		for (int i = 0; i < oldTexts.length; i++) {
			if (oldTexts [i] == null) continue;
			int slot = slot (oldTexts [i], oldFonts [i]);
			texts [slot] = oldTexts [i];
			fonts [slot] = oldFonts [i];
			widths [slot] = oldWidths [i];
			size++;
		}
	}

	private void clear () {
		texts = new String [16];
		fonts = new Font [16];
		widths = new int [16];
		size = 0;
	}

	private int measure (String text, Font font) {
		if (font != null && font.isDisposed ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (gc == null) {
			gc = new GC (drawable);
			gcFont = gc.getFont ();
		}
		gc.setFont (font != null ? font : gcFont);
		measured++;
		return gc.textExtent (text).x;
	}
}
