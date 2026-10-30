/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/, or (at your
 * option) under the Apache License, Version 2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Synexia (Hitesh Soliwal) - initial API and implementation (GC seam, leaf 2)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.function.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;

import io.github.humbleui.skija.Surface;

/**
 * The M3 renderer's side of the GC seam: hands {@link M3ExternalGC} the surface a drawn plane already owns.
 * <p>
 * Pattern (Initiative 31 <code>SkiaGlCanvasExtension</code>, PATTERN_ONLY): that class owns a GL context, a render
 * target and a surface per <code>Canvas</code>, and keeps a snapshot image of the last frame to restore the pixels
 * outside the damage. A drawn plane (<code>DrawnPlane</code>, <code>RenderTarget</code>) already owns its surface
 * (<code>M3RowCanvas.target</code>: <code>Surface.makeRasterDirect</code> over the DIB section of the
 * {@link BackBuffer}, or <code>Surface.makeRenderTarget</code> on <code>M3Gpu</code>) and the back buffer retains the
 * previous frame, so this class owns nothing: it adapts what the plane has, the plane's frame is the GC's clip, and
 * damage stays with the back buffer (the plane presents what it drew; a redraw request invalidates the control so the
 * plane's own paint presents again). Leaf 3 constructs it per plane and hands
 * <code>new GCExtension (new M3ExternalGC (extension, SWT.NONE))</code> to the <code>PaintListener</code>s.
 */
public final class M3PlaneCanvasExtension implements IM3CanvasExtension {
	private final Control control;
	private final BackBuffer buffer;
	private final Supplier<Surface> surface;
	private final M3Resources resources;
	private final DpiScalerUtil scaler;
	/* the damage of the frame being drawn, in client pixels; empty between frames */
	private final Rectangle frame = new Rectangle (0, 0, 0, 0);

	/**
	 * @param control the plane's widget: the drawable, device, colours, font and native zoom of the GC
	 * @param buffer the plane's back buffer: the pixels the surface draws into, the extent when no frame is set
	 * @param surface the plane's surface over that buffer, read per call (<code>M3RowCanvas.target</code> replaces it
	 *                when the buffer grows)
	 */
	public M3PlaneCanvasExtension (Control control, BackBuffer buffer, Supplier<Surface> surface) {
		this.control = control;
		this.buffer = buffer;
		this.surface = surface;
		this.resources = new M3Resources (control, this);
		this.scaler = new DpiScalerUtil (() -> control.nativeZoom);
	}

	/**
	 * The damage of the frame being drawn, in client pixels: the GC's initial clip and the paint event's bounds.
	 */
	public void frame (int x, int y, int width, int height) {
		frame.x = x;
		frame.y = y;
		frame.width = width;
		frame.height = height;
	}

	@Override
	public IM3Surface getSurface () {
		Surface current = surface.get ();
		return current == null ? null : new M3SurfaceAdapter (current);
	}

	@Override
	public IM3Resources getResources () {
		return resources;
	}

	@Override
	public IM3Surface createSupportSurface (int width, int height) {
		Surface current = surface.get ();
		Surface support = current == null ? null : current.makeSurface (width, height);
		return support == null ? null : new M3SurfaceAdapter (support);
	}

	@Override
	public DpiScalerUtil getScaler () {
		return scaler;
	}

	@Override
	public Drawable getDrawable () {
		return control;
	}

	@Override
	public Rectangle getClientArea () {
		if (frame.isEmpty ()) return new Rectangle (0, 0, buffer.width, buffer.height);
		return new Rectangle (frame.x, frame.y, frame.width, frame.height);
	}

	@Override
	public Device getDevice () {
		return control.getDisplay ();
	}

	@Override
	public Rectangle getBounds () {
		return getClientArea ();
	}

	@Override
	public void redraw (int x, int y, int width, int height, boolean all) {
		control.redraw (x, y, width, height, all);
	}
}
