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
import org.eclipse.swt.widgets.*;

/**
 * Prefetch and release driven by the viewport (brief rules 24 and 25): a cache that holds the window and nothing
 * else.
 * <p>
 * A <code>SWT.VIRTUAL</code> Table of 1,000,000 rows keeps its row data in blocks of 64 rows. The window is the
 * visible rows widened by 256 rows before and after (<code>M3Prefetch.setWindow</code>, the overscan). Rows that enter
 * the window are loaded block by block before <code>SWT.SetData</code> asks for them (<code>PREFETCH</code>); rows that
 * leave it release their blocks (<code>CANCEL</code>). The cache therefore holds at most the window plus one block at
 * each end, however far the table scrolls, and SetData never misses. The status shows the cached rows, the largest
 * cache seen, and the SetData hits and misses.
 * </p>
 * <p>
 * <code>M3Prefetch</code> is the window arithmetic over the viewport's entered and left ranges; with the core viewport
 * listener (<code>SWT.Viewport</code>) it derives its PREFETCH and CANCEL ranges from the same event, so there is one
 * mechanism (see "Viewport events and part visibility" in <code>docs/m3/ENHANCED_API.md</code>).
 * </p>
 * <p>
 * Arguments: <code>[exit]</code>: scroll through the table in 200 steps, log the counts and close.
 * </p>
 */
public class M3PrefetchReleaseExample {

static final Logger LOG = Logger.getLogger (M3PrefetchReleaseExample.class.getName ());
static final int ROWS = 1_000_000, BLOCK = 64, BEFORE = 256, AFTER = 256;

static final Map<Integer, String []> blocks = new HashMap<> ();
/* the rows of the window, as M3Prefetch announced them */
static final BitSet window = new BitSet (ROWS);
static int cachedRows, largest, hits, misses, prefetched, released;

public static void main (String [] args) {
	boolean exit = Arrays.asList (args).contains ("exit");
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3 prefetch and release: the cache holds the window");
	shell.setLayout (new GridLayout (1, false));
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	Label status = new Label (shell, SWT.NONE);
	status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
	table.addListener (SWT.SetData, event -> {
		String [] block = blocks.get (event.index / BLOCK);
		if (block != null) hits++; else misses++;
		((TableItem) event.item).setText (block != null ? block [event.index % BLOCK] : "row " + event.index + " (miss)");
	});
	M3Prefetch.setWindow (table, BEFORE, AFTER);
	M3Prefetch.addListener (table, event -> {
		if (event.detail == M3Prefetch.PREFETCH) {
			window.set (event.start, event.end + 1);
			for (int block = event.start / BLOCK; block <= event.end / BLOCK; block++) load (block);
		} else {
			window.clear (event.start, event.end + 1);
			release (event.start, event.end);
		}
		largest = Math.max (largest, cachedRows);
		status.setText (String.format (Locale.ROOT, "%,d rows cached (largest %,d), SetData %,d hits, %,d misses", cachedRows, largest, hits, misses));
	});
	table.setItemCount (ROWS);
	shell.setSize (360, 520);
	shell.open ();
	if (exit) {
		Random random = new Random (11);
		for (int step = 0; step < 200; step++) {
			table.setTopIndex (step % 25 == 0 ? random.nextInt (ROWS) : Math.min (ROWS - 1, table.getTopIndex () + 5 + random.nextInt (60)));
			while (display.readAndDispatch ()) {
				// paint the window
			}
		}
		LOG.info (String.format (Locale.ROOT, "%,d blocks prefetched, %,d released; %,d rows cached at the end, largest %,d (bound %,d); SetData %,d hits, %,d misses",
			prefetched, released, cachedRows, largest, bound (table), hits, misses));
		display.dispose ();
		return;
	}
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

static void load (int block) {
	if (blocks.containsKey (block)) return;
	String [] rows = new String [BLOCK];
	for (int k = 0; k < BLOCK; k++) rows [k] = "product " + (block * BLOCK + k);
	blocks.put (block, rows);
	cachedRows += BLOCK;
	prefetched++;
}

/* rows start..end left the window: a block goes when none of its rows is in the window any more */
static void release (int start, int end) {
	for (int block = start / BLOCK; block <= end / BLOCK; block++) {
		int next = window.nextSetBit (block * BLOCK);
		boolean inWindow = next >= 0 && next < block * BLOCK + BLOCK;
		if (!inWindow && blocks.remove (block) != null) {
			cachedRows -= BLOCK;
			released++;
		}
	}
}

/* the window and one block at each end */
static int bound (Table table) {
	return window.cardinality () + 2 * BLOCK;
}
}
