/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.custom;

import java.util.Random;

/** Executable independent interval-overlap oracle, without SWT or an OS display. */
public final class NativeResidencyWindowProof {
    private NativeResidencyWindowProof() { }

    private static void verify(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static boolean oracle(int margin, boolean resident, int x, int y,
            int width, int height, double left, double top, double right,
            double bottom, boolean visible) {
        if (margin == -1) return true;
        if (!visible || margin < 0 || margin > (1 << 20) || width <= 0 || height <= 0
                || !Double.isFinite(left) || !Double.isFinite(top)
                || !Double.isFinite(right) || !Double.isFinite(bottom)
                || left >= right || top >= bottom) return false;
        double padding = margin * (resident ? 2.0 : 1.0);
        double bx0 = x - padding, by0 = y - padding;
        double bx1 = (double) x + width + padding, by1 = (double) y + height + padding;
        // Construct the independent positive-area intersection rather than
        // rely on integer area casts that would overflow at extreme origins.
        return Math.max(left, bx0) < Math.min(right, bx1)
                && Math.max(top, by0) < Math.min(bottom, by1);
    }

    public static void main(String[] args) {
        verify(NativeResidencyWindow.admits(-1, false, 0, 0, 0, 0,
                Double.NaN, 0, 0, 0, false), "disabled preserves legacy semantics");
        verify(!NativeResidencyWindow.admits(0, false, 0, 0, 300, 200,
                300, 10, 310, 20, true), "touching the edge is not resident");
        verify(!NativeResidencyWindow.admits(12, false, 0, 0, 300, 200,
                320, 10, 330, 20, true), "20-pixel distant surface not allocated");
        verify(NativeResidencyWindow.admits(12, true, 0, 0, 300, 200,
                320, 10, 330, 20, true), "hysteresis keeps recently visible surface");
        verify(!NativeResidencyWindow.admits(12, true, 0, 0, 300, 200,
                325, 10, 335, 20, true), "evicts past the 24px release radius");
        verify(!NativeResidencyWindow.admits(12, false, 0, 0, 300, 200,
                -100, -100, -10, -10, false), "hidden source is never admitted");
        verify(!NativeResidencyWindow.admits(12, false, 0, 0, 300, 200,
                Double.POSITIVE_INFINITY, 1, Double.POSITIVE_INFINITY, 20, true),
                "overflow/invalid projections fail closed");
        verify(NativeResidencyWindow.admits(0, false, Integer.MAX_VALUE - 20, 0, 30, 50,
                (double) Integer.MAX_VALUE - 10, 2, (double) Integer.MAX_VALUE + 5, 20, true),
                "client xmax cannot overflow a signed int");
        verify(NativeResidencyWindow.admits(1 << 20, true, Integer.MIN_VALUE, 0, 200, 100,
                (double) Integer.MIN_VALUE - 1500000, 10,
                (double) Integer.MIN_VALUE - 500000, 30, true),
                "large overscan avoids signed 32-bit underflow");
        Random random = new Random(0x4d41594120261121L);
        for (int i = 0; i < 200_000; i++) {
            int x = random.nextInt(2001) - 1000;
            int y = random.nextInt(2001) - 1000;
            int width = random.nextInt(500);
            int height = random.nextInt(500);
            int margin = random.nextInt(201) - 1;
            boolean nativeNow = random.nextBoolean(), visible = random.nextBoolean();
            double left = random.nextInt(2401) - 1200 + random.nextDouble();
            double top = random.nextInt(2401) - 1200 + random.nextDouble();
            double right = left + random.nextInt(700);
            double bottom = top + random.nextInt(700);
            boolean result = NativeResidencyWindow.admits(margin, nativeNow,
                    x, y, width, height, left, top, right, bottom, visible);
            verify(result == oracle(margin, nativeNow,
                    x, y, width, height, left, top, right, bottom, visible),
                    "interval oracle drift on case " + i);
        }
        System.out.println("NativeResidencyWindowProof PASS: 200000 deterministic interval cases");
    }
}
