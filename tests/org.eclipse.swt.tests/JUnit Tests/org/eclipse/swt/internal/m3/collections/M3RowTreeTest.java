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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 826e57b779174368fec5c4295eca9e3ad794c2e1,
 * com.m3.collections.M3RowTreeTest (same author; the SWT copy of the viewport collections).
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

/**
 * Oracle test of the row tree: seeded random edits on the tree and on a plain list, after every step all list and
 * row-index answers are compared with the naive answers computed from the plain list.
 */
public class M3RowTreeTest {
	static final class Item implements M3RowTreeItem {
		int slot = -1, height, outsideRow = -7, lastRow = -1;
		boolean visible = true;

		Item (int height) {
			this.height = height;
		}

		@Override
		public int getRowIndex () {
			return outsideRow;
		}

		@Override
		public int rowTreeSlot () {
			return slot;
		}

		@Override
		public void enteredRowTree (int slot) {
			this.slot = slot;
		}

		@Override
		public void leftRowTree (int lastRow) {
			this.slot = -1;
			this.lastRow = lastRow;
		}

		@Override
		public boolean isVisible () {
			return visible;
		}

		@Override
		public int getHeight () {
			return height;
		}
	}

	private static int extent (Item item) {
		return item.visible ? item.height + 1 : 0;
	}

	private static void check (M3RowTree tree, List<Item> model) {
		assertEquals (model.size (), tree.size ());
		long total = 0;
		int visible = 0;
		for (int i = 0; i < model.size (); i++) {
			Item item = model.get (i);
			assertSame (item, tree.get (i));
			assertEquals (i, tree.indexOf (item));
			assertEquals (i, tree.lastIndexOf (item));
			assertEquals (visible, tree.rankOf (i));
			assertEquals (total, tree.extentBefore (i));
			assertEquals (extent (item), tree.extentAt (i));
			if (item.visible) {
				assertEquals (i, tree.positionOfRank (visible));
				assertEquals (total, tree.extentOfRank (visible));
				assertEquals (visible, tree.rankAtExtent (total));
				visible++;
			}
			total += extent (item);
		}
		assertEquals (visible, tree.visibleCount ());
		assertEquals (total, tree.totalExtent ());
		assertEquals (visible, tree.rankAtExtent (total), "past the end");
		assertEquals (-1, tree.rankAtExtent (-1));
		if (visible > 0) assertEquals (total, tree.extentOfRank (visible));
		int finalVisible = visible;
		assertThrows (IndexOutOfBoundsException.class, () -> tree.positionOfRank (finalVisible));
		for (int p = 0; p < model.size (); p++) {
			int next = -1, previous = -1;
			for (int i = p; i < model.size (); i++) if (model.get (i).visible) { next = i; break; }
			for (int i = p; i >= 0; i--) if (model.get (i).visible) { previous = i; break; }
			assertEquals (next, tree.nextVisible (p));
			assertEquals (previous, tree.previousVisible (p));
		}
	}

	@Test
	public void randomEditsMatchThePlainList () {
		Random random = new Random (20261019L);
		M3RowTree tree = new M3RowTree ();
		List<Item> model = new ArrayList<> ();
		for (int step = 0; step < 1500; step++) {
			int op = random.nextInt (10);
			if (op < 4 || model.isEmpty ()) {
				int index = random.nextInt (model.size () + 1);
				Item item = new Item (random.nextInt (5));
				item.visible = random.nextInt (4) != 0;
				tree.add (index, item);
				model.add (index, item);
			} else if (op < 6) {
				int index = random.nextInt (model.size ());
				Item removed = model.remove (index);
				assertSame (removed, tree.remove (index));
				assertEquals (index, removed.lastRow);
				assertEquals (-1, removed.slot);
			} else if (op == 6) {
				Item item = model.remove (random.nextInt (model.size ()));
				assertTrue (tree.remove (item));
				assertFalse (tree.remove (item));
			} else if (op == 7) {
				int index = random.nextInt (model.size ());
				Item replacement = new Item (random.nextInt (7));
				Item old = model.set (index, replacement);
				assertSame (old, tree.set (index, replacement));
				assertEquals (-1, old.slot);
			} else {
				Item item = model.get (random.nextInt (model.size ()));
				item.visible = !item.visible;
				item.height = random.nextInt (9);
				tree.update (item);
			}
			if (step % 50 == 0 || model.size () < 6) check (tree, model);
		}
		check (tree, model);
		Item last = new Item (3);
		tree.add (last);
		tree.clear ();
		assertEquals (0, tree.size ());
		assertEquals (-1, last.slot, "clear tells every item it left");
		check (tree, new ArrayList<> ());
	}

	@Test
	public void lookupsRowsOfAndIteration () {
		M3RowTree tree = new M3RowTree ();
		List<Item> model = new ArrayList<> ();
		for (int i = 0; i < 40; i++) {
			Item item = new Item (2);
			tree.add (item);
			model.add (item);
		}
		Item outside = new Item (1);
		assertFalse (tree.contains (outside));
		assertFalse (tree.contains ("not an item"));
		assertEquals (-1, tree.indexOf (outside));
		assertFalse (tree.remove (outside));
		tree.update (outside);
		outside.slot = 3;
		assertFalse (tree.contains (outside), "a foreign slot number is not membership");
		outside.slot = 1000;
		assertEquals (-1, tree.indexOf (outside));
		outside.slot = -1;
		assertArrayEquals (new int [] {5, -7}, tree.rowsOf (List.of (model.get (5), outside)), "few items: per item");
		List<Item> many = new ArrayList<> (model.subList (0, 10));
		many.add (outside);
		int [] rows = tree.rowsOf (new ArrayList<> (many));
		assertEquals (9, rows [9]);
		assertEquals (-7, rows [10], "many items: one pass over the tree");
		Iterator<M3RowTreeItem> iterator = tree.iterator ();
		for (Item item : model) assertSame (item, iterator.next ());
		assertFalse (iterator.hasNext ());
		assertThrows (NoSuchElementException.class, iterator::next);
		Iterator<M3RowTreeItem> stale = tree.iterator ();
		tree.remove (0);
		assertThrows (ConcurrentModificationException.class, stale::next);
		assertThrows (IndexOutOfBoundsException.class, () -> tree.get (-1));
		assertThrows (IndexOutOfBoundsException.class, () -> tree.add (-1, new Item (1)));
		assertThrows (IndexOutOfBoundsException.class, () -> tree.add (100, new Item (1)));
		assertThrows (IndexOutOfBoundsException.class, () -> tree.remove (39));
		assertThrows (IndexOutOfBoundsException.class, () -> tree.set (39, new Item (1)));
		Item member = (Item) tree.get (3);
		assertTrue (tree.contains (member));
		long extent = tree.totalExtent ();
		tree.update (member);
		assertEquals (extent, tree.totalExtent (), "an unchanged item needs no reweigh");
		int seen = 0;
		for (Iterator<M3RowTreeItem> it = tree.iterator (); it.hasNext (); it.next ()) seen++;
		assertEquals (tree.size (), seen);
		M3RowTree empty = new M3RowTree ();
		assertFalse (empty.iterator ().hasNext ());
		Item single = new Item (1);
		empty.add (single);
		assertSame (single, empty.remove (0));
		assertEquals (0, empty.size (), "removing the only item empties the tree");
	}

	@Test
	public void warmHeapGrowthAndSampler () {
		long mib = 1024L * 1024L;
		assertThrows (IllegalArgumentException.class, () -> M3WarmHeapGrowth.choose (0, 1, 0, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> M3WarmHeapGrowth.choose (0, 1, 65, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> M3WarmHeapGrowth.choose (-1, 1, 4, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> M3WarmHeapGrowth.choose (0, -1, 4, 0, 0));
		assertEquals (10, M3WarmHeapGrowth.choose (10, 4, 4, 0, 0), "already large enough");
		assertEquals (16, M3WarmHeapGrowth.choose (0, 1, 4, 0, 0), "no heap limit: geometric");
		assertEquals (20, M3WarmHeapGrowth.choose (10, 11, 4, Long.MAX_VALUE, 0));
		assertEquals (20, M3WarmHeapGrowth.choose (10, 11, 4, 1000 * mib, -1));
		assertEquals (20, M3WarmHeapGrowth.choose (10, 11, 4, 1000 * mib, 1001 * mib));
		assertEquals (30, M3WarmHeapGrowth.choose (10, 30, 4, 1000 * mib, 0), "geometric equals minimum");
		assertEquals (20, M3WarmHeapGrowth.choose (10, 11, 4, 1000 * mib, 0), "cold heap: geometric");
		assertEquals (11, M3WarmHeapGrowth.choose (10, 11, 4, 1000 * mib, 990 * mib), "available below the reserve");
		assertEquals (11, M3WarmHeapGrowth.choose (10, 11, 4, 1000 * mib, 750 * mib), "warm heap: exact");
		assertEquals (2_000_000, M3WarmHeapGrowth.choose (1_000_000, 1_000_001, 64, 1000 * mib, 0), "the peak fits");
		assertEquals (8_000_001, M3WarmHeapGrowth.choose (8_000_000, 8_000_001, 64, 1000 * mib, 0), "peak beyond the free heap");
		assertEquals (Integer.MAX_VALUE - 7, M3WarmHeapGrowth.choose (10, Integer.MAX_VALUE - 7, 1, 0, 0), "minimum past the int cap");
		assertEquals (11, M3WarmHeapGrowth.choose (10, 11, 4, Long.MAX_VALUE - 1, Long.MAX_VALUE - 50), "forecast saturates");
		assertTrue (M3WarmHeapGrowth.capacity (10, 11, 4) >= 11);
		assertTrue (M3HeapSampler.maxMemory () > 0);
		assertTrue (M3HeapSampler.usedMemory () > 0);
	}
}
