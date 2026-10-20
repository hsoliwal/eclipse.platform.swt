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
 * Enhanced M3 API, leaf 17: <code>M3CheckState</code>. A small check tree of the catalogue: one
 * root, the ten fruit groups and the first ten products of each group. Checking an item checks
 * its subtree and recomputes its ancestors as CHECKED, PARTIAL (checked and grayed) or
 * UNCHECKED; with dig-in on, the user's own check toggles do the same.
 */
public class M3CheckStateExample {

static final int PER_GROUP = 10;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final String [] STATES = {"UNCHECKED", "CHECKED", "PARTIAL"};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3CheckState");
	shell.setLayout (new GridLayout (3, false));
	Tree tree = new Tree (shell, SWT.CHECK | SWT.BORDER);
	tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
	TreeItem root = new TreeItem (tree, SWT.NONE);
	root.setText ("Catalogue");
	for (int f = 0; f < FRUITS.length; f++) {
		TreeItem group = new TreeItem (root, SWT.NONE);
		group.setText (FRUITS [f]);
		for (int k = 0; k < PER_GROUP; k++) {
			int i = k * FRUITS.length + f;
			new TreeItem (group, SWT.NONE).setText (name (i) + "  " + price (i));
		}
	}
	root.setExpanded (true);
	M3CheckState.setDigIn (tree, true);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	Runnable update = () -> {
		StringBuilder text = new StringBuilder ("Catalogue: " + STATES [M3CheckState.state (root)] + "\n");
		for (TreeItem group : root.getItems ()) {
			if (M3CheckState.state (group) != M3CheckState.UNCHECKED) text.append (group.getText ()).append (": ").append (STATES [M3CheckState.state (group)]).append ("  ");
		}
		status.setText (text.toString ());
	};
	tree.addListener (SWT.Selection, e -> {
		if (e.detail == SWT.CHECK) update.run ();
	});
	Button group = new Button (shell, SWT.PUSH);
	group.setText ("Check group Blueberry");
	group.addListener (SWT.Selection, e -> {
		M3CheckState.setChecked (root.getItem (3), true);
		update.run ();
	});
	Button product = new Button (shell, SWT.PUSH);
	product.setText ("Uncheck product 0000013");
	product.addListener (SWT.Selection, e -> {
		TreeItem item = root.getItem (3).getItem (1);
		item.getParentItem ().setExpanded (true);
		M3CheckState.setChecked (item, false);
		update.run ();
	});
	Button all = new Button (shell, SWT.PUSH);
	all.setText ("Check all");
	all.addListener (SWT.Selection, e -> {
		M3CheckState.setChecked (root, true);
		update.run ();
	});
	update.run ();
	shell.setSize (480, 480);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		group.notifyListeners (SWT.Selection, new Event ());
		product.notifyListeners (SWT.Selection, new Event ());
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
