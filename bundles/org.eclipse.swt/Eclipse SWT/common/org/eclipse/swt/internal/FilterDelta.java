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
package org.eclipse.swt.internal;

/*
 * Reports the change of an ColumnFilter from its old and new kept masks, word by word: the
 * structural change of the source first (RESET, INSERTED with the inserted rows that pass,
 * REMOVED), then the rows that left (old & ~new), the changed rows that stayed (UPDATED), and
 * the rows that entered (new & ~old, outside an inserted range). UPDATED comes before ENTERED so
 * that a sorted view re-places its changed rows before it binary-searches the entering ones.
 * Damage is in view rows (source order), so a row's view position is its rank in the new mask.
 */
final class FilterDelta {

	private final ColumnFilter filter;
	private final RowChange change;

	FilterDelta (ColumnFilter filter) {
		this.filter = filter;
		this.change = filter.change;
	}

	void report (RowChange in, long [] previous, long [] kept, int n) {
		if (!filter.hasListeners ()) return;
		int kind = in == null ? 0 : in.kind;
		int words = RowMask.words (n), last = filter.size () - 1;
		if (kind == RowChange.RESET) {
			change.set (RowChange.RESET, 0, 0);
			change.damage (0, last);
			filter.fire ();
			return;
		}
		int skipStart = 0, skipEnd = 0;
		if (kind == RowChange.INSERTED || kind == RowChange.REMOVED) {
			structural (in, kept);
			if (kind == RowChange.INSERTED) {
				skipStart = in.start;
				skipEnd = in.start + in.count;
			}
		}
		emit (RowChange.LEFT, previous, kept, words, 0, 0);
		if (kind == RowChange.UPDATED) updated (in, previous, kept);
		emit (RowChange.ENTERED, kept, previous, words, skipStart, skipEnd);
	}

	/* the INSERTED or REMOVED event of a source edit: the inserted rows that pass, damage from the edit to the end */
	void structural (RowChange in, long [] kept) {
		if (!filter.hasListeners ()) return;
		change.set (in.kind, in.start, in.count);
		if (in.kind == RowChange.INSERTED) {
			for (int row = in.start; row < in.start + in.count; row++) {
				if (RowMask.get (kept, row)) change.add (row);
			}
		}
		change.damage (filter.rankBefore (in.start), filter.size () - 1);
		filter.fire ();
	}

	/* the change of an UPDATED edit: the kept flips (ascending, ~row for a row that left) and the edited rows that stayed */
	void changedRows (RowChange in, int [] flips, int count, long [] previous, long [] kept) {
		if (!filter.hasListeners ()) return;
		emitFlips (RowChange.LEFT, flips, count, false);
		updated (in, previous, kept);
		emitFlips (RowChange.ENTERED, flips, count, true);
	}

	/* fires the flips that left (or entered), if any (as emit, without the scan) */
	private void emitFlips (int kind, int [] flips, int count, boolean entering) {
		int first = -1;
		for (int f = 0; f < count; f++) {
			if (flips [f] >= 0 != entering) continue;
			int row = entering ? flips [f] : ~flips [f];
			if (first < 0) {
				first = row;
				change.set (kind, 0, 0);
			}
			change.add (row);
		}
		if (first < 0) return;
		change.damage (filter.rankBefore (first), filter.size () - 1);
		filter.fire ();
	}

	/* fires the rows of a & ~b outside [skipStart, skipEnd), if any */
	private void emit (int kind, long [] a, long [] b, int words, int skipStart, int skipEnd) {
		int first = -1;
		for (int w = 0; w < words; w++) {
			for (long x = a [w] & ~b [w]; x != 0; x &= x - 1) {
				int row = w << 6 | Long.numberOfTrailingZeros (x);
				if (row >= skipStart && row < skipEnd) continue;
				if (first < 0) {
					first = row;
					change.set (kind, 0, 0);
				}
				change.add (row);
			}
		}
		if (first < 0) return;
		change.damage (filter.rankBefore (first), filter.size () - 1);
		filter.fire ();
	}

	void updated (RowChange in, long [] previous, long [] kept) {
		boolean any = false;
		for (int i = 0; i < in.rowCount; i++) {
			int row = in.rows [i];
			if (RowMask.get (kept, row) && RowMask.get (previous, row)) {
				if (!any) change.set (RowChange.UPDATED, 0, 0);
				any = true;
				change.add (row);
				int position = filter.rankBefore (row);
				change.damage (position, position);
			}
		}
		if (any) filter.fire ();
	}
}
