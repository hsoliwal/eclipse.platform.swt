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
 * com.m3.collections.M3RowIndex (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/nebula 79fdab533b25bd228e96a70feed55c905ab9aefb,
 * GridRowIndex.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * Rank, select and pixel prefix questions over the rows of a Grid, in item order: positions are
 * item indices, a visible row counts once and adds its extent (height plus the one-pixel line),
 * a hidden row counts neither. Answered by {@link GridRowExtents} (Fenwick trees, rebuilt after a
 * structural change) and by {@link M3RowTree} (the item list itself, never rebuilt).
 */
public interface M3RowIndex {
	/** @return the number of visible rows */
	int visibleCount();

	/** @return the extent of all visible rows */
	long totalExtent();

	/**
	 * @param position a position
	 * @return its extent, 0 when hidden
	 */
	int extentAt(int position);

	/**
	 * @param position a position in {@code [0, size]}
	 * @return the number of visible rows before it
	 */
	int rankOf(int position);

	/**
	 * @param position a position in {@code [0, size]}
	 * @return the extent of the visible rows before it
	 */
	long extentBefore(int position);

	/**
	 * @param rank a visible row, {@code 0 <= rank < visibleCount()}
	 * @return its position
	 */
	int positionOfRank(int rank);

	/**
	 * @param rank a number of visible rows, {@code 0 <= rank <= visibleCount()}
	 * @return the extent of the first {@code rank} visible rows
	 */
	long extentOfRank(int rank);

	/**
	 * @param offset a pixel offset from the first visible row
	 * @return the largest number of visible rows whose total extent is at most {@code offset}, or
	 *         -1 when {@code offset} is negative
	 */
	int rankAtExtent(long offset);

	/**
	 * @param position a position in {@code [0, size]}
	 * @return the first visible position at or after it, or -1
	 */
	int nextVisible(int position);

	/**
	 * @param position a position in {@code [-1, size - 1]}
	 * @return the last visible position at or before it, or -1
	 */
	int previousVisible(int position);
}
