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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (text leaf L13)
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.TextLayout;

//FLLDCIM: IDL-LAW — L13 TEXT_FALLBACK_SAFE / GLYPH_RUN_SINK: one value for the arguments of TextLayout.draw on an M3 GC
/**
 * The arguments of one <code>TextLayout.draw</code> on an M3 GC (text leaf L13): the layout, its origin in points and the
 * selection, as <code>IExternalGC.textLayoutDraw</code> receives them. Both routes (glyph runs and the transparent fallback)
 * take this value.
 */
record M3TextLayoutCall(TextLayout layout, int x, int y, int selectionStart, int selectionEnd,
		Color selectionForeground, Color selectionBackground, int flags) {

	/** draws the layout on <code>gc</code> with this call's origin, selection and flags */
	void drawOn(org.eclipse.swt.graphics.GC gc, int originX, int originY) {
		layout.draw(gc, originX, originY, selectionStart, selectionEnd, selectionForeground, selectionBackground, flags);
	}
}
