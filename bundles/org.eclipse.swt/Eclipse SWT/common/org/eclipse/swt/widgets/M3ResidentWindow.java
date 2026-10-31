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
package org.eclipse.swt.widgets;

/**
 * Public M3 view of the viewport's resident row window (enhanced API M3-VISIBLE-RANGE, M3-OVERSCAN).
 * <p>
 * A thin facade over the package-private {@code VirtualViewportPlanner} that every SWT viewport uses: the
 * caller states the logical count, the first visible row, the number of rows the viewport shows and the
 * overscan; the planner answers the half-open visible range and the resident (paint) range. Results are
 * written into a caller-owned {@code int[]} (DOOP: no per-row allocation). The answers do not depend on
 * the widget mode; the contract equals the M3JDK Swing port's {@code M3ViewportIndexWindow.project}.
 * </p>
 *
 * @since 3.130
 */
public final class M3ResidentWindow {
	/** Offsets of the four lanes written by {@link #project}. */
	public static final int FIRST_VISIBLE = 0, END_VISIBLE = 1, FIRST_RESIDENT = 2, END_RESIDENT = 3, LANES = 4;
	private final VirtualViewportPlanner planner = new VirtualViewportPlanner ();
	private int resident;

	/**
	 * Writes {@code [firstVisible, endVisible, firstResident, endResident]} at {@code offset}.
	 *
	 * @param count logical row count, {@code >= 0}
	 * @param first first visible row, {@code >= 0}; clamped to {@code count}
	 * @param visible rows the viewport shows, {@code >= 0}; 0 means no resident work
	 * @param overscan rows kept resident before and after the visible range, {@code >= 0}
	 * @param out caller-owned lanes
	 * @param offset first lane index in {@code out}
	 * @exception IllegalArgumentException if a geometry argument is negative
	 * @exception IndexOutOfBoundsException if {@code out} has no room for four lanes at {@code offset}
	 */
	public void project (int count, int first, int visible, int overscan, int [] out, int offset) {
		if (count < 0 || first < 0 || visible < 0 || overscan < 0) throw new IllegalArgumentException ("negative row geometry");
		if (offset < 0 || offset > out.length - LANES) throw new IndexOutOfBoundsException ("output window");
		int start = Math.min (first, count);
		if (count == 0 || start == count || visible == 0) {
			out [offset] = out [offset + 1] = out [offset + 2] = out [offset + 3] = start;
			resident = 0;
			return;
		}
		if (planner.logicalCount () != count) planner.setLogicalCount (count);
		planner.setOverscanRows (overscan);
		planner.setViewport (start, visible);
		out [offset + FIRST_VISIBLE] = planner.firstVisible ();
		out [offset + END_VISIBLE] = planner.firstVisible () + planner.visibleCount ();
		out [offset + FIRST_RESIDENT] = planner.paintStart ();
		out [offset + END_RESIDENT] = planner.paintEndExclusive ();
		resident = out [offset + END_RESIDENT] - out [offset + FIRST_RESIDENT];
	}

	/** Number of rows {@link #project} last made resident. */
	public int residentCount () {
		return resident;
	}
}
