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
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3fidelity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.*;

import org.eclipse.swt.internal.render.m3.*;
import org.eclipse.swt.internal.win32.*;
import org.junit.jupiter.api.*;

/**
 * TEXT_TRUNCATION v1: {@link M3GdiEllipsis} cuts where DrawText's DT_END_ELLIPSIS cuts. Oracle: the pixels DrawText draws
 * with DT_END_ELLIPSIS in a box equal the pixels of "prefix..." (the cut's prefix, drawn without ellipsis), for the cell
 * texts of the System tab and every box width from 10 to 90 pixels (Segoe UI 9 pt, lfHeight -12, as the tables use).
 */
public class Test_org_eclipse_swt_internal_render_m3_M3GdiEllipsis {
	static final String [] TEXTS = {"#FFFFFF ", "(255,255,255,255) ", "COLOR_WIDGET_BACKGROUND ", "Named", "System ", "tomorrow", "W"};
	/* winuser.h; OS has no constant for it */
	static final int DT_END_ELLIPSIS = 0x8000, HEIGHT = 20, MAX = 100;
	static long font, dc, bitmap, screen;

	@BeforeAll
	static void gdi () {
		LOGFONT logFont = new LOGFONT ();
		logFont.lfHeight = -12;
		logFont.lfWeight = 400;
		"Segoe UI".getChars (0, 8, logFont.lfFaceName, 0);
		font = OS.CreateFontIndirect (logFont);
		screen = OS.GetDC (0);
		dc = OS.CreateCompatibleDC (screen);
		bitmap = OS.CreateCompatibleBitmap (screen, MAX, HEIGHT);
		OS.SelectObject (dc, bitmap);
		OS.SelectObject (dc, font);
	}

	@AfterAll
	static void release () {
		OS.DeleteDC (dc);
		OS.DeleteObject (bitmap);
		OS.DeleteObject (font);
		OS.ReleaseDC (0, screen);
	}

	/* the pixels DrawText draws for a text in a box of the width, with or without DT_END_ELLIPSIS */
	static int [] draw (String text, int width, boolean ellipsis) {
		RECT all = new RECT ();
		OS.SetRect (all, 0, 0, MAX, HEIGHT);
		OS.FillRect (dc, all, OS.GetStockObject (OS.WHITE_BRUSH));
		RECT box = new RECT ();
		OS.SetRect (box, 0, 0, width, HEIGHT);
		char [] chars = text.toCharArray ();
		OS.DrawText (dc, chars, chars.length, box, OS.DT_SINGLELINE | OS.DT_NOPREFIX | (ellipsis ? DT_END_ELLIPSIS : 0));
		return IntStream.range (0, MAX * HEIGHT).map (i -> OS.GetPixel (dc, i % MAX, i / MAX)).toArray ();
	}

	@Test
	public void test_cutEqualsDrawTextEndEllipsis () {
		M3GdiEllipsis ellipsis = new M3GdiEllipsis ();
		try {
			for (String text : TEXTS) {
				for (int width = 10; width <= 90; width++) {
					int keep = ellipsis.cut (font, text, width);
					String drawn = keep < 0 ? text : text.substring (0, keep) + "...";
					assertArrayEquals (draw (text, width, true), draw (drawn, width, false), text + " in " + width + " px: " + drawn);
				}
			}
		} finally {
			ellipsis.dispose ();
		}
	}
}
