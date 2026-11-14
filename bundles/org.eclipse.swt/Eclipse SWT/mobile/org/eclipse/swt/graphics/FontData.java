/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;

import java.util.Objects;

/** Platform-independent mobile font request, with height measured in points. */
public final class FontData {
	private String name = "sans-serif", locale = "";
	private int height = 12, style;

	public FontData() {}

	public FontData(String name, int height, int style) {
		setName(name);
		setHeight(height);
		setStyle(style);
	}

	public String getName() {
		return name;
	}

	public int getHeight() {
		return height;
	}

	public int getStyle() {
		return style;
	}

	public String getLocale() {
		return locale;
	}

	public void setName(String value) {
		if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		name = value;
	}

	public void setHeight(int value) {
		if (value < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		height = value;
	}

	public void setStyle(int value) {
		style = value & (SWT.BOLD | SWT.ITALIC);
	}

	public void setLocale(String value) {
		locale = value == null ? "" : value;
	}

	@Override
	public boolean equals(Object v) {
		return v instanceof FontData f
				&& f.height == height
				&& f.style == style
				&& f.name.equals(name)
				&& f.locale.equals(locale);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, height, style, locale);
	}

	@Override
	public String toString() {
		return "1|" + name + "|" + height + "|" + style + "|MOBILE|1|";
	}
}
