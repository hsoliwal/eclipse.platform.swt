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
 * Instances of this class are sent when the window of visible rows of a viewport widget changed
 * (docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md). Indexes are positions in the widget's flattened visible order (a
 * Tree counts its expanded rows); ranges are ascending, coalesced pairs <code>[start0, end0, start1, end1, ...]</code>
 * with inclusive ends.
 *
 * @see ViewportListener
 * @since 3.136
 */
public class ViewportEvent extends TypedEvent {

	/** Cause bit: the widget scrolled. */
	public static final int SCROLL = 1;
	/** Cause bit: the widget was resized. */
	public static final int RESIZE = 2;
	/** Cause bit: a layout changed the row geometry (row heights, fonts, columns). */
	public static final int LAYOUT = 4;
	/** Cause bit: the model changed (rows inserted, removed, replaced or moved). */
	public static final int MODEL_DELTA = 8;
	/** Cause bit: the widget became part-visible (the first event after a <code>PartVisible</code>). */
	public static final int VISIBILITY = 16;
	/** Cause bit: a node was expanded or collapsed. */
	public static final int EXPAND = 32;

	/** The first index on screen, or -1 when nothing is on screen. */
	public int firstVisible;

	/** The last index on screen (inclusive), or -1 when nothing is on screen. */
	public int lastVisible;

	/** The indexes that came on screen since the last event, as ascending inclusive pairs; empty when none. */
	public int [] entered;

	/** The indexes that went off screen since the last event, as ascending inclusive pairs; empty when none. */
	public int [] left;

	/** Whether the scroll extents (the total rows or the total size) changed since the last event. */
	public boolean extentsChanged;

	/** The union of the causes coalesced into this event: {@link #SCROLL}, {@link #RESIZE}, {@link #LAYOUT}, {@link #MODEL_DELTA}, {@link #VISIBILITY}, {@link #EXPAND}. */
	public int cause;

	static final long serialVersionUID = 1L;
	private static final int [] NONE = {};

	/**
	 * Constructs a new instance of this class based on the information in the given untyped event: <code>start</code>
	 * and <code>end</code> hold the window, <code>detail</code> the causes, <code>count</code> 1 when the extents
	 * changed, and <code>data</code> an <code>int [][] {entered, left}</code>.
	 *
	 * @param e the untyped event containing the information
	 */
	public ViewportEvent (Event e) {
		super (e);
		this.firstVisible = e.start;
		this.lastVisible = e.end;
		this.cause = e.detail;
		this.extentsChanged = e.count != 0;
		int [][] ranges = e.data instanceof int [][] pairs && pairs.length == 2 ? pairs : null;
		this.entered = ranges != null && ranges [0] != null ? ranges [0] : NONE;
		this.left = ranges != null && ranges [1] != null ? ranges [1] : NONE;
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
			+ " first=" + firstVisible + " last=" + lastVisible + " entered=" + java.util.Arrays.toString (entered)
			+ " left=" + java.util.Arrays.toString (left) + " extentsChanged=" + extentsChanged + " cause=" + cause + "}";
	}
}
