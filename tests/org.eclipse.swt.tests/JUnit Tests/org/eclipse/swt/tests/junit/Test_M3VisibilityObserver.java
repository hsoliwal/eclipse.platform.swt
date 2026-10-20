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

import java.util.BitSet;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3VisibilityObserver (enhanced M3 API, leaf 11). The oracle replays the events into
 * a set of visible rows (an entered row must be new, a left row must be in the set) and compares
 * it with <code>M3ItemSelection.getVisibleRange</code> after every trigger. Resize is a trigger in
 * every mode; a programmatic scroll is announced at once with the viewports and at the next
 * trigger without them. Viewport deltas and hidden viewports (brief rule 24): EXTENTS after a
 * resize, a column resize or a row-count change and not after a scroll; HIDDEN, nothing while
 * hidden, then SHOWN (still dirty), the window delta and the extents, with the model's top row
 * re-anchored once at the show.
 */
public class Test_M3VisibilityObserver {
	Display display;
	Shell shell;

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
			// deliver pending paints, notifications and the coalesced checks
		}
	}

	static class Seen implements Listener {
		final BitSet rows = new BitSet ();
		int events, others;

		@Override
		public void handleEvent (Event event) {
			assertEquals (M3VisibilityObserver.EVENT, event.type);
			assertNull (event.item);
			if (event.detail > M3VisibilityObserver.LEFT) {
				/* EXTENTS, HIDDEN, SHOWN: checked by the tests of the viewport deltas */
				others++;
				return;
			}
			assertTrue (event.start <= event.end);
			events++;
			if (event.detail == M3VisibilityObserver.ENTERED) {
				assertEquals (0, rows.get (event.start, event.end + 1).cardinality (), "entered twice");
				rows.set (event.start, event.end + 1);
			} else {
				assertEquals (M3VisibilityObserver.LEFT, event.detail);
				assertEquals (event.end - event.start + 1, rows.get (event.start, event.end + 1).cardinality (), "left rows that were not visible");
				rows.clear (event.start, event.end + 1);
			}
		}
	}

	private static void assertSeen (Seen seen, M3ItemRange visible, String what) {
		BitSet expected = new BitSet ();
		if (!visible.isEmpty ()) expected.set (visible.start (), visible.end () + 1);
		assertEquals (expected, seen.rows, what + ": visible " + visible);
	}

	private static boolean viewport (Widget widget) {
		return M3Mode.of (widget) == M3Mode.VIEWPORT;
	}

	/* a resize the observer hears in every mode */
	private void resize (Control control, int height) {
		control.setSize (300, height);
		control.update ();
		flush ();
	}

	/* a resize that always changes the height: the trigger of the original mode after a programmatic scroll */
	private void nudge (Control control) {
		resize (control, control.getSize ().y == 380 ? 379 : 380);
	}

	@Test
	public void test_tableReportsWhatEntersAndLeaves () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (5000);
		flush ();
		Seen seen = new Seen ();
		M3VisibilityObserver.addListener (table, seen);
		assertSeen (seen, M3ItemSelection.getVisibleRange (table), "initial");
		resize (table, 200);
		assertSeen (seen, M3ItemSelection.getVisibleRange (table), "smaller");
		resize (table, 380);
		assertSeen (seen, M3ItemSelection.getVisibleRange (table), "larger");
		for (int top : new int [] {1, 400, 4999, 0}) {
			table.setTopIndex (top);
			table.update ();
			flush ();
			if (!viewport (table)) nudge (table);
			assertSeen (seen, M3ItemSelection.getVisibleRange (table), "top " + top);
		}
		int events = seen.events;
		table.redraw ();
		table.update ();
		resize (table, table.getSize ().y);
		assertEquals (events, seen.events, "no change, no event");
	}

	@Test
	public void test_listReportsWhatEntersAndLeaves () {
		List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		String [] strings = new String [5000];
		for (int i = 0; i < strings.length; i++) strings [i] = "row " + i;
		list.setItems (strings);
		flush ();
		Seen seen = new Seen ();
		M3VisibilityObserver.addListener (list, seen);
		assertSeen (seen, M3ItemSelection.getVisibleRange (list), "initial");
		for (int top : new int [] {3, 2500, 4999}) {
			list.setTopIndex (top);
			list.update ();
			flush ();
			if (!viewport (list)) nudge (list);
			assertSeen (seen, M3ItemSelection.getVisibleRange (list), "top " + top);
		}
		resize (list, 150);
		assertSeen (seen, M3ItemSelection.getVisibleRange (list), "smaller");
		list.removeAll ();
		resize (list, 151);
		assertTrue (seen.rows.isEmpty (), "an empty list shows nothing");
	}

	@Test
	public void test_treeReportsExpansion () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 100; i++) {
			TreeItem root = new TreeItem (tree, SWT.NONE);
			root.setText ("root " + i);
			for (int j = 0; j < 4; j++) new TreeItem (root, SWT.NONE).setText ("child " + j);
		}
		flush ();
		Seen seen = new Seen ();
		M3VisibilityObserver.addListener (tree, seen);
		assertSeen (seen, M3ItemSelection.getVisibleRange (tree), "initial");
		tree.getItem (0).setExpanded (true);
		tree.update ();
		flush ();
		resize (tree, 379);
		assertSeen (seen, M3ItemSelection.getVisibleRange (tree), "expanded");
		tree.setTopItem (tree.getItem (50));
		tree.update ();
		flush ();
		resize (tree, 380);
		assertSeen (seen, M3ItemSelection.getVisibleRange (tree), "scrolled");
	}

	@Test
	public void test_removedListenersHearNothing () {
		Table table = new Table (shell, SWT.NONE);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (1000);
		flush ();
		Seen seen = new Seen ();
		M3VisibilityObserver.addListener (table, seen);
		M3VisibilityObserver.removeListener (table, seen);
		int events = seen.events;
		table.setTopIndex (500);
		resize (table, 200);
		assertEquals (events, seen.events);
	}

	@Test
	public void test_listAndTreeListenersCanBeRemoved () {
		List list = new List (shell, SWT.MULTI | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 500; i++) list.add ("row " + i);
		Tree tree = new Tree (shell, SWT.NONE);
		tree.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 500; i++) new TreeItem (tree, SWT.NONE).setText ("node " + i);
		flush ();
		Seen fromList = new Seen (), fromTree = new Seen ();
		M3VisibilityObserver.addListener (list, fromList);
		M3VisibilityObserver.addListener (tree, fromTree);
		M3VisibilityObserver.removeListener (list, fromList);
		M3VisibilityObserver.removeListener (tree, fromTree);
		int heard = fromList.events + fromTree.events;
		list.setTopIndex (300);
		tree.setTopItem (tree.getItem (300));
		nudge (list);
		nudge (tree);
		assertEquals (heard, fromList.events + fromTree.events);
	}

	/* every event in order, the extents last announced, and the dirty answer during SHOWN */
	static final class Log extends Seen {
		final java.util.List<Integer> details = new java.util.ArrayList<> ();
		final Control widget;
		Event extents;
		boolean dirtyAtShown;

		Log (Control widget) {
			this.widget = widget;
		}

		@Override
		public void handleEvent (Event event) {
			details.add (event.detail);
			if (event.detail == M3VisibilityObserver.EXTENTS) extents = event;
			if (event.detail == M3VisibilityObserver.SHOWN) dirtyAtShown = dirty (widget);
			super.handleEvent (event);
		}

		int count (int detail) {
			int count = 0;
			for (int d : details) if (d == detail) count++;
			return count;
		}
	}

	static boolean dirty (Control widget) {
		if (widget instanceof Table table) return M3VisibilityObserver.isViewportDirty (table);
		if (widget instanceof Tree tree) return M3VisibilityObserver.isViewportDirty (tree);
		return M3VisibilityObserver.isViewportDirty ((List) widget);
	}

	static boolean hidden (Control widget) {
		if (widget instanceof Table table) return M3VisibilityObserver.isViewportHidden (table);
		if (widget instanceof Tree tree) return M3VisibilityObserver.isViewportHidden (tree);
		return M3VisibilityObserver.isViewportHidden ((List) widget);
	}

	private int labelsAsked;

	private final M3Model.Labels labels = (id, row) -> {
		labelsAsked++;
		row.setText (0, "row " + id);
	};

	private static void assertExtents (Event extents, Scrollable widget, int rows, String what) {
		Rectangle area = widget.getClientArea ();
		assertNotNull (extents, what);
		assertEquals (area.width, extents.width, what + ": width");
		assertEquals (area.height, extents.height, what + ": height");
		assertEquals (rows, extents.count, what + ": rows");
	}

	@Test
	public void test_extentsFollowResizeColumnsAndRowsButNotScrolling () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setWidth (120);
		new TableColumn (table, SWT.NONE).setWidth (80);
		table.setItemCount (5000);
		flush ();
		Log log = new Log (table);
		M3VisibilityObserver.addListener (table, log);
		assertEquals (1, log.count (M3VisibilityObserver.EXTENTS), "the first listener hears the extents");
		assertExtents (log.extents, table, 5000, "initial");
		table.setTopIndex (400);
		table.update ();
		flush ();
		if (!viewport (table)) {
			table.getVerticalBar ().notifyListeners (SWT.Selection, new Event ());
			flush ();
		}
		assertEquals (1, log.count (M3VisibilityObserver.EXTENTS), "scrolling changes no extent");
		resize (table, 200);
		assertEquals (2, log.count (M3VisibilityObserver.EXTENTS), "a resize changes the extents");
		assertExtents (log.extents, table, 5000, "smaller");
		column.setWidth (150);
		flush ();
		assertEquals (3, log.count (M3VisibilityObserver.EXTENTS), "a column resize changes the content width");
		table.setItemCount (6000);
		flush ();
		assertEquals (4, log.count (M3VisibilityObserver.EXTENTS), "a row count change");
		assertExtents (log.extents, table, 6000, "more rows");
		assertSeen (log, M3ItemSelection.getVisibleRange (table), "range after the extents");
		M3VisibilityObserver.removeListener (table, log);
		assertNull (table.getData ("org.eclipse.swt.widgets.M3VisibilityObserver"), "the last listener releases the latch");
	}

	@Test
	public void test_hiddenTabHoldsTheWindowBackAndShowsOnce () {
		TabFolder folder = new TabFolder (shell, SWT.NONE);
		folder.setBounds (5, 5, 310, 400);
		TabItem front = new TabItem (folder, SWT.NONE);
		front.setText ("front");
		front.setControl (new Label (folder, SWT.NONE));
		TabItem back = new TabItem (folder, SWT.NONE);
		back.setText ("back");
		Table table = new Table (folder, SWT.VIRTUAL | SWT.BORDER);
		back.setControl (table);
		folder.setSelection (1);
		table.setItemCount (5000);
		M3Model model = M3Model.of (table);
		model.apply (M3Patch.builder (M3Patch.ROOT).update (0, 1).build (), labels);
		table.setTopIndex (1000);
		flush ();
		long anchor = model.id (M3Patch.ROOT, table.getTopIndex ());
		Log log = new Log (table);
		M3VisibilityObserver.addListener (table, log);
		assertFalse (hidden (table));
		folder.setSelection (0);
		flush ();
		assertTrue (hidden (table), "a background tab is hidden");
		assertEquals (M3VisibilityObserver.HIDDEN, (int) log.details.get (log.details.size () - 1));
		assertFalse (dirty (table), "nothing changed yet");
		int heard = log.details.size ();
		labelsAsked = 0;
		long next = 1_000_000;
		java.util.Random random = new java.util.Random (20);
		for (int k = 0; k < 1000; k++) {
			int size = model.size (M3Patch.ROOT);
			M3Patch.Builder builder = M3Patch.builder (M3Patch.ROOT);
			switch (k % 3) {
				case 0 -> builder.insert (random.nextInt (900), next++);
				case 1 -> builder.remove (random.nextInt (900), 1);
				default -> builder.update (random.nextInt (size), 1);
			}
			model.apply (builder.build (), labels);
		}
		flush ();
		assertEquals (heard, log.details.size (), "a hidden viewport announces nothing");
		assertEquals (0, labelsAsked, "a hidden virtual table is asked for no label");
		assertTrue (dirty (table), "the model changed while hidden");
		folder.setSelection (1);
		assertEquals (M3VisibilityObserver.SHOWN, (int) log.details.get (heard), "SHOWN comes first");
		assertTrue (log.dirtyAtShown, "dirty during SHOWN");
		assertFalse (dirty (table), "clean after the show");
		assertFalse (hidden (table));
		assertEquals (anchor, model.id (M3Patch.ROOT, table.getTopIndex ()), "the top row is re-anchored at the show");
		assertExtents (log.extents, table, model.size (M3Patch.ROOT), "extents at the show");
		assertSeen (log, M3ItemSelection.getVisibleRange (table), "window delta at the show");
		flush ();
		assertTrue (labelsAsked > 0 && labelsAsked <= 200, "the show asks the window only: " + labelsAsked);
	}

	@Test
	public void test_hiddenShellWithoutListener () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (3000);
		M3Model model = M3Model.of (table);
		table.setTopIndex (500);
		flush ();
		long anchor = model.id (M3Patch.ROOT, table.getTopIndex ());
		assertFalse (dirty (table));
		model.apply (M3Patch.builder (M3Patch.ROOT).insert (0, 10_000, 10_001).build (), labels);
		assertFalse (dirty (table), "a visible viewport is never dirty");
		assertEquals (anchor, model.id (M3Patch.ROOT, table.getTopIndex ()));
		shell.setVisible (false);
		assertTrue (hidden (table));
		model.apply (M3Patch.builder (M3Patch.ROOT).insert (0, 20_000, 20_001, 20_002).remove (3, 2).build (), labels);
		assertTrue (dirty (table), "dirty without a listener");
		shell.setVisible (true);
		assertFalse (dirty (table));
		assertEquals (anchor, model.id (M3Patch.ROOT, table.getTopIndex ()), "re-anchored at the show");
		assertNull (table.getData ("org.eclipse.swt.widgets.M3VisibilityObserver"), "a clean viewport without listeners keeps nothing");
		shell.setMinimized (true);
		flush ();
		assertTrue (hidden (table), "a minimized shell hides the viewport");
		model.apply (M3Patch.builder (M3Patch.ROOT).remove (0, 3).build (), labels);
		assertTrue (dirty (table));
		shell.setMinimized (false);
		flush ();
		assertFalse (hidden (table));
		assertFalse (dirty (table), "a restored shell shows the viewport");
		assertEquals (anchor, model.id (M3Patch.ROOT, table.getTopIndex ()));
		table.setSize (300, 0);
		assertTrue (hidden (table), "an empty client area shows nothing");
		table.setSize (300, 380);
		assertFalse (hidden (table));
	}

	@Test
	public void test_applicationTopIndexWinsOverTheAnchor () {
		Composite page = new Composite (shell, SWT.NONE);
		page.setBounds (0, 0, 310, 400);
		Table table = new Table (page, SWT.VIRTUAL | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (3000);
		M3Model model = M3Model.of (table);
		table.setTopIndex (700);
		flush ();
		page.setVisible (false);
		model.apply (M3Patch.builder (M3Patch.ROOT).insert (0, 50_000).build (), labels);
		table.setTopIndex (10);
		int top = table.getTopIndex ();
		page.setVisible (true);
		assertEquals (top, table.getTopIndex (), "a top row set while hidden stays");
		assertFalse (dirty (table));
	}

	@Test
	public void test_hiddenTreeReanchorsItsTopItem () {
		Composite page = new Composite (shell, SWT.NONE);
		page.setBounds (0, 0, 310, 400);
		Tree tree = new Tree (page, SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 400; i++) new TreeItem (tree, SWT.NONE).setText ("root " + i);
		M3Model model = M3Model.of (tree);
		tree.setTopItem (tree.getItem (200));
		flush ();
		long anchor = model.id (M3Patch.ROOT, tree.indexOf (tree.getTopItem ()));
		Log log = new Log (tree);
		M3VisibilityObserver.addListener (tree, log);
		page.setVisible (false);
		assertTrue (hidden (tree));
		int heard = log.details.size ();
		model.apply (M3Patch.builder (M3Patch.ROOT).insert (0, 9_000, 9_001, 9_002).remove (5, 4).build (), labels);
		model.apply (M3Patch.builder (M3Patch.ROOT).insert (100, 9_003).build (), labels);
		assertTrue (dirty (tree));
		assertEquals (heard, log.details.size (), "a hidden tree announces nothing");
		page.setVisible (true);
		assertEquals (M3VisibilityObserver.SHOWN, (int) log.details.get (heard));
		assertTrue (log.dirtyAtShown);
		assertFalse (dirty (tree));
		assertEquals (anchor, model.id (M3Patch.ROOT, tree.indexOf (tree.getTopItem ())), "the top item is re-anchored");
		assertSeen (log, M3ItemSelection.getVisibleRange (tree), "tree window at the show");
	}

	@Test
	public void test_listHiddenAndDisposedWhileDirty () {
		Composite page = new Composite (shell, SWT.NONE);
		page.setBounds (0, 0, 310, 400);
		List list = new List (page, SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 300; i++) list.add ("row " + i);
		M3Model model = M3Model.of (list);
		Log log = new Log (list);
		M3VisibilityObserver.addListener (list, log);
		M3VisibilityObserver.removeListener (list, log);
		int showListeners = page.getListeners (SWT.Show).length;
		page.setVisible (false);
		assertTrue (hidden (list));
		model.apply (M3Patch.builder (M3Patch.ROOT).insert (0, 7_000).build (), labels);
		assertTrue (dirty (list));
		assertEquals (showListeners + 1, page.getListeners (SWT.Show).length, "a dirty viewport hears its ancestors");
		list.dispose ();
		assertEquals (showListeners, page.getListeners (SWT.Show).length, "a disposed widget leaves its ancestors clean");
	}

	@Test
	public void test_firstListenerOfAHiddenViewportHearsHidden () {
		Composite page = new Composite (shell, SWT.NONE);
		page.setBounds (0, 0, 310, 400);
		Table table = new Table (page, SWT.VIRTUAL);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (100);
		M3Model model = M3Model.of (table);
		page.setVisible (false);
		model.apply (M3Patch.builder (M3Patch.ROOT).update (0, 2).build (), labels);
		Log log = new Log (table);
		M3VisibilityObserver.addListener (table, log);
		assertEquals (java.util.List.of (M3VisibilityObserver.HIDDEN), log.details);
		page.setVisible (true);
		assertEquals (M3VisibilityObserver.SHOWN, (int) log.details.get (1));
		assertTrue (log.dirtyAtShown);
		assertEquals (1, log.count (M3VisibilityObserver.EXTENTS));
		assertSeen (log, M3ItemSelection.getVisibleRange (table), "first range at the show");
	}

	@Test
	public void test_viewportQueriesCheckTheirArguments () {
		Table table = new Table (shell, SWT.NONE);
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.isViewportHidden ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.isViewportHidden ((List) null));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.isViewportHidden ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.isViewportDirty ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.isViewportDirty ((List) null));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.isViewportDirty ((Tree) null));
		table.dispose ();
		assertThrows (SWTException.class, () -> M3VisibilityObserver.isViewportHidden (table));
		assertThrows (SWTException.class, () -> M3VisibilityObserver.isViewportDirty (table));
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = new Table (shell, SWT.NONE);
		Listener nothing = event -> {};
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.addListener ((Table) null, nothing));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.addListener ((List) null, nothing));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.addListener ((Tree) null, nothing));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.addListener (table, null));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.removeListener ((Table) null, nothing));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.removeListener ((List) null, nothing));
		assertThrows (IllegalArgumentException.class, () -> M3VisibilityObserver.removeListener ((Tree) null, nothing));
		M3VisibilityObserver.removeListener (table, nothing);
		table.dispose ();
		assertThrows (SWTException.class, () -> M3VisibilityObserver.addListener (table, nothing));
	}
}
