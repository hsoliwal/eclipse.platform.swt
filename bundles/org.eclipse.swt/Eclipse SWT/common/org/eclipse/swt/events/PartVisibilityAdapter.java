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
package org.eclipse.swt.events;

/**
 * This adapter class provides default implementations for the methods described by the
 * <code>PartVisibilityListener</code> interface.
 * <p>
 * Classes that wish to deal with <code>PartVisibilityEvent</code>s can extend this class and override only the
 * methods which they are interested in.
 * </p>
 *
 * @see PartVisibilityListener
 * @see PartVisibilityEvent
 * @since 3.136
 */
public abstract class PartVisibilityAdapter implements PartVisibilityListener {

	/**
	 * Sent when the control became part-visible. The default behavior is to do nothing.
	 *
	 * @param e an event with the cause and the origin of the transition
	 */
	@Override
	public void partVisible (PartVisibilityEvent e) {
	}

	/**
	 * Sent when the control stopped being part-visible. The default behavior is to do nothing.
	 *
	 * @param e an event with the cause and the origin of the transition
	 */
	@Override
	public void partHidden (PartVisibilityEvent e) {
	}
}
