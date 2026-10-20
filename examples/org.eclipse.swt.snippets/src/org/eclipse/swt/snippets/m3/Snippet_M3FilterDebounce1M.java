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
 * M3 enhanced API snippet: a live filter box over a virtual table of 1,000,000 rows.
 *
 * The filter is asynchronous: each keystroke only records the pattern and increments a
 * generation; after 150 ms without typing a worker thread filters the rows (and sorts the
 * result), dropping its work as soon as a newer keystroke arrives; the newest result is applied
 * on the UI thread in one step. The UI thread never walks the rows, so typing stays fluent. The
 * selected rows stay selected through every result.
 */
public class Snippet_M3FilterDebounce1M {

	public static void main (String [] args) {
		int rows = 1_000_000;
		Random random = new Random (3);
		String [] ids = new String [rows];
		int [] sizes = new int [rows];
		for (int i = 0; i < rows; i++) {
			ids [i] = Long.toString (random.nextLong () & Long.MAX_VALUE, 36);
			sizes [i] = random.nextInt (100_000);
		}
		M3CellText text = (row, column) -> column == 0 ? ids [row] : String.valueOf (sizes [row]);

		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("Live filter over 1,000,000 rows");
		shell.setLayout (new GridLayout ());
		Text box = new Text (shell, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		box.setMessage ("Filter ids (contains, commas separate alternatives)");
		box.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		table.setHeaderVisible (true);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		for (String title : new String [] {"Id", "Size"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (title);
			column.setWidth (200);
		}
		Label status = new Label (shell, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.BOTTOM, true, false));

		M3ColumnFilter filter = new M3ColumnFilter (2, rows, text);
		M3ColumnSort sort = new M3ColumnSort (2, filter);
		sort.setKey (0, M3ColumnKey.ofText (r -> ids [r], null));
		sort.setKey (1, M3ColumnKey.ofInts (r -> sizes [r]));
		sort.install (table);
		M3RowBinding.bind (table, sort, (item, row) -> {
			item.setText (0, ids [row]);
			item.setText (1, String.valueOf (sizes [row]));
		});
		filter.setAsync (display, 150);
		filter.addListener (change -> status.setText (filter.size () + " of " + rows + " rows, generation " + filter.getAppliedGeneration ()));
		box.addListener (SWT.Modify, event -> {
			String pattern = box.getText ();
			filter.setText (0, pattern.isEmpty () ? null : pattern, M3TextMatch.CONTAINS | M3TextMatch.IGNORE_CASE);
			status.setText ("filtering... (generation " + filter.getGeneration () + ")");
		});
		box.setText ("zz");

		shell.setSize (480, 480);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		filter.setAsync (null, 0);
		display.dispose ();
	}
}
