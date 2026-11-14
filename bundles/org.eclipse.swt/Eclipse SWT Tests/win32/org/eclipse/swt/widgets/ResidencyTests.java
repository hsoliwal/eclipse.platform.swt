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
package org.eclipse.swt.widgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.ItemRange;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of Residency (enhanced M3 API, leaf 7). The oracle for the window is the visible
 * range (<code>ItemSelection.getVisibleRange</code>): once a widget has painted, its resident
 * rows contain it. With <code>-Dswt.widget.mode=viewport</code> the windows are also checked to be
 * bounded; with <code>-Dswt.widget.mode=original</code> every row is resident.
 */
public class ResidencyTests {
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

	private static void assertCovers (ItemRange outer, ItemRange inner, String what) {
		if (inner.isEmpty ()) return;
		assertTrue (outer.start () <= inner.start () && inner.end () <= outer.end (), what + ": " + outer + " does not cover " + inner);
	}

	private static boolean viewport (Widget widget) {
		return ModeQuery.of (widget) == ModeQuery.VIEWPORT;
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
			ItemRange resident = Residency.residentRange (table);
			assertCovers (resident, ItemSelection.getVisibleRange (table), "top " + top);
			if (viewport (table)) {
				assertTrue (resident.size () < 1000, "bounded window " + resident);
			} else {
				assertEquals (TABLE_ROWS, resident.size ());
			}
			assertEquals (resident.size (), Residency.stats (table) [Residency.RESIDENT_ROWS]);
		}
	}

	@Test
	public void test_virtualTableIsStockAndFullyResident () {
		/* a virtual table keeps the stock owner-data path in every mode: every row is resident */
		Table table = virtualTable ();
		assertEquals (ROWS, Residency.residentRange (table).size ());
	}

	@Test
	public void test_plainTableRowsHaveNoItemUntilAskedWithTheViewport () {
		Table table = plainTable ();
		int far = TABLE_ROWS - 3;
		boolean before = Residency.isMaterialized (table, far);
		if (viewport (table)) assertFalse (before, "a lane row has no item until asked");
		assertEquals (before, Residency.isMaterialized (table, far), "asking changed the answer");
		TableItem item = table.getItem (far);
		assertTrue (Residency.isMaterialized (table, far));
		item.setText ("asked");
		assertTrue (Residency.stats (table) [Residency.FACADES] >= 1);
	}

	@Test
	public void test_isMaterializedNeverMakesAnItem () {
		Table table = virtualTable ();
		int far = ROWS - 7;
		long facadesBefore = Residency.stats (table) [Residency.FACADES];
		assertFalse (Residency.isMaterialized (table, far));
		assertFalse (Residency.isMaterialized (table, far));
		assertEquals (facadesBefore, Residency.stats (table) [Residency.FACADES]);
		TableItem item = table.getItem (far);
		assertTrue (Residency.isMaterialized (table, far));
		assertEquals ("row " + far, item.getText ());
		assertTrue (Residency.stats (table) [Residency.FACADES] > facadesBefore);
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
		for (int i = 0; i < items.length; i += 37) assertTrue (Residency.isMaterialized (table, i));
		long [] stats = Residency.stats (table);
		assertEquals (items.length, stats [Residency.FACADES]);
		assertTrue (stats [Residency.HELD_FACADES] <= stats [Residency.FACADES]);
		assertCovers (Residency.residentRange (table), ItemSelection.getVisibleRange (table), "plain");
	}

	@Test
	public void test_heldFacadesStayBoundedWhileScrolling () {
		Table table = plainTable ();
		for (int top = 0; top < TABLE_ROWS; top += TABLE_ROWS / 40) {
			table.setTopIndex (top);
			table.update ();
			flush ();
		}
		long [] stats = Residency.stats (table);
		assertTrue (stats [Residency.FACADES] >= stats [Residency.HELD_FACADES]);
		assertTrue (stats [Residency.HELD_FACADES] >= 0);
		if (viewport (table)) assertTrue (stats [Residency.HELD_FACADES] < 2000, "held " + stats [Residency.HELD_FACADES]);
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
			ItemRange resident = Residency.residentRange (list);
			assertCovers (resident, ItemSelection.getVisibleRange (list), "top " + top);
			if (viewport (list)) {
				assertTrue (resident.size () < 2000, "bounded window " + resident);
			} else {
				assertEquals (ROWS, resident.size ());
			}
			long [] stats = Residency.stats (list);
			assertArrayEquals (new long [] {resident.size (), 0, 0}, stats);
		}
		list.removeAll ();
		flush ();
		assertTrue (Residency.residentRange (list).isEmpty ());
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
			ItemRange resident = Residency.residentRange (tree);
			assertCovers (resident, ItemSelection.getVisibleRange (tree), "top " + index);
			if (viewport (tree)) {
				assertTrue (resident.size () < 20_000, "bounded window " + resident);
			} else {
				assertEquals (RowMath.visibleRowCount (tree), resident.size ());
			}
			long [] stats = Residency.stats (tree);
			assertEquals (resident.size (), stats [Residency.RESIDENT_ROWS]);
			assertTrue (stats [Residency.FACADES] >= 1);
			assertTrue (stats [Residency.HELD_FACADES] <= stats [Residency.FACADES]);
		}
		tree.removeAll ();
		flush ();
		assertTrue (Residency.residentRange (tree).isEmpty ());
	}

	/* ------------------------------------------------------------------ arguments */

	@Test
	public void test_argumentsAreChecked () {
		Table table = new Table (shell, SWT.NONE);
		new TableItem (table, SWT.NONE);
		assertThrows (IllegalArgumentException.class, () -> Residency.residentRange ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> Residency.residentRange ((List) null));
		assertThrows (IllegalArgumentException.class, () -> Residency.residentRange ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> Residency.stats ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> Residency.stats ((List) null));
		assertThrows (IllegalArgumentException.class, () -> Residency.stats ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> Residency.isMaterialized (null, 0));
		assertThrows (IllegalArgumentException.class, () -> Residency.isMaterialized (table, -1));
		assertThrows (IllegalArgumentException.class, () -> Residency.isMaterialized (table, 1));
		Table empty = new Table (shell, SWT.NONE);
		assertTrue (Residency.residentRange (empty).isEmpty ());
		assertEquals (0, Residency.stats (empty) [Residency.FACADES]);
		table.dispose ();
		assertThrows (SWTException.class, () -> Residency.residentRange (table));
		assertThrows (SWTException.class, () -> Residency.isMaterialized (table, 0));
	}

	/**
	 * In process, the official-jar differential Test_org_eclipse_swt_widgets_ResidencyDifferential (tests bundle):
	 * after every seeded public operation the resident ranges of the Table and the List cover their visible rows
	 * (public API), the stats are ordered (<code>FACADES &gt;= HELD_FACADES &gt;= 0</code>, unknown -1 allowed) and
	 * asking <code>isMaterialized</code> twice gives the same answer.
	 */
	@Test
	public void test_residentRangesCoverTheVisibleRowsOnSeededSteps () {
		for (int style : new int [] {SWT.NONE, SWT.VIRTUAL}) {
			SeededWidgetScene scene = new SeededWidgetScene (display, style);
			try {
				Random random = new Random (0x4D330007L);
				for (int step = 0; step < 150; step++) {
					scene.step (random);
					String at = style + " step " + step;
					for (int i = 0; i < Math.min (scene.table.getItemCount (), 64); i += 7) {
						assertEquals (Residency.isMaterialized (scene.table, i), Residency.isMaterialized (scene.table, i), at + " row " + i);
					}
					assertCovers (scene.visibleTable (), Residency.residentRange (scene.table), Residency.stats (scene.table), at + " table");
					assertCovers (scene.visibleList (), Residency.residentRange (scene.list), Residency.stats (scene.list), at + " list");
				}
			} finally {
				scene.dispose ();
			}
		}
	}

	private static void assertCovers (int [] visible, ItemRange resident, long [] stats, String at) {
		if (visible [1] >= visible [0]) {
			assertFalse (resident.isEmpty (), at + " " + Arrays.toString (visible));
			assertTrue (resident.start () <= visible [0] && resident.end () >= visible [1], at + " " + resident + " " + Arrays.toString (visible));
		}
		assertTrue (stats [Residency.FACADES] == -1 || stats [Residency.FACADES] >= stats [Residency.HELD_FACADES] && stats [Residency.HELD_FACADES] >= 0, at + " " + Arrays.toString (stats));
	}
}
