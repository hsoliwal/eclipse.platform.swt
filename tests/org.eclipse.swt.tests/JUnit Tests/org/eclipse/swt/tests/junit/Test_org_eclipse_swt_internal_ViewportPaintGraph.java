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
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

@SuppressWarnings("restriction")
public class Test_org_eclipse_swt_internal_ViewportPaintGraph {
	private Display display;
	private Image image;
	private GC gc;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		image = new Image (display, 64, 64);
		gc = new GC (image);
		gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		gc.fillRectangle (image.getBounds ());
		gc.setBackground (display.getSystemColor (SWT.COLOR_BLACK));
		gc.setForeground (display.getSystemColor (SWT.COLOR_BLACK));
	}

	@AfterEach
	public void tearDown () {
		if (gc != null && !gc.isDisposed ()) gc.dispose ();
		if (image != null && !image.isDisposed ()) image.dispose ();
	}

	@Test
	public void test_sharedTemplateInstancesDoNotDuplicateGeometry () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int row = graph.template ();
		graph.fillRectangle (row, 0, 0, 4, 4);
		graph.instance (graph.root (), row, Affine.translation (10, 10));
		graph.instance (graph.root (), row, Affine.translation (30, 10));

		assertEquals (1, graph.geometryNodeCount ());
		assertEquals (5, graph.nodeCount ());

		var stats = graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (2, stats.drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (11, 11));
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (31, 11));
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (20, 11));
	}

	@Test
	public void test_translationClipCullsWithoutChangingGraph () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int row = graph.template ();
		graph.line (row, 0, 0, 20, 0);

		var hidden = graph.replayTemplate (
				gc, row, Affine.translation (0, 40), new Rectangle (0, 0, 32, 20));
		assertEquals (0, hidden.drawnCommands ());
		assertEquals (1, hidden.culledCommands ());

		var visible = graph.replayTemplate (
				gc, row, Affine.translation (0, 10), new Rectangle (0, 0, 32, 20));
		assertEquals (1, visible.drawnCommands ());
		assertEquals (0, visible.culledCommands ());
		assertEquals (1, graph.geometryNodeCount ());
	}

	@Test
	public void test_generalAffineReplayRestoresCallerTransform () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int template = graph.template ();
		graph.drawRectangle (template, 1, 1, 8, 6);
		graph.instance (graph.root (), template, new Affine (1, 0.25f, 0.5f, 1, 7, 9));

		Transform caller = new Transform (display, 1, 0, 0, 1, 3, 4);
		Transform after = new Transform (display);
		try {
			gc.setTransform (caller);
			graph.replay (gc);
			gc.getTransform (after);

			float [] expected = new float [6];
			float [] actual = new float [6];
			caller.getElements (expected);
			after.getElements (actual);
			assertArrayEquals (expected, actual, 0.0001f,
					"retained replay must restore the caller's GC transform");
		} finally {
			after.dispose ();
			caller.dispose ();
		}
	}

	@Test
	public void test_instanceGraphRejectsCycles () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int row = graph.template ();
		int decoration = graph.template ();
		graph.instance (row, decoration, Affine.IDENTITY);
		assertThrows (IllegalArgumentException.class,
				() -> graph.instance (decoration, row, Affine.IDENTITY));
	}

	@Test
	public void test_affineCompositionMatchesNestedViewportTranslation () {
		Affine body = Affine.translation (-720, -440);
		Affine row = Affine.translation (0, 220);
		Affine combined = body.compose (row);

		assertEquals (-720f, combined.dx ());
		assertEquals (-220f, combined.dy ());
		assertTrue (combined.isIntegralTranslation ());
	}

	private RGB pixelRgb (int x, int y) {
		ImageData data = image.getImageData ();
		int pixel = data.getPixel (x, y);
		return data.palette.getRGB (pixel);
	}

	private RGB rgb (int swtColor) {
		return display.getSystemColor (swtColor).getRGB ();
	}
}
