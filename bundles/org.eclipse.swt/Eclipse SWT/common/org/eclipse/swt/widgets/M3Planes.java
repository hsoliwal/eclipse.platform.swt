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
 * Public M3 view of the viewport's retained planes (enhanced API M3-PLANES).
 * <p>
 * A thin facade over the package-private {@code ViewportLayerState}: the viewport keeps one origin and
 * answers, for each scroll, which planes are dirty as a bit mask. A vertical scroll never dirties the
 * HEADER plane; a horizontal scroll never dirties the FROZEN plane. Mode-neutral; the result is an int mask.
 * </p>
 *
 * @since 3.130
 */
public final class M3Planes {
	public static final int BODY = ViewportLayerState.BODY, FROZEN = ViewportLayerState.FROZEN, HEADER = ViewportLayerState.HEADER,
			EDITOR = ViewportLayerState.EDITOR, SCROLLBAR = ViewportLayerState.SCROLLBAR, FEEDBACK = ViewportLayerState.FEEDBACK,
			ALL = ViewportLayerState.ALL;
	private final ViewportLayerState layers = new ViewportLayerState ();

	/** Sets the origin without dirtying any plane. */
	public void initialize (double x, double y) {
		layers.initialize (x, y);
	}

	/**
	 * Moves the origin and returns the mask of dirty planes (0 when nothing moved; the first call only
	 * initializes the origin).
	 *
	 * @exception IllegalArgumentException if a coordinate is not finite
	 */
	public int scrollTo (double x, double y) {
		return layers.scrollTo (x, y);
	}

	public double originX () {
		return layers.originX ();
	}

	public double originY () {
		return layers.originY ();
	}
}
