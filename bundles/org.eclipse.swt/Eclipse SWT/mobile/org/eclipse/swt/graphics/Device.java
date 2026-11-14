/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobilePlatform;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Display;

/** Mobile graphics device. Native handles remain owned by their SWT resources. */
public abstract class Device implements Drawable {
	protected volatile boolean disposed;
	protected final MobilePlatform platform;
	boolean tracking;
	private boolean warnings = true;
	private Object[] objects = new Object[16];
	private Error[] errors = new Error[16];
	private Font systemFont;

	public Device() { this(null); }
	public Device(DeviceData data) {
		platform = MobileRuntime.platform();
		tracking = data != null && data.tracking;
	}
	static Device getDevice() { return Display.getCurrent(); }
	protected void checkDevice() { if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED); }
	public boolean isDisposed() { return disposed; }
	public void dispose() {
		if (disposed) return;
		checkDevice();
		if (systemFont != null) systemFont.dispose();
		disposed = true;
	}
	public final MobilePlatform internal_platform() { checkDevice(); return platform; }
	public Rectangle getBounds() {
		checkDevice(); int[] b = platform.displayBounds();
		return new Rectangle(b[0], b[1], b[2], b[3]);
	}
	public Rectangle getClientArea() { return getBounds(); }
	public int getDepth() { checkDevice(); return 32; }
	public Point getDPI() { checkDevice(); int[] d = platform.dpi(); return new Point(d[0], d[1]); }
	public boolean getWarnings() { checkDevice(); return warnings; }
	public void setWarnings(boolean value) { checkDevice(); warnings = value; }
	public Font getSystemFont() {
		checkDevice();
		if (systemFont == null || systemFont.isDisposed()) systemFont = new Font(this, "sans-serif", 12, SWT.NORMAL);
		return systemFont;
	}
	public Color getSystemColor(int id) {
		checkDevice();
		int rgb = switch (id) {
			case SWT.COLOR_WHITE, SWT.COLOR_LIST_BACKGROUND, SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW -> 0xffffff;
			case SWT.COLOR_BLACK, SWT.COLOR_WIDGET_FOREGROUND, SWT.COLOR_LIST_FOREGROUND, SWT.COLOR_INFO_FOREGROUND -> 0x000000;
			case SWT.COLOR_RED -> 0xff0000; case SWT.COLOR_DARK_RED -> 0x800000;
			case SWT.COLOR_GREEN -> 0x00ff00; case SWT.COLOR_DARK_GREEN -> 0x008000;
			case SWT.COLOR_YELLOW -> 0xffff00; case SWT.COLOR_DARK_YELLOW -> 0x808000;
			case SWT.COLOR_BLUE -> 0x0000ff; case SWT.COLOR_DARK_BLUE -> 0x000080;
			case SWT.COLOR_MAGENTA -> 0xff00ff; case SWT.COLOR_DARK_MAGENTA -> 0x800080;
			case SWT.COLOR_CYAN -> 0x00ffff; case SWT.COLOR_DARK_CYAN -> 0x008080;
			case SWT.COLOR_GRAY, SWT.COLOR_WIDGET_LIGHT_SHADOW -> 0xc0c0c0;
			case SWT.COLOR_DARK_GRAY, SWT.COLOR_WIDGET_NORMAL_SHADOW -> 0x808080;
			case SWT.COLOR_WIDGET_DARK_SHADOW -> 0x404040;
			case SWT.COLOR_WIDGET_BACKGROUND -> 0xf0f0f0;
			case SWT.COLOR_LIST_SELECTION -> 0x007aff;
			case SWT.COLOR_LIST_SELECTION_TEXT -> 0xffffff;
			case SWT.COLOR_INFO_BACKGROUND -> 0xffffe1;
			case SWT.COLOR_TITLE_BACKGROUND, SWT.COLOR_TITLE_BACKGROUND_GRADIENT -> 0x007aff;
			case SWT.COLOR_TITLE_FOREGROUND -> 0xffffff;
			case SWT.COLOR_TITLE_INACTIVE_BACKGROUND, SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT -> 0x808080;
			case SWT.COLOR_TITLE_INACTIVE_FOREGROUND -> 0xffffff;
			case SWT.COLOR_LINK_FOREGROUND -> 0x007aff;
			case SWT.COLOR_TRANSPARENT -> 0;
			default -> { SWT.error(SWT.ERROR_INVALID_ARGUMENT); yield 0; }
		};
		return new Color(this, rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, id == SWT.COLOR_TRANSPARENT ? 0 : 255);
	}
	public FontData[] getFontList(String name, boolean scalable) {
		checkDevice();
		throw new UnsupportedOperationException("Font enumeration is not implemented by the mobile backend");
	}
	public boolean loadFont(String path) {
		checkDevice(); if (path == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		throw new UnsupportedOperationException("Font registration is not implemented by the mobile backend");
	}
	public DeviceData getDeviceData() {
		checkDevice(); DeviceData result = new DeviceData(); result.tracking = tracking;
		synchronized (this) {
			int count = 0; for (Object o : objects) if (o != null) count++;
			result.objects = new Object[count]; result.errors = new Error[count]; int i = 0;
			for (int k = 0; k < objects.length; k++) if (objects[k] != null) { result.objects[i] = objects[k]; result.errors[i++] = errors[k]; }
		}
		return result;
	}
	synchronized void new_Object(Object object) {
		int i = 0; while (i < objects.length && objects[i] != null) i++;
		if (i == objects.length) { objects = Arrays.copyOf(objects, i + 16); errors = Arrays.copyOf(errors, i + 16); }
		objects[i] = object; errors[i] = new Error("Resource allocation");
	}
	synchronized void dispose_Object(Object object) {
		for (int i = 0; i < objects.length; i++) if (objects[i] == object) { objects[i] = null; errors[i] = null; break; }
	}
	@Override public long internal_new_GC(GCData data) { checkDevice(); data.device = this; data.screen = true; return 1; }
	@Override public void internal_dispose_GC(long handle, GCData data) { }
}
