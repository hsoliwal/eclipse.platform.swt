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

import java.util.Locale;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * List viewport, example 1: a List of 1,000,000 items, and changes to it.
 * <p>
 * <code>setItems</code> fills the List with 1,000,000 strings. On win32 the default mode keeps them in the
 * viewport's model and gives the native list box only the rows around the visible ones, so the top, the selection,
 * an insert or a removal cost about the same at row 10 as at row 999,999. The buttons jump, select far away, insert
 * one item near the top and remove 1,000 items from the middle; the status line shows what each call took. Run with
 * <code>-Dswt.widget.mode.List=original</code> for the stock code path, where the list box holds every string.
 * </p>
 */
public class M3ListMillionItemsExample {

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
	shell.setText ("M3 List: 1,000,000 items");
	shell.setLayout (new GridLayout (3, true));
	List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
	GridData listData = new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1);
	listData.widthHint = 360;
	listData.heightHint = 260;
	list.setLayoutData (listData);
	String [] items = new String [COUNT];
	for (int i = 0; i < COUNT; i++) items [i] = "Item " + String.format (Locale.ROOT, "%,d", i);
	Label status = new Label (shell, SWT.NONE);
	long millis = timed (() -> list.setItems (items));
	button (shell, status, list, "First", () -> list.setTopIndex (0));
	button (shell, status, list, "Middle", () -> list.setTopIndex (COUNT / 2));
	button (shell, status, list, "Last", () -> list.setTopIndex (list.getItemCount () - 1));
	button (shell, status, list, "Select 999,990", () -> {
		list.setSelection (COUNT - 10);
		list.showSelection ();
	});
	button (shell, status, list, "Insert at 10", () -> list.add ("Inserted", 10));
	button (shell, status, list, "Remove 1,000", () -> list.remove (COUNT / 2, COUNT / 2 + 999));
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	status.moveBelow (null);
	show (status, list, "setItems", millis);
	shell.pack ();
	shell.open ();
	return shell;
}

static long timed (Runnable action) {
	long start = System.nanoTime ();
	action.run ();
	return (System.nanoTime () - start) / 1_000_000;
}

static void show (Label status, List list, String what, long millis) {
	status.setText (String.format (Locale.ROOT, "%s took %d ms: %,d items, top %,d, selected %s", what, millis, list.getItemCount (),
			list.getTopIndex (), list.getSelectionCount () == 0 ? "none" : String.format (Locale.ROOT, "%,d", list.getSelectionIndex ())));
	status.getParent ().layout ();
}

static void button (Shell shell, Label status, List list, String text, Runnable action) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	button.addListener (SWT.Selection, event -> show (status, list, text, timed (action)));
}
}
