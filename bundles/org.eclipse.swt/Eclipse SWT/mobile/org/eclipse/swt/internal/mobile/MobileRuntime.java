/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

import java.util.Objects;

/** Bootstrap binding; it owns no widgets, render model, event loop or payload. */
public final class MobileRuntime {
	private static MobilePlatform platform;
	private MobileRuntime() {}

	public static synchronized void install(MobilePlatform candidate) {
		Objects.requireNonNull(candidate, "platform");
		if (platform != null && platform != candidate) {
			throw new IllegalStateException("An SWT mobile platform is already installed");
		}
		platform = candidate;
	}

	public static synchronized MobilePlatform platform() {
		if (platform == null) {
			throw new IllegalStateException("Start SWT through the Android or iOS application host");
		}
		return platform;
	}

	public static synchronized void uninstall(MobilePlatform expected) {
		if (platform != expected) throw new IllegalStateException("SWT platform identity changed");
		platform = null;
	}
}
