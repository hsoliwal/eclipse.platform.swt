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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/**
 * Headless contracts for the sparse facade store of virtual widgets
 * (<code>org.eclipse.swt.widgets.VirtualItemStorage</code>, reached by reflection like the other
 * viewport contract tests): its identity queries answer exactly what a first-match scan of the
 * materialized entries answers, under every edit, and they probe a few table slots whatever the
 * number of materialized entries, where the scan compared every entry.
 */
public class Test_VirtualItemStorage {

	/** Reflective handle on one store. */
	private static final class Storage {
		private static final Class<?> TYPE;
		private static final Method PUT, INSERT, REMOVE, REMOVE_RANGE, TRUNCATE, CLEAR, RELEASE_OUTSIDE,
				INDEX_OF_IDENTITY, STATE_OF_IDENTITY, FLAG_OF_IDENTITY, SIZE;
		private static final Field PROBES, BUILDS;
		static {
			try {
				TYPE = Class.forName ("org.eclipse.swt.widgets.VirtualItemStorage");
				PUT = method ("put", int.class, Object.class);
				INSERT = method ("insert", int.class, Object.class);
				REMOVE = method ("remove", int.class);
				REMOVE_RANGE = method ("removeRange", int.class, int.class, Consumer.class);
				TRUNCATE = method ("truncate", int.class, Consumer.class);
				CLEAR = method ("clear", Consumer.class);
				RELEASE_OUTSIDE = method ("releaseOutside", int.class, int.class, long.class, Consumer.class);
				INDEX_OF_IDENTITY = method ("indexOfIdentity", Object.class);
				STATE_OF_IDENTITY = method ("stateOfIdentity", Object.class);
				FLAG_OF_IDENTITY = method ("flagOfIdentity", Object.class, long.class, boolean.class);
				SIZE = method ("size");
				PROBES = TYPE.getDeclaredField ("identityProbes");
				BUILDS = TYPE.getDeclaredField ("identityBuilds");
				PROBES.setAccessible (true);
				BUILDS.setAccessible (true);
			} catch (ReflectiveOperationException e) {
				throw new ExceptionInInitializerError (e);
			}
		}

		private static Method method (String name, Class<?>... types) throws NoSuchMethodException {
			Method method = TYPE.getDeclaredMethod (name, types);
			method.setAccessible (true);
			return method;
		}

		final Object target;

		Storage () {
			try {
				Constructor<?> constructor = TYPE.getDeclaredConstructor ();
				constructor.setAccessible (true);
				target = constructor.newInstance ();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException (e);
			}
		}

		private Object call (Method method, Object... arguments) {
			try {
				return method.invoke (target, arguments);
			} catch (InvocationTargetException e) {
				if (e.getCause () instanceof RuntimeException runtime) throw runtime;
				throw new IllegalStateException (e.getCause ());
			} catch (IllegalAccessException e) {
				throw new IllegalStateException (e);
			}
		}

		void put (int index, Object value) { call (PUT, index, value); }
		void insert (int index, Object value) { call (INSERT, index, value); }
		void remove (int index) { call (REMOVE, index); }
		void removeRange (int start, int end) { call (REMOVE_RANGE, start, end, (Consumer<Object>) v -> {}); }
		void truncate (int count) { call (TRUNCATE, count, (Consumer<Object>) v -> {}); }
		void clear () { call (CLEAR, (Consumer<Object>) v -> {}); }
		int releaseOutside (int start, int end, long keep) { return (Integer) call (RELEASE_OUTSIDE, start, end, keep, (Consumer<Object>) v -> {}); }
		int indexOfIdentity (Object value) { return (Integer) call (INDEX_OF_IDENTITY, value); }
		long stateOfIdentity (Object value) { return (Long) call (STATE_OF_IDENTITY, value); }
		void flagOfIdentity (Object value, long mask, boolean enabled) { call (FLAG_OF_IDENTITY, value, mask, enabled); }
		int size () { return (Integer) call (SIZE); }

		long probes () {
			try {
				return PROBES.getLong (target);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException (e);
			}
		}

		long builds () {
			try {
				return BUILDS.getLong (target);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException (e);
			}
		}
	}

	/** Independent oracle: logical index to (value, state), and the first-match scan. */
	private static final class Oracle {
		TreeMap<Integer, Object []> entries = new TreeMap<> ();

		void put (int index, Object value) {
			Object [] old = entries.get (index);
			entries.put (index, new Object [] {value, old == null ? 0L : old [1]});
		}

		void shift (int from, int delta, int dropStart, int dropEnd) {
			TreeMap<Integer, Object []> next = new TreeMap<> ();
			for (Map.Entry<Integer, Object []> e : entries.entrySet ()) {
				int k = e.getKey ();
				if (k >= dropStart && k < dropEnd) continue;
				next.put (k >= from ? k + delta : k, e.getValue ());
			}
			entries = next;
		}

		void insert (int index, Object value) {
			shift (index, 1, 0, 0);
			entries.put (index, new Object [] {value, 0L});
		}

		void remove (int index) {
			shift (index + 1, -1, index, index + 1);
		}

		void removeRange (int start, int end) {
			shift (end, start - end, start, end);
		}

		void truncate (int count) {
			entries.tailMap (count, true).clear ();
		}

		int releaseOutside (int start, int end, long keep) {
			int dropped = 0;
			for (Iterator<Map.Entry<Integer, Object []>> it = entries.entrySet ().iterator (); it.hasNext ();) {
				Map.Entry<Integer, Object []> e = it.next ();
				int k = e.getKey ();
				if ((k < start || k >= end) && ((Long) e.getValue () [1] & keep) == 0) {
					it.remove ();
					dropped++;
				}
			}
			return dropped;
		}

		int indexOf (Object value) {
			for (Map.Entry<Integer, Object []> e : entries.entrySet ()) {
				if (e.getValue () [0] == value) return e.getKey ();
			}
			return -1;
		}

		long stateOf (Object value) {
			int index = indexOf (value);
			return index < 0 ? 0L : (Long) entries.get (index) [1];
		}

		void flag (Object value, long mask, boolean enabled) {
			Object [] entry = entries.get (indexOf (value));
			long state = (Long) entry [1];
			entry [1] = enabled ? state | mask : state & ~mask;
		}
	}

	@Test
	public void identityQueriesAgreeWithAFirstMatchScanUnderRandomEdits () {
		for (long seed : new long [] {20261125L, 7L, 4242L}) {
			Random random = new Random (seed);
			Storage storage = new Storage ();
			Oracle oracle = new Oracle ();
			List<Object> pool = new ArrayList<> ();
			Object [] absent = {new Object (), new Object ()};
			for (int step = 0; step < 3000; step++) {
				int op = random.nextInt (100);
				int index = random.nextInt (120);
				/* mostly fresh values; sometimes a value already stored, so a value can sit at two indices */
				Object value = pool.isEmpty () || random.nextInt (12) != 0 ? new Object () : pool.get (random.nextInt (pool.size ()));
				if (op < 40) {
					storage.put (index, value);
					oracle.put (index, value);
					pool.add (value);
				} else if (op < 52) {
					storage.insert (index, value);
					oracle.insert (index, value);
					pool.add (value);
				} else if (op < 62) {
					storage.remove (index);
					oracle.remove (index);
				} else if (op < 66) {
					int end = index + random.nextInt (20);
					storage.removeRange (index, end);
					oracle.removeRange (index, end);
				} else if (op < 69) {
					storage.truncate (index + 40);
					oracle.truncate (index + 40);
				} else if (op < 73) {
					int end = index + random.nextInt (60);
					long keep = 1L << random.nextInt (3);
					assertEquals (oracle.releaseOutside (index, end, keep), storage.releaseOutside (index, end, keep), "released at step " + step);
				} else if (op < 76) {
					/*
					 * The first release callback (an item's Dispose listener, say) materializes a facade
					 * after the last entry, or not, and throws: nothing was dropped or moved yet.
					 */
					int end = index + random.nextInt (60);
					boolean edit = random.nextBoolean ();
					int late = (oracle.entries.isEmpty () ? 0 : oracle.entries.lastKey () + 1) + random.nextInt (4);
					boolean [] ran = {false};
					try {
						storage.call (Storage.RELEASE_OUTSIDE, index, end, 0L, (Consumer<Object>) v -> {
							ran [0] = true;
							if (edit) storage.put (late, value);
							throw new IllegalStateException ("release callback");
						});
					} catch (IllegalStateException e) {
						assertTrue (ran [0], "only a callback throws");
					}
					if (ran [0] && edit) {
						oracle.put (late, value);
						pool.add (value);
					}
				} else if (op < 98) {
					if (!oracle.entries.isEmpty ()) {
						List<Object []> present = new ArrayList<> (oracle.entries.values ());
						Object target = present.get (random.nextInt (present.size ())) [0];
						long mask = 1L << random.nextInt (3);
						boolean enabled = random.nextBoolean ();
						storage.flagOfIdentity (target, mask, enabled);
						oracle.flag (target, mask, enabled);
					}
				} else {
					storage.clear ();
					oracle.entries.clear ();
				}
				assertEquals (oracle.entries.size (), storage.size (), "size at step " + step);
				for (Object [] entry : oracle.entries.values ()) {
					Object v = entry [0];
					assertEquals (oracle.indexOf (v), storage.indexOfIdentity (v), "indexOfIdentity at step " + step + " seed " + seed);
					assertEquals (oracle.stateOf (v), storage.stateOfIdentity (v), "stateOfIdentity at step " + step + " seed " + seed);
				}
				for (Object v : absent) {
					assertEquals (-1, storage.indexOfIdentity (v));
					assertEquals (0L, storage.stateOfIdentity (v));
				}
				/* a value removed from the store is no longer found */
				if (!pool.isEmpty ()) {
					Object v = pool.get (random.nextInt (pool.size ()));
					assertEquals (oracle.indexOf (v), storage.indexOfIdentity (v), "pooled value at step " + step);
				}
			}
		}
	}

	@Test
	public void identityQueriesProbeAFewSlotsWhateverTheMaterializedCount () {
		final int queries = 10_000;
		for (int count : new int [] {1_000, 100_000}) {
			Random random = new Random (count);
			Storage storage = new Storage ();
			Object [] items = new Object [count];
			for (int i = 0; i < count; i++) {
				items [i] = new Object ();
				storage.put (3 * i, items [i]);
			}
			assertEquals (0, storage.indexOfIdentity (items [0]));
			long builds = storage.builds ();
			long probes = storage.probes ();
			for (int q = 0; q < queries; q++) {
				int i = random.nextInt (count);
				assertEquals (3 * i, storage.indexOfIdentity (items [i]));
			}
			long hitProbes = storage.probes () - probes;
			/* a first-match scan compares count / 2 entries per hit on average: 50,000 at 100,000 entries */
			assertTrue (hitProbes <= 4L * queries, count + " entries: " + hitProbes + " slots probed for " + queries + " hits");
			probes = storage.probes ();
			for (int q = 0; q < queries; q++) {
				assertEquals (-1, storage.indexOfIdentity (new Object ()));
			}
			long missProbes = storage.probes () - probes;
			assertTrue (missProbes <= 8L * queries, count + " entries: " + missProbes + " slots probed for " + queries + " misses");
			assertEquals (builds, storage.builds (), "queries without edits never rebuild the table");

			/* facades made one at a time (paint) keep the table: it is rebuilt only when it doubles */
			for (int i = 0; i < count; i++) {
				Object item = new Object ();
				storage.put (3 * count + i, item);
				assertEquals (3 * count + i, storage.indexOfIdentity (item));
			}
			assertTrue (storage.builds () - builds <= 3, count + " added facades rebuilt the table " + (storage.builds () - builds) + " times");

			/* releasing facades outside a window moves the kept ones: one rebuild, then queries are table probes again */
			builds = storage.builds ();
			storage.releaseOutside (0, 3 * (count / 2), 0L);
			assertEquals (count / 2, storage.size ());
			assertEquals (3 * (count / 4), storage.indexOfIdentity (items [count / 4]));
			assertEquals (-1, storage.indexOfIdentity (items [count - 1]));
			assertTrue (storage.builds () - builds <= 1, "releaseOutside rebuilt the table " + (storage.builds () - builds) + " times");
			builds = storage.builds ();
			assertEquals (0, storage.releaseOutside (0, 3 * (count / 2), 0L));
			assertEquals (3 * (count / 8), storage.indexOfIdentity (items [count / 8]));
			assertEquals (builds, storage.builds (), "a releaseOutside that drops nothing keeps the table");
		}
	}

	/*
	 * releaseOutside copies each kept entry down before it overwrites the old slot. A release
	 * callback that changes the state of a kept entry must reach the slot that survives, as the
	 * first-match scan did.
	 */
	@Test
	public void stateWrittenFromAReleaseCallbackSurvivesTheCompaction () {
		Storage storage = new Storage ();
		int count = 200;
		Object [] items = new Object [count];
		for (int i = 0; i < count; i++) {
			items [i] = new Object ();
			storage.put (i, items [i]);
			if (i % 2 == 1) storage.flagOfIdentity (items [i], 1L, true);
		}
		assertEquals (1L, storage.stateOfIdentity (items [1]));
		int [] calls = {0};
		long [] written = {1L};
		/* the even entries are dropped; the odd ones are copied to half their position */
		int dropped = (Integer) storage.call (Storage.RELEASE_OUTSIDE, count, count, 1L, (Consumer<Object>) v -> {
			int i = 2 * calls [0]++;
			/* items [51] was copied to slot 25 at i == 51; its old slot 51 still holds it until i == 102 */
			if (i > 51 && i < 102) {
				long bit = 1L << (i / 2);
				storage.flagOfIdentity (items [51], bit, true);
				written [0] |= bit;
			}
		});
		assertEquals (count / 2, dropped);
		assertEquals (count / 2, storage.size ());
		assertEquals (written [0], storage.stateOfIdentity (items [51]), "every bit written from the callbacks survives");
		for (int i = 1; i < count; i += 2) {
			if (i != 51) assertEquals (1L, storage.stateOfIdentity (items [i]), "state of entry " + i);
			assertEquals (i, storage.indexOfIdentity (items [i]));
		}
		for (int i = 0; i < count; i += 2) {
			assertEquals (-1, storage.indexOfIdentity (items [i]));
		}
	}

	/*
	 * A release callback that throws (GTK's Widget.release rethrows a Dispose listener's exception)
	 * may already have changed the store, here by materializing a facade: the identity answers that
	 * follow must still be the first-match scan's.
	 */
	@Test
	public void aReleaseCallbackThatEditsAndThrowsLeavesTheAnswersExact () {
		for (boolean edit : new boolean [] {true, false}) {
			Storage storage = new Storage ();
			Object [] items = new Object [20];
			for (int i = 0; i < items.length; i++) {
				items [i] = new Object ();
				storage.put (i, items [i]);
			}
			assertEquals (0, storage.indexOfIdentity (items [0]));
			Object late = new Object ();
			int [] calls = {0};
			IllegalStateException thrown = assertThrows (IllegalStateException.class,
					() -> storage.call (Storage.RELEASE_OUTSIDE, 1, 100, 0L, (Consumer<Object>) v -> {
						calls [0]++;
						if (edit) storage.put (50, late);
						throw new IllegalStateException ("dispose listener");
					}));
			assertEquals ("dispose listener", thrown.getMessage ());
			assertEquals (1, calls [0]);
			assertEquals (items.length + (edit ? 1 : 0), storage.size ());
			for (int i = 0; i < items.length; i++) {
				assertEquals (i, storage.indexOfIdentity (items [i]), "entry " + i);
			}
			if (edit) {
				assertEquals (50, storage.indexOfIdentity (late));
				assertEquals (0L, storage.stateOfIdentity (late));
				storage.flagOfIdentity (late, 1L, true);
				assertEquals (1L, storage.stateOfIdentity (late));
				/* a later non-moving edit keeps answering from the store's entries */
				Object later = new Object ();
				storage.put (60, later);
				assertEquals (60, storage.indexOfIdentity (later));
				assertEquals (50, storage.indexOfIdentity (late));
			} else {
				assertEquals (-1, storage.indexOfIdentity (late));
			}
		}
	}

	/*
	 * The identity table must not keep entries that left the store reachable, whether or not a later
	 * query rebuilds it: after a release down to the scan threshold, after a remove (which moves
	 * entries) followed by truncate (0), and after a release with no query afterwards.
	 */
	@Test
	public void entriesThatLeaveTheStoreBecomeUnreachable () throws InterruptedException {
		final int count = 10_000;
		for (int scenario = 0; scenario < 3; scenario++) {
			Storage storage = new Storage ();
			Object [] items = new Object [count];
			WeakReference<?> [] refs = new WeakReference<?> [count];
			for (int i = 0; i < count; i++) {
				items [i] = new Object ();
				refs [i] = new WeakReference<> (items [i]);
				storage.put (i, items [i]);
			}
			assertEquals (count - 1, storage.indexOfIdentity (items [count - 1]));
			int keep;
			if (scenario == 0) {
				keep = 10;
				storage.releaseOutside (0, keep, 0L);
				for (int i = 0; i < keep; i++) {
					assertEquals (i, storage.indexOfIdentity (items [i]));
				}
			} else if (scenario == 1) {
				keep = 0;
				storage.remove (0);
				storage.truncate (0);
			} else {
				keep = 500;
				storage.releaseOutside (0, keep, 0L);
			}
			assertEquals (keep, storage.size ());
			for (int i = keep; i < count; i++) {
				items [i] = null;
			}
			int reachable = count - keep;
			for (int round = 0; round < 40 && reachable > 0; round++) {
				System.gc ();
				reachable = 0;
				for (int i = keep; i < count; i++) {
					if (refs [i].get () != null) reachable++;
				}
				if (reachable > 0) Thread.sleep (25);
			}
			assertEquals (0, reachable, "scenario " + scenario + ": entries that left the store are still reachable");
			for (int i = 0; i < keep; i++) {
				assertEquals (i, storage.indexOfIdentity (items [i]));
			}
		}
	}
}
