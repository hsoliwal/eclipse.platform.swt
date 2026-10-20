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
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;

/**
 * Enhanced M3 API, leaf 7: <code>M3Residency</code>. A virtual Table, a List and a virtual Tree
 * each show the 1,000,000 products; the labels show which rows each widget holds ready to paint
 * and how many item objects exist. With the viewports these stay bounded by the window, not by
 * the item count.
 */
public class M3ResidencyExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

static String describe (M3ItemRange resident, long [] stats) {
	return "resident " + (resident.isEmpty () ? "none" : resident.start () + ".." + resident.end ()) + "\n"
		+ stats [M3Residency.RESIDENT_ROWS] + " rows, " + stats [M3Residency.FACADES] + " items ("
		+ stats [M3Residency.HELD_FACADES] + " held)";
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3Residency");
	shell.setLayout (new GridLayout (3, true));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int i = table.indexOf (item);
		item.setText (name (i) + "  " + price (i));
	});
	table.setItemCount (COUNT);
	List list = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	String [] items = new String [COUNT];
	Arrays.setAll (items, M3ResidencyExample::name);
	list.setItems (items);
	Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
	tree.addListener (SWT.SetData, event -> {
		TreeItem item = (TreeItem) event.item;
		item.setText (name (tree.indexOf (item)));
	});
	tree.setItemCount (COUNT);
	Label [] labels = new Label [3];
	for (int k = 0; k < labels.length; k++) {
		labels [k] = new Label (shell, SWT.NONE);
		labels [k].setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		labels [k].setText ("\n\n");
	}
	for (Control control : new Control [] {table, list, tree}) control.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	Button jump = new Button (shell, SWT.PUSH);
	jump.setText ("Scroll all to row 500000");
	jump.setLayoutData (new GridData (SWT.LEFT, SWT.CENTER, false, false, 3, 1));
	jump.addListener (SWT.Selection, e -> {
		table.setTopIndex (500_000);
		list.setTopIndex (500_000);
		tree.setTopItem (tree.getItem (500_000));
	});
	Runnable refresh = new Runnable () {
		@Override
		public void run () {
			if (shell.isDisposed ()) return;
			labels [0].setText ("Table, row 500000 materialized: " + M3Residency.isMaterialized (table, 500_000) + "\n"
				+ describe (M3Residency.residentRange (table), M3Residency.stats (table)));
			labels [1].setText ("List\n" + describe (M3Residency.residentRange (list), M3Residency.stats (list)));
			labels [2].setText ("Tree\n" + describe (M3Residency.residentRange (tree), M3Residency.stats (tree)));
			display.timerExec (300, this);
		}
	};
	shell.setSize (900, 460);
	shell.open ();
	display.timerExec (300, refresh);
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		jump.notifyListeners (SWT.Selection, new Event ());
		display.timerExec (1200, () -> snapshot (shell, snap));
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
