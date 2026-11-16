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

/** A custom-drawn SWT Canvas, with painting and input owned by the original Control. */
public class Canvas extends Composite {
	public Canvas(Composite parent, int style) { super(parent, style); }
	Canvas(Display display, int style) { super(display, style); }
	/**
	 * Scrolls a rectangle of the receiver. The retained pixels move inside the backing store and only the
	 * part of the source that the move uncovered is repainted; children that intersect the rectangle move
	 * with it when <code>all</code> is set.
	 */
	public void scroll(int destX, int destY, int x, int y, int width, int height, boolean all) {
		checkWidget();
		if (width <= 0 || height <= 0) return;
		int dx = destX - x, dy = destY - y;
		if (dx == 0 && dy == 0) return;
		scrollArea(destX, destY, x, y, width, height);
		if (!all) return;
		for (Control child : getChildren()) {
			org.eclipse.swt.graphics.Rectangle bounds = child.getBounds();
			if (bounds.intersects(x, y, width, height)) child.setLocation(bounds.x + dx, bounds.y + dy);
		}
	}
}
