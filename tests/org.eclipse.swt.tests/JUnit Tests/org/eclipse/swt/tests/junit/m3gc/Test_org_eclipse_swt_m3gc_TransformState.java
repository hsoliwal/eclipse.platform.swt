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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.IntStream;

import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3Matrix33;

/**
 * Headless: the transform state of the M3 GC (GC seam, leaf 6; atom TRANSFORM_STATE, pattern M3-GC-TRANSFORM v1; census GF15,
 * GF16). Oracle: the stock win32 GC answers its transform in points (getTransform after setTransform reads the same
 * elements), sets it without growing any stack, and draws it on top of the device's own mapping (a frame matrix stays).
 */
public class Test_org_eclipse_swt_m3gc_TransformState {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testGetTransformAnswersPoints() {
		M3GcFixture f = new M3GcFixture(200);
		Transform set = new Transform(Display.getDefault(), 1, 0.5f, 0.25f, 1, 10, 20);
		Transform read = new Transform(Display.getDefault());
		f.gc.setTransform(set);
		f.gc.getTransform(read);
		float[] elements = new float[6];
		read.getElements(elements);
		assertArrayEquals(new float[] { 1, 0.5f, 0.25f, 1, 10, 20 }, elements, 1e-5f, "m11 m12 m21 m22 dx dy as set, at 200 %");
		set.dispose();
		read.dispose();
	}

	@Test
	public void testSetTransformDoesNotGrowTheStack() {
		M3GcFixture f = new M3GcFixture(100, M3Matrix33.IDENTITY);
		Transform t = new Transform(Display.getDefault(), 1, 0, 0, 1, 3, 4);
		IntStream.range(0, 5).forEach(i -> f.gc.setTransform(t));
		assertEquals(2, f.state().getSaveCount(), "one GC layer above the frame, not one per setTransform");
		f.gc.setTransform(null);
		assertEquals(M3Matrix33.IDENTITY, f.state().matrix(), "null is the identity");
		t.dispose();
	}

	@Test
	public void testTransformIsAppliedOnTheFrameMatrix() {
		M3Matrix33 frame = M3Matrix33.makeTranslate(5, 7);
		M3GcFixture f = new M3GcFixture(100, frame);
		Transform t = new Transform(Display.getDefault(), 2, 0, 0, 2, 10, 0);
		f.gc.setTransform(t);
		assertEquals(frame.makeConcat(new M3Matrix33(2, 0, 10, 0, 2, 0, 0, 0, 1)), f.state().matrix(), "frame x GC transform");
		f.gc.setTransform(null);
		assertEquals(frame, f.state().matrix(), "the identity GC transform leaves the frame's matrix");
		t.dispose();
	}
}
