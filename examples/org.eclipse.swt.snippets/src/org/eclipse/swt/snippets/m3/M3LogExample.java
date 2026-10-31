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
import org.eclipse.swt.widgets.List;

/**
 * Enhanced M3 API: <code>M3Log</code>. The Tree's own debug records (logger <code>org.eclipse.swt.widgets.Tree</code>,
 * written by the tree viewport in viewport mode) are kept in a ring of 100; the button shows them, and the
 * application writes its own records to the same ring.
 */
public class M3LogExample {

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3Log");
	shell.setLayout (new GridLayout (2, true));
	M3Log log = M3Log.get ("org.eclipse.swt.widgets.Tree");
	log.setLevel (M3Log.DEBUG);
	log.keep (100);
	Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
	tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("node " + event.index));
	List events = new List (shell, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
	events.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	Button show = new Button (shell, SWT.PUSH);
	show.setText ("Count 1000, then show the kept records");
	show.addListener (SWT.Selection, e -> {
		tree.setItemCount (1000);
		log.log (M3Log.INFO, () -> "application: " + tree.getItemCount () + " rows");
		display.timerExec (200, () -> events.setItems (log.events ()));
	});
	shell.setSize (640, 400);
	shell.open ();
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}
}
