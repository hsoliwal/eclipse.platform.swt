/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Original SWT TableItem identity for a materialized logical row. The parent
 * owns the sparse sorted row-index arrays, so SWT.VIRTUAL tables do not create
 * one TableItem per count and never densely enumerate unobserved rows.
 */
public class TableItem extends Item {
    private Table parent;
    int rowIndex;
    boolean cached, settingData;
    private boolean checked, grayed;
    private int imageIndent;
    private String[] cellTexts;
    private Image[] cellImages;
    private Color background, foreground;
    private Font font;

    /** Append an item to a nonvirtual table. */
    public TableItem(Table parent, int style) {
        this(parent, style, parent == null ? 0 : parent.getItemCount());
    }
    /** Insert an item at the specified logical index. */
    public TableItem(Table parent, int style, int index) {
        this(parent, style, index, true);
    }
    /** Materialize a virtual row identity without increasing logical item count. */
    TableItem(Table parent, int style, int index, boolean insert) {
        super(parent, style, index);
        this.parent = parent;
        rowIndex = index;
        if (insert) {
            cached = true;
            parent.insertItem(this, index);
        }
    }

    /** The original SWT Table parent. */
    public Table getParent() { checkWidget(); return parent; }
    /** Current logical row bounds, clipped only by the native viewport. */
    public Rectangle getBounds() { checkWidget(); return parent.itemBounds(this, -1); }
    /** Bounds for one logical column. */
    public Rectangle getBounds(int column) { checkWidget(); return parent.itemBounds(this, column); }
    /** Approximate text-cell geometry from the SWT GC/column layout. */
    public Rectangle getTextBounds(int column) { return getBounds(column); }
    /** Approximate cell image geometry from the SWT GC/column layout. */
    public Rectangle getImageBounds(int column) { return getBounds(column); }
    /** Return checkbox selection. */
    public boolean getChecked() { checkWidget(); parent.ensureData(this); return checked; }
    /** Set a CHECK-style row value without sending user Selection. */
    public void setChecked(boolean value) {
        checkWidget();
        checked = value; cached = true; parent.redraw();
    }
    /** Return the row grayed state. */
    public boolean getGrayed() { checkWidget(); return grayed; }
    /** Set the row's grayed state. */
    public void setGrayed(boolean value) {
        checkWidget();
        grayed = value; cached = true; parent.redraw();
    }
    /** Get optional row indentation. */
    public int getImageIndent() { checkWidget(); return imageIndent; }
    /** Set nonnegative row indentation. */
    public void setImageIndent(int indent) {
        checkWidget();
        imageIndent = Math.max(0, indent);
        parent.redraw();
    }

    /** Query text in the primary column. */
    @Override public String getText() { return getText(0); }
    /** Query text by column index, lazily firing SWT.SetData for virtual rows. */
    public String getText(int column) {
        checkWidget();
        parent.ensureData(this);
        if (isDisposed()) return "";
        if (column <= 0) return text;
        return cellTexts == null || column >= cellTexts.length || cellTexts[column] == null
                ? "" : cellTexts[column];
    }
    /** Set text in the primary column. */
    @Override public void setText(String value) { setText(0, value); }
    /** Update one cell of the Java-owned row data. */
    public void setText(int column, String value) {
        checkWidget();
        if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (column < 0) return;
        if (column == 0) text = value;
        else {
            if (column >= parent.getColumnCount()) return;
            if (cellTexts == null || cellTexts.length <= column)
                cellTexts = Arrays.copyOf(cellTexts == null ? new String[0] : cellTexts, column + 1);
            cellTexts[column] = value;
        }
        cached = true;
        parent.redraw();
    }
    /** Set text for multiple columns without changing row identity. */
    public void setText(String[] values) {
        checkWidget();
        if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (String value : values) if (value == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        for (int i = 0; i < values.length; i++) setText(i, values[i]);
    }
    /** Query image in the first logical column. */
    @Override public Image getImage() { return getImage(0); }
    /** Query caller-owned image for one cell. */
    public Image getImage(int column) {
        checkWidget();
        parent.ensureData(this);
        if (column < 0) return null;
        if (column == 0) return image;
        return cellImages == null || column >= cellImages.length ? null : cellImages[column];
    }
    /** Set primary cell image. */
    @Override public void setImage(Image value) { setImage(0, value); }
    /** Set optional caller-owned image for one cell. */
    public void setImage(int column, Image value) {
        checkWidget();
        if (value != null && (value.isDisposed() || value.getDevice() != display))
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        if (column < 0 || (column > 0 && column >= parent.getColumnCount())) return;
        if (column == 0) image = value;
        else {
            if (cellImages == null || cellImages.length <= column)
                cellImages = Arrays.copyOf(cellImages == null ? new Image[0] : cellImages, column + 1);
            cellImages[column] = value;
        }
        cached = true;
        parent.redraw();
    }
    /** Set image array for all provided columns. */
    public void setImage(Image[] values) {
        checkWidget();
        if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        for (Image value : values)
            if (value != null && (value.isDisposed() || value.getDevice() != display))
                SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        for (int i = 0; i < values.length; i++) setImage(i, values[i]);
    }

    /** Return row or inherited background. */
    public Color getBackground() { checkWidget(); return background == null ? parent.getBackground() : background; }
    /** Return cell background; per-cell overrides are not installed yet. */
    public Color getBackground(int column) { return getBackground(); }
    /** Apply a row background, preserving caller resource ownership. */
    public void setBackground(Color value) {
        checkWidget();
        if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        background = value; cached = true; parent.redraw();
    }
    /** Apply the same row background to the requested cell (fallback). */
    public void setBackground(int column, Color value) { setBackground(value); }
    /** Return row or inherited foreground. */
    public Color getForeground() { checkWidget(); return foreground == null ? parent.getForeground() : foreground; }
    /** Return cell foreground. */
    public Color getForeground(int column) { return getForeground(); }
    /** Apply row foreground. */
    public void setForeground(Color value) {
        checkWidget();
        if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        foreground = value; cached = true; parent.redraw();
    }
    /** Apply requested cell foreground (row fallback). */
    public void setForeground(int column, Color value) { setForeground(value); }
    /** Return row or inherited SWT font. */
    public Font getFont() { checkWidget(); return font == null ? parent.getFont() : font; }
    /** Return the column font (row fallback). */
    public Font getFont(int column) { return getFont(); }
    /** Update row font. */
    public void setFont(Font value) {
        checkWidget();
        if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        font = value; cached = true; parent.redraw();
    }
    /** Update a column font (row fallback). */
    public void setFont(int column, Font value) { setFont(value); }

    /** Forget virtual content without invalidating the stable row wrapper. */
    void clearData() {
        cached = false;
        text = "";
        image = null;
        cellTexts = null;
        cellImages = null;
        checked = grayed = false;
    }

    @Override void releaseParent() {
        Table owner = parent;
        parent = null;
        if (owner != null && !owner.isDisposed()) owner.removeItem(this);
    }
}
