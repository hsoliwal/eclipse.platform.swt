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
package org.eclipse.swt.internal.laf;

import java.util.*;

/**
 * The data an LLM proposes for a theme (docs/m3/look-and-feel/GENERATOR.md): a closed JSON schema, read strictly. An
 * unknown member, a value of the wrong type, a number out of range or an unknown density rejects the whole answer; a
 * colour that cannot be read is recorded in {@link #repairs()} and treated as missing, for {@link M3ThemeCompleter} to
 * derive. {@link #canonical()} is the accepted data as canonical JSON (sorted members, <code>#rrggbb</code> colours),
 * the input of the theme's content hash.
 */
public final class M3ThemeSpec {
	/** The palette slots, in the order of {@link #colour(int)}. */
	public static final List<String> PALETTE = List.of ("background", "foreground", "accent", "selection", "border", "error", "warning", "info");
	/** The densities. */
	public static final List<String> DENSITIES = List.of ("compact", "normal", "comfortable");
	/** A palette slot without a colour. */
	public static final int MISSING = 0;
	private static final Set<String> TOP = Set.of ("name", "dark", "palette", "font", "density"), FONT = Set.of ("family", "size");

	private final String name, family;
	private final Boolean dark;
	private final int [] palette;
	private final int size, density;
	private final List<String> repairs;

	M3ThemeSpec (String name, Boolean dark, int [] palette, String family, int size, int density, List<String> repairs) {
		this.name = name;
		this.dark = dark;
		this.palette = palette;
		this.family = family;
		this.size = size;
		this.density = density;
		this.repairs = List.copyOf (repairs);
	}

	/**
	 * Reads an LLM's answer.
	 *
	 * @throws IllegalArgumentException when the answer is not JSON or breaks the schema
	 */
	public static M3ThemeSpec parse (String json) {
		if (!(M3Json.parse (json) instanceof Map<?, ?> top)) throw new IllegalArgumentException ("a theme is a JSON object");
		only (top, TOP, "");
		Boolean dark = top.containsKey ("dark") ? type (top.get ("dark"), Boolean.class, "dark") : null;
		List<String> repairs = new ArrayList<> ();
		int [] palette = new int [PALETTE.size ()];
		if (top.containsKey ("palette")) {
			Map<?, ?> colours = type (top.get ("palette"), Map.class, "palette");
			only (colours, Set.copyOf (PALETTE), "palette.");
			for (int i = 0; i < PALETTE.size (); i++) {
				if (colours.containsKey (PALETTE.get (i))) palette [i] = colour (type (colours.get (PALETTE.get (i)), String.class, "palette." + PALETTE.get (i)), PALETTE.get (i), repairs);
			}
		}
		if (dark == null && palette [0] == MISSING) throw new IllegalArgumentException ("a theme needs dark or palette.background");
		String family = null;
		int size = 0;
		if (top.containsKey ("font")) {
			Map<?, ?> font = type (top.get ("font"), Map.class, "font");
			only (font, FONT, "font.");
			if (font.containsKey ("family")) family = text (type (font.get ("family"), String.class, "font.family"), 64, "font.family");
			if (font.containsKey ("size")) size = range (type (font.get ("size"), Long.class, "font.size"), 6, 32, "font.size");
		}
		int density = -1;
		if (top.containsKey ("density")) {
			density = DENSITIES.indexOf (type (top.get ("density"), String.class, "density"));
			if (density < 0) throw new IllegalArgumentException ("density is one of " + DENSITIES);
		}
		String name = top.containsKey ("name") ? text (type (top.get ("name"), String.class, "name"), 80, "name") : null;
		return new M3ThemeSpec (name, dark, palette, family, size, density, repairs);
	}

	/** The name, or <code>null</code>. */
	public String name () {
		return name;
	}

	/** True or false when the answer says, <code>null</code> when it does not. */
	public Boolean dark () {
		return dark;
	}

	/** The ARGB colour of a palette slot ({@link #PALETTE}), or {@link #MISSING}. */
	public int colour (int slot) {
		return palette [slot];
	}

	/** The font family, or <code>null</code>. */
	public String family () {
		return family;
	}

	/** The font size in points, or 0. */
	public int size () {
		return size;
	}

	/** The density ({@link #DENSITIES} index), or -1. */
	public int density () {
		return density;
	}

	/** What was repaired while reading (unreadable colours, translucent colours made opaque). */
	public List<String> repairs () {
		return repairs;
	}

	/** The accepted data as canonical JSON. */
	public String canonical () {
		StringBuilder out = new StringBuilder ("{");
		if (dark != null) out.append ("\"dark\":").append (dark).append (',');
		if (density >= 0) out.append ("\"density\":\"").append (DENSITIES.get (density)).append ("\",");
		if (family != null || size > 0) {
			out.append ("\"font\":{");
			if (family != null) out.append ("\"family\":").append (quote (family)).append (size > 0 ? "," : "");
			if (size > 0) out.append ("\"size\":").append (size);
			out.append ("},");
		}
		if (name != null) out.append ("\"name\":").append (quote (name)).append (',');
		StringBuilder colours = new StringBuilder ();
		List<String> sorted = new ArrayList<> (PALETTE);
		Collections.sort (sorted);
		for (String slot : sorted) {
			int colour = palette [PALETTE.indexOf (slot)];
			if (colour != MISSING) colours.append (colours.length () == 0 ? "" : ",").append ('"').append (slot).append ("\":\"").append (M3Palette.text (colour)).append ('"');
		}
		if (colours.length () > 0) out.append ("\"palette\":{").append (colours).append ("},");
		if (out.charAt (out.length () - 1) == ',') out.setLength (out.length () - 1);
		return out.append ('}').toString ();
	}

	private static void only (Map<?, ?> map, Set<String> allowed, String prefix) {
		for (Object key : map.keySet ()) {
			if (!allowed.contains (key)) throw new IllegalArgumentException ("unknown key " + prefix + key);
		}
	}

	private static <T> T type (Object value, Class<T> type, String where) {
		if (!type.isInstance (value)) throw new IllegalArgumentException (where + " is not a " + type.getSimpleName ().toLowerCase (Locale.ROOT));
		return type.cast (value);
	}

	private static String text (String value, int length, String where) {
		if (value.isBlank () || value.length () > length) throw new IllegalArgumentException (where + " is blank or longer than " + length);
		return value.trim ();
	}

	private static int range (Long value, int min, int max, String where) {
		if (value < min || value > max) throw new IllegalArgumentException (where + " is not in " + min + ".." + max);
		return value.intValue ();
	}

	/* #rgb, #rrggbb, #rrggbbaa (alpha dropped); anything else is recorded and missing */
	private static int colour (String text, String slot, List<String> repairs) {
		String value = text.trim ();
		if (value.matches ("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
			int argb = LookAndFeelLoader.color (value);
			if ((argb >>> 24) != 0xFF) repairs.add (slot + ": " + value + " made opaque");
			return 0xFF000000 | argb;
		}
		repairs.add (slot + ": unreadable colour " + quote (value) + " dropped");
		return MISSING;
	}

	static String quote (String text) {
		StringBuilder out = new StringBuilder ("\"");
		for (char c : text.toCharArray ()) {
			if (c == '"' || c == '\\') out.append ('\\').append (c);
			else if (c < 0x20) out.append (String.format ("\\u%04x", (int) c));
			else out.append (c);
		}
		return out.append ('"').toString ();
	}
}
