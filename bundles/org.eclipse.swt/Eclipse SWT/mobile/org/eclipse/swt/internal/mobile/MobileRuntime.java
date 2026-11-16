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
