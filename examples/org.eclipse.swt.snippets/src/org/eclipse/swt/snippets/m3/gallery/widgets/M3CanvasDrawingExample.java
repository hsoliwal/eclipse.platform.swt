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
package org.eclipse.swt.snippets.m3.gallery.widgets;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Canvas drawing with GC: paths, gradient and image patterns, transforms, text and text layout, images, alpha,
 * clipping and line attributes. The first argument picks one drawing (default: all, in a grid); the optional
 * argument <code>M3</code> creates the canvases with <code>SWT.M3</code>, drawn by the M3 renderer when
 * <code>-Dswt.widget.mode.Canvas=m3|gpu</code>.
 */
public class M3CanvasDrawingExample {
	//GALLERY: paths | GC paths: lines, quads, cubics, arcs, text outlines; fill and stroke
	//GALLERY: patterns | linear gradient and image patterns as foreground and background
	//GALLERY: transforms | translate, rotate, scale and shear transforms
	//GALLERY: text | drawText flags, fonts and a TextLayout with styles
	//GALLERY: images | images drawn scaled, transparent and from ImageData
	//GALLERY: alpha | alpha blending of shapes and images
	//GALLERY: clipping | rectangle, region and path clipping
	//GALLERY: lines | line width, dash, cap, join and antialias
	//GALLERY: all | every drawing in a grid
	//GALLERY: all M3 | every drawing on SWT.M3 canvases

	/** One drawing: paints into the canvas area with the GC. */
	interface Drawing {
		void paint (GC gc, Rectangle area);
	}

	static final Map<String, Drawing> DRAWINGS = new LinkedHashMap<> ();
	static {
		DRAWINGS.put ("paths", M3CanvasDrawingExample::paths);
		DRAWINGS.put ("patterns", M3CanvasDrawingExample::patterns);
		DRAWINGS.put ("transforms", M3CanvasDrawingExample::transforms);
		DRAWINGS.put ("text", M3CanvasDrawingExample::text);
		DRAWINGS.put ("images", M3CanvasDrawingExample::images);
		DRAWINGS.put ("alpha", M3CanvasDrawingExample::alpha);
		DRAWINGS.put ("clipping", M3CanvasDrawingExample::clipping);
		DRAWINGS.put ("lines", M3CanvasDrawingExample::lines);
	}

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		String kind = args.length > 0 ? args [0] : "all";
		int style = SWT.DOUBLE_BUFFERED | (args.length > 1 && "M3".equals (args [1]) ? SWT.M3 : 0);
		shell.setText ("M3CanvasDrawingExample: " + String.join (" ", args));
		shell.setLayout (new GridLayout ("all".equals (kind) ? 4 : 1, true));
		DRAWINGS.forEach ((name, drawing) -> {
			if ("all".equals (kind) || name.equals (kind)) canvas (shell, name, drawing, style);
		});
		shell.setSize ("all".equals (kind) ? 1000 : 420, "all".equals (kind) ? 560 : 360);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static void canvas (Composite parent, String name, Drawing drawing, int style) {
		Canvas canvas = new Canvas (parent, style);
		canvas.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		canvas.setBackground (parent.getDisplay ().getSystemColor (SWT.COLOR_WHITE));
		canvas.setToolTipText (name);
		canvas.addListener (SWT.Paint, e -> {
			e.gc.setAdvanced (true);
			e.gc.setAntialias (SWT.ON);
			drawing.paint (e.gc, canvas.getClientArea ());
			e.gc.drawString (name, 4, 4, true);
		});
	}

	static void paths (GC gc, Rectangle r) {
		Path path = new Path (gc.getDevice ());
		path.moveTo (20, 40);
		path.lineTo (r.width / 2f, 30);
		path.quadTo (r.width - 20, 40, r.width - 30, r.height / 2f);
		path.cubicTo (r.width - 40, r.height - 20, 60, r.height - 10, 30, r.height / 2f);
		path.close ();
		path.addArc (r.width / 2f - 30, r.height / 2f - 30, 60, 60, 0, 270);
		path.addString ("Path", 40, r.height / 2f - 10, gc.getFont ());
		gc.setBackground (gc.getDevice ().getSystemColor (SWT.COLOR_YELLOW));
		gc.fillPath (path);
		gc.drawPath (path);
		path.dispose ();
	}

	static void patterns (GC gc, Rectangle r) {
		Device device = gc.getDevice ();
		Pattern gradient = new Pattern (device, 0, 0, r.width, r.height, device.getSystemColor (SWT.COLOR_BLUE), device.getSystemColor (SWT.COLOR_GREEN));
		Image tile = checker (device);
		Pattern image = new Pattern (device, tile);
		gc.setBackgroundPattern (gradient);
		gc.fillRoundRectangle (10, 25, r.width - 20, r.height / 2 - 20, 20, 20);
		gc.setBackgroundPattern (image);
		gc.fillOval (10, r.height / 2 + 10, r.width - 20, r.height / 2 - 20);
		gc.setBackgroundPattern (null);
		gradient.dispose ();
		image.dispose ();
		tile.dispose ();
	}

	/** An 8x8 checker image. */
	static Image checker (Device device) {
		Image image = new Image (device, 8, 8);
		GC gc = new GC (image);
		gc.setBackground (device.getSystemColor (SWT.COLOR_RED));
		gc.fillRectangle (0, 0, 4, 4);
		gc.fillRectangle (4, 4, 4, 4);
		gc.dispose ();
		return image;
	}

	static void transforms (GC gc, Rectangle r) {
		Transform transform = new Transform (gc.getDevice ());
		float [] [] steps = {{1, 0, 0, 1, 0, 0}, {0.866f, 0.5f, -0.5f, 0.866f, 0, 0}, {1.4f, 0, 0, 0.7f, 0, 0}, {1, 0.3f, 0, 1, 0, 0}};
		for (int i = 0; i < steps.length; i++) {
			transform.setElements (steps [i] [0], steps [i] [1], steps [i] [2], steps [i] [3], r.width / 2f, r.height / 2f);
			gc.setTransform (transform);
			gc.setForeground (gc.getDevice ().getSystemColor (SWT.COLOR_RED + i));
			gc.drawRectangle (-40, -25, 80, 50);
			gc.drawString ("T" + i, -38, -23, true);
		}
		gc.setTransform (null);
		transform.dispose ();
	}

	static void text (GC gc, Rectangle r) {
		gc.drawText ("drawText with\ttab and\nline break", 10, 25, SWT.DRAW_DELIMITER | SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT);
		TextLayout layout = new TextLayout (gc.getDevice ());
		layout.setText ("TextLayout: bold, underlined, struck and coloured runs wrap at the canvas width.");
		layout.setWidth (Math.max (40, r.width - 20));
		Font bold = new Font (gc.getDevice (), gc.getFont ().getFontData () [0].getName (), 11, SWT.BOLD);
		TextStyle style = new TextStyle (bold, gc.getDevice ().getSystemColor (SWT.COLOR_DARK_GREEN), null);
		style.underline = true;
		layout.setStyle (style, 12, 15);
		layout.draw (gc, 10, 70);
		layout.dispose ();
		bold.dispose ();
	}

	static void images (GC gc, Rectangle r) {
		Device device = gc.getDevice ();
		Image icon = ((Display) device).getSystemImage (SWT.ICON_WARNING);
		Rectangle b = icon.getBounds ();
		gc.drawImage (icon, 10, 25);
		gc.drawImage (icon, 0, 0, b.width, b.height, 60, 25, b.width * 2, b.height * 2);
		ImageData data = new ImageData (32, 32, 24, new PaletteData (0xFF0000, 0xFF00, 0xFF));
		for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) data.setPixel (x, y, (x * 8) << 16 | (y * 8) << 8);
		Image generated = new Image (device, data);
		gc.drawImage (generated, 10, r.height - 50);
		generated.dispose ();
	}

	static void alpha (GC gc, Rectangle r) {
		int [] colors = {SWT.COLOR_RED, SWT.COLOR_GREEN, SWT.COLOR_BLUE};
		for (int i = 0; i < colors.length; i++) {
			gc.setAlpha (110);
			gc.setBackground (gc.getDevice ().getSystemColor (colors [i]));
			gc.fillOval (20 + i * 30, 30 + (i % 2) * 25, r.width / 2, r.height / 2);
		}
		gc.setAlpha (255);
	}

	static void clipping (GC gc, Rectangle r) {
		Region region = new Region (gc.getDevice ());
		region.add (new int [] {10, 30, r.width / 2, 25, r.width - 10, r.height - 10, 20, r.height - 30});
		gc.setClipping (region);
		gc.setBackground (gc.getDevice ().getSystemColor (SWT.COLOR_CYAN));
		gc.fillRectangle (r);
		Path circle = new Path (gc.getDevice ());
		circle.addArc (r.width / 2f - 40, r.height / 2f - 40, 80, 80, 0, 360);
		gc.setClipping (circle);
		gc.setBackground (gc.getDevice ().getSystemColor (SWT.COLOR_MAGENTA));
		gc.fillRectangle (r);
		gc.setClipping ((Rectangle) null);
		circle.dispose ();
		region.dispose ();
	}

	static void lines (GC gc, Rectangle r) {
		int [] caps = {SWT.CAP_FLAT, SWT.CAP_ROUND, SWT.CAP_SQUARE}, joins = {SWT.JOIN_MITER, SWT.JOIN_ROUND, SWT.JOIN_BEVEL};
		for (int i = 0; i < 3; i++) {
			gc.setLineAttributes (new LineAttributes (6 + 2 * i, caps [i], joins [i]));
			gc.drawPolyline (new int [] {20, 30 + i * 40, r.width / 2, 55 + i * 40, r.width - 20, 30 + i * 40});
		}
		gc.setLineAttributes (new LineAttributes (2, SWT.CAP_FLAT, SWT.JOIN_MITER, SWT.LINE_CUSTOM, new float [] {8, 4, 2, 4}, 0, 10));
		gc.drawLine (20, r.height - 20, r.width - 20, r.height - 20);
		gc.setLineStyle (SWT.LINE_SOLID);
		gc.setLineWidth (1);
	}
}
