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

/*
 * A virtual Table of 1,000,000 rows whose labels repeat like an Explorer
 * window (name, type, size, date). With the label pool on (default) the model
 * keeps one int label id per cell in M3LabelLanes; with -Dm3.labelpool=off it
 * keeps a String per cell. The header line shows the retained heap of the
 * model, and how many GC.stringExtent calls the widest-column computation of
 * every column needed: once per distinct label with the pool, once per row
 * without it.
 *
 * -Dm3.snapshot=<dir> saves a PNG of the shell to <dir> and exits (hidden-desktop
 * evidence); logging goes through SwtLog (java.util.logging).
 */
import java.util.*;
import java.util.function.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.m3.collections.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class M3LabelPoolTableSnippet {
	static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.snippets.M3LabelPoolTableSnippet");
	static final int ROWS = 1_000_000, COLUMNS = 4;
	static final int [] DISTINCT = {169_618, 693, 1_301, 2_836};
	static final String [] SUFFIX = {".java", ".tsv", ".json", ".md", ".class", ".png"};

	/** The label of a cell as an Explorer-like provider would format it (fresh String each call). */
	static String label (int column, int pick) {
		return switch (column) {
			case 0 -> "name-" + pick + SUFFIX [pick % SUFFIX.length];
			case 1 -> pick % 7 == 0 ? "File folder" : pick % 11 == 0 ? "JAVA File" : "T" + pick + " File";
			case 2 -> (pick + 1) + " KB";
			default -> "2026-10-" + (1 + pick % 28) + " " + (pick % 24) + ":" + String.format ("%02d", pick % 60);
		};
	}

	/** The one model: label ids per cell (pool on) or Strings per cell (pool off). */
	interface Model {
		String text (int row, int column);
		int widest (int column, ToIntFunction<String> measure);
		long calls ();
	}

	static Model pooled (Random random) {
		M3LabelPool pool = new M3LabelPool (4096);
		M3TextExtentCache extents = new M3TextExtentCache (1 << 18);
		M3LabelLanes lanes = new M3LabelLanes (COLUMNS, pool, extents);
		lanes.setRowCount (ROWS);
		for (int row = 0; row < ROWS; row++) {
			for (int c = 0; c < COLUMNS; c++) lanes.setText (row, c, label (c, random.nextInt (DISTINCT [c])));
		}
		return new Model () {
			public String text (int row, int column) { return lanes.text (row, column); }
			public int widest (int column, ToIntFunction<String> measure) {
				return lanes.widest (column, 0, ROWS, 0, (id, font) -> M3TextExtentCache.pack (measure.applyAsInt (pool.text (id)), 0));
			}
			public long calls () { return extents.misses (); }
		};
	}

	static Model plain (Random random) {
		String [] [] cells = new String [COLUMNS] [ROWS];
		for (int row = 0; row < ROWS; row++) {
			for (int c = 0; c < COLUMNS; c++) cells [c] [row] = label (c, random.nextInt (DISTINCT [c]));
		}
		long [] calls = new long [1];
		return new Model () {
			public String text (int row, int column) { return cells [column] [row]; }
			public int widest (int column, ToIntFunction<String> measure) {
				int widest = 0;
				for (int row = 0; row < ROWS; row++) {
					calls [0]++;
					widest = Math.max (widest, measure.applyAsInt (cells [column] [row]));
				}
				return widest;
			}
			public long calls () { return calls [0]; }
		};
	}

	static long retainedKb () {
		for (int i = 0; i < 3; i++) System.gc ();
		Runtime runtime = Runtime.getRuntime ();
		return (runtime.totalMemory () - runtime.freeMemory ()) / 1024;
	}

	public static void main (String [] args) {
		boolean pool = !"off".equals (System.getProperty ("m3.labelpool"));
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("M3LabelPool: 1,000,000 rows, pool " + (pool ? "on" : "off"));
		shell.setLayout (new GridLayout (1, false));
		Label status = new Label (shell, SWT.WRAP);
		status.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		table.setHeaderVisible (true);
		for (String name : new String [] {"Name", "Type", "Size", "Date modified"}) new TableColumn (table, SWT.NONE).setText (name);
		long before = retainedKb ();
		long start = System.nanoTime ();
		Model model = pool ? pooled (new Random (42)) : plain (new Random (42));
		long fillMs = (System.nanoTime () - start) / 1_000_000;
		long retained = retainedKb () - before;
		table.addListener (SWT.SetData, event -> {
			TableItem item = (TableItem) event.item;
			int row = table.indexOf (item);
			for (int c = 0; c < COLUMNS; c++) item.setText (c, model.text (row, c));
		});
		table.setItemCount (ROWS);
		GC gc = new GC (table);
		start = System.nanoTime ();
		int [] widths = new int [COLUMNS];
		for (int c = 0; c < COLUMNS; c++) widths [c] = model.widest (c, text -> gc.stringExtent (text).x) + 24;
		long widestMs = (System.nanoTime () - start) / 1_000_000;
		gc.dispose ();
		for (int c = 0; c < COLUMNS; c++) table.getColumn (c).setWidth (widths [c]);
		String summary = String.format ("pool %s: %,d rows x %d columns; model retained %,d KB (filled in %,d ms); widest columns: %,d stringExtent calls in %,d ms", pool ? "ON" : "OFF", ROWS, COLUMNS, retained, fillMs, model.calls (), widestMs);
		status.setText (summary);
		LOG.info (() -> summary);
		shell.setSize (900, 420);
		shell.open ();
		snapshotThenExit (display, shell, pool ? "table-pool-on" : "table-pool-off");
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	/** With -Dm3.snapshot=<dir>: saves <dir>/<name>.png after the first paints and closes the shell. */
	static void snapshotThenExit (Display display, Shell shell, String name) {
		String dir = System.getProperty ("m3.snapshot");
		if (dir == null) return;
		display.timerExec (1500, () -> {
			Image image = new Image (display, shell.getClientArea ().width, shell.getClientArea ().height);
			GC gc = new GC (image);
			shell.print (gc);
			gc.dispose ();
			ImageLoader loader = new ImageLoader ();
			loader.data = new ImageData [] {image.getImageData ()};
			String file = dir + "/" + name + ".png";
			loader.save (file, SWT.IMAGE_PNG);
			image.dispose ();
			LOG.info (() -> "saved " + file);
			shell.close ();
		});
	}
}
