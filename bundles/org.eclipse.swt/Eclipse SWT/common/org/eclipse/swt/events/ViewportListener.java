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

/**
 * Classes which implement this interface provide a method that deals with the window of visible rows of a viewport
 * widget (Tree, Table, List, Grid, the drawn planes): which part of its flattened visible order is on screen, sent by
 * the widget once per coalesced change and never while the widget is not part-visible
 * (docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md).
 * <p>
 * After creating an instance of a class that implements this interface it can be added to a scrollable using the
 * <code>addViewportListener</code> method and removed using the <code>removeViewportListener</code> method.
 * </p>
 *
 * @see ViewportEvent
 * @since 3.136
 */
@FunctionalInterface
public interface ViewportListener extends EventListener {

	/**
	 * Sent when the window of visible rows, the scroll extents or the part visibility of the viewport changed.
	 *
	 * @param e an event with the window, the rows that entered and left it, and the causes
	 */
	void viewportChanged (ViewportEvent e);
}
