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
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
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
    public void testDisjointClipAndBatchedLayerChanges() {
        Layer layer = viewport.addPaintLayer("clipped", 0, 0, 100, 100,
                (gc, context) -> { });
        layer.setWorldClip(new WorldBounds(200, 200, 20, 20));
        assertNull(viewport.hitTest(10, 10));
        viewport.beginUpdate();
        viewport.beginUpdate();
        try {
            layer.setWorldClip(new WorldBounds(4, 4, 30, 30));
            layer.setWorldBounds(5, 5, 45, 45);
            layer.setOpacity(210);
            layer.setVisible(false);
            layer.setVisible(true);
        } finally {
            viewport.endUpdate();
            viewport.endUpdate();
        }
        assertSame(layer, viewport.hitTest(10, 10));
        assertThrows(IllegalArgumentException.class, viewport::endUpdate);
    }

    @Test
    public void testReentrantHitTesterPreservesOuterCandidateSnapshot() {
        Layer bottom = viewport.addPaintLayer("bottom", 0, 0, 200, 200,
                (gc, context) -> { });
        Layer top = viewport.addPaintLayer("top", 0, 0, 200, 200,
                (gc, context) -> { });
        final boolean[] nested = { false };
        top.setHitTester((candidate, x, y) -> {
            if (!nested[0]) {
                nested[0] = true;
                top.setLocked(true);
                assertSame(bottom, viewport.hitTest(5, 5));
                top.setLocked(false);
            }
            return false;
        });
        assertSame(bottom, viewport.hitTest(5, 5));
        assertTrue(nested[0]);
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

    /* opens the shell and lets every pending paint run */
    private void show() {
        shell.setSize(400, 340);
        SwtTestUtil.openShell(shell);
        viewport.update();
        SwtTestUtil.processEvents();
    }

    /* the widget's pixels as they are on the screen */
    private ImageData pixels() {
        Rectangle area = viewport.getClientArea();
        Image shot = new Image(viewport.getDisplay(), area.width, area.height);
        GC gc = new GC(viewport);
        try {
            gc.copyArea(shot, 0, 0);
        } finally {
            gc.dispose();
        }
        ImageData data = shot.getImageData();
        shot.dispose();
        return data;
    }

    private static RGB rgb(ImageData data, int x, int y) {
        return data.palette.getRGB(data.getPixel(x, y));
    }

    @Test
    public void testAnExposeIsServedFromTheBackingStore() {
        int[] paints = { 0 };
        viewport.addPaintLayer("count", 0, 0, 200, 200, (gc, ctx) -> {
            paints[0]++;
            gc.fillRectangle(ctx.clip());
        });
        show();
        int composed = paints[0];
        assertTrue(composed > 0, "the first paint composes the layer");
        // the window system asks for the pixels again (the shell repaints its children); no layer changed
        shell.redraw(0, 0, shell.getSize().x, shell.getSize().y, true);
        shell.update();
        SwtTestUtil.processEvents();
        assertEquals(composed, paints[0], "an expose is copied from the backing store");
        viewport.redraw();
        viewport.update();
        assertTrue(paints[0] > composed, "redraw() still recomposes the layers");
    }

    @Test
    public void testAPanRecomposesOnlyTheBandThatComesIntoView() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        java.util.List<Rectangle> clips = new java.util.ArrayList<>();
        viewport.addPaintLayer("stripes", 0, 0, 4000, 4000, (gc, ctx) -> {
            clips.add(ctx.clip());
            Rectangle clip = ctx.clip();
            for (int x = clip.x; x < clip.x + clip.width; x++) {
                // stripes fixed in the world: a pan shows whether pixels moved with it
                boolean dark = Math.floorMod(ctx.originX() + x, 7) == 0;
                gc.setForeground(gc.getDevice().getSystemColor(dark ? SWT.COLOR_BLACK : SWT.COLOR_WHITE));
                gc.drawLine(x, clip.y, x, clip.y + clip.height - 1);
            }
        });
        show();
        clips.clear();
        viewport.setOrigin(10, 0);
        viewport.update();
        assertFalse(clips.isEmpty(), "the band is painted");
        for (Rectangle clip : clips) assertTrue(clip.width <= 10, "only the band that came into view: " + clips);
        assertSameAsAFullRecomposition();
        // back the other way: the band is on the left
        clips.clear();
        viewport.setOrigin(0, 0);
        viewport.update();
        assertFalse(clips.isEmpty(), "the band is painted");
        for (Rectangle clip : clips) {
            assertTrue(clip.x == 0 && clip.width <= 10, "only the band that came into view: " + clips);
        }
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testDirtyPixelsMoveWithAPan() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer marker = viewport.addPaintLayer("marker", 30, 0, 10, 50, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle(ctx.clip());
        });
        show();
        marker.setWorldBounds(60, 0, 10, 50); // its old and new pixels are dirty, not yet repainted
        viewport.setOrigin(10, 0); // and they move with the retained pixels
        viewport.update();
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testARetainedLayerIsNotRepaintedByChangesAboveIt() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        int[] mapPaints = { 0 }, mapColor = { SWT.COLOR_DARK_GREEN };
        Layer map = viewport.addPaintLayer("map", 0, 0, 300, 240, (gc, ctx) -> {
            mapPaints[0]++;
            gc.setForeground(gc.getDevice().getSystemColor(mapColor[0]));
            for (int y = 0; y < 240; y += 6) gc.drawLine(0, y, 300, 240 - y);
        });
        map.setRetained(true);
        assertTrue(map.isRetained());
        Layer marker = viewport.addPaintLayer("marker", 20, 20, 10, 10, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
        });
        show();
        mapPaints[0] = 0;
        marker.setWorldBounds(100, 100, 10, 10);
        viewport.update();
        assertEquals(0, mapPaints[0], "the map under the moved marker comes from its retained composite");
        viewport.beginUpdate();
        marker.setWorldBounds(150, 60, 10, 10);
        viewport.endUpdate();
        viewport.update();
        assertEquals(0, mapPaints[0], "batched changes keep that precision");
        assertSameAsAFullRecomposition();
        // the retained layer's own content changes: its composite is recomposed
        mapColor[0] = SWT.COLOR_MAGENTA;
        map.redraw();
        viewport.update();
        assertTrue(mapPaints[0] > 0, "the map repaints its own change");
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testAChangeBelowARetainedLayerRefreshesItsComposite() {
        int[] ground = { SWT.COLOR_WHITE };
        Layer below = viewport.addPaintLayer("ground", 0, 0, 300, 240, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(ground[0]));
            gc.fillRectangle(ctx.clip());
        });
        Layer top = viewport.addPaintLayer("lines", 0, 0, 300, 240, (gc, ctx) -> {
            gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
            gc.drawLine(0, 0, 299, 239);
        });
        top.setRetained(true);
        show();
        ground[0] = SWT.COLOR_YELLOW;
        below.redraw();
        viewport.update();
        assertEquals(viewport.getDisplay().getSystemColor(SWT.COLOR_YELLOW).getRGB(), rgb(pixels(), 200, 20),
                "the retained composite above the change is recomposed");
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testRetainedCompositesMoveWithAPan() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        java.util.List<Rectangle> clips = new java.util.ArrayList<>();
        Layer stripes = viewport.addPaintLayer("stripes", -4000, 0, 8000, 4000, (gc, ctx) -> {
            clips.add(ctx.clip());
            Rectangle clip = ctx.clip();
            for (int x = clip.x; x < clip.x + clip.width; x++) {
                boolean dark = Math.floorMod(ctx.originX() + x, 9) == 0;
                gc.setForeground(gc.getDevice().getSystemColor(dark ? SWT.COLOR_BLUE : SWT.COLOR_WHITE));
                gc.drawLine(x, clip.y, x, clip.y + clip.height - 1);
            }
        });
        stripes.setRetained(true);
        viewport.addPaintLayer("dot", 40, 40, 8, 8, (gc, ctx) -> gc.fillRectangle(ctx.clip()));
        show();
        clips.clear();
        viewport.setOrigin(-12, 0);
        viewport.update();
        assertFalse(clips.isEmpty());
        for (Rectangle clip : clips) assertTrue(clip.width <= 12, "only the band: " + clips);
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testABackgroundChangeAfterTheFirstPaintIsShown() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.addPaintLayer("dot", 10, 10, 5, 5, (gc, ctx) -> gc.fillRectangle(ctx.clip()));
        show();
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_CYAN));
        viewport.update();
        assertEquals(viewport.getDisplay().getSystemColor(SWT.COLOR_CYAN).getRGB(), rgb(pixels(), 150, 150));
    }

    /* the pixels shown equal those of recomposing every layer at the current origin */
    private void assertSameAsAFullRecomposition() {
        ImageData shown = pixels();
        viewport.redraw();
        viewport.update();
        ImageData full = pixels();
        for (int y = 0; y < shown.height; y++) {
            for (int x = 0; x < shown.width; x++) {
                assertEquals(rgb(full, x, y), rgb(shown, x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    public void testATranslucentPainterLayerIsOneGroup() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer shapes = viewport.addPaintLayer("shapes", 0, 0, 200, 200, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle(10, 10, 40, 40);
            gc.fillRectangle(30, 30, 40, 40);
        });
        shapes.setOpacity(128);
        show();
        ImageData data = pixels();
        RGB single = rgb(data, 15, 15), overlap = rgb(data, 35, 35);
        assertEquals(single, overlap, "the layer's own shapes do not darken each other");
        assertEquals(new RGB(255, 255, 255), rgb(data, 150, 150));
        assertTrue(single.red > 100 && single.red < 160, "half black over white: " + single);
    }
}
