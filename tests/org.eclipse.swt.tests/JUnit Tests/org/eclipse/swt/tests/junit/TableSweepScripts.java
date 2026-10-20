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

import java.util.*;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene;

/**
 * The SWEEP script of the Table differential (brief rule 24, viewport deltas; user 2026-10-20: "resize and layout
 * change the port"). The first 50 steps resize the Table (25 steps of one pixel, width and height in turn, then 25
 * large jumps); the next 50 change its columns (a width by one pixel, a width jump, a hidden column, a new order,
 * hidden columns shown again). The first step scrolls to the middle rows and selects one, so the sweep runs there; the
 * probes record count, top, both bars, selection, focus, events and pixels like any step. With the win32 viewport each step
 * also checks the counters (TableHiddenScripts.counters): no whole-list rebuild, no native row operation, no facade
 * and no measured row (Scene.hiddenViolations).
 */
final class TableSweepScripts {
	static final int RESIZES = 50;

	private TableSweepScripts () {
	}

	static String sweep (Scene scene, int kind, int fraction, int pick, int span, int delta) {
		Table table = scene.table;
		int step = scene.sweepStep++;
		if (step == 0 && table.getItemCount () > 0) {
			table.setTopIndex (table.getItemCount () / 2);
			table.setSelection (table.getItemCount () / 2 + 2);
			ViewportDifferential.settle (scene.display, 60);
		}
		long [] before = TableHiddenScripts.counters (table);
		String answer = step < RESIZES ? resize (table, step, fraction, pick) : columns (table, step, fraction, pick);
		ViewportDifferential.settle (scene.display, 60);
		long [] after = TableHiddenScripts.counters (table);
		if (before != null && !Arrays.equals (before, after)) {
			scene.hiddenViolations.add ("seed " + scene.seed + " sweep step " + step + " (" + answer + "): counters " + Arrays.toString (before) + " -> " + Arrays.toString (after));
		}
		return answer + " -> top " + table.getTopIndex ();
	}

	static String resize (Table table, int step, int fraction, int pick) {
		Rectangle bounds = table.getBounds ();
		if (step < RESIZES / 2) {
			int by = (step & 2) == 0 ? 1 : -1;
			if ((step & 1) == 0) table.setSize (bounds.width + by, bounds.height); else table.setSize (bounds.width, bounds.height + by);
		} else {
			table.setSize (60 + fraction % 300, 40 + pick % 340);
		}
		return "resize " + table.getSize ().x + " x " + table.getSize ().y;
	}

	static String columns (Table table, int step, int fraction, int pick) {
		int count = table.getColumnCount ();
		if (count == 0) return "no columns";
		TableColumn column = table.getColumn (pick % count);
		switch (step % 5) {
			case 0 -> {
				column.setWidth (column.getWidth () + ((pick & 1) == 0 ? 1 : -1));
				return "column " + (pick % count) + " width " + column.getWidth ();
			}
			case 1 -> {
				column.setWidth (20 + fraction % 300);
				return "column " + (pick % count) + " width " + column.getWidth ();
			}
			case 2 -> {
				column.setWidth (0);
				return "column " + (pick % count) + " hidden";
			}
			case 3 -> {
				int [] order = table.getColumnOrder ();
				Random random = new Random (fraction);
				for (int k = order.length - 1; k > 0; k--) {
					int j = random.nextInt (k + 1), swap = order [k];
					order [k] = order [j];
					order [j] = swap;
				}
				table.setColumnOrder (order);
				return "order " + Arrays.toString (order);
			}
			default -> {
				for (TableColumn shown : table.getColumns ()) {
					if (shown.getWidth () == 0) shown.setWidth (90);
				}
				return "columns shown";
			}
		}
	}
}
