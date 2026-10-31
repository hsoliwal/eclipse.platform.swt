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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.m3.M3CanvasHandler;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Display probe (GC seam, leaf 5; pattern M3-CANVAS-PROBE v2, v1 was leaf 4's single SWT.M3 canvas). One shell, five widgets:
 * a Canvas created without SWT.M3 (a fill, two LINE_CUSTOM dashed lines with offsets 0 and 3 (a quarter period: the direction shows), two miter polylines with limits
 * 1 and 10, then a partial redraw), a StyledText with a selection, a CTabFolder with 3 tabs (a Composite: leaf 7's
 * Composite hook), a CLabel with an image, and the leaf-4 Canvas created with SWT.M3. The startup mode (-Dswt.widget.mode=m3|gpu, or
 * none for stock) decides whether a widget gets the M3 handler. Per widget: handler, paints and GCExtension paints (counted
 * before any print), the print (a stock GC on an Image in every mode) against the M3 back buffer: exact mismatches of the
 * non-text regions, text coverage (pixels that are neither background nor selection) within TEXT_TOLERANCE. PNGs of both
 * and probe_&lt;mode&gt;.tsv go to -Dm3.probe.out.
 */
public class M3CanvasProbe {
	static final String OUT = System.getProperty ("m3.probe.out", "H:/m3-scratch/gc-leaf5/probes");
	static final String MODE = System.getProperty ("swt.widget.mode", "stock");
	static final boolean DRAWN = "m3".equals (MODE) || "gpu".equals (MODE);
	static final double TEXT_TOLERANCE = 0.5;
	static final Rectangle FILL = new Rectangle (10, 10, 60, 30), DAMAGE = new Rectangle (20, 20, 30, 10);
	static final int DASH_Y0 = 60, DASH_Y1 = 70, DASH_X0 = 10, DASH_X1 = 190, DASH = 6, DASH_SHIFT = 3, APEX_Y = 40;
	static final int [] APEX_X = {225, 285};

	/** One probed widget: counters taken before any print, the print (stock GC) and the M3 back buffer, the facts. */
	static final class Probe {
		final String name;
		final Composite control;
		final boolean covered;
		final Map<String, String> facts = new LinkedHashMap<> ();
		int paints, extension, screenPaints, screenExtension;
		ImageData print, buffer, stock;
		String result = "PASS";

		/* the stock reference: the stock run's print (in m3/gpu Control.print paints through the handler too, L5-46), else this run's */
		ImageData reference () {
			return stock != null ? stock : print;
		}

		Probe (String name, Composite control, boolean covered) {
			this.name = name;
			this.control = control;
			this.covered = covered;
			control.addListener (SWT.Paint, e -> {
				paints++;
				if (e.gc instanceof GCExtension) extension++;
			});
		}

		M3CanvasHandler handler () {
			return control.getData (M3CanvasHandler.DATA_KEY) instanceof M3CanvasHandler h ? h : null;
		}

		void fact (String key, Object value) {
			facts.put (key, String.valueOf (value));
		}

		void require (String what, boolean ok) {
			if (!ok && "PASS".equals (result)) result = "FAIL";
			if (!ok) fact ("failed", facts.getOrDefault ("failed", "") + what + ",");
		}
	}

	public static void main (String [] args) throws IOException {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setSize (720, 600);
		shell.setLayout (new GridLayout (2, true));
		Image image = labelImage (display);
		Probe dashes = dashes (shell), text = styledText (shell), tabs = tabs (shell), label = label (shell, image), bit = bit (shell);
		List<Probe> probes = List.of (dashes, text, tabs, label, bit);
		guarded (shell::open);
		settle (display, 1500);
		Rectangle damage = damage (display, dashes);
		probes.forEach (p -> { p.screenPaints = p.paints; p.screenExtension = p.extension; });
		probes.forEach (p -> capture (display, p));
		checkDashes (dashes, damage);
		checkText (text, (StyledText) text.control);
		checkTabs (tabs);
		checkLabel (label, ((CLabel) label.control).getBackground ().getRGB ());
		checkBit (bit);
		boolean pass = report (probes);
		image.dispose ();
		shell.dispose ();
		display.dispose ();
		System.exit (pass ? 0 : 1);
	}

	/* a bare canvas raises no event after its first paint: a timer wakes display.sleep () (L4-13) */
	static void settle (Display display, int millis) {
		boolean [] done = new boolean [1];
		display.timerExec (millis, () -> done [0] = true);
		while (!done [0]) guarded (() -> { if (!display.readAndDispatch ()) display.sleep (); });
	}

	static final List<String> ERRORS = new ArrayList<> ();

	/* a listener's exception (StyledText under M3 while GF01 is open, L5-50) is recorded and the probe goes on; any fails the run */
	static void guarded (Runnable step) {
		try {
			step.run ();
		} catch (RuntimeException e) {
			StackTraceElement [] at = e.getStackTrace ();
			String where = at.length > 1 ? " at " + at [0] + " <- " + at [1] : at.length > 0 ? " at " + at [0] : "";
			ERRORS.add (e.getClass ().getSimpleName () + ": " + e.getMessage () + where);
		}
	}

	static GridData fill () {
		return new GridData (SWT.FILL, SWT.FILL, true, true);
	}

	/* a Canvas without SWT.M3: covered in m3/gpu by leaf 5 */
	static Probe dashes (Shell shell) {
		Canvas canvas = new Canvas (shell, SWT.NONE);
		canvas.setLayoutData (fill ());
		canvas.addPaintListener (e -> paintDashes (e.gc, shell.getDisplay ()));
		return new Probe ("Canvas", canvas, true);
	}

	static void paintDashes (GC gc, Display display) {
		gc.setBackground (display.getSystemColor (SWT.COLOR_BLUE));
		gc.fillRectangle (FILL);
		gc.setForeground (display.getSystemColor (SWT.COLOR_BLACK));
		gc.setLineAttributes (new LineAttributes (1, SWT.CAP_FLAT, SWT.JOIN_MITER, SWT.LINE_CUSTOM, new float [] {DASH, DASH}, 0, 10));
		gc.drawLine (DASH_X0, DASH_Y0, DASH_X1, DASH_Y0);
		gc.setLineAttributes (new LineAttributes (1, SWT.CAP_FLAT, SWT.JOIN_MITER, SWT.LINE_CUSTOM, new float [] {DASH, DASH}, DASH_SHIFT, 10));
		gc.drawLine (DASH_X0, DASH_Y1, DASH_X1, DASH_Y1);
		miter (gc, display, APEX_X [0], 1);
		miter (gc, display, APEX_X [1], 10);
	}

	/* an open two-segment path with a sharp apex: the join is a miter or, past the limit, a bevel (drawPolyline has no joins in M3: P06) */
	static void miter (GC gc, Display display, int apexX, float limit) {
		gc.setLineAttributes (new LineAttributes (9, SWT.CAP_FLAT, SWT.JOIN_MITER, SWT.LINE_SOLID, null, 0, limit));
		org.eclipse.swt.graphics.Path path = new org.eclipse.swt.graphics.Path (display);
		path.moveTo (apexX - 25, 100);
		path.lineTo (apexX, APEX_Y);
		path.lineTo (apexX + 25, 100);
		gc.drawPath (path);
		path.dispose ();
	}

	static Probe styledText (Shell shell) {
		StyledText text = new StyledText (shell, SWT.MULTI);
		text.setLayoutData (fill ());
		text.setText ("M3 StyledText probe\nsecond line of text\nthird line, end");
		text.setSelection (3, 13);
		return new Probe ("StyledText", text, true);
	}

	/* CTabFolder extends Composite, not Canvas: leaf 7's Composite hook reaches it in m3/gpu (M3CompositeProbe measures its regions) */
	static Probe tabs (Shell shell) {
		CTabFolder folder = new CTabFolder (shell, SWT.NONE);
		folder.setLayoutData (fill ());
		for (int i = 1; i <= 3; i++) new CTabItem (folder, SWT.NONE).setText ("Tab " + i);
		folder.setSelection (0);
		return new Probe ("CTabFolder", folder, true);
	}

	static Image labelImage (Display display) {
		Image image = new Image (display, 24, 24);
		GC gc = new GC (image);
		gc.setBackground (display.getSystemColor (SWT.COLOR_MAGENTA));
		gc.fillRectangle (0, 0, 24, 24);
		gc.setBackground (display.getSystemColor (SWT.COLOR_GREEN));
		gc.fillRectangle (8, 8, 8, 8);
		gc.dispose ();
		return image;
	}

	static Probe label (Shell shell, Image image) {
		CLabel label = new CLabel (shell, SWT.NONE);
		label.setLayoutData (fill ());
		label.setImage (image);
		label.setText ("CLabel with image");
		return new Probe ("CLabel", label, true);
	}

	/* the leaf-4 probe: a Canvas created with SWT.M3 (stock without a mode) */
	static Probe bit (Shell shell) {
		Canvas canvas = new Canvas (shell, SWT.M3);
		canvas.setLayoutData (fill ());
		canvas.addPaintListener (e -> {
			e.gc.setBackground (shell.getDisplay ().getSystemColor (SWT.COLOR_RED));
			e.gc.fillRectangle (10, 10, 60, 40);
		});
		return new Probe ("Canvas(SWT.M3)", canvas, true);
	}

	/* a partial redraw: the next Paint event's bounds (the damage atom: the rectangle, not the whole canvas) */
	static Rectangle damage (Display display, Probe probe) {
		Rectangle [] seen = new Rectangle [1];
		org.eclipse.swt.widgets.Listener watch = e -> { if (seen [0] == null) seen [0] = e.getBounds (); };
		probe.control.addListener (SWT.Paint, watch);
		probe.control.redraw (DAMAGE.x, DAMAGE.y, DAMAGE.width, DAMAGE.height, false);
		guarded (probe.control::update);
		probe.control.removeListener (SWT.Paint, watch);
		return seen [0];
	}

	static void capture (Display display, Probe p) {
		Rectangle area = p.control.getClientArea ();
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (image);
		int before = p.extension;
		guarded (() -> p.control.print (gc));
		gc.dispose ();
		p.fact ("printThroughM3", p.extension - before);
		p.print = image.getImageData ();
		image.dispose ();
		M3CanvasHandler h = p.handler ();
		if (h != null) p.buffer = buffer (h, area.width, area.height);
		save (p);
		p.stock = stock (p);
		p.fact ("reference", p.stock != null ? "stock-run print" : "this run's print");
	}

	/* the stock run's print of the widget (print_<widget>_stock.png), in an m3/gpu run */
	static ImageData stock (Probe p) {
		Path file = Path.of (OUT, "print_" + p.name.replaceAll ("[^A-Za-z0-9]+", "_") + "_stock.png");
		return "stock".equals (MODE) || !Files.exists (file) ? null : new ImageData (file.toString ());
	}

	static ImageData buffer (M3CanvasHandler h, int width, int height) {
		ImageData data = new ImageData (width, height, 24, new PaletteData (0xFF0000, 0xFF00, 0xFF));
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) data.setPixel (x, y, Math.max (0, h.pixel (x, y)));
		return data;
	}

	static void save (Probe p) {
		String base = p.name.replaceAll ("[^A-Za-z0-9]+", "_") + "_" + MODE;
		png (p.print, OUT + "/print_" + base + ".png", p);
		if (p.buffer != null) png (p.buffer, OUT + "/buffer_" + base + ".png", p);
	}

	static void png (ImageData data, String file, Probe p) {
		try {
			ImageLoader loader = new ImageLoader ();
			loader.data = new ImageData [] {data};
			loader.save (file, SWT.IMAGE_PNG);
		} catch (RuntimeException e) {
			p.fact ("png", "failed " + e);
		}
	}

	static int rgb (ImageData data, int x, int y) {
		RGB c = data.palette.getRGB (data.getPixel (x, y));
		return c.red << 16 | c.green << 8 | c.blue;
	}

	static int mismatches (ImageData a, ImageData b, Rectangle r) {
		int n = 0;
		for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) if (rgb (a, x, y) != rgb (b, x, y)) n++;
		return n;
	}

	static int count (ImageData data, Rectangle r, IntPredicate test) {
		int n = 0;
		for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) if (test.test (rgb (data, x, y))) n++;
		return n;
	}

	/* every widget: the handler where expected, every screen paint through a GCExtension when handled, none otherwise */
	static void common (Probe p) {
		M3CanvasHandler h = p.handler ();
		p.fact ("handler", h != null);
		p.fact ("backend", h == null ? "none" : h.isGpu () ? "gpu" : "raster");
		p.fact ("paints", p.screenPaints);
		p.fact ("gcExtension", p.screenExtension);
		p.require ("handler", (h != null) == (DRAWN && p.covered));
		p.require ("paints", p.screenPaints > 0);
		p.require ("gcExtension", h != null ? p.screenExtension == p.screenPaints : p.screenExtension == 0);
	}

	/* the text-free check: exact where the buffer exists; a ratio of coverage counts within the tolerance */
	static boolean within (int a, int b) {
		return Math.abs (b - a) <= TEXT_TOLERANCE * Math.max (1, a);
	}

	static void checkDashes (Probe p, Rectangle damage) {
		common (p);
		p.fact ("damageBounds", damage == null ? "none" : damage.x + "," + damage.y + "," + damage.width + "," + damage.height);
		p.require ("damage", DAMAGE.equals (damage));
		dashFacts (p, "ref", p.reference ());
		if (p.buffer == null) return;
		dashFacts (p, "buffer", p.buffer);
		p.fact ("fillMismatch", mismatches (p.reference (), p.buffer, FILL));
		p.require ("fill", mismatches (p.reference (), p.buffer, FILL) == 0);
		p.require ("dashPhase", "1.000".equals (p.facts.get ("bufferPhase")));
		p.require ("miter", Integer.parseInt (p.facts.get ("bufferTip1")) < Integer.parseInt (p.facts.get ("bufferTip10")));
	}

	/* the dashed rows: on-pixel counts, the phase (row 1 equals row 0 shifted by the offset), the miter tips above the apexes */
	static void dashFacts (Probe p, String which, ImageData d) {
		int background = rgb (d, DASH_X1 + 20, DASH_Y0);
		int agree = 0, from = DASH_X0 + 1, span = DASH_X1 - DASH_X0 - 2 * DASH - 1;
		for (int x = from; x < from + span; x++) if ((rgb (d, x, DASH_Y1) != background) == (rgb (d, x + DASH_SHIFT, DASH_Y0) != background)) agree++;
		p.fact (which + "On0", count (d, new Rectangle (DASH_X0, DASH_Y0, DASH_X1 - DASH_X0, 1), c -> c != background));
		p.fact (which + "On1", count (d, new Rectangle (DASH_X0, DASH_Y1, DASH_X1 - DASH_X0, 1), c -> c != background));
		p.fact (which + "Phase", String.format ("%.3f", agree / (double) span));
		for (int i = 0; i < 2; i++) {
			Rectangle tip = new Rectangle (APEX_X [i] - 6, APEX_Y - 14, 13, 12);
			p.fact (which + "Tip" + (i == 0 ? 1 : 10), count (d, tip, c -> c != background));
		}
	}

	static void checkText (Probe p, StyledText text) {
		common (p);
		Rectangle area = text.getClientArea ();
		int lines = text.getLineHeight () * text.getLineCount ();
		int selection = pack (text.getSelectionBackground ().getRGB ());
		int white = pack (text.getBackground ().getRGB ());
		Rectangle ink = new Rectangle (0, 0, Math.min (200, area.width), lines);
		Rectangle blank = new Rectangle (0, lines + 4, area.width - 2, area.height - lines - 6);
		textFacts (p, "ref", p.reference (), ink, white, selection);
		if (p.buffer == null) return;
		textFacts (p, "buffer", p.buffer, ink, white, selection);
		p.fact ("blankMismatch", mismatches (p.reference (), p.buffer, blank));
		p.require ("blank", mismatches (p.reference (), p.buffer, blank) == 0);
		p.require ("textCoverage", within (Integer.parseInt (p.facts.get ("refInk")), Integer.parseInt (p.facts.get ("bufferInk"))));
		p.require ("selection", within (Integer.parseInt (p.facts.get ("refSelection")), Integer.parseInt (p.facts.get ("bufferSelection"))));
	}

	static void textFacts (Probe p, String which, ImageData d, Rectangle ink, int background, int selection) {
		p.fact (which + "Ink", count (d, ink, c -> c != background && c != selection));
		p.fact (which + "Selection", count (d, ink, c -> c == selection));
	}

	static int pack (RGB c) {
		return c.red << 16 | c.green << 8 | c.blue;
	}

	static void checkTabs (Probe p) {
		common (p);
		Rectangle area = p.control.getClientArea ();
		int background = rgb (p.print, area.width - 3, area.height - 3);
		p.fact ("printInk", count (p.print, new Rectangle (0, 0, area.width, Math.min (30, area.height)), c -> c != background));
		p.fact ("hook", "Composite (leaf 7; regions and ink in M3CompositeProbe)");
	}

	static void checkLabel (Probe p, RGB labelBackground) {
		common (p);
		int background = pack (labelBackground);
		Rectangle box = box (p.reference (), 0xFF00FF), area = p.control.getClientArea ();
		p.fact ("imageBox", box == null ? "none" : box.x + "," + box.y + "," + box.width + "," + box.height);
		p.require ("imageBox", box != null && box.width == 24 && box.height == 24);
		if (box == null) return;
		Rectangle ink = new Rectangle (box.x + box.width + 2, 0, area.width - box.x - box.width - 4, area.height);
		p.fact ("refInk", count (p.reference (), ink, c -> c != background));
		if (p.buffer == null) return;
		p.fact ("bufferInk", count (p.buffer, ink, c -> c != background));
		p.fact ("imageMismatch", mismatches (p.reference (), p.buffer, box));
		p.require ("image", mismatches (p.reference (), p.buffer, box) == 0);
		p.require ("textCoverage", within (Integer.parseInt (p.facts.get ("refInk")), Integer.parseInt (p.facts.get ("bufferInk"))));
	}

	/* the bounding box of the pixels of one colour */
	static Rectangle box (ImageData d, int colour) {
		int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = -1, b = -1;
		for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) if (rgb (d, x, y) == colour) {
			l = Math.min (l, x); t = Math.min (t, y); r = Math.max (r, x); b = Math.max (b, y);
		}
		return r < 0 ? null : new Rectangle (l, t, r - l + 1, b - t + 1);
	}

	static void checkBit (Probe p) {
		common (p);
		p.fact ("refInside", String.format ("%06x", rgb (p.reference (), 20, 20)));
		p.require ("refInside", rgb (p.reference (), 20, 20) == 0xFF0000);
		if (p.buffer == null) return;
		p.fact ("bufferInside", String.format ("%06x", rgb (p.buffer, 20, 20)));
		p.fact ("rectMismatch", mismatches (p.reference (), p.buffer, new Rectangle (0, 0, 100, 60)));
		p.require ("rect", mismatches (p.reference (), p.buffer, new Rectangle (0, 0, 100, 60)) == 0);
	}

	static boolean report (List<Probe> probes) throws IOException {
		List<String> rows = new ArrayList<> ();
		rows.add ("mode\twidget\tresult\tfacts");
		boolean pass = true;
		for (Probe p : probes) {
			pass &= !"FAIL".equals (p.result);
			StringBuilder facts = new StringBuilder ();
			p.facts.forEach ((k, v) -> facts.append (k).append ('=').append (v).append (' '));
			System.out.println ("PROBE mode=" + MODE + " widget=" + p.name + " result=" + p.result + " " + facts.toString ().trim ());
			rows.add (MODE + "\t" + p.name + "\t" + p.result + "\t" + facts.toString ().trim ());
		}
		ERRORS.stream ().distinct ().forEach (e -> rows.add (MODE + "\texception\tFAIL\t" + e));
		ERRORS.stream ().distinct ().limit (3).forEach (e -> System.out.println ("PROBE exception " + e));
		pass &= ERRORS.isEmpty ();
		System.out.println ("PROBE exceptions=" + ERRORS.size ());
		Files.write (Path.of (OUT, "probe_" + MODE + ".tsv"), rows);
		System.out.println (pass ? "PROBE PASS" : "PROBE FAIL");
		return pass;
	}
}
