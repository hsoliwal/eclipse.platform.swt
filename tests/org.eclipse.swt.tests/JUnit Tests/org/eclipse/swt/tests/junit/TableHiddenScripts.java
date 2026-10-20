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
import org.eclipse.swt.m3.M3Patch;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene;

/**
 * The HIDDEN script of the Table differential (brief rule 24, hidden viewports; user 2026-10-20: "if the viewport
 * becomes visible from hidden it should paint; while it is hidden the model may make changes and the viewport is dirty
 * and renders on becoming visible"). Each step selects rows and scrolls, hides the Table (not visible, or a zero size),
 * applies 250 model deltas (40 steps: 10^4 per seed), shows it again, puts the model's top row on top (a stock Table
 * that is not visible does not end on the top row setTopIndex asked for while it was hidden) and answers its rows; the probes record count,
 * top, scroll bars, selection, focus, events and pixels. The deltas go through M3Model where this SWT has it (the
 * default and original modes); the official jar has none, so the same runs go through the item API followed by
 * <code>setTopIndex</code> of the old top row or of the first row after it that survives, which is M3Model's contract.
 * With the win32 viewport the step also checks the counters: no native row or state operation, no whole-list rebuild,
 * no facade and no paint while hidden, and exactly one paint pass after showing (Scene.hiddenViolations).
 */
final class TableHiddenScripts {
	static final int DELTAS = 250;
	/* M3Model is this SWT's (the official jar has none): the model path is linked only where it exists */
	static final boolean MODEL = present ("org.eclipse.swt.widgets.M3Model");

	private TableHiddenScripts () {
	}

	static boolean present (String name) {
		try {
			Class.forName (name);
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
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
		/* the row on top, moved by every delta as M3Model keeps it (the top row, or the first row after it that survives) */
		int anchor = table.getTopIndex ();
		Rectangle bounds = table.getBounds ();
		if (zero) table.setSize (0, 0); else table.setVisible (false);
		ViewportDifferential.settle (scene.display, 30);
		int [] paints = new int [1];
		Listener paint = event -> paints [0]++;
		table.addListener (SWT.Paint, paint);
		long [] before = counters (table);
		Random random = new Random (((long) fraction << 16) ^ pick ^ ((long) kind << 40));
		int applied = 0;
		for (int i = 0; i < DELTAS; i++) {
			int rows = table.getItemCount ();
			int at = rows == 0 ? 0 : random.nextInt (rows);
			if (rows < 50 || random.nextBoolean ()) {
				long [] ids = new long [1 + random.nextInt (3)];
				for (int k = 0; k < ids.length; k++) ids [k] = scene.nextId++;
				apply (table, at, 0, ids, anchor);
				anchor = anchored (anchor, at, 0, ids.length, rows + ids.length);
			} else {
				int removed = Math.min (1 + random.nextInt (3), rows - at);
				if (removed == rows) continue;
				apply (table, at, removed, new long [0], anchor);
				anchor = anchored (anchor, at, removed, 0, rows - removed);
			}
			applied++;
		}
		ViewportDifferential.settle (scene.display, 30);
		long [] hidden = counters (table);
		int hiddenPaints = paints [0];
		if (zero) table.setBounds (bounds); else table.setVisible (true);
		ViewportDifferential.settle (scene.display, 150);
		table.removeListener (SWT.Paint, paint);
		/* a stock Table that is not visible loses the top rows setTopIndex asked for; the step puts the model's top row on top, visible */
		if (table.getItemCount () > 0) table.setTopIndex (anchor);
		ViewportDifferential.settle (scene.display, 30);
		if (before != null) {
			String step = "seed " + scene.seed + " step " + scene.counted + " (" + (zero ? "zero size" : "not visible") + ")";
			if (!Arrays.equals (before, hidden)) scene.hiddenViolations.add (step + ": counters while hidden " + Arrays.toString (before) + " -> " + Arrays.toString (hidden));
			if (hiddenPaints != 0) scene.hiddenViolations.add (step + ": " + hiddenPaints + " paints while hidden");
			if (paints [0] - hiddenPaints != 1) scene.hiddenViolations.add (step + ": " + (paints [0] - hiddenPaints) + " paint passes after showing");
		}
		return "hidden " + (zero ? "zero size" : "not visible") + ": " + applied + " deltas -> rows " + table.getItemCount () + " top " + table.getTopIndex ();
	}

	/* the run through M3Model where it exists; otherwise its contract through the item API */
	static void apply (Table table, int at, int removed, long [] ids, int top) {
		if (MODEL) {
			ModelPath.apply (table, at, removed, ids);
			return;
		}
		if (removed > 0) table.remove (at, at + removed - 1);
		for (int k = 0; k < ids.length; k++) new TableItem (table, SWT.NONE, at + k).setText ("row " + ids [k]);
		if (table.getItemCount () > 0) table.setTopIndex (anchored (top, at, removed, ids.length, table.getItemCount ()));
	}

	/* the row the top row has after a run, or the first row after it that survives, within the rows */
	static int anchored (int top, int at, int removed, int inserted, int count) {
		int row = top < at ? top : top >= at + removed ? top - removed + inserted : at + inserted;
		return Math.max (0, Math.min (row, count - 1));
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

	/** Linked only where M3Model exists. */
	static final class ModelPath {
		private ModelPath () {
		}

		static void apply (Table table, int at, int removed, long [] ids) {
			M3Patch.Builder builder = M3Patch.builder (M3Model.ROOT);
			if (removed > 0) builder.remove (at, removed);
			if (ids.length > 0) builder.insert (at, ids);
			M3Model.of (table).apply (builder.build (), (id, row) -> row.setText (0, "row " + id));
		}
	}
}
