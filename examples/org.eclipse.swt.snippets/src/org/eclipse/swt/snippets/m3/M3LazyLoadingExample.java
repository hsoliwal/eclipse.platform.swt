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
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Lazy loading driven by the viewport's entered and left ranges (brief rules 24 and 25).
 * <p>
 * A <code>SWT.VIRTUAL</code> Table of 1,000,000 rows reads a slow store in pages of 100 rows (80 ms per page, one
 * page at a time). The rows that enter the visible range request their pages; the rows that leave it withdraw the
 * requests the store has not served yet, so a fast scroll does not queue the pages it flew over. A row shows
 * "loading" until its page arrives; then only the rows of that page that are still visible are cleared, and
 * <code>SWT.SetData</code> fills them from the page. Nothing is polled: no timer reads <code>getTopIndex</code>.
 * </p>
 * <p>
 * The ranges come from <code>M3VisibilityObserver</code> (<code>ENTERED</code> / <code>LEFT</code>). The core viewport
 * listener (<code>SWT.Viewport</code>, whose event carries the same entered and left ranges) replaces it at the one
 * line marked below; see "Viewport events and part visibility" in <code>docs/m3/ENHANCED_API.md</code>.
 * </p>
 * <p>
 * Arguments: <code>[exit]</code>: scroll through the table in 60 steps (jumps and small moves), wait for the store,
 * log the counts and close (the hidden-desktop runs of <code>docs/m3/enhanced-api</code>).
 * </p>
 */
public class M3LazyLoadingExample {

static final Logger LOG = Logger.getLogger (M3LazyLoadingExample.class.getName ());
static final int ROWS = 1_000_000, PAGE = 100, PAGES = ROWS / PAGE;

/* the pages: requested and not served, served; the served pages' rows (one lane per page, dropped never) */
static final BitSet requested = new BitSet (PAGES), served = new BitSet (PAGES);
static final Map<Integer, Future<?>> pending = new HashMap<> ();
static final String [][] pages = new String [PAGES] [];
static int requests, withdrawn, arrived;

public static void main (String [] args) throws Exception {
	boolean exit = Arrays.asList (args).contains ("exit");
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3 lazy loading: pages requested by the rows that enter the view");
	shell.setLayout (new GridLayout (1, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	table.setHeaderVisible (true);
	for (String title : new String [] {"Row", "Product"}) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText (title);
		column.setWidth (160);
	}
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
	ExecutorService store = Executors.newSingleThreadExecutor (runnable -> {
		Thread thread = new Thread (runnable, "slow store");
		thread.setDaemon (true);
		return thread;
	});
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		String [] page = pages [event.index / PAGE];
		item.setText (new String [] {Integer.toString (event.index), page == null ? "loading" : page [event.index % PAGE]});
	});
	/* the one line the core viewport listener replaces: table.addListener (SWT.Viewport, ...) */
	M3VisibilityObserver.addListener (table, event -> {
		if (event.detail == M3VisibilityObserver.ENTERED) entered (table, store, event.start, event.end);
		if (event.detail == M3VisibilityObserver.LEFT) left (table, event.start, event.end);
		status.setText (String.format (Locale.ROOT, "%,d pages requested, %,d withdrawn before the store served them, %,d served",
			requests, withdrawn, arrived));
	});
	table.setItemCount (ROWS);
	shell.setSize (420, 520);
	shell.open ();
	if (exit) {
		Random random = new Random (7);
		for (int step = 0; step < 60; step++) {
			int top = step % 10 == 0 ? random.nextInt (ROWS) : Math.min (ROWS - 1, table.getTopIndex () + random.nextInt (40));
			table.setTopIndex (top);
			pump (display, 30);
		}
		pump (display, 2000);
		LOG.info (String.format (Locale.ROOT, "%,d pages requested, %,d withdrawn, %,d served, %,d of %,d pages read; visible rows loaded: %b",
			requests, withdrawn, arrived, served.cardinality (), PAGES, visibleLoaded (table)));
		display.dispose ();
		return;
	}
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

/* the rows start..end entered the view: request the pages that are neither served nor requested */
static void entered (Table table, ExecutorService store, int start, int end) {
	for (int page = start / PAGE; page <= end / PAGE; page++) {
		if (served.get (page) || requested.get (page)) continue;
		requested.set (page);
		requests++;
		int p = page;
		Display display = table.getDisplay ();
		pending.put (page, store.submit (() -> {
			sleep (80);
			String [] rows = new String [PAGE];
			for (int k = 0; k < PAGE; k++) rows [k] = "product " + (p * PAGE + k);
			display.asyncExec (() -> arrive (table, p, rows));
		}));
	}
}

/* the rows start..end left the view: withdraw the requests of pages that have no visible row any more */
static void left (Table table, int start, int end) {
	M3ItemRange visible = M3ItemSelection.getVisibleRange (table);
	for (int page = start / PAGE; page <= end / PAGE; page++) {
		boolean stillVisible = !visible.isEmpty () && page * PAGE <= visible.end () && page * PAGE + PAGE - 1 >= visible.start ();
		if (stillVisible || !requested.get (page)) continue;
		Future<?> future = pending.remove (page);
		if (future != null && future.cancel (false)) {
			requested.clear (page);
			withdrawn++;
		}
	}
}

/* a page arrived: only its rows that are visible now are cleared and asked again */
static void arrive (Table table, int page, String [] rows) {
	if (table.isDisposed ()) return;
	pending.remove (page);
	requested.clear (page);
	served.set (page);
	pages [page] = rows;
	arrived++;
	M3ItemRange visible = M3ItemSelection.getVisibleRange (table);
	int from = Math.max (page * PAGE, visible.isEmpty () ? 0 : visible.start ());
	int to = Math.min (page * PAGE + PAGE - 1, visible.isEmpty () ? -1 : visible.end ());
	if (from <= to) table.clear (from, to);
}

static boolean visibleLoaded (Table table) {
	M3ItemRange visible = M3ItemSelection.getVisibleRange (table);
	for (int row = visible.start (); row <= visible.end (); row++) {
		if (!served.get (row / PAGE)) return false;
	}
	return true;
}

static void sleep (int millis) {
	try {
		Thread.sleep (millis);
	} catch (InterruptedException e) {
		Thread.currentThread ().interrupt ();
	}
}

static void pump (Display display, int millis) {
	long end = System.currentTimeMillis () + millis;
	while (System.currentTimeMillis () < end) {
		if (!display.readAndDispatch ()) {
			display.timerExec (10, () -> {});
			display.sleep ();
		}
	}
}
}
