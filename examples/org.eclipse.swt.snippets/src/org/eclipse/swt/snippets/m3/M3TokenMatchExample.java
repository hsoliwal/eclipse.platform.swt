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
 * Enhanced M3 language API, leaf 4: <code>org.eclipse.swt.m3.M3TokenMatch</code> and
 * <code>M3TokenVocabulary</code>. Each product description of the 1,000,000-row virtual table
 * becomes a lane of token ids; a case-folded word query is matched with a presence-mask
 * prefilter and a Knuth-Morris-Pratt search. Words outside the vocabulary never match.
 */
public class M3TokenMatchExample {

static final int COUNT = 1_000_000;
static final int SCAN = 100_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final M3TokenVocabulary VOCABULARY = new M3TokenVocabulary (64, true);
static int [] needle = {};

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

static String description (int i) {
	return name (i) + ", price " + price (i) + ". In stock.";
}

/* The token ids of the words of a text; absent words are UNKNOWN unless add is set. */
static int [] lane (String text, boolean add) {
	return M3TextSegments.words (text, Locale.ENGLISH).tokens (text, VOCABULARY, add);
}

public static void main (String [] args) {
	lane (String.join (" ", FRUITS) + " price in stock", true);
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3TokenMatch / M3TokenVocabulary");
	shell.setLayout (new GridLayout (3, false));
	new Label (shell, SWT.NONE).setText ("Words:");
	Text query = new Text (shell, SWT.BORDER);
	query.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	Button next = new Button (shell, SWT.PUSH);
	next.setText ("Find next");
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
	for (String title : new String [] {"Description", "Token lane", "Match"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (title.equals ("Description") ? 260 : 120);
	}
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int i = table.indexOf (item);
		int [] lane = lane (description (i), false);
		int at = M3TokenMatch.indexOf (lane, needle, 0);
		item.setText (new String [] {description (i), Arrays.toString (lane), at < 0 ? "" : "at token " + at});
	});
	table.setItemCount (COUNT);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	status.setText ("\n");
	query.addListener (SWT.Modify, e -> {
		needle = lane (query.getText (), false);
		table.clearAll ();
		status.setText ("needle " + Arrays.toString (needle) + ", vocabulary " + VOCABULARY.size () + "/"
			+ VOCABULARY.capacity () + (VOCABULARY.foldsCase () ? ", case folded" : "") + "\n");
	});
	next.addListener (SWT.Selection, e -> {
		int from = table.getSelectionIndex () + 1, tested = 0;
		for (int i = from; i < Math.min (COUNT, from + SCAN); i++) {
			int [] lane = lane (description (i), false);
			if (!M3TokenMatch.mayContain (M3TokenMatch.presenceMask (lane), needle)) continue;
			tested++;
			int at = M3TokenMatch.indexOf (lane, needle, 0);
			if (at < 0) continue;
			table.setSelection (i);
			status.setText ("needle " + Arrays.toString (needle) + " found in row " + i + " at token " + at
				+ "\nrows scanned " + (i - from + 1) + ", passed the presence mask " + tested);
			return;
		}
		status.setText ("needle " + Arrays.toString (needle) + ": no match in " + SCAN + " rows\nrows passing the presence mask " + tested);
	});
	query.setText ("BLUEBERRY price");
	shell.setSize (600, 480);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		next.notifyListeners (SWT.Selection, new Event ());
		next.notifyListeners (SWT.Selection, new Event ());
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
