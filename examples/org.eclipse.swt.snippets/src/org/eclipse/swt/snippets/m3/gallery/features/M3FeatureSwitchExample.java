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
package org.eclipse.swt.snippets.m3.gallery.features;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Feature switches: every row of the M3Features registry beside a virtual tree; checking a shipped row switches the
 * feature on for that tree instance (<code>M3Features.set</code>, live), native defaults are read-only and unshipped
 * rows show why they cannot switch. Run with <code>-Dswt.widget.mode=custom</code> to see the frame overlay.
 */
@SuppressWarnings ("restriction")
public class M3FeatureSwitchExample {

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3FeatureSwitchExample (" + M3Features.COUNT + " features)");
		shell.setLayout (new GridLayout (2, true));
		features (shell, tree (shell));
		shell.setSize (900, 520);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static Tree tree (Composite parent) {
		Tree tree = new Tree (parent, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		tree.addListener (SWT.SetData, e -> {
			TreeItem item = (TreeItem) e.item;
			item.setText ((item.getParentItem () == null ? "node " : "leaf ") + e.index);
			item.setItemCount (item.getParentItem () == null ? 5 : 0);
		});
		tree.setItemCount (10_000);
		return tree;
	}

	/** The virtual table of every feature (check = on for the tree) and the status line. */
	static Table features (Shell shell, Tree tree) {
		Table table = new Table (shell, SWT.CHECK | SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		Label status = new Label (shell, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
		table.addListener (SWT.SetData, e -> fill ((TableItem) e.item, table.indexOf ((TableItem) e.item), tree));
		table.addListener (SWT.Selection, e -> {
			if (e.detail == SWT.CHECK) toggle ((TableItem) e.item, table.indexOf ((TableItem) e.item), tree, status);
		});
		table.setItemCount (M3Features.COUNT);
		return table;
	}

	/** Row text of a feature id (its registry description) and its state for the tree. */
	static void fill (TableItem item, int id, Tree tree) {
		item.setText (id + "  " + M3Features.describe (id));
		item.setChecked (M3Features.isOn (tree, id));
	}

	/** Switches a feature for the tree, or explains the refusal and restores the check box. */
	static void toggle (TableItem item, int id, Tree tree, Label status) {
		boolean on = item.getChecked ();
		try {
			M3Features.set (tree, id, on);
			tree.redraw ();
			status.setText (M3Features.key ("Tree", id) + " = " + on + " for this tree");
		} catch (IllegalArgumentException | SWTError e) {
			item.setChecked (!on);
			status.setText ("feature " + id + " cannot switch: " + e.getMessage ());
		}
	}
}
