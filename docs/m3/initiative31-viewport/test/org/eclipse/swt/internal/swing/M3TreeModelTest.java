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
import java.util.Vector;

import javax.swing.event.*;
import javax.swing.tree.*;

import org.junit.*;

/**
 * The insertion delta of {@link M3TreeModel} reads no child; the removal delta carries the nodes it is given.
 * Headless.
 */
public class M3TreeModelTest {
	/** A child vector that counts how often a child is read. */
	static final class CountingChildren extends Vector<TreeNode> {
		private static final long serialVersionUID = 1L;
		int reads;
		final int count;

		CountingChildren (int count) {
			this.count = count;
		}

		@Override
		public synchronized int size () {
			return count;
		}

		@Override
		public synchronized TreeNode elementAt (int index) {
			reads++;
			return new DefaultMutableTreeNode ("child " + index);
		}
	}

	private DefaultMutableTreeTableNode root;
	private CountingChildren children;
	private M3TreeModel model;
	private final List<TreeModelEvent> inserted = new ArrayList<> ();
	private final List<TreeModelEvent> removed = new ArrayList<> ();

	@Before
	public void setUp () {
		root = new DefaultMutableTreeTableNode (new Object [] {"root"});
		children = new CountingChildren (100_000);
		root.m3Children (children);
		model = new M3TreeModel (root);
		model.addTreeModelListener (new TreeModelListener () {
			@Override
			public void treeNodesInserted (TreeModelEvent e) {
				inserted.add (e);
			}

			@Override
			public void treeNodesRemoved (TreeModelEvent e) {
				removed.add (e);
			}

			@Override
			public void treeNodesChanged (TreeModelEvent e) {
				fail ("no change event");
			}

			@Override
			public void treeStructureChanged (TreeModelEvent e) {
				fail ("no structure event");
			}
		});
	}

	@Test
	public void insertionReadsNoChild () {
		model.inserted (root, 0, 100_000);
		assertEquals (1, inserted.size ());
		TreeModelEvent event = inserted.get (0);
		assertEquals (100_000, event.getChildIndices ().length);
		assertEquals (0, event.getChildIndices () [0]);
		assertEquals (99_999, event.getChildIndices () [99_999]);
		assertNull (event.getChildren ());
		assertSame (root, event.getTreePath ().getLastPathComponent ());
		assertEquals ("the event read no child", 0, children.reads);
		assertEquals ("the stock delta reads every child", 100_000, readsOfStockDelta ());
	}

	private int readsOfStockDelta () {
		CountingChildren stock = new CountingChildren (100_000);
		DefaultMutableTreeTableNode node = new DefaultMutableTreeTableNode (new Object [] {"stock"});
		node.m3Children (stock);
		int [] indices = new int [100_000];
		for (int i = 0; i < indices.length; i++) indices [i] = i;
		new DefaultTreeModel (node).nodesWereInserted (node, indices);
		return stock.reads;
	}

	@Test
	public void anEmptyRangeIsNoEvent () {
		model.inserted (root, 5, 5);
		model.inserted (root, 6, 5);
		assertTrue (inserted.isEmpty ());
	}

	@Test
	public void removalCarriesTheGivenNodes () {
		Object placeholder = new Object ();
		DefaultMutableTreeNode gone = new DefaultMutableTreeNode ("gone");
		model.removed (root, new int [] {3, 4}, new Object [] {placeholder, gone});
		assertEquals (1, removed.size ());
		assertArrayEquals (new int [] {3, 4}, removed.get (0).getChildIndices ());
		assertSame (placeholder, removed.get (0).getChildren () [0]);
		assertSame (gone, removed.get (0).getChildren () [1]);
		assertEquals (0, children.reads);
	}
}
