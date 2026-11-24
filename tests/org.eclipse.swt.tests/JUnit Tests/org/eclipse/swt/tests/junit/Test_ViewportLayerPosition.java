/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.eclipse.swt.internal.ViewportRuntime.LayerPosition;
import org.eclipse.swt.internal.ViewportRuntime.PinnedPlanes;
import org.junit.jupiter.api.Test;

/** Headless, contract-preserving model tests for independent SWT layer motion. */
public class Test_ViewportLayerPosition {
	private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
	private static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);
	private static final int[] RATES = {-4096, -1024, -512, -256, -128, -1,
			0, 1, 128, 255, 256, 257, 512, 1024, 4096};
	private static final long[] EDGES = {Long.MIN_VALUE, Long.MIN_VALUE + 1,
			-100, -1, 0, 1, 100, Long.MAX_VALUE - 1, Long.MAX_VALUE};

	private static long saturated(BigInteger value) {
		if (value.compareTo(MAX) > 0) return Long.MAX_VALUE;
		if (value.compareTo(MIN) < 0) return Long.MIN_VALUE;
		return value.longValueExact();
	}

	private static long oracle(long position, long extent, long origin, int viewport,
			int rate, int anchor, int sticky, int inset) {
		BigInteger span = BigInteger.valueOf(viewport).subtract(BigInteger.valueOf(extent));
		BigInteger bias = switch (anchor) {
			case LayerPosition.ANCHOR_START -> BigInteger.ZERO;
			case LayerPosition.ANCHOR_CENTER -> span.shiftRight(1);
			case LayerPosition.ANCHOR_END -> span;
			default -> throw new AssertionError(anchor);
		};
		BigInteger delta = BigInteger.valueOf(origin)
				.multiply(BigInteger.valueOf(rate)).shiftRight(8);
		long placed = saturated(BigInteger.valueOf(position).add(bias).subtract(delta));
		return switch (sticky) {
			case LayerPosition.STICK_NONE -> placed;
			case LayerPosition.STICK_START -> Math.max(placed, inset);
			case LayerPosition.STICK_END -> Math.min(placed,
					saturated(span.subtract(BigInteger.valueOf(inset))));
			default -> throw new AssertionError(sticky);
		};
	}

	@Test
	public void fixedFlowParallaxAnchorsAndStickyAreDistinctPolicies() {
		assertEquals(20, LayerPosition.coordinate(50, 15, 30, 300,
				LayerPosition.SCROLL, 0, 0, 0));
		assertEquals(50, LayerPosition.coordinate(50, 15, 30, 300,
				LayerPosition.FIXED, 0, 0, 0));
		assertEquals(35, LayerPosition.coordinate(50, 15, 30, 300,
				LayerPosition.PARALLAX_HALF, 0, 0, 0));
		assertEquals(290, LayerPosition.coordinate(-10, 100, 12345, 400,
				LayerPosition.FIXED, LayerPosition.ANCHOR_END, 0, 0));
		assertEquals(0, LayerPosition.coordinate(100, 15, 150, 400,
				LayerPosition.SCROLL, 0, LayerPosition.STICK_START, 0));
		assertEquals(270, LayerPosition.coordinate(500, 30, 0, 300,
				LayerPosition.SCROLL, 0, LayerPosition.STICK_END, 0));
		assertFalse(LayerPosition.movesWithScroll(0, 256, true, false));
		assertTrue(LayerPosition.movesWithScroll(0, 256, false, true));
		assertFalse(LayerPosition.movesWithScroll(0, 0, true, true));
		assertTrue(LayerPosition.movesWithScroll(128, 0, true, false));
	}

	@Test
	public void fiftyThousandExtremeCoordinatesMatchIndependentExactOracle() {
		Random random = new Random(0x718188L);
		for (int i = 0; i < 50_000; i++) {
			long position = i % 3 == 0 ? EDGES[random.nextInt(EDGES.length)] : random.nextLong();
			long extent = i % 5 == 0 ? EDGES[random.nextInt(EDGES.length)] : random.nextInt(1000);
			if (extent < 0) extent = extent == Long.MIN_VALUE ? Long.MAX_VALUE : -extent;
			long origin = i % 2 == 0 ? EDGES[random.nextInt(EDGES.length)] : random.nextLong();
			int viewport = random.nextInt(25_000);
			int rate = RATES[random.nextInt(RATES.length)];
			int anchor = random.nextInt(3);
			int sticky = random.nextInt(3);
			int inset = random.nextInt(500);
			long actual = LayerPosition.coordinate(position, extent, origin,
					viewport, rate, anchor, sticky, inset);
			assertEquals(oracle(position, extent, origin, viewport, rate, anchor,
					sticky, inset), actual, "sample " + i);
		}
	}

	@Test
	public void halfOpenClippingUsesCallerOwnedScratchWithoutOverflow() {
		Random random = new Random(0xCAFED00DL);
		int[] pixels = new int[4];
		assertTrue(LayerPosition.clip(-10, 10, 50, 50, 30, 40, pixels));
		assertArrayEquals(new int[] {0, 10, 30, 30}, pixels);
		for (int i = 0; i < 20_000; i++) {
			long x = i % 3 == 0 ? EDGES[random.nextInt(EDGES.length)] : random.nextLong();
			long y = i % 5 == 0 ? EDGES[random.nextInt(EDGES.length)] : random.nextLong();
			long width = i % 2 == 0 ? random.nextInt(10000) : Math.max(0, random.nextLong());
			long height = i % 7 == 0 ? Long.MAX_VALUE : random.nextInt(10000);
			int w = random.nextInt(1600), h = random.nextInt(1200);
			BigInteger x0 = BigInteger.valueOf(x).max(BigInteger.ZERO);
			BigInteger y0 = BigInteger.valueOf(y).max(BigInteger.ZERO);
			BigInteger x1 = BigInteger.valueOf(x).add(BigInteger.valueOf(width))
					.min(BigInteger.valueOf(w));
			BigInteger y1 = BigInteger.valueOf(y).add(BigInteger.valueOf(height))
					.min(BigInteger.valueOf(h));
			boolean visible = x0.compareTo(x1) < 0 && y0.compareTo(y1) < 0;
			assertEquals(visible, LayerPosition.clip(x, y, width, height, w, h, pixels),
					"clip sample " + i);
			if (visible) {
				assertArrayEquals(new int[] {x0.intValueExact(), y0.intValueExact(),
						x1.subtract(x0).intValueExact(), y1.subtract(y0).intValueExact()},
						pixels, "clip sample " + i);
			} else {
				assertArrayEquals(new int[4], pixels);
			}
		}
	}

	@Test
	public void nativeTreeHeaderAndFrozenColumnAreIndependentAxes() {
		PinnedPlanes p = new PinnedPlanes(600, 400, 32, 90);
		int[] bounds = new int[4];
		assertEquals(PinnedPlanes.BODY | PinnedPlanes.HEADER | PinnedPlanes.FROZEN,
				p.scrollTo(150, 800));
		assertEquals(PinnedPlanes.HEADER, p.planeAt(150, 10));
		assertEquals(PinnedPlanes.CORNER, p.planeAt(10, 10));
		assertEquals(PinnedPlanes.FROZEN, p.planeAt(10, 100));
		assertEquals(PinnedPlanes.BODY, p.planeAt(150, 100));
		assertTrue(p.project(PinnedPlanes.HEADER, 200, 4, 100, 24, bounds));
		assertArrayEquals(new int[] {90, 4, 60, 24}, bounds);
		assertTrue(p.project(PinnedPlanes.BODY, 200, 820, 100, 30, bounds));
		assertArrayEquals(new int[] {90, 52, 60, 30}, bounds);
		assertTrue(p.project(PinnedPlanes.FROZEN, 10, 820, 50, 30, bounds));
		assertArrayEquals(new int[] {10, 52, 50, 30}, bounds);
		assertEquals(PinnedPlanes.BODY | PinnedPlanes.FROZEN, p.scrollTo(150, 900));
		assertEquals(PinnedPlanes.BODY | PinnedPlanes.HEADER, p.scrollTo(151, 900));
		assertEquals(0, p.scrollTo(151, 900));
	}

	@Test
	public void fourPinnedPlanesPartitionEveryPixelWithoutOverlap() {
		int[] clip = new int[4];
		for (int width = 0; width <= 9; width++) {
			for (int height = 0; height <= 9; height++) {
				for (int frozen = 0; frozen <= width; frozen++) {
					for (int header = 0; header <= height; header++) {
						PinnedPlanes p = new PinnedPlanes(width, height, header, frozen);
						int sum = 0;
						for (int plane : new int[] {PinnedPlanes.BODY,
								PinnedPlanes.HEADER, PinnedPlanes.FROZEN, PinnedPlanes.CORNER}) {
							if (p.clip(plane, clip)) sum += clip[2] * clip[3];
						}
						assertEquals(width * height, sum, "partition");
						for (int y = 0; y < height; y++) {
							for (int x = 0; x < width; x++) {
								int chosen = p.planeAt(x, y), hits = 0;
								for (int plane : new int[] {PinnedPlanes.BODY,
										PinnedPlanes.HEADER, PinnedPlanes.FROZEN,
										PinnedPlanes.CORNER}) {
									if (!p.clip(plane, clip)) continue;
									if (x >= clip[0] && x < clip[0] + clip[2]
											&& y >= clip[1] && y < clip[1] + clip[3]) {
										hits++;
										assertEquals(plane, chosen);
									}
								}
								assertEquals(1, hits);
							}
						}
					}
				}
			}
		}
	}

	@Test
	public void invalidPoliciesAndOutOfBoundsDoNotLeakState() {
		int[] scratch = new int[4];
		assertThrows(IllegalArgumentException.class, () ->
				LayerPosition.coordinate(0, 1, 0, 100, 4097, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () ->
				LayerPosition.coordinate(0, -1, 0, 100, 256, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () ->
				LayerPosition.coordinate(0, 1, 0, 100, 256, 9, 0, 0));
		assertThrows(IllegalArgumentException.class, () ->
				LayerPosition.clip(0, 0, 1, -1, 100, 100, scratch));
		PinnedPlanes p = new PinnedPlanes(400, 300, 20, 50);
		long before = p.generation();
		assertThrows(IllegalArgumentException.class, () -> p.configure(400, 300, 301, 50));
		assertEquals(before, p.generation());
		assertTrue(p.clip(PinnedPlanes.HEADER, scratch));
		assertArrayEquals(new int[] {50, 0, 350, 20}, scratch);
	}
}
