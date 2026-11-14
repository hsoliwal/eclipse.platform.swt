/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

/**
 * Original SWT Text owner for Android EditText and UIKit UITextField/UITextView.
 * The Java Display thread owns the canonical UTF-16 text, selection and
 * Verify/Modify ordering. OS UI-thread callbacks only enqueue full snapshots;
 * they never execute user listeners or own authoritative model state.
 *
 * This is the native editing slice. Complex IME marked-text composition,
 * platform clipboard/DND, full Accessible, text segments and bidi layout
 * remain separate compatibility gates rather than silently approximated.
 */
public class Text extends Scrollable {
    /** Maximum SWT Text length supported by this Java/UTF-16 owner. */
    public static final int LIMIT = Integer.MAX_VALUE;
    /** Default line delimiter on the Android/iOS mobile source set. */
    public static final String DELIMITER = "\n";

    private String value = "";
    private String message = "";
    private int selectionStart, selectionEnd, textLimit = LIMIT;
    private int tabs = 8;
    private boolean editable;
    private boolean doubleClick = true;

    /** Construct the original SWT Text with the shared SWT style policy. */
    public Text(Composite parent, int style) {
        super(parent, WidgetStylePolicy.TEXT.applyAsInt(style), true);
        editable = (this.style & SWT.READ_ONLY) == 0;
        publish();
        publishAccessibility();
        addListener(SWT.Dispose, event -> { });
    }

    private int limit(int length) { return Math.max(0, Math.min(length, textLimit)); }
    private int clamp(int point) { return Math.max(0, Math.min(point, value.length())); }
    private void publish() {
        display.internal_platform().textContent(peer, value, selectionStart,
                selectionEnd, editable, textLimit, message);
    }
    private void publishAccessibility() {
        display.internal_platform().setAccessibility(peer,
                (style & SWT.PASSWORD) != 0 ? "Secure text field" : "Text",
                ACC.ROLE_TEXT, editable && isEnabled() ? ACC.STATE_NORMAL : ACC.STATE_READONLY);
    }
    private String verified(String replacement, int start, int end) {
        Event event = new Event();
        event.start = start;
        event.end = end;
        event.text = replacement;
        sendEvent(SWT.Verify, event);
        if (isDisposed() || !event.doit) return null;
        return event.text == null ? "" : event.text;
    }
    private void replace(int start, int end, String replacement, boolean fromNative,
            int nativeCaretStart, int nativeCaretEnd) {
        checkWidget();
        if (replacement == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (start < 0 || end < start || end > value.length()) SWT.error(SWT.ERROR_INVALID_RANGE);
        String permitted = verified(replacement, start, end);
        if (isDisposed()) return;
        if (permitted == null) {
            if (fromNative) publish();
            return;
        }
        int available = Math.max(0, textLimit - (value.length() - (end - start)));
        if (permitted.length() > available) permitted = permitted.substring(0, available);
        String next = value.substring(0, start) + permitted + value.substring(end);
        boolean changed = !next.equals(value);
        value = next;
        if (fromNative && permitted.equals(replacement)) {
            selectionStart = clamp(nativeCaretStart);
            selectionEnd = clamp(nativeCaretEnd);
        } else {
            selectionStart = selectionEnd = start + permitted.length();
        }
        publish();
        if (changed && !isDisposed()) sendEvent(SWT.Modify, new Event());
    }

    /** Called by Display's queued native text callback, never by an OS thread. */
    void nativeText(String proposed, int start, int end) {
        checkWidget();
        if (proposed == null) return;
        if (!editable || !isEnabled()) { publish(); return; }
        if (proposed.equals(value)) {
            selectionStart = clamp(start);
            selectionEnd = clamp(end);
            return;
        }
        int prefix = 0, beforeLength = value.length(), afterLength = proposed.length();
        while (prefix < beforeLength && prefix < afterLength
                && value.charAt(prefix) == proposed.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < beforeLength - prefix && suffix < afterLength - prefix
                && value.charAt(beforeLength - suffix - 1) == proposed.charAt(afterLength - suffix - 1)) suffix++;
        replace(prefix, beforeLength - suffix,
                proposed.substring(prefix, afterLength - suffix), true, start, end);
    }

    /** Add an original SWT Modify listener (fires after accepted text changes). */
    public void addModifyListener(ModifyListener listener) { addTypedListener(listener, SWT.Modify); }
    /** Remove an original SWT Modify listener. */
    public void removeModifyListener(ModifyListener listener) { removeTypedListener(SWT.Modify, listener); }
    /** Add an original SWT Verify listener (fires before text changes). */
    public void addVerifyListener(VerifyListener listener) { addTypedListener(listener, SWT.Verify); }
    /** Remove an original SWT Verify listener. */
    public void removeVerifyListener(VerifyListener listener) { removeTypedListener(SWT.Verify, listener); }
    /** Add original Selection/DefaultSelection listeners. */
    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }
    /** Remove original Selection/DefaultSelection listeners. */
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    /** Return canonical Java-owned text. */
    public String getText() { checkWidget(); return value; }
    /** Return inclusive UTF-16 substring, clamping outside endpoints. */
    public String getText(int start, int end) {
        checkWidget();
        int a = Math.max(0, start), b = Math.min(end, value.length() - 1);
        return b < a ? "" : value.substring(a, b + 1);
    }
    /** Return a defensive copy of the UTF-16 text characters. */
    public char[] getTextChars() { checkWidget(); return value.toCharArray(); }
    /** Set text, honoring Verify, text limit and Modify. */
    public void setText(String text) {
        checkWidget();
        if (text == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        replace(0, value.length(), text, false, 0, 0);
    }
    /** Replace text from a char array without retaining the caller's buffer. */
    public void setTextChars(char[] characters) {
        checkWidget();
        if (characters == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        setText(new String(characters));
    }
    /** Append text, with Verify and Modify semantics. */
    public void append(String text) {
        checkWidget();
        if (text == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        replace(value.length(), value.length(), text, false, 0, 0);
    }
    /** Insert at the active selection/caret. */
    public void insert(String text) {
        checkWidget();
        if (text == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        replace(selectionStart, selectionEnd, text, false, 0, 0);
    }
    /** Replace an exact UTF-16 range. */
    public void replaceTextRange(int start, int length, String text) {
        checkWidget();
        if (length < 0 || start < 0 || (long)start + length > value.length())
            SWT.error(SWT.ERROR_INVALID_RANGE);
        replace(start, start + length, text, false, 0, 0);
    }
    /** Get UTF-16 character count. */
    public int getCharCount() { checkWidget(); return value.length(); }
    /** Get the maximum accepted UTF-16 units. */
    public int getTextLimit() { checkWidget(); return textLimit; }
    /** Set text limit, ignoring invalid values. Existing content is preserved. */
    public void setTextLimit(int limit) {
        checkWidget();
        if (limit <= 0) SWT.error(SWT.ERROR_CANNOT_BE_ZERO);
        textLimit = limit;
        publish();
    }
    /** Return current selection start/end as SWT Point. */
    public Point getSelection() { checkWidget(); return new Point(selectionStart, selectionEnd); }
    /** Return current caret UTF-16 position. */
    public int getCaretPosition() { checkWidget(); return selectionEnd; }
    /** Set a collapsed selection/caret. */
    public void setSelection(int offset) { setSelection(offset, offset); }
    /** Set a selection, clamped to the current text. */
    public void setSelection(int start, int end) {
        checkWidget();
        selectionStart = clamp(Math.min(start, end));
        selectionEnd = clamp(Math.max(start, end));
        publish();
    }
    /** Set selection using SWT Point. */
    public void setSelection(Point point) {
        if (point == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        setSelection(point.x, point.y);
    }
    /** Select every UTF-16 code unit. */
    public void selectAll() { setSelection(0, getCharCount()); }
    /** Clear selection without deleting text. */
    public void clearSelection() { setSelection(getCaretPosition()); }
    /** Whether native typing is enabled. */
    public boolean getEditable() { checkWidget(); return editable; }
    /** Enable or disable text edits while retaining caller-owned text. */
    public void setEditable(boolean enabled) {
        checkWidget();
        if (editable == enabled) return;
        editable = enabled;
        publish();
        publishAccessibility();
    }
    /** Return current placeholder text. */
    public String getMessage() { checkWidget(); return message; }
    /** Set native placeholder text for an empty editor. */
    public void setMessage(String hint) {
        checkWidget();
        if (hint == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        message = hint;
        publish();
    }
    /** Query the original SWT tab-stop setting. */
    public int getTabs() { checkWidget(); return tabs; }
    /** Set tab stops for native multiline editing. */
    public void setTabs(int count) {
        checkWidget();
        if (count < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        tabs = count;
    }
    /** Return platform-neutral newline delimiter. */
    public String getLineDelimiter() { checkWidget(); return DELIMITER; }
    /** Number of logical lines, at least one. */
    public int getLineCount() {
        checkWidget();
        int lines = 1;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '\n') lines++;
        return lines;
    }
    /** Return logical line index for a valid UTF-16 offset. */
    public int getLineAtOffset(int offset) {
        checkWidget();
        if (offset < 0 || offset > value.length()) SWT.error(SWT.ERROR_INVALID_RANGE);
        int line = 0;
        for (int i = 0; i < offset; i++) if (value.charAt(i) == '\n') line++;
        return line;
    }
    /** Return offset of a logical line start. */
    public int getOffsetAtLine(int line) {
        checkWidget();
        if (line < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if (line == 0) return 0;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n' && ++count == line) return i + 1;
        }
        SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        return 0;
    }
    /** Query the standard line height via the owning SWT native GC. */
    public int getLineHeight() {
        checkWidget();
        Image scratch = new Image(display, 1, 1);
        try {
            GC gc = new GC(scratch);
            try { gc.setFont(getFont()); return Math.max(1, gc.getFontMetrics().getHeight()); }
            finally { gc.dispose(); }
        } finally { scratch.dispose(); }
    }
    /** Original SWT preferred edit size with native layout hints. */
    @Override public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int border = getBorderWidth() * 2;
        boolean multi = (style & SWT.MULTI) != 0;
        return new Point((wHint == SWT.DEFAULT ? 160 : Math.max(0, wHint)) + border,
                (hHint == SWT.DEFAULT ? (multi ? Math.max(84, getLineHeight() * 4) : Math.max(28, getLineHeight() + 10))
                        : Math.max(0, hHint)) + border);
    }
    /** Reset the cursor state on read-only transitions or Activity changes. */
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) { publish(); publishAccessibility(); }
    }
}
