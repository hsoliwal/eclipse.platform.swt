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
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Tree viewport, example 1: a virtual Tree of 1,000,000 roots.
 * <p>
 * Every tenth root has 1,000 children and every fifth of those has 100 grandchildren, so expanding opens up to
 * another 100,000 rows. Rows get their data from <code>SWT.SetData</code> when they first show (the pattern of
 * Snippet202). The buttons jump to any root, expand or collapse it, and scroll smoothly one row per frame. On win32
 * the default mode (viewport) keeps only the rows in view as native rows, so <code>setItemCount (1_000_000)</code>
 * and <code>getItem (int)</code> anywhere take about a millisecond; the status line shows the measured times. Run with
 * <code>-Dswt.widget.mode.Tree=original</code> for the stock code path, which is slower by orders of magnitude.
 * </p>
 */
public class M3TreeMillionNodesExample {

static final Logger LOG = Logger.getLogger (M3TreeMillionNodesExample.class.getName ());
static final int ROOTS = 1_000_000, CHILDREN = 1_000, GRANDCHILDREN = 100;

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = open (display);
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

/** Builds and opens the example's shell (the screenshot harness of docs/m3/tree-viewport calls it too). */
public static Shell open (Display display) {
	Shell shell = new Shell (display);
	shell.setText ("M3 Tree: 1,000,000 roots, SWT.VIRTUAL");
	shell.setLayout (new GridLayout (5, false));
	Text row = new Text (shell, SWT.BORDER);
	row.setText ("500000");
	row.setLayoutData (new GridData (80, SWT.DEFAULT));
	Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
	Label status = new Label (shell, SWT.NONE);
	button (shell, "Go to root", () -> {
		TreeItem item = tree.getItem (root (row, tree));
		tree.setTopItem (item);
		tree.setSelection (item);
		status (status, "top " + tree.getTopItem ().getText ());
	});
	button (shell, "Expand", () -> expand (tree, root (row, tree), true, status));
	button (shell, "Collapse", () -> expand (tree, root (row, tree), false, status));
	Button scroll = new Button (shell, SWT.TOGGLE);
	scroll.setText ("Smooth scroll");
	scroll.addListener (SWT.Selection, event -> {
		if (scroll.getSelection ()) scrollStep (tree, scroll);
	});
	/* the layout follows the children's order: the buttons first, then the tree, then the status line */
	tree.moveBelow (scroll);
	status.moveBelow (tree);
	GridData treeData = new GridData (SWT.FILL, SWT.FILL, true, true, 5, 1);
	treeData.widthHint = 420;
	treeData.heightHint = 360;
	tree.setLayoutData (treeData);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 5, 1));
	tree.addListener (SWT.SetData, event -> fill ((TreeItem) event.item, event.index));
	long start = System.nanoTime ();
	tree.setItemCount (ROOTS);
	long setCount = System.nanoTime () - start;
	start = System.nanoTime ();
	TreeItem middle = tree.getItem (ROOTS / 2);
	long getItem = System.nanoTime () - start;
	status (status, String.format (Locale.ROOT, "%,d roots: setItemCount %.1f ms, getItem (%,d) %.3f ms (%s)", tree.getItemCount (), setCount / 1e6,
			ROOTS / 2, getItem / 1e6, middle.getText ()));
	shell.pack ();
	shell.open ();
	return shell;
}

/* root n is "root n"; every tenth root has CHILDREN children, every fifth child GRANDCHILDREN grandchildren */
static void fill (TreeItem item, int index) {
	TreeItem parent = item.getParentItem ();
	if (parent == null) {
		item.setText (String.format (Locale.ROOT, "root %,d", index));
		if (index % 10 == 0) item.setItemCount (CHILDREN);
	} else {
		item.setText (parent.getText () + " / " + index);
		if (parent.getParentItem () == null && index % 5 == 0) item.setItemCount (GRANDCHILDREN);
	}
}

static int root (Text row, Tree tree) {
	try {
		return Math.max (0, Math.min (tree.getItemCount () - 1, Integer.parseInt (row.getText ().replace (",", "").trim ())));
	} catch (NumberFormatException e) {
		return 0;
	}
}

/* expands or collapses a root (and, when it opens, its first child that has children) and shows it at the top */
static void expand (Tree tree, int index, boolean expanded, Label status) {
	TreeItem item = tree.getItem (index);
	long start = System.nanoTime ();
	item.setExpanded (expanded);
	if (expanded && item.getItemCount () > 0) item.getItem (0).setExpanded (true);
	tree.setTopItem (item);
	long nanos = System.nanoTime () - start;
	status (status, String.format (Locale.ROOT, "%s %s: %,d children, %.2f ms, vertical bar maximum %,d", expanded ? "expanded" : "collapsed",
			item.getText (), item.getItemCount (), nanos / 1e6, tree.getVerticalBar ().getMaximum ()));
}

/* one row per frame: the item below the top row becomes the top item, until the toggle is released */
static void scrollStep (Tree tree, Button toggle) {
	if (tree.isDisposed () || !toggle.getSelection ()) return;
	TreeItem next = tree.getItem (new Point (5, tree.getItemHeight () + tree.getItemHeight () / 2));
	if (next == null) {
		toggle.setSelection (false);
		return;
	}
	tree.setTopItem (next);
	tree.getDisplay ().timerExec (16, () -> scrollStep (tree, toggle));
}

static void status (Label status, String text) {
	status.setText (text);
	status.getParent ().layout (new Control [] {status});
	LOG.info (text);
}

static void button (Shell shell, String text, Runnable action) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.addListener (SWT.Selection, event -> action.run ());
}
}
