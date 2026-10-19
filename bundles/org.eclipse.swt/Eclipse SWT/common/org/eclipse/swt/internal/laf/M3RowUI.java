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
package org.eclipse.swt.internal.laf;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * The row delegate of the drawn Tree, Table and List (docs/m3/LOOK_AND_FEEL.md, LF6), the part of Swing's
 * <code>TreeUI</code>/<code>TableUI</code>/<code>ListUI</code> that paints a row behind its cells: background, stripe,
 * selection, grid line. A drawn widget installs it with {@link M3LookAndFeel#installUI} (type <code>M3RowUI</code>), so
 * a look and feel's factory can replace it; {@link M3DefaultRowUI} reads the defaults of the widget class.
 */
public interface M3RowUI extends M3ComponentUI {
	/** The row is selected. */
	int SELECTED = 1;
	/** The widget has the focus (a selected row shows the active selection colours). */
	int FOCUSED = 1 << 1;
	/** The row is an odd row (striped where the look and feel stripes). */
	int ODD = 1 << 2;
	/** The pointer is over the row. */
	int HOT = 1 << 3;

	/** The row height in pixels at a zoom (100 for 100 %). */
	int rowHeight (int zoom);

	/**
	 * Paints the background of one row.
	 *
	 * @param state a combination of {@link #SELECTED}, {@link #FOCUSED}, {@link #ODD}, {@link #HOT}
	 * @return the foreground colour (ARGB) of the row's text
	 */
	int paintRow (Control control, GC gc, int x, int y, int width, int height, int state);
}
