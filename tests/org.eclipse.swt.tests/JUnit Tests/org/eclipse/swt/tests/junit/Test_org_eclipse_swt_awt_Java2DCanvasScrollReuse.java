/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.Java2DCanvas;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Actual SWT widget/GC/Image path; synthetic Paint evidence is not a screen capture. */
public class Test_org_eclipse_swt_awt_Java2DCanvasScrollReuse {
	private Display display;
	private Shell shell;
	private Java2DCanvas canvas;
	private final AtomicLong rendererPixels = new AtomicLong ();

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display, SWT.NO_TRIM);
		shell.setSize (160, 120);
		canvas = new Java2DCanvas (shell, SWT.NONE);
		canvas.setSize (64, 48);
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		canvas.setLogicalContentSize (400, 300);
		shell.open ();
		while (display.readAndDispatch ()) { }
		canvas.setRenderer (graphics -> {
			assertSame (display, Display.getCurrent ());
			java.awt.Rectangle clip = graphics.getClipBounds ();
			rendererPixels.addAndGet ((long)clip.width * clip.height);
			scene (graphics);
		});
	}

	@AfterEach
	public void tearDown () {
		if (shell != null && !shell.isDisposed ()) shell.dispose ();
	}

	private static void scene (Graphics2D graphics) {
		java.awt.Rectangle clip = graphics.getClipBounds ();
		for (int y = clip.y; y < clip.y + clip.height; y++) for (int x = clip.x; x < clip.x + clip.width; x++) {
			graphics.setColor (new java.awt.Color (Math.floorMod (x * 31, 256),
					Math.floorMod (y * 47, 256), Math.floorMod (x * 13 + y * 19, 256)));
			graphics.fillRect (x, y, 1, 1);
		}
	}

	private ImageData paint () {
		Rectangle area = canvas.getClientArea ();
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (image);
		try {
			Event event = new Event ();
			event.gc = gc;
			event.x = area.x; event.y = area.y; event.width = area.width; event.height = area.height;
			canvas.notifyListeners (SWT.Paint, event);
			assertFalse (gc.isDisposed (), "paint keeps caller GC ownership");
			return image.getImageData ();
		} finally {
			gc.dispose ();
			image.dispose ();
		}
	}

	private static int [] pixels (ImageData data) {
		int [] result = new int [data.width * data.height];
		for (int y = 0; y < data.height; y++) for (int x = 0; x < data.width; x++) {
			RGB rgb = data.palette.getRGB (data.getPixel (x, y));
			result [y * data.width + x] = 0xff000000 | rgb.red << 16 | rgb.green << 8 | rgb.blue;
		}
		return result;
	}

	private int [] fresh (int originX, int originY) {
		Rectangle area = canvas.getClientArea ();
		BufferedImage image = new BufferedImage (area.width, area.height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics ();
		try {
			graphics.setClip (0, 0, area.width, area.height);
			graphics.translate (-originX, -originY);
			scene (graphics);
			return image.getRGB (0, 0, area.width, area.height, null, 0, area.width);
		} finally {
			graphics.dispose ();
			image.flush ();
		}
	}

	@Test
	public void test_reuseIsOptInAndDefaultKeepsViewportFixedRenderer () {
		assertFalse (canvas.getScrollReuse ());
		canvas.setRenderer (graphics -> {
			graphics.setTransform (new AffineTransform ());
			java.awt.Rectangle clip = graphics.getClipBounds ();
			rendererPixels.addAndGet ((long)clip.width * clip.height);
			scene (graphics);
		});
		int [] before = pixels (paint ());
		rendererPixels.set (0);
		canvas.setViewportOrigin (7, 0);
		assertArrayEquals (before, pixels (paint ()));
		Rectangle area = canvas.getClientArea ();
		assertEquals ((long)area.width * area.height, rendererPixels.get ());
	}

	@Test
	public void test_horizontalReuseRendersOnlyExposedStrip () {
		canvas.setScrollReuse (true);
		assertTrue (canvas.getScrollReuse ());
		paint ();
		rendererPixels.set (0);
		canvas.setViewportOrigin (7, 0);
		assertArrayEquals (fresh (7, 0), pixels (paint ()));
		assertEquals (7L * canvas.getClientArea ().height, rendererPixels.get ());
		System.out.println ("NATIVE_SCROLL_WORK full=" + canvas.getClientArea ().width * canvas.getClientArea ().height
				+ " rendered=" + rendererPixels.get ());
	}

	@Test
	public void test_verticalAndReverseScrollPreservePixels () {
		canvas.setScrollReuse (true);
		paint ();
		int previous = 0;
		for (int next : new int [] {9, 3, 0}) {
			rendererPixels.set (0);
			canvas.setViewportOrigin (0, next);
			assertArrayEquals (fresh (0, next), pixels (paint ()));
			assertEquals ((long)Math.abs (next - previous) * canvas.getClientArea ().width, rendererPixels.get ());
			previous = next;
		}
	}

	@Test
	public void test_modelRedrawAndModeChangesInvalidateCache () {
		canvas.setScrollReuse (true);
		paint ();
		canvas.redraw ();
		rendererPixels.set (0);
		canvas.setViewportOrigin (4, 0);
		assertArrayEquals (fresh (4, 0), pixels (paint ()));
		Rectangle area = canvas.getClientArea ();
		assertEquals ((long)area.width * area.height, rendererPixels.get ());
		rendererPixels.set (0);
		canvas.setScrollReuse (true);
		paint ();
		assertEquals (0, rendererPixels.get (), "setting the same mode does not invalidate");
		canvas.setScrollReuse (false);
		paint ();
		assertEquals ((long)area.width * area.height, rendererPixels.get ());
	}

	@Test
	public void test_reentrantOriginCannotCommitStaleRaster () {
		canvas.setScrollReuse (true);
		paint ();
		AtomicBoolean move = new AtomicBoolean (true);
		canvas.setRenderer (graphics -> {
			if (move.getAndSet (false)) canvas.setViewportOrigin (5, 0);
			scene (graphics);
		});
		paint (); // The reentrant frame is deliberately not presented.
		assertEquals (5, canvas.getViewportOrigin ().x);
		assertArrayEquals (fresh (5, 0), pixels (paint ()));
	}

	@Test
	public void test_newModeAccessRetainsDisposedWidgetChecks () {
		canvas.dispose ();
		assertThrows (org.eclipse.swt.SWTException.class, () -> canvas.setScrollReuse (true));
		assertThrows (org.eclipse.swt.SWTException.class, () -> canvas.getScrollReuse ());
	}
}
