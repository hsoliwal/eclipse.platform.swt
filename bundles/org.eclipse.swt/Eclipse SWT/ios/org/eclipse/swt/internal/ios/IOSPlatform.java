/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.ios;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;

import org.eclipse.swt.internal.mobile.MobilePlatform;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Display;

/** UIKit peers and CoreGraphics resources for the existing mobile SWT owners. */
public final class IOSPlatform implements MobilePlatform {
	private Thread displayThread;

	/** Called on the JVM worker by the native application host. */
	public static void launch(String mainClass, String[] arguments) throws Throwable {
		Objects.requireNonNull(mainClass, "mainClass");
		Objects.requireNonNull(arguments, "arguments");
		IOSPlatform platform = new IOSPlatform();
		MobileRuntime.install(platform);
		Throwable failure = null;
		try {
			var main = Class.forName(mainClass).getMethod("main", String[].class);
			if (!Modifier.isStatic(main.getModifiers()) || main.getReturnType() != void.class) {
				throw new NoSuchMethodException(mainClass + ".main(String[]) must be static void");
			}
			try {
				main.invoke(null, (Object) arguments.clone());
			} catch (InvocationTargetException ex) {
				throw ex.getCause();
			}
		} catch (Throwable ex) {
			failure = ex;
		} finally {
			try {
				Display display = Display.getCurrent();
				if (display != null && !display.isDisposed()) display.dispose();
			} catch (Throwable ex) {
				if (failure == null) failure = ex; else failure.addSuppressed(ex);
			}
			try {
				if (platform.displayThread != null) platform.disconnect();
			} catch (Throwable ex) {
				if (failure == null) failure = ex; else failure.addSuppressed(ex);
			}
			try {
				MobileRuntime.uninstall(platform);
			} catch (Throwable ex) {
				if (failure == null) failure = ex; else failure.addSuppressed(ex);
			}
		}
		if (failure != null) throw failure;
	}

	@Override public String name() { return "ios"; }

	@Override public void connect(EventSink sink) {
		Objects.requireNonNull(sink, "sink");
		if (displayThread != null) throw new IllegalStateException("An iOS Display is already connected");
		OS.connect(sink);
		displayThread = Thread.currentThread();
	}

	private void checkThread() {
		if (displayThread == null || Thread.currentThread() != displayThread) {
			throw new IllegalStateException("Operation must run on the connected SWT Display thread");
		}
	}

	private void check() { checkThread(); OS.checkFailure(); }
	@Override public void checkFailure() { check(); }
	@Override public void disconnect() {
		checkThread();
		try { OS.disconnect(); } finally { displayThread = null; }
		OS.checkFailure();
	}
	@Override public int[] displayBounds() { check(); return OS.displayBounds(); }
	@Override public int[] dpi() { check(); return OS.dpi(); }
	@Override public long createControl(int kind, long parent, int style) { check(); return OS.createControl(kind, parent, style); }
	@Override public void disposeControl(long peer) { checkThread(); OS.disposeControl(peer); }
	@Override public void setBounds(long peer, int x, int y, int w, int h) { check(); OS.setBounds(peer, x, y, w, h); }
	@Override public void setVisible(long peer, boolean visible) { check(); OS.setVisible(peer, visible); }
	@Override public void setEnabled(long peer, boolean enabled) { check(); OS.setEnabled(peer, enabled); }
	@Override public void setTitle(long peer, String title) { check(); OS.setTitle(peer, Objects.requireNonNull(title, "title")); }
	@Override public boolean requestFocus(long peer) { check(); return OS.requestFocus(peer); }
	@Override public void setAccessibility(long peer, String label, int role, int state) { check(); OS.setAccessibility(peer, Objects.requireNonNull(label, "label"), role, state); }
	@Override public void present(long peer, long image) { check(); OS.present(peer, image); }
	@Override public long createImage(int w, int h, int[] argb) { check(); return OS.createImage(w, h, argb); }
	@Override public void disposeImage(long image) { checkThread(); OS.disposeImage(image); }
	@Override public int[] imagePixels(long image) { check(); return OS.imagePixels(image); }
	@Override public Graphics graphics(long image) { check(); return new CoreGraphics(OS.createGraphics(image)); }
	@Override public int[] captureScreen(int x, int y, int width, int height) {
		check();
		throw new UnsupportedOperationException("iOS has no synchronous visible-screen capture in this backend; use an XCUI device screenshot");
	}

	private final class CoreGraphics implements Graphics {
		private long handle;
		CoreGraphics(long handle) { this.handle = handle; }
		private long live() {
			check();
			if (handle == 0) throw new IllegalStateException("GC is disposed");
			return handle;
		}
		@Override public void setForeground(int argb) { OS.setForeground(live(), argb); }
		@Override public void setBackground(int argb) { OS.setBackground(live(), argb); }
		@Override public void setAlpha(int alpha) { OS.setAlpha(live(), alpha); }
		@Override public void setLineWidth(float width) { OS.setLineWidth(live(), width); }
		@Override public void setAntialias(boolean enabled) { OS.setAntialias(live(), enabled); }
		@Override public void setFont(String family, float size, int style) { OS.setFont(live(), Objects.requireNonNull(family, "family"), size, style); }
		@Override public void setTransform(float[] matrix) { OS.setTransform(live(), matrix == null ? null : matrix.clone()); }
		@Override public void setClipping(int x, int y, int width, int height) { OS.setClipping(live(), true, x, y, width, height); }
		@Override public void resetClipping() { OS.setClipping(live(), false, 0, 0, 0, 0); }
		@Override public void drawLine(int x1, int y1, int x2, int y2) { OS.drawLine(live(), x1, y1, x2, y2); }
		@Override public void drawRectangle(int x, int y, int w, int h) { OS.drawShape(live(), 0, false, x, y, w, h); }
		@Override public void fillRectangle(int x, int y, int w, int h) { OS.drawShape(live(), 0, true, x, y, w, h); }
		@Override public void drawOval(int x, int y, int w, int h) { OS.drawShape(live(), 1, false, x, y, w, h); }
		@Override public void fillOval(int x, int y, int w, int h) { OS.drawShape(live(), 1, true, x, y, w, h); }
		@Override public void drawPolygon(int[] points, boolean fill) { OS.drawPolygon(live(), Objects.requireNonNull(points, "points"), fill); }
		@Override public void drawText(String text, int x, int y, int flags) { OS.drawText(live(), Objects.requireNonNull(text, "text"), x, y, flags); }
		@Override public void drawImage(long image, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) { OS.drawImage(live(), image, sx, sy, sw, sh, dx, dy, dw, dh); }
		@Override public int[] textExtent(String text, int flags) { return OS.textExtent(live(), Objects.requireNonNull(text, "text"), flags); }
		@Override public int[] fontMetrics() { return OS.fontMetrics(live()); }
		@Override public void close() {
			checkThread();
			if (handle == 0) return;
			OS.disposeGraphics(handle);
			handle = 0;
		}
	}
}
