/*
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
package org.eclipse.swt.internal.gpu.m3;

import org.eclipse.swt.internal.gpu.*;

import jdk.m3.graphics.*;
import jdk.m3.graphics.*;

/**
 * Per-widget GPU target in the remote-framebuffer model: a content render
 * target (the widget's retained planes), a present render target (content plus
 * overlay planes) and a pool of tile render targets indexed by tile slot
 * (reused across evictions, never reallocated per frame). Every copy is an
 * integer-offset blit with source blending ({@code SRC}), so tiles meet at exact
 * device-pixel seams and nothing is filtered. Replay uses Skija's primitive
 * native entry points on canvas pointers; only cache misses allocate.
 */
final class M3Target implements PlaneBackend.Target {
	static final int TILE = TileGrid.SIZE;
	static final int INTERSECT = M3ClipMode.INTERSECT.ordinal (), SRC = M3BlendMode.SRC.ordinal ();
	static final int TEXT_MASK = PaintKeys.mask (PaintKeys.KIND_TEXT, 0, 0, 0, -1, 0, 0, PaintKeys.BACKEND_M3);

	final M3PlaneBackend backend;
	final M3Surface [] tiles;
	M3Surface frame, present;
	int frameW, frameH;
	final M3Pixmap pixmap = new M3Pixmap ();
	M3ImageInfo info;
	int infoW, infoH;

	M3Target (M3PlaneBackend backend, int capacity) {
		this.backend = backend;
		tiles = new M3Surface [capacity];
	}

	M3ImageInfo info (int w, int h) {
		if (info == null || infoW != w || infoH != h) {
			info = new M3ImageInfo (w, h, M3ColorType.BGRA_8888, M3ColorAlphaType.OPAQUE);
			infoW = w;
			infoH = h;
		}
		return info;
	}

	M3Surface target (int w, int h) {
		M3Surface surface = M3Surface.makeRenderTarget (backend.context, false, new M3ImageInfo (w, h, M3ColorType.BGRA_8888, M3ColorAlphaType.OPAQUE), 0, M3SurfaceOrigin.TOP_LEFT, backend.lcdProps, false);
		GpuCounters.add (GpuCounters.GPU_TILE_BYTES, 4L * w * h);
		GpuCounters.inc (GpuCounters.NATIVE_CREATES);
		return surface;
	}

	static void close (M3Surface surface, int w, int h) {
		if (surface == null) return;
		surface.close ();
		GpuCounters.add (GpuCounters.GPU_TILE_BYTES, -4L * w * h);
	}

	@Override
	public int beginFrame (int w, int h) {
		backend.makeCurrent ();
		if (frame != null && frameW >= w && frameH >= h) return 1;
		close (frame, frameW, frameH);
		close (present, frameW, frameH);
		frameW = Math.max (w, frameW);
		frameH = Math.max (h, frameH);
		frame = target (frameW, frameH);
		present = target (frameW, frameH);
		return frame != null && present != null ? 2 : 0;
	}

	M3Surface tile (int slot) {
		M3Surface tile = tiles [slot];
		if (tile == null) tile = tiles [slot] = target (TILE, TILE);
		return tile;
	}

	@Override
	public boolean rasterFrame (int x, int y, int w, int h, int background, OpStream ops) {
		long canvas = frame.getCanvas ()._ptr;
		int save = M3Canvas._nSave (canvas);
		M3Canvas._nClipRect (canvas, x, y, x + w, y + h, INTERSECT, false);
		if ((background >>> 24) != 0) M3Canvas._nDrawColor (canvas, background, SRC);
		try {
			Replay.run (backend, canvas, ops);
		} finally {
			M3Canvas._nRestoreToCount (canvas, save);
		}
		return true;
	}

	@Override
	public boolean uploadFrame (long bits, int stride, int x, int y, int w, int h) {
		pixmap.reset (info (w, h), bits + (long) y * stride + x * 4L, stride);
		frame.writePixels (pixmap, x, y);
		return true;
	}

	@Override
	public boolean copyFrame (int srcX, int srcY, int dstX, int dstY, int w, int h) {
		/* RFB CopyRect within one surface: snapshot the source block, then an integer-offset SRC blit */
		try (M3Image block = frame.makeImageSnapshot (M3IRect.makeXYWH (srcX, srcY, w, h))) {
			if (block == null) return false;
			jdk.m3.graphics.M3Canvas canvas = frame.getCanvas ();
			int save = M3Canvas._nSave (canvas._ptr);
			M3Canvas._nClipRect (canvas._ptr, dstX, dstY, dstX + w, dstY + h, INTERSECT, false);
			canvas.drawImage (block, dstX, dstY, backend.srcPaint);
			M3Canvas._nRestoreToCount (canvas._ptr, save);
		}
		return true;
	}

	@Override
	public boolean copyFrameToTile (int slot, int tileClientX, int tileClientY, int localX, int localY, int w, int h) {
		M3Surface tile = tile (slot);
		long canvas = tile.getCanvas ()._ptr;
		int save = M3Canvas._nSave (canvas);
		M3Canvas._nClipRect (canvas, localX, localY, localX + w, localY + h, INTERSECT, false);
		try {
			frame.draw (tile.getCanvas (), -tileClientX, -tileClientY, backend.srcPaint);
		} finally {
			M3Canvas._nRestoreToCount (canvas, save);
		}
		return true;
	}

	@Override
	public boolean copyTileToFrame (int slot, int tileClientX, int tileClientY, int localX, int localY, int w, int h) {
		M3Surface tile = tiles [slot];
		if (tile == null) return false;
		long canvas = frame.getCanvas ()._ptr;
		int save = M3Canvas._nSave (canvas);
		M3Canvas._nClipRect (canvas, tileClientX + localX, tileClientY + localY, tileClientX + localX + w, tileClientY + localY + h, INTERSECT, false);
		try {
			tile.draw (frame.getCanvas (), tileClientX, tileClientY, backend.srcPaint);
		} finally {
			M3Canvas._nRestoreToCount (canvas, save);
		}
		return true;
	}

	@Override
	public void tileEvicted (int slot) {
		/* the render target stays in the pool; the next tile in this slot overwrites it */
	}

	@Override
	public boolean overlay (OpStream ops, int x, int y, int w, int h) {
		long canvas = present.getCanvas ()._ptr;
		int save = M3Canvas._nSave (canvas);
		M3Canvas._nClipRect (canvas, x, y, x + w, y + h, INTERSECT, false);
		try {
			frame.draw (present.getCanvas (), 0, 0, backend.srcPaint);
			if (ops != null) Replay.run (backend, canvas, ops);
		} finally {
			M3Canvas._nRestoreToCount (canvas, save);
		}
		return true;
	}

	@Override
	public boolean present (long bits, int stride, int x, int y, int w, int h, int fromPresent) {
		backend.context.flush ();
		pixmap.reset (info (w, h), bits + (long) y * stride + x * 4L, stride);
		return (fromPresent != 0 ? present : frame).readPixels (pixmap, x, y);
	}

	@Override
	public long gpuBytes () {
		long bytes = frame != null ? 8L * frameW * frameH : 0;
		for (M3Surface tile : tiles) if (tile != null) bytes += 4L * TILE * TILE;
		return bytes;
	}

	@Override
	public void dispose () {
		backend.makeCurrent ();
		for (int i = 0; i < tiles.length; i++) {
			close (tiles [i], TILE, TILE);
			tiles [i] = null;
		}
		close (frame, frameW, frameH);
		close (present, frameW, frameH);
		frame = present = null;
		pixmap.close ();
	}
}
