/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

/** Standalone Java 21 exact-source CPU-native rectangle admission oracle. */
public final class NativeDirtyRegionsProof {
    private NativeDirtyRegionsProof() { }

    private static long state = 0x20261118L;
    private static long checks;

    private static int next(int bound) {
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;
        return (int) Long.remainderUnsigned(state, bound);
    }

    private static void equal(int expected, int actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + ", got " + actual);
        checks++;
    }

    private static void invalid(int width, int height, int[] r, int count) {
        try {
            NativeDirtyRegions.packedPixelCount(width, height, r, count);
            throw new AssertionError("accepted invalid native image region");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void main(String[] unused) {
        for (int trial = 0; trial < 20_000; trial++) {
            int width = 1 + next(127), height = 1 + next(83);
            int n = next(17);
            int[] rects = new int[n * 4 + 4]; // one spare rectangle
            long expected = 0;
            for (int i = 0; i < n; i++) {
                int x = next(width), y = next(height);
                int w = 1 + next(width - x), h = 1 + next(height - y);
                int offset = i * 4;
                rects[offset] = x;
                rects[offset + 1] = y;
                rects[offset + 2] = w;
                rects[offset + 3] = h;
                expected += (long) w * h;
                equal((int) (w * (long) h), w * h); // fits the small model
            }
            equal((int) expected, NativeDirtyRegions.packedPixelCount(width, height, rects, n));
            int[] invalid = rects.clone();
            invalid[n * 4] = width;
            invalid[n * 4 + 1] = 0;
            invalid[n * 4 + 2] = 1;
            invalid[n * 4 + 3] = 1;
            invalid(width, height, invalid, n + 1);
            invalid(width, height, rects, -1);
        }
        equal(0, NativeDirtyRegions.packedPixelCount(20, 20, new int[0], 0));
        invalid(1, 1, null, 0);
        invalid(1, 1, new int[0], 1);
        invalid(1, 1, new int[0], 4097);
        invalid(0, 1, new int[0], 0);
        invalid(1, 0, new int[0], 0);
        invalid(Integer.MAX_VALUE, Integer.MAX_VALUE, new int[0], 0);
        invalid(1, 1, new int[] {0, 0, 0, 1}, 1);
        invalid(1, 1, new int[] {0, 0, 1, -1}, 1);
        invalid(1, 1, new int[] {-1, 0, 1, 1}, 1);
        invalid(1, 1, new int[] {0, -1, 1, 1}, 1);
        invalid(1, 1, new int[] {0, 0, 2, 1}, 1);
        invalid(1, 1, new int[] {0, 0, 1, 2}, 1);
        // JNI's D3D11 UINT row pitch is bytes, not pixels: 4*(2^30) is
        // one byte above the representable unsigned 32-bit maximum.
        invalid(1_073_741_824, 1, new int[] {0, 0, 1_073_741_824, 1}, 1);
        equal(1_073_741_823,
                NativeDirtyRegions.packedPixelCount(1_073_741_823, 1,
                        new int[] {0, 0, 1_073_741_823, 1}, 1));
        invalid(100_000, 20_000, new int[] {0, 0, 100_000, 20_000}, 2);
        int[] large = new int[8];
        large[2] = large[6] = 46_340;
        large[3] = large[7] = 46_340;
        invalid(46_340, 46_340, large, 2); // overlapping total > Integer.MAX_VALUE
        equal(1, NativeDirtyRegions.packedPixelCount(1, 1, new int[] {0, 0, 1, 1}, 1));
        System.out.println("PASS " + checks + " exact Java21 NativeDirtyRegions validations");
    }
}
