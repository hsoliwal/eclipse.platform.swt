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

import java.lang.reflect.Field;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene;

/**
 * The HIDDEN script of the Table differential (brief rule 24, hidden viewports; user 2026-10-20: "if the viewport
 * becomes visible from hidden it should paint; while it is hidden the model may make changes and the viewport is dirty
 * and renders on becoming visible"). Each step selects rows and scrolls, hides the Table (not visible, or a zero size),
 * applies 250 model deltas (40 steps: 10^4 per seed), shows it again and answers its rows; the probes record count,
 * top, scroll bars, selection, focus, events and pixels. The deltas go through the item API followed by
 * <code>setTopIndex</code> of the old top row or of the first row after it that survives (the contract of the model
 * path), in this SWT and the official jar. TableSpliceTests in 'Eclipse SWT Tests/win32' runs the hidden deltas
 * through RowModel and checks the win32 viewport's counters: no native row or state operation, no whole-list rebuild,
 * no facade and no paint while hidden, and exactly one paint pass after showing.
 */
final class TableHiddenScripts {
	static final int DELTAS = 250;
	private TableHiddenScripts () {
	}

	static String hidden (Scene scene, int kind, int fraction, int pick, int span, int delta) {
		Table table = scene.table;
		int count = table.getItemCount ();
		if (count > 0) {
			table.setSelection (new int [] {TableViewportScripts.row (table, fraction), TableViewportScripts.row (table, (fraction * 7 + pick) & 0xFFFFF)});
			table.setTopIndex (TableViewportScripts.row (table, (fraction * 3 + span) & 0xFFFFF));
		}
		ViewportDifferential.settle (scene.display, 30);
		boolean zero = (pick & 1) != 0;
		Rectangle bounds = table.getBounds ();
		if (zero) table.setSize (0, 0); else table.setVisible (false);
		ViewportDifferential.settle (scene.display, 30);
		Random random = new Random (((long) fraction << 16) ^ pick ^ ((long) kind << 40));
		int applied = 0;
		for (int i = 0; i < DELTAS; i++) {
			int rows = table.getItemCount ();
			int at = rows == 0 ? 0 : random.nextInt (rows);
			int top = table.getTopIndex ();
			if (rows < 50 || random.nextBoolean ()) {
				long [] ids = new long [1 + random.nextInt (3)];
				for (int k = 0; k < ids.length; k++) ids [k] = scene.nextId++;
				apply (table, at, 0, ids, top);
			} else {
				int removed = Math.min (1 + random.nextInt (3), rows - at);
				if (removed == rows) continue;
				apply (table, at, removed, new long [0], top);
			}
			applied++;
		}
		ViewportDifferential.settle (scene.display, 30);
		if (zero) table.setBounds (bounds); else table.setVisible (true);
		ViewportDifferential.settle (scene.display, 150);
		return "hidden " + (zero ? "zero size" : "not visible") + ": " + applied + " deltas -> rows " + table.getItemCount () + " top " + table.getTopIndex ();
	}

	/* the contract of the model path through the item API */
	static void apply (Table table, int at, int removed, long [] ids, int top) {
		if (removed > 0) table.remove (at, at + removed - 1);
		for (int k = 0; k < ids.length; k++) new TableItem (table, SWT.NONE, at + k).setText ("row " + ids [k]);
		int anchor = top < at ? top : top >= at + removed ? top - removed + ids.length : at + ids.length;
		int count = table.getItemCount ();
		if (count > 0) table.setTopIndex (Math.min (anchor, count - 1));
	}

	/* native row and state operations, whole-list rebuilds, facades made and rows measured of the win32 viewport; null without it */
	static long [] counters (Table table) {
		try {
			Field viewportField = Table.class.getDeclaredField ("viewport");
			viewportField.setAccessible (true);
			Object viewport = viewportField.get (table);
			if (viewport == null) return null;
			Field made = viewport.getClass ().getDeclaredField ("facadesMade");
			made.setAccessible (true);
			long [] counters = new long [5];
			String [] names = {"m3RowOps", "m3StateOps", "m3FullRebuilds"};
			for (int i = 0; i < names.length; i++) {
				Field field = Table.class.getDeclaredField (names [i]);
				field.setAccessible (true);
				counters [i] = field.getLong (table);
			}
			counters [3] = made.getLong (viewport);
			Field measured = viewport.getClass ().getDeclaredField ("rowsMeasured");
			measured.setAccessible (true);
			counters [4] = measured.getLong (viewport);
			return counters;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
