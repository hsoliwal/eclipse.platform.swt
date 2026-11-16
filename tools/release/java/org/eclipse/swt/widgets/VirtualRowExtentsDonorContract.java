/* SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.util.Arrays;
import java.util.Random;

/** Compiles against actual SWT VirtualRowExtents, ItemRange and PackedHeapGrowth. */
public final class VirtualRowExtentsDonorContract {
    private VirtualRowExtentsDonorContract() { }

    private static void check(boolean valid, String context) {
        if (!valid) throw new AssertionError(context);
    }

    private static int denseRowAt(int[] heights, long pixel) {
        if (heights.length == 0) return -1;
        if (pixel <= 0) return 0;
        long extent = 0;
        for (int row = 0; row < heights.length; row++) {
            extent += heights[row];
            if (pixel < extent) return row;
        }
        return heights.length - 1;
    }

    private static void fixedExamples() {
        VirtualRowExtents value = new VirtualRowExtents(10);
        value.reset(10);
        value.setExtent(2, 40);
        value.setExtent(5, 0);
        int[] out = new int[2];
        value.visibleRange(58, 12, 30, 35, out);
        check(Arrays.equals(out, new int[] {2, 9}), "asymmetric cache");
        value.visibleRange(58, 12, 0, 0, out);
        check(Arrays.equals(out, new int[] {2, 4}), "zero-cache compatibility");
        value.visibleRange(58, 0, 10, 10, out);
        check(Arrays.equals(out, new int[] {2, 2}), "zero viewport");
        value.visibleRange(Long.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, out);
        check(Arrays.equals(out, new int[] {9, 10}), "saturated end");
        boolean refused = false;
        try { value.visibleRange(1, -1, 1, 1, out); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "negative viewport refused");
    }

    private static void randomized() {
        Random random = new Random(20261116L);
        long checks = 0;
        for (int round = 0; round < 180; round++) {
            int count = random.nextInt(170), estimate = 1 + random.nextInt(25);
            VirtualRowExtents value = new VirtualRowExtents(estimate);
            value.reset(count);
            int[] dense = new int[count];
            Arrays.fill(dense, estimate);
            for (int row = 0; row < count; row++) {
                if (random.nextBoolean()) {
                    int extent = random.nextInt(75);
                    value.setExtent(row, extent);
                    dense[row] = extent;
                }
            }
            long total = 0;
            for (int n : dense) total += n;
            check(total == value.totalExtent(), "total extent");
            for (long pixel = -2; pixel <= total + 2; pixel++) {
                check(value.rowAt(pixel) == denseRowAt(dense, pixel), "rowAt dense");
                checks++;
            }
            for (int query = 0; query < 40; query++) {
                long off = random.nextInt((int)total + 9) - 4L;
                int viewport = random.nextInt(120), before = random.nextInt(80), after = random.nextInt(80);
                int[] out = new int[2];
                value.visibleRange(off, viewport, before, after, out);
                if (count == 0) {
                    check(Arrays.equals(out, new int[] {0, 0}), "empty");
                } else if (viewport == 0) {
                    int row = denseRowAt(dense, off);
                    check(Arrays.equals(out, new int[] {row, row}), "no invisible prefetch");
                } else {
                    long begin = off < before ? 0 : off - before;
                    long last = off + viewport - 1L + after;
                    check(Arrays.equals(out, new int[] {
                            denseRowAt(dense, begin), denseRowAt(dense, last) + 1}), "prefetch range");
                }
                checks++;
            }
        }
        System.out.println("SWT_ROW_DONOR_ORACLE checks=" + checks + " result=PASS");
    }

    private static void largeSparse() {
        int rows = 1_000_000;
        int[] heights = new int[rows];
        Arrays.fill(heights, 13);
        VirtualRowExtents value = new VirtualRowExtents(13);
        value.reset(rows);
        for (int row = 0; row < rows; row += 11) {
            int height = row % 77 == 0 ? 0 : row % 43 + 1;
            heights[row] = height;
            value.setExtent(row, height);
        }
        long[] ends = new long[rows + 1];
        for (int row = 0; row < rows; row++) ends[row + 1] = ends[row] + heights[row];
        check(value.totalExtent() == ends[rows], "million total");
        Random r = new Random(20261116L);
        for (int i = 0; i < 50_000; i++) {
            long pixel = r.nextInt((int) ends[rows] + 7) - 3L;
            int expected;
            if (pixel <= 0) expected = 0;
            else if (pixel >= ends[rows]) expected = rows - 1;
            else {
                int lo = 0, hi = rows;
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (ends[mid + 1] <= pixel) lo = mid + 1;
                    else hi = mid;
                }
                expected = lo;
            }
            check(value.rowAt(pixel) == expected, "million query " + i);
        }
        System.out.println("SWT_ROW_DONOR_LARGE rows=" + rows
                + " measured=" + value.measuredCount() + " checks=50000 result=PASS");
    }

    public static void main(String[] args) {
        fixedExamples();
        randomized();
        largeSparse();
        System.out.println("SWT_ROW_DONOR_CONTRACT result=PASS");
    }
}
