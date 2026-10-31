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
package org.eclipse.swt.snippets.m3.gallery.scale;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Viewport scale: a virtual Table or Tree, a List or a Combo with 10^3 to 10^7 items; the title shows the time to fill
 * the widget and to scroll to the middle and to the end. List and Combo stop at 10^6, because their API keeps one
 * String per item. Arguments: the widget and the item count.
 */
public class M3ScaleExample {
	//GALLERY: Table 1000 | virtual Table, 10^3 rows
	//GALLERY: Table 100000 | virtual Table, 10^5 rows
	//GALLERY: Table 1000000 | virtual Table, 10^6 rows
	//GALLERY: Table 10000000 | virtual Table, 10^7 rows
	//GALLERY: Tree 1000 | virtual Tree, 10^3 roots
	//GALLERY: Tree 100000 | virtual Tree, 10^5 roots
	//GALLERY: Tree 1000000 | virtual Tree, 10^6 roots
	//GALLERY: Tree 10000000 | virtual Tree, 10^7 roots
	//GALLERY: List 1000 | List, 10^3 items
	//GALLERY: List 1000000 | List, 10^6 items
	//GALLERY: Combo 1000 | Combo, 10^3 items
	//GALLERY: Combo 1000000 | Combo, 10^6 items

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		String kind = args.length > 0 ? args [0] : "Table";
		int count = args.length > 1 ? Integer.parseInt (args [1]) : 1_000_000;
		shell.setLayout (new FillLayout ());
		shell.setText (measure (shell, kind, count));
		shell.setSize (520, 420);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	/** Fills the widget and scrolls it to the middle and the end; answers the timings as the shell title. */
	static String measure (Shell shell, String kind, int count) {
		long start = System.nanoTime ();
		Control control = fill (shell, kind, count);
		long filled = System.nanoTime ();
		scroll (control, count / 2);
		scroll (control, count - 1);
		long scrolled = System.nanoTime ();
		return String.format ("M3ScaleExample: %s %,d items; fill %d ms, scroll %d ms", kind, count, (filled - start) / 1_000_000, (scrolled - filled) / 1_000_000);
	}

	static String text (int i) {
		return "item " + i;
	}

	static Control fill (Composite parent, String kind, int count) {
		switch (kind) {
			case "Tree": {
				Tree tree = new Tree (parent, SWT.VIRTUAL | SWT.BORDER);
				tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText (text (e.index)));
				tree.setItemCount (count);
				return tree;
			}
			case "List":
				return list (parent, count);
			case "Combo":
				return combo (parent, count);
			default:
				return table (parent, count);
		}
	}

	static Table table (Composite parent, int count) {
		Table table = new Table (parent, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
		table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText (text (table.indexOf ((TableItem) e.item))));
		table.setItemCount (count);
		return table;
	}

	static org.eclipse.swt.widgets.List list (Composite parent, int count) {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (parent, SWT.BORDER | SWT.V_SCROLL);
		String [] items = new String [count];
		Arrays.setAll (items, M3ScaleExample::text);
		list.setItems (items);
		return list;
	}

	static Combo combo (Composite parent, int count) {
		Combo combo = new Combo (parent, SWT.BORDER);
		String [] items = new String [count];
		Arrays.setAll (items, M3ScaleExample::text);
		combo.setItems (items);
		return combo;
	}

	/** Scrolls an item into view (Combo: selects it). */
	static void scroll (Control control, int index) {
		if (index < 0) return;
		if (control instanceof Table table) table.setTopIndex (index);
		else if (control instanceof Tree tree) tree.setTopItem (tree.getItem (index));
		else if (control instanceof org.eclipse.swt.widgets.List list) list.setTopIndex (index);
		else ((Combo) control).select (index);
	}
}
