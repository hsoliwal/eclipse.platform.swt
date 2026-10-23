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

import java.util.function.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Enhanced M3 API, leaf 10: <code>M3DiffApply</code>. The virtual table shows the 1,000,000
 * products by identity (the product number). Filters and moves give a new identity order, and
 * <code>M3DiffApply.apply</code> removes and inserts only the rows that changed; kept rows keep
 * their item, text and selection. The status shows kept, removed and inserted rows.
 */
public class M3DiffApplyExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static long [] ids = LongStream.range (0, COUNT).toArray ();

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

static void apply (Table table, Label status, String what, long [] newIds) {
	long start = System.nanoTime ();
	int [] answer = M3DiffApply.apply (table, ids, newIds, null);
	long ms = (System.nanoTime () - start) / 1_000_000;
	ids = newIds;
	status.setText (what + ": kept " + answer [M3DiffApply.KEPT] + ", removed " + answer [M3DiffApply.REMOVED]
		+ ", inserted " + answer [M3DiffApply.INSERTED] + "\n" + table.getItemCount () + " rows, " + ms + " ms, selected row "
		+ table.getSelectionIndex () + " (product " + (table.getSelectionIndex () < 0 ? "-" : ids [table.getSelectionIndex ()]) + ")");
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3DiffApply");
	shell.setLayout (new GridLayout (3, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
	for (String title : new String [] {"Product", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (160);
	}
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int id = (int) ids [table.indexOf (item)];
		item.setText (new String [] {name (id), price (id)});
	});
	table.setItemCount (COUNT);
	table.select (12);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	status.setText ("identity = product number; row 12 selected\n");
	String [] titles = {"Hide kiwi rows below 100", "Move rows 0..2 to the end", "Restore the catalogue"};
	java.util.List<Supplier<long []>> orders = java.util.List.of (
		() -> LongStream.of (ids).filter (id -> id >= 100 || id % FRUITS.length != 6).toArray (),
		() -> LongStream.concat (LongStream.of (ids).skip (3), LongStream.of (ids).limit (3)).toArray (),
		() -> LongStream.range (0, COUNT).toArray ());
	Button [] buttons = new Button [titles.length];
	for (int b = 0; b < buttons.length; b++) {
		int which = b;
		buttons [b] = new Button (shell, SWT.PUSH);
		buttons [b].setText (titles [b]);
		buttons [b].addListener (SWT.Selection, e -> apply (table, status, titles [which], orders.get (which).get ()));
	}
	shell.setSize (600, 440);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		buttons [0].notifyListeners (SWT.Selection, new Event ());
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
