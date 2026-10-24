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

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

import org.junit.jupiter.api.*;

/**
 * Gate of the Tree footer engine (VirtualTreeAggregate, BEHAVIOURS.md "Tree footer"): a forest of 10^6 rows, then 10^4
 * random deltas (values, expansion, filter bits, new leaves, removed subtrees); every 1,000 deltas the footer total and
 * the subtotals of 64 random branches are compared with a brute-force recount over all rows, in every scope (ALL,
 * FILTERED, VISIBLE). Logs the cost per delta (rows walked) and the engine's own recounts (scope switches only).
 * Headless: <code>-Dm3.footer.rows</code> and <code>-Dm3.footer.deltas</code> change the sizes.
 */
public class Test_VirtualTreeAggregate {
	static final Logger LOG = Logger.getLogger (Test_VirtualTreeAggregate.class.getName ());
	static final int ROWS = Integer.getInteger ("m3.footer.rows", 1_000_000), DELTAS = Integer.getInteger ("m3.footer.deltas", 10_000);
	static final int EXPANDED = 1, FILTERED_OUT = 2;

	static MethodHandles.Lookup LOOKUP = MethodHandles.lookup ();
	Object engine;
	MethodHandle add, remove, setValue, setExpanded, setFilteredOut, setScope, trackMinMax, count, filled, sum, min, max, recounts, updates;

	/* the reference: rows by id (a parent's id is always smaller than its children's) */
	int size;
	int [] parent = new int [ROWS + DELTAS + 16];
	double [] value = new double [parent.length];
	byte [] bits = new byte [parent.length];
	boolean [] live = new boolean [parent.length];

	MethodHandle handle (Class<?> type, String name, Class<?>... parameters) throws ReflectiveOperationException {
		Method method = type.getDeclaredMethod (name, parameters);
		method.setAccessible (true);
		return LOOKUP.unreflect (method).bindTo (engine);
	}

	@BeforeEach
	public void setUp () throws ReflectiveOperationException {
		Class<?> type = Class.forName ("org.eclipse.swt.widgets.VirtualTreeAggregate");
		Constructor<?> constructor = type.getDeclaredConstructor ();
		constructor.setAccessible (true);
		engine = constructor.newInstance ();
		add = handle (type, "add", int.class, int.class, double.class, int.class);
		remove = handle (type, "remove", int.class);
		setValue = handle (type, "setValue", int.class, double.class);
		setExpanded = handle (type, "setExpanded", int.class, boolean.class);
		setFilteredOut = handle (type, "setFilteredOut", int.class, boolean.class);
		setScope = handle (type, "setScope", int.class);
		trackMinMax = handle (type, "trackMinMax", boolean.class);
		count = handle (type, "count", int.class);
		filled = handle (type, "filled", int.class);
		sum = handle (type, "sum", int.class);
		min = handle (type, "min", int.class);
		max = handle (type, "max", int.class);
		recounts = handle (type, "recounts");
		updates = handle (type, "updates");
	}

	@Test
	public void footerFollowsRandomDeltasWithoutARecount () throws Throwable {
		Random random = new Random (0xF007E2L);
		trackMinMax.invoke (true);
		long start = System.nanoTime ();
		for (int i = 0; i < ROWS; i++) addRow (random);
		long built = System.nanoTime () - start;
		LOG.info (() -> String.format (Locale.ROOT, "footer engine: %d rows built in %.1f ms", ROWS, built / 1e6));
		check (random, 0, "after the build");
		long recountsBefore = (long) recounts.invoke (), updatesBefore = (long) updates.invoke (), deltaNanos = 0, recountNanos = 0;
		int scope = 0, removedRows = 0;
		for (int d = 1; d <= DELTAS; d++) {
			long t = System.nanoTime ();
			removedRows += delta (random);
			deltaNanos += System.nanoTime () - t;
			if (d % 1_000 == 0) {
				long recountsNow = (long) recounts.invoke ();
				assertEquals (recountsBefore, recountsNow, "a delta recounted");
				scope = (scope + 1) % 3;
				long r = System.nanoTime ();
				setScope.invoke (scope);
				recountNanos += System.nanoTime () - r;
				recountsBefore = (long) recounts.invoke ();
				check (random, scope, "after " + d + " deltas");
			}
		}
		final long nanos = deltaNanos, walked = (long) updates.invoke () - updatesBefore, recounted = (long) recounts.invoke (), recountMs = recountNanos / 1_000_000 / Math.max (1, DELTAS / 1_000);
		final int removed = removedRows;
		LOG.info (() -> String.format (Locale.ROOT, "footer engine: %d deltas at %d rows, %.2f us per delta, %.1f rows walked per delta, %d rows removed in subtrees; recounts %d (scope switches and min/max on, none from a delta), %d ms per recount (scope switch) at this size; the delta time includes the test reference of removed subtrees",
			DELTAS, ROWS, nanos / 1e3 / DELTAS, (double) walked / DELTAS, removed, recounted, recountMs));
	}

	void addRow (Random random) throws Throwable {
		int id = size++;
		int p = id == 0 || random.nextInt (8) == 0 ? -1 : randomLive (random, id);
		double v = random.nextInt (10) == 0 ? Double.NaN : random.nextInt (2_000_001) - 1_000_000;
		int b = (random.nextBoolean () ? EXPANDED : 0) | (random.nextInt (10) == 0 ? FILTERED_OUT : 0);
		parent [id] = p;
		value [id] = v;
		bits [id] = (byte) b;
		live [id] = true;
		add.invoke (id, p, v, b);
	}

	/* a live row below limit, biased to recent rows so trees get deep */
	int randomLive (Random random, int limit) {
		for (int tries = 0; tries < 64; tries++) {
			int candidate = random.nextInt (4) == 0 ? random.nextInt (limit) : Math.max (0, limit - 1 - random.nextInt (Math.min (limit, 64)));
			if (live [candidate]) return candidate;
		}
		return -1;
	}

	/* one delta; answers the rows removed */
	int delta (Random random) throws Throwable {
		int id = randomLive (random, size);
		if (id < 0) return 0;
		switch (random.nextInt (20)) {
			case 0, 1, 2, 3, 4, 5, 6, 7 -> {
				double v = random.nextInt (10) == 0 ? Double.NaN : random.nextInt (2_000_001) - 1_000_000;
				value [id] = v;
				setValue.invoke (id, v);
			}
			case 8, 9, 10, 11 -> {
				boolean on = (bits [id] & EXPANDED) == 0;
				bits [id] = (byte) (on ? bits [id] | EXPANDED : bits [id] & ~EXPANDED);
				setExpanded.invoke (id, on);
			}
			case 12, 13, 14 -> {
				boolean on = (bits [id] & FILTERED_OUT) == 0;
				bits [id] = (byte) (on ? bits [id] | FILTERED_OUT : bits [id] & ~FILTERED_OUT);
				setFilteredOut.invoke (id, on);
			}
			case 15, 16, 17, 18 -> addRow (random);
			default -> {
				/* a removed subtree: ids above it whose parent is gone go too (parents have smaller ids) */
				remove.invoke (id);
				live [id] = false;
				int removed = 1;
				for (int x = id + 1; x < size; x++) {
					if (live [x] && parent [x] >= 0 && !live [parent [x]]) {
						live [x] = false;
						removed++;
					}
				}
				return removed;
			}
		}
		return 0;
	}

	/* brute force over all rows (children have larger ids than their parents: one reverse pass), then compare */
	void check (Random random, int scope, String when) throws Throwable {
		int n = size;
		long [] rCount = new long [n + 1], rFilled = new long [n + 1];
		double [] rSum = new double [n + 1], rMin = new double [n + 1], rMax = new double [n + 1];
		Arrays.fill (rMin, Double.POSITIVE_INFINITY);
		Arrays.fill (rMax, Double.NEGATIVE_INFINITY);
		for (int x = n - 1; x >= -1; x--) {
			int node = x + 1;
			if (x >= 0 && !live [x]) continue;
			boolean own = x >= 0 && (scope != 1 || (bits [x] & FILTERED_OUT) == 0);
			boolean has = own && !Double.isNaN (value [x]);
			/* children were folded into rCount [node] etc. as "child totals"; the scope decides whether they pass */
			boolean pass = x < 0 || scope != 2 || (bits [x] & EXPANDED) != 0;
			long c = (own ? 1 : 0) + (pass ? rCount [node] : 0), f = (has ? 1 : 0) + (pass ? rFilled [node] : 0);
			double s = (has ? value [x] : 0) + (pass ? rSum [node] : 0);
			double lo = Math.min (has ? value [x] : Double.POSITIVE_INFINITY, pass ? rMin [node] : Double.POSITIVE_INFINITY);
			double hi = Math.max (has ? value [x] : Double.NEGATIVE_INFINITY, pass ? rMax [node] : Double.NEGATIVE_INFINITY);
			rCount [node] = c;
			rFilled [node] = f;
			rSum [node] = s;
			rMin [node] = lo;
			rMax [node] = hi;
			if (x >= 0) {
				int up = parent [x] + 1;
				/* fold into the parent's child totals: the parent is processed later (smaller id) */
				rCount [up] += c;
				rFilled [up] += f;
				rSum [up] += s;
				rMin [up] = Math.min (rMin [up], lo);
				rMax [up] = Math.max (rMax [up], hi);
			}
		}
		compare (-1, rCount [0], rFilled [0], rSum [0], rMin [0], rMax [0], when + " (footer, scope " + scope + ")");
		for (int i = 0; i < 64; i++) {
			int x = randomLive (random, n);
			if (x < 0) continue;
			compare (x, rCount [x + 1], rFilled [x + 1], rSum [x + 1], rMin [x + 1], rMax [x + 1], when + " (subtotal of row " + x + ", scope " + scope + ")");
		}
	}

	void compare (int id, long c, long f, double s, double lo, double hi, String what) throws Throwable {
		assertEquals (c, (long) (int) count.invoke (id), "count " + what);
		assertEquals (f, (long) (int) filled.invoke (id), "filled " + what);
		assertEquals (s, (double) sum.invoke (id), Math.max (1e-6, Math.abs (s) * 1e-12), "sum " + what);
		assertEquals (lo == Double.POSITIVE_INFINITY ? Double.NaN : lo, (double) min.invoke (id), "min " + what);
		assertEquals (hi == Double.NEGATIVE_INFINITY ? Double.NaN : hi, (double) max.invoke (id), "max " + what);
	}
}
