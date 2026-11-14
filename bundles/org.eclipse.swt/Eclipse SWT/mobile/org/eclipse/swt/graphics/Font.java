/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;

/** Mobile font descriptor. Platform measurement and drawing resolve the requested family. */
public final class Font extends Resource {
	private final FontData data;

	public Font(Device device, String name, int height, int style) {
		this(device, new FontData(name, height, style));
	}

	public Font(Device device, FontData value) {
		super(device);
		if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		data = copy(value);
		init();
	}

	public Font(Device device, FontData[] values) {
		this(device, first(values));
	}

	private static FontData first(FontData[] values) {
		if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (values.length == 0 || values[0] == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		return values[0];
	}

	private static FontData copy(FontData v) {
		FontData r = new FontData(v.getName(), v.getHeight(), v.getStyle());
		r.setLocale(v.getLocale());
		return r;
	}

	public FontData[] getFontData() {
		getDevice();
		return new FontData[] {copy(data)};
	}

	public int internalPixelSize() {
		getDevice();
		return Math.max(1, Math.round(data.getHeight() * device.getDPI().y / 72f));
	}

	@Override
	public boolean isDisposed() {
		return device == null || device.isDisposed();
	}

	@Override
	public int hashCode() {
		return data.hashCode();
	}

	@Override
	public boolean equals(Object value) {
		return value == this
				|| value instanceof Font f
						&& !isDisposed()
						&& !f.isDisposed()
						&& f.device == device
						&& f.data.equals(data);
	}
}
