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

import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * RTL_FRAME v1 (real-app fidelity leaf, census GF48): a right-to-left Canvas drawn through the M3 renderer looks like the
 * stock one: the frame mirrored, glyphs readable, an image in its own orientation, nothing left unpainted. Oracle: the same
 * Paint listener on a right-to-left <code>SWT.EMBEDDED</code> canvas, which keeps the stock path in every mode.
 */
public class Test_org_eclipse_swt_widgets_M3CanvasMirroring {
	static final int W = 100, H = 60;
	Display display;
	Shell shell;
	Image picture;

	@BeforeEach
	void open () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new GridLayout (2, false));
		picture = new Image (display, 8, 4);
		GC gc = new GC (picture);
		gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
		gc.fillRectangle (0, 0, 4, 4);
		gc.setBackground (display.getSystemColor (SWT.COLOR_BLUE));
		gc.fillRectangle (4, 0, 4, 4);
		gc.dispose ();
	}

	@AfterEach
	void close () {
		shell.dispose ();
		picture.dispose ();
	}

	Canvas canvas (int style) {
		Canvas canvas = new Canvas (shell, style | SWT.RIGHT_TO_LEFT);
		canvas.setLayoutData (new GridData (W, H));
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		canvas.addListener (SWT.Paint, e -> {
			e.gc.setBackground (display.getSystemColor (SWT.COLOR_DARK_GREEN));
			e.gc.fillRectangle (0, 0, 10, 10);
			e.gc.drawImage (picture, 30, 2);
			e.gc.setForeground (display.getSystemColor (SWT.COLOR_BLACK));
			e.gc.drawString ("L", 50, 20, true);
		});
		return canvas;
	}

	ImageData screen () {
		Rectangle area = shell.getClientArea ();
		Image shot = new Image (display, area.width, area.height);
		GC gc = new GC (shell);
		gc.copyArea (shot, 0, 0);
		gc.dispose ();
		ImageData data = shot.getImageData ();
		shot.dispose ();
		return data;
	}

	static int rgb (ImageData d, Point origin, int x, int y) {
		RGB c = d.palette.getRGB (d.getPixel (origin.x + x, origin.y + y));
		return c.red << 16 | c.green << 8 | c.blue;
	}

	/* the columns of the canvas (physical) that hold dark ink in the text band */
	static int [] inkColumns (ImageData d, Point origin) {
		return IntStream.range (0, W).filter (x -> IntStream.range (16, 44).anyMatch (y -> (rgb (d, origin, x, y) & 0xFF) < 0x80 && rgb (d, origin, x, y) != 0x0000FF)).toArray ();
	}

	@Test
	public void test_mirroredFrameMatchesTheStockCanvas () {
		Canvas m3 = canvas (SWT.NONE), stock = canvas (SWT.EMBEDDED);
		shell.pack ();
		shell.open ();
		long end = System.currentTimeMillis () + 1500;
		while (System.currentTimeMillis () < end) if (!display.readAndDispatch ()) display.timerExec (20, () -> { });
		ImageData d = screen ();
		Point a = m3.getLocation (), b = stock.getLocation ();
		long shapes = IntStream.range (0, W * H).filter (i -> !inTextBand (i % W, i / W) && rgb (d, a, i % W, i / W) != rgb (d, b, i % W, i / W)).count ();
		assertEquals (0, shapes, "pixels outside the text band that differ from the stock canvas");
		int [] mine = inkColumns (d, a), theirs = inkColumns (d, b);
		assertTrue (mine.length > 0 && theirs.length > 0, "text drawn");
		assertTrue (Math.abs (mine [0] - theirs [0]) <= 1 && Math.abs (mine [mine.length - 1] - theirs [theirs.length - 1]) <= 1,
			"text box " + mine [0] + ".." + mine [mine.length - 1] + " vs stock " + theirs [0] + ".." + theirs [theirs.length - 1]);
		assertTrue (stroke (d, a, mine [0]) > stroke (d, a, mine [mine.length - 1]), "the L reads left to right (stem on the left)");
	}

	static boolean inTextBand (int x, int y) {
		return y >= 16 && y < 44 && x >= 30 && x < 60;
	}

	static long stroke (ImageData d, Point origin, int x) {
		return IntStream.range (16, 44).filter (y -> (rgb (d, origin, x, y) & 0xFF) < 0x80).count ();
	}
}
