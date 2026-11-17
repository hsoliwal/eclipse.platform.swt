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

import java.text.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.junit.jupiter.api.*;

/** Headless tests of {@link ColumnGroups} over a filter and a sort: no display, no widget. */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ColumnGroups {

	static final String [] KINDS = {"b", "a", "B", "", null, "c", "ä"};

	/* rows of {kind, number}, edited in place */
	static final class Rows {
		final java.util.List<String []> cells = new ArrayList<> ();

		void add (int at, Random random) {
			cells.add (at, new String [] {KINDS [random.nextInt (KINDS.length)], String.format ("%03d", random.nextInt (1000))});
		}

		String text (int row, int column) {
			return cells.get (row) [column];
		}
	}

	/* the plain grouping: the input's rows, stably partitioned by kind, the kinds in the collator's order */
	static java.util.List<java.util.List<Integer>> expected (RowView input, Rows rows, int column) {
		Collator collator = Collator.getInstance ();
		TreeMap<String, java.util.List<Integer>> groups = new TreeMap<> ((a, b) -> {
			int order = collator.compare (a, b);
			return order != 0 ? order : a.compareTo (b);
		});
		for (int i = 0; i < input.size (); i++) {
			String value = rows.text (input.map (i), column);
			groups.computeIfAbsent (value == null ? "" : value, key -> new ArrayList<> ()).add (input.map (i));
		}
		return new ArrayList<> (groups.values ());
	}

	static void check (ColumnGroups groups, RowView input, Rows rows) {
		java.util.List<java.util.List<Integer>> expected = expected (input, rows, groups.getColumn ());
		assertEquals (expected.size (), groups.getGroupCount ());
		assertEquals (input.size (), groups.size ());
		int row = 0;
		for (int g = 0; g < expected.size (); g++) {
			java.util.List<Integer> members = expected.get (g);
			assertEquals (row, groups.getGroupStart (g));
			assertEquals (members.size (), groups.getGroupSize (g));
			String value = rows.text (members.get (0), groups.getColumn ());
			assertEquals (value == null ? "" : value, groups.getGroupValue (g));
			for (int member : members) {
				assertEquals (member, groups.map (row));
				assertEquals (row, groups.unmap (member));
				assertEquals (g, groups.groupOf (row));
				row++;
			}
		}
		for (int source = 0; source < rows.cells.size (); source++) {
			assertEquals (input.unmap (source) < 0, groups.unmap (source) < 0, "shown in the groups exactly when the input shows it");
		}
	}

	@Test
	void theGroupsAreTheInputsRowsPartitionedByValueInTheInputsOrderThroughEveryChange () {
		Random random = new Random (5);
		int deltas = 0;
		for (int round = 0; round < 30; round++) {
			Rows rows = new Rows ();
			for (int i = random.nextInt (120); i > 0; i--) rows.add (rows.cells.size (), random);
			ColumnFilter filter = new ColumnFilter (2, rows.cells.size (), rows::text);
			ColumnSort sort = new ColumnSort (2, filter);
			sort.setCellText (rows::text);
			ColumnGroups groups = new ColumnGroups (sort, rows::text, 0);
			/* the last change: kind, start, count, first and last damaged row */
			int [] last = new int [5];
			groups.addListener (change -> {
				/* several changes in one step (a filter change: rows leave, then rows enter): the union of their damage */
				boolean again = last [0] != -1;
				last [0] = again && last [0] != change.kind () ? Math.max (RowChange.LEFT, last [0]) : change.kind ();
				last [1] = change.start ();
				last [2] = change.count ();
				if (change.firstDamaged () >= 0) {
					last [3] = last [3] < 0 ? change.firstDamaged () : Math.min (last [3], change.firstDamaged ());
					last [4] = Math.max (last [4], change.lastDamaged ());
				}
				if (again && (change.kind () == RowChange.RESET || change.kind () == RowChange.ORDER)) last [0] = RowChange.RESET;
			});
			check (groups, sort, rows);
			for (int step = 0; step < 40; step++) {
				int [] before = new int [groups.size ()];
				for (int i = 0; i < before.length; i++) before [i] = groups.map (i);
				Arrays.fill (last, -1);
				int edit = random.nextInt (7);
				switch (edit) {
					case 0 -> filter.setText (1, "" + random.nextInt (10), TextMatch.CONTAINS);
					case 1 -> filter.clear (1);
					case 2 -> sort.setSortKeys (new int [] {1}, new int [] {random.nextBoolean () ? SWT.UP : SWT.DOWN});
					case 3 -> {
						int at = random.nextInt (rows.cells.size () + 1), count = 1 + random.nextInt (10);
						for (int i = 0; i < count; i++) rows.add (at, random);
						groups.rowsInserted (at, count);
					}
					case 4 -> {
						if (rows.cells.isEmpty ()) continue;
						int at = random.nextInt (rows.cells.size ());
						rows.cells.remove (at);
						groups.rowsRemoved (at, 1);
					}
					case 5 -> {
						if (rows.cells.isEmpty ()) continue;
						int [] changed = new int [1 + random.nextInt (3)];
						for (int i = 0; i < changed.length; i++) {
							changed [i] = random.nextInt (rows.cells.size ());
							String [] cells = rows.cells.get (changed [i]);
							if (random.nextBoolean ()) cells [0] = KINDS [random.nextInt (KINDS.length)];
							else cells [1] = String.format ("%03d", random.nextInt (1000));
						}
						groups.rowsChanged (changed);
					}
					default -> groups.setColumn (random.nextInt (3) - 1 == 1 ? 1 : 0);
				}
				if (groups.getColumn () >= 0) check (groups, sort, rows);
				if (edit >= 3 && edit <= 5 && last [0] != -1) {
					assertNotEquals (RowChange.RESET, last [0], "a small edit is a delta");
					deltas++;
				}
				if (last [0] == -1 || last [0] == RowChange.RESET || last [0] == RowChange.ORDER) continue;
				for (int i = 0; i < Math.min (before.length, groups.size ()); i++) {
					if (last [3] >= 0 && i >= last [3] && i <= last [4]) continue;
					int expected = before [i];
					if (last [0] == RowChange.INSERTED && expected >= last [1]) expected += last [2];
					if (last [0] == RowChange.REMOVED && expected >= last [1] + last [2]) expected -= last [2];
					assertEquals (expected, groups.map (i), "row " + i + " outside the damage " + last [3] + ".." + last [4] + " of kind " + last [0]);
				}
			}
		}
		assertTrue (deltas > 300, deltas + " deltas");
	}

	@Test
	void insideAGroupTheRowsFollowTheSortBelow () {
		String [][] cells = {{"x", "3"}, {"y", "1"}, {"x", "1"}, {"y", "2"}, {"x", "2"}};
		CellText text = (row, column) -> cells [row][column];
		ColumnSort sort = new ColumnSort (2, cells.length);
		sort.setCellText (text);
		sort.setSortKeys (new int [] {1}, new int [] {SWT.DOWN});
		ColumnGroups groups = new ColumnGroups (sort, text, 0);
		int [] order = new int [groups.size ()];
		for (int i = 0; i < order.length; i++) order [i] = groups.map (i);
		assertArrayEquals (new int [] {0, 4, 2, 3, 1}, order, "x rows by number descending, then the y rows");
		assertEquals ("x", groups.getGroupValue (0));
		assertEquals (3, groups.getGroupSize (0));
		assertEquals (3, groups.getGroupStart (1));
		assertEquals (1, groups.groupOf (3));
		assertEquals (0, groups.groupOf (2));
	}

	@Test
	void rowsUpdatedTogetherArePlacedAmongRowsInTheInputsOrder () {
		/* source rows X, a, b, S, c, d of one group, sorted by number; X and S move in one update */
		String [][] cells = {{"x", "05"}, {"x", "10"}, {"x", "20"}, {"x", "30"}, {"x", "40"}, {"x", "50"}};
		CellText text = (row, column) -> cells [row][column];
		ColumnSort sort = new ColumnSort (2, cells.length);
		sort.setCellText (text);
		sort.setSortKeys (new int [] {1}, new int [] {SWT.UP});
		ColumnGroups groups = new ColumnGroups (sort, text, 0);
		int [] damage = new int [2];
		groups.addListener (change -> {
			assertEquals (RowChange.UPDATED, change.kind ());
			damage [0] = change.firstDamaged ();
			damage [1] = change.lastDamaged ();
		});
		cells [0][1] = "45";
		cells [3][1] = "99";
		groups.rowsChanged (0, 3);
		int [] order = new int [groups.size ()];
		for (int i = 0; i < order.length; i++) order [i] = groups.map (i);
		assertArrayEquals (new int [] {1, 2, 4, 0, 5, 3}, order, "a, b, c, X, d, S");
		assertArrayEquals (new int [] {0, 5}, damage);
	}

	@Test
	void noColumnShowsTheInputAsItIsAndAColumnChangeIsReported () {
		String [][] cells = {{"b", "1"}, {"a", "2"}, {"b", "3"}};
		CellText text = (row, column) -> cells [row][column];
		ColumnFilter filter = new ColumnFilter (2, cells.length, text);
		ColumnGroups groups = new ColumnGroups (filter, text, -1);
		assertEquals (0, groups.getGroupCount ());
		assertEquals (3, groups.size ());
		assertEquals (1, groups.map (1), "the input's order");
		assertEquals (-1, groups.groupOf (0));
		int [] resets = new int [1];
		groups.addListener (change -> resets [0]++);
		groups.setColumn (-1);
		assertEquals (0, resets [0], "the same column: nothing happens");
		groups.setColumn (0);
		assertEquals (1, resets [0]);
		assertEquals (2, groups.getGroupCount ());
		assertEquals (1, groups.map (0), "the a row first");
		groups.dispose ();
		filter.setText (0, "a", TextMatch.EXACT);
		assertEquals (1, resets [0], "a disposed view no longer follows its input");
	}

	@Test
	void argumentsAreChecked () {
		ColumnFilter filter = new ColumnFilter (1, 2, (r, c) -> "v");
		assertThrows (IllegalArgumentException.class, () -> new ColumnGroups (null, (r, c) -> "", 0));
		assertThrows (IllegalArgumentException.class, () -> new ColumnGroups (filter, null, 0));
		assertThrows (IllegalArgumentException.class, () -> new ColumnGroups (filter, (r, c) -> "", -2));
		ColumnGroups groups = new ColumnGroups (filter, (r, c) -> "v", 0);
		assertThrows (IllegalArgumentException.class, () -> groups.setColumn (-2));
		assertThrows (IllegalArgumentException.class, () -> groups.getGroupValue (1));
		assertThrows (IllegalArgumentException.class, () -> groups.getGroupStart (-1));
		assertThrows (IllegalArgumentException.class, () -> groups.getGroupSize (1));
		assertThrows (IllegalArgumentException.class, () -> groups.groupOf (2));
		assertThrows (IllegalArgumentException.class, () -> groups.map (2));
		assertThrows (IllegalArgumentException.class, () -> groups.unmap (2));
	}
}
