/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.ViewportRuntime;
import org.eclipse.swt.internal.residency.ItemSparseTable;

/**
 * Original SWT List API using the existing Android/iOS SWT native-backed
 * Control, GC and input event owners. Text and active callbacks use the original
 * sparse item table; selection uses the original packed range model. Storage
 * grows with materialized rows and selection ranges, not the highest row index.
 * Rendering and hit testing access only the visible row window.
 *
 * No native ScrollBar widget is implied: this List owns a software scroll
 * position, including touch/drag, wheel and keyboard navigation. Desktop
 * platform implementations and public package names remain unchanged.
 */
public class List extends Scrollable {
    private final ItemSparseTable items = new ItemSparseTable();
    private final ViewportRuntime.Selection selected = new ViewportRuntime.Selection();
    private final ItemSparseTable resolving = new ItemSparseTable();
    private int count, topIndex, focusIndex = -1, anchorIndex = -1;
    private int rowHeight;
    private int pointerY;
    private int dragPixels;
    private boolean pointerDown, dragging;

    /** Create a standard SWT list, with software scroll on mobile hosts. */
    public List(Composite parent, int style) {
        super(parent, WidgetStylePolicy.LIST_SELECTION.applyAsInt(style), true);
        addListener(SWT.Paint, event -> paint(event.gc));
        addListener(SWT.MouseDown, event -> {
            if (event.button != 1 || !isEnabled() || !isVisible()) return;
            pointerDown = true;
            dragging = false;
            pointerY = event.y;
            dragPixels = 0;
        });
        addListener(SWT.MouseMove, event -> {
            if (!pointerDown || !isEnabled() || !isVisible()) return;
            long delta = (long)event.y - pointerY;
            pointerY = event.y;
            if (delta != 0) {
                // A native pointer can cross the signed coordinate boundary.
                long changed = (long)dragPixels + delta;
                dragPixels = (int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, changed));
                if (Math.abs((long) dragPixels) > 3) dragging = true;
                if (dragging) {
                    int rows = dragPixels / getItemHeight();
                    if (rows != 0) {
                        scrollBy(-(long)rows);
                        dragPixels -= rows * getItemHeight();
                    }
                }
            }
        });
        addListener(SWT.MouseUp, event -> {
            if (event.button != 1 || !pointerDown) return;
            pointerDown = false;
            if (!dragging && isEnabled() && isVisible()) selectFromInput(rowAt(event.y), event.stateMask);
        });
        addListener(SWT.MouseExit, event -> pointerDown = false);
        addListener(SWT.MouseDoubleClick, event -> {
            if (!isEnabled() || !isVisible()) return;
            int index = rowAt(event.y);
            if (index >= 0) {
                Event e = new Event();
                e.index = index;
                sendEvent(SWT.DefaultSelection, e);
            }
        });
        addListener(SWT.MouseWheel, event -> scrollBy(-3L * event.count));
        addListener(SWT.KeyDown, event -> keyInput(event.keyCode, event.stateMask));
        publishAccessibility();
    }

    private void publishAccessibility() {
        display.internal_platform().setAccessibility(peer, "List",
                ACC.ROLE_LIST, isEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) publishAccessibility();
    }
    /** Native accessibility scrolls without changing the selected row. */
    void accessibilityScroll(boolean forward) {
        checkWidget();
        int rows = Math.max(1, getClientArea().height / getItemHeight());
        scrollBy(forward ? rows : -(long)rows);
    }

    private boolean multi() { return (style & SWT.MULTI) != 0; }
    private boolean virtual() { return (style & SWT.VIRTUAL) != 0; }
    private static void requireText(String text) {
        if (text == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
    }
    private void range(int index) {
        if (index < 0 || index >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
    }
    private String slot(int index) {
        return (String) items.get(index);
    }
    private void slot(int index, String value) {
        items.put(index, value);
    }
    private String textAt(int index) {
        if (isDisposed() || index < 0 || index >= count) return "";
        String value = slot(index);
        if (value != null || !virtual() || resolving.get(index) != null) return value == null ? "" : value;
        resolving.put(index, Boolean.TRUE);
        try {
            Event event = new Event();
            event.index = index;
            sendEvent(SWT.SetData, event);
        } finally { resolving.put(index, null); }
        return isDisposed() || index >= count ? "" : Objects.requireNonNullElse(slot(index), "");
    }
    private int nextSelected(int from) {
        if (from >= count) return -1;
        int low = 0, high = selected.rangeCount();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (selected.rangeEndExclusive(mid) <= from) low = mid + 1;
            else high = mid;
        }
        if (!selected.complementMode())
            return low == selected.rangeCount() ? -1 : Math.max(from, selected.rangeStart(low));
        int next = low < selected.rangeCount() && selected.rangeStart(low) <= from
                ? selected.rangeEndExclusive(low) : from;
        return next < count ? next : -1;
    }
    private int selectedInRange(int start, int end) {
        int represented = 0;
        for (int i = 0; i < selected.rangeCount(); i++) {
            int a = selected.rangeStart(i), b = selected.rangeEndExclusive(i);
            if (a >= end) break;
            if (b > start) represented += Math.min(b, end) - Math.max(a, start);
        }
        return selected.complementMode() ? end - start - represented : represented;
    }
    private int maxTop() { return Math.max(0, count - 1); }
    private void scrollBy(long amount) {
        if (count == 0 || amount == 0 || isDisposed()) return;
        long next = Math.max(0, Math.min((long)maxTop(), (long)topIndex + amount));
        if (next != topIndex) { topIndex = (int)next; redraw(); }
    }
    private int rowAt(int y) {
        if (y < 0 || y >= getClientArea().height) return -1;
        long row = (long)topIndex + y / Math.max(1, getItemHeight());
        return row < count ? (int)row : -1;
    }
    private void keyInput(int key, int stateMask) {
        if (!isEnabled() || !isVisible() || count == 0) return;
        int current = focusIndex >= 0 ? focusIndex : nextSelected(0);
        if (current < 0) current = 0;
        int pageRows = Math.max(1, getClientArea().height / getItemHeight());
        int target = switch (key) {
            case SWT.ARROW_UP -> Math.max(0, current - 1);
            case SWT.ARROW_DOWN -> Math.min(count - 1, current + 1);
            case SWT.PAGE_UP -> Math.max(0, current - pageRows);
            case SWT.PAGE_DOWN -> (int)Math.min((long)count - 1, (long)current + pageRows);
            case SWT.HOME -> 0;
            case SWT.END -> count - 1;
            default -> -1;
        };
        if (target >= 0) selectFromInput(target, stateMask);
    }
    private void selectFromInput(int index, int mask) {
        if (index < 0 || index >= count || isDisposed()) return;
        boolean ctrl = multi() && (mask & SWT.CTRL) != 0;
        boolean shift = multi() && (mask & SWT.SHIFT) != 0;
        boolean changed;
        if (shift) {
            int anchor = anchorIndex >= 0 ? anchorIndex : focusIndex >= 0 ? focusIndex : index;
            int start = Math.min(anchor, index), end = Math.max(anchor, index) + 1;
            int before = selected.selectedCount();
            int covered = selectedInRange(start, end);
            changed = ctrl ? covered != end - start : before != end - start || covered != before;
            if (!ctrl) selected.clear();
            selected.selectRange(start, end);
        } else if (ctrl) {
            selected.setSelected(index, !selected.isSelected(index));
            changed = true;
            anchorIndex = index;
        } else {
            changed = selected.selectedCount() != 1 || !selected.isSelected(index);
            selected.clear(); selected.setSelected(index, true);
            anchorIndex = index;
        }
        focusIndex = index;
        showIndex(index);
        if (changed) {
            redraw();
            Event event = new Event();
            event.index = index;
            sendEvent(SWT.Selection, event);
        }
    }
    private void showIndex(int index) {
        int rows = Math.max(1, getClientArea().height / getItemHeight());
        if (index < topIndex) scrollBy((long)index - topIndex);
        else if ((long)index >= (long)topIndex + rows) scrollBy((long)index - topIndex - rows + 1);
    }
    private void shiftSelectionRight(int index) {
        selected.insert(index, 1);
        if (focusIndex >= index) focusIndex++;
        if (anchorIndex >= index) anchorIndex++;
    }
    private void shiftSelectionLeft(int start, int end) {
        int length = end - start + 1;
        selected.remove(start, length);
        focusIndex = focusIndex >= start && focusIndex <= end ? -1 : focusIndex > end ? focusIndex - length : focusIndex;
        anchorIndex = anchorIndex >= start && anchorIndex <= end ? -1 : anchorIndex > end ? anchorIndex - length : anchorIndex;
    }

    /** Append a nonnull text item. */
    public void add(String string) { add(string, count); }
    /** Insert at a zero-based index including the current end. */
    public void add(String string, int index) {
        checkWidget(); requireText(string);
        if (index < 0 || index > count) SWT.error(SWT.ERROR_INVALID_RANGE);
        if (count == Integer.MAX_VALUE) SWT.error(SWT.ERROR_ITEM_NOT_ADDED);
        shiftSelectionRight(index);
        items.shift(index, 1);
        slot(index, string);
        count++;
        if (index < topIndex) topIndex++;
        redraw();
    }
    /** Register the original SWT selection callbacks. */
    public void addSelectionListener(SelectionListener listener) { addTypedListener(listener, SWT.Selection, SWT.DefaultSelection); }
    /** Unset one text cell (virtual lists reload it on demand). */
    public void clear(int index) { checkWidget(); range(index); slot(index, virtual() ? null : ""); redraw(); }
    /** Unset all text cells without changing the logical item count or selection. */
    public void clearAll() {
        checkWidget(); items.truncate(0); redraw();
    }
    /** Silently deselect one item. */
    public void deselect(int index) { checkWidget(); if (index >= 0 && index < count) { selected.setSelected(index, false); redraw(); } }
    /** Silently deselect the inclusive index range. */
    public void deselect(int start, int end) {
        checkWidget(); if (start > end || end < 0 || start >= count) return;
        selected.deselectRange(Math.max(0, start), (int)Math.min((long)count, (long)end + 1)); redraw();
    }
    /** Silently deselect all valid indices. */
    public void deselect(int[] indices) {
        checkWidget(); if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (int index : indices) if (index >= 0 && index < count) selected.setSelected(index, false);
        redraw();
    }
    /** Silently deselect every row. */
    public void deselectAll() { checkWidget(); selected.clear(); redraw(); }
    /** Return the focus row, or -1 when none is focused. */
    public int getFocusIndex() { checkWidget(); return focusIndex; }
    /** Read a cell, requesting SWT.SetData on a virtual cache miss. */
    public String getItem(int index) { checkWidget(); range(index); return textAt(index); }
    /** Return the number of logical items. */
    public int getItemCount() { checkWidget(); return count; }
    /** Return the measured height of one text row. */
    public int getItemHeight() {
        checkWidget();
        if (rowHeight == 0) {
            Image image = new Image(display, 1, 1);
            try {
                GC gc = new GC(image);
                try { gc.setFont(getFont()); rowHeight = Math.max(12, gc.textExtent("Mg").y + 4); }
                finally { gc.dispose(); }
            } finally { image.dispose(); }
        }
        return rowHeight;
    }
    /** Copy the text items. A huge virtual list may require large output memory. */
    public String[] getItems() {
        checkWidget();
        String[] copy = new String[count];
        for (int i = 0; i < count; i++) { copy[i] = textAt(i); if (isDisposed()) break; }
        return copy;
    }
    /** Return the selected texts in ascending row order. */
    public String[] getSelection() {
        checkWidget(); String[] result = new String[selected.selectedCount()];
        int n = 0;
        for (int index = nextSelected(0); index >= 0 && index < count; index = nextSelected(index + 1)) {
            // An earlier SetData callback may select additional later rows.
            if (n == result.length)
                result = Arrays.copyOf(result, Math.max(n + 1, n + (n >>> 1)));
            result[n++] = textAt(index);
            if (isDisposed()) break;
        }
        return n == result.length ? result : Arrays.copyOf(result, n);
    }
    /** Return the number of selected rows. */
    public int getSelectionCount() { checkWidget(); return selected.selectedCount(); }
    /** Return the first selection index, or -1. */
    public int getSelectionIndex() { checkWidget(); return nextSelected(0); }
    /** Return selected indices ascending. */
    public int[] getSelectionIndices() {
        checkWidget(); return selected.toArray();
    }
    /** Return the first visible logical row. */
    public int getTopIndex() { checkWidget(); return topIndex; }
    /** Find first occurrence from the beginning. */
    public int indexOf(String string) { return indexOf(string, 0); }
    /** Find first occurrence at or after start; return -1 when absent. */
    public int indexOf(String string, int start) {
        checkWidget(); requireText(string);
        for (int i = Math.max(0, start); i < count; i++) if (string.equals(textAt(i))) return i;
        return -1;
    }
    /** Test whether a row is selected. */
    public boolean isSelected(int index) { checkWidget(); return index >= 0 && index < count && selected.isSelected(index); }
    /** Remove exactly one item. */
    public void remove(int index) { remove(index, index); }
    /** Remove an inclusive range without emitting selection events. */
    public void remove(int start, int end) {
        checkWidget();
        if (start < 0 || end < start || end >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        int length = end - start + 1;
        shiftSelectionLeft(start, end);
        items.removeRange(start, end + 1);
        items.shift(end + 1, -length);
        count -= length;
        topIndex = Math.min(topIndex, maxTop()); redraw();
    }
    /** Remove the first equal item or report SWT.ERROR_INVALID_ARGUMENT. */
    public void remove(String string) {
        checkWidget(); requireText(string);
        int index = indexOf(string);
        if (index < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        remove(index);
    }
    /** Remove distinct indices atomically after validating every index. */
    public void remove(int[] indices) {
        checkWidget();
        if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int[] sorted = indices.clone(); Arrays.sort(sorted);
        for (int index : sorted) range(index);
        for (int i = sorted.length - 1; i >= 0; i--) if (i == sorted.length - 1 || sorted[i] != sorted[i + 1]) remove(sorted[i]);
    }
    /** Remove all items and their selection. */
    public void removeAll() {
        checkWidget(); items.truncate(0); count = 0;
        selected.clear(); selected.setLogicalCount(0); resolving.truncate(0); topIndex = 0; focusIndex = anchorIndex = -1;
        redraw();
    }
    /** Remove registered selection callbacks. */
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    /** Silently select one row, preserving other selections in SWT.MULTI. */
    public void select(int index) {
        checkWidget();
        if (index < 0 || index >= count) return;
        if (!multi()) selected.clear();
        selected.setSelected(index, true); focusIndex = anchorIndex = index; redraw();
    }
    /** Silently select an inclusive range, ignored when SINGLE and nontrivial. */
    public void select(int start, int end) {
        checkWidget();
        if (start > end || start >= count || end < 0 || (!multi() && start != end)) return;
        int a = Math.max(0, start), b = Math.min(count - 1, end);
        if (!multi()) selected.clear();
        selected.selectRange(a, b + 1); focusIndex = b; anchorIndex = a; redraw();
    }
    /** Silently select specified indices. */
    public void select(int[] indices) {
        checkWidget(); if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (!multi() && indices.length != 1) return;
        for (int index : indices) if (index >= 0 && index < count) {
            if (!multi()) selected.clear();
            selected.setSelected(index, true); focusIndex = anchorIndex = index;
        }
        redraw();
    }
    /** Silently select all rows only in SWT.MULTI mode. */
    public void selectAll() {
        checkWidget(); if (!multi()) return;
        selected.selectAll(); redraw();
    }
    /** Replace a single text cell without changing its selected state. */
    public void setItem(int index, String string) {
        checkWidget(); range(index); requireText(string);
        slot(index, string); redraw();
    }
    /** Resize the logical list; absent new entries start as empty text. */
    public void setItemCount(int requested) {
        checkWidget(); int next = Math.max(0, requested);
        if (next == count) return;
        selected.setLogicalCount(next);
        if (next < count) {
            items.truncate(next);
            if (focusIndex >= next) focusIndex = -1;
            if (anchorIndex >= next) anchorIndex = -1;
        }
        count = next; topIndex = Math.min(topIndex, maxTop()); redraw();
    }
    /** Replace all items after verifying all text values. */
    public void setItems(String... values) {
        checkWidget(); if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (String value : values) if (value == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        items.truncate(0); count = values.length;
        for (int i = 0; i < count; i++) slot(i, values[i]);
        selected.clear(); selected.setLogicalCount(count); resolving.truncate(0); topIndex = 0; focusIndex = anchorIndex = -1;
        redraw();
    }
    /** Replace selection with one index. */
    public void setSelection(int index) { checkWidget(); selected.clear(); select(index); showSelection(); }
    /** Replace selection with inclusive range. */
    public void setSelection(int start, int end) { checkWidget(); selected.clear(); select(start, end); showSelection(); }
    /** Replace selection with the supplied indices. */
    public void setSelection(int[] indices) { checkWidget(); selected.clear(); select(indices); showSelection(); }
    /** Replace selection with all matching texts, respecting SINGLE/MULTI. */
    public void setSelection(String[] strings) {
        checkWidget(); if (strings == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        selected.clear(); focusIndex = anchorIndex = -1;
        if (multi() || strings.length == 1) {
            for (String string : strings) {
                if (string == null) continue;
                for (int start = 0, i; (i = indexOf(string, start)) >= 0; start = i + 1) {
                    select(i); if (!multi()) break;
                }
            }
        }
        showSelection();
    }
    /** Scroll the receiver to a valid zero-based top row. */
    public void setTopIndex(int index) { checkWidget(); if (index >= 0 && index < count && index != topIndex) { topIndex = index; redraw(); } }
    /** Ensure the first selected row is visible. */
    public void showSelection() { checkWidget(); int index = nextSelected(0); if (index >= 0) showIndex(index); }
    /** Font change invalidates the native-row height cache. */
    @Override public void setFont(Font font) { super.setFont(font); rowHeight = 0; redraw(); }
    /** SWT preferred size for a text list. */
    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget(); int border = getBorderWidth() * 2;
        return new Point((wHint == SWT.DEFAULT ? 160 : Math.max(0, wHint)) + border,
                (hHint == SWT.DEFAULT ? Math.max(120, getItemHeight() * 6) : Math.max(0, hHint)) + border);
    }

    private void paint(GC gc) {
        if (isDisposed()) return;
        Rectangle area = getClientArea();
        int height = getItemHeight();
        if (area.width <= 0 || area.height <= 0) return;
        int visible = Math.min(count - topIndex, area.height / height + 2);
        Color normalFg = getForeground();
        Color normalBg = gc.getBackground();
        Color selectedBg = null, selectedFg = null;
        for (int offset = 0; offset < visible && !isDisposed(); offset++) {
            int index = topIndex + offset;
            if (index >= count) break;
            int y = offset * height;
            String text = textAt(index);
            if (isDisposed()) return;
            boolean active = index < count && selected.isSelected(index);
            if (active) {
                if (selectedBg == null) {
                    selectedBg = display.getSystemColor(SWT.COLOR_LIST_SELECTION);
                    selectedFg = display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
                }
                gc.setBackground(selectedBg);
                gc.fillRectangle(0, y, area.width, Math.min(height, area.height - y));
                gc.setForeground(selectedFg);
            } else {
                gc.setBackground(normalBg);
                gc.setForeground(normalFg);
            }
            gc.drawText(text, 3, y + 2, true);
        }
        gc.setForeground(normalFg);
        gc.setBackground(normalBg);
    }
}
