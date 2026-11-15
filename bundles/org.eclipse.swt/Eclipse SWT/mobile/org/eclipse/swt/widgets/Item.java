/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;

/**
 * Original SWT non-windowed Item superclass. The parent owns the native
 * control; items own only their Java identity/text/image and listener state.
 */
public abstract class Item extends Widget {
    String text = "";
    Image image;

    /** Construct an original SWT item under a widget owner. */
    public Item(Widget parent, int style) { super(parent, style); }

    /** Construct an original SWT item with a parent-managed logical index. */
    public Item(Widget parent, int style, int index) { this(parent, style); }

    /** Return the item's text, retaining the ordinary SWT empty default. */
    public String getText() { checkWidget(); return text; }

    /** Return the optional original SWT image without transferring ownership. */
    public Image getImage() { checkWidget(); return image; }

    /** Update text owned by this item; null is forbidden. */
    public void setText(String value) {
        checkWidget();
        if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        text = value;
    }

    /** Update caller-owned image; disposed or cross-device images are invalid. */
    public void setImage(Image value) {
        checkWidget();
        if (value != null && (value.isDisposed() || value.getDevice() != display))
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        image = value;
    }

    @Override void releaseWidget() {
        text = null;
        image = null;
        super.releaseWidget();
    }
}
