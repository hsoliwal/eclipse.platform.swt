/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.ios.OS;

/**
 * UIKit/CoreGraphics host for SWT's mobile widget owners. Native callbacks only
 * enqueue Display work; UIKit never waits for the SWT application thread.
 * The embedding application supplies the JavaVM and UIView through SWTIOSAttach.
 */
public final class IOSPlatform extends MobilePlatform {
	private long handle;
	private volatile int width, height;
	private volatile boolean disposed, suspended;
	private volatile MobileEventSink sink;
	private final AtomicReference<Throwable> failure = new AtomicReference<>();

	/** Construct on the dedicated SWT application thread after native attachment. */
	public IOSPlatform() {
		handle = OS.create(this);
		if (handle == 0) throw new IllegalStateException("The UIKit host was not created");
	}

	/** Start an ordinary SWT application without occupying UIKit's main thread. */
	public static Thread start(Runnable application) {
		Objects.requireNonNull(application, "application");
		Thread thread = new Thread(() -> {
			IOSPlatform platform = new IOSPlatform();
			MobilePlatform.bind(platform);
			try {
				application.run();
			} finally {
				try { platform.dispose(); } finally { MobilePlatform.unbind(platform); }
			}
		}, "SWT iOS Display");
		thread.start();
		return thread;
	}

	@Override public String name() { return "ios"; }
	@Override public int width() { return width; }
	@Override public int height() { return height; }

	@Override public void setEventSink(MobileEventSink eventSink) {
		checkOpen();
		sink = Objects.requireNonNull(eventSink, "sink");
		eventSink.resize(width, height);
		eventSink.suspend(suspended);
		eventSink.repaint();
	}

	@Override public synchronized void submit(MobileFrame frame) {
		checkOpen();
		Objects.requireNonNull(frame, "frame");
		OS.submit(handle, frame.width(), frame.height(), frame.operations(), frame.colors(),
			frame.lineWidths(), frame.coordinates(), frame.texts(), frame.fonts(),
			frame.fontSizes(), frame.fontStyles(), frame.antialiases());
	}

	@Override public int[] measureText(String text, String fontName, int pixelSize, int style) {
		checkOpen();
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(fontName, "fontName");
		if (pixelSize <= 0) throw new IllegalArgumentException("Font pixel size must be positive");
		return OS.measureText(text, fontName, pixelSize, style);
	}

	@Override public synchronized void setVisible(boolean visible) {
		checkOpen();
		OS.setVisible(handle, visible);
	}

	/**
	 * Explicitly request UIKit's plain text keyboard. Full marked-text/IME ownership
	 * belongs to the SWT text widget and is not inferred from this host primitive.
	 */
	public synchronized void setTextInputEnabled(boolean enabled) {
		checkOpen();
		OS.setTextInputEnabled(handle, enabled);
	}

	@Override public synchronized void dispose() {
		if (disposed) return;
		disposed = true;
		sink = null;
		long oldHandle = handle;
		handle = 0;
		OS.dispose(oldHandle);
	}

	private void checkOpen() {
		Throwable cause = failure.get();
		if (cause != null) throw new IllegalStateException("iOS native callback failed", cause);
		if (disposed) throw new IllegalStateException("iOS platform is disposed");
	}

	private static int modifiers(int nativeMask) {
		int result = 0;
		if ((nativeMask & 1) != 0) result |= SWT.SHIFT;
		if ((nativeMask & 2) != 0) result |= SWT.CTRL;
		if ((nativeMask & 4) != 0) result |= SWT.ALT;
		if ((nativeMask & 8) != 0) result |= SWT.COMMAND;
		return result;
	}

	private void deliver(java.util.function.Consumer<MobileEventSink> callback) {
		MobileEventSink target = sink;
		if (target == null || disposed) return;
		try { callback.accept(target); } catch (Throwable problem) { nativeFailure(problem); }
	}

	/* Called from JNI on UIKit's thread. These methods must never await Display. */
	@SuppressWarnings("unused")
	private void nativeResize(int newWidth, int newHeight) {
		width = Math.max(0, newWidth);
		height = Math.max(0, newHeight);
		deliver(s -> s.resize(width, height));
	}

	@SuppressWarnings("unused")
	private void nativePointer(int action, int x, int y, int buttons, int nativeModifiers, int time) {
		int mask = modifiers(nativeModifiers) | ((buttons & 1) != 0 ? SWT.BUTTON1 : 0);
		int type = switch (action) {
			case 0 -> SWT.MouseDown;
			case 1 -> SWT.MouseMove;
			case 2 -> SWT.MouseUp;
			case 3 -> SWT.MouseExit;
			default -> throw new IllegalArgumentException("Unknown native pointer action");
		};
		deliver(s -> s.pointer(type, x, y, action == 0 || action == 2 ? 1 : 0, mask, time));
	}

	@SuppressWarnings("unused")
	private void nativeKey(int action, int nativeKey, char character, int nativeModifiers, int time) {
		int keyCode = switch (nativeKey) {
			case -1 -> SWT.ARROW_LEFT;
			case -2 -> SWT.ARROW_RIGHT;
			case -3 -> SWT.ARROW_UP;
			case -4 -> SWT.ARROW_DOWN;
			case -5 -> SWT.HOME;
			case -6 -> SWT.END;
			case -7 -> SWT.PAGE_UP;
			case -8 -> SWT.PAGE_DOWN;
			case -9 -> SWT.DEL;
			case -10 -> SWT.INSERT;
			case -11 -> SWT.SHIFT;
			case -12 -> SWT.CTRL;
			case -13 -> SWT.ALT;
			case -14 -> SWT.COMMAND;
			default -> nativeKey;
		};
		deliver(s -> s.key(action == 0 ? SWT.KeyDown : SWT.KeyUp, keyCode,
			character, modifiers(nativeModifiers), time));
	}

	@SuppressWarnings("unused")
	private void nativeRepaint() { deliver(MobileEventSink::repaint); }

	@SuppressWarnings("unused")
	private void nativeSuspend(boolean value) {
		suspended = value;
		deliver(s -> s.suspend(value));
	}

	@SuppressWarnings("unused")
	private void nativeClose() { deliver(MobileEventSink::close); }

	@SuppressWarnings("unused")
	private void nativeFailure(Throwable problem) {
		Objects.requireNonNull(problem, "problem");
		if (!failure.compareAndSet(null, problem)) return;
		MobileEventSink target = sink;
		if (target != null && !disposed) {
			try { target.close(); } catch (Throwable closeFailure) { problem.addSuppressed(closeFailure); }
		}
	}
}
