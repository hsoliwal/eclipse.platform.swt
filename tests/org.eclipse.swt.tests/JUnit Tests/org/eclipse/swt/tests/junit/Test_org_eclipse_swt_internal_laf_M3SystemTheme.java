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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The look and feel following the operating system (docs/m3/LOOK_AND_FEEL.md, LF6, FlatLaf #204 absorbed): the
 * preference read, a follower switching when the preference changes, and the <code>SWT.Settings</code> event of the
 * display reaching it (win32: <code>WM_SETTINGCHANGE</code> sent to the display's message window). Needs a display.
 */
public class Test_org_eclipse_swt_internal_laf_M3SystemTheme {
	static final int WM_SETTINGCHANGE = 0x1A;
	Display display;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
	}

	@AfterEach
	public void tearDown () {
		M3LookAndFeel.reset ();
	}

	@Test
	public void thePreferenceAndTheHighContrastOverride () throws Exception {
		assertEquals (Display.isSystemDarkTheme (), M3SystemTheme.dark ());
		String preferred = M3SystemTheme.preferred (display, "light", "dark");
		if (display.getHighContrast ()) assertEquals (M3Palette.HIGH_CONTRAST, preferred);
		else assertEquals (M3SystemTheme.dark () ? "dark" : "light", preferred);
		Method method = M3SystemTheme.class.getDeclaredMethod ("preferred", Display.class, String.class, String.class, java.util.function.BooleanSupplier.class);
		method.setAccessible (true);
		if (!display.getHighContrast ()) {
			assertEquals ("dark", method.invoke (null, display, "light", "dark", (java.util.function.BooleanSupplier) () -> true));
			assertEquals ("light", method.invoke (null, display, "light", "dark", (java.util.function.BooleanSupplier) () -> false));
		}
	}

	@Test
	public void aFollowerSwitchesWhenThePreferenceChanges () throws Exception {
		Assumptions.assumeFalse (display.getHighContrast (), "high contrast wins over light and dark");
		boolean [] dark = {false};
		Constructor<M3SystemTheme.Follower> constructor = M3SystemTheme.Follower.class.getDeclaredConstructor (Display.class, String.class, String.class, java.util.function.BooleanSupplier.class);
		constructor.setAccessible (true);
		try (M3SystemTheme.Follower follower = constructor.newInstance (display, "light", "dark", (java.util.function.BooleanSupplier) () -> dark [0])) {
			assertEquals ("light", follower.current ());
			assertEquals ("light", M3LookAndFeel.getLookAndFeel ());
			assertEquals (1, follower.switches ());
			follower.check ();
			assertEquals (1, follower.switches (), "no change, no switch");
			M3LookAndFeel.setLookAndFeel ("swing");
			follower.check ();
			assertEquals ("swing", M3LookAndFeel.getLookAndFeel (), "the application's choice is kept while the preference stands");
			dark [0] = true;
			follower.check ();
			assertEquals ("dark", follower.current ());
			assertEquals ("dark", M3LookAndFeel.getLookAndFeel ());
			assertEquals (2, follower.switches ());
			if ("win32".equals (SWT.getPlatform ())) {
				/* the platform's way: WM_SETTINGCHANGE to the display's message window, debounced into SWT.Settings */
				dark [0] = false;
				Field field = Display.class.getDeclaredField ("hwndMessage");
				field.setAccessible (true);
				Class<?> os = Class.forName ("org.eclipse.swt.internal.win32.OS");
				os.getMethod ("SendMessage", long.class, int.class, long.class, long.class).invoke (null, field.getLong (display), WM_SETTINGCHANGE, 0L, 0L);
				assertTrue (Test_org_eclipse_swt_internal_laf_M3ThemeImport.await (display, () -> follower.switches () == 3), "the settings event reached the follower");
				assertEquals ("light", M3LookAndFeel.getLookAndFeel ());
			}
		}
		M3SystemTheme.Follower real = M3SystemTheme.follow (display, "light", "dark");
		assertEquals (M3SystemTheme.dark () ? "dark" : "light", real.current ());
		real.close ();
		real.close ();
	}
}
