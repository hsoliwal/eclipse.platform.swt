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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;

/** Scrollable geometry owner; native scrollbars are an explicit unsupported lane. */
public abstract class Scrollable extends Control {
	public Scrollable(Composite parent, int style) { super(parent, supportedStyle(style)); }
	/**
	 * Platform-internal software-scrolling seam for mobile list implementations:
	 * retain original SWT scroll style flags without claiming native ScrollBar peers.
	 */
	protected Scrollable(Composite parent, int style, boolean softwareScroll) {
		super(parent, softwareScroll ? style : supportedStyle(style));
	}
	Scrollable(Display display, int style) { super(display, supportedStyle(style)); }
	private static int supportedStyle(int style) {
		if ((style & (SWT.H_SCROLL | SWT.V_SCROLL)) != 0) throw new UnsupportedOperationException("Native mobile scrollbars are not implemented");
		return style;
	}
	public Rectangle getClientArea() {
		checkWidget(); org.eclipse.swt.graphics.Point size = getSize();
		return new Rectangle(0, 0, Math.max(0, size.x - 2 * getBorderWidth()), Math.max(0, size.y - 2 * getBorderWidth()));
	}
	public Rectangle computeTrim(int x, int y, int width, int height) {
		checkWidget(); int border = getBorderWidth(); return new Rectangle(x - border, y - border, width + 2 * border, height + 2 * border);
	}
}
