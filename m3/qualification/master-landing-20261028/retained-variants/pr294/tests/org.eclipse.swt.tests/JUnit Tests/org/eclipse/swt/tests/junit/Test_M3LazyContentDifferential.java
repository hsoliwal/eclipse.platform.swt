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
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3LazyContent against the official SWT jar (enhanced M3 API, row M3-LAZY-CONTENT).
 * A virtual Tree and Table show a synthetic source of ids. The official run drives them with a hand-written
 * <code>SWT.SetData</code> listener (the stock idiom: ids as item data, texts and child counts per request);
 * this SWT installs M3LazyContent. Both run the same expand, collapse, scroll and source-change steps and
 * record the texts of the rows on screen and the counts.
 */
public class Test_M3LazyContentDifferential {
	static final class Scene {
		final Shell shell;
		final Tree tree;
		final Table table;
		int roots = 300;

		Scene (Display display) {
			shell = new Shell (display);
			shell.setBounds (10, 10, 520, 330);
			tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
			tree.setBounds (5, 5, 250, 280);
			table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
			table.setBounds (265, 5, 240, 280);
			shell.open ();
		}

		int childCount (long id) {
			return id == -1 ? roots : id < 100_000 ? (int) (id % 4) : 0;
		}

		long childId (long parent, int index) {
			return parent == -1 ? index + 1 : parent * 10 + index;
		}

		String text (long id) {
			return "id " + id;
		}
	}

	/* the official run: the stock SetData idiom */
	static void stock (Scene scene) {
		scene.tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item, parent = item.getParentItem ();
			long id = scene.childId (parent == null ? -1 : (Long) parent.getData (), event.index);
			item.setData (id);
			item.setText (scene.text (id));
			item.setItemCount (scene.childCount (id));
		});
		scene.table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText (scene.text (scene.childId (-1, event.index))));
		scene.tree.setItemCount (scene.childCount (-1));
		scene.table.setItemCount (scene.childCount (-1));
	}

	/* This SWT only. */
	static final class Fork implements M3LazyContent.Source {
		final Scene scene;

		Fork (Scene scene) {
			this.scene = scene;
			M3LazyContent.install (scene.tree, this);
			M3LazyContent.install (scene.table, this);
		}

		@Override public int childCount (long id) { return scene.childCount (id); }
		@Override public long childId (long parentId, int index) { return scene.childId (parentId, index); }
		@Override public String text (long id, int column) { return scene.text (id); }

		static void refresh (Scene scene) {
			M3LazyContent.refresh (scene.tree, null);
			M3LazyContent.refresh (scene.table);
		}
	}

	static String apply (Scene scene, int kind, int arg) {
		int n = scene.tree.getItemCount ();
		TreeItem root = n > 0 ? scene.tree.getItem (arg % n) : null;
		if (root != null) root.getText ();
		switch (kind) {
			case 0, 1 -> { if (root != null) root.setExpanded (true); }
			case 2 -> { if (root != null) root.setExpanded (false); }
			case 3 -> { if (root != null) scene.tree.setTopItem (root); }
			case 4 -> scene.table.setTopIndex (arg * 3);
			default -> change (scene, arg);
		}
		return "op";
	}

	static void change (Scene scene, int arg) {
		scene.roots = 50 + arg * 7;
		if (!M3OfficialScene.OFFICIAL) {
			Fork.refresh (scene);
			return;
		}
		scene.tree.setItemCount (scene.roots);
		scene.tree.clearAll (true);
		scene.table.setItemCount (scene.roots);
		scene.table.clearAll ();
	}

	static String rows (Scene scene) {
		StringBuilder out = new StringBuilder ().append (scene.tree.getItemCount ()).append (' ').append (scene.table.getItemCount ());
		TreeItem top = scene.tree.getTopItem ();
		for (int i = 0; i < 12 && top != null; i++, top = M3OfficialScene.nextVisible (top)) out.append (' ').append (top.getText ());
		int first = scene.table.getTopIndex ();
		for (int i = first; i < Math.min (first + 12, scene.table.getItemCount ()); i++) out.append (' ').append (scene.table.getItem (i).getText ());
		return out.toString ();
	}

	static ViewportDifferential.Op<Scene> next (Random random, StringBuilder log) {
		int kind = random.nextInt (6), arg = random.nextInt (40);
		log.append ("op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> apply (scene, kind, arg);
	}

	@Test
	public void lazyWidgetsAnswerLikeTheStockIdiomOnTheOfficialJar () throws IOException {
		Scene scene = new Scene (Display.getDefault ());
		if (M3OfficialScene.OFFICIAL) stock (scene);
		else new Fork (scene);
		ViewportDifferential<Scene> harness = ViewportDifferential.of ("m3-lazycontent", 0x4D330014L, Test_M3LazyContentDifferential::next).widget ("Tree")
			.probe ("rows", Test_M3LazyContentDifferential::rows)
			.beforeSeed (s -> change (s, 35));
		try {
			harness.compareWithRecording (scene, ViewportDifferential.records ("Tree"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.shell.dispose ();
		}
	}
}
