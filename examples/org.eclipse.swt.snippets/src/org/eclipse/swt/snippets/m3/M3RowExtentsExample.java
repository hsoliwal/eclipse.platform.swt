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
 * Enhanced M3 API, leaf 16: <code>M3RowExtents</code>. A canvas draws the 1,000,000 products as
 * rows of one to three lines. Rows start at an estimated height and are measured when first
 * painted; <code>setExtentAnchored</code> keeps the rows in view still, and
 * <code>scrollOffsetFor</code> reveals a row with an alignment.
 */
public class M3RowExtentsExample {

static final int COUNT = 1_000_000, LINE = 18, BAR = 10_000;
static final String [] FRUITS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "Äpfel", "kiwi", "Zucchini", "éclair", "date"};
static final M3RowExtents EXTENTS = new M3RowExtents (COUNT, 2 * LINE);
static long offset;

static String name (int i) {
	return String.format ("%07d %s", i, FRUITS [i % FRUITS.length]);
}

static String price (int i) {
	return String.format ("%.2f", (i * 37L % 10000) / 100.0);
}

/* The lines of a row: the product, then up to two lines of details. */
static int lines (int row) {
	return 1 + row % 3;
}

/* Measures the unmeasured rows in view, moving the offset so that the content stays still. */
static void measure (int viewport) {
	M3ItemRange rows = EXTENTS.visibleRange (offset, viewport);
	for (int r = rows.start (); !rows.isEmpty () && r <= rows.end (); r++) {
		if (!EXTENTS.isMeasured (r)) offset += EXTENTS.setExtentAnchored (r, lines (r) * LINE + 4, offset, false);
	}
	offset = Math.max (0, Math.min (offset, EXTENTS.maxScrollOffset (viewport)));
}

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3RowExtents");
	shell.setLayout (new GridLayout (2, false));
	Canvas canvas = new Canvas (shell, SWT.BORDER | SWT.V_SCROLL | SWT.DOUBLE_BUFFERED);
	canvas.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
	ScrollBar bar = canvas.getVerticalBar ();
	bar.setValues (0, 0, BAR, BAR / 100, 1, BAR / 100);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
	canvas.addListener (SWT.Paint, e -> {
		int viewport = canvas.getClientArea ().height;
		measure (viewport);
		measure (viewport);
		M3ItemRange rows = EXTENTS.visibleRange (offset, viewport);
		for (int r = rows.start (); !rows.isEmpty () && r <= rows.end (); r++) {
			int y = (int) (EXTENTS.offsetOf (r) - offset);
			e.gc.setBackground (display.getSystemColor (r % 2 == 0 ? SWT.COLOR_LIST_BACKGROUND : SWT.COLOR_WIDGET_LIGHT_SHADOW));
			e.gc.fillRectangle (0, y, canvas.getClientArea ().width, EXTENTS.extentOf (r));
			e.gc.drawString (name (r) + "  " + price (r), 4, y + 2, true);
			for (int k = 1; k < lines (r); k++) e.gc.drawString ("detail line " + k + " of row " + r, 24, y + 2 + k * LINE, true);
		}
		long max = Math.max (1, EXTENTS.maxScrollOffset (viewport));
		bar.setSelection ((int) (offset * (BAR - bar.getThumb ()) / max));
		status.setText ("rows " + rows.start () + ".." + rows.end () + ", offset " + offset + " px of " + EXTENTS.totalExtent ()
			+ " px\nmeasured " + EXTENTS.measuredCount () + " rows, estimate " + EXTENTS.estimate () + " px");
	});
	bar.addListener (SWT.Selection, e -> {
		long max = EXTENTS.maxScrollOffset (canvas.getClientArea ().height);
		offset = (long) bar.getSelection () * max / (BAR - bar.getThumb ());
		canvas.redraw ();
	});
	Button reveal = new Button (shell, SWT.PUSH);
	reveal.setText ("Reveal row 500000 (ALIGN_CENTER)");
	reveal.addListener (SWT.Selection, e -> {
		int viewport = canvas.getClientArea ().height;
		offset = EXTENTS.scrollOffsetFor (500_000, M3RowExtents.ALIGN_CENTER, offset, viewport);
		canvas.redraw ();
	});
	status.setText ("\n");
	shell.setSize (520, 460);
	shell.open ();
	String snap = System.getProperty ("m3.snapshot");
	if (snap != null) display.timerExec (500, () -> {
		reveal.notifyListeners (SWT.Selection, new Event ());
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
