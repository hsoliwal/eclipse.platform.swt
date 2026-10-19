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
package org.eclipse.swt.internal.m3;

import static org.junit.Assert.*;

import org.junit.*;

/**
 * Argument checks and Fenwick edges of the copied {@link M3SparseExtents} that its source test
 * (M3SparseExtentsTest, copied alongside) does not reach. Headless.
 */
public class M3SparseExtentsEdgeTest {
	private M3SparseExtents extents;

	@Before
	public void setUp () {
		extents = new M3SparseExtents (10);
		extents.reset (20);
	}

	@Test
	public void rowArgumentsAreChecked () {
		assertEquals (200, extents.offsetOf (20));
		assertThrows (IllegalArgumentException.class, () -> extents.offsetOf (-1));
		assertThrows (IllegalArgumentException.class, () -> extents.offsetOf (21));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (-1, 5, 0, false));
		assertThrows (IllegalArgumentException.class, () -> extents.setExtentAnchored (3, -1, 0, false));
	}

	@Test
	public void insertArgumentsAreChecked () {
		assertThrows (IllegalArgumentException.class, () -> extents.insert (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.insert (21, 1));
		extents.insert (20, 2);
		assertEquals (22, extents.rowCount ());
	}

	@Test
	public void removeArgumentsAreChecked () {
		assertThrows (IllegalArgumentException.class, () -> extents.remove (0, -1));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> extents.remove (19, 2));
		extents.remove (18, 2);
		assertEquals (18, extents.rowCount ());
	}

	@Test
	public void measurementsInAnyOrderKeepPrefixSums () {
		for (int row : new int [] {15, 3, 9, 1, 12, 7, 18, 5}) {
			extents.setExtent (row, 10 + row);
		}
		long expected = 0;
		for (int row = 0; row < 20; row++) {
			assertEquals ("offset of " + row, expected, extents.offsetOf (row));
			expected += extents.extentOf (row);
		}
		assertEquals (expected, extents.totalExtent ());
	}
}
