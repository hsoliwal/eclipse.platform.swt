/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

/**
 * SWT Scale on Android/iOS using the same native-backed Control/GC and SWT
 * event stream as the desktop widget. The value is never stored in native
 * View state, so rotation and Activity recreation retain its Java owner.
 */
public class Scale extends Control {
    private static final int INSET = 9;
    private int minimum, maximum = 100, selection, increment = 1, pageIncrement = 10;
    private boolean dragging;

    /** Create a Scale with SWT.HORIZONTAL or SWT.VERTICAL orientation. */
    public Scale(Composite parent, int style) {
        super(parent, WidgetStylePolicy.ORIENTATION.applyAsInt(style));
        addListener(SWT.Paint, event -> paint(event.gc));
        addListener(SWT.MouseDown, event -> {
            if (event.button == 1 && isEnabled() && isVisible()) {
                dragging = true;
                userSelect(coordinate(event.x, event.y));
            }
        });
        addListener(SWT.MouseMove, event -> {
            if (dragging && isEnabled() && isVisible()) userSelect(coordinate(event.x, event.y));
        });
        addListener(SWT.MouseUp, event -> {
            if (event.button != 1) return;
            if (dragging && isEnabled() && isVisible()) userSelect(coordinate(event.x, event.y));
            dragging = false;
        });
        addListener(SWT.MouseExit, event -> dragging = false);
        addListener(SWT.KeyDown, event -> {
            if (!isEnabled() || !isVisible()) return;
            switch (event.keyCode) {
                case SWT.ARROW_LEFT, SWT.ARROW_DOWN -> userSelect(clamp(selection - (long)increment));
                case SWT.ARROW_RIGHT, SWT.ARROW_UP -> userSelect(clamp(selection + (long)increment));
                case SWT.PAGE_DOWN -> userSelect(clamp(selection - (long)pageIncrement));
                case SWT.PAGE_UP -> userSelect(clamp(selection + (long)pageIncrement));
                case SWT.HOME -> userSelect(minimum);
                case SWT.END -> userSelect(maximum);
                default -> { }
            }
        });
        publishAccessibility();
    }

    /** Add SWT.Selection notification for user-initiated value changes. */
    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }
    /** Remove a previously attached selection listener. */
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    /** Returns the current step increment, initially one. */
    public int getIncrement() { checkWidget(); return increment; }
    /** Returns the maximum value, initially 100. */
    public int getMaximum() { checkWidget(); return maximum; }
    /** Returns the minimum value, initially zero. */
    public int getMinimum() { checkWidget(); return minimum; }
    /** Returns the page increment, initially ten. */
    public int getPageIncrement() { checkWidget(); return pageIncrement; }
    /** Returns the selected value, clamped to the current range. */
    public int getSelection() { checkWidget(); return selection; }

    /** Invalid increments are ignored as on the desktop SWT widget. */
    public void setIncrement(int value) { checkWidget(); if (value > 0) increment = value; }
    /** Invalid page increments are ignored as on the desktop SWT widget. */
    public void setPageIncrement(int value) { checkWidget(); if (value > 0) pageIncrement = value; }
    /** Set the maximum, only if strictly greater than the minimum. */
    public void setMaximum(int value) {
        checkWidget();
        if (value <= minimum || value == maximum) return;
        maximum = value;
        setSelection(selection);
        redraw();
    }
    /** Set the minimum, only if nonnegative and less than the maximum. */
    public void setMinimum(int value) {
        checkWidget();
        if (value < 0 || value >= maximum || value == minimum) return;
        minimum = value;
        setSelection(selection);
        redraw();
    }
    /** Programmatic changes update the value without sending Selection. */
    public void setSelection(int value) {
        checkWidget();
        int next = clamp(value);
        if (next == selection) return;
        selection = next;
        publishAccessibility();
        redraw();
    }

    private int clamp(long value) {
        return (int)Math.max((long)minimum, Math.min((long)maximum, value));
    }

    private boolean vertical() { return (style & SWT.VERTICAL) != 0; }
    private int coordinate(int x, int y) {
        Point size = getSize();
        int length = vertical() ? size.y : size.x;
        int span = Math.max(1, length - 2 * INSET);
        long requested = (vertical() ? (long)y : (long)x) - INSET;
        int at = (int)Math.max(0L, Math.min((long)span, requested));
        long fraction = (long)at * (maximum - (long)minimum) + span / 2L;
        int delta = (int)(fraction / span);
        return vertical() ? maximum - delta : minimum + delta;
    }
    private void userSelect(int value) {
        if (isDisposed() || !isEnabled() || !isVisible()) return;
        int next = clamp(value);
        if (next == selection) return;
        selection = next;
        publishAccessibility();
        redraw();
        if (!isDisposed()) sendEvent(SWT.Selection, new Event());
    }

    @Override public void setEnabled(boolean value) {
        if (!value) dragging = false;
        super.setEnabled(value);
        if (!isDisposed()) publishAccessibility();
    }

    @Override public void setVisible(boolean value) {
        if (!value) dragging = false;
        super.setVisible(value);
    }

    private void publishAccessibility() {
        int flags = isEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED;
        display.internal_platform().setAccessibility(peer,
                "Scale " + selection + " of " + maximum, ACC.ROLE_SLIDER, flags);
    }

    private void paint(GC gc) {
        if (isDisposed()) return;
        Point size = getSize();
        int length = vertical() ? size.y : size.x;
        int width = vertical() ? size.x : size.y;
        if (length <= 0 || width <= 0) return;
        int span = Math.max(1, length - 2 * INSET);
        int pos = INSET + (int)((long)span * (vertical() ? maximum - (long)selection
                : selection - (long)minimum) / (maximum - (long)minimum));
        gc.setForeground(getForeground());
        int cross = width / 2;
        if (vertical()) gc.drawLine(cross, INSET, cross, Math.max(INSET, length - INSET));
        else gc.drawLine(INSET, cross, Math.max(INSET, length - INSET), cross);
        Color original = gc.getBackground();
        gc.setBackground(getForeground());
        try {
            if (vertical()) gc.fillOval(Math.max(0, cross - 7), Math.max(0, pos - 7), 14, 14);
            else gc.fillOval(Math.max(0, pos - 7), Math.max(0, cross - 7), 14, 14);
        } finally { gc.setBackground(original); }
    }

    /** Return orientation-appropriate default size or caller-provided hints. */
    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int border = getBorderWidth() * 2;
        return new Point((wHint == SWT.DEFAULT ? (vertical() ? 28 : 160) : Math.max(0, wHint)) + border,
                (hHint == SWT.DEFAULT ? (vertical() ? 160 : 28) : Math.max(0, hHint)) + border);
    }
}
