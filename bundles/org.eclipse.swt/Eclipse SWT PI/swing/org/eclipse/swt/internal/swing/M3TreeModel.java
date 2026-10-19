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

import javax.swing.tree.*;

/**
 * Tree model of an SWT Tree in the viewport mode: the model of the original mode, whose insertion delta does not
 * read the inserted children. <code>DefaultTreeModel.nodesWereInserted</code> puts every inserted child into its
 * event, which would materialize every row of a virtual tree; the listeners of a <code>JTree</code> (its layout
 * cache and its look and feel) use the indices only, so the event carries no children.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API.
 * </p>
 */
public final class M3TreeModel extends DefaultTreeModel {
	private static final long serialVersionUID = 1L;

	public M3TreeModel (TreeNode root) {
		super (root);
	}

	/**
	 * Tells the listeners that the children <code>from</code> (inclusive) to <code>to</code> (exclusive) of a
	 * parent were inserted, without reading them.
	 *
	 * @param parent the parent node
	 * @param from the first inserted index
	 * @param to the index after the last inserted one
	 */
	public void inserted (TreeNode parent, int from, int to) {
		if (to <= from) return;
		int [] indices = new int [to - from];
		for (int i = 0; i < indices.length; i++) {
			indices [i] = from + i;
		}
		fireTreeNodesInserted (this, getPathToRoot (parent), indices, null);
	}

	/**
	 * Tells the listeners that children of a parent were removed.
	 *
	 * @param parent the parent node
	 * @param indices the removed indices, ascending
	 * @param removed the removed nodes, in the same order; a child that was never materialized is represented by
	 * any object that is not a node of this model
	 */
	public void removed (TreeNode parent, int [] indices, Object [] removed) {
		fireTreeNodesRemoved (this, getPathToRoot (parent), indices, removed);
	}
}
