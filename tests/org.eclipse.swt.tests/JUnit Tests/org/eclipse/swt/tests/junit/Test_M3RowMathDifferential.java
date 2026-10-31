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
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3RowMath against the official SWT jar (enhanced M3 API, leaf 6).
 * <p>
 * Seeded scripts of public operations (add, expand, collapse, dispose, <code>setItemCount</code>,
 * <code>removeAll</code>, <code>clearAll</code>) run on a plain and on a <code>SWT.VIRTUAL</code> Tree.
 * With the official jar the test records what a preorder walk over the public API answers: the visible
 * row count, the items at four rows, the row and subtree size of every root and of each root's first
 * child, and the extent arithmetic. With this SWT, in any mode, the same columns come from M3RowMath
 * and must equal the recording. Recording directory: <code>-Dswt.viewport.recording</code>; without it
 * the test is skipped.
 * </p>
 */
public class Test_M3RowMathDifferential {
	static final boolean OFFICIAL = "official".equals (ViewportDifferential.reference ("Tree"));

	static final class Scene {
		final Shell shell;
		final Tree tree;
		int serial;

		Scene (Display display, int style) {
			shell = new Shell (display);
			shell.setBounds (10, 10, 300, 400);
			tree = new Tree (shell, SWT.MULTI | SWT.BORDER | style);
			tree.setBounds (5, 5, 280, 360);
			tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index + "d" + depth ((TreeItem) event.item)));
			shell.open ();
		}

		void reset () {
			tree.removeAll ();
			serial = 0;
		}
	}

	static int depth (TreeItem item) {
		int depth = 0;
		for (TreeItem parent = item.getParentItem (); parent != null; parent = parent.getParentItem ()) depth++;
		return depth;
	}

	/* The official answers: a preorder walk of the expanded items over the public API. */
	static void walk (TreeItem [] items, java.util.List<TreeItem> out) {
		for (TreeItem item : items) {
			out.add (item);
			if (item.getExpanded ()) walk (item.getItems (), out);
		}
	}

	static java.util.List<TreeItem> rows (Tree tree) {
		java.util.List<TreeItem> out = new ArrayList<> ();
		walk (tree.getItems (), out);
		return out;
	}

	interface Answers {
		long count (Tree tree);
		String textAt (Tree tree, long row);
		long rowOf (TreeItem item);
		long subtree (TreeItem item);
		long extent (Tree tree, long row);
		long rowAtExtent (Tree tree, long y);
	}

	static final class Walk implements Answers {
		@Override public long count (Tree tree) { return rows (tree).size (); }
		@Override public String textAt (Tree tree, long row) { return rows (tree).get ((int) row).getText (); }
		@Override public long rowOf (TreeItem item) { return rows (item.getParent ()).indexOf (item); }
		@Override public long extent (Tree tree, long row) { return row * tree.getItemHeight (); }

		@Override
		public long subtree (TreeItem item) {
			if (!item.getExpanded ()) return 1;
			java.util.List<TreeItem> out = new ArrayList<> ();
			walk (item.getItems (), out);
			return 1 + out.size ();
		}

		@Override
		public long rowAtExtent (Tree tree, long y) {
			if (y < 0) return -1;
			long row = y / Math.max (1, tree.getItemHeight ());
			return row < count (tree) ? row : -1;
		}
	}

	/* This SWT only: the M3RowMath answers (never loaded with the official jar). */
	static final class Fork implements Answers {
		@Override public long count (Tree tree) { return M3RowMath.visibleRowCount (tree); }
		@Override public String textAt (Tree tree, long row) { return M3RowMath.itemAt (tree, row).getText (); }
		@Override public long rowOf (TreeItem item) { return M3RowMath.rowOf (item); }
		@Override public long subtree (TreeItem item) { return M3RowMath.subtreeRowCount (item); }
		@Override public long extent (Tree tree, long row) { return M3RowMath.rowExtent (tree, row); }
		@Override public long rowAtExtent (Tree tree, long y) { return M3RowMath.rowAtExtent (tree, y); }
	}

	static Answers answers () {
		return OFFICIAL ? new Walk () : new Fork ();
	}

	static String at (Answers answers, Tree tree) {
		long count = answers.count (tree);
		StringBuilder out = new StringBuilder ("rows ").append (count);
		if (count == 0) return out.toString ();
		for (long row : new long [] {0, count / 3, 2 * count / 3, count - 1}) {
			out.append (' ').append (row).append ('=').append (answers.textAt (tree, row));
		}
		return out.toString ();
	}

	static String ranks (Answers answers, Tree tree) {
		StringBuilder out = new StringBuilder ();
		TreeItem [] roots = tree.getItems ();
		for (int i = 0; i < Math.min (6, roots.length); i++) {
			out.append (answers.rowOf (roots [i])).append ('/').append (answers.subtree (roots [i]));
			if (roots [i].getItemCount () > 0) out.append (':').append (answers.rowOf (roots [i].getItem (0)));
			out.append (' ');
		}
		return out.toString ().trim ();
	}

	static String extents (Answers answers, Tree tree) {
		long count = answers.count (tree), height = Math.max (1, tree.getItemHeight ());
		return answers.extent (tree, count) / height + " " + answers.rowAtExtent (tree, count * height / 2) + " "
			+ answers.rowAtExtent (tree, count * height) + " " + answers.rowAtExtent (tree, -1);
	}

	static ViewportDifferential.Op<Scene> next (Random random, StringBuilder log) {
		int kind = random.nextInt (13), arg = random.nextInt (6);
		log.append ("op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> apply (scene, kind, arg);
	}

	static String apply (Scene scene, int kind, int arg) {
		Tree tree = scene.tree;
		int n = tree.getItemCount ();
		TreeItem root = n > 0 ? tree.getItem (arg % n) : null;
		switch (kind) {
			case 0, 1 -> new TreeItem (tree, SWT.NONE, kind == 0 ? n : 0).setText ("r" + scene.serial++);
			case 2, 3 -> { if (root != null) new TreeItem (root, SWT.NONE).setText ("c" + scene.serial++); }
			case 4, 5 -> { if (root != null) root.setExpanded (kind == 4); }
			default -> deeper (scene, root, kind, arg);
		}
		return "op";
	}

	static void deeper (Scene scene, TreeItem root, int kind, int arg) {
		TreeItem child = root != null && root.getItemCount () > 0 ? root.getItem (0) : null;
		switch (kind) {
			case 6 -> { if (child != null) new TreeItem (child, SWT.NONE).setText ("g" + scene.serial++); }
			case 7 -> { if (child != null) child.setExpanded (arg % 2 == 0); }
			case 8 -> { if (root != null && arg < 2) root.dispose (); }
			case 9 -> { if (child != null) child.dispose (); }
			case 10 -> { if (root != null) root.setItemCount (arg); }
			case 11 -> scene.tree.setItemCount (arg == 0 ? 0 : arg + 2);
			default -> scene.tree.clearAll (arg == 0);
		}
	}

	static ViewportDifferential<Scene> harness (String name) {
		Answers answers = answers ();
		return ViewportDifferential.of (name, 0x4D330006L, Test_M3RowMathDifferential::next).widget ("Tree")
			.probe ("at", scene -> at (answers, scene.tree))
			.probe ("ranks", scene -> ranks (answers, scene.tree))
			.probe ("extents", scene -> extents (answers, scene.tree))
			.beforeSeed (Scene::reset);
	}

	static void run (String name, int style) throws IOException {
		Scene scene = new Scene (Display.getDefault (), style);
		try {
			harness (name).compareWithRecording (scene, ViewportDifferential.records ("Tree"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.shell.dispose ();
		}
	}

	@Test
	public void plainTreeAnswersLikeTheOfficialWalk () throws IOException {
		run ("m3-rowmath-plain", SWT.NONE);
	}

	@Test
	public void virtualTreeAnswersLikeTheOfficialWalk () throws IOException {
		run ("m3-rowmath-virtual", SWT.VIRTUAL);
	}
}
