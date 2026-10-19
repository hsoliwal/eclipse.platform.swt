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

import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_internal_render_RowRenderer.Recorder;
import org.junit.jupiter.api.*;

/**
 * The vector glyph path of the row renderer (leaf SV2/SV3, headless): a <code>vectorGlyphs</code> theme draws its
 * expander and check box from the SVG set at the row's zoom (16 points: 16, 24, 32 pixels), centred in the slot, one
 * rasterization per glyph and zoom; without an SVG rasterizer on the class path the look draws its own glyphs.
 */
public class Test_org_eclipse_swt_internal_render_VectorGlyphs {
	static final boolean SVG = Glyphs.rasterizer (glyph -> 0) != null;

	static RowPaint row (int zoom) {
		RowPaint row = Test_org_eclipse_swt_internal_render_RowRenderer.row (RowPaint.CHILDREN | RowPaint.EXPANDED | RowPaint.CHECKBOX | RowPaint.CHECKED);
		row.zoom = zoom;
		row.expanderWidth = 40;
		row.checkX = 40;
		row.checkWidth = 40;
		return row;
	}

	@Test
	public void vectorGlyphsAtTheZoomOrTheLooksOwn () {
		Theme theme = Theme.SWING.derive ("vector").vectorGlyphs (true).build ();
		assertTrue (theme.vectorGlyphs);
		assertFalse (Theme.SWING.vectorGlyphs, "the built-in themes keep their looks");
		RowRenderer renderer = new RowRenderer (theme);
		for (int zoom : new int [] {100, 150, 200}) {
			Recorder canvas = new Recorder ();
			renderer.paint (canvas, row (zoom));
			int size = 16 * zoom / 100, inset = (40 - size) / 2, top = 18 + (18 - size) / 2;
			if (SVG) {
				assertTrue (canvas.has ("imageData " + size + " " + inset + "," + top + " " + size + "x" + size), zoom + ": the expander " + canvas.calls);
				assertTrue (canvas.has ("imageData " + size + " " + (40 + inset) + "," + top + " " + size + "x" + size), zoom + ": the check box");
				assertEquals (2, canvas.count ("imageData"), "no other glyph");
			} else {
				assertEquals (0, canvas.count ("imageData"), "no rasterizer: the Metal look draws its handle and box");
				assertTrue (canvas.count ("fill") > 6);
			}
		}
	}

	@Test
	public void framesLookUpTheCache () {
		Assumptions.assumeTrue (SVG, "no SVG rasterizer (org.eclipse.swt.svg fragment) on the class path");
		RowRenderer renderer = new RowRenderer (Theme.INITIATIVE31.derive ("vector").vectorGlyphs (true).build ());
		Recorder canvas = new Recorder ();
		for (int frame = 0; frame < 200; frame++) renderer.paint (canvas, row (125));
		assertEquals (400, canvas.count ("imageData"), "every frame draws both glyphs");
		assertTrue (canvas.has ("imageData 20 10,17 20x20"), "125 % of 16 points is 20 pixels: " + canvas.calls.get (canvas.calls.size () - 3));
	}
}
