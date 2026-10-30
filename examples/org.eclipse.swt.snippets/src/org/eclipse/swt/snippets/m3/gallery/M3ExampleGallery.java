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
package org.eclipse.swt.snippets.m3.gallery;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * The M3 example gallery: one launcher window that lists every example of the generated index by category and runs
 * it in a child JVM with a render mode (global or per widget class) and feature switches. Widget modes are read once
 * per JVM, so switching the mode relaunches the example; "Run in all five modes" starts it once per mode side by side.
 * A toolbar slot mounts the theme and language switchers when their classes are on the class path.
 */
@SuppressWarnings ("restriction")
public final class M3ExampleGallery {
	/** Class names tried for the theme and language switchers (the first one found is mounted). */
	public static final String [] SWITCHERS = {"M3ThemeSwitcher", "M3LanguageSwitcher"};
	static final String [] SWITCHER_PACKAGES = {"org.eclipse.swt.widgets.", "org.eclipse.swt.custom.", "org.eclipse.swt.m3."};
	static final String DEFAULT_MODE = "(default)";

	final Shell shell;
	final List<M3GalleryModel.Entry> entries = M3GalleryModel.entries ();
	final List<Process> children = new ArrayList<> ();
	Tree tree;
	Combo mode, widget, widgetMode;
	Table features;
	Text description, log;
	Composite switchers;
	M3GalleryModel.Entry selected;

	/** Builds the gallery in a new shell of the display. */
	public M3ExampleGallery (Display display) {
		shell = new Shell (display);
		shell.setText ("M3 Example Gallery (" + entries.size () + " entries)");
		shell.setLayout (new GridLayout (1, false));
		toolbar (shell);
		SashForm sash = new SashForm (shell, SWT.HORIZONTAL);
		sash.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		tree (sash);
		details (sash);
		sash.setWeights (2, 3);
		shell.addListener (SWT.Dispose, e -> children.forEach (Process::destroy));
		shell.setSize (1100, 720);
	}

	public static void main (String [] args) {
		Display display = new Display ();
		M3ExampleGallery gallery = new M3ExampleGallery (display);
		gallery.shell.open ();
		while (!gallery.shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	void toolbar (Composite parent) {
		Composite bar = new Composite (parent, SWT.NONE);
		bar.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		bar.setLayout (new GridLayout (10, false));
		mode = combo (bar, "Mode", list (DEFAULT_MODE, M3GalleryModel.MODES));
		widget = combo (bar, "Widget", list ("(all)", M3GalleryModel.MODE_WIDGETS));
		widgetMode = combo (bar, "in", list (DEFAULT_MODE, M3GalleryModel.MODES));
		button (bar, "Run", () -> run (selectedMode (), false));
		button (bar, "Run in all five modes", () -> M3GalleryModel.MODES.forEach (m -> run (m, false)));
		button (bar, "Run and close (check)", () -> run (selectedMode (), true));
		switchers = new Composite (bar, SWT.NONE);
		switchers.setLayout (new RowLayout ());
		switchers.setLayoutData (new GridData (SWT.END, SWT.CENTER, true, false));
		mountSwitchers ();
	}

	static List<String> list (String first, List<String> rest) {
		List<String> items = new ArrayList<> (List.of (first));
		items.addAll (rest);
		return items;
	}

	Combo combo (Composite parent, String label, List<String> items) {
		new Label (parent, SWT.NONE).setText (label);
		Combo combo = new Combo (parent, SWT.READ_ONLY);
		combo.setItems (items.toArray (new String [0]));
		combo.select (0);
		return combo;
	}

	Button button (Composite parent, String text, Runnable action) {
		Button button = new Button (parent, SWT.PUSH);
		button.setText (text);
		button.addListener (SWT.Selection, e -> action.run ());
		return button;
	}

	/** Mounts each switcher found on the class path; hides the slot when none is. */
	void mountSwitchers () {
		int mounted = 0;
		for (String name : SWITCHERS) mounted += mount (switchers, name) ? 1 : 0;
		switchers.setVisible (mounted > 0);
		((GridData) switchers.getLayoutData ()).exclude = mounted == 0;
	}

	/** Creates <code>new &lt;name&gt; (parent, SWT.NONE)</code> from the first package that has the class. */
	static boolean mount (Composite parent, String name) {
		for (String pkg : SWITCHER_PACKAGES) {
			try {
				Class.forName (pkg + name).getConstructor (Composite.class, int.class).newInstance (parent, SWT.NONE);
				return true;
			} catch (ReflectiveOperationException | LinkageError e) {
				/* not on the class path in this package */
			}
		}
		return false;
	}

	/** Categories with their entries, then one node per enhanced API row with the examples that call it. */
	void tree (Composite parent) {
		tree = new Tree (parent, SWT.BORDER | SWT.SINGLE);
		M3GalleryModel.byCategory (entries).forEach ((category, list) -> node (tree, category + " (" + list.size () + ")", list).setExpanded (true));
		Map<String, List<M3GalleryModel.Entry>> rows = M3GalleryModel.byApi (entries);
		TreeItem apis = new TreeItem (tree, SWT.NONE);
		apis.setText ("enhanced API rows (" + rows.size () + ")");
		rows.forEach ((api, list) -> {
			TreeItem row = new TreeItem (apis, SWT.NONE);
			row.setText (api + " (" + list.size () + ")");
			list.forEach (e -> leaf (row, e));
		});
		tree.addListener (SWT.Selection, e -> select (e.item.getData () instanceof M3GalleryModel.Entry entry ? entry : null));
		tree.addListener (SWT.DefaultSelection, e -> run (selectedMode (), false));
	}

	TreeItem node (Tree parent, String text, List<M3GalleryModel.Entry> list) {
		TreeItem node = new TreeItem (parent, SWT.NONE);
		node.setText (text);
		list.forEach (e -> leaf (node, e));
		return node;
	}

	static TreeItem leaf (TreeItem parent, M3GalleryModel.Entry e) {
		TreeItem item = new TreeItem (parent, SWT.NONE);
		item.setText (e.label () + "  -  " + e.title ());
		item.setData (e);
		return item;
	}

	void details (Composite parent) {
		Composite right = new Composite (parent, SWT.NONE);
		right.setLayout (new GridLayout (1, false));
		description = text (right, SWT.WRAP, 140);
		new Label (right, SWT.NONE).setText ("Feature switches (M3Features; checked rows are passed as -Dswt.widget.feature.<id>=true)");
		features = new Table (right, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION | SWT.V_SCROLL);
		features.setLayoutData (gridData (130));
		fillFeatures ();
		new Label (right, SWT.NONE).setText ("Output");
		log = text (right, SWT.V_SCROLL | SWT.H_SCROLL, 200);
	}

	Text text (Composite parent, int style, int height) {
		Text text = new Text (parent, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | style);
		text.setLayoutData (gridData (height));
		return text;
	}

	static GridData gridData (int height) {
		GridData data = new GridData (SWT.FILL, SWT.FILL, true, true);
		data.heightHint = height;
		return data;
	}

	/** One row per feature; shipped switchable rows are checkable, native defaults are checked and grey. */
	void fillFeatures () {
		for (String [] row : M3GalleryModel.featureRows (M3Features.table ())) {
			TableItem item = new TableItem (features, SWT.NONE);
			item.setText (row [1] + "  [" + row [2] + ", default " + row [3] + (M3GalleryModel.switchable (row) ? ", switchable" : "") + "]");
			item.setData (row [1]);
			item.setChecked ("NATIVE_HAS".equals (row [2]));
			item.setGrayed (!M3GalleryModel.switchable (row));
		}
		features.addListener (SWT.Selection, e -> {
			if (e.detail == SWT.CHECK && e.item.getData () != null && ((TableItem) e.item).getGrayed ()) ((TableItem) e.item).setChecked (!((TableItem) e.item).getChecked ());
		});
	}

	void select (M3GalleryModel.Entry entry) {
		selected = entry;
		description.setText (entry == null ? "" : M3GalleryModel.describe (entry));
	}

	String selectedMode () {
		String text = mode.getText ();
		return DEFAULT_MODE.equals (text) ? null : text;
	}

	/** Feature ids checked by the user (switchable rows only). */
	List<String> checkedFeatures () {
		List<String> ids = new ArrayList<> ();
		for (TableItem item : features.getItems ()) if (item.getChecked () && !item.getGrayed ()) ids.add ((String) item.getData ());
		return ids;
	}

	Map<String, String> widgetModes () {
		Map<String, String> map = new TreeMap<> ();
		if (widget.getSelectionIndex () > 0 && widgetMode.getSelectionIndex () > 0) map.put (widget.getText (), widgetMode.getText ());
		return map;
	}

	/** The command line of the selected entry; null when no entry is selected. */
	List<String> command (String mode, boolean runner) {
		if (selected == null) return null;
		return M3GalleryModel.command (M3GalleryModel.java (System.getProperty ("java.home")), System.getProperty ("java.class.path"), selected, mode,
			widgetModes (), checkedFeatures (), M3GalleryModel.inherited (System.getProperties ()), runner);
	}

	/** Starts the selected entry in a child JVM and streams its output into the log. */
	void run (String mode, boolean runner) {
		List<String> command = command (mode, runner);
		if (command == null) {
			append ("select an example first");
			return;
		}
		String tag = selected.label () + " [" + (mode == null ? "default mode" : mode) + "]";
		append ("> " + tag + ": " + String.join (" ", command.subList (3, command.size ())));
		try {
			Process process = new ProcessBuilder (command).redirectErrorStream (true).start ();
			children.add (process);
			pump (process, tag);
		} catch (IOException e) {
			append (tag + ": " + e);
		}
	}

	/** Copies a child's output into the log from a daemon thread; ends with its exit value. */
	void pump (Process process, String tag) {
		Thread thread = new Thread (() -> {
			try (BufferedReader in = new BufferedReader (new InputStreamReader (process.getInputStream (), StandardCharsets.UTF_8))) {
				in.lines ().forEach (line -> appendAsync (tag + ": " + line));
				appendAsync (tag + ": exit " + process.waitFor ());
			} catch (IOException | InterruptedException e) {
				appendAsync (tag + ": " + e);
			}
		}, "M3ExampleGallery " + tag);
		thread.setDaemon (true);
		thread.start ();
	}

	void appendAsync (String line) {
		if (!shell.isDisposed ()) shell.getDisplay ().asyncExec (() -> append (line));
	}

	void append (String line) {
		if (!log.isDisposed ()) log.append (line + "\n");
	}
}
