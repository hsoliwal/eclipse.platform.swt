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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.IntStream;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.render.m3.gc.DpiScalerUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless: the integer rounding of the M3 GC's scaler (GC seam, leaf 6; census GF41, found by the GF01 background tile test).
 * Oracle: the stock DPIUtil, Math.round (half up) of the scaled value, for negative coordinates as for positive ones. The
 * donor's (v * zoom + 50) / 100 truncates toward zero, so a point at x = -3 drew at -2 even at 100 %.
 */
public class Test_org_eclipse_swt_m3gc_ScaleRounding {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testNegativeCoordinatesScaleLikePositiveOnes() {
		IntStream.of(100, 125, 150, 175, 200).forEach(zoom -> IntStream.rangeClosed(-40, 40).forEach(v -> {
			DpiScalerUtil sc = new DpiScalerUtil(zoom);
			assertEquals(Math.round(v * zoom / 100f), sc.autoScaleUp(v), "scale up " + v + " at " + zoom);
			assertEquals(Math.round(v * 100f / zoom), sc.scaleDown(new Point(v, 0)).x, "scale down " + v + " at " + zoom);
		}));
	}
}
