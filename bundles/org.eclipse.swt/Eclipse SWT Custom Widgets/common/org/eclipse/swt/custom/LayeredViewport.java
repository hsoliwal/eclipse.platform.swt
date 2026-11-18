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
import org.eclipse.swt.internal.render.LayerArgbCompositor;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

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
 * do not darken each other where they overlap. Non-normal blend modes,
 * arbitrary masks and native child-control composition are deliberately
 * outside this API.</p>
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

    /** Snapshot for painting one visible layer. The clip uses widget pixels. */
    public record PaintContext(Layer layer, Rectangle clip, long originX,
            long originY, double zoom) { }

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

    /**
     * Stable logical handle. Removing the layer invalidates this handle,
     * but it does not dispose an Image supplied by its application.
     */
    public final class Layer {
        private final long id;
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
        /* the blend mode (BLEND_*) and, for a blended image layer, its pixels read once (null until needed) */
        private int blendMode = BLEND_NORMAL;
        private ImageData blendSource;
        /* retained: this layer's composite (it and everything below it) kept in its own image, with the pixels of
           that image that no longer match the layers */
        private boolean retained;
        private Image composite;
        private ViewportDamageRegion stale;
        private Image image;
        private LayerPainter painter;
        private LayerHitTester hitTester;
        private LayerPointerListener pointerListener;
        private WorldBounds worldClip;
        /* SWT.HORIZONTAL and/or SWT.VERTICAL: the axes on which the layer is measured from the view's origin */
        private int pinned;
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
            releaseComposite();
        }

        /**
         * The layer's own content changed (its painter would now paint something else): its pixels are
         * recomposed at the next paint, from the retained composite below it where there is one. Layers below
         * it are not repainted.
         */
        public void redraw() {
            checked();
            blendSource = null; // the image's pixels may have changed
            if (nativeLayer != 0) {
                // a native layer's pixels are the window system's: send them again
                dropNative(this);
                syncNative();
                return;
            }
            invalidate(this);
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
         * while its image is drawn one to one (zoom 1, world size the image's size, no world clip, 100% device
         * zoom) and a backend exists; otherwise, and when that stops being true, it is painted as before.
         *
         * @return whether the layer is native now
         */
        public boolean setNative(boolean value) {
            checked();
            nativeRequested = value;
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
            invalidate(this);
            dropNative(this); // new pixels
            syncNative();
        }

        public void setPainter(LayerPainter value) {
            checked();
            invalidate(this);
            painter = value;
            image = null;
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
            removed = true;
            if (retained) retainedCount--;
            releaseComposite();
            dropNative(this);
            control = null; // the control stays the application's, where it was
            image = null;
            painter = null;
            hitTester = null;
            pointerListener = null;
            worldClip = null;
            publishOrder();
        }
    }

    /**
     * Blend modes of a layer ({@link Layer#setBlendMode(int)}): normal, then the separable modes of W3C Compositing
     * and Blending Level 1 (the layer modes of GIMP, Inkscape and Photoshop).
     */
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

    public LayeredViewport(Composite parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        viewportRuntime.initializeOrigin(0, 0);
        dirty = new ViewportDamageRegion();
        addPaintListener(event -> paintLayers(event.gc));
        addListener(SWT.Resize, event -> placeControls());
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
            if (composition != 0) nativeBackend.dispose(composition);
            composition = 0;
            for (int i = 0; i < count; i++) stack[i].nativeLayer = 0;
            // A painter may dispose the widget while one of these images has a
            // selected GC. The active paint closes that GC before the images.
            if (activeBackingPaints == 0) disposeBackingImages();
            for (int i = 0; i < count; i++) {
                stack[i].removed = true;
                stack[i].releaseComposite();
                stack[i].control = null;
                stack[i].image = null;
                stack[i].painter = null;
                stack[i].hitTester = null;
                stack[i].pointerListener = null;
                stack[i].worldClip = null;
                stack[i] = null;
            }
            count = retainedCount = 0;
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
        stack[count++] = layer;
        publishOrder();
        invalidate(layer);
        return layer;
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

    private long worldX(int deviceX) {
        return saturatedAdd(originX, floorLong(deviceX / zoom));
    }

    private long worldY(int deviceY) {
        return saturatedAdd(originY, floorLong(deviceY / zoom));
    }

    private Layer queryHit(int deviceX, int deviceY, boolean withPointerListener) {
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
                        && (!withPointerListener || layer.pointerListener != null)) return layer;
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
                if (layer.image != null && layer.blendMode != BLEND_NORMAL) {
                    // the scope restores the clip and alpha the blend sets, as for every other layer
                    try (ViewportGcProxy state = ViewportGcProxy.wrap(back[0])) {
                        state.clip(tile).alpha(255);
                        if (!layer.image.isDisposed()) paintBlended(back, layer, tile);
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
        return layer.pinned != 0 || layer.sticky != 0;
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
        if ((layer.pinned & axis) != 0) return 0;
        if (layer.sticky != axis) return origin;
        long start = axis == SWT.HORIZONTAL ? layer.x : layer.y, end = axis == SWT.HORIZONTAL ? layer.right : layer.bottom;
        long stuck = Math.min(saturatedAdd(origin, layer.stickyInset), saturatedAdd(layer.stickyLimit, start - end));
        return stuck > start ? saturatedAdd(origin, start - stuck) : origin;
    }

    /* a device point's world coordinates as a layer sees them */
    private long layerX(Layer layer, int deviceX) {
        return saturatedAdd(layerOriginX(layer), floorLong(deviceX / zoom));
    }

    private long layerY(Layer layer, int deviceY) {
        return saturatedAdd(layerOriginY(layer), floorLong(deviceY / zoom));
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
        long ox = layerOriginX(layer), oy = layerOriginY(layer);
        double left = device(layer.x, ox), top = device(layer.y, oy);
        double width = device(layer.right, ox) - left, height = device(layer.bottom, oy) - top;
        if (!(width > 0) || !(height > 0) || source.width == 0 || source.height == 0) return;
        Image under = new Image(getDisplay(), tile.width, tile.height);
        try {
            back[0].copyArea(under, tile.x, tile.y);
            ImageData below = under.getImageData();
            ImageDataArgb store = new ImageDataArgb(below); // whole-row conversion, no per-pixel RGB objects
            ImageDataArgb sourceColors = new ImageDataArgb(source);
            int rowsPerBatch = Math.max(1, BLEND_BATCH_PIXELS / tile.width);
            int capacity = Math.min(rowsPerBatch, tile.height) * tile.width;
            int[] dst = new int[capacity], src = new int[capacity];
            for (int startRow = 0; startRow < tile.height; startRow += rowsPerBatch) {
                int rows = Math.min(rowsPerBatch, tile.height - startRow);
                for (int row = 0; row < rows; row++) {
                    int j = startRow + row, base = row * tile.width;
                    int sy = clipSource((tile.y + j + 0.5 - top) / height * source.height, source.height - 1, false);
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
                    }
                }
                LayerArgbCompositor.composite(dst, 0, src, 0, rows * tile.width,
                        layer.opacity, BLEND_MODES[layer.blendMode]);
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
        Rectangle srcBounds = layer.image.getBounds();
        if (srcBounds.width == 0 || srcBounds.height == 0) return;

        long ox = layerOriginX(layer), oy = layerOriginY(layer);
        double left = device(layer.x, ox);
        double top = device(layer.y, oy);
        double right = device(layer.right, ox);
        double bottom = device(layer.bottom, oy);
        double width = right - left;
        double height = bottom - top;
        if (width <= 0 || height <= 0) return;

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
                gc.drawImage(layer.image, srcBounds.x, srcBounds.y,
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
            gc.drawImage(layer.image, srcBounds.x + sx0, srcBounds.y + sy0,
                    sx1 - sx0, sy1 - sy0, tile.x, tile.y, tile.width, tile.height);
        }
    }

    private Rectangle projectedClip(Layer layer, Rectangle damage) {
        return projectedClip(layer, layerOriginX(layer), layerOriginY(layer), damage);
    }

    private Rectangle projectedClip(Layer layer, long ox, long oy, Rectangle damage) {
        Rectangle visible = projectedClip(layer.x, layer.y, layer.right, layer.bottom, ox, oy, damage);
        if (visible == null || layer.worldClip == null) return visible;
        WorldBounds clip = layer.worldClip;
        return projectedClip(clip.x(), clip.y(), clip.x() + clip.width(),
                clip.y() + clip.height(), ox, oy, visible);
    }

    private Rectangle projectedClip(long worldX, long worldY, long worldRight,
            long worldBottom, long ox, long oy, Rectangle damage) {
        double left = device(worldX, ox);
        double top = device(worldY, oy);
        double right = device(worldRight, ox);
        double bottom = device(worldBottom, oy);
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

    /** Flush the bounded device-pixel damage region when the outer batch ends. */
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

    private int indexOf(Layer layer) {
        if (count > 0 && stack[count - 1] == layer) return count - 1;
        for (int i = 0; i < count; i++) if (stack[i] == layer) return i;
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
        long difference = coordinate - origin;
        if (((coordinate ^ origin) & (coordinate ^ difference)) < 0) {
            return coordinate >= origin ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return (double) difference * zoom;
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
        for (int i = Math.max(0, z); i < count; i++) {
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
        if (Math.abs(dx) >= size.width || Math.abs(dy) >= size.height) return false;
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
    private static void shiftStore(Image store, ViewportDamageRegion region, int sx, int sy, Rectangle size) {
        GC gc = new GC(store);
        try {
            gc.copyArea(Math.max(0, -sx), Math.max(0, -sy), size.width - Math.abs(sx), size.height - Math.abs(sy),
                    Math.max(0, sx), Math.max(0, sy));
        } finally {
            gc.dispose();
        }
        int n = region.rectCount();
        int[] moved = new int[n * 4];
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
        double left = device(layer.x, ox), top = device(layer.y, oy);
        double right = device(layer.right, ox), bottom = device(layer.bottom, oy);
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

    private boolean nativeAvailable() {
        if (!nativeTried) {
            nativeTried = true;
            nativeBackend = NativeLayers.platform();
            if (nativeBackend != null) composition = nativeBackend.create(this);
        }
        return composition != 0;
    }

    /* an image drawn one to one (zoom 1, world size the image's size, no clip, 100% device zoom) with a backend */
    private boolean nativeEligible(Layer layer) {
        if (!layer.nativeRequested || layer.removed || layer.image == null || layer.image.isDisposed()) return false;
        if (zoom != 1.0 || layer.worldClip != null || layer.blendMode != BLEND_NORMAL
                || DPIUtil.getDeviceZoom() != 100) return false;
        Rectangle size = layer.image.getBounds();
        return layer.right - layer.x == size.width && layer.bottom - layer.y == size.height && nativeAvailable();
    }

    /*
     * native layers are made or dropped as their eligibility changes (their pixels leave or join the store), then
     * one native frame carries every native layer's order, offset, opacity and visibility
     */
    private void syncNative() {
        if (!nativeTried) {
            boolean requested = false;
            for (Layer layer : paintOrder) requested |= layer.nativeRequested;
            if (!requested) return;
        }
        for (Layer layer : paintOrder) {
            boolean eligible = nativeEligible(layer);
            if (eligible && layer.nativeLayer == 0) {
                Rectangle size = layer.image.getBounds();
                long handle = nativeBackend.addLayer(composition, size.width, size.height);
                if (handle != 0 && nativeBackend.upload(handle, premultiplied(layer.image.getImageData())) == 0) {
                    damageBounds(layer); // its pixels leave the store
                    layer.nativeLayer = handle;
                } else if (handle != 0) {
                    nativeBackend.removeLayer(composition, handle);
                }
            } else if (!eligible && layer.nativeLayer != 0) {
                dropNative(layer);
            }
        }
        applyNative();
    }

    /* a layer stops being native: its native layer is freed and its pixels return to the store */
    private void dropNative(Layer layer) {
        if (layer.nativeLayer == 0) return;
        nativeBackend.removeLayer(composition, layer.nativeLayer);
        layer.nativeLayer = 0;
        if (!layer.removed) damageBounds(layer);
    }

    private void damageBounds(Layer layer) {
        Rectangle bounds = projectedClip(layer, getClientArea());
        if (bounds != null) redraw(bounds.x, bounds.y, bounds.width, bounds.height, false);
    }

    /* one native frame: the native layers in Z order with their offset, opacity and visibility */
    private void applyNative() {
        if (composition == 0) return;
        int n = 0;
        for (Layer layer : paintOrder) if (layer.nativeLayer != 0) n++;
        long[] handles = new long[n];
        float[] props = new float[n * 4];
        int i = 0;
        for (Layer layer : paintOrder) {
            if (layer.nativeLayer == 0) continue;
            handles[i] = layer.nativeLayer;
            props[i * 4] = (float) device(layer.x, layerOriginX(layer));
            props[i * 4 + 1] = (float) device(layer.y, layerOriginY(layer));
            props[i * 4 + 2] = layer.opacity / 255f;
            props[i * 4 + 3] = layer.visible && layer.opacity > 0 ? 1 : 0;
            i++;
        }
        nativeBackend.apply(composition, handles, props, n);
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
