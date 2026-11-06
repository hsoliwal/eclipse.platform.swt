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

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * A virtual Table whose SetData listener makes the table paint again while a frame asks for its rows. In the drawn
 * modes (custom, m3, gpu) the nested paint ended the frame's retained-row pass under the outer frame, and the next row
 * threw a NullPointerException in RetainedRows.paint; every mode must paint every row and fill it.
 */
public class Test_org_eclipse_swt_widgets_Table_ReentrantPaint {
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (10, 10, 300, 330);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	@Test
	public void test_setDataThatPaintsAgainFillsEveryRow () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.setBounds (5, 5, 280, 280);
		int [] nested = new int [1];
		table.addListener (SWT.SetData, event -> {
			((TableItem) event.item).setText ("row " + event.index);
			/* paint again inside the frame that asked for the row */
			table.redraw ();
			table.update ();
			nested [0]++;
		});
		table.setItemCount (500);
		table.redraw ();
		table.update ();
		while (display.readAndDispatch ()) {
			// let deferred frames run
		}
		assertTrue (nested [0] > 0);
		for (int i = 0; i < 5; i++) assertEquals ("row " + i, table.getItem (i).getText ());
	}
}
