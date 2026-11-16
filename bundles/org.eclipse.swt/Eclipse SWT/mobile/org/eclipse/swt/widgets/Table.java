/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.ExceptionStash;
import org.eclipse.swt.internal.mobile.MobileTableSelection;

/**
 * Original SWT Table API on Android/iOS, with a Java-owned sparse logical
 * row registry, index masks and the existing native-backed Control/GC path.
 *
 * Physical storage is arrays of materialized TableItem identities, never an
 * array or object per logical SWT.VIRTUAL row. The sorted row-index array
 * permits 1,000,000,000 logical rows while keeping only viewport/explicitly
 * requested rows resident. Rendering, native touch and accessibility events
 * flow through the same original SWT Display thread.
 *
 * Native ScrollBar peers, full per-cell accessible hierarchy, editor/DND,
 * desktop-equivalent column dragging and owner-draw measuring are additional
 * release gates, not silently simulated by this class.
 */
public class Table extends Composite {
    private int count, topIndex, focusIndex = -1, anchorIndex = -1;
    private int[] rowKeys = new int[8];
    private TableItem[] rowValues = new TableItem[8];
    private int materialized;
    // Interval-backed sparse selection: selecting one billion rows creates
    // one run, not a billion entries or a high-index BitSet allocation.
    private final MobileTableSelection selected = new MobileTableSelection();
    private TableColumn[] columns = new TableColumn[4];
    private int columnCount;
    private int[] order = new int[0];
    private TableColumn sortColumn;
    private int sortDirection = SWT.NONE;
    private int rowHeight, headerHeight;
    private boolean headerVisible, linesVisible, mutatingRows, disposingChildren;
    private Color headerBackground, headerForeground;
    private boolean pointerDown, dragged;
    private int pointerY, dragPixels;

    /** Create an original SWT Table with native-backed software scrolling. */
    public Table(Composite parent, int style) {
        super(parent, WidgetStylePolicy.TABLE_WINDOWS.applyAsInt(style), true);
        addListener(SWT.Paint, event -> paintTable(event.gc));
        addListener(SWT.MouseDown, event -> {
            if (event.button != 1 || !isEnabled() || !isVisible()) return;
            pointerDown = true; pointerY = event.y; dragPixels = 0; dragged = false;
        });
        addListener(SWT.MouseMove, event -> {
            if (!pointerDown || !isEnabled() || !isVisible()) return;
            long delta = (long)event.y - pointerY;
            pointerY = event.y;
            if (delta == 0) return;
            // Compute the coordinate difference before narrowing to the pixel accumulator.
            long changed = (long)dragPixels + delta;
            dragPixels = (int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, changed));
            if (Math.abs((long)dragPixels) > 3) dragged = true;
            if (dragged) {
                int rows = dragPixels / getItemHeight();
                if (rows != 0) {
                    scrollBy(-(long)rows);
                    dragPixels -= rows * getItemHeight();
                }
            }
        });
        addListener(SWT.MouseExit, event -> { pointerDown = false; dragPixels = 0; });
        addListener(SWT.MouseUp, event -> {
            if (event.button != 1 || !pointerDown) return;
            pointerDown = false;
            if (!dragged && isEnabled() && isVisible()) {
                if (headerVisible && event.y < getHeaderHeight()) {
                    TableColumn column = columnAt(event.x);
                    if (column != null) column.sendEvent(SWT.Selection, new Event());
                } else selectFromUser(rowAt(event.y), event.stateMask);
            }
        });
        addListener(SWT.MouseDoubleClick, event -> {
            int index = rowAt(event.y);
            if (index >= 0 && isEnabled()) {
                Event e = new Event(); e.index = index; e.item = getItem(index);
                sendEvent(SWT.DefaultSelection, e);
            }
        });
        addListener(SWT.MouseVerticalWheel, event -> scrollBy(-3L * event.count));
        addListener(SWT.KeyDown, event -> keyInput(event.keyCode, event.stateMask));
        publishAccessibility();
    }

    private boolean virtual() { return (style & SWT.VIRTUAL) != 0; }
    private boolean multi() { return (style & SWT.MULTI) != 0; }
    private int locate(int index) { return Arrays.binarySearch(rowKeys, 0, materialized, index); }
    private void capacity(int target) {
        if (target <= rowKeys.length) return;
        int size = Math.max(target, rowKeys.length + Math.max(8, rowKeys.length >>> 1));
        rowKeys = Arrays.copyOf(rowKeys, size);
        rowValues = Arrays.copyOf(rowValues, size);
    }
    private void put(TableItem item, int index) {
        int position = locate(index);
        if (position >= 0) throw new IllegalStateException("Duplicate SWT Table row identity " + index);
        position = -position - 1;
        capacity(materialized + 1);
        System.arraycopy(rowKeys, position, rowKeys, position + 1, materialized - position);
        System.arraycopy(rowValues, position, rowValues, position + 1, materialized - position);
        rowKeys[position] = index; rowValues[position] = item;
        materialized++;
    }
    private TableItem materialize(int index) {
        int location = locate(index);
        if (location >= 0) return rowValues[location];
        TableItem item = new TableItem(this, SWT.NONE, index, false);
        item.cached = !virtual();
        put(item, index);
        return item;
    }
    /** Fire original SWT.SetData only once per uncached virtual row. */
    void ensureData(TableItem item) {
        checkWidget();
        if (item == null || item.isDisposed() || item.cached || item.settingData || !virtual()) return;
        int index = item.rowIndex;
        if (index < 0 || index >= count) return;
        item.settingData = true;
        try {
            Event event = new Event();
            event.index = index; event.item = item;
            sendEvent(SWT.SetData, event);
            int slot = !isDisposed() && !item.isDisposed() ? locate(index) : -1;
            if (slot >= 0 && rowValues[slot] == item && item.rowIndex == index)
                item.cached = true;
        } finally { item.settingData = false; }
    }

    /** Insert a newly constructed nonvirtual TableItem. */
    void insertItem(TableItem item, int index) {
        checkWidget();
        if (index < 0 || index > count) SWT.error(SWT.ERROR_INVALID_RANGE);
        if (count == Integer.MAX_VALUE) SWT.error(SWT.ERROR_ITEM_NOT_ADDED);
        for (int i = 0; i < materialized; i++) {
            if (rowKeys[i] >= index) {
                rowKeys[i]++;
                rowValues[i].rowIndex++;
            }
        }
        selected.insertRow(index);
        if (focusIndex >= index) focusIndex++;
        if (anchorIndex >= index) anchorIndex++;
        if (topIndex >= index && count > 0) topIndex++;
        count++;
        put(item, index);
        redraw();
        if (count == 1) sendEvent(SWT.EmptinessChanged, new Event());
    }
    /** Remove a disposed TableItem and its logical row. */
    void removeItem(TableItem item) {
        if (mutatingRows || disposingChildren || isDisposed()) return;
        int index = item.rowIndex;
        int slot = index >= 0 && index < count ? locate(index) : -1;
        if (slot >= 0 && rowValues[slot] == item) remove(index);
    }
    private void deleteRange(int first, int last) {
        if (first < 0 || last < first || last >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        int length = last - first + 1, output = 0;
        mutatingRows = true;
        try (ExceptionStash failures = new ExceptionStash()) {
            for (int i = 0; i < materialized; i++) {
                int key = rowKeys[i];
                TableItem item = rowValues[i];
                if (key >= first && key <= last) {
                    if (!item.isDisposed()) {
                        try { item.dispose(); }
                        catch (RuntimeException | Error problem) { failures.stash(problem); }
                    }
                } else {
                    if (key > last) {
                        key -= length;
                        item.rowIndex = key;
                    }
                    rowKeys[output] = key;
                    rowValues[output++] = item;
                }
            }
            Arrays.fill(rowValues, output, materialized, null);
            materialized = output;
            selected.removeRows(first, last);
            count -= length;
            topIndex = Math.min(topIndex, Math.max(0, count - 1));
            focusIndex = transformAfterRemove(focusIndex, first, last);
            anchorIndex = transformAfterRemove(anchorIndex, first, last);
        } finally { mutatingRows = false; }
        redraw();
        if (count == 0) sendEvent(SWT.EmptinessChanged, new Event());

    }
    private static int transformAfterRemove(int index, int start, int end) {
        return index < start ? index : index <= end ? -1 : index - (end - start + 1);
    }

    /** Insert a TableColumn at its physical index. */
    void insertColumn(TableColumn column, int index) {
        checkWidget();
        if (index < 0 || index > columnCount) SWT.error(SWT.ERROR_INVALID_RANGE);
        if (columnCount == columns.length)
            columns = Arrays.copyOf(columns, columnCount + Math.max(4, columnCount >>> 1));
        System.arraycopy(columns, index, columns, index + 1, columnCount - index);
        columns[index] = column;
        for (int i = 0; i < materialized; i++)
            rowValues[i].columnInserted(index, columnCount);
        int[] next = new int[columnCount + 1];
        for (int i = 0; i < columnCount; i++)
            next[i] = order[i] >= index ? order[i] + 1 : order[i];
        next[columnCount] = index;
        order = next;
        columnCount++;
        redraw();
    }
    /** Remove a TableColumn and update the display-order permutation. */
    void removeColumn(TableColumn column) {
        if (disposingChildren || isDisposed()) return;
        int index = -1;
        for (int i = 0; i < columnCount; i++) if (columns[i] == column) { index = i; break; }
        if (index < 0) return;
        for (int i = 0; i < materialized; i++)
            rowValues[i].columnRemoved(index, columnCount);
        System.arraycopy(columns, index + 1, columns, index, columnCount - index - 1);
        columns[--columnCount] = null;
        int[] next = new int[columnCount];
        int n = 0;
        for (int old : order) {
            if (old == index) continue;
            next[n++] = old > index ? old - 1 : old;
        }
        order = next;
        if (sortColumn == column) sortColumn = null;
        redraw();
    }

    private void selection(int index, boolean value) {
        if (index < 0 || index >= count) return;
        if (value) selected.select(index, index); else selected.deselect(index, index);
    }
    /** Query a selected row using binary search over compact intervals. */
    public boolean isSelected(int index) {
        checkWidget();
        return index >= 0 && index < count && selected.contains(index);
    }
    private void clearSelection() { selected.clear(); }
    private int firstSelected() { return selected.first(); }

    private int headerOffset() { return headerVisible ? getHeaderHeight() : 0; }
    private int rowsInView() {
        return Math.max(1, (getClientArea().height - headerOffset()) / getItemHeight());
    }
    private void showIndex(int index) {
        int rows = rowsInView();
        if (index < topIndex) setTopIndex(index);
        else if ((long)index >= (long)topIndex + rows)
            setTopIndex((int)Math.max(0L, (long)index - rows + 1));
    }
    private int rowAt(int y) {
        int adjusted = y - headerOffset();
        if (adjusted < 0 || adjusted >= getClientArea().height - headerOffset()) return -1;
        long index = (long)topIndex + adjusted / getItemHeight();
        return index >= count ? -1 : (int)index;
    }
    private TableColumn columnAt(int x) {
        int offset = 0;
        for (int position : order) {
            TableColumn column = columns[position];
            int width = column.getWidth();
            if (x >= offset && (long)x < (long)offset + width) return column;
            offset = (int)Math.min(Integer.MAX_VALUE, (long)offset + width);
        }
        return null;
    }
    private void scrollBy(long delta) {
        if (delta == 0 || count == 0) return;
        long next = Math.max(0L, Math.min((long)count - 1, (long)topIndex + delta));
        setTopIndex((int)next);
    }
    /** Native Android/UIKit accessibility paging without selection mutation. */
    void accessibilityScroll(boolean forward) {
        checkWidget();
        scrollBy(forward ? rowsInView() : -(long)rowsInView());
    }
    private void keyInput(int key, int modifiers) {
        if (!isEnabled() || !isVisible() || count == 0) return;
        int current = focusIndex >= 0 ? focusIndex : firstSelected();
        if (current < 0) current = 0;
        int target = switch (key) {
            case SWT.ARROW_UP -> Math.max(0, current - 1);
            case SWT.ARROW_DOWN -> Math.min(count - 1, current + 1);
            case SWT.PAGE_UP -> Math.max(0, current - rowsInView());
            case SWT.PAGE_DOWN -> (int)Math.min((long)count - 1, (long)current + rowsInView());
            case SWT.HOME -> 0;
            case SWT.END -> count - 1;
            default -> -1;
        };
        if (target >= 0) selectFromUser(target, modifiers);
    }
    private void selectFromUser(int index, int modifiers) {
        if (index < 0 || index >= count || !isEnabled()) return;
        boolean control = multi() && (modifiers & SWT.CTRL) != 0;
        boolean shift = multi() && (modifiers & SWT.SHIFT) != 0;
        boolean changed;
        int beforeCount = getSelectionCount();
        boolean beforeSelected = isSelected(index);
        if (shift) {
            int anchor = anchorIndex >= 0 ? anchorIndex : focusIndex >= 0 ? focusIndex : index;
            if (!control) clearSelection();
            selected.select(Math.min(index, anchor), Math.max(index, anchor));
            changed = true;
        } else if (control) {
            selection(index, !beforeSelected);
            anchorIndex = index;
            changed = true;
        } else {
            clearSelection(); selection(index, true);
            anchorIndex = index;
            changed = beforeCount != 1 || !beforeSelected;
        }
        focusIndex = index;
        showIndex(index);
        if (!changed) return;
        redraw();
        Event event = new Event(); event.index = index; event.item = getItem(index);
        sendEvent(SWT.Selection, event);
    }

    private int columnX(int column) {
        if (column < 0) return 0;
        long x = 0;
        for (int position : order) {
            if (position == column) break;
            x += columns[position].getWidth();
        }
        return (int)Math.min(Integer.MAX_VALUE, x);
    }
    Rectangle itemBounds(TableItem item, int column) {
        checkWidget();
        int row = item.rowIndex;
        long y = (long)headerOffset() + (long)(row - (long)topIndex) * getItemHeight();
        if (y < Integer.MIN_VALUE || y > Integer.MAX_VALUE) return new Rectangle(0, -1, 0, 0);
        int x = columnX(column);
        int width = columnCount == 0 || column < 0
                ? getClientArea().width : column < columnCount ? columns[column].getWidth() : 0;
        return new Rectangle(x, (int)y, Math.max(0, width), getItemHeight());
    }

    private void paintTable(GC gc) {
        if (isDisposed()) return;
        Rectangle area = getClientArea();
        if (area.width <= 0 || area.height <= 0) return;
        gc.setFont(getFont());
        Color defaultForeground = gc.getForeground(), defaultBackground = gc.getBackground();
        if (headerVisible) {
            Color fill = headerBackground == null ? getBackground() : headerBackground;
            gc.setBackground(fill);
            gc.fillRectangle(0, 0, area.width, headerOffset());
            gc.setForeground(headerForeground == null ? getForeground() : headerForeground);
            if (columnCount == 0) gc.drawText("", 3, 3, true);
            else {
                long x = 0;
                for (int columnIndex : order) {
                    TableColumn col = columns[columnIndex];
                    if (x < area.width) {
                        int textX = (int)x + 4;
                        Image headerImage = col.getImage();
                        if (headerImage != null && !headerImage.isDisposed()) {
                            Rectangle bounds = headerImage.getBounds();
                            gc.drawImage(headerImage, textX, 2);
                            textX += bounds.width + 4;
                        }
                        String title = col.getText();
                        if (sortColumn == col && sortDirection != SWT.NONE)
                            title += sortDirection == SWT.UP ? " ▲" : " ▼";
                        gc.drawText(title, textX, 3, true);
                    }
                    x += col.getWidth();
                    if (x < area.width && linesVisible) gc.drawLine((int)x, 0, (int)x, headerOffset());
                }
            }
        }
        int height = getItemHeight();
        long viewport = (long)area.height - headerOffset();
        if (viewport <= 0) return;
        int visible = (int)Math.min((long)count - topIndex, viewport / height + 2);
        Color selectedBackground = null, selectedForeground = null;
        for (int offset = 0; offset < visible && !isDisposed(); offset++) {
            int index = topIndex + offset;
            if (index >= count) break;
            int y = headerOffset() + offset * height;
            TableItem item = getItem(index);
            ensureData(item);
            if (isDisposed() || item.isDisposed() || index >= count) break;
            boolean selectedRow = isSelected(index);
            if (selectedRow) {
                if (selectedBackground == null) {
                    selectedBackground = display.getSystemColor(SWT.COLOR_LIST_SELECTION);
                    selectedForeground = display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
                }
                gc.setBackground(selectedBackground);
                gc.setForeground(selectedForeground);
                gc.fillRectangle(0, y, area.width, Math.min(height, area.height - y));
            } else {
                gc.setBackground(item.getBackground());
                gc.fillRectangle(0, y, area.width, Math.min(height, area.height - y));
                gc.setForeground(item.getForeground());
            }
            gc.setFont(item.getFont());
            if (columnCount == 0) {
                drawCell(gc, item, 0, 4, y + 2);
            } else {
                long x = 0;
                for (int col : order) {
                    if (x >= area.width) break;
                    drawCell(gc, item, col, (int)x + 4, y + 2);
                    x += columns[col].getWidth();
                    if (linesVisible && x < area.width) gc.drawLine((int)x, y, (int)x, y + height);
                }
            }
            if (linesVisible) gc.drawLine(0, y + height - 1, area.width, y + height - 1);
        }
        gc.setForeground(defaultForeground);
        gc.setBackground(defaultBackground);
        gc.setFont(getFont());
    }
    private void drawCell(GC gc, TableItem item, int column, int x, int y) {
        Image cellImage = item.getImage(column);
        if (cellImage != null && !cellImage.isDisposed()) {
            gc.drawImage(cellImage, x, y);
            x += cellImage.getBounds().width + 4;
        }
        gc.drawText(item.getText(column), x, y, true);
    }
    private void publishAccessibility() {
        display.internal_platform().setAccessibility(peer, "Table",
                ACC.ROLE_TABLE, isEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) publishAccessibility();
    }
    @Override public void setFont(org.eclipse.swt.graphics.Font value) {
        super.setFont(value);
        rowHeight = headerHeight = 0;
    }
    /** Register original SWT Selection/DefaultSelection callbacks. */
    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }
    /** Remove registered original SWT selection callbacks. */
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    /** Return the current logical row count without materializing rows. */
    public int getItemCount() { checkWidget(); return count; }
    /** Return a stable TableItem wrapper only for the requested row. */
    public TableItem getItem(int index) {
        checkWidget();
        if (index < 0 || index >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        return materialize(index);
    }
    /** Return a row by local client coordinates, or null outside the viewport. */
    public TableItem getItem(Point point) {
        checkWidget();
        if (point == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int index = rowAt(point.y);
        return index < 0 ? null : getItem(index);
    }
    /** Enumerate all logical rows (large VIRTUAL results require large memory). */
    public TableItem[] getItems() {
        checkWidget();
        TableItem[] output = new TableItem[count];
        for (int i = 0; i < count; i++) output[i] = getItem(i);
        return output;
    }
    /** Resize the logical item count, preserving row wrapper identity in range. */
    public void setItemCount(int requested) {
        checkWidget();
        int next = Math.max(0, requested);
        if (next == count) return;
        if (next < count) { deleteRange(next, count - 1); return; }
        boolean wasEmpty = count == 0;
        count = next;
        redraw();
        if (wasEmpty) sendEvent(SWT.EmptinessChanged, new Event());
    }
    /** Remove an exact logical index. */
    public void remove(int index) { checkWidget(); deleteRange(index, index); }
    /** Remove inclusive row range. */
    public void remove(int start, int end) { checkWidget(); deleteRange(start, end); }
    /** Remove distinct validated indices, highest-first to preserve identity. */
    public void remove(int[] indices) {
        checkWidget();
        if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        for (int index : sorted)
            if (index < 0 || index >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        for (int i = sorted.length - 1; i >= 0; i--)
            if (i == sorted.length - 1 || sorted[i] != sorted[i + 1]) deleteRange(sorted[i], sorted[i]);
    }
    /** Remove all row entries and reset selection state. */
    public void removeAll() {
        checkWidget();
        if (count > 0) deleteRange(0, count - 1);
        clearSelection();
    }
    /** Forget a virtual item's data and request SWT.SetData at the next read. */
    public void clear(int index) {
        checkWidget();
        if (index < 0 || index >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        int slot = locate(index);
        if (slot >= 0) rowValues[slot].clearData();
        redraw();
    }
    /** Clear all materialized rows without enumerating virtual logical rows. */
    public void clearAll() {
        checkWidget();
        for (int i = 0; i < materialized; i++) rowValues[i].clearData();
        redraw();
    }
    /** Clear inclusive logical row interval, visiting only materialized rows. */
    public void clear(int start, int end) {
        checkWidget();
        if (start < 0 || end < start || end >= count) SWT.error(SWT.ERROR_INVALID_RANGE);
        int index = Arrays.binarySearch(rowKeys, 0, materialized, start);
        if (index < 0) index = -index - 1;
        for (; index < materialized && rowKeys[index] <= end; index++) rowValues[index].clearData();
        redraw();
    }
    /** Clear only a supplied set of logical rows. */
    public void clear(int[] indices) {
        checkWidget();
        if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (int index : indices) clear(index);
    }
    /** Return measured native row height. */
    public int getItemHeight() {
        checkWidget();
        if (rowHeight == 0) {
            Image scratch = new Image(display, 1, 1);
            try {
                GC gc = new GC(scratch);
                try { gc.setFont(getFont()); rowHeight = Math.max(14, gc.textExtent("Mg").y + 6); }
                finally { gc.dispose(); }
            } finally { scratch.dispose(); }
        }
        return rowHeight;
    }
    /** Return native header height when headers are enabled. */
    public int getHeaderHeight() {
        checkWidget();
        if (headerHeight == 0) headerHeight = getItemHeight() + 4;
        return headerHeight;
    }
    /** Header visibility flag. */
    public boolean getHeaderVisible() { checkWidget(); return headerVisible; }
    /** Display the original SWT column headers. */
    public void setHeaderVisible(boolean value) { checkWidget(); headerVisible = value; redraw(); }
    /** Gridline visibility flag. */
    public boolean getLinesVisible() { checkWidget(); return linesVisible; }
    /** Draw or suppress row/column separators. */
    public void setLinesVisible(boolean value) { checkWidget(); linesVisible = value; redraw(); }
    /** Gridline width in SWT logical pixels. */
    public int getGridLineWidth() { checkWidget(); return 1; }
    /** Header background or the inherited native SWT background. */
    public Color getHeaderBackground() { checkWidget(); return headerBackground == null ? getBackground() : headerBackground; }
    /** Header foreground or the inherited native SWT foreground. */
    public Color getHeaderForeground() { checkWidget(); return headerForeground == null ? getForeground() : headerForeground; }
    /** Update caller-owned header background. */
    public void setHeaderBackground(Color value) {
        checkWidget();
        if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        headerBackground = value; redraw();
    }
    /** Update caller-owned header foreground. */
    public void setHeaderForeground(Color value) {
        checkWidget();
        if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        headerForeground = value; redraw();
    }
    /** Number of original SWT columns. */
    public int getColumnCount() { checkWidget(); return columnCount; }
    /** Return a physical column index. */
    public TableColumn getColumn(int index) {
        checkWidget();
        if (index < 0 || index >= columnCount) SWT.error(SWT.ERROR_INVALID_RANGE);
        return columns[index];
    }
    /** Copy column identities in physical order. */
    public TableColumn[] getColumns() { checkWidget(); return Arrays.copyOf(columns, columnCount); }
    /** Return display column permutation. */
    public int[] getColumnOrder() { checkWidget(); return order.clone(); }
    /** Set display order after verifying a complete permutation. */
    public void setColumnOrder(int[] requested) {
        checkWidget();
        if (requested == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (requested.length != columnCount) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        boolean[] seen = new boolean[columnCount];
        for (int index : requested) {
            if (index < 0 || index >= columnCount || seen[index])
                SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            seen[index] = true;
        }
        order = requested.clone();
        redraw();
    }
    /** Return zero-based physical column index, or -1. */
    public int indexOf(TableColumn column) {
        checkWidget();
        if (column == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (int i = 0; i < columnCount; i++) if (columns[i] == column) return i;
        return -1;
    }
    /** Return logical index for a current parent-owned row identity. */
    public int indexOf(TableItem item) {
        checkWidget();
        if (item == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int index = item.rowIndex;
        int slot = index >= 0 && index < count ? locate(index) : -1;
        return slot >= 0 && rowValues[slot] == item ? index : -1;
    }
    /** Sorted logical selection count, without allocating an index-sized mask. */
    public int getSelectionCount() { checkWidget(); return selected.count(); }
    /** First selected row index, or -1. */
    public int getSelectionIndex() { checkWidget(); return firstSelected(); }
    /** Return selected row indices ascending. Caller requests a materialized array. */
    public int[] getSelectionIndices() {
        checkWidget();
        return selected.indices();
    }
    /** Return the selected item identities. */
    public TableItem[] getSelection() {
        checkWidget();
        int[] indices = getSelectionIndices();
        TableItem[] result = new TableItem[indices.length];
        for (int i = 0; i < indices.length; i++) result[i] = getItem(indices[i]);
        return result;
    }
    /** Select row without sending a user SWT.Selection. */
    public void select(int index) {
        checkWidget();
        if (index < 0 || index >= count) return;
        if (!multi()) clearSelection();
        selection(index, true); focusIndex = anchorIndex = index; redraw();
    }
    /** Add an inclusive interval of selected rows. */
    public void select(int start, int end) {
        checkWidget();
        if (start > end || start >= count || end < 0 || (!multi() && start != end)) return;
        int begin = Math.max(0, start), stop = Math.min(count - 1, end);
        if (!multi()) clearSelection();
        selected.select(begin, stop);
        focusIndex = stop; anchorIndex = begin; redraw();
    }
    /** Add selected row indices. */
    public void select(int[] indices) {
        checkWidget();
        if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (!multi() && indices.length != 1) return;
        for (int index : indices) if (index >= 0 && index < count) select(index);
    }
    /** Select all rows in O(1) space even for huge VIRTUAL tables. */
    public void selectAll() {
        checkWidget();
        if (multi()) { selected.selectAll(count); redraw(); }
    }
    /** Deselect one logical row. */
    public void deselect(int index) { checkWidget(); selection(index, false); redraw(); }
    /** Deselect an inclusive range. */
    public void deselect(int start, int end) {
        checkWidget();
        if (start > end || end < 0 || start >= count) return;
        selected.deselect(Math.max(0, start), Math.min(end, count - 1));
        redraw();
    }
    /** Deselect a list of indices. */
    public void deselect(int[] indices) {
        checkWidget();
        if (indices == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (int index : indices) selection(index, false);
        redraw();
    }
    /** Deselect all rows in O(1). */
    public void deselectAll() { checkWidget(); clearSelection(); redraw(); }
    /** Replace selection with one row. */
    public void setSelection(int index) { checkWidget(); clearSelection(); select(index); showSelection(); }
    /** Replace selection with an inclusive row range. */
    public void setSelection(int start, int end) {
        checkWidget(); clearSelection(); select(start, end); showSelection();
    }
    /** Replace selection with row indices. */
    public void setSelection(int[] indices) {
        checkWidget(); clearSelection(); select(indices); showSelection();
    }
    /** Replace selection with a row identity. */
    public void setSelection(TableItem item) {
        checkWidget();
        if (item == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        setSelection(indexOf(item));
    }
    /** Replace selection with row identities. */
    public void setSelection(TableItem[] items) {
        checkWidget();
        if (items == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int[] indices = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null || items[i].isDisposed() || indexOf(items[i]) < 0)
                SWT.error(SWT.ERROR_INVALID_ARGUMENT);
            indices[i] = indexOf(items[i]);
        }
        setSelection(indices);
    }
    /** Return logical index at the top of the software viewport. */
    public int getTopIndex() { checkWidget(); return topIndex; }
    /** Scroll to a valid logical row in O(1) regardless of row count. */
    public void setTopIndex(int index) {
        checkWidget();
        if (index >= 0 && index < count && index != topIndex) { topIndex = index; redraw(); }
    }
    /** Ensure an item identity appears in the viewport. */
    public void showItem(TableItem item) {
        checkWidget();
        if (item == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int index = indexOf(item);
        if (index >= 0) showIndex(index);
    }
    /** Bring the first selected row into the viewport. */
    public void showSelection() { checkWidget(); int index = firstSelected(); if (index >= 0) showIndex(index); }
    /** Horizontal native ScrollBar parity is not yet available. */
    public void showColumn(TableColumn column) {
        checkWidget();
        if (column == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (indexOf(column) < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
    }
    /** Return a sort-indicator owner; SWT does not reorder rows automatically. */
    public TableColumn getSortColumn() { checkWidget(); return sortColumn; }
    /** Set sort-indicator column only when it belongs to this Table. */
    public void setSortColumn(TableColumn column) {
        checkWidget();
        if (column != null && indexOf(column) < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        sortColumn = column; redraw();
    }
    /** Return SWT.UP, SWT.DOWN or SWT.NONE indicator. */
    public int getSortDirection() { checkWidget(); return sortDirection; }
    /** Set the sort arrow, without mutating the user-owned data model. */
    public void setSortDirection(int direction) {
        checkWidget();
        if (direction != SWT.UP && direction != SWT.DOWN && direction != SWT.NONE) return;
        sortDirection = direction; redraw();
    }
    /** Default preferred viewport dimensions, respecting caller hints. */
    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int width = 160;
        if (columnCount > 0) {
            long sum = 0;
            for (int i = 0; i < columnCount; i++) sum += columns[i].getWidth();
            width = (int)Math.min(Integer.MAX_VALUE - 4L, Math.max(60L, sum));
        }
        int height = getItemHeight() * 8 + headerOffset();
        int border = getBorderWidth() * 2;
        return new Point((wHint == SWT.DEFAULT ? width : Math.max(0, wHint)) + border,
                (hHint == SWT.DEFAULT ? height : Math.max(0, hHint)) + border);
    }
    /** Dispose only materialized item identities and original columns. */
    @Override void releaseChildren() {
        disposingChildren = true;
        try (ExceptionStash failures = new ExceptionStash()) {
            for (int i = 0; i < materialized; i++) {
                TableItem item = rowValues[i];
                if (item == null || item.isDisposed()) continue;
                try { item.dispose(); } catch (RuntimeException | Error e) { failures.stash(e); }
            }
            for (int i = 0; i < columnCount; i++) {
                TableColumn column = columns[i];
                if (column == null || column.isDisposed()) continue;
                try { column.dispose(); } catch (RuntimeException | Error e) { failures.stash(e); }
            }
        } finally {
            Arrays.fill(rowValues, 0, materialized, null);
            Arrays.fill(columns, 0, columnCount, null);
            materialized = columnCount = count = 0;
            disposingChildren = false;
        }
        super.releaseChildren();
    }
}
