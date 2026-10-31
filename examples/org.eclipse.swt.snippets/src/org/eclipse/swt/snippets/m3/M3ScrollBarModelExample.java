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
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Enhanced M3 API, row M3-SCROLLBAR-GEOMETRY: <code>M3ScrollBarModel</code>. A virtual table of 10,000,000
 * rows shows its vertical scroll bar model (the logical bar the viewport owns) in a label after every scroll,
 * read into one int lane the example keeps, so polling allocates nothing.
 * <p>
 * Arguments: <code>[exit]</code>: scroll to five positions, log the lanes and close.
 * </p>
 */
public class M3ScrollBarModelExample {
	static final Logger LOG = Logger.getLogger (M3ScrollBarModelExample.class.getName ());
	static final int ROWS = 10_000_000;
	static final int [] LANE = new int [M3ScrollBarModel.SIZE];

	static void report (Table table, Label status) {
		if (!M3ScrollBarModel.get (table, SWT.VERTICAL, LANE)) return;
		status.setText (String.format ("min %d max %d thumb %d increment %d page %d selection %d visible %d enabled %d",
			LANE [M3ScrollBarModel.MINIMUM], LANE [M3ScrollBarModel.MAXIMUM], LANE [M3ScrollBarModel.THUMB], LANE [M3ScrollBarModel.INCREMENT],
			LANE [M3ScrollBarModel.PAGE_INCREMENT], LANE [M3ScrollBarModel.SELECTION], LANE [M3ScrollBarModel.VISIBLE], LANE [M3ScrollBarModel.ENABLED]));
	}

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3ScrollBarModel");
		shell.setLayout (new GridLayout ());
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("row " + event.index));
		table.setItemCount (ROWS);
		Label status = new Label (shell, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		table.getVerticalBar ().addListener (SWT.Selection, event -> report (table, status));
		shell.setSize (560, 420);
		shell.open ();
		report (table, status);
		if (args.length > 0 && args [0].equals ("exit")) display.asyncExec (() -> exit (table, status, shell));
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static void exit (Table table, Label status, Shell shell) {
		for (int top : new int [] {0, 1000, ROWS / 2, ROWS - 100, 42}) {
			table.setTopIndex (top);
			report (table, status);
			LOG.info (status::getText);
		}
		shell.dispose ();
	}
}
