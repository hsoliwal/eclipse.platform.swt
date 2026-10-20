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

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The asynchronous column filter over 10^6 rows: typing stays responsive (the UI thread only
 * records the change), only the newest generation is applied, and the final view equals a
 * synchronous reference. Run on the hidden test desktop.
 */
@SuppressWarnings ("nls")
public class Test_M3FilterAsync {

	static final int ROWS = 1_000_000;
	Display display;
	Shell shell;
	int [] numbers;
	String [] names;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (500, 400);
		Random random = new Random (42);
		numbers = new int [ROWS];
		names = new String [ROWS];
		for (int i = 0; i < ROWS; i++) {
			numbers [i] = random.nextInt (1000);
			names [i] = "n" + random.nextInt (1_000_000);
		}
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
		while (display.readAndDispatch ()) {
			/* drain */
		}
	}

	String cell (int row, int column) {
		return column == 0 ? String.valueOf (numbers [row]) : names [row];
	}

	/* the synchronous reference: rows whose name contains the pattern, by number, then by row */
	int [] reference (String pattern) {
		java.util.List<Integer> rows = new ArrayList<> ();
		for (int i = 0; i < ROWS; i++) {
			if (names [i].contains (pattern)) rows.add (i);
		}
		rows.sort (Comparator.<Integer> comparingInt (i -> numbers [i]).thenComparingInt (i -> i));
		return rows.stream ().mapToInt (Integer::intValue).toArray ();
	}

	/* dispatches events for a while; returns the longest single dispatch in nanoseconds */
	long pump (long millis) {
		long end = System.nanoTime () + millis * 1_000_000L, longest = 0;
		while (System.nanoTime () < end) {
			long start = System.nanoTime ();
			boolean busy = display.readAndDispatch ();
			longest = Math.max (longest, System.nanoTime () - start);
			if (!busy) {
				try {
					Thread.sleep (1);
				} catch (InterruptedException e) {
					fail (e);
				}
			}
		}
		return longest;
	}

	long waitFor (BooleanSupplier condition) {
		long end = System.currentTimeMillis () + 30_000, longest = 0;
		while (!condition.getAsBoolean () && System.currentTimeMillis () < end) longest = Math.max (longest, pump (5));
		assertTrue (condition.getAsBoolean ());
		return longest;
	}

	static void time (String what, long nanos) {
		String file = System.getProperty ("m3.sortfilter.timings");
		if (file == null) return;
		try {
			Files.writeString (Paths.get (file), what + "\t" + ROWS + "\t" + nanos / 1000 + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			fail (e);
		}
	}

	/* a package-private field of an M3 object (the gates of brief rule 24 read its counters) */
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

	static int counter (Object owner, String name) {
		return (Integer) field (owner, name);
	}

	/*
	 * Brief rule 24, hidden viewports: while the table is hidden, 10^4 model deltas and 10 filter edits update the lanes
	 * (the edits on the worker) but nothing is posted to or applied on the UI thread and the widget is not touched;
	 * showing it applies the newest result once and refreshes the widget once, and the rows equal the plain reference.
	 */
	@Test
	public void test_hiddenViewportAppliesOnceOnShow () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI);
		for (int c = 0; c < 2; c++) new TableColumn (table, SWT.NONE).setWidth (150);
		M3ColumnFilter filter = new M3ColumnFilter (2, ROWS, this::cell);
		M3ColumnSort sort = new M3ColumnSort (2, filter);
		sort.setKey (0, M3ColumnKey.ofInts (r -> numbers [r]));
		sort.setSortKeys (new int [] {0}, new int [] {SWT.UP});
		M3RowBinding binding = M3RowBinding.bind (table, sort, (item, row) -> {
			item.setText (0, cell (row, 0));
			item.setText (1, cell (row, 1));
		});
		filter.setAsync (display, 50);
		shell.open ();
		filter.setText (1, "n1", M3TextMatch.CONTAINS);
		waitFor (() -> filter.getAppliedGeneration () == filter.getGeneration ());
		Object worker = field (filter, "worker");
		int posted = counter (worker, "posted"), applies = counter (filter, "applies"), refreshes = counter (binding, "refreshes");

		table.setVisible (false);
		Random random = new Random (24);
		String [] patterns = {"n12", "n123", "n12", "n1", "n2", "n23", "n234", "n23", "n2", "n29"};
		for (int k = 0; k < 10_000; k++) {
			int row = random.nextInt (ROWS);
			numbers [row] = random.nextInt (1000);
			names [row] = "n" + random.nextInt (1_000_000);
			sort.rowsChanged (row);
			if (k % 1000 == 999) {
				filter.setText (1, patterns [k / 1000], M3TextMatch.CONTAINS);
				pump (60);
			}
		}
		waitFor (() -> (Boolean) field (worker, "parked") && counter (worker, "resultGeneration") == filter.getGeneration ());
		pump (200);
		assertEquals (posted, counter (worker, "posted"), "results posted to the UI thread while hidden");
		assertEquals (applies, counter (filter, "applies"), "results applied while hidden");
		assertEquals (refreshes, counter (binding, "refreshes"), "widget refreshes while hidden");
		assertNotEquals (filter.getGeneration (), filter.getAppliedGeneration ());

		table.setVisible (true);
		assertEquals (applies + 1, counter (filter, "applies"), "applies on show");
		assertEquals (refreshes + 1, counter (binding, "refreshes"), "widget refreshes on show");
		assertEquals (filter.getGeneration (), filter.getAppliedGeneration ());
		pump (200);
		assertEquals (applies + 1, counter (filter, "applies"), "applies after show");
		assertEquals (refreshes + 1, counter (binding, "refreshes"), "widget refreshes after show");
		int [] expected = reference ("n29");
		assertArrayEquals (expected, Test_M3ColumnSort.order (sort));
		assertEquals (expected.length, table.getItemCount ());
		for (int i = 0; i < Math.min (60, expected.length); i++) assertEquals (cell (expected [i], 1), table.getItem (i).getText (1), "row " + i);
	}

	@Test
	public void test_typingOverAMillionRowsStaysResponsive () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI);
		for (int c = 0; c < 2; c++) new TableColumn (table, SWT.NONE).setWidth (150);
		M3ColumnFilter filter = new M3ColumnFilter (2, ROWS, this::cell);
		M3ColumnSort sort = new M3ColumnSort (2, filter);
		sort.setKey (0, M3ColumnKey.ofInts (r -> numbers [r]));
		sort.setCellText (this::cell);
		sort.setSortKeys (new int [] {0}, new int [] {SWT.UP});
		long [] firstFill = {0};
		boolean [] armed = {false};
		M3RowBinding binding = M3RowBinding.bind (table, sort, (item, row) -> {
			if (armed [0] && firstFill [0] == 0 && filter.getAppliedGeneration () == filter.getGeneration ()) firstFill [0] = System.nanoTime ();
			item.setText (0, cell (row, 0));
			item.setText (1, cell (row, 1));
		});
		filter.setAsync (display, 100);
		assertTrue (filter.isAsync ());
		java.util.List<int []> applied = new ArrayList<> ();
		filter.addListener (change -> applied.add (new int [] {filter.getAppliedGeneration (), filter.getGeneration ()}));
		shell.open ();
		pump (200);
		/* warm up the recording path (class loading, first compilation), then measure */
		filter.setText (1, "warm", M3TextMatch.CONTAINS);
		filter.setText (1, null, M3TextMatch.CONTAINS);
		waitFor (() -> filter.getAppliedGeneration () == filter.getGeneration ());
		applied.clear ();
		String last = "n123";
		int keep = reference (last) [0];
		binding.setSelectedRows (keep);
		String [] typed = {"n", "n1", "n12", "n123", "n1234", "n123", "n12", "n1", "n", "n9", "n98", "n9", "n", "n1", "n12", "n123", "n1239", "n123", "n12", "n123"};
		assertEquals (20, typed.length);
		long [] keystrokes = new long [typed.length];
		armed [0] = true;
		long slowestDispatch = 0, lastKeystroke = 0;
		for (int k = 0; k < typed.length; k++) {
			long start = System.nanoTime ();
			filter.setText (1, typed [k], M3TextMatch.CONTAINS);
			lastKeystroke = System.nanoTime ();
			keystrokes [k] = lastKeystroke - start;
			slowestDispatch = Math.max (slowestDispatch, pump (30));
		}
		assertTrue (applied.isEmpty (), "nothing applies while typing faster than the debounce");
		int finalGeneration = filter.getGeneration ();
		long applyDispatch = waitFor (() -> filter.getAppliedGeneration () == finalGeneration);
		waitFor (() -> firstFill [0] != 0);
		Arrays.sort (keystrokes);
		long median = keystrokes [keystrokes.length / 2], p95 = keystrokes [(int) Math.ceil (keystrokes.length * 0.95) - 1];
		time ("async-keystroke-median-ui", median);
		time ("async-keystroke-p95-ui", p95);
		time ("async-keystroke-max-ui", keystrokes [keystrokes.length - 1]);
		time ("async-dispatch-while-typing-max", slowestDispatch);
		time ("async-apply-dispatch-max", applyDispatch);
		time ("async-last-keystroke-to-first-page", firstFill [0] - lastKeystroke);
		assertTrue (p95 < 2_000_000L, "keystroke p95 " + p95 + " ns");
		assertEquals (1, applied.size ());
		assertArrayEquals (new int [] {finalGeneration, finalGeneration}, applied.get (0));
		assertArrayEquals (reference (last), Test_M3ColumnSort.order (sort));
		assertEquals (sort.size (), table.getItemCount ());
		assertArrayEquals (new int [] {keep}, binding.getSelectedRows ());
		assertArrayEquals (new int [] {sort.unmap (keep)}, table.getSelectionIndices ());
		assertEquals (cell (sort.map (0), 1), table.getItem (0).getText (1));
	}

	@Test
	public void test_supersededGenerationsNeverApply () {
		Logger logger = Logger.getLogger ("org.eclipse.swt.m3.M3ColumnFilter");
		Level level = logger.getLevel ();
		logger.setLevel (Level.FINE);
		try {
			M3ColumnFilter filter = new M3ColumnFilter (2, ROWS, this::cell);
			M3ColumnSort sort = new M3ColumnSort (2, filter);
			sort.setKey (0, M3ColumnKey.ofInts (r -> numbers [r]));
			sort.setSortKeys (new int [] {0}, new int [] {SWT.UP});
			filter.setAsync (display, 0);
			java.util.List<int []> applied = new ArrayList<> ();
			filter.addListener (change -> applied.add (new int [] {filter.getAppliedGeneration (), filter.getGeneration ()}));
			for (String pattern : new String [] {"1", "12", "123", "1234", "123", "12", "77"}) {
				filter.setText (1, pattern, M3TextMatch.CONTAINS);
				pump (3);
			}
			int finalGeneration = filter.getGeneration ();
			waitFor (() -> filter.getAppliedGeneration () == finalGeneration);
			for (int [] pair : applied) assertEquals (pair [1], pair [0]);
			assertEquals (finalGeneration, applied.get (applied.size () - 1) [0]);
			assertArrayEquals (reference ("77"), Test_M3ColumnSort.order (sort));
		} finally {
			logger.setLevel (level);
		}
	}

	@Test
	public void test_asyncEditsLogicParentsAndBackToSynchronous () {
		int n = 5000;
		String [] words = new String [n];
		for (int i = 0; i < n; i++) words [i] = "w" + (i % 97);
		int [] parents = new int [n];
		for (int i = 0; i < n; i++) parents [i] = i < 10 ? -1 : i % 10;
		M3ColumnFilter filter = new M3ColumnFilter (2, n, (row, column) -> column == 0 ? words [row] : String.valueOf (row));
		M3ColumnSort sort = new M3ColumnSort (2, filter);
		sort.setCellText ((row, column) -> words [row]);
		assertThrows (IllegalArgumentException.class, () -> filter.setAsync (display, -1));
		filter.setAsync (display, 5);
		filter.setAsync (display, 0);
		filter.setText (0, "w1*", M3TextMatch.WILDCARD);
		assertTrue (filter.isActive (0));
		assertEquals (n, filter.size (), "not applied yet");
		filter.setValues (1, "1", "2");
		filter.setLogic (1, M3ColumnFilter.OR);
		assertEquals (M3ColumnFilter.OR, filter.getLogic (1));
		words [3] = "w1x";
		filter.rowsChanged (3);
		waitFor (() -> filter.getAppliedGeneration () == filter.getGeneration ());
		int expected = 0;
		for (int i = 0; i < n; i++) {
			if (words [i].startsWith ("w1") || i == 1 || i == 2) expected++;
		}
		assertEquals (expected, filter.size ());
		sort.toggle (0, false);
		filter.setParents (r -> parents [r]);
		filter.setValues (1, r -> r, 7);
		sort.toggle (0, false);
		waitFor (() -> filter.getAppliedGeneration () == filter.getGeneration ());
		assertTrue (filter.size () >= expected);
		filter.clearAll ();
		filter.clear (0);
		filter.setText (new int [] {0, 1}, "w2*", M3TextMatch.WILDCARD);
		filter.setAsync (null, 0);
		assertFalse (filter.isAsync ());
		assertEquals (filter.getGeneration (), filter.getAppliedGeneration ());
		int synchronous = filter.size ();
		M3ColumnFilter reference = new M3ColumnFilter (2, n, (row, column) -> column == 0 ? words [row] : String.valueOf (row));
		reference.setParents (r -> parents [r]);
		reference.setText (new int [] {0, 1}, "w2*", M3TextMatch.WILDCARD);
		assertEquals (reference.size (), synchronous);
		filter.setAsync (null, 0);
	}

	@Test
	public void test_slowModelIsSupersededMidComputation () {
		int n = 50_000;
		M3ColumnFilter filter = new M3ColumnFilter (1, n, (row, column) -> {
			if (row % 8192 == 0) {
				try {
					Thread.sleep (40);
				} catch (InterruptedException e) {
					Thread.currentThread ().interrupt ();
				}
			}
			return "x" + row;
		});
		filter.setAsync (display, 0);
		java.util.List<Integer> applied = new ArrayList<> ();
		filter.addListener (change -> applied.add (filter.getAppliedGeneration ()));
		filter.setText (0, "x1*", M3TextMatch.WILDCARD);
		pump (60);
		filter.setText (0, "x2*", M3TextMatch.WILDCARD);
		pump (60);
		filter.setText (0, "x3*", M3TextMatch.WILDCARD);
		int finalGeneration = filter.getGeneration ();
		waitFor (() -> filter.getAppliedGeneration () == finalGeneration);
		assertEquals (java.util.List.of (finalGeneration), applied);
		int expected = 0;
		for (int i = 0; i < n; i++) {
			if (("x" + i).startsWith ("x3")) expected++;
		}
		assertEquals (expected, filter.size ());
		filter.setAsync (null, 0);
	}

	@Test
	public void test_workerFailureSurfacesOnTheUserInterfaceThread () {
		int n = 100;
		boolean [] broken = {false};
		M3ColumnFilter filter = new M3ColumnFilter (1, n, (row, column) -> {
			if (broken [0] && row == 50) throw new IllegalStateException ("cell 50");
			return "x" + row;
		});
		filter.setAsync (display, 0);
		broken [0] = true;
		filter.setText (0, "x5*", M3TextMatch.WILDCARD);
		SWTException e = assertThrows (SWTException.class, () -> pump (2000));
		assertInstanceOf (IllegalStateException.class, e.getCause ());
		assertEquals ("cell 50", e.getCause ().getMessage ());
		broken [0] = false;
		filter.setText (0, "x5*", M3TextMatch.WILDCARD);
		waitFor (() -> filter.getAppliedGeneration () == filter.getGeneration ());
		assertEquals (11, filter.size ());
		filter.setAsync (null, 0);
	}
}
