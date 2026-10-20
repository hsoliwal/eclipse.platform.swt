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

import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 24 gates for the Tree viewport (win32).
 * <ul>
 * <li>Viewport deltas: a resize (50 sizes, 1 px steps and large jumps) and a column-resize sweep move the native window
 * by the rows entering and leaving it only: TVM_INSERTITEM count = rows entering (by logical coordinate), TVM_DELETEITEM
 * at most the rows leaving, no window commit inserts a staying row again (full re-plans 0), and only rows entering the
 * client area are re-measured.</li>
 * <li>Hidden viewports: hide, apply 10^4 model deltas, show. While hidden no TVM message and no paint per delta (the
 * first delta parks the window once); after show one paint pass, and the rows in view are logged
 * ("hidden gate rows:") so the run on the official jar (stock) and the run on this build are compared line by line.</li>
 * </ul>
 * Counters are read by reflection from TreeViewport; stock SWT (official jar, original mode) has none, so only the
 * stock-comparable parts run there.
 */
public class Test_org_eclipse_swt_widgets_Tree_ViewportDeltas {
	static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Tree_ViewportDeltas.class.getName ());
	static final int TVM_INSERTITEM = 0x1132, TVM_DELETEITEM = 0x1101;
	static final int DELTAS = Integer.getInteger ("m3.hiddengate.deltas", 10_000);

	Display display;
	Shell shell;
	/* model of the hidden gate: root labels and child counts (SetData answers from it) */
	final List<String> roots = new ArrayList<> ();
	final Map<String, Integer> children = new HashMap<> ();

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 360, 460);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	/* ---------------------------------------------------------------- reflection on the viewport */

	static Object viewport (Tree tree) {
		try {
			Field field = Tree.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			return field.get (tree);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	static long counter (Object viewport, String name) {
		try {
			Field field = viewport.getClass ().getDeclaredField (name);
			field.setAccessible (true);
			return ((Number) field.get (viewport)).longValue ();
		} catch (NoSuchFieldException e) {
			/* a build before the counter (the "before" run of a ledger row) */
			return -1;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	/* the logical coordinates (parent id << 32 | child index) of the committed native window */
	static Set<Long> residentKeys (Object viewport) {
		try {
			Field field = viewport.getClass ().getDeclaredField ("plan");
			field.setAccessible (true);
			Object plan = field.get (viewport);
			Method size = plan.getClass ().getDeclaredMethod ("size"), keyOf = plan.getClass ().getDeclaredMethod ("keyOf", int.class);
			size.setAccessible (true);
			keyOf.setAccessible (true);
			Set<Long> keys = new HashSet<> ();
			int n = (Integer) size.invoke (plan);
			for (int entry = 0; entry < n; entry++) keys.add ((Long) keyOf.invoke (plan, entry));
			return keys;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	static int minus (Set<Long> a, Set<Long> b) {
		int n = 0;
		for (Long key : a) if (!b.contains (key)) n++;
		return n;
	}

	void drain () {
		ViewportDifferential.drain (display);
	}

	Tree tree (boolean columns, int roots) {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL | (columns ? SWT.FULL_SELECTION : 0));
		tree.setBounds (5, 5, 320, 400);
		if (columns) {
			tree.setHeaderVisible (true);
			for (int c = 0; c < 3; c++) new TreeColumn (tree, SWT.NONE).setWidth (c == 0 ? 140 : 90);
		}
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			String label = parent == null ? (event.index < this.roots.size () ? this.roots.get (event.index) : "root " + event.index) : parent.getText () + " / " + event.index;
			item.setText (label);
			if (columns) for (int c = 1; c < 3; c++) item.setText (c, label + " c" + c);
			Integer count = parent == null ? children.get (label) : null;
			if (count != null) item.setItemCount (count);
		});
		tree.setItemCount (roots);
		return tree;
	}

	/* ---------------------------------------------------------------- viewport deltas */

	/** One resize or column change: native inserts and deletes against the rows entering and leaving the window. */
	record Step (int inserted, int deleted, int entering, int leaving, long remeasured, long full, long reinserted, int setItems) {
	}

	Step step (Tree tree, Object viewport, Runnable change) {
		Set<Long> before = residentKeys (viewport);
		long remeasured = counter (viewport, "remeasuredRows"), full = counter (viewport, "fullRebuilds"), reinserted = counter (viewport, "reinsertedRows");
		int inserted, deleted, setItems;
		try (NativeMessageCounter counter = new NativeMessageCounter (tree.handle, OS.TV_FIRST, OS.TV_FIRST + 0xFF)) {
			change.run ();
			tree.update ();
			drain ();
			String breakdown = counter.breakdown ();
			inserted = count (breakdown, TVM_INSERTITEM);
			deleted = count (breakdown, TVM_DELETEITEM);
			/* TVM_SETITEM: a re-measure is one, so the count compares builds without the counter */
			setItems = count (breakdown, 0x113F);
		}
		Set<Long> after = residentKeys (viewport);
		return new Step (inserted, deleted, minus (after, before), minus (before, after), counter (viewport, "remeasuredRows") - remeasured,
			counter (viewport, "fullRebuilds") - full, counter (viewport, "reinsertedRows") - reinserted, setItems);
	}

	static int count (String breakdown, int message) {
		String key = "0x" + Integer.toHexString (message).toUpperCase () + "=";
		for (String part : breakdown.split (" ")) {
			if (part.startsWith (key)) return Integer.parseInt (part.substring (key.length ()));
		}
		return 0;
	}

	void assertDelta (List<Step> steps, String sweep, int itemHeight) {
		int inserted = 0, entering = 0, deleted = 0, leaving = 0;
		long remeasured = 0, setItems = 0;
		for (int i = 0; i < steps.size (); i++) {
			Step s = steps.get (i);
			inserted += s.inserted;
			entering += s.entering;
			deleted += s.deleted;
			leaving += s.leaving;
			remeasured += s.remeasured;
			setItems += s.setItems;
			assertEquals (0, s.full, sweep + " step " + i + ": full re-plan " + s);
			assertEquals (0, s.reinserted, sweep + " step " + i + ": staying rows inserted again " + s);
			assertEquals (s.entering, s.inserted, sweep + " step " + i + ": native inserts are not the rows entering " + s);
			assertTrue (s.deleted <= s.leaving, sweep + " step " + i + ": native deletes beyond the rows leaving " + s);
		}
		final int ins = inserted, ent = entering, del = deleted, lea = leaving;
		final long rem = remeasured, set = setItems;
		LOG.info (() -> sweep + ": " + steps.size () + " steps, native inserts " + ins + " (rows entering " + ent + "), deletes " + del
			+ " (rows leaving " + lea + "), rows re-measured " + rem + ", TVM_SETITEM " + set);
	}

	/** 50 sizes: one-pixel steps up and down around three heights, and large jumps between them. */
	static int [] resizeHeights () {
		int [] heights = new int [50];
		int n = 0;
		for (int base : new int [] {400, 157, 610}) {
			for (int d = 1; d <= 8 && n < heights.length; d++) heights [n++] = base + (d % 2 == 0 ? d / 2 : -(d + 1) / 2);
			heights [n++] = base;
		}
		int [] jumps = {90, 700, 23, 480, 300, 655, 120, 560, 40, 400, 333, 610, 222, 505, 180, 420, 75, 640, 260, 400, 1};
		for (int jump : jumps) {
			if (n < heights.length) heights [n++] = jump;
		}
		while (n < heights.length) heights [n] = 380 + n++;
		return heights;
	}

	@Test
	public void resizeSweepMovesOnlyTheRowsEnteringAndLeaving () {
		Tree tree = tree (false, 100_000);
		shell.setSize (360, 760);
		shell.open ();
		tree.setTopItem (tree.getItem (5_000));
		for (int i = 5_003; i < 5_040; i += 9) {
			TreeItem item = tree.getItem (i);
			children.put (item.getText (), 30);
			item.setItemCount (30);
			item.setExpanded (true);
		}
		tree.setTopItem (tree.getItem (5_000));
		drain ();
		Object viewport = viewport (tree);
		Assumptions.assumeTrue (viewport != null, "no Tree viewport in this build (stock)");
		List<Step> steps = new ArrayList<> ();
		for (int height : resizeHeights ()) steps.add (step (tree, viewport, () -> tree.setSize (320, height)));
		assertDelta (steps, "resize sweep", tree.getItemHeight ());
	}

	@Test
	public void columnResizeSweepMovesOnlyTheRowsEnteringAndLeaving () {
		Tree tree = tree (true, 100_000);
		shell.open ();
		tree.setTopItem (tree.getItem (7_000));
		drain ();
		Object viewport = viewport (tree);
		Assumptions.assumeTrue (viewport != null, "no Tree viewport in this build (stock)");
		List<Step> steps = new ArrayList<> ();
		Random random = new Random (24);
		for (int i = 0; i < 50; i++) {
			TreeColumn column = tree.getColumn (i % 3);
			int width = i % 10 == 9 ? 600 + random.nextInt (300) : Math.max (0, column.getWidth () + (random.nextBoolean () ? 1 : -1) * (1 + random.nextInt (i % 4 == 0 ? 120 : 2)));
			steps.add (step (tree, viewport, () -> column.setWidth (width)));
		}
		assertDelta (steps, "column-resize sweep", tree.getItemHeight ());
	}

	/* ---------------------------------------------------------------- hidden viewports */

	enum Hide {
		TREE, PARENT
	}

	@Test
	public void hiddenTreeAppliesOnePatchOnShow () {
		hiddenGate (Hide.TREE);
	}

	@Test
	public void treeInAHiddenParentAppliesOnePatchOnShow () {
		hiddenGate (Hide.PARENT);
	}

	void hiddenGate (Hide hide) {
		Composite page = new Composite (shell, SWT.NONE);
		page.setBounds (0, 0, 340, 420);
		for (int i = 0; i < 1_000; i++) roots.add ("root " + i);
		Tree tree = new Tree (page, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
		tree.setBounds (5, 5, 320, 400);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			item.setText (parent == null ? roots.get (event.index) : parent.getText () + " / " + event.index);
			Integer count = parent == null ? children.get (roots.get (event.index)) : null;
			if (count != null) item.setItemCount (count);
		});
		tree.setItemCount (roots.size ());
		shell.open ();
		tree.setTopItem (tree.getItem (200));
		drain ();
		Object viewport = viewport (tree);
		Control hidden = hide == Hide.TREE ? tree : page;
		hidden.setVisible (false);
		drain ();
		long parks = viewport == null ? 0 : counter (viewport, "parks");
		int resident = viewport == null ? 0 : residentKeys (viewport).size ();
		Random random = new Random (0x41DDE7L);
		long messages, paints;
		String breakdown;
		try (NativeMessageCounter counter = new NativeMessageCounter (tree.handle, OS.TV_FIRST, OS.TV_FIRST + 0xFF).countPaints ()) {
			for (int i = 0; i < DELTAS; i++) delta (tree, random);
			drain ();
			messages = counter.count ();
			paints = counter.paints ();
			breakdown = counter.breakdown ();
		}
		final long m = messages, p = paints;
		final String b = breakdown;
		LOG.info (() -> "hidden gate (" + hide + "): " + DELTAS + " deltas while hidden: " + m + " TVM messages (" + b + "), " + p + " paints");
		long showPaints;
		try (NativeMessageCounter counter = new NativeMessageCounter (tree.handle, OS.TV_FIRST, OS.TV_FIRST + 0xFF).countPaints ()) {
			hidden.setVisible (true);
			drain ();
			ViewportDifferential.settle (display, 200);
			showPaints = counter.paints ();
		}
		String rows = rowsInView (tree);
		final long sp = showPaints;
		LOG.info (() -> "hidden gate (" + hide + "): " + sp + " paint passes after show");
		LOG.info (() -> "hidden gate rows: " + rows);
		assertEquals (expectedRows (tree), rows, "rows in view after show follow the model");
		if (viewport != null && counter (viewport, "parks") >= 0) {
			assertEquals (parks + 1, counter (viewport, "parks"), "the viewport parks once while hidden");
			/* the first delta parks the window (lanes take its state, its rows go); every later delta sends nothing */
			assertTrue (messages <= 4L * resident + 16, "TVM messages while hidden: " + messages + " (" + breakdown + ")");
			assertEquals (0, paints, "paints while hidden");
			assertEquals (1, showPaints, "paint passes after show");
		}
	}

	/* one model delta: text, structure (count, insert, dispose) or expansion of a root */
	void delta (Tree tree, Random random) {
		int count = tree.getItemCount ();
		int index = count == 0 ? 0 : random.nextInt (count);
		switch (random.nextInt (8)) {
			case 0, 1 -> {
				if (count == 0) return;
				String label = "root " + random.nextInt (1_000_000);
				roots.set (index, label);
				tree.getItem (index).setText (label);
			}
			case 2 -> {
				int next = Math.max (100, Math.min (2_000, count + random.nextInt (21) - 10));
				while (roots.size () < next) roots.add ("root " + roots.size () + "+" + random.nextInt (1000));
				while (roots.size () > next) roots.remove (roots.size () - 1);
				tree.setItemCount (next);
			}
			case 3 -> {
				String label = "new " + random.nextInt (1_000_000);
				roots.add (index, label);
				new TreeItem (tree, SWT.NONE, index).setText (label);
			}
			case 4 -> {
				if (count <= 100) return;
				roots.remove (index);
				tree.getItem (index).dispose ();
			}
			case 5 -> {
				if (count == 0) return;
				TreeItem item = tree.getItem (index);
				int kids = random.nextInt (12);
				children.put (roots.get (index), kids);
				item.setItemCount (kids);
			}
			default -> {
				if (count == 0) return;
				tree.getItem (index).setExpanded (random.nextBoolean ());
			}
		}
	}

	/* the rows the client area shows, top to bottom, by hit test */
	static String rowsInView (Tree tree) {
		StringBuilder out = new StringBuilder ();
		TreeItem top = tree.getTopItem ();
		out.append ("top=").append (top == null ? "null" : top.getText ());
		int height = tree.getItemHeight (), client = tree.getClientArea ().height;
		for (int y = height / 2; y < client; y += height) {
			TreeItem item = null;
			for (int x = 25; item == null && x < 200; x += 20) item = tree.getItem (new Point (x, y));
			out.append (" | ").append (item == null ? "-" : item.getText ());
		}
		return out.toString ();
	}

	/* the same rows from the model: preorder of expanded roots from the top item */
	static String expectedRows (Tree tree) {
		StringBuilder out = new StringBuilder ();
		TreeItem top = tree.getTopItem ();
		out.append ("top=").append (top == null ? "null" : top.getText ());
		List<TreeItem> flat = new ArrayList<> ();
		int height = tree.getItemHeight (), client = tree.getClientArea ().height, rows = (client - height / 2 + height - 1) / height;
		if (top != null) {
			TreeItem parent = top.getParentItem ();
			int index = parent == null ? tree.indexOf (top) : parent.indexOf (top);
			collect (tree, parent, index, flat, rows);
		}
		for (int i = 0; i < rows; i++) out.append (" | ").append (i < flat.size () ? flat.get (i).getText () : "-");
		return out.toString ();
	}

	/* preorder from child index of parent (null: roots), then on through the following siblings of the ancestors */
	static void collect (Tree tree, TreeItem parent, int index, List<TreeItem> out, int limit) {
		preorder (tree, parent, index, out, limit);
		for (TreeItem child = parent; child != null && out.size () < limit; child = child.getParentItem ()) {
			TreeItem grand = child.getParentItem ();
			preorder (tree, grand, (grand == null ? tree.indexOf (child) : grand.indexOf (child)) + 1, out, limit);
		}
	}

	static void preorder (Tree tree, TreeItem parent, int index, List<TreeItem> out, int limit) {
		int count = parent == null ? tree.getItemCount () : parent.getItemCount ();
		for (int i = index; i < count && out.size () < limit; i++) {
			TreeItem item = parent == null ? tree.getItem (i) : parent.getItem (i);
			out.add (item);
			if (item.getExpanded () && item.getItemCount () > 0) preorder (tree, item, 0, out, limit);
		}
	}
}
