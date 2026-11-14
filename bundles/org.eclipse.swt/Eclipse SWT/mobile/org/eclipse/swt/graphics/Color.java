/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;

/** Immutable RGBA value; it owns no native allocation. */
public final class Color extends Resource {
	private final int argb;
	private boolean disposed;
	public Color(int r, int g, int b) { this(null, r, g, b, 255, false); }
	public Color(int r, int g, int b, int a) { this(null, r, g, b, a, false); }
	public Color(Device device, int r, int g, int b) { this(device, r, g, b, 255, true); }
	public Color(Device device, int r, int g, int b, int a) { this(device, r, g, b, a, true); }
	public Color(RGB rgb) { this(rgb, 255); }
	public Color(RGB rgb, int a) { this(null, require(rgb).red, rgb.green, rgb.blue, a, false); }
	public Color(Device d, RGB rgb) { this(d, rgb, 255); }
	public Color(Device d, RGB rgb, int a) { this(d, require(rgb).red, rgb.green, rgb.blue, a, true); }
	public Color(RGBA rgba) { this(require(rgba).rgb, rgba.alpha); }
	public Color(Device d, RGBA rgba) { this(d, require(rgba).rgb, rgba.alpha); }
	private Color(Device d, int r, int g, int b, int a, boolean requireDevice) {
		super();
		if ((r | g | b | a) < 0 || r > 255 || g > 255 || b > 255 || a > 255) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		device = d == null ? Device.getDevice() : d;
		if (requireDevice && device == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (device != null) device.checkDevice();
		argb = a << 24 | r << 16 | g << 8 | b;
	}
	private static RGB require(RGB v) { if (v == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return v; }
	private static RGBA require(RGBA v) { if (v == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return v; }
	private void check() { if (disposed) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED); }
	public int getAlpha() { check(); return argb >>> 24; }
	public int getRed() { check(); return argb >>> 16 & 255; }
	public int getGreen() { check(); return argb >>> 8 & 255; }
	public int getBlue() { check(); return argb & 255; }
	public RGB getRGB() { return new RGB(getRed(), getGreen(), getBlue()); }
	public RGBA getRGBA() { return new RGBA(getRed(), getGreen(), getBlue(), getAlpha()); }
	public int internal_argb() { check(); return argb; }
	@Override public Device getDevice() { check(); return device == null ? Device.getDevice() : device; }
	@Override public boolean isDisposed() { return disposed; }
	@Override public void dispose() { disposed = true; device = null; }
	@Override public boolean equals(Object value) { return this == value || value instanceof Color c && !disposed && !c.disposed && argb == c.argb; }
	@Override public int hashCode() { return argb; }
	@Override public String toString() { return disposed ? "Color {*DISPOSED*}" : "Color {" + getRed() + ", " + getGreen() + ", " + getBlue() + ", " + getAlpha() + "}"; }
}
