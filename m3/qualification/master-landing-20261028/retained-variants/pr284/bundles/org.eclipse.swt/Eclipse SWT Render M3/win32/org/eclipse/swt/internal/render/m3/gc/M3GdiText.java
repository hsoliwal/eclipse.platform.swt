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
package org.eclipse.swt.internal.render.m3.gc;

import java.util.function.LongFunction;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.SWTFontProvider;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.RECT;
import org.eclipse.swt.internal.win32.SIZE;

/**
 * Text measured exactly as the stock win32 GC measures it, in pixels (GC seam, leaf 6; atom TEXT_METRICS, pattern
 * M3-GC-TEXT-METRICS v2; census GF28, leaf 7 finding L7-35). REUSE_DIRECT of the stock <code>GC.textExtentInPixels</code> and
 * <code>stringExtentInPixels</code> (GDI path): <code>DrawText</code> with <code>DT_CALCRECT</code> and the flag mapping
 * (DT_SINGLELINE unless DRAW_DELIMITER, DT_EXPANDTABS with DRAW_TAB, DT_NOPREFIX unless DRAW_MNEMONIC),
 * <code>GetTextExtentPoint32</code>, and the height of a space for an empty string, on a memory DC holding the font's
 * handle at the GC's zoom.
 * <p>
 * Why GDI and not Skia: widgets lay out with stock GCs made outside paint (CTabFolder.updateItems, computeSize) and measure
 * again with the Paint GC; Skia's advances are linear (Segoe UI 9 pt "Tab 2": 29.2 pixels against GDI's hinted 28), so a
 * Skia measurement in paint shortened "Tab 2" to "Tab..." (leaf 7 probe). One measurement for both GCs keeps their layouts
 * equal; the glyphs are still drawn by Skia (a line may be up to a pixel or two wider than measured).
 * </p>
 */
final class M3GdiText {

	private M3GdiText() {
	}

	/** stock textExtentInPixels (GDI path) */
	static Point textExtent(Font font, int zoom, String text, int flags) {
		return measure(font, zoom, hdc -> text.isEmpty() ? space(hdc) : drawTextExtent(hdc, text, flags));
	}

	/** stock stringExtentInPixels (GDI path) */
	static Point stringExtent(Font font, int zoom, String text) {
		return measure(font, zoom, hdc -> text.isEmpty() ? space(hdc) : textExtentPoint(hdc, text));
	}

	private static Point measure(Font font, int zoom, LongFunction<Point> how) {
		final long hdc = OS.CreateCompatibleDC(0);
		final long previous = OS.SelectObject(hdc, SWTFontProvider.getFontHandle(font, zoom));
		try {
			return how.apply(hdc);
		} finally {
			OS.SelectObject(hdc, previous);
			OS.DeleteDC(hdc);
		}
	}

	private static Point space(long hdc) {
		final SIZE size = new SIZE();
		OS.GetTextExtentPoint32(hdc, new char[] { ' ' }, 1, size);
		return new Point(0, size.cy);
	}

	private static Point textExtentPoint(long hdc, String text) {
		final SIZE size = new SIZE();
		OS.GetTextExtentPoint32(hdc, text.toCharArray(), text.length(), size);
		return new Point(size.cx, size.cy);
	}

	private static Point drawTextExtent(long hdc, String text, int flags) {
		final RECT rect = new RECT();
		final char[] buffer = text.toCharArray();
		int format = OS.DT_LEFT | OS.DT_CALCRECT;
		format |= (flags & SWT.DRAW_DELIMITER) == 0 ? OS.DT_SINGLELINE : 0;
		format |= (flags & SWT.DRAW_TAB) != 0 ? OS.DT_EXPANDTABS : 0;
		format |= (flags & SWT.DRAW_MNEMONIC) == 0 ? OS.DT_NOPREFIX : 0;
		OS.DrawText(hdc, buffer, buffer.length, rect, format);
		return new Point(rect.right, rect.bottom);
	}
}
