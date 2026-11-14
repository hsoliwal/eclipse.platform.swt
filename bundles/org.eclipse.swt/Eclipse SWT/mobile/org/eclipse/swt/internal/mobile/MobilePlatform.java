/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

/**
 * OS binding owned by the mobile SWT platform source set. SWT widget state stays
 * in the original Widget/Control/Display owners. Native callbacks enqueue work
 * for the Display thread; they never wait for a listener on that thread.
 *
 * Coordinates are SWT logical pixels. Images use those same coordinates; the
 * platform scales the presented image for the native display. Every handle is
 * opaque and becomes invalid after disposal. Platforms must reject stale handles.
 */
public interface MobilePlatform {
	int SHELL = 1, COMPOSITE = 2, CANVAS = 3, TEXT = 4;
	int ACTION_ACTIVATE = 1, ACTION_INCREMENT = 2, ACTION_DECREMENT = 3;

	/** Receives OS events without running application listeners on an OS thread. */
	interface EventSink {
		void input(long peer, int type, int x, int y, int button, int keyCode,
			char character, int stateMask, int count, int time);
		/** Native IME/editor snapshot; only the SWT Display thread applies it. */
		default void textChanged(long peer, String text, int start, int end) {
			throw new UnsupportedOperationException("Native text owner must implement textChanged");
		}
		void resize(long peer, int width, int height);
		void paint(long peer);
		void close(long peer);
		void lifecycle(boolean active);
		/** Final OS host destruction: dispose without a vetoable SWT.Close request. */
		void terminate();
		void accessibility(long peer, int action);
	}

	String name();
	void connect(EventSink sink);
	void disconnect();
	/** Rethrow a failure retained by a native/OS callback outside a Java caller. */
	default void checkFailure() {}
	int[] displayBounds();
	int[] dpi();
	long createControl(int kind, long parent, int style);
	void disposeControl(long peer);
	void setBounds(long peer, int x, int y, int width, int height);
	void setVisible(long peer, boolean visible);
	void setEnabled(long peer, boolean enabled);
	void setTitle(long peer, String title);
	/** Real system clipboard text, read only on the SWT Display thread. */
	default String getClipboardText() {
		throw new UnsupportedOperationException("OS clipboard is not available");
	}
	/** Publish text to the real native system clipboard. */
	default void setClipboardText(String text) {
		throw new UnsupportedOperationException("OS clipboard is not available");
	}
	/** Publish canonical Text state to the native editor, suppressing echo callbacks. */
	default void textContent(long peer, String text, int start, int end,
			boolean editable, int limit, String hint) {
		throw new UnsupportedOperationException("Native Text widget is not implemented");
	}
	boolean requestFocus(long peer);
	void setAccessibility(long peer, String label, int role, int state);

	/** Present a snapshot. The implementation must not retain mutable image pixels. */
	void present(long peer, long image);
	long createImage(int width, int height, int[] argb);
	void disposeImage(long image);
	int[] imagePixels(long image);
	/** Actual visible window/screen capture; unsupported targets must throw. */
	int[] captureScreen(int x, int y, int width, int height);
	Graphics graphics(long image);

	/** One drawing resource owned by one SWT GC, confined to the Display thread. */
	interface Graphics extends AutoCloseable {
		void setForeground(int argb);
		void setBackground(int argb);
		void setAlpha(int alpha);
		void setLineWidth(float width);
		void setAntialias(boolean enabled);
		void setFont(String family, float size, int style);
		void setTransform(float[] matrix);
		void setClipping(int x, int y, int width, int height);
		void resetClipping();
		void drawLine(int x1, int y1, int x2, int y2);
		void drawRectangle(int x, int y, int width, int height);
		void fillRectangle(int x, int y, int width, int height);
		void drawOval(int x, int y, int width, int height);
		void fillOval(int x, int y, int width, int height);
		void drawPolygon(int[] points, boolean fill);
		void drawText(String text, int x, int y, int flags);
		void drawImage(long image, int srcX, int srcY, int srcWidth, int srcHeight,
			int destX, int destY, int destWidth, int destHeight);
		int[] textExtent(String text, int flags);
		/** ascent, descent, average character width, leading, height. */
		int[] fontMetrics();
		@Override void close();
	}
}
