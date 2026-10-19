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
package org.eclipse.swt.internal.render;

import java.util.*;

import org.eclipse.swt.internal.laf.*;

/**
 * The theme of a widget class's drawn rows read from a look and feel's {@link M3UIDefaults} (docs/m3/LOOK_AND_FEEL.md
 * LF6; the keys in docs/m3/render-modes/LAF_KEYS.tsv): every colour by its key id ({@link M3UIKeys}), so the colours of
 * a look and feel live in its table, not in the drawn modes. The look (how selection, expanders and check boxes are
 * drawn) comes from the look and feel's name ({@link #look}); a key the table leaves out answers that look's built-in
 * value. The native look keeps the widget's own text and background colours (an application's colours win, as on the
 * platform). Row height and indent stay the platform's: in stage 1 the native control is the behaviour plane and owns
 * the geometry.
 */
public final class ThemeDefaults {
	/* per-widget colour properties, read in this order by of (ids 0 to 4) */
	private static final String [] WIDGET = {"foreground", "background", "selectionForeground", "selectionBackground", "selectionInactiveBackground"};
	/* shared colour keys (ids 5 to 11): hover, tree lines, grid, expander and box border, focus, check mark, grayed check mark */
	private static final String [] SHARED = {"Component.hoverBackground", "Tree.lineColor", "Table.gridColor", "Tree.expandIconColor",
		"Component.focusColor", "CheckBox.icon.checkmarkColor", "CheckBox.icon.disabledCheckmarkColor"};

	private ThemeDefaults () {
	}

	/** The key ids a widget class's rows paint with (a row delegate's paint keys). */
	public static int [] keys (String widget) {
		int [] ids = new int [WIDGET.length + SHARED.length];
		for (int i = 0; i < WIDGET.length; i++) ids [i] = M3UIKeys.of (widget, WIDGET [i], M3UIKeys.COLOR);
		for (int i = 0; i < SHARED.length; i++) ids [WIDGET.length + i] = M3UIKeys.intern (SHARED [i], M3UIKeys.COLOR);
		return ids;
	}

	/**
	 * The look of a look and feel: <code>native</code> draws platform parts, <code>swing</code>/<code>metal</code> the
	 * Metal glyphs; every other look and feel (<code>initiative31</code>, <code>light</code>, <code>dark</code>,
	 * registered and imported ones) the flat glyphs of Initiative 31.
	 */
	public static Theme look (String lookAndFeel) {
		String name = lookAndFeel == null ? "" : lookAndFeel.trim ().toLowerCase (Locale.ROOT);
		return switch (name) {
			case "", LookAndFeel.NATIVE -> Theme.NATIVE;
			case "swing", "metal" -> Theme.SWING;
			default -> Theme.INITIATIVE31;
		};
	}

	/**
	 * The theme of a widget class under a look and feel.
	 *
	 * @param widget the widget's simple class name (<code>Tree</code>, <code>Table</code>, <code>List</code>)
	 * @param look the built-in theme whose look this is (its values answer the keys the table leaves out)
	 * @param defaults the look and feel's defaults
	 */
	public static Theme of (String widget, Theme look, M3UIDefaults defaults) {
		int [] ids = keys (widget);
		Theme.Builder b = look.derive (defaults.name ());
		if (look.look != Theme.LOOK_NATIVE) {
			b.colors (rgb (defaults, ids [0], look.foreground), rgb (defaults, ids [1], look.background)).selectionForeground (rgb (defaults, ids [2], look.selectionForeground));
		}
		return b.selection (rgb (defaults, ids [3], look.selectionBackground), rgb (defaults, ids [4], look.selectionInactiveBackground))
			.hot (rgb (defaults, ids [5], look.hotBackground)).lines (rgb (defaults, ids [6], look.hash), rgb (defaults, ids [7], look.grid))
			.border (rgb (defaults, ids [8], look.border)).focus (rgb (defaults, ids [9], look.focusColor))
			.check (rgb (defaults, ids [10], look.checkFill), rgb (defaults, ids [11], look.grayFill)).build ();
	}

	/* the 0xRRGGBB of a colour key (ARGB without alpha), or the fallback when neither the key nor its parent has one */
	private static int rgb (M3UIDefaults defaults, int key, int fallback) {
		return defaults.contains (key) ? defaults.color (key, fallback) & 0xFFFFFF : fallback;
	}
}
