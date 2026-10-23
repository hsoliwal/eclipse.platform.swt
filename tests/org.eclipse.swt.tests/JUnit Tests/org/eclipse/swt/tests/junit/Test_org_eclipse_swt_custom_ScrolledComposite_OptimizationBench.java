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
import org.eclipse.swt.custom.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Optimization pass bench of the scrollable composites (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): a
 * ScrolledComposite of 400 x 600 whose content holds n labels of 20 pixels in a column (n x 20 pixels, beyond the
 * native coordinate range from n = 1639, so the plane geometry of docs/m3/SCROLL_MEDIATED_VIEWPORT.md is exercised).
 * Hot operations: scroll a page (<code>setOrigin</code>), scroll by a scroll bar page (<code>WM_VSCROLL
 * SB_PAGEDOWN</code>), resize. Opt-in: <code>-Dswt.allwidgets.bench=true</code>; sizes
 * <code>-Dswt.allwidgets.bench.scrolled=1000,5000</code> (the per-process window quota caps the count below 10^4).
 */
public class Test_org_eclipse_swt_custom_ScrolledComposite_OptimizationBench {

	@Test
	public void measure () {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "scrolling is driven with win32 messages");
		Display display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		for (int count : AllWidgetsBench.counts ("swt.allwidgets.bench.scrolled", "1000,5000")) {
			Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
			shell.setBounds (40, 40, 400, 600);
			shell.setLayout (new FillLayout ());
			ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.V_SCROLL);
			Composite content = new Composite (scrolled, SWT.NONE);
			long start = System.nanoTime ();
			for (int i = 0; i < count; i++) {
				Label label = new Label (content, SWT.NONE);
				label.setText ("label " + i);
				label.setBounds (4, i * 20, 300, 18);
			}
			content.setSize (380, count * 20);
			scrolled.setContent (content);
			shell.open ();
			ViewportDifferential.drain (display);
			long create = (System.nanoTime () - start) / 1_000_000;
			java.util.List<AllWidgetsBench.Result> results = new ArrayList<> ();
			int page = scrolled.getClientArea ().height;
			results.add (AllWidgetsBench.measure ("origin", k -> {
				scrolled.setOrigin (0, (count * 20) / 2 + k * page);
				ViewportDifferential.drain (display);
				scrolled.update ();
			}));
			results.add (AllWidgetsBench.measure ("scroll", k -> {
				ViewportDifferential.send (scrolled, 0x115, 3, 0); // WM_VSCROLL SB_PAGEDOWN
				ViewportDifferential.drain (display);
				scrolled.update ();
			}));
			results.add (AllWidgetsBench.measure ("resize", k -> {
				shell.setSize (400 + ((k & 1) == 0 ? 1 : -1), 600);
				ViewportDifferential.drain (display);
				scrolled.update ();
			}));
			AllWidgetsBench.log ("scrolled", ViewportDifferential.reference ("Composite"), count, create, results);
			shell.dispose ();
		}
	}
}
