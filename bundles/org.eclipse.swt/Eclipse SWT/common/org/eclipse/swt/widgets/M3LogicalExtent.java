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
package org.eclipse.swt.widgets;

/**
 * Public M3 view of the viewport's logical geometry (enhanced API M3-SCROLL-INFO, M3-SCROLLBAR-GEOMETRY).
 * <p>
 * A thin facade over the package-private {@code VirtualScrollMetrics}: logical extents are {@code long}
 * (they may exceed the int range) and the scroll bar is an int projection of them. Mode-neutral; results
 * are ints and longs.
 * </p>
 *
 * @since 3.130
 */
public final class M3LogicalExtent {
	private final VirtualScrollMetrics metrics = new VirtualScrollMetrics ();

	/**
	 * States the geometry.
	 *
	 * @param rows logical rows, {@code >= 0}
	 * @param rowExtent pixels per row, {@code > 0}
	 * @param viewportExtent viewport pixels along the scroll axis, {@code >= 0}
	 * @exception IllegalArgumentException if an argument is out of range
	 */
	public void configure (long rows, int rowExtent, int viewportExtent) {
		metrics.configure (rows, rowExtent, viewportExtent);
	}

	/** Logical content extent in pixels ({@code rows * rowExtent}). */
	public long contentExtent () {
		return metrics.estimatedContentExtent ();
	}

	/** Rows a full viewport shows. */
	public int visibleRows () {
		return metrics.visibleRows ();
	}

	/** Largest valid top row. */
	public long maxTopRow () {
		return metrics.maxTopRow ();
	}

	/** True when the content does not fit the viewport. */
	public boolean scrollbarNeeded () {
		return metrics.scrollbarNeeded ();
	}

	/**
	 * Projects the scroll bar into a track of {@code track} pixels: {@code out[0]} thumb offset, {@code out[1]}
	 * thumb length (at least {@code min(track, 8)}), for the given top row.
	 */
	public void thumb (long topRow, int track, int [] out) {
		int maximum = Math.max (1, metrics.maximum ()), thumb = metrics.thumb ();
		int len = (int) Math.max (Math.min (track, 8), Math.min (track, (long) track * thumb / maximum));
		int range = Math.max (1, maximum - thumb), selection = metrics.selectionForTopRow (topRow);
		out [0] = (int) ((long) (track - len) * Math.min (selection, range) / range);
		out [1] = len;
	}
}
