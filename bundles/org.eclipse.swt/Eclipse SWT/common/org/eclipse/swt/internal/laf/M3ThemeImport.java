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
import java.util.regex.*;

import org.eclipse.swt.*;

/**
 * Theme import (docs/m3/LOOK_AND_FEEL.md, LF6, improvement 4): an IntelliJ <code>.theme.json</code> or a FlatLaf
 * <code>.properties</code> theme becomes an LF2 table (properties text extending <code>flat</code> or
 * <code>flatdark</code>) that {@link #install} registers as a look and feel.
 * <p>
 * The IntelliJ mapping is adapted from FlatLaf's <code>IntelliJTheme</code> (Apache-2.0, FormDev Software GmbH):
 * named colours, nested <code>ui</code> objects flattened with <code>.</code>, <code>*</code> for every key with the
 * property, <code>os.*</code> values by platform, colours without <code>#</code> for keys ending in
 * <code>ground</code> or <code>Color</code>, size keys ignored, and the key names of the LF2 catalogue. A value the LF2
 * format cannot hold (fonts, borders, fractions) is dropped and counted; a FlatLaf line the loader cannot resolve
 * (an unsupported function, a class name, a reference to a dropped key) is dropped the same way.
 * </p>
 */
public final class M3ThemeImport {
	/** An imported table: its text, the number of keys kept and the number dropped. */
	public record Imported (String text, int keys, int dropped) {
	}

	/** A key name dialect: IntelliJ themes. */
	static final char INTELLIJ = 'I';
	/** A key name dialect: Swing and FlatLaf (UIManager keys). */
	static final char SWING = 'S';
	/*
	 * Foreign key names -> LF2 catalogue key names, with the dialects that use them (the first row of a dialect for a
	 * catalogue key is the name M3ThemeExport writes); every row is read on import.
	 */
	private static final String [][] NAMES = {{"TableHeader.background", "Table.headerBackground", "IS"},
		{"TableHeader.foreground", "Table.headerForeground", "IS"}, {"TableHeader.separatorColor", "Table.headerSeparatorColor", "IS"},
		{"TableHeader.bottomSeparatorColor", "Table.headerSeparatorColor", ""}, {"Table.stripeColor", "Table.stripeBackground", "I"},
		{"Table.alternateRowColor", "Table.stripeBackground", "S"}, {"Tree.hash", "Tree.lineColor", "IS"},
		{"TabbedPane.underlineColor", "TabFolder.underlineColor", "IS"}, {"TabbedPane.hoverColor", "TabFolder.hoverBackground", "IS"},
		{"TabbedPane.background", "TabFolder.background", "IS"}, {"TabbedPane.selectedBackground", "TabFolder.selectedBackground", "IS"},
		{"Link.activeForeground", "Link.foreground", "I"}, {"Component.linkColor", "Link.foreground", "S"},
		{"Button.startBackground", "Button.background", "I"}, {"Button.startBorderColor", "Button.borderColor", "I"},
		{"TextField.background", "Text.background", "IS"}, {"TextField.foreground", "Text.foreground", "IS"},
		{"ProgressBar.progressColor", "ProgressBar.foreground", "I"}, {"ProgressBar.trackColor", "ProgressBar.background", "I"}};
	private static final Map<String, String> KEYS = new HashMap<> ();
	static {
		for (String [] row : NAMES) KEYS.put (row [0], row [1]);
	}
	private static final Set<String> OPTIONS = Set.of ("relative", "autoInverse", "noAutoInverse", "lazy", "derived");
	private static final Pattern CALL = Pattern.compile ("([A-Za-z]+)\\s*\\(");
	private static final Pattern FAILED_KEY = Pattern.compile (": ([^ :=]+) = |^import: ([^ :=]+): unknown ");

	private M3ThemeImport () {
	}

	/** The name of a catalogue key in a dialect ({@link #INTELLIJ}, {@link #SWING}): the first row that maps to it, else the key. */
	static String foreign (String key, char dialect) {
		for (String [] row : NAMES) {
			if (row [1].equals (key) && row [2].indexOf (dialect) >= 0) return row [0];
		}
		return key;
	}

	/**
	 * The LF2 table of an IntelliJ theme.
	 *
	 * @throws IllegalArgumentException when the text is not a JSON object
	 */
	public static Imported intelliJ (String json) {
		if (!(M3Json.parse (json) instanceof Map<?, ?> theme)) throw new IllegalArgumentException ("an IntelliJ theme is a JSON object");
		Map<String, String> colours = new HashMap<> ();
		if (theme.get ("colors") instanceof Map<?, ?> named) {
			for (Map.Entry<?, ?> entry : named.entrySet ()) colours.put (String.valueOf (entry.getKey ()), String.valueOf (entry.getValue ()));
		}
		Map<String, String> wildcards = new LinkedHashMap<> (), specific = new LinkedHashMap<> ();
		int [] dropped = {0};
		if (theme.get ("ui") instanceof Map<?, ?> ui) flatten ("", ui, colours, wildcards, specific, dropped);
		Map<String, String> table = new LinkedHashMap<> ();
		for (Map.Entry<String, String> wildcard : wildcards.entrySet ()) {
			for (String key : LookAndFeelThemes.KEYS) {
				if (key.endsWith (wildcard.getKey ())) table.put (key, wildcard.getValue ());
			}
		}
		table.putAll (specific);
		String base = Boolean.TRUE.equals (theme.get ("dark")) ? "flatdark" : "flat";
		return new Imported (text (theme.get ("name"), base, table), table.size (), dropped [0]);
	}

	/**
	 * The LF2 table of a FlatLaf properties theme: <code>@baseTheme = dark|darcula</code> extends <code>flatdark</code>,
	 * otherwise <code>flat</code>; <code>[light]</code> or <code>[dark]</code> lines of the other base and every other
	 * bracketed line are dropped; FlatLaf's function options are removed; Swing key names become catalogue names; lines the
	 * loader cannot resolve are dropped.
	 */
	public static Imported flatLaf (String properties) {
		Map<String, String> lines = LookAndFeelLoader.parse (properties, "FlatLaf properties");
		String baseTheme = lines.getOrDefault ("@baseTheme", "light").trim ();
		boolean dark = baseTheme.equals ("dark") || baseTheme.equals ("darcula");
		Map<String, String> table = new LinkedHashMap<> ();
		int dropped = 0;
		for (Map.Entry<String, String> line : lines.entrySet ()) {
			String key = line.getKey ();
			if (key.equals ("@baseTheme")) continue;
			if (key.startsWith ("[")) {
				String prefix = dark ? "[dark]" : "[light]";
				if (!key.startsWith (prefix)) {
					dropped++;
					continue;
				}
				key = key.substring (prefix.length ());
			}
			String value = options (line.getValue ());
			if (value == null) dropped++;
			else table.put (KEYS.getOrDefault (key, key), value);
		}
		String base = dark ? "flatdark" : "flat";
		dropped += resolvable (base, table);
		return new Imported (text (null, base, table), table.size (), dropped);
	}

	/** Registers an imported table as a look and feel ({@link M3LookAndFeel#register}). */
	public static void install (String name, Imported imported) {
		M3LookAndFeel.register (name, imported.text (), null);
	}

	/* nested ui objects flattened; "*" collected apart; os.* objects reduced to this platform's value */
	private static void flatten (String prefix, Map<?, ?> map, Map<String, String> colours, Map<String, String> wildcards, Map<String, String> specific, int [] dropped) {
		if (map.containsKey ("os.default") || map.containsKey ("os.windows") || map.containsKey ("os.mac") || map.containsKey ("os.linux")) {
			Object value = map.containsKey (os ()) ? map.get (os ()) : map.get ("os.default");
			if (value != null) leaf (prefix, value, colours, wildcards, specific, dropped);
			return;
		}
		for (Map.Entry<?, ?> entry : map.entrySet ()) {
			String key = prefix.isEmpty () ? String.valueOf (entry.getKey ()) : prefix + "." + entry.getKey ();
			if (entry.getValue () instanceof Map<?, ?> nested) flatten (key, nested, colours, wildcards, specific, dropped);
			else leaf (key, entry.getValue (), colours, wildcards, specific, dropped);
		}
	}

	private static void leaf (String key, Object value, Map<String, String> colours, Map<String, String> wildcards, Map<String, String> specific, int [] dropped) {
		if (key.endsWith (".border") || key.endsWith (".rowHeight") || key.endsWith ("ChildIndent") || key.endsWith (".padding")) return;
		String mapped = key.startsWith ("*.") ? key.substring (1) : KEYS.getOrDefault (key, key);
		String text = literal (mapped, value, colours);
		if (text == null) dropped [0]++;
		else (key.startsWith ("*.") ? wildcards : specific).put (mapped, text);
	}

	/* an IntelliJ value as LF2 text, or null when LF2 cannot hold it */
	private static String literal (String key, Object value, Map<String, String> colours) {
		if (value instanceof Boolean flag) return flag.toString ();
		if (value instanceof Long number) return number.toString ();
		if (!(value instanceof String text)) return null;
		text = colours.getOrDefault (text, text).trim ();
		if (!text.startsWith ("#") && (key.endsWith ("ground") || key.endsWith ("Color"))) text = "#" + text;
		if (text.startsWith ("#")) return colour (text) ? text : null;
		return text.matches ("-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+") ? text : null;
	}

	private static boolean colour (String text) {
		try {
			LookAndFeelLoader.color (text);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/* a FlatLaf value with the options of its functions removed; null when it needs one LF2 cannot follow */
	private static String options (String value) {
		if (value.contains ("{") || value.contains ("relative") || value.matches (".*[A-Za-z]+\\.[A-Za-z]+\\.[A-Za-z].*")) return null;
		StringBuilder out = new StringBuilder (value);
		for (String option : OPTIONS) {
			int at;
			while ((at = out.indexOf ("," + option)) >= 0) out.delete (at, at + option.length () + 1);
			while ((at = out.indexOf (", " + option)) >= 0) out.delete (at, at + option.length () + 2);
		}
		Matcher call = CALL.matcher (out);
		while (call.find ()) {
			String function = call.group (1);
			if (!M3ColorFunctions.NAMES.contains (function) && !Set.of ("darken", "lighten", "fade", "mix", "system").contains (function)) return null;
		}
		return out.toString ().trim ();
	}

	/* drops the lines the loader cannot resolve, one at a time (each failure names its key); answers how many */
	private static int resolvable (String base, Map<String, String> table) {
		for (int dropped = 0;; dropped++) {
			String text = text (null, base, table);
			try {
				new LookAndFeelLoader (n -> n.equals ("import") ? text : LookAndFeelThemes.text (n), name -> 0x808080).load ("import");
				return dropped;
			} catch (IllegalArgumentException e) {
				Matcher failed = FAILED_KEY.matcher (String.valueOf (e.getMessage ()));
				if (!failed.find () || table.remove (failed.group (1) != null ? failed.group (1) : failed.group (2)) == null) throw e;
			}
		}
	}

	private static String text (Object name, String base, Map<String, String> table) {
		StringBuilder out = new StringBuilder ();
		if (name != null) out.append ("# imported: ").append (name).append ('\n');
		out.append ("@extends = ").append (base).append ('\n');
		for (Map.Entry<String, String> entry : table.entrySet ()) out.append (entry.getKey ()).append (" = ").append (entry.getValue ()).append ('\n');
		return out.toString ();
	}

	private static String os () {
		String platform = SWT.getPlatform ();
		return platform.equals ("win32") ? "os.windows" : platform.equals ("cocoa") ? "os.mac" : "os.linux";
	}
}
