/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Mobile graphics device. Actual drawing is owned by the platform and the SWT control. */
public abstract class Device implements Drawable {
	protected volatile boolean disposed;
	boolean tracking;
	private final Set<Object> resources = Collections.newSetFromMap(new IdentityHashMap<>());

	protected Device() {}

	static Device getDevice() {
		return Display.getCurrent();
	}

	protected void checkDevice() {
		if (disposed) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
	}

	public boolean isDisposed() {
		return disposed;
	}

	public void dispose() {
		disposed = true;
	}

	void new_Object(Object object) {
		synchronized (resources) {
			resources.add(object);
		}
	}

	void dispose_Object(Object object) {
		synchronized (resources) {
			resources.remove(object);
		}
	}

	public Point getDPI() {
		checkDevice();
		return new Point(96, 96);
	}

	public int getDepth() {
		checkDevice();
		return 32;
	}

	public abstract Rectangle getBounds();

	public Rectangle getClientArea() {
		return getBounds();
	}

	public abstract Color getSystemColor(int id);

	public abstract Font getSystemFont();
}
