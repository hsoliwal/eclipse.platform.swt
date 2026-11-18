/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - clip and transform lifetime regressions
 *******************************************************************************/
package org.eclipse.swt.tests.junit.maya;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.canvasext.raster.RasterExternalGC;
import org.eclipse.swt.internal.maya.ClipMode;
import org.eclipse.swt.internal.maya.Matrix33;
import org.eclipse.swt.internal.maya.types.IRect;
import org.eclipse.swt.internal.maya.types.Rect;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Stateful transport-contract tests for the existing Maya GC. The recording
 * canvas models the save stack, matrix and clip bounds; it does not rasterize.
 * SWT Region and Transform objects, the GC implementation and Maya resource
 * construction remain real, so execution requires their native libraries and
 * an SWT display. These tests do not replace native pixel qualification.
 */
public class Test_org_eclipse_swt_maya_ClipTransformState {
	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	/** Extends the existing test transport without changing other tests' call logs. */
	private static final class StateCanvas extends MayaCanvasDouble {
		private record Saved(Matrix33 matrix, Rectangle clip) {
		}

		private final List<Saved> stack = new ArrayList<>();
		Matrix33 matrix = Matrix33.IDENTITY;
		Rectangle clip = new Rectangle(0, 0, 100, 100);
		int peakSaveCount = 1;

		StateCanvas(List<MethodCall> calls) {
			super(100, 100, "state-canvas", calls);
			saveCount = 1;
		}

		@Override
		public void save() {
			stack.add(new Saved(matrix, copy(clip)));
			super.save();
			peakSaveCount = Math.max(peakSaveCount, saveCount);
		}

		@Override
		public void restore() {
			if (saveCount > 1) {
				Saved saved = stack.remove(stack.size() - 1);
				matrix = saved.matrix();
				clip = saved.clip();
				saveCount--;
			}
			super.restore();
		}

		@Override
		public void restoreToCount(int count) {
			assertTrue(count >= 1, "the GC cannot remove the canvas' initial frame");
			while (saveCount > count) restore();
			super.restoreToCount(count);
		}

		@Override
		public void setMatrix(Matrix33 replacement) {
			matrix = new Matrix33(replacement.getMat().clone());
			super.setMatrix(replacement);
		}

		@Override
		public void clipRect(Rect rect) {
			clip = clip.intersection(mappedBounds(rect));
			super.clipRect(rect);
		}

		@Override
		public void clipPath(org.eclipse.swt.internal.maya.Path path, ClipMode mode, boolean antialias) {
			clip = clip.intersection(mappedBounds(path.getBounds()));
			super.clipPath(path, mode, antialias);
		}

		@Override
		public void clipRegion(org.eclipse.swt.internal.maya.Region region) {
			// Skia region clipping is in device coordinates and ignores the CTM.
			IRect bounds = region.getBounds();
			clip = clip.intersection(new Rectangle(bounds.getLeft(), bounds.getTop(),
					bounds.getWidth(), bounds.getHeight()));
			super.clipRegion(region);
		}

		private Rectangle mappedBounds(Rect rect) {
			float[] m = matrix.getMat();
			// All transform-state assertions use integral translations. This
			// transport intentionally makes no polygon/rasterization claim.
			assertEquals(1f, m[0]);
			assertEquals(0f, m[1]);
			assertEquals(0f, m[3]);
			assertEquals(1f, m[4]);
			return new Rectangle(Math.round(rect.getLeft() + m[2]), Math.round(rect.getTop() + m[5]),
					Math.round(rect.getWidth()), Math.round(rect.getHeight()));
		}
	}

	private static final class Fixture implements AutoCloseable {
		final Display display = Display.getDefault();
		final MayaSurfaceDouble surface = new MayaSurfaceDouble(null);
		final StateCanvas canvas = new StateCanvas(surface.calls);
		final CanvasExtensionDouble extension = new CanvasExtensionDouble();
		final RasterExternalGC gc;
		final int hostSaveCount;
		final Matrix33 hostMatrix;
		final Rectangle hostClip;

		Fixture() {
			this(100);
		}

		Fixture(int zoom) {
			surface.canvas = canvas;
			canvas.save();
			canvas.setMatrix(Matrix33.makeTranslate(3, 4));
			canvas.save();
			hostSaveCount = canvas.getSaveCount();
			hostMatrix = canvas.matrix;
			hostClip = copy(canvas.clip);
			extension.surface = surface;
			extension.resources = new MayaResourcesDouble(zoom);
			extension.clientArea = new Rectangle(0, 0, 100, 100);
			extension.bounds = copy(extension.clientArea);
			extension.device = display;
			gc = new RasterExternalGC(extension, SWT.NONE);
		}

		@Override
		public void close() {
			gc.dispose();
		}

		void assertHostRestored() {
			assertEquals(hostSaveCount, canvas.getSaveCount());
			assertArrayEquals(hostMatrix.getMat(), canvas.matrix.getMat());
			assertEquals(hostClip, canvas.clip);
		}
	}

	@Test
	public void test_repeatedTransformAndClipReplacementKeepsBoundedSaveDepth() {
		try (Fixture fixture = new Fixture()) {
			Transform transform = new Transform(fixture.display);
			try {
				for (int frame = 0; frame < 2000; frame++) {
					transform.setElements(1, 0, 0, 1, frame % 7, frame % 11);
					fixture.gc.setTransform(transform);
					fixture.gc.setClipping(new Rectangle(10, 10, 20, 20));
					fixture.gc.setTransform(null);
					fixture.gc.setClipping((Rectangle) null);
					assertTrue(fixture.canvas.getSaveCount() >= fixture.hostSaveCount);
					assertTrue(fixture.canvas.getSaveCount() <= fixture.hostSaveCount + 1,
							"replacement must not keep a canvas frame for each setter call");
					assertArrayEquals(Matrix33.IDENTITY.getMat(), fixture.canvas.matrix.getMat());
				}
				assertTrue(fixture.canvas.peakSaveCount <= fixture.hostSaveCount + 1);
				fixture.gc.dispose();
				fixture.assertHostRestored();
			} finally {
				transform.dispose();
			}
		}
	}

	@Test
	public void test_firstClipPreservesHostMatrixUntilIdentityIsExplicitlyRequested() {
		try (Fixture fixture = new Fixture()) {
			fixture.surface.calls.clear();
			fixture.gc.setClipping(new Rectangle(10, 10, 20, 20));
			assertEquals(List.of("save", "clipRect"), callNames(fixture));
			assertArrayEquals(fixture.hostMatrix.getMat(), fixture.canvas.matrix.getMat());
			assertEquals(new Rectangle(13, 14, 20, 20), fixture.canvas.clip);
			fixture.gc.setClipping(new Rectangle(20, 20, 10, 10));
			assertArrayEquals(fixture.hostMatrix.getMat(), fixture.canvas.matrix.getMat());
			assertEquals(new Rectangle(23, 24, 10, 10), fixture.canvas.clip);
			fixture.gc.setClipping((Rectangle) null);
			assertArrayEquals(fixture.hostMatrix.getMat(), fixture.canvas.matrix.getMat());
			assertEquals(fixture.hostClip, fixture.canvas.clip);
			assertFalse(callNames(fixture).contains("setMatrix"),
					"clip replacement must preserve the inherited host matrix before any explicit transform");

			fixture.gc.setClipping(new Rectangle(10, 10, 20, 20));
			fixture.surface.calls.clear();
			fixture.gc.setTransform(null);
			assertEquals(List.of("setMatrix"), callNames(fixture),
					"the first explicit identity is a real change from an unobserved host transform");
			assertArrayEquals(Matrix33.IDENTITY.getMat(), fixture.canvas.matrix.getMat());
			assertEquals(new Rectangle(13, 14, 20, 20), fixture.canvas.clip,
					"explicitly resetting the CTM cannot relocate an already established clip");
			fixture.gc.dispose();
			fixture.assertHostRestored();
		}
	}

	@Test
	public void test_unchangedTransformsAndAlreadyClearClipDoNotCallTheCanvas() {
		try (Fixture fixture = new Fixture()) {
			Transform transform = new Transform(fixture.display, 1, 0, 0, 1, 12, 15);
			Transform equivalent = new Transform(fixture.display, 1, 0, 0, 1, 12, 15);
			try {
				fixture.surface.calls.clear();
				fixture.gc.setTransform(null);
				assertEquals(List.of("save", "setMatrix"), callNames(fixture));
				for (int repeat = 0; repeat < 2000; repeat++) {
					fixture.gc.setTransform(null);
					fixture.gc.setClipping((Rectangle) null);
					fixture.gc.setClipping((Region) null);
				}
				assertEquals(List.of("save", "setMatrix"), callNames(fixture),
						"an established identity CTM and absent clip need no more native canvas calls");
				fixture.gc.setTransform(transform);
				assertEquals(List.of("save", "setMatrix", "setMatrix"), callNames(fixture),
						"changing the transform replaces the matrix without saving another frame");
				fixture.gc.setClipping(new Rectangle(10, 10, 20, 20));
				fixture.surface.calls.clear();
				for (int repeat = 0; repeat < 2000; repeat++) fixture.gc.setTransform(equivalent);
				assertTrue(fixture.surface.calls.isEmpty(),
						"equivalent transform values need no calls even while a clip is installed");
				assertEquals(new Rectangle(22, 25, 20, 20), fixture.canvas.clip);
				assertEquals(fixture.hostSaveCount + 1, fixture.canvas.getSaveCount());
			} finally {
				equivalent.dispose();
				transform.dispose();
			}
		}
	}

	@Test
	public void test_clipRemainsDeviceAnchoredWhenTransformChangesAndReplacementKeepsTransform() {
		try (Fixture fixture = new Fixture()) {
			Transform first = new Transform(fixture.display, 1, 0, 0, 1, 12, 15);
			Transform second = new Transform(fixture.display, 1, 0, 0, 1, 3, 5);
			Region observed = new Region(fixture.display);
			try {
				fixture.gc.setTransform(first);
				fixture.gc.setClipping(new Rectangle(10, 20, 30, 15));
				assertEquals(new Rectangle(22, 35, 30, 15), fixture.canvas.clip);
				fixture.gc.setTransform(second);
				assertEquals(new Rectangle(22, 35, 30, 15), fixture.canvas.clip,
						"changing the CTM cannot move an established clip");
				assertEquals(new Rectangle(19, 30, 30, 15), fixture.gc.getClipping());
				fixture.gc.getClipping(observed);
				assertEquals(fixture.gc.getClipping(), observed.getBounds());

				Rectangle replacement = new Rectangle(10, 10, 6, 7);
				fixture.gc.setClipping(replacement);
				replacement.x = 90;
				assertEquals(new Rectangle(13, 15, 6, 7), fixture.canvas.clip);
				assertArrayEquals(Matrix33.makeTranslate(3, 5).getMat(), fixture.canvas.matrix.getMat(),
						"replacing a clip cannot reset the current matrix to its older saved value");
				assertEquals(new Rectangle(10, 10, 6, 7), fixture.gc.getClipping());
				fixture.gc.setClipping((Rectangle) null);
				assertFalse(fixture.gc.isClipped());
				assertArrayEquals(Matrix33.makeTranslate(3, 5).getMat(), fixture.canvas.matrix.getMat());
				assertEquals(fixture.hostClip, fixture.canvas.clip);
			} finally {
				observed.dispose();
				second.dispose();
				first.dispose();
			}
		}
	}

	@Test
	public void test_translatedRegionClipUsesDeviceCoordinatesAndKeepsItsHole() {
		try (Fixture fixture = new Fixture()) {
			Transform first = new Transform(fixture.display, 1, 0, 0, 1, 12, 15);
			Transform second = new Transform(fixture.display, 1, 0, 0, 1, 2, 5);
			Region caller = new Region(fixture.display);
			Region observed = new Region(fixture.display);
			try {
				caller.add(new Rectangle(10, 10, 20, 20));
				caller.subtract(new Rectangle(15, 15, 5, 5));
				fixture.gc.setTransform(first);
				fixture.gc.setClipping(caller);
				caller.dispose();
				assertEquals(new Rectangle(22, 25, 20, 20), fixture.canvas.clip,
						"Maya clipRegion ignores its canvas CTM, so its region must already be mapped");
				fixture.gc.getClipping(observed);
				assertEquals(new Rectangle(10, 10, 20, 20), observed.getBounds());
				assertFalse(observed.contains(16, 16));
				fixture.gc.setTransform(second);
				assertEquals(new Rectangle(22, 25, 20, 20), fixture.canvas.clip);
				fixture.gc.getClipping(observed);
				assertEquals(new Rectangle(20, 20, 20, 20), observed.getBounds());
				assertEquals(observed.getBounds(), fixture.gc.getClipping());
				assertTrue(observed.contains(22, 22));
				assertFalse(observed.contains(26, 26),
						"querying after a CTM change maps the fixed device-space hole back to user coordinates");
			} finally {
				observed.dispose();
				if (!caller.isDisposed()) caller.dispose();
				second.dispose();
				first.dispose();
			}
		}
	}

	@Test
	public void test_regionIsCopiedWithHolesAndCallerMayMutateAndDisposeIt() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Region caller = new Region(fixture.display);
			Region observed = new Region(fixture.display);
			try {
				caller.add(new Rectangle(10, 10, 20, 20));
				caller.subtract(new Rectangle(15, 15, 5, 5));
				fixture.gc.setTransform(null);
				fixture.gc.setClipping(caller);
				Region owned = ownedClip(fixture.gc);
				assertNotNull(owned);
				assertNotSame(caller, owned, "the GC must own a snapshot, never the caller Region");
				fixture.surface.calls.clear();
				fixture.gc.setClipping(caller);
				assertTrue(owned.isDisposed(), "an equal replacement must release its previous owned snapshot");
				owned = ownedClip(fixture.gc);
				assertNotSame(caller, owned);
				assertFalse(owned.isDisposed());
				assertTrue(fixture.surface.calls.isEmpty(),
						"an identical device-space Region needs no canvas reset or repeated native clipping");
				caller.translate(50, 50);
				caller.dispose();
				fixture.gc.getClipping(observed);
				assertEquals(new Rectangle(10, 10, 20, 20), observed.getBounds());
				assertEquals(observed.getBounds(), fixture.gc.getClipping());
				assertTrue(observed.contains(12, 12));
				assertFalse(observed.contains(16, 16), "the interior hole must survive the snapshot");

				observed.translate(40, 0);
				fixture.gc.getClipping(observed);
				assertEquals(new Rectangle(10, 10, 20, 20), observed.getBounds(),
						"getClipping replaces the destination and does not retain its previous contents");
				assertFalse(observed.contains(16, 16));
				fixture.gc.setClipping(new Rectangle(2, 3, 6, 7));
				assertTrue(owned.isDisposed(), "replacing the clip frees the GC-owned Region snapshot");
			} finally {
				observed.dispose();
				if (!caller.isDisposed()) caller.dispose();
			}
		}
	}

	@Test
	public void test_clearingAndDisposingReleaseOwnedClipButNotCallerRegions() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Region caller = new Region(fixture.display);
			try {
				caller.add(new Rectangle(10, 10, 20, 20));
				fixture.gc.setClipping(caller);
				Region first = ownedClip(fixture.gc);
				fixture.gc.setClipping((Region) null);
				assertTrue(first.isDisposed());
				assertFalse(caller.isDisposed());
				assertFalse(fixture.gc.isClipped());
				fixture.gc.setClipping(caller);
				Region second = ownedClip(fixture.gc);
				assertNotSame(first, second);
				fixture.gc.dispose();
				assertTrue(second.isDisposed());
				assertFalse(caller.isDisposed());
				fixture.assertHostRestored();
			} finally {
				caller.dispose();
			}
		}
	}

	@Test
	public void test_disposedRegionRefusedBeforeReplacingCurrentState() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Region caller = new Region(fixture.display);
			Region invalid = new Region(fixture.display);
			try {
				caller.add(new Rectangle(10, 10, 20, 20));
				fixture.gc.setClipping(caller);
				Region owned = ownedClip(fixture.gc);
				Rectangle before = fixture.gc.getClipping();
				int saveCount = fixture.canvas.getSaveCount();
				invalid.dispose();
				assertThrows(IllegalArgumentException.class, () -> fixture.gc.setClipping(invalid));
				assertEquals(before, fixture.gc.getClipping());
				assertSame(owned, ownedClip(fixture.gc));
				assertFalse(owned.isDisposed());
				assertEquals(saveCount, fixture.canvas.getSaveCount());
			} finally {
				if (!invalid.isDisposed()) invalid.dispose();
				caller.dispose();
			}
		}
	}

	@Test
	public void test_disposedReusableGcPreservesHostStateAcrossTheNextOwnerDraw() {
		try (Fixture fixture = new Fixture()) {
			Transform transform = new Transform(fixture.display, 1, 0, 0, 1, 12, 15);
			try {
				for (int event = 0; event < 3; event++) {
					fixture.gc.setTransform(transform);
					fixture.gc.setClipping(new Rectangle(10 + event, 20, 8, 9));
					assertTrue(fixture.gc.isClipped());
					fixture.gc.dispose();
					assertFalse(fixture.gc.isClipped());
					fixture.assertHostRestored();
				}
				fixture.gc.dispose();
				fixture.assertHostRestored();
			} finally {
				transform.dispose();
			}
		}
	}

	@Test
	public void test_reusedGcCapturesTheHostSaveDepthForEachOwnerDraw() {
		try (Fixture fixture = new Fixture()) {
			Transform transform = new Transform(fixture.display, 1, 0, 0, 1, 12, 15);
			try {
				fixture.gc.setTransform(transform);
				fixture.gc.dispose();
				fixture.assertHostRestored();

				fixture.canvas.save();
				fixture.canvas.setMatrix(Matrix33.makeTranslate(7, 8));
				fixture.canvas.clipRect(new Rect(0, 0, 80, 80));
				int deeperSaveCount = fixture.canvas.getSaveCount();
				Rectangle deeperClip = copy(fixture.canvas.clip);
				fixture.gc.setTransform(transform);
				fixture.gc.setClipping(new Rectangle(10, 10, 20, 20));
				fixture.gc.dispose();
				assertEquals(deeperSaveCount, fixture.canvas.getSaveCount());
				assertArrayEquals(Matrix33.makeTranslate(7, 8).getMat(), fixture.canvas.matrix.getMat());
				assertEquals(deeperClip, fixture.canvas.clip);

				fixture.canvas.restore();
				fixture.canvas.restore();
				fixture.canvas.setMatrix(Matrix33.makeTranslate(9, 10));
				int shallowerSaveCount = fixture.canvas.getSaveCount();
				Rectangle shallowerClip = copy(fixture.canvas.clip);
				fixture.gc.setTransform(transform);
				fixture.gc.setClipping(new Rectangle(10, 10, 20, 20));
				fixture.gc.dispose();
				assertEquals(shallowerSaveCount, fixture.canvas.getSaveCount());
				assertArrayEquals(Matrix33.makeTranslate(9, 10).getMat(), fixture.canvas.matrix.getMat());
				assertEquals(shallowerClip, fixture.canvas.clip,
						"reuse must preserve the current host, not a save depth captured at construction");
			} finally {
				transform.dispose();
			}
		}
	}

	@Test
	public void test_doubleDpiClientBoundsAreAlreadyDevicePixels() {
		try (Fixture fixture = new Fixture(200)) {
			Region observed = new Region(fixture.display);
			try {
				fixture.gc.setTransform(null);
				assertEquals(new Rectangle(0, 0, 50, 50), fixture.gc.getClipping(),
						"the extension's 100 device pixels are 50 user units at 200 percent DPI");
				fixture.gc.getClipping(observed);
				assertEquals(new Rectangle(0, 0, 50, 50), observed.getBounds());
			} finally {
				observed.dispose();
			}
		}
	}

	@Test
	public void test_transformRoundTripKeepsShearAndScalesOnlyTranslationAtDoubleDpi() {
		try (Fixture fixture = new Fixture(200)) {
			Transform source = new Transform(fixture.display, 2, 0.25f, 0.5f, 3, 6, 7);
			Transform observed = new Transform(fixture.display);
			try {
				fixture.gc.setTransform(source);
				assertArrayEquals(new float[] {2, 0.5f, 12, 0.25f, 3, 14, 0, 0, 1},
						fixture.canvas.matrix.getMat());
				fixture.gc.getTransform(observed);
				float[] elements = new float[6];
				observed.getElements(elements);
				assertArrayEquals(new float[] {2, 0.25f, 0.5f, 3, 6, 7}, elements,
						"the inverse conversion must not scale shear or leave X translation in pixels");
				fixture.gc.setTransform(null);
				fixture.gc.getTransform(observed);
				observed.getElements(elements);
				assertArrayEquals(new float[] {1, 0, 0, 1, 0, 0}, elements);
			} finally {
				observed.dispose();
				source.dispose();
			}
		}
	}

	private static List<String> callNames(Fixture fixture) {
		return fixture.surface.calls.stream().map(call -> call.methodName).toList();
	}

	private static Region ownedClip(RasterExternalGC gc) throws Exception {
		Field field = RasterExternalGC.class.getDeclaredField("currentClipRegion");
		field.setAccessible(true);
		return (Region) field.get(gc);
	}

	private static Rectangle copy(Rectangle rectangle) {
		return new Rectangle(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
	}
}
