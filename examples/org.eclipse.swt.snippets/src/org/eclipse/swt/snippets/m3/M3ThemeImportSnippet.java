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
package org.eclipse.swt.snippets.m3;

/*
 * M3 look and feel: import an IntelliJ .theme.json and a FlatLaf .properties theme and apply them to drawn widgets
 * (docs/m3/look-and-feel/EXAMPLES.md). Without arguments the snippet imports the two sample themes below; with a file
 * argument it imports that file instead (a .json file as IntelliJ, anything else as FlatLaf), and with --watch it
 * reloads the file whenever it changes (edit it and watch the rows follow).
 *   java -cp swt.jar;snippets M3ThemeImportSnippet [theme-file [--watch]] [--screenshots <directory>]
 */
import java.nio.charset.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

@SuppressWarnings("restriction")
public class M3ThemeImportSnippet {
	static final String INTELLIJ = """
		{ "name": "Sample Night", "dark": true,
		  "colors": { "bg": "#1e2228", "fg": "#d7dae0", "accent": "#e5a50a" },
		  "ui": {
		    "*": { "background": "bg", "foreground": "fg", "selectionBackground": "accent", "selectionForeground": "#1e2228" },
		    "Table": { "stripeColor": "#262b33", "showGrid": true, "gridColor": "#323843" }
		  } }
		""";
	static final String FLATLAF = """
		@baseTheme = light
		@accentColor = #2e7d32
		Table.selectionBackground = lighten(@accentColor, 10%)
		Table.selectionForeground = #ffffff
		Table.stripeBackground = tint(@accentColor, 92%)
		Table.hoverBackground = tint(@accentColor, 80%)
		Table.showGrid = false
		""";

	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		List<String> arguments = Arrays.asList (args);
		List<String> names = new ArrayList<> ();
		M3ThemeWatcher watcher = null;
		if (args.length > 0 && !args [0].startsWith ("--")) {
			Path file = Paths.get (args [0]);
			if (arguments.contains ("--watch")) {
				watcher = M3ThemeWatcher.watch (display, file, "file");
			} else {
				String text = Files.readString (file, StandardCharsets.UTF_8);
				M3ThemeImport.install ("file", file.toString ().endsWith (".json") ? M3ThemeImport.intelliJ (text) : M3ThemeImport.flatLaf (text));
			}
			names.add ("file");
		} else {
			M3ThemeImport.install ("sample-intellij", M3ThemeImport.intelliJ (INTELLIJ));
			M3ThemeImport.install ("sample-flatlaf", M3ThemeImport.flatLaf (FLATLAF));
			names.add ("sample-intellij");
			names.add ("sample-flatlaf");
		}
		Shell shell = new Shell (display);
		shell.setText ("M3 look and feel: imported themes");
		shell.setLayout (new GridLayout (1, false));
		Combo choice = new Combo (shell, SWT.READ_ONLY);
		choice.setItems (names.toArray (new String [0]));
		choice.select (0);
		Canvas rows = M3LookAndFeelSwitchSnippet.rows (shell);
		rows.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		M3LookAndFeel.setLookAndFeel (names.get (0));
		choice.addListener (SWT.Selection, e -> M3LookAndFeel.setLookAndFeel (choice.getText ()));
		shell.setSize (420, 300);
		shell.open ();
		int screenshots = arguments.indexOf ("--screenshots");
		if (screenshots >= 0 && screenshots + 1 < args.length) {
			new java.io.File (args [screenshots + 1]).mkdirs ();
			for (String name : names) {
				M3LookAndFeel.setLookAndFeel (name);
				M3LookAndFeelSwitchSnippet.save (display, shell, args [screenshots + 1] + "/import-" + name + ".png");
			}
		} else {
			while (!shell.isDisposed ()) {
				if (!display.readAndDispatch ()) display.sleep ();
			}
		}
		if (watcher != null) watcher.close ();
		display.dispose ();
	}
}
