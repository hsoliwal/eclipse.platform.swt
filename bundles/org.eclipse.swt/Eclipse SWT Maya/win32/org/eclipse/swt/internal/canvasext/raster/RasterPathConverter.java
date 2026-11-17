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
 *     Synexia (Hitesh Soliwal) - adoption into the M3 fork, 2026
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext.raster;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.PathData;

import org.eclipse.swt.internal.maya.PathFillMode;

public class RasterPathConverter {

	public static org.eclipse.swt.internal.maya.Path convertSWTPathToMayaPath(Path swtPath, DpiScalerUtil sc) {

		if (swtPath == null || swtPath.isDisposed()) {
			return null;
		}
		final PathData data = swtPath.getPathData();

		{
			final org.eclipse.swt.internal.maya.Path mayaPathBuilder = new org.eclipse.swt.internal.maya.Path(); // Skija 0.116.3: Path is its own builder; the caller owns the returned path

			final float[] pts = data.points;
			final byte[] types = data.types;
			int pi = 0;
			for (final byte type : types) {
				switch (type) {
				case SWT.PATH_MOVE_TO:
					mayaPathBuilder.moveTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_LINE_TO:
					mayaPathBuilder.lineTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_CUBIC_TO:
					mayaPathBuilder.cubicTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]),
							sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]),
							sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_QUAD_TO:
					mayaPathBuilder.quadTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]),
							sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_CLOSE:
					mayaPathBuilder.closePath();
					break;
				default:
				}
			}
			final var p = mayaPathBuilder;
			return p;
		}

	}

	public static org.eclipse.swt.internal.maya.Path createPath(int[] pointArray,int fillRule , DpiScalerUtil sc) {
		// Create Skija path for the polygon
		{ // Skija 0.116.3: Path is its own builder; the caller owns the returned path
			org.eclipse.swt.internal.maya.Path pathBuilder = new org.eclipse.swt.internal.maya.Path(); // Move
			// to
			// first
			// point
			pathBuilder.moveTo(sc.autoScaleUp(pointArray[0]), sc.autoScaleUp(pointArray[1]));
			// Add lines to subsequent points
			for (int i = 2; i < pointArray.length; i += 2) {
				pathBuilder.lineTo(sc.autoScaleUp(pointArray[i]), sc.autoScaleUp(pointArray[i + 1]));
			}
			// Close the path to form a polygon
			pathBuilder.closePath();
			pathBuilder.setFillMode(fillRule == SWT.FILL_EVEN_ODD ? PathFillMode.EVEN_ODD : PathFillMode.WINDING);
			// Fill the polygon
			return pathBuilder;
		}
	}

}
