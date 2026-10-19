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

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Enhanced M3 API, leaf 8: <code>M3BulkUpdates</code>. Reloads the 1,000,000-product virtual
 * table (remove all, refill, append 10,000 rows one at a time, select a row) once as plain
 * edits and once inside <code>M3BulkUpdates.run</code>, and compares the time and the
 * emptiness events heard: the bulk update paints once and reports only the net state.
 */
public class M3BulkUpdatesExample {

static final int COUNT = 1_000_000;
static final int APPEND = 10_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static int events;

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

/* The edits of one reload. */
static void reload (Table table) {
	table.removeAll ();
	table.setItemCount (COUNT - APPEND);
	for (int k = 0; k < APPEND; k++) table.setItemCount (table.getItemCount () + 1);
	table.select (42);
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3BulkUpdates");
	shell.setLayout (new GridLayout (2, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
	for (String title : new String [] {"Product", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (160);
	}
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int i = table.indexOf (item);
		item.setText (new String [] {name (i), price (i)});
	});
	table.setItemCount (COUNT);
	M3Emptiness.addListener (table, event -> events++);
	Button plain = new Button (shell, SWT.PUSH);
	plain.setText ("Reload (plain edits)");
	Button bulk = new Button (shell, SWT.PUSH);
	bulk.setText ("Reload (M3BulkUpdates.run)");
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
	status.setText ("plain: -\nbulk: -");
	String [] lines = {"plain: -", "bulk: -"};
	Button [] buttons = {plain, bulk};
	for (int b = 0; b < buttons.length; b++) {
		int which = b;
		buttons [b].addListener (SWT.Selection, e -> {
			events = 0;
			long start = System.nanoTime ();
			if (which == 0) reload (table); else M3BulkUpdates.run (table, () -> reload (table));
			table.update ();
			long ms = (System.nanoTime () - start) / 1_000_000;
			lines [which] = (which == 0 ? "plain: " : "bulk: ") + ms + " ms, " + events + " emptiness events, "
				+ table.getItemCount () + " rows, selected " + table.getSelectionIndex ();
			status.setText (lines [0] + "\n" + lines [1]);
		});
	}
	shell.setSize (560, 440);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		plain.notifyListeners (SWT.Selection, new Event ());
		bulk.notifyListeners (SWT.Selection, new Event ());
		display.timerExec (1000, () -> snapshot (shell, snap));
	});
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

static void snapshot (Shell shell, String path) {
	Image image = new Image (shell.getDisplay (), shell.getSize ().x, shell.getSize ().y);
	GC gc = new GC (image);
	shell.print (gc);
	gc.dispose ();
	ImageLoader loader = new ImageLoader ();
	loader.data = new ImageData [] {image.getImageData ()};
	loader.save (path, SWT.IMAGE_PNG);
	image.dispose ();
	shell.dispose ();
}
}
