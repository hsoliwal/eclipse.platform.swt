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
package org.eclipse.swt.m3;

import java.util.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/* a display's theme, its colours (one per slot, lazy) and the look and feel it had before the first apply (M3Themes) */
final class M3ThemeState {
	final String lookAndFeel;
	/* the drawn widget classes kept stock by theme-provider=false (Themes.select (widget, Theme.NATIVE)) */
	final Set<String> excluded = new TreeSet<> ();
	M3Theme theme, from;
	Color [] colors = new Color [M3ThemeSlot.COUNT];
	Color [] previous;

	M3ThemeState (String lookAndFeel) {
		this.lookAndFeel = lookAndFeel;
	}

	void switchTo (M3Theme next) {
		from = theme;
		theme = next;
		previous = colors;
		colors = new Color [M3ThemeSlot.COUNT];
	}

	Color color (Display display, int slot) {
		Color color = colors [slot];
		if (color == null || color.isDisposed ()) colors [slot] = color = new Color (display, rgba (theme.get (slot)));
		return color;
	}

	/* the colours of the previous theme, once every control holds the new ones */
	void release () {
		if (previous != null) Arrays.stream (previous).filter (Objects::nonNull).forEach (Color::dispose);
		previous = null;
	}

	void dispose () {
		release ();
		Arrays.stream (colors).filter (Objects::nonNull).forEach (Color::dispose);
	}

	private static RGBA rgba (int argb) {
		return new RGBA ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24);
	}
}
