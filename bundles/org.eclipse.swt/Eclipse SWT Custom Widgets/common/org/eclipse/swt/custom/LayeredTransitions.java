/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.custom;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport.Layer;
import org.eclipse.swt.custom.LayeredViewport.WorldBounds;
import org.eclipse.swt.internal.ViewportTweenMath;
import org.eclipse.swt.widgets.Display;

/**
 * UI-thread position and opacity animator for original layered SWT scenes.
 * Native image layers retain DirectComposition visuals while moving or fading;
 * other painted layers use the retained store. Native child Controls move
 * through SWT bounds, but cannot be alpha-blended by this API. No new JNI ABI.
 *
 * <p>One timer drives all translations or image-opacity fades owned by this
 * controller. Each layer admits one animation at a time; replacing it cancels
 * its completion callback. The viewport and caller still own the layers.</p>
 */
public final class LayeredTransitions {
    public static final int LINEAR = ViewportTweenMath.LINEAR;
    public static final int EASE_OUT_CUBIC = ViewportTweenMath.EASE_OUT_CUBIC;
    public static final int EASE_IN_OUT_CUBIC = ViewportTweenMath.EASE_IN_OUT_CUBIC;
    private static final int FRAME_MILLIS = 16;
    private static final int MAX_DURATION_MILLIS = 60_000;

    private final LayeredViewport viewport;
    private final Display display;
    private final IdentityHashMap<Layer, Motion> active = new IdentityHashMap<>();
    private Motion[] snapshot = new Motion[8];
    private Motion[] completed = new Motion[8];
    private final Runnable tick = this::onFrame;
    private boolean scheduled;
    private boolean paintingFrame;
    private boolean disposed;

    public LayeredTransitions(LayeredViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.display = viewport.getDisplay();
        viewport.addListener(SWT.Dispose, event -> cancelOnDispose());
    }

    /** Move without changing width, height, layer identity or its native request. */
    public Motion moveTo(Layer layer, long x, long y, int durationMillis) {
        return moveTo(layer, x, y, durationMillis, EASE_OUT_CUBIC, null);
    }

    /**
     * Starts a movement, with an optional completion run on the SWT UI thread.
     * Zero duration applies the destination immediately. Target coordinates
     * must remain representable with the layer's current width and height.
     */
    public Motion moveTo(Layer layer, long x, long y, int durationMillis,
            int easing, Runnable onComplete) {
        checkOwner();
        Objects.requireNonNull(layer, "layer");
        if (durationMillis < 0 || durationMillis > MAX_DURATION_MILLIS
                || easing < LINEAR || easing > EASE_IN_OUT_CUBIC) {
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }
        int z = layer.getZIndex();
        Layer[] list = viewport.getLayers();
        if (z >= list.length || list[z] != layer) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        WorldBounds bounds = layer.getWorldBounds();
        try {
            Math.addExact(x, bounds.width());
            Math.addExact(y, bounds.height());
        } catch (ArithmeticException overflow) {
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }
        Motion old = active.remove(layer);
        if (old != null) old.running = false;
        Motion motion = new Motion(layer, bounds.x(), bounds.y(), x, y,
                bounds.width(), bounds.height(), durationMillis, easing, onComplete);
        if (durationMillis == 0 || (bounds.x() == x && bounds.y() == y)) {
            layer.setWorldBounds(x, y, bounds.width(), bounds.height());
            cancelIdleTimer();
            if (onComplete != null) onComplete.run();
        } else {
            motion.running = true;
            active.put(layer, motion);
            schedule();
        }
        return motion;
    }

    /**
     * Animate painted or native image opacity without changing Z or bounds.
     * Native child Control layers are rejected: their fractional alpha cannot
     * be represented by the existing SWT child-window presentation path.
     */
    public Motion fadeTo(Layer layer, int opacity, int durationMillis) {
        return fadeTo(layer, opacity, durationMillis, EASE_OUT_CUBIC, null);
    }

    /** Animate opacity (0..255), with easing and an optional completion callback. */
    public Motion fadeTo(Layer layer, int opacity, int durationMillis,
            int easing, Runnable onComplete) {
        checkOwner();
        Objects.requireNonNull(layer, "layer");
        if (opacity < 0 || opacity > 255 || durationMillis < 0
                || durationMillis > MAX_DURATION_MILLIS
                || easing < LINEAR || easing > EASE_IN_OUT_CUBIC) {
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }
        int z = layer.getZIndex();
        Layer[] layers = viewport.getLayers();
        if (z >= layers.length || layers[z] != layer || viewport.getControl(layer) != null) {
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }
        int from = layer.getOpacity();
        Motion old = active.remove(layer);
        if (old != null) old.running = false;
        Motion motion = new Motion(layer, from, opacity, durationMillis, easing, onComplete);
        if (durationMillis == 0 || from == opacity) {
            layer.setOpacity(opacity);
            cancelIdleTimer();
            if (onComplete != null) onComplete.run();
        } else {
            motion.running = true;
            active.put(layer, motion);
            schedule();
        }
        return motion;
    }

    /** A synchronously completed/replaced final motion needs no queued UI tick. */
    private void cancelIdleTimer() {
        if (active.isEmpty() && scheduled && !display.isDisposed()) {
            display.timerExec(-1, tick);
            scheduled = false;
        }
    }

    /** Cancel all this controller's animations; no completion callbacks run. */
    public void cancelAll() {
        checkOwner();
        for (Motion motion : active.values()) motion.running = false;
        active.clear();
        if (scheduled) {
            display.timerExec(-1, tick);
            scheduled = false;
        }
    }

    public int runningCount() {
        checkOwner();
        return active.size();
    }

    private void checkOwner() {
        if (disposed || viewport.isDisposed()) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
        viewport.getDisplay(); // SWT thread check and original Widget contract.
    }

    private void schedule() {
        if (!scheduled && !disposed && !display.isDisposed() && !active.isEmpty()) {
            scheduled = true;
            display.timerExec(FRAME_MILLIS, tick);
        }
    }

    private void onFrame() {
        scheduled = false;
        if (disposed || viewport.isDisposed() || display.isDisposed()) {
            cancelOnDispose();
            return;
        }
        if (paintingFrame) {
            schedule();
            return;
        }
        paintingFrame = true;
        int count = active.size(), done = 0;
        if (snapshot.length < count) snapshot = Arrays.copyOf(snapshot, Math.max(count, snapshot.length * 2));
        if (completed.length < count) completed = Arrays.copyOf(completed, Math.max(count, completed.length * 2));
        // Capture all motions before any setBounds/resize callbacks may mutate the controller.
        int used = 0;
        for (Motion motion : active.values()) snapshot[used++] = motion;
        long now = System.nanoTime();
        try {
            viewport.beginUpdate();
            try {
                for (int i = 0; i < used; i++) {
                    if (disposed || viewport.isDisposed()) break;
                    Motion motion = snapshot[i];
                    if (!motion.running) continue;
                    try {
                        motion.layer.getId();
                    } catch (IllegalArgumentException removed) {
                        motion.running = false;
                        active.remove(motion.layer, motion);
                        continue;
                    }
                    long elapsed = Math.max(0, (now - motion.startNanos) / 1_000_000L);
                    if (motion.opacityMotion) {
                        int opacity = (int) ViewportTweenMath.position(motion.opacity0,
                                motion.opacity1, elapsed, motion.durationMillis, motion.easing);
                        motion.layer.setOpacity(opacity);
                    } else {
                        long x = ViewportTweenMath.position(motion.x0, motion.x1, elapsed,
                                motion.durationMillis, motion.easing);
                        long y = ViewportTweenMath.position(motion.y0, motion.y1, elapsed,
                                motion.durationMillis, motion.easing);
                        motion.layer.setWorldBounds(x, y, motion.width, motion.height);
                    }
                    if (elapsed >= motion.durationMillis && motion.running) {
                        motion.running = false;
                        active.remove(motion.layer, motion);
                        completed[done++] = motion;
                    }
                }
            } finally {
                if (!viewport.isDisposed()) viewport.endUpdate();
            }
        } catch (RuntimeException | Error failedFrame) {
            // A caller's resize/paint listener can throw while a native
            // Control is moving. Never strand a motion with no live timer.
            // Preserve that exception and suppress successful completions.
            for (Motion motion : active.values()) motion.running = false;
            active.clear();
            Arrays.fill(completed, 0, done, null);
            throw failedFrame;
        } finally {
            Arrays.fill(snapshot, 0, used, null);
            paintingFrame = false;
        }
        try {
            for (int i = 0; i < done; i++) {
                Motion motion = completed[i];
                completed[i] = null;
                if (!disposed && !viewport.isDisposed() && motion.onComplete != null) {
                    motion.onComplete.run();
                }
            }
        } finally {
            Arrays.fill(completed, 0, done, null);
            schedule();
        }
    }

    private void cancelOnDispose() {
        disposed = true;
        for (Motion motion : active.values()) motion.running = false;
        active.clear();
        if (scheduled && !display.isDisposed()) display.timerExec(-1, tick);
        scheduled = false;
    }

    /** Cancellation handle retaining the last published position or opacity. */
    public final class Motion {
        private final Layer layer;
        private final long x0, y0, x1, y1, width, height, startNanos;
        private final boolean opacityMotion;
        private final int opacity0, opacity1, durationMillis, easing;
        private final Runnable onComplete;
        private boolean running;

        private Motion(Layer layer, long x0, long y0, long x1, long y1,
                long width, long height, int durationMillis, int easing, Runnable onComplete) {
            this.layer = layer;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.width = width;
            this.height = height;
            this.opacityMotion = false;
            this.opacity0 = this.opacity1 = 0;
            this.durationMillis = durationMillis;
            this.easing = easing;
            this.onComplete = onComplete;
            this.startNanos = System.nanoTime();
        }

        private Motion(Layer layer, int opacity0, int opacity1,
                int durationMillis, int easing, Runnable onComplete) {
            this.layer = layer;
            this.x0 = this.y0 = this.x1 = this.y1 = this.width = this.height = 0L;
            this.opacityMotion = true;
            this.opacity0 = opacity0;
            this.opacity1 = opacity1;
            this.durationMillis = durationMillis;
            this.easing = easing;
            this.onComplete = onComplete;
            this.startNanos = System.nanoTime();
        }

        public boolean isRunning() {
            checkOwner();
            return running;
        }

        public void cancel() {
            checkOwner();
            running = false;
            active.remove(layer, this);
            cancelIdleTimer();
        }
    }
}
