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
 * M3 enhanced API snippet: multi-column sort of a virtual table.
 *
 * Click a column header to sort by it (ascending, descending, unsorted); Shift+click adds the
 * column as the next sort key. With two or more keys every sorted column shows its direction and
 * priority. The rows live in arrays; the sort is a permutation of row numbers, and the table only
 * asks for the rows it shows.
 */
public class Snippet_M3MultiColumnSort {

	static final String [] CITIES = {"Berlin", "Delhi", "Lima", "Oslo", "Pune", "Quito", "Rome", "Tokyo"};
	static final String [] NAMES = {"Ada", "Bea", "Cal", "Dev", "Eli", "Fay", "Gus", "Hal", "Ira", "Jo"};

	public static void main (String [] args) {
		int rows = 100_000;
		Random random = new Random (1);
		String [] name = new String [rows], city = new String [rows];
		int [] amount = new int [rows];
		for (int i = 0; i < rows; i++) {
			name [i] = NAMES [random.nextInt (NAMES.length)];
			city [i] = CITIES [random.nextInt (CITIES.length)];
			amount [i] = random.nextInt (500);
		}

		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("Multi-column sort (Shift+click adds a key)");
		shell.setLayout (new FillLayout ());
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		table.setHeaderVisible (true);
		for (String title : new String [] {"Name", "City", "Amount"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (title);
			column.setWidth (140);
		}

		M3ColumnSort sort = new M3ColumnSort (3, rows);
		sort.setKey (0, M3ColumnKey.ofText (r -> name [r], null));
		sort.setKey (1, M3ColumnKey.ofText (r -> city [r], null));
		sort.setKey (2, M3ColumnKey.ofInts (r -> amount [r]));
		sort.setSortKeys (new int [] {1, 2}, new int [] {SWT.UP, SWT.DOWN});
		sort.install (table);
		M3RowBinding.bind (table, sort, (item, row) -> {
			item.setText (0, name [row]);
			item.setText (1, city [row]);
			item.setText (2, String.valueOf (amount [row]));
		});

		shell.setSize (480, 400);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}
}
