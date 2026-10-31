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
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
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
 * opaque run backgrounds stay opaque, the GC alpha is applied once.
 */
public class Test_org_eclipse_swt_m3gc_TextLayoutRuns {
	static final int DARK = 0xFF202020, W = 300, H = 80;
	Surface real;
	M3ExternalGC gc;
	Font font;
	TextLayout layout;

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
		M3Resources resources = new M3Resources(null, f.ext);
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
}
