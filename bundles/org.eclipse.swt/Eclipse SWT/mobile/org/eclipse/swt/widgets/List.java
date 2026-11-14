/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.util.Arrays;
import java.util.BitSet;
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

/**
 * Original SWT List API using the existing Android/iOS SWT native-backed
 * Control, GC and input event owners. The Java model is segmented into arrays;
 * VIRTUAL counts can be large without allocating one object per logical row.
 * Rendering and hit testing access only the visible row window.
 *
 * No native ScrollBar widget is implied: this List owns a software scroll
 * position, including touch/drag, wheel and keyboard navigation. Desktop
 * platform implementations and public package names remain unchanged.
 */
public class List extends Scrollable {
    private static final int PAGE_SHIFT = 8;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final int PAGE_MASK = PAGE_SIZE - 1;
    private String[][] pages = new String[0][];
    private final BitSet selected = new BitSet();
    private final BitSet resolving = new BitSet();
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
            int delta = event.y - pointerY;
            pointerY = event.y;
            if (delta != 0) {
                dragPixels += delta;
                if (Math.abs((long) dragPixels) > 3) dragging = true;
                if (dragging) {
                    int rows = dragPixels / getItemHeight();
                    if (rows != 0) {
                        scrollBy(-rows);
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
        int page = index >>> PAGE_SHIFT;
        return page >= pages.length || pages[page] == null ? null : pages[page][index & PAGE_MASK];
    }
    private void slot(int index, String value) {
        int page = index >>> PAGE_SHIFT;
        if (value == null && (page >= pages.length || pages[page] == null)) return;
        if (page >= pages.length) {
            int next = Math.max(page + 1, pages.length + Math.max(8, pages.length >>> 1));
            pages = Arrays.copyOf(pages, next);
        }
        if (pages[page] == null) pages[page] = new String[PAGE_SIZE];
        pages[page][index & PAGE_MASK] = value;
    }
    private String textAt(int index) {
        if (isDisposed() || index < 0 || index >= count) return "";
        String value = slot(index);
        if (value != null || !virtual() || resolving.get(index)) return value == null ? "" : value;
        resolving.set(index);
        try {
            Event event = new Event();
            event.index = index;
            sendEvent(SWT.SetData, event);
        } finally { resolving.clear(index); }
        return isDisposed() || index >= count ? "" : Objects.requireNonNullElse(slot(index), "");
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
        int current = focusIndex >= 0 ? focusIndex : selected.nextSetBit(0);
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
            BitSet previous = (BitSet) selected.clone();
            if (!ctrl) selected.clear();
            selected.set(Math.min(anchor, index), Math.max(anchor, index) + 1);
            changed = !selected.equals(previous);
        } else if (ctrl) {
            selected.flip(index);
            changed = true;
            anchorIndex = index;
        } else {
            changed = selected.cardinality() != 1 || !selected.get(index);
            selected.clear(); selected.set(index);
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
        for (int i = selected.previousSetBit(count - 1); i >= index && i >= 0; i = selected.previousSetBit(i - 1)) {
            selected.clear(i);
            selected.set(i + 1);
        }
        if (focusIndex >= index) focusIndex++;
        if (anchorIndex >= index) anchorIndex++;
    }
    private void shiftSelectionLeft(int start, int end) {
        int length = end - start + 1;
        selected.clear(start, end + 1);
        for (int i = selected.nextSetBit(end + 1); i >= 0; i = selected.nextSetBit(i + 1)) {
            selected.clear(i);
            selected.set(i - length);
        }
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
        for (int i = count; i > index; i--) slot(i, slot(i - 1));
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
        checkWidget(); Arrays.fill(pages, null); redraw();
    }
    /** Silently deselect one item. */
    public void deselect(int index) { checkWidget(); if (index >= 0 && index < count) { selected.clear(index); redraw(); } }
    /** Silently deselect the inclusive index range. */
    public void deselect(int start, int end) {
        checkWidget(); if (start > end || end < 0 || start >= count) return;
        selected.clear(Math.max(0, start), (int)Math.min((long)count, (long)end + 1)); redraw();
    }
    /** Silently deselect all valid indices. */
    public void deselect(int[] indices) {
        checkWidget(); if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (int index : indices) if (index >= 0 && index < count) selected.clear(index);
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
        checkWidget(); String[] result = new String[selected.cardinality()];
        int n = 0;
        for (int index = selected.nextSetBit(0); index >= 0 && index < count; index = index == Integer.MAX_VALUE ? -1 : selected.nextSetBit(index + 1)) {
            result[n++] = textAt(index);
            if (isDisposed()) break;
        }
        return n == result.length ? result : Arrays.copyOf(result, n);
    }
    /** Return the number of selected rows. */
    public int getSelectionCount() { checkWidget(); return selected.cardinality(); }
    /** Return the first selection index, or -1. */
    public int getSelectionIndex() { checkWidget(); return selected.nextSetBit(0); }
    /** Return selected indices ascending. */
    public int[] getSelectionIndices() {
        checkWidget(); int[] result = new int[selected.cardinality()]; int n = 0;
        for (int index = selected.nextSetBit(0); index >= 0; index = index == Integer.MAX_VALUE ? -1 : selected.nextSetBit(index + 1)) result[n++] = index;
        return result;
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
    public boolean isSelected(int index) { checkWidget(); return index >= 0 && index < count && selected.get(index); }
    /** Remove exactly one item. */
    public void remove(int index) { remove(index, index); }
    /** Remove an inclusive range without emitting selection events. */
    public void remove(int start, int end) {
        checkWidget();
        if (start < 0 || end < start || end >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        int length = end - start + 1;
        shiftSelectionLeft(start, end);
        for (int i = start; i < count - length; i++) slot(i, slot(i + length));
        for (int i = count - length; i < count; i++) slot(i, null);
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
        checkWidget(); Arrays.fill(pages, null); count = 0;
        selected.clear(); resolving.clear(); topIndex = 0; focusIndex = anchorIndex = -1;
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
        selected.set(index); focusIndex = anchorIndex = index; redraw();
    }
    /** Silently select an inclusive range, ignored when SINGLE and nontrivial. */
    public void select(int start, int end) {
        checkWidget();
        if (start > end || start >= count || end < 0 || (!multi() && start != end)) return;
        int a = Math.max(0, start), b = Math.min(count - 1, end);
        if (!multi()) selected.clear();
        selected.set(a, b + 1); focusIndex = b; anchorIndex = a; redraw();
    }
    /** Silently select specified indices. */
    public void select(int[] indices) {
        checkWidget(); if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (!multi() && indices.length != 1) return;
        for (int index : indices) if (index >= 0 && index < count) {
            if (!multi()) selected.clear();
            selected.set(index); focusIndex = anchorIndex = index;
        }
        redraw();
    }
    /** Silently select all rows only in SWT.MULTI mode. */
    public void selectAll() {
        checkWidget(); if (!multi()) return;
        selected.set(0, count); redraw();
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
        if (next < count) {
            selected.clear(next, count);
            int lastPage = next >>> PAGE_SHIFT;
            if (lastPage < pages.length) {
                if ((next & PAGE_MASK) != 0 && pages[lastPage] != null)
                    Arrays.fill(pages[lastPage], next & PAGE_MASK, PAGE_SIZE, null);
                int firstWholePage = lastPage + ((next & PAGE_MASK) == 0 ? 0 : 1);
                Arrays.fill(pages, firstWholePage, pages.length, null);
            }
            if (focusIndex >= next) focusIndex = -1;
            if (anchorIndex >= next) anchorIndex = -1;
        }
        count = next; topIndex = Math.min(topIndex, maxTop()); redraw();
    }
    /** Replace all items after verifying all text values. */
    public void setItems(String... values) {
        checkWidget(); if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (String value : values) if (value == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        Arrays.fill(pages, null); count = values.length;
        for (int i = 0; i < count; i++) slot(i, values[i]);
        selected.clear(); resolving.clear(); topIndex = 0; focusIndex = anchorIndex = -1;
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
    public void showSelection() { checkWidget(); int index = selected.nextSetBit(0); if (index >= 0) showIndex(index); }
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
            boolean active = selected.get(index);
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
