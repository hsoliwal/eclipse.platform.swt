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
package org.eclipse.swt.internal.render;

import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.eclipse.swt.internal.win32.*;

/**
 * One drawn plane as its {@link RenderThread} sees it (brief rule 28, leaf R28-3). The UI thread fills and publishes
 * {@link #slabs}; the render thread takes the newest, draws it in place into its canonical frame (the rows a slot shows
 * already are kept: {@link RetainedRows}), copies the canonical frame into the next {@link #frames} value, publishes it and
 * posts {@link #PRESENT} to the plane's window; the UI thread then blits the newest frame when it is the frame of the
 * newest snapshot (an older one is skipped: the newer one is complete). The canvas, the canonical frame and the fonts
 * are the render thread's; the UI thread owns the slab it fills and the frame it blits. Nothing here waits.
 */
public final class RenderTarget {
	/** The message the render thread posts to the plane's window when a frame is finished. */
	public static final int PRESENT = OS.WM_APP + 0x3D1;

	/** A finished frame: the canonical pixels at the time, and the snapshot generation drawn. */
	public static final class Frame {
		/** The pixels (the whole client area). */
		public final BackBuffer buffer = new BackBuffer ();
		/** The generation of the snapshot this frame shows. */
		public long slab;
		/** The client size the frame has pixels for. */
		public int width, height;
	}

	/** Snapshots, UI thread to render thread. */
	public final TripleBuffer<FrameSlab> slabs = new TripleBuffer<> (FrameSlab::new);
	/* the three frames, freed together when the target closes */
	private final Frame [] allFrames = new Frame [3];
	private int framesMade;
	/** Finished frames, render thread to UI thread. */
	public final TripleBuffer<Frame> frames = new TripleBuffer<> (this::newFrame);
	/** The render thread's frame log (frame times, generations); read once the target is quiet. */
	public final FrameLog log = new FrameLog ();
	/** Frames drawn by the render thread, snapshots it never drew (newer ones came first), frames the UI thread blitted. */
	public final AtomicLong rendered = new AtomicLong (), presented = new AtomicLong ();
	private final long hwnd;
	private final Supplier<BufferCanvas> canvasFactory;
	private volatile boolean closed;
	/* the render thread's */
	private BufferCanvas canvas;
	private final BackBuffer canonical = new BackBuffer ();
	private final RetainedRows rows = new RetainedRows (true);
	private final RowPaint row = new RowPaint ();
	private RowRenderer renderer;
	private boolean disposed;

	/**
	 * @param hwnd the plane's window (the completion is posted to it)
	 * @param canvasFactory makes the render thread's canvas, on the render thread, at the first frame
	 */
	public RenderTarget (long hwnd, Supplier<BufferCanvas> canvasFactory) {
		this.hwnd = hwnd;
		this.canvasFactory = canvasFactory;
	}

	/** Asks the render thread to free its resources and drop the target (UI thread, when the plane goes). */
	public void close () {
		closed = true;
	}

	boolean closed () {
		return closed;
	}

	/** Render thread: draws the newest published snapshot when there is one the target has not drawn; answers whether it drew. */
	boolean render () {
		if (!slabs.fresh ()) return false;
		ThreadRole.RENDER.check ("RenderTarget.render");
		FrameSlab slab = slabs.acquire ();
		long begin = System.nanoTime ();
		if (canvas == null) canvas = canvasFactory.get ();
		/* a new canonical frame holds no row */
		if (canonical.growFrame (0, slab.clientWidth, slab.clientHeight)) rows.forget ();
		if (renderer == null || renderer.theme () != slab.theme) renderer = new RowRenderer (slab.theme);
		int left = slab.damageX, top = slab.damageY, right = left + slab.damageWidth, bottom = top + slab.damageHeight;
		canvas.fonts (slab);
		canvas.target (canonical);
		canvas.begin (left, top, right - left, bottom - top);
		rows.begin (canvas, renderer, canonical, left, top, right, bottom, slab.clientWidth, slab.rowsTop, slab.retain);
		try {
			for (int r = 0; r < slab.rows; r++) {
				slab.read (r, row);
				if (slab.keep (r) == FrameSlab.EMPTY) {
					renderer.paintEmpty (canvas, row, left, row.y, right - left, row.height);
				} else {
					rows.paint (row);
				}
			}
		} finally {
			canvas.end ();
			canvas.fonts (null);
		}
		rows.end ();
		Frame frame = frames.writable ();
		frame.buffer.ensureFrame (0, slab.clientWidth, slab.clientHeight);
		OS.BitBlt (frame.buffer.dc, 0, 0, slab.clientWidth, slab.clientHeight, canonical.dc, 0, 0, OS.SRCCOPY);
		frame.slab = slabs.acquired ();
		frame.width = slab.clientWidth;
		frame.height = slab.clientHeight;
		frames.publish ();
		rendered.incrementAndGet ();
		/* rule 24 on the drawing side: a frame that drew every row of the rows area is a full repaint */
		boolean allRows = left <= 0 && right >= slab.clientWidth && (bottom - top) * 10L >= (slab.clientHeight - slab.rowsTop) * 9L;
		log.damage (allRows && rows.rowsIterated > 0 && rows.rowsDrawn == rows.rowsIterated, (long) (right - left) * (bottom - top));
		log.rows (rows.rowsDrawn, rows.kept);
		log.frame (begin, System.nanoTime (), frame.slab);
		OS.PostMessage (hwnd, PRESENT, 0, 0);
		return true;
	}

	/** Render thread: frees the canvas, the canonical frame and the frames once the target is closed. */
	void dispose () {
		if (disposed) return;
		disposed = true;
		if (canvas != null) canvas.dispose ();
		canvas = null;
		rows.release ();
		canonical.release ();
		/* the UI thread no longer blits once it closed the target */
		for (Frame frame : allFrames) {
			if (frame != null) frame.buffer.release ();
		}
	}

	private Frame newFrame () {
		Frame frame = new Frame ();
		allFrames [framesMade++] = frame;
		return frame;
	}

	/**
	 * UI thread: blits the newest finished frame over the rows area of the window when it shows the newest published
	 * snapshot (an older one is skipped, the newer one is coming and is complete).
	 *
	 * @return true when it blitted
	 */
	public boolean present (int rowsTop, int clientWidth, int clientHeight) {
		ThreadRole.RENDER.forbid ("RenderTarget.present");
		Frame frame = frames.acquire ();
		if (frame == null || frame.slab != slabs.generation () || frame.buffer.dc == 0) return false;
		int width = Math.min (clientWidth, frame.width), height = Math.min (clientHeight, frame.height) - rowsTop;
		if (width <= 0 || height <= 0) return false;
		long dc = OS.GetDC (hwnd);
		OS.BitBlt (dc, 0, rowsTop, width, height, frame.buffer.dc, 0, rowsTop, OS.SRCCOPY);
		OS.ReleaseDC (hwnd, dc);
		presented.incrementAndGet ();
		return true;
	}
}
