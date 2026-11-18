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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.maya.MayaComposition;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Layers composited by the window system (DirectComposition) above a shell's own pixels: Z order, offset, opacity
 * and visibility change in one {@code _nApply} each, and the shell is never asked to repaint. Pixels are read from
 * the screen, where the system composites them.
 */
public class Test_org_eclipse_swt_maya_Composition {

	private static final int SIZE = 100, OPAQUE_RED = 0xFFFF0000, OPAQUE_BLUE = 0xFF0000FF;
	private static final RGB WHITE = new RGB(255, 255, 255), RED = new RGB(255, 0, 0), BLUE = new RGB(0, 0, 255);

	private Display display;
	private Shell shell;
	private long composition;
	private int paints;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		shell.setBounds(60, 60, 240, 240);
		shell.open();
		pump(200);
		shell.addListener(SWT.Paint, e -> paints++);
		composition = MayaComposition._nCreate(shell.handle);
		assertNotEquals(0, composition, "a DirectComposition target for the shell");
	}

	@AfterEach
	public void tearDown() {
		if (composition != 0) MayaComposition._nDispose(composition);
		shell.dispose();
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

	private long layer(int argb) {
		long layer = MayaComposition._nAddLayer(composition, SIZE, SIZE);
		assertNotEquals(0, layer);
		int[] pixels = new int[SIZE * SIZE];
		Arrays.fill(pixels, argb);
		assertEquals(0, MayaComposition._nUpload(layer, pixels));
		return layer;
	}

	/* one frame: the layers bottom to top, x, y, opacity and visible per layer */
	private void frame(long[] layers, float... props) {
		assertEquals(0, MayaComposition._nApply(composition, layers, props, layers.length));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
	}

	/* the pixel the screen shows at the shell's (x, y) */
	private RGB screen(int x, int y) {
		Point at = shell.toDisplay(x, y);
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
	public void orderOffsetOpacityAndVisibilityAreCompositedWithoutRepainting() {
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		paints = 0;
		frame(new long[] { red, blue }, 20, 20, 1, 1, 60, 60, 0.5f, 1);
		assertEquals(RED, screen(30, 30));
		assertNear(new RGB(128, 0, 127), screen(80, 80)); // blue at half over red, blended once
		assertNear(new RGB(128, 128, 255), screen(140, 140)); // blue at half over the shell's white
		assertEquals(WHITE, screen(200, 200));

		frame(new long[] { blue, red }, 60, 60, 0.5f, 1, 20, 20, 1, 1); // the same layers, red now on top
		assertEquals(RED, screen(80, 80));
		assertNear(new RGB(128, 128, 255), screen(140, 140));

		frame(new long[] { blue, red }, 110, 60, 1, 1, 20, 20, 1, 1); // blue moved and opaque
		assertEquals(BLUE, screen(140, 140));
		assertEquals(RED, screen(80, 80));

		frame(new long[] { red, blue }, 20, 20, 1, 1, 110, 60, 1, 0); // blue hidden
		assertEquals(WHITE, screen(140, 140));
		assertEquals(0, paints, "the window system composites the layers: the shell never repaints");
	}

	@Test
	public void aRemovedLayerAndADisposedCompositionLeaveTheShellsPixels() {
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		frame(new long[] { red, blue }, 20, 20, 1, 1, 110, 110, 1, 1);
		assertEquals(BLUE, screen(140, 140));
		MayaComposition._nRemoveLayer(composition, blue);
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(WHITE, screen(140, 140));
		assertEquals(RED, screen(30, 30));
		MayaComposition._nDispose(composition);
		composition = 0;
		pump(250);
		assertEquals(WHITE, screen(30, 30), "disposing removes every layer from the window");
	}

	@Test
	public void invalidArgumentsAreRefused() {
		assertEquals(0, MayaComposition._nAddLayer(0, SIZE, SIZE));
		assertEquals(0, MayaComposition._nAddLayer(composition, 0, SIZE));
		long red = MayaComposition._nAddLayer(composition, SIZE, SIZE);
		assertNotEquals(0, MayaComposition._nUpload(red, new int[SIZE]), "too few pixels");
		assertNotEquals(0, MayaComposition._nUpload(0, new int[SIZE * SIZE]));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red }, new float[2], 1), "too few properties");
		assertNotEquals(0, MayaComposition._nApply(0, new long[0], new float[0], 0));
	}

	@Test
	public void invalidNativeHandlesAndFrameArraysNeverReachTheCompositor() {
		long red = layer(OPAQUE_RED);
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(RED, screen(30, 30));
		assertNotEquals(0, MayaComposition._nApply(composition, null, new float[0], 0));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[0], null, 0));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, 1, 1 }, Integer.MAX_VALUE));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { Long.MAX_VALUE },
				new float[] { 20, 20, 1, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red, red },
				new float[] { 20, 20, 1, 1, 20, 20, 1, 1 }, 2));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { Float.NaN, 20, 1, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, Float.POSITIVE_INFINITY, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, -0.1f, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, 1, 0.5f }, 1));
		assertEquals(RED, screen(30, 30), "invalid frames must not alter the last committed frame");
		assertNotEquals(0, MayaComposition._nUpload(Long.MAX_VALUE, new int[SIZE * SIZE]));
		assertEquals(0, MayaComposition._nAddLayer(Long.MAX_VALUE, 2, 2));
		assertEquals(0, MayaComposition._nAddLayer(composition, Integer.MAX_VALUE, Integer.MAX_VALUE));
		assertNotEquals(0, MayaComposition._nWaitForCommit(Long.MAX_VALUE));
		MayaComposition._nRemoveLayer(composition, Long.MAX_VALUE);
		MayaComposition._nDispose(Long.MAX_VALUE);
		MayaComposition._nRemoveLayer(composition, red);
		assertNotEquals(0, MayaComposition._nUpload(red, new int[SIZE * SIZE]),
				"a removed layer handle must be refused");
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 0, 0, 1, 1 }, 1));
	}
}
