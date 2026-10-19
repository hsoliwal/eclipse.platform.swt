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
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Combo viewport, example 2: a read-only Combo whose Verify listener guards the choice.
 * <p>
 * A read-only Combo sends <code>SWT.Verify</code> with the text an item would show before it changes the
 * selection. This listener refuses the retired units (<code>event.doit = false</code>: the selection stays) and
 * replaces an alias by the unit it stands for (<code>event.text</code>: the Combo then selects that item). Each
 * decision is logged with java.util.logging and shown in the status line. The Combo has 100,000 units, so in the
 * default mode on win32 the viewport answers for items far outside the combo's native window as stock SWT does.
 * </p>
 */
public class M3ComboVerifyExample {

static final Logger LOG = Logger.getLogger (M3ComboVerifyExample.class.getName ());
static final int COUNT = 100_000;

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = open (display);
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

/** Builds and opens the example's shell (the screenshot harness of docs/m3/list-viewport calls it too). */
public static Shell open (Display display) {
	Shell shell = new Shell (display);
	shell.setText ("M3 Combo: Verify on a read-only Combo");
	shell.setLayout (new GridLayout (3, true));
	Combo combo = new Combo (shell, SWT.DROP_DOWN | SWT.READ_ONLY);
	combo.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false, 3, 1));
	String [] items = new String [COUNT];
	for (int i = 0; i < COUNT; i++) items [i] = unit (i);
	combo.setItems (items);
	combo.select (0);
	Label status = new Label (shell, SWT.WRAP);
	combo.addListener (SWT.Verify, event -> verify (combo, status, event));
	button (shell, combo, "Unit 50,000", COUNT / 2);
	button (shell, combo, "Retired unit 90,007", 90_007);
	button (shell, combo, "Alias 99,999", COUNT - 1);
	GridData statusData = new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1);
	statusData.widthHint = 380;
	status.setLayoutData (statusData);
	status.moveBelow (null);
	status.setText ("Selected '" + combo.getText () + "'");
	shell.pack ();
	shell.open ();
	return shell;
}

/* every 7th unit is retired; the last one is an alias of unit 1 */
static String unit (int i) {
	if (i == COUNT - 1) return "Alias of Unit 000001";
	return String.format (Locale.ROOT, "Unit %06d%s", i, i % 7 == 0 && i > 0 ? " (retired)" : "");
}

static void verify (Combo combo, Label status, Event event) {
	String decision;
	if (event.text.endsWith ("(retired)")) {
		event.doit = false;
		decision = "refused '" + event.text + "'";
	} else if (event.text.startsWith ("Alias of ")) {
		decision = "replaced '" + event.text + "' by '" + event.text.substring (9) + "'";
		event.text = event.text.substring (9);
	} else {
		decision = "accepted '" + event.text + "'";
	}
	LOG.info (() -> "Verify " + decision);
	status.setData (decision);
}

static void button (Shell shell, Combo combo, String text, int index) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	button.addListener (SWT.Selection, event -> {
		Label status = (Label) shell.getChildren () [shell.getChildren ().length - 1];
		combo.select (index);
		status.setText (String.format (Locale.ROOT, "select (%,d): Verify %s; selected %,d '%s'", index, status.getData (),
				combo.getSelectionIndex (), combo.getText ()));
		shell.layout ();
	});
}
}
