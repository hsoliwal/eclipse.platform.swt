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
 * Enhanced M3 API, leaf 11: <code>M3VisibilityObserver</code>. A List of the 1,000,000
 * products reports the row ranges that came into view (ENTERED) and that left it (LEFT); the
 * first listener hears the current range as entered. The log on the right shows the events.
 */
public class M3VisibilityObserverExample {

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
	shell.setText ("M3VisibilityObserver");
	shell.setLayout (new GridLayout (3, false));
	List products = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	products.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
	String [] items = new String [COUNT];
	Arrays.setAll (items, i -> name (i) + "  " + price (i));
	products.setItems (items);
	List log = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	GridData logData = new GridData (SWT.FILL, SWT.FILL, false, true, 1, 3);
	logData.widthHint = 240;
	log.setLayoutData (logData);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
	status.setText ("visible rows: -");
	M3VisibilityObserver.addListener (products, event -> {
		log.add ((event.detail == M3VisibilityObserver.ENTERED ? "ENTERED " : "LEFT    ") + event.start + ".." + event.end);
		log.setTopIndex (log.getItemCount () - 1);
		M3ItemRange visible = M3ItemSelection.getVisibleRange (products);
		status.setText ("visible rows: " + visible.start () + ".." + visible.end ());
	});
	Button down = new Button (shell, SWT.PUSH);
	down.setText ("Scroll down 3 rows");
	down.addListener (SWT.Selection, e -> products.setTopIndex (products.getTopIndex () + 3));
	Button jump = new Button (shell, SWT.PUSH);
	jump.setText ("Jump to row 500000");
	jump.addListener (SWT.Selection, e -> products.setTopIndex (500_000));
	shell.setSize (620, 440);
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
