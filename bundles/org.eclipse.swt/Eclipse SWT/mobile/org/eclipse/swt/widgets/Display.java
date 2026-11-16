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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.ExceptionStash;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/**
 * SWT event-loop owner for Android/iOS. The application thread creates this
 * Display. OS main-thread callbacks only append work; they never invoke a
 * listener or synchronously wait for the application thread.
 */
public class Display extends Device implements Executor {

	/**
	 * Returns the SWT UI API capabilities currently available from this backend.
	 * This does not probe device hardware or guarantee support by every widget.
	 *
	 * @return an immutable platform capability descriptor
	 * @exception SWTException
	 *                ERROR_DEVICE_DISPOSED - if the display is disposed
	 *                ERROR_THREAD_INVALID_ACCESS - if called from another thread
	 * @since 3.138
	 */
	public PlatformCapabilities getPlatformCapabilities () {
		checkDevice ();
		return PlatformCapabilities.of (platform.name ());
	}

	private static final ThreadLocal<Display> CURRENT = new ThreadLocal<>();
	private static Display defaultDisplay;
	private static String appName = "SWT", appVersion = "";
	private final Thread thread = Thread.currentThread();
	private final Object queueLock = new Object();
	private Runnable[] queue = new Runnable[32];
	private int queueHead, queueSize;
	private boolean wakeRequested;
	private volatile boolean closing;
	private Runnable[] timers = new Runnable[8];
	private long[] timerDeadlines = new long[8];
	private int timerCount;
	private boolean dispatchTimerNext = true;
	private Control[] controls = new Control[16];
	private int controlCount;
	// Parent-owned index of full generation-tagged native peer IDs.
	// The existing controls[] retains enumeration/disposal order.
	private long[] peerKeys = new long[32];
	private Control[] peerOwners = new Control[32];
	private int peerCount;
	private Runnable[] disposal = new Runnable[0];
	private EventTable filters, listeners;
	private Consumer<RuntimeException> runtimeHandler = exception -> { throw exception; };
	private Consumer<Error> errorHandler = error -> { throw error; };
	private int lastEventTime;
	private volatile long focusGeneration;
	private long committedFocusGeneration;
	Control focusControl;
	boolean active = true;

	public Display() { this(null); }
	public Display(DeviceData data) {
		super(data);
		synchronized (Display.class) {
			if (CURRENT.get() != null || defaultDisplay != null && !defaultDisplay.isDisposed()) SWT.error(SWT.ERROR_NOT_IMPLEMENTED);
			CURRENT.set(this); defaultDisplay = this;
		}
		try { platform.connect(new NativeEvents()); }
		catch (RuntimeException | Error failure) {
			CURRENT.remove(); synchronized (Display.class) { if (defaultDisplay == this) defaultDisplay = null; }
			super.dispose(); throw failure;
		}
	}
	public static Display getCurrent() { Display d = CURRENT.get(); return d != null && !d.isDisposed() ? d : null; }
	public static synchronized Display getDefault() {
		if (defaultDisplay == null || defaultDisplay.isDisposed()) return new Display();
		return defaultDisplay;
	}
	public static synchronized Display findDisplay(Thread thread) {
		return defaultDisplay != null && !defaultDisplay.isDisposed() && defaultDisplay.thread == thread ? defaultDisplay : null;
	}
	public final void checkThread() {
		if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		if (Thread.currentThread() != thread) SWT.error(SWT.ERROR_THREAD_INVALID_ACCESS);
	}
	void checkCreation() {
		checkThread();
		// Dispose callbacks may inspect live owners, but cannot extend the
		// ownership tree after its cleanup traversal has started.
		if (closing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
	}
	boolean isDisposing() { return closing; }
	@Override protected void checkDevice() { checkThread(); }
	public Thread getThread() { if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED); return thread; }
	int getLastEventTime() { return lastEventTime == 0 ? (int) (System.nanoTime() / 1_000_000) : lastEventTime; }

	private boolean enqueue(Runnable task) {
		synchronized (queueLock) {
			if (disposed || closing) return false;
			if (queueSize == queue.length) {
				Runnable[] next = new Runnable[Math.addExact(queue.length, queue.length)];
				for (int i = 0; i < queueSize; i++) next[i] = queue[(queueHead + i) % queue.length];
				queue = next; queueHead = 0;
			}
			queue[(queueHead + queueSize++) % queue.length] = task;
			queueLock.notifyAll(); return true;
		}
	}
	public void asyncExec(Runnable runnable) {
		if (runnable == null) { wake(); return; }
		if (!enqueue(runnable)) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
	}
	@Override public void execute(Runnable runnable) {
		if (runnable == null) throw new NullPointerException("runnable");
		if (Thread.currentThread() == thread) { checkThread(); runnable.run(); } else asyncExec(runnable);
	}
	public void syncExec(Runnable runnable) {
		if (disposed || closing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		if (runnable == null) { wake(); return; }
		if (Thread.currentThread() == thread) { runnable.run(); return; }
		SyncTask task = new SyncTask(runnable);
		if (!enqueue(task)) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		boolean interrupted = false;
		for (;;) {
			try { task.done.await(); break; } catch (InterruptedException e) { interrupted = true; }
		}
		if (interrupted) Thread.currentThread().interrupt();
		if (task.aborted) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		if (task.failure != null) SWT.error(SWT.ERROR_FAILED_EXEC, task.failure);
	}
	private static final class SyncTask implements Runnable {
		final Runnable runnable;
		final CountDownLatch done = new CountDownLatch(1);
		volatile Throwable failure;
		volatile boolean aborted;
		SyncTask(Runnable runnable) { this.runnable = runnable; }
		@Override public void run() {
			try { runnable.run(); } catch (Throwable error) { failure = error; } finally { done.countDown(); }
		}
		void abort() { aborted = true; done.countDown(); }
	}
	public boolean readAndDispatch() {
		checkThread(); platform.checkFailure();
		Runnable task = null;
		synchronized (queueLock) {
			long now = System.nanoTime(); int first = -1;
			for (int i = 0; i < timerCount; i++) if (timerDeadlines[i] - now <= 0 && (first < 0 || timerDeadlines[i] < timerDeadlines[first])) first = i;
			if (first >= 0 && (queueSize == 0 || dispatchTimerNext)) {
				task = timers[first]; removeTimer(first); if (queueSize != 0) dispatchTimerNext = false;
			} else if (queueSize != 0) {
				task = queue[queueHead]; queue[queueHead] = null;
				queueHead = (queueHead + 1) % queue.length; queueSize--; dispatchTimerNext = true;
			}
		}
		if (task == null) return false;
		try { task.run(); } catch (RuntimeException e) { runtimeHandler.accept(e); } catch (Error e) { errorHandler.accept(e); }
		return true;
	}
	public boolean sleep() {
		checkThread(); platform.checkFailure();
		synchronized (queueLock) {
			if (queueSize != 0 || wakeRequested) { wakeRequested = false; return false; }
			long delay = Long.MAX_VALUE, now = System.nanoTime();
			for (int i = 0; i < timerCount; i++) delay = Math.min(delay, timerDeadlines[i] - now);
			if (delay <= 0) return false;
			try {
				// OS callbacks retain errors outside Java callers. Periodic polling prevents
				// such a failure being stranded when no input event arrives to wake us.
				delay = Math.min(delay, 250_000_000L);
				queueLock.wait(delay / 1_000_000, (int) (delay % 1_000_000));
			} catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
			wakeRequested = false;
		}
		platform.checkFailure(); return true;
	}
	public void wake() {
		synchronized (queueLock) { if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED); wakeRequested = true; queueLock.notifyAll(); }
	}
	public void timerExec(int milliseconds, Runnable runnable) {
		checkThread(); if (runnable == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		synchronized (queueLock) {
			for (int i = 0; i < timerCount; i++) if (timers[i] == runnable) { removeTimer(i); break; }
			if (milliseconds < 0) return;
			if (timerCount == timers.length) { timers = Arrays.copyOf(timers, timerCount + 8); timerDeadlines = Arrays.copyOf(timerDeadlines, timerCount + 8); }
			timers[timerCount] = runnable; timerDeadlines[timerCount++] = System.nanoTime() + (long) milliseconds * 1_000_000;
			queueLock.notifyAll();
		}
	}
	private void removeTimer(int i) {
		int tail = --timerCount - i;
		System.arraycopy(timers, i + 1, timers, i, tail);
		System.arraycopy(timerDeadlines, i + 1, timerDeadlines, i, tail);
		timers[timerCount] = null;
	}
	public void disposeExec(Runnable runnable) {
		checkThread(); if (runnable == null) return;
		if (closing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		disposal = Arrays.copyOf(disposal, disposal.length + 1); disposal[disposal.length - 1] = runnable;
	}
	@Override public void dispose() {
		if (disposed || closing) return;
		checkThread();
		synchronized (queueLock) { closing = true; }
		try (ExceptionStash failures = new ExceptionStash()) {
			try { sendEvent(SWT.Dispose, new Event()); } catch (RuntimeException | Error e) { failures.stash(e); }
			for (Shell shell : getShells()) try { shell.dispose(); } catch (RuntimeException | Error e) { failures.stash(e); }
			for (Runnable runnable : disposal) try { runnable.run(); } catch (RuntimeException | Error e) { failures.stash(e); }
			synchronized (queueLock) {
				closing = true;
				for (Runnable runnable : queue) if (runnable instanceof SyncTask task) task.abort();
				Arrays.fill(queue, null); queueSize = 0; Arrays.fill(timers, null); timerCount = 0;
				queueLock.notifyAll();
			}
			try { super.dispose(); } catch (RuntimeException | Error e) { failures.stash(e); }
			try { platform.disconnect(); } catch (RuntimeException | Error e) { failures.stash(e); }
			finally { disposed = true; controls = new Control[0]; controlCount = 0; peerKeys = new long[0]; peerOwners = new Control[0]; peerCount = 0; CURRENT.remove(); synchronized (Display.class) { if (defaultDisplay == this) defaultDisplay = null; } }
		}
	}
	public void close() { checkThread(); Event e = new Event(); sendEvent(SWT.Close, e); if (e.doit) dispose(); }

	/**
	 * Requests that a keyboard or mouse event be generated by the native system.
	 * Mobile hosts do not currently provide native input injection, so this method
	 * returns {@code false}. It does not synthesize SWT listener notifications.
	 * This method may be called from any thread.
	 *
	 * @param event the event to be generated
	 * @return {@code false} when native event generation is unsupported
	 * @throws IllegalArgumentException if {@code event} is {@code null}
	 * @throws org.eclipse.swt.SWTException if this Display has been disposed
	 */
	public boolean post(Event event) {
		synchronized (queueLock) {
			if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
			if (event == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
			return false;
		}
	}

	private static int peerHash(long key) {
		int hash = (int)(key ^ (key >>> 32));
		hash ^= hash >>> 16;
		hash *= 0x7feb352d;
		hash ^= hash >>> 15;
		return hash;
	}
	// Open addressing on parallel arrays; no native peer Entry objects.
	private void putPeer(long key, Control value) {
		int mask = peerKeys.length - 1, slot = peerHash(key) & mask;
		while (peerKeys[slot] != 0) {
			if (peerKeys[slot] == key) throw new IllegalStateException("Duplicate SWT native peer");
			slot = (slot + 1) & mask;
		}
		peerKeys[slot] = key;
		peerOwners[slot] = value;
		peerCount++;
	}
	private void indexPeer(Control owner) {
		long key = owner.peer;
		if (key == 0) throw new IllegalStateException("Missing SWT native peer");
		if (((long)peerCount + 1) * 2 >= peerKeys.length) {
			long[] oldKeys = peerKeys;
			Control[] oldOwners = peerOwners;
			long[] nextKeys = new long[Math.multiplyExact(oldKeys.length, 2)];
			Control[] nextOwners = new Control[nextKeys.length];
			peerKeys = nextKeys;
			peerOwners = nextOwners;
			peerCount = 0;
			for (int i = 0; i < oldKeys.length; i++) if (oldKeys[i] != 0) putPeer(oldKeys[i], oldOwners[i]);
		}
		putPeer(key, owner);
	}
	private void compactPeerIndex() {
		// Grow at half occupancy and shrink only below one eighth.
		// Rehash full generation-tagged native tokens without tombstones.
		if (peerKeys.length <= 32 || ((long)peerCount) * 8 > peerKeys.length) return;
		long[] oldKeys = peerKeys;
		Control[] oldOwners = peerOwners;
		int capacity = peerKeys.length >>> 1;
		long[] nextKeys = new long[capacity];
		Control[] nextOwners = new Control[capacity];
		peerKeys = nextKeys;
		peerOwners = nextOwners;
		peerCount = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldKeys[i] != 0) putPeer(oldKeys[i], oldOwners[i]);
		}
	}
	private void unindexPeer(Control owner) {
		long key = owner.peer;
		if (key == 0 || peerKeys.length == 0) return;
		int mask = peerKeys.length - 1, slot = peerHash(key) & mask;
		while (peerKeys[slot] != 0) {
			if (peerKeys[slot] == key && peerOwners[slot] == owner) {
				peerKeys[slot] = 0;
				peerOwners[slot] = null;
				peerCount--;
				int next = (slot + 1) & mask;
				while (peerKeys[next] != 0) {
					long movedKey = peerKeys[next];
					Control movedOwner = peerOwners[next];
					peerKeys[next] = 0;
					peerOwners[next] = null;
					peerCount--;
					putPeer(movedKey, movedOwner);
					next = (next + 1) & mask;
				}
				compactPeerIndex();
				return;
			}
			slot = (slot + 1) & mask;
		}
	}
	void register(Control control) {
		if (controlCount == controls.length) controls = Arrays.copyOf(controls, controlCount + 16);
		indexPeer(control);
		controls[controlCount++] = control;
	}
	void unregister(Control control) {
		unindexPeer(control);
		if (focusControl == control) focusControl = null;
		for (int i = 0; i < controlCount; i++) {
			if (controls[i] != control) continue;
			System.arraycopy(controls, i + 1, controls, i, --controlCount - i);
			controls[controlCount] = null;
			// A viewport may temporarily own many native Controls.
			// Reclaim historical enumeration capacity when they are released.
			if (controls.length > 16 && ((long)controlCount) * 4 <= controls.length)
				controls = Arrays.copyOf(controls, Math.max(16, controls.length >>> 1));
			break;
		}
	}
	void clearFocus(Control control) {
		control.clearedFocusGeneration = ++focusGeneration;
		Control focused = focusControl;
		for (Control owner = focused; owner != null; owner = owner.parent) {
			if (owner != control) continue;
			focusControl = null;
			if (!focused.isDisposed()) focused.sendEvent(SWT.FocusOut, new Event());
			return;
		}
	}
	long beginFocusRequest() { return ++focusGeneration; }
	void commitFocusRequest(long generation) {
		committedFocusGeneration = Math.max(committedFocusGeneration, generation);
	}
	Control control(long peer) {
		if (peer == 0 || peerKeys.length == 0) return null;
		int mask = peerKeys.length - 1, slot = peerHash(peer) & mask;
		while (peerKeys[slot] != 0) {
			if (peerKeys[slot] == peer) {
				Control owner = peerOwners[slot];
				return owner != null && !owner.isDisposed() && owner.peer == peer ? owner : null;
			}
			slot = (slot + 1) & mask;
		}
		return null;
	}
	public Widget findWidget(long peer) { checkThread(); return control(peer); }
	public Shell[] getShells() { checkThread(); return Arrays.stream(controls, 0, controlCount).filter(Shell.class::isInstance).map(Shell.class::cast).toArray(Shell[]::new); }
	public Shell getActiveShell() { checkThread(); if (focusControl != null) return focusControl.getShell(); Shell[] shells = getShells(); for (int i = shells.length - 1; i >= 0; i--) if (shells[i].isVisible()) return shells[i]; return null; }
	public Control getFocusControl() { checkThread(); return focusControl; }
	/**
	 * Returns the current native root safe-area insets, as four SWT logical
	 * coordinates in the order left, top, right, bottom. A new array is returned
	 * on every call. It excludes the software keyboard and does not resize any
	 * control or create an item facade.
	 * <p>
	 * Android's existing display client area already excludes the insets applied
	 * to its root view; clients must not subtract these values a second time.
	 * UIKit hosts may extend their content under the unsafe area and can use
	 * this information when arranging their own contents.
	 * </p>
	 *
	 * @return an independent four-element native safe-area snapshot
	 */
	public int[] getSafeAreaInsets() {
		checkThread();
		int[] insets = platform.safeAreaInsets();
		if (insets == null || insets.length != 4)
			throw new IllegalStateException("Native safe area must contain four insets");
		for (int inset : insets) if (inset < 0)
			throw new IllegalStateException("Native safe area contains a negative inset");
		return insets.clone();
	}
	public boolean getTouchEnabled() { checkThread(); return false; }
	public Monitor getPrimaryMonitor() { checkThread(); Monitor m = new Monitor(); m.handle = 1; m.setBounds(getBounds()); m.setClientArea(getClientArea()); return m; }
	public Monitor[] getMonitors() { return new Monitor[] {getPrimaryMonitor()}; }
	public void update() { checkThread(); for (Control c : Arrays.copyOf(controls, controlCount)) if (!c.isDisposed()) c.update(); }
	public Point map(Control from, Control to, Point point) { if (point == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return map(from, to, point.x, point.y); }
	public Point map(Control from, Control to, int x, int y) {
		checkThread();
		if (from != null && (from.isDisposed() || from.display != this) || to != null && (to.isDisposed() || to.display != this)) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		Point p = from == null ? new Point(x, y) : from.toDisplay(x, y);
		return to == null ? p : to.toControl(p);
	}
	public Rectangle map(Control from, Control to, Rectangle r) { if (r == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return map(from, to, r.x, r.y, r.width, r.height); }
	public Rectangle map(Control from, Control to, int x, int y, int width, int height) { Point p = map(from, to, x, y); return new Rectangle(p.x, p.y, width, height); }
	public void addFilter(int type, Listener listener) { checkThread(); if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (filters == null) filters = new EventTable(); filters.hook(type, listener); }
	public void removeFilter(int type, Listener listener) { checkThread(); if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (filters != null) filters.unhook(type, listener); }
	boolean filterEvent(Event event) { if (filters != null) filters.sendEvent(event); return event.type == SWT.None; }
	public void addListener(int type, Listener listener) { checkThread(); if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (listeners == null) listeners = new EventTable(); listeners.hook(type, listener); }
	public void removeListener(int type, Listener listener) { checkThread(); if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (listeners != null) listeners.unhook(type, listener); }
	private void sendEvent(int type, Event event) { event.type = type; event.display = this; if (!filterEvent(event) && listeners != null) listeners.sendEvent(event); }
	public final Consumer<RuntimeException> getRuntimeExceptionHandler() { checkThread(); return runtimeHandler; }
	public final void setRuntimeExceptionHandler(Consumer<RuntimeException> handler) { checkThread(); if (handler == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); runtimeHandler = handler; }
	public final Consumer<Error> getErrorHandler() { checkThread(); return errorHandler; }
	public final void setErrorHandler(Consumer<Error> handler) { checkThread(); if (handler == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); errorHandler = handler; }
	public static String getAppName() { return appName; }
	public static void setAppName(String name) { if (name != null) appName = name; }
	public static String getAppVersion() { return appVersion; }
	public static void setAppVersion(String version) { if (version != null) appVersion = version; }

	private final class NativeEvents implements MobilePlatform.EventSink {
		@Override public void input(long peer, int type, int x, int y, int button, int keyCode, char character, int stateMask, int count, int time) {
			boolean focusEvent = type == SWT.FocusIn || type == SWT.FocusOut;
			long generation = focusEvent ? focusGeneration : 0;
			enqueue(() -> {
				Control c = control(peer); if (c == null || c.disposing || !c.isEnabled() || !c.isVisible()) return;
				if (type == SWT.FocusIn && (c.style & SWT.NO_FOCUS) != 0) return;
				// Successful requests supersede older focus packets; failed attempts do not.
				if (focusEvent && generation < committedFocusGeneration) return;
				if (focusEvent) for (Control owner = c; owner != null; owner = owner.parent) {
					if (generation < owner.clearedFocusGeneration) return;
				}
				lastEventTime = time;
				if (type == SWT.FocusIn) { if (focusControl == c) return; focusControl = c; }
				else if (type == SWT.FocusOut) { if (focusControl != c) return; focusControl = null; }
				Event e = new Event(); e.x = x; e.y = y; e.button = button; e.keyCode = keyCode; e.character = character; e.stateMask = stateMask; e.count = count; e.time = time;
				// SWT reports state immediately before a modifier key transition.
				if (keyCode == SWT.SHIFT || keyCode == SWT.CTRL || keyCode == SWT.ALT || keyCode == SWT.COMMAND) {
					if (type == SWT.KeyDown) e.stateMask &= ~keyCode;
					else if (type == SWT.KeyUp) e.stateMask |= keyCode;
				}
				c.sendEvent(type, e);
			});
		}
		@Override public void textChanged(long peer, String text, int selectionStart, int selectionEnd) {
			enqueue(() -> {
				Control c = control(peer);
				if (c instanceof Text editor && !editor.isDisposed())
					editor.nativeText(text, selectionStart, selectionEnd);
			});
		}
		@Override public void resize(long peer, int width, int height) { enqueue(() -> { Control c = control(peer); if (c != null) c.nativeResize(width, height); }); }
		@Override public void paint(long peer) { enqueue(() -> { Control c = control(peer); if (c != null) c.redraw(); }); }
		@Override public void close(long peer) { enqueue(() -> { Control c = control(peer); if (c instanceof Shell shell) shell.close(); }); }
		@Override public void terminate() { enqueue(Display.this::dispose); }
		@Override public void lifecycle(boolean nextActive) {
			enqueue(() -> {
				active = nextActive;
				for (Shell shell : getShells()) { shell.sendEvent(active ? SWT.Activate : SWT.Deactivate, new Event()); if (active && !shell.isDisposed()) shell.redrawAll(); }
			});
		}
		@Override public void accessibility(long peer, int action) {
			enqueue(() -> {
				Control c = control(peer); if (c == null || !c.isEnabled() || !c.isVisible()) return;
				if (c instanceof Table table && (action == MobilePlatform.ACTION_INCREMENT
						|| action == MobilePlatform.ACTION_DECREMENT)) {
					table.accessibilityScroll(action == MobilePlatform.ACTION_INCREMENT);
					return;
				}
				if (c instanceof List list && (action == MobilePlatform.ACTION_INCREMENT
						|| action == MobilePlatform.ACTION_DECREMENT)) {
					list.accessibilityScroll(action == MobilePlatform.ACTION_INCREMENT);
					return;
				}
				if (c instanceof Scale && (action == MobilePlatform.ACTION_INCREMENT
						|| action == MobilePlatform.ACTION_DECREMENT)) {
					// The original Scale's keyboard owner applies its exact increment,
					// emits SWT.Selection once, and handles listener reentrancy.
					Event key = new Event();
					key.keyCode = action == MobilePlatform.ACTION_INCREMENT
							? SWT.ARROW_UP : SWT.ARROW_DOWN;
					c.sendEvent(SWT.KeyDown, key);
					return;
				}
				if (c instanceof Link link && action == MobilePlatform.ACTION_ACTIVATE) {
					// VoiceOver/TalkBack activates the original Link, not the
					// centre of an arbitrary text span inside its native Canvas.
					link.nativeActivate();
					return;
				}
				if (action != MobilePlatform.ACTION_ACTIVATE)
					throw new UnsupportedOperationException("Unsupported SWT accessibility action " + action);
				Point size = c.getSize(); Event down = new Event(); down.x = size.x / 2; down.y = size.y / 2; down.button = 1; down.count = 1;
				c.sendEvent(SWT.MouseDown, down);
				if (!c.isDisposed()) { Event up = new Event(); up.x = down.x; up.y = down.y; up.button = 1; up.count = 1; c.sendEvent(SWT.MouseUp, up); }
			});
		}
	}
}
