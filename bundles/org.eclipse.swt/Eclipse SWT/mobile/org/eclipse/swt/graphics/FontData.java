/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.graphics;

import java.util.Objects;
import org.eclipse.swt.SWT;

/** Portable font request; native engines resolve this request on each platform. */
public final class FontData {
	private String name;
	private int height;
	private int style;
	private String locale = "";
	public FontData() { this("sans-serif", 12, SWT.NORMAL); }
	public FontData(String name, int height, int style) { setName(name); setHeight(height); setStyle(style); }
	public FontData(FontData other) {
		if (other == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		name = other.name; height = other.height; style = other.style; locale = other.locale;
	}
	public FontData(String data) {
		if (data == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		String[] parts = data.split("\\|", -1);
		try {
			if (parts.length < 5 || !parts[0].equals("1")) throw new IllegalArgumentException();
			setName(parts[1]); setHeight(Integer.parseInt(parts[2])); setStyle(Integer.parseInt(parts[3]));
		} catch (RuntimeException e) { SWT.error(SWT.ERROR_INVALID_ARGUMENT); }
	}
	public String getName() { return name; }
	public int getHeight() { return height; }
	public int getStyle() { return style; }
	public String getLocale() { return locale == null ? "" : locale; }
	public void setName(String value) { if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); name = value; }
	public void setHeight(int value) { if (value < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT); height = value; }
	public void setStyle(int value) { style = value & (SWT.BOLD | SWT.ITALIC); }
	public void setLocale(String value) { locale = value == null ? "" : value; }
	@Override public boolean equals(Object value) { return value instanceof FontData f && name.equals(f.name) && height == f.height && style == f.style && Objects.equals(locale, f.locale); }
	@Override public int hashCode() { return Objects.hash(name, height, style, locale); }
	@Override public String toString() { return "1|" + name + "|" + height + "|" + style + "|MOBILE|1|"; }
}
