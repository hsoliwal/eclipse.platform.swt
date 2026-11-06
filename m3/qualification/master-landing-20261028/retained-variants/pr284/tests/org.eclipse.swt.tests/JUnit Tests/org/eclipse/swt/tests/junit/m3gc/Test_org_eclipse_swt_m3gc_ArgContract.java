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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Headless: the argument and paint contracts of drawImage and drawPoint on the M3 GC (GC seam, leaf 6; atom ARG_CONTRACT,
 * pattern M3-GC-CONTRACT v1; census GF24, GF46). Oracle: the stock win32 GC. drawImage returns for a zero source or
 * destination size before any other check, rejects negative sizes and source coordinates, a null image (ERROR_NULL_ARGUMENT)
 * and a disposed one; drawPoint fills one pixel with the foreground brush (colour at the GC alpha, or the foreground
 * pattern), never the background pattern.
 */
public class Test_org_eclipse_swt_m3gc_ArgContract {
	Image image;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@BeforeEach
	void setUp() {
		image = new Image(Display.getDefault(), 4, 4);
	}

	@AfterEach
	void tearDown() {
		image.dispose();
	}

	@Test
	public void testNullImageIsRejected() {
		M3GcFixture f = new M3GcFixture(100);
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(null, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(null, 0, 0, 4, 4));
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(null, 0, 0, 4, 4, 0, 0, 4, 4));
	}

	@Test
	public void testZeroSizeReturnsBeforeAnyCheck() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.drawImage(image, 0, 0, 0, 4, 0, 0, 8, 8);
		f.gc.drawImage(image, 0, 0, 4, 4, 0, 0, 8, 0);
		f.gc.drawImage(null, 0, 0, 0, 0, 0, 0, 0, 0);
		f.gc.drawImage(image, 0, 0, 0, 4);
		assertEquals(0, f.surface.calls.size(), "nothing drawn, nothing thrown");
	}

	@Test
	public void testNegativeArgumentsAreRejected() {
		M3GcFixture f = new M3GcFixture(100);
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(image, -1, 0, 4, 4, 0, 0, 4, 4));
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(image, 0, 0, 4, 4, 0, 0, -4, 4));
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(image, 0, 0, -4, 4));
	}

	@Test
	public void testDisposedImageIsRejected() {
		M3GcFixture f = new M3GcFixture(100);
		Image disposed = new Image(Display.getDefault(), 2, 2);
		disposed.dispose();
		assertThrows(IllegalArgumentException.class, () -> f.gc.drawImage(disposed, 0, 0));
	}

	@Test
	public void testDrawPointUsesTheGcAlpha() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.setAlpha(128);
		f.gc.drawPoint(3, 4);
		assertEquals(128, f.paint("drawRect").color >>> 24, "the foreground at the GC alpha");
	}

	@Test
	public void testDrawPointUsesTheForegroundBrush() {
		M3GcFixture f = new M3GcFixture(100);
		Pattern pattern = new Pattern(Display.getDefault(), 0, 0, 10, 10, Display.getDefault().getSystemColor(SWT.COLOR_RED),
				Display.getDefault().getSystemColor(SWT.COLOR_BLUE));
		f.gc.setBackgroundPattern(pattern);
		f.gc.drawPoint(3, 4);
		assertNull(f.paint("drawRect").shader, "the background pattern is not the point's brush");
		f.gc.setForegroundPattern(pattern);
		f.gc.drawPoint(3, 4);
		assertNotNull(f.paint("drawRect").shader, "the foreground pattern is (GDI+ foreground brush)");
		pattern.dispose();
	}
}
