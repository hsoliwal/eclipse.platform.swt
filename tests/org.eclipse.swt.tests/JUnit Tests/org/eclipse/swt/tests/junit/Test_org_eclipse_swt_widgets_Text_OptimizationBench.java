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
 * Optimization pass bench of the multi-line Text (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): one
 * <code>MULTI | V_SCROLL | H_SCROLL</code> Text of 600 x 400 with n lines <code>line &lt;i&gt;</code>. Hot operations:
 * <code>setText</code>, <code>getText</code>, scroll to the middle (<code>setTopIndex</code>), append one line, insert
 * a few characters, paint. Opt-in: <code>-Dswt.allwidgets.bench=true</code>; sizes
 * <code>-Dswt.allwidgets.bench.text=100000,1000000</code>.
 */
public class Test_org_eclipse_swt_widgets_Text_OptimizationBench {

	@Test
	public void measure () {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		Display display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		for (int count : AllWidgetsBench.counts ("swt.allwidgets.bench.text", "100000,1000000")) {
			Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
			shell.setBounds (40, 40, 600, 400);
			shell.setLayout (new FillLayout ());
			Text text = new Text (shell, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
			text.setTextLimit (Text.LIMIT);
			shell.open ();
			ViewportDifferential.drain (display);
			StringBuilder builder = new StringBuilder (count * 12);
			for (int i = 0; i < count; i++) builder.append ("line ").append (i).append ('\n');
			String document = builder.toString ();
			long start = System.nanoTime ();
			text.setText (document);
			ViewportDifferential.drain (display);
			long create = (System.nanoTime () - start) / 1_000_000;
			java.util.List<AllWidgetsBench.Result> results = new ArrayList<> ();
			results.add (AllWidgetsBench.measure ("setText", k -> text.setText (document)));
			int [] length = {0};
			results.add (AllWidgetsBench.measure ("getText", k -> length [0] += text.getText ().length ()));
			results.add (AllWidgetsBench.measure ("top", k -> {
				text.setTopIndex (count / 2 + k * 30);
				text.update ();
			}));
			results.add (AllWidgetsBench.measure ("append", k -> text.append ("appended " + k + "\n")));
			results.add (AllWidgetsBench.measure ("insert", k -> {
				text.setSelection (text.getCharCount () / 2);
				text.insert ("ins" + k);
			}));
			results.add (AllWidgetsBench.measure ("paint", k -> {
				text.redraw ();
				text.update ();
			}));
			AllWidgetsBench.log ("text", ViewportDifferential.reference ("Text") + " chars " + document.length (), count, create, results);
			shell.dispose ();
		}
	}
}
