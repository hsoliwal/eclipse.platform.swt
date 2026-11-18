/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredRibbon;
import org.eclipse.swt.custom.LayeredTransitions;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.ViewportTweenMath;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Opt-in ribbon and headless geometry contracts; native pixels are tested separately. */
@SuppressWarnings("restriction")
public class Test_org_eclipse_swt_custom_LayeredRibbon {
    private Display display;
    private Shell shell;
    private LayeredRibbon ribbon;

    @BeforeEach
    public void setUp() {
        display = Display.getDefault();
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
        ribbon = new LayeredRibbon(shell, SWT.NONE);
        shell.setSize(460, 250);
        shell.open();
        ribbon.layout(true, true);
    }

    @AfterEach
    public void tearDown() {
        if (shell != null && !shell.isDisposed()) shell.dispose();
    }

    private void pump(long millis) {
        long end = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < end) {
            if (!display.readAndDispatch()) {
                try { Thread.sleep(5); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Test
    public void nativeControlRibbonSlidesBetweenTabsAndKeepsIds() {
        ribbon.setSlideDuration(0);
        LayeredViewport pages = ribbon.getPageViewport();
        Composite home = new Composite(pages, SWT.NONE);
        Composite edit = new Composite(pages, SWT.NONE);
        LayeredRibbon.Tab first = ribbon.addControlTab("Home", home);
        LayeredRibbon.Tab second = ribbon.addControlTab("Edit", edit);
        long homeId = first.getLayer().getId();
        assertSame(first, ribbon.getSelection());
        assertEquals(SWT.HORIZONTAL | SWT.VERTICAL, first.getLayer().getPinned());
        assertTrue(home.getVisible());
        assertFalse(edit.getVisible());
        List<LayeredRibbon.Tab> events = new ArrayList<>();
        ribbon.addListener(SWT.Selection, event -> events.add((LayeredRibbon.Tab) event.data));
        ribbon.setSelection(second);
        assertSame(second, ribbon.getSelection());
        assertFalse(home.getVisible());
        assertTrue(edit.getVisible());
        assertEquals(0, second.getLayer().getWorldBounds().x());
        assertEquals(homeId, first.getLayer().getId());
        assertTrue(events.isEmpty(), "a programmatic tab change does not fabricate SWT.Selection");
        ToolItem homeItem = ribbon.getCategoryBar().getItem(0);
        homeItem.setSelection(true);
        homeItem.notifyListeners(SWT.Selection, new Event());
        assertEquals(List.of(first), events);
        assertSame(first, ribbon.getSelection());
        assertEquals(0, first.getLayer().getWorldBounds().x());
        assertEquals(pages.getClientArea().width, home.getBounds().width);
        second.remove();
        assertFalse(edit.isDisposed(), "removing a tab never disposes the application Control");
    }

    @Test
    public void nativeImagePagePreservesSizeAndBorrowedImageThroughTransitions() {
        LayeredViewport pages = ribbon.getPageViewport();
        Image raster = new Image(display, 40, 28);
        GC gc = new GC(raster);
        try {
            gc.fillRectangle(raster.getBounds());
        } finally {
            gc.dispose();
        }
        try {
            Composite home = new Composite(pages, SWT.NONE);
            LayeredRibbon.Tab first = ribbon.addControlTab("Commands", home);
            LayeredRibbon.Tab image = ribbon.addImageTab("Preview", raster);
            ribbon.setSlideDuration(48);
            ribbon.setSelection(image);
            pump(250);
            assertSame(image, ribbon.getSelection());
            assertEquals(0, image.getLayer().getWorldBounds().x());
            assertEquals(40, image.getLayer().getWorldBounds().width());
            assertEquals(28, image.getLayer().getWorldBounds().height());
            assertFalse(first.getLayer().isVisible());
            assertTrue(image.getLayer().isVisible());
            image.remove();
            assertFalse(raster.isDisposed(), "the image is borrowed, not transferred");
            assertSame(first, ribbon.getSelection());
        } finally {
            raster.dispose();
        }
    }

    @Test
    public void interruptedMovesDoNotCompleteAndRemovalCancelsTimers() {
        LayeredViewport pages = ribbon.getPageViewport();
        Composite p = new Composite(pages, SWT.NONE);
        LayeredRibbon.Tab page = ribbon.addControlTab("Tools", p);
        LayeredTransitions transitions = new LayeredTransitions(pages);
        int[] completed = {0};
        var first = transitions.moveTo(page.getLayer(), 100, 0, 150,
                LayeredTransitions.EASE_OUT_CUBIC, () -> completed[0]++);
        assertTrue(first.isRunning());
        first.cancel();
        pump(180);
        assertEquals(0, completed[0]);
        assertEquals(0, transitions.runningCount());
        var second = transitions.moveTo(page.getLayer(), 50, 0, 20,
                LayeredTransitions.LINEAR, () -> completed[0]++);
        pump(160);
        assertFalse(second.isRunning());
        assertEquals(1, completed[0]);
        assertEquals(50, page.getLayer().getWorldBounds().x());
        page.remove();
        assertTrue(transitions.runningCount() == 0);
    }

    @Test
    public void exact64BitMotionAndBounds() {
        long large = 1L << 55;
        for (int mode = 0; mode <= 2; mode++) {
            assertEquals(large + 7, ViewportTweenMath.position(large + 7, large + 19, 0, 100, mode));
            assertEquals(large + 19, ViewportTweenMath.position(large + 7, large + 19, 100, 100, mode));
            long previous = Long.MIN_VALUE;
            for (int i = 0; i <= 100; i++) {
                long next = ViewportTweenMath.position(Long.MIN_VALUE, Long.MAX_VALUE, i, 100, mode);
                assertTrue(next >= previous, "motion cannot reverse: frame " + i);
                previous = next;
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> ViewportTweenMath.position(0, 1, 10, -1, ViewportTweenMath.LINEAR));
        assertThrows(IllegalArgumentException.class,
                () -> ViewportTweenMath.position(0, 1, 10, 20, 99));
        assertThrows(IllegalArgumentException.class, () -> ribbon.setSlideDuration(-1));
        assertThrows(NullPointerException.class,
                () -> ribbon.setSelection(null));
    }
}
