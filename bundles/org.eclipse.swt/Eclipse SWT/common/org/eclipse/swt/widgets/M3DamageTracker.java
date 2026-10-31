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

import org.eclipse.swt.internal.ViewportDamageRegion;

/**
 * Public M3 view of the viewport's damage region (enhanced API M3-DAMAGE).
 * <p>
 * A thin facade over {@code ViewportDamageRegion}, the region every SWT viewport records its repaint and
 * blit work in: rectangles and copies are added, the bounding box is taken and cleared. Results are ints
 * written into a caller-owned array (DOOP).
 * </p>
 *
 * @since 3.130
 */
public final class M3DamageTracker {
	private final ViewportDamageRegion region = new ViewportDamageRegion ();
	private final int [] box = new int [4];

	/** Adds a damaged rectangle; empty rectangles are ignored. */
	public void add (int x, int y, int width, int height) {
		if (width > 0 && height > 0) region.add (x, y, width, height);
	}

	/**
	 * Records a blit of the rectangle by {@code (dx, dy)}: the copy is counted and its destination joins the
	 * region (the recorder's contract: copied pixels are part of the frame's changed area, not repainted).
	 */
	public void addCopy (int x, int y, int width, int height, int dx, int dy) {
		region.addCopy (x, y, x + dx, y + dy, width, height);
	}

	public boolean isEmpty () {
		return region.isEmpty ();
	}

	/** Damaged area in pixels (overlaps counted once per stored rectangle). */
	public long area () {
		return region.area ();
	}

	/** Blits recorded since the last {@link #take}. */
	public long copyCount () {
		return region.copyCount ();
	}

	/**
	 * Writes the bounding box {@code x, y, width, height} into {@code out} and clears the region.
	 *
	 * @return false (and {@code out} untouched) when nothing is damaged
	 */
	public boolean take (int [] out) {
		if (region.isEmpty ()) {
			region.clear ();
			return false;
		}
		region.boundingBox (box);
		System.arraycopy (box, 0, out, 0, 4);
		region.clear ();
		return true;
	}
}
