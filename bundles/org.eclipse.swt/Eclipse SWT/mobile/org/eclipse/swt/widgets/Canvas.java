/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

/** SWT Canvas implemented by the mobile graphics and input backends. */
public class Canvas extends Composite {
	public Canvas(Composite parent, int style) {
		super(parent, style);
	}

	Canvas(Display display, int style) {
		super(display, style);
	}
}
