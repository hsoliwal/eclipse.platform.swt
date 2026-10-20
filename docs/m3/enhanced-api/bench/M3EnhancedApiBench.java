/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0
 * which accompany this distribution.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.m3.bench;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.M3BulkUpdates;
import org.eclipse.swt.widgets.M3ItemRange;
import org.eclipse.swt.widgets.M3Prefetch;
import org.eclipse.swt.widgets.M3Residency;
import org.eclipse.swt.widgets.M3RowMath;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Optimization pass (C:/m3-superset/tools/OPTIMIZATION_PASS.md): each enhanced API against the plain-API code an
 * application writes for the same answer, at the sizes given. Runs on the hidden desktop (a shown Shell).
 * One TSV row per (api, operation, widget, size): median ns/op of 5 rounds and allocated bytes/op, plain then M3,
 * plus a third measure where one applies (paints/op, item objects made).
 * <p>
 * Usage: {@code M3EnhancedApiBench <rowmath|residency|bulk|prefetch> <sizes...>}
 */
public final class M3EnhancedApiBench {

	private static final Logger LOG = Logger.getLogger (M3EnhancedApiBench.class.getName ());
	private static final int ROUNDS = 5;
	private static final com.sun.management.ThreadMXBean THREADS =
		(com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();

	private final Display display;
	private final Shell shell;

	private M3EnhancedApiBench (Display display) {
		this.display = display;
		shell = new Shell (display);
		shell.setSize (420, 640);
		shell.open ();
	}

	/** median ns/op and bytes/op of op over the targets, ROUNDS rounds after one warm-up round */
	private static long [] measure (int [] targets, IntConsumer op) {
		long [] times = new long [ROUNDS];
		long [] bytes = new long [ROUNDS];
		for (int round = -1; round < ROUNDS; round++) {
			long allocated = THREADS.getCurrentThreadAllocatedBytes ();
			long start = System.nanoTime ();
			for (int target : targets) op.accept (target);
			long elapsed = System.nanoTime () - start;
			if (round >= 0) {
				times [round] = elapsed / targets.length;
				bytes [round] = (THREADS.getCurrentThreadAllocatedBytes () - allocated) / targets.length;
			}
		}
		Arrays.sort (times);
		Arrays.sort (bytes);
		return new long [] {times [ROUNDS / 2], bytes [ROUNDS / 2]};
	}

	/** like measure, with a reset before every round that is not timed: the op runs once per round */
	private long [] measureOnce (Runnable reset, Runnable op) {
		long [] times = new long [ROUNDS];
		long [] bytes = new long [ROUNDS];
		for (int round = -1; round < ROUNDS; round++) {
			reset.run ();
			long allocated = THREADS.getCurrentThreadAllocatedBytes ();
			long start = System.nanoTime ();
			op.run ();
			long elapsed = System.nanoTime () - start;
			if (round >= 0) {
				times [round] = elapsed;
				bytes [round] = THREADS.getCurrentThreadAllocatedBytes () - allocated;
			}
		}
		Arrays.sort (times);
		Arrays.sort (bytes);
		return new long [] {times [ROUNDS / 2], bytes [ROUNDS / 2]};
	}

	private static int [] targets (int count, int bound, long seed) {
		return new Random (seed).ints (count, 0, bound).toArray ();
	}

	private static void row (String api, String op, String widget, long size, long [] plain, long [] m3, String extra) {
		LOG.info (String.join ("\t", api, op, widget, Long.toString (size), Long.toString (plain [0]),
			Long.toString (m3 [0]), "ns/op", Long.toString (plain [1]), Long.toString (m3 [1]), "bytes/op", extra));
	}

	private void drain () {
		while (display.readAndDispatch ()) {
			// paint and dispatch until idle
		}
	}

	/* ---------------- M3RowMath: a virtual tree, size - 1000 roots, the middle root expanded with 1000 children */

	private static long plainExpandedRows (TreeItem item) {
		if (!item.getExpanded ()) return 0;
		long rows = 0;
		int count = item.getItemCount ();
		for (int i = 0; i < count; i++) rows += 1 + plainExpandedRows (item.getItem (i));
		return rows;
	}

	static long plainVisibleRowCount (Tree tree) {
		long rows = 0;
		int count = tree.getItemCount ();
		for (int i = 0; i < count; i++) rows += 1 + plainExpandedRows (tree.getItem (i));
		return rows;
	}

	static long plainRowOf (Tree tree, TreeItem item) {
		long row = 0;
		for (TreeItem current = item; current != null;) {
			TreeItem parent = current.getParentItem ();
			int index = parent == null ? tree.indexOf (current) : parent.indexOf (current);
			for (int i = 0; i < index; i++) {
				row += 1 + plainExpandedRows (parent == null ? tree.getItem (i) : parent.getItem (i));
			}
			if (parent != null) row++;
			current = parent;
		}
		return row;
	}

	static TreeItem plainItemAt (Tree tree, long row) {
		int count = tree.getItemCount ();
		for (int i = 0; i < count; i++) {
			TreeItem item = tree.getItem (i);
			if (row == 0) return item;
			row--;
			long below = plainExpandedRows (item);
			if (row < below) return plainItemAtBelow (item, row);
			row -= below;
		}
		return null;
	}

	private static TreeItem plainItemAtBelow (TreeItem parent, long row) {
		int count = parent.getItemCount ();
		for (int i = 0; i < count; i++) {
			TreeItem item = parent.getItem (i);
			if (row == 0) return item;
			row--;
			long below = plainExpandedRows (item);
			if (row < below) return plainItemAtBelow (item, row);
			row -= below;
		}
		return null;
	}

	private void rowMath (int size) {
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 400, 600);
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
		int roots = size - 1000;
		tree.setItemCount (roots);
		TreeItem middle = tree.getItem (roots / 2);
		middle.setItemCount (1000);
		middle.setExpanded (true);
		drain ();
		int rows = (int) M3RowMath.visibleRowCount (tree);
		int slow = size >= 1_000_000 ? 3 : 20;
		int [] few = targets (slow, rows, 7);
		TreeItem [] items = Arrays.stream (few).mapToObj (r -> M3RowMath.itemAt (tree, r)).toArray (TreeItem []::new);
		for (int i = 0; i < few.length; i++) {
			if (plainRowOf (tree, items [i]) != few [i] || plainItemAt (tree, few [i]) != items [i]) {
				throw new IllegalStateException ("plain walk disagrees at row " + few [i]);
			}
		}
		long [] plain = measure (new int [slow], i -> plainVisibleRowCount (tree));
		long [] m3 = measure (new int [2000], i -> M3RowMath.visibleRowCount (tree));
		row ("M3RowMath", "visible-row-count", "Tree-virtual", size, plain, m3, "rows=" + rows);
		int [] index = new int [few.length];
		Arrays.setAll (index, i -> i);
		plain = measure (index, i -> plainRowOf (tree, items [i]));
		m3 = measure (index, i -> M3RowMath.rowOf (items [i]));
		row ("M3RowMath", "row-of", "Tree-virtual", size, plain, m3, "");
		plain = measure (few, r -> plainItemAt (tree, r));
		m3 = measure (few, r -> M3RowMath.itemAt (tree, r));
		row ("M3RowMath", "item-at", "Tree-virtual", size, plain, m3, "");
		tree.dispose ();
	}

	/* ---------------- M3Residency: a table without SWT.VIRTUAL of size rows (lanes; no items until asked) */

	private void residency (int size) {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 400, 600);
		long [] [] results = new long [2] [];
		long [] facades = new long [2];
		for (int side = 0; side < 2; side++) {
			boolean m3 = side == 1;
			long [] probe = new long [1];
			results [side] = measureOnce (() -> {
				table.setItemCount (0);
				table.setItemCount (size);
				drain ();
			}, () -> {
				for (int i = 0; i < size; i++) {
					// the plain API has no way to ask without making the item: getItem materializes it
					if (m3 ? M3Residency.isMaterialized (table, i) : table.getItem (i).getData () != null) probe [0]++;
				}
			});
			facades [side] = M3Residency.stats (table) [M3Residency.FACADES];
		}
		long [] perRowPlain = {results [0] [0] / size, results [0] [1] / size};
		long [] perRowM3 = {results [1] [0] / size, results [1] [1] / size};
		row ("M3Residency", "scan-for-items (per row)", "Table", size, perRowPlain, perRowM3,
			"item objects alive after: plain " + facades [0] + ", m3 " + facades [1]);
		int maxTop = Math.max (0, size - 30);
		int [] tops = targets (2000, maxTop, 11);
		long [] plain = measure (tops, top -> {
			table.setTopIndex (top);
			Rectangle area = table.getClientArea ();
			int first = table.getTopIndex ();
			int last = Math.min (size - 1, first + (area.height + table.getItemHeight () - 1) / table.getItemHeight () - 1);
			if (last < first) throw new IllegalStateException ();
		});
		long [] m3 = measure (tops, top -> {
			table.setTopIndex (top);
			drain ();
			M3ItemRange range = M3Residency.residentRange (table);
			if (range.isEmpty ()) throw new IllegalStateException ();
		});
		row ("M3Residency", "resident-range after setTopIndex", "Table", size, plain, m3,
			"m3 side includes the paint that defines the window");
		table.dispose ();
	}

	/* ---------------- M3BulkUpdates: append 1000 rows to a table of size rows, then paint */

	private void bulk (int size) {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 400, 600);
		table.setItemCount (size);
		table.setTopIndex (size - 20);
		drain ();
		int [] paints = new int [1];
		table.addListener (SWT.Paint, event -> paints [0]++);
		int [] once = {0};
		Runnable append = () -> {
			for (int i = 0; i < 1000; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		};
		Runnable reset = () -> {
			table.remove (size, size + 999);
			drain ();
		};
		String [] names = {"unbatched", "setRedraw", "M3BulkUpdates"};
		long [] [] results = new long [3] [];
		double [] paintsPerOp = new double [3];
		for (int variant = 0; variant < 3; variant++) {
			int v = variant;
			paints [0] = 0;
			results [variant] = measure (new int [3], ignored -> {
				if (v == 0) append.run ();
				else if (v == 1) {
					table.setRedraw (false);
					append.run ();
					table.setRedraw (true);
				} else M3BulkUpdates.run (table, append);
				drain ();
				reset.run ();
			});
			paintsPerOp [variant] = paints [0] / (double) ((ROUNDS + 1) * 3);
		}
		for (int variant = 0; variant < 2; variant++) {
			row ("M3BulkUpdates", "append 1000 rows + paint (" + names [variant] + " vs M3)", "Table", size,
				results [variant], results [2], "paints/op " + paintsPerOp [variant] + " vs " + paintsPerOp [2]);
		}
		table.dispose ();
	}

	/* ---------------- M3Prefetch: scroll a list of size rows page by page and learn the rows to load */

	private void prefetch (int size) {
		List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 400, 600);
		String [] items = new String [size];
		Arrays.setAll (items, i -> "item " + i);
		list.setItems (items);
		drain ();
		int page = list.getClientArea ().height / list.getItemHeight ();
		int [] tops = targets (2000, Math.max (1, size - page), 13);
		long [] announced = new long [2];
		int [] window = {-1, -1};
		long [] plain = measure (tops, top -> {
			list.setTopIndex (top);
			drain ();
			int first = list.getTopIndex ();
			int last = Math.min (size - 1, first + page);
			// the application diffs the new window against the old one itself
			announced [0] += first > window [1] || last < window [0] ? last - first + 1
				: Math.max (0, window [0] - first) + Math.max (0, last - window [1]);
			window [0] = first;
			window [1] = last;
		});
		org.eclipse.swt.widgets.Listener listener = event -> {
			if (event.detail == M3Prefetch.PREFETCH) announced [1] += event.end - event.start + 1;
		};
		M3Prefetch.addListener (list, listener);
		long [] m3 = measure (tops, top -> {
			list.setTopIndex (top);
			drain ();
		});
		M3Prefetch.removeListener (list, listener);
		row ("M3Prefetch", "scroll to a random page + rows to load", "List", size, plain, m3,
			"rows announced plain " + announced [0] + ", m3 " + announced [1]);
		list.dispose ();
	}

	public static void main (String [] args) {
		Display display = new Display ();
		try {
			M3EnhancedApiBench bench = new M3EnhancedApiBench (display);
			LOG.info (String.join ("\t", "api", "operation", "widget", "size", "plain", "m3", "unit", "plain_bytes",
				"m3_bytes", "bytes_unit", "note"));
			for (int i = 1; i < args.length; i++) {
				int size = Integer.parseInt (args [i]);
				switch (args [0]) {
					case "rowmath" -> bench.rowMath (size);
					case "residency" -> bench.residency (size);
					case "bulk" -> bench.bulk (size);
					case "prefetch" -> bench.prefetch (size);
					default -> throw new IllegalArgumentException (args [0]);
				}
			}
		} finally {
			display.dispose ();
		}
	}
}
