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
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 28 bench (docs/m3/SYNCHRONIZER.md), against any jar: bytes a producer thread allocates per asyncExec,
 * throughput of 10^6 asyncExec from 4 threads drained by the UI thread, and the latency of one runnable posted behind a
 * flood of 10^6 (plain, and with the rebuilt dispatcher also in the INPUT class). Medians of 5. Opt-in:
 * <code>-Dswt.allwidgets.bench=true</code>.
 */
public class Test_org_eclipse_swt_widgets_Synchronizer_Bench {
	static final Logger LOG = Logger.getLogger ("org.eclipse.swt.tests.junit.AllWidgetsBench");
	static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();
	static final Runnable NOOP = () -> {};
	Display display;

	void drain (AtomicLong ran, long target) {
		while (ran.get () < target) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
	}

	@Test
	public void measure () throws Exception {
		Assumptions.assumeTrue (Boolean.getBoolean ("swt.allwidgets.bench"), "-Dswt.allwidgets.bench=true is not set");
		display = Display.getDefault ();
		AtomicLong ran = new AtomicLong ();
		Runnable count = ran::incrementAndGet;
		/* bytes per asyncExec on the posting thread */
		long [] bytes = new long [5], throughput = new long [5], latency = new long [5], inputLatency = new long [5];
		for (int round = 0; round < 5; round++) {
			int n = 100_000;
			long [] allocated = {0};
			long base = ran.get ();
			Thread producer = new Thread (() -> {
				for (int i = 0; i < 1000; i++) display.asyncExec (count);
				long start = THREADS.getCurrentThreadAllocatedBytes ();
				for (int i = 0; i < n; i++) display.asyncExec (count);
				allocated [0] = THREADS.getCurrentThreadAllocatedBytes () - start;
			});
			producer.start ();
			drain (ran, base + n + 1000);
			producer.join ();
			bytes [round] = allocated [0] * 1000 / n;
		}
		/* throughput: 10^6 from 4 threads */
		for (int round = 0; round < 5; round++) {
			long base = ran.get (), start = System.nanoTime ();
			Thread [] producers = new Thread [4];
			for (int t = 0; t < 4; t++) {
				producers [t] = new Thread (() -> {
					for (int i = 0; i < 250_000; i++) display.asyncExec (count);
				});
				producers [t].start ();
			}
			drain (ran, base + 1_000_000);
			throughput [round] = 1_000_000L * 1_000_000_000L / (System.nanoTime () - start);
			for (Thread p : producers) p.join ();
		}
		/* latency behind a flood of 10^6 */
		Method keyed = null;
		int input = 0;
		try {
			keyed = Synchronizer.class.getMethod ("asyncExec", int.class, Object.class, Runnable.class);
			input = Synchronizer.class.getField ("INPUT").getInt (null);
		} catch (ReflectiveOperationException e) {
			// the stock Synchronizer: no priority classes
		}
		for (int round = 0; round < 5; round++) {
			for (int i = 0; i < 1_000_000; i++) display.asyncExec (NOOP);
			long [] at = {0, 0};
			long posted = System.nanoTime ();
			display.asyncExec (() -> at [0] = System.nanoTime ());
			if (keyed != null) keyed.invoke (display.getSynchronizer (), input, null, (Runnable) () -> at [1] = System.nanoTime ());
			while (at [0] == 0 || keyed != null && at [1] == 0) {
				if (!display.readAndDispatch ()) display.sleep ();
			}
			latency [round] = (at [0] - posted) / 1000;
			inputLatency [round] = keyed == null ? -1 : (at [1] - posted) / 1000;
		}
		LOG.info ("synchronizer-bench " + display.getSynchronizer ().getClass ().getName () + (keyed == null ? " stock" : " dispatch")
			+ " | bytes/asyncExec(x1000) " + median (bytes) + " | asyncExec/s " + median (throughput)
			+ " | latency behind 10^6 plain " + median (latency) + " us | input class " + median (inputLatency) + " us");
	}

	static long median (long [] values) {
		long [] sorted = values.clone ();
		Arrays.sort (sorted);
		return sorted [sorted.length / 2];
	}
}
