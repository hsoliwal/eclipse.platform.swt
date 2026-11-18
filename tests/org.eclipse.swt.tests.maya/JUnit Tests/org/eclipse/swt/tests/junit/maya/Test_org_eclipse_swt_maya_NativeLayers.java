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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.custom.LayeredViewport.Layer;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * LayeredViewport image layers composited by the window system through maya (DirectComposition): opacity,
 * visibility and Z order change without the viewport repainting; a zoom other than 1 paints the layer again.
 * Pixels are read from the screen.
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
		GC gc = new GC(display);
		try {
			gc.copyArea(pixel, at.x, at.y);
		} finally {
			gc.dispose();
		}
		ImageData data = pixel.getImageData();
		pixel.dispose();
		return data.palette.getRGB(data.getPixel(0, 0));
	}

	private static void assertNear(RGB expected, RGB actual) {
		assertTrue(Math.abs(expected.red - actual.red) <= 2 && Math.abs(expected.green - actual.green) <= 2
				&& Math.abs(expected.blue - actual.blue) <= 2, "expected " + expected + ", was " + actual);
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
	public void aZoomPaintsTheLayerAgainAndAPanMovesIt() {
		Layer layer = viewport.addImageLayer("red", red, 20, 20);
		shell.open();
		pump(250);
		assertTrue(layer.setNative(true));
		viewport.setOrigin(-30, 0);
		pump(300);
		assertEquals(RED, screen(55, 25), "a pan moves the native layer");
		assertEquals(WHITE, screen(25, 25));
		viewport.setZoom(2);
		pump(300);
		assertFalse(layer.isNative(), "at zoom 2 the image is not drawn one to one: painted");
		assertEquals(RED, screen(150, 50), "painted at twice the size");
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
		assertEquals(BLUE, screen(20, 70));
		viewport.setOrigin(0, 40); // the row scrolls up to 20..60, under the header at 0..40
		pump(300);
		assertEquals(RED, screen(20, 10), "the pinned header stays at the top");
		assertEquals(RED, screen(20, 30), "above the row that scrolled under it");
		assertEquals(BLUE, screen(20, 50), "the row moved up");
		viewport.setOrigin(25, 40); // a horizontal pan moves both
		pump(300);
		assertEquals(RED, screen(10, 10));
		assertEquals(WHITE, screen(20, 10), "the header followed the horizontal pan");
		assertTrue(header.isNative() && row.isNative(), "still composited by the window system");
		header.setNative(false);
		pump(300);
		assertEquals(RED, screen(10, 10), "painted in the store, pinned in the same place");
		assertEquals(WHITE, screen(20, 10));
	}
}
