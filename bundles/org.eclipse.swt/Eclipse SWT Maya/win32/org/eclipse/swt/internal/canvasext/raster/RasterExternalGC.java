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

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.FontMetricsExtension;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageContent;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.PathData;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.canvasext.IExternalGC;
import org.eclipse.swt.internal.gdip.Gdip;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.RECT;

import org.eclipse.swt.internal.maya.ClipMode;
import org.eclipse.swt.internal.maya.EncoderPNG;
import org.eclipse.swt.internal.maya.FilterMipmap;
import org.eclipse.swt.internal.maya.FilterMode;
import org.eclipse.swt.internal.maya.FilterTileMode;
import org.eclipse.swt.internal.maya.GradientStyle;
import org.eclipse.swt.internal.maya.Matrix33;
import org.eclipse.swt.internal.maya.MipmapMode;
import org.eclipse.swt.internal.maya.Paint;
import org.eclipse.swt.internal.maya.PaintMode;
import org.eclipse.swt.internal.maya.PathEffect;
import org.eclipse.swt.internal.maya.PathFillMode;
import org.eclipse.swt.internal.maya.SamplingMode;
import org.eclipse.swt.internal.maya.Shader;
import org.eclipse.swt.internal.maya.types.IRect;
import org.eclipse.swt.internal.maya.types.Rect;

public class RasterExternalGC implements IExternalGC {

	private static boolean logImageNullError = true;
	private final IRasterSurface surface;
	private int alpha = 255;
	private int antialias;
	private boolean xorModeActive;
	private int lineWidth = 1;
	private int lineCap = SWT.CAP_FLAT;
	private int lineStyle = SWT.LINE_SOLID;
	private Pattern foregroundPattern;
	private Pattern backgroundPattern;

	private int fillRule = SWT.FILL_EVEN_ODD;
	private int lineJoin = SWT.JOIN_MITER;
	private float[] lineDashes;
	// M3 (GC seam, leaf 5; pattern M3-GC-STROKE v1): the stroke state the donor stored but never applied, with the stock
	// defaults (GCData: dash offset 0, miter limit 10); the dash phase reaches LINE_CUSTOM patterns (RasterPaintManager), the
	// miter limit every stroke once setLineAttributes ran (until then the paint keeps the donor's Skija miter, 4, and
	// drawPath's 1000, which the adopted tests pin)
	private float dashOffset;
	private float miterLimit = 10;
	private boolean miterLimitSet;

	private final Drawable drawable;
	private final Device device;

	private Matrix33 currentTransform = Matrix33.IDENTITY;
	// A host matrix is deliberately left intact until this GC installs its own.
	// IRasterCanvas cannot query an inherited matrix; identity is the public GC default.
	private boolean explicitTransform;

	private SamplingMode interpolationMode = SamplingMode.DEFAULT;

	private boolean isClipSet;
	private boolean savedCanvasState;
	// An owned SWT snapshot and a device-coordinate RGNDATA snapshot. Never replay
	// RegionLog here: its operands may refer to caller Regions already disposed.
	private Region currentClipRegion;
	private int[] currentClipData;

	private final IRasterCanvasExtension mayaExtension;
	private final IRasterResources resources;
	private final int style;
	private int textAntiAlias = SWT.ON;

	private int savedCanvasCount;
	private final RasterPaintManager paintManager;
	// M3 (efficiency): the Skia copy of each image drawn since the GC was made or disposed, by zoom and content changes
	// (ImageContent), so an image drawn many times in one paint (the icon of every row) is copied from its bitmap once.
	// A copy is never kept while a GC draws into the image; the copies are closed at dispose and above the limit.
	private final Map<Image, CachedImage> images = new IdentityHashMap<>();
	static final int IMAGE_CACHE_LIMIT = 64;

	private record CachedImage(int zoom, int changes, IRasterImage raster) {
	}
	private final DpiScalerUtil dpiScalerUtil;

	@SuppressWarnings("this-escape") // the paint manager and the clip keep this GC; nothing subclasses it
	public RasterExternalGC(IRasterCanvasExtension exst, int style) {
		this.drawable = exst.getDrawable();
		device = exst.getDevice();
		this.surface = exst.getSurface();
		this.mayaExtension = exst;
		this.resources = mayaExtension.getResources();
		this.style = style;
		this.paintManager = new RasterPaintManager(this);
		this.dpiScalerUtil = new DpiScalerUtil(this.resources.getScaler());

	}

	/* the kept Skia copy of an image at a zoom, made now if needed; null while a GC draws into the image */
	private IRasterImage cachedImage(Image image, int zoom) {
		if (ImageContent.isDrawnInto(image)) {
			return null;
		}
		final int changes = ImageContent.changes(image);
		final CachedImage kept = images.get(image);
		if (kept != null && kept.zoom() == zoom && kept.changes() == changes) {
			return kept.raster();
		}
		if (kept != null) {
			images.remove(image).raster().close();
		} else if (images.size() >= IMAGE_CACHE_LIMIT) {
			closeImages();
		}
		final IRasterImage raster = SwtToRasterImageConverter.convertSWTImageToMayaImage(image, zoom, resources);
		images.put(image, new CachedImage(zoom, changes, raster));
		return raster;
	}

	private void closeImages() {
		images.values().forEach(kept -> kept.raster().close());
		images.clear();
	}

	@Override
	public void dispose() {

		closeImages();
		resources.resetBaseColors();
		// Only this GC's private frame is removed; the host's matrix and clip survive.
		if (savedCanvasState) {
			surface.getCanvas().restoreToCount(savedCanvasCount);
			savedCanvasState = false;
		}
		// Drawn planes reuse this GC after each owner-draw event.
		releaseClipRegion();
		isClipSet = false;
		currentClipData = null;
		currentTransform = Matrix33.IDENTITY;
		explicitTransform = false;

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

	private static void logImageNull(int[] positionData) {
		if (logImageNullError) {
			new IllegalArgumentException(
					"Image argument is null. Position and size data: " + java.util.Arrays.toString(positionData)).printStackTrace(); //$NON-NLS-1$
			logImageNullError = false;
		}
	}

	@Override
	public void drawImage(Image image, int x, int y) {

		if (image == null) {
			logImageNull(new int[] { x, y });
			return;
		}

		final var imgBounds = image.getBounds();
		drawImage(image, 0, 0, imgBounds.width, imgBounds.height, x, y, imgBounds.width, imgBounds.height);
	}

	@Override
	public void drawImage(Image image, int destX, int destY, int destWidth, int destHeight) {

		if (image == null) {
			logImageNull(new int[] { destX, destY, destWidth, destHeight });
			return;
		}

		drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height, destX, destY, destWidth, destHeight);

	}

	@Override
	public void drawImage(Image image, int srcX, int srcY, int srcWidth, int srcHeight, int destX, int destY,
			int destWidth, int destHeight) {

		if (image == null) {
			logImageNull(new int[] { srcX, srcY, srcWidth, srcHeight, destX, destY, destWidth, destHeight });
			return;
		}

		int sizeFactorInImage = Math.round(Math.max((float) destWidth / srcWidth, (float) destHeight / srcHeight));

		if (sizeFactorInImage == 0) {
			sizeFactorInImage = 1;
		}
		final IRasterCanvas canvas = surface.getCanvas();
		final int fac = sizeFactorInImage;

		paintManager.performDraw(paint -> {
			paint.setAlpha(alpha);
			paint.setAntiAlias(true);
			final int zoom = getScaler().autoScaleUp(100 * fac);
			final IRasterImage cached = cachedImage(image, zoom);
			final IRasterImage raster = cached != null ? cached
					: SwtToRasterImageConverter.convertSWTImageToMayaImage(image, zoom, resources);
			try {
				canvas.drawImageRect(raster,
						RectangleConverter.createScaledRectangle(getScaler(), srcX * fac, srcY * fac, srcWidth * fac,
								srcHeight * fac),
						RectangleConverter.createScaledRectangle(getScaler(), destX, destY, destWidth, destHeight),
						this.interpolationMode, paint, false);
			} finally {
				if (cached == null) {
					raster.close();
				}
			}
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
	static void writeFile(String str, org.eclipse.swt.internal.maya.Image image) {
		final byte[] imageBytes = EncoderPNG.encode(image).getBytes();

		final File f = new File(str);
		if (f.exists()) {
			f.delete();
		}

		try (FileOutputStream fis = new FileOutputStream(f)) {
			fis.write(imageBytes);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void drawLine(int x1, int y1, int x2, int y2) {
		final float scaledOffsetValue = RectangleConverter.getScaledOffsetValue(getScaler(), lineWidth);

		final var scaler = this.dpiScalerUtil;
		paintManager.performDraw(paint -> surface.getCanvas().drawLine(scaler.autoScaleUp(x1) + scaledOffsetValue,
				scaler.autoScaleUp(y1) + scaledOffsetValue, scaler.autoScaleUp(x2) + scaledOffsetValue,
				scaler.autoScaleUp(y2) + scaledOffsetValue, paint));
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

	private org.eclipse.swt.internal.maya.Font getMayaFont() {
		return this.resources.getMayaFont();
	}

	private void executeTextDraw(String inputText, int flags, int x, int y) {

		if (this.surface.getWidth() < x || this.surface.getHeight() < y) {
			return;
		}

		final String splits[] = this.resources.getTextSplits(inputText, flags);
		RasterTextDrawing.drawText(this, splits, flags, x, y);
	}

	RasterPaintManager getPaintManager() {
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
			try (org.eclipse.swt.internal.maya.Path mayaPath = RasterPathConverter.convertSWTPathToMayaPath(path,
					getScaler())) {
				if (mayaPath == null) {
					return;
				}
				// the stroke miter on GC is quite high (the donor's default; leaf 5: a miter limit setLineAttributes gave wins,
				// RasterPaintManager.performDraw applied it)
				if (!miterLimitSet) {
					paint.setStrokeMiter(1000);
				}
				surface.getCanvas().drawPath(mayaPath, paint);
			}
		});
	}

	@Override
	public void drawPoint(int x, int y) {
		paintManager.performDrawFilled(paint -> {
			// points have always one pixel no matter the zoom...
			// fill mode is right for it, not stroke, so we use performDrawFilled
			paint.setColor(RasterColorConverter.convertSWTColorToMayaColor(getForeground()));
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
			try (org.eclipse.swt.internal.maya.Path pathBuilder = new org.eclipse.swt.internal.maya.Path()) {
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
		drawText(string, x, y, isTransparent);
	}

	@Override
	public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		paintManager.performDrawFilled(paint -> surface.getCanvas().drawArc(getScaler().autoScaleUp(x),
				getScaler().autoScaleUp(y), getScaler().autoScaleUp(x + width), getScaler().autoScaleUp(y + height),
				-startAngle, -arcAngle, true, paint));
	}

	@Override
	public void fillGradientRectangle(int x, int y, int width, int height, boolean vertical) {

		boolean swapColors = false;
		if (width < 0) {
			x = Math.max(0, x + width);
			width = -width;
			if (!vertical) {
				swapColors = true;
			}
		}
		if (height < 0) {
			y = Math.max(0, y + height);
			height = -height;
			if (vertical) {
				swapColors = true;
			}
		}
		final int x2 = vertical ? x : x + width;
		final int y2 = vertical ? y + height : y;

		final Rect rect = RectangleConverter.createScaledRectangle(getScaler(), x, y, width, height);
		int fromColor = RasterColorConverter.convertSWTColorToMayaColor(getForeground());
		int toColor = RasterColorConverter.convertSWTColorToMayaColor(getBackground());
		if (fromColor == toColor) {
			paintManager.performDrawFilled(paint -> surface.getCanvas().drawRect(rect, paint));
			return;
		}
		if (swapColors) {
			final int tempColor = fromColor;
			fromColor = toColor;
			toColor = tempColor;
		}

		final var s = getScaler();
		performDrawGradientFilled(paint -> surface.getCanvas().drawRect(rect, paint), s.autoScaleUp(x),
				s.autoScaleUp(y), s.autoScaleUp(x2), s.autoScaleUp(y2), fromColor, toColor);
	}

	private void performDrawGradientFilled(Consumer<Paint> operations, int x, int y, int x2, int y2, int fromColor,
			int toColor) {
		paintManager.performDraw(paint -> {

			try (Shader gradient = convertGradientRectangleToMayaShader(x, y, x2 - x, y2 - y, false, fromColor,
					toColor)) {
				paint.setShader(gradient);
				paint.setAntiAlias(true);
				paint.setMode(PaintMode.FILL);
				operations.accept(paint);
			}
		});
	}

	private static Shader convertGradientRectangleToMayaShader(int x, int y, int width, int height, boolean vertical,
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
			try (org.eclipse.swt.internal.maya.Path mayaPath = RasterPathConverter.convertSWTPathToMayaPath(path,
					getScaler())) {
				if (mayaPath == null) {
					return;
				}
				mayaPath
				.setFillMode(this.fillRule == SWT.FILL_EVEN_ODD ? PathFillMode.EVEN_ODD : PathFillMode.WINDING);
				paint.setAntiAlias(this.antialias == SWT.ON);
				surface.getCanvas().drawPath(mayaPath, paint);
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

			try (var path = RasterPathConverter.createPath(pointArray, fillRule, getScaler())) {
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
		Matrix33 replacement = RasterTransformConverter.toMayaMatrix(transform, getScaler());
		if (explicitTransform && Arrays.equals(currentTransform.getMat(), replacement.getMat())) {
			return;
		}
		IRasterCanvas canvas = surface.getCanvas();
		if (!savedCanvasState) {
			savedCanvasCount = canvas.getSaveCount();
			canvas.save();
			savedCanvasState = true;
		}
		canvas.setMatrix(replacement);
		currentTransform = replacement;
		explicitTransform = true;
	}

	@Override
	public void setAlpha(int alpha) {
		alpha = alpha & 0xFF;
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
		this.antialias = antialias;
	}

	@Override
	public void setAdvanced(boolean enable) {
		// Nothing to do...
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
		final LineAttributes attributes = getLineAttributesInPixels();
		attributes.width = getScaler().autoScaleDown(attributes.width);
		if (attributes.dash != null) {
			attributes.dash = getScaler().autoScaleDown(attributes.dash);
		}
		return attributes;
	}

	LineAttributes getLineAttributesInPixels() {
		return new LineAttributes(lineWidth, lineCap, lineJoin, lineStyle, lineDashes, dashOffset, miterLimit);
	}

	@Override
	public Rectangle getClipping() {
		long handle = clippingInUserCoordinates();
		try {
			RECT bounds = new RECT();
			OS.GetRgnBox(handle, bounds);
			return new Rectangle(bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top);
		} finally {
			OS.DeleteObject(handle);
		}
	}

	@Override
	public Point stringExtent(String string) {
		return textExtent(string);
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

		final IRasterImage mayaImage = SwtToRasterImageConverter.convertSWTImageToMayaImage(image,
				getScaler().getNativeZoom(), resources);
		try (final IRasterImage copiedArea = surface.makeImageSnapshot(RectangleConverter
				.createScaledRectangle(getScaler(), x, y, mayaImage.getWidth(), mayaImage.getHeight()).toIRect())) {

			if (copiedArea == null) {
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			}

			try (final IRasterSurface imageSurface = surface.makeSurface(mayaImage.getWidth(), mayaImage.getHeight())) {
				imageSurface.getCanvas().drawImage(copiedArea, 0, 0);
				try (final IRasterImage snapshot = imageSurface.makeImageSnapshot()) {
					final ImageData imgData = RasterToSwtImageConverter.convertMayaImageToImageData(snapshot);
					Image i = null;
					GC gc = null;
					try {
						i = new Image(device, imgData);
						gc = new GC(image);
						gc.drawImage(i, 0, 0);
					} finally {
						if (gc != null) {
							gc.dispose();
						}
						if (i != null) {
							i.dispose();
						}
					}
				}
			}
		}
	}

	@Override
	public void copyArea(int srcX, int srcY, int width, int height, int destX, int destY) {
		try (var copiedArea = surface.makeImageSnapshot(
				RectangleConverter.createScaledRectangle(getScaler(), srcX, srcY, width, height).toIRect())) {
			surface.getCanvas().drawImage(copiedArea, getScaler().autoScaleUp(destX), getScaler().autoScaleUp(destY));
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
			surface.getCanvas().clear(RasterColorConverter.convertSWTColorToMayaColor(getBackground()));
			// Restore the canvas state
			surface.getCanvas().restore();
			// Trigger redraw for the source area if using SWT Canvas
			mayaExtension.redraw(srcX, srcY, width, height, false);
		}
	}

	@Override
	public boolean isClipped() {
		return isClipSet;
	}

	@Override
	public int getFillRule() {
		return fillRule;
	}

	@Override
	public void getClipping(Region region) {
		if (region == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (region.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		long handle = clippingInUserCoordinates();
		try {
			int[] data = regionData(handle);
			region.intersect(0, 0, 0, 0);
			for (int index = data[0] / Integer.BYTES, end = index + data[2] * 4; index < end; index += 4) {
				region.add(data[index], data[index + 1], data[index + 2] - data[index], data[index + 3] - data[index + 1]);
			}
		} finally {
			OS.DeleteObject(handle);
		}
	}

	@Override
	public int getAdvanceWidth(char ch) {

		final var f = getMayaFont();
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
		return true;
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
		return null;
	}

	@Override
	public int getInterpolation() {
		if (interpolationMode == SamplingMode.DEFAULT) {
			return SWT.NONE;
		}
		if (interpolationMode == SamplingMode.LINEAR) {
			return SWT.HIGH;
		}
		if (interpolationMode instanceof final FilterMipmap fm) {
			if (fm.getFilterMode() == FilterMode.LINEAR && fm.getMipmapMode() == MipmapMode.LINEAR) {
				return SWT.LOW;
			}
		}
		return SWT.DEFAULT;
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
		RasterTransformConverter.fromMayaMatrix(currentTransform, transform, getScaler());
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
		this.backgroundPattern = pattern;
	}

	@Override
	public void setClipping(Path path) {
		if (path == null) {
			clearClipping();
			return;
		}
		if (path.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		// Prepare all resources before replacing the established clip.
		int[] data = pathClipData(path);
		try (org.eclipse.swt.internal.maya.Path mayaPath = RasterPathConverter.convertSWTPathToMayaPath(path, getScaler())) {
			mayaPath.setFillMode(fillRule == SWT.FILL_EVEN_ODD ? PathFillMode.EVEN_ODD : PathFillMode.WINDING);
			replaceClipping(data, null, canvas -> canvas.clipPath(mayaPath, ClipMode.INTERSECT, true));
		}
	}

	@Override
	public void setClipping(Rectangle rect) {
		if (rect == null) {
			clearClipping();
			return;
		}
		Rect scaled = RectangleConverter.createScaledRectangle(getScaler(), rect);
		Rect normalized = new Rect(Math.min(scaled.getLeft(), scaled.getRight()), Math.min(scaled.getTop(), scaled.getBottom()),
				Math.max(scaled.getLeft(), scaled.getRight()), Math.max(scaled.getTop(), scaled.getBottom()));
		long handle = checkedRegion(OS.CreateRectRgn((int) normalized.getLeft(), (int) normalized.getTop(),
				(int) normalized.getRight(), (int) normalized.getBottom()));
		try {
			int[] data = transformedRegionData(handle, currentTransform);
			replaceClipping(data, null, canvas -> canvas.clipRect(normalized));
		} finally {
			OS.DeleteObject(handle);
		}
	}

	@Override
	public void setClipping(Region region) {
		if (region == null) {
			clearClipping();
			return;
		}
		if (region.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		Region owned = new Region(device);
		boolean installed = false;
		try {
			// Region.add snapshots SWT's native operations, including holes. Only
			// its actual native region is read, never its auxiliary RegionLog.
			owned.add(region);
			int[] data = transformedRegionData(Region.win32_getHandle(owned, getScaler().getNativeZoom()), currentTransform);
			if (currentClipRegion != null && Arrays.equals(currentClipData, data)) {
				// Equal device geometry needs no new Skia resource or canvas call.
				releaseClipRegion();
				currentClipRegion = owned;
				installed = true;
				return;
			}
			try (org.eclipse.swt.internal.maya.Region clip = new org.eclipse.swt.internal.maya.Region()) {
				IRect[] rectangles = new IRect[data[2]];
				for (int i = 0, offset = data[0] / Integer.BYTES; i < rectangles.length; i++, offset += 4) {
					rectangles[i] = new IRect(data[offset], data[offset + 1], data[offset + 2], data[offset + 3]);
				}
				clip.setRects(rectangles);
				// Skia clipRegion ignores the CTM, so the snapshot is already mapped
				// into device coordinates. Negative coordinates need no special clip.
				replaceClipping(data, owned, canvas -> canvas.clipRegion(clip));
				installed = true;
			}
		} finally {
			if (!installed) owned.dispose();
		}
	}

	private void clearClipping() {
		if (isClipSet) replaceClipping(null, null, canvas -> { });
	}

	private void replaceClipping(int[] data, Region owned, Consumer<IRasterCanvas> applyClip) {
		IRasterCanvas canvas = surface.getCanvas();
		if (savedCanvasState) {
			canvas.restoreToCount(savedCanvasCount);
		} else {
			// Reused GCs can be entered under a different host frame/row clip.
			savedCanvasCount = canvas.getSaveCount();
		}
		canvas.save();
		savedCanvasState = true;
		if (explicitTransform) canvas.setMatrix(currentTransform);
		applyClip.accept(canvas);
		releaseClipRegion();
		currentClipRegion = owned;
		currentClipData = data;
		isClipSet = data != null;
	}

	private void releaseClipRegion() {
		if (currentClipRegion != null) {
			currentClipRegion.dispose();
			currentClipRegion = null;
		}
	}

	private static long checkedRegion(long handle) {
		if (handle == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		return handle;
	}

	private static int[] regionData(long handle) {
		int bytes = OS.GetRegionData(handle, 0, null);
		if (bytes == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		int[] data = new int[bytes / Integer.BYTES];
		if (OS.GetRegionData(handle, bytes, data) == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		return data;
	}

	private static int[] transformedRegionData(long handle, Matrix33 transform) {
		int[] data = regionData(handle);
		float[] m = transform.getMat();
		if (Arrays.equals(m, Matrix33.IDENTITY.getMat())) return data;
		long mapped = checkedRegion(OS.ExtCreateRegion(new float[] { m[0], m[3], m[1], m[4], m[2], m[5] },
				data.length * Integer.BYTES, data));
		try {
			return regionData(mapped);
		} finally {
			OS.DeleteObject(mapped);
		}
	}

	private long clippingInUserCoordinates() {
		// The host's real (possibly nonrectangular) clip and undisclosed matrix
		// remain on its saved frame. IRasterCanvas cannot query that geometry;
		// query coordinates track the transforms explicitly installed by this GC.
		// RasterPlaneCanvasExtension exposes damage bounds in device pixels.
		Rectangle bounds = mayaExtension.getClientArea();
		long handle = checkedRegion(OS.CreateRectRgn(bounds.x, bounds.y,
				bounds.x + bounds.width, bounds.y + bounds.height));
		try {
			if (currentClipData != null) {
				long clip = checkedRegion(OS.ExtCreateRegion(null, currentClipData.length * Integer.BYTES, currentClipData));
				try {
					OS.CombineRgn(handle, handle, clip, OS.RGN_AND);
				} finally {
					OS.DeleteObject(clip);
				}
			}
			float[] m = currentTransform.getMat();
			if (getScaler().getNativeZoom() == 100 && Arrays.equals(m, Matrix33.IDENTITY.getMat())) {
				long result = handle;
				handle = 0;
				return result;
			}
			double determinant = (double) m[0] * m[4] - (double) m[1] * m[3];
			if (determinant == 0 || !Double.isFinite(determinant)) {
				return checkedRegion(OS.CreateRectRgn(0, 0, 0, 0));
			}
			double scale = 100.0 / getScaler().getNativeZoom() / determinant;
			float[] inverse = { (float) (m[4] * scale), (float) (-m[3] * scale),
					(float) (-m[1] * scale), (float) (m[0] * scale),
					(float) (((double) m[1] * m[5] - (double) m[4] * m[2]) * scale),
					(float) (((double) m[3] * m[2] - (double) m[0] * m[5]) * scale) };
			for (float element : inverse) {
				if (!Float.isFinite(element)) return checkedRegion(OS.CreateRectRgn(0, 0, 0, 0));
			}
			int[] data = regionData(handle);
			return checkedRegion(OS.ExtCreateRegion(inverse, data.length * Integer.BYTES, data));
		} finally {
			if (handle != 0) OS.DeleteObject(handle);
		}
	}

	private int[] pathClipData(Path path) {
		// Query geometry is rasterized once by SWT's existing GDI+ bindings;
		// rendering continues through Maya's antialiased path clipping.
		long nativePath = Gdip.GraphicsPath_new(fillRule == SWT.FILL_EVEN_ODD ? Gdip.FillModeAlternate : Gdip.FillModeWinding);
		if (nativePath == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		long matrix = 0, region = 0, dc = 0, graphics = 0, handle = 0;
		try {
			PathData data = path.getPathData();
			float[] points = getScaler().autoScaleUp(data.points);
			float x = 0, y = 0, startX = 0, startY = 0;
			int index = 0;
			for (byte type : data.types) {
				switch (type) {
				case SWT.PATH_MOVE_TO:
					Gdip.GraphicsPath_StartFigure(nativePath);
					x = startX = points[index++]; y = startY = points[index++];
					break;
				case SWT.PATH_LINE_TO:
					Gdip.GraphicsPath_AddLine(nativePath, x, y, points[index], points[index + 1]);
					x = points[index++]; y = points[index++];
					break;
				case SWT.PATH_CUBIC_TO:
					Gdip.GraphicsPath_AddBezier(nativePath, x, y, points[index], points[index + 1], points[index + 2],
							points[index + 3], points[index + 4], points[index + 5]);
					x = points[index + 4]; y = points[index + 5]; index += 6;
					break;
				case SWT.PATH_QUAD_TO:
					float controlX = points[index++], controlY = points[index++];
					float endX = points[index++], endY = points[index++];
					Gdip.GraphicsPath_AddBezier(nativePath, x, y, x + (controlX - x) * 2 / 3, y + (controlY - y) * 2 / 3,
							endX + (controlX - endX) * 2 / 3, endY + (controlY - endY) * 2 / 3, endX, endY);
					x = endX; y = endY;
					break;
				case SWT.PATH_CLOSE:
					Gdip.GraphicsPath_CloseFigure(nativePath);
					x = startX; y = startY;
					break;
				default:
					throw new IllegalArgumentException("Unknown SWT path segment"); //$NON-NLS-1$
				}
			}
			float[] m = currentTransform.getMat();
			matrix = Gdip.Matrix_new(m[0], m[3], m[1], m[4], m[2], m[5]);
			if (matrix == 0) SWT.error(SWT.ERROR_NO_HANDLES);
			Gdip.GraphicsPath_Transform(nativePath, matrix);
			region = Gdip.Region_newGraphicsPath(nativePath);
			if (region == 0) SWT.error(SWT.ERROR_NO_HANDLES);
			dc = OS.CreateCompatibleDC(0);
			if (dc == 0) SWT.error(SWT.ERROR_NO_HANDLES);
			graphics = Gdip.Graphics_new(dc);
			if (graphics == 0) SWT.error(SWT.ERROR_NO_HANDLES);
			handle = checkedRegion(Gdip.Region_GetHRGN(region, graphics));
			return regionData(handle);
		} finally {
			if (handle != 0) OS.DeleteObject(handle);
			if (graphics != 0) Gdip.Graphics_delete(graphics);
			if (dc != 0) OS.DeleteDC(dc);
			if (region != 0) Gdip.Region_delete(region);
			if (matrix != 0) Gdip.Matrix_delete(matrix);
			Gdip.GraphicsPath_delete(nativePath);
		}
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

		switch (interpolation) {
		case SWT.NONE -> this.interpolationMode = SamplingMode.DEFAULT; // Nearest neighbor
		case SWT.LOW -> this.interpolationMode = SamplingMode.LINEAR;
		case SWT.DEFAULT -> this.interpolationMode = SamplingMode.MITCHELL;
		case SWT.HIGH -> this.interpolationMode = SamplingMode.CATMULL_ROM;
		default -> SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
	}

	@Override
	public void setLineAttributes(LineAttributes attributes) {
		if (attributes == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}

		if(attributes.width <= 0) {
			attributes.width = 1;
		}

		final float scaledWidth = getScaler().autoScaleUp(attributes.width);
		setLineAttributesInPixels(attributes, scaledWidth);
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

		final var font = getMayaFont();
		final var m = font.getMetrics();
		final var fe = new FontMetricsExtension(new RasterExternalFontMetrics(m, getScaler()));
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
			/* the layout draws over the pixels already under it (a background, a selection), not over a white box */
			copyUnder(i, xInPoints - rectangle.x, yInPoints - rectangle.y);
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

	/*
	 * The surface's pixels under an image placed at (x, y) in points, copied into the image. The snapshot is the part of
	 * the area on the surface: it is placed where that part starts, so an area above or left of the surface stays
	 * aligned; pixels off the surface keep the image's own, and an area wholly off the surface copies nothing.
	 */
	private void copyUnder(Image image, int x, int y) {
		final int width, height;
		try (final IRasterImage size = SwtToRasterImageConverter.convertSWTImageToMayaImage(image,
				getScaler().getNativeZoom(), resources)) {
			width = size.getWidth();
			height = size.getHeight();
		}
		/* the origin scales to pixels; the size is the image's, already in pixels */
		final var wanted = org.eclipse.swt.internal.maya.types.IRect.makeXYWH(getScaler().autoScaleUp(x),
				getScaler().autoScaleUp(y), width, height);
		if (wanted.getRight() <= 0 || wanted.getBottom() <= 0 || wanted.getLeft() >= surface.getWidth()
				|| wanted.getTop() >= surface.getHeight()) {
			return;
		}
		try (final IRasterImage under = surface.makeImageSnapshot(wanted)) {
			try (final IRasterSurface copy = surface.makeSurface(width, height)) {
				copy.getCanvas().drawImage(under, Math.max(0, -wanted.getLeft()), Math.max(0, -wanted.getTop()));
				try (final IRasterImage snapshot = copy.makeImageSnapshot()) {
					final Image pixels = new Image(device, RasterToSwtImageConverter.convertMayaImageToImageData(snapshot));
					final GC gc = new GC(image);
					try {
						gc.drawImage(pixels, 0, 0);
					} finally {
						gc.dispose();
						pixels.dispose();
					}
				}
			}
		}
	}

	IRasterResources getMayaResources() {
		return this.resources;
	}

	IRasterSurface getMayaSurface() {
		return this.surface;
	}

	IRasterCanvasExtension getMayaExtension() {
		return this.mayaExtension;
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
