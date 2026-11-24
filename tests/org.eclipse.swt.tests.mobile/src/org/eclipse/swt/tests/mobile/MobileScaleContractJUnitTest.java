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
 *     Hitesh Soliwal (Synexia) - fractional-density capture bounds coverage
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;

import org.eclipse.swt.internal.mobile.MobileScale;
import org.junit.jupiter.api.Test;

/**
 * Executed logical/native rounding contracts for a density-scaled transport.
 * AndroidPlatform rounds displayBounds() and maps captureScreen() requests with
 * MobileScale; Android PixelCopy on a device remains a separate native gate.
 */
public final class MobileScaleContractJUnitTest {
	/** Android density buckets and fractional device/emulator densities. */
	private static final float[] DENSITIES = {0.75f, 1f, 1.3312501f, 1.5f, 1.75f, 2f, 2.225f, 2.5f, 2.625f, 2.75f, 3f, 3.5f, 4f};

	@Test void pixel6ClientAreaIsCapturableAtDensity2625() {
		// pixel_6 emulator: root 1080x2400 at 0,0 with padding 0,128,0,63 (cutout and navigation bar).
		float density = 2.625f;
		int left = 0, top = 128, right = 1080, bottom = 2400 - 63;
		int width = MobileScale.logical(right - left, density);
		int height = MobileScale.logical(bottom - top, density);
		assertEquals(411, width);
		assertEquals(842, height, "2209 / 2.625 = 841.52 is reported as 842");
		assertEquals(2210, MobileScale.physical(height, density), "842 logical rows map back past the 2209 native rows");
		assertArrayEquals(new int[] {0, 128, 1079, 2337}, MobileScale.captureSource(density, left, top, right, bottom, 0, 0, width, height));
		assertArrayEquals(new int[] {0, 2336, 1079, 2337}, MobileScale.captureSource(density, left, top, right, bottom, 0, height - 1, width, 1));
		assertOutside(density, left, top, right, bottom, 0, 0, width, height + 1);
		assertOutside(density, left, top, right, bottom, 0, 1, width, height);
		assertOutside(density, left, top, right, bottom, 0, 0, width + 1, height);
		assertOutside(density, left, top, right, bottom, 1, 0, width, height);
		assertOutside(density, left, top, right, bottom, -1, 0, 1, 1);
		assertOutside(density, left, top, right, bottom, 0, -1, 1, 1);
	}

	@Test void everyReportedClientAreaIsCapturable() {
		for (float density : DENSITIES) {
			for (int extent = 1; extent <= 3000; extent++) {
				int logical = MobileScale.logical(extent, density);
				if (logical == 0) continue;
				int left = 7, top = 128, right = left + extent, bottom = top + extent;
				Supplier<String> at = () -> " at density " + density + " for " + (right - left) + " native pixels";
				assertInside(MobileScale.captureSource(density, left, top, right, bottom, 0, 0, logical, logical), left, top, right, bottom, at);
				assertInside(MobileScale.captureSource(density, left, top, right, bottom, logical - 1, logical - 1, 1, 1), left, top, right, bottom, at);
				if (legacy(density, left, top, right, bottom, 0, 0, logical + 1, logical + 1) == null)
					assertOutside(density, left, top, right, bottom, 0, 0, logical + 1, logical + 1);
			}
		}
		// Below density 1 the last logical column can start on the content edge; it keeps one native pixel.
		assertEquals(3, MobileScale.logical(2, 0.75f));
		assertArrayEquals(new int[] {1, 0, 2, 1}, MobileScale.captureSource(0.75f, 0, 0, 2, 2, 2, 0, 1, 1));
	}

	@Test void earlierAcceptedCapturesAreUnchangedAndOnlyTheReportedAreaIsAdded() {
		for (float density : DENSITIES) {
			for (int extent = 1; extent <= 48; extent++) {
				int logical = MobileScale.logical(extent, density);
				for (int offset = -2; offset <= logical + 2; offset++) {
					for (int length = 1; length <= logical + 3; length++) {
						// Horizontal axis varies; the vertical request fits the old rule.
						checkAxis(density, 5, 3, 5 + extent, 103, offset, 0, length, 1, offset, length, logical);
						// Vertical axis varies; the horizontal request fits the old rule.
						checkAxis(density, 3, 5, 103, 5 + extent, 0, offset, 1, length, offset, length, logical);
					}
				}
			}
		}
	}

	@Test void invalidExtentsAndOverflowKeepTheirExceptions() {
		float density = 2.625f;
		assertThrows(IllegalArgumentException.class, () -> MobileScale.captureSource(density, 0, 128, 1080, 2337, 0, 0, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> MobileScale.captureSource(density, 0, 128, 1080, 2337, 0, 0, 1, -1));
		assertThrows(ArithmeticException.class, () -> MobileScale.captureSource(density, 0, 128, 1080, 2337, Integer.MAX_VALUE, 0, 1, 1));
		assertThrows(ArithmeticException.class, () -> MobileScale.captureSource(density, 0, 128, 1080, 2337, 0, Integer.MAX_VALUE, 1, 1));
		assertThrows(ArithmeticException.class, () -> MobileScale.captureSource(density, 0, 128, 1080, 2337, 1, 0, Integer.MAX_VALUE, 1));
		assertThrows(ArithmeticException.class, () -> legacy(density, 0, 128, 1080, 2337, 1, 0, Integer.MAX_VALUE, 1));
		assertOutside(density, 0, 128, 1080, 2337, 0, 0, Integer.MAX_VALUE, 1);
		assertNull(legacy(density, 0, 128, 1080, 2337, 0, 0, Integer.MAX_VALUE, 1));
	}

	private static void checkAxis(float density, int left, int top, int right, int bottom,
			int x, int y, int width, int height, int offset, int length, int logical) {
		int[] before = legacy(density, left, top, right, bottom, x, y, width, height);
		int[] now;
		try {
			now = MobileScale.captureSource(density, left, top, right, bottom, x, y, width, height);
		} catch (IllegalArgumentException outside) {
			now = null;
		}
		Supplier<String> at = () -> " at density " + density + " for request " + x + "," + y + " " + width + "x" + height
				+ " in " + left + "," + top + "-" + right + "," + bottom;
		if (before != null) {
			assertArrayEquals(before, now, () -> "an earlier accepted capture keeps its native rectangle" + at.get());
		} else if (offset >= 0 && offset + length <= logical) {
			assertNotNull(now, () -> "the reported logical area is capturable" + at.get());
			assertInside(now, left, top, right, bottom, at);
		} else {
			assertNull(now, () -> "a request outside both the native content and the reported area is refused" + at.get());
		}
	}

	/** AndroidPlatform.captureScreen bounds before fractional-density acceptance; null means refused. */
	private static int[] legacy(float density, int contentLeft, int contentTop, int contentRight, int contentBottom,
			int x, int y, int width, int height) {
		int left = Math.addExact(contentLeft, Math.round(x * density));
		int top = Math.addExact(contentTop, Math.round(y * density));
		int right = Math.addExact(left, Math.round(width * density));
		int bottom = Math.addExact(top, Math.round(height * density));
		if (x < 0 || y < 0 || right > contentRight || bottom > contentBottom) return null;
		return new int[] {left, top, right, bottom};
	}

	private static void assertInside(int[] source, int left, int top, int right, int bottom, Supplier<String> at) {
		assertTrue(left <= source[0] && source[0] < source[2] && source[2] <= right, () -> "native columns inside the content" + at.get());
		assertTrue(top <= source[1] && source[1] < source[3] && source[3] <= bottom, () -> "native rows inside the content" + at.get());
	}

	private static void assertOutside(float density, int left, int top, int right, int bottom, int x, int y, int width, int height) {
		IllegalArgumentException outside = assertThrows(IllegalArgumentException.class,
				() -> MobileScale.captureSource(density, left, top, right, bottom, x, y, width, height));
		assertEquals("Capture outside visible SWT window", outside.getMessage());
	}
}
