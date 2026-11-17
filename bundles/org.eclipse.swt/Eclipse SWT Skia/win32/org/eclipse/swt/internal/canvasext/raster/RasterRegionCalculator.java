/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext.raster;

import java.util.HashMap;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.RegionLog;
import org.eclipse.swt.graphics.RegionLog.OpType;
import org.eclipse.swt.graphics.RegionLog.Operation;

import org.eclipse.swt.internal.maya.types.IRect;

public class RasterRegionCalculator implements AutoCloseable {

	private final static HashMap<OpType, org.eclipse.swt.internal.maya.Region.Op> operationMapping = new HashMap<OpType, org.eclipse.swt.internal.maya.Region.Op>();

	static {
		operationMapping.put(OpType.ADD, org.eclipse.swt.internal.maya.Region.Op.UNION);
		operationMapping.put(OpType.SUBTRACT, org.eclipse.swt.internal.maya.Region.Op.DIFFERENCE);
		operationMapping.put(OpType.INTERSECT, org.eclipse.swt.internal.maya.Region.Op.INTERSECT);
	}

	private final org.eclipse.swt.graphics.Region region;
	private org.eclipse.swt.internal.maya.Region calculatedRegion;
	private final IRasterCanvasExtension skiaExtension;

	public RasterRegionCalculator(org.eclipse.swt.graphics.Region r, IRasterCanvasExtension skiaExtension) {
		this.region = r;
		this.skiaExtension = skiaExtension;
	}

	org.eclipse.swt.internal.maya.Region getSkiaRegion() {

		// just all operations for the region creation will be executed to get the skia
		// region.

		if (calculatedRegion != null) {
			return calculatedRegion;
		}

		final org.eclipse.swt.internal.maya.Region reg = new org.eclipse.swt.internal.maya.Region();
		final var log = RegionLog.getLog(region);
		for (final var o : log.getOperations()) {
			apply(o, reg);
		}

		calculatedRegion = reg;
		return calculatedRegion;

	}

	@Override
	public void close() {
		if (calculatedRegion != null) {
			calculatedRegion.close();
			calculatedRegion = null;
		}
	}

	private void apply(Operation o, org.eclipse.swt.internal.maya.Region reg) {
		final org.eclipse.swt.internal.maya.Region.Op skiaOperation = operationMapping.get(o.type());
		if (skiaOperation != null) {
			executeOperation(skiaOperation, o.executionObject(), reg);
			return;
		}

		if (OpType.TRANSLATE.equals(o.type())) {
			if (o.executionObject() instanceof final Point p) {
				reg.translate(p.x, p.y);
				return;
			}
		}

		throw new IllegalStateException("Unknown type and object: " + o); //$NON-NLS-1$

	}

	private void executeOperation(org.eclipse.swt.internal.maya.Region.Op skiaOperation, Object ob, org.eclipse.swt.internal.maya.Region reg) {
		if (ob instanceof final int[] polygon) {
			final var tempReg = createPolygonSkiaRegion(polygon);
			reg.op(tempReg, skiaOperation);
		} else if (ob instanceof final Rectangle rec) {
			final var irect = createRectRegion(rec);
			reg.op(irect, skiaOperation);
		} else if (ob instanceof final Region otherReg) {
			try (final RasterRegionCalculator src = new RasterRegionCalculator(otherReg, skiaExtension)) {
				reg.op(src.getSkiaRegion(), skiaOperation);
			}
		}
	}

	/**
	 * Creates a Skija region from an SWT rectangle by converting it to a polygon.
	 */
	private org.eclipse.swt.internal.maya.Region createRectRegion(Rectangle rec) {
		final var rect = new IRect(rec.x, rec.y, rec.x + rec.width, rec.y + rec.height);

		return createPolygonSkiaRegion(new int[] { rect.getLeft(), rect.getTop(), rect.getRight(), rect.getTop(),
				rect.getRight(), rect.getBottom(), rect.getLeft(), rect.getBottom() });

	}

	private org.eclipse.swt.internal.maya.Region createPolygonSkiaRegion(int[] polygon) {

		final org.eclipse.swt.internal.maya.Region r = new org.eclipse.swt.internal.maya.Region();

		final var scaler = new DpiScalerUtil(skiaExtension.getScaler());

		try (final var pathBuilder = new org.eclipse.swt.internal.maya.Path()) {
			pathBuilder.addPoly(scaler.autoScaleUp(toFloat(polygon)), true);

			final Point maxV = getMax(scaler.autoScaleUp(polygon));

			r.setRect(new IRect(0, 0, maxV.x, maxV.y));

			// a path has to be set for a clipping region.
			// so first we have to create a region big enough for the path and then set the
			// path.
			{
				var path = pathBuilder; // Skija 0.116.3: Path is its own builder; the outer try closes it
				r.setPath(path, r);
			}

			return r;
		}
	}

	/**
	 *
	 * max value on x and on y in one point.
	 *
	 * @param polygon
	 * @return
	 */
	private static Point getMax(int[] polygon) {

		final var p = new Point(0, 0);

		for (int i = 0; i < polygon.length; i++) {

			if (i % 2 == 0) {
				p.x = Math.max(polygon[i], p.x);
			}
			if (i % 2 == 1) {
				p.y = Math.max(polygon[i], p.y);
			}

		}

		return p;
	}

	private static float[] toFloat(int[] arr) {

		final float[] r = new float[arr.length];

		for (int i = 0; i < arr.length; i++) {
			r[i] = arr[i];
		}

		return r;

	}

}