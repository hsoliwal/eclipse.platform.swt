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

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Tests of {@link ColumnChooser} on real Table and Tree widgets. The shell is opened only where a test needs the
 * table laid out: GTK sends a column's <code>SWT.Move</code> from the size allocation of its header.
 */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ColumnChooser {

	static final int [] WIDTHS = {50, 60, 70, 80};
	Display display;
	Shell shell;
	Table table;
	ColumnArrangement arrangement;
	/* the arrangement's changes as "kind column from to", and the control's column resize events */
	final java.util.List<String> changes = new ArrayList<> ();
	final int [] resizes = new int [WIDTHS.length];

	@BeforeEach
	void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		table = new Table (shell, SWT.BORDER);
		table.setHeaderVisible (true);
		table.setBounds (0, 0, 400, 200);
		String [] names = {"beta", "Zeta", "\u00C4rger", "Alpha"};
		for (int i = 0; i < WIDTHS.length; i++) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (names [i]);
			column.setWidth (WIDTHS [i]);
			column.setMoveable (true);
			int index = i;
			column.addListener (SWT.Resize, e -> resizes [index]++);
		}
		arrangement = new ColumnArrangement (WIDTHS.length);
		arrangement.addListener ((kind, column, from, to) -> changes.add (kind + " " + column + " " + from + " " + to));
	}

	@AfterEach
	void tearDown () {
		shell.dispose ();
	}

	int [] widths () {
		int [] result = new int [table.getColumnCount ()];
		for (int i = 0; i < result.length; i++) result [i] = table.getColumn (i).getWidth ();
		return result;
	}

	@Test
	void hidingStoresTheWidthAndShowingRestoresItTouchingOnlyThatColumn () {
		new ColumnChooser (table, arrangement);
		Arrays.fill (resizes, 0);
		arrangement.setVisible (1, false);
		assertArrayEquals (new int [] {50, 0, 70, 80}, widths ());
		assertFalse (table.getColumn (1).getResizable ());
		assertEquals (60, arrangement.getWidth (1), "the hidden column's width is kept in the arrangement");
		assertArrayEquals (new int [] {0, 1, 0, 0}, resizes, "only the hidden column changed");
		arrangement.setVisible (1, true);
		assertArrayEquals (WIDTHS, widths ());
		assertTrue (table.getColumn (1).getResizable ());
		assertArrayEquals (new int [] {0, 2, 0, 0}, resizes);
	}

	@Test
	void aColumnThatWasNotResizableStaysSoAfterShowing () {
		table.getColumn (2).setResizable (false);
		new ColumnChooser (table, arrangement);
		arrangement.setVisible (2, false);
		arrangement.setVisible (2, true);
		assertFalse (table.getColumn (2).getResizable ());
		assertEquals (70, table.getColumn (2).getWidth ());
	}

	@Test
	void theBindingsOwnChangesAreNotReportedBack () {
		new ColumnChooser (table, arrangement);
		changes.clear ();
		arrangement.setVisible (0, false);
		arrangement.setVisible (0, true);
		assertEquals (java.util.List.of ("2 0 0 0", "4 0 -1 50", "1 0 0 0"), changes,
				"the hide, the one width record it causes, the show: nothing echoed from the table");
	}

	@Test
	void theOrderFollowsTheArrangementAndTheUser () {
		/* the header moves reach the binding as SWT.Move, which GTK sends once the moved headers are laid out */
		shell.open ();
		new ColumnChooser (table, arrangement);
		arrangement.move (3, 0);
		assertArrayEquals (new int [] {3, 0, 1, 2}, table.getColumnOrder ());
		dispatchUntil (() -> false, 200);
		changes.clear ();
		table.setColumnOrder (new int [] {2, 3, 0, 1});
		int [] moved = {2, 3, 0, 1};
		dispatchUntil (() -> Arrays.equals (moved, arrangement.getOrder ()), 2000);
		assertArrayEquals (moved, arrangement.getOrder (), "a header move sets the arrangement's order");
		assertFalse (changes.isEmpty ());
	}

	/* dispatches events until the condition holds or the time runs out */
	void dispatchUntil (java.util.function.BooleanSupplier condition, int millis) {
		long end = System.currentTimeMillis () + millis;
		while (!condition.getAsBoolean () && System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) {
				try {
					Thread.sleep (5);
				} catch (InterruptedException e) {
					Thread.currentThread ().interrupt ();
					return;
				}
			}
		}
	}

	@Test
	void resizingAShownColumnSetsItsWidthAndAHiddenOneIsIgnored () {
		new ColumnChooser (table, arrangement);
		table.getColumn (2).setWidth (99);
		assertEquals (99, arrangement.getWidth (2));
		arrangement.setVisible (3, false);
		changes.clear ();
		table.getColumn (3).setWidth (0);
		assertTrue (changes.isEmpty ());
		arrangement.setWidth (2, 44);
		assertEquals (44, table.getColumn (2).getWidth ());
		arrangement.setWidth (3, 33);
		assertEquals (0, table.getColumn (3).getWidth (), "a hidden column keeps width 0");
		arrangement.setVisible (3, true);
		assertEquals (33, table.getColumn (3).getWidth ());
	}

	@Test
	void theMenuListsEveryColumnByCollatorWithItsState () {
		ColumnChooser chooser = new ColumnChooser (table, arrangement);
		arrangement.setVisible (1, false);
		Menu menu = new Menu (shell);
		chooser.fillMenu (menu);
		MenuItem [] items = menu.getItems ();
		String [] texts = Arrays.stream (items).map (MenuItem::getText).toArray (String []::new);
		assertArrayEquals (new String [] {"Alpha", "\u00C4rger", "beta", "Zeta"}, texts, "Collator order, not String order");
		assertFalse (items [3].getSelection (), "the hidden column is listed unchecked");
		assertTrue (items [0].getSelection ());
		assertTrue (Arrays.stream (items).allMatch (i -> (i.getStyle () & SWT.CHECK) != 0));
		chooser.fillMenu (menu);
		assertEquals (4, menu.getItemCount (), "filling again replaces the items");
	}

	@Test
	void selectingAnItemShowsOrHidesItsColumn () {
		ColumnChooser chooser = new ColumnChooser (table, arrangement);
		Menu menu = new Menu (shell);
		chooser.fillMenu (menu);
		MenuItem beta = menu.getItem (2);
		beta.setSelection (false);
		beta.notifyListeners (SWT.Selection, new Event ());
		assertFalse (arrangement.isVisible (0));
		assertEquals (0, table.getColumn (0).getWidth ());
	}

	@Test
	void theLastShownColumnAndLockedColumnsCannotBeHidden () {
		ColumnChooser chooser = new ColumnChooser (table, arrangement);
		chooser.setLocked (3, true);
		Menu menu = new Menu (shell);
		chooser.fillMenu (menu);
		assertFalse (menu.getItem (0).getEnabled (), "Alpha is locked");
		assertTrue (menu.getItem (1).getEnabled ());
		arrangement.setVisible (0, false);
		arrangement.setVisible (1, false);
		arrangement.setVisible (3, false);
		chooser.fillMenu (menu);
		assertFalse (menu.getItem (1).getEnabled (), "\u00C4rger is the last shown column");
		assertTrue (menu.getItem (2).getEnabled (), "a hidden column can be shown");
	}

	@Test
	void anAmpersandInAHeaderIsNotAMnemonic () {
		table.getColumn (0).setText ("R&D");
		ColumnChooser chooser = new ColumnChooser (table, arrangement);
		Menu menu = new Menu (shell);
		chooser.fillMenu (menu);
		assertTrue (Arrays.stream (menu.getItems ()).anyMatch (i -> "R&&D".equals (i.getText ())));
	}

	@Test
	void theHeaderMenuIsChosenOverTheHeaderAndTheOwnMenuOverTheRows () {
		Menu rows = new Menu (shell);
		table.setMenu (rows);
		ColumnChooser chooser = new ColumnChooser (table, arrangement);
		chooser.installHeaderMenu ();
		Rectangle area = table.getClientArea ();
		Point header = table.toDisplay (10, area.y + table.getHeaderHeight () / 2);
		Point body = table.toDisplay (10, area.y + table.getHeaderHeight () + 20);
		menuDetect (header, SWT.MENU_MOUSE);
		assertSame (chooser.getHeaderMenu (), table.getMenu ());
		assertEquals (4, chooser.getHeaderMenu ().getItemCount ());
		menuDetect (body, SWT.MENU_MOUSE);
		assertSame (rows, table.getMenu ());
		menuDetect (header, SWT.MENU_KEYBOARD);
		assertSame (rows, table.getMenu (), "a keyboard menu request gets the rows' menu");
		chooser.dispose ();
		assertSame (rows, table.getMenu ());
		assertTrue (table.getHeaderHeight () > 0);
	}

	void menuDetect (Point at, int detail) {
		Event event = new Event ();
		event.x = at.x;
		event.y = at.y;
		event.detail = detail;
		table.notifyListeners (SWT.MenuDetect, event);
	}

	@Test
	void aRestoredStateIsAppliedAndDisposingEndsTheBinding () {
		ColumnChooser chooser = new ColumnChooser (table, arrangement);
		ColumnArrangement other = new ColumnArrangement (WIDTHS.length);
		other.setVisible (2, false);
		other.move (0, 3);
		other.setWidth (1, 25);
		arrangement.restoreState (other.saveState ());
		assertArrayEquals (new int [] {1, 2, 3, 0}, table.getColumnOrder ());
		assertArrayEquals (new int [] {50, 25, 0, 80}, widths ());
		chooser.dispose ();
		assertTrue (chooser.isDisposed ());
		arrangement.setVisible (2, true);
		assertEquals (0, table.getColumn (2).getWidth (), "a disposed binding leaves the table alone");
		ColumnChooser second = new ColumnChooser (table, arrangement);
		table.dispose ();
		assertTrue (second.isDisposed ());
	}

	@Test
	void aTreeIsBoundTheSameWay () {
		Tree tree = new Tree (shell, SWT.BORDER);
		for (int width : WIDTHS) new TreeColumn (tree, SWT.NONE).setWidth (width);
		ColumnArrangement treeArrangement = new ColumnArrangement (WIDTHS.length);
		new ColumnChooser (tree, treeArrangement);
		treeArrangement.setVisible (0, false);
		treeArrangement.move (3, 0);
		assertEquals (0, tree.getColumn (0).getWidth ());
		assertArrayEquals (new int [] {3, 0, 1, 2}, tree.getColumnOrder ());
		treeArrangement.setVisible (0, true);
		assertEquals (50, tree.getColumn (0).getWidth ());
	}

	@Test
	void columnCountsMustAgree () {
		assertThrows (IllegalArgumentException.class, () -> new ColumnChooser (table, new ColumnArrangement (3)));
		assertThrows (IllegalArgumentException.class, () -> new ColumnChooser ((Table) null, arrangement));
	}
}
