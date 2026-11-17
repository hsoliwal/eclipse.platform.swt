/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

import org.eclipse.swt.internal.maya.impl.Library;

/**
 * maya's canvas layer above the platform's lowest common denominator (a window and its pixels): it draws a whole
 * packed draw-command stream (<code>org.eclipse.swt.internal.gpu.OpStream</code>) on a canvas in one native call
 * (<code>native/cc/swt/M3Canvas.cc</code>), with the pixel conventions of
 * <code>OpStreamReplay</code>.
 */
public final class M3Canvas {
	static {
		Library.staticLoad();
	}

	private M3Canvas() {
	}

	/**
	 * Draws the first <code>size</code> ints of a command stream on a canvas.
	 *
	 * @param canvas    the native canvas
	 * @param ops       the stream: a header int <code>op | (length &lt;&lt; 8)</code>, then <code>length</code> int arguments
	 * @param size      the number of ints to replay
	 * @param blobs     the text blob of each DRAW_TEXT and GLYPH_RUN command, in stream order (0 draws nothing)
	 * @param blobCount the number of entries of <code>blobs</code> in use
	 * @param textMask  the <code>PaintKeys</code> mask of text paints
	 * @return the number of commands drawn
	 */
	public static native int _nReplay(long canvas, int[] ops, int size, long[] blobs, int blobCount, int textMask);
}
