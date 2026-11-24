/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
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
 *     Hitesh Soliwal (Synexia) - fractional-density capture bounds
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

/**
 * Rounding between SWT logical pixels and the native pixels of a transport
 * whose display density may be fractional.
 * <p>
 * Each conversion rounds to the nearest unit, so a reported logical extent can
 * map back to slightly more native pixels than it was measured from. At density
 * 2.625 a content box 2209 native pixels high is reported as 842 logical pixels
 * (841.52 rounded), and 842 logical pixels map back to 2210 native pixels.
 * {@link #captureSource} keeps that whole reported area capturable.
 * </p>
 */
public final class MobileScale {
	private MobileScale() {}

	/** Nearest native pixel coordinate or extent for a logical one. */
	public static int physical(int logical, float density) { return Math.round(logical * density); }

	/** Nearest logical pixel coordinate or extent for a native one. */
	public static int logical(int physical, float density) { return Math.round(physical / density); }

	/**
	 * Native source rectangle for a capture of the visible window.
	 * <p>
	 * The content box is in native window pixels. The request is in logical
	 * pixels relative to the content box's top-left corner. Each axis of the
	 * request is accepted when its rounded native span fits inside the content
	 * box, or when it lies inside the logical extent that the transport reports
	 * for that box: {@code logical(contentRight - contentLeft)} by
	 * {@code logical(contentBottom - contentTop)}. A native span that only the
	 * second rule accepts is clamped to the content box and keeps at least one
	 * native pixel. Any other request is outside the visible window.
	 * </p>
	 *
	 * @return the native {@code {left, top, right, bottom}} edges
	 * @throws IllegalArgumentException if the extent is not positive, or the
	 *         request is outside the visible window
	 * @throws ArithmeticException if a native edge overflows {@code int}
	 */
	public static int[] captureSource(float density, int contentLeft, int contentTop,
			int contentRight, int contentBottom, int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("Capture extent must be positive");
		int left = Math.addExact(contentLeft, physical(x, density));
		int top = Math.addExact(contentTop, physical(y, density));
		int right = Math.addExact(left, physical(width, density));
		int bottom = Math.addExact(top, physical(height, density));
		boolean horizontal = right <= contentRight || (long) x + width <= logical(contentRight - contentLeft, density);
		boolean vertical = bottom <= contentBottom || (long) y + height <= logical(contentBottom - contentTop, density);
		if (x < 0 || y < 0 || !horizontal || !vertical) throw new IllegalArgumentException("Capture outside visible SWT window");
		if (right > contentRight) { right = contentRight; left = Math.min(left, right - 1); }
		if (bottom > contentBottom) { bottom = contentBottom; top = Math.min(top, bottom - 1); }
		return new int[] {left, top, right, bottom};
	}
}
