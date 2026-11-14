/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Original SWT Label API on the mobile Control/GC owner.
 * Drawing uses the existing Android Canvas or iOS CoreGraphics peer, not an
 * Android-only widget class or a second presentation toolkit.
 */
public class Label extends Control {
    private String text = "";
    private Image image;
    private boolean imageMode;

    public Label(Composite parent, int style) {
        super(parent, WidgetStylePolicy.LABEL.applyAsInt(style));
        addListener(SWT.Paint, this::paintLabel);
        publishAccessibility();
    }

    public int getAlignment() {
        checkWidget();
        if ((style & SWT.SEPARATOR) != 0) return 0;
        if ((style & SWT.CENTER) != 0) return SWT.CENTER;
        if ((style & SWT.RIGHT) != 0) return SWT.RIGHT;
        return SWT.LEFT;
    }

    public void setAlignment(int alignment) {
        checkWidget();
        if ((style & SWT.SEPARATOR) != 0) return;
        if (alignment != SWT.LEFT && alignment != SWT.CENTER && alignment != SWT.RIGHT) return;
        style = (style & ~(SWT.LEFT | SWT.CENTER | SWT.RIGHT)) | alignment;
        redraw();
    }

    public String getText() {
        checkWidget();
        return (style & SWT.SEPARATOR) != 0 ? "" : text;
    }

    public void setText(String value) {
        checkWidget();
        if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if ((style & SWT.SEPARATOR) != 0) return;
        if (text.equals(value) && !imageMode) return;
        text = value;
        imageMode = false;
        publishAccessibility();
        redraw();
    }

    public Image getImage() {
        checkWidget();
        return image;
    }

    public void setImage(Image value) {
        checkWidget();
        if (value != null && (value.isDisposed() || value.getDevice() != display))
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if ((style & SWT.SEPARATOR) != 0) return;
        if (image == value && imageMode == (value != null)) return;
        image = value;
        imageMode = value != null;
        publishAccessibility();
        redraw();
    }

    @Override public boolean setFocus() { checkWidget(); return false; }
    @Override public boolean forceFocus() { checkWidget(); return false; }

    private void publishAccessibility() {
        display.internal_platform().setAccessibility(peer,
                (style & SWT.SEPARATOR) != 0 || imageMode ? "" : visibleText(text),
                ACC.ROLE_LABEL, ACC.STATE_NORMAL);
    }

    /** SWT mnemonic escapes are visual markup; getters retain the original text. */
    private static String visibleText(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '&') i++;
                else continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Keep physical allocations proportional to visible text, not viewport length. */
    private List<String> lines(GC gc, int maxWidth) {
        ArrayList<String> result = new ArrayList<>();
        for (String paragraph : visibleText(text).split("\\r?\\n", -1)) {
            if ((style & SWT.WRAP) == 0 || maxWidth <= 0) {
                result.add(paragraph);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String proposed = line.length() == 0 ? word : line + " " + word;
                if (line.length() != 0 && gc.textExtent(proposed).x > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(proposed);
                }
            }
            result.add(line.toString());
        }
        return result;
    }

    private void paintLabel(Event event) {
        if (isDisposed()) return;
        Point size = getSize();
        GC gc = event.gc;
        if ((style & SWT.SEPARATOR) != 0) {
            gc.setForeground(getForeground());
            if ((style & SWT.VERTICAL) != 0)
                gc.drawLine(size.x / 2, 0, size.x / 2, Math.max(0, size.y - 1));
            else
                gc.drawLine(0, size.y / 2, Math.max(0, size.x - 1), size.y / 2);
            return;
        }
        if (imageMode && image != null && !image.isDisposed()) {
            Rectangle bounds = image.getBounds();
            int x = align(bounds.width, size.x);
            gc.drawImage(image, x, Math.max(0, (size.y - bounds.height) / 2));
            return;
        }
        gc.setFont(getFont());
        gc.setForeground(getForeground());
        int y = 0;
        for (String line : lines(gc, Math.max(1, size.x))) {
            Point extents = gc.textExtent(line);
            if (y + extents.y > size.y) break;
            gc.drawText(line, align(extents.x, size.x), y, true);
            y += extents.y;
        }
    }

    private int align(int measured, int available) {
        if (getAlignment() == SWT.RIGHT) return Math.max(0, available - measured);
        if (getAlignment() == SWT.CENTER) return Math.max(0, (available - measured) / 2);
        return 0;
    }

    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int width = 2, height = 2;
        if ((style & SWT.SEPARATOR) != 0) {
            width = (style & SWT.VERTICAL) != 0 ? 2 : 64;
            height = (style & SWT.VERTICAL) != 0 ? 64 : 2;
        } else if (imageMode && image != null && !image.isDisposed()) {
            Rectangle b = image.getBounds(); width = b.width; height = b.height;
        } else {
            Image scratch = new Image(display, 1, 1);
            try {
                GC gc = new GC(scratch);
                try {
                    gc.setFont(getFont());
                    int wrapWidth = wHint == SWT.DEFAULT ? Integer.MAX_VALUE : Math.max(1, wHint);
                    for (String line : lines(gc, wrapWidth)) {
                        Point extent = gc.textExtent(line);
                        width = Math.max(width, extent.x);
                        height += Math.max(1, extent.y);
                    }
                } finally { gc.dispose(); }
            } finally { scratch.dispose(); }
        }
        int border = getBorderWidth() * 2;
        return new Point(wHint == SWT.DEFAULT ? width + border : wHint + border,
                hHint == SWT.DEFAULT ? height + border : hHint + border);
    }
}
