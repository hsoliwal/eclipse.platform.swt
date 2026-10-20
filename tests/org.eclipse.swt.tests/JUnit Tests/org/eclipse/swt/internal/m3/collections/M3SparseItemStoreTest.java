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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 118b3cb7e59ea4d4cc32468dccfc6f32e5715dcb,
 * com.m3.collections.M3SparseItemStoreTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct tests of the sparse identity-preserving item store with shifting logical indices and state masks. */
public class M3SparseItemStoreTest {
	private static M3SparseItemStore<String> store (int... indices) {
		M3SparseItemStore<String> store = new M3SparseItemStore<> ();
		for (int index : indices) store.put (index, "v" + index);
		return store;
	}

	private static List<Integer> indices (M3SparseItemStore<String> store) {
		List<Integer> out = new ArrayList<> ();
		store.forEachIndexed ((index, value) -> out.add (index));
		return out;
	}

	@Test
	public void putGetAndGrowth () {
		M3SparseItemStore<String> store = new M3SparseItemStore<> ();
		assertTrue (store.isEmpty ());
		assertNull (store.get (-1));
		assertNull (store.get (3));
		for (int i = 9; i >= 0; i--) store.put (i * 10, "v" + i);
		assertEquals (10, store.size ());
		assertFalse (store.isEmpty ());
		assertEquals ("v4", store.get (40));
		assertNull (store.get (41));
		String replacement = "w";
		store.put (40, replacement);
		assertSame (replacement, store.get (40));
		assertEquals (10, store.size ());
		assertEquals (0, store.indexAt (0));
		assertEquals ("v9", store.valueAt (9));
		assertThrows (IndexOutOfBoundsException.class, () -> store.indexAt (10));
		assertThrows (IndexOutOfBoundsException.class, () -> store.valueAt (-1));
		assertThrows (IllegalArgumentException.class, () -> store.put (-1, "x"));
		assertThrows (NullPointerException.class, () -> store.put (1, null));
	}

	@Test
	public void insertAndRemoveShift () {
		M3SparseItemStore<String> store = store (2, 5, 9);
		store.insert (5, "new");
		assertEquals (List.of (2, 5, 6, 10), indices (store));
		assertEquals ("new", store.get (5));
		assertEquals ("v5", store.get (6));
		assertThrows (IllegalArgumentException.class, () -> store.insert (-1, "x"));
		assertThrows (NullPointerException.class, () -> store.insert (0, null));
		M3SparseItemStore<String> empty = new M3SparseItemStore<> ();
		empty.insert (3, "first");
		assertEquals ("first", empty.get (3), "insert into an empty store");
		assertEquals ("v9", store.remove (10), "remove the last item: no tail");
		assertEquals ("new", store.remove (5));
		assertEquals (List.of (2, 5), indices (store));
		assertNull (store.remove (3), "removing a cold row shifts the rows after it");
		assertEquals (List.of (2, 4), indices (store));
		assertThrows (IllegalArgumentException.class, () -> store.remove (-1));
		M3SparseItemStore<String> full = store (Integer.MAX_VALUE);
		assertThrows (IllegalArgumentException.class, () -> full.insert (0, "x"), "the last index cannot shift");
	}

	@Test
	public void rangesTruncateClearAndIteration () {
		M3SparseItemStore<String> store = store (1, 3, 5, 7, 9);
		List<String> removed = new ArrayList<> ();
		assertThrows (IllegalArgumentException.class, () -> store.removeRange (-1, 2, removed::add));
		assertThrows (IllegalArgumentException.class, () -> store.removeRange (3, 2, removed::add));
		store.removeRange (4, 4, null);
		assertThrows (NullPointerException.class, () -> store.removeRange (0, 1, null));
		store.removeRange (2, 6, removed::add);
		assertEquals (List.of ("v3", "v5"), removed);
		assertEquals (List.of (1, 3, 5), indices (store));
		store.removeRange (4, 10, removed::add);
		assertEquals (List.of (1, 3), indices (store), "a range at the tail leaves no tail to move");
		assertThrows (IllegalArgumentException.class, () -> store.truncate (-1, removed::add));
		assertThrows (NullPointerException.class, () -> store.truncate (0, null));
		store.truncate (2, removed::add);
		assertEquals (List.of (1), indices (store));
		List<String> seen = new ArrayList<> ();
		store.forEach (seen::add);
		assertEquals (List.of ("v1"), seen);
		assertThrows (NullPointerException.class, () -> store.forEach (null));
		assertThrows (NullPointerException.class, () -> store.forEachIndexed (null));
		assertThrows (NullPointerException.class, () -> store.clear (null));
		removed.clear ();
		store.clear (removed::add);
		assertEquals (List.of ("v1"), removed);
		assertTrue (store.isEmpty ());
	}

	@Test
	public void stateMasksAndIdentity () {
		M3SparseItemStore<String> store = store (1, 4, 8);
		String four = store.get (4);
		assertEquals (4, store.indexOfIdentity (four));
		assertEquals (-1, store.indexOfIdentity ("other"));
		assertEquals (0L, store.state (2), "cold rows have no state");
		store.state (4, 6L);
		assertEquals (6L, store.state (4));
		assertThrows (IllegalStateException.class, () -> store.state (2, 1L));
		assertTrue (store.flag (4, 2L));
		store.flag (4, 2L, false);
		assertFalse (store.flag (4, 2L));
		store.flag (4, 1L, true);
		assertTrue (store.flag (4, 1L));
		assertThrows (IllegalStateException.class, () -> store.flag (2, 1L, true));
		assertEquals (5L, store.stateOfIdentity (four));
		assertEquals (0L, store.stateOfIdentity ("other"));
		assertTrue (store.flagOfIdentity (four, 4L));
		store.flagOfIdentity (four, 4L, false);
		assertFalse (store.flagOfIdentity (four, 4L));
		store.flagOfIdentity (four, 8L, true);
		assertTrue (store.flagOfIdentity (four, 8L));
		assertThrows (IllegalStateException.class, () -> store.flagOfIdentity ("other", 1L, true));
	}

	@Test
	public void releaseOutsideKeepsWindowAndPinned () {
		M3SparseItemStore<String> store = store (0, 2, 4, 6, 8);
		store.flag (0, 1L, true);
		List<String> released = new ArrayList<> ();
		assertThrows (NullPointerException.class, () -> store.releaseOutside (0, 1, 0L, null));
		assertEquals (2, store.releaseOutside (4, 7, 1L, released::add));
		assertEquals (List.of ("v2", "v8"), released);
		assertEquals (List.of (0, 4, 6), indices (store), "pinned row 0 and the window 4..6 stay");
		assertEquals (1L, store.state (0));
	}
}
