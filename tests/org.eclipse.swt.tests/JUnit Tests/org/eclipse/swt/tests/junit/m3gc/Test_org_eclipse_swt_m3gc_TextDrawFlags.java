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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.internal.render.m3.gc.M3Resources;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless: the text draw flags of the M3 GC (GC seam, leaf 6; atom TEXT_DRAW_FLAGS, pattern M3-GC-TEXT-FLAGS v1; census
 * GF29, GF28). Oracle: the stock win32 GC. drawString draws the string as given on one line (ExtTextOut: no delimiter, tab or
 * mnemonic processing); drawText splits lines only with DRAW_DELIMITER; DRAW_MNEMONIC drops a single '&' and turns "&&"
 * into '&' (DrawText without DT_NOPREFIX).
 */
public class Test_org_eclipse_swt_m3gc_TextDrawFlags {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	static List<Object> texts(M3GcFixture f) {
		return f.calls("drawString").stream().map(c -> c.params[0]).toList();
	}

	@Test
	public void testDrawStringIgnoresDelimiters() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.drawString("a\nb", 10, 20, true);
		assertEquals(List.of("a\nb"), texts(f), "one line, as given");
	}

	@Test
	public void testDrawStringIgnoresTabs() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.drawString("a\tb", 10, 20);
		assertEquals(List.of("a\tb"), texts(f), "no tab expansion");
	}

	@Test
	public void testDrawTextStillSplitsLines() {
		M3GcFixture f = new M3GcFixture(100);
		f.gc.drawText("a\nb", 10, 20, true);
		assertEquals(List.of("a", "b"), texts(f), "drawText(String, int, int, boolean) keeps DRAW_DELIMITER");
	}

	@Test
	public void testMnemonicSemantics() {
		M3Resources resources = new M3Resources(null, new M3GcFixture(100).ext);
		Font font = new Font(Display.getDefault(), "Arial", 12, SWT.NORMAL);
		resources.setFont(font);
		assertArrayEquals(new String[] { "a&b" }, resources.getTextSplits("a&&b", SWT.DRAW_MNEMONIC), "'&&' draws one '&'");
		assertArrayEquals(new String[] { "ab" }, resources.getTextSplits("a&b", SWT.DRAW_MNEMONIC), "a single '&' marks the mnemonic");
		assertArrayEquals(new String[] { "a&b" }, resources.getTextSplits("a&b", SWT.NONE), "without DRAW_MNEMONIC the '&' stays");
		font.dispose();
	}
}
