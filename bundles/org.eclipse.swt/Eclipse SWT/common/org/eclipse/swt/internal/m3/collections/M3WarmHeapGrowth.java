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
 * com.m3.collections.M3WarmHeapGrowth (same author; the SWT copy of the viewport collections).
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * Xmx-aware capacity for every growable primitive array of the Grid model.
 *
 * <p>Adapted from Synexia {@code synexia-common/.../collections/PackedHeapGrowth} (#10871, source
 * sha256 {@code 143f793bc326a3fb7fe8e89b955db27163973fd7c83265e61aada9839e971003}) and the warm/high
 * thresholds of {@code synexia-mindex/.../M3PreGc} (#10879, sha256
 * {@code 02a7e9478224ea97ca5ff2ab640538c9c93852f0528bc8399f92cdcce4d2d236}). The heap is sampled
 * once per capacity miss, never per element. Pressure only removes speculative capacity: the
 * required length is always returned, nothing is evicted and no GC is forced.</p>
 *
 * <p>{@link #choose} is pure (GWT-safe). {@link #capacity} samples the heap through
 * {@link M3HeapSampler}, which a GWT build replaces with a sampler that reports no information.</p>
 */
public final class M3WarmHeapGrowth {

	private static final long MIB = 1024L * 1024L;
	private static final int WARM_PERCENT = 70;

	private M3WarmHeapGrowth() {
	}

	/**
	 * @param current      current array length
	 * @param minimum      required length
	 * @param elementBytes bytes per slot (all parallel arrays of one slot), 1..64
	 * @return the new length: doubling when the heap has room, exactly {@code minimum} under
	 *         pressure
	 */
	public static int capacity(final int current, final int minimum, final int elementBytes) {
		return choose(current, minimum, elementBytes, M3HeapSampler.maxMemory(), M3HeapSampler.usedMemory());
	}

	/**
	 * Pure decision with explicit measurements ({@code max <= 0}: no information, plain doubling).
	 *
	 * @param current      current length
	 * @param minimum      required length
	 * @param elementBytes bytes per slot (all parallel arrays of one slot), 1..64
	 * @param max          {@code Runtime.maxMemory()}
	 * @param used         used heap
	 * @return the new length
	 */
	public static int choose(final int current, final int minimum, final int elementBytes, final long max,
			final long used) {
		if (elementBytes < 1 || elementBytes > 64) {
			throw new IllegalArgumentException("bytes per slot 1..64: " + elementBytes);
		}
		if (current < 0 || minimum < 0) {
			throw new IllegalArgumentException("negative array length");
		}
		if (minimum <= current) {
			return current;
		}
		// Never return a capacity smaller than the caller's required length,
		// including the Java array VM-limit boundary. Allocation itself may OOME.
		final long desired = Math.max((long) minimum, Math.max(16L, 2L * current));
		final int geometric = minimum > Integer.MAX_VALUE - 8L
				? minimum : (int) Math.min(Integer.MAX_VALUE - 8L, desired);
		if (geometric == minimum || max <= 0 || max == Long.MAX_VALUE || used < 0 || used > max) {
			return geometric;
		}
		final long available = max - used;
		final long reserve = Math.min(64L * MIB, Math.max(MIB, max >>> 3));
		final long peak = (long) geometric * elementBytes + 16L;
		final long forecast = used > Long.MAX_VALUE - peak ? Long.MAX_VALUE : used + peak;
		final boolean warm = forecast >= (max / 100) * WARM_PERCENT;
		// the replacement is live next to its source during the copy
		if (available <= reserve || peak > available - reserve || warm) {
			return minimum;
		}
		return geometric;
	}
}
