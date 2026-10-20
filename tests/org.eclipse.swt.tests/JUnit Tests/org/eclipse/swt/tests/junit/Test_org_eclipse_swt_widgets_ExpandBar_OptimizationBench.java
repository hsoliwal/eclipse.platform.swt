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

import java.util.ArrayList;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Optimization pass bench of the ExpandBar (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): one bar of 400 x 600
 * with n items of height 40, every other expanded, one <code>Label</code> control every 1000th item (the per-process
 * window quota allows no more at 10^5); the hot operations are expanding or collapsing the first item (a layout from
 * item 0), scrolling a page (<code>WM_VSCROLL SB_PAGEDOWN</code>), changing the height of the first item, 100 mouse
 * moves over the headers (<code>WM_MOUSEMOVE</code>, hover tracking), and appending one item. Opt-in: <code>-Dswt.allwidgets.bench=true</code>; sizes <code>-Dswt.allwidgets.bench.expandbar=10000,100000</code>.
 * Win32 only (scrolling is driven with window messages).
 */
public class Test_org_eclipse_swt_widgets_ExpandBar_OptimizationBench {
	static final int CONTROL_EVERY = 1000;

	@Test
	public void measure () {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "scrolling is driven with win32 messages");
		Display display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		for (int count : AllWidgetsBench.counts ("swt.allwidgets.bench.expandbar", "10000,100000")) {
			Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
			shell.setBounds (40, 40, 400, 600);
			shell.setLayout (new FillLayout ());
			ExpandBar bar = new ExpandBar (shell, SWT.V_SCROLL);
			shell.open ();
			ViewportDifferential.drain (display);
			long start = System.nanoTime ();
			for (int i = 0; i < count; i++) append (bar, i);
			ViewportDifferential.drain (display);
			long create = (System.nanoTime () - start) / 1_000_000;
			java.util.List<AllWidgetsBench.Result> results = new ArrayList<> ();
			ExpandItem first = bar.getItem (0);
			results.add (AllWidgetsBench.measure ("expand", k -> {
				first.setExpanded (!first.getExpanded ());
				ViewportDifferential.drain (display);
				bar.update ();
			}));
			if (!first.getExpanded ()) {
				first.setExpanded (true); // a height change lays out the later items only when the item is expanded
				ViewportDifferential.drain (display);
			}
			results.add (AllWidgetsBench.measure ("height", k -> {
				first.setHeight (40 + (k & 1) * 8);
				ViewportDifferential.drain (display);
				bar.update ();
			}));
			results.add (AllWidgetsBench.measure ("scroll", k -> {
				ViewportDifferential.send (bar, 0x115, 3, 0); // WM_VSCROLL SB_PAGEDOWN
				bar.update ();
			}));
			results.add (AllWidgetsBench.measure ("resize", k -> {
				shell.setSize (400 + ((k & 1) == 0 ? 1 : -1) * (1 + k), 600);
				ViewportDifferential.drain (display);
				bar.update ();
			}));
			results.add (AllWidgetsBench.measure ("move", k -> {
				/* 100 WM_MOUSEMOVE messages down the visible headers (hover in and out) */
				for (int m = 0; m < 100; m++) ViewportDifferential.send (bar, 0x200, 0, ((long) (10 + m * 5) << 16) | 50);
			}));
			results.add (AllWidgetsBench.measure ("cursor", k -> {
				/* 100 WM_SETCURSOR (HTCLIENT, from WM_MOUSEMOVE), which Windows sends before every mouse move */
				for (int m = 0; m < 100; m++) ViewportDifferential.send (bar, 0x20, 0, (0x200L << 16) | 1);
			}));
			ViewportDifferential.send (bar, 0x2A3, 0, 0); // WM_MOUSELEAVE: no header hovered
			results.add (AllWidgetsBench.measure ("cursor-none", k -> {
				/* 100 WM_SETCURSOR with no header hovered (the mouse over an item body or the gaps) */
				for (int m = 0; m < 100; m++) ViewportDifferential.send (bar, 0x20, 0, (0x200L << 16) | 1);
			}));
			int [] next = {count};
			results.add (AllWidgetsBench.measure ("append", k -> {
				append (bar, next [0]++);
				ViewportDifferential.drain (display);
			}));
			AllWidgetsBench.log ("expandbar", ViewportDifferential.reference ("ExpandBar"), count, create, results);
			shell.dispose ();
		}
	}

	/* one item: text, height 40, every other expanded, a Label every CONTROL_EVERY items */
	static void append (ExpandBar bar, int i) {
		ExpandItem item = new ExpandItem (bar, SWT.NONE);
		item.setText ("item " + i);
		item.setHeight (40);
		if (i % CONTROL_EVERY == 0) {
			Label label = new Label (bar, SWT.NONE);
			label.setText ("control " + i);
			item.setControl (label);
		}
		item.setExpanded ((i & 1) == 0);
	}
}
