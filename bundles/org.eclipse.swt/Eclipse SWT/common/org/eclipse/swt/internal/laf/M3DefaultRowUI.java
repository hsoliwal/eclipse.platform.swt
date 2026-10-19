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
 * The default row delegate ({@link M3RowUI}) of one widget class (<code>Tree</code>, <code>Table</code> or
 * <code>List</code>): it reads <code>&lt;Widget&gt;.background</code>, <code>.foreground</code>,
 * <code>.selectionBackground</code>, <code>.selectionForeground</code>, <code>.selectionInactiveBackground</code>,
 * <code>.stripeBackground</code>, <code>.gridColor</code>, <code>.showGrid</code> and <code>.rowHeight</code> (each
 * falling back to its <code>Component</code> key), per-widget overrides first ({@link M3UIOverrides}). The key ids are
 * interned once per delegate; a paint is array reads and the display's shared colours.
 */
public final class M3DefaultRowUI implements M3RowUI {
	private final int background, foreground, selection, selectionText, inactive, inactiveText, stripe, grid, showGrid, rowHeight, hover;
	private final int [] paintKeys, metricKeys;
	private M3UIDefaults defaults;
	private M3UIOverrides overrides;

	/** The row delegate of a widget class, for example <code>"Table"</code>. */
	public M3DefaultRowUI (String widget) {
		background = M3UIKeys.of (widget, "background", M3UIKeys.COLOR);
		foreground = M3UIKeys.of (widget, "foreground", M3UIKeys.COLOR);
		selection = M3UIKeys.of (widget, "selectionBackground", M3UIKeys.COLOR);
		selectionText = M3UIKeys.of (widget, "selectionForeground", M3UIKeys.COLOR);
		inactive = M3UIKeys.of (widget, "selectionInactiveBackground", M3UIKeys.COLOR);
		inactiveText = M3UIKeys.of (widget, "selectionInactiveForeground", M3UIKeys.COLOR);
		stripe = M3UIKeys.of (widget, "stripeBackground", M3UIKeys.COLOR);
		grid = M3UIKeys.of (widget, "gridColor", M3UIKeys.COLOR);
		showGrid = M3UIKeys.of (widget, "showGrid", M3UIKeys.BOOLEAN);
		hover = M3UIKeys.of (widget, "hoverBackground", M3UIKeys.COLOR);
		rowHeight = M3UIKeys.of (widget, "rowHeight", M3UIKeys.INTEGER);
		paintKeys = new int [] {background, foreground, selection, selectionText, inactive, inactiveText, stripe, grid, showGrid, hover};
		metricKeys = new int [] {rowHeight};
	}

	@Override
	public void installUI (Control control, M3UIDefaults defaults) {
		this.defaults = defaults;
		this.overrides = M3UIOverrides.of (control.getDisplay ());
	}

	@Override
	public Class<? extends M3ComponentUI> type () {
		return M3RowUI.class;
	}

	@Override
	public int [] paintKeys () {
		return paintKeys;
	}

	@Override
	public int [] metricKeys () {
		return metricKeys;
	}

	@Override
	public int rowHeight (int zoom) {
		return defaults.metric (rowHeight, 18, zoom);
	}

	@Override
	public int paintRow (Control control, GC gc, int x, int y, int width, int height, int state) {
		boolean selected = (state & SELECTED) != 0, focused = (state & FOCUSED) != 0;
		int fill = colour (control, background, 0xFFFFFFFF);
		if ((state & ODD) != 0 && defaults.contains (stripe)) fill = colour (control, stripe, fill);
		if ((state & HOT) != 0 && defaults.contains (hover)) fill = colour (control, hover, fill);
		if (selected) fill = colour (control, focused ? selection : inactive, fill);
		M3UIResources resources = M3UIResources.of (control.getDisplay ());
		gc.setBackground (resources.color (fill));
		gc.fillRectangle (x, y, width, height);
		if (defaults.bool (showGrid, false)) {
			gc.setForeground (resources.color (colour (control, grid, 0xFFC0C0C0)));
			gc.drawLine (x, y + height - 1, x + width - 1, y + height - 1);
		}
		int text = colour (control, foreground, 0xFF000000);
		return selected ? colour (control, focused ? selectionText : inactiveText, text) : text;
	}

	private int colour (Control control, int key, int fallback) {
		return defaults.color (overrides, control, key, fallback);
	}
}
