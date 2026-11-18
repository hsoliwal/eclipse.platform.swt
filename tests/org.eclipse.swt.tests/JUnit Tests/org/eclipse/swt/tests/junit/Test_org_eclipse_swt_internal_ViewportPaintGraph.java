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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

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
        if (gc != null && !gc.isDisposed()) {
            gc.dispose();
        }
        if (image != null && !image.isDisposed()) {
            image.dispose();
        }
	}


	@Test
	public void test_thickLineOutsideClipStillPaintsItsVisibleStroke () {
		assertStrokeMatchesImmediate (false, 4, 8, 52, 8, 12, SWT.CAP_FLAT,
				new Rectangle (0, 12, 64, 16));
	}

	@Test
	public void test_squareCapOutsideClipStillPaintsItsVisibleCorner () {
		assertStrokeMatchesImmediate (false, 8, 8, 20, 20, 12, SWT.CAP_SQUARE,
				new Rectangle (22, 18, 20, 20));
	}

	@Test
	public void test_rectangleOutsideClipStillPaintsItsVisibleJoin () {
		assertStrokeMatchesImmediate (true, 8, 8, 32, 32, 12, SWT.CAP_FLAT,
				new Rectangle (43, 43, 12, 12));
	}

	@Test
	public void test_fractionalStrokeOutsideClipMatchesImmediateGc () {
		assertStrokeMatchesImmediate (false, 4, 9, 52, 9, 9.5f, SWT.CAP_ROUND,
				new Rectangle (0, 12, 64, 16));
	}

	@Test
	public void test_groupCullBoundsIncludeVisibleStrokeOutsideGeometryBounds () {
		Rectangle clip = new Rectangle (0, 33, 40, 10);
		Image reference = new Image (display, 64, 64);
		GC immediate = new GC (reference);
		try {
			immediate.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
			immediate.fillRectangle (reference.getBounds ());
			immediate.setForeground (display.getSystemColor (SWT.COLOR_BLACK));
			LineAttributes stroke = new LineAttributes (12, SWT.CAP_FLAT, SWT.JOIN_MITER);
			immediate.setLineAttributes (stroke);
			gc.setLineAttributes (stroke);
			immediate.setClipping (clip);
			gc.setClipping (clip);

			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int group = graph.group (graph.root ());
			graph.setCullBounds (group, 0, 0, 32, 32);
			graph.line (group, 4, 31, 28, 31);

			immediate.drawLine (4, 31, 28, 31);
			var stats = graph.replay (gc, Affine.IDENTITY, clip);

			assertImagesEqual (reference, image,
					"coarse group bounds must include stroke pixels crossing the logical group edge");
			assertEquals (1, stats.drawnCommands ());
		} finally {
			immediate.dispose ();
			reference.dispose ();
		}
	}

	private void assertStrokeMatchesImmediate (boolean rectangle, int x, int y,
			int x2OrWidth, int y2OrHeight, float width, int cap, Rectangle clip) {
		Image reference = new Image (display, 64, 64);
		GC immediate = new GC (reference);
		try {
			immediate.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
			immediate.fillRectangle (reference.getBounds ());
			immediate.setForeground (display.getSystemColor (SWT.COLOR_BLACK));
			LineAttributes stroke = new LineAttributes (width, cap, SWT.JOIN_MITER);
			immediate.setLineAttributes (stroke);
			gc.setLineAttributes (stroke);
			immediate.setClipping (clip);
			gc.setClipping (clip);

			ViewportPaintGraph graph = new ViewportPaintGraph ();
			if (rectangle) {
				immediate.drawRectangle (x, y, x2OrWidth, y2OrHeight);
				graph.drawRectangle (graph.root (), x, y, x2OrWidth, y2OrHeight);
			} else {
				immediate.drawLine (x, y, x2OrWidth, y2OrHeight);
				graph.line (graph.root (), x, y, x2OrWidth, y2OrHeight);
			}
			var stats = graph.replay (gc, Affine.IDENTITY, clip);
			assertImagesEqual (reference, image,
					"retained stroke must match immediate SWT GC under the same clip");
			assertEquals (1, stats.drawnCommands ());
		} finally {
			immediate.dispose ();
			reference.dispose ();
		}
	}

	private void assertImagesEqual (Image expectedImage, Image actualImage, String message) {
		ImageData expected = expectedImage.getImageData ();
		ImageData actual = actualImage.getImageData ();
		int painted = 0;
		RGB white = rgb (SWT.COLOR_WHITE);
		for (int py = 0; py < 64; py++) {
			for (int px = 0; px < 64; px++) {
				RGB expectedRgb = expected.palette.getRGB (expected.getPixel (px, py));
                if (!expectedRgb.equals(white)) {
                    painted++;
                }
				assertEquals (
						expectedRgb,
						actual.palette.getRGB (actual.getPixel (px, py)),
						message + " at " + px + "," + py);
			}
		}
		assertTrue (painted > 0, "the native oracle must paint inside the clip");
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
	public void test_groupCullBoundsSkipOffscreenRetainedSubtreesBeforeCommands () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int row = graph.template ();
		graph.setCullBounds (row, 0, 0, 96, 20);
		for (int i = 0; i < 64; i++) {
			graph.line (row, 0, i % 20, 95, i % 20);
		}
		for (int i = 0; i < 200; i++) {
			graph.instance (graph.root (), row, Affine.translation (0, i * 24));
		}

		var stats = graph.replay (
				gc, Affine.IDENTITY, new Rectangle (0, 0, 96, 20));

		assertEquals (64, stats.drawnCommands (),
				"only the first row instance intersects the replay clip");
		assertTrue (stats.visitedNodes () < 500,
				"coarse retained bounds must reject offscreen row subtrees before their child commands");
		assertEquals (64, graph.geometryNodeCount (),
				"culling must not duplicate or mutate retained geometry");
	}

	@Test
	public void test_groupCullBoundsAreConservativeUnderAffineTransforms () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int group = graph.group (
				graph.root (),
				new Affine (0, 1, -1, 0, 40, 10));
		graph.setCullBounds (group, 0, 0, 20, 10);
		graph.fillRectangle (group, 0, 0, 20, 10);

		var visible = graph.replay (
				gc, Affine.IDENTITY, new Rectangle (28, 8, 16, 24));
		assertEquals (1, visible.drawnCommands (),
				"conservative affine AABB must retain a rotated subtree that intersects the clip");

		var hidden = graph.replay (
				gc, Affine.IDENTITY, new Rectangle (0, 40, 12, 12));
		assertEquals (0, hidden.drawnCommands (),
				"affine subtree bounds outside the clip must be rejected as a unit");
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
	public void test_viewportPlanesMapPaintAndEventsThroughSameAffineChain () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int body = graph.group (graph.root (), Affine.translation (-620, -1400));
		int frozen = graph.group (graph.root (), Affine.translation (0, -1400));
		int header = graph.group (graph.root (), Affine.translation (-620, 0));
		int editor = graph.group (body, new Affine (0, 1, -1, 0, 80, 40));

		graph.setLayer (body, 10);
		graph.setLayer (frozen, 20);
		graph.setLayer (header, 30);
		graph.setLayer (editor, 40);

		float [] point = new float [2];
		graph.mapToRoot (body, 700, 1500, point);
		assertArrayEquals (new float[] {80, 100}, point, 0.0001f,
				"body follows horizontal and vertical logical origin");

		graph.mapToRoot (header, 700, 15, point);
		assertArrayEquals (new float[] {80, 15}, point, 0.0001f,
				"header follows horizontal projection but remains fixed vertically");

		graph.mapToRoot (frozen, 40, 1500, point);
		assertArrayEquals (new float[] {40, 100}, point, 0.0001f,
				"frozen plane follows vertical projection without horizontal translation");

		float [] rootPoint = new float [2];
		graph.mapToRoot (editor, 5, 10, rootPoint);
		float [] local = new float [2];
		assertTrue (graph.mapFromRoot (editor, rootPoint [0], rootPoint [1], local));
		assertArrayEquals (new float[] {5, 10}, local, 0.0001f,
				"event coordinates must invert the same chain used for paint");

		int [] layer = new int [1];
		assertTrue (graph.effectiveLayer (editor, layer));
		assertEquals (40, layer [0]);
		graph.clearLayer (editor);
		assertTrue (graph.effectiveLayer (editor, layer));
		assertEquals (10, layer [0],
				"clearing editor layer inherits the body z-plane");

		int detached = graph.template ();
		assertThrows (IllegalArgumentException.class,
				() -> graph.mapToRoot (detached, 0, 0, new float [2]),
				"detached reusable templates have no unique root coordinate");
	}

	@Test
	public void test_viewportClipAndStrokeStateInheritWithoutRetainedGc () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int viewport = graph.group (graph.root ());
		int body = graph.group (viewport, Affine.translation (-100, -200));
		int child = graph.group (body, Affine.translation (50, 25));

		graph.setClip (viewport, 0, 0, 1000, 700);
		graph.setClip (body, 100, 200, 800, 600);
		graph.setClip (child, 0, 0, 300, 300);

		float [] clip = new float [4];
		assertTrue (graph.rootClip (child, clip));
		assertArrayEquals (new float[] {0, 0, 250, 125}, clip, 0.0001f,
				"nested local clips become one conservative root-space clip");

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
	}

	@Test
	public void test_groupLocalTransformParticipatesInRetainedReplay () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int body = graph.group (graph.root (), Affine.translation (10, 10));
		graph.fillRectangle (body, 0, 0, 4, 4);

		var stats = graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (1, stats.drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (11, 11));
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (5, 5));
	}

	@Test
	public void test_affineInverseRejectsSingularTransform () {
		Affine singular = new Affine (0, 0, 0, 0, 0, 0);
		assertFalse (singular.inverseMap (10, 10, new float [2]));

		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int node = graph.group (graph.root (), singular);
		assertFalse (graph.mapFromRoot (node, 10, 10, new float [2]));
	}


	@Test
	public void test_retainedZOrderReordersSiblingsAndPreservesEqualOrder () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int fill = graph.group (graph.root ());
		graph.fillRectangle (fill, 8, 8, 24, 12);
		int stroke = graph.group (graph.root ());
		graph.line (stroke, 8, 10, 31, 10);

		gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
		gc.setForeground (display.getSystemColor (SWT.COLOR_BLUE));

		graph.setZOrder (fill, 10);
		graph.setZOrder (stroke, 20);
		assertEquals (10, graph.zOrder (fill));
		assertEquals (20, graph.zOrder (stroke));
		graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (rgb (SWT.COLOR_BLUE), pixelRgb (12, 10),
				"higher z retained stroke must paint above the fill");

		gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		gc.fillRectangle (image.getBounds ());
		gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
		graph.setZOrder (stroke, 0);
		graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (rgb (SWT.COLOR_RED), pixelRgb (12, 10),
				"lower z retained stroke must paint behind the fill");

		gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		gc.fillRectangle (image.getBounds ());
		gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
		graph.setZOrder (fill, 5);
		graph.setZOrder (stroke, 5);
		graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (rgb (SWT.COLOR_BLUE), pixelRgb (12, 10),
				"equal z must preserve creation order, matching historical replay order");

		assertThrows (IllegalArgumentException.class,
				() -> graph.setZOrder (graph.root (), 1),
				"the root has no sibling stacking context");
	}

	@Test
	public void test_orderedLayerReplayFiltersAndPreservesBackToFrontSequence () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int body = graph.group (graph.root ());
		graph.setLayer (body, 10);
		graph.fillRectangle (body, 2, 2, 4, 4);

		int header = graph.group (graph.root ());
		graph.setLayer (header, 30);
		graph.fillRectangle (header, 20, 2, 4, 4);

		var headerOnly = graph.replayLayer (
				gc, 30, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (1, headerOnly.drawnCommands ());
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (3, 3));
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (21, 3));

		gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		gc.fillRectangle (image.getBounds ());
		gc.setBackground (display.getSystemColor (SWT.COLOR_BLACK));

		var layered = graph.replayLayers (
				gc, new int[] {10, 30}, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (2, layered.drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (3, 3));
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (21, 3));
	}



	@Test
	public void test_layerVisibilitySuppressesReplayAndCanBeRestored () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int layer = graph.group (graph.root ());
		graph.fillRectangle (layer, 4, 4, 12, 12);
		assertTrue (graph.isVisible (layer), "legacy groups must be visible by default");
		graph.setVisible (layer, false);
		assertFalse (graph.isVisible (layer));
		assertEquals (0, graph.replay (gc).drawnCommands ());
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (5, 5));
		graph.setVisible (layer, true);
		assertEquals (1, graph.replay (gc).drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (5, 5));
	}

	@Test
	public void test_layerHitTestUsesZOrderAndInheritedVisibility () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int back = graph.group (graph.root ());
		int front = graph.group (graph.root ());
		graph.setHitBounds (back, 0, 0, 20, 20);
		graph.setHitBounds (front, 0, 0, 20, 20);
		graph.setZOrder (back, 10);
		graph.setZOrder (front, 20);
		assertEquals (front, graph.hitTest (5, 5));
		graph.setVisible (front, false);
		assertEquals (back, graph.hitTest (5, 5));
		graph.setVisible (front, true);
		graph.setZOrder (front, 0);
		assertEquals (back, graph.hitTest (5, 5));
		graph.clearHitBounds (back);
		assertEquals (front, graph.hitTest (5, 5));
		assertEquals (-1, graph.hitTest (20, 5), "hit regions are half-open");
		graph.setZOrder (front, 5);
		graph.setZOrder (back, 5);
		graph.setHitBounds (back, 0, 0, 20, 20);
		assertEquals (front, graph.hitTest (5, 5), "equal Z respects creation order");
	}

	@Test
	public void test_layerHitTestRespectsAffineAndGroupClips () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int outer = graph.group (graph.root (), Affine.translation (10, 5));
		int inner = graph.group (outer);
		graph.setHitBounds (outer, 0, 0, 20, 20);
		graph.setHitBounds (inner, 0, 0, 20, 20);
		graph.setClip (outer, 4, 4, 8, 8);
		assertEquals (inner, graph.hitTest (15, 10), "child takes priority over parent");
		assertEquals (-1, graph.hitTest (13, 10), "outside ancestor local clip");
		assertEquals (-1, graph.hitTest (22, 10), "right edge excluded");
		graph.setTransform (inner, new Affine (0, 0, 0, 0, 0, 0));
		assertEquals (outer, graph.hitTest (15, 10), "singular child has no pointer inverse");
		assertThrows (IllegalArgumentException.class, () -> graph.hitTest (Float.NaN, 0));
		assertThrows (IllegalArgumentException.class, () -> graph.hitTest (Float.POSITIVE_INFINITY, 0));
		assertThrows (IllegalArgumentException.class, () -> graph.setHitBounds (outer, 0, 0, -1, 10));
		assertThrows (IllegalArgumentException.class, () -> graph.setHitBounds (outer, 0, 0, Float.NaN, 10));
	}

	@Test
	public void test_layerInstancesAreIndependentlyVisibleAndPickable () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int template = graph.template ();
		graph.fillRectangle (template, 0, 0, 5, 5);
		int instance = graph.instance (graph.root (), template, Affine.translation (20, 15));
		graph.setHitBounds (instance, 0, 0, 5, 5);
		assertEquals (instance, graph.hitTest (22, 17));
		assertEquals (-1, graph.hitTest (2, 2), "detached template is not a global target");
		graph.setVisible (instance, false);
		assertEquals (-1, graph.hitTest (22, 17));
		assertEquals (0, graph.replay (gc).drawnCommands ());
		graph.setVisible (instance, true);
		assertEquals (1, graph.replay (gc).drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (22, 17));
		graph.setVisible (template, false);
		assertEquals (0, graph.replay (gc).drawnCommands ());
		assertEquals (-1, graph.hitTest (22, 17));
	}

	@Test
	public void test_layerHitTestRejectsPrimitiveTargetsAndHandlesLargeScenes () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int paint = graph.fillRectangle (graph.root (), 0, 0, 10, 10);
		assertThrows (IllegalArgumentException.class, () -> graph.setVisible (paint, false));
		assertThrows (IllegalArgumentException.class, () -> graph.setHitBounds (paint, 0, 0, 10, 10));
		assertThrows (IllegalArgumentException.class, () -> graph.clearHitBounds (paint));
		for (int i = 0; i < 1024; i++) {
			int group = graph.group (graph.root ());
			graph.setHitBounds (group, 0, 0, 10, 10);
		}
		int frontmost = graph.nodeCount () - 1;
		assertEquals (frontmost, graph.hitTest (3, 4));
		assertEquals (frontmost, graph.hitTest (3, 4), "repeated hit traversal reuses scratch");
		assertEquals (-1, graph.hitTest (10, 4));
	}

	@Test
	public void test_retainedPathCopiesPrimitiveGeometryAndReplaysAcrossInstances () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int template = graph.template ();
		PathData data = new PathData ();
		data.types = new byte[] {
				SWT.PATH_MOVE_TO, SWT.PATH_LINE_TO, SWT.PATH_LINE_TO, SWT.PATH_CLOSE};
		data.points = new float[] {0, 0, 10, 0, 10, 10};
		graph.fillPath (template, data);
		graph.instance (graph.root (), template, Affine.translation (10, 10));
		graph.instance (graph.root (), template, Affine.translation (30, 10));

		data.types [0] = SWT.PATH_CLOSE;
		Arrays.fill (data.points, 50);

		var first = graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (2, first.drawnCommands ());
		assertEquals (1, graph.geometryNodeCount ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (17, 13));
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (37, 13));
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (55, 55));

		var second = graph.replay (gc, Affine.IDENTITY, new Rectangle (0, 0, 64, 64));
		assertEquals (2, second.drawnCommands (),
				"replay-local native Path resources must be reconstructible after disposal");
	}

	@Test
	public void test_retainedPathRejectsMalformedOrNonFiniteGeometry () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();

		PathData mismatched = new PathData ();
		mismatched.types = new byte[] {SWT.PATH_MOVE_TO};
		mismatched.points = new float[] {1};
		assertThrows (IllegalArgumentException.class,
				() -> graph.drawPath (graph.root (), mismatched));

		PathData unknown = new PathData ();
		unknown.types = new byte[] {(byte)0x7f};
		unknown.points = new float[0];
		assertThrows (IllegalArgumentException.class,
				() -> graph.drawPath (graph.root (), unknown));

		PathData nonFinite = new PathData ();
		nonFinite.types = new byte[] {SWT.PATH_MOVE_TO};
		nonFinite.points = new float[] {Float.NaN, 0};
		assertThrows (IllegalArgumentException.class,
				() -> graph.fillPath (graph.root (), nonFinite));
	}

	@Test
	public void test_retainedDrawPathCullingIncludesStrokeEnvelope () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		PathData data = new PathData ();
		data.types = new byte[] {SWT.PATH_MOVE_TO, SWT.PATH_LINE_TO};
		data.points = new float[] {4, 8, 52, 8};
		graph.drawPath (graph.root (), data);

		gc.setLineWidth (12);
		Rectangle clip = new Rectangle (0, 12, 64, 16);
		gc.setClipping (clip);
		var stats = graph.replay (gc, Affine.IDENTITY, clip);
		assertEquals (1, stats.drawnCommands (),
				"path centreline outside the clip must survive when its stroke enters the viewport");
	}

	private RGB pixelRgb (int x, int y) {
		ImageData data = image.getImageData ();
		int pixel = data.getPixel (x, y);
		return data.palette.getRGB (pixel);
	}

	private RGB rgb (int swtColor) {
		return display.getSystemColor (swtColor).getRGB ();
	}
	@Test
	public void test_repeatedViewportTransformsReuseOneOwnedSlot () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int moving = graph.group (graph.root ());
		int stationary = graph.group (graph.root (), Affine.translation (40, 50));
		int baseline = graph.transformCount ();
		float [] point = new float [2];
		for (int frame = 0; frame < 10_000; frame++) {
			Affine transform = (frame & 1) == 0 ? Affine.translation (frame, -frame) : Affine.IDENTITY;
			graph.setTransform (moving, transform);
			graph.mapToRoot (moving, 2, 3, point);
			assertArrayEquals (new float [] {2 + transform.dx (), 3 + transform.dy ()}, point);
			assertTrue (graph.transformCount () <= baseline + 1,
					"scrolling must retain at most one transform slot per moving group");
		}
		graph.mapToRoot (stationary, 2, 3, point);
		assertArrayEquals (new float [] {42, 53}, point,
				"updating one group must not change another group's transform");
	}


	@Test
	public void test_imageLayersPaintAndPickInStableSiblingZOrder () {
		Image red = solidLayer (20, 20, SWT.COLOR_RED);
		Image blue = solidLayer (20, 20, SWT.COLOR_BLUE);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int bottom = graph.image (graph.root (), red, 4, 4);
			int top = graph.image (graph.root (), blue, 8, 8);
			assertEquals (255, graph.opacity (top));
			assertTrue (graph.isVisible (top));
			assertTrue (graph.isPickable (top));
			graph.setZOrder (bottom, Integer.MIN_VALUE);
			graph.setZOrder (top, Integer.MAX_VALUE);
			assertEquals (2, graph.replay (gc).drawnCommands ());
			assertEquals (rgb (SWT.COLOR_BLUE), pixelRgb (12, 12));
			assertEquals (top, graph.hitTestImage (12, 12).node ());
			assertEquals (-1, graph.hitTestImage (12, 12).instance ());
			assertEquals (4f, graph.hitTestImage (12, 12).x ());
			assertEquals (4f, graph.hitTestImage (12, 12).y ());

			graph.setZOrder (bottom, Integer.MAX_VALUE);
			graph.setZOrder (top, Integer.MIN_VALUE);
			clearLayerTarget ();
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_RED), pixelRgb (12, 12));
			assertEquals (bottom, graph.hitTestImage (12, 12).node ());

			graph.setZOrder (bottom, 7);
			graph.setZOrder (top, 7);
			clearLayerTarget ();
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_BLUE), pixelRgb (12, 12));
			assertEquals (top, graph.hitTestImage (12, 12).node (),
					"equal z restores creation order for both painting and picking");
		} finally {
			blue.dispose ();
			red.dispose ();
		}
	}

	@Test
	public void test_imageLayersRespectVisibilityAndInputTransparentAncestors () {
		Image red = solidLayer (20, 20, SWT.COLOR_RED);
		Image blue = solidLayer (20, 20, SWT.COLOR_BLUE);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int bottom = graph.image (graph.root (), red, 0, 0);
			int group = graph.group (graph.root ());
			int top = graph.image (group, blue, 0, 0);
			graph.setPickable (group, false);
			assertFalse (graph.isPickable (group));
			assertEquals (bottom, graph.hitTestImage (5, 5).node ());
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_BLUE), pixelRgb (5, 5),
					"an input-transparent layer remains visible");

			graph.setPickable (group, true);
			graph.setVisible (group, false);
			assertFalse (graph.isVisible (group));
			assertEquals (bottom, graph.hitTestImage (5, 5).node ());
			clearLayerTarget ();
			assertEquals (1, graph.replay (gc).drawnCommands ());
			assertEquals (rgb (SWT.COLOR_RED), pixelRgb (5, 5));

			graph.setVisible (group, true);
			graph.setOpacity (top, 0);
			assertEquals (bottom, graph.hitTestImage (5, 5).node ());
			assertEquals (1, graph.replay (gc).drawnCommands ());
			graph.setOpacity (top, 255);
			graph.setPickable (top, false);
			assertEquals (bottom, graph.hitTestImage (5, 5).node ());
			graph.setPickable (top, true);
			assertEquals (top, graph.hitTestImage (5, 5).node ());
		} finally {
			blue.dispose ();
			red.dispose ();
		}
	}

	@Test
	public void test_imageLayerOpacityCompositesFlattenedOverlapsOnce () {
		Image source = solidLayer (32, 16, SWT.COLOR_WHITE);
		GC sourceGc = new GC (source);
		try {
			sourceGc.setBackground (display.getSystemColor (SWT.COLOR_RED));
			sourceGc.fillRectangle (0, 0, 20, 16);
			sourceGc.fillRectangle (10, 0, 20, 16);
			sourceGc.dispose ();
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int layer = graph.image (graph.root (), source, 0, 0);
			graph.setOpacity (layer, 128);
			graph.replay (gc);
			RGB oneStroke = pixelRgb (5, 5);
			RGB overlappingStrokes = pixelRgb (15, 5);
			assertEquals (oneStroke, overlappingStrokes,
					"opacity belongs to the flattened image, not to each source stroke");
			assertColorNear (new RGB (255, 127, 127), oneStroke, 1);
			assertEquals (255, gc.getAlpha (), "replay restores the caller alpha");

			clearLayerTarget ();
			gc.setAlpha (128);
			graph.replay (gc);
			assertColorNear (new RGB (255, 191, 191), pixelRgb (15, 5), 1);
			assertEquals (128, gc.getAlpha (),
					"caller alpha and layer opacity multiply and caller state survives");
		} finally {
			if (!sourceGc.isDisposed ()) sourceGc.dispose ();
			source.dispose ();
		}
	}

	@Test
	public void test_imageReplacementAndMovementKeepBorrowedResourcesAndFreshBounds () {
		Image first = solidLayer (20, 20, SWT.COLOR_RED);
		Image replacement = solidLayer (6, 8, SWT.COLOR_BLUE);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int layer = graph.image (graph.root (), first, 2, 3);
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_RED), pixelRgb (3, 4));
			graph.setImage (layer, replacement);
			graph.setImageLocation (layer, 30, 20);
			clearLayerTarget ();
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (3, 4),
					"a fresh frame clears the former layer position");
			assertEquals (rgb (SWT.COLOR_BLUE), pixelRgb (31, 21));
			assertNull (graph.hitTestImage (3, 4));
			assertNull (graph.hitTestImage (36, 21), "right image edge is exclusive");
			assertNull (graph.hitTestImage (31, 28), "bottom image edge is exclusive");
			assertEquals (layer, graph.hitTestImage (35.5f, 27.5f).node ());
			assertFalse (first.isDisposed (), "replacing a borrowed image never disposes it");
			assertFalse (replacement.isDisposed (), "replay never disposes a borrowed image");
			graph.setVisible (layer, false);
			clearLayerTarget ();
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (31, 21));
		} finally {
			replacement.dispose ();
			first.dispose ();
		}
	}

	@Test
	public void test_imagePickingUsesNestedTransformAndClipCoordinates () {
		Image source = solidLayer (12, 10, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int viewport = graph.group (graph.root (), Affine.translation (8, 6));
			graph.setClip (viewport, 0, 0, 30, 30);
			int content = graph.group (viewport, Affine.scale (2, 2));
			graph.setClip (content, 2, 3, 6, 5);
			int layer = graph.image (content, source, 2, 3);
			var hit = graph.hitTestImage (18, 16);
			assertNotNull (hit);
			assertEquals (layer, hit.node ());
			assertEquals (3f, hit.x (), 0.0001f);
			assertEquals (2f, hit.y (), 0.0001f);
			assertNull (graph.hitTestImage (11, 16));
			assertNull (graph.hitTestImage (24, 16), "nested clip right edge is exclusive");
			assertNull (graph.hitTestImage (18, 22), "nested clip bottom edge is exclusive");
			graph.setTransform (content, new Affine (0, 0, 0, 0, 0, 0));
			assertNull (graph.hitTestImage (8, 6), "singular mappings cannot be picked");
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_imagePickingReportsTheAttachedSharedTemplateInstance () {
		Image source = solidLayer (12, 12, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int template = graph.template ();
			int layer = graph.image (template, source, 1, 2);
			int first = graph.instance (graph.root (), template, Affine.translation (5, 6));
			int nested = graph.template ();
			graph.instance (nested, template, Affine.translation (2, 3));
			int second = graph.instance (graph.root (), nested, Affine.translation (30, 6));
			var firstHit = graph.hitTestImage (8, 11);
			var secondHit = graph.hitTestImage (35, 14);
			assertEquals (layer, firstHit.node ());
			assertEquals (layer, secondHit.node ());
			assertEquals (first, firstHit.instance ());
			assertEquals (second, secondHit.instance (),
					"the outer attached instance distinguishes a shared detached template");
			assertEquals (2f, firstHit.x ());
			assertEquals (3f, firstHit.y ());
			assertEquals (2f, secondHit.x ());
			assertEquals (3f, secondHit.y ());
			graph.setVisible (first, false);
			assertNull (graph.hitTestImage (8, 11));
			assertEquals (second, graph.hitTestImage (35, 14).instance ());
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_imageLayerClipIntersectsCallerRegionAndRestoresItsHoles () {
		Image source = solidLayer (24, 24, SWT.COLOR_RED);
		Region caller = new Region (display);
		Region after = new Region (display);
		try {
			caller.add (0, 0, 64, 64);
			caller.subtract (12, 12, 2, 2);
			gc.setClipping (caller);
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int group = graph.group (graph.root (), Affine.translation (10, 10));
			graph.setClip (group, 0, 0, 8, 8);
			graph.image (group, source, 0, 0);
			graph.replay (gc);
			gc.getClipping (after);
			ImageData actualPixels = image.getImageData ();
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					boolean expected = x >= 10 && x < 18 && y >= 10 && y < 18
							&& caller.contains (x, y);
					assertEquals (rgb (expected ? SWT.COLOR_RED : SWT.COLOR_WHITE),
							actualPixels.palette.getRGB (actualPixels.getPixel (x, y)),
							"clipped layer pixel at " + x + "," + y);
					assertEquals (caller.contains (x, y), after.contains (x, y),
							"caller clip must retain holes at " + x + "," + y);
				}
			}
		} finally {
			after.dispose ();
			caller.dispose ();
			source.dispose ();
		}
	}

	@Test
	public void test_rotatedLayerClipConstrainsPixelsAndPicking () {
		Image source = solidLayer (24, 24, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int group = graph.group (graph.root (), new Affine (0, 1, -1, 0, 30, 10));
			graph.setClip (group, 2, 3, 8, 6);
			int layer = graph.image (group, source, 0, 0);
			graph.replay (gc);
			ImageData actualPixels = image.getImageData ();
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					boolean expected = x >= 21 && x < 27 && y >= 12 && y < 20;
					assertEquals (rgb (expected ? SWT.COLOR_RED : SWT.COLOR_WHITE),
							actualPixels.palette.getRGB (actualPixels.getPixel (x, y)),
							"rotated layer clip at " + x + "," + y);
				}
			}
			assertEquals (layer, graph.hitTestImage (24, 16).node ());
			assertNull (graph.hitTestImage (20, 16));
			assertNull (graph.hitTestImage (24, 21));
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_layerClipDoesNotEscapeIntoLaterSiblingsOrCallerTransform () {
		Image source = solidLayer (24, 24, SWT.COLOR_RED);
		Transform caller = new Transform (display, 1, 0, 0, 1, 3, 4);
		Transform after = new Transform (display);
		try {
			gc.setTransform (caller);
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int first = graph.group (graph.root (), Affine.translation (4, 5));
			graph.setClip (first, 0, 0, 6, 6);
			graph.image (first, source, 0, 0);
			graph.image (graph.root (), source, 30, 20);
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_RED), pixelRgb (8, 10));
			assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (14, 10));
			assertEquals (rgb (SWT.COLOR_RED), pixelRgb (35, 26),
					"the first sibling clip cannot suppress a later un-clipped image");
			gc.getTransform (after);
			float [] expected = new float [6];
			float [] actual = new float [6];
			caller.getElements (expected);
			after.getElements (actual);
			assertArrayEquals (expected, actual, 0.0001f);
		} finally {
			after.dispose ();
			caller.dispose ();
			source.dispose ();
		}
	}

	@Test
	public void test_layerReplayRestoresCallerStateWhenBorrowedImageWasDisposed () {
		Image source = solidLayer (12, 12, SWT.COLOR_RED);
		Transform caller = new Transform (display, 1, 0, 0, 1, 2, 3);
		Transform after = new Transform (display);
		Region clip = new Region (display);
		Region afterClip = new Region (display);
		try {
			clip.add (0, 0, 32, 32);
			clip.subtract (8, 8, 4, 4);
			gc.setTransform (caller);
			gc.setClipping (clip);
			gc.setAlpha (91);
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int group = graph.group (graph.root (), Affine.scale (2, 2));
			graph.setClip (group, 0, 0, 6, 6);
			int layer = graph.image (group, source, 0, 0);
			graph.setOpacity (layer, 100);
			source.dispose ();
			assertThrows (IllegalArgumentException.class, () -> graph.replay (gc));
			assertEquals (91, gc.getAlpha ());
			gc.getTransform (after);
			float [] expected = new float [6];
			float [] actual = new float [6];
			caller.getElements (expected);
			after.getElements (actual);
			assertArrayEquals (expected, actual, 0.0001f);
			gc.getClipping (afterClip);
			for (int y = 0; y < 40; y++) {
				for (int x = 0; x < 40; x++) {
					assertEquals (clip.contains (x, y), afterClip.contains (x, y));
				}
			}
		} finally {
			afterClip.dispose ();
			clip.dispose ();
			after.dispose ();
			caller.dispose ();
			if (!source.isDisposed ()) source.dispose ();
		}
	}

	@Test
	public void test_layerPickingIsBoundsBasedWithoutImagePixelReadback () {
		ImageData pixels = new ImageData (8, 8, 24, new PaletteData (0xff0000, 0xff00, 0xff));
		pixels.alphaData = new byte [64];
		Image transparent = new Image (display, pixels);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int layer = graph.image (graph.root (), transparent, 2, 3);
			assertEquals (layer, graph.hitTestImage (4, 5).node (),
					"per-pixel masks are a separate owner policy; this is bounds picking");
			graph.setPickable (layer, false);
			assertNull (graph.hitTestImage (4, 5));
		} finally {
			transparent.dispose ();
		}
	}

	@Test
	public void test_layerValidationRefusesInvalidStateBeforeMutation () {
		Image source = solidLayer (8, 8, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int layer = graph.image (graph.root (), source, 0, 0);
			graph.setOpacity (layer, 170);
			assertThrows (IllegalArgumentException.class, () -> graph.setOpacity (layer, -1));
			assertThrows (IllegalArgumentException.class, () -> graph.setOpacity (layer, 256));
			assertEquals (170, graph.opacity (layer));
			assertThrows (IllegalArgumentException.class, () -> graph.setOpacity (graph.root (), 128));
			assertThrows (IllegalArgumentException.class, () -> graph.setImageLocation (graph.root (), 1, 2));
			assertThrows (IllegalArgumentException.class, () -> graph.setVisible (-1, false));
			assertThrows (IllegalArgumentException.class, () -> graph.setPickable (-1, false));
			assertThrows (IllegalArgumentException.class, () -> graph.hitTestImage (Float.NaN, 0));
			assertThrows (IllegalArgumentException.class, () -> graph.hitTestImage (0, Float.POSITIVE_INFINITY));
			graph.setClip (graph.root (), 0, 0, 0, 8);
			assertNull (graph.hitTestImage (1, 1));
			assertEquals (0, graph.replay (gc).drawnCommands ());
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_shearedLayerClipUsesThePolygonRatherThanItsBoundingBox () {
		Image source = solidLayer (24, 24, SWT.COLOR_RED);
		Region expectedClip = new Region (display);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int group = graph.group (graph.root (), new Affine (1, 0, 1, 1, 8, 6));
			graph.setClip (group, 2, 3, 8, 6);
			int layer = graph.image (group, source, 0, 0);
			expectedClip.add (new int [] {13, 9, 21, 9, 27, 15, 19, 15});
			graph.replay (gc);
			ImageData actual = image.getImageData ();
			for (int py = 0; py < 64; py++) {
				for (int px = 0; px < 64; px++) {
					RGB expected = rgb (expectedClip.contains (px, py) ? SWT.COLOR_RED : SWT.COLOR_WHITE);
					assertEquals (expected, actual.palette.getRGB (actual.getPixel (px, py)),
							"sheared polygon clipping at " + px + "," + py);
				}
			}
			assertEquals (layer, graph.hitTestImage (20, 12).node ());
			assertNull (graph.hitTestImage (13, 14),
					"a point inside the polygon's AABB but outside the clip must miss");
		} finally {
			expectedClip.dispose ();
			source.dispose ();
		}
	}

	@Test
	public void test_singularImageTransformDoesNotPoisonTheFollowingPrimitive () {
		Image source = solidLayer (8, 8, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int collapsed = graph.group (graph.root (), new Affine (0, 0, 0, 0, 24, 24));
			graph.image (collapsed, source, 0, 0);
			graph.fillRectangle (graph.root (), 40, 40, 8, 8);
			var stats = assertDoesNotThrow (() -> graph.replay (gc));
			assertEquals (1, stats.drawnCommands (),
					"the singular image is skipped before touching the native GC transform");
			assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (24, 24));
			assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (42, 42),
					"a valid primitive after the singular image must still paint");
			gc.fillRectangle (52, 52, 4, 4);
			assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (53, 53),
					"the borrowed native GC must remain usable after replay");
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_imageLayersRestoreAdvancedModeAndExplicitClipPresence () {
		Image source = solidLayer (8, 8, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int group = graph.group (graph.root (), Affine.translation (4, 5));
			graph.setClip (group, 0, 0, 4, 4);
			int layer = graph.image (group, source, 0, 0);
			graph.setOpacity (layer, 96);
			for (boolean requestedAdvanced : new boolean [] {false, true}) {
				for (boolean explicitClip : new boolean [] {false, true}) {
					gc.setAdvanced (requestedAdvanced);
					gc.setClipping (explicitClip ? new Rectangle (0, 0, 60, 60) : null);
					boolean originalAdvanced = gc.getAdvanced ();
					boolean originalClipped = gc.isClipped ();
					Rectangle originalClip = gc.getClipping ();
					graph.replay (gc);
					assertEquals (originalAdvanced, gc.getAdvanced (),
							"restore the mode actually provided by this backend");
					assertEquals (originalClipped, gc.isClipped (),
							"an inherited temporary clip must not become an explicit caller clip");
					assertEquals (originalClip, gc.getClipping ());
				}
			}
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_repeatedImagePickingAcrossWideSiblingList () {
		Image source = solidLayer (8, 8, SWT.COLOR_RED);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int [] nodes = new int [96];
			for (int i = 0; i < nodes.length; i++) {
				nodes [i] = graph.image (graph.root (), source, i * 12, 0);
			}
			for (int sample = 0; sample < 4096; sample++) {
				int index = sample * 37 % nodes.length;
				var hit = graph.hitTestImage (index * 12 + 3, 4);
				assertNotNull (hit);
				assertEquals (nodes [index], hit.node ());
				assertEquals (3f, hit.x ());
				assertEquals (4f, hit.y ());
				assertNull (graph.hitTestImage (index * 12 + 9, 4),
						"an empty gap must not reuse a previous traversal result");
			}
			for (int i = 0; i < nodes.length; i += 2) graph.setVisible (nodes [i], false);
			for (int i = 0; i < nodes.length; i++) {
				var hit = graph.hitTestImage (i * 12 + 3, 4);
				if ((i & 1) == 0) assertNull (hit);
				else assertEquals (nodes [i], hit.node ());
			}
		} finally {
			source.dispose ();
		}
	}

	@Test
	public void test_wideSiblingPlanesKeepPaintAndPickOrderAfterZChanges () {
		Image red = solidLayer (8, 8, SWT.COLOR_RED);
		Image blue = solidLayer (8, 8, SWT.COLOR_BLUE);
		try {
			ViewportPaintGraph graph = new ViewportPaintGraph ();
			int [] planes = new int [256];
			int [] nodes = new int [planes.length];
			Integer [] order = new Integer [planes.length];
			for (int i = 0; i < planes.length; i++) {
				planes [i] = graph.group (graph.root ());
				nodes [i] = graph.image (planes [i], (i & 1) == 0 ? red : blue, 4, 4);
				graph.setZOrder (planes [i], i % 11 - 5);
				order [i] = i;
			}
			Arrays.sort (order, Comparator.<Integer>comparingInt (i -> i % 11 - 5)
					.thenComparingInt (i -> i));
			assertEquals (planes.length, graph.replay (gc).drawnCommands ());
			int front = order [order.length - 1];
			assertEquals (rgb ((front & 1) == 0 ? SWT.COLOR_RED : SWT.COLOR_BLUE), pixelRgb (6, 6));
			for (int i = order.length - 1; i >= 0; i--) {
				int expected = order [i];
				var hit = graph.hitTestImage (6, 6);
				assertNotNull (hit);
				assertEquals (nodes [expected], hit.node (),
						"wide traversal must resume the next sibling in descending z and creation order");
				assertEquals (-1, hit.instance ());
				assertEquals (2f, hit.x ());
				assertEquals (2f, hit.y ());
				graph.setVisible (planes [expected], false);
			}
			assertNull (graph.hitTestImage (6, 6));
			for (int plane : planes) graph.setVisible (plane, true);
			graph.setZOrder (planes [1], Integer.MAX_VALUE);
			graph.setZOrder (planes [0], Integer.MAX_VALUE);
			assertEquals (nodes [1], graph.hitTestImage (6, 6).node (),
					"equal z retains creation order even after reinsertion in the opposite order");
			graph.setZOrder (planes [1], Integer.MIN_VALUE);
			assertEquals (nodes [0], graph.hitTestImage (6, 6).node ());
			clearLayerTarget ();
			graph.replay (gc);
			assertEquals (rgb (SWT.COLOR_RED), pixelRgb (6, 6));
		} finally {
			blue.dispose ();
			red.dispose ();
		}
	}

	@Test
	public void test_manyPrimitivesInOneAffinePlaneApplyTheTransformOnce () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int plane = graph.group (graph.root (), Affine.scale (2, 2));
		for (int i = 0; i < 96; i++) {
			graph.fillRectangle (plane, i % 16 * 2, i / 16 * 2, 1, 1);
		}
		var stats = graph.replay (gc);
		assertEquals (96, stats.drawnCommands ());
		assertEquals (1, stats.transformSwitches (),
				"one inherited affine plane needs one native transform application, independent of its width");
		for (int i = 0; i < 96; i++) {
			assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (i % 16 * 4, i / 16 * 4));
			assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (i % 16 * 4 + 3, i / 16 * 4));
		}
		Transform observed = new Transform (display);
		try {
			gc.getTransform (observed);
			assertTrue (observed.isIdentity (), "the caller transform is restored after the cached plane");
		} finally {
			observed.dispose ();
		}
	}

	@Test
	public void test_cachedAffinePlaneSurvivesSiblingClipChanges () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		int plane = graph.group (graph.root (), Affine.scale (2, 2));
		int left = graph.group (plane);
		graph.setClip (left, 0, 0, 8, 8);
		graph.fillRectangle (left, 0, 0, 16, 16);
		int empty = graph.group (plane);
		graph.setClip (empty, 0, 0, 0, 16);
		graph.fillRectangle (empty, 0, 0, 32, 32);
		int right = graph.group (plane);
		graph.setClip (right, 12, 0, 4, 8);
		graph.fillRectangle (right, 0, 0, 16, 16);
		graph.fillRectangle (plane, 8, 10, 4, 4);
		assertEquals (3, graph.replay (gc).drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (12, 12));
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (20, 12),
				"neither clipped sibling may fill the gap between its device-space polygons");
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (28, 12),
				"installing the next clip must invalidate and reapply the inherited transform");
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (20, 24),
				"the later primitive inherits the parent clip, not the preceding sibling's clip");
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (28, 24));
	}

	@Test
	public void test_nestedPathInstanceAndPrimitiveResumeTheRightAffinePlane () {
		ViewportPaintGraph graph = new ViewportPaintGraph ();
		PathData first = new PathData ();
		first.types = new byte [] {SWT.PATH_MOVE_TO, SWT.PATH_LINE_TO, SWT.PATH_LINE_TO,
				SWT.PATH_LINE_TO, SWT.PATH_CLOSE};
		first.points = new float [] {2, 2, 6, 2, 6, 6, 2, 6};
		int inner = graph.template ();
		graph.fillPath (inner, first);
		int outer = graph.template ();
		graph.instance (outer, inner, Affine.IDENTITY);
		graph.fillRectangle (outer, 2, 10, 4, 4);
		Affine scale = Affine.scale (2, 2);
		graph.instance (graph.root (), outer, scale);
		graph.fillRectangle (graph.root (), 40, 24, 4, 4);
		int last = graph.group (graph.root (), scale);
		PathData second = new PathData ();
		second.types = first.types.clone ();
		second.points = new float [] {16, 2, 20, 2, 20, 6, 16, 6};
		graph.fillPath (last, second);

		assertEquals (4, graph.replay (gc).drawnCommands ());
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (8, 8));
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (8, 24),
				"returning from a nested instance must resume the adjacent primitive in its parent plane");
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (42, 26),
				"the ordinary root primitive must restore the identity transform after the path");
		assertEquals (rgb (SWT.COLOR_BLACK), pixelRgb (36, 8),
				"the same affine values must be applied again after the identity primitive");
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (18, 4),
				"the later path must not reuse the identity CTM or the first path's native geometry");
		assertEquals (rgb (SWT.COLOR_WHITE), pixelRgb (2, 2));
	}

	private Image solidLayer (int width, int height, int color) {
		Image layer = new Image (display, width, height);
		GC painter = null;
		boolean complete = false;
		try {
			painter = new GC (layer);
			painter.setBackground (display.getSystemColor (color));
			painter.fillRectangle (layer.getBounds ());
			complete = true;
			return layer;
		} finally {
			if (painter != null) painter.dispose ();
			if (!complete) layer.dispose ();
		}
	}

	private void clearLayerTarget () {
		int alpha = gc.getAlpha ();
		gc.setAlpha (255);
		gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		gc.fillRectangle (image.getBounds ());
		gc.setBackground (display.getSystemColor (SWT.COLOR_BLACK));
		gc.setAlpha (alpha);
	}

	private void assertColorNear (RGB expected, RGB actual, int tolerance) {
		assertTrue (Math.abs (expected.red - actual.red) <= tolerance
				&& Math.abs (expected.green - actual.green) <= tolerance
				&& Math.abs (expected.blue - actual.blue) <= tolerance,
				() -> "expected " + expected + " but was " + actual);
	}

}
