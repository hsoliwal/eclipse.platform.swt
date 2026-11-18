/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
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
 *     Hitesh Soliwal (Synexia) - retained Canvas image layers
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.ExceptionStash;
import org.eclipse.swt.internal.ViewportRuntime;

/**
 * Canvas-owned image conversion and presentation. Layer identity, ordering,
 * transforms, masks and compositing belong to the shared viewport runtime.
 * This helper creates no child Controls. At most sixteen native tile Images
 * are owned by the Canvas and released on explicit clear or widget release.
 */
final class CanvasLayerSupport {

	private static final int TILE_SIZE = 128;
	private static final int CACHE_SIZE = 16;
	private static final PaletteData RGB_PALETTE = new PaletteData (0xff0000, 0xff00, 0xff);

	private final Canvas canvas;
	private ViewportRuntime.ImageLayers layers = new ViewportRuntime.ImageLayers ();
	private final double [] oldBounds = new double [4];
	private final double [] newBounds = new double [4];
	private final float [] elements = new float [6];
	private final float [] previousElements = new float [6];
	private final Image [] cachedImages = new Image [CACHE_SIZE];
	private final int [] cacheX = new int [CACHE_SIZE], cacheY = new int [CACHE_SIZE];
	private final int [] cacheWidth = new int [CACHE_SIZE], cacheHeight = new int [CACHE_SIZE];
	private final boolean [] occupied = new boolean [CACHE_SIZE], valid = new boolean [CACHE_SIZE];
	private final boolean [] protectedTiles = new boolean [CACHE_SIZE];
	private final long [] ages = new long [CACHE_SIZE];
	private long clock;
	private int [] tilePixels;
	private ImageData tileData;

	CanvasLayerSupport (Canvas canvas) {
		this.canvas = canvas;
		canvas.addListener (SWT.Paint, this::paint);
	}

	/** Called by Canvas.releaseWidget, including when Dispose events are suppressed. */
	void dispose () {
		try {
			releaseTiles ();
		} finally {
			layers = null;
			tilePixels = null;
			tileData = null;
		}
	}

	int add (int width, int height, int [] pixels) {
		int layer = layers.addImageLayer (width, height, pixels);
		paintedBounds (layer, newBounds);
		damage (newBounds);
		return layer;
	}

	int [] ids () {
		return layers.imageLayers ();
	}

	void remove (int layer) {
		paintedBounds (layer, oldBounds);
		layers.removeImageLayer (layer);
		try {
			if (layers.imageLayerCount () == 0) {
				tilePixels = null;
				tileData = null;
				releaseTiles ();
			}
		} finally {
			damage (oldBounds);
		}
	}

	void clear () {
		boolean any = false;
		for (int layer : layers.imageLayers ()) {
			paintedBounds (layer, newBounds);
			if (empty (newBounds)) continue;
			if (!any) {
				System.arraycopy (newBounds, 0, oldBounds, 0, 4);
				any = true;
			} else {
				oldBounds [0] = Math.min (oldBounds [0], newBounds [0]);
				oldBounds [1] = Math.min (oldBounds [1], newBounds [1]);
				oldBounds [2] = Math.max (oldBounds [2], newBounds [2]);
				oldBounds [3] = Math.max (oldBounds [3], newBounds [3]);
			}
		}
		layers.clearImageLayers ();
		tilePixels = null;
		tileData = null;
		try {
			releaseTiles ();
		} finally {
			if (any) damage (oldBounds);
		}
	}

	void setImage (int layer, ImageData image) {
		layers.imageLayerOpacity (layer); // Validate the handle before reading caller data.
		int [] pixels = copyPixels (image);
		paintedBounds (layer, oldBounds);
		layers.setImageLayerPixels (layer, image.width, image.height, pixels);
		changed (layer);
	}

	void setZOrder (int layer, int zOrder) {
		if (layers.zOrder (layer) == zOrder) return;
		paintedBounds (layer, oldBounds);
		layers.setZOrder (layer, zOrder);
		changed (layer);
	}

	int zOrder (int layer) {
		return layers.zOrder (layer);
	}

	void setOpacity (int layer, int opacity) {
		if (layers.imageLayerOpacity (layer) == opacity) return;
		paintedBounds (layer, oldBounds);
		layers.setImageLayerOpacity (layer, opacity);
		changed (layer);
	}

	int opacity (int layer) {
		return layers.imageLayerOpacity (layer);
	}

	void setVisible (int layer, boolean visible) {
		if (layers.imageLayerVisible (layer) == visible) return;
		paintedBounds (layer, oldBounds);
		layers.setImageLayerVisible (layer, visible);
		changed (layer);
	}

	boolean visible (int layer) {
		return layers.imageLayerVisible (layer);
	}

	void setInputEnabled (int layer, boolean enabled) {
		layers.setImageLayerInputEnabled (layer, enabled);
	}

	boolean inputEnabled (int layer) {
		return layers.imageLayerInputEnabled (layer);
	}

	void setLocation (int layer, int x, int y) {
		layers.getImageLayerTransform (layer, elements);
		if (elements [4] == (float)x && elements [5] == (float)y) return;
		elements [4] = x;
		elements [5] = y;
		paintedBounds (layer, oldBounds);
		layers.setImageLayerTransform (layer, elements);
		changed (layer);
	}

	void setTransform (int layer, Transform transform) {
		layers.getImageLayerTransform (layer, previousElements);
		elements [0] = elements [3] = 1;
		elements [1] = elements [2] = elements [4] = elements [5] = 0;
		if (transform != null) {
			checkTransform (transform);
			transform.getElements (elements);
		}
		boolean same = true;
		for (int index = 0; index < 6; index++) same &= elements [index] == previousElements [index];
		if (same) return;
		paintedBounds (layer, oldBounds);
		layers.setImageLayerTransform (layer, elements);
		changed (layer);
	}

	void getTransform (int layer, Transform transform) {
		if (transform == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		checkTransform (transform);
		layers.getImageLayerTransform (layer, elements);
		transform.setElements (elements [0], elements [1], elements [2],
				elements [3], elements [4], elements [5]);
	}

	private void checkTransform (Transform transform) {
		if (transform.isDisposed () || transform.getDevice () != canvas.getDisplay ()) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
	}

	void setClip (int layer, Rectangle clip) {
		if (clip == null ? !layers.imageLayerHasClip (layer)
				: layers.imageLayerClipEquals (layer, clip.x, clip.y, clip.width, clip.height)) return;
		paintedBounds (layer, oldBounds);
		if (clip == null) {
			layers.clearImageLayerClip (layer);
		} else {
			layers.setImageLayerClip (layer, clip.x, clip.y, clip.width, clip.height);
		}
		changed (layer);
	}

	void setMask (int layer, byte [] alphaMask) {
		if (alphaMask == null && !layers.imageLayerHasMask (layer)) return;
		paintedBounds (layer, oldBounds);
		layers.setImageLayerMask (layer, alphaMask);
		changed (layer);
	}

	private void paintedBounds (int layer, double [] out) {
		layers.getImageLayerBounds (layer, out);
		if (!layers.imageLayerVisible (layer) || layers.imageLayerOpacity (layer) == 0) {
			out [0] = out [1] = out [2] = out [3] = 0;
		}
	}

	private static boolean empty (double [] bounds) {
		return bounds [2] <= bounds [0] || bounds [3] <= bounds [1];
	}

	private void changed (int layer) {
		paintedBounds (layer, newBounds);
		// Keep disjoint old and new extents separate: tiles in between remain reusable.
		damage (oldBounds);
		if (oldBounds [0] != newBounds [0] || oldBounds [1] != newBounds [1]
				|| oldBounds [2] != newBounds [2] || oldBounds [3] != newBounds [3]) damage (newBounds);
	}

	private void damage (double [] bounds) {
		if (empty (bounds)) return;
		for (int slot = 0; slot < CACHE_SIZE; slot++) {
			if (occupied [slot] && bounds [0] < (long)cacheX [slot] + cacheWidth [slot]
					&& bounds [2] > cacheX [slot] && bounds [1] < (long)cacheY [slot] + cacheHeight [slot]
					&& bounds [3] > cacheY [slot]) valid [slot] = false;
		}
		Rectangle area = canvas.getClientArea ();
		long left = Math.max (area.x, (long)Math.floor (bounds [0]));
		long top = Math.max (area.y, (long)Math.floor (bounds [1]));
		long right = Math.min ((long)area.x + area.width, (long)Math.ceil (bounds [2]));
		long bottom = Math.min ((long)area.y + area.height, (long)Math.ceil (bounds [3]));
		if (right > left && bottom > top && left <= Integer.MAX_VALUE && top <= Integer.MAX_VALUE) {
			canvas.redraw ((int)left, (int)top, (int)(right - left), (int)(bottom - top), false);
		}
	}

	int atPoint (int x, int y) {
		Rectangle area = canvas.getClientArea ();
		if (x < area.x || y < area.y || x >= (long)area.x + area.width
				|| y >= (long)area.y + area.height) return -1;
		return layers.imageLayerAt (x, y);
	}

	/** Converts the ImageData contract to owned straight-alpha ARGB pixels. */
	static int [] copyPixels (ImageData image) {
		if (image == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		long count = (long)image.width * image.height;
		if (image.width <= 0 || image.height <= 0 || count > Integer.MAX_VALUE
				|| image.palette == null || image.data == null
				|| image.alpha < -1 || image.alpha > 255) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (image.depth != 1 && image.depth != 2 && image.depth != 4 && image.depth != 8
				&& image.depth != 16 && image.depth != 24 && image.depth != 32) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		long minimumRowBytes = ((long)image.width * image.depth + 7) / 8;
		if (image.bytesPerLine < minimumRowBytes
				|| (long)image.bytesPerLine * image.height > image.data.length) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		if (image.alpha == -1 && image.alphaData != null && image.alphaData.length < count) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		long maskStride = 0;
		if (image.maskData != null) {
			if (image.maskPad <= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			maskStride = (((image.width + 7L) / 8 + image.maskPad - 1) / image.maskPad) * image.maskPad;
			if (maskStride * image.height > image.maskData.length) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		int [] result = new int [(int)count];
		PaletteData palette = image.palette;
		int [] indexedColors = null;
		if (!palette.isDirect) {
			RGB [] colors = palette.getRGBs ();
			if (colors == null) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			indexedColors = new int [colors.length];
			for (int index = 0; index < colors.length; index++) {
				RGB color = colors [index];
				if (color == null || color.red < 0 || color.red > 255
						|| color.green < 0 || color.green > 255 || color.blue < 0 || color.blue > 255) {
					SWT.error (SWT.ERROR_INVALID_ARGUMENT);
				}
				indexedColors [index] = color.red << 16 | color.green << 8 | color.blue;
			}
		}
		for (int y = 0; y < image.height; y++) {
			int offset = y * image.width;
			image.getPixels (0, y, image.width, result, offset);
			for (int x = 0; x < image.width; x++) {
				int index = offset + x;
				int pixel = result [index];
				int rgb;
				if (palette.isDirect) {
					int red = channel (pixel, palette.redMask, palette.redShift);
					int green = channel (pixel, palette.greenMask, palette.greenShift);
					int blue = channel (pixel, palette.blueMask, palette.blueShift);
					rgb = red << 16 | green << 8 | blue;
				} else {
					if (pixel < 0 || pixel >= indexedColors.length) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
					rgb = indexedColors [pixel];
				}
				int alpha = image.alpha != -1 ? image.alpha
						: image.alphaData == null ? 255 : image.alphaData [index] & 0xff;
				if (image.transparentPixel != -1 && pixel == image.transparentPixel) alpha = 0;
				if (image.maskData != null
						&& (image.maskData [(int)(y * maskStride + (x >>> 3))] & (0x80 >>> (x & 7))) == 0) {
					alpha = 0;
				}
				result [index] = alpha << 24 | rgb;
			}
		}
		return result;
	}

	private static int channel (int pixel, int mask, int shift) {
		int value = pixel & mask;
		return (shift < 0 ? value >>> -shift : value << shift) & 0xff;
	}

	private void paint (Event event) {
		if (layers == null || layers.imageLayerCount () == 0 || canvas.isDisposed ()) return;
		Rectangle area = canvas.getClientArea ();
		long left = Math.max (area.x, event.x);
		long top = Math.max (area.y, event.y);
		long right = Math.min ((long)area.x + area.width, (long)event.x + event.width);
		long bottom = Math.min ((long)area.y + area.height, (long)event.y + event.height);
		right = Math.min (right, (long)Integer.MAX_VALUE + 1);
		bottom = Math.min (bottom, (long)Integer.MAX_VALUE + 1);
		if (right <= left || bottom <= top) return;
		GC gc = event.gc;
		int savedAlpha = gc.getAlpha ();
		Transform savedTransform = new Transform (canvas.getDisplay ());
		boolean transformCaptured = false;
		try {
			gc.getTransform (savedTransform);
			transformCaptured = true;
			gc.setTransform (null);
			gc.setAlpha (255);
			Rectangle clip = gc.getClipping ();
			left = Math.max (left, clip.x);
			top = Math.max (top, clip.y);
			right = Math.min (right, (long)clip.x + clip.width);
			bottom = Math.min (bottom, (long)clip.y + clip.height);
			// The GC retains any nonrectangular clip; its bounds also limit tile work.
			if (right <= left || bottom <= top) return;
			for (int slot = 0; slot < CACHE_SIZE; slot++) {
				protectedTiles [slot] = valid [slot] && occupied [slot]
						&& cacheX [slot] < right && (long)cacheX [slot] + cacheWidth [slot] > left
						&& cacheY [slot] < bottom && (long)cacheY [slot] + cacheHeight [slot] > top;
			}
			for (long y = Math.floorDiv (top, TILE_SIZE) * TILE_SIZE; y < bottom; y += TILE_SIZE) {
				for (long x = Math.floorDiv (left, TILE_SIZE) * TILE_SIZE; x < right; x += TILE_SIZE) {
					int tileX = (int)Math.max (x, area.x), tileY = (int)Math.max (y, area.y);
					int width = (int)(Math.min (x + TILE_SIZE, (long)area.x + area.width) - tileX);
					int height = (int)(Math.min (y + TILE_SIZE, (long)area.y + area.height) - tileY);
					int slot = tile (tileX, tileY, width, height);
					Image image = cachedImages [slot];
					if (image == null) continue; // Empty tiles are cached too.
					int drawX = (int)Math.max (tileX, left), drawY = (int)Math.max (tileY, top);
					int drawWidth = (int)(Math.min ((long)tileX + width, right) - drawX);
					int drawHeight = (int)(Math.min ((long)tileY + height, bottom) - drawY);
					canvas.drawLayerTile (gc, image, drawX - tileX, drawY - tileY,
							drawX, drawY, drawWidth, drawHeight);
				}
			}
		} finally {
			for (int slot = 0; slot < CACHE_SIZE; slot++) protectedTiles [slot] = false;
			try {
				if (transformCaptured && !gc.isDisposed ()) {
					try {
						gc.setTransform (savedTransform.isIdentity () ? null : savedTransform);
					} finally {
						if (!gc.isDisposed ()) gc.setAlpha (savedAlpha);
					}
				}
			} finally {
				savedTransform.dispose ();
			}
		}
	}

	private int tile (int x, int y, int width, int height) {
		int slot = -1, oldest = 0, unprotected = -1;
		for (int index = 0; index < CACHE_SIZE; index++) {
			if (occupied [index] && cacheX [index] == x && cacheY [index] == y) {
				slot = index;
				if (valid [index] && cacheWidth [index] == width && cacheHeight [index] == height) {
					touch (slot);
					return slot;
				}
				break;
			}
			if ((!occupied [index] || !valid [index]) && slot == -1) slot = index;
			if (ages [index] < ages [oldest]) oldest = index;
			if (!protectedTiles [index] && (unprotected == -1 || ages [index] < ages [unprotected])) {
				unprotected = index;
			}
		}
		if (slot == -1) slot = unprotected == -1 ? oldest : unprotected;
		// Keep reusable residents through a paint larger than the cache. If every
		// slot is protected, one victim becomes the replacement lane for this paint
		// instead of evicting all the tiles before their turn to draw arrives.
		protectedTiles [slot] = false;
		if (tilePixels == null || tileData.width != width || tileData.height != height) {
			int [] pixels = tilePixels == null ? new int [TILE_SIZE * TILE_SIZE] : tilePixels;
			ImageData data = new ImageData (width, height, 32, RGB_PALETTE);
			data.alphaData = new byte [width * height];
			tilePixels = pixels;
			tileData = data;
		}
		layers.compositeImageLayers (x, y, width, height, tilePixels);
		boolean visible = false;
		for (int row = 0; row < height; row++) {
			tileData.setPixels (0, row, width, tilePixels, row * width);
			for (int column = 0; column < width; column++) {
				int alpha = tilePixels [row * width + column] >>> 24;
				tileData.alphaData [row * width + column] = (byte)alpha;
				visible |= alpha != 0;
			}
		}
		// Release the previous owner before allocation: even a failed upload leaves
		// at most CACHE_SIZE Images and an invalid slot that the next paint can retry.
		Image previous = cachedImages [slot];
		valid [slot] = occupied [slot] = false;
		cachedImages [slot] = null;
		if (previous != null) previous.dispose ();
		Image image = visible ? new Image (canvas.getDisplay (), tileData) : null;
		cacheX [slot] = x;
		cacheY [slot] = y;
		cacheWidth [slot] = width;
		cacheHeight [slot] = height;
		cachedImages [slot] = image;
		occupied [slot] = valid [slot] = true;
		touch (slot);
		return slot;
	}

	private void touch (int slot) {
		if (clock == Long.MAX_VALUE) {
			for (int index = 0; index < CACHE_SIZE; index++) ages [index] >>>= 1;
			clock >>>= 1;
		}
		ages [slot] = ++clock;
	}

	private void releaseTiles () {
		try (ExceptionStash exceptions = new ExceptionStash ()) {
			for (int slot = 0; slot < CACHE_SIZE; slot++) {
				Image image = cachedImages [slot];
				cachedImages [slot] = null;
				occupied [slot] = valid [slot] = false;
				protectedTiles [slot] = false;
				ages [slot] = 0;
				if (image != null) {
					try {
						image.dispose ();
					} catch (RuntimeException | Error failure) {
						exceptions.stash (failure);
					}
				}
			}
			clock = 0;
		}
	}
}
