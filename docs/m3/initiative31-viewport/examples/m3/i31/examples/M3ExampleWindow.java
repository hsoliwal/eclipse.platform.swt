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
package m3.i31.examples;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.*;
import java.io.*;

import javax.imageio.*;
import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * The window of the Initiative 31 viewport examples: a line describing the run (widget, rows, mode, look and feel,
 * timings) above the widget. Arguments shared by every example:
 * <ul>
 * <li><code>--rows N</code>: number of rows (default 1,000,000);</li>
 * <li><code>--top N</code>: row to show at the top after opening (default: the middle row);</li>
 * <li><code>--shot file.png</code>: write a screenshot of the window after opening and exit.</li>
 * </ul>
 * The mode is chosen with <code>-Dswt.widget.mode=viewport|original</code> and the look and feel with
 * Initiative 31's <code>-Dswt.swing.laf=&lt;LookAndFeel class&gt;</code>.
 */
final class M3ExampleWindow {
	final Display display;
	final Shell shell;
	final Label info;
	final int rows;
	final int top;
	private final String shot;

	M3ExampleWindow (String widget, String [] args) {
		rows = intArg (args, "--rows", 1_000_000);
		top = intArg (args, "--top", rows / 2);
		shot = stringArg (args, "--shot");
		display = new Display ();
		shell = new Shell (display);
		shell.setText ("Initiative 31 viewport: " + widget + ", " + String.format ("%,d", rows) + " rows");
		shell.setLayout (new GridLayout (1, false));
		info = new Label (shell, SWT.WRAP);
		info.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		shell.setSize (520, 420);
	}

	/** Gives the widget the rest of the window. */
	void fill (Control control) {
		control.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	}

	/** Shows the line that describes the run. */
	void describe (String widget, long millis, String details) {
		String mode = System.getProperty ("swt.widget.mode." + widget, System.getProperty ("swt.widget.mode", "viewport (default)"));
		info.setText (widget + ": " + String.format ("%,d", rows) + " rows, mode " + mode + ", filled in " + millis + " ms, " + details
			+ "\nlook and feel " + UIManager.getLookAndFeel ().getName ());
		shell.layout (true, true);
	}

	/** Opens the window; writes the screenshot and exits when one was asked for, otherwise runs until closed. */
	void run (Runnable afterOpen) {
		shell.open ();
		pump ();
		afterOpen.run ();
		pump ();
		if (shot != null) {
			screenshot (new File (shot));
		} else {
			while (!shell.isDisposed ()) {
				if (!display.readAndDispatch ()) display.sleep ();
			}
		}
		display.dispose ();
	}

	void pump () {
		for (int round = 0; round < 5; round++) {
			for (int i = 0; i < 100_000 && display.readAndDispatch (); i++) {
				// drain the queue
			}
			try {
				Thread.sleep (40);
			} catch (InterruptedException e) {
				Thread.currentThread ().interrupt ();
			}
		}
	}

	private void screenshot (File file) {
		Component component = (Component) shell.handle;
		Window window = component instanceof Window ? (Window) component : SwingUtilities.getWindowAncestor (component);
		try {
			SwingUtilities.invokeAndWait (() -> {
				BufferedImage image = new BufferedImage (window.getWidth (), window.getHeight (), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics ();
				window.printAll (g);
				g.dispose ();
				try {
					ImageIO.write (image, "png", file);
				} catch (IOException e) {
					throw new UncheckedIOException (e);
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException ("screenshot failed", e);
		}
	}

	private static int intArg (String [] args, String name, int fallback) {
		String value = stringArg (args, name);
		return value == null ? fallback : Integer.parseInt (value);
	}

	private static String stringArg (String [] args, String name) {
		for (int i = 0; i + 1 < args.length; i++) {
			if (args [i].equals (name)) return args [i + 1];
		}
		return null;
	}
}
