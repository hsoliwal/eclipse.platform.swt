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
 *     Hitesh Soliwal (Synexia) - allocation and map contract regressions
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.*;

import org.junit.jupiter.api.Test;

/** Headless contracts of the primitive row/handle index, including replacement at its load threshold. */
public class Test_VirtualLongIntTable {

	private static final class Table {
		private final Class<?> type;
		private final Object value;

		Table (int expected) throws ReflectiveOperationException {
			type = Class.forName ("org.eclipse.swt.widgets.VirtualLongIntTable");
			Constructor<?> constructor = expected < 0 ? type.getDeclaredConstructor () : type.getDeclaredConstructor (int.class);
			constructor.setAccessible (true);
			value = expected < 0 ? constructor.newInstance () : constructor.newInstance (expected);
		}

		private Object call (String name, Class<?> [] types, Object... arguments) throws ReflectiveOperationException {
			Method method = type.getDeclaredMethod (name, types);
			method.setAccessible (true);
			try {
				return method.invoke (value, arguments);
			} catch (InvocationTargetException failure) {
				if (failure.getCause () instanceof RuntimeException runtime) throw runtime;
				if (failure.getCause () instanceof Error error) throw error;
				throw failure;
			}
		}

		int put (long key, int number) throws ReflectiveOperationException {
			return (Integer) call ("put", new Class<?> [] {long.class, int.class}, key, number);
		}

		int get (long key) throws ReflectiveOperationException {
			return (Integer) call ("get", new Class<?> [] {long.class}, key);
		}

		int remove (long key) throws ReflectiveOperationException {
			return (Integer) call ("remove", new Class<?> [] {long.class}, key);
		}

		int size () throws ReflectiveOperationException {
			return (Integer) call ("size", new Class<?> [0]);
		}

		void clear () throws ReflectiveOperationException {
			call ("clear", new Class<?> [0]);
		}

		Object backing (String name) throws ReflectiveOperationException {
			Field field = type.getDeclaredField (name);
			field.setAccessible (true);
			return field.get (value);
		}
	}

	@Test
	public void replacingAtHalfCapacityKeepsBothPrimitiveArrays () throws Exception {
		for (int expected : new int [] {-1, 0, 4, 16, 4096}) {
			Table table = new Table (expected);
			int entries = ((long []) table.backing ("keys")).length / 2;
			for (int i = 0; i < entries; i++) assertEquals (-1, table.put (i, i));
			Object keys = table.backing ("keys"), values = table.backing ("values");
			for (int i = entries - 1; i >= 0; i--) {
				assertEquals (i, table.put (i, Integer.MAX_VALUE - i));
				assertEquals (Integer.MAX_VALUE - i, table.get (i));
				assertEquals (entries, table.size ());
				assertSame (keys, table.backing ("keys"), "replacing a key must not allocate a larger key lane");
				assertSame (values, table.backing ("values"), "replacing a key must not allocate a larger value lane");
			}
		}
	}

	@Test
	public void addingANewKeyAtHalfCapacityStillGrowsAndKeepsEveryEntry () throws Exception {
		Table table = new Table (-1);
		for (int i = 0; i < 8; i++) assertEquals (-1, table.put (i, i));
		Object keys = table.backing ("keys"), values = table.backing ("values");
		assertEquals (-1, table.put (Long.MIN_VALUE, Integer.MAX_VALUE));
		assertNotSame (keys, table.backing ("keys"));
		assertNotSame (values, table.backing ("values"));
		assertEquals (9, table.size ());
		for (int i = 0; i < 8; i++) assertEquals (i, table.get (i));
		assertEquals (Integer.MAX_VALUE, table.get (Long.MIN_VALUE));
		assertEquals (Integer.MAX_VALUE, table.put (Long.MIN_VALUE, 0));
		assertEquals (0, table.get (Long.MIN_VALUE));
	}

	@Test
	public void wrappedProbeClusterSurvivesReplacementDeletionAndReinsertion () throws Exception {
		// These keys occupy the bucket at the end of the initial 16-slot lane.
		long [] keys = {29, 33, 53, 57, 109, 162, 165, 174};
		Table table = new Table (-1);
		Map<Long, Integer> oracle = new HashMap<> ();
		for (int i = 0; i < keys.length; i++) {
			assertEquals (previous (oracle.put (keys [i], i)), table.put (keys [i], i));
		}
		for (long key : keys) {
			assertEquals (previous (oracle.put (key, Integer.MAX_VALUE)), table.put (key, Integer.MAX_VALUE));
			assertEquals (previous (oracle.remove (key)), table.remove (key));
			assertEquals (-1, table.remove (key));
			for (long probe : keys) assertEquals (previous (oracle.get (probe)), table.get (probe));
			assertEquals (previous (oracle.put (key, 0)), table.put (key, 0));
			assertEquals (oracle.size (), table.size ());
		}
	}

	@Test
	public void extremeKeysAndValuesKeepMapSemantics () throws Exception {
		long [] keys = {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1L << 32};
		Table table = new Table (4);
		for (long key : keys) assertEquals (-1, table.put (key, Integer.MAX_VALUE));
		for (long key : keys) {
			assertEquals (Integer.MAX_VALUE, table.put (key, 0));
			assertEquals (0, table.get (key));
			assertEquals (0, table.remove (key));
			assertEquals (-1, table.get (key));
		}
		assertEquals (0, table.size ());
	}

	@Test
	public void seededOperationsAgreeWithHashMap () throws Exception {
		Random random = new Random (0x535754);
		for (int round = 0; round < 24; round++) {
			Table table = new Table (round % 2 == 0 ? -1 : round);
			Map<Long, Integer> oracle = new HashMap<> ();
			for (int step = 0; step < 2000; step++) {
				long key = random.nextInt (257) - 128;
				if (round % 3 == 1) key <<= 32;
				else if (round % 3 == 2) key += Long.MIN_VALUE;
				switch (random.nextInt (8)) {
					case 0 -> assertEquals (previous (oracle.remove (key)), table.remove (key));
					case 1 -> assertEquals (previous (oracle.get (key)), table.get (key));
					case 2 -> {
						if (step % 31 == 0) {
							oracle.clear ();
							table.clear ();
						}
					}
					default -> {
						int value = random.nextBoolean () ? Integer.MAX_VALUE : random.nextInt (1 << 20);
						assertEquals (previous (oracle.put (key, value)), table.put (key, value));
					}
				}
				assertEquals (oracle.size (), table.size ());
			}
			for (Map.Entry<Long, Integer> entry : oracle.entrySet ()) {
				assertEquals (entry.getValue ().intValue (), table.get (entry.getKey ()), "surviving key");
			}
		}
	}

	@Test
	public void negativeValuesAreRefusedWithoutGrowingOrMutating () throws Exception {
		Table table = new Table (-1);
		for (int i = 0; i < 8; i++) table.put (i, i);
		Object keys = table.backing ("keys"), values = table.backing ("values");
		assertThrows (IllegalArgumentException.class, () -> table.put (0, -1));
		assertThrows (IllegalArgumentException.class, () -> table.put (Long.MAX_VALUE, Integer.MIN_VALUE));
		assertSame (keys, table.backing ("keys"));
		assertSame (values, table.backing ("values"));
		assertEquals (8, table.size ());
		for (int i = 0; i < 8; i++) assertEquals (i, table.get (i));
		assertEquals (-1, table.get (Long.MAX_VALUE));
	}

	private static int previous (Integer value) {
		return value == null ? -1 : value;
	}
}
