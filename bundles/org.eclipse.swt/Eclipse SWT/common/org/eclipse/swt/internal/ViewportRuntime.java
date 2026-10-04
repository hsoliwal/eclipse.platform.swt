/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

/**
 * Shared model-only runtime for viewport-driven SWT controls.
 *
 * <p>This class owns no native resources and does not replace SWT's public
 * {@code GC}, {@code ScrollBar}, {@code Item}, or event contracts. It centralizes
 * origin/layer invalidation and scrollbar fixed-point calculations so platform
 * widgets and custom widgets can share the same viewport mechanics.</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ViewportRuntime {

	public static final int BODY = 1 << 0;
	public static final int FROZEN = 1 << 1;
	public static final int HEADER = 1 << 2;
	public static final int EDITOR = 1 << 3;
	public static final int SCROLLBAR = 1 << 4;
	public static final int FEEDBACK = 1 << 5;
	public static final int ALL = BODY | FROZEN | HEADER | EDITOR | SCROLLBAR | FEEDBACK;

	public static final int AUTO = 0;
	public static final int ALWAYS = 1;
	public static final int NEVER = 2;

	public record PixelLayout (
			boolean horizontalVisible,
			boolean verticalVisible,
			boolean cornerVisible,
			int bodyWidth,
			int bodyHeight,
			long contentWidth,
			long contentHeight) {
	}

	public record RowLayout (
			boolean horizontalVisible,
			boolean verticalVisible,
			boolean cornerVisible,
			int bodyWidth,
			int bodyHeight,
			int headerWidth,
			int headerHeight,
			int visibleRows,
			long logicalRows,
			long estimatedContentHeight,
			long logicalContentWidth) {
	}

	private double originX;
	private double originY;
	private boolean originInitialized;

	public void initializeOrigin (double x, double y) {
		requireFinite (x, y);
		originX = x;
		originY = y;
		originInitialized = true;
	}

	/**
	 * Updates the logical viewport origin and returns the dirty z-plane mask.
	 */
	public int scrollTo (double x, double y) {
		requireFinite (x, y);
		if (!originInitialized) {
			initializeOrigin (x, y);
			return 0;
		}
		boolean horizontal = Double.doubleToLongBits (originX) != Double.doubleToLongBits (x);
		boolean vertical = Double.doubleToLongBits (originY) != Double.doubleToLongBits (y);
		originX = x;
		originY = y;

		int dirty = 0;
		if (horizontal) dirty |= BODY | HEADER | SCROLLBAR;
		if (vertical) dirty |= BODY | FROZEN | SCROLLBAR;
		return dirty;
	}

	public double originX () {
		return originX;
	}

	public double originY () {
		return originY;
	}

	public boolean originInitialized () {
		return originInitialized;
	}

	/**
	 * Solves arbitrary pixel-sized content and scrollbar visibility to a fixed
	 * point. This is the general form used by ScrolledComposite-like controls.
	 */
	public static PixelLayout solvePixels (
			int outerWidth,
			int outerHeight,
			long contentWidth,
			long contentHeight,
			int horizontalBarHeight,
			int verticalBarWidth,
			int horizontalPolicy,
			int verticalPolicy) {
		if (outerWidth < 0 || outerHeight < 0) {
			throw new IllegalArgumentException ("negative viewport extent");
		}
		if (contentWidth < 0 || contentHeight < 0) {
			throw new IllegalArgumentException ("negative logical extent");
		}
		if (horizontalBarHeight < 0 || verticalBarWidth < 0) {
			throw new IllegalArgumentException ("negative scrollbar extent");
		}
		checkPolicy (horizontalPolicy);
		checkPolicy (verticalPolicy);

		boolean horizontal = horizontalPolicy == ALWAYS;
		boolean vertical = verticalPolicy == ALWAYS;
		for (int pass = 0; pass < 4; pass++) {
			int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
			int bodyHeight = Math.max (0, outerHeight - (horizontal ? horizontalBarHeight : 0));
			boolean nextHorizontal = policyVisible (horizontalPolicy, contentWidth > bodyWidth);
			boolean nextVertical = policyVisible (verticalPolicy, contentHeight > bodyHeight);
			if (horizontal == nextHorizontal && vertical == nextVertical) break;
			horizontal = nextHorizontal;
			vertical = nextVertical;
		}

		int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
		int bodyHeight = Math.max (0, outerHeight - (horizontal ? horizontalBarHeight : 0));
		return new PixelLayout (
				horizontal,
				vertical,
				horizontal && vertical,
				bodyWidth,
				bodyHeight,
				contentWidth,
				contentHeight);
	}

	/**
	 * Solves a uniform-row viewport. This preserves the historical viewport rule
	 * that a partially visible final row counts as visible.
	 */
	public static RowLayout solveRows (
			int outerWidth,
			int outerHeight,
			int headerHeight,
			long logicalRows,
			int sampleRowHeight,
			long logicalContentWidth,
			int horizontalBarHeight,
			int verticalBarWidth,
			int horizontalPolicy,
			int verticalPolicy) {
		if (outerWidth < 0 || outerHeight < 0) {
			throw new IllegalArgumentException ("negative viewport extent");
		}
		if (headerHeight < 0 || headerHeight > outerHeight) {
			throw new IllegalArgumentException ("invalid header extent");
		}
		if (logicalRows < 0 || logicalContentWidth < 0) {
			throw new IllegalArgumentException ("negative logical extent");
		}
		if (sampleRowHeight <= 0) {
			throw new IllegalArgumentException ("non-positive sample row extent");
		}
		if (horizontalBarHeight < 0 || verticalBarWidth < 0) {
			throw new IllegalArgumentException ("negative scrollbar extent");
		}
		checkPolicy (horizontalPolicy);
		checkPolicy (verticalPolicy);

		boolean horizontal = horizontalPolicy == ALWAYS;
		boolean vertical = verticalPolicy == ALWAYS;
		for (int pass = 0; pass < 4; pass++) {
			int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
			int bodyHeight = Math.max (
					0, outerHeight - headerHeight - (horizontal ? horizontalBarHeight : 0));
			boolean nextHorizontal = policyVisible (
					horizontalPolicy, logicalContentWidth > bodyWidth);
			int visibleRows = visibleRows (logicalRows, sampleRowHeight, bodyHeight);
			boolean nextVertical = policyVisible (verticalPolicy, logicalRows > visibleRows);
			if (horizontal == nextHorizontal && vertical == nextVertical) break;
			horizontal = nextHorizontal;
			vertical = nextVertical;
		}

		int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
		int bodyHeight = Math.max (
				0, outerHeight - headerHeight - (horizontal ? horizontalBarHeight : 0));
		int visibleRows = visibleRows (logicalRows, sampleRowHeight, bodyHeight);
		return new RowLayout (
				horizontal,
				vertical,
				horizontal && vertical,
				bodyWidth,
				bodyHeight,
				bodyWidth,
				headerHeight,
				visibleRows,
				logicalRows,
				saturatedMultiply (logicalRows, sampleRowHeight),
				logicalContentWidth);
	}

	private static boolean policyVisible (int policy, boolean automatic) {
		return switch (policy) {
			case ALWAYS -> true;
			case NEVER -> false;
			default -> automatic;
		};
	}

	private static int visibleRows (long logicalRows, int sampleRowHeight, int bodyHeight) {
		if (logicalRows == 0 || bodyHeight == 0) return 0;
		long rows = ((long)bodyHeight + sampleRowHeight - 1L) / sampleRowHeight;
		return (int)Math.min (logicalRows, Math.max (1L, rows));
	}

	private static long saturatedMultiply (long value, int multiplier) {
		if (value == 0) return 0;
		if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
		return value * multiplier;
	}

	private static void checkPolicy (int policy) {
		if (policy != AUTO && policy != ALWAYS && policy != NEVER) {
			throw new IllegalArgumentException ("invalid scrollbar policy");
		}
	}

	private static void requireFinite (double x, double y) {
		if (!Double.isFinite (x) || !Double.isFinite (y)) {
			throw new IllegalArgumentException ("non-finite viewport origin");
		}
	}
}
