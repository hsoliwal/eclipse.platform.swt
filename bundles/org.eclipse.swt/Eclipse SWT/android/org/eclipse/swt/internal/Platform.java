/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

/** Platform identity for the Android source set. */
public final class Platform {
    public static final String PLATFORM = "android";
    private Platform() {}
    public static boolean isLoadable() { return true; }
}
