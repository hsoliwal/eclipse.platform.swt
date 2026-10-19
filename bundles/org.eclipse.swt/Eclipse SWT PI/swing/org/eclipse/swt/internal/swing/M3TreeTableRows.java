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

import java.awt.*;
import java.util.Enumeration;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

/**
 * Viewport mode of the inner <code>JTree</code> of a {@link JTreeTable}: the tree keeps state only for
 * expanded nodes and its rows are measured only when they are shown.
 * <ul>
 * <li>The tree is a large model with a fixed row height, so Swing lays rows out arithmetically
 * (<code>FixedHeightLayoutCache</code>) instead of measuring every row.</li>
 * <li>An expansion or collapse changes the table by the rows shown below the node, counted from the model's
 * child counts and the expanded paths, instead of counting the descendants node by node
 * (which reads every child).</li>
 * <li>The tree column's preferred width is the widest row shown so far, instead of the widest of all rows
 * (which renders every row).</li>
 * </ul>
 * Installed as a client property of the inner tree; absent in the original mode.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API.
 * </p>
 */
public final class M3TreeTableRows implements TreeWillExpandListener {
	static final String KEY = "org.eclipse.swt.internal.swing.M3TreeTableRows";

	private final JTreeTable treeTable;
	private final JTree tree;
	private int collapsing;
	private int widest;

	private M3TreeTableRows (JTreeTable treeTable) {
		this.treeTable = treeTable;
		this.tree = treeTable.tree;
	}

	/**
	 * Puts the inner tree of a tree table in the viewport mode.
	 *
	 * @param treeTable the tree table
	 * @return the bookkeeping now attached to its inner tree
	 */
	public static M3TreeTableRows install (JTreeTable treeTable) {
		JTree tree = treeTable.tree;
		M3TreeTableRows rows = new M3TreeTableRows (treeTable);
		tree.putClientProperty (KEY, rows);
		tree.addTreeWillExpandListener (rows);
		rows.swapExpansionListener ();
		rows.fixRowHeight ();
		tree.setLargeModel (true);
		return rows;
	}

	/** The bookkeeping of a tree, or <code>null</code> when it is not in the viewport mode. */
	static M3TreeTableRows of (JTree tree) {
		Object rows = tree.getClientProperty (KEY);
		return rows instanceof M3TreeTableRows ? (M3TreeTableRows) rows : null;
	}

	/**
	 * Sets the row height the rows are laid out with: the look and feel's when it gives one (Windows), otherwise
	 * (Metal) the height of a rendered empty row or of the tree's font, whichever is larger.
	 */
	public void fixRowHeight () {
		int height = tree.getRowHeight ();
		if (height <= 0) {
			Component c = tree.getCellRenderer ().getTreeCellRendererComponent (tree, null, false, false, true, 0, false);
			height = Math.max (tree.getFontMetrics (tree.getFont ()).getHeight (), c.getPreferredSize ().height);
		}
		tree.setRowHeight (height);
	}

	@Override
	public void treeWillExpand (TreeExpansionEvent event) {
		// rows are counted once the path is expanded
	}

	@Override
	public void treeWillCollapse (TreeExpansionEvent event) {
		collapsing = shownBelow (event.getPath ());
	}

	/**
	 * Replaces the tree table's own expansion listener, which counts the descendants of an expanded node one
	 * child at a time, by {@link #rowsListener}. <code>getTreeExpansionListeners</code> answers the listeners
	 * last-added first, so they are added back from the end: every listener keeps its place.
	 */
	private void swapExpansionListener () {
		TreeExpansionListener [] listeners = tree.getTreeExpansionListeners ();
		for (TreeExpansionListener listener : listeners) {
			tree.removeTreeExpansionListener (listener);
		}
		for (int i = listeners.length - 1; i >= 0; i--) {
			TreeExpansionListener listener = listeners [i];
			tree.addTreeExpansionListener (listener.getClass ().getEnclosingClass () == JTreeTable.class ? rowsListener : listener);
		}
	}

	/** Inserts the table rows an expansion added and deletes the rows a collapse removed. */
	private final TreeExpansionListener rowsListener = new TreeExpansionListener () {
		@Override
		public void treeExpanded (TreeExpansionEvent event) {
			int count = shownBelow (event.getPath ());
			if (count > 0) fire (event.getPath (), count, TableModelEvent.INSERT);
		}

		@Override
		public void treeCollapsed (TreeExpansionEvent event) {
			int count = collapsing;
			collapsing = 0;
			if (count > 0) fire (event.getPath (), count, TableModelEvent.DELETE);
		}
	};

	/**
	 * The number of rows shown below an expanded path: its children, plus the children of every expanded
	 * descendant that is shown. Counts come from the model and the tree's expanded state, so no child is read
	 * and the answer does not depend on when the look and feel updates its layout.
	 */
	private int shownBelow (TreePath path) {
		if (!tree.isExpanded (path)) return 0;
		TreeModel model = tree.getModel ();
		int count = model.getChildCount (path.getLastPathComponent ());
		Enumeration<TreePath> expanded = tree.getExpandedDescendants (path);
		while (expanded.hasMoreElements ()) {
			TreePath descendant = expanded.nextElement ();
			if (!descendant.equals (path)) count += model.getChildCount (descendant.getLastPathComponent ());
		}
		return count;
	}

	private void fire (TreePath path, int count, int type) {
		int first = tree.getRowForPath (path) + 1;
		treeTable.tableModel.fireTableChanged (new TableModelEvent (treeTable.tableModel, first, first + count - 1, TableModelEvent.ALL_COLUMNS, type));
	}

	/**
	 * Hook of {@link JTreeTable#getPreferredColumnWidth(int)} for the tree column: the widest row shown
	 * so far, plus the tree's insets.
	 *
	 * @return the width, or -1 when the tree is not in the viewport mode
	 */
	static int preferredWidth (JTreeTable treeTable) {
		M3TreeTableRows rows = of (treeTable.tree);
		return rows == null ? -1 : rows.measureShown ();
	}

	private int measureShown () {
		int count = tree.getRowCount ();
		if (count > 0) {
			Rectangle shown = treeTable.getVisibleRect ();
			int first = rowAt (shown.y, count);
			int last = rowAt (shown.y + Math.max (0, shown.height - 1), count);
			for (int row = first; row <= last; row++) {
				Rectangle bounds = tree.getRowBounds (row);
				widest = Math.max (widest, bounds.x + bounds.width);
			}
		}
		Insets insets = tree.getInsets ();
		return widest + insets.left + insets.right;
	}

	private int rowAt (int y, int count) {
		int row = treeTable.table.rowAtPoint (new Point (0, y));
		return row < 0 ? count - 1 : row;
	}

	/** Forgets the widest row, for example after the rows were cleared. */
	public void resetWidth () {
		widest = 0;
	}

	/** The distance between the tops of two adjacent rows of the tree table, in pixels. */
	public int pitch () {
		return tree.getRowHeight () + treeTable.table.getIntercellSpacing ().height;
	}

	/** The number of rows of the tree table. */
	public int rowCount () {
		return tree.getRowCount ();
	}

	/** The tree's path at a row, or <code>null</code>. */
	public TreePath pathForRow (int row) {
		return tree.getPathForRow (row);
	}
}
