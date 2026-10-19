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
 * Enhanced M3 language API, leaf 3: <code>org.eclipse.swt.m3.M3TextSegments</code>. Selecting a
 * product of the 1,000,000-row virtual table segments its description into words, graphemes,
 * line-break pieces or sentences and lists the segments with their offsets.
 */
public class M3TextSegmentsExample {

static final int COUNT = 1_000_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final String [] KINDS = {"WORD", "GRAPHEME", "LINE", "SENTENCE"};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

/* The text that is segmented: two sentences built from the row. */
static String description (int i) {
	return name (i) + ", price " + price (i) + ". In stock.";
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3TextSegments");
	shell.setLayout (new GridLayout (2, true));
	Table products = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	products.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 1, 2));
	products.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		item.setText (name (products.indexOf (item)));
	});
	products.setItemCount (COUNT);
	Combo kind = new Combo (shell, SWT.READ_ONLY);
	kind.setItems (KINDS);
	kind.select (M3TextSegments.WORD);
	Table segments = new Table (shell, SWT.BORDER | SWT.FULL_SELECTION);
	segments.setHeaderVisible (true);
	segments.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	for (String title : new String [] {"#", "Start", "End", "Text", "Word"}) {
		TableColumn column = new TableColumn (segments, SWT.NONE);
		column.setText (title);
		column.setWidth (title.equals ("Text") ? 120 : 44);
	}
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
	Runnable update = () -> {
		int row = Math.max (0, products.getSelectionIndex ());
		String text = description (row);
		M3TextSegments facts = M3TextSegments.of (kind.getSelectionIndex (), text, Locale.ENGLISH);
		segments.removeAll ();
		int words = 0;
		for (int s = 0; s < facts.count (); s++) {
			boolean word = facts.isWord (s);
			if (word) words++;
			TableItem item = new TableItem (segments, SWT.NONE);
			item.setText (new String [] {Integer.toString (s), Integer.toString (facts.start (s)),
				Integer.toString (facts.end (s)), "\"" + text.substring (facts.start (s), facts.end (s)) + "\"", word ? "yes" : ""});
		}
		status.setText ("\"" + text + "\"\n" + KINDS [facts.kind ()] + ": " + facts.count () + " segments, "
			+ words + " words, length " + facts.length () + ", segment at offset 9 = " + facts.segmentAt (9));
	};
	products.addListener (SWT.Selection, e -> update.run ());
	kind.addListener (SWT.Selection, e -> update.run ());
	update.run ();
	shell.setSize (640, 480);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		products.select (5);
		update.run ();
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
