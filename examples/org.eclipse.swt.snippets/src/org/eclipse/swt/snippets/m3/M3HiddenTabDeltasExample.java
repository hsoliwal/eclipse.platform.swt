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
package org.eclipse.swt.snippets.m3;

import java.util.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * Hidden viewports (brief rules 24 and 25): a view in a background tab receives 100,000 deltas, then shows and
 * paints once.
 * <p>
 * The catalogue tab holds a <code>SWT.VIRTUAL</code> Table of 1,000,000 rows fed by {@link M3Model#apply}. While the
 * overview tab is in front, the stream applies 100,000 one-row deltas (70% label updates, 15% inserts, 15% removes)
 * to the hidden table. The contract: the rows and ids change, no label is asked and nothing paints while the table is
 * hidden; selecting the catalogue tab (<code>SWT.PartVisible</code> for the table) paints its window once, asking
 * labels for those rows only, with the row that was on top when the tab was left back on top. The example measures
 * every part of it with stock means: a counting <code>M3Model.Labels</code>, a counting <code>SWT.Paint</code>
 * listener, and the id of the top row.
 * </p>
 * <p>
 * Arguments: <code>[rows] [deltas] [exit]</code>, defaults <code>1000000 100000</code>. The counts are logged through
 * <code>java.util.logging</code> and shown in the overview tab; with <code>exit</code> the example closes after the
 * show (the hidden-desktop runs of <code>docs/m3/enhanced-api</code> use it).
 * </p>
 */
public class M3HiddenTabDeltasExample {

static final Logger LOG = Logger.getLogger (M3HiddenTabDeltasExample.class.getName ());

static long labelsAsked, paints, tick;

public static void main (String [] args) {
	int rows = args.length > 0 ? Integer.parseInt (args [0]) : 1_000_000;
	int deltas = args.length > 1 ? Integer.parseInt (args [1]) : 100_000;
	boolean exit = Arrays.asList (args).contains ("exit");
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3 hidden viewport: 100,000 deltas in a background tab");
	shell.setLayout (new FillLayout ());
	TabFolder folder = new TabFolder (shell, SWT.NONE);
	TabItem overview = new TabItem (folder, SWT.NONE);
	overview.setText ("Overview");
	Label report = new Label (folder, SWT.WRAP);
	overview.setControl (report);
	TabItem catalogue = new TabItem (folder, SWT.NONE);
	catalogue.setText ("Catalogue");
	Table table = new Table (folder, SWT.VIRTUAL | SWT.MULTI | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	for (String title : new String [] {"Id", "Name", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (140);
	}
	catalogue.setControl (table);
	shell.setSize (520, 600);
	shell.open ();
	folder.setSelection (1);
	table.setItemCount (rows);
	M3Model model = M3Model.of (table);
	M3Model.Labels labels = (id, row) -> {
		labelsAsked++;
		row.setText (0, Long.toString (id));
		row.setText (1, "product " + id);
		row.setText (2, String.format (Locale.ROOT, "%.2f", ((id * 37 + tick) % 10000) / 100.0));
	};
	model.apply (M3Patch.builder (M3Patch.ROOT).update (0, 1).build (), labels);
	table.setTopIndex (rows / 2);
	table.addListener (SWT.Paint, event -> paints++);
	pump (display);
	long anchor = model.id (M3Patch.ROOT, table.getTopIndex ());

	/* the view goes to the background: the overview tab comes to the front */
	folder.setSelection (0);
	pump (display);
	labelsAsked = 0;
	paints = 0;
	Random random = new Random (2026);
	long [] nanos = new long [deltas];
	long nextId = rows;
	long start = System.nanoTime ();
	for (int k = 0; k < deltas; k++) {
		int size = model.size (M3Patch.ROOT), kind = random.nextInt (100);
		M3Patch.Builder builder = M3Patch.builder (M3Patch.ROOT);
		if (kind < 70) {
			builder.update (random.nextInt (size), 1);
		} else if (kind < 85) {
			builder.insert (random.nextInt (size + 1), nextId++);
		} else {
			builder.remove (random.nextInt (size), 1);
		}
		tick = k;
		long t0 = System.nanoTime ();
		model.apply (builder.build (), labels);
		nanos [k] = System.nanoTime () - t0;
		if (k % 1000 == 999) pump (display);
	}
	long streamMillis = (System.nanoTime () - start) / 1_000_000;
	pump (display);
	long topWhileHidden = model.id (M3Patch.ROOT, table.getTopIndex ());
	String hidden = String.format (Locale.ROOT,
		"hidden: %,d deltas in %,d ms (median %,d us, p99 %,d us), %,d labels asked, %,d paints, %,d rows, top row id %d",
		deltas, streamMillis, percentile (nanos, 50), percentile (nanos, 99), labelsAsked, paints, model.size (M3Patch.ROOT),
		topWhileHidden);
	labelsAsked = 0;
	paints = 0;

	/* the view comes back: one show, one paint of the window */
	long shown = System.nanoTime ();
	folder.setSelection (1);
	pump (display);
	long showMillis = (System.nanoTime () - shown) / 1_000_000;
	long top = model.id (M3Patch.ROOT, table.getTopIndex ());
	String back = String.format (Locale.ROOT,
		"shown: %d ms to the first idle loop, %,d paints, %,d labels asked, top row id %d (%s; id on top when hidden %d)",
		showMillis, paints, labelsAsked, top, top == anchor ? "re-anchored" : "moved", anchor);
	LOG.info (hidden);
	LOG.info (back);
	report.setText (hidden + "\n\n" + back);
	while (!exit && !shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

static long percentile (long [] nanos, int percent) {
	long [] sorted = nanos.clone ();
	Arrays.sort (sorted);
	return sorted.length == 0 ? 0 : sorted [Math.min (sorted.length - 1, sorted.length * percent / 100)] / 1000;
}

/* runs the event loop until it is idle: paints and SetData of the rows that show */
static void pump (Display display) {
	for (int idle = 0; idle < 3; ) {
		if (display.readAndDispatch ()) {
			idle = 0;
		} else {
			idle++;
			display.timerExec (20, () -> {});
			display.sleep ();
		}
	}
}
}
