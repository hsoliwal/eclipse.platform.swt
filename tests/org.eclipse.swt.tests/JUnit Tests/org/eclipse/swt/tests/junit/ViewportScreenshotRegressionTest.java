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
import java.nio.file.Path;
import java.security.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.ViewportPaintGraph.Affine;
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
	private static final String NATIVE_CAPTURE = "swt.viewport.screenshots.native";
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
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
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
		ViewportChromeScene viewport = createViewportChromeScene (tabs);
		GraphicsStateScene graphics = createGraphicsStateScene (tabs);
		shell.open ();
		drainEvents (120);

		captureTableCheckedSelection (tabs, table, output);
		captureTreeResidencySequence (tabs, tree, output);
		captureViewportChromeSequence (tabs, viewport, output);
		captureGraphicsState (tabs, graphics, output);

		assertTrue (Files.size (output.resolve ("table-checked-selection.png")) > 0);
		assertTrue (Files.size (output.resolve ("tree-pinned-expanded.png")) > 0);
		assertTrue (Files.size (output.resolve ("tree-pinned-collapsed.png")) > 0);
		assertTrue (Files.size (output.resolve ("tree-pinned-restored.png")) > 0);
		assertTrue (Files.size (output.resolve ("viewport-vertical-scroll.png")) > 0);
		assertTrue (Files.size (output.resolve ("viewport-horizontal-scroll.png")) > 0);
		assertTrue (Files.size (output.resolve ("viewport-narrow.png")) > 0);
		assertTrue (Files.size (output.resolve ("viewport-wide.png")) > 0);
		assertTrue (Files.size (output.resolve ("viewport-affine-clip-stroke.png")) > 0);
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
            if ((index & 31) == 0) {
                item.setChecked(true);
            }
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



	private static final class GraphicsStateScene {
		final Canvas canvas;
		final ViewportPaintGraph graph;
		final long [] paints = {0};
		Rectangle lastIncomingClip;

		GraphicsStateScene (Canvas canvas, ViewportPaintGraph graph) {
			this.canvas = canvas;
			this.graph = graph;
		}
	}

	private GraphicsStateScene createGraphicsStateScene (TabFolder tabs) {
		TabItem tab = new TabItem (tabs, SWT.NONE);
		tab.setText ("retained affine/clip/stroke/path");

		Canvas canvas = new Canvas (tabs, SWT.DOUBLE_BUFFERED | SWT.BORDER);
		tab.setControl (canvas);

		ViewportPaintGraph graph = new ViewportPaintGraph ();
		float [] elements = new float [6];
		Transform transform = new Transform (canvas.getDisplay ());
		try {
			transform.translate (180, 120);
			transform.rotate (14);
			transform.scale (1.12f, 0.92f);
			transform.getElements (elements);
		} finally {
			transform.dispose ();
		}
		int graphics = graph.group (
				graph.root (),
				new Affine (
						elements [0], elements [1], elements [2],
						elements [3], elements [4], elements [5]));
		graph.setLayer (graphics, 40);
		graph.setClip (graphics, -60, -50, 520, 300);
		graph.setStroke (graphics, 3, SWT.LINE_DASH, SWT.CAP_ROUND, SWT.JOIN_BEVEL);

		PathData retainedPath = new PathData ();
		retainedPath.types = new byte[] {
				SWT.PATH_MOVE_TO, SWT.PATH_CUBIC_TO, SWT.PATH_LINE_TO};
		retainedPath.points = new float[] {
				0, 40,
				80, -25, 160, 120, 250, 30,
				340, 140};
		graph.drawPath (graphics, retainedPath);
		graph.drawRectangle (graphics, 0, 0, 360, 190);
		graph.line (graphics, -80, 95, 460, 95);
		graph.text (graphics, "affine + clip + stroke + retained path", 24, 132, true);

		GraphicsStateScene scene = new GraphicsStateScene (canvas, graph);
		canvas.addListener (SWT.Paint, event -> {
			scene.paints[0]++;
			GC gc = event.gc;
			scene.lastIncomingClip = gc.getClipping ();

			Rectangle oldClip = gc.getClipping ();
			int oldLineWidth = gc.getLineWidth ();
			int oldLineStyle = gc.getLineStyle ();
			int oldLineCap = gc.getLineCap ();
			int oldLineJoin = gc.getLineJoin ();
			try {
				gc.setClipping (new Rectangle (-60, -50, 520, 300));
				gc.setLineWidth (3);
				gc.setLineStyle (SWT.LINE_DASH);
				gc.setLineCap (SWT.CAP_ROUND);
				gc.setLineJoin (SWT.JOIN_BEVEL);
				graph.replay (
						gc, Affine.IDENTITY,
						new Rectangle (-60, -50, 520, 300));
			} finally {
				gc.setClipping (oldClip);
				gc.setLineWidth (oldLineWidth);
				gc.setLineStyle (oldLineStyle);
				gc.setLineCap (oldLineCap);
				gc.setLineJoin (oldLineJoin);
			}
		});
		return scene;
	}

	private void captureGraphicsState (
			TabFolder tabs, GraphicsStateScene scene, Path output) throws Exception {
		tabs.setSelection (3);
		shell.setSize (1000, 700);
		drainEvents (120);
		capture (
				"viewport-affine-clip-stroke", scene.canvas, output,
				"graphics.paints=" + scene.paints[0] + "\n"
				+ "graphics.incomingClip=" + scene.lastIncomingClip + "\n"
				+ "graphics.transform=translate(180,120),rotate(14),scale(1.12,0.92)\n"
				+ "graphics.localClip=(-60,-50,520,300)\n"
				+ "graphics.stroke=width:3,style:DASH,cap:ROUND,join:BEVEL\n"
				+ "graphics.retainedPath=true\n"
				+ "graphics.geometryNodes=" + scene.graph.geometryNodeCount () + "\n");
	}

	private static final class ViewportChromeScene {
		final Composite root;
		final Canvas header;
		final ScrolledComposite scroller;
		final int[] headerOriginX = {0};
		final long[] headerPaints = {0};
		final long[] bodyPaints = {0};

		ViewportChromeScene (Composite root, Canvas header, ScrolledComposite scroller) {
			this.root = root;
			this.header = header;
			this.scroller = scroller;
		}
	}

	private ViewportChromeScene createViewportChromeScene (TabFolder tabs) {
		TabItem tab = new TabItem (tabs, SWT.NONE);
		tab.setText ("Viewport chrome");
		Composite root = new Composite (tabs, SWT.NONE);
		root.setLayout (new GridLayout (1, false));
		tab.setControl (root);

		Canvas header = new Canvas (root, SWT.DOUBLE_BUFFERED | SWT.BORDER);
		GridData headerData = new GridData (SWT.FILL, SWT.CENTER, true, false);
		headerData.heightHint = 30;
		header.setLayoutData (headerData);

		ScrolledComposite scroller = new ScrolledComposite (
				root, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		scroller.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));

		Canvas body = new Canvas (scroller, SWT.DOUBLE_BUFFERED);
		body.setSize (1800, 6000);
		scroller.setContent (body);

		ViewportChromeScene scene = new ViewportChromeScene (root, header, scroller);
		header.addListener (SWT.Paint, event -> {
			scene.headerPaints[0]++;
			int x = -scene.headerOriginX[0];
			for (int column = 0; column < 8; column++) {
				event.gc.drawRectangle (x, 0, 224, 29);
				event.gc.drawText ("column " + column, x + 8, 6, true);
				x += 224;
			}
		});
		body.addListener (SWT.Paint, event -> {
			scene.bodyPaints[0]++;
			Rectangle clip = event.gc.getClipping ();
			int first = Math.max (0, clip.y / 22);
			int last = Math.min (273, (clip.y + clip.height + 21) / 22);
			for (int row = first; row < last; row++) {
				int y = row * 22;
				event.gc.drawText ("row " + row, 8, y + 3, true);
				for (int column = 1; column < 8; column++) {
					int x = column * 224;
					event.gc.drawLine (x, y, x, y + 22);
				}
				event.gc.drawLine (0, y + 21, 1800, y + 21);
			}
		});

		ScrollBar horizontal = scroller.getHorizontalBar ();
		if (horizontal != null) {
			horizontal.addListener (SWT.Selection, event -> {
				scene.headerOriginX[0] = scroller.getOrigin ().x;
				header.redraw ();
			});
		}
		return scene;
	}

	private void captureViewportChromeSequence (
			TabFolder tabs, ViewportChromeScene scene, Path output) throws Exception {
		tabs.setSelection (2);
		shell.setSize (1000, 700);
		drainEvents (120);

		scene.scroller.setOrigin (0, 1400);
		drainEvents (120);
		capture (
				"viewport-vertical-scroll", scene.root, output,
				viewportChromeSidecar (scene));

		scene.scroller.setOrigin (620, 1400);
		scene.headerOriginX[0] = scene.scroller.getOrigin ().x;
		scene.header.redraw ();
		drainEvents (120);
		capture (
				"viewport-horizontal-scroll", scene.root, output,
				viewportChromeSidecar (scene));

		shell.setSize (520, 420);
		drainEvents (120);
		capture (
				"viewport-narrow", scene.root, output,
				viewportChromeSidecar (scene));

		shell.setSize (1000, 700);
		drainEvents (120);
		capture (
				"viewport-wide", scene.root, output,
				viewportChromeSidecar (scene));
	}

	private String viewportChromeSidecar (ViewportChromeScene scene) {
		Point origin = scene.scroller.getOrigin ();
		return "viewport.origin=" + origin + "\n"
				+ "viewport.headerOriginX=" + scene.headerOriginX[0] + "\n"
				+ "viewport.headerPaints=" + scene.headerPaints[0] + "\n"
				+ "viewport.bodyPaints=" + scene.bodyPaints[0] + "\n"
				+ "viewport.rootClient=" + scene.root.getClientArea () + "\n"
				+ "viewport.scrollerClient=" + scene.scroller.getClientArea () + "\n"
				+ scrollBarText ("h", scene.scroller.getHorizontalBar ())
				+ scrollBarText ("v", scene.scroller.getVerticalBar ());
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
		int expandedResident = requiredResidentChildren (tree, branch);
		assertTrue (expandedResident >= 11 && expandedResident < TREE_CHILDREN,
				"expanded branch must retain the pinned child without eagerly allocating all children");
		capture (
				"tree-pinned-expanded", tree, output,
				treeSidecar (tree, branch, child));

		branch.setExpanded (false);
		tree.setTopItem (branch);
		tree.setSelection (branch);
		drainEvents (220);
		assertEquals (TREE_CHILDREN, branch.getItemCount ());
		int collapsedResident = requiredResidentChildren (tree, branch);
		assertTrue (collapsedResident >= 11 && collapsedResident < expandedResident,
				"collapse must release native rows while retaining the pinned child");
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
		assertEquals ("pinned child 10", child.getText ());
		assertEquals (TREE_CHILDREN, branch.getItemCount ());
		assertTrue (requiredResidentChildren (tree, branch) < TREE_CHILDREN,
				"restore must remain lazy");
		capture (
				"tree-pinned-restored", tree, output,
				treeSidecar (tree, branch, child));
	}

	private static int requiredResidentChildren (Tree tree, TreeItem branch) throws Exception {
		Method method = Tree.class.getDeclaredMethod ("virtualResidentChildCount", TreeItem.class);
		method.setAccessible (true);
		return ((Number) method.invoke (tree, branch)).intValue ();
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
				tree, "virtualViewport", "topRow",
				"tree.viewport.topRow"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "visibleRows",
				"tree.viewport.visibleRows"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "firstPaintRow",
				"tree.viewport.firstPaintRow"));
		out.append (reflectFieldMethod (
				tree, "virtualViewport", "paintRowCount",
				"tree.viewport.paintRowCount"));
		out.append (scrollBarText ("h", tree.getHorizontalBar ()));
		out.append (scrollBarText ("v", tree.getVerticalBar ()));
		return out.toString ();
	}

	private void capture (String name, Scrollable target, Path output, String sidecar) throws Exception {
		target.getShell ().layout (true, true);
		target.redraw ();
		target.update ();
		drainEvents (80);

		Path png = output.resolve (name + ".png");
		SwtScreenshotCapture.Result capture = SwtScreenshotCapture.captureControl (target, png);

		StringBuilder text = new StringBuilder (512);
		text.append ("scenario=").append (name).append ('\n');
		text.append ("platform=").append (SWT.getPlatform ()).append ('\n');
		text.append ("bounds=").append (target.getBounds ()).append ('\n');
		text.append ("client=").append (target.getClientArea ()).append ('\n');
		text.append ("screenshot=").append (png.getFileName ()).append ('\n');
		text.append ("screenshot.captureMethod=").append (capture.method ()).append ('\n');
		text.append ("screenshot.sha256=").append (capture.sha256 ()).append ('\n');

		if (Boolean.getBoolean (NATIVE_CAPTURE)) {
			Path nativePng = output.resolve (name + "-native.png");
			try {
				SwtScreenshotCapture.Result nativeCapture =
						SwtScreenshotCapture.captureNativeShell (target, nativePng);
				text.append ("nativeScreenshot=").append (nativePng.getFileName ()).append ('\n');
				text.append ("nativeScreenshot.captureMethod=")
						.append (nativeCapture.method ()).append ('\n');
				text.append ("nativeScreenshot.sha256=")
						.append (nativeCapture.sha256 ()).append ('\n');
			} catch (RuntimeException | IOException unavailable) {
				text.append ("nativeScreenshot.error=")
						.append (unavailable.getClass ().getName ())
						.append (": ").append (String.valueOf (unavailable.getMessage ()))
						.append ('\n');
			}
		}
		text.append (sidecar);
		Files.writeString (
				output.resolve (name + ".txt"), text, StandardCharsets.UTF_8);
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
        if (bar == null || bar.isDisposed()) {
            return prefix + ".scrollbar=<none>\n";
        }
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
            if (owner == null) {
                return label + "=<none>\n";
            }
			Method method = owner.getClass ().getDeclaredMethod (methodName);
			method.setAccessible (true);
			return label + "=" + method.invoke (owner) + "\n";
		} catch (ReflectiveOperationException | RuntimeException unavailable) {
			return label + "=<unavailable>\n";
		}
	}

	private static int depth (TreeItem item) {
		int result = 0;
        while ((item = item.getParentItem()) != null) {
            result++;
        }
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
