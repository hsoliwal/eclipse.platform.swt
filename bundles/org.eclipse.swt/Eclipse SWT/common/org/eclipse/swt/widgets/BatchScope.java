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

import java.util.concurrent.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * Platform-neutral part of <code>Control.beginBatch</code>,
 * <code>endBatch</code> and <code>runBatch</code>, and the bulk update of
 * the item widgets ({@link #bulk(Control, Runnable)}).
 * <p>
 * <code>run</code> keeps the event loop running while the background work
 * executes, so the frozen planes record their damage, the planes that are not
 * frozen (the scroll bars, a viewport during a main-plane batch) stay live, and
 * after {@link #BUSY_THRESHOLD} milliseconds every shell shows the busy cursor,
 * the way <code>BusyIndicator.showWhile(Future)</code> does.
 * </p>
 */
final class BatchScope {
	static final int PLANES = SWT.PLANE_MAIN | SWT.PLANE_VIEWPORT | SWT.PLANE_SCROLLBARS;

	/** Milliseconds of background work before the busy cursor shows (-Dswt.batch.busyMs). */
	static final int BUSY_THRESHOLD = Integer.getInteger ("swt.batch.busyMs", 100).intValue (); //$NON-NLS-1$

	private BatchScope () {
	}

	/* the planes a structural edit changes; the main plane (layout, chrome) stays live */
	private static final int BULK_PLANES = SWT.PLANE_VIEWPORT | SWT.PLANE_SCROLLBARS;

	/**
	 * Runs a group of edits of a <code>Tree</code>, <code>Table</code> or <code>List</code> (adds, removes,
	 * <code>setItemCount</code>, text and selection changes) as one update: the viewport and scroll bar planes of
	 * the widget are frozen while the edits run (see <code>Control.beginBatch</code>), so nothing is painted in
	 * between even if the edits dispatch events, and at the end the widget repaints only what changed, once. An
	 * {@link M3Emptiness} listener hears at most one crossing, with the final count. The resulting state is the one
	 * the same edits produce without the call.
	 * <p>
	 * Calls nest. An exception thrown by the edits leaves nothing frozen and propagates unchanged. With the
	 * viewports the native work is already planned (a Table append burst becomes one native count change; List and
	 * Tree touch only their window); in the original mode the stock per-item native calls remain and only their
	 * painting is coalesced.
	 * </p>
	 *
	 * @param widget the tree, table or list
	 * @param edits the edits
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the widget or the edits are null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	static void bulk (Control widget, Runnable edits) {
		if (widget == null || edits == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.beginBatch (BULK_PLANES);
		M3Emptiness.hold (widget);
		try {
			edits.run ();
		} finally {
			/* the edits may have disposed the widget: then there is nothing left to resume */
			if (!widget.isDisposed ()) {
				try {
					M3Emptiness.release (widget);
				} finally {
					widget.endBatch (BULK_PLANES);
				}
			}
		}
	}

	/**
	 * Fallback for platforms without per-plane painting gates (GTK, Cocoa; NOT_RUN on
	 * this host): the main plane defers layout, the viewport plane uses
	 * <code>setRedraw</code>, the scroll bar plane is left live.
	 */
	static void beginFallback (Control control, int planes) {
		if ((planes & SWT.PLANE_MAIN) != 0 && control instanceof Composite composite) {
			composite.setLayoutDeferred (true);
		}
		if ((planes & SWT.PLANE_VIEWPORT) != 0) {
			control.setRedraw (false);
		}
	}

	static void endFallback (Control control, int planes) {
		if ((planes & SWT.PLANE_VIEWPORT) != 0) {
			control.setRedraw (true);
		}
		if ((planes & SWT.PLANE_MAIN) != 0 && control instanceof Composite composite) {
			composite.setLayoutDeferred (false);
		}
	}

	static void run (Control control, int planes, Runnable background, Runnable apply) {
		Display display = control.getDisplay ();
		control.beginBatch (planes);
		try {
			CompletableFuture<Void> work = CompletableFuture.runAsync (background);
			waitFor (display, work);
			try {
				work.join ();
			} catch (CompletionException | CancellationException e) {
				SWT.error (SWT.ERROR_FAILED_EXEC, e.getCause () != null ? e.getCause () : e);
			}
			if (apply != null && !control.isDisposed ()) {
				apply.run ();
			}
		} finally {
			if (!control.isDisposed ()) {
				control.endBatch (planes);
			}
		}
	}

	static void waitFor (Display display, CompletableFuture<Void> work) {
		if (work.isDone ()) {
			return;
		}
		Runnable wake = () -> {
			/* empty: posting it wakes Display.sleep() */
		};
		work.handle ((nil1, nil2) -> {
			if (!display.isDisposed ()) {
				try {
					display.asyncExec (wake);
				} catch (SWTException e) {
					/* display disposed between the check and the post */
				}
			}
			return null;
		});
		boolean [] busy = new boolean [1];
		display.timerExec (BUSY_THRESHOLD, () -> busy [0] = !work.isDone ());
		Shell [] shells = null;
		Cursor [] saved = null;
		try {
			while (!work.isDone () && !display.isDisposed ()) {
				if (busy [0] && shells == null) {
					shells = display.getShells ();
					saved = new Cursor [shells.length];
					Cursor wait = display.getSystemCursor (SWT.CURSOR_WAIT);
					for (int i = 0; i < shells.length; i++) {
						saved [i] = shells [i].getCursor ();
						shells [i].setCursor (wait);
					}
				}
				if (!display.readAndDispatch ()) {
					display.sleep ();
				}
			}
		} finally {
			if (shells != null) {
				for (int i = 0; i < shells.length; i++) {
					if (!shells [i].isDisposed ()) {
						shells [i].setCursor (saved [i]);
					}
				}
			}
		}
	}
}
