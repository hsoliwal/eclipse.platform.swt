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
		for (Mode mode : Mode.values()) {
			int[] destination = {0xFF0000FF, 0xFF808080, 0x00000000};
			int[] source = {0xFFFF0000, 0xFFFFFFFF, 0x00000000};
			LayerArgbCompositor.composite(destination, 0, source, 0, 3, 255, mode);
			int expected = switch (mode) {
				case SRC_OVER -> 0xFFFF0000;
				case MULTIPLY -> 0xFF000000;
				case SCREEN -> 0xFFFF00FF;
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
}
