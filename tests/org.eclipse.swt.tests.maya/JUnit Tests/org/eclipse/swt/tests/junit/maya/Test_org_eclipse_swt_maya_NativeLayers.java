/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit.maya;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.custom.LayeredViewport.Layer;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.internal.maya.MayaComposition;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * LayeredViewport image layers composited by the window system through maya (DirectComposition): opacity,
 * visibility and Z order change without the viewport repainting. Optional affine/clip native support preserves
 * native image layers while zooming; older libraries retain the painted fallback. Pixels are read from the screen.
 */
public class Test_org_eclipse_swt_maya_NativeLayers {

	private static final RGB WHITE = new RGB(255, 255, 255), RED = new RGB(255, 0, 0), BLUE = new RGB(0, 0, 255);

	private Display display;
	private Shell shell;
	private LayeredViewport viewport;
	private Image red, blue;
	private int paints;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setLayout(new FillLayout());
		viewport = new LayeredViewport(shell, SWT.NONE);
		viewport.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		shell.setBounds(60, 60, 320, 260);
		red = solid(SWT.COLOR_RED);
		blue = solid(SWT.COLOR_BLUE);
		viewport.addListener(SWT.Paint, e -> paints++);
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
		red.dispose();
		blue.dispose();
	}

	private Image solid(int color) {
		Image image = new Image(display, 40, 40);
		GC gc = new GC(image);
		gc.setBackground(display.getSystemColor(color));
		gc.fillRectangle(0, 0, 40, 40);
		gc.dispose();
		return image;
	}

	private void pump(long millis) {
		long end = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < end) {
			while (display.readAndDispatch()) {
				// drain
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private RGB screen(int x, int y) {
		Point at = viewport.toDisplay(x, y);
		Image pixel = new Image(display, 1, 1);
		try {
			GC gc = new GC(display);
			try {
				gc.copyArea(pixel, at.x, at.y);
			} finally {
				gc.dispose();
			}
			ImageData data = pixel.getImageData();
			return data.palette.getRGB(data.getPixel(0, 0));
		} finally {
			pixel.dispose(); // also release this native Image after a failing screen copy
		}
	}

	/**
	 * An incorrect initial native pixel still fails this test. Follow-up bounded
	 * sampling only distinguishes slow DWM presentation from persistent grey.
	 * Neither a successful JNI call nor zero JNI warnings proves a visible pixel.
	 */
	private void assertPinnedPixelPresented(RGB expected, int x, int y) {
		RGB first = screen(x, y);
		if (expected.equals(first)) return;
		RGB last = first;
		int samples = 1;
		long started = System.nanoTime();
		long deadline = started + 1_000_000_000L;
		boolean recovered = false;
		while (System.nanoTime() < deadline && !display.isDisposed() && !shell.isDisposed()) {
			for (int i = 0; i < 64 && display.readAndDispatch(); i++) {
				// Limit the event batch even if a native widget keeps posting messages.
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted while diagnosing native pixel readback", interrupted);
			}
			last = screen(x, y);
			samples++;
			if (expected.equals(last)) {
				recovered = true;
				break;
			}
		}
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
		assertEquals(expected, first, "MAYA_PIXEL_" + (recovered ? "LATE" : "PERSISTENT")
				+ ": first screen pixel differs at (" + x + "," + y + ")"
				+ "; first=" + first + "; last=" + last + "; samples=" + samples
				+ "; follow-upMillis=" + elapsedMillis
				+ "; origin=(" + viewport.getOriginX() + "," + viewport.getOriginY() + ")"
				+ "; zero JNI warnings do not prove OS-composited pixels");
	}

	private static void assertNear(RGB expected, RGB actual) {
		assertTrue(Math.abs(expected.red - actual.red) <= 2 && Math.abs(expected.green - actual.green) <= 2
				&& Math.abs(expected.blue - actual.blue) <= 2, "expected " + expected + ", was " + actual);
	}

	private boolean sceneSupported() {
		// Probe a separate Shell target; the viewport continues to own its target.
		long probe = MayaComposition._nCreate(shell.handle);
		assertNotEquals(0, probe, "capability probe needs a live native composition");
		try {
			long required = MayaComposition.CAP_AFFINE_2D | MayaComposition.CAP_LOCAL_RECT_CLIP;
			boolean available = (MayaComposition.capabilities(probe) & required) == required;
			if (Boolean.getBoolean("swt.maya.scene.required")) {
				assertTrue(available, "required native affine/clip scene ABI is missing");
			}
			return available;
		} finally {
			MayaComposition._nDispose(probe);
		}
	}

	@Test
	public void nativeImageLayersChangeWithoutRepaintingTheViewport() {
		Layer below = viewport.addImageLayer("red", red, 20, 20);
		Layer above = viewport.addImageLayer("blue", blue, 40, 40);
		shell.open();
		pump(250);
		assertTrue(below.setNative(true), "maya composites the layer");
		assertTrue(above.setNative(true));
		pump(300);
		assertEquals(RED, screen(25, 25));
		assertEquals(BLUE, screen(50, 50), "blue above red");
		assertEquals(WHITE, screen(150, 150));
		paints = 0;
		above.setOpacity(128);
		pump(300);
		assertNear(new RGB(128, 0, 127), screen(50, 50)); // half blue over red, blended by the window system
		below.moveTo(1);
		pump(300);
		assertEquals(RED, screen(50, 50), "red now above blue");
		above.setVisible(false);
		pump(300);
		assertEquals(WHITE, screen(70, 70), "hidden");
		assertEquals(0, paints, "opacity, order and visibility of native layers repaint nothing");
	}

	@Test
	public void newlyAddedImageCanBecomeNativeBeforeTheNextPaint() {
		shell.open();
		pump(250);
		// The first paint published an empty stack. The append marks the order dirty.
		Layer added = viewport.addImageLayer("new layer", red, 20, 20);
		assertTrue(added.setNative(true), "native activation must publish the freshly appended layer");
		pump(300);
		assertEquals(RED, screen(25, 25));
	}

	@Test
	public void aPanAndZoomPreserveNativeOrPaintedLayerPixels() {
		Layer layer = viewport.addImageLayer("red", red, 20, 20);
		shell.open();
		pump(250);
		boolean affineScene = sceneSupported();
		assertTrue(layer.setNative(true));
		viewport.setOrigin(-30, 0);
		pump(300);
		assertEquals(RED, screen(55, 25), "a pan moves the native layer");
		assertEquals(WHITE, screen(25, 25));
		viewport.setZoom(2);
		pump(300);
		assertEquals(affineScene, layer.isNative(), "zoom uses advertised affine support or the old painted fallback");
		assertEquals(RED, screen(150, 50), "presented at twice the size");
		viewport.setZoom(1);
		pump(300);
		assertTrue(layer.isNative(), "native again at zoom 1");
		assertEquals(RED, screen(55, 25));
		layer.setNative(false);
		pump(300);
		assertFalse(layer.isNative());
		assertEquals(RED, screen(55, 25), "painted again, in the same place");
	}

	@Test
	public void nativeViewportScaleAndWorldClipMatchThePaintedFallback() {
		Layer layer = viewport.addImageLayer("scaled clipped image", red, 20, 20);
		shell.open();
		pump(250);
		assumeTrue(sceneSupported(), "optional native affine/clip scene ABI is not packaged");
		assertTrue(layer.setNative(true));
		viewport.setZoom(2);
		layer.setWorldClip(new LayeredViewport.WorldBounds(25, 25, 10, 10));
		pump(300);
		assertTrue(layer.isNative(), "the existing image layer consumes native affine and clip capabilities");
		assertEquals(RED, screen(55, 55));
		assertEquals(WHITE, screen(45, 55));
		assertEquals(WHITE, screen(75, 55));
		layer.setNative(false);
		pump(300);
		assertFalse(layer.isNative());
		assertEquals(RED, screen(55, 55), "painted fallback has the same clipped interior");
		assertEquals(WHITE, screen(45, 55));
		assertEquals(WHITE, screen(75, 55));
		assertFalse(red.isDisposed(), "native presentation never takes ownership of the caller Image");
		assertTrue(layer.setNative(true));
		viewport.setZoom(1);
		pump(300);
		assertEquals(RED, screen(30, 30));
		assertEquals(WHITE, screen(50, 30));
		layer.setWorldClip(null); // ordinary legacy apply must clear the previous native local clip
		pump(300);
		assertTrue(layer.isNative());
		assertEquals(RED, screen(50, 30));
	}

	@Test
	public void nativeScaledPixelBoundaryAndPanMatchOpaquePaintedImage() {
		GC ink = new GC(red);
		try {
			ink.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			ink.fillRectangle(20, 0, 20, 40);
		} finally {
			ink.dispose();
		}
		Layer layer = viewport.addImageLayer("contrasting source pixels", red, 20, 20);
		shell.open();
		pump(250);
		assumeTrue(sceneSupported(), "optional native affine/clip scene ABI is not packaged");
		viewport.setZoom(2);
		pump(300);
		assertFalse(layer.isNative());
		assertEquals(RED, screen(79, 60), "last red pixel of the opaque painted image");
		assertEquals(BLUE, screen(80, 60), "first blue pixel of the opaque painted image");
		assertTrue(layer.setNative(true));
		pump(300);
		assertEquals(RED, screen(79, 60), "native nearest sampling must not blend the source boundary");
		assertEquals(BLUE, screen(80, 60));
		viewport.setOrigin(5, 0); // same scale and clip; only the physical client origin changes
		pump(300);
		assertTrue(layer.isNative());
		assertEquals(RED, screen(69, 60), "a scene pan updates its folded matrix translation");
		assertEquals(BLUE, screen(70, 60));
		layer.setNative(false);
		pump(300);
		assertEquals(RED, screen(69, 60));
		assertEquals(BLUE, screen(70, 60));
		assertFalse(red.isDisposed(), "the original layer still references the caller's image");
	}


	@Test
	public void partialNativeImageRedrawChangesOnlyOneRectangle() {
		Layer layer = viewport.addImageLayer("partially dirty", red, 20, 20);
		shell.open();
		pump(250);
		assertTrue(layer.setNative(true));
		pump(300);
		assertEquals(RED, screen(25, 25));
		long identity = layer.getId();
		GC ink = new GC(red);
		try {
			ink.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			ink.fillRectangle(7, 8, 6, 5);
		} finally {
			ink.dispose();
		}
		paints = 0;
		layer.redraw(new Rectangle(7, 8, 6, 5));
		pump(300);
		assertEquals(BLUE, screen(29, 30), "native changed region");
		assertEquals(RED, screen(25, 25), "outside dirty region retained");
		assertEquals(identity, layer.getId(), "stable logical layer identity");
		assertTrue(layer.isNative());
		assertEquals(0, paints, "native dirty pixels need no SWT Canvas paint");
	}

	@Test
	public void batchedNativeImageDamageUsesTwoRectsAndOneCommit() {
		int probe = MayaComposition.uploadDirty(0, new int[0], new int[0], 0);
		if (Boolean.getBoolean("swt.maya.dirty.required")) {
			assertNotEquals(-1, probe, "required native dirty upload ABI is missing");
		}
		assumeTrue(probe != -1, "native dirty image ABI not yet packaged");
		Layer layer = viewport.addImageLayer("two changed regions", red, 20, 20);
		shell.open();
		pump(250);
		assertTrue(layer.setNative(true));
		pump(300);
		GC ink = new GC(red);
		try {
			ink.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			ink.fillRectangle(4, 5, 5, 4);
			ink.setBackground(display.getSystemColor(SWT.COLOR_GREEN));
			ink.fillRectangle(23, 24, 6, 4);
		} finally {
			ink.dispose();
		}
		paints = 0;
		layer.redrawRegions(new Rectangle(4, 5, 5, 4), new Rectangle(23, 24, 6, 4));
		pump(300);
		assertEquals(BLUE, screen(25, 26), "first rectangle");
		assertEquals(new RGB(0, 255, 0), screen(46, 46), "second rectangle");
		assertEquals(RED, screen(35, 35), "pixels between rectangles preserved");
		assertTrue(layer.isNative());
		assertEquals(0, paints, "CPU-staged native changes never require a Canvas repaint");
	}

	@Test
	public void aPinnedNativeHeaderStaysWhileTheRowsPanUnderIt() {
		// a row, then a header above it in Z pinned vertically: both composited by the window system
		Layer row = viewport.addImageLayer("row", blue, 0, 60);
		Layer header = viewport.addImageLayer("header", red, 0, 0);
		header.setPinned(SWT.VERTICAL);
		shell.open();
		pump(250);
		assertTrue(row.setNative(true), "maya composites the row");
		assertTrue(header.setNative(true), "and the pinned header");
		pump(300);
		assertPinnedPixelPresented(BLUE, 20, 70));
		viewport.setOrigin(0, 40); // the row scrolls up to 20..60, under the header at 0..40
		pump(300);
		assertPinnedPixelPresented(RED, 20, 10), "the pinned header stays at the top");
		assertPinnedPixelPresented(RED, 20, 30), "above the row that scrolled under it");
		assertPinnedPixelPresented(BLUE, 20, 50), "the row moved up");
		viewport.setOrigin(25, 40); // a horizontal pan moves both
		pump(300);
		assertPinnedPixelPresented(RED, 10, 10));
		assertPinnedPixelPresented(WHITE, 20, 10), "the header followed the horizontal pan");
		assertTrue(header.isNative() && row.isNative(), "still composited by the window system");
		header.setNative(false);
		pump(300);
		assertPinnedPixelPresented(RED, 10, 10), "painted in the store, pinned in the same place");
		assertPinnedPixelPresented(WHITE, 20, 10));
	}
}
