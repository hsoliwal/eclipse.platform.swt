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
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;

/**
 * A theme: one packed int table of named colour, font and metric slots ({@link M3ThemeSlot}), a dark flag and a font
 * family (docs/m3/theme-language/README.md). Immutable; a widget reads a slot with one array read. Built-in themes are
 * {@link #LIGHT}, {@link #DARK} and {@link #HIGH_CONTRAST}; user themes come from {@link #builder(String, M3Theme)} or from a
 * small properties or JSON file ({@link #parse(String)}). Every theme defines every slot: a builder starts from a complete
 * base and a file names the theme it extends.
 * <p>
 * {@link M3Themes#apply} switches a display to a theme at run time.
 * </p>
 *
 * @since 3.138
 */
public final class M3Theme {
	/** The light built-in theme. */
	public static final M3Theme LIGHT = new M3Theme ("light", false, M3ThemeBuiltins.light (), "");
	/** The dark built-in theme. */
	public static final M3Theme DARK = new M3Theme ("dark", true, M3ThemeBuiltins.dark (), "");
	/** The high-contrast built-in theme (white on black, cyan selection, yellow focus and links, two-pixel focus and borders). */
	public static final M3Theme HIGH_CONTRAST = new M3Theme ("high-contrast", true, M3ThemeBuiltins.highContrast (), "");
	/** WCAG 2 AA contrast for text, and for user-interface components. */
	public static final double TEXT_CONTRAST = 4.5, UI_CONTRAST = 3.0;

	private final String name, font;
	private final boolean dark;
	private final int [] slots;

	M3Theme (String name, boolean dark, int [] slots, String font) {
		this.name = name;
		this.dark = dark;
		this.slots = slots;
		this.font = font;
	}

	/** @return the three built-in themes */
	public static List<M3Theme> builtIns () {
		return List.of (LIGHT, DARK, HIGH_CONTRAST);
	}

	/**
	 * A builder that starts from every value of a base theme.
	 *
	 * @param name the new theme's name (trimmed, lower case)
	 * @param base the theme whose slots, dark flag and font it starts from
	 * @return the builder
	 * @exception IllegalArgumentException <ul><li>ERROR_NULL_ARGUMENT - if name or base is null</li><li>ERROR_INVALID_ARGUMENT - if the name is blank</li></ul>
	 */
	public static Builder builder (String name, M3Theme base) {
		if (name == null || base == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		return new Builder (name, base);
	}

	/**
	 * Reads a theme file: properties (<code>slot = value</code>) or one JSON object (members, or a <code>"slots"</code>
	 * object). Keys: <code>name</code>, <code>extends</code> (a built-in or registered theme, default <code>light</code>),
	 * <code>dark</code>, <code>font</code>, and any slot name (<code>--vs-</code> prefix accepted). Colours are
	 * <code>#rgb</code>, <code>#rgba</code>, <code>#rrggbb</code> or <code>#rrggbbaa</code>; metrics are integers.
	 *
	 * @param text the file contents
	 * @return the theme
	 * @exception IllegalArgumentException when a key, a value or the base is unknown or malformed (the message names it)
	 */
	public static M3Theme parse (String text) {
		return M3ThemeFile.parse (text);
	}

	/** @return the name (lower case) */
	public String name () {
		return name;
	}

	/** @return true for a theme with light text on dark backgrounds */
	public boolean dark () {
		return dark;
	}

	/** @return the font family, or the empty string for the platform's */
	public String font () {
		return font;
	}

	/**
	 * @param slot a slot ({@link M3ThemeSlot})
	 * @return its value: ARGB for a colour slot, the number for a metric slot
	 */
	//FLLDCIM: HPCPOP — the read every painter does: one bounds check, one array read
	public int get (int slot) {
		M3ThemeSlot.check (slot);
		return slots [slot];
	}

	/** @return a copy of the packed table, {@link M3ThemeSlot#COUNT} ints */
	public int [] slots () {
		return slots.clone ();
	}

	/**
	 * @param foreground a colour slot
	 * @param background a colour slot
	 * @return the WCAG 2 contrast ratio of the two colours, 1 to 21
	 */
	public double contrast (int foreground, int background) {
		return M3Palette.contrast (get (foreground), get (background));
	}

	/** @return the pairs of {@link M3ThemeSlot#TEXT_PAIRS} below 4.5:1 and of {@link M3ThemeSlot#UI_PAIRS} below 3:1, as slot names */
	public List<String> contrastFailures () {
		Stream<String> text = Arrays.stream (M3ThemeSlot.TEXT_PAIRS).filter (p -> contrast (p [0], p [1]) < TEXT_CONTRAST).map (this::pair);
		Stream<String> ui = Arrays.stream (M3ThemeSlot.UI_PAIRS).filter (p -> contrast (p [0], p [1]) < UI_CONTRAST).map (this::pair);
		return Stream.concat (text, ui).toList ();
	}

	private String pair (int [] pair) {
		return M3ThemeSlot.name (pair [0]) + "/" + M3ThemeSlot.name (pair [1]) + String.format (Locale.ROOT, " %.2f", contrast (pair [0], pair [1]));
	}

	/**
	 * @param newName the derived theme's name
	 * @return a builder that starts from this theme
	 */
	public Builder derive (String newName) {
		return builder (newName, this);
	}

	/** @return this theme as a properties file that {@link #parse(String)} reads back to an equal theme */
	public String toText () {
		return M3ThemeFile.text (this);
	}

	/**
	 * @return the look and feel table of this theme (LF2 format, docs/m3/LOOK_AND_FEEL.md): the drawn planes, the M3 renderer
	 *         and every look and feel delegate read the slots through it
	 */
	public String toLookAndFeelTable () {
		return M3ThemeFile.lookAndFeel (this);
	}

	@Override
	public boolean equals (Object other) {
		return other instanceof M3Theme theme && name.equals (theme.name) && dark == theme.dark && font.equals (theme.font)
			&& Arrays.equals (slots, theme.slots);
	}

	@Override
	public int hashCode () {
		return Objects.hash (name, dark, font) * 31 + Arrays.hashCode (slots);
	}

	@Override
	public String toString () {
		return "M3Theme " + name;
	}

	/** Collects the values of a theme; {@link #build()} makes the immutable theme. */
	public static final class Builder {
		private final String name;
		private final int [] slots;
		private boolean dark;
		private String font;

		Builder (String name, M3Theme base) {
			this.name = name.strip ().toLowerCase (Locale.ROOT);
			if (this.name.isEmpty ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			this.slots = base.slots.clone ();
			this.dark = base.dark;
			this.font = base.font;
		}

		/**
		 * @param slot a slot
		 * @param value ARGB for a colour slot (use <code>0xFF000000 | rgb</code> for opaque); for a metric slot, within its range
		 * @return this builder
		 * @exception IllegalArgumentException <ul><li>ERROR_INVALID_RANGE - if the slot is unknown</li><li>ERROR_INVALID_ARGUMENT - if a metric is out of its range</li></ul>
		 */
		public Builder set (int slot, int value) {
			if (M3ThemeSlot.kind (slot) == M3ThemeSlot.METRIC && !M3ThemeFile.inRange (slot, value)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			slots [slot] = value;
			return this;
		}

		/**
		 * @param slot a slot name ({@link M3ThemeSlot#id(String)})
		 * @param value as {@link #set(int, int)}
		 * @return this builder
		 */
		public Builder set (String slot, int value) {
			int id = M3ThemeSlot.id (slot);
			if (id < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT, null, " unknown theme slot '" + slot + "'");
			return set (id, value);
		}

		/** @return this builder, for a theme with light text on dark backgrounds or not */
		public Builder dark (boolean value) {
			dark = value;
			return this;
		}

		/** @return this builder, with a font family (empty for the platform's) */
		public Builder font (String family) {
			font = family == null ? "" : family.strip ();
			return this;
		}

		/** @return the immutable theme */
		public M3Theme build () {
			return new M3Theme (name, dark, slots.clone (), font);
		}
	}
}
