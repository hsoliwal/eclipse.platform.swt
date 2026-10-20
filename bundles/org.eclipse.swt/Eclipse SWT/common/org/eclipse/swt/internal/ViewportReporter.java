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
package org.eclipse.swt.internal;

import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;

/**
 * The firing side of <code>SWT.Viewport</code> for widget implementations (docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md):
 * a viewport calls {@link #report} from every place that moves its window of visible rows (scroll, resize, layout,
 * model delta, expand); the reporter coalesces the changes of one event-loop turn into one {@link ViewportEvent}
 * (one posted flush per turn), holds them while the widget is not part-visible, and adds {@link ViewportEvent#VISIBILITY}
 * to the first event after a <code>PartVisible</code>. A widget without a viewport listener pays one
 * <code>isListening</code> test per report and has no reporter.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API. It is marked public only so that it can be shared
 * within the packages provided by SWT (and the M3 custom widgets).
 * </p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ViewportReporter {
	private static final String KEY = ViewportReporter.class.getName ();
	private static final int [] NONE = {};

	private final Scrollable control;
	/* the window of the last event, and the current one (inclusive; -1 when empty) */
	private int firstSent = -1, lastSent = -1, first = -1, last = -1;
	private int cause;
	private boolean extents, pending, posted;
	private int [] enteredExtra = NONE, leftExtra = NONE;
	private final Listener partVisible = event -> becameVisible ();

	private ViewportReporter (Scrollable control) {
		this.control = control;
		control.addListener (SWT.PartVisible, partVisible);
	}

	/**
	 * Reports the current window of a viewport after a change. Returns at once when nobody listens for
	 * <code>SWT.Viewport</code>.
	 *
	 * @param control the viewport widget
	 * @param first the first index on screen in the flattened visible order, -1 when nothing is on screen
	 * @param last the last index on screen (inclusive), -1 when nothing is on screen
	 * @param cause the {@link ViewportEvent} cause bits of the change
	 * @param extentsChanged whether the total rows or the total size changed
	 */
	public static void report (Scrollable control, int first, int last, int cause, boolean extentsChanged) {
		report (control, first, last, cause, extentsChanged, null, null);
	}

	/**
	 * {@link #report(Scrollable, int, int, int, boolean)} with explicit ranges for a model delta: rows inserted into
	 * or replaced inside the window (<code>entered</code>) and removed from it (<code>left</code>), as ascending
	 * inclusive pairs, added to the difference of the windows.
	 */
	public static void report (Scrollable control, int first, int last, int cause, boolean extentsChanged, int [] entered, int [] left) {
		if (control.isDisposed () || !control.isListening (SWT.Viewport)) return;
		ViewportReporter reporter = (ViewportReporter) control.getData (KEY);
		if (reporter == null) control.setData (KEY, reporter = new ViewportReporter (control));
		reporter.changed (first, last, cause, extentsChanged, entered, left);
	}

	private void changed (int first, int last, int cause, boolean extentsChanged, int [] entered, int [] left) {
		this.first = first < 0 || last < first ? -1 : first;
		this.last = this.first < 0 ? -1 : last;
		this.cause |= cause;
		this.extents |= extentsChanged;
		if (entered != null && entered.length > 0) enteredExtra = union (enteredExtra, entered);
		if (left != null && left.length > 0) leftExtra = union (leftExtra, left);
		pending = true;
		post ();
	}

	private void post () {
		if (posted || !control.isPartVisible ()) return;
		posted = true;
		control.getDisplay ().asyncExec (this::flush);
	}

	private void becameVisible () {
		cause |= ViewportEvent.VISIBILITY;
		pending = true;
		post ();
	}

	/* one event with everything coalesced since the last one, when the widget is part-visible */
	private void flush () {
		posted = false;
		if (!pending || control.isDisposed ()) return;
		if (!control.isListening (SWT.Viewport)) {
			reset ();
			return;
		}
		if (!control.isPartVisible ()) return;
		int [] entered = union (difference (first, last, firstSent, lastSent), enteredExtra);
		int [] left = union (difference (firstSent, lastSent, first, last), leftExtra);
		Event event = new Event ();
		event.start = first;
		event.end = last;
		event.detail = cause;
		event.count = extents ? 1 : 0;
		event.data = new int [][] {entered, left};
		firstSent = first;
		lastSent = last;
		reset ();
		control.notifyListeners (SWT.Viewport, event);
	}

	private void reset () {
		cause = 0;
		extents = false;
		pending = false;
		enteredExtra = leftExtra = NONE;
	}

	/* the indexes of [a, b] outside [c, d], as ascending inclusive pairs (empty ranges are -1, -1) */
	static int [] difference (int a, int b, int c, int d) {
		if (a < 0) return NONE;
		if (c < 0 || d < a || c > b) return new int [] {a, b};
		if (c <= a && d >= b) return NONE;
		if (c > a && d < b) return new int [] {a, c - 1, d + 1, b};
		return c > a ? new int [] {a, c - 1} : new int [] {d + 1, b};
	}

	/* the union of two ascending inclusive pair lists, coalescing overlapping and adjacent ranges */
	static int [] union (int [] x, int [] y) {
		if (y.length == 0) return x;
		if (x.length == 0) return y.clone ();
		int [] out = new int [x.length + y.length];
		int n = 0, i = 0, j = 0;
		while (i < x.length || j < y.length) {
			int start, end;
			if (j >= y.length || i < x.length && x [i] <= y [j]) {
				start = x [i];
				end = x [i + 1];
				i += 2;
			} else {
				start = y [j];
				end = y [j + 1];
				j += 2;
			}
			if (n > 0 && start <= out [n - 1] + 1) {
				out [n - 1] = Math.max (out [n - 1], end);
			} else {
				out [n++] = start;
				out [n++] = end;
			}
		}
		return n == out.length ? out : java.util.Arrays.copyOf (out, n);
	}
}
