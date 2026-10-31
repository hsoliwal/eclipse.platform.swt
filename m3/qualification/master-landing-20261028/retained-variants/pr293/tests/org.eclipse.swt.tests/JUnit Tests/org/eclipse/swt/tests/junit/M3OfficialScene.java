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

import java.io.*;
import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * The shared scene of the enhanced-API differentials against the official SWT jar: one Table, List and Tree
 * (plain or <code>SWT.VIRTUAL</code>), public structural and scrolling operations, and public-state columns.
 * <p>
 * A leaf differential adds its own column: with the official jar (no M3 classes) the column is computed from
 * public API only (the oracle of the leaf's contract), with this SWT it is computed from the M3 API. Both runs
 * also record the public state, so an M3 listener or seam that changed stock behaviour fails as well. This
 * class uses public API only and loads with the official jar; M3 code stays in the leaf's nested
 * <code>Fork</code> class. The file is byte-identical in every enhanced-API leaf branch.
 * </p>
 */
public final class M3OfficialScene {
	/** True when this JVM runs the official jar (it has no widget modes). */
	public static final boolean OFFICIAL = "official".equals (ViewportDifferential.reference ("Table"));
	public final Shell shell;
	public final Table table;
	public final List list;
	public final Tree tree;
	public final int style;
	/** Per-leaf state of the current step (event logs, mementos); cleared by the leaf. */
	public final StringBuilder notes = new StringBuilder ();
	int serial;

	public M3OfficialScene (Display display, int style) {
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
	}

	/** Empties the widgets and restarts the item names (the start state of a seed). */
	public void reset () {
		table.removeAll ();
		list.removeAll ();
		tree.removeAll ();
		serial = 0;
		notes.setLength (0);
		drain ();
	}

	/** A fresh item text: the same sequence in every run of a seed. */
	public String text () {
		return "x" + serial++;
	}

	/** Runs the event loop until it is idle. */
	public void drain () {
		Display display = shell.getDisplay ();
		while (display.readAndDispatch ()) {
			// paints, coalesced checks, posted notifications
		}
	}

	/** Public state column: item counts and top indices. */
	public String counts () {
		return "T" + table.getItemCount () + "@" + table.getTopIndex () + " L" + list.getItemCount () + "@" + list.getTopIndex () + " R" + tree.getItemCount ();
	}

	/** The rows of the table whose bounds intersect its client area, from public API: "first..last" or "-". */
	public String visibleTable () {
		int count = table.getItemCount (), top = table.getTopIndex (), height = table.getClientArea ().height;
		int last = top - 1;
		while (last + 1 < count && table.getItem (last + 1).getBounds ().y < height) last++;
		return last < top ? "-" : top + ".." + last;
	}

	/** The rows of the list that intersect its client area, from public API. */
	public String visibleList () {
		int count = list.getItemCount (), top = list.getTopIndex ();
		if (count == 0) return "-";
		int rows = (list.getClientArea ().height + list.getItemHeight () - 1) / list.getItemHeight ();
		return top + ".." + Math.min (count - 1, top + rows - 1);
	}

	/** A random public operation on one of the widgets: structure, scrolling or selection. */
	public static ViewportDifferential.Op<M3OfficialScene> next (Random random, StringBuilder log) {
		int widget = random.nextInt (3), kind = random.nextInt (10), arg = random.nextInt (40);
		log.append ("w").append (widget).append (" op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> widget == 0 ? scene.tableOp (kind, arg) : widget == 1 ? scene.listOp (kind, arg) : scene.treeOp (kind, arg);
	}

	String tableOp (int kind, int arg) {
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
		return "table";
	}

	String listOp (int kind, int arg) {
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
		return "list";
	}

	String treeOp (int kind, int arg) {
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
		return "tree";
	}

	/** The tree's top item text and the texts of its visible items from public API, as "top first..last". */
	public String visibleTree () {
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
			TreeItem [] siblings = parent == null ? at.getParent ().getItems () : parent.getItems ();
			int index = parent == null ? at.getParent ().indexOf (at) : parent.indexOf (at);
			if (index + 1 < siblings.length) return siblings [index + 1];
		}
		return null;
	}

	/** Runs a leaf harness on a fresh scene: records with the official jar, compares with this SWT. */
	public static void run (ViewportDifferential<M3OfficialScene> harness, int style, java.util.function.Consumer<M3OfficialScene> setup) throws IOException {
		M3OfficialScene scene = new M3OfficialScene (Display.getDefault (), style);
		try {
			setup.accept (scene);
			harness.compareWithRecording (scene, ViewportDifferential.records ("Table"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.shell.dispose ();
		}
	}
}
