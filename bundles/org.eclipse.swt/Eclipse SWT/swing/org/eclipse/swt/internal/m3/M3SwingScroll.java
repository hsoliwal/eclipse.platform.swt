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
package org.eclipse.swt.internal.m3;

import java.awt.*;

import javax.swing.*;

/**
 * Scroll contract of the Swing-based Tree, Table and List in the viewport mode.
 * <ul>
 * <li>The top row is the row shown at the top of the viewport.</li>
 * <li>Setting the top row puts that row at the top, clamped to the scrollable range.</li>
 * <li>Showing a row scrolls the least amount that reveals it, and not at all when it is shown.</li>
 * </ul>
 * The row arithmetic is {@link M3SparseExtents} over rows of one pitch: a <code>JTable</code>, the
 * <code>JTreeTable</code> of a Tree with a fixed row height and a <code>JList</code> with one font lay
 * their rows out at one pitch from the top of their view. The scrollable range is the smaller of the
 * rows' extent and the view's height, as the scroll pane lays it out.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API.
 * </p>
 */
public final class M3SwingScroll {
	private final M3SparseExtents extents = new M3SparseExtents (1);

	/**
	 * Answers the row shown at the top of the viewport.
	 *
	 * @param viewport the viewport of the scroll pane
	 * @param rows the number of rows of the view
	 * @param pitch the height of one row, in pixels
	 * @return the top row, or -1 when there is no row
	 */
	public int topRow (JViewport viewport, int rows, int pitch) {
		if (rows <= 0 || pitch <= 0) return -1;
		layout (rows, pitch);
		return extents.rowAt (Math.max (0, viewport.getViewPosition ().y));
	}

	/**
	 * Scrolls the viewport so that a row is at its top, as far as the scrollable range allows.
	 *
	 * @param viewport the viewport of the scroll pane
	 * @param row the row to show at the top
	 * @param rows the number of rows of the view
	 * @param pitch the height of one row, in pixels
	 */
	public void setTopRow (JViewport viewport, int row, int rows, int pitch) {
		scrollTo (viewport, row, rows, pitch, M3SparseExtents.ALIGN_START);
	}

	/**
	 * Scrolls the viewport the least amount that shows a row; does nothing when the row is shown.
	 *
	 * @param viewport the viewport of the scroll pane
	 * @param row the row to show
	 * @param rows the number of rows of the view
	 * @param pitch the height of one row, in pixels
	 */
	public void showRow (JViewport viewport, int row, int rows, int pitch) {
		scrollTo (viewport, row, rows, pitch, M3SparseExtents.ALIGN_AUTO);
	}

	private void scrollTo (JViewport viewport, int row, int rows, int pitch, int align) {
		if (row < 0 || row >= rows || pitch <= 0) return;
		layout (rows, pitch);
		Container pane = viewport.getParent ();
		if (pane != null && !pane.isValid ()) pane.validate ();
		Point position = viewport.getViewPosition ();
		int extent = viewport.getExtentSize ().height;
		long target = extents.scrollOffsetFor (row, align, position.y, extent);
		long limit = Math.max (0L, (long) viewport.getViewSize ().height - extent);
		int y = (int) Math.min (target, limit);
		if (y != position.y) viewport.setViewPosition (new Point (position.x, y));
	}

	private void layout (int rows, int pitch) {
		if (extents.estimate () != pitch) extents.setEstimate (pitch);
		if (extents.rowCount () != rows) extents.reset (rows);
	}
}
