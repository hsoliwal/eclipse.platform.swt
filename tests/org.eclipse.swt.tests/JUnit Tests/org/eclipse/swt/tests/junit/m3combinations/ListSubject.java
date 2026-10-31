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

/** A List; its rows are its string items. */
final class ListSubject extends Subject {
	org.eclipse.swt.widgets.List list;

	@Override
	void create (Shell shell, int style) {
		list = new org.eclipse.swt.widgets.List (shell, style);
	}

	@Override
	Widget widget () {
		return list;
	}

	@Override
	void show (int [] order) {
		list.setItems (texts (order));
	}

	@Override
	void scrollTo (int row) {
		list.setTopIndex (row);
	}

	@Override
	void select (int start, int end) {
		if ((list.getStyle () & SWT.MULTI) != 0) list.setSelection (start, end); else list.setSelection (start);
	}

	@Override
	void update (int row, String text) {
		list.setItem (row, text);
	}

	@Override
	String state () {
		if (list.isDisposed ()) return "disposed";
		int n = list.getItemCount (), top = list.getTopIndex ();
		return "count=" + n + " top=" + top + " sel=" + ranges (list.getSelectionIndices ()) + " text=" + (n == 0 ? "-" : list.getItem (top));
	}

	@Override
	java.util.List<Widget> items () {
		return new ArrayList<> ();
	}
}
