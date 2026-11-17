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

import org.eclipse.swt.internal.maya.ClipMode;
import org.eclipse.swt.internal.maya.Font;
import org.eclipse.swt.internal.maya.Matrix33;
import org.eclipse.swt.internal.maya.Paint;
import org.eclipse.swt.internal.maya.Path;
import org.eclipse.swt.internal.maya.Region;
import org.eclipse.swt.internal.maya.types.RRect;
import org.eclipse.swt.internal.maya.types.Rect;

/**
 * Abstraction for Skija's Canvas, for future decoupling.
 */
public interface IRasterCanvas {
	int getSaveCount();
	void restoreToCount(int saveCount);
	void drawLine(float x0, float y0, float x1, float y1, Paint paint);
	void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter, Paint paint);
	void drawRect(Rect rect, Paint paint);
	void drawOval(Rect rect, Paint paint);
	void drawPath(Path path, Paint paint);
	void drawPolygon(float[] points, Paint paint);
	void drawRRect(RRect rrect, Paint paint);
	void drawImage(IRasterImage image, int x, int y);
	void drawImageRect(IRasterImage image, Rect src, Rect dst, org.eclipse.swt.internal.maya.SamplingMode sampling, Paint paint, boolean strict);
	void setMatrix(Matrix33 matrix);
	void save();
	void clipRect(Rect rect);
	void clear(int color);
	void restore();
	void clipPath(Path mayaPath, ClipMode intersect, boolean b);
	void drawString(String text, int x, int i, Font f, Paint fgp);
	void clipRegion(Region mayaRegion);
}