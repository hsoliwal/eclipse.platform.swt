/* SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 */
package org.eclipse.swt.widgets;

/**
 * Headless-only compilation adapter for the SWT-native viewport owner.
 * This fixture intentionally does NOT qualify native selection, geometry or painting.
 * Never package it in a product plugin.
 */
final class VirtualViewportPlanner {
    static final class Selection {
        void clear() { }
        void setSelected(int index, boolean selected) { }
        void selectRange(int first, int afterLast) { }
        void deselectRange(int first, int afterLast) { }
        void selectAll() { }
        boolean isSelected(int index) { return false; }
        int selectedCount() { return 0; }
        int firstSelectedIndex() { return -1; }
        int[] toArray() { return new int[0]; }
    }
    private final Selection selection = new Selection();
    private int count;
    private int firstVisible;
    private int visibleCount;

    VirtualViewportPlanner(boolean hybrid) { }
    Selection selection() { return selection; }
    void setLogicalCount(int count) { this.count = count; }
    void insert(int index, int added) { count = Math.addExact(count, added); }
    void remove(int index, int removed) { count -= removed; }
    int firstVisible() { return firstVisible; }
    int visibleCount() { return visibleCount; }
    void setUniformGeometry(int count, int rowHeight, int viewportHeight, int first) {
        this.count = count;
        this.firstVisible = Math.max(0, Math.min(first, Math.max(0, count - 1)));
        this.visibleCount = count == 0 ? 0 : Math.min(count,
                Math.max(1, (int) (((long) viewportHeight + rowHeight - 1) / rowHeight)));
    }
}
