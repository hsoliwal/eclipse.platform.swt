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
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.internal.render.m3.gc.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;

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
 * <p>
 * Leaf 5: every <code>Canvas</code> subclass of the <code>m3</code> and <code>gpu</code> modes gets this handler
 * (<code>M3CanvasFactory</code>). Damage (pattern M3-CANVAS-DAMAGE v1, {@link M3CanvasDamage}): the update region is read
 * before <code>BeginPaint</code>, each Paint event is drawn clipped to its part of it (one per rectangle with
 * <code>SWT.NO_MERGE_PAINTS</code>), and only its rectangles are presented, so a partial <code>Canvas.redraw</code> repaints
 * and copies that damage alone. Retention (M3-CANVAS-RETAIN v1): a canvas that draws over its previous frame
 * (<code>SWT.NO_BACKGROUND</code>, <code>SWT.TRANSPARENT</code>) keeps its pixels when the buffer grows, and a new GPU
 * target starts from the buffer's pixels.
 * </p>
 * <p>
 * Leaf 7 (pattern COMPOSITE_PAINT_HOOK v1): a <code>Composite</code> that paints itself (CTabFolder, CBanner, ViewForm, an
 * application's or Nebula's composite with a Paint listener) gets this handler too, made at its first painted
 * <code>WM_PAINT</code> (<code>Composite.paintThroughM3</code>). Its child controls stay native windows: the paint runs between
 * <code>BeginPaint</code> and <code>EndPaint</code> with <code>WS_CLIPCHILDREN</code>, so the present, clipped by the paint
 * device context, never covers a child (CHILD_CLIP v1); the back buffer under a child holds the composite's own pixels.
 * </p>
 */
public final class M3CanvasHandler implements IExternalCanvasHandler {
	/** The data key under which a handled canvas carries its handler (<code>Canvas.getData</code>). */
	public static final String DATA_KEY = "org.eclipse.swt.internal.render.m3.M3CanvasHandler"; //$NON-NLS-1$
	private static final SurfaceProps PROPS = new SurfaceProps (false, PixelGeometry.RGB_H);

	private final Composite control;
	private final M3Gpu gpu;
	private final BackBuffer buffer = new BackBuffer ();
	private final M3PlaneCanvasExtension extension;
	private Surface surface;
	private long bits;
	private int width, height;
	private Pixmap readback;
	private ImageInfo readbackInfo;
	private boolean disposed;
	/* leaf 5: the damage of the paint in progress, the update region read before BeginPaint, the release deferred while a paint runs */
	private final M3CanvasDamage damage = new M3CanvasDamage ();
	private long regionHandle;
	private int [] regionData = new int [M3CanvasDamage.HEADER + 4 * 8];
	private boolean painting, released;

	private M3CanvasHandler (Composite canvas, M3Gpu gpu) {
		this.control = canvas;
		this.gpu = gpu;
		this.extension = new M3PlaneCanvasExtension (canvas, buffer, this::surface);
		canvas.setData (DATA_KEY, this);
		canvas.addListener (SWT.Dispose, e -> dispose ());
	}

	/** The <code>m3</code> mode: the surface draws straight into the back buffer's pixels. */
	public static M3CanvasHandler raster (Composite canvas) {
		return new M3CanvasHandler (canvas, null);
	}

	/**
	 * The <code>gpu</code> mode: a surface on the thread's GL context, read back into the back buffer; the raster handler
	 * when no GL context can be made (as <code>RowBackends</code> falls back for the planes).
	 */
	public static M3CanvasHandler gpu (Composite canvas) {
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
		int oldBits = clipBits (hwnd);
		readRegion (hwnd);
		PAINTSTRUCT ps = new PAINTSTRUCT ();
		long hdc = OS.BeginPaint (hwnd, ps);
		painting = true;
		try {
			if (!disposed && ps.right > ps.left && ps.bottom > ps.top) frame (send, hdc, ps);
		} finally {
			painting = false;
			OS.EndPaint (hwnd, ps);
			if (disposed) release (); else if (oldBits != 0) OS.SetWindowLong (hwnd, OS.GWL_STYLE, oldBits);
		}
		return LRESULT.ZERO;
	}

	/* the stock Composite.WM_PAINT clips siblings and children while it paints; answers the bits to restore, or 0 */
	private int clipBits (long hwnd) {
		int oldBits = OS.GetWindowLong (hwnd, OS.GWL_STYLE), newBits = oldBits | OS.WS_CLIPSIBLINGS | OS.WS_CLIPCHILDREN;
		if (newBits == oldBits) return 0;
		OS.SetWindowLong (hwnd, OS.GWL_STYLE, newBits);
		return oldBits;
	}

	/* the update region as RGNDATA, read before BeginPaint validates it (ViewportDamageHook.record); no rectangle when there is none */
	private void readRegion (long hwnd) {
		if (regionHandle == 0) regionHandle = OS.CreateRectRgn (0, 0, 0, 0);
		regionData [2] = 0;
		if (OS.GetUpdateRgn (hwnd, regionHandle, false) <= OS.NULLREGION) return;
		int bytes = OS.GetRegionData (regionHandle, 0, null);
		if (bytes <= 0) return;
		if (regionData.length < (bytes + 3) / 4) regionData = new int [(bytes + 3) / 4];
		if (OS.GetRegionData (regionHandle, bytes, regionData) == 0) regionData [2] = 0;
	}

	/* the stock Composite.WM_PAINT in M3: per pass the background and the Paint event, clipped to the damage; then the damage presented */
	private void frame (Consumer<Event> send, long hdc, PAINTSTRUCT ps) {
		damage.read (regionData, ps.left, ps.top, ps.right, ps.bottom);
		prepare (hdc);
		int passes = damage.passes (control.getStyle ());
		try {
			IntStream.range (0, passes).takeWhile (p -> !disposed).forEach (p -> pass (send, p, passes));
		} finally {
			/* leaf 7: a listener that throws still has its partial frame presented (stock: what it drew is on the paint DC); it still throws */
			if (!disposed) IntStream.range (0, damage.count ()).forEach (i -> present (hdc, damage.x (i), damage.y (i), damage.width (i), damage.height (i)));
		}
	}

	/* one Paint event, clipped to its pass (M3CanvasDamage.clip): the background first unless the canvas retains its pixels */
	private void pass (Consumer<Event> send, int pass, int passes) {
		io.github.humbleui.skija.Canvas canvas = surface.getCanvas ();
		canvas.save ();
		damage.clip (canvas, pass, passes);
		if (!retains ()) canvas.clear (M3ColorConverter.convertSWTColorToM3Color (control.getBackground ()));
		try {
			event (send, damage.bounds (pass, passes), damage.eventCount (pass, passes));
		} finally {
			canvas.restore ();
		}
	}

	/* SWT.NO_BACKGROUND and SWT.TRANSPARENT draw over the previous frame (the stock path draws no background for them) */
	private boolean retains () {
		return (control.getStyle () & (SWT.NO_BACKGROUND | SWT.TRANSPARENT)) != 0;
	}

	/* one damage rectangle: the GPU frame read back into it, then copied to the window */
	private void present (long hdc, int x, int y, int presentWidth, int presentHeight) {
		if (gpu != null) readback (x, y, presentWidth, presentHeight);
		buffer.present (hdc, x, y, presentWidth, presentHeight);
	}

	/* the back buffer at the client size (size classes, never shrinking; growFrame keeps the pixels of a retaining canvas), its surface */
	private void prepare (long hdc) {
		RECT client = new RECT ();
		OS.GetClientRect (control.handle, client);
		if (retains ()) buffer.growFrame (hdc, client.right, client.bottom); else buffer.ensureFrame (hdc, client.right, client.bottom);
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
		surface = gpu == null ? Surface.makeRasterDirect (info, bits, width * 4L, PROPS) : gpuTarget (info);
	}

	/* a render target on the thread's GL context that starts from the buffer's pixels, so retained pixels survive a growth */
	private Surface gpuTarget (ImageInfo info) {
		gpu.makeCurrent ();
		Surface target = Surface.makeRenderTarget (gpu.context, false, info, 0, SurfaceOrigin.TOP_LEFT, PROPS);
		try (Pixmap pixels = Pixmap.make (info, bits, width * 4)) {
			target.writePixels (pixels, 0, 0);
		}
		return target;
	}

	/*
	 * One GC per pass, as the stock path makes one per WM_PAINT: made inside the pass's save so M3ExternalGC restores to it
	 * on dispose. The bounds are the pass in points by the zoom the extension's scaler uses (control.nativeZoom); count is
	 * the number of NO_MERGE_PAINTS events still to come.
	 */
	private void event (Consumer<Event> send, int [] bounds, int count) {
		extension.frame (bounds [0], bounds [1], bounds [2], bounds [3]);
		GC gc = new GCExtension (new M3ExternalGC (extension, SWT.NONE));
		Event event = new Event ();
		event.gc = gc;
		event.count = count;
		event.setBounds (Win32DPIUtils.pixelToPoint (new Rectangle (bounds [0], bounds [1], bounds [2], bounds [3]), control.nativeZoom));
		try {
			send.accept (event);
		} finally {
			gc.dispose ();
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

	/* a listener may dispose the canvas during its Paint event: the native resources then go when the paint ends */
	private void dispose () {
		disposed = true;
		if (!painting) release ();
	}

	/* once: the surface, the readback, the GL context reference, the buffer, the region */
	private void release () {
		if (released) return;
		released = true;
		if (surface != null) surface.close ();
		surface = null;
		if (readback != null) readback.close ();
		readback = null;
		if (gpu != null) gpu.release ();
		buffer.release ();
		if (regionHandle != 0) OS.DeleteObject (regionHandle);
		regionHandle = 0;
	}
}
