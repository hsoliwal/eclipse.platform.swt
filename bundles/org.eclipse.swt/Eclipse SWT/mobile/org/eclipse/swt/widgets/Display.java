/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.ExceptionStash;
import org.eclipse.swt.internal.mobile.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Mobile SWT event loop. A dedicated SWT thread owns widgets; Android/UIKit own native views.
 * Native callbacks only enqueue, and complete frames cross back without blocking the native UI
 * thread on SWT.
 */
public class Display extends Device implements Executor {
	private static final ConcurrentHashMap<Thread, Display> DISPLAYS = new ConcurrentHashMap<>();
	private static volatile Display defaultDisplay;
	private final Thread thread = Thread.currentThread();
	private final MobilePlatform platform;
	private final Object queueLock = new Object();
	private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
	private final ArrayList<Shell> shells = new ArrayList<>();
	private final ArrayList<Runnable> disposeCallbacks = new ArrayList<>();
	private final Map<Integer, Color> colors = new HashMap<>();
	private final Map<Runnable, Timer> timerByRunnable = new IdentityHashMap<>();
	private final PriorityQueue<Timer> timers =
			new PriorityQueue<>(
					Comparator.comparingLong((Timer t) -> t.deadline)
							.thenComparingLong(t -> t.sequence));
	private long sequence;
	private int dispatchBurst;
	private boolean disposing, frameRequested, woken, suspended, rendering;
	private int width, height;
	private Shell activeShell;
	private Control focus, capture, hover;
	private Font systemFont;
	private EventTable filters, listeners;
	private Consumer<RuntimeException> runtimeHandler =
			e -> {
				throw e;
			};
	private Consumer<Error> errorHandler =
			e -> {
				throw e;
			};

	public Display() {
		if (DISPLAYS.containsKey(thread)) SWT.error(SWT.ERROR_THREAD_INVALID_ACCESS);
		platform = MobilePlatform.current();
		width = Math.max(0, platform.width());
		height = Math.max(0, platform.height());
		DISPLAYS.put(thread, this);
		synchronized (Display.class) {
			if (defaultDisplay == null || defaultDisplay.isDisposed()) defaultDisplay = this;
		}
		try {
			platform.setEventSink(
					new MobileEventSink() {
						public void resize(int w, int h) {
							enqueueNative(() -> resizeSurface(w, h));
						}

						public void pointer(
								int type, int x, int y, int button, int state, int time) {
							enqueueNative(() -> dispatchPointer(type, x, y, button, state, time));
						}

						public void key(int type, int code, char character, int state, int time) {
							enqueueNative(() -> dispatchKey(type, code, character, state, time));
						}

						public void repaint() {
							enqueueNative(
									() -> {
										for (Shell s : getShells()) invalidate(s);
										requestFrame();
									});
						}

						public void suspend(boolean value) {
							enqueueNative(
									() -> {
										suspended = value;
										if (!value) {
											for (Shell s : getShells()) invalidate(s);
											requestFrame();
										} else {
											capture = null;
											hover = null;
										}
									});
						}

						public void close() {
							enqueueNative(Display.this::dispose);
						}
					});
		} catch (RuntimeException | Error ex) {
			DISPLAYS.remove(thread, this);
			synchronized (Display.class) {
				if (defaultDisplay == this) defaultDisplay = null;
			}
			throw ex;
		}
	}

	public static Display getCurrent() {
		return DISPLAYS.get(Thread.currentThread());
	}

	public static Display findDisplay(Thread thread) {
		if (thread == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return DISPLAYS.get(thread);
	}

	public static synchronized Display getDefault() {
		if (defaultDisplay == null || defaultDisplay.isDisposed()) return new Display();
		return defaultDisplay;
	}

	@Override
	protected void checkDevice() {
		super.checkDevice();
		if (Thread.currentThread() != thread) SWT.error(SWT.ERROR_THREAD_INVALID_ACCESS);
	}

	public Thread getThread() {
		if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		return thread;
	}

	int eventTime() {
		return (int) (System.nanoTime() / 1_000_000L);
	}

	boolean isDisposing() {
		return disposing;
	}

	private boolean enqueueNative(Runnable action) {
		synchronized (queueLock) {
			if (disposed || disposing) return false;
			queue.addLast(action);
			queueLock.notifyAll();
			return true;
		}
	}

	public void asyncExec(Runnable runnable) {
		synchronized (queueLock) {
			if (disposed || disposing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
			if (runnable == null) woken = true;
			else queue.addLast(runnable);
			queueLock.notifyAll();
		}
	}

	@Override
	public void execute(Runnable runnable) {
		if (runnable == null) throw new NullPointerException("runnable");
		if (Thread.currentThread() == thread) {
			checkDevice();
			runnable.run();
		} else asyncExec(runnable);
	}

	public void syncExec(Runnable runnable) {
		if (runnable == null) {
			wake();
			return;
		}
		if (Thread.currentThread() == thread) {
			checkDevice();
			runnable.run();
			return;
		}
		SyncCall call = new SyncCall(runnable);
		synchronized (queueLock) {
			if (disposed || disposing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
			queue.addLast(call);
			queueLock.notifyAll();
		}
		try {
			call.result.join();
		} catch (CompletionException e) {
			SWT.error(SWT.ERROR_FAILED_EXEC, e.getCause());
		}
	}

	private static final class SyncCall implements Runnable {
		final Runnable task;
		final CompletableFuture<Void> result = new CompletableFuture<>();

		SyncCall(Runnable task) {
			this.task = task;
		}

		public void run() {
			try {
				task.run();
				result.complete(null);
			} catch (Throwable e) {
				result.completeExceptionally(e);
			}
		}
	}

	private static final class Timer {
		final Runnable action;
		final long deadline, sequence;
		boolean cancelled;

		Timer(Runnable r, long deadline, long sequence) {
			action = r;
			this.deadline = deadline;
			this.sequence = sequence;
		}
	}

	public void timerExec(int milliseconds, Runnable runnable) {
		checkDevice();
		if (runnable == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		synchronized (queueLock) {
			Timer old = timerByRunnable.remove(runnable);
			if (old != null) old.cancelled = true;
			if (milliseconds >= 0) {
				Timer t =
						new Timer(
								runnable,
								System.nanoTime() + milliseconds * 1_000_000L,
								sequence++);
				timerByRunnable.put(runnable, t);
				timers.add(t);
			}
			queueLock.notifyAll();
		}
	}

	private void cleanTimers() {
		while (!timers.isEmpty() && timers.peek().cancelled) timers.remove();
	}

	public boolean readAndDispatch() {
		checkDevice();
		Runnable task;
		if (dispatchBurst >= 32 && frameRequested && !suspended) {
			dispatchBurst = 0;
			render();
			return true;
		}
		synchronized (queueLock) {
			cleanTimers();
			if (!timers.isEmpty() && timers.peek().deadline - System.nanoTime() <= 0) {
				Timer timer = timers.remove();
				timerByRunnable.remove(timer.action);
				task = timer.action;
			} else task = queue.pollFirst();
			woken = false;
		}
		if (task != null) {
			dispatchBurst++;
			task.run();
			return true;
		}
		if (frameRequested && !suspended) {
			dispatchBurst = 0;
			render();
			return true;
		}
		dispatchBurst = 0;
		return false;
	}

	public boolean sleep() {
		checkDevice();
		synchronized (queueLock) {
			for (; ; ) {
				cleanTimers();
				if (disposed || !queue.isEmpty() || woken || frameRequested && !suspended) {
					woken = false;
					return true;
				}
				long delay =
						timers.isEmpty()
								? Long.MAX_VALUE
								: timers.peek().deadline - System.nanoTime();
				if (delay <= 0) return true;
				try {
					if (delay == Long.MAX_VALUE) queueLock.wait();
					else queueLock.wait(delay / 1_000_000L, (int) (delay % 1_000_000L));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
	}

	public void wake() {
		synchronized (queueLock) {
			if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
			woken = true;
			queueLock.notifyAll();
		}
	}

	void requestFrame() {
		synchronized (queueLock) {
			if (disposed || disposing) return;
			frameRequested = true;
			queueLock.notifyAll();
		}
	}

	public void update() {
		checkDevice();
		if (!suspended && !rendering && frameRequested) render();
	}

	private void render() {
		if (rendering || disposed) return;
		rendering = true;
		frameRequested = false;
		try {
			MobileFrame.Builder frame = new MobileFrame.Builder();
			frame.shape(
					MobileFrame.FILL_RECT,
					getSystemColor(SWT.COLOR_WIDGET_BACKGROUND).internalArgb(),
					0,
					0,
					0,
					width,
					height,
					0,
					0);
			for (Shell shell : getShells()) shell.paint(frame);
			if (!disposed) platform.submit(frame.build(width, height));
		} finally {
			rendering = false;
		}
	}

	private static void invalidate(Control control) {
		if (control.isDisposed()) return;
		control.redraw();
		if (control instanceof Composite c) for (Control child : c.getChildren()) invalidate(child);
	}

	private void resizeSurface(int w, int h) {
		width = Math.max(0, w);
		height = Math.max(0, h);
		for (Shell s : getShells()) {
			s.setBounds(0, 0, width, height);
			if (!s.isDisposed()) s.layout(true, true);
		}
		requestFrame();
	}

	void register(Shell shell) {
		checkDevice();
		if (disposing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		shells.add(shell);
	}

	void unregister(Shell shell) {
		shells.remove(shell);
		if (activeShell == shell) activeShell = null;
		requestFrame();
		if (shells.isEmpty()) platform.setVisible(false);
	}

	void activate(Shell shell) {
		checkDevice();
		if (activeShell == shell) return;
		Shell old = activeShell;
		activeShell = shell;
		if (old != null && !old.isDisposed()) old.notifyListeners(SWT.Deactivate, new Event());
		if (!shell.isDisposed()) shell.notifyListeners(SWT.Activate, new Event());
		platform.setVisible(true);
	}

	public Shell[] getShells() {
		checkDevice();
		return shells.stream().filter(s -> !s.isDisposed()).toArray(Shell[]::new);
	}

	public Shell getActiveShell() {
		checkDevice();
		return activeShell;
	}

	public Control getFocusControl() {
		checkDevice();
		return focus != null && !focus.isDisposed() ? focus : null;
	}

	boolean setFocus(Control next) {
		checkDevice();
		if (next == focus) return true;
		if (next != null
				&& (!next.isVisible() || !next.isEnabled() || (next.style & SWT.NO_FOCUS) != 0))
			return false;
		Control old = focus;
		focus = next;
		if (old != null && !old.isDisposed()) old.notifyListeners(SWT.FocusOut, new Event());
		if (next != null && !next.isDisposed() && focus == next)
			next.notifyListeners(SWT.FocusIn, new Event());
		return focus == next;
	}

	void clearFocus(Control control) {
		if (focus != null && focus.descendantOf(control)) setFocus(null);
		if (capture != null && capture.descendantOf(control)) capture = null;
		if (hover != null && hover.descendantOf(control)) hover = null;
	}

	private Control hit(int x, int y) {
		for (int i = shells.size() - 1; i >= 0; i--) {
			Shell s = shells.get(i);
			if (!s.isDisposed()) {
				Control c = s.hit(x - s.x(), y - s.y());
				if (c != null) return c;
			}
		}
		return null;
	}

	private void pointerEvent(
			Control target, int type, int x, int y, int button, int state, int time) {
		if (target == null || target.isDisposed() || !target.isEnabled()) return;
		Point p = target.toControl(x, y);
		Event e = new Event();
		e.x = p.x;
		e.y = p.y;
		e.button = button;
		e.stateMask = state;
		e.time = time;
		e.count = 1;
		target.notifyListeners(type, e);
	}

	private void dispatchPointer(int type, int x, int y, int button, int state, int time) {
		if (type == SWT.MouseExit) {
			Control old = hover;
			hover = null;
			if ((state & (SWT.BUTTON1 | SWT.BUTTON2 | SWT.BUTTON3)) == 0) capture = null;
			pointerEvent(old, SWT.MouseExit, x, y, button, state, time);
			return;
		}
		Control target = hit(x, y);
		if (target != hover) {
			Control old = hover;
			hover = target;
			pointerEvent(old, SWT.MouseExit, x, y, button, state, time);
			pointerEvent(target, SWT.MouseEnter, x, y, button, state, time);
		}
		if (type == SWT.MouseEnter) return;
		if (target != null && target.isDisposed()) target = null;
		if (type == SWT.MouseDown) {
			capture = target;
			if (target != null && !target.isDisposed()) setFocus(target);
		}
		Control recipient =
				(type == SWT.MouseUp || type == SWT.MouseMove) && capture != null
						? capture
						: target;
		try {
			pointerEvent(recipient, type, x, y, button, state, time);
		} finally {
			if (type == SWT.MouseUp) capture = null;
		}
	}

	private void dispatchKey(int type, int keyCode, char character, int state, int time) {
		Control target = getFocusControl();
		if (target == null || !target.isEnabled()) return;
		Event e = new Event();
		e.keyCode = keyCode;
		e.character = character;
		e.stateMask = state;
		e.time = time;
		target.notifyListeners(type, e);
	}

	public boolean post(Event event) {
		if (event == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		int type = event.type,
				x = event.x,
				y = event.y,
				button = event.button,
				state = event.stateMask,
				time = event.time,
				key = event.keyCode;
		char character = event.character;
		if (type == SWT.KeyDown || type == SWT.KeyUp)
			return enqueueNative(() -> dispatchKey(type, key, character, state, time));
		if (type == SWT.MouseDown || type == SWT.MouseUp || type == SWT.MouseMove)
			return enqueueNative(() -> dispatchPointer(type, x, y, button, state, time));
		return false;
	}

	public void addFilter(int type, Listener listener) {
		checkDevice();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (filters == null) filters = new EventTable();
		filters.hook(type, listener);
	}

	public void removeFilter(int type, Listener listener) {
		checkDevice();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (filters != null) filters.unhook(type, listener);
	}

	void filterEvent(Event event) {
		if (filters != null) filters.sendEvent(event);
	}

	public void addListener(int type, Listener listener) {
		checkDevice();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (listeners == null) listeners = new EventTable();
		listeners.hook(type, listener);
	}

	public void removeListener(int type, Listener listener) {
		checkDevice();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (listeners != null) listeners.unhook(type, listener);
	}

	public Consumer<RuntimeException> getRuntimeExceptionHandler() {
		return runtimeHandler;
	}

	public void setRuntimeExceptionHandler(Consumer<RuntimeException> h) {
		checkDevice();
		if (h == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		runtimeHandler = h;
	}

	public Consumer<Error> getErrorHandler() {
		return errorHandler;
	}

	public void setErrorHandler(Consumer<Error> h) {
		checkDevice();
		if (h == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		errorHandler = h;
	}

	public void disposeExec(Runnable runnable) {
		checkDevice();
		if (disposing) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		if (runnable != null) disposeCallbacks.add(runnable);
	}

	@Override
	public Rectangle getBounds() {
		checkDevice();
		return new Rectangle(0, 0, width, height);
	}

	@Override
	public Font getSystemFont() {
		checkDevice();
		if (systemFont == null || systemFont.isDisposed())
			systemFont = new Font(this, "sans-serif", 12, SWT.NORMAL);
		return systemFont;
	}

	@Override
	public Color getSystemColor(int id) {
		checkDevice();
		Color existing = colors.get(id);
		if (existing != null && !existing.isDisposed()) return existing;
		int rgb =
				switch (id) {
					case SWT.COLOR_WHITE, SWT.COLOR_LIST_BACKGROUND -> 0xffffff;
					case SWT.COLOR_RED -> 0xff0000;
					case SWT.COLOR_DARK_RED -> 0x800000;
					case SWT.COLOR_GREEN -> 0x00ff00;
					case SWT.COLOR_DARK_GREEN -> 0x008000;
					case SWT.COLOR_BLUE -> 0x0000ff;
					case SWT.COLOR_LIST_SELECTION -> 0x0067c0;
					case SWT.COLOR_DARK_BLUE -> 0x000080;
					case SWT.COLOR_CYAN -> 0x00ffff;
					case SWT.COLOR_MAGENTA -> 0xff00ff;
					case SWT.COLOR_YELLOW -> 0xffff00;
					case SWT.COLOR_GRAY -> 0xc0c0c0;
					case SWT.COLOR_DARK_GRAY, SWT.COLOR_WIDGET_NORMAL_SHADOW -> 0x808080;
					case SWT.COLOR_WIDGET_BACKGROUND -> 0xf0f0f0;
					case SWT.COLOR_WIDGET_LIGHT_SHADOW,
									SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW,
									SWT.COLOR_LIST_SELECTION_TEXT ->
							0xffffff;
					default -> 0;
				};
		Color value = new Color(this, rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255);
		colors.put(id, value);
		return value;
	}

	@Override
	public long internal_new_GC(GCData data) {
		checkDevice();
		SWT.error(SWT.ERROR_NOT_IMPLEMENTED);
		return 0;
	}

	@Override
	public void internal_dispose_GC(long handle, GCData data) {}

	@Override
	public void dispose() {
		if (disposed) return;
		checkDevice();
		if (disposing) return;
		synchronized (queueLock) {
			disposing = true;
		}
		try (ExceptionStash errors = new ExceptionStash()) {
			if (listeners != null) {
				Event e = new Event();
				e.display = this;
				e.type = SWT.Dispose;
				try {
					listeners.sendEvent(e);
				} catch (RuntimeException | Error ex) {
					errors.stash(ex);
				}
			}
			for (Shell shell : getShells())
				try {
					shell.dispose();
				} catch (RuntimeException | Error ex) {
					errors.stash(ex);
				}
			for (Runnable runnable : new ArrayList<>(disposeCallbacks))
				try {
					runnable.run();
				} catch (RuntimeException | Error ex) {
					errors.stash(ex);
				}
			try {
				if (systemFont != null) systemFont.dispose();
				for (Color c : colors.values()) c.dispose();
			} catch (RuntimeException | Error ex) {
				errors.stash(ex);
			}
			try {
				platform.dispose();
			} catch (RuntimeException | Error ex) {
				errors.stash(ex);
			}
		} finally {
			synchronized (queueLock) {
				disposed = true;
				for (Runnable r : queue)
					if (r instanceof SyncCall c)
						c.result.completeExceptionally(
								new IllegalStateException("Display disposed before syncExec"));
				queue.clear();
				timers.clear();
				timerByRunnable.clear();
				queueLock.notifyAll();
			}
			focus = capture = hover = null;
			activeShell = null;
			shells.clear();
			disposeCallbacks.clear();
			colors.clear();
			filters = listeners = null;
			systemFont = null;
			DISPLAYS.remove(thread, this);
			synchronized (Display.class) {
				if (defaultDisplay == this) defaultDisplay = null;
			}
			MobilePlatform.unbind(platform);
		}
	}
}
