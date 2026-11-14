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
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of <code>SWT.EmptinessChanged</code> on a <code>Table</code> and a <code>List</code>. The
 * oracle is <code>getItemCount</code> before and after every public operation: an event is expected
 * exactly when the count crossed zero. A <code>Tree</code> keeps the stock rules. The class passes
 * unchanged in every startup mode; the mode differential is this class run with
 * <code>-Dswt.widget.mode=viewport</code> and with <code>-Dswt.widget.mode=original</code>.
 */
public class Test_EmptinessChanged {
	Display display;
	Shell shell;
	/* events received: {type, detail, count} */
	final java.util.List<int []> events = new ArrayList<> ();
	final Listener recorder = event -> events.add (new int [] {event.type, event.detail, event.count});

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	/* asserts the events since the last call: each pair is {detail, count} */
	private void expect (int... detailCount) {
		assertEquals (detailCount.length / 2, events.size (), () -> "events " + describe ());
		for (int i = 0; i < events.size (); i++) {
			assertEquals (SWT.EmptinessChanged, events.get (i) [0]);
			assertEquals (detailCount [2 * i], events.get (i) [1], () -> "detail " + describe ());
			assertEquals (detailCount [2 * i + 1], events.get (i) [2], () -> "count " + describe ());
		}
		events.clear ();
	}

	private String describe () {
		StringBuilder text = new StringBuilder ();
		for (int [] event : events) text.append (Arrays.toString (event));
		return text.toString ();
	}

	/* event.detail when the last item went away, and when the first item arrived */
	private static final int EMPTY = 1;
	private static final int FILLED = 0;

	/* ------------------------------------------------------------------ Table */

	@Test
	public void test_tableReportsEveryZeroCrossingOnce () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.addListener (SWT.EmptinessChanged, recorder);
		TableItem first = new TableItem (table, SWT.NONE);
		expect (FILLED, 1);
		TableItem second = new TableItem (table, SWT.NONE, 0);
		table.clearAll ();
		second.dispose ();
		expect ();
		first.dispose ();
		expect (EMPTY, 0);
		for (int i = 0; i < 3; i++) new TableItem (table, SWT.NONE);
		expect (FILLED, 1);
		table.remove (0);
		table.remove (0, 1);
		expect (EMPTY, 0);
		table.setItemCount (5);
		expect (FILLED, 5);
		table.setItemCount (5);
		table.setItemCount (2);
		expect ();
		table.remove (new int [] {1, 0, 1});
		expect (EMPTY, 0);
		table.removeAll ();
		table.remove (new int [0]);
		table.setItemCount (0);
		expect ();
		table.setItemCount (3);
		table.removeAll ();
		expect (FILLED, 3, EMPTY, 0);
	}

	@Test
	public void test_virtualTableReportsCountChanges () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("row " + event.index));
		table.addListener (SWT.EmptinessChanged, recorder);
		table.setItemCount (100000);
		expect (FILLED, 100000);
		table.getItem (99999).dispose ();
		table.remove (0, 49999);
		expect ();
		table.setItemCount (0);
		expect (EMPTY, 0);
	}

	/* ------------------------------------------------------------------ List */

	@Test
	public void test_listReportsEveryZeroCrossingOnce () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI | SWT.BORDER);
		list.addListener (SWT.EmptinessChanged, recorder);
		list.add ("a");
		expect (FILLED, 1);
		/* remove and add inside: the transient empty list is not reported */
		list.setItem (0, "b");
		assertEquals ("b", list.getItem (0));
		expect ();
		list.add ("c", 0);
		list.remove ("b");
		expect ();
		/* remove (String) calls remove (int): one event */
		list.remove ("c");
		expect (EMPTY, 0);
		list.setItems ("x", "y");
		expect (FILLED, 2);
		list.setItems ();
		expect (EMPTY, 0);
		/* the native setItemCount adds and removes through the public methods: one event each way */
		list.setItemCount (3);
		expect (FILLED, 3);
		list.setItemCount (0);
		expect (EMPTY, 0);
		list.setItems ("a", "b", "c", "d");
		list.remove (new int [] {3, 1});
		list.remove (0);
		expect (FILLED, 4);
		list.remove (0, 0);
		expect (EMPTY, 0);
		list.removeAll ();
		list.remove (new int [0]);
		expect ();
		list.add ("z");
		list.removeAll ();
		expect (FILLED, 1, EMPTY, 0);
	}

	@Test
	public void test_listFailedOperationsAreReportedByTheNextChange () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.SINGLE);
		list.addListener (SWT.EmptinessChanged, recorder);
		assertThrows (IllegalArgumentException.class, () -> list.setItem (0, "x"));
		assertThrows (IllegalArgumentException.class, () -> list.add (null));
		assertThrows (IllegalArgumentException.class, () -> list.remove (5));
		expect ();
		/* the hold of the failed setItem was released: the next change is reported */
		list.add ("a");
		expect (FILLED, 1);
	}

	/* ------------------------------------------------------------------ Tree */

	@Test
	public void test_treeKeepsTheStockRules () {
		Tree tree = new Tree (shell, SWT.BORDER);
		tree.addListener (SWT.EmptinessChanged, recorder);
		TreeItem item = new TreeItem (tree, SWT.NONE);
		item.dispose ();
		new TreeItem (tree, SWT.NONE);
		int beforeRemoveAll = events.size ();
		tree.removeAll ();
		/* stock: first item created (0), last item disposed (1); its removeAll sends nothing */
		assertTrue (events.size () >= 2, () -> "events " + describe ());
		for (int [] event : events) assertEquals (SWT.EmptinessChanged, event [0]);
		assertEquals (FILLED, events.get (0) [1]);
		assertEquals (EMPTY, events.get (1) [1]);
		assertEquals (beforeRemoveAll, events.size ());
		events.clear ();
	}

	/* ------------------------------------------------------------------ registration */

	@Test
	public void test_listenersStartFromTheCurrentStateAndCanBeRemoved () {
		Table table = new Table (shell, SWT.NONE);
		new TableItem (table, SWT.NONE);
		table.addListener (SWT.EmptinessChanged, recorder);
		new TableItem (table, SWT.NONE);
		expect ();
		java.util.List<Integer> second = new ArrayList<> ();
		Listener counter = event -> second.add (event.detail);
		table.addListener (SWT.EmptinessChanged, counter);
		table.removeAll ();
		expect (EMPTY, 0);
		assertEquals (java.util.List.of (EMPTY), second);
		table.removeListener (SWT.EmptinessChanged, recorder);
		new TableItem (table, SWT.NONE);
		expect ();
		assertEquals (java.util.List.of (EMPTY, FILLED), second);
		table.removeListener (SWT.EmptinessChanged, counter);
		table.removeAll ();
		assertEquals (2, second.size ());
		/* a new registration reads the state again */
		table.addListener (SWT.EmptinessChanged, recorder);
		new TableItem (table, SWT.NONE);
		expect (FILLED, 1);
	}

	@Test
	public void test_listenerMayChangeTheWidget () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.NONE);
		/* a placeholder that refills the list when it becomes empty */
		list.addListener (SWT.EmptinessChanged, event -> {
			if (event.detail == EMPTY) list.add ("placeholder");
		});
		list.addListener (SWT.EmptinessChanged, recorder);
		list.add ("a");
		list.remove (0);
		assertEquals (1, list.getItemCount ());
		expect (FILLED, 1, EMPTY, 0, FILLED, 1);
	}

	@Test
	public void test_listenerMayChangeATableInsideTheEvent () {
		Table table = new Table (shell, SWT.NONE);
		/* the refill happens inside the dispatch of BECAME_EMPTY: the net state is announced after it */
		table.addListener (SWT.EmptinessChanged, event -> {
			if (event.detail == EMPTY) new TableItem (table, SWT.NONE).setText ("placeholder");
		});
		table.addListener (SWT.EmptinessChanged, recorder);
		new TableItem (table, SWT.NONE).dispose ();
		assertEquals (1, table.getItemCount ());
		expect (FILLED, 1, EMPTY, 0, FILLED, 1);
	}

	@Test
	public void test_removedListListenerHearsNothing () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.NONE);
		list.addListener (SWT.EmptinessChanged, recorder);
		list.removeListener (SWT.EmptinessChanged, recorder);
		list.add ("a");
		list.removeAll ();
		expect ();
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = new Table (shell, SWT.NONE);
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.NONE);
		assertThrows (IllegalArgumentException.class, () -> table.addListener (SWT.EmptinessChanged, null));
		assertThrows (IllegalArgumentException.class, () -> list.addListener (SWT.EmptinessChanged, null));
		assertThrows (IllegalArgumentException.class, () -> table.removeListener (SWT.EmptinessChanged, null));
		assertThrows (IllegalArgumentException.class, () -> list.removeListener (SWT.EmptinessChanged, null));
		list.removeListener (SWT.EmptinessChanged, recorder);
		list.dispose ();
		assertThrows (SWTException.class, () -> list.addListener (SWT.EmptinessChanged, recorder));
		assertThrows (SWTException.class, () -> list.removeListener (SWT.EmptinessChanged, recorder));
		/* the refused registrations left nothing behind */
		new TableItem (table, SWT.NONE);
		expect ();
	}

	/* ------------------------------------------------------------------ oracle */

	@Test
	public void test_randomOperationsAgreeWithTheCountOracle () {
		Random random = new Random (31);
		Table table = new Table (shell, SWT.MULTI);
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI);
		Tree tree = new Tree (shell, SWT.MULTI);
		table.addListener (SWT.EmptinessChanged, recorder);
		list.addListener (SWT.EmptinessChanged, recorder);
		/* the tree keeps the stock rules and is not listened; its steps keep the seeded sequence */
		java.util.List<Consumer<Random>> tableOps = java.util.List.of (
			r -> new TableItem (table, SWT.NONE),
			r -> { if (table.getItemCount () > 0) table.getItem (r.nextInt (table.getItemCount ())).dispose (); },
			r -> { if (table.getItemCount () > 0) table.remove (r.nextInt (table.getItemCount ())); },
			r -> table.setItemCount (r.nextInt (4)),
			r -> table.removeAll (),
			r -> { int n = table.getItemCount (); if (n > 0) table.remove (0, r.nextInt (n)); });
		java.util.List<Consumer<Random>> listOps = java.util.List.of (
			r -> list.add ("x"),
			r -> { if (list.getItemCount () > 0) list.setItem (0, "y"); },
			r -> { if (list.getItemCount () > 0) list.remove (r.nextInt (list.getItemCount ())); },
			r -> list.setItemCount (r.nextInt (4)),
			r -> list.setItems (r.nextBoolean () ? new String [0] : new String [] {"a"}),
			r -> list.removeAll ());
		java.util.List<Consumer<Random>> treeOps = java.util.List.of (
			r -> new TreeItem (tree, SWT.NONE),
			r -> { if (tree.getItemCount () > 0) new TreeItem (tree.getItem (0), SWT.NONE); },
			r -> { if (tree.getItemCount () > 0) tree.getItem (r.nextInt (tree.getItemCount ())).dispose (); },
			r -> tree.setItemCount (r.nextInt (4)),
			r -> tree.removeAll ());
		for (int step = 0; step < 600; step++) {
			int kind = random.nextInt (3);
			Control widget = kind == 0 ? table : kind == 1 ? list : tree;
			java.util.List<Consumer<Random>> ops = kind == 0 ? tableOps : kind == 1 ? listOps : treeOps;
			int before = count (widget);
			ops.get (random.nextInt (ops.size ())).accept (random);
			int after = count (widget);
			if (kind == 2 || (before == 0) == (after == 0)) {
				expect ();
			} else {
				expect (after == 0 ? EMPTY : FILLED, after);
			}
		}
	}

	private static int count (Control widget) {
		if (widget instanceof Table table) return table.getItemCount ();
		if (widget instanceof Tree tree) return tree.getItemCount ();
		return ((org.eclipse.swt.widgets.List) widget).getItemCount ();
	}
}
