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
package org.eclipse.swt.tests.junit.m3fidelity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * SWITCHER_SIZE v1 (real-app fidelity leaf): the theme switcher (#286) sizes its drop-down to its entries when they change
 * (a theme registered and applied, a language switch); before, it asked its own composite for a layout, which keeps the
 * drop-down's cached size, so a longer entry was cut. The plain Combo test guards the viewport Combo's measurement.
 */
public class Test_org_eclipse_swt_widgets_ComboContentSize {
	Display display;
	Shell shell;

	@BeforeEach
	void open () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new RowLayout ());
	}

	@AfterEach
	void close () {
		shell.dispose ();
	}

	static int widest (Control control, String... texts) {
		GC gc = new GC (control);
		try {
			return Arrays.stream (texts).mapToInt (text -> gc.textExtent (text).x).max ().orElse (0);
		} finally {
			gc.dispose ();
		}
	}

	@Test
	public void test_readOnlyComboIsAsWideAsItsWidestItem () {
		Combo combo = new Combo (shell, SWT.READ_ONLY | SWT.DROP_DOWN);
		combo.setItems ("Light", "Dark", "Solarized High Contrast Dark");
		combo.select (0);
		int width = combo.computeSize (SWT.DEFAULT, SWT.DEFAULT).x;
		assertTrue (width > widest (combo, combo.getItems ()), "width " + width + " for items up to " + widest (combo, combo.getItems ()));
	}

	/* the event loop until it is idle, so deferred layouts (requestLayout) run */
	void pump () {
		long end = System.currentTimeMillis () + 500;
		while (System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) Thread.onSpinWait ();
		}
	}

	@Test
	public void test_themeSwitcherGrowsForALongerEntry () {
		shell.setSize (900, 200);
		M3ThemeSwitcher switcher = new M3ThemeSwitcher (shell, SWT.NONE);
		shell.open ();
		shell.layout (true, true);
		pump ();
		M3Theme longer = M3Theme.builder ("solarized-high-contrast-midnight", M3Theme.DARK).build ();
		M3Themes.register (longer);
		try {
			M3Themes.apply (display, longer);
			pump ();
			Combo combo = switcher.getCombo ();
			int width = combo.getSize ().x, entries = widest (combo, combo.getItems ());
			assertTrue (width > entries, "switcher drop-down " + width + " for entries up to " + entries + ": " + Arrays.toString (combo.getItems ()));
		} finally {
			M3Themes.reset (display);
			M3Themes.unregister (longer.name ());
		}
	}
}
