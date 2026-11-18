/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport.Layer;
import org.eclipse.swt.custom.LayeredViewport.WorldBounds;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * A native SWT tab strip above an opt-in layered page viewport.
 *
 * <p>Pages may be actual child Controls (command groups, editors, palettes),
 * or borrowed Images. Both slide through the same world-bound layer API.
 * Native image pages request the existing Maya/DirectComposition backend;
 * unsupported platforms use the normal painted fallback. Native Controls
 * remain in the OS child-control plane, rather than pretending to be images
 * or composited below painted overlays.</p>
 *
 * <p>Category selection is exclusive. Only clicks fire SWT.Selection on this
 * ribbon, with the selected Tab in Event.data. Programmatic selection mirrors
 * SWT TabFolder and does not fabricate a Selection event. Neither removing a
 * tab nor hiding a page disposes its application-owned Image or Control.</p>
 */
public final class LayeredRibbon extends Composite {
    private static final int DEFAULT_SLIDE_MILLIS = 180;
    private static final int DEFAULT_CONTENT_HEIGHT = 144;

    private final ToolBar categories;
    private final LayeredViewport pages;
    private final LayeredTransitions transitions;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab selected;
    private int slideMillis = DEFAULT_SLIDE_MILLIS;

    public LayeredRibbon(Composite parent, int style) {
        super(parent, style);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);
        categories = new ToolBar(this, SWT.FLAT | SWT.HORIZONTAL);
        categories.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        pages = new LayeredViewport(this, SWT.NONE);
        GridData content = new GridData(SWT.FILL, SWT.FILL, true, true);
        content.heightHint = DEFAULT_CONTENT_HEIGHT;
        pages.setLayoutData(content);
        transitions = new LayeredTransitions(pages);
        pages.addListener(SWT.Resize, event -> layoutPages());
    }

    /** Parent for the page Controls supplied to {@link #addControlTab}. */
    public LayeredViewport getPageViewport() {
        checkWidget();
        return pages;
    }

    /** The original native category ToolBar, for SWT styling and accessibility. */
    public ToolBar getCategoryBar() {
        checkWidget();
        return categories;
    }

    /** Default 180 ms; zero disables animation, including for reduced-motion UIs. */
    public void setSlideDuration(int millis) {
        checkWidget();
        if (millis < 0 || millis > 60_000) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        slideMillis = millis;
    }

    public int getSlideDuration() {
        checkWidget();
        return slideMillis;
    }

    /** Adds an application-owned native Control as a ribbon page. */
    public Tab addControlTab(String title, Control page) {
        checkWidget();
        if (title == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        Objects.requireNonNull(page, "page");
        if (page.isDisposed() || page.getParent() != pages) SWT.error(SWT.ERROR_INVALID_PARENT);
        Rectangle view = pages.getClientArea();
        Layer layer = pages.addControlLayer(title, page, 0, 0,
                Math.max(1, view.width), Math.max(1, view.height));
        return add(title, layer, page);
    }

    /**
     * Adds a borrowed raster page. It opts into the native compositor while
     * one-to-one rendering is supported; otherwise the page paints normally.
     */
    public Tab addImageTab(String title, Image image) {
        checkWidget();
        if (title == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        Objects.requireNonNull(image, "image");
        if (image.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        Layer layer = pages.addImageLayer(title, image, 0, 0);
        Tab tab = add(title, layer, null);
        // Native admission walks the accepted paint-order snapshot. Refresh it
        // after the append even when this new page is not selected yet.
        pages.getLayers();
        layer.setNative(true);
        return tab;
    }

    private Tab add(String title, Layer layer, Control control) {
        if (title == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
        ToolItem item = new ToolItem(categories, SWT.RADIO);
        item.setText(title);
        layer.setPinned(SWT.HORIZONTAL | SWT.VERTICAL);
        layer.setVisible(false);
        Tab tab = new Tab(item, layer, control);
        tabs.add(tab);
        item.addListener(SWT.Selection, event -> {
            if (item.getSelection() && !tab.removed) {
                select(tab, true);
            }
        });
        if (selected == null) select(tab, false);
        return tab;
    }

    public Tab[] getTabs() {
        checkWidget();
        return tabs.toArray(new Tab[0]);
    }

    public Tab getSelection() {
        checkWidget();
        return selected;
    }

    public void setSelection(Tab tab) {
        checkWidget();
        Objects.requireNonNull(tab, "tab").checked();
        if (!tabs.contains(tab)) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        select(tab, false);
    }

    private void select(Tab next, boolean notify) {
        if (selected == next) return;
        transitions.cancelAll();
        Tab prior = selected;
        int direction = prior == null || tabs.indexOf(next) > tabs.indexOf(prior) ? 1 : -1;
        // Snap interrupted motions into a single logical starting state.
        for (Tab tab : tabs) {
            if (tab != prior) tab.layer.setVisible(false);
            if (tab != prior && tab != next) place(tab, 0);
        }
        selected = next;
        int span = Math.max(1, pages.getClientArea().width);
        if (prior != null) place(prior, 0);
        next.layer.moveTo(pages.getLayers().length - 1);
        place(next, prior == null || slideMillis == 0 ? 0 : (long) direction * span);
        next.layer.setVisible(true);
        next.item.setSelection(true);
        for (Tab tab : tabs) if (tab != next) tab.item.setSelection(false);
        if (prior != null) {
            prior.layer.setVisible(true);
            if (slideMillis == 0) {
                place(prior, -(long) direction * span);
                prior.layer.setVisible(false);
            } else {
                final Tab outgoing = prior;
                transitions.moveTo(next.layer, 0, 0, slideMillis);
                transitions.moveTo(outgoing.layer, -(long) direction * span, 0,
                        slideMillis, LayeredTransitions.EASE_OUT_CUBIC,
                        () -> {
                            if (!isDisposed() && !outgoing.removed && selected != outgoing) {
                                outgoing.layer.setVisible(false);
                            }
                        });
            }
        }
        if (notify) {
            Event event = new Event();
            event.data = next;
            notifyListeners(SWT.Selection, event);
        }
    }

    private void place(Tab tab, long x) {
        if (tab.removed) return;
        WorldBounds bounds = tab.layer.getWorldBounds();
        Rectangle view = pages.getClientArea();
        long width = tab.control == null ? bounds.width() : Math.max(1, view.width);
        long height = tab.control == null ? bounds.height() : Math.max(1, view.height);
        tab.layer.setWorldBounds(x, 0, width, height);
    }

    private void layoutPages() {
        if (isDisposed() || selected == null) return;
        transitions.cancelAll();
        pages.beginUpdate();
        try {
            for (Tab tab : tabs) {
                if (tab.removed) continue;
                place(tab, 0);
                tab.layer.setVisible(tab == selected);
            }
        } finally {
            if (!pages.isDisposed()) pages.endUpdate();
        }
    }

    /** Stable category handle; removing it leaves its Control/Image application-owned. */
    public final class Tab {
        private final ToolItem item;
        private final Layer layer;
        private final Control control;
        private boolean removed;

        private Tab(ToolItem item, Layer layer, Control control) {
            this.item = item;
            this.layer = layer;
            this.control = control;
        }

        private void checked() {
            LayeredRibbon.this.checkWidget();
            if (removed) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
        }

        public Layer getLayer() {
            checked();
            return layer;
        }

        public Control getControl() {
            checked();
            return control;
        }

        public String getText() {
            checked();
            return item.getText();
        }

        public void setText(String value) {
            checked();
            if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
            item.setText(value);
            layer.setName(value);
        }

        public void remove() {
            checked();
            transitions.cancelAll();
            int index = tabs.indexOf(this);
            boolean wasSelected = selected == this;
            if (control != null && !control.isDisposed()) control.setVisible(false);
            layer.setVisible(false);
            layer.remove();
            removed = true;
            tabs.remove(index);
            item.dispose();
            if (wasSelected) {
                selected = null;
                if (!tabs.isEmpty()) select(tabs.get(Math.min(index, tabs.size() - 1)), false);
            } else {
                layoutPages();
            }
        }
    }
}
