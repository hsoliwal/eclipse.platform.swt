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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3GridSelectionsTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Oracle tests of the position-marked selection and the cell selection counts against plain lists. */
public class M3GridSelectionsTest {
	static final class Item implements M3SelectionItem {
		int position = -1;
		final int id;

		Item (int id) {
			this.id = id;
		}

		@Override
		public int selectionPosition () {
			return position;
		}

		@Override
		public void markSelectionPosition (int position) {
			this.position = position;
		}

		@Override
		public String toString () {
			return "Item" + id;
		}
	}

	private static void check (M3PositionMarkedSelection selection, List<Item> model, List<Item> all) {
		assertEquals (model.size (), selection.size ());
		for (int i = 0; i < model.size (); i++) assertSame (model.get (i), selection.get (i));
		for (Item item : all) {
			assertEquals (model.indexOf (item), selection.indexOf (item));
			assertEquals (model.contains (item), selection.contains (item));
			assertEquals (model.indexOf (item), selection.lastIndexOf (item));
		}
		List<M3SelectionItem> iterated = new ArrayList<> ();
		selection.forEach (iterated::add);
		assertEquals (new ArrayList<M3SelectionItem> (model), iterated);
		assertArrayEquals (model.toArray (), selection.toArray ());
	}

	@Test
	public void positionMarkedSelectionMatchesArrayList () {
		Random random = new Random (19L);
		List<Item> all = new ArrayList<> ();
		for (int i = 0; i < 60; i++) all.add (new Item (i));
		M3PositionMarkedSelection selection = new M3PositionMarkedSelection ();
		List<Item> model = new ArrayList<> ();
		for (int step = 0; step < 3000; step++) {
			Item item = all.get (random.nextInt (all.size ()));
			int op = random.nextInt (8);
			if (op < 3 && !model.contains (item)) {
				selection.add (item);
				model.add (item);
			} else if (op == 3 && !model.contains (item)) {
				int index = random.nextInt (model.size () + 1);
				selection.add (index, item);
				model.add (index, item);
			} else if (op == 4) {
				assertEquals (model.remove (item), selection.remove (item));
			} else if (op == 5 && !model.isEmpty ()) {
				int index = random.nextInt (model.size ());
				assertSame (model.remove (index), selection.remove (index));
			} else if (op == 6 && !model.isEmpty () && !model.contains (item)) {
				int index = random.nextInt (model.size ());
				assertSame (model.set (index, item), selection.set (index, item));
			} else if (op == 7 && random.nextInt (40) == 0) {
				selection.clear ();
				model.clear ();
			}
			if (!model.isEmpty () && random.nextBoolean ()) assertSame (model.get (0), selection.get (0));
			if (step % 25 == 0) check (selection, model, all);
		}
		check (selection, model, all);
	}

	@Test
	public void positionMarkedSelectionEdges () {
		M3PositionMarkedSelection selection = new M3PositionMarkedSelection ();
		assertFalse (selection.addAll (List.of ()));
		List<Item> items = new ArrayList<> ();
		for (int i = 0; i < 40; i++) items.add (new Item (i));
		assertTrue (selection.addAll (items));
		assertFalse (selection.contains ("other"));
		assertEquals (-1, selection.indexOf ("other"));
		assertFalse (selection.remove ("other"));
		Item stranger = new Item (99);
		stranger.position = 3;
		assertFalse (selection.contains (stranger), "a position inside the window but another item");
		stranger.position = 1000;
		assertFalse (selection.contains (stranger), "a foreign position is not membership");
		for (int i = 0; i < 30; i++) assertTrue (selection.remove (items.get (i)));
		assertEquals (10, selection.size ());
		assertSame (items.get (30), selection.get (0), "get(0) skips the holes");
		M3SelectionItem [] big = selection.toArray (new M3SelectionItem [12]);
		assertNull (big [10], "a larger array is terminated with null");
		M3SelectionItem [] exact = selection.toArray (new M3SelectionItem [0]);
		assertEquals (10, exact.length);
		Iterator<M3SelectionItem> iterator = selection.iterator ();
		for (int i = 0; i < 10; i++) iterator.next ();
		assertFalse (iterator.hasNext ());
		assertThrows (NoSuchElementException.class, iterator::next);
		Iterator<M3SelectionItem> stale = selection.iterator ();
		selection.add (new Item (100));
		assertThrows (ConcurrentModificationException.class, stale::next);
		assertThrows (IndexOutOfBoundsException.class, () -> selection.get (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> selection.get (11));
		assertThrows (IndexOutOfBoundsException.class, () -> selection.add (-1, new Item (101)));
		assertThrows (IndexOutOfBoundsException.class, () -> selection.add (20, new Item (102)));
		for (int i = 30; i < 40; i++) selection.remove (items.get (i));
		assertEquals (1, selection.size ());
		selection.clear ();
		assertTrue (selection.isEmpty ());
		Item only = new Item (7);
		selection.add (only);
		selection.remove (only);
		assertEquals (-1, only.position);
		assertTrue (selection.isEmpty (), "removing the last item resets the window");
	}

	@Test
	public void cellSelectionCountsMatchArrayList () {
		Random random = new Random (7L);
		M3CellSelectionCounts cells = new M3CellSelectionCounts ();
		List<M3CellPoint> model = new ArrayList<> ();
		for (int step = 0; step < 2000; step++) {
			M3CellPoint p = new M3CellPoint (random.nextInt (5), random.nextInt (5));
			switch (random.nextInt (9)) {
				case 0, 1 -> { cells.add (p); model.add (new M3CellPoint (p.x, p.y)); }
				case 2 -> { int i = random.nextInt (model.size () + 1); cells.add (i, p); model.add (i, new M3CellPoint (p.x, p.y)); }
				case 3 -> assertEquals (model.remove (p), cells.remove (p));
				case 4 -> { if (!model.isEmpty ()) { int i = random.nextInt (model.size ()); assertEquals (model.set (i, new M3CellPoint (p.x, p.y)), cells.set (i, p)); } }
				case 5 -> { if (!model.isEmpty ()) { int i = random.nextInt (model.size ()); assertEquals (model.remove (i), cells.remove (i)); } }
				case 6 -> { List<M3CellPoint> c = List.of (p, new M3CellPoint (p.y, p.x)); assertEquals (model.removeAll (c), cells.removeAll (c)); }
				case 7 -> { if (random.nextInt (20) == 0) { List<M3CellPoint> c = List.of (p); assertEquals (model.retainAll (c), cells.retainAll (c)); } }
				default -> { List<M3CellPoint> c = List.of (p, new M3CellPoint (p.x + 1, p.y)); int i = random.nextInt (model.size () + 1); assertEquals (model.addAll (i, c), cells.addAll (i, c)); }
			}
			assertEquals (model, cells);
			assertEquals (model.contains (p), cells.contains (p));
		}
		assertFalse (cells.contains ("other"));
		assertTrue (cells.containsAll (List.of ()));
		assertFalse (cells.containsAll (List.of (new M3CellPoint (50, 50))));
		assertFalse (cells.addAll (List.of ()));
		assertFalse (cells.addAll (0, List.of ()));
		assertFalse (cells.removeAll (List.of ()));
		assertFalse (cells.remove ("other"));
		cells.clear ();
		assertFalse (cells.removeAll (List.of (new M3CellPoint (1, 1))), "empty list");
		M3CellPoint original = new M3CellPoint (2, 3);
		assertTrue (cells.addAll (List.of (original)));
		assertTrue (cells.containsAll (List.of (new M3CellPoint (2, 3))));
		original.x = 9;
		assertTrue (cells.contains (new M3CellPoint (2, 3)), "the list keeps its own copy");
		M3CellPoint [] out = cells.toArray (new M3CellPoint [0]);
		out [0].y = 8;
		assertTrue (cells.contains (new M3CellPoint (2, 3)), "toArray hands out copies");
		cells.get (0).x = 4;
		cells.add (new M3CellPoint (6, 6));
		cells.get (1).x = 7;
		assertEquals (new M3CellPoint (7, 6), cells.remove (1), "removing a point changed in place before reindex");
		assertTrue (cells.contains (new M3CellPoint (6, 6)), "a count changed in place stays until reindex");
		cells.reindex ();
		assertTrue (cells.contains (new M3CellPoint (4, 3)), "reindex after an in-place change");
		assertFalse (cells.contains (new M3CellPoint (6, 6)));
		assertFalse (cells.removeAll (List.of ("not a point")));
		assertFalse (cells.retainAll (List.of (new M3CellPoint (4, 3), "not a point")));
		assertEquals ("M3CellPoint {4, 3}", cells.get (0).toString ());
		assertNotEquals (new M3CellPoint (1, 2), "x");
		assertEquals (new M3CellPoint (1, 2).hashCode (), 1 ^ 2);
	}
}
