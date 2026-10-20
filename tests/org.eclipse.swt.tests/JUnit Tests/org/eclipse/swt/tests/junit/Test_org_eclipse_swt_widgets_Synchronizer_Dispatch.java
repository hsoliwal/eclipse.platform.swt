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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 28 (docs/m3/SYNCHRONIZER.md). The differential half runs against any jar, the stock one included: random
 * interleavings of asyncExec and syncExec from 8 threads, each call taking a ticket under one lock right before it
 * posts, run in ticket order (the stock first-in-first-out order), with runnables that post again from the UI thread,
 * null runnables, failures and syncExec from the UI thread itself. The second half needs the rebuilt dispatcher (it
 * skips on the stock jar): keyed coalescing in place, the priority classes, model patches once per frame tick before
 * the frame listeners, and the frame generation.
 */
public class Test_org_eclipse_swt_widgets_Synchronizer_Dispatch {
	Display display;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
	}

	/* runs the event loop until the condition holds or the deadline passes */
	boolean pump (java.util.function.BooleanSupplier done, long millis) {
		long end = System.currentTimeMillis () + millis;
		while (!done.getAsBoolean () && System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		return done.getAsBoolean ();
	}

	@Test
	public void randomInterleavingsFromEightThreadsRunInPostingOrder () throws InterruptedException {
		for (int seed = 1; seed <= 5; seed++) {
			int threads = 8, perThread = 2000;
			Object order = new Object ();
			long [] ticket = {0};
			java.util.List<Long> ran = Collections.synchronizedList (new ArrayList<> ());
			AtomicInteger finished = new AtomicInteger ();
			java.util.List<Throwable> failures = Collections.synchronizedList (new ArrayList<> ());
			for (int t = 0; t < threads; t++) {
				Random random = new Random (seed * 100 + t);
				Thread thread = new Thread (() -> {
					try {
						for (int i = 0; i < perThread; i++) {
							boolean sync = random.nextInt (10) == 0;
							synchronized (order) {
								long mine = ticket [0]++;
								Runnable record = () -> ran.add (mine);
								if (sync) display.syncExec (record);
								else display.asyncExec (record);
							}
							if (random.nextInt (50) == 0) Thread.yield ();
						}
					} catch (Throwable e) {
						failures.add (e);
					} finally {
						finished.incrementAndGet ();
					}
				}, "producer " + t);
				thread.start ();
			}
			assertTrue (pump (() -> finished.get () == threads && ran.size () == threads * perThread, 60_000), "seed " + seed + " ran " + ran.size ());
			assertEquals (java.util.List.of (), failures);
			for (int i = 0; i < ran.size (); i++) assertEquals ((long) i, (long) ran.get (i), "seed " + seed + " position " + i);
		}
	}

	@Test
	public void postingFromTheUserInterfaceThreadNullRunnablesAndFailures () {
		java.util.List<String> ran = new ArrayList<> ();
		display.asyncExec (() -> {
			ran.add ("a");
			display.asyncExec (() -> ran.add ("c"));
		});
		display.asyncExec (null);
		display.asyncExec (() -> ran.add ("b"));
		display.syncExec (() -> ran.add ("sync on the UI thread runs at once"));
		assertTrue (pump (() -> ran.size () == 4, 10_000));
		assertEquals (java.util.List.of ("sync on the UI thread runs at once", "a", "b", "c"), ran);
		display.asyncExec (() -> {
			throw new IllegalStateException ("expected");
		});
		SWTException e = assertThrows (SWTException.class, () -> pump (() -> false, 2000));
		assertEquals (SWT.ERROR_FAILED_EXEC, e.code);
		Thread caller = new Thread (() -> {
			try {
				display.syncExec (() -> {
					throw new IllegalStateException ("sync failure");
				});
				ran.add ("no exception");
			} catch (SWTException expected) {
				ran.add ("caller saw " + expected.code);
			}
		});
		caller.start ();
		assertThrows (SWTException.class, () -> pump (() -> false, 2000));
		assertTrue (pump (() -> !caller.isAlive (), 5000));
		assertEquals ("caller saw " + SWT.ERROR_FAILED_EXEC, ran.get (ran.size () - 1));
	}

	/* the rebuilt dispatcher's API, or a skip on the stock jar */
	static Method method (String name, Class<?>... types) {
		try {
			return Synchronizer.class.getMethod (name, types);
		} catch (NoSuchMethodException e) {
			Assumptions.abort ("the stock Synchronizer has no " + name);
			return null;
		}
	}

	static int constant (String name) throws ReflectiveOperationException {
		return Synchronizer.class.getField (name).getInt (null);
	}

	@Test
	public void keyedCoalescingAndPriorityClasses () throws Exception {
		Method keyed = method ("asyncExec", int.class, Object.class, Runnable.class);
		int input = constant ("INPUT"), layout = constant ("LAYOUT"), plain = constant ("DEFAULT"), idle = constant ("IDLE");
		Synchronizer synchronizer = display.getSynchronizer ();
		java.util.List<String> ran = new ArrayList<> ();
		CountDownLatch posted = new CountDownLatch (1);
		Thread producer = new Thread (() -> {
			try {
				keyed.invoke (synchronizer, idle, null, (Runnable) () -> ran.add ("idle"));
				display.asyncExec (() -> ran.add ("plain 1"));
				keyed.invoke (synchronizer, plain, "k", (Runnable) () -> ran.add ("keyed old"));
				display.asyncExec (() -> ran.add ("plain 2"));
				keyed.invoke (synchronizer, layout, null, (Runnable) () -> ran.add ("layout"));
				keyed.invoke (synchronizer, input, null, (Runnable) () -> ran.add ("input"));
				keyed.invoke (synchronizer, plain, "k", (Runnable) () -> ran.add ("keyed new"));
			} catch (ReflectiveOperationException e) {
				ran.add ("failed " + e);
			} finally {
				posted.countDown ();
			}
		});
		/* hold the UI thread so that every post is queued before anything runs */
		producer.start ();
		posted.await ();
		assertTrue (pump (() -> ran.size () == 6, 10_000), ran::toString);
		assertEquals (java.util.List.of ("input", "layout", "plain 1", "keyed new", "plain 2", "idle"), ran);
		Method keyedDefault = method ("asyncExec", Object.class, Runnable.class);
		ran.clear ();
		keyedDefault.invoke (synchronizer, "x", (Runnable) () -> ran.add ("x1"));
		keyedDefault.invoke (synchronizer, "x", (Runnable) () -> ran.add ("x2"));
		display.asyncExec (() -> ran.add ("after"));
		assertTrue (pump (() -> ran.size () == 2, 10_000));
		keyedDefault.invoke (synchronizer, "x", (Runnable) () -> ran.add ("x3"));
		assertTrue (pump (() -> ran.size () == 3, 10_000));
		assertEquals (java.util.List.of ("x2", "after", "x3"), ran, "a key run is free again");
		assertThrows (InvocationTargetException.class, () -> keyed.invoke (synchronizer, 9, null, (Runnable) () -> {}));
		assertThrows (InvocationTargetException.class, () -> keyed.invoke (synchronizer, plain, null, null));
	}

	@Test
	public void modelPatchesRunOncePerFrameBeforeTheFrameListeners () throws Exception {
		Method keyed = method ("asyncExec", int.class, Object.class, Runnable.class);
		Method add = method ("addFrameListener", FrameListener.class), remove = method ("removeFrameListener", FrameListener.class);
		Method request = method ("requestFrame"), generation = method ("getFrameGeneration");
		int model = constant ("MODEL");
		Synchronizer synchronizer = display.getSynchronizer ();
		java.util.List<String> ran = new ArrayList<> ();
		FrameListener listener = (d, frame) -> ran.add ("frame " + (frame - (long) 0));
		add.invoke (synchronizer, listener);
		long before = (Long) generation.invoke (synchronizer);
		for (int i = 0; i < 100; i++) {
			int row = i % 3;
			keyed.invoke (synchronizer, model, "widget " + row, (Runnable) () -> ran.add ("patch " + row));
		}
		display.asyncExec (() -> ran.add ("plain"));
		assertTrue (pump (() -> ran.size () == 5, 10_000), ran::toString);
		assertEquals ("plain", ran.get (0), "the default class before the frame");
		assertEquals (java.util.List.of ("patch 0", "patch 1", "patch 2"), ran.subList (1, 4), "one patch per key");
		assertEquals ("frame " + (before + 1), ran.get (4));
		ran.clear ();
		request.invoke (synchronizer);
		assertTrue (pump (() -> ran.size () == 1, 10_000));
		assertEquals ("frame " + (before + 2), ran.get (0), "a requested frame without patches");
		ran.clear ();
		pump (() -> false, 200);
		assertEquals (java.util.List.of (), ran, "no tick when nothing is dirty");
		remove.invoke (synchronizer, listener);
		keyed.invoke (synchronizer, model, null, (Runnable) () -> ran.add ("patch"));
		assertTrue (pump (() -> ran.size () == 1, 10_000));
		assertEquals ((Long) (before + 3), generation.invoke (synchronizer));
	}
}
