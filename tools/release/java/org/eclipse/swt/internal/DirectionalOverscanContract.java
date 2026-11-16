/* SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt.internal;

import java.math.BigInteger;
import java.util.Random;

import org.eclipse.swt.internal.ViewportRuntime.RowWindow;

/** Headless real-source contract for original SWT row-window directional cache. */
public final class DirectionalOverscanContract {
	private DirectionalOverscanContract () {}

	private static void same (long expected, long actual, String where) {
		if (expected != actual) throw new AssertionError (
				where + " expected=" + expected + " actual=" + actual);
	}

	private static void check (RowWindow window, long logical, long requestedFirst,
			int requestedVisible, int before, int after, String label) {
		long first = logical == 0 ? 0 : Math.min (requestedFirst, logical - 1);
		int visible = (int) Math.min ((long) requestedVisible, Math.max (0L, logical - first));
		BigInteger low = BigInteger.valueOf (first).subtract (BigInteger.valueOf (before))
				.max (BigInteger.ZERO);
		BigInteger high = BigInteger.valueOf (first).add (BigInteger.valueOf (visible))
				.add (BigInteger.valueOf (after)).min (BigInteger.valueOf (logical));
		if (visible == 0) low = high = BigInteger.valueOf (first);
		same (first, window.firstVisible (), label + " origin");
		same (visible, window.visibleCount (), label + " visible");
		same (low.longValueExact (), window.paintStart (), label + " start");
		same (high.longValueExact (), window.paintEndExclusive (), label + " end");
		long count = high.subtract (low).longValueExact ();
		if (count <= Integer.MAX_VALUE) same (count, window.paintCount (), label + " count");
	}

	public static void main (String [] args) {
		RowWindow window = new RowWindow ();
		same (8, window.overscanRows (), "default");
		same (8, window.overscanBeforeRows (), "default before");
		same (8, window.overscanAfterRows (), "default after");
		window.setLogicalCount (1000);
		window.setViewport (500, 40);
		check (window, 1000, 500, 40, 8, 8, "legacy");
		long generation = window.generation ();
		window.setDirectionalOverscanRows (2, 18);
		same (generation + 1, window.generation (), "changed");
		same (18, window.overscanRows (), "max getter");
		check (window, 1000, 500, 40, 2, 18, "directional");
		window.setDirectionalOverscanRows (2, 18);
		same (generation + 1, window.generation (), "no-op");
		window.setOverscanRows (8);
		check (window, 1000, 500, 40, 8, 8, "restore symmetry");
		window.setViewport (500, 0);
		window.setDirectionalOverscanRows (Integer.MAX_VALUE, Integer.MAX_VALUE);
		check (window, 1000, 500, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, "collapsed");
		window.setDirectionalOverscanRows (2, 18);
		window.setViewport (500, 40);
		window.insert (0, 3);
		check (window, 1003, 503, 40, 2, 18, "insert");
		window.remove (0, 3);
		check (window, 1000, 500, 40, 2, 18, "remove");
		Random random = new Random (0x20261116L);
		for (int trial = 0; trial < 120_000; trial++) {
			long logical = trial % 7 == 0 ? Long.MAX_VALUE
					: 1L + random.nextInt (10_000_000);
			long first = trial % 5 == 0 ? logical - 1
					: Math.floorMod (random.nextLong (), logical);
			int rows = random.nextInt (101);
			int before = random.nextInt (200), after = random.nextInt (200);
			RowWindow candidate = new RowWindow ();
			candidate.setLogicalCount (logical);
			candidate.setViewport (first, rows);
			candidate.setDirectionalOverscanRows (before, after);
			check (candidate, logical, first, rows, before, after, "trial=" + trial);
			if (trial % 13 == 0) {
				candidate.setViewport (first, 0);
				check (candidate, logical, first, 0, before, after,
						"collapsed trial=" + trial);
			}
		}
		System.out.println ("DIRECTIONAL_OVERSCAN_ORACLE queries=120000 PASS");
	}
}
