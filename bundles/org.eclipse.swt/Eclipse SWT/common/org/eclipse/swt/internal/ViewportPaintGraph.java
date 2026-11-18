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
 * <p>Translation-only commands use coordinate offsets. Replay without clipping,
 * images or general affine transforms allocates no native {@link Transform}.
 * Clipped/affine replay uses at most two temporary transforms and restores the
 * caller's GC transform before returning.</p>
 *
 * <p>Image nodes are flattened viewport layers. They borrow caller-owned images,
 * compose each image once with its opacity, and participate in the same sibling
 * z order, transforms and clips as retained commands. The owner must redraw the
 * old and new covered areas after a mutation, keep images alive during use, and
 * dispose them when no longer needed. No native child control is reordered.</p>
 *
 * <p>Group and replay clips are intersected with the caller's actual GC region,
 * including holes. Transformed clip polygons are rasterized to integer region
 * coordinates by rounding vertices to the nearest integer. Picking uses logical
 * half-open rectangles, so a raster boundary may differ because of rounding.</p>
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
	private static final byte DRAW_PATH = 6;
	private static final byte FILL_PATH = 7;
	private static final byte IMAGE = 8;

	private static final int ROOT = 0;
	private static final int DETACHED = -2;
	private static final int NONE = -1;
	private static final short TEXT_TRANSPARENT = 1;
	private static final short HAS_CLIP = 1 << 1;
	private static final short HAS_STROKE = 1 << 2;
	private static final short HAS_LAYER = 1 << 3;
	private static final short HAS_CULL_BOUNDS = 1 << 4;
	private static final short HIDDEN = 1 << 5;
	private static final short HAS_HIT_BOUNDS = 1 << 6;
	private static final short LOCKED = 1 << 7;
	private static final short HAS_OPACITY = 1 << 8;
	private static final short INPUT_TRANSPARENT = 1 << 9;

	private byte [] kinds = new byte [16];
	private int [] firstChild = new int [16];
	private int [] lastChild = new int [16];
	private int [] nextSibling = new int [16];
	private int [] previousSibling = new int [16];
	private int [] parents = new int [16];
	private int [] targets = new int [16];
	private int [] transformIds = new int [16];
	private int [] layers = new int [16];
	private int [] zOrders = new int [16];
	private byte [] opacities = new byte [16];
	private float [] clipX = new float [16];
	private float [] clipY = new float [16];
	private float [] clipWidth = new float [16];
	private float [] clipHeight = new float [16];
	private float [] cullX = new float [16];
	private float [] cullY = new float [16];
	private float [] cullWidth = new float [16];
	private float [] cullHeight = new float [16];
	private float [] hitX = new float [16];
	private float [] hitY = new float [16];
	private float [] hitWidth = new float [16];
	private float [] hitHeight = new float [16];
	private int [] lineWidth = new int [16];
	private int [] lineStyle = new int [16];
	private int [] lineCap = new int [16];
	private int [] lineJoin = new int [16];
	private int [] pathTypeOffsets = new int [16];
	private int [] pathTypeCounts = new int [16];
	private int [] pathPointOffsets = new int [16];
	private int [] pathPointCounts = new int [16];
	private float [] pathMinX = new float [16];
	private float [] pathMinY = new float [16];
	private float [] pathMaxX = new float [16];
	private float [] pathMaxY = new float [16];
	private int [] a = new int [16];
	private int [] b = new int [16];
	private int [] c = new int [16];
	private int [] d = new int [16];
	private short [] flags = new short [16];
	private Object [] payloads = new Object [16];
	private int nodeCount = 1;
	private int geometryNodeCount;
	private int pathNodeCount;
	private int imageNodeCount;
	private int clipNodeCount;
	private int opacityNodeCount;

	private byte [] pathTypes = new byte [64];
	private int pathTypeSize;
	private float [] pathPoints = new float [128];
	private int pathPointSize;

	private float [] transforms = new float [6 * 8];
	private int transformCount = 1;

	/*
	 * Viewport paint graphs are UI-thread confined. These reusable lanes keep
	 * coordinate/hit-test/clip queries allocation-free on paint and pointer paths.
	 */
	private final float [] affineScratch = new float [6];
	private final float [] boundsScratch = new float [4];
	private final float [] hitPointScratch = new float [2];
	private int [] ancestryScratch = new int [16];
	private int [] hitStack = new int [16];
	private boolean [] hitStackSiblings = new boolean [16];
	private int [] hitNodes = new int [16];
	private int [] hitInstances = new int [16];
	private boolean [] hitSiblings = new boolean [16];
	private int [] hitOpacities = new int [16];
	private float [] hitTransforms = new float [16 * 6];
	private final double [] hitPoint = new double [2];
	private int [] reachSeen = new int [16];
	private int [] reachStack = new int [16];
	private int reachEpoch;
	private ReplayStack replayStack;

	/**
	 * One graph-owned paint traversal workspace. It grows to the largest pending
	 * depth, not the total number of retained nodes. Nested replay receives a
	 * separate workspace; no GC or native resource is retained here.
	 */
	private static final class ReplayStack {
		int [] nodes = new int [16];
		Affine [] transforms = new Affine [16];
		int [] layers = new int [16];
		boolean [] hasLayers = new boolean [16];
		int [] opacities = new int [16];
		boolean [] siblings = new boolean [16];
		Region [] clips = new Region [16];
		final double [] clipVertices = new double [24];
		final double [] clipScratch = new double [24];
		Path [] paths = new Path [0];
		int [] usedPathSlots = new int [16];
		int usedPaths;
		boolean inUse;

		void ensureCapacity (int required) {
			if (required <= nodes.length) return;
			int capacity = Math.max (required, nodes.length * 2);
			// Allocate every lane before replacing any owner reference.
			int [] nextNodes = Arrays.copyOf (nodes, capacity);
			Affine [] nextTransforms = Arrays.copyOf (transforms, capacity);
			int [] nextLayers = Arrays.copyOf (layers, capacity);
			boolean [] nextHasLayers = Arrays.copyOf (hasLayers, capacity);
			int [] nextOpacities = Arrays.copyOf (opacities, capacity);
			boolean [] nextSiblings = Arrays.copyOf (siblings, capacity);
			Region [] nextClips = Arrays.copyOf (clips, capacity);
			nodes = nextNodes;
			transforms = nextTransforms;
			layers = nextLayers;
			hasLayers = nextHasLayers;
			opacities = nextOpacities;
			siblings = nextSiblings;
			clips = nextClips;
		}

		void reservePath (int slot) {
			if (slot >= paths.length) {
				paths = Arrays.copyOf (paths, Math.max (slot + 1, Math.max (16, paths.length * 2)));
			}
			if (usedPaths == usedPathSlots.length) {
				usedPathSlots = Arrays.copyOf (usedPathSlots, usedPathSlots.length * 2);
			}
		}

		void releasePaths () {
			Throwable failure = null;
			for (int index = 0; index < usedPaths; index++) {
				int slot = usedPathSlots [index];
				Path path = paths [slot];
				paths [slot] = null;
				try {
					if (path != null && !path.isDisposed ()) path.dispose ();
				} catch (RuntimeException | Error thrown) {
					if (failure == null) failure = thrown;
					else failure.addSuppressed (thrown);
				}
			}
			usedPaths = 0;
			if (failure instanceof Error error) throw error;
			if (failure instanceof RuntimeException error) throw error;
		}
	}

	private ReplayStack borrowReplayStack () {
		ReplayStack stack = replayStack;
		if (stack == null) {
			stack = new ReplayStack ();
			replayStack = stack;
		} else if (stack.inUse) {
			stack = new ReplayStack ();
		}
		stack.inUse = true;
		return stack;
	}

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
			if (x == 0f && y == 0f) return IDENTITY;
			return new Affine (1, 0, 0, 1, x, y);
		}

		public static Affine scale (float x, float y) {
			if (x == 1f && y == 1f) return IDENTITY;
			return new Affine (x, 0, 0, y, 0, 0);
		}

		public static Affine rotation (float radians) {
			if (radians == 0f) return IDENTITY;
			/*
			 * Preserve exact integer geometry for canonical float quadrant angles.
			 * Tiny residual cosine/sine values at +/-PI/2 and PI otherwise expand
			 * conservative bounds and can incorrectly retain or cull edge strokes.
			 * Adjacent representable angles keep their ordinary trig result.
			 */
			double quadrant = Math.rint (radians / (Math.PI / 2d));
			if (Math.abs (quadrant) <= 4d
					&& radians == (float)(quadrant * (Math.PI / 2d))) {
				return switch ((int)quadrant & 3) {
					case 0 -> IDENTITY;
					case 1 -> new Affine (0, 1, -1, 0, 0, 0);
					case 2 -> new Affine (-1, 0, 0, -1, 0, 0);
					default -> new Affine (0, -1, 1, 0, 0, 0);
				};
			}
			float sin = (float)Math.sin (radians);
			float cos = (float)Math.cos (radians);
			return new Affine (cos, sin, -sin, cos, 0, 0);
		}

		public static Affine shear (float x, float y) {
			if (x == 0f && y == 0f) return IDENTITY;
			return new Affine (1, y, x, 1, 0, 0);
		}

		/** Returns {@code this * local}; {@code local} is applied first. */
		public Affine compose (Affine local) {
			Objects.requireNonNull (local, "local");
			if (local.isIdentity ()) return this;
			if (isIdentity ()) return local;
			return new Affine (
					m11 * local.m11 + m21 * local.m12,
					m12 * local.m11 + m22 * local.m12,
					m11 * local.m21 + m21 * local.m22,
					m12 * local.m21 + m22 * local.m22,
					m11 * local.dx + m21 * local.dy + dx,
					m12 * local.dx + m22 * local.dy + dy);
		}

		public float determinant () {
			return m11 * m22 - m21 * m12;
		}

		public Affine inverse () {
			float determinant = determinant ();
			if (!Float.isFinite (determinant) || determinant == 0f) {
				throw new IllegalStateException ("non-invertible affine transform");
			}
			float inverseDeterminant = 1f / determinant;
			float i11 = m22 * inverseDeterminant;
			float i12 = -m12 * inverseDeterminant;
			float i21 = -m21 * inverseDeterminant;
			float i22 = m11 * inverseDeterminant;
			float idx = -(i11 * dx + i21 * dy);
			float idy = -(i12 * dx + i22 * dy);
			return new Affine (i11, i12, i21, i22, idx, idy);
		}

		public boolean isIdentity () {
			return this == IDENTITY
					|| (same (m11, 1) && same (m12, 0)
					&& same (m21, 0) && same (m22, 1)
					&& same (dx, 0) && same (dy, 0));
		}

		public boolean isTranslationOnly () {
			return same (m11, 1) && same (m12, 0)
					&& same (m21, 0) && same (m22, 1);
		}

		public boolean isIntegralTranslation () {
			return isTranslationOnly ()
					&& dx == Math.rint (dx) && dy == Math.rint (dy);
		}

		public void map (float x, float y, float [] out) {
			if (out == null || out.length < 2) {
				throw new IllegalArgumentException ("affine output too small");
			}
			out [0] = m11 * x + m21 * y + dx;
			out [1] = m12 * x + m22 * y + dy;
		}

		public boolean inverseMap (float x, float y, float [] out) {
			if (out == null || out.length < 2) {
				throw new IllegalArgumentException ("affine output too small");
			}
			float determinant = determinant ();
			if (!Float.isFinite (determinant) || determinant == 0f) return false;
			float px = x - dx;
			float py = y - dy;
			out [0] = (m22 * px - m21 * py) / determinant;
			out [1] = (-m12 * px + m11 * py) / determinant;
			return true;
		}

		private static boolean same (float left, float right) {
			return Float.floatToIntBits (left) == Float.floatToIntBits (right);
		}
	}

	/**
	 * OpStreamReplay counters used by diagnostics and regression tests.
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
		return group (parent, Affine.IDENTITY);
	}

	public int group (int parent, Affine transform) {
		requireGroup (parent);
		int id = transformId (Objects.requireNonNull (transform, "transform"));
		int node = newNode (GROUP, parent);
		transformIds [node] = id;
		return node;
	}

	public void setTransform (int group, Affine transform) {
		requireGroup (group);
		Objects.requireNonNull (transform, "transform");
		int id = transformIds [group];
		if (id == 0) {
			transformIds [group] = transformId (transform);
		} else {
			// Non-identity slots have one owner. Reuse its lane across scroll frames,
			// including identity resets, instead of retaining every past transform.
			setTransformElements (id, transform);
		}
	}

	public void setLayer (int group, int layer) {
		requireGroup (group);
		layers [group] = layer;
		flags [group] |= HAS_LAYER;
	}

	public void clearLayer (int group) {
		requireGroup (group);
		flags [group] &= ~HAS_LAYER;
	}

	/** Returns whether the owner and every ancestor are visible. */
	public boolean isEffectivelyVisible (int node) {
		requireVisibleOwner (node);
		for (int current = node; current >= 0; current = parents [current]) {
			if ((flags [current] & HIDDEN) != 0) return false;
		}
		return kinds [node] != INSTANCE || isEffectivelyVisible (targets [node]);
	}

	/**
	 * Locks a layer against pointer selection. Locked layers still paint and may
	 * be updated by internal rendering code; this is not widget disposal state.
	 */
	public void setLocked (int node, boolean locked) {
		requireVisibleOwner (node);
		if (locked) flags [node] |= LOCKED;
		else flags [node] &= ~LOCKED;
	}

	/** Returns whether this layer or one of its owners is locked. */
	public boolean isLocked (int node) {
		requireVisibleOwner (node);
		for (int current = node; current >= 0; current = parents [current]) {
			if ((flags [current] & LOCKED) != 0) return true;
		}
		return kinds [node] == INSTANCE && isLocked (targets [node]);
	}

	/**
	 * Sets per-command alpha inherited by groups, template instances and images.
	 * A flattened image blends once at the accumulated alpha; overlapping child
	 * commands in a group each blend individually. The owning widget remains
	 * responsible for redraw and for any isolated offscreen group compositing.
	 */
	public void setOpacity (int node, int opacity) {
		requireVisibleOwner (node);
		if (opacity < 0 || opacity > 255) {
			throw new IllegalArgumentException ("opacity must be in 0..255");
		}
		boolean hadOpacity = (flags [node] & HAS_OPACITY) != 0;
		if (opacity == 255) {
			flags [node] &= ~HAS_OPACITY;
			if (hadOpacity) opacityNodeCount--;
		} else {
			opacities [node] = (byte)opacity;
			flags [node] |= HAS_OPACITY;
			if (!hadOpacity) opacityNodeCount++;
		}
	}

	/** Returns the layer's local 0..255 opacity. */
	public int opacity (int node) {
		requireVisibleOwner (node);
		return (flags [node] & HAS_OPACITY) == 0 ? 255 : opacities [node] & 0xff;
	}

	/** Returns alpha accumulated in root-to-child order. */
	public int effectiveOpacity (int node) {
		requireVisibleOwner (node);
		int depth = 0;
		for (int current = node; current >= 0; current = parents [current]) {
			if (depth == ancestryScratch.length) {
				ancestryScratch = Arrays.copyOf (ancestryScratch, depth * 2);
			}
			ancestryScratch [depth++] = current;
		}
		int alpha = 255;
		while (depth != 0) {
			int owner = ancestryScratch [--depth];
			if ((flags [owner] & HAS_OPACITY) != 0) {
				alpha = multiplyAlpha (alpha, opacities [owner] & 0xff);
			}
		}
		if (kinds [node] == INSTANCE) {
			alpha = multiplyAlpha (alpha, effectiveOpacity (targets [node]));
		}
		return alpha;
	}

	/** Geometry and clipping are tested separately from this input eligibility. */
	public boolean isInteractive (int node) {
		return isEffectivelyVisible (node) && !isLocked (node) && acceptsPointer (node) && effectiveOpacity (node) != 0;
	}

	private boolean acceptsPointer (int node) {
		for (int current = node; current >= 0; current = parents [current]) {
			if ((flags [current] & INPUT_TRANSPARENT) != 0) return false;
		}
		return kinds [node] != INSTANCE || acceptsPointer (targets [node]);
	}

	private static int multiplyAlpha (int first, int second) {
		return (first * second + 127) / 255;
	}

	/**
	 * Attaches an opt-in half-open pointer region in the node's local coordinates.
	 * Unlike conservative cull bounds, these bounds determine event eligibility.
	 * Template instances need their own hit bounds because their template geometry
	 * may be reused at multiple independent viewport positions.
	 */
	public void setHitBounds (int node, float x, float y, float width, float height) {
		requireLayerOwner (node);
		if (!Float.isFinite (x) || !Float.isFinite (y)
				|| !Float.isFinite (width) || !Float.isFinite (height)
				|| width < 0 || height < 0) {
			throw new IllegalArgumentException ("invalid pointer bounds");
		}
		hitX [node] = x;
		hitY [node] = y;
		hitWidth [node] = width;
		hitHeight [node] = height;
		flags [node] |= HAS_HIT_BOUNDS;
	}

	/** Removes the node's pointer target without changing drawing or Z-order. */
	public void clearHitBounds (int node) {
		requireLayerOwner (node);
		flags [node] &= ~HAS_HIT_BOUNDS;
	}

	/**
	 * Finds the frontmost attached group or instance whose explicit region
	 * contains the point. Descendants take priority over their own group's region.
	 * Returns -1 if none qualifies. An instance is a distinct target; detached
	 * template internals are never returned as ambiguous global pointer targets.
	 * The normal event path reuses a bounded primitive stack (no recursion).
	 */
	public int hitTest (float rootX, float rootY) {
		if (!Float.isFinite (rootX) || !Float.isFinite (rootY)) {
			throw new IllegalArgumentException ("non-finite pointer position");
		}
		int size = 1;
		hitStack [0] = ROOT;
		hitStackSiblings [0] = false;
		while (size != 0) {
			int entry = hitStack [--size];
			if (entry < 0) {
				int node = -entry - 1;
				if (isInteractive (node) && containsLocalPoint (
						node, rootX, rootY,
						hitX [node], hitY [node], hitWidth [node], hitHeight [node])) {
					return node;
				}
				continue;
			}
			int node = entry;
			if (hitStackSiblings [size] && previousSibling [node] != NONE) {
				hitStack [size++] = previousSibling [node];
			}
			if ((flags [node] & (HIDDEN | LOCKED | INPUT_TRANSPARENT)) != 0
					|| ((flags [node] & HAS_OPACITY) != 0 && opacities [node] == 0)) continue;
			if (kinds [node] == GROUP) {
				if ((flags [node] & HAS_CLIP) != 0
						&& !containsLocalPoint (
								node, rootX, rootY,
								clipX [node], clipY [node], clipWidth [node], clipHeight [node])) {
					continue;
				}
				// Negative entries test parents after their children.
				if (node != ROOT && (flags [node] & HAS_HIT_BOUNDS) != 0) {
					pushHit (-node - 1, size++, false);
				}
				// Visit the frontmost sibling immediately, retaining one continuation.
				int child = lastChild [node];
				if (child != NONE) {
					pushHit (child, size++, true);
				}
			} else if (kinds [node] == INSTANCE
					&& (flags [node] & HAS_HIT_BOUNDS) != 0
					&& isInteractive (node)
					&& (flags [targets [node]] & INPUT_TRANSPARENT) == 0
					&& containsLocalPoint (
							node, rootX, rootY,
							hitX [node], hitY [node], hitWidth [node], hitHeight [node])) {
				return node;
			}
		}
		return NONE;
	}

	private void pushHit (int entry, int size, boolean siblings) {
		if (size >= hitStack.length) {
			int [] nodes = Arrays.copyOf (hitStack, hitStack.length * 2);
			boolean [] continuations = Arrays.copyOf (hitStackSiblings, nodes.length);
			hitStack = nodes;
			hitStackSiblings = continuations;
		}
		hitStack [size] = entry;
		hitStackSiblings [size] = siblings;
	}

	private boolean containsLocalPoint (
			int node, float rootX, float rootY,
			float x, float y, float width, float height) {
		if (width == 0 || height == 0 || !mapFromRoot (node, rootX, rootY, hitPointScratch)) {
			return false;
		}
		// Promote subtraction to double to avoid overflowing finite float endpoints.
		double dx = (double) hitPointScratch [0] - x;
		double dy = (double) hitPointScratch [1] - y;
		return dx >= 0 && dx < width && dy >= 0 && dy < height;
	}


	/**
	 * Orders one retained node among siblings in the same parent/viewport plane.
	 *
	 * <p>Lower values paint first. Equal values retain creation order. The sibling
	 * links are reordered when this method is called, so replay performs no
	 * sorting or allocation.</p>
	 */
	public void setZOrder (int node, int zOrder) {
		requireNode (node);
		int parent = parents [node];
        if (parent < 0) {
            throw new IllegalArgumentException("z-order requires an attached sibling");
        }
        if (zOrders [node] == zOrder) {
            return;
        }
		unlinkChild (parent, node);
		zOrders [node] = zOrder;
		linkChild (parent, node);
	}

	public int zOrder (int node) {
		requireNode (node);
		return zOrders [node];
	}

	public boolean effectiveLayer (int node, int [] out) {
		requireNode (node);
        if (out == null || out.length == 0) {
            throw new IllegalArgumentException("layer output too small");
        }
		for (int current = node; current >= 0; current = parents [current]) {
			if ((flags [current] & HAS_LAYER) != 0) {
				out [0] = layers [current];
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds a caller-owned, already flattened image layer. The graph never disposes
	 * or paints into the image. Use a group transform for scrolling and scaling.
	 */
	public int image (int parent, Image image, int x, int y) {
		requireGroup (parent);
		Rectangle bounds = imageBounds (image);
		int node = newNode (IMAGE, parent);
		a [node] = x;
		b [node] = y;
		c [node] = bounds.width;
		d [node] = bounds.height;
		payloads [node] = image;
		opacities [node] = (byte)255;
		imageNodeCount++;
		geometryNodeCount++;
		return node;
	}

	/** Replaces a borrowed image and its bounds without disposing either image. */
	public void setImage (int node, Image image) {
		requireImage (node);
		Rectangle bounds = imageBounds (image);
		payloads [node] = image;
		c [node] = bounds.width;
		d [node] = bounds.height;
	}

	/** Moves an image in its parent's coordinate space. */
	public void setImageLocation (int node, int x, int y) {
		requireImage (node);
		a [node] = x;
		b [node] = y;
	}

	/** Hides or shows a node and its complete subtree, without changing z order. */
	public void setVisible (int node, boolean visible) {
		requireVisibleOwner (node);
		if (visible) flags [node] &= ~HIDDEN;
		else flags [node] |= HIDDEN;
	}

	/** Returns this node's own visibility, before ancestor visibility is applied. */
	public boolean isVisible (int node) {
		requireVisibleOwner (node);
		return (flags [node] & HIDDEN) == 0;
	}

	/**
	 * Enables or disables picking for this node and its subtree in both queries.
	 * A disabled
	 * overlay remains visible and lets picking continue into the layers below it.
	 */
	public void setPickable (int node, boolean pickable) {
		requireNode (node);
		if (pickable) flags [node] &= ~INPUT_TRANSPARENT;
		else flags [node] |= INPUT_TRANSPARENT;
	}

	/** Returns this node's own picking policy, before ancestor policy is applied. */
	public boolean isPickable (int node) {
		requireNode (node);
		return (flags [node] & INPUT_TRANSPARENT) == 0;
	}

	/**
	 * A bounds hit in image-local coordinates. {@code instance} is the outermost
	 * attached template instance, or -1 for an ordinary attached image. It names
	 * the attachment, not a complete path through nested shared templates.
	 */
	public record Hit (int node, int instance, float x, float y) {
	}

	/**
	 * Picks the frontmost visible, pickable image with nonzero layer opacity.
	 * Traversal reverses painting order and uses the same group/instance affine
	 * chain and inherited clips of unfiltered {@link #replay(GC)}. An explicit
	 * {@link #replayLayers(GC, int[], Affine, Rectangle)} schedule is not used by
	 * this query. Bounds are half-open; individual transparent
	 * pixels still belong to the image. Singular transforms have no hit.
	 *
	 * <p>Coordinates are in graph-root space, before an optional replay transform.
	 * The host owns SWT input capture and must retain its chosen target during a
	 * drag. This method neither dispatches events nor changes native capture.</p>
	 *
	 * @return the hit, or {@code null} if no image contains the point
	 */
	public Hit hitTestImage (float rootX, float rootY) {
		if (!Float.isFinite (rootX) || !Float.isFinite (rootY)) {
			throw new IllegalArgumentException ("non-finite hit coordinate");
		}
		int size = 1;
		hitNodes [0] = ROOT;
		hitInstances [0] = NONE;
		hitOpacities [0] = 255;
		hitSiblings [0] = false;
		Arrays.fill (hitTransforms, 0, 6, 0);
		hitTransforms [0] = hitTransforms [3] = 1;
		while (size != 0) {
			int node = hitNodes [--size];
			int instance = hitInstances [size];
			int frame = size;
			int alpha = hitOpacities [frame];
			if (hitSiblings [frame] && previousSibling [node] != NONE) {
				// Keep only the next sibling, with the parent's original mapping.
				// Width does not grow the stack or delay the frontmost visit.
				hitNodes [size++] = previousSibling [node];
			}
			if ((flags [node] & (HIDDEN | LOCKED | INPUT_TRANSPARENT)) != 0) continue;
			if ((flags [node] & HAS_OPACITY) != 0) alpha = multiplyAlpha (alpha, opacities [node] & 0xff);
			if (alpha == 0) continue;
			if (kinds [node] != GROUP && kinds [node] != INSTANCE && kinds [node] != IMAGE) continue;
			System.arraycopy (hitTransforms, frame * 6, affineScratch, 0, 6);
			if (kinds [node] == GROUP || kinds [node] == INSTANCE) {
				composeHitTransform (transformIds [node]);
				if ((flags [node] & HAS_CLIP) != 0) {
					if (!inversePoint (affineScratch, rootX, rootY, hitPoint)
							|| !contains (hitPoint [0], hitPoint [1], clipX [node], clipY [node],
									clipWidth [node], clipHeight [node])) continue;
				}
				int first = kinds [node] == INSTANCE ? targets [node] : lastChild [node];
				if (kinds [node] == INSTANCE && instance == NONE) instance = node;
				if (first != NONE) {
					ensureHitCapacity (size + 1);
					hitNodes [size] = first;
					hitInstances [size] = instance;
					hitOpacities [size] = alpha;
					hitSiblings [size] = kinds [node] == GROUP;
					System.arraycopy (affineScratch, 0, hitTransforms, size * 6, 6);
					size++;
				}
			} else if (kinds [node] == IMAGE
					&& !((Image)payloads [node]).isDisposed ()
					&& inversePoint (affineScratch, rootX, rootY, hitPoint)
					&& contains (hitPoint [0], hitPoint [1], a [node], b [node], c [node], d [node])) {
				return new Hit (node, instance, (float)(hitPoint [0] - a [node]), (float)(hitPoint [1] - b [node]));
			}
		}
		return null;
	}

	/* Mirror Affine.compose arithmetic without allocating an Affine per pointer/node. */
	private void composeHitTransform (int id) {
		if (id == 0) return;
		int offset = id * 6;
		float m11 = affineScratch [0], m12 = affineScratch [1];
		float m21 = affineScratch [2], m22 = affineScratch [3];
		float dx = affineScratch [4], dy = affineScratch [5];
		affineScratch [0] = m11 * transforms [offset] + m21 * transforms [offset + 1];
		affineScratch [1] = m12 * transforms [offset] + m22 * transforms [offset + 1];
		affineScratch [2] = m11 * transforms [offset + 2] + m21 * transforms [offset + 3];
		affineScratch [3] = m12 * transforms [offset + 2] + m22 * transforms [offset + 3];
		affineScratch [4] = m11 * transforms [offset + 4] + m21 * transforms [offset + 5] + dx;
		affineScratch [5] = m12 * transforms [offset + 4] + m22 * transforms [offset + 5] + dy;
	}

	private void ensureHitCapacity (int required) {
		if (required <= hitNodes.length) return;
		int capacity = Math.max (required, Math.multiplyExact (hitNodes.length, 2));
		int [] nodes = Arrays.copyOf (hitNodes, capacity);
		int [] instances = Arrays.copyOf (hitInstances, capacity);
		boolean [] siblings = Arrays.copyOf (hitSiblings, capacity);
		int [] alphas = Arrays.copyOf (hitOpacities, capacity);
		float [] mappings = Arrays.copyOf (hitTransforms, Math.multiplyExact (capacity, 6));
		hitNodes = nodes;
		hitInstances = instances;
		hitSiblings = siblings;
		hitOpacities = alphas;
		hitTransforms = mappings;
	}

	private static boolean inversePoint (float [] mapping, double x, double y, double [] out) {
		double determinant = (double)mapping [0] * mapping [3] - (double)mapping [2] * mapping [1];
		if (determinant == 0 || !Double.isFinite (determinant)) return false;
		double px = x - mapping [4], py = y - mapping [5];
		out [0] = (mapping [3] * px - mapping [2] * py) / determinant;
		out [1] = (-mapping [1] * px + mapping [0] * py) / determinant;
		return Double.isFinite (out [0]) && Double.isFinite (out [1]);
	}

	private static boolean contains (double x, double y, double left, double top,
			double width, double height) {
		return x >= left && y >= top && x < left + width && y < top + height;
	}

	private static Rectangle imageBounds (Image image) {
		Objects.requireNonNull (image, "image");
		if (image.isDisposed ()) throw new IllegalArgumentException ("disposed image");
		return image.getBounds ();
	}

	private void requireImage (int node) {
		requireNode (node);
		if (kinds [node] != IMAGE) throw new IllegalArgumentException ("node is not an image");
	}

	public void setClip (int group, float x, float y, float width, float height) {
		requireGroup (group);
		if (!Float.isFinite (x) || !Float.isFinite (y)
				|| !Float.isFinite (width) || !Float.isFinite (height)
				|| width < 0 || height < 0) {
			throw new IllegalArgumentException ("invalid viewport clip");
		}
		if ((flags [group] & HAS_CLIP) == 0) clipNodeCount++;
		clipX [group] = x;
		clipY [group] = y;
		clipWidth [group] = width;
		clipHeight [group] = height;
		flags [group] |= HAS_CLIP;
	}

	public void clearClip (int group) {
		requireGroup (group);
		if ((flags [group] & HAS_CLIP) != 0) clipNodeCount--;
		flags [group] &= ~HAS_CLIP;
	}

	/**
	 * Sets conservative local-space bounds for coarse subtree culling.
	 *
	 * <p>This is deliberately separate from clipping.  A clip changes what may
	 * be drawn inside a group; cull bounds only let replay skip the whole
	 * retained subtree when none of it can intersect the requested replay
	 * region.  Callers should use the cheapest stable row/pane bounds they
	 * already know.</p>
	 */
	public void setCullBounds (int group, float x, float y, float width, float height) {
		requireGroup (group);
		if (!Float.isFinite (x) || !Float.isFinite (y)
				|| !Float.isFinite (width) || !Float.isFinite (height)
				|| width < 0 || height < 0) {
			throw new IllegalArgumentException ("invalid viewport cull bounds");
		}
		cullX [group] = x;
		cullY [group] = y;
		cullWidth [group] = width;
		cullHeight [group] = height;
		flags [group] |= HAS_CULL_BOUNDS;
	}

	public void clearCullBounds (int group) {
		requireGroup (group);
		flags [group] &= ~HAS_CULL_BOUNDS;
	}

	/**
	 * Computes the conservative root-space bounds of all clips inherited by
	 * one attached graph node. Detached templates have no unique root mapping.
	 */
	public boolean rootClip (int node, float [] out) {
		requireAttachedNode (node);
        if (out == null || out.length < 4) {
            throw new IllegalArgumentException("clip output too small");
        }
		boolean clipped = false;
		float left = Float.NEGATIVE_INFINITY;
		float top = Float.NEGATIVE_INFINITY;
		float right = Float.POSITIVE_INFINITY;
		float bottom = Float.POSITIVE_INFINITY;
		for (int current = node; current >= 0; current = parents [current]) {
            if ((flags [current] & HAS_CLIP) == 0) {
                continue;
            }
			mapRectToRoot (
					current,
					clipX [current], clipY [current],
					clipWidth [current], clipHeight [current],
					boundsScratch);
			if (!clipped) {
				left = boundsScratch [0];
				top = boundsScratch [1];
				right = boundsScratch [0] + boundsScratch [2];
				bottom = boundsScratch [1] + boundsScratch [3];
				clipped = true;
			} else {
				left = Math.max (left, boundsScratch [0]);
				top = Math.max (top, boundsScratch [1]);
				right = Math.min (right, boundsScratch [0] + boundsScratch [2]);
				bottom = Math.min (bottom, boundsScratch [1] + boundsScratch [3]);
			}
		}
        if (!clipped) {
            return false;
        }
		out [0] = left;
		out [1] = top;
		out [2] = Math.max (0, right - left);
		out [3] = Math.max (0, bottom - top);
		return true;
	}

	public void setStroke (int group, int width, int style, int cap, int join) {
		requireGroup (group);
        if (width < 0) {
            throw new IllegalArgumentException("negative line width");
        }
		lineWidth [group] = width;
		lineStyle [group] = style;
		lineCap [group] = cap;
		lineJoin [group] = join;
		flags [group] |= HAS_STROKE;
	}

	public void clearStroke (int group) {
		requireGroup (group);
		flags [group] &= ~HAS_STROKE;
	}

	public boolean effectiveStroke (int node, int [] out) {
		requireNode (node);
        if (out == null || out.length < 4) {
            throw new IllegalArgumentException("stroke output too small");
        }
		for (int current = node; current >= 0; current = parents [current]) {
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

	public void mapToRoot (int node, float x, float y, float [] out) {
		requireAttachedNode (node);
        if (out == null || out.length < 2) {
            throw new IllegalArgumentException("coordinate output too small");
        }
		rootTransform (node, affineScratch);
		out [0] = affineScratch [0] * x + affineScratch [2] * y + affineScratch [4];
		out [1] = affineScratch [1] * x + affineScratch [3] * y + affineScratch [5];
	}

	public boolean mapFromRoot (int node, float x, float y, float [] out) {
		requireAttachedNode (node);
        if (out == null || out.length < 2) {
            throw new IllegalArgumentException("coordinate output too small");
        }
		rootTransform (node, affineScratch);
		float determinant = affineScratch [0] * affineScratch [3]
				- affineScratch [2] * affineScratch [1];
		if (!Float.isFinite (determinant) || determinant == 0) {
            return false;
        }
		float px = x - affineScratch [4];
		float py = y - affineScratch [5];
		out [0] = (affineScratch [3] * px - affineScratch [2] * py) / determinant;
		out [1] = (-affineScratch [1] * px + affineScratch [0] * py) / determinant;
		return true;
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
        if (transparent) {
            flags [node] |= TEXT_TRANSPARENT;
        }
		geometryNodeCount++;
		return node;
	}

	public int drawPath (int parent, PathData data) {
		return addPath (parent, data, DRAW_PATH);
	}

	public int fillPath (int parent, PathData data) {
		return addPath (parent, data, FILL_PATH);
	}

	private int addPath (int parent, PathData data, byte kind) {
		requireGroup (parent);
		Objects.requireNonNull (data, "data");
		byte [] types = Objects.requireNonNull (data.types, "data.types");
		float [] points = Objects.requireNonNull (data.points, "data.points");
		long expectedPoints = 0;
		for (byte type : types) {
			expectedPoints += switch (type) {
				case org.eclipse.swt.SWT.PATH_MOVE_TO, org.eclipse.swt.SWT.PATH_LINE_TO -> 2;
				case org.eclipse.swt.SWT.PATH_CUBIC_TO -> 6;
				case org.eclipse.swt.SWT.PATH_QUAD_TO -> 4;
				case org.eclipse.swt.SWT.PATH_CLOSE -> 0;
				default -> throw new IllegalArgumentException ("invalid retained path type");
			};
		}
		if (expectedPoints != points.length) {
			throw new IllegalArgumentException ("retained path point count does not match types");
		}
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		for (int index = 0; index < points.length; index += 2) {
			float x = points [index];
			float y = points [index + 1];
			if (!Float.isFinite (x) || !Float.isFinite (y)) {
				throw new IllegalArgumentException ("non-finite retained path point");
			}
			minX = Math.min (minX, x);
			minY = Math.min (minY, y);
			maxX = Math.max (maxX, x);
			maxY = Math.max (maxY, y);
		}
        if (points.length == 0) {
            minX = minY = maxX = maxY = 0;
        }

		int nextTypeSize;
		int nextPointSize;
		try {
			nextTypeSize = Math.addExact (pathTypeSize, types.length);
			nextPointSize = Math.addExact (pathPointSize, points.length);
		} catch (ArithmeticException overflow) {
			throw new IllegalArgumentException ("retained path storage exceeds integer capacity", overflow);
		}
		ensurePathTypeCapacity (nextTypeSize);
		ensurePathPointCapacity (nextPointSize);
		int node = newNode (kind, parent);
		pathTypeOffsets [node] = pathTypeSize;
		pathTypeCounts [node] = types.length;
		System.arraycopy (types, 0, pathTypes, pathTypeSize, types.length);
		pathTypeSize = nextTypeSize;
		pathPointOffsets [node] = pathPointSize;
		pathPointCounts [node] = points.length;
		System.arraycopy (points, 0, pathPoints, pathPointSize, points.length);
		pathPointSize = nextPointSize;
		pathMinX [node] = minX;
		pathMinY [node] = minY;
		pathMaxX [node] = maxX;
		pathMaxY [node] = maxY;
		// A path node's otherwise unused instance-target lane is its cache slot.
		targets [node] = pathNodeCount++;
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
		int id = transformId (Objects.requireNonNull (transform, "transform"));
		int node = newNode (INSTANCE, parent);
		targets [node] = template;
		transformIds [node] = id;
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
		return replay (gc, ROOT, Affine.IDENTITY, null, false, 0);
	}

	public ReplayStats replay (GC gc, Affine transform, Rectangle clip) {
		return replay (gc, ROOT, transform, clip, false, 0);
	}

	/**
	 * Replays only commands in the requested effective viewport layer.
	 * Commands without a layer, and commands inherited into other layers, are skipped.
	 */
	public ReplayStats replayLayer (GC gc, int layer, Affine transform, Rectangle clip) {
		return replay (gc, ROOT, transform, clip, true, layer);
	}

	/**
	 * Replays viewport layers in caller-supplied back-to-front order.
	 *
	 * <p>This mirrors draw-channel/layer scheduling used by immediate-mode and GPU
	 * renderers without changing ordinary {@link #replay(GC)} semantics.</p>
	 */
	public ReplayStats replayLayers (GC gc, int [] orderedLayers, Affine transform, Rectangle clip) {
		Objects.requireNonNull (orderedLayers, "orderedLayers");
		int visited = 0, drawn = 0, culled = 0, switches = 0;
		for (int layer : orderedLayers) {
			ReplayStats stats = replayLayer (gc, layer, transform, clip);
			visited += stats.visitedNodes ();
			drawn += stats.drawnCommands ();
			culled += stats.culledCommands ();
			switches += stats.transformSwitches ();
		}
		return new ReplayStats (visited, drawn, culled, switches);
	}

	public ReplayStats replayTemplate (GC gc, int template, Affine transform, Rectangle clip) {
		if (template <= ROOT || template >= nodeCount || kinds [template] != GROUP
				|| parents [template] != DETACHED) {
			throw new IllegalArgumentException ("not a detached template");
		}
		return replay (gc, template, transform, clip, false, 0);
	}

	private ReplayStats replay (
			GC gc, int start, Affine initialTransform, Rectangle clip,
			boolean filterLayer, int requestedLayer) {
		Objects.requireNonNull (gc, "gc");
		Objects.requireNonNull (initialTransform, "initialTransform");
        if (gc.isDisposed()) {
            throw new IllegalArgumentException("disposed GC");
        }
		if (clip != null && clip.isEmpty ()) {
			return new ReplayStats (0, 0, 0, 0);
		}

		long strokeOutset = Math.max (1L, gc.getLineWidth ()) + 1;
		boolean useClips = clip != null || clipNodeCount != 0;
		boolean restoreBasic = (useClips || imageNodeCount != 0 || opacityNodeCount != 0) && !gc.getAdvanced ();
		ReplayStack scratch = borrowReplayStack ();
		int [] nodeStack = scratch.nodes;
		Affine [] transformStack = scratch.transforms;
		int [] layerStack = scratch.layers;
		boolean [] hasLayerStack = scratch.hasLayers;
		int [] opacityStack = scratch.opacities;
		boolean [] siblingStack = scratch.siblings;
		Region [] clipStack = scratch.clips;
		int stackSize = 0;

		nodeStack [stackSize] = start;
		transformStack [stackSize] = initialTransform;
		layerStack [stackSize] = 0;
		opacityStack [stackSize] = 255;
		siblingStack [stackSize] = false;
		hasLayerStack [stackSize++] = false;

		Transform saved = null;
		Transform work = null;
		Affine savedAffine = null;
		Affine appliedGraphTransform = null;
		boolean transformedGc = false;
		double [] clipVertices = scratch.clipVertices;
		double [] clipScratch = scratch.clipScratch;
		boolean callerClipped = false;
		boolean clipCaptured = false;
		Region savedClip = null;
		Region viewportClip = null;
		Region appliedClip = null;
		int visited = 0;
		int drawn = 0;
		int culled = 0;
		int transformSwitches = 0;
		int savedAlpha = -1;
		int currentAlpha = -1;
		/* Conservative stroke envelope and a reusable stack bounded by depth. */

		try {
			if (useClips || restoreBasic) {
				saved = new Transform (gc.getDevice ());
				gc.getTransform (saved);
				work = new Transform (gc.getDevice ());
				float [] elements = new float [6];
				saved.getElements (elements);
				savedAffine = new Affine (elements [0], elements [1], elements [2],
						elements [3], elements [4], elements [5]);
				callerClipped = gc.isClipped ();
				savedClip = new Region (gc.getDevice ());
				// Regions are captured/applied at identity: changing a GC transform
				// after setClipping does not move the device-anchored clip.
				gc.setTransform (null);
				transformedGc = true;
				gc.getClipping (savedClip);
				clipCaptured = true;
				if (clip != null) {
					viewportClip = intersectClip (savedClip, savedAffine,
							clip.x, clip.y, clip.width, clip.height, clipVertices, clipScratch);
				}
				if (useClips) clipStack [0] = viewportClip == null ? savedClip : viewportClip;
				gc.setTransform (saved.isIdentity () ? null : saved);
				transformedGc = false;
			}
			while (stackSize != 0) {
				int node = nodeStack [--stackSize];
				Region inheritedClip = clipStack [stackSize];
				if (node == NONE) {
					// Exit marker: native clip residency is bounded by nesting depth.
					inheritedClip.dispose ();
					continue;
				}
				Affine transform = transformStack [stackSize];
				int inheritedLayer = layerStack [stackSize];
				boolean hasInheritedLayer = hasLayerStack [stackSize];
				int alpha = opacityStack [stackSize];
				if (siblingStack [stackSize] && nextSibling [node] != NONE) {
					// Retain the continuation in place, before entering this subtree.
					nodeStack [stackSize++] = nextSibling [node];
				}
				visited++;
				if ((flags [node] & HIDDEN) != 0) continue;
				if ((flags [node] & HAS_OPACITY) != 0) {
					alpha = multiplyAlpha (alpha, opacities [node] & 0xff);
				}
				if (alpha == 0) continue;

				switch (kinds [node]) {
					case GROUP -> {
						Affine next = transform;
                        if (transformIds [node] != 0) {
                            next = transform.compose(transform(transformIds [node]));
                        }
						if (clip != null && (flags [node] & HAS_CULL_BOUNDS) != 0
								&& outsideBounds (
										next,
										cullX [node], cullY [node],
										cullWidth [node], cullHeight [node],
										clip, strokeOutset)) {
							continue;
						}
						Region nextClip = inheritedClip;
						if ((flags [node] & HAS_CLIP) != 0) {
							if (stackSize == nodeStack.length) {
								scratch.ensureCapacity (stackSize + 1);
								nodeStack = scratch.nodes;
								transformStack = scratch.transforms;
								layerStack = scratch.layers;
								hasLayerStack = scratch.hasLayers;
								opacityStack = scratch.opacities;
								siblingStack = scratch.siblings;
								clipStack = scratch.clips;
							}
							nextClip = intersectClip (inheritedClip, savedAffine.compose (next),
									clipX [node], clipY [node], clipWidth [node], clipHeight [node],
									clipVertices, clipScratch);
							nodeStack [stackSize] = NONE;
							clipStack [stackSize++] = nextClip;
						}
						if (nextClip != null && nextClip.isEmpty ()) continue;
						boolean hasNextLayer = hasInheritedLayer;
						int nextLayer = inheritedLayer;
						if ((flags [node] & HAS_LAYER) != 0) {
							hasNextLayer = true;
							nextLayer = layers [node];
						}
						int child = firstChild [node];
						if (child != NONE) {
							if (stackSize == nodeStack.length) {
								scratch.ensureCapacity (stackSize + 1);
								nodeStack = scratch.nodes;
								transformStack = scratch.transforms;
								layerStack = scratch.layers;
								hasLayerStack = scratch.hasLayers;
								opacityStack = scratch.opacities;
								siblingStack = scratch.siblings;
								clipStack = scratch.clips;
							}
							nodeStack [stackSize] = child;
							transformStack [stackSize] = next;
							layerStack [stackSize] = nextLayer;
							clipStack [stackSize] = nextClip;
							siblingStack [stackSize] = true;
							hasLayerStack [stackSize] = hasNextLayer;
							opacityStack [stackSize++] = alpha;
						}
					}
					case INSTANCE -> {
						Affine next = transform;
						if (transformIds [node] != 0) {
							next = transform.compose (transform (transformIds [node]));
						}
						if (stackSize == nodeStack.length) {
							scratch.ensureCapacity (stackSize + 1);
							nodeStack = scratch.nodes;
							transformStack = scratch.transforms;
							layerStack = scratch.layers;
							hasLayerStack = scratch.hasLayers;
							opacityStack = scratch.opacities;
							siblingStack = scratch.siblings;
							clipStack = scratch.clips;
						}
						nodeStack [stackSize] = targets [node];
						transformStack [stackSize] = next;
						layerStack [stackSize] = inheritedLayer;
						clipStack [stackSize] = inheritedClip;
						siblingStack [stackSize] = false;
						hasLayerStack [stackSize] = hasInheritedLayer;
						opacityStack [stackSize++] = alpha;
					}
					default -> {
						if (filterLayer && (!hasInheritedLayer || inheritedLayer != requestedLayer)) {
							continue;
						}
						if (clip != null && transform.isIntegralTranslation ()
								&& outsideClip (node, transform, clip, strokeOutset)) {
							culled++;
							continue;
						}
						if (inheritedClip != null && appliedClip != inheritedClip) {
							gc.setTransform (null);
							transformedGc = true;
							gc.setClipping (inheritedClip);
							gc.setTransform (saved.isIdentity () ? null : saved);
							transformedGc = false;
							appliedClip = inheritedClip;
							appliedGraphTransform = null;
							transformSwitches += 2;
						}
						if (alpha != 255 || savedAlpha != -1) {
							if (savedAlpha == -1) currentAlpha = savedAlpha = gc.getAlpha ();
							int targetAlpha = multiplyAlpha (savedAlpha, alpha);
							if (currentAlpha != targetAlpha) {
								gc.setAlpha (targetAlpha);
								currentAlpha = targetAlpha;
							}
						}
						boolean pathCommand = kinds [node] == DRAW_PATH || kinds [node] == FILL_PATH;
						boolean directPath = pathCommand && transform.isIdentity ();
						boolean fast = transform.isIntegralTranslation () && !pathCommand;
						if (directPath) {
							if (transformedGc) {
								gc.setTransform (saved.isIdentity () ? null : saved);
								transformedGc = false;
								transformSwitches++;
							}
							drawRaw (gc, node, 0, 0, scratch);
						} else if (!fast) {
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
							if (!transformedGc || appliedGraphTransform != transform) {
								Affine combined = savedAffine.compose (transform);
								// Cairo treats a singular CTM as a permanent context error.
								// A collapsed plane has no invertible paint/input mapping.
								double determinant = (double)combined.m11 * combined.m22
										- (double)combined.m21 * combined.m12;
								if (determinant == 0 || !Double.isFinite (determinant)) {
									culled++;
									continue;
								}
								work.setElements (
										combined.m11, combined.m12, combined.m21,
										combined.m22, combined.dx, combined.dy);
								gc.setTransform (work);
								transformedGc = true;
								appliedGraphTransform = transform;
								transformSwitches++;
							}
							drawRaw (gc, node, 0, 0, scratch);
						} else {
							if (transformedGc) {
								gc.setTransform (saved.isIdentity () ? null : saved);
								transformedGc = false;
								transformSwitches++;
							}
							drawRaw (gc, node, Math.round (transform.dx), Math.round (transform.dy), scratch);
						}
						drawn++;
					}
				}
			}
		} finally {
			try {
				try {
					if (savedAlpha != -1 && currentAlpha != savedAlpha && !gc.isDisposed ()) {
						gc.setAlpha (savedAlpha);
					}
				} finally {
					try {
						if (saved != null && !gc.isDisposed ()) {
							if (clipCaptured) {
								gc.setTransform (null);
								try {
									gc.setClipping (callerClipped ? savedClip : null);
								} finally {
									gc.setTransform (saved.isIdentity () ? null : saved);
								}
							} else if (transformedGc) {
								gc.setTransform (saved.isIdentity () ? null : saved);
							}
							if (restoreBasic && gc.getAdvanced ()) {
								gc.setAdvanced (false);
								gc.setClipping (callerClipped ? savedClip : null);
							}
						}
					} finally {
						try {
							releaseReplayRegions (nodeStack, clipStack, stackSize, viewportClip, savedClip);
						} finally {
							try {
								if (work != null) work.dispose ();
							} finally {
								if (saved != null) saved.dispose ();
							}
						}
					}
				}
			} finally {
				try {
					scratch.releasePaths ();
				} finally {
					Arrays.fill (scratch.transforms, null);
					Arrays.fill (scratch.clips, null);
					scratch.inUse = false;
				}
			}
		}
		return new ReplayStats (visited, drawn, culled, transformSwitches);
	}

	private static void releaseReplayRegions (int [] nodes, Region [] clips, int size,
			Region viewportClip, Region savedClip) {
		Throwable failure = null;
		for (int index = 0; index < size + 2; index++) {
			Region region = index < size ? (nodes [index] == NONE ? clips [index] : null)
					: index == size ? viewportClip : savedClip;
			try {
				if (region != null && !region.isDisposed ()) region.dispose ();
			} catch (RuntimeException | Error thrown) {
				if (failure == null) failure = thrown;
				else failure.addSuppressed (thrown);
			}
		}
		if (failure instanceof Error error) throw error;
		if (failure instanceof RuntimeException error) throw error;
	}

	/** Intersects a transformed rectangle with a device-anchored parent region. */
	private static Region intersectClip (Region parent, Affine mapping,
			double x, double y, double width, double height,
			double [] vertices, double [] scratch) {
		Region result = new Region (parent.getDevice ());
		boolean complete = false;
		try {
			if (width <= 0 || height <= 0 || parent.isEmpty ()) {
				complete = true;
				return result;
			}
			Rectangle bounds = parent.getBounds ();
			for (int i = 0; i < 4; i++) {
				double px = x + (i == 1 || i == 2 ? width : 0);
				double py = y + (i >= 2 ? height : 0);
				vertices [i * 2] = mapping.m11 * px + mapping.m21 * py + mapping.dx;
				vertices [i * 2 + 1] = mapping.m12 * px + mapping.m22 * py + mapping.dy;
			}
			int count = 4;
			for (int edge = 0; edge < 4 && count != 0; edge++) {
				double limit = switch (edge) {
					case 0 -> bounds.x;
					case 1 -> Math.min (Integer.MAX_VALUE, (double)bounds.x + bounds.width);
					case 2 -> bounds.y;
					default -> Math.min (Integer.MAX_VALUE, (double)bounds.y + bounds.height);
				};
				count = clipPolygon (vertices, count, scratch, edge, limit);
				double [] swap = vertices;
				vertices = scratch;
				scratch = swap;
			}
			if (count >= 3) {
				int [] polygon = new int [count * 2];
				for (int i = 0; i < polygon.length; i++) {
					polygon [i] = Math.toIntExact (Math.round (vertices [i]));
				}
				result.add (polygon);
				result.intersect (parent);
			}
			complete = true;
			return result;
		} finally {
			if (!complete) result.dispose ();
		}
	}

	/* Sutherland-Hodgman clipping of a convex polygon against one bounding edge. */
	private static int clipPolygon (double [] input, int count, double [] output,
			int edge, double limit) {
		int axis = edge < 2 ? 0 : 1;
		boolean minimum = (edge & 1) == 0;
		int written = 0;
		int previous = count - 1;
		for (int current = 0; current < count; current++) {
			double p = input [previous * 2 + axis], q = input [current * 2 + axis];
			boolean pInside = minimum ? p >= limit : p <= limit;
			boolean qInside = minimum ? q >= limit : q <= limit;
			if (pInside != qInside) {
				double t = (limit - p) / (q - p);
				for (int coordinate = 0; coordinate < 2; coordinate++) {
					output [written * 2 + coordinate] = coordinate == axis ? limit
							: input [previous * 2 + coordinate]
							+ t * (input [current * 2 + coordinate] - input [previous * 2 + coordinate]);
				}
				written++;
			}
			if (qInside) {
				output [written * 2] = input [current * 2];
				output [written * 2 + 1] = input [current * 2 + 1];
				written++;
			}
			previous = current;
		}
		return written;
	}

	private void drawRaw (GC gc, int node, int dx, int dy, ReplayStack scratch) {
		switch (kinds [node]) {
			case LINE -> gc.drawLine (a [node] + dx, b [node] + dy, c [node] + dx, d [node] + dy);
			case DRAW_RECT -> gc.drawRectangle (a [node] + dx, b [node] + dy, c [node], d [node]);
			case FILL_RECT -> gc.fillRectangle (a [node] + dx, b [node] + dy, c [node], d [node]);
			case IMAGE -> {
				Image image = (Image)payloads [node];
				if (image.isDisposed () || image.getDevice () != gc.getDevice ()) {
					throw new IllegalArgumentException ("image is disposed or belongs to another device");
				}
				gc.drawImage (image, Math.addExact (a [node], dx), Math.addExact (b [node], dy));
			}
			case TEXT -> gc.drawText (
					(String)payloads [node], a [node] + dx, b [node] + dy,
					(flags [node] & TEXT_TRANSPARENT) != 0);
			case DRAW_PATH -> gc.drawPath (replayPath (gc, node, scratch));
			case FILL_PATH -> gc.fillPath (replayPath (gc, node, scratch));
			default -> throw new IllegalStateException ("not a paint command");
		}
	}

	private Path replayPath (GC gc, int node, ReplayStack scratch) {
		int slot = targets [node];
		Path path = slot < scratch.paths.length ? scratch.paths [slot] : null;
		if (path != null) return path;
		// Reserve Java lanes before creating a new native Path.
		scratch.reservePath (slot);
		PathData data = new PathData ();
		int typeOffset = pathTypeOffsets [node];
		int typeCount = pathTypeCounts [node];
		data.types = Arrays.copyOfRange (pathTypes, typeOffset, typeOffset + typeCount);
		int pointOffset = pathPointOffsets [node];
		int pointCount = pathPointCounts [node];
		data.points = Arrays.copyOfRange (pathPoints, pointOffset, pointOffset + pointCount);
		path = new Path (gc.getDevice (), data);
		scratch.paths [slot] = path;
		scratch.usedPathSlots [scratch.usedPaths++] = slot;
		return path;
	}

	private boolean outsideBounds (
			Affine transform, float x, float y, float width, float height,
			Rectangle clip, long strokeOutset) {
		mapRect (transform, x, y, width, height, boundsScratch);
		/*
		 * Cull bounds describe retained geometry, not necessarily its rasterized
		 * stroke.  Expand them by a conservative transform-scaled envelope so a
		 * child stroke that crosses the logical group edge remains eligible.
		 */
		double m11 = transform.m11;
		double m12 = transform.m12;
		double m21 = transform.m21;
		double m22 = transform.m22;
		double scaleUpperBound = Math.max (
				1d, Math.sqrt (m11 * m11 + m12 * m12 + m21 * m21 + m22 * m22));
		double envelope = strokeOutset * scaleUpperBound;
		double left = boundsScratch [0] - envelope;
		double top = boundsScratch [1] - envelope;
		double right = boundsScratch [0] + boundsScratch [2] + envelope;
		double bottom = boundsScratch [1] + boundsScratch [3] + envelope;
		long clipRight = (long)clip.x + clip.width;
		long clipBottom = (long)clip.y + clip.height;
		return right <= clip.x || bottom <= clip.y
				|| left >= clipRight || top >= clipBottom;
	}

	private boolean outsideClip (
			int node, Affine transform, Rectangle clip, long strokeOutset) {
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
			case IMAGE -> {
				left = (long)a [node] + dx;
				top = (long)b [node] + dy;
				right = left + c [node];
				bottom = top + d [node];
			}
			case DRAW_RECT, FILL_RECT -> {
				left = Math.min (a [node], (long)a [node] + c [node]) + dx;
				top = Math.min (b [node], (long)b [node] + d [node]) + dy;
				right = Math.max (a [node], (long)a [node] + c [node]) + dx + 1;
				bottom = Math.max (b [node], (long)b [node] + d [node]) + dy + 1;
			}
			case DRAW_PATH, FILL_PATH -> {
				double envelope = kinds [node] == DRAW_PATH ? strokeOutset : 1d;
				double pathLeft = pathMinX [node] + dx - envelope;
				double pathTop = pathMinY [node] + dy - envelope;
				double pathRight = pathMaxX [node] + dx + envelope;
				double pathBottom = pathMaxY [node] + dy + envelope;
				long clipRight = (long)clip.x + clip.width;
				long clipBottom = (long)clip.y + clip.height;
				return pathRight <= clip.x || pathBottom <= clip.y
						|| pathLeft >= clipRight || pathTop >= clipBottom;
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
				|| left >= (long)clip.x + clip.width
				|| top >= (long)clip.y + clip.height;
	}

	private int newNode (byte kind, int parent) {
		ensureNodeCapacity (nodeCount + 1);
		int node = nodeCount++;
		kinds [node] = kind;
		firstChild [node] = lastChild [node] = NONE;
		nextSibling [node] = previousSibling [node] = NONE;
		parents [node] = parent;
		targets [node] = NONE;
        if (parent >= 0) {
            linkChild(parent, node);
        }
		return node;
	}

	private void linkChild (int parent, int child) {
		int tail = lastChild [parent];
		if (tail == NONE) {
			firstChild [parent] = lastChild [parent] = child;
			return;
		}
		// The dominant construction case appends at equal/increasing Z in O(1).
		if (comesBefore (tail, child)) {
			nextSibling [tail] = child;
			previousSibling [child] = tail;
			lastChild [parent] = child;
			return;
		}
		int cursor = firstChild [parent];
		while (cursor != NONE && comesBefore (cursor, child)) {
			cursor = nextSibling [cursor];
		}
		if (cursor == NONE) {
			nextSibling [tail] = child;
			previousSibling [child] = tail;
			lastChild [parent] = child;
			return;
		}
		int previous = previousSibling [cursor];
		nextSibling [child] = cursor;
		previousSibling [child] = previous;
		previousSibling [cursor] = child;
        if (previous == NONE) {
            firstChild [parent] = child;
        } else {
            nextSibling [previous] = child;
        }
	}

	private void unlinkChild (int parent, int child) {
		int previous = previousSibling [child];
		int next = nextSibling [child];
        if (previous == NONE) {
            firstChild [parent] = next;
        } else {
            nextSibling [previous] = next;
        }
        if (next == NONE) {
            lastChild [parent] = previous;
        } else {
            previousSibling [next] = previous;
        }
		previousSibling [child] = NONE;
		nextSibling [child] = NONE;
	}

	private boolean comesBefore (int left, int right) {
		int order = Integer.compare (zOrders [left], zOrders [right]);
		return order < 0 || (order == 0 && left < right);
	}

	private boolean wouldReach (int start, int wanted) {
		if (start == wanted) return true;
		// Detached templates can only target other detached templates; no edge
		// can point back into the root-attached graph.
		for (int parent = wanted; parent != NONE; parent = parents [parent]) {
			if (parent == ROOT) return false;
			if (parents [parent] == DETACHED) break;
		}
		if (reachSeen.length < nodeCount) {
			reachSeen = Arrays.copyOf (reachSeen, Math.max (nodeCount, reachSeen.length * 2));
		}
		int epoch = ++reachEpoch;
		if (epoch == 0) {
			Arrays.fill (reachSeen, 0);
			epoch = ++reachEpoch;
		}
		int size = 0;
		reachStack [size++] = start;
		reachSeen [start] = epoch;
		while (size != 0) {
			int node = reachStack [--size];
			if (node == wanted) return true;
			if (kinds [node] == INSTANCE) {
				int target = targets [node];
				if (reachSeen [target] != epoch) {
					if (size == reachStack.length) reachStack = Arrays.copyOf (reachStack, size * 2);
					reachSeen [target] = epoch;
					reachStack [size++] = target;
				}
			}
			for (int child = firstChild [node]; child != NONE; child = nextSibling [child]) {
				if (reachSeen [child] == epoch) continue;
				if (size == reachStack.length) reachStack = Arrays.copyOf (reachStack, size * 2);
				reachSeen [child] = epoch;
				reachStack [size++] = child;
			}
		}
		return false;
	}

	private int transformId (Affine transform) {
		return transform.isIdentity () ? 0 : storeTransform (transform);
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

	private void requireLayerOwner (int node) {
		requireNode (node);
		if (kinds [node] != GROUP && kinds [node] != INSTANCE) {
			throw new IllegalArgumentException ("node is not a group or instance");
		}
	}

	private void requireVisibleOwner (int node) {
		requireNode (node);
		if (kinds [node] != IMAGE) requireLayerOwner (node);
	}

	private void requireGroup (int node) {
		if (node < 0 || node >= nodeCount || kinds [node] != GROUP) {
			throw new IllegalArgumentException ("parent is not a group");
		}
	}

	private void requireNode (int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IllegalArgumentException("invalid paint node");
        }
	}

	private void requireAttachedNode (int node) {
		requireNode (node);
		for (int current = node; current >= 0; current = parents [current]) {
			if (parents [current] == DETACHED) {
				throw new IllegalArgumentException ("detached paint node has no unique root mapping");
			}
		}
	}

	private void rootTransform (int node, float [] out) {
		float r11 = 1, r12 = 0, r21 = 0, r22 = 1, rdx = 0, rdy = 0;
		for (int current = node; current >= 0; current = parents [current]) {
			if (parents [current] == DETACHED) {
				throw new IllegalArgumentException ("detached paint node has no unique root mapping");
			}
			int transformId = (kinds [current] == GROUP || kinds [current] == INSTANCE)
					? transformIds [current] : 0;
            if (transformId == 0) {
                continue;
            }
			int offset = transformId * 6;
			float l11 = transforms [offset];
			float l12 = transforms [offset + 1];
			float l21 = transforms [offset + 2];
			float l22 = transforms [offset + 3];
			float ldx = transforms [offset + 4];
			float ldy = transforms [offset + 5];

			float next11 = l11 * r11 + l21 * r12;
			float next12 = l12 * r11 + l22 * r12;
			float next21 = l11 * r21 + l21 * r22;
			float next22 = l12 * r21 + l22 * r22;
			float nextDx = l11 * rdx + l21 * rdy + ldx;
			float nextDy = l12 * rdx + l22 * rdy + ldy;
			r11 = next11;
			r12 = next12;
			r21 = next21;
			r22 = next22;
			rdx = nextDx;
			rdy = nextDy;
		}
		out [0] = r11;
		out [1] = r12;
		out [2] = r21;
		out [3] = r22;
		out [4] = rdx;
		out [5] = rdy;
	}

	private void mapRectToRoot (
			int node, float x, float y, float width, float height, float [] out) {
		rootTransform (node, affineScratch);
		mapRect (
				affineScratch [0], affineScratch [1], affineScratch [2],
				affineScratch [3], affineScratch [4], affineScratch [5],
				x, y, width, height, out);
	}

	private static void mapRect (
			Affine transform, float x, float y, float width, float height, float [] out) {
		mapRect (
				transform.m11, transform.m12, transform.m21,
				transform.m22, transform.dx, transform.dy,
				x, y, width, height, out);
	}

	private static void mapRect (
			float m11, float m12, float m21, float m22, float dx, float dy,
			float x, float y, float width, float height, float [] out) {
		float x1 = m11 * x + m21 * y + dx;
		float y1 = m12 * x + m22 * y + dy;
		float x2 = m11 * (x + width) + m21 * y + dx;
		float y2 = m12 * (x + width) + m22 * y + dy;
		float x3 = m11 * x + m21 * (y + height) + dx;
		float y3 = m12 * x + m22 * (y + height) + dy;
		float x4 = m11 * (x + width) + m21 * (y + height) + dx;
		float y4 = m12 * (x + width) + m22 * (y + height) + dy;
		float left = Math.min (Math.min (x1, x2), Math.min (x3, x4));
		float top = Math.min (Math.min (y1, y2), Math.min (y3, y4));
		float right = Math.max (Math.max (x1, x2), Math.max (x3, x4));
		float bottom = Math.max (Math.max (y1, y2), Math.max (y3, y4));
		out [0] = left;
		out [1] = top;
		out [2] = right - left;
		out [3] = bottom - top;
	}

	private void ensureNodeCapacity (int required) {
        if (required <= kinds.length) {
            return;
        }
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
		zOrders = Arrays.copyOf (zOrders, next);
		opacities = Arrays.copyOf (opacities, next);
		clipX = Arrays.copyOf (clipX, next);
		clipY = Arrays.copyOf (clipY, next);
		clipWidth = Arrays.copyOf (clipWidth, next);
		clipHeight = Arrays.copyOf (clipHeight, next);
		cullX = Arrays.copyOf (cullX, next);
		cullY = Arrays.copyOf (cullY, next);
		cullWidth = Arrays.copyOf (cullWidth, next);
		cullHeight = Arrays.copyOf (cullHeight, next);
		hitX = Arrays.copyOf (hitX, next);
		hitY = Arrays.copyOf (hitY, next);
		hitWidth = Arrays.copyOf (hitWidth, next);
		hitHeight = Arrays.copyOf (hitHeight, next);
		lineWidth = Arrays.copyOf (lineWidth, next);
		lineStyle = Arrays.copyOf (lineStyle, next);
		lineCap = Arrays.copyOf (lineCap, next);
		lineJoin = Arrays.copyOf (lineJoin, next);
		pathTypeOffsets = Arrays.copyOf (pathTypeOffsets, next);
		pathTypeCounts = Arrays.copyOf (pathTypeCounts, next);
		pathPointOffsets = Arrays.copyOf (pathPointOffsets, next);
		pathPointCounts = Arrays.copyOf (pathPointCounts, next);
		pathMinX = Arrays.copyOf (pathMinX, next);
		pathMinY = Arrays.copyOf (pathMinY, next);
		pathMaxX = Arrays.copyOf (pathMaxX, next);
		pathMaxY = Arrays.copyOf (pathMaxY, next);
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

	private void ensurePathTypeCapacity (int required) {
        if (required <= pathTypes.length) {
            return;
        }
		pathTypes = Arrays.copyOf (pathTypes, Math.max (required, pathTypes.length * 2));
	}

	private void ensurePathPointCapacity (int required) {
        if (required <= pathPoints.length) {
            return;
        }
		pathPoints = Arrays.copyOf (pathPoints, Math.max (required, pathPoints.length * 2));
	}

	private void ensureTransformCapacity (int required) {
		int current = transforms.length / 6;
        if (required <= current) {
            return;
        }
		transforms = Arrays.copyOf (transforms, Math.max (required, current * 2) * 6);
	}
}
