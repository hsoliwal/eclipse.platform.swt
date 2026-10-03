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
 * Scroll geometry derived from logical model cardinality, one representative
 * row extent and the current body viewport.
 *
 * <p>Scrollbar units are logical rows rather than pixels. This avoids overflow
 * and makes scrollbar visibility/thumb calculations independent of whether the
 * logical content would span millions or billions of pixels.</p>
 */
final class VirtualScrollMetrics {
	private int logicalRows;
	private int sampleRowExtent = 1;
	private int viewportExtent;

	void configure (int logicalRows, int sampleRowExtent, int viewportExtent) {
		if (logicalRows < 0) throw new IllegalArgumentException ("negative logical rows");
		if (sampleRowExtent <= 0) throw new IllegalArgumentException ("non-positive sample row extent");
		if (viewportExtent < 0) throw new IllegalArgumentException ("negative viewport extent");
		this.logicalRows = logicalRows;
		this.sampleRowExtent = sampleRowExtent;
		this.viewportExtent = viewportExtent;
	}

	int logicalRows () {
		return logicalRows;
	}

	int sampleRowExtent () {
		return sampleRowExtent;
	}

	int viewportExtent () {
		return viewportExtent;
	}

	long estimatedContentExtent () {
		return Math.multiplyExact ((long)logicalRows, sampleRowExtent);
	}

	int visibleRows () {
		if (logicalRows == 0 || viewportExtent == 0) return 0;
		long rows = ((long)viewportExtent + sampleRowExtent - 1L) / sampleRowExtent;
		return (int)Math.min (logicalRows, Math.max (1L, rows));
	}

	boolean scrollbarNeeded () {
		return logicalRows > visibleRows ();
	}

	int minimum () {
		return 0;
	}

	int maximum () {
		/*
		 * SWT ScrollBar semantics use maximum together with thumb. Row units keep
		 * this bounded by logical cardinality rather than logical pixel extent.
		 */
		return logicalRows;
	}

	int thumb () {
		if (logicalRows == 0) return 0;
		return Math.max (1, visibleRows ());
	}

	int increment () {
		return logicalRows == 0 ? 0 : 1;
	}

	int pageIncrement () {
		return Math.max (1, visibleRows ());
	}

	int maxTopRow () {
		return Math.max (0, logicalRows - visibleRows ());
	}

	int clampTopRow (int requested) {
		return Math.max (0, Math.min (requested, maxTopRow ()));
	}
}
