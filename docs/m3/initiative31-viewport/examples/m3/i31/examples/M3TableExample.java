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
package m3.i31.examples;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

/**
 * A virtual Table of 1,000,000 rows and three columns on Initiative 31 (SWT on Swing), filled on demand; see
 * {@link M3ExampleWindow} for the arguments.
 */
public final class M3TableExample {
	private M3TableExample () {
	}

	public static void main (String [] args) {
		M3ExampleWindow window = new M3ExampleWindow ("Table", args);
		Table table = new Table (window.shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		window.fill (table);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		for (String title : new String [] {"Row", "Hex", "Square mod 97"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (title);
			column.setWidth (140);
		}
		int [] setData = new int [1];
		table.addListener (SWT.SetData, e -> {
			setData [0]++;
			int row = e.index;
			((TableItem) e.item).setText (new String [] {String.format ("%,d", row), Integer.toHexString (row), String.valueOf ((long) row * row % 97)});
		});
		long start = System.nanoTime ();
		table.setItemCount (window.rows);
		long millis = (System.nanoTime () - start) / 1_000_000;
		window.run (() -> {
			table.setTopIndex (window.top);
			table.setSelection (window.top + 2);
			window.pump ();
			window.describe ("Table", millis, "SetData " + setData [0] + " times, top index " + String.format ("%,d", table.getTopIndex ()));
		});
	}
}
