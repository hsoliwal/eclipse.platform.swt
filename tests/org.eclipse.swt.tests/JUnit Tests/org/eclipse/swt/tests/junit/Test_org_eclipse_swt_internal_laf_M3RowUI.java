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
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The row delegate of the drawn Tree, Table and List (docs/m3/LOOK_AND_FEEL.md, LF6): background, stripe, hover,
 * active and inactive selection and grid line painted from the look and feel's defaults and the widget's overrides,
 * row heights scaled by the zoom, and a look and feel's factory replacing the default delegate. Needs a display.
 */
public class Test_org_eclipse_swt_internal_laf_M3RowUI {
	static final String ROWS = """
		@extends = flat
		Table.background = #ffffff
		Table.foreground = #101010
		Table.selectionBackground = #2050a0
		Table.selectionForeground = #fefefe
		Table.selectionInactiveBackground = #d0d0d0
		Table.selectionInactiveForeground = #202020
		Table.stripeBackground = #f0f0f0
		Table.hoverBackground = #e0e8f0
		Table.gridColor = #0000ff
		Table.showGrid = true
		Table.rowHeight = 20
		""";

	Display display;
	Shell shell;
	Canvas canvas;
	Image image;
	GC gc;

	@BeforeEach
	public void setUp () {
		M3LookAndFeel.register ("test-rows", ROWS, null);
		M3LookAndFeel.setLookAndFeel ("test-rows");
		display = Display.getDefault ();
		shell = new Shell (display);
		canvas = new Canvas (shell, SWT.NONE);
		image = new Image (display, 40, 20);
		gc = new GC (image);
	}

	@AfterEach
	public void tearDown () {
		gc.dispose ();
		image.dispose ();
		shell.dispose ();
		M3LookAndFeel.reset ();
	}

	int pixel (int x, int y) {
		ImageData data = image.getImageData ();
		RGB rgb = data.palette.getRGB (data.getPixel (x, y));
		return 0xFF000000 | rgb.red << 16 | rgb.green << 8 | rgb.blue;
	}

	@Test
	public void everyStatePaintsItsColours () {
		M3RowUI rows = M3LookAndFeel.installUI (canvas, M3RowUI.class, new M3DefaultRowUI ("Table"));
		assertEquals (M3RowUI.class, rows.type ());
		assertEquals (0xFF101010, rows.paintRow (canvas, gc, 0, 0, 40, 20, 0));
		assertEquals (0xFFFFFFFF, pixel (5, 5), "background");
		assertEquals (0xFF0000FF, pixel (5, 19), "the grid line");
		rows.paintRow (canvas, gc, 0, 0, 40, 20, M3RowUI.ODD);
		assertEquals (0xFFF0F0F0, pixel (5, 5), "stripe");
		rows.paintRow (canvas, gc, 0, 0, 40, 20, M3RowUI.HOT);
		assertEquals (0xFFE0E8F0, pixel (5, 5), "hover");
		assertEquals (0xFFFEFEFE, rows.paintRow (canvas, gc, 0, 0, 40, 20, M3RowUI.SELECTED | M3RowUI.FOCUSED | M3RowUI.ODD));
		assertEquals (0xFF2050A0, pixel (5, 5), "active selection over the stripe");
		assertEquals (0xFF202020, rows.paintRow (canvas, gc, 0, 0, 40, 20, M3RowUI.SELECTED));
		assertEquals (0xFFD0D0D0, pixel (5, 5), "inactive selection");
		assertEquals (20, rows.rowHeight (100));
		assertEquals (40, rows.rowHeight (200));
		assertEquals (1, rows.metricKeys ().length);
		assertEquals (10, rows.paintKeys ().length);
	}

	@Test
	public void overridesAndLooksWithoutStripesOrGrid () {
		M3RowUI rows = M3LookAndFeel.installUI (canvas, M3RowUI.class, new M3DefaultRowUI ("Table"));
		M3LookAndFeel.override (canvas, M3UIKeys.intern ("Table.background", M3UIKeys.COLOR), 0xFF00FF00);
		rows.paintRow (canvas, gc, 0, 0, 40, 20, 0);
		assertEquals (0xFF00FF00, pixel (5, 5), "the widget's own background");
		Canvas list = new Canvas (shell, SWT.NONE);
		M3RowUI plain = M3LookAndFeel.installUI (list, M3RowUI.class, new M3DefaultRowUI ("CoverageList"));
		int text = plain.paintRow (list, gc, 0, 0, 40, 20, M3RowUI.ODD | M3RowUI.HOT);
		int component = M3LookAndFeel.getDefaults ().color (M3UIKeys.intern ("Component.background", M3UIKeys.COLOR), 0);
		int hover = M3LookAndFeel.getDefaults ().color (M3UIKeys.intern ("Component.hoverBackground", M3UIKeys.COLOR), component);
		assertEquals (hover, pixel (5, 5), "no stripe key: the Component hover over the Component background");
		assertEquals (M3LookAndFeel.getDefaults ().color (M3UIKeys.intern ("Component.foreground", M3UIKeys.COLOR), 0), text);
		assertEquals (pixel (5, 5), pixel (5, 19), "no grid line without showGrid");
	}

	@Test
	public void aLookAndFeelFactoryReplacesTheRowDelegate () {
		M3RowUI custom = new M3RowUI () {
			@Override
			public void installUI (Control control, M3UIDefaults defaults) {
			}

			@Override
			public int [] paintKeys () {
				return NO_KEYS;
			}

			@Override
			public int rowHeight (int zoom) {
				return 7;
			}

			@Override
			public int paintRow (Control control, GC gc, int x, int y, int width, int height, int state) {
				return 1;
			}
		};
		M3LookAndFeel.register ("test-rows-factory", ROWS, new RendererFactory () {
			@Override
			public <R> R create (Class<R> type, Object widget) {
				return type == M3RowUI.class ? type.cast (custom) : null;
			}
		});
		M3LookAndFeel.setLookAndFeel ("test-rows-factory");
		assertSame (custom, M3LookAndFeel.installUI (canvas, M3RowUI.class, new M3DefaultRowUI ("Table")));
		assertEquals (7, custom.rowHeight (100));
	}
}
