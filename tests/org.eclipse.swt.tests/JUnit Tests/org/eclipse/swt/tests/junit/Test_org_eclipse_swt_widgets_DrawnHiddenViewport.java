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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.List;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 24, hidden viewports, in the drawn modes: a Tree, a Table and a List of 10^4 rows are hidden
 * (<code>setVisible (false)</code>), take 10^4 model deltas (text changes of the first rows, inserts and removes across
 * the model), and are shown again. Gate:
 * <ul>
 * <li>while hidden the plane paints nothing, and no message reaches the native window procedure (the deltas cause no
 * native work: the widget's viewport only marks its window dirty);</li>
 * <li>on show the plane paints exactly once, drawing or keeping every visible row;</li>
 * <li>the rows it kept are the rows it would draw (the capture equals the capture after the plane forgot every row).</li>
 * </ul>
 * Each widget logs its counts (messages while hidden, frames while hidden, frames on show, rows drawn and kept).
 */
public class Test_org_eclipse_swt_widgets_DrawnHiddenViewport {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_DrawnHiddenViewport.class.getName ());
	static final int ROWS = 10_000, DELTAS = 10_000;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 360, 420);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	/* the delta i of a model of n rows: 0 text of a first row, 1 insert, 2 remove, 3 text of a first row */
	interface Deltas {
		void text (int at, String text);
		void insert (int at, String text);
		void remove (int at);
	}

	static void apply (Deltas deltas, int rows) {
		int n = rows;
		for (int i = 0; i < DELTAS; i++) {
			switch (i & 3) {
				case 0 -> deltas.text (i % 50, "t" + i);
				case 1 -> {
					deltas.insert ((i * 7) % n, "n" + i);
					n++;
				}
				case 2 -> {
					deltas.remove ((i * 13) % n);
					n--;
				}
				default -> deltas.text (i % 50 + 1, "u" + i);
			}
		}
	}

	void gate (Control control, Deltas deltas) {
		Assumptions.assumeTrue (Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.log (control) != null, "a drawn mode (custom, m3, gpu) only");
		control.setBounds (5, 5, 340, 380);
		shell.open ();
		ViewportDifferential.settle (display, 300);
		control.update ();
		control.setVisible (false);
		ViewportDifferential.settle (display, 60);
		FrameLog log = Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.log (control);
		long messages = log.messages (), frames = log.frames ();
		long start = System.nanoTime ();
		apply (deltas, ROWS);
		long deltaNanos = System.nanoTime () - start;
		ViewportDifferential.settle (display, 60);
		long hiddenMessages = log.messages () - messages, hiddenFrames = log.frames () - frames;
		long shown = log.frames (), drawn = log.rowsDrawn (), kept = log.rowsKept ();
		start = System.nanoTime ();
		control.setVisible (true);
		control.update ();
		long showNanos = System.nanoTime () - start;
		ViewportDifferential.settle (display, 100);
		control.update ();
		FrameLog after = Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.log (control);
		long showFrames = after == log ? log.frames () - shown : -1;
		long rowsDrawn = log.rowsDrawn () - drawn, rowsKept = log.rowsKept () - kept;
		String name = control.getClass ().getSimpleName () + " " + ViewportDifferential.reference (control.getClass ().getSimpleName ());
		String line = String.format (Locale.ROOT, "%s: %d deltas while hidden in %.1f ms, %d messages and %d frames while hidden; show in %.1f ms, %d frames, %d rows drawn, %d kept",
			name, DELTAS, deltaNanos / 1e6, hiddenMessages, hiddenFrames, showNanos / 1e6, showFrames, rowsDrawn, rowsKept);
		LOG.info ("drawn hidden viewport: " + line);
		assertEquals (0, hiddenFrames, "hidden viewports: the plane painted while hidden: " + line);
		assertEquals (1, showFrames, "hidden viewports: one paint pass on show: " + line);
		assertTrue (rowsDrawn + rowsKept > 0, "the show frame painted the visible rows: " + line);
		keptRowsAreTheDrawnRows (display, control);
		assertEquals (0, hiddenMessages, "hidden viewports: the deltas made native work while hidden (the widget's viewport owns it): " + line);
	}

	/* the capture with kept rows equals the capture after the plane forgot every row and drew them all */
	static void keptRowsAreTheDrawnRows (Display display, Control control) {
		ImageData kept = ViewportDifferential.render (control);
		control.setFont (control.getFont ());
		control.redraw ();
		control.update ();
		ViewportDifferential.settle (display, 60);
		ImageData drawn = ViewportDifferential.render (control);
		String name = control.getClass ().getSimpleName () + "-" + ViewportDifferential.reference (control.getClass ().getSimpleName ());
		if (!Arrays.equals (kept.data, drawn.data)) {
			ViewportDifferential.save ("hidden-kept-" + name, kept);
			ViewportDifferential.save ("hidden-drawn-" + name, drawn);
			fail ("rule 24: a kept row differs from the drawn row (hidden-kept-/hidden-drawn-" + name + ".png)");
		}
	}

	@Test
	public void treeHiddenDeltasPaintOnceOnShow () {
		Tree tree = new Tree (shell, SWT.MULTI | SWT.BORDER);
		List<TreeItem> items = new ArrayList<> (ROWS + DELTAS);
		for (int i = 0; i < ROWS; i++) {
			TreeItem item = new TreeItem (tree, SWT.NONE);
			item.setText ("row " + i);
			items.add (item);
		}
		gate (tree, new Deltas () {
			@Override
			public void text (int at, String text) {
				items.get (at).setText (text);
			}

			@Override
			public void insert (int at, String text) {
				TreeItem item = new TreeItem (tree, SWT.NONE, at);
				item.setText (text);
				items.add (at, item);
			}

			@Override
			public void remove (int at) {
				items.remove (at).dispose ();
			}
		});
	}

	@Test
	public void tableHiddenDeltasPaintOnceOnShow () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		List<TableItem> items = new ArrayList<> (ROWS + DELTAS);
		for (int i = 0; i < ROWS; i++) {
			TableItem item = new TableItem (table, SWT.NONE);
			item.setText ("row " + i);
			items.add (item);
		}
		gate (table, new Deltas () {
			@Override
			public void text (int at, String text) {
				items.get (at).setText (text);
			}

			@Override
			public void insert (int at, String text) {
				TableItem item = new TableItem (table, SWT.NONE, at);
				item.setText (text);
				items.add (at, item);
			}

			@Override
			public void remove (int at) {
				items.remove (at);
				table.remove (at);
			}
		});
	}

	@Test
	public void listHiddenDeltasPaintOnceOnShow () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		String [] rows = new String [ROWS];
		Arrays.setAll (rows, i -> "row " + i);
		list.setItems (rows);
		gate (list, new Deltas () {
			@Override
			public void text (int at, String text) {
				list.setItem (at, text);
			}

			@Override
			public void insert (int at, String text) {
				list.add (text, at);
			}

			@Override
			public void remove (int at) {
				list.remove (at);
			}
		});
	}
}
