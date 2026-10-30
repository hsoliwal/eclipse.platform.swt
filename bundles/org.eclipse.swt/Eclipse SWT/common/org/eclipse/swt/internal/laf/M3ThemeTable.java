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
 * Reads a user theme file of <code>org.eclipse.swt.m3.M3Theme</code> (docs/m3/theme-language/README.md) into ordered
 * key/value text: either properties (<code>key = value</code>, <code>#</code> or <code>!</code> comment lines, a later
 * line overrides an earlier one) or one JSON object whose members are keys, with an optional <code>"slots"</code>
 * object whose members are keys too. JSON is read by {@link M3Json}; numbers and booleans become their text.
 */
//FLLDCIM: SFA — theme files are data: keys and values stay text until M3Theme interprets them
public final class M3ThemeTable {

	private M3ThemeTable () {
	}

	/**
	 * @param text the file contents
	 * @return the keys in file order (the last value of a repeated key wins)
	 * @throws IllegalArgumentException when JSON is malformed, not an object, or a value is an array, object or null
	 */
	public static Map<String, String> read (String text) {
		Objects.requireNonNull (text, "text");
		String trimmed = text.strip ();
		return trimmed.startsWith ("{") ? json (trimmed) : properties (text);
	}

	private static Map<String, String> properties (String text) {
		Map<String, String> table = new LinkedHashMap<> ();
		text.lines ().map (String::strip).filter (line -> !line.isEmpty () && line.charAt (0) != '#' && line.charAt (0) != '!')
			.forEach (line -> entry (table, line));
		return table;
	}

	private static void entry (Map<String, String> table, String line) {
		int at = line.indexOf ('=');
		if (at < 0) at = line.indexOf (':');
		if (at <= 0) throw new IllegalArgumentException ("theme line without key = value: '" + line + "'");
		table.put (line.substring (0, at).strip (), line.substring (at + 1).strip ());
	}

	private static Map<String, String> json (String text) {
		if (!(M3Json.parse (text) instanceof Map<?, ?> top)) throw new IllegalArgumentException ("a theme is a JSON object");
		Map<String, String> table = new LinkedHashMap<> ();
		top.forEach ((key, value) -> member (table, (String) key, value, true));
		return table;
	}

	private static void member (Map<String, String> table, String key, Object value, boolean top) {
		if (top && "slots".equals (key) && value instanceof Map<?, ?> slots) {
			slots.forEach ((slot, slotValue) -> member (table, (String) slot, slotValue, false));
			return;
		}
		if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			table.put (key, value.toString ());
			return;
		}
		throw new IllegalArgumentException ("theme member '" + key + "' must be a string, number or boolean");
	}
}
