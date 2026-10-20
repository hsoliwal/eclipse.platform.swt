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
import org.junit.jupiter.api.*;

/**
 * Contract of the M3Patch additions of the public model API (brief rule 26), headless: column masks, reorder and
 * reset sections, parent sections, eager values, and the id-addressed builder resolved against a model. The
 * id-addressed patches are checked against an oracle that edits the id lists directly.
 */
public class Test_M3PatchSections {

	/* a tree of ids in lists per parent; only what the builder reads is implemented */
	static final class IdModel implements M3TreeModel {
		final Map<Long, List<Long>> children = new HashMap<> ();
		final Map<Long, Long> parents = new HashMap<> ();
		long generation = 41;

		IdModel add (long parent, long... ids) {
			for (long id : ids) {
				children.computeIfAbsent (parent, p -> new ArrayList<> ()).add (id);
				parents.put (id, parent);
			}
			return this;
		}

		@Override public int indexOf (long id) {
			Long parent = parents.get (id);
			return parent == null ? -1 : children.get (parent).indexOf (id);
		}
		@Override public int slot (long id) { return parents.containsKey (id) ? (int) id : -1; }
		@Override public long parent (long id) { return parents.get (id); }
		@Override public long generation () { return generation; }
		@Override public int size () { throw new UnsupportedOperationException (); }
		@Override public long id (int row) { throw new UnsupportedOperationException (); }
		@Override public int slotAt (int row) { throw new UnsupportedOperationException (); }
		@Override public int rowOf (int slot) { throw new UnsupportedOperationException (); }
		@Override public int slotCapacity () { throw new UnsupportedOperationException (); }
		@Override public int order (long parent, int from, int count, int [] slots) { throw new UnsupportedOperationException (); }
		@Override public long sequence (int slot) { throw new UnsupportedOperationException (); }
		@Override public M3LabelTable labels () { throw new UnsupportedOperationException (); }
		@Override public int label (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public int text (int slot, int column, char [] buffer, int offset) { throw new UnsupportedOperationException (); }
		@Override public String text (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public int intAt (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public long longAt (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public double doubleAt (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public Image image (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public boolean flag (int slot, int flag) { throw new UnsupportedOperationException (); }
		@Override public long flagWord (int flag, int word) { throw new UnsupportedOperationException (); }
		@Override public M3StyleTable styles () { throw new UnsupportedOperationException (); }
		@Override public int style (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public int extent (int slot) { throw new UnsupportedOperationException (); }
		@Override public long apply (M3Patch patch) { throw new UnsupportedOperationException (); }
		@Override public boolean applyIfCurrent (M3Patch patch) { throw new UnsupportedOperationException (); }
		@Override public M3TreeModel snapshot () { throw new UnsupportedOperationException (); }
		@Override public void addModelListener (M3ModelListener listener) { throw new UnsupportedOperationException (); }
		@Override public void removeModelListener (M3ModelListener listener) { throw new UnsupportedOperationException (); }
		@Override public void viewportVisibility (Object viewport, boolean visible) { throw new UnsupportedOperationException (); }
		@Override public int visibleViewports () { throw new UnsupportedOperationException (); }
		@Override public int columnCount () { throw new UnsupportedOperationException (); }
		@Override public M3Column column (int index) { throw new UnsupportedOperationException (); }
		@Override public int childCount (long parent) { throw new UnsupportedOperationException (); }
		@Override public long childId (long parent, int index) { throw new UnsupportedOperationException (); }
		@Override public int depth (long id) { throw new UnsupportedOperationException (); }
		@Override public int parentSlot (int slot) { throw new UnsupportedOperationException (); }
		@Override public long visibleRowCount () { throw new UnsupportedOperationException (); }
		@Override public long visibleRow (long id) { throw new UnsupportedOperationException (); }
		@Override public long idAtVisibleRow (long row) { throw new UnsupportedOperationException (); }
	}

	/* a flat model: only indexOf, slot and generation */
	static final class FlatModel implements M3ListModel {
		final IdModel ids = new IdModel ();

		@Override public int indexOf (long id) { return ids.indexOf (id); }
		@Override public int slot (long id) { return ids.slot (id); }
		@Override public long generation () { return 7; }
		@Override public int size () { throw new UnsupportedOperationException (); }
		@Override public long id (int row) { throw new UnsupportedOperationException (); }
		@Override public int slotAt (int row) { throw new UnsupportedOperationException (); }
		@Override public int rowOf (int slot) { throw new UnsupportedOperationException (); }
		@Override public int slotCapacity () { throw new UnsupportedOperationException (); }
		@Override public int order (long parent, int from, int count, int [] slots) { throw new UnsupportedOperationException (); }
		@Override public long sequence (int slot) { throw new UnsupportedOperationException (); }
		@Override public M3LabelTable labels () { throw new UnsupportedOperationException (); }
		@Override public int label (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public int text (int slot, int column, char [] buffer, int offset) { throw new UnsupportedOperationException (); }
		@Override public String text (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public int intAt (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public long longAt (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public double doubleAt (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public Image image (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public boolean flag (int slot, int flag) { throw new UnsupportedOperationException (); }
		@Override public long flagWord (int flag, int word) { throw new UnsupportedOperationException (); }
		@Override public M3StyleTable styles () { throw new UnsupportedOperationException (); }
		@Override public int style (int slot, int column) { throw new UnsupportedOperationException (); }
		@Override public int extent (int slot) { throw new UnsupportedOperationException (); }
		@Override public long apply (M3Patch patch) { throw new UnsupportedOperationException (); }
		@Override public boolean applyIfCurrent (M3Patch patch) { throw new UnsupportedOperationException (); }
		@Override public M3ListModel snapshot () { throw new UnsupportedOperationException (); }
		@Override public void addModelListener (M3ModelListener listener) { throw new UnsupportedOperationException (); }
		@Override public void removeModelListener (M3ModelListener listener) { throw new UnsupportedOperationException (); }
		@Override public void viewportVisibility (Object viewport, boolean visible) { throw new UnsupportedOperationException (); }
		@Override public int visibleViewports () { throw new UnsupportedOperationException (); }
	}

	/* records the calls of a target */
	static class Recorder implements M3Patch.Target {
		final StringBuilder log = new StringBuilder ();

		@Override public void remove (int index, int count) { log.append ("-").append (index).append (':').append (count).append (' '); }
		@Override public void insert (int index, int count) { log.append ("+").append (index).append (':').append (count).append (' '); }
		@Override public void update (int index, int count) { log.append ("~").append (index).append (':').append (count).append (' '); }
	}

	/* records the replayed values */
	static final class Values implements M3CellWriter {
		final StringBuilder log = new StringBuilder ();

		private M3CellWriter add (String what) {
			log.append (what).append (';');
			return this;
		}
		@Override public M3CellWriter text (int column, CharSequence text) { return add ("text " + column + " " + text); }
		@Override public M3CellWriter label (int column, int labelId) { return add ("label " + column + " " + labelId); }
		@Override public M3CellWriter intValue (int column, int value) { return add ("int " + column + " " + value); }
		@Override public M3CellWriter longValue (int column, long value) { return add ("long " + column + " " + value); }
		@Override public M3CellWriter doubleValue (int column, double value) { return add ("double " + column + " " + value); }
		@Override public M3CellWriter image (int column, Image image) { return add ("image " + column + " " + image); }
		@Override public M3CellWriter flag (int flag, boolean value) { return add ("flag " + flag + " " + value); }
		@Override public M3CellWriter style (int column, int styleId) { return add ("style " + column + " " + styleId); }
		@Override public M3CellWriter extent (int extent) { return add ("extent " + extent); }
	}

	@Test
	public void test_columnMasks () {
		M3Patch patch = M3Patch.builder (M3Patch.ROOT).update (0, 2, 1L << 3 | M3Patch.FLAGS).update (5, 1).build ();
		assertEquals (1L << 3 | M3Patch.FLAGS, patch.updateColumns (0));
		assertEquals (M3Patch.ALL, patch.updateColumns (1));
		assertEquals (M3Patch.ALL, M3Patch.builder (M3Patch.ROOT).update (1, 1).build ().updateColumns (0), "no mask given: ALL");
		assertEquals (M3Patch.RUNS, patch.kind ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).update (0, 1, 0));
		assertNotEquals (M3Patch.FLAGS, M3Patch.STYLE);
		assertNotEquals (M3Patch.EXTENT, M3Patch.PARENT);
	}

	@Test
	public void test_reorder () {
		M3Patch patch = M3Patch.builder (M3Patch.ROOT).reorder (new int [] {2, 0, 1}).build ();
		assertEquals (M3Patch.REORDER, patch.kind ());
		assertEquals (3, patch.reorderSize ());
		assertEquals (2, patch.reorderedRow (0));
		assertArrayEquals (new long [] {30, 10, 20}, patch.apply (new long [] {10, 20, 30}));
		assertThrows (IllegalArgumentException.class, () -> patch.apply (new long [] {10, 20}));
		Recorder recorder = new Recorder ();
		patch.applyTo (recorder);
		assertEquals ("~0:3 ", recorder.log.toString (), "the default reorder marks every row updated");
		M3Patch.builder (M3Patch.ROOT).reorder (new int [0]).build ().applyTo (recorder);
		assertEquals ("~0:3 ", recorder.log.toString (), "an empty reorder updates nothing");
		assertFalse (patch.isEmpty ());
		assertTrue (patch.toString ().contains ("reorder 3"), patch.toString ());
		assertThrows (IllegalArgumentException.class, () -> patch.reorderedRow (3));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (null));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (new int [] {0, 0}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (new int [] {0, 2}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (new int [] {-1}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).update (0, 1).reorder (new int [] {0}));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (new int [] {0}).remove (0, 1));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (new int [] {0}).insert (0, 5));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reorder (new int [] {0}).update (0, 1));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).build ().reorderedRow (0));
		assertEquals (0, M3Patch.builder (M3Patch.ROOT).build ().reorderSize ());
	}

	@Test
	public void test_reset () {
		M3Patch patch = M3Patch.builder (M3Patch.ROOT).reset (7, 8, 9).build ();
		assertEquals (M3Patch.RESET, patch.kind ());
		assertEquals (3, patch.resetSize ());
		assertEquals (8, patch.resetId (1));
		assertArrayEquals (new long [] {7, 8, 9}, patch.apply (new long [] {1, 2}));
		assertThrows (SWTError.class, () -> patch.applyTo (new Recorder ()), "a target that cannot reset reports it");
		int [] count = {-1};
		patch.applyTo (new Recorder () {
			@Override public void reset (int rows) { count [0] = rows; }
		});
		assertEquals (3, count [0]);
		assertTrue (patch.toString ().contains ("reset 3"));
		assertFalse (patch.isEmpty ());
		assertThrows (IllegalArgumentException.class, () -> patch.resetId (3));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).build ().resetId (0));
		assertEquals (0, M3Patch.builder (M3Patch.ROOT).build ().resetSize ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reset ((long []) null));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).reset (1, 1));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).remove (0, 1).reset (1));
	}

	@Test
	public void test_sectionsPerParent () {
		M3Patch patch = M3Patch.builder (M3Patch.ROOT).insert (0, 1).parent (1).insert (0, 10, 11).parent (2).update (0, 1).build ();
		assertEquals (3, patch.sections ());
		assertEquals (M3Patch.ROOT, patch.parent (), "the patch reads as section 0");
		assertEquals (1, patch.section (1).parent ());
		assertEquals (2, patch.section (1).insertedCount ());
		assertEquals (1, patch.section (1).sections ());
		assertSame (patch.section (2), patch.section (2).section (0));
		assertFalse (patch.isEmpty ());
		assertTrue (patch.toString ().contains ("3 sections"), patch.toString ());
		assertThrows (IllegalArgumentException.class, () -> patch.section (3));
		assertThrows (IllegalArgumentException.class, () -> patch.section (-1));
		M3Patch replaced = M3Patch.builder (M3Patch.ROOT).parent (5).update (0, 1).build ();
		assertEquals (1, replaced.sections (), "an empty section is replaced, not kept");
		assertEquals (5, replaced.parent ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (1).update (0, 1).parent (2).update (0, 1).parent (1).update (1, 1).build (),
				"two sections of one parent");
		M3Patch empty = M3Patch.builder (3).parent (4).parent (5).build ();
		assertTrue (empty.isEmpty ());
		assertEquals (5, empty.parent ());
		M3Patch twoEmpty = M3Patch.builder (3).update (0, 1).parent (4).reorder (new int [0]).build ();
		assertFalse (twoEmpty.isEmpty (), "a reorder section is content");
		M3Patch quiet = M3Patch.builder (M3Patch.ROOT).update (0, 1).parent (1).update (0, 1).build ();
		assertFalse (quiet.isEmpty ());
	}

	@Test
	public void test_eagerValues () {
		M3Patch.Builder builder = M3Patch.builder (M3Patch.ROOT).insert (0, 7, 5, 3);
		builder.values (7).text (0, new StringBuilder ("seven")).label (1, 9).intValue (2, 7).longValue (3, 70L).doubleValue (4, 7.5)
			.image (5, null).flag (M3ListModel.CHECKED, true).style (-1, 3).extent (20);
		builder.values (5).text (0, "five");
		builder.values (3).text (0, null);
		builder.values (5).intValue (2, 5);
		M3Patch patch = builder.build ();
		assertTrue (patch.hasValues (7));
		assertTrue (patch.hasValues (3));
		assertFalse (patch.hasValues (8));
		Values out = new Values ();
		patch.values (7, out);
		assertEquals ("text 0 seven;label 1 9;int 2 7;long 3 70;double 4 7.5;image 5 null;flag 0 true;style -1 3;extent 20;", out.log.toString ());
		out.log.setLength (0);
		patch.values (5, out);
		assertEquals ("text 0 five;int 2 5;", out.log.toString (), "one row's values in the order they were written");
		out.log.setLength (0);
		patch.values (8, out);
		assertEquals ("", out.log.toString ());
		assertThrows (IllegalArgumentException.class, () -> patch.values (7, null));
		M3Patch plain = M3Patch.builder (M3Patch.ROOT).insert (0, 1).build ();
		assertFalse (plain.hasValues (1));
		plain.values (1, out);
		assertEquals ("", out.log.toString ());
		M3CellWriter writer = M3Patch.builder (M3Patch.ROOT).values (1);
		assertThrows (IllegalArgumentException.class, () -> writer.flag (64, true));
		assertThrows (IllegalArgumentException.class, () -> writer.flag (-1, true));
		assertThrows (IllegalArgumentException.class, () -> writer.extent (-1));
		assertThrows (IllegalArgumentException.class, () -> writer.text (-2, "x"));
		M3Patch.Builder many = M3Patch.builder (M3Patch.ROOT);
		for (long id = 100; id > 0; id--) many.values (id).intValue (0, (int) id);
		M3Patch grown = many.build ();
		out.log.setLength (0);
		grown.values (42, out);
		assertEquals ("int 0 42;", out.log.toString ());
		assertFalse (many.build ().hasValues (42), "build empties the builder");
	}

	@Test
	public void test_idAddressedFlat () {
		FlatModel model = new FlatModel ();
		model.ids.add (M3Patch.ROOT, 10, 20, 30, 40, 50);
		M3Patch patch = M3Patch.builder (model).removeId (20).removeId (30).insertId (M3Patch.ROOT, 0, 99).updateId (50, 1L)
				.moveId (10, M3Patch.ROOT, 3).build ();
		assertArrayEquals (new long [] {99, 40, 50, 10}, patch.apply (new long [] {10, 20, 30, 40, 50}));
		assertEquals (7, patch.baseGeneration ());
		assertEquals (1, patch.moveCount ());
		assertEquals (0, patch.moveFrom (0));
		assertEquals (3, patch.moveTo (0));
		assertEquals (1, patch.updateRuns ());
		assertEquals (2, patch.updateAt (0));
		assertEquals (1L, patch.updateColumns (0));
		assertEquals (0, M3Patch.builder (M3Patch.ROOT).build ().baseGeneration (), "a free builder has no base");
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).insertId (5, 0, 77).build (), "a flat model has no parent 5");
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).removeId (11).build ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).removeId (10).removeId (10).build ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).insertId (M3Patch.ROOT, 0, 40).build (), "present and not removed");
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).insertId (M3Patch.ROOT, 0, M3Patch.ROOT).build ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).insertId (M3Patch.ROOT, 0, 77).insertId (M3Patch.ROOT, 1, 77).build ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).insertId (M3Patch.ROOT, 1, 77).insertId (M3Patch.ROOT, 1, 78).build (),
				"two inserts at one index");
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).updateId (11, 1).build ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).removeId (10).updateId (10, 1).build ());
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).updateId (10, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).insertId (M3Patch.ROOT, -1, 77));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).moveId (10, M3Patch.ROOT, -1));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).moveId (11, M3Patch.ROOT, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (M3Patch.ROOT).removeId (10), "a free builder takes no ids");
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder ((M3ListModel) null));
		assertThrows (IllegalArgumentException.class, () -> M3Patch.builder (model).update (0, 1).updateId (40, 1).build (),
				"an explicit section and an id section of one parent");
		M3Patch mixed = M3Patch.builder (model).parent (5).update (0, 1).updateId (40, 2).build ();
		assertEquals (2, mixed.sections ());
		M3Patch onlyIds = M3Patch.builder (model).updateId (40, 2).updateId (40, 4).updateId (50, 1).build ();
		assertEquals (1, onlyIds.sections (), "the empty first section gives way to the id section");
		assertEquals (2, onlyIds.updateRuns (), "different masks are different runs");
		assertEquals (6L, onlyIds.updateColumns (0), "masks of one row merge");
		M3Patch nothing = M3Patch.builder (model).build ();
		assertTrue (nothing.isEmpty ());
	}

	@Test
	public void test_idAddressedRandomAgainstOracle () {
		Random random = new Random (2610);
		for (int round = 0; round < 400; round++) {
			FlatModel model = new FlatModel ();
			int size = random.nextInt (30);
			long [] before = new long [size];
			for (int i = 0; i < size; i++) before [i] = 1000 + i;
			model.ids.add (M3Patch.ROOT, before);
			List<Long> after = new ArrayList<> ();
			for (long id : before) after.add (id);
			M3Patch.Builder builder = M3Patch.builder (model);
			Set<Long> removed = new HashSet<> ();
			for (long id : before) {
				if (random.nextInt (4) == 0) {
					builder.removeId (id);
					removed.add (id);
					after.remove (id);
				}
			}
			/* inserts at final indices: build the final list first, then name each new id's final index */
			int inserts = random.nextInt (6);
			for (int k = 0; k < inserts; k++) after.add (random.nextInt (after.size () + 1), 5000L + k);
			for (int index = 0; index < after.size (); index++) {
				long id = after.get (index);
				if (id >= 5000) builder.insertId (M3Patch.ROOT, index, id);
			}
			Map<Integer, Long> updates = new TreeMap<> ();
			for (int index = 0; index < after.size (); index++) {
				long id = after.get (index);
				if (id < 5000 && random.nextInt (3) == 0) {
					long mask = 1L << random.nextInt (2);
					builder.updateId (id, mask);
					updates.put (index, mask);
				}
			}
			M3Patch patch = builder.build ();
			long [] expected = after.stream ().mapToLong (Long::longValue).toArray ();
			assertArrayEquals (expected, patch.apply (before), "round " + round);
			Map<Integer, Long> got = new TreeMap<> ();
			for (int i = 0; i < patch.updateRuns (); i++) {
				for (int k = 0; k < patch.updateCount (i); k++) got.put (patch.updateAt (i) + k, patch.updateColumns (i));
			}
			assertEquals (updates, got, "round " + round);
		}
	}

	@Test
	public void test_idAddressedTree () {
		IdModel model = new IdModel ().add (M3Patch.ROOT, 1, 2).add (1, 11, 12, 13).add (2, 21);
		M3Patch patch = M3Patch.builder (model).moveId (12, 2, 0).updateId (13, M3Patch.STYLE).insertId (1, 0, 10).build ();
		assertEquals (2, patch.sections ());
		M3Patch one = patch.section (0), two = patch.section (1);
		assertEquals (1, one.parent ());
		assertArrayEquals (new long [] {10, 11, 13}, one.apply (new long [] {11, 12, 13}));
		assertEquals (2, one.updateAt (0), "13 ends at index 2 of parent 1");
		assertEquals (M3Patch.STYLE, one.updateColumns (0));
		assertEquals (2, two.parent ());
		assertArrayEquals (new long [] {12, 21}, two.apply (new long [] {21}));
		assertEquals (0, two.updateAt (0));
		assertEquals (M3Patch.PARENT, two.updateColumns (0), "a reparent carries PARENT");
		assertEquals (0, one.moveCount (), "a reparent is no move inside one parent");
		assertEquals (41, patch.baseGeneration ());
		assertEquals (41, two.baseGeneration (), "sections share the base generation");
		M3Patch within = M3Patch.builder (model).moveId (13, 1, 0).build ();
		assertArrayEquals (new long [] {13, 11, 12}, within.apply (new long [] {11, 12, 13}));
		assertEquals (1, within.moveCount ());
		M3Patch.Builder reused = M3Patch.builder (model);
		reused.insertId (2, 1, 22);
		assertEquals (1, reused.build ().insertedCount ());
		assertTrue (reused.build ().isEmpty (), "build empties the builder");
		reused.insertId (2, 1, 23);
		reused.clear ();
		assertTrue (reused.build ().isEmpty ());
	}
}
