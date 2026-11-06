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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless: resetting the M3 GC's state (GC seam, leaf 6; atom GC_STATE_RESET, pattern M3-GC-RESET v1; census GF05, GF49).
 * Oracle: the stock win32 GC. getAdvanced is false until advanced graphics are asked for (setAdvanced(true), or a call that
 * needs them: alpha, antialias, interpolation, transform, patterns, path clip); setAdvanced(false) puts alpha back to 255 and
 * drops the patterns, the transform and the clip. A reused GC (a drawn plane disposes and reuses it per owner-draw event)
 * starts every event as a new stock GC does: every attribute at its default.
 */
public class Test_org_eclipse_swt_m3gc_GcStateReset {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	static Pattern pattern() {
		Display d = Display.getDefault();
		return new Pattern(d, 0, 0, 10, 10, d.getSystemColor(SWT.COLOR_RED), d.getSystemColor(SWT.COLOR_BLUE));
	}

	@Test
	public void testAdvancedOnlyWhenAskedFor() {
		M3ExternalGC gc = new M3GcFixture(100).gc;
		assertFalse(gc.getAdvanced(), "a fresh GC is not advanced (CTabFolderRenderer saves its clip then)");
		gc.setAdvanced(true);
		assertTrue(gc.getAdvanced());
		M3ExternalGC other = new M3GcFixture(100).gc;
		other.setAlpha(100);
		assertTrue(other.getAdvanced(), "alpha needs advanced graphics (stock initGdip)");
	}

	@Test
	public void testSetAdvancedFalseResetsAdvancedState() {
		M3GcFixture f = new M3GcFixture(100);
		Pattern pattern = pattern();
		Transform t = new Transform(Display.getDefault(), 1, 0, 0, 1, 5, 5);
		f.gc.setAdvanced(true);
		f.gc.setAlpha(100);
		f.gc.setBackgroundPattern(pattern);
		f.gc.setTransform(t);
		f.gc.setClipping(1, 2, 3, 4);
		f.gc.setAntialias(SWT.ON);
		f.gc.setAdvanced(false);
		assertEquals(255, f.gc.getAlpha(), "StyledText's block selection: alpha back to opaque");
		assertNull(f.gc.getBackgroundPattern());
		assertFalse(f.gc.isClipped());
		assertEquals(SWT.DEFAULT, f.gc.getAntialias(), "no advanced graphics: DEFAULT");
		assertFalse(f.gc.getAdvanced());
		Transform read = new Transform(Display.getDefault());
		f.gc.getTransform(read);
		assertTrue(read.isIdentity(), "the transform dropped");
		read.dispose();
		t.dispose();
		pattern.dispose();
	}

	@Test
	public void testDisposeResetsEveryAttribute() {
		M3GcFixture f = new M3GcFixture(100);
		Pattern pattern = pattern();
		Font font = new Font(Display.getDefault(), "Arial", 20, SWT.BOLD);
		f.gc.setAlpha(50);
		f.gc.setLineAttributes(new LineAttributes(4, SWT.CAP_ROUND, SWT.JOIN_BEVEL, SWT.LINE_CUSTOM, new float[] { 2, 3 }, 1, 3));
		f.gc.setAntialias(SWT.ON);
		f.gc.setTextAntialias(SWT.OFF);
		f.gc.setInterpolation(SWT.HIGH);
		f.gc.setXORMode(true);
		f.gc.setForegroundPattern(pattern);
		f.gc.setBackgroundPattern(pattern);
		f.gc.setFillRule(SWT.FILL_WINDING);
		f.gc.setFont(font);
		f.gc.dispose();
		assertEquals(255, f.gc.getAlpha());
		LineAttributes line = f.gc.getLineAttributes();
		assertEquals(1f, line.width);
		assertEquals(SWT.CAP_FLAT, line.cap);
		assertEquals(SWT.JOIN_MITER, line.join);
		assertEquals(SWT.LINE_SOLID, line.style);
		assertArrayEquals(null, line.dash);
		assertEquals(0f, line.dashOffset);
		assertEquals(10f, line.miterLimit);
		assertEquals(SWT.DEFAULT, f.gc.getAntialias());
		assertEquals(SWT.DEFAULT, f.gc.getTextAntialias());
		assertEquals(SWT.DEFAULT, f.gc.getInterpolation());
		assertFalse(f.gc.getXORMode());
		assertNull(f.gc.getForegroundPattern());
		assertNull(f.gc.getBackgroundPattern());
		assertEquals(SWT.FILL_EVEN_ODD, f.gc.getFillRule());
		assertNull(f.resources.font, "the font goes back to the control's (resources reset)");
		assertFalse(f.gc.getAdvanced());
		font.dispose();
		pattern.dispose();
	}
}
