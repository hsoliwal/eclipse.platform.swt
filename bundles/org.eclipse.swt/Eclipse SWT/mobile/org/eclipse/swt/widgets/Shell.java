/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ShellListener;

/** Root SWT composite hosted inside the native Android/iOS application. */
public class Shell extends Decorations {
	public Shell() {
		this(Display.getDefault());
	}

	public Shell(int style) {
		this(Display.getDefault(), style);
	}

	public Shell(Display display) {
		this(display, SWT.SHELL_TRIM);
	}

	public Shell(Display display, int style) {
		super(display == null ? Display.getDefault() : display, style);
		rootFlags = ENABLED;
		rootWidth = this.display.getBounds().width;
		rootHeight = this.display.getBounds().height;
		this.display.register(this);
	}

	public void open() {
		checkWidget();
		setVisible(true);
		if (isDisposed()) return;
		display.activate(this);
		if (isDisposed()) return;
		layout(true, true);
		if (!isDisposed()) redraw();
	}

	public void close() {
		checkWidget();
		Event e = new Event();
		notifyListeners(SWT.Close, e);
		if (!isDisposed() && e.doit) dispose();
	}

	public void addShellListener(ShellListener l) {
		addTypedListener(l, SWT.Activate, SWT.Close, SWT.Deactivate, SWT.Deiconify, SWT.Iconify);
	}

	public void removeShellListener(ShellListener l) {
		for (int type :
				new int[] {SWT.Activate, SWT.Close, SWT.Deactivate, SWT.Deiconify, SWT.Iconify})
			removeTypedListener(type, l);
	}

	@Override
	void releaseWidget() {
		try {
			display.unregister(this);
		} finally {
			super.releaseWidget();
		}
	}
}
