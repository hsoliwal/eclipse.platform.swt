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

/**
 * Told once per frame tick of a display's {@link Synchronizer}, on the user-interface thread, after the model patches
 * of the frame (the runnables posted with {@link Synchronizer#MODEL} priority) were applied (brief rule 28,
 * docs/m3/SYNCHRONIZER.md). Drawn planes publish their snapshots for the render thread here.
 *
 * @see Synchronizer#addFrameListener(FrameListener)
 * @since 3.136
 */
@FunctionalInterface
public interface FrameListener {

	/**
	 * Sent after the patches of a frame were applied.
	 *
	 * @param display the display of the synchronizer
	 * @param frameGeneration the frame's number, per display, counting from 1
	 */
	void frameApplied (Display display, long frameGeneration);
}
