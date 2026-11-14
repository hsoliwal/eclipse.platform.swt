/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Original SWT Button API backed by the existing mobile Control native peer.
 * Mouse/keyboard input is delivered by Android/UIKit through SWT's Display
 * event queue; resource and disposal ownership remain in Control.
 */
public class Button extends Control {
    private String text = "";
    private Image image;
    private boolean selected, grayed, armed;

    public Button(Composite parent, int style) {
        super(parent, WidgetStylePolicy.BUTTON.applyAsInt(style));
        addListener(SWT.Paint, this::paintButton);
        addListener(SWT.MouseDown, event -> {
            if (event.button == 1 && inside(event.x, event.y)) armed = true;
        });
        addListener(SWT.MouseUp, event -> {
            boolean activate = armed && event.button == 1 && inside(event.x, event.y);
            armed = false;
            if (activate) activate();
        });
        addListener(SWT.KeyUp, event -> {
            if (event.character == ' ' || event.character == '\r') activate();
        });
        publishAccessibility();
    }

    private boolean inside(int x, int y) {
        Point size = getSize();
        return x >= 0 && y >= 0 && x < size.x && y < size.y;
    }

    private boolean holdsSelection() {
        return (style & (SWT.CHECK | SWT.RADIO | SWT.TOGGLE)) != 0;
    }

    private void activate() {
        if (isDisposed() || !isEnabled() || !isVisible()) return;
        if ((style & SWT.RADIO) != 0) {
            if ((parent.getStyle() & SWT.NO_RADIO_GROUP) == 0)
                deselectAdjacentRadioPeers();
            selected = true;
        } else if (holdsSelection()) {
            selected = !selected;
        }
        publishAccessibility();
        redraw();
        if (!isDisposed()) sendEvent(SWT.Selection, new Event());
    }

    private void deselectAdjacentRadioPeers() {
        Control[] siblings = parent.getChildren();
        for (int index = 0; index < siblings.length; index++) {
            if (siblings[index] != this) continue;
            for (int p = index - 1; p >= 0; p--) {
                if (!(siblings[p] instanceof Button b) || (b.style & SWT.RADIO) == 0) break;
                b.setSelection(false);
            }
            for (int p = index + 1; p < siblings.length; p++) {
                if (!(siblings[p] instanceof Button b) || (b.style & SWT.RADIO) == 0) break;
                b.setSelection(false);
            }
            break;
        }
    }

    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }

    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }

    public boolean getSelection() { checkWidget(); return holdsSelection() && selected; }
    public void setSelection(boolean value) {
        checkWidget();
        if (!holdsSelection() || selected == value) return;
        selected = value;
        publishAccessibility();
        redraw();
    }

    public boolean getGrayed() { checkWidget(); return (style & SWT.CHECK) != 0 && grayed; }
    public void setGrayed(boolean value) {
        checkWidget();
        if ((style & SWT.CHECK) == 0 || grayed == value) return;
        grayed = value;
        publishAccessibility();
        redraw();
    }

    public String getText() { checkWidget(); return (style & SWT.ARROW) != 0 ? "" : text; }
    public void setText(String value) {
        checkWidget();
        if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if ((style & SWT.ARROW) != 0 || text.equals(value)) return;
        text = value;
        publishAccessibility();
        redraw();
    }

    public Image getImage() { checkWidget(); return image; }
    public void setImage(Image value) {
        checkWidget();
        if (value != null && (value.isDisposed() || value.getDevice() != display))
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if ((style & SWT.ARROW) != 0 || image == value) return;
        image = value;
        redraw();
    }

    public int getAlignment() {
        checkWidget();
        if ((style & SWT.ARROW) != 0) {
            if ((style & SWT.DOWN) != 0) return SWT.DOWN;
            if ((style & SWT.LEFT) != 0) return SWT.LEFT;
            if ((style & SWT.RIGHT) != 0) return SWT.RIGHT;
            return SWT.UP;
        }
        if ((style & SWT.CENTER) != 0) return SWT.CENTER;
        if ((style & SWT.RIGHT) != 0) return SWT.RIGHT;
        return SWT.LEFT;
    }

    public void setAlignment(int value) {
        checkWidget();
        if ((style & SWT.ARROW) != 0) {
            if (value != SWT.UP && value != SWT.DOWN && value != SWT.LEFT && value != SWT.RIGHT) return;
            style = style & ~(SWT.UP | SWT.DOWN | SWT.LEFT | SWT.RIGHT) | value;
        } else {
            if (value != SWT.LEFT && value != SWT.CENTER && value != SWT.RIGHT) return;
            style = style & ~(SWT.LEFT | SWT.CENTER | SWT.RIGHT) | value;
        }
        redraw();
    }

    @Override public void setEnabled(boolean value) {
        super.setEnabled(value);
        if (!isDisposed()) publishAccessibility();
    }

    private void publishAccessibility() {
        int role = (style & SWT.CHECK) != 0 ? ACC.ROLE_CHECKBUTTON
                : (style & SWT.RADIO) != 0 ? ACC.ROLE_RADIOBUTTON : ACC.ROLE_PUSHBUTTON;
        int state = (selected ? ACC.STATE_CHECKED : ACC.STATE_NORMAL)
                | (!getEnabled() ? ACC.STATE_DISABLED : 0);
        display.internal_platform().setAccessibility(peer, visibleText(text), role, state);
    }

    private static String visibleText(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&') {
                if (i + 1 < value.length() && value.charAt(i + 1) == '&') i++;
                else continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private void paintButton(Event event) {
        if (isDisposed()) return;
        GC gc = event.gc;
        Point size = getSize();
        if (size.x <= 0 || size.y <= 0) return;
        gc.setForeground(getForeground());
        // GC.fill* takes its fill color from Background, not Foreground.
        // Keep the control's normal background restore for client listeners.
        org.eclipse.swt.graphics.Color savedBackground = gc.getBackground();
        gc.setBackground(getForeground());
        try {
        int leftInset = 6, contentWidth = size.x - 12;
        if ((style & SWT.CHECK) != 0) {
            int y = Math.max(0, (size.y - 15) / 2);
            gc.drawRectangle(4, y, 14, 14);
            if (selected) {
                if (grayed) gc.fillRectangle(8, y + 4, 7, 7);
                else { gc.drawLine(7, y + 7, 10, y + 11); gc.drawLine(10, y + 11, 16, y + 4); }
            }
            leftInset = 26; contentWidth = size.x - 30;
        } else if ((style & SWT.RADIO) != 0) {
            int y = Math.max(0, (size.y - 15) / 2);
            gc.drawOval(4, y, 14, 14);
            if (selected) gc.fillOval(8, y + 4, 6, 6);
            leftInset = 26; contentWidth = size.x - 30;
        } else if ((style & SWT.ARROW) != 0) {
            int cx = size.x / 2, cy = size.y / 2;
            int[] points;
            switch (getAlignment()) {
                case SWT.DOWN -> points = new int[] {cx - 7, cy - 4, cx + 7, cy - 4, cx, cy + 5};
                case SWT.LEFT -> points = new int[] {cx + 4, cy - 7, cx + 4, cy + 7, cx - 5, cy};
                case SWT.RIGHT -> points = new int[] {cx - 4, cy - 7, cx - 4, cy + 7, cx + 5, cy};
                default -> points = new int[] {cx - 7, cy + 4, cx + 7, cy + 4, cx, cy - 5};
            }
            gc.fillPolygon(points);
            return;
        } else {
            gc.drawRectangle(0, 0, Math.max(0, size.x - 1), Math.max(0, size.y - 1));
            if (selected) gc.drawRectangle(2, 2, Math.max(0, size.x - 5), Math.max(0, size.y - 5));
        }
        int imageWidth = 0, imageHeight = 0;
        if (image != null && !image.isDisposed()) {
            Rectangle b = image.getBounds(); imageWidth = b.width; imageHeight = b.height;
        }
        String caption = visibleText(text);
        gc.setFont(getFont());
        Point extent = gc.textExtent(caption);
        int gap = imageWidth != 0 && !caption.isEmpty() ? 4 : 0;
        int totalWidth = imageWidth + gap + (caption.isEmpty() ? 0 : extent.x);
        int x = leftInset;
        if (getAlignment() == SWT.RIGHT) x += Math.max(0, contentWidth - totalWidth);
        else if (getAlignment() == SWT.CENTER) x += Math.max(0, (contentWidth - totalWidth) / 2);
        if (imageWidth != 0) {
            gc.drawImage(image, x, Math.max(0, (size.y - imageHeight) / 2));
            x += imageWidth + gap;
        }
        if (!caption.isEmpty()) gc.drawText(caption, x, Math.max(0, (size.y - extent.y) / 2), true);
        } finally { gc.setBackground(savedBackground); }
    }

    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int width = 28, height = 30;
        if ((style & SWT.ARROW) == 0) {
            Image scratch = new Image(display, 1, 1);
            try {
                GC gc = new GC(scratch);
                try {
                    gc.setFont(getFont());
                    Point extent = gc.textExtent(visibleText(text));
                    width = Math.max(width, extent.x + 16);
                    height = Math.max(height, extent.y + 10);
                } finally { gc.dispose(); }
            } finally { scratch.dispose(); }
            if (image != null && !image.isDisposed()) {
                Rectangle b = image.getBounds();
                width += b.width + 4;
                height = Math.max(height, b.height + 10);
            }
            if ((style & (SWT.CHECK | SWT.RADIO)) != 0) width += 20;
        }
        int border = 2 * getBorderWidth();
        return new Point(wHint == SWT.DEFAULT ? width + border : wHint + border,
                hHint == SWT.DEFAULT ? height + border : hHint + border);
    }
}
