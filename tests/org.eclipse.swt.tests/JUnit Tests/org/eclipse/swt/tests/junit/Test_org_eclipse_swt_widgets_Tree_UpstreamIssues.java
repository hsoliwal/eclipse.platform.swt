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
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Upstream SWT issues the Tree viewport answers. Each test fails on stock SWT (the official jar, or this build with
 * <code>-Dswt.widget.mode.Tree=original</code>) and passes in the default mode; the measured times are in the
 * assertion messages and in <code>docs/m3/tree-viewport/UPSTREAM_DEFECTS.tsv</code>.
 * <ul>
 * <li>eclipse.platform.swt#882 (and #904): <code>TreeItem.getItemCount ()</code> and <code>TreeItem.getItem (int)</code>
 * have linear complexity. Stock win32 walks the native siblings (TVGN_NEXT) for every call that its one-entry cache
 * does not answer; the viewport keeps the count and the child order in lanes.</li>
 * <li>eclipse.platform.swt#208: UI freezes in <code>setItemCount</code>. Stock win32 inserts or deletes one native
 * item per row; the viewport keeps the count in a lane and materializes only the rows in view.</li>
 * </ul>
 */
public class Test_org_eclipse_swt_widgets_Tree_UpstreamIssues {
	static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Tree_UpstreamIssues.class.getName ());
	/* a budget stock SWT misses by one or two orders of magnitude and the viewport meets by two */
	static final long BUDGET_MS = 250;
	static final int CHILDREN = 200_000, ROOTS = 1_000_000;

	Display display;
	Shell shell;
	Tree tree;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
		tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
		tree.setBounds (5, 5, 300, 380);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			item.setText ((parent == null ? "root " : parent.getText () + " / ") + event.index);
		});
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	static long millis (long startNanos) {
		return (System.nanoTime () - startNanos) / 1_000_000;
	}

	/** eclipse.platform.swt#882, #904: the count and an indexed child of a row with 200,000 children, 200 times each, alternating two rows. */
	@Test
	public void getItemCountAndGetItemAreNotLinearInTheChildCount () {
		tree.setItemCount (2);
		TreeItem a = tree.getItem (0), b = tree.getItem (1);
		a.setItemCount (CHILDREN);
		b.setItemCount (CHILDREN);
		tree.update ();
		long start = System.nanoTime ();
		int count = 0;
		for (int i = 0; i < 100; i++) {
			/* two rows in turn: stock's one-entry cache (cachedFirstItem) answers neither */
			count += a.getItemCount () + b.getItemCount ();
		}
		long counting = millis (start);
		assertEquals (2 * 100 * CHILDREN, count);
		start = System.nanoTime ();
		TreeItem last = null;
		for (int i = 0; i < 100; i++) {
			last = a.getItem (CHILDREN - 1 - i);
			last = b.getItem (CHILDREN - 1 - i);
		}
		long indexing = millis (start);
		assertEquals ("root 1 / " + (CHILDREN - 100), last.getText ());
		LOG.info (() -> "swt#882: 200 x getItemCount " + counting + " ms, 200 x getItem (n - i) " + indexing + " ms, " + CHILDREN + " children");
		assertTrue (counting < BUDGET_MS, "200 x TreeItem.getItemCount over " + CHILDREN + " children took " + counting + " ms (eclipse.platform.swt#882)");
		assertTrue (indexing < BUDGET_MS, "200 x TreeItem.getItem (n - i) over " + CHILDREN + " children took " + indexing + " ms (eclipse.platform.swt#882)");
	}

	/** eclipse.platform.swt#208 (Table; the Tree counterpart): a million roots, a shrink to ten, and 200,000 children of one row. */
	@Test
	public void setItemCountIsNotLinearInTheCount () {
		long start = System.nanoTime ();
		tree.setItemCount (ROOTS);
		long grow = millis (start);
		assertEquals (ROOTS, tree.getItemCount ());
		assertEquals ("root " + (ROOTS - 1), tree.getItem (ROOTS - 1).getText ());
		start = System.nanoTime ();
		tree.setItemCount (10);
		long shrink = millis (start);
		assertEquals (10, tree.getItemCount ());
		TreeItem root = tree.getItem (3);
		start = System.nanoTime ();
		root.setItemCount (CHILDREN);
		long children = millis (start);
		assertEquals (CHILDREN, root.getItemCount ());
		LOG.info (() -> "swt#208: setItemCount (" + ROOTS + ") " + grow + " ms, setItemCount (10) " + shrink + " ms, item.setItemCount (" + CHILDREN + ") " + children + " ms");
		assertTrue (grow < BUDGET_MS, "Tree.setItemCount (" + ROOTS + ") took " + grow + " ms (eclipse.platform.swt#208)");
		assertTrue (shrink < BUDGET_MS, "Tree.setItemCount (10) from " + ROOTS + " took " + shrink + " ms (eclipse.platform.swt#208)");
		assertTrue (children < BUDGET_MS, "TreeItem.setItemCount (" + CHILDREN + ") took " + children + " ms (eclipse.platform.swt#208)");
	}
}
