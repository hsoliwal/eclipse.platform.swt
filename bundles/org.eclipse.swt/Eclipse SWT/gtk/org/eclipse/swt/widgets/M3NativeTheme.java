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

/**
 * The operating system's own treatment of a light or dark M3 theme (org.eclipse.swt.m3.M3Themes). GTK has no
 * runtime switch of the native controls' dark appearance in SWT yet: {@link #prepare} and {@link #apply} answer false and
 * M3Themes falls back to background and foreground colours.
 *
 * @noreference This class is not intended to be referenced by clients; use org.eclipse.swt.m3.M3Themes.
 * @since 3.138
 */
public final class M3NativeTheme {

	private M3NativeTheme () {
	}

	/**
	 * @param display the display
	 * @param dark whether the theme is dark
	 * @param menuBackground the menu bar background, or null
	 * @param menuForeground the menu bar foreground, or null
	 * @param headerLine the column delimiter colour of custom-coloured table headers, or null
	 * @return false: no native treatment on this platform
	 */
	public static boolean prepare (Display display, boolean dark, Color menuBackground, Color menuForeground, Color headerLine) {
		return false;
	}

	/**
	 * @param control the control
	 * @param dark whether the theme is dark
	 * @return false: no native treatment on this platform
	 */
	public static boolean apply (Control control, boolean dark) {
		return false;
	}

	/** @return false: no native dark treatment on this platform */
	public static boolean available () {
		return false;
	}
}
