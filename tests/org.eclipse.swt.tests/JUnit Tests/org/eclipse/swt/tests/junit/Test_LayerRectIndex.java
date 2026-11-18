/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.eclipse.swt.internal.LayerRectIndex;
import org.junit.jupiter.api.Test;

/** Headless differential test for half-open, long-coordinate Z-stack queries. */
public class Test_LayerRectIndex {
	private static final class Rects implements LayerRectIndex.Bounds {
		final long[] x, y, right, bottom;
		final boolean[] active;
		Rects(int size) {
			x = new long[size];
			y = new long[size];
			right = new long[size];
			bottom = new long[size];
			active = new boolean[size];
		}
		@Override public int size() { return x.length; }
		@Override public boolean active(int i) { return active[i]; }
		@Override public long left(int i) { return x[i]; }
		@Override public long top(int i) { return y[i]; }
		@Override public long right(int i) { return right[i]; }
		@Override public long bottom(int i) { return bottom[i]; }
	}

	@Test
	public void randomizedAgainstBruteForce() {
		Random random = new Random(18431L);
		for (int size : new int[] {0, 1, 2, 12, 100, 2048, 10000}) {
			Rects model = new Rects(size);
			for (int i = 0; i < size; i++) {
				long x = random.nextBoolean() ? random.nextInt(20000) - 10000
						: Long.MAX_VALUE - 50000;
				long y = random.nextBoolean() ? random.nextInt(20000) - 10000
						: Long.MIN_VALUE + 10000;
				model.x[i] = x;
				model.y[i] = y;
				model.right[i] = x + random.nextInt(100);
				model.bottom[i] = y + random.nextInt(100);
				model.active[i] = random.nextInt(4) != 0;
			}
			LayerRectIndex index = new LayerRectIndex();
			index.rebuild(model);
			for (int q = 0; q < (size > 5000 ? 150 : 500); q++) {
				long x0 = random.nextBoolean() ? random.nextInt(20000) - 10000
						: Long.MAX_VALUE - 50000;
				long y0 = random.nextBoolean() ? random.nextInt(20000) - 10000
						: Long.MIN_VALUE + 10000;
				long x1 = x0 + random.nextInt(500);
				long y1 = y0 + random.nextInt(500);
				int found = index.query(x0, y0, x1, y1);
				int expected = 0;
				if (x0 < x1 && y0 < y1) {
					for (int i = 0; i < size; i++) {
						if (model.active[i] && model.x[i] < model.right[i]
								&& model.y[i] < model.bottom[i] && model.x[i] < x1
								&& model.right[i] > x0 && model.y[i] < y1
								&& model.bottom[i] > y0) {
							assertTrue(expected < found, "missing source " + i);
							assertEquals(i, index.resultAt(expected++),
									"size " + size + " query " + q);
						}
					}
				}
				assertEquals(expected, found, "size " + size + " query " + q);
			}
			long before = index.generation();
			index.clear();
			assertEquals(0, index.indexedCount());
			assertTrue(index.generation() > before);
		}
	}

	@Test
	public void clipBoundariesRebuildAndInputValidation() {
		Rects model = new Rects(3);
		model.x[0] = -10; model.y[0] = -10;
		model.right[0] = 10; model.bottom[0] = 10;
		model.x[1] = 10; model.y[1] = 10;
		model.right[1] = 20; model.bottom[1] = 20;
		model.x[2] = 0; model.y[2] = 0;
		model.right[2] = 0; model.bottom[2] = 20;
		model.active[0] = model.active[1] = model.active[2] = true;
		LayerRectIndex index = new LayerRectIndex();
		index.rebuild(model);
		assertEquals(2, index.indexedCount());
		assertEquals(1, index.query(9, 9, 10, 10));
		assertEquals(0, index.resultAt(0));
		assertEquals(1, index.query(10, 10, 11, 11));
		assertEquals(1, index.resultAt(0));
		assertEquals(0, index.query(10, 9, 10, 11));
		assertThrows(IndexOutOfBoundsException.class, () -> index.resultAt(0));

		model.active[1] = false;
		index.rebuild(model);
		assertEquals(0, index.query(10, 10, 11, 11));
		assertEquals(1, index.indexedCount());
		assertThrows(IllegalArgumentException.class, () -> index.rebuild(null));
		model.right[0] = -11;
		assertThrows(IllegalArgumentException.class, () -> index.rebuild(model));
	}
}
