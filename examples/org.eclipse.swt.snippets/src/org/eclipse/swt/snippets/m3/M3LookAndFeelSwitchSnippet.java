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
 * M3 look and feel: switch the look and feel of drawn widgets at runtime (native, swing, initiative31, light, dark),
 * like Swing's UIManager.setLookAndFeel, and change the accent colour (docs/m3/look-and-feel/EXAMPLES.md).
 *
 * The rows on the left are painted with the colours of an example UI delegate (M3ComponentUI); the Table on the
 * right is a native widget and keeps native pixels. Run with the M3 SWT jar on the class path:
 *   java -cp swt.jar;snippets M3LookAndFeelSwitchSnippet [--screenshots <directory>]
 * With --screenshots, the snippet saves one PNG per look and feel (and one with another accent) and exits.
 */
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

@SuppressWarnings("restriction")
public class M3LookAndFeelSwitchSnippet {
	static final String [] LOOKS = {"native", "swing", "initiative31", "light", "dark"};
	static final int [] ACCENTS = {0xFF2F6FD0, 0xFFC0392B, 0xFF16A085, 0xFF8E44AD};

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3 look and feel: runtime switching");
		shell.setLayout (new GridLayout (3, false));
		Combo choice = new Combo (shell, SWT.READ_ONLY);
		choice.setItems (LOOKS);
		choice.select (0);
		Button accent = new Button (shell, SWT.PUSH);
		accent.setText ("Next accent");
		Label note = new Label (shell, SWT.WRAP);
		note.setText ("Left: rows drawn by the look and feel. Right: a native Table (native pixels in every look and feel).");
		note.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		Canvas rows = rows (shell);
		rows.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
		Table table = new Table (shell, SWT.BORDER | SWT.FULL_SELECTION);
		for (int i = 0; i < 8; i++) new TableItem (table, SWT.NONE).setText ("Native row " + i);
		table.select (2);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		M3LookAndFeel.setLookAndFeel (LOOKS [0]);
		choice.addListener (SWT.Selection, e -> M3LookAndFeel.setLookAndFeel (choice.getText ()));
		int [] next = {0};
		accent.addListener (SWT.Selection, e -> M3LookAndFeel.setAccent (ACCENTS [++next [0] % ACCENTS.length]));
		shell.setSize (640, 320);
		shell.open ();
		int screenshots = Arrays.asList (args).indexOf ("--screenshots");
		if (screenshots >= 0 && screenshots + 1 < args.length) {
			screenshots (display, shell, args [screenshots + 1]);
		} else {
			while (!shell.isDisposed ()) {
				if (!display.readAndDispatch ()) display.sleep ();
			}
		}
		display.dispose ();
	}

	/*
	 * An example UI delegate (M3ComponentUI) that keeps the row colours of its look and feel; the drawn Tree, Table and
	 * List of the drawn modes use org.eclipse.swt.internal.render.M3DrawnRowUI the same way. A switch that changes one of its
	 * paint keys installs it again and repaints the canvas; a change of its metric key relays it out.
	 */
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

	/* a canvas whose rows its delegate's colours paint: row 2 selected, odd rows striped */
	static Canvas rows (Composite parent) {
		Canvas canvas = new Canvas (parent, SWT.DOUBLE_BUFFERED | SWT.BORDER);
		M3LookAndFeel.installUI (canvas, Rows.class, new Rows ());
		canvas.addListener (SWT.Paint, e -> {
			Rows ui = (Rows) M3LookAndFeel.getUI (canvas);
			M3UIResources resources = M3UIResources.of (canvas.getDisplay ());
			int width = canvas.getClientArea ().width;
			for (int i = 0, y = 0; y < canvas.getClientArea ().height; i++, y += ui.rowHeight) {
				boolean selected = i == 2;
				e.gc.setBackground (resources.color (selected ? ui.selection : i % 2 == 1 ? ui.stripe : ui.background));
				e.gc.fillRectangle (0, y, width, ui.rowHeight);
				e.gc.setForeground (resources.color (selected ? ui.selectionText : ui.foreground));
				e.gc.drawText ("Drawn row " + i, 6, y + 2, true);
			}
		});
		return canvas;
	}

	/* one PNG per look and feel, then one with another accent */
	static void screenshots (Display display, Shell shell, String directory) {
		new java.io.File (directory).mkdirs ();
		for (String look : LOOKS) {
			for (Control child : shell.getChildren ()) {
				if (child instanceof Combo combo) combo.setText (look);
			}
			M3LookAndFeel.setLookAndFeel (look);
			save (display, shell, directory + "/switch-" + look + ".png");
		}
		M3LookAndFeel.setLookAndFeel ("light");
		M3LookAndFeel.setAccent (ACCENTS [1]);
		save (display, shell, directory + "/switch-light-accent.png");
	}

	static void save (Display display, Shell shell, String file) {
		while (display.readAndDispatch ()) {
			// let the update paint
		}
		/* the client area with every child printed at its place (Control.print of each child) */
		Rectangle client = shell.getClientArea ();
		Image image = new Image (display, client.width, client.height);
		GC gc = new GC (image);
		gc.setBackground (shell.getBackground ());
		gc.fillRectangle (0, 0, client.width, client.height);
		for (Control child : shell.getChildren ()) {
			Rectangle bounds = child.getBounds ();
			if (bounds.width <= 0 || bounds.height <= 0) continue;
			Image part = new Image (display, bounds.width, bounds.height);
			GC partGC = new GC (part);
			child.print (partGC);
			partGC.dispose ();
			gc.drawImage (part, bounds.x, bounds.y);
			part.dispose ();
		}
		gc.dispose ();
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		loader.save (file, SWT.IMAGE_PNG);
		image.dispose ();
		java.util.logging.Logger.getLogger (M3LookAndFeelSwitchSnippet.class.getName ()).info ("saved " + file);
	}
}
