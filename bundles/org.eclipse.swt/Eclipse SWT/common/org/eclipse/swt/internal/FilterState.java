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
package org.eclipse.swt.internal;

import java.util.*;

/*
 * The criteria of a ColumnFilter as text, and back. One header line, then one line per criterion (the
 * multi-column one first, then the columns in order):
 *
 *   ColumnFilter/1 <columns>
 *   <column> <logic> <suspended 0|1> T <mode> <n> <column>... <length>:<pattern>     a text match
 *   <column> <logic> <suspended 0|1> V <n> <length>:<value>...                        a value list, sorted
 *   <column> <logic> <suspended 0|1> I                                                value ids (the ids stay with the application)
 *   <column> <logic> <suspended 0|1> N                                                no criterion
 *
 * Texts carry their length, so a pattern may contain any character. Reading checks everything before it
 * returns: a text that is not a state of a filter with that many columns is rejected as a whole.
 */
final class FilterState {

	static final String HEADER = "ColumnFilter/1 "; //$NON-NLS-1$

	/* the kinds of a criterion line */
	static final char TEXT = 'T', VALUES = 'V', IDS = 'I', NONE = 'N';

	private final String state;
	private int at;

	private FilterState (String state) {
		this.state = state;
	}

	static String save (FilterCriterion [] criteria) {
		StringBuilder out = new StringBuilder (HEADER).append (criteria.length - 1).append ('\n');
		for (int i = 0; i < criteria.length; i++) {
			FilterCriterion criterion = criteria [i];
			out.append (i - 1).append (' ').append (criterion.logic).append (' ').append (criterion.suspended ? 1 : 0).append (' ');
			if (criterion.match != null) {
				out.append (TEXT).append (' ').append (criterion.match.mode ()).append (' ').append (criterion.columns.length);
				for (int column : criterion.columns) out.append (' ').append (column);
				text (out, criterion.match.pattern ());
			} else if (criterion.values != null) {
				String [] values = criterion.values.toArray (new String [0]);
				Arrays.sort (values);
				out.append (VALUES).append (' ').append (values.length);
				for (String value : values) text (out, value);
			} else {
				out.append (criterion.valueId != null ? IDS : NONE);
			}
			out.append ('\n');
		}
		return out.toString ();
	}

	private static void text (StringBuilder out, String text) {
		out.append (' ').append (text.length ()).append (':').append (text);
	}

	/*
	 * Reads a state into one criterion per index: logic, suspended, and a match with its columns or a value set.
	 * kinds receives the kind of each line. Throws IllegalArgumentException for anything that save does not write
	 * for a filter of that many columns.
	 */
	static FilterCriterion [] parse (String state, int columnCount, boolean hasText, char [] kinds) {
		FilterState in = new FilterState (state);
		try {
			RowChecks.argument (state.startsWith (HEADER));
			in.at = HEADER.length ();
			RowChecks.argument (in.number () == columnCount);
			in.expect ('\n');
			FilterCriterion [] criteria = new FilterCriterion [columnCount + 1];
			for (int i = 0; i < criteria.length; i++) {
				criteria [i] = new FilterCriterion ();
				RowChecks.argument (in.number () == i - 1);
				in.expect (' ');
				kinds [i] = in.criterion (criteria [i], i - 1, columnCount, hasText);
				in.expect ('\n');
			}
			RowChecks.argument (in.at == state.length ());
			return criteria;
		} catch (IndexOutOfBoundsException | NumberFormatException e) {
			RowChecks.argument (false);
			return null;
		}
	}

	/* <logic> <suspended> <kind> and what the kind carries; returns the kind */
	private char criterion (FilterCriterion criterion, int column, int columnCount, boolean hasText) {
		criterion.logic = number ();
		RowChecks.argument (criterion.logic == ColumnFilter.AND || criterion.logic == ColumnFilter.OR);
		expect (' ');
		int suspended = number ();
		RowChecks.argument (suspended == 0 || suspended == 1);
		criterion.suspended = suspended == 1;
		expect (' ');
		char kind = next ();
		if (kind == TEXT) {
			RowChecks.argument (hasText);
			expect (' ');
			int mode = number ();
			expect (' ');
			int [] columns = new int [RowChecks.range (number () - 1, columnCount) + 1];
			for (int c = 0; c < columns.length; c++) {
				expect (' ');
				columns [c] = RowChecks.range (number (), columnCount);
			}
			RowChecks.argument (column == ColumnFilter.ALL_COLUMNS || columns.length == 1 && columns [0] == column);
			expect (' ');
			TextMatch match = TextMatch.compile (text (), mode);
			if (!match.isActive ()) return NONE;
			criterion.match = match;
			criterion.columns = columns;
		} else if (kind == VALUES) {
			RowChecks.argument (hasText && column != ColumnFilter.ALL_COLUMNS);
			expect (' ');
			int count = number ();
			RowChecks.argument (count >= 0);
			Set<String> values = new HashSet<> ();
			for (int v = 0; v < count; v++) {
				expect (' ');
				values.add (text ());
			}
			RowChecks.argument (values.size () == count);
			criterion.values = values;
			criterion.columns = new int [] {column};
		} else {
			RowChecks.argument (kind == NONE || kind == IDS && column != ColumnFilter.ALL_COLUMNS);
		}
		return kind;
	}

	private char next () {
		return state.charAt (at++);
	}

	private void expect (char c) {
		RowChecks.argument (next () == c);
	}

	/* a decimal number */
	private int number () {
		int start = at;
		if (state.charAt (at) == '-') at++;
		while (at < state.length () && state.charAt (at) >= '0' && state.charAt (at) <= '9') at++;
		return Integer.parseInt (state.substring (start, at));
	}

	/* <length>:<text> */
	private String text () {
		int length = number ();
		expect (':');
		RowChecks.argument (length >= 0 && length <= state.length () - at);
		String text = state.substring (at, at + length);
		at += length;
		return text;
	}
}
