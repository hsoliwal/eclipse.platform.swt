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

import org.eclipse.swt.internal.ViewportRuntime.Selection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Independent dense oracle for the shared, native-resource-free selection model. */
public class Test_ViewportSelectionEnumeration {

	@Test
	void everySmallSelectionInBothRepresentations () {
		int checked = 0;
		for (int n = 0; n <= 10; n++) {
			for (int mask = 0; mask < 1 << n; mask++) {
				for (boolean complement : new boolean [] {false, true}) {
					Selection model = new Selection ();
					model.setLogicalCount (n);
					if (complement) model.selectAll ();
					List<Boolean> oracle = new ArrayList<> ();
					for (int row = 0; row < n; row++) {
						boolean selected = (mask & 1 << row) != 0;
						oracle.add (selected);
						model.setSelected (row, selected);
					}
					verify (model, oracle);
					checked++;
				}
			}
		}
		assertEquals (4094, checked);
	}

	@Test
	void seededMutationSequencesMatchIndependentList () {
		Random random = new Random (0x53454c454354L);
		for (int trial = 0; trial < 32; trial++) {
			Selection model = new Selection ();
			List<Boolean> oracle = new ArrayList<> ();
			for (int step = 0; step < 256; step++) {
				int n = oracle.size ();
				int a = random.nextInt (n + 1), b = random.nextInt (n + 1);
				int start = Math.min (a, b), end = Math.max (a, b);
				switch (random.nextInt (8)) {
					case 0 -> {
						model.selectAll ();
						Collections.fill (oracle, true);
					}
					case 1 -> {
						model.clear ();
						Collections.fill (oracle, false);
					}
					case 2 -> {
						model.selectRange (start, end);
						Collections.fill (oracle.subList (start, end), true);
					}
					case 3 -> {
						model.deselectRange (start, end);
						Collections.fill (oracle.subList (start, end), false);
					}
					case 4 -> {
						int count = n < 64 ? random.nextInt (4) : 0;
						model.insert (a, count);
						oracle.addAll (a, Collections.nCopies (count, false));
					}
					case 5 -> {
						model.remove (start, end - start);
						oracle.subList (start, end).clear ();
					}
					case 6 -> {
						int size = random.nextInt (65);
						model.setLogicalCount (size);
						if (size < n) oracle.subList (size, n).clear ();
						else oracle.addAll (Collections.nCopies (size - n, false));
					}
					case 7 -> {
						if (a < n) {
							boolean selected = random.nextBoolean ();
							model.setSelected (a, selected);
							oracle.set (a, selected);
						}
					}
				}
				verify (model, oracle);
			}
		}
	}

	@Test
	void adjacentExclusionsAndEmptyRangesKeepEndpoints () {
		Selection model = new Selection ();
		model.setLogicalCount (12);
		model.selectAll ();
		model.deselectRange (1, 4);
		model.deselectRange (4, 8);
		model.deselectRange (9, 12);
		model.deselectRange (0, 0);
		model.selectRange (12, 12);
		assertArrayEquals (new int [] {0, 8}, model.toArray ());
		model.selectRange (3, 6);
		assertArrayEquals (new int [] {0, 3, 4, 5, 8}, model.toArray ());
	}

	@Test
	@Timeout (60)
	void maximumLogicalCountWithTinyComplementOutput () {
		Selection model = new Selection ();
		model.setLogicalCount (Integer.MAX_VALUE);
		model.selectAll ();
		model.deselectRange (0, Integer.MAX_VALUE - 1);
		assertEquals (1, model.selectedCount ());
		assertArrayEquals (new int [] {Integer.MAX_VALUE - 1}, model.toArray ());
		assertEquals (1, model.rangeCount ());
		assertTrue (model.complementMode ());
		assertThrows (ArithmeticException.class, () -> model.insert (0, 1));
		assertEquals (Integer.MAX_VALUE, model.logicalCount ());
	}

	@Test
	void invalidMutationsDoNotChangeSelection () {
		Selection model = new Selection ();
		model.setLogicalCount (3);
		model.selectAll ();
		model.deselectRange (1, 2);
		assertThrows (IllegalArgumentException.class, () -> model.setLogicalCount (-1));
		assertThrows (IllegalArgumentException.class, () -> model.selectRange (2, 1));
		assertThrows (IllegalArgumentException.class, () -> model.deselectRange (-1, 1));
		assertThrows (IllegalArgumentException.class, () -> model.insert (4, 1));
		assertThrows (IllegalArgumentException.class, () -> model.remove (2, 2));
		assertArrayEquals (new int [] {0, 2}, model.toArray ());
	}

	@Test
	void alternatingHolesProduceSortedFreshOutput () {
		Selection model = new Selection ();
		model.setLogicalCount (4096);
		model.selectAll ();
		for (int row = 0; row < 4096; row += 2) model.deselectRange (row, row + 1);
		int [] result = model.toArray ();
		assertEquals (2048, result.length);
		for (int i = 0; i < result.length; i++) assertEquals (2 * i + 1, result [i]);
		result [0] = -1;
		assertEquals (1, model.toArray () [0]);
	}


	@Test
	void clippedViewportSelectionInBothRepresentations () {
		Selection model = new Selection ();
		model.setLogicalCount (24);
		model.selectRange (2, 5);
		model.selectRange (10, 15);
		model.selectRange (18, 24);
		int [] included = {4, 10, 11, 12, 13, 14, 18, 19};
		assertEquals (included.length, model.selectedCount (4, 20));
		assertArrayEquals (included, model.toArray (4, 20));
		int [] output = new int [included.length + 2];
		Arrays.fill (output, -7);
		assertEquals (included.length, model.copySelectedIndices (4, 20, output, 1));
		assertEquals (-7, output [0]);
		assertEquals (-7, output [output.length - 1]);
		assertArrayEquals (included, Arrays.copyOfRange (output, 1, output.length - 1));
		model.invert ();
		int [] excluded = {5, 6, 7, 8, 9, 15, 16, 17};
		assertEquals (excluded.length, model.selectedCount (4, 20));
		assertArrayEquals (excluded, model.toArray (4, 20));
		assertArrayEquals (new int [0], model.toArray (20, 20));
	}

	@Test
	@Timeout (60)
	void nearMaximumLogicalCountOnlyCopiesSmallViewport () {
		Selection model = new Selection ();
		model.setLogicalCount (Integer.MAX_VALUE);
		model.selectAll ();
		model.deselectRange (0, Integer.MAX_VALUE - 10);
		model.deselectRange (Integer.MAX_VALUE - 3, Integer.MAX_VALUE);
		assertEquals (7, model.selectedCount (Integer.MAX_VALUE - 20, Integer.MAX_VALUE));
		int [] clipped = model.toArray (Integer.MAX_VALUE - 20, Integer.MAX_VALUE);
		assertEquals (7, clipped.length);
		for (int i = 0; i < 7; i++) assertEquals (Integer.MAX_VALUE - 10 + i, clipped [i]);
		int [] out = new int [10];
		Arrays.fill (out, -123);
		assertEquals (7, model.copySelectedIndices (
				Integer.MAX_VALUE - 20, Integer.MAX_VALUE, out, 2));
		assertEquals (-123, out [0]);
		assertEquals (-123, out [1]);
		assertEquals (-123, out [9]);
		assertArrayEquals (clipped, Arrays.copyOfRange (out, 2, 9));
		assertEquals (7, model.selectedCount ());
		assertEquals (Integer.MAX_VALUE, model.logicalCount ());
	}

	@Test
	void selectionSliceRefusalsDoNotModifyDestinationOrSource () {
		Selection model = new Selection ();
		model.setLogicalCount (10);
		model.selectRange (2, 7);
		int [] out = {-1, -1, -1, -1, -1};
		assertThrows (IndexOutOfBoundsException.class, () ->
				model.copySelectedIndices (0, 10, out, 1));
		assertArrayEquals (new int [] {-1, -1, -1, -1, -1}, out);
		assertThrows (IndexOutOfBoundsException.class, () ->
				model.copySelectedIndices (0, 10, out, -1));
		assertThrows (NullPointerException.class, () ->
				model.copySelectedIndices (0, 10, null, 0));
		assertThrows (IllegalArgumentException.class, () -> model.selectedCount (-1, 5));
		assertThrows (IllegalArgumentException.class, () -> model.toArray (6, 3));
		assertThrows (IllegalArgumentException.class, () ->
				model.copySelectedIndices (0, 11, out, 0));
		assertArrayEquals (new int [] {-1, -1, -1, -1, -1}, out);
		assertArrayEquals (new int [] {2, 3, 4, 5, 6}, model.toArray ());
	}

	private static void verify (Selection model, List<Boolean> oracle) {
		int [] expected = java.util.stream.IntStream.range (0, oracle.size ())
				.filter (oracle::get).toArray ();
		int ranges = model.rangeCount ();
		int [] bounds = new int [2 * ranges];
		for (int i = 0; i < ranges; i++) {
			bounds [2 * i] = model.rangeStart (i);
			bounds [2 * i + 1] = model.rangeEndExclusive (i);
		}
		boolean complement = model.complementMode ();
		int [] actual = model.toArray ();
		assertArrayEquals (expected, actual);
		// Existing dense oracle independently checks the new bounded viewport query.
		int from = oracle.size () / 3, to = (oracle.size () * 2) / 3;
		int [] window = java.util.stream.IntStream.range (from, to)
				.filter (oracle::get).toArray ();
		assertEquals (window.length, model.selectedCount (from, to));
		assertArrayEquals (window, model.toArray (from, to));
		int [] destination = new int [window.length + 2];
		Arrays.fill (destination, -44);
		assertEquals (window.length, model.copySelectedIndices (from, to, destination, 1));
		assertEquals (-44, destination [0]);
		assertEquals (-44, destination [destination.length - 1]);
		assertArrayEquals (window, Arrays.copyOfRange (destination, 1, destination.length - 1));
		assertNotSame (actual, model.toArray ());
		if (actual.length > 0) actual [0] = -1;
		assertArrayEquals (expected, model.toArray ());
		assertEquals (oracle.size (), model.logicalCount ());
		assertEquals (expected.length, model.selectedCount ());
		assertEquals (complement, model.complementMode ());
		assertEquals (ranges, model.rangeCount ());
		for (int i = 0; i < ranges; i++) {
			assertEquals (bounds [2 * i], model.rangeStart (i));
			assertEquals (bounds [2 * i + 1], model.rangeEndExclusive (i));
		}
		for (int row = 0; row < oracle.size (); row++) assertEquals (oracle.get (row), model.isSelected (row));
	}
}
