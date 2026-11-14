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

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of RowMath (enhanced M3 API, leaf 6). The oracle is a recursive walk over the public
 * API (<code>getItems</code>, <code>getExpanded</code>). The class passes unchanged in every
 * startup mode: with <code>-Dswt.widget.mode=viewport</code> the virtual trees are answered by the
 * viewport's lanes, with <code>-Dswt.widget.mode=original</code> by the walk.
 */
public class RowMathTests {
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

	/* ------------------------------------------------------------------ oracle */

	private static java.util.List<TreeItem> visible (Tree tree) {
		java.util.List<TreeItem> rows = new ArrayList<> ();
		collect (tree.getItems (), rows);
		return rows;
	}

	private static void collect (TreeItem [] items, java.util.List<TreeItem> rows) {
		for (TreeItem item : items) {
			rows.add (item);
			if (item.getExpanded ()) collect (item.getItems (), rows);
		}
	}

	private static long subtree (TreeItem item) {
		long rows = 1;
		if (item.getExpanded ()) {
			for (TreeItem child : item.getItems ()) rows += subtree (child);
		}
		return rows;
	}

	private static void all (TreeItem [] items, java.util.List<TreeItem> out) {
		for (TreeItem item : items) {
			out.add (item);
			all (item.getItems (), out);
		}
	}

	/* every row, every item, every subtree against the oracle */
	private static void verify (Tree tree) {
		java.util.List<TreeItem> rows = visible (tree);
		assertEquals (rows.size (), RowMath.visibleRowCount (tree));
		for (int row = 0; row < rows.size (); row++) {
			assertSame (rows.get (row), RowMath.itemAt (tree, row), "itemAt " + row);
			assertEquals (row, RowMath.rowOf (rows.get (row)), "rowOf " + row);
		}
		java.util.List<TreeItem> items = new ArrayList<> ();
		all (tree.getItems (), items);
		Set<TreeItem> shown = Collections.newSetFromMap (new IdentityHashMap<> ());
		shown.addAll (rows);
		for (TreeItem item : items) {
			if (!shown.contains (item)) assertEquals (-1, RowMath.rowOf (item), "hidden " + item.getText ());
			assertEquals (subtree (item), RowMath.subtreeRowCount (item), "subtree " + item.getText ());
		}
	}

	/* ------------------------------------------------------------------ plain tree */

	private Tree plain () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 30; i++) {
			TreeItem root = new TreeItem (tree, SWT.NONE);
			root.setText ("r" + i);
			if (i % 3 != 0) continue;
			for (int j = 0; j < 4; j++) {
				TreeItem child = new TreeItem (root, SWT.NONE);
				child.setText ("r" + i + "." + j);
				if (j % 2 == 0) for (int k = 0; k < 3; k++) new TreeItem (child, SWT.NONE).setText ("r" + i + "." + j + "." + k);
			}
		}
		tree.getItem (0).setExpanded (true);
		tree.getItem (0).getItem (0).setExpanded (true);
		tree.getItem (3).getItem (2).setExpanded (true);
		tree.getItem (9).setExpanded (true);
		return tree;
	}

	@Test
	public void test_plainTreeAgreesWithTheWalk () {
		Tree tree = plain ();
		verify (tree);
		/* a collapsed parent hides an expanded child: rowOf -1, subtree still counts its own rows */
		TreeItem hidden = tree.getItem (3).getItem (2);
		assertEquals (-1, RowMath.rowOf (hidden));
		assertEquals (4, RowMath.subtreeRowCount (hidden));
		tree.getItem (3).setExpanded (true);
		tree.getItem (0).setExpanded (false);
		verify (tree);
	}

	@Test
	public void test_plainTreeFollowsStructuralChanges () {
		Tree tree = plain ();
		new TreeItem (tree.getItem (0), SWT.NONE, 1).setText ("inserted");
		new TreeItem (tree, SWT.NONE, 2).setText ("new root");
		/* root r9 (with children) moved to index 10 */
		tree.getItem (10).getItem (1).dispose ();
		verify (tree);
		tree.getItem (0).getItem (0).setItemCount (1);
		tree.setItemCount (12);
		verify (tree);
		tree.removeAll ();
		assertEquals (0, RowMath.visibleRowCount (tree));
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (tree, 0));
	}

	/* ------------------------------------------------------------------ virtual tree */

	private Tree virtualTree (int roots) {
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			item.setText ((parent == null ? "v" : parent.getText () + ".") + event.index);
		});
		tree.setItemCount (roots);
		return tree;
	}

	@Test
	public void test_virtualTreeAgreesWithTheWalk () {
		Tree tree = virtualTree (120);
		for (int i : new int [] {0, 5, 50, 119}) {
			TreeItem root = tree.getItem (i);
			root.setItemCount (6);
			root.setExpanded (true);
		}
		TreeItem inner = tree.getItem (5).getItem (2);
		inner.setItemCount (3);
		inner.setExpanded (true);
		verify (tree);
		tree.getItem (5).setExpanded (false);
		tree.getItem (50).getItem (0).dispose ();
		tree.getItem (0).setItemCount (2);
		verify (tree);
		tree.setItemCount (40);
		verify (tree);
	}

	@Test
	public void test_millionRowVirtualTreeAnswersFromTheLanes () {
		Tree tree = virtualTree (1);
		Assumptions.assumeTrue (ModeQuery.of (tree) == ModeQuery.VIEWPORT, "lanes exist only for the viewport");
		tree.setItemCount (1_000_000);
		TreeItem middle = tree.getItem (500_000);
		middle.setItemCount (1000);
		middle.setExpanded (true);
		assertEquals (1_001_000, RowMath.visibleRowCount (tree));
		Random random = new Random (5);
		long start = System.nanoTime ();
		for (int i = 0; i < 2000; i++) {
			long row = random.nextInt (1_001_000);
			TreeItem item = RowMath.itemAt (tree, row);
			assertEquals (row, RowMath.rowOf (item));
		}
		long millis = (System.nanoTime () - start) / 1_000_000;
		assertTrue (millis < 5000, "2000 round trips took " + millis + " ms");
		assertSame (middle, RowMath.itemAt (tree, 500_000));
		assertSame (middle.getItem (999), RowMath.itemAt (tree, 501_000));
		assertSame (tree.getItem (500_001), RowMath.itemAt (tree, 501_001));
		assertEquals (1001, RowMath.subtreeRowCount (middle));
		assertEquals (999_999 + 1000, RowMath.rowOf (tree.getItem (999_999)));
	}

	/*
	 * The walk asks each parent for its child count once: a plain tree's root count walks the native siblings, so a
	 * count per step made the walk quadratic (combination matrix r0200: 10^5 roots, over 900 s in original mode).
	 */
	@Test
	public void test_walkOverManyPlainRootsIsLinear () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		int roots = 20_000;
		tree.setRedraw (false);
		for (int i = 0; i < roots; i++) new TreeItem (tree, SWT.NONE).setText ("r" + i);
		tree.setRedraw (true);
		TreeItem last = tree.getItem (roots - 1);
		long start = System.nanoTime ();
		assertEquals (roots, RowMath.visibleRowCount (tree));
		assertEquals (roots - 1, RowMath.rowOf (last));
		assertSame (last, RowMath.itemAt (tree, roots - 1));
		long millis = (System.nanoTime () - start) / 1_000_000;
		assertTrue (millis < 3000, "three walks over " + roots + " roots took " + millis + " ms");
	}

	/* ------------------------------------------------------------------ extents */

	@Test
	public void test_extentsAreRowTimesHeightAndMatchTheBounds () {
		Tree tree = plain ();
		int height = tree.getItemHeight ();
		long count = RowMath.visibleRowCount (tree);
		for (long row = 0; row < count; row++) {
			long y = RowMath.rowExtent (tree, row);
			assertEquals (row * height, y);
			assertEquals (row, RowMath.rowAtExtent (tree, y));
			assertEquals (row, RowMath.rowAtExtent (tree, y + height - 1));
		}
		assertEquals (count * height, RowMath.rowExtent (tree, count));
		assertEquals (-1, RowMath.rowAtExtent (tree, count * height));
		assertEquals (-1, RowMath.rowAtExtent (tree, -1));
		assertThrows (IllegalArgumentException.class, () -> RowMath.rowExtent (tree, count + 1));
		assertThrows (IllegalArgumentException.class, () -> RowMath.rowExtent (tree, -1));
		/* rows on screen: bounds relative to the top item */
		tree.setTopItem (tree.getItem (3));
		TreeItem top = tree.getTopItem ();
		long topRow = RowMath.rowOf (top);
		int topY = top.getBounds ().y;
		int clientBottom = tree.getClientArea ().height;
		for (long row = topRow; row < count; row++) {
			TreeItem item = RowMath.itemAt (tree, row);
			int y = item.getBounds ().y;
			if (y >= clientBottom) break;
			assertEquals (RowMath.rowExtent (tree, row) - RowMath.rowExtent (tree, topRow), y - topY, item.getText ());
		}
	}

	/* ------------------------------------------------------------------ arguments */

	@Test
	public void test_argumentsAreChecked () {
		Tree tree = plain ();
		assertThrows (IllegalArgumentException.class, () -> RowMath.visibleRowCount (null));
		assertThrows (IllegalArgumentException.class, () -> RowMath.rowOf (null));
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (null, 0));
		assertThrows (IllegalArgumentException.class, () -> RowMath.subtreeRowCount (null));
		assertThrows (IllegalArgumentException.class, () -> RowMath.rowExtent (null, 0));
		assertThrows (IllegalArgumentException.class, () -> RowMath.rowAtExtent (null, 0));
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (tree, -1));
		long count = RowMath.visibleRowCount (tree);
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (tree, count));
		Tree virtual = virtualTree (10);
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (virtual, 10));
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (virtual, -1));
		TreeItem item = tree.getItem (0);
		item.dispose ();
		assertThrows (SWTException.class, () -> RowMath.rowOf (item));
		assertThrows (SWTException.class, () -> RowMath.subtreeRowCount (item));
		tree.dispose ();
		assertThrows (SWTException.class, () -> RowMath.visibleRowCount (tree));
		assertThrows (SWTException.class, () -> RowMath.itemAt (tree, 0));
	}

	@Test
	public void test_walkStopsAtTheEndOfAPlainTree () {
		Tree tree = new Tree (shell, SWT.NONE);
		for (int i = 0; i < 4; i++) new TreeItem (tree, SWT.NONE).setText ("r" + i);
		/* far past the last row: the walk runs out of items before it reaches the row */
		assertThrows (IllegalArgumentException.class, () -> RowMath.itemAt (tree, 9));
		assertEquals ("r3", RowMath.itemAt (tree, 3).getText ());
	}

	/**
	 * In process, the official-jar differential Test_org_eclipse_swt_widgets_RowMathDifferential (tests bundle):
	 * after every seeded structural, expansion and count operation the answers of RowMath equal the walk of the
	 * expanded items over the public API, rows, items, subtrees and extents.
	 */
	@Test
	public void test_answersEqualTheWalkOnSeededSteps () {
		for (int style : new int [] {SWT.NONE, SWT.VIRTUAL}) {
			Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | style);
			tree.setBounds (5, 5, 280, 360);
			tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
			try {
				Random random = new Random (0x4D330006L);
				int [] serial = {0};
				for (int step = 0; step < 150; step++) {
					seededOp (tree, random.nextInt (13), random.nextInt (6), serial);
					verify (tree);
					long count = visible (tree).size (), height = Math.max (1, tree.getItemHeight ());
					assertEquals (count * height, RowMath.rowExtent (tree, count), style + " step " + step);
					assertEquals (count == 0 ? -1 : count / 2, RowMath.rowAtExtent (tree, count * height / 2), style + " step " + step);
					assertEquals (-1, RowMath.rowAtExtent (tree, count * height), style + " step " + step);
					assertEquals (-1, RowMath.rowAtExtent (tree, -1), style + " step " + step);
				}
			} finally {
				tree.dispose ();
			}
		}
	}

	/* the operations of the differential: roots, children, grandchildren, expansion, disposal, counts, clearAll */
	private static void seededOp (Tree tree, int kind, int arg, int [] serial) {
		int n = tree.getItemCount ();
		TreeItem root = n > 0 ? tree.getItem (arg % n) : null;
		TreeItem child = root != null && root.getItemCount () > 0 ? root.getItem (0) : null;
		switch (kind) {
			case 0, 1 -> new TreeItem (tree, SWT.NONE, kind == 0 ? n : 0).setText ("r" + serial [0]++);
			case 2, 3 -> { if (root != null) new TreeItem (root, SWT.NONE).setText ("c" + serial [0]++); }
			case 4, 5 -> { if (root != null) root.setExpanded (kind == 4); }
			case 6 -> { if (child != null) new TreeItem (child, SWT.NONE).setText ("g" + serial [0]++); }
			case 7 -> { if (child != null) child.setExpanded (arg % 2 == 0); }
			case 8 -> { if (root != null && arg < 2) root.dispose (); }
			case 9 -> { if (child != null) child.dispose (); }
			case 10 -> { if (root != null) root.setItemCount (arg); }
			case 11 -> tree.setItemCount (arg == 0 ? 0 : arg + 2);
			default -> tree.clearAll (arg == 0);
		}
	}
}
