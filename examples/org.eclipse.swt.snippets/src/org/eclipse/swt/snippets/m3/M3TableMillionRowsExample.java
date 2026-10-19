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
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Table viewport, example 1: a Table of 1,000,000 rows.
 * <p>
 * Without an argument the Table is <code>SWT.VIRTUAL</code>: <code>setItemCount</code> makes the rows and
 * <code>SWT.SetData</code> fills a row when it first shows (the pattern of Snippet144). With the argument
 * <code>plain</code> the Table is a plain one: <code>setItemCount</code> makes 1,000,000 empty rows and four of
 * them get a text through <code>getItem (int)</code>. On win32 the default mode keeps every plain Table as
 * owner data, so the million rows cost one native count and no TableItem until the application asks for a row;
 * the status line shows what <code>setItemCount</code> took. Run with
 * <code>-Dswt.widget.mode.Table=original</code> for the stock code path.
 * </p>
 */
public class M3TableMillionRowsExample {

static final int COUNT = 1_000_000;

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = open (display, args.length > 0 && args [0].equals ("plain"));
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

/** Builds and opens the example's shell (the screenshot harness of docs/m3/table-viewport calls it too). */
public static Shell open (Display display, boolean plain) {
	Shell shell = new Shell (display);
	shell.setText ("M3 Table: 1,000,000 rows, " + (plain ? "plain" : "SWT.VIRTUAL"));
	shell.setLayout (new GridLayout (4, true));
	Table table = new Table (shell, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | (plain ? 0 : SWT.VIRTUAL));
	table.setHeaderVisible (true);
	table.setLinesVisible (true);
	GridData tableData = new GridData (SWT.FILL, SWT.FILL, true, true, 4, 1);
	tableData.widthHint = 380;
	tableData.heightHint = 300;
	table.setLayoutData (tableData);
	for (String title : new String [] {"Row", "Hex", "Square mod 9973"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (120);
	}
	if (!plain) table.addListener (SWT.SetData, event -> fill ((TableItem) event.item, event.index));
	long start = System.nanoTime ();
	table.setItemCount (COUNT);
	long millis = (System.nanoTime () - start) / 1_000_000;
	if (plain) {
		for (int row : new int [] {0, 1, COUNT / 2, COUNT - 1}) fill (table.getItem (row), row);
	}
	button (shell, "First", () -> table.setTopIndex (0));
	button (shell, "Middle", () -> table.setTopIndex (COUNT / 2 - 2));
	button (shell, "Last", () -> table.showItem (table.getItem (COUNT - 1)));
	button (shell, "Select last", () -> {
		table.setSelection (COUNT - 1);
		table.showSelection ();
	});
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 4, 1));
	status.setText (String.format (Locale.ROOT, "%,d rows: setItemCount took %d ms, bar maximum %,d", table.getItemCount (), millis,
			table.getVerticalBar ().getMaximum ()));
	shell.pack ();
	shell.open ();
	return shell;
}

static void fill (TableItem item, int row) {
	item.setText (new String [] {String.format (Locale.ROOT, "%,d", row), Integer.toHexString (row), Long.toString ((long) row * row % 9973)});
}

static void button (Shell shell, String text, Runnable action) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	button.addListener (SWT.Selection, event -> action.run ());
}
}
