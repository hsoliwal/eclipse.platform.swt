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
package org.eclipse.swt.internal;

import java.util.*;

import org.eclipse.swt.graphics.*;

/**
 * Internal retained paint graph for viewport-driven SWT rendering.
 *
 * <p>The graph does not proxy or retain {@link GC}. SWT's public GC remains the
 * execution surface and keeps its normal lifetime. Geometry, hierarchy and
 * instance links live in primitive columnar arrays. Detached templates may be
 * referenced by many instance nodes, allowing repeated row/header strokes to be
 * retained once and replayed under different affine transforms.</p>
 *
 * <p>Translation-only instances use coordinate offsets and allocate no native
 * {@link Transform}. General affine instances use at most two temporary
 * transforms for an entire replay and restore the caller's GC transform before
 * returning.</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ViewportPaintGraph {
	private static final byte GROUP = 0;
	private static final byte LINE = 1;
	private static final byte DRAW_RECT = 2;
	private static final byte FILL_RECT = 3;
	private static final byte TEXT = 4;
	private static final byte INSTANCE = 5;

	private static final int ROOT = 0;
	private static final int DETACHED = -2;
	private static final int NONE = -1;
	private static final int TEXT_TRANSPARENT = 1 << 0;
	private static final int HAS_CLIP = 1 << 1;
	private static final int HAS_STROKE = 1 << 2;

	private byte [] kinds = new byte [16];
	private int [] firstChild = new int [16];
	private int [] lastChild = new int [16];
	private int [] nextSibling = new int [16];
	private int [] previousSibling = new int [16];
	private int [] parents = new int [16];
	private int [] targets = new int [16];
	private int [] transformIds = new int [16];
	private int [] layers = new int [16];
	private float [] clipX = new float [16];
	private float [] clipY = new float [16];
	private float [] clipWidth = new float [16];
	private float [] clipHeight = new float [16];
	private int [] lineWidth = new int [16];
	private int [] lineStyle = new int [16];
	private int [] lineCap = new int [16];
	private int [] lineJoin = new int [16];
	private int [] a = new int [16];
	private int [] b = new int [16];
	private int [] c = new int [16];
	private int [] d = new int [16];
	private int [] flags = new int [16];
	private Object [] payloads = new Object [16];
	private int nodeCount = 1;
	private int geometryNodeCount;

	private float [] transforms = new float [6 * 8];
	private int transformCount = 1;

	public ViewportPaintGraph () {
		Arrays.fill (firstChild, NONE);
		Arrays.fill (lastChild, NONE);
		Arrays.fill (nextSibling, NONE);
		Arrays.fill (previousSibling, NONE);
		Arrays.fill (parents, NONE);
		Arrays.fill (targets, NONE);
		kinds [ROOT] = GROUP;
		setTransformElements (0, Affine.IDENTITY);
	}

	/**
	 * Affine transform in SWT element order
	 * {@code {m11, m12, m21, m22, dx, dy}}.
	 *
	 * @noreference This type is not intended to be referenced by clients.
	 */
	public record Affine (
			float m11, float m12, float m21, float m22, float dx, float dy) {
		public static final Affine IDENTITY = new Affine (1, 0, 0, 1, 0, 0);

		public Affine {
			if (!Float.isFinite (m11) || !Float.isFinite (m12)
					|| !Float.isFinite (m21) || !Float.isFinite (m22)
					|| !Float.isFinite (dx) || !Float.isFinite (dy)) {
				throw new IllegalArgumentException ("non-finite affine element");
			}
		}

		public static Affine translation (float x, float y) {
			return new Affine (1, 0, 0, 1, x, y);
		}

		public static Affine scale (float x, float y) {
			return new Affine (x, 0, 0, y, 0, 0);
		}

		/** Returns {@code this * local}; {@code local} is applied first. */
		public Affine compose (Affine local) {
			Objects.requireNonNull (local, "local");
			return new Affine (
					m11 * local.m11 + m21 * local.m12,
					m12 * local.m11 + m22 * local.m12,
					m11 * local.m21 + m21 * local.m22,
					m12 * local.m21 + m22 * local.m22,
					m11 * local.dx + m21 * local.dy + dx,
					m12 * local.dx + m22 * local.dy + dy);
		}

		public boolean isIntegralTranslation () {
			return same (m11, 1) && same (m12, 0)
					&& same (m21, 0) && same (m22, 1)
					&& dx == Math.rint (dx) && dy == Math.rint (dy);
		}

		private static boolean same (float left, float right) {
			return Float.floatToIntBits (left) == Float.floatToIntBits (right);
		}
	}

	/**
	 * Replay counters used by diagnostics and regression tests.
	 *
	 * @noreference This type is not intended to be referenced by clients.
	 */
	public record ReplayStats (
			int visitedNodes, int drawnCommands, int culledCommands, int transformSwitches) {
	}

	public int root () {
		return ROOT;
	}

	/** Creates a detached reusable group. */
	public int template () {
		return newNode (GROUP, DETACHED);
	}

	public int group (int parent) {
		return group (parent, 0);
	}

	public int group (int parent, int layer) {
		requireGroup (parent);
		int node = newNode (GROUP, parent);
		layers [node] = layer;
		return node;
	}

	public int layer (int node) {
		requireGroup (node);
		return layers [node];
	}

	public void setLayer (int node, int layer) {
		requireGroup (node);
		layers [node] = layer;
	}

	public void setTransform (int node, Affine transform) {
		requireGroup (node);
		Objects.requireNonNull (transform, "transform");
		int id = transformIds [node];
		if (id == 0) transformIds [node] = storeTransform (transform);
		else setTransformElements (id, transform);
	}

	public void setTranslation (int node, float x, float y) {
		setTransform (node, Affine.translation (x, y));
	}

	public void setClip (int node, float x, float y, float width, float height) {
		requireGroup (node);
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

	public void clearClip (int node) {
		requireGroup (node);
		flags [node] &= ~HAS_CLIP;
	}

	public void setStroke (int node, int width, int style, int cap, int join) {
		requireGroup (node);
		if (width < 0) throw new IllegalArgumentException ("negative line width");
		lineWidth [node] = width;
		lineStyle [node] = style;
		lineCap [node] = cap;
		lineJoin [node] = join;
		flags [node] |= HAS_STROKE;
	}

	public void clearStroke (int node) {
		requireGroup (node);
		flags [node] &= ~HAS_STROKE;
	}

	public void mapToRoot (int node, float x, float y, float [] out) {
		requireGroup (node);
		checkOut (out, 2);
		requireFinite (x);
		requireFinite (y);
		Affine transform = rootTransform (node);
		out [0] = transform.m11 * x + transform.m21 * y + transform.dx;
		out [1] = transform.m12 * x + transform.m22 * y + transform.dy;
	}

	public boolean mapFromRoot (int node, float x, float y, float [] out) {
		requireGroup (node);
		checkOut (out, 2);
		requireFinite (x);
		requireFinite (y);
		Affine transform = rootTransform (node);
		float determinant = transform.m11 * transform.m22 - transform.m12 * transform.m21;
		if (determinant == 0) return false;
		float dx = x - transform.dx;
		float dy = y - transform.dy;
		out [0] = (transform.m22 * dx - transform.m21 * dy) / determinant;
		out [1] = (-transform.m12 * dx + transform.m11 * dy) / determinant;
		return true;
	}

	public boolean rootClip (int node, float [] out) {
		requireGroup (node);
		checkOut (out, 4);
		boolean clipped = false;
		float left = Float.NEGATIVE_INFINITY;
		float top = Float.NEGATIVE_INFINITY;
		float right = Float.POSITIVE_INFINITY;
		float bottom = Float.POSITIVE_INFINITY;
		float [] bounds = new float [4];
		for (int current = node; current != NONE; current = parents [current]) {
			if ((flags [current] & HAS_CLIP) == 0) continue;
			mapRectBounds (
					current, clipX [current], clipY [current],
					clipWidth [current], clipHeight [current], bounds);
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
		if (!clipped) return false;
		out [0] = left;
		out [1] = top;
		out [2] = Math.max (0, right - left);
		out [3] = Math.max (0, bottom - top);
		return true;
	}

	public boolean effectiveStroke (int node, int [] out) {
		requireGroup (node);
		if (out == null || out.length < 4) throw new IllegalArgumentException ("stroke output too small");
		for (int current = node; current != NONE; current = parents [current]) {
			if ((flags [current] & HAS_STROKE) == 0) continue;
			out [0] = lineWidth [current];
			out [1] = lineStyle [current];
			out [2] = lineCap [current];
			out [3] = lineJoin [current];
			return true;
		}
		return false;
	}

	/**
	 * Applies a viewport group's coordinate/clip/stroke state to the real SWT
	 * GC for one bounded callback and restores the caller's state afterwards.
	 */
	public void withState (GC gc, int node, Runnable painter) {
		Objects.requireNonNull (gc, "gc");
		Objects.requireNonNull (painter, "painter");
		requireGroup (node);
		if (gc.isDisposed ()) throw new IllegalArgumentException ("disposed GC");

		Transform savedTransform = new Transform (gc.getDevice ());
		Transform workTransform = new Transform (gc.getDevice ());
		Region savedClip = new Region (gc.getDevice ());
		Region workClip = new Region (gc.getDevice ());
		int oldLineWidth = gc.getLineWidth ();
		int oldLineStyle = gc.getLineStyle ();
		int oldLineCap = gc.getLineCap ();
		int oldLineJoin = gc.getLineJoin ();
		try {
			gc.getTransform (savedTransform);
			gc.getClipping (savedClip);
			gc.getClipping (workClip);

			float [] clip = new float [4];
			if (rootClip (node, clip)) {
				int x = (int)Math.floor (clip [0]);
				int y = (int)Math.floor (clip [1]);
				int right = (int)Math.ceil (clip [0] + clip [2]);
				int bottom = (int)Math.ceil (clip [1] + clip [3]);
				workClip.intersect (x, y, Math.max (0, right - x), Math.max (0, bottom - y));
				gc.setClipping (workClip);
			}

			float [] existing = new float [6];
			savedTransform.getElements (existing);
			Affine base = new Affine (
					existing [0], existing [1], existing [2],
					existing [3], existing [4], existing [5]);
			Affine combined = base.compose (rootTransform (node));
			workTransform.setElements (
					combined.m11, combined.m12, combined.m21,
					combined.m22, combined.dx, combined.dy);
			gc.setTransform (workTransform);

			int [] stroke = new int [4];
			if (effectiveStroke (node, stroke)) {
				gc.setLineWidth (stroke [0]);
				gc.setLineStyle (stroke [1]);
				gc.setLineCap (stroke [2]);
				gc.setLineJoin (stroke [3]);
			}
			painter.run ();
		} finally {
			gc.setTransform (savedTransform);
			gc.setClipping (savedClip);
			gc.setLineWidth (oldLineWidth);
			gc.setLineStyle (oldLineStyle);
			gc.setLineCap (oldLineCap);
			gc.setLineJoin (oldLineJoin);
			workClip.dispose ();
			savedClip.dispose ();
			workTransform.dispose ();
			savedTransform.dispose ();
		}
	}

	public int line (int parent, int x1, int y1, int x2, int y2) {
		requireGroup (parent);
		int node = newNode (LINE, parent);
		a [node] = x1;
		b [node] = y1;
		c [node] = x2;
		d [node] = y2;
		geometryNodeCount++;
		return node;
	}

	public int drawRectangle (int parent, int x, int y, int width, int height) {
		requireGroup (parent);
		int node = newNode (DRAW_RECT, parent);
		a [node] = x;
		b [node] = y;
		c [node] = width;
		d [node] = height;
		geometryNodeCount++;
		return node;
	}

	public int fillRectangle (int parent, int x, int y, int width, int height) {
		requireGroup (parent);
		int node = newNode (FILL_RECT, parent);
		a [node] = x;
		b [node] = y;
		c [node] = width;
		d [node] = height;
		geometryNodeCount++;
		return node;
	}

	public int text (int parent, String text, int x, int y, boolean transparent) {
		requireGroup (parent);
		Objects.requireNonNull (text, "text");
		int node = newNode (TEXT, parent);
		a [node] = x;
		b [node] = y;
		payloads [node] = text;
		if (transparent) flags [node] |= TEXT_TRANSPARENT;
		geometryNodeCount++;
		return node;
	}

	/**
	 * Adds one DAG edge from {@code parent} to a detached template.
	 */
	public int instance (int parent, int template, Affine transform) {
		requireGroup (parent);
		if (template <= ROOT || template >= nodeCount || kinds [template] != GROUP
				|| parents [template] != DETACHED) {
			throw new IllegalArgumentException ("instance target is not a detached template");
		}
		if (wouldReach (template, parent)) {
			throw new IllegalArgumentException ("paint graph cycle");
		}
		int node = newNode (INSTANCE, parent);
		targets [node] = template;
		transformIds [node] = storeTransform (Objects.requireNonNull (transform, "transform"));
		return node;
	}

	public int nodeCount () {
		return nodeCount;
	}

	public int geometryNodeCount () {
		return geometryNodeCount;
	}

	public int transformCount () {
		return transformCount;
	}

	public ReplayStats replay (GC gc) {
		return replay (gc, ROOT, Affine.IDENTITY, null);
	}

	public ReplayStats replay (GC gc, Affine transform, Rectangle clip) {
		return replay (gc, ROOT, transform, clip);
	}

	public ReplayStats replayTemplate (GC gc, int template, Affine transform, Rectangle clip) {
		if (template <= ROOT || template >= nodeCount || kinds [template] != GROUP
				|| parents [template] != DETACHED) {
			throw new IllegalArgumentException ("not a detached template");
		}
		return replay (gc, template, transform, clip);
	}

	private ReplayStats replay (GC gc, int start, Affine initialTransform, Rectangle clip) {
		Objects.requireNonNull (gc, "gc");
		Objects.requireNonNull (initialTransform, "initialTransform");
		if (gc.isDisposed ()) throw new IllegalArgumentException ("disposed GC");

		int initialCapacity = Math.max (16, nodeCount);
		int [] nodeStack = new int [initialCapacity];
		Affine [] transformStack = new Affine [initialCapacity];
		int stackSize = 0;

		nodeStack [stackSize] = start;
		transformStack [stackSize++] = initialTransform;

		Transform saved = null;
		Transform work = null;
		Affine savedAffine = null;
		boolean transformedGc = false;
		int visited = 0;
		int drawn = 0;
		int culled = 0;
		int transformSwitches = 0;

		try {
			while (stackSize != 0) {
				int node = nodeStack [--stackSize];
				Affine transform = transformStack [stackSize];
				visited++;

				switch (kinds [node]) {
					case GROUP -> {
						Affine next = transform.compose (transform (transformIds [node]));
						for (int child = lastChild [node]; child != NONE; child = previousSibling [child]) {
							if (stackSize == nodeStack.length) {
								nodeStack = Arrays.copyOf (nodeStack, nodeStack.length * 2);
								transformStack = Arrays.copyOf (transformStack, transformStack.length * 2);
							}
							nodeStack [stackSize] = child;
							transformStack [stackSize++] = next;
						}
					}
					case INSTANCE -> {
						Affine next = transform.compose (transform (transformIds [node]));
						if (stackSize == nodeStack.length) {
							nodeStack = Arrays.copyOf (nodeStack, nodeStack.length * 2);
							transformStack = Arrays.copyOf (transformStack, transformStack.length * 2);
						}
						nodeStack [stackSize] = targets [node];
						transformStack [stackSize++] = next;
					}
					default -> {
						if (clip != null && transform.isIntegralTranslation ()
								&& outsideClip (node, transform, clip)) {
							culled++;
							continue;
						}
						boolean fast = transform.isIntegralTranslation ();
						if (!fast) {
							if (saved == null) {
								saved = new Transform (gc.getDevice ());
								work = new Transform (gc.getDevice ());
								gc.getTransform (saved);
								float [] elements = new float [6];
								saved.getElements (elements);
								savedAffine = new Affine (
										elements [0], elements [1], elements [2],
										elements [3], elements [4], elements [5]);
							}
							Affine combined = savedAffine.compose (transform);
							work.setElements (
									combined.m11, combined.m12, combined.m21,
									combined.m22, combined.dx, combined.dy);
							gc.setTransform (work);
							transformedGc = true;
							transformSwitches++;
							drawRaw (gc, node, 0, 0);
						} else {
							if (transformedGc) {
								gc.setTransform (saved);
								transformedGc = false;
								transformSwitches++;
							}
							drawRaw (gc, node, Math.round (transform.dx), Math.round (transform.dy));
						}
						drawn++;
					}
				}
			}
		} finally {
			if (saved != null) {
				if (transformedGc) gc.setTransform (saved);
				work.dispose ();
				saved.dispose ();
			}
		}
		return new ReplayStats (visited, drawn, culled, transformSwitches);
	}

	private void drawRaw (GC gc, int node, int dx, int dy) {
		switch (kinds [node]) {
			case LINE -> gc.drawLine (a [node] + dx, b [node] + dy, c [node] + dx, d [node] + dy);
			case DRAW_RECT -> gc.drawRectangle (a [node] + dx, b [node] + dy, c [node], d [node]);
			case FILL_RECT -> gc.fillRectangle (a [node] + dx, b [node] + dy, c [node], d [node]);
			case TEXT -> gc.drawText (
					(String)payloads [node], a [node] + dx, b [node] + dy,
					(flags [node] & TEXT_TRANSPARENT) != 0);
			default -> throw new IllegalStateException ("not a paint command");
		}
	}

	private boolean outsideClip (int node, Affine transform, Rectangle clip) {
		int dx = Math.round (transform.dx);
		int dy = Math.round (transform.dy);
		int left;
		int top;
		int right;
		int bottom;
		switch (kinds [node]) {
			case LINE -> {
				left = Math.min (a [node], c [node]) + dx;
				top = Math.min (b [node], d [node]) + dy;
				right = Math.max (a [node], c [node]) + dx + 1;
				bottom = Math.max (b [node], d [node]) + dy + 1;
			}
			case DRAW_RECT, FILL_RECT -> {
				left = Math.min (a [node], a [node] + c [node]) + dx;
				top = Math.min (b [node], b [node] + d [node]) + dy;
				right = Math.max (a [node], a [node] + c [node]) + dx + 1;
				bottom = Math.max (b [node], b [node] + d [node]) + dy + 1;
			}
			case TEXT -> {
				// Font metrics are intentionally not retained in the graph.
				return false;
			}
			default -> {
				return false;
			}
		}
		return right <= clip.x || bottom <= clip.y
				|| left >= clip.x + clip.width || top >= clip.y + clip.height;
	}

	private int newNode (byte kind, int parent) {
		ensureNodeCapacity (nodeCount + 1);
		int node = nodeCount++;
		kinds [node] = kind;
		firstChild [node] = lastChild [node] = NONE;
		nextSibling [node] = previousSibling [node] = NONE;
		parents [node] = parent;
		targets [node] = NONE;
		if (parent >= 0) linkChild (parent, node);
		return node;
	}

	private void linkChild (int parent, int child) {
		int tail = lastChild [parent];
		if (tail == NONE) {
			firstChild [parent] = lastChild [parent] = child;
			return;
		}
		nextSibling [tail] = child;
		previousSibling [child] = tail;
		lastChild [parent] = child;
	}

	private boolean wouldReach (int start, int wanted) {
		if (start == wanted) return true;
		boolean [] seen = new boolean [nodeCount];
		int [] stack = new int [Math.max (16, nodeCount)];
		int size = 0;
		stack [size++] = start;
		while (size != 0) {
			int node = stack [--size];
			if (node == wanted) return true;
			if (seen [node]) continue;
			seen [node] = true;
			for (int child = firstChild [node]; child != NONE; child = nextSibling [child]) {
				if (size == stack.length) stack = Arrays.copyOf (stack, stack.length * 2);
				stack [size++] = child;
				if (kinds [child] == INSTANCE) {
					if (size == stack.length) stack = Arrays.copyOf (stack, stack.length * 2);
					stack [size++] = targets [child];
				}
			}
		}
		return false;
	}

	private Affine rootTransform (int node) {
		Affine result = Affine.IDENTITY;
		for (int current = node; current != NONE; current = parents [current]) {
			if (kinds [current] != GROUP) continue;
			result = transform (transformIds [current]).compose (result);
		}
		return result;
	}

	private void mapRectBounds (
			int node, float x, float y, float width, float height, float [] out) {
		float [] point = new float [2];
		mapToRoot (node, x, y, point);
		float minX = point [0], maxX = point [0];
		float minY = point [1], maxY = point [1];
		mapToRoot (node, x + width, y, point);
		minX = Math.min (minX, point [0]); maxX = Math.max (maxX, point [0]);
		minY = Math.min (minY, point [1]); maxY = Math.max (maxY, point [1]);
		mapToRoot (node, x, y + height, point);
		minX = Math.min (minX, point [0]); maxX = Math.max (maxX, point [0]);
		minY = Math.min (minY, point [1]); maxY = Math.max (maxY, point [1]);
		mapToRoot (node, x + width, y + height, point);
		minX = Math.min (minX, point [0]); maxX = Math.max (maxX, point [0]);
		minY = Math.min (minY, point [1]); maxY = Math.max (maxY, point [1]);
		out [0] = minX;
		out [1] = minY;
		out [2] = maxX - minX;
		out [3] = maxY - minY;
	}

	private static void checkOut (float [] out, int length) {
		if (out == null || out.length < length) throw new IllegalArgumentException ("paint output too small");
	}

	private static void requireFinite (float value) {
		if (!Float.isFinite (value)) throw new IllegalArgumentException ("non-finite paint coordinate");
	}

	private int storeTransform (Affine transform) {
		ensureTransformCapacity (transformCount + 1);
		int id = transformCount++;
		setTransformElements (id, transform);
		return id;
	}

	private Affine transform (int id) {
		int offset = id * 6;
		return new Affine (
				transforms [offset], transforms [offset + 1], transforms [offset + 2],
				transforms [offset + 3], transforms [offset + 4], transforms [offset + 5]);
	}

	private void setTransformElements (int id, Affine transform) {
		int offset = id * 6;
		transforms [offset] = transform.m11;
		transforms [offset + 1] = transform.m12;
		transforms [offset + 2] = transform.m21;
		transforms [offset + 3] = transform.m22;
		transforms [offset + 4] = transform.dx;
		transforms [offset + 5] = transform.dy;
	}

	private void requireGroup (int node) {
		if (node < 0 || node >= nodeCount || kinds [node] != GROUP) {
			throw new IllegalArgumentException ("parent is not a group");
		}
	}

	private void ensureNodeCapacity (int required) {
		if (required <= kinds.length) return;
		int old = kinds.length;
		int next = Math.max (required, old * 2);
		kinds = Arrays.copyOf (kinds, next);
		firstChild = grow (firstChild, old, next, NONE);
		lastChild = grow (lastChild, old, next, NONE);
		nextSibling = grow (nextSibling, old, next, NONE);
		previousSibling = grow (previousSibling, old, next, NONE);
		parents = grow (parents, old, next, NONE);
		targets = grow (targets, old, next, NONE);
		transformIds = Arrays.copyOf (transformIds, next);
		layers = Arrays.copyOf (layers, next);
		clipX = Arrays.copyOf (clipX, next);
		clipY = Arrays.copyOf (clipY, next);
		clipWidth = Arrays.copyOf (clipWidth, next);
		clipHeight = Arrays.copyOf (clipHeight, next);
		lineWidth = Arrays.copyOf (lineWidth, next);
		lineStyle = Arrays.copyOf (lineStyle, next);
		lineCap = Arrays.copyOf (lineCap, next);
		lineJoin = Arrays.copyOf (lineJoin, next);
		a = Arrays.copyOf (a, next);
		b = Arrays.copyOf (b, next);
		c = Arrays.copyOf (c, next);
		d = Arrays.copyOf (d, next);
		flags = Arrays.copyOf (flags, next);
		payloads = Arrays.copyOf (payloads, next);
	}

	private static int [] grow (int [] source, int oldLength, int newLength, int fill) {
		int [] result = Arrays.copyOf (source, newLength);
		Arrays.fill (result, oldLength, newLength, fill);
		return result;
	}

	private void ensureTransformCapacity (int required) {
		int current = transforms.length / 6;
		if (required <= current) return;
		transforms = Arrays.copyOf (transforms, Math.max (required, current * 2) * 6);
	}
}
