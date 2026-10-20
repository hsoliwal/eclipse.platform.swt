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
	/* the current window (inclusive; -1 when empty) */
	private int first = -1, last = -1;
	/*
	 * the rows of the last event's window that are still in the model, as pairs in the current index space (shifted()
	 * moves them; inserted rows split them), and the log of this batch's shifts to map them back to the last event's
	 * index space
	 */
	private int [] sent = NONE;
	private int [] shiftAt = new int [4], shiftCount = new int [4];
	private int shifts;
	private int cause;
	private boolean extents, pending, posted;
	private int [] enteredExtra = NONE, leftExtra = NONE, leftRemoved = NONE;
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

	/**
	 * Reports rows inserted (<code>count</code> above 0) or removed (below 0) at an index of the current index space,
	 * before the widget reports its new window. Rows that only shift are neither entered nor left; removed rows that
	 * were on screen are reported as left at the indexes of the last event (docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md).
	 *
	 * @param control the viewport widget
	 * @param at the index of the first inserted or removed row
	 * @param count the number of rows inserted (positive) or removed (negative)
	 */
	public static void shifted (Scrollable control, int at, int count) {
		if (count == 0 || control.isDisposed () || !control.isListening (SWT.Viewport)) return;
		ViewportReporter reporter = (ViewportReporter) control.getData (KEY);
		if (reporter == null) control.setData (KEY, reporter = new ViewportReporter (control));
		reporter.shift (at, count);
	}

	private void shift (int at, int count) {
		if (count > 0) {
			sent = insertGap (sent, at, count);
		} else {
			int [] gone = intersect (sent, at, at - count - 1);
			if (gone.length > 0) leftRemoved = union (leftRemoved, mapBack (gone));
			sent = removeAndClose (sent, at, -count);
		}
		if (shifts == shiftAt.length) {
			shiftAt = java.util.Arrays.copyOf (shiftAt, shifts * 2);
			shiftCount = java.util.Arrays.copyOf (shiftCount, shifts * 2);
		}
		shiftAt [shifts] = at;
		shiftCount [shifts++] = count;
		cause |= ViewportEvent.MODEL_DELTA;
		extents = true;
		pending = true;
		post ();
	}

	/* pairs of the current index space back to the last event's, through this batch's shifts in reverse */
	private int [] mapBack (int [] pairs) {
		int [] out = pairs.clone ();
		for (int s = shifts - 1; s >= 0; s--) {
			int at = shiftAt [s], count = shiftCount [s];
			for (int i = 0; i < out.length; i++) {
				if (count > 0) {
					if (out [i] >= at + count) out [i] -= count;
					else if (out [i] >= at) out [i] = at;
				} else if (out [i] >= at) {
					out [i] -= count;
				}
			}
		}
		return out;
	}

	/* the pairs with a gap of count new rows opened at index at */
	static int [] insertGap (int [] pairs, int at, int count) {
		int [] out = new int [pairs.length + 2];
		int n = 0;
		for (int i = 0; i < pairs.length; i += 2) {
			int s = pairs [i], e = pairs [i + 1];
			if (e < at) {
				out [n++] = s;
				out [n++] = e;
			} else if (s >= at) {
				out [n++] = s + count;
				out [n++] = e + count;
			} else {
				out [n++] = s;
				out [n++] = at - 1;
				out [n++] = at + count;
				out [n++] = e + count;
			}
		}
		return java.util.Arrays.copyOf (out, n);
	}

	/* the pairs without the rows [at, at + count - 1], the rows after them moved up by count */
	static int [] removeAndClose (int [] pairs, int at, int count) {
		int [] out = new int [pairs.length];
		int n = 0, end = at + count - 1;
		for (int i = 0; i < pairs.length; i += 2) {
			int s = pairs [i], e = pairs [i + 1];
			/* the part before the removed rows stays; the part after moves up (a pair across them closes into one) */
			if (s < at) n = add (out, n, s, Math.min (e, at - 1));
			if (e > end) n = add (out, n, Math.max (s, end + 1) - count, e - count);
		}
		return java.util.Arrays.copyOf (out, n);
	}

	/* appends a pair, coalescing it with the previous one when they touch */
	private static int add (int [] out, int n, int s, int e) {
		if (n > 0 && s <= out [n - 1] + 1) {
			out [n - 1] = Math.max (out [n - 1], e);
			return n;
		}
		if (n + 2 > out.length) return n; // cannot happen: a removal never adds pairs beyond one split per pair
		out [n++] = s;
		out [n++] = e;
		return n;
	}

	/* the parts of the pairs inside [s, e] */
	static int [] intersect (int [] pairs, int s, int e) {
		int [] out = new int [pairs.length];
		int n = 0;
		for (int i = 0; i < pairs.length; i += 2) {
			int a = Math.max (pairs [i], s), b = Math.min (pairs [i + 1], e);
			if (a <= b) {
				out [n++] = a;
				out [n++] = b;
			}
		}
		return java.util.Arrays.copyOf (out, n);
	}

	/* the pairs a minus the pairs b */
	static int [] minus (int [] a, int [] b) {
		int [] out = a;
		for (int i = 0; i < b.length; i += 2) {
			int [] next = new int [out.length + 2];
			int n = 0;
			for (int j = 0; j < out.length; j += 2) {
				int [] rest = difference (out [j], out [j + 1], b [i], b [i + 1]);
				for (int value : rest) next [n++] = value;
			}
			out = java.util.Arrays.copyOf (next, n);
		}
		return out;
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
		int [] window = first < 0 ? NONE : new int [] {first, last};
		int [] entered = union (minus (window, sent), enteredExtra);
		int [] left = union (union (mapBack (minus (sent, window)), leftRemoved), leftExtra);
		Event event = new Event ();
		event.start = first;
		event.end = last;
		event.detail = cause;
		event.count = extents ? 1 : 0;
		event.data = new int [][] {entered, left};
		sent = window;
		reset ();
		control.notifyListeners (SWT.Viewport, event);
	}

	private void reset () {
		cause = 0;
		extents = false;
		pending = false;
		shifts = 0;
		enteredExtra = leftExtra = leftRemoved = NONE;
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
