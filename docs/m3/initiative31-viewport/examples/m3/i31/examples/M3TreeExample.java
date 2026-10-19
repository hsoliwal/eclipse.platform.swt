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
 * A virtual Tree of 1,000,000 root items on Initiative 31 (SWT on Swing). Every tenth item has three children,
 * filled on demand. In the viewport mode the tree creates items only for the rows on screen; see
 * {@link M3ExampleWindow} for the arguments.
 */
public final class M3TreeExample {
	private M3TreeExample () {
	}

	public static void main (String [] args) {
		M3ExampleWindow window = new M3ExampleWindow ("Tree", args);
		Tree tree = new Tree (window.shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		window.fill (tree);
		int [] setData = new int [1];
		tree.addListener (SWT.SetData, e -> {
			setData [0]++;
			TreeItem item = (TreeItem) e.item;
			TreeItem parent = item.getParentItem ();
			item.setText (parent == null ? String.format ("Item %,d", e.index) : parent.getText () + " / child " + e.index);
			if (parent == null && e.index % 10 == 0) item.setItemCount (3);
		});
		long start = System.nanoTime ();
		tree.setItemCount (window.rows);
		long millis = (System.nanoTime () - start) / 1_000_000;
		window.run (() -> {
			TreeItem top = tree.getItem (window.top);
			top.setExpanded (true);
			tree.setTopItem (top);
			tree.setSelection (top);
			window.pump ();
			window.describe ("Tree", millis, "SetData " + setData [0] + " times, top item " + String.format ("%,d", tree.indexOf (tree.getTopItem ())));
		});
	}
}
