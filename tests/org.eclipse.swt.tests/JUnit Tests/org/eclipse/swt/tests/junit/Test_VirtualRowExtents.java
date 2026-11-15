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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Headless differential contracts for variable row extents: every query is compared with a
 * naive linear-scan oracle written from the documented semantics. No native widget fixtures.
 */
public class Test_VirtualRowExtents {
	private static final int AUTO = 0, START = 1, CENTER = 2, END = 3, SMART = 4;

	/** Reflective handle on the package-private owner. */
	private static final class Extents {
		final Object target;

		Extents(int estimate) {
			try {
				Constructor<?> c = Class.forName("org.eclipse.swt.widgets.VirtualRowExtents").getDeclaredConstructor(int.class);
				c.setAccessible(true);
				target = c.newInstance(estimate);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException r) throw r;
				throw new IllegalStateException(e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}

		Object call(String name, Class<?>[] types, Object... args) {
			try {
				Method m = target.getClass().getDeclaredMethod(name, types);
				m.setAccessible(true);
				return m.invoke(target, args);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException r) throw r;
				throw new IllegalStateException(e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}

		int rows() { return (Integer) call("rowCount", new Class<?>[0]); }
		int measured() { return (Integer) call("measuredCount", new Class<?>[0]); }
		int extentOf(int r) { return (Integer) call("extentOf", new Class<?>[] {int.class}, r); }
		boolean isMeasured(int r) { return (Boolean) call("isMeasured", new Class<?>[] {int.class}, r); }
		void setEstimate(int e) { call("setEstimate", new Class<?>[] {int.class}, e); }
		void reset(int n) { call("reset", new Class<?>[] {int.class}, n); }
		void setExtent(int r, int e) { call("setExtent", new Class<?>[] {int.class, int.class}, r, e); }
		void clearExtent(int r) { call("clearExtent", new Class<?>[] {int.class}, r); }
		void clearFrom(int r) { call("clearFrom", new Class<?>[] {int.class}, r); }
		void insert(int i, int n) { call("insert", new Class<?>[] {int.class, int.class}, i, n); }
		void remove(int i, int n) { call("remove", new Class<?>[] {int.class, int.class}, i, n); }
		long offsetOf(int r) { return (Long) call("offsetOf", new Class<?>[] {int.class}, r); }
		long total() { return (Long) call("totalExtent", new Class<?>[0]); }
		int rowAt(long o) { return (Integer) call("rowAt", new Class<?>[] {long.class}, o); }
		int[] visible(long o, int v) {
			int[] out = new int[2];
			call("visibleRange", new Class<?>[] {long.class, int.class, int[].class}, o, v, out);
			return out;
		}
		long scrollFor(int r, int align, long cur, int v) {
			return (Long) call("scrollOffsetFor", new Class<?>[] {int.class, int.class, long.class, int.class}, r, align, cur, v);
		}
		long anchored(int r, int e, long off, boolean back) {
			return (Long) call("setExtentAnchored", new Class<?>[] {int.class, int.class, long.class, boolean.class}, r, e, off, back);
		}
	}

	/** Naive oracle over plain arrays. */
	private static final class Oracle {
		int estimate;
		int[] measured = new int[0]; // -1 = unmeasured

		Oracle(int estimate) { this.estimate = estimate; }

		int count() { return measured.length; }
		int eff(int r) { return measured[r] < 0 ? estimate : measured[r]; }
		long offset(int r) { long s = 0; for (int i = 0; i < r; i++) s += eff(i); return s; }
		long total() { return offset(count()); }
		int measuredCount() { int n = 0; for (int m : measured) if (m >= 0) n++; return n; }

		int rowAt(long off) {
			if (count() == 0) return -1;
			if (off <= 0) return 0;
			long s = 0;
			for (int i = 0; i < count(); i++) {
				s += eff(i);
				if (off < s) return i;
			}
			return count() - 1;
		}

		void insert(int index, int n) {
			int[] next = new int[count() + n];
			System.arraycopy(measured, 0, next, 0, index);
			Arrays.fill(next, index, index + n, -1);
			System.arraycopy(measured, index, next, index + n, count() - index);
			measured = next;
		}

		void remove(int index, int n) {
			int[] next = new int[count() - n];
			System.arraycopy(measured, 0, next, 0, index);
			System.arraycopy(measured, index + n, next, index, count() - index - n);
			measured = next;
		}

		long clamp(long off, int viewport) { return Math.max(0, Math.min(off, Math.max(0, total() - viewport))); }

		long scrollFor(int r, int align, long cur, int v) {
			long s = offset(r);
			long e = s + eff(r);
			long t;
			switch (align) {
				case START -> t = s;
				case END -> t = e - v;
				case CENTER -> t = s + Math.floorDiv(eff(r) - v, 2);
				case SMART -> {
					boolean revealed = cur >= Math.min(s, e - v) && cur <= Math.max(s, e - v);
					t = revealed ? cur : s + Math.floorDiv(eff(r) - v, 2);
				}
				default -> t = cur < Math.min(s, e - v) ? Math.min(s, e - v) : Math.min(cur, Math.max(s, e - v));
			}
			return clamp(t, v);
		}
	}

	private static void same(Extents x, Oracle o, Random rnd, String ctx) {
		assertEquals(o.count(), x.rows(), ctx);
		assertEquals(o.measuredCount(), x.measured(), ctx);
		assertEquals(o.total(), x.total(), ctx);
		int n = o.count();
		int step = Math.max(1, n / 40);
		for (int r = 0; r <= n; r += step) assertEquals(o.offset(r), x.offsetOf(r), ctx + " offsetOf " + r);
		for (int r = 0; r < n; r += step) {
			assertEquals(o.eff(r), x.extentOf(r), ctx + " extentOf " + r);
			assertEquals(o.measured[r] >= 0, x.isMeasured(r), ctx + " isMeasured " + r);
		}
		long total = o.total();
		long[] probes = {-5, 0, 1, total - 1, total, total + 7, rnd.nextInt((int) Math.max(1, total + 5))};
		for (long p : probes) assertEquals(o.rowAt(p), x.rowAt(p), ctx + " rowAt " + p);
		int viewport = rnd.nextInt(60);
		long off = rnd.nextInt((int) Math.max(1, total + 5)) - 3;
		int[] range = x.visible(off, viewport);
		if (n == 0) {
			assertArrayEquals(new int[] {0, 0}, range, ctx);
		} else {
			int first = o.rowAt(off);
			int end = viewport == 0 ? first : o.rowAt(off + viewport - 1) + 1;
			assertArrayEquals(new int[] {first, end}, range, ctx + " visible");
		}
		if (n > 0) {
			int row = rnd.nextInt(n);
			long cur = rnd.nextInt((int) Math.max(1, total)) ;
			for (int align : new int[] {AUTO, START, CENTER, END, SMART}) {
				assertEquals(o.scrollFor(row, align, cur, viewport), x.scrollFor(row, align, cur, viewport), ctx + " scroll align " + align);
			}
		}
	}

	private static void apply(Extents x, Oracle o, Random rnd) {
		int n = o.count();
		int pick = rnd.nextInt(100);
		if (pick < 35 && n > 0) {
			int r = rnd.nextInt(n);
			int e = rnd.nextInt(5) == 0 ? 0 : rnd.nextInt(40);
			x.setExtent(r, e);
			o.measured[r] = e;
		} else if (pick < 45 && n > 0) {
			int r = rnd.nextInt(n);
			x.clearExtent(r);
			o.measured[r] = -1;
		} else if (pick < 50) {
			int r = rnd.nextInt(n + 1);
			x.clearFrom(r);
			for (int i = r; i < n; i++) o.measured[i] = -1;
		} else if (pick < 66) {
			int i = rnd.nextInt(n + 1);
			int k = rnd.nextInt(6);
			x.insert(i, k);
			o.insert(i, k);
		} else if (pick < 80 && n > 0) {
			int i = rnd.nextInt(n);
			int k = rnd.nextInt(Math.min(6, n - i) + 1);
			x.remove(i, k);
			o.remove(i, k);
		} else if (pick < 84) {
			int e = 1 + rnd.nextInt(30);
			x.setEstimate(e);
			o.estimate = e;
		} else if (pick < 86) {
			int k = rnd.nextInt(50);
			x.reset(k);
			o.measured = new int[k];
			Arrays.fill(o.measured, -1);
		} else if (n > 0) {
			int r = rnd.nextInt(n);
			int e = rnd.nextInt(40);
			long off = rnd.nextInt((int) Math.max(1, o.total() + 5));
			boolean back = rnd.nextBoolean();
			boolean first = o.measured[r] < 0;
			long start = o.offset(r);
			long end = start + o.eff(r);
			long delta = (long) e - o.eff(r);
			boolean above = first ? start < off : end <= off && !back;
			long expected = (delta != 0 && above) ? delta : 0;
			assertEquals(expected, x.anchored(r, e, off, back), "anchor");
			o.measured[r] = e;
		}
	}

	@Test
	public void randomOperationsAgreeWithLinearScanOracle() {
		for (int seed = 1; seed <= 60; seed++) {
			Random rnd = new Random(seed);
			int estimate = 1 + rnd.nextInt(20);
			Extents x = new Extents(estimate);
			Oracle o = new Oracle(estimate);
			x.reset(rnd.nextInt(40));
			o.measured = new int[x.rows()];
			Arrays.fill(o.measured, -1);
			for (int step = 0; step < 400; step++) {
				apply(x, o, rnd);
				same(x, o, rnd, "seed=" + seed + " step=" + step);
			}
		}
	}

	@Test
	public void invalidArgumentsAreRejectedWithoutChangingState() {
		Extents x = new Extents(10);
		x.reset(5);
		assertThrows(IllegalArgumentException.class, () -> new Extents(0));
		assertThrows(IllegalArgumentException.class, () -> x.setEstimate(0));
		assertThrows(IllegalArgumentException.class, () -> x.reset(-1));
		assertThrows(IllegalArgumentException.class, () -> x.setExtent(5, 1));
		assertThrows(IllegalArgumentException.class, () -> x.setExtent(-1, 1));
		assertThrows(IllegalArgumentException.class, () -> x.setExtent(0, -2));
		assertThrows(IllegalArgumentException.class, () -> x.insert(6, 1));
		assertThrows(IllegalArgumentException.class, () -> x.insert(0, -1));
		assertThrows(IllegalArgumentException.class, () -> x.remove(3, 3));
		assertThrows(IllegalArgumentException.class, () -> x.clearFrom(6));
		assertThrows(IllegalArgumentException.class, () -> x.offsetOf(6));
		assertThrows(IllegalArgumentException.class, () -> x.visible(0, -1));
		assertThrows(IllegalArgumentException.class, () -> x.scrollFor(0, 9, 0, 10));
		assertEquals(5, x.rows());
		assertEquals(50, x.total());
		assertEquals(-1, new Extents(3).rowAt(10));
	}

	@Test
	public void measurementReplacesTheEstimateAndAnchoringFollowsTheDonorRules() {
		Extents x = new Extents(10);
		x.reset(100); // total 1000
		assertEquals(1000, x.total());
		// first measurement of a row that starts above the offset: whole change is returned
		assertEquals(30, x.anchored(2, 40, 500, false));
		assertEquals(1030, x.total());
		// re-measure of a row entirely above the offset, scrolling forward: returned
		assertEquals(-15, x.anchored(2, 25, 500, false));
		// the same re-measure while scrolling backward: not returned, size still recorded
		assertEquals(0, x.anchored(2, 35, 500, true));
		assertEquals(35, x.extentOf(2));
		// a row spanning the offset is not compensated on re-measure
		long start = x.offsetOf(2);
		assertEquals(0, x.anchored(2, 45, start + 5, false));
		// first measurement of a row below the offset: nothing to compensate
		assertEquals(0, x.anchored(50, 70, 100, false));
	}

	@Test
	public void alignmentChoosesTheSmallestMoveForAuto() {
		Extents x = new Extents(10);
		x.reset(100);
		assertEquals(500, x.scrollFor(50, AUTO, 500, 100)); // fully visible: stay
		assertEquals(500, x.scrollFor(50, AUTO, 800, 100)); // above: start
		assertEquals(410, x.scrollFor(50, AUTO, 100, 100)); // below: end
		assertEquals(500, x.scrollFor(50, START, 0, 100));
		assertEquals(410, x.scrollFor(50, END, 0, 100));
		assertEquals(455, x.scrollFor(50, CENTER, 0, 100));
		assertEquals(900, x.scrollFor(99, START, 0, 100)); // clamped to the last full page
		assertEquals(0, x.scrollFor(0, END, 0, 100));
	}

	@Test
	public void oversizedRowsAndSmartAlignmentFollowTheDonorRules() {
		Extents x = new Extents(10);
		x.reset(20);
		x.setExtent(5, 300); // row 5 spans [50, 350), viewport 100
		// oversized row already filling the viewport: AUTO leaves the offset alone
		assertEquals(120, x.scrollFor(5, AUTO, 120, 100));
		assertEquals(250, x.scrollFor(5, AUTO, 250, 100));
		// above its first revealed offset: show its top; below its last: show its bottom
		assertEquals(50, x.scrollFor(5, AUTO, 0, 100));
		assertEquals(250, x.scrollFor(5, AUTO, 300, 100));
		// SMART: revealed keeps the offset, otherwise centers: 50 + (300 - 100) / 2 = 150
		assertEquals(120, x.scrollFor(5, SMART, 120, 100));
		assertEquals(150, x.scrollFor(5, SMART, 0, 100));
		// ordinary rows: row 0 is revealed at offset 0; row 10 (start 390, 10 high) is hidden and centered at 390 - 45
		assertEquals(0, x.scrollFor(0, SMART, 0, 100));
		assertEquals(345, x.scrollFor(10, SMART, 0, 100));
	}

	@Test
	public void largeListStaysConsistentWithBinarySearchOnPrefixSums() {
		int rows = 1_000_000;
		Random rnd = new Random(42);
		Extents x = new Extents(20);
		x.reset(rows);
		int[] eff = new int[rows];
		Arrays.fill(eff, 20);
		for (int i = 0; i < 200_000; i++) {
			int r = rnd.nextInt(rows);
			int e = rnd.nextInt(80);
			x.setExtent(r, e);
			eff[r] = e;
		}
		long[] prefix = new long[rows + 1];
		for (int i = 0; i < rows; i++) prefix[i + 1] = prefix[i] + eff[i];
		assertEquals(prefix[rows], x.total());
		for (int i = 0; i < 2000; i++) {
			int r = rnd.nextInt(rows + 1);
			assertEquals(prefix[r], x.offsetOf(r));
			long off = (long) (rnd.nextDouble() * prefix[rows]);
			int expected = Arrays.binarySearch(prefix, off);
			if (expected < 0) expected = -expected - 2;
			while (expected + 1 < rows && prefix[expected + 1] <= off) expected++;
			assertEquals(Math.min(expected, rows - 1), x.rowAt(off));
		}
		x.remove(0, rows / 2);
		assertEquals(rows / 2, x.rows());
		assertEquals(prefix[rows] - prefix[rows / 2], x.total());
	}

	@Test
	public void consecutiveMutationsAgreeWithOracleBeforeAndAfterQueries() {
		Random random = new Random(0x5157524f57L);
		Extents x = new Extents(20);
		Oracle oracle = new Oracle(20);
		x.reset(512);
		oracle.measured = new int[512];
		Arrays.fill(oracle.measured, -1);
		// First measurements arrive out of order, followed by remeasurements and
		// an append before the first offset query. No batch API is required.
		for (int row = 500; row >= 0; row -= 3) {
			x.setExtent(row, row % 41);
			oracle.measured[row] = row % 41;
		}
		x.setExtent(200, 0);
		oracle.measured[200] = 0;
		x.setExtent(511, 73);
		oracle.measured[511] = 73;
		same(x, oracle, random, "unqueried middle insertions and remeasurement");
		for (int round = 0; round < 80; round++) {
			// Includes clear/remove/reset/estimate changes and anchored queries;
			// mutations may be observed immediately or after several more changes.
			for (int operation = 0; operation < 31; operation++) apply(x, oracle, random);
			same(x, oracle, random, "mutation group " + round);
		}
	}

	@Test
	public void maximumLogicalCountRetainsSparseMeasurementBacking() throws Exception {
		int rows = Integer.MAX_VALUE;
		Extents x = new Extents(20);
		x.reset(rows);
		x.setExtent(rows - 1, 40);
		x.setExtent(rows / 2, 10);
		x.setExtent(0, 0);
		assertEquals(3, x.measured());
		assertEquals(20L * rows - 10, x.total());
		assertEquals(rows - 1, x.rowAt(x.total() - 1));
		long backingBytes = 0;
		for (java.lang.reflect.Field field : x.target.getClass().getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
			field.setAccessible(true);
			Object backing = field.get(x.target);
			if (backing instanceof int[] lane) backingBytes += 4L * lane.length;
			if (backing instanceof long[] lane) backingBytes += 8L * lane.length;
		}
		assertTrue(backingBytes < 4096, "three measurements allocated " + backingBytes + " bytes");
		x.remove(rows - 4, 4);
		assertEquals(2, x.measured());
		assertEquals(20L * (rows - 4) - 30, x.total());
		x.reset(rows);
		assertEquals(0, x.measured());
		assertEquals(20L * rows, x.total());
	}

	@Test
	public void visibleRangeClampsExtremeOffsetsWithoutWrapping() {
		Extents x = new Extents(10);
		x.reset(10);
		assertArrayEquals(new int[] {9, 10}, x.visible(Long.MAX_VALUE, 2));
		assertArrayEquals(new int[] {9, 10}, x.visible(Long.MAX_VALUE - 1, Integer.MAX_VALUE));
		assertArrayEquals(new int[] {9, 10}, x.visible(Long.MAX_VALUE, 1));
		assertArrayEquals(new int[] {9, 9}, x.visible(Long.MAX_VALUE, 0));
		assertArrayEquals(new int[] {0, 1}, x.visible(Long.MIN_VALUE, Integer.MAX_VALUE));
		assertArrayEquals(new int[] {0, 0}, x.visible(Long.MIN_VALUE, 0));
		assertArrayEquals(new int[] {0, 10}, x.visible(-5, 105));
		org.eclipse.swt.internal.ItemRange range = (org.eclipse.swt.internal.ItemRange)
				x.call("visibleRange", new Class<?>[] {long.class, int.class}, Long.MAX_VALUE, 2);
		assertEquals(9, range.start());
		assertEquals(9, range.end());
		assertFalse(range.isEmpty());
		x.reset(0);
		assertArrayEquals(new int[] {0, 0}, x.visible(Long.MAX_VALUE, Integer.MAX_VALUE));
	}

	@Test
	public void clearingAndRemovingMeasurementBatchesAgreeWithDenseOracle() {
		for (int seed = 0; seed < 40; seed++) {
			Random random = new Random(0x43524541L + seed);
			Extents x = new Extents(20);
			Oracle oracle = new Oracle(20);
			x.reset(128);
			oracle.measured = new int[128];
			Arrays.fill(oracle.measured, -1);
			for (int group = 0; group < 80; group++) {
				// Deliberately observe several mutations together. Both clean-prefix
				// truncations and middle edits must survive subsequent remeasurement.
				for (int operation = 0; operation < 16; operation++) {
					int n = oracle.count();
					if (n < 4) {
						x.insert(n, 64);
						oracle.insert(n, 64);
						n = oracle.count();
					}
					switch (random.nextInt(8)) {
						case 0 -> {
							int row = n - 1;
							while (row > 0 && oracle.measured[row] < 0) row--;
							x.clearExtent(row);
							oracle.measured[row] = -1;
						}
						case 1 -> {
							int row = random.nextInt(n + 1);
							x.clearFrom(row);
							Arrays.fill(oracle.measured, row, n, -1);
						}
						case 2, 3 -> {
							int row = random.nextInt(n);
							int length = random.nextBoolean() ? n - row : random.nextInt(n - row + 1);
							x.remove(row, length);
							oracle.remove(row, length);
						}
						case 4 -> {
							int row = random.nextInt(n);
							x.clearExtent(row);
							oracle.measured[row] = -1;
						}
						case 5 -> {
							int index = random.nextInt(n + 1);
							x.insert(index, 3);
							oracle.insert(index, 3);
						}
						default -> {
							int row = random.nextInt(n), extent = random.nextInt(80);
							x.setExtent(row, extent);
							oracle.measured[row] = extent;
						}
					}
				}
				same(x, oracle, random, "clear sequence seed=" + seed + " group=" + group);
			}
		}
	}

	@Test
	public void suffixRemovalRetainsValuesAcrossRemeasurementAndAppend() {
		for (int operation = 0; operation < 3; operation++) {
			for (boolean pendingMiddleMeasurement : new boolean[] {false, true}) {
				Extents x = new Extents(7);
				Oracle oracle = new Oracle(7);
				x.reset(65);
				oracle.measured = new int[65];
				Arrays.fill(oracle.measured, -1);
				for (int row = 0; row < 65; row += 2) {
					x.setExtent(row, row % 17);
					oracle.measured[row] = row % 17;
				}
				assertEquals(oracle.total(), x.total());
				if (pendingMiddleMeasurement) {
					x.setExtent(1, 15);
					oracle.measured[1] = 15;
				}
				for (int row = 64; row >= 2; row -= 2) {
					if (operation == 0) x.clearFrom(row);
					else if (operation == 1) x.clearExtent(row);
					else x.remove(row, oracle.count() - row);
					if (operation == 2) oracle.remove(row, oracle.count() - row);
					else Arrays.fill(oracle.measured, row, oracle.count(), -1);
					x.setExtent(0, row);
					oracle.measured[0] = row;
					assertEquals(oracle.total(), x.total());
				}
				int n = oracle.count();
				x.insert(n, 3);
				oracle.insert(n, 3);
				x.setExtent(n + 2, 23);
				oracle.measured[n + 2] = 23;
				same(x, oracle, new Random(42), "suffix operation=" + operation);
				x.clearFrom(0);
				Arrays.fill(oracle.measured, -1);
				x.setExtent(0, 11);
				oracle.measured[0] = 11;
				same(x, oracle, new Random(43), "reuse after clearing every measurement");
			}
		}
	}
}
