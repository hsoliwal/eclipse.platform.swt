/* SPDX-FileCopyrightText: 2026 Synexia contributors
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt;
/** Test-only stand-in for a headless owner-source compile. Never ship in SWT JARs. */
public final class SWT {
    public static final int ERROR_INVALID_ARGUMENT = 5;
    public static final int ERROR_INVALID_RANGE = 6;
    private SWT() { }
    public static void error(int code) { throw new IllegalArgumentException("headless SWT " + code); }
}
