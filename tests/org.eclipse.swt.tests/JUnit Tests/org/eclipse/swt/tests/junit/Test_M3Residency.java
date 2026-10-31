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

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3Residency (enhanced M3 API, leaf 7). The oracle for the window is the visible
 * range (<code>M3ItemSelection.getVisibleRange</code>): once a widget has painted, its resident
 * rows equal it, in every mode (the mode-neutral contract of docs/m3/ENHANCED_API.md): the resident
 * rows are the rows in view, computed from public state, and none while the widget is not visible.
 * Only the item-object census (FACADES, HELD_FACADES) measures the mode: with
 * <code>-Dswt.widget.mode=viewport</code> it is also checked to be bounded.
 */
public class Test_M3Residency {
	/* native rows in the original mode cost seconds per 10^5: the stock widgets get a tenth */
	static final int ROWS = "original".equals (System.getProperty ("swt.widget.mode")) ? 10_000 : 100_000;
	Display display;
	Shell shell;

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

	private void flush () {
		while (display.readAndDispatch ()) {
			// paint and deliver the pending notifications
		}
	}

	private static boolean viewport (Widget widget) {
		return M3Mode.of (widget) == M3Mode.VIEWPORT;
	}

	private Table virtualTable () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("row " + event.index));
		table.setItemCount (ROWS);
		flush ();
		table.update ();
		flush ();
		return table;
	}

	/* the Table viewport serves tables without SWT.VIRTUAL; their rows live in lanes until a facade is asked for */
	static final int TABLE_ROWS = 20_000;

	private Table plainTable () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (TABLE_ROWS);
		flush ();
		table.update ();
		flush ();
		return table;
	}

	/* ------------------------------------------------------------------ Table */

	@Test
	public void test_tableWindowCoversTheVisibleRowsAndIsBounded () {
		Table table = plainTable ();
		for (int top : new int [] {0, 5000, TABLE_ROWS / 2, TABLE_ROWS - 1}) {
			table.setTopIndex (top);
			table.redraw ();
			table.update ();
			flush ();
			M3ItemRange resident = M3Residency.residentRange (table);
			assertEquals (M3ItemSelection.getVisibleRange (table), resident, "top " + top);
			assertTrue (resident.size () < 1000, "bounded window " + resident);
			assertEquals (resident.size (), M3Residency.stats (table) [M3Residency.RESIDENT_ROWS]);
		}
	}

	@Test
	public void test_virtualTableResidentRowsAreTheRowsInView () {
		Table table = virtualTable ();
		assertEquals (M3ItemSelection.getVisibleRange (table), M3Residency.residentRange (table));
		table.setTopIndex (ROWS / 2);
		table.update ();
		flush ();
		assertEquals (M3ItemSelection.getVisibleRange (table), M3Residency.residentRange (table));
		assertTrue (M3Residency.residentRange (table).contains (ROWS / 2));
	}

	@Test
	public void test_aWidgetThatIsNotVisibleHoldsNoRows () {
		Shell hidden = new Shell (display);
		hidden.setBounds (20, 20, 320, 420);
		Table table = new Table (hidden, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (500);
		List list = new List (hidden, SWT.MULTI | SWT.V_SCROLL);
		list.setItems ("a", "b");
		Tree tree = new Tree (hidden, SWT.NONE);
		new TreeItem (tree, SWT.NONE);
		assertTrue (M3Residency.residentRange (table).isEmpty ());
		assertTrue (M3Residency.residentRange (list).isEmpty ());
		assertTrue (M3Residency.residentRange (tree).isEmpty ());
		assertEquals (0, M3Residency.stats (table) [M3Residency.RESIDENT_ROWS]);
		hidden.dispose ();
	}

	@Test
	public void test_plainTableRowsHaveNoItemUntilAskedWithTheViewport () {
		Table table = plainTable ();
		int far = TABLE_ROWS - 3;
		boolean before = M3Residency.isMaterialized (table, far);
		if (viewport (table)) assertFalse (before, "a lane row has no item until asked");
		assertEquals (before, M3Residency.isMaterialized (table, far), "asking changed the answer");
		TableItem item = table.getItem (far);
		assertTrue (M3Residency.isMaterialized (table, far));
		item.setText ("asked");
		assertTrue (M3Residency.stats (table) [M3Residency.FACADES] >= 1);
	}

	@Test
	public void test_isMaterializedNeverMakesAnItem () {
		Table table = virtualTable ();
		int far = ROWS - 7;
		long facadesBefore = M3Residency.stats (table) [M3Residency.FACADES];
		assertFalse (M3Residency.isMaterialized (table, far));
		assertFalse (M3Residency.isMaterialized (table, far));
		assertEquals (facadesBefore, M3Residency.stats (table) [M3Residency.FACADES]);
		TableItem item = table.getItem (far);
		assertTrue (M3Residency.isMaterialized (table, far));
		assertEquals ("row " + far, item.getText ());
		assertTrue (M3Residency.stats (table) [M3Residency.FACADES] > facadesBefore);
	}

	@Test
	public void test_plainTableHasAnItemForEveryRow () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		TableItem [] items = new TableItem [300];
		for (int i = 0; i < items.length; i++) (items [i] = new TableItem (table, SWT.NONE)).setText ("row " + i);
		flush ();
		table.update ();
		flush ();
		/* the application holds every item, so each one is materialized */
		for (int i = 0; i < items.length; i += 37) assertTrue (M3Residency.isMaterialized (table, i));
		long [] stats = M3Residency.stats (table);
		assertEquals (items.length, stats [M3Residency.FACADES]);
		assertTrue (stats [M3Residency.HELD_FACADES] <= stats [M3Residency.FACADES]);
		assertEquals (M3ItemSelection.getVisibleRange (table), M3Residency.residentRange (table), "plain");
	}

	@Test
	public void test_heldFacadesStayBoundedWhileScrolling () {
		Table table = plainTable ();
		for (int top = 0; top < TABLE_ROWS; top += TABLE_ROWS / 40) {
			table.setTopIndex (top);
			table.update ();
			flush ();
		}
		long [] stats = M3Residency.stats (table);
		assertTrue (stats [M3Residency.FACADES] >= stats [M3Residency.HELD_FACADES]);
		assertTrue (stats [M3Residency.HELD_FACADES] >= 0);
		if (viewport (table)) assertTrue (stats [M3Residency.HELD_FACADES] < 2000, "held " + stats [M3Residency.HELD_FACADES]);
	}

	/* ------------------------------------------------------------------ List */

	@Test
	public void test_listWindowCoversTheVisibleRowsAndIsBounded () {
		List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		String [] strings = new String [ROWS];
		for (int i = 0; i < ROWS; i++) strings [i] = "row " + i;
		list.setItems (strings);
		for (int top : new int [] {0, ROWS / 3, ROWS - 1}) {
			list.setTopIndex (top);
			list.update ();
			flush ();
			M3ItemRange resident = M3Residency.residentRange (list);
			assertEquals (M3ItemSelection.getVisibleRange (list), resident, "top " + top);
			long [] stats = M3Residency.stats (list);
			assertArrayEquals (new long [] {resident.size (), 0, 0}, stats);
		}
		list.removeAll ();
		flush ();
		assertTrue (M3Residency.residentRange (list).isEmpty ());
	}

	/* ------------------------------------------------------------------ Tree */

	@Test
	public void test_treeWindowCoversTheVisibleRowsAndIsBounded () {
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("node " + event.index));
		tree.setItemCount (ROWS);
		flush ();
		for (int index : new int [] {0, ROWS / 2, ROWS - 1}) {
			tree.setTopItem (tree.getItem (index));
			tree.update ();
			flush ();
			M3ItemRange resident = M3Residency.residentRange (tree);
			assertEquals (M3ItemSelection.getVisibleRange (tree), resident, "top " + index);
			long [] stats = M3Residency.stats (tree);
			assertEquals (resident.size (), stats [M3Residency.RESIDENT_ROWS]);
			assertTrue (stats [M3Residency.FACADES] >= 1);
			assertTrue (stats [M3Residency.HELD_FACADES] <= stats [M3Residency.FACADES]);
		}
		tree.removeAll ();
		flush ();
		assertTrue (M3Residency.residentRange (tree).isEmpty ());
	}

	/* ------------------------------------------------------------------ arguments */

	@Test
	public void test_argumentsAreChecked () {
		Table table = new Table (shell, SWT.NONE);
		new TableItem (table, SWT.NONE);
		assertThrows (IllegalArgumentException.class, () -> M3Residency.residentRange ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.residentRange ((List) null));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.residentRange ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.stats ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.stats ((List) null));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.stats ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.isMaterialized (null, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.isMaterialized (table, -1));
		assertThrows (IllegalArgumentException.class, () -> M3Residency.isMaterialized (table, 1));
		Table empty = new Table (shell, SWT.NONE);
		assertTrue (M3Residency.residentRange (empty).isEmpty ());
		assertEquals (0, M3Residency.stats (empty) [M3Residency.FACADES]);
		table.dispose ();
		assertThrows (SWTException.class, () -> M3Residency.residentRange (table));
		assertThrows (SWTException.class, () -> M3Residency.isMaterialized (table, 0));
	}
}
