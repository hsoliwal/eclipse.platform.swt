/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.custom;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;

/**
 * An opt-in native-control panel sliding on the existing {@link LayeredViewport}
 * Z stack. No second Canvas, backing store, compositor or child-Control proxy is
 * created. The caller retains ownership of the supplied native Control.
 *
 * <p>On every edge the layer is pinned to both viewport axes. Native child
 * controls are above the painted SWT plane; their order relative to
 * DirectComposition image visuals is platform-specific and requires pixel
 * verification. Controls cannot be interleaved with painted layers or alpha-blended.</p>
 */
public final class LayeredSlidePanel implements AutoCloseable {
    private static final int FRAME_MILLIS = 16;
    private static final int MAX_EXTENT = 1 << 14;
    private static final int MAX_DURATION_MILLIS = 10_000;
    private final LayeredViewport viewport;
    private final Control control;
    private final LayeredViewport.Layer layer;
    private final int edge, collapsedExtent, expandedExtent;
    private final Listener resized, parentDisposed, controlDisposed;
    private int shownExtent, durationMillis = 200, animationEpoch;
    private boolean expanded, closed;

    /**
     * @param edge one of SWT.TOP, SWT.BOTTOM, SWT.LEFT, SWT.RIGHT
     * @param collapsedExtent visible pixels while collapsed, including a tab
     *                        strip or handle (zero for a fully hidden flyout)
     * @param expandedExtent full panel width (left/right) or height (top/bottom)
     */
    public LayeredSlidePanel(LayeredViewport viewport, Control control, int edge,
            int collapsedExtent, int expandedExtent) {
        if (viewport == null || control == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (viewport.isDisposed() || control.isDisposed()) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
        if (control.getParent() != viewport) SWT.error(SWT.ERROR_INVALID_PARENT);
        if (edge != SWT.TOP && edge != SWT.BOTTOM && edge != SWT.LEFT && edge != SWT.RIGHT)
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if (collapsedExtent < 0 || expandedExtent < collapsedExtent || expandedExtent > MAX_EXTENT)
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        this.viewport = viewport;
        this.control = control;
        this.edge = edge;
        this.collapsedExtent = collapsedExtent;
        this.expandedExtent = expandedExtent;
        this.shownExtent = collapsedExtent;
        Rectangle bounds = projected(collapsedExtent);
        layer = viewport.addControlLayer("slide-panel", control,
                bounds.x, bounds.y, bounds.width, bounds.height);
        layer.setPinned(SWT.HORIZONTAL | SWT.VERTICAL);
        resized = event -> {
            if (!closed && !viewport.isDisposed() && !control.isDisposed()) apply(shownExtent);
        };
        parentDisposed = event -> {
            closed = true;
            animationEpoch++;
        };
        controlDisposed = event -> close();
        viewport.addListener(SWT.Resize, resized);
        viewport.addListener(SWT.Dispose, parentDisposed);
        control.addListener(SWT.Dispose, controlDisposed);
    }

    public LayeredViewport.Layer layer() {
        check();
        return layer;
    }

    public Control control() {
        check();
        return control;
    }

    public boolean isExpanded() {
        check();
        return expanded;
    }

    public int visibleExtent() {
        check();
        return shownExtent;
    }

    public int durationMillis() {
        check();
        return durationMillis;
    }

    /** Set a bounded frame duration; zero disables animation. */
    public void setDurationMillis(int value) {
        check();
        if (value < 0 || value > MAX_DURATION_MILLIS) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        durationMillis = value;
    }

    public void setExpanded(boolean value) {
        setExpanded(value, true);
    }

    /**
     * Reversing a transition starts at the last published extent. Old timer
     * callbacks are generation-invalidated, including after native disposal.
     */
    public void setExpanded(boolean value, boolean animate) {
        check();
        expanded = value;
        int destination = value ? expandedExtent : collapsedExtent;
        int epoch = ++animationEpoch;
        if (!animate || durationMillis == 0 || shownExtent == destination
                || !viewport.isVisible()) {
            apply(destination);
            return;
        }
        int start = shownExtent;
        long started = System.nanoTime();
        viewport.getDisplay().timerExec(FRAME_MILLIS, () -> frame(epoch, start, destination, started));
    }

    /** Follow the current layer stack explicitly; do not steal later tooltips. */
    public void bringToFront() {
        check();
        int last = viewport.getLayers().length - 1;
        if (layer.getZIndex() != last) layer.moveTo(last);
    }

    private void frame(int epoch, int start, int destination, long started) {
        if (epoch != animationEpoch || closed || viewport.isDisposed() || control.isDisposed()) return;
        long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        apply(LayeredSlideMath.height(start, destination, elapsed, durationMillis));
        if (epoch == animationEpoch && elapsed < durationMillis && !closed) {
            viewport.getDisplay().timerExec(FRAME_MILLIS,
                    () -> frame(epoch, start, destination, started));
        }
    }

    private Rectangle projected(int visible) {
        Rectangle view = viewport.getClientArea();
        int width = Math.max(0, view.width), height = Math.max(0, view.height);
        return switch (edge) {
            case SWT.TOP -> new Rectangle(0, 0, width, visible);
            case SWT.BOTTOM -> new Rectangle(0, height - visible, width, expandedExtent);
            case SWT.LEFT -> new Rectangle(visible - expandedExtent, 0, expandedExtent, height);
            case SWT.RIGHT -> new Rectangle(width - visible, 0, expandedExtent, height);
            default -> throw new AssertionError("validated edge");
        };
    }

    private void apply(int extent) {
        shownExtent = extent;
        Rectangle projected = projected(extent);
        layer.setWorldBounds(projected.x, projected.y, projected.width, projected.height);
    }

    private void check() {
        if (closed || viewport.isDisposed() || control.isDisposed())
            SWT.error(SWT.ERROR_WIDGET_DISPOSED);
        viewport.getClientArea(); // SWT UI-thread guard
    }

    /**
     * Remove the layer but leave the caller's Control alive. Pending timer
     * callbacks become inert and the viewport's resize listener is detached.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        animationEpoch++;
        if (!viewport.isDisposed()) {
            viewport.removeListener(SWT.Resize, resized);
            viewport.removeListener(SWT.Dispose, parentDisposed);
            layer.remove();
        }
        if (!control.isDisposed()) control.removeListener(SWT.Dispose, controlDisposed);
    }
}
