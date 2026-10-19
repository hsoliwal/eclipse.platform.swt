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
 * Enhanced M3 API, leaf 9: <code>M3Prefetch</code>. A virtual tree of 1,000,000 products
 * announces the row ranges entering (PREFETCH) and leaving (CANCEL) its resident window, widened
 * by <code>setWindow</code>, so a data source could fetch a block before <code>SWT.SetData</code>
 * asks row by row. The log shows the ranges and counts the SetData calls.
 */
public class M3PrefetchExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static long prefetched, setData;

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3Prefetch");
	shell.setLayout (new GridLayout (3, false));
	Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
	tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
	tree.addListener (SWT.SetData, event -> {
		TreeItem item = (TreeItem) event.item;
		int i = tree.indexOf (item);
		item.setText (name (i) + "  " + price (i));
		setData++;
	});
	List log = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	GridData logData = new GridData (SWT.FILL, SWT.FILL, false, true, 1, 3);
	logData.widthHint = 260;
	log.setLayoutData (logData);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
	status.setText ("window widened by 20 rows above and 40 below\n");
	M3Prefetch.setWindow (tree, 20, 40);
	M3Prefetch.addListener (tree, event -> {
		boolean fetch = event.detail == M3Prefetch.PREFETCH;
		log.add ((fetch ? "PREFETCH " : "CANCEL   ") + event.start + ".." + event.end);
		log.setTopIndex (log.getItemCount () - 1);
		if (fetch) prefetched += event.end - event.start + 1;
		status.setText ("rows announced for prefetch: " + prefetched + "\nSWT.SetData calls: " + setData);
	});
	tree.setItemCount (COUNT);
	Button down = new Button (shell, SWT.PUSH);
	down.setText ("Scroll down 5 rows");
	down.addListener (SWT.Selection, e -> {
		TreeItem top = tree.getTopItem ();
		tree.setTopItem (tree.getItem (Math.min (COUNT - 1, (top == null ? 0 : tree.indexOf (top)) + 5)));
	});
	Button jump = new Button (shell, SWT.PUSH);
	jump.setText ("Jump to row 500000");
	jump.addListener (SWT.Selection, e -> tree.setTopItem (tree.getItem (500_000)));
	shell.setSize (640, 460);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		down.notifyListeners (SWT.Selection, new Event ());
		jump.notifyListeners (SWT.Selection, new Event ());
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
