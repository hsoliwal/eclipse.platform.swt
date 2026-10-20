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

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 28 (leaf R28-3): with <code>-Dswt.drawn.renderThread=true</code> a drawn plane's frames are drawn on the
 * Display's render thread from snapshots. For a Tree, a Table and a List of 10^4 rows: the render thread's newest frame
 * equals the frame drawn synchronously on the UI thread (a capture), after the first paint, a text change of a visible
 * row and a scroll; frames were rendered and presented; the UI thread's paint and the render thread's frame times are
 * logged. Run with <code>-Dswt.m3.threadCheck=true</code>, a role violation on either thread fails the frame.
 */
public class Test_org_eclipse_swt_widgets_DrawnRenderThread {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_DrawnRenderThread.class.getName ());
	static final int ROWS = 10_000;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 360, 420);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	static RenderTarget target (Control control) throws ReflectiveOperationException {
		Field plane = control.getClass ().getDeclaredField ("drawnPlane");
		plane.setAccessible (true);
		Object drawn = plane.get (control);
		if (drawn == null) return null;
		Field target = Class.forName ("org.eclipse.swt.widgets.DrawnPlane").getDeclaredField ("renderTarget");
		target.setAccessible (true);
		return (RenderTarget) target.get (drawn);
	}

	/* waits (pumping events) until the render thread's newest frame is the newest snapshot's and was presented */
	void settle (RenderTarget target) {
		long deadline = System.currentTimeMillis () + 5_000;
		while (System.currentTimeMillis () < deadline) {
			ViewportDifferential.settle (display, 20);
			if (target.slabs.generation () > 0 && target.rendered.get () > 0 && !target.slabs.fresh () && !target.frames.fresh ()) return;
		}
	}

	/* the render thread's newest frame, the rows area, as image data (the UI thread is the frames' reader) */
	static ImageData frame (RenderTarget target, Control control, int rowsTop) {
		RenderTarget.Frame frame = target.frames.acquire ();
		assertNotNull (frame, "the render thread finished a frame");
		RECT client = clientRect (control);
		int width = Math.min (client.right, frame.width), height = Math.min (client.bottom, frame.height);
		Image image = new Image (control.getDisplay (), width, height);
		GC gc = new GC (image);
		long hdc = Test_org_eclipse_swt_widgets_DrawnRenderThread.handle (gc);
		OS.BitBlt (hdc, 0, 0, width, height, frame.buffer.dc, 0, 0, OS.SRCCOPY);
		gc.dispose ();
		ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	static long handle (GC gc) {
		try {
			Field handle = GC.class.getDeclaredField ("handle");
			handle.setAccessible (true);
			return handle.getLong (gc);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException (e);
		}
	}

	static RECT clientRect (Control control) {
		RECT rect = new RECT ();
		try {
			Field handle = Control.class.getDeclaredField ("handle");
			handle.setAccessible (true);
			OS.GetClientRect (handle.getLong (control), rect);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException (e);
		}
		return rect;
	}

	/* the frame drawn synchronously on the UI thread (WM_PRINTCLIENT), the client area in pixels */
	static ImageData capture (Control control) {
		RECT client = clientRect (control);
		Image image = new Image (control.getDisplay (), Math.max (1, client.right), Math.max (1, client.bottom));
		GC gc = new GC (image);
		ViewportDifferential.send (control, 0x0318, handle (gc), 0x4 | 0x8);
		gc.dispose ();
		ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	void same (String step, Control control, RenderTarget target) {
		settle (target);
		ImageData drawn = capture (control), rendered = frame (target, control, 0);
		String name = control.getClass ().getSimpleName () + "-" + ViewportDifferential.reference (control.getClass ().getSimpleName ()) + "-" + step;
		int differ = 0;
		for (int y = 0; y < Math.min (drawn.height, rendered.height); y++) {
			for (int x = 0; x < Math.min (drawn.width, rendered.width); x++) {
				if (drawn.getPixel (x, y) != rendered.getPixel (x, y)) differ++;
			}
		}
		if (differ > 0) {
			ViewportDifferential.save ("render-thread-ui-" + name, drawn);
			ViewportDifferential.save ("render-thread-frame-" + name, rendered);
		}
		assertEquals (0, differ, "rule 28: the render thread's frame differs from the UI thread's at " + step + " (" + differ + " pixels; render-thread-*-" + name + ".png)");
	}

	void gate (Control control, Runnable change, Runnable scroll) throws Exception {
		RenderTarget target = target (control);
		Assumptions.assumeTrue (target != null, "a drawn mode with -Dswt.drawn.renderThread=true only");
		control.setBounds (5, 5, 340, 380);
		shell.open ();
		same ("open", control, target);
		change.run ();
		control.update ();
		same ("change", control, target);
		scroll.run ();
		control.update ();
		same ("scroll", control, target);
		FrameLog ui = Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.log (control);
		String line = String.format (Locale.ROOT, "%s %s: %d snapshots, %d rendered, %d presented; UI paint p50 %.3f p99 %.3f ms; render frame p50 %.3f p99 %.3f ms; rows drawn %d kept %d",
			control.getClass ().getSimpleName (), ViewportDifferential.reference (control.getClass ().getSimpleName ()), target.slabs.generation (), target.rendered.get (),
			target.presented.get (), ui.frameTime (50) / 1e6, ui.frameTime (99) / 1e6, target.log.frameTime (50) / 1e6, target.log.frameTime (99) / 1e6,
			target.log.rowsDrawn (), target.log.rowsKept ());
		LOG.info ("drawn render thread: " + line);
		assertTrue (target.rendered.get () > 0 && target.presented.get () > 0, line);
	}

	@Test
	public void treeFramesComeFromTheRenderThread () throws Exception {
		Tree tree = new Tree (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER);
		for (int i = 0; i < ROWS; i++) new TreeItem (tree, SWT.NONE).setText ("row " + i);
		gate (tree, () -> tree.getItem (3).setText ("changed"), () -> tree.setTopItem (tree.getItem (500)));
	}

	@Test
	public void tableFramesComeFromTheRenderThread () throws Exception {
		Table table = new Table (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible (true);
		for (int c = 0; c < 2; c++) new TableColumn (table, SWT.NONE).setWidth (150);
		for (int i = 0; i < ROWS; i++) new TableItem (table, SWT.NONE).setText (new String [] {"row " + i, "v" + i});
		gate (table, () -> table.getItem (3).setText (1, "changed"), () -> table.setTopIndex (500));
	}

	@Test
	public void listFramesComeFromTheRenderThread () throws Exception {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		String [] items = new String [ROWS];
		Arrays.setAll (items, i -> "row " + i);
		list.setItems (items);
		gate (list, () -> list.setItem (3, "changed"), () -> list.setTopIndex (500));
	}

}
