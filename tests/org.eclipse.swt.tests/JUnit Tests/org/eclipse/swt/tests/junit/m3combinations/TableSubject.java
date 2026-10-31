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
package org.eclipse.swt.tests.junit.m3combinations;

import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

/** A Table with one column; virtual rows are filled from the order on SetData, plain rows are set directly. */
final class TableSubject extends Subject {
	Table table;

	@Override
	void create (Shell shell, int style) {
		table = new Table (shell, style);
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setWidth (300);
		if (virtual ()) table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText (rowText (table.indexOf ((TableItem) e.item))));
	}

	boolean virtual () {
		return (table.getStyle () & SWT.VIRTUAL) != 0;
	}

	@Override
	Widget widget () {
		return table;
	}

	@Override
	void show (int [] order) {
		if (virtual ()) {
			table.clearAll ();
			table.setItemCount (order.length);
			return;
		}
		table.setItemCount (order.length);
		for (int i = 0; i < order.length; i++) table.getItem (i).setText (text (order [i]));
	}

	@Override
	void scrollTo (int row) {
		table.setTopIndex (row);
	}

	@Override
	void select (int start, int end) {
		if ((table.getStyle () & SWT.MULTI) != 0) table.setSelection (start, end); else table.setSelection (start);
	}

	@Override
	void update (int row, String text) {
		table.getItem (row).setText (text);
	}

	@Override
	String state () {
		if (table.isDisposed ()) return "disposed";
		int n = table.getItemCount (), top = table.getTopIndex ();
		String text = n == 0 ? "-" : table.getItem (top).getText ();
		String checked = n == 0 || (table.getStyle () & SWT.CHECK) == 0 ? "" : " checked=" + table.getItem (top).getChecked ();
		return "count=" + n + " top=" + top + " sel=" + ranges (table.getSelectionIndices ()) + " text=" + text + checked;
	}

	@Override
	List<Widget> items () {
		List<Widget> items = new ArrayList<> ();
		for (int i : sample (table.getItemCount ())) items.add (table.getItem (i));
		items.addAll (Arrays.asList (table.getColumns ()));
		return items;
	}
}
