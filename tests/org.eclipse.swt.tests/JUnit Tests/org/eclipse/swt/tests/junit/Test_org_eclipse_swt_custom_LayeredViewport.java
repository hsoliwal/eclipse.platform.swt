/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.custom.LayeredViewport.Layer;
import org.eclipse.swt.custom.LayeredViewport.LayerPointerEvent;
import org.eclipse.swt.custom.LayeredViewport.WorldBounds;
import org.eclipse.swt.custom.LayeredViewport.WorldPoint;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.NativeLayers;
import org.eclipse.swt.internal.render.LayerArgbCompositor;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Event;
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
    public void testFilteredHitSearchUsesFrontmostAcceptedLayerWithoutChangingOrder() {
        Layer bottom = viewport.addPaintLayer("bottom", 0, 0, 100, 100, (gc, ctx) -> { });
        Layer middle = viewport.addPaintLayer("middle", 0, 0, 100, 100, (gc, ctx) -> { });
        Layer top = viewport.addPaintLayer("top", 0, 0, 100, 100, (gc, ctx) -> { });
        List<Layer> visited = new ArrayList<>();
        assertSame(top, viewport.hitTest(10, 10));
        assertSame(middle, viewport.hitTest(10, 10, candidate -> {
            visited.add(candidate);
            return candidate != top;
        }));
        assertEquals(List.of(top, middle), visited, "frontmost first; stop at first accepted");
        assertSame(top, viewport.hitTest(10, 10), "filter must not mutate normal hit priority");
        assertNull(viewport.hitTest(10, 10, candidate -> false));
        assertSame(bottom, viewport.hitTest(10, 10, candidate -> candidate == bottom));
        assertThrows(IllegalArgumentException.class, () -> viewport.hitTest(10, 10, null));

        top.setHitTester((candidate, x, y) -> false);
        visited.clear();
        assertSame(middle, viewport.hitTest(10, 10, candidate -> {
            visited.add(candidate);
            return true;
        }));
        assertEquals(List.of(middle), visited, "non-hittable front layers never reach the predicate");
        assertNull(viewport.hitTest(500, 500, candidate -> {
            fail("outside client area must not invoke accept");
            return true;
        }));
    }

    @Test
    public void testFilteredHitSearchPreservesReentrantCandidateSnapshot() {
        Layer bottom = viewport.addPaintLayer("bottom", 0, 0, 200, 200, (gc, ctx) -> { });
        Layer top = viewport.addPaintLayer("top", 0, 0, 200, 200, (gc, ctx) -> { });
        int[] calls = {0};
        assertSame(bottom, viewport.hitTest(10, 10, candidate -> {
            if (candidate == top) {
                calls[0]++;
                assertSame(top, viewport.hitTest(10, 10), "nested unfiltered query sees same scene");
                top.setVisible(false);
            }
            return true;
        }));
        assertEquals(1, calls[0]);
        assertSame(bottom, viewport.hitTest(10, 10));
    }

    @Test
    public void testGroupChildExclusionSearchAndMutationRevalidation() {
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("diagram", 0, 0, 100, 100);
        LayeredViewport.LayerGroup.Child bottom = group.addPaintChild("shape", 0, 0, 60, 60, (gc, ctx) -> { });
        LayeredViewport.LayerGroup.Child top = group.addPaintChild("dragged", 0, 0, 60, 60, (gc, ctx) -> { });
        assertSame(top, group.hitTestChild(10, 10));
        assertSame(bottom, group.hitTestChild(10, 10, candidate -> candidate != top));
        assertNull(group.hitTestChild(10, 10, candidate -> false));
        assertThrows(IllegalArgumentException.class, () -> group.hitTestChild(10, 10, null));
        assertSame(bottom, group.hitTestChild(10, 10, candidate -> {
            if (candidate == top) top.setLocked(true);
            return true;
        }));
        assertSame(bottom, group.hitTestChild(10, 10));
        top.setLocked(false);
        assertNull(group.hitTestChild(10, 10, candidate -> {
            if (candidate == top) group.getLayer().setWorldBounds(200, 200, 100, 100);
            return true;
        }), "a moved parent must not yield stale hits from its former position");
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
    public void testCachedZPositionAcrossReorderInsertionAndRemoval() {
        List<Layer> expected = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            expected.add(viewport.addPaintLayer("layer " + i,
                    0, 0, 100, 100, (gc, ctx) -> { }));
        }
        java.util.Random random = new java.util.Random(87123L);
        for (int iteration = 0; iteration < 256; iteration++) {
            if (iteration % 17 == 0) {
                Layer cached = expected.get(random.nextInt(expected.size()));
                cached.setRetained(!cached.isRetained());
            }
            int from = random.nextInt(expected.size());
            int to = random.nextInt(expected.size());
            Layer moved = expected.remove(from);
            expected.add(to, moved);
            moved.moveTo(to);
            moved.setWorldBounds(1 + iteration % 2, 0, 100, 100);
            moved.setWorldBounds(0, 0, 100, 100);
            Layer[] ordered = viewport.getLayers();
            for (int i = 0; i < ordered.length; i++) {
                assertSame(expected.get(i), ordered[i]);
                assertEquals(i, ordered[i].getZIndex(), "Z cache after move " + iteration);
            }
            assertSame(expected.getLast(), viewport.hitTest(10, 10));
        }

        Layer appended = viewport.addPaintLayer("after reorders",
                0, 0, 100, 100, (gc, ctx) -> { });
        expected.add(appended);
        assertEquals(expected.size() - 1, appended.getZIndex());
        for (int i = 0; i < 12; i++) {
            int position = random.nextInt(expected.size());
            Layer removed = expected.remove(position);
            removed.remove();
            assertThrows(IllegalArgumentException.class, removed::getZIndex);
            Layer[] ordered = viewport.getLayers();
            assertEquals(expected.size(), ordered.length);
            for (int z = 0; z < ordered.length; z++) {
                assertSame(expected.get(z), ordered[z]);
                assertEquals(z, ordered[z].getZIndex(), "Z cache after removal");
            }
        }
        assertSame(expected.getLast(), viewport.hitTest(10, 10));
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
    public void testFixedHeaderIgnoresPanZoomAndStillWinsHitTesting() {
        Layer rows = viewport.addPaintLayer("rows", -1000, -1000, 3000, 3000, (gc, ctx) -> { });
        Layer header = viewport.addPaintLayer("fixed header", 0, 0, 300, 24, (gc, ctx) -> { });
        header.setFixed(true);
        assertTrue(header.isFixed());
        header.setHitTester((candidate, x, y) -> x < 100 && y < 20);
        assertSame(header, viewport.hitTest(10, 10));
        assertSame(rows, viewport.hitTest(110, 10), "fixed hit tester sees viewport pixels");
        viewport.setOrigin(500, -400);
        viewport.setZoom(2.0);
        assertSame(header, viewport.hitTest(10, 10), "fixed layer is not shifted or scaled");
        assertSame(rows, viewport.hitTest(110, 10));
        assertSame(rows, viewport.hitTest(10, 30));
        header.setWorldClip(new WorldBounds(20, 0, 200, 24));
        assertSame(rows, viewport.hitTest(10, 10));
        assertSame(header, viewport.hitTest(25, 10));
        header.setWorldClip(null);
        header.setFixed(false);
        assertFalse(header.isFixed());
        assertSame(rows, viewport.hitTest(25, 10), "the old fixed hit region is no longer indexed");
    }

    @Test
    public void testTreeHeaderPinsOnlyVerticalAxisAndScrollsWithColumns() {
        Layer rows = viewport.addPaintLayer("rows", -1000, -1000, 3000, 3000, (gc, ctx) -> { });
        Layer header = viewport.addPaintLayer("column headings", 80, 0, 80, 24, (gc, ctx) -> { });
        header.setFixedY(true);
        assertTrue(header.isFixedY());
        assertFalse(header.isFixedX());
        header.setHitTester((candidate, x, y) -> x >= 80 && y < 24);
        assertSame(header, viewport.hitTest(90, 10));
        viewport.setOrigin(40, 500);
        assertSame(header, viewport.hitTest(60, 10), "header follows horizontal column scroll");
        assertSame(rows, viewport.hitTest(130, 10), "header did move horizontally");
        viewport.setZoom(2.0);
        assertSame(header, viewport.hitTest(90, 10), "header column width zooms with the body");
        assertSame(rows, viewport.hitTest(90, 30), "header height remains fixed in widget pixels");
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


    @Test
    public void testPinAndStickyChangesDuringHitRecheckProjectedCoordinates() {
        Layer body = viewport.addPaintLayer("body", 0, 0, 300, 600, (gc, ctx) -> { });
        Layer header = viewport.addPaintLayer("header", 0, 0, 200, 20, (gc, ctx) -> { });
        header.setPinned(SWT.VERTICAL);
        viewport.setOrigin(0, 180);
        assertSame(header, viewport.hitTest(10, 10), "pinned header intercepts its device Y");
        header.setHitTester((candidate, x, y) -> {
            assertEquals(10, y, "the callback receives view-relative Y");
            candidate.setPinned(SWT.NONE);
            return true;
        });
        assertSame(body, viewport.hitTest(10, 10),
                "the header moved to world Y=0, outside the visible world: recheck must reject it");

        Layer group = viewport.addPaintLayer("group", 0, 100, 200, 16, (gc, ctx) -> { });
        group.setSticky(SWT.VERTICAL, 20, 300);
        assertSame(group, viewport.hitTest(10, 25));
        group.setHitTester((candidate, x, y) -> {
            assertEquals(105, y, "stuck group callback receives original local world Y");
            candidate.setSticky(SWT.NONE, 0, 0);
            return true;
        });
        assertSame(body, viewport.hitTest(10, 25),
                "unstuck group no longer occupies the hit point after its callback");
    }

    @Test
    public void testMixedScrolledPinnedAndStickyZOrderAfterReorder() {
        Layer body = viewport.addPaintLayer("body", 0, 0, 300, 600, (gc, ctx) -> { });
        Layer header = viewport.addPaintLayer("header", 0, 0, 100, 20, (gc, ctx) -> { });
        header.setPinned(SWT.VERTICAL);
        Layer sticky = viewport.addPaintLayer("sticky", 0, 100, 100, 16, (gc, ctx) -> { });
        sticky.setSticky(SWT.VERTICAL, 5, 300);
        viewport.setOrigin(0, 180);
        assertSame(sticky, viewport.hitTest(10, 10), "topmost sticky above pinned header");
        sticky.moveTo(0);
        assertSame(header, viewport.hitTest(10, 10), "pinned header above reordered sticky");
        assertSame(body, viewport.hitTest(10, 50), "world-space body remains selectable");
    }

    /* Open the shell and observe the viewport's first native paint. */
    private void show() {
        paintAfter(() -> {
            shell.setSize(400, 340);
            SwtTestUtil.openShell(shell);
        });
    }

    /* GTK may defer update() until a frame tick. Observe the paint before reading pixels,
     * and install the listener before the change so synchronous platforms cannot race it. */
    private void paintAfter(Runnable change) {
        assertTrue(SwtTestUtil.waitEvent(() -> {
            change.run();
            viewport.update();
        }, viewport, SWT.Paint, 1000), "the requested viewport paint must complete");
    }

    private void panAndPaint(long x, long y) {
        if (viewport.getOriginX() == x && viewport.getOriginY() == y) {
            viewport.setOrigin(x, y); // an unchanged origin does not request a paint
        } else {
            paintAfter(() -> viewport.setOrigin(x, y));
        }
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
    public void testDamageRequestedInsidePainterSurvivesCurrentPaint() throws InterruptedException {
        // Regression for removing dirty rectangles AFTER invoking a painter:
        // that ordering erased this new redraw request.
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        int[] paints = { 0 };
        Layer[] layer = { null };
        layer[0] = viewport.addPaintLayer("reentrant", 0, 0, 200, 200,
                (gc, context) -> {
                    paints[0]++;
                    if (paints[0] == 1) layer[0].setOpacity(128);
                    org.eclipse.swt.graphics.Color old = gc.getBackground();
                    try {
                        gc.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_BLACK));
                        gc.fillRectangle(context.clip());
                    } finally {
                        gc.setBackground(old);
                    }
                });
        show();
        viewport.update();
        SwtTestUtil.processEvents(1000, () -> paints[0] >= 2);
        assertTrue(paints[0] >= 2,
                "new damage requested during the first paint must be recomposed");
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
        paintAfter(() -> {
            shell.redraw(0, 0, shell.getSize().x, shell.getSize().y, true);
            shell.update();
        });
        assertEquals(composed, paints[0], "an expose is copied from the backing store");
        paintAfter(viewport::redraw);
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
        panAndPaint(10, 0);
        assertFalse(clips.isEmpty(), "the band is painted");
        for (Rectangle clip : clips) assertTrue(clip.width <= 10, "only the band that came into view: " + clips);
        assertSameAsAFullRecomposition();
        // back the other way: the band is on the left
        clips.clear();
        panAndPaint(0, 0);
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
        paintAfter(() -> {
            marker.setWorldBounds(60, 0, 10, 50); // its old and new pixels are dirty, not yet repainted
            viewport.setOrigin(10, 0); // and they move with the retained pixels
        });
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
        paintAfter(() -> marker.setWorldBounds(100, 100, 10, 10));
        assertEquals(0, mapPaints[0], "the map under the moved marker comes from its retained composite");
        paintAfter(() -> {
            viewport.beginUpdate();
            marker.setWorldBounds(150, 60, 10, 10);
            viewport.endUpdate();
        });
        assertEquals(0, mapPaints[0], "batched changes keep that precision");
        assertSameAsAFullRecomposition();
        // the retained layer's own content changes: its composite is recomposed
        mapColor[0] = SWT.COLOR_MAGENTA;
        paintAfter(map::redraw);
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
        paintAfter(below::redraw);
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
        panAndPaint(-12, 0);
        assertFalse(clips.isEmpty());
        for (Rectangle clip : clips) assertTrue(clip.width <= 12, "only the band: " + clips);
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testPinnedHeaderDoesNotLeaveGhostPixelsAfterScroll() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.addPaintLayer("scrolling body", -2000, -2000, 4000, 4000, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(ctx.clip());
        });
        Layer header = viewport.addPaintLayer("sticky header", 0, 0, 300, 24, (gc, ctx) -> {
            assertEquals(viewport.getOriginX(), ctx.originX());
            assertEquals(0, ctx.originY());
            assertEquals(viewport.getZoom(), ctx.zoom());
            assertEquals(12, ctx.projectY(12));
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
        });
        header.setFixedY(true);
        show();
        RGB red = viewport.getDisplay().getSystemColor(SWT.COLOR_RED).getRGB();
        RGB blue = viewport.getDisplay().getSystemColor(SWT.COLOR_BLUE).getRGB();
        assertEquals(red, rgb(pixels(), 10, 10));
        assertEquals(blue, rgb(pixels(), 10, 35));
        viewport.setOrigin(0, -18); // copies old header pixels down toward the body
        viewport.update();
        assertEquals(red, rgb(pixels(), 10, 10), "header stays pinned");
        assertEquals(blue, rgb(pixels(), 10, 35), "the shifted header's old pixels are repaired");
        assertSameAsAFullRecomposition();
        viewport.setZoom(2.0);
        viewport.update();
        assertEquals(red, rgb(pixels(), 10, 10));
        assertEquals(blue, rgb(pixels(), 10, 35), "zoom does not scale fixed chrome");
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testFrozenColumnDoesNotLeaveGhostPixelsAfterHorizontalScroll() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.addPaintLayer("scrolling rows", -2000, -2000, 4000, 4000, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(ctx.clip());
        });
        Layer frozen = viewport.addPaintLayer("row labels", 0, 0, 24, 240, (gc, ctx) -> {
            assertEquals(0, ctx.originX());
            assertEquals(12, ctx.projectX(12));
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
        });
        frozen.setFixedX(true);
        show();
        RGB red = viewport.getDisplay().getSystemColor(SWT.COLOR_RED).getRGB();
        RGB blue = viewport.getDisplay().getSystemColor(SWT.COLOR_BLUE).getRGB();
        assertEquals(red, rgb(pixels(), 10, 10));
        assertEquals(blue, rgb(pixels(), 35, 10));
        viewport.setOrigin(-18, 0);
        viewport.update();
        assertEquals(red, rgb(pixels(), 10, 10));
        assertEquals(blue, rgb(pixels(), 35, 10), "old frozen pixels are repaired");
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testABackgroundChangeAfterTheFirstPaintIsShown() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.addPaintLayer("dot", 10, 10, 5, 5, (gc, ctx) -> gc.fillRectangle(ctx.clip()));
        show();
        paintAfter(() -> viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_CYAN)));
        assertEquals(viewport.getDisplay().getSystemColor(SWT.COLOR_CYAN).getRGB(), rgb(pixels(), 150, 150));
    }

    @Test
    public void testAControlLayerFollowsPanZoomAndItsLayer() {
        org.eclipse.swt.widgets.Button button = new org.eclipse.swt.widgets.Button(viewport, SWT.PUSH);
        Layer layer = viewport.addControlLayer("button", button, 20, 30, 80, 24);
        assertSame(button, viewport.getControl(layer));
        assertEquals(new Rectangle(20, 30, 80, 24), button.getBounds());
        viewport.setOrigin(10, 5);
        assertEquals(new Rectangle(10, 25, 80, 24), button.getBounds(), "a pan moves the control");
        viewport.setZoom(2.0);
        assertEquals(new Rectangle(20, 50, 160, 48), button.getBounds(), "a zoom scales it");
        layer.setWorldBounds(40, 30, 10, 10);
        assertEquals(new Rectangle(60, 50, 20, 20), button.getBounds(), "the layer's bounds place it");
        assertTrue(button.getVisible());
        layer.setVisible(false);
        assertFalse(button.getVisible(), "hidden with its layer");
        layer.setVisible(true);
        assertTrue(button.getVisible());
        viewport.setOrigin(100000, 0);
        assertFalse(button.getVisible(), "hidden outside the view");
        viewport.setOrigin(10, 5);
        assertTrue(button.getVisible());
        layer.setOpacity(0);
        assertFalse(button.getVisible(), "hidden at opacity 0");
        layer.setOpacity(255);
        assertSame(layer, viewport.hitTest(65, 55), "a control layer is hit like any layer");
        layer.remove();
        assertFalse(button.isDisposed(), "the control stays the application's");
        viewport.setOrigin(0, 0);
        assertEquals(new Rectangle(60, 50, 20, 20), button.getBounds(), "a removed layer no longer moves its control");
        org.eclipse.swt.widgets.Button stranger = new org.eclipse.swt.widgets.Button(shell, SWT.PUSH);
        assertThrows(IllegalArgumentException.class, () -> viewport.addControlLayer("x", stranger, 0, 0, 5, 5));
    }

    @Test
    public void testPinnedNativeTreeHeaderIsAboveScrollingControl() {
        Button body = new Button(viewport, SWT.PUSH);
        Button header = new Button(viewport, SWT.PUSH);
        viewport.addControlLayer("scrolling body", body, 100, 100, 150, 100);
        Layer chrome = viewport.addControlLayer("pinned header", header, 50, 0, 300, 24);
        chrome.setFixedY(true);
        assertSame(header, viewport.getChildren()[0], "native header is above native body");
        viewport.setOrigin(50, 100);
        assertEquals(new Rectangle(50, 0, 150, 100), body.getBounds());
        assertEquals(new Rectangle(0, 0, 300, 24), header.getBounds());
        viewport.setZoom(2.0);
        assertEquals(new Rectangle(100, 0, 300, 200), body.getBounds());
        assertEquals(new Rectangle(0, 0, 600, 24), header.getBounds());
        chrome.setWorldBounds(50, 0, 300, 32);
        assertEquals(new Rectangle(0, 0, 600, 32), header.getBounds());
        assertSame(chrome, viewport.hitTest(10, 10));
    }

    @Test
    public void testControlLayersStackInLayerOrder() {
        org.eclipse.swt.widgets.Button a = new org.eclipse.swt.widgets.Button(viewport, SWT.PUSH);
        org.eclipse.swt.widgets.Button b = new org.eclipse.swt.widgets.Button(viewport, SWT.PUSH);
        Layer lower = viewport.addControlLayer("a", a, 10, 10, 50, 30);
        viewport.addControlLayer("b", b, 30, 20, 50, 30);
        assertSame(b, viewport.getChildren()[0], "the upper layer's control is on top (children list the topmost first)");
        lower.moveTo(1);
        assertSame(a, viewport.getChildren()[0], "moving the layer up moves its control up");
    }

    /* an image of one colour */
    private Image solidImage(int color, int size) {
        Image image = new Image(viewport.getDisplay(), size, size);
        GC gc = new GC(image);
        gc.setBackground(viewport.getDisplay().getSystemColor(color));
        gc.fillRectangle(0, 0, size, size);
        gc.dispose();
        return image;
    }

    @Test
    public void testBlendModesOfAnImageLayer() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.addPaintLayer("blue", 0, 0, 120, 120, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(ctx.clip());
        });
        Image red = solidImage(SWT.COLOR_RED, 40), white = solidImage(SWT.COLOR_WHITE, 40);
        try {
            Layer layer = viewport.addImageLayer("red", red, 20, 20);
            assertEquals(LayeredViewport.BLEND_NORMAL, layer.getBlendMode());
            layer.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
            show();
            assertEquals(new RGB(0, 0, 0), rgb(pixels(), 30, 30), "red multiplied with blue");
            assertEquals(new RGB(0, 0, 255), rgb(pixels(), 100, 100), "outside the image the blue is unchanged");
            assertEquals(new RGB(255, 255, 255), rgb(pixels(), 200, 200));
            paintAfter(() -> layer.setBlendMode(LayeredViewport.BLEND_SCREEN));
            assertEquals(new RGB(255, 0, 255), rgb(pixels(), 30, 30), "red screened over blue");
            paintAfter(() -> {
                layer.setImage(white);
                layer.setBlendMode(LayeredViewport.BLEND_DIFFERENCE);
            });
            assertEquals(new RGB(255, 255, 0), rgb(pixels(), 30, 30), "white minus blue");
            paintAfter(() -> {
                layer.setImage(red);
                layer.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
                layer.setOpacity(128);
            });
            assertEquals(new RGB(0, 0, 127), rgb(pixels(), 30, 30), "multiply at half opacity");
            assertThrows(IllegalArgumentException.class, () -> layer.setBlendMode(LayeredViewport.BLEND_EXCLUSION + 1));
            assertThrows(IllegalArgumentException.class, () -> layer.setBlendMode(-1));
            assertSameAsAFullRecomposition();
        } finally {
            red.dispose();
            white.dispose();
        }
    }

    @Test
    public void testMaskCoverageModesAndPremultipliedCompositor() {
        assertEquals(255, LayerArgbCompositor.maskCoverage(255, 0xFFFFFF, LayeredViewport.MASK_ALPHA));
        assertEquals(0, LayerArgbCompositor.maskCoverage(255, 0xFFFFFF, LayeredViewport.MASK_INVERTED_ALPHA));
        assertEquals(0, LayerArgbCompositor.maskCoverage(0, 0xFFFFFF, LayeredViewport.MASK_LUMINANCE));
        assertEquals(0, LayerArgbCompositor.maskCoverage(0, 0xFFFFFF, LayeredViewport.MASK_INVERTED_LUMINANCE));
        assertEquals(255, LayerArgbCompositor.maskCoverage(255, 0, LayeredViewport.MASK_INVERTED_LUMINANCE));
        assertEquals(128, LayerArgbCompositor.maskCoverage(255, 0x808080, LayeredViewport.MASK_LUMINANCE));
        assertEquals(127, LayerArgbCompositor.maskCoverage(255, 0x808080, LayeredViewport.MASK_INVERTED_LUMINANCE));
        for (int alpha = 0; alpha <= 255; alpha++) {
            for (int grey = 0; grey <= 255; grey++) {
                int rgb = grey * 0x010101;
                int luminance = LayerArgbCompositor.maskCoverage(alpha, rgb, LayeredViewport.MASK_LUMINANCE);
                int inverse = LayerArgbCompositor.maskCoverage(alpha, rgb, LayeredViewport.MASK_INVERTED_LUMINANCE);
                assertEquals(alpha, luminance + inverse, "luminance mask partitions this alpha");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> LayerArgbCompositor.maskCoverage(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LayerArgbCompositor.maskCoverage(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LayerArgbCompositor.maskCoverage(255, 0, 4));
        int[] dst = { 0xFFFFFFFF }, src = { 0xFFFF0000 };
        byte[] half = { (byte) 128 };
        LayerArgbCompositor.compositeMasked(dst, 0, src, 0, half, 0, 1, 255,
                LayerArgbCompositor.Mode.SRC_OVER);
        assertEquals(0xFFFF7F7F, dst[0], "masked source-over red on opaque white");
    }

    @Test
    public void testRasterLayerMaskAlphaLuminanceAndBorrowedOwnership() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
        ImageData alpha = new ImageData(4, 1, 24, palette);
        for (int x = 0; x < 4; x++) alpha.setPixel(x, 0, 0xFFFFFF);
        alpha.setAlpha(0, 0, 255);
        alpha.setAlpha(1, 0, 128);
        alpha.setAlpha(2, 0, 0);
        alpha.setAlpha(3, 0, 255);
        ImageData grey = new ImageData(2, 1, 24, palette);
        grey.setPixel(0, 0, 0);
        grey.setPixel(1, 0, 0xFFFFFF);
        Image foregroundImage = solidImage(SWT.COLOR_RED, 40);
        Image alphaMask = new Image(viewport.getDisplay(), alpha);
        Image greyMask = new Image(viewport.getDisplay(), grey);
        Layer foreground = viewport.addImageLayer("masked", foregroundImage, 20, 20);
        try {
            assertEquals(LayeredViewport.MASK_ALPHA, foreground.getMaskMode());
            foreground.setMask(alphaMask);
            assertSame(alphaMask, foreground.getMask());
            assertThrows(IllegalArgumentException.class, () -> foreground.setMaskMode(-1));
            assertThrows(IllegalArgumentException.class, () -> foreground.setMaskMode(4));
            Layer painter = viewport.addPaintLayer("unmasked painter", 100, 100, 20, 20, (gc, ctx) -> { });
            assertThrows(IllegalArgumentException.class, () -> painter.setMask(alphaMask));
            painter.remove();
            show();
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 25, 30));
            RGB half = rgb(pixels(), 35, 30);
            assertEquals(255, half.red);
            assertTrue(Math.abs(half.green - 127) <= 4 && Math.abs(half.blue - 127) <= 4);
            assertEquals(system(SWT.COLOR_WHITE), rgb(pixels(), 45, 30));
            paintAfter(() -> foreground.setMaskMode(LayeredViewport.MASK_INVERTED_ALPHA));
            assertEquals(system(SWT.COLOR_WHITE), rgb(pixels(), 25, 30));
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 45, 30));
            paintAfter(() -> {
                foreground.setMask(greyMask);
                foreground.setMaskMode(LayeredViewport.MASK_LUMINANCE);
            });
            assertEquals(system(SWT.COLOR_WHITE), rgb(pixels(), 25, 30));
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 55, 30));
            paintAfter(() -> foreground.setMaskMode(LayeredViewport.MASK_INVERTED_LUMINANCE));
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 25, 30));
            assertEquals(system(SWT.COLOR_WHITE), rgb(pixels(), 55, 30));
            assertSameAsAFullRecomposition();
            paintAfter(() -> foreground.setMask(null));
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 25, 30));
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 55, 30));
            assertFalse(alphaMask.isDisposed(), "the caller owns the alpha mask");
            assertFalse(greyMask.isDisposed(), "the caller owns the luminance mask");
        } finally {
            if (!viewport.isDisposed()) foreground.remove();
            foregroundImage.dispose();
            alphaMask.dispose();
            greyMask.dispose();
        }
    }

    @Test
    public void testPaintedImageHitTestingDefaultsToGeometryAndOptionallyUsesSourceAndMask() {
        Layer bottom = viewport.addPaintLayer("behind image", 0, 0, 200, 200, (gc, ctx) -> { });
        PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
        ImageData imageData = new ImageData(3, 1, 24, palette);
        for (int x = 0; x < 3; x++) imageData.setPixel(x, 0, 0xFF0000);
        imageData.setAlpha(0, 0, 255);
        imageData.setAlpha(1, 0, 0);
        imageData.setAlpha(2, 0, 255);
        ImageData alphaCoverage = new ImageData(3, 1, 24, palette);
        for (int x = 0; x < 3; x++) alphaCoverage.setPixel(x, 0, 0xFFFFFF);
        alphaCoverage.setAlpha(0, 0, 255);
        alphaCoverage.setAlpha(1, 0, 255);
        alphaCoverage.setAlpha(2, 0, 0);
        ImageData luminanceCoverage = new ImageData(3, 1, 24, palette);
        luminanceCoverage.setPixel(0, 0, 0x000000);
        luminanceCoverage.setPixel(1, 0, 0x808080);
        luminanceCoverage.setPixel(2, 0, 0xFFFFFF);
        for (int x = 0; x < 3; x++) luminanceCoverage.setAlpha(x, 0, 255);
        Image image = new Image(viewport.getDisplay(), imageData);
        Image alphaMask = new Image(viewport.getDisplay(), alphaCoverage);
        Image luminanceMask = new Image(viewport.getDisplay(), luminanceCoverage);
        Layer top = viewport.addImageLayer("painted hit", image, 20, 20);
        top.setWorldBounds(20, 20, 60, 30);
        try {
            top.setMask(alphaMask);
            assertFalse(top.isPaintedHitTesting(), "geometric hits are the historical default");
            assertSame(top, viewport.hitTest(25, 25));
            assertSame(top, viewport.hitTest(45, 25), "even transparent source pixels formerly hit");
            assertSame(top, viewport.hitTest(75, 25), "even transparent masked pixels formerly hit");
            top.setPaintedHitTesting(true);
            assertTrue(top.isPaintedHitTesting());
            assertSame(top, viewport.hitTest(25, 25));
            assertSame(bottom, viewport.hitTest(45, 25), "zero source alpha passes through");
            assertSame(bottom, viewport.hitTest(75, 25), "zero mask alpha passes through");
            top.setMaskMode(LayeredViewport.MASK_INVERTED_ALPHA);
            assertSame(bottom, viewport.hitTest(25, 25), "inverse alpha rejects the opaque mask side");
            assertSame(top, viewport.hitTest(75, 25), "inverse alpha accepts transparent mask side");
            top.setMask(luminanceMask);
            top.setMaskMode(LayeredViewport.MASK_LUMINANCE);
            assertSame(bottom, viewport.hitTest(25, 25), "black luminance rejects");
            assertSame(top, viewport.hitTest(75, 25), "white luminance accepts");
            top.setMaskMode(LayeredViewport.MASK_INVERTED_LUMINANCE);
            assertSame(top, viewport.hitTest(25, 25), "inverted black luminance accepts");
            assertSame(bottom, viewport.hitTest(75, 25), "inverted white luminance rejects");
            top.setMask(null);
            assertSame(bottom, viewport.hitTest(45, 25), "source alpha applies even without a mask");
            assertSame(top, viewport.hitTest(75, 25));
            top.setPaintedHitTesting(false);
            assertSame(top, viewport.hitTest(45, 25), "opt-out restores geometric interception");
            assertFalse(image.isDisposed(), "the caller owns the borrowed source image");
            assertFalse(alphaMask.isDisposed(), "the caller owns the borrowed alpha mask");
            assertFalse(luminanceMask.isDisposed(), "the caller owns the borrowed luminance mask");
        } finally {
            if (!viewport.isDisposed()) { top.remove(); bottom.remove(); }
            image.dispose();
            alphaMask.dispose();
            luminanceMask.dispose();
        }
    }

    @Test
    public void testPaintedHitTesterAndFilterRecheckMaskAfterCallbacks() {
        Layer bottom = viewport.addPaintLayer("under", 0, 0, 200, 200, (gc, ctx) -> { });
        Image topImage = solidImage(SWT.COLOR_RED, 40);
        PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
        ImageData data = new ImageData(2, 1, 24, palette);
        for (int x = 0; x < 2; x++) data.setPixel(x, 0, 0xFFFFFF);
        data.setAlpha(0, 0, 255);
        data.setAlpha(1, 0, 0);
        Image mask = new Image(viewport.getDisplay(), data);
        Layer top = viewport.addImageLayer("top", topImage, 20, 20);
        try {
            top.setMask(mask);
            top.setMaskMode(LayeredViewport.MASK_INVERTED_ALPHA);
            top.setPaintedHitTesting(true);
            assertSame(top, viewport.hitTest(50, 25), "right mask half is painted in inverse mode");
            top.setHitTester((layer, worldX, worldY) -> {
                top.setMaskMode(LayeredViewport.MASK_ALPHA);
                return true;
            });
            assertSame(bottom, viewport.hitTest(50, 25),
                    "a hit tester that hides its pixel must not return a stale layer");
            top.setHitTester(null);
            top.setMaskMode(LayeredViewport.MASK_INVERTED_ALPHA);
            assertSame(bottom, viewport.hitTest(50, 25, candidate -> {
                if (candidate == top) top.setMaskMode(LayeredViewport.MASK_ALPHA);
                return true;
            }), "an exclusion predicate that changes coverage must also be rechecked");
            top.setPaintedHitTesting(false);
            assertSame(top, viewport.hitTest(50, 25), "old geometric behavior is restored");
        } finally {
            if (!viewport.isDisposed()) { top.remove(); bottom.remove(); }
            topImage.dispose();
            mask.dispose();
        }
    }

    @Test
    public void testMaskedBlendRecomposesAcrossBoundedRasterBands() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Image blue = solidImage(SWT.COLOR_BLUE, 280), red = solidImage(SWT.COLOR_RED, 280);
        PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
        ImageData coverage = new ImageData(3, 1, 24, palette);
        for (int x = 0; x < 3; x++) coverage.setPixel(x, 0, 0xFFFFFF);
        coverage.setAlpha(0, 0, 0);
        coverage.setAlpha(1, 0, 128);
        coverage.setAlpha(2, 0, 255);
        Image mask = new Image(viewport.getDisplay(), coverage);
        Layer ground = viewport.addImageLayer("blue below", blue, 0, 0);
        Layer top = viewport.addImageLayer("masked red", red, 0, 0);
        try {
            top.setMask(mask);
            top.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
            top.setRetained(true);
            show();
            // A 280-wide dirty tile splits after each 58 rows (16,384/280).
            for (int y : new int[] { 0, 57, 58, 115, 116, 173, 174, 231 }) {
                ImageData data = pixels();
                assertEquals(new RGB(0, 0, 255), rgb(data, 20, y),
                        "uncovered blue near tile band y=" + y);
                RGB half = rgb(data, 140, y);
                assertEquals(0, half.red);
                assertEquals(0, half.green);
                assertTrue(Math.abs(half.blue - 127) <= 3,
                        "half coverage near tile band y=" + y + ": " + half);
                assertEquals(new RGB(0, 0, 0), rgb(data, 260, y),
                        "red multiplied with blue near tile band y=" + y);
            }
            assertSameAsAFullRecomposition();
            paintAfter(() -> top.setMaskMode(LayeredViewport.MASK_INVERTED_ALPHA));
            ImageData inverse = pixels();
            assertEquals(new RGB(0, 0, 0), rgb(inverse, 20, 173));
            assertEquals(new RGB(0, 0, 255), rgb(inverse, 260, 173));
            assertSameAsAFullRecomposition();
            paintAfter(() -> top.setBlendMode(LayeredViewport.BLEND_SCREEN));
            ImageData screen = pixels();
            assertEquals(new RGB(255, 0, 255), rgb(screen, 20, 174));
            assertEquals(new RGB(0, 0, 255), rgb(screen, 260, 174));
            panAndPaint(10, 2);
            assertSameAsAFullRecomposition();
            assertFalse(mask.isDisposed(), "SWT does not own a borrowed raster mask");
        } finally {
            if (!viewport.isDisposed()) {
                top.remove();
                ground.remove();
            }
            blue.dispose();
            red.dispose();
            mask.dispose();
        }
    }

    @Test
    public void testLargeBlendedTileCrossesNativeBatchBoundaryWithoutSeams() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Image red = solidImage(SWT.COLOR_RED, 180), blue = solidImage(SWT.COLOR_BLUE, 180);
        try {
            viewport.addImageLayer("base", blue, 0, 0);
            Layer upper = viewport.addImageLayer("blend", red, 0, 0);
            upper.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
            show();
            ImageData shown = pixels();
            // 180 * 180 pixels: the bounded 16K JNI batch is crossed at row 91.
            for (int y : new int[] { 0, 89, 90, 91, 92, 179 }) {
                assertEquals(new RGB(0, 0, 0), rgb(shown, 20, y), "multiply at row " + y);
            }
            assertEquals(new RGB(255, 255, 255), rgb(shown, 200, 200));
            paintAfter(() -> upper.setBlendMode(LayeredViewport.BLEND_SCREEN));
            shown = pixels();
            for (int y : new int[] { 0, 90, 91, 179 }) {
                assertEquals(new RGB(255, 0, 255), rgb(shown, 170, y), "screen at row " + y);
            }
            assertSameAsAFullRecomposition();
        } finally {
            red.dispose();
            blue.dispose();
        }
    }

    /* the pixels shown equal those of recomposing every layer at the current origin */
    private void assertSameAsAFullRecomposition() {
        ImageData shown = pixels();
        paintAfter(viewport::redraw);
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

    private void pointer(int type, int x, int y, int button) {
        Event event = new Event();
        event.x = x;
        event.y = y;
        event.button = button;
        event.count = 1;
        event.time = 1234;
        event.stateMask = SWT.SHIFT;
        viewport.notifyListeners(type, event);
    }

    @Test
    public void testPointerSurfaceIgnoresDecorationAndRetainsCanvasEvents() {
        Layer interactive = viewport.addPaintLayer("cell", 0, 0, 100, 100, (gc, ctx) -> { });
        Layer decoration = viewport.addPaintLayer("decoration", 0, 0, 100, 100, (gc, ctx) -> { });
        List<LayerPointerEvent> delivered = new ArrayList<>();
        List<Integer> nativeEvents = new ArrayList<>();
        interactive.setPointerListener(delivered::add);
        viewport.addListener(SWT.MouseDown, event -> nativeEvents.add(event.type));
        viewport.addListener(SWT.MouseMove, event -> nativeEvents.add(event.type));
        viewport.addListener(SWT.MouseUp, event -> nativeEvents.add(event.type));

        assertSame(decoration, viewport.hitTest(10, 10),
                "public picking still includes noninteractive decorations");
        pointer(SWT.MouseDown, 10, 10, 1);
        assertSame(interactive, viewport.getPointerCapture());
        assertEquals(1, delivered.size());
        assertSame(interactive, delivered.get(0).layer());
        assertEquals(SWT.MouseDown, delivered.get(0).type());
        assertEquals(10, delivered.get(0).worldX());
        assertFalse(delivered.get(0).captured(), "first down begins capture");
        assertEquals(SWT.SHIFT, delivered.get(0).stateMask());

        pointer(SWT.MouseMove, 290, 230, 0);
        assertEquals(2, delivered.size(), "drag keeps the original layer outside its bounds");
        assertTrue(delivered.get(1).captured());
        assertEquals(290, delivered.get(1).worldX());
        pointer(SWT.MouseUp, 290, 230, 1);
        assertEquals(3, delivered.size());
        assertNull(viewport.getPointerCapture());
        pointer(SWT.MouseMove, 290, 230, 0);
        assertEquals(3, delivered.size(), "the layer no longer receives out-of-bounds moves");
        assertEquals(List.of(SWT.MouseDown, SWT.MouseMove, SWT.MouseUp, SWT.MouseMove), nativeEvents,
                "normal SWT Canvas listeners must still observe every event");
        pointer(SWT.MouseHover, 10, 10, 0);
        assertEquals(SWT.MouseHover, delivered.get(3).type());
    }

    @Test
    public void testPointerStateAndFineHitTestRetainBackLayerRouting() {
        Layer back = viewport.addPaintLayer("back", 0, 0, 100, 100, (gc, ctx) -> { });
        Layer front = viewport.addPaintLayer("front", 0, 0, 100, 100, (gc, ctx) -> { });
        List<LayerPointerEvent> delivered = new ArrayList<>();
        back.setPointerListener(delivered::add);
        front.setPointerListener(delivered::add);

        pointer(SWT.MouseMove, 5, 5, 0);
        assertSame(front, delivered.get(0).layer());
        front.setHitTester((layer, x, y) -> false);
        pointer(SWT.MouseMove, 5, 5, 0);
        assertSame(back, delivered.get(1).layer());
        front.setHitTester(null);
        front.setLocked(true);
        pointer(SWT.MouseMove, 5, 5, 0);
        assertSame(back, delivered.get(2).layer());
        front.setLocked(false);
        front.setOpacity(0);
        pointer(SWT.MouseMove, 5, 5, 0);
        assertSame(back, delivered.get(3).layer());
        front.setOpacity(255);
        front.setVisible(false);
        pointer(SWT.MouseMove, 5, 5, 0);
        assertSame(back, delivered.get(4).layer());
        front.setVisible(true);
        front.setPointerListener(null);
        pointer(SWT.MouseMove, 5, 5, 0);
        assertSame(back, delivered.get(5).layer());
    }

    @Test
    public void testPointerCaptureMultiButtonCancellationAndRemoval() {
        Layer layer = viewport.addPaintLayer("handle", 0, 0, 100, 100, (gc, ctx) -> { });
        List<LayerPointerEvent> delivered = new ArrayList<>();
        layer.setPointerListener(delivered::add);
        pointer(SWT.MouseDown, 10, 10, 1);
        pointer(SWT.MouseDown, 10, 10, 3);
        pointer(SWT.MouseUp, 10, 10, 1);
        assertSame(layer, viewport.getPointerCapture(), "second button still holds capture");
        pointer(SWT.MouseUp, 10, 10, 3);
        assertNull(viewport.getPointerCapture());

        pointer(SWT.MouseDown, 10, 10, 1);
        layer.setVisible(false);
        assertNull(viewport.getPointerCapture(), "hidden target releases capture");
        layer.setVisible(true);
        pointer(SWT.MouseDown, 10, 10, 1);
        viewport.notifyListeners(SWT.MouseExit, new Event());
        assertNull(viewport.getPointerCapture(), "leaving the Canvas releases logical capture");
        pointer(SWT.MouseDown, 10, 10, 1);
        viewport.releasePointerCapture();
        assertNull(viewport.getPointerCapture());
        pointer(SWT.MouseDown, 10, 10, 1);
        viewport.notifyListeners(SWT.FocusOut, new Event());
        assertNull(viewport.getPointerCapture());
        pointer(SWT.MouseDown, 10, 10, 1);
        layer.remove();
        assertNull(viewport.getPointerCapture(), "removed handle cannot hold input");
        pointer(SWT.MouseMove, 10, 10, 0);
        assertEquals(9, delivered.size(), "no callback to removed or unregistered surfaces");
    }

    @Test
    public void testPointerWorldCoordsAndReentrantHandlerMutation() {
        long large = 1L << 55;
        viewport.setOrigin(large, large);
        viewport.setZoom(2);
        Layer back = viewport.addPaintLayer("back", large, large, 80, 80, (gc, ctx) -> { });
        Layer front = viewport.addPaintLayer("front", large, large, 80, 80, (gc, ctx) -> { });
        List<LayerPointerEvent> backEvents = new ArrayList<>();
        back.setPointerListener(backEvents::add);
        front.setPointerListener(event -> {
            if (event.type() == SWT.MouseDown) {
                assertEquals(large + 10, event.worldX());
                assertEquals(large + 15, event.worldY());
                assertSame(front, viewport.getPointerCapture());
                front.remove();
                pointer(SWT.MouseMove, 20, 30, 0);
            }
        });
        pointer(SWT.MouseDown, 20, 30, 1);
        assertNull(viewport.getPointerCapture());
        assertEquals(1, backEvents.size());
        assertSame(back, backEvents.get(0).layer());
        assertEquals(large + 10, backEvents.get(0).worldX());
    }

    @Test
    public void testHitCallbackCanRemoveManyLayersAndAppendDuringNestedQuery() {
        Layer bottom = viewport.addPaintLayer("bottom", 0, 0, 40, 40, (gc, ctx) -> { });
        List<Layer> removed = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            removed.add(viewport.addPaintLayer("middle " + i, 0, 0, 40, 40, (gc, ctx) -> { }));
        }
        Layer top = viewport.addPaintLayer("top", 0, 0, 40, 40, (gc, ctx) -> { });
        Layer[] appended = new Layer[1];
        top.setHitTester((candidate, x, y) -> {
            for (Layer layer : removed) layer.remove();
            candidate.remove();
            appended[0] = viewport.addPaintLayer("new", 0, 0, 40, 40, (gc, ctx) -> { });
            assertSame(appended[0], viewport.hitTest(5, 5),
                    "a nested query observes the newly published scene");
            return true;
        });

        assertSame(bottom, assertDoesNotThrow(() -> viewport.hitTest(5, 5)),
                "the outer query skips removed handles and retains its original candidate snapshot");
        assertArrayEquals(new Layer[] {bottom, appended[0]}, viewport.getLayers());
        assertSame(appended[0], viewport.hitTest(5, 5));
        assertThrows(IllegalArgumentException.class, top::getId);
    }

    @Test
    public void testHitCallbacksRevalidateTheirOwnAndLaterCandidateEligibility() {
        Layer bottom = viewport.addPaintLayer("bottom", 0, 0, 200, 200, (gc, ctx) -> { });
        List<Consumer<Layer>> changes = List.of(
                layer -> layer.setVisible(false),
                layer -> layer.setLocked(true),
                layer -> layer.setOpacity(0),
                layer -> layer.setWorldBounds(100, 100, 20, 20),
                layer -> layer.setWorldClip(new WorldBounds(100, 100, 20, 20)));
        for (Consumer<Layer> change : changes) {
            Layer top = viewport.addPaintLayer("changing", 0, 0, 40, 40, (gc, ctx) -> { });
            top.setHitTester((candidate, x, y) -> {
                change.accept(candidate);
                return true;
            });
            assertSame(bottom, viewport.hitTest(5, 5),
                    "a true callback result cannot return a layer made ineligible during that callback");
            top.remove();
        }

        Layer middle = viewport.addPaintLayer("later candidate", 0, 0, 40, 40, (gc, ctx) -> { });
        middle.setHitTester((candidate, x, y) -> {
            fail("a layer moved out of bounds by an earlier callback must not be called");
            return true;
        });
        Layer top = viewport.addPaintLayer("moves another candidate", 0, 0, 40, 40, (gc, ctx) -> { });
        top.setHitTester((candidate, x, y) -> {
            middle.setWorldBounds(100, 100, 20, 20);
            return false;
        });
        assertSame(bottom, viewport.hitTest(5, 5));
    }

    @Test
    public void testHitCallbackMayDisposeViewport() {
        viewport.addPaintLayer("bottom", 0, 0, 40, 40, (gc, ctx) -> { });
        Layer top = viewport.addPaintLayer("disposes", 0, 0, 40, 40, (gc, ctx) -> { });
        top.setHitTester((candidate, x, y) -> {
            disposedIntentionally = true;
            viewport.dispose();
            return true;
        });
        assertNull(assertDoesNotThrow(() -> viewport.hitTest(5, 5)));
        assertTrue(viewport.isDisposed());
    }

    @Test
    public void testSparseReadsAcrossBulkAppendKeepStableDefensiveOrder() throws Exception {
        List<Layer> expected = new ArrayList<>();
        for (int batch = 0; batch < 8; batch++) {
            Layer[] beforeAppend = cachedOrder();
            viewport.beginUpdate();
            try {
                for (int i = 0; i < 256; i++) {
                    Layer layer = viewport.addPaintLayer("layer " + expected.size(),
                            0, 0, 10, 10, (gc, ctx) -> { });
                    expected.add(layer);
                    if (batch == 0 && i == 0) layer.setRetained(true);
                }
            } finally {
                viewport.endUpdate();
            }
            assertSame(beforeAppend, cachedOrder(),
                    "ordinary painter appends must not rebuild the order snapshot, even with a retained layer present");
            assertArrayEquals(expected.toArray(new Layer[0]), viewport.getLayers());
            assertSame(expected.get(expected.size() - 1), viewport.hitTest(3, 4));
        }
        Layer[] earlier = viewport.getLayers();
        Layer earlierLast = earlier[earlier.length - 1];
        for (int i = 0; i < 128; i++) {
            expected.add(viewport.addPaintLayer("tail " + i, 0, 0, 10, 10, (gc, ctx) -> { }));
        }
        assertEquals(2048, earlier.length);
        assertSame(earlierLast, earlier[earlier.length - 1], "an earlier structural snapshot stays stable");
        Layer first = expected.remove(0);
        first.moveTo(expected.size());
        expected.add(first);
        expected.remove(17).remove();
        Layer[] current = viewport.getLayers();
        assertArrayEquals(expected.toArray(new Layer[0]), current);
        current[0] = null;
        assertSame(expected.get(0), viewport.getLayers()[0]);
        assertSame(first, viewport.hitTest(3, 4));
    }

    private Layer[] cachedOrder() throws ReflectiveOperationException {
        Field field = LayeredViewport.class.getDeclaredField("paintOrder");
        field.setAccessible(true);
        return (Layer[]) field.get(viewport);
    }

    @Test
    public void testPaintMutationKeepsFrameOrderAndSchedulesTheNextFrame() {
        viewport.setSize(64, 64);
        List<String> calls = new ArrayList<>();
        Layer[] peers = new Layer[2];
        boolean[] changed = {false};
        viewport.addPaintLayer("A", 0, 0, 64, 64, (gc, ctx) -> {
            calls.add("A");
            if (!changed[0]) {
                changed[0] = true;
                peers[0].remove();
                peers[1].moveTo(0);
                viewport.addPaintLayer("D", 0, 0, 64, 64, (next, context) -> calls.add("D"));
            }
        });
        peers[0] = viewport.addPaintLayer("B", 0, 0, 64, 64, (gc, ctx) -> calls.add("B"));
        peers[1] = viewport.addPaintLayer("C", 0, 0, 64, 64, (gc, ctx) -> calls.add("C"));
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            paintInto(gc);
            assertEquals(List.of("A", "C"), calls,
                    "removals take effect immediately; reorder and append do not rewrite the active frame");
            calls.clear();
            paintInto(gc);
            assertEquals(List.of("C", "A", "D"), calls,
                    "callback-created damage survives retirement of the preceding frame's damage");
            calls.clear();
            paintInto(gc);
            assertTrue(calls.isEmpty(), "a subsequent clean expose is served from the backing store");
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testPanDuringPainterDefersBackingCopy() {
        viewport.setSize(64, 64);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        int[] calls = {0};
        viewport.addPaintLayer("marker", 0, 0, 100, 100, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle((int) (10 - ctx.originX()), 10, 4, 4);
            if (++calls[0] == 1) viewport.setOrigin(1, 0);
        });
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            assertDoesNotThrow(() -> paintInto(gc),
                    "panning from a painter cannot open another GC on its active backing image");
            paintInto(gc);
            assertEquals(2, calls[0]);
            ImageData data = target.getImageData();
            assertEquals(new RGB(0, 0, 0), rgb(data, 9, 11));
            assertEquals(new RGB(255, 255, 255), rgb(data, 13, 11),
                    "the next composition retires the marker's pre-pan pixels");
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testOpaquePainterMayDisposeViewport() {
        assertPainterMayDisposeViewport(255);
    }

    @Test
    public void testIsolatedPainterMayDisposeViewport() {
        assertPainterMayDisposeViewport(128);
    }

    @Test
    public void testRetainedPainterMayDisposeViewport() {
        assertPainterMayDisposeViewport(255, true);
    }

    @Test
    public void testRetainedPainterCanRemoveItsOwnLayer() {
        viewport.setSize(64, 64);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer retained = viewport.addPaintLayer("removes its cache", 0, 0, 64, 64, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(ctx.clip());
            ctx.layer().remove();
        });
        retained.setRetained(true);
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            assertDoesNotThrow(() -> paintInto(gc),
                    "removal cannot destroy the retained Image while its composition GC is selected");
            assertEquals(0, viewport.getLayers().length);
            paintInto(gc);
            assertEquals(new RGB(255, 255, 255), rgb(target.getImageData(), 12, 12),
                    "the next frame erases the removed retained layer");
            assertThrows(IllegalArgumentException.class, retained::getId);
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testRetainedPainterCanDisableItsOwnCache() {
        viewport.setSize(64, 64);
        int[] calls = {0};
        Layer retained = viewport.addPaintLayer("disables its cache", 0, 0, 64, 64, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
            if (++calls[0] == 1) ctx.layer().setRetained(false);
        });
        retained.setRetained(true);
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            assertDoesNotThrow(() -> paintInto(gc),
                    "disabling retention cannot clear fields still used by the active refresh");
            assertFalse(retained.isRetained());
            paintInto(gc);
            assertEquals(2, calls[0]);
            assertEquals(new RGB(255, 0, 0), rgb(target.getImageData(), 12, 12));
            paintInto(gc);
            assertEquals(2, calls[0], "the clean backing store remains usable after the cache is retired");
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testRedrawInsideRetainedRefreshSurvivesTheCurrentFrame() {
        viewport.setSize(64, 64);
        int[] calls = {0};
        Layer retained = viewport.addPaintLayer("refreshes again", 0, 0, 64, 64, (gc, ctx) -> {
            int paint = ++calls[0];
            gc.setBackground(gc.getDevice().getSystemColor(paint == 1 ? SWT.COLOR_BLACK : SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
            if (paint == 1) ctx.layer().redraw();
        });
        retained.setRetained(true);
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            paintInto(gc);
            paintInto(gc);
            assertEquals(2, calls[0],
                    "a retained refresh must not consume stale pixels added by its own callback");
            assertEquals(new RGB(255, 0, 0), rgb(target.getImageData(), 12, 12));
            paintInto(gc);
            assertEquals(2, calls[0]);
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testControlOrderAndPlacementSurviveSparseReadsAfterBulkAppend() {
        Button lowerControl = new Button(viewport, SWT.PUSH);
        Button upperControl = new Button(viewport, SWT.PUSH);
        Layer lower = viewport.addControlLayer("lower control", lowerControl, 20, 30, 40, 20);
        viewport.beginUpdate();
        Layer upper;
        try {
            for (int i = 0; i < 256; i++) {
                viewport.addPaintLayer("below upper " + i, 0, 0, 10, 10, (gc, ctx) -> { });
            }
            upper = viewport.addControlLayer("upper control", upperControl, 40, 40, 40, 20);
            for (int i = 0; i < 128; i++) {
                viewport.addPaintLayer("above controls " + i, 0, 0, 10, 10, (gc, ctx) -> { });
            }
        } finally {
            viewport.endUpdate();
        }
        assertSame(upperControl, viewport.getChildren()[0]);
        assertSame(lowerControl, viewport.getControl(lower));
        assertSame(upperControl, viewport.getControl(upper));
        lower.moveTo(viewport.getLayers().length - 1);
        assertSame(lowerControl, viewport.getChildren()[0]);
        viewport.setOrigin(10, 5);
        viewport.setZoom(2);
        assertEquals(new Rectangle(20, 50, 80, 40), lowerControl.getBounds());
        assertEquals(new Rectangle(60, 70, 80, 40), upperControl.getBounds());
        lower.moveTo(0);
        assertSame(upperControl, viewport.getChildren()[0],
                "lazy structural snapshots cannot leave native control stacking at an older order");
    }

    private void assertPainterMayDisposeViewport(int opacity) {
        assertPainterMayDisposeViewport(opacity, false);
    }

    private void assertPainterMayDisposeViewport(int opacity, boolean retained) {
        viewport.setSize(64, 64);
        Layer layer = viewport.addPaintLayer("disposes", 0, 0, 64, 64, (gc, ctx) -> {
            disposedIntentionally = true;
            viewport.dispose();
        });
        layer.setOpacity(opacity);
        layer.setRetained(retained);
        viewport.addPaintLayer("must not run", 0, 0, 64, 64,
                (gc, ctx) -> fail("painting must stop after widget disposal"));
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            assertDoesNotThrow(() -> paintInto(gc));
            assertTrue(viewport.isDisposed());
            assertFalse(gc.isDisposed(), "the PaintEvent GC is borrowed");
            gc.fillRectangle(0, 0, 2, 2);
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testComposeLayersPreservesCompleteCallerStateAndRegionHoles() {
        Image target = new Image(viewport.getDisplay(), 64, 64);
        Image source = new Image(viewport.getDisplay(), 16, 16);
        GC gc = new GC(target);
        GC sourceGc = new GC(source);
        Region callerClip = new Region(viewport.getDisplay());
        Transform callerTransform = new Transform(viewport.getDisplay(), 1, 0, 0, 1, 3, 5);
        Transform changedTransform = new Transform(viewport.getDisplay(), 1, 0, 0, 1, 30, 40);
        Color red = viewport.getDisplay().getSystemColor(SWT.COLOR_RED);
        Color blue = viewport.getDisplay().getSystemColor(SWT.COLOR_BLUE);
        Pattern foreground = new Pattern(viewport.getDisplay(), 0, 0, 8, 8, red, blue);
        Pattern background = new Pattern(viewport.getDisplay(), 0, 0, 8, 8, blue, red);
        Pattern changedPattern = new Pattern(viewport.getDisplay(), 0, 0, 4, 4, red, red);
        Font changedFont = new Font(viewport.getDisplay(), gc.getFont().getFontData()[0].getName(), 17, SWT.BOLD);
        try {
            gc.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            gc.fillRectangle(target.getBounds());
            sourceGc.setBackground(red);
            sourceGc.fillRectangle(source.getBounds());
            sourceGc.dispose();
            callerClip.add(0, 0, 64, 64);
            callerClip.subtract(12, 12, 8, 8);
            gc.setClipping(callerClip);
            gc.setTransform(callerTransform);
            gc.setAlpha(200);
            gc.setForeground(blue);
            gc.setBackground(red);
            gc.setForegroundPattern(foreground);
            gc.setBackgroundPattern(background);
            gc.setLineAttributes(new LineAttributes(2, SWT.CAP_ROUND, SWT.JOIN_BEVEL,
                    SWT.LINE_CUSTOM, new float[] {3, 2}, 1, 7));
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setInterpolation(SWT.LOW);
            gc.setFillRule(SWT.FILL_EVEN_ODD);
            try (GcState original = new GcState(gc)) {
                Region layerClip = new Region(viewport.getDisplay());
                try {
                    layerClip.add(original.clip);
                    layerClip.intersect(0, 0, 48, 48);
                    int[] callbacks = {0};
                    viewport.addPaintLayer("changes GC", 0, 0, 48, 48, (paint, ctx) -> {
                        callbacks[0]++;
                        original.assertState(paint, 200, layerClip);
                        mutateGc(paint, changedPattern, changedFont, changedTransform);
                    });
                    viewport.addPaintLayer("observes restored GC", 0, 0, 48, 48, (paint, ctx) -> {
                        callbacks[0]++;
                        original.assertState(paint, 200, layerClip);
                    });
                    Layer raster = viewport.addImageLayer("alpha", source, 0, 0);
                    raster.setOpacity(128);
                    composeInto(gc, target, new Rectangle(0, 0, 48, 48));
                    assertEquals(2, callbacks[0]);
                    original.assertState(gc, 200, original.clip);
                    ImageData data = target.getImageData();
                    RGB blended = rgb(data, 6, 8);
                    assertEquals(255, blended.red);
                    assertTrue(Math.abs(blended.green - 155) <= 1 && Math.abs(blended.blue - 155) <= 1,
                            "caller alpha 200 times image-layer alpha 128 must composite at alpha 100: " + blended);
                    assertEquals(new RGB(255, 255, 255), rgb(data, 14, 14),
                            "the caller Region's interior hole must remain unpainted");
                } finally {
                    layerClip.dispose();
                }
            }
        } finally {
            if (!viewport.isDisposed()) {
                for (Layer layer : viewport.getLayers()) layer.remove();
            }
            if (!sourceGc.isDisposed()) sourceGc.dispose();
            gc.dispose();
            changedFont.dispose();
            changedPattern.dispose();
            background.dispose();
            foreground.dispose();
            changedTransform.dispose();
            callerTransform.dispose();
            callerClip.dispose();
            source.dispose();
            target.dispose();
        }
    }

    @Test
    public void testComposeLayersRestoresStateAfterPainterThrows() {
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        Region clip = new Region(viewport.getDisplay());
        Transform caller = new Transform(viewport.getDisplay(), 1, 0, 0, 1, 2, 3);
        Transform changed = new Transform(viewport.getDisplay(), 1, 0, 0, 1, 20, 30);
        IllegalStateException failure = new IllegalStateException("painter failure");
        try {
            clip.add(0, 0, 40, 40);
            clip.subtract(10, 10, 5, 5);
            gc.setClipping(clip);
            gc.setTransform(caller);
            gc.setAlpha(137);
            try (GcState original = new GcState(gc)) {
                viewport.addPaintLayer("throws", 0, 0, 40, 40, (paint, ctx) -> {
                    paint.setAlpha(9);
                    paint.setBackground(paint.getDevice().getSystemColor(SWT.COLOR_RED));
                    paint.setLineWidth(9);
                    paint.setTransform(changed);
                    paint.setClipping(1, 1, 2, 2);
                    throw failure;
                });
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> composeInto(gc, target, new Rectangle(0, 0, 40, 40))));
                original.assertState(gc, 137, original.clip);
                assertFalse(gc.isDisposed());
            }
        } finally {
            if (!viewport.isDisposed()) {
                for (Layer layer : viewport.getLayers()) layer.remove();
            }
            gc.dispose();
            changed.dispose();
            caller.dispose();
            clip.dispose();
            target.dispose();
        }
    }

    /* Dispatches the real paint listener into an offscreen SWT Image GC. */
    private void paintInto(GC gc) {
        Event event = new Event();
        event.gc = gc;
        event.width = event.height = 64;
        viewport.notifyListeners(SWT.Paint, event);
    }

    /*
     * Exercise the existing composition seam with a real caller GC, including
     * nonrectangular clipping that the ordinary backing store does not create.
     * These tests use only opaque painters and image layers; they do not claim
     * screen-capture coverage or change the public widget API.
     */
    private void composeInto(GC gc, Image target, Rectangle damage) {
        try {
            Method method = LayeredViewport.class.getDeclaredMethod("composeLayers",
                    GC[].class, Image.class, Rectangle.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(viewport, new GC[] {gc}, target, damage, 0, viewport.getLayers().length);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void mutateGc(GC gc, Pattern pattern, Font font, Transform transform) {
        gc.setAlpha(17);
        gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_GREEN));
        gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_YELLOW));
        gc.setForegroundPattern(pattern);
        gc.setBackgroundPattern(pattern);
        gc.setFont(font);
        gc.setLineAttributes(new LineAttributes(7, SWT.CAP_SQUARE, SWT.JOIN_ROUND,
                SWT.LINE_CUSTOM, new float[] {6, 1}, 3, 4));
        gc.setAntialias(SWT.OFF);
        gc.setTextAntialias(SWT.OFF);
        gc.setInterpolation(SWT.HIGH);
        gc.setFillRule(SWT.FILL_WINDING);
        gc.setXORMode(true);
        gc.setTransform(transform);
        gc.setClipping(1, 2, 3, 4);
    }

    private static final class GcState implements AutoCloseable {
        final Region clip;
        final float[] transform;
        final boolean advanced, clipped, xor;
        final int antialias, textAntialias, interpolation, fillRule;
        final Color foreground, background;
        final Pattern foregroundPattern, backgroundPattern;
        final Font font;
        final LineAttributes line;

        GcState(GC gc) {
            clip = new Region(gc.getDevice());
            gc.getClipping(clip);
            transform = transformElements(gc);
            advanced = gc.getAdvanced();
            clipped = gc.isClipped();
            xor = gc.getXORMode();
            antialias = gc.getAntialias();
            textAntialias = gc.getTextAntialias();
            interpolation = gc.getInterpolation();
            fillRule = gc.getFillRule();
            foreground = gc.getForeground();
            background = gc.getBackground();
            foregroundPattern = gc.getForegroundPattern();
            backgroundPattern = gc.getBackgroundPattern();
            font = gc.getFont();
            line = gc.getLineAttributes();
        }

        void assertState(GC gc, int expectedAlpha, Region expectedClip) {
            assertEquals(expectedAlpha, gc.getAlpha());
            assertArrayEquals(transform, transformElements(gc));
            assertEquals(advanced, gc.getAdvanced());
            assertEquals(clipped, gc.isClipped());
            assertEquals(xor, gc.getXORMode());
            assertEquals(antialias, gc.getAntialias());
            assertEquals(textAntialias, gc.getTextAntialias());
            assertEquals(interpolation, gc.getInterpolation());
            assertEquals(fillRule, gc.getFillRule());
            assertEquals(foreground, gc.getForeground());
            assertEquals(background, gc.getBackground());
            assertSame(foregroundPattern, gc.getForegroundPattern());
            assertSame(backgroundPattern, gc.getBackgroundPattern());
            assertEquals(font, gc.getFont());
            LineAttributes actualLine = gc.getLineAttributes();
            assertEquals(line.width, actualLine.width);
            assertEquals(line.cap, actualLine.cap);
            assertEquals(line.join, actualLine.join);
            assertEquals(line.style, actualLine.style);
            assertArrayEquals(line.dash, actualLine.dash);
            assertEquals(line.dashOffset, actualLine.dashOffset);
            assertEquals(line.miterLimit, actualLine.miterLimit);
            Region actual = new Region(gc.getDevice());
            Region difference = new Region(gc.getDevice());
            try {
                gc.getClipping(actual);
                difference.add(expectedClip);
                difference.subtract(actual);
                assertTrue(difference.isEmpty(), "clip lost caller pixels");
                difference.add(actual);
                difference.subtract(expectedClip);
                assertTrue(difference.isEmpty(), "clip gained pixels, including a caller Region hole");
            } finally {
                difference.dispose();
                actual.dispose();
            }
        }

        @Override
        public void close() {
            clip.dispose();
        }
    }

    private static float[] transformElements(GC gc) {
        Transform transform = new Transform(gc.getDevice());
        try {
            gc.getTransform(transform);
            float[] elements = new float[6];
            transform.getElements(elements);
            return elements;
        } finally {
            transform.dispose();
        }
    }

    private RGB system(int color) {
        return viewport.getDisplay().getSystemColor(color).getRGB();
    }

    private Layer solid(String name, long x, long y, long width, long height, int color) {
        return viewport.addPaintLayer(name, x, y, width, height, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(color));
            gc.fillRectangle(ctx.clip());
        });
    }

    /* rows of 10 world px, blue and white, under a red header pinned vertically; a green tooltip above the header */
    private Layer[] treeWithHeader() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer rows = viewport.addPaintLayer("rows", 0, 0, 4000, 4000, (gc, ctx) -> {
            Rectangle clip = ctx.clip();
            for (int y = clip.y; y < clip.y + clip.height; y++) {
                boolean blue = Math.floorMod(ctx.originY() + y, 20) < 10;
                gc.setBackground(gc.getDevice().getSystemColor(blue ? SWT.COLOR_BLUE : SWT.COLOR_WHITE));
                gc.fillRectangle(clip.x, y, clip.width, 1);
            }
        });
        Layer header = solid("header", 0, 0, 200, 20, SWT.COLOR_RED);
        header.setPinned(SWT.VERTICAL);
        Layer tooltip = solid("tooltip", 40, 137, 10, 10, SWT.COLOR_GREEN);
        return new Layer[] { rows, header, tooltip };
    }

    @Test
    public void testAPinnedHeaderStaysOnTopWhileTheRowsScrollUnderIt() {
        Layer[] tree = treeWithHeader();
        Layer rows = tree[0], header = tree[1], tooltip = tree[2];
        assertEquals(SWT.VERTICAL, header.getPinned());
        show();
        RGB red = system(SWT.COLOR_RED), blue = system(SWT.COLOR_BLUE), white = system(SWT.COLOR_WHITE);
        // down by blits, back up by a blit (the header's ghost lands inside the view), down again
        for (int y : new int[] { 35, 100, 90, 135 }) {
            panAndPaint(0, y);
            ImageData shown = pixels();
            assertEquals(red, rgb(shown, 100, 5), "the header stays on top at origin " + y);
            assertEquals(Math.floorMod(y + 25, 20) < 10 ? blue : white, rgb(shown, 100, 25),
                    "the rows scroll under it at origin " + y);
            assertSameAsAFullRecomposition();
        }
        assertEquals(system(SWT.COLOR_GREEN), rgb(pixels(), 45, 5), "a layer above the header in Z paints over it");
        assertSame(header, viewport.hitTest(100, 5));
        assertSame(rows, viewport.hitTest(100, 25));
        assertSame(tooltip, viewport.hitTest(45, 5));
        // a horizontal pan moves the header with the rows: it ends at world x 200, device 170
        panAndPaint(30, 135);
        assertEquals(red, rgb(pixels(), 160, 5));
        assertNotEquals(red, rgb(pixels(), 180, 5));
        assertSameAsAFullRecomposition();
    }

    @Test
    public void testPinnedLayersSeeViewRelativeCoordinatesAndPinTheirControls() {
        Layer rows = viewport.addPaintLayer("rows", 0, 0, 4000, 4000, (gc, ctx) -> { });
        Layer header = viewport.addPaintLayer("header", 0, 0, 200, 20, (gc, ctx) -> { });
        header.setPinned(SWT.VERTICAL);
        viewport.setOrigin(10, 300);
        List<LayerPointerEvent> events = new ArrayList<>();
        header.setPointerListener(events::add);
        rows.setPointerListener(events::add);
        pointer(SWT.MouseMove, 50, 5, 0);
        pointer(SWT.MouseMove, 50, 40, 0);
        assertSame(header, events.get(0).layer());
        assertEquals(60, events.get(0).worldX(), "a free axis is world space");
        assertEquals(5, events.get(0).worldY(), "a pinned axis is measured from the view's origin");
        assertSame(rows, events.get(1).layer());
        assertEquals(340, events.get(1).worldY());
        // a control pinned on both axes keeps its place through any pan
        Button button = new Button(viewport, SWT.PUSH);
        Layer corner = viewport.addControlLayer("corner", button, 4, 6, 30, 20);
        corner.setPinned(SWT.HORIZONTAL | SWT.VERTICAL);
        assertEquals(new Rectangle(4, 6, 30, 20), button.getBounds());
        assertTrue(button.getVisible());
        viewport.setOrigin(-500, 9000);
        assertEquals(new Rectangle(4, 6, 30, 20), button.getBounds());
        corner.setPinned(SWT.NONE);
        assertFalse(button.getVisible(), "unpinned, it is back at world (4, 6), far outside the view");
        assertThrows(IllegalArgumentException.class, () -> corner.setPinned(SWT.LEFT));
    }

    @Test
    public void testAPinnedLegendKeepsItsPlaceBetweenRetainedLayers() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer base = viewport.addPaintLayer("base", -4000, -4000, 8000, 8000, (gc, ctx) -> {
            Rectangle clip = ctx.clip();
            for (int x = clip.x; x < clip.x + clip.width; x++) {
                boolean dark = Math.floorMod(ctx.originX() + x, 9) == 0;
                gc.setBackground(gc.getDevice().getSystemColor(dark ? SWT.COLOR_BLUE : SWT.COLOR_WHITE));
                gc.fillRectangle(x, clip.y, 1, clip.height);
            }
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_GREEN));
            for (int y = clip.y; y < clip.y + clip.height; y++) {
                if (Math.floorMod(ctx.originY() + y, 11) == 0) gc.fillRectangle(clip.x, y, clip.width, 1);
            }
        });
        base.setRetained(true);
        Layer legend = solid("legend", 200, 150, 40, 40, SWT.COLOR_MAGENTA);
        legend.setPinned(SWT.HORIZONTAL | SWT.VERTICAL);
        // above the legend, a retained layer: its composite holds the legend's pixels too
        solid("marker", 120, 60, 6, 6, SWT.COLOR_BLACK).setRetained(true);
        show();
        for (int[] origin : new int[][] { { 7, -9 }, { -20, 15 }, { 0, 0 }, { 33, 31 } }) {
            panAndPaint(origin[0], origin[1]);
            assertEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), 220, 170), "at " + origin[0] + "," + origin[1]);
            assertSameAsAFullRecomposition();
        }
    }

    @Test
    public void testAGroupHeaderSticksUnderTheColumnHeaderUntilTheNextGroupPushesItAway() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer rows = viewport.addPaintLayer("rows", 0, 0, 4000, 4000, (gc, ctx) -> {
            Rectangle clip = ctx.clip();
            for (int y = clip.y; y < clip.y + clip.height; y++) {
                boolean blue = Math.floorMod(ctx.originY() + y, 20) < 10;
                gc.setBackground(gc.getDevice().getSystemColor(blue ? SWT.COLOR_BLUE : SWT.COLOR_WHITE));
                gc.fillRectangle(clip.x, y, clip.width, 1);
            }
        });
        // group A spans world y 100 to 300, group B 300 to 600; each header is 16 high and sticks 20 below the top
        Layer a = solid("group A", 0, 100, 200, 16, SWT.COLOR_YELLOW);
        a.setSticky(SWT.VERTICAL, 20, 300);
        Layer b = solid("group B", 0, 300, 200, 16, SWT.COLOR_GREEN);
        b.setSticky(SWT.VERTICAL, 20, 600);
        solid("columns", 0, 0, 200, 20, SWT.COLOR_RED).setPinned(SWT.VERTICAL);
        assertEquals(SWT.VERTICAL, a.getSticky());
        show();
        RGB yellow = system(SWT.COLOR_YELLOW), green = system(SWT.COLOR_GREEN), red = system(SWT.COLOR_RED);
        // origin, then the device y expected of A's and B's top (A pushed up by B and under the column header;
        // at 90 A is stuck, at 70 it is not: the pan between them unsticks it with its ghost in view)
        int[][] steps = { { 0, 100, 300 }, { 50, 50, 250 }, { 150, 20, 150 }, { 270, 14, 30 }, { 400, -116, 20 },
                { 380, -96, 20 }, { 90, 20, 210 }, { 70, 30, 230 }, { 150, 20, 150 } };
        for (int[] step : steps) {
            panAndPaint(0, step[0]);
            ImageData shown = pixels();
            String at = " at origin " + step[0];
            assertEquals(red, rgb(shown, 100, 5), "the column header stays on top" + at);
            if (step[1] + 15 >= 20) assertEquals(yellow, rgb(shown, 100, step[1] + 15), "A's last line" + at);
            if (step[1] + 15 >= 20) assertEquals(yellow, rgb(shown, 2, step[1] + 15), "A sticks only vertically" + at);
            if (step[2] < 240) assertEquals(green, rgb(shown, 100, step[2]), "B's first line" + at);
            if (step[1] > 20) assertNotEquals(yellow, rgb(shown, 100, step[1] - 1), "above A" + at);
            assertSameAsAFullRecomposition();
        }
        // hit testing and pointer events keep A's own world coordinates while it is stuck
        assertSame(a, viewport.hitTest(100, 25));
        List<LayerPointerEvent> events = new ArrayList<>();
        a.setPointerListener(events::add);
        rows.setPointerListener(events::add);
        pointer(SWT.MouseMove, 100, 25, 0);
        assertSame(a, events.get(0).layer());
        assertEquals(105, events.get(0).worldY(), "stuck at origin 150 + 20: A's line 5 is under device line 25");
        pointer(SWT.MouseMove, 100, 60, 0);
        assertSame(rows, events.get(1).layer());
        assertEquals(210, events.get(1).worldY());
    }

    @Test
    public void testAStickyColumnAndItsArguments() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer column = solid("column", 100, 0, 30, 200, SWT.COLOR_MAGENTA);
        column.setSticky(SWT.HORIZONTAL, 0, 1000);
        show();
        panAndPaint(150, 0);
        assertEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), 5, 50), "stuck at the left edge");
        assertNotEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), 35, 50));
        assertSameAsAFullRecomposition();
        panAndPaint(140, 0);
        panAndPaint(90, 0); // unstuck by a pan wider than the column: its ghost lands beside it
        assertEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), 15, 50));
        assertSameAsAFullRecomposition();
        panAndPaint(1000, 0);
        assertNotEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), 5, 50), "pushed out past its limit");
        paintAfter(() -> {
            column.setSticky(SWT.NONE, 0, 0);
            viewport.setOrigin(150, 0);
        });
        assertNotEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), 5, 50), "no longer sticky: at world x 100");
        assertSameAsAFullRecomposition();
        assertThrows(IllegalArgumentException.class, () -> column.setSticky(SWT.HORIZONTAL | SWT.VERTICAL, 0, 0));
    }
    @Test
    public void testExtremePanRecomposesInsteadOfNarrowingLongMinValue() {
        viewport.setSize(64, 64);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.setOrigin(-1, 0);
        Layer layer = solid("retained", 0, 0, 64, 64, SWT.COLOR_BLACK);
        layer.setRetained(true);
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            paintInto(gc);
            assertEquals(system(SWT.COLOR_BLACK), rgb(target.getImageData(), 10, 10));
            viewport.setOrigin(Long.MAX_VALUE, 0);
            paintInto(gc);
            assertEquals(system(SWT.COLOR_WHITE), rgb(target.getImageData(), 10, 10));
            viewport.setOrigin(-1, 0);
            paintInto(gc);
            assertEquals(system(SWT.COLOR_BLACK), rgb(target.getImageData(), 10, 10));
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testCachedRanksPreserveStableOrderAcrossSparseRetainedEdits() {
        List<Layer> expected = new ArrayList<>();
        java.util.Random random = new java.util.Random(0x20261118L);
        viewport.beginUpdate();
        try {
            for (int i = 0; i < 64; i++) {
                Layer layer = viewport.addPaintLayer("rank " + i, 0, 0, 32, 32, (gc, ctx) -> { });
                if ((i & 15) == 0) layer.setRetained(true);
                expected.add(layer);
            }
            for (int step = 0; step < 512; step++) {
                Layer moved = expected.remove(random.nextInt(expected.size()));
                int next = random.nextInt(expected.size() + 1);
                expected.add(next, moved);
                moved.moveTo(next);
                if ((step & 7) == 0) moved.setRetained(!moved.isRetained());
                moved.setOpacity(step % 254 + 1);
                for (int i = 0; i < expected.size(); i++) assertEquals(i, expected.get(i).getZIndex());
                assertSame(expected.get(expected.size() - 1), viewport.hitTest(4, 4));
                if ((step & 31) == 0 && expected.size() > 32) {
                    Layer removed = expected.remove(random.nextInt(expected.size()));
                    removed.remove();
                    assertThrows(IllegalArgumentException.class, removed::getZIndex);
                }
            }
        } finally {
            viewport.endUpdate();
        }
        assertArrayEquals(expected.toArray(Layer[]::new), viewport.getLayers());
    }

    @Test
    public void testFixedAxesPreservePinnedZoomAndImmutableProjection() {
        Layer layer = solid("fixed header", 20, 5, 40, 20, SWT.COLOR_RED);
        layer.setFixedY(true);
        viewport.setOrigin(10, 200);
        viewport.setZoom(2);
        LayeredViewport.Projection captured = layer.getProjection();
        assertEquals(20.0, captured.projectX(20));
        assertEquals(5.0, captured.projectY(5));
        assertEquals(new WorldPoint(22, 10), captured.toWorld(25, 10));
        assertSame(layer, viewport.hitTest(25, 10));
        List<LayerPointerEvent> events = new ArrayList<>();
        layer.setPointerListener(events::add);
        pointer(SWT.MouseMove, 25, 10, 0);
        assertEquals(22, events.getFirst().worldX());
        assertEquals(10, events.getFirst().worldY());
        layer.setWorldClip(new WorldBounds(20, 8, 40, 5));
        assertNull(viewport.hitTest(25, 6));
        assertSame(layer, viewport.hitTest(25, 10));
        layer.setPinned(SWT.VERTICAL);
        layer.setFixedY(false);
        assertEquals(10.0, layer.getProjection().projectY(5), "pinned axes still zoom");
        viewport.setZoom(0.5);
        viewport.setOrigin(30, 400);
        assertEquals(20.0, captured.projectX(20), "captured projection does not consult live viewport state");
        assertEquals(5.0, captured.projectY(5));
        assertArrayEquals(new String[] { "layer", "clip", "originX", "originY", "zoom" },
                java.util.Arrays.stream(LayeredViewport.PaintContext.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
    }

    @Test
    public void testFixedControlsUseIndependentAxisScalesAndRestoreStickyState() {
        Button button = new Button(viewport, SWT.PUSH);
        Layer layer = viewport.addControlLayer("fixed control", button, 20, 5, 40, 20);
        layer.setSticky(SWT.VERTICAL, 2, 500);
        layer.setFixedY(true);
        viewport.setOrigin(10, 200);
        viewport.setZoom(2);
        assertEquals(new Rectangle(20, 5, 80, 20), button.getBounds());
        layer.setFixedX(true);
        assertTrue(layer.isFixed());
        assertEquals(new Rectangle(20, 5, 40, 20), button.getBounds());
        layer.setFixed(false);
        assertFalse(layer.isFixedX());
        assertFalse(layer.isFixedY());
        assertEquals(new Rectangle(20, 4, 80, 40), button.getBounds(), "sticky inset is restored with zoom");
        assertSame(button, viewport.getControl(layer));
    }

    @Test
    public void testFixedImageGhostsAndRetainedCompositesMatchFullRecomposition() {
        viewport.setSize(48, 48);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer base = solid("background", -100, -100, 200, 200, SWT.COLOR_BLUE);
        base.setRetained(true);
        Image red = new Image(viewport.getDisplay(), 8, 8);
        GC fill = new GC(red);
        try { fill.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_RED)); fill.fillRectangle(0, 0, 8, 8); }
        finally { fill.dispose(); }
        Layer fixed = viewport.addImageLayer("fixed image", red, 20, 20);
        fixed.setFixed(true);
        viewport.addPaintLayer("retained above", -100, -100, 200, 200, (gc, ctx) -> { }).setRetained(true);
        Image target = new Image(viewport.getDisplay(), 48, 48);
        GC gc = new GC(target);
        try {
            paintInto(gc);
            for (int[] origin : new int[][] { { 7, -9 }, { -5, 4 }, { 0, 0 }, { 13, 12 } }) {
                viewport.setOrigin(origin[0], origin[1]);
                paintInto(gc);
                ImageData shifted = target.getImageData();
                assertEquals(system(SWT.COLOR_RED), rgb(shifted, 22, 22));
                viewport.redraw();
                paintInto(gc);
                ImageData full = target.getImageData();
                for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) {
                    assertEquals(rgb(full, x, y), rgb(shifted, x, y), "ghost at " + x + "," + y);
                }
            }
            viewport.setZoom(2);
            paintInto(gc);
            assertEquals(system(SWT.COLOR_RED), rgb(target.getImageData(), 22, 22));
            assertEquals(system(SWT.COLOR_BLUE), rgb(target.getImageData(), 29, 22));
        } finally {
            gc.dispose();
            target.dispose();
            fixed.remove();
            red.dispose();
        }
    }

    @Test
    public void testStickyLongLimitCancellationKeepsLeadingEdgeVisible() {
        Layer layer = solid("extreme sticky", Long.MIN_VALUE, 0, 16, 16, SWT.COLOR_RED);
        layer.setSticky(SWT.HORIZONTAL, 0, Long.MAX_VALUE);
        viewport.setOrigin(1, 0);
        assertEquals(Long.MIN_VALUE, layer.getProjection().originX());
        assertEquals(0.0, layer.getProjection().projectX(Long.MIN_VALUE));
        assertSame(layer, viewport.hitTest(5, 5));
    }

    @Test
    public void testStickyArithmeticAgainstExactIntegerOracle() throws Exception {
        Method sum = LayeredViewport.class.getDeclaredMethod("saturatedAddSubtract", long.class, long.class, long.class);
        sum.setAccessible(true);
        long[] edges = { Long.MIN_VALUE, Long.MIN_VALUE + 1, -100, -1, 0, 1, 100, Long.MAX_VALUE - 1, Long.MAX_VALUE };
        java.util.Random random = new java.util.Random(12345);
        java.math.BigInteger low = java.math.BigInteger.valueOf(Long.MIN_VALUE);
        java.math.BigInteger high = java.math.BigInteger.valueOf(Long.MAX_VALUE);
        for (int n = 0; n < 10729; n++) {
            long a = n < 729 ? edges[n / 81] : random.nextLong();
            long b = n < 729 ? edges[n / 9 % 9] : random.nextLong();
            long c = n < 729 ? edges[n % 9] : random.nextLong();
            long expected = java.math.BigInteger.valueOf(a).add(java.math.BigInteger.valueOf(b))
                    .subtract(java.math.BigInteger.valueOf(c)).max(low).min(high).longValue();
            assertEquals(expected, ((Long) sum.invoke(null, a, b, c)).longValue());
        }
    }

    @Test
    public void testFixedLayerScreenCaptureShowsFreshMovedPixels() throws Exception {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer layer = solid("screen marker", 20, 20, 16, 16, SWT.COLOR_RED);
        layer.setFixed(true);
        show();
        ImageData first = captureScreen("fixed-before");
        assertEquals(system(SWT.COLOR_RED), rgb(first, 25, 25));
        assertEquals(system(SWT.COLOR_WHITE), rgb(first, 65, 65));
        paintAfter(() -> layer.setWorldBounds(60, 60, 16, 16));
        ImageData next = captureScreen("fixed-after");
        assertEquals(system(SWT.COLOR_WHITE), rgb(next, 25, 25), "old pixels must be retired");
        assertEquals(system(SWT.COLOR_RED), rgb(next, 65, 65), "new pixels must reach the screen");
    }

    private ImageData captureScreen(String name) throws Exception {
        Rectangle bounds = viewport.getClientArea();
        org.eclipse.swt.graphics.Point origin = viewport.toDisplay(bounds.x, bounds.y);
        Image image = new Image(viewport.getDisplay(), bounds.width, bounds.height);
        GC gc = new GC(viewport.getDisplay());
        try {
            gc.copyArea(image, origin.x, origin.y);
            ImageData result = image.getImageData();
            String directory = System.getProperty("swt.layer.proof.dir");
            if (directory != null) {
                java.nio.file.Path file = java.nio.file.Path.of(directory, name + ".png");
                java.nio.file.Files.createDirectories(file.getParent());
                org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader();
                loader.data = new ImageData[] { result };
                loader.save(file.toString(), SWT.IMAGE_PNG);
            }
            return result;
        } finally {
            gc.dispose();
            image.dispose();
        }
    }

/* Records the Java/native submission boundary; it does not claim GPU or
       DirectComposition coverage. All SWT widgets, Images and GCs remain real. */
    private static final class RecordingNativeLayers implements org.eclipse.swt.internal.NativeLayers {
        private final java.util.Map<Long, Integer> pixelsPerLayer = new java.util.HashMap<>();
        private long nextHandle = 100;
        private int additions, uploads, applications, removals, disposals;
        private int creations;
        private boolean allowCreate;
        private long createdComposition = 1L;
        private int applyResult;
        private long supported;
        private int capabilityQueries, sceneApplications;
        private boolean lastScene;
        private int[] submittedFlagArray, lastFlags = new int[0];
        private Runnable onApply, onCapabilities, onUpload;
        private long[] submittedHandleArray, lastHandles = new long[0];
        private float[] submittedPropertyArray, lastProperties = new float[0];

        @Override
        public long create(org.eclipse.swt.widgets.Control control) {
            assertTrue(allowCreate, "unexpected native compositor creation");
            creations++;
            return createdComposition;
        }

        @Override
        public long capabilities(long composition) {
            assertEquals(1L, composition);
            capabilityQueries++;
            Runnable callback = onCapabilities;
            onCapabilities = null;
            if (callback != null) callback.run();
            return supported;
        }

        @Override
        public long addLayer(long composition, int width, int height) {
            assertEquals(1L, composition);
            long handle = nextHandle++;
            pixelsPerLayer.put(handle, Math.multiplyExact(width, height));
            additions++;
            return handle;
        }

        @Override
        public int upload(long layer, int[] pixels) {
            assertTrue(pixelsPerLayer.containsKey(layer), "upload owns a live native layer");
            assertEquals(pixelsPerLayer.get(layer).intValue(), pixels.length);
            uploads++;
            Runnable callback = onUpload;
            onUpload = null;
            if (callback != null) callback.run();
            return 0;
        }

        @Override
        public int apply(long composition, long[] handles, float[] properties, int count) {
            assertEquals(1L, composition);
            assertTrue(count >= 0 && count <= handles.length);
            assertTrue((long) count * 4 <= properties.length);
            applications++;
            lastScene = false;
            submittedHandleArray = handles;
            submittedPropertyArray = properties;
            lastHandles = java.util.Arrays.copyOf(handles, count);
            lastProperties = java.util.Arrays.copyOf(properties, count * 4);
            for (int i = 0; i < count; i++) {
                assertTrue(pixelsPerLayer.containsKey(handles[i]), "only live handles are submitted");
                for (int p = 0; p < 4; p++) {
                    assertTrue(Float.isFinite(properties[i * 4 + p]),
                            "native properties must be finite, including hidden layers");
                }
                assertTrue(properties[i * 4 + 2] >= 0 && properties[i * 4 + 2] <= 1);
                assertTrue(properties[i * 4 + 3] == 0 || properties[i * 4 + 3] == 1);
            }
            Runnable callback = onApply;
            onApply = null;
            if (callback != null) callback.run();
            return applyResult;
        }

        @Override
        public int applyScene(long composition, long[] handles, float[] properties, int[] flags, int count) {
            assertEquals(1L, composition);
            assertTrue(count >= 0 && count <= handles.length && count <= flags.length);
            assertTrue((long) count * NativeLayers.SCENE_FLOATS <= properties.length);
            applications++;
            sceneApplications++;
            lastScene = true;
            submittedHandleArray = handles;
            submittedPropertyArray = properties;
            submittedFlagArray = flags;
            lastHandles = java.util.Arrays.copyOf(handles, count);
            lastProperties = java.util.Arrays.copyOf(properties, count * NativeLayers.SCENE_FLOATS);
            lastFlags = java.util.Arrays.copyOf(flags, count);
            for (int i = 0; i < count; i++) {
                assertTrue(pixelsPerLayer.containsKey(handles[i]));
                assertEquals(0, flags[i] & ~NativeLayers.SCENE_CLIP);
                int at = i * NativeLayers.SCENE_FLOATS;
                for (int p = 0; p < NativeLayers.SCENE_FLOATS; p++) {
                    assertTrue(Float.isFinite(properties[at + p]));
                    assertTrue(Math.abs(properties[at + p]) <= 0x1.0p20);
                }
                assertTrue(properties[at + 2] >= 0 && properties[at + 2] <= 1);
                assertTrue(properties[at + 3] == 0 || properties[at + 3] == 1);
                if (flags[i] != 0) {
                    assertTrue(properties[at + 10] <= properties[at + 12]);
                    assertTrue(properties[at + 11] <= properties[at + 13]);
                }
            }
            Runnable callback = onApply;
            onApply = null;
            if (callback != null) callback.run();
            return applyResult;
        }

        @Override
        public void removeLayer(long composition, long layer) {
            assertEquals(1L, composition);
            assertNotNull(pixelsPerLayer.remove(layer), "a native layer is freed once");
            removals++;
        }

        @Override
        public int waitForCommit(long composition) {
            assertEquals(1L, composition);
            return 0;
        }

        @Override
        public void dispose(long composition) {
            assertEquals(1L, composition);
            disposals++;
            pixelsPerLayer.clear();
        }
    }

    private RecordingNativeLayers installRecordingNativeLayers() throws ReflectiveOperationException {
        org.junit.jupiter.api.Assumptions.assumeTrue(org.eclipse.swt.internal.DPIUtil.getDeviceZoom() == 100,
                "the native image contract currently admits 100% device zoom only");
        RecordingNativeLayers backend = new RecordingNativeLayers();
        Field backendField = LayeredViewport.class.getDeclaredField("nativeBackend");
        backendField.setAccessible(true);
        backendField.set(viewport, backend);
        Field compositionField = LayeredViewport.class.getDeclaredField("composition");
        compositionField.setAccessible(true);
        compositionField.setLong(viewport, 1L);
        Field triedField = LayeredViewport.class.getDeclaredField("nativeTried");
        triedField.setAccessible(true);
        triedField.setBoolean(viewport, true);
        return backend;
    }

    private Image nativeTestImage(int width, int height, int color) {
        Image image = new Image(viewport.getDisplay(), width, height);
        GC gc = new GC(image);
        try {
            gc.setBackground(viewport.getDisplay().getSystemColor(color));
            gc.fillRectangle(0, 0, width, height);
        } finally {
            gc.dispose();
        }
        return image;
    }

    @Test
    public void testNativeResidencyDisabledByDefaultAndExplicitlyReclaimed()
            throws ReflectiveOperationException {
        viewport.setSize(300, 240);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(10, 10, SWT.COLOR_RED);
        Layer far = viewport.addImageLayer("old unrestricted native membership", image, 2000, 20);
        try {
            assertEquals(-1, viewport.getNativeResidencyOverscanPixels());
            assertThrows(IllegalArgumentException.class,
                    () -> viewport.setNativeResidencyOverscanPixels(-2));
            assertThrows(IllegalArgumentException.class,
                    () -> viewport.setNativeResidencyOverscanPixels((1 << 20) + 1));
            assertEquals(-1, viewport.getNativeResidencyOverscanPixels(),
                    "rejected configuration never mutates the policy");
            assertTrue(far.setNative(true), "default preserves prior offscreen-native behavior");
            assertEquals(1, backend.additions);
            assertEquals(1, backend.uploads);
            assertEquals(0, backend.removals);
            viewport.setNativeResidencyOverscanPixels(12);
            assertEquals(12, viewport.getNativeResidencyOverscanPixels());
            assertFalse(far.isNative(), "out-of-view image releases native surface");
            assertEquals(1, backend.removals);
            assertEquals(0, backend.pixelsPerLayer.size());
            int idle = backend.applications;
            viewport.setNativeResidencyOverscanPixels(12);
            assertEquals(idle, backend.applications, "same admission policy is a no-op");
            viewport.setNativeResidencyOverscanPixels(-1);
            assertTrue(far.isNative(), "disabling policy restores original native membership");
            assertEquals(2, backend.additions);
            assertEquals(2, backend.uploads, "released surface reuploads borrowed Image");
            assertFalse(image.isDisposed());
        } finally {
            if (!viewport.isDisposed()) far.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeResidencyHysteresisAcrossPanAndVisibility()
            throws ReflectiveOperationException {
        viewport.setSize(300, 240);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        viewport.setNativeResidencyOverscanPixels(12);
        Image image = nativeTestImage(10, 10, SWT.COLOR_BLUE);
        Layer near = viewport.addImageLayer("scrolling native overscan", image, 320, 20);
        try {
            long logicalId = near.getId();
            assertFalse(near.setNative(true), "20 pixels beyond viewport exceeds 12px admission");
            assertEquals(0, backend.additions, "no native surface for distant layer");
            assertEquals(0, backend.uploads);
            viewport.setOrigin(10, 0);
            assertTrue(near.isNative(), "10px outside viewport is prefetched");
            assertEquals(1, backend.additions);
            assertEquals(1, backend.uploads);
            viewport.setOrigin(0, 0);
            assertTrue(near.isNative(), "retain within 24px release distance");
            assertEquals(0, backend.removals);
            viewport.setOrigin(-10, 0);
            assertFalse(near.isNative(), "release at 30px beyond viewport");
            assertEquals(1, backend.removals);
            viewport.setOrigin(10, 0);
            assertTrue(near.isNative(), "re-entering generates one fresh native surface");
            assertEquals(2, backend.additions);
            assertEquals(2, backend.uploads);
            near.setVisible(false);
            assertFalse(near.isNative(), "invisible images no longer occupy native surfaces");
            assertEquals(2, backend.removals);
            near.setVisible(true);
            assertTrue(near.isNative(), "visible image becomes native again in overscan");
            assertEquals(3, backend.additions);
            assertEquals(logicalId, near.getId(), "logical image layer identity is unchanged");
            assertFalse(image.isDisposed(), "native owner never disposes borrowed Image");
        } finally {
            if (!viewport.isDisposed()) near.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeResidencyUsesPinnedProjectionClipsAndExtremeLongPans()
            throws ReflectiveOperationException {
        viewport.setSize(100, 80);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        viewport.setNativeResidencyOverscanPixels(8);
        Image image = nativeTestImage(20, 20, SWT.COLOR_GREEN);
        Layer layer = viewport.addImageLayer("pinned native residency", image, 130, 10);
        try {
            layer.setPinned(SWT.HORIZONTAL);
            assertFalse(layer.setNative(true), "pinned image is 30px offscreen");
            viewport.setOrigin(120, 0);
            assertFalse(layer.isNative(), "horizontal pin prevents pan-based admission");
            assertEquals(0, backend.additions);

            layer.setPinned(0); // ordinary world projection: now at client x=10
            assertTrue(layer.isNative());
            assertEquals(1, backend.additions);
            layer.setWorldClip(new WorldBounds(1000, 10, 10, 10));
            assertFalse(layer.isNative(), "disjoint world clip excludes native residency");
            assertEquals(1, backend.removals);
            layer.setWorldClip(null);
            assertTrue(layer.isNative(), "restored visible projection is readmitted");
            assertEquals(2, backend.additions);

            viewport.setOrigin(Long.MAX_VALUE, 0);
            assertFalse(layer.isNative(), "extreme signed-long pan cannot overflow into view");
            viewport.setOrigin(120, 0);
            assertTrue(layer.isNative(), "native surface can return after extreme pan");
            assertEquals(3, backend.uploads, "reentry restores borrowed source pixels");
            assertFalse(image.isDisposed());
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeSceneFeaturesAreAdmittedIndividually() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D;
        Image image = nativeTestImage(40, 30, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("affine admission", image, 10, 20);
        try {
            viewport.setZoom(2);
            assertTrue(layer.setNative(true));
            assertTrue(backend.lastScene);
            assertEquals(2, backend.lastProperties[4]);
            layer.setWorldClip(new WorldBounds(10, 20, 20, 15));
            assertFalse(layer.isNative(), "affine support does not imply clip support");
            layer.setWorldClip(null);
            assertTrue(layer.isNative());
            layer.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
            assertFalse(layer.isNative(), "scene support does not imply a backdrop blend effect");
            layer.setBlendMode(LayeredViewport.BLEND_NORMAL);
            assertTrue(layer.isNative());
            assertEquals(1, backend.capabilityQueries, "capabilities belong to this composition, not every frame");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testMaskedRasterCannotBypassSoftwareThroughNativeScene() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D | NativeLayers.LOCAL_RECT_CLIP;
        Image image = nativeTestImage(40, 30, SWT.COLOR_RED);
        Image mask = nativeTestImage(2, 2, SWT.COLOR_WHITE);
        Layer layer = viewport.addImageLayer("native mask gate", image, 10, 20);
        try {
            assertTrue(layer.setNative(true));
            layer.setMask(mask);
            assertFalse(layer.isNative(), "native composition has no agreed mask capability");
            assertSame(mask, layer.getMask());
            layer.setMask(null);
            assertTrue(layer.isNative(), "removing the mask restores the old eligible image path");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
            mask.dispose();
        }
    }

    @Test
    public void testNativeClipSupportDoesNotImplyAffineSupport() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.LOCAL_RECT_CLIP;
        Image image = nativeTestImage(40, 30, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("clip admission", image, 10, 20);
        try {
            layer.setWorldClip(new WorldBounds(15, 25, 10, 10));
            assertTrue(layer.setNative(true));
            assertArrayEquals(new int[] {NativeLayers.SCENE_CLIP}, backend.lastFlags);
            assertEquals(5, backend.lastProperties[10]);
            assertEquals(15, backend.lastProperties[12]);
            viewport.setZoom(2);
            assertFalse(layer.isNative());
            viewport.setZoom(1);
            assertTrue(layer.isNative());
            layer.setWorldClip(null);
            assertFalse(backend.lastScene, "legacy submission explicitly resets a prior clip");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeAffineClipUsesSourcePixelsAndIndependentAxisProjection() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D | NativeLayers.LOCAL_RECT_CLIP;
        Image image = nativeTestImage(40, 30, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("source pixels", image, 10, 20);
        try {
            layer.setWorldBounds(10, 20, 80, 60);
            layer.setWorldClip(new WorldBounds(20, 25, 30, 15));
            viewport.setOrigin(5, 7);
            viewport.setZoom(2);
            assertTrue(layer.setNative(true));
            assertArrayEquals(new float[] {10, 26, 1, 1, 4, 0, 0, 4, 0, 0, 5, 2.5f, 20, 10},
                    backend.lastProperties);
            assertSame(layer, viewport.hitTest(40, 45));
            assertNull(viewport.hitTest(20, 30));
            layer.setFixedX(true);
            assertEquals(10, backend.lastProperties[0]);
            assertEquals(2, backend.lastProperties[4]);
            assertEquals(4, backend.lastProperties[7]);
            layer.setPinned(SWT.VERTICAL);
            assertEquals(40, backend.lastProperties[1]);
            assertEquals(1, backend.additions, "geometry changes retain the same native surface");
            assertEquals(1, backend.uploads);
            layer.setWorldClip(null);
            layer.setWorldBounds(10, 20, 40, 30);
            viewport.setZoom(1);
            assertFalse(backend.lastScene, "the original apply resets identity and clip");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeSceneDpiUsesShellAutoscaleAndKeepsCanonicalRaster() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D;
        Image image = nativeTestImage(8, 6, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("DPI projection", image, 10, 20);
        try {
            assertTrue(layer.setNative(true));
            org.eclipse.swt.internal.DPIUtil.runWithAutoScaleValue("150", () -> {
                layer.setOpacity(200);
                assertTrue(layer.isNative());
                assertTrue(backend.lastScene);
                assertEquals(15, backend.lastProperties[0]);
                assertEquals(30, backend.lastProperties[1]);
                assertEquals(1.5f, backend.lastProperties[4]);
                assertEquals(1.5f, backend.lastProperties[7]);
                assertEquals(48, backend.pixelsPerLayer.get(backend.lastHandles[0]).intValue());
                assertEquals(1, backend.uploads, "DPI changes transform canonical pixels without resizing the source");
            });
            layer.setOpacity(255);
            assertFalse(backend.lastScene);
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeSceneHiddenExtremesAndDisjointWorldClipStayFinite() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D | NativeLayers.LOCAL_RECT_CLIP;
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("far scene", image, Long.MIN_VALUE, 20);
        try {
            viewport.setZoom(2);
            assertTrue(layer.setNative(true));
            assertEquals(0, backend.lastProperties[3]);
            assertEquals(0, backend.lastProperties[0]);
            assertEquals(1, backend.lastProperties[4]);
            layer.setWorldClip(new WorldBounds(Long.MAX_VALUE - 8, 20, 8, 8));
            assertTrue(layer.isNative());
            assertEquals(0, backend.lastProperties[3]);
            layer.setWorldClip(null);
            viewport.setOrigin(Long.MIN_VALUE, 0);
            assertEquals(1, backend.lastProperties[3]);
            assertEquals(0, backend.lastProperties[0]);
            layer.setWorldBounds(0, 0, Long.MAX_VALUE, 8);
            assertFalse(layer.isNative(), "an unrepresentable affine falls back before native mutation");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeSceneBatchesReuseAllLanesAndIsolateNestedCallbacks() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D | NativeLayers.LOCAL_RECT_CLIP;
        Image image = nativeTestImage(20, 20, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("scene batch", image, 10, 20);
        try {
            viewport.setZoom(2);
            assertTrue(layer.setNative(true));
            long[] reusableHandles = backend.submittedHandleArray;
            float[] reusableProperties = backend.submittedPropertyArray;
            int[] reusableFlags = backend.submittedFlagArray;
            int applications = backend.applications;
            viewport.beginUpdate();
            try {
                layer.setOpacity(200);
                layer.setWorldClip(new WorldBounds(12, 22, 10, 10));
                layer.setWorldBounds(11, 21, 20, 20);
                assertEquals(applications, backend.applications);
            } finally {
                viewport.endUpdate();
            }
            assertEquals(applications + 1, backend.applications);
            assertSame(reusableHandles, backend.submittedHandleArray);
            assertSame(reusableProperties, backend.submittedPropertyArray);
            assertSame(reusableFlags, backend.submittedFlagArray);
            backend.onApply = () -> {
                float[] outer = backend.submittedPropertyArray;
                int[] flags = backend.submittedFlagArray;
                float[] expected = outer.clone();
                int[] expectedFlags = flags.clone();
                layer.setWorldClip(null);
                assertNotSame(outer, backend.submittedPropertyArray);
                assertArrayEquals(expected, outer);
                assertArrayEquals(expectedFlags, flags);
            };
            layer.setOpacity(128);
            layer.setOpacity(96);
            assertSame(reusableProperties, backend.submittedPropertyArray);
            assertSame(reusableFlags, backend.submittedFlagArray);
            backend.onApply = () -> { throw new IllegalStateException("scene callback"); };
            assertThrows(IllegalStateException.class, () -> layer.setOpacity(80));
            layer.setOpacity(64);
            assertSame(reusableProperties, backend.submittedPropertyArray);
        } finally {
            backend.onApply = null;
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeSceneFailurePreservesPaintedFallbackAndBorrowedImage() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D;
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("scene failure", image, 10, 20);
        try {
            viewport.setZoom(2);
            assertTrue(layer.setNative(true));
            backend.applyResult = -1;
            layer.setOpacity(128);
            assertFalse(layer.isNative());
            assertEquals(1, backend.disposals);
            assertFalse(layer.setNative(true));
            assertFalse(image.isDisposed());
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeCapabilityAndUploadCallbacksCannotPublishRetiredOwners() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D;
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("capability callback", image, 10, 20);
        try {
            backend.onCapabilities = () -> layer.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
            assertFalse(layer.setNative(true));
            assertEquals(0, backend.additions);
            backend.onUpload = layer::remove;
            layer.setBlendMode(LayeredViewport.BLEND_NORMAL);
            assertEquals(1, backend.additions);
            assertEquals(1, backend.removals);
            assertTrue(backend.pixelsPerLayer.isEmpty());
            assertFalse(image.isDisposed());
        } finally {
            image.dispose();
        }
    }

    @Test
    public void testNativeExceptionalUploadReleasesUnpublishedSurface() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D;
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("exceptional upload", image, 10, 20);
        try {
            backend.onUpload = () -> { throw new IllegalStateException("upload callback"); };
            assertThrows(IllegalStateException.class, () -> layer.setNative(true));
            assertEquals(1, backend.removals);
            assertTrue(backend.pixelsPerLayer.isEmpty());
            assertFalse(layer.isNative());
            assertTrue(layer.setNative(true), "the preparation guard is restored on exceptional exit");
            assertFalse(image.isDisposed());
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeCapabilityCallbackMayDisposeWithoutPublishingFeatures() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D;
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("retired query", image, 10, 20);
        try {
            backend.onCapabilities = () -> {
                disposedIntentionally = true;
                viewport.dispose();
            };
            assertFalse(layer.setNative(true));
            assertTrue(viewport.isDisposed());
            assertEquals(0, backend.additions);
            assertEquals(1, backend.disposals);
            assertFalse(image.isDisposed());
        } finally {
            image.dispose();
        }
    }

    @Test
    public void testNativeActivationSeesAppendedLayersWithoutAnOrderRead() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(12, 10, SWT.COLOR_RED);
        Layer lower = null, upper = null;
        try {
            lower = viewport.addImageLayer("first native", image, 10, 20);
            assertTrue(lower.setNative(true), "append followed immediately by native activation");
            assertEquals(1, backend.additions);
            assertEquals(1, backend.uploads);
            assertEquals(1, backend.applications);
            long lowerHandle = backend.lastHandles[0];
            assertArrayEquals(new float[] {10, 20, 1, 1}, backend.lastProperties);

            upper = viewport.addImageLayer("second native", image, 30, 40);
            assertTrue(upper.setNative(true), "another append must invalidate the native order");
            assertEquals(2, backend.additions);
            assertEquals(2, backend.uploads);
            assertEquals(2, backend.lastHandles.length);
            long upperHandle = backend.lastHandles[1];
            assertArrayEquals(new long[] {lowerHandle, upperHandle}, backend.lastHandles);

            lower.moveTo(1);
            assertArrayEquals(new long[] {upperHandle, lowerHandle}, backend.lastHandles,
                    "native Z order follows the live structural mutation");
            assertFalse(image.isDisposed(), "image ownership remains with the application");
        } finally {
            if (!viewport.isDisposed()) {
                if (lower != null) lower.remove();
                if (upper != null) upper.remove();
            }
            image.dispose();
        }
    }

    @Test
    public void testNativePropertyBatchesReuseSubmissionArraysAndCommitOnce() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("batched native", image, 10, 20);
        try {
            assertTrue(layer.setNative(true));
            long[] handles = backend.submittedHandleArray;
            float[] properties = backend.submittedPropertyArray;
            int applications = backend.applications;
            viewport.beginUpdate();
            try {
                viewport.beginUpdate();
                try {
                    layer.setOpacity(128);
                    layer.setWorldBounds(40, 30, 8, 8);
                    layer.setVisible(false);
                    layer.setFixed(true);
                    viewport.setZoom(2);
                    layer.setVisible(true);
                } finally {
                    viewport.endUpdate();
                }
                assertEquals(applications, backend.applications,
                        "an inner endUpdate does not present the pending frame");
            } finally {
                viewport.endUpdate();
            }
            assertEquals(applications + 1, backend.applications);
            assertSame(handles, backend.submittedHandleArray);
            assertSame(properties, backend.submittedPropertyArray);
            assertArrayEquals(new float[] {40, 30, 128 / 255f, 1}, backend.lastProperties);
            assertEquals(1, backend.additions, "fixed pixels remain native through the viewport zoom");
            assertEquals(1, backend.uploads, "property-only updates do not upload image pixels");

            applications = backend.applications;
            layer.setOpacity(128);
            layer.setVisible(true);
            layer.setWorldBounds(40, 30, 8, 8);
            assertEquals(applications, backend.applications, "unchanged setters preserve their existing no-op behavior");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeBorderClientAreaResynchronizesControlAndCompositor() throws ReflectiveOperationException {
        org.junit.jupiter.api.Assumptions.assumeFalse("cocoa".equals(SWT.getPlatform()),
                "Unbordered Cocoa Canvas intentionally has no NSScrollView bezel");
        viewport.setSize(100, 100);
        Rectangle original = viewport.getClientArea();
        Button control = new Button(viewport, SWT.PUSH);
        Layer childLayer = viewport.addControlLayer("native edge child", control, 98, 10, 8, 18);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer nativeLayer = viewport.addImageLayer("native edge image", image, 98, 40);
        try {
            assertTrue(nativeLayer.setNative(true));
            assertEquals(1f, backend.lastProperties[3], "initial client region exposes the native layer");
            assertTrue(control.getVisible(), "initial client region exposes the child");

            int applications = backend.applications;
            viewport.setBorderRelief(SWT.SHADOW_IN);
            Rectangle inset = viewport.getClientArea();
            Field field = LayeredViewport.class.getDeclaredField("nativeClientGeometry");
            field.setAccessible(true);
            assertArrayEquals(new int[] {inset.x, inset.y, inset.width, inset.height},
                    (int[]) field.get(viewport), "native client geometry remains authoritative");
            boolean inside = inset.width > 98 && inset.height > 40;
            assertEquals(inside, control.getVisible(), "the OS client area determines native child culling");
            assertEquals(inside ? 1f : 0f, backend.lastProperties[3],
                    "the same client area determines Maya native-layer culling");
            if (!original.equals(inset)) {
                assertTrue(backend.applications > applications,
                        "a client-area change must submit a revised native frame");
            }

            applications = backend.applications;
            viewport.setBorderRelief(SWT.SHADOW_IN);
            assertEquals(applications, backend.applications,
                    "a no-op border request cannot issue another viewport frame");

            viewport.setBorderRelief(SWT.DEFAULT);
            assertEquals(original, viewport.getClientArea(),
                    "restoring the native frame restores the original viewport geometry");
            assertTrue(control.getVisible());
            assertEquals(1f, backend.lastProperties[3]);
            assertSame(control, viewport.getControl(childLayer),
                    "native child ownership is unchanged");
        } finally {
            if (!viewport.isDisposed()) {
                nativeLayer.remove();
                childLayer.remove();
            }
            image.dispose();
        }
    }

    @Test
    public void testNativeBorderReentrancyCoalescesViewportFrame() throws ReflectiveOperationException {
        org.junit.jupiter.api.Assumptions.assumeTrue("win32".equals(SWT.getPlatform()),
                "This contract requires a synchronous Win32 non-client frame transition");
        viewport.setSize(100, 100);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("reentrant native border", image, 98, 40);
        try {
            assertTrue(layer.setNative(true));
            int before = backend.applications;
            backend.onApply = () -> viewport.setBorderRelief(SWT.DEFAULT);
            viewport.setBorderRelief(SWT.SHADOW_IN);
            assertEquals(SWT.DEFAULT, viewport.getBorderRelief(),
                    "a nested native callback overrides the preceding request");
            Rectangle area = viewport.getClientArea();
            Field field = LayeredViewport.class.getDeclaredField("nativeClientGeometry");
            field.setAccessible(true);
            assertArrayEquals(new int[] {area.x, area.y, area.width, area.height},
                    (int[]) field.get(viewport),
                    "the final client rectangle is published after reentrant frame changes");
            assertTrue(backend.applications > before);
            assertEquals(1, backend.additions, "reentrant geometry must not duplicate native layers");
            assertEquals(0, backend.disposals, "reentrant frame synchronization retains composition ownership");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeZoomChangedReappliesViewportWithStableClientBounds()
            throws ReflectiveOperationException {
        viewport.setSize(100, 100);
        Button control = new Button(viewport, SWT.PUSH);
        Layer child = viewport.addControlLayer("zoom child", control, 20, 20, 40, 20);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer nativeLayer = viewport.addImageLayer("zoom native", image, 30, 30);
        try {
            assertTrue(nativeLayer.setNative(true));
            Rectangle clientBefore = viewport.getClientArea();
            Rectangle childBefore = control.getBounds();
            int frames = backend.applications;
            viewport.notifyListeners(SWT.ZoomChanged, new Event());
            assertEquals(clientBefore, viewport.getClientArea(),
                    "a synthetic DPI signal does not itself change logical native bounds");
            assertEquals(childBefore, control.getBounds(),
                    "native child remains at its logical projection");
            assertTrue(backend.applications > frames,
                    "a zoom event must reapply the native frame even with equal client bounds");
            assertSame(control, viewport.getControl(child));
            Field field = LayeredViewport.class.getDeclaredField("nativeClientGeometry");
            field.setAccessible(true);
            assertArrayEquals(new int[] {clientBefore.x, clientBefore.y,
                    clientBefore.width, clientBefore.height}, (int[]) field.get(viewport));
        } finally {
            if (!viewport.isDisposed()) {
                nativeLayer.remove();
                child.remove();
            }
            image.dispose();
        }
    }

    @Test
    public void testNativeCullingRespondsToViewportResize() throws ReflectiveOperationException {
        viewport.setSize(100, 100);
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("outside original width", image, 150, 10);
        try {
            assertTrue(layer.setNative(true));
            assertEquals(0f, backend.lastProperties[3]);
            int applications = backend.applications;
            viewport.setSize(200, 100);
            assertTrue(backend.applications > applications, "resize resubmits native visibility");
            assertArrayEquals(new float[] {150, 10, 1, 1}, backend.lastProperties);

            viewport.setSize(100, 100);
            assertEquals(0f, backend.lastProperties[3], "shrinking the view hides the layer again");
            layer.setWorldBounds(-4, -4, 8, 8);
            assertEquals(1f, backend.lastProperties[3], "partial intersection is visible");
            viewport.setSize(0, 0);
            assertEquals(0f, backend.lastProperties[3], "an empty viewport cannot contain visible pixels");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeExtremeCoordinatesSubmitFiniteHiddenProperties() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("long-coordinate native", image, 10, 10);
        try {
            assertTrue(layer.setNative(true));
            viewport.setOrigin(Long.MIN_VALUE, Long.MIN_VALUE);
            assertArrayEquals(new float[] {0, 0, 1, 0}, backend.lastProperties,
                    "overflowing projection is hidden without Infinity crossing JNI");
            viewport.setOrigin(Long.MAX_VALUE, Long.MAX_VALUE);
            assertArrayEquals(new float[] {0, 0, 1, 0}, backend.lastProperties);

            layer.setWorldBounds(Long.MAX_VALUE - 8, Long.MAX_VALUE - 8, 8, 8);
            viewport.setOrigin(Long.MIN_VALUE, Long.MIN_VALUE);
            assertArrayEquals(new float[] {0, 0, 1, 0}, backend.lastProperties);
            viewport.setOrigin(Long.MAX_VALUE - 8, Long.MAX_VALUE - 8);
            assertArrayEquals(new float[] {0, 0, 1, 1}, backend.lastProperties,
                    "a far layer becomes visible when its exact nearby origin is restored");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeFrameFailureRestoresPaintedPixelsAndDisposesOnce() throws ReflectiveOperationException {
        viewport.setSize(32, 32);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Image target = new Image(viewport.getDisplay(), 32, 32);
        GC gc = new GC(target);
        Layer layer = viewport.addImageLayer("native then painted", image, 0, 0);
        try {
            assertTrue(layer.setNative(true));
            paintInto(gc);
            assertEquals(new RGB(255, 255, 255), rgb(target.getImageData(), 2, 2),
                    "a native layer is absent from the painted backing store");
            backend.applyResult = -1;
            layer.setWorldBounds(1, 1, 8, 8);
            assertFalse(layer.isNative());
            assertEquals(0, backend.removals, "composition disposal owns all its layers");
            assertEquals(1, backend.disposals);
            int applications = backend.applications;
            assertFalse(layer.setNative(true), "failed native composition keeps the painted fallback");
            assertEquals(applications, backend.applications);

            paintInto(gc);
            ImageData painted = target.getImageData();
            assertEquals(new RGB(255, 0, 0), rgb(painted, 2, 2),
                    "native presentation failure damages and repaints the image");
            assertEquals(new RGB(255, 255, 255), rgb(painted, 0, 0));
            assertFalse(image.isDisposed(), "the borrowed image survives native teardown");
            disposedIntentionally = true;
            viewport.dispose();
            assertEquals(1, backend.disposals, "widget disposal must not free the failed composition twice");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            gc.dispose();
            target.dispose();
            image.dispose();
        }
    }

    @Test
    public void testNativeFailureNeedsExplicitRecoveryAndReuploadsEveryRequestedLayer()
            throws ReflectiveOperationException {
        viewport.setSize(32, 32);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("native recovery", image, 3, 4);
        try {
            assertTrue(layer.setNative(true));
            assertEquals(1, backend.additions);
            assertEquals(1, backend.uploads);
            backend.applyResult = -1; // model a DXGI/DComp submit failure
            layer.setOpacity(200);
            assertFalse(layer.isNative());
            assertEquals(1, backend.disposals);
            assertEquals(0, backend.creations);
            int before = backend.applications;
            assertFalse(layer.setNative(true), "failure retains the software fallback");
            assertEquals(before, backend.applications, "no automatic native retries");

            // A transient device reset has ended: the application explicitly
            // requests one new native composition on the SWT event thread.
            backend.applyResult = 0;
            backend.allowCreate = true;
            backend.createdComposition = 0; // a still-unavailable graphics adapter
            assertFalse(viewport.retryNativeComposition());
            assertEquals(1, backend.creations);
            assertEquals(1, backend.disposals);
            backend.createdComposition = 1;
            assertTrue(viewport.retryNativeComposition());
            assertTrue(layer.isNative());
            assertEquals(2, backend.creations, "zero-create attempt was not retried automatically");
            assertEquals(2, backend.additions, "new compositor owns a fresh layer");
            assertEquals(2, backend.uploads, "pixels come from the borrowed Image again");
            assertEquals(1, backend.disposals, "the old compositor was released once");
            assertTrue(viewport.retryNativeComposition(), "already healthy");
            assertEquals(2, backend.creations, "a healthy compositor is not recreated");
            assertFalse(image.isDisposed());

            layer.setNative(false);
            assertFalse(layer.isNative());
            assertFalse(viewport.retryNativeComposition(), "no native requests remain");
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeSceneRecoveryReprobesFeaturesBeforeRestoringProjection()
            throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        backend.supported = NativeLayers.AFFINE_2D | NativeLayers.LOCAL_RECT_CLIP;
        Image image = nativeTestImage(20, 20, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("scene recovery", image, 10, 20);
        try {
            viewport.setZoom(2);
            layer.setWorldClip(new WorldBounds(15, 25, 10, 10));
            assertTrue(layer.setNative(true));
            assertTrue(backend.lastScene);
            float[] before = backend.lastProperties.clone();
            backend.applyResult = -1;
            layer.setOpacity(200);
            assertFalse(layer.isNative());
            backend.applyResult = 0;
            backend.allowCreate = true;
            assertTrue(viewport.retryNativeComposition());
            assertTrue(layer.isNative());
            assertEquals(2, backend.capabilityQueries);
            before[2] = 200 / 255f;
            assertArrayEquals(before, backend.lastProperties,
                    "recovery restores the same source-pixel scale and clip");
            assertArrayEquals(new int[] {NativeLayers.SCENE_CLIP}, backend.lastFlags);
            backend.applyResult = -1;
            layer.setOpacity(180);
            backend.supported = 0;
            backend.applyResult = 0;
            assertTrue(viewport.retryNativeComposition(), "legacy composition is still available");
            assertEquals(3, backend.capabilityQueries);
            assertFalse(layer.isNative(), "a recovered legacy backend cannot inherit stale affine/clip permission");
            assertFalse(image.isDisposed());
        } finally {
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeRetryCannotReenterPaintOrNativeFrame() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_GREEN);
        Layer layer = viewport.addImageLayer("native reentrancy guard", image, 0, 0);
        try {
            assertTrue(layer.setNative(true));
            backend.onApply = () -> assertFalse(viewport.retryNativeComposition(),
                    "native callback cannot create/reuse compositor recursively");
            layer.setOpacity(190);
            assertEquals(0, backend.creations);
            assertTrue(layer.isNative());
            viewport.addPaintLayer("paint reentry probe", 0, 0, 16, 16,
                    (gc, context) -> assertFalse(viewport.retryNativeComposition(),
                            "a painter callback cannot recreate native resources"));
            Image target = new Image(viewport.getDisplay(), 32, 32);
            GC gc = new GC(target);
            try {
                paintInto(gc);
            } finally {
                gc.dispose();
                target.dispose();
            }
            viewport.beginUpdate();
            try {
                assertFalse(viewport.retryNativeComposition(),
                        "a batched model snapshot cannot reinitialize native resources");
            } finally {
                viewport.endUpdate();
            }
            assertTrue(layer.isNative());
        } finally {
            backend.onApply = null;
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testMovingRetainedLayerRefreshesItsDisjointPrefixPixels() {
        viewport.setSize(64, 64);
        solid("blue base", 0, 0, 64, 64, SWT.COLOR_BLUE);
        Layer red = solid("red retained", 0, 0, 10, 10, SWT.COLOR_RED);
        red.setRetained(true);
        solid("disjoint green", 50, 50, 8, 8, SWT.COLOR_GREEN);
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        try {
            paintInto(gc);
            assertEquals(system(SWT.COLOR_GREEN), rgb(target.getImageData(), 53, 53));
            red.moveTo(2);
            viewport.addPaintLayer("damage disjoint region", 50, 50, 8, 8, (drawing, context) -> { });
            paintInto(gc);
            assertEquals(system(SWT.COLOR_GREEN), rgb(target.getImageData(), 53, 53),
                    "the retained prefix must include the newly lower disjoint layer");
            red.moveTo(0);
            viewport.redraw(50, 50, 8, 8, false);
            paintInto(gc);
            assertEquals(system(SWT.COLOR_GREEN), rgb(target.getImageData(), 53, 53));
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    @Test
    public void testHitTesterChangingFixedAxisCannotAcceptItsOldProjection() {
        viewport.setOrigin(100, 0);
        Layer lower = viewport.addPaintLayer("lower", 100, 0, 20, 20, (gc, ctx) -> { });
        Layer moving = viewport.addPaintLayer("becomes fixed", 100, 0, 20, 20, (gc, ctx) -> { });
        moving.setHitTester((candidate, x, y) -> {
            assertEquals(105L, x);
            candidate.setFixedX(true);
            return true;
        });
        assertSame(lower, viewport.hitTest(5, 5),
                "the accepted target moved away from the pointer during its hit callback");
    }

    @Test
    public void testNativeReentryPreservesOuterArraysAndRestoresReuseAfterException() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer first = viewport.addImageLayer("outer first", image, 10, 10);
        Layer second = viewport.addImageLayer("outer second", image, 20, 20);
        try {
            assertTrue(first.setNative(true));
            assertTrue(second.setNative(true));
            long[] reusableHandles = backend.submittedHandleArray;
            float[] reusableProperties = backend.submittedPropertyArray;
            int applications = backend.applications;
            backend.onApply = () -> {
                long[] outerHandles = backend.submittedHandleArray;
                float[] outerProperties = backend.submittedPropertyArray;
                long[] expectedHandles = outerHandles.clone();
                float[] expectedProperties = outerProperties.clone();
                first.moveTo(1);
                second.setOpacity(128);
                assertNotSame(outerHandles, backend.submittedHandleArray,
                        "a nested frame has its own active handle prefix");
                assertNotSame(outerProperties, backend.submittedPropertyArray,
                        "a nested frame has its own active property prefix");
                assertArrayEquals(expectedHandles, outerHandles, "the outer backend still owns its input");
                assertArrayEquals(expectedProperties, outerProperties, "nested changes cannot rewrite that input");
            };
            first.setOpacity(192);
            assertEquals(applications + 3, backend.applications, "one outer and two nested frames");
            first.setOpacity(160);
            assertSame(reusableHandles, backend.submittedHandleArray);
            assertSame(reusableProperties, backend.submittedPropertyArray);

            backend.onApply = () -> { throw new IllegalStateException("backend callback failure"); };
            assertThrows(IllegalStateException.class, () -> first.setOpacity(120));
            first.setOpacity(96);
            assertSame(reusableHandles, backend.submittedHandleArray,
                    "an exceptional exit must not leave future ordinary frames marked nested");
            assertSame(reusableProperties, backend.submittedPropertyArray);
        } finally {
            backend.onApply = null;
            if (!viewport.isDisposed()) {
                first.remove();
                second.remove();
            }
            image.dispose();
        }
    }

    @Test
    public void testOuterNativeFailureClearsLayersAddedByItsCallback() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer first = viewport.addImageLayer("outer native", image, 0, 0);
        Layer[] added = new Layer[1];
        try {
            assertTrue(first.setNative(true));
            backend.onApply = () -> {
                added[0] = viewport.addImageLayer("nested native", image, 20, 0);
                assertTrue(added[0].setNative(true));
                backend.applyResult = -1; // only the still-active outer frame now fails
            };
            first.setOpacity(128);
            assertFalse(first.isNative());
            assertNotNull(added[0]);
            assertFalse(added[0].isNative(), "failure also detaches native handles absent from the outer snapshot");
            assertEquals(1, backend.disposals);
            assertEquals(0, backend.removals, "one composition teardown releases its entire live set");
            assertTrue(backend.pixelsPerLayer.isEmpty());
            assertFalse(image.isDisposed());
        } finally {
            backend.onApply = null;
            if (!viewport.isDisposed()) {
                first.remove();
                if (added[0] != null) added[0].remove();
            }
            image.dispose();
        }
    }

    @Test
    public void testNestedNativeFailureDoesNotDisposeTheCompositionTwice() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("nested failure", image, 10, 10);
        try {
            assertTrue(layer.setNative(true));
            backend.onApply = () -> {
                backend.applyResult = -1;
                layer.setOpacity(64);
            };
            layer.setOpacity(128);
            assertFalse(layer.isNative());
            assertEquals(1, backend.disposals, "the outer failed result belongs to an already detached composition");
            assertEquals(0, backend.removals);
            assertFalse(image.isDisposed());
            disposedIntentionally = true;
            viewport.dispose();
            assertEquals(1, backend.disposals);
        } finally {
            backend.onApply = null;
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testNativeApplyCallbackMayDisposeViewportWithoutAnotherTeardown() throws ReflectiveOperationException {
        RecordingNativeLayers backend = installRecordingNativeLayers();
        Image image = nativeTestImage(8, 8, SWT.COLOR_RED);
        Layer layer = viewport.addImageLayer("dispose in native callback", image, 10, 10);
        try {
            assertTrue(layer.setNative(true));
            backend.applyResult = -1;
            backend.onApply = () -> {
                disposedIntentionally = true;
                viewport.dispose();
            };
            layer.setOpacity(128);
            assertTrue(viewport.isDisposed());
            assertEquals(1, backend.disposals, "the returning apply cannot dispose a widget's old composition again");
            assertEquals(0, backend.removals);
            assertFalse(image.isDisposed(), "native teardown does not own the image");
        } finally {
            backend.onApply = null;
            if (!viewport.isDisposed()) layer.remove();
            image.dispose();
        }
    }

    @Test
    public void testBlendedRowsPreservePixelsWithFixedAxisZoomOpacityAndClip() throws ReflectiveOperationException {
        viewport.setSize(64, 64);
        viewport.setOrigin(7, 5);
        viewport.setZoom(2);
        solid("blue beneath blend", -100, -100, 200, 200, SWT.COLOR_BLUE);
        Image red = nativeTestImage(8, 8, SWT.COLOR_RED);
        Image white = nativeTestImage(8, 8, SWT.COLOR_WHITE);
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC gc = new GC(target);
        Layer layer = viewport.addImageLayer("mixed-axis blended image", red, 20, 20);
        layer.setFixedX(true);
        try {
            layer.setBlendMode(LayeredViewport.BLEND_MULTIPLY);
            paintInto(gc);
            assertEquals(new RGB(0, 0, 0), rgb(target.getImageData(), 21, 31));

            layer.setBlendMode(LayeredViewport.BLEND_SCREEN);
            paintInto(gc);
            ImageData screened = target.getImageData();
            assertEquals(new RGB(255, 0, 255), rgb(screened, 21, 31));
            assertEquals(system(SWT.COLOR_BLUE), rgb(screened, 29, 31), "fixed X retains the image width");
            assertEquals(system(SWT.COLOR_BLUE), rgb(screened, 21, 47), "world Y follows pan and zoom");

            layer.setOpacity(128);
            paintInto(gc);
            assertEquals(new RGB(128, 0, 255), rgb(target.getImageData(), 21, 31));

            layer.setOpacity(255);
            layer.setWorldClip(new WorldBounds(22, 22, 4, 4));
            paintInto(gc);
            ImageData clipped = target.getImageData();
            assertEquals(new RGB(255, 0, 255), rgb(clipped, 23, 35));
            assertEquals(system(SWT.COLOR_BLUE), rgb(clipped, 21, 35));
            assertEquals(system(SWT.COLOR_BLUE), rgb(clipped, 23, 33));

            layer.setImage(white);
            layer.setBlendMode(LayeredViewport.BLEND_DIFFERENCE);
            paintInto(gc);
            assertEquals(new RGB(255, 255, 0), rgb(target.getImageData(), 23, 35));
        } finally {
            gc.dispose();
            target.dispose();
            if (!viewport.isDisposed()) layer.remove();
            red.dispose();
            white.dispose();
        }
    }

    @Test
    public void testProjectionRejectsUnsupportedScalesOnEitherAxis() {
        double minimum = 1.0 / 4096.0, maximum = 4096.0;
        for (double value : new double[] {0, -1, Double.MIN_VALUE, Double.MAX_VALUE,
                Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Math.nextDown(minimum), Math.nextUp(maximum)}) {
            assertThrows(IllegalArgumentException.class, () -> new LayeredViewport.Projection(0, 0, value, 1));
            assertThrows(IllegalArgumentException.class, () -> new LayeredViewport.Projection(0, 0, 1, value));
        }
        LayeredViewport.Projection limits = new LayeredViewport.Projection(Long.MIN_VALUE, Long.MAX_VALUE,
                minimum, maximum);
        assertEquals(new WorldPoint(Long.MIN_VALUE + 4096, Long.MAX_VALUE - 1), limits.toWorld(1, -1));
        LayeredViewport.Projection saturated = new LayeredViewport.Projection(Long.MAX_VALUE, Long.MIN_VALUE, 1, 1);
        assertEquals(new WorldPoint(Long.MAX_VALUE, Long.MIN_VALUE), saturated.toWorld(1, -1));
    }




    @Test
    public void testLayerGroupHasAtomicZOrderAndLocalChildren() {
        Layer below = viewport.addPaintLayer("below", 0, 0, 300, 240, (gc, ctx) -> { });
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("group", 30, 40, 100, 80);
        LayeredViewport.LayerGroup.Child first = group.addPaintChild(
                "first", 10, 10, 50, 40, (gc, ctx) -> { });
        LayeredViewport.LayerGroup.Child second = group.addPaintChild(
                "second", 15, 15, 25, 25, (gc, ctx) -> { });
        assertEquals(2, group.getChildren().length);
        assertSame(second, group.hitTestChild(50, 60));
        assertSame(group.getLayer(), viewport.hitTest(50, 60));
        assertEquals(1, group.getLayer().getZIndex());

        first.moveTo(1);
        assertEquals(1, first.getZIndex());
        assertSame(first, group.hitTestChild(50, 60));
        first.setLocked(true);
        assertSame(second, group.hitTestChild(50, 60));
        second.setVisible(false);
        assertNull(group.hitTestChild(50, 60));
        assertSame(below, viewport.hitTest(50, 60));
        first.setLocked(false);
        assertSame(first, group.hitTestChild(50, 60));
        second.setVisible(true);

        group.getLayer().setWorldBounds(130, 90, 100, 80);
        assertNull(group.hitTestChild(50, 60));
        assertSame(first, group.hitTestChild(150, 110));
        assertSame(group.getLayer(), viewport.hitTest(150, 110));
        group.getLayer().moveTo(0);
        assertSame(below, viewport.hitTest(150, 110));
        group.getLayer().moveTo(1);
        assertSame(group.getLayer(), viewport.hitTest(150, 110));
    }

    @Test
    public void testGroupChildrenHandlesAndClipping() {
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("group", 20, 20, 50, 50);
        LayeredViewport.LayerGroup.Child outside = group.addPaintChild(
                "outside", 60, 0, 10, 10, (gc, ctx) -> { });
        assertNull(group.hitTestChild(85, 25), "a child cannot pick beyond its parent bounds");
        LayeredViewport.LayerGroup.Child inside = group.addPaintChild(
                "inside", 10, 15, 12, 12, (gc, ctx) -> { });
        assertSame(inside, group.hitTestChild(35, 40));
        inside.setHitTester((child, x, y) -> x >= 15);
        assertNull(group.hitTestChild(33, 40));
        assertSame(inside, group.hitTestChild(38, 40));
        LayeredViewport.LayerGroup.Child[] snapshot = group.getChildren();
        snapshot[0] = inside;
        assertSame(outside, group.getChildren()[0]);
        assertThrows(IllegalArgumentException.class, () -> inside.setLocalBounds(0, 0, -1, 10));

        long id = inside.getId();
        inside.remove();
        assertThrows(IllegalArgumentException.class, inside::getId);
        LayeredViewport.LayerGroup.Child newer = group.addPaintChild(
                "new", 0, 0, 10, 10, (gc, ctx) -> { });
        assertTrue(newer.getId() > id);
        group.getLayer().remove();
        assertThrows(IllegalArgumentException.class, newer::getId);
    }

    @Test
    public void testGroupOpacityIsAppliedAfterItsChildrenOverlap() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("overlay", 0, 0, 140, 120);
        group.addPaintChild("red", 0, 0, 100, 90, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
        });
        group.addPaintChild("blue", 25, 20, 80, 70, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(ctx.clip());
        });
        group.getLayer().setOpacity(128);
        show();
        ImageData shown = pixels();
        RGB red = rgb(shown, 10, 10);
        RGB blue = rgb(shown, 40, 40);
        assertTrue(red.red > 250 && Math.abs(red.green - 127) <= 4 && Math.abs(red.blue - 127) <= 4,
                "the group opacity halves red above white");
        assertTrue(Math.abs(blue.red - 127) <= 4 && Math.abs(blue.green - 127) <= 4 && blue.blue > 250,
                "the overlapping blue child replaces red before opacity is applied once");
        assertEquals(system(SWT.COLOR_WHITE), rgb(shown, 130, 110));
        paintAfter(() -> group.getLayer().setOpacity(255));
        assertEquals(system(SWT.COLOR_BLUE), rgb(pixels(), 40, 40));
    }

    @Test
    public void testLayerGroupIndexAndReentrantHitTest() {
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("many", 0, 0, 250, 100);
        for (int i = 0; i < 64; i++) {
            group.addPaintChild("small-" + i, i * 3, 20, 2, 6, (gc, ctx) -> { });
        }
        LayeredViewport.LayerGroup.Child over = group.addPaintChild(
                "over", 10, 10, 60, 30, (gc, ctx) -> { });
        assertSame(over, group.hitTestChild(16, 23));
        // A hit-test callback may move itself: recheck captured candidates
        // instead of returning a stale hit or corrupting the packed Z array.
        over.setHitTester((child, x, y) -> {
            over.setLocalBounds(100, 40, 20, 20);
            assertNotSame(over, group.hitTestChild(16, 23));
            return true;
        });
        LayeredViewport.LayerGroup.Child found = group.hitTestChild(16, 23);
        assertNotNull(found);
        assertNotSame(over, found);
        assertEquals("small-5", found.getName());
        assertSame(over, group.hitTestChild(110, 45));
    }

    @Test
    public void testPinnedLayerGroupAndChildPainting() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("header", 0, 0, 150, 30);
        group.addPaintChild("blue header", 0, 0, 150, 30, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(ctx.clip());
        });
        group.getLayer().setPinned(SWT.VERTICAL);
        show();
        panAndPaint(0, 200);
        assertSame(group.getLayer(), viewport.hitTest(10, 10));
        assertEquals("blue header", group.hitTestChild(10, 10).getName());
        assertEquals(system(SWT.COLOR_BLUE), rgb(pixels(), 10, 10));
    }

    @Test
    public void testGroupRasterChildZoomAndBorrowedImageOwnership() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Image raster = new Image(viewport.getDisplay(), 8, 8);
        try {
            GC brush = new GC(raster);
            try {
                brush.setBackground(brush.getDevice().getSystemColor(SWT.COLOR_GREEN));
                brush.fillRectangle(0, 0, 8, 8);
            } finally {
                brush.dispose();
            }
            LayeredViewport.LayerGroup group = viewport.addLayerGroup("raster", 20, 30, 100, 100);
            LayeredViewport.LayerGroup.Child child = group.addImageChild("borrowed", raster, 10, 10);
            assertSame(child, group.hitTestChild(31, 41));
            show();
            assertEquals(system(SWT.COLOR_GREEN), rgb(pixels(), 31, 41));
            paintAfter(() -> viewport.setZoom(2.0));
            assertSame(child, group.hitTestChild(61, 81));
            assertEquals(system(SWT.COLOR_GREEN), rgb(pixels(), 61, 81));
            paintAfter(() -> group.getLayer().setWorldClip(new WorldBounds(0, 0, 30, 30)));
            assertEquals(system(SWT.COLOR_WHITE), rgb(pixels(), 61, 81));
            group.getLayer().setWorldClip(null);
            group.getLayer().remove();
            assertFalse(raster.isDisposed(), "group only borrows Image data");
        } finally {
            raster.dispose();
        }
    }

    @Test
    public void testGroupLocalCoordinatesAtLargeWorldOrigins() {
        long large = 1L << 55;
        viewport.setOrigin(large, large);
        LayeredViewport.LayerGroup group =
                viewport.addLayerGroup("huge", large + 10, large + 15, 60, 60);
        LayeredViewport.LayerGroup.Child child = group.addPaintChild(
                "near", 5, 5, 20, 20, (gc, ctx) -> { });
        assertSame(group.getLayer(), viewport.hitTest(16, 21));
        assertSame(child, group.hitTestChild(16, 21));
        assertNull(group.hitTestChild(14, 19));
    }

    @Test
    public void testLongMinimumPanDoesNotReuseRetainedPixels() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        viewport.setOrigin(-1, 0);
        Layer scene = viewport.addPaintLayer("old-world", -1, 0, 150, 150, (gc, ctx) -> {
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle(ctx.clip());
        });
        scene.setRetained(true);
        show();
        assertEquals(new RGB(0, 0, 0), rgb(pixels(), 10, 10));
        // -1 - Long.MAX_VALUE is exactly Long.MIN_VALUE without subtraction
        // overflow. An abs-based guard wrongly admits a 0px store translation.
        viewport.setOrigin(Long.MAX_VALUE, 0);
        viewport.update();
        SwtTestUtil.processEvents();
        assertEquals(new RGB(255, 255, 255), rgb(pixels(), 10, 10));
    }

    @Test
    public void testFixedAxisGroupsKeepRasterClipHitAndProjectionInAgreement() {
        viewport.setSize(200, 160);
        viewport.setOrigin(10, 10);
        viewport.setZoom(2);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Image raster = new Image(viewport.getDisplay(), 8, 6);
        Image target = new Image(viewport.getDisplay(), 200, 160);
        GC brush = new GC(raster), output = new GC(target);
        try {
            brush.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_RED));
            brush.fillRectangle(0, 0, 8, 6);
            for (boolean fixedX : new boolean[] { true, false }) {
                LayeredViewport.LayerGroup group = viewport.addLayerGroup("mixed", 30, 40, 80, 60);
                Layer root = group.getLayer();
                root.setFixedX(fixedX);
                root.setFixedY(!fixedX);
                LayeredViewport.LayerGroup.Child child = group.addImageChild("raster", raster, 5, 7);
                // Select through the group's spatial-index branch, including distant local children.
                for (int i = 0; i < 40; i++) {
                    group.addPaintChild("distant " + i, 800 + i * 4, 800, 2, 2,
                            (gc, ctx) -> fail("a distant child must be culled"));
                }
                root.setWorldClip(new WorldBounds(36, 48, 4, 3));
                int scaleX = fixedX ? 1 : 2, scaleY = fixedX ? 2 : 1;
                int baseX = fixedX ? 30 : 40, baseY = fixedX ? 60 : 40;
                int left = baseX + 6 * scaleX, top = baseY + 8 * scaleY;
                LayeredViewport.GroupProjection projection = group.getProjection();
                assertEquals(new org.eclipse.swt.graphics.Point(left, top), projection.toScreen(6, 8));
                assertEquals(new WorldPoint(6, 8), projection.toLocal(left, top));
                assertEquals(projection, child.getProjection());
                paintInto(output);
                ImageData shown = target.getImageData();
                assertEquals(system(SWT.COLOR_RED), rgb(shown, left, top));
                assertEquals(system(SWT.COLOR_RED), rgb(shown, left + 4 * scaleX - 1, top + 3 * scaleY - 1));
                assertEquals(system(SWT.COLOR_WHITE), rgb(shown, left - 1, top));
                assertEquals(system(SWT.COLOR_WHITE), rgb(shown, left + 4 * scaleX, top));
                assertEquals(system(SWT.COLOR_WHITE), rgb(shown, left, top - 1));
                assertEquals(system(SWT.COLOR_WHITE), rgb(shown, left, top + 3 * scaleY));
                assertSame(root, viewport.hitTest(left, top));
                assertSame(child, group.hitTestChild(left, top));
                assertNull(group.hitTestChild(left - 1, top));
                assertNull(group.hitTestChild(left, top + 3 * scaleY));
                root.remove();
                assertFalse(raster.isDisposed(), "fixed groups continue to borrow their images");
            }
        } finally {
            output.dispose();
            brush.dispose();
            target.dispose();
            raster.dispose();
        }
    }

    @Test
    public void testGroupProjectionSurvivesRootMutationAndNestedPainting() {
        viewport.setSize(64, 64);
        viewport.setZoom(2);
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("snapshot", 5, 6, 20, 20);
        group.getLayer().setFixedX(true);
        LayeredViewport.GroupProjection[] captured = { null };
        List<String> frames = new ArrayList<>();
        boolean[] changed = { false };
        Image target = new Image(viewport.getDisplay(), 64, 64);
        Image nestedTarget = new Image(viewport.getDisplay(), 64, 64);
        GC output = new GC(target), nested = new GC(nestedTarget);
        try {
            group.addPaintChild("first", 0, 0, 4, 4, (gc, ctx) -> {
                LayeredViewport.GroupProjection projection = ctx.child().getProjection();
                frames.add("first " + projection.scaleX() + "," + projection.scaleY());
                if (!changed[0]) {
                    changed[0] = true;
                    captured[0] = projection;
                    group.getLayer().setFixedX(false);
                    composeInto(nested, nestedTarget, new Rectangle(0, 0, 64, 64));
                    assertSame(projection, ctx.child().getProjection(), "the outer frame resumes after nested paint");
                    assertEquals(new org.eclipse.swt.graphics.Point(7, 18), projection.toScreen(2, 3));
                }
                gc.fillRectangle(ctx.clip());
            });
            group.addPaintChild("second", 10, 5, 4, 4, (gc, ctx) -> {
                LayeredViewport.GroupProjection projection = ctx.child().getProjection();
                frames.add("second " + projection.scaleX() + "," + projection.scaleY());
                assertSame(projection, group.getProjection());
                gc.fillRectangle(ctx.clip());
            });
            paintInto(output);
            assertEquals(List.of("first 1.0,2.0", "first 2.0,2.0", "second 2.0,2.0", "second 1.0,2.0"), frames);
            assertEquals(new org.eclipse.swt.graphics.Point(7, 18), captured[0].toScreen(2, 3),
                    "retaining an immutable capture does not retain mutable flags");
            assertEquals(new org.eclipse.swt.graphics.Point(14, 18), group.getProjection().toScreen(2, 3),
                    "outside paint, a new capture sees the current root");
        } finally {
            nested.dispose();
            output.dispose();
            nestedTarget.dispose();
            target.dispose();
        }
    }

    @Test
    public void testRetainedFixedGroupKeepsChildDamageRequestedInsidePainting() {
        viewport.setSize(64, 64);
        viewport.setZoom(2);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("retained group", 10, 10, 50, 25);
        Layer root = group.getLayer();
        root.setFixedX(true);
        root.setRetained(true);
        boolean[] changed = { false };
        group.addPaintChild("red", 0, 0, 8, 8, (gc, ctx) -> {
            if (!changed[0]) {
                changed[0] = true;
                group.addPaintChild("added", 40, 15, 4, 4, (nextGc, nextContext) -> {
                    nextGc.setBackground(nextGc.getDevice().getSystemColor(SWT.COLOR_GREEN));
                    nextGc.fillRectangle(nextContext.clip());
                });
                root.setRetained(false);
                assertFalse(gc.isDisposed(), "retention changes keep the active group GC alive");
            }
            gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
            gc.fillRectangle(ctx.clip());
        });
        Image target = new Image(viewport.getDisplay(), 64, 64);
        GC output = new GC(target);
        try {
            paintInto(output);
            assertEquals(system(SWT.COLOR_RED), rgb(target.getImageData(), 12, 22));
            paintInto(output);
            assertEquals(system(SWT.COLOR_GREEN), rgb(target.getImageData(), 51, 51),
                    "new child damage survives the original retained composition");
            assertEquals("added", group.hitTestChild(51, 51).getName());
            assertFalse(root.isRetained());
        } finally {
            output.dispose();
            target.dispose();
        }
    }

    @Test
    public void testGroupProjectionKeepsTheExistingContextRecordContract() {
        assertEquals(5, LayeredViewport.GroupPaintContext.class.getRecordComponents().length);
        LayeredViewport.GroupPaintContext context = new LayeredViewport.GroupPaintContext(
                null, new Rectangle(0, 0, 20, 20), 10, 20, 2);
        assertEquals(new org.eclipse.swt.graphics.Point(16, 28), context.toScreen(3, 4));
        for (double invalid : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY, 8192 }) {
            assertThrows(IllegalArgumentException.class, () -> new LayeredViewport.GroupProjection(0, 0, invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new LayeredViewport.GroupProjection(0, 0, 1, invalid));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new LayeredViewport.GroupProjection(Double.NaN, 0, 1, 1));
    }

    @Test
    public void testFixedAxisBlendKeepsPixelsAcrossFullAndPartialNativeBatches() {
        viewport.setSize(160, 240);
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_BLUE));
        viewport.setOrigin(5000, 10);
        viewport.setZoom(2);
        ImageData source = new ImageData(120, 100, 24,
                new org.eclipse.swt.graphics.PaletteData(0xFF0000, 0xFF00, 0xFF));
        for (int y = 0; y < source.height; y++) {
            for (int x = 0; x < source.width; x++) {
                int red = (x * 7 + y * 11) & 255, green = (x * 13 + y * 3) & 255;
                source.setPixel(x, y, red << 16 | green << 8 | (x + y) & 255);
            }
        }
        Image raster = new Image(viewport.getDisplay(), source);
        Image target = new Image(viewport.getDisplay(), 160, 240);
        GC output = new GC(target);
        try {
            Layer layer = viewport.addImageLayer("batched fixed", raster, 10, 20);
            layer.setFixedX(true);
            layer.setBlendMode(LayeredViewport.BLEND_SCREEN);
            layer.setOpacity(128);
            // The projected 120x200 tile forms a 16,320-pixel batch and a 7,680-pixel tail.
            // A matched optional JNI backend exercises the first; Java also covers the tail.
            paintInto(output);
            ImageData shown = target.getImageData();
            for (int y = 0; y < shown.height; y++) {
                for (int x = 0; x < shown.width; x++) {
                    RGB expected = system(SWT.COLOR_BLUE);
                    if (x >= 10 && x < 130 && y >= 20 && y < 220) {
                        int sx = x - 10, sy = (y - 20) / 2;
                        int red = (sx * 7 + sy * 11) & 255, green = (sx * 13 + sy * 3) & 255;
                        expected = new RGB((red * 128 + 127) / 255, (green * 128 + 127) / 255, 255);
                    }
                    assertEquals(expected, rgb(shown, x, y), "batched pixel " + x + "," + y);
                }
            }
        } finally {
            output.dispose();
            target.dispose();
            raster.dispose();
        }
    }

    @Test
    public void testFilteredGroupHitsRecheckFixedAxisChanges() {
        viewport.setOrigin(100, 0);
        viewport.setZoom(2);
        Layer body = viewport.addPaintLayer("body", 0, 0, 300, 100, (gc, ctx) -> { });
        LayeredViewport.LayerGroup group = viewport.addLayerGroup("fixed target", 10, 0, 30, 30);
        Layer root = group.getLayer();
        root.setFixedX(true);
        LayeredViewport.LayerGroup.Child child = group.addPaintChild("child", 0, 0, 20, 20, (gc, ctx) -> { });
        assertSame(root, viewport.hitTest(12, 10));
        assertSame(child, group.hitTestChild(12, 10));
        assertSame(body, viewport.hitTest(12, 10, candidate -> {
            if (candidate == root) root.setFixedX(false);
            return true;
        }), "a filter cannot return a fixed group that moved outside the point");
        root.setFixedX(true);
        assertNull(group.hitTestChild(12, 10, candidate -> {
            root.setFixedX(false);
            return true;
        }), "child filters recheck the parent's current per-axis projection");
        assertEquals(1, root.getZIndex(), "filtering never changes the live Z rank");
    }

    @Test
    public void testParallaxPlanesKeepPaintHitAndPointerOrderAcrossBlitPans() {
        viewport.setBackground(viewport.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        Layer rows = solid("body", 0, 0, 4000, 4000, SWT.COLOR_BLUE);
        Layer parallax = solid("parallax", 140, 40, 40, 40, SWT.COLOR_MAGENTA);
        parallax.setScrollRates(128, 128);
        Layer header = solid("header", 0, 0, 300, 20, SWT.COLOR_RED);
        header.setPinned(SWT.VERTICAL);
        show();
        assertEquals(128, parallax.getScrollRateX());
        assertEquals(128, parallax.getScrollRateY());
        for (int[] origin : new int[][] {{0, 0}, {20, 20}, {60, 40},
                {80, 30}, {0, 0}}) {
            panAndPaint(origin[0], origin[1]);
            int x = 145 - origin[0] / 2;
            int y = 45 - origin[1] / 2;
            assertEquals(system(SWT.COLOR_MAGENTA), rgb(pixels(), x, y),
                    "parallax layer at origin " + origin[0] + "," + origin[1]);
            assertEquals(system(SWT.COLOR_RED), rgb(pixels(), 20, 5),
                    "pinned header stays above independently scrolling body");
            assertSame(parallax, viewport.hitTest(x, y));
            assertSame(header, viewport.hitTest(20, 5));
            assertSameAsAFullRecomposition();
        }
        List<LayerPointerEvent> events = new ArrayList<>();
        parallax.setPointerListener(events::add);
        viewport.setOrigin(60, 40);
        viewport.update();
        pointer(SWT.MouseMove, 115, 25, 0);
        assertFalse(events.isEmpty(), "parallax layer receives routed pointer event");
        assertEquals(145, events.get(0).worldX());
        assertEquals(45, events.get(0).worldY());
        assertSame(parallax, events.get(0).layer());
        assertSame(rows, viewport.hitTest(100, 80));
    }

    @Test
    public void testScrollRatesReverseFixedAndPinnedPrecedence() {
        Layer layer = solid("effect", 140, 40, 40, 40, SWT.COLOR_MAGENTA);
        assertEquals(256, layer.getScrollRateX());
        assertEquals(256, layer.getScrollRateY());
        assertThrows(IllegalArgumentException.class, () -> layer.setScrollRates(4097, 256));
        assertThrows(IllegalArgumentException.class, () -> layer.setScrollRates(0, -4097));
        assertEquals(256, layer.getScrollRateX());
        layer.setScrollRates(0, -256);
        viewport.setOrigin(10, 20);
        assertSame(layer, viewport.hitTest(145, 65), "fixed X and reverse Y");
        layer.setPinned(SWT.VERTICAL);
        assertSame(layer, viewport.hitTest(145, 45), "native pin overrides reverse Y");
        layer.setPinned(SWT.NONE);
        assertSame(layer, viewport.hitTest(145, 65), "rate reactivates after unpin");
        layer.setScrollRates(256, 256);
        assertSame(layer, viewport.hitTest(135, 25), "ordinary world scrolling restored");
    }
}
