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
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.junit.jupiter.api.*;

/** Headless tests of {@link ColumnFilter}, {@link RowWindow} and the filter-sort-window chain. */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ColumnFilter {

	static final String [][] CELLS = {
		{"apple", "red", "1"},
		{"banana", "yellow", "2"},
		{"cherry", "red", "3"},
		{"date", "brown", "4"},
		{"elder", "black", "5"},
		{"fig", "purple", null},
	};

	static ColumnFilter fruits () {
		return new ColumnFilter (3, CELLS.length, (row, column) -> CELLS [row][column]);
	}

	static int [] rows (RowView view) {
		return Test_org_eclipse_swt_internal_ColumnSort.order (view);
	}

	static CellText text (Test_org_eclipse_swt_internal_ColumnSort.Rows rows) {
		return (row, column) -> switch (column) {
			case 0 -> String.valueOf (rows.ints.get (row));
			case 1 -> String.valueOf (rows.longs.get (row));
			case 2 -> String.valueOf (rows.doubles.get (row));
			default -> rows.texts.get (row);
		};
	}

	@Test
	public void test_textCriteriaPerColumn () {
		ColumnFilter filter = fruits ();
		assertArrayEquals (new int [] {0, 1, 2, 3, 4, 5}, rows (filter));
		filter.setText (1, "red", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		filter.setText (0, "c*", TextMatch.WILDCARD);
		assertArrayEquals (new int [] {2}, rows (filter));
		filter.setText (0, "a.*,b.*", TextMatch.REGEX);
		assertArrayEquals (new int [] {0}, rows (filter));
		filter.setText (0, "A", TextMatch.CONTAINS | TextMatch.IGNORE_CASE | TextMatch.NEGATE);
		assertArrayEquals (new int [] {2}, rows (filter));
		assertTrue (filter.isActive (0));
		assertEquals ("A", filter.getMatch (0).pattern ());
		filter.setText (0, null, TextMatch.CONTAINS);
		assertFalse (filter.isActive (0));
		assertNull (filter.getMatch (0));
		filter.setText (0, ",", TextMatch.CONTAINS);
		assertFalse (filter.isActive (0));
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		filter.setText (2, "", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		filter.clear (1);
		assertEquals (6, filter.size ());
		assertEquals (3, filter.getColumnCount ());
		assertTrue (filter.toString ().contains ("6 of 6"));
	}

	@Test
	public void test_suspendedCriterionKeepsItsPatternAndRejoinsWithoutTesting () {
		int [] reads = new int [1];
		ColumnFilter filter = new ColumnFilter (3, CELLS.length, (row, column) -> {
			reads [0]++;
			return CELLS [row][column];
		});
		filter.setText (1, "red", TextMatch.EXACT);
		filter.setText (0, "c*", TextMatch.WILDCARD);
		assertArrayEquals (new int [] {2}, rows (filter));
		assertFalse (filter.isSuspended (0));

		int before = reads [0];
		filter.setSuspended (0, true);
		assertArrayEquals (new int [] {0, 2}, rows (filter), "the other criterion still filters");
		assertTrue (filter.isSuspended (0));
		assertTrue (filter.isActive (0), "a suspended criterion keeps its specification");
		assertEquals ("c*", filter.getMatch (0).pattern ());
		filter.setSuspended (0, true);
		filter.setSuspended (1, true);
		assertArrayEquals (new int [] {0, 1, 2, 3, 4, 5}, rows (filter), "every row passes when all criteria are suspended");
		filter.setSuspended (1, false);
		filter.setSuspended (0, false);
		assertArrayEquals (new int [] {2}, rows (filter));
		assertEquals (before, reads [0], "suspending and resuming read no cell");

		/* the rows a suspended criterion matches follow the source */
		filter.setSuspended (0, true);
		filter.setLogic (1, ColumnFilter.OR);
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		filter.setSuspended (0, false);
		assertArrayEquals (new int [] {0, 2}, rows (filter), "c* OR red");
		filter.setText (1, "yellow", TextMatch.EXACT);
		assertArrayEquals (new int [] {1, 2}, rows (filter), "c* OR yellow");

		/* setting or clearing the criterion resumes it */
		filter.setSuspended (0, true);
		filter.setText (0, "d*", TextMatch.WILDCARD);
		assertFalse (filter.isSuspended (0));
		filter.setSuspended (0, true);
		filter.clear (0);
		assertFalse (filter.isSuspended (0));
		assertFalse (filter.isActive (0));

		filter.setText (new int [] {0, 1}, "e", TextMatch.CONTAINS);
		filter.setSuspended (ColumnFilter.ALL_COLUMNS, true);
		assertTrue (filter.isSuspended (ColumnFilter.ALL_COLUMNS));
		assertArrayEquals (new int [] {1}, rows (filter), "only the column criterion (yellow) filters");
		assertThrows (IllegalArgumentException.class, () -> filter.setSuspended (3, true));
		assertThrows (IllegalArgumentException.class, () -> filter.isSuspended (-2));
	}

	@Test
	public void test_multiColumnCriterionAndLogic () {
		ColumnFilter filter = fruits ();
		filter.setText (new int [] {0, 1}, "r", TextMatch.CONTAINS);
		assertArrayEquals (new int [] {0, 2, 3, 4, 5}, rows (filter));
		assertArrayEquals (new int [] {0, 1}, filter.getColumns ());
		assertTrue (filter.isActive (ColumnFilter.ALL_COLUMNS));
		filter.setText (new int [] {0, 1}, "r", TextMatch.CONTAINS | TextMatch.NEGATE);
		assertArrayEquals (new int [] {1}, rows (filter));
		filter.setText (new int [] {0, 1}, "e", TextMatch.CONTAINS);
		filter.setText (1, "red", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		filter.setLogic (1, ColumnFilter.OR);
		assertEquals (ColumnFilter.OR, filter.getLogic (1));
		assertArrayEquals (new int [] {0, 1, 2, 3, 4, 5}, rows (filter));
		filter.setText (2, "5", TextMatch.EXACT);
		assertArrayEquals (new int [] {4}, rows (filter));
		filter.setLogic (ColumnFilter.ALL_COLUMNS, ColumnFilter.OR);
		assertArrayEquals (new int [] {4}, rows (filter));
		filter.clear (ColumnFilter.ALL_COLUMNS);
		assertArrayEquals (new int [0], filter.getColumns ());
		filter.setLogic (2, ColumnFilter.OR);
		assertArrayEquals (new int [] {0, 2, 4}, rows (filter));
		filter.clearAll ();
		assertEquals (6, filter.size ());
	}

	@Test
	public void test_valueLists () {
		ColumnFilter filter = fruits ();
		filter.setValues (1, "red", "black");
		assertArrayEquals (new int [] {0, 2, 4}, rows (filter));
		filter.setValues (2, "");
		assertArrayEquals (new int [0], rows (filter));
		filter.setValues (2, (String []) null);
		filter.setValues (1, (String []) null);
		assertFalse (filter.isActive (1));
		int [] ids = {3, -1, 70, 0, 3, 64};
		filter.setValues (0, row -> ids [row], 3, 64, 0);
		assertArrayEquals (new int [] {0, 3, 4, 5}, rows (filter));
		filter.setValues (0, row -> ids [row]);
		assertArrayEquals (new int [0], rows (filter));
		assertThrows (IllegalArgumentException.class, () -> filter.setValues (0, row -> 0, -1));
		assertThrows (IllegalArgumentException.class, () -> filter.setValues (0, null, 1));
		assertThrows (IllegalArgumentException.class, () -> filter.setValues (0, row -> 0, (int []) null));
		ColumnFilter idsOnly = new ColumnFilter (1, 6, null);
		idsOnly.setValues (0, row -> ids [row], 70);
		assertArrayEquals (new int [] {2}, rows (idsOnly));
		assertThrows (IllegalArgumentException.class, () -> idsOnly.setValues (0, "x"));
		assertThrows (IllegalArgumentException.class, () -> idsOnly.setText (0, "x", TextMatch.CONTAINS));
		idsOnly.setText (0, null, TextMatch.CONTAINS);
		idsOnly.setValues (0, (String []) null);
		assertEquals (6, idsOnly.size ());
	}

	@Test
	public void test_argumentChecks () {
		ColumnFilter filter = fruits ();
		assertThrows (IllegalArgumentException.class, () -> new ColumnFilter (-1, 0, null));
		assertThrows (IllegalArgumentException.class, () -> new ColumnFilter (1, -1, null));
		assertThrows (IllegalArgumentException.class, () -> filter.setText (3, "x", 0));
		assertThrows (IllegalArgumentException.class, () -> filter.setText (-1, "x", 0));
		assertThrows (IllegalArgumentException.class, () -> filter.setText ((int []) null, "x", 0));
		assertThrows (IllegalArgumentException.class, () -> filter.setText (new int [] {3}, "x", 0));
		assertThrows (IllegalArgumentException.class, () -> filter.setText (0, "(", TextMatch.REGEX));
		assertThrows (IllegalArgumentException.class, () -> filter.setValues (3, "x"));
		assertThrows (IllegalArgumentException.class, () -> filter.clear (-2));
		assertThrows (IllegalArgumentException.class, () -> filter.clear (3));
		assertThrows (IllegalArgumentException.class, () -> filter.setLogic (0, 2));
		assertThrows (IllegalArgumentException.class, () -> filter.setLogic (0, -1));
		assertThrows (IllegalArgumentException.class, () -> filter.getLogic (4));
		assertThrows (IllegalArgumentException.class, () -> filter.isActive (4));
		assertThrows (IllegalArgumentException.class, () -> filter.getMatch (4));
		assertThrows (IllegalArgumentException.class, () -> filter.matches (6));
		assertThrows (IllegalArgumentException.class, () -> filter.map (6));
		assertThrows (IllegalArgumentException.class, () -> filter.unmap (-1));
		filter.setText (0, "a", TextMatch.CONTAINS);
		assertEquals (-1, filter.unmap (2));
		assertEquals (1, filter.unmap (1));
		assertTrue (filter.matches (3));
		assertFalse (filter.matches (5));
	}

	@Test
	public void test_treeKeepsAncestorsOfMatches () {
		/* 0 [1 [2, 3], 4], 5 [6], 7 */
		int [] parents = {-1, 0, 1, 1, 0, -1, 5, -1};
		String [] names = {"root", "dir", "match", "other", "file", "top", "x", "lone"};
		ColumnFilter filter = new ColumnFilter (1, 8, (row, column) -> names [row]);
		filter.setParents (row -> parents [row]);
		filter.setText (0, "match", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 1, 2}, rows (filter));
		assertTrue (filter.matches (2));
		assertFalse (filter.matches (1));
		filter.setText (0, "match,x", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 1, 2, 5, 6}, rows (filter));
		filter.setText (0, "dir", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 1}, rows (filter));
		filter.setParents (null);
		assertArrayEquals (new int [] {1}, rows (filter));
		parents [3] = 8;
		filter.setText (0, "other", TextMatch.EXACT);
		assertThrows (IllegalArgumentException.class, () -> filter.setParents (row -> parents [row]));
		parents [3] = -2;
		assertThrows (IllegalArgumentException.class, () -> filter.setParents (row -> parents [row]));
	}

	@Test
	public void test_treeInsertMakesALaterAncestorEnter () {
		List<String> names = new ArrayList<> (List.of ("a", "b"));
		List<Integer> parents = new ArrayList<> (List.of (-1, -1));
		ColumnFilter filter = new ColumnFilter (1, 2, (row, column) -> names.get (row));
		filter.setParents (parents::get);
		filter.setText (0, "match", TextMatch.EXACT);
		assertEquals (0, filter.size ());
		List<String> log = new ArrayList<> ();
		filter.addListener (change -> {
			StringBuilder rows = new StringBuilder ();
			for (int i = 0; i < change.rowCount (); i++) rows.append (change.row (i));
			log.add (change.kind () + ":" + rows);
		});
		names.add (0, "match");
		parents.add (0, 2);
		filter.rowsInserted (0, 1);
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		assertEquals (List.of ("2:0", "4:2"), log);
		log.clear ();
		names.remove (0);
		parents.remove (0);
		filter.rowsRemoved (0, 1);
		assertEquals (0, filter.size ());
		assertEquals (List.of ("3:", "5:1"), log);
	}

	@Test
	public void test_treeChainWithSortedSiblings () {
		int [] parents = {-1, 0, 1, 1, 0, -1, 5, -1};
		String [] names = {"root", "dir", "match", "amatch", "file", "top", "x", "lone"};
		ColumnFilter filter = new ColumnFilter (1, 8, (row, column) -> names [row]);
		filter.setParents (row -> parents [row]);
		ColumnSort sort = new ColumnSort (1, filter);
		sort.setParents (row -> parents [row]);
		sort.setCellText ((row, column) -> names [row]);
		sort.toggle (0, false);
		filter.setText (0, "*match", TextMatch.WILDCARD);
		assertEquals (1, sort.childCount (-1));
		assertEquals (0, sort.child (-1, 0));
		assertEquals (1, sort.childCount (0));
		assertEquals (3, sort.child (1, 0));
		assertEquals (2, sort.child (1, 1));
		names [6] = "xmatch";
		sort.rowsChanged (6);
		assertEquals (2, sort.childCount (-1));
		assertEquals (5, sort.child (-1, 1));
		assertEquals (6, sort.child (5, 0));
		names [2] = "gone";
		names [3] = "gone";
		filter.rowsChanged (2, 3);
		assertEquals (1, sort.childCount (-1));
		assertEquals (-1, sort.unmap (0));
		assertEquals (-1, sort.indexOfChild (1));
	}

	@Test
	public void test_changeEventsAndDamage () {
		List<String []> cells = new ArrayList<> ();
		for (String [] row : CELLS) cells.add (row.clone ());
		ColumnFilter filter = new ColumnFilter (3, cells.size (), (row, column) -> cells.get (row) [column]);
		List<String> log = new ArrayList<> ();
		filter.addListener (change -> {
			StringBuilder rows = new StringBuilder ();
			for (int i = 0; i < change.rowCount (); i++) rows.append (change.row (i));
			log.add (change.kind () + ":" + rows + ":" + change.firstDamaged () + ".." + change.lastDamaged ());
			assertSame (filter, change.view ());
		});
		filter.setText (1, "red,yellow", TextMatch.EXACT);
		filter.setText (1, "red,brown", TextMatch.EXACT);
		assertEquals (List.of ("5:345:-1..-1", "5:1:1..2", "4:3:2..2"), log);
		log.clear ();
		cells.get (3) [1] = "red";
		filter.rowsChanged (3, 0);
		assertEquals (List.of ("6:03:0..2"), log);
		log.clear ();
		cells.add (1, new String [] {"kiwi", "green", "9"});
		cells.add (2, new String [] {"lime", "red", "7"});
		filter.rowsInserted (1, 2);
		cells.remove (1);
		cells.remove (1);
		filter.rowsRemoved (1, 2);
		filter.setSourceSize (6);
		cells.get (3) [1] = "brown";
		filter.rowsChanged (3);
		filter.rowsChanged (1);
		assertEquals (List.of ("2:2:1..3", "3::1..2", "1::0..2", "6:3:2..2"), log);
	}

	/* a filter of four columns over random rows with some criteria, logic changes and suspensions */
	static ColumnFilter randomFilter (CellText text, int n, Random random) {
		ColumnFilter filter = new ColumnFilter (4, n, text);
		for (int i = random.nextInt (7); i > 0; i--) criterion (filter, random);
		for (int column = -1; column < 4; column++) {
			if (filter.isActive (column) && random.nextInt (4) == 0) filter.setSuspended (column, true);
		}
		return filter;
	}

	@Test
	public void test_savedStateRestoresTheSameFilter () {
		Random random = new Random (5);
		for (int round = 0; round < 80; round++) {
			int n = 40 + random.nextInt (300);
			Test_org_eclipse_swt_internal_ColumnSort.Rows rows = new Test_org_eclipse_swt_internal_ColumnSort.Rows (n, round, 7);
			CellText text = text (rows);
			ColumnFilter filter = randomFilter (text, n, random);
			String state = filter.saveState ();

			/* into a filter with other criteria: everything is replaced */
			ColumnFilter restored = randomFilter (text, n, random);
			restored.restoreState (state);
			assertEquals (state, restored.saveState (), "round " + round);
			assertArrayEquals (rows (filter), rows (restored), "round " + round);
			for (int column = -1; column < 4; column++) {
				assertEquals (filter.isActive (column), restored.isActive (column));
				assertEquals (filter.getLogic (column), restored.getLogic (column));
				assertEquals (filter.isSuspended (column), restored.isSuspended (column));
			}
			Test_org_eclipse_swt_internal_ColumnSort.assertInverse (restored);

			/* the restored filter follows source edits like the original */
			if (n > 3) {
				rows.texts.set (2, "t1x");
				rows.ints.set (2, 1);
				filter.rowsChanged (2);
				restored.rowsChanged (2);
				assertArrayEquals (rows (filter), rows (restored), "round " + round + " after an edit");
			}
		}
	}

	@Test
	public void test_restoringTheStateAFilterHasReadsNoCell () {
		int [] reads = new int [1];
		ColumnFilter filter = new ColumnFilter (3, CELLS.length, (row, column) -> {
			reads [0]++;
			return CELLS [row][column];
		});
		filter.setText (1, "red", TextMatch.EXACT);
		filter.setValues (0, "apple", "cherry", "fig");
		filter.setText (new int [] {0, 2}, "e", TextMatch.CONTAINS);
		filter.setLogic (1, ColumnFilter.OR);
		int [] before = rows (filter);
		String state = filter.saveState ();
		int [] events = new int [1];
		filter.addListener (change -> events [0]++);
		reads [0] = 0;
		filter.restoreState (state);
		assertEquals (0, reads [0], "nothing changed: no cell is read");
		assertEquals (0, events [0], "nothing changed: no event");
		assertArrayEquals (before, rows (filter));

		/* only the logic differs: no criterion is tested again (a cell whose test was deferred may be read now) */
		String or = state.replace ("\n0 0 0 V", "\n0 1 0 V");
		assertNotEquals (state, or);
		filter.restoreState (or);
		assertTrue (reads [0] < CELLS.length, "a logic change tests no criterion again: " + reads [0] + " cells read");
		assertEquals (ColumnFilter.OR, filter.getLogic (0));
		assertEquals (or, filter.saveState ());
	}

	@Test
	public void test_stateKeepsAnyPatternText () {
		String [] patterns = {"a b", "3:x", "line\nbreak", " lead", "trail ", "ä中😀", "a,b*", ":", "0"};
		for (String pattern : patterns) {
			ColumnFilter filter = fruits ();
			filter.setText (0, pattern, TextMatch.CONTAINS | TextMatch.IGNORE_CASE);
			filter.setValues (1, pattern, "", "x y");
			ColumnFilter restored = fruits ();
			restored.restoreState (filter.saveState ());
			assertEquals (pattern, restored.getMatch (0).pattern ());
			assertEquals (TextMatch.CONTAINS | TextMatch.IGNORE_CASE, restored.getMatch (0).mode ());
			assertEquals (filter.saveState (), restored.saveState ());
		}
	}

	@Test
	public void test_restoreStateRejectsWhatItCannotRead () {
		ColumnFilter filter = fruits ();
		filter.setText (1, "red", TextMatch.EXACT);
		filter.setText (new int [] {0, 1}, "e", TextMatch.CONTAINS);
		filter.setValues (2, "1", "3");
		String state = filter.saveState ();
		int [] before = rows (filter);
		String [] bad = {
			"", "x", "ColumnFilter/2 3\n", "ColumnFilter/1 4\n" + state.substring (state.indexOf ('\n') + 1),
			state.substring (0, state.length () - 1), state + "x", state + state,
			state.replace ("\n1 0 0 T", "\n1 2 0 T"), state.replace ("\n1 0 0 T", "\n1 0 7 T"), state.replace ("\n1 0 0 T", "\n1 0 0 Q"),
			state.replace ("3:red", "9:red"), state.replace ("3:red", "-1:red"), state.replace ("3:red", "red"),
			state.replace ("\n1 0 0 T 2 1 1 ", "\n1 0 0 T 2 1 2 "), state.replace ("\n1 0 0 T 2 1 1 ", "\n1 0 0 T 2 1 9 "),
			state.replace ("\n1 0 0 T 2 ", "\n1 0 0 T 99 "), state.replace ("\n2 0 0 V 2 ", "\n2 0 0 V 3 "),
			state.replace ("\n0 0 0 N", "\n5 0 0 N"), state.replace ("\n", "\r\n"),
			"ColumnFilter/1 3\n-1 0 0 N\n0 0 0 T 4 1 0 1:(\n1 0 0 N\n2 0 0 N\n",
		};
		for (String text : bad) {
			assertThrows (IllegalArgumentException.class, () -> filter.restoreState (text), text);
			assertEquals (state, filter.saveState (), "a rejected state changes nothing");
			assertArrayEquals (before, rows (filter));
		}
		assertThrows (IllegalArgumentException.class, () -> filter.restoreState (null));
		/* a filter without cell text cannot take text criteria */
		ColumnFilter idsOnly = new ColumnFilter (3, CELLS.length, null);
		assertThrows (IllegalArgumentException.class, () -> idsOnly.restoreState (state));
		idsOnly.restoreState (fruits ().saveState ());
	}

	@Test
	public void test_stateLeavesACriterionOnValueIdsInPlace () {
		ColumnFilter filter = fruits ();
		filter.setValues (2, row -> row % 3, 1);
		filter.setText (0, "a", TextMatch.CONTAINS);
		filter.setLogic (2, ColumnFilter.OR);
		String state = filter.saveState ();
		assertTrue (state.contains ("\n2 1 0 I\n"), state);

		ColumnFilter other = fruits ();
		other.setValues (2, row -> row % 2, 0);
		other.setSuspended (2, true);
		other.setText (1, "red", TextMatch.EXACT);
		other.restoreState (state);
		assertTrue (other.isActive (2), "the criterion on value ids stays");
		assertFalse (other.isSuspended (2));
		assertEquals (ColumnFilter.OR, other.getLogic (2));
		assertFalse (other.isActive (1));
		/* a* OR even rows: apple, banana, cherry (row 2), date, elder (row 4) */
		assertArrayEquals (new int [] {0, 1, 2, 3, 4}, rows (other));

		/* without such a criterion the column has none after the restore */
		ColumnFilter plain = fruits ();
		plain.setText (2, "5", TextMatch.EXACT);
		plain.restoreState (state);
		assertFalse (plain.isActive (2));
		assertArrayEquals (new int [] {0, 1, 3}, rows (plain));
	}

	/* 6,400 rows: column 0 has 64 groups of 100 rows, column 1 the row number, column 2 "even" or "odd" */
	static String shortCircuitCell (int row, int column) {
		return column == 0 ? "g" + row % 64 : column == 1 ? "row" + row : row % 2 == 0 ? "even" : "odd";
	}

	static int [] reference (ColumnFilter filter, CellText text, int n) {
		return java.util.stream.IntStream.range (0, n).filter (row -> suspendable (filter, text, row)).toArray ();
	}

	/*
	 * Short circuit: a changed criterion is tested only on the rows whose result still depends on it. The cell
	 * reads are counted per column; the rows shown always equal the plain fold of every criterion over every row.
	 */
	@Test
	public void test_criteriaAreTestedOnlyWhereTheResultDependsOnThem () {
		int n = 6400;
		int [] reads = new int [3];
		CellText counting = (row, column) -> {
			reads [column]++;
			return shortCircuitCell (row, column);
		};
		CellText plain = Test_org_eclipse_swt_internal_ColumnFilter::shortCircuitCell;

		/* AND, the selective criterion on the left: the next one is tested on the rows it kept */
		ColumnFilter filter = new ColumnFilter (3, n, counting);
		filter.setText (0, "g7", TextMatch.EXACT);
		assertEquals (n, reads [0], "the only criterion is tested on every row");
		assertEquals (100, filter.size ());
		filter.setText (1, "row1", TextMatch.STARTS_WITH);
		assertEquals (100, reads [1], "column 1 is tested on the rows column 0 kept");
		assertArrayEquals (reference (filter, plain, n), rows (filter));

		/* the rows that were not tested are tested when they start to matter, and only those */
		Arrays.fill (reads, 0);
		filter.clear (0);
		assertEquals (n - 100, reads [1], "the other rows of column 1");
		assertEquals (0, reads [0]);
		assertArrayEquals (reference (filter, plain, n), rows (filter));

		/* AND, the selective criterion on the right: it still decides first, because it is known for every row */
		filter = new ColumnFilter (3, n, counting);
		filter.setText (1, "row12", TextMatch.STARTS_WITH);
		int kept = filter.size ();
		assertTrue (kept > 0 && kept < 200, "rows starting with row12: " + kept);
		Arrays.fill (reads, 0);
		filter.setText (0, "g7", TextMatch.EXACT);
		assertEquals (kept, reads [0], "column 0 is tested on the rows column 1 kept");
		assertEquals (0, reads [1]);
		assertArrayEquals (reference (filter, plain, n), rows (filter));

		/* typing on narrows the rows that were tested and passed; no other row is read */
		Arrays.fill (reads, 0);
		int passing = filter.size ();
		filter.setText (0, "g7", TextMatch.STARTS_WITH);
		filter.setText (0, "g70", TextMatch.STARTS_WITH);
		assertTrue (reads [0] <= kept + passing, "narrowing read " + reads [0] + " cells of column 0");
		assertArrayEquals (reference (filter, plain, n), rows (filter));

		/* OR: a criterion is not tested on a row that another one already accepts */
		filter = new ColumnFilter (3, n, counting);
		filter.setText (2, "even", TextMatch.EXACT);
		filter.setLogic (2, ColumnFilter.OR);
		Arrays.fill (reads, 0);
		filter.setText (1, "row1", TextMatch.STARTS_WITH);
		assertEquals (n / 2, reads [1], "column 1 is tested on the odd rows");
		assertEquals (0, reads [2]);
		assertArrayEquals (reference (filter, plain, n), rows (filter));

		/* a mixed chain: (column 0 AND column 1) OR column 2 */
		filter.setText (0, "g7", TextMatch.EXACT);
		assertArrayEquals (reference (filter, plain, n), rows (filter));
		filter.setLogic (1, ColumnFilter.AND);
		assertArrayEquals (reference (filter, plain, n), rows (filter));
		filter.setSuspended (2, true);
		assertArrayEquals (reference (filter, plain, n), rows (filter));
		filter.setSuspended (2, false);
		assertArrayEquals (reference (filter, plain, n), rows (filter));
	}

	/*
	 * Rows that were never tested stay correct through source edits: inserted and updated rows are tested, the
	 * untested rows move with their neighbours, and a later criterion change tests them with their current text.
	 */
	@Test
	public void test_untestedRowsFollowSourceEdits () {
		Random random = new Random (17);
		for (int round = 0; round < 40; round++) {
			List<String []> cells = new ArrayList<> ();
			for (int row = 0; row < 150 + random.nextInt (80); row++) cells.add (randomCells (random));
			CellText text = (row, column) -> cells.get (row) [column];
			ColumnFilter filter = new ColumnFilter (3, cells.size (), text);
			for (int step = 0; step < 60; step++) {
				switch (random.nextInt (9)) {
					case 0: filter.setText (random.nextInt (3), "" + (char) ('a' + random.nextInt (3)), TextMatch.CONTAINS); break;
					case 1: filter.setText (random.nextInt (3), "" + (char) ('a' + random.nextInt (3)), TextMatch.STARTS_WITH | (random.nextBoolean () ? TextMatch.NEGATE : 0)); break;
					case 2: filter.setLogic (random.nextInt (3), random.nextInt (2)); break;
					case 3: filter.clear (random.nextInt (3)); break;
					case 4: filter.setSuspended (random.nextInt (3), random.nextBoolean ()); break;
					case 5: {
						int start = random.nextInt (cells.size () + 1), count = 1 + random.nextInt (70);
						for (int i = 0; i < count; i++) cells.add (start, randomCells (random));
						filter.rowsInserted (start, count);
						break;
					}
					case 6: {
						if (cells.isEmpty ()) break;
						int start = random.nextInt (cells.size ()), count = 1 + random.nextInt (Math.min (70, cells.size () - start));
						for (int i = 0; i < count; i++) cells.remove (start);
						filter.rowsRemoved (start, count);
						break;
					}
					case 7: {
						if (cells.isEmpty ()) break;
						int row = random.nextInt (cells.size ());
						cells.set (row, randomCells (random));
						filter.rowsChanged (row);
						break;
					}
					default: {
						filter.setText (new int [] {0, 2}, "" + (char) ('a' + random.nextInt (3)), TextMatch.CONTAINS);
						filter.setLogic (ColumnFilter.ALL_COLUMNS, random.nextInt (2));
						break;
					}
				}
				int n = cells.size ();
				int [] expected = java.util.stream.IntStream.range (0, n).filter (row -> suspendable (filter, text, row)).toArray ();
				assertArrayEquals (expected, rows (filter), "round " + round + " step " + step);
			}
		}
	}

	static String [] randomCells (Random random) {
		String [] cells = new String [3];
		for (int c = 0; c < 3; c++) cells [c] = "" + (char) ('a' + random.nextInt (4)) + (char) ('a' + random.nextInt (4));
		return cells;
	}

	/* the plain fold, leaving suspended criteria out */
	static boolean suspendable (ColumnFilter filter, CellText text, int row) {
		Boolean result = null;
		for (int column = -1; column < filter.getColumnCount (); column++) {
			if (!filter.isActive (column) || filter.isSuspended (column)) continue;
			boolean value = test (filter, text, column, row);
			result = result == null ? value : filter.getLogic (column) == ColumnFilter.AND ? result && value : result || value;
		}
		return result == null || result;
	}

	@Test
	public void test_incrementalEditsEqualAReference () {
		Random random = new Random (31);
		for (int n : new int [] {0, 3, 63, 64, 65, 200, 1500}) {
			Test_org_eclipse_swt_internal_ColumnSort.Rows rows = new Test_org_eclipse_swt_internal_ColumnSort.Rows (n, n + 3, 7);
			CellText text = text (rows);
			ColumnFilter filter = new ColumnFilter (4, n, text);
			ColumnSort sort = new ColumnSort (4, filter);
			rows.keys (sort);
			RowWindow window = new RowWindow (sort);
			window.setWindow (2, 10);
			int [] c = {3, 0}, d = {SWT.UP, SWT.DOWN};
			sort.setSortKeys (c, d);
			for (int step = 0; step < 80; step++) {
				if (random.nextInt (4) == 0) criterion (filter, random); else Test_org_eclipse_swt_internal_ColumnSort.edit (rows, window, random);
				int [] expected = reference (rows, filter, text, c, d);
				assertArrayEquals (expected, rows (sort), "n " + n + " step " + step);
				Test_org_eclipse_swt_internal_ColumnSort.assertInverse (filter);
				Test_org_eclipse_swt_internal_ColumnSort.assertInverse (sort);
				assertEquals (Math.max (0, Math.min (10, expected.length - 2)), window.size ());
				for (int i = 0; i < window.size (); i++) assertEquals (expected [i + 2], window.map (i));
			}
		}
	}

	static void criterion (ColumnFilter filter, Random random) {
		int column = random.nextInt (5) - 1;
		switch (random.nextInt (5)) {
			case 0: filter.clear (column); break;
			case 1: {
				String pattern = random.nextBoolean () ? "t1*,t2*" : "*1*";
				if (column < 0) filter.setText (new int [] {0, 3}, pattern, TextMatch.WILDCARD); else filter.setText (column, pattern, TextMatch.WILDCARD);
				break;
			}
			case 2: if (column >= 0) filter.setText (column, "-", TextMatch.CONTAINS | TextMatch.NEGATE); break;
			case 3: filter.setLogic (column, random.nextInt (2)); break;
			default: if (column >= 0) filter.setValues (column, "0", "1", "t3", "NaN"); break;
		}
	}

	/* the reference: test each row against a plain predicate fold, then List.sort by the key chain */
	static int [] reference (Test_org_eclipse_swt_internal_ColumnSort.Rows rows, ColumnFilter filter, CellText text, int [] c, int [] d) {
		List<Integer> kept = new ArrayList<> ();
		for (int row = 0; row < rows.size (); row++) {
			if (passes (filter, text, row)) kept.add (row);
		}
		Comparator<Integer> chain = (a, b) -> 0;
		for (int k = 0; k < c.length; k++) {
			Comparator<Integer> comparator = rows.comparator (c [k]);
			chain = chain.thenComparing (d [k] == SWT.DOWN ? comparator.reversed () : comparator);
		}
		kept.sort (chain);
		return kept.stream ().mapToInt (Integer::intValue).toArray ();
	}

	static boolean passes (ColumnFilter filter, CellText text, int row) {
		Boolean result = null;
		for (int column = -1; column < filter.getColumnCount (); column++) {
			if (!filter.isActive (column)) continue;
			boolean value = test (filter, text, column, row);
			result = result == null ? value : filter.getLogic (column) == ColumnFilter.AND ? result && value : result || value;
		}
		return result == null || result;
	}

	static boolean test (ColumnFilter filter, CellText text, int column, int row) {
		TextMatch match = filter.getMatch (column);
		if (match == null) {
			String value = text.text (row, column);
			return Set.of ("0", "1", "t3", "NaN").contains (value == null ? "" : value);
		}
		int [] columns = column < 0 ? filter.getColumns () : new int [] {column};
		IntPredicate positive = c -> TextMatch.compile (match.pattern (), match.mode () & ~TextMatch.NEGATE).matches (text.text (row, c));
		boolean any = Arrays.stream (columns).anyMatch (positive);
		return any != ((match.mode () & TextMatch.NEGATE) != 0);
	}

	/* the package-private count of input changes a view applied by a full pass (brief rule 24: edits are deltas) */
	static int fullPasses (RowView view) {
		try {
			java.lang.reflect.Field field = Class.forName ("org.eclipse.swt.internal.RowViewBase").getDeclaredField ("fullPasses");
			field.setAccessible (true);
			return field.getInt (view);
		} catch (ReflectiveOperationException e) {
			return fail (e);
		}
	}

	/* a package-private field of an M3 object, declared in its class or a superclass */
	static Object field (Object owner, String name) {
		for (Class<?> type = owner.getClass (); type != null; type = type.getSuperclass ()) {
			try {
				java.lang.reflect.Field field = type.getDeclaredField (name);
				field.setAccessible (true);
				return field.get (owner);
			} catch (NoSuchFieldException e) {
				/* declared in a superclass */
			} catch (IllegalAccessException e) {
				return fail (e);
			}
		}
		return fail ("no field " + name);
	}

	/* applies a change of a filter to a list of its rows (source order), as a listener that keeps a copy would */
	static void replay (List<Integer> mirror, RowChange change, RowView view) {
		int start = change.start (), count = change.count ();
		switch (change.kind ()) {
			case RowChange.RESET:
				mirror.clear ();
				for (int i = 0; i < view.size (); i++) mirror.add (view.map (i));
				return;
			case RowChange.INSERTED:
				mirror.replaceAll (row -> row >= start ? row + count : row);
				break;
			case RowChange.REMOVED:
				mirror.removeIf (row -> row >= start && row < start + count);
				mirror.replaceAll (row -> row >= start + count ? row - count : row);
				return;
			case RowChange.LEFT:
				for (int i = 0; i < change.rowCount (); i++) mirror.remove ((Integer) change.row (i));
				return;
			default:
				break;
		}
		if (change.kind () == RowChange.UPDATED) return;
		for (int i = 0; i < change.rowCount (); i++) {
			int row = change.row (i), at = Collections.binarySearch (mirror, row);
			assertTrue (at < 0, "row " + row + " entered twice");
			mirror.add (-at - 1, row);
		}
	}

	@Test
	public void test_sourceEditsAreDeltasThatReplay () {
		Random random = new Random (24);
		for (int seed = 0; seed < 30; seed++) {
			Test_org_eclipse_swt_internal_ColumnSort.Rows rows = new Test_org_eclipse_swt_internal_ColumnSort.Rows (random.nextInt (400), seed, 2 + random.nextInt (9));
			CellText text = text (rows);
			ColumnFilter filter = new ColumnFilter (4, rows.size (), text);
			criterion (filter, random);
			List<Integer> mirror = new ArrayList<> ();
			for (int row : rows (filter)) mirror.add (row);
			int [] resets = {0};
			filter.addListener (change -> {
				if (change.kind () == RowChange.RESET) resets [0]++;
				replay (mirror, change, filter);
			});
			int passes = fullPasses (filter);
			for (int step = 0; step < 60; step++) {
				if (random.nextInt (4) == 0) criterion (filter, random); else Test_org_eclipse_swt_internal_ColumnSort.edit (rows, filter, random);
				List<Integer> expected = new ArrayList<> ();
				for (int row = 0; row < rows.size (); row++) {
					if (passes (filter, text, row)) expected.add (row);
				}
				String where = "seed " + seed + " step " + step;
				assertEquals (expected, mirror, where);
				assertArrayEquals (expected.stream ().mapToInt (Integer::intValue).toArray (), rows (filter), where);
				for (int row = 0; row < rows.size (); row++) assertEquals (expected.indexOf (row), filter.unmap (row), where + " row " + row);
			}
			assertEquals (passes + resets [0], fullPasses (filter), "only a new source (RESET) is a full pass");
		}
	}

	@Test
	public void test_treeValueUpdatesAreDeltasThatReplay () {
		Random random = new Random (25);
		for (int seed = 0; seed < 30; seed++) {
			int n = 1 + random.nextInt (300);
			int [] parents = new int [n];
			String [] labels = new String [n];
			for (int row = 0; row < n; row++) {
				parents [row] = row == 0 || random.nextInt (4) == 0 ? -1 : random.nextInt (row);
				labels [row] = word (random);
			}
			ColumnFilter filter = new ColumnFilter (1, n, (row, column) -> labels [row]);
			filter.setParents (row -> parents [row]);
			String pattern = random.nextBoolean () ? "ab" : "ba";
			filter.setText (0, pattern, TextMatch.CONTAINS);
			List<Integer> mirror = new ArrayList<> ();
			for (int row : rows (filter)) mirror.add (row);
			filter.addListener (change -> replay (mirror, change, filter));
			int passes = fullPasses (filter);
			for (int step = 0; step < 50; step++) {
				int [] changed = new int [1 + random.nextInt (random.nextInt (5) == 0 ? n : 3)];
				for (int k = 0; k < changed.length; k++) {
					changed [k] = random.nextInt (n);
					labels [changed [k]] = word (random);
					/* a value update may move a row to another parent (kept acyclic: a parent precedes its child) */
					if (random.nextInt (3) == 0) parents [changed [k]] = changed [k] == 0 || random.nextInt (4) == 0 ? -1 : random.nextInt (changed [k]);
				}
				filter.rowsChanged (changed);
				boolean [] keep = new boolean [n];
				for (int row = 0; row < n; row++) {
					if (!labels [row].contains (pattern)) continue;
					for (int r = row; r != -1 && !keep [r]; r = parents [r]) keep [r] = true;
				}
				List<Integer> expected = new ArrayList<> ();
				for (int row = 0; row < n; row++) {
					if (keep [row]) expected.add (row);
				}
				String where = "seed " + seed + " step " + step;
				assertEquals (expected, mirror, where);
				assertArrayEquals (expected.stream ().mapToInt (Integer::intValue).toArray (), rows (filter), where);
				for (int row = 0; row < n; row++) assertEquals (expected.indexOf (row), filter.unmap (row), where + " row " + row);
			}
			assertEquals (passes, fullPasses (filter), "tree value updates are deltas");
		}
	}

	private static String word (Random random) {
		StringBuilder word = new StringBuilder ();
		for (int k = 1 + random.nextInt (4); k > 0; k--) word.append (random.nextBoolean () ? 'a' : 'b');
		return word.toString ();
	}

	@Test
	public void test_typingRefinementsEqualAFreshEvaluation () {
		Random random = new Random (8);
		int n = 3000;
		String [] words = new String [n];
		String alphabet = "abAB\u00e9";
		for (int i = 0; i < n; i++) {
			StringBuilder word = new StringBuilder ();
			for (int k = 1 + random.nextInt (6); k > 0; k--) word.append (alphabet.charAt (random.nextInt (alphabet.length ())));
			words [i] = word.toString ();
		}
		CellText cells = (row, column) -> column == 0 ? words [row] : words [n - 1 - row];
		ColumnFilter filter = new ColumnFilter (2, n, cells);
		int [] modes = {TextMatch.CONTAINS, TextMatch.CONTAINS | TextMatch.IGNORE_CASE, TextMatch.STARTS_WITH, TextMatch.STARTS_WITH | TextMatch.IGNORE_CASE, TextMatch.CONTAINS | TextMatch.NEGATE};
		for (int session = 0; session < 40; session++) {
			int mode = modes [random.nextInt (modes.length)], column = random.nextInt (2);
			StringBuilder typed = new StringBuilder ();
			for (int key = 0; key < 8; key++) {
				if (typed.length () > 0 && random.nextInt (4) == 0) typed.setLength (typed.length () - 1);
				else typed.append (random.nextInt (9) == 0 ? ',' : alphabet.charAt (random.nextInt (alphabet.length ())));
				if (random.nextInt (10) == 0) {
					int row = random.nextInt (n);
					words [row] = words [random.nextInt (n)];
					filter.rowsChanged (row, n - 1 - row);
				}
				if (random.nextInt (12) == 0) {
					filter.clear (column);
					column = 1 - column;
				}
				filter.setText (column, typed.toString (), mode);
				ColumnFilter fresh = new ColumnFilter (2, n, cells);
				fresh.setText (column, typed.toString (), mode);
				assertArrayEquals (rows (fresh), rows (filter), "session " + session + " typed '" + typed + "' mode " + mode);
			}
			filter.clear (column);
		}
	}

	@Test
	public void test_windowRangesAndChangeDescriptions () {
		ColumnSort sort = new ColumnSort (1, 50);
		sort.setKey (0, ColumnKey.ofInts (i -> i % 5));
		RowWindow window = new RowWindow (sort);
		List<String> kinds = new ArrayList<> ();
		List<ItemRange> damaged = new ArrayList<> ();
		window.addListener (change -> {
			kinds.add (change.toString ());
			damaged.add (change.damaged ());
		});
		window.setWindow (ItemRange.of (10, 19));
		assertEquals (ItemRange.of (10, 19), window.getRange ());
		window.setWindow (ItemRange.EMPTY);
		assertEquals (ItemRange.EMPTY, window.getRange ());
		window.setWindow (45, 10);
		assertEquals (ItemRange.of (45, 49), window.getRange ());
		sort.rowsInserted (0, 1);
		sort.rowsRemoved (0, 1);
		sort.toggle (0, false);
		sort.rowsChanged (3);
		sort.setSourceSize (50);
		assertThrows (IllegalArgumentException.class, () -> window.setWindow (null));
		String all = String.join (" ", kinds);
		for (String kind : new String [] {"RESET", "INSERTED", "REMOVED", "ORDER", "UPDATED"}) assertTrue (all.contains (kind), kind);
		assertEquals (ItemRange.of (0, 9), damaged.get (0));
		assertEquals (ItemRange.EMPTY, damaged.get (1));
		ColumnFilter filter = fruits ();
		RowChange [] last = new RowChange [1];
		filter.addListener (change -> last [0] = change);
		filter.setText (0, "e", TextMatch.CONTAINS);
		assertTrue (last [0].toString ().contains ("LEFT"));
		filter.setText (0, null, 0);
		assertTrue (last [0].toString ().contains ("ENTERED"));
	}

	@Test
	public void test_valueChangesWithoutStatusChangeSkipTheFullPass () {
		List<String []> cells = new ArrayList<> ();
		for (String [] row : CELLS) cells.add (row.clone ());
		ColumnFilter filter = new ColumnFilter (3, cells.size (), (row, column) -> cells.get (row) [column]);
		filter.setText (1, "r", TextMatch.CONTAINS);
		ColumnSort sort = new ColumnSort (3, filter);
		sort.setCellText ((row, column) -> cells.get (row) [column]);
		sort.toggle (0, false);
		assertArrayEquals (new int [] {0, 2, 3, 5}, rows (sort));
		cells.get (0) [0] = "zucchini";
		sort.rowsChanged (0);
		assertArrayEquals (new int [] {2, 3, 5, 0}, rows (sort));
		cells.get (1) [0] = "aaa";
		filter.rowsChanged (1);
		assertArrayEquals (new int [] {2, 3, 5, 0}, rows (sort));
		cells.get (1) [1] = "orange";
		filter.rowsChanged (1);
		assertArrayEquals (new int [] {1, 2, 3, 5, 0}, rows (sort));
		ColumnFilter quiet = new ColumnFilter (3, cells.size (), (row, column) -> cells.get (row) [column]);
		quiet.setText (0, "a", TextMatch.CONTAINS);
		quiet.rowsChanged (0, 1);
		assertEquals (2, quiet.size ());
	}

	@Test
	public void test_windowClipsDamage () {
		ColumnSort sort = new ColumnSort (1, 100);
		sort.setKey (0, ColumnKey.ofInts (i -> -i));
		RowWindow window = new RowWindow (sort);
		List<String> log = new ArrayList<> ();
		window.addListener (change -> log.add (change.kind () + ":" + change.firstDamaged () + ".." + change.lastDamaged ()));
		window.setWindow (10, 20);
		assertEquals (10, window.getFirst ());
		assertEquals (20, window.getCount ());
		assertEquals (20, window.size ());
		assertEquals (10, window.map (0));
		assertEquals (5, window.unmap (15));
		assertEquals (-1, window.unmap (5));
		assertEquals (-1, window.unmap (30));
		sort.rowsChanged (50);
		sort.rowsChanged (15);
		sort.rowsRemoved (0, 95);
		assertEquals (0, window.size ());
		sort.setSourceSize (12);
		assertEquals (2, window.size ());
		assertEquals (List.of ("1:0..19", "6:-1..-1", "6:5..5", "3:-1..-1", "1:0..1"), log);
		assertThrows (IllegalArgumentException.class, () -> window.setWindow (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> window.setWindow (1, -1));
		assertThrows (IllegalArgumentException.class, () -> window.map (2));
		assertThrows (IllegalArgumentException.class, () -> new RowWindow (null));
		assertTrue (window.toString ().contains ("2 rows"));
		window.dispose ();
		sort.setSourceSize (3);
		assertEquals (5, log.size ());
		assertEquals (RowChange.ORDER, 7);
		RowChange [] seen = new RowChange [1];
		sort.addListener (change -> seen [0] = change);
		sort.toggle (0, true);
		assertTrue (seen [0].toString ().contains ("ORDER"));
		assertThrows (IllegalArgumentException.class, () -> seen [0].row (0));
		assertEquals (0, seen [0].start ());
		assertEquals (0, seen [0].count ());
	}

	@Test
	public void test_invalidUpdatedParentFailsAndResetRecovers () {
		int [] parents = {-1, 0, -1};
		String [] labels = {"root", "match", "other"};
		ColumnFilter filter = new ColumnFilter (1, labels.length, (row, column) -> labels [row]);
		filter.setParents (row -> parents [row]);
		filter.setText (0, "match", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 1}, rows (filter));
		for (int invalid : new int [] {-2, labels.length}) {
			parents [1] = invalid;
			assertThrows (IllegalArgumentException.class, () -> filter.rowsChanged (1));
			parents [1] = 0;
			filter.setSourceSize (labels.length);
			assertArrayEquals (new int [] {0, 1}, rows (filter));
			assertFalse (filter.matches (0));
			assertTrue (filter.matches (1));
			assertEquals (-1, filter.unmap (2));
		}
	}

	@Test
	public void test_ancestorCycleFallbackKeepsOnlyReachableRows () {
		int [] parents = {1, 0, -1};
		String [] labels = {"other", "match", "other"};
		ColumnFilter filter = new ColumnFilter (1, labels.length, (row, column) -> labels [row]);
		filter.setParents (row -> parents [row]);
		filter.setText (0, "match", TextMatch.EXACT);
		assertArrayEquals (new int [] {0, 1}, rows (filter));
		labels [1] = "other";
		filter.rowsChanged (1);
		assertArrayEquals (new int [0], rows (filter));
		labels [0] = "match";
		filter.rowsChanged (0);
		assertArrayEquals (new int [] {0, 1}, rows (filter));
		assertTrue (filter.matches (0));
		assertFalse (filter.matches (1));
		assertEquals (-1, filter.unmap (2));
		Test_org_eclipse_swt_internal_ColumnSort.assertInverse (filter);
	}

	@Test
	public void test_synchronousGenerationAndCriterionCopies () {
		ColumnFilter filter = fruits ();
		int generation = filter.getGeneration ();
		assertFalse (filter.isAsync ());
		filter.setAsync (null, 0);
		assertEquals (generation, filter.getGeneration ());
		assertEquals (generation, filter.getAppliedGeneration ());
		assertThrows (IllegalArgumentException.class, () -> filter.setAsync (null, -1));
		assertEquals (generation, filter.getGeneration ());
		int [] columns = {0, 1};
		filter.setText (columns, "red", TextMatch.EXACT);
		columns [0] = 2;
		int [] copy = filter.getColumns ();
		copy [1] = 2;
		assertArrayEquals (new int [] {0, 1}, filter.getColumns ());
		assertArrayEquals (new int [] {0, 2}, rows (filter));
		assertEquals (generation + 1, filter.getGeneration ());
		assertEquals (filter.getGeneration (), filter.getAppliedGeneration ());
		filter.rowsChanged (0, 0);
		assertEquals (generation + 2, filter.getGeneration ());
		assertEquals (filter.getGeneration (), filter.getAppliedGeneration ());
		filter.clearAll ();
		assertEquals (CELLS.length, filter.size ());
		assertEquals (generation + 3, filter.getAppliedGeneration ());
	}
}
