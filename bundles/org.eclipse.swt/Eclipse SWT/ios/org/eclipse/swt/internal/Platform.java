/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

/** Platform identity for SWT's iOS source selection. */
public final class Platform {
	public static final String PLATFORM = "ios";
	private Platform() { }
	public static boolean isLoadable() { return true; }
}
