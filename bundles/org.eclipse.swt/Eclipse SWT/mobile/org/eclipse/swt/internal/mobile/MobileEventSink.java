/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

/** Native UI callbacks. Implementations enqueue work and never wait for the Display thread. */
public interface MobileEventSink {
	void resize(int width, int height);

	void pointer(int type, int x, int y, int button, int stateMask, int time);

	void key(int type, int keyCode, char character, int stateMask, int time);

	void repaint();

	void suspend(boolean suspended);

	void close();
}
