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

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of ScrollInfo and Scrolling (enhanced M3 API, leaf 2). Every answer is checked
 * against a public-API oracle (<code>getTopIndex</code>, the clamping of <code>setTopIndex</code>,
 * item bounds against the item area, a recursive row-order walk), so the class passes unchanged
 * in every startup mode: the mode differential of the leaf is this class run with
 * <code>-Dswt.widget.mode=viewport</code> and with <code>-Dswt.widget.mode=original</code>.
 */
public class ScrollingTests {
	static final int ROWS = 200;
	static final int ROOTS = 40;
	static final int CHILDREN = 4;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private Table table () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		for (int i = 0; i < ROWS; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		shell.open ();
		flush ();
		return table;
	}

	private org.eclipse.swt.widgets.List list () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < ROWS; i++) list.add ("row " + i);
		shell.open ();
		flush ();
		return list;
	}

	/* ROOTS roots; every third root has CHILDREN children; roots 0, 3 and 9 are expanded */
	private Tree tree () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		for (int i = 0; i < ROOTS; i++) {
			TreeItem root = new TreeItem (tree, SWT.NONE);
			root.setText ("root " + i);
			if (i % 3 != 0) continue;
			for (int j = 0; j < CHILDREN; j++) new TreeItem (root, SWT.NONE).setText ("root " + i + " / " + j);
		}
		tree.getItem (0).setExpanded (true);
		tree.getItem (3).setExpanded (true);
		tree.getItem (9).setExpanded (true);
		shell.open ();
		flush ();
		return tree;
	}

	private void flush () {
		while (display.readAndDispatch ()) {
		}
	}

	private static void visibleRows (Tree tree, TreeItem parent, java.util.List<TreeItem> rows) {
		for (TreeItem item : parent == null ? tree.getItems () : parent.getItems ()) {
			rows.add (item);
			if (item.getExpanded ()) visibleRows (tree, item, rows);
		}
	}

	/* ------------------------------------------------------------------ Table */

	@Test
	public void test_tableScrollInfoMatchesTheWidget () {
		Table table = table ();
		ScrollInfo info = Scrolling.getScrollInfo (table);
		assertEquals (table.getTopIndex (), info.topIndex ());
		assertEquals (0, info.pixelOffset ());
		assertEquals (table.getItemHeight (), info.rowExtent ());
		assertEquals (table.getClientArea ().height, info.viewportExtent ());
		assertEquals (ROWS, info.rowCount ());
		assertEquals (ItemSelection.getVisibleRange (table).size (), info.visibleRows ());
		assertEquals (info.viewportExtent () / info.rowExtent (), info.fullRows ());
		assertEquals ((long) ROWS * table.getItemHeight (), info.contentExtent ());
		assertEquals (0L, info.pixelPosition ());
		assertTrue (info.toString ().startsWith ("ScrollInfo ["));
		/* the oracle of maxTopIndex is the clamping of setTopIndex */
		table.setTopIndex (ROWS - 1);
		flush ();
		assertEquals (table.getTopIndex (), info.maxTopIndex ());
		ScrollInfo end = Scrolling.getScrollInfo (table);
		assertEquals (table.getTopIndex (), end.topIndex ());
		assertEquals ((long) table.getTopIndex () * table.getItemHeight (), end.pixelPosition ());
		table.setHeaderVisible (true);
		flush ();
		assertEquals (table.getClientArea ().height - table.getHeaderHeight (), Scrolling.getScrollInfo (table).viewportExtent ());
	}

	@Test
	public void test_tableScrollToTop () {
		Table table = table ();
		assertEquals (50, Scrolling.scrollTo (table, 50, Scrolling.TOP));
		assertEquals (50, table.getTopIndex ());
		int max = Scrolling.getScrollInfo (table).maxTopIndex ();
		assertEquals (max, Scrolling.scrollTo (table, ROWS - 1, Scrolling.TOP));
		assertEquals (max, Scrolling.scrollTo (table, 5000, Scrolling.TOP));
		assertEquals (0, Scrolling.scrollTo (table, -3, Scrolling.TOP));
		assertEquals (0, table.getTopIndex ());
	}

	@Test
	public void test_tableScrollToBottomShowsTheRowAsTheLastFullRow () {
		Table table = table ();
		Scrolling.scrollTo (table, 80, Scrolling.BOTTOM);
		flush ();
		assertLastFullRow (table, 80);
		assertEquals (0, Scrolling.scrollTo (table, 2, Scrolling.BOTTOM));
		Scrolling.scrollTo (table, ROWS - 1, Scrolling.BOTTOM);
		flush ();
		assertLastFullRow (table, ROWS - 1);
	}

	/* row i ends inside the item area and row i + 1 does not */
	private void assertLastFullRow (Table table, int i) {
		int bottom = table.getClientArea ().height;
		Rectangle bounds = table.getItem (i).getBounds ();
		assertTrue (bounds.y + bounds.height <= bottom, "row " + i + " " + bounds + " bottom " + bottom);
		if (i + 1 < table.getItemCount ()) {
			Rectangle next = table.getItem (i + 1).getBounds ();
			assertTrue (next.y + next.height > bottom, "row " + (i + 1) + " " + next + " bottom " + bottom);
		}
	}

	@Test
	public void test_tableScrollToCenterPutsTheRowInTheMiddle () {
		Table table = table ();
		Scrolling.scrollTo (table, 100, Scrolling.CENTER);
		flush ();
		Rectangle bounds = table.getItem (100).getBounds ();
		int middle = table.getClientArea ().height / 2;
		assertTrue (Math.abs (bounds.y + bounds.height / 2 - middle) <= bounds.height, bounds + " middle " + middle);
		assertEquals (0, Scrolling.scrollTo (table, 1, Scrolling.CENTER));
	}

	@Test
	public void test_tableScrollToMinDistanceIsTheShowItemRule () {
		Table table = table ();
		Scrolling.scrollTo (table, 50, Scrolling.TOP);
		int full = Scrolling.getScrollInfo (table).fullRows ();
		assertEquals (50, Scrolling.scrollTo (table, 55, Scrolling.MIN_DISTANCE));
		assertEquals (50, Scrolling.scrollTo (table, 50 + full - 1, Scrolling.MIN_DISTANCE));
		assertEquals (20, Scrolling.scrollTo (table, 20, Scrolling.MIN_DISTANCE));
		Scrolling.scrollTo (table, 20 + full + 3, Scrolling.MIN_DISTANCE);
		flush ();
		assertLastFullRow (table, 20 + full + 3);
	}

	@Test
	public void test_tableEmptyAndErrors () {
		Table table = table ();
		table.removeAll ();
		ScrollInfo info = Scrolling.getScrollInfo (table);
		assertEquals (0, info.rowCount ());
		assertEquals (0, info.visibleRows ());
		assertEquals (0, info.maxTopIndex ());
		assertEquals (0L, info.contentExtent ());
		assertEquals (0, Scrolling.scrollTo (table, 3, Scrolling.TOP));
		table.setSize (300, 0);
		flush ();
		assertEquals (0, Scrolling.getScrollInfo (table).fullRows ());
		assertThrows (IllegalArgumentException.class, () -> Scrolling.getScrollInfo ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.scrollTo (table, 0, 4));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.scrollTo (table, 0, -1));
		table.dispose ();
		assertThrows (org.eclipse.swt.SWTException.class, () -> Scrolling.getScrollInfo (table));
	}

	/* ------------------------------------------------------------------ List */

	@Test
	public void test_listScrollInfoAndScrollTo () {
		org.eclipse.swt.widgets.List list = list ();
		ScrollInfo info = Scrolling.getScrollInfo (list);
		assertEquals (0, info.topIndex ());
		assertEquals (list.getItemHeight (), info.rowExtent ());
		assertEquals (list.getClientArea ().height, info.viewportExtent ());
		assertEquals (ROWS, info.rowCount ());
		assertEquals (ItemSelection.getVisibleRange (list).size (), info.visibleRows ());
		list.setTopIndex (ROWS - 1);
		flush ();
		assertEquals (list.getTopIndex (), info.maxTopIndex ());
		assertEquals (40, Scrolling.scrollTo (list, 40, Scrolling.TOP));
		assertEquals (list.getTopIndex (), Scrolling.getScrollInfo (list).topIndex ());
		int full = info.fullRows ();
		assertEquals (40, Scrolling.scrollTo (list, 40 + full - 1, Scrolling.MIN_DISTANCE));
		assertEquals (41, Scrolling.scrollTo (list, 40 + full, Scrolling.MIN_DISTANCE));
		assertEquals (90 - full + 1, Scrolling.scrollTo (list, 90, Scrolling.BOTTOM));
		assertEquals (90 - (full - 1) / 2, Scrolling.scrollTo (list, 90, Scrolling.CENTER));
		assertEquals (info.maxTopIndex (), Scrolling.scrollTo (list, ROWS - 1, Scrolling.TOP));
		list.removeAll ();
		assertEquals (0, Scrolling.scrollTo (list, 3, Scrolling.TOP));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.getScrollInfo ((org.eclipse.swt.widgets.List) null));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.scrollTo (list, 0, 9));
	}

	/* ------------------------------------------------------------------ Tree */

	@Test
	public void test_treeScrollInfoCountsVisibleRows () {
		Tree tree = tree ();
		java.util.List<TreeItem> rows = new ArrayList<> ();
		visibleRows (tree, null, rows);
		ScrollInfo info = Scrolling.getScrollInfo (tree);
		assertEquals (rows.size (), info.rowCount ());
		assertEquals (0, info.topIndex ());
		assertEquals (tree.getItemHeight (), info.rowExtent ());
		assertEquals (tree.getClientArea ().height, info.viewportExtent ());
		assertEquals (ItemSelection.getVisibleRange (tree).size (), info.visibleRows ());
		tree.setTopItem (rows.get (20));
		flush ();
		assertEquals (rows.indexOf (tree.getTopItem ()), Scrolling.getScrollInfo (tree).topIndex ());
		tree.getItem (3).setExpanded (false);
		flush ();
		assertEquals (rows.size () - CHILDREN, Scrolling.getScrollInfo (tree).rowCount ());
		tree.setHeaderVisible (true);
		flush ();
		assertEquals (tree.getClientArea ().height - tree.getHeaderHeight (), Scrolling.getScrollInfo (tree).viewportExtent ());
		tree.removeAll ();
		ScrollInfo empty = Scrolling.getScrollInfo (tree);
		assertEquals (0, empty.rowCount ());
		assertEquals (0, empty.topIndex ());
	}

	@Test
	public void test_treeScrollToAlignments () {
		Tree tree = tree ();
		java.util.List<TreeItem> rows = new ArrayList<> ();
		visibleRows (tree, null, rows);
		int full = Scrolling.getScrollInfo (tree).fullRows ();
		assertSame (rows.get (12), Scrolling.scrollTo (tree, rows.get (12), Scrolling.TOP));
		assertSame (rows.get (12), tree.getTopItem ());
		TreeItem top = Scrolling.scrollTo (tree, rows.get (30), Scrolling.BOTTOM);
		assertSame (tree.getTopItem (), top);
		assertEquals (30 - full + 1, rows.indexOf (top));
		flush ();
		assertLastFullRow (tree, rows, 30);
		TreeItem before = tree.getTopItem ();
		assertSame (before, Scrolling.scrollTo (tree, rows.get (28), Scrolling.MIN_DISTANCE));
		assertEquals (30 - full + 1, rows.indexOf (tree.getTopItem ()));
		assertSame (rows.get (5), Scrolling.scrollTo (tree, rows.get (5), Scrolling.MIN_DISTANCE));
		assertEquals (40 - (full - 1) / 2, rows.indexOf (Scrolling.scrollTo (tree, rows.get (40), Scrolling.CENTER)));
		int max = Scrolling.getScrollInfo (tree).maxTopIndex ();
		assertEquals (max, rows.indexOf (Scrolling.scrollTo (tree, rows.get (rows.size () - 1), Scrolling.TOP)));
		assertSame (rows.get (0), Scrolling.scrollTo (tree, rows.get (1), Scrolling.BOTTOM));
	}

	private void assertLastFullRow (Tree tree, java.util.List<TreeItem> rows, int i) {
		int bottom = tree.getClientArea ().height - (tree.getHeaderVisible () ? tree.getHeaderHeight () : 0);
		Rectangle bounds = rows.get (i).getBounds ();
		assertTrue (bounds.y + bounds.height <= bottom, "row " + i + " " + bounds + " bottom " + bottom);
		Rectangle next = rows.get (i + 1).getBounds ();
		assertTrue (next.y + next.height > bottom, "row " + (i + 1) + " " + next + " bottom " + bottom);
	}

	@Test
	public void test_treeScrollToExpandsCollapsedAncestorsWithoutEvents () {
		Tree tree = tree ();
		TreeItem root3 = tree.getItem (3);
		root3.setExpanded (false);
		int [] events = new int [1];
		tree.addListener (SWT.Expand, event -> events [0]++);
		tree.addListener (SWT.Collapse, event -> events [0]++);
		TreeItem child = root3.getItem (2);
		assertSame (child, Scrolling.scrollTo (tree, child, Scrolling.TOP));
		assertTrue (root3.getExpanded ());
		assertSame (child, tree.getTopItem ());
		assertEquals (0, events [0]);
	}

	@Test
	public void test_treeErrors () {
		Tree tree = tree ();
		Tree other = new Tree (shell, SWT.NONE);
		TreeItem foreign = new TreeItem (other, SWT.NONE);
		assertThrows (IllegalArgumentException.class, () -> Scrolling.getScrollInfo ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.scrollTo (tree, null, Scrolling.TOP));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.scrollTo (tree, foreign, Scrolling.TOP));
		assertThrows (IllegalArgumentException.class, () -> Scrolling.scrollTo (tree, tree.getItem (1), 7));
		TreeItem gone = tree.getItem (2);
		gone.dispose ();
		assertThrows (org.eclipse.swt.SWTException.class, () -> Scrolling.scrollTo (tree, gone, Scrolling.TOP));
	}
}
