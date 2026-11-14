/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import java.util.Arrays;

/** Measurements returned by the platform text engine for the selected GC font. */
public final class FontMetrics {
	private final int[] metrics;
	FontMetrics(int[] metrics) {
		if (metrics == null || metrics.length != 5) throw new IllegalArgumentException("Invalid native font metrics");
		this.metrics = metrics.clone();
	}
	public int getAscent() { return metrics[0]; }
	public int getDescent() { return metrics[1]; }
	public int getAverageCharWidth() { return metrics[2]; }
	public double getAverageCharacterWidth() { return metrics[2]; }
	public int getLeading() { return metrics[3]; }
	public int getHeight() { return metrics[4]; }
	@Override public boolean equals(Object value) { return value instanceof FontMetrics f && Arrays.equals(metrics, f.metrics); }
	@Override public int hashCode() { return Arrays.hashCode(metrics); }
}
