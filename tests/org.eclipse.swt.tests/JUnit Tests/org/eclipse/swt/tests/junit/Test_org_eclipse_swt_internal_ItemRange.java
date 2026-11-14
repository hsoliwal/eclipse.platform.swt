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

import org.eclipse.swt.internal.ItemRange;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of the ItemRange value (enhanced M3 API, leaf 1). Headless.
 */
public class Test_org_eclipse_swt_internal_ItemRange {

	@Test
	public void test_valueAndEmptiness () {
		ItemRange range = new ItemRange (3, 7);
		assertEquals (3, range.start ());
		assertEquals (7, range.end ());
		assertEquals (5, range.size ());
		assertFalse (range.isEmpty ());
		ItemRange single = new ItemRange (4, 4);
		assertEquals (1, single.size ());
		ItemRange empty = new ItemRange (5, 2);
		assertTrue (empty.isEmpty ());
		assertEquals (0, empty.size ());
		assertTrue (ItemRange.EMPTY.isEmpty ());
		assertEquals (0, ItemRange.EMPTY.start ());
		assertEquals (-1, ItemRange.EMPTY.end ());
	}

	@Test
	public void test_ofNormalizesEmpty () {
		assertSame (ItemRange.EMPTY, ItemRange.of (3, 2));
		assertEquals (new ItemRange (2, 9), ItemRange.of (2, 9));
		assertThrows (IllegalArgumentException.class, () -> ItemRange.of (-1, 2));
		assertThrows (IllegalArgumentException.class, () -> new ItemRange (-1, 2));
	}

	@Test
	public void test_containsAndIntersection () {
		ItemRange range = new ItemRange (10, 20);
		assertTrue (range.contains (10));
		assertTrue (range.contains (20));
		assertFalse (range.contains (9));
		assertFalse (range.contains (21));
		assertFalse (ItemRange.EMPTY.contains (0));
		assertEquals (new ItemRange (15, 20), range.intersection (new ItemRange (15, 30)));
		assertEquals (new ItemRange (10, 12), range.intersection (new ItemRange (0, 12)));
		assertTrue (range.intersection (new ItemRange (21, 25)).isEmpty ());
		assertTrue (range.intersection (ItemRange.EMPTY).isEmpty ());
		assertThrows (IllegalArgumentException.class, () -> range.intersection (null));
	}

	@Test
	public void test_coalesceBuildsNormalForm () {
		ItemRange [] ranges = ItemRange.coalesce (new int [] {5, 3, 4, 9, 1});
		assertArrayEquals (new ItemRange [] {new ItemRange (1, 1), new ItemRange (3, 5), new ItemRange (9, 9)}, ranges);
		assertEquals (5, ItemRange.countOf (ranges));
		assertArrayEquals (new ItemRange [] {new ItemRange (2, 3)}, ItemRange.coalesce (new int [] {2, 2, 3}));
		assertArrayEquals (new ItemRange [] {new ItemRange (0, 4)}, ItemRange.coalesce (new int [] {0, 1, 2, 3, 4}));
		assertArrayEquals (new ItemRange [] {new ItemRange (7, 7)}, ItemRange.coalesce (new int [] {7}));
		assertEquals (0, ItemRange.coalesce (new int [0]).length);
		assertEquals (0, ItemRange.countOf (new ItemRange [0]));
		assertThrows (IllegalArgumentException.class, () -> ItemRange.coalesce (null));
		assertThrows (IllegalArgumentException.class, () -> ItemRange.coalesce (new int [] {2, -1}));
		assertThrows (IllegalArgumentException.class, () -> ItemRange.countOf (null));
		assertThrows (IllegalArgumentException.class, () -> ItemRange.countOf (new ItemRange [] {null}));
	}

	@Test
	public void test_coalesceLeavesTheArgumentUntouched () {
		int [] indices = {9, 1, 1, 4};
		ItemRange.coalesce (indices);
		assertArrayEquals (new int [] {9, 1, 1, 4}, indices);
	}


	@Test
	public void test_upperIntBoundaryAndNonAdjacentRuns () {
		/* Source indices can reach int's positive limit; the adjacency check must not
		 * overflow when the largest valid index is the final member of a range. */
		int last = Integer.MAX_VALUE;
		ItemRange tail = ItemRange.of (last - 1, last);
		assertEquals (2, tail.size ());
		assertTrue (tail.contains (last));
		assertFalse (tail.contains (last - 2));
		assertArrayEquals (new ItemRange [] {tail},
			ItemRange.coalesce (new int [] {last - 1, last}));
		assertArrayEquals (new ItemRange [] {
			ItemRange.of (last - 2, last - 2), ItemRange.of (last, last)
		}, ItemRange.coalesce (new int [] {last, last - 2}));
		assertEquals (2, ItemRange.countOf (new ItemRange [] {
			ItemRange.of (last - 2, last - 2), ItemRange.of (last, last)
		}));
		assertEquals (ItemRange.of (last, last),
			tail.intersection (ItemRange.of (last, last)));
	}



	@Test
	public void test_duplicateAtMaximumIntCoalescesOnce () {
		/* Release-blocking regression: int overflow in +1 must not split duplicates.
		 * The current implementation needs the canonical source-pinned recipe fix. */
		int last = Integer.MAX_VALUE;
		ItemRange [] ranges = ItemRange.coalesce (new int [] {last, last});
		assertArrayEquals (new ItemRange [] {ItemRange.of (last, last)}, ranges);
		assertEquals (1, ItemRange.countOf (ranges));
	}


	@Test
	public void test_equalsHashCodeToString () {
		ItemRange same = new ItemRange (1, 2);
		assertTrue (same.equals (same));
		assertFalse (ItemRange.EMPTY.equals (same));
		assertFalse (same.equals (ItemRange.EMPTY));
		assertEquals (new ItemRange (1, 2), new ItemRange (1, 2));
		assertEquals (new ItemRange (1, 2).hashCode (), new ItemRange (1, 2).hashCode ());
		assertNotEquals (new ItemRange (1, 2), new ItemRange (1, 3));
		assertNotEquals (new ItemRange (1, 2), new ItemRange (0, 2));
		assertEquals (new ItemRange (8, 2), ItemRange.EMPTY);
		assertEquals (new ItemRange (8, 2).hashCode (), ItemRange.EMPTY.hashCode ());
		assertNotEquals (new ItemRange (1, 2), "ItemRange [1..2]");
		assertNotEquals (new ItemRange (1, 2), null);
		assertEquals ("ItemRange [1..2]", new ItemRange (1, 2).toString ());
		assertEquals ("ItemRange []", ItemRange.EMPTY.toString ());
	}
}
