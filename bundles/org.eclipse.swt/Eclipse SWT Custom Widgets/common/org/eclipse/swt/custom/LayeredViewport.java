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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.LayerRectIndex;
import org.eclipse.swt.internal.ViewportDamageRegion;
import org.eclipse.swt.internal.ViewportRuntime;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

/**
 * An opt-in, painted Z-stack over a long-coordinate, zoomable XY viewport.
 *
 * <p>Layer order is bottom-to-top. Unlike native Control.moveAbove/Below,
 * these layers are logical objects, not child controls. Painting is clipped
 * to the visible XY window; changing a layer only invalidates its projected
 * bounds. The array-backed stack is copied only on structural edits.</p>
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
            releaseComposite();
        }

        /**
         * The layer's own content changed (its painter would now paint something else): its pixels are
         * recomposed at the next paint, from the retained composite below it where there is one. Layers below
         * it are not repainted.
         */
        public void redraw() {
            checked();
            invalidate(this);
        }

        private void releaseComposite() {
            if (composite != null) composite.dispose();
            composite = null;
            stale = null;
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
            invalidate(this);
        }

        public void setPainter(LayerPainter value) {
            checked();
            invalidate(this);
            painter = value;
            image = null;
            invalidate(this);
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
            releaseComposite();
            image = null;
            painter = null;
            hitTester = null;
            pointerListener = null;
            worldClip = null;
            publishOrder();
        }
    }

    private static final double MIN_ZOOM = 1.0 / 4096.0;
    private static final double MAX_ZOOM = 4096.0;

    private final ViewportRuntime viewportRuntime = new ViewportRuntime();
    private Layer[] stack = new Layer[8];
    private Layer[] paintOrder = new Layer[0];
    private final LayerRectIndex spatialIndex = new LayerRectIndex();
    private final LayerRectIndex.Bounds spatialSource = new LayerRectIndex.Bounds() {
        @Override public int size() { return paintOrder.length; }
        @Override public boolean active(int at) {
            Layer layer = paintOrder[at];
            if (layer.removed || !layer.visible || layer.opacity == 0) return false;
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
    private boolean spatialDirty = true;
    private boolean fullDamage;
    private int batchDepth;
    private int activePaintFrames;
    private int activeHitQueries;
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
            if (backBuffer != null) backBuffer.dispose();
            if (scratch != null) scratch.dispose();
            backBuffer = scratch = null;
            for (int i = 0; i < count; i++) {
                stack[i].removed = true;
                stack[i].releaseComposite();
                stack[i].image = null;
                stack[i].painter = null;
                stack[i].hitTester = null;
                stack[i].pointerListener = null;
                stack[i].worldClip = null;
                stack[i] = null;
            }
            count = 0;
            pointerListenerCount = 0;
            clearPointerCapture();
            paintOrder = new Layer[0];
            spatialIndex.clear();
            Arrays.fill(paintCandidates, null);
            Arrays.fill(hitCandidates, null);
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
        return paintOrder.clone();
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
        return queryHit(deviceX, deviceY, worldX(deviceX), worldY(deviceY), false);
    }

    private long worldX(int deviceX) {
        return saturatedAdd(originX, floorLong(deviceX / zoom));
    }

    private long worldY(int deviceY) {
        return saturatedAdd(originY, floorLong(deviceY / zoom));
    }

    private Layer queryHit(int deviceX, int deviceY, long worldX, long worldY,
            boolean withPointerListener) {
        if (!getClientArea().contains(deviceX, deviceY)) return null;
        ensureSpatialIndex();
        int candidates = spatialIndex.query(worldX, worldY,
                saturatedAdd(worldX, 1), saturatedAdd(worldY, 1));
        // Hit testers may re-enter the scene; capture handles before callbacks.
        Layer[] hits = activeHitQueries == 0
                ? growHitCandidates(candidates) : new Layer[candidates];
        for (int i = 0; i < candidates; i++) {
            hits[i] = paintOrder[spatialIndex.resultAt(i)];
        }
        activeHitQueries++;
        try {
            for (int i = candidates - 1; i >= 0; i--) {
                Layer layer = hits[i];
                if (layer.removed || layer.locked || !layer.visible || layer.opacity == 0
                        || (withPointerListener && layer.pointerListener == null)) continue;
                if ((layer.hitTester == null
                        || layer.hitTester.contains(layer, worldX, worldY))
                        && !layer.removed && !layer.locked && layer.visible
                        && layer.opacity != 0
                        && (!withPointerListener || layer.pointerListener != null)) return layer;
            }
            return null;
        } finally {
            Arrays.fill(hits, 0, candidates, null);
            activeHitQueries--;
        }
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
        long worldX = worldX(event.x), worldY = worldY(event.y);
        Layer layer = byCapture ? capturedLayer
                : queryHit(event.x, event.y, worldX, worldY, true);
        if (layer == null || layer.removed) return;
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
        Rectangle area = getClientArea();
        Rectangle damage = intersect(gc.getClipping(), area);
        if (damage == null) return;
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
                // Consume the captured damage BEFORE running user painters.
                // New invalidations triggered by those callbacks must survive
                // and be recomposed in a later SWT paint event.
                dirty.remove(damage.x, damage.y, damage.width, damage.height);
                GC[] back = { null };
                boolean complete = false;
                try {
                    back[0] = new GC(backBuffer);
                    for (int i = 0; i < staleCount; i++) {
                        int offset = i * 4;
                        compose(back, new Rectangle(stale[offset], stale[offset + 1],
                                stale[offset + 2], stale[offset + 3]));
                    }
                    complete = true;
                } finally {
                    try {
                        if (!complete && !isDisposed()) {
                            // Rendering failed: do not drop any captured
                            // damage just because it was consumed up front.
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
            if (!isDisposed()) {
                gc.drawImage(backBuffer, damage.x, damage.y, damage.width, damage.height,
                        damage.x, damage.y, damage.width, damage.height);
            }
        } finally {
            staleDepth--;
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
        Layer[] frame = paintOrder;
        int below = -1;
        for (int z = 0; z < frame.length; z++) {
            Layer layer = frame[z];
            if (!layer.retained || layer.removed) continue;
            refresh(layer, below, z, damage);
            below = z;
        }
        back[0].setClipping(damage);
        start(back[0], below, damage);
        composeLayers(back, backBuffer, damage, below + 1, frame.length);
        back[0].setClipping((Rectangle) null);
    }

    /* recomposes the stale pixels of retained layer {@code z} inside {@code damage}, from the composite below it */
    private void refresh(Layer layer, int below, int z, Rectangle damage) {
        Rectangle size = backBuffer.getBounds();
        if (layer.composite == null) {
            layer.composite = new Image(getDisplay(), size.width, size.height);
            layer.stale = new ViewportDamageRegion();
            layer.stale.add(0, 0, size.width, size.height);
        }
        int n = layer.stale.rectCount();
        if (n == 0) return;
        int[] rects = new int[n * 4];
        int k = 0;
        for (int i = 0; i < n; i++) {
            Rectangle r = intersect(new Rectangle(layer.stale.x(i), layer.stale.y(i), layer.stale.width(i),
                    layer.stale.height(i)), damage);
            if (r == null) continue;
            rects[k * 4] = r.x;
            rects[k * 4 + 1] = r.y;
            rects[k * 4 + 2] = r.width;
            rects[k * 4 + 3] = r.height;
            k++;
        }
        if (k == 0) return;
        GC[] gc = { new GC(layer.composite) };
        try {
            for (int i = 0; i < k; i++) {
                Rectangle r = new Rectangle(rects[i * 4], rects[i * 4 + 1], rects[i * 4 + 2], rects[i * 4 + 3]);
                gc[0].setClipping(r);
                start(gc[0], below, r);
                composeLayers(gc, layer.composite, r, below + 1, z + 1);
            }
        } finally {
            gc[0].dispose();
        }
        layer.stale.remove(damage.x, damage.y, damage.width, damage.height);
    }

    /* the pixels a composition starts from: the retained composite of layer {@code below}, or the background */
    private void start(GC gc, int below, Rectangle r) {
        if (below < 0) {
            drawBackground(gc, r.x, r.y, r.width, r.height);
        } else {
            gc.drawImage(paintOrder[below].composite, r.x, r.y, r.width, r.height, r.x, r.y, r.width, r.height);
        }
    }

    /* the layers the spatial index finds under {@code damage}, bottom to top, into the store's GC */
    /* the layers of Z indices {@code from} to {@code to - 1} the spatial index finds under {@code damage}, into
       {@code target} through {@code back[0]} */
    private void composeLayers(GC[] back, Image target, Rectangle damage, int from, int to) {
        int originalAlpha = back[0].getAlpha();
        Rectangle originalClip = back[0].getClipping();
        ensureSpatialIndex();
        long x0 = saturatedAdd(originX, saturatedAdd(floorLong(damage.x / zoom), -1));
        long y0 = saturatedAdd(originY, saturatedAdd(floorLong(damage.y / zoom), -1));
        long x1 = saturatedAdd(originX, saturatedAdd(ceilLong(
                ((double) damage.x + damage.width) / zoom), 1));
        long y1 = saturatedAdd(originY, saturatedAdd(ceilLong(
                ((double) damage.y + damage.height) / zoom), 1));
        int selected = spatialIndex.query(x0, y0, x1, y1);
        Layer[] candidates = activePaintFrames == 0
                ? growPaintCandidates(selected) : new Layer[selected];
        Layer[] frame = paintOrder;
        int kept = 0;
        for (int i = 0; i < selected; i++) {
            int z = spatialIndex.resultAt(i);
            if (z >= from && z < to) candidates[kept++] = frame[z];
        }
        selected = kept;
        activePaintFrames++;
        try {
            for (int i = 0; i < selected; i++) {
                if (isDisposed()) break;
                Layer layer = candidates[i];
                if (layer.removed || !layer.visible || layer.opacity == 0) continue;
                Rectangle tile = projectedClip(layer, damage);
                if (tile == null) continue;
                if (layer.image == null && layer.painter != null && layer.opacity < 255) {
                    paintIsolated(back, target, layer, tile);
                    back[0].setAlpha(originalAlpha);
                    back[0].setClipping(originalClip);
                    continue;
                }
                GC gc = back[0];
                gc.setClipping(tile);
                gc.setAlpha(layer.opacity);
                try {
                    if (layer.image != null) {
                        if (!layer.image.isDisposed()) paintImage(gc, layer, tile);
                    } else if (layer.painter != null) {
                        layer.painter.paint(gc,
                                new PaintContext(layer, tile, originX, originY, zoom));
                    }
                } finally {
                    gc.setAlpha(originalAlpha);
                    gc.setClipping(originalClip);
                }
            }
        } finally {
            Arrays.fill(candidates, 0, selected, null);
            activePaintFrames--;
            back[0].setAlpha(originalAlpha);
            back[0].setClipping(originalClip);
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
        if (spatialDirty) {
            spatialIndex.rebuild(spatialSource);
            spatialDirty = false;
        }
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
    private void paintIsolated(GC[] back, Image target, Layer layer, Rectangle tile) {
        Rectangle size = backBuffer.getBounds();
        if (scratch == null) scratch = new Image(getDisplay(), size.width, size.height);
        back[0].dispose();
        GC copy = new GC(scratch);
        try {
            copy.drawImage(target, tile.x, tile.y, tile.width, tile.height, tile.x, tile.y, tile.width, tile.height);
            copy.setClipping(tile);
            layer.painter.paint(copy, new PaintContext(layer, tile, originX, originY, zoom));
        } finally {
            copy.dispose();
            back[0] = new GC(target);
        }
        back[0].setClipping(tile);
        back[0].setAlpha(layer.opacity);
        back[0].drawImage(scratch, tile.x, tile.y, tile.width, tile.height, tile.x, tile.y, tile.width, tile.height);
        back[0].setAlpha(255);
    }

    private void paintImage(GC gc, Layer layer, Rectangle tile) {
        Rectangle srcBounds = layer.image.getBounds();
        if (srcBounds.width == 0 || srcBounds.height == 0) return;

        double left = device(layer.x, originX);
        double top = device(layer.y, originY);
        double right = device(layer.right, originX);
        double bottom = device(layer.bottom, originY);
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
        Rectangle visible = projectedClip(layer.x, layer.y, layer.right, layer.bottom, damage);
        if (visible == null || layer.worldClip == null) return visible;
        WorldBounds clip = layer.worldClip;
        return projectedClip(clip.x(), clip.y(), clip.x() + clip.width(),
                clip.y() + clip.height(), visible);
    }

    private Rectangle projectedClip(long worldX, long worldY, long worldRight,
            long worldBottom, Rectangle damage) {
        double left = device(worldX, originX);
        double top = device(worldY, originY);
        double right = device(worldRight, originX);
        double bottom = device(worldBottom, originY);
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
        if (!layer.visible || layer.opacity == 0) return;
        Rectangle bounds = projectedClip(layer, getClientArea());
        if (bounds == null) return;
        markRetained(indexOf(layer), bounds);
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
        paintOrder = Arrays.copyOf(stack, count);
        spatialDirty = true;
    }

    private int indexOf(Layer layer) {
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
        Layer[] frame = paintOrder;
        for (int i = Math.max(0, z); i < frame.length; i++) {
            if (frame[i].stale != null) frame[i].stale.add(area.x, area.y, area.width, area.height);
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

    /* the store matches the client area; a new size starts with every pixel dirty */
    private void ensureBackBuffer(Rectangle area) {
        int width = Math.max(1, area.width), height = Math.max(1, area.height);
        if (backBuffer != null) {
            Rectangle size = backBuffer.getBounds();
            if (size.width == width && size.height == height) return;
            backBuffer.dispose();
        }
        if (scratch != null) {
            scratch.dispose();
            scratch = null;
        }
        for (Layer layer : paintOrder) layer.releaseComposite();
        backBuffer = new Image(getDisplay(), width, height);
        dirty.clear();
        dirty.add(0, 0, width, height);
    }

    /*
     * a pan by a whole number of pixels moves the retained pixels, and the dirty region with them; only the band
     * that comes into view is dirty. Returns false when the whole view must be recomposed instead.
     */
    private boolean shiftBackBuffer(long previousX, long previousY) {
        if (backBuffer == null) return false;
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
        for (Layer layer : paintOrder) {
            if (layer.composite != null) shiftStore(layer.composite, layer.stale, sx, sy, size);
        }
        super.redraw();
        return true;
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
}
