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
 * Logical viewport over a virtual Tree's flat visible-row projection.
 *
 * <p>The viewport keeps exact logical row coordinates in {@code long}. Native
 * scrollbar ranges and native row residency are projections of this state, not
 * its authority. The paint window is the visible range plus bounded overscan.</p>
 */
final class VirtualTreeViewport {
	static final int DEFAULT_OVERSCAN_ROWS = 8;

	private final VirtualTreeVisibleProjection projection;
	private final VirtualScrollMetrics scrollMetrics = new VirtualScrollMetrics ();
	private final int overscanRows;
	private long topRow;
	private long generation;

	VirtualTreeViewport (VirtualTreeVisibleProjection projection) {
		this (projection, DEFAULT_OVERSCAN_ROWS);
	}

	VirtualTreeViewport (VirtualTreeVisibleProjection projection, int overscanRows) {
		if (projection == null) throw new IllegalArgumentException ("projection");
		if (overscanRows < 0) throw new IllegalArgumentException ("negative overscan");
		this.projection = projection;
		this.overscanRows = overscanRows;
	}

	void configureGeometry (int sampleRowExtent, int viewportExtent) {
		long beforeTop = topRow;
		scrollMetrics.configure (projection.visibleRowCount (), sampleRowExtent, viewportExtent);
		topRow = scrollMetrics.clampTopRow (topRow);
		if (beforeTop != topRow) generation++;
	}

	void refreshLogicalRange () {
		long beforeTop = topRow;
		scrollMetrics.configure (
				projection.visibleRowCount (),
				scrollMetrics.sampleRowExtent (),
				scrollMetrics.viewportExtent ());
		topRow = scrollMetrics.clampTopRow (topRow);
		if (beforeTop != topRow) generation++;
	}

	void setTopRow (long requested) {
		long next = scrollMetrics.clampTopRow (requested);
		if (next == topRow) return;
		topRow = next;
		generation++;
	}

	void setTopMaterializedId (int materializedId) {
		long row = projection.visibleIndexOf (materializedId);
		if (row >= 0) setTopRow (row);
	}

	long topRow () {
		return topRow;
	}

	long visibleRowCount () {
		return scrollMetrics.logicalRowCount ();
	}

	int visibleRows () {
		return scrollMetrics.visibleRows ();
	}

	int scrollbarMaximum () {
		return scrollMetrics.maximum ();
	}

	int scrollbarThumb () {
		return scrollMetrics.thumb ();
	}

	int scrollbarPageIncrement () {
		return scrollMetrics.pageIncrement ();
	}

	int scrollbarSelection () {
		return scrollMetrics.selectionForTopRow (topRow);
	}

	void setScrollbarSelection (int selection) {
		setTopRow (scrollMetrics.topRowForSelection (selection));
	}

	long firstPaintRow () {
		return Math.max (0L, topRow - overscanRows);
	}

	int paintRowCount () {
		long total = visibleRowCount ();
		if (total == 0) return 0;
		long first = firstPaintRow ();
		long visibleEnd = Math.min (total, Math.addExact (topRow, visibleRows ()));
		long end = Math.min (total, Math.addExact (visibleEnd, overscanRows));
		return Math.toIntExact (end - first);
	}

	VirtualTreeVisibleProjection.Row [] visibleWindow () {
		return projection.window (topRow, visibleRows ());
	}

	VirtualTreeVisibleProjection.Row [] paintWindow () {
		return projection.window (firstPaintRow (), paintRowCount ());
	}

	void ensureVisible (long row) {
		long total = visibleRowCount ();
		if (row < 0 || row >= total) throw new IllegalArgumentException ("row outside viewport model");
		int visible = visibleRows ();
		if (visible <= 0) {
			setTopRow (row);
			return;
		}
		if (row < topRow) {
			setTopRow (row);
			return;
		}
		long end = Math.addExact (topRow, visible);
		if (row >= end) setTopRow (row - visible + 1L);
	}

	long generation () {
		return generation;
	}
}
