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
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Optimization pass bench of the OS light/dark switch (FlatLaf #204, docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv):
 * n canvases of 8 x 8 pixels in one shell, each with a delegate that paints with four colour keys and measures with
 * one metric key. The hot operation is the switch the {@link M3SystemTheme.Follower} makes when the preference changes
 * (<code>setLookAndFeel</code> between <code>light</code> and <code>dark</code>, then the paint); the bench also
 * reports the follower's check when nothing changed, and counts the layouts and paints a switch causes (a cheap
 * repaint lays nothing out). Opt-in: <code>-Dswt.allwidgets.bench=true</code>; sizes
 * <code>-Dswt.allwidgets.bench.theme=1000,5000</code> (the per-process window quota caps the count below 10^4).
 */
public class Test_org_eclipse_swt_internal_laf_M3SystemTheme_OptimizationBench {
	static final int BACKGROUND = M3UIKeys.intern ("Table.background", M3UIKeys.COLOR), FOREGROUND = M3UIKeys.intern ("Table.foreground", M3UIKeys.COLOR);
	static final int SELECTION = M3UIKeys.intern ("Table.selectionBackground", M3UIKeys.COLOR), GRID = M3UIKeys.intern ("Table.gridColor", M3UIKeys.COLOR);
	static final int ROW_HEIGHT = M3UIKeys.intern ("Table.rowHeight", M3UIKeys.INTEGER);
	static final int [] PAINT = {BACKGROUND, FOREGROUND, SELECTION, GRID}, METRIC = {ROW_HEIGHT};

	/* the delegate: four colours and one metric, read at install */
	static final class Cell implements M3ComponentUI {
		int background, foreground, selection, grid, rowHeight;

		@Override
		public void installUI (Control control, M3UIDefaults defaults) {
			background = defaults.color (BACKGROUND, 0xFFFFFFFF);
			foreground = defaults.color (FOREGROUND, 0xFF000000);
			selection = defaults.color (SELECTION, 0xFF0078D7);
			grid = defaults.color (GRID, 0xFFC0C0C0);
			rowHeight = defaults.integer (ROW_HEIGHT, 18);
		}

		@Override
		public int [] paintKeys () {
			return PAINT;
		}

		@Override
		public int [] metricKeys () {
			return METRIC;
		}
	}

	@Test
	public void measure () {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		Display display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		for (int count : AllWidgetsBench.counts ("swt.allwidgets.bench.theme", "1000,5000")) {
			M3LookAndFeel.setLookAndFeel ("light");
			Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
			shell.setBounds (40, 40, 800, 600);
			int [] layouts = {0}, paints = {0};
			shell.setLayout (new Layout () {
				@Override
				protected Point computeSize (Composite composite, int wHint, int hHint, boolean flushCache) {
					return new Point (800, 600);
				}

				@Override
				protected void layout (Composite composite, boolean flushCache) {
					layouts [0]++;
				}
			});
			Listener paint = event -> paints [0]++;
			long start = System.nanoTime ();
			for (int i = 0; i < count; i++) {
				Canvas canvas = new Canvas (shell, SWT.NONE);
				canvas.setBounds ((i % 100) * 8, (i / 100) * 8, 8, 8);
				canvas.addListener (SWT.Paint, paint);
				M3LookAndFeel.installUI (canvas, Cell.class, new Cell ());
			}
			shell.open ();
			ViewportDifferential.drain (display);
			long create = (System.nanoTime () - start) / 1_000_000;
			java.util.List<AllWidgetsBench.Result> results = new ArrayList<> ();
			int changed = java.util.Arrays.stream (M3UIDefaults.diff (M3LookAndFeel.defaults ("light"), M3LookAndFeel.defaults ("dark"))).mapToInt (Long::bitCount).sum ();
			layouts [0] = paints [0] = 0;
			results.add (AllWidgetsBench.measure ("switch", k -> {
				M3LookAndFeel.setLookAndFeel ((k & 1) == 0 ? "dark" : "light");
				ViewportDifferential.drain (display);
				shell.update ();
			}));
			int switchLayouts = layouts [0] / AllWidgetsBench.ROUNDS, switchPaints = paints [0] / AllWidgetsBench.ROUNDS;
			/* the switch alone (the diff and the delegates installed again, the widgets invalidated), without the paint */
			results.add (AllWidgetsBench.measure ("update", k -> M3LookAndFeel.setLookAndFeel ((k & 1) == 0 ? "dark" : "light")));
			ViewportDifferential.drain (display);
			M3SystemTheme.Follower follower = M3SystemTheme.follow (display, "light", "dark");
			ViewportDifferential.drain (display);
			results.add (AllWidgetsBench.measure ("check", k -> follower.check ()));
			follower.close ();
			AllWidgetsBench.log ("theme", "keys-changed " + changed + " layouts/switch " + switchLayouts + " paints/switch " + switchPaints, count, create, results);
			shell.dispose ();
			M3LookAndFeel.reset ();
		}
	}
}
