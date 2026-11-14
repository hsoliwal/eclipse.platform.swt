/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;

/** Font descriptor owned by SWT; native GC instances retain resolved font state. */
public final class Font extends Resource {
	private final FontData[] descriptions;
	private boolean disposed;
	public Font(Device device, String name, int height, int style) { this(device, new FontData(name, height, style)); }
	public Font(Device device, FontData data) { this(device, new FontData[] {data}); }
	public Font(Device device, FontData[] data) {
		super(device);
		if (data == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (data.length == 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		descriptions = new FontData[data.length];
		for (int i = 0; i < data.length; i++) { if (data[i] == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT); descriptions[i] = new FontData(data[i]); }
		init();
	}
	public FontData[] getFontData() {
		if (isDisposed()) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
		FontData[] copy = new FontData[descriptions.length];
		for (int i = 0; i < copy.length; i++) copy[i] = new FontData(descriptions[i]);
		return copy;
	}
	@Override void destroy() { disposed = true; }
	@Override public boolean isDisposed() { return disposed || device == null || device.isDisposed(); }
	@Override public boolean equals(Object value) { return this == value || value instanceof Font f && !isDisposed() && !f.isDisposed() && java.util.Arrays.equals(descriptions, f.descriptions); }
	@Override public int hashCode() { return java.util.Arrays.hashCode(descriptions); }
	@Override public String toString() { return isDisposed() ? "Font {*DISPOSED*}" : "Font {" + descriptions[0] + "}"; }
}
