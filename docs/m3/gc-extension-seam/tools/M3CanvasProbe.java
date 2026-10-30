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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.m3.M3CanvasHandler;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Display probe (GC seam, leaf 4): a Canvas created with SWT.M3; the startup mode (-Dswt.widget.mode=m3|gpu, or none) decides
 * whether its PaintListener gets a GCExtension over the M3 back buffer or the stock GC. The listener fills a red rectangle;
 * the stock reference pixels come from Control.print (a stock GC on an Image in every mode), the M3 pixels from the handler's
 * back buffer. Both are read inside the rectangle and outside it (the canvas background).
 */
public class M3CanvasProbe {
	static final int INSIDE_X = 20, INSIDE_Y = 20, OUTSIDE_X = 120, OUTSIDE_Y = 120;

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setSize (320, 240);
		shell.setLayout (new FillLayout ());
		Canvas canvas = new Canvas (shell, SWT.M3);
		int [] paints = new int [2];
		canvas.addPaintListener (e -> {
			paints [0]++;
			if (e.gc instanceof GCExtension) paints [1]++;
			e.gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
			e.gc.fillRectangle (10, 10, 60, 40);
		});
		shell.open ();
		long end = System.currentTimeMillis () + 1500;
		while (System.currentTimeMillis () < end) if (!display.readAndDispatch ()) display.sleep ();
		int screenPaints = paints [0], extensionPaints = paints [1];
		Rectangle area = canvas.getClientArea ();
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (image);
		canvas.print (gc);
		gc.dispose ();
		ImageData data = image.getImageData ();
		int printInside = rgb (data, INSIDE_X, INSIDE_Y), printOutside = rgb (data, OUTSIDE_X, OUTSIDE_Y);
		image.dispose ();
		Object handler = canvas.getData (M3CanvasHandler.DATA_KEY);
		String backend = "none";
		int bufferInside = -1, bufferOutside = -1;
		if (handler instanceof M3CanvasHandler h) {
			backend = h.isGpu () ? "gpu" : "raster";
			bufferInside = h.pixel (INSIDE_X, INSIDE_Y);
			bufferOutside = h.pixel (OUTSIDE_X, OUTSIDE_Y);
		}
		String mode = System.getProperty ("swt.widget.mode");
		boolean m3 = "m3".equals (mode) || "gpu".equals (mode);
		boolean pass = screenPaints > 0 && printInside == 0xFF0000;
		if (m3) {
			pass &= handler != null && extensionPaints == screenPaints && bufferInside == 0xFF0000 && bufferOutside == printOutside;
		} else {
			pass &= handler == null && extensionPaints == 0;
		}
		System.out.println ("PROBE mode=" + mode + " handler=" + (handler != null) + " backend=" + backend + " paints=" + screenPaints
			+ " gcExtension=" + extensionPaints + " printInside=" + hex (printInside) + " printOutside=" + hex (printOutside)
			+ " bufferInside=" + hex (bufferInside) + " bufferOutside=" + hex (bufferOutside));
		System.out.println (pass ? "PROBE PASS" : "PROBE FAIL");
		shell.dispose ();
		display.dispose ();
		System.exit (pass ? 0 : 1);
	}

	static int rgb (ImageData data, int x, int y) {
		RGB c = data.palette.getRGB (data.getPixel (x, y));
		return c.red << 16 | c.green << 8 | c.blue;
	}

	static String hex (int rgb) {
		return rgb < 0 ? "none" : String.format ("%06x", rgb);
	}
}
