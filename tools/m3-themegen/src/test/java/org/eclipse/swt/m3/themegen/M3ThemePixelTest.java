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
package org.eclipse.swt.m3.themegen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.*;
import java.nio.file.*;
import java.util.List;

import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Pixel smoke test (docs/m3/look-and-feel/GENERATOR.md): one generated theme (the recorded answer) applied at runtime
 * to SWT (a canvas drawn through a look and feel delegate) and to Swing (a <code>JTable</code> through
 * <code>UIManager</code>); the selected row of both shows the theme's selection colour. Runs on the hidden desktop
 * with <code>-Dm3.themegen.desktop=true</code>; skipped headless.
 */
public class M3ThemePixelTest {
	@Test
	public void theSameThemeAppliesInBothToolkits () throws Exception {
		Assumptions.assumeTrue (Boolean.getBoolean ("m3.themegen.desktop"), "needs a desktop: -Dm3.themegen.desktop=true");
		M3ThemeGenerator.Generated generated = new M3ThemeGenerator (new M3FixtureModel (Paths.get (System.getProperty ("m3.themegen.fixtures", "src/test/resources/fixtures"), "harbour-night.json")))
			.generate ("x", List.of (), null, null, false, null);
		int selection = 0xFF3B4252;
		Display display = new Display ();
		Shell shell = new Shell (display);
		try {
			M3LookAndFeel.register ("pixel", M3ThemeExport.lf2 (generated.theme ()), null);
			M3LookAndFeel.setLookAndFeel ("pixel");
			Canvas canvas = new Canvas (shell, SWT.NONE);
			M3LookAndFeel.installUI (canvas, M3ThemeGeneratorExample.Rows.class, new M3ThemeGeneratorExample.Rows ());
			canvas.setBounds (0, 0, 200, 120);
			shell.setSize (240, 160);
			shell.open ();
			Image image = new Image (display, 200, 120);
			GC gc = new GC (image);
			M3ThemeGeneratorExample.paintRows (canvas, gc);
			gc.dispose ();
			ImageData data = image.getImageData ();
			int rowHeight = ((M3ThemeGeneratorExample.Rows) M3LookAndFeel.getUI (canvas)).rowHeight;
			RGB rgb = data.palette.getRGB (data.getPixel (150, rowHeight * 2 + rowHeight / 2));
			image.dispose ();
			assertEquals (selection, 0xFF000000 | rgb.red << 16 | rgb.green << 8 | rgb.blue, "SWT: the selected row");
			int [] swing = new int [1];
			String flatLaf = M3ThemeExport.flatLaf (generated.theme ());
			SwingUtilities.invokeAndWait (() -> {
				M3SwingTheme.apply (flatLaf);
				JTable table = new JTable (new Object [][] {{"a"}, {"b"}, {"c"}}, new Object [] {"c"});
				table.setSize (200, 100);
				table.setRowSelectionInterval (2, 2);
				BufferedImage buffer = new BufferedImage (200, 100, BufferedImage.TYPE_INT_RGB);
				table.paint (buffer.getGraphics ());
				swing [0] = 0xFF000000 | buffer.getRGB (150, table.getRowHeight () * 2 + table.getRowHeight () / 2);
			});
			assertEquals (selection, swing [0], "Swing: the selected row");
		} finally {
			M3LookAndFeel.reset ();
			shell.dispose ();
			display.dispose ();
		}
	}
}
