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
 *     Hitesh Soliwal (Synexia) - Canvas layer contract coverage
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Original Canvas API and offscreen layer-presentation contracts. These tests
 * exercise the real Canvas paint listener and GC, but do not claim OS screen
 * capture qualification. Mobile executes the same tests through its existing
 * test-only transport; desktop suites use the platform's SWT graphics.
 */
public class Test_CanvasLayers {
	protected static final int RED = 0xffff0000;
	protected static final int GREEN = 0xff00ff00;
	protected static final int BLUE = 0xff0000ff;
	protected static final int WHITE = 0xffffffff;
	protected Display display;
	protected Shell shell;
	protected Canvas canvas;
	private boolean ownsDisplay;

	/** Allows an existing test transport to be installed before Display creation. */
	protected void beforeDisplay () { }
	/** Allows the test transport to check ownership after widget cleanup. */
	protected void afterDisplay () { }

	@BeforeEach
	public void createCanvas () {
		beforeDisplay ();
		display = Display.getCurrent ();
		ownsDisplay = display == null;
		if (ownsDisplay) display = new Display ();
		shell = new Shell (display);
		canvas = new Canvas (shell, SWT.NONE);
		canvas.setBounds (0, 0, 32, 32);
	}

	@AfterEach
	public void disposeCanvas () {
		try {
			try {
				if (shell != null && !shell.isDisposed ()) shell.dispose ();
			} finally {
				if (ownsDisplay && display != null && !display.isDisposed ()) display.dispose ();
			}
		} finally {
			afterDisplay ();
		}
	}

	protected static ImageData solid (int width, int height, int argb) {
		ImageData data = new ImageData (width, height, 32,
				new PaletteData (0xff0000, 0xff00, 0xff));
		int [] row = new int [width];
		Arrays.fill (row, argb & 0xffffff);
		for (int y = 0; y < height; y++) data.setPixels (0, y, width, row, 0);
		data.alpha = argb >>> 24;
		return data;
	}

	protected ImageData render (int width, int height) {
		Image image = new Image (display, width, height);
		try {
			GC gc = new GC (image);
			try {
				gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
				gc.fillRectangle (0, 0, width, height);
				paint (gc, 0, 0, width, height);
			} finally {
				gc.dispose ();
			}
			return image.getImageData ();
		} finally {
			image.dispose ();
		}
	}

	protected void paint (GC gc, int x, int y, int width, int height) {
		Event event = new Event ();
		event.gc = gc;
		event.x = x;
		event.y = y;
		event.width = width;
		event.height = height;
		canvas.notifyListeners (SWT.Paint, event);
	}

	protected static int rgb (ImageData image, int x, int y) {
		RGB value = image.palette.getRGB (image.getPixel (x, y));
		return 0xff000000 | value.red << 16 | value.green << 8 | value.blue;
	}

	@Test
	public void test_emptyQueriesPreserveOrdinaryCanvas () {
		int paints = canvas.getListeners (SWT.Paint).length;
		assertArrayEquals (new int [0], canvas.getLayerIds ());
		assertEquals (-1, canvas.getLayerAtPoint (0, 0));
		canvas.removeAllLayers ();
		assertEquals (paints, canvas.getListeners (SWT.Paint).length);
		assertEquals (0, canvas.getChildren ().length);
		assertEquals (WHITE, rgb (render (4, 4), 1, 1));
	}

	@Test
	public void test_invalidFirstLayerLeavesCanvasListenerStateUnchanged () {
		int paints = canvas.getListeners (SWT.Paint).length;
		int disposals = canvas.getListeners (SWT.Dispose).length;
		ImageData invalid = solid (2, 2, BLUE);
		invalid.depth = 3;
		assertThrows (IllegalArgumentException.class, () -> canvas.addLayer (invalid));
		assertEquals (paints, canvas.getListeners (SWT.Paint).length);
		assertEquals (disposals, canvas.getListeners (SWT.Dispose).length);
		assertArrayEquals (new int [0], canvas.getLayerIds ());
		assertEquals (-1, canvas.getLayerAtPoint (0, 0));
	}

	@Test
	public void test_stableZOrderMatchesPixelsAndHitTesting () {
		int red = canvas.addLayer (solid (4, 4, RED));
		int blue = canvas.addLayer (solid (4, 4, BLUE));
		assertArrayEquals (new int [] {red, blue}, canvas.getLayerIds ());
		assertEquals (BLUE, rgb (render (8, 8), 2, 2));
		assertEquals (blue, canvas.getLayerAtPoint (2, 2));
		canvas.setLayerZOrder (red, Integer.MAX_VALUE);
		canvas.setLayerZOrder (blue, Integer.MIN_VALUE);
		assertArrayEquals (new int [] {blue, red}, canvas.getLayerIds ());
		assertEquals (RED, rgb (render (8, 8), 2, 2));
		assertEquals (red, canvas.getLayerAtPoint (2, 2));
		canvas.setLayerZOrder (red, 0);
		canvas.setLayerZOrder (blue, 0);
		assertArrayEquals (new int [] {red, blue}, canvas.getLayerIds ());
		int [] snapshot = canvas.getLayerIds ();
		snapshot [0] = -33;
		assertArrayEquals (new int [] {red, blue}, canvas.getLayerIds ());
		assertEquals (0, canvas.getChildren ().length);
	}

	@Test
	public void test_opacityAppliesOnceToTheFlattenedImage () {
		canvas.addLayer (solid (3, 1, BLUE));
		// The flattened result of overlapping opaque red shapes is uniformly red.
		int front = canvas.addLayer (solid (3, 1, RED));
		canvas.setLayerOpacity (front, 128);
		ImageData result = render (4, 2);
		for (int x = 0; x < 3; x++) assertEquals (0xff80007f, rgb (result, x, 0));
		assertEquals (128, canvas.getLayerOpacity (front));
		assertEquals (WHITE, rgb (result, 3, 0));
	}

	@Test
	public void test_globalAlphaOverridesAlphaDataAndSourcesAreCopied () {
		ImageData source = solid (2, 1, RED);
		source.alpha = 128;
		source.alphaData = new byte [] {0, (byte)255};
		int layer = canvas.addLayer (source);
		source.setPixel (0, 0, GREEN & 0xffffff);
		source.alpha = 0;
		source.alphaData [1] = 0;
		ImageData result = render (3, 2);
		assertEquals (0xffff7f7f, rgb (result, 0, 0));
		assertEquals (0xffff7f7f, rgb (result, 1, 0));
		assertEquals (layer, canvas.getLayerAtPoint (0, 0));
	}

	@Test
	public void test_indexedTransparencyAndMaskDataRemainTransparent () {
		ImageData indexed = new ImageData (2, 1, 8,
				new PaletteData (new RGB (255, 0, 0), new RGB (0, 0, 255)));
		indexed.setPixels (0, 0, 2, new int [] {0, 1}, 0);
		indexed.transparentPixel = 1;
		int layer = canvas.addLayer (indexed);
		ImageData result = render (3, 2);
		assertEquals (RED, rgb (result, 0, 0));
		assertEquals (WHITE, rgb (result, 1, 0));
		assertEquals (-1, canvas.getLayerAtPoint (1, 0));
		ImageData masked = solid (2, 1, GREEN);
		masked.maskPad = 1;
		masked.maskData = new byte [] {(byte)0x80};
		canvas.setLayerImage (layer, masked);
		result = render (3, 2);
		assertEquals (GREEN, rgb (result, 0, 0));
		assertEquals (WHITE, rgb (result, 1, 0));
		assertEquals (-1, canvas.getLayerAtPoint (1, 0));
	}

	@Test
	public void test_layerMaskClipAndInputPolicyAgreeWithPaint () {
		int back = canvas.addLayer (solid (4, 2, BLUE));
		int front = canvas.addLayer (solid (4, 2, RED));
		byte [] mask = {(byte)255, 0, (byte)128, (byte)255,
				(byte)255, 0, (byte)128, (byte)255};
		canvas.setLayerMask (front, mask);
		mask [0] = 0;
		Rectangle clip = new Rectangle (0, 0, 3, 2);
		canvas.setLayerClip (front, clip);
		clip.width = 0;
		ImageData result = render (5, 3);
		assertEquals (RED, rgb (result, 0, 0));
		assertEquals (BLUE, rgb (result, 1, 0));
		assertEquals (0xff80007f, rgb (result, 2, 0));
		assertEquals (BLUE, rgb (result, 3, 0));
		assertEquals (front, canvas.getLayerAtPoint (0, 0));
		assertEquals (back, canvas.getLayerAtPoint (1, 0));
		assertEquals (back, canvas.getLayerAtPoint (3, 0));
		canvas.setLayerInputEnabled (front, false);
		assertEquals (back, canvas.getLayerAtPoint (0, 0));
		assertEquals (RED, rgb (render (5, 3), 0, 0));
		canvas.setLayerVisible (front, false);
		assertEquals (BLUE, rgb (render (5, 3), 0, 0));
		assertFalse (canvas.getLayerVisible (front));
		assertFalse (canvas.getLayerInputEnabled (front));
		canvas.setLayerVisible (front, true);
		canvas.setLayerInputEnabled (front, true);
		canvas.setLayerClip (front, null);
		canvas.setLayerMask (front, null);
		assertEquals (RED, rgb (render (5, 3), 3, 0));
	}

	@Test
	public void test_transformsAreCopiedAndLocationPreservesScale () {
		int layer = canvas.addLayer (solid (2, 1, RED));
		Transform source = new Transform (display, 2, 0, 0, 2, 3, 4);
		try {
			canvas.setLayerTransform (layer, source);
			source.identity ();
		} finally {
			source.dispose ();
		}
		ImageData result = render (16, 16);
		assertEquals (RED, rgb (result, 3, 4));
		assertEquals (RED, rgb (result, 6, 5));
		assertEquals (WHITE, rgb (result, 7, 5));
		assertEquals (layer, canvas.getLayerAtPoint (6, 5));
		canvas.setLayerLocation (layer, 7, 8);
		Transform copy = new Transform (display);
		try {
			canvas.getLayerTransform (layer, copy);
			float [] elements = new float [6];
			copy.getElements (elements);
			assertArrayEquals (new float [] {2, 0, 0, 2, 7, 8}, elements);
		} finally {
			copy.dispose ();
		}
		result = render (16, 16);
		assertEquals (WHITE, rgb (result, 3, 4));
		assertEquals (RED, rgb (result, 10, 9));
		canvas.setLayerTransform (layer, null);
		assertEquals (RED, rgb (render (4, 2), 1, 0));
		assertEquals (-1, canvas.getLayerAtPoint (-1, 0));
		assertEquals (-1, canvas.getLayerAtPoint (32, 0));
	}

	@Test
	public void test_rejectedMutationsLeavePreviousImageIntact () {
		int layer = canvas.addLayer (solid (2, 2, RED));
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerOpacity (layer, -1));
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerOpacity (layer, 256));
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerMask (layer, new byte [1]));
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerImage (layer, null));
		assertThrows (IllegalArgumentException.class, () -> canvas.getLayerTransform (layer, null));
		assertThrows (IllegalArgumentException.class, () -> canvas.removeLayer (-1));
		ImageData broken = solid (2, 2, BLUE);
		broken.alpha = -1;
		broken.alphaData = new byte [1];
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerImage (layer, broken));
		assertEquals (255, canvas.getLayerOpacity (layer));
		assertArrayEquals (new int [] {layer}, canvas.getLayerIds ());
		assertEquals (RED, rgb (render (3, 3), 1, 1));
	}

	@Test
	public void test_malformedMutableImageDataIsRejectedBeforeReplacingPixels () {
		int layer = canvas.addLayer (solid (2, 2, RED));
		ImageData badDepth = solid (2, 2, BLUE);
		badDepth.depth = 3;
		ImageData badStride = solid (2, 2, BLUE);
		badStride.bytesPerLine = 1;
		ImageData shortData = solid (2, 2, BLUE);
		shortData.data = new byte [1];
		RGB invalidColor = new RGB (0, 0, 255);
		ImageData badPalette = new ImageData (1, 1, 8, new PaletteData (invalidColor));
		invalidColor.red = 256;
		ImageData [] invalid = {badDepth, badStride, shortData, badPalette};
		String [] cases = {"unsupported depth", "undersized stride", "truncated backing data", "mutated palette RGB"};
		for (int index = 0; index < invalid.length; index++) {
			ImageData image = invalid [index];
			assertThrows (IllegalArgumentException.class, () -> canvas.setLayerImage (layer, image), cases [index]);
			assertArrayEquals (new int [] {layer}, canvas.getLayerIds (), cases [index]);
			assertEquals (RED, rgb (render (3, 3), 1, 1), cases [index]);
		}
	}

	@Test
	public void test_removalDoesNotRetargetOldLayerIds () {
		int first = canvas.addLayer (solid (1, 1, RED));
		canvas.removeLayer (first);
		int next = canvas.addLayer (solid (1, 1, BLUE));
		assertNotEquals (first, next);
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerVisible (first, false));
		assertEquals (next, canvas.getLayerAtPoint (0, 0));
		canvas.removeAllLayers ();
		assertArrayEquals (new int [0], canvas.getLayerIds ());
		assertEquals (WHITE, rgb (render (2, 2), 0, 0));
		int afterClear = canvas.addLayer (solid (1, 1, GREEN));
		assertNotEquals (next, afterClear);
		assertThrows (IllegalArgumentException.class, () -> canvas.setLayerZOrder (next, 0));
	}

	@Test
	public void test_paintListenerOrderingRemainsRegistrationOrder () {
		canvas.addListener (SWT.Paint, event -> {
			event.gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
			event.gc.fillRectangle (0, 0, 8, 8);
		});
		canvas.addLayer (solid (4, 4, BLUE));
		canvas.addListener (SWT.Paint, event -> {
			event.gc.setBackground (display.getSystemColor (SWT.COLOR_GREEN));
			event.gc.fillRectangle (1, 1, 1, 1);
		});
		ImageData result = render (8, 8);
		assertEquals (BLUE, rgb (result, 0, 0));
		assertEquals (GREEN, rgb (result, 1, 1));
		assertEquals (RED, rgb (result, 5, 5));
	}

	@Test
	public void test_paintRestoresGcAlphaTransformAndClipping () {
		canvas.addLayer (solid (32, 32, RED));
		Image image = new Image (display, 32, 32);
		try {
			GC gc = new GC (image);
			Transform transform = new Transform (display, 1, 0, 0, 1, 7, 8);
			Transform after = new Transform (display);
			try {
				gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
				gc.fillRectangle (0, 0, 32, 32);
				gc.setTransform (transform);
				gc.setAlpha (73);
				gc.setClipping (0, 0, 4, 4);
				Rectangle before = gc.getClipping ();
				paint (gc, 0, 0, 32, 32);
				assertEquals (73, gc.getAlpha ());
				assertEquals (before, gc.getClipping ());
				gc.getTransform (after);
				float [] elements = new float [6];
				after.getElements (elements);
				assertArrayEquals (new float [] {1, 0, 0, 1, 7, 8}, elements);
			} finally {
				after.dispose ();
				transform.dispose ();
				gc.dispose ();
			}
			ImageData result = image.getImageData ();
			assertEquals (RED, rgb (result, 7, 8));
			assertEquals (RED, rgb (result, 10, 11));
			assertEquals (WHITE, rgb (result, 6, 8));
			assertEquals (WHITE, rgb (result, 11, 11));
		} finally {
			image.dispose ();
		}
	}

	@Test
	public void test_damageClipAndClientBoundsRestrictPainting () {
		canvas.setSize (6, 5);
		canvas.addLayer (solid (20, 20, RED));
		Image image = new Image (display, 10, 10);
		try {
			GC gc = new GC (image);
			try {
				gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
				gc.fillRectangle (0, 0, 10, 10);
				gc.setClipping (1, 1, 8, 8);
				paint (gc, 3, 2, 8, 8);
			} finally {
				gc.dispose ();
			}
			ImageData result = image.getImageData ();
			for (int y = 0; y < 10; y++) {
				for (int x = 0; x < 10; x++) {
					boolean covered = x >= 3 && x < 6 && y >= 2 && y < 5;
					assertEquals (covered ? RED : WHITE, rgb (result, x, y), x + "," + y);
				}
			}
		} finally {
			image.dispose ();
		}
		assertEquals (-1, canvas.getLayerAtPoint (6, 4));
		assertEquals (-1, canvas.getLayerAtPoint (5, 5));
	}

	@Test
	public void test_tiledImageHasNoSeamsOrLostEdgePixels () {
		int width = 259, height = 131;
		canvas.setSize (width, height);
		ImageData source = solid (width, height, RED);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				source.setPixel (x, y, (x & 255) << 16 | (y & 255) << 8 | ((x + y) & 255));
			}
		}
		int layer = canvas.addLayer (source);
		ImageData result = render (width, height);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				assertEquals (rgb (source, x, y), rgb (result, x, y), x + "," + y);
			}
		}
		assertEquals (layer, canvas.getLayerAtPoint (width - 1, height - 1));
	}

	@Test
	public void test_disposedCanvasRejectsLayerCalls () {
		int layer = canvas.addLayer (solid (1, 1, RED));
		canvas.dispose ();
		SWTException error = assertThrows (SWTException.class, canvas::getLayerIds);
		assertEquals (SWT.ERROR_WIDGET_DISPOSED, error.code);
		assertThrows (SWTException.class, () -> canvas.getLayerAtPoint (0, 0));
		assertThrows (SWTException.class, () -> canvas.setLayerOpacity (layer, 1));
		assertThrows (SWTException.class, canvas::removeAllLayers);
	}

	@Test
	public void test_layerCallsPreserveUiThreadGuard () throws Exception {
		canvas.addLayer (solid (1, 1, RED));
		AtomicReference<Throwable> failure = new AtomicReference<> ();
		Thread other = new Thread (() -> {
			try {
				canvas.getLayerIds ();
			} catch (Throwable error) {
				failure.set (error);
			}
		}, "Canvas layer invalid-thread test");
		other.start ();
		other.join (5000);
		assertFalse (other.isAlive ());
		SWTException error = assertInstanceOf (SWTException.class, failure.get ());
		assertEquals (SWT.ERROR_THREAD_INVALID_ACCESS, error.code);
	}
}
