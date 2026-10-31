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
 * Enhanced M3 API, row M3-SNAPSHOT: <code>M3Snapshot</code>. "Capture" records the table's state and pixel
 * CRC; after scrolling or selecting, "Compare" names the first field that changed (the way a test compares
 * two runs).
 * <p>
 * Arguments: <code>[exit]</code>: capture, scroll, compare, log and close.
 * </p>
 */
public class M3SnapshotExample {
	static final Logger LOG = Logger.getLogger (M3SnapshotExample.class.getName ());
	static M3Snapshot captured;

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3Snapshot");
		shell.setLayout (new GridLayout (2, true));
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
		for (int i = 0; i < 1000; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		Label status = new Label (shell, SWT.WRAP);
		status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
		Button capture = new Button (shell, SWT.PUSH);
		capture.setText ("Capture");
		capture.addListener (SWT.Selection, event -> {
			captured = M3Snapshot.capture (table);
			status.setText ("captured: visible " + captured.visibleRange () + ", pixels " + Long.toHexString (captured.pixels ()));
		});
		Button compare = new Button (shell, SWT.PUSH);
		compare.setText ("Compare");
		compare.addListener (SWT.Selection, event -> status.setText (compare (table)));
		shell.setSize (420, 480);
		shell.open ();
		if (args.length > 0 && args [0].equals ("exit")) display.asyncExec (() -> exit (table, capture, compare, status, shell));
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static String compare (Table table) {
		if (captured == null) return "capture first";
		String difference = captured.diff (M3Snapshot.capture (table));
		return difference == null ? "no change" : "first change: " + difference;
	}

	static void exit (Table table, Button capture, Button compare, Label status, Shell shell) {
		capture.notifyListeners (SWT.Selection, new Event ());
		table.setTopIndex (500);
		compare.notifyListeners (SWT.Selection, new Event ());
		LOG.info (status::getText);
		shell.dispose ();
	}
}
