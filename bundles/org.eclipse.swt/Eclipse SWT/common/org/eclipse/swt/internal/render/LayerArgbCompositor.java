/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

import java.util.Arrays;
import java.util.Objects;

/**
 * Contiguous scanline blend operations over premultiplied ARGB8888 pixels.
 * The integer equations are deliberately shared with an optional JNI kernel.
 * The arrays hold pixels in 0xAARRGGBB order, with each color channel <= alpha.
 * No SWT widget, GC or native resource is owned by this class.
 *
 * <p>Native execution is off by default: enable with
 * {@code -Dswt.layerblend.native=true} and supply a matched library named
 * {@code swt-layerblend}. Missing libraries fall back to Java. The threshold
 * of 8192 pixels avoids crossing the JNI boundary for small draws, but is
 * not a measured performance crossover and must be benchmarked per platform.</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class LayerArgbCompositor {
	public enum Mode { SRC_OVER, MULTIPLY, SCREEN }
	private static final int NATIVE_THRESHOLD = 8192;

	private LayerArgbCompositor() { }

	/**
	 * Composite {@code length} source pixels over the destination. Opacity is
	 * a global multiplier in [0,255]. The source may alias the destination;
	 * partially overlapping aliases are snapshotted to preserve semantics.
	 * Premultiplied color input is required; this kernel is not a converter.
	 */
	public static void composite(int[] dst, int dstOffset, int[] src, int srcOffset,
			int length, int opacity, Mode mode) {
		Objects.requireNonNull(dst, "destination");
		Objects.requireNonNull(src, "source");
		Objects.requireNonNull(mode, "mode");
		if (opacity < 0 || opacity > 255) throw new IllegalArgumentException("opacity outside 0..255");
		if (dstOffset < 0 || srcOffset < 0 || length < 0 ||
				dstOffset > dst.length - length || srcOffset > src.length - length) {
			throw new IndexOutOfBoundsException("invalid scanline bounds");
		}
		if (opacity == 0 || length == 0) return;
		if (dst == src && dstOffset != srcOffset &&
				(long) dstOffset < (long) srcOffset + length &&
				(long) srcOffset < (long) dstOffset + length) {
			src = Arrays.copyOfRange(src, srcOffset, srcOffset + length);
			srcOffset = 0;
		}
		if (length >= NATIVE_THRESHOLD && dst != src && Native.ENABLED) {
			blendNative(dst, dstOffset, src, srcOffset, length, opacity, mode.ordinal());
			return;
		}
		for (int i = 0; i < length; i++) {
			int s = src[srcOffset + i];
			int d = dst[dstOffset + i];
			int sa = mul255(s >>> 24, opacity);
			int sr = mul255((s >>> 16) & 255, opacity);
			int sg = mul255((s >>> 8) & 255, opacity);
			int sb = mul255(s & 255, opacity);
			int da = d >>> 24;
			int dr = (d >>> 16) & 255, dg = (d >>> 8) & 255, db = d & 255;
			int a = sa + mul255(da, 255 - sa);
			int r, g, b;
			switch (mode) {
				case SRC_OVER -> {
					r = sr + mul255(dr, 255 - sa);
					g = sg + mul255(dg, 255 - sa);
					b = sb + mul255(db, 255 - sa);
				}
				case MULTIPLY -> {
					r = mul255(sr, 255 - da) + mul255(dr, 255 - sa) + mul255(sr, dr);
					g = mul255(sg, 255 - da) + mul255(dg, 255 - sa) + mul255(sg, dg);
					b = mul255(sb, 255 - da) + mul255(db, 255 - sa) + mul255(sb, db);
				}
				case SCREEN -> {
					r = sr + dr - mul255(sr, dr);
					g = sg + dg - mul255(sg, dg);
					b = sb + db - mul255(sb, db);
				}
				default -> throw new AssertionError(mode);
			}
			// Fixed-point independent rounding can exceed alpha by one or two.
			dst[dstOffset + i] = (a << 24) | (Math.min(r, a) << 16)
					| (Math.min(g, a) << 8) | Math.min(b, a);
		}
	}

	private static int mul255(int x, int y) { return (x * y + 127) / 255; }

	/** Whether the optional matched JNI library was loaded. */
	public static boolean nativeEnabled() { return Native.ENABLED; }

	private static final class Native {
		static final boolean ENABLED = load();
		private static boolean load() {
			if (!Boolean.getBoolean("swt.layerblend.native")) return false;
			try {
				System.loadLibrary("swt-layerblend");
				return true;
			} catch (UnsatisfiedLinkError | SecurityException unavailable) {
				return false;
			}
		}
	}

	private static native void blendNative(int[] dst, int dstOffset,
			int[] src, int srcOffset, int length, int opacity, int mode);
}
