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
package org.eclipse.swt.internal.m3.collections;

import java.util.Arrays;
import java.util.Objects;

/**
 * The label lanes of one row model: an {@code int} label id per cell.
 *
 * <p>One widget stack (an SWT Table or Tree with its JFace viewer and data
 * binding, or a Swing JTable/JTree with its model) owns exactly one instance.
 * Every layer reads and writes the same ids, so no layer keeps a String per
 * row: label providers intern their text here, items and renderers read the
 * pooled String facade, and measurement goes through the shared extent cache.
 * Each cell holds one reference on its label in the {@link M3LabelPool}; the
 * pool's evictions are wired to the {@link M3TextExtentCache}.</p>
 *
 * <p>Rows are inserted and removed like the rows of a table: the lanes shift
 * by {@link System#arraycopy}. A new row's cells hold {@link M3LabelPool#EMPTY},
 * the empty string. Not thread-safe; owned by the model's thread.</p>
 */
public final class M3LabelLanes {
	private final M3LabelPool pool;
	private final M3TextExtentCache extents;
	private final int [] [] lanes;
	private int rows;

	/**
	 * Creates lanes for {@code columns} columns over {@code pool} and
	 * {@code extents}, and wires the pool's evictions to the extent cache.
	 */
	public M3LabelLanes (int columns, M3LabelPool pool, M3TextExtentCache extents) {
		if (columns < 1) throw new IllegalArgumentException ("columns < 1: " + columns);
		this.pool = Objects.requireNonNull (pool);
		this.extents = Objects.requireNonNull (extents);
		lanes = new int [columns] [16];
		pool.setEvictionListener (extents::invalidateLabel);
	}

	/** Number of rows. */
	public int rowCount () {
		return rows;
	}

	/** Number of columns. */
	public int columnCount () {
		return lanes.length;
	}

	/** Sets the row count, adding empty rows at the end or removing rows from the end. */
	public void setRowCount (int count) {
		if (count < 0) throw new IllegalArgumentException ("negative row count: " + count);
		if (count > rows) insertRows (rows, count - rows); else removeRows (count, rows - count);
	}

	/** Inserts {@code count} empty rows before {@code row}. */
	public void insertRows (int row, int count) {
		Objects.checkIndex (row, rows + 1);
		if (count < 0) throw new IllegalArgumentException ("negative count: " + count);
		ensureCapacity (rows + count);
		for (int [] lane : lanes) {
			System.arraycopy (lane, row, lane, row + count, rows - row);
			Arrays.fill (lane, row, row + count, M3LabelPool.EMPTY);
		}
		rows += count;
	}

	/** Removes the rows {@code [row, row + count)} and releases their labels. */
	public void removeRows (int row, int count) {
		Objects.checkFromIndexSize (row, count, rows);
		for (int [] lane : lanes) {
			for (int i = row; i < row + count; i++) pool.release (lane [i]);
			System.arraycopy (lane, row + count, lane, row, rows - row - count);
		}
		rows -= count;
	}

	/** Sets the text of a cell: interns the new text, then releases the old label. */
	public void setText (int row, int column, String text) {
		int [] lane = lane (row, column);
		int id = pool.intern (text);
		pool.release (lane [row]);
		lane [row] = id;
	}

	/** Label id of a cell. */
	public int labelId (int row, int column) {
		return lane (row, column) [row];
	}

	/** Text of a cell, the pooled String facade of its label. */
	public String text (int row, int column) {
		return pool.text (labelId (row, column));
	}

	/** Extent of a cell's label in {@code fontId}, measured once per distinct label. */
	public long extent (int row, int column, int fontId, M3TextExtentCache.Measurer measurer) {
		return extents.extent (labelId (row, column), fontId, measurer);
	}

	/** Width of the widest label of {@code column} among rows {@code [from, to)}. */
	public int widest (int column, int from, int to, int fontId, M3TextExtentCache.Measurer measurer) {
		Objects.checkIndex (column, lanes.length);
		Objects.checkFromToIndex (from, to, rows);
		return extents.widest (lanes [column], from, to, fontId, measurer);
	}

	/** The label pool of this model. */
	public M3LabelPool pool () {
		return pool;
	}

	/** The extent cache of this model. */
	public M3TextExtentCache extents () {
		return extents;
	}

	/** Estimated heap bytes of the lanes, the pool and the extent cache. */
	public long retainedBytes () {
		return 16L + lanes.length * (16L + lanes [0].length * 4L) + pool.retainedBytes () + extents.retainedBytes ();
	}

	private int [] lane (int row, int column) {
		Objects.checkIndex (column, lanes.length);
		Objects.checkIndex (row, rows);
		return lanes [column];
	}

	private void ensureCapacity (int capacity) {
		if (capacity <= lanes [0].length) return;
		int grown = Math.max (capacity, lanes [0].length * 2);
		for (int c = 0; c < lanes.length; c++) lanes [c] = Arrays.copyOf (lanes [c], grown);
	}
}
