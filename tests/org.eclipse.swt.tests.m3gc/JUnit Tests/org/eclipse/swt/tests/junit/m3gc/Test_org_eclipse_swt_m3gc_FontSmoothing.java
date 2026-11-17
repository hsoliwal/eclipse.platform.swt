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
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Set;

import org.eclipse.swt.internal.canvasext.raster.FontSmoothing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.PixelGeometry;

/** The Skia surfaces' text smoothing follows the system's: ClearType in the screen's subpixel order, else grayscale. */
public class Test_org_eclipse_swt_m3gc_FontSmoothing {

	static final int STANDARD = 0x0001, CLEARTYPE = 0x0002, BGR = 0x0000, RGB = 0x0001;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void clearTypeGivesLcdTextInTheScreensSubpixelOrderAndAnythingElseGrayscale() {
		assertEquals(PixelGeometry.RGB_H, FontSmoothing.pixelGeometry(true, CLEARTYPE, RGB));
		assertEquals(PixelGeometry.BGR_H, FontSmoothing.pixelGeometry(true, CLEARTYPE, BGR));
		assertEquals(PixelGeometry.UNKNOWN, FontSmoothing.pixelGeometry(true, STANDARD, RGB), "standard smoothing is grayscale");
		assertEquals(PixelGeometry.UNKNOWN, FontSmoothing.pixelGeometry(false, CLEARTYPE, RGB), "smoothing off: no LCD text");
	}

	@Test
	public void theSystemIsAsked() {
		PixelGeometry now = FontSmoothing.pixelGeometry();
		System.out.println("text smoothing of this system: " + now);
		assertTrue(Set.of(PixelGeometry.RGB_H, PixelGeometry.BGR_H, PixelGeometry.UNKNOWN).contains(now));
		assertEquals(now, FontSmoothing.surfaceProps().getPixelGeometry());
	}
}
