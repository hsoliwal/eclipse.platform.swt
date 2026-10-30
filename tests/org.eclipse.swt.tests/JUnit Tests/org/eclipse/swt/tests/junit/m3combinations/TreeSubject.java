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

/** A Tree whose rows are root items (one column); virtual roots are filled from the order on SetData. */
final class TreeSubject extends Subject {
	Tree tree;

	@Override
	void create (Shell shell, int style) {
		tree = new Tree (shell, style);
		TreeColumn column = new TreeColumn (tree, SWT.NONE);
		column.setWidth (300);
		if (virtual ()) tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText (rowText (e.index)));
	}

	boolean virtual () {
		return (tree.getStyle () & SWT.VIRTUAL) != 0;
	}

	@Override
	Widget widget () {
		return tree;
	}

	@Override
	void show (int [] order) {
		if (virtual ()) {
			tree.clearAll (true);
			tree.setItemCount (order.length);
			return;
		}
		tree.setItemCount (order.length);
		for (int i = 0; i < order.length; i++) tree.getItem (i).setText (text (order [i]));
	}

	@Override
	void scrollTo (int row) {
		tree.setTopItem (tree.getItem (row));
	}

	@Override
	void select (int start, int end) {
		boolean multi = (tree.getStyle () & SWT.MULTI) != 0;
		TreeItem [] items = new TreeItem [multi ? end - start + 1 : 1];
		for (int i = 0; i < items.length; i++) items [i] = tree.getItem (start + i);
		tree.setSelection (items);
	}

	@Override
	void update (int row, String text) {
		tree.getItem (row).setText (text);
	}

	@Override
	String state () {
		if (tree.isDisposed ()) return "disposed";
		int n = tree.getItemCount ();
		TreeItem top = n == 0 ? null : tree.getTopItem ();
		int [] selection = Arrays.stream (tree.getSelection ()).mapToInt (tree::indexOf).toArray ();
		String checked = top == null || (tree.getStyle () & SWT.CHECK) == 0 ? "" : " checked=" + top.getChecked ();
		return "count=" + n + " top=" + (top == null ? -1 : tree.indexOf (top)) + " sel=" + ranges (selection) + " text=" + (top == null ? "-" : top.getText ()) + checked;
	}

	@Override
	List<Widget> items () {
		List<Widget> items = new ArrayList<> ();
		for (int i : sample (tree.getItemCount ())) items.add (tree.getItem (i));
		items.addAll (Arrays.asList (tree.getColumns ()));
		return items;
	}
}
