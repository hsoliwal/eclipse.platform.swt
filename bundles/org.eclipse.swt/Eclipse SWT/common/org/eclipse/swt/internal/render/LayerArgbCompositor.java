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
	/**
	 * Blend modes. The first three are the integer equations the optional JNI kernel shares; the others are the
	 * remaining separable modes of W3C Compositing and Blending Level 1 (as in GIMP, Inkscape and Photoshop),
	 * computed in Java by the general premultiplied form {@code cs (1 - da) + cd (1 - sa) + sa da B(Cb, Cs)}.
	 */
	public enum Mode {
		SRC_OVER, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT,
		DIFFERENCE, EXCLUSION
	}
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
		if (length >= NATIVE_THRESHOLD && dst != src && Native.ENABLED && mode.ordinal() <= Mode.SCREEN.ordinal()) {
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
				default -> {
					r = separable(mode, sr, sa, dr, da);
					g = separable(mode, sg, sa, dg, da);
					b = separable(mode, sb, sa, db, da);
				}
			}
			// Fixed-point independent rounding can exceed alpha by one or two.
			dst[dstOffset + i] = (a << 24) | (Math.min(r, a) << 16)
					| (Math.min(g, a) << 8) | Math.min(b, a);
		}
	}

	private static int mul255(int x, int y) { return (x * y + 127) / 255; }

	/* one premultiplied channel of a separable W3C mode: cs (1 - da) + cd (1 - sa) + sa da B(cd / da, cs / sa) */
	private static int separable(Mode mode, int sc, int sa, int dc, int da) {
		int base = mul255(sc, 255 - da) + mul255(dc, 255 - sa);
		if (sa == 0 || da == 0) return base;
		double cs = Math.min(1, (double) sc / sa), cb = Math.min(1, (double) dc / da);
		return base + (int) Math.round(sa * da * blend(mode, cb, cs) / 255.0);
	}

	/** The W3C blend function {@code B(Cb, Cs)} of a separable mode, channels 0 to 1. */
	static double blend(Mode mode, double cb, double cs) {
		return switch (mode) {
			case MULTIPLY -> cb * cs;
			case SCREEN -> cb + cs - cb * cs;
			case OVERLAY -> hardLight(cs, cb);
			case DARKEN -> Math.min(cb, cs);
			case LIGHTEN -> Math.max(cb, cs);
			case COLOR_DODGE -> cb == 0 ? 0 : cs >= 1 ? 1 : Math.min(1, cb / (1 - cs));
			case COLOR_BURN -> cb >= 1 ? 1 : cs <= 0 ? 0 : 1 - Math.min(1, (1 - cb) / cs);
			case HARD_LIGHT -> hardLight(cb, cs);
			case SOFT_LIGHT -> softLight(cb, cs);
			case DIFFERENCE -> Math.abs(cb - cs);
			case EXCLUSION -> cb + cs - 2 * cb * cs;
			case SRC_OVER -> cs;
		};
	}

	private static double hardLight(double cb, double cs) {
		return cs <= 0.5 ? cb * 2 * cs : cb + (2 * cs - 1) - cb * (2 * cs - 1);
	}

	private static double softLight(double cb, double cs) {
		if (cs <= 0.5) return cb - (1 - 2 * cs) * cb * (1 - cb);
		double d = cb <= 0.25 ? ((16 * cb - 12) * cb + 4) * cb : Math.sqrt(cb);
		return cb + (2 * cs - 1) * (d - cb);
	}

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
