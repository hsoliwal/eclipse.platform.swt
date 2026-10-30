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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 7)
 *******************************************************************************/
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CBanner;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.m3.M3CanvasHandler;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.POINT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Display probe (GC seam, leaf 7; pattern M3-COMPOSITE-PROBE v1, after leaf 5's M3-CANVAS-PROBE v2). One shell, six composites
 * that are not canvases: a CTabFolder (3 tabs, the second selected, close buttons), a CBanner (native Label children left and
 * right), a ViewForm (border, a native Label top left, a pure container as content), a child-clip composite (a Paint listener
 * fills it red, a native green child composite in it), a pure container (no Paint listener, a native Button) and Nebula's
 * CollapsibleButtons when its classes are on the class path. The startup mode (-Dswt.widget.mode=m3|gpu, or none for stock)
 * decides whether a composite gets the M3 handler. Per composite: handler, paints and GCExtension paints (before any print),
 * the stock run's print (the reference) against the M3 back buffer with the native children masked: exact mismatches of the
 * flat regions, ink (dark pixels) within TEXT_TOLERANCE, fills by their dominant colour. Child clip: the composite's device
 * context excludes the child (system region), the back buffer under the child holds the composite's red, and the window
 * pixels (copyArea of the window DC, when the desktop gives them) show the child's green. PNGs and probe_&lt;mode&gt;.tsv go
 * to -Dm3.probe.out.
 */
public class M3CompositeProbe {
	static final String OUT = System.getProperty ("m3.probe.out", "H:/m3-scratch/gc-leaf7/probes");
	static final String MODE = System.getProperty ("swt.widget.mode", "stock");
	static final boolean DRAWN = "m3".equals (MODE) || "gpu".equals (MODE);
	static final double TEXT_TOLERANCE = 0.5, MEAN_DELTA = 4.0;
	static final Rectangle CHILD = new Rectangle (40, 30, 60, 40);
	static final int RED = 0xFF0000, GREEN = 0x00FF00;
	static final List<String> ERRORS = new ArrayList<> ();

	/** One probed composite: counters taken before any print, the print and the M3 back buffer, the facts. */
	static final class Probe {
		final String name;
		final Composite control;
		final boolean covered;
		final Map<String, String> facts = new LinkedHashMap<> ();
		int paints, extension, screenPaints, screenExtension;
		ImageData print, buffer, stock;
		String result = "PASS";

		Probe (String name, Composite control, boolean covered, boolean counting) {
			this.name = name;
			this.control = control;
			this.covered = covered;
			if (counting) control.addListener (SWT.Paint, e -> { paints++; if (e.gc instanceof GCExtension) extension++; });
		}

		ImageData reference () {
			return stock != null ? stock : print;
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

		/* the native children's bounds: masked out of every comparison (the back buffer holds the composite's pixels there) */
		List<Rectangle> children () {
			List<Rectangle> out = new ArrayList<> ();
			for (Control child : control.getChildren ()) if (child.isVisible ()) out.add (child.getBounds ());
			return out;
		}
	}

	public static void main (String [] args) throws IOException {
		Display display = new Display ();
		Shell shell = shell (display, 780, 640, 2);
		List<Probe> probes = new ArrayList<> (List.of (tabs (shell), banner (shell), viewForm (shell), childClip (shell), container (shell)));
		Probe nebula = nebula (shell);
		if (nebula != null) probes.add (nebula);
		guarded (shell::open);
		settle (display, 1500);
		probes.forEach (p -> { p.screenPaints = p.paints; p.screenExtension = p.extension; });
		Map<String, Object> screen = screen (probes.get (3));
		probes.forEach (p -> capture (display, p));
		check (probes, nebula, screen);
		boolean pass = report (probes, nebula == null);
		census (display);
		finish (display, shell, pass);
	}

	static Shell shell (Display display, int width, int height, int columns) {
		Shell shell = new Shell (display);
		shell.setSize (width, height);
		shell.setLayout (new GridLayout (columns, true));
		return shell;
	}

	static void finish (Display display, Shell shell, boolean pass) {
		shell.dispose ();
		display.dispose ();
		System.exit (pass ? 0 : 1);
	}

	/* the per-composite checks, in the order the probes were made */
	static void check (List<Probe> probes, Probe nebula, Map<String, Object> screen) {
		checkTabs (probes.get (0));
		checkBanner (probes.get (1));
		checkViewForm (probes.get (2));
		checkClip (probes.get (3), screen);
		checkContainer (probes.get (4));
		if (nebula != null) checkNebula (nebula);
	}

	static void settle (Display display, int millis) {
		boolean [] done = new boolean [1];
		display.timerExec (millis, () -> done [0] = true);
		while (!done [0]) guarded (() -> { if (!display.readAndDispatch ()) display.sleep (); });
	}

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

	static Probe tabs (Shell shell) {
		CTabFolder folder = new CTabFolder (shell, SWT.BORDER | SWT.CLOSE);
		folder.setLayoutData (fill ());
		for (int i = 1; i <= 3; i++) new CTabItem (folder, SWT.NONE).setText ("Tab " + i);
		folder.setSelection (1);
		return new Probe ("CTabFolder", folder, true, true);
	}

	static Label label (Composite parent, String text, int colour) {
		Label label = new Label (parent, SWT.NONE);
		label.setText (text);
		label.setBackground (parent.getDisplay ().getSystemColor (colour));
		return label;
	}

	static Probe banner (Shell shell) {
		CBanner banner = new CBanner (shell, SWT.NONE);
		banner.setLayoutData (fill ());
		banner.setLeft (label (banner, "CBanner left", SWT.COLOR_YELLOW));
		banner.setRight (label (banner, "right", SWT.COLOR_CYAN));
		return new Probe ("CBanner", banner, true, true);
	}

	static Probe viewForm (Shell shell) {
		ViewForm form = new ViewForm (shell, SWT.BORDER);
		form.setLayoutData (fill ());
		form.setTopLeft (label (form, "ViewForm", SWT.COLOR_YELLOW));
		Composite content = new Composite (form, SWT.NONE);
		content.setBackground (shell.getDisplay ().getSystemColor (SWT.COLOR_WHITE));
		form.setContent (content);
		return new Probe ("ViewForm", form, true, true);
	}

	/* a Paint listener fills the composite red; a native green child must stay green on the window (CHILD_CLIP) */
	static Probe childClip (Shell shell) {
		Composite clip = new Composite (shell, SWT.NONE);
		clip.setLayoutData (fill ());
		clip.addPaintListener (e -> {
			e.gc.setBackground (shell.getDisplay ().getSystemColor (SWT.COLOR_RED));
			e.gc.fillRectangle (clip.getClientArea ());
		});
		Composite child = new Composite (clip, SWT.NONE);
		child.setBackground (shell.getDisplay ().getSystemColor (SWT.COLOR_GREEN));
		child.setBounds (CHILD);
		return new Probe ("ChildClip", clip, true, true);
	}

	/* a pure container: no Paint listener (none added for counting either), so the stock path in every mode (CONTAINER_OPT_OUT) */
	static Probe container (Shell shell) {
		Composite container = new Composite (shell, SWT.NONE);
		container.setLayoutData (fill ());
		Button button = new Button (container, SWT.PUSH);
		button.setText ("native child");
		button.setBounds (20, 20, 120, 30);
		return new Probe ("Container", container, false, false);
	}

	/* Nebula CollapsibleButtons (a Composite with a Paint listener whose buttons are Composites with Paint listeners), by reflection */
	static Probe nebula (Shell shell) {
		try {
			Class<?> type = Class.forName ("org.eclipse.nebula.widgets.collapsiblebuttons.CollapsibleButtons");
			Composite buttons = (Composite) type.getConstructor (Composite.class, int.class).newInstance (shell, SWT.NONE);
			buttons.setLayoutData (fill ());
			var add = type.getMethod ("addButton", String.class, String.class, Image.class, Image.class);
			Image big = image (shell.getDisplay (), 24), small = image (shell.getDisplay (), 16);
			for (String name : new String [] {"Mail", "Calendar", "Contacts"}) add.invoke (buttons, name, name, big, small);
			return new Probe ("CollapsibleButtons", buttons, true, true);
		} catch (ReflectiveOperationException | LinkageError e) {
			ERRORS.add ("nebula: " + e);
			return null;
		}
	}

	/* a magenta square with a green centre (the leaf-5 CLabel image), drawn through drawImage by the Nebula buttons; lives until exit */
	static Image image (Display display, int size) {
		Image image = new Image (display, size, size);
		GC gc = new GC (image);
		gc.setBackground (display.getSystemColor (SWT.COLOR_MAGENTA));
		gc.fillRectangle (0, 0, size, size);
		gc.setBackground (display.getSystemColor (SWT.COLOR_GREEN));
		gc.fillRectangle (size / 3, size / 3, size / 3, size / 3);
		gc.dispose ();
		return image;
	}

	/* the window pixels (copyArea from the window DC) at the child and at the parent, before any print (CHILD_CLIP) */
	static Map<String, Object> screen (Probe p) {
		Map<String, Object> out = new HashMap<> ();
		Point size = p.control.getSize ();
		Image image = new Image (p.control.getDisplay (), size.x, size.y);
		GC gc = new GC (p.control);
		guarded (() -> gc.copyArea (image, 0, 0));
		gc.dispose ();
		ImageData data = image.getImageData ();
		image.dispose ();
		out.put ("screenChild", rgb (data, CHILD.x + CHILD.width / 2, CHILD.y + CHILD.height / 2));
		out.put ("screenParent", rgb (data, 10, 10));
		out.put ("dcExcludesChild", dcExcludes (p.control, CHILD.x + CHILD.width / 2, CHILD.y + CHILD.height / 2));
		png (data, OUT + "/screen_ChildClip_" + MODE + ".png", p);
		return out;
	}

	/* whether the composite's device context (system region, screen coordinates) leaves the client point out */
	static boolean dcExcludes (Composite control, int x, int y) {
		long hdc = OS.GetDC (control.handle), rgn = OS.CreateRectRgn (0, 0, 0, 0);
		POINT pt = new POINT ();
		pt.x = x;
		pt.y = y;
		OS.MapWindowPoints (control.handle, 0, pt, 1);
		boolean inside = OS.GetRandomRgn (hdc, rgn, OS.SYSRGN) == 1 && OS.PtInRegion (rgn, pt.x, pt.y);
		OS.DeleteObject (rgn);
		OS.ReleaseDC (control.handle, hdc);
		return !inside;
	}

	static void capture (Display display, Probe p) {
		Point size = p.control.getSize ();
		Image image = new Image (display, size.x, size.y);
		GC gc = new GC (image);
		int before = p.extension;
		guarded (() -> p.control.print (gc));
		gc.dispose ();
		p.fact ("printThroughM3", p.extension - before);
		p.print = image.getImageData ();
		image.dispose ();
		M3CanvasHandler h = p.handler ();
		if (h != null) p.buffer = buffer (h, size.x, size.y);
		save (p);
		p.stock = stock (p);
		p.fact ("reference", p.stock != null ? "stock-run print" : "this run's print");
	}

	static ImageData stock (Probe p) {
		Path file = Path.of (OUT, "print_" + p.name + "_stock.png");
		return "stock".equals (MODE) || !Files.exists (file) ? null : new ImageData (file.toString ());
	}

	static ImageData buffer (M3CanvasHandler h, int width, int height) {
		ImageData data = new ImageData (width, height, 24, new PaletteData (0xFF0000, 0xFF00, 0xFF));
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) data.setPixel (x, y, Math.max (0, h.pixel (x, y)));
		return data;
	}

	static void save (Probe p) {
		png (p.print, OUT + "/print_" + p.name + "_" + MODE + ".png", p);
		if (p.buffer != null) png (p.buffer, OUT + "/buffer_" + p.name + "_" + MODE + ".png", p);
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
		if (x < 0 || y < 0 || x >= data.width || y >= data.height) return -1;
		RGB c = data.palette.getRGB (data.getPixel (x, y));
		return c.red << 16 | c.green << 8 | c.blue;
	}

	static boolean masked (List<Rectangle> mask, int x, int y) {
		return mask.stream ().anyMatch (r -> r.contains (x, y));
	}

	/* exact mismatches inside r, outside the mask */
	static int mismatches (ImageData a, ImageData b, Rectangle r, List<Rectangle> mask) {
		int n = 0;
		for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) if (!masked (mask, x, y) && rgb (a, x, y) != rgb (b, x, y)) n++;
		return n;
	}

	static int count (ImageData d, Rectangle r, List<Rectangle> mask, IntPredicate test) {
		int n = 0;
		for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) if (!masked (mask, x, y) && test.test (rgb (d, x, y))) n++;
		return n;
	}

	/* ink: a dark pixel (text, close crosses), independent of how either renderer anti-aliases its edge colours */
	static boolean dark (int c) {
		return c >= 0 && Math.max (c >> 16 & 0xFF, Math.max (c >> 8 & 0xFF, c & 0xFF)) < 128;
	}

	/* the most frequent colour inside r, outside the mask */
	static int dominant (ImageData d, Rectangle r, List<Rectangle> mask) {
		Map<Integer, Integer> histogram = new HashMap<> ();
		for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) if (!masked (mask, x, y)) histogram.merge (rgb (d, x, y), 1, Integer::sum);
		return histogram.entrySet ().stream ().max (Map.Entry.comparingByValue ()).map (Map.Entry::getKey).orElse (-1);
	}

	/* mean absolute channel difference inside r, outside the mask */
	static double meanDelta (ImageData a, ImageData b, Rectangle r, List<Rectangle> mask) {
		long sum = 0, n = 0;
		for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) {
			if (masked (mask, x, y)) continue;
			int p = rgb (a, x, y), q = rgb (b, x, y);
			sum += Math.abs ((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs ((p >> 8 & 0xFF) - (q >> 8 & 0xFF)) + Math.abs ((p & 0xFF) - (q & 0xFF));
			n += 3;
		}
		return n == 0 ? 0 : sum / (double) n;
	}

	static boolean within (int a, int b) {
		return Math.abs (b - a) <= TEXT_TOLERANCE * Math.max (1, a);
	}

	static Rectangle area (Probe p) {
		Point size = p.control.getSize ();
		return new Rectangle (0, 0, size.x, size.y);
	}

	static void common (Probe p) {
		M3CanvasHandler h = p.handler ();
		p.fact ("handler", h != null);
		p.fact ("backend", h == null ? "none" : h.isGpu () ? "gpu" : "raster");
		p.fact ("paints", p.screenPaints);
		p.fact ("gcExtension", p.screenExtension);
		p.fact ("children", p.children ().size ());
		p.require ("handler", (h != null) == (DRAWN && p.covered));
		if (!p.covered) return;
		p.require ("paints", p.screenPaints > 0);
		p.require ("gcExtension", h != null ? p.screenExtension == p.screenPaints : p.screenExtension == 0);
	}

	/* region (flat, exact) and ink (within the tolerance) of one rectangle, reference against the buffer */
	static void region (Probe p, String key, Rectangle r, List<Rectangle> mask, boolean exact) {
		p.fact ("ref" + key + "Ink", count (p.reference (), r, mask, M3CompositeProbe::dark));
		if (p.buffer == null) return;
		int mismatch = mismatches (p.reference (), p.buffer, r, mask), ref = count (p.reference (), r, mask, M3CompositeProbe::dark);
		int ink = count (p.buffer, r, mask, M3CompositeProbe::dark);
		p.fact ("buffer" + key + "Ink", ink);
		p.fact (key + "Mismatch", mismatch);
		if (exact) p.require (key + "Mismatch", mismatch == 0); else p.require (key + "Ink", within (ref, ink));
	}

	static void checkTabs (Probe p) {
		common (p);
		CTabFolder folder = (CTabFolder) p.control;
		Rectangle client = folder.getClientArea (), all = area (p), selected = folder.getSelection ().getBounds ();
		Rectangle other = folder.getItem (0).getBounds ();
		p.fact ("tabHeight", folder.getTabHeight ());
		p.fact ("selectedBounds", selected.x + "," + selected.y + "," + selected.width + "," + selected.height);
		region (p, "body", client, List.of (), true);
		region (p, "strip", new Rectangle (0, 0, all.width, client.y), List.of (), false);
		region (p, "close", new Rectangle (selected.x + selected.width - 22, selected.y, 22, selected.height), List.of (), false);
		fill (p, "selected", selected);
		fill (p, "unselected", other);
	}

	/* a fill by its dominant colour: equal in the reference and the buffer */
	static void fill (Probe p, String key, Rectangle r) {
		int ref = dominant (p.reference (), r, List.of ());
		p.fact ("ref" + key + "Fill", String.format ("%06x", ref));
		if (p.buffer == null) return;
		int buf = dominant (p.buffer, r, List.of ());
		p.fact ("buffer" + key + "Fill", String.format ("%06x", buf));
		p.require (key + "Fill", ref == buf);
	}

	static void checkBanner (Probe p) {
		common (p);
		CBanner banner = (CBanner) p.control;
		Rectangle all = area (p), left = banner.getLeft ().getBounds (), right = banner.getRight ().getBounds ();
		int x0 = left.x + left.width, x1 = Math.max (x0, right.x);
		Rectangle band = new Rectangle (x0, 0, x1 - x0, all.height);
		List<Rectangle> mask = new ArrayList<> (p.children ());
		region (p, "curve", band, mask, true);
		mask.add (band);
		region (p, "flat", all, mask, true);
	}

	static void checkViewForm (Probe p) {
		common (p);
		Rectangle all = area (p);
		List<Rectangle> ring = List.of (new Rectangle (0, 0, all.width, 1), new Rectangle (0, all.height - 1, all.width, 1),
			new Rectangle (0, 0, 1, all.height), new Rectangle (all.width - 1, 0, 1, all.height));
		if (p.buffer != null) {
			int border = ring.stream ().mapToInt (r -> mismatches (p.reference (), p.buffer, r, p.children ())).sum ();
			p.fact ("borderMismatch", border);
			p.require ("borderMismatch", border == 0);
		}
		List<Rectangle> mask = new ArrayList<> (p.children ());
		mask.addAll (ring);
		region (p, "flat", all, mask, true);
	}

	static void checkClip (Probe p, Map<String, Object> screen) {
		common (p);
		screen.forEach ((k, v) -> p.fact (k, v instanceof Integer i ? String.format ("%06x", i) : v));
		p.require ("dcExcludesChild", Boolean.TRUE.equals (screen.get ("dcExcludesChild")));
		boolean windowPixels = Integer.valueOf (RED).equals (screen.get ("screenParent"));
		p.fact ("windowPixels", windowPixels ? "available" : "unavailable on this desktop");
		if (windowPixels) p.require ("screenChild", Integer.valueOf (GREEN).equals (screen.get ("screenChild")));
		region (p, "flat", area (p), p.children (), true);
		if (p.buffer == null) return;
		p.fact ("bufferUnderChild", String.format ("%06x", rgb (p.buffer, CHILD.x + CHILD.width / 2, CHILD.y + CHILD.height / 2)));
	}

	/* the stock path in every mode: no handler, this run's print equal to the stock run's outside the native child */
	static void checkContainer (Probe p) {
		common (p);
		if (p.stock == null) return;
		int mismatch = mismatches (p.stock, p.print, area (p), p.children ());
		p.fact ("stockMismatch", mismatch);
		p.require ("stockMismatch", mismatch == 0);
	}

	static void checkNebula (Probe p) {
		common (p);
		long handled = descendants (p.control).stream ().filter (c -> c.getData (M3CanvasHandler.DATA_KEY) != null).count ();
		p.fact ("handledComposites", handled);
		region (p, "all", area (p), p.children (), false);
		if (p.buffer == null) return;
		double delta = meanDelta (p.reference (), p.buffer, area (p), p.children ());
		p.fact ("meanDelta", String.format ("%.2f", delta));
		p.require ("meanDelta", delta <= MEAN_DELTA);
		for (Control child : p.control.getChildren ()) if (child instanceof Composite c && c.getData (M3CanvasHandler.DATA_KEY) != null) nested (p, c);
	}

	/*
	 * A nested self-painting composite (Nebula's CustomButton, ToolbarComposite): its own back buffer against the reference print
	 * at its bounds (the print draws children); image pixels (the magenta box) exact, ink within the tolerance, mean delta.
	 */
	static void nested (Probe p, Composite child) {
		Rectangle b = child.getBounds ();
		ImageData ref = crop (p.reference (), b), buf = buffer ((M3CanvasHandler) child.getData (M3CanvasHandler.DATA_KEY), b.width, b.height);
		Rectangle all = new Rectangle (0, 0, b.width, b.height), box = box (ref, 0xFF00FF);
		String key = child.getClass ().getSimpleName () + "@" + b.y;
		png (buf, OUT + "/buffer_" + p.name + "_" + key.replace ('@', '_') + "_" + MODE + ".png", p);
		int refInk = count (ref, all, List.of (), M3CompositeProbe::dark), ink = count (buf, all, List.of (), M3CompositeProbe::dark);
		double delta = meanDelta (ref, buf, all, List.of ());
		int image = box == null ? -1 : mismatches (ref, buf, box, List.of ());
		p.fact (key, "mismatch=" + mismatches (ref, buf, all, List.of ()) + ",ink=" + refInk + "/" + ink + ",meanDelta=" + String.format ("%.2f", delta) + ",image=" + image);
		p.require (key + ".ink", within (refInk, ink));
		p.require (key + ".meanDelta", delta <= MEAN_DELTA);
		if (box != null) p.require (key + ".image", image == 0);
	}

	static ImageData crop (ImageData d, Rectangle r) {
		ImageData out = new ImageData (r.width, r.height, 24, new PaletteData (0xFF0000, 0xFF00, 0xFF));
		for (int y = 0; y < r.height; y++) for (int x = 0; x < r.width; x++) out.setPixel (x, y, Math.max (0, rgb (d, r.x + x, r.y + y)));
		return out;
	}

	/* the bounding box of the pixels of one colour */
	static Rectangle box (ImageData d, int colour) {
		int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = -1, b = -1;
		for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) if (rgb (d, x, y) == colour) {
			l = Math.min (l, x); t = Math.min (t, y); r = Math.max (r, x); b = Math.max (b, y);
		}
		return r < 0 ? null : new Rectangle (l, t, r - l + 1, b - t + 1);
	}

	/* the fork's composite classes constructible as (Composite, int), and Nebula's when present (OLE sites and Browser: foreign by class) */
	static final String [] CENSUS = {"org.eclipse.swt.widgets.Composite", "org.eclipse.swt.custom.CTabFolder", "org.eclipse.swt.custom.CBanner",
		"org.eclipse.swt.custom.ViewForm", "org.eclipse.swt.custom.CCombo", "org.eclipse.swt.custom.SashForm",
		"org.eclipse.swt.custom.ScrolledComposite", "org.eclipse.swt.widgets.Group", "org.eclipse.swt.widgets.TabFolder",
		"org.eclipse.swt.widgets.Table", "org.eclipse.swt.widgets.Tree", "org.eclipse.swt.widgets.ToolBar", "org.eclipse.swt.widgets.CoolBar",
		"org.eclipse.swt.widgets.Combo", "org.eclipse.swt.widgets.Spinner", "org.eclipse.swt.widgets.DateTime",
		"org.eclipse.swt.widgets.ExpandBar", "org.eclipse.nebula.widgets.collapsiblebuttons.CollapsibleButtons"};

	/*
	 * Measured inventory (COVERAGE.tsv): each class in its own cell of a second shell, opened and painted; per class whether it
	 * listens to Paint after construction (the subclass constructor's own listener) and whether it got the M3 handler.
	 */
	static void census (Display display) throws IOException {
		Shell shell = shell (display, 900, 700, 6);
		Map<String, Composite> made = new LinkedHashMap<> ();
		for (String name : CENSUS) guarded (() -> made.put (name, make (shell, name)));
		guarded (shell::open);
		settle (display, 1000);
		List<String> rows = new ArrayList<> (List.of ("mode\tclass\tlistensPaint\thandler"));
		made.forEach ((name, c) -> rows.add (MODE + "\t" + name.substring (name.lastIndexOf ('.') + 1) + "\t" + c.isListening (SWT.Paint)
			+ "\t" + (c.getData (M3CanvasHandler.DATA_KEY) != null)));
		Files.write (Path.of (OUT, "census_" + MODE + ".tsv"), rows);
		System.out.println ("PROBE census classes=" + made.size () + " handled=" + rows.stream ().filter (r -> r.endsWith ("\ttrue")).count ());
		shell.dispose ();
	}

	static Composite make (Shell shell, String name) {
		try {
			Composite c = (Composite) Class.forName (name).getConstructor (Composite.class, int.class).newInstance (shell, SWT.NONE);
			c.setLayoutData (fill ());
			return c;
		} catch (ReflectiveOperationException | LinkageError e) {
			throw new IllegalStateException ("census " + name + ": " + e);
		}
	}

	static List<Composite> descendants (Composite root) {
		List<Composite> out = new ArrayList<> ();
		for (Control child : root.getChildren ()) {
			if (child instanceof Composite c) {
				out.add (c);
				out.addAll (descendants (c));
			}
		}
		return out;
	}

	/* one probe's line on stdout and in the TSV; whether it did not fail */
	static boolean row (Probe p, List<String> rows) {
		StringBuilder facts = new StringBuilder ();
		p.facts.forEach ((k, v) -> facts.append (k).append ('=').append (v).append (' '));
		System.out.println ("PROBE mode=" + MODE + " widget=" + p.name + " result=" + p.result + " " + facts.toString ().trim ());
		rows.add (MODE + "\t" + p.name + "\t" + p.result + "\t" + facts.toString ().trim ());
		return !"FAIL".equals (p.result);
	}

	static boolean report (List<Probe> probes, boolean nebulaMissing) throws IOException {
		List<String> rows = new ArrayList<> ();
		rows.add ("mode\twidget\tresult\tfacts");
		boolean pass = probes.stream ().map (p -> row (p, rows)).toList ().stream ().allMatch (ok -> ok);
		if (nebulaMissing) rows.add (MODE + "\tCollapsibleButtons\tNOT_RUN\tNebula classes not on the class path");
		ERRORS.stream ().distinct ().forEach (e -> rows.add (MODE + "\texception\tFAIL\t" + e));
		ERRORS.stream ().distinct ().limit (3).forEach (e -> System.out.println ("PROBE exception " + e));
		pass &= ERRORS.isEmpty ();
		System.out.println ("PROBE exceptions=" + ERRORS.size ());
		Files.write (Path.of (OUT, "probe_" + MODE + ".tsv"), rows);
		System.out.println (pass ? "PROBE PASS" : "PROBE FAIL");
		return pass;
	}
}
