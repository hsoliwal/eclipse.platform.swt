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
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/** Tests of {@link ValueListPanel} on a real, never opened shell. */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ValueListPanel {

	/* {kind, place}: kind has the values Apple, apricot, Banana, the empty cell and Cherry */
	static final String [][] ROWS = {{"Apple", "x"}, {"Banana", "x"}, {"apricot", "y"}, {"Banana", "y"}, {null, "x"}, {"Cherry", "y"}, {"Apple", "y"}};
	Display display;
	Shell shell;
	ColumnFilter filter;
	ValueListPanel panel;
	Text search;
	Table list;

	@BeforeEach
	void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		filter = new ColumnFilter (2, ROWS.length, (row, column) -> ROWS [row][column]);
	}

	@AfterEach
	void tearDown () {
		shell.dispose ();
	}

	void open (int column) {
		panel = new ValueListPanel (shell, filter, column);
		panel.setSize (panel.computeSize (200, SWT.DEFAULT));
		search = (Text) panel.getChildren () [0];
		list = (Table) panel.getChildren () [1];
	}

	/* the texts of the items after the first, and a mark per item: + checked, - unchecked, ~ grayed */
	String items () {
		StringBuilder out = new StringBuilder ();
		for (TableItem item : list.getItems ()) {
			out.append (item.getGrayed () ? '~' : item.getChecked () ? '+' : '-');
			if (list.indexOf (item) > 0) out.append (item.getText ());
			out.append ('|');
		}
		return out.toString ();
	}

	void click (int index) {
		click (list, index);
	}

	static void click (Table list, int index) {
		TableItem item = list.getItem (index);
		item.setChecked (!item.getChecked ());
		Event event = new Event ();
		event.item = item;
		event.detail = SWT.CHECK;
		list.notifyListeners (SWT.Selection, event);
	}

	Set<String> shownKinds () {
		Set<String> kinds = new TreeSet<> ();
		for (int i = 0; i < filter.size (); i++) kinds.add (String.valueOf (ROWS [filter.map (i)][0]));
		return kinds;
	}

	static String blank () {
		return SWT.getMessage ("SWT_Values_Blank");
	}

	@Test
	void everyValueIsListedWithItsCountAndCheckedWithoutACriterion () {
		Locale before = Locale.getDefault ();
		Locale.setDefault (Locale.ENGLISH);
		try {
			open (0);
		} finally {
			Locale.setDefault (before);
		}
		assertEquals ("+|+" + blank () + " (1)|+Apple (2)|+apricot (1)|+Banana (2)|+Cherry (1)|", items ());
		assertEquals (SWT.getMessage ("SWT_Values_Select_All"), list.getItem (0).getText ());
		assertEquals (SWT.getMessage ("SWT_Values_Search"), search.getMessage ());
		assertFalse (panel.isModified ());
	}

	@Test
	void theChecksFollowTheFilterAndTheValuesItsOwnCriterionHidesAreOffered () {
		filter.setValues (0, "Banana", "Cherry");
		filter.setText (1, "y", TextMatch.EXACT);
		open (0);
		assertEquals (4, list.getItemCount () - 1, "the kinds of the y rows: Apple, apricot, Banana, Cherry");
		assertTrue (panel.isChecked ("Banana"));
		assertTrue (panel.isChecked ("Cherry"));
		assertFalse (panel.isChecked ("Apple"), "hidden by the column's own criterion, offered unchecked");
		assertFalse (panel.isChecked (null), "the empty cell is on an x row only: not offered");
		assertTrue (list.getItem (0).getGrayed (), "some values are checked");
		filter.setText (0, "an", TextMatch.CONTAINS);
		panel.refresh ();
		assertTrue (panel.isChecked ("Banana"));
		assertFalse (panel.isChecked ("Cherry"), "any criterion: checked when a row with the value passes");
	}

	@Test
	void applySetsTheCheckedValuesOnlyWhenChanged () {
		filter.setText (0, "a", TextMatch.CONTAINS | TextMatch.IGNORE_CASE);
		open (0);
		panel.apply ();
		assertNotNull (filter.getMatch (0), "no change: the text criterion stays");
		click (3);
		assertTrue (panel.isModified ());
		assertFalse (panel.isChecked ("apricot"));
		panel.apply ();
		assertFalse (panel.isModified ());
		assertNull (filter.getMatch (0));
		assertEquals (new TreeSet<> (java.util.List.of ("Apple", "Banana")), shownKinds ());
		panel.refresh ();
		assertFalse (panel.isChecked ("apricot"));
		assertFalse (panel.isChecked ("Cherry"));
		assertTrue (panel.isChecked ("Apple"));
	}

	@Test
	void everyValueCheckedRemovesTheCriterionAndNoneCheckedShowsNoRow () {
		filter.setValues (0, "Apple");
		open (0);
		click (0);
		assertEquals ("+|+" + blank () + " (1)|+Apple (2)|+apricot (1)|+Banana (2)|+Cherry (1)|", items (), "partly checked: the first item checks all");
		panel.apply ();
		assertFalse (filter.isActive (0));
		assertEquals (ROWS.length, filter.size ());
		panel.refresh ();
		click (0);
		assertEquals ("-|-" + blank () + " (1)|-Apple (2)|-apricot (1)|-Banana (2)|-Cherry (1)|", items (), "all checked: the first item unchecks all");
		panel.apply ();
		assertTrue (filter.isActive (0));
		assertEquals (0, filter.size ());
	}

	@Test
	void theSearchNarrowsTheListKeepsHiddenChecksAndTheFirstItemActsOnWhatItShows () {
		open (0);
		search.setText ("AP");
		assertEquals ("+|+Apple (2)|+apricot (1)|", items (), "contains, ignoring case");
		click (0);
		assertEquals ("-|-Apple (2)|-apricot (1)|", items ());
		search.setText ("");
		assertTrue (panel.isChecked ("Banana"), "values the search hid keep their checks");
		assertFalse (panel.isChecked ("Apple"));
		assertTrue (list.getItem (0).getGrayed ());
		search.setText ("zz");
		assertEquals (1, list.getItemCount ());
		click (0);
		search.setText ("");
		assertFalse (panel.isChecked ("Apple"), "the first item over no value changes nothing");
		assertTrue (panel.isChecked ("Banana"));
		panel.apply ();
		assertEquals (new TreeSet<> (java.util.List.of ("Banana", "Cherry", "null")), shownKinds (), "the empty cell is a value: it stays");
	}

	@Test
	void theFilterPopupListsTheValuesWhenAskedAndAppliesThemWhenItCloses () {
		Table table = new Table (shell, SWT.BORDER);
		table.setHeaderVisible (true);
		table.setBounds (0, 0, 300, 200);
		for (String name : new String [] {"kind", "place"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (name);
			column.setWidth (100);
		}
		FilterPopup popup = FilterPopup.install (table, filter);
		try {
			assertFalse (popup.getValueList ());
			popup.open (0);
			assertNull (popup.getValueListPanel (), "no value list unless asked for");
			popup.close ();
			popup.setValueList (true);
			popup.open (0);
			ValueListPanel values = popup.getValueListPanel ();
			assertNotNull (values);
			assertSame (popup.getShell (), values.getParent ());
			Table items = (Table) values.getChildren () [1];
			int cherry = -1;
			for (int i = 1; i < items.getItemCount (); i++) {
				if (items.getItem (i).getText ().startsWith ("Cherry")) cherry = i;
			}
			click (items, cherry);
			assertEquals (ROWS.length, filter.size (), "checks apply when the popup closes");
			popup.close ();
			assertTrue (values.isDisposed ());
			assertNull (popup.getValueListPanel ());
			assertEquals (new TreeSet<> (java.util.List.of ("Apple", "Banana", "apricot", "null")), shownKinds ());
			assertTrue (filter.isActive (0));
		} finally {
			popup.dispose ();
		}
	}

	@Test
	void argumentsAreChecked () {
		assertThrows (IllegalArgumentException.class, () -> new ValueListPanel (shell, null, 0));
		assertThrows (IllegalArgumentException.class, () -> new ValueListPanel (shell, filter, 2));
		assertThrows (IllegalArgumentException.class, () -> new ValueListPanel (shell, new ColumnFilter (1, 1, null), 0));
		assertEquals (0, shell.getChildren ().length, "a rejected panel creates no widget");
	}
}
