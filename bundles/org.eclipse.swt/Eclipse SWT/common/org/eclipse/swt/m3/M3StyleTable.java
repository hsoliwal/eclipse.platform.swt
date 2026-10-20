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

import java.util.*;

import org.eclipse.swt.graphics.*;

/**
 * Interned styles of a lane model (public model API, brief rule 26): one <code>int</code> style id per distinct
 * combination of font, foreground and background, so a row or cell style is an <code>int</code> lane. Id 0 is no
 * style (the widget's defaults). Styles are few and live as long as the table; the fonts and colors stay owned by
 * the client, which disposes them after the model. Not thread-safe.
 *
 * @since 3.138
 */
public final class M3StyleTable {

	private Font [] fonts = new Font [16];
	private Color [] foregrounds = new Color [16], backgrounds = new Color [16];
	private int [] hashes = new int [16];
	private int next = 1;
	private int [] table = new int [32];

	/**
	 * Returns the id of a style, adding it when it is new.
	 *
	 * @param font the font, or <code>null</code> for the widget's
	 * @param foreground the foreground, or <code>null</code> for the widget's
	 * @param background the background, or <code>null</code> for the widget's
	 * @return the style id; 0 when all three are <code>null</code>
	 */
	public int intern (Font font, Color foreground, Color background) {
		if (font == null && foreground == null && background == null) return 0;
		int hash = Objects.hash (font, foreground, background);
		int mask = table.length - 1;
		int index = (hash ^ (hash >>> 16)) & mask;
		for (int id; (id = table [index]) != 0; index = (index + 1) & mask) {
			if (hashes [id] == hash && Objects.equals (fonts [id], font) && Objects.equals (foregrounds [id], foreground)
					&& Objects.equals (backgrounds [id], background)) return id;
		}
		int id = next++;
		if (id == fonts.length) {
			int capacity = id * 2;
			fonts = Arrays.copyOf (fonts, capacity);
			foregrounds = Arrays.copyOf (foregrounds, capacity);
			backgrounds = Arrays.copyOf (backgrounds, capacity);
			hashes = Arrays.copyOf (hashes, capacity);
		}
		fonts [id] = font;
		foregrounds [id] = foreground;
		backgrounds [id] = background;
		hashes [id] = hash;
		table [index] = id;
		if (id * 2 > table.length) rehash ();
		return id;
	}

	/**
	 * Returns the font of a style.
	 *
	 * @param styleId the style id
	 * @return the font, or <code>null</code> for the widget's
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no style has the id</li>
	 * </ul>
	 */
	public Font font (int styleId) {
		check (styleId);
		return fonts [styleId];
	}

	/**
	 * Returns the foreground of a style.
	 *
	 * @param styleId the style id
	 * @return the color, or <code>null</code> for the widget's
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no style has the id</li>
	 * </ul>
	 */
	public Color foreground (int styleId) {
		check (styleId);
		return foregrounds [styleId];
	}

	/**
	 * Returns the background of a style.
	 *
	 * @param styleId the style id
	 * @return the color, or <code>null</code> for the widget's
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no style has the id</li>
	 * </ul>
	 */
	public Color background (int styleId) {
		check (styleId);
		return backgrounds [styleId];
	}

	/**
	 * Returns the number of styles, not counting 0.
	 *
	 * @return the styles
	 */
	public int size () {
		return next - 1;
	}

	private void check (int id) {
		if (id < 0 || id >= next) org.eclipse.swt.SWT.error (org.eclipse.swt.SWT.ERROR_INVALID_ARGUMENT);
	}

	private void rehash () {
		int [] grown = new int [table.length * 2];
		int mask = grown.length - 1;
		for (int id = 1; id < next; id++) {
			int index = (hashes [id] ^ (hashes [id] >>> 16)) & mask;
			while (grown [index] != 0) index = (index + 1) & mask;
			grown [index] = id;
		}
		table = grown;
	}
}
