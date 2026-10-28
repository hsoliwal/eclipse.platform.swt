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

/**
 * A font as a snapshot carries it to the render thread (brief rule 28): the face, the height as the platform states it
 * (on win32 the <code>LOGFONT</code> height: negative for the character height, positive for the cell height, in
 * pixels), the weight, the slant, and the platform's character set, quality and pitch-and-family. The render thread
 * makes its own font from it and never uses the UI thread's font handle, which the UI thread may free.
 *
 * @param face the family name
 * @param height the platform height in pixels
 * @param weight 100 to 900 (400 regular, 700 bold)
 * @param italic the slant
 * @param charset the platform character set (win32 <code>lfCharSet</code>)
 * @param quality the platform output quality (win32 <code>lfQuality</code>)
 * @param pitchAndFamily the platform pitch and family (win32 <code>lfPitchAndFamily</code>)
 */
public record FontDescriptor (String face, int height, int weight, boolean italic, int charset, int quality, int pitchAndFamily) {
	/** A font with the platform's default character set (1), quality (0) and pitch (0). */
	public FontDescriptor (String face, int height, int weight, boolean italic) {
		this (face, height, weight, italic, 1, 0, 0);
	}
}
