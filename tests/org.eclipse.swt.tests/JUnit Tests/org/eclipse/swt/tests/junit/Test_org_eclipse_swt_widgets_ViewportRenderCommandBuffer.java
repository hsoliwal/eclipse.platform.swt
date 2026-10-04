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

import org.junit.jupiter.api.Test;

public class Test_org_eclipse_swt_widgets_ViewportRenderCommandBuffer {

	@Test
	public void test_dirtyReplayUsesPrimitiveRenderStream() throws Exception {
		Class<?> graphType = Class.forName("org.eclipse.swt.widgets.ViewportPaintGraph");
		Constructor<?> graphCtor = graphType.getDeclaredConstructor();
		graphCtor.setAccessible(true);
		Object graph = graphCtor.newInstance();

		Method addNode = graphType.getDeclaredMethod(
				"addNode", long.class, int.class, long.class, long.class, long.class, long.class);
		Method markAllClean = graphType.getDeclaredMethod("markAllClean");
		Method invalidateBounds = graphType.getDeclaredMethod(
				"invalidateBounds", long.class, long.class, long.class, long.class, int.class);
		for (Method method : new Method[] {addNode, markAllClean, invalidateBounds}) method.setAccessible(true);

		Class<?> layerType = Class.forName("org.eclipse.swt.widgets.ViewportLayerState");
		Field bodyField = layerType.getDeclaredField("BODY");
		bodyField.setAccessible(true);
		int body = bodyField.getInt(null);

		int node0 = (Integer)addNode.invoke(graph, 10L, body, 0L, 0L, 100L, 20L);
		int node1 = (Integer)addNode.invoke(graph, 11L, body, 0L, 20L, 100L, 20L);

		Class<?> bufferType = Class.forName("org.eclipse.swt.widgets.ViewportRenderCommandBuffer");
		Constructor<?> bufferCtor = bufferType.getDeclaredConstructor();
		bufferCtor.setAccessible(true);
		Object buffer = bufferCtor.newInstance();

		Class<?> transformType = Class.forName("org.eclipse.swt.widgets.ViewportAffineTransform");
		Method translation = transformType.getDeclaredMethod("translation", double.class, double.class);
		translation.setAccessible(true);
		Object transform = translation.invoke(null, 5.0, 7.0);

		Method save = method(bufferType, "save", int.class);
		Method restore = method(bufferType, "restore", int.class);
		Method concat = method(bufferType, "concatTransform", int.class, transformType);
		Method clip = method(bufferType, "clipRect", int.class, double.class, double.class, double.class, double.class);
		Method stroke = method(bufferType, "strokePath", int.class, long.class);
		Method fill = method(bufferType, "fillPath", int.class, long.class);
		Method text = method(bufferType, "drawText", int.class, long.class, double.class, double.class);
		Method image = method(bufferType, "drawImage", int.class, long.class, double.class, double.class, double.class, double.class);
		Method validate = method(bufferType, "validateBalancedState");

		save.invoke(buffer, node0);
		concat.invoke(buffer, node0, transform);
		clip.invoke(buffer, node0, 0.0, 0.0, 100.0, 20.0);
		stroke.invoke(buffer, node0, 101L);
		fill.invoke(buffer, node0, 102L);
		text.invoke(buffer, node0, 103L, 8.0, 14.0);
		restore.invoke(buffer, node0);
		image.invoke(buffer, node1, 201L, 1.0, 21.0, 16.0, 16.0);
		validate.invoke(buffer);

		markAllClean.invoke(graph);
		invalidateBounds.invoke(graph, 0L, 0L, 100L, 20L, body);

		Class<?> replayerType = Class.forName(
				"org.eclipse.swt.widgets.ViewportRenderCommandBuffer$Replayer");
		List<String> replay = new ArrayList<>();
		Object replayer = Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class<?>[] {replayerType},
				(proxy, invoked, args) -> {
					replay.add(invoked.getName());
					return null;
				});

		Method replayDirty = bufferType.getDeclaredMethod("replayDirty", graphType, replayerType);
		replayDirty.setAccessible(true);
		replayDirty.invoke(buffer, graph, replayer);

		assertEquals(List.of(
				"save", "concatTransform", "clipRect", "strokePath",
				"fillPath", "drawText", "restore"), replay,
				"only commands owned by the dirty paint node should replay");
	}

	@Test
	public void test_unbalancedStateIsRejected() throws Exception {
		Class<?> bufferType = Class.forName("org.eclipse.swt.widgets.ViewportRenderCommandBuffer");
		Constructor<?> ctor = bufferType.getDeclaredConstructor();
		ctor.setAccessible(true);
		Object buffer = ctor.newInstance();

		Method save = method(bufferType, "save", int.class);
		Method validate = method(bufferType, "validateBalancedState");
		save.invoke(buffer, 0);

		InvocationTargetException failure =
				assertThrows(InvocationTargetException.class, () -> validate.invoke(buffer));
		assertInstanceOf(IllegalStateException.class, failure.getCause());
	}

	private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
		Method result = type.getDeclaredMethod(name, parameterTypes);
		result.setAccessible(true);
		return result;
	}
}
