/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
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
 *     Hitesh Soliwal (Synexia) - desktop Canvas layer XOR state regression
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

/** Desktop-only offscreen regression; requires real platform graphics execution. */
public class Test_CanvasLayerXor {
	@Test
	public void test_layerCompositingTemporarilyDisablesAndRestoresXor () {
		Display display = Display.getCurrent ();
		boolean ownsDisplay = display == null;
		if (ownsDisplay) display = new Display ();
		Shell shell = new Shell (display);
		try {
			Canvas canvas = new Canvas (shell, SWT.NONE);
			canvas.setSize (4, 4);
			ImageData red = new ImageData (2, 2, 32, new PaletteData (0xff0000, 0xff00, 0xff));
			for (int y = 0; y < 2; y++) red.setPixels (0, y, 2, new int [] {0xff0000, 0xff0000}, 0);
			canvas.addLayer (red);
			Image image = new Image (display, 4, 4);
			try {
				GC gc = new GC (image);
				try {
					gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
					gc.fillRectangle (0, 0, 4, 4);
					gc.setXORMode (true);
					Event event = new Event ();
					event.gc = gc;
					event.width = event.height = 4;
					canvas.notifyListeners (SWT.Paint, event);
					assertTrue (gc.getXORMode (), "Layer drawing must restore the caller's XOR state");
				} finally {
					gc.dispose ();
				}
				ImageData result = image.getImageData ();
				assertEquals (new RGB (255, 0, 0), result.palette.getRGB (result.getPixel (0, 0)));
				assertEquals (new RGB (255, 255, 255), result.palette.getRGB (result.getPixel (3, 3)));
			} finally {
				image.dispose ();
			}
		} finally {
			try {
				shell.dispose ();
			} finally {
				if (ownsDisplay) display.dispose ();
			}
		}
	}
}
