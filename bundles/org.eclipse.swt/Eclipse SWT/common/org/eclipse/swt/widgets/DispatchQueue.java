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
package org.eclipse.swt.widgets;

import java.util.logging.*;

import org.eclipse.swt.*;

/**
 * The queue retained by a display across {@link Synchronizer} replacements, a dispatcher in the manner of Swing's
 * <code>EventQueue</code>: one ring of slots per priority class (input, layout and paint, default, model patches,
 * idle), keyed coalescing (the latest runnable for a key replaces the queued one in place, like
 * <code>EventQueue.coalesceEvents</code>), model patches applied together once per frame tick followed by the
 * {@link FrameListener}s, and one reused lock per calling thread for <code>syncExec</code>. Plain
 * <code>asyncExec</code> and <code>syncExec</code> go into the default class, first in, first out, and run exactly as the
 * stock queue ran them; posting allocates nothing (the rings grow by doubling).
 */
final class DispatchQueue {
	static final int INPUT = 0, LAYOUT = 1, DEFAULT = 2, MODEL = 3, IDLE = 4, CLASSES = 5;
	/* the frame pacing: at most one tick per this many milliseconds */
	static final int FRAME_MILLIS = Integer.getInteger ("swt.synchronizer.frameMillis", 16).intValue (); //$NON-NLS-1$
	/* a syncExec waiting longer than this is logged once, with the caller's and the UI thread's stacks */
	static final long WARN_MILLIS = Long.getLong ("swt.synchronizer.syncWarnMillis", 5000L).longValue (); //$NON-NLS-1$
	private static final Logger LOG = Logger.getLogger ("org.eclipse.swt.widgets.Synchronizer"); //$NON-NLS-1$
	private static final FrameListener [] NO_LISTENERS = {};

	/** The reused lock of a thread's syncExec (a thread waits for one syncExec at a time). */
	static final class SyncLock {
		final Thread thread;
		boolean done;
		Throwable throwable;

		SyncLock (Thread thread) {
			this.thread = thread;
		}
	}

	private static final ThreadLocal<SyncLock> SYNC_LOCKS = ThreadLocal.withInitial (() -> new SyncLock (Thread.currentThread ()));

	/* a growable ring of slots; first is the sequence number of the head slot */
	static final class Ring {
		Runnable [] runnables = new Runnable [16];
		SyncLock [] locks = new SyncLock [16];
		Object [] keys = new Object [16];
		int head, size;
		long first;

		long append (Runnable runnable, SyncLock lock, Object key) {
			if (size == runnables.length) grow ();
			int at = (head + size) % runnables.length;
			runnables [at] = runnable;
			locks [at] = lock;
			keys [at] = key;
			size++;
			return first + size - 1;
		}

		/* the slot of a sequence number still queued, or -1 */
		int slot (long sequence) {
			long offset = sequence - first;
			return offset < 0 || offset >= size ? -1 : (int) ((head + offset) % runnables.length);
		}

		private void grow () {
			int length = runnables.length;
			Runnable [] r = new Runnable [length * 2];
			SyncLock [] l = new SyncLock [length * 2];
			Object [] k = new Object [length * 2];
			for (int i = 0; i < size; i++) {
				int from = (head + i) % length;
				r [i] = runnables [from];
				l [i] = locks [from];
				k [i] = keys [from];
			}
			runnables = r;
			locks = l;
			keys = k;
			head = 0;
		}

		void clear () {
			java.util.Arrays.fill (runnables, null);
			java.util.Arrays.fill (locks, null);
			java.util.Arrays.fill (keys, null);
			head = size = 0;
		}
	}

	private volatile Synchronizer owner;
	/* A synchronizer prepared before installation may still have a posting caller in flight. */
	private volatile DispatchQueue replacement;
	private final Ring [] rings = new Ring [CLASSES];
	/* keyed entries: open addressing (linear probing, backward-shift deletion) from key to class and sequence */
	private Object [] keyTable = new Object [16];
	private int [] keyClasses = new int [16];
	private long [] keySequences = new long [16];
	private int keyCount;
	/* frame ticks */
	private boolean tickScheduled, tickWanted, frameDue, frameRequested, frameRunning, frameApplying;
	private long lastFrameNanos, generation;
	private volatile FrameListener [] frameListeners = NO_LISTENERS;
	private final Runnable tick = this::tickFired;

	DispatchQueue (Synchronizer owner) {
		this.owner = owner;
		for (int i = 0; i < CLASSES; i++) rings [i] = new Ring ();
	}

	/* ---- posting (any thread) ---- */

	/** Queues a runnable; a key already queued in the same class has its runnable replaced in place (no new slot). */
	void add (int priority, Object key, Runnable runnable, SyncLock lock) {
		boolean wake = false;
		DispatchQueue next;
		synchronized (this) {
			next = replacement;
			if (next == null) {
				if (key != null) {
					int at = findKey (key, priority);
					if (at >= 0) {
						Ring ring = rings [priority];
						int slot = ring.slot (keySequences [at]);
						if (slot >= 0) {
							ring.runnables [slot] = runnable;
							return;
						}
					}
				}
				wake = !hasWorkLocked ();
				long sequence = rings [priority].append (runnable, lock, key);
				if (key != null) putKey (key, priority, sequence);
				if (priority == MODEL && !tickScheduled) {
					tickWanted = true;
					wake = true;
				}
			}
		}
		if (next != null) {
			next.add (priority, key, runnable, lock);
			return;
		}
		if (wake) {
			Display display = owner.display;
			if (display != null) display.wakeThread ();
		}
	}

	/** The calling thread's reused lock, reset and queued with the runnable (default class). */
	SyncLock addSync (Runnable runnable) {
		SyncLock lock = SYNC_LOCKS.get ();
		lock.done = false;
		lock.throwable = null;
		add (DEFAULT, null, runnable, lock);
		return lock;
	}

	/** Waits for a syncExec to complete, logging a long wait once; rethrows its failure as stock does. */
	void await (SyncLock lock) {
		synchronized (lock) {
			boolean interrupted = false, warned = false;
			long start = System.nanoTime ();
			while (!lock.done) {
				try {
					lock.wait (warned ? 0 : Math.max (1, WARN_MILLIS));
				} catch (InterruptedException e) {
					interrupted = true;
				}
				if (!warned && !lock.done && (System.nanoTime () - start) / 1_000_000L >= WARN_MILLIS) {
					warned = true;
					warnLongWait (lock);
				}
			}
			if (interrupted) Thread.currentThread ().interrupt ();
			Throwable throwable = lock.throwable;
			lock.throwable = null;
			if (throwable != null) SWT.error (SWT.ERROR_FAILED_EXEC, throwable);
		}
	}

	private void warnLongWait (SyncLock lock) {
		if (!LOG.isLoggable (Level.WARNING)) return;
		Display display = owner.display;
		Thread ui = display == null ? null : display.getThread ();
		StringBuilder message = new StringBuilder ("syncExec from ").append (lock.thread.getName ()).append (" has waited ")
			.append (WARN_MILLIS).append (" ms for the user-interface thread");
		if (ui != null) {
			message.append ("; the user-interface thread ").append (ui.getName ()).append (" is at:");
			for (StackTraceElement element : ui.getStackTrace ()) message.append ("\n\tat ").append (element);
		}
		Throwable caller = new Throwable ("the waiting caller");
		caller.setStackTrace (lock.thread.getStackTrace ());
		LOG.log (Level.WARNING, message.toString (), caller);
	}

	/* ---- frames ---- */

	void addFrameListener (FrameListener listener) {
		DispatchQueue next;
		synchronized (this) {
			next = replacement;
			if (next == null) {
				FrameListener [] old = frameListeners, grown = java.util.Arrays.copyOf (old, old.length + 1);
				grown [old.length] = listener;
				frameListeners = grown;
				return;
			}
		}
		next.addFrameListener (listener);
	}

	void removeFrameListener (FrameListener listener) {
		DispatchQueue next;
		synchronized (this) {
			next = replacement;
			if (next == null) {
				FrameListener [] old = frameListeners;
				for (int i = 0; i < old.length; i++) {
					if (old [i] == listener) {
						FrameListener [] shrunk = new FrameListener [old.length - 1];
						System.arraycopy (old, 0, shrunk, 0, i);
						System.arraycopy (old, i + 1, shrunk, i, old.length - i - 1);
						frameListeners = shrunk;
						return;
					}
				}
				return;
			}
		}
		next.removeFrameListener (listener);
	}

	void requestFrame () {
		boolean wake = false;
		DispatchQueue next;
		synchronized (this) {
			next = replacement;
			if (next == null) {
				frameRequested = true;
				wake = !tickScheduled && !tickWanted;
				tickWanted = true;
			}
		}
		if (next != null) {
			next.requestFrame ();
			return;
		}
		Display display = owner.display;
		if (wake && display != null) display.wakeThread ();
	}

	long generation () {
		DispatchQueue next;
		synchronized (this) {
			next = replacement;
			if (next == null) return generation;
		}
		return next.generation ();
	}

	/* the frame timer fired (user-interface thread): the frame is due if anything is dirty */
	private void tickFired () {
		synchronized (this) {
			if (replacement != null) return; // an installed queue's old timer was cancelled
			tickScheduled = false;
			if (rings [MODEL].size > 0 || frameRequested) {
				if (frameRunning) tickWanted = true;
				else frameDue = true;
			}
		}
	}

	/* user-interface thread: schedules the one pending tick, paced to FRAME_MILLIS after the last frame */
	private void scheduleTick () {
		Display display = owner.display;
		if (display == null || display.isDisposed ()) return;
		long sinceLast = (System.nanoTime () - lastFrameNanos) / 1_000_000L;
		int delay = (int) Math.max (0, Math.min (FRAME_MILLIS, FRAME_MILLIS - sinceLast));
		display.timerExec (delay, tick);
	}

	/* ---- the user-interface thread ---- */

	private boolean hasWorkLocked () {
		return rings [INPUT].size + rings [LAYOUT].size + rings [DEFAULT].size + rings [IDLE].size > 0
			|| frameRunning && rings [MODEL].size > 0
			|| !frameRunning && (frameDue && (rings [MODEL].size > 0 || frameRequested) || tickWanted && !tickScheduled);
	}

	boolean isEmpty () {
		DispatchQueue next;
		synchronized (this) {
			next = replacement;
			if (next == null) return !hasWorkLocked ();
		}
		return next.isEmpty ();
	}

	/** Stock runAsyncMessages: one message (or, with all, every message) in priority order; a due frame counts as one. */
	boolean run (boolean all) {
		DispatchQueue next = replacement;
		if (next != null) return next.run (all);
		boolean run = false;
		do {
			Runnable runnable;
			SyncLock lock;
			boolean frame = false, schedule = false;
			int priority = -1;
			synchronized (this) {
				if (tickWanted && !tickScheduled && !frameRunning) {
					tickWanted = false;
					tickScheduled = true;
					schedule = true;
				}
				for (int c = INPUT; c < CLASSES && priority < 0; c++) {
					if (c == MODEL) {
						if (frameRunning && rings [MODEL].size > 0) {
							// A nested event loop may need a model callback to finish. Only publication is deferred.
							priority = MODEL;
							if (!frameApplying) frameRequested = true;
						} else if (frameDue && !frameRunning) {
							frame = true;
							priority = MODEL;
							frameDue = false;
							frameRequested = false;
							frameRunning = true;
							frameApplying = true;
						}
					} else if (rings [c].size > 0) {
						priority = c;
					}
				}
				if (priority < 0 || frame) {
					runnable = null;
					lock = null;
				} else {
					Ring ring = rings [priority];
					int slot = ring.head;
					runnable = ring.runnables [slot];
					lock = ring.locks [slot];
					Object key = ring.keys [slot];
					ring.runnables [slot] = null;
					ring.locks [slot] = null;
					ring.keys [slot] = null;
					ring.head = (slot + 1) % ring.runnables.length;
					ring.size--;
					long sequence = ring.first++;
					if (key != null) removeKey (key, priority, sequence);
				}
			}
			/* timerExec runs outside the queue's lock */
			if (schedule) scheduleTick ();
			if (priority < 0) return run;
			run = true;
			if (frame) {
				runFrame ();
			} else {
				runOne (runnable, lock);
			}
		} while (all);
		return run;
	}

	/* one queued message, exactly as the stock runAsyncMessages ran a RunnableLock */
	private void runOne (Runnable runnable, SyncLock lock) {
		Synchronizer active = owner;
		Display display = active.display;
		active.syncThread = lock == null ? null : lock.thread;
		display.sendPreEvent (SWT.None);
		try {
			if (runnable != null) {
				try {
					runnable.run ();
				} catch (RuntimeException exception) {
					display.getRuntimeExceptionHandler ().accept (exception);
				} catch (Error error) {
					display.getErrorHandler ().accept (error);
				}
			}
		} catch (Throwable t) {
			if (lock != null) lock.throwable = t;
			SWT.error (SWT.ERROR_FAILED_EXEC, t);
		} finally {
			Display current = owner.display;
			if (current != null && !current.isDisposed ()) current.sendPostEvent (SWT.None);
			active.syncThread = null;
			owner.syncThread = null;
			if (lock != null) {
				synchronized (lock) {
					lock.done = true;
					lock.notifyAll ();
				}
			}
		}
	}

	/* the model patches queued when the frame became due, then the frame listeners */
	private void runFrame () {
		long through;
		synchronized (this) {
			through = rings [MODEL].first + rings [MODEL].size;
		}
		boolean applied = false;
		try {
			while (true) {
				Runnable runnable;
				synchronized (this) {
					Ring ring = rings [MODEL];
					if (ring.size == 0 || through - ring.first <= 0) break;
					int slot = ring.head;
					runnable = ring.runnables [slot];
					Object key = ring.keys [slot];
					ring.runnables [slot] = null;
					ring.keys [slot] = null;
					ring.head = (slot + 1) % ring.runnables.length;
					ring.size--;
					long sequence = ring.first++;
					if (key != null) removeKey (key, MODEL, sequence);
				}
				runOne (runnable, null);
				Display display = owner.display;
				if (display == null || display.isDisposed ()) return;
			}
			long frameGeneration;
			FrameListener [] listeners;
			synchronized (this) {
				lastFrameNanos = System.nanoTime ();
				frameGeneration = ++generation;
				listeners = frameListeners;
				applied = true;
				frameApplying = false;
			}
			for (FrameListener listener : listeners) {
				Display display = owner.display;
				if (display == null || display.isDisposed ()) return;
				listener.frameApplied (display, frameGeneration);
			}
		} finally {
			synchronized (this) {
				frameRunning = false;
				frameApplying = false;
				// Requests posted while applying or notifying belong to the next frame.
				if (owner.display != null) {
					if (!applied) frameRequested = true;
					if (rings [MODEL].size > 0 || frameRequested) tickWanted = true;
				}
			}
		}
	}

	/* ---- moving and clearing ---- */

	/**
	 * Keeps the active display queue and appends the new synchronizer's pending messages, class by class. Retaining
	 * this queue also retains an in-flight frame, its listeners, generations and timer. Both synchronizers subsequently
	 * submit to it; a caller that already obtained the new synchronizer's old queue is forwarded here.
	 */
	void install (Synchronizer receiver) {
		DispatchQueue current = current ();
		if (current != this) {
			current.install (receiver);
			return;
		}
		DispatchQueue pending = receiver.queue.current ();
		boolean cancelTick = false;
		synchronized (this) {
			synchronized (pending) {
				if (pending != this) {
					cancelTick = pending.tickScheduled;
					boolean pendingFrame = pending.tickWanted || pending.tickScheduled || pending.frameDue
						|| pending.frameRequested || pending.rings [MODEL].size > 0;
					for (int c = 0; c < CLASSES; c++) {
						drainInto (pending.rings [c], rings [c]);
						pending.rings [c].clear ();
					}
					rebuildKeys ();
					if (pending.frameListeners.length > 0) {
						FrameListener [] combined = java.util.Arrays.copyOf (frameListeners,
							frameListeners.length + pending.frameListeners.length);
						System.arraycopy (pending.frameListeners, 0, combined, frameListeners.length, pending.frameListeners.length);
						frameListeners = combined;
					}
					generation = Math.max (generation, pending.generation);
					frameRequested |= pending.frameRequested;
					if (pendingFrame) tickWanted = true;
					pending.frameListeners = NO_LISTENERS;
					java.util.Arrays.fill (pending.keyTable, null);
					pending.keyCount = 0;
					pending.tickScheduled = pending.tickWanted = pending.frameDue = pending.frameRequested = false;
					pending.replacement = this;
				}
				receiver.syncThread = owner.syncThread;
				owner = receiver;
				receiver.queue = this;
			}
		}
		Display display = receiver.display;
		if (display != null && !display.isDisposed ()) {
			if (cancelTick) display.timerExec (-1, pending.tick);
			if (!isEmpty ()) display.wakeThread ();
		}
	}

	private DispatchQueue current () {
		DispatchQueue current = this, next;
		while ((next = current.replacement) != null) current = next;
		return current;
	}

	private static void drainInto (Ring from, Ring to) {
		for (int i = 0; i < from.size; i++) {
			int slot = (from.head + i) % from.runnables.length;
			to.append (from.runnables [slot], from.locks [slot], from.keys [slot]);
		}
	}

	private void rebuildKeys () {
		keyTable = new Object [16];
		keyClasses = new int [16];
		keySequences = new long [16];
		keyCount = 0;
		for (int c = 0; c < CLASSES; c++) {
			Ring ring = rings [c];
			for (int i = 0; i < ring.size; i++) {
				Object key = ring.keys [(ring.head + i) % ring.runnables.length];
				if (key != null) putKey (key, c, ring.first + i);
			}
		}
	}

	void clear () {
		synchronized (this) {
			for (Ring ring : rings) ring.clear ();
			java.util.Arrays.fill (keyTable, null);
			keyCount = 0;
			frameListeners = NO_LISTENERS;
			tickScheduled = tickWanted = frameDue = frameRequested = frameRunning = frameApplying = false;
		}
	}

	/* ---- the key table ---- */

	private int findKey (Object key, int priority) {
		int mask = keyTable.length - 1;
		for (int i = keyHash (key, priority) & mask; keyTable [i] != null; i = (i + 1) & mask) {
			if (keyClasses [i] == priority && keyTable [i].equals (key)) return i;
		}
		return -1;
	}

	private void putKey (Object key, int priority, long sequence) {
		if ((keyCount + 1) * 2 > keyTable.length) growKeys ();
		int mask = keyTable.length - 1, i = keyHash (key, priority) & mask;
		while (keyTable [i] != null && (keyClasses [i] != priority || !keyTable [i].equals (key))) i = (i + 1) & mask;
		if (keyTable [i] == null) keyCount++;
		keyTable [i] = key;
		keyClasses [i] = priority;
		keySequences [i] = sequence;
	}

	/* removes the key when it still maps to this entry (a later entry with the key replaced the mapping otherwise) */
	private void removeKey (Object key, int priority, long sequence) {
		int at = findKey (key, priority);
		if (at < 0 || keyClasses [at] != priority || keySequences [at] != sequence) return;
		int mask = keyTable.length - 1, hole = at;
		keyTable [hole] = null;
		keyCount--;
		for (int i = (hole + 1) & mask; keyTable [i] != null; i = (i + 1) & mask) {
			int home = keyHash (keyTable [i], keyClasses [i]) & mask;
			if (hole <= i ? home <= hole || home > i : home <= hole && home > i) {
				keyTable [hole] = keyTable [i];
				keyClasses [hole] = keyClasses [i];
				keySequences [hole] = keySequences [i];
				keyTable [i] = null;
				hole = i;
			}
		}
	}

	private void growKeys () {
		Object [] oldKeys = keyTable;
		int [] oldClasses = keyClasses;
		long [] oldSequences = keySequences;
		keyTable = new Object [oldKeys.length * 2];
		keyClasses = new int [oldKeys.length * 2];
		keySequences = new long [oldKeys.length * 2];
		keyCount = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldKeys [i] != null) putKey (oldKeys [i], oldClasses [i], oldSequences [i]);
		}
	}

	private static int mix (int hash) {
		hash *= 0x9E3779B9;
		return hash ^ (hash >>> 16);
	}

	private static int keyHash (Object key, int priority) {
		return mix (31 * key.hashCode () + priority);
	}
}
