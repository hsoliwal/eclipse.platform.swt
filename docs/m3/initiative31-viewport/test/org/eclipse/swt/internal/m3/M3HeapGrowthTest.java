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
package org.eclipse.swt.internal.m3;

import static org.junit.Assert.*;

import org.junit.*;

/**
 * The growth policy behind {@link M3SparseExtents} (a copy of M3JDK's <code>M3HeapGrowth</code>, see
 * PROVENANCE.tsv). Headless.
 */
public class M3HeapGrowthTest {
	private static final long MIB = 1024L * 1024L;

	@After
	public void restoreSampler () {
		M3HeapGrowth.sampler (null);
	}

	@Test
	public void growIsGeometric () {
		assertEquals (8, M3HeapGrowth.grow (0, 1));
		assertEquals (15, M3HeapGrowth.grow (10, 11));
		assertEquals (100, M3HeapGrowth.grow (10, 100));
		assertEquals (M3HeapGrowth.MAX_ARRAY_SIZE, M3HeapGrowth.grow (Integer.MAX_VALUE - 100, Integer.MAX_VALUE - 50));
		assertThrows (OutOfMemoryError.class, () -> M3HeapGrowth.grow (0, -1));
		assertThrows (OutOfMemoryError.class, () -> M3HeapGrowth.grow (0, Integer.MAX_VALUE));
	}

	@Test
	public void chooseFallsBackToTheMinimumUnderPressure () {
		long max = 1024 * MIB;
		assertThrows (IllegalArgumentException.class, () -> M3HeapGrowth.choose (10, 11, 0, max, 0));
		assertEquals ("geometric is the minimum", 100, M3HeapGrowth.choose (10, 100, 4, max, max));
		assertEquals ("no maximum", 15, M3HeapGrowth.choose (10, 11, 4, 0, 0));
		assertEquals ("unbounded maximum", 15, M3HeapGrowth.choose (10, 11, 4, Long.MAX_VALUE, 0));
		assertEquals ("unknown use", 15, M3HeapGrowth.choose (10, 11, 4, max, -1));
		assertEquals ("inconsistent use", 15, M3HeapGrowth.choose (10, 11, 4, max, max + 1));
		assertEquals ("inside the reserve", 11, M3HeapGrowth.choose (10, 11, 4, max, max - MIB));
		assertEquals ("the copy would not fit", 1_000_001, M3HeapGrowth.choose (1_000_000, 1_000_001, 64, max, max - 70 * MIB));
		assertEquals ("room to grow", 15, M3HeapGrowth.choose (10, 11, 4, max, 0));
	}

	@Test
	public void capacitySamplesTheHeap () {
		int samples = M3HeapGrowth.samples ();
		int exact = M3HeapGrowth.exactChoices ();
		assertEquals (15, M3HeapGrowth.capacity (10, 11, 4));
		assertEquals (samples + 1, M3HeapGrowth.samples ());
		M3HeapGrowth.sampler (new M3HeapGrowth.Sampler () {
			@Override
			public long maxBytes () {
				return 1024 * MIB;
			}

			@Override
			public long usedBytes () {
				return 1024 * MIB - MIB;
			}
		});
		assertEquals (11, M3HeapGrowth.capacity (10, 11, 4));
		assertEquals (exact + 1, M3HeapGrowth.exactChoices ());
		assertEquals ("a minimum that is the geometric size is not an exact choice", 100, M3HeapGrowth.capacity (10, 100, 4));
		assertEquals (exact + 1, M3HeapGrowth.exactChoices ());
	}
}
