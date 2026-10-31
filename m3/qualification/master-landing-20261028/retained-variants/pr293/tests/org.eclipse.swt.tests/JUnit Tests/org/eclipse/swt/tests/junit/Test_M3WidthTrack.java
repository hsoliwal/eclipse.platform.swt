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
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3WidthTrack and M3TextMeasure (enhanced M3 API, rows M3-WIDTH-TRACK and
 * M3-SHAPING-MEASURE). The oracle is <code>GC.textExtent</code> on a fresh GC for every text. The
 * class passes unchanged in every startup mode (run with <code>-Dswt.widget.mode=viewport</code> and
 * <code>original</code>: the mode differential).
 */
public class Test_M3WidthTrack {
	Display display;
	Shell shell;
	Font bold;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 420, 420);
		shell.open ();
		FontData data = shell.getFont ().getFontData () [0];
		bold = new Font (display, data.getName (), data.getHeight () + 4, SWT.BOLD);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
		bold.dispose ();
	}

	/* the oracle: one fresh GC per text */
	private int extent (Control control, String text, Font font) {
		GC gc = new GC (control);
		try {
			gc.setFont (font);
			return gc.textExtent (text).x;
		} finally {
			gc.dispose ();
		}
	}

	private int [] oracle (org.eclipse.swt.widgets.List list) {
		int best = 0, row = -1;
		for (int i = 0; i < list.getItemCount (); i++) {
			int width = extent (list, list.getItem (i), list.getFont ());
			if (row < 0 || width > best) {
				best = width;
				row = i;
			}
		}
		return new int [] {row, best};
	}

	private int [] oracle (Table table, int column) {
		int best = 0, row = -1;
		for (int i = 0; i < table.getItemCount (); i++) {
			TableItem item = table.getItem (i);
			int width = extent (table, item.getText (column), item.getFont (column));
			if (row < 0 || width > best) {
				best = width;
				row = i;
			}
		}
		return new int [] {row, best};
	}

	private static String word (Random random) {
		char [] text = new char [1 + random.nextInt (24)];
		for (int i = 0; i < text.length; i++) text [i] = (char) ('a' + random.nextInt (26) - (random.nextInt (5) == 0 ? 32 : 0));
		return new String (text);
	}

	private long measured (Control widget) {
		return M3WidthTrack.stats (widget) [M3TextMeasure.MEASURED];
	}

	@Test
	public void test_listAgreesWithTheExtentOracle () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.V_SCROLL);
		assertEquals (0, M3WidthTrack.contentWidth (list));
		assertEquals (-1, M3WidthTrack.widestRow (list));
		Random random = new Random (5);
		for (int i = 0; i < 300; i++) list.add (word (random));
		int [] expected = oracle (list);
		assertEquals (expected [1], M3WidthTrack.contentWidth (list));
		assertEquals (expected [0], M3WidthTrack.widestRow (list));
		list.setFont (bold);
		assertArrayEquals (oracle (list), new int [] {M3WidthTrack.widestRow (list), M3WidthTrack.contentWidth (list)});
	}

	@Test
	public void test_askingAgainMeasuresOnlyNewTexts () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.V_SCROLL);
		for (int i = 0; i < 200; i++) list.add ("row " + i);
		M3WidthTrack.contentWidth (list);
		long first = measured (list);
		assertEquals (200, first);
		M3WidthTrack.contentWidth (list);
		M3WidthTrack.widestRow (list);
		assertEquals (first, measured (list), "no edit, no measurement");
		/* an insert above everything measures the new text only */
		list.add ("a much longer row than every other row", 0);
		assertEquals (0, M3WidthTrack.widestRow (list));
		assertEquals (first + 1, measured (list));
		/* narrowing the widest row: one measurement, and the next widest row defines the width */
		list.setItem (0, "x");
		int [] expected = oracle (list);
		assertEquals (expected [0], M3WidthTrack.widestRow (list));
		assertEquals (expected [1], M3WidthTrack.contentWidth (list));
		assertEquals (first + 2, measured (list));
	}

	@Test
	public void test_tableColumnsUseTheCellFonts () {
		Table table = new Table (shell, SWT.MULTI);
		for (int c = 0; c < 3; c++) new TableColumn (table, SWT.NONE).setWidth (80);
		Random random = new Random (11);
		for (int i = 0; i < 150; i++) {
			TableItem item = new TableItem (table, SWT.NONE);
			for (int c = 0; c < 3; c++) item.setText (c, word (random));
			if (i % 7 == 0) item.setFont (1, bold);
			if (i % 11 == 0) item.setFont (bold);
		}
		for (int c = 0; c < 3; c++) {
			int [] expected = oracle (table, c);
			assertEquals (expected [1], M3WidthTrack.contentWidth (table, c), "column " + c);
			assertEquals (expected [0], M3WidthTrack.widestRow (table, c), "column " + c);
		}
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.contentWidth (table, 3));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.widestRow (table, -1));
	}

	@Test
	public void test_tableWithoutColumnsAndVirtualTable () {
		Table plain = new Table (shell, SWT.NONE);
		assertEquals (-1, M3WidthTrack.widestRow (plain, 0));
		for (int i = 0; i < 40; i++) new TableItem (plain, SWT.NONE).setText ("item " + i * i);
		assertArrayEquals (oracle (plain, 0), new int [] {M3WidthTrack.widestRow (plain, 0), M3WidthTrack.contentWidth (plain, 0)});
		Table virtual = new Table (shell, SWT.VIRTUAL);
		virtual.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("v" + event.index * 7));
		virtual.setItemCount (500);
		assertArrayEquals (oracle (virtual, 0), new int [] {M3WidthTrack.widestRow (virtual, 0), M3WidthTrack.contentWidth (virtual, 0)});
	}

	@Test
	public void test_invalidateMeasuresTheRowsAgain () {
		Table table = new Table (shell, SWT.NONE);
		for (int i = 0; i < 20; i++) new TableItem (table, SWT.NONE).setText ("item " + i);
		M3WidthTrack.contentWidth (table, 0);
		long before = measured (table);
		M3WidthTrack.invalidate (table, M3ItemRange.of (5, 9));
		M3WidthTrack.invalidate (table, M3ItemRange.of (100, 200));
		M3WidthTrack.invalidate (table, M3ItemRange.EMPTY);
		M3WidthTrack.contentWidth (table, 0);
		assertEquals (before + 5, measured (table));
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.NONE);
		for (int i = 0; i < 20; i++) list.add ("item " + i);
		M3WidthTrack.contentWidth (list);
		M3WidthTrack.invalidate (list, M3ItemRange.of (0, 2));
		M3WidthTrack.invalidate (list, M3ItemRange.EMPTY);
		M3WidthTrack.contentWidth (list);
		assertEquals (23, measured (list));
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = new Table (shell, SWT.NONE);
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.NONE);
		assertArrayEquals (new long [3], M3WidthTrack.stats (table));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.contentWidth ((org.eclipse.swt.widgets.List) null));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.widestRow ((org.eclipse.swt.widgets.List) null));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.contentWidth ((Table) null, 0));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.widestRow ((Table) null, 0));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.invalidate (table, null));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.invalidate ((Table) null, M3ItemRange.EMPTY));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.invalidate (list, null));
		assertThrows (IllegalArgumentException.class, () -> M3WidthTrack.stats (null));
		table.dispose ();
		assertThrows (SWTException.class, () -> M3WidthTrack.contentWidth (table, 0));
		assertThrows (SWTException.class, () -> M3WidthTrack.stats (table));
	}

	/* ------------------------------------------------------------------ M3TextMeasure */

	@Test
	public void test_measureAgreesWithTheExtentAndCaches () {
		M3TextMeasure measure = new M3TextMeasure (shell);
		assertEquals (1 << 16, measure.capacity ());
		Font font = shell.getFont ();
		assertEquals (extent (shell, "Hello", bold), measure.width ("Hello", bold));
		assertEquals (extent (shell, "Hello", font), measure.width ("Hello", null));
		assertEquals (extent (shell, "Hello", bold), measure.width (new String ("Hello"), bold));
		assertArrayEquals (new long [] {2, 1, 2}, measure.stats ());
		int [] lane = measure.widths (new String [] {"a", "Hello", ""}, bold);
		assertArrayEquals (new int [] {extent (shell, "a", bold), extent (shell, "Hello", bold), extent (shell, "", bold)}, lane);
		measure.invalidate (bold);
		assertEquals (1, measure.stats () [M3TextMeasure.ENTRIES]);
		measure.invalidate (null);
		assertEquals (0, measure.stats () [M3TextMeasure.ENTRIES]);
		measure.release ();
		measure.release ();
	}

	@Test
	public void test_forgetKeepsEveryOtherEntryReachable () {
		M3TextMeasure measure = new M3TextMeasure (shell);
		Random random = new Random (3);
		java.util.List<String> texts = new ArrayList<> ();
		for (int i = 0; i < 2000; i++) texts.add (word (random) + i);
		for (String text : texts) measure.width (text, bold);
		Set<String> forgotten = new HashSet<> ();
		for (int i = 0; i < 700; i++) forgotten.add (texts.get (random.nextInt (texts.size ())));
		for (String text : forgotten) measure.forget (text, bold);
		measure.forget ("never measured", bold);
		long before = measure.stats () [M3TextMeasure.MEASURED];
		for (String text : texts) measure.width (text, bold);
		measure.release ();
		assertEquals (before + forgotten.size (), measure.stats () [M3TextMeasure.MEASURED], "only the forgotten texts are measured again");
		assertEquals (texts.size (), measure.stats () [M3TextMeasure.ENTRIES]);
	}

	@Test
	public void test_capacityBoundsTheTable () {
		M3TextMeasure measure = new M3TextMeasure (shell, 10);
		for (int i = 0; i < 35; i++) measure.width ("t" + i, null);
		assertTrue (measure.stats () [M3TextMeasure.ENTRIES] <= 10);
		assertEquals (35, measure.stats () [M3TextMeasure.MEASURED]);
		measure.release ();
		assertThrows (IllegalArgumentException.class, () -> new M3TextMeasure (null));
		assertThrows (IllegalArgumentException.class, () -> new M3TextMeasure (shell, 0));
		assertThrows (IllegalArgumentException.class, () -> measure.width (null, null));
		assertThrows (IllegalArgumentException.class, () -> measure.widths (null, null));
		assertThrows (IllegalArgumentException.class, () -> measure.widths (new String [] {"a", null}, null));
		assertThrows (IllegalArgumentException.class, () -> measure.forget (null, null));
		Font gone = new Font (display, shell.getFont ().getFontData ());
		gone.dispose ();
		assertThrows (IllegalArgumentException.class, () -> measure.width ("new text", gone));
		measure.release ();
	}
}
