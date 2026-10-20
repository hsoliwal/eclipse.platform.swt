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
import org.eclipse.swt.widgets.List;

/**
 * Enhanced M3 API, leaf 5: <code>M3Emptiness</code>. A listener hears each time the virtual
 * product table crosses zero items (BECAME_EMPTY, BECAME_NON_EMPTY); changes that stay on one
 * side of zero, such as shrinking 1,000,000 rows to 500, send nothing. An empty-state label
 * is shown and hidden from the event.
 */
public class M3EmptinessExample {

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
	shell.setText ("M3Emptiness");
	shell.setLayout (new GridLayout (4, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
	for (String title : new String [] {"Product", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (140);
	}
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int i = table.indexOf (item);
		item.setText (new String [] {name (i), price (i)});
	});
	List log = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	GridData logData = new GridData (SWT.FILL, SWT.FILL, false, true, 1, 3);
	logData.widthHint = 220;
	log.setLayoutData (logData);
	Label empty = new Label (shell, SWT.NONE);
	empty.setText ("The catalogue is empty.");
	empty.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	M3Emptiness.addListener (table, event -> {
		boolean nowEmpty = event.detail == M3Emptiness.BECAME_EMPTY;
		log.add ((nowEmpty ? "BECAME_EMPTY" : "BECAME_NON_EMPTY") + ", count " + event.count);
		empty.setText (nowEmpty ? "The catalogue is empty." : "The catalogue has items.");
	});
	String [] labels = {"Fill 1,000,000", "Shrink to 500", "Remove all"};
	int [] counts = {COUNT, 500, 0};
	Button [] buttons = new Button [labels.length];
	for (int b = 0; b < buttons.length; b++) {
		int count = counts [b];
		buttons [b] = new Button (shell, SWT.PUSH);
		buttons [b].setText (labels [b]);
		buttons [b].addListener (SWT.Selection, e -> {
			log.add ("setItemCount (" + count + ")");
			if (count == 0) table.removeAll (); else table.setItemCount (count);
		});
	}
	shell.setSize (620, 420);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		for (int b : new int [] {0, 1, 2, 0}) buttons [b].notifyListeners (SWT.Selection, new Event ());
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
