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
package org.eclipse.swt.snippets.m3.gallery.renderer;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * M3 renderer: a Canvas created with <code>SWT.M3</code> beside a plain Canvas, both painting the same scene (gradient,
 * shapes, text, an image, alpha). With <code>-Dswt.widget.mode.Canvas=m3</code> the M3 canvas is drawn by the M3
 * renderer into a raster surface, with <code>gpu</code> on the graphics hardware; in every other mode it paints with
 * the native GC like the plain one. The argument names the mode the entry is meant for; the title shows the mode the
 * Canvas class runs.
 */
public class M3RendererCanvasExample {
	//GALLERY: m3 | SWT.M3 canvas drawn by the M3 renderer, raster (run with mode m3)
	//GALLERY: gpu | SWT.M3 canvas drawn on the graphics hardware (run with mode gpu)

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setLayout (new GridLayout (2, true));
		Canvas m3 = canvas (shell, SWT.M3, "SWT.M3 canvas");
		canvas (shell, SWT.NONE, "plain canvas");
		String meant = args.length > 0 ? args [0] : "m3";
		String runs = M3Mode.name (M3Mode.of (m3));
		String requested = System.getProperty ("swt.widget.mode.Canvas", System.getProperty ("swt.widget.mode", "(not set)"));
		shell.setText ("M3RendererCanvasExample: meant for " + meant + ", Canvas mode requested " + requested + ", M3Mode " + runs);
		shell.setSize (760, 380);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static Canvas canvas (Composite parent, int style, String name) {
		Canvas canvas = new Canvas (parent, style | SWT.DOUBLE_BUFFERED);
		canvas.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		canvas.addListener (SWT.Paint, e -> scene (e.gc, canvas.getClientArea (), name));
		return canvas;
	}

	/** The shared scene. */
	static void scene (GC gc, Rectangle r, String name) {
		Device device = gc.getDevice ();
		gc.setAdvanced (true);
		gc.setAntialias (SWT.ON);
		Pattern gradient = new Pattern (device, 0, 0, r.width, r.height, device.getSystemColor (SWT.COLOR_WHITE), device.getSystemColor (SWT.COLOR_DARK_CYAN));
		gc.setBackgroundPattern (gradient);
		gc.fillRectangle (r);
		gc.setBackgroundPattern (null);
		gradient.dispose ();
		shapes (gc, r);
		gc.drawImage (((Display) device).getSystemImage (SWT.ICON_INFORMATION), r.width - 50, 10);
		gc.setFont (device.getSystemFont ());
		gc.drawString (name, 10, 10, true);
	}

	static void shapes (GC gc, Rectangle r) {
		Device device = gc.getDevice ();
		gc.setAlpha (160);
		gc.setBackground (device.getSystemColor (SWT.COLOR_RED));
		gc.fillOval (20, 40, r.width / 2, r.height / 2);
		gc.setBackground (device.getSystemColor (SWT.COLOR_BLUE));
		gc.fillRoundRectangle (r.width / 3, r.height / 3, r.width / 2, r.height / 2, 24, 24);
		gc.setAlpha (255);
		gc.setLineWidth (3);
		gc.drawPolygon (new int [] {r.width / 2, 30, r.width - 30, r.height - 30, 30, r.height - 30});
		gc.setLineWidth (1);
	}
}
