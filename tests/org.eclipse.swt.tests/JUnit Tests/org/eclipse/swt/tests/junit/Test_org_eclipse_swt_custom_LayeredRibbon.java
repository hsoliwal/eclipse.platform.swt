/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredRibbon;
import org.eclipse.swt.custom.LayeredSlidePanel;
import org.eclipse.swt.custom.LayeredTransitions;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

/** Native SWT smoke tests: the oracle is layer geometry and real SWT controls. */
public class Test_org_eclipse_swt_custom_LayeredRibbon {
    @Test
    public void pinnedNativeTabsRemainOverScrollingRowsAndPreserveSelection() {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        LayeredViewport viewport = new LayeredViewport(shell, SWT.NONE);
        LayeredRibbon ribbon = new LayeredRibbon(viewport, 38, 160);
        try {
            viewport.addPaintLayer("rows", 0, 0, 2000, 2000, (gc, ctx) -> {});
            Composite first = ribbon.addTab("Home");
            Composite second = ribbon.addTab("Insert");
            ribbon.bringToFront();
            shell.setSize(400, 300);
            shell.open();
            drain(display);
            assertEquals(SWT.HORIZONTAL | SWT.VERTICAL, ribbon.layer().getPinned());
            assertEquals(38, ribbon.visibleHeight());
            assertSame(ribbon.layer(), viewport.hitTest(20, 10));
            Rectangle before = ribbon.tabs().getBounds();
            viewport.setOrigin(500, 700);
            drain(display);
            assertEquals(before, ribbon.tabs().getBounds());
            assertSame(ribbon.layer(), viewport.hitTest(20, 10));
            ribbon.select(1);
            ribbon.setExpanded(true, false);
            assertEquals(1, ribbon.tabs().getSelectionIndex());
            assertEquals(160, ribbon.visibleHeight());
            assertFalse(first.isDisposed());
            assertFalse(second.isDisposed());
            ribbon.setExpanded(false, false);
            assertEquals(38, ribbon.visibleHeight());
        } finally {
            if (!shell.isDisposed()) shell.dispose();
        }
    }

    @Test
    public void slideReversalAndDisposalLeaveNoDanglingNativeControl() {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        LayeredViewport viewport = new LayeredViewport(shell, SWT.NONE);
        Composite drawer = new Composite(viewport, SWT.NONE);
        LayeredSlidePanel slide = new LayeredSlidePanel(viewport, drawer, SWT.RIGHT, 0, 120);
        try {
            shell.setSize(360, 240);
            shell.open();
            drain(display);
            assertEquals(0, slide.visibleExtent());
            slide.setExpanded(true, false);
            assertEquals(120, slide.visibleExtent());
            assertTrue(drawer.getBounds().x < viewport.getClientArea().width);
            slide.setExpanded(false, true);
            slide.setExpanded(true, true);
            slide.setExpanded(false, false);
            assertEquals(0, slide.visibleExtent());
            slide.close();
            assertFalse(drawer.isDisposed(), "borrowed drawer stays owned by application");
            assertThrows(RuntimeException.class, () -> slide.setExpanded(true));
        } finally {
            if (!shell.isDisposed()) shell.dispose();
            drain(display);
        }
    }

    @Test
    public void nativeTabbedRibbonRejectsInvalidBoundsAndSupportsDisposalDuringMotion() {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        LayeredViewport viewport = new LayeredViewport(shell, SWT.NONE);
        try {
            assertThrows(RuntimeException.class, () -> new LayeredRibbon(viewport, 0, 100));
            assertThrows(RuntimeException.class, () -> new LayeredRibbon(viewport, 120, 100));
            LayeredRibbon ribbon = new LayeredRibbon(viewport, 38, 160);
            ribbon.addTab("View");
            shell.setSize(360, 240);
            shell.open();
            ribbon.setExpanded(true, true);
            ribbon.close();
            org.eclipse.swt.SWTException error = assertThrows(org.eclipse.swt.SWTException.class,
                    () -> ribbon.tabs());
            assertEquals(SWT.ERROR_WIDGET_DISPOSED, error.code);
        } finally {
            if (!shell.isDisposed()) shell.dispose();
            drain(display);
        }
    }


    @Test
    public void imageFadeAndCrossfadePreserveLayerIdentityAndExactEndpoints() {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        LayeredViewport viewport = new LayeredViewport(shell, SWT.NONE);
        org.eclipse.swt.graphics.Image borrowed = new org.eclipse.swt.graphics.Image(display, 32, 32);
        try {
            LayeredViewport.Layer a = viewport.addImageLayer("a", borrowed, 0, 0);
            LayeredViewport.Layer b = viewport.addImageLayer("b", borrowed, 0, 0);
            LayeredTransitions animation = new LayeredTransitions(viewport);
            shell.setSize(220, 150);
            shell.open();
            drain(display);
            animation.fadeTo(b, 0, 0);
            int[] completed = new int[2];
            LayeredTransitions.Motion fadingOut = animation.fadeTo(a, 0, 60,
                    LayeredTransitions.LINEAR, () -> completed[0]++);
            LayeredTransitions.Motion fadingIn = animation.fadeTo(b, 255, 60,
                    LayeredTransitions.EASE_IN_OUT_CUBIC, () -> completed[1]++);
            assertTrue(fadingOut.isRunning());
            assertTrue(fadingIn.isRunning());
            assertEquals(2, animation.runningCount(), "same timer owns both image fades");
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (animation.runningCount() > 0 && System.nanoTime() < deadline) {
                drain(display);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    fail("interrupted while awaiting native UI-timer completion");
                }
            }
            assertEquals(0, animation.runningCount());
            assertEquals(0, a.getOpacity());
            assertEquals(255, b.getOpacity());
            assertArrayEquals(new int[] {1, 1}, completed);
            assertEquals(2, viewport.getLayers().length);
            LayeredTransitions.Motion old = animation.fadeTo(a, 255, 1000,
                    LayeredTransitions.LINEAR, () -> fail("cancelled fade completed"));
            assertTrue(old.isRunning());
            animation.fadeTo(a, 71, 0);
            assertFalse(old.isRunning());
            assertEquals(71, a.getOpacity());
            assertEquals(0, animation.runningCount());
            assertFalse(borrowed.isDisposed(), "caller owns the image");
        } finally {
            if (!shell.isDisposed()) shell.dispose();
            borrowed.dispose();
            drain(display);
        }
    }

    @Test
    public void nativeControlFadeIsRejectedRatherThanImpersonatingAlpha() {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        LayeredViewport viewport = new LayeredViewport(shell, SWT.NONE);
        Composite child = new Composite(viewport, SWT.NONE);
        try {
            LayeredViewport.Layer nativeChild = viewport.addControlLayer(
                    "child", child, 0, 0, 60, 30);
            LayeredTransitions animation = new LayeredTransitions(viewport);
            assertThrows(RuntimeException.class, () -> animation.fadeTo(nativeChild, 128, 100));
            assertThrows(RuntimeException.class, () -> animation.fadeTo(nativeChild, 256, 100));
            assertEquals(255, nativeChild.getOpacity());
            assertEquals(0, animation.runningCount());
            assertFalse(child.isDisposed());
        } finally {
            if (!shell.isDisposed()) shell.dispose();
            drain(display);
        }
    }

    private static void drain(Display display) {
        while (!display.isDisposed() && display.readAndDispatch()) {
            // Ensure native events and Layout are delivered.
        }
    }
}
