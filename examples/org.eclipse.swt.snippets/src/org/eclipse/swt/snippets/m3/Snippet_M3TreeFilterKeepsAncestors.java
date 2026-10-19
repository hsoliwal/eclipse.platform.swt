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
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/*
 * M3 enhanced API snippet: filtering a virtual tree keeps the ancestors of the matches.
 *
 * The tree is three arrays (name, parent, size) of a generated folder hierarchy. Typing in the
 * filter field keeps every folder or file whose name matches the wildcard pattern and every
 * ancestor of such a row, so each match stays reachable; siblings are sorted inside their
 * parent by name. Matching rows are expanded to the first level.
 */
public class Snippet_M3TreeFilterKeepsAncestors {

	public static void main (String [] args) {
		int fanOut = 12, depth = 4, rows = 0;
		int count = 0, level = 1;
		for (int d = 0; d < depth; d++) {
			level *= fanOut;
			count += level;
		}
		String [] name = new String [count];
		int [] parent = new int [count];
		int first = 0, width = fanOut;
		for (int d = 0; d < depth; d++) {
			for (int i = 0; i < width; i++) {
				int row = rows++;
				parent [row] = d == 0 ? -1 : first - width / fanOut + i / fanOut;
				name [row] = (d == depth - 1 ? "file-" : "folder-") + Integer.toString (row * 7919 % 1000, 36) + "-" + d;
			}
			first += width;
			width *= fanOut;
		}
		M3CellText text = (row, column) -> name [row];

		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("Tree filter keeps ancestors (" + count + " rows)");
		shell.setLayout (new GridLayout ());
		Text box = new Text (shell, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		box.setMessage ("Wildcard, e.g. file-a*");
		box.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		tree.setHeaderVisible (true);
		tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		TreeColumn column = new TreeColumn (tree, SWT.NONE);
		column.setText ("Name");
		column.setWidth (300);

		M3ColumnFilter filter = new M3ColumnFilter (1, count, text);
		filter.setParents (r -> parent [r]);
		M3ColumnSort sort = new M3ColumnSort (1, filter);
		sort.setParents (r -> parent [r]);
		sort.setCellText (text);
		sort.setSortKeys (new int [] {0}, new int [] {SWT.UP});
		sort.install (tree);
		M3RowBinding.bind (tree, sort, (item, row) -> item.setText (name [row]));
		filter.setAsync (display, 100);
		filter.addListener (change -> {
			for (TreeItem item : tree.getItems ()) item.setExpanded (box.getText ().length () > 0);
		});
		box.addListener (SWT.Modify, event -> filter.setText (0, box.getText ().isEmpty () ? null : box.getText (), M3TextMatch.WILDCARD | M3TextMatch.IGNORE_CASE));
		box.setText ("file-a*");

		shell.setSize (420, 520);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		filter.setAsync (null, 0);
		display.dispose ();
	}
}
