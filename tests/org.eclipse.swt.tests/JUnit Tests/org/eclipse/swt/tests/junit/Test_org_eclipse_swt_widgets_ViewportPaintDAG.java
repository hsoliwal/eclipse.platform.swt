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

import org.eclipse.swt.*;
import org.junit.jupiter.api.*;

public class Test_org_eclipse_swt_widgets_ViewportPaintDAG {

	@Test
	public void test_coordinatePlanesAndInverseEventMapping () throws Exception {
		Class<?> type = Class.forName ("org.eclipse.swt.widgets.ViewportPaintDAG");
		Constructor<?> constructor = type.getDeclaredConstructor ();
		constructor.setAccessible (true);
		Object dag = constructor.newInstance ();

		Field rootField = type.getDeclaredField ("ROOT");
		rootField.setAccessible (true);
		int root = rootField.getInt (null);

		Class<?> layers = Class.forName ("org.eclipse.swt.widgets.ViewportLayerState");
		int bodyLayer = intField (layers, "BODY");
		int frozenLayer = intField (layers, "FROZEN");
		int headerLayer = intField (layers, "HEADER");
		int editorLayer = intField (layers, "EDITOR");

		Method addNode = method (type, "addNode", int.class, int.class);
		Method setTranslation = method (type, "setTranslation", int.class, double.class, double.class);
		Method setTransform = method (
				type, "setTransform",
				int.class,
				double.class, double.class, double.class, double.class, double.class, double.class);
		Method mapToRoot = method (
				type, "mapToRoot", int.class, double.class, double.class, double[].class);
		Method mapFromRoot = method (
				type, "mapFromRoot", int.class, double.class, double.class, double[].class);
		Method layer = method (type, "layer", int.class);

		int rootSpace = (int)addNode.invoke (dag, root, 0);
		int body = (int)addNode.invoke (dag, rootSpace, bodyLayer);
		int frozen = (int)addNode.invoke (dag, rootSpace, frozenLayer);
		int header = (int)addNode.invoke (dag, rootSpace, headerLayer);
		int editor = (int)addNode.invoke (dag, body, editorLayer);

		setTranslation.invoke (dag, body, -620d, -1400d);
		setTranslation.invoke (dag, frozen, 0d, -1400d);
		setTranslation.invoke (dag, header, -620d, 0d);
		// 90 degree rotation around a translated editor origin.
		setTransform.invoke (dag, editor, 0d, -1d, 1d, 0d, 80d, 40d);

		double [] point = new double [2];
		mapToRoot.invoke (dag, body, 700d, 1500d, point);
		assertArrayEquals (new double[] {80d, 100d}, point, 0.00001,
				"scrolling body maps logical coordinates through both axes");

		mapToRoot.invoke (dag, header, 700d, 15d, point);
		assertArrayEquals (new double[] {80d, 15d}, point, 0.00001,
				"header follows horizontal projection but remains fixed vertically");

		mapToRoot.invoke (dag, frozen, 40d, 1500d, point);
		assertArrayEquals (new double[] {40d, 100d}, point, 0.00001,
				"frozen plane follows vertical projection without horizontal translation");

		double [] editorRoot = new double [2];
		mapToRoot.invoke (dag, editor, 5d, 10d, editorRoot);
		double [] local = new double [2];
		assertTrue ((boolean)mapFromRoot.invoke (dag, editor, editorRoot[0], editorRoot[1], local));
		assertArrayEquals (new double[] {5d, 10d}, local, 0.00001,
				"mouse/hit-test coordinates must invert the same affine chain used for paint");

		assertEquals (bodyLayer, (int)layer.invoke (dag, body));
		assertEquals (headerLayer, (int)layer.invoke (dag, header));
	}

	@Test
	public void test_clipAndStrokeInheritance () throws Exception {
		Class<?> type = Class.forName ("org.eclipse.swt.widgets.ViewportPaintDAG");
		Constructor<?> constructor = type.getDeclaredConstructor ();
		constructor.setAccessible (true);
		Object dag = constructor.newInstance ();

		Field rootField = type.getDeclaredField ("ROOT");
		rootField.setAccessible (true);
		int root = rootField.getInt (null);

		Method addNode = method (type, "addNode", int.class, int.class);
		Method setTranslation = method (type, "setTranslation", int.class, double.class, double.class);
		Method setClip = method (
				type, "setClip",
				int.class, double.class, double.class, double.class, double.class);
		Method rootClip = method (type, "rootClip", int.class, double[].class);
		Method setStroke = method (
				type, "setStroke", int.class, int.class, int.class, int.class, int.class);
		Method effectiveStroke = method (type, "effectiveStroke", int.class, int[].class);
		Method clearStroke = method (type, "clearStroke", int.class);
		Method nodeCount = method (type, "nodeCount");

		int rootSpace = (int)addNode.invoke (dag, root, 0);
		int body = (int)addNode.invoke (dag, rootSpace, 1);
		int child = (int)addNode.invoke (dag, body, 2);

		setClip.invoke (dag, rootSpace, 0d, 0d, 1000d, 700d);
		setTranslation.invoke (dag, body, -100d, -200d);
		setClip.invoke (dag, body, 100d, 200d, 800d, 600d);
		setTranslation.invoke (dag, child, 50d, 25d);
		setClip.invoke (dag, child, 0d, 0d, 300d, 300d);

		double [] clip = new double [4];
		assertTrue ((boolean)rootClip.invoke (dag, child, clip));
		assertArrayEquals (new double[] { -50d, -175d, 300d, 300d }, clip, 0.00001,
				"child clip is transformed and intersected in root coordinates");

		setStroke.invoke (dag, body, 3, SWT.LINE_DASH, SWT.CAP_ROUND, SWT.JOIN_BEVEL);
		int [] stroke = new int [4];
		assertTrue ((boolean)effectiveStroke.invoke (dag, child, stroke));
		assertArrayEquals (
				new int[] {3, SWT.LINE_DASH, SWT.CAP_ROUND, SWT.JOIN_BEVEL},
				stroke,
				"child inherits nearest viewport stroke state");

		setStroke.invoke (dag, child, 1, SWT.LINE_SOLID, SWT.CAP_FLAT, SWT.JOIN_MITER);
		assertTrue ((boolean)effectiveStroke.invoke (dag, child, stroke));
		assertArrayEquals (
				new int[] {1, SWT.LINE_SOLID, SWT.CAP_FLAT, SWT.JOIN_MITER},
				stroke);
		clearStroke.invoke (dag, child);
		assertTrue ((boolean)effectiveStroke.invoke (dag, child, stroke));
		assertEquals (3, stroke[0]);

		assertEquals (3, (int)nodeCount.invoke (dag));
	}

	@Test
	public void test_singularAndInvalidStateAreRejected () throws Exception {
		Class<?> type = Class.forName ("org.eclipse.swt.widgets.ViewportPaintDAG");
		Constructor<?> constructor = type.getDeclaredConstructor ();
		constructor.setAccessible (true);
		Object dag = constructor.newInstance ();

		int root = intField (type, "ROOT");
		Method addNode = method (type, "addNode", int.class, int.class);
		Method setTransform = method (
				type, "setTransform",
				int.class,
				double.class, double.class, double.class, double.class, double.class, double.class);
		Method mapFromRoot = method (
				type, "mapFromRoot", int.class, double.class, double.class, double[].class);
		Method setClip = method (
				type, "setClip",
				int.class, double.class, double.class, double.class, double.class);

		int node = (int)addNode.invoke (dag, root, 1);
		setTransform.invoke (dag, node, 0d, 0d, 0d, 0d, 0d, 0d);
		assertFalse ((boolean)mapFromRoot.invoke (dag, node, 10d, 10d, new double [2]));

		InvocationTargetException negativeClip = assertThrows (
				InvocationTargetException.class,
				() -> setClip.invoke (dag, node, 0d, 0d, -1d, 10d));
		assertInstanceOf (IllegalArgumentException.class, negativeClip.getCause ());

		InvocationTargetException invalidParent = assertThrows (
				InvocationTargetException.class,
				() -> addNode.invoke (dag, 99, 1));
		assertInstanceOf (IllegalArgumentException.class, invalidParent.getCause ());
	}

	private static Method method (Class<?> type, String name, Class<?>... parameterTypes)
			throws NoSuchMethodException {
		Method method = type.getDeclaredMethod (name, parameterTypes);
		method.setAccessible (true);
		return method;
	}

	private static int intField (Class<?> type, String name)
			throws NoSuchFieldException, IllegalAccessException {
		Field field = type.getDeclaredField (name);
		field.setAccessible (true);
		return field.getInt (null);
	}
}
