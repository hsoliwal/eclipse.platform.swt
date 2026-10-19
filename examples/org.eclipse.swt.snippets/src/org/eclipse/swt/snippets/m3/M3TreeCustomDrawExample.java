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
 * Tree viewport, example 2: custom draw on a virtual Tree of 1,000,000 roots.
 * <p>
 * The three custom draw events of the Tree (the pattern of Snippet227 and Snippet239) on a huge tree:
 * <code>SWT.MeasureItem</code> makes the rows taller and wider, <code>SWT.EraseItem</code> paints a gradient
 * behind selected rows and takes the selection over, and <code>SWT.PaintItem</code> draws a status dot before the
 * name and a progress bar in the second column. Custom draw events come only for rows that are painted, so their
 * cost does not grow with the tree; in the default mode (viewport) the measured rows are only the ones in view.
 * "Taller rows" grows the item height at run time (a MeasureItem height above the current one): the rows change at
 * once and the scroll bar follows at the next scroll, as on stock SWT.
 * </p>
 */
public class M3TreeCustomDrawExample {

static final Logger LOG = Logger.getLogger (M3TreeCustomDrawExample.class.getName ());
static final int ROOTS = 1_000_000, CHILDREN = 50;
static int rowHeight = 24;

public static void main (String [] args) {
	Display display = new Display ();
	Shell shell = open (display);
	while (!shell.isDisposed ()) {
		if (!display.readAndDispatch ()) display.sleep ();
	}
	display.dispose ();
}

/** Builds and opens the example's shell (the screenshot harness of docs/m3/tree-viewport calls it too). */
public static Shell open (Display display) {
	Shell shell = new Shell (display);
	shell.setText ("M3 Tree: custom draw on 1,000,000 roots");
	shell.setLayout (new GridLayout (3, false));
	Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL | SWT.FULL_SELECTION);
	tree.setHeaderVisible (true);
	for (String title : new String [] {"Task", "Progress"}) {
		TreeColumn column = new TreeColumn (tree, SWT.NONE);
		column.setText (title);
		column.setWidth (200);
	}
	GridData treeData = new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1);
	treeData.widthHint = 440;
	treeData.heightHint = 360;
	tree.setLayoutData (treeData);
	Label status = new Label (shell, SWT.NONE);
	button (shell, "Middle", () -> {
		TreeItem item = tree.getItem (ROOTS / 2);
		item.setExpanded (true);
		tree.setTopItem (item);
		tree.setSelection (item.getItem (2));
	});
	button (shell, "Taller rows", () -> {
		rowHeight += 4;
		tree.redraw ();
		tree.update ();
		LOG.info (() -> "row height asked " + rowHeight + ", item height " + tree.getItemHeight ());
	});
	status.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
	status.moveBelow (null);
	int [] measured = new int [1];
	tree.addListener (SWT.SetData, event -> fill ((TreeItem) event.item, event.index));
	tree.addListener (SWT.MeasureItem, event -> {
		measured [0]++;
		event.height = Math.max (event.height, rowHeight);
		if (event.index == 1) event.width = 180;
	});
	tree.addListener (SWT.EraseItem, event -> {
		if ((event.detail & SWT.SELECTED) == 0) return;
		GC gc = event.gc;
		Rectangle area = tree.getClientArea ();
		Color old = gc.getBackground ();
		gc.setForeground (display.getSystemColor (SWT.COLOR_LIST_SELECTION));
		gc.setBackground (display.getSystemColor (SWT.COLOR_LIST_BACKGROUND));
		gc.fillGradientRectangle (0, event.y, area.width, event.height, false);
		gc.setBackground (old);
		event.detail &= ~SWT.SELECTED;
	});
	tree.addListener (SWT.PaintItem, event -> {
		TreeItem item = (TreeItem) event.item;
		int percent = progress (item);
		GC gc = event.gc;
		if (event.index == 0) {
			gc.setBackground (display.getSystemColor (percent == 100 ? SWT.COLOR_DARK_GREEN : percent > 50 ? SWT.COLOR_DARK_YELLOW : SWT.COLOR_RED));
			gc.fillOval (event.x - 10, event.y + event.height / 2 - 3, 6, 6);
		} else {
			int width = 160 * percent / 100;
			gc.setBackground (display.getSystemColor (SWT.COLOR_DARK_BLUE));
			gc.fillRectangle (event.x + 4, event.y + 4, width, event.height - 8);
			gc.setForeground (display.getSystemColor (SWT.COLOR_DARK_GRAY));
			gc.drawRectangle (event.x + 4, event.y + 4, 160, event.height - 9);
			gc.setForeground (display.getSystemColor (percent > 45 ? SWT.COLOR_WHITE : SWT.COLOR_BLACK));
			gc.drawText (percent + "%", event.x + 70, event.y + (event.height - gc.getFontMetrics ().getHeight ()) / 2, true);
		}
	});
	long start = System.nanoTime ();
	tree.setItemCount (ROOTS);
	long nanos = System.nanoTime () - start;
	shell.pack ();
	shell.open ();
	tree.update ();
	status.setText (String.format (Locale.ROOT, "%,d roots in %.1f ms; %d MeasureItem events for the first paint", ROOTS, nanos / 1e6, measured [0]));
	LOG.info (status.getText ());
	return shell;
}

static void fill (TreeItem item, int index) {
	TreeItem parent = item.getParentItem ();
	item.setText (0, parent == null ? String.format (Locale.ROOT, "task %,d", index) : parent.getText () + " / step " + index);
	if (parent == null && index % 4 == 0) item.setItemCount (CHILDREN);
}

/* a progress figure that follows the row's text, so a row paints the same wherever it is */
static int progress (TreeItem item) {
	return Math.floorMod (item.getText ().hashCode (), 101);
}

static void button (Shell shell, String text, Runnable action) {
	Button button = new Button (shell, SWT.PUSH);
	button.setText (text);
	button.addListener (SWT.Selection, event -> action.run ());
}
}
