/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.custom;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

/**
 * A native tabbed ribbon over an existing LayeredViewport: a single TabFolder
 * native Control layer, not one Control per painted row or a new viewport.
 *
 * <p>Its tab strip stays visible and pinned while the page below slides out or
 * in, with world rows and image layers scrolling beneath. The native TabFolder
 * retains standard SWT selection, keyboard focus and accessible tab behavior.</p>
 */
public final class LayeredRibbon implements AutoCloseable {
    private final TabFolder tabs;
    private final LayeredSlidePanel slide;
    private boolean closed;

    /** Total height is the height with the ribbon expanded, including tabs. */
    public LayeredRibbon(LayeredViewport viewport, int tabStripHeight, int totalHeight) {
        if (viewport == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        if (viewport.isDisposed()) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
        if (tabStripHeight <= 0 || totalHeight <= tabStripHeight)
            SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        tabs = new TabFolder(viewport, SWT.TOP);
        try {
            slide = new LayeredSlidePanel(viewport, tabs, SWT.TOP, tabStripHeight, totalHeight);
        } catch (RuntimeException failure) {
            tabs.dispose();
            throw failure;
        }
        tabs.addListener(SWT.Selection, event -> {
            if (!closed && !tabs.isDisposed()) slide.setExpanded(true);
        });
    }

    /** The native tab control, for existing SWT listeners and styling. */
    public TabFolder tabs() {
        check();
        return tabs;
    }

    /** Native layer handle: stable across selections, panning and animation. */
    public LayeredViewport.Layer layer() {
        check();
        return slide.layer();
    }

    /** Adds a native tab page; the caller owns any children placed on it. */
    public Composite addTab(String title) {
        check();
        if (title == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        TabItem tab = new TabItem(tabs, SWT.NONE);
        Composite page = new Composite(tabs, SWT.NONE);
        page.setLayout(new FillLayout());
        tab.setText(title);
        tab.setControl(page);
        return page;
    }

    public void select(int index) {
        check();
        if (index < 0 || index >= tabs.getItemCount()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        tabs.setSelection(index);
        slide.setExpanded(true);
    }

    public int visibleHeight() {
        check();
        return slide.visibleExtent();
    }

    public boolean isExpanded() {
        check();
        return slide.isExpanded();
    }

    public void setExpanded(boolean expanded, boolean animate) {
        check();
        slide.setExpanded(expanded, animate);
    }

    public void toggle() {
        check();
        slide.setExpanded(!slide.isExpanded());
    }

    /** Places the native ribbon above older control/paint layers in Z order. */
    public void bringToFront() {
        check();
        slide.bringToFront();
    }

    private void check() {
        if (closed || tabs.isDisposed()) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
    }

    /** Owns the TabFolder; removing its Layer does not dispose other controls. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        slide.close();
        if (!tabs.isDisposed()) tabs.dispose();
    }
}
