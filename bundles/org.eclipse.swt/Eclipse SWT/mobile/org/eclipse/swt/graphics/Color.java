/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;

/** An RGBA value on a mobile Display. No native color object is retained. */
public final class Color extends Resource {
	private final int argb;
	private boolean disposed;

	public Color(Device device, int red, int green, int blue) {
		this(device, red, green, blue, 255);
	}

	public Color(Device device, int red, int green, int blue, int alpha) {
		super(device);
		if ((red | green | blue | alpha) < 0
				|| red > 255
				|| green > 255
				|| blue > 255
				|| alpha > 255) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		argb = alpha << 24 | red << 16 | green << 8 | blue;
	}

	public Color(Device device, RGB rgb) {
		this(device, red(rgb), rgb.green, rgb.blue);
	}

	public Color(Device device, RGBA rgba) {
		this(device, rgbaRGB(rgba).red, rgba.rgb.green, rgba.rgb.blue, rgba.alpha);
	}

	public Color(int red, int green, int blue) {
		this(null, red, green, blue);
	}

	public Color(int red, int green, int blue, int alpha) {
		this(null, red, green, blue, alpha);
	}

	public Color(RGB rgb) {
		this(null, rgb);
	}

	public Color(RGBA rgba) {
		this(null, rgba);
	}

	private static int red(RGB rgb) {
		if (rgb == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return rgb.red;
	}

	private static RGB rgbaRGB(RGBA rgba) {
		if (rgba == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return rgba.rgb;
	}

	private void check() {
		if (disposed) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
	}

	public int getRed() {
		check();
		return argb >>> 16 & 255;
	}

	public int getGreen() {
		check();
		return argb >>> 8 & 255;
	}

	public int getBlue() {
		check();
		return argb & 255;
	}

	public int getAlpha() {
		check();
		return argb >>> 24;
	}

	public RGB getRGB() {
		return new RGB(getRed(), getGreen(), getBlue());
	}

	public RGBA getRGBA() {
		return new RGBA(getRed(), getGreen(), getBlue(), getAlpha());
	}

	public int internalArgb() {
		check();
		return argb;
	}

	@Override
	public void dispose() {
		disposed = true;
	}

	@Override
	public boolean isDisposed() {
		return disposed;
	}

	@Override
	public int hashCode() {
		return argb;
	}

	@Override
	public boolean equals(Object value) {
		return value == this
				|| value instanceof Color c && !disposed && !c.disposed && c.argb == argb;
	}

	@Override
	public String toString() {
		return disposed
				? "Color {*DISPOSED*}"
				: "Color {"
						+ getRed()
						+ ", "
						+ getGreen()
						+ ", "
						+ getBlue()
						+ ", "
						+ getAlpha()
						+ "}";
	}
}
