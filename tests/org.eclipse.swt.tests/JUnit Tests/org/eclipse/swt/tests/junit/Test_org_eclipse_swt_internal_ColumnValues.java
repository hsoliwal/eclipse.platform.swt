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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.eclipse.swt.internal.*;
import org.junit.jupiter.api.*;

/** Headless tests of {@link ColumnValues} and {@link ColumnFilter#matchesExcept(int, int)}: no display, no widget. */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ColumnValues {

	static final int COLUMNS = 3;
	static final String [] WORDS = {"a", "ab", "b", "B", "ba", "c", "", null};

	static String [][] rows (Random random, int n) {
		String [][] rows = new String [n][COLUMNS];
		for (String [] row : rows) {
			for (int c = 0; c < COLUMNS; c++) row [c] = WORDS [random.nextInt (WORDS.length)];
		}
		return rows;
	}

	static void randomCriterion (ColumnFilter filter, Random random) {
		int column = random.nextInt (COLUMNS + 1) - 1;
		int [] modes = {TextMatch.CONTAINS, TextMatch.STARTS_WITH, TextMatch.EXACT};
		int mode = modes [random.nextInt (modes.length)] | (random.nextInt (4) == 0 ? TextMatch.NEGATE : 0) | (random.nextBoolean () ? TextMatch.IGNORE_CASE : 0);
		String word = WORDS [random.nextInt (WORDS.length - 2)];
		switch (random.nextInt (5)) {
			case 0, 1 -> {
				if (column == ColumnFilter.ALL_COLUMNS) filter.setText (new int [] {0, 2}, word, mode);
				else filter.setText (column, word, mode);
			}
			case 2 -> {
				if (column >= 0) filter.setValues (column, word, WORDS [random.nextInt (WORDS.length - 1)]);
			}
			case 3 -> filter.setLogic (column, random.nextBoolean () ? ColumnFilter.AND : ColumnFilter.OR);
			default -> filter.setSuspended (column, random.nextBoolean ());
		}
	}

	@Test
	void matchesExceptEqualsTheFilterWithoutThatColumnsCriterion () {
		Random random = new Random (11);
		int checked = 0, differs = 0;
		for (int round = 0; round < 60; round++) {
			String [][] rows = rows (random, random.nextInt (150));
			CellText text = (row, column) -> rows [row][column];
			ColumnFilter filter = new ColumnFilter (COLUMNS, rows.length, text);
			for (int step = 1 + random.nextInt (8); step > 0; step--) randomCriterion (filter, random);
			for (int column = ColumnFilter.ALL_COLUMNS; column < COLUMNS; column++) {
				ColumnFilter without = new ColumnFilter (COLUMNS, rows.length, text);
				without.restoreState (filter.saveState ());
				without.clear (column);
				for (int row = 0; row < rows.length; row++) {
					assertEquals (without.matches (row), filter.matchesExcept (row, column), "round " + round + " column " + column + " row " + row + "\n" + filter.saveState ());
					if (filter.matches (row) != without.matches (row)) differs++;
					checked++;
				}
			}
		}
		assertTrue (checked > 10_000 && differs > 500, checked + " checked, " + differs + " where leaving the criterion out matters");
	}

	@Test
	void matchesExceptChecksItsArgumentsAndLeavesTheViewAlone () {
		ColumnFilter filter = new ColumnFilter (2, 3, (row, column) -> "x" + row);
		filter.setText (0, "x1", TextMatch.EXACT);
		assertEquals (1, filter.size ());
		assertTrue (filter.matchesExcept (2, 0));
		assertFalse (filter.matchesExcept (2, 1), "column 1 has no criterion: the filter as it is");
		assertEquals (1, filter.size ());
		assertThrows (IllegalArgumentException.class, () -> filter.matchesExcept (3, 0));
		assertThrows (IllegalArgumentException.class, () -> filter.matchesExcept (0, 2));
		assertThrows (IllegalArgumentException.class, () -> filter.matchesExcept (0, -2));
	}

	@Test
	void valuesAreCountedPerTextInCollatorOrder () {
		Locale before = Locale.getDefault ();
		Locale.setDefault (Locale.ENGLISH);
		try {
			String [] cells = {"b", null, "B", "ä", "", "a", "b"};
			ColumnValues values = new ColumnValues ((row, column) -> column == 1 ? cells [row] : "other", 1, cells.length, null);
			String [] seen = new String [values.size ()];
			int [] counts = new int [values.size ()];
			for (int i = 0; i < seen.length; i++) {
				seen [i] = values.getValue (i);
				counts [i] = values.getCount (i);
			}
			assertArrayEquals (new String [] {"", "a", "ä", "b", "B"}, seen, "an empty cell is the empty text");
			assertArrayEquals (new int [] {2, 1, 1, 2, 1}, counts);
			assertEquals (cells.length, values.getTotal ());
			assertEquals (1, values.getColumn ());
			for (int i = 0; i < seen.length; i++) assertEquals (i, values.indexOf (seen [i]));
			assertEquals (0, values.indexOf (null));
			assertEquals (-1, values.indexOf ("c"));
			assertEquals (-1, values.indexOf ("A"));
		} finally {
			Locale.setDefault (before);
		}
	}

	@Test
	void aPredicateChoosesTheRowsCounted () {
		ColumnValues values = new ColumnValues ((row, column) -> row % 3 == 0 ? "three" : "other", 0, 10, row -> row % 2 == 0);
		assertEquals (5, values.getTotal ());
		assertEquals (2, values.getCount (values.indexOf ("three")), "rows 0 and 6");
		assertEquals (3, values.getCount (values.indexOf ("other")), "rows 2, 4 and 8");
		assertEquals (0, new ColumnValues ((row, column) -> "x", 0, 0, null).size ());
	}

	@Test
	void ofOffersTheValuesTheColumnsOwnCriterionHides () {
		String [][] rows = {{"a", "x"}, {"b", "x"}, {"b", "y"}, {"c", "y"}, {"a", "y"}, {null, "x"}};
		ColumnFilter filter = new ColumnFilter (2, rows.length, (row, column) -> rows [row][column]);
		filter.setValues (0, "a");
		filter.setText (1, "x", TextMatch.EXACT);
		assertEquals (1, filter.size ());
		ColumnValues first = ColumnValues.of (filter, 0);
		assertEquals (3, first.size (), "the values of the rows with x: a, b and the empty cell");
		assertEquals (1, first.getCount (first.indexOf ("b")));
		assertEquals (1, first.getCount (first.indexOf ("")));
		assertEquals (-1, first.indexOf ("c"), "c is only on a row without x");
		ColumnValues second = ColumnValues.of (filter, 1);
		assertEquals (2, second.getTotal (), "the rows with a");
		assertEquals (1, second.getCount (second.indexOf ("y")), "y is offered though the column's own criterion hides it");
		assertEquals (1, filter.size (), "the view did not change");
	}

	@Test
	void argumentsAreChecked () {
		assertThrows (IllegalArgumentException.class, () -> new ColumnValues (null, 0, 1, null));
		assertThrows (IllegalArgumentException.class, () -> new ColumnValues ((r, c) -> "", -1, 1, null));
		assertThrows (IllegalArgumentException.class, () -> new ColumnValues ((r, c) -> "", 0, -1, null));
		ColumnFilter ids = new ColumnFilter (1, 2, null);
		assertThrows (IllegalArgumentException.class, () -> ColumnValues.of (ids, 0), "a filter without cell text");
		ColumnFilter filter = new ColumnFilter (1, 2, (r, c) -> "v");
		assertThrows (IllegalArgumentException.class, () -> ColumnValues.of (filter, 1));
		assertThrows (IllegalArgumentException.class, () -> ColumnValues.of (null, 0));
		ColumnValues values = ColumnValues.of (filter, 0);
		assertThrows (IllegalArgumentException.class, () -> values.getValue (1));
		assertThrows (IllegalArgumentException.class, () -> values.getCount (-1));
	}
}
