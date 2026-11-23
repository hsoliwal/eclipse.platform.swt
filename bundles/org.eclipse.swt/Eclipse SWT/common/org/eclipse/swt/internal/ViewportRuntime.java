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
 *     Hitesh Soliwal (Synexia) - compact selection topology operations
 *******************************************************************************/
package org.eclipse.swt.internal;

import java.util.*;

/**
 * Shared model-only runtime for viewport-driven SWT controls.
 *
 * <p>This class owns no native resources and does not replace SWT's public
 * {@code GC}, {@code ScrollBar}, {@code Item}, or event contracts. It centralizes
 * origin/layer invalidation and scrollbar fixed-point calculations so platform
 * widgets and custom widgets can share the same viewport mechanics.</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ViewportRuntime {

	public static final int BODY = 1 << 0;
	public static final int FROZEN = 1 << 1;
	public static final int HEADER = 1 << 2;
	public static final int EDITOR = 1 << 3;
	public static final int SCROLLBAR = 1 << 4;
	public static final int FEEDBACK = 1 << 5;
	public static final int ALL = BODY | FROZEN | HEADER | EDITOR | SCROLLBAR | FEEDBACK;

	public static final int AUTO = 0;
	public static final int ALWAYS = 1;
	public static final int NEVER = 2;

	public record PixelLayout (
			boolean horizontalVisible,
			boolean verticalVisible,
			boolean cornerVisible,
			int bodyWidth,
			int bodyHeight,
			long contentWidth,
			long contentHeight) {
	}

	public record RowLayout (
			boolean horizontalVisible,
			boolean verticalVisible,
			boolean cornerVisible,
			int bodyWidth,
			int bodyHeight,
			int headerWidth,
			int headerHeight,
			int visibleRows,
			long logicalRows,
			long estimatedContentHeight,
			long logicalContentWidth) {
	}

	/**
	 * Widget-owned isolated raster layers, with stable integer identities and
	 * back-to-front z ordering. Primitive columns hold state; only image pixels
	 * and optional masks retain per-layer arrays. No native resource or per-layer
	 * structural object is created. All operations are UI-thread confined by the
	 * owning widget. Removed identities retain primitive tombstones until owner
	 * disposal; their pixel and mask arrays are released immediately.
	 *
	 * <p>This raster plane is separate from retained vector-command replay. Each
	 * input image is already flattened; source-over layer opacity is applied once.
	 * Nested group opacity, native child controls, color profiles and perspective
	 * are outside this model's contract.</p>
	 *
	 * @noreference This class is not intended to be referenced by clients.
	 */
	public static final class ImageLayers {

		private static final int ROOT = 0;
		private static final int NONE = -1;
		private static final byte IMAGE_LAYER = 1;
		private static final byte REMOVED = 0;
		private static final byte HAS_CLIP = 1;
		private static final byte IMAGE_HIDDEN = 1 << 1;
		private static final byte IMAGE_INPUT_DISABLED = 1 << 2;
		private static final byte PIXELS_OPAQUE = 1 << 3;
		private static final byte MASK_OPAQUE = 1 << 4;
		private static final byte INTEGER_TRANSLATION = 1 << 5;
		private static final byte SOURCE_EMPTY = 1 << 6;
		private static final int OPAQUE_TRANSLATION = PIXELS_OPAQUE | MASK_OPAQUE | INTEGER_TRANSLATION;

		private byte [] kinds = new byte [16];
		private byte [] flags = new byte [16];
		private int [] nextSibling = new int [16];
		private int [] previousSibling = new int [16];
		private int [] zOrders = new int [16];
		private int [] widths = new int [16];
		private int [] heights = new int [16];
		private int [][] pixels = new int [16][];
		private byte [][] imageMasks = new byte [16][];
		private int [] imageOpacities = new int [16];
		private int [] imageClipX = new int [16];
		private int [] imageClipY = new int [16];
		private int [] imageClipWidth = new int [16];
		private int [] imageClipHeight = new int [16];
		private float [] transforms = new float [16 * 6];
		private double [] imageInverses = new double [16 * 4];
		private double [] imageBounds = new double [16 * 4];
		private int first = NONE, last = NONE;
		private int nodeCount = 1;
		private int imageLayerCount;

		/**
		 * Adds an isolated raster layer to this widget-owned image plane. Pixels are
		 * straight (unassociated) ARGB, in row-major order, and are copied. New layers
		 * are visible, input enabled and fully opaque, with identity transform and
		 * z-order zero. Equal z-order retains creation order.
		 */
		public int addImageLayer (int width, int height, int [] argb) {
			int [] copy = copyImagePixels (width, height, argb);
			int node = newImageLayer ();
			widths [node] = width;
			heights [node] = height;
			pixels [node] = copy;
			imageOpacities [node] = 255;
			flags [node] |= MASK_OPAQUE;
			updateImagePixelOpacity (node);
			updateImageTransform (node);
			imageLayerCount++;
			return node;
		}

		/**
		 * Replaces a layer's copied pixels. A size change removes its pixel mask;
		 * z-order, transform, visibility, opacity and local clip remain unchanged.
		 */
		public void setImageLayerPixels (int node, int width, int height, int [] argb) {
			requireImageLayer (node);
			int [] copy = copyImagePixels (width, height, argb);
			if (widths [node] != width || heights [node] != height) {
				imageMasks [node] = null;
				flags [node] |= MASK_OPAQUE;
			}
			widths [node] = width;
			heights [node] = height;
			pixels [node] = copy;
			updateImagePixelOpacity (node);
			updateImageBounds (node);
		}

		/** Releases copied pixels and mask; the removed identity is never reused. */
		public void removeImageLayer (int node) {
			requireImageLayer (node);
			unlinkImageLayer (node);
			pixels [node] = null;
			imageMasks [node] = null;
			kinds [node] = REMOVED;
			Arrays.fill (transforms, node * 6, node * 6 + 6, 0);
			Arrays.fill (imageInverses, node * 4, node * 4 + 4, 0);
			Arrays.fill (imageBounds, node * 4, node * 4 + 4, 0);
			flags [node] = 0;
			imageOpacities [node] = 0;
			imageLayerCount--;
		}

		/** Removes all layers while retaining invalid identities as primitive tombstones. */
		public void clearImageLayers () {
			for (int node = first; node != NONE;) {
				int next = nextSibling [node];
				if (kinds [node] == IMAGE_LAYER) removeImageLayer (node);
				node = next;
			}
		}

		public int imageLayerCount () {
			return imageLayerCount;
		}

		/** Returns a caller-owned snapshot in back-to-front order, including hidden layers. */
		public int [] imageLayers () {
			int [] result = new int [imageLayerCount];
			int offset = 0;
			for (int node = first; node != NONE; node = nextSibling [node]) {
				if (kinds [node] == IMAGE_LAYER) result [offset++] = node;
			}
			return result;
		}

		public void setImageLayerVisible (int node, boolean visible) {
			requireImageLayer (node);
			if (visible) flags [node] &= ~IMAGE_HIDDEN;
			else flags [node] |= IMAGE_HIDDEN;
		}

		public boolean imageLayerVisible (int node) {
			requireImageLayer (node);
			return (flags [node] & IMAGE_HIDDEN) == 0;
		}

		/** Input transparency does not change the rendered image. */
		public void setImageLayerInputEnabled (int node, boolean enabled) {
			requireImageLayer (node);
			if (enabled) flags [node] &= ~IMAGE_INPUT_DISABLED;
			else flags [node] |= IMAGE_INPUT_DISABLED;
		}

		public boolean imageLayerInputEnabled (int node) {
			requireImageLayer (node);
			return (flags [node] & IMAGE_INPUT_DISABLED) == 0;
		}

		/** Sets opacity applied once to the isolated image, in the inclusive range 0..255. */
		public void setImageLayerOpacity (int node, int alpha) {
			requireImageLayer (node);
			if (alpha < 0 || alpha > 255) throw new IllegalArgumentException ("invalid image opacity");
			imageOpacities [node] = alpha;
		}

		public int imageLayerOpacity (int node) {
			requireImageLayer (node);
			return imageOpacities [node];
		}

		/** Copies an unsigned 8-bit mask, or removes the mask when null. */
		public void setImageLayerMask (int node, byte [] mask) {
			requireImageLayer (node);
			if (mask != null && mask.length != pixels [node].length) {
				throw new IllegalArgumentException ("image mask size mismatch");
			}
			byte [] copy = mask == null ? null : mask.clone ();
			boolean opaque = true;
			if (copy != null) {
				for (byte alpha : copy) {
					if ((alpha & 255) != 255) {
						opaque = false;
						break;
					}
				}
			}
			imageMasks [node] = copy;
			if (opaque) flags [node] |= MASK_OPAQUE;
			else flags [node] &= ~MASK_OPAQUE;
		}

		public boolean imageLayerHasMask (int node) {
			requireImageLayer (node);
			return imageMasks [node] != null;
		}

		/**
		 * Copies six finite, invertible SWT-order affine elements
		 * {m11, m12, m21, m22, dx, dy}. Reuses the layer's primitive transform slot.
		 */
		public void setImageLayerTransform (int node, float [] elements) {
			requireImageLayer (node);
			if (elements == null || elements.length != 6) {
				throw new IllegalArgumentException ("invalid image transform elements");
			}
			for (float element : elements) {
				if (!Float.isFinite (element)) throw new IllegalArgumentException ("non-finite image transform");
			}
			double determinant = (double)elements [0] * elements [3] - (double)elements [1] * elements [2];
			if (!Double.isFinite (determinant) || determinant == 0d) {
				throw new IllegalArgumentException ("non-invertible image transform");
			}
			System.arraycopy (elements, 0, transforms, node * 6, 6);
			updateImageTransform (node);
		}

		/** Copies the transform into the first six caller-owned output elements. */
		public void getImageLayerTransform (int node, float [] out) {
			requireImageLayer (node);
			if (out == null || out.length < 6) throw new IllegalArgumentException ("image transform output too small");
			System.arraycopy (transforms, node * 6, out, 0, 6);
		}

		/**
		 * Copies conservative clipped device bounds as {left, top, right, bottom}.
		 * Visibility, opacity and input state do not alter these bounds. An empty
		 * source clip returns four zeroes; extra output elements are untouched.
		 * A nonempty extent that collapses at double precision is rounded outward
		 * by one representable value so an invalidation caller can retain it.
		 */
		public void getImageLayerBounds (int node, double [] out) {
			requireImageLayer (node);
			if (out == null || out.length < 4) throw new IllegalArgumentException ("image bounds output too small");
			System.arraycopy (imageBounds, node * 4, out, 0, 4);
			if ((flags [node] & SOURCE_EMPTY) == 0) {
				if (out [2] == out [0]) out [2] = Math.nextUp (out [2]);
				if (out [3] == out [1]) out [3] = Math.nextUp (out [3]);
			}
		}

		/** Sets a half-open rectangular clip in untransformed source-pixel coordinates. */
		public void setImageLayerClip (int node, int x, int y, int width, int height) {
			requireImageLayer (node);
			if (width < 0 || height < 0) throw new IllegalArgumentException ("negative image clip");
			imageClipX [node] = x;
			imageClipY [node] = y;
			imageClipWidth [node] = width;
			imageClipHeight [node] = height;
			flags [node] |= HAS_CLIP;
			updateImageBounds (node);
		}

		public void clearImageLayerClip (int node) {
			requireImageLayer (node);
			flags [node] &= ~HAS_CLIP;
			updateImageBounds (node);
		}

		public boolean imageLayerHasClip (int node) {
			requireImageLayer (node);
			return (flags [node] & HAS_CLIP) != 0;
		}

		public boolean imageLayerClipEquals (int node, int x, int y, int width, int height) {
			requireImageLayer (node);
			return (flags [node] & HAS_CLIP) != 0 && imageClipX [node] == x && imageClipY [node] == y
					&& imageClipWidth [node] == width && imageClipHeight [node] == height;
		}

		/**
		 * Finds the frontmost eligible image at the center of the specified device
		 * pixel, or -1. Uses the same nearest-neighbor sample, local clip, mask and
		 * rounded effective alpha as composition. Transparent or hidden pixels and
		 * input-disabled layers do not intercept the point.
		 */
		public int imageLayerAt (int x, int y) {
			return imageLayerAtCenter (x + 0.5d, y + 0.5d);
		}

		/** Fractional coordinates use the same +0.5 pixel-center convention. */
		public int imageLayerAt (float x, float y) {
			if (!Float.isFinite (x) || !Float.isFinite (y)) {
				throw new IllegalArgumentException ("non-finite image point");
			}
			return imageLayerAtCenter ((double)x + 0.5d, (double)y + 0.5d);
		}

		private int imageLayerAtCenter (double x, double y) {
			for (int node = last; node != NONE; node = previousSibling [node]) {
				if (kinds [node] != IMAGE_LAYER
						|| (flags [node] & (IMAGE_HIDDEN | IMAGE_INPUT_DISABLED | SOURCE_EMPTY)) != 0
						|| imageOpacities [node] == 0) continue;
				int bounds = node * 4;
				// Keep the geometric rejection conservative at rounded affine edges.
				if (x < Math.nextDown (imageBounds [bounds]) || x > Math.nextUp (imageBounds [bounds + 2])
						|| y < Math.nextDown (imageBounds [bounds + 1]) || y > Math.nextUp (imageBounds [bounds + 3])) continue;
				int pixel = imageSample (node, x, y);
				if ((pixel >>> 24) != 0) return node;
			}
			return NONE;
		}

		/**
		 * Composites the requested device-space tile into straight ARGB. The first
		 * width*height output entries are cleared; any remaining output is untouched.
		 * Layers use nearest-neighbor sampling and source-over in the supplied 8-bit
		 * channel space, with rounded 8-bit alpha after each layer. This is raster
		 * layer opacity, not opacity applied separately to individual drawing calls.
		 * No native graphics object or viewport-sized scratch buffer is allocated.
		 */
		public void compositeImageLayers (int x, int y, int width, int height, int [] out) {
			compositeImageLayers (x, y, width, height, out, 0, width);
		}

		/**
		 * Composites directly into a caller-owned, row-strided ARGB backing array,
		 * without a temporary contiguous copy. Only the addressed tile rows are
		 * cleared and painted; surrounding data, row padding and suffix survive.
		 * All geometry and output bounds are validated before any mutation.
		 *
		 * @param outOffset first destination pixel in {@code out}
		 * @param outStride distance in ints between consecutive output rows
		 */
		public void compositeImageLayers (int x, int y, int width, int height, int [] out,
				int outOffset, int outStride) {
			long count = (long)width * height;
			if (width < 0 || height < 0 || count > Integer.MAX_VALUE
					|| out == null || outOffset < 0 || outOffset > out.length
					|| outStride < width) {
				throw new IllegalArgumentException ("invalid image tile");
			}
			// Zero-area rectangles never address a pixel: preserve the original
			// packed method's no-op, even for an extreme hypothetical row span.
			if (count == 0) return;
			long end = (long)outOffset + ((long)height - 1) * outStride + width;
			if (end > out.length) throw new IllegalArgumentException ("invalid image tile");
			if (outStride == width) {
				Arrays.fill (out, outOffset, outOffset + (int)count, 0);
			} else {
				for (int row = 0; row < height; row++) {
					int at = outOffset + row * outStride;
					Arrays.fill (out, at, at + width, 0);
				}
			}
			int start = first;
			// An opaque translated layer discards every lower destination sample.
			// Keep all surviving layers in back-to-front order: rounded 8-bit
			// source-over is not interchangeable with front-to-back evaluation.
			for (int node = last; node != NONE; node = previousSibling [node]) {
				if ((flags [node] & (OPAQUE_TRANSLATION | IMAGE_HIDDEN)) != OPAQUE_TRANSLATION
						|| imageOpacities [node] != 255) continue;
				int bounds = node * 4;
				if (imageBounds [bounds] <= x && imageBounds [bounds + 1] <= y
						&& imageBounds [bounds + 2] >= (double)x + width
						&& imageBounds [bounds + 3] >= (double)y + height) {
					start = node;
					break;
				}
			}
			for (int node = start; node != NONE; node = nextSibling [node]) {
				if (kinds [node] != IMAGE_LAYER || (flags [node] & IMAGE_HIDDEN) != 0
						|| imageOpacities [node] == 0) continue;
				compositeImageTile (node, x, y, width, height, out, outOffset, outStride);
			}
		}

		private void compositeImageTile (int node, int x, int y, int width, int height, int [] out,
				int outOffset, int outStride) {
			int bounds = node * 4;
			if ((flags [node] & SOURCE_EMPTY) != 0) return;
			int startX = Math.max (0, (int)Math.floor (imageBounds [bounds] - x));
			int startY = Math.max (0, (int)Math.floor (imageBounds [bounds + 1] - y));
			int endX = Math.min (width, (int)Math.ceil (imageBounds [bounds + 2] - x));
			int endY = Math.min (height, (int)Math.ceil (imageBounds [bounds + 3] - y));
			if (startX >= endX || startY >= endY) return;
			if ((flags [node] & INTEGER_TRANSLATION) != 0) {
				compositeTranslatedImage (node, x, y, outOffset, outStride, startX, startY, endX, endY, out);
				return;
			}
			for (int row = startY; row < endY; row++) {
				int offset = outOffset + row * outStride + startX;
				for (int column = startX; column < endX; column++, offset++) {
					int source = imageSample (node, (double)x + column + 0.5d, (double)y + row + 0.5d);
					out [offset] = imageSourceOver (source, out [offset]);
				}
			}
		}

		private void compositeTranslatedImage (int node, int x, int y, int outOffset, int outStride,
				int startX, int startY, int endX, int endY, int [] out) {
			int transform = node * 6;
			int sourceX = (int)((long)x + startX - (int)transforms [transform + 4]);
			int sourceY = (int)((long)y + startY - (int)transforms [transform + 5]);
			int sourceWidth = widths [node], length = endX - startX;
			boolean opaque = imageOpacities [node] == 255
					&& (flags [node] & (PIXELS_OPAQUE | MASK_OPAQUE)) == (PIXELS_OPAQUE | MASK_OPAQUE);
			for (int row = startY; row < endY; row++) {
				int source = (sourceY + row - startY) * sourceWidth + sourceX;
				int destination = outOffset + row * outStride + startX;
				if (opaque) {
					System.arraycopy (pixels [node], source, out, destination, length);
				} else {
					for (int column = 0; column < length; column++, source++, destination++) {
						out [destination] = imageSourceOver (imagePixel (node, source), out [destination]);
					}
				}
			}
		}

		private void updateImagePixelOpacity (int node) {
			flags [node] |= PIXELS_OPAQUE;
			for (int pixel : pixels [node]) {
				if ((pixel >>> 24) != 255) {
					flags [node] &= ~PIXELS_OPAQUE;
					return;
				}
			}
		}

		/* Recompute derived geometry only when its source state changes. */
		private void updateImageBounds (int node) {
			double left = 0, top = 0, right = widths [node], bottom = heights [node];
			if ((flags [node] & HAS_CLIP) != 0) {
				left = Math.max (left, imageClipX [node]);
				top = Math.max (top, imageClipY [node]);
				right = Math.min (right, (long)imageClipX [node] + imageClipWidth [node]);
				bottom = Math.min (bottom, (long)imageClipY [node] + imageClipHeight [node]);
			}
			int bounds = node * 4;
			if (right <= left || bottom <= top) {
				flags [node] |= SOURCE_EMPTY;
				Arrays.fill (imageBounds, bounds, bounds + 4, 0);
				return;
			}
			flags [node] &= ~SOURCE_EMPTY;
			int offset = node * 6;
			double a = transforms [offset], b = transforms [offset + 1];
			double c = transforms [offset + 2], d = transforms [offset + 3];
			double dx = transforms [offset + 4], dy = transforms [offset + 5];
			double x1 = a * left + c * top + dx, x2 = a * right + c * top + dx;
			double x3 = a * left + c * bottom + dx, x4 = a * right + c * bottom + dx;
			double y1 = b * left + d * top + dy, y2 = b * right + d * top + dy;
			double y3 = b * left + d * bottom + dy, y4 = b * right + d * bottom + dy;
			imageBounds [bounds] = Math.min (Math.min (x1, x2), Math.min (x3, x4));
			imageBounds [bounds + 1] = Math.min (Math.min (y1, y2), Math.min (y3, y4));
			imageBounds [bounds + 2] = Math.max (Math.max (x1, x2), Math.max (x3, x4));
			imageBounds [bounds + 3] = Math.max (Math.max (y1, y2), Math.max (y3, y4));
		}

		/* Finite float matrices can overflow a float determinant; use double arithmetic. */
		private void updateImageTransform (int node) {
			int offset = node * 6;
			double a = transforms [offset], b = transforms [offset + 1];
			double c = transforms [offset + 2], d = transforms [offset + 3];
			double dx = transforms [offset + 4], dy = transforms [offset + 5];
			double determinant = a * d - b * c;
			int inverse = node * 4;
			imageInverses [inverse] = d / determinant;
			imageInverses [inverse + 1] = -b / determinant;
			imageInverses [inverse + 2] = -c / determinant;
			imageInverses [inverse + 3] = a / determinant;
			if (a == 1 && b == 0 && c == 0 && d == 1
					&& dx >= Integer.MIN_VALUE && dx <= Integer.MAX_VALUE && dx == Math.rint (dx)
					&& dy >= Integer.MIN_VALUE && dy <= Integer.MAX_VALUE && dy == Math.rint (dy)) {
				flags [node] |= INTEGER_TRANSLATION;
			} else {
				flags [node] &= ~INTEGER_TRANSLATION;
			}
			updateImageBounds (node);
		}

		private int imageSample (int node, double x, double y) {
			int inverse = node * 4, transform = node * 6;
			double dx = x - transforms [transform + 4], dy = y - transforms [transform + 5];
			double localX = imageInverses [inverse] * dx + imageInverses [inverse + 2] * dy;
			double localY = imageInverses [inverse + 1] * dx + imageInverses [inverse + 3] * dy;
			if (!(localX >= 0 && localX < widths [node] && localY >= 0 && localY < heights [node])) return 0;
			if ((flags [node] & HAS_CLIP) != 0
					&& !(localX >= imageClipX [node]
					&& localX < (long)imageClipX [node] + imageClipWidth [node]
					&& localY >= imageClipY [node]
					&& localY < (long)imageClipY [node] + imageClipHeight [node])) return 0;
			int offset = (int)localY * widths [node] + (int)localX;
			return imagePixel (node, offset);
		}

		private int imagePixel (int node, int offset) {
			int pixel = pixels [node] [offset];
			int alpha = pixel >>> 24;
			byte [] mask = imageMasks [node];
			alpha = mask == null ? (alpha * imageOpacities [node] + 127) / 255
					: (alpha * imageOpacities [node] * (mask [offset] & 255) + 32512) / 65025;
			return alpha == 0 ? 0 : (alpha << 24) | (pixel & 0x00ffffff);
		}

		private static int imageSourceOver (int source, int destination) {
			int sourceAlpha = source >>> 24;
			if (sourceAlpha == 0) return destination;
			if (sourceAlpha == 255 || (destination >>> 24) == 0) return source;
			int destinationAlpha = destination >>> 24;
			int destinationWeight = destinationAlpha * (255 - sourceAlpha);
			int sourceWeight = sourceAlpha * 255;
			int alphaNumerator = sourceWeight + destinationWeight;
			int alpha = (alphaNumerator + 127) / 255;
			int red = imageBlendChannel (source >>> 16, destination >>> 16, sourceWeight, destinationWeight, alphaNumerator);
			int green = imageBlendChannel (source >>> 8, destination >>> 8, sourceWeight, destinationWeight, alphaNumerator);
			int blue = imageBlendChannel (source, destination, sourceWeight, destinationWeight, alphaNumerator);
			return (alpha << 24) | (red << 16) | (green << 8) | blue;
		}

		private static int imageBlendChannel (int source, int destination, int sourceWeight,
				int destinationWeight, int alphaNumerator) {
			int numerator = (source & 255) * sourceWeight + (destination & 255) * destinationWeight;
			return (numerator + alphaNumerator / 2) / alphaNumerator;
		}

		private static int [] copyImagePixels (int width, int height, int [] pixels) {
			long count = (long)width * height;
			if (width <= 0 || height <= 0 || count > Integer.MAX_VALUE
					|| pixels == null || pixels.length != count) {
				throw new IllegalArgumentException ("invalid image pixels");
			}
			return pixels.clone ();
		}

		private void requireImageLayer (int node) {
			if (node <= ROOT || node >= nodeCount || kinds [node] != IMAGE_LAYER) {
				throw new IllegalArgumentException ("invalid image layer");
			}
		}


		/** Lower z paints first; equal z always retains creation order. */
		public void setZOrder (int node, int zOrder) {
			requireImageLayer (node);
			if (zOrders [node] == zOrder) return;
			unlinkImageLayer (node);
			zOrders [node] = zOrder;
			linkImageLayer (node);
		}

		public int zOrder (int node) {
			requireImageLayer (node);
			return zOrders [node];
		}

		private int newImageLayer () {
			if (nodeCount >= Integer.MAX_VALUE / 6) {
				throw new IllegalStateException ("image layer identities exhausted");
			}
			ensureImageCapacity (nodeCount + 1);
			int node = nodeCount++;
			kinds [node] = IMAGE_LAYER;
			nextSibling [node] = previousSibling [node] = NONE;
			transforms [node * 6] = transforms [node * 6 + 3] = 1;
			linkImageLayer (node);
			return node;
		}

		/* Array-linked stable ordering is shared with the retained paint graph's policy. */
		private void linkImageLayer (int node) {
			if (last == NONE || comesBefore (last, node)) {
				previousSibling [node] = last;
				if (last == NONE) first = node;
				else nextSibling [last] = node;
				last = node;
				return;
			}
			int cursor = first;
			while (cursor != NONE && comesBefore (cursor, node)) cursor = nextSibling [cursor];
			if (cursor == NONE) {
				previousSibling [node] = last;
				if (last == NONE) first = node;
				else nextSibling [last] = node;
				last = node;
				return;
			}
			int previous = previousSibling [cursor];
			nextSibling [node] = cursor;
			previousSibling [node] = previous;
			previousSibling [cursor] = node;
			if (previous == NONE) first = node;
			else nextSibling [previous] = node;
		}

		private void unlinkImageLayer (int node) {
			int previous = previousSibling [node], next = nextSibling [node];
			if (previous == NONE) first = next;
			else nextSibling [previous] = next;
			if (next == NONE) last = previous;
			else previousSibling [next] = previous;
			previousSibling [node] = nextSibling [node] = NONE;
		}

		private boolean comesBefore (int left, int right) {
			int order = Integer.compare (zOrders [left], zOrders [right]);
			return order < 0 || (order == 0 && left < right);
		}

		private void ensureImageCapacity (int required) {
			if (required <= kinds.length) return;
			int next = (int)Math.min (Integer.MAX_VALUE / 6L, Math.max ((long)required, kinds.length * 2L));
			// Reserve every column before publication so allocation failure cannot
			// leave mismatched capacities or corrupt an already usable layer stack.
			byte [] nextKinds = Arrays.copyOf (kinds, next);
			byte [] nextFlags = Arrays.copyOf (flags, next);
			int [] nextLinks = Arrays.copyOf (nextSibling, next);
			int [] previousLinks = Arrays.copyOf (previousSibling, next);
			int [] nextZOrders = Arrays.copyOf (zOrders, next);
			int [] nextWidths = Arrays.copyOf (widths, next);
			int [] nextHeights = Arrays.copyOf (heights, next);
			int [][] nextPixels = Arrays.copyOf (pixels, next);
			byte [][] nextMasks = Arrays.copyOf (imageMasks, next);
			int [] nextOpacities = Arrays.copyOf (imageOpacities, next);
			int [] nextClipX = Arrays.copyOf (imageClipX, next);
			int [] nextClipY = Arrays.copyOf (imageClipY, next);
			int [] nextClipWidth = Arrays.copyOf (imageClipWidth, next);
			int [] nextClipHeight = Arrays.copyOf (imageClipHeight, next);
			float [] nextTransforms = Arrays.copyOf (transforms, next * 6);
			double [] nextInverses = Arrays.copyOf (imageInverses, next * 4);
			double [] nextBounds = Arrays.copyOf (imageBounds, next * 4);
			kinds = nextKinds;
			flags = nextFlags;
			nextSibling = nextLinks;
			previousSibling = previousLinks;
			zOrders = nextZOrders;
			widths = nextWidths;
			heights = nextHeights;
			pixels = nextPixels;
			imageMasks = nextMasks;
			imageOpacities = nextOpacities;
			imageClipX = nextClipX;
			imageClipY = nextClipY;
			imageClipWidth = nextClipWidth;
			imageClipHeight = nextClipHeight;
			transforms = nextTransforms;
			imageInverses = nextInverses;
			imageBounds = nextBounds;
		}
	}

	public static final class RowWindow {
		public static final int DEFAULT_OVERSCAN_ROWS = 8;

		private long logicalCount;
		private long firstVisible;
		private int visibleCount;
		// Capacity survives clipping at the logical tail or an empty model.
		private int viewportRows;
		private int overscanRows = DEFAULT_OVERSCAN_ROWS;
		private int overscanBeforeRows = DEFAULT_OVERSCAN_ROWS;
		private int overscanAfterRows = DEFAULT_OVERSCAN_ROWS;
		private long generation;

		public long logicalCount () {
			return logicalCount;
		}

		public void setLogicalCount (long count) {
			if (count < 0) throw new IllegalArgumentException ("negative logical count");
			if (count == logicalCount) return;
			logicalCount = count;
			clampViewport ();
			generation++;
		}

		public void insert (long index, long count) {
			if (count < 0 || index < 0 || index > logicalCount) {
				throw new IllegalArgumentException ("invalid insert");
			}
			if (count == 0) return;
			long nextCount = Math.addExact (logicalCount, count);
			if (index <= firstVisible && logicalCount != 0) {
				firstVisible = Math.addExact (firstVisible, count);
			}
			logicalCount = nextCount;
			clampViewport ();
			generation++;
		}

		public void remove (long index, long count) {
			if (count < 0 || index < 0 || index > logicalCount - count) {
				throw new IllegalArgumentException ("invalid remove");
			}
			if (count == 0) return;
			if (index < firstVisible) {
				firstVisible -= Math.min (count, firstVisible - index);
			}
			logicalCount -= count;
			clampViewport ();
			generation++;
		}

		public void setViewport (long first, int visibleRows) {
			if (first < 0 || visibleRows < 0) {
				throw new IllegalArgumentException ("negative viewport");
			}
			long nextFirst = logicalCount == 0 ? 0 : Math.min (first, logicalCount - 1);
			int nextVisible = (int)Math.min (
					(long)visibleRows, Math.max (0L, logicalCount - nextFirst));
			if (nextFirst == firstVisible && nextVisible == visibleCount
					&& visibleRows == viewportRows) return;
			firstVisible = nextFirst;
			visibleCount = nextVisible;
			viewportRows = visibleRows;
			generation++;
		}

		public long firstVisible () {
			return firstVisible;
		}

		public int visibleCount () {
			return visibleCount;
		}

		/**
		 * Restores symmetric prefetch on both sides. The legacy setter and getter
		 * retain their original behavior unless directional prefetch is used.
		 */
		public void setOverscanRows (int rows) {
			if (rows < 0) throw new IllegalArgumentException ("negative overscan");
			if (rows == overscanRows && rows == overscanBeforeRows
					&& rows == overscanAfterRows) return;
			overscanRows = overscanBeforeRows = overscanAfterRows = rows;
			generation++;
		}

		/** Largest side of the prefetch budget; equals the legacy symmetric value by default. */
		public int overscanRows () {
			return overscanRows;
		}

		/**
		 * Optional directional prefetch. Equivalent to the symmetric legacy
		 * overscan only when both values match. Does not create native items.
		 * Both budgets are bounded by the logical model during projection;
		 * an empty viewport retains its anchor and admits no paint demand.
		 */
		public void setDirectionalOverscanRows (int beforeRows, int afterRows) {
			if (beforeRows < 0 || afterRows < 0) {
				throw new IllegalArgumentException ("negative overscan");
			}
			if (beforeRows == overscanBeforeRows && afterRows == overscanAfterRows) return;
			overscanBeforeRows = beforeRows;
			overscanAfterRows = afterRows;
			overscanRows = Math.max (beforeRows, afterRows);
			generation++;
		}

		public int overscanBeforeRows () {
			return overscanBeforeRows;
		}

		public int overscanAfterRows () {
			return overscanAfterRows;
		}

		public long paintStart () {
			if (visibleCount == 0) return firstVisible;
			return Math.max (0L, firstVisible - overscanBeforeRows);
		}

		public long paintEndExclusive () {
			if (visibleCount == 0) return firstVisible;
			long visibleEnd = saturatedAdd (firstVisible, visibleCount);
			return Math.min (logicalCount, saturatedAdd (visibleEnd, overscanAfterRows));
		}

		public int paintCount () {
			return Math.toIntExact (paintEndExclusive () - paintStart ());
		}

		public boolean isVisible (long index) {
			return 0 <= index && index < logicalCount
					&& firstVisible <= index
					&& index < saturatedAdd (firstVisible, visibleCount);
		}

		public boolean isPaintCandidate (long index) {
			return 0 <= index && index < logicalCount
					&& paintStart () <= index && index < paintEndExclusive ();
		}

		public void ensureVisible (long row) {
			if (row < 0 || row >= logicalCount) {
				throw new IllegalArgumentException ("row outside viewport model");
			}
			if (viewportRows <= 0 || row < firstVisible) {
				setViewport (row, viewportRows);
				return;
			}
			long end = saturatedAdd (firstVisible, viewportRows);
			if (row >= end) setViewport (row - viewportRows + 1L, viewportRows);
		}

		public long generation () {
			return generation;
		}

		private void clampViewport () {
			if (logicalCount == 0) {
				firstVisible = 0;
				visibleCount = 0;
				return;
			}
			firstVisible = Math.min (firstVisible, logicalCount - 1);
			visibleCount = (int)Math.min (
					(long)viewportRows, Math.max (0L, logicalCount - firstVisible));
		}
	}

	public static final class Selection {
		private int logicalCount;
		private boolean allSelected;
		private int [] starts = new int [4];
		private int [] ends = new int [4];
		private int rangeCount;

		public int logicalCount () {
			return logicalCount;
		}

		public void setLogicalCount (int count) {
			if (count < 0) throw new IllegalArgumentException ("negative logical count");
			if (count == logicalCount) return;
			if (count < logicalCount) {
				remove (count, logicalCount - count);
				return;
			}
			int oldCount = logicalCount;
			logicalCount = count;
			if (allSelected && oldCount < count) addRange (oldCount, count);
		}

		public boolean isSelected (int index) {
			checkIndex (index);
			boolean represented = contains (index);
			return allSelected ? !represented : represented;
		}

		public void setSelected (int index, boolean selected) {
			checkIndex (index);
			if (selected == isSelected (index)) return;
			if (allSelected) {
				if (selected) removeRange (index, index + 1);
				else addRange (index, index + 1);
			} else {
				if (selected) addRange (index, index + 1);
				else removeRange (index, index + 1);
			}
		}

		public void selectRange (int start, int endExclusive) {
			checkRange (start, endExclusive);
			if (allSelected) removeRange (start, endExclusive);
			else addRange (start, endExclusive);
		}

		public void deselectRange (int start, int endExclusive) {
			checkRange (start, endExclusive);
			if (allSelected) addRange (start, endExclusive);
			else removeRange (start, endExclusive);
		}

		public void clear () {
			allSelected = false;
			rangeCount = 0;
		}

		public void selectAll () {
			allSelected = true;
			rangeCount = 0;
		}

		/** Inverts every row in O(1): the represented ranges flip meaning. */
		public void invert () {
			allSelected = !allSelected;
		}

		public int selectedCount () {
			long represented = 0;
			for (int i = 0; i < rangeCount; i++) represented += ends [i] - starts [i];
			long selected = allSelected ? (long)logicalCount - represented : represented;
			return Math.toIntExact (selected);
		}

		/**
		 * Count selected rows only in a visible/model interval without enumerating
		 * the complete selection. Range endpoints are half-open, including when
		 * the logical model reaches Integer.MAX_VALUE.
		 */
		public int selectedCount (int fromInclusive, int toExclusive) {
			checkRange (fromInclusive, toExclusive);
			int represented = 0;
			int first = firstEndingAtOrAfter (fromInclusive);
			for (int i = first; i < rangeCount && starts [i] < toExclusive; i++) {
				int left = Math.max (fromInclusive, starts [i]);
				int right = Math.min (toExclusive, ends [i]);
				represented += Math.max (0, right - left);
			}
			return allSelected ? toExclusive - fromInclusive - represented : represented;
		}

		/** Sorted selected row IDs within a half-open interval, as a fresh array. */
		public int [] toArray (int fromInclusive, int toExclusive) {
			int [] result = new int [selectedCount (fromInclusive, toExclusive)];
			copySelectedIndices (fromInclusive, toExclusive, result, 0);
			return result;
		}

		/**
		 * Copies selected row IDs in a viewport to caller-owned primitive storage.
		 * No temporary selection, per-row object or global selection array is made.
		 * Insufficient capacity is rejected before touching any destination slot.
		 *
		 * @return the exact number of written row IDs
		 */
		public int copySelectedIndices (
				int fromInclusive, int toExclusive, int [] destination, int destinationOffset) {
			checkRange (fromInclusive, toExclusive);
			Objects.requireNonNull (destination);
			int needed = selectedCount (fromInclusive, toExclusive);
			if (destinationOffset < 0 || destinationOffset > destination.length
					|| needed > destination.length - destinationOffset) {
				throw new IndexOutOfBoundsException ("viewport selection destination");
			}
			int at = destinationOffset;
			int first = firstEndingAtOrAfter (fromInclusive);
			if (!allSelected) {
				for (int i = first; i < rangeCount && starts [i] < toExclusive; i++) {
					at = appendRange (destination, at,
							Math.max (fromInclusive, starts [i]),
							Math.min (toExclusive, ends [i]));
				}
			} else {
				int cursor = fromInclusive;
				for (int i = first; i < rangeCount && starts [i] < toExclusive; i++) {
					at = appendRange (destination, at, cursor, Math.min (toExclusive, starts [i]));
					cursor = Math.max (cursor, Math.min (toExclusive, ends [i]));
				}
				appendRange (destination, at, cursor, toExclusive);
			}
			return needed;
		}

		public int [] toArray () {
			int count = selectedCount ();
			int [] result = new int [count];
			int offset = 0;
			if (!allSelected) {
				for (int i = 0; i < rangeCount; i++) {
					offset = appendRange (result, offset, starts [i], ends [i]);
				}
				return result;
			}
			// Excluded ranges are already normalized; enumerate only the selected gaps.
			int start = 0;
			for (int range = 0; range < rangeCount; range++) {
				offset = appendRange (result, offset, start, starts [range]);
				start = ends [range];
			}
			appendRange (result, offset, start, logicalCount);
			return result;
		}

		private static int appendRange (int [] result, int offset, int start, int endExclusive) {
			for (int value = start; value < endExclusive; value++) {
				result [offset++] = value;
			}
			return offset;
		}

		public void insert (int index, int count) {
			if (count < 0 || index < 0 || index > logicalCount) {
				throw new IllegalArgumentException ("invalid insert");
			}
			if (count == 0) return;
			int nextCount = Math.addExact (logicalCount, count);
			int first = firstEndingAtOrAfter (index);
			if (!allSelected && first < rangeCount && ends [first] == index) first++;
			boolean crossing = first < rangeCount && starts [first] < index;
			boolean touches = first < rangeCount && starts [first] <= index;
			int extra = allSelected ? (touches ? 0 : 1) : (crossing ? 1 : 0);
			int capacity = compactCapacity (rangeCount + extra);
			if (capacity != starts.length || capacity != ends.length) {
				int [] nextStarts = Arrays.copyOf (starts, capacity);
				int [] nextEnds = Arrays.copyOf (ends, capacity);
				starts = nextStarts;
				ends = nextEnds;
			}
			if (allSelected) {
				// New rows are unselected. Extend a touching exception, or insert one.
				if (touches) {
					ends [first] += count;
					shiftRanges (first + 1, count);
				} else {
					for (int i = rangeCount; i > first; i--) {
						starts [i] = starts [i - 1] + count;
						ends [i] = ends [i - 1] + count;
					}
					starts [first] = index;
					ends [first] = index + count;
					rangeCount++;
				}
			} else {
				if (crossing) {
					// Split the only selected interval crossing the insertion point.
					int end = ends [first];
					for (int i = rangeCount; i > first + 1; i--) {
						starts [i] = starts [i - 1] + count;
						ends [i] = ends [i - 1] + count;
					}
					ends [first] = index;
					starts [first + 1] = index + count;
					ends [first + 1] = end + count;
					rangeCount++;
				} else {
					shiftRanges (first, count);
				}
			}
			logicalCount = nextCount;
		}

		private void shiftRanges (int first, int count) {
			for (int i = first; i < rangeCount; i++) {
				starts [i] += count;
				ends [i] += count;
			}
		}

		public void remove (int index, int count) {
			if (count < 0 || index < 0 || index > logicalCount - count) {
				throw new IllegalArgumentException ("invalid remove");
			}
			if (count == 0) return;
			int nextCount = projectRemoval (index, count, null, null);
			// Match the historical rebuild's compact geometric capacity. Reuse when
			// already compact, but actually release large backing after collapsing runs.
			int capacity = compactCapacity (nextCount);
			int [] nextStarts = starts, nextEnds = ends;
			if (capacity != starts.length || capacity != ends.length) {
				nextStarts = new int [capacity];
				nextEnds = new int [capacity];
			}
			projectRemoval (index, count, nextStarts, nextEnds);
			starts = nextStarts;
			ends = nextEnds;
			rangeCount = nextCount;
			logicalCount -= count;
		}

		/** Count first, then compact left-to-right; no temporary Selection or per-run object. */
		private int projectRemoval (int index, int count, int [] outStarts, int [] outEnds) {
			int endRemoved = index + count;
			int written = 0, previousEnd = -1;
			for (int i = 0; i < rangeCount; i++) {
				int start = starts [i], end = ends [i];
				start = start >= endRemoved ? start - count : Math.min (start, index);
				end = end >= endRemoved ? end - count : Math.min (end, index);
				if (start >= end) continue;
				if (written != 0 && start <= previousEnd) {
					previousEnd = Math.max (previousEnd, end);
					if (outEnds != null) outEnds [written - 1] = previousEnd;
				} else {
					if (outStarts != null) {
						outStarts [written] = start;
						outEnds [written] = end;
					}
					previousEnd = end;
					written++;
				}
			}
			return written;
		}

		private static int compactCapacity (int required) {
			int capacity = 4;
			while (capacity < required) capacity += capacity / 2;
			return capacity;
		}

		public int rangeCount () {
			return rangeCount;
		}

		public int rangeStart (int range) {
			return starts [rangeIndex (range)];
		}

		public int rangeEndExclusive (int range) {
			return ends [rangeIndex (range)];
		}

		public boolean complementMode () {
			return allSelected;
		}

		private void addRange (int start, int endExclusive) {
			if (start >= endExclusive) return;
			int first = firstEndingAtOrAfter (start);
			int mergedStart = start, mergedEnd = endExclusive;
			int last = first;
			while (last < rangeCount && starts [last] <= mergedEnd) {
				mergedStart = Math.min (mergedStart, starts [last]);
				mergedEnd = Math.max (mergedEnd, ends [last]);
				last++;
			}
			int removed = last - first;
			ensureCapacity (rangeCount - removed + 1);
			System.arraycopy (starts, last, starts, first + 1, rangeCount - last);
			System.arraycopy (ends, last, ends, first + 1, rangeCount - last);
			starts [first] = mergedStart;
			ends [first] = mergedEnd;
			rangeCount = rangeCount - removed + 1;
		}

		private void removeRange (int start, int endExclusive) {
			if (start >= endExclusive || rangeCount == 0) return;
			int first = firstEndingAtOrAfter (start + 1);
			if (first == rangeCount || starts [first] >= endExclusive) return;
			int last = first;
			while (last < rangeCount && starts [last] < endExclusive) last++;
			int leftStart = starts [first];
			int rightEnd = ends [last - 1];
			boolean left = leftStart < start;
			boolean right = rightEnd > endExclusive;
			int pieces = (left ? 1 : 0) + (right ? 1 : 0);
			int newCount = rangeCount - (last - first) + pieces;
			ensureCapacity (newCount);
			System.arraycopy (starts, last, starts, first + pieces, rangeCount - last);
			System.arraycopy (ends, last, ends, first + pieces, rangeCount - last);
			int write = first;
			if (left) {
				starts [write] = leftStart;
				ends [write++] = start;
			}
			if (right) {
				starts [write] = endExclusive;
				ends [write] = rightEnd;
			}
			rangeCount = newCount;
		}

		/** First range whose end is at or after <code>position</code> (ranges are sorted and disjoint). */
		private int firstEndingAtOrAfter (int position) {
			int low = 0, high = rangeCount;
			while (low < high) {
				int mid = (low + high) >>> 1;
				if (ends [mid] < position) low = mid + 1;
				else high = mid;
			}
			return low;
		}

		private boolean contains (int index) {
			int low = 0, high = rangeCount - 1;
			while (low <= high) {
				int mid = (low + high) >>> 1;
				if (index < starts [mid]) high = mid - 1;
				else if (index >= ends [mid]) low = mid + 1;
				else return true;
			}
			return false;
		}

		private void ensureCapacity (int required) {
			if (required <= starts.length) return;
			int next = Math.max (required, Math.max (4, starts.length * 3 / 2));
			int [] nextStarts = Arrays.copyOf (starts, next);
			int [] nextEnds = Arrays.copyOf (ends, next);
			starts = nextStarts;
			ends = nextEnds;
		}

		private int rangeIndex (int range) {
			if (range < 0 || range >= rangeCount) {
				throw new IndexOutOfBoundsException ("Index " + range + " out of bounds for length " + rangeCount);
			}
			return range;
		}

		private void checkIndex (int index) {
			if (index < 0 || index >= logicalCount) {
				throw new IllegalArgumentException ("index outside logical model");
			}
		}

		private void checkRange (int start, int endExclusive) {
			if (start < 0 || endExclusive < start || endExclusive > logicalCount) {
				throw new IllegalArgumentException ("range outside logical model");
			}
		}
	}



	/**
	 * Stateless coordinate policy for flowing, pinned, anchored, parallax and
	 * sticky viewport layers. It owns neither native widgets nor scene nodes.
	 * The common path uses no allocations; only extreme 64-bit intermediate
	 * overflow falls back to exact BigInteger arithmetic.
	 *
	 * <p>The policy can be held in primitive columns in a retained graph,
	 * with no per-row structural allocation. This projection deliberately
	 * does not replace native TreeColumn headers or SWT input lifecycles.</p>
	 */
	public static final class LayerPosition {
		public static final int FIXED = 0;
		public static final int SCROLL = 256;
		public static final int PARALLAX_HALF = 128;
		public static final int ANCHOR_START = 0;
		public static final int ANCHOR_CENTER = 1;
		public static final int ANCHOR_END = 2;
		public static final int STICK_NONE = 0;
		public static final int STICK_START = 1;
		public static final int STICK_END = 2;
		private static final int MAX_RATE = 4096;
		private static final java.math.BigInteger LONG_MAX =
				java.math.BigInteger.valueOf(Long.MAX_VALUE);
		private static final java.math.BigInteger LONG_MIN =
				java.math.BigInteger.valueOf(Long.MIN_VALUE);

		private LayerPosition () { }

		/**
		 * Maps one world coordinate to device-pixel space relative to a viewport.
		 * A rate of 256 follows the full scroll origin; 0 is fixed; 128 is 50%
		 * parallax; a negative factor moves oppositely. ANCHOR_END attaches the
		 * far edge to the viewport edge, useful for a floating minimap/footer.
		 * STICK_START/END clamps a layer to an edge, but does not decide when
		 * a section loses ownership of its sticky layer.
		 *
		 * @param position layer offset from the anchor
		 * @param extent nonnegative layer extent along this axis
		 * @param origin logical scroll origin
		 * @param viewportExtent viewport device-pixel length
		 * @param rateQ8 scroll multiplier in 1/256 steps (-4096..4096)
		 * @param anchor ANCHOR_START, ANCHOR_CENTER or ANCHOR_END
		 * @param sticky STICK_NONE, STICK_START or STICK_END
		 * @param inset nonnegative edge inset used only by sticky modes
		 */
		public static long coordinate (long position, long extent, long origin,
				int viewportExtent, int rateQ8, int anchor, int sticky, int inset) {
			if (extent < 0 || viewportExtent < 0 || rateQ8 < -MAX_RATE
					|| rateQ8 > MAX_RATE || anchor < ANCHOR_START
					|| anchor > ANCHOR_END || sticky < STICK_NONE
					|| sticky > STICK_END || inset < 0) {
				throw new IllegalArgumentException ("invalid layer positioning policy");
			}
			long span = (long) viewportExtent - extent;
			long anchorOffset = switch (anchor) {
				case ANCHOR_START -> 0L;
				case ANCHOR_CENTER -> Math.floorDiv (span, 2L);
				case ANCHOR_END -> span;
				default -> throw new AssertionError (anchor);
			};
			long mapped;
			try {
				long scaled = Math.addExact (
						Math.multiplyExact (origin / SCROLL, (long) rateQ8),
						Math.floorDiv ((origin % SCROLL) * (long) rateQ8, SCROLL));
				mapped = Math.subtractExact (Math.addExact (position, anchorOffset), scaled);
			} catch (ArithmeticException overflow) {
				java.math.BigInteger scaled = java.math.BigInteger.valueOf (origin)
						.multiply (java.math.BigInteger.valueOf (rateQ8)).shiftRight (8);
				java.math.BigInteger exact = java.math.BigInteger.valueOf (position)
						.add (java.math.BigInteger.valueOf (anchorOffset)).subtract (scaled);
				if (exact.compareTo (LONG_MAX) > 0) mapped = Long.MAX_VALUE;
				else if (exact.compareTo (LONG_MIN) < 0) mapped = Long.MIN_VALUE;
				else mapped = exact.longValue ();
			}
			return switch (sticky) {
				case STICK_NONE -> mapped;
				case STICK_START -> Math.max (mapped, (long) inset);
				case STICK_END -> Math.min (mapped,
						saturatedSubtract ((long) viewportExtent - extent, inset));
				default -> throw new AssertionError (sticky);
			};
		}

		/**
		 * Signed Q8 scroll displacement (logical coordinates), with floor rounding
		 * for fractional and negative motion. Ordinary frames allocate nothing;
		 * the rare overflowing calculation uses an exact saturated slow path.
		 */
		public static long scrollOffset (long origin, int rateQ8) {
			if (rateQ8 < -MAX_RATE || rateQ8 > MAX_RATE) {
				throw new IllegalArgumentException ("scroll multiplier out of range");
			}
			try {
				return Math.addExact (
						Math.multiplyExact (origin / SCROLL, (long) rateQ8),
						Math.floorDiv ((origin % SCROLL) * (long) rateQ8, SCROLL));
			} catch (ArithmeticException overflow) {
				java.math.BigInteger scaled = java.math.BigInteger.valueOf (origin)
						.multiply (java.math.BigInteger.valueOf (rateQ8)).shiftRight (8);
				if (scaled.compareTo (LONG_MAX) > 0) return Long.MAX_VALUE;
				if (scaled.compareTo (LONG_MIN) < 0) return Long.MIN_VALUE;
				return scaled.longValue ();
			}
		}

		/** Whether a changed scroll axis can move a layer with these Q8 factors. */
		public static boolean movesWithScroll (int xRateQ8, int yRateQ8,
				boolean horizontalChanged, boolean verticalChanged) {
			if (xRateQ8 < -MAX_RATE || xRateQ8 > MAX_RATE
					|| yRateQ8 < -MAX_RATE || yRateQ8 > MAX_RATE) {
				throw new IllegalArgumentException ("scroll multiplier out of range");
			}
			return (horizontalChanged && xRateQ8 != 0)
					|| (verticalChanged && yRateQ8 != 0);
		}

		/**
		 * Clips projected half-open bounds to the viewport. Returns visibility,
		 * writing x, y, width, height into caller-owned four-int scratch.
		 * No Rectangle/GC/native object is created.
		 */
		public static boolean clip (long x, long y, long width, long height,
				int viewportWidth, int viewportHeight, int [] out) {
			if (out == null || out.length < 4) {
				throw new IllegalArgumentException ("need four output ints");
			}
			if (width < 0 || height < 0 || viewportWidth < 0 || viewportHeight < 0) {
				throw new IllegalArgumentException ("invalid layer bounds");
			}
			long x0 = Math.max (0L, x);
			long y0 = Math.max (0L, y);
			long x1 = Math.min (viewportWidth, saturatedAdd (x, width));
			long y1 = Math.min (viewportHeight, saturatedAdd (y, height));
			if (x0 >= x1 || y0 >= y1) {
				out [0] = out [1] = out [2] = out [3] = 0;
				return false;
			}
			out [0] = (int) x0;
			out [1] = (int) y0;
			out [2] = (int) (x1 - x0);
			out [3] = (int) (y1 - y0);
			return true;
		}

		private static long saturatedAdd (long a, long b) {
			long value = a + b;
			if (((a ^ value) & (b ^ value)) < 0) {
				return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
			}
			return value;
		}

		private static long saturatedSubtract (long a, long b) {
			long value = a - b;
			if (((a ^ b) & (a ^ value)) < 0) {
				return a >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
			}
			return value;
		}
	}

	/**
	 * Primitive, four-plane viewport projection used by native Trees, grids,
	 * tables and future layered controls. A fixed header remains above the
	 * vertically scrolling body; a frozen left region remains above the
	 * horizontally scrolling body. The corner is fixed on both axes.
	 *
	 * <p>Geometry is in device pixels; scroll origin and logical rectangles use
	 * signed long coordinates. Every clip/query writes into caller-owned int[4],
	 * avoiding native handles, Rectangle allocation, and per-row scene nodes.
	 * This model does not replace SWT's native TreeColumn header or input
	 * routing, and does not change existing viewport scroll dirty masks.</p>
	 */
	public static final class PinnedPlanes {
		public static final int BODY = ViewportRuntime.BODY;
		public static final int HEADER = ViewportRuntime.HEADER;
		public static final int FROZEN = ViewportRuntime.FROZEN;
		/** The top-left intersection has no scrolling dependency. */
		public static final int CORNER = 1 << 8;

		private int width, height, headerHeight, frozenWidth;
		private long originX, originY, generation;

		public PinnedPlanes (int width, int height, int headerHeight, int frozenWidth) {
			configure (width, height, headerHeight, frozenWidth);
		}

		/**
		 * The top strip and left strip may be empty. Rejected changes leave the
		 * previous geometry intact.
		 */
		public void configure (int width, int height, int headerHeight, int frozenWidth) {
			if (width < 0 || height < 0 || headerHeight < 0 || headerHeight > height
					|| frozenWidth < 0 || frozenWidth > width) {
				throw new IllegalArgumentException ("invalid pinned viewport geometry");
			}
			if (this.width == width && this.height == height
					&& this.headerHeight == headerHeight && this.frozenWidth == frozenWidth) return;
			this.width = width;
			this.height = height;
			this.headerHeight = headerHeight;
			this.frozenWidth = frozenWidth;
			generation++;
		}

		/** Scroll only planes attached to the changed axes; never invalidate the corner. */
		public int scrollTo (long x, long y) {
			boolean horizontal = x != originX, vertical = y != originY;
			if (!horizontal && !vertical) return 0;
			originX = x;
			originY = y;
			generation++;
			int dirty = 0;
			if (horizontal) {
				if (width > frozenWidth && height > headerHeight) dirty |= BODY;
				if (width > frozenWidth && headerHeight > 0) dirty |= HEADER;
			}
			if (vertical) {
				if (width > frozenWidth && height > headerHeight) dirty |= BODY;
				if (frozenWidth > 0 && height > headerHeight) dirty |= FROZEN;
			}
			return dirty;
		}

		public long originX () { return originX; }
		public long originY () { return originY; }
		public long generation () { return generation; }

		/**
		 * Routes an interior pixel to its topmost fixed plane. Native editors,
		 * focus, IME and accessible peers must be dispatched before this model.
		 * Returns zero for a point outside the viewport.
		 */
		public int planeAt (int pixelX, int pixelY) {
			if (pixelX < 0 || pixelY < 0 || pixelX >= width || pixelY >= height) return 0;
			if (pixelY < headerHeight) return pixelX < frozenWidth ? CORNER : HEADER;
			return pixelX < frozenWidth ? FROZEN : BODY;
		}

		/** Writes device-pixel x,y,width,height of a plane into caller-owned scratch. */
		public boolean clip (int plane, int [] out) {
			checkScratch (out);
			int x0, y0, x1, y1;
			switch (plane) {
				case BODY -> {
					x0 = frozenWidth; y0 = headerHeight; x1 = width; y1 = height;
				}
				case HEADER -> {
					x0 = frozenWidth; y0 = 0; x1 = width; y1 = headerHeight;
				}
				case FROZEN -> {
					x0 = 0; y0 = headerHeight; x1 = frozenWidth; y1 = height;
				}
				case CORNER -> {
					x0 = 0; y0 = 0; x1 = frozenWidth; y1 = headerHeight;
				}
				default -> throw new IllegalArgumentException ("unknown viewport plane");
			}
			out [0] = x0;
			out [1] = y0;
			out [2] = x1 - x0;
			out [3] = y1 - y0;
			return x0 < x1 && y0 < y1;
		}

		/**
		 * Projects a half-open logical rectangle to its plane and intersects its
		 * visible clip. BODY and HEADER scroll horizontally, BODY and FROZEN
		 * scroll vertically. Body rows are relative to the area under HEADER.
		 * Offscreen content is not materialized, and all intermediate arithmetic
		 * saturates before narrowing to device-pixel ints.
		 */
		public boolean project (int plane, long x, long y, long width, long height, int [] out) {
			checkScratch (out);
			if (width < 0 || height < 0 || (width > 0 && x > Long.MAX_VALUE - width)
					|| (height > 0 && y > Long.MAX_VALUE - height)) {
				throw new IllegalArgumentException ("invalid logical bounds");
			}
			if (!clip (plane, out) || width == 0 || height == 0) {
				out [2] = out [3] = 0;
				return false;
			}
			int left = out [0], top = out [1];
			int right = left + out [2], bottom = top + out [3];
			boolean horizontal = plane == BODY || plane == HEADER;
			boolean vertical = plane == BODY || plane == FROZEN;
			long sx = LayerPosition.coordinate (x, width, originX, this.width,
					horizontal ? LayerPosition.SCROLL : LayerPosition.FIXED,
					LayerPosition.ANCHOR_START, LayerPosition.STICK_NONE, 0);
			long sy = LayerPosition.coordinate (y, height, originY, this.height,
					vertical ? LayerPosition.SCROLL : LayerPosition.FIXED,
					LayerPosition.ANCHOR_START, LayerPosition.STICK_NONE, 0);
			if (vertical) sy = saturatingAdd (sy, headerHeight);
			long ex = saturatingAdd (sx, width), ey = saturatingAdd (sy, height);
			long x0 = Math.max ((long)left, sx), y0 = Math.max ((long)top, sy);
			long x1 = Math.min ((long)right, ex), y1 = Math.min ((long)bottom, ey);
			if (x0 >= x1 || y0 >= y1) {
				out [2] = out [3] = 0;
				return false;
			}
			out [0] = (int)x0; out [1] = (int)y0;
			out [2] = (int)(x1 - x0); out [3] = (int)(y1 - y0);
			return true;
		}

		private static void checkScratch (int [] out) {
			if (out == null || out.length < 4) {
				throw new IllegalArgumentException ("need four output ints");
			}
		}

		private static long saturatingAdd (long a, long b) {
			long value = a + b;
			if (((a ^ value) & (b ^ value)) < 0) {
				return b >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
			}
			return value;
		}

		private static long saturatingSubtract (long a, long b) {
			long value = a - b;
			if (((a ^ b) & (a ^ value)) < 0) {
				return a >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
			}
			return value;
		}
	}

	private double originX;
	private double originY;
	private boolean originInitialized;

	public void initializeOrigin (double x, double y) {
		requireFinite (x, y);
		originX = x;
		originY = y;
		originInitialized = true;
	}

	/**
	 * Updates the logical viewport origin and returns the dirty z-plane mask.
	 */
	public int scrollTo (double x, double y) {
		requireFinite (x, y);
		if (!originInitialized) {
			initializeOrigin (x, y);
			return 0;
		}
		boolean horizontal = Double.doubleToLongBits (originX) != Double.doubleToLongBits (x);
		boolean vertical = Double.doubleToLongBits (originY) != Double.doubleToLongBits (y);
		originX = x;
		originY = y;

		int dirty = 0;
        if (horizontal) {
            dirty |= BODY | HEADER | EDITOR | FEEDBACK | SCROLLBAR;
        }
        if (vertical) {
            dirty |= BODY | FROZEN | EDITOR | FEEDBACK | SCROLLBAR;
        }
		return dirty;
	}

	public double originX () {
		return originX;
	}

	public double originY () {
		return originY;
	}

	public boolean originInitialized () {
		return originInitialized;
	}

	/**
	 * Solves arbitrary pixel-sized content and scrollbar visibility to a fixed
	 * point. This is the general form used by ScrolledComposite-like controls.
	 */
	public static PixelLayout solvePixels (
			int outerWidth,
			int outerHeight,
			long contentWidth,
			long contentHeight,
			int horizontalBarHeight,
			int verticalBarWidth,
			int horizontalPolicy,
			int verticalPolicy) {
		if (outerWidth < 0 || outerHeight < 0) {
			throw new IllegalArgumentException ("negative viewport extent");
		}
		if (contentWidth < 0 || contentHeight < 0) {
			throw new IllegalArgumentException ("negative logical extent");
		}
		if (horizontalBarHeight < 0 || verticalBarWidth < 0) {
			throw new IllegalArgumentException ("negative scrollbar extent");
		}
		checkPolicy (horizontalPolicy);
		checkPolicy (verticalPolicy);

		boolean horizontal = horizontalPolicy == ALWAYS;
		boolean vertical = verticalPolicy == ALWAYS;
		for (int pass = 0; pass < 4; pass++) {
			int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
			int bodyHeight = Math.max (0, outerHeight - (horizontal ? horizontalBarHeight : 0));
			boolean nextHorizontal = policyVisible (horizontalPolicy, contentWidth > bodyWidth);
			boolean nextVertical = policyVisible (verticalPolicy, contentHeight > bodyHeight);
            if (horizontal == nextHorizontal && vertical == nextVertical) {
                break;
            }
			horizontal = nextHorizontal;
			vertical = nextVertical;
		}

		int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
		int bodyHeight = Math.max (0, outerHeight - (horizontal ? horizontalBarHeight : 0));
		return new PixelLayout (
				horizontal,
				vertical,
				horizontal && vertical,
				bodyWidth,
				bodyHeight,
				contentWidth,
				contentHeight);
	}

	/**
	 * Solves a uniform-row viewport. This preserves the historical viewport rule
	 * that a partially visible final row counts as visible.
	 */
	public static RowLayout solveRows (
			int outerWidth,
			int outerHeight,
			int headerHeight,
			long logicalRows,
			int sampleRowHeight,
			long logicalContentWidth,
			int horizontalBarHeight,
			int verticalBarWidth,
			int horizontalPolicy,
			int verticalPolicy) {
		if (outerWidth < 0 || outerHeight < 0) {
			throw new IllegalArgumentException ("negative viewport extent");
		}
		if (headerHeight < 0 || headerHeight > outerHeight) {
			throw new IllegalArgumentException ("invalid header extent");
		}
		if (logicalRows < 0 || logicalContentWidth < 0) {
			throw new IllegalArgumentException ("negative logical extent");
		}
		if (sampleRowHeight <= 0) {
			throw new IllegalArgumentException ("non-positive sample row extent");
		}
		if (horizontalBarHeight < 0 || verticalBarWidth < 0) {
			throw new IllegalArgumentException ("negative scrollbar extent");
		}
		checkPolicy (horizontalPolicy);
		checkPolicy (verticalPolicy);

		boolean horizontal = horizontalPolicy == ALWAYS;
		boolean vertical = verticalPolicy == ALWAYS;
		for (int pass = 0; pass < 4; pass++) {
			int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
			int bodyHeight = Math.max (
					0, outerHeight - headerHeight - (horizontal ? horizontalBarHeight : 0));
			boolean nextHorizontal = policyVisible (
					horizontalPolicy, logicalContentWidth > bodyWidth);
			int visibleRows = visibleRows (logicalRows, sampleRowHeight, bodyHeight);
			boolean nextVertical = policyVisible (verticalPolicy, logicalRows > visibleRows);
            if (horizontal == nextHorizontal && vertical == nextVertical) {
                break;
            }
			horizontal = nextHorizontal;
			vertical = nextVertical;
		}

		int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
		int bodyHeight = Math.max (
				0, outerHeight - headerHeight - (horizontal ? horizontalBarHeight : 0));
		int visibleRows = bodyWidth == 0 ? 0 : visibleRows (logicalRows, sampleRowHeight, bodyHeight);
		return new RowLayout (
				horizontal,
				vertical,
				horizontal && vertical,
				bodyWidth,
				bodyHeight,
				bodyWidth,
				headerHeight,
				visibleRows,
				logicalRows,
				saturatedMultiply (logicalRows, sampleRowHeight),
				logicalContentWidth);
	}

	private static boolean policyVisible (int policy, boolean automatic) {
		return switch (policy) {
			case ALWAYS -> true;
			case NEVER -> false;
			default -> automatic;
		};
	}

	private static int visibleRows (long logicalRows, int sampleRowHeight, int bodyHeight) {
        if (logicalRows == 0 || bodyHeight == 0) {
            return 0;
        }
		long rows = ((long)bodyHeight + sampleRowHeight - 1L) / sampleRowHeight;
		return (int)Math.min (logicalRows, Math.max (1L, rows));
	}

	/** Saturating addition for the nonnegative row coordinates and counts above. */
	private static long saturatedAdd (long value, int increment) {
		return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
	}

	private static long saturatedMultiply (long value, int multiplier) {
        if (value == 0) {
            return 0;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
		return value * multiplier;
	}

	private static void checkPolicy (int policy) {
		if (policy != AUTO && policy != ALWAYS && policy != NEVER) {
			throw new IllegalArgumentException ("invalid scrollbar policy");
		}
	}

	private static void requireFinite (double x, double y) {
		if (!Double.isFinite (x) || !Double.isFinite (y)) {
			throw new IllegalArgumentException ("non-finite viewport origin");
		}
	}
}
