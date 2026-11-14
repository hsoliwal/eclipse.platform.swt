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

import org.eclipse.swt.*;

/**
 * <code>SWT.EmptinessChanged</code> of a <code>Table</code> and a <code>List</code>.
 * <p>
 * A listener of the event receives it each time the item count of the widget crosses zero:
 * <code>detail</code> is {@link #BECAME_EMPTY} when the last item went away and
 * {@link #BECAME_NON_EMPTY} when the first item arrived (the values of the <code>Tree</code> event),
 * and <code>count</code> is the item count after the change. Every structural path counts: creating
 * and disposing items, <code>remove</code>, <code>removeAll</code>, <code>setItemCount</code> and,
 * for a List, <code>add</code> and <code>setItems</code>. A change that keeps the widget on the same
 * side of zero sends nothing, and one operation sends at most one event (<code>List.setItem</code> on
 * a one-item list reports no transient empty state).
 * </p>
 * <p>
 * The widgets call {@link #watch(Control)} when a listener of the event is added, and the seams below
 * after their structural changes. A <code>Tree</code> keeps its own rules for the event (first item
 * created, last item disposed) and does not use this class. The win32 widgets send the event.
 * </p>
 */
final class Emptiness {

	/** <code>event.detail</code> when the first item arrived. */
	static final int BECAME_NON_EMPTY = 0;
	/** <code>event.detail</code> when the last item went away. */
	static final int BECAME_EMPTY = 1;

	private static final String KEY = "org.eclipse.swt.widgets.Emptiness"; //$NON-NLS-1$

	/* The latch of a listened widget: the emptiness last announced, the open holds, and the dispatch state. */
	private static final class Latch {
		boolean empty;
		int holds;
		boolean sending;
		boolean again;
	}

	private Emptiness () {
	}

	/* ------------------------------------------------------------------ widget seam */

	/*
	 * A listener of SWT.EmptinessChanged is being added (called before it is hooked): a widget that had no
	 * listener reads its state again.
	 */
	static void watch (Control widget) {
		if (!widget.hooks (SWT.EmptinessChanged) || latch (widget) == null) {
			Latch latch = new Latch ();
			latch.empty = count (widget) == 0;
			widget.setData (KEY, latch);
		}
	}

	/* End of a structural change: announces a zero crossing of the item count (getItemCount). */
	static void changed (Control widget) {
		if (!widget.hooks (SWT.EmptinessChanged) || widget.isDisposed ()) return;
		Latch latch = latch (widget);
		if (latch == null || latch.holds > 0) return;
		announce (widget, latch, count (widget) == 0);
	}

	/* Start of a compound change whose intermediate states are not announced. */
	static void hold (Control widget) {
		Latch latch = widget.hooks (SWT.EmptinessChanged) && !widget.isDisposed () && widget.isValidThread () ? latch (widget) : null;
		if (latch != null) latch.holds++;
	}

	/* End of a compound change (in a finally block): announces the net crossing, if any. */
	static void release (Control widget) {
		if (!widget.hooks (SWT.EmptinessChanged) || widget.isDisposed () || !widget.isValidThread ()) return;
		Latch latch = latch (widget);
		if (latch != null && latch.holds > 0) latch.holds--;
		changed (widget);
	}

	/* ------------------------------------------------------------------ helpers */

	/*
	 * Sends the crossing. A listener that changes the widget again is not answered from inside the
	 * dispatch (later listeners would see the states out of order): the change is noted, and once
	 * every listener has the event, the net state is announced if it differs.
	 */
	private static void announce (Control widget, Latch latch, boolean empty) {
		if (latch.sending) {
			latch.again = true;
			return;
		}
		latch.sending = true;
		try {
			while (empty != latch.empty) {
				latch.empty = empty;
				latch.again = false;
				Event event = new Event ();
				event.detail = empty ? BECAME_EMPTY : BECAME_NON_EMPTY;
				event.count = empty ? 0 : count (widget);
				widget.sendEvent (SWT.EmptinessChanged, event);
				/* a listener may have removed the last listener, or removed it and added a new one (a new latch) */
				if (!latch.again || widget.isDisposed () || !widget.hooks (SWT.EmptinessChanged) || latch (widget) != latch) break;
				empty = count (widget) == 0;
			}
		} finally {
			latch.sending = false;
		}
	}

	private static Latch latch (Widget widget) {
		return widget.getData (KEY) instanceof Latch latch ? latch : null;
	}

	private static int count (Control widget) {
		if (widget instanceof Table table) return table.getItemCount ();
		return ((List) widget).getItemCount ();
	}
}
