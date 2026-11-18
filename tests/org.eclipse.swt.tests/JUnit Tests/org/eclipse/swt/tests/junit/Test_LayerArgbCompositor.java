/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

import org.eclipse.swt.internal.render.LayerArgbCompositor;
import org.eclipse.swt.internal.render.LayerArgbCompositor.Mode;
import org.junit.jupiter.api.Test;

/** Headless premultiplied-ARGB reference and optional JNI-differential tests. */
public class Test_LayerArgbCompositor {
	private static int premulColor(Random random) {
		int alpha = random.nextInt(256);
		int red = random.nextInt(alpha + 1);
		int green = random.nextInt(alpha + 1);
		int blue = random.nextInt(alpha + 1);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	@Test
	public void opaqueColorsAndTransparentSource() {
		// the three modes of the integer kernel; the W3C modes added later have their own test below
		for (Mode mode : new Mode[] { Mode.SRC_OVER, Mode.MULTIPLY, Mode.SCREEN }) {
			int[] destination = {0xFF0000FF, 0xFF808080, 0x00000000};
			int[] source = {0xFFFF0000, 0xFFFFFFFF, 0x00000000};
			LayerArgbCompositor.composite(destination, 0, source, 0, 3, 255, mode);
			int expected = switch (mode) {
				case SRC_OVER -> 0xFFFF0000;
				case MULTIPLY -> 0xFF000000;
				case SCREEN -> 0xFFFF00FF;
				default -> throw new AssertionError(mode);
			};
			assertEquals(expected, destination[0], "mode " + mode);
			assertEquals(0x00000000, destination[2]);
		}
	}

	@Test
	public void aliasingDeterminismPremultiplicationAndBounds() {
		Random random = new Random(1897);
		int[] source = new int[32000], destination = new int[32000];
		for (int i = 0; i < source.length; i++) {
			source[i] = premulColor(random);
			destination[i] = premulColor(random);
		}
		for (Mode mode : Mode.values()) {
			for (int opacity : new int[]{0, 1, 17, 64, 128, 192, 254, 255}) {
				int[] actual = destination.clone();
				LayerArgbCompositor.composite(actual, 0, source, 0, actual.length,
						opacity, mode);
				for (int color : actual) {
					int alpha = color >>> 24;
					assertTrue(((color >>> 16) & 255) <= alpha);
					assertTrue(((color >>> 8) & 255) <= alpha);
					assertTrue((color & 255) <= alpha);
				}
				if (opacity == 0) assertArrayEquals(destination, actual);
				int[] alias = Arrays.copyOf(destination, 30);
				int[] expected = alias.clone();
				int[] sourceSnapshot = Arrays.copyOfRange(alias, 0, 20);
				LayerArgbCompositor.composite(expected, 5, sourceSnapshot, 0, 20,
						opacity, mode);
				LayerArgbCompositor.composite(alias, 5, alias, 0, 20,
						opacity, mode);
				assertArrayEquals(expected, alias);
				int[] second = destination.clone();
				LayerArgbCompositor.composite(second, 0, source, 0, second.length,
						opacity, mode);
				assertArrayEquals(actual, second);
			}
		}
		assertThrows(IndexOutOfBoundsException.class, () ->
				LayerArgbCompositor.composite(new int[2], 1, new int[2], 0,
						2, 255, Mode.SRC_OVER));
		assertThrows(IllegalArgumentException.class, () ->
				LayerArgbCompositor.composite(new int[2], 0, new int[2], 0,
						2, 256, Mode.SRC_OVER));
		assertThrows(NullPointerException.class, () ->
				LayerArgbCompositor.composite(new int[2], 0, new int[2], 0,
						2, 255, null));
	}

	@Test
	public void overlappingSourceAndDestinationInEitherDirectionMatchSnapshots() {
		Random random = new Random(0xA11A51A5L);
		for (Mode mode : Mode.values()) {
			for (int opacity : new int[] { 0, 1, 57, 128, 255 }) {
				for (int[] offsets : new int[][] { {0, 7}, {7, 0}, {3, 3}, {0, 35} }) {
					int[] input = new int[72];
					for (int i = 0; i < input.length; i++) input[i] = premulColor(random);
					int[] expected = input.clone(), actual = input.clone();
					int sourceAt = offsets[0], destinationAt = offsets[1], count = 30;
					int[] snapshot = Arrays.copyOfRange(expected, sourceAt, sourceAt + count);
					LayerArgbCompositor.composite(expected, destinationAt, snapshot, 0, count, opacity, mode);
					LayerArgbCompositor.composite(actual, destinationAt, actual, sourceAt, count, opacity, mode);
					assertArrayEquals(expected, actual, mode + " at opacity " + opacity + " offsets "
							+ sourceAt + " -> " + destinationAt);
				}
			}
		}
	}

	@Test
	public void optionalNativeBulkModesMatchJavaChunks() {
		// This always tests the Java result. With -Dswt.layerblend.native=true and a matched library,
		// the 16,384-pixel call also exercises JNI for every supported mode.
		Random random = new Random(0xB1E0D2026L);
		int length = 16_384;
		int[] source = new int[length], destination = new int[length];
		for (int i = 0; i < length; i++) {
			source[i] = premulColor(random);
			destination[i] = premulColor(random);
		}
		for (Mode mode : Mode.values()) {
			for (int opacity : new int[] { 1, 57, 128, 255 }) {
				int[] expected = destination.clone(), actual = destination.clone();
				for (int at = 0; at < length; at += 257) {
					int count = Math.min(257, length - at); // always below JNI threshold
					LayerArgbCompositor.composite(expected, at, source, at, count, opacity, mode);
				}
				LayerArgbCompositor.composite(actual, 0, source, 0, length, opacity, mode);
				assertArrayEquals(expected, actual, "bulk/chunk parity for " + mode + " opacity " + opacity);
			}
		}
	}

	/* one channel: a source channel with alpha (straight, 0 to 255) over an opaque backdrop channel, by the kernel */
	private static int channel(Mode mode, int backdrop, int source, int alpha) {
		int[] dst = { 0xFF000000 | backdrop };
		int premultiplied = (source * alpha + 127) / 255;
		int[] src = { alpha << 24 | premultiplied };
		LayerArgbCompositor.composite(dst, 0, src, 0, 1, 255, mode);
		assertEquals(255, dst[0] >>> 24, "an opaque backdrop stays opaque");
		return dst[0] & 255;
	}

	/* a pixel: an opaque source RGB over an opaque backdrop RGB */
	private static int pixel(Mode mode, int backdropRgb, int sourceRgb) {
		int[] dst = { 0xFF000000 | backdropRgb };
		LayerArgbCompositor.composite(dst, 0, new int[] { 0xFF000000 | sourceRgb }, 0, 1, 255, mode);
		return dst[0] & 0xFFFFFF;
	}

	@Test
	public void w3cSeparableModesOverAnOpaqueBackdrop() {
		// values worked out by hand from the W3C Compositing and Blending Level 1 formulas
		assertEquals(64, channel(Mode.MULTIPLY, 128, 128, 255), "0.502 x 0.502");
		assertEquals(192, channel(Mode.SCREEN, 128, 128, 255), "0.502 + 0.502 - 0.252");
		assertEquals(255, channel(Mode.OVERLAY, 255, 0, 255), "overlay keeps a white backdrop white");
		assertEquals(0, channel(Mode.OVERLAY, 0, 255, 255), "and a black one black");
		assertEquals(100, channel(Mode.DARKEN, 100, 200, 255));
		assertEquals(200, channel(Mode.LIGHTEN, 100, 200, 255));
		assertEquals(0, channel(Mode.COLOR_DODGE, 0, 200, 255), "a black backdrop stays black");
		assertEquals(255, channel(Mode.COLOR_DODGE, 51, 204, 255), "0.2 / (1 - 0.8)");
		assertEquals(85, channel(Mode.COLOR_DODGE, 51, 102, 255), "0.2 / 0.6");
		assertEquals(255, channel(Mode.COLOR_BURN, 255, 0, 255), "a white backdrop stays white");
		assertEquals(0, channel(Mode.COLOR_BURN, 200, 0, 255), "a black source");
		assertEquals(64, channel(Mode.COLOR_BURN, 102, 204, 255), "1 - 0.6 / 0.8");
		assertEquals(0, channel(Mode.HARD_LIGHT, 128, 0, 255), "multiplied by 2 x 0");
		assertEquals(255, channel(Mode.HARD_LIGHT, 128, 255, 255), "screened by 2 x 1 - 1");
		assertEquals(128, channel(Mode.SOFT_LIGHT, 64, 255, 255), "cb + (sqrt(cb) - cb)");
		assertEquals(16, channel(Mode.SOFT_LIGHT, 64, 0, 255), "cb - cb (1 - cb)");
		assertEquals(150, channel(Mode.DIFFERENCE, 200, 50, 255));
		assertEquals(150, channel(Mode.DIFFERENCE, 50, 200, 255));
		assertEquals(0, channel(Mode.EXCLUSION, 255, 255, 255), "1 + 1 - 2");
		assertEquals(127, channel(Mode.MULTIPLY, 255, 0, 128), "black at half over white");
		assertEquals(200, channel(Mode.DIFFERENCE, 200, 0, 0), "a transparent source leaves the backdrop");
		assertEquals(0x000000, pixel(Mode.MULTIPLY, 0x0000FF, 0xFF0000), "red x blue");
		assertEquals(0xFF00FF, pixel(Mode.SCREEN, 0x0000FF, 0xFF0000), "red screen blue");
		assertEquals(0xFFFF00, pixel(Mode.DIFFERENCE, 0x0000FF, 0xFFFFFF), "white difference blue");
		assertEquals(0x00FFFF, pixel(Mode.EXCLUSION, 0xFFFFFF, 0xFF0000), "red exclusion white");
	}

	@Test
	public void newModesKeepAlphaAndPremultiplication() {
		int[] dst = { 0x80402010 }; // a translucent premultiplied backdrop
		int[] src = { 0x40201008 };
		for (Mode mode : Mode.values()) {
			int[] out = dst.clone();
			LayerArgbCompositor.composite(out, 0, src, 0, 1, 255, mode);
			int a = out[0] >>> 24;
			assertEquals(0x40 + (0x80 * (255 - 0x40) + 127) / 255, a, mode + ": source-over alpha");
			assertTrue((out[0] >>> 16 & 255) <= a && (out[0] >>> 8 & 255) <= a && (out[0] & 255) <= a, mode + ": premultiplied");
		}
	}

	@Test
	public void overAnEmptyBackdropEveryModeIsTheSource() {
		for (Mode mode : Mode.values()) {
			int[] dst = { 0x00000000, 0x00000000 };
			LayerArgbCompositor.composite(dst, 0, new int[] { 0xFFFF0000, 0x80402000 }, 0, 2, 255, mode);
			assertEquals(0xFFFF0000, dst[0], mode + ": an opaque source over nothing");
			assertEquals(0x80402000, dst[1], mode + ": a translucent source over nothing");
		}
	}
}
