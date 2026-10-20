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

import org.eclipse.swt.widgets.*;

/**
 * Instances of this class are sent as a result of a control becoming part-visible or part-hidden
 * (docs/m3/visibility/PART_VISIBLE.md). <code>widget</code> is the control whose state changed.
 *
 * @see PartVisibilityListener
 * @since 3.136
 */
public class PartVisibilityEvent extends TypedEvent {

	/** The cause: <code>setVisible</code> of the control or an ancestor (tab and stack selection, expand, collapse). */
	public static final int VISIBILITY = 1;
	/** The cause: the width or height of the control or an ancestor crossed zero. */
	public static final int SIZE = 2;
	/** The cause: the shell was minimized or restored. */
	public static final int MINIMIZE = 3;
	/** The cause: <code>setParent</code> moved the control or an ancestor. */
	public static final int REPARENT = 4;
	/** The cause: a container called <code>setPartHidden</code> on the control or an ancestor. */
	public static final int CONTAINER = 5;

	/** One of {@link #VISIBILITY}, {@link #SIZE}, {@link #MINIMIZE}, {@link #REPARENT}, {@link #CONTAINER}. */
	public int cause;

	/** The control whose change caused the transition: the control itself or one of its ancestors. */
	public Control origin;

	static final long serialVersionUID = 1L;

	/**
	 * Constructs a new instance of this class based on the information in the given untyped event (its
	 * <code>detail</code> holds the cause, its <code>item</code> the origin).
	 *
	 * @param e the untyped event containing the information
	 */
	public PartVisibilityEvent (Event e) {
		super (e);
		this.cause = e.detail;
		this.origin = e.item instanceof Control control ? control : null;
	}

	/**
	 * Returns a string containing a concise, human-readable description of the receiver.
	 *
	 * @return a string representation of the event
	 */
	@Override
	public String toString () {
		String string = super.toString ();
		return string.substring (0, string.length () - 1) // remove trailing '}'
			+ " cause=" + cause + " origin=" + origin + "}";
	}
}
