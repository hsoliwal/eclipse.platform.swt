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
 * Enhanced M3 API, leaf 2: <code>M3Scrolling</code> and <code>M3ScrollInfo</code>. Scrolls a
 * virtual table of 1,000,000 products to a row with each of the four alignments (TOP, CENTER,
 * BOTTOM, MIN_DISTANCE) and shows the pixel-level scroll state.
 */
public class M3ScrollingExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final String [] ALIGNMENTS = {"TOP", "CENTER", "BOTTOM", "MIN_DISTANCE"};
static final int [] CODES = {M3Scrolling.TOP, M3Scrolling.CENTER, M3Scrolling.BOTTOM, M3Scrolling.MIN_DISTANCE};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3Scrolling / M3ScrollInfo");
	shell.setLayout (new GridLayout (6, false));
	new Label (shell, SWT.NONE).setText ("Row:");
	Text row = new Text (shell, SWT.BORDER);
	row.setText ("500000");
	row.setLayoutData (new GridData (80, SWT.DEFAULT));
	Button [] buttons = new Button [ALIGNMENTS.length];
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	Label status = new Label (shell, SWT.NONE);
	for (int a = 0; a < ALIGNMENTS.length; a++) {
		int alignment = CODES [a];
		String title = ALIGNMENTS [a];
		buttons [a] = new Button (shell, SWT.PUSH);
		buttons [a].setText (title);
		buttons [a].moveAbove (table);
		buttons [a].addListener (SWT.Selection, e -> {
			int index = Integer.parseInt (row.getText ().trim ());
			table.select (index);
			int top = M3Scrolling.scrollTo (table, index, alignment);
			status.setText ("scrollTo (" + index + ", " + title + ") -> top " + top + "\n" + describe (table));
		});
	}
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 6, 1));
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
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 6, 1));
	status.setText ("\n" + describe (table));
	table.getVerticalBar ().addListener (SWT.Selection, e -> status.setText ("scrolled by the user\n" + describe (table)));
	shell.setSize (560, 480);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		buttons [M3Scrolling.CENTER].notifyListeners (SWT.Selection, new Event ());
		display.timerExec (1000, () -> snapshot (shell, snap));
	});
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

static String describe (Table table) {
	M3ScrollInfo info = M3Scrolling.getScrollInfo (table);
	return "top " + info.topIndex () + ", offset " + info.pixelOffset () + " px, row " + info.rowExtent ()
		+ " px, viewport " + info.viewportExtent () + " px, rows " + info.visibleRows () + " (" + info.fullRows ()
		+ " full)\ncontent " + info.contentExtent () + " px, position " + info.pixelPosition ()
		+ " px, max top " + info.maxTopIndex () + " of " + info.rowCount () + " rows";
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
