/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Property-gated visual regression lane for the viewport rewrite.
 *
 * <p>The scenes are original SWT tests distilled from the supplied SWT/Java2s,
 * legacy JFace viewport/lazy-viewer and Virtual TreeView behavioral examples.
 * Donor source is not copied.</p>
 */
public class ViewportScreenshotRegressionTest {
	private static final String ENABLED = "swt.viewport.screenshotRegression";
	private static final String OUTPUT = "swt.viewport.screenshots";
	private static final int TABLE_ROWS = 2_000_000;
	private static final int TREE_CHILDREN = 2_000;

	private Shell shell;

	@BeforeEach
	void setUpShell () {
		shell = new Shell ();
	}

	@AfterEach
	void tearDownShell () {
		Display display = shell != null && !shell.isDisposed () ? shell.getDisplay () : Display.getCurrent ();
		if (shell != null && !shell.isDisposed ()) shell.dispose ();
		while (display != null && !display.isDisposed () && display.readAndDispatch ()) {
			// Flush native destroy/redraw work.
		}
	}

	@Test
	public void test_viewportScreenshotRegression () throws Exception {
		Assumptions.assumeTrue (Boolean.getBoolean (ENABLED), "viewport screenshot lane disabled");
		Assumptions.assumeTrue (SwtTestUtil.isGTK, "initial deterministic screenshot lane is GTK");

		Path output = Path.of (System.getProperty (OUTPUT, "target/screenshots/viewport"));
		Files.createDirectories (output);

		shell.setText ("SWT viewport screenshot regression");
		shell.setLayout (new FillLayout ());
		shell.setSize (1000, 700);

		TabFolder tabs = new TabFolder (shell, SWT.NONE);
		Table table = createTableScene (tabs);
		Tree tree = createTreeScene (tabs);
		shell.open ();
		drainEvents (120);

		captureTableCheckedSelection (tabs, table, output);
		captureTreeResidencySequence (tabs, tree, output);

		assertTrue (Files.size (output.resolve ("table-checked-selection.png")) > 0);
		assertTrue (Files.size (output.resolve ("tree-pinned-expanded.png")) > 0);
		assertTrue (Files.size (output.resolve ("tree-pinned-collapsed.png")) > 0);
		assertTrue (Files.size (output.resolve ("tree-pinned-restored.png")) > 0);
	}

	private Table createTableScene (TabFolder tabs) {
		TabItem tab = new TabItem (tabs, SWT.NONE);
		tab.setText ("2M virtual Table");
		Composite root = new Composite (tabs, SWT.NONE);
		root.setLayout (new FillLayout ());
		tab.setControl (root);

		Table table = new Table (
				root, SWT.VIRTUAL | SWT.CHECK | SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		for (int i = 0; i < 4; i++) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText ("Column " + i);
			column.setWidth (210);
		}
		table.addListener (SWT.SetData, event -> {
			int index = event.index;
			TableItem item = (TableItem) event.item;
			item.setText (new String[] {
					"row " + index,
					"group " + (index >>> 10),
					"hex " + Integer.toHexString (index),
					"mask " + (index & 63)
			});
			if ((index & 31) == 0) item.setChecked (true);
		});
		table.setItemCount (TABLE_ROWS);
		return table;
	}

	private Tree createTreeScene (TabFolder tabs) {
		TabItem tab = new TabItem (tabs, SWT.NONE);
		tab.setText ("Virtual Tree residency");
		Composite root = new Composite (tabs, SWT.NONE);
		root.setLayout (new FillLayout ());
		tab.setControl (root);

		Tree tree = new Tree (root, SWT.VIRTUAL | SWT.CHECK | SWT.MULTI | SWT.BORDER);
		tree.setHeaderVisible (true);
		TreeColumn column = new TreeColumn (tree, SWT.NONE);
		column.setText ("Logical Tree");
		column.setWidth (760);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			item.setText ("depth=" + depth (item) + " index=" + event.index);
		});
		tree.setItemCount (64);
		return tree;
	}

	private void captureTableCheckedSelection (TabFolder tabs, Table table, Path output) throws Exception {
		tabs.setSelection (0);
		table.setTopIndex (0);
		table.setSelection (new int[] {0, 1, 32, 33, 64});
		table.showSelection ();
		drainEvents (120);
		capture (
				"table-checked-selection", table, output,
				"table.logicalRows=" + table.getItemCount () + "\n"
				+ "table.topIndex=" + table.getTopIndex () + "\n"
				+ "table.selectionCount=" + table.getSelectionCount () + "\n"
				+ scrollBarText ("h", table.getHorizontalBar ())
				+ scrollBarText ("v", table.getVerticalBar ()));
	}

	private void captureTreeResidencySequence (
			TabFolder tabs, Tree tree, Path output) throws Exception {
		tabs.setSelection (1);
		TreeItem branch = tree.getItem (17);
		branch.setText ("screenshot root 17");
		branch.setItemCount (TREE_CHILDREN);
		TreeItem child = branch.getItem (10);
		child.setText ("pinned child 10");
		child.setChecked (true);
		child.setGrayed (true);

		branch.setExpanded (true);
		tree.setTopItem (branch);
		tree.setSelection (child);
		drainEvents (150);
		capture (
				"tree-pinned-expanded", tree, output,
				treeSidecar (tree, branch, child));

		branch.setExpanded (false);
		tree.setTopItem (branch);
		tree.setSelection (branch);
		drainEvents (220);
		assertEquals (TREE_CHILDREN, branch.getItemCount ());
		capture (
				"tree-pinned-collapsed", tree, output,
				treeSidecar (tree, branch, child));

		branch.setExpanded (true);
		tree.setTopItem (branch);
		tree.setSelection (child);
		drainEvents (220);
		assertSame (child, branch.getItem (10));
		assertTrue (child.getChecked ());
		assertTrue (child.getGrayed ());
		capture (
				"tree-pinned-restored", tree, output,
				treeSidecar (tree, branch, child));
	}

	private String treeSidecar (Tree tree, TreeItem branch, TreeItem child) {
		StringBuilder out = new StringBuilder ();
		out.append ("tree.rootCount=").append (tree.getItemCount ()).append ('\n');
		out.append ("tree.branchChildCount=").append (branch.getItemCount ()).append ('\n');
		out.append ("tree.branchExpanded=").append (branch.getExpanded ()).append ('\n');
		out.append ("tree.childChecked=").append (child.getChecked ()).append ('\n');
		out.append ("tree.childGrayed=").append (child.getGrayed ()).append ('\n');
		out.append ("tree.childText=").append (child.getText ()).append ('\n');
		out.append ("tree.selectionCount=").append (tree.getSelectionCount ()).append ('\n');
		out.append (reflectValue (
				tree, "virtualResidentChildCount",
				new Class<?>[] {TreeItem.class}, new Object[] {branch},
				"tree.nativeResidentChildren"));
		out.append (reflectValue (
				tree, "virtualVisibleRowCount",
				new Class<?>[0], new Object[0],
				"tree.virtualVisibleRows"));
		out.append (reflectFieldMethod (
				tree, "virtualTopology", "materializedCount",
				"tree.topology.materializedCount"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "firstVisible",
				"tree.viewport.firstVisible"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "visibleCount",
				"tree.viewport.visibleCount"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "paintStart",
				"tree.viewport.paintStart"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "paintEndExclusive",
				"tree.viewport.paintEndExclusive"));
		out.append (scrollBarText ("h", tree.getHorizontalBar ()));
		out.append (scrollBarText ("v", tree.getVerticalBar ()));
		return out.toString ();
	}

	private void capture (String name, Control target, Path output, String sidecar) throws Exception {
		target.getShell ().layout (true, true);
		target.redraw ();
		target.update ();
		drainEvents (80);

		Point size = target.getSize ();
		assertTrue (size.x > 0 && size.y > 0);
		Image image = new Image (target.getDisplay (), size.x, size.y);
		try {
			GC gc = new GC (image);
			try {
				if (!target.print (gc)) {
					gc.dispose ();
					gc = new GC (target);
					try {
						gc.copyArea (image, 0, 0);
					} finally {
						gc.dispose ();
					}
					gc = null;
				}
			} finally {
				if (gc != null && !gc.isDisposed ()) gc.dispose ();
			}
			Path png = output.resolve (name + ".png");
			ImageLoader loader = new ImageLoader ();
			loader.data = new ImageData[] {image.getImageData ()};
			loader.save (png.toString (), SWT.IMAGE_PNG);
			String text = "scenario=" + name + "\n"
					+ "platform=" + SWT.getPlatform () + "\n"
					+ "bounds=" + target.getBounds () + "\n"
					+ "client=" + target.getClientArea () + "\n"
					+ "screenshot.sha256=" + sha256 (png) + "\n"
					+ sidecar;
			Files.writeString (
					output.resolve (name + ".txt"), text, StandardCharsets.UTF_8);
		} finally {
			image.dispose ();
		}
	}

	private void drainEvents (long millis) throws InterruptedException {
		Display display = shell.getDisplay ();
		long deadline = System.currentTimeMillis () + millis;
		do {
			while (display.readAndDispatch ()) {
				// Drain async layout, redraw and collapse-compaction work.
			}
			Thread.sleep (5);
		} while (System.currentTimeMillis () < deadline);
	}

	private static String scrollBarText (String prefix, ScrollBar bar) {
		if (bar == null || bar.isDisposed ()) return prefix + ".scrollbar=<none>\n";
		return prefix + ".selection=" + bar.getSelection () + "\n"
				+ prefix + ".minimum=" + bar.getMinimum () + "\n"
				+ prefix + ".maximum=" + bar.getMaximum () + "\n"
				+ prefix + ".thumb=" + bar.getThumb () + "\n"
				+ prefix + ".increment=" + bar.getIncrement () + "\n"
				+ prefix + ".pageIncrement=" + bar.getPageIncrement () + "\n"
				+ prefix + ".visible=" + bar.getVisible () + "\n";
	}

	private static String reflectValue (
			Object target, String methodName, Class<?>[] parameterTypes, Object[] args,
			String label) {
		try {
			Method method = target.getClass ().getDeclaredMethod (methodName, parameterTypes);
			method.setAccessible (true);
			return label + "=" + method.invoke (target, args) + "\n";
		} catch (ReflectiveOperationException | RuntimeException unavailable) {
			return label + "=<unavailable>\n";
		}
	}

	private static String reflectFieldMethod (
			Object target, String fieldName, String methodName, String label) {
		try {
			Field field = target.getClass ().getDeclaredField (fieldName);
			field.setAccessible (true);
			Object owner = field.get (target);
			if (owner == null) return label + "=<none>\n";
			Method method = owner.getClass ().getDeclaredMethod (methodName);
			method.setAccessible (true);
			return label + "=" + method.invoke (owner) + "\n";
		} catch (ReflectiveOperationException | RuntimeException unavailable) {
			return label + "=<unavailable>\n";
		}
	}

	private static int depth (TreeItem item) {
		int result = 0;
		while ((item = item.getParentItem ()) != null) result++;
		return result;
	}

	private static String sha256 (Path path) throws IOException {
		try {
			return HexFormat.of ().formatHex (
					MessageDigest.getInstance ("SHA-256").digest (Files.readAllBytes (path)));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError (impossible);
		}
	}
}
