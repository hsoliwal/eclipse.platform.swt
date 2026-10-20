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

import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Tree viewport, example 3: the native expand and collapse keys.
 * <p>
 * The TreeView expands and collapses the caret row itself: numpad minus or Left collapses an expanded row, numpad
 * plus or Right expands a collapsed one, and numpad star expands the whole subtree. SWT reports each as
 * <code>SWT.Expand</code> or <code>SWT.Collapse</code>. The log under the tree shows every event and, after each key,
 * <code>getExpanded ()</code>, the child count, the top item and the vertical bar, which is what stock SWT answers:
 * after a collapse the row is collapsed, no Expand follows, and when the top row was inside the collapsed subtree the
 * row itself becomes the top row (Test_org_eclipse_swt_widgets_Tree_KeyCollapse and the expansion-key differential
 * check the default mode against a stock recording). "Prepare" expands root 7 and scrolls its children to the top,
 * the case that needs the top to move.
 * </p>
 */
public class M3TreeCollapseKeysExample {

static final Logger LOG = Logger.getLogger (M3TreeCollapseKeysExample.class.getName ());
static final int ROOTS = 300, CHILDREN = 40;

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
	shell.setText ("M3 Tree: native expand and collapse keys");
	shell.setLayout (new GridLayout (2, false));
	Label help = new Label (shell, SWT.WRAP);
	help.setText ("Select an expandable row (every seventh root) and press numpad -, Left, numpad +, Right or numpad *.");
	help.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false, 2, 1));
	Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
	GridData treeData = new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1);
	treeData.widthHint = 360;
	treeData.heightHint = 300;
	tree.setLayoutData (treeData);
	List log = new List (shell, SWT.BORDER | SWT.V_SCROLL);
	GridData logData = new GridData (SWT.FILL, SWT.FILL, true, false, 1, 1);
	logData.heightHint = 120;
	log.setLayoutData (logData);
	Button prepare = new Button (shell, SWT.PUSH);
	prepare.setText ("Prepare");
	prepare.setLayoutData (new GridData (SWT.FILL, SWT.TOP, false, false));
	prepare.addListener (SWT.Selection, event -> prepare (tree, log));
	tree.addListener (SWT.SetData, event -> {
		TreeItem item = (TreeItem) event.item;
		TreeItem parent = item.getParentItem ();
		item.setText (parent == null ? "root " + event.index : parent.getText () + " / " + event.index);
		if (parent == null && event.index % 7 == 0) item.setItemCount (CHILDREN);
	});
	tree.addListener (SWT.Expand, event -> log (log, "Expand " + ((TreeItem) event.item).getText ()));
	tree.addListener (SWT.Collapse, event -> log (log, "Collapse " + ((TreeItem) event.item).getText ()));
	/* the state after the native key: the TreeView has finished the expansion when KeyUp arrives */
	tree.addListener (SWT.KeyUp, event -> {
		TreeItem [] selection = tree.getSelection ();
		if (selection.length > 0) log (log, state (tree, selection [0]));
	});
	tree.setItemCount (ROOTS);
	shell.pack ();
	shell.open ();
	return shell;
}

/** Expands root 7, selects it and puts its 30th child at the top: a collapse key must then move the top to root 7. */
public static TreeItem prepare (Tree tree, List log) {
	TreeItem root = tree.getItem (7);
	root.setExpanded (true);
	tree.setSelection (root);
	tree.setTopItem (root.getItem (30));
	tree.setFocus ();
	log (log, "prepared: " + state (tree, root));
	return root;
}

/** What a client reads after a key: the row's expansion and count, the top item and the vertical bar. */
public static String state (Tree tree, TreeItem item) {
	ScrollBar bar = tree.getVerticalBar ();
	TreeItem top = tree.getTopItem ();
	return item.getText () + " expanded=" + item.getExpanded () + " count=" + item.getItemCount () + " top=" + (top == null ? "-" : top.getText ())
		+ " bar " + bar.getSelection () + "/" + bar.getMaximum ();
}

static void log (List log, String line) {
	log.add (line);
	log.setTopIndex (log.getItemCount () - 1);
	LOG.info (line);
}
}
