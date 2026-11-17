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

import java.text.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/** Tests of {@link FooterRow} on real Table and Tree widgets (the shell is never opened; paints go to an image). */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_FooterRow {

	static final int [] WIDTHS = {60, 70, 200, 200};
	static final String [] NAMES = {"ab", "ba", "ab", "cd", "ab", "dd"};
	static final double [] AMOUNTS = {1.5, 2, Double.NaN, 4, 1000.25, -3};
	Display display;
	Shell shell;
	Table table;
	ColumnFilter filter;
	ColumnSummary summary;
	FooterRow row;
	/* the reads of the values of columns 2 and 3 */
	final int [] reads = new int [4];

	@BeforeEach
	void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		table = new Table (shell, SWT.BORDER);
		table.setHeaderVisible (true);
		table.setBounds (0, 0, 700, 200);
		for (int i = 0; i < WIDTHS.length; i++) {
			TableColumn column = new TableColumn (table, i == 2 ? SWT.RIGHT : SWT.LEFT);
			column.setText ("c" + i);
			column.setWidth (WIDTHS [i]);
			column.setMoveable (true);
		}
		filter = new ColumnFilter (4, NAMES.length, (r, c) -> NAMES [r]);
		summary = new ColumnSummary (4, filter);
		summary.setValues (2, r -> {
			reads [2]++;
			return AMOUNTS [r];
		});
		summary.setValues (3, r -> {
			reads [3]++;
			return 10 * r;
		});
		row = new FooterRow (shell, table, summary);
		row.setBounds (0, 200, 700, row.computeSize (SWT.DEFAULT, SWT.DEFAULT).y);
	}

	@AfterEach
	void tearDown () {
		shell.dispose ();
	}

	static String text (int function, String value) {
		String [] keys = {null, "SWT_Summary_Count", "SWT_Summary_Sum", "SWT_Summary_Minimum", "SWT_Summary_Maximum", "SWT_Summary_Average"};
		return SWT.getMessage (keys [function]) + ": " + value;
	}

	static String number (double value) {
		return NumberFormat.getNumberInstance ().format (value);
	}

	/* the x of a column's left edge in the row */
	int left (int column) {
		int x = row.toControl (table.toDisplay (0, 0)).x + table.getClientArea ().x;
		for (int index : table.getColumnOrder ()) {
			if (index == column) return x;
			x += table.getColumn (index).getWidth ();
		}
		throw new AssertionError (column);
	}

	/* paints the row into a white image, the damage limited to x in [from, to) */
	ImageData paint (int from, int to) {
		Point size = row.getSize ();
		Image image = new Image (display, size.x, size.y);
		GC gc = new GC (image);
		try {
			gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
			gc.fillRectangle (0, 0, size.x, size.y);
			Event event = new Event ();
			event.gc = gc;
			event.x = from;
			event.width = to - from;
			event.height = size.y;
			row.notifyListeners (SWT.Paint, event);
		} finally {
			gc.dispose ();
		}
		ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	/* the x of the leftmost and rightmost dark pixel below the top line within [from, to), or {-1, -1} */
	static int [] ink (ImageData data, int from, int to) {
		int first = -1, last = -1;
		for (int x = from; x < to; x++) {
			for (int y = 1; y < data.height; y++) {
				RGB rgb = data.palette.getRGB (data.getPixel (x, y));
				if (rgb.red < 160 && rgb.green < 160 && rgb.blue < 160) {
					if (first < 0) first = x;
					last = x;
					break;
				}
			}
		}
		return new int [] {first, last};
	}

	Event menuAt (int x, int detail) {
		Event event = new Event ();
		Point point = row.toDisplay (x, row.getSize ().y / 2);
		event.x = point.x;
		event.y = point.y;
		event.detail = detail;
		event.doit = true;
		row.notifyListeners (SWT.MenuDetect, event);
		return event;
	}

	@Test
	void theTextFollowsTheFunctionTheFilterAndTheFormat () {
		assertEquals ("", row.getText (2));
		summary.setFunction (2, ColumnSummary.SUM);
		assertEquals (text (ColumnSummary.SUM, number (1.5 + 2 + 4 + 1000.25 - 3)), row.getText (2));
		filter.setText (0, "ab", TextMatch.EXACT);
		assertEquals (text (ColumnSummary.SUM, number (1.5 + 1000.25)), row.getText (2));
		summary.setFunction (1, ColumnSummary.COUNT);
		assertEquals (text (ColumnSummary.COUNT, "3"), row.getText (1), "a column without values counts the rows shown");
		summary.setFunction (2, ColumnSummary.COUNT);
		assertEquals (text (ColumnSummary.COUNT, "2"), row.getText (2), "a row without a value is not counted");
		summary.setFunction (2, ColumnSummary.AVERAGE);
		row.setFormat (2, new DecimalFormat ("0.000", DecimalFormatSymbols.getInstance (Locale.ROOT)));
		assertEquals (text (ColumnSummary.AVERAGE, "500.875"), row.getText (2));
		filter.setText (0, "zz", TextMatch.EXACT);
		assertEquals ("", row.getText (2), "no value: no text");
		summary.setFunction (3, ColumnSummary.COUNT);
		row.setFormat (3, new DecimalFormat ("0.00"));
		assertEquals (text (ColumnSummary.COUNT, "0"), row.getText (3), "a count is a whole number whatever the format");
		assertThrows (IllegalArgumentException.class, () -> row.getText (4));
		assertThrows (IllegalArgumentException.class, () -> row.setFormat (-1, null));
	}

	@Test
	void nothingIsComputedUntilTheRowPaintsOrAsks () {
		summary.setFunction (2, ColumnSummary.SUM);
		summary.setFunction (3, ColumnSummary.MAXIMUM);
		filter.setText (0, "ab", TextMatch.EXACT);
		filter.clear (0);
		filter.setText (0, "a", TextMatch.CONTAINS);
		assertArrayEquals (new int [4], reads, "a row that is not painted computes nothing");
		assertEquals (text (ColumnSummary.SUM, number (1.5 + 2 + 1000.25)), row.getText (2));
		assertEquals (4, reads [2], "one pass over the rows shown");
		row.getText (2);
		assertEquals (4, reads [2], "asked again without a change: no pass");
		assertEquals (0, reads [3]);
	}

	@Test
	void aPaintComputesTheColumnsItsDamageTouchesWithTheirAlignment () {
		summary.setFunction (2, ColumnSummary.SUM);
		summary.setFunction (3, ColumnSummary.SUM);
		int left2 = left (2), left3 = left (3);
		ImageData data = paint (left2 + 10, left2 + 20);
		assertEquals (NAMES.length, reads [2]);
		assertEquals (0, reads [3], "a column outside the damage is not computed");
		int [] right = ink (data, left2, left2 + WIDTHS [2]);
		assertTrue (right [0] > left2 + WIDTHS [2] / 2, "a right aligned column paints at its right: " + Arrays.toString (right));
		assertTrue (right [1] < left2 + WIDTHS [2], "inside its column");
		data = paint (0, row.getSize ().x);
		assertEquals (NAMES.length, reads [3]);
		int [] leftAligned = ink (data, left3, left3 + WIDTHS [3]);
		assertTrue (leftAligned [0] >= left3 && leftAligned [0] < left3 + WIDTHS [3] / 4, "a left aligned column paints at its left: " + Arrays.toString (leftAligned));
		assertArrayEquals (new int [] {-1, -1}, ink (data, left (0), left (2)), "columns without a function paint nothing");
	}

	@Test
	void aNarrowColumnPaintsInsideItsMargins () {
		summary.setFunction (2, ColumnSummary.SUM);
		table.getColumn (2).setWidth (30);
		int left2 = left (2);
		int [] painted = ink (paint (0, row.getSize ().x), left2 - 4, left2 + 40);
		assertTrue (painted [0] >= left2 && painted [1] < left2 + 30, Arrays.toString (painted));
		table.getColumn (2).setWidth (12);
		painted = ink (paint (0, row.getSize ().x), left2 - 20, left2 + 32);
		assertTrue (painted [0] < 0 || painted [0] >= left2 + FooterRowMargin.MARGIN && painted [1] < left2 + 12 - FooterRowMargin.MARGIN,
			"even an ellipsis that does not fit stays inside the margins: " + Arrays.toString (painted));
		table.getColumn (2).setWidth (6);
		assertArrayEquals (new int [] {-1, -1}, ink (paint (0, row.getSize ().x), left (2) - 4, left (2) + 12), "no room inside the margins: nothing painted");
	}

	/* FooterRow.MARGIN, which the test package cannot see */
	static final class FooterRowMargin {
		static final int MARGIN = 4;
	}

	@Test
	void theMenuOverAColumnChoosesItsFunctionFollowingTheColumnOrder () {
		Event event = menuAt (left (1) + 5, SWT.MENU_MOUSE);
		assertTrue (event.doit);
		MenuItem [] items = row.getMenu ().getItems ();
		assertEquals (6, items.length);
		for (MenuItem item : items) assertTrue ((item.getStyle () & SWT.RADIO) != 0);
		assertTrue (items [ColumnSummary.NONE].getSelection ());
		assertEquals (SWT.getMessage ("SWT_Summary_Sum"), items [ColumnSummary.SUM].getText ());
		items [ColumnSummary.COUNT].setSelection (true);
		items [ColumnSummary.COUNT].notifyListeners (SWT.Selection, new Event ());
		items [ColumnSummary.NONE].setSelection (false);
		items [ColumnSummary.NONE].notifyListeners (SWT.Selection, new Event ());
		assertEquals (ColumnSummary.COUNT, summary.getFunction (1), "the item that lost the selection sets nothing");
		table.setColumnOrder (new int [] {3, 0, 1, 2});
		menuAt (left (3) + 5, SWT.MENU_MOUSE);
		assertTrue (row.getMenu ().getItems () [ColumnSummary.NONE].getSelection ());
		row.getMenu ().getItems () [ColumnSummary.MAXIMUM].setSelection (true);
		row.getMenu ().getItems () [ColumnSummary.MAXIMUM].notifyListeners (SWT.Selection, new Event ());
		assertEquals (ColumnSummary.MAXIMUM, summary.getFunction (3), "the column at the point, in display order");
		assertEquals (text (ColumnSummary.MAXIMUM, number (50)), row.getText (3));
		assertFalse (menuAt (left (2) + WIDTHS [2] + 5, SWT.MENU_MOUSE).doit, "no column at the point: no menu");
	}

	@Test
	void theKeyboardMenuIsForTheFirstShownColumn () {
		table.setColumnOrder (new int [] {1, 2, 0, 3});
		table.getColumn (1).setWidth (0);
		assertTrue (menuAt (0, SWT.MENU_KEYBOARD).doit);
		row.getMenu ().getItems () [ColumnSummary.SUM].setSelection (true);
		row.getMenu ().getItems () [ColumnSummary.SUM].notifyListeners (SWT.Selection, new Event ());
		assertEquals (ColumnSummary.SUM, summary.getFunction (2), "column 1 is hidden (width 0); column 2 is the first shown in display order");
		assertEquals (ColumnSummary.NONE, summary.getFunction (0));
		assertEquals (ColumnSummary.NONE, summary.getFunction (1));
	}

	@Test
	void fillMenuPutsTheChoicesIntoAnotherMenu () {
		summary.setFunction (2, ColumnSummary.AVERAGE);
		Menu menu = new Menu (shell);
		new MenuItem (menu, SWT.PUSH).setText ("old");
		row.fillMenu (menu, 2);
		assertEquals (6, menu.getItemCount ());
		assertTrue (menu.getItem (ColumnSummary.AVERAGE).getSelection ());
		assertThrows (IllegalArgumentException.class, () -> row.fillMenu (null, 2));
		assertThrows (IllegalArgumentException.class, () -> row.fillMenu (menu, 9));
		assertEquals (6, menu.getItemCount (), "a rejected column leaves the menu as it was");
	}

	@Test
	void extraColumnsAreLeftAloneAndDisposingTheControlDisposesTheRow () {
		new TableColumn (table, SWT.NONE).setWidth (50);
		row.refresh ();
		summary.setFunction (3, ColumnSummary.SUM);
		paint (0, row.getSize ().x);
		assertFalse (menuAt (left (4) + 5, SWT.MENU_MOUSE).doit, "a column the summary does not have gets no menu");
		table.dispose ();
		assertTrue (row.isDisposed ());
		filter.setText (0, "ab", TextMatch.EXACT);
		summary.setFunction (2, ColumnSummary.SUM);
		assertEquals (NAMES.length, reads [3]);
	}

	@Test
	void aTreeFooterFollowsItsColumns () {
		Tree tree = new Tree (shell, SWT.BORDER);
		tree.setHeaderVisible (true);
		tree.setBounds (0, 300, 300, 100);
		for (int i = 0; i < 2; i++) new TreeColumn (tree, SWT.NONE).setWidth (100);
		ColumnSummary treeSummary = new ColumnSummary (2, filter);
		treeSummary.setValues (1, r -> AMOUNTS [r]);
		FooterRow treeRow = new FooterRow (shell, tree, treeSummary);
		treeRow.setBounds (0, 400, 300, treeRow.computeSize (SWT.DEFAULT, SWT.DEFAULT).y);
		treeSummary.setFunction (1, ColumnSummary.MINIMUM);
		assertEquals (text (ColumnSummary.MINIMUM, number (-3)), treeRow.getText (1));
		Event event = new Event ();
		Point point = treeRow.toDisplay (treeRow.toControl (tree.toDisplay (tree.getClientArea ().x, 0)).x + 150, 2);
		event.x = point.x;
		event.y = point.y;
		event.doit = true;
		treeRow.notifyListeners (SWT.MenuDetect, event);
		assertTrue (event.doit);
		assertTrue (treeRow.getMenu ().getItem (ColumnSummary.MINIMUM).getSelection (), "the menu is for column 1");
	}
}
