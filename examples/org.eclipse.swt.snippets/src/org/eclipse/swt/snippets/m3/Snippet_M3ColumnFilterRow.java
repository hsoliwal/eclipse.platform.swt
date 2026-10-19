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
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/*
 * M3 enhanced API snippet: per-column filters on a virtual table.
 *
 * The row of fields above the table filters each column ("contains", ignoring case; commas
 * separate alternatives). A right click on a column header opens the column's filter popup with
 * wildcard, regular expression, contains, starts with and equals, and their negations. Filtered
 * columns carry a funnel mark; a header click sorts the filtered rows.
 */
public class Snippet_M3ColumnFilterRow {

	public static void main (String [] args) {
		int rows = 50_000;
		Random random = new Random (2);
		String [][] cells = new String [rows][];
		String [] fruits = {"apple", "banana", "cherry", "date", "elder", "fig", "grape"};
		String [] colors = {"red", "yellow", "green", "brown", "black", "purple"};
		for (int i = 0; i < rows; i++) {
			cells [i] = new String [] {fruits [random.nextInt (fruits.length)], colors [random.nextInt (colors.length)], String.valueOf (random.nextInt (1000))};
		}
		M3CellText text = (row, column) -> cells [row][column];

		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("Column filters: filter row and header popup (right click)");
		shell.setLayout (new GridLayout ());
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		table.setHeaderVisible (true);
		for (String title : new String [] {"Fruit", "Colour", "Weight"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (title);
			column.setWidth (150);
		}

		M3ColumnFilter filter = new M3ColumnFilter (3, rows, text);
		M3ColumnSort sort = new M3ColumnSort (3, filter);
		sort.setCellText (text);
		sort.setKey (2, M3ColumnKey.ofInts (r -> Integer.parseInt (cells [r][2])));
		sort.install (table);
		M3RowBinding.bind (table, sort, (item, row) -> item.setText (cells [row]));

		M3FilterRow filterRow = new M3FilterRow (shell, table, filter);
		filterRow.moveAbove (table);
		filterRow.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		M3FilterPopup.install (table, filter);
		filterRow.getText (1).setText ("red,black");

		Label status = new Label (shell, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.BOTTOM, true, false));
		filter.addListener (change -> status.setText (filter.size () + " of " + rows + " rows"));

		shell.setSize (520, 420);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}
}
