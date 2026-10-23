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
 * A small JSON reader for theme files (docs/m3/LOOK_AND_FEEL.md, LF6, IntelliJ <code>.theme.json</code>): objects
 * (in order, as <code>LinkedHashMap</code>), arrays (<code>ArrayList</code>), strings with escapes, numbers
 * (<code>Long</code> when integral, else <code>Double</code>), <code>true</code>, <code>false</code>, <code>null</code>.
 * Written for this package; FlatLaf's reader (derived from minimal-json) is the pattern
 * (look-and-feel/FOSS_REUSE_DECISION.tsv, json_reader).
 */
final class M3Json {
	private final String text;
	private int at;

	private M3Json (String text) {
		this.text = text;
	}

	/**
	 * Reads one JSON value that fills the text (whitespace around it allowed).
	 *
	 * @throws IllegalArgumentException with the offset when the text is not JSON
	 */
	static Object parse (String text) {
		M3Json reader = new M3Json (text);
		Object value = reader.value ();
		reader.space ();
		if (reader.at != text.length ()) throw reader.error ("text after the value");
		return value;
	}

	private Object value () {
		space ();
		if (at >= text.length ()) throw error ("a value expected");
		char c = text.charAt (at);
		switch (c) {
			case '{': return object ();
			case '[': return array ();
			case '"': return string ();
			case 't': return literal ("true", Boolean.TRUE);
			case 'f': return literal ("false", Boolean.FALSE);
			case 'n': return literal ("null", null);
			default: return number ();
		}
	}

	private Map<String, Object> object () {
		Map<String, Object> map = new LinkedHashMap<> ();
		at++;
		space ();
		if (peek ('}')) return map;
		do {
			space ();
			if (at >= text.length () || text.charAt (at) != '"') throw error ("a name expected");
			String name = string ();
			space ();
			expect (':');
			map.put (name, value ());
			space ();
		} while (peek (','));
		expect ('}');
		return map;
	}

	private List<Object> array () {
		List<Object> list = new ArrayList<> ();
		at++;
		space ();
		if (peek (']')) return list;
		do {
			list.add (value ());
			space ();
		} while (peek (','));
		expect (']');
		return list;
	}

	private String string () {
		StringBuilder out = new StringBuilder ();
		at++;
		while (at < text.length ()) {
			char c = text.charAt (at++);
			if (c == '"') return out.toString ();
			if (c != '\\') {
				out.append (c);
				continue;
			}
			if (at >= text.length ()) break;
			char e = text.charAt (at++);
			switch (e) {
				case 'b': out.append ('\b'); break;
				case 'f': out.append ('\f'); break;
				case 'n': out.append ('\n'); break;
				case 'r': out.append ('\r'); break;
				case 't': out.append ('\t'); break;
				case 'u': out.append (unicode ()); break;
				default: out.append (e);
			}
		}
		throw error ("an unterminated string");
	}

	private char unicode () {
		if (at + 4 > text.length ()) throw error ("a short unicode escape");
		try {
			char c = (char) Integer.parseInt (text.substring (at, at + 4), 16);
			at += 4;
			return c;
		} catch (NumberFormatException e) {
			throw error ("a bad unicode escape");
		}
	}

	private Object number () {
		int start = at;
		while (at < text.length () && "+-0123456789.eE".indexOf (text.charAt (at)) >= 0) at++;
		String number = text.substring (start, at);
		try {
			if (number.matches ("-?\\d+")) return Long.valueOf (number);
			return Double.valueOf (number);
		} catch (NumberFormatException e) {
			at = start;
			throw error ("a value expected");
		}
	}

	private Object literal (String word, Object value) {
		if (!text.startsWith (word, at)) throw error ("a value expected");
		at += word.length ();
		return value;
	}

	private void space () {
		while (at < text.length () && Character.isWhitespace (text.charAt (at))) at++;
	}

	private boolean peek (char c) {
		if (at < text.length () && text.charAt (at) == c) {
			at++;
			return true;
		}
		return false;
	}

	private void expect (char c) {
		if (!peek (c)) throw error ("'" + c + "' expected");
	}

	private IllegalArgumentException error (String what) {
		return new IllegalArgumentException ("JSON at offset " + at + ": " + what);
	}
}
