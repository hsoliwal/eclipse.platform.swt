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

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Recorded differential of a {@link Tree}: seeded random public-API scripts. Every step records
 * <code>seed, step, operation, answer, item count, selection, events, scroll, SetData calls of the step</code>, one
 * tab-separated line. The same seeds run once per mode (one JVM each); {@link DifferentialCompare} compares
 * the records. Even seeds use <code>SWT.VIRTUAL</code> (items filled by SetData), odd seeds create their
 * items up front.
 * <p>
 * The script reads an item (<code>getText</code>) before it changes it, as a client does, so the moment
 * SetData fills an item does not decide the tree's shape. SetData creates children from the item's path
 * only, so both modes build the same tree whatever order SetData runs in.
 * </p>
 * <p>
 * Usage: <code>TreeDifferential &lt;out-file&gt; &lt;first-seed&gt; &lt;seeds&gt; &lt;steps&gt;</code>
 * </p>
 */
public final class TreeDifferential {
	private static final Watchdog WATCHDOG = Watchdog.start (120_000);
	private final Display display;
	private final StringBuilder events = new StringBuilder ();
	private Tree tree;
	private int setData;

	private TreeDifferential (Display display) {
		this.display = display;
	}

	public static void main (String [] args) throws IOException {
		java.util.List<String> out = new ArrayList<> ();
		int first = Integer.parseInt (args [1]);
		int seeds = Integer.parseInt (args [2]);
		int steps = Integer.parseInt (args [3]);
		Display display = new Display ();
		try {
			TreeDifferential differential = new TreeDifferential (display);
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
		boolean virtual = seed % 2 == 0;
		tree = new Tree (shell, SWT.BORDER | SWT.MULTI | (virtual ? SWT.VIRTUAL : 0));
		setData = 0;
		events.setLength (0);
		listen ();
		if (virtual) {
			tree.setItemCount (20 + random.nextInt (200));
		} else {
			build (random);
		}
		shell.open ();
		pump (true);
		out.add (record (seed, -1, virtual ? "virtual" : "plain", "", random));
		for (int step = 0; step < steps; step++) {
			WATCHDOG.tick (seed + ":" + step);
			String [] done = step (random, virtual);
			pump (step % 25 == 24);
			out.add (record (seed, step, done [0], done [1], random));
		}
		shell.dispose ();
		pump (false);
	}

	private void listen () {
		tree.addListener (SWT.SetData, e -> {
			setData++;
			TreeItem item = (TreeItem) e.item;
			String path = path (item);
			item.setText ("n" + path);
			int hash = Math.floorMod (path.hashCode (), 97);
			if (depth (path) < 3 && hash % 4 == 0) item.setItemCount (1 + hash % 7);
		});
		Listener recorder = e -> events.append (e.type).append (':').append (e.item instanceof TreeItem ? path ((TreeItem) e.item) : "-").append (' ');
		for (int type : new int [] {SWT.Selection, SWT.DefaultSelection, SWT.Expand, SWT.Collapse}) {
			tree.addListener (type, recorder);
		}
	}

	private void build (SplittableRandom random) {
		int roots = 10 + random.nextInt (60);
		for (int i = 0; i < roots; i++) {
			TreeItem item = new TreeItem (tree, SWT.NONE);
			item.setText ("n/" + i);
			int children = random.nextInt (4) == 0 ? 1 + random.nextInt (6) : 0;
			for (int j = 0; j < children; j++) {
				new TreeItem (item, SWT.NONE).setText ("n/" + i + "/" + j);
			}
		}
	}

	private String [] step (SplittableRandom random, boolean virtual) {
		int count = tree.getItemCount ();
		int op = count == 0 ? 0 : random.nextInt (18);
		int i = count == 0 ? 0 : random.nextInt (count);
		try {
			return operate (op, i, count, random, virtual);
		} catch (RuntimeException e) {
			return new String [] {"op" + op + "(" + i + ")", "EX:" + e.getClass ().getSimpleName ()};
		}
	}

	private String [] operate (int op, int i, int count, SplittableRandom random, boolean virtual) {
		switch (op) {
			case 0: {
				int grow = 1 + random.nextInt (60);
				if (virtual) {
					tree.setItemCount (count + grow);
				} else {
					new TreeItem (tree, SWT.NONE, random.nextInt (count + 1)).setText ("x" + count);
				}
				return new String [] {"grow(" + grow + ")", ""};
			}
			case 1:
				return new String [] {"text(" + i + ")", tree.getItem (i).getText ()};
			case 2: {
				TreeItem item = touched (i);
				int children = item.getItemCount ();
				if (children == 0) return new String [] {"child(" + i + ")", "none"};
				return new String [] {"child(" + i + ")", item.getItem (random.nextInt (children)).getText ()};
			}
			case 3: {
				TreeItem item = touched (i);
				boolean expand = random.nextBoolean ();
				item.setExpanded (expand);
				return new String [] {"expand(" + i + "," + expand + ")", String.valueOf (item.getExpanded ())};
			}
			case 4:
				return new String [] {"count(" + i + ")", String.valueOf (touched (i).getItemCount ())};
			case 5: {
				TreeItem item = touched (i);
				if (random.nextBoolean ()) tree.setSelection (item); else tree.select (item);
				return new String [] {"select(" + i + ")", String.valueOf (tree.getSelectionCount ())};
			}
			case 6:
				tree.deselectAll ();
				return new String [] {"deselectAll", String.valueOf (tree.getSelectionCount ())};
			case 7:
				tree.setTopItem (touched (i));
				return new String [] {"setTopItem(" + i + ")", ""};
			case 8:
				tree.showItem (touched (i));
				return new String [] {"showItem(" + i + ")", ""};
			case 9:
				return new String [] {"indexOf(" + i + ")", String.valueOf (tree.indexOf (tree.getItem (i)))};
			case 10:
				tree.clear (i, false);
				return new String [] {"clear(" + i + ")", tree.getItem (i).getText ()};
			case 11:
				tree.clearAll (random.nextBoolean ());
				return new String [] {"clearAll", ""};
			case 12:
				touched (i).dispose ();
				return new String [] {"dispose(" + i + ")", String.valueOf (tree.getItemCount ())};
			case 13:
				return new String [] {"items", String.valueOf (tree.getItems ().length)};
			case 14:
				if (random.nextInt (8) != 0) return new String [] {"noop", ""};
				tree.removeAll ();
				return new String [] {"removeAll", String.valueOf (tree.getItemCount ())};
			case 15: {
				TreeItem item = touched (i);
				int children = item.getItemCount ();
				if (children == 0) return new String [] {"childIndex(" + i + ")", "none"};
				int j = random.nextInt (children);
				return new String [] {"childIndex(" + i + "," + j + ")", String.valueOf (item.indexOf (item.getItem (j)))};
			}
			case 16:
				return new String [] {"getTopItem", ""};
			default: {
				TreeItem item = touched (i);
				String text = item.getText ();
				return new String [] {"parent(" + i + ")", text + "|" + (item.getParentItem () == null) + "|" + (item.getParent () == tree)};
			}
		}
	}

	/** The root item at an index, read once so SetData has filled it. */
	private TreeItem touched (int index) {
		TreeItem item = tree.getItem (index);
		item.getText ();
		return item;
	}

	private String record (int seed, int step, String op, String answer, SplittableRandom random) {
		String recorded = events.toString ().trim ();
		int calls = setData;
		setData = 0;
		events.setLength (0);
		return seed + "\t" + step + "\t" + op + "\t" + answer + "\t" + tree.getItemCount () + "\t" + selection () + "\t" + recorded + "\t" + scroll () + "\t" + calls;
	}

	private String selection () {
		TreeItem [] selected = tree.getSelection ();
		String [] paths = new String [selected.length];
		for (int i = 0; i < selected.length; i++) {
			paths [i] = path (selected [i]);
		}
		Arrays.sort (paths);
		return String.join (",", paths);
	}

	/**
	 * The top item. Hit tests (<code>getItem(Point)</code>) are left out: Initiative 31 answers them on the SWT
	 * thread with the cell renderer that the Swing thread paints with, which races in both modes.
	 */
	private String scroll () {
		TreeItem top = tree.getTopItem ();
		return top == null ? "null" : path (top);
	}

	/** The item's index path from the root, for example <code>/3/0</code>. */
	static String path (TreeItem item) {
		if (item.isDisposed ()) return "disposed";
		TreeItem parent = item.getParentItem ();
		if (parent == null) return "/" + item.getParent ().indexOf (item);
		return path (parent) + "/" + parent.indexOf (item);
	}

	private static int depth (String path) {
		int depth = -1;
		for (int i = 0; i < path.length (); i++) {
			if (path.charAt (i) == '/') depth++;
		}
		return depth;
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
