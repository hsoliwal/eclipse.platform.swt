/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Project
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FrameListener;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Synchronizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Automated Test Suite for class org.eclipse.swt.widgets.Synchronizer
 *
 * @see org.eclipse.swt.widgets.Synchronizer
 */
public class Test_org_eclipse_swt_widgets_Synchronizer {

	private static final int TIMEOUT_MS = 20_000;

	/** A model patch's request for another frame must survive completion of the current frame. */
	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_requestFrameDuringModelPatchSchedulesAnotherFrame() {
		Display display = Display.getDefault();
		Synchronizer synchronizer = display.getSynchronizer();
		AtomicInteger frames = new AtomicInteger();
		AtomicBoolean timedOut = new AtomicBoolean();
		Runnable timeout = () -> timedOut.set(true);
		FrameListener listener = (current, generation) -> frames.incrementAndGet();
		try {
			synchronizer.addFrameListener(listener);
			synchronizer.asyncExec(Synchronizer.MODEL, new Object(), synchronizer::requestFrame);
			display.timerExec(3_000, timeout);
			while (frames.get() < 2 && !timedOut.get()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
			assertFalse(timedOut.get(), "A frame requested by a running model patch was lost");
			assertEquals(2, frames.get(), "Exactly one follow-up frame should be delivered");
		} finally {
			display.timerExec(-1, timeout);
			synchronizer.removeFrameListener(listener);
			display.dispose();
		}
	}


	/**
	 * Regression test for
	 * https://github.com/eclipse-platform/eclipse.platform.swt/issues/3151
	 *
	 * <p>
	 * Verifies that every task posted via {@code asyncExec()} from a non-UI thread
	 * is eventually executed, even when posted concurrently while the UI thread is
	 * draining the message queue.
	 *
	 * <p>
	 * The old {@code Synchronizer.addLast()} implementation had this race:
	 * <ol>
	 * <li>Producer thread: {@code messages.isEmpty()} → {@code false} (queue has
	 * items), so {@code wake = false}</li>
	 * <li>UI thread: drains all remaining items; calls {@code sleep()} →
	 * {@code OS.WaitMessage()} / blocks</li>
	 * <li>Producer thread: {@code messages.add(lock)}</li>
	 * <li>Producer thread: {@code if (wake)} → {@code false} → {@code wakeThread()}
	 * never called → Display sleeps forever despite a pending message</li>
	 * </ol>
	 *
	 * <p>
	 * Using multiple concurrent producers maximises the probability of hitting the
	 * race window: one producer's task appears in the queue when another producer
	 * evaluates {@code isEmpty()}, then that task gets consumed and the UI sleeps
	 * before the second producer's {@code add()} completes.
	 *
	 * <p>
	 * A {@code timerExec} sentinel bounds the test duration so a hung Display
	 * produces a test failure rather than an infinite hang.
	 */
	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_asyncExec_allTasksComplete_noMissedWakeup() throws InterruptedException {
		final int PRODUCERS = 4;
		final int TASKS_PER_PRODUCER = 1_000;
		final int TOTAL_TASKS = PRODUCERS * TASKS_PER_PRODUCER;
		final int TIMEOUT_MS = 15_000;

		Display display = Display.getDefault();
		try {
			AtomicInteger completedCount = new AtomicInteger();
			AtomicBoolean timedOut = new AtomicBoolean(false);

			// Safety net: if the Display gets stuck due to a missed wakeThread() call,
			// the timer fires on the UI thread and lets the event loop exit cleanly so
			// the assertion below produces a clear failure instead of hanging CI.
			display.timerExec(TIMEOUT_MS * 3/4, () -> timedOut.set(true));

			// Multiple concurrent producers increase the chance that one producer's
			// isEmpty() check sees another's task, which is then drained before the
			// first producer's add() completes – the exact sequence that triggers the bug.
			Thread[] producers = new Thread[PRODUCERS];
			for (int p = 0; p < PRODUCERS; p++) {
				producers[p] = new Thread(() -> {
					for (int i = 0; i < TASKS_PER_PRODUCER; i++) {
						if (timedOut.get()) {
							return;
						}
						display.asyncExec(completedCount::incrementAndGet);
					}
				}, "asyncExec-producer-" + p);
				producers[p].start();
			}

			while (completedCount.get() < TOTAL_TASKS && !timedOut.get()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}

			for (Thread producer : producers) {
				producer.join(1_000);
			}

			assertFalse(timedOut.get(),
					"Display did not process all asyncExec tasks within " + TIMEOUT_MS + " ms " + "(completed "
							+ completedCount.get() + "/" + TOTAL_TASKS + "). "
							+ "Likely a missed wakeThread() call in Synchronizer.addLast() "
							+ "– regression of https://github.com/eclipse-platform/eclipse.platform.swt/issues/3151");
			assertEquals(TOTAL_TASKS, completedCount.get(), "Not all asyncExec tasks were executed.");
		} finally {
			display.dispose();
		}
	}

	@Test
	public void test_keyedAsyncExec_isScopedByPriority() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			List<String> calls = new ArrayList<>();
			sync.asyncExec(Synchronizer.INPUT, "key", () -> calls.add("old input"));
			sync.asyncExec(Synchronizer.DEFAULT, "key", () -> calls.add("default"));
			sync.asyncExec(Synchronizer.INPUT, "key", () -> calls.add("new input"));
			dispatch(sync, true);
			assertEquals(List.of("new input", "default"), calls);
		});
	}

	private record CollisionKey(int value) {
		@Override
		public int hashCode() { return 7; }
	}

	@Test
	public void test_keyedAsyncExec_collisionsGrowthDeletionAndWrap() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			for (int round = 0; round < 4; round++) {
				List<List<Integer>> actual = new ArrayList<>();
				for (int priority = Synchronizer.INPUT; priority <= Synchronizer.IDLE; priority++) {
					List<Integer> lane = new ArrayList<>();
					actual.add(lane);
					for (int key = 0; key < 73; key++) {
						int value = key;
						sync.asyncExec(priority, new CollisionKey(key), () -> lane.add(value));
					}
					for (int key = 0; key < 73; key += 2) {
						int value = 1000 + key;
						sync.asyncExec(priority, new CollisionKey(key), () -> lane.add(value));
					}
				}
				dispatch(sync, true);
				fireTick(display, sync);
				dispatch(sync, true);
				List<Integer> expected = new ArrayList<>();
				for (int key = 0; key < 73; key++) expected.add((key & 1) == 0 ? 1000 + key : key);
				for (List<Integer> lane : actual) assertEquals(expected, lane);
			}
		});
	}

	@Test
	public void test_dueFrameAndListeners_surviveSynchronizerReplacement() throws Exception {
		withDisplay(display -> {
			Synchronizer original = display.getSynchronizer();
			List<String> calls = new ArrayList<>();
			FrameListener listener = (d, generation) -> calls.add("frame " + generation);
			original.addFrameListener(listener);
			original.asyncExec(Synchronizer.MODEL, null, () -> calls.add("patch"));
			dispatch(original, false);
			fireTick(display, original);
			Synchronizer replacement = new Synchronizer(display);
			display.setSynchronizer(replacement);
			assertTrue(dispatch(replacement, true));
			assertEquals(List.of("patch", "frame 1"), calls);
			replacement.requestFrame();
			dispatch(replacement, false);
			fireTick(display, replacement);
			dispatch(replacement, true);
			assertEquals(List.of("patch", "frame 1", "frame 2"), calls);
			replacement.removeFrameListener(listener);
			replacement.requestFrame();
			dispatch(replacement, false);
			fireTick(display, replacement);
			dispatch(replacement, true);
			assertEquals(3, replacement.getFrameGeneration());
			assertEquals(3, calls.size());
		});
	}

	@Test
	public void test_frameRequestWithoutModel_survivesPendingTimerReplacement() throws Exception {
		withDisplay(display -> {
			Synchronizer original = display.getSynchronizer();
			AtomicInteger frames = new AtomicInteger();
			original.addFrameListener((d, generation) -> frames.incrementAndGet());
			original.requestFrame();
			dispatch(original, false);
			Synchronizer replacement = new Synchronizer(display);
			display.setSynchronizer(replacement);
			fireTick(display, replacement);
			dispatch(replacement, true);
			assertEquals(1, frames.get());
			assertEquals(1, replacement.getFrameGeneration());
		});
	}

	@Test
	public void test_replacementPreservesOrderAndPreviouslyCapturedQueues() throws Exception {
		withDisplay(display -> {
			Synchronizer original = display.getSynchronizer();
			Synchronizer replacement = new Synchronizer(display);
			Object captured = queue(replacement);
			List<String> calls = new ArrayList<>();
			original.asyncExec(null, () -> calls.add("old"));
			replacement.asyncExec(null, () -> calls.add("prepared"));
			display.setSynchronizer(replacement);
			original.asyncExec(null, () -> calls.add("old reference"));
			invoke(captured, "add", Synchronizer.DEFAULT, null, (Runnable) () -> calls.add("captured queue"), null);
			replacement.asyncExec(null, () -> calls.add("new"));
			dispatch(replacement, true);
			assertEquals(List.of("old", "prepared", "old reference", "captured queue", "new"), calls);
			display.setSynchronizer(original);
			original.asyncExec(null, () -> calls.add("restored"));
			dispatch(original, true);
			assertEquals("restored", calls.getLast());
		});
	}

	@Test
	public void test_nestedModelDispatch_defersOnlyFramePublication() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			List<String> calls = new ArrayList<>();
			sync.addFrameListener((d, generation) -> calls.add("frame " + generation));
			sync.asyncExec(Synchronizer.MODEL, null, () -> {
				calls.add("first start");
				assertTrue(dispatch(sync, false), "nested model callbacks must remain dispatchable");
				assertEquals(0, sync.getFrameGeneration(), "outer patch is unfinished");
				calls.add("first end");
			});
			sync.asyncExec(Synchronizer.MODEL, null, () -> calls.add("second"));
			dispatch(sync, false);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of("first start", "second", "first end", "frame 1"), calls);
		});
	}

	@Test
	public void test_nestedModelDispatch_doesNotExtendTheAdmittedFrame() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			List<String> calls = new ArrayList<>();
			sync.addFrameListener((d, generation) -> calls.add("frame " + generation));
			sync.asyncExec(Synchronizer.MODEL, null, () -> {
				calls.add("first start");
				assertTrue(dispatch(sync, false));
				sync.asyncExec(Synchronizer.MODEL, null, () -> calls.add("future"));
				calls.add("first end");
			});
			sync.asyncExec(Synchronizer.MODEL, null, () -> calls.add("second"));
			dispatch(sync, false);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of("first start", "second", "first end", "frame 1"), calls);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of("first start", "second", "first end", "frame 1", "future", "frame 2"), calls);
		});
	}

	@Test
	public void test_nestedModelDuringNotification_requestsFollowupPublication() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			List<String> calls = new ArrayList<>();
			sync.addFrameListener((d, generation) -> {
				calls.add("frame " + generation + " start");
				if (generation == 1) {
					sync.asyncExec(Synchronizer.MODEL, null, () -> calls.add("nested"));
					assertTrue(dispatch(sync, false));
					assertEquals(1, sync.getFrameGeneration());
				}
				calls.add("frame " + generation + " end");
			});
			sync.requestFrame();
			dispatch(sync, false);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of("frame 1 start", "nested", "frame 1 end"), calls);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of("frame 1 start", "nested", "frame 1 end", "frame 2 start", "frame 2 end"), calls);
		});
	}

	@Test
	public void test_replacementInsideModelPatch_retainsActiveFrame() throws Exception {
		withDisplay(display -> {
			Synchronizer original = display.getSynchronizer();
			Synchronizer replacement = new Synchronizer(display);
			List<String> calls = new ArrayList<>();
			original.addFrameListener((d, generation) -> calls.add("frame " + generation));
			original.asyncExec(Synchronizer.MODEL, null, () -> {
				calls.add("first start");
				display.setSynchronizer(replacement);
				assertTrue(dispatch(replacement, false));
				assertEquals(0, replacement.getFrameGeneration());
				calls.add("first end");
			});
			original.asyncExec(Synchronizer.MODEL, null, () -> calls.add("second"));
			dispatch(original, false);
			fireTick(display, original);
			dispatch(original, true);
			assertEquals(List.of("first start", "second", "first end", "frame 1"), calls);
			assertEquals(1, replacement.getFrameGeneration());
		});
	}

	@Test
	public void test_requestDuringPatchOrListener_producesFollowupFrame() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			List<Long> frames = new ArrayList<>();
			sync.addFrameListener((d, generation) -> {
				frames.add(generation);
				if (generation == 1) {
					sync.requestFrame();
					assertFalse(dispatch(sync, false));
				}
			});
			sync.asyncExec(Synchronizer.MODEL, null, sync::requestFrame);
			dispatch(sync, false);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of(1L), frames);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(List.of(1L, 2L), frames);
		});
	}

	@Test
	public void test_modelFailure_doesNotPoisonLaterFrames() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			AtomicInteger completed = new AtomicInteger();
			sync.asyncExec(Synchronizer.MODEL, null, () -> { throw new IllegalStateException("model failure"); });
			sync.asyncExec(Synchronizer.MODEL, null, completed::incrementAndGet);
			dispatch(sync, false);
			fireTick(display, sync);
			assertThrows(RuntimeException.class, () -> dispatch(sync, true));
			assertEquals(0, sync.getFrameGeneration());
			dispatch(sync, false);
			fireTick(display, sync);
			dispatch(sync, true);
			assertEquals(1, completed.get());
			assertEquals(1, sync.getFrameGeneration());
		});
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_syncExecWaiter_completesAcrossReplacement() throws Exception {
		withDisplay(display -> {
			Synchronizer original = display.getSynchronizer();
			AtomicReference<Throwable> failure = new AtomicReference<>();
			AtomicBoolean ran = new AtomicBoolean();
			Thread caller = new Thread(() -> {
				try {
					display.syncExec(() -> {
						assertSame(Thread.currentThread(), display.getThread());
						ran.set(true);
					});
				} catch (Throwable error) { failure.set(error); }
			}, "synchronizer-replacement-caller");
			caller.setDaemon(true);
			caller.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while ((boolean) invoke(queue(original), "isEmpty") && System.nanoTime() < deadline) Thread.sleep(1);
			assertFalse((boolean) invoke(queue(original), "isEmpty"), "caller submitted its syncExec");
			Synchronizer replacement = new Synchronizer(display);
			display.setSynchronizer(replacement);
			dispatch(replacement, true);
			caller.join(5000);
			assertFalse(caller.isAlive(), "syncExec waiter was notified");
			assertNull(failure.get());
			assertTrue(ran.get());
		});
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_preEventFailure_releasesSyncExecCaller() throws Exception {
		observerFailureReleasesCaller(SWT.PreEvent, null);
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_postEventFailure_releasesSyncExecCaller() throws Exception {
		observerFailureReleasesCaller(SWT.PostEvent, null);
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_callbackAndPostEventFailures_preserveCallerCause() throws Exception {
		observerFailureReleasesCaller(SWT.PostEvent, new IllegalArgumentException("callback failure"));
	}

	private static void observerFailureReleasesCaller(int eventType, RuntimeException callbackFailure) throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			List<String> calls = new ArrayList<>();
			IllegalStateException observerFailure = new IllegalStateException("observer failure");
			Listener before = event -> {
				if (event.detail == SWT.None) {
					calls.add("pre");
					if (eventType == SWT.PreEvent) throw observerFailure;
				}
			};
			Listener after = event -> {
				if (event.detail == SWT.None) {
					calls.add("post");
					if (eventType == SWT.PostEvent) throw observerFailure;
				}
			};
			AtomicReference<Throwable> failure = new AtomicReference<>();
			Thread caller = startSyncCaller(display, failure, () -> {
				calls.add("runnable");
				if (callbackFailure != null) throw callbackFailure;
			});
			awaitSyncWait(caller);
			display.addListener(SWT.PreEvent, before);
			display.addListener(SWT.PostEvent, after);
			try {
				assertSame(observerFailure, assertThrows(IllegalStateException.class, () -> dispatch(sync, false)),
						"the UI observer exception keeps its original identity");
				caller.join(2000);
				assertFalse(caller.isAlive(), "observer failure stranded a syncExec caller");
				SWTException reported = assertInstanceOf(SWTException.class, failure.get());
				assertEquals(SWT.ERROR_FAILED_EXEC, reported.code);
				assertSame(callbackFailure == null ? observerFailure : callbackFailure, reported.getCause());
				assertNull(display.getSyncThread(), "failed dispatch must clear its caller identity");
				assertEquals(eventType == SWT.PreEvent ? List.of("pre") : List.of("pre", "runnable", "post"), calls);
			} finally {
				display.removeListener(SWT.PreEvent, before);
				display.removeListener(SWT.PostEvent, after);
			}
		});
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_disposeDrainsQueuedSyncExecCaller() throws Exception {
		withDisplay(display -> {
			AtomicReference<Throwable> failure = new AtomicReference<>();
			AtomicBoolean ran = new AtomicBoolean();
			Thread caller = startSyncCaller(display, failure, () -> ran.set(true));
			awaitSyncWait(caller);
			display.dispose();
			caller.join(2000);
			assertFalse(caller.isAlive(), "disposal must finish the queued call");
			assertTrue(ran.get(), "Display drains accepted default work before releasing its synchronizer");
			assertNull(failure.get());
		});
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_disposeObserverFailure_completesBothQueuedCallers() throws Exception {
		withDisplay(display -> {
			AtomicReference<Throwable> firstFailure = new AtomicReference<>();
			AtomicReference<Throwable> secondFailure = new AtomicReference<>();
			AtomicBoolean firstRan = new AtomicBoolean(), secondRan = new AtomicBoolean();
			Thread first = startSyncCaller(display, firstFailure, () -> firstRan.set(true));
			awaitSyncWait(first);
			Thread second = startSyncCaller(display, secondFailure, () -> secondRan.set(true));
			awaitSyncWait(second);
			IllegalStateException observerFailure = new IllegalStateException("disposal observer failure");
			AtomicBoolean failOnce = new AtomicBoolean(true);
			display.addListener(SWT.PreEvent, event -> {
				if (event.detail == SWT.None && failOnce.getAndSet(false)) throw observerFailure;
			});
			assertSame(observerFailure, assertThrows(IllegalStateException.class, display::dispose));
			first.join(2000);
			second.join(2000);
			assertFalse(first.isAlive(), "the failed in-flight call must finish");
			assertFalse(second.isAlive(), "exceptional disposal must continue draining later work");
			assertTrue(display.isDisposed());
			assertFalse(firstRan.get());
			assertTrue(secondRan.get());
			SWTException reported = assertInstanceOf(SWTException.class, firstFailure.get());
			assertEquals(SWT.ERROR_FAILED_EXEC, reported.code);
			assertSame(observerFailure, reported.getCause());
			assertNull(secondFailure.get());
		});
	}

	@Test
	@Timeout(value = TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
	public void test_cpuCalculationPublishesOnlyOnSwtThread() throws Exception {
		withDisplay(display -> {
			Synchronizer sync = display.getSynchronizer();
			Thread ui = Thread.currentThread();
			AtomicReference<Thread> producer = new AtomicReference<>(), consumer = new AtomicReference<>();
			AtomicInteger published = new AtomicInteger();
			int[] detachedRows = {3, 5, 7, 9};
			try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
				Future<?> preparation = workers.submit(() -> {
					producer.set(Thread.currentThread());
					int sum = 0;
					for (int row : detachedRows) sum += row;
					int result = sum;
					sync.asyncExec(Synchronizer.MODEL, new Object(), () -> {
						consumer.set(Thread.currentThread());
						published.set(result);
					});
				});
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (consumer.get() == null && System.nanoTime() < deadline) {
					if (!display.readAndDispatch()) Thread.sleep(1);
				}
				preparation.get(5, TimeUnit.SECONDS);
				assertEquals(24, published.get());
				assertNotNull(producer.get());
				assertNotSame(ui, producer.get());
				assertSame(ui, consumer.get());
			}
		});
	}

	private static Thread startSyncCaller(Display display, AtomicReference<Throwable> failure, Runnable runnable) {
		Thread caller = new Thread(() -> {
			try { display.syncExec(runnable); } catch (Throwable error) { failure.set(error); }
		}, "synchronizer-lifecycle-caller");
		caller.setDaemon(true); // a regression must not keep the test VM alive after its bounded assertion fails
		caller.start();
		return caller;
	}

	private static void awaitSyncWait(Thread caller) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (caller.getState() != Thread.State.WAITING && caller.getState() != Thread.State.TIMED_WAITING
				&& caller.isAlive() && System.nanoTime() < deadline) Thread.sleep(1);
		assertTrue(caller.getState() == Thread.State.WAITING || caller.getState() == Thread.State.TIMED_WAITING,
				"worker did not reach the synchronous wait");
	}

	@FunctionalInterface
	private interface DisplayTest { void run(Display display) throws Exception; }

	private static void withDisplay(DisplayTest test) throws Exception {
		Display display = new Display();
		try { test.run(display); } finally { if (!display.isDisposed()) display.dispose(); }
	}

	private static Object queue(Synchronizer sync) {
		try {
			Field field = Synchronizer.class.getDeclaredField("queue");
			field.setAccessible(true);
			return field.get(sync);
		} catch (ReflectiveOperationException error) { throw new AssertionError(error); }
	}

	private static boolean dispatch(Synchronizer sync, boolean all) {
		return (boolean) invoke(queue(sync), "run", all);
	}

	/** Exercise the actual timer callback at a deterministic boundary, without wall-clock sleeps. */
	private static void fireTick(Display display, Synchronizer sync) {
		try {
			Object queue = queue(sync);
			Field field = queue.getClass().getDeclaredField("tick");
			field.setAccessible(true);
			Runnable tick = (Runnable) field.get(queue);
			display.timerExec(-1, tick);
			tick.run();
		} catch (ReflectiveOperationException error) { throw new AssertionError(error); }
	}

	private static Object invoke(Object target, String name, Object... args) {
		try {
			for (Method method : target.getClass().getDeclaredMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == args.length) {
					method.setAccessible(true);
					return method.invoke(target, args);
				}
			}
			throw new AssertionError("Missing queue operation: " + name);
		} catch (InvocationTargetException error) {
			Throwable cause = error.getCause();
			if (cause instanceof RuntimeException exception) throw exception;
			if (cause instanceof Error failure) throw failure;
			throw new AssertionError(cause);
		} catch (ReflectiveOperationException error) { throw new AssertionError(error); }
	}

}
