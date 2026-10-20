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
 * The label lanes of one row model: one label per cell.
 *
 * <p>One widget stack (an SWT Table or Tree with its JFace viewer and data
 * binding, or a Swing JTable/JTree with its model) owns exactly one instance.
 * Every layer reads and writes the same labels, so no layer keeps a String per
 * row: label providers set their text here, items and renderers read it back,
 * and measurement goes through the shared extent cache. A pooled cell holds
 * one reference on its label in the {@link M3LabelPool}; the pool's evictions
 * are wired to the {@link M3TextExtentCache}.</p>
 *
 * <p>Two lane widths. Pooled lanes ({@code immediates == false}) keep an
 * {@code int} pool id per cell (4 bytes). Immediate lanes keep an
 * {@link M3ShortLabel} {@code long} per cell (8 bytes): short labels carry
 * their own text and need no pool entry, longer labels hold a pool id. Pick
 * immediate lanes for columns whose labels are mostly short and distinct
 * (severities, units, locations, extensions); the extra 4 bytes per cell pay
 * only when the pool entries saved outweigh them (see OPTIMIZATION_LEDGER).</p>
 *
 * <p>Rows are inserted and removed like the rows of a table: the lanes shift
 * by {@link System#arraycopy}. A new row's cells hold the empty label. Not
 * thread-safe; owned by the model's thread.</p>
 */
public final class M3LabelLanes {
	private final M3LabelPool pool;
	private final M3TextExtentCache extents;
	private final boolean immediates;
	private final int [] [] ids;
	private final long [] [] labels;
	private final int columns;
	private int rows, capacity = 16;

	/** Creates pooled lanes (an int pool id per cell). */
	public M3LabelLanes (int columns, M3LabelPool pool, M3TextExtentCache extents) {
		this (columns, pool, extents, false);
	}

	/**
	 * Creates lanes for {@code columns} columns over {@code pool} and
	 * {@code extents}, immediate lanes when {@code immediates} is true, and wires
	 * the pool's evictions to the extent cache.
	 */
	public M3LabelLanes (int columns, M3LabelPool pool, M3TextExtentCache extents, boolean immediates) {
		if (columns < 1) throw new IllegalArgumentException ("columns < 1: " + columns);
		this.pool = Objects.requireNonNull (pool);
		this.extents = Objects.requireNonNull (extents);
		this.immediates = immediates;
		this.columns = columns;
		ids = immediates ? null : new int [columns] [capacity];
		labels = immediates ? new long [columns] [capacity] : null;
		pool.setEvictionListener (extents::invalidateLabel);
	}

	/** Number of rows. */
	public int rowCount () {
		return rows;
	}

	/** Number of columns. */
	public int columnCount () {
		return columns;
	}

	/** Answers whether the lanes hold {@link M3ShortLabel} values. */
	public boolean immediates () {
		return immediates;
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
		for (int c = 0; c < columns; c++) {
			if (immediates) {
				System.arraycopy (labels [c], row, labels [c], row + count, rows - row);
				Arrays.fill (labels [c], row, row + count, M3ShortLabel.EMPTY);
			} else {
				System.arraycopy (ids [c], row, ids [c], row + count, rows - row);
				Arrays.fill (ids [c], row, row + count, M3LabelPool.EMPTY);
			}
		}
		rows += count;
	}

	/** Removes the rows {@code [row, row + count)} and releases their labels. */
	public void removeRows (int row, int count) {
		Objects.checkFromIndexSize (row, count, rows);
		for (int c = 0; c < columns; c++) {
			for (int i = row; i < row + count; i++) release (get (c, i));
			if (immediates) System.arraycopy (labels [c], row + count, labels [c], row, rows - row - count);
			else System.arraycopy (ids [c], row + count, ids [c], row, rows - row - count);
		}
		rows -= count;
	}

	/**
	 * Sets the text of a cell. A short text in immediate lanes is packed into the
	 * cell; any other text is interned (one reference). The old label is
	 * released afterwards, so setting the same text again changes nothing.
	 */
	public void setText (int row, int column, String text) {
		check (row, column);
		long label = immediates ? M3ShortLabel.pack (text) : M3ShortLabel.NONE;
		if (label == M3ShortLabel.NONE) label = pool.intern (text);
		release (get (column, row));
		put (column, row, label);
	}

	/** The label of a cell: a pool id or, in immediate lanes, possibly an immediate value. */
	public long label (int row, int column) {
		check (row, column);
		return get (column, row);
	}

	/** The pool id of a cell; refused for a cell that holds an immediate label. */
	public int labelId (int row, int column) {
		return M3ShortLabel.poolId (label (row, column));
	}

	/** Text of a cell. Pooled labels answer their String facade; immediates a new String. */
	public String text (int row, int column) {
		return text (label (row, column));
	}

	/** Text of {@code label} of this model. */
	public String text (long label) {
		return M3ShortLabel.isImmediate (label) ? M3ShortLabel.toString (label) : pool.text (M3ShortLabel.poolId (label));
	}

	/** Number of characters of a cell's text. */
	public int length (int row, int column) {
		long label = label (row, column);
		return M3ShortLabel.isImmediate (label) ? M3ShortLabel.length (label) : pool.length ((int) label);
	}

	/**
	 * Copies the text of a cell into {@code dst} at {@code offset} and returns its
	 * length, creating no String: the path for native text callbacks that fill a
	 * caller's character buffer.
	 */
	public int getChars (int row, int column, char [] dst, int offset) {
		return getChars (label (row, column), dst, offset);
	}

	/** Copies the text of {@code label} of this model into {@code dst}; returns its length. */
	public int getChars (long label, char [] dst, int offset) {
		return M3ShortLabel.isImmediate (label) ? M3ShortLabel.getChars (label, dst, offset) : pool.getChars (M3ShortLabel.poolId (label), dst, offset);
	}

	/** Extent of a cell's label in {@code fontId}, measured once per distinct label. */
	public long extent (int row, int column, int fontId, M3TextExtentCache.Measurer measurer) {
		return extents.extent (label (row, column), fontId, measurer);
	}

	/** Width of the widest label of {@code column} among rows {@code [from, to)}. */
	public int widest (int column, int from, int to, int fontId, M3TextExtentCache.Measurer measurer) {
		Objects.checkIndex (column, columns);
		Objects.checkFromToIndex (from, to, rows);
		return immediates ? extents.widest (labels [column], from, to, fontId, measurer) : extents.widest (ids [column], from, to, fontId, measurer);
	}

	/** Number of cells of {@code column} that carry an immediate label. */
	public int immediateCells (int column) {
		Objects.checkIndex (column, columns);
		int count = 0;
		for (int r = 0; r < rows && immediates; r++) {
			if (M3ShortLabel.isImmediate (labels [column] [r])) count++;
		}
		return count;
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
		long lanes = 16L + columns * (16L + (long) capacity * (immediates ? 8 : 4));
		return lanes + pool.retainedBytes () + extents.retainedBytes ();
	}

	private long get (int column, int row) {
		return immediates ? labels [column] [row] : ids [column] [row];
	}

	private void put (int column, int row, long label) {
		if (immediates) labels [column] [row] = label; else ids [column] [row] = (int) label;
	}

	private void release (long label) {
		if (!M3ShortLabel.isImmediate (label)) pool.release ((int) label);
	}

	private void check (int row, int column) {
		Objects.checkIndex (column, columns);
		Objects.checkIndex (row, rows);
	}

	private void ensureCapacity (int needed) {
		if (needed <= capacity) return;
		capacity = Math.max (needed, capacity * 2);
		for (int c = 0; c < columns; c++) {
			if (immediates) labels [c] = Arrays.copyOf (labels [c], capacity);
			else ids [c] = Arrays.copyOf (ids [c], capacity);
		}
	}
}
