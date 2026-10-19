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
 * Enhanced M3 API, leaf 6: <code>M3RowMath</code>. The 1,000,000 products sit in a virtual tree
 * of 1000 groups with 1000 products each. The example converts between visible rows, items and
 * pixel offsets: go to a visible row, and read the row, the subtree size and the extent of the
 * selected item.
 */
public class M3RowMathExample {

static final int GROUPS = 1000, PER_GROUP = 1000;
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
	shell.setText ("M3RowMath");
	shell.setLayout (new GridLayout (4, false));
	new Label (shell, SWT.NONE).setText ("Visible row:");
	Text row = new Text (shell, SWT.BORDER);
	row.setText ("1500");
	row.setLayoutData (new GridData (80, SWT.DEFAULT));
	Button go = new Button (shell, SWT.PUSH);
	go.setText ("Go (itemAt)");
	Button expand = new Button (shell, SWT.PUSH);
	expand.setText ("Expand groups 0 and 2");
	Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
	tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 4, 1));
	tree.addListener (SWT.SetData, event -> {
		TreeItem item = (TreeItem) event.item;
		TreeItem parent = item.getParentItem ();
		if (parent == null) {
			int group = tree.indexOf (item);
			item.setText (String.format ("Group %03d: %07d..%07d", group, group * PER_GROUP, group * PER_GROUP + PER_GROUP - 1));
			item.setItemCount (PER_GROUP);
		} else {
			int i = tree.indexOf (parent) * PER_GROUP + parent.indexOf (item);
			item.setText (name (i) + "  " + price (i));
		}
	});
	tree.setItemCount (GROUPS);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 4, 1));
	Runnable update = () -> {
		long rows = M3RowMath.visibleRowCount (tree);
		String text = "visibleRowCount " + rows + ", content " + M3RowMath.rowExtent (tree, rows) + " px, row at y=1000 px: "
			+ M3RowMath.rowAtExtent (tree, 1000);
		TreeItem [] selection = tree.getSelection ();
		if (selection.length > 0) {
			long r = M3RowMath.rowOf (selection [0]);
			text += "\nselected \"" + selection [0].getText () + "\"\nrowOf " + r + ", rowExtent " + (r < 0 ? "-" : M3RowMath.rowExtent (tree, r) + " px")
				+ ", subtreeRowCount " + M3RowMath.subtreeRowCount (selection [0]);
		}
		status.setText (selection.length > 0 ? text : text + "\nnothing selected\n");
	};
	go.addListener (SWT.Selection, e -> {
		long r = Long.parseLong (row.getText ().trim ());
		if (r < 0 || r >= M3RowMath.visibleRowCount (tree)) return;
		TreeItem item = M3RowMath.itemAt (tree, r);
		tree.setSelection (item);
		update.run ();
	});
	expand.addListener (SWT.Selection, e -> {
		for (int group : new int [] {0, 2}) {
			TreeItem item = tree.getItem (group);
			item.setItemCount (PER_GROUP);
			item.setExpanded (true);
		}
		update.run ();
	});
	tree.addListener (SWT.Selection, e -> update.run ());
	tree.addListener (SWT.Expand, e -> display.asyncExec (update));
	tree.addListener (SWT.Collapse, e -> display.asyncExec (update));
	update.run ();
	shell.setSize (560, 480);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		expand.notifyListeners (SWT.Selection, new Event ());
		go.notifyListeners (SWT.Selection, new Event ());
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
