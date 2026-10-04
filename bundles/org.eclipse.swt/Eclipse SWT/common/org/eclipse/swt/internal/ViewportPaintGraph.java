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
	private static final byte TEXT_TRANSPARENT = 1;

	private byte [] kinds = new byte [16];
	private int [] firstChild = new int [16];
	private int [] lastChild = new int [16];
	private int [] nextSibling = new int [16];
	private int [] previousSibling = new int [16];
	private int [] parents = new int [16];
	private int [] targets = new int [16];
	private int [] transformIds = new int [16];
	private int [] a = new int [16];
	private int [] b = new int [16];
	private int [] c = new int [16];
	private int [] d = new int [16];
	private byte [] flags = new byte [16];
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
		requireGroup (parent);
		return newNode (GROUP, parent);
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
		// One conservative envelope per replay, including fractional widths, square
		// caps, rectangle joins and antialiasing. No per-command native allocation.
		long strokeOutset = Math.max (1L, gc.getLineWidth ()) + 1;

		try {
			while (stackSize != 0) {
				int node = nodeStack [--stackSize];
				Affine transform = transformStack [stackSize];
				visited++;

				switch (kinds [node]) {
					case GROUP -> {
						for (int child = lastChild [node]; child != NONE; child = previousSibling [child]) {
							if (stackSize == nodeStack.length) {
								nodeStack = Arrays.copyOf (nodeStack, nodeStack.length * 2);
								transformStack = Arrays.copyOf (transformStack, transformStack.length * 2);
							}
							nodeStack [stackSize] = child;
							transformStack [stackSize++] = transform;
						}
					}
					case INSTANCE -> {
						Affine next = transform.compose (transform (transformIds [node]));
						int template = targets [node];
						for (int child = lastChild [template]; child != NONE; child = previousSibling [child]) {
							if (stackSize == nodeStack.length) {
								nodeStack = Arrays.copyOf (nodeStack, nodeStack.length * 2);
								transformStack = Arrays.copyOf (transformStack, transformStack.length * 2);
							}
							nodeStack [stackSize] = child;
							transformStack [stackSize++] = next;
						}
					}
					default -> {
						if (clip != null && transform.isIntegralTranslation ()
								&& outsideClip (node, transform, clip, strokeOutset)) {
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

	private boolean outsideClip (int node, Affine transform, Rectangle clip, long strokeOutset) {
		int dx = Math.round (transform.dx);
		int dy = Math.round (transform.dy);
		long left;
		long top;
		long right;
		long bottom;
		switch (kinds [node]) {
			case LINE -> {
				left = Math.min (a [node], c [node]) + (long)dx;
				top = Math.min (b [node], d [node]) + (long)dy;
				right = Math.max (a [node], c [node]) + (long)dx + 1;
				bottom = Math.max (b [node], d [node]) + (long)dy + 1;
			}
			case DRAW_RECT, FILL_RECT -> {
				left = Math.min (a [node], (long)a [node] + c [node]) + dx;
				top = Math.min (b [node], (long)b [node] + d [node]) + dy;
				right = Math.max (a [node], (long)a [node] + c [node]) + dx + 1;
				bottom = Math.max (b [node], (long)b [node] + d [node]) + dy + 1;
			}
			case TEXT -> {
				// Font metrics are intentionally not retained in the graph.
				return false;
			}
			default -> {
				return false;
			}
		}
		if (kinds [node] == LINE || kinds [node] == DRAW_RECT) {
			left -= strokeOutset;
			top -= strokeOutset;
			right += strokeOutset;
			bottom += strokeOutset;
		}
		return right <= clip.x || bottom <= clip.y
				|| left >= (long)clip.x + clip.width || top >= (long)clip.y + clip.height;
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
