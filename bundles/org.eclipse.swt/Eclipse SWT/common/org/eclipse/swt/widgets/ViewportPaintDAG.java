/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.*;

/**
 * Compact inherited paint-state graph for viewport planes.
 *
 * <p>This is deliberately not a public GC wrapper. SWT continues to expose the
 * real {@code GC} through paint events. The graph describes coordinate spaces,
 * clips, z-plane ownership and stroke state that an internal viewport painter
 * can apply to that GC. Nodes are kept in primitive lanes so a header/body/
 * frozen/editor hierarchy does not require a retained object graph.</p>
 *
 * <p>Each node has one parent, which is sufficient for paint-state inheritance
 * while still allowing shared ancestors. Local affine transforms map a node's
 * coordinates into its parent coordinates. Clips are stored in the node's
 * local coordinates. Stroke state uses nearest-ancestor inheritance.</p>
 */
final class ViewportPaintDAG {
	static final int ROOT = -1;

	private static final int HAS_CLIP = 1 << 0;
	private static final int HAS_STROKE = 1 << 1;

	private int [] parents = new int [8];
	private int [] layers = new int [8];
	private int [] flags = new int [8];

	private double [] m00 = new double [8];
	private double [] m01 = new double [8];
	private double [] m10 = new double [8];
	private double [] m11 = new double [8];
	private double [] tx = new double [8];
	private double [] ty = new double [8];

	private double [] clipX = new double [8];
	private double [] clipY = new double [8];
	private double [] clipWidth = new double [8];
	private double [] clipHeight = new double [8];

	private int [] lineWidth = new int [8];
	private int [] lineStyle = new int [8];
	private int [] lineCap = new int [8];
	private int [] lineJoin = new int [8];

	private int size;

	/*
	 * Viewport widget state is UI-thread confined. Reuse scratch lanes so
	 * transform/hit-test/clip operations stay allocation-free on paint and
	 * pointer-move paths.
	 */
	private final double [] transformScratch = new double [6];
	private final double [] boundsScratch = new double [4];
	private final double [] pointScratch = new double [2];

	int addNode (int parent, int layer) {
		if (parent < ROOT || parent >= size) {
			throw new IllegalArgumentException ("invalid paint-state parent");
		}
		ensureCapacity (size + 1);
		int node = size++;
		parents [node] = parent;
		layers [node] = layer;
		m00 [node] = 1;
		m01 [node] = 0;
		m10 [node] = 0;
		m11 [node] = 1;
		tx [node] = 0;
		ty [node] = 0;
		return node;
	}

	int nodeCount () {
		return size;
	}

	int parent (int node) {
		checkNode (node);
		return parents [node];
	}

	int layer (int node) {
		checkNode (node);
		return layers [node];
	}

	void setTransform (
			int node,
			double m00, double m01,
			double m10, double m11,
			double tx, double ty) {
		checkNode (node);
		requireFinite (m00);
		requireFinite (m01);
		requireFinite (m10);
		requireFinite (m11);
		requireFinite (tx);
		requireFinite (ty);
		this.m00 [node] = m00;
		this.m01 [node] = m01;
		this.m10 [node] = m10;
		this.m11 [node] = m11;
		this.tx [node] = tx;
		this.ty [node] = ty;
	}

	void setTranslation (int node, double x, double y) {
		checkNode (node);
		requireFinite (x);
		requireFinite (y);
		m00 [node] = 1;
		m01 [node] = 0;
		m10 [node] = 0;
		m11 [node] = 1;
		tx [node] = x;
		ty [node] = y;
	}

	void setClip (int node, double x, double y, double width, double height) {
		checkNode (node);
		requireFinite (x);
		requireFinite (y);
		requireFinite (width);
		requireFinite (height);
		if (width < 0 || height < 0) throw new IllegalArgumentException ("negative clip extent");
		clipX [node] = x;
		clipY [node] = y;
		clipWidth [node] = width;
		clipHeight [node] = height;
		flags [node] |= HAS_CLIP;
	}

	void clearClip (int node) {
		checkNode (node);
		flags [node] &= ~HAS_CLIP;
	}

	void setStroke (int node, int width, int style, int cap, int join) {
		checkNode (node);
		if (width < 0) throw new IllegalArgumentException ("negative line width");
		lineWidth [node] = width;
		lineStyle [node] = style;
		lineCap [node] = cap;
		lineJoin [node] = join;
		flags [node] |= HAS_STROKE;
	}

	void clearStroke (int node) {
		checkNode (node);
		flags [node] &= ~HAS_STROKE;
	}

	/**
	 * Maps one local point into root/device coordinates.
	 *
	 * @param out caller-owned array with room for at least two doubles
	 */
	void mapToRoot (int node, double x, double y, double [] out) {
		checkNode (node);
		checkOut (out, 2);
		requireFinite (x);
		requireFinite (y);
		int current = node;
		double px = x, py = y;
		while (current != ROOT) {
			double nextX = m00 [current] * px + m01 [current] * py + tx [current];
			double nextY = m10 [current] * px + m11 [current] * py + ty [current];
			px = nextX;
			py = nextY;
			current = parents [current];
		}
		out [0] = px;
		out [1] = py;
	}

	/**
	 * Maps one root/device point into a node's local coordinates.
	 *
	 * @return false when the composed affine transform is singular
	 */
	boolean mapFromRoot (int node, double x, double y, double [] out) {
		checkNode (node);
		checkOut (out, 2);
		requireFinite (x);
		requireFinite (y);
		rootTransform (node, transformScratch);
		double determinant = transformScratch [0] * transformScratch [3]
				- transformScratch [1] * transformScratch [2];
		if (determinant == 0) return false;
		double dx = x - transformScratch [4];
		double dy = y - transformScratch [5];
		out [0] = (transformScratch [3] * dx - transformScratch [1] * dy) / determinant;
		out [1] = (-transformScratch [2] * dx + transformScratch [0] * dy) / determinant;
		return true;
	}

	/**
	 * Computes the conservative axis-aligned root/device clip inherited by a
	 * node. Rotated local clips are transformed to their root-space bounds.
	 *
	 * @param out caller-owned array receiving x, y, width and height
	 * @return true when at least one clip is active
	 */
	boolean rootClip (int node, double [] out) {
		checkNode (node);
		checkOut (out, 4);
		boolean clipped = false;
		double left = Double.NEGATIVE_INFINITY;
		double top = Double.NEGATIVE_INFINITY;
		double right = Double.POSITIVE_INFINITY;
		double bottom = Double.POSITIVE_INFINITY;
		int current = node;
		while (current != ROOT) {
			if ((flags [current] & HAS_CLIP) != 0) {
				mapRectBounds (
						current,
						clipX [current], clipY [current],
						clipWidth [current], clipHeight [current],
						boundsScratch);
				if (!clipped) {
					left = bounds [0];
					top = bounds [1];
					right = bounds [0] + bounds [2];
					bottom = bounds [1] + bounds [3];
					clipped = true;
				} else {
					left = Math.max (left, bounds [0]);
					top = Math.max (top, bounds [1]);
					right = Math.min (right, bounds [0] + bounds [2]);
					bottom = Math.min (bottom, bounds [1] + bounds [3]);
				}
			}
			current = parents [current];
		}
		if (!clipped) return false;
		out [0] = left;
		out [1] = top;
		out [2] = Math.max (0, right - left);
		out [3] = Math.max (0, bottom - top);
		return true;
	}

	/**
	 * Returns the nearest inherited stroke state as width/style/cap/join.
	 */
	boolean effectiveStroke (int node, int [] out) {
		checkNode (node);
		if (out == null || out.length < 4) throw new IllegalArgumentException ("stroke output too small");
		for (int current = node; current != ROOT; current = parents [current]) {
			if ((flags [current] & HAS_STROKE) != 0) {
				out [0] = lineWidth [current];
				out [1] = lineStyle [current];
				out [2] = lineCap [current];
				out [3] = lineJoin [current];
				return true;
			}
		}
		return false;
	}

	private void mapRectBounds (
			int node, double x, double y, double width, double height, double [] out) {
		mapToRoot (node, x, y, pointScratch);
		double minX = pointScratch [0], maxX = pointScratch [0];
		double minY = pointScratch [1], maxY = pointScratch [1];

		mapToRoot (node, x + width, y, pointScratch);
		minX = Math.min (minX, pointScratch [0]);
		maxX = Math.max (maxX, pointScratch [0]);
		minY = Math.min (minY, pointScratch [1]);
		maxY = Math.max (maxY, pointScratch [1]);

		mapToRoot (node, x, y + height, pointScratch);
		minX = Math.min (minX, pointScratch [0]);
		maxX = Math.max (maxX, pointScratch [0]);
		minY = Math.min (minY, pointScratch [1]);
		maxY = Math.max (maxY, pointScratch [1]);

		mapToRoot (node, x + width, y + height, pointScratch);
		minX = Math.min (minX, pointScratch [0]);
		maxX = Math.max (maxX, pointScratch [0]);
		minY = Math.min (minY, pointScratch [1]);
		maxY = Math.max (maxY, pointScratch [1]);

		out [0] = minX;
		out [1] = minY;
		out [2] = maxX - minX;
		out [3] = maxY - minY;
	}

	private void rootTransform (int node, double [] out) {
		double a = 1, b = 0, c = 0, d = 1, x = 0, y = 0;
		int current = node;
		while (current != ROOT) {
			double nextA = m00 [current] * a + m01 [current] * c;
			double nextB = m00 [current] * b + m01 [current] * d;
			double nextC = m10 [current] * a + m11 [current] * c;
			double nextD = m10 [current] * b + m11 [current] * d;
			double nextX = m00 [current] * x + m01 [current] * y + tx [current];
			double nextY = m10 [current] * x + m11 [current] * y + ty [current];
			a = nextA;
			b = nextB;
			c = nextC;
			d = nextD;
			x = nextX;
			y = nextY;
			current = parents [current];
		}
		out [0] = a;
		out [1] = b;
		out [2] = c;
		out [3] = d;
		out [4] = x;
		out [5] = y;
	}

	private void ensureCapacity (int required) {
		if (required <= parents.length) return;
		int capacity = Math.max (required, parents.length * 3 / 2);
		parents = Arrays.copyOf (parents, capacity);
		layers = Arrays.copyOf (layers, capacity);
		flags = Arrays.copyOf (flags, capacity);
		m00 = Arrays.copyOf (m00, capacity);
		m01 = Arrays.copyOf (m01, capacity);
		m10 = Arrays.copyOf (m10, capacity);
		m11 = Arrays.copyOf (m11, capacity);
		tx = Arrays.copyOf (tx, capacity);
		ty = Arrays.copyOf (ty, capacity);
		clipX = Arrays.copyOf (clipX, capacity);
		clipY = Arrays.copyOf (clipY, capacity);
		clipWidth = Arrays.copyOf (clipWidth, capacity);
		clipHeight = Arrays.copyOf (clipHeight, capacity);
		lineWidth = Arrays.copyOf (lineWidth, capacity);
		lineStyle = Arrays.copyOf (lineStyle, capacity);
		lineCap = Arrays.copyOf (lineCap, capacity);
		lineJoin = Arrays.copyOf (lineJoin, capacity);
	}

	private void checkNode (int node) {
		if (node < 0 || node >= size) throw new IllegalArgumentException ("invalid paint-state node");
	}

	private static void checkOut (double [] out, int length) {
		if (out == null || out.length < length) throw new IllegalArgumentException ("paint output too small");
	}

	private static void requireFinite (double value) {
		if (!Double.isFinite (value)) throw new IllegalArgumentException ("non-finite paint coordinate");
	}
}
