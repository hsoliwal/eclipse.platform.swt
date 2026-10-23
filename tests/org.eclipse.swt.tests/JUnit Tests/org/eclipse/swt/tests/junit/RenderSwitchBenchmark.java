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

import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_internal_render_RowRenderer.Recorder;

/**
 * The cost of the first frame after a theme or look and feel switch (optimization pass, leaf "switch repaint"), headless:
 * a switch makes a new {@link RowRenderer}; its first row (expander and check box from the SVG glyph set) is timed, over
 * <code>switches</code> switches between three themes of different tints at one zoom, after a warm-up. Prints one TSV row:
 * the median and p90 first-row time and the glyph images drawn. Uses only API present before the shared glyph cache, so
 * it runs against the build before and after. Usage: <code>RenderSwitchBenchmark [switches] [zoom]</code>.
 */
public final class RenderSwitchBenchmark {
	public static void main (String [] args) {
		int switches = args.length > 0 ? Integer.parseInt (args [0]) : 300, zoom = args.length > 1 ? Integer.parseInt (args [1]) : 150;
		Theme [] themes = {
			Theme.SWING.derive ("switch-a").vectorGlyphs (true).border (0x335577).build (),
			Theme.SWING.derive ("switch-b").vectorGlyphs (true).border (0x775533).build (),
			Theme.INITIATIVE31.derive ("switch-c").vectorGlyphs (true).border (0x557733).build ()};
		RowPaint row = Test_org_eclipse_swt_internal_render_RowRenderer.row (RowPaint.CHILDREN | RowPaint.EXPANDED | RowPaint.CHECKBOX | RowPaint.CHECKED);
		row.zoom = zoom;
		row.expanderWidth = 40;
		row.checkX = 40;
		row.checkWidth = 40;
		Recorder canvas = new Recorder ();
		for (int i = 0; i < 30; i++) new RowRenderer (themes [i % themes.length]).paint (canvas, row);
		long [] nanos = new long [switches];
		for (int i = 0; i < switches; i++) {
			RowRenderer renderer = new RowRenderer (themes [i % themes.length]);
			long start = System.nanoTime ();
			renderer.paint (canvas, row);
			nanos [i] = System.nanoTime () - start;
		}
		Arrays.sort (nanos);
		System.out.printf (Locale.ROOT, "switches\t%d\tzoom\t%d\tfirst_row_median_us\t%.1f\tp90_us\t%.1f\timages\t%d%n", switches, zoom, nanos [switches / 2] / 1e3,
			nanos [switches * 9 / 10] / 1e3, canvas.count ("imageData"));
	}
}
