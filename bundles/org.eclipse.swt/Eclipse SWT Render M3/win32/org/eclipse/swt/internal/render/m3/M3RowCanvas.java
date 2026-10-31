/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3;

import java.util.*;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.WidgetMode;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.M3PlaneCanvasExtension;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.Control;

import jdk.m3.graphics.*;
import jdk.m3.graphics.M3Font;
import jdk.m3.graphics.M3FontMetrics;
import jdk.m3.graphics.M3Image;
import jdk.m3.graphics.M3Path;
import jdk.m3.graphics.M3Rect;

/**
 * The <code>m3</code> backend on win32, the M3 renderer: it draws through the Skija library (Apache-2.0) on the CPU,
 * straight into the pixels of the plane's back buffer (<code>Surface.makeRasterDirect</code> over the DIB section), so
 * a frame allocates no surface and copies nothing; the plane then copies the damage to the window. This package is the
 * one adapter between SWT and the library's binding classes, which keep their package because the library's prebuilt
 * natives bind to it.
 * <p>
 * Caches, all bounded LRUs closed on eviction: one library font (with its metrics) per native font handle, shaped
 * text lines per font and string with their blob and width (the skija-canvas text LRU pattern), images per SWT
 * image, theme part images per part, state and size. This class is loaded by name, so SWT runs without the Skija
 * jars when this mode is not used.
 * </p><p>
 * The paint path is data oriented (rule 18): the per-row operations (fills, text) run on cached pointers through
 * the library's primitive native entry points and allocate nothing; objects are made only when a cache misses.
 * </p>
 */
public final class M3RowCanvas implements BufferCanvas {
	private static final int FONTS = 32, LINES = 2048, PART_IMAGES = 256;
	private static final int CLIP_INTERSECT = M3ClipMode.INTERSECT.ordinal ();
	private static final String ELLIPSIS_TEXT = "...";
	/* what Canvas.drawImage and drawImageRect pass: the default sampling, strict source bounds */
	private static final long SAMPLING = M3SamplingMode.DEFAULT._pack ();

	/** A cached font, its metrics measured once, and its shaped lines (by string, an LRU closed on eviction). */
	static final class FontEntry implements AutoCloseable {
		final M3Font font;
		/* what the font was made from on the render thread (null: from the platform's handle) */
		final FontDescriptor descriptor;
		final float ascent, lineHeight;
		final Map<String, LineEntry> lines = lru (LINES);

		FontEntry (M3Font font, FontDescriptor descriptor) {
			this.font = font;
			this.descriptor = descriptor;
			M3FontMetrics metrics = font.getMetrics ();
			ascent = -metrics.getAscent ();
			lineHeight = metrics.getDescent () - metrics.getAscent ();
		}

		@Override
		public void close () {
			lines.values ().forEach (M3RowCanvas::close);
			lines.clear ();
			font.close ();
		}
	}

	/** A library image with its pointer and size, read once: drawn through the primitive entry point. */
	static final class Bitmap implements AutoCloseable {
		final M3Image image;
		final long ptr;
		final int width, height;

		Bitmap (M3Image image) {
			this.image = image;
			ptr = jdk.m3.graphics.impl.M3Native.getPtr (image);
			width = image.getWidth ();
			height = image.getHeight ();
		}

		@Override
		public void close () {
			image.close ();
		}
	}

	/** A cached shaped line: its blob pointer and width, read once; and its ellipsis form for the last box width asked. */
	static final class LineEntry implements AutoCloseable {
		final M3TextLine line;
		final M3TextBlob blob;
		final long blobPtr;
		final float width;
		int fittedWidth = -1;
		LineEntry fitted;

		LineEntry (M3TextLine line) {
			this.line = line;
			blob = line.getTextBlob ();
			blobPtr = blob == null ? 0 : jdk.m3.graphics.impl.M3Native.getPtr (blob);
			width = line.getWidth ();
		}

		@Override
		public void close () {
			if (fitted != null) fitted.close ();
			if (blob != null) blob.close ();
			line.close ();
		}
	}

	private final IntSupplier zoom;
	private final ThemeParts parts;
	private final M3Paint paint = new M3Paint ().setAntiAlias (false);
	private final M3Paint textPaint = new M3Paint ().setAntiAlias (true);
	private final M3Paint focusPaint = new M3Paint ().setMode (M3PaintMode.STROKE).setStrokeWidth (1).setColor (0xFF000000)
		.setPathEffect (M3PathEffect.makeDash (new float [] {1, 1}, 0));
	private final long paintPtr = jdk.m3.graphics.impl.M3Native.getPtr (paint);
	private final long textPaintPtr = jdk.m3.graphics.impl.M3Native.getPtr (textPaint);
	private final long focusPaintPtr = jdk.m3.graphics.impl.M3Native.getPtr (focusPaint);
	private final M3SurfaceProps props = new M3SurfaceProps (false, M3PixelGeometry.RGB_H);
	private M3Surface surface;
	private M3Canvas canvas;
	private long canvasPtr;
	private long dc, bits;
	private int width, height;
	/* by native handle or packed part key: no boxing per lookup (LongCache) */
	private final LongCache<FontEntry> fonts = new LongCache<> (FONTS, M3RowCanvas::close);
	private final Map<org.eclipse.swt.graphics.Image, Bitmap> images = new WeakHashMap<> ();
	private final Map<ImageData, Bitmap> glyphs = new WeakHashMap<> ();
	private final LongCache<Bitmap> partImages = new LongCache<> (PART_IMAGES, M3RowCanvas::close);
	private final LOGFONT logFont = new LOGFONT ();
	/* the fonts of the frame being drawn on the render thread (rule 28); null on the UI thread */
	private FontTable table;
	/* the path of polygon, reset per call */
	private final M3PathBuilder path = new M3PathBuilder (); // Skija 0.143: the mutable path is a PathBuilder (L12-09)
	/* TEXT_TRUNCATION v1: DrawText's ellipsis cut measured by GDI */
	private final M3GdiEllipsis gdiEllipsis = new M3GdiEllipsis ();
	/* the gpu mode: the frame is drawn on the graphics hardware and read back into the back buffer */
	private final M3Gpu gpu;
	private M3Pixmap readback;
	private M3ImageInfo readbackInfo;
	private int frameX, frameY, frameWidth, frameHeight;
	/*
	 * The GC seam (leaf 3): the plane's side of the seam is made once per canvas (its resources listen to the control); the
	 * GC over it once per surface (M3ExternalGC keeps the surface it was made on), inside a frame, so its save count is the
	 * frame's and a dispose per event restores to it. Null until a listener asks.
	 */
	private M3PlaneCanvasExtension extension;
	private GC gc;

	/**
	 * The CPU (raster) backend of the <code>m3</code> mode.
	 *
	 * @param hwnd the plane's window (theme parts are opened for it)
	 * @param itemThemeClass the uxtheme class of rows (NUL-terminated)
	 * @param zoom the widget's current native zoom
	 */
	public M3RowCanvas (long hwnd, char [] itemThemeClass, IntSupplier zoom) {
		this (hwnd, itemThemeClass, zoom, null);
	}

	/* M3_LOAD_LATCH v1: without the native library the backend is refused at creation (RowBackends then takes the GC backend) */
	private static M3Gpu requireLibrary (M3Gpu gpu) {
		if (!M3Library.loaded ()) throw new UnsatisfiedLinkError ("the M3 renderer's native library is not loaded"); //$NON-NLS-1$
		return gpu;
	}

	/** MODE_TRUTH v1 (census L10): this backend realizes the gpu mode on a GL context, the m3 mode otherwise. */
	@Override
	public WidgetMode route () {
		return gpu != null ? WidgetMode.GPU : WidgetMode.M3;
	}

	/** BACKEND_REPORT v1: <code>M3RowCanvas(gpu)</code> or <code>M3RowCanvas(raster)</code>. */
	@Override
	public String backend () {
		return "M3RowCanvas(" + (gpu != null ? "gpu" : "raster") + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private M3RowCanvas (long hwnd, char [] itemThemeClass, IntSupplier zoom, M3Gpu gpu) {
		this.gpu = requireLibrary (gpu);
		this.zoom = zoom;
		this.parts = new ThemeParts (hwnd, itemThemeClass, zoom);
	}

	/**
	 * The graphics hardware backend of the <code>gpu</code> mode (Skia over OpenGL, see {@link M3Gpu}), or null when
	 * no GL context can be made.
	 */
	public static M3RowCanvas gpu (long hwnd, char [] itemThemeClass, IntSupplier zoom) {
		M3Gpu gpu = M3Library.loaded () ? M3Gpu.acquire () : null;
		return gpu == null ? null : new M3RowCanvas (hwnd, itemThemeClass, zoom, gpu);
	}

	private static <K, V> Map<K, V> lru (int capacity) {
		return new LinkedHashMap<> (16, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry (Map.Entry<K, V> eldest) {
				if (size () <= capacity) return false;
				if (eldest.getValue () instanceof AutoCloseable closeable) close (closeable);
				return true;
			}
		};
	}

	private static void close (AutoCloseable closeable) {
		try {
			closeable.close ();
		} catch (Exception e) {
			/* Skija's close does not throw */
		}
	}

	@Override
	public void target (BackBuffer buffer) {
		dc = buffer.dc;
		if (surface != null && buffer.bits == bits && buffer.width == width && buffer.height == height) return;
		if (surface != null) surface.close ();
		/* the GC was made on the surface being replaced: the next listener gets one on the new surface */
		gc = null;
		bits = buffer.bits;
		width = buffer.width;
		height = buffer.height;
		M3ImageInfo info = new M3ImageInfo (width, height, M3ColorType.BGRA_8888, M3ColorAlphaType.PREMUL);
		if (gpu != null) {
			gpu.makeCurrent ();
			surface = M3Surface.makeRenderTarget (gpu.context, false, info, 0, M3SurfaceOrigin.TOP_LEFT, props);
		} else {
			surface = M3Surface.makeRasterDirect (info, bits, width * 4L, props);
		}
		canvas = surface.getCanvas ();
		canvasPtr = jdk.m3.graphics.impl.M3Native.getPtr (canvas);
	}

	@Override
	public void begin (int x, int y, int width, int height) {
		/* a GDI call that answers flushes the GDI batch of the behaviour pass before Skia writes the same pixels */
		OS.GetPixel (dc, x, y);
		if (gpu != null) gpu.makeCurrent ();
		frameX = x;
		frameY = y;
		frameWidth = width;
		frameHeight = height;
		M3Canvas._nSave (canvasPtr);
		M3Canvas._nClipRect (canvasPtr, x, y, x + width, y + height, CLIP_INTERSECT, false);
	}

	@Override
	public void end () {
		M3Canvas._nRestore (canvasPtr);
		if (gpu == null || frameWidth <= 0 || frameHeight <= 0) return;
		/* the GPU frame is read back into the damage of the back buffer; the read waits for the GPU work */
		gpu.context.flush ();
		if (readbackInfo == null || readbackInfo.getWidth () != frameWidth || readbackInfo.getHeight () != frameHeight) {
			readbackInfo = new M3ImageInfo (frameWidth, frameHeight, M3ColorType.BGRA_8888, M3ColorAlphaType.PREMUL);
		}
		long address = bits + ((long) frameY * width + frameX) * 4L;
		if (readback == null) {
			readback = M3Pixmap.make (readbackInfo, address, width * 4);
		} else {
			readback.reset (readbackInfo, address, width * 4);
		}
		surface.readPixels (readback, frameX, frameY);
	}

	/** The library surface over the current target's pixels (replaced when the back buffer grows), or null before the first target. */
	public M3Surface surface () {
		return surface;
	}

	/*
	 * A GCExtension over M3ExternalGC on this canvas's surface (the GC seam, leaves 1 and 2), for the owner-draw events of
	 * the plane: one per surface, made inside the frame that first asks (M3ExternalGC records the canvas save count it
	 * restores to on dispose, and the plane disposes it after each event, which leaves the frame's own clip in place). The
	 * frame's damage is the GC's client area and initial clip.
	 */
	@Override
	public GC gc (Control control, BackBuffer buffer) {
		if (surface == null) return null;
		if (extension == null) extension = new M3PlaneCanvasExtension (control, buffer, this::surface);
		extension.frame (frameX, frameY, frameWidth, frameHeight);
		if (gc == null) gc = new GCExtension (new M3ExternalGC (extension, SWT.NONE));
		return gc;
	}

	@Override
	public void clip (int x, int y, int width, int height) {
		M3Canvas._nSave (canvasPtr);
		M3Canvas._nClipRect (canvasPtr, x, y, x + width, y + height, CLIP_INTERSECT, false);
	}

	@Override
	public void unclip () {
		M3Canvas._nRestore (canvasPtr);
	}

	@Override
	public void fill (int x, int y, int width, int height, int rgb) {
		if (width <= 0 || height <= 0) return;
		paint.setColor (0xFF000000 | rgb);
		M3Canvas._nDrawRect (canvasPtr, x, y, x + width, y + height, paintPtr);
	}

	@Override
	public void text (String text, long font, int rgb, int x, int y, int width, int height, int align) {
		if (text.isEmpty () || width <= 0) return;
		FontEntry entry = font (font);
		LineEntry line = line (entry, text);
		if ((align & ELLIPSIS) != 0) line = ellipsis (entry, line, font, text, width);
		align &= ~ELLIPSIS;
		if (line.blobPtr == 0) return;
		float baseline = y + (height - entry.lineHeight) / 2 + entry.ascent;
		float left = align == SWT.RIGHT ? x + width - line.width : align == SWT.CENTER ? x + (width - line.width) / 2 : x;
		textPaint.setColor (0xFF000000 | rgb);
		M3Canvas._nSave (canvasPtr);
		M3Canvas._nClipRect (canvasPtr, x, y, x + width, y + height, CLIP_INTERSECT, false);
		M3Canvas._nDrawTextBlob (canvasPtr, line.blobPtr, Math.round (left), Math.round (baseline), textPaintPtr);
		M3Canvas._nRestore (canvasPtr);
	}

	/* one library font per native font handle: face, pixel height, weight and italic of its LOGFONT */
	private FontEntry font (long hFont) {
		/* on the render thread the frame describes its fonts; a handle reused for another font makes a new entry */
		FontDescriptor described = table != null ? table.font (hFont) : null;
		FontEntry cached = fonts.get (hFont);
		if (cached != null && (described == null || described.equals (cached.descriptor))) return cached;
		String face = "Segoe UI";
		int height = -12, weight = 400;
		boolean italic = false;
		if (described != null) {
			face = described.face ();
			height = described.height ();
			weight = described.weight ();
			italic = described.italic ();
		} else if (hFont != 0 && hFont != -1 && OS.GetObject (hFont, LOGFONT.sizeof, logFont) != 0) {
			int length = 0;
			while (length < logFont.lfFaceName.length && logFont.lfFaceName [length] != 0) length++;
			face = new String (logFont.lfFaceName, 0, length);
			height = logFont.lfHeight;
			weight = logFont.lfWeight;
			italic = logFont.lfItalic != 0;
		}
		M3FontStyle style = new M3FontStyle (weight == 0 ? 400 : weight, M3FontWidth.NORMAL, italic ? M3FontSlant.ITALIC : M3FontSlant.UPRIGHT);
		M3Typeface typeface = M3FontMgr.getDefault ().matchFamilyStyle (face, style);
		M3Font font = new M3Font (typeface, Math.abs (height));
		if (height > 0) {
			/* a positive height is the cell height: scale the em size so ascent plus descent match it */
			M3FontMetrics metrics = font.getMetrics ();
			font.setSize (height * height / Math.max (1f, metrics.getDescent () - metrics.getAscent ()));
		}
		font.setEdging (M3FontEdging.SUBPIXEL_ANTI_ALIAS).setSubpixel (true).setHinting (M3FontHinting.NORMAL);
		FontEntry entry = new FontEntry (font, described);
		fonts.put (hFont, entry);
		return entry;
	}

	@Override
	public void fonts (FontTable table) {
		this.table = table;
	}

	/*
	 * The line cut as DrawText's DT_END_ELLIPSIS cuts it: the longest prefix whose width plus the width of "..." fits
	 * the box, then "..." (only "..." when no character fits); the whole line when it fits. Kept on the full line for the
	 * last width asked (fitted null: the line fits).
	 */
	private LineEntry ellipsis (FontEntry entry, LineEntry full, long font, String text, int width) {
		if (full.fittedWidth != width) {
			int keep = cut (entry, full, font, text, width);
			if (full.fitted != null) full.fitted.close ();
			full.fitted = keep < 0 ? null : new LineEntry (M3TextLine.make (text.substring (0, keep) + ELLIPSIS_TEXT, entry.font));
			full.fittedWidth = width;
		}
		return full.fitted != null ? full.fitted : full;
	}

	/*
	 * TEXT_TRUNCATION v1 (real-app fidelity): on the UI thread GDI measures the cut on the cell's own font handle, as the
	 * list view does (M3GdiEllipsis); the render thread, which holds no platform font, cuts with the library's advances.
	 */
	private int cut (FontEntry entry, LineEntry full, long font, String text, int width) {
		if (table == null && font != 0 && font != -1) return gdiEllipsis.cut (font, text, width);
		if (full.width <= width) return -1;
		return Math.max (1, libraryCut (full, 0, text.length (), width - line (entry, ELLIPSIS_TEXT).width));
	}

	/* the longest prefix in [low, high] whose library advance fits the room */
	private static int libraryCut (LineEntry full, int low, int high, float room) {
		if (low >= high) return low;
		int middle = (low + high + 1) >>> 1;
		return full.line.getCoordAtOffset (middle) <= room ? libraryCut (full, middle, high, room) : libraryCut (full, low, middle - 1, room);
	}

	private static LineEntry line (FontEntry entry, String text) {
		LineEntry line = entry.lines.get (text);
		if (line == null) {
			line = new LineEntry (M3TextLine.make (text, entry.font));
			entry.lines.put (text, line);
		}
		return line;
	}

	/* an image at a box: Canvas.drawImageRect (whole image, box, default sampling, no paint, strict) without the rectangles */
	private void draw (Bitmap bitmap, float x, float y, float width, float height) {
		M3Canvas._nDrawImageRect (canvasPtr, bitmap.ptr, 0, 0, bitmap.width, bitmap.height, x, y, x + width, y + height, SAMPLING, 0, true);
	}

	@Override
	public void image (org.eclipse.swt.graphics.Image image, int x, int y, int width, int height) {
		if (image == null || image.isDisposed () || width <= 0 || height <= 0) return;
		Bitmap bitmap = images.get (image);
		if (bitmap == null) {
			bitmap = new Bitmap (convert (image.getImageData (zoom.getAsInt ())));
			images.put (image, bitmap);
		}
		draw (bitmap, x, y, width, height);
	}

	private static M3Image convert (ImageData data) {
		return M3Image.makeRasterFromBytes (new M3ImageInfo (data.width, data.height, M3ColorType.BGRA_8888, M3ColorAlphaType.PREMUL), RowImages.bgra (data), data.width * 4L);
	}

	@Override
	public void imageData (ImageData data, int x, int y, int width, int height) {
		if (data == null || width <= 0 || height <= 0) return;
		Bitmap bitmap = glyphs.get (data);
		if (bitmap == null) {
			bitmap = new Bitmap (convert (data));
			glyphs.put (data, bitmap);
		}
		draw (bitmap, x, y, width, height);
	}

	@Override
	public void polygon (int [] points, int count, int rgb) {
		if (count < 3) return;
		path.reset ();
		path.moveTo (points [0], points [1]);
		for (int i = 1; i < count; i++) path.lineTo (points [2 * i], points [2 * i + 1]);
		path.closePath ();
		paint.setColor (0xFF000000 | rgb);
		try (M3Path shape = path.detach ()) { // Skija 0.143: draw the immutable Path the builder detaches (L12-10)
			canvas.drawPath (shape, paint);
		}
	}

	@Override
	public void part (int part, int state, int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) return;
		long key = (long) part << 56 | (long) (state & 0xFFFF) << 40 | (long) (width & 0xFFFFF) << 20 | height & 0xFFFFF;
		Bitmap image = partImages.get (key);
		if (image == null) {
			byte [] pixels = parts.image (part, state, width, height);
			image = new Bitmap (M3Image.makeRasterFromBytes (new M3ImageInfo (width, height, M3ColorType.BGRA_8888, M3ColorAlphaType.PREMUL), pixels, width * 4L));
			partImages.put (key, image);
		}
		/* Canvas.drawImage at (x, y): the image at its own size */
		draw (image, x, y, image.width, image.height);
	}

	@Override
	public void focus (int x, int y, int width, int height) {
		/* Canvas.drawRect of (x + 0.5, y + 0.5, width - 1, height - 1) with the dotted paint */
		M3Canvas._nDrawRect (canvasPtr, x + 0.5f, y + 0.5f, x + 0.5f + (width - 1), y + 0.5f + (height - 1), focusPaintPtr);
	}

	@Override
	public void dispose () {
		parts.close ();
		gdiEllipsis.dispose ();
		fonts.clear ();
		images.values ().forEach (M3RowCanvas::close);
		images.clear ();
		glyphs.values ().forEach (M3RowCanvas::close);
		glyphs.clear ();
		partImages.clear ();
		path.close ();
		if (surface != null) surface.close ();
		surface = null;
		if (readback != null) readback.close ();
		readback = null;
		if (gpu != null) gpu.release ();
		paint.close ();
		textPaint.close ();
		focusPaint.close ();
	}
}
