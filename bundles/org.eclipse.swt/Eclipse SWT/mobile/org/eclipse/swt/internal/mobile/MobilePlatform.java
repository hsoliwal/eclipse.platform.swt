/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

import java.util.Objects;

/** Platform services for SWT's own mobile widget implementations. No widget state lives here. */
public abstract class MobilePlatform {
	private static final ThreadLocal<MobilePlatform> CURRENT = new ThreadLocal<>();

	/** Bind on the SWT application thread before constructing its Display. */
	public static void bind(MobilePlatform platform) {
		Objects.requireNonNull(platform, "platform");
		if (CURRENT.get() != null)
			throw new IllegalStateException("A mobile platform is already bound");
		CURRENT.set(platform);
	}

	public static MobilePlatform current() {
		MobilePlatform result = CURRENT.get();
		if (result == null)
			throw new IllegalStateException(
					"Launch through the Android or iOS SWT host before creating Display");
		return result;
	}

	public static void unbind(MobilePlatform platform) {
		if (CURRENT.get() == platform) CURRENT.remove();
	}

	public abstract String name();

	/** Dimensions and event coordinates use logical pixels, before the native backing scale. */
	public abstract int width();

	public abstract int height();

	public abstract void setEventSink(MobileEventSink sink);

	/** Publish a completely immutable frame without waiting for the native UI thread. */
	public abstract void submit(MobileFrame frame);

	/** Returns width, line height and ascent in logical pixels; callable on the Display thread. */
	public abstract int[] measureText(String text, String fontName, int pixelSize, int style);

	public abstract void setVisible(boolean visible);

	public abstract void dispose();
}
