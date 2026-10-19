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
import java.util.function.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Tree viewport, example 4: what happens to the selection when the caret row goes away.
 * <p>
 * In a <code>SWT.MULTI</code> Tree the caret (the focus row) and the selection are separate. When the caret row is
 * disposed, or <code>setItemCount</code> removes it, the TreeView moves the caret to a neighbouring row, and that
 * native caret move also deselects the most recently selected row other than the old caret; the new caret keeps
 * the selection state it had. The rule follows the order in which rows were selected, not their position, and
 * <code>selectAll</code> leaves rows that were already selected in their place in that order. The default mode
 * (viewport) reproduces these rules for rows that are not native rows (CaretMoveSelectionOracle,
 * Test_org_eclipse_swt_widgets_Tree_CaretMoveSelection). Each button runs one case from the same start and logs
 * the selection before and after; run with <code>-Dswt.widget.mode.Tree=original</code> to compare with stock.
 * </p>
 */
public class M3TreeCaretSelectionExample {

static final Logger LOG = Logger.getLogger (M3TreeCaretSelectionExample.class.getName ());
static final int ROOTS = 300;

/** The cases: a name and an action on a tree that starts with ROOTS roots and nothing selected. */
public static final Map<String, Consumer<Tree>> CASES = new LinkedHashMap<> ();
static {
	CASES.put ("Dispose the caret row", tree -> {
		tree.setSelection (tree.getItem (10));
		tree.select (tree.getItem (3));
		tree.select (tree.getItem (20));
		tree.getItem (10).dispose ();
	});
	CASES.put ("Order, not position", tree -> {
		tree.setSelection (tree.getItem (50));
		tree.select (tree.getItem (60));
		tree.select (tree.getItem (40));
		tree.getItem (50).dispose ();
	});
	CASES.put ("Shrink past the caret", tree -> {
		tree.setSelection (tree.getItem (295));
		tree.select (tree.getItem (280));
		tree.select (tree.getItem (290));
		tree.setItemCount (293);
	});
	CASES.put ("selectAll, deselect, shrink", tree -> {
		tree.setSelection (tree.getItem (299));
		tree.deselectAll ();
		tree.select (tree.getItem (297));
		tree.selectAll ();
		tree.deselect (tree.getItem (298));
		tree.setItemCount (299);
	});
}

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
	shell.setText ("M3 Tree: the selection when the caret row goes away");
	shell.setLayout (new GridLayout (2, false));
	Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
	GridData treeData = new GridData (SWT.FILL, SWT.FILL, true, true, 1, CASES.size () + 1);
	treeData.widthHint = 220;
	treeData.heightHint = 320;
	tree.setLayoutData (treeData);
	org.eclipse.swt.widgets.List log = new org.eclipse.swt.widgets.List (shell, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
	GridData logData = new GridData (SWT.FILL, SWT.FILL, true, true);
	logData.widthHint = 380;
	log.setLayoutData (logData);
	tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("root " + event.index));
	CASES.forEach ((name, action) -> {
		Button button = new Button (shell, SWT.PUSH);
		button.setText (name);
		button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, false, false));
		button.addListener (SWT.Selection, event -> run (tree, name, action, log));
	});
	tree.setItemCount (ROOTS);
	shell.pack ();
	shell.open ();
	return shell;
}

/** Resets the tree, runs one case and logs the selection after it. */
public static String run (Tree tree, String name, Consumer<Tree> action, org.eclipse.swt.widgets.List log) {
	tree.deselectAll ();
	tree.setItemCount (0);
	tree.setItemCount (ROOTS);
	tree.setTopItem (tree.getItem (0));
	action.accept (tree);
	TreeItem [] selection = tree.getSelection ();
	if (selection.length > 0) tree.showItem (selection [0]);
	String line = name + ": " + describe (selection);
	if (log != null) {
		log.add (line);
		log.setTopIndex (log.getItemCount () - 1);
	}
	LOG.info (line);
	return line;
}

/** The selection as a client reads it: the count and the rows, compressed to ranges. */
public static String describe (TreeItem [] selection) {
	StringBuilder out = new StringBuilder (selection.length + " selected");
	int start = -1, previous = -2;
	for (TreeItem item : selection) {
		int index = item.getParent ().indexOf (item);
		if (index != previous + 1) {
			if (start >= 0) out.append (start == previous ? " " + start : " " + start + ".." + previous);
			start = index;
		}
		previous = index;
	}
	if (start >= 0) out.append (start == previous ? " " + start : " " + start + ".." + previous);
	return out.toString ();
}
}
