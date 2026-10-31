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
 * The scroll bar model of a <code>Scrollable</code> as one int lane (enhanced M3 API).
 * <p>
 * The lane holds, at the indices below, what the widget's <code>ScrollBar</code> answers: minimum,
 * maximum, thumb, increment, page increment, selection, and visibility and enablement as 0 or 1.
 * With the viewport these are the logical values the viewport owns (a table of millions of rows has
 * a logical maximum, not a native one); in every mode the lane equals the scroll bar's getters, read
 * in one call. {@link #get(Scrollable, int, int[])} fills a lane the caller keeps, so polling the
 * model allocates nothing.
 * </p>
 *
 * @since 3.138
 */
public final class M3ScrollBarModel {
	/** Lane index: <code>ScrollBar.getMinimum</code>. */
	public static final int MINIMUM = 0;
	/** Lane index: <code>ScrollBar.getMaximum</code>. */
	public static final int MAXIMUM = 1;
	/** Lane index: <code>ScrollBar.getThumb</code>. */
	public static final int THUMB = 2;
	/** Lane index: <code>ScrollBar.getIncrement</code>. */
	public static final int INCREMENT = 3;
	/** Lane index: <code>ScrollBar.getPageIncrement</code>. */
	public static final int PAGE_INCREMENT = 4;
	/** Lane index: <code>ScrollBar.getSelection</code>. */
	public static final int SELECTION = 5;
	/** Lane index: 1 when <code>ScrollBar.getVisible</code>, else 0. */
	public static final int VISIBLE = 6;
	/** Lane index: 1 when <code>ScrollBar.getEnabled</code>, else 0. */
	public static final int ENABLED = 7;
	/** The length of a lane. */
	public static final int SIZE = 8;

	private M3ScrollBarModel () {
	}

	/**
	 * Returns the model of the widget's scroll bar in an orientation.
	 *
	 * @param widget the widget
	 * @param orientation <code>SWT.VERTICAL</code> or <code>SWT.HORIZONTAL</code>
	 * @return a new lane of {@link #SIZE} ints, or <code>null</code> when the widget has no such bar
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the widget is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the orientation is neither VERTICAL nor HORIZONTAL</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public static int [] get (Scrollable widget, int orientation) {
		int [] lane = new int [SIZE];
		return get (widget, orientation, lane) ? lane : null;
	}

	/**
	 * Fills a lane with the model of the widget's scroll bar in an orientation.
	 *
	 * @param widget the widget
	 * @param orientation <code>SWT.VERTICAL</code> or <code>SWT.HORIZONTAL</code>
	 * @param lane the lane, at least {@link #SIZE} long; left unchanged when the widget has no such bar
	 * @return whether the widget has the bar
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the widget or the lane is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the orientation is neither VERTICAL nor HORIZONTAL, or the lane is shorter than SIZE</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public static boolean get (Scrollable widget, int orientation, int [] lane) {
		ScrollBar bar = bar (widget, orientation);
		if (lane == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (lane.length < SIZE) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (bar == null) return false;
		lane [MINIMUM] = bar.getMinimum ();
		lane [MAXIMUM] = bar.getMaximum ();
		lane [THUMB] = bar.getThumb ();
		lane [INCREMENT] = bar.getIncrement ();
		lane [PAGE_INCREMENT] = bar.getPageIncrement ();
		lane [SELECTION] = bar.getSelection ();
		lane [VISIBLE] = bar.getVisible () ? 1 : 0;
		lane [ENABLED] = bar.getEnabled () ? 1 : 0;
		return true;
	}

	private static ScrollBar bar (Scrollable widget, int orientation) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		if (orientation != SWT.VERTICAL && orientation != SWT.HORIZONTAL) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return orientation == SWT.VERTICAL ? widget.getVerticalBar () : widget.getHorizontalBar ();
	}
}
