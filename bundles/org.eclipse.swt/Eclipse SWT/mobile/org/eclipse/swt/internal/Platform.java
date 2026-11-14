/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

import org.eclipse.swt.internal.mobile.MobileRuntime;

/** Platform identity comes from the installed, OS-specific application host. */
public final class Platform {
	public static final String PLATFORM = MobileRuntime.platform().name();
	private Platform() { }
	public static boolean isLoadable() { MobileRuntime.platform().checkFailure(); return true; }
	public static void exitIfNotLoadable() { if (!isLoadable()) throw new IllegalStateException("SWT mobile native host is unavailable"); }
}
