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
import java.lang.management.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.m3.collections.*;
import org.eclipse.swt.widgets.*;

/**
 * Label interning and path ids measured inside real SWT widgets at 10^6 rows.
 *
 * <p>Runs with any SWT jar (the official 3.135.0 jar for the reference) plus
 * the {@code org.eclipse.swt.internal.m3.collections} classes, on a hidden
 * desktop. Three measurements, written as TSV rows:</p>
 * <ol>
 * <li><b>table-memory</b>: a virtual Table of 10^6 rows x 4 Explorer columns
 * whose model keeps a String per cell (pool off) or an int label id per cell
 * (pool on); retained heap of the model after GC, per cell and per distinct
 * label.</li>
 * <li><b>table-scroll</b>: 200 page jumps through the Table; per page every
 * visible cell's text width is needed (label tooltips, column auto-width, the
 * viewport's DT_CALCRECT pass). Pool off calls {@code GC.stringExtent} per cell;
 * pool on asks {@link M3TextExtentCache} by (label id, font id). Reported:
 * stringExtent calls per page and milliseconds per page.</li>
 * <li><b>tree-restore</b>: a virtual Tree of 1,000 x 1,000 nodes (10^6 rows when
 * expanded) with 200 expanded roots and 400 expanded children is fully
 * refreshed ({@code removeAll}, {@code setItemCount}) and its expansion
 * restored, once with the scheme of eclipse.platform.ui#4247 (a set of element
 * paths with an incrementally carried hash, walking every item below an
 * expanded node) and once from {@link M3TreePathTrie} flags on index paths
 * (resolving only the flagged paths). Reported: restore time, items
 * materialized, bytes allocated.</li>
 * </ol>
 *
 * <pre>java -cp swt.jar;m3collections M3InterningWidgetBench out.tsv [table|tree|all]</pre>
 */
public final class M3InterningWidgetBench {
	static final Logger LOG = Logger.getLogger ("org.eclipse.swt.m3.M3InterningWidgetBench");
	static final int ROWS = 1_000_000, COLUMNS = 4, PAGES = 200;
	static final int [] DISTINCT = {169_618, 693, 1_301, 2_836};
	static final String [] SUFFIX = {".java", ".tsv", ".json", ".md", ".class", ".png"};
	static final int ROOTS = 1000, CHILDREN = 1000, GRANDCHILDREN = 10, EXPANDED_ROOTS = 200, EXPANDED_CHILDREN = 400;

	static String label (int column, int pick) {
		return switch (column) {
			case 0 -> "name-" + pick + SUFFIX [pick % SUFFIX.length];
			case 1 -> pick % 7 == 0 ? "File folder" : pick % 11 == 0 ? "JAVA File" : "T" + pick + " File";
			case 2 -> (pick + 1) + " KB";
			default -> "2026-10-" + (1 + pick % 28) + " " + (pick % 24) + ":" + (pick % 60 < 10 ? "0" : "") + (pick % 60);
		};
	}

	public static void main (String [] args) throws IOException {
		Path out = Path.of (args.length > 0 ? args [0] : "WIDGET_BENCH.tsv");
		String what = args.length > 1 ? args [1] : "all";
		Display display = new Display ();
		try (PrintWriter tsv = new PrintWriter (Files.newBufferedWriter (out, StandardCharsets.UTF_8))) {
			tsv.print ("measurement\tvariant\trows\tvalue\tunit\tdetail\n");
			if (!what.equals ("tree")) {
				table (display, false, tsv);
				table (display, true, tsv);
			}
			if (!what.equals ("table")) tree (display, tsv);
		}
		display.dispose ();
		LOG.info (() -> "wrote " + out.toAbsolutePath ());
	}

	static void row (PrintWriter tsv, String measurement, String variant, long rows, double value, String unit, String detail) {
		String line = String.format (Locale.ROOT, "%s\t%s\t%d\t%.3f\t%s\t%s", measurement, variant, rows, value, unit, detail);
		tsv.print (line + "\n");
		tsv.flush ();
		LOG.info (() -> line);
	}

	/* ---------------------------------------------------------------- Table */

	static void table (Display display, boolean pool, PrintWriter tsv) {
		String variant = pool ? "pool-on" : "pool-off";
		Shell shell = new Shell (display);
		shell.setSize (1000, 700);
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds (0, 0, 980, 660);
		table.setHeaderVisible (true);
		for (int c = 0; c < COLUMNS; c++) new TableColumn (table, SWT.NONE).setWidth (230);
		long before = retained ();
		Random random = new Random (42);
		M3LabelPool labels = pool ? new M3LabelPool (4096) : null;
		M3TextExtentCache extents = pool ? new M3TextExtentCache (1 << 16) : null;
		M3LabelLanes lanes = pool ? new M3LabelLanes (COLUMNS, labels, extents) : null;
		String [] [] cells = pool ? null : new String [COLUMNS] [ROWS];
		if (pool) lanes.setRowCount (ROWS);
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLUMNS; c++) {
				String text = label (c, random.nextInt (DISTINCT [c]));
				if (pool) lanes.setText (r, c, text); else cells [c] [r] = text;
			}
		}
		long retainedBytes = retained () - before;
		int distinct = pool ? labels.size () : distinct (cells);
		row (tsv, "table-memory", variant, ROWS, retainedBytes / 1048576.0, "MB retained by the model", "distinct labels " + distinct);
		row (tsv, "table-memory", variant, ROWS, retainedBytes / (double) (ROWS * COLUMNS), "bytes per cell", "");
		row (tsv, "table-memory", variant, ROWS, retainedBytes / (double) distinct, "bytes per distinct label", "");
		table.addListener (SWT.SetData, event -> {
			TableItem item = (TableItem) event.item;
			int r = table.indexOf (item);
			for (int c = 0; c < COLUMNS; c++) item.setText (c, pool ? lanes.text (r, c) : cells [c] [r]);
		});
		table.setItemCount (ROWS);
		shell.open ();
		drain (display);
		scroll (display, table, pool, lanes, cells, tsv, variant);
		shell.dispose ();
	}

	static void scroll (Display display, Table table, boolean pool, M3LabelLanes lanes, String [] [] cells, PrintWriter tsv, String variant) {
		int page = Math.max (1, table.getClientArea ().height / table.getItemHeight ());
		GC gc = new GC (table);
		long [] calls = new long [1];
		M3TextExtentCache.Measurer measurer = (id, font) -> {
			calls [0]++;
			Point extent = gc.stringExtent (lanes.pool ().text (id));
			return M3TextExtentCache.pack (extent.x, extent.y);
		};
		Random random = new Random (9);
		long measureNanos = 0, pageNanos = 0, sink = 0;
		for (int p = 0; p < PAGES; p++) {
			int top = random.nextInt (ROWS - page);
			long start = System.nanoTime ();
			table.setTopIndex (top);
			drain (display);
			long measureStart = System.nanoTime ();
			for (int r = top; r < top + page; r++) {
				for (int c = 0; c < COLUMNS; c++) {
					if (pool) {
						sink += lanes.extent (r, c, 0, measurer) >> 32;
					} else {
						calls [0]++;
						sink += gc.stringExtent (cells [c] [r]).x;
					}
				}
			}
			long end = System.nanoTime ();
			measureNanos += end - measureStart;
			pageNanos += end - start;
		}
		gc.dispose ();
		row (tsv, "table-scroll", variant, ROWS, calls [0] / (double) PAGES, "stringExtent calls per page", page + " visible rows x " + COLUMNS + " columns; sink " + (sink & 1));
		row (tsv, "table-scroll", variant, ROWS, measureNanos / 1e6 / PAGES, "ms per page in the width pass", "");
		row (tsv, "table-scroll", variant, ROWS, pageNanos / 1e6 / PAGES, "ms per page incl. setTopIndex + SetData", "");
	}

	static int distinct (String [] [] cells) {
		HashSet<String> seen = new HashSet<> ();
		for (String [] column : cells) seen.addAll (Arrays.asList (column));
		return seen.size ();
	}

	/* ----------------------------------------------------------------- Tree */

	/** Element of a node, as a content provider would hand it to the viewer (equal across a refresh). */
	static Long element (int root, int child, int grandchild) {
		return Long.valueOf (((long) root << 40) | ((long) (child + 1) << 20) | (grandchild + 1));
	}

	/** The #4247 scheme: element paths with a hash carried down the walk; equals compares segments. */
	record ElementPath (Object [] segments, int hash) {
		@Override public int hashCode () {
			return hash;
		}

		@Override public boolean equals (Object other) {
			return other instanceof ElementPath path && Arrays.equals (segments, path.segments);
		}
	}

	static void tree (Display display, PrintWriter tsv) {
		Shell shell = new Shell (display);
		shell.setSize (800, 700);
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		tree.setBounds (0, 0, 780, 660);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item, parent = item.getParentItem ();
			item.setText (parent == null ? "folder-" + event.index : "file-" + (event.index % 997) + ".java");
		});
		shell.open ();
		Random random = new Random (5);
		int [] roots = pick (random, ROOTS, EXPANDED_ROOTS);
		int [] [] children = new int [EXPANDED_ROOTS] [];
		for (int i = 0; i < EXPANDED_ROOTS; i++) children [i] = i < EXPANDED_ROOTS / 10 ? pick (random, CHILDREN, EXPANDED_CHILDREN / (EXPANDED_ROOTS / 10)) : new int [0];
		Set<ElementPath> paths = new HashSet<> ();
		M3TreePathTrie trie = new M3TreePathTrie ();
		for (int i = 0; i < roots.length; i++) {
			paths.add (path (element (roots [i], -1, -1)));
			trie.flag (trie.child (M3TreePathTrie.ROOT, roots [i]), M3TreePathTrie.EXPANDED, true);
			for (int child : children [i]) {
				paths.add (path (element (roots [i], -1, -1), element (roots [i], child, -1)));
				trie.flag (trie.intern (new int [] {roots [i], child}, 0, 2), M3TreePathTrie.EXPANDED, true);
			}
		}
		int expected = paths.size ();
		for (String scheme : new String [] {"treepath-hash-4247", "m3-path-trie", "treepath-hash-4247", "m3-path-trie"}) {
			long [] samples = new long [5];
			long allocated = 0, items = 0;
			for (int k = 0; k < samples.length; k++) {
				refresh (display, tree);
				long allocatedBefore = allocated ();
				long start = System.nanoTime ();
				long [] counts = scheme.startsWith ("m3") ? restoreFromTrie (tree, trie) : restoreByWalk (tree, paths);
				samples [k] = System.nanoTime () - start;
				allocated += allocated () - allocatedBefore;
				items = counts [1];
				if (counts [0] != expected) throw new AssertionError (scheme + " restored " + counts [0] + " of " + expected);
				verify (tree, roots, children);
			}
			Arrays.sort (samples);
			row (tsv, "tree-restore", scheme, (long) ROOTS * CHILDREN, samples [2] / 1e6, "ms per restore (median of 5)", expected + " expanded paths; " + items + " items materialized; " + allocated / 5 / 1024 + " KB allocated");
		}
		shell.dispose ();
	}

	static ElementPath path (Object... segments) {
		int hash = 0;
		for (Object segment : segments) hash += segment.hashCode ();
		return new ElementPath (segments, hash);
	}

	static void refresh (Display display, Tree tree) {
		tree.removeAll ();
		tree.setItemCount (ROOTS);
		drain (display);
	}

	/** #4247: walk the items, carry the parent's hash, look each path up; below expanded nodes every child is visited. */
	static long [] restoreByWalk (Tree tree, Set<ElementPath> paths) {
		long restored = 0, items = 0;
		for (int r = 0; r < ROOTS && restored < paths.size (); r++) {
			TreeItem item = tree.getItem (r);
			items++;
			Object rootElement = element (r, -1, -1);
			int hash = rootElement.hashCode ();
			if (!paths.contains (new ElementPath (new Object [] {rootElement}, hash))) continue;
			restored++;
			item.setItemCount (CHILDREN);
			item.setExpanded (true);
			for (int c = 0; c < CHILDREN && restored < paths.size (); c++) {
				Object childElement = element (r, c, -1);
				TreeItem child = item.getItem (c);
				items++;
				if (paths.contains (new ElementPath (new Object [] {rootElement, childElement}, hash + childElement.hashCode ()))) {
					restored++;
					child.setItemCount (GRANDCHILDREN);
					child.setExpanded (true);
				}
			}
		}
		return new long [] {restored, items};
	}

	/** Trie: resolve only the flagged index paths, parents first; no other item is touched. */
	static long [] restoreFromTrie (Tree tree, M3TreePathTrie trie) {
		long [] counts = new long [2];
		int [] segments = new int [2];
		for (int depth = 1; depth <= 2; depth++) {
			int level = depth;
			trie.forEachFlagged (M3TreePathTrie.EXPANDED, path -> {
				if (trie.depth (path) != level) return;
				trie.segments (path, segments);
				TreeItem item = tree.getItem (segments [0]);
				if (level == 2) item = item.getItem (segments [1]);
				counts [1] += level;
				item.setItemCount (level == 1 ? CHILDREN : GRANDCHILDREN);
				item.setExpanded (true);
				counts [0]++;
			});
		}
		return counts;
	}

	static void verify (Tree tree, int [] roots, int [] [] children) {
		for (int i = 0; i < roots.length; i++) {
			TreeItem root = tree.getItem (roots [i]);
			if (!root.getExpanded ()) throw new AssertionError ("root " + roots [i] + " not expanded");
			for (int child : children [i]) {
				if (!root.getItem (child).getExpanded ()) throw new AssertionError ("child " + child + " not expanded");
			}
		}
	}

	static int [] pick (Random random, int range, int count) {
		int [] all = new int [range];
		for (int i = 0; i < range; i++) all [i] = i;
		for (int i = 0; i < count; i++) {
			int j = i + random.nextInt (range - i), swap = all [i];
			all [i] = all [j];
			all [j] = swap;
		}
		int [] chosen = Arrays.copyOf (all, count);
		Arrays.sort (chosen);
		return chosen;
	}

	/* ------------------------------------------------------------- helpers */

	static void drain (Display display) {
		while (display.readAndDispatch ()) {
			/* dispatch pending SetData and paints */
		}
	}

	static long allocated () {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ()).getCurrentThreadAllocatedBytes ();
	}

	static long retained () {
		for (int i = 0; i < 4; i++) {
			System.gc ();
			try {
				Thread.sleep (50);
			} catch (InterruptedException e) {
				Thread.currentThread ().interrupt ();
			}
		}
		Runtime runtime = Runtime.getRuntime ();
		return runtime.totalMemory () - runtime.freeMemory ();
	}
}
