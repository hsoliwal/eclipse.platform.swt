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

import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.tree.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.m3.*;
import org.eclipse.swt.internal.swing.*;

/**
 * Viewport mode of a {@link Tree} on Swing (the default mode; <code>-Dswt.widget.mode.Tree=original</code>
 * selects the original implementation).
 * <ul>
 * <li>Items are created when the screen or the API needs them: <code>setItemCount</code> records a count,
 * and a child is materialized when Swing or the API reads it ({@link #item(M3TreeChildren, int)}).
 * SetData is therefore sent only for items that are shown or read.</li>
 * <li>Each bulk change reaches Swing as one model event, and the inner <code>JTree</code> is a large model
 * with a fixed row height ({@link M3TreeTableRows}).</li>
 * <li>The top item is the item shown at the top of the viewport, and <code>setTopItem</code> puts the item
 * there ({@link M3SwingScroll}).</li>
 * </ul>
 * The look and feel, the renderers and the events of the original implementation are unchanged.
 */
final class M3TreeViewport {
	/** Whether new trees use the viewport mode. */
	static final boolean ENABLED = WidgetMode.of ("Tree", WidgetMode.VIEWPORT) == WidgetMode.VIEWPORT;
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.widgets.Tree");
	/** Stands for a removed child that was never materialized, in a removal event. */
	private static final Object UNMATERIALIZED = new DefaultMutableTreeNode ();

	final Tree tree;
	final M3TreeChildren roots;
	private final M3TreeTableRows rows;
	private final M3SwingScroll scroll = new M3SwingScroll ();
	/** The parent whose slot {@link #adoptingIndex} is being materialized, or <code>null</code>. */
	private M3TreeChildren adopting;
	private int adoptingIndex = -1;

	private M3TreeViewport (Tree tree) {
		this.tree = tree;
		CTree cTree = (CTree) tree.handle;
		roots = new M3TreeChildren (this, null, cTree.getRoot ());
		cTree.getRoot ().m3Children (roots);
		rows = M3TreeTableRows.install ((JTreeTable) cTree.getSwingComponent ());
	}

	/**
	 * Puts a new tree in the viewport mode.
	 *
	 * @param tree the tree, right after its Swing handle was created
	 * @return the viewport of the tree
	 */
	static M3TreeViewport install (Tree tree) {
		M3TreeViewport viewport = new M3TreeViewport (tree);
		LOG.debug (() -> "viewport installed, style " + Integer.toHexString (tree.style));
		return viewport;
	}

	/** The children of a parent, created on demand when <code>create</code> is set. */
	M3TreeChildren children (TreeItem parentItem, boolean create) {
		if (parentItem == null) return roots;
		M3TreeChildren children = parentItem.m3Children;
		if (children == null && create) {
			DefaultMutableTreeTableNode node = (DefaultMutableTreeTableNode) parentItem.handle;
			children = new M3TreeChildren (this, parentItem, node);
			node.m3Children (children);
			parentItem.m3Children = children;
		}
		return children;
	}

	/** The child at an index, materialized when it was not. */
	TreeItem item (M3TreeChildren children, int index) {
		TreeItem item = children.items.get (index);
		if (item != null) return item;
		M3TreeChildren outer = adopting;
		int outerIndex = adoptingIndex;
		adopting = children;
		adoptingIndex = index;
		try {
			return children.owner == null ? new TreeItem (tree, SWT.NONE, index) : new TreeItem (children.owner, SWT.NONE, index);
		} finally {
			adopting = outer;
			adoptingIndex = outerIndex;
		}
	}

	/** Hook of <code>Tree.createItem</code>: adopts a materialized child, or inserts a new one. */
	void insert (TreeItem parentItem, TreeItem item, int index) {
		M3TreeChildren children = children (parentItem, true);
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) item.handle;
		if (adopting == children && adoptingIndex == index) {
			children.items.put (index, item);
			node.setParent (children.node);
			return;
		}
		if (index > children.count) SWT.error (SWT.ERROR_INVALID_RANGE);
		children.items.insert (index, item);
		children.count++;
		node.setParent (children.node);
		cTree ().getModel ().nodesWereInserted (children.node, new int [] {index});
		if (parentItem == null) cTree ().expandPath (new TreePath (children.node.getPath ()));
	}

	/** Hook of <code>Tree.releaseItem</code>: removes a disposed child. */
	void remove (TreeItem item, boolean release) {
		M3TreeChildren children = children (item.parentItem, false);
		int index = children == null ? -1 : children.items.indexOfIdentity (item);
		if (index >= 0) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) item.handle;
			children.items.remove (index);
			children.count--;
			node.setParent (null);
			cTree ().getModel ().nodesWereRemoved (children.node, new int [] {index}, new Object [] {node});
		}
		tree.handle.repaint ();
		if (release) item.release (false);
	}

	/** Hook of <code>Tree.setItemCount</code> for the root items (<code>parentItem == null</code>) or an item. */
	void setItemCount (TreeItem parentItem, int count) {
		M3TreeChildren children = children (parentItem, count > 0);
		if (children == null || children.count == count) return;
		int old = children.count;
		if (count > old) {
			grow (children, old, count);
		} else {
			shrink (children, count, true);
		}
		LOG.debug (() -> "setItemCount " + old + " -> " + count + (parentItem == null ? " (roots)" : ""));
	}

	private void grow (M3TreeChildren children, int old, int count) {
		children.count = count;
		cTree ().getModel ().nodesWereInserted (children.node, range (old, count));
		if (children.owner == null) cTree ().expandPath (new TreePath (children.node.getPath ()));
		refresh ();
	}

	/** Drops the children from <code>count</code> on; materialized ones are released when <code>release</code> is set. */
	private void shrink (M3TreeChildren children, int count, boolean release) {
		int old = children.count;
		Object [] removed = new Object [old - count];
		Arrays.fill (removed, UNMATERIALIZED);
		java.util.List<TreeItem> gone = materialized (children, count);
		for (int i = gone.size () - 1; i >= 0; i--) {
			TreeItem item = gone.get (i);
			int index = children.items.indexOfIdentity (item);
			removed [index - count] = item.handle;
			((DefaultMutableTreeNode) item.handle).setParent (null);
			if (release) item.release (false);
		}
		children.items.truncate (count, item -> {});
		children.count = count;
		cTree ().getModel ().nodesWereRemoved (children.node, range (count, old), removed);
		refresh ();
	}

	/** The materialized children at or after an index, in index order. */
	private static java.util.List<TreeItem> materialized (M3TreeChildren children, int from) {
		java.util.List<TreeItem> list = new ArrayList<> ();
		children.items.forEachIndexed ((index, item) -> {
			if (index >= from) list.add (item);
		});
		return list;
	}

	private static int [] range (int from, int to) {
		int [] indices = new int [to - from];
		Arrays.setAll (indices, i -> from + i);
		return indices;
	}

	private void refresh () {
		JComponent treeTable = (JComponent) cTree ().getSwingComponent ();
		treeTable.revalidate ();
		treeTable.repaint ();
	}

	/** Hook of <code>Tree.getItem(int)</code> and <code>TreeItem.getItem(int)</code>. */
	TreeItem getItem (TreeItem parentItem, int index) {
		M3TreeChildren children = children (parentItem, false);
		if (children == null || index < 0 || index >= children.count) SWT.error (SWT.ERROR_INVALID_RANGE);
		return item (children, index);
	}

	/** Hook of <code>Tree.getItemCount()</code> and <code>TreeItem.getItemCount()</code>. */
	int getItemCount (TreeItem parentItem) {
		M3TreeChildren children = children (parentItem, false);
		return children == null ? 0 : children.count;
	}

	/** Hook of <code>Tree.getItems()</code> and <code>TreeItem.getItems()</code>: materializes every child. */
	TreeItem [] getItems (TreeItem parentItem) {
		M3TreeChildren children = children (parentItem, false);
		TreeItem [] items = new TreeItem [children == null ? 0 : children.count];
		for (int i = 0; i < items.length; i++) {
			items [i] = item (children, i);
		}
		return items;
	}

	/** Hook of <code>Tree.indexOf(TreeItem)</code> and <code>TreeItem.indexOf(TreeItem)</code>. */
	int indexOf (TreeItem parentItem, TreeItem item) {
		M3TreeChildren children = children (parentItem, false);
		return children == null ? -1 : children.items.indexOfIdentity (item);
	}

	/** Hook of <code>Tree.removeAll()</code> and <code>TreeItem.removeAll()</code>. */
	void removeAll (TreeItem parentItem) {
		M3TreeChildren children = children (parentItem, false);
		if (children == null || children.count == 0) return;
		java.util.List<TreeItem> items = materialized (children, 0);
		for (int i = items.size () - 1; i >= 0; i--) {
			TreeItem item = items.get (i);
			item.dispose ();
		}
		if (children.count > 0) shrink (children, 0, false);
		if (parentItem == null) rows.resetWidth ();
	}

	/** Hook of <code>Tree.clearAll(boolean)</code>: only materialized items hold data. */
	void clearAll (boolean all) {
		java.util.List<TreeItem> items = materialized (roots, 0);
		for (int i = items.size () - 1; i >= 0; i--) {
			items.get (i).clearAll (all);
		}
	}

	/** Hook of <code>Tree.releaseChildren(boolean)</code>. */
	void release () {
		for (TreeItem item : materialized (roots, 0)) {
			item.release (false);
		}
		roots.items.clear (item -> {});
		roots.count = 0;
	}

	/** Hook of <code>Tree.createItem(TreeColumn, int)</code>: the materialized root items get the column. */
	void columnInserted (int index) {
		for (TreeItem item : materialized (roots, 0)) {
			item.handle.insertColumn (index);
			if (index == 0) {
				item.text = "";
				item.image = null;
			}
		}
	}

	/** Hook of <code>Tree.destroyItem(TreeColumn)</code>: the materialized root items lose the column. */
	void columnRemoved (int index) {
		for (TreeItem item : materialized (roots, 0)) {
			item.handle.removeColumn (index);
		}
	}

	/** Hook of <code>Tree.getTopItem()</code>: the item shown at the top of the viewport. */
	TreeItem getTopItem () {
		int row = scroll.topRow (viewport (), rows.rowCount (), rows.pitch ());
		TreePath path = row < 0 ? null : rows.pathForRow (row);
		return path == null ? null : ((CTreeItem) path.getLastPathComponent ()).getTreeItem ();
	}

	/** Hook of <code>Tree.setTopItem(TreeItem)</code>: shows the item at the top of the viewport. */
	void setTopItem (TreeItem item) {
		int row = cTree ().getRowForPath (new TreePath (((DefaultMutableTreeNode) item.handle).getPath ()));
		if (row < 0) return;
		scroll.setTopRow (viewport (), row, rows.rowCount (), rows.pitch ());
	}

	private CTree cTree () {
		return (CTree) tree.handle;
	}

	private JViewport viewport () {
		return ((JScrollPane) tree.handle).getViewport ();
	}
}
