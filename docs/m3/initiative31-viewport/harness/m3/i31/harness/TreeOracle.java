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
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Measures how a virtual {@link Tree} materializes items: SetData calls, timing of
 * {@link Tree#setItemCount(int)}, and the answers of the scrolling API. Uses only public SWT API,
 * so the same class runs against Initiative 31 (SWT on Swing) and the official SWT jar.
 * <p>
 * Usage: <code>TreeOracle &lt;out-file&gt; &lt;count&gt;...</code>
 * </p>
 */
public final class TreeOracle {
	private TreeOracle () {
	}

	public static void main (String [] args) throws IOException {
		List<String> out = new ArrayList<> ();
		out.add ("platform=" + SWT.getPlatform () + " version=" + SWT.getVersion () + " mode=" + System.getProperty ("swt.widget.mode.Tree", System.getProperty ("swt.widget.mode", "default")));
		Display display = new Display ();
		try {
			for (int i = 1; i < args.length; i++) {
				measure (display, Integer.parseInt (args [i]), out);
			}
		} finally {
			display.dispose ();
		}
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
	}

	static void measure (Display display, int count, List<String> out) {
		Shell shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (320, 240);
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		int [] setData = new int [1];
		BitSet touched = new BitSet ();
		tree.addListener (SWT.SetData, e -> {
			setData [0]++;
			TreeItem item = (TreeItem) e.item;
			if (item.getParentItem () == null) touched.set (e.index);
			item.setText ("r" + e.index);
		});
		long t0 = System.nanoTime ();
		tree.setItemCount (count);
		long t1 = System.nanoTime ();
		out.add ("count=" + count + " setItemCount_ms=" + (t1 - t0) / 1_000_000 + " setData_after_setItemCount=" + setData [0]);
		shell.open ();
		pump (display);
		out.add ("count=" + count + " setData_after_open=" + setData [0] + " touched=" + describe (touched));
		out.add ("count=" + count + " itemCount=" + tree.getItemCount () + " itemHeight>0=" + (tree.getItemHeight () > 0) + " top=" + index (tree, tree.getTopItem ()));
		int target = count / 2;
		tree.setTopItem (tree.getItem (target));
		pump (display);
		out.add ("count=" + count + " setTopItem(" + target + ") top=" + index (tree, tree.getTopItem ()) + " setData=" + setData [0] + " touched=" + describe (touched));
		tree.showItem (tree.getItem (count - 1));
		pump (display);
		out.add ("count=" + count + " showItem(last) top=" + index (tree, tree.getTopItem ()) + " setData=" + setData [0]);
		Point size = tree.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		pump (display);
		out.add ("count=" + count + " computeSize setData=" + setData [0] + " size>0=" + (size.x > 0 && size.y > 0));
		long t2 = System.nanoTime ();
		tree.setItemCount (0);
		long t3 = System.nanoTime ();
		out.add ("count=" + count + " setItemCount(0)_ms=" + (t3 - t2) / 1_000_000 + " itemCount=" + tree.getItemCount ());
		shell.dispose ();
		pump (display);
	}

	static int index (Tree tree, TreeItem item) {
		return item == null ? -2 : tree.indexOf (item);
	}

	static String describe (BitSet touched) {
		int first = touched.nextSetBit (0);
		return touched.cardinality () + (first < 0 ? "" : "[" + first + ".." + touched.length () + ")");
	}

	static void pump (Display display) {
		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < 10_000 && display.readAndDispatch (); i++) {
				// drain
			}
			try {
				Thread.sleep (30);
			} catch (InterruptedException e) {
				Thread.currentThread ().interrupt ();
			}
		}
	}
}
