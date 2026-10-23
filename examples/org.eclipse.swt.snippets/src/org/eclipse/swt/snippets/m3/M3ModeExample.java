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
 * Enhanced M3 API: <code>M3Mode</code>. Shows the startup mode each list-type widget of the
 * 1,000,000-product catalogue effectively runs in. Run with, for example,
 * <code>-Dswt.widget.mode=original</code> or <code>-Dswt.widget.mode.Tree=custom</code> to see
 * the answers change.
 */
public class M3ModeExample {

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
	shell.setText ("M3Mode");
	shell.setLayout (new GridLayout (3, true));
	Label property = new Label (shell, SWT.NONE);
	property.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	property.setText ("swt.widget.mode = " + System.getProperty ("swt.widget.mode", "(not set)"));
	Label [] labels = new Label [3];
	for (int k = 0; k < labels.length; k++) {
		labels [k] = new Label (shell, SWT.NONE);
		labels [k].setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
	}
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	for (String title : new String [] {"Product", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (120);
	}
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int i = table.indexOf (item);
		item.setText (new String [] {name (i), price (i)});
	});
	table.setItemCount (COUNT);
	List list = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	String [] items = new String [COUNT];
	Arrays.setAll (items, M3ModeExample::name);
	list.setItems (items);
	Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
	tree.addListener (SWT.SetData, event -> {
		TreeItem item = (TreeItem) event.item;
		item.setText (name (tree.indexOf (item)));
	});
	tree.setItemCount (COUNT);
	Control [] widgets = {table, list, tree};
	for (int k = 0; k < widgets.length; k++) {
		widgets [k].setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		int mode = M3Mode.of (widgets [k]);
		labels [k].setText (widgets [k].getClass ().getSimpleName () + ": " + M3Mode.name (mode) + " (" + mode + ")");
	}
	shell.setSize (760, 420);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (1500, () -> snapshot (shell, snap));
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
