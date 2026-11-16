/* SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.lang.reflect.Field;
import java.util.Random;

/** Actual SWT source contract for stable viewport IDs/coordinates and physical child directory. */
public final class VirtualTreeResidencyIndexContract {
	private VirtualTreeResidencyIndexContract () { }

	private static void check (boolean valid, String label) {
		if (!valid) throw new AssertionError (label);
	}

	private static int [] lane (VirtualTreeVisibleProjection.Residency residency, String name)
			throws Exception {
		Field field = residency.getClass ().getDeclaredField (name);
		field.setAccessible (true);
		return (int []) field.get (residency);
	}

	private static void forest () {
		VirtualTreeTopology topology = new VirtualTreeTopology ();
		topology.setChildCount (VirtualTreeTopology.ROOT, 5);
		topology.bind (0, VirtualTreeTopology.ROOT, 1);
		topology.setChildCount (0, 5);
		topology.flag (0, VirtualItemState.EXPANDED, true);
		topology.bind (1, 0, 2);
		topology.setChildCount (1, 4);
		topology.flag (1, VirtualItemState.EXPANDED, true);
		topology.bind (2, VirtualTreeTopology.ROOT, 4);
		topology.setChildCount (2, 9);
		VirtualTreeVisibleProjection projection = new VirtualTreeVisibleProjection (topology);
		VirtualTreeVisibleProjection.Residency buffer = new VirtualTreeVisibleProjection.Residency ();
		projection.residencyWindow (0, 30, buffer);
		for (int entry = 0; entry < buffer.size (); entry++) {
			check (buffer.physicalChild (buffer.physicalParent (entry),
					buffer.physicalIndex (entry)) == entry, "nested physical child");
			check (buffer.entryForCoordinate (buffer.parentId (entry),
					buffer.childIndex (entry)) == entry, "nested coordinate");
			if (buffer.materializedId (entry) >= 0) {
				check (buffer.entryForMaterializedId (buffer.materializedId (entry)) == entry,
						"nested materialized identity");
			}
		}
		check (buffer.entryForCoordinate (2, 0) < 0, "collapsed child not resident");
		buffer.addExpanderHints (topology);
		int hint = buffer.entryForCoordinate (2, 0);
		check (hint >= 0 && buffer.isHint (hint), "hint append invalidates coordinate index");
		check (buffer.physicalChild (buffer.physicalParent (hint),
				buffer.physicalIndex (hint)) == hint, "hint directory rebuild");
	}

	private static void large () throws Exception {
		VirtualTreeTopology topology = new VirtualTreeTopology ();
		topology.setChildCount (VirtualTreeTopology.ROOT, 10_000);
		VirtualTreeVisibleProjection projection = new VirtualTreeVisibleProjection (topology);
		VirtualTreeVisibleProjection.Residency packet = new VirtualTreeVisibleProjection.Residency ();
		projection.residencyWindow (0L, 10_000, packet);
		check (packet.size () == 10_000, "wide viewport count");
		check (packet.physicalChild (-1, 9999) == 9999, "root last child");
		int [] retained = lane (packet, "physicalChildEntries");
		check (retained.length >= 10_000 && retained.length <= 20_000, "directory bounded");
		Random random = new Random (20261116L);
		long queries = 0;
		for (int i = 0; i < 20_000; i++) {
			int index = random.nextInt (10_000);
			check (packet.physicalChild (-1, index) == index, "wide root child");
			check (packet.entryForCoordinate (VirtualTreeTopology.ROOT, index) == index,
					"wide root coordinate");
			queries += 2;
		}
		packet.bindFacade (9, 42);
		check (packet.entryForMaterializedId (42) == 9, "bind cold facade");
		packet.bindFacade (9, 43);
		check (packet.entryForMaterializedId (42) == -1, "old facade invalidated");
		check (packet.entryForMaterializedId (43) == 9, "new facade");
		for (int sample = 0; sample < 500; sample++) {
			int first = random.nextInt (9_900), count = 100;
			projection.residencyWindow (first, count, packet);
			for (int child = 0; child < count; child++) {
				check (packet.physicalChild (-1, child) == child, "window-local index");
				check (packet.entryForCoordinate (VirtualTreeTopology.ROOT, first + child) == child,
						"window coordinate");
				queries += 2;
			}
			check (packet.entryForMaterializedId (43) == -1, "reset facade binding");
		}
		check (lane (packet, "physicalChildEntries") == retained,
				"smaller windows retain already allocated scratch");
		projection.residencyWindow (0L, 0, packet);
		check (packet.physicalChild (-1, 0) == -1, "collapsed native model");
		check (packet.entryForCoordinate (VirtualTreeTopology.ROOT, 0) == -1,
				"collapsed identity directory");
		System.out.println ("SWT_QT_TREE_INDEX queries=" + queries
				+ " bound=viewport source=actual-owner result=PASS");
	}

	public static void main (String [] args) throws Exception {
		forest ();
		large ();
		System.out.println ("SWT_QT_TREE_INDEX_CONTRACT result=PASS");
	}
}
