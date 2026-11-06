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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 6)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.function.Consumer;
import java.util.stream.IntStream;

import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.types.Rect;

/**
 * The canvas state an {@link M3ExternalGC} owns above the frame it was made in (GC seam, leaf 6; atoms CLIP_STATE and
 * TRANSFORM_STATE, patterns M3-GC-CLIP v1 and M3-GC-TRANSFORM v1; census GF02, GF16).
 * <p>
 * One save layer carries the GC transform and the GC clip, kept apart as the stock GC keeps them: setTransform changes the
 * layer's matrix and keeps its clip (Skia keeps a clip in device space, as GDI+ does); setClipping replaces the clip, so the
 * layer is reopened at the current transform and clipped once; close (dispose, setAdvanced(false)) restores the frame. The
 * layer's matrix is the frame's matrix concatenated with the GC transform. The donor (I31 SkiaGC) saved one level per
 * setTransform and popped one per setClipping on the same stack, so a clip set before a transform was never replaced.
 * </p>
 * Units: matrices map local pixels (the GC's point coordinates scaled by the zoom) to device pixels; bounds are pixels.
 */
final class M3GcLayer {
	private final IM3Surface surface;
	private final int baseCount;
	private Matrix33 base = Matrix33.IDENTITY;
	private Matrix33 transform = Matrix33.IDENTITY;
	private boolean open;
	/* the device bounds of the clip as set (null: no clip); a region clip also keeps its device rectangles */
	private Rect clipDevice;
	private int[] regionRects;

	M3GcLayer(IM3Surface surface) {
		this.surface = surface;
		this.baseCount = surface.getCanvas().getSaveCount();
	}

	Matrix33 transform() {
		return transform;
	}

	boolean isClipped() {
		return clipDevice != null;
	}

	/* the rectangles of a region clip (left, top, right, bottom in device pixels), null for any other clip or none */
	int[] regionRects() {
		return regionRects;
	}

	boolean isTransformed() {
		return !Matrix33.IDENTITY.equals(transform);
	}

	/** The GC transform (local pixels): the layer's matrix changes, the clip in force stays. */
	void setTransform(Matrix33 next) {
		transform = next;
		if (!open && !isTransformed()) return;
		openLayer();
		canvas().setMatrix(matrix());
	}

	/**
	 * Replaces the clip: the layer is reopened at the current transform and {@code apply} clips it once.
	 *
	 * @param apply the clip, applied under the current matrix; null clears the clip
	 * @param localBounds the clip's bounds in local pixels (a region: device pixels)
	 * @param rects a region clip's device rectangles, else null
	 */
	void setClip(Consumer<IM3Canvas> apply, Rect localBounds, int[] rects) {
		if (open) canvas().restoreToCount(baseCount);
		open = false;
		regionRects = rects;
		clipDevice = null;
		if (apply == null && !isTransformed()) return;
		openLayer();
		if (isTransformed()) canvas().setMatrix(matrix());
		if (apply == null) return;
		clipDevice = rects != null ? localBounds : map(matrix(), localBounds);
		apply.accept(canvas());
	}

	/** Back to the frame: the canvas restored to the GC's construction count, no clip, the identity transform. */
	void close() {
		canvas().restoreToCount(baseCount);
		open = false;
		clipDevice = null;
		regionRects = null;
		transform = Matrix33.IDENTITY;
		base = Matrix33.IDENTITY;
	}

	/** The visible clip in local pixels: the clip's device bounds within the frame (device pixels), seen through the matrix. */
	Rect localClip(Rect frame) {
		Rect device = clipDevice == null ? frame : frame.intersect(clipDevice);
		if (device == null) return new Rect(0, 0, 0, 0);
		Matrix33 matrix = open ? matrix() : canvas().getLocalToDevice();
		return map(invert(matrix), device);
	}

	private void openLayer() {
		if (open) return;
		IM3Canvas canvas = canvas();
		base = canvas.getLocalToDevice();
		canvas.save();
		open = true;
	}

	private IM3Canvas canvas() {
		return surface.getCanvas();
	}

	private Matrix33 matrix() {
		return base.makeConcat(transform);
	}

	/* the bounding box of a rectangle mapped by an affine matrix */
	static Rect map(Matrix33 matrix, Rect r) {
		float[] m = matrix.getMat();
		float[] xs = { r.getLeft(), r.getRight(), r.getLeft(), r.getRight() };
		float[] ys = { r.getTop(), r.getTop(), r.getBottom(), r.getBottom() };
		double[] mx = IntStream.range(0, 4).mapToDouble(i -> m[0] * xs[i] + m[1] * ys[i] + m[2]).toArray();
		double[] my = IntStream.range(0, 4).mapToDouble(i -> m[3] * xs[i] + m[4] * ys[i] + m[5]).toArray();
		return new Rect((float) min(mx), (float) min(my), (float) max(mx), (float) max(my));
	}

	/* the inverse of an affine matrix; a singular matrix maps everything to the origin */
	static Matrix33 invert(Matrix33 matrix) {
		float[] m = matrix.getMat();
		float det = m[0] * m[4] - m[1] * m[3];
		if (det == 0) return new Matrix33(0, 0, 0, 0, 0, 0, 0, 0, 1);
		float a = m[4] / det, b = -m[1] / det, d = -m[3] / det, e = m[0] / det;
		return new Matrix33(a, b, -(a * m[2] + b * m[5]), d, e, -(d * m[2] + e * m[5]), 0, 0, 1);
	}

	private static double min(double[] values) {
		return java.util.Arrays.stream(values).min().orElse(0);
	}

	private static double max(double[] values) {
		return java.util.Arrays.stream(values).max().orElse(0);
	}
}
