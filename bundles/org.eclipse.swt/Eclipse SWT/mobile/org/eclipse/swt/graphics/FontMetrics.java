/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
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
