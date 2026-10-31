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

import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * Enhanced M3 API, rows M3-WIDTH-TRACK and M3-SHAPING-MEASURE: <code>M3WidthTrack</code> over
 * <code>M3TextMeasure</code>. A 20,000-row table sizes its "Product" column to the widest cell after
 * every edit. The first fit measures every text once; later fits measure only the texts an edit
 * made, so a fit after one edit costs one measurement and a scan of cached widths. The label shows
 * the widest row and the measuring counts.
 * <p>
 * Arguments: <code>[exit]</code>: make 50 edits, fit after each, log the counts and close.
 * </p>
 */
public class M3WidthTrackExample {
	static final Logger LOG = Logger.getLogger (M3WidthTrackExample.class.getName ());
	static final int ROWS = 20_000;
	static final String [] WORDS = {"apple", "Apricot", "banana", "Blueberry", "cherry", "kiwi", "Zucchini", "date"};
	static int edits;

	static String product (int i) {
		return WORDS [i % WORDS.length] + " " + "x".repeat (i % 23);
	}

	static void fit (Table table, Label status) {
		TableColumn column = table.getColumn (1);
		column.setWidth (M3WidthTrack.contentWidth (table, 1) + 16);
		long [] stats = M3WidthTrack.stats (table);
		status.setText (String.format ("widest row %d, width %d; measured %d, cached answers %d, %d edits",
			M3WidthTrack.widestRow (table, 1), M3WidthTrack.contentWidth (table, 1), stats [M3TextMeasure.MEASURED], stats [M3TextMeasure.HITS], edits));
	}

	static Table table (Shell shell) {
		Table table = new Table (shell, SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible (true);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
		for (String title : new String [] {"Row", "Product"}) new TableColumn (table, SWT.NONE).setText (title);
		table.getColumn (0).setWidth (70);
		for (int i = 0; i < ROWS; i++) new TableItem (table, SWT.NONE).setText (new String [] {Integer.toString (i), product (i)});
		return table;
	}

	static void edit (Table table, Label status) {
		int row = (edits * 7919) % ROWS;
		table.getItem (row).setText (1, product (row) + " edited ".repeat (1 + edits % 3));
		edits++;
		fit (table, status);
	}

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3WidthTrack");
		shell.setLayout (new GridLayout (2, false));
		Table table = table (shell);
		Label status = new Label (shell, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		Button button = new Button (shell, SWT.PUSH);
		button.setText ("Edit a row and fit");
		button.addListener (SWT.Selection, event -> edit (table, status));
		fit (table, status);
		shell.setSize (620, 480);
		shell.open ();
		if (args.length > 0 && args [0].equals ("exit")) display.asyncExec (() -> exit (table, status, shell));
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static void exit (Table table, Label status, Shell shell) {
		for (int i = 0; i < 50; i++) edit (table, status);
		LOG.info (status::getText);
		shell.dispose ();
	}
}
