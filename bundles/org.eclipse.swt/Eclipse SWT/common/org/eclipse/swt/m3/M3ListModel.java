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

import org.eclipse.swt.graphics.*;

/**
 * Flat rows of a lane model (public model API, brief rule 26): what a <code>List</code> shows, and the
 * base of {@link M3TableModel} and {@link M3TreeModel}.
 * <p>
 * A row has three names. Its <em>row</em> is its position among the children of its parent ({@link M3Patch#ROOT}
 * for a flat model), shifted by every patch. Its <em>id</em> is the client's <code>long</code> identity, given by
 * the patch that inserts it, unique in the model and never {@link M3Patch#ROOT}. Its <em>slot</em> is the dense
 * storage index of its lanes, stable for the life of the row and reused after it is removed. Cells, flags, styles and
 * extents are read by slot; id to slot is O(1) expected, row to slot and slot to row O(log n).
 * </p>
 * <p>
 * {@link #apply(M3Patch)} is the only update path. A model is applied on one thread at a time and its listeners run
 * on that thread; other threads read a {@link #snapshot()}. See <code>docs/m3/enhanced-api/MODEL_API.md</code>.
 * </p>
 *
 * @since 3.138
 */
public interface M3ListModel {

	/** Flag lane: the row is checked. */
	int CHECKED = 0;
	/** Flag lane: the check box of the row is grayed. */
	int GRAYED = 1;
	/** Flag lane: the row is expanded (trees). */
	int EXPANDED = 2;
	/** Flag lane: the row has or may have children; the expander shows before any child is known (trees). */
	int HAS_CHILDREN = 3;
	/** Flag lane: the row is disabled. */
	int DISABLED = 4;
	/** The first flag lane of the client; lanes <code>CUSTOM</code> to 63 are free for the application. */
	int CUSTOM = 16;

	/**
	 * Returns the number of rows of {@link M3Patch#ROOT}.
	 *
	 * @return the rows
	 */
	int size ();

	/**
	 * Returns the id of a row of {@link M3Patch#ROOT}.
	 *
	 * @param row the row, <code>0 &lt;= row &lt; size ()</code>
	 * @return the id
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the row is out of range</li>
	 * </ul>
	 */
	long id (int row);

	/**
	 * Returns the row of an id among the children of its parent.
	 *
	 * @param id the id
	 * @return the row, or -1 when no row has the id
	 */
	int indexOf (long id);

	/**
	 * Returns the slot of an id.
	 *
	 * @param id the id
	 * @return the slot, or -1 when no row has the id
	 */
	int slot (long id);

	/**
	 * Returns the slot of a row of {@link M3Patch#ROOT}.
	 *
	 * @param row the row
	 * @return the slot
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the row is out of range</li>
	 * </ul>
	 */
	int slotAt (int row);

	/**
	 * Returns the row of a slot among the children of its parent.
	 *
	 * @param slot the slot
	 * @return the row, or -1 when the slot is free
	 */
	int rowOf (int slot);

	/**
	 * Returns the number of slots: every live slot is below it.
	 *
	 * @return the slot capacity
	 */
	int slotCapacity ();

	/**
	 * Exports the slots of consecutive rows of a parent, in O(count + log n).
	 *
	 * @param parent the parent id, {@link M3Patch#ROOT} for a flat model
	 * @param from the first row
	 * @param count the number of rows wanted
	 * @param slots receives the slots from index 0
	 * @return the number of slots written: <code>count</code>, or fewer at the end of the rows
	 */
	int order (long parent, int from, int count, int [] slots);

	/**
	 * Returns an order key of a slot: within one parent, <code>sequence (a) &lt; sequence (b)</code> exactly when
	 * <code>rowOf (a) &lt; rowOf (b)</code>. Keys may be renumbered by any patch: read them, do not keep them.
	 *
	 * @param slot a live slot
	 * @return the key
	 */
	long sequence (int slot);

	/**
	 * Returns the generation: 0 for a new model, one more after every {@link #apply(M3Patch)}.
	 *
	 * @return the generation
	 */
	long generation ();

	/**
	 * Returns the interned labels of the text cells.
	 *
	 * @return the label table
	 */
	M3LabelTable labels ();

	/**
	 * Returns the label id of a text cell.
	 *
	 * @param slot the slot
	 * @param column the column, 0 for a list
	 * @return the label id, 0 for an empty cell
	 */
	int label (int slot, int column);

	/**
	 * Copies the text of a cell, without creating a String.
	 *
	 * @param slot the slot
	 * @param column the column
	 * @param buffer the receiver
	 * @param offset where the text starts in the buffer
	 * @return the length of the text; when the buffer is too short nothing is copied and the result is the
	 *    negative of the length
	 */
	int text (int slot, int column, char [] buffer, int offset);

	/**
	 * Returns the text of a cell (convenience; allocates for computed columns).
	 *
	 * @param slot the slot
	 * @param column the column
	 * @return the text, empty for an empty cell
	 */
	String text (int slot, int column);

	/**
	 * Returns the value of an <code>INT</code> cell.
	 *
	 * @param slot the slot
	 * @param column the column
	 * @return the value
	 */
	int intAt (int slot, int column);

	/**
	 * Returns the value of a <code>LONG</code> cell.
	 *
	 * @param slot the slot
	 * @param column the column
	 * @return the value
	 */
	long longAt (int slot, int column);

	/**
	 * Returns the value of a <code>DOUBLE</code> cell.
	 *
	 * @param slot the slot
	 * @param column the column
	 * @return the value
	 */
	double doubleAt (int slot, int column);

	/**
	 * Returns the image of a cell. Images are shared objects owned by the client.
	 *
	 * @param slot the slot
	 * @param column the column
	 * @return the image, or <code>null</code>
	 */
	Image image (int slot, int column);

	/**
	 * Returns one flag of a row.
	 *
	 * @param slot the slot
	 * @param flag the flag lane, 0 to 63
	 * @return the flag
	 */
	boolean flag (int slot, int flag);

	/**
	 * Returns one flag of 64 slots: bit <code>k</code> is the flag of slot <code>word * 64 + k</code>.
	 *
	 * @param flag the flag lane, 0 to 63
	 * @param word the word
	 * @return the bits
	 */
	long flagWord (int flag, int word);

	/**
	 * Returns the interned styles of rows and cells.
	 *
	 * @return the style table
	 */
	M3StyleTable styles ();

	/**
	 * Returns the style id of a cell or row.
	 *
	 * @param slot the slot
	 * @param column the column, or -1 for the row
	 * @return the style id, 0 for none
	 */
	int style (int slot, int column);

	/**
	 * Returns the height of a row.
	 *
	 * @param slot the slot
	 * @return the height in points, 0 for the widget's default
	 */
	int extent (int slot);

	/**
	 * Applies a patch: the only update path. The listeners run after the lanes changed.
	 *
	 * @param patch the patch
	 * @return the new generation
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the patch is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if a run does not fit the rows or an inserted id is present</li>
	 * </ul>
	 * @exception org.eclipse.swt.SWTException <ul>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the model's thread</li>
	 * </ul>
	 */
	long apply (M3Patch patch);

	/**
	 * Applies a patch only when it was built against the current generation (newest wins, rule 28).
	 *
	 * @param patch the patch
	 * @return <code>false</code> when the patch is stale and was dropped
	 */
	boolean applyIfCurrent (M3Patch patch);

	/**
	 * Returns an immutable view of the model at the current generation, readable from any thread.
	 *
	 * @return the snapshot
	 */
	M3ListModel snapshot ();

	/**
	 * Adds a listener of the applied patches.
	 *
	 * @param listener the listener
	 */
	void addModelListener (M3ModelListener listener);

	/**
	 * Removes a listener.
	 *
	 * @param listener the listener
	 */
	void removeModelListener (M3ModelListener listener);

	/**
	 * Reports whether a viewport showing this model is visible (widgets call it on <code>SWT.PartVisible</code> and
	 * <code>SWT.PartHidden</code>), so a chain of proxies can park work while nothing shows.
	 *
	 * @param viewport the widget or view
	 * @param visible whether it is visible
	 */
	void viewportVisibility (Object viewport, boolean visible);

	/**
	 * Returns how many of the reported viewports are visible.
	 *
	 * @return the visible viewports
	 */
	int visibleViewports ();
}
