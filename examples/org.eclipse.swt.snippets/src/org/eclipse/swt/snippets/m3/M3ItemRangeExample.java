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
 * Enhanced M3 API, leaf 1: <code>M3ItemRange</code> and <code>M3ItemSelection</code>.
 * Selects and deselects whole row ranges of a virtual table of 1,000,000 products and shows the
 * selection in normal form (sorted, disjoint ranges) and the visible row range.
 */
public class M3ItemRangeExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3ItemRange / M3ItemSelection");
	shell.setLayout (new GridLayout (3, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
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
	Label status = new Label (shell, SWT.WRAP);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	Runnable update = () -> status.setText (describe (table));
	Button select = new Button (shell, SWT.PUSH);
	select.setText ("Select 2..5, 9..14, 20..20019");
	select.addListener (SWT.Selection, e -> {
		M3ItemSelection.setSelectionRanges (table, new M3ItemRange [] {
			M3ItemRange.of (2, 5), M3ItemRange.of (9, 14), M3ItemRange.of (20, 20_019)});
		update.run ();
	});
	Button deselect = new Button (shell, SWT.PUSH);
	deselect.setText ("Deselect 11..12");
	deselect.addListener (SWT.Selection, e -> {
		M3ItemSelection.deselect (table, M3ItemRange.of (11, 12));
		update.run ();
	});
	Button add = new Button (shell, SWT.PUSH);
	add.setText ("Add 999990..999999");
	add.addListener (SWT.Selection, e -> {
		M3ItemSelection.select (table, M3ItemRange.of (999_990, 999_999));
		update.run ();
	});
	table.addListener (SWT.Selection, e -> update.run ());
	table.getVerticalBar ().addListener (SWT.Selection, e -> update.run ());
	table.addListener (SWT.Resize, e -> update.run ());
	update.run ();
	shell.setSize (520, 480);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		select.notifyListeners (SWT.Selection, new Event ());
		deselect.notifyListeners (SWT.Selection, new Event ());
		display.timerExec (1000, () -> snapshot (shell, snap));
	});
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

static String describe (Table table) {
	M3ItemRange [] ranges = M3ItemSelection.getSelectionRanges (table);
	StringBuilder text = new StringBuilder ("Selected ranges:");
	for (M3ItemRange range : ranges) text.append (' ').append (range.start ()).append ("..").append (range.end ());
	M3ItemRange visible = M3ItemSelection.getVisibleRange (table);
	text.append ("\nSelected rows: ").append (M3ItemRange.countOf (ranges)).append (" in ").append (ranges.length).append (" ranges");
	text.append ("\nVisible rows: ").append (visible.start ()).append ("..").append (visible.end ()).append (" (").append (visible.size ()).append (" rows)");
	return text.toString ();
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
