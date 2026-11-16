/*******************************************************************************
 * Copyright (c) 2025 Syntevo GmbH and others; 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 *
 * Derived from SWT Initiative31 Link and its renderer. The original mobile
 * SWT owner paints with the existing Android/UIKit Canvas, never a second UI.
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.ArrayList;
import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

/** Original SWT Link API, using one native peer and the Initiative31 parser. */
public class Link extends Control {
    private String text = "", displayText = "";
    // Parallel segment arrays and packed rectangles; no per-link native peers.
    // Null segment means a hard line break; null target is ordinary text.
    private String[] segments = new String[0], targets = new String[0];
    private int[] hitRects = new int[0];
    private Color linkForeground;
    private int alignment, leftMargin = 3, rightMargin = 3, topMargin = 3, bottomMargin = 3;
    private int armed = -1;

    public Link(Composite parent, int style) {
        super(parent, WidgetStylePolicy.WITHOUT_SCROLLBARS.applyAsInt(style));
        alignment = (style & SWT.CENTER) != 0 ? SWT.CENTER
                : (style & SWT.RIGHT) != 0 ? SWT.RIGHT : SWT.LEFT;
        addListener(SWT.Paint, this::paintLink);
        addListener(SWT.MouseDown, e -> {
            if (e.button == 1 && isEnabled()) armed = hitTest(e.x, e.y);
        });
        addListener(SWT.MouseUp, e -> {
            int selected = armed;
            armed = -1;
            if (e.button == 1 && selected >= 0 && selected == hitTest(e.x, e.y))
                activate(selected);
        });
        addListener(SWT.KeyUp, e -> {
            if (e.character == '\r' || e.character == ' ') nativeActivate();
        });
        publishAccessibility();
    }

    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    public String getText() { checkWidget(); return text; }
    public void setText(String value) {
        checkWidget();
        if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (text.equals(value)) return;
        text = value;
        ArrayList<String> parts = new ArrayList<>(), links = new ArrayList<>();
        StringBuilder visible = new StringBuilder(value.length());
        for (LinkParser.Part part : LinkParser.parse(value)) {
            String displayed = unescapeMnemonics(part.text());
            int from = 0;
            for (int i = 0; i < displayed.length(); i++) {
                if (displayed.charAt(i) != '\n') continue;
                if (i > from) {
                    String token = displayed.substring(from, i);
                    parts.add(token); links.add(part.linkData()); visible.append(token);
                }
                parts.add(null); links.add(null); visible.append('\n');
                from = i + 1;
            }
            if (from < displayed.length()) {
                String token = displayed.substring(from);
                parts.add(token); links.add(part.linkData()); visible.append(token);
            }
        }
        segments = parts.toArray(String[]::new);
        targets = links.toArray(String[]::new);
        hitRects = new int[4 * segments.length];
        displayText = visible.toString();
        armed = -1;
        publishAccessibility();
        redraw();
    }

    private static String unescapeMnemonics(String value) {
        StringBuilder b = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '&') { b.append(c); continue; }
            if (i + 1 < value.length() && value.charAt(i + 1) == '&') {
                b.append('&'); i++;
            }
        }
        return b.toString();
    }
    private void publishAccessibility() {
        display.internal_platform().setAccessibility(peer, displayText,
                ACC.ROLE_LINK, getEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) publishAccessibility();
    }
    public Color getLinkForeground() {
        checkWidget();
        return linkForeground == null ? display.getSystemColor(SWT.COLOR_LINK_FOREGROUND) : linkForeground;
    }
    public void setLinkForeground(Color color) {
        checkWidget();
        if (color != null && (color.isDisposed() || color.getDevice() != display))
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        linkForeground = color;
        redraw();
    }
    public int getAlignment() { checkWidget(); return alignment; }
    public void setAlignment(int value) {
        checkWidget();
        if (value != SWT.LEFT && value != SWT.CENTER && value != SWT.RIGHT)
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        alignment = value;
        redraw();
    }
    public int getLeftMargin() { checkWidget(); return leftMargin; }
    public int getRightMargin() { checkWidget(); return rightMargin; }
    public int getTopMargin() { checkWidget(); return topMargin; }
    public int getBottomMargin() { checkWidget(); return bottomMargin; }
    public void setLeftMargin(int value) { checkWidget(); if (value >= 0) { leftMargin = value; redraw(); } }
    public void setRightMargin(int value) { checkWidget(); if (value >= 0) { rightMargin = value; redraw(); } }
    public void setTopMargin(int value) { checkWidget(); if (value >= 0) { topMargin = value; redraw(); } }
    public void setBottomMargin(int value) { checkWidget(); if (value >= 0) { bottomMargin = value; redraw(); } }
    public void setMargins(int left, int top, int right, int bottom) {
        checkWidget();
        leftMargin = Math.max(0, left);
        topMargin = Math.max(0, top);
        rightMargin = Math.max(0, right);
        bottomMargin = Math.max(0, bottom);
        redraw();
    }

    private int hitTest(int x, int y) {
        for (int i = 0; i < segments.length; i++) {
            if (targets[i] == null) continue;
            int p = 4 * i;
            if (x >= hitRects[p] && y >= hitRects[p + 1]
                    && x - hitRects[p] < hitRects[p + 2]
                    && y - hitRects[p + 1] < hitRects[p + 3]) return i;
        }
        return -1;
    }
    private void activate(int i) {
        if (isDisposed() || !isEnabled() || !isVisible()
                || i < 0 || i >= targets.length || targets[i] == null) return;
        Event e = new Event();
        e.text = targets[i].isEmpty() ? segments[i] : targets[i];
        sendEvent(SWT.Selection, e);
    }
    /** Invoked on the SWT Display thread by Android/UIKit accessibility. */
    void nativeActivate() {
        if (isDisposed() || !isEnabled() || !isVisible()) return;
        for (int i = 0; i < targets.length; i++)
            if (targets[i] != null && segments[i] != null && !segments[i].isEmpty()) {
                activate(i); return;
            }
    }

    private void paintLink(Event event) {
        if (isDisposed()) return;
        Arrays.fill(hitRects, 0);
        GC gc = event.gc;
        gc.setFont(getFont());
        Color saved = gc.getForeground(), regular = getForeground(), linked = getLinkForeground();
        Point size = getSize();
        int height = Math.max(1, gc.textExtent("Mg").y), y = topMargin;
        try {
            for (int begin = 0; begin < segments.length && y < size.y - bottomMargin;) {
                int end = begin, width = 0;
                while (end < segments.length && segments[end] != null)
                    width += gc.textExtent(segments[end++]).x;
                int available = Math.max(0, size.x - leftMargin - rightMargin);
                int x = leftMargin + (alignment == SWT.RIGHT ? Math.max(0, available - width)
                        : alignment == SWT.CENTER ? Math.max(0, (available - width) / 2) : 0);
                for (int i = begin; i < end; i++) {
                    Point extent = gc.textExtent(segments[i]);
                    boolean link = targets[i] != null;
                    gc.setForeground(link && isEnabled() ? linked : regular);
                    gc.drawText(segments[i], x, y, true);
                    if (link) {
                        gc.drawLine(x, y + extent.y - 1, x + extent.x, y + extent.y - 1);
                        int p = i * 4;
                        hitRects[p] = x; hitRects[p + 1] = y;
                        hitRects[p + 2] = extent.x; hitRects[p + 3] = height;
                    }
                    x += extent.x;
                }
                begin = end + 1;
                y += height;
            }
        } finally { gc.setForeground(saved); }
    }

    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int width = 0, height = 0, lineWidth = 0;
        Image sample = new Image(display, 1, 1);
        try {
            GC gc = new GC(sample);
            try {
                gc.setFont(getFont());
                int lineHeight = Math.max(1, gc.textExtent("Mg").y);
                height = lineHeight;
                for (String segment : segments) {
                    if (segment == null) {
                        width = Math.max(width, lineWidth);
                        lineWidth = 0; height += lineHeight;
                    } else lineWidth += gc.textExtent(segment).x;
                }
                width = Math.max(width, lineWidth);
            } finally { gc.dispose(); }
        } finally { sample.dispose(); }
        int border = 2 * getBorderWidth();
        width += leftMargin + rightMargin + border;
        height += topMargin + bottomMargin + border;
        return new Point(wHint == SWT.DEFAULT ? width : wHint + border,
                hHint == SWT.DEFAULT ? height : hHint + border);
    }
}
