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

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.swing.tree.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.junit.*;

/**
 * Acceptance of the viewport mode of the Initiative 31 (SWT on Swing) Tree, Table and List: the behaviour the
 * mode promises (DESIGN.md section 3) and the official SWT answers it restores (UPSTREAM_DEFECTS.tsv). Runs on
 * the hidden desktop; skips when a widget is in the original mode.
 */
public class M3ViewportAcceptanceTest {
	private static Display display;
	private Shell shell;
	private int setData;

	@BeforeClass
	public static void createDisplay () {
		display = new Display ();
	}

	@AfterClass
	public static void disposeDisplay () {
		display.dispose ();
	}

	@Before
	public void setUp () {
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (320, 240);
		setData = 0;
	}

	@After
	public void tearDown () {
		shell.dispose ();
		pump ();
	}

	private void pump () {
		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < 100_000 && display.readAndDispatch (); i++) {
				// drain
			}
			try {
				Thread.sleep (20);
			} catch (InterruptedException e) {
				Thread.currentThread ().interrupt ();
			}
		}
	}

	private Tree virtualTree (int count) {
		assumeTrue (M3TreeViewport.ENABLED);
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		tree.addListener (SWT.SetData, e -> {
			setData++;
			TreeItem item = (TreeItem) e.item;
			TreeItem parent = item.getParentItem ();
			item.setText ((parent == null ? "r" : parent.getText () + ".") + e.index);
		});
		tree.setItemCount (count);
		return tree;
	}

	@Test
	public void setItemCountCreatesNoItemAndSendsNoSetData () {
		Tree tree = virtualTree (100_000);
		assertEquals (100_000, tree.getItemCount ());
		assertEquals (0, setData);
		assertEquals (0, tree.viewport.roots.items.size ());
		shell.open ();
		pump ();
		assertTrue ("only rows on screen are filled: " + setData, setData > 0 && setData < 100);
		assertTrue (tree.viewport.roots.items.size () < 100);
	}

	@Test
	public void itemsAreMaterializedWhenRead () {
		Tree tree = virtualTree (1000);
		TreeItem item = tree.getItem (500);
		assertSame (item, tree.getItem (500));
		assertEquals ("r500", item.getText ());
		assertEquals (500, tree.indexOf (item));
		assertEquals (1000, tree.getItems ().length);
		assertEquals (1000, tree.viewport.roots.items.size ());
	}

	@Test
	public void outOfRangeReadsFail () {
		Tree tree = virtualTree (10);
		assertThrows (IllegalArgumentException.class, () -> tree.getItem (10));
		assertThrows (IllegalArgumentException.class, () -> tree.getItem (-1));
		assertThrows (IllegalArgumentException.class, () -> new TreeItem (tree, SWT.NONE, 11));
	}

	@Test
	public void childrenAreLazyToo () {
		Tree tree = virtualTree (5);
		TreeItem parent = tree.getItem (2);
		assertEquals (0, parent.getItemCount ());
		assertEquals (0, parent.getItems ().length);
		assertEquals (-1, parent.indexOf (tree.getItem (0)));
		parent.setItemCount (0);
		assertNull (parent.m3Children);
		parent.setItemCount (50);
		assertEquals (50, parent.getItemCount ());
		TreeItem child = parent.getItem (7);
		assertEquals ("r2.7", child.getText ());
		assertEquals (7, parent.indexOf (child));
		assertSame (parent, child.getParentItem ());
		parent.setExpanded (true);
		assertTrue (parent.getExpanded ());
		parent.setItemCount (5);
		assertTrue (child.isDisposed ());
		assertEquals (5, parent.getItemCount ());
		parent.removeAll ();
		assertEquals (0, parent.getItemCount ());
	}

	@Test
	public void shrinkingReleasesMaterializedItems () {
		Tree tree = virtualTree (100);
		TreeItem kept = tree.getItem (10);
		TreeItem dropped = tree.getItem (90);
		boolean [] disposed = new boolean [1];
		dropped.addListener (SWT.Dispose, e -> disposed [0] = true);
		tree.setItemCount (50);
		assertEquals (50, tree.getItemCount ());
		assertTrue (disposed [0]);
		assertFalse (kept.isDisposed ());
		assertEquals (10, tree.indexOf (kept));
		tree.setItemCount (50);
		assertEquals (50, tree.getItemCount ());
	}

	@Test
	public void insertAndDisposeKeepIndices () {
		Tree tree = virtualTree (20);
		TreeItem last = tree.getItem (19);
		TreeItem inserted = new TreeItem (tree, SWT.NONE, 5);
		inserted.setText ("x");
		assertEquals (21, tree.getItemCount ());
		assertEquals (5, tree.indexOf (inserted));
		assertEquals (20, tree.indexOf (last));
		TreeItem appended = new TreeItem (tree, SWT.NONE);
		assertEquals (21, tree.indexOf (appended));
		inserted.dispose ();
		assertEquals (21, tree.getItemCount ());
		assertEquals (19, tree.indexOf (last));
		TreeItem child = new TreeItem (last, SWT.NONE);
		assertEquals (0, last.indexOf (child));
		new TreeItem (last, SWT.NONE, 0);
		assertEquals (1, last.indexOf (child));
		assertEquals (2, last.getItemCount ());
		tree.viewport.remove (inserted, false);
		assertEquals (21, tree.getItemCount ());
	}

	@Test
	public void removeAllDisposesMaterializedItemsAndDropsTheRest () {
		Tree tree = virtualTree (1000);
		TreeItem item = tree.getItem (3);
		tree.removeAll ();
		assertEquals (0, tree.getItemCount ());
		assertTrue (item.isDisposed ());
		tree.removeAll ();
		assertEquals (0, tree.getItemCount ());
	}

	@Test
	public void clearAllRequestsDataAgain () {
		Tree tree = virtualTree (10);
		TreeItem item = tree.getItem (4);
		item.getText ();
		int before = setData;
		tree.clearAll (true);
		assertEquals ("r4", item.getText ());
		assertTrue ("SetData again for the item read and at most the rows the viewport shows: " + (setData - before), setData > before && setData - before <= 30);
	}

	@Test
	public void columnsReachMaterializedRootItems () {
		Tree tree = virtualTree (10);
		TreeItem item = tree.getItem (1);
		item.getText ();
		TreeColumn first = new TreeColumn (tree, SWT.NONE, 0);
		assertEquals ("", item.getText ());
		TreeColumn second = new TreeColumn (tree, SWT.NONE, 1);
		item.setText (1, "b");
		assertEquals ("b", item.getText (1));
		second.dispose ();
		first.dispose ();
		assertEquals (0, tree.getColumnCount ());
	}

	@Test
	public void topItemIsTheItemAtTheTopOfTheViewport () {
		Tree tree = virtualTree (10_000);
		shell.open ();
		pump ();
		assertEquals (0, tree.indexOf (tree.getTopItem ()));
		tree.setTopItem (tree.getItem (5000));
		pump ();
		assertEquals (5000, tree.indexOf (tree.getTopItem ()));
		TreeItem parent = tree.getItem (5001);
		parent.setItemCount (3);
		tree.setTopItem (parent.getItem (1));
		assertEquals ("a collapsed child has no row", 5000, tree.indexOf (tree.getTopItem ()));
		tree.setTopItem (tree.getItem (9999));
		pump ();
		int top = tree.indexOf (tree.getTopItem ());
		assertTrue ("clamped to the last page: " + top, top > 9900 && top < 9999);
	}

	@Test
	public void anEmptyTreeHasNoTopItem () {
		Tree tree = virtualTree (0);
		shell.open ();
		pump ();
		assertNull (tree.getTopItem ());
	}

	@Test
	public void expandingAddsTableRows () {
		Tree tree = virtualTree (30);
		shell.open ();
		pump ();
		TreeItem parent = tree.getItem (0);
		parent.setItemCount (4);
		parent.setExpanded (true);
		pump ();
		assertEquals ("r0.3", parent.getItem (3).getText ());
		parent.setExpanded (false);
		pump ();
		assertFalse (parent.getExpanded ());
		Point size = tree.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		assertTrue (size.x > 0 && size.y > 0);
		assertTrue ("computeSize reads only rows on screen: " + setData, setData < 60);
	}

	@Test
	public void childVectorAnswersSwing () {
		Tree tree = virtualTree (3);
		M3TreeChildren roots = tree.viewport.roots;
		assertFalse (roots.isEmpty ());
		assertEquals (3, roots.size ());
		TreeNode first = roots.firstElement ();
		TreeNode last = roots.lastElement ();
		assertSame (first, roots.get (0));
		assertEquals (0, roots.indexOf (first));
		assertEquals (2, roots.lastIndexOf (last));
		assertEquals (-1, roots.indexOf (first, 1));
		assertEquals (-1, roots.indexOf ("not a node"));
		assertTrue (roots.contains (last));
		assertEquals (3, roots.toArray ().length);
		assertEquals (3, roots.toArray (new TreeNode [0]).length);
		Iterator<TreeNode> iterator = roots.iterator ();
		assertSame (first, iterator.next ());
		java.util.Enumeration<TreeNode> elements = roots.elements ();
		for (int i = 0; i < 3; i++) elements.nextElement ();
		assertFalse (elements.hasMoreElements ());
		assertThrows (NoSuchElementException.class, elements::nextElement);
		assertThrows (ArrayIndexOutOfBoundsException.class, () -> roots.elementAt (3));
		assertThrows (ArrayIndexOutOfBoundsException.class, () -> roots.elementAt (-1));
		assertTrue (roots.equals (roots));
		assertFalse (roots.equals (tree.viewport.children (tree.getItem (0), true)));
		assertEquals (System.identityHashCode (roots), roots.hashCode ());
		assertTrue (roots.toString ().contains ("count=3"));
		M3TreeChildren empty = tree.viewport.children (tree.getItem (1), true);
		assertTrue (empty.isEmpty ());
		assertThrows (NoSuchElementException.class, empty::firstElement);
		assertThrows (NoSuchElementException.class, empty::lastElement);
	}

	@Test
	public void childVectorIsReadOnly () {
		Tree tree = virtualTree (2);
		M3TreeChildren roots = tree.viewport.roots;
		TreeNode node = roots.get (0);
		assertThrows (UnsupportedOperationException.class, () -> roots.insertElementAt (node, 0));
		assertThrows (UnsupportedOperationException.class, () -> roots.removeElementAt (0));
		assertThrows (UnsupportedOperationException.class, () -> roots.addElement (node));
		assertThrows (UnsupportedOperationException.class, () -> roots.add (node));
		assertThrows (UnsupportedOperationException.class, () -> roots.add (0, node));
		assertThrows (UnsupportedOperationException.class, () -> roots.set (0, node));
		assertThrows (UnsupportedOperationException.class, () -> roots.remove (0));
		assertThrows (UnsupportedOperationException.class, () -> roots.remove (node));
		assertThrows (UnsupportedOperationException.class, roots::removeAllElements);
		assertThrows (UnsupportedOperationException.class, roots::clear);
	}

	@Test
	public void disposingTheTreeReleasesItsItems () {
		Tree tree = virtualTree (100);
		TreeItem item = tree.getItem (7);
		tree.dispose ();
		assertTrue (item.isDisposed ());
	}

	@Test
	public void tableTopIndexFollowsTheViewport () {
		assumeTrue (M3TableViewport.ENABLED);
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText ("r" + e.index));
		table.setItemCount (10_000);
		shell.open ();
		pump ();
		assertEquals (0, table.getTopIndex ());
		table.setTopIndex (5000);
		pump ();
		assertEquals (5000, table.getTopIndex ());
		table.setTopIndex (9999);
		pump ();
		int top = table.getTopIndex ();
		assertTrue ("clamped: " + top, top > 9900 && top < 9999);
	}

	@Test
	public void virtualTableRowsChangeInBulk () {
		assumeTrue (M3TableViewport.ENABLED);
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText ("r" + e.index));
		table.setItemCount (100);
		TableItem kept = table.getItem (10);
		TableItem dropped = table.getItem (60);
		TableItem last = table.getItem (99);
		table.setItemCount (50);
		assertEquals (50, table.getItemCount ());
		assertTrue (dropped.isDisposed ());
		assertTrue (last.isDisposed ());
		assertFalse (kept.isDisposed ());
		assertEquals ("r10", kept.getText ());
		table.setItemCount (0);
		assertEquals (0, table.getItemCount ());
		assertTrue (kept.isDisposed ());
	}

	@Test
	public void tableRemovesRunsOfRowsAndDisposesMaterializedItems () {
		assumeTrue (M3TableViewport.ENABLED);
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText ("r" + e.index));
		table.setItemCount (100);
		TableItem before = table.getItem (9);
		TableItem inside = table.getItem (30);
		TableItem after = table.getItem (61);
		java.util.List<Integer> rowEvents = new java.util.ArrayList<> ();
		((javax.swing.JTable) ((org.eclipse.swt.internal.swing.CTable) table.handle).getSwingComponent ()).getModel ()
			.addTableModelListener (e -> {
				if (e.getType () == javax.swing.event.TableModelEvent.DELETE) rowEvents.add (e.getLastRow () - e.getFirstRow () + 1);
			});
		table.remove (10, 60);
		assertEquals (49, table.getItemCount ());
		assertTrue (inside.isDisposed ());
		assertFalse (before.isDisposed ());
		assertFalse (after.isDisposed ());
		assertEquals ("r61", after.getText ());
		assertEquals (after, table.getItem (10));
		assertEquals ("a run of 30, the materialized item, a run of 20", java.util.List.of (30, 1, 20), rowEvents);
		rowEvents.clear ();
		table.removeAll ();
		assertEquals (0, table.getItemCount ());
		assertTrue (before.isDisposed ());
		assertTrue (after.isDisposed ());
		assertEquals ("a run of 38, the two materialized items, a run of 9", java.util.List.of (38, 1, 1, 9), rowEvents);
		table.removeAll ();
		assertEquals (0, table.getItemCount ());
	}

	@Test
	public void plainTableRemovesItemByItem () {
		assumeTrue (M3TableViewport.ENABLED);
		Table table = new Table (shell, SWT.BORDER);
		for (int i = 0; i < 5; i++) new TableItem (table, SWT.NONE).setText ("p" + i);
		table.remove (1, 3);
		assertEquals (2, table.getItemCount ());
		assertEquals ("p4", table.getItem (1).getText ());
		table.removeAll ();
		assertEquals (0, table.getItemCount ());
	}

	@Test
	public void listSetItemsReachesSwingAsOneDelta () {
		assumeTrue (M3ListViewport.ENABLED);
		List list = new List (shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		list.setItems ("a", "b", "c");
		list.setSelection (1);
		javax.swing.ListModel<?> model = ((javax.swing.JList<?>) ((org.eclipse.swt.internal.swing.CList) list.handle).getSwingComponent ()).getModel ();
		java.util.List<String> events = new java.util.ArrayList<> ();
		model.addListDataListener (new javax.swing.event.ListDataListener () {
			public void intervalAdded (javax.swing.event.ListDataEvent e) { events.add ("+" + e.getIndex0 () + ".." + e.getIndex1 ()); }
			public void intervalRemoved (javax.swing.event.ListDataEvent e) { events.add ("-" + e.getIndex0 () + ".." + e.getIndex1 ()); }
			public void contentsChanged (javax.swing.event.ListDataEvent e) { events.add ("~"); }
		});
		String [] items = new String [1000];
		for (int i = 0; i < items.length; i++) items [i] = "n" + i;
		list.setItems (items);
		assertEquals (java.util.List.of ("-0..2", "+0..999"), events);
		assertEquals (1000, list.getItemCount ());
		assertEquals ("n999", list.getItem (999));
		assertEquals ("the selection goes with the old rows", 0, list.getSelectionCount ());
		events.clear ();
		list.setItems ();
		assertEquals (java.util.List.of ("-0..999"), events);
		assertEquals (0, list.getItemCount ());
		list.setItems ();
		assertEquals (0, list.getItemCount ());
	}

	@Test
	public void plainTableKeepsTheOriginalSetItemCount () {
		assumeTrue (M3TableViewport.ENABLED);
		Table table = new Table (shell, SWT.BORDER);
		table.setItemCount (20);
		assertEquals (20, table.getItemCount ());
		assertEquals ("", table.getItem (19).getText ());
	}

	@Test
	public void listScrollContract () {
		assumeTrue (M3ListViewport.ENABLED);
		List list = new List (shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		list.setTopIndex (3);
		list.showSelection ();
		String [] items = new String [10_000];
		for (int i = 0; i < items.length; i++) items [i] = "r" + i;
		list.setItems (items);
		shell.open ();
		pump ();
		list.showSelection ();
		assertEquals (0, list.getTopIndex ());
		list.setTopIndex (5000);
		pump ();
		assertEquals (5000, list.getTopIndex ());
		list.setSelection (5002);
		list.showSelection ();
		pump ();
		assertEquals ("a shown selection does not scroll", 5000, list.getTopIndex ());
		list.setSelection (9999);
		list.showSelection ();
		pump ();
		int top = list.getTopIndex ();
		assertTrue ("the last row is shown: " + top, top > 9900 && top < 9999);
		list.setSelection (100);
		list.showSelection ();
		pump ();
		top = list.getTopIndex ();
		assertTrue ("a hidden row goes to the middle: " + top, top < 100 && top > 80);
	}
}
