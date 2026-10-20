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

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

/**
 * Headless benchmark of one window commit of the Tree viewport (VirtualTreeResidentPlan.plan + commit), the work a
 * scroll, resize or expansion does per layout: nanoseconds and bytes allocated per commit, for a window of W roots
 * moving by 1 row (scroll), by W/2 rows (page) and growing or shrinking by 1 row (resize). Method handles reach the
 * package-private class without boxing, so the allocation counted is the plan's own. Plain main (no Display):
 * <code>java -cp tests;swt.jar org.eclipse.swt.tests.junit.VirtualTreeResidentPlanBenchmark [window] [commits] [runs]</code>.
 */
public class VirtualTreeResidentPlanBenchmark {
	static final Logger LOG = Logger.getLogger (VirtualTreeResidentPlanBenchmark.class.getName ());

	public static void main (String [] args) throws Throwable {
		int window = args.length > 0 ? Integer.parseInt (args [0]) : 60;
		int commits = args.length > 1 ? Integer.parseInt (args [1]) : 200_000;
		int runs = args.length > 2 ? Integer.parseInt (args [2]) : 5;
		Class<?> type = Class.forName ("org.eclipse.swt.widgets.VirtualTreeResidentPlan");
		Constructor<?> constructor = type.getDeclaredConstructor ();
		constructor.setAccessible (true);
		MethodHandle plan = handle (type, "plan", MethodType.methodType (void.class, Object.class, int.class, int [].class, int [].class, int [].class), int.class, int [].class, int [].class, int [].class);
		MethodHandle commit = handle (type, "commit", MethodType.methodType (void.class, Object.class, int.class, int [].class, int [].class, int [].class, long [].class), int.class, int [].class, int [].class, int [].class, long [].class);
		MethodHandle planned = handle (type, "plannedHandle", MethodType.methodType (long.class, Object.class, int.class), int.class);
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean ();
		for (String pattern : new String [] {"scroll", "page", "resize"}) {
			double [] ns = new double [runs], bytes = new double [runs];
			for (int run = 0; run < runs; run++) {
				Object subject = constructor.newInstance ();
				Mover mover = new Mover (window, pattern);
				for (int i = 0; i < commits / 4; i++) mover.step (subject, plan, commit, planned);
				long allocated = threads.getThreadAllocatedBytes (Thread.currentThread ().getId ());
				long start = System.nanoTime ();
				for (int i = 0; i < commits; i++) mover.step (subject, plan, commit, planned);
				long elapsed = System.nanoTime () - start;
				allocated = threads.getThreadAllocatedBytes (Thread.currentThread ().getId ()) - allocated;
				ns [run] = (double) elapsed / commits;
				bytes [run] = (double) allocated / commits;
			}
			Arrays.sort (ns);
			Arrays.sort (bytes);
			final double n = ns [runs / 2], b = bytes [runs / 2];
			LOG.info (() -> String.format (Locale.ROOT, "resident plan %s, window %d: %.1f ns/commit, %.1f bytes/commit (median of %d runs x %d commits)", pattern, window, n, b, runs, commits));
		}
	}

	static MethodHandle handle (Class<?> type, String name, MethodType erased, Class<?>... parameters) throws ReflectiveOperationException {
		Method method = type.getDeclaredMethod (name, parameters);
		method.setAccessible (true);
		return MethodHandles.lookup ().unreflect (method).asType (erased);
	}

	/* a window of roots (parent -1, physical parent -1) that moves or resizes; native handles are numbered */
	static final class Mover {
		final int base;
		final String pattern;
		final int [] parents, indices, physical;
		final long [] handles;
		int first, count, step;
		long nextHandle = 1;

		Mover (int window, String pattern) {
			this.base = window;
			this.pattern = pattern;
			parents = new int [window + 1];
			indices = new int [window + 1];
			physical = new int [window + 1];
			handles = new long [window + 1];
			Arrays.fill (parents, -1);
			Arrays.fill (physical, -1);
			count = window;
		}

		void step (Object subject, MethodHandle plan, MethodHandle commit, MethodHandle planned) throws Throwable {
			switch (pattern) {
				case "scroll" -> first = (first + 1) % 1_000_000;
				case "page" -> first = (first + base / 2) % 1_000_000;
				default -> count = (step++ & 1) == 0 ? base + 1 : base;
			}
			for (int e = 0; e < count; e++) indices [e] = first + e;
			plan.invokeExact (subject, count, parents, indices, physical);
			for (int e = 0; e < count; e++) {
				long reused = (long) planned.invokeExact (subject, e);
				handles [e] = reused != 0 ? reused : nextHandle++;
			}
			commit.invokeExact (subject, count, parents, indices, physical, handles);
		}
	}
}
