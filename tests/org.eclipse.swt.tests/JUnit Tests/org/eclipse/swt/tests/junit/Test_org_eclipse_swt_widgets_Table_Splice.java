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
import org.eclipse.swt.graphics.*;
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
		/* a hidden model Table (brief rule 24) answers after showing like the visible reference: the contract of M3Model
		 * (the top row stays on top), which a stock Table that is not visible does not keep (observed: after showing, its top
		 * row is not the one setTopIndex asked for while it was hidden) */
		if (hidden) model.setVisible (false);
		int at = random.nextInt (rows);
		int removed = random.nextInt (3) == 0 ? 0 : 1 + random.nextInt (Math.max (1, Math.min (6, rows - at - 1)));
		removed = Math.min (removed, rows - at);
		int insertCount = removed == 0 || random.nextBoolean () ? 1 + random.nextInt (5) : 0;
		long [] inserted = ids (insertCount);
		/* the model's ids are positional: "row i" for row i, like the reference */
		model (model, at, removed, inserted);
		itemApi (reference, at, removed, inserted, top);
		if (hidden) {
			model.setVisible (true);
			while (display.readAndDispatch ()) {
			}
		}
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

	/* the client area as the control draws it (WM_PRINT), as the differential records pixels */
	static ImageData print (Table table) {
		Rectangle size = table.getClientArea ();
		Image image = new Image (table.getDisplay (), Math.max (1, size.width), Math.max (1, size.height));
		GC gc = new GC (image);
		try {
			OS.SendMessage (table.handle, OS.WM_PRINT, gc.handle, OS.PRF_CLIENT | OS.PRF_ERASEBKGND | OS.PRF_CHILDREN);
		} finally {
			gc.dispose ();
		}
		ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	/**
	 * Brief rule 24, hidden viewports: hide, apply 10^4 deltas through M3Model, show. While hidden: no native row or
	 * state operation, no whole-list rebuild, no facade and no paint. After showing: exactly one paint pass, and the
	 * Table answers and draws like the reference, which made the same edits visible through the item API.
	 */
	@Test
	public void hiddenTableAppliesOneWindowPatchWhenShown () {
		int rows = 2000, deltas = 10_000;
		Table model = table (SWT.MULTI | SWT.FULL_SELECTION, rows, 5), reference = table (SWT.MULTI | SWT.FULL_SELECTION, rows, 320);
		Assumptions.assumeTrue (splices (model), "the hidden viewport is the win32 viewport's");
		for (Table table : new Table [] {model, reference}) {
			table.setSelection (new int [] {100, 101, 1500});
			table.setTopIndex (900);
		}
		int [] paints = new int [1];
		model.addListener (SWT.Paint, event -> paints [0]++);
		while (display.readAndDispatch ()) {
		}
		model.setVisible (false);
		while (display.readAndDispatch ()) {
		}
		paints [0] = 0;
		Object viewport = Test_org_eclipse_swt_widgets_Table_ViewportDeltas.field (model, Table.class, "viewport");
		long [] before = {counter (model, "m3RowOps"), counter (model, "m3StateOps"), counter (model, "m3FullRebuilds"),
				(Long) Test_org_eclipse_swt_widgets_Table_ViewportDeltas.field (viewport, viewport.getClass (), "facadesMade")};
		Random random = new Random (0x41DE);
		for (int i = 0; i < deltas; i++) {
			int count = model.getItemCount ();
			int at = random.nextInt (count);
			int top = reference.getTopIndex ();
			if (random.nextBoolean () || count < 200) {
				long [] inserted = ids (1 + random.nextInt (3));
				model (model, at, 0, inserted);
				itemApi (reference, at, 0, inserted, top);
			} else {
				int removed = Math.min (1 + random.nextInt (3), count - at);
				if (removed == count) continue;
				model (model, at, removed, new long [0]);
				itemApi (reference, at, removed, new long [0], top);
			}
		}
		long [] hidden = {counter (model, "m3RowOps"), counter (model, "m3StateOps"), counter (model, "m3FullRebuilds"),
				(Long) Test_org_eclipse_swt_widgets_Table_ViewportDeltas.field (viewport, viewport.getClass (), "facadesMade")};
		while (display.readAndDispatch ()) {
		}
		assertArrayEquals (before, hidden, "native row and state operations, whole-list rebuilds and facades while hidden");
		assertEquals (0, paints [0], "paints while hidden");
		model.setVisible (true);
		for (int i = 0; i < 3; i++) {
			while (display.readAndDispatch ()) {
			}
		}
		assertEquals (1, paints [0], "paint passes after showing");
		assertEquals (reference.getItemCount (), model.getItemCount ());
		assertEquals (reference.getTopIndex (), model.getTopIndex (), "the top row");
		ImageData drawn = print (model), expected = print (reference);
		assertArrayEquals (expected.data, drawn.data, "the visible rows draw like the reference");
		assertEquals (state (reference), state (model), "after showing");
		long [] shown = {counter (model, "m3RowOps") - hidden [0], counter (model, "m3StateOps") - hidden [1]};
		LOG.info (() -> "hidden: " + deltas + " deltas, " + model.getItemCount () + " rows; window patch: row operations " + shown [0] + ", state operations " + shown [1]);
	}

	private static void model (Table table, M3Patch patch, M3Model.Labels labels) {
		M3Model.of (table).apply (patch, labels);
	}

	private static void settle (Display display) {
		for (int i = 0; i < 3; i++) {
			while (display.readAndDispatch ()) {
			}
		}
	}

	/* splices and window patches with the Table logger at FINEST: the traces build their messages */
	@Test
	public void tracedSplicesAndWindowPatches () {
		Logger logger = Logger.getLogger ("org.eclipse.swt.widgets.Table");
		java.util.logging.Level old = logger.getLevel ();
		logger.setLevel (java.util.logging.Level.FINEST);
		try {
			Table table = table (SWT.MULTI, 200, 5);
			Assumptions.assumeTrue (splices (table), "the splice is the win32 viewport's");
			table.setTopIndex (100);
			settle (display);
			model (table, 10, 2, ids (3));
			table.setVisible (false);
			model (table, 10, 2, ids (3));
			table.setVisible (true);
			settle (display);
			assertEquals (202, table.getItemCount ());
		} finally {
			logger.setLevel (old);
		}
	}

	/* the selected rows that keep their index across a splice stay selected without a message of their own */
	@Test
	public void selectedRowsThatKeepTheirIndexStaySelected () {
		Table table = table (SWT.MULTI, 60, 5), reference = table (SWT.MULTI, 60, 320);
		for (Table t : new Table [] {table, reference}) t.setSelection (new int [] {10, 12, 40});
		model (table, 5, 2, new long [0]);
		itemApi (reference, 5, 2, new long [0], reference.getTopIndex ());
		assertEquals (state (reference), state (table));
		assertArrayEquals (new int [] {8, 10, 38}, table.getSelectionIndices ());
	}

	/* a removed focus row at the end passes the focus to the new last row */
	@Test
	public void removingTheLastRowsMovesTheFocusToTheNewLastRow () {
		Table table = table (SWT.MULTI, 40, 5), reference = table (SWT.MULTI, 40, 320);
		for (Table t : new Table [] {table, reference}) t.setSelection (38);
		model (table, 36, 4, new long [0]);
		itemApi (reference, 36, 4, new long [0], reference.getTopIndex ());
		assertEquals (state (reference), state (table));
	}

	/* a Table that does not draw splices without invalidating rows; drawing again shows them */
	@Test
	public void spliceOfATableThatDoesNotDraw () {
		Table table = table (SWT.MULTI, 80, 5);
		table.setRedraw (false);
		model (table, 3, 1, ids (2));
		table.setRedraw (true);
		settle (display);
		assertEquals (81, table.getItemCount ());
		assertEquals ("row 0 row 1 row 2", table.getItem (0).getText () + " " + table.getItem (1).getText () + " " + table.getItem (2).getText ());
	}

	/* a count change that invalidates every row is a whole-list rebuild (brief rule 24) */
	@Test
	public void countChangeWithoutNoInvalidateAllIsCountedAsARebuild () {
		Table table = table (SWT.MULTI, 30, 5);
		Assumptions.assumeTrue (splices (table), "the counters are the win32 viewport's");
		long rebuilds = counter (table, "m3FullRebuilds");
		OS.SendMessage (table.handle, OS.LVM_SETITEMCOUNT, 30, 0);
		assertEquals (rebuilds + 1, counter (table, "m3FullRebuilds"));
	}

	/*
	 * Labels: the lanes take text and check box of a row without a facade; a row with a facade, with an image or labelled
	 * with one goes through its item. A visible row is repainted once, a row outside the window not at all; a
	 * column-less Table widens to a wider row, at once while drawing and at the next paint otherwise.
	 */
	@Test
	public void labelsGoToTheLanesOrThroughTheItem () {
		Table table = new Table (shell, SWT.MULTI | SWT.CHECK | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		for (int c = 0; c < 3; c++) new TableColumn (table, SWT.NONE).setWidth (80);
		for (int i = 0; i < 200; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		boolean viewport = splices (table);
		Image image = new Image (display, 16, 16);
		try {
			table.getItem (150).setImage (1, image);
			TableItem held = table.getItem (160);
			settle (display);
			M3Patch.Builder builder = M3Patch.builder (M3Model.ROOT).insert (5, 900_000L, 900_001L).update (0, 2).update (152, 1).update (162, 1).update (172, 2);
			model (table, builder.build (), (id, row) -> {
				if (id == 900_001L || id == 1) row.setImage (0, image);
				if (id == 171) row.setText (1, null);
				if (id == 0) {
					row.setText (2, "third only");
				} else {
					row.setText (0, "label " + id);
				}
				row.setChecked (id % 2 == 0);
			});
			assertEquals ("", table.getItem (0).getText (0));
			assertEquals ("third only", table.getItem (0).getText (2));
			assertTrue (table.getItem (0).getChecked ());
			assertEquals (image, table.getItem (1).getImage (0), "a labelled image goes through the item");
			assertEquals ("label 900001", table.getItem (6).getText ());
			assertEquals ("label 150", table.getItem (152).getText ());
			assertNull (table.getItem (152).getImage (1), "the row with an image was relabelled through its item");
			assertEquals ("label 160", held.getText (), "a held facade is the row's state");
			assertEquals ("", table.getItem (173).getText (1));
			assertFalse (table.getItem (173).getChecked ());
		} finally {
			table.dispose ();
			image.dispose ();
		}
		/* repaint and width of a column-less Table */
		Table plain = table (SWT.MULTI, 100, 5);
		settle (display);
		long rowOps = counter (plain, "m3RowOps");
		int width = (int) OS.SendMessage (plain.handle, OS.LVM_GETCOLUMNWIDTH, 0, 0);
		model (plain, M3Patch.builder (M3Model.ROOT).update (2, 1).build (), (id, row) -> row.setText (0, "a row whose text is much wider than the column of the table " + id));
		if (viewport) assertEquals (rowOps + 1, counter (plain, "m3RowOps"), "a visible row is repainted once");
		assertTrue (OS.SendMessage (plain.handle, OS.LVM_GETCOLUMNWIDTH, 0, 0) > width, "the column widens to the row");
		rowOps = counter (plain, "m3RowOps");
		model (plain, M3Patch.builder (M3Model.ROOT).update (90, 1).build (), (id, row) -> row.setText (0, "row " + id));
		if (viewport) assertEquals (rowOps, counter (plain, "m3RowOps"), "a row outside the window is not repainted");
		plain.setRedraw (false);
		model (plain, M3Patch.builder (M3Model.ROOT).update (3, 1).build (), (id, row) -> row.setText (0, "an even wider row text than the widest one before it in this table " + id));
		plain.setRedraw (true);
		settle (display);
		assertEquals ("an even wider row text than the widest one before it in this table 3", plain.getItem (3).getText ());
	}

	/*
	 * A dirty (hidden) viewport answers the count, the top row and the queries that do not depend on the rows itself;
	 * an item setter repaints nothing; a query of the rows applies the window patch while the Table is still hidden, and
	 * so does a column autosize. A minimized shell and a zero size are hidden too.
	 */
	@Test
	public void hiddenQueriesApplyTheWindowPatch () {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setWidth (100);
		for (int i = 0; i < 300; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		Assumptions.assumeTrue (splices (table), "the hidden viewport is the win32 viewport's");
		table.setTopIndex (100);
		settle (display);
		table.setVisible (false);
		model (table, 10, 0, ids (5));
		long stateOps = counter (table, "m3StateOps");
		assertEquals (305, table.getItemCount (), "the logical count");
		assertEquals (105, table.getTopIndex (), "the logical top row");
		assertTrue (OS.SendMessage (table.handle, OS.LVM_GETCOUNTPERPAGE, 0, 0) >= 0);
		assertEquals (100, OS.SendMessage (table.handle, OS.LVM_GETCOLUMNWIDTH, 0, 0));
		table.getItem (107).setText ("changed while hidden");
		table.getFont ();
		assertEquals (stateOps, counter (table, "m3StateOps"), "nothing native yet");
		column.setWidth (120);
		assertEquals (0, table.getSelectionIndices ().length, "a query of the rows applies the window patch");
		model (table, 10, 0, ids (5));
		column.pack ();
		table.setVisible (true);
		settle (display);
		assertEquals (310, table.getItemCount ());
		assertEquals ("changed while hidden", table.getItem (107).getText ());
		/* a minimized shell, then a zero size, without selection or a top change */
		shell.setMinimized (true);
		settle (display);
		model (table, 290, 0, ids (3));
		shell.setMinimized (false);
		settle (display);
		table.setSize (0, 0);
		model (table, 290, 1, new long [0]);
		table.setSize (300, 380);
		settle (display);
		assertEquals (312, table.getItemCount ());
		assertEquals (table.getItemCount (), (int) OS.SendMessage (table.handle, OS.LVM_GETITEMCOUNT, 0, 0));
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
