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
 * com.m3.collections.M3ListDiffTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Oracle tests of the keyed diff: applying the patch must give the new snapshot, with a minimal number of moves. */
public class M3ListDiffTest {
	/** Longest strictly increasing subsequence length, O(n^2) oracle. */
	private static int lisLength (int [] values) {
		int [] best = new int [values.length];
		int length = 0;
		for (int i = 0; i < values.length; i++) {
			best [i] = 1;
			for (int j = 0; j < i; j++) if (values [j] < values [i]) best [i] = Math.max (best [i], best [j] + 1);
			length = Math.max (length, best [i]);
		}
		return length;
	}

	@Test
	public void randomSnapshots () {
		Random random = new Random (42L);
		for (int round = 0; round < 400; round++) {
			int n = random.nextInt (30);
			List<Long> before = new ArrayList<> ();
			for (int i = 0; i < n; i++) before.add ((long) (i * 3 + random.nextInt (3)));
			List<Long> after = new ArrayList<> ();
			for (Long key : before) if (random.nextInt (4) != 0) after.add (key);
			Collections.shuffle (after.subList (0, after.size () / (1 + random.nextInt (3))), random);
			int added = random.nextInt (6);
			for (int i = 0; i < added; i++) after.add (random.nextInt (after.size () + 1), 1000L + round * 10 + i);
			long [] b = before.stream ().mapToLong (Long::longValue).toArray ();
			long [] a = after.stream ().mapToLong (Long::longValue).toArray ();
			M3Patch patch = M3ListDiff.diff (b, a);
			assertArrayEquals (a, patch.apply (b, a), "round " + round);
			Set<Long> newKeys = new HashSet<> (after);
			int [] survivorPositions = before.stream ().filter (newKeys::contains).mapToInt (after::indexOf).toArray ();
			int kept = lisLength (survivorPositions);
			assertEquals (kept, patch.keptCount ());
			assertEquals (b.length - kept, patch.removedCount ());
			assertEquals (a.length - kept, patch.insertedCount ());
			assertEquals (survivorPositions.length - kept, patch.moveCount ());
			assertEquals (Arrays.equals (a, b), patch.isEmpty ());
			for (int m = 0; m < patch.moveCount (); m++) assertEquals (b [patch.moveFrom (m)], a [patch.moveTo (m)]);
			for (int r = 1; r < patch.removeRuns (); r++) assertTrue (patch.removeAt (r) < patch.removeAt (r - 1));
			for (int r = 1; r < patch.insertRuns (); r++) assertTrue (patch.insertAt (r) > patch.insertAt (r - 1));
		}
	}

	@Test
	public void runsAndTargets () {
		long [] before = {1, 2, 3, 4, 5, 6};
		long [] after = {1, 9, 3, 2, 6, 7, 8};
		M3Patch patch = M3ListDiff.diff (before, after);
		List<String> ops = new ArrayList<> ();
		patch.applyTo (new M3Patch.Target () {
			@Override
			public void remove (int index, int count) {
				ops.add ("-" + index + "x" + count);
			}

			@Override
			public void insert (int index, int count) {
				ops.add ("+" + index + "x" + count);
			}
		});
		assertArrayEquals (after, patch.apply (before, after));
		assertEquals (3, patch.keptCount (), "1, 3, 6 stay");
		assertEquals (List.of ("-3x2", "-1x1", "+1x1", "+3x1", "+5x2"), ops);
		assertEquals (2, patch.removeRuns ());
		assertEquals (3, patch.removeAt (0));
		assertEquals (2, patch.removeCount (0));
		assertEquals (3, patch.insertRuns ());
		assertEquals (5, patch.insertAt (2));
		assertEquals (2, patch.insertCount (2));
		assertEquals (1, patch.moveCount (), "2 moves behind 3");
		assertEquals (1, patch.moveFrom (0));
		assertEquals (3, patch.moveTo (0));
		assertTrue (M3ListDiff.diff (new long [0], new long [0]).isEmpty ());
		assertTrue (M3ListDiff.diff (before, before.clone ()).isEmpty ());
		M3Patch insertOnly = M3ListDiff.diff (new long [0], new long [] {5});
		assertFalse (insertOnly.isEmpty ());
		M3Patch removeOnly = M3ListDiff.diff (new long [] {5}, new long [0]);
		assertFalse (removeOnly.isEmpty ());
		assertThrows (IllegalArgumentException.class, () -> M3ListDiff.diff (new long [] {1, 1}, new long [0]));
		assertThrows (IllegalArgumentException.class, () -> M3ListDiff.diff (new long [0], new long [] {2, 2}));
		assertArrayEquals (new int [0], M3ListDiff.longestIncreasing (new int [0], 0));
		assertArrayEquals (new int [] {1, 2, 3}, M3ListDiff.longestIncreasing (new int [] {1, 0, 2, 3}, 4));
	}
}
