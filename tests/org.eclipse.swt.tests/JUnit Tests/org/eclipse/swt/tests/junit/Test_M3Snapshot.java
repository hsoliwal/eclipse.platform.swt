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
 * Contract of M3Snapshot (enhanced M3 API, row M3-SNAPSHOT): idle captures are equal, a scroll changes
 * the visible range and the pixels, <code>diff</code> names the first differing field, and the fields agree
 * with the public API. The class passes unchanged in every startup mode.
 */
public class Test_M3Snapshot {
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
		M3Snapshot first = M3Snapshot.capture (table), second = M3Snapshot.capture (table);
		assertNull (first.diff (second));
		assertEquals (first, second);
		assertEquals (first.hashCode (), second.hashCode ());
		assertEquals (first.pixels (), second.pixels ());
		assertNotEquals (0, first.pixels ());
		assertTrue (first.toString ().startsWith ("M3Snapshot {class=org.eclipse.swt.widgets.Table"));
		assertNotEquals (first, "a string");
	}

	@Test
	public void test_scrollChangesTheVisibleRangeAndTheFirstDifferenceIsNamed () {
		Table table = table ();
		M3Snapshot before = M3Snapshot.capture (table);
		table.setTopIndex (100);
		flush ();
		M3Snapshot after = M3Snapshot.capture (table);
		assertEquals (M3ItemSelection.getVisibleRange (table), after.visibleRange ());
		assertEquals (100, after.visibleRange ().start ());
		assertNotEquals (before.pixels (), after.pixels ());
		assertTrue (before.diff (after).startsWith ("vbar: "), before.diff (after));
		table.setSelection (new int [] {101, 102, 105});
		M3Snapshot selected = M3Snapshot.capture (table);
		assertArrayEquals (M3ItemSelection.getSelectionRanges (table), selected.selectionRanges ());
		assertTrue (after.diff (selected).startsWith ("selection: "), after.diff (selected));
		assertTrue (selected.state ().contains ("\ntop=100\n"), selected.state ());
	}

	@Test
	public void test_listTreeAndPlainControls () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.V_SCROLL | SWT.MULTI);
		list.setBounds (5, 5, 150, 150);
		for (int i = 0; i < 50; i++) list.add ("item " + i);
		list.setSelection (new int [] {1, 2});
		M3Snapshot listShot = M3Snapshot.capture (list);
		assertEquals (M3ItemSelection.getVisibleRange (list), listShot.visibleRange ());
		assertEquals (1, listShot.selectionRanges ().length);
		Tree tree = new Tree (shell, SWT.BORDER);
		tree.setBounds (160, 5, 150, 150);
		for (int i = 0; i < 30; i++) new TreeItem (tree, SWT.NONE).setText ("node " + i);
		tree.setSelection (tree.getItem (3));
		M3Snapshot treeShot = M3Snapshot.capture (tree);
		assertEquals (M3ItemSelection.getVisibleRange (tree), treeShot.visibleRange ());
		assertEquals (3, treeShot.selectionRanges () [0].start ());
		Button button = new Button (shell, SWT.PUSH);
		button.setBounds (5, 200, 100, 30);
		M3Snapshot buttonShot = M3Snapshot.capture (button);
		assertTrue (buttonShot.visibleRange ().isEmpty ());
		assertEquals (0, buttonShot.selectionRanges ().length);
		assertTrue (buttonShot.diff (listShot).startsWith ("class: "));
		assertTrue (listShot.diff (buttonShot).startsWith ("class: "));
		Label empty = new Label (shell, SWT.NONE);
		assertEquals (0, M3Snapshot.capture (empty).pixels ());
		empty.setBounds (5, 240, 50, 0);
		assertEquals (0, M3Snapshot.capture (empty).pixels ());
		button.setEnabled (false);
		assertTrue (buttonShot.diff (M3Snapshot.capture (button)).startsWith ("state: "));
		button.setVisible (false);
		assertTrue (M3Snapshot.capture (button).state ().contains ("state=hidden disabled"));
		Tree bare = new Tree (shell, SWT.NONE);
		bare.setBounds (5, 280, 100, 50);
		assertTrue (M3Snapshot.capture (bare).state ().contains ("\ntop=0\n"));
	}

	@Test
	public void test_pixelsAloneCanDiffer () {
		Table table = table ();
		M3Snapshot before = M3Snapshot.capture (table);
		/* item texts are not fields: only the pixels show an edit that keeps the widest row */
		table.getItem (0).setText ("row 9");
		flush ();
		M3Snapshot after = M3Snapshot.capture (table);
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
		M3Snapshot.capture (canvas);
		assertEquals (before + 4, prints [0]);
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = table ();
		M3Snapshot shot = M3Snapshot.capture (table);
		assertThrows (IllegalArgumentException.class, () -> M3Snapshot.capture (null));
		assertThrows (IllegalArgumentException.class, () -> shot.diff (null));
		table.dispose ();
		assertThrows (SWTException.class, () -> M3Snapshot.capture (table));
	}
}
