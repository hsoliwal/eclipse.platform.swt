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
package org.eclipse.swt.widgets;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * The scene of the official-jar differentials (tests bundle, OfficialScene), in process: a Table, a List and a
 * Tree driven by seeded public operations. A test compares an internal path with the stock path after every
 * operation; the official-jar differentials record the stock path against the official SWT jar.
 */
final class SeededWidgetScene {
	final Shell shell;
	final Table table;
	final List list;
	final Tree tree;
	final int style;
	int serial;

	SeededWidgetScene (Display display, int style) {
		this.style = style;
		shell = new Shell (display);
		shell.setBounds (10, 10, 640, 330);
		table = new Table (shell, SWT.MULTI | SWT.BORDER | style);
		table.setBounds (5, 5, 200, 280);
		list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (215, 5, 200, 280);
		tree = new Tree (shell, SWT.MULTI | SWT.BORDER | style);
		tree.setBounds (425, 5, 200, 280);
		table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("t" + event.index));
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
		shell.open ();
		drain ();
	}

	String text () {
		return "x" + serial++;
	}

	void drain () {
		Display display = shell.getDisplay ();
		while (display.readAndDispatch ()) {
			// paints, coalesced checks, posted notifications
		}
	}

	void dispose () {
		shell.dispose ();
	}

	/** One seeded public operation on one of the widgets (structure, scrolling or selection), then the event loop. */
	void step (Random random) {
		int widget = random.nextInt (3), kind = random.nextInt (10), arg = random.nextInt (40);
		if (widget == 0) tableOp (kind, arg);
		else if (widget == 1) listOp (kind, arg);
		else treeOp (kind, arg);
		drain ();
	}

	void tableOp (int kind, int arg) {
		int n = table.getItemCount ();
		switch (kind) {
			case 0, 1 -> { for (int i = 0; i < arg; i++) new TableItem (table, SWT.NONE).setText (text ()); }
			case 2 -> { if (n > 0) table.remove (arg % n); }
			case 3 -> { if (n > 0) table.remove (0, Math.min (arg / 4, n - 1)); }
			case 4 -> table.setItemCount (arg * 3);
			case 5, 6 -> table.setTopIndex (arg * 2);
			case 7 -> { if (n > 0) table.setSelection (arg % n); }
			case 8 -> new TableItem (table, SWT.NONE, 0).setText (text ());
			default -> table.removeAll ();
		}
	}

	void listOp (int kind, int arg) {
		int n = list.getItemCount ();
		switch (kind) {
			case 0, 1 -> { for (int i = 0; i < arg; i++) list.add (text ()); }
			case 2 -> { if (n > 0) list.remove (arg % n); }
			case 3 -> { if (n > 0) list.remove (0, Math.min (arg / 4, n - 1)); }
			case 4 -> list.add (text (), 0);
			case 5, 6 -> list.setTopIndex (arg * 2);
			case 7 -> { if (n > 0) list.setSelection (arg % n); }
			case 8 -> { if (n > 0) list.setItem (0, text ()); }
			default -> list.removeAll ();
		}
	}

	void treeOp (int kind, int arg) {
		int n = tree.getItemCount ();
		TreeItem root = n > 0 ? tree.getItem (arg % n) : null;
		switch (kind) {
			case 0, 1 -> { for (int i = 0; i < arg / 2; i++) new TreeItem (tree, SWT.NONE).setText (text ()); }
			case 2 -> { if (root != null) for (int i = 0; i < 3; i++) new TreeItem (root, SWT.NONE).setText (text ()); }
			case 3 -> { if (root != null) root.setExpanded (true); }
			case 4 -> { if (root != null) root.setExpanded (false); }
			case 5, 6 -> { if (root != null) tree.setTopItem (root); }
			case 7 -> { if (root != null) root.dispose (); }
			case 8 -> tree.setItemCount (arg);
			default -> { if (arg < 8) tree.removeAll (); }
		}
	}

	/** Item counts and top indices, from public API. */
	String counts () {
		return "T" + table.getItemCount () + "@" + table.getTopIndex () + " L" + list.getItemCount () + "@" + list.getTopIndex () + " R" + tree.getItemCount ();
	}

	/** The rows of the table whose bounds intersect its client area, from public API: {first, last}, last < first when none. */
	int [] visibleTable () {
		int count = table.getItemCount (), top = table.getTopIndex (), height = table.getClientArea ().height;
		int last = top - 1;
		while (last + 1 < count && table.getItem (last + 1).getBounds ().y < height) last++;
		return new int [] {top, last};
	}

	/** The rows of the list that intersect its client area, from public API: {first, last}, last < first when none. */
	int [] visibleList () {
		int count = list.getItemCount (), top = list.getTopIndex ();
		if (count == 0) return new int [] {0, -1};
		int rows = (list.getClientArea ().height + list.getItemHeight () - 1) / list.getItemHeight ();
		return new int [] {top, Math.min (count - 1, top + rows - 1)};
	}

	/** The tree's top item and the number of visible items from public API. */
	String visibleTree () {
		TreeItem top = tree.getTopItem ();
		if (top == null) return "-";
		Rectangle area = tree.getClientArea ();
		int rows = 0;
		for (TreeItem item = top; item != null && item.getBounds ().y < area.height; item = nextVisible (item)) rows++;
		return top.getText () + "+" + rows;
	}

	/* the next item in visible (preorder of expanded items) order, from public API */
	static TreeItem nextVisible (TreeItem item) {
		if (item.getExpanded () && item.getItemCount () > 0) return item.getItem (0);
		for (TreeItem at = item; at != null; at = at.getParentItem ()) {
			TreeItem parent = at.getParentItem ();
			TreeItem [] siblings = parent != null ? parent.getItems () : at.getParent ().getItems ();
			int index = Arrays.asList (siblings).indexOf (at);
			if (index + 1 < siblings.length) return siblings [index + 1];
		}
		return null;
	}

	/** The public state of the three widgets: counts, visible rows and selections. */
	String state () {
		return counts () + " " + Arrays.toString (visibleTable ()) + " " + Arrays.toString (visibleList ()) + " " + visibleTree ()
			+ " sel " + table.getSelectionCount () + "/" + list.getSelectionCount () + "/" + tree.getSelectionCount ();
	}
}
