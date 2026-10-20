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

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * The eager cell values of a patch (public model API, brief rule 26): one entry per written value in parallel lanes
 * (row id, operation, column, 64 value bits, object for texts and images), staged by a builder's writer and frozen
 * sorted by id, so a model replays the values of one row with a binary search and a short scan. Texts are copied to a
 * <code>String</code> when staged, because a <code>CharSequence</code> may change after the call.
 */
final class M3PatchValues {

	static final byte TEXT = 0, LABEL = 1, INT = 2, LONG = 3, DOUBLE = 4, IMAGE = 5, FLAG = 6, STYLE = 7, EXTENT = 8;

	long [] ids = new long [16];
	byte [] ops = new byte [16];
	int [] columns = new int [16];
	long [] bits = new long [16];
	Object [] objects = new Object [16];
	int count;

	/** The writer of a builder: appends the values of the row named by {@link #id}. */
	static final class Stage implements M3CellWriter {
		final M3PatchValues values = new M3PatchValues ();
		long id;

		private M3CellWriter add (byte op, int column, long value, Object object) {
			values.add (id, op, column, value, object);
			return this;
		}

		@Override
		public M3CellWriter text (int column, CharSequence text) {
			return add (TEXT, column, 0, text == null ? null : text.toString ());
		}

		@Override
		public M3CellWriter label (int column, int labelId) {
			return add (LABEL, column, labelId, null);
		}

		@Override
		public M3CellWriter intValue (int column, int value) {
			return add (INT, column, value, null);
		}

		@Override
		public M3CellWriter longValue (int column, long value) {
			return add (LONG, column, value, null);
		}

		@Override
		public M3CellWriter doubleValue (int column, double value) {
			return add (DOUBLE, column, Double.doubleToRawLongBits (value), null);
		}

		@Override
		public M3CellWriter image (int column, Image image) {
			return add (IMAGE, column, 0, image);
		}

		@Override
		public M3CellWriter flag (int flag, boolean value) {
			if (flag < 0 || flag > 63) SWT.error (SWT.ERROR_INVALID_RANGE);
			return add (FLAG, flag, value ? 1 : 0, null);
		}

		@Override
		public M3CellWriter style (int column, int styleId) {
			return add (STYLE, column, styleId, null);
		}

		@Override
		public M3CellWriter extent (int extent) {
			if (extent < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			return add (EXTENT, -1, extent, null);
		}
	}

	void add (long id, byte op, int column, long value, Object object) {
		if (column < -1) SWT.error (SWT.ERROR_INVALID_RANGE);
		if (count == ids.length) {
			int capacity = count * 2;
			ids = Arrays.copyOf (ids, capacity);
			ops = Arrays.copyOf (ops, capacity);
			columns = Arrays.copyOf (columns, capacity);
			bits = Arrays.copyOf (bits, capacity);
			objects = Arrays.copyOf (objects, capacity);
		}
		ids [count] = id;
		ops [count] = op;
		columns [count] = column;
		bits [count] = value;
		objects [count++] = object;
	}

	void clear () {
		Arrays.fill (objects, 0, count, null);
		count = 0;
	}

	/* a copy sorted by id, the entries of one id in the order they were written (stable merge sort of indices) */
	M3PatchValues freeze () {
		int [] order = new int [count], work = new int [count];
		for (int i = 0; i < count; i++) order [i] = i;
		sort (order, work, 0, count);
		M3PatchValues frozen = new M3PatchValues ();
		frozen.ids = new long [count];
		frozen.ops = new byte [count];
		frozen.columns = new int [count];
		frozen.bits = new long [count];
		frozen.objects = new Object [count];
		for (int i = 0; i < count; i++) {
			int k = order [i];
			frozen.ids [i] = ids [k];
			frozen.ops [i] = ops [k];
			frozen.columns [i] = columns [k];
			frozen.bits [i] = bits [k];
			frozen.objects [i] = objects [k];
		}
		frozen.count = count;
		return frozen;
	}

	private void sort (int [] order, int [] work, int from, int to) {
		if (to - from < 2) return;
		int middle = (from + to) >>> 1;
		sort (order, work, from, middle);
		sort (order, work, middle, to);
		if (ids [order [middle - 1]] <= ids [order [middle]]) return;
		System.arraycopy (order, from, work, from, to - from);
		for (int i = from, a = from, b = middle; i < to; i++) {
			order [i] = b >= to || a < middle && ids [work [a]] <= ids [work [b]] ? work [a++] : work [b++];
		}
	}

	/* the first entry of an id in a frozen copy, or -1 */
	int first (long id) {
		int low = 0, high = count;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (ids [middle] < id) low = middle + 1; else high = middle;
		}
		return low < count && ids [low] == id ? low : -1;
	}

	void replay (long id, M3CellWriter out) {
		for (int i = first (id); i >= 0 && i < count && ids [i] == id; i++) {
			int column = columns [i];
			long value = bits [i];
			switch (ops [i]) {
				case TEXT -> out.text (column, (CharSequence) objects [i]);
				case LABEL -> out.label (column, (int) value);
				case INT -> out.intValue (column, (int) value);
				case LONG -> out.longValue (column, value);
				case DOUBLE -> out.doubleValue (column, Double.longBitsToDouble (value));
				case IMAGE -> out.image (column, (Image) objects [i]);
				case FLAG -> out.flag (column, value != 0);
				case STYLE -> out.style (column, (int) value);
				default -> out.extent ((int) value);
			}
		}
	}
}
