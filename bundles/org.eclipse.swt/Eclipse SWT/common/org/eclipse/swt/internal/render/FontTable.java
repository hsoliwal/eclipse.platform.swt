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
 * The fonts a frame names by handle, as descriptors (brief rule 28): a canvas on the render thread resolves a row's font
 * handle through the frame's table ({@link FrameSlab}) and makes its own font, never asking the platform about the UI
 * thread's handle.
 */
public interface FontTable {
	/** The descriptor of a font handle the frame names, or null when the frame does not name it. */
	FontDescriptor font (long handle);
}
