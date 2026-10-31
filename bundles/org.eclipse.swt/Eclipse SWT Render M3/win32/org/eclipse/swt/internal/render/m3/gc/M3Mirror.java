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
package org.eclipse.swt.internal.render.m3.gc;

import jdk.m3.graphics.M3Matrix33;

/**
 * RTL_FRAME v1 (real-app fidelity leaf; census GF48): the frame of a right-to-left canvas is mirrored by its canvas matrix
 * (x to clientWidth - x, M3CanvasHandler), as the stock paint DC is (LAYOUT_RTL); a text run and an image are drawn
 * counter-flipped about their own box, so glyphs read and images look as authored while their place is mirrored, as GDI
 * draws text in a mirrored DC and the stock GC preserves bitmap orientation.
 */
final class M3Mirror {
	private M3Mirror () {
	}

	/**
	 * Runs a draw at a box flipped back about its centre when the GC is mirrored.
	 *
	 * @param canvas the GC's canvas
	 * @param mirrored whether the GC's style is <code>SWT.MIRRORED</code>
	 * @param left the box's left edge in the canvas's local pixels
	 * @param width the box's width in local pixels
	 * @param draw the draw
	 */
	static void unmirrored (IM3Canvas canvas, boolean mirrored, float left, float width, Runnable draw) {
		if (!mirrored) {
			draw.run ();
			return;
		}
		canvas.save ();
		try {
			canvas.setMatrix (canvas.getLocalToDevice ().makeConcat (new M3Matrix33 (-1, 0, 2 * left + width, 0, 1, 0, 0, 0, 1)));
			draw.run ();
		} finally {
			canvas.restore ();
		}
	}
}
