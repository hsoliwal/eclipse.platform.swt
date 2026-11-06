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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (text leaf L14)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

//FLLDCIM: L14 ONE_TEXT_SHAPER v1 (raster) — GC text on a raster surface is the stock DrawText over the real pixels (decision L13-12)
/**
 * GC <code>drawText</code>/<code>drawString</code> on a raster M3 surface drawn by the stock GC over the surface pixels
 * (text leaf L14, ONE_TEXT_SHAPER v1, raster half; same route decision as TextLayout, L13-12). One shaper for draw and
 * measure: GDI/Uniscribe shapes, links fonts (emoji, CJK), orders bidi runs, expands tabs, draws mnemonics and measures
 * (M3GdiText), so the M3 frame shows the stock pixels. Taken under <code>swt.m3.text=auto|gdi</code> when the GC alpha is
 * 255, no XOR or pattern is set, the canvas maps the box by a whole-pixel translation at 100 % and the box lies inside a
 * raster surface; anything else keeps the Skia path (M3TextDrawing), as on GPU surfaces.
 */
final class M3GdiTextDraw {

	private M3GdiTextDraw() {
	}

	/** draws <code>text</code> as stock <code>drawText</code> (or <code>drawString</code>); false when the route does not apply */
	static boolean draw(M3ExternalGC gc, String text, int flags, int x, int y, boolean string) {
		if (!applies(gc) || text.isEmpty() || !gc.isRasterSurface()) {
			return false;
		}
		final Point extent = string ? gc.stringExtent(text) : gc.textExtent(text, flags);
		if (extent == null) {
			return false;
		}
		final Rectangle dest = new Rectangle(x, y, extent.x + extent.y / 3 + 2, extent.y);
		final ImageData under = gc.readSurface(dest);
		return under != null && drawOver(gc, under, dest, stock -> stockText(stock, text, flags, string));
	}

	private static void stockText(GC stock, String text, int flags, boolean string) {
		if (string) {
			stock.drawString(text, 0, 0, (flags & SWT.DRAW_TRANSPARENT) != 0);
		} else {
			stock.drawText(text, 0, 0, flags);
		}
	}

	private static boolean applies(M3ExternalGC gc) {
		final String mode = M3GlyphRuns.mode();
		return ("auto".equals(mode) || "gdi".equals(mode)) && gc.getAlpha() == 255 && !gc.getXORMode()
				&& gc.getForegroundPattern() == null && gc.getBackgroundPattern() == null;
	}

	private static boolean drawOver(M3ExternalGC gc, ImageData under, Rectangle dest, java.util.function.Consumer<GC> how) {
		final Image image = new Image(gc.getDevice(), under);
		final GC stock = new GC(image);
		try {
			M3TextLayoutFallback.carry(gc, stock);
			how.accept(stock);
			final ImageData drawn = image.getImageData(100);
			gc.drawLayoutRaster(M3TextLayoutFallback.opaque(drawn), drawn.width, drawn.height, dest);
			return true;
		} finally {
			stock.dispose();
			image.dispose();
		}
	}
}
