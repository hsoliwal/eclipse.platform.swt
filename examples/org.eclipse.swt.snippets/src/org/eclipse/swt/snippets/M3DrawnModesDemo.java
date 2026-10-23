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
package org.eclipse.swt.snippets;

import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * The drawn modes of a Table (docs/m3/RENDER_MODES.md): a virtual Table of a million rows painted by the drawn plane,
 * the frame-time and input-latency overlay of the interaction model, and run-time look and feel switching.
 * <p>
 * Run with the mode and the overlay, for example:
 * <pre>
 * java -Dswt.widget.mode=gpu -Dswt.drawn.overlay=true -cp swt.jar;swt-render-m3.jar;skija-shared.jar;skija-windows-x64.jar;types.jar
 *      org.eclipse.swt.snippets.M3DrawnModesDemo
 * </pre>
 * <code>custom</code> draws with GDI, <code>m3</code> with the M3 renderer on the CPU, <code>gpu</code> with the M3
 * renderer on the graphics hardware. The buttons switch the look and feel at run time
 * ({@link M3LookAndFeel#setLookAndFeel}): the rows' delegate is installed again and the table repaints. Scroll fast
 * and type to see the overlay's frame time and input-to-pixel latency.
 * </p>
 */
public class M3DrawnModesDemo {
	private static final Logger LOG = Logger.getLogger (M3DrawnModesDemo.class.getName ());
	static final int ROWS = 1_000_000;
	static final String [] LOOK_AND_FEELS = {"native", "swing", "initiative31", "flat", "flatdark"};

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("Drawn modes: " + System.getProperty ("swt.widget.mode", "viewport") + ", look and feel " + M3LookAndFeel.getLookAndFeel ());
		shell.setLayout (new GridLayout (LOOK_AND_FEELS.length, true));
		Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, LOOK_AND_FEELS.length, 1));
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		for (String title : new String [] {"Row", "Value", "A longer column whose text ends with an ellipsis"}) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText (title);
			column.setWidth (140);
		}
		table.addListener (SWT.SetData, event -> {
			TableItem item = (TableItem) event.item;
			int index = event.index;
			item.setText (new String [] {"row " + index, Integer.toString (index * 7 % 1000), "a longer cell text " + index});
			if (index % 10 == 0) item.setForeground (1, display.getSystemColor (SWT.COLOR_DARK_RED));
		});
		table.setItemCount (ROWS);
		for (String name : LOOK_AND_FEELS) {
			Button button = new Button (shell, SWT.PUSH);
			button.setText (name);
			button.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
			button.addListener (SWT.Selection, event -> {
				M3LookAndFeel.setLookAndFeel (name);
				shell.setText ("Drawn modes: " + System.getProperty ("swt.widget.mode", "viewport") + ", look and feel " + name);
				LOG.info (() -> "look and feel " + name);
			});
		}
		shell.setSize (560, 520);
		shell.open ();
		/* -Dm3.demo.capture=<png>: scroll to the middle, select, switch to flatdark, save the table's pixels and exit */
		String capture = System.getProperty ("m3.demo.capture");
		if (capture != null) display.timerExec (1500, () -> {
			table.setTopIndex (ROWS / 2);
			table.setSelection (ROWS / 2 + 3);
			display.timerExec (800, () -> {
				M3LookAndFeel.setLookAndFeel ("flatdark");
				display.timerExec (800, () -> {
					save (table, capture);
					shell.dispose ();
				});
			});
		});
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	/* the table's pixels through Control.print (WM_PRINT: the drawn plane paints them) as a PNG */
	static void save (Table table, String file) {
		Point size = table.getSize ();
		Image image = new Image (table.getDisplay (), size.x, size.y);
		GC gc = new GC (image);
		table.print (gc);
		gc.dispose ();
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		loader.save (file, SWT.IMAGE_PNG);
		image.dispose ();
		LOG.info (() -> "saved " + file);
	}
}
