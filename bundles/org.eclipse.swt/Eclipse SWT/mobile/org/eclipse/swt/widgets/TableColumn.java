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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

/**
 * Original SWT TableColumn API. Columns are Java-owned non-windowed Items
 * drawn through their parent Table's existing Android/UIKit GC owner.
 */
public class TableColumn extends Item {
    private Table parent;
    private int width = 100;
    private boolean moveable, resizable = true;
    private String toolTipText;

    /** Append a column to its original SWT Table. */
    public TableColumn(Table parent, int style) { this(parent, style, parent == null ? 0 : parent.getColumnCount()); }

    /** Insert a column at the requested physical index. */
    public TableColumn(Table parent, int style, int index) {
        super(parent, WidgetStylePolicy.COLUMN_ALIGNMENT.applyAsInt(style), index);
        this.parent = parent;
        parent.insertColumn(this, index);
    }

    /** Register original SWT Move and Resize listeners. */
    public void addControlListener(ControlListener listener) { addTypedListener(listener, SWT.Move, SWT.Resize); }
    /** Register original SWT Selection listeners. */
    public void addSelectionListener(SelectionListener listener) {
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }
    /** Remove registered SWT Move/Resize listeners. */
    public void removeControlListener(ControlListener listener) {
        removeTypedListener(SWT.Move, listener);
        removeTypedListener(SWT.Resize, listener);
    }
    /** Remove registered SWT Selection listeners. */
    public void removeSelectionListener(SelectionListener listener) {
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }
    /** Return LEFT, CENTER or RIGHT, preserving original SWT alignment bits. */
    public int getAlignment() {
        checkWidget();
        if ((style & SWT.RIGHT) != 0) return SWT.RIGHT;
        if ((style & SWT.CENTER) != 0) return SWT.CENTER;
        return SWT.LEFT;
    }
    /** Change alignment of the displayed column data. */
    public void setAlignment(int value) {
        checkWidget();
        if (value != SWT.LEFT && value != SWT.RIGHT && value != SWT.CENTER) return;
        style = (style & ~(SWT.LEFT | SWT.RIGHT | SWT.CENTER)) | value;
        parent.redraw();
    }
    /** Return whether the user may reorder this column. */
    public boolean getMoveable() { checkWidget(); return moveable; }
    /** Mark this column as user-moveable. Native drag-to-reorder is unqualified. */
    public void setMoveable(boolean value) { checkWidget(); moveable = value; }
    /** Return whether the user may resize this column. */
    public boolean getResizable() { checkWidget(); return resizable; }
    /** Mark this column resizable or fixed-width. */
    public void setResizable(boolean value) { checkWidget(); resizable = value; }
    /** Return the original Table parent. */
    public Table getParent() { checkWidget(); return parent; }
    /** Return the supplied tooltip string; native display is not implemented. */
    public String getToolTipText() { checkWidget(); return toolTipText; }
    /** Store a tooltip without inventing a native tooltip implementation. */
    public void setToolTipText(String value) { checkWidget(); toolTipText = value; }
    /** Current width in SWT logical pixels. */
    public int getWidth() { checkWidget(); return width; }
    /** Change width and emit original SWT.Resize if it actually changed. */
    public void setWidth(int value) {
        checkWidget();
        if (value < 0 || value == width) return;
        width = value;
        parent.redraw();
        sendEvent(SWT.Resize, new Event());
    }
    /** Compute a preferred width from the header using the mobile SWT GC. */
    public void pack() {
        checkWidget();
        Image scratch = new Image(display, 1, 1);
        try {
            GC gc = new GC(scratch);
            try {
                gc.setFont(parent.getFont());
                Point size = gc.textExtent(text);
                setWidth(Math.max(20, size.x + 16));
            } finally { gc.dispose(); }
        } finally { scratch.dispose(); }
    }
    /** Change the original column header text. */
    @Override public void setText(String value) {
        super.setText(value);
        parent.redraw();
    }
    /** Change caller-owned column image. */
    @Override public void setImage(Image value) {
        super.setImage(value);
        parent.redraw();
    }
    @Override void releaseParent() {
        Table owner = parent;
        parent = null;
        if (owner != null && !owner.isDisposed()) owner.removeColumn(this);
    }
}
