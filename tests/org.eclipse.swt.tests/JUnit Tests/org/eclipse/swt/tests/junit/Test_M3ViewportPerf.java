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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.stream.IntStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.M3RowMath;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Timing leaf of <code>docs/m3/VIEWPORT_PERF_20261031.md</code>: each slow path the combination matrix found, with a
 * time budget and a counted-operations assertion. The counts (native rows, native sibling steps, DrawText calls) do
 * not depend on the speed of the machine; the budget catches what the counts do not name. win32, this SWT, any mode:
 * each assertion names the mode it holds in.
 */
public class Test_M3ViewportPerf {
	static final int ROWS = 100_000, LIST_ROWS = 1_000_000;
	static final long BUDGET_MS = 1_000;
	/* native rows a viewport keeps for a 300 pixel high tree: the client rows, the overscan and their ancestors */
	static final long WINDOW_ROWS = 256;
	static final int TVM_GETCOUNT = 0x1105, TVM_GETVISIBLECOUNT = 0x1110;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		assumeTrue ("win32".equals (SWT.getPlatform ()), "the counted operations are win32 native messages and counters");
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (400, 300);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) shell.dispose ();
	}

	static long millis (Runnable action) {
		long start = System.nanoTime ();
		action.run ();
		return (System.nanoTime () - start) / 1_000_000;
	}

	static long counter (Object widget, String name) {
		try {
			Field field = widget.getClass ().getDeclaredField (name);
			field.setAccessible (true);
			return ((Number) field.get (widget)).longValue ();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	static long nativeRows (Tree tree) {
		return ViewportDifferential.send (tree, TVM_GETCOUNT, 0, 0);
	}

	static boolean viewport (String widget) {
		return !"original".equals (ViewportDifferential.reference (widget));
	}

	/** r0200 / CACHED_CHILD_COUNT: root and child counts of a plain Tree read the sibling cache, one walk per list. */
	@Test
	public void childCountsReadTheSiblingCache () {
		int roots = 3_000, children = 2_000;
		Tree tree = new Tree (shell, SWT.NONE);
		shell.open ();
		tree.setItemCount (roots);
		tree.getItem (7).setItemCount (children);
		long before = counter (tree, "siblingSteps");
		TreeItem parent = tree.getItem (7);
		/* as M3TreeWalk asks: the count of one list per row; alternating lists share stock's one-list cache (3.135.0 too) */
		long elapsed = millis (() -> {
			IntStream.range (0, 1_000).forEach (i -> assertEquals (roots, tree.getItemCount ()));
			IntStream.range (0, 1_000).forEach (i -> assertEquals (children, parent.getItemCount ()));
		});
		long steps = counter (tree, "siblingSteps") - before;
		assertTrue (steps <= 2L * (roots + children), "1000 root and 1000 child counts took " + steps + " native sibling steps");
		assertTrue (elapsed < BUDGET_MS, "1000 root and child counts took " + elapsed + " ms");
	}

	/** r0200 / CACHED_CHILD_COUNT: M3RowMath.visibleRowCount walks the roots once (the walk asks the root count per row). */
	@Test
	public void rowMathOverPlainRootsIsLinear () {
		int roots = 4_000;
		Tree tree = new Tree (shell, SWT.MULTI);
		shell.open ();
		tree.setItemCount (roots);
		long before = counter (tree, "siblingSteps");
		long[] rows = new long [1];
		long elapsed = millis (() -> rows [0] = M3RowMath.visibleRowCount (tree));
		long steps = counter (tree, "siblingSteps") - before;
		assertEquals (roots, rows [0]);
		assertTrue (steps <= 2L * roots, "visibleRowCount over " + roots + " roots took " + steps + " native sibling steps");
		assertTrue (elapsed < BUDGET_MS, "visibleRowCount over " + roots + " roots took " + elapsed + " ms");
	}

	/**
	 * r0074 / TOP_ITEM_SEEK: <code>setTopItem (getItem (k))</code> of a virtual Tree puts row k at the top (official 3.135.0
	 * answers k); the viewport does it with the window's native rows only, the original mode with k + one page of them.
	 */
	@Test
	public void virtualSetTopItemTopsTheTargetRow () {
		boolean viewport = viewport ("Tree");
		int count = viewport ? ROWS : 4_000, target = count / 2;
		Tree tree = new Tree (shell, SWT.VIRTUAL);
		new TreeColumn (tree, SWT.NONE).setWidth (300);
		tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText ("item " + e.index));
		shell.open ();
		tree.setItemCount (count);
		long elapsed = millis (() -> tree.setTopItem (tree.getItem (target)));
		assertEquals (target, tree.indexOf (tree.getTopItem ()));
		long page = ViewportDifferential.send (tree, TVM_GETVISIBLECOUNT, 0, 0) + 1;
		long bound = viewport ? WINDOW_ROWS : target + 2 * page + 1;
		assertTrue (nativeRows (tree) <= bound, "native rows " + nativeRows (tree) + " > " + bound);
		if (viewport) assertTrue (elapsed < BUDGET_MS, "setTopItem (getItem (" + target + ")) took " + elapsed + " ms");
	}

	/**
	 * F02/F05/F10/F15 / DEFERRED_WIDTH_MEASURE + GLYPH_ADVANCE_LANE: a List of a million rows opens within the budget: the
	 * exact width is one pass in one DC, ASCII rows answered by the advance lane (no DrawText).
	 */
	@Test
	public void millionRowListOpensWithinBudget () {
		assumeTrue (viewport ("List"), "the native list box of the original mode holds every row (stock)");
		String [] items = new String [LIST_ROWS];
		java.util.Arrays.setAll (items, i -> "Item " + String.format (Locale.ROOT, "%,d", i));
		List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		long elapsed = millis (() -> {
			list.setItems (items);
			shell.pack ();
			shell.open ();
		});
		assertEquals (0, counter (list, "measureDrawTexts"), "DrawText calls for ASCII rows");
		assertEquals (LIST_ROWS, counter (list, "measureLaneAnswers"), "rows answered by the advance lane");
		assertTrue (elapsed < BUDGET_MS, "setItems + pack + open of " + LIST_ROWS + " rows took " + elapsed + " ms");
	}

	/** GLYPH_ADVANCE_LANE: rows outside printable ASCII keep DrawText, one call each, in the same single pass. */
	@Test
	public void nonAsciiRowsKeepDrawText () {
		assumeTrue (viewport ("List"), "the original mode measures with the native list box");
		String [] items = IntStream.range (0, 3_000).mapToObj (i -> i % 3 == 0 ? "Zeile äöü " + i : i % 3 == 1 ? "row\t" + i : "row " + i).toArray (String []::new);
		List list = new List (shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
		list.setItems (items);
		list.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		assertEquals (2_000, counter (list, "measureDrawTexts"), "DrawText calls (umlaut and tab rows)");
		assertEquals (1_000, counter (list, "measureLaneAnswers"), "rows answered by the advance lane");
	}

	/** The viewport List measures each row once: a second exact width is free. */
	@Test
	public void secondComputeSizeMeasuresNothing () {
		assumeTrue (viewport ("List"), "viewport only");
		List list = new List (shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		list.setItems (IntStream.range (0, 5_000).mapToObj (i -> "row " + i).toArray (String []::new));
		list.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		long draws = counter (list, "measureDrawTexts"), lanes = counter (list, "measureLaneAnswers");
		list.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		assertEquals (draws, counter (list, "measureDrawTexts"));
		assertEquals (lanes, counter (list, "measureLaneAnswers"));
	}
}
