/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

/**
 * SWT ProgressBar on the shared Android/iOS Canvas/GC mobile platform.
 * The public values and style policy match the standard SWT widget; this
 * paints on the existing Control peer and does not create a second UI tree.
 */
public class ProgressBar extends Control {
    private int minimum, maximum = 100, selection, state = SWT.NORMAL, phase;
    private boolean pulsePending;
    private final Runnable pulse = () -> {
        pulsePending = false;
        if (isDisposed() || !isVisible() || (style & SWT.INDETERMINATE) == 0) return;
        phase = (phase + 1) % 120;
        redraw();
        schedulePulse();
    };

    /** Create a progress indicator with standard SWT orientation and style policy. */
    public ProgressBar(Composite parent, int style) {
        super(parent, WidgetStylePolicy.PROGRESS.applyAsInt(style));
        addListener(SWT.Paint, event -> paint(event.gc));
        addListener(SWT.Dispose, event -> stopPulse());
        publishAccessibility();
        schedulePulse();
    }

    /** Inclusive upper bound (defaults to 100). */
    public int getMaximum() { checkWidget(); return maximum; }
    /** Inclusive lower bound (defaults to zero). */
    public int getMinimum() { checkWidget(); return minimum; }
    /** Current clamped progress value. */
    public int getSelection() { checkWidget(); return selection; }
    /** SWT.NORMAL, SWT.PAUSED or SWT.ERROR, independent of INDETERMINATE. */
    public int getState() { checkWidget(); return state; }

    /** Invalid maximum values are ignored, matching SWT's range contract. */
    public void setMaximum(int value) {
        checkWidget();
        if (value <= minimum || value == maximum) return;
        maximum = value;
        if (selection > maximum) selection = maximum;
        changed();
    }

    /** Invalid minimum values are ignored, matching SWT's range contract. */
    public void setMinimum(int value) {
        checkWidget();
        if (value < 0 || value >= maximum || value == minimum) return;
        minimum = value;
        if (selection < minimum) selection = minimum;
        changed();
    }

    /** Programmatic value changes do not dispatch SWT.Selection. */
    public void setSelection(int value) {
        checkWidget();
        int next = Math.max(minimum, Math.min(maximum, value));
        if (next == selection) return;
        selection = next;
        changed();
    }

    /** Update the visual state; other values are ignored as on SWT desktop. */
    public void setState(int value) {
        checkWidget();
        if ((value != SWT.NORMAL && value != SWT.ERROR && value != SWT.PAUSED) || state == value) return;
        state = value;
        changed();
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) publishAccessibility();
    }

    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) schedulePulse(); else stopPulse();
    }

    private void changed() {
        publishAccessibility();
        redraw();
    }

    private void publishAccessibility() {
        int flags = (style & SWT.INDETERMINATE) != 0 ? ACC.STATE_BUSY : ACC.STATE_NORMAL;
        if (!isEnabled()) flags |= ACC.STATE_DISABLED;
        display.internal_platform().setAccessibility(peer,
                "Progress " + selection + " of " + maximum, ACC.ROLE_PROGRESSBAR, flags);
    }

    private void schedulePulse() {
        if (!pulsePending && !isDisposed() && isVisible() && (style & SWT.INDETERMINATE) != 0) {
            pulsePending = true;
            display.timerExec(90, pulse);
        }
    }

    private void stopPulse() {
        if (pulsePending && !display.isDisposed()) display.timerExec(-1, pulse);
        pulsePending = false;
    }

    private void paint(GC gc) {
        if (isDisposed()) return;
        schedulePulse();
        Point size = getSize();
        int width = size.x, height = size.y;
        if (width <= 0 || height <= 0) return;
        boolean vertical = (style & SWT.VERTICAL) != 0;
        int length = Math.max(0, (vertical ? height : width) - 4);
        int breadth = Math.max(0, (vertical ? width : height) - 4);
        gc.setForeground(getForeground());
        gc.drawRectangle(0, 0, Math.max(0, width - 1), Math.max(0, height - 1));
        if (length == 0 || breadth == 0) return;
        int span, start;
        if ((style & SWT.INDETERMINATE) != 0) {
            span = Math.max(1, length / 3);
            start = (int)((long)phase * Math.max(0, length - span) / 119L);
        } else {
            span = (int)((long)length * (selection - (long)minimum) / (maximum - (long)minimum));
            start = vertical ? length - span : 0;
        }
        org.eclipse.swt.graphics.Color saved = gc.getBackground();
        gc.setBackground(getForeground());
        try {
            if (span > 0) {
                if (vertical) gc.fillRectangle(2, start + 2, breadth, span);
                else gc.fillRectangle(start + 2, 2, span, breadth);
            }
            // Small marks distinguish PAUSED/ERROR even on monochrome targets.
            if (state == SWT.PAUSED) gc.drawLine(2, height / 2, width - 3, height / 2);
            if (state == SWT.ERROR) gc.drawLine(2, 2, width - 3, height - 3);
        } finally { gc.setBackground(saved); }
    }

    /** SWT default preferred size (vertical/horizontal), plus border trim. */
    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int border = 2 * getBorderWidth();
        boolean vertical = (style & SWT.VERTICAL) != 0;
        return new Point((wHint == SWT.DEFAULT ? (vertical ? 20 : 160) : Math.max(0, wHint)) + border,
                (hHint == SWT.DEFAULT ? (vertical ? 160 : 20) : Math.max(0, hHint)) + border);
    }
}
