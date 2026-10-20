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
 * com.m3.collections.M3HeapSampler (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * Heap measurements for {@link M3WarmHeapGrowth}: the JVM's maximum heap and the heap in use. Written for M3 (the
 * EPL-2.0-only Nebula sampler it replaces was not copied); kept separate so a web build can supply its own variant.
 */
public final class M3HeapSampler {
	private M3HeapSampler () {
	}

	/** The maximum heap the JVM will try to use ({@link Runtime#maxMemory()}). */
	public static long maxMemory () {
		return Runtime.getRuntime ().maxMemory ();
	}

	/** The heap in use: total heap minus free heap. */
	public static long usedMemory () {
		Runtime runtime = Runtime.getRuntime ();
		return runtime.totalMemory () - runtime.freeMemory ();
	}
}
