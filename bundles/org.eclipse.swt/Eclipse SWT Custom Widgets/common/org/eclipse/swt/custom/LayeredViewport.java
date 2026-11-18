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
import org.eclipse.swt.internal.ViewportRuntime;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

/**
 * An opt-in, painted Z-stack over a long-coordinate, zoomable XY viewport.
 *
 * <p>Layer order is bottom-to-top. Unlike native Control.moveAbove/Below,
 * these layers are logical objects, not child controls. Painting is clipped
 * to the visible XY window; changing a layer only invalidates its projected
 * bounds. The array-backed stack is copied only on structural edits.</p>
 *
 * <p>Image layers receive opacity as one image-compositing operation.
 * Custom painter opacity applies to each GC operation, and therefore does
 * not yet provide isolated Photoshop-style group transparency. Non-normal
 * blend modes, arbitrary masks and native child-control composition are
 * deliberately outside this initial API.</p>
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

    /** Paint into the event GC; do not dispose it or retain it past the call. */
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
        private Image image;
        private LayerPainter painter;
        private LayerHitTester hitTester;

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
            invalidate(this);
        }

        public boolean isLocked() {
            checked();
            return locked;
        }

        /** Locked layers remain painted but cannot be returned by hitTest. */
        public void setLocked(boolean value) {
            checked();
            locked = value;
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
            int index = indexOf(this);
            System.arraycopy(stack, index + 1, stack, index, count - index - 1);
            stack[--count] = null;
            removed = true;
            image = null;
            painter = null;
            hitTester = null;
            publishOrder();
        }
    }

    private static final double MIN_ZOOM = 1.0 / 4096.0;
    private static final double MAX_ZOOM = 4096.0;

    private final ViewportRuntime viewportRuntime = new ViewportRuntime();
    private Layer[] stack = new Layer[8];
    private Layer[] paintOrder = new Layer[0];
    private int count;
    private long nextId = 1;
    private long originX, originY;
    private double zoom = 1.0;

    public LayeredViewport(Composite parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        viewportRuntime.initializeOrigin(0, 0);
        addPaintListener(event -> paintLayers(event.gc));
        addListener(SWT.Dispose, event -> {
            for (int i = 0; i < count; i++) {
                stack[i].removed = true;
                stack[i].image = null;
                stack[i].painter = null;
                stack[i].hitTester = null;
                stack[i] = null;
            }
            count = 0;
            paintOrder = new Layer[0];
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
        originX = x;
        originY = y;
        viewportRuntime.scrollTo((double) x, (double) y);
        redraw();
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

    /** Topmost visible, non-locked, nontransparent *bounding* layer. */
    public Layer hitTest(int deviceX, int deviceY) {
        checkWidget();
        if (!getClientArea().contains(deviceX, deviceY)) return null;
        WorldPoint point = toWorld(deviceX, deviceY);
        for (int i = paintOrder.length - 1; i >= 0; i--) {
            Layer layer = paintOrder[i];
            if (layer.removed || !layer.visible || layer.locked || layer.opacity == 0) continue;
            if (point.x < layer.x || point.x >= layer.right
                    || point.y < layer.y || point.y >= layer.bottom) continue;
            if (layer.hitTester == null
                    || layer.hitTester.contains(layer, point.x, point.y)) return layer;
        }
        return null;
    }

    private void paintLayers(GC gc) {
        Rectangle damage = intersect(gc.getClipping(), getClientArea());
        if (damage == null) return;
        int originalAlpha = gc.getAlpha();
        Rectangle originalClip = gc.getClipping();
        Layer[] frame = paintOrder;
        try {
            for (Layer layer : frame) {
                if (layer.removed || !layer.visible || layer.opacity == 0) continue;
                Rectangle tile = projectedClip(layer, damage);
                if (tile == null) continue;
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
            gc.setAlpha(originalAlpha);
            gc.setClipping(originalClip);
        }
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
        double left = device(layer.x, originX);
        double top = device(layer.y, originY);
        double right = device(layer.right, originX);
        double bottom = device(layer.bottom, originY);
        int x0 = (int) Math.max((double) damage.x, Math.floor(left));
        int y0 = (int) Math.max((double) damage.y, Math.floor(top));
        int x1 = (int) Math.min((double) damage.x + damage.width, Math.ceil(right));
        int y1 = (int) Math.min((double) damage.y + damage.height, Math.ceil(bottom));
        return x1 > x0 && y1 > y0 ? new Rectangle(x0, y0, x1 - x0, y1 - y0) : null;
    }

    private void invalidate(Layer layer) {
        if (!layer.visible || layer.opacity == 0) return;
        Rectangle bounds = projectedClip(layer, getClientArea());
        if (bounds != null) redraw(bounds.x, bounds.y, bounds.width, bounds.height, false);
    }

    private void publishOrder() {
        paintOrder = Arrays.copyOf(stack, count);
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
}
