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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3LazyContent (enhanced M3 API, row M3-LAZY-CONTENT). The source is a synthetic tree of
 * ids (id = parent * 1000 + index + 1; the top level has <code>ROOTS</code> rows, every id below 10^6
 * has three children); the oracle is the source itself. The class passes unchanged in every startup
 * mode (the mode differential is the class under <code>viewport</code> and <code>original</code>).
 */
public class Test_M3LazyContent {
	/* native rows in the original mode cost seconds per 10^5: the stock tree gets fewer roots */
	static final int ROOTS = "original".equals (System.getProperty ("swt.widget.mode")) ? 20_000 : 1_000_000;
	Display display;
	Shell shell;

	/* the synthetic source, counting its requests */
	static final class Ids implements M3LazyContent.Source {
		int roots = ROOTS, counts, texts;

		@Override
		public int childCount (long id) {
			counts++;
			return id == M3LazyContent.ROOT ? roots : id < 1_000_000 ? 3 : 0;
		}

		@Override
		public long childId (long parentId, int index) {
			return parentId == M3LazyContent.ROOT ? index + 1 : parentId * 1000 + index + 1;
		}

		@Override
		public String text (long id, int column) {
			texts++;
			return "id " + id + " c" + column;
		}
	}

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private void flush () {
		while (display.readAndDispatch ()) {
			// paints and SetData requests
		}
	}

	@Test
	public void test_treeAsksOnlyForTheRowsOnScreen () {
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		Ids ids = new Ids ();
		M3LazyContent.install (tree, ids);
		assertEquals (ROOTS, tree.getItemCount ());
		assertEquals (1, ids.counts, "only the top-level count");
		flush ();
		long fills = M3LazyContent.stats (tree) [M3LazyContent.FILLS];
		assertTrue (fills > 0 && fills < 200, "rows filled for the first paint: " + fills);
		TreeItem top = tree.getItem (0);
		assertEquals ("id 1 c0", top.getText ());
		assertEquals (1, M3LazyContent.idOf (top));
		assertEquals (3, top.getItemCount ());
		top.setExpanded (true);
		assertEquals ("id 1002 c0", top.getItem (1).getText ());
		assertEquals (1002, M3LazyContent.idOf (top.getItem (1)));
		tree.setTopItem (tree.getItem (ROOTS / 2));
		flush ();
		assertEquals ("id " + (ROOTS / 2 + 1) + " c0", tree.getItem (ROOTS / 2).getText ());
		assertTrue (M3LazyContent.stats (tree) [M3LazyContent.FILLS] < 600, "a jump fills the window, not the rows between");
	}

	@Test
	public void test_tableWithColumnsAndRefresh () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		for (int c = 0; c < 3; c++) new TableColumn (table, SWT.NONE).setWidth (90);
		Ids ids = new Ids ();
		ids.roots = 50_000;
		M3LazyContent.install (table, ids);
		assertEquals ("id 7 c2", table.getItem (6).getText (2));
		assertEquals (7, M3LazyContent.idOf (table.getItem (6)));
		ids.roots = 10;
		M3LazyContent.refresh (table);
		assertEquals (10, table.getItemCount ());
		assertEquals ("id 10 c1", table.getItem (9).getText (1));
		long [] stats = M3LazyContent.stats (table);
		assertEquals (2, stats [M3LazyContent.CHILD_COUNTS]);
		assertTrue (stats [M3LazyContent.CHILD_IDS] >= 2 && stats [M3LazyContent.FILLS] == stats [M3LazyContent.CHILD_IDS]);
	}

	@Test
	public void test_refreshAsksTheStructureBelowARowAgain () {
		Tree tree = new Tree (shell, SWT.VIRTUAL);
		Ids ids = new Ids ();
		ids.roots = 4;
		M3LazyContent.install (tree, ids);
		TreeItem first = tree.getItem (0);
		first.getText ();
		first.setExpanded (true);
		assertEquals ("id 1001 c0", first.getItem (0).getText ());
		int counts = ids.counts;
		M3LazyContent.refresh (tree, first);
		assertEquals (counts + 1, ids.counts);
		assertEquals ("id 1003 c0", first.getItem (2).getText ());
		ids.roots = 2;
		M3LazyContent.refresh (tree, null);
		assertEquals (2, tree.getItemCount ());
		assertEquals ("id 2 c0", tree.getItem (1).getText ());
		Tree other = new Tree (shell, SWT.VIRTUAL);
		TreeItem foreign = new TreeItem (other, SWT.NONE);
		assertEquals (M3LazyContent.ROOT, M3LazyContent.idOf (foreign));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.refresh (tree, foreign));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.refresh (other, null));
		TableItem plainRow = new TableItem (new Table (shell, SWT.NONE), SWT.NONE);
		assertEquals (M3LazyContent.ROOT, M3LazyContent.idOf (plainRow));
	}

	@Test
	public void test_installReplacesAndUninstallStops () {
		Tree tree = new Tree (shell, SWT.VIRTUAL);
		Ids first = new Ids (), second = new Ids ();
		first.roots = 5;
		second.roots = 7;
		M3LazyContent.install (tree, first);
		M3LazyContent.install (tree, second);
		assertEquals (7, tree.getItemCount ());
		tree.getItem (3).getText ();
		assertEquals (0, first.texts);
		M3LazyContent.uninstall (tree);
		M3LazyContent.uninstall (tree);
		assertArrayEquals (new long [3], M3LazyContent.stats (tree));
		int texts = second.texts;
		tree.getItem (5).getText ();
		assertEquals (texts, second.texts);
	}

	@Test
	public void test_argumentsAreChecked () {
		Tree tree = new Tree (shell, SWT.VIRTUAL);
		Table plain = new Table (shell, SWT.NONE);
		Ids ids = new Ids ();
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.install ((Tree) null, ids));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.install (tree, null));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.install (plain, ids));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.install (new Tree (shell, SWT.NONE), ids));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.refresh (plain));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.refresh ((Tree) null, null));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.idOf ((TreeItem) null));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.idOf ((TableItem) null));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.stats (null));
		assertThrows (IllegalArgumentException.class, () -> M3LazyContent.uninstall (null));
		tree.dispose ();
		assertThrows (SWTException.class, () -> M3LazyContent.install (tree, ids));
		assertThrows (SWTException.class, () -> M3LazyContent.stats (tree));
	}
}
