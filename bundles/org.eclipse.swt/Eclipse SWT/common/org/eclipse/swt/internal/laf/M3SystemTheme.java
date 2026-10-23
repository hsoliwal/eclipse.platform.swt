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

import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;

/**
 * The look and feel the operating system asks for, followed at runtime (docs/m3/LOOK_AND_FEEL.md, LF6; absorbs
 * FlatLaf #204, automatic light and dark switching with the OS): {@link Display#isSystemDarkTheme()} answers the
 * platform's preference (win32: the registry value <code>AppsUseLightTheme</code>; GTK and Cocoa: the toolkit's),
 * and the platform tells SWT about a change with an <code>SWT.Settings</code> event (win32: <code>WM_SETTINGCHANGE</code>
 * with <code>ImmersiveColorSet</code>, debounced by the display). A {@link Follower} switches the look and feel on
 * that event when the preference changed, and high contrast ({@link Display#getHighContrast()}) wins over both.
 */
public final class M3SystemTheme {
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.laf.M3SystemTheme");

	private M3SystemTheme () {
	}

	/** The platform's preference: true for a dark appearance. */
	public static boolean dark () {
		return Display.isSystemDarkTheme ();
	}

	/**
	 * The look and feel the platform asks for: {@link M3Palette#HIGH_CONTRAST} in high contrast, else the dark or
	 * the light name.
	 */
	public static String preferred (Display display, String light, String dark) {
		return preferred (display, light, dark, M3SystemTheme::dark);
	}

	static String preferred (Display display, String light, String dark, BooleanSupplier darkness) {
		if (display.getHighContrast ()) return M3Palette.preferred (display);
		return darkness.getAsBoolean () ? dark : light;
	}

	/**
	 * Selects the preferred look and feel now and again on every settings change of the display (the application's
	 * {@link M3LookAndFeel#setLookAndFeel} calls in between are kept until the platform's preference changes).
	 *
	 * @param display the display whose settings events are followed (its thread)
	 * @param light the look and feel of a light preference, for example <code>light</code>
	 * @param dark the look and feel of a dark preference, for example <code>dark</code>
	 */
	public static Follower follow (Display display, String light, String dark) {
		return new Follower (display, light, dark, M3SystemTheme::dark);
	}

	/** One display following the platform; {@link #close} stops it. */
	public static final class Follower implements AutoCloseable {
		private final Display display;
		private final String light, dark;
		private final BooleanSupplier darkness;
		private final Listener listener = event -> check ();
		private String current;
		private int switches;

		Follower (Display display, String light, String dark, BooleanSupplier darkness) {
			this.display = display;
			this.light = light;
			this.dark = dark;
			this.darkness = darkness;
			display.addListener (SWT.Settings, listener);
			current = preferred (display, light, dark, darkness);
			M3LookAndFeel.setLookAndFeel (current);
			switches++;
			LOG.debug (() -> "following the platform: " + current);
		}

		/** The look and feel last selected for the platform's preference. */
		public String current () {
			return current;
		}

		/** How many times the follower selected a look and feel (once at the start, then per change). */
		public int switches () {
			return switches;
		}

		/** Re-reads the platform's preference and switches when it changed (what a settings event does). */
		public void check () {
			String preferred = preferred (display, light, dark, darkness);
			if (preferred.equals (current)) return;
			current = preferred;
			M3LookAndFeel.setLookAndFeel (preferred);
			switches++;
			LOG.debug (() -> "the platform changed its preference: " + preferred);
		}

		@Override
		public void close () {
			if (!display.isDisposed ()) display.removeListener (SWT.Settings, listener);
		}
	}
}
