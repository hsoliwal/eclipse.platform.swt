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

import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

import javax.imageio.*;
import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Example (docs/m3/look-and-feel/EXAMPLES.md): generate a theme from a prompt, then apply it live to an SWT shell and
 * a Swing <code>JFrame</code> side by side. The SWT shell draws rows through a look and feel delegate; the Swing frame
 * shows a <code>JTable</code> themed through <code>UIManager</code> ({@link M3SwingTheme}).
 * <pre>
 * java -cp ... org.eclipse.swt.m3.themegen.M3ThemeGeneratorExample --prompt "..." [--ollama [model] | --anthropic [model] | --fixture answer.json]
 *   [--brand #hex] [--twin] [--screenshots &lt;directory&gt;]
 * </pre>
 * No key in code: the Anthropic backend reads <code>ANTHROPIC_API_KEY</code> from the environment. The "Generate"
 * button asks the model again with the prompt in the text field; "Twin" switches both toolkits to the other brightness.
 */
public final class M3ThemeGeneratorExample {
	private static final Logger LOG = Logger.getLogger (M3ThemeGeneratorExample.class.getName ());

	/* an SWT delegate that keeps the row colours of the current look and feel */
	static final class Rows implements M3ComponentUI {
		static final int BACKGROUND = M3UIKeys.intern ("Table.background", M3UIKeys.COLOR), FOREGROUND = M3UIKeys.intern ("Table.foreground", M3UIKeys.COLOR);
		static final int SELECTION = M3UIKeys.intern ("Table.selectionBackground", M3UIKeys.COLOR), SELECTION_TEXT = M3UIKeys.intern ("Table.selectionForeground", M3UIKeys.COLOR);
		static final int STRIPE = M3UIKeys.intern ("Table.stripeBackground", M3UIKeys.COLOR), ROW_HEIGHT = M3UIKeys.intern ("Table.rowHeight", M3UIKeys.INTEGER);
		int background, foreground, selection, selectionText, stripe, rowHeight;

		@Override
		public void installUI (Control control, M3UIDefaults defaults) {
			background = defaults.color (BACKGROUND, 0xFFFFFFFF);
			foreground = defaults.color (FOREGROUND, 0xFF000000);
			selection = defaults.color (SELECTION, 0xFF0078D7);
			selectionText = defaults.color (SELECTION_TEXT, 0xFFFFFFFF);
			stripe = defaults.color (STRIPE, background);
			rowHeight = Math.max (16, defaults.integer (ROW_HEIGHT, 18));
		}

		@Override
		public int [] paintKeys () {
			return new int [] {BACKGROUND, FOREGROUND, SELECTION, SELECTION_TEXT, STRIPE};
		}

		@Override
		public int [] metricKeys () {
			return new int [] {ROW_HEIGHT};
		}
	}

	public static void main (String [] args) throws Exception {
		System.setProperty ("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
		Map<String, String> options = M3ThemeGenerator.options (args);
		M3ThemeGenerator generator = new M3ThemeGenerator (M3ThemeGenerator.model (options));
		String prompt = options.getOrDefault ("prompt", "a calm harbour at night");
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3 theme generator: SWT");
		shell.setLayout (new GridLayout (3, false));
		Text field = new Text (shell, SWT.BORDER);
		field.setText (prompt);
		field.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		Button generate = new Button (shell, SWT.PUSH);
		generate.setText ("Generate");
		Button twin = new Button (shell, SWT.PUSH);
		twin.setText ("Twin");
		Canvas rows = new Canvas (shell, SWT.DOUBLE_BUFFERED | SWT.BORDER);
		rows.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 3, 1));
		M3LookAndFeel.installUI (rows, Rows.class, new Rows ());
		rows.addListener (SWT.Paint, e -> paintRows (rows, e.gc));
		JFrame frame = new JFrame ("M3 theme generator: Swing");
		JTable table = new JTable (new Object [][] {{"Swing row 0"}, {"Swing row 1"}, {"Swing row 2"}, {"Swing row 3"}, {"Swing row 4"}, {"Swing row 5"}}, new Object [] {"Column"});
		table.setRowSelectionInterval (2, 2);
		frame.setContentPane (new JScrollPane (table));
		frame.setDefaultCloseOperation (JFrame.DISPOSE_ON_CLOSE);
		frame.setSize (400, 300);
		M3ThemeGenerator.Generated [] current = new M3ThemeGenerator.Generated [1];
		Runnable apply = () -> {
			if (current [0] == null) return;
			M3ThemeCompleter.Theme theme = current [0].theme ();
			M3LookAndFeel.register ("generated", M3ThemeExport.lf2 (theme), null);
			M3LookAndFeel.setLookAndFeel ("generated");
			String flatLaf = M3ThemeExport.flatLaf (theme);
			SwingUtilities.invokeLater (() -> M3SwingTheme.apply (flatLaf));
			shell.setText ("M3 theme generator: SWT, " + theme.name ());
			SwingUtilities.invokeLater (() -> frame.setTitle ("M3 theme generator: Swing, " + theme.name ()));
		};
		generate.addListener (SWT.Selection, e -> {
			try {
				current [0] = generator.generate (field.getText (), M3ThemeGenerator.brand (options.get ("brand")), null, null, true, null);
				apply.run ();
			} catch (IOException | IllegalArgumentException x) {
				LOG.log (Level.WARNING, "no theme: " + x.getMessage ());
			}
		});
		twin.addListener (SWT.Selection, e -> {
			if (current [0] == null || current [0].twin () == null) return;
			current [0] = new M3ThemeGenerator.Generated (current [0].twin (), current [0].theme (), current [0].spec (), current [0].answer (), null);
			apply.run ();
		});
		shell.setSize (400, 300);
		shell.setLocation (40, 40);
		shell.open ();
		frame.setLocation (460, 40);
		SwingUtilities.invokeAndWait (() -> frame.setVisible (true));
		current [0] = generator.generate (prompt, M3ThemeGenerator.brand (options.get ("brand")), null, null, true, null);
		apply.run ();
		if (options.containsKey ("screenshots")) {
			screenshots (display, shell, frame, rows, options.get ("screenshots"), current [0]);
			twin.notifyListeners (SWT.Selection, new Event ());
			screenshots (display, shell, frame, rows, options.get ("screenshots"), current [0]);
			shell.dispose ();
		}
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		SwingUtilities.invokeLater (frame::dispose);
		display.dispose ();
	}

	static void paintRows (Canvas canvas, GC gc) {
		Rows ui = (Rows) M3LookAndFeel.getUI (canvas);
		M3UIResources resources = M3UIResources.of (canvas.getDisplay ());
		int width = canvas.getClientArea ().width;
		for (int i = 0, y = 0; y < canvas.getClientArea ().height; i++, y += ui.rowHeight) {
			boolean selected = i == 2;
			gc.setBackground (resources.color (selected ? ui.selection : i % 2 == 1 ? ui.stripe : ui.background));
			gc.fillRectangle (0, y, width, ui.rowHeight);
			gc.setForeground (resources.color (selected ? ui.selectionText : ui.foreground));
			gc.drawText ("SWT row " + i, 6, y + 2, true);
		}
	}

	/* the SWT rows and the Swing table of the current theme as PNG files */
	static void screenshots (Display display, Shell shell, JFrame frame, Canvas rows, String directory, M3ThemeGenerator.Generated generated) throws Exception {
		Files.createDirectories (Paths.get (directory));
		String stem = generated.stem ();
		while (display.readAndDispatch ()) {
			// paint
		}
		Rectangle bounds = rows.getBounds ();
		Image image = new Image (display, bounds.width, bounds.height);
		GC gc = new GC (image);
		rows.print (gc);
		gc.dispose ();
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		loader.save (directory + "/generated-" + stem + "-swt.png", SWT.IMAGE_PNG);
		image.dispose ();
		SwingUtilities.invokeAndWait (() -> {
			BufferedImage swing = new BufferedImage (frame.getContentPane ().getWidth (), frame.getContentPane ().getHeight (), BufferedImage.TYPE_INT_RGB);
			frame.getContentPane ().paint (swing.getGraphics ());
			try {
				ImageIO.write (swing, "png", new File (directory, "generated-" + stem + "-swing.png"));
			} catch (IOException e) {
				LOG.log (Level.WARNING, "no Swing screenshot: " + e.getMessage ());
			}
		});
		LOG.info ("saved generated-" + stem + "-{swt,swing}.png in " + directory);
	}
}
