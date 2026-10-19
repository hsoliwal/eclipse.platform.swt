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
package org.eclipse.swt.internal.swing;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.*;
import javax.swing.tree.*;

import org.junit.*;

/**
 * The viewport mode of the inner tree of a {@link JTreeTable} (lightweight components only, so the test runs
 * headless): a root with 5 children, each with 3 grandchildren.
 */
public class M3TreeTableRowsTest {
	private JTreeTable treeTable;
	private DefaultMutableTreeNode root;
	private final List<TableModelEvent> events = new ArrayList<> ();

	@Before
	public void setUp () {
		root = new DefaultMutableTreeNode ("root");
		for (int i = 0; i < 5; i++) {
			DefaultMutableTreeNode child = new DefaultMutableTreeNode ("child " + i);
			for (int j = 0; j < 3; j++) {
				child.add (new DefaultMutableTreeNode ("grandchild " + i + "." + j));
			}
			root.add (child);
		}
		treeTable = new JTreeTable (new DefaultTreeModel (root));
		treeTable.tableModel.addTableModelListener (events::add);
	}

	private TreePath child (int i) {
		return new TreePath (new Object [] {root, root.getChildAt (i)});
	}

	@Test
	public void installMakesALargeModelWithAFixedRowHeight () {
		assertNull (M3TreeTableRows.of (treeTable.tree));
		M3TreeTableRows rows = M3TreeTableRows.install (treeTable);
		assertSame (rows, M3TreeTableRows.of (treeTable.tree));
		assertTrue (treeTable.tree.isLargeModel ());
		assertTrue (treeTable.tree.getRowHeight () > 0);
	}

	@Test
	public void aMissingRowHeightIsMeasuredFromTheRenderer () {
		treeTable.tree.setRowHeight (0);
		M3TreeTableRows.install (treeTable);
		assertTrue (treeTable.tree.getRowHeight () > 0);
	}

	@Test
	public void theTreeTableListenerIsSwappedInPlace () {
		TreeExpansionListener first = new TreeExpansionAdapter ();
		treeTable.tree.addTreeExpansionListener (first);
		TreeExpansionListener [] before = treeTable.tree.getTreeExpansionListeners ();
		M3TreeTableRows.install (treeTable);
		TreeExpansionListener [] after = treeTable.tree.getTreeExpansionListeners ();
		assertEquals (before.length, after.length);
		for (int i = 0; i < after.length; i++) {
			assertNotSame (JTreeTable.class, after [i].getClass ().getEnclosingClass ());
			if (before [i].getClass ().getEnclosingClass () != JTreeTable.class) assertSame (before [i], after [i]);
		}
		assertSame ("getTreeExpansionListeners answers the last added first", first, after [0]);
	}

	@Test
	public void expandAndCollapseChangeTheTableByTheRowDelta () {
		M3TreeTableRows.install (treeTable);
		treeTable.tree.expandPath (new TreePath (root));
		events.clear ();
		treeTable.expandPath (child (1));
		assertEquals (1, events.size ());
		TableModelEvent inserted = events.get (0);
		assertEquals (TableModelEvent.INSERT, inserted.getType ());
		assertEquals (3, inserted.getFirstRow ());
		assertEquals (5, inserted.getLastRow ());
		events.clear ();
		treeTable.collapsePath (child (1));
		assertEquals (1, events.size ());
		assertEquals (TableModelEvent.DELETE, events.get (0).getType ());
		assertEquals (3, events.get (0).getFirstRow ());
		assertEquals (5, events.get (0).getLastRow ());
	}

	@Test
	public void expansionEventsWithoutARowChangeAreIgnored () {
		M3TreeTableRows.install (treeTable);
		events.clear ();
		treeTable.tree.fireTreeExpanded (child (0));
		treeTable.tree.fireTreeCollapsed (child (0));
		assertTrue (events.isEmpty ());
	}

	@Test
	public void theTreeColumnIsAsWideAsTheWidestRowShown () {
		assertTrue ("original mode", treeTable.getPreferredColumnWidth (0) > 0);
		M3TreeTableRows rows = M3TreeTableRows.install (treeTable);
		treeTable.tree.expandPath (new TreePath (root));
		treeTable.setSize (300, 3 * rows.pitch ());
		treeTable.doLayout ();
		int shown = treeTable.getPreferredColumnWidth (0);
		assertTrue (shown > 0);
		treeTable.setSize (300, 100 * rows.pitch ());
		treeTable.doLayout ();
		assertTrue (treeTable.getPreferredColumnWidth (0) >= shown);
		rows.resetWidth ();
		treeTable.setSize (300, 0);
		assertTrue (treeTable.getPreferredColumnWidth (0) > 0);
	}

	@Test
	public void anEmptyTreeHasOnlyItsInsets () {
		JTreeTable empty = new JTreeTable (new DefaultTreeModel (new DefaultMutableTreeNode ("root")));
		empty.tree.setRootVisible (false);
		M3TreeTableRows.install (empty);
		assertEquals (0, empty.tree.getRowCount ());
		assertEquals (0, empty.getPreferredColumnWidth (0));
	}

	@Test
	public void pitchRowsAndPaths () {
		M3TreeTableRows rows = M3TreeTableRows.install (treeTable);
		int height = treeTable.tree.getRowHeight ();
		treeTable.setGridVisible (true);
		assertEquals (height + 1, rows.pitch ());
		treeTable.setGridVisible (false);
		assertEquals (height, rows.pitch ());
		treeTable.tree.expandPath (new TreePath (root));
		assertEquals (6, rows.rowCount ());
		assertEquals (child (2), rows.pathForRow (3));
	}

	/** An expansion listener that does nothing. */
	static final class TreeExpansionAdapter implements TreeExpansionListener {
		@Override
		public void treeExpanded (TreeExpansionEvent event) {
			// nothing
		}

		@Override
		public void treeCollapsed (TreeExpansionEvent event) {
			// nothing
		}
	}
}
