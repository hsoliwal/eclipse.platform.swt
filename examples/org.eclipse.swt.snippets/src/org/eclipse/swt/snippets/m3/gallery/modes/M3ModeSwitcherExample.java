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
package org.eclipse.swt.snippets.m3.gallery.modes;

import java.io.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Live render-mode switcher for one widget class: shows the widget with the mode it runs in (M3Mode) and the mode the
 * startup properties ask for; each mode button relaunches this example with
 * <code>-Dswt.widget.mode.&lt;Widget&gt;=&lt;mode&gt;</code>, because a widget class reads its mode once per JVM.
 */
public class M3ModeSwitcherExample {
	//GALLERY: Table | Table in each render mode
	//GALLERY: Tree | Tree in each render mode
	//GALLERY: List | List in each render mode
	//GALLERY: Combo | Combo in each render mode
	//GALLERY: StyledText | StyledText in each render mode
	//GALLERY: Canvas | SWT.M3 Canvas in each render mode
	//GALLERY: CTabFolder | CTabFolder in each render mode

	static final String [] MODES = {"viewport", "original", "custom", "m3", "gpu"};

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		String kind = args.length > 0 ? args [0] : "Table";
		shell.setLayout (new GridLayout (MODES.length, true));
		Control control = create (shell, kind);
		control.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, MODES.length, 1));
		String effective = M3Mode.name (M3Mode.of (control));
		shell.setText ("M3ModeSwitcherExample: " + kind + " runs " + effective + ", requested " + requested (kind));
		for (String mode : MODES) relaunchButton (shell, kind, mode, effective);
		shell.setSize (640, 420);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	/** The mode the startup properties ask for (the widget's own property first). */
	static String requested (String kind) {
		return System.getProperty ("swt.widget.mode." + kind, System.getProperty ("swt.widget.mode", "(not set: viewport)"));
	}

	/** The widget under test, filled with 100,000 rows where it has items. */
	static Control create (Composite parent, String kind) {
		switch (kind) {
			case "Tree": {
				Tree tree = new Tree (parent, SWT.VIRTUAL | SWT.BORDER);
				tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText ("node " + e.index));
				tree.setItemCount (100_000);
				return tree;
			}
			case "List": {
				org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (parent, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
				list.setItems (rows (100_000));
				return list;
			}
			default:
				return other (parent, kind);
		}
	}

	static Control other (Composite parent, String kind) {
		switch (kind) {
			case "Combo": {
				Combo combo = new Combo (parent, SWT.BORDER);
				combo.setItems (rows (1000));
				combo.select (0);
				return combo;
			}
			case "StyledText": {
				StyledText text = new StyledText (parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
				text.setText (String.join ("\n", rows (10_000)));
				return text;
			}
			default:
				return rest (parent, kind);
		}
	}

	static Control rest (Composite parent, String kind) {
		switch (kind) {
			case "Canvas": {
				Canvas canvas = new Canvas (parent, SWT.M3 | SWT.DOUBLE_BUFFERED);
				canvas.addListener (SWT.Paint, e -> e.gc.drawString ("SWT.M3 canvas; mode " + M3Mode.name (M3Mode.of (canvas)), 10, 10));
				return canvas;
			}
			case "CTabFolder": {
				CTabFolder folder = new CTabFolder (parent, SWT.BORDER);
				for (int i = 0; i < 30; i++) new CTabItem (folder, SWT.NONE).setText ("Tab " + i);
				folder.setSelection (0);
				return folder;
			}
			default:
				return table (parent);
		}
	}

	static Table table (Composite parent) {
		Table table = new Table (parent, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
		table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText ("row " + table.indexOf ((TableItem) e.item)));
		table.setItemCount (100_000);
		return table;
	}

	static String [] rows (int count) {
		String [] rows = new String [count];
		Arrays.setAll (rows, i -> "row " + i);
		return rows;
	}

	static void relaunchButton (Shell shell, String kind, String mode, String effective) {
		Button button = new Button (shell, SWT.PUSH);
		button.setText (mode.equals (effective) ? "[" + mode + "]" : mode);
		button.addListener (SWT.Selection, e -> relaunch (kind, mode));
	}

	/** Starts this example again in a child JVM with the widget class switched to the mode. */
	static void relaunch (String kind, String mode) {
		String java = System.getProperty ("java.home") + File.separator + "bin" + File.separator + "java";
		List<String> command = new ArrayList<> (List.of (java, "-cp", System.getProperty ("java.class.path"), "-Dswt.widget.mode." + kind + "=" + mode));
		for (String key : new String [] {"java.library.path", "swt.library.path"}) {
			if (System.getProperty (key) != null) command.add ("-D" + key + "=" + System.getProperty (key));
		}
		command.addAll (List.of (M3ModeSwitcherExample.class.getName (), kind));
		try {
			new ProcessBuilder (command).inheritIO ().start ();
		} catch (IOException e) {
			throw new SWTException (SWT.ERROR_IO, e.getMessage ());
		}
	}
}
