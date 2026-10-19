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

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.Assertions;

/**
 * Scripts of the Table differential at every scale (T-Table-14), on the runtime rescaling of win32
 * (<code>swt.autoScale.updateOnRuntime</code>, on unless set to false; the gate scripts set it to true):
 * <ul>
 * <li>SCALE: a seed runs at one zoom, 100, 125, 150 or 200 % (96, 120, 144 or 192 dpi by seed), set by
 * <code>WM_DPICHANGED</code> right after its shell opens. It draws keys and clicks with Shift and Ctrl, marquee,
 * accessibility and type-ahead, presentation and geometry (fonts, colours and images per row and cell,
 * <code>getItem (Point)</code>, bounds, pixels, resizes while scrolled, column widths, label tips) and the API
 * script, all at that zoom.</li>
 * <li>ZOOM: the resize, font and DPI legs of the List differential in one script: <code>Table.setFont</code> (7, 9,
 * 12 and 16 pt of the system font, and the default font), resizes, top index, keys and changes among the four
 * zooms while scrolled, with reads of bounds, rows at points, the client area and pixels, column widths and the
 * API script between them.</li>
 * </ul>
 * The zoom returns to 100 % before a seed's shell is disposed ({@link TableViewportScripts#tearDown}).
 */
final class TableScaleScripts {
	static final int [] FONT_HEIGHTS = {7, 9, 12, 16};

	private TableScaleScripts () {
	}

	/* SCALE: the seed's zoom, once its shell is open */
	static void opened (Scene scene) {
		if (scene.variant.script () != Test_org_eclipse_swt_widgets_Table_ViewportDifferential.SCALE) return;
		Assertions.assertTrue (scene.display.isRescalingAtRuntime (), "the SCALE variant needs runtime rescaling (swt.autoScale.updateOnRuntime)");
		int dpi = TableViewportScripts.DPIS [scene.seed % TableViewportScripts.DPIS.length];
		if (dpi != 96) TableViewportScripts.changeDpi (scene, dpi);
	}

	/* ZOOM: the Table fonts of the font leg, made per seed and disposed with the seed's other resources */
	static Font [] fonts (Display display) {
		String name = display.getSystemFont ().getFontData () [0].getName ();
		return Arrays.stream (FONT_HEIGHTS).mapToObj (height -> new Font (display, name, height, SWT.NORMAL)).toArray (Font []::new);
	}

	static String scale (Scene scene, int kind, int fraction, int pick, int span, int delta) {
		/* input kinds 0..4 are keys and 5, 6 clicks; 9 is marquee, accessibility or type-ahead */
		if (kind < 7) return TableViewportScripts.input (scene, kind, fraction, pick, span, delta);
		if (kind == 7) return TableViewportScripts.input (scene, 9, fraction, pick, span, delta);
		if (kind < 12) return TableViewportScripts.geometry (scene, (pick >> 4) % 11, fraction, pick, span, delta);
		return TableViewportScripts.structure (scene, fraction, pick, span, delta);
	}

	static String zoom (Scene scene, int kind, int fraction, int pick, int span, int delta) {
		return switch (kind) {
			/* the DPI script: 2 sets the top index, 4 resizes, 0 changes the zoom among 96, 120, 144 and 192 dpi */
			case 0, 1 -> TableViewportScripts.dpi (scene, 2, fraction, pick, span, delta);
			case 2 -> TableViewportScripts.input (scene, pick % 5, fraction, pick, span, delta);
			case 3, 4 -> TableViewportScripts.dpi (scene, 4, fraction, pick, span, delta);
			case 5, 6 -> font (scene, pick);
			case 7 -> TableViewportScripts.dpi (scene, 0, fraction, pick, span, delta);
			/* the geometry script: 5 asks the row at a point, 6 reads bounds, 7 the client area or pixels, 9 sets a column width */
			case 8 -> TableViewportScripts.geometry (scene, 5 + pick % 2, fraction, pick, span, delta);
			case 9 -> TableViewportScripts.geometry (scene, 7, fraction, pick, span, delta);
			case 10 -> TableViewportScripts.geometry (scene, 9, fraction, pick, span, delta);
			default -> TableViewportScripts.structure (scene, fraction, pick, span, delta);
		};
	}

	static String font (Scene scene, int pick) {
		Table table = scene.table;
		Font [] fonts = scene.state.fonts;
		int i = pick % (fonts.length + 1);
		table.setFont (i < fonts.length ? fonts [i] : null);
		return "table font " + (i < fonts.length ? FONT_HEIGHTS [i] + " pt" : "default") + " -> itemHeight " + table.getItemHeight () + " header "
				+ table.getHeaderHeight () + " top " + table.getTopIndex ();
	}
}
