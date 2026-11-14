/* SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.swt.internal.mobile.ios;

import java.util.Objects;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime.*;

/**
 * UIKit host for SWT's mobile ownership/event SPI. The embedding iOS Java/AOT
 * runtime supplies a valid UIView pointer on the UIKit main thread. This class
 * does not initialize or package a JVM; app/toolchain integration is separate.
 */
public final class IosMobileHost implements Host {
	static { System.loadLibrary("swt-mobile-uikit"); }
	private final Thread uiThread;
	private long context;
	private EventSink sink;
	private volatile boolean closed;

	public IosMobileHost(long rootUIViewPointer) {
		if (rootUIViewPointer == 0 || !nIsMainThread())
			throw new IllegalArgumentException("UIKit root view/main thread required");
		uiThread = Thread.currentThread();
		context = nAttach(rootUIViewPointer);
		if (context == 0) throw new IllegalStateException("cannot attach UIKit SWT host");
	}
	@Override public boolean isUiThread() {
		return Thread.currentThread() == uiThread && nIsMainThread();
	}
	private void check() {
		if (!isUiThread()) throw new IllegalStateException("UIKit main thread required");
		if (closed) throw new IllegalStateException("UIKit host closed");
	}
	@Override public void post(Runnable runnable) {
		if (!closed) nPost(Objects.requireNonNull(runnable));
	}
	@Override public void attach(EventSink sink) { check(); this.sink = Objects.requireNonNull(sink); }
	@Override public void create(long id, long parent, Kind kind) {
		check(); nCreate(context, id, parent, Objects.requireNonNull(kind).ordinal());
	}
	@Override public void setBounds(long id, Bounds bounds) {
		check(); nSetBounds(context, id, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}
	@Override public void setText(long id, String text) {
		check(); nSetText(context, id, Objects.requireNonNull(text));
	}
	@Override public void setEnabled(long id, boolean enabled) { check(); nSetEnabled(context, id, enabled); }
	@Override public void setVisible(long id, boolean visible) { check(); nSetVisible(context, id, visible); }
	@Override public void setAccessibilityLabel(long id, String label) {
		check(); nSetAccessibilityLabel(context, id, Objects.requireNonNull(label));
	}
	@Override public void requestKeyboard(long id, boolean shown) { check(); nRequestKeyboard(context, id, shown); }
	@Override public void remove(long id) { check(); nRemove(context, id); }
	@Override public void close() {
		check();
		nClose(context);
		context = 0;
		sink = null;
		closed = true;
	}
	/** Native callback; event codes are the stable EventType ordinal values. */
	@SuppressWarnings("unused")
	private void onNativeEvent(long id, int kind, String text, float x, float y) {
		if (closed || sink == null) return;
		if (!isUiThread()) throw new IllegalStateException("off-main UIKit callback");
		EventType[] types = EventType.values();
		if (kind < 0 || kind >= types.length) throw new IllegalArgumentException("native event code");
		sink.accept(new Event(id, types[kind], text, x, y));
	}
	private static native boolean nIsMainThread();
	private native long nAttach(long rootUIViewPointer);
	private native void nPost(Runnable runnable);
	private native void nCreate(long context, long id, long parent, int kind);
	private native void nSetBounds(long context, long id, int x, int y, int width, int height);
	private native void nSetText(long context, long id, String text);
	private native void nSetEnabled(long context, long id, boolean enabled);
	private native void nSetVisible(long context, long id, boolean visible);
	private native void nSetAccessibilityLabel(long context, long id, String label);
	private native void nRequestKeyboard(long context, long id, boolean shown);
	private native void nRemove(long context, long id);
	private native void nClose(long context);
}
