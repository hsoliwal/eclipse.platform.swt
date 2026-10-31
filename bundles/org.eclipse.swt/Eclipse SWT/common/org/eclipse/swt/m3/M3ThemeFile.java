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

import static org.eclipse.swt.m3.M3ThemeSlot.*;

import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;

/*
 * Theme file text (M3Theme.parse, toText) and the look and feel table of a theme (M3Theme.toLookAndFeelTable).
 */
final class M3ThemeFile {
	static final String NAME = "name", EXTENDS = "extends", DARK = "dark", FONT = "font";
	/* look and feel keys (docs/m3/LOOK_AND_FEEL.md, LookAndFeelThemes.KEYS and the ThemeDefaults keys) and the slot each reads */
	private static final Object [][] LAF = {{"Component.background", WIDGET_BACKGROUND}, {"Component.foreground", WIDGET_FOREGROUND},
		{"Component.disabledForeground", WIDGET_DISABLED_FOREGROUND}, {"Component.borderColor", BORDER}, {"Component.focusColor", FOCUS_RING},
		{"Component.focusWidth", FOCUS_WIDTH}, {"Component.arc", RADIUS}, {"Component.hoverBackground", HOVER},
		{"Component.selectionBackground", SELECTION}, {"Component.selectionForeground", SELECTION_TEXT},
		{"Component.selectionInactiveBackground", SELECTION_INACTIVE}, {"Component.selectionInactiveForeground", LIST_FOREGROUND},
		{"Button.background", SURFACE}, {"Button.foreground", WIDGET_FOREGROUND}, {"Label.foreground", WIDGET_FOREGROUND},
		{"Link.foreground", LINK_FOREGROUND}, {"Text.background", LIST_BACKGROUND}, {"Text.foreground", LIST_FOREGROUND},
		{"List.background", LIST_BACKGROUND}, {"List.foreground", LIST_FOREGROUND}, {"List.selectionBackground", SELECTION},
		{"List.selectionForeground", SELECTION_TEXT}, {"List.selectionInactiveBackground", SELECTION_INACTIVE},
		{"Table.background", LIST_BACKGROUND}, {"Table.foreground", LIST_FOREGROUND}, {"Table.selectionBackground", SELECTION},
		{"Table.selectionForeground", SELECTION_TEXT}, {"Table.selectionInactiveBackground", SELECTION_INACTIVE},
		{"Table.gridColor", GRIDLINE}, {"Table.stripeBackground", ROW_STRIPE}, {"Table.headerBackground", HEADER_BACKGROUND},
		{"Table.headerForeground", HEADER_FOREGROUND}, {"Table.headerSeparatorColor", BORDER}, {"Tree.background", LIST_BACKGROUND},
		{"Tree.foreground", LIST_FOREGROUND}, {"Tree.selectionBackground", SELECTION}, {"Tree.selectionForeground", SELECTION_TEXT},
		{"Tree.selectionInactiveBackground", SELECTION_INACTIVE}, {"Tree.lineColor", GRIDLINE}, {"Tree.expandIconColor", MUTED},
		{"TabFolder.background", WIDGET_BACKGROUND}, {"TabFolder.selectedBackground", LIST_BACKGROUND}, {"TabFolder.hoverBackground", HOVER},
		{"TabFolder.underlineColor", ACCENT}, {"ProgressBar.foreground", ACCENT}, {"ToolTip.background", INFO_BACKGROUND},
		{"ToolTip.foreground", INFO_FOREGROUND}, {"ToolTip.borderColor", BORDER}, {"CheckBox.icon.checkmarkColor", ACCENT},
		{"CheckBox.icon.disabledCheckmarkColor", WIDGET_DISABLED_FOREGROUND}};
	/* the variables of the flat tables, so that every key the theme does not name derives from the theme too */
	private static final Object [][] VARIABLES = {{"@background", WIDGET_BACKGROUND}, {"@foreground", WIDGET_FOREGROUND},
		{"@field", LIST_BACKGROUND}, {"@accent", ACCENT}, {"@selectionText", SELECTION_TEXT}, {"@border", BORDER},
		{"@disabled", WIDGET_DISABLED_FOREGROUND}};

	private M3ThemeFile () {
	}

	static M3Theme parse (String text) {
		Map<String, String> table = M3ThemeTable.read (text);
		String name = table.get (NAME);
		if (name == null || name.isBlank ()) throw new IllegalArgumentException ("theme file without a name");
		M3Theme.Builder builder = base (table.getOrDefault (EXTENDS, M3Theme.LIGHT.name ())).derive (name);
		table.forEach ((key, value) -> entry (builder, name, key, value));
		return builder.build ();
	}

	private static M3Theme base (String name) {
		M3Theme base = M3Themes.find (name);
		if (base == null) throw new IllegalArgumentException ("theme extends unknown theme '" + name + "'");
		return base;
	}

	private static void entry (M3Theme.Builder builder, String theme, String key, String value) {
		switch (key) {
			case NAME, EXTENDS -> { /* read first */ }
			case DARK -> builder.dark (bool (theme, value));
			case FONT -> builder.font (value);
			default -> slot (builder, theme, key, value);
		}
	}

	private static void slot (M3Theme.Builder builder, String theme, String key, String value) {
		int slot = id (key);
		if (slot < 0) throw new IllegalArgumentException (theme + ": unknown theme key '" + key + "'");
		int number = kind (slot) == COLOR ? color (theme, key, value) : metric (theme, key, value);
		if (kind (slot) == METRIC && !inRange (slot, number)) throw new IllegalArgumentException (theme + ": " + key + " = " + value + " is out of range");
		builder.set (slot, number);
	}

	private static boolean bool (String theme, String value) {
		if (!"true".equalsIgnoreCase (value) && !"false".equalsIgnoreCase (value)) throw new IllegalArgumentException (theme + ": dark must be true or false, not '" + value + "'");
		return Boolean.parseBoolean (value);
	}

	/* #rgb, #rgba, #rrggbb, #rrggbbaa -> ARGB */
	static int color (String theme, String key, String value) {
		String hex = value.startsWith ("#") ? value.substring (1) : "";
		if (!hex.chars ().allMatch (c -> Character.digit (c, 16) >= 0) || (hex.length () != 3 && hex.length () != 4 && hex.length () != 6 && hex.length () != 8)) {
			throw new IllegalArgumentException (theme + ": " + key + " = '" + value + "' is not a colour (#rgb, #rgba, #rrggbb, #rrggbbaa)");
		}
		if (hex.length () <= 4) hex = hex.chars ().mapToObj (c -> "" + (char) c + (char) c).collect (Collectors.joining ());
		int rgb = Integer.parseUnsignedInt (hex.substring (0, 6), 16);
		int alpha = hex.length () == 8 ? Integer.parseInt (hex.substring (6), 16) : 0xFF;
		return alpha << 24 | rgb;
	}

	private static int metric (String theme, String key, String value) {
		try {
			return Integer.parseInt (value.strip ());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException (theme + ": " + key + " = '" + value + "' is not an integer", e);
		}
	}

	static boolean inRange (int slot, int value) {
		return switch (slot) {
			case FONT_HEIGHT -> value >= 0 && value <= 200;
			case FONT_STYLE -> (value & ~(SWT.BOLD | SWT.ITALIC)) == 0;
			case ROW_HEIGHT, INDENT -> value >= -1 && value <= 512;
			default -> value >= 0 && value <= 64;
		};
	}

	static String text (M3Theme theme) {
		Stream<String> head = Stream.of ("# M3Theme " + theme.name (), NAME + " = " + theme.name (), DARK + " = " + theme.dark (), FONT + " = " + theme.font ());
		Stream<String> body = IntStream.range (0, COUNT).mapToObj (slot -> name (slot) + " = " + value (theme, slot));
		return Stream.concat (head, body).collect (Collectors.joining ("\n", "", "\n"));
	}

	private static String value (M3Theme theme, int slot) {
		return kind (slot) == COLOR ? M3Palette.text (theme.get (slot)) : Integer.toString (theme.get (slot));
	}

	static String lookAndFeel (M3Theme theme) {
		Stream<String> head = Stream.of ("# M3Theme " + theme.name (), "@extends = " + (theme.dark () ? "flatdark" : "flat"));
		Stream<String> variables = Arrays.stream (VARIABLES).map (row -> row [0] + " = " + value (theme, (Integer) row [1]));
		Stream<String> keys = Arrays.stream (LAF).map (row -> row [0] + " = " + value (theme, (Integer) row [1]));
		return Stream.of (head, variables, keys, metrics (theme)).flatMap (s -> s).collect (Collectors.joining ("\n", "", "\n"));
	}

	/* the platform's row height and indent (-1) are left to the base table */
	private static Stream<String> metrics (M3Theme theme) {
		int height = theme.get (ROW_HEIGHT), indent = theme.get (INDENT);
		Stream<String> rows = height < 0 ? Stream.empty () : Stream.of ("Table", "Tree", "List").map (w -> w + ".rowHeight = " + height);
		return Stream.concat (rows, indent < 0 ? Stream.empty () : Stream.of ("Tree.indent = " + indent));
	}
}
