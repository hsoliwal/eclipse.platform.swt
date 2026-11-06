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
 * Internal z-plane dirtiness policy for viewport-driven widgets.
 *
 * <p>The constants are ordered from the scrolling body toward transient chrome.
 * The class does not paint or create native children; it only describes which
 * independently managed planes are affected by an origin change.</p>
 */
final class ViewportLayerState {
	static final int BODY = 1 << 0;
	static final int FROZEN = 1 << 1;
	static final int HEADER = 1 << 2;
	static final int EDITOR = 1 << 3;
	static final int SCROLLBAR = 1 << 4;
	static final int FEEDBACK = 1 << 5;

	static final int ALL = BODY | FROZEN | HEADER | EDITOR | SCROLLBAR | FEEDBACK;

	private double originX;
	private double originY;
	private boolean initialized;

	void initialize (double x, double y) {
		originX = x;
		originY = y;
		initialized = true;
	}

	int scrollTo (double x, double y) {
		if (!initialized) {
			initialize (x, y);
			return 0;
		}
		boolean horizontal = Double.doubleToLongBits (originX) != Double.doubleToLongBits (x);
		boolean vertical = Double.doubleToLongBits (originY) != Double.doubleToLongBits (y);
		originX = x;
		originY = y;

		int dirty = 0;
		if (horizontal) {
			/*
			 * Normal column headers follow horizontal body projection.
			 * Frozen columns are a separate plane and therefore do not.
			 */
			dirty |= BODY | HEADER | SCROLLBAR;
		}
		if (vertical) {
			/*
			 * Frozen columns follow row projection vertically, but column
			 * headers remain stationary.
			 */
			dirty |= BODY | FROZEN | SCROLLBAR;
		}
		return dirty;
	}

	double originX () {
		return originX;
	}

	double originY () {
		return originY;
	}
}
