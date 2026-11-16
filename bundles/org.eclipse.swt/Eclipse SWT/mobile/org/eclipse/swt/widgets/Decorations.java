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
