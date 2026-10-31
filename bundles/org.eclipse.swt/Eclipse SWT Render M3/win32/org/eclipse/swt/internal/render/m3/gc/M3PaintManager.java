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
package org.eclipse.swt.internal.render.m3.gc;

import java.util.Arrays;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Pattern;

import jdk.m3.graphics.M3Paint;
import jdk.m3.graphics.M3PaintMode;
import jdk.m3.graphics.M3PaintStrokeCap;
import jdk.m3.graphics.M3PaintStrokeJoin;
import jdk.m3.graphics.M3PathEffect;
import jdk.m3.graphics.M3Shader;

public class M3PaintManager {

	// Skia format: dash/dot widths and whitespace widths must be considered.
	static final float[] LINE_DOT_ZERO = new float[] { 1, 1 };
	static final float[] LINE_DASH_ZERO = new float[] { 3, 1 };
	static final float[] LINE_DASHDOT_ZERO = new float[] { 3, 1, 1, 1 };
	static final float[] LINE_DASHDOTDOT_ZERO = new float[] { 3, 1, 1, 1, 1, 1 };

	private final M3ExternalGC gc;

	public M3PaintManager(M3ExternalGC gc) {
		this.gc = gc;
	}

	public void performDraw(Consumer<M3Paint> operations) {
		try (final M3Paint paint = new M3Paint()) {
			paint.setAlpha(gc.getAlpha());
			paint.setColor(M3ColorConverter.convertSWTColorToM3Color(gc.getForeground(), gc.getAlpha()));
			paint.setAntiAlias(gc.getAntialias() == SWT.ON);
			if (gc.getXORMode()) {
				paint.setBlendMode(jdk.m3.graphics.M3BlendMode.DIFFERENCE);
			} else {
				paint.setBlendMode(jdk.m3.graphics.M3BlendMode.SRC_OVER);
			}
			final var scaledLineWidth = gc.getScaler().autoScaleUp(gc.getLineWidth() * 1F);
			paint.setMode(M3PaintMode.STROKE);
			paint.setStrokeWidth(scaledLineWidth);
			paint.setStrokeCap(getM3LineCap(gc.getLineCap()));
			paint.setStrokeJoin(getM3LineJoin(gc.getLineJoin()));
			// M3 (GC seam, leaf 5): the miter limit of the line attributes, once set (M3ExternalGC.isMiterLimitSet)
			if (gc.isMiterLimitSet()) {
				paint.setStrokeMiter(gc.getMiterLimit());
			}

			try (var pathEffect = createPathEffect(scaledLineWidth, gc.getScaler())) {

				paint.setPathEffect(pathEffect);

				if (gc.getForegroundPattern() != null && !gc.getForegroundPattern().isDisposed()) {
					try (var shader = convertSWTPatternToM3Shader(gc.getForegroundPattern())) {
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
	}

	private M3PathEffect createPathEffect(float scaledLineWidth, DpiScalerUtil scaler) {

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
			// M3 (GC seam, leaf 6; STROKE_SCALE, pattern M3-GC-STROKE v2, census GF11 = PRE_EXISTING P05): custom dashes are
			// absolute lengths, already in pixels (M3ExternalGC scales them when set), and the array is doubled as the stock GC
			// doubles it (GC.getPen, GDI+ dash / width); the donor multiplied even arrays by the scaled width and odd arrays by
			// the zoom a second time. M3 (GC seam, leaf 5): the dash offset of the line attributes is the phase
			return M3PathEffect.makeDash(doubled(floats), gc.getDashPhase());
		}

		final var scaledFloats = getScaledPathFloats(scaledLineWidth, floats, scaler);
		if (scaledFloats == null || scaledFloats.length == 0) {
			return null;
		}

		return M3PathEffect.makeDash(scaledFloats, gc.getDashPhase());

	}

	/* leaf 6: the pattern twice (Skia needs an even count; the stock GC doubles every custom pattern) */
	private static float[] doubled(float[] dashes) {
		final float[] twice = Arrays.copyOf(dashes, dashes.length * 2);
		System.arraycopy(dashes, 0, twice, dashes.length, dashes.length);
		return twice;
	}

	private static float[] getScaledPathFloats(float scaledLineWidth, float[] lineDotZero, DpiScalerUtil scaler) {
		final float[] result = new float[lineDotZero.length];
		for (int i = 0; i < lineDotZero.length; i++) {
			result[i] = lineDotZero[i] * scaledLineWidth;
		}
		return result;
	}

	public void performDrawFilled(Consumer<M3Paint> operations) {
		try (final M3Paint paint = new M3Paint()) {
			paint.setMode(M3PaintMode.FILL);
			paint.setColor(M3ColorConverter.convertSWTColorToM3Color(gc.getBackground(), gc.getAlpha()));
			paint.setAntiAlias(gc.getAntialias() == SWT.ON);
			if (gc.getXORMode()) {
				paint.setBlendMode(jdk.m3.graphics.M3BlendMode.DIFFERENCE);
			} else {
				paint.setBlendMode(jdk.m3.graphics.M3BlendMode.SRC_OVER);
			}
			if (gc.getBackgroundPattern() != null && !gc.getBackgroundPattern().isDisposed()) {
				try (M3Shader shader = convertSWTPatternToM3Shader(gc.getBackgroundPattern())) {
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

	/**
	 * leaf 6 (ARG_CONTRACT, census GF46): a fill with the foreground brush, as the stock GDI+ getFgBrush: the foreground colour at
	 * the GC alpha, or the foreground pattern (drawPoint).
	 */
	public void performFillForeground(Consumer<M3Paint> operations) {
		try (final M3Paint paint = new M3Paint()) {
			paint.setMode(M3PaintMode.FILL);
			paint.setColor(M3ColorConverter.convertSWTColorToM3Color(gc.getForeground(), gc.getAlpha()));
			paint.setAntiAlias(gc.getAntialias() == SWT.ON);
			paint.setBlendMode(gc.getXORMode() ? jdk.m3.graphics.M3BlendMode.DIFFERENCE : jdk.m3.graphics.M3BlendMode.SRC_OVER);
			final Pattern pattern = gc.getForegroundPattern();
			try (M3Shader shader = pattern != null && !pattern.isDisposed() ? convertSWTPatternToM3Shader(pattern) : null) {
				paint.setShader(shader);
				operations.accept(paint);
			}
		}
	}

	public static M3PaintStrokeCap getM3LineCap(int lineCap) {
		if (lineCap == SWT.CAP_SQUARE) {
			return M3PaintStrokeCap.SQUARE;
		}
		if (lineCap == SWT.CAP_ROUND) {
			return M3PaintStrokeCap.ROUND;
		}
		return M3PaintStrokeCap.BUTT;
	}

	public static M3PaintStrokeJoin getM3LineJoin(int lineJoin) {
		if (lineJoin == SWT.JOIN_ROUND) {
			return M3PaintStrokeJoin.ROUND;
		}
		if (lineJoin == SWT.JOIN_BEVEL) {
			return M3PaintStrokeJoin.BEVEL;
		}
		return M3PaintStrokeJoin.MITER;
	}

	/**
	 * Converts an SWT Pattern to a Skija Shader.
	 *
	 * @param pattern the SWT Pattern to convert
	 * @return the Skija Shader or null if conversion fails
	 */
	public M3Shader convertSWTPatternToM3Shader(Pattern pattern) {
		final var props = org.eclipse.swt.graphics.PatternProperties.get(pattern);
		final var sc = gc.getScaler();
		if (props.getImage() == null) {
			final int col1 = M3ColorConverter.convertSWTColorToM3Color(props.getColor1(), props.getAlpha1());
			final int col2 = M3ColorConverter.convertSWTColorToM3Color(props.getColor2(), props.getAlpha2());
			final var gs = new jdk.m3.graphics.M3GradientStyle(jdk.m3.graphics.M3FilterTileMode.REPEAT,
					true, null);
			return M3Shader.makeLinearGradient(sc.autoScaleUp(props.getBaseX1()), sc.autoScaleUp(props.getBaseY1()),
					sc.autoScaleUp(props.getBaseX2()), sc.autoScaleUp(props.getBaseY2()), new int[] { col1, col2 },
					null, gs);
		}
		try (final var image = SwtToM3ImageConverter.convertSWTImageToM3Image(props.getImage(),
				gc.getScaler().getNativeZoom(), gc.getM3Resources())) {
			return image.makeShader(jdk.m3.graphics.M3FilterTileMode.REPEAT);
		}
	}
}