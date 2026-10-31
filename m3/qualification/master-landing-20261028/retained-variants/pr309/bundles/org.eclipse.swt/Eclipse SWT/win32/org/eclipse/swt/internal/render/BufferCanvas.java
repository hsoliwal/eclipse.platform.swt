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
package org.eclipse.swt.internal.render;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/** A win32 backend that draws into a plane's {@link BackBuffer}. */
public interface BufferCanvas extends RowCanvas {
	/** Sets the back buffer the next frame draws into. */
	void target (BackBuffer buffer);

	/**
	 * The GC of the frame being drawn, for a listener of the widget's owner-draw events ({@link DrawnCustomDraw}): it
	 * draws on the frame's pixels, is made on first use inside a frame (after {@link #begin}, before any {@link #clip})
	 * and is the backend's until {@link #end}. The <code>custom</code> backend answers its own GC on the back buffer's
	 * device context; the M3 backends answer a <code>GCExtension</code> over the M3 renderer on the plane's surface.
	 * Null (the default): the backend has no GC, as on the render thread (rule 28), and the plane sends no events.
	 *
	 * @param control the plane's widget: the drawable, device, colours, font and native zoom of the GC
	 * @param buffer the plane's back buffer (the frame's pixels)
	 */
	default GC gc (Control control, BackBuffer buffer) {
		return null;
	}

	/**
	 * Narrows the frame's clip to a rectangle until {@link #unclip} (rule 24: a row that only widened draws the strip
	 * a resize exposed). Calls do not nest.
	 */
	void clip (int x, int y, int width, int height);

	/** Restores the frame's clip after {@link #clip}. */
	void unclip ();

	/**
	 * The fonts of the frame being drawn, by handle (brief rule 28): a canvas on the render thread makes its own fonts
	 * from these descriptors and never asks the platform about the UI thread's handles. Null (the UI thread): font handles
	 * are the platform's.
	 */
	void fonts (FontTable table);

	/** MODE_TRUTH v1 (census L10): the widget mode this backend realizes; a GC backend draws the custom mode. */
	default org.eclipse.swt.internal.WidgetMode route () {
		return org.eclipse.swt.internal.WidgetMode.CUSTOM;
	}

	/** BACKEND_REPORT v1: the backend's name for reports, for example <code>M3RowCanvas(raster)</code>. */
	default String backend () {
		return getClass ().getSimpleName ();
	}
}
