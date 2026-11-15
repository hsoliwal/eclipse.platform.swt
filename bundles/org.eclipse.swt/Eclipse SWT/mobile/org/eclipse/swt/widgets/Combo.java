/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SegmentListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.mobile.MobileComboArrayOps;
import org.eclipse.swt.internal.ExceptionStash;

/**
 * The original SWT Combo API on Android and iOS.
 *
 * This is an internal composition of the mobile SWT Text, List and Button
 * owners. The platform-specific edit field, native Canvas rows and popup
 * Shell belong to the original SWT Display. No Android-only or UIKit-only
 * substitute Combo class is introduced. Programmatic selection is silent,
 * while a user selecting a popup row emits the Combo Selection event.
 *
 * This first mobile owner supports single-selection SIMPLE/DROP_DOWN and
 * READ_ONLY semantics. Full platform IME composition, native popup window
 * behavior, bidi SegmentListener and OS-level accessible child navigation
 * remain separate release/compatibility gates.
 */
public class Combo extends Composite {
    private String[] items = new String[8];
    private int itemCount;
    private int selectionIndex = -1;
    private int visibleItemCount = 5;
    private final Text text;
    private final Button arrow;
    private List list;
    private Shell popup;
    private boolean layingOut;
    private boolean updatingItems;

    /** Construct a standard SWT Combo using the original shared style policy. */
    public Combo(Composite parent, int style) {
        super(parent, WidgetStylePolicy.COMBO.applyAsInt(style));
        boolean simple = simple();
        text = new Text(this, SWT.SINGLE | ((this.style & SWT.READ_ONLY) != 0 ? SWT.READ_ONLY : SWT.NONE));
        if (simple) {
            arrow = null;
            list = new List(this, SWT.SINGLE | SWT.V_SCROLL);
            attachList(list);
        } else {
            arrow = new Button(this, SWT.ARROW | SWT.DOWN);
            arrow.addListener(SWT.Selection, event -> setListVisible(!getListVisible()));
        }
        text.addListener(SWT.Verify, event -> {
            Event forwarded = new Event();
            forwarded.start = event.start;
            forwarded.end = event.end;
            forwarded.text = event.text;
            forwarded.doit = event.doit;
            forwarded.stateMask = event.stateMask;
            sendEvent(SWT.Verify, forwarded);
            event.text = forwarded.text;
            event.doit = forwarded.doit;
        });
        text.addListener(SWT.Modify, event -> {
            if (isDisposed()) return;
            selectionIndex = indexOf(text.getText());
            syncListSelection();
            publishAccessibility();
            sendEvent(SWT.Modify, new Event());
        });
        text.addListener(SWT.DefaultSelection, event -> sendEvent(SWT.DefaultSelection, new Event()));
        // EditText/UITextField may own focus, while a READ_ONLY Combo can
        // focus its composite peer. Both paths share one selection policy.
        text.addListener(SWT.KeyDown, this::keyboardInput);
        addListener(SWT.KeyDown, this::keyboardInput);
        addListener(SWT.Resize, event -> layoutInternals());
        addListener(SWT.MouseUp, event -> {
            if (event.button == 1 && !simple() && isEnabled()) setListVisible(true);
        });
        publishAccessibility();
        layoutInternals();
    }

    private void publishAccessibility() {
        if (isDisposed()) return;
        display.internal_platform().setAccessibility(peer, "Combo " + text.getText(),
                ACC.ROLE_COMBOBOX, isEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED);
    }
    private boolean simple() { return (style & SWT.SIMPLE) != 0; }
    private boolean readOnly() { return (style & SWT.READ_ONLY) != 0; }
    private void valid(int index) {
        if (index < 0 || index >= itemCount) SWT.error(SWT.ERROR_INVALID_RANGE);
    }
    private void syncListSelection() {
        if (list == null || list.isDisposed()) return;
        if (selectionIndex < 0 || selectionIndex >= itemCount) list.deselectAll();
        else list.setSelection(selectionIndex);
    }
    private void refreshList() {
        if (list == null || list.isDisposed()) return;
        updatingItems = true;
        try {
            list.setItems(Arrays.copyOf(items, itemCount));
            syncListSelection();
        } finally { updatingItems = false; }
    }
    private void attachList(List receiver) {
        receiver.addListener(SWT.Selection, event -> {
            if (updatingItems || isDisposed()) return;
            int index = receiver.getSelectionIndex();
            if (index >= 0) choose(index, true);
        });
        receiver.addListener(SWT.DefaultSelection, event -> {
            if (!isDisposed()) sendEvent(SWT.DefaultSelection, new Event());
        });
    }
    private void choose(int index, boolean fromUser) {
        if (index < 0 || index >= itemCount) return;
        // Modify/Verify listeners may reenter add/remove/setItems or dispose
        // the Combo. The requested row must still refer to the same item.
        String requested = items[index];
        text.setText(requested);
        if (isDisposed()) return;
        if (index >= itemCount || !requested.equals(items[index])) {
            selectionIndex = indexOf(text.getText());
            syncListSelection();
            return;
        }
        selectionIndex = requested.equals(text.getText()) ? index : indexOf(text.getText());
        syncListSelection();
        if (fromUser && selectionIndex == index) {
            Event selection = new Event();
            selection.index = index;
            sendEvent(SWT.Selection, selection);
            if (!isDisposed() && !simple()) setListVisible(false);
        }
    }
    private void keyboardInput(Event event) {
        if (isDisposed() || !isEnabled() || !isVisible()) return;
        if (event.keyCode == SWT.ESC) {
            if (!simple() && getListVisible()) setListVisible(false);
            return;
        }
        if (event.keyCode != SWT.ARROW_UP && event.keyCode != SWT.ARROW_DOWN)
            return;
        if (itemCount == 0) return;
        int target = selectionIndex < 0 ? 0
                : event.keyCode == SWT.ARROW_DOWN
                    ? Math.min(itemCount - 1, selectionIndex + 1)
                    : Math.max(0, selectionIndex - 1);
        if (target != selectionIndex) choose(target, true);
    }

    private void createPopup() {
        if (popup != null && !popup.isDisposed()) return;
        popup = new Shell(getShell(), SWT.NO_TRIM | SWT.ON_TOP);
        list = new List(popup, SWT.SINGLE | SWT.V_SCROLL);
        attachList(list);
        refreshList();
    }
    private void positionPopup() {
        if (popup == null || popup.isDisposed()) return;
        Rectangle bounds = getClientArea();
        Point origin = toDisplay(0, bounds.height);
        int rowHeight = Math.max(1, list.getItemHeight());
        int height = (int)Math.min(Integer.MAX_VALUE,
                (long)rowHeight * Math.max(1, Math.min(visibleItemCount, Math.max(1, itemCount))));
        popup.setBounds(origin.x, origin.y, Math.max(1, bounds.width), height);
        list.setBounds(0, 0, Math.max(1, bounds.width), height);
    }
    private void layoutInternals() {
        if (layingOut || isDisposed() || text == null || text.isDisposed()) return;
        layingOut = true;
        try {
            Rectangle area = getClientArea();
            int rowHeight = Math.min(area.height, Math.max(1, text.getLineHeight() + 10));
            int arrowWidth = simple() ? 0 : Math.min(32, area.width);
            text.setBounds(0, 0, Math.max(0, area.width - arrowWidth), rowHeight);
            if (arrow != null && !arrow.isDisposed())
                arrow.setBounds(Math.max(0, area.width - arrowWidth), 0, arrowWidth, rowHeight);
            if (simple() && list != null && !list.isDisposed())
                list.setBounds(0, rowHeight, area.width, Math.max(0, area.height - rowHeight));
            else if (popup != null && !popup.isDisposed() && popup.getVisible())
                positionPopup();
        } finally { layingOut = false; }
    }

    /** Append an item; no SWT.Selection event is sent. */
    public void add(String string) { add(string, itemCount); }
    /** Insert an item, shifting the existing selected index if necessary. */
    public void add(String string, int index) {
        checkWidget();
        if (string == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (index < 0 || index > itemCount) SWT.error(SWT.ERROR_INVALID_RANGE);
        if (itemCount == Integer.MAX_VALUE) SWT.error(SWT.ERROR_ITEM_NOT_ADDED);
        items = MobileComboArrayOps.insert(items, itemCount, index, string);
        selectionIndex = MobileComboArrayOps.selectionAfterInsert(selectionIndex, index);
        itemCount++;
        refreshList();
    }
    /** Register original SWT Modify event listeners. */
    public void addModifyListener(ModifyListener listener) { addTypedListener(listener, SWT.Modify); }
    /** Register original SWT Selection/DefaultSelection listeners. */
    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }
    /** Register original SWT Verify listeners. */
    public void addVerifyListener(VerifyListener listener) { addTypedListener(listener, SWT.Verify); }
    /** Bidi segment listeners require a qualified mobile native text segment owner. */
    public void addSegmentListener(SegmentListener listener) {
        checkWidget();
        if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        throw new UnsupportedOperationException("Mobile Combo bidi segments are not yet supported");
    }
    /** Bidi segment listeners require a qualified mobile native text segment owner. */
    public void removeSegmentListener(SegmentListener listener) {
        checkWidget();
        if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        throw new UnsupportedOperationException("Mobile Combo bidi segments are not yet supported");
    }
    /** Clear the editor selection without changing the selected list item. */
    public void clearSelection() { checkWidget(); text.clearSelection(); }
    /** Copy the native editor's selected text using the OS clipboard. */
    public void copy() { checkWidget(); text.copy(); }
    /** Cut editable text through the SWT Text Verify/Modify owner. */
    public void cut() { checkWidget(); if (!readOnly()) text.cut(); }
    /** Deselect an item without changing its text. */
    public void deselect(int index) {
        checkWidget();
        if (selectionIndex == index) { selectionIndex = -1; syncListSelection(); }
    }
    /** Clear the selected item index without editing the text. */
    public void deselectAll() { checkWidget(); selectionIndex = -1; syncListSelection(); }
    /** Return caret geometry in SWT logical coordinates. */
    public Point getCaretLocation() {
        checkWidget();
        String content = text.getText();
        int end = Math.min(content.length(), text.getCaretPosition());
        Image scratch = new Image(display, 1, 1);
        try {
            GC gc = new GC(scratch);
            try {
                gc.setFont(text.getFont());
                return new Point(gc.textExtent(content.substring(0, end)).x, 0);
            } finally { gc.dispose(); }
        } finally { scratch.dispose(); }
    }
    /** Return caret offset within the editable portion. */
    public int getCaretPosition() { checkWidget(); return text.getCaretPosition(); }
    /** Return an item by its zero-based list index. */
    public String getItem(int index) { checkWidget(); valid(index); return items[index]; }
    /** Return the logical number of list items. */
    public int getItemCount() { checkWidget(); return itemCount; }
    /** Return the measured native list-row height. */
    public int getItemHeight() { checkWidget(); return list != null ? list.getItemHeight() : text.getLineHeight() + 4; }
    /** Return a defensive copy of the list entries. */
    public String[] getItems() { checkWidget(); return Arrays.copyOf(items, itemCount); }
    /** Return true while the popup is visible; SIMPLE lists are always shown. */
    public boolean getListVisible() {
        checkWidget();
        return simple() || (popup != null && !popup.isDisposed() && popup.getVisible());
    }
    /** Return the current text-selection range in UTF-16 code units. */
    public Point getSelection() { checkWidget(); return text.getSelection(); }
    /** Return selected list index, or -1 for unmatched typed text. */
    public int getSelectionIndex() { checkWidget(); return selectionIndex; }
    /** Return editor text (which may not match a list item if editable). */
    public String getText() { checkWidget(); return text.getText(); }
    /** Return preferred native editor height. */
    public int getTextHeight() { checkWidget(); return text.getLineHeight() + 10; }
    /** Return the current UTF-16 text length limit. */
    public int getTextLimit() { checkWidget(); return text.getTextLimit(); }
    /** Return how many rows the popup displays. */
    public int getVisibleItemCount() { checkWidget(); return visibleItemCount; }
    /** Find an exact item, returning -1 when absent. */
    public int indexOf(String string) { return indexOf(string, 0); }
    /** Find an exact item at or after start. */
    public int indexOf(String string, int start) {
        checkWidget();
        if (string == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        return MobileComboArrayOps.indexOf(items, itemCount, string, start);
    }
    /** Paste through the native editor only when editable. */
    public void paste() { checkWidget(); if (!readOnly()) text.paste(); }
    /** Remove a list entry by its zero-based index. */
    public void remove(int index) { remove(index, index); }
    /** Remove a validated inclusive range of entries. */
    public void remove(int start, int end) {
        checkWidget();
        if (start < 0 || end < start || end >= itemCount) SWT.error(SWT.ERROR_INVALID_RANGE);
        boolean removedSelection = selectionIndex >= start && selectionIndex <= end;
        MobileComboArrayOps.removeRange(items, itemCount, start, end);
        selectionIndex = MobileComboArrayOps.selectionAfterRemove(selectionIndex, start, end);
        itemCount -= end - start + 1;
        if (removedSelection) {
            text.setText("");
            selectionIndex = -1;
        }
        refreshList();
    }
    /** Remove first equal item or throw SWT.ERROR_INVALID_ARGUMENT. */
    public void remove(String string) {
        checkWidget();
        if (string == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        int index = indexOf(string);
        if (index < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        remove(index);
    }
    /** Remove all list entries and clear the editor. */
    public void removeAll() {
        checkWidget();
        items = new String[8];
        itemCount = 0;
        selectionIndex = -1;
        text.setText("");
        refreshList();
    }
    /** Remove original SWT Modify listeners. */
    public void removeModifyListener(ModifyListener listener) { removeTypedListener(SWT.Modify, listener); }
    /** Remove original SWT Selection listeners. */
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    /** Remove original SWT Verify listeners. */
    public void removeVerifyListener(VerifyListener listener) { removeTypedListener(SWT.Verify, listener); }
    /** Select one item programmatically, without sending SWT.Selection. */
    public void select(int index) { checkWidget(); choose(index, false); }
    /** Replace a list entry without sending selection events. */
    public void setItem(int index, String string) {
        checkWidget();
        valid(index);
        if (string == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        items[index] = string;
        if (selectionIndex == index) text.setText(string);
        refreshList();
    }
    /** Atomically replace every list entry after validation. */
    public void setItems(String... strings) {
        checkWidget();
        if (strings == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (String item : strings) if (item == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        items = Arrays.copyOf(strings, Math.max(8, strings.length));
        itemCount = strings.length;
        selectionIndex = -1;
        text.setText("");
        refreshList();
    }
    /** Show/hide a native rooted popup Shell (SIMPLE is permanently visible). */
    public void setListVisible(boolean visible) {
        checkWidget();
        if (simple()) return;
        if (!visible) {
            if (popup != null && !popup.isDisposed()) popup.setVisible(false);
            return;
        }
        createPopup();
        positionPopup();
        syncListSelection();
        popup.setVisible(true);
        if (list != null && !list.isDisposed()) list.setFocus();
    }
    /** Change text, matching the list only in READ_ONLY mode. */
    public void setText(String value) {
        checkWidget();
        if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (readOnly()) {
            int index = indexOf(value);
            if (index >= 0) choose(index, false);
        } else text.setText(value);
    }
    /** Change UTF-16 editor selection. */
    public void setSelection(Point selection) { checkWidget(); text.setSelection(selection); }
    /** Change native editor text limit. */
    public void setTextLimit(int limit) { checkWidget(); text.setTextLimit(limit); }
    /** Change maximum number of visible popup rows. */
    public void setVisibleItemCount(int count) {
        checkWidget();
        if (count <= 0) return;
        visibleItemCount = count;
        if (!simple() && getListVisible()) positionPopup();
    }
    /** Give focus to the Combo's native text child. */
    @Override public boolean setFocus() {
        checkWidget();
        return text.setFocus() || super.setFocus();
    }
    /** Also disable the internal editor/list peers. */
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) {
            text.setEnabled(enabled);
            if (arrow != null && !arrow.isDisposed()) arrow.setEnabled(enabled);
            if (list != null && !list.isDisposed()) list.setEnabled(enabled);
            if (!enabled && !simple()) setListVisible(false);
            publishAccessibility();
        }
    }
    /** Hide open popups with their Combo owner. */
    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!isDisposed() && !visible && !simple()) setListVisible(false);
    }
    /** Return a native text measurement without exposing internal controls. */
    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        Point preferred = text.computeSize(SWT.DEFAULT, SWT.DEFAULT, changed);
        int width = Math.max(120, preferred.x + (simple() ? 0 : 32));
        int height = Math.max(28, preferred.y);
        if (simple()) height += Math.max(1, visibleItemCount) * getItemHeight();
        return new Point(wHint == SWT.DEFAULT ? width : Math.max(0, wHint),
                hHint == SWT.DEFAULT ? height : Math.max(0, hHint));
    }
    /** The internal native Text/List/Button are implementation details. */
    @Override public Control[] getChildren() { checkWidget(); return new Control[0]; }
    /** Dispose the popup and internal children before releasing Combo peer. */
    @Override void releaseChildren() {
        try (ExceptionStash failures = new ExceptionStash()) {
            if (popup != null && !popup.isDisposed()) {
                try { popup.dispose(); }
                catch (RuntimeException | Error e) { failures.stash(e); }
            }
            for (Control child : super.getChildren()) {
                if (child.isDisposed()) continue;
                try { child.dispose(); }
                catch (RuntimeException | Error e) { failures.stash(e); }
            }
        }
    }
}
