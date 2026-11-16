/* SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

/** Java 21 proof over actual SWT tree topology, projection and resident plan classes. */
public final class VirtualTreeResidentScratchContract {
	private VirtualTreeResidentScratchContract () { }

	private static void check (boolean value, String message) {
		if (!value) throw new AssertionError (message);
	}

	private static int [] scratch (VirtualTreeResidentPlan plan, String name) throws Exception {
		Field field = VirtualTreeResidentPlan.class.getDeclaredField (name);
		field.setAccessible (true);
		return (int []) field.get (plan);
	}

	private static void verifyAfterMove (VirtualTreeResidentPlan plan,
			VirtualTreeVisibleProjection.Residency source, long [] handles, int previousFirst) {
		for (int entry = 0; entry < source.size (); entry++) {
			int row = source.childIndex (entry);
			long expected = row >= previousFirst && row < previousFirst + handles.length
					? handles [row - previousFirst] : 0L;
			check (plan.plannedHandle (entry) == expected,
					"native identity lost row=" + row);
		}
	}

	public static void main (String [] args) throws Exception {
		VirtualTreeTopology topology = new VirtualTreeTopology ();
		topology.setChildCount (VirtualTreeTopology.ROOT, 128);
		VirtualTreeVisibleProjection projection = new VirtualTreeVisibleProjection (topology);
		VirtualTreeVisibleProjection.Residency packet = new VirtualTreeVisibleProjection.Residency ();
		VirtualTreeResidentPlan plan = new VirtualTreeResidentPlan ();
		projection.residencyWindow (0L, 96, packet);
		plan.plan (packet);
		int [] parents = scratch (plan, "scratchParentIds");
		int [] children = scratch (plan, "scratchChildIndices");
		int [] physical = scratch (plan, "scratchPhysicalParents");
		check (parents.length >= 96, "initial capacity");
		long [] last = new long [96];
		long nextHandle = 1;
		for (int i = 0; i < last.length; i++) last [i] = nextHandle++;
		plan.commit (last);
		int previousFirst = 0;
		Random random = new Random (20261116L);
		long reused = 0, generated = 96;
		for (int iteration = 0; iteration < 12_000; iteration++) {
			int first = random.nextInt (129);
			int wanted = random.nextInt (97);
			projection.residencyWindow ((long)first, wanted, packet);
			plan.plan (packet);
			check (scratch (plan, "scratchParentIds") == parents, "parent lane replaced");
			check (scratch (plan, "scratchChildIndices") == children, "child lane replaced");
			check (scratch (plan, "scratchPhysicalParents") == physical, "physical lane replaced");
			verifyAfterMove (plan, packet, last, previousFirst);
			long [] current = new long [packet.size ()];
			for (int row = 0; row < packet.size (); row++) {
				long old = plan.plannedHandle (row);
				if (old != 0) reused++;
				else generated++;
				current [row] = old == 0 ? nextHandle++ : old;
			}
			plan.commit (current);
			for (int row = 0; row < packet.size (); row++) {
				int actual = plan.entryOf (current [row]);
				check (actual == row, "committed native handle lookup");
			}
			last = current;
			previousFirst = first;
		}
		plan.clear ();
		projection.residencyWindow (20L, 5, packet);
		plan.plan (packet);
		check (scratch (plan, "scratchParentIds") == parents, "clear released capacity");
		check (scratch (plan, "scratchChildIndices") == children, "clear child capacity");
		check (scratch (plan, "scratchPhysicalParents") == physical, "clear physical capacity");
		System.out.println ("SWT_TREE_RESIDENT_SCRATCH trials=12000"
				+ " retainedLanes=3"
				+ " capacity=" + parents.length
				+ " reused=" + reused + " new=" + generated
				+ " result=PASS");
	}
}
