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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 6)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.M3Resources;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless: the text metrics of the M3 GC (GC seam, leaf 6; atom TEXT_METRICS, pattern M3-GC-TEXT-METRICS v1; census GF36,
 * GF28). Oracle: the stock win32 GC. FontMetrics: ascent = tmAscent - tmInternalLeading, descent = tmDescent, height =
 * tmHeight, leading = height - ascent - descent (so ascent + leading reaches the baseline), each scaled to points once.
 * textExtent honours its flags (DT_SINGLELINE unless DRAW_DELIMITER, DT_EXPANDTABS with DRAW_TAB, DT_NOPREFIX unless
 * DRAW_MNEMONIC) and rounds up; stringExtent measures the string as given.
 */
public class Test_org_eclipse_swt_m3gc_TextMetrics {
	static Font font;

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
		font = new Font(Display.getDefault(), "Arial", 12, SWT.NORMAL);
	}

	@AfterAll
	static void disposeFont() {
		if (font != null) {
			font.dispose();
		}
	}

	/* the GC over the doubles with the real resources (fonts, splits and extents of M3Resources) */
	static M3ExternalGC realGc(int zoom) {
		M3GcFixture f = new M3GcFixture(zoom);
		M3Resources resources = new M3Resources(null, f.ext);
		resources.setFont(font);
		f.ext.resources = resources;
		return new M3ExternalGC(f.ext, SWT.NONE);
	}

	@Test
	public void testAscentExcludesTheInternalLeading() {
		M3GcFixture f = new M3GcFixture(100);
		var m = f.resources.m3Font.getMetrics();
		int ascent = (int) Math.ceil(Math.abs(m.getAscent())), descent = (int) Math.ceil(m.getDescent());
		int internal = Math.max(0, ascent + descent - Math.round(f.resources.m3Font.getSize()));
		FontMetrics fm = f.gc.getFontMetrics();
		assertEquals(ascent - internal, fm.getAscent(), "tmAscent - tmInternalLeading");
		assertEquals(descent, fm.getDescent(), "tmDescent");
		assertEquals(ascent + descent, fm.getHeight(), "tmHeight = tmAscent + tmDescent");
		assertEquals(internal, fm.getLeading(), "the internal leading");
	}

	@Test
	public void testMetricsScaleOnceAndKeepTheInvariant() {
		FontMetrics at100 = new M3GcFixture(100).gc.getFontMetrics(), at200 = new M3GcFixture(200).gc.getFontMetrics();
		assertEquals(at200.getHeight(), at200.getAscent() + at200.getDescent() + at200.getLeading(), "height = ascent + descent + leading");
		assertEquals(Math.round(at100.getAscent() / 2f), at200.getAscent(), "the same pixels at 200 %: half the points");
		assertEquals(Math.round(at100.getHeight() / 2f), at200.getHeight());
	}

	@Test
	public void testDelimiterAddsLines() {
		M3ExternalGC gc = realGc(100);
		int one = gc.textExtent("a", SWT.DRAW_DELIMITER).y;
		assertEquals(2 * one, gc.textExtent("a\nb", SWT.DRAW_DELIMITER).y, "DRAW_DELIMITER: two lines are twice as high");
		assertEquals(one, gc.textExtent("", SWT.DRAW_DELIMITER).y, "an empty string is one line high (stock: tmHeight)");
		assertEquals(0, gc.textExtent("", SWT.NONE).x);
	}

	@Test
	public void testAmpersandOnlyWithMnemonic() {
		M3ExternalGC gc = realGc(100);
		Point plain = gc.textExtent("a&b", SWT.NONE), mnemonic = gc.textExtent("a&b", SWT.DRAW_MNEMONIC);
		assertTrue(plain.x > mnemonic.x, "without DRAW_MNEMONIC the '&' is measured: " + plain + " vs " + mnemonic);
		assertEquals(gc.stringExtent("a&b").x, gc.textExtent("a&&b", SWT.DRAW_MNEMONIC).x, "'&&' is one '&'");
		assertTrue(gc.stringExtent("a&b").x > gc.stringExtent("ab").x, "stringExtent measures the string as given");
	}

	@Test
	public void testTabExpandsOnlyWithDrawTab() {
		M3ExternalGC gc = realGc(100);
		assertTrue(gc.textExtent("\tx", SWT.DRAW_TAB).x > gc.textExtent("\tx", SWT.NONE).x, "DRAW_TAB expands the tab");
	}

	@Test
	public void testExtentIsTheStockGcs() {
		// v2 (L6-26): the stock GC is the measure, not ceil(Skia width): 'Wil' in Arial 12 is 22.21 Skia pixels and 22 GDI pixels
		M3ExternalGC gc = realGc(DPIUtil.getDeviceZoom());
		Image image = new Image(Display.getDefault(), 4, 4);
		GC stock = new GC(image);
		stock.setFont(font);
		assertEquals(stock.textExtent("Wil", SWT.NONE), gc.textExtent("Wil", SWT.NONE), "DrawText DT_CALCRECT, as stock");
		stock.dispose();
		image.dispose();
	}
}
