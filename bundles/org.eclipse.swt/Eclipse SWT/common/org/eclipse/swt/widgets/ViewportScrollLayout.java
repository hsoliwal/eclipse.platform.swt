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
package org.eclipse.swt.widgets;

/**
 * Resolves viewport body/chrome geometry when scrollbar visibility on one axis
 * changes the available viewport on the other axis.
 *
 * <p>The solver is deliberately model-first: vertical demand is derived from a
 * logical row count and one representative row extent, while horizontal demand
 * is derived from logical content width. Scrollbar controls are consumers of
 * the result, not the source of truth.</p>
 */
final class ViewportScrollLayout {
	static final int AUTO = 0;
	static final int ALWAYS = 1;
	static final int NEVER = 2;

	record Result (
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

	private ViewportScrollLayout () {
	}

	static Result solve (
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

		/*
		 * Two axes can only force each other once, but allow a few fixed-point
		 * passes so platform-specific zero-width bars/policies remain harmless.
		 */
		for (int pass = 0; pass < 4; pass++) {
			int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
			int bodyHeight = Math.max (
					0, outerHeight - headerHeight - (horizontal ? horizontalBarHeight : 0));

			boolean nextHorizontal = policyVisible (
					horizontalPolicy, logicalContentWidth > bodyWidth);
			int visibleRows = visibleRows (logicalRows, sampleRowHeight, bodyHeight);
			boolean nextVertical = policyVisible (
					verticalPolicy, logicalRows > visibleRows);

			if (horizontal == nextHorizontal && vertical == nextVertical) break;
			horizontal = nextHorizontal;
			vertical = nextVertical;
		}

		int bodyWidth = Math.max (0, outerWidth - (vertical ? verticalBarWidth : 0));
		int bodyHeight = Math.max (
				0, outerHeight - headerHeight - (horizontal ? horizontalBarHeight : 0));
		int visibleRows = visibleRows (logicalRows, sampleRowHeight, bodyHeight);

		return new Result (
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

	private static long saturatedMultiply (long value, int multiplier) {
		if (value == 0) return 0;
		if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
		return value * multiplier;
	}

	private static boolean policyVisible (int policy, boolean autoValue) {
		return switch (policy) {
			case ALWAYS -> true;
			case NEVER -> false;
			default -> autoValue;
		};
	}

	private static int visibleRows (long logicalRows, int sampleRowHeight, int bodyHeight) {
		if (logicalRows == 0 || bodyHeight == 0) return 0;
		long rows = ((long)bodyHeight + sampleRowHeight - 1L) / sampleRowHeight;
		return (int)Math.min (logicalRows, Math.max (1L, rows));
	}

	private static void checkPolicy (int policy) {
		if (policy != AUTO && policy != ALWAYS && policy != NEVER) {
			throw new IllegalArgumentException ("invalid scrollbar policy");
		}
	}
}
