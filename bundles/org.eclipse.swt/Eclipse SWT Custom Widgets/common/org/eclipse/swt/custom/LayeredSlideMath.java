/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.custom;

/** Integer endpoints with a clamped, monotone smooth-step animation. */
final class LayeredSlideMath {
    private LayeredSlideMath() {
    }

    static int height(int from, int to, long elapsedMillis, int durationMillis) {
        if (elapsedMillis <= 0 && durationMillis > 0) return from;
        if (durationMillis <= 0 || elapsedMillis >= durationMillis) return to;
        double t = (double) elapsedMillis / durationMillis;
        double eased = t * t * (3.0 - 2.0 * t);
        long value = from + Math.round(((long) to - from) * eased);
        return (int) Math.max(Math.min(from, to), Math.min(Math.max(from, to), value));
    }
}
