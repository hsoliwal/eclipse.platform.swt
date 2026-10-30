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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.types.Rect;

/**
 * Headless: the clip state of the M3 GC (GC seam, leaf 6; atom CLIP_STATE, pattern M3-GC-CLIP v1; census GF02, GF03, GF16,
 * GF18). Oracle: the stock win32 GC answers its clip box in points (the paint area when no clip is set, the set rectangle or
 * region within it otherwise), copies a region into its own handle when the clip is set, and replaces the clip on every
 * setClipping whatever setTransform did in between. The canvas side is a StateCanvasDouble that keeps Skia's save stack.
 */
public class Test_org_eclipse_swt_m3gc_ClipState {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testNoClipAnswersTheFrameInPoints() {
		M3GcFixture f = new M3GcFixture(200);
		assertEquals(new Rectangle(0, 0, 200, 150), f.gc.getClipping(), "the 400 x 300 pixel frame at 200 %, in points");
		assertFalse(f.gc.isClipped());
	}

	@Test
	public void testRectangleClipIsAnswered() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.setClipping(10, 20, 30, 40);
		assertEquals(new Rectangle(10, 20, 30, 40), f.gc.getClipping());
		assertTrue(f.gc.isClipped());
	}

	@Test
	public void testRegionClipIsAnswered() {
		M3GcFixture f = new M3GcFixture(100);
		Region region = new Region();
		region.add(10, 20, 30, 40);
		f.gc.setClipping(region);
		region.dispose();
		assertEquals(new Rectangle(10, 20, 30, 40), f.gc.getClipping(), "CTabFolderRenderer reads getClipping() after a Region clip");
	}

	@Test
	public void testRegionClipIsACopy() {
		M3GcFixture f = new M3GcFixture(100);
		Region region = new Region();
		region.add(10, 20, 30, 40);
		f.gc.setClipping(region);
		region.add(100, 100, 50, 50);
		region.dispose();
		Region out = new Region();
		f.gc.getClipping(out);
		assertEquals(new Rectangle(10, 20, 30, 40), out.getBounds(), "the clip as set: later mutation and dispose do not reach it");
		out.dispose();
	}

	@Test
	public void testRegionReplayKeepsTranslateAndNegativeAreaAtZoom() {
		M3GcFixture f = new M3GcFixture(200, Matrix33.IDENTITY);
		Region region = new Region();
		region.add(-10, -10, 30, 30);
		region.translate(5, 5);
		f.gc.setClipping(region);
		region.dispose();
		assertEquals(List.of(new Rect(-10, -10, 50, 50)), f.state().clips(), "(-5, -5, 30, 30) points = (-10, -10) to (50, 50) pixels");
	}

	@Test
	public void testRegionFilledByTheStockGcReplays() {
		int zoom = DPIUtil.getDeviceZoom();
		Image image = new Image(Display.getDefault(), 64, 64);
		GC stock = new GC(image);
		Region region = new Region();
		stock.setClipping(3, 4, 5, 6);
		stock.getClipping(region);
		stock.dispose();
		M3GcFixture f = new M3GcFixture(zoom);
		f.gc.setClipping(region);
		assertEquals(region.getBounds(), f.gc.getClipping(), "a region not built by logged operations is not empty");
		region.dispose();
		image.dispose();
	}

	@Test
	public void testSetClippingReplacesTheClipAfterSetTransform() {
		M3GcFixture f = new M3GcFixture(100, Matrix33.IDENTITY);
		Transform t = new Transform(Display.getDefault(), 1, 0, 0, 1, 7, 9);
		f.gc.setClipping(10, 10, 50, 50);
		f.gc.setTransform(t);
		f.gc.setClipping(20, 20, 10, 10);
		assertEquals(List.of(new Rect(20, 20, 30, 30)), f.state().clips(), "only the second clip is in force");
		assertEquals(Matrix33.makeTranslate(7, 9), f.state().matrix(), "the transform stays");
		f.gc.setClipping((Rectangle) null);
		assertEquals(List.of(), f.state().clips(), "setClipping(null) after setTransform clears the clip");
		assertEquals(Matrix33.makeTranslate(7, 9), f.state().matrix());
		t.dispose();
	}

	@Test
	public void testSetTransformKeepsTheClip() {
		M3GcFixture f = new M3GcFixture(100, Matrix33.IDENTITY);
		Transform t = new Transform(Display.getDefault(), 1, 0, 0, 1, 7, 9);
		f.gc.setClipping(10, 10, 50, 50);
		f.gc.setTransform(t);
		assertEquals(List.of(new Rect(10, 10, 60, 60)), f.state().clips());
		f.gc.dispose();
		assertEquals(List.of(), f.state().clips(), "dispose restores the frame");
		assertEquals(Matrix33.IDENTITY, f.state().matrix());
		t.dispose();
	}

	@Test
	public void testTransformedClipAnswersWorldCoordinates() {
		M3GcFixture f = new M3GcFixture(100);
		Transform t = new Transform(Display.getDefault(), 1, 0, 0, 1, 10, 20);
		f.gc.setTransform(t);
		assertEquals(new Rectangle(-10, -20, 400, 300), f.gc.getClipping(), "the frame seen through the transform (GDI+ visible clip bounds)");
		t.dispose();
	}
}
