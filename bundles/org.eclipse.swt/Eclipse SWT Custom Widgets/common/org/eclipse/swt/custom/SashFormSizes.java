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
package org.eclipse.swt.custom;

/**
 * The pane limits of a {@link SashForm} (docs/m3/split-panels/SPLIT_PANELS.md, leaf SF1): minimum and maximum sizes
 * per pane in points or percent of the area, and the arithmetic that honours them, after the pattern of VS Code's
 * splitview and allotment (MIT, adapted): the panes get their weighted share; a pane over a limit is pinned to it and
 * the rest is shared again among the free panes by weight, until no pane breaks a limit; when even the minimums do
 * not fit, the panes give up space from the last one backwards. Pure arithmetic over int arrays, no SWT calls.
 */
final class SashFormSizes {
	int minimumUnit = SashForm.POINTS, maximumUnit = SashForm.POINTS;
	int [] minimums = new int [0], maximums = new int [0];

	boolean hasLimits () {
		for (int value : minimums) if (value > 0) return true;
		for (int value : maximums) if (value > 0) return true;
		return false;
	}

	/* the limit of a pane in points for an area, or 0 (none) */
	static int resolve (int [] values, int unit, int index, int area) {
		if (index >= values.length || values [index] <= 0) return 0;
		return unit == SashForm.PERCENT ? (int) ((long) values [index] * area / 100) : values [index];
	}

	int minimum (int index, int area) {
		return resolve (minimums, minimumUnit, index, area);
	}

	int maximum (int index, int area) {
		return resolve (maximums, maximumUnit, index, area);
	}

	/**
	 * The sizes of the panes for the space they share: the weighted shares with every pane inside its limits, summing
	 * to <code>available</code> exactly (the last free pane takes the rounding, as the stock layout gives the
	 * remainder to the last control).
	 *
	 * @param weights the panes' weights (all above 0)
	 * @param available the space of the panes (the area minus the sashes)
	 * @param area the area the percent limits refer to
	 */
	int [] sizes (long [] weights, int available, int area) {
		int n = weights.length;
		int [] sizes = new int [n];
		if (n == 0) return sizes;
		int [] min = new int [n], max = new int [n];
		long minTotal = 0;
		for (int i = 0; i < n; i++) {
			min [i] = minimum (i, area);
			int limit = maximum (i, area);
			max [i] = limit > 0 ? Math.max (limit, min [i]) : Integer.MAX_VALUE;
			minTotal += min [i];
		}
		if (minTotal >= available) {
			/* even the minimums do not fit: every pane at its minimum, the last ones give up space first */
			long excess = minTotal - available;
			for (int i = 0; i < n; i++) sizes [i] = min [i];
			for (int i = n - 1; i >= 0 && excess > 0; i--) {
				int give = (int) Math.min (excess, sizes [i]);
				sizes [i] -= give;
				excess -= give;
			}
			return sizes;
		}
		boolean [] pinned = new boolean [n];
		long free = available;
		/* each pass pins at least one pane or ends: n passes at most */
		for (int pass = 0; pass <= n; pass++) {
			long weightTotal = 0;
			for (int i = 0; i < n; i++) if (!pinned [i]) weightTotal += weights [i];
			if (weightTotal == 0) break;
			boolean changed = false;
			long assigned = 0;
			int lastFree = -1;
			for (int i = 0; i < n; i++) {
				if (pinned [i]) continue;
				sizes [i] = (int) (weights [i] * free / weightTotal);
				assigned += sizes [i];
				lastFree = i;
			}
			sizes [lastFree] += (int) (free - assigned);
			/* pin the panes that break a limit, the worst direction first so a pass cannot overshoot both ways */
			long over = 0, under = 0;
			for (int i = 0; i < n; i++) {
				if (pinned [i]) continue;
				if (sizes [i] < min [i]) under += min [i] - sizes [i];
				else if (sizes [i] > max [i]) over += sizes [i] - max [i];
			}
			for (int i = 0; i < n; i++) {
				if (pinned [i]) continue;
				if (under >= over && under > 0 && sizes [i] < min [i]) {
					sizes [i] = min [i];
					pinned [i] = changed = true;
					free -= min [i];
				} else if (over > under && sizes [i] > max [i]) {
					sizes [i] = max [i];
					pinned [i] = changed = true;
					free -= max [i];
				}
			}
			if (!changed) return sizes;
		}
		/* every pane pinned (all at maximum with space left): the last pane takes the rest */
		long total = 0;
		for (int size : sizes) total += size;
		sizes [n - 1] += (int) (available - total);
		return sizes;
	}

	/**
	 * The shift of a sash drag between two panes that keeps both inside their limits: <code>shift</code> moves space
	 * from the second pane to the first (negative: the other way).
	 */
	int clampShift (int index, int first, int second, int shift, int area) {
		int min1 = minimum (index, area), max1 = maximum (index, area), min2 = minimum (index + 1, area), max2 = maximum (index + 1, area);
		int low = Math.max (min1 - first, max2 > 0 ? second - max2 : Integer.MIN_VALUE);
		int high = Math.min (second - min2, max1 > 0 ? max1 - first : Integer.MAX_VALUE);
		if (low > high) return 0;
		return Math.max (low, Math.min (high, shift));
	}
}
