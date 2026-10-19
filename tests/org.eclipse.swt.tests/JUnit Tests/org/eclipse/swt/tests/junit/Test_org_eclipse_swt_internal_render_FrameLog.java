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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/** The frame log of the interaction model (leaf IM1) without a display: percentiles, latency, monotonic generations, the window. */
public class Test_org_eclipse_swt_internal_render_FrameLog {

	@Test
	public void emptyLogAnswersZero () {
		FrameLog log = new FrameLog ();
		assertEquals (0, log.frames ());
		assertEquals (0, log.frameTime (99));
		assertEquals (0, log.inputLatency (99));
		assertTrue (log.monotonic ());
	}

	@Test
	public void framePercentilesAndInputLatency () {
		FrameLog log = new FrameLog ();
		for (int i = 1; i <= 100; i++) log.frame (1000L * i, 1000L * i + i, i);
		assertEquals (100, log.frames ());
		assertEquals (99, log.frameTime (99));
		assertEquals (50, log.frameTime (50));
		assertEquals (1, log.frameTime (0), "a percentile below 1 is the minimum");
		assertEquals (100, log.frameTime (150), "a percentile above 100 is the maximum");
		assertEquals (0, log.inputLatency (99), "no frame followed an input");
		log.input (500_000);
		log.input (600_000);
		log.frame (700_000, 800_000, 101);
		assertEquals (300_000, log.inputLatency (99), "the latency runs from the oldest input not yet shown");
		log.frame (900_000, 900_010, 102);
		assertEquals (300_000, log.inputLatency (50), "a frame without input has no latency");
		assertTrue (log.monotonic ());
	}

	@Test
	public void generationsThatGoBackAreSeenAndTheWindowSlides () {
		FrameLog log = new FrameLog ();
		log.frame (0, 1, 5);
		log.frame (2, 3, 4);
		assertFalse (log.monotonic (), "invariant I2: an older snapshot after a newer one");
		for (int i = 0; i < FrameLog.CAPACITY; i++) log.frame (10 + i, 12 + i, 10 + i);
		assertTrue (log.monotonic (), "the window keeps the last frames only");
		assertEquals (FrameLog.CAPACITY + 2, log.frames ());
		assertEquals (2, log.frameTime (100));
	}
}
