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

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Optimization pass bench of the CTabFolder (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): one folder of
 * 800 x 300 with n tabs. Hot operations: select a far tab, rename the selected tab, resize (layout), one mouse move over
 * the tabs, append one tab. Opt-in: <code>-Dswt.allwidgets.bench=true</code>; sizes
 * <code>-Dswt.allwidgets.bench.ctabfolder=10000,100000</code>.
 */
public class Test_org_eclipse_swt_custom_CTabFolder_OptimizationBench {

	@Test
	public void measure () {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		Display display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		for (int count : AllWidgetsBench.counts ("swt.allwidgets.bench.ctabfolder", "10000,100000")) {
			Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
			shell.setBounds (40, 40, 800, 300);
			shell.setLayout (new FillLayout ());
			CTabFolder folder = new CTabFolder (shell, SWT.CLOSE | SWT.BORDER);
			shell.open ();
			ViewportDifferential.drain (display);
			long start = System.nanoTime ();
			for (int i = 0; i < count; i++) new CTabItem (folder, SWT.NONE).setText ("tab " + i);
			ViewportDifferential.drain (display);
			long create = (System.nanoTime () - start) / 1_000_000;
			java.util.List<AllWidgetsBench.Result> results = new ArrayList<> ();
			Random random = new Random (count);
			results.add (AllWidgetsBench.measure ("select", k -> {
				folder.setSelection (count / 4 + random.nextInt (count / 2));
				ViewportDifferential.drain (display);
				folder.update ();
			}));
			results.add (AllWidgetsBench.measure ("rename", k -> {
				folder.getSelection ().setText ("renamed " + k);
				ViewportDifferential.drain (display);
				folder.update ();
			}));
			results.add (AllWidgetsBench.measure ("resize", k -> {
				shell.setSize (800 + ((k & 1) == 0 ? 1 : -1), 300);
				ViewportDifferential.drain (display);
				folder.update ();
			}));
			Event event = new Event ();
			results.add (AllWidgetsBench.measure ("move", k -> {
				event.x = 5 + k * 97;
				event.y = 10;
				folder.notifyListeners (SWT.MouseMove, event);
			}));
			int [] next = {count};
			results.add (AllWidgetsBench.measure ("append", k -> {
				new CTabItem (folder, SWT.NONE).setText ("tab " + next [0]++);
				ViewportDifferential.drain (display);
			}));
			AllWidgetsBench.log ("ctabfolder", ViewportDifferential.reference ("CTabFolder"), count, create, results);
			shell.dispose ();
		}
	}
}
