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
 * Enhanced M3 API, leaf 13: <code>M3ScrollMemento</code>. Saves the scroll position of the
 * 1,000,000-product virtual table as one <code>long</code>, moves it through new rows inserted
 * at the top (<code>remap</code>), and restores it after a full reload, so the same product
 * stays on top.
 */
public class M3ScrollMementoExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static int added;
static long memento;

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

/* The product shown in a row: new arrivals (numbered from COUNT) sit above the catalogue. */
static int product (int row) {
	return row < added ? COUNT + added - 1 - row : row - added;
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3ScrollMemento");
	shell.setLayout (new GridLayout (3, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
	for (String title : new String [] {"Row", "Product", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (title.equals ("Product") ? 180 : 90);
	}
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int row = table.indexOf (item), i = product (row);
		item.setText (new String [] {Integer.toString (row), name (i), price (i)});
	});
	table.setItemCount (COUNT);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	status.setText ("scroll, then save\n");
	Runnable report = () -> status.setText (String.format ("memento 0x%016x%ntop row %d shows product %07d",
		memento, table.getTopIndex (), product (table.getTopIndex ())));
	Button save = new Button (shell, SWT.PUSH);
	save.setText ("Save position");
	save.addListener (SWT.Selection, e -> {
		memento = M3ScrollMemento.save (table);
		report.run ();
	});
	Button insert = new Button (shell, SWT.PUSH);
	insert.setText ("Insert 100 new rows at the top");
	insert.addListener (SWT.Selection, e -> {
		memento = M3ScrollMemento.remap (memento, 0, 100);
		added += 100;
		table.setItemCount (COUNT + added);
		table.clearAll ();
		report.run ();
	});
	Button reload = new Button (shell, SWT.PUSH);
	reload.setText ("Reload and restore");
	reload.addListener (SWT.Selection, e -> {
		table.removeAll ();
		table.setItemCount (COUNT + added);
		M3ScrollMemento.restore (table, memento);
		report.run ();
	});
	shell.setSize (560, 460);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		table.setTopIndex (250_000);
		save.notifyListeners (SWT.Selection, new Event ());
		insert.notifyListeners (SWT.Selection, new Event ());
		reload.notifyListeners (SWT.Selection, new Event ());
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
