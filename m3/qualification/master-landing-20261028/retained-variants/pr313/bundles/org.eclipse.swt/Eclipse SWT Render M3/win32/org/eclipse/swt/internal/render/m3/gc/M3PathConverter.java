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
package org.eclipse.swt.internal.render.m3.gc;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.PathData;

import jdk.m3.graphics.M3PathFillMode;

public class M3PathConverter {

	public static jdk.m3.graphics.M3Path convertSWTPathToM3Path(Path swtPath, DpiScalerUtil sc) {

		if (swtPath == null || swtPath.isDisposed()) {
			return null;
		}
		final PathData data = swtPath.getPathData();

		try (final jdk.m3.graphics.M3PathBuilder m3PathBuilder = new jdk.m3.graphics.M3PathBuilder()) {

			final float[] pts = data.points;
			final byte[] types = data.types;
			int pi = 0;
			for (final byte type : types) {
				switch (type) {
				case SWT.PATH_MOVE_TO:
					m3PathBuilder.moveTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_LINE_TO:
					m3PathBuilder.lineTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_CUBIC_TO:
					m3PathBuilder.cubicTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]),
							sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]),
							sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_QUAD_TO:
					m3PathBuilder.quadTo(sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]),
							sc.autoScaleUp(pts[pi++]), sc.autoScaleUp(pts[pi++]));
					break;
				case SWT.PATH_CLOSE:
					m3PathBuilder.closePath();
					break;
				default:
				}
			}
			final var p = m3PathBuilder.build();
			return p;
		}

	}

	public static jdk.m3.graphics.M3Path createPath(int[] pointArray,int fillRule , DpiScalerUtil sc) {
		// Create the M3Path for the polygon
		try (jdk.m3.graphics.M3PathBuilder pathBuilder = new jdk.m3.graphics.M3PathBuilder()) { // Move
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
			pathBuilder.setFillMode(fillRule == SWT.FILL_EVEN_ODD ? M3PathFillMode.EVEN_ODD : M3PathFillMode.WINDING);
			// Fill the polygon
			return pathBuilder.build();
		}
	}

}
