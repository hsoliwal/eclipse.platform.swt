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
 * Table viewport, example 2: fast scrolling and <code>selectAll</code> over 1,000,000 rows.
 * <p>
 * Each button runs one operation 100 times on a 1,000,000-row Table and shows the median time of one run,
 * the Table repainted (<code>update</code>) inside the measured time: a page down by <code>setTopIndex</code>,
 * a jump of 10,000 rows, <code>selectAll</code> (after <code>deselectAll</code>), <code>deselectAll</code>
 * (after <code>selectAll</code>) and a selection of row 900,000 brought into view. These are the operations
 * of the Table benchmark in docs/m3/TABLE_MEDIATED_VIEWPORT.md (section Performance). The Table is
 * <code>SWT.VIRTUAL</code>; the argument <code>plain</code> makes a plain Table of empty rows, which the default
 * mode keeps as owner data on win32. Run with <code>-Dswt.widget.mode.Table=original</code> to compare with the
 * stock code path.
 * </p>
 */
public class M3TableScrollSelectExample {

static final int COUNT = 1_000_000, RUNS = 100;
static final String [] OPERATIONS = {"Page down", "Jump 10,000", "Select all", "Deselect all", "Select row 900,000"};

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
	shell.setText ("M3 Table: scroll and select, " + (plain ? "plain" : "SWT.VIRTUAL"));
	shell.setLayout (new GridLayout (2, false));
	Table table = new Table (shell, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | (plain ? 0 : SWT.VIRTUAL));
	table.setHeaderVisible (true);
	GridData tableData = new GridData (SWT.FILL, SWT.FILL, true, true, 1, OPERATIONS.length + 1);
	tableData.widthHint = 240;
	tableData.heightHint = 320;
	table.setLayoutData (tableData);
	TableColumn column = new TableColumn (table, SWT.NONE);
	column.setText ("Row");
	column.setWidth (200);
	if (!plain) table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText (String.format (Locale.ROOT, "row %,d", event.index)));
	table.setItemCount (COUNT);
	Label result = new Label (shell, SWT.WRAP);
	for (String operation : OPERATIONS) {
		Button button = new Button (shell, SWT.PUSH);
		button.setText (operation);
		button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, false, false));
		button.addListener (SWT.Selection, event -> result.setText (measure (table, operation)));
	}
	GridData resultData = new GridData (SWT.FILL, SWT.FILL, false, true);
	resultData.widthHint = 220;
	result.setLayoutData (resultData);
	result.setText ("Press an operation: " + RUNS + " runs, median ms");
	shell.pack ();
	shell.open ();
	return shell;
}

/** Runs one operation {@link #RUNS} times and answers its median time and the Table's state after it. */
public static String measure (Table table, String operation) {
	Display display = table.getDisplay ();
	int page = Math.max (1, (table.getClientArea ().height - table.getHeaderHeight ()) / table.getItemHeight ());
	long [] nanos = new long [RUNS];
	for (int run = 0; run < RUNS; run++) {
		prepare (table, operation, run);
		table.update ();
		while (display.readAndDispatch ()) {
			/* settle before the measured run */
		}
		long start = System.nanoTime ();
		switch (operation) {
			case "Page down" -> table.setTopIndex (table.getTopIndex () + page);
			case "Jump 10,000" -> table.setTopIndex (table.getTopIndex () + 10_000);
			case "Select all" -> table.selectAll ();
			case "Deselect all" -> table.deselectAll ();
			default -> {
				table.setSelection (900_000);
				table.showSelection ();
			}
		}
		table.update ();
		nanos [run] = System.nanoTime () - start;
	}
	Arrays.sort (nanos);
	return String.format (Locale.ROOT, "%s: %.2f ms median, %.2f ms max (%d runs)%ntop row %,d, %,d selected", operation, nanos [RUNS / 2] / 1e6,
			nanos [RUNS - 1] / 1e6, RUNS, table.getTopIndex (), table.getSelectionCount ());
}

static void prepare (Table table, String operation, int run) {
	switch (operation) {
		case "Page down", "Jump 10,000" -> {
			if (run == 0) table.setTopIndex (0);
		}
		case "Select all" -> table.deselectAll ();
		case "Deselect all" -> table.selectAll ();
		default -> {
			table.setSelection (0);
			table.setTopIndex (0);
		}
	}
}
}
