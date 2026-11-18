/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.custom.LayeredViewport.Layer;
import org.eclipse.swt.custom.LayeredViewport.WorldBounds;
import org.eclipse.swt.custom.LayeredViewport.WorldPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract tests for the opt-in painted Z-stack. */
public class Test_org_eclipse_swt_custom_LayeredViewport extends Test_org_eclipse_swt_widgets_Canvas {
    private LayeredViewport viewport;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        viewport = new LayeredViewport(shell, SWT.NONE);
        viewport.setSize(300, 240);
        setWidget(viewport);
    }

    @Test
    public void testOrderVisibilityAndHitTesting() {
        Layer bottom = viewport.addPaintLayer("bottom", 0, 0, 200, 200, (gc, ctx) -> { });
        Layer top = viewport.addPaintLayer("top", 5, 5, 100, 100, (gc, ctx) -> { });

        assertSame(top, viewport.hitTest(10, 10));
        assertEquals(0, bottom.getZIndex());
        assertEquals(1, top.getZIndex());
        bottom.moveTo(1);
        assertSame(bottom, viewport.hitTest(10, 10));

        bottom.setLocked(true);
        assertSame(top, viewport.hitTest(10, 10));
        top.setVisible(false);
        assertNull(viewport.hitTest(10, 10));
        bottom.setLocked(false);
        assertSame(bottom, viewport.hitTest(10, 10));

        bottom.setOpacity(0);
        assertNull(viewport.hitTest(10, 10));
        bottom.setOpacity(255);
        assertSame(bottom, viewport.hitTest(10, 10));
    }

    @Test
    public void testSnapshotAndRemovedIdentity() {
        Layer first = viewport.addPaintLayer("first", 0, 0, 20, 20, (gc, ctx) -> { });
        Layer second = viewport.addPaintLayer("second", 0, 0, 20, 20, (gc, ctx) -> { });
        Layer[] snapshot = viewport.getLayers();
        snapshot[0] = second;
        assertSame(first, viewport.getLayers()[0]);

        long id = first.getId();
        assertTrue(id > 0);
        first.remove();
        assertEquals(1, viewport.getLayers().length);
        assertSame(second, viewport.getLayers()[0]);
        assertThrows(IllegalArgumentException.class, first::getId);
        assertTrue(second.getId() > id);
    }

    @Test
    public void testLongCoordinatesAndWorldProjection() {
        long large = 1L << 55;
        viewport.setOrigin(large, large);
        Layer far = viewport.addPaintLayer("far", large + 12, large + 20,
                100, 80, (gc, ctx) -> { });

        assertEquals(new WorldPoint(large + 18, large + 25), viewport.toWorld(18, 25));
        assertEquals(18, viewport.toScreen(large + 18, large + 25).x);
        assertSame(far, viewport.hitTest(18, 25));
        assertEquals(new WorldBounds(large + 12, large + 20, 100, 80),
                far.getWorldBounds());
        viewport.setZoom(2.0);
        assertSame(far, viewport.hitTest(30, 50));
    }

    @Test
    public void testInvalidBoundsAndHitTestRefinement() {
        assertThrows(IllegalArgumentException.class, () ->
                viewport.addPaintLayer("bad", Long.MAX_VALUE, 0, 2, 1,
                        (gc, ctx) -> { }));
        Layer layer = viewport.addPaintLayer("pick", 0, 0, 100, 100, (gc, ctx) -> { });
        assertThrows(IllegalArgumentException.class, () -> layer.setOpacity(256));
        assertThrows(IllegalArgumentException.class, () -> viewport.setZoom(Double.NaN));
        layer.setHitTester((candidate, worldX, worldY) -> worldX >= 50);
        assertNull(viewport.hitTest(12, 12));
        assertSame(layer, viewport.hitTest(60, 12));
        layer.setWorldClip(new WorldBounds(60, 0, 10, 25));
        assertNull(viewport.hitTest(55, 12));
        assertSame(layer, viewport.hitTest(65, 12));
        layer.setWorldClip(null);
        assertSame(layer, viewport.hitTest(65, 12));
    }
}
