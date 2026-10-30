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
package org.eclipse.swt.tests.junit.m3gallery;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;
import org.eclipse.swt.snippets.m3.gallery.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

/**
 * Display tests of the gallery (hidden desktop): the window lists every entry by category and by enhanced API row,
 * selecting an entry describes it, the mode, widget-mode and feature controls shape the child command line, a child
 * JVM runs and reports its exit, the switcher slot mounts by class name, and the in-process runner passes, fails and
 * snapshots examples on a thread of their own.
 */
public class Test_M3ExampleGallery {
	static Display display;

	@BeforeAll
	static void open () {
		display = new Display ();
	}

	@AfterAll
	static void close () {
		display.dispose ();
	}

	static Object field (Object target, String name) throws ReflectiveOperationException {
		Field field = target.getClass ().getDeclaredField (name);
		field.setAccessible (true);
		return field.get (target);
	}

	static Object call (Object target, String name, Class<?> [] types, Object... args) throws ReflectiveOperationException {
		Method method = target.getClass ().getDeclaredMethod (name, types);
		method.setAccessible (true);
		return method.invoke (target, args);
	}

	static List<TreeItem> leaves (Tree tree) {
		List<TreeItem> leaves = new ArrayList<> ();
		for (TreeItem node : tree.getItems ()) collect (node, leaves);
		return leaves;
	}

	static void collect (TreeItem item, List<TreeItem> leaves) {
		if (item.getData () != null) leaves.add (item);
		for (TreeItem child : item.getItems ()) collect (child, leaves);
	}

	@Test
	void treeListsEveryEntryByCategoryAndApiRow () throws ReflectiveOperationException {
		M3ExampleGallery gallery = new M3ExampleGallery (display);
		Tree tree = (Tree) field (gallery, "tree");
		assertEquals (M3GalleryModel.CATEGORIES.size () + 1, tree.getItemCount ());
		long categorized = leaves (tree).stream ().filter (i -> !i.getParentItem ().getText ().startsWith ("M3-")).count ();
		assertEquals (M3GalleryIndex.SIZE, categorized);
		assertTrue (tree.getItem (M3GalleryModel.CATEGORIES.size ()).getText ().startsWith ("enhanced API rows ("));
		((Shell) field (gallery, "shell")).dispose ();
	}

	@Test
	void selectionDescribesAndShapesTheCommand () throws ReflectiveOperationException {
		M3ExampleGallery gallery = new M3ExampleGallery (display);
		assertNull (call (gallery, "command", new Class [] {String.class, boolean.class}, "m3", false));
		clickFirstEntry ((Tree) field (gallery, "tree"));
		assertTrue (((Text) field (gallery, "description")).getText ().contains ("class: "));
		select ((Combo) field (gallery, "widget"), "Tree");
		select ((Combo) field (gallery, "widgetMode"), "custom");
		select ((Combo) field (gallery, "mode"), "gpu");
		checkSwitchable ((Table) field (gallery, "features"));
		@SuppressWarnings ("unchecked")
		List<String> command = (List<String>) call (gallery, "command", new Class [] {String.class, boolean.class}, call (gallery, "selectedMode", new Class [0]), true);
		assertTrue (command.contains ("-Dswt.widget.mode=gpu") && command.contains ("-Dswt.widget.mode.Tree=custom"), command.toString ());
		assertTrue (command.contains ("-Dswt.widget.feature.frame-overlay=true"), command.toString ());
		assertTrue (command.contains (M3ExampleRunner.class.getName ()), command.toString ());
		((Shell) field (gallery, "shell")).dispose ();
	}

	static void clickFirstEntry (Tree tree) {
		TreeItem item = leaves (tree).get (0);
		tree.setSelection (item);
		Event event = new Event ();
		event.item = item;
		tree.notifyListeners (SWT.Selection, event);
	}

	static void select (Combo combo, String text) {
		combo.setText (text);
		combo.select (combo.indexOf (text));
	}

	/** Checks every row; the gallery keeps grey (not switchable) rows at their state and passes the switchable ones. */
	static void checkSwitchable (Table features) {
		for (TableItem row : features.getItems ()) {
			boolean before = row.getChecked ();
			row.setChecked (!before);
			Event event = new Event ();
			event.item = row;
			event.detail = SWT.CHECK;
			features.notifyListeners (SWT.Selection, event);
			if (row.getGrayed ()) assertEquals (before, row.getChecked (), String.valueOf (row.getData ()));
			if (!row.getGrayed ()) row.setChecked (true);
		}
	}

	@Test
	void childJvmRunsAndReportsItsExit () throws ReflectiveOperationException {
		M3ExampleGallery gallery = new M3ExampleGallery (display);
		Tree tree = (Tree) field (gallery, "tree");
		TreeItem button = leaves (tree).stream ().filter (i -> i.getText ().startsWith ("M3ControlsExample Button")).findFirst ().orElseThrow ();
		call (gallery, "select", new Class [] {M3GalleryModel.Entry.class}, button.getData ());
		call (gallery, "run", new Class [] {String.class, boolean.class}, "original", true);
		Text log = (Text) field (gallery, "log");
		long end = System.currentTimeMillis () + 120_000;
		while (!log.getText ().contains (": exit ") && System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		assertTrue (log.getText ().contains ("[original]: exit 0"), log.getText ());
		call (gallery, "select", new Class [] {M3GalleryModel.Entry.class}, (Object) null);
		call (gallery, "run", new Class [] {String.class, boolean.class}, null, false);
		assertTrue (log.getText ().contains ("select an example first"));
		((Shell) field (gallery, "shell")).dispose ();
	}

	/** Every toolbar button and a double click start children; disposing the gallery destroys them. */
	@Test
	void toolbarButtonsStartChildrenAndDisposeEndsThem () throws ReflectiveOperationException, InterruptedException {
		M3ExampleGallery gallery = new M3ExampleGallery (display);
		Tree tree = (Tree) field (gallery, "tree");
		TreeItem button = leaves (tree).stream ().filter (i -> i.getText ().startsWith ("M3ControlsExample Button")).findFirst ().orElseThrow ();
		call (gallery, "select", new Class [] {M3GalleryModel.Entry.class}, button.getData ());
		Shell shell = (Shell) field (gallery, "shell");
		for (Control c : ((Composite) shell.getChildren () [0]).getChildren ()) if (c instanceof Button b) b.notifyListeners (SWT.Selection, new Event ());
		tree.notifyListeners (SWT.DefaultSelection, new Event ());
		@SuppressWarnings ("unchecked")
		List<Process> children = new ArrayList<> ((List<Process>) field (gallery, "children"));
		assertEquals (8, children.size (), "Run, five modes, Run and close, double click");
		shell.dispose ();
		for (Process p : children) p.waitFor (20, java.util.concurrent.TimeUnit.SECONDS);
		assertTrue (children.stream ().noneMatch (Process::isAlive), "children end with the gallery");
	}

	@Test
	void runnerUsageWithoutArguments () throws Exception {
		M3ExampleRunner.main (new String [0]);
	}

	@Test
	void switcherSlotMountsByClassName () {
		Shell shell = new Shell (display);
		assertTrue (gallery ().mount (shell, "Composite"), "org.eclipse.swt.widgets.Composite has (Composite, int)");
		assertFalse (gallery ().mount (shell, "M3NoSuchSwitcher"));
		shell.dispose ();
	}

	/** The package-private mount through a subclass-free bridge. */
	static Mount gallery () {
		return (parent, name) -> {
			try {
				Method mount = M3ExampleGallery.class.getDeclaredMethod ("mount", Composite.class, String.class);
				mount.setAccessible (true);
				return (Boolean) mount.invoke (null, parent, name);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError (e);
			}
		};
	}

	interface Mount {
		boolean mount (Composite parent, String name);
	}

	/** Runs an example in-process on a thread of its own (its own Display). */
	static M3ExampleRunner.Result runOnThread (String className, String [] args, Path png) throws InterruptedException {
		AtomicReference<M3ExampleRunner.Result> result = new AtomicReference<> ();
		Thread thread = new Thread (() -> result.set (M3ExampleRunner.run (className, args, png)), "example");
		thread.start ();
		thread.join (180_000);
		return result.get ();
	}

	@Test
	void runnerPassesFailsAndSnapshots (@TempDir Path dir) throws InterruptedException {
		M3ExampleRunner.Result pass = runOnThread ("org.eclipse.swt.snippets.m3.gallery.widgets.M3ControlsExample", new String [] {"Label"}, dir.resolve ("label.png"));
		assertEquals ("PASS", pass.status (), pass.detail ());
		assertTrue (Files.exists (dir.resolve ("label.png")));
		M3ExampleRunner.Result fail = runOnThread ("org.eclipse.swt.snippets.m3.gallery.widgets.M3ItemWidgetsExample", new String [] {"Bogus"}, null);
		assertEquals ("FAIL", fail.status ());
		assertTrue (fail.detail ().contains ("unknown widget Bogus"), fail.detail ());
		M3ExampleRunner.Result missing = runOnThread ("org.eclipse.swt.snippets.NoSuchExample", new String [0], null);
		assertEquals ("FAIL", missing.status ());
		assertTrue (missing.tsv ("t", 1).startsWith ("t\t1\torg.eclipse.swt.snippets.NoSuchExample\t-\tFAIL\t"), missing.tsv ("t", 1));
		M3ExampleRunner.Result gallery = runOnThread (M3ExampleGallery.class.getName (), new String [0], null);
		assertEquals ("PASS", gallery.status (), gallery.detail ());
	}
}
