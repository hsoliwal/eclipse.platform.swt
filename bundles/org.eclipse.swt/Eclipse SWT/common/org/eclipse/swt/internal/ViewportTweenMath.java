/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.internal;

import java.math.BigInteger;

/** Exact-endpoint monotone motion of signed 64-bit viewport coordinates.
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ViewportTweenMath {
    public static final int LINEAR = 0;
    public static final int EASE_OUT_CUBIC = 1;
    public static final int EASE_IN_OUT_CUBIC = 2;
    private static final long FRACTION = 1L << 52;

    private ViewportTweenMath() { }

    public static long position(long from, long to, long elapsedMillis, long durationMillis, int easing) {
        if (durationMillis < 0 || easing < LINEAR || easing > EASE_IN_OUT_CUBIC) {
            throw new IllegalArgumentException("invalid duration or easing");
        }
        if (durationMillis == 0 || elapsedMillis >= durationMillis) return to;
        if (elapsedMillis <= 0 || from == to) return from;
        double t = elapsedMillis / (double) durationMillis;
        double p = switch (easing) {
            case LINEAR -> t;
            case EASE_OUT_CUBIC -> 1 - (1 - t) * (1 - t) * (1 - t);
            case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            default -> throw new AssertionError(easing);
        };
        p = Math.max(0, Math.min(1, p));
        try {
            long delta = Math.subtractExact(to, from);
            return Math.addExact(from, Math.round(delta * p));
        } catch (ArithmeticException oppositeSignSpan) {
            // Only a span crossing the full signed-long range takes this path.
            // Q52 arithmetic retains its endpoints without floating-point loss
            // of the (possibly > 2^53) base coordinate.
            long fraction = Math.min(FRACTION, Math.max(0, Math.round(p * FRACTION)));
            BigInteger delta = BigInteger.valueOf(to).subtract(BigInteger.valueOf(from));
            BigInteger distance = delta.abs().multiply(BigInteger.valueOf(fraction))
                    .add(BigInteger.ONE.shiftLeft(51)).shiftRight(52);
            return BigInteger.valueOf(from).add(delta.signum() < 0 ? distance.negate() : distance)
                    .longValueExact();
        }
    }
}
