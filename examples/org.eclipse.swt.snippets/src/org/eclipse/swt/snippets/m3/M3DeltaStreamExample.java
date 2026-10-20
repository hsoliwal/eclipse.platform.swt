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

import java.util.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * Model seam, delta-only views (brief rule 24): a stream of 100,000 deltas into a <code>Table</code> and a
 * <code>Tree</code> of 1,000,000 rows, without a full refresh.
 * <p>
 * Every change reaches the widget as one {@link M3Patch} through {@link M3Model#apply(M3Patch, M3Model.Labels)}:
 * 70% label updates, 15% inserts and 15% removes of one row at a random position. The example never calls
 * <code>setItemCount</code>, <code>removeAll</code>, <code>clearAll</code>, <code>setItems</code> or a refresh
 * after the initial load. Both widgets are <code>SWT.VIRTUAL</code>, so the labels of a changed row are asked only
 * when the row is on screen, and a delta outside the window keeps the top row (anchored by id) and changes only the
 * scroll bar. The Tree is the catalogue grouped by category: 1,000 categories of 999 products.
 * </p>
 * <p>
 * Arguments: <code>[table|tree|both] [rows] [deltas] [seconds] [exit]</code>, defaults
 * <code>both 1000000 100000 600</code>. The events are pumped after every 100 deltas, so the window repaints
 * while the stream runs. The result (deltas applied, wall time, median and 99th percentile per delta, labels asked,
 * deltas that moved the top row) is logged through <code>java.util.logging</code> and shown in the status line;
 * with <code>exit</code> the example closes when the stream ends (the hidden-desktop runs of
 * <code>docs/m3/enhanced-api</code> use it).
 * </p>
 */
public class M3DeltaStreamExample {

static final Logger LOG = Logger.getLogger (M3DeltaStreamExample.class.getName ());
static final int CATEGORIES = 1000;
static final long CHILD_BASE = 1L << 32;

/** The labels of the catalogue: product id, name and a price that changes with every update. */
static final class Catalogue implements M3Model.Labels {
	long asked;
	long tick;

	@Override
	public void fill (long id, M3Model.Row row) {
		asked++;
		row.setText (0, Long.toString (id));
		row.setText (1, id >= CHILD_BASE ? "product " + (id - CHILD_BASE) : "category " + id);
		row.setText (2, String.format (Locale.ROOT, "%.2f", ((id * 37 + tick) % 10000) / 100.0));
	}
}

/** One delta stream over one parent lane; the caller picks the lane per delta. */
static final class Stream {
	final Random random = new Random (2026);
	final long [] nanos;
	int applied, topMoved;
	long nextId;

	Stream (int deltas, long firstNewId) {
		nanos = new long [deltas];
		nextId = firstNewId;
	}

	/* one delta of one row in a lane of the given size: 70% update, 15% insert, 15% remove */
	M3Patch next (long parent, int size) {
		int kind = random.nextInt (100);
		M3Patch.Builder builder = M3Patch.builder (parent);
		if (kind < 70 && size > 0) return builder.update (random.nextInt (size), 1).build ();
		if (kind < 85 || size == 0) return builder.insert (random.nextInt (size + 1), nextId++).build ();
		return builder.remove (random.nextInt (size), 1).build ();
	}

	String summary (String what, long wallNanos, long asked, int rows) {
		long [] sorted = Arrays.copyOf (nanos, applied);
		Arrays.sort (sorted);
		long median = sorted.length == 0 ? 0 : sorted [sorted.length / 2] / 1000;
		long p99 = sorted.length == 0 ? 0 : sorted [Math.min (sorted.length - 1, sorted.length * 99 / 100)] / 1000;
		return String.format (Locale.ROOT, "%s: %,d deltas in %,d ms, median %,d us, p99 %,d us per delta, %,d labels asked, %,d rows,"
			+ " top row moved by %d deltas outside the window", what, applied, wallNanos / 1_000_000, median, p99, asked, rows, topMoved);
	}
}

public static void main (String [] args) {
	String which = args.length > 0 ? args [0] : "both";
	int rows = args.length > 1 ? Integer.parseInt (args [1]) : 1_000_000;
	int deltas = args.length > 2 ? Integer.parseInt (args [2]) : 100_000;
	long seconds = args.length > 3 ? Long.parseLong (args [3]) : 600;
	boolean exit = Arrays.asList (args).contains ("exit");
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3 model seam: a delta stream, no full refresh");
	shell.setLayout (new GridLayout (2, true));
	Label status = new Label (shell, SWT.WRAP);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false, 2, 1));
	shell.setSize (900, 640);
	shell.open ();
	StringBuilder report = new StringBuilder ();
	if (!which.equals ("tree")) report.append (streamTable (shell, rows, deltas, seconds)).append ('\n');
	if (!which.equals ("table")) report.append (streamTree (shell, rows, deltas, seconds));
	status.setText (report.toString ().trim ());
	shell.layout (true, true);
	while (!exit && !shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

static Table table (Shell shell) {
	Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
	table.setHeaderVisible (true);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	for (String title : new String [] {"Id", "Name", "Price"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (130);
	}
	return table;
}

/* a flat lane of rows: the Table's rows are the product ids 0..rows-1 after the load */
static String streamTable (Shell shell, int rows, int deltas, long seconds) {
	Table table = table (shell);
	table.setItemCount (rows);
	shell.layout (true, true);
	M3Model model = M3Model.of (table);
	Catalogue labels = new Catalogue ();
	Stream stream = new Stream (deltas, rows);
	table.setTopIndex (rows / 2);
	long start = System.nanoTime (), deadline = start + seconds * 1_000_000_000L;
	for (int k = 0; k < deltas && System.nanoTime () < deadline; k++) {
		int size = model.size (M3Model.ROOT);
		M3Patch patch = stream.next (M3Model.ROOT, size);
		int top = table.getTopIndex ();
		long topId = model.id (M3Model.ROOT, top);
		boolean outside = outsideWindow (patch, top, table.getClientArea ().height / Math.max (1, table.getItemHeight ()) + 1);
		labels.tick = k;
		long t0 = System.nanoTime ();
		model.apply (patch, labels);
		stream.nanos [stream.applied++] = System.nanoTime () - t0;
		if (outside && model.id (M3Model.ROOT, table.getTopIndex ()) != topId) stream.topMoved++;
		if (k % 100 == 99) pump (shell.getDisplay ());
	}
	String line = stream.summary ("Table " + rows, System.nanoTime () - start, labels.asked, table.getItemCount ());
	LOG.info (line);
	return line;
}

/* the catalogue grouped by category: CATEGORIES roots, each with rows / CATEGORIES - 1 products */
static String streamTree (Shell shell, int rows, int deltas, long seconds) {
	Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
	tree.setHeaderVisible (true);
	tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	for (String title : new String [] {"Id", "Name", "Price"}) {
		TreeColumn column = new TreeColumn (tree, SWT.NONE);
		column.setText (title);
		column.setWidth (130);
	}
	int perCategory = rows / CATEGORIES - 1;
	tree.setItemCount (CATEGORIES);
	M3Model model = M3Model.of (tree);
	for (int c = 0; c < CATEGORIES; c++) {
		tree.getItem (c).setItemCount (perCategory);
		long [] ids = new long [perCategory];
		for (int k = 0; k < perCategory; k++) ids [k] = CHILD_BASE + (long) c * perCategory + k;
		model.setIds (c, ids);
	}
	for (int c = CATEGORIES / 2; c < CATEGORIES / 2 + 3; c++) tree.getItem (c).setExpanded (true);
	tree.setTopItem (tree.getItem (CATEGORIES / 2));
	shell.layout (true, true);
	Catalogue labels = new Catalogue ();
	Stream stream = new Stream (deltas, CHILD_BASE + (long) CATEGORIES * perCategory);
	long start = System.nanoTime (), deadline = start + seconds * 1_000_000_000L;
	for (int k = 0; k < deltas && System.nanoTime () < deadline; k++) {
		long category = stream.random.nextInt (CATEGORIES);
		M3Patch patch = stream.next (category, model.size (category));
		TreeItem top = tree.getTopItem ();
		boolean outside = !tree.getItem ((int) category).getExpanded ();
		labels.tick = k;
		long t0 = System.nanoTime ();
		model.apply (patch, labels);
		stream.nanos [stream.applied++] = System.nanoTime () - t0;
		if (outside && tree.getTopItem () != top) stream.topMoved++;
		if (k % 100 == 99) pump (shell.getDisplay ());
	}
	long total = CATEGORIES;
	for (int c = 0; c < CATEGORIES; c++) total += model.size (c);
	String line = stream.summary ("Tree " + rows, System.nanoTime () - start, labels.asked, (int) total);
	LOG.info (line);
	return line;
}

/* whether a one-row patch lands outside the rows [top, top + visible) of the window */
static boolean outsideWindow (M3Patch patch, int top, int visible) {
	int at = patch.removeRuns () > 0 ? patch.removeAt (0) : patch.insertRuns () > 0 ? patch.insertAt (0) : patch.updateAt (0);
	return at < top || at > top + visible;
}

static void pump (Display display) {
	while (display.readAndDispatch ()) {
		// paint the window and answer SWT.SetData for the rows that show
	}
}
}
