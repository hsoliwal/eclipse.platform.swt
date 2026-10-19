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
package org.eclipse.swt.widgets;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;

/**
 * The sparse item storage the Tree viewport keeps its materialized children in (a copy of the SWT viewport's
 * <code>VirtualItemStorage</code>, see PROVENANCE.tsv). Headless.
 */
public class VirtualItemStorageTest {
	private VirtualItemStorage<String> storage;
	private final List<String> removed = new ArrayList<> ();

	@Before
	public void setUp () {
		storage = new VirtualItemStorage<> ();
		for (int index : new int [] {10, 2, 30, 7, 20}) {
			storage.put (index, ("v" + index).intern ());
		}
	}

	private List<String> indexed () {
		List<String> all = new ArrayList<> ();
		storage.forEachIndexed ((index, value) -> all.add (index + "=" + value));
		return all;
	}

	@Test
	public void putGetAndOrder () {
		assertEquals (5, storage.size ());
		assertFalse (storage.isEmpty ());
		assertEquals ("v7", storage.get (7));
		assertNull (storage.get (8));
		assertNull (storage.get (-1));
		storage.put (7, "w7");
		assertEquals ("w7", storage.get (7));
		assertEquals (List.of ("2=v2", "7=w7", "10=v10", "20=v20", "30=v30"), indexed ());
		assertEquals (2, storage.indexAt (0));
		assertEquals ("v30", storage.valueAt (4));
		assertThrows (IndexOutOfBoundsException.class, () -> storage.valueAt (5));
		assertThrows (IllegalArgumentException.class, () -> storage.put (-1, "x"));
		assertThrows (NullPointerException.class, () -> storage.put (1, null));
		assertTrue (new VirtualItemStorage<String> ().isEmpty ());
	}

	@Test
	public void insertShiftsLaterIndices () {
		storage.insert (7, "n7");
		assertEquals (List.of ("2=v2", "7=n7", "8=v7", "11=v10", "21=v20", "31=v30"), indexed ());
		storage.insert (100, "end");
		assertEquals (100, storage.indexOfIdentity ("end"));
		assertThrows (IllegalArgumentException.class, () -> storage.insert (-1, "x"));
		assertThrows (NullPointerException.class, () -> storage.insert (1, null));
		VirtualItemStorage<String> full = new VirtualItemStorage<> ();
		full.put (Integer.MAX_VALUE, "max");
		assertThrows (IllegalArgumentException.class, () -> full.insert (0, "x"));
	}

	@Test
	public void removeShiftsLaterIndices () {
		assertEquals ("v7", storage.remove (7));
		assertEquals (List.of ("2=v2", "9=v10", "19=v20", "29=v30"), indexed ());
		assertNull (storage.remove (0));
		assertEquals ("a removal before the first item shifts every index", List.of ("1=v2", "8=v10", "18=v20", "28=v30"), indexed ());
		assertEquals ("v30", storage.remove (28));
		assertThrows (IllegalArgumentException.class, () -> storage.remove (-1));
	}

	@Test
	public void removeRangeDropsAndShifts () {
		storage.removeRange (5, 5, removed::add);
		storage.removeRange (5, 15, removed::add);
		assertEquals (List.of ("v7", "v10"), removed);
		assertEquals (List.of ("2=v2", "10=v20", "20=v30"), indexed ());
		storage.removeRange (15, 40, removed::add);
		assertEquals (List.of ("2=v2", "10=v20"), indexed ());
		assertThrows (IllegalArgumentException.class, () -> storage.removeRange (-1, 2, removed::add));
		assertThrows (IllegalArgumentException.class, () -> storage.removeRange (3, 2, removed::add));
		assertThrows (NullPointerException.class, () -> storage.removeRange (0, 2, null));
	}

	@Test
	public void truncateClearAndForEach () {
		storage.truncate (10, removed::add);
		assertEquals (List.of ("v10", "v20", "v30"), removed);
		assertEquals (2, storage.size ());
		List<String> values = new ArrayList<> ();
		storage.forEach (values::add);
		assertEquals (List.of ("v2", "v7"), values);
		assertThrows (IllegalArgumentException.class, () -> storage.truncate (-1, removed::add));
		assertThrows (NullPointerException.class, () -> storage.truncate (0, null));
		removed.clear ();
		storage.clear (removed::add);
		assertEquals (List.of ("v2", "v7"), removed);
		assertTrue (storage.isEmpty ());
		assertThrows (NullPointerException.class, () -> storage.clear (null));
		assertThrows (NullPointerException.class, () -> storage.forEach (null));
		assertThrows (NullPointerException.class, () -> storage.forEachIndexed (null));
	}

	@Test
	public void stateLanesFollowTheItems () {
		storage.state (10, 0b101L);
		assertEquals (0b101L, storage.state (10));
		assertEquals (0L, storage.state (11));
		assertTrue (storage.flag (10, 0b100L));
		assertFalse (storage.flag (10, 0b010L));
		storage.flag (10, 0b010L, true);
		storage.flag (10, 0b001L, false);
		assertEquals (0b110L, storage.state (10));
		assertThrows (IllegalStateException.class, () -> storage.state (11, 1L));
		assertThrows (IllegalStateException.class, () -> storage.flag (11, 1L, true));
		storage.insert (0, "first");
		assertEquals ("state moves with the item", 0b110L, storage.state (11));
		assertEquals (0b110L, storage.stateOfIdentity ("v10"));
		assertEquals (0L, storage.stateOfIdentity ("absent"));
		assertTrue (storage.flagOfIdentity ("v10", 0b010L));
		storage.flagOfIdentity ("v10", 0b001L, true);
		storage.flagOfIdentity ("v10", 0b100L, false);
		assertEquals (0b011L, storage.stateOfIdentity ("v10"));
		assertThrows (IllegalStateException.class, () -> storage.flagOfIdentity ("absent", 1L, true));
		assertEquals (-1, storage.indexOfIdentity ("absent"));
	}

	@Test
	public void releaseOutsideKeepsTheWindowAndPinnedItems () {
		storage.state (30, 0b1L);
		int dropped = storage.releaseOutside (5, 15, 0b1L, removed::add);
		assertEquals (2, dropped);
		assertEquals (List.of ("v2", "v20"), removed);
		assertEquals (List.of ("7=v7", "10=v10", "30=v30"), indexed ());
		assertEquals (0b1L, storage.state (30));
		assertThrows (NullPointerException.class, () -> storage.releaseOutside (0, 1, 0L, null));
	}

	@Test
	public void edgesOfInsertReleaseAndFlags () {
		VirtualItemStorage<String> empty = new VirtualItemStorage<> ();
		empty.insert (0, "a");
		assertEquals ("a", empty.get (0));
		int dropped = storage.releaseOutside (0, 8, 0L, removed::add);
		assertEquals (3, dropped);
		assertEquals (List.of ("2=v2", "7=v7"), indexed ());
		storage.flagOfIdentity ("v7", 0b1L, true);
		assertTrue (storage.flagOfIdentity ("v7", 0b1L));
		assertFalse (storage.flagOfIdentity ("v7", 0b10L));
	}

	@Test
	public void capacityGrows () {
		VirtualItemStorage<Integer> big = new VirtualItemStorage<> ();
		for (int i = 0; i < 1000; i++) {
			big.put (i * 2, i);
		}
		assertEquals (1000, big.size ());
		assertEquals (Integer.valueOf (500), big.get (1000));
	}
}
