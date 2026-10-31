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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (text leaf L13)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.M3GlyphRuns;
import org.eclipse.swt.internal.render.m3.gc.M3Resources;
import org.eclipse.swt.internal.render.m3.gc.M3SurfaceAdapter;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceProps;

/**
 * Headless, on a real Skija raster surface: TextLayout on an M3 GC (text leaf L13). TEXT_FALLBACK_SAFE: no white box over
 * what lies under the layout (the Java editor's dark background), the GC foreground reaches default runs (punctuation),
 * opaque run backgrounds stay opaque, the GC alpha is applied once. GLYPH_RUN_SINK/DRAW, RUN_STYLE, RUN_COVERAGE: through a
 * GCExtension every run is one Skia text blob (runs recorded == blobs drawn, 0 fallbacks) for LTR, RTL and fallback-font
 * fixtures, placed in the stock GC's columns; underline, strikeout and selection need no fallback.
 */
public class Test_org_eclipse_swt_m3gc_TextLayoutRuns {
	static final int DARK = 0xFF202020, W = 300, H = 80;
	Surface real;
	M3ExternalGC gc;
	Font font;
	TextLayout layout;
	M3Resources resources;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@BeforeEach
	void setUp() {
		real = Surface.makeRaster(ImageInfo.makeN32Premul(W, H), 0, new SurfaceProps(false, PixelGeometry.RGB_H));
		real.getCanvas().clear(DARK);
		font = new Font(Display.getDefault(), "Consolas", 10, SWT.NORMAL);
		M3GcFixture f = new M3GcFixture(DPIUtil.getDeviceZoom());
		f.ext.surface = new M3SurfaceAdapter(real);
		resources = new M3Resources(null, f.ext);
		resources.setFont(font);
		resources.setForeground(new Color(0xDD, 0xDD, 0xDD));
		resources.setBackground(new Color(0x20, 0x20, 0x20));
		f.ext.resources = resources;
		gc = new M3ExternalGC(f.ext, SWT.NONE);
		layout = new TextLayout(Display.getDefault());
		layout.setFont(font);
	}

	@AfterEach
	void tearDown() {
		System.clearProperty("swt.m3.text");
		layout.dispose();
		real.close();
		font.dispose();
	}

	int pixel(int x, int y) {
		try (var bitmap = new io.github.humbleui.skija.Bitmap()) {
			bitmap.allocN32Pixels(1, 1);
			real.readPixels(bitmap, x, y);
			return bitmap.getColor(0, 0) & 0xFFFFFF;
		}
	}

	/* pixels of the band [0, w) x [0, h) whose largest channel is above mid grey */
	long bright(int w, int h) {
		return IntStream.range(0, h).flatMap(y -> IntStream.range(0, w).map(x -> pixel(x, y)))
				.filter(c -> Math.max(c >> 16 & 0xFF, Math.max(c >> 8 & 0xFF, c & 0xFF)) > 0x80).count();
	}

	void draw(int x, int y) {
		gc.textLayoutDraw(layout, null, x, y, -1, -1, null, null, 0);
	}

	@Test
	public void testNoWhiteBoxOverTheBackground() {
		layout.setText("public class Hello {");
		draw(0, 0);
		Rectangle bounds = layout.getBounds();
		long white = IntStream.range(0, bounds.height).flatMap(y -> IntStream.range(0, bounds.width).map(x -> pixel(x, y)))
				.filter(c -> c == 0xFFFFFF).count();
		assertEquals(0, white, "white pixels inside the layout bounds");
		assertEquals(DARK & 0xFFFFFF, pixel(bounds.width - 1, bounds.height - 1), "background under the layout's corner");
	}

	@Test
	public void testPunctuationInTheGcForeground() {
		layout.setText("{ ( ) [ ] ; }");
		draw(0, 0);
		Rectangle bounds = layout.getBounds();
		assertTrue(bright(bounds.width, bounds.height) > 20, "punctuation drawn in the light GC foreground");
	}

	@Test
	public void testRunBackgroundStaysOpaque() {
		layout.setText("abc def");
		layout.setStyle(new TextStyle(null, null, new Color(255, 0, 0)), 4, 6);
		draw(0, 0);
		Rectangle run = layout.getBounds(4, 6);
		assertEquals(0xFF0000, pixel(run.x + 1, run.y + 1), "style background of 'def'");
	}

	@Test
	public void testAlphaAppliedOnce() {
		layout.setText("abc");
		layout.setStyle(new TextStyle(null, null, new Color(255, 255, 255)), 0, 2);
		gc.setAlpha(128);
		draw(0, 0);
		int c = pixel(1, 1) & 0xFF;
		int expected = (0xFF * 128 + 0x20 * 127) / 255;
		assertTrue(Math.abs(c - expected) <= 3, "white at alpha 128 over 0x20: " + c + " expected " + expected);
	}

	/* L13 GLYPH_RUN_SINK/DRAW: layout drawn through a GCExtension (TextLayout dispatches to the M3 run sink); route forced to runs */
	long[] drawRuns(String text, int x, int y) {
		System.setProperty("swt.m3.text", "runs");
		layout.setText(text);
		long runs = M3GlyphRuns.runs(), blobs = M3GlyphRuns.blobs(), fallbacks = M3GlyphRuns.fallbacks();
		layout.draw(new GCExtension(gc), x, y);
		return new long[] { M3GlyphRuns.runs() - runs, M3GlyphRuns.blobs() - blobs, M3GlyphRuns.fallbacks() - fallbacks };
	}

	void assertAllRuns(long[] counts, String what) {
		assertTrue(counts[0] > 0, what + ": runs recorded " + counts[0]);
		assertEquals(counts[0], counts[1], what + ": runs recorded == blobs drawn");
		assertEquals(0, counts[2], what + ": fallbacks");
	}

	/* ink per column (max channel < 0x80) of the M3 surface over [0, w) x [0, h) */
	int[] inkColumns(int w, int h) {
		return IntStream.range(0, w).map(x -> (int) IntStream.range(0, h).map(y -> pixel(x, y))
				.filter(c -> Math.max(c >> 16 & 0xFF, Math.max(c >> 8 & 0xFF, c & 0xFF)) < 0x80).count()).toArray();
	}

	/* the same layout drawn by the stock GC on a white image, ink per column */
	int[] stockInkColumns(int w, int h) {
		Image image = new Image(Display.getDefault(), w, h);
		GC stock = new GC(image);
		stock.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
		layout.draw(stock, 0, 0);
		stock.dispose();
		ImageData data = image.getImageData();
		image.dispose();
		return IntStream.range(0, w).map(x -> (int) IntStream.range(0, h).mapToObj(y -> data.palette.getRGB(data.getPixel(x, y)))
				.filter(rgb -> Math.max(rgb.red, Math.max(rgb.green, rgb.blue)) < 0x80).count()).toArray();
	}

	static int distance(int[] a, int[] b, boolean mirrored) {
		return IntStream.range(0, a.length).map(i -> Math.abs(a[i] - b[mirrored ? a.length - 1 - i : i])).sum();
	}

	/* black text on white for both renderers; the glyphs must land in the stock columns and not mirrored */
	void assertStockPlacement(String text) {
		real.getCanvas().clear(0xFFFFFFFF);
		resources.setForeground(new Color(0, 0, 0));
		assertAllRuns(drawRuns(text, 0, 0), text);
		Rectangle b = layout.getBounds();
		int[] m3 = inkColumns(Math.min(W, b.width), Math.min(H, b.height));
		int[] stock = stockInkColumns(Math.min(W, b.width), Math.min(H, b.height));
		int ink = IntStream.of(stock).sum();
		assertTrue(distance(m3, stock, false) < distance(m3, stock, true), text + ": ink order follows stock, not mirrored");
		assertTrue(distance(m3, stock, false) <= ink / 2, text + ": column ink distance " + distance(m3, stock, false) + " of " + ink);
	}

	@Test
	public void testLtrRunsAreBlobs() {
		assertAllRuns(drawRuns("public class Hello { int[] a; } // done", 0, 0), "LTR");
		assertTrue(bright(W, 20) > 20, "LTR glyphs in the light foreground");
	}

	@Test
	public void testLtrPlacementMatchesStock() {
		assertStockPlacement("System.out.println(\"hello m3\");");
	}

	@Test
	public void testRtlRunsAreBlobs() {
		assertStockPlacement("abc שלום עולם def");
		assertStockPlacement("مرحبا بالعالم");
	}

	@Test
	public void testFallbackFontRunsAreBlobs() {
		assertStockPlacement("ab 漢字かな cd");
	}

	@Test
	public void testRunStyleLinesAndSelection() {
		TextStyle style = new TextStyle(null, new Color(255, 0, 0), null);
		style.underline = true;
		style.strikeout = true;
		layout.setText("under strike");
		layout.setStyle(style, 0, 4);
		long blobs = M3GlyphRuns.blobs(), fallbacks = M3GlyphRuns.fallbacks();
		System.setProperty("swt.m3.text", "runs");
		layout.draw(new GCExtension(gc), 0, 0, 6, 11, new Color(0, 0, 0), new Color(0, 0, 255));
		assertTrue(M3GlyphRuns.blobs() > blobs, "blobs drawn");
		assertEquals(fallbacks, M3GlyphRuns.fallbacks(), "underline/strikeout/selection need no fallback");
		Rectangle sel = layout.getBounds(6, 11);
		assertEquals(0x0000FF, pixel(sel.x + sel.width - 1, sel.y + 1), "selection background");
		Rectangle under = layout.getBounds(0, 4);
		long lines = IntStream.range(under.y, under.y + under.height).filter(y -> IntStream.range(under.x, under.x + under.width)
				.filter(x -> pixel(x, y) == 0xFF0000).count() >= under.width - 2).count();
		assertTrue(lines >= 2, "underline and strikeout rows in the run colour: " + lines);
	}

	/* L13 TEXT_FALLBACK_SAFE v2 (GDI_UNDERLAY), the default route on a raster surface: the stock pixels exactly */
	@Test
	public void testRasterRouteIsStockExactly() {
		layout.setText("System.out.println(\"hello m3\"); { [ ] }");
		layout.setStyle(new TextStyle(null, new Color(0xCC, 0x78, 0x32), null), 0, 6);
		long fallbacks = M3GlyphRuns.fallbacks();
		layout.draw(new GCExtension(gc), 0, 0);
		assertEquals(fallbacks + 1, M3GlyphRuns.fallbacks(), "auto on a raster surface takes GDI");
		Rectangle b = layout.getBounds();
		Image image = new Image(Display.getDefault(), b.width, b.height);
		GC stock = new GC(image);
		stock.setBackground(new Color(0x20, 0x20, 0x20));
		stock.fillRectangle(0, 0, b.width, b.height);
		stock.setForeground(new Color(0xDD, 0xDD, 0xDD));
		layout.draw(stock, 0, 0);
		stock.dispose();
		ImageData d = image.getImageData();
		image.dispose();
		long differ = IntStream.range(0, b.height).flatMap(y -> IntStream.range(0, b.width).filter(x -> {
			var rgb = d.palette.getRGB(d.getPixel(x, y));
			return (rgb.red << 16 | rgb.green << 8 | rgb.blue) != pixel(x, y);
		})).count();
		assertEquals(0, differ, "pixels differing from the stock GC inside the layout");
	}
}
