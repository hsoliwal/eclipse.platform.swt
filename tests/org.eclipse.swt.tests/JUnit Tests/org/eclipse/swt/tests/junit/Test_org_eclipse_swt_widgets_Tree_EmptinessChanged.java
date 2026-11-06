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
 * <code>SWT.EmptinessChanged</code> of a virtual Tree as the official 3.135.0 jar sends it on win32
 * (TreeEmptinessOracle, 2026-10-31). Stock sends detail 0 when a created item (not a row materialized by
 * <code>getItem</code> or by painting) gets item id 0, and detail 1 when the last row goes (dispose,
 * <code>setItemCount</code>). <code>removeAll</code> sends nothing, keeps the next-id cursor and resets the id
 * table to four slots, so the first item created after it may or may not get id 0, and an item created in a
 * tree that is not empty gets id 0 (detail 0 again) when id 0 is free. Every mode must send the stock events.
 */
public class Test_org_eclipse_swt_widgets_Tree_EmptinessChanged {
	Shell shell;
	Tree tree;
	final StringBuilder events = new StringBuilder ();

	@BeforeEach
	public void setUp () {
		shell = new Shell (Display.getDefault ());
		shell.setBounds (10, 10, 300, 330);
		tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
		tree.setBounds (5, 5, 200, 280);
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
		tree.addListener (SWT.EmptinessChanged, event -> events.append ('d').append (event.detail).append (' '));
		shell.open ();
		ViewportDifferential.drain (shell.getDisplay ());
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private void step (String expected, Runnable action, String label) {
		events.setLength (0);
		action.run ();
		assertEquals (expected, events.toString ().trim (), label);
	}

	private void create (String expected, String label) {
		step (expected, () -> new TreeItem (tree, SWT.NONE), label);
	}

	@Test
	public void test_removeAllKeepsTheIdCursor () {
		create ("d0", "first item");
		create ("", "second item");
		step ("", () -> new TreeItem (tree, SWT.NONE, 0), "inserted first");
		step ("", tree::removeAll, "removeAll sends nothing");
		create ("", "first item after removeAll gets id 3");
		create ("d0", "second item after removeAll gets id 0");
		step ("", () -> new TreeItem (tree.getItem (0), SWT.NONE), "child");
		step ("", () -> tree.getItem (0).dispose (), "dispose a root, one left");
		step ("", () -> new TreeItem (tree.getItem (0), SWT.NONE), "child");
		step ("d1", () -> tree.getItem (0).dispose (), "dispose the last root");
		create ("d0", "first item of an empty tree");
		assertEquals (1, tree.getItemCount ());
	}

	@Test
	public void test_materializedRowsTakeIdsWithoutEvents () {
		create ("d0", "first item");
		step ("", () -> tree.setItemCount (3), "two cold rows");
		step ("", () -> tree.getItem (2), "materialized by getItem");
		step ("", () -> tree.getItem (2).dispose (), "dispose a materialized row");
		create ("", "created item takes the freed id");
		step ("", tree::removeAll, "removeAll");
		step ("", () -> tree.setItemCount (2), "cold rows");
		step ("", () -> ViewportDifferential.drain (shell.getDisplay ()), "painting materializes the rows");
		create ("d0", "created item after painted rows gets id 0");
		step ("d1", () -> tree.setItemCount (0), "count 0");
	}

	@Test
	public void test_childrenAndColdRowsKeepTheirIds () {
		step ("d0", () -> {
			tree.setRedraw (false);
			for (int i = 0; i < 5; i++) new TreeItem (tree, SWT.NONE);
			tree.setRedraw (true);
		}, "five items with redraw off");
		ViewportDifferential.drain (shell.getDisplay ());
		step ("", () -> tree.getItem (1).setItemCount (3), "cold children");
		step ("", () -> tree.getItem (1).getItem (0), "materialized child");
		step ("", () -> tree.getItem (1).removeAll (), "item removeAll");
		step ("", () -> new TreeItem (tree.getItem (1), SWT.NONE), "child");
		step ("", () -> tree.getItem (0).dispose (), "dispose root id 0");
		create ("d0", "created item in a tree that is not empty gets id 0");
		step ("", tree::removeAll, "removeAll");
		step ("", tree::removeAll, "removeAll of an empty tree");
		step ("", () -> tree.setItemCount (1), "one cold row");
		create ("", "id 1");
		create ("", "id 2");
		create ("", "id 3");
		create ("d0", "the cold row holds no id: id 0");
		step ("d1", () -> tree.setItemCount (0), "count 0");
		create ("d0", "first item of an empty tree");
	}
}
