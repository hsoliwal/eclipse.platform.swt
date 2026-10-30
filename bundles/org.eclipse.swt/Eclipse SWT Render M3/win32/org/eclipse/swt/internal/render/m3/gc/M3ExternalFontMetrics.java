/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *     SAP SE and others - initial API and implementation
 *     Synexia (Hitesh Soliwal) - stock TEXTMETRIC semantics (GC seam, leaf 6), 2026
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.Objects;

import org.eclipse.swt.internal.canvasext.IExternalFontMetrics;

/**
 * Font metrics of the M3 GC with the stock win32 meaning (GC seam, leaf 6; atom TEXT_METRICS, pattern M3-GC-TEXT-METRICS v1,
 * census GF36): ascent = tmAscent - tmInternalLeading, descent = tmDescent, height = tmHeight = tmAscent + tmDescent, leading =
 * height - ascent - descent (the internal leading), each pixel value scaled to points once. tmAscent and tmDescent are the
 * ascender and descender as M3TextDrawing places a line (baseline at ceil |ascent|), the internal leading the part of them
 * above the em size. The donor subtracted Skia's line gap from the ascent, scaled the leading twice and took Skia's height.
 */
public class M3ExternalFontMetrics implements IExternalFontMetrics {

	private final io.github.humbleui.skija.FontMetrics m;
	private final float size;
	private final DpiScalerUtil sc;

	M3ExternalFontMetrics(io.github.humbleui.skija.FontMetrics metrics, float size, DpiScalerUtil dpiScaler) {
		this.sc = dpiScaler;
		this.m = metrics;
		this.size = size;
	}

	private int ascentPixels() {
		return (int) Math.ceil(Math.abs(m.getAscent()));
	}

	private int descentPixels() {
		return (int) Math.ceil(Math.abs(m.getDescent()));
	}

	private int internalLeadingPixels() {
		return Math.max(0, ascentPixels() + descentPixels() - Math.round(size));
	}

	@Override
	public int getAscent() {
		return sc.autoScaleDownToInt(ascentPixels() - internalLeadingPixels());
	}

	@Override
	public double getAverageCharacterWidth() {
		return sc.autoScaleDown(m.getAvgCharWidth());
	}

	@Override
	public int getAverageCharWidth() {
		return (int) Math.ceil(sc.autoScaleDown(m.getAvgCharWidth()));
	}

	@Override
	public int getDescent() {
		return sc.autoScaleDownToInt(descentPixels());
	}

	@Override
	public int getHeight() {
		return sc.autoScaleDownToInt(ascentPixels() + descentPixels());
	}

	@Override
	public int getLeading() {
		// stock FontMetrics.getLeading: derived, so height == ascent + descent + leading holds in points
		return getHeight() - getAscent() - getDescent();
	}

	@Override
	public int hashCode() {
		return Objects.hash(m, size);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final M3ExternalFontMetrics other = (M3ExternalFontMetrics) obj;
		return Objects.equals(m, other.m) && size == other.size;
	}

}
