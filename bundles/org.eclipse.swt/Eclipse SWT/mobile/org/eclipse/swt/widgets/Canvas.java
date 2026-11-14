/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

/** A custom-drawn SWT Canvas, with painting and input owned by the original Control. */
public class Canvas extends Composite {
	public Canvas(Composite parent, int style) { super(parent, style); }
	Canvas(Display display, int style) { super(display, style); }
}
