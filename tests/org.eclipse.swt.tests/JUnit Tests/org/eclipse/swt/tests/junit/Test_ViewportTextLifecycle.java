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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.lang.reflect.Field;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Embedded viewport editors must release every native registration exactly once. */
public class Test_ViewportTextLifecycle {

private Display display;
private Field widgetTable;

@BeforeEach
public void setUp () throws Exception {
	assumeTrue ("gtk".equals (SWT.getPlatform ()));
	display = Display.getDefault ();
	widgetTable = Display.class.getDeclaredField ("widgetTable");
	widgetTable.setAccessible (true);
}

private int registrations () throws IllegalAccessException {
	int count = 0;
	for (Widget widget : (Widget[])widgetTable.get (display)) if (widget != null) count++;
	return count;
}

@Test
public void singleAndMultilineEditorsReturnNativeRegistrationsToBaseline () throws Exception {
	Shell shell = new Shell (display);
	try {
		int baseline = registrations ();
		for (int style : new int[] {SWT.SINGLE, SWT.SINGLE | SWT.BORDER, SWT.MULTI | SWT.V_SCROLL, SWT.MULTI | SWT.WRAP}) {
			for (int iteration = 0; iteration < 16; iteration++) {
				Text text = new Text (shell, style);
				text.setText ("viewport editor");
				text.setSelection (2, 7);
				assertEquals ("ewpor", text.getSelectionText ());
				assertDoesNotThrow (text::dispose, "every registered native owner must be removed once");
				assertTrue (text.isDisposed ());
				assertEquals (baseline, registrations (), "disposing an editor must not retain an unreachable widget slot");
			}
		}
	} finally {
		shell.dispose ();
	}
}

@Test
public void parentDisposalReleasesEditorsAndDispatchesOneDisposeEvent () throws Exception {
	int baseline = registrations ();
	Shell shell = new Shell (display);
	Text single = new Text (shell, SWT.SINGLE);
	Text multi = new Text (shell, SWT.MULTI);
	int[] events = {0};
	single.addDisposeListener (event -> events[0]++);
	multi.addDisposeListener (event -> events[0]++);
	try {
		assertDoesNotThrow (shell::dispose, "parent disposal must release embedded editors without duplicate removal");
		assertTrue (single.isDisposed () && multi.isDisposed ());
		assertEquals (2, events[0]);
		assertEquals (baseline, registrations ());
	} finally {
		if (!shell.isDisposed ()) shell.dispose ();
	}
}
}
