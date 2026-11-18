/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;

/**
 * Paints inset, outset and etched borders through the platform's native SWT
 * graphics context. A border is drawn <em>inside</em> its supplied rectangle;
 * it does not change a control's native trim or take ownership of SWT resources.
 * This makes it suitable for owner-drawn controls, figures and viewport layers.
 *
 * <p>Each band is at most four native filled rectangles, independent of its
 * thickness. The current GC clipping and alpha are respected, and its
 * background color is restored even if painting throws. Theme colors are
 * obtained from the GC's device at paint time.</p>
 */
public final class BorderPainter {

	private BorderPainter() {
	}

	/**
	 * Draws a border with one of {@link SWT#SHADOW_IN}, {@link SWT#SHADOW_OUT},
	 * {@link SWT#SHADOW_ETCHED_IN}, {@link SWT#SHADOW_ETCHED_OUT}, or
	 * {@link SWT#NONE}. Thickness is in the same logical units as GC drawing.
	 *
	 * @param gc the native SWT graphics context
	 * @param bounds the outer, half-open border bounds
	 * @param style one of the supported SWT shadow styles
	 * @param thickness total inward thickness on each edge, or zero to draw nothing
	 * @exception IllegalArgumentException if any argument is invalid or null
	 * @exception org.eclipse.swt.SWTException if the GC has been disposed
	 */
	public static void draw(GC gc, Rectangle bounds, int style, int thickness) {
		if (bounds == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		draw(gc, bounds.x, bounds.y, bounds.width, bounds.height, style, thickness);
	}

	/**
	 * Draws a native GC border without allocating a rectangle. Unlike a native
	 * window trim, this operation changes no control geometry or hit testing.
	 */
	public static void draw(GC gc, int x, int y, int width, int height, int style, int thickness) {
		if (gc == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (gc.isDisposed()) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
		if (!supported(style) || width < 0 || height < 0 || thickness < 0
				|| (long) x + width > Integer.MAX_VALUE
				|| (long) y + height > Integer.MAX_VALUE) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		if (style == SWT.NONE || thickness == 0 || width == 0 || height == 0) return;
		int depth = Math.min(thickness, (int) (((long) Math.min(width, height) + 1) / 2));
		if (depth == 0) return;

		Device device = gc.getDevice();
		Color highlight = device.getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW);
		Color light = device.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
		Color normal = device.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		Color dark = device.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW);
		Color previous = gc.getBackground();
		try {
			switch (style) {
				case SWT.SHADOW_OUT -> {
					band(gc, x, y, width, height, 1, highlight, dark);
					if (depth > 1) band(gc, x + 1, y + 1, width - 2, height - 2,
							depth - 1, light, normal);
				}
				case SWT.SHADOW_IN -> {
					band(gc, x, y, width, height, 1, dark, highlight);
					if (depth > 1) band(gc, x + 1, y + 1, width - 2, height - 2,
							depth - 1, normal, light);
				}
				case SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT -> {
					int outer = (depth + 1) / 2;
					boolean etchedIn = style == SWT.SHADOW_ETCHED_IN;
					band(gc, x, y, width, height, outer,
							etchedIn ? normal : light, etchedIn ? light : normal);
					if (depth > outer) band(gc, x + outer, y + outer,
							width - 2 * outer, height - 2 * outer, depth - outer,
							etchedIn ? highlight : dark, etchedIn ? dark : highlight);
				}
				default -> { /* SWT.NONE was handled before state changes. */ }
			}
		} finally {
			if (!gc.isDisposed()) gc.setBackground(previous);
		}
	}

	private static boolean supported(int style) {
		return style == SWT.NONE || style == SWT.SHADOW_IN || style == SWT.SHADOW_OUT
				|| style == SWT.SHADOW_ETCHED_IN || style == SWT.SHADOW_ETCHED_OUT;
	}

	/* Top/left and bottom/right meet at their corners; no per-pixel or per-depth loop. */
	private static void band(GC gc, int x, int y, int width, int height, int depth,
			Color topLeft, Color bottomRight) {
		if (depth <= 0 || width <= 0 || height <= 0) return;
		int d = Math.min(depth, (int) (((long) Math.min(width, height) + 1) / 2));
		if (d == 0) return;
		gc.setBackground(topLeft);
		gc.fillRectangle(x, y, width, d);
		if (height > d * 2) gc.fillRectangle(x, y + d, d, height - 2 * d);
		gc.setBackground(bottomRight);
		gc.fillRectangle(x, y + height - d, width, d);
		if (height > d * 2) gc.fillRectangle(x + width - d, y + d, d, height - 2 * d);
	}
}
