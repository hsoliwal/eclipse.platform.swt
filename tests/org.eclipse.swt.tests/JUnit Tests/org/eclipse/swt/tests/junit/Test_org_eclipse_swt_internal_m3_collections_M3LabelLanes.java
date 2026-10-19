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
package org.eclipse.swt.tests.junit;

import org.eclipse.swt.internal.m3.collections.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Pool-on versus pool-off differential of {@link M3LabelLanes}: the same seeded
 * table script runs against plain String lanes measured on every call and
 * against the label lanes; every answer must be equal.
 */
public class Test_org_eclipse_swt_internal_m3_collections_M3LabelLanes {
	private static final String [] TYPES = {"JAVA File", "File folder", "TSV File", "अनु File", ""};
	private int measured;

	private long measure (String text, int font) {
		measured++;
		int width = 0;
		for (int i = 0; i < text.length (); i++) width += 5 + (text.charAt (i) % 7) + font;
		return M3TextExtentCache.pack (width, 12 + font);
	}

	@Test
	void seededDifferentialPoolOnVersusPoolOff () {
		for (int seed = 1; seed <= 16; seed++) {
			Random random = new Random (seed);
			M3LabelPool pool = new M3LabelPool (seed % 3 * 4);
			M3LabelLanes on = new M3LabelLanes (2, pool, new M3TextExtentCache (1 + seed % 5 * 8));
			M3TextExtentCache.Measurer byId = (label, font) -> measure (pool.text (label), font);
			List<String []> off = new ArrayList<> ();
			for (int step = 0; step < 3000; step++) {
				int op = random.nextInt (10), rows = off.size ();
				if (op == 0) {
					int at = random.nextInt (rows + 1), count = random.nextInt (4);
					on.insertRows (at, count);
					for (int i = 0; i < count; i++) off.add (at, new String [] {"", ""});
				} else if (op == 1 && rows > 0) {
					int at = random.nextInt (rows), count = random.nextInt (Math.min (3, rows - at) + 1);
					on.removeRows (at, count);
					off.subList (at, at + count).clear ();
				} else if (op <= 6 && rows > 0) {
					int row = random.nextInt (rows), column = random.nextInt (2);
					String text = column == 0 ? TYPES [random.nextInt (TYPES.length)] : "row " + random.nextInt (50);
					on.setText (row, column, text);
					off.get (row) [column] = text;
				} else if (op == 7 && rows > 0) {
					int column = random.nextInt (2), font = random.nextInt (3), from = random.nextInt (rows), to = from + random.nextInt (rows - from + 1);
					int expected = 0;
					for (int r = from; r < to; r++) expected = Math.max (expected, M3TextExtentCache.width (measure (off.get (r) [column], font)));
					assertEquals (expected, on.widest (column, from, to, font, byId));
				} else if (op == 8 && rows > 0) {
					int row = random.nextInt (rows), column = random.nextInt (2), font = random.nextInt (3);
					assertEquals (measure (off.get (row) [column], font), on.extent (row, column, font, byId));
				} else if (op == 9) {
					on.setRowCount (random.nextInt (rows + 3));
					while (off.size () > on.rowCount ()) off.remove (off.size () - 1);
					while (off.size () < on.rowCount ()) off.add (new String [] {"", ""});
				}
				assertEquals (off.size (), on.rowCount ());
				for (int r = 0; r < off.size (); r++) {
					for (int c = 0; c < 2; c++) assertEquals (off.get (r) [c], on.text (r, c), "seed " + seed + " step " + step);
				}
			}
		}
	}

	@Test
	void repeatedLabelsShareIdsAndMeasurements () {
		M3LabelPool pool = new M3LabelPool ();
		M3TextExtentCache extents = new M3TextExtentCache (64);
		M3LabelLanes lanes = new M3LabelLanes (1, pool, extents);
		lanes.setRowCount (10_000);
		for (int r = 0; r < 10_000; r++) lanes.setText (r, 0, TYPES [r % 4]);
		assertEquals (4, pool.size ());
		assertEquals (lanes.labelId (0, 0), lanes.labelId (4, 0));
		assertSame (lanes.text (0, 0), lanes.text (8, 0));
		measured = 0;
		int widest = lanes.widest (0, 0, 10_000, 0, (label, font) -> measure (pool.text (label), font));
		assertEquals (4, measured);
		assertTrue (widest > 0);
		assertEquals (1, lanes.columnCount ());
		assertSame (pool, lanes.pool ());
		assertSame (extents, lanes.extents ());
		assertTrue (lanes.retainedBytes () > 10_000 * 4);
		lanes.setRowCount (0);
		assertEquals (4, pool.cachedCount ());
		pool.clear ();
	}

	@Test
	void evictedLabelsLeaveTheExtentCache () {
		M3LabelPool pool = new M3LabelPool (0);
		M3TextExtentCache extents = new M3TextExtentCache (8);
		M3LabelLanes lanes = new M3LabelLanes (1, pool, extents);
		lanes.setRowCount (1);
		lanes.setText (0, 0, "first");
		int first = lanes.labelId (0, 0);
		lanes.extent (0, 0, 1, (label, font) -> measure (pool.text (label), font));
		assertNotEquals (-1L, extents.peek (first, 1));
		lanes.setText (0, 0, "second");
		assertEquals (-1L, extents.peek (first, 1));
	}

	@Test
	void refusesInvalidArguments () {
		M3LabelPool pool = new M3LabelPool ();
		M3TextExtentCache extents = new M3TextExtentCache (8);
		assertThrows (IllegalArgumentException.class, () -> new M3LabelLanes (0, pool, extents));
		assertThrows (NullPointerException.class, () -> new M3LabelLanes (1, null, extents));
		assertThrows (NullPointerException.class, () -> new M3LabelLanes (1, pool, null));
		M3LabelLanes lanes = new M3LabelLanes (2, pool, extents);
		assertThrows (IllegalArgumentException.class, () -> lanes.setRowCount (-1));
		assertThrows (IllegalArgumentException.class, () -> lanes.insertRows (0, -1));
		assertThrows (IndexOutOfBoundsException.class, () -> lanes.insertRows (1, 1));
		assertThrows (IndexOutOfBoundsException.class, () -> lanes.removeRows (0, 1));
		assertThrows (IndexOutOfBoundsException.class, () -> lanes.labelId (0, 0));
		lanes.setRowCount (1);
		assertThrows (IndexOutOfBoundsException.class, () -> lanes.labelId (0, 2));
		assertThrows (IndexOutOfBoundsException.class, () -> lanes.widest (2, 0, 1, 0, (l, f) -> 0L));
		assertThrows (IndexOutOfBoundsException.class, () -> lanes.widest (0, 0, 2, 0, (l, f) -> 0L));
		assertThrows (NullPointerException.class, () -> lanes.setText (0, 0, null));
		assertEquals ("", lanes.text (0, 1));
	}
}
