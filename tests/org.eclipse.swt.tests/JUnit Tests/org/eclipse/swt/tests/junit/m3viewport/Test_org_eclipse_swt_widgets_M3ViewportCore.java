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
package org.eclipse.swt.tests.junit.m3viewport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.eclipse.swt.widgets.M3DamageTracker;
import org.eclipse.swt.widgets.M3LogicalExtent;
import org.eclipse.swt.widgets.M3Planes;
import org.eclipse.swt.widgets.M3ResidentWindow;
import org.junit.jupiter.api.Test;

/**
 * Contract of the public M3 viewport core facades. The resident window is checked against the reference
 * arithmetic of the M3JDK Swing port's window contract (the "original" side of the differential) over
 * random geometry; the facades hold no widget, so the answers are mode-neutral.
 */
public class Test_org_eclipse_swt_widgets_M3ViewportCore {
	static int [] reference (int count, int first, int visible, int overscan) {
		int start = Math.min (first, count);
		int end = visible == 0 ? start : (int) Math.min ((long) count, (long) start + visible);
		boolean none = visible == 0 || start == end;
		return new int [] { start, end, none ? start : Math.max (0, start - overscan), none ? end : (int) Math.min ((long) count, (long) end + overscan) };
	}

	@Test
	public void residentWindowEqualsReference () {
		M3ResidentWindow w = new M3ResidentWindow ();
		Random r = new Random (20261031);
		int [] out = new int [M3ResidentWindow.LANES];
		for (int i = 0; i < 20_000; i++) {
			int count = r.nextInt (5) == 0 ? r.nextInt (3) : r.nextInt (2_000_000), first = r.nextInt (count + 3), visible = r.nextInt (60), over = r.nextInt (10);
			w.project (count, first, visible, over, out, 0);
			assertArrayEquals (reference (count, first, visible, over), out, count + "/" + first + "/" + visible + "/" + over);
			assertTrue (w.residentCount () <= visible + 2 * over);
		}
	}

	@Test
	public void residentWindowRejectsBadArguments () {
		M3ResidentWindow w = new M3ResidentWindow ();
		assertThrows (IllegalArgumentException.class, () -> w.project (-1, 0, 1, 0, new int [4], 0));
		assertThrows (IndexOutOfBoundsException.class, () -> w.project (1, 0, 1, 0, new int [4], 1));
		w.project (10, 0, 0, 2, new int [4], 0);
		assertEquals (0, w.residentCount ());
	}

	@Test
	public void logicalExtentBeyondInt () {
		M3LogicalExtent e = new M3LogicalExtent ();
		e.configure (1_000_000, 4096, 260);
		assertEquals (4_096_000_000L, e.contentExtent ());
		assertTrue (e.scrollbarNeeded () && e.visibleRows () == 1 && e.maxTopRow () == 999_999);
		int [] t = new int [2];
		e.thumb (500_000, 200, t);
		assertTrue (t [0] > 0 && t [1] >= 8 && t [0] + t [1] <= 200);
		e.thumb (0, 200, t);
		assertEquals (0, t [0]);
		e.configure (3, 20, 260);
		assertFalse (e.scrollbarNeeded ());
		assertThrows (IllegalArgumentException.class, () -> e.configure (1, 0, 1));
	}

	@Test
	public void planesDirtyMasks () {
		M3Planes p = new M3Planes ();
		assertEquals (0, p.scrollTo (0, 0));
		int v = p.scrollTo (0, 40);
		assertTrue ((v & M3Planes.BODY) != 0 && (v & M3Planes.HEADER) == 0, "vertical scroll keeps the header plane");
		int h = p.scrollTo (30, 40);
		assertTrue ((h & M3Planes.HEADER) != 0 && (h & M3Planes.FROZEN) == 0, "horizontal scroll keeps the frozen plane");
		assertEquals (0, p.scrollTo (30, 40));
		p.initialize (1, 2);
		assertEquals (1.0, p.originX ());
		assertEquals (2.0, p.originY ());
		assertEquals (M3Planes.ALL, M3Planes.ALL & (M3Planes.BODY | M3Planes.FROZEN | M3Planes.HEADER | M3Planes.EDITOR | M3Planes.SCROLLBAR | M3Planes.FEEDBACK));
	}

	@Test
	public void damageTrackerUnion () {
		M3DamageTracker d = new M3DamageTracker ();
		int [] out = new int [4];
		assertFalse (d.take (out));
		d.add (10, 10, 5, 5);
		d.add (40, 2, 4, 4);
		d.add (0, 0, 0, 9);
		assertTrue (!d.isEmpty () && d.area () == 41);
		assertTrue (d.take (out));
		assertArrayEquals (new int [] { 10, 2, 34, 13 }, out);
		assertTrue (d.isEmpty ());
		d.addCopy (0, 0, 100, 50, 0, 5);
		assertEquals (1, d.copyCount ());
		d.take (out);
	}
}
