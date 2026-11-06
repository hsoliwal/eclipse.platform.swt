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
 * The virtual Tree's vertical bar while its visible rows fit, as the official 3.135.0 jar reads it on win32
 * (TreeBarOracle, 2026-10-31): 0 for minimum, maximum, thumb and selection, hidden. The viewport mode read
 * maximum and thumb <code>rows - 1</code>; every mode must read the stock values, and a bar that scrolls is
 * unchanged.
 */
public class Test_org_eclipse_swt_widgets_Tree_HiddenBar {
	Shell shell;
	Tree tree;

	@BeforeEach
	public void setUp () {
		shell = new Shell (Display.getDefault ());
		shell.setBounds (10, 10, 300, 330);
		tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
		tree.setBounds (5, 5, 200, 280);
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private String bar () {
		ScrollBar bar = tree.getVerticalBar ();
		return bar.getMinimum () + "," + bar.getMaximum () + "," + bar.getThumb () + "," + bar.getSelection () + "," + bar.getVisible ();
	}

	@Test
	public void test_rowsThatFitReadZero () {
		for (int rows : new int [] {1, 5, 13, 14}) {
			tree.setItemCount (rows);
			assertEquals ("0,0,0,0,false", bar (), rows + " rows");
		}
		tree.removeAll ();
		for (int i = 0; i < 13; i++) new TreeItem (tree, SWT.NONE).setText ("x" + i);
		assertEquals ("0,0,0,0,false", bar (), "13 created rows");
	}

	@Test
	public void test_scrollingBarIsUnchangedAndCollapseFitsAgain () {
		tree.setItemCount (200);
		assertEquals ("0,199,14,0,true", bar ());
		tree.setItemCount (13);
		assertEquals ("0,0,0,0,false", bar (), "shrunk to fit");
		tree.getItem (0).setItemCount (3);
		tree.getItem (0).setExpanded (true);
		assertEquals ("0,15,14,0,true", bar (), "16 visible rows");
		tree.getItem (0).setExpanded (false);
		assertEquals ("0,0,0,0,false", bar (), "collapsed to 13");
	}
}
