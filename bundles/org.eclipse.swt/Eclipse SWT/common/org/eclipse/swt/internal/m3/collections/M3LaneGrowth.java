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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 118b3cb7e59ea4d4cc32468dccfc6f32e5715dcb,
 * com.m3.collections.M3LaneGrowth (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 553eefd41d9961d303c41e0e4e181712e64a1e45,
 * VirtualGrowth.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * The one growth rule for the packed lanes of the lazy layer (bit lanes, page-source queues and
 * node registries). It delegates to {@link M3HeapGrowth} (SWT #122, ported from Synexia
 * <code>PackedHeapGrowth</code>/<code>M3PreGc</code>): on a capacity miss the heap limit and the
 * used heap are sampled once; geometric growth while it fits, the exact required length under
 * pressure. A GWT build replaces PackedHeapGrowth with plain geometric growth.
 */
public final class M3LaneGrowth {
	private M3LaneGrowth () {
	}

	/**
	 * Returns the new capacity for an array of <code>current</code> elements that must hold
	 * <code>required</code>, each element taking about <code>elementBytes</code> bytes.
	 */
	public static int capacity (int current, int required, int elementBytes) {
		if (required <= current) return current;
		return M3HeapGrowth.capacity (current, required, Math.max (1, elementBytes));
	}
}
