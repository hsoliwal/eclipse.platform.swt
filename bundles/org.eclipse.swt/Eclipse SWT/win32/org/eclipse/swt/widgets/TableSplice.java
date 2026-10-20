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

import java.util.Arrays;

import org.eclipse.swt.internal.SwtLog;
import org.eclipse.swt.internal.win32.*;

/**
 * The splice seam of a plain Table in the viewport mode (enhanced API leaf 19, step 3; Table leaf T-Table-O2 of
 * docs/m3/TABLE_MEDIATED_VIEWPORT.md) and the count of the native operations of every win32 Table.
 * <p>
 * <code>splice (at, removed, inserted)</code> replaces the rows <code>[at, at + removed)</code> by
 * <code>inserted</code> empty rows. The lanes, the item array and the facades move once, and the owner-data ListView
 * gets one count change (<code>LVM_SETITEMCOUNT</code> with <code>LVSICF_NOINVALIDATEALL | LVSICF_NOSCROLL</code>)
 * instead of one <code>LVM_DELETEITEM</code> or <code>LVM_INSERTITEM</code> per row. The result is that of the same
 * edits through the item API (<code>remove (at, at + removed - 1)</code>, then
 * <code>new TableItem (table, SWT.NONE, at + k)</code>) followed by <code>setTopIndex</code> of the old top row, or of
 * the first row after it that survives, as <code>M3Model.apply</code> makes them:
 * </p>
 * <ul>
 * <li>A count change does not move the selection, the focus and the selection mark of an owner-data ListView
 * (evidence/OwnerDataOracle, case A); row messages move them. They are moved here by the rules of the row messages,
 * measured on native items (evidence/SpliceProbe, RULES: 400 scenarios, no mismatch on the official 3.135.0 jar): a
 * row before the run keeps its state and a row after it moves by <code>inserted - removed</code>; a removed row loses
 * its selection; a removed focus row passes the focus to the row now at <code>at</code>, or to the new last row, and
 * the inserts at <code>at</code> move it on; the selection mark follows the focus rule.</li>
 * <li>The top row stays the same row. With the native redraw off, the count change and an <code>LVM_SCROLL</code> by
 * the rows inserted or removed above the top paint nothing (SpliceProbe, SCROLL T9; T10 for focus and mark outside the
 * visible rows), so a run outside the visible rows changes only the scroll bar.</li>
 * <li>A run that reaches the visible rows invalidates them from the first changed row down: O(visible rows).</li>
 * </ul>
 * <p>
 * Moving the selection costs one state message per selected row at or after the run. When more rows are selected
 * there than the run has rows, the splice declines and the caller keeps the item API, whose row messages move the
 * selection natively; so does an empty Table or a splice that leaves no row (<code>removeAll</code> semantics).
 * </p>
 * <p>
 * Native operations: <code>count</code> runs for every message that reaches the native ListView
 * (<code>Table.callWindowProc</code>). Row operations are <code>LVM_INSERTITEM</code>, <code>LVM_DELETEITEM</code>,
 * <code>LVM_DELETEALLITEMS</code>, <code>LVM_REDRAWITEMS</code>, <code>LVM_SETITEM</code>, <code>LVM_SETITEMTEXT</code>,
 * an <code>LVM_SETITEMCOUNT</code> without <code>LVSICF_NOINVALIDATEALL</code> and the rows the splice invalidates;
 * state operations are <code>LVM_SETITEMSTATE</code> and <code>LVM_SETSELECTIONMARK</code>; whole-list rebuilds
 * (<code>m3FullRebuilds</code>, brief rule 24) are <code>LVM_DELETEALLITEMS</code> and a count change without
 * <code>LVSICF_NOINVALIDATEALL</code>, also counted as row operations. Row invalidations made by
 * <code>InvalidateRect</code> elsewhere are not messages; tests see them as paints.
 * </p>
 */
final class TableSplice {
	static final int LVM_GETSELECTIONMARK = OS.LVM_FIRST + 66, LVM_SETITEMTEXT = OS.LVM_FIRST + 116;
	/* more selected rows after the run than this beyond its length: the item API moves them natively */
	static final int SELECTION_SLACK = 64;
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.widgets.Table");

	private TableSplice () {
	}

	/** Counts a message that reaches the native ListView (Table.callWindowProc). */
	static void count (Table table, int msg, long lParam) {
		switch (msg) {
			case OS.LVM_INSERTITEM, OS.LVM_DELETEITEM, OS.LVM_REDRAWITEMS, OS.LVM_SETITEM, LVM_SETITEMTEXT -> table.m3RowOps++;
			case OS.LVM_DELETEALLITEMS -> {
				table.m3RowOps++;
				table.m3FullRebuilds++;
			}
			case OS.LVM_SETITEMCOUNT -> {
				if ((lParam & OS.LVSICF_NOINVALIDATEALL) == 0) {
					table.m3RowOps++;
					table.m3FullRebuilds++;
				}
			}
			case OS.LVM_SETITEMSTATE, OS.LVM_SETSELECTIONMARK -> table.m3StateOps++;
			default -> {
			}
		}
	}

	/** The index a row of the old numbering has after the splice; for a removed row the rule of the focus. */
	static int moved (int row, int count, int at, int removed, int inserted) {
		if (row < at) return row;
		if (row >= at + removed) return row - removed + inserted;
		int left = count - removed;
		int next = at < left ? at : left - 1;
		return next >= at ? next + inserted : next;
	}

	/**
	 * Replaces the rows [at, at + removed) of a mediated Table by inserted empty rows. The caller validated the run (M3Patch.apply: inside the
	 * rows, not empty).
	 * @return false when the caller is to make the edit through the item API
	 */
	static boolean splice (Table table, TableViewport viewport, int at, int removed, int inserted) {
		viewport.flush ();
		long hwnd = table.handle;
		int count = (int) OS.SendMessage (hwnd, OS.LVM_GETITEMCOUNT, 0, 0);
		int newCount = count - removed + inserted;
		if (count == 0 || newCount == 0) return false;
		int selectedAfter = selectedFrom (viewport, hwnd, at, removed + inserted + SELECTION_SLACK);
		if (selectedAfter < 0) return false;
		int top = Math.max (0, (int) OS.SendMessage (hwnd, OS.LVM_GETTOPINDEX, 0, 0));
		int focus = (int) OS.SendMessage (hwnd, OS.LVM_GETNEXTITEM, -1, OS.LVNI_FOCUSED);
		int mark = (int) OS.SendMessage (hwnd, LVM_GETSELECTIONMARK, 0, 0);
		table.setDeferResize (true);
		moveRows (table, viewport, count, at, removed, inserted);
		boolean redraw = table.getDrawing () && OS.IsWindowVisible (hwnd);
		boolean oldSelect = table.ignoreSelect, oldShrink = table.ignoreShrink;
		table.ignoreSelect = table.ignoreShrink = true;
		int newTop;
		try {
			if (redraw) OS.SendMessage (hwnd, OS.WM_SETREDRAW, 0, 0);
			OS.SendMessage (hwnd, OS.LVM_SETITEMCOUNT, newCount, OS.LVSICF_NOINVALIDATEALL | OS.LVSICF_NOSCROLL);
			moveSelection (hwnd, viewport.spliceSelection, selectedAfter, newCount, at, removed, inserted);
			setFocus (hwnd, focus < 0 ? -1 : moved (focus, count, at, removed, inserted));
			setMark (hwnd, mark < 0 ? -1 : moved (mark, count, at, removed, inserted));
			newTop = anchorTop (table, top, count, at, removed, inserted);
		} finally {
			if (redraw) OS.SendMessage (hwnd, OS.WM_SETREDRAW, 1, 0);
			table.ignoreSelect = oldSelect;
			table.ignoreShrink = oldShrink;
		}
		if (redraw) invalidateChanged (table, top, newTop, newCount, at, removed, inserted);
		table.setDeferResize (false);
		table.m3StructureChanged ();
		if (LOG.isTraceEnabled ()) {
			final int shownTop = newTop;
			LOG.trace (() -> "Table splice at " + at + ": -" + removed + " +" + inserted + ", rows " + count + " -> " + newCount + ", top " + top + " -> " + shownTop
					+ ", " + selectedAfter + " selected rows moved");
		}
		return true;
	}

	/* the selected rows at or after at, into viewport.spliceSelection; -1 when there are more than limit */
	private static int selectedFrom (TableViewport viewport, long hwnd, int at, int limit) {
		int [] selected = viewport.spliceSelection;
		int n = 0;
		int row = at - 1;
		while ((row = (int) OS.SendMessage (hwnd, OS.LVM_GETNEXTITEM, row, OS.LVNI_SELECTED)) != -1) {
			if (n == limit) return -1;
			if (n == selected.length) selected = viewport.spliceSelection = Arrays.copyOf (selected, Math.min (limit, Math.max (16, n * 2)));
			selected [n++] = row;
		}
		return n;
	}

	/* the Java side: facades of the removed rows released, the item array and the lanes moved once */
	private static void moveRows (Table table, TableViewport viewport, int count, int at, int removed, int inserted) {
		for (int row = at; row < at + removed; row++) {
			TableItem item = viewport.liveFacade (row);
			if (item != null) item.release (false);
		}
		int newCount = count - removed + inserted;
		TableItem [] items = table.items;
		if (newCount > items.length) items = table.items = Arrays.copyOf (items, VirtualGrowth.capacity (items.length, newCount, 8));
		System.arraycopy (items, at + removed, items, at + inserted, count - at - removed);
		Arrays.fill (items, at, at + inserted, null);
		if (newCount < count) Arrays.fill (items, newCount, count, null);
		if (removed > 0) viewport.remove (at, removed);
		if (inserted > 0) viewport.insert (at, inserted);
	}

	/*
	 * After the count change the native selection is the old one by index, cut at the new count. The rows after the
	 * run move by inserted - removed; the removed rows lose theirs. Both lists are ascending: one merge.
	 */
	private static void moveSelection (long hwnd, int [] selected, int n, int newCount, int at, int removed, int inserted) {
		if (n == 0) return;
		int delta = inserted - removed;
		int i = 0, j = 0;
		/* old: selected [i] (as now selected natively); new: selected [j] + delta for the rows after the run */
		while (j < n && selected [j] < at + removed) j++;
		while (i < n || j < n) {
			int old = i < n ? selected [i] : Integer.MAX_VALUE;
			int next = j < n ? selected [j] + delta : Integer.MAX_VALUE;
			if (old == next) {
				i++;
				j++;
			} else if (old < next) {
				if (old < newCount) setSelected (hwnd, old, false);
				i++;
			} else {
				setSelected (hwnd, next, true);
				j++;
			}
		}
	}

	private static void setSelected (long hwnd, int row, boolean select) {
		LVITEM lvItem = new LVITEM ();
		lvItem.stateMask = OS.LVIS_SELECTED;
		lvItem.state = select ? OS.LVIS_SELECTED : 0;
		OS.SendMessage (hwnd, OS.LVM_SETITEMSTATE, row, lvItem);
	}

	/* the focus where the row messages put it, when the count change left it elsewhere (setting it also moves the mark) */
	private static void setFocus (long hwnd, int row) {
		if (row < 0 || row == (int) OS.SendMessage (hwnd, OS.LVM_GETNEXTITEM, -1, OS.LVNI_FOCUSED)) return;
		LVITEM lvItem = new LVITEM ();
		lvItem.state = lvItem.stateMask = OS.LVIS_FOCUSED;
		OS.SendMessage (hwnd, OS.LVM_SETITEMSTATE, row, lvItem);
	}

	/* the selection mark where the row messages put it, when it is elsewhere now */
	private static void setMark (long hwnd, int row) {
		if (row != (int) OS.SendMessage (hwnd, LVM_GETSELECTIONMARK, 0, 0)) OS.SendMessage (hwnd, OS.LVM_SETSELECTIONMARK, 0, row);
	}

	/* the old top row (or the first row after it that survives) on top again: one LVM_SCROLL while the redraw is off */
	private static int anchorTop (Table table, int top, int count, int at, int removed, int inserted) {
		long hwnd = table.handle;
		int target = top < at ? top : top >= at + removed ? top - removed + inserted : at + inserted;
		int current = Math.max (0, (int) OS.SendMessage (hwnd, OS.LVM_GETTOPINDEX, 0, 0));
		if (target != current) OS.SendMessage (hwnd, OS.LVM_SCROLL, 0, (target - current) * table.getItemHeightInPixels ());
		return Math.max (0, (int) OS.SendMessage (hwnd, OS.LVM_GETTOPINDEX, 0, 0));
	}

	/* the old row a row of the new numbering shows, or -1 for an inserted row */
	private static int oldRow (int row, int at, int removed, int inserted) {
		if (row < at) return row;
		if (row < at + inserted) return -1;
		return row - inserted + removed;
	}

	/* the visible rows from the first one that shows another row than before; none when the run is outside them */
	private static void invalidateChanged (Table table, int top, int newTop, int newCount, int at, int removed, int inserted) {
		long hwnd = table.handle;
		int page = Math.max (1, (int) OS.SendMessage (hwnd, OS.LVM_GETCOUNTPERPAGE, 0, 0));
		int first = -1;
		for (int r = 0; r <= page && first < 0; r++) {
			int row = newTop + r;
			if (row >= newCount || oldRow (row, at, removed, inserted) != top + r) first = r;
		}
		if (first < 0) return;
		RECT rect = new RECT ();
		rect.left = OS.LVIR_BOUNDS;
		int height = table.getItemHeightInPixels ();
		int y = OS.SendMessage (hwnd, OS.LVM_GETITEMRECT, newTop, rect) != 0 ? rect.top + first * height : 0;
		RECT client = new RECT ();
		OS.GetClientRect (hwnd, client);
		client.top = Math.max (client.top, y);
		OS.InvalidateRect (hwnd, client, true);
		table.m3RowOps += page + 1 - first;
	}
}
