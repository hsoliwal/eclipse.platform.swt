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
 *     Hitesh Soliwal (Synexia) - retained Canvas image layers
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/** A custom-drawn SWT Canvas, with painting and input owned by the original Control. */
public class Canvas extends Composite {
	private CanvasLayerSupport canvasLayers;
	public Canvas(Composite parent, int style) { super(parent, style); }
	Canvas(Display display, int style) { super(display, style); }
	/**
	 * Scrolls a rectangle of the receiver. The retained pixels move inside the backing store and only the
	 * part of the source that the move uncovered is repainted; children that intersect the rectangle move
	 * with it when <code>all</code> is set.
	 */
	public void scroll(int destX, int destY, int x, int y, int width, int height, boolean all) {
		checkWidget();
		if (width <= 0 || height <= 0) return;
		int dx = destX - x, dy = destY - y;
		if (dx == 0 && dy == 0) return;
		scrollArea(destX, destY, x, y, width, height);
		if (!all) return;
		for (Control child : getChildren()) {
			org.eclipse.swt.graphics.Rectangle bounds = child.getBounds();
			if (bounds.intersects(x, y, width, height)) child.setLocation(bounds.x + dx, bounds.y + dy);
		}
	}

/**
 * Adds a retained raster layer to this Canvas and returns its stable handle.
 * The image pixels are copied; subsequent changes to the ImageData do not
 * affect the layer. New layers are visible, input-enabled, fully opaque and
 * have z order zero. Equal z orders paint in creation order.
 * <p>
 * Layers use Canvas client coordinates, nearest-neighbor affine sampling,
 * eight-bit source-over compositing and per-layer opacity. They are painted
 * by a Paint listener installed on the first call to this method, retaining
 * the ordinary ordering of other Paint listeners. Native child Controls keep
 * their original native stacking and are not transformed or composited.
 * </p><p>
 * Global ImageData alpha overrides alphaData; transparent pixels and icon
 * masks remain transparent. A layer is a flattened image, so opacity applies
 * once to its pixels. This does not isolate arbitrary Paint listeners or
 * create perspective, blend effects or nested group opacity.
 * </p><p>
 * Presentation reuses at most sixteen native image tiles of up to 128 by 128
 * pixels. Clearing the layers or disposing this Canvas releases that cache.
 * Moving or changing a layer repaints its affected client regions.
 * </p>
 *
 * @param image the source pixels (cannot be null)
 * @return a Canvas-local handle, never reused by this Canvas
 * @exception IllegalArgumentException if the image is null or invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int addLayer (ImageData image) {
	checkWidget ();
	int [] pixels = CanvasLayerSupport.copyPixels (image);
	if (canvasLayers == null) canvasLayers = new CanvasLayerSupport (this);
	return canvasLayers.add (image.width, image.height, pixels);
}

/**
 * Returns the live layer handles in back-to-front paint order.
 * The returned array is a caller-owned snapshot.
 *
 * @return the layer handles, or an empty array
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int [] getLayerIds () {
	checkWidget ();
	return canvasLayers == null ? new int [0] : canvasLayers.ids ();
}

/**
 * Removes a layer and releases its pixels and mask. Its handle becomes invalid.
 *
 * @param layer a live layer handle returned by this Canvas
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void removeLayer (int layer) {
	checkWidget ();
	layerSupport ().remove (layer);
}

/**
 * Removes all layers and releases their pixels, masks and presentation buffers.
 * Previously returned handles remain invalid after subsequent additions.
 *
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void removeAllLayers () {
	checkWidget ();
	if (canvasLayers != null) canvasLayers.clear ();
}

/**
 * Replaces a layer's pixels with a copy of the supplied image. Placement,
 * clipping, z order, visibility and opacity are preserved. A mask is retained
 * only if the image dimensions are unchanged; otherwise it is cleared.
 *
 * @param layer a live layer handle returned by this Canvas
 * @param image the replacement source pixels
 * @exception IllegalArgumentException if the handle or image is invalid, or image is null
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerImage (int layer, ImageData image) {
	checkWidget ();
	layerSupport ().setImage (layer, image);
}

/**
 * Sets a layer's stacking depth. Lower values paint first; all int values are
 * valid. Equal depths retain creation order, including after reordering.
 *
 * @param layer a live layer handle returned by this Canvas
 * @param zOrder the stacking depth
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerZOrder (int layer, int zOrder) {
	checkWidget ();
	layerSupport ().setZOrder (layer, zOrder);
}

/**
 * Returns a layer's stacking depth.
 * @param layer a live layer handle returned by this Canvas
 * @return the stacking depth
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int getLayerZOrder (int layer) {
	checkWidget ();
	return layerSupport ().zOrder (layer);
}

/**
 * Sets the opacity of the flattened layer, multiplied with its pixel and mask
 * alpha. Zero is transparent and 255 is fully opaque.
 * @param layer a live layer handle returned by this Canvas
 * @param opacity an opacity from 0 through 255
 * @exception IllegalArgumentException if the handle or opacity is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerOpacity (int layer, int opacity) {
	checkWidget ();
	layerSupport ().setOpacity (layer, opacity);
}

/**
 * Returns a layer's opacity.
 * @param layer a live layer handle returned by this Canvas
 * @return an opacity from 0 through 255
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int getLayerOpacity (int layer) {
	checkWidget ();
	return layerSupport ().opacity (layer);
}

/**
 * Sets whether a layer participates in painting and picking. Hidden pixels are
 * retained so showing the layer again restores its contents.
 * @param layer a live layer handle returned by this Canvas
 * @param visible whether the layer is visible
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerVisible (int layer, boolean visible) {
	checkWidget ();
	layerSupport ().setVisible (layer, visible);
}

/**
 * Returns the explicit visibility of a layer.
 * @param layer a live layer handle returned by this Canvas
 * @return whether the layer is visible
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public boolean getLayerVisible (int layer) {
	checkWidget ();
	return layerSupport ().visible (layer);
}

/**
 * Sets whether picking may return a layer. This does not change its rendering
 * or deliver synthetic events. Mouse and keyboard events remain Canvas events.
 * @param layer a live layer handle returned by this Canvas
 * @param enabled whether the layer participates in picking
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerInputEnabled (int layer, boolean enabled) {
	checkWidget ();
	layerSupport ().setInputEnabled (layer, enabled);
}

/**
 * Returns whether a layer participates in picking.
 * @param layer a live layer handle returned by this Canvas
 * @return whether picking is enabled
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public boolean getLayerInputEnabled (int layer) {
	checkWidget ();
	return layerSupport ().inputEnabled (layer);
}

/**
 * Moves the layer's local origin, preserving the linear part of its transform.
 * Coordinates use the same float precision as SWT Transform elements.
 * @param layer a live layer handle returned by this Canvas
 * @param x the Canvas x coordinate
 * @param y the Canvas y coordinate
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerLocation (int layer, int x, int y) {
	checkWidget ();
	layerSupport ().setLocation (layer, x, y);
}

/**
 * Copies a finite, invertible affine transform from layer-local pixels to Canvas
 * coordinates. Null resets the identity. The caller retains the Transform and
 * may change or dispose it after this call. Sampling uses nearest neighbors.
 * @param layer a live layer handle returned by this Canvas
 * @param transform the transform, or null for identity
 * @exception IllegalArgumentException if the handle, transform or its device is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerTransform (int layer, Transform transform) {
	checkWidget ();
	layerSupport ().setTransform (layer, transform);
}

/**
 * Copies the layer's transform into a caller-owned Transform on the same Display.
 * @param layer a live layer handle returned by this Canvas
 * @param transform the destination transform (cannot be null)
 * @exception IllegalArgumentException if the handle or destination is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void getLayerTransform (int layer, Transform transform) {
	checkWidget ();
	layerSupport ().getTransform (layer, transform);
}

/**
 * Copies an optional rectangular clip in layer-local pixel coordinates. Null
 * clears the clip. Empty clips hide the layer; negative dimensions are invalid.
 * @param layer a live layer handle returned by this Canvas
 * @param clip the local clip, or null
 * @exception IllegalArgumentException if the handle or clip is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerClip (int layer, Rectangle clip) {
	checkWidget ();
	layerSupport ().setClip (layer, clip);
}

/**
 * Copies a mask containing one unsigned alpha byte per source pixel, in row-major
 * order. It multiplies the source alpha and layer opacity. Null clears the mask.
 * @param layer a live layer handle returned by this Canvas
 * @param alphaMask the mask, or null
 * @exception IllegalArgumentException if the handle or mask length is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerMask (int layer, byte [] alphaMask) {
	checkWidget ();
	layerSupport ().setMask (layer, alphaMask);
}

/**
 * Returns the topmost visible, input-enabled layer with nonzero effective alpha
 * at a Canvas client pixel, or -1. Clipping, masks and transforms match painting.
 * Pixel centers are sampled. Points outside the client area return -1.
 * @param x the Canvas x coordinate
 * @param y the Canvas y coordinate
 * @return the picked layer handle, or -1
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int getLayerAtPoint (int x, int y) {
	checkWidget ();
	return canvasLayers == null ? -1 : canvasLayers.atPoint (x, y);
}

private CanvasLayerSupport layerSupport () {
	if (canvasLayers == null) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
	return canvasLayers;
}

/* The mobile graphics transport has source-over image drawing and no XOR mode. */
void drawLayerTile (GC gc, Image tile, int sourceX, int sourceY, int x, int y, int width, int height) {
	gc.drawImage (tile, sourceX, sourceY, width, height, x, y, width, height);
}

@Override
void releaseWidget () {
	try {
		releaseLayers ();
	} finally {
		super.releaseWidget ();
	}
}

private void releaseLayers () {
	CanvasLayerSupport support = canvasLayers;
	canvasLayers = null;
	if (support != null) support.dispose ();
}
}
