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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderRenderer;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.VisibilityGate;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scrollable;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Effective-visibility gate (win32): hidden work is deferred and recorded,
 * public results are computed on demand, and visibility triggers one catch-up.
 */
public class Test_org_eclipse_swt_internal_VisibilityGate {

	Display display;
	Shell shell;
	int layouts;

	/** Places every child at a fixed inset; counts layout calls. */
	final class CountingLayout extends Layout {
		@Override
		protected Point computeSize (Composite composite, int wHint, int hHint, boolean flushCache) {
			return new Point (100, 100);
		}

		@Override
		protected void layout (Composite composite, boolean flushCache) {
			layouts++;
			Rectangle area = composite.getClientArea ();
			for (Control child : composite.getChildren ()) {
				child.setBounds (area.x + 7, area.y + 5, area.width / 2, area.height / 3);
			}
		}
	}

	@BeforeEach
	public void setUp () {
		assumeTrue (SwtTestUtil.isWindows, "visibility gate port is win32-only");
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setBounds (40, 40, 400, 300);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) {
			shell.dispose ();
		}
	}

	@Test
	public void test_hiddenLayoutIsDeferredButGeometryIsComputedOnDemand () {
		Composite composite = new Composite (shell, SWT.NONE);
		composite.setLayout (new CountingLayout ());
		Button button = new Button (composite, SWT.PUSH);
		shell.layout (true, true);
		layouts = 0;
		composite.setSize (300, 210);
		composite.layout (true, true);
		assertEquals (0, layouts, "layout of a never-shown shell must be deferred");
		Rectangle area = composite.getClientArea ();
		assertEquals (new Rectangle (area.x + 7, area.y + 5, area.width / 2, area.height / 3), button.getBounds ());
		assertEquals (1, layouts, "the geometry query runs the deferred layout exactly once");
		button.getBounds ();
		assertEquals (1, layouts);
	}

	@Test
	public void test_openRunsDeferredLayoutOnce () {
		Composite composite = new Composite (shell, SWT.NONE);
		composite.setLayout (new CountingLayout ());
		new Button (composite, SWT.PUSH);
		layouts = 0;
		for (int i = 0; i < 5; i++) {
			composite.layout (true, true);
		}
		assertEquals (0, layouts);
		shell.open ();
		SwtTestUtil.processEvents ();
		assertTrue (layouts >= 1 && layouts <= 2, "one catch-up layout (plus at most the open resize), was " + layouts);
	}

	@Test
	public void test_isOffscreenFollowsShellVisibilityAndMinimize () throws InterruptedException {
		Button button = new Button (shell, SWT.PUSH);
		assertTrue (VisibilityGate.isOffscreen (button, false), "never-shown shell");
		shell.open ();
		SwtTestUtil.processEvents ();
		assertFalse (VisibilityGate.isOffscreen (button, false), "open shell");
		shell.setMinimized (true);
		SwtTestUtil.processEvents (2000, shell::getMinimized);
		assertTrue (VisibilityGate.isOffscreen (button, false), "minimized shell");
		shell.setMinimized (false);
		SwtTestUtil.processEvents (2000, () -> !shell.getMinimized ());
		assertFalse (VisibilityGate.isOffscreen (button, false), "restored shell");
		button.setVisible (false);
		assertTrue (VisibilityGate.isOffscreen (button, false), "hidden control");
		assertFalse (button.isVisible (), "public API unchanged");
		button.setVisible (true);
		assertFalse (VisibilityGate.isOffscreen (button, false));
	}

	@Test
	public void test_controlScrolledOutOfScrolledCompositeIsOffscreen () {
		shell.setLayout (null);
		ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.V_SCROLL);
		scrolled.setBounds (0, 0, 200, 100);
		Composite column = new Composite (scrolled, SWT.NONE);
		column.setSize (180, 2000);
		Button top = new Button (column, SWT.PUSH);
		top.setBounds (0, 0, 100, 20);
		Button far = new Button (column, SWT.PUSH);
		far.setBounds (0, 1500, 100, 20);
		scrolled.setContent (column);
		shell.open ();
		SwtTestUtil.processEvents ();
		assertFalse (VisibilityGate.isOffscreen (top, false));
		assertTrue (VisibilityGate.isOffscreen (far, false), "clipped out by the ScrolledComposite viewport");
		assertTrue (far.isVisible (), "public isVisible keeps its semantics");
		scrolled.setOrigin (0, 1490);
		SwtTestUtil.processEvents ();
		assertFalse (VisibilityGate.isOffscreen (far, false), "scrolled into view");
		assertTrue (VisibilityGate.isOffscreen (top, false));
	}

	/**
	 * User report "inset problem": the native vertical scrollbar column must be painted
	 * with its track after the widget comes back from the gate. The column of a
	 * StyledText that was scrolled out of a ScrolledComposite and back is captured
	 * from the screen and compared with a twin that was never clipped.
	 */
	@Test
	public void test_scrollbarTrackPaintedAfterCatchUp () {
		/* ON_TOP so no other desktop window covers the captured pixels */
		shell.dispose ();
		shell = new Shell (display, SWT.SHELL_TRIM | SWT.ON_TOP);
		shell.setBounds (40, 40, 700, 360);
		ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.V_SCROLL);
		scrolled.setBounds (0, 0, 300, 200);
		Composite column = new Composite (scrolled, SWT.NONE);
		column.setSize (280, 2000);
		StyledText gated = new StyledText (column, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		gated.setBounds (0, 1500, 260, 180);
		scrolled.setContent (column);
		StyledText twin = new StyledText (shell, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		twin.setBounds (360, 10, 260, 180);
		StringBuilder text = new StringBuilder ();
		for (int i = 0; i < 2000; i++) {
			text.append ("line ").append (i).append (System.lineSeparator ());
		}
		shell.open ();
		SwtTestUtil.processEvents ();
		gated.setText (text.toString ());
		twin.setText (text.toString ());
		assertTrue (VisibilityGate.isOffscreen (gated, true));
		scrolled.setOrigin (0, 1500);
		SwtTestUtil.processEvents ();
		assertFalse (VisibilityGate.isOffscreen (gated, true));
		ImageData a = captureRightColumn (gated), b = captureRightColumn (twin);
		assertEquals (b.width, a.width);
		assertEquals (b.height, a.height);
		/* Track colour = the twin column's most frequent pixel; the thumb may differ (Windows 11 animates it). */
		java.util.Map<Integer, Integer> histogram = new java.util.HashMap<> ();
		for (int y = 0; y < b.height; y++) {
			for (int x = 0; x < b.width; x++) {
				histogram.merge (b.getPixel (x, y), 1, Integer::sum);
			}
		}
		int track = histogram.entrySet ().stream ().max (java.util.Map.Entry.comparingByValue ()).get ().getKey ();
		int trackPixels = 0, whiteStripColumns = 0;
		for (int x = 0; x < a.width; x++) {
			boolean gatedWhite = true, twinWhite = true;
			for (int y = 0; y < a.height; y++) {
				if (a.getPixel (x, y) == track) {
					trackPixels++;
				}
				gatedWhite &= a.palette.getRGB (a.getPixel (x, y)).equals (new org.eclipse.swt.graphics.RGB (255, 255, 255));
				twinWhite &= b.palette.getRGB (b.getPixel (x, y)).equals (new org.eclipse.swt.graphics.RGB (255, 255, 255));
			}
			if (gatedWhite && !twinWhite) {
				whiteStripColumns++;
			}
		}
		if (trackPixels * 10 < a.width * a.height * 8 || whiteStripColumns != 0) {
			save (a, "gated-column");
			save (b, "twin-column");
		}
		assertEquals (0, whiteStripColumns, "white strip in the scrollbar column");
		assertTrue (trackPixels * 10 >= a.width * a.height * 8, "scrollbar track painted on at least 80% of the column, was " + trackPixels + " of " + a.width * a.height);
	}

	static void save (ImageData data, String name) {
		org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader ();
		loader.data = new ImageData [] {data};
		loader.save (new java.io.File (System.getProperty ("java.io.tmpdir"), "swt-visibility-gate-" + name + ".png").getPath (), SWT.IMAGE_PNG);
	}

	/** Screen pixels of the control's vertical scrollbar column (non-client, right of the client area). */
	ImageData captureRightColumn (Scrollable control) {
		Rectangle client = control.getClientArea ();
		Point origin = control.toDisplay (client.x + client.width, client.y);
		int width = control.getVerticalBar ().getSize ().x;
		Image image = new Image (display, width, client.height);
		GC gc = new GC (display);
		try {
			gc.copyArea (image, origin.x, origin.y);
		} finally {
			gc.dispose ();
		}
		ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	@Test
	public void test_deferredConsumerWorkCatchesUpOnceWhenShown () {
		Composite composite = new Composite (shell, SWT.NONE);
		int [] caught = new int [2];
		composite.addListener (VisibilityGate.CATCH_UP, e -> {
			caught [0]++;
			caught [1] |= e.detail;
		});
		assertTrue (VisibilityGate.defer (composite, VisibilityGate.MEASURE));
		assertTrue (VisibilityGate.defer (composite, VisibilityGate.DATA));
		shell.open ();
		SwtTestUtil.processEvents ();
		assertEquals (1, caught [0], "one catch-up event");
		assertEquals (VisibilityGate.MEASURE | VisibilityGate.DATA, caught [1] & (VisibilityGate.MEASURE | VisibilityGate.DATA));
		assertFalse (VisibilityGate.defer (composite, VisibilityGate.MEASURE), "visible: work proceeds");
	}

	@Test
	public void test_hiddenIndeterminateProgressBarParksItsMarquee () throws Exception {
		ProgressBar bar = new ProgressBar (shell, SWT.INDETERMINATE);
		int [] paints = new int [1];
		bar.addListener (SWT.Paint, e -> paints [0]++);
		shell.open ();
		spin (500);
		assertTrue (paints [0] > 0, "the marquee paints while shown");
		assertFalse (marqueeParked (bar));
		bar.setVisible (false);
		spin (400);
		assertTrue (marqueeParked (bar), "a marquee nobody can see stops its timer");
		paints [0] = 0;
		bar.setVisible (true);
		spin (600);
		assertFalse (marqueeParked (bar), "the catch-up restarts the marquee");
		assertTrue (paints [0] > 1, "the marquee animates again after the show, paints " + paints [0]);
	}

	void spin (int milliseconds) throws InterruptedException {
		long end = System.currentTimeMillis () + milliseconds;
		while (System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) Thread.sleep (5);
		}
	}

	@Test
	public void test_hiddenTableAndTreeEditorsArmNoRelayoutTimer () throws Exception {
		Composite page = new Composite (shell, SWT.NONE);
		page.setLayout (new FillLayout ());
		Table table = new Table (page, SWT.BORDER);
		new TableColumn (table, SWT.NONE).setWidth (120);
		TableItem row = new TableItem (table, SWT.NONE);
		Text cell = new Text (table, SWT.NONE);
		TableEditor tableEditor = new TableEditor (table);
		tableEditor.grabHorizontal = true;
		Tree tree = new Tree (page, SWT.BORDER);
		TreeItem node = new TreeItem (tree, SWT.NONE);
		node.setText ("node");
		Text label = new Text (tree, SWT.NONE);
		TreeEditor treeEditor = new TreeEditor (tree);
		treeEditor.grabHorizontal = true;
		shell.open ();
		SwtTestUtil.processEvents ();
		tableEditor.setEditor (cell, row, 0);
		treeEditor.setEditor (label, node);
		assertEquals (1, timers ("org.eclipse.swt.custom.TableEditor"), "shown: the relayout timer");
		assertEquals (1, timers ("org.eclipse.swt.custom.TreeEditor"), "shown: the relayout timer");
		page.setVisible (false);
		for (int i = 0; i < 50; i++) {
			tableEditor.setItem (row);
			treeEditor.setItem (node);
		}
		assertEquals (0, timers ("org.eclipse.swt.custom.TableEditor"), "hidden: no relayout timer");
		assertEquals (0, timers ("org.eclipse.swt.custom.TreeEditor"), "hidden: no relayout timer");
		assertEquals (1, table.getListeners (VisibilityGate.CATCH_UP).length, "one catch-up listener per editor");
		page.setVisible (true);
		SwtTestUtil.processEvents ();
		assertEquals (row.getBounds (0).y, cell.getBounds ().y, "the table editor is on its row");
		assertEquals (node.getBounds ().y, label.getBounds ().y, "the tree editor is on its row");
		tableEditor.dispose ();
		treeEditor.dispose ();
		assertEquals (0, table.getListeners (VisibilityGate.CATCH_UP).length, "dispose removes the listener");
		assertEquals (0, tree.getListeners (VisibilityGate.CATCH_UP).length, "dispose removes the listener");
	}

	@Test
	public void test_minimizedVirtualListParksItsSetDataPrefetch () throws Exception {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.VIRTUAL | SWT.V_SCROLL | SWT.BORDER);
		int [] requests = new int [1];
		list.addListener (SWT.SetData, e -> {
			requests [0]++;
			list.setItem (e.index, "row " + e.index);
		});
		list.setItemCount (100_000);
		shell.open ();
		spin (400);
		assertTrue (requests [0] > 0, "shown: rows are requested");
		shell.setMinimized (true);
		spin (200);
		requests [0] = 0;
		list.setTopIndex (50_000);
		spin (400);
		assertEquals (0, requests [0], "minimized: no SetData prefetch");
		assertEquals (0, timers ("org.eclipse.swt.widgets.List$$"), "minimized: no prefetch timer");
		shell.setMinimized (false);
		spin (400);
		assertTrue (requests [0] > 0, "restored: the rows are requested");
		assertEquals (50_000, list.getTopIndex ());
	}

	@Test
	public void test_occlusionTrackerParksWhileNoShellIsVisible () throws Exception {
		java.lang.reflect.Field field = Display.class.getDeclaredField ("occlusionTracker");
		field.setAccessible (true);
		Object tracker = field.get (display);
		assumeTrue (tracker != null, "occlusion tracking is off");
		java.lang.reflect.Field parked = tracker.getClass ().getDeclaredField ("parked");
		parked.setAccessible (true);
		for (Shell other : display.getShells ()) {
			if (other.isVisible ()) other.setVisible (false);
		}
		spin (800);
		assertTrue (parked.getBoolean (tracker), "no shell visible: the tracker parks");
		shell.open ();
		spin (300);
		assertFalse (parked.getBoolean (tracker), "a shown shell wakes the tracker");
		shell.setMinimized (true);
		spin (800);
		assertTrue (parked.getBoolean (tracker), "the only shell minimized: the tracker parks");
		shell.setMinimized (false);
		spin (300);
		assertFalse (parked.getBoolean (tracker), "a restored shell wakes the tracker");
	}

	@Test
	public void test_hiddenCTabFolderMeasuresNoTabsUntilShown () throws Exception {
		Composite page = new Composite (shell, SWT.NONE);
		page.setLayout (new FillLayout ());
		CTabFolder folder = new CTabFolder (page, SWT.BORDER);
		int [] sizes = new int [1];
		folder.setRenderer (new CTabFolderRenderer (folder) {
			@Override
			protected Point computeSize (int part, int state, GC gc, int wHint, int hHint) {
				sizes [0]++;
				return super.computeSize (part, state, gc, wHint, hHint);
			}
		});
		CTabItem [] items = new CTabItem [20];
		for (int i = 0; i < items.length; i++) {
			items [i] = new CTabItem (folder, SWT.NONE);
			items [i].setText ("tab " + i);
		}
		shell.open ();
		spin (300);
		page.setVisible (false);
		spin (100);
		sizes [0] = 0;
		for (int i = 0; i < 200; i++) {
			items [i % items.length].setText ("renamed tab " + i);
		}
		spin (300);
		assertEquals (0, sizes [0], "hidden: no tab measured");
		page.setVisible (true);
		spin (300);
		assertTrue (sizes [0] > 0, "shown: the folded update measures the tabs");
		assertEquals ("renamed tab 199", items [19].getText ());
		assertTrue (items [0].getBounds ().width > 0);
	}

	@Test
	public void test_hiddenTableKeepsItsAppendsPendingUntilShown () throws Exception {
		Composite page = new Composite (shell, SWT.NONE);
		page.setLayout (new FillLayout ());
		Table table = new Table (page, SWT.BORDER);
		shell.open ();
		spin (200);
		java.lang.reflect.Field viewportField = Table.class.getDeclaredField ("viewport");
		viewportField.setAccessible (true);
		Object viewport = viewportField.get (table);
		assumeTrue (viewport != null, "the Table viewport is off");
		java.lang.reflect.Field pending = viewport.getClass ().getDeclaredField ("pending");
		pending.setAccessible (true);
		for (int i = 0; i < 100; i++) new TableItem (table, SWT.NONE).setText ("shown " + i);
		spin (100);
		assertEquals (0, pending.getInt (viewport), "shown: the posted flush counts the appends");
		page.setVisible (false);
		for (int i = 0; i < 1000; i++) new TableItem (table, SWT.NONE); // no setText: a text change is a message that observes the row
		spin (100);
		assertTrue (pending.getInt (viewport) > 0, "hidden: no native count change for the appends");
		assertEquals (1100, table.getItemCount ());
		page.setVisible (true);
		spin (300);
		assertEquals (0, pending.getInt (viewport), "shown: the first observing message counts them");
		assertEquals (1100, table.getItemCount ());
		assertEquals ("", table.getItem (1099).getText ());
	}

	/* pending timers whose runnable class starts with the prefix (win32 Display.timerList) */
	int timers (String prefix) throws ReflectiveOperationException {
		java.lang.reflect.Field field = Display.class.getDeclaredField ("timerList");
		field.setAccessible (true);
		Runnable [] list = (Runnable []) field.get (display);
		int count = 0;
		if (list != null) {
			for (Runnable timer : list) {
				if (timer != null && timer.getClass ().getName ().startsWith (prefix)) count++;
			}
		}
		return count;
	}

	static boolean marqueeParked (ProgressBar bar) throws ReflectiveOperationException {
		java.lang.reflect.Field field = ProgressBar.class.getDeclaredField ("marqueeParked");
		field.setAccessible (true);
		return field.getBoolean (bar);
	}
}
