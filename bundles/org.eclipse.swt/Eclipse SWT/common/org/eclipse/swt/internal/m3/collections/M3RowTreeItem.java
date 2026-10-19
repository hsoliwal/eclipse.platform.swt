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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3RowTreeItem (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * Element of an {@link M3RowTree}. The tree keeps its own slot inside the element, so finding an element's row needs
 * no hash map and no scan: the element is the identity, the slot is its address.
 */
public interface M3RowTreeItem {
	/** The element's row when it is not in a tree (the tree answers for elements it holds). */
	int getRowIndex ();

	/** The tree slot recorded by {@link #enteredRowTree(int)}, or a negative value when the element is in no tree. */
	int rowTreeSlot ();

	/** Records the slot the tree gave this element. */
	void enteredRowTree (int slot);

	/** Clears the slot; {@code lastRow} is the row the element had when it left. */
	void leftRowTree (int lastRow);

	/** Whether the element takes rows (an invisible element has extent 0). */
	boolean isVisible ();

	/** Height of the element's row in pixels. */
	int getHeight ();
}
