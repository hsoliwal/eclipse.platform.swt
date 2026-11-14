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

import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of ScrollMemento (enhanced M3 API, leaf 13). The oracle is the text of the top row
 * before and after the edits. The class passes unchanged in every startup mode.
 */
public class ScrollMementoTests {
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

	private Table table (int rows) {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		for (int i = 0; i < rows; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		return table;
	}

	@Test
	public void test_restoreReturnsToTheSavedTopRow () {
		Table table = table (500);
		table.setTopIndex (240);
		long memento = ScrollMemento.save (table);
		table.setTopIndex (0);
		assertTrue (ScrollMemento.restore (table, memento));
		assertEquals (240, table.getTopIndex ());
		/* a rebuild with the same rows */
		table.removeAll ();
		for (int i = 0; i < 500; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		assertTrue (ScrollMemento.restore (table, memento));
		assertEquals ("row 240", table.getItem (table.getTopIndex ()).getText ());
	}

	@Test
	public void test_remapKeepsTheAnchorRowOnTop () {
		Table table = table (500);
		table.setTopIndex (200);
		long memento = ScrollMemento.save (table);
		/* 10 rows inserted above, 5 removed above, 3 inserted below */
		for (int i = 0; i < 10; i++) new TableItem (table, SWT.NONE, 50).setText ("new " + i);
		memento = ScrollMemento.remap (memento, 50, 10);
		table.remove (0, 4);
		memento = ScrollMemento.remap (memento, 0, -5);
		for (int i = 0; i < 3; i++) new TableItem (table, SWT.NONE, 400).setText ("below " + i);
		memento = ScrollMemento.remap (memento, 400, 3);
		table.setTopIndex (0);
		assertTrue (ScrollMemento.restore (table, memento));
		assertEquals ("row 200", table.getItem (table.getTopIndex ()).getText ());
		/* the anchor itself removed: the row that took its place is on top */
		int top = table.getTopIndex ();
		table.remove (top, top + 2);
		memento = ScrollMemento.remap (memento, top, -3);
		table.setTopIndex (0);
		ScrollMemento.restore (table, memento);
		assertEquals ("row 203", table.getItem (table.getTopIndex ()).getText ());
	}

	@Test
	public void test_restoreClampsToTheRows () {
		List list = new List (shell, SWT.SINGLE | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 300; i++) list.add ("row " + i);
		list.setTopIndex (280);
		long memento = ScrollMemento.save (list);
		list.setItemCount (100);
		assertTrue (ScrollMemento.restore (list, memento));
		assertTrue (list.getTopIndex () <= 99);
		list.removeAll ();
		assertTrue (ScrollMemento.restore (list, memento));
		assertEquals (0, list.getItemCount ());
	}

	@Test
	public void test_treeAnchorIsAVisibleRow () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 100; i++) {
			TreeItem root = new TreeItem (tree, SWT.NONE);
			root.setText ("root " + i);
			for (int j = 0; j < 3; j++) new TreeItem (root, SWT.NONE).setText ("root " + i + "." + j);
		}
		tree.getItem (10).setExpanded (true);
		tree.setTopItem (tree.getItem (10).getItem (1));
		long memento = ScrollMemento.save (tree);
		tree.setTopItem (tree.getItem (0));
		assertTrue (ScrollMemento.restore (tree, memento));
		assertEquals ("root 10.1", tree.getTopItem ().getText ());
	}

	@Test
	public void test_foreignLongsAreIgnored () {
		Table table = table (100);
		table.setTopIndex (30);
		for (long foreign : new long [] {0L, -1L, 30L, Long.MAX_VALUE, Long.MIN_VALUE, ScrollMemento.save (table) | 1L}) {
			assertFalse (ScrollMemento.restore (table, foreign), Long.toHexString (foreign));
			assertEquals (30, table.getTopIndex ());
			assertEquals (foreign, ScrollMemento.remap (foreign, 0, 5), "remap leaves a foreign long alone");
		}
	}

	@Test
	public void test_emptyWidgets () {
		Tree tree = new Tree (shell, SWT.NONE);
		long memento = ScrollMemento.save (tree);
		assertTrue (ScrollMemento.restore (tree, memento));
		assertFalse (ScrollMemento.restore (tree, 0L));
		new TreeItem (tree, SWT.NONE).setText ("only");
		assertTrue (ScrollMemento.restore (tree, ScrollMemento.remap (memento, 0, 1)));
		assertEquals ("only", tree.getTopItem ().getText ());
		Table table = new Table (shell, SWT.NONE);
		assertTrue (ScrollMemento.restore (table, ScrollMemento.save (table)));
		assertFalse (ScrollMemento.restore (new List (shell, SWT.NONE), -1L));
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = table (3);
		long memento = ScrollMemento.save (table);
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.save ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.save ((List) null));
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.save ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.restore ((Table) null, memento));
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.restore ((List) null, memento));
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.restore ((Tree) null, memento));
		assertThrows (IllegalArgumentException.class, () -> ScrollMemento.remap (memento, -1, 1));
		assertEquals (memento, ScrollMemento.remap (memento, 0, 0));
		table.dispose ();
		assertThrows (SWTException.class, () -> ScrollMemento.save (table));
		assertThrows (SWTException.class, () -> ScrollMemento.restore (table, memento));
	}

	@Test
	public void test_removalBelowTheAnchorKeepsIt () {
		Table table = table (100);
		table.setTopIndex (10);
		long memento = ScrollMemento.save (table);
		/* rows 20..24 removed: the anchor row 10 is above them and stays */
		assertEquals (memento, ScrollMemento.remap (memento, 20, -5));
		/* an insertion below the anchor keeps it too */
		assertEquals (memento, ScrollMemento.remap (memento, 30, 4));
	}

	/**
	 * In process, the official-jar differential Test_org_eclipse_swt_widgets_ScrollMementoDifferential (tests bundle):
	 * <code>save</code>, an insertion or removal above or below the top row, <code>remap</code> and
	 * <code>restore</code> leave the top index of the arithmetic anchor set through <code>setTopIndex</code>.
	 */
	@Test
	public void test_remapAndRestoreEqualTheAnchorArithmeticOnSeededSteps () {
		for (int style : new int [] {SWT.NONE, SWT.VIRTUAL}) {
			SeededWidgetScene scene = new SeededWidgetScene (display, style);
			try {
				Random random = new Random (0x4D33000DL);
				for (int step = 0; step < 200; step++) {
					if (random.nextInt (3) > 0) {
						scene.step (random);
						continue;
					}
					boolean onTable = random.nextBoolean ();
					int at = random.nextInt (200), delta = random.nextInt (13) - 6;
					memento (scene, onTable ? scene.table : scene.list, at, delta, style + " step " + step);
				}
			} finally {
				scene.dispose ();
			}
		}
	}

	private static void memento (SeededWidgetScene scene, Scrollable widget, int at, int delta, String what) {
		Table table = widget instanceof Table t ? t : null;
		List list = widget instanceof List l ? l : null;
		int count = table != null ? table.getItemCount () : list.getItemCount ();
		if (count < 8) return;
		int index = at % count, change = delta < 0 ? -Math.min (-delta, count - index) : delta;
		int row = table != null ? table.getTopIndex () : list.getTopIndex ();
		long memento = table != null ? ScrollMemento.save (table) : ScrollMemento.save (list);
		for (int i = 0; i < change; i++) {
			if (table != null) new TableItem (table, SWT.NONE, index).setText (scene.text ());
			else list.add (scene.text (), index);
		}
		if (change < 0 && table != null) table.remove (index, index - change - 1);
		if (change < 0 && list != null) list.remove (index, index - change - 1);
		if (table != null) table.setTopIndex (0);
		else list.setTopIndex (0);
		if (table != null) ScrollMemento.restore (table, ScrollMemento.remap (memento, index, change));
		else ScrollMemento.restore (list, ScrollMemento.remap (memento, index, change));
		scene.drain ();
		int restored = table != null ? table.getTopIndex () : list.getTopIndex ();
		int rows = table != null ? table.getItemCount () : list.getItemCount ();
		int anchor = Math.min (anchor (row, index, change), rows - 1);
		if (table != null) table.setTopIndex (anchor);
		else list.setTopIndex (anchor);
		scene.drain ();
		assertEquals (table != null ? table.getTopIndex () : list.getTopIndex (), restored, what);
	}

	/* the contract of remap: rows above stay, rows below move, a removed anchor becomes the first row after the removal */
	private static int anchor (int row, int index, int change) {
		if (change > 0) return row >= index ? row + change : row;
		if (row >= index - change) return row + change;
		return row >= index ? index : row;
	}
}
