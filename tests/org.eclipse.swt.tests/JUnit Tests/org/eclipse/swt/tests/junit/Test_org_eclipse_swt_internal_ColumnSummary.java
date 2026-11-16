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

/** Headless tests of {@link ColumnSummary} over a {@link ColumnFilter}: no display, no widget. */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ColumnSummary {

	static final int [] FUNCTIONS = {ColumnSummary.COUNT, ColumnSummary.SUM, ColumnSummary.MINIMUM, ColumnSummary.MAXIMUM, ColumnSummary.AVERAGE};

	/* rows of {name, amount}; an amount of NaN is a row without a value */
	static final class Rows {
		final List<String> names = new ArrayList<> ();
		final List<Double> amounts = new ArrayList<> ();
		int reads;

		void add (int at, Random random) {
			names.add (at, "" + (char) ('a' + random.nextInt (4)) + (char) ('a' + random.nextInt (4)));
			amounts.add (at, random.nextInt (6) == 0 ? Double.NaN : (double) (random.nextInt (2001) - 1000) / 8);
		}

		double amount (int row) {
			reads++;
			return amounts.get (row);
		}
	}

	/* the plain computation over the rows the view shows, in view order */
	static double expected (RowView view, Rows rows, int function) {
		int n = 0;
		double sum = 0, low = Double.NaN, high = Double.NaN;
		for (int i = 0; i < view.size (); i++) {
			double v = rows.amounts.get (view.map (i));
			if (Double.isNaN (v)) continue;
			sum += v;
			low = n == 0 ? v : Math.min (low, v);
			high = n == 0 ? v : Math.max (high, v);
			n++;
		}
		return switch (function) {
			case ColumnSummary.COUNT -> n;
			case ColumnSummary.SUM -> sum;
			case ColumnSummary.MINIMUM -> low;
			case ColumnSummary.MAXIMUM -> high;
			default -> n == 0 ? Double.NaN : sum / n;
		};
	}

	@Test
	public void test_everyFunctionOverAFilteredViewEqualsThePlainComputation () {
		Random random = new Random (3);
		for (int round = 0; round < 40; round++) {
			Rows rows = new Rows ();
			for (int i = random.nextInt (200); i > 0; i--) rows.add (rows.names.size (), random);
			ColumnFilter filter = new ColumnFilter (2, rows.names.size (), (row, column) -> rows.names.get (row));
			ColumnSummary summary = new ColumnSummary (2, filter);
			summary.setValues (1, rows::amount);
			for (int step = 0; step < 50; step++) {
				switch (random.nextInt (6)) {
					case 0 -> filter.setText (0, "" + (char) ('a' + random.nextInt (4)), TextMatch.CONTAINS);
					case 1 -> filter.clear (0);
					case 2 -> {
						int at = random.nextInt (rows.names.size () + 1), count = 1 + random.nextInt (20);
						for (int i = 0; i < count; i++) rows.add (at, random);
						filter.rowsInserted (at, count);
					}
					case 3 -> {
						if (rows.names.isEmpty ()) break;
						int at = random.nextInt (rows.names.size ()), count = 1 + random.nextInt (Math.min (20, rows.names.size () - at));
						for (int i = 0; i < count; i++) {
							rows.names.remove (at);
							rows.amounts.remove (at);
						}
						filter.rowsRemoved (at, count);
					}
					case 4 -> {
						if (rows.names.isEmpty ()) break;
						int row = random.nextInt (rows.names.size ());
						rows.names.remove (row);
						rows.amounts.remove (row);
						rows.add (row, random);
						filter.rowsChanged (row);
					}
					default -> filter.setText (0, "" + (char) ('a' + random.nextInt (4)), TextMatch.STARTS_WITH | TextMatch.NEGATE);
				}
				int function = FUNCTIONS [random.nextInt (FUNCTIONS.length)];
				summary.setFunction (1, function);
				String where = "round " + round + " step " + step + " function " + function;
				assertEquals (expected (filter, rows, function), summary.getValue (1), 0.0, where);
				assertEquals ((int) expected (filter, rows, ColumnSummary.COUNT), summary.getCount (1), where);
				summary.setFunction (0, ColumnSummary.COUNT);
				assertEquals (filter.size (), summary.getValue (0), 0.0, "a column without values counts the rows shown");
			}
			summary.dispose ();
		}
	}

	@Test
	public void test_valuesAreReadOncePerChangeBurstAndOnlyWhenAsked () {
		Rows rows = new Rows ();
		Random random = new Random (9);
		for (int i = 0; i < 500; i++) rows.add (i, random);
		ColumnFilter filter = new ColumnFilter (2, 500, (row, column) -> rows.names.get (row));
		ColumnSummary summary = new ColumnSummary (2, filter);
		summary.setValues (1, rows::amount);
		summary.setFunction (1, ColumnSummary.SUM);
		int [] events = new int [1];
		summary.addListener (column -> events [0]++);

		/* ten changes without a question: nothing is read */
		rows.reads = 0;
		for (int i = 0; i < 10; i++) filter.setText (0, "" + (char) ('a' + i % 4), TextMatch.CONTAINS);
		assertEquals (0, rows.reads, "no value is read for a footer nobody looks at");
		assertTrue (events [0] >= 1, "the listener hears that the rows changed");

		/* one question: one pass over the rows shown; the other functions of the column need no further pass */
		double sum = summary.getValue (1);
		assertEquals (filter.size (), rows.reads);
		assertEquals (expected (filter, rows, ColumnSummary.SUM), sum, 0.0);
		for (int function : FUNCTIONS) {
			summary.setFunction (1, function);
			assertEquals (expected (filter, rows, function), summary.getValue (1), 0.0);
		}
		summary.getCount (1);
		assertEquals (filter.size (), rows.reads, "the function changes without reading a value");

		/* a new order of the same rows changes no summary */
		ColumnSort sort = new ColumnSort (2, filter);
		sort.setKey (1, ColumnKey.ofDoubles (row -> rows.amounts.get (row)));
		ColumnSummary sorted = new ColumnSummary (2, sort);
		sorted.setValues (1, rows::amount);
		sorted.setFunction (1, ColumnSummary.MAXIMUM);
		double before = sorted.getValue (1);
		rows.reads = 0;
		sort.setSortKeys (new int [] {1}, new int [] {org.eclipse.swt.SWT.DOWN});
		assertEquals (before, sorted.getValue (1), 0.0);
		assertEquals (0, rows.reads, "an order change reads no value");
	}

	@Test
	public void test_emptyViewsAndColumnsWithoutValues () {
		Rows rows = new Rows ();
		ColumnFilter filter = new ColumnFilter (1, 0, (row, column) -> rows.names.get (row));
		ColumnSummary summary = new ColumnSummary (3, filter);
		assertEquals (3, summary.getColumnCount ());
		assertTrue (Double.isNaN (summary.getValue (0)), "no function");
		summary.setValues (1, rows::amount);
		summary.setFunction (1, ColumnSummary.SUM);
		assertEquals (0.0, summary.getValue (1), 0.0, "the sum of no rows");
		for (int function : new int [] {ColumnSummary.MINIMUM, ColumnSummary.MAXIMUM, ColumnSummary.AVERAGE}) {
			summary.setFunction (1, function);
			assertTrue (Double.isNaN (summary.getValue (1)), "function " + function + " of no rows");
		}
		summary.setFunction (2, ColumnSummary.SUM);
		assertTrue (Double.isNaN (summary.getValue (2)), "a sum needs values");
		summary.setFunction (2, ColumnSummary.COUNT);
		assertEquals (0.0, summary.getValue (2), 0.0);

		/* rows that all lack a value */
		rows.names.add ("aa");
		rows.amounts.add (Double.NaN);
		filter.rowsInserted (0, 1);
		summary.setFunction (1, ColumnSummary.COUNT);
		assertEquals (0.0, summary.getValue (1), 0.0);
		assertEquals (1.0, summary.getValue (2), 0.0);
		summary.setFunction (1, ColumnSummary.AVERAGE);
		assertTrue (Double.isNaN (summary.getValue (1)));

		/* values removed again */
		summary.setValues (1, null);
		summary.setFunction (1, ColumnSummary.COUNT);
		assertEquals (1.0, summary.getValue (1), 0.0);
		assertEquals (1, summary.getCount (1));
	}

	@Test
	public void test_listenersAndArguments () {
		ColumnFilter filter = new ColumnFilter (1, 3, (row, column) -> "x");
		ColumnSummary summary = new ColumnSummary (2, filter);
		List<Integer> heard = new ArrayList<> ();
		ColumnSummary.Listener listener = heard::add;
		summary.addListener (listener);
		summary.setFunction (1, ColumnSummary.COUNT);
		summary.setFunction (1, ColumnSummary.COUNT);
		summary.setValues (0, row -> row);
		filter.rowsInserted (3, 2);
		assertEquals (List.of (1, 0, -1), heard);
		assertEquals (5.0, summary.getValue (1), 0.0);
		summary.removeListener (listener);
		filter.rowsRemoved (0, 1);
		assertEquals (3, heard.size ());
		assertEquals (4.0, summary.getValue (1), 0.0, "the summary still follows the view");
		summary.dispose ();
		filter.rowsRemoved (0, 1);
		assertThrows (IllegalArgumentException.class, () -> new ColumnSummary (-1, filter));
		assertThrows (IllegalArgumentException.class, () -> new ColumnSummary (1, null));
		assertThrows (IllegalArgumentException.class, () -> summary.setFunction (2, ColumnSummary.SUM));
		assertThrows (IllegalArgumentException.class, () -> summary.setFunction (0, 6));
		assertThrows (IllegalArgumentException.class, () -> summary.setFunction (0, -1));
		assertThrows (IllegalArgumentException.class, () -> summary.getValue (-1));
		assertThrows (IllegalArgumentException.class, () -> summary.setValues (2, null));
		assertThrows (IllegalArgumentException.class, () -> summary.addListener (null));
	}
}
