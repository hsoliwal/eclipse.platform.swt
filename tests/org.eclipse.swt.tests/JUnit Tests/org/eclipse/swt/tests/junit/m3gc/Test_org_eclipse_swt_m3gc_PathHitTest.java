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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless: Path.contains with the M3 GC (GC seam, leaf 6; atom PATH_HIT_TEST, pattern M3-GC-PATH-HIT v1; census GF51).
 * Oracle: the stock win32 Path.contains: GDI+ IsVisible / IsOutlineVisible with the GC's fill rule and a pen of its line
 * attributes. The GCExtension has no GDI+ graphics, so the donor's path threw in initGdip.
 */
public class Test_org_eclipse_swt_m3gc_PathHitTest {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testInsideAndOutline() {
		GC gc = new GCExtension(new M3GcFixture(100).gc);
		Path path = new Path(Display.getDefault());
		path.addRectangle(10, 10, 20, 20);
		assertTrue(path.contains(15, 15, gc, false), "inside the filled path");
		assertFalse(path.contains(5, 5, gc, false), "outside");
		assertTrue(path.contains(10, 15, gc, true), "on the outline");
		assertFalse(path.contains(20, 20, gc, true), "the middle is not on a 1-point outline");
		gc.setLineWidth(30);
		assertTrue(path.contains(20, 20, gc, true), "the GC's line width makes the outline wide");
		path.dispose();
	}

	@Test
	public void testFillRuleOfTheGc() {
		GC gc = new GCExtension(new M3GcFixture(100).gc);
		Path path = new Path(Display.getDefault());
		path.addRectangle(0, 0, 40, 40);
		path.addRectangle(10, 10, 20, 20);
		assertFalse(path.contains(20, 20, gc, false), "even-odd (the default): the inner square is a hole");
		gc.setFillRule(SWT.FILL_WINDING);
		assertTrue(path.contains(20, 20, gc, false), "winding: both squares turn the same way");
		path.dispose();
	}
}
