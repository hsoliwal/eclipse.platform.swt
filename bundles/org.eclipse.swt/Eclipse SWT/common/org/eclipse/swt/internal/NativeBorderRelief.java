/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

import org.eclipse.swt.SWT;

/**
 * Shared input contract for the opt-in native Control border relief.
 * All rendering and native handle ownership remain in each platform's Control.
 */
public final class NativeBorderRelief {
    private NativeBorderRelief() {
    }

    public static boolean isValid(int relief) {
        return relief == SWT.DEFAULT || relief == SWT.SHADOW_NONE
                || relief == SWT.SHADOW_IN || relief == SWT.SHADOW_OUT
                || relief == SWT.SHADOW_ETCHED_IN || relief == SWT.SHADOW_ETCHED_OUT;
    }

    public static boolean isEtched(int relief) {
        return relief == SWT.SHADOW_ETCHED_IN || relief == SWT.SHADOW_ETCHED_OUT;
    }

    /**
     * Per-widget, theme-overriding GTK declaration; never a global CSS rule.
     * A default relief supplies no rule and restores the original GTK theme.
     */
    public static String gtkDeclaration(int relief) {
        return switch (relief) {
            case SWT.SHADOW_IN -> "border: 2px inset currentColor;";
            case SWT.SHADOW_OUT -> "border: 2px outset currentColor;";
            case SWT.SHADOW_ETCHED_IN -> "border: 2px groove currentColor;";
            case SWT.SHADOW_ETCHED_OUT -> "border: 2px ridge currentColor;";
            case SWT.SHADOW_NONE -> "border: 0 none;";
            default -> "";
        };
    }
}
