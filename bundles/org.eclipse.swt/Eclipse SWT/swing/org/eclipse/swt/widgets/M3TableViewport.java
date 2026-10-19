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
 * <li>the scroll contract of {@link M3SwingScroll}: the top index is the row shown at the top of the viewport,
 * and <code>setTopIndex</code> puts the row there.</li>
 * </ul>
 */
final class M3TableViewport {
	/** Whether new tables use the viewport mode. */
	static final boolean ENABLED = WidgetMode.of ("Table", WidgetMode.VIEWPORT) == WidgetMode.VIEWPORT;

	final Table table;
	private final M3SwingScroll scroll = new M3SwingScroll ();

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
		return new M3TableViewport (table);
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
		ArrayList<Object> slots = slots ();
		int old = slots.size ();
		if (count > old) {
			slots.addAll (Collections.nCopies (count - old, null));
			M3TableRows.inserted ((CTable) table.handle, old, count - old);
		} else {
			shrink (slots, count);
		}
		return true;
	}

	private void shrink (ArrayList<Object> slots, int count) {
		int end = slots.size ();
		while (end > count) {
			int start = end;
			while (start > count && slots.get (start - 1) == null) start--;
			if (start < end) {
				slots.subList (start, end).clear ();
				M3TableRows.removed ((CTable) table.handle, start, end - start);
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
