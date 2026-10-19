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

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * Enhanced M3 language API, leaf 12: <code>org.eclipse.swt.m3.M3Collation</code> and
 * <code>M3SortKeys</code>. Sorts the 1,000,000 products by product name with the collation of
 * the chosen locale: in German "Äpfel" sorts with "apple", in Swedish after "Zucchini". The
 * keys are built once per locale; the stable order keeps the product numbers ascending in a group.
 */
public class M3CollationExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final Locale [] LOCALES = {Locale.ENGLISH, Locale.GERMAN, Locale.forLanguageTag ("sv")};
static int [] order = new int [COUNT];

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

public static void main (String [] args) {
	Arrays.setAll (order, i -> i);
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3Collation / M3SortKeys");
	shell.setLayout (new GridLayout (3, false));
	new Label (shell, SWT.NONE).setText ("Sort by product name for:");
	Combo locale = new Combo (shell, SWT.READ_ONLY);
	for (Locale l : LOCALES) locale.add (l.getDisplayLanguage (Locale.ENGLISH));
	Button next = new Button (shell, SWT.PUSH);
	next.setText ("Next group boundary");
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
		int row = table.indexOf (item), i = order [row];
		item.setText (new String [] {Integer.toString (row), name (i), price (i)});
	});
	table.setItemCount (COUNT);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	status.setText ("unsorted\n");
	locale.addListener (SWT.Selection, e -> {
		Locale l = LOCALES [locale.getSelectionIndex ()];
		long start = System.nanoTime ();
		String [] names = new String [COUNT];
		Arrays.setAll (names, i -> FRUITS [i % FRUITS.length]);
		M3SortKeys keys = M3Collation.keys (names, l);
		order = keys.sortedOrder ();
		long ms = (System.nanoTime () - start) / 1_000_000;
		StringBuilder groups = new StringBuilder ();
		for (int f : M3Collation.keys (FRUITS, l).sortedOrder ()) groups.append (groups.length () == 0 ? "" : " < ").append (FRUITS [f]);
		status.setText (l.getDisplayLanguage (Locale.ENGLISH) + ": " + keys.size () + " keys and the stable order in " + ms + " ms\n" + groups);
		table.clearAll ();
		table.setTopIndex (0);
	});
	next.addListener (SWT.Selection, e -> {
		int group = (table.getTopIndex () + 3) / (COUNT / FRUITS.length) + 1;
		table.setTopIndex (Math.min (COUNT - 1, group * (COUNT / FRUITS.length) - 3));
	});
	shell.setSize (560, 460);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		locale.select (2);
		locale.notifyListeners (SWT.Selection, new Event ());
		for (int k = 0; k < 9; k++) next.notifyListeners (SWT.Selection, new Event ());
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
