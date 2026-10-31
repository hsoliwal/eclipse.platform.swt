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
package org.eclipse.swt.internal.canvasext;

/**
 * BACKGROUND_PARITY v1 (census L16, real-app fidelity leaf): the stock background chain of a widget's <code>WM_PAINT</code>
 * (<code>Control.drawBackground (hDC, rect)</code>: the background control's image at its offset, else the parent's theme
 * part, else the inherited colour), handed by the widget to its M3 handler, which fills its frame's damage through it
 * before the Paint event, exactly where the stock path fills the paint device context.
 */
@FunctionalInterface
public interface M3BackgroundChain {
	/**
	 * Fills a rectangle of a device context with the widget's stock background.
	 *
	 * @param hdc the device context (the M3 frame's memory DC, client coordinates in pixels)
	 * @param x the left edge in pixels
	 * @param y the top edge in pixels
	 * @param width the width in pixels
	 * @param height the height in pixels
	 */
	void fill (long hdc, int x, int y, int width, int height);
}
