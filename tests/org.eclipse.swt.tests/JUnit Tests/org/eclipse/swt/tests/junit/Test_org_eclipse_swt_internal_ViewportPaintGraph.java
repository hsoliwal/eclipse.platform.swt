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


	@Test
	public void test_coordinatePlanesAndInverseEventMapping () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int rootSpace = graph.group (graph.root (), 0);
		int body = graph.group (rootSpace, 1);
		int frozen = graph.group (rootSpace, 2);
		int header = graph.group (rootSpace, 4);
		int editor = graph.group (body, 8);

		graph.setTranslation (body, -620, -1400);
		graph.setTranslation (frozen, 0, -1400);
		graph.setTranslation (header, -620, 0);
		graph.setTransform (editor, new Affine (0, -1, 1, 0, 80, 40));

		float [] point = new float [2];
		graph.mapToRoot (body, 700, 1500, point);
		assertArrayEquals (new float[] {80, 100}, point, 0.0001f);
		graph.mapToRoot (header, 700, 15, point);
		assertArrayEquals (new float[] {80, 15}, point, 0.0001f);
		graph.mapToRoot (frozen, 40, 1500, point);
		assertArrayEquals (new float[] {40, 100}, point, 0.0001f);

		float [] editorRoot = new float [2];
		graph.mapToRoot (editor, 5, 10, editorRoot);
		float [] local = new float [2];
		assertTrue (graph.mapFromRoot (editor, editorRoot [0], editorRoot [1], local));
		assertArrayEquals (new float[] {5, 10}, local, 0.0001f,
				"input/hit-test must invert the same affine chain used for paint");
		assertEquals (1, graph.layer (body));
		assertEquals (4, graph.layer (header));
	}

	@Test
	public void test_clipAndStrokeInheritance () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int rootSpace = graph.group (graph.root (), 0);
		int body = graph.group (rootSpace, 1);
		int child = graph.group (body, 2);

		graph.setClip (rootSpace, 0, 0, 1000, 700);
		graph.setTranslation (body, -100, -200);
		graph.setClip (body, 100, 200, 800, 600);
		graph.setTranslation (child, 50, 25);
		graph.setClip (child, 0, 0, 300, 300);

		float [] clip = new float [4];
		assertTrue (graph.rootClip (child, clip));
		assertArrayEquals (new float[] {0, 0, 250, 125}, clip, 0.0001f,
				"nested local clips must intersect in root coordinates");

		graph.setStroke (body, 3, SWT.LINE_DASH, SWT.CAP_ROUND, SWT.JOIN_BEVEL);
		int [] stroke = new int [4];
		assertTrue (graph.effectiveStroke (child, stroke));
		assertArrayEquals (
				new int[] {3, SWT.LINE_DASH, SWT.CAP_ROUND, SWT.JOIN_BEVEL}, stroke);

		graph.setStroke (child, 1, SWT.LINE_SOLID, SWT.CAP_FLAT, SWT.JOIN_MITER);
		assertTrue (graph.effectiveStroke (child, stroke));
		assertArrayEquals (
				new int[] {1, SWT.LINE_SOLID, SWT.CAP_FLAT, SWT.JOIN_MITER}, stroke);
		graph.clearStroke (child);
		assertTrue (graph.effectiveStroke (child, stroke));
		assertEquals (3, stroke [0]);

		graph.setTransform (child, new Affine (0, 0, 0, 0, 0, 0));
		assertFalse (graph.mapFromRoot (child, 10, 10, new float [2]));
		assertThrows (IllegalArgumentException.class, () -> graph.setClip (child, 0, 0, -1, 10));
	}

	@Test
	public void test_withStateAppliesAndRestoresRealGcState () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int body = graph.group (graph.root (), 1);
		graph.setTranslation (body, 5, 6);
		graph.setClip (body, 0, 0, 20, 20);
		graph.setStroke (body, 3, SWT.LINE_DASH, SWT.CAP_ROUND, SWT.JOIN_BEVEL);

		Transform caller = new Transform (display, 1, 0, 0, 1, 2, 3);
		Transform after = new Transform (display);
		Rectangle callerClip = new Rectangle (0, 0, 60, 60);
		gc.setTransform (caller);
		gc.setClipping (callerClip);
		gc.setLineWidth (1);
		gc.setLineStyle (SWT.LINE_SOLID);
		gc.setLineCap (SWT.CAP_FLAT);
		gc.setLineJoin (SWT.JOIN_MITER);
		try {
			graph.withState (gc, body, () -> {
				Transform during = new Transform (display);
				try {
					gc.getTransform (during);
					float [] actual = new float [6];
					during.getElements (actual);
					assertArrayEquals (
							new float[] {1, 0, 0, 1, 7, 9}, actual, 0.0001f);
					assertEquals (3, gc.getLineWidth ());
					assertEquals (SWT.LINE_DASH, gc.getLineStyle ());
					assertEquals (SWT.CAP_ROUND, gc.getLineCap ());
					assertEquals (SWT.JOIN_BEVEL, gc.getLineJoin ());
				} finally {
					during.dispose ();
				}
			});

			gc.getTransform (after);
			float [] expected = new float [6];
			float [] actual = new float [6];
			caller.getElements (expected);
			after.getElements (actual);
			assertArrayEquals (expected, actual, 0.0001f);
			assertEquals (callerClip, gc.getClipping ());
			assertEquals (1, gc.getLineWidth ());
			assertEquals (SWT.LINE_SOLID, gc.getLineStyle ());
			assertEquals (SWT.CAP_FLAT, gc.getLineCap ());
			assertEquals (SWT.JOIN_MITER, gc.getLineJoin ());
		} finally {
			after.dispose ();
			caller.dispose ();
		}
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
