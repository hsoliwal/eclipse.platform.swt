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

/**
 * Table viewport, example 3: the row math of a Table, computed from public API and checked against the widget.
 * <p>
 * All rows of a Table have one height <code>h</code> (<code>getItemHeight</code>). Below the header (height
 * <code>H</code>, <code>getHeaderHeight</code>) of a client area of height <code>A</code>:
 * </p>
 * <ul>
 * <li>a page is <code>P = (A - H) / h</code> whole rows, and <code>ceil ((A - H) / h)</code> rows are painted
 * (Snippet253 counts these);</li>
 * <li>row <code>r</code> starts at <code>y = H + (r - top) * h</code>, and the row at <code>y</code> is
 * <code>top + (y - H) / h</code> (Snippet3 asks <code>getItem (Point)</code>);</li>
 * <li><code>setTopIndex (n)</code> ends on <code>max (0, min (n, count - P))</code>: the last page stays full;</li>
 * <li>the vertical bar counts rows: maximum <code>count - 1</code>, thumb <code>P</code>, selection
 * <code>top</code>.</li>
 * </ul>
 * <p>
 * "Check" sets several top rows of a 1,000,000-row Table and compares every formula with the widget's answer;
 * moving the mouse over the rows compares the row at the pointer. Run with
 * <code>-Dswt.widget.mode.Table=original</code> for the stock code path, which answers the same.
 * </p>
 */
public class M3TableRowMathExample {

static final int COUNT = 1_000_000;

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = open (display);
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

/** Builds and opens the example's shell (the screenshot harness of docs/m3/table-viewport calls it too). */
public static Shell open (Display display) {
	Shell shell = new Shell (display);
	shell.setText ("M3 Table: row math");
	shell.setLayout (new GridLayout (2, false));
	Table table = new Table (shell, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
	table.setHeaderVisible (true);
	table.setLinesVisible (true);
	GridData tableData = new GridData (SWT.FILL, SWT.FILL, false, true, 1, 2);
	tableData.widthHint = 200;
	tableData.heightHint = 300;
	table.setLayoutData (tableData);
	TableColumn column = new TableColumn (table, SWT.NONE);
	column.setText ("Row");
	column.setWidth (160);
	table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText (String.format (Locale.ROOT, "row %,d", event.index)));
	table.setItemCount (COUNT);
	Text report = new Text (shell, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL);
	GridData reportData = new GridData (SWT.FILL, SWT.FILL, true, true);
	reportData.widthHint = 440;
	report.setLayoutData (reportData);
	report.setFont (new Font (display, "Consolas", 9, SWT.NORMAL));
	report.addListener (SWT.Dispose, event -> report.getFont ().dispose ());
	Button check = new Button (shell, SWT.PUSH);
	check.setText ("Check");
	check.addListener (SWT.Selection, event -> report.setText (check (table)));
	table.addListener (SWT.MouseMove, event -> shell.setText ("M3 Table: row math, " + pointer (table, event.y)));
	shell.pack ();
	shell.open ();
	report.setText (check (table));
	return shell;
}

/** Sets several top rows and answers one line per formula and top row: computed, answered, ok or DIFF. */
public static String check (Table table) {
	StringBuilder out = new StringBuilder ();
	int h = table.getItemHeight (), header = table.getHeaderHeight (), area = table.getClientArea ().height;
	int page = (area - header) / h, painted = (area - header + h - 1) / h, count = table.getItemCount ();
	out.append (String.format (Locale.ROOT, "h %d, H %d, A %d: P = %d whole rows, %d painted%n", h, header, area, page, painted));
	ScrollBar bar = table.getVerticalBar ();
	line (out, "bar maximum = count - 1", count - 1, bar.getMaximum ());
	line (out, "bar thumb = P", page, bar.getThumb ());
	for (int request : new int [] {0, 1, 12_345, count / 2, count - page, count - 1}) {
		table.setTopIndex (request);
		int top = Math.max (0, Math.min (request, count - page));
		line (out, "setTopIndex (" + request + ") -> top", top, table.getTopIndex ());
		line (out, "  bar selection = top", top, bar.getSelection ());
		int last = Math.min (count, top + painted) - 1;
		line (out, "  y of row " + last, header + (last - top) * h, table.getItem (last).getBounds ().y);
		int y = header + (painted / 2) * h + h / 2;
		line (out, "  row at y " + y, top + (y - header) / h, table.indexOf (table.getItem (new Point (4, y))));
	}
	return out.toString ();
}

static void line (StringBuilder out, String formula, int computed, int answered) {
	out.append (String.format (Locale.ROOT, "%-34s %,9d %,9d  %s%n", formula, computed, answered, computed == answered ? "ok" : "DIFF"));
}

static String pointer (Table table, int y) {
	int header = table.getHeaderHeight (), h = table.getItemHeight ();
	if (y < header) return "header";
	int computed = table.getTopIndex () + (y - header) / h;
	TableItem item = table.getItem (new Point (4, y));
	return String.format (Locale.ROOT, "row at y %d: %,d computed, %s answered", y, computed, item == null ? "none" : String.format (Locale.ROOT, "%,d", table.indexOf (item)));
}
}
