/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.Test;

/** Regression tests for the ORIGINAL SWT paint graph's staged primitive backing. */
public final class Test_org_eclipse_swt_widgets_ViewportPaintGraphAtomicGrowth {

    private static final String[] NODE_COLUMNS = {
            "keys", "layers", "x", "y", "width", "height", "firstOutgoing"
    };

    @Test
    public void testNodeAndDirtyWordGrowthRetainAllColumns() throws Exception {
        Fixture fixture = new Fixture();
        for (int id = 0; id < 150; id++) {
            assertEquals(id, fixture.add());
            assertTrue(fixture.isDirty(id));
        }
        assertEquals(150, fixture.nodeCount());
        assertEquals(150, fixture.dirtyCount());
        int length = fixture.arrayLength("keys");
        for (String column : NODE_COLUMNS) assertEquals(length, fixture.arrayLength(column), column);
        assertEquals(3, fixture.arrayLength("dirtyWords"));
        assertEquals(3, fixture.arrayLength("pendingWords"));
        int[] next = (int[]) fixture.field("firstOutgoing");
        for (int id = 0; id < fixture.nodeCount(); id++) assertEquals(-1, next[id]);
        fixture.allClean();
        fixture.invalidate(64);
        assertEquals(1, fixture.dirtyCount());
    }

    @Test
    public void testNodeGrowthWithoutReserveKeepsOriginalArrays() throws Exception {
        Fixture fixture = new Fixture();
        Object[] before = fixture.nodeArrays();
        for (int id = 0; id < 8; id++) fixture.add();
        Object[] after = fixture.nodeArrays();
        for (int i = 0; i < before.length; i++) assertSame(before[i], after[i], NODE_COLUMNS[i]);
        assertEquals(8, fixture.nodeCount());
        fixture.add();
        assertEquals(9, fixture.nodeCount());
        assertNotSame(before[0], fixture.field("keys"));
    }

    @Test
    public void testEdgeGrowthPreservesOrderAndDirtyPropagation() throws Exception {
        Fixture fixture = new Fixture();
        for (int id = 0; id < 140; id++) fixture.add();
        for (int id = 1; id < 140; id++) fixture.addDependency(0, id);
        assertEquals(fixture.arrayLength("edgeTo"), fixture.arrayLength("edgeNext"));
        fixture.allClean();
        fixture.invalidate(0);
        assertEquals(140, fixture.dirtyCount());
        List<Integer> visited = new ArrayList<>();
        fixture.forEachDirty(visited::add);
        assertEquals(140, visited.size());
        for (int i = 0; i < visited.size(); i++) assertEquals(i, visited.get(i));
    }

    @Test
    public void testInputFailuresDoNotPublishPartialLogicalNodes() throws Exception {
        Fixture fixture = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.addInvalidLayer());
        assertThrows(IllegalArgumentException.class, () -> fixture.addInvalidBounds());
        assertEquals(0, fixture.nodeCount());
        assertEquals(0, fixture.dirtyCount());
        int one = fixture.add();
        assertThrows(IllegalArgumentException.class, () -> fixture.addDependency(one, one));
        assertEquals(1, fixture.nodeCount());
    }

    private static final class Fixture {
        private final Class<?> type;
        private final Object graph;
        private final Method addNode;
        private final Method addDependency;
        private final Method nodeCount;
        private final Method markAllClean;
        private final Method invalidateBounds;
        private final Method isDirty;
        private final Method dirtyCount;
        private final Method forEachDirty;
        private final int body;

        Fixture() throws ReflectiveOperationException {
            type = Class.forName("org.eclipse.swt.widgets.ViewportPaintGraph");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            graph = constructor.newInstance();
            addNode = method("addNode", long.class, int.class, long.class, long.class, long.class, long.class);
            addDependency = method("addDependency", int.class, int.class);
            nodeCount = method("nodeCount");
            markAllClean = method("markAllClean");
            invalidateBounds = method("invalidateBounds", long.class, long.class, long.class, long.class, int.class);
            isDirty = method("isDirty", int.class);
            dirtyCount = method("dirtyCount");
            forEachDirty = method("forEachDirty", IntConsumer.class);
            Class<?> state = Class.forName("org.eclipse.swt.widgets.ViewportLayerState");
            Field mask = state.getDeclaredField("BODY");
            mask.setAccessible(true);
            body = mask.getInt(null);
        }

        private Method method(String name, Class<?>... parameters) throws ReflectiveOperationException {
            Method method = type.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        }
        Object field(String name) throws ReflectiveOperationException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(graph);
        }
        int arrayLength(String name) throws ReflectiveOperationException {
            return Array.getLength(field(name));
        }
        Object[] nodeArrays() throws ReflectiveOperationException {
            Object[] result = new Object[NODE_COLUMNS.length];
            for (int i = 0; i < result.length; i++) result[i] = field(NODE_COLUMNS[i]);
            return result;
        }
        private Object call(Method method, Object... args) {
            try { return method.invoke(graph, args); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Error error) throw error;
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new AssertionError(failure.getCause());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        int add() { return (int) call(addNode, (long) nodeCount(), body, (long) nodeCount() * 2L, 0L, 1L, 1L); }
        void addInvalidLayer() { call(addNode, 0L, 0, 0L, 0L, 1L, 1L); }
        void addInvalidBounds() { call(addNode, 0L, body, 0L, 0L, -1L, 1L); }
        void addDependency(int source, int dependant) { call(addDependency, source, dependant); }
        int nodeCount() { return (int) call(nodeCount); }
        int dirtyCount() { return (int) call(dirtyCount); }
        boolean isDirty(int id) { return (boolean) call(isDirty, id); }
        void allClean() { call(markAllClean); }
        void invalidate(int id) { call(invalidateBounds, id * 2L, 0L, 1L, 1L, body); }
        void forEachDirty(IntConsumer visitor) { call(forEachDirty, visitor); }
    }
}
