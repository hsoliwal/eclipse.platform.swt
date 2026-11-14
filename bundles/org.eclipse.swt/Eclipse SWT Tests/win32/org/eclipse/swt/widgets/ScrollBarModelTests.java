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
package org.eclipse.swt.widgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of ScrollBarModel (enhanced M3 API, row M3-SCROLLBAR-GEOMETRY): the lane equals the scroll bar's
 * getters for every scrollable kind, in every startup mode (viewport and original are the mode differential).
 */
public class ScrollBarModelTests {
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

	private static int [] getters (ScrollBar bar) {
		return new int [] {bar.getMinimum (), bar.getMaximum (), bar.getThumb (), bar.getIncrement (), bar.getPageIncrement (),
			bar.getSelection (), bar.getVisible () ? 1 : 0, bar.getEnabled () ? 1 : 0};
	}

	private static void assertModel (Scrollable widget, String what) {
		for (int orientation : new int [] {SWT.VERTICAL, SWT.HORIZONTAL}) {
			ScrollBar bar = orientation == SWT.VERTICAL ? widget.getVerticalBar () : widget.getHorizontalBar ();
			int [] lane = ScrollBarModel.get (widget, orientation);
			if (bar == null) {
				assertNull (lane, what);
			} else {
				assertArrayEquals (getters (bar), lane, what + " " + orientation);
			}
		}
	}

	@Test
	public void test_laneEqualsTheGettersOfEveryKind () {
		Table table = new Table (shell, SWT.V_SCROLL | SWT.H_SCROLL);
		table.setBounds (5, 5, 150, 150);
		for (int i = 0; i < 500; i++) new TableItem (table, SWT.NONE).setText ("row " + i + " with a long text to scroll sideways");
		table.setTopIndex (200);
		assertModel (table, "table");
		Tree tree = new Tree (shell, SWT.VIRTUAL);
		tree.setBounds (160, 5, 150, 150);
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
		tree.setItemCount (100_000);
		assertModel (tree, "virtual tree");
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.V_SCROLL);
		list.setBounds (5, 160, 150, 100);
		for (int i = 0; i < 80; i++) list.add ("item " + i);
		list.setTopIndex (30);
		assertModel (list, "list");
		Text text = new Text (shell, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		text.setBounds (160, 160, 150, 100);
		text.setText ("a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nm");
		assertModel (text, "text");
		ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.V_SCROLL | SWT.H_SCROLL);
		scrolled.setBounds (5, 270, 150, 100);
		Label content = new Label (scrolled, SWT.NONE);
		content.setSize (800, 600);
		scrolled.setContent (content);
		scrolled.setOrigin (100, 50);
		assertModel (scrolled, "scrolled composite");
		assertModel (new Canvas (shell, SWT.NONE), "canvas without bars");
	}

	@Test
	public void test_callerLaneIsFilledInPlace () {
		Table table = new Table (shell, SWT.V_SCROLL);
		table.setBounds (5, 5, 150, 150);
		for (int i = 0; i < 300; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		int [] lane = new int [ScrollBarModel.SIZE + 2];
		lane [ScrollBarModel.SIZE] = 77;
		assertTrue (ScrollBarModel.get (table, SWT.VERTICAL, lane));
		assertEquals (table.getVerticalBar ().getSelection (), lane [ScrollBarModel.SELECTION]);
		assertEquals (77, lane [ScrollBarModel.SIZE]);
		table.setTopIndex (120);
		assertTrue (ScrollBarModel.get (table, SWT.VERTICAL, lane));
		assertEquals (table.getVerticalBar ().getSelection (), lane [ScrollBarModel.SELECTION]);
		table.getVerticalBar ().setEnabled (false);
		assertTrue (ScrollBarModel.get (table, SWT.VERTICAL, lane));
		assertEquals (table.getVerticalBar ().getEnabled () ? 1 : 0, lane [ScrollBarModel.ENABLED]);
		Canvas bare = new Canvas (shell, SWT.NONE);
		int [] untouched = {1, 2, 3, 4, 5, 6, 7, 8};
		assertFalse (ScrollBarModel.get (bare, SWT.HORIZONTAL, untouched));
		assertArrayEquals (new int [] {1, 2, 3, 4, 5, 6, 7, 8}, untouched);
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = new Table (shell, SWT.V_SCROLL);
		assertThrows (IllegalArgumentException.class, () -> ScrollBarModel.get (null, SWT.VERTICAL));
		assertThrows (IllegalArgumentException.class, () -> ScrollBarModel.get (table, SWT.NONE));
		assertThrows (IllegalArgumentException.class, () -> ScrollBarModel.get (table, SWT.VERTICAL, null));
		assertThrows (IllegalArgumentException.class, () -> ScrollBarModel.get (table, SWT.VERTICAL, new int [ScrollBarModel.SIZE - 1]));
		table.dispose ();
		assertThrows (SWTException.class, () -> ScrollBarModel.get (table, SWT.VERTICAL));
	}

	/**
	 * In process, the official-jar differential Test_org_eclipse_swt_widgets_ScrollBarModelDifferential (tests bundle):
	 * after every seeded public operation the lanes of the Table, List and Tree equal their stock getters.
	 */
	@Test
	public void test_lanesEqualTheGettersOnSeededSteps () {
		for (int style : new int [] {SWT.NONE, SWT.VIRTUAL}) {
			SeededWidgetScene scene = new SeededWidgetScene (display, style);
			try {
				Random random = new Random (0x4D330013L);
				for (int step = 0; step < 150; step++) {
					scene.step (random);
					for (Scrollable widget : new Scrollable [] {scene.table, scene.list, scene.tree}) {
						assertArrayEquals (getters (widget.getVerticalBar ()), ScrollBarModel.get (widget, SWT.VERTICAL), style + " step " + step);
					}
				}
			} finally {
				scene.dispose ();
			}
		}
	}
}
