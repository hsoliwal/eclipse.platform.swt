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
 * Enhanced M3 API, leaf 15: <code>M3TypeAhead</code>. Finds the first of the 1,000,000 products
 * whose text starts with (or equals) the typed text, ignoring case, from the selected row on,
 * with wrap-around. Typing selects and reveals the row; "Find next" continues the search.
 */
public class M3TypeAheadExample {

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
	shell.setText ("M3TypeAhead");
	shell.setLayout (new GridLayout (4, false));
	new Label (shell, SWT.NONE).setText ("Find:");
	Text query = new Text (shell, SWT.BORDER);
	query.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	Button whole = new Button (shell, SWT.CHECK);
	whole.setText ("Whole text");
	Button next = new Button (shell, SWT.PUSH);
	next.setText ("Find next");
	List list = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	list.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 4, 1));
	String [] items = new String [COUNT];
	Arrays.setAll (items, M3TypeAheadExample::name);
	list.setItems (items);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 4, 1));
	status.setText ("type a product number or text\n");
	int [] from = {0};
	Runnable find = () -> {
		long start = System.nanoTime ();
		int row = M3TypeAhead.indexOf (list, query.getText (), from [0], !whole.getSelection (), true);
		long us = (System.nanoTime () - start) / 1000;
		if (row >= 0) {
			list.setSelection (row);
			list.showSelection ();
		}
		status.setText ("indexOf (\"" + query.getText () + "\", from " + from [0] + ", " + (whole.getSelection () ? "equals" : "prefix")
			+ ", wrap) = " + row + "\n" + (row < 0 ? "no match" : list.getItem (row) + ", " + price (row)) + " (" + us + " µs)");
	};
	query.addListener (SWT.Modify, e -> {
		from [0] = Math.max (0, list.getSelectionIndex ());
		find.run ();
	});
	next.addListener (SWT.Selection, e -> {
		from [0] = list.getSelectionIndex () + 1;
		find.run ();
	});
	whole.addListener (SWT.Selection, e -> find.run ());
	shell.setSize (520, 460);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		query.setText ("0012343 BLUE");
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
