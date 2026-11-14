/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.ios;

import org.eclipse.swt.internal.mobile.IOSPlatform;

/**
 * JNI declarations for the statically embedded UIKit host. The embedding app
 * calls SWTIOSAttach() after creating its JavaVM and adding SWT's classes to its
 * class path. That function registers this class's methods; no library loader,
 * JavaVM implementation or executable code download is hidden here.
 */
public final class OS {
	private OS() { }
	public static native long create(IOSPlatform platform);
	public static native void submit(long handle, int width, int height,
		int[] operations, int[] colors, float[] lineWidths, float[] coordinates,
		String[] texts, String[] fonts, int[] fontSizes, int[] fontStyles, int[] antialiases);
	public static native int[] measureText(String text, String fontName, int pixelSize, int style);
	public static native void setVisible(long handle, boolean visible);
	public static native void setTextInputEnabled(long handle, boolean enabled);
	public static native void dispose(long handle);
}
