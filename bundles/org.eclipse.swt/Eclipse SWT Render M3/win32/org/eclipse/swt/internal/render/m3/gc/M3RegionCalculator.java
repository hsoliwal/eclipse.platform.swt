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
package org.eclipse.swt.internal.render.m3.gc;

import java.util.HashMap;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.RegionLog;
import org.eclipse.swt.graphics.RegionLog.OpType;
import org.eclipse.swt.graphics.RegionLog.Operation;

import io.github.humbleui.types.IRect;

public class M3RegionCalculator implements AutoCloseable {

	private final static HashMap<OpType, io.github.humbleui.skija.Region.Op> operationMapping = new HashMap<OpType, io.github.humbleui.skija.Region.Op>();

	static {
		operationMapping.put(OpType.ADD, io.github.humbleui.skija.Region.Op.UNION);
		operationMapping.put(OpType.SUBTRACT, io.github.humbleui.skija.Region.Op.DIFFERENCE);
		operationMapping.put(OpType.INTERSECT, io.github.humbleui.skija.Region.Op.INTERSECT);
	}

	private final org.eclipse.swt.graphics.Region region;
	private io.github.humbleui.skija.Region calculatedRegion;
	private final IM3CanvasExtension m3Extension;

	public M3RegionCalculator(org.eclipse.swt.graphics.Region r, IM3CanvasExtension m3Extension) {
		this.region = r;
		this.m3Extension = m3Extension;
	}

	io.github.humbleui.skija.Region getM3Region() {

		// just all operations for the region creation will be executed to get the skia
		// region.

		if (calculatedRegion != null) {
			return calculatedRegion;
		}

		final io.github.humbleui.skija.Region reg = new io.github.humbleui.skija.Region();
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

	private void apply(Operation o, io.github.humbleui.skija.Region reg) {
		final io.github.humbleui.skija.Region.Op m3Operation = operationMapping.get(o.type());
		if (m3Operation != null) {
			executeOperation(m3Operation, o.executionObject(), reg);
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

	private void executeOperation(io.github.humbleui.skija.Region.Op m3Operation, Object ob, io.github.humbleui.skija.Region reg) {
		if (ob instanceof final int[] polygon) {
			final var tempReg = createPolygonM3Region(polygon);
			reg.op(tempReg, m3Operation);
		} else if (ob instanceof final Rectangle rec) {
			final var irect = createRectRegion(rec);
			reg.op(irect, m3Operation);
		} else if (ob instanceof final Region otherReg) {
			try (final M3RegionCalculator src = new M3RegionCalculator(otherReg, m3Extension)) {
				reg.op(src.getM3Region(), m3Operation);
			}
		}
	}

	/**
	 * Creates a Skija region from an SWT rectangle by converting it to a polygon.
	 */
	private io.github.humbleui.skija.Region createRectRegion(Rectangle rec) {
		final var rect = new IRect(rec.x, rec.y, rec.x + rec.width, rec.y + rec.height);

		return createPolygonM3Region(new int[] { rect.getLeft(), rect.getTop(), rect.getRight(), rect.getTop(),
				rect.getRight(), rect.getBottom(), rect.getLeft(), rect.getBottom() });

	}

	private io.github.humbleui.skija.Region createPolygonM3Region(int[] polygon) {

		final io.github.humbleui.skija.Region r = new io.github.humbleui.skija.Region();

		final var scaler = new DpiScalerUtil(m3Extension.getScaler());

		try (final var pathBuilder = new io.github.humbleui.skija.Path()) {
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