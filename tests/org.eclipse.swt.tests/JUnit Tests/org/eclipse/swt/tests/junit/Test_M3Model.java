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

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;
import org.junit.jupiter.api.*;

/**
 * Differential of M3Model.apply (enhanced M3 API, leaf 19, the model seam): a twin widget receives the same
 * patch through the stock item API (remove, new item, setText, then the anchored setTopIndex). Rows, labels,
 * selection, top row, emptiness events and pixels must be equal. Runs in every startup mode.
 */
public class Test_M3Model {
	Display display;
	Shell shell;
	/* label version per id: an update run bumps it */
	final Map<Long, Integer> versions = new HashMap<> ();
	int fills;

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

	private void flush () {
		while (display.readAndDispatch ()) {
			// paint
		}
	}

	private String label (long id) {
		return "row " + id + " v" + versions.getOrDefault (id, 0);
	}

	private final M3Model.Labels labels = (id, row) -> {
		fills++;
		row.setText (0, label (id));
		row.setText (1, Long.toString (id * 7));
	};

	/* a random patch of a list of ids: removes, inserts with fresh ids, updates */
	private M3Patch randomPatch (Random random, long [] ids, long [] nextId) {
		M3Patch.Builder builder = M3Patch.builder (M3Patch.ROOT);
		int size = ids.length, limit = size;
		for (int k = random.nextInt (4); k > 0 && limit > 0; k--) {
			int count = 1 + random.nextInt (Math.min (5, limit));
			int at = random.nextInt (limit - count + 1);
			builder.remove (at, count);
			limit = at;
			size -= count;
		}
		int from = 0;
		for (int k = random.nextInt (4); k > 0; k--) {
			int at = from + random.nextInt (size - from + 1), count = 1 + random.nextInt (4);
			long [] fresh = new long [count];
			for (int i = 0; i < count; i++) fresh [i] = nextId [0]++;
			builder.insert (at, fresh);
			size += count;
			from = at + count;
		}
		int up = 0;
		for (int k = random.nextInt (3); k > 0 && up < size; k--) {
			int at = up + random.nextInt (size - up), count = 1 + random.nextInt (Math.min (3, size - at));
			builder.update (at, count);
			up = at + count;
		}
		return builder.build ();
	}

	/* the stock twin of apply: the same runs through the item API, then the anchored top row */
	private void stockApply (Table table, M3Patch patch, long [] before, long [] after) {
		int top = table.getTopIndex ();
		patch.applyTo (new M3Patch.Target () {
			int run;

			@Override
			public void remove (int index, int count) {
				table.remove (index, index + count - 1);
			}

			@Override
			public void insert (int index, int count) {
				for (int k = 0; k < count; k++) {
					TableItem item = new TableItem (table, SWT.NONE, index + k);
					long id = patch.insertedId (run, k);
					item.setText (new String [] {label (id), Long.toString (id * 7)});
				}
				run++;
			}

			@Override
			public void update (int index, int count) {
				for (int k = 0; k < count; k++) {
					long id = after [index + k];
					table.getItem (index + k).setText (new String [] {label (id), Long.toString (id * 7)});
				}
			}
		});
		if (before.length > 0 && after.length > 0) table.setTopIndex (anchor (before, top, after));
	}

	private static int anchor (long [] before, int top, long [] after) {
		for (int k = top; k < before.length; k++) {
			for (int i = 0; i < after.length; i++) if (after [i] == before [k]) return i;
		}
		return after.length - 1;
	}

	private Table table (int style, int rows, int x) {
		Table table = new Table (shell, style | SWT.MULTI | SWT.BORDER);
		table.setBounds (x, 5, 300, 380);
		new TableColumn (table, SWT.NONE).setWidth (180);
		new TableColumn (table, SWT.NONE).setWidth (90);
		for (int i = 0; i < rows; i++) new TableItem (table, SWT.NONE).setText (new String [] {label (i), Long.toString (i * 7L)});
		return table;
	}

	private static ImageData pixels (Control control) {
		Point size = control.getSize ();
		Image image = new Image (control.getDisplay (), size.x, size.y);
		GC gc = new GC (image);
		control.print (gc);
		gc.dispose ();
		ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	private void assertTwins (Table model, Table stock, String what) {
		assertEquals (stock.getItemCount (), model.getItemCount (), what);
		for (int i = 0; i < stock.getItemCount (); i++) {
			assertEquals (stock.getItem (i).getText (0), model.getItem (i).getText (0), what + " row " + i);
			assertEquals (stock.getItem (i).getText (1), model.getItem (i).getText (1), what + " row " + i);
		}
		assertArrayEquals (stock.getSelectionIndices (), model.getSelectionIndices (), what);
		assertEquals (stock.getTopIndex (), model.getTopIndex (), what);
	}

	private void differential (int style) {
		Random random = new Random (190 + style);
		Table model = table (style, 300, 5), stock = table (style, 300, 320);
		java.util.List<int []> modelEvents = new ArrayList<> (), stockEvents = new ArrayList<> ();
		M3Emptiness.addListener (model, e -> modelEvents.add (new int [] {e.detail, e.count}));
		M3Emptiness.addListener (stock, e -> stockEvents.add (new int [] {e.detail, e.count}));
		M3Model m3 = M3Model.of (model);
		long [] ids = new long [300];
		for (int i = 0; i < ids.length; i++) ids [i] = i;
		long [] nextId = {1000};
		for (int round = 0; round < 60; round++) {
			int top = random.nextInt (Math.max (1, model.getItemCount ()));
			model.setTopIndex (top);
			stock.setTopIndex (top);
			int [] selection = random.ints (3, 0, Math.max (1, model.getItemCount ())).toArray ();
			model.setSelection (selection);
			stock.setSelection (selection);
			M3Patch patch = randomPatch (random, ids, nextId);
			long [] after = patch.apply (ids);
			for (int i = 0; i < patch.updateRuns (); i++) {
				for (int k = 0; k < patch.updateCount (i); k++) versions.merge (after [patch.updateAt (i) + k], 1, Integer::sum);
			}
			m3.apply (patch, labels);
			stockApply (stock, patch, ids, after);
			flush ();
			assertTwins (model, stock, "round " + round);
			for (int i = 0; i < after.length; i += 17) assertEquals (i, m3.indexOf (after [i]));
			ids = after;
		}
		assertEquals (stockEvents.size (), modelEvents.size ());
		/* neither table has the focus: a focused selection paints in another colour */
		Button focus = new Button (shell, SWT.PUSH);
		focus.setBounds (5, 390, 60, 20);
		focus.setFocus ();
		model.redraw ();
		stock.redraw ();
		model.update ();
		stock.update ();
		ImageData a = pixels (model), b = pixels (stock);
		assertArrayEquals (b.data, a.data, "pixels");
	}

	@Test
	public void test_tableEqualsStockEdits () {
		differential (SWT.NONE);
	}

	@Test
	public void test_virtualTableEqualsStockEdits () {
		differential (SWT.VIRTUAL);
	}

	@Test
	public void test_aDeltaOutsideTheWindowKeepsTheTopRowAndAsksNoLabels () {
		Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		M3Model m3 = M3Model.of (table);
		long [] start = new long [5000];
		for (int i = 0; i < start.length; i++) start [i] = i;
		m3.apply (M3Patch.builder (M3Patch.ROOT).insert (0, start).build (), labels);
		table.setTopIndex (2000);
		flush ();
		table.update ();
		long topId = m3.id (M3Model.ROOT, table.getTopIndex ());
		fills = 0;
		long [] above = new long [100], below = new long [100];
		for (int i = 0; i < 100; i++) {
			above [i] = 10_000 + i;
			below [i] = 20_000 + i;
		}
		m3.apply (M3Patch.builder (M3Patch.ROOT).remove (10, 50).insert (0, above).insert (4900, below).build (), labels);
		assertEquals (0, fills, "no label asked outside the window");
		assertEquals (topId, m3.id (M3Model.ROOT, table.getTopIndex ()), "the top row stays");
		assertEquals (5150, table.getItemCount ());
		flush ();
		table.update ();
		assertTrue (fills <= 60, "labels only for the rows shown: " + fills);
	}

	@Test
	public void test_listEqualsStockEdits () {
		List model = new List (shell, SWT.MULTI | SWT.V_SCROLL), stock = new List (shell, SWT.MULTI | SWT.V_SCROLL);
		model.setBounds (5, 5, 300, 380);
		stock.setBounds (320, 5, 300, 380);
		for (int i = 0; i < 200; i++) {
			model.add (label (i));
			stock.add (label (i));
		}
		M3Model m3 = M3Model.of (model);
		Random random = new Random (7);
		long [] ids = new long [200];
		for (int i = 0; i < ids.length; i++) ids [i] = i;
		long [] nextId = {1000};
		for (int round = 0; round < 40; round++) {
			int top = random.nextInt (model.getItemCount ());
			model.setTopIndex (top);
			stock.setTopIndex (top);
			M3Patch patch = randomPatch (random, ids, nextId);
			long [] after = patch.apply (ids);
			for (int i = 0; i < patch.updateRuns (); i++) {
				for (int k = 0; k < patch.updateCount (i); k++) versions.merge (after [patch.updateAt (i) + k], 1, Integer::sum);
			}
			m3.apply (patch, labels);
			int stockTop = stock.getTopIndex ();
			long [] before = ids;
			patch.applyTo (new M3Patch.Target () {
				int run;

				@Override
				public void remove (int index, int count) {
					stock.remove (index, index + count - 1);
				}

				@Override
				public void insert (int index, int count) {
					for (int k = 0; k < count; k++) stock.add (label (patch.insertedId (run, k)), index + k);
					run++;
				}

				@Override
				public void update (int index, int count) {
					for (int k = 0; k < count; k++) stock.setItem (index + k, label (after [index + k]));
				}
			});
			if (after.length > 0) stock.setTopIndex (anchor (before, stockTop, after));
			assertArrayEquals (stock.getItems (), model.getItems (), "round " + round);
			assertEquals (stock.getTopIndex (), model.getTopIndex (), "round " + round);
			ids = after;
		}
		assertEquals (label (ids [3]), m3.text (ids [3], 0));
		assertNull (m3.image (ids [3], 0));
	}

	@Test
	public void test_idsAndArguments () {
		Table table = new Table (shell, SWT.NONE);
		for (int i = 0; i < 3; i++) new TableItem (table, SWT.NONE).setText ("t" + i);
		M3Model m3 = M3Model.of (table);
		assertSame (m3, M3Model.of (table));
		assertEquals (3, m3.size (M3Model.ROOT));
		assertEquals (2, m3.id (M3Model.ROOT, 2), "positional ids");
		assertEquals (1, m3.indexOf (1));
		assertEquals (-1, m3.indexOf (3));
		assertEquals (M3Model.ROOT, m3.parentOf (0));
		assertEquals (-2, m3.parentOf (99));
		m3.setIds (M3Model.ROOT, new long [] {30, 10, 20});
		assertEquals (2, m3.indexOf (20));
		assertEquals ("t1", m3.text (10, 0));
		assertNull (m3.image (10, 0));
		/* rows added outside the model make the ids positional again */
		new TableItem (table, SWT.NONE).setText ("t3");
		assertEquals (3, m3.indexOf (3));
		assertThrows (IllegalArgumentException.class, () -> m3.size (5));
		assertThrows (IllegalArgumentException.class, () -> m3.id (M3Model.ROOT, 4));
		assertThrows (IllegalArgumentException.class, () -> m3.id (M3Model.ROOT, -1));
		assertThrows (IllegalArgumentException.class, () -> m3.setIds (M3Model.ROOT, null));
		assertThrows (IllegalArgumentException.class, () -> m3.setIds (M3Model.ROOT, new long [2]));
		assertThrows (IllegalArgumentException.class, () -> m3.setIds (M3Model.ROOT, new long [] {1, 1, 2, 3}));
		assertThrows (IllegalArgumentException.class, () -> m3.text (99, 0));
		assertThrows (IllegalArgumentException.class, () -> m3.image (99, 0));
		assertThrows (IllegalArgumentException.class, () -> m3.apply (null, labels));
		assertThrows (IllegalArgumentException.class, () -> m3.apply (M3Patch.builder (M3Patch.ROOT).build (), null));
		assertThrows (IllegalArgumentException.class, () -> m3.apply (M3Patch.builder (4).build (), labels));
		assertThrows (IllegalArgumentException.class, () -> m3.apply (M3Patch.builder (M3Patch.ROOT).remove (3, 5).build (), labels));
		assertThrows (IllegalArgumentException.class, () -> m3.apply (M3Patch.builder (M3Patch.ROOT).insert (0, 2).build (), labels), "id 2 is in use");
		assertThrows (IllegalArgumentException.class, () -> M3Model.of ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> M3Model.of ((List) null));
		table.dispose ();
		assertThrows (SWTException.class, () -> m3.size (M3Model.ROOT));
	}
}
