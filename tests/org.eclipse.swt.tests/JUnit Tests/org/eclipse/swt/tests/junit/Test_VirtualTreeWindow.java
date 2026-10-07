/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Headless semantic oracle for the common Tree viewport, shared by all backends. */
public class Test_VirtualTreeWindow {
	private record Expected (int parentId, int childIndex, int materializedId, int depth) { }

	private static Object call (Object target, String name, Object... args) throws Exception {
		Class<?> [] types = Arrays.stream (args).map (v -> v instanceof Integer ? int.class
				: v instanceof Long ? long.class : v instanceof Boolean ? boolean.class : v.getClass ())
				.toArray (Class<?> []::new);
		Method method = target.getClass ().getDeclaredMethod (name, types);
		method.setAccessible (true);
		try {
			return method.invoke (target, args);
		} catch (InvocationTargetException failure) {
			if (failure.getCause () instanceof Error error) throw error;
			if (failure.getCause () instanceof Exception exception) throw exception;
			throw failure;
		}
	}

	private static Object construct (String name, Object... args) throws Exception {
		Class<?> type = Class.forName ("org.eclipse.swt.widgets." + name);
		Constructor<?> constructor = type.getDeclaredConstructor (
				Arrays.stream (args).map (Object::getClass).toArray (Class<?> []::new));
		constructor.setAccessible (true);
		return constructor.newInstance (args);
	}

	private static long expanded () throws Exception {
		Field field = Class.forName ("org.eclipse.swt.widgets.VirtualItemState").getDeclaredField ("EXPANDED");
		field.setAccessible (true);
		return field.getLong (null);
	}

	private static Expected row (Object row) throws Exception {
		return new Expected ((int)call (row, "parentId"), (int)call (row, "childIndex"),
				(int)call (row, "materializedId"), (int)call (row, "depth"));
	}

	private static List<Expected> window (Object projection, long start, int length) throws Exception {
		Object [] rows = (Object [])call (projection, "window", start, length);
		List<Expected> result = new ArrayList<> (rows.length);
		for (Object value : rows) result.add (row (value));
		return result;
	}

	/** Test-only eager model, independent of topology aggregate and sibling lanes. */
	private static final class Node {
		int id, count;
		boolean known, expanded;
		Map<Integer, Node> children = new TreeMap<> ();
	}

	private static Node fixture (Random random, int depth, int [] nextId) {
		Node node = new Node ();
		node.id = nextId [0]++;
		node.known = depth == 0 || random.nextInt (5) != 0;
		node.expanded = depth == 0 || random.nextBoolean ();
		node.count = node.known && depth < 4 ? random.nextInt (6) : 0;
		for (int i = 0; i < node.count; i++) {
			if (random.nextBoolean ()) node.children.put (i, fixture (random, depth + 1, nextId));
		}
		return node;
	}

	private static void bind (Object topology, Node node, long bit) throws Exception {
		if (node.known) call (topology, "setChildCount", node.id, node.count);
		for (var entry : node.children.entrySet ()) {
			Node child = entry.getValue ();
			call (topology, "bind", child.id, node.id, entry.getKey ());
			bind (topology, child, bit);
			call (topology, "flag", child.id, bit, child.expanded);
		}
	}

	private static void enumerate (Node parent, int depth, List<Expected> rows) {
		if (!parent.known || !parent.expanded) return;
		for (int i = 0; i < parent.count; i++) {
			Node child = parent.children.get (i);
			rows.add (new Expected (parent.id, i, child == null ? -1 : child.id, depth));
			if (child != null) enumerate (child, depth + 1, rows);
		}
	}

	@Test
	public void frozenSparseForestPermutationsMatchIndependentPreorder () throws Exception {
		long bit = expanded ();
		for (int seed = 0; seed < 64; seed++) {
			Node root = fixture (new Random (0x535754L + seed), 0, new int [] {-1});
			Object topology = construct ("VirtualTreeTopology");
			bind (topology, root, bit);
			Object projection = construct ("VirtualTreeVisibleProjection", topology);
			List<Expected> expected = new ArrayList<> ();
			enumerate (root, 0, expected);
			assertEquals ((long)expected.size (), call (projection, "visibleRowCount"));
			for (int offset = 0; offset <= expected.size () + 1; offset++) {
				for (int length : new int [] {0, 1, 2, 7, 31, Integer.MAX_VALUE}) {
					int end = (int)Math.min ((long)expected.size (), (long)offset + length);
					List<Expected> want = offset >= expected.size () ? List.of () : expected.subList (offset, end);
					assertEquals (want, window (projection, offset, length), "seed=" + seed + " offset=" + offset);
				}
				if (offset < expected.size ()) assertEquals (expected.get (offset), row (call (projection, "rowAt", (long)offset)));
			}
			assertEquals (List.of (), window (projection, Long.MAX_VALUE, 4));
		}
	}

	@Test
	public void coldGapsAndLongOffsetsNeverMaterializeLogicalSiblings () throws Exception {
		Object topology = construct ("VirtualTreeTopology");
		call (topology, "setChildCount", -1, Integer.MAX_VALUE);
		call (topology, "bind", 0, -1, 1);
		call (topology, "setChildCount", 0, Integer.MAX_VALUE);
		call (topology, "flag", 0, expanded (), true);
		Object projection = construct ("VirtualTreeVisibleProjection", topology);
		assertEquals (2L * Integer.MAX_VALUE, call (projection, "visibleRowCount"));
		assertEquals (List.of (new Expected (0, Integer.MAX_VALUE - 2, -1, 1),
				new Expected (0, Integer.MAX_VALUE - 1, -1, 1),
				new Expected (-1, 2, -1, 0), new Expected (-1, 3, -1, 0)),
				window (projection, Integer.MAX_VALUE, 4));
		assertEquals (List.of (new Expected (-1, Integer.MAX_VALUE - 1, -1, 0)),
				window (projection, 2L * Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
		assertEquals (1, call (topology, "materializedCount"));
	}

	@Test
	public void deepWindowUnwindsWithoutRecursiveStackOrSiblingExpansion () throws Exception {
		int depth = 12_000;
		Object topology = construct ("VirtualTreeTopology");
		call (topology, "setChildCount", -1, 2);
		for (int id = 0; id < depth; id++) {
			call (topology, "bind", id, id - 1, 0);
			call (topology, "setChildCount", id, id + 1 == depth ? 0 : 1);
		}
		long bit = expanded ();
		for (int id = depth - 1; id >= 0; id--) call (topology, "flag", id, bit, true);
		Object projection = construct ("VirtualTreeVisibleProjection", topology);
		List<Expected> expected = new ArrayList<> ();
		for (int id = depth - 5; id < depth; id++) expected.add (new Expected (id - 1, 0, id, id));
		expected.add (new Expected (-1, 1, -1, 0));
		assertEquals (expected, window (projection, depth - 5, 64));
		assertEquals (depth, call (topology, "materializedCount"));
	}

	@Test
	public void windowsAreFreshAfterExpansionRebindAndPruning () throws Exception {
		Object topology = construct ("VirtualTreeTopology");
		call (topology, "setChildCount", -1, 4);
		call (topology, "bind", 0, -1, 1);
		call (topology, "setChildCount", 0, 2);
		Object projection = construct ("VirtualTreeVisibleProjection", topology);
		List<Expected> before = window (projection, 0, 20);
		call (topology, "flag", 0, expanded (), true);
		assertEquals (List.of (new Expected (0, 0, -1, 1), new Expected (0, 1, -1, 1),
				new Expected (-1, 2, -1, 0)), window (projection, 2, 3));
		call (topology, "bind", 0, -1, 3);
		assertEquals (List.of (new Expected (-1, 3, 0, 0), new Expected (0, 0, -1, 1),
				new Expected (0, 1, -1, 1)), window (projection, 3, 3));
		call (topology, "setChildCount", -1, 3);
		assertEquals (List.of (new Expected (-1, 2, -1, 0)), window (projection, 2, 4));
		assertEquals (4, before.size ());
		assertEquals (new Expected (-1, 1, 0, 0), before.get (1));
	}

	@Test
	public void emptyUnknownAndInvalidWindowContractsSurvive () throws Exception {
		Object projection = construct ("VirtualTreeVisibleProjection", construct ("VirtualTreeTopology"));
		assertEquals (List.of (), window (projection, 0, Integer.MAX_VALUE));
		assertThrows (IllegalArgumentException.class, () -> window (projection, -1, 0));
		assertThrows (IllegalArgumentException.class, () -> window (projection, 0, -1));
	}
}
