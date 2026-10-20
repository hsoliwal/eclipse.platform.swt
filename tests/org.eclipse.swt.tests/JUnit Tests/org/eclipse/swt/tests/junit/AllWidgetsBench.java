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

import java.lang.management.*;
import java.util.*;
import java.util.logging.*;

/**
 * The measuring rules of the optimization pass (C:/m3-superset/tools/OPTIMIZATION_PASS.md, brief rule 23) for the
 * all-widgets benches: an operation runs five times, the median wall time and the median of the bytes the thread
 * allocated are reported, one JUL line per widget and size (logger <code>org.eclipse.swt.tests.junit.AllWidgetsBench</code>).
 */
final class AllWidgetsBench {
	static final Logger LOG = Logger.getLogger ("org.eclipse.swt.tests.junit.AllWidgetsBench");
	static final int ROUNDS = 5;
	private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();

	/** One measured operation: wall time in microseconds and allocated bytes, medians of {@link #ROUNDS}. */
	record Result (String operation, long micros, long bytes) {
		@Override
		public String toString () {
			return operation + " " + micros + " us " + bytes + " B";
		}
	}

	private AllWidgetsBench () {
	}

	/** Runs the operation {@link #ROUNDS} times (round index as argument) and returns the medians. */
	static Result measure (String operation, java.util.function.IntConsumer round) {
		long [] micros = new long [ROUNDS], bytes = new long [ROUNDS];
		for (int k = 0; k < ROUNDS; k++) {
			long allocated = THREADS.getCurrentThreadAllocatedBytes ();
			long start = System.nanoTime ();
			round.accept (k);
			micros [k] = (System.nanoTime () - start) / 1000;
			bytes [k] = THREADS.getCurrentThreadAllocatedBytes () - allocated;
		}
		return new Result (operation, median (micros), median (bytes));
	}

	/** The sizes of a bench: the property's comma list, else the default. */
	static int [] counts (String property, String defaults) {
		return Arrays.stream (System.getProperty (property, defaults).split (",")).mapToInt (Integer::parseInt).toArray ();
	}

	/** The heap in use after a collection, in megabytes. */
	static long retainedMegabytes () {
		System.gc ();
		Runtime runtime = Runtime.getRuntime ();
		return (runtime.totalMemory () - runtime.freeMemory ()) >> 20;
	}

	static void log (String widget, String reference, int count, long createMillis, List<Result> results) {
		StringBuilder line = new StringBuilder (widget).append ("-bench ").append (reference).append (" n ").append (count)
			.append (" create ").append (createMillis).append (" ms retained ").append (retainedMegabytes ()).append (" MB");
		for (Result result : results) line.append (" | ").append (result);
		LOG.info (line.toString ());
	}

	static long median (long [] values) {
		long [] sorted = values.clone ();
		Arrays.sort (sorted);
		return sorted [sorted.length / 2];
	}
}
