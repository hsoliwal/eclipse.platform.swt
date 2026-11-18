/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
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

import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.internal.ViewportRuntime.ImageLayers;
import org.junit.jupiter.api.*;

/** Java raster-plane tests; native Canvas presentation has separate coverage. */
@SuppressWarnings("restriction")
public class Test_ViewportImageLayers {
	private static final int RED = 0xffff0000;
	private static final int GREEN = 0xff00ff00;
	private static final int BLUE = 0xff0000ff;
	private static final int WHITE = 0xffffffff;

	@Test
	public void test_creationOrderIsStableAndSnapshotsAreOwnedByCaller () {
		ImageLayers graph = new ImageLayers ();
		int red = graph.addImageLayer (1, 1, new int [] {RED});
		int blue = graph.addImageLayer (1, 1, new int [] {BLUE});
		assertEquals (2, graph.imageLayerCount ());
		assertArrayEquals (new int [] {red, blue}, graph.imageLayers ());
		int [] returned = graph.imageLayers ();
		returned [0] = -77;
		assertArrayEquals (new int [] {red, blue}, graph.imageLayers ());
		assertEquals (BLUE, pixel (graph, 0, 0));
		assertEquals (blue, graph.imageLayerAt (0, 0));
		assertEquals (255, graph.imageLayerOpacity (red));
		assertTrue (graph.imageLayerVisible (red));
		assertTrue (graph.imageLayerInputEnabled (red));
		assertArrayEquals (identity (), transform (graph, red));
	}

	@Test
	public void test_zOrderExtremaAndRestoredTiesPreserveCreationOrder () {
		ImageLayers graph = new ImageLayers ();
		int first = graph.addImageLayer (1, 1, new int [] {RED});
		int second = graph.addImageLayer (1, 1, new int [] {GREEN});
		int third = graph.addImageLayer (1, 1, new int [] {BLUE});
		graph.setZOrder (first, Integer.MAX_VALUE);
		graph.setZOrder (third, Integer.MIN_VALUE);
		assertArrayEquals (new int [] {third, second, first}, graph.imageLayers ());
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (first, graph.imageLayerAt (0, 0));
		graph.setZOrder (first, 0);
		graph.setZOrder (third, 0);
		assertArrayEquals (new int [] {first, second, third}, graph.imageLayers ());
		assertEquals (BLUE, pixel (graph, 0, 0));
	}

	@Test
	public void test_sourceOverKnownOpaqueAndTransparentDestinations () {
		ImageLayers graph = new ImageLayers ();
		graph.addImageLayer (1, 1, new int [] {BLUE});
		int red = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerOpacity (red, 128);
		assertEquals (0xff80007f, pixel (graph, 0, 0));
		ImageLayers transparent = new ImageLayers ();
		int isolated = transparent.addImageLayer (1, 1, new int [] {RED});
		transparent.setImageLayerOpacity (isolated, 128);
		assertEquals (0x80ff0000, pixel (transparent, 0, 0));
	}

	@Test
	public void test_layerOpacityIsAppliedOnceToFlattenedOverlappingContent () {
		ImageLayers graph = new ImageLayers ();
		// A flattened image of overlapping opaque red shapes is red in every
		// covered pixel, including the overlap; layer opacity must remain uniform.
		int layer = graph.addImageLayer (3, 1, new int [] {RED, RED, RED});
		graph.setImageLayerOpacity (layer, 128);
		int [] result = new int [3];
		graph.compositeImageLayers (0, 0, 3, 1, result);
		assertArrayEquals (new int [] {0x80ff0000, 0x80ff0000, 0x80ff0000}, result);
	}

	@Test
	public void test_partialSourceAlphaMaskAndOpacityMultiplyOnce () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {0x80ffffff});
		graph.setImageLayerOpacity (layer, 128);
		graph.setImageLayerMask (layer, new byte [] {(byte)128});
		assertEquals (0x20ffffff, pixel (graph, 0, 0));
		assertEquals (layer, graph.imageLayerAt (0, 0));
		graph.setImageLayerOpacity (layer, 0);
		assertEquals (0, pixel (graph, 0, 0));
		assertEquals (-1, graph.imageLayerAt (0, 0));
	}

	@Test
	public void test_roundedZeroAlphaDoesNotInterceptInput () {
		ImageLayers graph = new ImageLayers ();
		int bottom = graph.addImageLayer (1, 1, new int [] {RED});
		int top = graph.addImageLayer (1, 1, new int [] {0x010000ff});
		graph.setImageLayerOpacity (top, 1);
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (bottom, graph.imageLayerAt (0, 0));
	}

	@Test
	public void test_colorMathAgainstIndependentDoublePrecisionOracle () {
		ImageLayers graph = new ImageLayers ();
		int back = graph.addImageLayer (1, 1, new int [1]);
		int front = graph.addImageLayer (1, 1, new int [1]);
		Random random = new Random (0x51a7e);
		for (int trial = 0; trial < 4096; trial++) {
			int destination = random.nextInt ();
			int source = random.nextInt ();
			graph.setImageLayerPixels (back, 1, 1, new int [] {destination});
			graph.setImageLayerPixels (front, 1, 1, new int [] {source});
			int expected = referenceOver (source, destination);
			int actual = pixel (graph, 0, 0);
			assertEquals (expected, actual, "source-over trial " + trial);
		}
	}

	@Test
	public void test_visibilityAndInputTransparencyHaveDifferentEffects () {
		ImageLayers graph = new ImageLayers ();
		int back = graph.addImageLayer (1, 1, new int [] {RED});
		int front = graph.addImageLayer (1, 1, new int [] {BLUE});
		graph.setImageLayerInputEnabled (front, false);
		assertFalse (graph.imageLayerInputEnabled (front));
		assertEquals (BLUE, pixel (graph, 0, 0));
		assertEquals (back, graph.imageLayerAt (0, 0));
		graph.setImageLayerVisible (front, false);
		assertFalse (graph.imageLayerVisible (front));
		assertEquals (RED, pixel (graph, 0, 0));
		graph.setImageLayerVisible (front, true);
		graph.setImageLayerInputEnabled (front, true);
		assertEquals (front, graph.imageLayerAt (0, 0));
	}

	@Test
	public void test_sourcePixelsAndMaskAreDefensiveCopies () {
		ImageLayers graph = new ImageLayers ();
		int [] pixels = {RED, BLUE};
		byte [] mask = {(byte)255, 0};
		int layer = graph.addImageLayer (2, 1, pixels);
		graph.setImageLayerMask (layer, mask);
		pixels [0] = GREEN;
		mask [0] = 0;
		mask [1] = (byte)255;
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (0, pixel (graph, 1, 0));
		assertEquals (-1, graph.imageLayerAt (1, 0));
		int [] replacement = {GREEN, WHITE};
		graph.setImageLayerPixels (layer, 2, 1, replacement);
		replacement [0] = BLUE;
		assertEquals (GREEN, pixel (graph, 0, 0));
		assertEquals (0, pixel (graph, 1, 0));
		graph.setImageLayerMask (layer, null);
		assertEquals (WHITE, pixel (graph, 1, 0));
	}

	@Test
	public void test_transformIngressAndEgressDoNotExposeRetainedState () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		float [] input = translation (3, 4);
		graph.setImageLayerTransform (layer, input);
		input [4] = 99;
		float [] output = new float [7];
		output [6] = 123;
		graph.getImageLayerTransform (layer, output);
		assertArrayEquals (new float [] {1, 0, 0, 1, 3, 4, 123}, output);
		output [4] = 88;
		assertEquals (RED, pixel (graph, 3, 4));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerTransform (layer, new float [7]));
		assertThrows (IllegalArgumentException.class, () -> graph.getImageLayerTransform (layer, null));
		assertThrows (IllegalArgumentException.class, () -> graph.getImageLayerTransform (layer, new float [5]));
		assertEquals (RED, pixel (graph, 3, 4));
	}

	@Test
	public void test_resizeClearsSizeDependentMaskAndRetainsLayerProperties () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerMask (layer, new byte [1]);
		graph.setImageLayerTransform (layer, translation (4, 5));
		graph.setZOrder (layer, 9);
		graph.setImageLayerOpacity (layer, 128);
		graph.setImageLayerPixels (layer, 2, 1, new int [] {GREEN, BLUE});
		assertEquals (0x8000ff00, pixel (graph, 4, 5));
		assertEquals (0x800000ff, pixel (graph, 5, 5));
		assertEquals (9, graph.zOrder (layer));
		assertArrayEquals (translation (4, 5), transform (graph, layer));
	}

	@Test
	public void test_translationAndScaleUsePixelCentersConsistently () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (2, 1, new int [] {RED, BLUE});
		graph.setImageLayerTransform (layer, affine (2, 0, 0, 2, -3, 4));
		assertEquals (RED, pixel (graph, -3, 4));
		assertEquals (RED, pixel (graph, -2, 5));
		assertEquals (BLUE, pixel (graph, -1, 4));
		assertEquals (BLUE, pixel (graph, 0, 5));
		assertEquals (0, pixel (graph, 1, 5));
		assertEquals (layer, graph.imageLayerAt (-3, 4));
		assertEquals (-1, graph.imageLayerAt (1, 5));
	}

	@Test
	public void test_mirrorAndQuadrantRotationPreserveHalfOpenCoverage () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (2, 1, new int [] {RED, BLUE});
		graph.setImageLayerTransform (layer, affine (-1, 0, 0, 1, 2, 0));
		assertEquals (BLUE, pixel (graph, 0, 0));
		assertEquals (RED, pixel (graph, 1, 0));
		assertEquals (0, pixel (graph, 2, 0));
		graph.setImageLayerTransform (layer, affine (0, 1, -1, 0, 1, 0));
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (BLUE, pixel (graph, 0, 1));
		assertEquals (0, pixel (graph, 1, 1));
		assertEquals (layer, graph.imageLayerAt (0, 1));
	}

	@Test
	public void test_fractionalMirrorBoundaryIsSampledByBothPaintAndPick () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerTransform (layer, affine (-1, 0, 0, 1, 1.5f, 0));
		assertEquals (0, pixel (graph, 0, 0));
		assertEquals (RED, pixel (graph, 1, 0));
		assertEquals (-1, graph.imageLayerAt (0, 0));
		assertEquals (layer, graph.imageLayerAt (1, 0));
	}

	@Test
	public void test_localClipTracksAffineTransformAndCanBeRemoved () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (2, 1, new int [] {RED, BLUE});
		graph.setImageLayerClip (layer, 1, 0, 1, 1);
		graph.setImageLayerTransform (layer, translation (-2, 3));
		assertEquals (0, pixel (graph, -2, 3));
		assertEquals (BLUE, pixel (graph, -1, 3));
		assertEquals (-1, graph.imageLayerAt (-2, 3));
		assertEquals (layer, graph.imageLayerAt (-1, 3));
		graph.clearImageLayerClip (layer);
		assertEquals (RED, pixel (graph, -2, 3));
		graph.setImageLayerClip (layer, 0, 0, 0, 1);
		assertEquals (0, pixel (graph, -2, 3));
		assertEquals (-1, graph.imageLayerAt (-2, 3));
	}

	@Test
	public void test_clipEndpointsUseWideIntegerArithmetic () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerClip (layer, -2147483646, 0, Integer.MAX_VALUE, 1);
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (layer, graph.imageLayerAt (0, 0));
		graph.setImageLayerClip (layer, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 1);
		assertEquals (0, pixel (graph, 0, 0));
	}

	@Test
	public void test_transformDeterminantDoesNotOverflowFloatArithmetic () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerTransform (layer, scale (Float.MAX_VALUE, Float.MAX_VALUE));
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (layer, graph.imageLayerAt (0, 0));
	}

	@Test
	public void test_tinyInvertibleTransformsAreAcceptedWithoutStaleOutput () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerTransform (layer, scale (Float.MIN_VALUE, Float.MIN_VALUE));
		assertEquals (0, pixel (graph, 0, 0));
		assertEquals (-1, graph.imageLayerAt (0, 0));
	}

	@Test
	public void test_tileEndpointsBeyondIntegerMaximumDoNotWrap () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (2, 1, new int [] {RED, BLUE});
		graph.setImageLayerTransform (layer, translation (2147483648f, 0));
		int [] out = new int [3];
		graph.compositeImageLayers (Integer.MAX_VALUE, 0, 3, 1, out);
		assertArrayEquals (new int [] {0, RED, BLUE}, out);
		assertEquals (-1, graph.imageLayerAt (Integer.MAX_VALUE, 0));
	}

	@Test
	public void test_removeRejectsStaleHandlesAndNeverReusesThem () {
		ImageLayers graph = new ImageLayers ();
		int first = graph.addImageLayer (1, 1, new int [] {RED});
		graph.removeImageLayer (first);
		int second = graph.addImageLayer (1, 1, new int [] {BLUE});
		assertTrue (second > first);
		assertEquals (1, graph.imageLayerCount ());
		assertEquals (BLUE, pixel (graph, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> graph.removeImageLayer (first));
		assertThrows (IllegalArgumentException.class, () -> graph.setZOrder (first, 1));
		assertThrows (IllegalArgumentException.class, () -> graph.zOrder (first));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerOpacity (first, 0));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerTransform (first, identity ()));
	}

	@Test
	public void test_clearFreesAllPixelAndMaskPayloadsAndRetainsInvalidIdentities () throws Exception {
		ImageLayers graph = new ImageLayers ();
		int last = 0;
		for (int n = 0; n < 1000; n++) {
			last = graph.addImageLayer (2, 1, new int [] {RED, BLUE});
			graph.setImageLayerMask (last, new byte [] {127, (byte)255});
		}
		assertEquals (1000, graph.imageLayerCount ());
		graph.clearImageLayers ();
		graph.clearImageLayers ();
		assertEquals (0, graph.imageLayerCount ());
		assertArrayEquals (new int [0], graph.imageLayers ());
		assertEquals (0, pixel (graph, 0, 0));
		assertEquals (-1, graph.imageLayerAt (0, 0));
		for (String fieldName : new String [] {"pixels", "imageMasks"}) {
			Field field = ImageLayers.class.getDeclaredField (fieldName);
			field.setAccessible (true);
			for (Object payload : (Object [])field.get (graph)) assertNull (payload);
		}
		assertTrue (graph.addImageLayer (1, 1, new int [] {GREEN}) > last);
	}

	@Test
	public void test_repeatedTransformChangesReuseOnePrimitiveSlot () throws Exception {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		Field field = ImageLayers.class.getDeclaredField ("transforms");
		field.setAccessible (true);
		Object before = field.get (graph);
		for (int n = 0; n < 1000; n++) {
			graph.setImageLayerTransform (layer, translation (n, -n));
			graph.setImageLayerTransform (layer, identity ());
		}
		assertSame (before, field.get (graph));
		assertEquals (RED, pixel (graph, 0, 0));
	}

	@Test
	public void test_changedPixelsAndRemovalCannotLeaveStaleTileEntries () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		int [] out = {99, 99, 99};
		graph.compositeImageLayers (0, 0, 2, 1, out);
		assertArrayEquals (new int [] {RED, 0, 99}, out);
		graph.setImageLayerTransform (layer, translation (1, 0));
		graph.compositeImageLayers (0, 0, 2, 1, out);
		assertArrayEquals (new int [] {0, RED, 99}, out);
		graph.removeImageLayer (layer);
		graph.compositeImageLayers (0, 0, 2, 1, out);
		assertArrayEquals (new int [] {0, 0, 99}, out);
	}

	@Test
	public void test_tiledAndWholeRasterAgreeAcrossNegativeCoordinatesAndClips () {
		ImageLayers graph = new ImageLayers ();
		int a = graph.addImageLayer (3, 2, new int [] {RED, GREEN, BLUE, WHITE, 0x80000000, 0});
		int b = graph.addImageLayer (2, 2, new int [] {BLUE, 0x80ff0000, WHITE, GREEN});
		graph.setImageLayerTransform (a, affine (2, 0.5f, -0.25f, 2, -3, -2));
		graph.setImageLayerTransform (b, affine (1, -0.5f, 0.5f, 1, -1, 0));
		graph.setImageLayerClip (a, 1, 0, 2, 2);
		graph.setImageLayerOpacity (b, 137);
		int [] full = new int [13 * 11];
		graph.compositeImageLayers (-7, -5, 13, 11, full);
		int [] assembled = new int [full.length];
		int [] tile = new int [4 * 3];
		for (int row = 0; row < 11; row += 3) {
			for (int column = 0; column < 13; column += 4) {
				int width = Math.min (4, 13 - column), height = Math.min (3, 11 - row);
				graph.compositeImageLayers (-7 + column, -5 + row, width, height, tile);
				for (int y = 0; y < height; y++) {
					System.arraycopy (tile, y * width, assembled, (row + y) * 13 + column, width);
				}
			}
		}
		assertArrayEquals (full, assembled);
		for (int row = 0; row < 11; row++) {
			for (int column = 0; column < 13; column++) {
				assertEquals ((full [row * 13 + column] >>> 24) != 0,
						graph.imageLayerAt (-7 + column, -5 + row) >= 0);
			}
		}
	}

	@Test
	public void test_rejectedDimensionsAndPayloadsDoNotCreateOrModifyLayers () {
		ImageLayers graph = new ImageLayers ();
		for (int [] dimensions : new int [][] {{0, 1}, {1, 0}, {-1, 1}, {Integer.MAX_VALUE, 2}}) {
			assertThrows (IllegalArgumentException.class,
					() -> graph.addImageLayer (dimensions [0], dimensions [1], new int [1]));
		}
		assertThrows (IllegalArgumentException.class, () -> graph.addImageLayer (1, 1, null));
		assertThrows (IllegalArgumentException.class, () -> graph.addImageLayer (1, 1, new int [2]));
		assertEquals (0, graph.imageLayerCount ());
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerPixels (layer, 2, 1, new int [1]));
		assertEquals (RED, pixel (graph, 0, 0));
	}

	@Test
	public void test_rejectedPropertiesPreservePreviousState () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerOpacity (layer, -1));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerOpacity (layer, 256));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerMask (layer, new byte [2]));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerClip (layer, 0, 0, -1, 2));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerTransform (layer, null));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerTransform (layer, scale (0, 1)));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerTransform (layer, affine (Float.NaN, 0, 0, 1, 0, 0)));
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerTransform (layer, affine (1, 0, 0, 1, Float.POSITIVE_INFINITY, 0)));
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (255, graph.imageLayerOpacity (layer));
		assertArrayEquals (identity (), transform (graph, layer));
	}

	@Test
	public void test_invalidCoordinatesTilesAndWrongNodeTypesAreRejected () {
		ImageLayers graph = new ImageLayers ();
		assertThrows (IllegalArgumentException.class, () -> graph.setImageLayerVisible (-1, false));
		assertThrows (IllegalArgumentException.class, () -> graph.removeImageLayer (0));
		assertThrows (IllegalArgumentException.class, () -> graph.imageLayerAt (Float.NaN, 0));
		assertThrows (IllegalArgumentException.class, () -> graph.imageLayerAt (0, Float.NEGATIVE_INFINITY));
		assertThrows (IllegalArgumentException.class, () -> graph.compositeImageLayers (0, 0, -1, 1, new int [1]));
		assertThrows (IllegalArgumentException.class, () -> graph.compositeImageLayers (0, 0, 1, 1, null));
		assertThrows (IllegalArgumentException.class, () -> graph.compositeImageLayers (0, 0, 2, 1, new int [1]));
		assertThrows (IllegalArgumentException.class,
				() -> graph.compositeImageLayers (0, 0, Integer.MAX_VALUE, 2, new int [1]));
		int [] untouched = {123};
		graph.compositeImageLayers (0, 0, 0, 1, untouched);
		assertArrayEquals (new int [] {123}, untouched);
	}

	@Test
	public void test_clippedBoundsFollowGeometryMutationsAndRemainCallerOwned () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (3, 2, new int [6]);
		assertArrayEquals (new double [] {0, 0, 3, 2}, bounds (graph, layer));
		graph.setImageLayerTransform (layer, affine (0, 2, -3, 0, 10, -5));
		assertArrayEquals (new double [] {4, -5, 10, 1}, bounds (graph, layer));
		graph.setImageLayerClip (layer, 1, 0, 1, 2);
		assertArrayEquals (new double [] {4, -3, 10, -1}, bounds (graph, layer));
		graph.setImageLayerVisible (layer, false);
		graph.setImageLayerOpacity (layer, 0);
		graph.setImageLayerInputEnabled (layer, false);
		double [] out = {0, 0, 0, 0, 71};
		graph.getImageLayerBounds (layer, out);
		assertArrayEquals (new double [] {4, -3, 10, -1, 71}, out);
		out [0] = 999;
		assertArrayEquals (new double [] {4, -3, 10, -1}, bounds (graph, layer));
		graph.setImageLayerPixels (layer, 1, 1, new int [] {RED});
		assertArrayEquals (new double [4], bounds (graph, layer));
		graph.clearImageLayerClip (layer);
		assertArrayEquals (new double [] {7, -5, 10, -3}, bounds (graph, layer));
		graph.setImageLayerTransform (layer, affine (-2, 0, 0, -3, 4, 5));
		assertArrayEquals (new double [] {2, 2, 4, 5}, bounds (graph, layer));
		assertThrows (IllegalArgumentException.class, () -> graph.getImageLayerBounds (layer, new double [3]));
		assertThrows (IllegalArgumentException.class, () -> graph.getImageLayerBounds (layer, null));
		graph.removeImageLayer (layer);
		assertThrows (IllegalArgumentException.class, () -> graph.getImageLayerBounds (layer, out));
	}

	@Test
	public void test_integralTranslationRowsRespectClipsTileStrideAndOutputSuffix () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (3, 2, new int [] {RED, GREEN, BLUE, WHITE, RED, GREEN});
		graph.setImageLayerTransform (layer, translation (-1, 0));
		graph.setImageLayerClip (layer, 1, 0, 2, 2);
		int [] out = new int [22];
		Arrays.fill (out, 97);
		graph.compositeImageLayers (-2, -1, 5, 4, out);
		assertArrayEquals (new int [] {
				0, 0, 0, 0, 0,
				0, 0, GREEN, BLUE, 0,
				0, 0, RED, GREEN, 0,
				0, 0, 0, 0, 0, 97, 97}, out);
		assertEquals (layer, graph.imageLayerAt (0, 0));
		assertEquals (-1, graph.imageLayerAt (-1, 0));
	}

	@Test
	public void test_opaqueCoverageRequiresEveryTileSampleAndTracksAlphaMutations () {
		ImageLayers graph = new ImageLayers ();
		graph.addImageLayer (3, 2, filledPixels (6, BLUE));
		int top = graph.addImageLayer (3, 2, filledPixels (6, GREEN));
		int [] out = new int [6];
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (filledPixels (6, GREEN), out);
		byte [] mask = new byte [6];
		Arrays.fill (mask, (byte)255);
		graph.setImageLayerMask (top, mask);
		mask [5] = 0;
		graph.setImageLayerMask (top, mask);
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (new int [] {GREEN, GREEN, GREEN, GREEN, GREEN, BLUE}, out);
		graph.setImageLayerMask (top, null);
		graph.setImageLayerOpacity (top, 128);
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (filledPixels (6, 0xff00807f), out);
		graph.setImageLayerOpacity (top, 255);
		int [] pixels = filledPixels (6, GREEN);
		pixels [5] = 0;
		graph.setImageLayerPixels (top, 3, 2, pixels);
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (new int [] {GREEN, GREEN, GREEN, GREEN, GREEN, BLUE}, out);
		graph.setImageLayerPixels (top, 3, 2, filledPixels (6, GREEN));
		graph.setImageLayerClip (top, 1, 0, 2, 2);
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (new int [] {BLUE, GREEN, GREEN, BLUE, GREEN, GREEN}, out);
		graph.clearImageLayerClip (top);
		graph.setImageLayerInputEnabled (top, false);
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (filledPixels (6, GREEN), out);
		graph.setImageLayerVisible (top, false);
		graph.compositeImageLayers (0, 0, 3, 2, out);
		assertArrayEquals (filledPixels (6, BLUE), out);
	}

	@Test
	public void test_rotatedOpaqueBoundsDoNotEraseUncoveredCorners () {
		ImageLayers graph = new ImageLayers ();
		graph.addImageLayer (4, 4, filledPixels (16, BLUE));
		int top = graph.addImageLayer (2, 2, filledPixels (4, GREEN));
		graph.setImageLayerTransform (top, affine (1, 1, -1, 1, 2, 0));
		assertArrayEquals (new double [] {0, 0, 4, 4}, bounds (graph, top));
		assertEquals (BLUE, pixel (graph, 0, 0));
		assertEquals (GREEN, pixel (graph, 2, 1));
		assertEquals (BLUE, pixel (graph, 3, 3));
	}

	@Test
	public void test_opaqueCutoffKeepsUpperRoundedCompositionOrder () {
		ImageLayers graph = new ImageLayers ();
		graph.addImageLayer (1, 1, new int [] {0x33445566});
		graph.addImageLayer (1, 1, new int [] {BLUE});
		graph.addImageLayer (1, 1, new int [] {0x79fd0713});
		graph.addImageLayer (1, 1, new int [] {0x3137c90b});
		graph.addImageLayer (1, 1, new int [] {0x017b4521});
		int expected = referenceOver (0x017b4521, referenceOver (0x3137c90b,
				referenceOver (0x79fd0713, BLUE)));
		assertEquals (expected, pixel (graph, 0, 0));
	}

	@Test
	public void test_nonemptyTinyTransformedExtentRetainsPixelCenterAndDamageBounds () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		graph.setImageLayerTransform (layer, affine (Float.MIN_VALUE, 0, 0, Float.MIN_VALUE, 0.5f, 0.5f));
		assertEquals (RED, pixel (graph, 0, 0));
		assertEquals (layer, graph.imageLayerAt (0, 0));
		double [] bounds = bounds (graph, layer);
		assertEquals (0.5, bounds [0]);
		assertEquals (0.5, bounds [1]);
		assertTrue (bounds [2] > bounds [0]);
		assertTrue (bounds [3] > bounds [1]);
		graph.setImageLayerClip (layer, 0, 0, 0, 1);
		assertArrayEquals (new double [4], bounds (graph, layer));
		assertEquals (0, pixel (graph, 0, 0));
	}

	@Test
	public void test_clipAndMaskPresenceQueriesTrackRemovalResizeAndInvalidIdentities () {
		ImageLayers graph = new ImageLayers ();
		int layer = graph.addImageLayer (1, 1, new int [] {RED});
		assertFalse (graph.imageLayerHasClip (layer));
		assertFalse (graph.imageLayerHasMask (layer));
		assertFalse (graph.imageLayerClipEquals (layer, 0, 0, 1, 1));
		graph.setImageLayerClip (layer, 0, 0, 1, 1);
		assertTrue (graph.imageLayerHasClip (layer));
		assertTrue (graph.imageLayerClipEquals (layer, 0, 0, 1, 1));
		assertFalse (graph.imageLayerClipEquals (layer, 1, 0, 1, 1));
		graph.setImageLayerMask (layer, new byte [] {(byte)255});
		assertTrue (graph.imageLayerHasMask (layer));
		graph.setImageLayerPixels (layer, 2, 1, new int [] {RED, BLUE});
		assertFalse (graph.imageLayerHasMask (layer));
		assertTrue (graph.imageLayerHasClip (layer));
		graph.clearImageLayerClip (layer);
		assertFalse (graph.imageLayerHasClip (layer));
		graph.removeImageLayer (layer);
		assertThrows (IllegalArgumentException.class, () -> graph.imageLayerHasClip (layer));
		assertThrows (IllegalArgumentException.class, () -> graph.imageLayerHasMask (layer));
		assertThrows (IllegalArgumentException.class, () -> graph.imageLayerClipEquals (layer, 0, 0, 1, 1));
	}

	private static int [] filledPixels (int size, int color) {
		int [] result = new int [size];
		Arrays.fill (result, color);
		return result;
	}

	private static double [] bounds (ImageLayers graph, int layer) {
		double [] result = new double [4];
		graph.getImageLayerBounds (layer, result);
		return result;
	}

	private static float [] identity () {
		return affine (1, 0, 0, 1, 0, 0);
	}

	private static float [] affine (float a, float b, float c, float d, float x, float y) {
		return new float [] {a, b, c, d, x, y};
	}

	private static float [] translation (float x, float y) {
		return affine (1, 0, 0, 1, x, y);
	}

	private static float [] scale (float x, float y) {
		return affine (x, 0, 0, y, 0, 0);
	}

	private static float [] transform (ImageLayers graph, int layer) {
		float [] result = new float [6];
		graph.getImageLayerTransform (layer, result);
		return result;
	}

	private static int pixel (ImageLayers graph, int x, int y) {
		int [] result = new int [1];
		graph.compositeImageLayers (x, y, 1, 1, result);
		return result [0];
	}

	/* Independent real-valued source-over definition, rounded only at the boundary. */
	private static int referenceOver (int source, int destination) {
		double a = (source >>> 24) / 255d;
		double b = (destination >>> 24) / 255d;
		double outAlpha = a + b * (1 - a);
		if (outAlpha == 0) return 0;
		int result = (int)Math.round (outAlpha * 255) << 24;
		for (int shift : new int [] {16, 8, 0}) {
			double channel = (((source >>> shift) & 255) * a
					+ ((destination >>> shift) & 255) * b * (1 - a)) / outAlpha;
			result |= (int)Math.round (channel) << shift;
		}
		return result;
	}
}
