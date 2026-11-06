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
 * The List's hidden vertical bar after a reset and one row, as the official 3.135.0 jar reads it on win32
 * (measured with ListBarOracle, 2026-10-31): minimum, maximum, thumb and selection all 0. The viewport
 * mode read maximum 100 (a default-bar rule that the stock list box does not show); every mode must read 0.
 */
public class Test_org_eclipse_swt_widgets_List_HiddenBar {
	Shell shell;
	List list;

	@BeforeEach
	public void setUp () {
		shell = new Shell (Display.getDefault ());
		shell.setBounds (10, 10, 300, 300);
		list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 200, 280);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private String bar () {
		ScrollBar bar = list.getVerticalBar ();
		return bar.getMinimum () + "," + bar.getMaximum () + "," + bar.getThumb () + "," + bar.getSelection () + "," + bar.getVisible ();
	}

	private void fill (int rows) {
		for (int i = 0; i < rows; i++) list.add ("r" + i);
	}

	@Test
	public void test_oneRowAfterAResetReadsZero () {
		list.add ("a");
		assertEquals ("0,0,0,0,false", bar (), "first row of a new list");
		list.removeAll ();
		list.add ("b", 0);
		assertEquals ("0,0,0,0,false", bar (), "insert after removeAll");
		list.removeAll ();
		fill (40);
		list.removeAll ();
		list.add ("z");
		assertEquals ("0,0,0,0,false", bar (), "add after a shown bar and removeAll");
		list.remove (0);
		fill (40);
		list.remove (0, 39);
		list.add ("v");
		assertEquals ("0,0,0,0,false", bar (), "add after removing every row");
	}

	@Test
	public void test_aListWithAHorizontalBarReadsTheDefaultBar () {
		/* the official jar: with WS_HSCROLL the first row leaves the default bar 0..100 */
		List wide = new List (shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		wide.setBounds (210, 5, 80, 280);
		wide.add ("a");
		ScrollBar bar = wide.getVerticalBar ();
		assertEquals ("0,100,0,0,false", bar.getMinimum () + "," + bar.getMaximum () + "," + bar.getThumb () + "," + bar.getSelection () + "," + bar.getVisible ());
		wide.removeAll ();
		wide.add ("b", 0);
		assertEquals (100, bar.getMaximum ());
	}

	@Test
	public void test_twoRowsAndAShownBarAreUnchanged () {
		list.add ("x");
		list.add ("y");
		assertEquals ("0,1,1,0,false", bar ());
		list.removeAll ();
		fill (40);
		assertEquals (39, list.getVerticalBar ().getMaximum ());
		assertTrue (list.getVerticalBar ().getVisible ());
	}
}
