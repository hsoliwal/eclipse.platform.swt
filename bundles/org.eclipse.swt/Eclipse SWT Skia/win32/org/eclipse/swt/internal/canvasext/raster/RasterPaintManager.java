/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *     SAP SE and others - initial API and implementation
 *     Synexia (Hitesh Soliwal) - adoption into the M3 fork, 2026
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext.raster;

import java.util.Arrays;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Pattern;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.PathEffect;
import io.github.humbleui.skija.Shader;

public class RasterPaintManager {

	// Skia format: dash/dot widths and whitespace widths must be considered.
	static final float[] LINE_DOT_ZERO = new float[] { 1, 1 };
	static final float[] LINE_DASH_ZERO = new float[] { 3, 1 };
	static final float[] LINE_DASHDOT_ZERO = new float[] { 3, 1, 1, 1 };
	static final float[] LINE_DASHDOTDOT_ZERO = new float[] { 3, 1, 1, 1, 1, 1 };

	private final RasterExternalGC gc;

	// M3 (efficiency): one stroke and one fill paint per GC, kept for the life of the GC object (a GC is reused after
	// dispose; Skija's cleaner frees the paints with it). A property is set on a base paint only when the GC's value
	// differs from the one the paint holds, and each draw gets a clone (one native copy) that its operation may change,
	// instead of a new paint, about ten native setters and a new dash effect per draw.
	private final Paint stroke = new Paint().setMode(PaintMode.STROKE);
	private final Paint fill = new Paint().setMode(PaintMode.FILL);
	private int strokeColor, fillColor, strokeCap = -1, strokeJoin = -1;
	private float strokeWidth = -1, strokeMiter = DEFAULT_MITER;
	private Boolean strokeAntiAlias, fillAntiAlias, strokeXor, fillXor;
	private boolean colorsKnown;
	/* the dash effect on the stroke paint and the inputs it was made from */
	private PathEffect dash;
	private int dashStyle = Integer.MIN_VALUE;
	private float[] dashSource;
	private float dashWidth = Float.NaN, dashUnit = Float.NaN, dashPhase = Float.NaN;

	/** Skia's miter limit of a new paint. */
	static final float DEFAULT_MITER = 4f;

	public RasterPaintManager(RasterExternalGC gc) {
		this.gc = gc;
	}

	public void performDraw(Consumer<Paint> operations) {
		final var scaledLineWidth = gc.getScaler().autoScaleUp(gc.getLineWidth() * 1F);
		syncStroke(scaledLineWidth);
		try (final Paint paint = stroke.makeClone()) {
			if (gc.getForegroundPattern() != null && !gc.getForegroundPattern().isDisposed()) {
				try (var shader = convertSWTPatternToSkiaShader(gc.getForegroundPattern())) {
					if (shader != null) {
						paint.setShader(shader);
					}
					operations.accept(paint);
				}
			} else {
				operations.accept(paint);
			}
		}
	}

	/* the GC's line attributes onto the stroke paint, each only when it changed */
	private void syncStroke(float scaledLineWidth) {
		final int color = RasterColorConverter.convertSWTColorToSkiaColor(gc.getForeground(), gc.getAlpha());
		if (!colorsKnown || color != strokeColor) {
			stroke.setColor(strokeColor = color);
		}
		final boolean antiAlias = gc.getAntialias() != SWT.OFF;
		if (strokeAntiAlias == null || antiAlias != strokeAntiAlias) {
			stroke.setAntiAlias(strokeAntiAlias = antiAlias);
		}
		final boolean xor = gc.getXORMode();
		if (strokeXor == null || xor != strokeXor) {
			stroke.setBlendMode((strokeXor = xor) ? io.github.humbleui.skija.BlendMode.DIFFERENCE : io.github.humbleui.skija.BlendMode.SRC_OVER);
		}
		if (scaledLineWidth != strokeWidth) {
			stroke.setStrokeWidth(strokeWidth = scaledLineWidth);
		}
		if (gc.getLineCap() != strokeCap) {
			stroke.setStrokeCap(getSkiaLineCap(strokeCap = gc.getLineCap()));
		}
		if (gc.getLineJoin() != strokeJoin) {
			stroke.setStrokeJoin(getSkiaLineJoin(strokeJoin = gc.getLineJoin()));
		}
		// M3 (GC seam, leaf 5): the miter limit of the line attributes, once set (RasterExternalGC.isMiterLimitSet)
		final float miter = gc.isMiterLimitSet() ? gc.getMiterLimit() : DEFAULT_MITER;
		if (miter != strokeMiter) {
			stroke.setStrokeMiter(strokeMiter = miter);
		}
		syncDash(scaledLineWidth);
		colorsKnown = true;
	}

	/* a new dash effect only when the line style, the dashes, the width, the zoom or the phase changed */
	private void syncDash(float scaledLineWidth) {
		final int style = gc.getLineStyle();
		final float[] source = style == SWT.LINE_CUSTOM ? gc.getLineDashes() : null;
		final float unit = gc.getScaler().autoScaleUp(1);
		final float phase = gc.getDashPhase();
		if (style == dashStyle && Arrays.equals(source, dashSource) && Float.compare(scaledLineWidth, dashWidth) == 0
				&& Float.compare(unit, dashUnit) == 0 && Float.compare(phase, dashPhase) == 0) {
			return;
		}
		final PathEffect made = createPathEffect(scaledLineWidth, gc.getScaler());
		stroke.setPathEffect(made);
		if (dash != null) {
			dash.close();
		}
		dash = made;
		dashStyle = style;
		dashSource = source == null ? null : source.clone();
		dashWidth = scaledLineWidth;
		dashUnit = unit;
		dashPhase = phase;
	}

	private PathEffect createPathEffect(float scaledLineWidth, DpiScalerUtil scaler) {

		final var floats = switch (gc.getLineStyle()) {
		case SWT.LINE_SOLID -> null;
		case SWT.LINE_DOT -> LINE_DOT_ZERO;
		case SWT.LINE_DASH -> LINE_DASH_ZERO;
		case SWT.LINE_DASHDOT -> LINE_DASHDOT_ZERO;
		case SWT.LINE_DASHDOTDOT -> LINE_DASHDOTDOT_ZERO;
		case SWT.LINE_CUSTOM -> gc.getLineDashes();
		default -> null;
		};

		if (floats == null || floats.length == 0) {
			return null;
		}

		if (gc.getLineStyle() == SWT.LINE_CUSTOM) {
			if (floats.length % 2 != 0) {
				// Skia requires even number of elements in the array, so we duplicate the
				// pattern to make it even.
				final float[] newFloats = new float[floats.length * 2];
				System.arraycopy(floats, 0, newFloats, 0, floats.length);
				System.arraycopy(floats, 0, newFloats, floats.length, floats.length);
				// M3 (GC seam, leaf 5): the dash offset of the line attributes is the phase (RasterExternalGC.getDashPhase)
				return PathEffect.makeDash(getScaledPathFloats(scaler.autoScaleUp(1), newFloats, scaler), gc.getDashPhase());
			}

		}

		final var scaledFloats = getScaledPathFloats(scaledLineWidth, floats, scaler);
		if (scaledFloats == null || scaledFloats.length == 0) {
			return null;
		}

		return PathEffect.makeDash(scaledFloats, gc.getDashPhase());

	}

	private static float[] getScaledPathFloats(float scaledLineWidth, float[] lineDotZero, DpiScalerUtil scaler) {
		final float[] result = new float[lineDotZero.length];
		for (int i = 0; i < lineDotZero.length; i++) {
			result[i] = lineDotZero[i] * scaledLineWidth;
		}
		return result;
	}

	public void performDrawFilled(Consumer<Paint> operations) {
		final int color = RasterColorConverter.convertSWTColorToSkiaColor(gc.getBackground(), gc.getAlpha());
		if (fillAntiAlias == null || color != fillColor) {
			fill.setColor(fillColor = color);
		}
		final boolean antiAlias = gc.getAntialias() != SWT.OFF;
		if (fillAntiAlias == null || antiAlias != fillAntiAlias) {
			fill.setAntiAlias(fillAntiAlias = antiAlias);
		}
		final boolean xor = gc.getXORMode();
		if (fillXor == null || xor != fillXor) {
			fill.setBlendMode((fillXor = xor) ? io.github.humbleui.skija.BlendMode.DIFFERENCE : io.github.humbleui.skija.BlendMode.SRC_OVER);
		}
		try (final Paint paint = fill.makeClone()) {
			if (gc.getBackgroundPattern() != null && !gc.getBackgroundPattern().isDisposed()) {
				try (Shader shader = convertSWTPatternToSkiaShader(gc.getBackgroundPattern())) {
					if (shader != null) {
						paint.setShader(shader);
					}
					operations.accept(paint);
				}
			} else {
				operations.accept(paint);
			}
		}
	}

	public static PaintStrokeCap getSkiaLineCap(int lineCap) {
		if (lineCap == SWT.CAP_SQUARE) {
			return PaintStrokeCap.SQUARE;
		}
		if (lineCap == SWT.CAP_ROUND) {
			return PaintStrokeCap.ROUND;
		}
		return PaintStrokeCap.BUTT;
	}

	public static PaintStrokeJoin getSkiaLineJoin(int lineJoin) {
		if (lineJoin == SWT.JOIN_ROUND) {
			return PaintStrokeJoin.ROUND;
		}
		if (lineJoin == SWT.JOIN_BEVEL) {
			return PaintStrokeJoin.BEVEL;
		}
		return PaintStrokeJoin.MITER;
	}

	/**
	 * Converts an SWT Pattern to a Skija Shader.
	 *
	 * @param pattern the SWT Pattern to convert
	 * @return the Skija Shader or null if conversion fails
	 */
	public Shader convertSWTPatternToSkiaShader(Pattern pattern) {
		final var props = org.eclipse.swt.graphics.PatternProperties.get(pattern);
		final var sc = gc.getScaler();
		if (props.getImage() == null) {
			final int col1 = RasterColorConverter.convertSWTColorToSkiaColor(props.getColor1(), props.getAlpha1());
			final int col2 = RasterColorConverter.convertSWTColorToSkiaColor(props.getColor2(), props.getAlpha2());
			final var gs = new io.github.humbleui.skija.GradientStyle(io.github.humbleui.skija.FilterTileMode.REPEAT,
					true, null);
			return Shader.makeLinearGradient(sc.autoScaleUp(props.getBaseX1()), sc.autoScaleUp(props.getBaseY1()),
					sc.autoScaleUp(props.getBaseX2()), sc.autoScaleUp(props.getBaseY2()), new int[] { col1, col2 },
					null, gs);
		}
		try (final var image = SwtToRasterImageConverter.convertSWTImageToSkiaImage(props.getImage(),
				gc.getScaler().getNativeZoom(), gc.getSkiaResources())) {
			return image.makeShader(io.github.humbleui.skija.FilterTileMode.REPEAT);
		}
	}
}