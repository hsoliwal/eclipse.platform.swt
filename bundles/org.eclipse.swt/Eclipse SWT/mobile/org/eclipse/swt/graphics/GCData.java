/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.internal.mobile.MobileFrame;

import java.util.function.Supplier;

/** Internal graphics lease for a mobile Drawable. */
public final class GCData {
	public Device device;
	public Color foreground, background;
	public Font font;
	public Rectangle clipping;
	public Supplier<MobileFrame.Builder> commands;
	public Runnable changed, check;
}
