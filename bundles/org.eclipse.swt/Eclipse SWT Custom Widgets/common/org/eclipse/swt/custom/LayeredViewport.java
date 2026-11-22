/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.custom;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.LayerRectIndex;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.NativeLayers;
import org.eclipse.swt.internal.ViewportDamageRegion;
import org.eclipse.swt.internal.ViewportGcProxy;
import org.eclipse.swt.internal.ViewportRuntime;
import org.eclipse.swt.internal.render.ImageDataArgb;
import org.eclipse.swt.internal.render.NativeDirtyRegions;
import org.eclipse.swt.internal.render.LayerArgbCompositor;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * An opt-in, painted Z-stack over a long-coordinate, zoomable XY viewport.
 *
 * <p>Layer order is bottom-to-top. Unlike native Control.moveAbove/Below,
 * these layers are logical objects, not child controls. Painting is clipped
 * to the visible XY window; changing a layer only invalidates its projected
 * bounds. Structural edits invalidate a shared order snapshot, rebuilt only
 * when painting, picking or reading the layer order requires it.</p>
 *
 * <p>The viewport keeps a backing store: the composite of every layer, in
 * widget pixels, retained between paints, with the region whose pixels no
 * longer match the layers. A paint recomposes only the damaged pixels that
 * are dirty and copies the rest from the store, so an expose (a window
 * uncovered) repaints no layer. Changing a layer, {@link #redraw()} and a
 * zoom mark their pixels dirty; panning by a whole number of pixels moves
 * the retained pixels and recomposes only the band that comes into view.</p>
 *
 * <p>Opacity is Photoshop's layer opacity: an image layer is drawn once with
 * its alpha, and a painter layer below 255 is painted onto a copy of the
 * composite beneath it which is then blended once, so the layer's own shapes
 * do not darken each other where they overlap. Image layers also support the
 * separable blend modes below. Image layers can use alpha/luminance raster
 * masks; vector-painter, group and native-control masking remain outside this API.</p>
 *
 * <p>Control layers bring real widgets into the layered world: a native
 * child control (a Text, a Button, any Nebula widget) placed at world bounds,
 * moved and resized with every pan and zoom and shown only while its layer is
 * visible and inside the view. The window system composites a child control
 * above every painted layer; among control layers the Z order is the layers'
 * order.</p>
 *
 * <p>The widget never disposes application-owned Images or a PaintEvent GC.
 * All API access follows SWT widget-thread and disposal rules.</p>
 */
public final class LayeredViewport extends Canvas {

    /** An exact long-coordinate point in the scene. */
    public record WorldPoint(long x, long y) { }

    /** Half-open scene rectangle, whose far edge must fit in a signed long. */
    public record WorldBounds(long x, long y, long width, long height) { }

    /**
     * Immutable coordinate projection captured from a layer. Fixed axes use widget pixels;
     * the other axes retain the viewport zoom and effective pinned or sticky origin.
     * Capturing once before a painter changes the scene preserves its original transform.
     */
    public record Projection(long originX, long originY, double scaleX, double scaleY) {
        public Projection {
            if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY)
                    || scaleX < MIN_ZOOM || scaleX > MAX_ZOOM || scaleY < MIN_ZOOM || scaleY > MAX_ZOOM) {
                throw new IllegalArgumentException("scales must be within the viewport zoom range");
            }
        }

        public double projectX(long x) { return device(x, originX, scaleX); }
        public double projectY(long y) { return device(y, originY, scaleY); }

        public Point toScreen(long x, long y) {
            return new Point(saturatedPixel(projectX(x)), saturatedPixel(projectY(y)));
        }

        public WorldPoint toWorld(int x, int y) {
            return new WorldPoint(saturatedAdd(originX, floorLong(x / scaleX)),
                    saturatedAdd(originY, floorLong(y / scaleY)));
        }
    }

    /**
     * Snapshot for painting one visible layer. The clip uses widget pixels.
     * For mixed fixed axes, capture {@code layer().getProjection()} at painter entry.
     * The original five record components retain their uniform-zoom contract.
     */
    public record PaintContext(Layer layer, Rectangle clip, long originX,
            long originY, double zoom) {
        /** Widget pixel of world {@code x} under the layer's current projection, fixed and pinned axes included (#498). */
        public int projectX(long x) { return saturatedPixel(layer.getProjection().projectX(x)); }
        /** Widget pixel of world {@code y} under the layer's current projection, fixed and pinned axes included (#498). */
        public int projectY(long y) { return saturatedPixel(layer.getProjection().projectY(y)); }
    }

    /**
     * Paint in widget pixels into the GC given (the viewport's backing store, or
     * a scratch copy of it for a translucent layer); do not dispose it or retain
     * it past the call. Only the context's clip needs painting.
     */

    @FunctionalInterface
    public interface LayerPainter {
        void paint(GC gc, PaintContext context);
    }

    /** Optional fine-grained hit test after rectangular layer bounds. */
    @FunctionalInterface
    public interface LayerHitTester {
        boolean contains(Layer layer, long worldX, long worldY);
    }

    /**
     * Immutable pointer notification for an opt-in painted interaction surface.
     * The original SWT event continues to the Canvas listeners unchanged.
     * Capture is logical to this Canvas and ends on focus loss or mouse exit;
     * it does not move OS input ownership away from native child controls.
     */
    public record LayerPointerEvent(Layer layer, int type, int x, int y,
            long worldX, long worldY, int button, int count, int stateMask,
            int time, boolean captured) { }

    /** An optional handler for pointer events on a painted layer. */
    @FunctionalInterface
    public interface LayerPointerListener {
        void handle(LayerPointerEvent event);
    }


    /** A paint callback for a child of a composited layer group. */
    @FunctionalInterface
    public interface GroupPainter {
        void paint(GC gc, GroupPaintContext context);
    }

    /** Hit testing for a group child; coordinates are relative to its group. */
    @FunctionalInterface
    public interface GroupHitTester {
        boolean contains(LayerGroup.Child child, long localX, long localY);
    }

    /** Immutable group-local to widget projection, including independently fixed axes. */
    public record GroupProjection(double groupPixelX, double groupPixelY, double scaleX, double scaleY) {
        public GroupProjection {
            if (!Double.isFinite(groupPixelX) || !Double.isFinite(groupPixelY)
                    || !Double.isFinite(scaleX) || !Double.isFinite(scaleY)
                    || scaleX < MIN_ZOOM || scaleX > MAX_ZOOM || scaleY < MIN_ZOOM || scaleY > MAX_ZOOM) {
                throw new IllegalArgumentException("finite offsets and scales within the viewport zoom range required");
            }
        }

        public double projectX(long localX) { return groupPixelX + localX * scaleX; }
        public double projectY(long localY) { return groupPixelY + localY * scaleY; }

        public Point toScreen(long localX, long localY) {
            return new Point(saturatedPixel(projectX(localX)), saturatedPixel(projectY(localY)));
        }

        public WorldPoint toLocal(int deviceX, int deviceY) {
            return new WorldPoint(floorLong((deviceX - groupPixelX) / scaleX),
                    floorLong((deviceY - groupPixelY) / scaleY));
        }
    }

    /**
     * Painting coordinates for a group child. Children use group-local
     * world units; a pinned or sticky root is projected consistently with
     * the parent layer. The callback may only use the borrowed GC in scope.
     * The original five components and {@link #toScreen(long, long)} keep
     * their uniform-zoom contract. For independently fixed axes, capture
     * {@code child().getProjection()} during the callback.
     */
    public record GroupPaintContext(LayerGroup.Child child, Rectangle clip,
            double groupPixelX, double groupPixelY, double zoom) {
        public Point toScreen(long localX, long localY) {
            return new Point(saturatedPixel(groupPixelX + localX * zoom),
                    saturatedPixel(groupPixelY + localY * zoom));
        }
    }

    /**
     * A single, reorderable Z item containing multiple painters and images.
     *
     * <p>The whole group is one ordinary painted {@link Layer}; setting the
     * root's opacity uses the existing isolated painter composition. Thus
     * overlapping child shapes receive group opacity once, and moving the
     * root moves the children without changing their local bounds. Children
     * are logical handles, never SWT native Controls.</p>
     *
     * <p>For predictable composition the first implementation is one level
     * deep: each child paints normally inside the group's clipping bounds.
     * Per-child opacity, nested blend groups and arbitrary masks require
     * separate intermediate surfaces and are not advertised here.</p>
     */
    public final class LayerGroup {
        private Layer root;
        private Child[] children = new Child[8];
        private Child[] order = new Child[0];
        private int size;
        private long nextChildId = 1;
        private boolean orderDirty;
        private boolean spatialDirty = true;
        private final LayerRectIndex spatialIndex = new LayerRectIndex();
        private final LayerRectIndex.Bounds spatialSource = new LayerRectIndex.Bounds() {
            @Override public int size() { return order.length; }
            @Override public boolean active(int at) {
                Child c = order[at];
                return !c.removed && c.visible;
            }
            @Override public long left(int at) { return order[at].x; }
            @Override public long top(int at) { return order[at].y; }
            @Override public long right(int at) { return order[at].right; }
            @Override public long bottom(int at) { return order[at].bottom; }
        };
        private Child[] paintCandidates = new Child[16];
        private Child[] hitCandidates = new Child[16];
        private int activeGroupPaints;
        private int activeChildHits;
        private GroupProjection activeProjection;

        /** Stable group-local handle; bounds are relative to the root. */
        public final class Child {
            private final long id;
            private String name;
            private long x, y, right, bottom;
            private boolean visible = true;
            private boolean locked;
            private boolean removed;
            private Image image;
            private GroupPainter painter;
            private GroupHitTester hitTester;

            private Child(long id, String name, long x, long y, long width,
                    long height, Image image, GroupPainter painter) {
                this.id = id;
                this.name = name;
                this.x = x;
                this.y = y;
                right = x + width;
                bottom = y + height;
                this.image = image;
                this.painter = painter;
            }

            private void checked() {
                LayerGroup.this.checked();
                if (removed) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            }

            public long getId() {
                checked();
                return id;
            }

            public String getName() {
                checked();
                return name;
            }

            public void setName(String value) {
                checked();
                if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
                name = value;
            }

            /** Bounds relative to the layer group's origin. */
            public WorldBounds getLocalBounds() {
                checked();
                return new WorldBounds(x, y, right - x, bottom - y);
            }

            /** Captures the group-local transform; a painter receives its active frame's snapshot. */
            public GroupProjection getProjection() {
                checked();
                return LayerGroup.this.getProjection();
            }

            public void setLocalBounds(long localX, long localY, long width, long height) {
                checked();
                validateBounds(localX, localY, width, height);
                if (x == localX && y == localY
                        && right == localX + width && bottom == localY + height) return;
                root.redraw();
                x = localX;
                y = localY;
                right = localX + width;
                bottom = localY + height;
                spatialDirty = true;
            }

            public boolean isVisible() {
                checked();
                return visible;
            }

            public void setVisible(boolean value) {
                checked();
                if (visible == value) return;
                visible = value;
                spatialDirty = true;
                root.redraw();
            }

            public boolean isLocked() {
                checked();
                return locked;
            }

            /** Locking excludes picking but does not change painting. */
            public void setLocked(boolean value) {
                checked();
                locked = value;
            }

            public void setImage(Image value) {
                checked();
                if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
                image = value;
                painter = null;
                root.redraw();
            }

            public void setPainter(GroupPainter value) {
                checked();
                painter = value;
                image = null;
                root.redraw();
            }

            public void setHitTester(GroupHitTester value) {
                checked();
                hitTester = value;
            }

            public int getZIndex() {
                checked();
                return indexOf(this);
            }

            /** Group-local Z order; the entire group remains one parent Z item. */
            public void moveTo(int z) {
                checked();
                if (z < 0 || z >= size) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
                int previous = indexOf(this);
                if (previous == z) return;
                if (previous < z) {
                    System.arraycopy(children, previous + 1, children, previous, z - previous);
                } else {
                    System.arraycopy(children, z, children, z + 1, previous - z);
                }
                children[z] = this;
                orderDirty = true;
                root.redraw();
            }

            /** Does not dispose caller-owned images. */
            public void remove() {
                checked();
                int at = indexOf(this);
                System.arraycopy(children, at + 1, children, at, size - at - 1);
                children[--size] = null;
                removed = true;
                image = null;
                painter = null;
                hitTester = null;
                orderDirty = true;
                root.redraw();
            }
        }

        private void checked() {
            LayeredViewport.this.checkWidget();
            root.checked();
        }

        /** The group's parent Z-stack item; use it for opacity, clip and pan pinning. */
        public Layer getLayer() {
            checked();
            return root;
        }

        /**
         * Returns an immutable group-local projection. During child painting this
         * is the active frame's captured transform, even if a callback changes the
         * root. Otherwise it captures the current root transform.
         */
        public GroupProjection getProjection() {
            checked();
            return activeProjection != null ? activeProjection : captureProjection();
        }

        private GroupProjection captureProjection() {
            double scaleX = layerScaleX(root), scaleY = layerScaleY(root);
            return new GroupProjection(device(root.x, layerOriginX(root), scaleX),
                    device(root.y, layerOriginY(root), scaleY), scaleX, scaleY);
        }

        public Child addPaintChild(String name, long localX, long localY,
                long width, long height, GroupPainter painter) {
            checked();
            if (name == null || painter == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            return appendChild(name, localX, localY, width, height, null, painter);
        }

        public Child addImageChild(String name, Image image, long localX, long localY) {
            checked();
            if (name == null || image == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            if (image.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            Rectangle bounds = image.getBounds();
            return appendChild(name, localX, localY, bounds.width, bounds.height, image, null);
        }

        private Child appendChild(String name, long localX, long localY,
                long width, long height, Image image, GroupPainter painter) {
            validateBounds(localX, localY, width, height);
            if (nextChildId == Long.MAX_VALUE) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (size == children.length) children = Arrays.copyOf(children, size * 2);
            Child child = new Child(nextChildId++, name, localX, localY, width, height, image, painter);
            children[size++] = child;
            orderDirty = true;
            root.redraw();
            return child;
        }

        /** Bottom-to-top immutable-by-caller snapshot of child handles. */
        public Child[] getChildren() {
            checked();
            return orderSnapshot().clone();
        }

        private Child[] orderSnapshot() {
            if (orderDirty) {
                order = Arrays.copyOf(children, size);
                orderDirty = false;
                spatialDirty = true;
            }
            return order;
        }

        private int indexOf(Child child) {
            for (int i = 0; i < size; i++) if (children[i] == child) return i;
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            return -1;
        }

        private boolean containsAny(long worldX, long worldY) {
            return findChild(worldX - root.x, worldY - root.y) != null;
        }

        /**
         * Topmost visible unlocked child at a widget pixel, or null.
         * The parent layer's own bounds, clip, visibility and lock apply.
         */
        public Child hitTestChild(int deviceX, int deviceY) {
            return queryChildHit(deviceX, deviceY, null);
        }

        /**
         * Exclusion/acceptance search for editor drop targets and selection handles.
         * The predicate is invoked front-to-back only for hittable children and may
         * mutate the scene; a rejected child never changes its Z order or capture.
         * Unlike a native SWT Control, a group child is only a logical object.
         */
        public Child hitTestChild(int deviceX, int deviceY, Predicate<? super Child> accept) {
            if (accept == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            return queryChildHit(deviceX, deviceY, accept);
        }

        private Child queryChildHit(int deviceX, int deviceY, Predicate<? super Child> accept) {
            checked();
            if (!getClientArea().contains(deviceX, deviceY)) return null;
            long x = layerX(root, deviceX), y = layerY(root, deviceY);
            if (!containsHit(root, x, y)) return null;
            return findChild(x - root.x, y - root.y, accept, deviceX, deviceY);
        }

        private void ensureChildIndex() {
            orderSnapshot();
            if (spatialDirty) {
                spatialIndex.rebuild(spatialSource);
                spatialDirty = false;
            }
        }

        private Child[] growPaintCandidates(int selected) {
            if (paintCandidates.length < selected) {
                paintCandidates = Arrays.copyOf(paintCandidates, selected);
            }
            return paintCandidates;
        }

        private Child[] growHitCandidates(int selected) {
            if (hitCandidates.length < selected) {
                hitCandidates = Arrays.copyOf(hitCandidates, selected);
            }
            return hitCandidates;
        }

        private Child findChild(long x, long y) {
            return findChild(x, y, null, 0, 0);
        }

        private Child findChild(long x, long y, Predicate<? super Child> accept,
                int deviceX, int deviceY) {
            Child[] frame = orderSnapshot();
            int selected;
            Child[] candidates;
            if (frame.length <= 32) {
                selected = frame.length;
                candidates = activeChildHits == 0 ? growHitCandidates(selected) : new Child[selected];
                System.arraycopy(frame, 0, candidates, 0, selected);
            } else {
                ensureChildIndex();
                int found = spatialIndex.query(x, y, saturatedAdd(x, 1), saturatedAdd(y, 1));
                selected = found;
                candidates = activeChildHits == 0 ? growHitCandidates(selected) : new Child[selected];
                for (int k = 0; k < selected; k++) candidates[k] = frame[spatialIndex.resultAt(k)];
            }
            activeChildHits++;
            try {
                for (int k = selected - 1; k >= 0; k--) {
                    if (isDisposed()) return null;
                    Child c = candidates[k];
                    if (!candidate(c, x, y)) continue;
                    GroupHitTester tester = c.hitTester;
                    boolean accepted = tester == null || tester.contains(c, x, y);
                    if (isDisposed()) return null;
                    if (accepted && candidate(c, x, y)) {
                        if (accept == null) return c;
                        boolean included = accept.test(c);
                        if (isDisposed()) return null;
                        // The callback can move its parent, remove the child or
                        // change a pinned axis. Recheck in current device space.
                        if (included) {
                            long currentX = layerX(root, deviceX), currentY = layerY(root, deviceY);
                            if (containsHit(root, currentX, currentY)
                                    && candidate(c, currentX - root.x, currentY - root.y)) return c;
                        }
                    }
                }
                return null;
            } finally {
                Arrays.fill(candidates, 0, selected, null);
                activeChildHits--;
            }
        }

        private boolean candidate(Child c, long x, long y) {
            return !c.removed && c.visible && !c.locked
                    && x >= c.x && x < c.right && y >= c.y && y < c.bottom;
        }

        private void paint(GC gc, PaintContext context) {
            Child[] frame = orderSnapshot();
            GroupProjection projection = captureProjection();
            double baseX = projection.groupPixelX(), baseY = projection.groupPixelY();
            double scaleX = projection.scaleX(), scaleY = projection.scaleY();
            Rectangle damage = context.clip();
            int selected;
            Child[] candidates;
            if (frame.length <= 32) {
                selected = frame.length;
                candidates = activeGroupPaints == 0 ? growPaintCandidates(selected) : new Child[selected];
                System.arraycopy(frame, 0, candidates, 0, selected);
            } else {
                // The existing primitive spatial index is reused in group-local
                // coordinates. The padding keeps fractional zoom conservative.
                ensureChildIndex();
                long x0 = saturatedAdd(floorLong((damage.x - baseX) / scaleX), -1);
                long y0 = saturatedAdd(floorLong((damage.y - baseY) / scaleY), -1);
                long x1 = saturatedAdd(ceilLong(((double) damage.x + damage.width - baseX) / scaleX), 1);
                long y1 = saturatedAdd(ceilLong(((double) damage.y + damage.height - baseY) / scaleY), 1);
                selected = spatialIndex.query(x0, y0, x1, y1);
                candidates = activeGroupPaints == 0 ? growPaintCandidates(selected) : new Child[selected];
                for (int i = 0; i < selected; i++) candidates[i] = frame[spatialIndex.resultAt(i)];
            }
            GroupProjection previousProjection = activeProjection;
            activeProjection = projection;
            activeGroupPaints++;
            try {
                for (int i = 0; i < selected; i++) {
                    if (isDisposed() || gc.isDisposed()) return;
                    Child child = candidates[i];
                    if (child.removed || !child.visible) continue;
                    Rectangle tile = clipChild(child, projection, damage);
                    if (tile == null) continue;
                    try (ViewportGcProxy state = ViewportGcProxy.wrap(gc)) {
                        state.clip(tile);
                        if (child.image != null) {
                            if (!child.image.isDisposed()) {
                                double left = projection.projectX(child.x);
                                double top = projection.projectY(child.y);
                                double right = projection.projectX(child.right);
                                double bottom = projection.projectY(child.bottom);
                                paintImage(gc, child.image, left, top, right, bottom, tile);
                            }
                        } else if (child.painter != null) {
                            child.painter.paint(gc, new GroupPaintContext(
                                    child, tile, baseX, baseY, context.zoom()));
                        }
                    }
                }
            } finally {
                Arrays.fill(candidates, 0, selected, null);
                activeGroupPaints--;
                activeProjection = previousProjection;
            }
        }

        private void releaseChildren() {
            for (int i = 0; i < size; i++) {
                Child child = children[i];
                child.removed = true;
                child.image = null;
                child.painter = null;
                child.hitTester = null;
                children[i] = null;
            }
            size = 0;
            order = new Child[0];
            spatialIndex.clear();
            if (activeGroupPaints == 0) Arrays.fill(paintCandidates, null);
            if (activeChildHits == 0) Arrays.fill(hitCandidates, null);
            paintCandidates = new Child[0];
            hitCandidates = new Child[0];
            spatialDirty = true;
            orderDirty = false;
        }

        private Rectangle clipChild(Child child, GroupProjection projection, Rectangle damage) {
            // Child bounds are group-local. Root clipping is already imposed
            // by the parent compositor; clip the child again, in device pixels.
            double left = projection.projectX(child.x);
            double top = projection.projectY(child.y);
            double right = projection.projectX(child.right);
            double bottom = projection.projectY(child.bottom);
            int x0 = (int) Math.max((double) damage.x, Math.floor(left));
            int y0 = (int) Math.max((double) damage.y, Math.floor(top));
            int x1 = (int) Math.min((double) damage.x + damage.width, Math.ceil(right));
            int y1 = (int) Math.min((double) damage.y + damage.height, Math.ceil(bottom));
            return x1 > x0 && y1 > y0 ? new Rectangle(x0, y0, x1 - x0, y1 - y0) : null;
        }
    }

    /**
     * Creates one painted Z item with its own ordered logical children.
     * Its opacity is isolated by the existing parent compositor, and its
     * local children move when the root world bounds are repositioned.
     */
    public LayerGroup addLayerGroup(String name, long x, long y, long width, long height) {
        checkWidget();
        if (name == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        LayerGroup group = new LayerGroup();
        group.root = append(name, x, y, width, height, null, group::paint);
        group.root.childGroup = group;
        group.root.setHitTester((layer, worldX, worldY) -> group.containsAny(worldX, worldY));
        return group;
    }

    /**
     * Stable logical handle. Removing the layer invalidates this handle,
     * but it does not dispose an Image supplied by its application.
     */
    public final class Layer {
        private final long id;
        private int zIndex = -1;
        private String name;
        private long x, y, right, bottom;
        private int opacity = 255;
        private boolean visible = true;
        private boolean locked;
        private boolean removed;
        /* a control layer's native child, placed at the layer's bounds; null for painted layers */
        private Control control;
        /* native: asked to be composited by the window system; nativeLayer is its native handle (0 while painted) */
        private boolean nativeRequested;
        private long nativeLayer;
        private int nativePixelWidth, nativePixelHeight;
        private boolean nativePreparing;
        /* the blend mode (BLEND_*) and, for a blended image layer, its pixels read once (null until needed) */
        private int blendMode = BLEND_NORMAL;
        private ImageData blendSource;
        /* Borrowed raster coverage, sampled in the layer's projected bounds. Re-read on redraw. */
        private Image mask;
        private ImageData maskSource;
        private int maskMode = MASK_ALPHA;
        /* retained: this layer's composite (it and everything below it) kept in its own image, with the pixels of
           that image that no longer match the layers */
        private boolean retained;
        private Image composite;
        private ViewportDamageRegion stale;
        private Image image;
        private LayerPainter painter;
        private LayerHitTester hitTester;
        private LayerPointerListener pointerListener;
        private LayerGroup childGroup;
        private WorldBounds worldClip;
        /* SWT.HORIZONTAL and/or SWT.VERTICAL: the axes on which the layer is measured from the view's origin */
        private int pinned;
        /* fixed axes ignore both pan and zoom; pinned axes ignore pan but retain zoom */
        private int fixed;
        /* the axis (SWT.HORIZONTAL or SWT.VERTICAL) the layer sticks on, or 0; its inset from the view's leading
           edge and the world coordinate its trailing edge does not pass */
        private int sticky;
        private long stickyInset, stickyLimit;

        private Layer(long id, String name, long x, long y,
                long width, long height, Image image, LayerPainter painter) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.right = x + width;
            this.bottom = y + height;
            this.image = image;
            this.painter = painter;
        }

        private void checked() {
            LayeredViewport.this.checkWidget();
            if (removed) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }

        public long getId() {
            checked();
            return id;
        }

        public String getName() {
            checked();
            return name;
        }

        public void setName(String value) {
            checked();
            if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            name = value;
        }

        public WorldBounds getWorldBounds() {
            checked();
            return new WorldBounds(x, y, right - x, bottom - y);
        }

        /** Optional additional rectangular world-space paint/hit clip. */
        public WorldBounds getWorldClip() {
            checked();
            return worldClip;
        }

        public void setWorldClip(WorldBounds value) {
            checked();
            if (value != null) validateBounds(value.x(), value.y(), value.width(), value.height());
            invalidate(this);
            worldClip = value;
            spatialDirty = true;
            invalidate(this);
            syncNative();
        }

        public void setWorldBounds(long newX, long newY, long width, long height) {
            checked();
            validateBounds(newX, newY, width, height);
            if (newX == x && newY == y && right == newX + width
                    && bottom == newY + height) return;
            invalidate(this);
            x = newX;
            y = newY;
            right = newX + width;
            bottom = newY + height;
            spatialDirty = true;
            invalidate(this);
            placeControl(this);
            syncNative();
        }

        /** The axes this layer is pinned on: {@link SWT#NONE}, {@link SWT#HORIZONTAL}, {@link SWT#VERTICAL} or both. */
        public int getPinned() {
            checked();
            return pinned;
        }

        /**
         * Pins the layer on {@link SWT#HORIZONTAL}, {@link SWT#VERTICAL}, both, or neither ({@link SWT#NONE}): on a
         * pinned axis its world coordinates are measured from the view's origin, so a pan along that axis leaves it
         * where it is while the other layers move under and over it. A tree's or a table's header pinned vertically
         * stays on top while the rows scroll beneath it, and still follows a horizontal pan; frozen columns or a row
         * header are pinned horizontally; a corner cell, a legend, a minimap or a HUD on both. Zoom still applies.
         * Its bounds, clip, painter context, hit tester and pointer events all use these view-relative coordinates.
         */
        public void setPinned(int axes) {
            checked();
            int value = axes & (SWT.HORIZONTAL | SWT.VERTICAL);
            if (axes != value) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (pinned == value) return;
            invalidate(this);
            pinned = value;
            spatialDirty = true;
            invalidate(this);
            placeControl(this);
            syncNative();
        }

        /** Captures the current per-axis transform without retaining mutable viewport state. */
        public Projection getProjection() {
            checked();
            return new Projection(layerOriginX(this), layerOriginY(this), layerScaleX(this), layerScaleY(this));
        }

        /** Whether both axes use widget pixels, independent of pan and zoom. */
        public boolean isFixed() {
            checked();
            return fixed == (SWT.HORIZONTAL | SWT.VERTICAL);
        }

        /** Fixes both axes in widget pixels, or restores their pinned/sticky/world projection. */
        public void setFixed(boolean value) {
            checked();
            setFixedAxes(value ? SWT.HORIZONTAL | SWT.VERTICAL : SWT.NONE);
        }

        public boolean isFixedX() { checked(); return (fixed & SWT.HORIZONTAL) != 0; }
        public boolean isFixedY() { checked(); return (fixed & SWT.VERTICAL) != 0; }

        /** Fixes the horizontal axis in widget pixels while retaining the vertical projection. */
        public void setFixedX(boolean value) {
            checked();
            setFixedAxes(value ? fixed | SWT.HORIZONTAL : fixed & ~SWT.HORIZONTAL);
        }

        /** Fixes the vertical axis in widget pixels while retaining the horizontal projection. */
        public void setFixedY(boolean value) {
            checked();
            setFixedAxes(value ? fixed | SWT.VERTICAL : fixed & ~SWT.VERTICAL);
        }

        private void setFixedAxes(int axes) {
            if (fixed == axes) return;
            invalidate(this);
            fixed = axes;
            spatialDirty = true;
            invalidate(this);
            placeControl(this);
            syncNative();
        }

        /** The axis this layer is sticky on ({@link SWT#HORIZONTAL} or {@link SWT#VERTICAL}), or {@link SWT#NONE}. */
        public int getSticky() {
            checked();
            return sticky;
        }

        /**
         * Makes the layer sticky on one axis, like CSS {@code position: sticky}: it moves with the world until its
         * leading edge comes within {@code inset} world units of the view's leading edge, stays there while the view
         * pans on, and moves with the world again once its trailing edge reaches {@code limit}, the end of its
         * group. A tree's or a list's group header sticks under the column header while its group scrolls by and is
         * then pushed away by the next group's header. {@link SWT#NONE} ends it. Like a pinned layer, its bounds,
         * clip, painter context, hit tester and pointer events keep its own world coordinates; a pinned axis wins.
         */
        public void setSticky(int axis, long inset, long limit) {
            checked();
            if (axis != SWT.NONE && axis != SWT.HORIZONTAL && axis != SWT.VERTICAL) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (sticky == axis && stickyInset == inset && stickyLimit == limit) return;
            invalidate(this);
            sticky = axis;
            stickyInset = inset;
            stickyLimit = limit;
            spatialDirty = true;
            invalidate(this);
            placeControl(this);
            syncNative();
        }

        public int getOpacity() {
            checked();
            return opacity;
        }

        /** Normal source-over alpha, 0 to 255. */
        public void setOpacity(int value) {
            checked();
            if (value < 0 || value > 255) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (opacity == value) return;
            invalidate(this);
            opacity = value;
            spatialDirty = true;
            if (opacity == 0 && capturedLayer == this) clearPointerCapture();
            invalidate(this);
            placeControl(this);
            syncNative();
        }

        public boolean isVisible() {
            checked();
            return visible;
        }

        public void setVisible(boolean value) {
            checked();
            if (visible == value) return;
            invalidate(this);
            visible = value;
            spatialDirty = true;
            if (!visible && capturedLayer == this) clearPointerCapture();
            invalidate(this);
            placeControl(this);
            syncNative();
        }

        /**
         * Whether the composite of this layer and every layer below it is kept in an image of its own,
         * Photoshop's "flatten below" kept as a cache.
         */
        public boolean isRetained() {
            checked();
            return retained;
        }

        /**
         * Keeps the composite of this layer and every layer below it in an image of the view's size, so that
         * a change above this layer is recomposed from that image instead of repainting the layers beneath:
         * a heavy base (a map, a document, a chart) under light layers that change often (markers, carets,
         * a crosshair). The pixels shown do not change.
         */
        public void setRetained(boolean value) {
            checked();
            if (retained == value) return;
            retained = value;
            retainedCount += value ? 1 : -1;
            retainedZ.set(indexOf(this), value);
            releaseComposite();
            // A running refresh keeps its selected image alive, but a changed
            // retention boundary must receive a subsequent complete composition.
            if (activeBackingPaints != 0) invalidate(this);
        }

        /**
         * The layer's own content changed (its painter would now paint something else): its pixels are
         * recomposed at the next paint, from the retained composite below it where there is one. Layers below
         * it are not repainted.
         */
        public void redraw() {
            checked();
            blendSource = null; // the image's pixels may have changed
            maskSource = null; // a borrowed mask's pixels may also have changed
            if (nativeLayer != 0) {
                // a native layer's pixels are the window system's: send them again
                dropNative(this);
                syncNative();
                return;
            }
            invalidate(this);
        }

        /**
         * Updates only an image-local dirty rectangle on a compatible native
         * DirectComposition layer. An older maya DLL, missing native backend or
         * failed native update falls back to the original full redraw. Image
         * ownership, the layer ID, paint GC and public Canvas contract do not
         * change. The rectangle uses image pixels, not world coordinates.
         */
        public void redraw(Rectangle dirtyPixels) {
            checked();
            if (dirtyPixels == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            if (image == null || image.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            Rectangle size = image.getBounds();
            int x = dirtyPixels.x, y = dirtyPixels.y;
            int width = dirtyPixels.width, height = dirtyPixels.height;
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || x >= size.width || y >= size.height
                    || width > size.width - x || height > size.height - y) {
                SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            }
            blendSource = null;
            if (nativeLayer != 0 && nativeBackend != null) {
                ImageDataArgb rows = new ImageDataArgb(image.getImageData());
                long pixelCount = (long) width * height;
                if (pixelCount > Integer.MAX_VALUE) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
                int[] packed = new int[(int) pixelCount];
                for (int row = 0; row < height; row++) {
                    rows.premultipliedSpan(y + row, x, width, packed, row * width);
                }
                if (nativeBackend.uploadDirty(nativeLayer, packed,
                        new int[] { x, y, width, height }, 1) == 0) {
                    if (applyNative()) return; // dirty-only frames also require a successful Commit
                }
            }
            redraw();
        }


        /**
         * Submits several image-local dirty rectangles as a single JNI call.
         * All rectangles and the aggregate packed payload are checked on the
         * CPU before converting any pixels. Regions can overlap; later patches
         * cover earlier ones. The existing software/full-image path remains
         * the fallback when a native backend or its optional export is absent.
         * No new SWT controls or native handles are created for individual
         * rectangles. An empty array is a no-op.
         *
         * @param dirtyRegions changed rectangles in borrowed image pixel coordinates
         */
        public void redrawRegions(Rectangle... dirtyRegions) {
            checked();
            if (dirtyRegions == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            if (image == null || image.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            int count = dirtyRegions.length;
            if (count == 0) return;
            if (count > NativeDirtyRegions.MAX_RECTS) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            int[] regions = new int[count * 4];
            for (int i = 0; i < count; i++) {
                Rectangle region = dirtyRegions[i];
                if (region == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
                int at = i * 4;
                regions[at] = region.x;
                regions[at + 1] = region.y;
                regions[at + 2] = region.width;
                regions[at + 3] = region.height;
            }
            Rectangle size = image.getBounds();
            int pixels;
            try {
                pixels = NativeDirtyRegions.packedPixelCount(size.width, size.height, regions, count);
            } catch (IllegalArgumentException invalid) {
                SWT.error(SWT.ERROR_INVALID_ARGUMENT);
                return;
            }
            blendSource = null;
            if (nativeLayer != 0 && nativeBackend != null) {
                ImageDataArgb rows = new ImageDataArgb(image.getImageData());
                int[] packed = new int[pixels];
                int offset = 0;
                for (int i = 0; i < count; i++) {
                    int at = i * 4;
                    int x = regions[at], y = regions[at + 1];
                    int width = regions[at + 2], height = regions[at + 3];
                    for (int row = 0; row < height; row++) {
                        rows.premultipliedSpan(y + row, x, width, packed, offset);
                        offset += width;
                    }
                }
                if (nativeBackend.uploadDirty(nativeLayer, packed, regions, count) == 0 && applyNative()) return;
            }
            redraw();
        }

        private void releaseComposite() {
            if (composite != null) retireComposite(composite);
            composite = null;
            stale = null;
        }

        /**
         * Asks for this image layer to be composited by the window system (a DirectComposition visual on Windows,
         * through the optional maya backend): its pan, opacity, visibility and Z order then change without
         * repainting anything, and it shows above every painted layer, like a control layer. It is native only
         * while the backend can represent its image projection. Original backends retain the one-to-one,
         * unclipped, 100% device-scale contract. A backend explicitly advertising affine/clip support can
         * also scale images and apply rectangular world clips. Source pixels are mapped to this shell's
         * physical client pixels; unsupported features and non-normal blend modes stay painted.
         *
         * @return whether the layer is native now
         */
        public boolean setNative(boolean value) {
            checked();
            if (nativeRequested != value) {
                nativeRequested = value;
                nativeRequestCount += value ? 1 : -1;
            }
            syncNative();
            return nativeLayer != 0;
        }

        /** Whether the window system composites this layer now (see {@link #setNative(boolean)}). */
        public boolean isNative() {
            checked();
            return nativeLayer != 0;
        }

        /**
         * The layer's blend mode with what is below it: {@link LayeredViewport#BLEND_NORMAL} or one of the W3C Compositing and
         * Blending separable modes (multiply, screen, overlay, darken, lighten, color dodge, color burn, hard light,
         * soft light, difference, exclusion), as in GIMP, Inkscape and Photoshop. Image layers blend; a painter layer
         * draws normally whatever its mode. A blended layer is never native: the window system cannot read what is
         * below it.
         */
        public void setBlendMode(int mode) {
            checked();
            if (mode < BLEND_NORMAL || mode > BLEND_EXCLUSION) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (blendMode == mode) return;
            invalidate(this);
            blendMode = mode;
            invalidate(this);
            syncNative();
        }

        /** The layer's blend mode, one of the {@code BLEND_} constants of {@link LayeredViewport}. */
        public int getBlendMode() {
            checked();
            return blendMode;
        }

        /**
         * Non-destructive image-layer coverage. The borrowed mask is never disposed by SWT.
         * It is stretched to this layer's world bounds and only damaged viewport pixels are sampled.
         * Raster masks are not yet supported for painters, layer groups or native controls.
         * After changing pixels of the same mask Image, call {@link #redraw()} to refresh its snapshot.
         */
        public void setMask(Image value) {
            checked();
            if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (value != null && (image == null || control != null)) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (mask == value) return;
            invalidate(this);
            mask = value;
            maskSource = null;
            invalidate(this);
            syncNative();
        }

        public Image getMask() {
            checked();
            return mask;
        }

        /** One of MASK_ALPHA, MASK_INVERTED_ALPHA, MASK_LUMINANCE or MASK_INVERTED_LUMINANCE. */
        public void setMaskMode(int value) {
            checked();
            if (value < MASK_ALPHA || value > MASK_INVERTED_LUMINANCE) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            if (maskMode == value) return;
            if (mask != null) invalidate(this);
            maskMode = value;
            if (mask != null) invalidate(this);
        }

        public int getMaskMode() {
            checked();
            return maskMode;
        }

        public boolean isLocked() {
            checked();
            return locked;
        }

        /** Locked layers remain painted but cannot be returned by hitTest. */
        public void setLocked(boolean value) {
            checked();
            locked = value;
            if (locked && capturedLayer == this) clearPointerCapture();
        }

        /** A replacement raster is borrowed, not copied or owned. */
        public void setImage(Image value) {
            checked();
            if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            invalidate(this);
            image = value;
            painter = null;
            blendSource = null;
            maskSource = null;
            invalidate(this);
            dropNative(this); // new pixels
            syncNative();
        }

        public void setPainter(LayerPainter value) {
            checked();
            invalidate(this);
            painter = value;
            image = null;
            mask = null; // an image coverage mask never silently affects a replacement painter
            maskSource = null;
            invalidate(this);
            syncNative();
        }

        public void setHitTester(LayerHitTester value) {
            checked();
            hitTester = value;
        }

        /**
         * Opt into mouse dispatch without allocating a native Control for this
         * layer. Decoration layers without a handler are skipped by dispatch,
         * but remain ordinary targets of {@link LayeredViewport#hitTest(int, int)}.
         */
        public void setPointerListener(LayerPointerListener value) {
            checked();
            if (pointerListener == value) return;
            if (pointerListener == null && value != null) pointerListenerCount++;
            if (pointerListener != null && value == null) pointerListenerCount--;
            pointerListener = value;
            if (value == null && capturedLayer == this) clearPointerCapture();
        }

        public LayerPointerListener getPointerListener() {
            checked();
            return pointerListener;
        }

        public int getZIndex() {
            checked();
            return indexOf(this);
        }

        /** Reorder without changing this layer's stable identity. */
        public void moveTo(int zIndex) {
            checked();
            if (zIndex < 0 || zIndex >= count) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            int previous = indexOf(this);
            if (previous == zIndex) return;
            invalidate(this);
            if (previous < zIndex) {
                System.arraycopy(stack, previous + 1, stack, previous, zIndex - previous);
            } else {
                System.arraycopy(stack, zIndex, stack, zIndex + 1, previous - zIndex);
            }
            stack[zIndex] = this;
            reindex(Math.min(previous, zIndex), Math.max(previous, zIndex) + 1);
            // A retained image includes the whole prefix, not just this layer's
            // bounds. Crossing a disjoint layer changes that prefix elsewhere.
            if (retained) releaseComposite();
            publishOrder();
            invalidate(this);
            orderControls();
            syncNative();
        }

        public void remove() {
            checked();
            invalidate(this);
            if (capturedLayer == this) clearPointerCapture();
            if (pointerListener != null) pointerListenerCount--;
            int index = indexOf(this);
            System.arraycopy(stack, index + 1, stack, index, count - index - 1);
            stack[--count] = null;
            reindex(index, count);
            retainedZ.clear(count);
            zIndex = -1;
            removed = true;
            if (nativeRequested) nativeRequestCount--;
            if (retained) retainedCount--;
            releaseComposite();
            dropNative(this);
            control = null; // the control stays the application's, where it was
            image = null;
            mask = null;
            maskSource = null;
            painter = null;
            hitTester = null;
            pointerListener = null;
            worldClip = null;
            if (childGroup != null) {
                childGroup.releaseChildren();
                childGroup = null;
            }
            publishOrder();
        }
    }

    /**
     * Blend modes of a layer ({@link Layer#setBlendMode(int)}): normal, then the separable modes of W3C Compositing
     * and Blending Level 1 (the layer modes of GIMP, Inkscape and Photoshop).
     */
    /** Raster mask modes parallel GTK/GSK: alpha, inverted alpha, luminance and inverted luminance. */
    public static final int MASK_ALPHA = 0, MASK_INVERTED_ALPHA = 1,
            MASK_LUMINANCE = 2, MASK_INVERTED_LUMINANCE = 3;

    public static final int BLEND_NORMAL = 0, BLEND_MULTIPLY = 1, BLEND_SCREEN = 2, BLEND_OVERLAY = 3,
            BLEND_DARKEN = 4, BLEND_LIGHTEN = 5, BLEND_COLOR_DODGE = 6, BLEND_COLOR_BURN = 7,
            BLEND_HARD_LIGHT = 8, BLEND_SOFT_LIGHT = 9, BLEND_DIFFERENCE = 10, BLEND_EXCLUSION = 11;

    /* the compositor's mode of each BLEND_ constant (the same order) */
    private static final LayerArgbCompositor.Mode[] BLEND_MODES = LayerArgbCompositor.Mode.values();

    private static final double MIN_ZOOM = 1.0 / 4096.0;
    private static final double MAX_ZOOM = 4096.0;
    /* Native raster blending amortizes JNI over several rows without retaining a full tile of int arrays. */
    private static final int BLEND_BATCH_PIXELS = 16_384;

    private final ViewportRuntime viewportRuntime = new ViewportRuntime();
    private Layer[] stack = new Layer[8];
    private Layer[] paintOrder = new Layer[0];
    private boolean orderDirty;
    private final LayerRectIndex spatialIndex = new LayerRectIndex();
    private final LayerRectIndex.Bounds spatialSource = new LayerRectIndex.Bounds() {
        @Override public int size() { return paintOrder.length; }
        @Override public boolean active(int at) {
            Layer layer = paintOrder[at];
            // pinned and sticky layers are found through pinnedZ: a pan moves them in world space
            if (layer.removed || !layer.visible || layer.opacity == 0 || viewRelative(layer)) return false;
            WorldBounds clip = layer.worldClip;
            return clip == null || (Math.max(layer.x, clip.x()) <
                    Math.min(layer.right, clip.x() + clip.width()) &&
                    Math.max(layer.y, clip.y()) <
                    Math.min(layer.bottom, clip.y() + clip.height()));
        }
        @Override public long left(int at) {
            Layer l = paintOrder[at];
            return l.worldClip == null ? l.x : Math.max(l.x, l.worldClip.x());
        }
        @Override public long top(int at) {
            Layer l = paintOrder[at];
            return l.worldClip == null ? l.y : Math.max(l.y, l.worldClip.y());
        }
        @Override public long right(int at) {
            Layer l = paintOrder[at];
            return l.worldClip == null ? l.right
                    : Math.min(l.right, l.worldClip.x() + l.worldClip.width());
        }
        @Override public long bottom(int at) {
            Layer l = paintOrder[at];
            return l.worldClip == null ? l.bottom
                    : Math.min(l.bottom, l.worldClip.y() + l.worldClip.height());
        }
    };
    private final ViewportDamageRegion pendingDamage = new ViewportDamageRegion();
    private Layer[] paintCandidates = new Layer[16];
    private Layer[] hitCandidates = new Layer[16];
    /* the Z indices, ascending, of the shown pinned and sticky layers, collected with the spatial index */
    private int[] pinnedZ = new int[4];
    private int pinnedCount;
    private boolean spatialDirty = true;
    private boolean fullDamage;
    private int batchDepth;
    private int activePaintFrames;
    private int activeHitQueries;
    private int activeBackingPaints;
    private Image[] retiredComposites = new Image[4];
    private int retiredCompositeCount;
    private int retainedCount;
    private final BitSet retainedZ = new BitSet();
    private int pointerListenerCount;
    private int capturedButtons;
    private Layer capturedLayer;
    private int count;
    private long nextId = 1;
    private long originX, originY;
    private double zoom = 1.0;
    /* the backing store (composite of all layers), its pixels that no longer match the layers, and the scratch
       image a translucent painter layer is isolated in; made on first paint, remade at a new size */
    private Image backBuffer, scratch;
    private ViewportDamageRegion dirty;
    // Parent-owned primitive dirty-rectangle snapshot. Nested paint callbacks
    // borrow independent arrays rather than corrupt an active outer scan.
    private int[] staleWork = new int[64];
    private int staleDepth;
    // Main and retained stores move sequentially on the SWT UI thread.
    // Share one growable primitive rectangle lane across those stores.
    private int[] movedWork = new int[64];
    /*
     * The native client rectangle is authoritative. A border/frame-style
     * transition may change it without changing the outer Control bounds.
     * Store only primitive geometry, not one object per viewport/layout edit.
     */
    private final int[] nativeClientGeometry = new int[4];
    private boolean nativeClientGeometryKnown;
    private boolean nativeClientGeometryRefreshing;
    private boolean nativeClientGeometryPending;
    private boolean nativeClientGeometryQueued;

    public LayeredViewport(Composite parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        viewportRuntime.initializeOrigin(0, 0);
        dirty = new ViewportDamageRegion();
        addPaintListener(event -> paintLayers(event.gc));
        addListener(SWT.Resize, event -> refreshNativeClientGeometry());
        // A monitor/DPI transition may leave logical bounds unchanged, and
        // Win32 may adapt children after its Shell's asynchronous pass. Both
        // notifications use the existing client-geometry synchronization owner.
        addListener(SWT.ZoomChanged, nativeZoomListener);
        addListener(SWT.MouseDown, this::dispatchPointer);
        addListener(SWT.MouseUp, this::dispatchPointer);
        addListener(SWT.MouseMove, this::dispatchPointer);
        addListener(SWT.MouseEnter, this::dispatchPointer);
        addListener(SWT.MouseHover, this::dispatchPointer);
        addListener(SWT.MouseDoubleClick, this::dispatchPointer);
        addListener(SWT.MouseWheel, this::dispatchPointer);
        addListener(SWT.MouseExit, event -> clearPointerCapture());
        addListener(SWT.FocusOut, event -> clearPointerCapture());
        addListener(SWT.Dispose, event -> {
            long disposedComposition = composition;
            composition = 0;
            for (int i = 0; i < count; i++) stack[i].nativeLayer = 0;
            if (disposedComposition != 0) nativeBackend.dispose(disposedComposition);
            // A painter may dispose the widget while one of these images has a
            // selected GC. The active paint closes that GC before the images.
            if (activeBackingPaints == 0) disposeBackingImages();
            for (int i = 0; i < count; i++) {
                stack[i].removed = true;
                if (stack[i].childGroup != null) stack[i].childGroup.releaseChildren();
                stack[i].childGroup = null;
                stack[i].releaseComposite();
                stack[i].control = null;
                stack[i].image = null;
                stack[i].mask = null;
                stack[i].maskSource = null;
                stack[i].painter = null;
                stack[i].hitTester = null;
                stack[i].pointerListener = null;
                stack[i].worldClip = null;
                stack[i] = null;
            }
            count = retainedCount = nativeRequestCount = 0;
            retainedZ.clear();
            nativeHandles = new long[0];
            nativeProperties = new float[0];
            nativeSceneProperties = new float[0];
            nativeSceneFlags = new int[0];
            nativeCapabilities = nativeCapabilitiesComposition = 0;
            if (nativeZoomShell != null && !nativeZoomShell.isDisposed()) {
                nativeZoomShell.removeListener(SWT.ZoomChanged, nativeZoomListener);
            }
            nativeZoomShell = null;
            nativeSyncPending = false;
            pointerListenerCount = 0;
            clearPointerCapture();
            paintOrder = new Layer[0];
            orderDirty = false;
            spatialIndex.clear();
            pinnedCount = 0;
            // Active callbacks keep their own local array reference and clear
            // it in finally; do not erase a snapshot still being traversed.
            if (activePaintFrames == 0) Arrays.fill(paintCandidates, null);
            if (activeHitQueries == 0) Arrays.fill(hitCandidates, null);
            paintCandidates = new Layer[0];
            hitCandidates = new Layer[0];
            pendingDamage.clear();
        });
    }

    public Layer addPaintLayer(String name, long x, long y,
            long width, long height, LayerPainter painter) {
        checkWidget();
        if (name == null || painter == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        return append(name, x, y, width, height, null, painter);
    }

    public Layer addImageLayer(String name, Image image, long x, long y) {
        checkWidget();
        if (name == null || image == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (image.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        Rectangle size = image.getBounds();
        return append(name, x, y, size.width, size.height, image, null);
    }

    /**
     * Adds a layer that places a native control at world bounds: it moves and resizes with every pan and zoom,
     * and is shown only while the layer is visible, its opacity above 0 and its bounds inside the view. The
     * window system draws a child control above every painted layer (it cannot be blended); among control layers
     * the Z order is the layers' order. The control must be a child of this viewport and stays the
     * application's: removing the layer leaves it where it was and does not dispose it.
     */
    public Layer addControlLayer(String name, Control control, long x, long y, long width, long height) {
        checkWidget();
        if (name == null || control == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (control.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if (control.getParent() != this) SWT.error(SWT.ERROR_INVALID_PARENT);
        Layer layer = append(name, x, y, width, height, null, null);
        layer.control = control;
        placeControl(layer);
        orderControls();
        return layer;
    }

    /** The control of a control layer, or null for a painted layer. */
    public Control getControl(Layer layer) {
        checkWidget();
        if (layer == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        layer.checked();
        return layer.control;
    }

    private Layer append(String name, long x, long y, long width,
            long height, Image image, LayerPainter painter) {
        validateBounds(x, y, width, height);
        if (nextId == Long.MAX_VALUE) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if (count == stack.length) stack = Arrays.copyOf(stack, count * 2);
        Layer layer = new Layer(nextId++, name, x, y, width, height, image, painter);
        layer.zIndex = count;
        stack[count++] = layer;
        publishOrder();
        invalidate(layer);
        return layer;
    }

    /**
     * Resync native child bounds and compositor visibility after a change in
     * the actual platform client rectangle, including border-only changes that
     * do not issue a normal outer-window SWT.Resize event.
     *
     * The native OS still owns frame recalculation. This UI-thread method only
     * observes the resulting client area; the existing Control.setBounds and
     * NativeLayers.apply backends own the native geometry operations.
     */
    @Override
    public void setBorderRelief(int relief) {
        super.setBorderRelief(relief);
        if (!isDisposed()) refreshNativeClientGeometry();
    }

    private boolean matchesNativeClientGeometry(Rectangle area) {
        return nativeClientGeometryKnown && nativeClientGeometry[0] == area.x
                && nativeClientGeometry[1] == area.y
                && nativeClientGeometry[2] == area.width
                && nativeClientGeometry[3] == area.height;
    }

    /*
     * SWT/OS native frame callbacks and child resize listeners can re-enter
     * this method. Never recursively apply child positions or overwrite an
     * active Maya composition frame. Two synchronous passes absorb ordinary
     * reentry; a further change is coalesced into one UI-thread callback.
     */
    private void refreshNativeClientGeometry() {
        if (isDisposed()) return;
        if (nativeClientGeometryRefreshing) {
            nativeClientGeometryPending = true;
            return;
        }
        nativeClientGeometryRefreshing = true;
        try {
            for (int pass = 0; pass < 2 && !isDisposed(); pass++) {
                Rectangle area = getClientArea();
                if (matchesNativeClientGeometry(area)) {
                    nativeClientGeometryPending = false;
                    break;
                }
                nativeClientGeometry[0] = area.x;
                nativeClientGeometry[1] = area.y;
                nativeClientGeometry[2] = area.width;
                nativeClientGeometry[3] = area.height;
                nativeClientGeometryKnown = true;
                nativeClientGeometryPending = false;

                // Clip, retained pixels, native child visibility and compositor
                // culling all depend on the same OS-reported client rectangle.
                spatialDirty = true;
                redraw();
                if (isDisposed()) break;
                placeControls();
                if (isDisposed()) break;
                syncNative();
                if (isDisposed()) break;
                if (!matchesNativeClientGeometry(getClientArea())) {
                    nativeClientGeometryPending = true;
                }
                if (!nativeClientGeometryPending) break;
            }
        } finally {
            nativeClientGeometryRefreshing = false;
        }
        if (nativeClientGeometryPending && !nativeClientGeometryQueued && !isDisposed()) {
            nativeClientGeometryQueued = true;
            getDisplay().asyncExec(() -> {
                nativeClientGeometryQueued = false;
                if (!isDisposed()) refreshNativeClientGeometry();
            });
        }
    }

    /** Bottom-to-top snapshot; changing this array cannot reorder the view. */
    public Layer[] getLayers() {
        checkWidget();
        return orderSnapshot().clone();
    }

    public long getOriginX() {
        checkWidget();
        return originX;
    }

    public long getOriginY() {
        checkWidget();
        return originY;
    }

    /** Pan without changing any layer's world-space geometry. */
    public void setOrigin(long x, long y) {
        checkWidget();
        if (originX == x && originY == y) return;
        long previousX = originX, previousY = originY;
        originX = x;
        originY = y;
        viewportRuntime.scrollTo((double) x, (double) y);
        if (!shiftBackBuffer(previousX, previousY)) redraw();
        placeControls();
        syncNative();
    }

    public double getZoom() {
        checkWidget();
        return zoom;
    }

    /** World-to-device zoom, bounded to keep the viewport numerically stable. */
    public void setZoom(double value) {
        checkWidget();
        if (!Double.isFinite(value) || value < MIN_ZOOM || value > MAX_ZOOM) {
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }
        if (zoom == value) return;
        zoom = value;
        redraw();
        placeControls();
        syncNative();
    }

    public Point toScreen(long worldX, long worldY) {
        checkWidget();
        return new Point(saturatedPixel(device(worldX, originX)),
                saturatedPixel(device(worldY, originY)));
    }

    public WorldPoint toWorld(int deviceX, int deviceY) {
        checkWidget();
        return new WorldPoint(saturatedAdd(originX, floorLong(deviceX / zoom)),
                saturatedAdd(originY, floorLong(deviceY / zoom)));
    }

    /** Topmost visible, unlocked layer, using a narrow two-axis spatial query. */
    public Layer hitTest(int deviceX, int deviceY) {
        checkWidget();
        return queryHit(deviceX, deviceY, false);
    }

    /**
     * Topmost hittable layer accepted by {@code accept}, or null. As in Draw2D's
     * ExclusionSearch, callers can omit a dragged/selected layer and target the
     * layers beneath it without changing scene order or native pointer capture.
     * Filtering occurs after the layer's own hit tester; a callback may reenter
     * or mutate the scene, and candidates are rechecked before returning.
     */
    public Layer hitTest(int deviceX, int deviceY, Predicate<? super Layer> accept) {
        checkWidget();
        if (accept == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        return queryHit(deviceX, deviceY, false, accept);
    }

    private long worldX(int deviceX) {
        return saturatedAdd(originX, floorLong(deviceX / zoom));
    }

    private long worldY(int deviceY) {
        return saturatedAdd(originY, floorLong(deviceY / zoom));
    }

    private Layer queryHit(int deviceX, int deviceY, boolean withPointerListener) {
        return queryHit(deviceX, deviceY, withPointerListener, null);
    }

    private Layer queryHit(int deviceX, int deviceY, boolean withPointerListener,
            Predicate<? super Layer> accept) {
        if (!getClientArea().contains(deviceX, deviceY)) return null;
        ensureSpatialIndex();
        long worldX = worldX(deviceX), worldY = worldY(deviceY);
        int found = spatialIndex.query(worldX, worldY,
                saturatedAdd(worldX, 1), saturatedAdd(worldY, 1));
        int candidates = found + pinnedCount;
        // Hit testers may re-enter the scene; capture handles before callbacks.
        Layer[] hits = activeHitQueries == 0
                ? growHitCandidates(candidates) : new Layer[candidates];
        int normal = 0, pinned = 0;
        for (int i = 0; i < candidates; i++) {
            int a = normal < found ? spatialIndex.resultAt(normal) : Integer.MAX_VALUE;
            int b = pinned < pinnedCount ? pinnedZ[pinned] : Integer.MAX_VALUE;
            if (a < b) normal++;
            else pinned++;
            hits[i] = paintOrder[Math.min(a, b)];
        }
        activeHitQueries++;
        try {
            for (int i = candidates - 1; i >= 0; i--) {
                if (isDisposed()) return null;
                Layer layer = hits[i];
                worldX = layerX(layer, deviceX);
                worldY = layerY(layer, deviceY);
                if (!containsHit(layer, worldX, worldY)
                        || (withPointerListener && layer.pointerListener == null)) continue;
                LayerHitTester tester = layer.hitTester;
                boolean accepted = tester == null || tester.contains(layer, worldX, worldY);
                if (isDisposed()) return null;
                // Callbacks can remove, move, hide or lock their own target,
                // or another candidate captured by this query.
                if (accepted && containsHit(layer, layerX(layer, deviceX), layerY(layer, deviceY))
                        && (!withPointerListener || layer.pointerListener != null)) {
                    if (accept == null) return layer;
                    boolean included = accept.test(layer);
                    if (isDisposed()) return null;
                    // User filtering may change visibility, world origin, native
                    // pinning or this very layer. Never return stale geometry.
                    if (included && containsHit(layer, layerX(layer, deviceX), layerY(layer, deviceY))
                            && (!withPointerListener || layer.pointerListener != null)) return layer;
                }
            }
            return null;
        } finally {
            Arrays.fill(hits, 0, candidates, null);
            activeHitQueries--;
        }
    }

    private static boolean containsHit(Layer layer, long worldX, long worldY) {
        if (layer.removed || layer.locked || !layer.visible || layer.opacity == 0) return false;
        if (worldX < layer.x || worldX >= layer.right
                || worldY < layer.y || worldY >= layer.bottom) return false;
        WorldBounds clip = layer.worldClip;
        return clip == null || (worldX >= clip.x() && worldX < clip.x() + clip.width()
                && worldY >= clip.y() && worldY < clip.y() + clip.height());
    }

    /** Current logical pointer capture, or null. The layer is never a native Control. */
    public Layer getPointerCapture() {
        checkWidget();
        return capturedLayer;
    }

    /** Releases logical pointer capture without altering SWT/native event routing. */
    public void releasePointerCapture() {
        checkWidget();
        clearPointerCapture();
    }

    private void clearPointerCapture() {
        capturedLayer = null;
        capturedButtons = 0;
    }

    private static int buttonBit(int button) {
        return button > 0 && button <= 31 ? 1 << (button - 1) : 0;
    }

    private void dispatchPointer(Event event) {
        if (pointerListenerCount == 0 && capturedLayer == null) return;
        boolean byCapture = capturedLayer != null && event.type != SWT.MouseWheel;
        Layer layer = byCapture ? capturedLayer
                : queryHit(event.x, event.y, true);
        if (layer == null || layer.removed) return;
        long worldX = layerX(layer, event.x), worldY = layerY(layer, event.y);
        LayerPointerListener listener = layer.pointerListener;
        if (listener == null) {
            if (byCapture) clearPointerCapture();
            return;
        }
        int button = buttonBit(event.button);
        if (event.type == SWT.MouseDown && button != 0) {
            if (capturedLayer == null) capturedLayer = layer;
            capturedButtons |= button;
        }
        boolean delivered = false;
        try {
            listener.handle(new LayerPointerEvent(layer, event.type, event.x, event.y,
                    worldX, worldY, event.button, event.count, event.stateMask,
                    event.time, byCapture));
            delivered = true;
        } finally {
            if (!delivered && event.type == SWT.MouseDown && capturedLayer == layer) {
                capturedButtons &= ~button;
                if (capturedButtons == 0) clearPointerCapture();
            }
            if (event.type == SWT.MouseUp && capturedLayer == layer) {
                capturedButtons &= ~button;
                if (capturedButtons == 0 || button == 0) clearPointerCapture();
            }
        }
    }

    /*
     * recomposes the damaged pixels that are dirty into the backing store, then copies the damage from the store to
     * the paint event's GC
     */
    private void paintLayers(GC gc) {
        if (isDisposed() || gc.isDisposed()) return;
        Rectangle area = getClientArea();
        if (activeBackingPaints != 0) {
            // A callback can dispatch another paint. An Image supports only
            // one selected GC, so defer that paint until this one releases it.
            markDirty(area);
            super.redraw();
            return;
        }
        Rectangle damage = intersect(gc.getClipping(), area);
        if (damage == null) return;
        activeBackingPaints++;
        try {
            ensureBackBuffer(area);
            int n = dirty.rectCount();
            int required = Math.multiplyExact(n, 4);
            int[] stale = staleDepth == 0 ? growStaleWork(required) : new int[required];
            staleDepth++;
            try {
                int staleCount = 0;
                for (int i = 0; i < n; i++) {
                    long x0 = Math.max((long) dirty.x(i), damage.x);
                    long y0 = Math.max((long) dirty.y(i), damage.y);
                    long x1 = Math.min((long) dirty.x(i) + dirty.width(i),
                            (long) damage.x + damage.width);
                    long y1 = Math.min((long) dirty.y(i) + dirty.height(i),
                            (long) damage.y + damage.height);
                    if (x1 <= x0 || y1 <= y0) continue;
                    int offset = staleCount++ * 4;
                    stale[offset] = (int) x0;
                    stale[offset + 1] = (int) y0;
                    stale[offset + 2] = (int) (x1 - x0);
                    stale[offset + 3] = (int) (y1 - y0);
                }
                if (staleCount > 0) {
                    // Consume captured damage before callbacks; their new
                    // invalidations belong to the next SWT paint event.
                    dirty.remove(damage.x, damage.y, damage.width, damage.height);
                    GC[] back = { null };
                    boolean complete = false;
                    try {
                        back[0] = new GC(backBuffer);
                        for (int i = 0; i < staleCount; i++) {
                            int offset = i * 4;
                            compose(back, new Rectangle(stale[offset], stale[offset + 1],
                                    stale[offset + 2], stale[offset + 3]));
                            if (isDisposed() || back[0].isDisposed()) return;
                        }
                        complete = true;
                    } finally {
                        try {
                            if (!complete && !isDisposed()) {
                                for (int i = 0; i < staleCount; i++) {
                                    int offset = i * 4;
                                    dirty.add(stale[offset], stale[offset + 1],
                                            stale[offset + 2], stale[offset + 3]);
                                }
                            }
                        } finally {
                            if (back[0] != null && !back[0].isDisposed()) back[0].dispose();
                        }
                    }
                }
                if (!isDisposed() && !gc.isDisposed()) {
                    gc.drawImage(backBuffer, damage.x, damage.y, damage.width, damage.height,
                            damage.x, damage.y, damage.width, damage.height);
                }
            } finally {
                staleDepth--;
            }
        } finally {
            activeBackingPaints--;
            if (activeBackingPaints == 0) {
                disposeRetiredComposites();
                if (isDisposed()) disposeBackingImages();
            }
        }
    }

    private int[] growStaleWork(int required) {
        if (staleWork.length < required) {
            int capacity = staleWork.length;
            while (capacity < required) capacity += Math.max(64, capacity >>> 1);
            staleWork = Arrays.copyOf(staleWork, capacity);
        }
        return staleWork;
    }

    private int[] growMovedWork(int required) {
        if (movedWork.length < required) {
            int capacity = (int) Math.min(Integer.MAX_VALUE,
                    Math.max((long) required, movedWork.length + (long) Math.max(64, movedWork.length >>> 1)));
            movedWork = Arrays.copyOf(movedWork, capacity);
        }
        return movedWork;
    }

    /*
     * the store's pixels inside {@code damage}: the retained composites are brought up to date there, bottom to
     * top, each from the one below it; the store then starts from the topmost one (or the background) and only
     * the layers above it are painted
     */
    private void compose(GC[] back, Rectangle damage) {
        Layer[] frame = orderSnapshot();
        Image below = null;
        int from = 0;
        for (int z = 0; z < frame.length; z++) {
            Layer layer = frame[z];
            if (!layer.retained || layer.removed) continue;
            below = refresh(layer, below, from, z + 1, damage, frame);
            if (isDisposed() || back[0].isDisposed()) return;
            from = z + 1;
        }
        back[0].setClipping(damage);
        start(back[0], below, damage);
        composeLayers(back, backBuffer, damage, from, frame.length, frame);
        if (!isDisposed() && !back[0].isDisposed()) back[0].setClipping((Rectangle) null);
    }

    /* recomposes a retained layer's stale pixels inside {@code damage}, from the captured composite below it */
    private Image refresh(Layer layer, Image below, int from, int to, Rectangle damage, Layer[] frame) {
        Rectangle size = backBuffer.getBounds();
        if (layer.composite == null) {
            layer.composite = new Image(getDisplay(), size.width, size.height);
            layer.stale = new ViewportDamageRegion();
            layer.stale.add(0, 0, size.width, size.height);
        }
        // Callbacks can remove this layer or release its cache. Keep the
        // selected image and damage owner alive until this frame closes its GC.
        Image target = layer.composite;
        ViewportDamageRegion region = layer.stale;
        int n = region.rectCount();
        if (n == 0) return target;
        int[] rects = new int[Math.multiplyExact(n, 4)];
        int k = 0;
        for (int i = 0; i < n; i++) {
            Rectangle r = intersect(new Rectangle(region.x(i), region.y(i), region.width(i),
                    region.height(i)), damage);
            if (r == null) continue;
            rects[k * 4] = r.x;
            rects[k * 4 + 1] = r.y;
            rects[k * 4 + 2] = r.width;
            rects[k * 4 + 3] = r.height;
            k++;
        }
        if (k == 0) return target;
        region.remove(damage.x, damage.y, damage.width, damage.height);
        GC[] gc = { null };
        boolean complete = false;
        try {
            gc[0] = new GC(target);
            for (int i = 0; i < k; i++) {
                Rectangle r = new Rectangle(rects[i * 4], rects[i * 4 + 1], rects[i * 4 + 2], rects[i * 4 + 3]);
                gc[0].setClipping(r);
                start(gc[0], below, r);
                composeLayers(gc, target, r, from, to, frame);
                if (isDisposed() || gc[0].isDisposed()) return target;
            }
            complete = true;
        } finally {
            try {
                if (!complete && !isDisposed() && layer.composite == target) {
                    for (int i = 0; i < k; i++) {
                        int offset = i * 4;
                        region.add(rects[offset], rects[offset + 1], rects[offset + 2], rects[offset + 3]);
                    }
                }
            } finally {
                if (gc[0] != null && !gc[0].isDisposed()) gc[0].dispose();
            }
        }
        return target;
    }

    /* the pixels a composition starts from: the retained composite of layer {@code below}, or the background */
    private void start(GC gc, Image below, Rectangle r) {
        if (below == null) {
            drawBackground(gc, r.x, r.y, r.width, r.height);
        } else {
            gc.drawImage(below, r.x, r.y, r.width, r.height, r.x, r.y, r.width, r.height);
        }
    }

    /* the layers the spatial index finds under {@code damage}, bottom to top, into the store's GC */
    /* the layers of Z indices {@code from} to {@code to - 1} the spatial index finds under {@code damage}, into
       {@code target} through {@code back[0]} */
    private void composeLayers(GC[] back, Image target, Rectangle damage, int from, int to) {
        composeLayers(back, target, damage, from, to, orderSnapshot());
    }

    private void composeLayers(GC[] back, Image target, Rectangle damage, int from, int to, Layer[] frame) {
        int originalAlpha = back[0].getAlpha();
        Layer[] candidates;
        int selected;
        if (frame == paintOrder && !orderDirty) {
            ensureSpatialIndex();
            long x0 = saturatedAdd(originX, saturatedAdd(floorLong(damage.x / zoom), -1));
            long y0 = saturatedAdd(originY, saturatedAdd(floorLong(damage.y / zoom), -1));
            long x1 = saturatedAdd(originX, saturatedAdd(ceilLong(
                    ((double) damage.x + damage.width) / zoom), 1));
            long y1 = saturatedAdd(originY, saturatedAdd(ceilLong(
                    ((double) damage.y + damage.height) / zoom), 1));
            int found = spatialIndex.query(x0, y0, x1, y1), total = found + pinnedCount;
            candidates = activePaintFrames == 0 ? growPaintCandidates(total) : new Layer[total];
            selected = 0;
            int normal = 0, pinned = 0;
            for (int i = 0; i < total; i++) {
                int a = normal < found ? spatialIndex.resultAt(normal) : Integer.MAX_VALUE;
                int b = pinned < pinnedCount ? pinnedZ[pinned] : Integer.MAX_VALUE;
                if (a < b) normal++;
                else pinned++;
                int z = Math.min(a, b);
                if (z >= from && z < to) candidates[selected++] = frame[z];
            }
        } else {
            // An earlier retained-cache painter changed the scene order. Its
            // original frame still governs the remaining segments of this paint.
            selected = to - from;
            candidates = activePaintFrames == 0 ? growPaintCandidates(selected) : new Layer[selected];
            System.arraycopy(frame, from, candidates, 0, selected);
        }
        activePaintFrames++;
        try {
            for (int i = 0; i < selected; i++) {
                if (isDisposed() || back[0].isDisposed()) break;
                Layer layer = candidates[i];
                if (layer.removed || !layer.visible || layer.opacity == 0 || layer.nativeLayer != 0) continue;
                Rectangle tile = projectedClip(layer, damage);
                if (tile == null) continue;
                if (layer.image != null && (layer.blendMode != BLEND_NORMAL || layer.mask != null)) {
                    // Masks reuse the existing bounded premultiplied scanline compositor.
                    try (ViewportGcProxy state = ViewportGcProxy.wrap(back[0])) {
                        state.clip(tile).alpha(255);
                        if (!layer.image.isDisposed() && (layer.mask == null || !layer.mask.isDisposed())) {
                            paintBlended(back, layer, tile);
                        }
                    }
                    continue;
                }
                if (layer.image == null && layer.painter != null && layer.opacity < 255) {
                    paintIsolated(back, target, layer, tile, originalAlpha);
                    continue;
                }
                GC gc = back[0];
                try (ViewportGcProxy state = ViewportGcProxy.wrap(gc)) {
                    state.clip(tile).alpha(combinedAlpha(originalAlpha, layer.opacity));
                    if (layer.image != null) {
                        if (!layer.image.isDisposed()) paintImage(gc, layer, tile);
                    } else if (layer.painter != null) {
                        layer.painter.paint(gc,
                                new PaintContext(layer, tile, layerOriginX(layer), layerOriginY(layer), zoom));
                    }
                }
            }
        } finally {
            Arrays.fill(candidates, 0, selected, null);
            activePaintFrames--;
        }
    }

    private Layer[] growPaintCandidates(int selected) {
        if (selected > paintCandidates.length) {
            int capacity = paintCandidates.length;
            while (capacity < selected) capacity = capacity + Math.max(16, capacity >>> 1);
            paintCandidates = Arrays.copyOf(paintCandidates, capacity);
        }
        return paintCandidates;
    }

    private Layer[] growHitCandidates(int selected) {
        if (selected > hitCandidates.length) {
            int capacity = hitCandidates.length;
            while (capacity < selected) capacity = capacity + Math.max(16, capacity >>> 1);
            hitCandidates = Arrays.copyOf(hitCandidates, capacity);
        }
        return hitCandidates;
    }

    private void ensureSpatialIndex() {
        orderSnapshot();
        if (spatialDirty) {
            spatialIndex.rebuild(spatialSource);
            pinnedCount = 0;
            for (int z = 0; z < paintOrder.length; z++) {
                Layer layer = paintOrder[z];
                if (!viewRelative(layer) || layer.removed || !layer.visible || layer.opacity == 0) continue;
                if (pinnedCount == pinnedZ.length) pinnedZ = Arrays.copyOf(pinnedZ, 2 * pinnedCount);
                pinnedZ[pinnedCount++] = z;
            }
            spatialDirty = false;
        }
    }

    /* whether a pan moves the layer in world space: it is pinned or sticky */
    private static boolean viewRelative(Layer layer) {
        return layer.fixed != 0 || layer.pinned != 0 || layer.sticky != 0;
    }

    /* the origin a layer is projected from on each axis */
    private long layerOriginX(Layer layer) {
        return layerOrigin(layer, SWT.HORIZONTAL, originX);
    }

    private long layerOriginY(Layer layer) {
        return layerOrigin(layer, SWT.VERTICAL, originY);
    }

    /*
     * the origin a layer is projected from on one axis when the view's origin there is {@code origin}: 0 where it is
     * pinned; where it is sticky and stuck, the view's less how far the layer has been carried past its own place
     */
    private static long layerOrigin(Layer layer, int axis, long origin) {
        if (((layer.fixed | layer.pinned) & axis) != 0) return 0;
        if (layer.sticky != axis) return origin;
        long start = axis == SWT.HORIZONTAL ? layer.x : layer.y, end = axis == SWT.HORIZONTAL ? layer.right : layer.bottom;
        long stuck = Math.min(saturatedAdd(origin, layer.stickyInset), saturatedAdd(layer.stickyLimit, start - end));
        return stuck > start ? saturatedAddSubtract(origin, start, stuck) : origin;
    }

    private double layerScaleX(Layer layer) { return (layer.fixed & SWT.HORIZONTAL) != 0 ? 1 : zoom; }
    private double layerScaleY(Layer layer) { return (layer.fixed & SWT.VERTICAL) != 0 ? 1 : zoom; }

    /* a device point's world coordinates as a layer sees them */
    private long layerX(Layer layer, int deviceX) {
        return saturatedAdd(layerOriginX(layer), floorLong(deviceX / layerScaleX(layer)));
    }

    private long layerY(Layer layer, int deviceY) {
        return saturatedAdd(layerOriginY(layer), floorLong(deviceY / layerScaleY(layer)));
    }

    private static long ceilLong(double value) {
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value <= Long.MIN_VALUE) return Long.MIN_VALUE;
        return (long) Math.ceil(value);
    }

    /*
     * a translucent painter layer, as a group: painted onto a copy of the composite beneath it, which is then drawn
     * once over the store with the layer's opacity, so overlapping shapes of the layer do not darken each other
     */
    private void paintIsolated(GC[] back, Image target, Layer layer, Rectangle tile, int originalAlpha) {
        Rectangle size = target.getBounds();
        if (scratch == null) scratch = new Image(getDisplay(), size.width, size.height);
        int opacity = combinedAlpha(originalAlpha, layer.opacity);
        LayerPainter painter = layer.painter;
        back[0].dispose();
        GC copy = new GC(scratch);
        try {
            copy.drawImage(target, tile.x, tile.y, tile.width, tile.height, tile.x, tile.y, tile.width, tile.height);
            copy.setClipping(tile);
            painter.paint(copy, new PaintContext(layer, tile, layerOriginX(layer), layerOriginY(layer), zoom));
        } finally {
            copy.dispose();
            if (!isDisposed()) back[0] = new GC(target);
        }
        if (isDisposed()) return;
        try (ViewportGcProxy state = ViewportGcProxy.wrap(back[0])) {
            state.clip(tile).alpha(opacity);
            back[0].drawImage(scratch, tile.x, tile.y, tile.width, tile.height, tile.x, tile.y, tile.width, tile.height);
        }
    }

    private static int combinedAlpha(int alpha, int opacity) {
        return (alpha * opacity + 127) / 255;
    }

    /*
     * a blended image layer: the pixels below the tile are read from the store, blended with the layer's (nearest
     * source pixel, its alpha times the layer's opacity) by its mode, and written back once
     */
    private void paintBlended(GC[] back, Layer layer, Rectangle tile) {
        if (tile.width <= 0 || tile.height <= 0) return;
        if (layer.blendSource == null) layer.blendSource = layer.image.getImageData();
        ImageData source = layer.blendSource;
        Image mask = layer.mask;
        if (mask != null && layer.maskSource == null) layer.maskSource = mask.getImageData();
        ImageData maskData = mask == null ? null : layer.maskSource;
        int maskMode = layer.maskMode;
        long ox = layerOriginX(layer), oy = layerOriginY(layer);
        double left = device(layer.x, ox, layerScaleX(layer)), top = device(layer.y, oy, layerScaleY(layer));
        double width = device(layer.right, ox, layerScaleX(layer)) - left, height = device(layer.bottom, oy, layerScaleY(layer)) - top;
        if (!(width > 0) || !(height > 0) || source.width == 0 || source.height == 0
                || maskData != null && (maskData.width == 0 || maskData.height == 0)) return;
        Image under = new Image(getDisplay(), tile.width, tile.height);
        try {
            back[0].copyArea(under, tile.x, tile.y);
            ImageData below = under.getImageData();
            ImageDataArgb store = new ImageDataArgb(below); // whole-row conversion, no per-pixel RGB objects
            ImageDataArgb sourceColors = new ImageDataArgb(source);
            ImageDataArgb maskColors = maskData != null && maskMode >= MASK_LUMINANCE
                    ? new ImageDataArgb(maskData) : null;
            int rowsPerBatch = Math.max(1, BLEND_BATCH_PIXELS / tile.width);
            int capacity = Math.min(rowsPerBatch, tile.height) * tile.width;
            int[] dst = new int[capacity], src = new int[capacity];
            byte[] cover = maskData == null ? null : new byte[capacity];
            for (int startRow = 0; startRow < tile.height; startRow += rowsPerBatch) {
                int rows = Math.min(rowsPerBatch, tile.height - startRow);
                for (int row = 0; row < rows; row++) {
                    int j = startRow + row, base = row * tile.width;
                    int sy = clipSource((tile.y + j + 0.5 - top) / height * source.height, source.height - 1, false);
                    int my = maskData == null ? 0 : clipSource(
                            (tile.y + j + 0.5 - top) / height * maskData.height, maskData.height - 1, false);
                    store.opaqueRow(j, dst, base);
                    for (int i = 0; i < tile.width; i++) {
                        int sx = clipSource((tile.x + i + 0.5 - left) / width * source.width, source.width - 1, false);
                        int pixel = source.getPixel(sx, sy);
                        int alpha = source.alphaData != null ? source.getAlpha(sx, sy) : source.alpha >= 0 ? source.alpha : 255;
                        if (source.transparentPixel >= 0 && pixel == source.transparentPixel) alpha = 0;
                        int rgb = sourceColors.rgb(pixel);
                        src[base + i] = alpha << 24 | ((rgb >>> 16 & 0xFF) * alpha + 127) / 255 << 16
                                | ((rgb >>> 8 & 0xFF) * alpha + 127) / 255 << 8
                                | ((rgb & 0xFF) * alpha + 127) / 255;
                        if (maskData != null) {
                            int mx = clipSource((tile.x + i + 0.5 - left) / width
                                    * maskData.width, maskData.width - 1, false);
                            int maskPixel = maskData.getPixel(mx, my);
                            int maskAlpha = maskData.alphaData != null ? maskData.getAlpha(mx, my)
                                    : maskData.alpha >= 0 ? maskData.alpha : 255;
                            if (maskData.transparentPixel >= 0 && maskPixel == maskData.transparentPixel) {
                                maskAlpha = 0;
                            }
                            int maskRgb = maskColors == null ? 0 : maskColors.rgb(maskPixel);
                            cover[base + i] = (byte) LayerArgbCompositor.maskCoverage(maskAlpha, maskRgb, maskMode);
                        }
                    }
                }
                if (cover == null) {
                    LayerArgbCompositor.composite(dst, 0, src, 0, rows * tile.width,
                            layer.opacity, BLEND_MODES[layer.blendMode]);
                } else {
                    LayerArgbCompositor.compositeMasked(dst, 0, src, 0, cover, 0, rows * tile.width,
                            layer.opacity, BLEND_MODES[layer.blendMode]);
                }
                for (int row = 0; row < rows; row++) {
                    int j = startRow + row, base = row * tile.width;
                    store.setOpaqueRow(j, dst, base);
                }
            }
            Image blended = new Image(getDisplay(), below);
            try {
                back[0].setClipping(tile);
                back[0].setAlpha(255);
                back[0].drawImage(blended, tile.x, tile.y);
            } finally {
                blended.dispose();
            }
        } finally {
            under.dispose();
        }
    }

    private void paintImage(GC gc, Layer layer, Rectangle tile) {
        long ox = layerOriginX(layer), oy = layerOriginY(layer);
        paintImage(gc, layer.image, device(layer.x, ox, layerScaleX(layer)), device(layer.y, oy, layerScaleY(layer)),
                device(layer.right, ox, layerScaleX(layer)), device(layer.bottom, oy, layerScaleY(layer)), tile);
    }

    /** Shared full-source mapping and clipped-source fallback for groups and ordinary layers. */
    private void paintImage(GC gc, Image image, double left, double top,
            double right, double bottom, Rectangle tile) {
        Rectangle srcBounds = image.getBounds();
        if (srcBounds.width == 0 || srcBounds.height == 0) return;
        double width = right - left;
        double height = bottom - top;
        if (!(width > 0) || !(height > 0)) return;

        // Ordinary layers retain the complete source-to-destination mapping.
        // The clipped source path avoids enormous int destination coordinates.
        if (left >= Integer.MIN_VALUE && top >= Integer.MIN_VALUE
                && right <= Integer.MAX_VALUE && bottom <= Integer.MAX_VALUE
                && Math.ceil(right) - Math.floor(left) <= Integer.MAX_VALUE
                && Math.ceil(bottom) - Math.floor(top) <= Integer.MAX_VALUE) {
            int x = (int) Math.floor(left);
            int y = (int) Math.floor(top);
            int w = (int) (Math.ceil(right) - x);
            int h = (int) (Math.ceil(bottom) - y);
            if (w > 0 && h > 0) {
                gc.drawImage(image, srcBounds.x, srcBounds.y,
                        srcBounds.width, srcBounds.height, x, y, w, h);
            }
            return;
        }

        if (!Double.isFinite(width) || !Double.isFinite(height)) return;
        int sx0 = clipSource((tile.x - left) / width * srcBounds.width, srcBounds.width, false);
        int sy0 = clipSource((tile.y - top) / height * srcBounds.height, srcBounds.height, false);
        int sx1 = clipSource(((double)tile.x + tile.width - left) / width
                * srcBounds.width, srcBounds.width, true);
        int sy1 = clipSource(((double)tile.y + tile.height - top) / height
                * srcBounds.height, srcBounds.height, true);
        if (sx1 > sx0 && sy1 > sy0) {
            gc.drawImage(image, srcBounds.x + sx0, srcBounds.y + sy0,
                    sx1 - sx0, sy1 - sy0, tile.x, tile.y, tile.width, tile.height);
        }
    }

    private Rectangle projectedClip(Layer layer, Rectangle damage) {
        return projectedClip(layer, layerOriginX(layer), layerOriginY(layer), damage);
    }

    private Rectangle projectedClip(Layer layer, long ox, long oy, Rectangle damage) {
        Rectangle visible = projectedClip(layer.x, layer.y, layer.right, layer.bottom, ox, oy, layerScaleX(layer), layerScaleY(layer), damage);
        if (visible == null || layer.worldClip == null) return visible;
        WorldBounds clip = layer.worldClip;
        return projectedClip(clip.x(), clip.y(), clip.x() + clip.width(),
                clip.y() + clip.height(), ox, oy, layerScaleX(layer), layerScaleY(layer), visible);
    }

    private Rectangle projectedClip(long worldX, long worldY, long worldRight,
            long worldBottom, long ox, long oy, double scaleX, double scaleY, Rectangle damage) {
        double left = device(worldX, ox, scaleX);
        double top = device(worldY, oy, scaleY);
        double right = device(worldRight, ox, scaleX);
        double bottom = device(worldBottom, oy, scaleY);
        int x0 = (int) Math.max((double) damage.x, Math.floor(left));
        int y0 = (int) Math.max((double) damage.y, Math.floor(top));
        int x1 = (int) Math.min((double) damage.x + damage.width, Math.ceil(right));
        int y1 = (int) Math.min((double) damage.y + damage.height, Math.ceil(bottom));
        return x1 > x0 && y1 > y0 ? new Rectangle(x0, y0, x1 - x0, y1 - y0) : null;
    }

    /**
     * Begin coalescing damage for several layer edits. Every call requires a
     * matching endUpdate on the SWT UI thread, including exceptional paths.
     */
    public void beginUpdate() {
        checkWidget();
        batchDepth++;
    }

    /** Flush device-pixel damage and the pending native property frame when the outer batch ends. */
    public void endUpdate() {
        checkWidget();
        if (batchDepth == 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if (--batchDepth != 0) return;
        if (fullDamage) {
            redraw();
        } else {
            for (int i = 0; i < pendingDamage.rectCount(); i++) {
                // the retained composites were marked when each layer changed
                damage(pendingDamage.x(i), pendingDamage.y(i),
                        pendingDamage.width(i), pendingDamage.height(i));
            }
        }
        pendingDamage.clear();
        fullDamage = false;
        if (nativeSyncPending) applyNative();
    }

    private void invalidate(Layer layer) {
        if (layer.nativeLayer != 0) return; // not in the store: the window system shows it
        if (!layer.visible || layer.opacity == 0) return;
        Rectangle bounds = projectedClip(layer, getClientArea());
        if (bounds == null) return;
        if (retainedCount != 0) markRetained(indexOf(layer), bounds);
        if (batchDepth == 0) {
            damage(bounds.x, bounds.y, bounds.width, bounds.height);
        } else if (!fullDamage) {
            try {
                pendingDamage.add(bounds.x, bounds.y, bounds.width, bounds.height);
            } catch (IllegalArgumentException largeDeviceBounds) {
                fullDamage = true;
                pendingDamage.clear();
            }
        }
    }

    private void publishOrder() {
        orderDirty = true;
        spatialDirty = true;
    }

    private Layer[] orderSnapshot() {
        if (orderDirty) {
            paintOrder = Arrays.copyOf(stack, count);
            orderDirty = false;
        }
        return paintOrder;
    }

    private void reindex(int from, int to) {
        for (int i = from; i < to; i++) {
            stack[i].zIndex = i;
            retainedZ.set(i, stack[i].retained);
        }
    }

    private int indexOf(Layer layer) {
        int at = layer.zIndex;
        if (at >= 0 && at < count && stack[at] == layer) return at;
        SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        return -1;
    }

    private static void validateBounds(long x, long y, long width, long height) {
        if (width < 0 || height < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        try {
            Math.addExact(x, width);
            Math.addExact(y, height);
        } catch (ArithmeticException overflow) {
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }
    }

    private double device(long coordinate, long origin) {
        return device(coordinate, origin, zoom);
    }

    private static double device(long coordinate, long origin, double scale) {
        long difference = coordinate - origin;
        if (((coordinate ^ origin) & (coordinate ^ difference)) < 0) {
            return coordinate >= origin ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return (double) difference * scale;
    }

    private static int saturatedPixel(double value) {
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) Math.floor(value);
    }

    private static long floorLong(double value) {
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value <= Long.MIN_VALUE) return Long.MIN_VALUE;
        return (long) Math.floor(value);
    }

    private static long saturatedAdd(long base, long increment) {
        long result = base + increment;
        if (((base ^ result) & (increment ^ result)) < 0) {
            return increment > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }

    /* Exact cancellation before saturation: neither intermediate overflow may lose low bits. */
    private static long saturatedAddSubtract(long a, long b, long c) {
        try {
            return Math.subtractExact(Math.addExact(a, b), c);
        } catch (ArithmeticException overflow) {
            try {
                return Math.addExact(Math.subtractExact(a, c), b);
            } catch (ArithmeticException stillOverflow) {
                return b > c ? Long.MAX_VALUE : Long.MIN_VALUE;
            }
        }
    }

    private static int clipSource(double value, int bound, boolean roundUp) {
        if (value <= 0) return 0;
        if (value >= bound) return bound;
        return (int) (roundUp ? Math.ceil(value) : Math.floor(value));
    }

    private static Rectangle intersect(Rectangle a, Rectangle b) {
        long x0 = Math.max(a.x, b.x);
        long y0 = Math.max(a.y, b.y);
        long x1 = Math.min((long) a.x + a.width, (long) b.x + b.width);
        long y1 = Math.min((long) a.y + a.height, (long) b.y + b.height);
        if (x1 <= x0 || y1 <= y0) return null;
        return new Rectangle((int) x0, (int) y0, (int) (x1 - x0), (int) (y1 - y0));
    }
    /** Marks the whole view dirty, so the next paint recomposes every layer. */
    @Override
    public void redraw() {
        super.redraw();
        markDirty(getClientArea());
    }

    /** Marks the rectangle dirty, so the next paint recomposes the layers there. */
    @Override
    public void redraw(int x, int y, int width, int height, boolean all) {
        super.redraw(x, y, width, height, all);
        markDirty(new Rectangle(x, y, width, height));
    }

    /* the area may show anything new: the store and every retained composite are dirty there */
    private void markDirty(Rectangle area) {
        if (dirty == null || backBuffer == null) return; // the first paint composes everything
        Rectangle visible = intersect(area, getClientArea());
        if (visible == null) return;
        dirty.add(visible.x, visible.y, visible.width, visible.height);
        markRetained(0, visible);
    }

    /* a layer of Z index {@code z} changed inside {@code area}: the composites that include it are stale there */
    private void markRetained(int z, Rectangle area) {
        if (retainedCount == 0) return;
        for (int i = retainedZ.nextSetBit(Math.max(0, z)); i >= 0; i = retainedZ.nextSetBit(i + 1)) {
            if (stack[i].stale != null) stack[i].stale.add(area.x, area.y, area.width, area.height);
        }
    }

    /* the screen and the store are damaged inside the rectangle (the retained composites are marked by the caller) */
    private void damage(int x, int y, int width, int height) {
        super.redraw(x, y, width, height, false);
        if (dirty == null || backBuffer == null) return;
        Rectangle visible = intersect(new Rectangle(x, y, width, height), getClientArea());
        if (visible != null) dirty.add(visible.x, visible.y, visible.width, visible.height);
    }

    /** The background under the layers changes: every pixel is recomposed. */
    @Override
    public void setBackground(org.eclipse.swt.graphics.Color color) {
        super.setBackground(color);
        redraw();
    }

    /** The background under the layers changes: every pixel is recomposed. */
    @Override
    public void setBackgroundImage(Image image) {
        super.setBackgroundImage(image);
        redraw();
    }

    private void retireComposite(Image image) {
        if (activeBackingPaints == 0) {
            image.dispose();
            return;
        }
        if (retiredCompositeCount == retiredComposites.length) {
            retiredComposites = Arrays.copyOf(retiredComposites, retiredCompositeCount * 2);
        }
        retiredComposites[retiredCompositeCount++] = image;
    }

    private void disposeRetiredComposites() {
        for (int i = 0; i < retiredCompositeCount; i++) {
            Image image = retiredComposites[i];
            retiredComposites[i] = null;
            if (!image.isDisposed()) image.dispose();
        }
        retiredCompositeCount = 0;
    }

    private void disposeBackingImages() {
        if (backBuffer != null && !backBuffer.isDisposed()) backBuffer.dispose();
        if (scratch != null && !scratch.isDisposed()) scratch.dispose();
        backBuffer = scratch = null;
    }

    /* the store matches the client area; a new size starts with every pixel dirty */
    private void ensureBackBuffer(Rectangle area) {
        int width = Math.max(1, area.width), height = Math.max(1, area.height);
        if (backBuffer != null) {
            Rectangle size = backBuffer.getBounds();
            if (size.width == width && size.height == height) return;
            backBuffer.dispose();
            backBuffer = null;
        }
        if (scratch != null) {
            scratch.dispose();
            scratch = null;
        }
        for (Layer layer : orderSnapshot()) layer.releaseComposite();
        backBuffer = new Image(getDisplay(), width, height);
        dirty.clear();
        dirty.add(0, 0, width, height);
    }

    /*
     * a pan by a whole number of pixels moves the retained pixels, and the dirty region with them; only the band
     * that comes into view is dirty. Returns false when the whole view must be recomposed instead.
     */
    private boolean shiftBackBuffer(long previousX, long previousY) {
        // A painter can pan the scene. Recompose after releasing the selected
        // image GC rather than selecting the same backing image a second time.
        if (activeBackingPaints != 0 || backBuffer == null) return false;
        // at zoom 1 a pan moves every layer by exactly the same whole number of pixels; other zooms recompose
        if (zoom != 1.0) return false;
        Rectangle size = backBuffer.getBounds();
        long dx, dy;
        try {
            dx = Math.subtractExact(previousX, originX);
            dy = Math.subtractExact(previousY, originY);
        } catch (ArithmeticException overflow) {
            return false;
        }
        // abs(Long.MIN_VALUE) is negative: reject out-of-view pans before
        // narrowing a signed-long delta to an int pixel translation.
        if (dx <= -(long) size.width || dx >= size.width
                || dy <= -(long) size.height || dy >= size.height) return false;
        int sx = (int) dx, sy = (int) dy;
        shiftStore(backBuffer, dirty, sx, sy, size);
        for (Layer layer : orderSnapshot()) {
            if (layer.composite != null) shiftStore(layer.composite, layer.stale, sx, sy, size);
        }
        repairPinned(previousX, previousY, sx, sy, size);
        super.redraw();
        return true;
    }

    /*
     * after a blit by (sx, sy) from the origin (previousX, previousY): a pinned or sticky layer that did not move
     * with the world has a ghost where the blit put its old pixels, and its own place shows what was under the
     * ghost; both are recomposed
     */
    private void repairPinned(long previousX, long previousY, int sx, int sy, Rectangle size) {
        Rectangle view = new Rectangle(0, 0, size.width, size.height);
        Rectangle reach = new Rectangle(-Math.abs(sx), -Math.abs(sy), size.width + 2 * Math.abs(sx),
                size.height + 2 * Math.abs(sy));
        Layer[] frame = orderSnapshot();
        for (int z = 0; z < frame.length; z++) {
            Layer layer = frame[z];
            if (!viewRelative(layer) || layer.removed || !layer.visible || layer.opacity == 0 || layer.nativeLayer != 0) continue;
            Rectangle now = projectedClip(layer, reach);
            Rectangle old = projectedClip(layer, layerOrigin(layer, SWT.HORIZONTAL, previousX),
                    layerOrigin(layer, SWT.VERTICAL, previousY), reach);
            Rectangle moved = old == null ? null : new Rectangle(old.x + sx, old.y + sy, old.width, old.height);
            if (moved == null ? now == null : moved.equals(now)) continue; // it moved with everything else
            Rectangle place = now == null ? null : intersect(now, view);
            Rectangle ghost = moved == null ? null : intersect(moved, view);
            if (place != null) {
                dirty.add(place.x, place.y, place.width, place.height);
                markRetained(z, place);
            }
            if (ghost != null) {
                dirty.add(ghost.x, ghost.y, ghost.width, ghost.height);
                markRetained(z, ghost);
            }
        }
    }

    /* moves a store's pixels by (sx, sy) and its dirty region with them; the band that comes into view is dirty */
    private void shiftStore(Image store, ViewportDamageRegion region, int sx, int sy, Rectangle size) {
        GC gc = new GC(store);
        try {
            gc.copyArea(Math.max(0, -sx), Math.max(0, -sy), size.width - Math.abs(sx), size.height - Math.abs(sy),
                    Math.max(0, sx), Math.max(0, sy));
        } finally {
            gc.dispose();
        }
        int n = region.rectCount();
        int[] moved = growMovedWork(Math.multiplyExact(n, 4));
        for (int i = 0; i < n; i++) {
            moved[i * 4] = region.x(i) + sx;
            moved[i * 4 + 1] = region.y(i) + sy;
            moved[i * 4 + 2] = region.width(i);
            moved[i * 4 + 3] = region.height(i);
        }
        region.clear();
        Rectangle view = new Rectangle(0, 0, size.width, size.height);
        for (int i = 0; i < n; i++) {
            Rectangle r = intersect(new Rectangle(moved[i * 4], moved[i * 4 + 1], moved[i * 4 + 2], moved[i * 4 + 3]), view);
            if (r != null) region.add(r.x, r.y, r.width, r.height);
        }
        if (sx > 0) region.add(0, 0, sx, size.height);
        if (sx < 0) region.add(size.width + sx, 0, -sx, size.height);
        if (sy > 0) region.add(0, 0, size.width, sy);
        if (sy < 0) region.add(0, size.height + sy, size.width, -sy);
    }

    /* every control layer at its projected bounds */
    private void placeControls() {
        for (Layer layer : orderSnapshot()) placeControl(layer);
    }

    /*
     * a control layer's control at the layer's projected bounds, shown while the layer is visible, opaque at all and
     * inside the view; coordinates far outside the view are clamped (the control is then clipped by the view anyway)
     */
    private void placeControl(Layer layer) {
        Control control = layer.control;
        if (isDisposed() || layer.removed || control == null || control.isDisposed()) return;
        Rectangle area = getClientArea();
        long ox = layerOriginX(layer), oy = layerOriginY(layer);
        double left = device(layer.x, ox, layerScaleX(layer)), top = device(layer.y, oy, layerScaleY(layer));
        double right = device(layer.right, ox, layerScaleX(layer)), bottom = device(layer.bottom, oy, layerScaleY(layer));
        boolean shown = layer.visible && layer.opacity > 0 && right > area.x && left < (double) area.x + area.width
                && bottom > area.y && top < (double) area.y + area.height;
        if (shown) {
            int x = clampPixel(Math.floor(left), area.x - CONTROL_MARGIN, area.x + area.width);
            int y = clampPixel(Math.floor(top), area.y - CONTROL_MARGIN, area.y + area.height);
            int x1 = clampPixel(Math.ceil(right), area.x, area.x + area.width + CONTROL_MARGIN);
            int y1 = clampPixel(Math.ceil(bottom), area.y, area.y + area.height + CONTROL_MARGIN);
            control.setBounds(x, y, x1 - x, y1 - y);
        }
        // setBounds may dispatch application resize listeners.
        if (isDisposed() || layer.removed || control.isDisposed() || layer.control != control) return;
        if (control.getVisible() != shown) control.setVisible(shown);
    }

    /* the native Z order of the control layers' controls follows the layers' order, bottom to top */
    private void orderControls() {
        for (Layer layer : orderSnapshot()) {
            if (isDisposed()) return;
            if (!layer.removed && layer.control != null && !layer.control.isDisposed()) layer.control.moveAbove(null);
        }
    }

    /* how far outside the view a control's bounds may reach (a control larger than that is clamped to it) */
    private static final int CONTROL_MARGIN = 1 << 14;

    private static int clampPixel(double value, int low, int high) {
        return (int) Math.max(low, Math.min(high, value));
    }

    /* the native layer backend (null: none installed) and this viewport's composition, made on the first request */
    private NativeLayers nativeBackend;
    private long composition;
    private boolean nativeTried;
    private int nativeRequestCount;
    private long[] nativeHandles = new long[0];
    private float[] nativeProperties = new float[0];
    private float[] nativeSceneProperties = new float[0];
    private int[] nativeSceneFlags = new int[0];
    private long nativeCapabilities, nativeCapabilitiesComposition;
    private Shell nativeZoomShell;
    private final Listener nativeZoomListener = event -> {
        if (!isDisposed()) {
            nativeClientGeometryKnown = false;
            refreshNativeClientGeometry();
        }
    };
    private boolean nativeSyncPending;
    private int nativeFrameDepth;
    private boolean nativeRetrying;

    private boolean nativeAvailable() {
        if (isDisposed()) return false;
        if (!nativeTried) {
            nativeTried = true;
            nativeBackend = NativeLayers.platform();
            NativeLayers backend = nativeBackend;
            if (backend != null) {
                long created = backend.create(this);
                // A backend callback may dispose this viewport before returning.
                if (isDisposed()) {
                    if (created != 0) backend.dispose(created);
                    return false;
                }
                composition = created;
            }
        }
        if (composition == 0) return false;
        Shell shell = getShell();
        if (nativeZoomShell != shell) {
            if (nativeZoomShell != null && !nativeZoomShell.isDisposed()) {
                nativeZoomShell.removeListener(SWT.ZoomChanged, nativeZoomListener);
            }
            nativeZoomShell = shell;
            shell.addListener(SWT.ZoomChanged, nativeZoomListener);
        }
        if (nativeCapabilitiesComposition != composition) {
            long queriedComposition = composition;
            NativeLayers queriedBackend = nativeBackend;
            // A nested query sees the original conservative contract, never a
            // half-published feature set, and cannot recursively probe forever.
            nativeCapabilitiesComposition = queriedComposition;
            nativeCapabilities = 0;
            long supported = queriedBackend.capabilities(queriedComposition);
            if (isDisposed() || composition != queriedComposition || nativeBackend != queriedBackend) return false;
            nativeCapabilities = supported & (NativeLayers.AFFINE_2D | NativeLayers.LOCAL_RECT_CLIP | NativeLayers.DIRTY_UPLOAD);
        }
        return !isDisposed() && composition != 0;
    }

    /**
     * Explicitly retries the native compositor after a failed frame has
     * detached it and returned the layer pixels to SWT's painted backing
     * store. An application can call this on the SWT UI thread after a
     * driver/device reset, monitor change or another external recovery event.
     *
     * <p>No automatic retry timer or new native UI thread is introduced. A
     * failed retry leaves normal software painting authoritative, without
     * retrying again until the application requests it. The native backend
     * owns all newly recreated OS device/surface objects; each previously
     * requested and still eligible layer is uploaded from its borrowed
     * Image using the original native submission path.</p>
     *
     * <p>Returns false if there is no requested native layer, the widget is
     * inside a paint/update/native callback, the backend cannot create a
     * composition, or frame submission fails again. Returns true if a native
     * composition is available at return; a layer whose zoom, clipping or
     * blend mode makes it ineligible may still be software painted. Inspect
     * {@link Layer#isNative()} for an individual layer.</p>
     *
     * @return whether a native composition is available after the attempt
     */
    public boolean retryNativeComposition() {
        checkWidget();
        // Never re-enter create/dispose while a native frame, paint or model
        // batch is active; those own the current layer snapshot.
        if (nativeRetrying || nativeFrameDepth != 0 || activeBackingPaints != 0
                || batchDepth != 0 || nativeRequestCount <= 0) return false;
        if (composition != 0) return true;
        NativeLayers backend = nativeBackend != null ? nativeBackend : NativeLayers.platform();
        nativeTried = true; // failed attempts cannot enter an automatic loop
        if (backend == null) return false;
        nativeBackend = backend;
        long candidate;
        nativeRetrying = true;
        try {
            candidate = backend.create(this);
        } finally {
            nativeRetrying = false;
        }
        if (candidate == 0) return false;
        // Native creation may dispatch user callbacks, including dispose or
        // removing the final native request. Do not publish a stale owner.
        if (isDisposed() || nativeRequestCount <= 0 || composition != 0) {
            backend.dispose(candidate);
            return false;
        }
        composition = candidate;
        syncNative(); // uploads again; on failure reverts to software once
        return !isDisposed() && composition == candidate;
    }

    /* Shell DPI is local to this window; SWT autoscale policy determines how
       its logical Control coordinates map to native client pixels. ImageData
       remains the canonical 100% raster, including for dirty subregions. */
    private double nativePixelScale() {
        return DPIUtil.getZoomForAutoscaleProperty(getShell().getZoom()) / 100.0;
    }

    private static final double NATIVE_SCENE_LIMIT = 0x1.0p20;

    private static boolean nativeSceneValue(double value) {
        return Double.isFinite(value) && Math.abs(value) <= NATIVE_SCENE_LIMIT;
    }

    private boolean nativeNeedsScene(Layer layer, int width, int height, double pixelScale) {
        return pixelScale != 1.0 || layerScaleX(layer) != 1.0 || layerScaleY(layer) != 1.0
                || layer.right - layer.x != width || layer.bottom - layer.y != height || layer.worldClip != null;
    }

    /* Eligibility is feature-specific. No advanced call is made merely because
       a backend class can load. The unchanged legacy path remains the fallback. */
    private boolean nativeEligible(Layer layer, double pixelScale, Rectangle area) {
        if (isDisposed() || layer.nativePreparing || !layer.nativeRequested || layer.removed
                || layer.image == null || layer.image.isDisposed() || layer.blendMode != BLEND_NORMAL
                || layer.mask != null) return false;
        if (!nativeAvailable() || layer.removed || !layer.nativeRequested
                || layer.image == null || layer.image.isDisposed() || layer.blendMode != BLEND_NORMAL
                || layer.mask != null) return false;
        int width = layer.nativePixelWidth, height = layer.nativePixelHeight;
        if (layer.nativeLayer == 0) {
            Rectangle size = layer.image.getBounds();
            width = size.width;
            height = size.height;
        }
        if (width <= 0 || height <= 0 || layer.right <= layer.x || layer.bottom <= layer.y) return false;
        if (!Double.isFinite(pixelScale) || pixelScale <= 0) return false;
        if (!nativeNeedsScene(layer, width, height, pixelScale)) return true;
        boolean affine = pixelScale != 1.0 || layerScaleX(layer) != 1.0 || layerScaleY(layer) != 1.0
                || layer.right - layer.x != width || layer.bottom - layer.y != height;
        if (affine && (nativeCapabilities & NativeLayers.AFFINE_2D) == 0) return false;
        if (layer.worldClip != null && (nativeCapabilities & NativeLayers.LOCAL_RECT_CLIP) == 0) return false;
        return nativeSceneRecord(layer, width, height, pixelScale, area, null, 0) >= 0;
    }

    /*
     * native layers are made or dropped as their eligibility changes (their pixels leave or join the store), then
     * one native frame carries every native layer's order, offset, opacity and visibility
     */
    private void syncNative() {
        if (isDisposed() || composition == 0 && (nativeRequestCount == 0 || nativeTried)) return;
        // One window-DPI/client snapshot for the membership pass, not one
        // OS query per layer. Reentrant passes own their own local snapshot.
        double pixelScale = nativePixelScale();
        Rectangle area = getClientArea();
        // Ordinary Java-only edits do not materialize a lazy order snapshot.
        Layer[] frame = orderSnapshot();
        for (Layer layer : frame) {
            if (isDisposed()) return;
            if (layer.nativePreparing) continue;
            boolean eligible = nativeEligible(layer, pixelScale, area);
            if (isDisposed()) return;
            if (eligible && layer.nativeLayer == 0) {
                NativeLayers backend = nativeBackend;
                long owner = composition;
                Image source = layer.image;
                long handle = 0;
                layer.nativePreparing = true;
                try {
                    ImageData data = source.getImageData();
                    if (!nativeOwnerCurrent(layer, source, backend, owner)) continue;
                    handle = backend.addLayer(owner, data.width, data.height);
                    if (!nativeOwnerCurrent(layer, source, backend, owner)) continue;
                    int result = handle == 0 ? -1 : backend.upload(handle, premultiplied(data));
                    if (!nativeOwnerCurrent(layer, source, backend, owner)) continue;
                    if (result == 0) {
                        damageBounds(layer); // its pixels leave the store
                        layer.nativePixelWidth = data.width;
                        layer.nativePixelHeight = data.height;
                        layer.nativeLayer = handle;
                        handle = 0; // ownership transferred to the layer
                    }
                } finally {
                    layer.nativePreparing = false;
                    // Includes exceptional uploads and callbacks that retired
                    // the logical layer. A disposed composition already owns
                    // cleanup of every handle added to it.
                    if (handle != 0 && !isDisposed() && composition == owner && nativeBackend == backend) {
                        backend.removeLayer(owner, handle);
                    }
                }
                // Native callbacks can change projection or clip while the
                // image is uploaded. Recheck before publishing a scene frame.
                if (!isDisposed() && layer.nativeLayer != 0 && !nativeEligible(layer, pixelScale, area)) dropNative(layer);
            } else if (!eligible && layer.nativeLayer != 0) {
                dropNative(layer);
            }
        }
        if (!isDisposed()) applyNative();
    }

    private boolean nativeOwnerCurrent(Layer layer, Image image, NativeLayers backend, long owner) {
        return !isDisposed() && composition == owner && nativeBackend == backend && !layer.removed
                && layer.nativeRequested && layer.image == image && layer.mask == null && !image.isDisposed();
    }

    /* a layer stops being native: its native layer is freed and its pixels return to the store */
    private void dropNative(Layer layer) {
        long handle = layer.nativeLayer;
        if (handle == 0) return;
        layer.nativeLayer = 0; // detach before a native callback can reenter
        layer.nativePixelWidth = layer.nativePixelHeight = 0;
        nativeBackend.removeLayer(composition, handle);
        if (!isDisposed() && !layer.removed) damageBounds(layer);
    }

    private void damageBounds(Layer layer) {
        Rectangle bounds = projectedClip(layer, getClientArea());
        if (bounds != null) redraw(bounds.x, bounds.y, bounds.width, bounds.height, false);
    }

    /* Fill a source-image-pixel -> physical-client-pixel record. Intersect
       world clips before subtraction, so signed-long distant clips cannot
       overflow. Hidden layers submit a finite neutral record. No native call,
       callback or mutable snapshot escapes this calculation. */
    private int nativeSceneRecord(Layer layer, int width, int height, double pixelScale,
            Rectangle area, float[] properties, int at) {
        if (width <= 0 || height <= 0 || layer.right <= layer.x || layer.bottom <= layer.y) return -1;
        double sx = ((layer.right - layer.x) / (double) width) * layerScaleX(layer) * pixelScale;
        double sy = ((layer.bottom - layer.y) / (double) height) * layerScaleY(layer) * pixelScale;
        if (!nativeSceneValue(sx) || !nativeSceneValue(sy) || sx <= 0 || sy <= 0) return -1;
        long originX = layerOriginX(layer), originY = layerOriginY(layer);
        double scaleX = layerScaleX(layer), scaleY = layerScaleY(layer);
        double x = device(layer.x, originX, scaleX) * pixelScale;
        double y = device(layer.y, originY, scaleY) * pixelScale;
        long visibleLeft = layer.x, visibleTop = layer.y;
        long visibleRight = layer.right, visibleBottom = layer.bottom;
        int flags = 0;
        double left = 0, top = 0, right = 0, bottom = 0;
        if (layer.worldClip != null) {
            WorldBounds clip = layer.worldClip;
            long x0 = Math.max(layer.x, clip.x()), y0 = Math.max(layer.y, clip.y());
            long x1 = Math.min(layer.right, clip.x() + clip.width());
            long y1 = Math.min(layer.bottom, clip.y() + clip.height());
            visibleLeft = x0;
            visibleTop = y0;
            visibleRight = x1;
            visibleBottom = y1;
            if (x0 < x1 && y0 < y1) {
                left = (x0 - layer.x) / (double) (layer.right - layer.x) * width;
                top = (y0 - layer.y) / (double) (layer.bottom - layer.y) * height;
                right = (x1 - layer.x) / (double) (layer.right - layer.x) * width;
                bottom = (y1 - layer.y) / (double) (layer.bottom - layer.y) * height;
                if (!nativeSceneValue(left) || !nativeSceneValue(top)
                        || !nativeSceneValue(right) || !nativeSceneValue(bottom)) return -1;
                flags = NativeLayers.SCENE_CLIP;
            }
        }
        // Primitive culling avoids allocating a Rectangle for every retained
        // native layer on every frame. The caller owns the one client snapshot.
        boolean shown = area.width > 0 && area.height > 0 && layer.visible && layer.opacity > 0
                && visibleLeft < visibleRight && visibleTop < visibleBottom
                && device(visibleRight, originX, scaleX) > area.x
                && device(visibleLeft, originX, scaleX) < (double) area.x + area.width
                && device(visibleBottom, originY, scaleY) > area.y
                && device(visibleTop, originY, scaleY) < (double) area.y + area.height;
        if (shown && (!nativeSceneValue(x) || !nativeSceneValue(y))) return -1;
        if (properties != null) {
            Arrays.fill(properties, at, at + NativeLayers.SCENE_FLOATS, 0);
            properties[at] = shown ? (float) x : 0;
            properties[at + 1] = shown ? (float) y : 0;
            properties[at + 2] = layer.opacity / 255f;
            properties[at + 3] = shown ? 1 : 0;
            properties[at + 4] = shown ? (float) sx : 1;
            properties[at + 7] = shown ? (float) sy : 1;
            if (shown && flags != 0) {
                properties[at + 10] = (float) left;
                properties[at + 11] = (float) top;
                properties[at + 12] = (float) right;
                properties[at + 13] = (float) bottom;
            }
        }
        return shown ? flags : 0;
    }

    /* One retained native frame. Preserve legacy arrays and identity-reset
       behavior when no member needs the optional scene representation. */
    private boolean applyNative() {
        if (isDisposed() || composition == 0) return false;
        if (batchDepth != 0) {
            // deferred to endBatch: nothing committed yet, so a caller keeps its CPU repaint
            nativeSyncPending = true;
            return false;
        }
        nativeSyncPending = false;
        Layer[] frame = orderSnapshot();
        int n = 0;
        boolean scene = false;
        double pixelScale = nativePixelScale();
        for (Layer layer : frame) {
            if (layer.nativeLayer == 0) continue;
            n++;
            scene |= nativeNeedsScene(layer, layer.nativePixelWidth, layer.nativePixelHeight, pixelScale);
        }
        int stride = scene ? NativeLayers.SCENE_FLOATS : 4;
        long[] handles;
        float[] properties;
        int[] flags = null;
        if (nativeFrameDepth == 0) {
            int capacity = nativeHandles.length;
            if (capacity < n) {
                int next = capacity >= Integer.MAX_VALUE / (NativeLayers.SCENE_FLOATS * 2)
                        ? n : Math.max(4, capacity * 2);
                capacity = Math.max(n, next);
            }
            // Allocate every needed lane before publishing any replacements.
            long[] grownHandles = nativeHandles.length < capacity ? new long[capacity] : nativeHandles;
            float[] current = scene ? nativeSceneProperties : nativeProperties;
            float[] grownProperties = current.length < Math.multiplyExact(capacity, stride)
                    ? new float[Math.multiplyExact(capacity, stride)] : current;
            int[] grownFlags = scene && nativeSceneFlags.length < capacity ? new int[capacity] : nativeSceneFlags;
            nativeHandles = grownHandles;
            if (scene) {
                nativeSceneProperties = grownProperties;
                nativeSceneFlags = grownFlags;
                flags = grownFlags;
            } else {
                nativeProperties = grownProperties;
            }
            handles = grownHandles;
            properties = grownProperties;
        } else {
            // A synchronous backend callback must not overwrite its outer frame.
            handles = new long[n];
            properties = new float[Math.multiplyExact(n, stride)];
            if (scene) flags = new int[n];
        }
        long submittedComposition = composition;
        NativeLayers submittedBackend = nativeBackend;
        nativeFrameDepth++;
        try {
            Rectangle area = getClientArea();
            int i = 0;
            int result = 0;
            for (Layer layer : frame) {
                if (layer.nativeLayer == 0) continue;
                handles[i] = layer.nativeLayer;
                if (scene) {
                    int value = nativeSceneRecord(layer, layer.nativePixelWidth, layer.nativePixelHeight,
                            pixelScale, area, properties, i * stride);
                    if (value < 0) { result = -1; break; }
                    flags[i] = value;
                } else {
                    double x = device(layer.x, layerOriginX(layer), layerScaleX(layer));
                    double y = device(layer.y, layerOriginY(layer), layerScaleY(layer));
                    boolean shown = area.width > 0 && area.height > 0 && layer.visible && layer.opacity > 0
                            && Double.isFinite(x) && Double.isFinite(y)
                            && x < (double) area.x + area.width && y < (double) area.y + area.height
                            && x + (layer.right - layer.x) > area.x && y + (layer.bottom - layer.y) > area.y;
                    properties[i * 4] = shown ? (float) x : 0;
                    properties[i * 4 + 1] = shown ? (float) y : 0;
                    properties[i * 4 + 2] = layer.opacity / 255f;
                    properties[i * 4 + 3] = shown ? 1 : 0;
                }
                i++;
            }
            if (result == 0) {
                result = scene ? submittedBackend.applyScene(submittedComposition, handles, properties, flags, n)
                        : submittedBackend.apply(submittedComposition, handles, properties, n);
            }
            if (result != 0 && !isDisposed() && composition == submittedComposition
                    && nativeBackend == submittedBackend) {
                // A nested callback may have changed the live stack or already
                // disposed this composition. Detach it before any teardown call.
                composition = 0;
                nativeCapabilities = nativeCapabilitiesComposition = 0;
                nativeSyncPending = false;
                for (Layer layer : orderSnapshot()) {
                    if (layer.nativeLayer == 0) continue;
                    layer.nativeLayer = 0;
                    layer.nativePixelWidth = layer.nativePixelHeight = 0;
                    if (!layer.removed) damageBounds(layer);
                }
                // dispose owns every native layer, including any added by a callback.
                submittedBackend.dispose(submittedComposition);
            }
            return result == 0;
        } finally {
            nativeFrameDepth--;
        }
    }

    /* an image's pixels as premultiplied ARGB, row by row, with its alpha (alpha data, global alpha, transparent pixel) */
    private static int[] premultiplied(ImageData data) {
        int w = data.width, h = data.height;
        int[] out = new int[w * h], pixels = new int[w];
        byte[] alphas = new byte[w];
        for (int y = 0; y < h; y++) {
            data.getPixels(0, y, w, pixels, 0);
            if (data.alphaData != null) data.getAlphas(0, y, w, alphas, 0);
            for (int x = 0; x < w; x++) {
                int a = data.alphaData != null ? alphas[x] & 0xFF : data.alpha >= 0 ? data.alpha : 255;
                if (data.transparentPixel >= 0 && pixels[x] == data.transparentPixel) a = 0;
                org.eclipse.swt.graphics.RGB rgb = data.palette.getRGB(pixels[x]);
                out[y * w + x] = a << 24 | (rgb.red * a + 127) / 255 << 16 | (rgb.green * a + 127) / 255 << 8
                        | (rgb.blue * a + 127) / 255;
            }
        }
        return out;
    }
}
