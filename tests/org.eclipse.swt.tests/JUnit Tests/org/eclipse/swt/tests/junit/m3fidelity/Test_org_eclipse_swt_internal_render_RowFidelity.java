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
package org.eclipse.swt.tests.junit.m3fidelity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * Drawn-row atoms of the real-app fidelity leaf, against the stock screens of ControlExample (Windows 11, 100 %):
 * ROW_IMAGE_BOUNDS v1 (the "Colors and Font" table: 12 x 12 images in 19-pixel rows, stock at row top + 3) and
 * GRID_LINES v1 (the System and Table tabs: the column detail ebf4fe on each column's last pixel without grid lines).
 */
public class Test_org_eclipse_swt_internal_render_RowFidelity {
	static final List<String> calls = new ArrayList<> ();

	/* records the primitives the row atoms produce */
	static final RowCanvas RECORDER = new RowCanvas () {
		@Override public void begin (int x, int y, int w, int h) { }
		@Override public void end () { }
		@Override public void fill (int x, int y, int w, int h, int rgb) { calls.add ("fill " + x + "," + y + " " + w + "x" + h + " " + Integer.toHexString (rgb)); }
		@Override public void text (String text, long font, int rgb, int x, int y, int w, int h, int align) { }
		@Override public void image (Image image, int x, int y, int w, int h) { calls.add ("image " + x + "," + y + " " + w + "x" + h); }
		@Override public void imageData (ImageData data, int x, int y, int w, int h) { calls.add ("imageData " + x + "," + y + " " + w + "x" + h); }
		@Override public void polygon (int [] points, int count, int rgb) { }
		@Override public void part (int part, int state, int x, int y, int w, int h) { }
		@Override public void focus (int x, int y, int w, int h) { }
		@Override public void dispose () { }
	};

	@BeforeEach
	void clear () {
		calls.clear ();
	}

	/* a list view row at y = 346, 19 pixels high, cells at 0..55, 55..95, 95..140 */
	static RowPaint row (int state) {
		RowPaint row = new RowPaint ();
		row.y = 346;
		row.width = 200;
		row.height = 19;
		row.state = state;
		row.cells (3);
		int [] x = {0, 55, 95}, w = {55, 40, 45};
		for (int i = 0; i < 3; i++) {
			row.cellX [i] = row.textX [i] = x [i];
			row.cellWidth [i] = row.textWidth [i] = w [i];
		}
		return row;
	}

	@Test
	public void test_imageIsDrawnAtTheImageListSizeCentredInTheRow () {
		RowPaint row = row (0);
		row.imageData [0] = new ImageData (12, 12, 24, new PaletteData (0xFF, 0xFF00, 0xFF0000));
		row.imageX [0] = 4;
		row.imageWidth [0] = 12;
		row.imageHeight = 12;
		new RowRenderer (Theme.NATIVE).paintCell (RECORDER, row, 0);
		assertEquals (List.of ("imageData 4,349 12x12"), calls);
	}

	@Test
	public void test_unknownImageListSizeKeepsTheRowHeight () {
		RowPaint row = row (0);
		row.imageData [0] = new ImageData (12, 12, 24, new PaletteData (0xFF, 0xFF00, 0xFF0000));
		row.imageX [0] = 4;
		row.imageWidth [0] = 12;
		new RowRenderer (Theme.NATIVE).paintCell (RECORDER, row, 0);
		assertEquals (List.of ("imageData 4,346 12x19"), calls);
	}

	@Test
	public void test_rowLayoutCarriesTheImageHeight () {
		RowPaint row = row (0);
		row.imageHeight = 12;
		int [] ints = new int [RowPaint.keyLength (row.cells)];
		String [] texts = new String [3];
		ImageData [] images = new ImageData [3];
		row.write (ints, 0, texts, images, 0);
		RowPaint back = new RowPaint ();
		back.read (ints, 0, texts, images, 0);
		assertEquals (12, back.imageHeight);
		assertEquals (139, back.cellX [2] + back.cellWidth [2] - 1);
	}
}
