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
 * Enhanced M3 API, leaf 14: <code>org.eclipse.swt.m3.M3ProxyModel</code>. A filtered and sorted
 * view of the 1,000,000 source products, kept as int lanes and bound to a virtual table: filter
 * by a name fragment, then sort by price or by German collation of the name. The status maps
 * between view rows and source rows.
 */
public class M3ProxyModelExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final String [] SORTS = {"source order", "price", "name (German collation)"};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3ProxyModel");
	shell.setLayout (new GridLayout (4, false));
	new Label (shell, SWT.NONE).setText ("Name contains:");
	Text filter = new Text (shell, SWT.BORDER);
	filter.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	filter.setMessage ("fruit name, then Enter");
	new Label (shell, SWT.NONE).setText ("Sort by:");
	Combo sort = new Combo (shell, SWT.READ_ONLY);
	sort.setItems (SORTS);
	sort.select (0);
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 4, 1));
	for (String title : new String [] {"View row", "Product", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (title.equals ("Product") ? 180 : 90);
	}
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 4, 1));
	String [] lower = new String [FRUITS.length];
	Arrays.setAll (lower, f -> FRUITS [f].toLowerCase (Locale.ROOT));
	String [] names = new String [COUNT];
	Arrays.setAll (names, i -> FRUITS [i % FRUITS.length]);
	M3SortKeys [] german = new M3SortKeys [1];
	M3ProxyModel model = new M3ProxyModel (COUNT);
	Runnable rebuild = () -> {
		long start = System.nanoTime ();
		String fragment = filter.getText ().trim ().toLowerCase (Locale.ROOT);
		model.clearStages ();
		if (!fragment.isEmpty ()) model.addFilter (s -> lower [s % FRUITS.length].contains (fragment));
		if (sort.getSelectionIndex () == 1) model.addSort ((a, b) -> Long.compare (a * 37L % 10000, b * 37L % 10000));
		if (sort.getSelectionIndex () == 2) {
			if (german [0] == null) german [0] = M3Collation.keys (names, Locale.GERMAN);
			model.addSort (german [0]);
		}
		model.refresh ();
		model.bind (table, (item, source) -> item.setText (new String [] {
			Integer.toString (model.unmap (source)), name (source), price (source)}));
		long ms = (System.nanoTime () - start) / 1_000_000;
		status.setText ("view " + model.size () + " of " + model.sourceSize () + " source rows (" + ms + " ms)\n"
			+ (model.size () == 0 ? "empty view" : "view row 0 -> source " + model.map (0) + "; source 3 -> view row " + model.unmap (3)));
	};
	filter.addListener (SWT.DefaultSelection, e -> rebuild.run ());
	sort.addListener (SWT.Selection, e -> rebuild.run ());
	rebuild.run ();
	shell.setSize (600, 460);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		filter.setText ("berry");
		sort.select (1);
		rebuild.run ();
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
