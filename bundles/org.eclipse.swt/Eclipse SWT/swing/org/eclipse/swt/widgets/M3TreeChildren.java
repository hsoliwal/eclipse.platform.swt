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

import java.util.*;

import javax.swing.tree.*;

import org.eclipse.swt.internal.swing.*;

/**
 * The children of one parent of a {@link Tree} in the viewport mode: the logical child count and the
 * children materialized so far, by index. A parent is the tree itself (its root items) or a
 * {@link TreeItem}.
 * <p>
 * The parent's Swing node uses this object as its child vector, so <code>JTree</code>,
 * <code>DefaultTreeModel</code> and <code>DefaultMutableTreeNode</code> read the count and the
 * children from it. Reading a child that is not materialized creates it (see
 * {@link M3TreeViewport#item(M3TreeChildren, int)}). The vector is read-only for Swing: every change
 * comes from the tree, through {@link M3TreeViewport}.
 * </p>
 */
final class M3TreeChildren extends Vector<TreeNode> {
	private static final long serialVersionUID = 1L;

	final transient M3TreeViewport viewport;
	/** The parent item, or <code>null</code> for the root items. */
	final transient TreeItem owner;
	/** The Swing node of the parent. */
	final transient DefaultMutableTreeTableNode node;
	/** The children materialized so far, by index. */
	final transient VirtualItemStorage<TreeItem> items = new VirtualItemStorage<> ();
	/** The number of children. */
	int count;
	/** First child added while the tree was hidden, not yet told to Swing; -1 when Swing knows every child. */
	int hiddenFrom = -1;

	M3TreeChildren (M3TreeViewport viewport, TreeItem owner, DefaultMutableTreeTableNode node) {
		super (0);
		this.viewport = viewport;
		this.owner = owner;
		this.node = node;
	}

	@Override
	public synchronized int size () {
		return count;
	}

	@Override
	public synchronized boolean isEmpty () {
		return count == 0;
	}

	@Override
	public synchronized TreeNode elementAt (int index) {
		if (index < 0 || index >= count) throw new ArrayIndexOutOfBoundsException (index);
		return (TreeNode) viewport.item (this, index).handle;
	}

	@Override
	public synchronized TreeNode get (int index) {
		return elementAt (index);
	}

	@Override
	public synchronized TreeNode firstElement () {
		if (count == 0) throw new NoSuchElementException ();
		return elementAt (0);
	}

	@Override
	public synchronized TreeNode lastElement () {
		if (count == 0) throw new NoSuchElementException ();
		return elementAt (count - 1);
	}

	@Override
	public int indexOf (Object node) {
		return indexOf (node, 0);
	}

	@Override
	public synchronized int indexOf (Object node, int from) {
		if (!(node instanceof CTreeItem)) return -1;
		TreeItem item = ((CTreeItem) node).getTreeItem ();
		int index = item == null ? -1 : items.indexOfIdentity (item);
		return index >= from ? index : -1;
	}

	@Override
	public synchronized int lastIndexOf (Object node) {
		return indexOf (node, 0);
	}

	@Override
	public boolean contains (Object node) {
		return indexOf (node, 0) >= 0;
	}

	@Override
	public Enumeration<TreeNode> elements () {
		return new Enumeration<> () {
			int next;

			@Override
			public boolean hasMoreElements () {
				return next < count;
			}

			@Override
			public TreeNode nextElement () {
				if (next >= count) throw new NoSuchElementException ();
				return elementAt (next++);
			}
		};
	}

	@Override
	public synchronized Iterator<TreeNode> iterator () {
		return Collections.list (elements ()).iterator ();
	}

	@Override
	public synchronized Object [] toArray () {
		return Collections.list (elements ()).toArray ();
	}

	@Override
	public synchronized <T> T [] toArray (T [] array) {
		return Collections.list (elements ()).toArray (array);
	}

	@Override
	public synchronized void insertElementAt (TreeNode node, int index) {
		throw readOnly ();
	}

	@Override
	public synchronized void removeElementAt (int index) {
		throw readOnly ();
	}

	@Override
	public synchronized void addElement (TreeNode node) {
		throw readOnly ();
	}

	@Override
	public synchronized boolean add (TreeNode node) {
		throw readOnly ();
	}

	@Override
	public void add (int index, TreeNode node) {
		throw readOnly ();
	}

	@Override
	public synchronized TreeNode set (int index, TreeNode node) {
		throw readOnly ();
	}

	@Override
	public synchronized TreeNode remove (int index) {
		throw readOnly ();
	}

	@Override
	public boolean remove (Object node) {
		throw readOnly ();
	}

	@Override
	public synchronized void removeAllElements () {
		throw readOnly ();
	}

	@Override
	public void clear () {
		throw readOnly ();
	}

	@Override
	public synchronized boolean equals (Object other) {
		return this == other;
	}

	@Override
	public synchronized int hashCode () {
		return System.identityHashCode (this);
	}

	@Override
	public synchronized String toString () {
		return "M3TreeChildren {count=" + count + ", materialized=" + items.size () + "}";
	}

	private static UnsupportedOperationException readOnly () {
		return new UnsupportedOperationException ("children of a viewport Tree change through the Tree");
	}
}
