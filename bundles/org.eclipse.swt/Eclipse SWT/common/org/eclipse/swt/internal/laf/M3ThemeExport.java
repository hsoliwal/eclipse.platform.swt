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
 * Writes a completed theme ({@link M3ThemeCompleter.Theme}) for three toolkits (docs/m3/look-and-feel/GENERATOR.md):
 * an LF2 table for SWT ({@link M3LookAndFeel#register}), FlatLaf <code>.properties</code> for Swing (FlatLaf, or
 * UIManager through a small applier) and an IntelliJ <code>.theme.json</code>. Each carries the completer version and
 * the content hash; each reads back through {@link M3ThemeImport} (or the LF2 loader) to the same catalogue values.
 * Key names of the other toolkits come from the import's name table, so import and export never disagree.
 */
public final class M3ThemeExport {
	private M3ThemeExport () {
	}

	/** The LF2 table: <code>@background</code>, <code>@foreground</code>, <code>@accent</code> and every catalogue key; values equal to the accent refer to <code>@accent</code>. */
	public static String lf2 (M3ThemeCompleter.Theme theme) {
		StringBuilder out = header (theme, "#");
		String accent = M3Palette.text (theme.palette () [2]);
		variables (out, theme, "@accent");
		for (Map.Entry<String, String> entry : theme.table ().entrySet ()) {
			out.append (entry.getKey ()).append (" = ").append (entry.getValue ().equals (accent) ? "@accent" : entry.getValue ()).append ('\n');
		}
		return out.toString ();
	}

	/** FlatLaf properties: <code>@baseTheme</code>, the FlatLaf variables and the Swing key names. */
	public static String flatLaf (M3ThemeCompleter.Theme theme) {
		StringBuilder out = header (theme, "#");
		out.append ("@baseTheme = ").append (theme.dark () ? "dark" : "light").append ('\n');
		variables (out, theme, "@accentColor");
		for (Map.Entry<String, String> entry : theme.table ().entrySet ()) {
			out.append (M3ThemeImport.foreign (entry.getKey (), M3ThemeImport.SWING)).append (" = ").append (entry.getValue ()).append ('\n');
		}
		return out.toString ();
	}

	/** An IntelliJ theme: name, brightness, the palette as named colours, and the keys grouped by component. */
	public static String intelliJ (M3ThemeCompleter.Theme theme) {
		Map<String, Map<String, String>> ui = new TreeMap<> ();
		for (Map.Entry<String, String> entry : theme.table ().entrySet ()) {
			String name = M3ThemeImport.foreign (entry.getKey (), M3ThemeImport.INTELLIJ);
			int dot = name.indexOf ('.');
			ui.computeIfAbsent (name.substring (0, dot), k -> new TreeMap<> ()).put (name.substring (dot + 1), json (entry.getValue ()));
		}
		StringBuilder out = new StringBuilder ("{\n");
		out.append ("  \"name\": ").append (M3ThemeSpec.quote (theme.name ())).append (",\n");
		out.append ("  \"dark\": ").append (theme.dark ()).append (",\n");
		out.append ("  \"author\": ").append (M3ThemeSpec.quote ("M3ThemeCompleter " + M3ThemeCompleter.VERSION + " sha256:" + theme.hash ())).append (",\n");
		out.append ("  \"colors\": {");
		for (int i = 0; i < M3ThemeSpec.PALETTE.size (); i++) {
			out.append (i == 0 ? "\n" : ",\n").append ("    \"").append (M3ThemeSpec.PALETTE.get (i)).append ("\": \"").append (M3Palette.text (theme.palette () [i])).append ('"');
		}
		out.append ("\n  },\n  \"ui\": {");
		String separator = "\n";
		for (Map.Entry<String, Map<String, String>> component : ui.entrySet ()) {
			out.append (separator).append ("    \"").append (component.getKey ()).append ("\": {");
			String inner = " ";
			for (Map.Entry<String, String> property : component.getValue ().entrySet ()) {
				out.append (inner).append ('"').append (property.getKey ()).append ("\": ").append (property.getValue ());
				inner = ", ";
			}
			out.append (" }");
			separator = ",\n";
		}
		return out.append ("\n  }\n}\n").toString ();
	}

	private static StringBuilder header (M3ThemeCompleter.Theme theme, String comment) {
		return new StringBuilder ().append (comment).append (' ').append (theme.name ()).append ('\n').append (comment)
			.append (" M3ThemeCompleter ").append (M3ThemeCompleter.VERSION).append (" sha256 ").append (theme.hash ()).append ('\n');
	}

	private static void variables (StringBuilder out, M3ThemeCompleter.Theme theme, String accent) {
		out.append ("@background = ").append (M3Palette.text (theme.palette () [0])).append ('\n');
		out.append ("@foreground = ").append (M3Palette.text (theme.palette () [1])).append ('\n');
		out.append (accent).append (" = ").append (M3Palette.text (theme.palette () [2])).append ('\n');
	}

	/* a table value as JSON: numbers and booleans bare, colours and insets as strings */
	private static String json (String value) {
		return value.matches ("-?\\d+|true|false") ? value : '"' + value + '"';
	}
}
