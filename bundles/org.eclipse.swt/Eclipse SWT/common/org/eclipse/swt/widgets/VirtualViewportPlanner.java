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
 * Toolkit-neutral plan for a logical virtual widget and its bounded paint window.
 *
 * <p>The planner owns coordinates, selection and repaint geometry only.  Native
 * TableItem/TreeItem facades are deliberately not evicted here: once a facade is
 * exposed through SWT API or SetData it remains identity-pinned until normal SWT
 * removal/disposal.  Painting can nevertheless remain limited to the visible
 * window plus a small overscan.</p>
 */
final class VirtualViewportPlanner {
	static final int DEFAULT_OVERSCAN_ROWS = 8;

	private final VirtualSelectionModel selection = new VirtualSelectionModel ();
	private final VirtualScrollMetrics scrollMetrics = new VirtualScrollMetrics ();
	private int logicalCount;
	private int firstVisible;
	private int visibleCount;
	private int overscanRows = DEFAULT_OVERSCAN_ROWS;
	private int repaintLockDepth;
	private int generation;

	int logicalCount () {
		return logicalCount;
	}

	void setLogicalCount (int count) {
		if (count < 0) throw new IllegalArgumentException ("negative logical count");
		if (count == logicalCount) return;
		selection.setLogicalCount (count);
		logicalCount = count;
		clampViewport ();
		generation++;
	}

	void insert (int index, int count) {
		if (count < 0 || index < 0 || index > logicalCount) throw new IllegalArgumentException ("invalid insert");
		if (count == 0) return;
		selection.insert (index, count);
		if (index <= firstVisible && logicalCount != 0) firstVisible = Math.addExact (firstVisible, count);
		logicalCount = Math.addExact (logicalCount, count);
		clampViewport ();
		generation++;
	}

	void remove (int index, int count) {
		if (count < 0 || index < 0 || index > logicalCount - count) throw new IllegalArgumentException ("invalid remove");
		if (count == 0) return;
		selection.remove (index, count);
		if (index < firstVisible) firstVisible -= Math.min (count, firstVisible - index);
		logicalCount -= count;
		clampViewport ();
		generation++;
	}

	void setViewport (int first, int visibleRows) {
		if (first < 0 || visibleRows < 0) throw new IllegalArgumentException ("negative viewport");
		int nextFirst = logicalCount == 0 ? 0 : Math.min (first, logicalCount - 1);
		int nextVisible = Math.min (visibleRows, Math.max (0, logicalCount - nextFirst));
		if (nextFirst == firstVisible && nextVisible == visibleCount) return;
		firstVisible = nextFirst;
		visibleCount = nextVisible;
		generation++;
	}

	void setUniformGeometry (
			int logicalRows, int sampleRowExtent, int viewportExtent, int requestedTopRow) {
		scrollMetrics.configure (logicalRows, sampleRowExtent, viewportExtent);
		setLogicalCount (logicalRows);
		int top = scrollMetrics.clampTopRow (requestedTopRow);
		setViewport (top, scrollMetrics.visibleRows ());
	}

	VirtualScrollMetrics scrollMetrics () {
		return scrollMetrics;
	}

	int firstVisible () {
		return firstVisible;
	}

	int visibleCount () {
		return visibleCount;
	}

	void setOverscanRows (int rows) {
		if (rows < 0) throw new IllegalArgumentException ("negative overscan");
		if (rows == overscanRows) return;
		overscanRows = rows;
		generation++;
	}

	int overscanRows () {
		return overscanRows;
	}

	int paintStart () {
		return Math.max (0, firstVisible - overscanRows);
	}

	int paintEndExclusive () {
		long end = (long) firstVisible + visibleCount + overscanRows;
		return (int) Math.min (logicalCount, end);
	}

	boolean isVisible (int index) {
		return 0 <= index && index < logicalCount
				&& firstVisible <= index && index < firstVisible + visibleCount;
	}

	boolean isPaintCandidate (int index) {
		return paintStart () <= index && index < paintEndExclusive ();
	}

	VirtualSelectionModel selection () {
		return selection;
	}

	void beginRepaintLock () {
		repaintLockDepth++;
	}

	void endRepaintLock () {
		if (repaintLockDepth == 0) throw new IllegalStateException ("repaint lock underflow");
		repaintLockDepth--;
		generation++;
	}

	boolean repaintLocked () {
		return repaintLockDepth != 0;
	}

	int generation () {
		return generation;
	}

	private void clampViewport () {
		if (logicalCount == 0) {
			firstVisible = visibleCount = 0;
			return;
		}
		firstVisible = Math.min (firstVisible, logicalCount - 1);
		visibleCount = Math.min (visibleCount, logicalCount - firstVisible);
	}
}
