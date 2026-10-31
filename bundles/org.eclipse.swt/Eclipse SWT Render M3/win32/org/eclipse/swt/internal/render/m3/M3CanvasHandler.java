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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 4)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3;

import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.internal.render.m3.gc.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;

import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;
import io.github.humbleui.types.Rect;

/**
 * The M3 renderer behind a <code>Canvas</code> created with {@link SWT#M3} (GC seam, leaf 4): the canvas's
 * <code>WM_PAINT</code> through {@link IExternalCanvasHandler}, painting the damage into a retained {@link BackBuffer}
 * of the canvas through a <code>GCExtension</code> over {@link M3ExternalGC}, then presenting it. The surface is
 * exactly the drawn planes' (<code>M3RowCanvas.target</code>): <code>Surface.makeRasterDirect</code> over the DIB
 * section in the <code>m3</code> mode, a render target on the thread's {@link M3Gpu} context read back into the
 * damage in the <code>gpu</code> mode; no GL context or surface of its own.
 * <p>
 * Pattern (Initiative 31 <code>SkiaGlCanvasExtension</code> and <code>GLPaintEventInvoker</code>, PATTERN_ONLY):
 * clip the canvas to the area to paint, clear it with the widget's background, send one Paint event with a fresh
 * <code>GCExtension</code> and dispose it, restore the canvas. Contract: the stock <code>Composite.WM_PAINT</code> of
 * this repository (background unless <code>SWT.NO_BACKGROUND</code>, then the Paint event whose bounds are the damage
 * in points, one GC per paint). The handler is the canvas's data under {@link #DATA_KEY} and answers
 * {@link #pixel} for probes and tests.
 * </p>
 */
public final class M3CanvasHandler implements IExternalCanvasHandler {
	/** The data key under which a handled canvas carries its handler (<code>Canvas.getData</code>). */
	public static final String DATA_KEY = "org.eclipse.swt.internal.render.m3.M3CanvasHandler"; //$NON-NLS-1$
	private static final SurfaceProps PROPS = new SurfaceProps (false, PixelGeometry.RGB_H);

	private final Canvas control;
	private final M3Gpu gpu;
	private final BackBuffer buffer = new BackBuffer ();
	private final M3PlaneCanvasExtension extension;
	private Surface surface;
	private long bits;
	private int width, height;
	private Pixmap readback;
	private ImageInfo readbackInfo;
	private boolean disposed;

	private M3CanvasHandler (Canvas canvas, M3Gpu gpu) {
		this.control = canvas;
		this.gpu = gpu;
		this.extension = new M3PlaneCanvasExtension (canvas, buffer, this::surface);
		canvas.setData (DATA_KEY, this);
		canvas.addListener (SWT.Dispose, e -> dispose ());
	}

	/** The <code>m3</code> mode: the surface draws straight into the back buffer's pixels. */
	public static M3CanvasHandler raster (Canvas canvas) {
		return new M3CanvasHandler (canvas, null);
	}

	/**
	 * The <code>gpu</code> mode: a surface on the thread's GL context, read back into the back buffer; the raster handler
	 * when no GL context can be made (as <code>RowBackends</code> falls back for the planes).
	 */
	public static M3CanvasHandler gpu (Canvas canvas) {
		return new M3CanvasHandler (canvas, M3Gpu.acquire ());
	}

	/** Whether frames are drawn on the graphics hardware. */
	public boolean isGpu () {
		return gpu != null;
	}

	/** The library surface over the back buffer (replaced when the buffer grows), or null before the first frame. */
	public Surface surface () {
		return surface;
	}

	/** The colour (<code>0xRRGGBB</code>) of a back-buffer pixel, or -1 before the first frame or outside the buffer. */
	public int pixel (int x, int y) {
		if (buffer.dc == 0 || x < 0 || y < 0 || x >= buffer.width || y >= buffer.height) return -1;
		int bgr = OS.GetPixel (buffer.dc, x, y);
		return (bgr & 0xFF) << 16 | (bgr & 0xFF00) | (bgr >> 16 & 0xFF);
	}

	@Override
	public Object paint (Consumer<Event> send, long wParam, long lParam) {
		long hwnd = control.handle;
		PAINTSTRUCT ps = new PAINTSTRUCT ();
		long hdc = OS.BeginPaint (hwnd, ps);
		try {
			int damageWidth = ps.right - ps.left, damageHeight = ps.bottom - ps.top;
			if (!disposed && damageWidth > 0 && damageHeight > 0) frame (send, hdc, ps.left, ps.top, damageWidth, damageHeight);
		} finally {
			OS.EndPaint (hwnd, ps);
		}
		return LRESULT.ZERO;
	}

	/* the stock Composite.WM_PAINT in M3: background, the Paint event with the damage as its bounds, present */
	private void frame (Consumer<Event> send, long hdc, int x, int y, int damageWidth, int damageHeight) {
		prepare (hdc);
		io.github.humbleui.skija.Canvas canvas = surface.getCanvas ();
		canvas.save ();
		canvas.clipRect (Rect.makeXYWH (x, y, damageWidth, damageHeight));
		if ((control.getStyle () & SWT.NO_BACKGROUND) == 0) {
			canvas.clear (M3ColorConverter.convertSWTColorToM3Color (control.getBackground ()));
		}
		try {
			event (send, x, y, damageWidth, damageHeight);
		} finally {
			canvas.restore ();
		}
		if (gpu != null) readback (x, y, damageWidth, damageHeight);
		buffer.present (hdc, x, y, damageWidth, damageHeight);
	}

	/* the back buffer at the client size (size classes, never shrinking), the surface over it, the GL context current */
	private void prepare (long hdc) {
		RECT client = new RECT ();
		OS.GetClientRect (control.handle, client);
		buffer.ensureFrame (hdc, client.right, client.bottom);
		target ();
		if (gpu != null) gpu.makeCurrent ();
	}

	/* M3RowCanvas.target: one surface over the buffer's pixels, replaced when the buffer is (re)made */
	private void target () {
		if (surface != null && buffer.bits == bits && buffer.width == width && buffer.height == height) return;
		if (surface != null) surface.close ();
		bits = buffer.bits;
		width = buffer.width;
		height = buffer.height;
		ImageInfo info = new ImageInfo (width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL);
		if (gpu != null) {
			gpu.makeCurrent ();
			surface = Surface.makeRenderTarget (gpu.context, false, info, 0, SurfaceOrigin.TOP_LEFT, PROPS);
		} else {
			surface = Surface.makeRasterDirect (info, bits, width * 4L, PROPS);
		}
	}

	/*
	 * One GC per paint, as the stock path makes one per WM_PAINT: made inside the frame's save so M3ExternalGC restores
	 * to it on dispose. The bounds are the damage in points by the zoom the extension's scaler uses (control.nativeZoom).
	 */
	private void event (Consumer<Event> send, int x, int y, int damageWidth, int damageHeight) {
		extension.frame (x, y, damageWidth, damageHeight);
		Event event = new Event ();
		event.gc = new GCExtension (new M3ExternalGC (extension, SWT.NONE));
		event.setBounds (Win32DPIUtils.pixelToPoint (new Rectangle (x, y, damageWidth, damageHeight), control.nativeZoom));
		try {
			send.accept (event);
		} finally {
			event.gc.dispose ();
			event.gc = null;
		}
	}

	/* M3RowCanvas.end: the GPU frame is read back into the damage of the back buffer; the read waits for the GPU work */
	private void readback (int x, int y, int damageWidth, int damageHeight) {
		gpu.context.flush ();
		if (readbackInfo == null || readbackInfo.getWidth () != damageWidth || readbackInfo.getHeight () != damageHeight) {
			readbackInfo = new ImageInfo (damageWidth, damageHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL);
		}
		long address = bits + ((long) y * width + x) * 4L;
		if (readback == null) {
			readback = Pixmap.make (readbackInfo, address, width * 4);
		} else {
			readback.reset (readbackInfo, address, width * 4);
		}
		surface.readPixels (readback, x, y);
	}

	/** The back buffer retains the frame: a redraw request only invalidates the window (<code>Canvas.redraw</code> does). */
	@Override
	public void redrawTriggered () {
	}

	@Override
	public void redrawTriggered (int x, int y, int redrawWidth, int redrawHeight, boolean all) {
	}

	/**
	 * <code>Canvas.scroll</code> in points: the back buffer's pixels move with the window's (<code>ScrollWindowEx</code>),
	 * the exposed band is invalidated and painted by the next frame; children move with <code>SW_SCROLLCHILDREN</code>.
	 */
	@Override
	public void scroll (int destX, int destY, int x, int y, int scrollWidth, int scrollHeight, boolean all) {
		/* Canvas.scroll delegates before its checkWidget (adopted I31 hook); Widget.checkWidget is protected: the thread check is the window's */
		if (control.isDisposed ()) return;
		int zoom = control.nativeZoom;
		Rectangle source = Win32DPIUtils.pointToPixel (new Rectangle (x, y, scrollWidth, scrollHeight), zoom);
		int deltaX = DPIUtil.pointToPixel (destX, zoom) - source.x, deltaY = DPIUtil.pointToPixel (destY, zoom) - source.y;
		if (buffer.dc != 0) {
			OS.BitBlt (buffer.dc, source.x + deltaX, source.y + deltaY, source.width, source.height, buffer.dc, source.x, source.y, OS.SRCCOPY);
		}
		RECT rect = new RECT ();
		OS.SetRect (rect, source.x, source.y, source.x + source.width, source.y + source.height);
		int flags = OS.SW_INVALIDATE | OS.SW_ERASE | (all ? OS.SW_SCROLLCHILDREN : 0);
		OS.ScrollWindowEx (control.handle, deltaX, deltaY, rect, null, 0, null, flags);
	}

	private void dispose () {
		disposed = true;
		if (surface != null) surface.close ();
		surface = null;
		if (readback != null) readback.close ();
		readback = null;
		if (gpu != null) gpu.release ();
		buffer.release ();
	}
}
