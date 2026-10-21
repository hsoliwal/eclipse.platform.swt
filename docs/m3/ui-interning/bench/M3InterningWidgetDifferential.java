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
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.m3.collections.*;
import org.eclipse.swt.widgets.*;

/**
 * Widget answers with the label pool off and on must be identical.
 *
 * <p>Two Tables of the same size sit in one shell on a hidden desktop. Table A
 * gets a fresh String per cell (pool off); table B gets the pooled String
 * facade of each cell's label id from {@link M3LabelLanes} (pool on). A seeded
 * script edits cells, inserts and removes rows, changes the font and packs
 * columns, and after every step records from both tables: item text, item
 * bounds per column, column widths after pack, item height, and the width of
 * every visible cell, measured directly with {@code GC.stringExtent} for A and
 * through {@link M3TextExtentCache} (keyed by label id and font id) for B. Any
 * difference is a mismatch, written with its seed and step.</p>
 *
 * <pre>java -cp swt.jar;m3collections M3InterningWidgetDifferential out.tsv [seeds] [steps]</pre>
 */
public final class M3InterningWidgetDifferential {
	static final Logger LOG = Logger.getLogger ("org.eclipse.swt.m3.M3InterningWidgetDifferential");
	static final String [] TYPES = {"JAVA File", "File folder", "TSV File", "अनु File", "PNG File", ""};
	static final int ROWS = 400, COLUMNS = 3;

	public static void main (String [] args) throws IOException {
		java.nio.file.Path out = java.nio.file.Path.of (args.length > 0 ? args [0] : "DIFFERENTIAL.tsv");
		int seeds = args.length > 1 ? Integer.parseInt (args [1]) : 10, steps = args.length > 2 ? Integer.parseInt (args [2]) : 200;
		Display display = new Display ();
		long checks = 0, mismatches = 0;
		try (PrintWriter tsv = new PrintWriter (Files.newBufferedWriter (out, StandardCharsets.UTF_8))) {
			tsv.print ("seed\tstep\toperation\twhat\tpool_off\tpool_on\n");
			for (int seed = 1; seed <= seeds; seed++) {
				long [] result = run (display, seed, steps, tsv);
				checks += result [0];
				mismatches += result [1];
			}
			String summary = String.format ("summary\t%d seeds x %d steps\tchecks %d\tmismatches %d\t-\t-", seeds, steps, checks, mismatches);
			tsv.print (summary + "\n");
			LOG.info (() -> summary);
		}
		display.dispose ();
	}

	static long [] run (Display display, int seed, int steps, PrintWriter tsv) {
		Random random = new Random (seed);
		Shell shell = new Shell (display);
		shell.setSize (1000, 600);
		Table off = table (shell, 0), on = table (shell, 500);
		M3LabelPool pool = new M3LabelPool (seed % 3 * 8);
		M3TextExtentCache extents = new M3TextExtentCache (64);
		M3LabelLanes lanes = new M3LabelLanes (COLUMNS, pool, extents, seed % 2 == 0);
		Font [] fonts = {display.getSystemFont (), new Font (display, "Segoe UI", 11, SWT.BOLD), new Font (display, "Consolas", 9, SWT.NORMAL)};
		int [] font = {0};
		for (int r = 0; r < ROWS; r++) insert (off, on, lanes, r, random);
		shell.open ();
		long [] counts = new long [2];
		GC gcOff = new GC (off), gcOn = new GC (on);
		for (int step = 0; step < steps; step++) {
			String operation = step (off, on, lanes, random, fonts, font, gcOff, gcOn, extents);
			drain (display);
			compare (seed, step, operation, off, on, lanes, font [0], gcOff, gcOn, fonts, counts, tsv);
		}
		gcOff.dispose ();
		gcOn.dispose ();
		shell.dispose ();
		fonts [1].dispose ();
		fonts [2].dispose ();
		return counts;
	}

	static Table table (Shell shell, int x) {
		Table table = new Table (shell, SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds (x, 0, 480, 560);
		table.setHeaderVisible (true);
		for (int c = 0; c < COLUMNS; c++) new TableColumn (table, SWT.NONE).setText ("column " + c);
		return table;
	}

	static String text (int column, Random random) {
		return switch (column) {
			case 0 -> "name-" + random.nextInt (60) + (random.nextBoolean () ? ".java" : ".tsv");
			case 1 -> TYPES [random.nextInt (TYPES.length)];
			default -> (1 + random.nextInt (40)) + " KB";
		};
	}

	static void insert (Table off, Table on, M3LabelLanes lanes, int row, Random random) {
		TableItem a = new TableItem (off, SWT.NONE, row), b = new TableItem (on, SWT.NONE, row);
		lanes.insertRows (row, 1);
		for (int c = 0; c < COLUMNS; c++) {
			String text = text (c, random);
			a.setText (c, new String (text));
			lanes.setText (row, c, text);
			b.setText (c, lanes.text (row, c));
		}
	}

	static String step (Table off, Table on, M3LabelLanes lanes, Random random, Font [] fonts, int [] font, GC gcOff, GC gcOn, M3TextExtentCache extents) {
		int rows = off.getItemCount (), op = random.nextInt (10);
		if (op < 5 && rows > 0) {
			int row = random.nextInt (rows), column = random.nextInt (COLUMNS);
			String text = text (column, random);
			off.getItem (row).setText (column, new String (text));
			lanes.setText (row, column, text);
			on.getItem (row).setText (column, lanes.text (row, column));
			return "setText " + row + " " + column;
		}
		if (op == 5) {
			int row = random.nextInt (rows + 1);
			insert (off, on, lanes, row, random);
			return "insert " + row;
		}
		if (op == 6 && rows > 1) {
			int row = random.nextInt (rows);
			off.remove (row);
			on.remove (row);
			lanes.removeRows (row, 1);
			return "remove " + row;
		}
		if (op == 7) {
			font [0] = random.nextInt (fonts.length);
			off.setFont (fonts [font [0]]);
			on.setFont (fonts [font [0]]);
			gcOff.setFont (fonts [font [0]]);
			gcOn.setFont (fonts [font [0]]);
			return "font " + font [0];
		}
		for (int c = 0; c < COLUMNS; c++) {
			off.getColumn (c).pack ();
			on.getColumn (c).pack ();
		}
		return "pack";
	}

	static void compare (int seed, int step, String operation, Table off, Table on, M3LabelLanes lanes, int font, GC gcOff, GC gcOn, Font [] fonts, long [] counts, PrintWriter tsv) {
		check (seed, step, operation, "item count", off.getItemCount (), on.getItemCount (), counts, tsv);
		check (seed, step, operation, "item height", off.getItemHeight (), on.getItemHeight (), counts, tsv);
		for (int c = 0; c < COLUMNS; c++) check (seed, step, operation, "column " + c + " width", off.getColumn (c).getWidth (), on.getColumn (c).getWidth (), counts, tsv);
		M3TextExtentCache.Measurer measurer = (id, f) -> {
			Point extent = gcOn.stringExtent (lanes.text (id));
			return M3TextExtentCache.pack (extent.x, extent.y);
		};
		int top = off.getTopIndex (), bottom = Math.min (off.getItemCount (), top + 30);
		for (int r = top; r < bottom; r++) {
			TableItem a = off.getItem (r), b = on.getItem (r);
			for (int c = 0; c < COLUMNS; c++) {
				check (seed, step, operation, "text " + r + "," + c, a.getText (c), b.getText (c), counts, tsv);
				check (seed, step, operation, "bounds " + r + "," + c, a.getBounds (c), b.getBounds (c), counts, tsv);
				Point direct = gcOff.stringExtent (a.getText (c));
				long cached = lanes.extent (r, c, font, measurer);
				check (seed, step, operation, "extent " + r + "," + c, direct.x + "x" + direct.y, M3TextExtentCache.width (cached) + "x" + M3TextExtentCache.height (cached), counts, tsv);
			}
		}
	}

	static void check (int seed, int step, String operation, String what, Object a, Object b, long [] counts, PrintWriter tsv) {
		counts [0]++;
		if (Objects.equals (a, b)) return;
		counts [1]++;
		tsv.print (seed + "\t" + step + "\t" + operation + "\t" + what + "\t" + a + "\t" + b + "\n");
	}

	static void drain (Display display) {
		while (display.readAndDispatch ()) {
			/* dispatch pending paints */
		}
	}
}
