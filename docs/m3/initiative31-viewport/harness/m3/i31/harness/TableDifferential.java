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
package m3.i31.harness;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Recorded differential of a {@link Table} (even seeds) and a {@link List} (odd seeds), with the columns of
 * {@link TreeDifferential}: <code>seed, step, operation, answer, item count, selection, events, scroll, SetData
 * calls of the step</code>. Tables of even seeds divisible by 4 are virtual.
 * <p>
 * Usage: <code>TableDifferential &lt;out-file&gt; &lt;first-seed&gt; &lt;seeds&gt; &lt;steps&gt;</code>
 * </p>
 */
public final class TableDifferential {
	private static final Watchdog WATCHDOG = Watchdog.start (120_000);
	private static final Logger LOG = Logger.getLogger ("m3.i31.harness.TableDifferential");
	private final Display display;
	private final StringBuilder events = new StringBuilder ();
	private Table table;
	private List list;
	private int setData;

	private TableDifferential (Display display) {
		this.display = display;
	}

	public static void main (String [] args) throws IOException {
		java.util.List<String> out = new ArrayList<> ();
		int first = Integer.parseInt (args [1]);
		int seeds = Integer.parseInt (args [2]);
		int steps = Integer.parseInt (args [3]);
		Display display = new Display ();
		try {
			TableDifferential differential = new TableDifferential (display);
			for (int seed = first; seed < first + seeds; seed++) {
				differential.run (seed, steps, out);
			}
		} finally {
			display.dispose ();
		}
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
		// the record is complete; do not wait for Swing threads that a disposed Initiative 31 display can leave behind
		System.exit (0);
	}

	private void run (int seed, int steps, java.util.List<String> out) {
		SplittableRandom random = new SplittableRandom (seed);
		Shell shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (320, 240);
		setData = 0;
		events.setLength (0);
		boolean isTable = seed % 2 == 0;
		boolean virtual = seed % 4 == 0;
		if (isTable) {
			list = null;
			table = new Table (shell, SWT.BORDER | SWT.MULTI | (virtual ? SWT.VIRTUAL : 0));
			table.addListener (SWT.SetData, e -> {
				setData++;
				((TableItem) e.item).setText ("r" + e.index);
			});
			table.addListener (SWT.Selection, e -> events.append ("sel "));
			table.setItemCount (50 + random.nextInt (400));
		} else {
			table = null;
			list = new List (shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
			list.addListener (SWT.Selection, e -> events.append ("sel "));
			String [] items = new String [50 + random.nextInt (400)];
			Arrays.setAll (items, i -> "r" + i);
			list.setItems (items);
		}
		shell.open ();
		pump (true);
		out.add (record (seed, -1, isTable ? (virtual ? "virtual" : "table") : "list", ""));
		for (int step = 0; step < steps; step++) {
			WATCHDOG.tick (seed + ":" + step);
			String [] done = step (random);
			pump (step % 25 == 24);
			out.add (record (seed, step, done [0], done [1]));
		}
		shell.dispose ();
		pump (false);
	}

	private int count () {
		return table != null ? table.getItemCount () : list.getItemCount ();
	}

	private String [] step (SplittableRandom random) {
		int count = count ();
		int op = count == 0 ? 0 : random.nextInt (14);
		int i = count == 0 ? 0 : random.nextInt (count);
		try {
			return table != null ? table (op, i, count, random) : list (op, i, count, random);
		} catch (RuntimeException e) {
			// the record keeps the exception class; the log keeps where it was thrown
			LOG.log (Level.WARNING, "op" + op + "(" + i + ") of " + count + " rows threw", e);
			return new String [] {"op" + op + "(" + i + ")", "EX:" + e.getClass ().getSimpleName ()};
		}
	}

	private String [] table (int op, int i, int count, SplittableRandom random) {
		switch (op) {
			case 0: {
				int next = Math.max (0, count + random.nextInt (120) - 40);
				table.setItemCount (next);
				// Initiative 31 resets the selection here in the original mode (UPSTREAM_DEFECTS I31-B3); start both modes again from none
				table.deselectAll ();
				return new String [] {"setItemCount(" + next + ")", ""};
			}
			case 1: return new String [] {"text(" + i + ")", table.getItem (i).getText ()};
			case 2: table.setSelection (i); return new String [] {"setSelection(" + i + ")", Arrays.toString (table.getSelectionIndices ())};
			case 3: table.select (i); return new String [] {"select(" + i + ")", Arrays.toString (table.getSelectionIndices ())};
			case 4: table.setTopIndex (i); return new String [] {"setTopIndex(" + i + ")", ""};
			case 5: table.showItem (table.getItem (i)); return new String [] {"showItem(" + i + ")", ""};
			case 6: table.showSelection (); return new String [] {"showSelection", ""};
			case 7: return new String [] {"indexOf(" + i + ")", String.valueOf (table.indexOf (table.getItem (i)))};
			case 8: table.clear (i); return new String [] {"clear(" + i + ")", table.getItem (i).getText ()};
			case 9: table.clearAll (); return new String [] {"clearAll", ""};
			case 10: table.remove (i); return new String [] {"remove(" + i + ")", ""};
			case 11: table.deselectAll (); return new String [] {"deselectAll", ""};
			case 12: return new String [] {"items", String.valueOf (table.getItems ().length)};
			default:
				int pick = random.nextInt (6);
				if (pick == 1) {
					int end = Math.min (count - 1, i + random.nextInt (60));
					table.remove (i, end);
					return new String [] {"remove(" + i + "," + end + ")", ""};
				}
				if (pick != 0) return new String [] {"noop", ""};
				table.removeAll ();
				return new String [] {"removeAll", ""};
		}
	}

	private String [] list (int op, int i, int count, SplittableRandom random) {
		switch (op) {
			case 0: {
				int added = 1 + random.nextInt (40);
				for (int k = 0; k < added; k++) list.add ("a" + count + "." + k, random.nextInt (count + k + 1));
				return new String [] {"add(" + added + ")", ""};
			}
			case 1: return new String [] {"item(" + i + ")", list.getItem (i)};
			case 2: list.setSelection (i); return new String [] {"setSelection(" + i + ")", Arrays.toString (list.getSelectionIndices ())};
			case 3: list.select (i); return new String [] {"select(" + i + ")", Arrays.toString (list.getSelectionIndices ())};
			case 4: list.setTopIndex (i); return new String [] {"setTopIndex(" + i + ")", ""};
			case 5: list.deselect (i); return new String [] {"deselect(" + i + ")", Arrays.toString (list.getSelectionIndices ())};
			case 6: list.showSelection (); return new String [] {"showSelection", ""};
			case 7: return new String [] {"indexOf(" + i + ")", String.valueOf (list.indexOf (list.getItem (i)))};
			case 8: list.setItem (i, "s" + i); return new String [] {"setItem(" + i + ")", list.getItem (i)};
			case 9: list.remove (i); return new String [] {"remove(" + i + ")", ""};
			case 10: list.deselectAll (); return new String [] {"deselectAll", ""};
			case 11: return new String [] {"items", String.valueOf (list.getItems ().length)};
			case 12: return new String [] {"selectionCount", String.valueOf (list.getSelectionCount ())};
			default:
				int pick = random.nextInt (6);
				if (pick == 1) {
					int end = Math.min (count - 1, i + random.nextInt (60));
					list.remove (i, end);
					return new String [] {"remove(" + i + "," + end + ")", ""};
				}
				if (pick == 2) {
					String [] items = new String [random.nextInt (400)];
					Arrays.setAll (items, k -> "n" + k);
					list.setItems (items);
					return new String [] {"setItems(" + items.length + ")", ""};
				}
				if (pick != 0) return new String [] {"noop", ""};
				list.removeAll ();
				return new String [] {"removeAll", ""};
		}
	}

	private String record (int seed, int step, String op, String answer) {
		String recorded = events.toString ().trim ();
		events.setLength (0);
		int calls = setData;
		setData = 0;
		int [] selection = table != null ? table.getSelectionIndices () : list.getSelectionIndices ();
		Arrays.sort (selection);
		int top = table != null ? table.getTopIndex () : list.getTopIndex ();
		return seed + "\t" + step + "\t" + op + "\t" + answer + "\t" + count () + "\t" + Arrays.toString (selection) + "\t" + recorded + "\t" + top + "\t" + calls;
	}

	private void pump (boolean settle) {
		for (int round = 0; round < (settle ? 3 : 1); round++) {
			for (int i = 0; i < 100_000 && display.readAndDispatch (); i++) {
				// drain the queue
			}
			if (!settle) return;
			try {
				Thread.sleep (20);
			} catch (InterruptedException e) {
				Thread.currentThread ().interrupt ();
			}
		}
	}
}
