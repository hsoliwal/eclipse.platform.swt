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
import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * List viewport, example 2: fast scrolling through 1,000,000 items.
 * <p>
 * Each button scrolls the List many times and paints after every step (<code>update</code>), as a user holding
 * Page Down or dragging the thumb would see it: 1,000 pages down, 1,000 jumps of 997 rows, or 1,000 random rows.
 * The status line shows the time per step. In the default mode on win32 a page moves only the rows that enter and
 * leave the list box's window; a jump far away replaces the window (about two pages of rows) instead of the stock
 * list box scrolling through all of its strings. Run with <code>-Dswt.widget.mode.List=original</code> to compare.
 * </p>
 */
public class M3ListFastScrollExample {

static final int COUNT = 1_000_000, STEPS = 1_000;

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
	shell.setText ("M3 List: fast scrolling, 1,000,000 items");
	shell.setLayout (new GridLayout (3, true));
	List list = new List (shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
	GridData listData = new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1);
	listData.widthHint = 360;
	listData.heightHint = 260;
	list.setLayoutData (listData);
	String [] items = new String [COUNT];
	for (int i = 0; i < COUNT; i++) items [i] = "Row " + String.format (Locale.ROOT, "%,d", i);
	list.setItems (items);
	Label status = new Label (shell, SWT.NONE);
	int page = Math.max (1, list.getClientArea ().height / list.getItemHeight ());
	button (shell, status, list, "1,000 pages", step -> (list.getTopIndex () + page) % COUNT);
	button (shell, status, list, "1,000 jumps", step -> (list.getTopIndex () + 997) % COUNT);
	Random random = new Random (42);
	button (shell, status, list, "1,000 random", step -> random.nextInt (COUNT));
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 3, 1));
	status.moveBelow (null);
	status.setText ("Press a button: each scrolls 1,000 times and paints after every step");
	shell.pack ();
	shell.open ();
	return shell;
}

interface Step {
	int top (int step);
}

static void button (Shell shell, Label status, List list, String text, Step next) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	button.addListener (SWT.Selection, event -> {
		long start = System.nanoTime ();
		for (int step = 0; step < STEPS; step++) {
			list.setTopIndex (next.top (step));
			list.update ();
		}
		double micros = (System.nanoTime () - start) / 1_000.0 / STEPS;
		status.setText (String.format (Locale.ROOT, "%s: %.1f us per scroll and paint, top now %,d", text, micros, list.getTopIndex ()));
		shell.layout ();
	});
}
}
