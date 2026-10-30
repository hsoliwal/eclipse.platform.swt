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
package org.eclipse.swt.snippets.m3.gallery.widgets;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * The SWT item widgets: Table and Tree (virtual and non-virtual, check, multi selection), List, Combo, TabFolder,
 * ToolBar, CoolBar, ExpandBar and menus, each filled with items. Arguments: the widget and optional style names.
 */
public class M3ItemWidgetsExample {
	//GALLERY: Table | non-virtual table with columns and 1,000 items
	//GALLERY: Table VIRTUAL | virtual table, 100,000 rows filled on demand
	//GALLERY: Table CHECK MULTI | check boxes and multi selection
	//GALLERY: Tree | non-virtual tree, three levels
	//GALLERY: Tree VIRTUAL | virtual tree, 100,000 roots with lazy children
	//GALLERY: Tree CHECK | tree with check boxes
	//GALLERY: List | single-selection list
	//GALLERY: List MULTI | multi-selection list
	//GALLERY: Combo | drop-down, read-only and simple combos
	//GALLERY: TabFolder | tab folder with three pages
	//GALLERY: ToolBar | tool bar with push, check, radio, drop-down and separator items
	//GALLERY: CoolBar | cool bar with two tool bars
	//GALLERY: ExpandBar | expand bar with two items
	//GALLERY: Menu | menu bar, cascade and pop-up menus

	static final String [] KINDS = {"Table", "Tree", "List", "Combo", "TabFolder", "ToolBar", "CoolBar", "ExpandBar", "Menu"};

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		String kind = args.length > 0 ? args [0] : "Table";
		int style = style (Arrays.copyOfRange (args, Math.min (1, args.length), args.length));
		shell.setText ("M3ItemWidgetsExample: " + String.join (" ", args));
		shell.setLayout (new FillLayout ());
		create (shell, kind, style);
		shell.setSize (520, 420);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	/** Style bits of style names (VIRTUAL, CHECK, MULTI); BORDER is always set. */
	static int style (String [] names) {
		int style = SWT.BORDER;
		for (String name : names) style |= "VIRTUAL".equals (name) ? SWT.VIRTUAL : "CHECK".equals (name) ? SWT.CHECK : "MULTI".equals (name) ? SWT.MULTI : 0;
		return style;
	}

	static void create (Composite parent, String kind, int style) {
		switch (kind) {
			case "Table" -> table (parent, style);
			case "Tree" -> tree (parent, style);
			case "List" -> list (parent, style);
			case "Combo" -> combos (parent);
			case "TabFolder" -> tabs (parent);
			case "ToolBar" -> toolBar (parent);
			case "CoolBar" -> coolBar (parent);
			case "ExpandBar" -> expandBar (parent);
			case "Menu" -> menus (parent.getShell ());
			default -> throw new IllegalArgumentException ("unknown widget " + kind + "; one of " + String.join (", ", KINDS));
		}
	}

	static void table (Composite parent, int style) {
		Table table = new Table (parent, style | SWT.FULL_SELECTION);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		for (String title : new String [] {"Name", "Size"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (title);
			column.setWidth (160);
		}
		boolean virtual = (style & SWT.VIRTUAL) != 0;
		if (virtual) table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText (new String [] {"row " + table.indexOf ((TableItem) e.item), "" + e.index}));
		if (virtual) table.setItemCount (100_000);
		else for (int i = 0; i < 1000; i++) new TableItem (table, SWT.NONE).setText (new String [] {"row " + i, Integer.toString (i * 7)});
	}

	static void tree (Composite parent, int style) {
		Tree tree = new Tree (parent, style);
		if ((style & SWT.VIRTUAL) != 0) {
			tree.addListener (SWT.SetData, e -> {
				TreeItem item = (TreeItem) e.item;
				item.setText ((item.getParentItem () == null ? "root " : "child ") + e.index);
				item.setItemCount (item.getParentItem () == null ? 3 : 0);
			});
			tree.setItemCount (100_000);
			return;
		}
		for (int i = 0; i < 20; i++) branch (new TreeItem (tree, SWT.NONE), "node " + i, 2);
	}

	static void branch (TreeItem item, String text, int depth) {
		item.setText (text);
		for (int i = 0; depth > 0 && i < 3; i++) branch (new TreeItem (item, SWT.NONE), text + "." + i, depth - 1);
	}

	static void list (Composite parent, int style) {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (parent, style | SWT.V_SCROLL | ((style & SWT.MULTI) != 0 ? 0 : SWT.SINGLE));
		for (int i = 0; i < 1000; i++) list.add ("item " + i);
	}

	static void combos (Composite parent) {
		Composite area = new Composite (parent, SWT.NONE);
		area.setLayout (new GridLayout (1, false));
		for (int style : new int [] {SWT.DROP_DOWN, SWT.READ_ONLY, SWT.SIMPLE}) {
			Combo combo = new Combo (area, style | SWT.BORDER);
			for (int i = 0; i < 50; i++) combo.add ("choice " + i);
			combo.select (3);
			combo.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, style == SWT.SIMPLE));
		}
	}

	static void tabs (Composite parent) {
		TabFolder folder = new TabFolder (parent, SWT.TOP);
		for (int i = 0; i < 3; i++) {
			TabItem item = new TabItem (folder, SWT.NONE);
			item.setText ("Page " + (i + 1));
			Label label = new Label (folder, SWT.CENTER);
			label.setText ("content of page " + (i + 1));
			item.setControl (label);
		}
	}

	static ToolBar toolBar (Composite parent) {
		ToolBar bar = new ToolBar (parent, SWT.FLAT | SWT.WRAP | SWT.RIGHT);
		int [] styles = {SWT.PUSH, SWT.CHECK, SWT.RADIO, SWT.RADIO, SWT.SEPARATOR, SWT.DROP_DOWN};
		for (int i = 0; i < styles.length; i++) {
			ToolItem item = new ToolItem (bar, styles [i]);
			if (styles [i] != SWT.SEPARATOR) item.setText ("item " + i);
		}
		return bar;
	}

	static void coolBar (Composite parent) {
		CoolBar cool = new CoolBar (parent, SWT.NONE);
		for (int i = 0; i < 2; i++) {
			ToolBar bar = toolBar (cool);
			CoolItem item = new CoolItem (cool, SWT.NONE);
			item.setControl (bar);
			org.eclipse.swt.graphics.Point size = bar.computeSize (SWT.DEFAULT, SWT.DEFAULT);
			item.setPreferredSize (item.computeSize (size.x, size.y));
		}
	}

	static void expandBar (Composite parent) {
		ExpandBar bar = new ExpandBar (parent, SWT.V_SCROLL);
		for (int i = 0; i < 2; i++) {
			Composite content = new Composite (bar, SWT.NONE);
			content.setLayout (new FillLayout (SWT.VERTICAL));
			for (int k = 0; k < 3; k++) new Button (content, SWT.CHECK).setText ("option " + k);
			ExpandItem item = new ExpandItem (bar, SWT.NONE);
			item.setText ("Section " + (i + 1));
			item.setHeight (content.computeSize (SWT.DEFAULT, SWT.DEFAULT).y);
			item.setControl (content);
			item.setExpanded (i == 0);
		}
	}

	static void menus (Shell shell) {
		Menu bar = new Menu (shell, SWT.BAR);
		shell.setMenuBar (bar);
		for (String title : new String [] {"File", "Edit"}) {
			MenuItem cascade = new MenuItem (bar, SWT.CASCADE);
			cascade.setText (title);
			Menu menu = new Menu (shell, SWT.DROP_DOWN);
			cascade.setMenu (menu);
			for (int style : new int [] {SWT.PUSH, SWT.CHECK, SWT.SEPARATOR, SWT.RADIO}) new MenuItem (menu, style).setText (style == SWT.SEPARATOR ? "" : title + " item");
		}
		Label label = new Label (shell, SWT.CENTER);
		label.setText ("right-click for the pop-up menu");
		Menu popup = new Menu (label);
		new MenuItem (popup, SWT.PUSH).setText ("Pop-up item");
		label.setMenu (popup);
	}
}
