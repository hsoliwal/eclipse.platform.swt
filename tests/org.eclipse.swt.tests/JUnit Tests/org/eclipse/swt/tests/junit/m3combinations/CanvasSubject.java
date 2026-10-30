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
package org.eclipse.swt.tests.junit.m3combinations;

import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

/**
 * A Canvas (created with <code>SWT.M3</code>, so the m3 and gpu modes draw it with the M3 renderer) that paints the
 * rows of the order from a top row; with a vertical scroll bar the top row is the bar's selection.
 */
final class CanvasSubject extends Subject {
	static final int ROW = 18;
	Canvas canvas;
	int top;

	@Override
	void create (Shell shell, int style) {
		canvas = new Canvas (shell, style | SWT.M3 | SWT.DOUBLE_BUFFERED);
		canvas.addListener (SWT.Paint, e -> {
			for (int row = top, y = 0; row < order.length && y < e.height + e.y; row++, y += ROW) e.gc.drawString (rowText (row), 4, y, true);
		});
		ScrollBar bar = canvas.getVerticalBar ();
		if (bar != null) bar.addListener (SWT.Selection, e -> scrollTo (bar.getSelection ()));
	}

	@Override
	Widget widget () {
		return canvas;
	}

	@Override
	void show (int [] order) {
		top = Math.min (top, Math.max (0, order.length - 1));
		ScrollBar bar = canvas.getVerticalBar ();
		if (bar != null) bar.setValues (top, 0, Math.max (1, order.length), 10, 1, 10);
		repaint ();
	}

	void repaint () {
		canvas.redraw ();
		canvas.update ();
	}

	@Override
	void scrollTo (int row) {
		ScrollBar bar = canvas.getVerticalBar ();
		if (bar != null) bar.setSelection (row);
		top = bar != null ? bar.getSelection () : row;
		repaint ();
	}

	@Override
	void select (int start, int end) {
		throw new UnsupportedOperationException ("Canvas has no selection");
	}

	@Override
	void update (int row, String text) {
		order [row] = -1 - order [row];
		repaint ();
	}

	@Override
	String rowText (int row) {
		return order [row] < 0 ? "updated" : super.rowText (row);
	}

	@Override
	String state () {
		if (canvas.isDisposed ()) return "disposed";
		ScrollBar bar = canvas.getVerticalBar ();
		String scroll = bar == null ? "no-bar" : "bar=" + bar.getSelection () + "/" + bar.getMaximum () + "/" + bar.getThumb ();
		return "count=" + order.length + " top=" + top + " " + scroll + " text=" + (order.length == 0 ? "-" : rowText (top));
	}

	@Override
	List<Widget> items () {
		return new ArrayList<> ();
	}
}
