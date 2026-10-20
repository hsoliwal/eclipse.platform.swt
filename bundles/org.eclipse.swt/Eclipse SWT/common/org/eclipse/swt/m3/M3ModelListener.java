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
package org.eclipse.swt.m3;

/**
 * Listener of a lane model (public model API, brief rule 26). Runs on the thread that applies the model.
 *
 * @since 3.138
 */
public interface M3ModelListener {

	/**
	 * A patch was applied. The model is final: {@link M3ListModel#slotCapacity()} covers every inserted slot and
	 * {@link M3ListModel#slot(long)} answers for the inserted ids. The patch is immutable and may be kept.
	 *
	 * @param model the model
	 * @param patch the patch, stamped with its generation
	 */
	void patched (M3ListModel model, M3Patch patch);

	/**
	 * The number of visible viewports of the model changed (see {@link M3ListModel#viewportVisibility(Object, boolean)}).
	 *
	 * @param model the model
	 * @param visible the visible viewports
	 */
	default void visibleViewportsChanged (M3ListModel model, int visible) {
	}
}
