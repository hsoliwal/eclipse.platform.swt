/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.custom;

/**
 * Primitive, allocation-free viewport admission for optional Maya native
 * image surfaces. Coordinates are projected SWT client pixels, not OS handles.
 * The disabled (-1) policy preserves historical native layer membership.
 */
final class NativeResidencyWindow {
    static final int DISABLED = -1;
    static final int MAX_OVERSCAN = 1 << 20;

    private NativeResidencyWindow() { }

    static boolean admits(int overscanPixels, boolean currentlyNative,
            int clientX, int clientY, int clientWidth, int clientHeight,
            double left, double top, double right, double bottom,
            boolean visible) {
        if (overscanPixels == DISABLED) return true;
        if (!visible || overscanPixels < 0 || overscanPixels > MAX_OVERSCAN
                || clientWidth <= 0 || clientHeight <= 0
                || !Double.isFinite(left) || !Double.isFinite(top)
                || !Double.isFinite(right) || !Double.isFinite(bottom)
                || !(left < right && top < bottom)) return false;
        // Enter inside one overscan radius; release only after leaving two.
        // This suppresses repeated COM CreateSurface/retire at a scroll edge.
        double margin = currentlyNative ? 2.0 * overscanPixels : overscanPixels;
        double x0 = (double) clientX - margin;
        double y0 = (double) clientY - margin;
        double x1 = (double) clientX + clientWidth + margin;
        double y1 = (double) clientY + clientHeight + margin;
        return left < x1 && right > x0 && top < y1 && bottom > y0;
    }
}
