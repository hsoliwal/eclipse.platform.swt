/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;

/** SWT decoration owner for the embedded mobile surface. */
public class Decorations extends Canvas {
	private String text = "";

	public Decorations(Composite parent, int style) {
		super(parent, style);
	}

	Decorations(Display display, int style) {
		super(display, style);
	}

	public String getText() {
		checkWidget();
		return text;
	}

	public void setText(String value) {
		checkWidget();
		if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		text = value;
	}
}
