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
 * Contract of {@link ControlSnapshot}: idle captures are equal, a scroll changes the visible range and the
 * pixels, <code>diff</code> names the first differing field, and the fields agree with the public API. The
 * class passes unchanged in every startup mode.
 */
public class Test_ControlSnapshot {
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
			// paints
		}
	}

	/* the rows that intersect an area of the given height from the top row, clamped to the item count */
	private static ControlSnapshot.Rows window (int top, int count, int areaHeight, int itemHeight) {
		int rows = areaHeight <= 0 ? 0 : (areaHeight + itemHeight - 1) / itemHeight;
		return rows == 0 || top >= count ? ControlSnapshot.Rows.EMPTY : new ControlSnapshot.Rows (top, Math.min (count - 1, top + rows - 1));
	}

	private Table table () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 300);
		for (int i = 0; i < 400; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		flush ();
		return table;
	}

	@Test
	public void test_idleCapturesAreEqual () {
		Table table = table ();
		ControlSnapshot first = ControlSnapshot.capture (table), second = ControlSnapshot.capture (table);
		assertNull (first.diff (second));
		assertEquals (first, second);
		assertEquals (first.hashCode (), second.hashCode ());
		assertEquals (first.pixels (), second.pixels ());
		assertNotEquals (0, first.pixels ());
		assertTrue (first.toString ().startsWith ("ControlSnapshot {class=org.eclipse.swt.widgets.Table"));
		assertNotEquals (first, "a string");
	}

	@Test
	public void test_scrollChangesTheVisibleRangeAndTheFirstDifferenceIsNamed () {
		Table table = table ();
		ControlSnapshot before = ControlSnapshot.capture (table);
		table.setTopIndex (100);
		flush ();
		ControlSnapshot after = ControlSnapshot.capture (table);
		assertEquals (window (100, 400, table.getClientArea ().height, table.getItemHeight ()), after.visibleRange ());
		assertEquals (100, after.visibleRange ().start ());
		assertNotEquals (before.pixels (), after.pixels ());
		assertTrue (before.diff (after).startsWith ("vbar: "), before.diff (after));
		table.setSelection (new int [] {101, 102, 105});
		ControlSnapshot selected = ControlSnapshot.capture (table);
		assertArrayEquals (new ControlSnapshot.Rows [] {new ControlSnapshot.Rows (101, 102), new ControlSnapshot.Rows (105, 105)}, selected.selectionRanges ());
		assertTrue (after.diff (selected).startsWith ("selection: "), after.diff (selected));
		assertTrue (selected.state ().contains ("\ntop=100\n"), selected.state ());
	}

	@Test
	public void test_listTreeAndPlainControls () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.V_SCROLL | SWT.MULTI);
		list.setBounds (5, 5, 150, 150);
		for (int i = 0; i < 50; i++) list.add ("item " + i);
		list.setSelection (new int [] {1, 2});
		ControlSnapshot listShot = ControlSnapshot.capture (list);
		assertEquals (window (list.getTopIndex (), 50, list.getClientArea ().height, list.getItemHeight ()), listShot.visibleRange ());
		assertEquals (1, listShot.selectionRanges ().length);
		Tree tree = new Tree (shell, SWT.BORDER);
		tree.setBounds (160, 5, 150, 150);
		for (int i = 0; i < 30; i++) new TreeItem (tree, SWT.NONE).setText ("node " + i);
		tree.setSelection (tree.getItem (3));
		ControlSnapshot treeShot = ControlSnapshot.capture (tree);
		assertEquals (window (tree.indexOf (tree.getTopItem ()), 30, tree.getClientArea ().height, tree.getItemHeight ()), treeShot.visibleRange ());
		assertEquals (3, treeShot.selectionRanges () [0].start ());
		Button button = new Button (shell, SWT.PUSH);
		button.setBounds (5, 200, 100, 30);
		ControlSnapshot buttonShot = ControlSnapshot.capture (button);
		assertTrue (buttonShot.visibleRange ().isEmpty ());
		assertEquals (0, buttonShot.selectionRanges ().length);
		assertTrue (buttonShot.diff (listShot).startsWith ("class: "));
		assertTrue (listShot.diff (buttonShot).startsWith ("class: "));
		Label empty = new Label (shell, SWT.NONE);
		assertEquals (0, ControlSnapshot.capture (empty).pixels ());
		empty.setBounds (5, 240, 50, 0);
		assertEquals (0, ControlSnapshot.capture (empty).pixels ());
		button.setEnabled (false);
		assertTrue (buttonShot.diff (ControlSnapshot.capture (button)).startsWith ("state: "));
		button.setVisible (false);
		assertTrue (ControlSnapshot.capture (button).state ().contains ("state=hidden disabled"));
		Tree bare = new Tree (shell, SWT.NONE);
		bare.setBounds (5, 280, 100, 50);
		assertTrue (ControlSnapshot.capture (bare).state ().contains ("\ntop=0\n"));
	}

	@Test
	public void test_treeRowsFollowTheExpandedItems () {
		Tree tree = new Tree (shell, SWT.BORDER);
		tree.setBounds (5, 5, 200, 300);
		for (int i = 0; i < 40; i++) {
			TreeItem root = new TreeItem (tree, SWT.NONE);
			root.setText ("root " + i);
			for (int k = 0; k < 3; k++) new TreeItem (root, SWT.NONE).setText ("child " + k);
		}
		tree.getItem (0).setExpanded (true);
		tree.getItem (2).setExpanded (true);
		tree.setTopItem (tree.getItem (2));
		flush ();
		ControlSnapshot shot = ControlSnapshot.capture (tree);
		/* the rows before root 2: root 0, its three children, and root 1 */
		assertSame (tree.getItem (2), tree.getTopItem ());
		assertEquals (5, shot.visibleRange ().start ());
		assertTrue (shot.state ().contains ("
top=5
"), shot.state ());
	}

	@Test
	public void test_pixelsAloneCanDiffer () {
		Table table = table ();
		ControlSnapshot before = ControlSnapshot.capture (table);
		/* item texts are not fields: only the pixels show an edit that keeps the widest row */
		table.getItem (0).setText ("row 9");
		flush ();
		ControlSnapshot after = ControlSnapshot.capture (table);
		assertTrue (before.diff (after).startsWith ("pixels: "), before.diff (after));
		assertNotEquals (before, after);
	}

	@Test
	public void test_controlThatNeverSettlesIsPrintedFourTimes () {
		Canvas canvas = new Canvas (shell, SWT.NONE);
		canvas.setBounds (5, 5, 120, 40);
		int [] prints = new int [1];
		/* every print draws a different number */
		canvas.addListener (SWT.Paint, event -> event.gc.drawText (Integer.toString (prints [0]++), 2, 2));
		flush ();
		int before = prints [0];
		ControlSnapshot.capture (canvas);
		assertEquals (before + 4, prints [0]);
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = table ();
		ControlSnapshot shot = ControlSnapshot.capture (table);
		assertThrows (IllegalArgumentException.class, () -> ControlSnapshot.capture (null));
		assertThrows (IllegalArgumentException.class, () -> shot.diff (null));
		table.dispose ();
		assertThrows (SWTException.class, () -> ControlSnapshot.capture (table));
	}
}
