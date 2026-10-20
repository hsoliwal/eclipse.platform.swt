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
package org.eclipse.swt.m3;

/**
 * Rows with columns and children (public model API, brief rule 26): what a <code>Tree</code> shows. The roots are
 * the children of {@link M3Patch#ROOT}; the flat methods of {@link M3ListModel} read the roots. Each parent's children
 * are ordered by an order-statistic index over slots, so rank ({@link #indexOf(long)}) and select
 * ({@link #childId(long, int)}) are O(log n). Expansion is model state ({@link M3ListModel#EXPANDED}): two trees that
 * show one model share it.
 *
 * @since 3.138
 */
public interface M3TreeModel extends M3TableModel {

	/**
	 * Returns the number of children of a parent.
	 *
	 * @param parent the parent id, or {@link M3Patch#ROOT}
	 * @return the children, or -1 while they are not known (lazy children: no patch of that parent yet)
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no row has the id</li>
	 * </ul>
	 */
	int childCount (long parent);

	/**
	 * Returns the id of a child.
	 *
	 * @param parent the parent id, or {@link M3Patch#ROOT}
	 * @param index the child
	 * @return the id
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no row has the parent id</li>
	 *    <li>ERROR_INVALID_RANGE - if the index is out of range</li>
	 * </ul>
	 */
	long childId (long parent, int index);

	/**
	 * Returns the parent of a row.
	 *
	 * @param id the id
	 * @return the parent id, {@link M3Patch#ROOT} for a root
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no row has the id</li>
	 * </ul>
	 */
	long parent (long id);

	/**
	 * Returns the depth of a row.
	 *
	 * @param id the id
	 * @return 0 for a root
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no row has the id</li>
	 * </ul>
	 */
	int depth (long id);

	/**
	 * Returns the slot of the parent of a slot (a lane read).
	 *
	 * @param slot the slot
	 * @return the parent's slot, -1 for a root
	 */
	int parentSlot (int slot);

	/**
	 * Returns the number of rows of the expanded tree: the roots, and the children of every expanded row whose
	 * ancestors are expanded.
	 *
	 * @return the visible rows
	 */
	long visibleRowCount ();

	/**
	 * Returns the row of an id in the expanded tree.
	 *
	 * @param id the id
	 * @return the visible row, or -1 when an ancestor is collapsed or no row has the id
	 */
	long visibleRow (long id);

	/**
	 * Returns the id at a row of the expanded tree.
	 *
	 * @param row the visible row
	 * @return the id
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the row is out of range</li>
	 * </ul>
	 */
	long idAtVisibleRow (long row);

	@Override
	M3TreeModel snapshot ();
}
