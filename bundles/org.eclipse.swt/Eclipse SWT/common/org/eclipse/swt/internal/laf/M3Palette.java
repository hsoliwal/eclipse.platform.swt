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

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * Palettes (docs/m3/LOOK_AND_FEEL.md, LF6, improvement 5): a tonal ramp generated from an accent colour (HSL
 * lightness steps at the accent's hue and saturation, the pattern of FlatLaf's accent colour, Darklaf's themes and
 * Radiance's derived colour schemes), WCAG 2 contrast to pick a readable text colour, and the high-contrast look and
 * feel taken from the platform's colours when the platform runs in high contrast.
 */
public final class M3Palette {
	/** The lightness (percent) of the steps of {@link #ramp}: from the lightest tint to the darkest shade. */
	public static final int [] STEPS = {95, 90, 80, 70, 60, 50, 40, 30, 20, 10};
	/** WCAG 2 AA contrast for normal text. */
	public static final double READABLE = 4.5;
	/** The name of the high-contrast look and feel. */
	public static final String HIGH_CONTRAST = "highcontrast";

	private M3Palette () {
	}

	/** The tonal ramp of an accent: one opaque ARGB colour per {@link #STEPS} lightness, at the accent's hue and saturation. */
	public static int [] ramp (int accent) {
		float [] hsl = LookAndFeelLoader.hsl (accent);
		int [] ramp = new int [STEPS.length];
		for (int i = 0; i < STEPS.length; i++) ramp [i] = LookAndFeelLoader.argb (new float [] {hsl [0], hsl [1], STEPS [i]}, 255);
		return ramp;
	}

	/** The WCAG 2 relative luminance of an ARGB colour (alpha ignored), 0 (black) to 1 (white). */
	public static double luminance (int argb) {
		return 0.2126 * linear ((argb >> 16) & 0xFF) + 0.7152 * linear ((argb >> 8) & 0xFF) + 0.0722 * linear (argb & 0xFF);
	}

	/** The WCAG 2 contrast ratio of two colours, 1 to 21. */
	public static double contrast (int a, int b) {
		double x = luminance (a), y = luminance (b);
		return (Math.max (x, y) + 0.05) / (Math.min (x, y) + 0.05);
	}

	/** Black or white, whichever contrasts more with a background (the text colour on an accent). */
	public static int readableOn (int background) {
		return contrast (background, 0xFF000000) >= contrast (background, 0xFFFFFFFF) ? 0xFF000000 : 0xFFFFFFFF;
	}

	/** An ARGB colour as table text, <code>#rrggbb</code> or <code>#rrggbbaa</code>. */
	public static String text (int argb) {
		return (argb >>> 24) == 0xFF ? String.format ("#%06x", argb & 0xFFFFFF) : String.format ("#%06x%02x", argb & 0xFFFFFF, argb >>> 24);
	}

	/**
	 * The table of the high-contrast look and feel: the platform's list, widget and selection colours (on a display
	 * thread, with the platform in high contrast, these are its high-contrast colours), borders in the foreground colour
	 * and a two-pixel focus.
	 */
	public static String highContrastTable (Display display) {
		int background = system (display, SWT.COLOR_LIST_BACKGROUND), foreground = system (display, SWT.COLOR_LIST_FOREGROUND);
		int selection = system (display, SWT.COLOR_LIST_SELECTION), selectionText = system (display, SWT.COLOR_LIST_SELECTION_TEXT);
		return "@extends = native\n"
			+ "Component.background = " + text (background) + "\nComponent.foreground = " + text (foreground) + "\n"
			+ "Component.borderColor = " + text (foreground) + "\nComponent.focusColor = " + text (foreground) + "\n"
			+ "Component.focusWidth = 2\n"
			+ "Component.selectionBackground = " + text (selection) + "\nComponent.selectionForeground = " + text (selectionText) + "\n"
			+ "Component.selectionInactiveBackground = " + text (selection) + "\nComponent.selectionInactiveForeground = " + text (selectionText) + "\n";
	}

	/**
	 * The look and feel the platform asks for: {@link #HIGH_CONTRAST} (registered from the platform's colours) when the
	 * display runs in high contrast, otherwise the selected one.
	 */
	public static String preferred (Display display) {
		if (!display.getHighContrast ()) return M3LookAndFeel.getLookAndFeel ();
		M3LookAndFeel.register (HIGH_CONTRAST, highContrastTable (display), null);
		return HIGH_CONTRAST;
	}

	private static int system (Display display, int id) {
		RGB rgb = display.getSystemColor (id).getRGB ();
		return 0xFF000000 | rgb.red << 16 | rgb.green << 8 | rgb.blue;
	}

	private static double linear (int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow ((c + 0.055) / 1.055, 2.4);
	}
}
