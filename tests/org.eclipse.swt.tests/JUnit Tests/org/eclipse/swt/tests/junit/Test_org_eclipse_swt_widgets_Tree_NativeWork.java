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

import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Native work per operation must be O(window): the TreeView messages (TVM_*) an operation sends must not grow with
 * the model. Each operation runs at a small and a ten times larger size; the larger may send at most twice the
 * messages of the smaller plus a constant. Stock SWT (the official jar or <code>-Dswt.widget.mode.Tree=original</code>)
 * inserts one native item per logical row and fails; the default mode (viewport) keeps only the rows in view.
 * The counts and times are logged for docs/m3/tree-viewport/OPTIMIZATION_LEDGER.tsv.
 */
public class Test_org_eclipse_swt_widgets_Tree_NativeWork {
	static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Tree_NativeWork.class.getName ());
	static final int SMALL = Integer.getInteger ("m3.nativework.small", 10_000), LARGE = Integer.getInteger ("m3.nativework.large", 100_000);

	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	Tree tree () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
		tree.setBounds (5, 5, 300, 380);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			item.setText ((parent == null ? "root " : parent.getText () + " / ") + event.index);
		});
		return tree;
	}

	/** messages, then milliseconds */
	long [] measure (Tree tree, Runnable operation, String name) {
		tree.update ();
		long nanos;
		long messages;
		String breakdown;
		try (NativeMessageCounter counter = new NativeMessageCounter (tree.handle, OS.TV_FIRST, OS.TV_FIRST + 0xFF)) {
			long start = System.nanoTime ();
			operation.run ();
			tree.update ();
			nanos = System.nanoTime () - start;
			messages = counter.count ();
			breakdown = counter.breakdown ();
		}
		long millis = nanos / 1_000_000;
		LOG.info (() -> name + ": " + messages + " TVM messages, " + millis + " ms (" + breakdown + ")");
		return new long [] {messages, millis};
	}

	static void assertWindowBound (long [] small, long [] large, String operation) {
		assertTrue (large [0] <= 2 * small [0] + 64, operation + ": " + large [0] + " TVM messages at " + LARGE + " against " + small [0] + " at " + SMALL
			+ " (native work grows with the model)");
	}

	/** Tree.setItemCount on the roots, from empty. */
	@Test
	public void rootSetItemCountIsWindowBound () {
		Tree tree = tree ();
		long [] small = measure (tree, () -> tree.setItemCount (SMALL), "setItemCount " + SMALL);
		tree.dispose ();
		Tree tree2 = tree ();
		long [] large = measure (tree2, () -> tree2.setItemCount (LARGE), "setItemCount " + LARGE);
		assertEquals (LARGE, tree2.getItemCount ());
		assertWindowBound (small, large, "Tree.setItemCount");
	}

	/** TreeItem.setItemCount, then expand and collapse that row, with the row at the top. */
	@Test
	public void expandAndCollapseOfALargeSubtreeAreWindowBound () {
		long [][] results = new long [2][];
		for (int pass = 0; pass < 2; pass++) {
			int children = pass == 0 ? SMALL : LARGE;
			Tree tree = tree ();
			tree.setItemCount (50);
			TreeItem root = tree.getItem (3);
			long [] count = measure (tree, () -> root.setItemCount (children), "item.setItemCount " + children);
			long [] expand = measure (tree, () -> root.setExpanded (true), "expand " + children);
			long [] scroll = measure (tree, () -> tree.setTopItem (root.getItem (children / 2)), "setTopItem middle of " + children);
			long [] collapse = measure (tree, () -> root.setExpanded (false), "collapse " + children);
			assertEquals (children, root.getItemCount ());
			results [pass] = new long [] {count [0] + expand [0] + scroll [0] + collapse [0], count [1] + expand [1] + scroll [1] + collapse [1]};
			tree.dispose ();
		}
		assertWindowBound (results [0], results [1], "TreeItem.setItemCount + expand + setTopItem + collapse");
	}
}
