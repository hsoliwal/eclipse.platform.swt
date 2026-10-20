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

import java.util.*;
import java.util.function.*;

/**
 * Classes which implement this interface provide methods that deal with a control becoming part-visible or
 * part-hidden: the control and all its ancestors visible with a non-zero size, its shell visible and not minimized,
 * and no container marking it hidden (see <code>Control.isPartVisible ()</code>). SWT sends the events itself, once
 * per transition, whatever caused it: <code>setVisible</code> of the control or an ancestor (tab and stack-page
 * selection, expand and collapse), a size crossing zero, the shell minimized or restored, <code>setParent</code>,
 * or <code>Control.setPartHidden</code>.
 * <p>
 * After creating an instance of a class that implements this interface it can be added to a control using the
 * <code>addPartVisibilityListener</code> method and removed using the <code>removePartVisibilityListener</code>
 * method (docs/m3/visibility/PART_VISIBLE.md).
 * </p>
 *
 * @see PartVisibilityAdapter
 * @see PartVisibilityEvent
 * @since 3.136
 */
public interface PartVisibilityListener extends EventListener {

	/**
	 * Sent when the control became part-visible.
	 *
	 * @param e an event with the cause and the origin of the transition
	 */
	void partVisible (PartVisibilityEvent e);

	/**
	 * Sent when the control stopped being part-visible.
	 *
	 * @param e an event with the cause and the origin of the transition
	 */
	void partHidden (PartVisibilityEvent e);

	/**
	 * Static helper method to create a <code>PartVisibilityListener</code> for the {@link #partVisible} method, given a
	 * lambda expression or a method reference.
	 *
	 * @param c the consumer of the event
	 * @return PartVisibilityListener
	 */
	static PartVisibilityListener partVisibleAdapter (Consumer<PartVisibilityEvent> c) {
		return new PartVisibilityAdapter () {
			@Override
			public void partVisible (PartVisibilityEvent e) {
				c.accept (e);
			}
		};
	}

	/**
	 * Static helper method to create a <code>PartVisibilityListener</code> for the {@link #partHidden} method, given a
	 * lambda expression or a method reference.
	 *
	 * @param c the consumer of the event
	 * @return PartVisibilityListener
	 */
	static PartVisibilityListener partHiddenAdapter (Consumer<PartVisibilityEvent> c) {
		return new PartVisibilityAdapter () {
			@Override
			public void partHidden (PartVisibilityEvent e) {
				c.accept (e);
			}
		};
	}
}
