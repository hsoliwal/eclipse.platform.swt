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
import org.eclipse.swt.widgets.*;

/**
 * Combo viewport, example 1: a read-only and an editable Combo of 1,000,000 items.
 * <p>
 * On win32 the default mode keeps the items of a drop-down Combo in the viewport's model; the combo's list box
 * holds the rows around the selection and, while the list is dropped, around the visible rows. The buttons select far
 * away, drop the lists and type a prefix into the editable combo; the status line shows what each call took and what
 * the combos answer. Run with <code>-Dswt.widget.mode.Combo=original</code> for the stock code path.
 * </p>
 */
public class M3ComboMillionItemsExample {

static final int COUNT = 1_000_000;

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
	shell.setText ("M3 Combo: 1,000,000 items");
	shell.setLayout (new GridLayout (3, true));
	String [] items = new String [COUNT];
	for (int i = 0; i < COUNT; i++) items [i] = String.format (Locale.ROOT, "City %07d", i);
	Combo readOnly = combo (shell, SWT.DROP_DOWN | SWT.READ_ONLY);
	Combo editable = combo (shell, SWT.DROP_DOWN);
	Label status = new Label (shell, SWT.WRAP);
	long millis = M3ListMillionItemsExample.timed (() -> {
		readOnly.setItems (items);
		editable.setItems (items);
	});
	button (shell, status, readOnly, editable, "Select 999,999", () -> readOnly.select (COUNT - 1));
	button (shell, status, readOnly, editable, "Select 500,000", () -> readOnly.select (COUNT / 2));
	button (shell, status, readOnly, editable, "Drop", () -> readOnly.setListVisible (true));
	button (shell, status, readOnly, editable, "Close", () -> readOnly.setListVisible (false));
	button (shell, status, readOnly, editable, "Type 'City 07'", () -> editable.setText ("City 07"));
	button (shell, status, readOnly, editable, "Find 'City 0765432'", () -> editable.select (editable.indexOf ("City 0765432")));
	GridData statusData = new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1);
	statusData.widthHint = 380;
	status.setLayoutData (statusData);
	status.moveBelow (null);
	show (status, readOnly, editable, "setItems (twice)", millis);
	shell.pack ();
	shell.open ();
	return shell;
}

static Combo combo (Shell shell, int style) {
	Combo combo = new Combo (shell, style);
	combo.setVisibleItemCount (12);
	combo.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false, 3, 1));
	return combo;
}

static void show (Label status, Combo readOnly, Combo editable, String what, long millis) {
	status.setText (String.format (Locale.ROOT, "%s took %d ms. Read-only: %,d items, selected %,d '%s', dropped %b. Editable: '%s', index %,d",
			what, millis, readOnly.getItemCount (), readOnly.getSelectionIndex (), readOnly.getText (), readOnly.getListVisible (),
			editable.getText (), editable.getSelectionIndex ()));
	status.getParent ().layout ();
}

static void button (Shell shell, Label status, Combo readOnly, Combo editable, String text, Runnable action) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	button.addListener (SWT.Selection, event -> show (status, readOnly, editable, text, M3ListMillionItemsExample.timed (action)));
}
}
