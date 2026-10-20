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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.m3.M3Patch;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * <code>M3Model.apply</code> on a plain Table: the splice of the win32 viewport (Table leaf T-Table-O2,
 * docs/m3/TABLE_MEDIATED_VIEWPORT.md; TableSplice) answers like the same edits made through the item API
 * (<code>remove (start, end)</code>, <code>new TableItem (table, SWT.NONE, index)</code>, then
 * <code>setTopIndex</code> of the old top row or of the first row after it that survives), and a run outside the
 * visible rows costs no native row operation. The item-API answers are those of the stock Table on native items
 * (evidence/SpliceProbe.java.txt, RULES: 400 scenarios, no mismatch on the official jar). The tests run in every
 * mode: without the viewport M3Model edits through the item API and the counters stay unread.
 */
public class Test_org_eclipse_swt_widgets_Table_Splice {
	static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Table_Splice.class.getName ());
	static final int LVM_GETSELECTIONMARK = OS.LVM_FIRST + 66;

	Display display;
	Shell shell;
	long nextId = 1_000_000L;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 640, 420);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private Table table (int style, int rows, int x) {
		Table table = new Table (shell, style | SWT.BORDER);
		table.setBounds (x, 5, 300, 380);
		for (int i = 0; i < rows; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		return table;
	}

	/* the viewport counters of the Table (TableSplice.count); -1 when this SWT has none */
	static long counter (Table table, String name) {
		try {
			Field field = Table.class.getDeclaredField (name);
			field.setAccessible (true);
			return field.getLong (table);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1;
		}
	}

	static boolean splices (Table table) {
		try {
			Field field = Table.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			return field.get (table) != null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static String state (Table table) {
		StringBuilder answer = new StringBuilder ();
		answer.append ("count ").append (table.getItemCount ()).append (" top ").append (table.getTopIndex ());
		answer.append (" selection ").append (Arrays.toString (table.getSelectionIndices ()));
		answer.append (" focus ").append (OS.SendMessage (table.handle, OS.LVM_GETNEXTITEM, -1, OS.LVNI_FOCUSED));
		answer.append (" mark ").append (OS.SendMessage (table.handle, LVM_GETSELECTIONMARK, 0, 0));
		answer.append (" texts");
		for (TableItem item : table.getItems ()) answer.append (' ').append (item.getText ());
		return answer.toString ();
	}

	private long [] ids (int count) {
		long [] ids = new long [count];
		for (int i = 0; i < count; i++) ids [i] = nextId++;
		return ids;
	}

	/* reference: the run through the item API, then the top row of M3Model.restoreTop */
	private static void itemApi (Table table, int at, int removed, long [] inserted, int top) {
		int anchorRow = top;
		if (removed > 0) table.remove (at, at + removed - 1);
		if (top >= at && top < at + removed) anchorRow = at;
		else if (top >= at + removed) anchorRow = top - removed;
		for (int k = 0; k < inserted.length; k++) new TableItem (table, SWT.NONE, at + k).setText ("row " + inserted [k]);
		if (anchorRow >= at && inserted.length > 0) anchorRow += inserted.length;
		int count = table.getItemCount ();
		if (count > 0) table.setTopIndex (Math.min (anchorRow, count - 1));
	}

	private static void model (Table table, int at, int removed, long [] inserted) {
		M3Patch.Builder builder = M3Patch.builder (M3Model.ROOT);
		if (removed > 0) builder.remove (at, removed);
		if (inserted.length > 0) builder.insert (at, inserted);
		M3Model.of (table).apply (builder.build (), (id, row) -> row.setText (0, "row " + id));
	}

	/* positional ids answer "row i" for the first rows: the labels of the model are those of the item API */
	private void scenario (Random random, int style, boolean hidden) {
		int rows = 20 + random.nextInt (60);
		Table model = table (style, rows, 5), reference = table (style, rows, 320);
		for (Table table : new Table [] {model, reference}) table.setVisible (!hidden);
		int selected = random.nextInt (4);
		int [] selection = new int [selected];
		for (int i = 0; i < selected; i++) selection [i] = random.nextInt (rows);
		int top = random.nextInt (rows);
		for (Table table : new Table [] {model, reference}) {
			if (selected > 0) table.setSelection (selection);
			if (selected > 1 && (style & SWT.MULTI) != 0) table.select (selection [1]);
			table.setTopIndex (top);
		}
		display.readAndDispatch ();
		top = model.getTopIndex ();
		assertEquals (state (reference), state (model), "before the run");
		int at = random.nextInt (rows);
		int removed = random.nextInt (3) == 0 ? 0 : 1 + random.nextInt (Math.max (1, Math.min (6, rows - at - 1)));
		removed = Math.min (removed, rows - at - 1);
		int insertCount = removed == 0 || random.nextBoolean () ? 1 + random.nextInt (5) : 0;
		long [] inserted = ids (insertCount);
		/* the model's ids are positional: "row i" for row i, like the reference */
		model (model, at, removed, inserted);
		itemApi (reference, at, removed, inserted, top);
		assertEquals (state (reference), state (model), "at " + at + " removed " + removed + " inserted " + insertCount + " top " + top + " selection "
				+ Arrays.toString (selection) + (hidden ? " hidden" : ""));
		model.dispose ();
		reference.dispose ();
	}

	@Test
	public void spliceAnswersLikeTheItemApi () {
		Random random = new Random (0x5B1CE);
		for (int i = 0; i < 60; i++) scenario (random, SWT.MULTI | SWT.FULL_SELECTION, false);
		for (int i = 0; i < 20; i++) scenario (random, SWT.SINGLE, false);
		for (int i = 0; i < 10; i++) scenario (random, SWT.MULTI, true);
	}

	@Test
	public void runsOutsideTheVisibleRowsCostNoNativeRowOperation () {
		int rows = 100_000;
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (rows);
		Assumptions.assumeTrue (splices (table), "the Table splices only with the win32 viewport");
		table.setTopIndex (rows / 2);
		int top = table.getTopIndex ();
		int page = (int) OS.SendMessage (table.handle, OS.LVM_GETCOUNTPERPAGE, 0, 0);
		for (int i = top; i <= top + page; i++) table.getItem (i).setText ("row " + i);
		table.setSelection (top + 3);
		while (display.readAndDispatch ()) {
		}
		long rowOps = counter (table, "m3RowOps"), stateOps = counter (table, "m3StateOps"), rebuilds = counter (table, "m3FullRebuilds");
		model (table, top - 5000, 0, ids (100));
		model (table, top + page + 5000, 100, new long [0]);
		assertEquals (rowOps, counter (table, "m3RowOps"), "native row operations of runs outside the visible rows");
		assertEquals (rows, table.getItemCount ());
		assertEquals ("row " + top, table.getItem (table.getTopIndex ()).getText (), "the top row stays on top");
		assertArrayEquals (new int [] {top + 103}, table.getSelectionIndices (), "the selection moves with its row");
		long states = counter (table, "m3StateOps") - stateOps;
		/* inside the window: the rows from the first changed one down are invalidated, nothing more */
		int newTop = table.getTopIndex ();
		long before = counter (table, "m3RowOps");
		model (table, newTop + 4, 0, ids (5));
		long inside = counter (table, "m3RowOps") - before;
		assertTrue (inside <= page + 1, "a run inside the visible rows costs at most the visible rows: " + inside);
		assertEquals ("row " + top, table.getItem (table.getTopIndex ()).getText ());
		assertEquals (rebuilds, counter (table, "m3FullRebuilds"), "no whole-list rebuild (brief rule 24)");
		LOG.info (() -> "splice outside the window: state messages " + states + "; inside: row operations " + inside + " (" + (page + 1) + " visible rows)");
	}

	@Test
	public void declinedRunsKeepTheItemApi () {
		/* more selected rows after the run than it can move: the item API moves them natively */
		Table table = table (SWT.MULTI, 300, 5), reference = table (SWT.MULTI, 300, 320);
		table.selectAll ();
		reference.selectAll ();
		long rowOps = counter (table, "m3RowOps");
		model (table, 10, 2, new long [0]);
		itemApi (reference, 10, 2, new long [0], reference.getTopIndex ());
		assertEquals (state (reference), state (table));
		if (splices (table)) assertTrue (counter (table, "m3RowOps") > rowOps, "declined: the rows go through the item API");
		/* the last rows go, and rows come into an empty table */
		model (table, 0, table.getItemCount (), new long [0]);
		assertEquals (0, table.getItemCount ());
		model (table, 0, 0, new long [] {7, 8});
		assertEquals ("row 7 row 8", table.getItem (0).getText () + " " + table.getItem (1).getText ());
	}
}
