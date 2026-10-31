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
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;

/**
 * Enhanced M3 API: <code>M3Overscan</code>. A table of 100,000 rows holds 20 rows above and 60 below the rows in
 * view ready to paint; the label shows the rows in view and the resident window (the same in every widget mode),
 * and the log shows the prefetch ranges the overscan announces.
 */
public class M3OverscanExample {

static final int COUNT = 100_000;

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3Overscan");
	shell.setLayout (new GridLayout (2, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("row " + table.indexOf ((TableItem) event.item)));
	List log = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	GridData logData = new GridData (SWT.FILL, SWT.FILL, false, true);
	logData.widthHint = 220;
	log.setLayoutData (logData);
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
	table.setItemCount (COUNT);
	M3Overscan.set (table, 20, 60);
	M3Prefetch.addListener (table, event -> {
		log.add ((event.detail == M3Prefetch.PREFETCH ? "PREFETCH " : "CANCEL   ") + event.start + ".." + event.end);
		log.setTopIndex (log.getItemCount () - 1);
		status.setText ("in view " + M3ItemSelection.getVisibleRange (table) + ", resident " + M3Residency.residentRange (table));
	});
	shell.setSize (520, 420);
	shell.open ();
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}
}
