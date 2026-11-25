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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.util.BitSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GTK List measured column: the tree view runs in fixed-height mode, so GtkTreeView measures and draws only
 * the rows it shows, and the column width comes from the List's width lane. The rows GtkTreeView itself
 * measures or draws are counted through the List's test seam (a cell data function installed when
 * <code>List.nativeMeasureProbe</code> is set before the List is created). The stock column
 * (<code>List.measuredColumns</code> false, as with <code>-Dswt.widget.mode.List=original</code>) is the
 * comparison in the same run. GTK3 only; the measured column is not enabled on GTK4.
 */
public class Test_org_eclipse_swt_widgets_List_MeasuredColumn {
	static final int ROWS = 100_000;
	Display display;
	Shell shell;
	boolean measuredDefault;

	@BeforeEach
	public void setUp () {
		assumeTrue (SwtTestUtil.isGTK, "GTK List measured column");
		display = Display.getDefault ();
		measuredDefault = (Boolean) getStatic ("measuredColumns");
		assumeTrue (measuredDefault, "measured column enabled (GTK3, viewport mode)");
		shell = new Shell (display);
		shell.setLayout (null);
		shell.setBounds (20, 20, 300, 360);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		if (display == null) return;
		setStatic ("nativeMeasureProbe", false);
		setStatic ("measuredColumns", measuredDefault);
		if (shell != null) shell.dispose ();
	}

	private static Field field (String name) {
		try {
			Field field = List.class.getDeclaredField (name);
			field.setAccessible (true);
			return field;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	private static Object getStatic (String name) {
		try {
			return field (name).get (null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	private static void setStatic (String name, Object value) {
		try {
			field (name).set (null, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	private static Object get (List list, String name) {
		try {
			return field (name).get (list);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	private static BitSet probed (List list) {
		return (BitSet) get (list, "probedRows");
	}

	/* Creates a list in the measured (true) or stock (false) column mode, counting the rows GtkTreeView measures. */
	private List list (int style, boolean measured) {
		return list (style, measured, true);
	}

	private List list (int style, boolean measured, boolean probe) {
		setStatic ("measuredColumns", measured);
		setStatic ("nativeMeasureProbe", probe);
		try {
			List list = new List (shell, style);
			list.setBounds (5, 5, 260, 300);
			return list;
		} finally {
			setStatic ("nativeMeasureProbe", false);
			setStatic ("measuredColumns", measuredDefault);
		}
	}

	private static String [] items (int count) {
		String [] items = new String [count];
		for (int i = 0; i < count; i++) items [i] = "item " + i;
		return items;
	}

	private void paint (List list) {
		list.redraw ();
		list.update ();
		while (display.readAndDispatch ()) {
			// drain
		}
	}

	/* Runs the event loop until it stays idle for about 50 ms; the elapsed ms, or -1 if it was still busy at the cap. */
	private long drainUntilIdle (long capMs) {
		long start = System.nanoTime (), deadline = start + capMs * 1_000_000L;
		int quiet = 0;
		while (System.nanoTime () < deadline) {
			if (display.readAndDispatch ()) {
				quiet = 0;
				continue;
			}
			if (++quiet >= 5) return (System.nanoTime () - start) / 1_000_000L;
			try {
				Thread.sleep (10);
			} catch (InterruptedException e) {
				Thread.currentThread ().interrupt ();
				break;
			}
		}
		return -1;
	}

	/* An upper bound of the rows on screen: getItemHeight may exceed the height GtkTreeView gives a row by a few pixels. */
	private static int visibleRows (List list) {
		return list.getClientArea ().height / Math.max (1, list.getItemHeight () - 4) + 2;
	}

	/**
	 * After a 100,000-row list is filled, shown and scrolled to its middle and its end, GtkTreeView has measured
	 * or drawn only the rows it showed (and the one row fixed-height mode measures for the row height), not every
	 * row in the background; a full redraw draws only the rows on screen. The stock column's count after the same
	 * fill is reported for comparison.
	 */
	@Test
	public void aShownListMeasuresOnlyTheRowsOnScreen () {
		for (boolean virtual : new boolean [] {false, true}) {
			int [] setData = {0};
			List list = list (SWT.SINGLE | SWT.V_SCROLL | (virtual ? SWT.VIRTUAL : 0), true);
			fill (list, virtual, setData);
			paint (list);
			drainUntilIdle (1000);
			int visible = visibleRows (list);
			int filled = probed (list).cardinality ();
			assertTrue (filled <= 2 * visible + 8, "rows measured after the fill: " + filled + ", visible " + visible);
			list.setTopIndex (ROWS / 2);
			paint (list);
			drainUntilIdle (1000);
			list.setTopIndex (ROWS - 10);
			paint (list);
			drainUntilIdle (1000);
			int scrolled = probed (list).cardinality ();
			/* scrolling may animate through a few intermediate pages; never through the 100,000 rows */
			assertTrue (scrolled <= 1000, "rows measured after two scrolls: " + scrolled);
			int top = list.getTopIndex ();
			probed (list).clear ();
			paint (list);
			BitSet redrawn = probed (list);
			assertTrue (redrawn.cardinality () <= visible, "rows drawn by a full redraw: " + redrawn.cardinality () + ", visible at most " + visible);
			assertTrue (redrawn.isEmpty () || redrawn.nextSetBit (0) >= top - 1, "a redraw draws the rows from the top row " + top + " on: " + redrawn);
			if (virtual) assertTrue (setData [0] <= scrolled + visible, "SetData only for drawn rows: " + setData [0]);
			list.dispose ();

			List stock = list (SWT.SINGLE | SWT.V_SCROLL | (virtual ? SWT.VIRTUAL : 0), false);
			int [] stockSetData = {0};
			fill (stock, virtual, stockSetData);
			paint (stock);
			drainUntilIdle (1000);
			int stockFilled = probed (stock).cardinality ();
			stock.dispose ();
			System.out.println ("GTK_LIST_MEASURED_ROWS kind=" + (virtual ? "virtual" : "plain") + " rows=" + ROWS + " visible=" + visible
				+ " measured.afterFill=" + filled + " measured.afterTwoScrolls=" + scrolled + " measured.redraw=" + redrawn.cardinality ()
				+ " measured.setData=" + setData [0] + " stock.afterFillAndOneSecond=" + stockFilled + " stock.setData=" + stockSetData [0]);
		}
	}

	private static void fill (List list, boolean virtual, int [] setData) {
		if (virtual) {
			list.addListener (SWT.SetData, event -> {
				setData [0]++;
				list.setItem (event.index, "item " + event.index);
			});
			list.setItemCount (ROWS);
		} else {
			list.setItems (items (ROWS));
		}
	}

	/**
	 * computeSize answers the stock column's width (the widest row as the column's own renderer measures it, plus
	 * the row padding), once the stock column has measured every row; and a plain and a VIRTUAL list holding the
	 * same items answer the same, before and after edits. Only the width is compared with the stock column: its
	 * height is GtkTreeView's preferred height, which on GTK3 counts the rows validated when that size was last
	 * requested (a 1,000-row stock list answered 14 rows' height after the probe run), while the measured column
	 * answers every row at the height fixed-height mode measured.
	 */
	@Test
	public void computeSizeIsTheStockWidthAndEqualForPlainAndVirtual () {
		String [] ascii = new String [1000];
		for (int i = 0; i < ascii.length; i++) ascii [i] = (i % 11 == 0 ? "a wider row number " : "row ") + i;
		List stockAscii = list (SWT.SINGLE | SWT.V_SCROLL, false, false);
		stockAscii.setItems (ascii);
		paint (stockAscii);
		assertTrue (drainUntilIdle (10_000) >= 0, "the stock column measures every row");
		Point stockAsciiSize = stockAscii.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		stockAscii.dispose ();
		List measuredAscii = list (SWT.SINGLE | SWT.V_SCROLL, true, false);
		measuredAscii.setItems (ascii);
		Point measuredAsciiSize = measuredAscii.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		assertEquals (stockAsciiSize.x, measuredAsciiSize.x, "one script: the stock width");
		System.out.println ("GTK_LIST_COMPUTE_SIZE ascii stock=" + stockAsciiSize + " measured=" + measuredAsciiSize);
		measuredAscii.dispose ();
		String [] items = new String [2000];
		String [] samples = {"i", "MMMMMMMMMM", "a mixed Row 123", "\u05e9\u05dc\u05d5\u05dd world", "\u65e5\u672c\u8a9e\u306e\u884c", "e\u0301e\u0301", "", "tab\there", "x\0hidden tail that is not drawn"};
		for (int i = 0; i < items.length; i++) items [i] = samples [i % samples.length] + (i % 97 == 0 ? " wide wide wide wide " + i : " " + (i % 13));
		for (int style : new int [] {SWT.SINGLE | SWT.V_SCROLL, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL}) {
			List stock = list (style, false);
			stock.setItems (items);
			paint (stock);
			assertTrue (drainUntilIdle (10_000) >= 0, "the stock column measures every row");
			Point stockSize = stock.computeSize (SWT.DEFAULT, SWT.DEFAULT);
			stock.dispose ();
			List plain = list (style, true);
			List virtual = list (style | SWT.VIRTUAL, true);
			plain.setItems (items);
			virtual.setItems (items);
			Point plainSize = plain.computeSize (SWT.DEFAULT, SWT.DEFAULT);
			assertEquals (plainSize, virtual.computeSize (SWT.DEFAULT, SWT.DEFAULT), "plain and VIRTUAL");
			assertEquals (stockSize.x, plainSize.x, "the measured column answers the stock column's width");
			int drawnBefore = probed (plain).cardinality ();
			plain.computeSize (SWT.DEFAULT, SWT.DEFAULT);
			assertEquals (drawnBefore, probed (plain).cardinality (), "computeSize measures in the lane, not through GtkTreeView rows");
			for (List list : new List [] {plain, virtual}) {
				list.remove (0, 199);
				list.remove (new int [] {3, 4, 5, 400, 1700});
				list.setItem (10, "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM");
				list.add ("short", 0);
			}
			assertEquals (plain.computeSize (SWT.DEFAULT, SWT.DEFAULT), virtual.computeSize (SWT.DEFAULT, SWT.DEFAULT), "plain and VIRTUAL after edits");
			plain.setItem (11, "i");
			virtual.setItem (11, "i");
			Point narrower = plain.computeSize (SWT.DEFAULT, SWT.DEFAULT);
			assertEquals (narrower, virtual.computeSize (SWT.DEFAULT, SWT.DEFAULT));
			System.out.println ("GTK_LIST_COMPUTE_SIZE style=" + Integer.toHexString (style) + " stock=" + stockSize + " measured=" + plainSize + " afterEdits=" + narrower);
			plain.dispose ();
			virtual.dispose ();
		}
	}

	private static int laneMeasured (List list) {
		Object lane = get (list, "widths");
		try {
			java.lang.reflect.Method count = lane.getClass ().getDeclaredMethod ("count");
			java.lang.reflect.Method pending = lane.getClass ().getDeclaredMethod ("pendingCount");
			count.setAccessible (true);
			pending.setAccessible (true);
			return (Integer) count.invoke (lane) - (Integer) pending.invoke (lane);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	/**
	 * Intended difference from the stock column: the width is the widest current row, so it shrinks when that row
	 * is removed, shortened or replaced (as on win32). The stock GROW_ONLY column never shrinks.
	 */
	@Test
	public void theWidthShrinksWhenTheWidestRowLeaves () {
		for (int style : new int [] {SWT.SINGLE | SWT.V_SCROLL, SWT.MULTI | SWT.V_SCROLL | SWT.VIRTUAL, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL}) {
			List narrow = list (style, true, false);
			narrow.setItems ("short", "mid row");
			int narrowWidth = narrow.computeSize (SWT.DEFAULT, SWT.DEFAULT).x;
			List empty = list (style, true, false);
			int emptyWidth = empty.computeSize (SWT.DEFAULT, SWT.DEFAULT).x;
			List list = list (style, true, false);
			list.setItems ("short", "a row much wider than the other two rows", "mid row");
			int wide = list.computeSize (SWT.DEFAULT, SWT.DEFAULT).x;
			assertTrue (wide > narrowWidth, "the wide row widens the list: " + wide + " against " + narrowWidth);
			list.remove (1);
			assertEquals (narrowWidth, list.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "remove");
			list.setItem (0, "a row much wider than the other two rows");
			assertEquals (wide, list.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "a wider setItem");
			list.setItem (0, "short");
			assertEquals (narrowWidth, list.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "a shorter setItem");
			list.setItems ("a row much wider than the other two rows");
			list.setItems ("short", "mid row");
			assertEquals (narrowWidth, list.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "setItems");
			list.removeAll ();
			assertEquals (emptyWidth, list.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "removeAll");
			narrow.dispose ();
			empty.dispose ();
			list.dispose ();
		}
	}

	/**
	 * setSelection far away (deselect, the cursor's clamp and showSelection) goes to the row at once: a VIRTUAL
	 * list requests the rows at each destination, not the rows an animated scroll would pass.
	 */
	@Test
	public void setSelectionGoesToTheRowAtOnce () {
		for (int style : new int [] {SWT.SINGLE, SWT.MULTI}) {
			int [] setData = {0};
			List list = list (style | SWT.V_SCROLL | SWT.VIRTUAL, true, false);
			fill (list, true, setData);
			paint (list);
			drainUntilIdle (1000);
			int visible = visibleRows (list);
			int before = setData [0];
			for (int target : new int [] {ROWS / 2, ROWS - 3, 7}) {
				if (style == SWT.SINGLE) list.setSelection (target);
				else list.setSelection (new int [] {target, target + 1});
				paint (list);
				drainUntilIdle (1000);
				int top = list.getTopIndex ();
				assertTrue (top <= target && target < top + visible, "row " + target + " is shown, top " + top);
			}
			int requested = setData [0] - before;
			assertTrue (requested <= 3 * visible, "SetData for three destinations: " + requested + ", visible at most " + visible);
			list.dispose ();
		}
	}

	/**
	 * computeSize of a list longer than EXACT_WIDTH_ROWS measures one window of rows from the top row on, not every
	 * row: a refill costs a bounded measurement. A row beyond the window counts once it has been measured, here
	 * when the list scrolls to it; a plain and a VIRTUAL list holding the same items answer the same.
	 */
	@Test
	public void computeSizeOfALongListMeasuresOneWindow () {
		String [] items = items (ROWS);
		items [ROWS / 2] = "a row far wider than any other row of this list, beyond the first window";
		List plain = list (SWT.SINGLE | SWT.V_SCROLL, true, false);
		List virtual = list (SWT.SINGLE | SWT.V_SCROLL | SWT.VIRTUAL, true, false);
		for (List list : new List [] {plain, virtual}) list.setItems (items);
		int exact = (Integer) getStatic ("EXACT_WIDTH_ROWS");
		int first = plain.computeSize (SWT.DEFAULT, SWT.DEFAULT).x;
		assertEquals (first, virtual.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "plain and VIRTUAL");
		int measured = laneMeasured (plain);
		assertTrue (measured <= exact + visibleRows (plain), "rows measured by computeSize: " + measured);
		for (List list : new List [] {plain, virtual}) {
			list.setTopIndex (ROWS / 2 - 2);
			paint (list);
			drainUntilIdle (1000);
		}
		int scrolled = plain.computeSize (SWT.DEFAULT, SWT.DEFAULT).x;
		assertEquals (scrolled, virtual.computeSize (SWT.DEFAULT, SWT.DEFAULT).x, "plain and VIRTUAL after scrolling");
		assertTrue (scrolled > first, "the wide row counts once measured: " + scrolled + " against " + first);
		assertTrue (laneMeasured (plain) <= 2 * (exact + visibleRows (plain)), "rows measured: " + laneMeasured (plain));
		plain.dispose ();
		virtual.dispose ();
	}

	/* computeSize's height once GtkTreeView has validated the shown rows (its preferred height counts validated rows) */
	private int validatedHeight (List list) {
		paint (list);
		drainUntilIdle (2000);
		return list.computeSize (SWT.DEFAULT, SWT.DEFAULT).y;
	}

	/**
	 * An item with a line break keeps its taller row (fixed-height mode is left), and setItems or removeAll with
	 * single-line items return to fixed-height mode.
	 */
	@Test
	public void aLineBreakKeepsTheTallerRowUntilEveryRowIsReplaced () {
		List list = list (SWT.SINGLE | SWT.V_SCROLL, true);
		list.setItems ("one", "two", "three");
		int single = validatedHeight (list);
		assertFalse ((Boolean) get (list, "variableRows"));
		list.setItems ("one", "two\nlines", "three");
		assertTrue ((Boolean) get (list, "variableRows"));
		int multi = validatedHeight (list);
		assertTrue (multi > single, "the two-line row is taller: " + multi + " against " + single);
		list.setItems ("one", "two", "three");
		assertFalse ((Boolean) get (list, "variableRows"));
		assertEquals (single, validatedHeight (list));
		list.add ("x\0hidden\nbreak after the NUL is not stored");
		assertFalse ((Boolean) get (list, "variableRows"), "the native row ends at the NUL");
		list.add ("four\r\nlines");
		assertTrue ((Boolean) get (list, "variableRows"));
		list.removeAll ();
		assertFalse ((Boolean) get (list, "variableRows"));
		list.dispose ();
	}

	/**
	 * Report only: setItems / setItemCount, showing (first paint until the event loop is idle, at most 5 s), scrolling and
	 * computeSize times at 10,000, 100,000 and 1,000,000 rows, with the measured and the stock column, plus the
	 * native rows (one GtkListStore row per item in both modes) and the ints the width lane holds. With the measured
	 * column, computeSize is exact (every row measured once) up to EXACT_WIDTH_ROWS (4,096) rows; past that it measures
	 * one window of 4,096 rows from the top row on, so its 100,000- and 1,000,000-row times are the cost of that
	 * window and do not scale with the row count. The stock column's computeSize answers what GTK validated within
	 * 10 ms. The rows GtkTreeView measures are counted in aShownListMeasuresOnlyTheRowsOnScreen.
	 * It builds lists of up to 1,000,000 rows in twelve configurations and asserts nothing about time, so it runs
	 * only with <code>-Dswt.list.measure.report=true</code>.
	 */
	@Test
	public void reportFillSettleScrollAndSizeTimes () {
		assumeTrue (Boolean.getBoolean ("swt.list.measure.report"), "report only: run with -Dswt.list.measure.report=true");
		String [] largest = items (1_000_000);
		for (int count : new int [] {10_000, 100_000, 1_000_000}) {
			String [] items = count == largest.length ? largest : java.util.Arrays.copyOf (largest, count);
			for (boolean virtual : new boolean [] {false, true}) {
				for (boolean measured : new boolean [] {true, false}) {
					/* no probe here: its cell data function would slow the stock column's row measurement */
					List list = list (SWT.SINGLE | SWT.V_SCROLL | (virtual ? SWT.VIRTUAL : 0), measured, false);
					int [] setData = {0};
					if (virtual) {
						list.addListener (SWT.SetData, event -> {
							setData [0]++;
							list.setItem (event.index, "item " + event.index);
						});
					}
					long t0 = System.nanoTime ();
					if (virtual) list.setItemCount (count);
					else list.setItems (items);
					long fillMs = (System.nanoTime () - t0) / 1_000_000L;
					/* the first paint and every idle step after it, GtkTreeView's background row validation included */
					long t3 = System.nanoTime ();
					list.redraw ();
					list.update ();
					long settleMs = drainUntilIdle (5000) < 0 ? -1 : (System.nanoTime () - t3) / 1_000_000L;
					int [] tops = {count / 4, count / 2, 3 * count / 4, count - 1, 0};
					long t1 = System.nanoTime ();
					for (int top : tops) {
						list.setTopIndex (top);
						list.update ();
					}
					long scrollUs = (System.nanoTime () - t1) / 1000L / tops.length;
					drainUntilIdle (2000);
					long t2 = System.nanoTime ();
					Point point = list.computeSize (SWT.DEFAULT, SWT.DEFAULT);
					String size = (System.nanoTime () - t2) / 1_000_000L + "ms/" + point.x;
					Object lane = get (list, "widths");
					System.out.println ("GTK_LIST_TIMES mode=" + (measured ? "measured" : "stock") + " kind=" + (virtual ? "virtual" : "plain")
						+ " rows=" + count + " fillMs=" + fillMs + " showUntilIdleMs=" + (settleMs < 0 ? ">5000" : settleMs)
						+ " scrollUsPerJump=" + scrollUs + " computeSize=" + size
						+ " nativeRows=" + list.getItemCount () + " laneInts=" + laneInts (lane) + " setData=" + setData [0]);
					assertEquals (count, list.getItemCount ());
					list.dispose ();
				}
			}
		}
	}

	private static int laneInts (Object lane) {
		if (lane == null) return 0;
		int ints = 0;
		for (String name : new String [] {"widths", "measuredRows"}) {
			try {
				Field field = lane.getClass ().getDeclaredField (name);
				field.setAccessible (true);
				ints += ((int []) field.get (lane)).length;
			} catch (ReflectiveOperationException e) {
				throw new AssertionError (e);
			}
		}
		return ints;
	}
}
