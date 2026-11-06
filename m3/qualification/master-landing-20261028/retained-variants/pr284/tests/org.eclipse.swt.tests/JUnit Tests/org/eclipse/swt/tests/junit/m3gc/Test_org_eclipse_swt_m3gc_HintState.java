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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless: the hint state of the M3 GC (GC seam, leaf 6; atom HINT_STATE, pattern M3-GC-HINT v1; census GF06). Oracle: the
 * stock win32 GC, whose fresh GC answers SWT.DEFAULT for antialias, text antialias and interpolation, reads back what was set,
 * rejects an invalid text antialias, strokes aliased unless antialias is ON, and smooths text by the text antialias alone.
 */
public class Test_org_eclipse_swt_m3gc_HintState {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testFreshGcAnswersDefault() {
		M3GcFixture f = new M3GcFixture(100);
		assertEquals(SWT.DEFAULT, f.gc.getAntialias(), "antialias");
		assertEquals(SWT.DEFAULT, f.gc.getTextAntialias(), "text antialias");
		assertEquals(SWT.DEFAULT, f.gc.getInterpolation(), "interpolation");
	}

	@Test
	public void testSetValuesReadBack() {
		M3GcFixture f = new M3GcFixture(100);
		IntStream.of(SWT.NONE, SWT.LOW, SWT.DEFAULT, SWT.HIGH).forEach(v -> {
			f.gc.setInterpolation(v);
			assertEquals(v, f.gc.getInterpolation(), "interpolation " + v);
		});
		IntStream.of(SWT.ON, SWT.OFF, SWT.DEFAULT).forEach(v -> {
			f.gc.setTextAntialias(v);
			assertEquals(v, f.gc.getTextAntialias(), "text antialias " + v);
		});
	}

	@Test
	public void testInvalidTextAntialiasRejected() {
		M3GcFixture f = new M3GcFixture(100);
		assertThrows(IllegalArgumentException.class, () -> f.gc.setTextAntialias(5));
		assertEquals(SWT.DEFAULT, f.gc.getTextAntialias(), "unchanged after the rejected value");
	}

	@Test
	public void testDefaultInterpolationSamplesLikeAnUntouchedGc() {
		Image image = new Image(Display.getDefault(), 4, 4);
		try {
			M3GcFixture untouched = new M3GcFixture(100), reset = new M3GcFixture(100);
			untouched.gc.drawImage(image, 0, 0, 4, 4, 0, 0, 8, 8);
			reset.gc.setInterpolation(SWT.HIGH);
			reset.gc.setInterpolation(SWT.DEFAULT);
			reset.gc.drawImage(image, 0, 0, 4, 4, 0, 0, 8, 8);
			assertEquals(untouched.calls("drawImageRect").get(0).params[3], reset.calls("drawImageRect").get(0).params[3],
					"one DEFAULT: the sampling of a fresh GC and of setInterpolation(DEFAULT) are the same");
		} finally {
			image.dispose();
		}
	}

	@Test
	public void testDefaultAntialiasDrawsAliased() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.setAntialias(SWT.DEFAULT);
		f.gc.drawLine(1, 1, 20, 20);
		assertFalse(f.paint("drawLine").antiAlias, "DEFAULT strokes aliased (GDI; GDI+ SmoothingModeDefault)");
		f.gc.setAntialias(SWT.ON);
		f.gc.drawLine(1, 1, 20, 20);
		assertTrue(f.paint("drawLine").antiAlias, "ON antialiases");
	}

	@Test
	public void testTextAntialiasAloneDecidesText() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.setAntialias(SWT.ON);
		f.gc.setTextAntialias(SWT.OFF);
		f.gc.drawText("Hint", 10, 20, true);
		assertFalse(f.paint("drawString").antiAlias, "text antialias OFF: aliased text whatever the shape antialias");
	}
}
