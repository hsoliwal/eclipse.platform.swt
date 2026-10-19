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
package org.eclipse.swt.snippets.m3;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.m3.collections.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * A viewport assembled from collections, driving an SWT virtual Table: the same views as the Swing example in
 * M3JDK (m3/collections/examples/viewport/ViewportFromCollectionsSwingExample). A tree is flattened
 * (M3FlatteningView), filtered by a word mask (M3BitMask), sorted as a row permutation (M3PermutationIndex) and
 * selected through sorted ranges (M3RangeSet); the Table only maps rows in SetData. Run with {@code --check} to build
 * and verify the model without a Display.
 */
public class M3ViewportFromCollectionsSnippet {
	record Node (int group, int index) {
	}

	public static void main (String [] args) {
		M3FlatteningView<Node> tree = new M3FlatteningView<> (new M3FlatteningView.Source<> () {
			@Override
			public int childCount (Node parent) {
				return parent == null ? 100 : parent.group () < 0 ? 1_000 : 0;
			}

			@Override
			public Node child (Node parent, int index) {
				return new Node (parent == null ? -1 : parent.index (), index);
			}
		});
		for (int g = 99; g >= 0; g--) tree.expand (g);
		int n = tree.size ();
		long [] mask = M3BitMask.ensure (new long [0], n);
		for (int row = 0; row < n; row++) M3BitMask.set (mask, row, tree.get (row).index () % 7 != 0);
		int [] rows = new int [n];
		int visible = M3BitMask.compact (mask, M3BitMask.words (n), rows, new int [M3BitMask.words (n)]);
		M3PermutationIndex sort = new M3PermutationIndex ();
		sort.sort (rows, visible, new M3RadixKey [] {new M3RadixKey () {
			@Override
			public int radixKeys (int [] r, int count, M3PermutationIndex lanes) {
				for (int i = 0; i < count; i++) lanes.keys [i] = 1_000 - tree.get (r [i]).index ();
				return 2;
			}
		}}, new boolean [] {false}, 1, (a, b) -> Integer.compare (tree.get (b).index (), tree.get (a).index ()));
		M3RangeSet selection = new M3RangeSet ();
		selection.setLogicalCount (visible);
		selection.selectRange (0, 3);
		if (args.length > 0 && args [0].equals ("--check")) {
			if (visible >= n || tree.get (rows [0]).index () % 7 == 0) throw new AssertionError ("snippet model");
			System.getLogger ("M3ViewportFromCollectionsSnippet").log (System.Logger.Level.INFO,
					"tree rows=" + n + " visible after filter=" + visible + " first=" + tree.get (rows [0]).index ());
			return;
		}
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("viewport from collections");
		shell.setLayout (new FillLayout ());
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		table.setHeaderVisible (true);
		for (String title : new String [] {"group", "index", "selected"}) new TableColumn (table, SWT.NONE).setText (title);
		table.addListener (SWT.SetData, e -> {
			TableItem item = (TableItem) e.item;
			int row = table.indexOf (item);
			Node node = tree.get (rows [row]);
			item.setText (new String [] {String.valueOf (node.group ()), String.valueOf (node.index ()),
					String.valueOf (selection.isSelected (row))});
		});
		table.setItemCount (visible);
		for (TableColumn column : table.getColumns ()) column.pack ();
		shell.setSize (480, 360);
		shell.open ();
		while (!shell.isDisposed ()) if (!display.readAndDispatch ()) display.sleep ();
		display.dispose ();
	}
}
