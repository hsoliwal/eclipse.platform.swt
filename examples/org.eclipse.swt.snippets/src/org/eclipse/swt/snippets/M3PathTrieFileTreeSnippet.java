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
package org.eclipse.swt.snippets;

/*
 * A lazy file-system Tree driven by path ids, whose expansion survives a full
 * refresh.
 *
 * Every node is one int path id in an M3TreePathTrie whose segments are pooled
 * file names (M3LabelPool): "S:/a/b/c.txt" is the id of "c.txt" under the id of
 * "b" under the id of "a". The item keeps only its path id; its file path is
 * rebuilt from the trie when the children are listed (lazy, on SetData).
 * Expand and Collapse set the EXPANDED flag of the path; "Refresh" disposes
 * every item (removeAll) and rebuilds the roots; the flagged paths are then
 * re-expanded, parents first, without any String path map.
 *
 * Usage: M3PathTrieFileTreeSnippet [root-directory]
 * -Dm3.snapshot=<dir> expands a few folders, saves before/after-refresh PNGs to
 * <dir> and exits (hidden-desktop evidence). Logging goes through SwtLog.
 */
import java.io.*;
import java.util.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.m3.collections.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class M3PathTrieFileTreeSnippet {
	static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.snippets.M3PathTrieFileTreeSnippet");
	static final M3LabelPool NAMES = new M3LabelPool (1024);
	static final M3TreePathTrie PATHS = new M3TreePathTrie (NAMES);
	static int rootPath;
	static int listed;

	/** Child names of the directory of {@code path}, sorted, directories marked by a trailing separator. */
	static String [] children (int path) {
		File dir = new File (PATHS.appendPath (path, new StringBuilder (), "/").toString ());
		String [] names = dir.list ();
		if (names == null) return new String [0];
		Arrays.sort (names, String.CASE_INSENSITIVE_ORDER);
		listed++;
		return names;
	}

	static void bind (TreeItem item, int parentPath, int index) {
		String [] names = children (parentPath);
		if (index >= names.length) return;
		int path = PATHS.childNamed (parentPath, names [index]);
		item.setData (path);
		item.setText (names [index]);
		File file = new File (PATHS.appendPath (path, new StringBuilder (), "/").toString ());
		item.setItemCount (file.isDirectory () ? Math.max (0, children (path).length) : 0);
	}

	static int pathOf (TreeItem item) {
		return (Integer) item.getData ();
	}

	/** Re-expands every flagged path, parents first: a full refresh does not lose the expansion. */
	static int restore (Tree tree) {
		int [] restored = new int [1];
		for (int depth = PATHS.depth (rootPath) + 1; depth < 64; depth++) {
			int level = depth;
			int found = PATHS.forEachFlagged (M3TreePathTrie.EXPANDED, path -> {
				if (PATHS.depth (path) != level) return;
				TreeItem item = itemOf (tree, path);
				if (item != null) {
					item.getText (); /* materializes a virtual item: SetData binds its path id and child count */
					item.setExpanded (true);
					restored [0]++;
				}
			});
			if (found == 0) break;
		}
		return restored [0];
	}

	/** The item of {@code path}, found by its index among its parent's children, or null. */
	static TreeItem itemOf (Tree tree, int path) {
		int parent = PATHS.parent (path);
		String name = NAMES.text (PATHS.segment (path));
		int index = Arrays.asList (children (parent)).indexOf (name);
		if (index < 0) return null;
		if (parent == rootPath) return tree.getItem (index);
		TreeItem parentItem = itemOf (tree, parent);
		return parentItem == null || !parentItem.getExpanded () ? null : parentItem.getItem (index);
	}

	static void refresh (Tree tree) {
		tree.removeAll ();
		tree.setItemCount (children (rootPath).length);
		int restored = restore (tree);
		LOG.info (() -> "refresh: " + restored + " folders re-expanded from path flags; " + PATHS.size () + " paths, " + NAMES.size () + " names pooled");
	}

	public static void main (String [] args) {
		String root = args.length > 0 ? args [0] : new File (".").getAbsolutePath ();
		rootPath = PATHS.internPath (root.replace ('\\', '/'), '/');
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3TreePathTrie: " + root);
		shell.setLayout (new GridLayout (1, false));
		Button refresh = new Button (shell, SWT.PUSH);
		refresh.setText ("Refresh (removeAll + rebuild)");
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item, parent = item.getParentItem ();
			bind (item, parent == null ? rootPath : pathOf (parent), event.index);
		});
		tree.addListener (SWT.Expand, event -> PATHS.flag (pathOf ((TreeItem) event.item), M3TreePathTrie.EXPANDED, true));
		tree.addListener (SWT.Collapse, event -> PATHS.flag (pathOf ((TreeItem) event.item), M3TreePathTrie.EXPANDED, false));
		refresh.addListener (SWT.Selection, event -> refresh (tree));
		tree.setItemCount (children (rootPath).length);
		shell.setSize (520, 640);
		shell.open ();
		if (System.getProperty ("m3.snapshot") != null) demo (display, shell, tree);
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	/** Expands the first folders two levels deep, snapshots, refreshes, snapshots again, compares, exits. */
	static void demo (Display display, Shell shell, Tree tree) {
		display.timerExec (800, () -> {
			for (int i = 0, opened = 0; i < tree.getItemCount () && opened < 3; i++) {
				TreeItem item = tree.getItem (i);
				item.getText ();
				if (item.getItemCount () == 0) continue;
				expand (item);
				opened++;
				for (int j = 0; j < Math.min (item.getItemCount (), 4); j++) {
					item.getItem (j).getText ();
					if (item.getItem (j).getItemCount () > 0) {
						expand (item.getItem (j));
						break;
					}
				}
			}
			display.timerExec (800, () -> {
				String before = save (display, shell, "tree-before-refresh");
				int expanded = PATHS.forEachFlagged (M3TreePathTrie.EXPANDED, path -> { });
				refresh (tree);
				display.timerExec (800, () -> {
					String after = save (display, shell, "tree-after-refresh");
					LOG.info (() -> "expanded paths " + expanded + "; directories listed " + listed + "; evidence " + before + " " + after);
					shell.close ();
				});
			});
		});
	}

	static void expand (TreeItem item) {
		item.setExpanded (true);
		PATHS.flag (pathOf (item), M3TreePathTrie.EXPANDED, true);
	}

	static String save (Display display, Shell shell, String name) {
		Rectangle area = shell.getClientArea ();
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (image);
		shell.print (gc);
		gc.dispose ();
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		String file = System.getProperty ("m3.snapshot") + "/" + name + ".png";
		loader.save (file, SWT.IMAGE_PNG);
		image.dispose ();
		return file;
	}
}
