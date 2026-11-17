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
 *     Hitesh Soliwal (Synexia) - adoption into this fork, 2026
 *******************************************************************************/

package org.eclipse.swt.internal.canvasext.raster;

import org.eclipse.swt.internal.maya.Canvas;
import org.eclipse.swt.internal.maya.Font;
import org.eclipse.swt.internal.maya.Matrix33;
import org.eclipse.swt.internal.maya.Paint;
import org.eclipse.swt.internal.maya.Path;
import org.eclipse.swt.internal.maya.Region;
import org.eclipse.swt.internal.maya.SamplingMode;
import org.eclipse.swt.internal.maya.types.RRect;
import org.eclipse.swt.internal.maya.types.Rect;

/**
 * Adapter to wrap Skija's Canvas as IRasterCanvas. (Stub for now, can be extended
 * as needed.)
 */
class RasterCanvasAdapter implements IRasterCanvas {
	private final Canvas delegate;

	public RasterCanvasAdapter(Canvas canvas) {
		this.delegate = canvas;
	}

	@Override
	public int getSaveCount() {
		return delegate.getSaveCount();
	}

	@Override
	public void restoreToCount(int saveCount) {
		delegate.restoreToCount(saveCount);
	}

	@Override
	public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
		delegate.drawLine(x0, y0, x1, y1, paint);
	}

	@Override
	public void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter,
			Paint paint) {
		delegate.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint);
	}

	@Override
	public void drawRect(Rect rect, Paint paint) {
		delegate.drawRect(rect, paint);
	}

	@Override
	public void drawOval(Rect rect, Paint paint) {
		delegate.drawOval(rect, paint);
	}

	@Override
	public void drawPath(Path path, Paint paint) {
		delegate.drawPath(path, paint);
	}

	@Override
	public void drawPolygon(float[] points, Paint paint) {
		delegate.drawPolygon(points, paint);
	}

	@Override
	public void drawRRect(RRect rrect, Paint paint) {
		delegate.drawRRect(rrect, paint);
	}

	@Override
	public void drawImage(IRasterImage image, int x, int y) {
		delegate.drawImage(((RasterImageAdapter)image).getSkiaImage(), x, y);
	}

	@Override
	public void drawImageRect(IRasterImage image, Rect src, Rect dst, SamplingMode sampling, Paint paint, boolean strict) {
		delegate.drawImageRect(((RasterImageAdapter)image).getSkiaImage(), src, dst, sampling, paint, strict);
	}

	@Override
	public void setMatrix(Matrix33 matrix) {
		delegate.setMatrix(matrix);
	}

	@Override
	public void save() {
		delegate.save();
	}

	@Override
	public void clipRect(Rect rect) {
		delegate.clipRect(rect);
	}

	@Override
	public void clear(int color) {
		delegate.clear(color);
	}

	@Override
	public void restore() {
		delegate.restore();
	}

	@Override
	public void clipPath(org.eclipse.swt.internal.maya.Path path, org.eclipse.swt.internal.maya.ClipMode mode,
			boolean doAntiAlias) {
		delegate.clipPath(path, mode, doAntiAlias);
	}

	@Override
	public void drawString(String text, int x, int i, Font f, Paint fgp) {
		delegate.drawString(text, x, i, f, fgp);

	}

	@Override
	public void clipRegion(Region skiaRegion) {
		delegate.clipRegion(skiaRegion);
	}
}