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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 24, viewport deltas (user 2026-10-20: "resize and layout change the port"): a resize and a column
 * resize, move or hide are deltas of the viewport of a plain Table of 10^5 rows scrolled to its middle. Gate: a resize
 * sweep (50 sizes, steps of one pixel and large jumps) and a column sweep (50 widths, orders and hidden columns) make
 * no whole-list rebuild, no native row operation (an owner-data ListView has no native row to create or destroy), no
 * facade (rows are painted from the reused paint facades) and measure no row. Counters: Table.m3FullRebuilds,
 * Table.m3RowOps (TableSplice.count), TableViewport.facadesMade and TableViewport.rowsMeasured. Runs where the Table
 * has the win32 viewport; elsewhere it is skipped.
 */
public class Test_org_eclipse_swt_widgets_Table_ViewportDeltas {
	static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Table_ViewportDeltas.class.getName ());
	static final int ROWS = 100_000, SIZES = 50;

	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 700, 600);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	static Object field (Object owner, Class<?> type, String name) {
		try {
			Field field = type.getDeclaredField (name);
			field.setAccessible (true);
			return field.get (owner);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/* full rebuilds, native row operations, facades made, rows measured */
	static long [] counters (Table table) {
		Object viewport = field (table, Table.class, "viewport");
		return new long [] {(Long) field (table, Table.class, "m3FullRebuilds"), (Long) field (table, Table.class, "m3RowOps"),
				(Long) field (viewport, viewport.getClass (), "facadesMade"), (Long) field (viewport, viewport.getClass (), "rowsMeasured")};
	}

	private Table table (int columns) {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds (5, 5, 400, 380);
		Assumptions.assumeTrue (field (table, Table.class, "viewport") != null, "the viewport of a plain Table is win32 only");
		if (columns > 0) table.setHeaderVisible (true);
		for (int c = 0; c < columns; c++) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText ("column " + c);
			column.setWidth (90);
		}
		table.setItemCount (ROWS);
		table.setTopIndex (ROWS / 2);
		int top = table.getTopIndex ();
		for (int row = top - 40; row < top + 80; row++) {
			TableItem item = table.getItem (row);
			for (int c = 0; c < Math.max (1, columns); c++) item.setText (c, "row " + row + " cell " + c);
		}
		table.setSelection (top + 2);
		settle ();
		return table;
	}

	private void settle () {
		for (int i = 0; i < 3; i++) {
			while (display.readAndDispatch ()) {
			}
		}
	}

	private static void assertDelta (long [] before, long [] after, String what) {
		assertEquals (before [0], after [0], what + ": whole-list rebuilds");
		assertEquals (before [1], after [1], what + ": native row operations");
		assertEquals (before [2], after [2], what + ": facades made");
		assertEquals (before [3], after [3], what + ": rows measured");
	}

	/* 25 steps of one pixel (width and height in turn) and 25 large jumps, seeded */
	private void resizeSweep (Table table, String name) {
		Random random = new Random (0x5123);
		long [] start = counters (table);
		int top = table.getTopIndex ();
		for (int i = 0; i < SIZES; i++) {
			Rectangle bounds = table.getBounds ();
			if (i < SIZES / 2) {
				int step = (i & 2) == 0 ? 1 : -1;
				if ((i & 1) == 0) table.setSize (bounds.width + step, bounds.height); else table.setSize (bounds.width, bounds.height + step);
			} else {
				table.setSize (60 + random.nextInt (600), 40 + random.nextInt (520));
			}
			settle ();
			assertDelta (start, counters (table), name + " resize " + i + " to " + table.getSize ());
		}
		LOG.info (() -> name + ": " + SIZES + " sizes, top " + top + " -> " + table.getTopIndex () + ", counters " + Arrays.toString (counters (table)));
	}

	@Test
	public void resizeSweepIsADeltaOfTheViewport () {
		resizeSweep (table (2), "two columns");
	}

	@Test
	public void resizeSweepOfAColumnlessTableIsADeltaOfTheViewport () {
		resizeSweep (table (0), "column-less");
	}

	@Test
	public void columnSweepIsADeltaOfTheViewport () {
		Table table = table (4);
		Random random = new Random (0xC01);
		long [] start = counters (table);
		for (int i = 0; i < SIZES; i++) {
			TableColumn column = table.getColumn (random.nextInt (4));
			String what;
			switch (i % 5) {
				case 0 -> {
					column.setWidth (column.getWidth () + ((i & 1) == 0 ? 1 : -1));
					what = "width by one pixel";
				}
				case 1 -> {
					column.setWidth (20 + random.nextInt (300));
					what = "width jump";
				}
				case 2 -> {
					column.setWidth (0);
					what = "hide";
				}
				case 3 -> {
					int [] order = table.getColumnOrder ();
					for (int k = order.length - 1; k > 0; k--) {
						int j = random.nextInt (k + 1), swap = order [k];
						order [k] = order [j];
						order [j] = swap;
					}
					table.setColumnOrder (order);
					what = "order " + Arrays.toString (order);
				}
				default -> {
					for (TableColumn shown : table.getColumns ()) {
						if (shown.getWidth () == 0) shown.setWidth (90);
					}
					what = "show";
				}
			}
			settle ();
			assertDelta (start, counters (table), "column step " + i + " (" + what + ")");
		}
		LOG.info (() -> "columns: " + SIZES + " steps, counters " + Arrays.toString (counters (table)));
	}
}
