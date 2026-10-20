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

import java.awt.event.HierarchyEvent;
import java.util.*;

import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.m3.*;
import org.eclipse.swt.internal.swing.*;

/**
 * Viewport mode of a {@link Table} on Swing (the default mode; <code>-Dswt.widget.mode.Table=original</code>
 * selects the original implementation). Table items are already created on demand in the original mode; the
 * viewport mode adds:
 * <ul>
 * <li>one model event per <code>setItemCount</code> of a virtual table, instead of one per row;</li>
 * <li>one model event per run of unmaterialized rows in <code>remove(int, int)</code> and <code>removeAll()</code>
 * (Swing gets the delta, never one event per row);</li>
 * <li>the scroll contract of {@link M3SwingScroll}: the top index is the row shown at the top of the viewport,
 * and <code>setTopIndex</code> puts the row there.</li>
 * </ul>
 */
final class M3TableViewport {
	/** Whether new tables use the viewport mode. */
	static final boolean ENABLED = WidgetMode.of ("Table", WidgetMode.VIEWPORT) == WidgetMode.VIEWPORT;

	final Table table;
	private final M3SwingScroll scroll = new M3SwingScroll ();
	/** Rows changed while the table was not showing; Swing gets them as one patch when it shows. */
	private boolean dirty;

	private M3TableViewport (Table table) {
		this.table = table;
	}

	/**
	 * Puts a new table in the viewport mode.
	 *
	 * @param table the table, right after its Swing handle was created
	 * @return the viewport of the table
	 */
	static M3TableViewport install (Table table) {
		M3TableViewport viewport = new M3TableViewport (table);
		viewport.rows ().addHierarchyListener (e -> {
			if ((e.getChangeFlags () & HierarchyEvent.SHOWING_CHANGED) != 0 && e.getComponent ().isShowing ()) viewport.shown ();
		});
		return viewport;
	}

	/**
	 * The table shows again (brief rule 24, hidden viewports): the rows changed while it was hidden reach Swing as
	 * one validation and one paint of the window.
	 */
	void shown () {
		if (!dirty) return;
		dirty = false;
		JTable rows = rows ();
		rows.revalidate ();
		rows.repaint ();
	}

	/** Hook of <code>Table.destroyItem(TableItem)</code>: the slot leaves with a model event, or quietly while hidden. */
	void destroyItem (TableItem item) {
		int index = table.indexOf (item);
		if (index < 0) return;
		slots ().remove (index);
		removed (index, 1);
	}

	private void inserted (int index, int count) {
		if (rows ().isShowing ()) {
			M3TableRows.inserted ((CTable) table.handle, index, count);
		} else {
			M3TableRows.insertedHidden ((CTable) table.handle, index, count);
			dirty = true;
		}
	}

	private void removed (int index, int count) {
		if (rows ().isShowing ()) {
			M3TableRows.removed ((CTable) table.handle, index, count);
		} else {
			M3TableRows.removedHidden ((CTable) table.handle, index, count);
			dirty = true;
		}
	}

	/** Hook of <code>Table.getTopIndex()</code>. */
	int getTopIndex () {
		JTable rows = rows ();
		return Math.max (0, scroll.topRow (viewport (), rows.getRowCount (), rows.getRowHeight ()));
	}

	/** Hook of <code>Table.setTopIndex(int)</code>. */
	void setTopIndex (int index) {
		JTable rows = rows ();
		scroll.setTopRow (viewport (), index, rows.getRowCount (), rows.getRowHeight ());
	}

	/**
	 * Hook of <code>Table.setItemCount(int)</code> for a virtual table: grows or shrinks the item slots and
	 * tells Swing once per run of rows. Materialized items that are dropped are released, as in the original
	 * mode.
	 *
	 * @return <code>false</code> when the table is not virtual (the original code applies)
	 */
	boolean setItemCount (int count) {
		if ((table.style & SWT.VIRTUAL) == 0) return false;
		quietly (() -> resize (count));
		return true;
	}

	private void resize (int count) {
		ArrayList<Object> slots = slots ();
		int old = slots.size ();
		if (count > old) {
			slots.addAll (Collections.nCopies (count - old, null));
			inserted (old, count - old);
		} else {
			shrink (slots, count);
		}
	}

	/**
	 * Hook of <code>Table.remove(int, int)</code> and <code>Table.removeAll()</code> (the range is checked): from the
	 * end to the start, as in the original mode, materialized items are disposed and each run of unmaterialized rows
	 * leaves with one model event.
	 *
	 * @param start the first row removed
	 * @param end the last row removed (inclusive); below <code>start</code> removes nothing
	 */
	void remove (int start, int end) {
		quietly (() -> removeRuns (start, end));
	}

	private void removeRuns (int start, int end) {
		ArrayList<Object> slots = slots ();
		int stop = end + 1;
		while (stop > start) {
			int from = stop;
			while (from > start && slots.get (from - 1) == null) from--;
			if (from < stop) {
				slots.subList (from, stop).clear ();
				removed (from, stop - from);
			}
			// disposing the item takes its slot out (Table.destroyItem)
			if (from > start) ((TableItem) slots.get (from - 1)).dispose ();
			stop = from - 1;
		}
	}

	/**
	 * Runs a change of the rows with <code>isAdjustingSelection</code> set: the selection events Swing sends for
	 * it do not become SWT.Selection, as the official SWT sends none for a programmatic change (UPSTREAM_DEFECTS
	 * I31-B3).
	 */
	private void quietly (Runnable change) {
		boolean adjusting = table.isAdjustingSelection;
		table.isAdjustingSelection = true;
		try {
			change.run ();
		} finally {
			table.isAdjustingSelection = adjusting;
		}
	}

	private void shrink (ArrayList<Object> slots, int count) {
		int end = slots.size ();
		while (end > count) {
			int start = end;
			while (start > count && slots.get (start - 1) == null) start--;
			if (start < end) {
				slots.subList (start, end).clear ();
				removed (start, end - start);
			}
			if (start > count) ((TableItem) slots.get (start - 1)).release (true);
			end = Math.min (start - 1, slots.size ());
		}
	}

	@SuppressWarnings ("unchecked")
	private ArrayList<Object> slots () {
		return table.itemList;
	}

	private JTable rows () {
		return (JTable) ((CTable) table.handle).getSwingComponent ();
	}

	private JViewport viewport () {
		return ((JScrollPane) table.handle).getViewport ();
	}
}
