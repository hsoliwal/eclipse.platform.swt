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
import org.eclipse.swt.events.ShellListener;

/** A native Android/iOS root or owned shell. */
public class Shell extends Decorations {
	public Shell() { this(Display.getDefault()); }
	public Shell(int style) { this(Display.getDefault(), style); }
	public Shell(Display display) { this(display, SWT.SHELL_TRIM); }
	public Shell(Display display, int style) { super(display == null ? Display.getDefault() : display, style); setBounds(this.display.getClientArea()); }
	public Shell(Shell parent) { this(parent, SWT.DIALOG_TRIM); }
	public Shell(Shell parent, int style) { super(parent, style); setBounds(display.getClientArea()); }
	public void open() {
		checkWidget(); setVisible(true);
		if (disposed) return;
		layout(true, true);
		if (disposed) return;
		setFocus();
		if (!disposed) redrawAll();
	}
	public void close() { checkWidget(); Event e = new Event(); sendEvent(SWT.Close, e); if (!disposed && e.doit) dispose(); }
	public void addShellListener(ShellListener listener) { addTypedListener(listener, SWT.Close, SWT.Activate, SWT.Deactivate, SWT.Iconify, SWT.Deiconify); }
	public void removeShellListener(ShellListener listener) { for (int type : new int[] {SWT.Close,SWT.Activate,SWT.Deactivate,SWT.Iconify,SWT.Deiconify}) removeTypedListener(type, listener); }
	public Shell[] getShells() { checkWidget(); return java.util.Arrays.stream(display.getShells()).filter(s -> s != this && isDescendant(s)).toArray(Shell[]::new); }
	private boolean isDescendant(Shell shell) { for (Control c = shell.parent; c != null; c = c.parent) if (c == this) return true; return false; }
	public void setActive() { checkWidget(); setVisible(true); if (!disposed) setFocus(); }
	public void forceActive() { setActive(); }
}
