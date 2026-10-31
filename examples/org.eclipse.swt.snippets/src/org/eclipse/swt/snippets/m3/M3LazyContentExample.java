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
package org.eclipse.swt.snippets.m3;

import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Enhanced M3 API, row M3-LAZY-CONTENT: <code>M3LazyContent</code>. A virtual tree shows a file system
 * of 1,000,000 folders described only by ids (each folder below the top level has 50 files); the
 * source is asked for the rows on screen only. The label shows how often the source was asked.
 * <p>
 * Arguments: <code>[exit]</code>: expand a folder, jump to the middle, log the counts and close.
 * </p>
 */
public class M3LazyContentExample {
	static final Logger LOG = Logger.getLogger (M3LazyContentExample.class.getName ());
	static final int FOLDERS = 1_000_000;

	/* folders are 1..FOLDERS; the files of folder f are f * 100 + 1 .. f * 100 + 50 */
	static final M3LazyContent.Source SOURCE = new M3LazyContent.Source () {
		@Override
		public int childCount (long id) {
			return id == M3LazyContent.ROOT ? FOLDERS : id <= FOLDERS ? 50 : 0;
		}

		@Override
		public long childId (long parentId, int index) {
			return parentId == M3LazyContent.ROOT ? index + 1 : parentId * 100 + index + 1;
		}

		@Override
		public String text (long id, int column) {
			if (id <= FOLDERS) return column == 0 ? String.format ("folder %07d", id) : "50 files";
			return column == 0 ? String.format ("file %d of folder %d", id % 100, id / 100) : (id * 37 % 9000 + 100) + " KB";
		}
	};

	static void report (Tree tree, Label status) {
		long [] stats = M3LazyContent.stats (tree);
		status.setText (String.format ("source asked: %d child counts, %d ids, %d rows filled", stats [M3LazyContent.CHILD_COUNTS],
			stats [M3LazyContent.CHILD_IDS], stats [M3LazyContent.FILLS]));
	}

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3LazyContent");
		shell.setLayout (new GridLayout ());
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		tree.setHeaderVisible (true);
		tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		for (String title : new String [] {"Name", "Size"}) new TreeColumn (tree, SWT.NONE).setWidth (title.equals ("Name") ? 260 : 100);
		Label status = new Label (shell, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		M3LazyContent.install (tree, SOURCE);
		tree.addListener (SWT.Expand, event -> display.asyncExec (() -> report (tree, status)));
		shell.setSize (460, 520);
		shell.open ();
		display.asyncExec (() -> report (tree, status));
		if (args.length > 0 && args [0].equals ("exit")) display.asyncExec (() -> exit (tree, status, shell));
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static void exit (Tree tree, Label status, Shell shell) {
		TreeItem first = tree.getItem (0);
		first.getText ();
		first.setExpanded (true);
		tree.setTopItem (tree.getItem (FOLDERS / 2));
		while (shell.getDisplay ().readAndDispatch ()) {
			// paint the jump
		}
		report (tree, status);
		LOG.info (status::getText);
		shell.dispose ();
	}
}
