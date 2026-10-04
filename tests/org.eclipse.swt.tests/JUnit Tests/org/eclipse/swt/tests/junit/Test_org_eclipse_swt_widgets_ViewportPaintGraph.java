/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import org.junit.jupiter.api.*;

public class Test_org_eclipse_swt_widgets_ViewportPaintGraph {

	@Test
	public void test_affineViewportTranslationAndInverse() throws Exception {
		Class<?> type = Class.forName ("org.eclipse.swt.widgets.ViewportAffineTransform");
		Method translation = type.getDeclaredMethod ("translation", double.class, double.class);
		Method map = type.getDeclaredMethod ("map", double.class, double.class);
		Method inverse = type.getDeclaredMethod ("inverse");
		Method mapBounds = type.getDeclaredMethod (
				"mapBounds", long.class, long.class, long.class, long.class);
		for (Method method : new Method[] {translation, map, inverse, mapBounds}) method.setAccessible (true);

		Object transform = translation.invoke (null, -5_000_000d, -8_000_000d);
		Object point = map.invoke (transform, 5_000_010d, 8_000_020d);
		assertEquals (10d, component (point, "x"), 0d);
		assertEquals (20d, component (point, "y"), 0d);

		Object inverseTransform = inverse.invoke (transform);
		Object logical = map.invoke (inverseTransform, 10d, 20d);
		assertEquals (5_000_010d, component (logical, "x"), 0d);
		assertEquals (8_000_020d, component (logical, "y"), 0d);

		Object bounds = mapBounds.invoke (
				transform, 5_000_000L, 8_000_000L, 100L, 40L);
		assertEquals (0d, component (bounds, "x"), 0d);
		assertEquals (0d, component (bounds, "y"), 0d);
		assertEquals (100d, component (bounds, "width"), 0d);
		assertEquals (40d, component (bounds, "height"), 0d);
	}

	@Test
	public void test_paintGraphDirtyBoundsLayersAndDependencies() throws Exception {
		Class<?> graphType = Class.forName ("org.eclipse.swt.widgets.ViewportPaintGraph");
		Constructor<?> constructor = graphType.getDeclaredConstructor ();
		constructor.setAccessible (true);
		Object graph = constructor.newInstance ();

		Class<?> layers = Class.forName ("org.eclipse.swt.widgets.ViewportLayerState");
		int body = constant (layers, "BODY");
		int header = constant (layers, "HEADER");
		int feedback = constant (layers, "FEEDBACK");

		Method addNode = graphType.getDeclaredMethod (
				"addNode", long.class, int.class, long.class, long.class, long.class, long.class);
		Method addDependency = graphType.getDeclaredMethod ("addDependency", int.class, int.class);
		Method markAllClean = graphType.getDeclaredMethod ("markAllClean");
		Method invalidateBounds = graphType.getDeclaredMethod (
				"invalidateBounds",
				long.class, long.class, long.class, long.class, int.class);
		Method invalidateLayers = graphType.getDeclaredMethod ("invalidateLayers", int.class);
		Method isDirty = graphType.getDeclaredMethod ("isDirty", int.class);
		Method dirtyCount = graphType.getDeclaredMethod ("dirtyCount");
		for (Method method : new Method[] {
				addNode, addDependency, markAllClean, invalidateBounds,
				invalidateLayers, isDirty, dirtyCount}) {
			method.setAccessible (true);
		}

		int row0 = (Integer)addNode.invoke (graph, 1L, body, 0L, 0L, 500L, 20L);
		int row1 = (Integer)addNode.invoke (graph, 2L, body, 0L, 20L, 500L, 20L);
		int columnHeader = (Integer)addNode.invoke (graph, 3L, header, 0L, 0L, 500L, 24L);
		int hover = (Integer)addNode.invoke (graph, 4L, feedback, 0L, 20L, 500L, 20L);
		addDependency.invoke (graph, row1, hover);
		markAllClean.invoke (graph);

		invalidateBounds.invoke (graph, 0L, 20L, 500L, 20L, body);
		assertFalse ((Boolean)isDirty.invoke (graph, row0));
		assertTrue ((Boolean)isDirty.invoke (graph, row1));
		assertTrue ((Boolean)isDirty.invoke (graph, hover),
				"dependent feedback must become dirty with its source row");
		assertFalse ((Boolean)isDirty.invoke (graph, columnHeader));
		assertEquals (2, ((Integer)dirtyCount.invoke (graph)).intValue ());

		markAllClean.invoke (graph);
		invalidateLayers.invoke (graph, header);
		assertTrue ((Boolean)isDirty.invoke (graph, columnHeader));
		assertFalse ((Boolean)isDirty.invoke (graph, row0));
		assertFalse ((Boolean)isDirty.invoke (graph, row1));
		assertFalse ((Boolean)isDirty.invoke (graph, hover));
	}

	@Test
	public void test_layerPolicyVerticalScrollLeavesHeaderClean() throws Exception {
		Class<?> layerType = Class.forName ("org.eclipse.swt.widgets.ViewportLayerState");
		Constructor<?> constructor = layerType.getDeclaredConstructor ();
		constructor.setAccessible (true);
		Object state = constructor.newInstance ();
		Method initialize = layerType.getDeclaredMethod ("initialize", double.class, double.class);
		Method scrollTo = layerType.getDeclaredMethod ("scrollTo", double.class, double.class);
		initialize.setAccessible (true);
		scrollTo.setAccessible (true);

		int body = constant (layerType, "BODY");
		int frozen = constant (layerType, "FROZEN");
		int header = constant (layerType, "HEADER");
		int scrollbar = constant (layerType, "SCROLLBAR");

		initialize.invoke (state, 0d, 0d);
		int verticalDirty = (Integer)scrollTo.invoke (state, 0d, 400d);
		assertEquals (body | frozen | scrollbar, verticalDirty);
		assertEquals (0, verticalDirty & header,
				"vertical body scrolling must not repaint the stationary header plane");

		int horizontalDirty = (Integer)scrollTo.invoke (state, 120d, 400d);
		assertEquals (body | header | scrollbar, horizontalDirty);
	}

	private static int constant (Class<?> type, String name) throws Exception {
		Field field = type.getDeclaredField (name);
		field.setAccessible (true);
		return field.getInt (null);
	}

	private static double component (Object record, String name) throws Exception {
		Method method = record.getClass ().getDeclaredMethod (name);
		method.setAccessible (true);
		return ((Double)method.invoke (record)).doubleValue ();
	}
}
