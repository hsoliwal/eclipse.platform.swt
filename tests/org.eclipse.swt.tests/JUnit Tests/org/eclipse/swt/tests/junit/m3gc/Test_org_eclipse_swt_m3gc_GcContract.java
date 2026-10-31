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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 6)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCData;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.humbleui.types.Rect;

/**
 * Headless (an unopened Shell; no window is shown): the stock widget code's contract with a GC, met by the M3 GC (GC seam,
 * leaf 6; atom ARG_CONTRACT, pattern M3-GC-CONTRACT v1; census GF01). Oracle: the stock win32 GC and widgets. getGCData is
 * never null; Canvas/Composite.drawBackground fill through any GC with the background control's colour or image (else the
 * GC's background), ignoring the GC's alpha and pattern and leaving its state as it was; Control.print draws into any GC.
 * The donor answered a null GCData, so StyledText's line backgrounds threw (leaf 5 probe, GF01_StyledText_stack.txt).
 */
public class Test_org_eclipse_swt_m3gc_GcContract {
	Shell shell;
	Canvas canvas;
	M3GcFixture f;
	GC gc;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@BeforeEach
	void setUp() {
		shell = new Shell(Display.getDefault());
		canvas = new Canvas(shell, SWT.NONE);
		canvas.setSize(40, 30);
		f = new M3GcFixture(100);
		gc = new GCExtension(f.gc);
		gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLUE));
	}

	@AfterEach
	void tearDown() {
		gc.dispose();
		shell.dispose();
	}

	@Test
	public void testGcDataIsThisGcs() {
		GCData data = new M3GcFixture(150).gc.getGCData();
		assertNotNull(data, "stock callers read gc.getGCData() fields");
		assertEquals(-1, data.planeDraw, "no retained plane");
		assertEquals(150, data.nativeZoom);
	}

	@Test
	public void testDrawBackgroundFillsWithTheControlColour() {
		canvas.setBackground(new Color(255, 0, 0));
		gc.setAlpha(100);
		canvas.drawBackground(gc, 10, 20, 30, 40);
		assertEquals(new Rect(10, 20, 40, 60), f.calls("drawRect").get(0).params[0], "the area, in pixels at 100 %");
		assertEquals(0xFFFF0000, f.paint("drawRect").color, "the canvas background, opaque (FillRect ignores the GC alpha)");
		assertEquals(100, gc.getAlpha(), "the GC's alpha restored");
		assertEquals(Display.getDefault().getSystemColor(SWT.COLOR_BLUE).getRGB(), gc.getBackground().getRGB(), "the GC's background restored");
	}

	@Test
	public void testDrawBackgroundWithoutAColourUsesTheGcBackground() {
		canvas.drawBackground(gc, 0, 0, 5, 5);
		assertEquals(0xFF0000FF, f.paint("drawRect").color, "background == -1: the GC's background");
	}

	@Test
	public void testDrawBackgroundTilesTheBackgroundImage() {
		Image image = new Image(Display.getDefault(), 8, 8);
		canvas.setBackgroundImage(image);
		canvas.drawBackground(gc, 0, 0, 20, 10, 3, 0);
		assertEquals(6, f.calls("drawImageRect").size(), "3 x 2 tiles of 8 x 8 over 20 x 10");
		assertEquals(-3f, ((Rect) f.calls("drawImageRect").get(0).params[2]).getLeft(), "the tiles move with the offset");
		assertFalse(f.gc.isClipped(), "the clip set for the tiles is taken back");
		canvas.setBackgroundImage(null);
		image.dispose();
	}

	@Test
	public void testPrintDrawsThroughTheGc() {
		assertTrue(canvas.print(gc), "Control.print into a GCExtension");
		assertEquals(1, f.calls("drawImageRect").size(), "the control printed into an image, drawn through the GC");
	}
}
