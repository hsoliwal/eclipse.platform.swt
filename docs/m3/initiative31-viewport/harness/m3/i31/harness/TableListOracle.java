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
package m3.i31.harness;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.BitSet;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Measures a virtual {@link Table} and a {@link List} the way {@link TreeOracle} measures a tree:
 * SetData calls, bulk timing, and the answers of the scrolling API. Public SWT API only.
 * <p>
 * Usage: <code>TableListOracle &lt;out-file&gt; &lt;count&gt;...</code>
 * </p>
 */
public final class TableListOracle {
	private TableListOracle () {
	}

	public static void main (String [] args) throws IOException {
		java.util.List<String> out = new ArrayList<> ();
		out.add ("platform=" + SWT.getPlatform () + " version=" + SWT.getVersion ());
		Display display = new Display ();
		try {
			for (int i = 1; i < args.length; i++) {
				table (display, Integer.parseInt (args [i]), out);
				list (display, Integer.parseInt (args [i]), out);
			}
		} finally {
			display.dispose ();
		}
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
	}

	static Shell shell (Display display) {
		Shell shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (320, 240);
		return shell;
	}

	static void table (Display display, int count, java.util.List<String> out) {
		Shell shell = shell (display);
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		int [] setData = new int [1];
		BitSet touched = new BitSet ();
		table.addListener (SWT.SetData, e -> {
			setData [0]++;
			touched.set (e.index);
			((TableItem) e.item).setText ("r" + e.index);
		});
		long t0 = System.nanoTime ();
		table.setItemCount (count);
		long t1 = System.nanoTime ();
		out.add ("table count=" + count + " setItemCount_ms=" + (t1 - t0) / 1_000_000 + " setData=" + setData [0]);
		shell.open ();
		TreeOracle.pump (display);
		out.add ("table count=" + count + " open setData=" + setData [0] + " touched=" + TreeOracle.describe (touched) + " top=" + table.getTopIndex ());
		int target = count / 2;
		table.setTopIndex (target);
		TreeOracle.pump (display);
		out.add ("table count=" + count + " setTopIndex(" + target + ") top=" + table.getTopIndex () + " setData=" + setData [0]);
		table.showItem (table.getItem (count - 1));
		TreeOracle.pump (display);
		out.add ("table count=" + count + " showItem(last) top=" + table.getTopIndex () + " setData=" + setData [0]);
		table.setSelection (3);
		table.showSelection ();
		TreeOracle.pump (display);
		out.add ("table count=" + count + " showSelection(3) top=" + table.getTopIndex () + " indexOf=" + table.indexOf (table.getItem (count - 2)));
		Point size = table.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		out.add ("table count=" + count + " computeSize setData=" + setData [0] + " size>0=" + (size.x > 0 && size.y > 0));
		long t2 = System.nanoTime ();
		table.setItemCount (0);
		long t3 = System.nanoTime ();
		out.add ("table count=" + count + " setItemCount(0)_ms=" + (t3 - t2) / 1_000_000 + " itemCount=" + table.getItemCount ());
		shell.dispose ();
		TreeOracle.pump (display);
	}

	static void list (Display display, int count, java.util.List<String> out) {
		Shell shell = shell (display);
		List list = new List (shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		String [] items = new String [count];
		for (int i = 0; i < count; i++) items [i] = "r" + i;
		long t0 = System.nanoTime ();
		list.setItems (items);
		long t1 = System.nanoTime ();
		for (int i = 0; i < count / 10; i++) list.add ("a" + i);
		long t2 = System.nanoTime ();
		out.add ("list count=" + count + " setItems_ms=" + (t1 - t0) / 1_000_000 + " add(n/10)_ms=" + (t2 - t1) / 1_000_000 + " itemCount=" + list.getItemCount ());
		shell.open ();
		TreeOracle.pump (display);
		out.add ("list count=" + count + " open top=" + list.getTopIndex ());
		int target = count / 2;
		list.setTopIndex (target);
		TreeOracle.pump (display);
		out.add ("list count=" + count + " setTopIndex(" + target + ") top=" + list.getTopIndex ());
		list.setSelection (count - 1);
		list.showSelection ();
		TreeOracle.pump (display);
		out.add ("list count=" + count + " showSelection(last) top=" + list.getTopIndex () + " indexOf=" + list.indexOf ("r" + (count - 2)));
		long t3 = System.nanoTime ();
		Point size = list.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		long t4 = System.nanoTime ();
		out.add ("list count=" + count + " computeSize_ms=" + (t4 - t3) / 1_000_000 + " size>0=" + (size.x > 0 && size.y > 0));
		long t5 = System.nanoTime ();
		list.removeAll ();
		long t6 = System.nanoTime ();
		out.add ("list count=" + count + " removeAll_ms=" + (t6 - t5) / 1_000_000 + " itemCount=" + list.getItemCount ());
		shell.dispose ();
		TreeOracle.pump (display);
	}
}
