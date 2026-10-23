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

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;

/**
 * The repaint cost of a run-time theme selection for one widget class (optimization pass, leaf "switch repaint"): a
 * Tree, a Table and a List of 10^4 rows in the JVM's drawn mode; <code>Themes.select ("Table", ...)</code> alternates
 * between two themes <code>switches</code> times, each followed by a drain of the paints. Prints one TSV row: the frames
 * each plane painted and the wall time of the switches. Only the Table's theme changes, so only the Table needs to
 * repaint. Uses API present before the change (frame counts by reflection), so it runs against the build before and
 * after. Usage: <code>RenderThemeRepaintBenchmark [switches]</code>.
 */
public final class RenderThemeRepaintBenchmark {
	public static void main (String [] args) {
		int switches = args.length > 0 ? Integer.parseInt (args [0]) : 20;
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 900, 420);
		Tree tree = new Tree (shell, SWT.BORDER);
		tree.setBounds (5, 5, 290, 380);
		Table table = new Table (shell, SWT.BORDER);
		table.setBounds (300, 5, 290, 380);
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (595, 5, 290, 380);
		String [] items = new String [10_000];
		Arrays.setAll (items, i -> "row " + i);
		for (String item : items) {
			new TreeItem (tree, SWT.NONE).setText (item);
			new TableItem (table, SWT.NONE).setText (item);
		}
		list.setItems (items);
		shell.open ();
		ViewportDifferential.settle (display, 400);
		Control [] controls = {tree, table, list};
		FrameLog [] logs = new FrameLog [3];
		long [] before = new long [3];
		for (int i = 0; i < 3; i++) {
			logs [i] = Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.log (controls [i]);
			before [i] = logs [i] == null ? 0 : logs [i].frames ();
		}
		long start = System.nanoTime ();
		for (int s = 0; s < switches; s++) {
			Themes.select ("Table", s % 2 == 0 ? Theme.SWING : Theme.INITIATIVE31);
			ViewportDifferential.settle (display, 50);
			for (Control control : controls) control.update ();
		}
		long nanos = System.nanoTime () - start;
		StringBuilder row = new StringBuilder ("switches\t" + switches);
		for (int i = 0; i < 3; i++) row.append ('\t').append (controls [i].getClass ().getSimpleName ()).append ("_frames\t").append (logs [i] == null ? -1 : logs [i].frames () - before [i]);
		row.append (String.format (Locale.ROOT, "\twall_ms\t%.1f", nanos / 1e6));
		System.out.println (row);
		shell.dispose ();
		display.dispose ();
	}
}
