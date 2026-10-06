/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.ViewportPaintGraph.Affine;
import org.junit.jupiter.api.*;

@SuppressWarnings("restriction")
public class Test_org_eclipse_swt_internal_ViewportGcProxy {

	private Display display;
	private Image image;
	private GC gc;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		image = new Image (display, 96, 96);
		gc = new GC (image);
	}

	@AfterEach
	public void tearDown () {
		if (gc != null && !gc.isDisposed ()) gc.dispose ();
		if (image != null && !image.isDisposed ()) image.dispose ();
	}

	@Test
	public void test_affineV2FactoriesInverseAndClassification () {
		assertSame (Affine.IDENTITY, Affine.translation (0, 0));
		assertSame (Affine.IDENTITY, Affine.scale (1, 1));
		assertSame (Affine.IDENTITY, Affine.rotation (0));
		assertSame (Affine.IDENTITY, Affine.shear (0, 0));

		Affine quarterTurn = Affine.rotation ((float)(Math.PI / 2d));
		assertEquals (1f, quarterTurn.determinant (), 0.0001f);
		assertFalse (quarterTurn.isTranslationOnly ());
		assertTrue (Affine.translation (4, -7).isTranslationOnly ());
		assertFalse (Affine.shear (1, 0).isTranslationOnly ());

		Affine transform = Affine.translation (40, -10).compose (Affine.scale (2, 4));
		Affine restored = transform.compose (transform.inverse ());
		assertTrue (restored.isIdentity ());

		float [] mapped = new float [2];
		float [] inverse = new float [2];
		quarterTurn.map (1, 2, mapped);
		assertEquals (-2f, mapped [0], 0.0001f);
		assertEquals (1f, mapped [1], 0.0001f);
		assertTrue (quarterTurn.inverseMap (mapped [0], mapped [1], inverse));
		assertArrayEquals (new float [] {1, 2}, inverse, 0.0001f);

		assertThrows (IllegalStateException.class, () -> Affine.scale (0, 1).inverse ());
	}

	@Test
	public void test_gcScopeRestoresCallerStateAfterPlaneMutation () {
		gc.setAdvanced (true);
		if (!gc.getAdvanced ()) return; // Advanced graphics are optional on some native stacks.

		Rectangle originalClip = new Rectangle (3, 5, 70, 60);
		gc.setClipping (originalClip);
		gc.setAlpha (211);
		gc.setAntialias (SWT.OFF);
		gc.setTextAntialias (SWT.ON);
		gc.setInterpolation (SWT.HIGH);
		gc.setFillRule (SWT.FILL_EVEN_ODD);
		gc.setXORMode (true);
		gc.setForeground (display.getSystemColor (SWT.COLOR_DARK_BLUE));
		gc.setBackground (display.getSystemColor (SWT.COLOR_YELLOW));
		LineAttributes originalLine =
				new LineAttributes (3.5f, SWT.CAP_ROUND, SWT.JOIN_BEVEL, SWT.LINE_DASH, null, 0, 10);
		gc.setLineAttributes (originalLine);
		Transform originalTransform = new Transform (display, 1, 0.25f, 0.5f, 1, 7, 9);
		gc.setTransform (originalTransform);

		float [] expectedTransform = elements (originalTransform);
		LineAttributes expectedLine = gc.getLineAttributes ();
		Color expectedForeground = gc.getForeground ();
		Color expectedBackground = gc.getBackground ();

		try (ViewportGcProxy proxy = ViewportGcProxy.wrap (gc)) {
			proxy.clip (new Rectangle (20, 20, 30, 20))
					.transform (Affine.rotation (0.37f).compose (Affine.translation (13, -8)))
					.lineAttributes (new LineAttributes (
							9f, SWT.CAP_SQUARE, SWT.JOIN_ROUND, SWT.LINE_SOLID, null, 0, 10))
					.alpha (77);

			GC scoped = proxy.gc ();
			scoped.setAntialias (SWT.ON);
			scoped.setTextAntialias (SWT.OFF);
			scoped.setInterpolation (SWT.LOW);
			scoped.setFillRule (SWT.FILL_WINDING);
			scoped.setXORMode (false);
			scoped.setForeground (display.getSystemColor (SWT.COLOR_RED));
			scoped.setBackground (display.getSystemColor (SWT.COLOR_GREEN));
			assertNotEquals (originalClip, scoped.getClipping ());
		}

		assertEquals (originalClip, gc.getClipping ());
		assertArrayEquals (expectedTransform, elements (gc), 0.0001f);
		assertLineEquals (expectedLine, gc.getLineAttributes ());
		assertEquals (211, gc.getAlpha ());
		assertEquals (SWT.OFF, gc.getAntialias ());
		assertEquals (SWT.ON, gc.getTextAntialias ());
		assertEquals (SWT.HIGH, gc.getInterpolation ());
		assertEquals (SWT.FILL_EVEN_ODD, gc.getFillRule ());
		assertTrue (gc.getXORMode ());
		assertEquals (expectedForeground, gc.getForeground ());
		assertEquals (expectedBackground, gc.getBackground ());

		originalTransform.dispose ();
	}

	@Test
	public void test_gcScopeIsIdempotentAndRejectsUseAfterClose () {
		ViewportGcProxy proxy = ViewportGcProxy.wrap (gc);
		proxy.close ();
		proxy.close ();
		assertThrows (IllegalStateException.class, proxy::gc);
		assertThrows (IllegalStateException.class, () -> proxy.alpha (1));
	}

	private static float [] elements (Transform transform) {
		float [] result = new float [6];
		transform.getElements (result);
		return result;
	}

	private static float [] elements (GC gc) {
		Transform transform = new Transform (gc.getDevice ());
		try {
			gc.getTransform (transform);
			return elements (transform);
		} finally {
			transform.dispose ();
		}
	}

	private static void assertLineEquals (LineAttributes expected, LineAttributes actual) {
		assertEquals (expected.width, actual.width, 0.0001f);
		assertEquals (expected.cap, actual.cap);
		assertEquals (expected.join, actual.join);
		assertEquals (expected.style, actual.style);
		assertEquals (expected.dashOffset, actual.dashOffset, 0.0001f);
		assertEquals (expected.miterLimit, actual.miterLimit, 0.0001f);
		assertArrayEquals (expected.dash, actual.dash);
	}
}
