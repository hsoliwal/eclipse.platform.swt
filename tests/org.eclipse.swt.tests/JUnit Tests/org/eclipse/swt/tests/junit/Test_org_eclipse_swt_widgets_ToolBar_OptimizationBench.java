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
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Optimization pass bench of the native ToolBar (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): one
 * <code>FLAT | RIGHT</code> bar with n items (every seventh a separator, the others with text). Hot operations:
 * append one item, rename a middle item, <code>computeSize</code>, <code>indexOf</code> the last item, paint.
 * Opt-in: <code>-Dswt.allwidgets.bench=true</code>; sizes <code>-Dswt.allwidgets.bench.toolbar=2000,10000</code>.
 */
public class Test_org_eclipse_swt_widgets_ToolBar_OptimizationBench {

	@Test
	public void measure () {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		Display display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		for (int count : AllWidgetsBench.counts ("swt.allwidgets.bench.toolbar", "2000,10000")) {
			Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
			shell.setBounds (40, 40, 800, 120);
			ToolBar bar = new ToolBar (shell, SWT.FLAT | SWT.RIGHT);
			bar.setBounds (0, 0, 800, 40);
			shell.open ();
			ViewportDifferential.drain (display);
			long start = System.nanoTime ();
			for (int i = 0; i < count; i++) append (bar, i);
			ViewportDifferential.drain (display);
			long create = (System.nanoTime () - start) / 1_000_000;
			java.util.List<AllWidgetsBench.Result> results = new ArrayList<> ();
			int [] next = {count};
			results.add (AllWidgetsBench.measure ("append", k -> {
				append (bar, next [0]++ * 7 + 1); // never a separator
				ViewportDifferential.drain (display);
			}));
			ToolItem middle = bar.getItem (count / 2 + (count / 2 % 7 == 0 ? 1 : 0));
			results.add (AllWidgetsBench.measure ("rename", k -> {
				middle.setText ("renamed " + k);
				ViewportDifferential.drain (display);
			}));
			results.add (AllWidgetsBench.measure ("computeSize", k -> bar.computeSize (SWT.DEFAULT, SWT.DEFAULT, true)));
			ToolItem last = bar.getItem (bar.getItemCount () - 1);
			results.add (AllWidgetsBench.measure ("indexOf", k -> bar.indexOf (last)));
			results.add (AllWidgetsBench.measure ("paint", k -> {
				bar.redraw ();
				bar.update ();
			}));
			AllWidgetsBench.log ("toolbar", ViewportDifferential.reference ("ToolBar"), count, create, results);
			shell.dispose ();
		}
	}

	static void append (ToolBar bar, int i) {
		if (i % 7 == 0) {
			new ToolItem (bar, SWT.SEPARATOR);
			return;
		}
		ToolItem item = new ToolItem (bar, SWT.PUSH);
		item.setText ("item " + i);
		item.setToolTipText ("tip " + i);
	}
}
