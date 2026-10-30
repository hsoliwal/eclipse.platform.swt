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

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.FontMetricsExtension;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.canvasext.IExternalGC;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.EncoderPNG;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PathEffect;
import io.github.humbleui.skija.PathFillMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.Rect;

public class M3ExternalGC implements IExternalGC {

	private final IM3Surface surface;
	private int alpha = 255;
	// M3 (GC seam, leaf 6; HINT_STATE, pattern M3-GC-HINT v1, census GF06): the stock fresh GC answers SWT.DEFAULT (no GDI+ graphics);
	// the donor started at 0 (= SWT.OFF), text ON and nearest sampling read back as NONE
	private int antialias = SWT.DEFAULT;
	private boolean xorModeActive;
	// leaf 6 (GC_STATE_RESET, GF05): whether advanced graphics were asked for (stock: a GDI+ graphics exists)
	private boolean advanced;
	private int lineWidth = 1;
	private int lineCap = SWT.CAP_FLAT;
	private int lineStyle = SWT.LINE_SOLID;
	private Pattern foregroundPattern;
	private Pattern backgroundPattern;

	private int fillRule = SWT.FILL_EVEN_ODD;
	private int lineJoin = SWT.JOIN_MITER;
	private float[] lineDashes;
	// M3 (GC seam, leaf 5; pattern M3-GC-STROKE v1): the stroke state the donor stored but never applied, with the stock
	// defaults (GCData: dash offset 0, miter limit 10); the dash phase reaches LINE_CUSTOM patterns (M3PaintManager), the
	// miter limit every stroke once setLineAttributes ran (until then the paint keeps the donor's Skija miter, 4, and
	// drawPath's 1000, which the adopted tests pin)
	private float dashOffset;
	private float miterLimit = 10;
	private boolean miterLimitSet;

	private final Drawable drawable;
	private final Device device;

	private SamplingMode interpolationMode = SamplingMode.DEFAULT;
	private int interpolation = SWT.DEFAULT;

	private final IM3CanvasExtension m3Extension;
	private final IM3Resources resources;
	private final int style;
	private int textAntiAlias = SWT.DEFAULT;

	// M3 (GC seam, leaf 6; CLIP_STATE + TRANSFORM_STATE, census GF02, GF16): the clip and the transform of this GC in one layer
	// above the frame (M3GcLayer); it records the canvas save count at construction, the count dispose restores
	private final M3GcLayer layer;
	private final M3PaintManager paintManager;
	private final GCData data = new GCData();
	private final DpiScalerUtil dpiScalerUtil;

	public M3ExternalGC(IM3CanvasExtension exst, int style) {
		this.drawable = exst.getDrawable();
		device = exst.getDevice();
		this.surface = exst.getSurface();
		this.m3Extension = exst;
		this.resources = m3Extension.getResources();
		this.style = style;
		this.layer = new M3GcLayer(this.surface);
		this.paintManager = new M3PaintManager(this);
		this.dpiScalerUtil = new DpiScalerUtil(this.resources.getScaler());

	}

	@Override
	public void dispose() {

		resources.resetBaseColors();
		// M3 (GC seam, leaves 3 and 6): the GC is reusable after dispose. A drawn plane disposes it after each owner-draw event
		// and hands the same object to the next one: the canvas goes back to the construction save count and the clip and
		// transform bookkeeping to none (M3GcLayer.close)
		layer.close();
		resetState();
	}

	/*
	 * leaf 6 (GC_STATE_RESET, census GF49): every attribute back to the constructed defaults, so a reused GC (drawn planes dispose
	 * and reuse one per owner-draw event) starts each event as a new stock GC does; the donor reset only the colours, the
	 * canvas state, the clip and the transform
	 */
	private void resetState() {
		advanced = xorModeActive = miterLimitSet = false;
		alpha = 255;
		foregroundPattern = backgroundPattern = null;
		lineWidth = 1;
		lineCap = SWT.CAP_FLAT;
		lineJoin = SWT.JOIN_MITER;
		lineStyle = SWT.LINE_SOLID;
		lineDashes = null;
		dashOffset = 0;
		miterLimit = 10;
		fillRule = SWT.FILL_EVEN_ODD;
		resetHints();
		resources.resetFont();
	}

	@Override
	public Point textExtent(String string) {
		return textExtent(string, SWT.NONE);
	}

	@Override
	public void setBackground(Color color) {
		this.resources.setBackground(color);
	}

	@Override
	public void setForeground(Color color) {
		this.resources.setForeground(color);
	}

	@Override
	public void fillRectangle(Rectangle rect) {
		fillRectangle(rect.x, rect.y, rect.width, rect.height);
	}

	/* leaf 6 (ARG_CONTRACT, GF24): the stock checks after the size checks: null image, disposed image */
	private static void checkImage(Image image) {
		if (image == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		if (image.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
	}

	@Override
	public void drawImage(Image image, int x, int y) {

		checkImage(image);
		final var imgBounds = image.getBounds();
		drawImage(image, 0, 0, imgBounds.width, imgBounds.height, x, y, imgBounds.width, imgBounds.height);
	}

	@Override
	public void drawImage(Image image, int destX, int destY, int destWidth, int destHeight) {

		// leaf 6 (ARG_CONTRACT, pattern M3-GC-CONTRACT v1, census GF24): the stock order: a zero size returns, a negative size is
		// rejected, then the image is checked (the donor logged a null image once and returned, and divided by a zero size)
		if (destWidth == 0 || destHeight == 0) {
			return;
		}
		if (destWidth < 0 || destHeight < 0) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		checkImage(image);
		drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height, destX, destY, destWidth, destHeight);

	}

	@Override
	public void drawImage(Image image, int srcX, int srcY, int srcWidth, int srcHeight, int destX, int destY,
			int destWidth, int destHeight) {

		// leaf 6 (ARG_CONTRACT, GF24): the stock order (W32G/GC.java drawImage): sizes, then signs, then the image
		if (srcWidth == 0 || srcHeight == 0 || destWidth == 0 || destHeight == 0) {
			return;
		}
		if (srcX < 0 || srcY < 0 || srcWidth < 0 || srcHeight < 0 || destWidth < 0 || destHeight < 0) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		checkImage(image);

		int sizeFactorInImage = Math.round(Math.max((float) destWidth / srcWidth, (float) destHeight / srcHeight));

		if (sizeFactorInImage == 0) {
			sizeFactorInImage = 1;
		}
		final IM3Canvas canvas = surface.getCanvas();
		final int fac = sizeFactorInImage;

		paintManager.performDraw(paint -> {
			paint.setAlpha(alpha);
			paint.setAntiAlias(true);
			canvas.drawImageRect(
					SwtToM3ImageConverter.convertSWTImageToM3Image(image, getScaler().autoScaleUp(100 * fac),
							resources),
					RectangleConverter.createScaledRectangle(getScaler(), srcX * fac, srcY * fac, srcWidth * fac,
							srcHeight * fac),
					RectangleConverter.createScaledRectangle(getScaler(), destX, destY, destWidth, destHeight),
					this.interpolationMode, paint, false);

		});

	}

	DpiScalerUtil getScaler() {
		return this.dpiScalerUtil;
	}

	/**
	 * Test method for drawing an image.
	 *
	 * @param str
	 * @param image
	 */
	static void writeFile(String str, io.github.humbleui.skija.Image image) {
		final byte[] imageBytes = EncoderPNG.encode(image).getBytes();

		final File f = new File(str);
		if (f.exists()) {
			f.delete();
		}

		try (FileOutputStream fis = new FileOutputStream(f)) {
			fis.write(imageBytes);
		} catch (final Exception e) {
			Logger.logException(e);
		}
	}

	@Override
	public void drawLine(int x1, int y1, int x2, int y2) {
		final float scaledOffsetValue = RectangleConverter.getScaledOffsetValue(getScaler(), lineWidth);
		// leaf 6 (LINE_ENDS, pattern M3-GC-LINE-ENDS v1, leaf 7 finding L7-32): an axis-aligned line is offset across its direction
		// only, so a one-pixel line covers x1 .. x2 - 1 like the stock line (ViewForm's separator); the donor offset both axes and
		// the start pixel was lost. Diagonals keep the donor's offset (the adopted drawLine tests pin it)
		final float ox = y1 == y2 && x1 != x2 ? 0 : scaledOffsetValue, oy = x1 == x2 && y1 != y2 ? 0 : scaledOffsetValue;
		final var scaler = this.dpiScalerUtil;
		paintManager.performDraw(paint -> surface.getCanvas().drawLine(scaler.autoScaleUp(x1) + ox, scaler.autoScaleUp(y1) + oy,
				scaler.autoScaleUp(x2) + ox, scaler.autoScaleUp(y2) + oy, paint));
	}

	@Override
	public Color getForeground() {
		return this.resources.getForeground();
	}

	@Override
	public void drawText(String string, int x, int y) {
		drawText(string, x, y, SWT.DRAW_DELIMITER | SWT.DRAW_TAB);
	}

	@Override
	public void drawText(String string, int x, int y, boolean isTransparent) {
		int flags = SWT.DRAW_DELIMITER | SWT.DRAW_TAB;
		if (isTransparent) {
			flags |= SWT.DRAW_TRANSPARENT;
		}
		drawText(string, x, y, flags);
	}

	@Override
	public void drawText(String text, int x, int y, int flags) {
		if (text == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		executeTextDraw(text, flags, x, y);
	}

	private io.github.humbleui.skija.Font getM3Font() {
		return this.resources.getM3Font();
	}

	private void executeTextDraw(String inputText, int flags, int x, int y) {
		if (isCulled(x, y)) {
			return;
		}
		final String splits[] = this.resources.getTextSplits(inputText, flags);
		M3TextDrawing.drawText(this, splits, flags, x, y);
	}

	/* the donor's early out (GF33, approximated): a start beyond the surface draws nothing */
	private boolean isCulled(int x, int y) {
		return this.surface.getWidth() < x || this.surface.getHeight() < y;
	}

	M3PaintManager getPaintManager() {
		return this.paintManager;
	}

	@Override
	public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		paintManager.performDraw(paint -> surface.getCanvas().drawArc(getScaler().autoScaleUp(x),
				getScaler().autoScaleUp(y), getScaler().autoScaleUp(x + width), getScaler().autoScaleUp(y + height),
				-startAngle, -arcAngle, false, paint));
	}

	@Override
	public void drawFocus(int x, int y, int width, int height) {
		paintManager.performDraw(paint -> {
			final var scaledLineWidth = getScaler().autoScaleUp(lineWidth * 1F);

			try (var pe = PathEffect.makeDash(new float[] { 1.5f * scaledLineWidth, 1.5f * scaledLineWidth }, 0.0f)) {
				paint.setPathEffect(pe);
				surface.getCanvas().drawRect(
						RectangleConverter.createScaledRectangleWithOffset(getScaler(), 0, x, y, width, height), paint);
			}

		});
	}

	@Override
	public void drawOval(int x, int y, int width, int height) {
		paintManager.performDraw(paint -> surface.getCanvas().drawOval(
				RectangleConverter.createScaledRectangleWithOffset(getScaler(), lineWidth, x, y, width, height),
				paint));
	}

	@Override
	public void drawPath(Path path) {
		paintManager.performDraw(paint -> {
			try (io.github.humbleui.skija.Path m3Path = M3PathConverter.convertSWTPathToM3Path(path,
					getScaler())) {
				if (m3Path == null) {
					return;
				}
				// the stroke miter on GC is quite high (the donor's default; leaf 5: a miter limit setLineAttributes gave wins,
				// M3PaintManager.performDraw applied it)
				if (!miterLimitSet) {
					paint.setStrokeMiter(1000);
				}
				surface.getCanvas().drawPath(m3Path, paint);
			}
		});
	}

	@Override
	public void drawPoint(int x, int y) {
		// leaf 6 (ARG_CONTRACT, GF46): the stock GDI+ point is a 1x1 fill with the foreground brush (colour at the GC alpha, or
		// the foreground pattern); the donor filled with performDrawFilled, whose background pattern replaced the colour
		paintManager.performFillForeground(paint -> {
			// points have always one pixel no matter the zoom...
			final var rec = RectangleConverter.scaleUpRectangle(getScaler(), new Rectangle(x, y, 1, 1));
			final var r = new Rect(rec.x, rec.y, rec.x + 1, rec.y + 1);
			surface.getCanvas().drawRect(r, paint);
		});
	}

	@Override
	public void drawPolygon(int[] inputPointArray) {

		final int[] pointArray = Arrays.copyOf(inputPointArray, inputPointArray.length);

		if (pointArray == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		if (isDisposed()) {
			SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
		}
		if (pointArray.length < 6 || pointArray.length % 2 != 0) {
			return;
		}

		// Handle SWT.MIRRORED style (adjust x-coordinates if needed)
		final int style = getStyle();
		final boolean mirrored = (style & SWT.MIRRORED) != 0;
		final boolean adjustX = mirrored && lineWidth != 0 && lineWidth % 2 == 0;
		if (adjustX) {
			for (int i = 0; i < pointArray.length; i += 2) {
				pointArray[i]--;
			}
		}

		paintManager.performDraw(paint -> {
			// Create Skija path for the polygon
			try (io.github.humbleui.skija.Path pathBuilder = new io.github.humbleui.skija.Path()) {
				// Move to first point
				pathBuilder.moveTo(getScaler().autoScaleUp(pointArray[0]), getScaler().autoScaleUp(pointArray[1]));
				// Add lines to subsequent points
				for (int i = 2; i < pointArray.length; i += 2) {
					pathBuilder.lineTo(getScaler().autoScaleUp(pointArray[i]),
							getScaler().autoScaleUp(pointArray[i + 1]));
				}
				pathBuilder.closePath();
				// Draw the polygon outline

				{
					var path = pathBuilder; // Skija 0.116.3: Path is its own builder; the outer try closes it
					if (path.isEmpty()) {
						return;
					}
					surface.getCanvas().drawPath(path, paint);
				}
			}
		});
		// Restore x-coordinates if mirrored
		if (adjustX) {
			for (int i = 0; i < pointArray.length; i += 2) {
				pointArray[i]++;
			}
		}
	}

	@Override
	public void drawPolyline(int[] pointArray) {
		paintManager.performDraw(paint -> surface.getCanvas().drawPolygon(convertToFloat(pointArray), paint));
	}

	private float[] convertToFloat(int[] array) {
		final float[] arrayAsFloat = new float[array.length];
		for (int i = 0; i < array.length; i++) {
			arrayAsFloat[i] = getScaler().autoScaleUp(array[i]);
		}
		return arrayAsFloat;
	}

	@Override
	public void drawRectangle(int x, int y, int width, int height) {

		paintManager.performDraw(paint -> {
			surface.getCanvas().drawRect(
					RectangleConverter.createScaledRectangleWithOffset(dpiScalerUtil, lineWidth, x, y, width, height),
					paint);
		});

	}

	@Override
	public void drawRectangle(Rectangle rect) {
		drawRectangle(rect.x, rect.y, rect.width, rect.height);
	}

	@Override
	public void drawRoundRectangle(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		paintManager
		.performDraw(
				paint -> surface.getCanvas()
				.drawRRect(
						RectangleConverter
						.offsetRectangle(getScaler(), lineWidth,
								RectangleConverter.createScaledRoundRectangle(getScaler(), x, y,
										width, height, arcWidth / 2.0f, arcHeight / 2.0f)),
						paint));
	}

	@Override
	public void drawString(String string, int x, int y) {
		drawString(string, x, y, false);
	}

	@Override
	public void drawString(String string, int x, int y, boolean isTransparent) {
		if (string == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		// leaf 6 (TEXT_DRAW_FLAGS, pattern M3-GC-TEXT-FLAGS v1, census GF29): one line as given, as the stock ExtTextOut draws it;
		// the donor drew through drawText with DRAW_DELIMITER | DRAW_TAB (lines split, tabs expanded)
		if (!isCulled(x, y)) {
			M3TextDrawing.drawText(this, new String[] { string }, isTransparent ? SWT.DRAW_TRANSPARENT : SWT.NONE, x, y);
		}
	}

	@Override
	public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		paintManager.performDrawFilled(paint -> surface.getCanvas().drawArc(getScaler().autoScaleUp(x),
				getScaler().autoScaleUp(y), getScaler().autoScaleUp(x + width), getScaler().autoScaleUp(y + height),
				-startAngle, -arcAngle, true, paint));
	}

	@Override
	public void fillGradientRectangle(int x, int y, int width, int height, boolean vertical) {
		// leaf 6 (GRADIENT_ROWS, pattern M3-GC-GRADIENT v1, leaf 7 findings L7-31, L7-34): a zero extent draws nothing; a negative
		// extent moves the origin (stock x += width; the donor clamped it to 0: GF47); without advanced graphics the stock GC
		// uses GDI GradientFill, whose rows are reproduced exactly (gradientRows), else the Skia gradient stands for GDI+'s brush
		if (width == 0 || height == 0) {
			return;
		}
		boolean swapColors = false;
		if (width < 0) {
			x = x + width;
			width = -width;
			if (!vertical) {
				swapColors = true;
			}
		}
		if (height < 0) {
			y = y + height;
			height = -height;
			if (vertical) {
				swapColors = true;
			}
		}
		final int x2 = vertical ? x : x + width;
		final int y2 = vertical ? y + height : y;

		final Rect rect = RectangleConverter.createScaledRectangle(getScaler(), x, y, width, height);
		int fromColor = M3ColorConverter.convertSWTColorToM3Color(getForeground());
		int toColor = M3ColorConverter.convertSWTColorToM3Color(getBackground());
		if (fromColor == toColor) {
			paintManager.performDrawFilled(paint -> surface.getCanvas().drawRect(rect, paint));
			return;
		}
		if (swapColors) {
			final int tempColor = fromColor;
			fromColor = toColor;
			toColor = tempColor;
		}

		if (!advanced) {
			gradientRows(rect, vertical, fromColor, toColor);
			return;
		}
		final var s = getScaler();
		performDrawGradientFilled(paint -> surface.getCanvas().drawRect(rect, paint), s.autoScaleUp(x),
				s.autoScaleUp(y), s.autoScaleUp(x2), s.autoScaleUp(y2), fromColor, toColor);
	}

	/* leaf 6: GDI GradientFill's rows in pixels: row i of n = from + (to - from) * i / n per 16-bit channel, aliased, opaque */
	private void gradientRows(Rect rect, boolean vertical, int from, int to) {
		final int left = Math.round(rect.getLeft()), top = Math.round(rect.getTop());
		final int right = Math.round(rect.getRight()), bottom = Math.round(rect.getBottom());
		final int n = vertical ? bottom - top : right - left;
		paintManager.performDrawFilled(paint -> {
			paint.setShader(null);
			paint.setAntiAlias(false);
			IntStream.range(0, n).forEach(i -> {
				paint.setColor(gradientColor(from, to, i, n));
				surface.getCanvas().drawRect(vertical ? Rect.makeLTRB(left, top + i, right, top + i + 1)
						: Rect.makeLTRB(left + i, top, left + i + 1, bottom), paint);
			});
		});
	}

	/* leaf 6: one GradientFill row colour: ((a * 257) + (b - a) * 257 * i / n) >> 8 per channel (measured: 0 mismatches) */
	static int gradientColor(int from, int to, int i, int n) {
		return 0xFF000000 | IntStream.of(16, 8, 0).map(s -> {
			final int a = (from >> s) & 0xFF, b = (to >> s) & 0xFF;
			return (((a * 257) + (b - a) * 257 * i / n) >> 8) << s;
		}).reduce(0, (p, q) -> p | q);
	}

	private void performDrawGradientFilled(Consumer<Paint> operations, int x, int y, int x2, int y2, int fromColor,
			int toColor) {
		paintManager.performDraw(paint -> {

			try (Shader gradient = convertGradientRectangleToM3Shader(x, y, x2 - x, y2 - y, false, fromColor,
					toColor)) {
				paint.setShader(gradient);
				paint.setAntiAlias(true);
				paint.setMode(PaintMode.FILL);
				operations.accept(paint);
			}
		});
	}

	private static Shader convertGradientRectangleToM3Shader(int x, int y, int width, int height, boolean vertical,
			int fromColor, int toColor) {

		final var gs = new GradientStyle(FilterTileMode.REPEAT, true, null);
		final Shader s = Shader.makeLinearGradient(x, y, x + width, y + height, new int[] { fromColor, toColor }, null,
				gs);

		return s;
	}

	@Override
	public void fillOval(int x, int y, int width, int height) {
		paintManager.performDrawFilled(paint -> surface.getCanvas()
				.drawOval(RectangleConverter.createScaledRectangle(getScaler(), x, y, width, height), paint));
	}

	@Override
	public void fillPath(Path path) {
		paintManager.performDrawFilled(paint -> {
			try (io.github.humbleui.skija.Path m3Path = M3PathConverter.convertSWTPathToM3Path(path,
					getScaler())) {
				if (m3Path == null) {
					return;
				}
				m3Path
				.setFillMode(this.fillRule == SWT.FILL_EVEN_ODD ? PathFillMode.EVEN_ODD : PathFillMode.WINDING);
				paint.setAntiAlias(this.antialias == SWT.ON);
				surface.getCanvas().drawPath(m3Path, paint);
			}
		});
	}

	@Override
	public void fillPolygon(int[] pointArray) {
		if (pointArray == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		if (isDisposed()) {
			SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
		}
		if (pointArray.length < 6 || pointArray.length % 2 != 0) {
			return;
		}

		paintManager.performDrawFilled(paint -> {

			try (var path = M3PathConverter.createPath(pointArray, fillRule, getScaler())) {
				if (path == null || path.isEmpty()) {
					return;
				}
				surface.getCanvas().drawPath(path, paint);
			}

		});
	}

	@Override
	public void fillRectangle(int x, int y, int width, int height) {
		paintManager.performDrawFilled(paint -> surface.getCanvas()
				.drawRect(RectangleConverter.createScaledRectangle(getScaler(), x, y, width, height), paint));
	}

	@Override
	public void fillRoundRectangle(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		paintManager.performDrawFilled(
				paint -> surface.getCanvas().drawRRect(RectangleConverter.createScaledRoundRectangle(getScaler(), x, y,
						width, height, arcWidth / 2.0f, arcHeight / 2.0f), paint));
	}

	@Override
	public Point textExtent(String text, int flags) {
		return getScaler().scaleDown(resources.textExtent(text, flags));
	}

	@Override
	public void setFont(org.eclipse.swt.graphics.Font font) {
		this.resources.setFont(font);
	}

	@Override
	public void setClipping(int x, int y, int width, int height) {
		setClipping(new Rectangle(x, y, width, height));
	}

	@Override
	public void setTransform(Transform transform) {

		// leaf 6 (TRANSFORM_STATE, GF16): the matrix of the GC layer on the frame's matrix; the clip in force stays (the donor saved
		// a level per call, which setClipping then popped instead of the clip)
		advanced |= transform != null;
		layer.setTransform(M3TransformConverter.toM3Matrix(transform, getScaler()));

	}

	@Override
	public void setAlpha(int alpha) {
		alpha = alpha & 0xFF;
		// leaf 6 (GC_STATE_RESET, GF05): as the stock GC, an alpha other than 255 needs advanced graphics
		advanced |= alpha != 0xFF;
		this.alpha = alpha;
	}

	@Override
	public int getAlpha() {
		return this.alpha;
	}

	@Override
	public void setLineWidth(int i) {

		if (i <= 0) {
			this.lineWidth = 1;
		} else {
			this.lineWidth = i;
		}

	}

	@Override
	public int getAntialias() {
		return antialias;
	}

	@Override
	public void setAntialias(int antialias) {
		if (antialias != SWT.DEFAULT && antialias != SWT.ON && antialias != SWT.OFF) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		advanced |= antialias != SWT.DEFAULT;
		this.antialias = antialias;
	}

	@Override
	public void setAdvanced(boolean enable) {
		// leaf 6 (GC_STATE_RESET, pattern M3-GC-RESET v1, census GF05): stock SetAdvancedOperation: leaving the advanced mode puts
		// alpha back to 255, drops the patterns, the transform and the clip, and the hints read DEFAULT again (no GDI+ graphics);
		// the donor did nothing, so StyledText's block selection left alpha 100 for the rest of the paint
		if (enable) {
			advanced = true;
			return;
		}
		advanced = false;
		alpha = 255;
		foregroundPattern = backgroundPattern = null;
		layer.close();
		resetHints();
	}

	/* leaf 6: antialias, text antialias and interpolation at DEFAULT (the stock GC without GDI+ graphics) */
	private void resetHints() {
		antialias = SWT.DEFAULT;
		textAntiAlias = SWT.DEFAULT;
		interpolation = SWT.DEFAULT;
		interpolationMode = SamplingMode.DEFAULT;
	}

	@Override
	public void setLineStyle(int lineStyle) {
		if (lineStyle != SWT.LINE_SOLID && lineStyle != SWT.LINE_DASH && lineStyle != SWT.LINE_DOT
				&& lineStyle != SWT.LINE_DASHDOT && lineStyle != SWT.LINE_DASHDOTDOT && lineStyle != SWT.LINE_CUSTOM) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		this.lineStyle = lineStyle;
	}

	@Override
	public int getLineStyle() {
		return lineStyle;
	}

	@Override
	public int getLineWidth() {
		return lineWidth;
	}

	@Override
	public LineAttributes getLineAttributes() {
		// leaf 6 (STROKE_SCALE, pattern M3-GC-STROKE v2, census GF14): every field in points, a fresh object. The line width and the
		// dash offset are kept in points, the dashes in pixels (the donor divided the point width by the zoom a second time)
		return new LineAttributes(lineWidth, lineCap, lineJoin, lineStyle, getScaler().autoScaleDown(lineDashes), dashOffset,
				miterLimit);
	}

	@Override
	public Rectangle getClipping() {
		// leaf 6 (CLIP_STATE, GF02): the stock clip box in points: the clip (or the frame when none) within the frame, seen
		// through the transform (GDI+ visible clip bounds). The donor answered the frame in pixels unless a region was set, and
		// null after setClipping(Region)
		return toPoints(layer.localClip(frame()));
	}

	/* the frame of this GC (the paint's damage) in device pixels; the surface when the extension has none */
	private Rect frame() {
		final Rectangle area = m3Extension.getClientArea();
		if (area == null) {
			return new Rect(0, 0, surface.getWidth(), surface.getHeight());
		}
		return Rect.makeXYWH(area.x, area.y, area.width, area.height);
	}

	/* local pixels to the enclosing rectangle in points */
	private Rectangle toPoints(Rect r) {
		final var sc = getScaler();
		final int left = (int) Math.floor(sc.autoScaleDown(r.getLeft()) + 1e-3f);
		final int top = (int) Math.floor(sc.autoScaleDown(r.getTop()) + 1e-3f);
		final int right = (int) Math.ceil(sc.autoScaleDown(r.getRight()) - 1e-3f);
		final int bottom = (int) Math.ceil(sc.autoScaleDown(r.getBottom()) - 1e-3f);
		return new Rectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
	}

	@Override
	public Point stringExtent(String string) {
		// leaf 6 (TEXT_METRICS, GF28): the string as given (stock GetTextExtentPoint32), not textExtent
		return getScaler().scaleDown(resources.stringExtent(string));
	}

	@Override
	public int getLineCap() {
		return lineCap;
	}

	@Override
	public void copyArea(Image image, int x, int y) {

		if (image == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}

		if (image.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		// leaf 6 (PIXEL_COPY, pattern M3-GC-PIXEL-COPY v1, census GF21): the image's area of the surface at this GC's zoom, opaque and
		// unpremultiplied in 24-bit RGB (M3ToSwtImageConverter), replaces the image's pixels at that zoom (BitBlt SRCCOPY); the
		// part outside the surface is left as it was. The donor gave 24-bit data 32-bit masks, kept premultiplied colour,
		// drew a 100 % image in points (twice the size at 200 %) and blended it over the old content
		final Rectangle size = image.getBounds();
		final IRect area = RectangleConverter.createScaledRectangle(getScaler(), x, y, size.width, size.height).toIRect();
		final IRect visible = area.intersect(new IRect(0, 0, surface.getWidth(), surface.getHeight()));
		if (visible == null) {
			return;
		}
		try (final IM3Image copied = surface.makeImageSnapshot(visible)) {
			if (copied != null) {
				replacePixels(image, M3ToSwtImageConverter.convertM3ImageToImageData(copied), visible.getLeft() - area.getLeft(),
						visible.getTop() - area.getTop());
			}
		}
	}

	/* leaf 6: opaque pixels (at this GC's zoom) drawn 1:1 into the image through a stock GC, at the pixel offset */
	private void replacePixels(Image image, ImageData pixels, int offsetX, int offsetY) {
		pixels.alphaData = null;
		final int zoom = getScaler().getNativeZoom();
		final Image copy = new Image(device, (ImageDataProvider) z -> z == zoom ? pixels
				: pixels.scaledTo(Math.max(1, pixels.width * z / zoom), Math.max(1, pixels.height * z / zoom)));
		final GC imageGC = new GC(image);
		try {
			final Point at = getScaler().scaleDown(new Point(offsetX, offsetY));
			imageGC.drawImage(copy, at.x, at.y);
		} finally {
			imageGC.dispose();
			copy.dispose();
		}
	}

	@Override
	public void copyArea(int srcX, int srcY, int width, int height, int destX, int destY) {
		// leaf 6 (PIXEL_COPY, GF20): the source cut to the surface as BitBlt cuts it, the destination moved by the cut; a source
		// outside the surface copies nothing (the donor threw on the null snapshot and misplaced a source cut at the edge)
		final IRect source = RectangleConverter.createScaledRectangle(getScaler(), srcX, srcY, width, height).toIRect();
		final IRect visible = source.intersect(new IRect(0, 0, surface.getWidth(), surface.getHeight()));
		if (visible == null) {
			return;
		}
		try (var copiedArea = surface.makeImageSnapshot(visible)) {
			if (copiedArea != null) {
				surface.getCanvas().drawImage(copiedArea, getScaler().autoScaleUp(destX) + visible.getLeft() - source.getLeft(),
						getScaler().autoScaleUp(destY) + visible.getTop() - source.getTop());
			}
		}
	}

	@Override
	public void copyArea(int srcX, int srcY, int width, int height, int destX, int destY, boolean paint) {

		copyArea(srcX, srcY, width, height, destX, destY);
		if (paint) {
			// Save the canvas state before clipping
			surface.getCanvas().save();
			// Clip to the destination rectangle so only this area is affected
			surface.getCanvas()
			.clipRect(RectangleConverter.createScaledRectangle(getScaler(), srcX, srcY, width, height));
			// Clear the clipped area with transparent background (simulates OS.SW_ERASE)
			surface.getCanvas().clear(M3ColorConverter.convertSWTColorToM3Color(getBackground()));
			// Restore the canvas state
			surface.getCanvas().restore();
			// Trigger redraw for the source area if using SWT Canvas
			m3Extension.redraw(srcX, srcY, width, height, false);
		}
	}

	@Override
	public boolean isClipped() {
		return layer.isClipped();
	}

	@Override
	public int getFillRule() {
		return fillRule;
	}

	@Override
	public void getClipping(Region region) {
		if (region == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		if (region.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}

		// leaf 6 (CLIP_STATE, GF03, GF04): the region becomes the clip in points: a region clip's own rectangles within the frame
		// (untransformed), else the clip box. The donor intersected with the caller's region object (disposed by
		// CTabFolderRenderer after setClipping: ERROR_INVALID_ARGUMENT) and answered nothing for a path clip
		region.intersect(0, 0, 0, 0);
		final int[] rects = layer.regionRects();
		if (rects == null || layer.isTransformed()) {
			region.add(getClipping());
			return;
		}
		final Rect frame = frame();
		IntStream.range(0, rects.length / 4).mapToObj(i -> Rect.makeLTRB(rects[4 * i], rects[4 * i + 1], rects[4 * i + 2],
				rects[4 * i + 3]).intersect(frame)).filter(r -> r != null).forEach(r -> region.add(toPoints(r)));
	}

	@Override
	public int getAdvanceWidth(char ch) {

		final var f = getM3Font();
		final AtomicInteger result = new AtomicInteger();
		result.set(-1);

		paintManager.performDraw(paint -> {
			paint.setAntiAlias(false);
			paint.setMode(PaintMode.FILL);

			final var textWidth = f.measureTextWidth(String.valueOf(ch), paint);
			result.set((int) Math.ceil(textWidth));

		});

		return result.get();

	}

	@Override
	public boolean getAdvanced() {
		// leaf 6 (GC_STATE_RESET, GF05): stock data.gdipGraphics != 0: false until setAdvanced(true) or a call that needs GDI+
		return advanced;
	}

	@Override
	public Pattern getBackgroundPattern() {
		return backgroundPattern;
	}

	@Override
	public int getCharWidth(char ch) {
		return getAdvanceWidth(ch);
	}

	@Override
	public Pattern getForegroundPattern() {
		return foregroundPattern;
	}

	@Override
	public GCData getGCData() {
		// leaf 6 (ARG_CONTRACT, pattern M3-GC-CONTRACT v1, census GF01): stock widget code reads the GC's data (planeDraw in
		// Composite.drawBackgroundInPixels and RetainedHook, gdipGraphics and nativeZoom in Control/Shell.print, uiState): this
		// GC's own, with no GDI+ graphics (0) and no retained plane (-1). The donor answered null (I31 SkiaGC.getGCData)
		data.device = device;
		data.style = style;
		data.nativeZoom = getScaler().getNativeZoom();
		data.alpha = alpha;
		return data;
	}

	@Override
	public int getInterpolation() {
		// leaf 6 (HINT_STATE, GF06): the value set, as the stock GC reads its GDI+ mode back (the donor decoded the sampling: LOW
		// read back HIGH, HIGH read back DEFAULT, the fresh GC NONE)
		return interpolation;
	}

	@Override
	public int[] getLineDash() {
		if (lineDashes == null) {
			return null;
		}
		final int[] lineDashesInt = new int[lineDashes.length];
		for (int i = 0; i < lineDashesInt.length; i++) {
			lineDashesInt[i] = getScaler().autoScaleDownToInt(lineDashes[i]);
		}
		return lineDashesInt;
	}

	@Override
	public int getLineJoin() {
		return lineJoin;
	}

	@Override
	public int getStyle() {
		return style;
	}

	@Override
	public int getTextAntialias() {
		return textAntiAlias;
	}

	@Override
	public void getTransform(Transform transform) {
		if (transform == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		if (transform.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		M3TransformConverter.fromM3Matrix(layer.transform(), transform, getScaler());
	}

	@Override
	public boolean getXORMode() {
		return xorModeActive;
	}

	@Override
	public void setBackgroundPattern(Pattern pattern) {
		if (pattern != null && pattern.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		advanced |= pattern != null;
		this.backgroundPattern = pattern;
	}

	@Override
	public void setClipping(Path path) {
		// leaf 6 (CLIP_STATE, GF16): replaces the clip in the GC layer at the current transform (M3GcLayer.setClip); a disposed
		// path is rejected before the clip changes, as the stock GC does
		if (path != null && path.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		try (final io.github.humbleui.skija.Path m3Path = path == null ? null
				: M3PathConverter.convertSWTPathToM3Path(path, getScaler())) {
			if (m3Path == null) {
				layer.setClip(null, null, null);
				return;
			}
			m3Path.setFillMode(fillRule == SWT.FILL_EVEN_ODD ? PathFillMode.EVEN_ODD : PathFillMode.WINDING);
			layer.setClip(canvas -> canvas.clipPath(m3Path, ClipMode.INTERSECT, true), m3Path.computeTightBounds(), null);
		}
	}

	@Override
	public void setClipping(Rectangle rect) {
		// leaf 6 (CLIP_STATE, GF02, GF16): the rectangle replaces the clip in the GC layer, whatever setTransform did since
		if (rect == null) {
			layer.setClip(null, null, null);
			return;
		}
		final Rect local = RectangleConverter.createScaledRectangle(getScaler(), rect);
		layer.setClip(canvas -> canvas.clipRect(local), local, null);
	}

	@Override
	public void setClipping(Region region) {
		// leaf 6 (CLIP_STATE, GF03, GF18): the region's rectangles at this GC's zoom, copied now from the region's own handle
		// (M3RegionCalculator), so a later change or dispose of the caller's region does not reach the clip
		if (region == null) {
			layer.setClip(null, null, null);
			return;
		}
		if (region.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		final int[] rects = M3RegionCalculator.rectangles(region, getScaler().getNativeZoom());
		layer.setClip(canvas -> M3RegionCalculator.clip(canvas, rects), M3RegionCalculator.bounds(rects), rects);
	}

	@Override
	public void setFillRule(int rule) {
		if (rule != SWT.FILL_EVEN_ODD && rule != SWT.FILL_WINDING) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		this.fillRule = rule;
	}

	@Override
	public void setForegroundPattern(Pattern pattern) {
		if (pattern != null && pattern.isDisposed()) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		advanced |= pattern != null;
		this.foregroundPattern = pattern;
	}

	@Override
	public void setInterpolation(int interpolation) {

		// GDI | Skia | description
		// NearestNeighbor SKFilterMode.Nearest hart pixels
		// Low / Bilinear SKFilterMode.Linear simple linear
		// High / Bicubic SKCubicResampler.Mitchell high quality
		// HighQualityBicubic SKCubicResampler.CatmullRom maximum sharp, cubic
		// interpolation

		// leaf 6 (HINT_STATE, GF06): DEFAULT samples nearest like the untouched GC (stock without GDI+: StretchBlt COLORONCOLOR);
		// the donor mapped DEFAULT to Mitchell, a second default (departure from I31 SkiaGC.setInterpolation)
		switch (interpolation) {
		case SWT.NONE, SWT.DEFAULT -> this.interpolationMode = SamplingMode.DEFAULT; // Nearest neighbor
		case SWT.LOW -> this.interpolationMode = SamplingMode.LINEAR;
		case SWT.HIGH -> this.interpolationMode = SamplingMode.CATMULL_ROM;
		default -> SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		advanced |= interpolation != SWT.DEFAULT;
		this.interpolation = interpolation;
	}

	@Override
	public void setLineAttributes(LineAttributes attributes) {
		if (attributes == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}

		// leaf 6 (STROKE_SCALE, GF14): the width stays in points, the unit of lineWidth everywhere (setLineWidth, getLineWidth,
		// M3PaintManager scales it once); the caller's object is not written (the stock GC copies it). A width <= 0 strokes 1
		// point as the donor did (GF13, hairline semantics, not in this leaf)
		final float width = attributes.width <= 0 ? 1 : attributes.width;
		setLineAttributesInPixels(attributes, width);
	}

	private void setLineAttributesInPixels(LineAttributes attributes, float scaledWidth) {
		if (isDisposed()) {
			SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
		}
		boolean changed = false;
		if (scaledWidth != this.lineWidth) {
			this.lineWidth = (int) Math.ceil(scaledWidth);
			changed = true;
		}
		// Handle line style with validation
		int lineStyle = attributes.style;
		if (lineStyle != this.lineStyle) {
			switch (lineStyle) {
			case SWT.NONE:
				lineStyle = SWT.LINE_SOLID;
				break;
			case SWT.LINE_SOLID:
			case SWT.LINE_DASH:
			case SWT.LINE_DOT:
			case SWT.LINE_DASHDOT:
			case SWT.LINE_DASHDOTDOT:
				break;
			case SWT.LINE_CUSTOM:
				if (attributes.dash == null) {
					lineStyle = SWT.LINE_SOLID;
				}
				break;
			default:
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			}
			this.lineStyle = lineStyle;
			changed = true;
		}
		// Handle line cap with validation
		final int cap = attributes.cap;
		if (cap != this.lineCap) {
			switch (cap) {
			case SWT.CAP_FLAT:
			case SWT.CAP_ROUND:
			case SWT.CAP_SQUARE:
				break;
			default:
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			}
			this.lineCap = cap;
			changed = true;
		}
		// Handle line join with validation
		final int join = attributes.join;
		if (join != this.lineJoin) {
			switch (join) {
			case SWT.JOIN_MITER:
			case SWT.JOIN_ROUND:
			case SWT.JOIN_BEVEL:
				break;
			default:
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			}
			this.lineJoin = join;
			changed = true;
		}
		// Handle dash pattern with validation and DPI scaling
		final float[] dashes = attributes.dash;
		final float[] currentDashes = this.lineDashes;

		if (dashes != null && dashes.length > 0) {
			boolean dashesChanged = currentDashes == null || currentDashes.length != dashes.length;
			final float[] newDashes = new float[dashes.length];

			for (int i = 0; i < dashes.length; i++) {
				final float dash = dashes[i];
				if (dash <= 0) {
					SWT.error(SWT.ERROR_INVALID_ARGUMENT);
				}

				// Scale dash values for DPI
				newDashes[i] = getScaler().autoScaleUp(dash);

				if (!dashesChanged && currentDashes != null && currentDashes[i] != newDashes[i]) {
					dashesChanged = true;
				}
			}
			if (dashesChanged) {
				this.lineDashes = newDashes;
				changed = true;
			}
		} else {
			// Clear dash pattern
			if (currentDashes != null && currentDashes.length > 0) {
				this.lineDashes = null;
				changed = true;
			}
		}
		// Handle dash offset - store for use in createPathEffectForLineStyle()
		final float dashOffset = attributes.dashOffset;
		if (this.dashOffset != dashOffset) {
			this.dashOffset = dashOffset;
			changed = true;
		}
		// Handle miter limit - store for use in performDraw()
		final float miterLimit = attributes.miterLimit;
		if (this.miterLimit != miterLimit) {
			this.miterLimit = miterLimit;
			changed = true;
		}
		this.miterLimitSet = true;
		if (!changed) {
			return;
		}
	}

	@Override
	public void setLineCap(int cap) {
		if (cap != SWT.CAP_FLAT && cap != SWT.CAP_ROUND && cap != SWT.CAP_SQUARE) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		this.lineCap = cap;
	}

	@Override
	public void setLineDash(int[] dashes) {
		if (dashes != null && dashes.length > 0) {
			boolean changed = this.lineStyle != SWT.LINE_CUSTOM || lineDashes == null
					|| lineDashes.length != dashes.length;
			final float[] newDashes = new float[dashes.length];
			for (int i = 0; i < dashes.length; i++) {
				if (dashes[i] <= 0) {
					SWT.error(SWT.ERROR_INVALID_ARGUMENT);
				}
				newDashes[i] = getScaler().autoScaleUp(dashes[i]);
				if (!changed && lineDashes != null && lineDashes[i] != newDashes[i]) {
					changed = true;
				}
			}
			if (!changed) {
				return;
			}
			this.lineDashes = newDashes;
			this.lineStyle = SWT.LINE_CUSTOM;
		} else {
			if (this.lineStyle == SWT.LINE_SOLID && (lineDashes == null || lineDashes.length == 0)) {
				return;
			}
			this.lineDashes = null;
			this.lineStyle = SWT.LINE_SOLID;
		}
	}

	@Override
	public void setLineJoin(int join) {
		if (join != SWT.JOIN_MITER && join != SWT.JOIN_ROUND && join != SWT.JOIN_BEVEL) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		this.lineJoin = join;
	}

	@Override
	public void setXORMode(boolean xor) {
		this.xorModeActive = xor;
	}

	@Override
	public void setTextAntialias(int antialias) {
		// leaf 6 (HINT_STATE, GF06): validated as the stock GC does (DEFAULT, OFF, ON)
		if (antialias != SWT.DEFAULT && antialias != SWT.ON && antialias != SWT.OFF) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		advanced |= antialias != SWT.DEFAULT;
		this.textAntiAlias = antialias;
	}

	@Override
	public Color getBackground() {
		return resources.getBackground();
	}

	@Override
	public Device getDevice() {
		return device;
	}

	@Override
	public org.eclipse.swt.graphics.Font getFont() {
		return this.resources.getFont();
	}

	@Override
	public boolean isDisposed() {
		return surface.isClosed();
	}

	@Override
	public FontMetrics getFontMetrics() {

		final var font = getM3Font();
		final var m = font.getMetrics();
		final var fe = new FontMetricsExtension(new M3ExternalFontMetrics(m, font.getSize(), getScaler()));
		return fe;
	}

	@Override
	public Drawable getDrawable() {
		return drawable;
	}

	@Override
	public void textLayoutDraw(TextLayout textLayout, GC gc, int xInPoints, int yInPoints, int selectionStart,
			int selectionEnd, Color selectionForeground, Color selectionBackground, int flags) {

		final var rectangle = textLayout.getBounds();

		if (rectangle.width <= 0 || rectangle.height <= 0) {
			return;
		}

		Image i = null;
		GC nativeGC = null;
		try {
			i = new Image(device, rectangle.width, rectangle.height);
			nativeGC = new GC(i);

			textLayout.draw(nativeGC, 0, 0, selectionStart, selectionEnd, selectionForeground, selectionBackground,
					flags);

			drawImage(i, rectangle.x, rectangle.y, rectangle.width, rectangle.height, xInPoints, yInPoints,
					rectangle.width, rectangle.height);
		} finally {
			if (nativeGC != null) {
				nativeGC.dispose();
			}
			if (i != null) {
				i.dispose();
			}
		}

	}

	IM3Resources getM3Resources() {
		return this.resources;
	}

	IM3Surface getM3Surface() {
		return this.surface;
	}

	IM3CanvasExtension getM3Extension() {
		return this.m3Extension;
	}

	float[] getLineDashes() {
		return this.lineDashes;
	}

	/* leaf 5: the dash phase in pixels; the stock GC offsets only a custom pattern (win32 GC.getPen: LINE_CUSTOM, GDI+ dash offset) */
	float getDashPhase() {
		return lineStyle == SWT.LINE_CUSTOM ? getScaler().autoScaleUp(dashOffset) : 0f;
	}

	/* leaf 5: whether setLineAttributes gave a miter limit (the stroke then uses it) */
	boolean isMiterLimitSet() {
		return miterLimitSet;
	}

	/* leaf 5: the miter limit of the line attributes (stock default 10) */
	float getMiterLimit() {
		return miterLimit;
	}

}
