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

import static org.eclipse.swt.m3.M3ThemeSlot.*;

import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;

/*
 * One switch of a display (M3Themes.apply, reset): the look and feel of the drawn planes, the operating system treatment,
 * and the colours of every control that follows the theme, with each shell's redraw suspended so that it repaints once.
 */
//FLLDCIM: HPCPOP — cold path: runs once per switch; per control it sets colours and never touches items
final class M3ThemeApplier {

	private M3ThemeApplier () {
	}

	static int apply (Display display, M3ThemeState state) {
		M3Theme theme = state.theme;
		lookAndFeel (theme);
		boolean dark = theme.dark ();
		boolean undo = state.from != null && state.from.dark () && !dark;
		M3NativeTheme.prepare (display, dark, dark ? state.color (display, WIDGET_BACKGROUND) : null,
			dark ? state.color (display, WIDGET_FOREGROUND) : null, state.color (display, GRIDLINE));
		Set<String> excluded = new TreeSet<> ();
		int themed = shells (display, control -> paint (control, display, state, dark, undo), excluded);
		excluded.forEach (widget -> Themes.select (widget, Theme.NATIVE));
		state.excluded.addAll (excluded);
		return themed;
	}

	static int reset (Display display, M3ThemeState state) {
		M3LookAndFeel.setLookAndFeel (state.lookAndFeel);
		state.excluded.forEach (widget -> Themes.select (widget, null));
		boolean undo = state.theme.dark ();
		M3NativeTheme.prepare (display, false, null, null, null);
		return shells (display, control -> clear (control, undo), new TreeSet<> ());
	}

	/* every control of every shell; a shell's redraw is suspended while its controls change (one repaint per shell) */
	private static int shells (Display display, java.util.function.Consumer<Control> action, Set<String> excluded) {
		return Arrays.stream (display.getShells ()).mapToInt (shell -> shell (shell, action, excluded)).sum ();
	}

	private static int shell (Shell shell, java.util.function.Consumer<Control> action, Set<String> excluded) {
		shell.setRedraw (false);
		try {
			return (int) walk (shell).filter (control -> visit (control, action, excluded)).count ();
		} finally {
			shell.setRedraw (true);
		}
	}

	static Stream<Control> walk (Control control) {
		Stream<Control> children = control instanceof Composite composite ? Arrays.stream (composite.getChildren ()).flatMap (M3ThemeApplier::walk) : Stream.empty ();
		return Stream.concat (Stream.of (control), children);
	}

	private static boolean visit (Control control, java.util.function.Consumer<Control> action, Set<String> excluded) {
		if (M3Themes.follows (control)) {
			action.accept (control);
			return true;
		}
		if (M3LookAndFeel.getUI (control) != null) excluded.add (control.getClass ().getSimpleName ());
		return false;
	}

	/* the theme as the look and feel m3-<name>: drawn planes reinstall only the delegates whose keys change */
	static void lookAndFeel (M3Theme theme) {
		String name = M3Themes.LOOK_AND_FEEL_PREFIX + theme.name ();
		String table = theme.toLookAndFeelTable ();
		if (name.equals (M3LookAndFeel.getLookAndFeel ())) {
			M3LookAndFeel.reload (name, table);
			return;
		}
		M3LookAndFeel.register (name, table, null);
		M3LookAndFeel.setLookAndFeel (name);
	}

	private static void paint (Control control, Display display, M3ThemeState state, boolean dark, boolean undo) {
		if (dark || undo) M3NativeTheme.apply (control, dark);
		boolean field = field (control);
		control.setBackground (state.color (display, field ? LIST_BACKGROUND : WIDGET_BACKGROUND));
		control.setForeground (state.color (display, field ? LIST_FOREGROUND : WIDGET_FOREGROUND));
		extras (control, slot -> state.color (display, slot));
	}

	private static void clear (Control control, boolean undo) {
		if (undo) M3NativeTheme.apply (control, false);
		control.setBackground (null);
		control.setForeground (null);
		extras (control, slot -> null);
	}

	/* the colours stock SWT keeps apart from background and foreground; a null colour is the default */
	private static void extras (Control control, java.util.function.IntFunction<Color> colors) {
		switch (control) {
			case Table table -> pair (table::setHeaderBackground, table::setHeaderForeground, colors, HEADER_BACKGROUND, HEADER_FOREGROUND);
			case Tree tree -> pair (tree::setHeaderBackground, tree::setHeaderForeground, colors, HEADER_BACKGROUND, HEADER_FOREGROUND);
			case StyledText text -> pair (text::setSelectionBackground, text::setSelectionForeground, colors, SELECTION, SELECTION_TEXT);
			case CTabFolder folder -> pair (folder::setSelectionBackground, folder::setSelectionForeground, colors, LIST_BACKGROUND, LIST_FOREGROUND);
			case Link link -> link.setLinkForeground (colors.apply (LINK_FOREGROUND));
			default -> { /* background and foreground only */ }
		}
	}

	private static void pair (java.util.function.Consumer<Color> background, java.util.function.Consumer<Color> foreground,
		java.util.function.IntFunction<Color> colors, int backgroundSlot, int foregroundSlot) {
		background.accept (colors.apply (backgroundSlot));
		foreground.accept (colors.apply (foregroundSlot));
	}

	/* controls that hold content (lists, editors): the list colours; every other control: the widget colours */
	static boolean field (Control control) {
		return control instanceof Table || control instanceof Tree || control instanceof List || control instanceof Text
			|| control instanceof StyledText || control instanceof Combo || control instanceof CCombo || control instanceof Spinner;
	}

	static int itemHeight (Control control) {
		return switch (control) {
			case Table table -> table.getItemHeight ();
			case Tree tree -> tree.getItemHeight ();
			case List list -> list.getItemHeight ();
			default -> -1;
		};
	}
}
