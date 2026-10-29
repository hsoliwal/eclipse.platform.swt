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
 *     Hitesh Soliwal (Synexia) - visible repaint qualification regressions
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.eclipse.swt.internal.ViewportDamageRecorder;
import org.junit.jupiter.api.Test;

/** Independent pixel and exact-integer oracles; no native widget fixture. */
public class Test_ViewportDamageRecorderClipping {
	@Test
	public void protectedOverscanCannotHideAFullVisibleRepaint () {
		ViewportDamageRecorder recorder = new ViewportDamageRecorder (100, 100);
		recorder.protectedArea ().add (100, 0, 100, 100);
		recorder.paint (0, 0, 200, 100);
		recorder.endFrame ();
		assertEquals (20_000, recorder.paintedPx (0));
		assertEquals (10_000, recorder.visiblePaintedPx (0));
		assertEquals (10_000, recorder.protectedHitPx (0), "offscreen protected violations remain observable");
		assertTrue (recorder.isFullRepaint (0), "protected overscan is outside the visible denominator");
		assertEquals (1, recorder.fullRepaints ());
	}

	@Test
	public void resizingClipsOldProtectedGeometryForRepaintAccounting () {
		ViewportDamageRecorder recorder = new ViewportDamageRecorder (200, 100);
		recorder.protectedArea ().add (90, -10, 110, 120);
		recorder.setViewport (100, 100);
		recorder.paint (0, 0, 200, 100);
		recorder.endFrame ();
		assertEquals (11_000, recorder.protectedHitPx (0));
		assertEquals (10_000, recorder.visiblePaintedPx (0));
		assertTrue (recorder.isFullRepaint (0), "all 9,000 unprotected visible pixels were painted");
	}

	@Test
	public void copiesKeepTheirProtectedDiagnosticsWithoutBecomingPaint () {
		ViewportDamageRecorder recorder = new ViewportDamageRecorder (100, 100);
		recorder.protectedArea ().add (-50, 0, 50, 100);
		recorder.copy (0, 0, -50, 0, 50, 100);
		recorder.paint (0, 0, 100, 100);
		recorder.endFrame ();
		assertEquals (5_000, recorder.protectedHitPx (0));
		assertEquals (1, recorder.copyRects (0));
		assertEquals (10_000, recorder.paintedPx (0));
		assertTrue (recorder.isFullRepaint (0));
	}

	@Test
	public void emptyHugeViewportIsNeverAFullRepaint () {
		ViewportDamageRecorder recorder = new ViewportDamageRecorder (Integer.MAX_VALUE, Integer.MAX_VALUE);
		for (int percent = 1; percent <= 100; percent++) {
			recorder.setFullRepaintPercent (percent);
			recorder.endFrame ();
			assertFalse (recorder.isFullRepaint (percent - 1), "empty frame at " + percent + "%");
		}
		assertEquals (0, recorder.fullRepaints ());
	}

	@Test
	public void largeThresholdsMatchBigIntegerOracle () {
		int limit = 1 << 29;
		for (int percent = 1; percent <= 100; percent++) {
			for (int columns : new int [] {0, 1, limit / 2, limit - 1, limit}) {
				ViewportDamageRecorder recorder = new ViewportDamageRecorder (limit, limit);
				recorder.setFullRepaintPercent (percent);
				recorder.paint (0, 0, columns, limit);
				recorder.endFrame ();
				BigInteger painted = BigInteger.valueOf ((long) columns * limit).multiply (BigInteger.valueOf (100));
				BigInteger required = BigInteger.valueOf ((long) limit * limit).multiply (BigInteger.valueOf (percent));
				assertEquals (painted.compareTo (required) >= 0, recorder.isFullRepaint (0), columns + " columns at " + percent + "%");
			}
		}
	}

	@Test
	public void thresholdRoundsUpToTheNextWholePixel () {
		ViewportDamageRecorder recorder = new ViewportDamageRecorder (21, 1);
		recorder.paint (0, 0, 19, 1);
		recorder.endFrame ();
		recorder.paint (0, 0, 20, 1);
		recorder.endFrame ();
		assertFalse (recorder.isFullRepaint (0));
		assertTrue (recorder.isFullRepaint (1));
	}

	@Test
	public void clippedProtectedCoverageMatchesIndependentPixelSets () {
		Random random = new Random (0x53575444414d4147L);
		for (int sample = 0; sample < 4_000; sample++) {
			int width = 1 + random.nextInt (12), height = 1 + random.nextInt (10);
			int percent = 1 + random.nextInt (100);
			ViewportDamageRecorder recorder = new ViewportDamageRecorder (width, height);
			recorder.setFullRepaintPercent (percent);
			boolean [] protectedPixels = new boolean [width * height];
			boolean [] paintedPixels = new boolean [width * height];
			for (int layer = 0; layer < 2; layer++) {
				int rectangles = random.nextInt (8);
				for (int rectangle = 0; rectangle < rectangles; rectangle++) {
					int x = random.nextInt (width + 12) - 6;
					int y = random.nextInt (height + 12) - 6;
					int w = random.nextInt (width + 8), h = random.nextInt (height + 8);
					if (layer == 0) recorder.protectedArea ().add (x, y, w, h);
					else recorder.paint (x, y, w, h);
					boolean [] pixels = layer == 0 ? protectedPixels : paintedPixels;
					for (int py = 0; py < height; py++) {
						for (int px = 0; px < width; px++) {
							if (px >= x && px < x + w && py >= y && py < y + h) pixels [py * width + px] = true;
						}
					}
				}
			}
			int available = 0, covered = 0, visible = 0;
			for (int pixel = 0; pixel < paintedPixels.length; pixel++) {
				if (paintedPixels [pixel]) visible++;
				if (!protectedPixels [pixel]) {
					available++;
					if (paintedPixels [pixel]) covered++;
				}
			}
			assertFalse (recorder.currentPainted ().overflowed (), "oracle corpus must have exact damage");
			assertFalse (recorder.protectedArea ().overflowed (), "oracle corpus must have exact protection");
			recorder.endFrame ();
			assertEquals (visible, recorder.visiblePaintedPx (0), "visible pixels at sample " + sample);
			assertEquals (available > 0 && covered * 100 >= available * percent,
					recorder.isFullRepaint (0), "pixel-set oracle at sample " + sample);
		}
	}
}
