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

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Palettes, accent, high contrast, DPI-aware metrics and the light and dark built-ins (docs/m3/LOOK_AND_FEEL.md, LF6,
 * improvement 5). {@link #theAccentUpdatesOnlyItsReaders} and the high-contrast cases need a display.
 */
public class Test_org_eclipse_swt_internal_laf_M3Palette {
	static final int SELECTION = M3UIKeys.intern ("Table.selectionBackground", M3UIKeys.COLOR);
	static final int GRID = M3UIKeys.intern ("Table.gridColor", M3UIKeys.COLOR);
	static final int ROW_HEIGHT = M3UIKeys.intern ("Tree.rowHeight", M3UIKeys.INTEGER);
	static final int MARGIN = M3UIKeys.intern ("Button.margin", M3UIKeys.INSETS);

	@Test
	public void aRampGoesFromTintToShade () {
		int [] ramp = M3Palette.ramp (0xFF2F6FD0);
		assertEquals (M3Palette.STEPS.length, ramp.length);
		for (int i = 1; i < ramp.length; i++) {
			assertTrue (M3Palette.luminance (ramp [i]) < M3Palette.luminance (ramp [i - 1]), "step " + i);
			assertEquals (0xFF, ramp [i] >>> 24);
		}
		int [] grey = M3Palette.ramp (0xFF808080);
		assertEquals ((grey [5] >> 16) & 0xFF, grey [5] & 0xFF, "a grey accent gives a grey ramp");
	}

	@Test
	public void contrastPicksReadableText () {
		assertEquals (21.0, M3Palette.contrast (0xFF000000, 0xFFFFFFFF), 1e-9);
		assertEquals (1.0, M3Palette.contrast (0xFF2F6FD0, 0xFF2F6FD0), 1e-9);
		assertEquals (0xFF000000, M3Palette.readableOn (0xFFFFFFFF));
		assertEquals (0xFFFFFFFF, M3Palette.readableOn (0xFF000000));
		assertEquals (0xFFFFFFFF, M3Palette.readableOn (0xFF2F6FD0), "white on the flat accent");
		for (int background : new int [] {0xFF777777, 0xFFFFD700, 0xFF123456, 0xFFE0EEFE}) {
			int text = M3Palette.readableOn (background), other = text == 0xFF000000 ? 0xFFFFFFFF : 0xFF000000;
			assertTrue (M3Palette.contrast (text, background) >= M3Palette.contrast (other, background), Integer.toHexString (background));
		}
		assertTrue (M3Palette.contrast (0xFF000000, 0xFF777777) >= M3Palette.READABLE);
		assertEquals ("#2f6fd0", M3Palette.text (0xFF2F6FD0));
		assertEquals ("#2f6fd080", M3Palette.text (0x802F6FD0));
	}

	@Test
	public void metricsScaleWithTheZoom () {
		M3UIDefaults defaults = new M3UIDefaults (new LookAndFeelLoader (LookAndFeelThemes::text, name -> 0x808080).load ("flat"), new M3UIDefaults.Lanes ());
		int rowHeight = defaults.integer (ROW_HEIGHT, 0);
		assertEquals (rowHeight, defaults.metric (ROW_HEIGHT, 0, 100));
		assertEquals (rowHeight * 2, defaults.metric (ROW_HEIGHT, 0, 200));
		assertEquals (Math.round (rowHeight * 1.5f), defaults.metric (ROW_HEIGHT, 0, 150));
		int [] points = new int [4], pixels = new int [4];
		assertTrue (defaults.insets (MARGIN, points));
		assertTrue (defaults.insets (MARGIN, pixels, 200));
		for (int i = 0; i < 4; i++) assertEquals (points [i] * 2, pixels [i]);
		assertFalse (defaults.insets (M3UIKeys.intern ("Button.coverageNoInsets", M3UIKeys.INSETS), pixels, 200));
	}

	@Test
	public void lightAndDarkAreBuiltIn () {
		int flat = new LookAndFeelLoader (LookAndFeelThemes::text, name -> 0x808080).load ("flatdark").color ("Table.background", 0);
		M3LookAndFeel.setLookAndFeel ("dark");
		try {
			assertEquals ("dark", M3LookAndFeel.getLookAndFeel ());
			assertEquals (flat, M3LookAndFeel.getDefaults ().color (M3UIKeys.intern ("Table.background", M3UIKeys.COLOR), 0));
			M3LookAndFeel.setLookAndFeel ("light");
			assertEquals ("light", M3LookAndFeel.getLookAndFeel ());
		} finally {
			M3LookAndFeel.reset ();
		}
	}

	@Test
	public void theAccentUpdatesOnlyItsReaders () {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		M3LookAndFeel.register ("test-accent", "@extends = flat\n", null);
		M3LookAndFeel.setLookAndFeel ("test-accent");
		try {
			int gridBefore = M3LookAndFeel.getDefaults ().color (GRID, 0);
			Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe reader = new Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe (new int [] {SELECTION}, M3ComponentUI.NO_KEYS);
			Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe grid = new Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe (new int [] {GRID}, M3ComponentUI.NO_KEYS);
			M3LookAndFeel.installUI (new Canvas (shell, SWT.NONE), Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe.class, reader);
			M3LookAndFeel.installUI (new Canvas (shell, SWT.NONE), Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe.class, grid);
			assertEquals (1, M3LookAndFeel.setAccent (0xFFC0392B), "only the reader of an accent-derived key");
			assertEquals (2, reader.installs);
			assertEquals (1, grid.installs);
			assertEquals (0xFFC0392B, M3LookAndFeel.getDefaults ().color (SELECTION, 0), "Table.selectionBackground follows @accent");
			assertEquals (gridBefore, M3LookAndFeel.getDefaults ().color (GRID, 0));
			assertEquals (0xFFC0392B, reader.defaults.color (SELECTION, 0));
		} finally {
			M3LookAndFeel.getDefaults ().removeVariable ("accent");
			M3LookAndFeel.reset ();
			shell.dispose ();
		}
		assertEquals (0, M3LookAndFeel.setAccent (0xFF2F6FD0), "no widget left");
		M3LookAndFeel.getDefaults ().removeVariable ("accent");
	}

	@Test
	public void highContrastFromThePlatform () {
		Display display = Display.getDefault ();
		String table = M3Palette.highContrastTable (display);
		LookAndFeelDefaults defaults = new LookAndFeelLoader (n -> n.equals ("hc") ? table : LookAndFeelThemes.text (n), name -> 0x808080).load ("hc");
		assertEquals (2, defaults.integer ("Component.focusWidth", 0));
		assertEquals (defaults.color ("Component.foreground", 0), defaults.color ("Component.borderColor", 1), "borders in the foreground colour");
		org.eclipse.swt.graphics.RGB list = display.getSystemColor (SWT.COLOR_LIST_BACKGROUND).getRGB ();
		assertEquals (0xFF000000 | list.red << 16 | list.green << 8 | list.blue, defaults.color ("Component.background", 0));
		if (!display.getHighContrast ()) assertEquals (M3LookAndFeel.getLookAndFeel (), M3Palette.preferred (display));
	}
}
