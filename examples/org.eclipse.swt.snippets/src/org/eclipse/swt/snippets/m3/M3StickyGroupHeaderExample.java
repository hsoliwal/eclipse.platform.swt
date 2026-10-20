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
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * A sticky group header driven by the viewport (brief rules 24 and 25).
 * <p>
 * A <code>SWT.VIRTUAL</code> Table of 1,000,000 rows is sorted into 1,301 groups of different sizes. The strip above
 * the table always names the group of the first visible row, like a pinned section header: when a group's first row
 * scrolls past the top, the strip switches to it. The group starts are one <code>int</code> lane, and the group of a
 * row is a binary search over it, so the strip costs O(log groups) per viewport change and is redrawn only when the
 * group changes. The first row of each group is drawn bold in the table.
 * </p>
 * <p>
 * The first visible row comes from the viewport's ranges (<code>M3VisibilityObserver</code> today; the core viewport
 * listener <code>SWT.Viewport</code> carries the first visible row directly, see "Viewport events and part
 * visibility" in <code>docs/m3/ENHANCED_API.md</code>).
 * </p>
 * <p>
 * Arguments: <code>[exit]</code>: scroll through the table in 120 steps, check the strip against the first visible
 * row after each, log the counts and close.
 * </p>
 */
public class M3StickyGroupHeaderExample {

static final Logger LOG = Logger.getLogger (M3StickyGroupHeaderExample.class.getName ());
static final int ROWS = 1_000_000;

/* group g holds the rows starts [g] .. starts [g + 1] - 1 */
static final int [] starts = groups ();
static int shownGroup = -1, redraws, viewportChanges;

static int [] groups () {
	int [] lane = new int [2048];
	int count = 0;
	for (int row = 0; row < ROWS; row += 200 + (count * 397) % 1300) {
		if (count == lane.length) lane = Arrays.copyOf (lane, count * 2);
		lane [count++] = row;
	}
	int [] result = Arrays.copyOf (lane, count + 1);
	result [count] = ROWS;
	return result;
}

static int groupOf (int row) {
	int index = Arrays.binarySearch (starts, 0, starts.length - 1, row);
	return index >= 0 ? index : -index - 2;
}

public static void main (String [] args) {
	boolean exit = Arrays.asList (args).contains ("exit");
	Display display = new Display ();
	Shell shell = new Shell (display);
	shell.setText ("M3 sticky group header: " + (starts.length - 1) + " groups, 1,000,000 rows");
	shell.setLayout (new GridLayout (1, false));
	Label strip = new Label (shell, SWT.BORDER);
	strip.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
	strip.setBackground (display.getSystemColor (SWT.COLOR_INFO_BACKGROUND));
	Font bold = new Font (display, display.getSystemFont ().getFontData () [0].getName (), 9, SWT.BOLD);
	strip.setFont (bold);
	Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
	table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	table.addListener (SWT.SetData, event -> {
		TableItem item = (TableItem) event.item;
		int group = groupOf (event.index);
		boolean first = starts [group] == event.index;
		item.setText (first ? "Group " + group + " (" + (starts [group + 1] - starts [group]) + " rows)" : "  row " + event.index);
		if (first) item.setFont (bold);
	});
	M3VisibilityObserver.addListener (table, event -> {
		viewportChanges++;
		M3ItemRange visible = M3ItemSelection.getVisibleRange (table);
		int group = visible.isEmpty () ? -1 : groupOf (visible.start ());
		if (group == shownGroup) return;
		shownGroup = group;
		redraws++;
		strip.setText (group < 0 ? "" : "Group " + group + ": rows " + starts [group] + " to " + (starts [group + 1] - 1));
	});
	table.setItemCount (ROWS);
	shell.setSize (360, 520);
	shell.open ();
	if (exit) {
		Random random = new Random (3);
		int wrong = 0;
		for (int step = 0; step < 120; step++) {
			table.setTopIndex (step % 20 == 0 ? random.nextInt (ROWS) : Math.min (ROWS - 1, table.getTopIndex () + random.nextInt (400)));
			while (display.readAndDispatch ()) {
				// paint the window
			}
			if (shownGroup != groupOf (M3ItemSelection.getVisibleRange (table).start ())) wrong++;
		}
		LOG.info (String.format (Locale.ROOT, "%d groups; %d viewport changes, %d strip redraws, %d steps where the strip named another group than the first visible row's",
			starts.length - 1, viewportChanges, redraws, wrong));
		bold.dispose ();
		display.dispose ();
		return;
	}
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	bold.dispose ();
	display.dispose ();
}
}
