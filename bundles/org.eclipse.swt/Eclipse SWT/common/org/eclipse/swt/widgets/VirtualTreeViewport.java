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

import org.eclipse.swt.internal.*;

/**
 * Logical viewport over a virtual Tree's flat visible-row projection.
 *
 * <p>The tree keeps its topology/projection specialization, while the generic
 * visible + overscan window is owned by {@link ViewportRuntime.RowWindow}.
 * Native scrollbar ranges and native row residency remain projections of this
 * state, not its authority.</p>
 */
final class VirtualTreeViewport {
	static final int DEFAULT_OVERSCAN_ROWS = ViewportRuntime.RowWindow.DEFAULT_OVERSCAN_ROWS;

	private final VirtualTreeVisibleProjection projection;
	private final VirtualScrollMetrics scrollMetrics = new VirtualScrollMetrics ();
	private final ViewportRuntime.RowWindow window = new ViewportRuntime.RowWindow ();
	private long generation;

	VirtualTreeViewport (VirtualTreeVisibleProjection projection) {
		this (projection, DEFAULT_OVERSCAN_ROWS);
	}

	VirtualTreeViewport (VirtualTreeVisibleProjection projection, int overscanRows) {
		if (projection == null) throw new IllegalArgumentException ("projection");
		if (overscanRows < 0) throw new IllegalArgumentException ("negative overscan");
		this.projection = projection;
		window.setOverscanRows (overscanRows);
	}

	void configureGeometry (int sampleRowExtent, int viewportExtent) {
		long beforeTop = topRow ();
		scrollMetrics.configure (projection.visibleRowCount (), sampleRowExtent, viewportExtent);
		long top = scrollMetrics.clampTopRow (beforeTop);
		syncWindow (top);
		if (beforeTop != top) generation++;
	}

	void refreshLogicalRange () {
		long beforeTop = topRow ();
		scrollMetrics.configure (
				projection.visibleRowCount (),
				scrollMetrics.sampleRowExtent (),
				scrollMetrics.viewportExtent ());
		long top = scrollMetrics.clampTopRow (beforeTop);
		syncWindow (top);
		if (beforeTop != top) generation++;
	}

	void setTopRow (long requested) {
		long next = scrollMetrics.clampTopRow (requested);
		if (next == topRow ()) {
			syncWindow (next);
			return;
		}
		syncWindow (next);
		generation++;
	}

	void setTopMaterializedId (int materializedId) {
		long row = projection.visibleIndexOf (materializedId);
		if (row >= 0) setTopRow (row);
	}

	long topRow () {
		return window.firstVisible ();
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
		return scrollMetrics.selectionForTopRow (topRow ());
	}

	void setScrollbarSelection (int selection) {
		setTopRow (scrollMetrics.topRowForSelection (selection));
	}

	long firstPaintRow () {
		return window.paintStart ();
	}

	int paintRowCount () {
		return window.paintCount ();
	}

	VirtualTreeVisibleProjection.Row [] visibleWindow () {
		return projection.window (topRow (), visibleRows ());
	}

	VirtualTreeVisibleProjection.Row [] paintWindow () {
		return projection.window (firstPaintRow (), paintRowCount ());
	}

	void ensureVisible (long row) {
		long total = visibleRowCount ();
		if (row < 0 || row >= total) {
			throw new IllegalArgumentException ("row outside viewport model");
		}
		int visible = visibleRows ();
		if (visible <= 0) {
			setTopRow (row);
			return;
		}
		if (row < topRow ()) {
			setTopRow (row);
			return;
		}
		long end = saturatedAdd (topRow (), visible);
		if (row >= end) setTopRow (row - visible + 1L);
	}

	long generation () {
		return generation;
	}

	private void syncWindow (long top) {
		window.setLogicalCount (scrollMetrics.logicalRowCount ());
		window.setViewport (top, scrollMetrics.visibleRows ());
	}

	private static long saturatedAdd (long value, int delta) {
		if (delta > 0 && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
		return value + delta;
	}
}
