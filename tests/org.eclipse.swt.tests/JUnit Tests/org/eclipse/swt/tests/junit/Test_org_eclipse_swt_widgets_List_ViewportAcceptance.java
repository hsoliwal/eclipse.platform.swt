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

import org.eclipse.swt.*;
import org.eclipse.swt.accessibility.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.ole.win32.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Acceptance tests of the win32 List viewport (docs/m3/LIST_MEDIATED_VIEWPORT.md): the native
 * list box holds at most one window of rows while the public API answers for every row. The
 * tests skip (never block) when the viewport is not active.
 */
public class Test_org_eclipse_swt_widgets_List_ViewportAcceptance {
	static final int MILLION = 1_000_000;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "the List viewport is win32 only");
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) shell.dispose ();
	}

	private List list (int style) {
		List list = new List (shell, style | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		Assumptions.assumeTrue (viewport (list) != null, "List viewport mediation is not active in this build");
		return list;
	}

	static Object viewport (List list) {
		try {
			Field field = List.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			return field.get (list);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	static int residentRows (List list) {
		try {
			Object viewport = viewport (list);
			Method method = viewport.getClass ().getDeclaredMethod ("residentCount");
			method.setAccessible (true);
			return (Integer) method.invoke (viewport);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	static String [] rows (int count) {
		String [] rows = new String [count];
		for (int i = 0; i < count; i++) rows [i] = "row " + i;
		return rows;
	}

	@Test
	public void millionItemsKeepOneWindowNative () {
		for (int style : new int [] {SWT.SINGLE, SWT.MULTI}) {
			List list = list (style);
			shell.open ();
			long start = System.nanoTime ();
			list.setItems (rows (MILLION));
			long setItems = System.nanoTime () - start;
			int page = list.getClientArea ().height / list.getItemHeight ();
			int bound = 2 * (page + 2) + 4 * Math.max (16, 2 * page);
			assertEquals (MILLION, list.getItemCount ());
			assertTrue (residentRows (list) <= bound, "native rows " + residentRows (list) + " > " + bound);
			assertEquals (MILLION - 1, list.getVerticalBar ().getMaximum ());
			start = System.nanoTime ();
			list.setTopIndex (MILLION - 1);
			assertEquals (MILLION - page, list.getTopIndex ());
			assertEquals ("row " + (MILLION - 1), list.getItem (MILLION - 1));
			list.setSelection (500_000);
			assertEquals (500_000, list.getSelectionIndex ());
			assertTrue (list.getTopIndex () <= 500_000 && 500_000 < list.getTopIndex () + page);
			list.add ("inserted", 10);
			list.remove (20, 29);
			assertEquals (MILLION - 9, list.getItemCount ());
			assertEquals ("inserted", list.getItem (10));
			assertEquals (500_000 - 9, list.getSelectionIndex ());
			long operations = System.nanoTime () - start;
			assertTrue (residentRows (list) <= bound, "native rows " + residentRows (list) + " > " + bound);
			assertTrue (operations < 2_000_000_000L, "top, selection, insert and remove at 1,000,000 rows took " + operations / 1_000_000 + " ms");
			assertTrue (setItems < 20_000_000_000L, "setItems (1,000,000) took " + setItems / 1_000_000 + " ms");
			if ((style & SWT.MULTI) != 0) {
				list.selectAll ();
				assertEquals (MILLION - 9, list.getSelectionCount ());
				list.deselect (0, 499_999);
				assertEquals (MILLION - 9 - 500_000, list.getSelectionCount ());
			}
			list.dispose ();
		}
	}

	@Test
	public void scrollingMovesOnlyTheChangedRows () {
		List list = list (SWT.MULTI);
		shell.open ();
		list.setItems (rows (100_000));
		list.setSelection (new int [] {5, 50_000, 99_999});
		for (int top = 0; top < 100_000; top += 997) {
			list.setTopIndex (top);
			assertEquals (Math.min (top, 100_000 - list.getClientArea ().height / list.getItemHeight ()), list.getTopIndex ());
		}
		assertArrayEquals (new int [] {5, 50_000, 99_999}, list.getSelectionIndices ());
		list.setTopIndex (50_000 - 3);
		assertTrue (list.isSelected (50_000));
		assertEquals ("row 50000", list.getItem (50_000));
	}

	/* window messages through the list's own window procedure (win32 only, reflective so the test compiles everywhere) */
	static long send (List list, String message, long wParam, long lParam) {
		try {
			Class<?> os = Class.forName ("org.eclipse.swt.internal.win32.OS");
			int msg = os.getField (message).getInt (null);
			long handle = List.class.getField ("handle").getLong (list);
			return (Long) os.getMethod ("SendMessage", long.class, int.class, long.class, long.class).invoke (null, handle, msg, wParam, lParam);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	static int constant (String name) {
		try {
			return Class.forName ("org.eclipse.swt.internal.win32.OS").getField (name).getInt (null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	@Test
	public void scrollBarWheelAndKeysMoveTheLogicalTopLikeTheListBox () {
		List list = list (SWT.SINGLE);
		shell.open ();
		list.setItems (rows (100_000));
		int page = list.getClientArea ().height / list.getItemHeight ();
		list.setTopIndex (50_000);
		send (list, "WM_VSCROLL", constant ("SB_LINEDOWN"), 0);
		assertEquals (50_001, list.getTopIndex ());
		send (list, "WM_VSCROLL", constant ("SB_PAGEDOWN"), 0);
		assertEquals (50_001 + page - 1, list.getTopIndex ());
		send (list, "WM_VSCROLL", constant ("SB_BOTTOM"), 0);
		assertEquals (100_000 - page, list.getTopIndex ());
		assertEquals (100_000 - page, list.getVerticalBar ().getSelection ());
		send (list, "WM_VSCROLL", constant ("SB_TOP"), 0);
		assertEquals (0, list.getTopIndex ());
		list.setTopIndex (70_000);
		int lines = 3;
		send (list, "WM_MOUSEWHEEL", (long) (-120) << 16, 0);
		int wheeled = list.getTopIndex () - 70_000;
		assertTrue (wheeled > 0 && wheeled <= Math.max (lines, page), "wheel moved the top by " + wheeled);
		list.setSelection (80_000);
		send (list, "WM_KEYDOWN", constant ("VK_END"), 0);
		assertEquals (99_999, list.getSelectionIndex ());
		assertEquals (100_000 - page, list.getTopIndex ());
		send (list, "WM_KEYDOWN", constant ("VK_HOME"), 0);
		assertEquals (0, list.getSelectionIndex ());
		assertEquals (0, list.getTopIndex ());
		/* Down from the last fully visible row scrolls one row, natively inside the window */
		list.setSelection (page - 1);
		send (list, "WM_KEYDOWN", constant ("VK_DOWN"), 0);
		assertEquals (page, list.getSelectionIndex ());
		assertEquals (1, list.getTopIndex ());
		/* Next: caret + (page - 1), scrolled just into view (ListRulesOracle: caret 110, top 100 -> 134, top 110) */
		list.setSelection (110);
		list.setTopIndex (100);
		send (list, "WM_KEYDOWN", constant ("VK_NEXT"), 0);
		assertEquals (110 + page - 1, list.getSelectionIndex ());
		assertEquals (110, list.getTopIndex ());
		send (list, "WM_KEYDOWN", constant ("VK_PRIOR"), 0);
		assertEquals (110, list.getSelectionIndex ());
		assertEquals (110, list.getTopIndex ());
		/* Down from a caret far outside the window: the next row, scrolled into view */
		list.setSelection (5_000);
		list.setTopIndex (50_000);
		send (list, "WM_KEYDOWN", constant ("VK_DOWN"), 0);
		assertEquals (5_001, list.getSelectionIndex ());
		assertEquals (5_001, list.getFocusIndex ());
		assertEquals (5_001, list.getTopIndex ());
		/* type-ahead searches the logical rows, not only the resident ones */
		list.add ("zeta", 99_000);
		send (list, "WM_CHAR", 'z', 0);
		assertEquals (99_000, list.getSelectionIndex ());
		assertTrue (list.getTopIndex () <= 99_000 && 99_000 < list.getTopIndex () + page);
	}

	/*
	 * An MSAA client's calls into SWT's IAccessible implementation (the Accessible of the list), with
	 * VARIANTs in native memory: what a screen reader gets after AccessibleObjectFromWindow.
	 */
	static final class Msaa {
		final Accessible accessible;
		final long memory = OS.HeapAlloc (OS.GetProcessHeap (), OS.HEAP_ZERO_MEMORY, 64);

		Msaa (List list) {
			OS.SendMessage (list.handle, OS.WM_GETOBJECT, 0, OS.OBJID_CLIENT);
			accessible = list.getAccessible ();
		}

		int invoke (String name, Class<?> [] types, Object... args) {
			try {
				Method method = Accessible.class.getDeclaredMethod (name, types);
				method.setAccessible (true);
				return (Integer) method.invoke (accessible, args);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError (e);
			}
		}

		/* VARIANT VT_I4 with an MSAA child id (rows are 1-based) at memory; out values at memory + 32 */
		void child (int osChild) {
			OS.MoveMemory (memory, new short [] {(short) COM.VT_I4}, 2);
			OS.MoveMemory (memory + 8, new int [] {osChild}, 4);
		}

		int childCount () {
			invoke ("get_accChildCount", new Class<?> [] {long.class}, memory + 32);
			int [] count = new int [1];
			OS.MoveMemory (count, memory + 32, 4);
			return count [0];
		}

		String name (int row) {
			child (row + 1);
			invoke ("get_accName", new Class<?> [] {long.class, long.class}, memory, memory + 32);
			long [] bstr = new long [1];
			OS.MoveMemory (bstr, memory + 32, C.PTR_SIZEOF);
			if (bstr [0] == 0) return null;
			int size = COM.SysStringByteLen (bstr [0]);
			char [] chars = new char [size / 2];
			OS.MoveMemory (chars, bstr [0], size);
			COM.SysFreeString (bstr [0]);
			return new String (chars);
		}

		int variantOut (String method, int row) {
			child (row + 1);
			invoke (method, new Class<?> [] {long.class, long.class}, memory, memory + 32);
			int [] value = new int [1];
			OS.MoveMemory (value, memory + 40, 4);
			return value [0];
		}

		/* accLocation of a row: left, top, width, height on the screen */
		int [] location (int row) {
			child (row + 1);
			invoke ("accLocation", new Class<?> [] {long.class, long.class, long.class, long.class, long.class}, memory + 32, memory + 36, memory + 40,
				memory + 44, memory);
			int [] bounds = new int [4];
			OS.MoveMemory (bounds, memory + 32, 16);
			return bounds;
		}

		/* accHitTest at a screen point: "code vt value" (VT_I4 values are MSAA child ids: 0 the list itself, rows 1-based) */
		String hitTest (int x, int y) {
			int code = invoke ("accHitTest", new Class<?> [] {int.class, int.class, long.class}, x, y, memory + 32);
			return code + " " + variant (memory + 32);
		}

		/* get_accSelection: "code vt value" */
		String selection () {
			int code = invoke ("get_accSelection", new Class<?> [] {long.class}, memory + 32);
			return code + " " + variant (memory + 32);
		}

		/* the first rows of the IEnumVARIANT enumeration of the children, as MSAA child ids */
		int [] children (int count) {
			long variants = OS.HeapAlloc (OS.GetProcessHeap (), OS.HEAP_ZERO_MEMORY, count * VARIANT.sizeof);
			try {
				invoke ("Reset", new Class<?> [0]);
				invoke ("Next", new Class<?> [] {int.class, long.class, long.class}, count, variants, memory + 48);
				int [] fetched = new int [1];
				OS.MoveMemory (fetched, memory + 48, 4);
				int [] ids = new int [fetched [0]];
				for (int i = 0; i < ids.length; i++) {
					int [] value = new int [1];
					OS.MoveMemory (value, variants + (long) i * VARIANT.sizeof + 8, 4);
					ids [i] = value [0];
				}
				return ids;
			} finally {
				OS.HeapFree (OS.GetProcessHeap (), 0, variants);
			}
		}

		static String variant (long address) {
			short [] type = new short [1];
			int [] value = new int [1];
			OS.MoveMemory (type, address, 2);
			OS.MoveMemory (value, address + 8, 4);
			return type [0] + " " + (type [0] == COM.VT_I4 ? value [0] : 0);
		}

		/* the focused child as a 0-based row, or -1 */
		int focus () {
			invoke ("get_accFocus", new Class<?> [] {long.class}, memory + 32);
			short [] type = new short [1];
			int [] value = new int [1];
			OS.MoveMemory (type, memory + 32, 2);
			OS.MoveMemory (value, memory + 40, 4);
			return type [0] == COM.VT_I4 ? value [0] - 1 : -1;
		}
	}

	@Test
	public void accessibilityAnswersForLogicalRows () {
		List list = list (SWT.MULTI);
		shell.open ();
		list.setItems (rows (100_000));
		list.setSelection (new int [] {50_010, 99_998});
		list.setTopIndex (50_000);
		list.setFocus ();
		Msaa msaa = new Msaa (list);
		assertEquals (100_000, msaa.childCount ());
		assertEquals ("row 99999", msaa.name (99_999));
		assertEquals ("row 50005", msaa.name (50_005));
		assertEquals ("row 0", msaa.name (0));
		assertEquals (COM.ROLE_SYSTEM_LISTITEM, msaa.variantOut ("get_accRole", 99_999));
		int selected = msaa.variantOut ("get_accState", 99_998), visible = msaa.variantOut ("get_accState", 50_005), far = msaa.variantOut ("get_accState", 3);
		assertTrue ((selected & COM.STATE_SYSTEM_SELECTED) != 0, "state of a selected off-screen row: " + Integer.toHexString (selected));
		assertTrue ((selected & COM.STATE_SYSTEM_OFFSCREEN) != 0, "state of a selected off-screen row: " + Integer.toHexString (selected));
		assertEquals (0, visible & COM.STATE_SYSTEM_OFFSCREEN, "a visible row is on screen");
		assertTrue ((far & COM.STATE_SYSTEM_OFFSCREEN) != 0);
		if (list.isFocusControl ()) assertEquals (list.getFocusIndex (), msaa.focus ());
	}

	/*
	 * Bounds, hit tests, selection and the children enumeration answer for logical rows: a visible row where the
	 * list box paints it, a row far below the window where it would be, points in a row, on the border, outside the
	 * list and below the last row; one, several and no selected rows; and the first children of the enumeration.
	 * A key the list box handles afterwards moves the accessible focus with the caret.
	 */
	@Test
	public void accessibilityLocatesHitTestsAndEnumeratesLogicalRows () {
		List list = list (SWT.MULTI);
		shell.open ();
		list.setItems (rows (100_000));
		list.setTopIndex (50_000);
		list.setSelection (50_003);
		list.setFocus ();
		Msaa msaa = new Msaa (list);
		int height = list.getItemHeight (), width = list.getClientArea ().width;
		org.eclipse.swt.graphics.Point origin = list.toDisplay (0, 0);
		assertArrayEquals (new int [] {origin.x, origin.y + 3 * height, width, height}, msaa.location (50_003));
		assertArrayEquals (new int [] {origin.x, origin.y + 49_000 * height, width, height}, msaa.location (99_000));
		assertEquals ("0 " + COM.VT_I4 + " " + (50_003 + 1), msaa.hitTest (origin.x + 10, origin.y + 3 * height + height / 2));
		org.eclipse.swt.graphics.Point corner = list.toDisplay (-1, -1);
		assertEquals ("0 " + COM.VT_I4 + " 0", msaa.hitTest (corner.x, corner.y), "the border is the list itself");
		org.eclipse.swt.graphics.Point outside = shell.toDisplay (1, 1);
		assertEquals ("1 " + COM.VT_EMPTY + " 0", msaa.hitTest (outside.x, outside.y), "a point outside the list");
		assertEquals ("0 " + COM.VT_I4 + " " + (50_003 + 1), msaa.selection ());
		list.setSelection (new int [] {10, 99_999});
		assertTrue (msaa.selection ().startsWith ("0 "), "several selected rows: " + msaa.selection ());
		list.deselectAll ();
		assertEquals ("1 " + COM.VT_EMPTY + " 0", msaa.selection ());
		assertArrayEquals (new int [] {1, 2, 3}, msaa.children (3));
		send (list, "WM_KEYDOWN", constant ("VK_DOWN"), 0);
		if (list.isFocusControl ()) assertEquals (list.getFocusIndex (), msaa.focus ());
		list.setItems (rows (5));
		assertEquals ("0 " + COM.VT_I4 + " 0", msaa.hitTest (origin.x + 10, origin.y + 10 * height + height / 2), "below the last row");
	}

	/*
	 * Rule 24, viewport deltas: a resize is a delta of the viewport. Over a sweep of 50 heights (1 px steps up and down,
	 * then large jumps) with the top row far inside 100,000 rows, the window is never replaced whole and the native row
	 * operations of each resize are exactly the rows that enter or leave the resident window.
	 */
	@Test
	public void resizeSweepMovesOnlyTheRowsEnteringOrLeaving () throws ReflectiveOperationException {
		shell.setBounds (20, 20, 330, 1000);
		List list = list (SWT.MULTI);
		shell.open ();
		list.setItems (rows (100_000));
		list.setTopIndex (50_000);
		settleUi ();
		int [] heights = new int [50];
		for (int i = 0; i < 20; i++) heights [i] = 380 + i;
		for (int i = 20; i < 40; i++) heights [i] = 399 - (i - 20);
		int [] jumps = {120, 860, 64, 700, 381, 950, 90, 512, 300, 777};
		System.arraycopy (jumps, 0, heights, 40, jumps.length);
		Method ops = List.class.getDeclaredMethod ("m3NativeRowOps"), replans = List.class.getDeclaredMethod ("m3WindowReplans");
		ops.setAccessible (true);
		replans.setAccessible (true);
		for (int height : heights) {
			int [] before = window (list);
			long opsBefore = (Long) ops.invoke (list), replansBefore = (Long) replans.invoke (list);
			list.setSize (300, height);
			settleUi ();
			int [] after = window (list);
			int entering = Math.max (0, before [0] - after [0]) + Math.max (0, after [1] - before [1]);
			int leaving = Math.max (0, after [0] - before [0]) + Math.max (0, before [1] - after [1]);
			assertEquals (replansBefore, replans.invoke (list), "the window was replaced whole at height " + height);
			assertEquals (entering + leaving, (Long) ops.invoke (list) - opsBefore, "native row operations at height " + height + ", window "
				+ before [0] + ".." + before [1] + " -> " + after [0] + ".." + after [1]);
			assertTrue (after [0] <= list.getTopIndex () && list.getTopIndex () < after [1], "the top row is resident at height " + height);
		}
	}

	/* the resident window [first, end) of a viewport list */
	static int [] window (List list) throws ReflectiveOperationException {
		Object viewport = viewport (list);
		Field planField = viewport.getClass ().getDeclaredField ("plan");
		planField.setAccessible (true);
		Object plan = planField.get (viewport);
		Method first = plan.getClass ().getDeclaredMethod ("first"), end = plan.getClass ().getDeclaredMethod ("end");
		first.setAccessible (true);
		end.setAccessible (true);
		return new int [] {(Integer) first.invoke (plan), (Integer) end.invoke (plan)};
	}

	void settleUi () {
		for (int i = 0; i < 3; i++) {
			while (display.readAndDispatch ()) {
				/* drain */
			}
		}
	}

	/*
	 * The model seam (List.m3Splice, leaf 19): one edit run over the lanes. A run outside the resident window changes
	 * the rows, the bar and the top row's index (the top row stays the same row) with zero native row operations; a
	 * run inside it touches O(changed) native rows; a removal at or before a set anchor clears it (two operations).
	 * Every run answers like the same edits made through the item API on a second list.
	 */
	@Test
	public void spliceOutsideTheWindowTouchesNoNativeRow () throws ReflectiveOperationException {
		List list = list (SWT.MULTI), stock = list (SWT.MULTI);
		shell.open ();
		list.setItems (rows (100_000));
		stock.setItems (rows (100_000));
		list.setTopIndex (50_000);
		stock.setTopIndex (50_000);
		Method splice = List.class.getDeclaredMethod ("m3Splice", int.class, int.class, String [].class);
		Method ops = List.class.getDeclaredMethod ("m3NativeRowOps");
		splice.setAccessible (true);
		ops.setAccessible (true);
		String [] thousand = new String [1000];
		for (int i = 0; i < 1000; i++) thousand [i] = "new " + i;
		long before = (Long) ops.invoke (list);
		/* above the window: 1,000 rows in at 10, then 500 out at 20; below it: 2,000 out and 1,000 in at 90,000 */
		splice.invoke (list, 10, 0, thousand);
		itemApi (stock, 10, 0, thousand);
		assertEquals (before, ops.invoke (list), "native row operations of an insert above the window");
		assertSameList (stock, list);
		splice.invoke (list, 20, 500, new String [0]);
		itemApi (stock, 20, 500, new String [0]);
		assertEquals (before, ops.invoke (list), "native row operations of a removal above the window");
		assertSameList (stock, list);
		splice.invoke (list, 90_000, 2_000, thousand);
		itemApi (stock, 90_000, 2_000, thousand);
		assertEquals (before, ops.invoke (list), "native row operations of a run below the window");
		assertSameList (stock, list);
		/* with a selection and its anchor: a removal above clears the anchor (at most two operations) */
		list.setSelection (new int [] {7, 50_503, 99_990});
		stock.setSelection (new int [] {7, 50_503, 99_990});
		splice.invoke (list, 30, 10, new String [] {"a", "b"});
		itemApi (stock, 30, 10, new String [] {"a", "b"});
		long cleared = (Long) ops.invoke (list) - before;
		assertTrue (cleared <= 2, "native row operations of a removal above the window with an anchor: " + cleared);
		assertSameList (stock, list);
		/* inside the window: three rows replaced by two */
		before = (Long) ops.invoke (list);
		splice.invoke (list, list.getTopIndex () + 2, 3, new String [] {"in 1", "in 2"});
		itemApi (stock, stock.getTopIndex () + 2, 3, new String [] {"in 1", "in 2"});
		long inside = (Long) ops.invoke (list) - before;
		assertTrue (inside > 0 && inside <= 7, "native row operations of a run inside the window: " + inside);
		assertSameList (stock, list);
	}

	/* the same edits through the item API, with the top row kept on the same row, as the seam keeps it */
	static void itemApi (List list, int at, int removed, String [] inserted) {
		int top = list.getTopIndex ();
		if (removed > 0) list.remove (at, at + removed - 1);
		for (int i = 0; i < inserted.length; i++) list.add (inserted [i], at + i);
		if (at + removed <= top) list.setTopIndex (top - removed + inserted.length);
	}

	static void assertSameList (List expected, List actual) {
		assertEquals (expected.getItemCount (), actual.getItemCount ());
		assertEquals (expected.getTopIndex (), actual.getTopIndex ());
		assertEquals (expected.getFocusIndex (), actual.getFocusIndex ());
		assertArrayEquals (expected.getSelectionIndices (), actual.getSelectionIndices ());
		int count = actual.getItemCount (), top = actual.getTopIndex ();
		for (int row : new int [] {0, 9, 10, 1_009, 1_010, 20, 519, 29, 30, 31, 32, count / 2, 89_999, 90_000, 91_000, count - 1, top, top + 1, top + 2, top + 3, top + 4, top + 5}) {
			int r = Math.max (0, Math.min (count - 1, row));
			assertEquals (expected.getItem (r), actual.getItem (r), "row " + r);
		}
		assertEquals (expected.getVerticalBar ().getSelection (), actual.getVerticalBar ().getSelection ());
		assertEquals (expected.getVerticalBar ().getMaximum (), actual.getVerticalBar ().getMaximum ());
	}

	@Test
	public void emptyAndTinyListsAnswerLikeTheListBox () {
		List list = list (SWT.SINGLE);
		assertEquals (-1, list.getFocusIndex ());
		assertEquals (0, list.getTopIndex ());
		list.setTopIndex (5);
		assertEquals (0, list.getTopIndex ());
		list.add ("a");
		assertEquals (0, list.getFocusIndex ());
		list.removeAll ();
		assertEquals (-1, list.getFocusIndex ());
		assertEquals (0, list.getVerticalBar ().getMaximum ());
	}
}
