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
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/** A CTabFolder whose rows are tabs; scrolling shows a tab, selecting selects one tab. */
final class CTabFolderSubject extends Subject {
	CTabFolder folder;

	@Override
	void create (Shell shell, int style) {
		folder = new CTabFolder (shell, style);
	}

	@Override
	Widget widget () {
		return folder;
	}

	@Override
	void show (int [] order) {
		while (folder.getItemCount () > order.length) folder.getItem (folder.getItemCount () - 1).dispose ();
		while (folder.getItemCount () < order.length) new CTabItem (folder, SWT.NONE);
		for (int i = 0; i < order.length; i++) folder.getItem (i).setText (text (order [i]));
	}

	@Override
	void scrollTo (int row) {
		folder.showItem (folder.getItem (row));
	}

	@Override
	void select (int start, int end) {
		folder.setSelection (start);
	}

	@Override
	void update (int row, String text) {
		folder.getItem (row).setText (text);
	}

	@Override
	String state () {
		if (folder.isDisposed ()) return "disposed";
		int n = folder.getItemCount (), selection = folder.getSelectionIndex ();
		String showing = n == 0 ? "-" : folder.getItem (0).isShowing () + "/" + folder.getItem (n - 1).isShowing ();
		return "count=" + n + " sel=" + selection + " showing(first/last)=" + showing + " text=" + (selection < 0 ? "-" : folder.getItem (selection).getText ());
	}

	@Override
	List<Widget> items () {
		List<Widget> items = new ArrayList<> ();
		for (int i : sample (folder.getItemCount ())) items.add (folder.getItem (i));
		return items;
	}
}
