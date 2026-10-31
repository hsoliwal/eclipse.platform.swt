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

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.win32.*;

/**
 * The operating system's own treatment of a light or dark M3 theme (org.eclipse.swt.m3.M3Themes): on Windows 10 1903 and
 * later the undocumented dark mode of the common controls (scroll bars, tree expanders, headers, buttons), which the stock
 * Display data keys apply only to controls created afterwards. Here the keys are set for later controls ({@link #prepare})
 * and existing controls are switched one by one ({@link #apply}), without recreating them.
 *
 * @noreference This class is not intended to be referenced by clients; use org.eclipse.swt.m3.M3Themes.
 * @since 3.138
 */
public final class M3NativeTheme {

	private M3NativeTheme () {
	}

	/**
	 * Sets the display's theme data for controls created later: the dark explorer theme, the dark combo, the menu bar and
	 * table header line colours (null colours remove the data, back to stock).
	 *
	 * @param display the display
	 * @param dark whether the theme is dark
	 * @param menuBackground the menu bar background, or null
	 * @param menuForeground the menu bar foreground, or null
	 * @param headerLine the column delimiter colour of custom-coloured table headers, or null
	 * @return true when the platform has a dark treatment of native controls
	 */
	public static boolean prepare (Display display, boolean dark, Color menuBackground, Color menuForeground, Color headerLine) {
		display.setData (Display.USE_DARKMODE_EXPLORER_THEME_KEY, dark ? Boolean.TRUE : null);
		display.setData (Display.COMBO_USE_DARK_THEME, dark ? Boolean.TRUE : null);
		display.setData (Display.MENUBAR_BACKGROUND_COLOR_KEY, menuBackground);
		display.setData (Display.MENUBAR_FOREGROUND_COLOR_KEY, menuForeground);
		display.setData (Display.TABLE_HEADER_LINE_COLOR_KEY, headerLine);
		return available ();
	}

	/**
	 * Switches an existing control to the dark or light system theme of its window class (no handle is recreated).
	 *
	 * @param control the control
	 * @param dark whether the theme is dark
	 * @return true when the platform applied a native treatment
	 */
	public static boolean apply (Control control, boolean dark) {
		if (!available () || control.isDisposed ()) return false;
		long handle = control.handle;
		OS.AllowDarkModeForWindow (handle, dark);
		OS.SetWindowTheme (handle, Display.EXPLORER, null);
		if (dark) control.maybeEnableDarkSystemTheme ();
		OS.SendMessage (handle, OS.WM_THEMECHANGED, 0, 0);
		return true;
	}

	/** @return true when this platform has a native dark treatment */
	public static boolean available () {
		return !Display.disableCustomThemeTweaks && OS.IsDarkModeAvailable ();
	}
}
