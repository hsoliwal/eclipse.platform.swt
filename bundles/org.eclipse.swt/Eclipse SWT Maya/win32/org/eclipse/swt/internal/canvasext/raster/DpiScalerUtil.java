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

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.canvasext.IDpiScaler;
import org.eclipse.swt.widgets.Display;

public class DpiScalerUtil implements IDpiScaler {

	private final IDpiScaler scaler;

	public DpiScalerUtil(IDpiScaler scaler) {
		this.scaler = scaler;
	}

	public DpiScalerUtil(int nativeZoom) {
		this.scaler = () -> nativeZoom;
	}

	public DpiScalerUtil(RasterResources resources) {
		this.scaler = resources.getScaler();
	}

	public IDpiScaler getScaler() {
		return scaler;
	}

	/**
	 * Scales a value up according to the native zoom factor. Uses integer
	 * arithmetic with rounding for precise results (e.g. for nativeZoom=125).
	 */
	private int scaleUp(int value) {
		/* floorDiv: '/' truncates toward zero, which moved every negative coordinate (above or left of the canvas) one pixel */
		return Math.floorDiv(value * getNativeZoom() + 50, 100);
	}

	/**
	 * Scales a value down according to the native zoom factor. Uses integer
	 * arithmetic with rounding for precise results (e.g. for nativeZoom=125).
	 */
	private int scaleDown(int value) {
		return Math.floorDiv(value * 100 + getNativeZoom() / 2, getNativeZoom());
	}

	public int getZoomedFontSize(int fontSize) {
		// dpi to inch
		fontSize = (fontSize * Display.getDefault().getDPI().y) / 72;
		return scaleUp(fontSize);
	}

	public Point scaleSize(int x, int y) {
		return new Point(scaleUp(x), scaleUp(y));
	}

	public Point scaleSurfaceSize(int width, int height) {
		return new Point(scaleUp(width), scaleUp(height));
	}

	public float autoScaleUp(float f) {
		return getNativeZoom() * f / 100f;
	}

	public int autoScaleUp(int value) {
		return scaleUp(value);
	}

	public float autoScaleDown(float width) {
		return (100f * width) / getNativeZoom();
	}

	/**
	 * Scales a float array down according to the native zoom factor. Each value is
	 * divided by nativeZoom/100, preserving float precision.
	 *
	 * @param values the array to scale down
	 * @return a new array with all values scaled down, or null if input is null
	 */
	public float[] autoScaleDown(float[] values) {
		if (values == null) {
			return null;
		}
		final float[] result = new float[values.length];
		final float factor = 100f / getNativeZoom();
		for (int i = 0; i < values.length; i++) {
			result[i] = values[i] * factor;
		}
		return result;
	}

	/**
	 * Scales a float value down according to the native zoom factor and rounds to
	 * the nearest integer.
	 *
	 * @param f the value to scale down
	 * @return the scaled and rounded integer value
	 */
	public int autoScaleDownToInt(float f) {
		return Math.round((100f * f) / getNativeZoom());
	}

	public Point scaleDown(Point point) {
		return new Point(scaleDown(point.x), scaleDown(point.y));
	}

	public float[] autoScaleUp(float[] values) {
		if (values == null) {
			return null;
		}
		final float[] result = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = autoScaleUp(values[i]);
		}
		return result;
	}

	public int[] autoScaleUp(int[] values) {
		if (values == null) {
			return null;
		}
		final int[] result = new int[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = autoScaleUp(values[i]);
		}
		return result;
	}

	@Override
	public int getNativeZoom() {
		return this.scaler.getNativeZoom();
	}

}
