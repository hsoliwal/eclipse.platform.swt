/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

import java.util.Objects;

/**
 * Contiguous scanline blend operations over premultiplied ARGB8888 pixels.
 * The integer equations are deliberately shared with an optional JNI kernel.
 * The arrays hold pixels in 0xAARRGGBB order, with each color channel <= alpha.
 * No SWT widget, GC or native resource is owned by this class.
 *
 * <p>Native execution is off by default: enable with
 * {@code -Dswt.layerblend.native=true} and supply a matched library named
 * {@code swt-layerblend}. Missing libraries fall back to Java. An older JNI
 * library supporting only the first three modes is detected without changing
 * its existing ABI, and one without the masked entry point keeps masked
 * blends in Java. The threshold of 8192 pixels avoids crossing the JNI
 * boundary for small draws; it is not a measured performance crossover.</p>
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
	 * partially overlapping aliases are traversed in the safe direction,
	 * without making a temporary copy.
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
		if (length >= NATIVE_THRESHOLD && dst != src && mode.ordinal() < Native.MODE_COUNT) {
			blendNative(dst, dstOffset, src, srcOffset, length, opacity, mode.ordinal());
			return;
		}
		boolean backward = dst == src && dstOffset > srcOffset &&
				(long) dstOffset < (long) srcOffset + length;
		for (int k = 0; k < length; k++) {
			int i = backward ? length - 1 - k : k;
			dst[dstOffset + i] = blendPixel(src[srcOffset + i], dst[dstOffset + i], opacity, mode);
		}
	}

	/**
	 * Composite {@code length} source pixels over the destination through a byte coverage mask (unsigned 0..255),
	 * for every {@link Mode}. Coverage 0 leaves the destination pixel untouched; 255 matches {@link #composite}.
	 * The effective opacity of a pixel is {@code (opacity * coverage + 127) / 255}, the same rounding in Java and
	 * in the optional JNI kernel. Aliasing follows {@link #composite}: partial overlaps are traversed in the safe
	 * direction without a temporary copy.
	 */
	public static void compositeMasked(int[] dst, int dstOffset, int[] src, int srcOffset,
			byte[] mask, int maskOffset, int length, int opacity, Mode mode) {
		Objects.requireNonNull(dst, "destination");
		Objects.requireNonNull(src, "source");
		Objects.requireNonNull(mask, "mask");
		Objects.requireNonNull(mode, "mode");
		if (opacity < 0 || opacity > 255) throw new IllegalArgumentException("opacity outside 0..255");
		if (length < 0 || dstOffset < 0 || srcOffset < 0 || maskOffset < 0 ||
				dstOffset > dst.length - length || srcOffset > src.length - length ||
				maskOffset > mask.length - length) {
			throw new IndexOutOfBoundsException("invalid masked scanline bounds");
		}
		if (length == 0 || opacity == 0) return;
		if (length >= NATIVE_THRESHOLD && dst != src && mode.ordinal() < Native.MODE_COUNT && !Native.maskUnavailable) {
			try {
				blendMaskedNative(dst, dstOffset, src, srcOffset, mask, maskOffset, length, opacity, mode.ordinal());
				return;
			} catch (UnsatisfiedLinkError olderLibrary) {
				// A library built before the masked entry point keeps its unmasked ABI; use Java from now on.
				Native.maskUnavailable = true;
			}
		}
		boolean backward = dst == src && dstOffset > srcOffset &&
				(long) dstOffset < (long) srcOffset + length;
		for (int k = 0; k < length; k++) {
			int i = backward ? length - 1 - k : k;
			int coverage = mul255(opacity, mask[maskOffset + i] & 255);
			if (coverage != 0) dst[dstOffset + i] = blendPixel(src[srcOffset + i], dst[dstOffset + i], coverage, mode);
		}
	}

	/** One premultiplied pixel of {@code mode} at {@code opacity}: the Java atom both scanline paths share. */
	private static int blendPixel(int s, int d, int opacity, Mode mode) {
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
		return (a << 24) | (Math.min(r, a) << 16) | (Math.min(g, a) << 8) | Math.min(b, a);
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
		static final int MODE_COUNT = modes();
		/* set once an older library is found to lack the masked entry point */
		static volatile boolean maskUnavailable;
		private static int modes() {
			if (!ENABLED) return 0;
			try {
				int supported = nativeModeCount();
				return supported >= 3 && supported <= Mode.values().length ? supported : 3;
			} catch (UnsatisfiedLinkError incompatible) {
				// A pre-extension library has no capability query and supports modes 0..2.
				return 3;
			}
		}
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

	private static native int nativeModeCount();

	private static native void blendNative(int[] dst, int dstOffset,
			int[] src, int srcOffset, int length, int opacity, int mode);

	private static native void blendMaskedNative(int[] dst, int dstOffset,
			int[] src, int srcOffset, byte[] mask, int maskOffset, int length, int opacity, int mode);
}
