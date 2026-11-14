/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.graphics.Rectangle;

/** Shared mobile superclass for client-area controls. */
public abstract class Scrollable extends Control {
	Scrollable(Composite parent, int style) {
		super(parent, style);
	}

	Scrollable(Display display, int style) {
		super(display, style);
	}

	public Rectangle getClientArea() {
		checkWidget();
		int border = getBorderWidth();
		return new Rectangle(
				0, 0, Math.max(0, width() - 2 * border), Math.max(0, height() - 2 * border));
	}

	public Rectangle computeTrim(int x, int y, int w, int h) {
		checkWidget();
		int border = getBorderWidth();
		return new Rectangle(x - border, y - border, w + 2 * border, h + 2 * border);
	}
}
