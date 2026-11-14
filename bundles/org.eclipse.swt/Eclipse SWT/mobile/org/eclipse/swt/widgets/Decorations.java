/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;

/** Title and close state for mobile SWT shells. */
public class Decorations extends Canvas {
	private String text = "";
	public Decorations(Composite parent, int style) { super(parent, style); }
	Decorations(Display display, int style) { super(display, style); }
	public String getText() { checkWidget(); return text; }
	public void setText(String value) { checkWidget(); if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); text = value; display.internal_platform().setTitle(peer, value); }
	public boolean getMinimized() { checkWidget(); return false; }
	public void setMinimized(boolean value) { checkWidget(); if (value) throw new UnsupportedOperationException("Mobile shells cannot be minimized independently of their application"); }
	public boolean getMaximized() { checkWidget(); return getBounds().equals(display.getClientArea()); }
	public void setMaximized(boolean value) { checkWidget(); if (value) setBounds(display.getClientArea()); else throw new UnsupportedOperationException("Window restoration is not implemented by the mobile shell"); }
}
